package de.iot.hub.telemetry.exception;

import java.util.UUID;

public class TelemetryEventConflictException extends RuntimeException {
    public TelemetryEventConflictException(String sensorId, UUID eventId) {
        super("Event " + eventId + " already exists with different content for sensor " + sensorId);
    }
}
