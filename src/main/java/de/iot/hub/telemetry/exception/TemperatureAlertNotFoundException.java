package de.iot.hub.telemetry.exception;

public class TemperatureAlertNotFoundException extends RuntimeException {
    public TemperatureAlertNotFoundException(String sensorId) {
        super("No temperature alert state found for sensor '" + sensorId + "'");
    }
}
