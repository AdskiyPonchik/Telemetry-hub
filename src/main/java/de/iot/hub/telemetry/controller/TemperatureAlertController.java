package de.iot.hub.telemetry.controller;


import de.iot.hub.telemetry.dto.TemperatureAlertResponse;
import de.iot.hub.telemetry.service.TemperatureAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/temperature-alerts")
@RequiredArgsConstructor
@Tag(name = "Temperature alerts")
public class TemperatureAlertController {
    private final TemperatureAlertService service;

    @GetMapping("/{sensorID}")
    @Operation(summary = "Get temperature alert state for a sensor",
            description = "Returns the consecutive breach cound and alert flag; "
                    + "404 if no state exists")
    public TemperatureAlertResponse getState(@PathVariable String sensorID) {
        return service.getState(sensorID);
    }
}
