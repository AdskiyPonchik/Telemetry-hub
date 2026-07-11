package de.iot.hub.telemetry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end flow against a real Postgres (Testcontainers): Flyway migration,
 * ingestion with status evaluation, analytics aggregation and the paginated listing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class TelemetryHubIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private Map<String, Object> reading(String sensorId, double voltage) {
        return Map.of(
                "sensorId", sensorId,
                "location", "Integration-Test",
                "voltage", voltage,
                "current", 5.0,
                "frequency", 50.0,
                "temperature", 45.0,
                "vibration", 2.5);
    }

    @Test
    void ingestedReadings_showUpInAnalyticsAndListing() {
        ResponseEntity<Void> alarmReading =
                restTemplate.postForEntity("/api/v1/telemetry", reading("IT-SENSOR-01", 260.0), Void.class);
        assertEquals(HttpStatus.CREATED, alarmReading.getStatusCode());

        ResponseEntity<Void> normalReading =
                restTemplate.postForEntity("/api/v1/telemetry", reading("IT-SENSOR-01", 230.0), Void.class);
        assertEquals(HttpStatus.CREATED, normalReading.getStatusCode());

        ResponseEntity<Map<String, Object>> stats = restTemplate.exchange(
                "/api/v1/analytics/IT-SENSOR-01", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK, stats.getStatusCode());
        assertNotNull(stats.getBody());
        assertEquals(2, ((Number) stats.getBody().get("totalReadings")).intValue());
        assertEquals(1, ((Number) stats.getBody().get("alarmCount")).intValue());

        ResponseEntity<String> page =
                restTemplate.getForEntity("/api/v1/telemetry?page=0&size=10", String.class);
        assertEquals(HttpStatus.OK, page.getStatusCode());
        assertTrue(page.getBody().contains("IT-SENSOR-01"));
        assertTrue(page.getBody().contains("ALARM"));
    }

    @Test
    void invalidReading_isRejectedWithValidationErrors() {
        Map<String, Object> invalid = Map.of(
                "location", "Integration-Test",
                "voltage", -5.0,
                "current", 5.0,
                "frequency", 50.0,
                "temperature", 45.0,
                "vibration", 2.5);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/v1/telemetry", invalid, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("sensorId"));
    }

    @Test
    void analyticsForUnknownSensor_returns404() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/analytics/DOES-NOT-EXIST", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
