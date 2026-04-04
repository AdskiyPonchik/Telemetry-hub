package de.iot.hub.telemetry.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnalyticsResponse {
    private String sensorId;
    private long totalReadings;
    private long alarmCount;
    private double avgVoltage;
    private double avgFrequency;
}
