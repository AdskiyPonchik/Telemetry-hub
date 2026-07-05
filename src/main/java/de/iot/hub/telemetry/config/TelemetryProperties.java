package de.iot.hub.telemetry.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "telemetry")
public class TelemetryProperties {

    private final Voltage voltage = new Voltage();
    private final Frequency frequency = new Frequency();
    private final Thresholds thresholds = new Thresholds();
    private final Analytics analytics = new Analytics();

    @Getter
    @Setter
    public static class Voltage {
        @NotNull private Double min;
        @NotNull private Double max;
    }

    @Getter
    @Setter
    public static class Frequency {
        @NotNull private Double min;
        @NotNull private Double max;
    }

    @Getter
    @Setter
    public static class Thresholds {
        @NotNull private Double defaultMaxTemperature;
        @NotNull private Double defaultMaxVibration;
    }

    @Getter
    @Setter
    public static class Analytics {
        @NotNull private Integer windowHours;
    }
}
