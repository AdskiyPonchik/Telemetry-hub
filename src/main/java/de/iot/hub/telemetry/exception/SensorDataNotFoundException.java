package de.iot.hub.telemetry.exception;

public class SensorDataNotFoundException extends RuntimeException {

    public SensorDataNotFoundException(String sensorId, int windowHours) {
        super("No telemetry data found for sensor '" + sensorId + "' within the last " + windowHours + " hours");
    }
}
