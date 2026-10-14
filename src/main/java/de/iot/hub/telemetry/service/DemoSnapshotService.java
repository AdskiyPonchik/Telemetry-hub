package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.DemoProperties;
import de.iot.hub.telemetry.config.TelemetryProperties;
import de.iot.hub.telemetry.dto.AnalyticsResponse;
import de.iot.hub.telemetry.dto.DemoReadingResponse;
import de.iot.hub.telemetry.dto.DemoSnapshotResponse;
import de.iot.hub.telemetry.model.SensorThresholds;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.repository.SensorThresholdRepository;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("demo")
@RequiredArgsConstructor
public class DemoSnapshotService {

    private static final int SNAPSHOT_MINUTES = 15;
    private static final int MAX_READINGS = 180;

    private final TelemetryRepository telemetryRepository;
    private final SensorThresholdRepository thresholdRepository;
    private final TelemetryProperties telemetryProperties;
    private final DemoProperties demoProperties;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DemoSnapshotResponse getSnapshot() {
        Instant generatedAt = clock.instant();
        LocalDateTime nowUtc = LocalDateTime.ofInstant(generatedAt, ZoneOffset.UTC);
        String sensorId = demoProperties.getSensorId();

        SensorThresholds effectiveSensorThresholds = thresholdRepository.findById(sensorId)
                .orElseGet(() -> new SensorThresholds(
                        sensorId,
                        telemetryProperties.getThresholds().getDefaultMaxTemperature(),
                        telemetryProperties.getThresholds().getDefaultMaxVibration()));

        DemoReadingResponse latest = telemetryRepository.findFirstBySensorIdOrderByTimestampDescIdDesc(sensorId)
                .map(this::toDemoReading)
                .orElse(null);

        List<Telemetry> newestFirst = telemetryRepository.findRecentForSensor(
                sensorId,
                nowUtc.minusMinutes(SNAPSHOT_MINUTES),
                nowUtc,
                PageRequest.of(0, MAX_READINGS));
        List<Telemetry> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        List<DemoReadingResponse> readings = chronological.stream()
                .map(this::toDemoReading)
                .toList();

        AnalyticsResponse analytics = telemetryRepository.getAggregatedStats(
                sensorId,
                nowUtc.minusHours(telemetryProperties.getAnalytics().getWindowHours()));

        return new DemoSnapshotResponse(
                new DemoSnapshotResponse.Sensor(sensorId, demoProperties.getLocation()),
                new DemoSnapshotResponse.Thresholds(
                        telemetryProperties.getVoltage().getMin(),
                        telemetryProperties.getVoltage().getMax(),
                        telemetryProperties.getFrequency().getMin(),
                        telemetryProperties.getFrequency().getMax(),
                        effectiveSensorThresholds.getMaxTemperature(),
                        effectiveSensorThresholds.getMaxVibration()),
                generatedAt,
                latest,
                readings,
                analytics);
    }

    private DemoReadingResponse toDemoReading(Telemetry telemetry) {
        return new DemoReadingResponse(
                telemetry.getId(),
                telemetry.getSensorId(),
                telemetry.getLocation(),
                telemetry.getVoltage(),
                telemetry.getCurrent(),
                telemetry.getFrequency(),
                telemetry.getTemperature(),
                telemetry.getVibration(),
                telemetry.getStatus(),
                telemetry.getTimestamp().atOffset(ZoneOffset.UTC));
    }
}
