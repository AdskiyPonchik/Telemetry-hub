package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.dto.TelemetryIngestionResult;
import de.iot.hub.telemetry.dto.TelemetryReceiptResponse;
import de.iot.hub.telemetry.exception.TelemetryEventConflictException;
import de.iot.hub.telemetry.dto.TelemetryResponse;
import de.iot.hub.telemetry.model.TelemetryStatus;
import de.iot.hub.telemetry.service.TelemetryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    private static final UUID EVENT_ID = UUID.fromString("018f6d2a-7a01-4000-8000-000000000001");
    private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 10, 23, 12, 0);

    private static final String VALID_BODY = """
            {
              "eventId": "018f6d2a-7a01-4000-8000-000000000001",
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
        when(service.processTelemetry(any())).thenReturn(new TelemetryIngestionResult(receipt(), true));
        mockMvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.sensorId").value("MGD-PWR-01"))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-23T12:00:00"));

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

        mockMvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(invalidBody)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Validation Failed")).andExpect(jsonPath("$.errors.sensorId").exists()).andExpect(jsonPath("$.errors.voltage").exists());

        verifyNoInteractions(service);
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content("{ this is not json")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid JSON format or data types"));
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> nonFiniteMeasurements() {
        return java.util.stream.Stream.of("voltage", "current", "frequency", "temperature", "vibration")
                .flatMap(field -> java.util.stream.Stream.of("\"NaN\"", "\"Infinity\"", "\"-Infinity\"", "1e309")
                        .map(value -> org.junit.jupiter.params.provider.Arguments.of(field, value)));
    }

    @ParameterizedTest
    @MethodSource("nonFiniteMeasurements")
    void nonFiniteMeasurementsReturn400BeforeService(String field, String jsonValue) throws Exception {
        String body = VALID_BODY.replaceFirst("\"" + field + "\": [0-9.]+",
                "\"" + field + "\": " + jsonValue);
        mockMvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void unexpectedServiceFailure_returns500WithErrorBody() throws Exception {
        doThrow(new RuntimeException("database unavailable")).when(service).processTelemetry(any(TelemetryRequest.class));

        mockMvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("An internal server error occurred"));
    }

    @Test
    void listReadings_returnsPageOfDtos() throws Exception {
        TelemetryResponse response = new TelemetryResponse(1L, "MGD-PWR-01", "Magdeburg-Nord", 230.0, 5.0, 50.0, 45.0, 2.5, TelemetryStatus.OK, LocalDateTime.now());
        when(service.getAll(any())).thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/telemetry").param("page", "0").param("size", "5")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].sensorId").value("MGD-PWR-01")).andExpect(jsonPath("$.content[0].status").value("OK"));
    }

    @Test
    void sensorIdExceedsMaxLength_returns400AndRejectsRequest() throws Exception {
        String tooLongSensorId = "A".repeat(256);
        String invalidBody = """
                {
                  "eventId": "018f6d2a-7a01-4000-8000-000000000001",
                  "sensorId": "%s",
                  "location": "Magdeburg-Nord",
                  "voltage": 230.0,
                  "current": 5.0,
                  "frequency": 50.0,
                  "temperature": 45.0,
                  "vibration": 2.5
                }
                """.formatted(tooLongSensorId);

        mockMvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(invalidBody)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Validation Failed")).andExpect(jsonPath("$.errors.sensorId").exists());

        verifyNoInteractions(service);
    }

    @Test
    void locationExceedsMaxLength_returns400andRejectsRequest() throws Exception {
        String tooLongLocationString = "A".repeat(256);
        String invalidBody = """
                {
                  "eventId": "018f6d2a-7a01-4000-8000-000000000001",
                  "sensorId": "MGD-PWR-01",
                  "location": "%s",
                  "voltage": 230.0,
                  "current": 5.0,
                  "frequency": 50.0,
                  "temperature": 45.0,
                  "vibration": 2.5
                }
                """.formatted(tooLongLocationString);

        mockMvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(invalidBody)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Validation Failed")).andExpect(jsonPath("$.errors.location").exists());
        verifyNoInteractions(service);
    }

    @Test
    void repeatedEvent_returns200WithOriginalReceipt() throws Exception {
        when(service.processTelemetry(any())).thenReturn(new TelemetryIngestionResult(receipt(), false));

        mockMvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.timestamp").value("2026-10-23T12:00:00"));
    }

    @Test
    void reusedEventWithDifferentContent_returns409() throws Exception {
        when(service.processTelemetry(any()))
                .thenThrow(new TelemetryEventConflictException("MGD-PWR-01", EVENT_ID));

        mockMvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void missingEventId_returns400AndDoesNotProcessReading() throws Exception {
        String body = VALID_BODY.replace("\"eventId\": \"" + EVENT_ID + "\",", "");
        mockMvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.eventId").exists());
        verifyNoInteractions(service);
    }

    @Test
    void nullEventId_returns400AndDoesNotProcessReading() throws Exception {
        String body = VALID_BODY.replace("\"" + EVENT_ID + "\"", "null");
        mockMvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.eventId").exists());
        verifyNoInteractions(service);
    }

    @Test
    void malformedEventId_returns400AndDoesNotProcessReading() throws Exception {
        String body = VALID_BODY.replace(EVENT_ID.toString(), "not-a-uuid");
        mockMvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid JSON format or data types"));
        verifyNoInteractions(service);
    }

    private TelemetryReceiptResponse receipt() {
        return new TelemetryReceiptResponse(42L, EVENT_ID, "MGD-PWR-01", TelemetryStatus.OK, RECEIVED_AT);
    }

}
