package de.iot.hub.telemetry.config;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "telemetry.thresholds")
public class TelemetryProperties {

    @Valid
    private Threshold voltage = new Threshold();

    @Valid
    private Threshold frequency = new Threshold();

    @Data
    public static class Threshold{
        @NotNull
        @Min(0)
        @Max(1000)
        private Double min;

        @NotNull
        @Min(0)
        @Max(1000)
        private Double max;
    }
}
