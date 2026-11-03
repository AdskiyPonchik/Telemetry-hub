package de.iot.hub.telemetry.exception;

public class InvalidIncidentPageException extends RuntimeException {
    public InvalidIncidentPageException() {
        super("sensorId must be non-blank, page must be non-negative and size must be between 1 and 100");
    }
}
