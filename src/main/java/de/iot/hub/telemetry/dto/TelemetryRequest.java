package de.iot.hub.telemetry.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TelemetryRequest {
    @NotBlank(message = "Sensor ID is required")
    private String sensorId;

    private String location;

    @NotNull
    @Positive
    private Double voltage;

    @NotNull
    @Positive
    private Double current;

    @NotNull
    @Positive
    private Double frequency;

    @NotNull
    private Double temperature;

    @NotNull
    @PositiveOrZero
    private Double vibration;
}
