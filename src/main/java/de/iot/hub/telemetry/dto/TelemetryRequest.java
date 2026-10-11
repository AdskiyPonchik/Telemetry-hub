package de.iot.hub.telemetry.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TelemetryRequest {
    @NotBlank(message = "Sensor ID is required")
    @Size(max=255, message = "Sensor's ID size is not allowed to be more than 255 symbols.")
    private String sensorId;

    @Size(max=255, message = "Location's size is not allowed to be more than 255 symbols.")
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
