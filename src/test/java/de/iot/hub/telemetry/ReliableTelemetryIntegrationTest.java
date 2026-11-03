package de.iot.hub.telemetry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** HTTP and PostgreSQL acceptance scenarios, including separate concurrent transactions. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ReliableTelemetryIntegrationTest {
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @SuppressWarnings("unchecked")
    void openApiDocumentsRequiredEventIdentityAndBothSuccessStatuses() {
        var spec = get("/v3/api-docs");
        var paths = (Map<String, Object>) spec.get("paths");
        var telemetry = (Map<String, Object>) paths.get("/api/v1/telemetry");
        var operation = (Map<String, Object>) telemetry.get("post");
        var responses = (Map<String, Object>) operation.get("responses");
        assertTrue(responses.keySet().containsAll(List.of("201", "200", "400", "409")));
        var components = (Map<String, Object>) spec.get("components");
        var schemas = (Map<String, Object>) components.get("schemas");
        var request = (Map<String, Object>) schemas.get("TelemetryRequest");
        assertTrue(((List<String>) request.get("required")).contains("eventId"));
    }

    @Test
    void repeatedEventReturnsOriginalReceiptEvenAfterThresholdChangeAndConflictsAreAtomic() {
        String sensor = sensor();
        Map<String, Object> input = reading(sensor, 85);
        var created = post(input);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        var equivalent = new HashMap<>(input);
        equivalent.put("voltage", 230); // JSON numeric representation is not event identity.
        jdbc.update("INSERT INTO sensor_configs(sensor_id,max_temperature,max_vibration) VALUES (?,?,?)",
                sensor, 100.0, 20.0);
        var repeated = post(equivalent);
        assertEquals(HttpStatus.OK, repeated.getStatusCode());
        assertEquals(created.getBody(), repeated.getBody());
        assertEquals("MECHANICAL_DAMAGE", repeated.getBody().get("status"));

        for (String field : List.of("location", "voltage", "current", "frequency", "temperature", "vibration")) {
            var conflicting = new HashMap<>(input);
            conflicting.put(field, field.equals("location") ? "Changed location" : 999.0);
            assertEquals(HttpStatus.CONFLICT, post(conflicting).getStatusCode(), field);
        }
        assertEquals(1L, count("telemetry_data", sensor));
        assertEquals(1, number(alert(sensor), "consecutiveBreaches"));
        assertEquals(0L, count("temperature_incidents", sensor));

        var otherSensor = new HashMap<>(input);
        otherSensor.put("sensorId", sensor());
        assertEquals(HttpStatus.CREATED, post(otherSensor).getStatusCode());
    }

    @Test
    void hundredEventsAndTwentyRepeatsStoreExactlyHundredAndOneActiveIncident() {
        String sensor = sensor();
        var events = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 100; i++) {
            var input = reading(sensor, 81 + i * 0.01);
            events.add(input);
            assertEquals(HttpStatus.CREATED, post(input).getStatusCode());
        }
        for (var input : events.subList(0, 20)) {
            assertEquals(HttpStatus.OK, post(input).getStatusCode());
        }
        assertEquals(100L, count("telemetry_data", sensor));
        assertEquals(1L, count("temperature_incidents", sensor));
        assertEquals(3, number(alert(sensor), "consecutiveBreaches"));
        var analytics = get("/api/v1/analytics/" + sensor);
        assertEquals(100, number(analytics, "totalReadings"));
    }

    @Test
    void concurrentIdenticalRequestsCreateOneReadingAndIncrementOnlyOnce() throws Exception {
        String sensor = sensor();
        var input = reading(sensor, 90);
        var responses = concurrently(8, () -> post(input));
        assertEquals(1, responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count());
        assertEquals(7, responses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count());
        var receipt = responses.getFirst().getBody();
        assertTrue(responses.stream().allMatch(r -> receipt.equals(r.getBody())));
        assertEquals(1L, count("telemetry_data", sensor));
        assertEquals(1, number(alert(sensor), "consecutiveBreaches"));
        assertEquals(0L, count("temperature_incidents", sensor));
    }

    @Test
    void lifecycleKeepsAcknowledgementIndependentAndRequiresUninterruptedRecovery() {
        String sensor = sensor();
        var first = reading(sensor, 81);
        post(first);
        post(first);
        assertEquals(1, number(alert(sensor), "consecutiveBreaches"));
        post(reading(sensor, 82));
        assertEquals(false, alert(sensor).get("alertActive"));
        post(reading(sensor, 83));
        var incident = incidents(sensor).getFirst();
        long id = ((Number) incident.get("id")).longValue();
        assertEquals(true, incident.get("active"));
        assertEquals(false, incident.get("migrated"));
        assertEquals(80.0, ((Number) incident.get("thresholdAtOpen")).doubleValue());
        assertEquals(83.0, ((Number) incident.get("temperatureAtOpen")).doubleValue());
        assertTrue(incident.get("openedAt").toString().endsWith("Z"));

        var acknowledged = acknowledge(id);
        assertNotNull(acknowledged.get("acknowledgedAt"));
        assertEquals(acknowledged, acknowledge(id));
        assertNull(acknowledged.get("recoveredAt"));
        post(reading(sensor, 80));
        assertEquals(true, alert(sensor).get("alertActive"));
        assertEquals(1, number(alert(sensor), "consecutiveNormalReadings"));
        post(reading(sensor, 79));
        assertEquals(2, number(alert(sensor), "consecutiveNormalReadings"));
        post(reading(sensor, 85));
        assertEquals(0, number(alert(sensor), "consecutiveNormalReadings"));
        assertEquals(1, incidents(sensor).size());
        for (int i = 0; i < 3; i++) post(reading(sensor, 80));
        assertEquals(false, alert(sensor).get("alertActive"));
        var recovered = get("/api/v1/temperature-incidents/" + id);
        assertNotNull(recovered.get("recoveredAt"));
        assertEquals(acknowledged.get("acknowledgedAt"), recovered.get("acknowledgedAt"));
        assertEquals(recovered, acknowledge(id));

        for (int i = 0; i < 3; i++) post(reading(sensor, 90));
        var history = incidents(sensor);
        assertEquals(2, history.size());
        assertTrue(((Number) history.getFirst().get("id")).longValue() > id);
        assertEquals(true, history.getFirst().get("active"));
        assertEquals(false, history.get(1).get("active"));
    }

    @Test
    void acknowledgementAndRecoveryInConcurrentTransactionsPreserveBothChanges() throws Exception {
        String sensor = sensor();
        for (int i = 0; i < 3; i++) post(reading(sensor, 90));
        long id = ((Number) incidents(sensor).getFirst().get("id")).longValue();
        post(reading(sensor, 80));
        post(reading(sensor, 80));
        var normal = reading(sensor, 80);
        var operations = concurrently(2, new Callable<Map<String, Object>>() {
            private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            public Map<String, Object> call() {
                return calls.getAndIncrement() == 0 ? acknowledge(id) : post(normal).getBody();
            }
        });
        assertEquals(2, operations.size());
        var stored = get("/api/v1/temperature-incidents/" + id);
        assertNotNull(stored.get("acknowledgedAt"));
        assertNotNull(stored.get("recoveredAt"));
        assertEquals(false, stored.get("active"));
        assertEquals(false, alert(sensor).get("alertActive"));
        assertEquals(stored, acknowledge(id));
    }

    @Test
    void concurrentDifferentContentForSameKeyHasOneWinnerAndOneConflict() throws Exception {
        String sensor = sensor();
        var first = reading(sensor, 90);
        var second = new HashMap<>(first);
        second.put("temperature", 40.0);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var responses = concurrently(2, () -> post(calls.getAndIncrement() == 0 ? first : second));
        assertEquals(1, responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count());
        assertEquals(1, responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count());
        assertEquals(1L, count("telemetry_data", sensor));
    }

    private String sensor() { return "RELEASE-" + UUID.randomUUID(); }

    private Map<String, Object> reading(String sensor, double temperature) {
        return Map.of("eventId", UUID.randomUUID().toString(), "sensorId", sensor,
                "location", "Release test", "voltage", 230.0, "current", 5.0,
                "frequency", 50.0, "temperature", temperature, "vibration", 2.0);
    }

    private ResponseEntity<Map<String, Object>> post(Map<String, Object> input) {
        return exchange("/api/v1/telemetry", HttpMethod.POST, input);
    }

    private Map<String, Object> get(String path) {
        var response = exchange(path, HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), path);
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private Map<String, Object> acknowledge(long id) {
        var response = exchange("/api/v1/temperature-incidents/" + id + "/acknowledgement", HttpMethod.PUT, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private ResponseEntity<Map<String, Object>> exchange(String path, HttpMethod method, Object body) {
        return http.exchange(path, method, new HttpEntity<>(body), new ParameterizedTypeReference<>() {});
    }

    private Map<String, Object> alert(String sensor) { return get("/api/v1/temperature-alerts/" + sensor); }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> incidents(String sensor) {
        return (List<Map<String, Object>>) get("/api/v1/temperature-incidents?sensorId=" + sensor).get("content");
    }

    private long count(String table, String sensor) {
        // Table names are constants supplied by these tests, never request input.
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE sensor_id = ?", Long.class, sensor);
    }

    private int number(Map<String, Object> body, String field) { return ((Number) body.get(field)).intValue(); }

    private <T> List<T> concurrently(int workers, Callable<T> operation) throws Exception {
        try (var executor = Executors.newFixedThreadPool(workers)) {
            var ready = new CountDownLatch(workers);
            var start = new CountDownLatch(1);
            var futures = new ArrayList<java.util.concurrent.Future<T>>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return operation.call();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        }
    }
}
