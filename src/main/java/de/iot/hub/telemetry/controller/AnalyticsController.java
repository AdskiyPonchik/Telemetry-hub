package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.dto.AnalyticsResponse;
import de.iot.hub.telemetry.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService service;

    @GetMapping("/{sensorId}")
    @ResponseStatus(HttpStatus.OK)
    public AnalyticsResponse getStats(@PathVariable String sensorId){
        return service.getSensorStats(sensorId);
    }
}
