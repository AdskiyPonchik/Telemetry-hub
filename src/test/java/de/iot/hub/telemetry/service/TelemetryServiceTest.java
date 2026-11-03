package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.TelemetryProperties;
import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.model.SensorThresholds;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.model.TelemetryStatus;
import de.iot.hub.telemetry.model.TemperatureAlertState;
import de.iot.hub.telemetry.model.TemperatureIncident;
import de.iot.hub.telemetry.repository.TemperatureIncidentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import de.iot.hub.telemetry.repository.SensorThresholdRepository;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import de.iot.hub.telemetry.repository.TemperatureAlertStateRepository;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class TelemetryServiceTest {

    @Mock
    private TelemetryRepository repo;

    @Mock
    private SensorThresholdRepository thresholdRepo;

    @Mock
    private TemperatureAlertStateRepository stateRepo;

    @Mock
    private TemperatureIncidentRepository incidentRepo;

    private TelemetryService service;

    @BeforeEach
    void setUp() {
        TelemetryProperties props = new TelemetryProperties();
        props.getVoltage().setMin(207.0);
        props.getVoltage().setMax(253.0);
        props.getFrequency().setMin(49.0);
        props.getFrequency().setMax(51.0);
        props.getThresholds().setDefaultMaxTemperature(80.0);
        props.getThresholds().setDefaultMaxVibration(10.0);

        service = new TelemetryService(repo, props, thresholdRepo,
                new TemperatureAlertEvaluator(), stateRepo, incidentRepo,
                Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC));
        Mockito.when(repo.saveAndFlush(Mockito.any(Telemetry.class))).thenAnswer(invocation -> {
            Telemetry telemetry = invocation.getArgument(0);
            telemetry.setId(42L);
            return telemetry;
        });
        Mockito.when(stateRepo.findBySensorIdForUpdate(Mockito.anyString())).thenAnswer(invocation -> {
            TemperatureAlertState state = new TemperatureAlertState();
            state.setSensorId(invocation.getArgument(0));
            return Optional.of(state);
        });
    }

    private void verifySavedTelemetry(Consumer<Telemetry> assertions) {
        ArgumentCaptor<Telemetry> captor = ArgumentCaptor.forClass(Telemetry.class);
        verify(repo).saveAndFlush(captor.capture());
        assertions.accept(captor.getValue());
    }

    @Test
    void whenSensorAlreadyHasTwoBreaches_thenThirdReadingActivatesAlert() {
        TemperatureAlertState state = new TemperatureAlertState();
        state.setSensorId("STREAK");
        state.setConsecutiveBreaches(2);
        Mockito.when(stateRepo.findBySensorIdForUpdate("STREAK")).thenReturn(Optional.of(state));
        TelemetryRequest request = new TelemetryRequest();
        request.setEventId(UUID.randomUUID());
        request.setSensorId("STREAK");
        request.setVoltage(230.0);
        request.setFrequency(50.0);
        request.setCurrent(1.0);
        request.setTemperature(81.0);
        request.setVibration(2.4);

        service.processTelemetry(request);

        assertEquals(3, state.getConsecutiveBreaches());
        var order = Mockito.inOrder(stateRepo, incidentRepo, repo);
        order.verify(stateRepo).initializeIfAbsent("STREAK");
        order.verify(stateRepo).findBySensorIdForUpdate("STREAK");
        order.verify(incidentRepo).findActiveForUpdate("STREAK");
        ArgumentCaptor<TemperatureIncident> incidentCaptor = ArgumentCaptor.forClass(TemperatureIncident.class);
        order.verify(incidentRepo).save(incidentCaptor.capture());
        assertEquals(80.0, incidentCaptor.getValue().getThresholdAtOpen());
        assertEquals(81.0, incidentCaptor.getValue().getTemperatureAtOpen());
        order.verify(repo).saveAndFlush(Mockito.any(Telemetry.class));
    }

    @Test
    void whenVoltageIsHigh_thenStatusShouldBeAlarm() {
        TelemetryRequest request = new TelemetryRequest();
        request.setEventId(UUID.randomUUID());
        request.setSensorId("TEST-01");
        request.setVoltage(260.0);
        request.setFrequency(50.0);
        request.setCurrent(1.0);
        request.setTemperature(50.0);
        request.setVibration(2.4);

        service.processTelemetry(request);

        verifySavedTelemetry(telemetry -> {
            assertEquals(TelemetryStatus.ALARM, telemetry.getStatus());
        });
    }

    @Test
    void whenFrequencyIsLow_thenStatusShouldBeAlarm() {
        TelemetryRequest request = new TelemetryRequest();
        request.setEventId(UUID.randomUUID());
        request.setSensorId("TEST-02");
        request.setVoltage(230.0);
        request.setFrequency(48.9);
        request.setCurrent(1.0);
        request.setTemperature(50.0);
        request.setVibration(2.4);

        service.processTelemetry(request);

        verifySavedTelemetry(telemetry -> {
            assertEquals(TelemetryStatus.ALARM, telemetry.getStatus());
        });
    }

    @Test
    void everythingShouldBeNormal() {
        TelemetryRequest request = new TelemetryRequest();
        request.setEventId(UUID.randomUUID());
        request.setSensorId("TEST-03");
        request.setVoltage(230.0);
        request.setFrequency(50.0);
        request.setCurrent(1.0);
        request.setTemperature(50.0);
        request.setVibration(2.4);

        service.processTelemetry(request);

        verifySavedTelemetry(telemetry -> {
            assertEquals(TelemetryStatus.OK, telemetry.getStatus());
        });
    }

    @Test
    void whenVoltageAndTemperatureAreHigh_thenStatusShouldBeCombined() {
        TelemetryRequest request = new TelemetryRequest();
        request.setEventId(UUID.randomUUID());
        request.setSensorId("TEST-04");
        request.setVoltage(260.0);
        request.setFrequency(50.0);
        request.setCurrent(1.0);
        request.setTemperature(120.0);
        request.setVibration(2.4);

        service.processTelemetry(request);

        verifySavedTelemetry(telemetry -> {
            assertEquals(TelemetryStatus.ALARM_AND_MECHANICAL_DAMAGE, telemetry.getStatus());
        });
    }

    @Test
    void whenSensorHasCustomThresholds_thenStatusShouldBeOk() {
        TelemetryRequest request = new TelemetryRequest();
        request.setEventId(UUID.randomUUID());
        request.setSensorId("TEST-CUSTOM");
        request.setVoltage(230.0);
        request.setFrequency(50.0);
        request.setCurrent(1.0);
        request.setTemperature(120.0);
        request.setVibration(2.4);

        SensorThresholds customConfig = new SensorThresholds("TEST-CUSTOM", 150.0, 15.0);
        TemperatureAlertState state = new TemperatureAlertState();
        state.setSensorId("TEST-CUSTOM");
        Mockito.when(stateRepo.findBySensorIdForUpdate("TEST-CUSTOM")).thenReturn(Optional.of(state));

        Mockito.when(thresholdRepo.findById("TEST-CUSTOM"))
                .thenReturn(Optional.of(customConfig));

        service.processTelemetry(request);

        verifySavedTelemetry(telemetry -> {
            assertEquals(TelemetryStatus.OK, telemetry.getStatus());
        });
        assertEquals(0, state.getConsecutiveBreaches());
        Mockito.verify(incidentRepo, Mockito.never()).save(Mockito.any());
    }

}
