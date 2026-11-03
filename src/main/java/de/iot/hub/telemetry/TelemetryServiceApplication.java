package de.iot.hub.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TelemetryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TelemetryServiceApplication.class, args);
	}


}
