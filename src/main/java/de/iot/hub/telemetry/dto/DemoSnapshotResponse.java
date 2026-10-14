package de.iot.hub.telemetry.dto;

import java.time.Instant;
import java.util.List;

public record DemoSnapshotResponse(
        Sensor sensor,
        Thresholds thresholds,
        Instant generatedAt,
        DemoReadingResponse latest,
        List<DemoReadingResponse> readings,
        AnalyticsResponse analytics) {

    public record Sensor(String id, String location) {
    }

    public record Thresholds(
            double voltageMin,
            double voltageMax,
            double frequencyMin,
            double frequencyMax,
            double maxTemperature,
            double maxVibration) {
    }
}
