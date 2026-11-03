package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.dto.TemperatureAlertResponse;
import de.iot.hub.telemetry.exception.TemperatureAlertNotFoundException;
import de.iot.hub.telemetry.service.TemperatureAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemperatureAlertController.class)
class TemperatureAlertControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private TemperatureAlertService service;

    @Test
    void stateRetainsSensorIDAndExposesRecoveryCounter() throws Exception {
        when(service.getState("SENSOR"))
                .thenReturn(new TemperatureAlertResponse("SENSOR", 0, true, 2));

        mvc.perform(get("/api/v1/temperature-alerts/SENSOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorID").value("SENSOR"))
                .andExpect(jsonPath("$.consecutiveBreaches").value(0))
                .andExpect(jsonPath("$.alertActive").value(true))
                .andExpect(jsonPath("$.consecutiveNormalReadings").value(2));
    }

    @Test
    void unknownSensorReturns404() throws Exception {
        when(service.getState("UNKNOWN")).thenThrow(new TemperatureAlertNotFoundException("UNKNOWN"));
        mvc.perform(get("/api/v1/temperature-alerts/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
