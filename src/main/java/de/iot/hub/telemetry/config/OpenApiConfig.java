package de.iot.hub.telemetry.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI telemetryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Telemetry Hub API")
                .description("Industrial IoT telemetry ingestion with per-sensor event UUID deduplication, threshold-based status evaluation, persistent temperature incidents and analytics")
                .version("v1"));
    }
}
