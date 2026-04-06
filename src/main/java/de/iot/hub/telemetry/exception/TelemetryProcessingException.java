package de.iot.hub.telemetry.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class TelemetryProcessingException extends RuntimeException{
    private final Map<String, String> errorDetails;

    public TelemetryProcessingException(String message, Map<String, String> details) {
        super(message);
        this.errorDetails = details;
    }

    public TelemetryProcessingException(String message, Throwable cause, Map<String, String> details) {
        super(message, cause);
        this.errorDetails = details;
    }
}
