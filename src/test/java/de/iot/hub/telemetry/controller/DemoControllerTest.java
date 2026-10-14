package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.dto.AnalyticsResponse;
import de.iot.hub.telemetry.dto.DemoReadingResponse;
import de.iot.hub.telemetry.dto.DemoSnapshotResponse;
import de.iot.hub.telemetry.exception.GlobalExceptionHandler;
import de.iot.hub.telemetry.model.TelemetryStatus;
import de.iot.hub.telemetry.service.DemoSnapshotService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
@ActiveProfiles("demo")
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoSnapshotService service;

    @Test
    void snapshot_returnsExactDemoContractWithUtcTimestamps() throws Exception {
        OffsetDateTime timestamp = OffsetDateTime.of(2026, 10, 13, 10, 0, 0, 0, ZoneOffset.UTC);
        DemoReadingResponse reading = new DemoReadingResponse(
                7L, "MGD-PWR-01", "Magdeburg-Nord", 230.0, 5.0, 50.0,
                45.0, 2.5, TelemetryStatus.OK, timestamp);
        DemoSnapshotResponse response = new DemoSnapshotResponse(
                new DemoSnapshotResponse.Sensor("MGD-PWR-01", "Magdeburg-Nord"),
                new DemoSnapshotResponse.Thresholds(198, 242, 49, 51, 80, 10),
                Instant.parse("2026-10-13T10:01:00Z"),
                reading,
                List.of(reading),
                new AnalyticsResponse("MGD-PWR-01", 1, 0, 230, 50));
        when(service.getSnapshot()).thenReturn(response);

        mockMvc.perform(get("/api/v1/demo/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensor.id").value("MGD-PWR-01"))
                .andExpect(jsonPath("$.sensor.location").value("Magdeburg-Nord"))
                .andExpect(jsonPath("$.thresholds.maxVibration").value(10))
                .andExpect(jsonPath("$.generatedAt").value("2026-10-13T10:01:00Z"))
                .andExpect(jsonPath("$.latest.timestamp").value("2026-10-13T10:00:00Z"))
                .andExpect(jsonPath("$.readings[0].id").value(7))
                .andExpect(jsonPath("$.analytics.alarmCount").value(0));
    }

    @Test
    void databaseFailure_returns500InsteadOfAnEmptySnapshot() throws Exception {
        when(service.getSnapshot()).thenThrow(new RuntimeException("database unavailable"));

        mockMvc.perform(get("/api/v1/demo/snapshot"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An internal server error occurred"))
                .andExpect(jsonPath("$.readings").doesNotExist());
    }

    @Test
    void controllerBeanIsAbsentWithoutDemoProfile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            DefaultListableBeanFactory beanFactory = context.getDefaultListableBeanFactory();
            beanFactory.registerSingleton("demoSnapshotService", mock(DemoSnapshotService.class));
            context.register(DemoController.class, GlobalExceptionHandler.class);
            context.refresh();

            assertFalse(context.containsBeanDefinition("demoController"));
        }
    }
}
