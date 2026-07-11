package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.dto.AnalyticsResponse;
import de.iot.hub.telemetry.exception.SensorDataNotFoundException;
import de.iot.hub.telemetry.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService service;

    @Test
    void statsForKnownSensor_returnsAggregates() throws Exception {
        when(service.getSensorStats("MGD-PWR-01"))
                .thenReturn(new AnalyticsResponse("MGD-PWR-01", 120, 3, 231.5, 50.01));

        mockMvc.perform(get("/api/v1/analytics/MGD-PWR-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorId").value("MGD-PWR-01"))
                .andExpect(jsonPath("$.totalReadings").value(120))
                .andExpect(jsonPath("$.alarmCount").value(3))
                .andExpect(jsonPath("$.avgVoltage").value(231.5));
    }

    @Test
    void statsForSensorWithoutData_returns404() throws Exception {
        when(service.getSensorStats("UNKNOWN"))
                .thenThrow(new SensorDataNotFoundException("UNKNOWN", 24));

        mockMvc.perform(get("/api/v1/analytics/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
