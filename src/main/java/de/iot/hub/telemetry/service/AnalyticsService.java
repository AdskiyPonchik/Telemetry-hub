package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.TelemetryProperties;
import de.iot.hub.telemetry.dto.AnalyticsResponse;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final TelemetryRepository repo;
    private final TelemetryProperties properties;

    public AnalyticsResponse getSensorStats(String sensorId) {
        int hours = properties.getAnalytics().getWindowHours();
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return repo.getAggregatedStats(sensorId, since);
    }
}

