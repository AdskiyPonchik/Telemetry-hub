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

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
