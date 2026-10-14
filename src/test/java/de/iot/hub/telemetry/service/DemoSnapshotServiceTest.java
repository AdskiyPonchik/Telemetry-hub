package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.DemoProperties;
import de.iot.hub.telemetry.config.TelemetryProperties;
import de.iot.hub.telemetry.dto.AnalyticsResponse;
import de.iot.hub.telemetry.dto.DemoSnapshotResponse;
import de.iot.hub.telemetry.model.SensorThresholds;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.model.TelemetryStatus;
import de.iot.hub.telemetry.repository.SensorThresholdRepository;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoSnapshotServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");

    @Mock
    private TelemetryRepository telemetryRepository;

    @Mock
    private SensorThresholdRepository thresholdRepository;

    private DemoSnapshotService service;

    @BeforeEach
    void setUp() {
        TelemetryProperties telemetryProperties = new TelemetryProperties();
        telemetryProperties.getVoltage().setMin(198.0);
        telemetryProperties.getVoltage().setMax(242.0);
        telemetryProperties.getFrequency().setMin(49.0);
        telemetryProperties.getFrequency().setMax(51.0);
        telemetryProperties.getThresholds().setDefaultMaxTemperature(80.0);
        telemetryProperties.getThresholds().setDefaultMaxVibration(10.0);
        telemetryProperties.getAnalytics().setWindowHours(24);

        DemoProperties demoProperties = new DemoProperties();
        service = new DemoSnapshotService(
                telemetryRepository,
                thresholdRepository,
                telemetryProperties,
                demoProperties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void emptyDatabase_returnsNullLatestAndAnalyticsWithEmptyReadings() {
        when(thresholdRepository.findById("MGD-PWR-01")).thenReturn(Optional.empty());
        when(telemetryRepository.findFirstBySensorIdOrderByTimestampDescIdDesc("MGD-PWR-01"))
                .thenReturn(Optional.empty());
        when(telemetryRepository.findRecentForSensor(eq("MGD-PWR-01"), any(), any(), any()))
                .thenReturn(List.of());
        when(telemetryRepository.getAggregatedStats(eq("MGD-PWR-01"), any())).thenReturn(null);

        DemoSnapshotResponse snapshot = service.getSnapshot();

        assertEquals(NOW, snapshot.generatedAt());
        assertNull(snapshot.latest());
        assertEquals(List.of(), snapshot.readings());
        assertNull(snapshot.analytics());
        assertEquals(80.0, snapshot.thresholds().maxTemperature());
        assertEquals(10.0, snapshot.thresholds().maxVibration());
    }

    @Test
    void snapshotUsesEffectiveThresholdsAndOrdersRecentReadingsAscending() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        Telemetry newerSameTime = reading(3L, now.minusMinutes(1));
        Telemetry olderSameTime = reading(2L, now.minusMinutes(1));
        AnalyticsResponse analytics = new AnalyticsResponse("MGD-PWR-01", 3, 1, 235, 50);

        when(thresholdRepository.findById("MGD-PWR-01"))
                .thenReturn(Optional.of(new SensorThresholds("MGD-PWR-01", 95.0, 14.0)));
        when(telemetryRepository.findFirstBySensorIdOrderByTimestampDescIdDesc("MGD-PWR-01"))
                .thenReturn(Optional.of(newerSameTime));
        when(telemetryRepository.findRecentForSensor(eq("MGD-PWR-01"), any(), any(), any()))
                .thenReturn(List.of(newerSameTime, olderSameTime));
        when(telemetryRepository.getAggregatedStats(eq("MGD-PWR-01"), any())).thenReturn(analytics);

        DemoSnapshotResponse snapshot = service.getSnapshot();

        assertEquals(3L, snapshot.latest().id());
        assertEquals(List.of(2L, 3L), snapshot.readings().stream().map(reading -> reading.id()).toList());
        assertEquals(ZoneOffset.UTC, snapshot.latest().timestamp().getOffset());
        assertEquals(95.0, snapshot.thresholds().maxTemperature());
        assertEquals(14.0, snapshot.thresholds().maxVibration());
        assertEquals(1, snapshot.analytics().getAlarmCount());

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(telemetryRepository).findRecentForSensor(
                eq("MGD-PWR-01"), sinceCaptor.capture(), eq(now), pageableCaptor.capture());
        assertEquals(now.minusMinutes(15), sinceCaptor.getValue());
        assertEquals(180, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void latestIsPreservedWhenItIsOlderThanTheFifteenMinuteChartWindow() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        Telemetry stale = reading(1L, now.minusHours(2));
        when(thresholdRepository.findById("MGD-PWR-01")).thenReturn(Optional.empty());
        when(telemetryRepository.findFirstBySensorIdOrderByTimestampDescIdDesc("MGD-PWR-01"))
                .thenReturn(Optional.of(stale));
        when(telemetryRepository.findRecentForSensor(eq("MGD-PWR-01"), any(), any(), any()))
                .thenReturn(List.of());
        when(telemetryRepository.getAggregatedStats(eq("MGD-PWR-01"), any()))
                .thenReturn(new AnalyticsResponse("MGD-PWR-01", 1, 0, 230, 50));

        DemoSnapshotResponse snapshot = service.getSnapshot();

        assertEquals(1L, snapshot.latest().id());
        assertEquals(List.of(), snapshot.readings());
    }

    @Test
    void repositoryFailurePropagates() {
        when(thresholdRepository.findById("MGD-PWR-01"))
                .thenThrow(new RuntimeException("database unavailable"));

        assertThrows(RuntimeException.class, service::getSnapshot);
    }

    private Telemetry reading(long id, LocalDateTime timestamp) {
        return Telemetry.builder()
                .id(id)
                .sensorId("MGD-PWR-01")
                .location("Magdeburg-Nord")
                .voltage(230.0)
                .current(5.0)
                .frequency(50.0)
                .temperature(45.0)
                .vibration(2.5)
                .status(TelemetryStatus.OK)
                .timestamp(timestamp)
                .build();
    }
}
