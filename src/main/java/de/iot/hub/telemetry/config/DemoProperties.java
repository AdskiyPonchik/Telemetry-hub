package de.iot.hub.telemetry.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "telemetry.demo")
public class DemoProperties {

    @NotBlank
    private String sensorId = "MGD-PWR-01";

    @NotBlank
    private String location = "Magdeburg-Nord";

    @Min(1)
    private int retentionDays = 7;
}
