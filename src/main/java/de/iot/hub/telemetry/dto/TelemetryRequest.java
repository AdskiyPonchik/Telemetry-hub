package de.iot.hub.telemetry.dto;

import de.iot.hub.telemetry.validation.Finite;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.UUID;

@Data
public class TelemetryRequest {
    @NotNull(message = "Event ID is required")
    private UUID eventId;

    @NotBlank(message = "Sensor ID is required")
    @Size(max=255, message = "Sensor's ID size is not allowed to be more than 255 symbols.")
    private String sensorId;

    @Size(max=255, message = "Location's size is not allowed to be more than 255 symbols.")
    private String location;

    @NotNull
    @Finite
    @Positive
    private Double voltage;

    @NotNull
    @Finite
    @Positive
    private Double current;

    @NotNull
    @Finite
    @Positive
    private Double frequency;

    @NotNull
    @Finite
    private Double temperature;

    @NotNull
    @Finite
    @PositiveOrZero
    private Double vibration;
}
