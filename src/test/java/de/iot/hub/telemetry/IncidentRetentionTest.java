package de.iot.hub.telemetry;

import de.iot.hub.telemetry.config.DemoProperties;
import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.model.TemperatureIncident;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import de.iot.hub.telemetry.repository.TemperatureAlertStateRepository;
import de.iot.hub.telemetry.repository.TemperatureIncidentRepository;
import de.iot.hub.telemetry.service.DemoRetentionService;
import de.iot.hub.telemetry.service.TelemetryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IncidentRetentionTest {
    @Autowired private TelemetryService telemetryService;
    @Autowired private TelemetryRepository readings;
    @Autowired private TemperatureAlertStateRepository states;
    @Autowired private TemperatureIncidentRepository incidents;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void retentionKeepsActiveAndRecentIncidentsAndExpiresDedupOnlyForDeletedReadings() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String sensor = "RETENTION-" + UUID.randomUUID();
        String other = "RETENTION-OTHER-" + UUID.randomUUID();
        var original = reading(sensor);
        var firstReceipt = telemetryService.processTelemetry(original);
        telemetryService.processTelemetry(reading(sensor));
        telemetryService.processTelemetry(reading(sensor));
        var active = incidents.findBySensorIdAndRecoveredAtIsNull(sensor).orElseThrow();
        jdbc.update("UPDATE temperature_incidents SET opened_at=? WHERE id=?",
                now.minus(9, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC), active.getId());
        jdbc.update("UPDATE telemetry_data SET timestamp=? WHERE sensor_id=?",
                LocalDateTime.ofInstant(now.minus(8, ChronoUnit.DAYS), ZoneOffset.UTC), sensor);
        var expired = recovered(sensor, now.minus(8, ChronoUnit.DAYS));
        var recent = recovered(sensor, now.minus(1, ChronoUnit.DAYS));
        var otherSensor = recovered(other, now.minus(8, ChronoUnit.DAYS));
        telemetryService.processTelemetry(reading(other));

        DemoProperties properties = new DemoProperties();
        properties.setSensorId(sensor);
        properties.setRetentionDays(7);
        var retention = new DemoRetentionService(readings, properties, Clock.fixed(now, ZoneOffset.UTC),
                states, incidents);
        int deleted = new TransactionTemplate(transactionManager).execute(
                ignored -> retention.deleteExpiredReadings());
        assertEquals(3, deleted);
        assertTrue(incidents.existsById(active.getId()));
        assertTrue(incidents.existsById(recent.getId()));
        assertFalse(incidents.existsById(expired.getId()));
        assertTrue(incidents.existsById(otherSensor.getId()));
        assertTrue(readings.findBySensorIdAndEventId(other,
                readings.findFirstBySensorIdOrderByTimestampDescIdDesc(other).orElseThrow().getEventId()).isPresent());
        assertFalse(readings.findBySensorIdAndEventId(sensor, original.getEventId()).isPresent());

        var acceptedAfterExpiry = telemetryService.processTelemetry(original);
        assertTrue(acceptedAfterExpiry.created());
        assertNotEquals(firstReceipt.receipt().id(), acceptedAfterExpiry.receipt().id());
        assertEquals(active.getId(), incidents.findBySensorIdAndRecoveredAtIsNull(sensor).orElseThrow().getId());
    }

    private TemperatureIncident recovered(String sensor, Instant recoveredAt) {
        return incidents.saveAndFlush(TemperatureIncident.builder().sensorId(sensor)
                .openedAt(recoveredAt.minus(1, ChronoUnit.HOURS)).recoveredAt(recoveredAt)
                .thresholdAtOpen(80.0).temperatureAtOpen(90.0).build());
    }

    private TelemetryRequest reading(String sensor) {
        var input = new TelemetryRequest();
        input.setEventId(UUID.randomUUID());
        input.setSensorId(sensor);
        input.setVoltage(230.0);
        input.setCurrent(5.0);
        input.setFrequency(50.0);
        input.setTemperature(90.0);
        input.setVibration(2.0);
        return input;
    }
}
