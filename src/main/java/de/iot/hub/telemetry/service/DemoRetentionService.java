package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.DemoProperties;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import de.iot.hub.telemetry.repository.TemperatureAlertStateRepository;
import de.iot.hub.telemetry.repository.TemperatureIncidentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("demo")
@RequiredArgsConstructor
public class DemoRetentionService {

    private final TelemetryRepository telemetryRepository;
    private final DemoProperties demoProperties;
    private final Clock clock;
    private final TemperatureAlertStateRepository stateRepository;
    private final TemperatureIncidentRepository incidentRepository;

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    @Transactional
    public int deleteExpiredReadings() {
        String sensorId = demoProperties.getSensorId();
        stateRepository.findBySensorIdForUpdate(sensorId);
        Instant cutoff = clock.instant().minus(demoProperties.getRetentionDays(), ChronoUnit.DAYS);
        int deletedReadings = telemetryRepository.deleteDemoReadingsOlderThan(sensorId,
                LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC));
        incidentRepository.deleteRecoveredOlderThan(sensorId, cutoff);
        return deletedReadings;
    }
}
