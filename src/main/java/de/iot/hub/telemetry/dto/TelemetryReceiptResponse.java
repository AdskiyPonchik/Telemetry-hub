package de.iot.hub.telemetry.dto;

import de.iot.hub.telemetry.model.TelemetryStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record TelemetryReceiptResponse(Long id, UUID eventId, String sensorId,
                                       TelemetryStatus status, LocalDateTime timestamp) {
}
