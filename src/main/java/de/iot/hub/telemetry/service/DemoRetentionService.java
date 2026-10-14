package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.DemoProperties;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import java.time.Clock;
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

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    @Transactional
    public int deleteExpiredReadings() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .minusDays(demoProperties.getRetentionDays());
        return telemetryRepository.deleteDemoReadingsOlderThan(demoProperties.getSensorId(), cutoff);
    }
}
