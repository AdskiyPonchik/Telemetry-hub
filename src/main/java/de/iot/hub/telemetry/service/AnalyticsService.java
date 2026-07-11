package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.TelemetryProperties;
import de.iot.hub.telemetry.dto.AnalyticsResponse;
import de.iot.hub.telemetry.exception.SensorDataNotFoundException;
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
        AnalyticsResponse stats = repo.getAggregatedStats(sensorId, since);
        // The aggregation query returns no row when the sensor has no readings in the window
        if (stats == null) {
            throw new SensorDataNotFoundException(sensorId, hours);
        }
        return stats;
    }
}

