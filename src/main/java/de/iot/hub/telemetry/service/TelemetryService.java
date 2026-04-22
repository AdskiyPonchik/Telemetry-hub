package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.TelemetryProperties;
import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.exception.TelemetryProcessingException;
import de.iot.hub.telemetry.model.SensorThresholds;
import de.iot.hub.telemetry.model.Telemetry;
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
    public void processTelemetry(TelemetryRequest request){
        try {
            SensorThresholds config = thresholdRepository.findById(request.getSensorId()).orElse(new SensorThresholds(request.getSensorId(), 80.0, 10.0));
            String finalStatus = statusCheck(request.getVoltage(), request.getFrequency(),
                    request.getTemperature(), request.getVibration(), config, request.getStatus());

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

        } catch (Exception e){
            Map<String, String> details = new java.util.HashMap<>();
            details.put("sensorId", request.getSensorId());
            details.put("location", request.getLocation());
            details.put("exception", e.getClass().getSimpleName());
            details.put("message", e.getMessage() != null ? e.getMessage() : "No message");
            throw new TelemetryProcessingException("Failed to save telemetry for sensor: " + request.getSensorId(), e, details);
        }
    }
    private String statusCheck(Double voltage, Double frequency, Double temperature,
                               Double vibration, SensorThresholds config, String defaultAnswer){
        StringBuilder status = new StringBuilder();
        boolean voltageIssue = voltage < properties.getVoltage().getMin() ||
                voltage > properties.getVoltage().getMax();
        boolean frequencyIssue = frequency < properties.getFrequency().getMin() ||
                frequency > properties.getFrequency().getMax();
        boolean tempIssue = temperature > config.getMaxTemperature();
        boolean vibIssue = vibration > config.getMaxVibration();

        if (voltageIssue || frequencyIssue) {
            status.append("ALARM");
        }
        if(tempIssue || vibIssue){
            if(!status.isEmpty()){
                status.append("_AND_");
            }
            status.append("MECHANICAL_DAMAGE");
        }
        if (status.isEmpty()){
            status.append(defaultAnswer);
        }
        return status.toString();
    }

    public Page<Telemetry> getAll(Pageable pageable){
        return repository.findAll(pageable);
    }

}
