package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.dto.AnalyticsResponse;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final TelemetryRepository repo;

    public AnalyticsResponse getSensorStats(String sensorId){
        LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
        return repo.getAggregatedStats(sensorId, yesterday);
    }
}
