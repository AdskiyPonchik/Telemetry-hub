package de.iot.hub.telemetry;

import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** Upgrades an actual V2 schema, not merely a freshly created latest schema. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IncidentMigrationTest {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void upgradePreservesLegacyRowsAndCountersAndMarksUnknownIncidentHistory() {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("2").load().migrate();
            jdbc.update("INSERT INTO " + schema + ".telemetry_data(sensor_id,location,voltage,timestamp) " +
                    "VALUES ('LEGACY-ACTIVE','Old location',230,'2026-01-02 03:04:05')");
            jdbc.update("INSERT INTO " + schema + ".temperature_alert_states(sensor_id,consecutive_breaches) " +
                    "VALUES ('LEGACY-ACTIVE',3),('LEGACY-PENDING',2)");
            Map<String, Object> oldReading = jdbc.queryForMap(
                    "SELECT id,sensor_id,location,voltage,timestamp FROM " + schema + ".telemetry_data");

            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();

            assertEquals(oldReading, jdbc.queryForMap(
                    "SELECT id,sensor_id,location,voltage,timestamp FROM " + schema + ".telemetry_data"));
            assertNull(jdbc.queryForObject("SELECT event_id FROM " + schema + ".telemetry_data", UUID.class));
            var incident = jdbc.queryForMap("SELECT * FROM " + schema + ".temperature_incidents");
            assertEquals("LEGACY-ACTIVE", incident.get("sensor_id"));
            assertEquals(true, incident.get("migrated"));
            for (String unknown : new String[]{"opened_at", "acknowledged_at", "recovered_at",
                    "threshold_at_open", "temperature_at_open"}) {
                assertNull(incident.get(unknown), unknown);
            }
            assertEquals(3, jdbc.queryForObject("SELECT consecutive_breaches FROM " + schema +
                    ".temperature_alert_states WHERE sensor_id='LEGACY-ACTIVE'", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT consecutive_breaches FROM " + schema +
                    ".temperature_alert_states WHERE sensor_id='LEGACY-PENDING'", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT sum(consecutive_normal_readings) FROM " + schema +
                    ".temperature_alert_states", Integer.class));

            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "INSERT INTO " + schema + ".temperature_incidents(sensor_id,migrated) VALUES ('LEGACY-ACTIVE',true)"));
            UUID eventId = UUID.randomUUID();
            jdbc.update("INSERT INTO " + schema + ".telemetry_data(sensor_id,event_id) VALUES (?,?)",
                    "NEW", eventId);
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "INSERT INTO " + schema + ".telemetry_data(sensor_id,event_id) VALUES (?,?)", "NEW", eventId));
            jdbc.update("INSERT INTO " + schema + ".telemetry_data(sensor_id,event_id) VALUES (?,?)",
                    "OTHER", eventId);
        } finally {
            // This schema belongs exclusively to the disposable Testcontainers database.
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
