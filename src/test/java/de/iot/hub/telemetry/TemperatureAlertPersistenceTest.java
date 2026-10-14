package de.iot.hub.telemetry;

import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.model.SensorThresholds;
import de.iot.hub.telemetry.model.TemperatureAlertState;
import de.iot.hub.telemetry.repository.SensorThresholdRepository;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import de.iot.hub.telemetry.repository.TemperatureAlertStateRepository;
import de.iot.hub.telemetry.service.TelemetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TemperatureAlertPersistenceTest {

    private static final double DEFAULT_MAX_TEMPERATURE = 80.0;

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private TemperatureAlertStateRepository alertStateRepository;

    @Autowired
    private SensorThresholdRepository thresholdRepository;

    @Autowired
    private TelemetryRepository telemetryRepository;

    @Test
    void counterIsPersistedCappedAtThreeAndResetAtExactThreshold() {
        String sensorId = "PERSISTENCE-COUNTER";

        telemetryService.processTelemetry(reading(sensorId, 81.0));
        assertEquals(1, persistedBreaches(sensorId));

        telemetryService.processTelemetry(reading(sensorId, 82.0));
        assertEquals(2, persistedBreaches(sensorId));

        telemetryService.processTelemetry(reading(sensorId, 83.0));
        assertEquals(3, persistedBreaches(sensorId));

        telemetryService.processTelemetry(reading(sensorId, 84.0));
        assertEquals(3, persistedBreaches(sensorId));

        telemetryService.processTelemetry(reading(sensorId, DEFAULT_MAX_TEMPERATURE));
        assertEquals(0, persistedBreaches(sensorId));
    }

    @Test
    void countersAreIndependentForDifferentSensors() {
        String firstSensor = "PERSISTENCE-INDEPENDENT-A";
        String secondSensor = "PERSISTENCE-INDEPENDENT-B";

        telemetryService.processTelemetry(reading(firstSensor, 81.0));
        telemetryService.processTelemetry(reading(firstSensor, 82.0));
        telemetryService.processTelemetry(reading(secondSensor, 81.0));

        assertEquals(2, persistedBreaches(firstSensor));
        assertEquals(1, persistedBreaches(secondSensor));
    }

    @Test
    void customTemperatureThresholdIsUsedForTheCounter() {
        String sensorId = "PERSISTENCE-CUSTOM-THRESHOLD";
        thresholdRepository.saveAndFlush(new SensorThresholds(sensorId, 100.0, 10.0));

        telemetryService.processTelemetry(reading(sensorId, 90.0));
        assertEquals(0, persistedBreaches(sensorId));

        telemetryService.processTelemetry(reading(sensorId, 101.0));
        assertEquals(1, persistedBreaches(sensorId));
    }

    @Test
    void threeConcurrentInitialReadingsAreAllCounted() throws Exception {
        String sensorId = "PERSISTENCE-CONCURRENT-INITIAL";
        int workers = 3;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Void>> futures = java.util.stream.IntStream.range(0, workers)
                    .mapToObj(ignored -> executor.<Void>submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(5, TimeUnit.SECONDS), "Timed out waiting to start concurrent readings");
                        telemetryService.processTelemetry(reading(sensorId, 81.0));
                        return null;
                    }))
                    .toList();

            assertTrue(ready.await(5, TimeUnit.SECONDS), "Workers did not become ready in time");
            start.countDown();

            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Worker pool did not terminate in time");
        }

        assertEquals(3, persistedBreaches(sensorId));
        assertEquals(3, readingsFor(sensorId).size());
    }

    @Test
    void failedTelemetryInsertRollsBackNewAlertState() {
        String sensorId = "PERSISTENCE-ROLLBACK-NEW";
        TelemetryRequest invalidReading = reading(sensorId, 81.0);
        invalidReading.setLocation("x".repeat(256));

        assertThrows(DataIntegrityViolationException.class,
                () -> telemetryService.processTelemetry(invalidReading));

        assertFalse(alertStateRepository.existsById(sensorId));
        assertTrue(readingsFor(sensorId).isEmpty());
    }

    @Test
    void failedTelemetryInsertRollsBackChangesToExistingAlertState() {
        String sensorId = "PERSISTENCE-ROLLBACK-EXISTING";
        TemperatureAlertState existingState = new TemperatureAlertState();
        existingState.setSensorId(sensorId);
        existingState.setConsecutiveBreaches(2);
        alertStateRepository.saveAndFlush(existingState);

        TelemetryRequest invalidReading = reading(sensorId, 81.0);
        invalidReading.setLocation("x".repeat(256));

        assertThrows(DataIntegrityViolationException.class,
                () -> telemetryService.processTelemetry(invalidReading));

        assertEquals(2, persistedBreaches(sensorId));
        assertTrue(readingsFor(sensorId).isEmpty());
    }

    private int persistedBreaches(String sensorId) {
        return alertStateRepository.findById(sensorId)
                .orElseThrow(() -> new AssertionError("Missing temperature alert state for " + sensorId))
                .getConsecutiveBreaches();
    }

    private List<?> readingsFor(String sensorId) {
        return telemetryRepository.findBySensorIdAndTimestampAfter(
                sensorId,
                LocalDateTime.now().minusMinutes(5));
    }

    private TelemetryRequest reading(String sensorId, double temperature) {
        TelemetryRequest request = new TelemetryRequest();
        request.setSensorId(sensorId);
        request.setLocation("Persistence Test");
        request.setVoltage(230.0);
        request.setCurrent(5.0);
        request.setFrequency(50.0);
        request.setTemperature(temperature);
        request.setVibration(2.5);
        return request;
    }
}
