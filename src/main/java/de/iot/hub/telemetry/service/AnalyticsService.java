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
        List<Telemetry> data = repo.findBySensorIdAndTimestampAfter(sensorId, yesterday);

        if(data.isEmpty()){
            return AnalyticsResponse.builder().sensorId(sensorId).totalReadings(0).build();
        }

        long alarms = data.stream().filter(t -> "ALARM".equals(t.getStatus())).count();
        double avgVolt = data.stream().mapToDouble(Telemetry::getVoltage).average().orElse(0.0);
        double avgFreq = data.stream().mapToDouble(Telemetry::getFrequency).average().orElse(0.0);

        return AnalyticsResponse.builder()
                .sensorId(sensorId)
                .totalReadings(data.size())
                .alarmCount(alarms)
                .avgVoltage(avgVolt)
                .avgFrequency(avgFreq)
                .build();
    }
}
