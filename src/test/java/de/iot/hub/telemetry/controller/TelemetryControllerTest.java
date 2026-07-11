package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.dto.TelemetryResponse;
import de.iot.hub.telemetry.model.TelemetryStatus;
import de.iot.hub.telemetry.service.TelemetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelemetryController.class)
class TelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelemetryService service;

    private static final String VALID_BODY = """
            {
              "sensorId": "MGD-PWR-01",
              "location": "Magdeburg-Nord",
              "voltage": 230.0,
              "current": 5.0,
              "frequency": 50.0,
              "temperature": 45.0,
              "vibration": 2.5
            }
            """;

    @Test
    void validReading_returns201AndDelegatesToService() throws Exception {
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        verify(service).processTelemetry(any(TelemetryRequest.class));
    }

    @Test
    void invalidReading_returns400WithFieldErrors() throws Exception {
        String invalidBody = """
                {
                  "location": "Magdeburg-Nord",
                  "voltage": -5.0,
                  "current": 5.0,
                  "frequency": 50.0,
                  "temperature": 45.0,
                  "vibration": 2.5
                }
                """;

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.sensorId").exists())
                .andExpect(jsonPath("$.errors.voltage").exists());

        verifyNoInteractions(service);
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid JSON format or data types"));
    }

    @Test
    void unexpectedServiceFailure_returns500WithErrorBody() throws Exception {
        doThrow(new RuntimeException("database unavailable"))
                .when(service).processTelemetry(any(TelemetryRequest.class));

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An internal server error occurred"));
    }

    @Test
    void listReadings_returnsPageOfDtos() throws Exception {
        TelemetryResponse response = new TelemetryResponse(1L, "MGD-PWR-01", "Magdeburg-Nord",
                230.0, 5.0, 50.0, 45.0, 2.5, TelemetryStatus.OK, LocalDateTime.now());
        when(service.getAll(any())).thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/telemetry").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sensorId").value("MGD-PWR-01"))
                .andExpect(jsonPath("$.content[0].status").value("OK"));
    }
}
