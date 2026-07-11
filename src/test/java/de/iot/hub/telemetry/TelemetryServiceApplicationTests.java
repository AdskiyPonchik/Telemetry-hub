package de.iot.hub.telemetry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TelemetryServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
