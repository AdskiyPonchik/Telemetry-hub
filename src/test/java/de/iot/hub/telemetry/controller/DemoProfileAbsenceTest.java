package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.service.AnalyticsService;
import de.iot.hub.telemetry.service.TelemetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
class DemoProfileAbsenceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelemetryService telemetryService;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Test
    void snapshotRouteIs404WithoutDemoProfile() throws Exception {
        mockMvc.perform(get("/api/v1/demo/snapshot"))
                .andExpect(status().isNotFound());
    }
}
