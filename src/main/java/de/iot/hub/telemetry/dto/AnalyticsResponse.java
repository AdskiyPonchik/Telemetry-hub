package de.iot.hub.telemetry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AnalyticsResponse {
    private String sensorId;
    private long totalReadings;
    private long alarmCount;
    private double avgVoltage;
    private double avgFrequency;
}
