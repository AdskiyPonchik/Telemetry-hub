package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.TelemetryProperties;
import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.exception.TelemetryProcessingException;
import de.iot.hub.telemetry.model.Telemetry;
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

    @Transactional
    public void processTelemetry(TelemetryRequest request){

        try {
            boolean voltageIssue = request.getVoltage() < properties.getVoltage().getMin() ||
                    request.getVoltage() > properties.getVoltage().getMax();
            boolean frequencyIssue = request.getFrequency() < properties.getFrequency().getMin() ||
                    request.getFrequency() > properties.getFrequency().getMax();

            String finalStatus = request.getStatus();
            if (voltageIssue || frequencyIssue) {
                finalStatus = "ALARM";
            }

            Telemetry telemetry = Telemetry.builder()
                    .sensorId(request.getSensorId())
                    .location(request.getLocation())
                    .voltage(request.getVoltage())
                    .current(request.getCurrent())
                    .frequency(request.getFrequency())
                    .status(finalStatus)
                    .timestamp(LocalDateTime.now())
                    .build();

            repository.save(telemetry);
        } catch (Exception e){
            Map<String, String> details = Map.of(
                    "sensorId", String.valueOf(request.getSensorId()),
                    "location", request.getLocation(),
                    "exception", e.getClass().getSimpleName(),
                    "message", e.getMessage() != null ? e.getMessage() : "No message"
            );
            throw new TelemetryProcessingException("Failed to save telemetry for sensor: " + request.getSensorId(), e, details);
        }
    }

    public Page<Telemetry> getAll(Pageable pageable){
        return repository.findAll(pageable);
    }

}
