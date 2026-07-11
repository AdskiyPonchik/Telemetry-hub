package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.dto.TelemetryResponse;
import de.iot.hub.telemetry.service.TelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
@Tag(name = "Telemetry", description = "Ingestion and browsing of raw sensor readings")
public class TelemetryController {
    private final TelemetryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ingest a sensor reading", description = "Validates the reading, evaluates it against per-sensor thresholds and stores it with the computed status")
    public void receiveTelemetry(@Valid @RequestBody TelemetryRequest request) {
        service.processTelemetry(request);
    }

    @GetMapping
    @Operation(summary = "List stored readings", description = "Paginated, newest first by default")
    public Page<TelemetryResponse> getAllTelemetry(
            @PageableDefault(sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.getAll(pageable);
    }
}
