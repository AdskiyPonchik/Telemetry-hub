package de.iot.hub.telemetry.dto;

import de.iot.hub.telemetry.model.TelemetryStatus;
import java.time.LocalDateTime;

public record TelemetryResponse(
        Long id,
        String sensorId,
        String location,
        Double voltage,
        Double current,
        Double frequency,
        Double temperature,
        Double vibration,
        TelemetryStatus status,
        LocalDateTime timestamp) {
}