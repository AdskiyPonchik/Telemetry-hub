package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.dto.TemperatureIncidentResponse;
import de.iot.hub.telemetry.exception.InvalidIncidentPageException;
import de.iot.hub.telemetry.exception.TemperatureIncidentNotFoundException;
import de.iot.hub.telemetry.model.TemperatureIncident;
import de.iot.hub.telemetry.repository.TemperatureIncidentRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TemperatureIncidentService {
    private final TemperatureIncidentRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<TemperatureIncidentResponse> getIncidents(String sensorId, int page, int size) {
        if (sensorId == null || sensorId.isBlank() || page < 0 || size < 1 || size > 100) {
            throw new InvalidIncidentPageException();
        }
        return repository.findBySensorIdOrderByIdDesc(sensorId, PageRequest.of(page, size))
                .map(TemperatureIncidentService::toResponse);
    }

    @Transactional(readOnly = true)
    public TemperatureIncidentResponse getIncident(Long id) {
        return toResponse(repository.findById(id)
                .orElseThrow(() -> new TemperatureIncidentNotFoundException(id)));
    }

    @Transactional
    public TemperatureIncidentResponse acknowledge(Long id) {
        TemperatureIncident incident = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new TemperatureIncidentNotFoundException(id));
        if (incident.getAcknowledgedAt() == null) {
            incident.setAcknowledgedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        }
        return toResponse(incident);
    }

    public static TemperatureIncidentResponse toResponse(TemperatureIncident incident) {
        return new TemperatureIncidentResponse(incident.getId(), incident.getSensorId(),
                incident.getOpenedAt(), incident.getAcknowledgedAt(), incident.getRecoveredAt(),
                incident.getThresholdAtOpen(), incident.getTemperatureAtOpen(), incident.isMigrated(),
                incident.getRecoveredAt() == null);
    }
}
