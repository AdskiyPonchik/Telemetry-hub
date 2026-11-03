package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.dto.TemperatureAlertResponse;
import de.iot.hub.telemetry.exception.TemperatureAlertNotFoundException;
import de.iot.hub.telemetry.model.TemperatureAlertState;
import de.iot.hub.telemetry.repository.TemperatureAlertStateRepository;
import de.iot.hub.telemetry.repository.TemperatureIncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TemperatureAlertService {
    private final TemperatureAlertStateRepository repository;
    private final TemperatureIncidentRepository incidentRepository;

    @Transactional(readOnly = true)
    public TemperatureAlertResponse getState(String sensorID) {
        TemperatureAlertState state = repository.findById(sensorID)
                .orElseThrow(() -> new TemperatureAlertNotFoundException(sensorID));

        return new TemperatureAlertResponse(state.getSensorId(),
                state.getConsecutiveBreaches(),
                incidentRepository.existsBySensorIdAndRecoveredAtIsNull(sensorID),
                state.getConsecutiveNormalReadings());
    }
}
