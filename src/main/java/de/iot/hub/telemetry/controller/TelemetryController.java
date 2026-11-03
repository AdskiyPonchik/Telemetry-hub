package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.dto.TelemetryResponse;
import de.iot.hub.telemetry.dto.TelemetryReceiptResponse;
import de.iot.hub.telemetry.service.TelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
@Tag(name = "Telemetry", description = "Ingestion and browsing of raw sensor readings")
public class TelemetryController {
    private final TelemetryService service;

    @PostMapping
    @Operation(summary = "Ingest a sensor reading", description = "Requires eventId UUID and finite measurements. New event: 201 receipt; identical retry for the same sensor and event: 200 original receipt; changed content: 409. Retry deduplication lasts while the reading is retained.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "New event stored",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TelemetryReceiptResponse.class))),
                    @ApiResponse(responseCode = "200", description = "Identical retry; original receipt",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TelemetryReceiptResponse.class)))
            })
    public ResponseEntity<TelemetryReceiptResponse> receiveTelemetry(@Valid @RequestBody TelemetryRequest request) {
        var result = service.processTelemetry(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.receipt());
    }

    @GetMapping
    @Operation(summary = "List stored readings", description = "Paginated, newest first by default")
    public Page<TelemetryResponse> getAllTelemetry(
            @PageableDefault(sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.getAll(pageable);
    }
}
