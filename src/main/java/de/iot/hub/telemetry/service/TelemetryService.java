package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.TelemetryProperties;
import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.dto.TelemetryResponse;
import de.iot.hub.telemetry.model.SensorThresholds;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.model.TelemetryStatus;
import de.iot.hub.telemetry.repository.SensorThresholdRepository;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TelemetryService {
    private final TelemetryRepository repository;
    private final TelemetryProperties properties;
    private final SensorThresholdRepository thresholdRepository;

    @Transactional
    public void processTelemetry(TelemetryRequest request) {
        SensorThresholds config = thresholdRepository.findById(request.getSensorId())
                .orElseGet(() -> new SensorThresholds(
                        request.getSensorId(),
                        properties.getThresholds().getDefaultMaxTemperature(),
                        properties.getThresholds().getDefaultMaxVibration()));

        TelemetryStatus finalStatus = statusCheck(request.getVoltage(), request.getFrequency(),
                request.getTemperature(), request.getVibration(), config);

        Telemetry telemetry = Telemetry.builder()
                .sensorId(request.getSensorId())
                .location(request.getLocation())
                .voltage(request.getVoltage())
                .current(request.getCurrent())
                .frequency(request.getFrequency())
                .temperature(request.getTemperature())
                .vibration(request.getVibration())
                .status(finalStatus)
                .timestamp(LocalDateTime.now())
                .build();

        repository.save(telemetry);
    }

    private TelemetryStatus statusCheck(Double voltage, Double frequency, Double temperature,
            Double vibration, SensorThresholds config) {
        boolean electricalAlarm = voltage < properties.getVoltage().getMin() ||
                voltage > properties.getVoltage().getMax() ||
                frequency < properties.getFrequency().getMin() ||
                frequency > properties.getFrequency().getMax();

        boolean mechanicalDamage = temperature > config.getMaxTemperature() ||
                vibration > config.getMaxVibration();

        if (electricalAlarm && mechanicalDamage) {
            return TelemetryStatus.ALARM_AND_MECHANICAL_DAMAGE;
        }
        if (electricalAlarm) {
            return TelemetryStatus.ALARM;
        }
        if (mechanicalDamage) {
            return TelemetryStatus.MECHANICAL_DAMAGE;
        }

        return TelemetryStatus.OK;
    }

    public Page<TelemetryResponse> getAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::convertToResponse);
    }

    private TelemetryResponse convertToResponse(Telemetry telemetry) {
        return new TelemetryResponse(
                telemetry.getId(),
                telemetry.getSensorId(),
                telemetry.getLocation(),
                telemetry.getVoltage(),
                telemetry.getCurrent(),
                telemetry.getFrequency(),
                telemetry.getTemperature(),
                telemetry.getVibration(),
                telemetry.getStatus(),
                telemetry.getTimestamp());
    }

}
