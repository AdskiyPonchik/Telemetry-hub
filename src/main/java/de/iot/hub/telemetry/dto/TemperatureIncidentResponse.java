package de.iot.hub.telemetry.dto;

import java.time.Instant;

public record TemperatureIncidentResponse(Long id, String sensorId, Instant openedAt,
        Instant acknowledgedAt, Instant recoveredAt, Double thresholdAtOpen,
        Double temperatureAtOpen, boolean migrated, boolean active) {
}
