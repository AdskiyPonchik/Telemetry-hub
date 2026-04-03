package de.iot.hub.telemetry.service;
import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TelemetryService {
    private final TelemetryRepository repository;

    @Value("${telemetry.thresholds.voltage.min}")
    private double minVoltage;

    @Value("${telemetry.thresholds.voltage.max}")
    private double maxVoltage;

    @Value("${telemetry.thresholds.frequency.min}")
    private double minFrequency;

    @Value("${telemetry.thresholds.frequency.max}")
    private double maxFrequency;

    public void processTelemetry(TelemetryRequest request){

        boolean voltageIssue = request.getVoltage() < minVoltage || request.getVoltage() > maxVoltage;
        boolean frequencyIssue = request.getFrequency() < minFrequency || request.getFrequency() > maxFrequency;

        String finalStatus = request.getStatus();
        if(voltageIssue || frequencyIssue){
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
    }

    public List<Telemetry> getAll(){
        return repository.findAll();
    }

}
