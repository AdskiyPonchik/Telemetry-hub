package de.iot.hub.telemetry.dto;

public record TelemetryIngestionResult(TelemetryReceiptResponse receipt, boolean created) {
}
