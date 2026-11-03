package de.iot.hub.telemetry.exception;

public class TemperatureIncidentNotFoundException extends RuntimeException {
    public TemperatureIncidentNotFoundException(Long id) {
        super("Temperature incident " + id + " was not found");
    }
}
