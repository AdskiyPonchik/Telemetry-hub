package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
public class TelemetryController {
    private final TelemetryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void receiveTelemetry(@Valid @RequestBody TelemetryRequest request){
        service.processTelemetry(request);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Page<Telemetry> getAllTelemetry(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size){
        return service.getAll(PageRequest.of(page, size, Sort.by("timestamp").descending()));
    }
}

