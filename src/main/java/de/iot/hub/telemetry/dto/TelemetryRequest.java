package de.iot.hub.telemetry.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TelemetryRequest {
    @NotBlank(message = "Sensor ID is required")
    private String sensorId;

    private String location;

    @NotNull @Positive
    private Double voltage;

    @NotNull @Positive
    private Double current;

    @NotNull @Positive
    private Double frequency;

    private String status;
}
