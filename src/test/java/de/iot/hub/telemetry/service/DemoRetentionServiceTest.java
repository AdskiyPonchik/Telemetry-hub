package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.DemoProperties;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import de.iot.hub.telemetry.repository.TemperatureAlertStateRepository;
import de.iot.hub.telemetry.repository.TemperatureIncidentRepository;
import static org.mockito.Mockito.inOrder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoRetentionServiceTest {

    @Test
    void deletesOnlyConfiguredSensorBeforeExactRetentionCutoff() {
        TelemetryRepository repository = mock(TelemetryRepository.class);
        TemperatureAlertStateRepository states = mock(TemperatureAlertStateRepository.class);
        TemperatureIncidentRepository incidents = mock(TemperatureIncidentRepository.class);
        DemoProperties properties = new DemoProperties();
        properties.setSensorId("MGD-PWR-01");
        properties.setRetentionDays(7);
        Instant now = Instant.parse("2026-10-13T12:34:56Z");
        LocalDateTime expectedCutoff = LocalDateTime.ofInstant(now, ZoneOffset.UTC).minusDays(7);
        when(repository.deleteDemoReadingsOlderThan("MGD-PWR-01", expectedCutoff)).thenReturn(12);
        DemoRetentionService service = new DemoRetentionService(
                repository, properties, Clock.fixed(now, ZoneOffset.UTC), states, incidents);

        assertEquals(12, service.deleteExpiredReadings());

        var order = inOrder(states, repository, incidents);
        order.verify(states).findBySensorIdForUpdate("MGD-PWR-01");
        order.verify(repository).deleteDemoReadingsOlderThan("MGD-PWR-01", expectedCutoff);
        order.verify(incidents).deleteRecoveredOlderThan("MGD-PWR-01", now.minusSeconds(7 * 24 * 3600));
    }
}
