package de.iot.hub.telemetry.repository;

import de.iot.hub.telemetry.TestcontainersConfiguration;
import de.iot.hub.telemetry.dto.AnalyticsResponse;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.model.TelemetryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TelemetryRepositoryTest {

    @Autowired
    private TelemetryRepository repository;

    private Telemetry reading(String sensorId, TelemetryStatus status, double voltage, LocalDateTime timestamp) {
        return Telemetry.builder()
                .sensorId(sensorId)
                .location("TEST-HALL")
                .voltage(voltage)
                .current(5.0)
                .frequency(50.0)
                .temperature(40.0)
                .vibration(1.0)
                .status(status)
                .timestamp(timestamp)
                .build();
    }

    @Test
    void aggregatedStats_countCombinedStatusAsAlarm_andRespectTimeWindow() {
        LocalDateTime now = LocalDateTime.now();
        repository.save(reading("S1", TelemetryStatus.OK, 230.0, now.minusHours(1)));
        repository.save(reading("S1", TelemetryStatus.ALARM, 260.0, now.minusHours(2)));
        repository.save(reading("S1", TelemetryStatus.ALARM_AND_MECHANICAL_DAMAGE, 260.0, now.minusHours(3)));
        repository.save(reading("S1", TelemetryStatus.MECHANICAL_DAMAGE, 230.0, now.minusHours(4)));
        // outside the window and from another sensor: both must be ignored
        repository.save(reading("S1", TelemetryStatus.ALARM, 260.0, now.minusHours(30)));
        repository.save(reading("S2", TelemetryStatus.ALARM, 260.0, now.minusHours(1)));

        AnalyticsResponse stats = repository.getAggregatedStats("S1", now.minusHours(24));

        assertEquals("S1", stats.getSensorId());
        assertEquals(4, stats.getTotalReadings());
        assertEquals(2, stats.getAlarmCount()); // ALARM + ALARM_AND_MECHANICAL_DAMAGE
        assertEquals(245.0, stats.getAvgVoltage(), 0.001);
        assertEquals(50.0, stats.getAvgFrequency(), 0.001);
    }

    @Test
    void aggregatedStats_returnNullWhenSensorHasNoDataInWindow() {
        repository.save(reading("S1", TelemetryStatus.ALARM, 260.0, LocalDateTime.now().minusHours(30)));

        assertNull(repository.getAggregatedStats("S1", LocalDateTime.now().minusHours(24)));
        assertNull(repository.getAggregatedStats("UNKNOWN", LocalDateTime.now().minusHours(24)));
    }

    @Test
    void demoQueriesAreSensorScopedBoundedAndStableForTimestampTies() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 13, 12, 0);
        Telemetry stale = repository.save(reading(
                "MGD-PWR-01", TelemetryStatus.OK, 230.0, now.minusHours(2)));
        repository.save(reading("OTHER", TelemetryStatus.ALARM, 260.0, now.minusMinutes(1)));
        repository.save(reading("MGD-PWR-01", TelemetryStatus.OK, 230.0, now.plusSeconds(1)));

        for (int index = 0; index < 181; index++) {
            repository.save(reading(
                    "MGD-PWR-01", TelemetryStatus.OK, 230.0, now.minusMinutes(1)));
        }
        repository.flush();

        Telemetry latest = repository.findFirstBySensorIdOrderByTimestampDescIdDesc("MGD-PWR-01")
                .orElseThrow();
        List<Telemetry> recent = repository.findRecentForSensor(
                "MGD-PWR-01", now.minusMinutes(15), now, PageRequest.of(0, 180));

        assertEquals(now.plusSeconds(1), latest.getTimestamp());
        assertEquals(180, recent.size());
        assertTrue(recent.stream().allMatch(reading -> reading.getSensorId().equals("MGD-PWR-01")));
        assertTrue(recent.stream().allMatch(reading -> !reading.getTimestamp().isAfter(now)));
        assertFalse(recent.stream().anyMatch(reading -> reading.getId().equals(stale.getId())));
        for (int index = 1; index < recent.size(); index++) {
            Telemetry previous = recent.get(index - 1);
            Telemetry current = recent.get(index);
            assertTrue(previous.getTimestamp().isAfter(current.getTimestamp())
                    || previous.getTimestamp().isEqual(current.getTimestamp())
                    && previous.getId() > current.getId());
        }
    }

    @Test
    void retentionDeletesOnlyConfiguredSensorStrictlyBeforeCutoff() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 6, 12, 0);
        Telemetry expired = repository.save(reading(
                "MGD-PWR-01", TelemetryStatus.OK, 230.0, cutoff.minusNanos(1_000)));
        Telemetry exactlyAtCutoff = repository.save(reading(
                "MGD-PWR-01", TelemetryStatus.OK, 230.0, cutoff));
        Telemetry otherSensor = repository.save(reading(
                "OTHER", TelemetryStatus.OK, 230.0, cutoff.minusDays(1)));
        repository.flush();

        assertEquals(1, repository.deleteDemoReadingsOlderThan("MGD-PWR-01", cutoff));

        assertFalse(repository.existsById(expired.getId()));
        assertTrue(repository.existsById(exactlyAtCutoff.getId()));
        assertTrue(repository.existsById(otherSensor.getId()));
    }
}
