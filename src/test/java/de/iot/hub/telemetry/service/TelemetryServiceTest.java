package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.TelemetryProperties;
import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.model.SensorThresholds;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.repository.SensorThresholdRepository;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;

import java.util.Optional;
import java.util.function.Consumer;


@ExtendWith(MockitoExtension.class)
public class TelemetryServiceTest {

    @Mock
    private TelemetryRepository repo;

    @Mock
    private SensorThresholdRepository thresholdRepo;

    private TelemetryService service;

    @BeforeEach
    void setUp(){
        TelemetryProperties props = new TelemetryProperties();
        props.getVoltage().setMin(207.0);
        props.getVoltage().setMax(253.0);
        props.getFrequency().setMin(49.0);
        props.getFrequency().setMax(51.0);

        service = new TelemetryService(repo, props, thresholdRepo);
    }

    private void verifySavedTelemetry(Consumer<Telemetry> assertions){
        ArgumentCaptor<Telemetry> captor = ArgumentCaptor.forClass(Telemetry.class);
        verify(repo).save(captor.capture());
        assertions.accept(captor.getValue());
    }

    @Test
    void whenVoltageIsHigh_thenStatusShouldBeAlarm(){
        TelemetryRequest request = new TelemetryRequest();
        request.setSensorId("TEST-01");
        request.setVoltage(260.0);
        request.setFrequency(50.0);
        request.setCurrent(1.0);
        request.setStatus("OK");
        request.setTemperature(50.0);
        request.setVibration(2.4);

        service.processTelemetry(request);

        verifySavedTelemetry(telemetry -> {
            assertEquals("ALARM", telemetry.getStatus());
        });
    }

    @Test
    void whenFrequencyIsHigh_thenStatusShouldBeAlarm(){
        TelemetryRequest request = new TelemetryRequest();
        request.setSensorId("TEST-02");
        request.setVoltage(230.0);
        request.setFrequency(48.9);
        request.setCurrent(1.0);
        request.setStatus("OK");
        request.setTemperature(50.0);
        request.setVibration(2.4);


        service.processTelemetry(request);

        verifySavedTelemetry(telemetry -> {
            assertEquals("ALARM", telemetry.getStatus());
        });
    }

    @Test
    void everythingShouldBeNormal(){
        TelemetryRequest request = new TelemetryRequest();
        request.setSensorId("TEST-03");
        request.setVoltage(230.0);
        request.setFrequency(50.0);
        request.setCurrent(1.0);
        request.setStatus("OK");
        request.setTemperature(50.0);
        request.setVibration(2.4);

        service.processTelemetry(request);

        verifySavedTelemetry(telemetry -> {
            assertEquals("OK", telemetry.getStatus());
        });
    }

    @Test
    void whenVoltageAndTemperatureAreHigh_thenStatusShouldBeCombined(){
        TelemetryRequest request = new TelemetryRequest();
        request.setSensorId("TEST-04");
        request.setVoltage(260.0);
        request.setFrequency(50.0);
        request.setCurrent(1.0);
        request.setStatus("OK");
        request.setTemperature(120.0);
        request.setVibration(2.4);

        service.processTelemetry(request);

        verifySavedTelemetry(telemetry -> {
            assertEquals("ALARM_AND_MECHANICAL_DAMAGE", telemetry.getStatus());
        });
    }

    @Test
    void whenSensorHasCustomThresholds_thenStatusShouldBeOk(){
        TelemetryRequest request = new TelemetryRequest();
        request.setSensorId("TEST-CUSTOM");
        request.setVoltage(230.0);
        request.setFrequency(50.0);
        request.setCurrent(1.0);
        request.setStatus("OK");
        request.setTemperature(120.0);
        request.setVibration(2.4);

        SensorThresholds customConfig = new SensorThresholds("TEST-CUSTOM", 150.0, 15.0);

        Mockito.when(thresholdRepo.findById("TEST-CUSTOM"))
                .thenReturn(Optional.of(customConfig));

        service.processTelemetry(request);

        verifySavedTelemetry(telemetry -> {
            assertEquals("OK", telemetry.getStatus());
        });
    }

}
