package de.iot.hub.telemetry.service;
import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TelemetryService {
    private final TelemetryRepository repository;

    public void processTelemetry(TelemetryRequest request){

        boolean voltageIssue = request.getVoltage() < 207.0 || request.getVoltage() > 253.0;
        boolean frequencyIssue = request.getFrequency() < 49.0 || request.getFrequency() > 51.0;

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
