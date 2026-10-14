package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.DemoProperties;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoRetentionServiceTest {

    @Test
    void deletesOnlyConfiguredSensorBeforeExactRetentionCutoff() {
        TelemetryRepository repository = mock(TelemetryRepository.class);
        DemoProperties properties = new DemoProperties();
        properties.setSensorId("MGD-PWR-01");
        properties.setRetentionDays(7);
        Instant now = Instant.parse("2026-10-13T12:34:56Z");
        LocalDateTime expectedCutoff = LocalDateTime.ofInstant(now, ZoneOffset.UTC).minusDays(7);
        when(repository.deleteDemoReadingsOlderThan("MGD-PWR-01", expectedCutoff)).thenReturn(12);
        DemoRetentionService service = new DemoRetentionService(
                repository, properties, Clock.fixed(now, ZoneOffset.UTC));

        assertEquals(12, service.deleteExpiredReadings());

        verify(repository).deleteDemoReadingsOlderThan("MGD-PWR-01", expectedCutoff);
    }
}
