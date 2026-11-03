package de.iot.hub.telemetry.controller;

import de.iot.hub.telemetry.model.TemperatureIncident;
import de.iot.hub.telemetry.repository.TemperatureIncidentRepository;
import de.iot.hub.telemetry.service.TemperatureIncidentService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemperatureIncidentController.class)
@Import(TemperatureIncidentService.class)
class TemperatureIncidentControllerTest {
    private static final String ROUTE = "/api/v1/temperature-incidents";
    private static final Instant OPENED = Instant.parse("2026-10-23T10:00:00Z");
    private static final Instant ACKNOWLEDGED = Instant.parse("2026-10-23T10:01:00Z");

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private TemperatureIncidentRepository repository;
    @MockitoBean
    private Clock clock;

    @Test
    void listUsesDefaultPagingAndIncludesLifecycleFields() throws Exception {
        when(repository.findBySensorIdOrderByIdDesc(eq("SENSOR"), any()))
                .thenReturn(new PageImpl<>(List.of(incident()), PageRequest.of(0, 20), 1));

        mvc.perform(get(ROUTE).param("sensorId", "SENSOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].sensorId").value("SENSOR"))
                .andExpect(jsonPath("$.content[0].openedAt").value(OPENED.toString()))
                .andExpect(jsonPath("$.content[0].thresholdAtOpen").value(80))
                .andExpect(jsonPath("$.content[0].temperatureAtOpen").value(85))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.content[0].migrated").value(false));
        verify(repository).findBySensorIdOrderByIdDesc("SENSOR", PageRequest.of(0, 20));
    }

    @Test
    void explicitPagingAcceptsMaximumSize() throws Exception {
        when(repository.findBySensorIdOrderByIdDesc(eq("SENSOR"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 100), 0));
        mvc.perform(get(ROUTE).param("sensorId", "SENSOR").param("page", "2").param("size", "100"))
                .andExpect(status().isOk());
        verify(repository).findBySensorIdOrderByIdDesc("SENSOR", PageRequest.of(2, 100));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "bad,20", "0,bad"})
    void invalidPagingReturns400WithoutQueryingRepository(String page, String size) throws Exception {
        mvc.perform(get(ROUTE).param("sensorId", "SENSOR").param("page", page).param("size", size))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void missingOrBlankSensorReturns400() throws Exception {
        mvc.perform(get(ROUTE)).andExpect(status().isBadRequest());
        mvc.perform(get(ROUTE).param("sensorId", "  ")).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void getReturnsIncidentAndUnknownIdReturns404() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(incident()));
        mvc.perform(get(ROUTE + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get(ROUTE + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void acknowledgementKeepsIncidentActiveAndPreservesFirstTimestampOnRepeat() throws Exception {
        TemperatureIncident incident = incident();
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(clock.instant()).thenReturn(ACKNOWLEDGED, ACKNOWLEDGED.plusSeconds(30));

        for (int i = 0; i < 2; i++) {
            mvc.perform(put(ROUTE + "/7/acknowledgement"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.acknowledgedAt").value(ACKNOWLEDGED.toString()))
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.recoveredAt").isEmpty());
        }
        verify(clock).instant();
    }

    @Test
    void recoveredIncidentCanBeAcknowledgedWithoutChangingRecovery() throws Exception {
        TemperatureIncident incident = incident();
        Instant recovered = OPENED.plusSeconds(60);
        incident.setRecoveredAt(recovered);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(clock.instant()).thenReturn(ACKNOWLEDGED);

        mvc.perform(put(ROUTE + "/7/acknowledgement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.recoveredAt").value(recovered.toString()))
                .andExpect(jsonPath("$.acknowledgedAt").value(ACKNOWLEDGED.toString()));
    }

    @Test
    void acknowledgingUnknownIncidentReturns404() throws Exception {
        mvc.perform(put(ROUTE + "/999/acknowledgement"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        verifyNoInteractions(clock);
    }

    private TemperatureIncident incident() {
        return TemperatureIncident.builder().id(7L).sensorId("SENSOR").openedAt(OPENED)
                .thresholdAtOpen(80.0).temperatureAtOpen(85.0).build();
    }
}
