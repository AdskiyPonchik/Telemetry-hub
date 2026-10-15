package de.iot.hub.telemetry.dto;

public record TemperatureAlertResponse(
        String sensorID,
        int consecutiveBreaches,
        boolean alertActive
) {
};
