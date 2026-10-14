package de.iot.hub.telemetry.config;

import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemoConfigurationTest {

    @Test
    void utcTimeZoneIsAccepted() {
        assertDoesNotThrow(() -> DemoConfiguration.requireUtc(ZoneOffset.UTC));
        assertDoesNotThrow(() -> DemoConfiguration.requireUtc(ZoneId.of("Etc/UTC")));
    }

    @Test
    void nonUtcTimeZoneFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> DemoConfiguration.requireUtc(ZoneId.of("Europe/Berlin")));
    }
}
