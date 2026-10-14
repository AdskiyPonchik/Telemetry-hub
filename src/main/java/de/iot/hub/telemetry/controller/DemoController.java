package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.dto.DemoSnapshotResponse;
import de.iot.hub.telemetry.service.DemoSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
@Profile("demo")
@Tag(name = "Demo", description = "Read-only snapshot for the public telemetry demo")
public class DemoController {

    private final DemoSnapshotService service;

    @GetMapping("/snapshot")
    @Operation(summary = "Get the current public demo snapshot")
    public DemoSnapshotResponse getSnapshot() {
        return service.getSnapshot();
    }
}
