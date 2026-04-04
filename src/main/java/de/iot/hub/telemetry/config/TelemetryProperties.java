package de.iot.hub.telemetry.config;
import jdk.jfr.Threshold;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "telemetry.thresholds")
public class TelemetryProperties {

    private Threshold voltage = new Threshold();
    private Threshold frequency = new Threshold();

    @Data
    public static class Threshold{
        private Double min;
        private Double max;
    }
}
