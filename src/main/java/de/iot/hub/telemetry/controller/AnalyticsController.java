package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.dto.AnalyticsResponse;
import de.iot.hub.telemetry.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Aggregated statistics per sensor")
public class AnalyticsController {
    private final AnalyticsService service;

    @GetMapping("/{sensorId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get aggregated stats for a sensor", description = "Reading count, alarm count and averages over the configured time window; 404 if the sensor has no data in the window")
    public AnalyticsResponse getStats(@PathVariable String sensorId) {
        return service.getSensorStats(sensorId);
    }
}
