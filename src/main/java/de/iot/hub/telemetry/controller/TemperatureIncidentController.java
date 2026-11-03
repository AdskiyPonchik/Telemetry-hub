package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.dto.TemperatureIncidentResponse;
import de.iot.hub.telemetry.service.TemperatureIncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/temperature-incidents")
@RequiredArgsConstructor
@Tag(name = "Temperature incidents", description = "Persistent temperature incidents and operator acknowledgement")
public class TemperatureIncidentController {
    private final TemperatureIncidentService service;

    @GetMapping
    @Operation(summary = "List a sensor's incidents", description = "Newest incident ID first; page >= 0 and size between 1 and 100")
    public Page<TemperatureIncidentResponse> getIncidents(@RequestParam String sensorId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.getIncidents(sensorId, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a temperature incident", description = "Returns 404 for an unknown incident")
    public TemperatureIncidentResponse getIncident(@PathVariable Long id) {
        return service.getIncident(id);
    }

    @PutMapping("/{id}/acknowledgement")
    @Operation(summary = "Acknowledge an incident", description = "Idempotent; preserves the first acknowledgement time, including for recovered incidents")
    public TemperatureIncidentResponse acknowledge(@PathVariable Long id) {
        return service.acknowledge(id);
    }
}
