package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.TelemetryProperties;
import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.BeforeEach;

import java.util.function.Consumer;


@ExtendWith(MockitoExtension.class)
public class TelemetryServiceTest {

    @Mock
    private TelemetryRepository repo;

    private TelemetryService service;

    @BeforeEach
    void setUp(){
        TelemetryProperties props = new TelemetryProperties();
        props.getVoltage().setMin(207.0);
        props.getVoltage().setMax(253.0);
        props.getFrequency().setMin(49.0);
        props.getFrequency().setMax(51.0);

        service = new TelemetryService(repo, props);
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

        service.processTelemetry(request);

        verifySavedTelemetry(telemetry -> {
            assertEquals("OK", telemetry.getStatus());
        });
    }

}
