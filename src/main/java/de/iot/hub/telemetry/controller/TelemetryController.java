package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.dto.TelemetryResponse;
import de.iot.hub.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
public class TelemetryController {
    private final TelemetryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void receiveTelemetry(@Valid @RequestBody TelemetryRequest request) {
        service.processTelemetry(request);
    }

    @GetMapping
    public Page<TelemetryResponse> getAllTelemetry(Pageable pageable) {
        return service.getAll(pageable);
    }
}
