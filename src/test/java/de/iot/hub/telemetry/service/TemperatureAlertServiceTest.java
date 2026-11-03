package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.exception.TemperatureAlertNotFoundException;
import de.iot.hub.telemetry.model.TemperatureAlertState;
import de.iot.hub.telemetry.repository.TemperatureAlertStateRepository;
import de.iot.hub.telemetry.repository.TemperatureIncidentRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TemperatureAlertServiceTest {
    private final TemperatureAlertStateRepository states = mock(TemperatureAlertStateRepository.class);
    private final TemperatureIncidentRepository incidents = mock(TemperatureIncidentRepository.class);
    private final TemperatureAlertService service = new TemperatureAlertService(states, incidents);

    @Test
    void activeFlagComesFromPersistentIncidentWhileRecoveryCounterAdvances() {
        TemperatureAlertState state = new TemperatureAlertState();
        state.setSensorId("SENSOR");
        state.setConsecutiveNormalReadings(2);
        when(states.findById("SENSOR")).thenReturn(Optional.of(state));
        when(incidents.existsBySensorIdAndRecoveredAtIsNull("SENSOR")).thenReturn(true);

        var result = service.getState("SENSOR");

        assertEquals("SENSOR", result.sensorID());
        assertEquals(0, result.consecutiveBreaches());
        assertEquals(2, result.consecutiveNormalReadings());
        assertTrue(result.alertActive());
    }

    @Test
    void pendingBreachesDoNotActivateIncident() {
        TemperatureAlertState state = new TemperatureAlertState();
        state.setSensorId("SENSOR");
        state.setConsecutiveBreaches(2);
        when(states.findById("SENSOR")).thenReturn(Optional.of(state));

        assertFalse(service.getState("SENSOR").alertActive());
    }

    @Test
    void unknownSensorThrowsWithoutLookingForIncidents() {
        assertThrows(TemperatureAlertNotFoundException.class, () -> service.getState("UNKNOWN"));
        verifyNoInteractions(incidents);
    }
}
