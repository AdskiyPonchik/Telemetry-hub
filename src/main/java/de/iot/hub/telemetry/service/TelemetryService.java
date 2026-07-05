package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.TelemetryProperties;
import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.exception.TelemetryProcessingException;
import de.iot.hub.telemetry.model.SensorThresholds;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.model.TelemetryStatus;
import de.iot.hub.telemetry.repository.SensorThresholdRepository;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TelemetryService {
    private final TelemetryRepository repository;
    private final TelemetryProperties properties;
    private final SensorThresholdRepository thresholdRepository;

    @Transactional
    public void processTelemetry(TelemetryRequest request) {
        try {
            SensorThresholds config = thresholdRepository.findById(request.getSensorId())
                    .orElse(new SensorThresholds(request.getSensorId(), 80.0, 10.0));
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

        } catch (Exception e) {
            Map<String, String> details = new java.util.HashMap<>();
            details.put("sensorId", request.getSensorId());
            details.put("location", request.getLocation());
            details.put("exception", e.getClass().getSimpleName());
            details.put("message", e.getMessage() != null ? e.getMessage() : "No message");
            throw new TelemetryProcessingException("Failed to save telemetry for sensor: " + request.getSensorId(), e,
                    details);
        }
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

}
