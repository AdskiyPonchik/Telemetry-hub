package de.iot.hub.telemetry.config;

import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("demo")
public class DemoConfiguration {

    @Bean
    Clock demoClock() {
        return Clock.systemUTC();
    }

    @PostConstruct
    void requireUtcJvmTimeZone() {
        requireUtc(ZoneId.systemDefault());
    }

    static void requireUtc(ZoneId zone) {
        boolean fixedUtc = zone.getRules().isFixedOffset()
                && zone.getRules().getOffset(Instant.EPOCH).equals(ZoneOffset.UTC);
        if (!fixedUtc) {
            throw new IllegalStateException(
                    "The demo profile requires the JVM default time zone to be UTC; current zone is " + zone);
        }
    }
}
