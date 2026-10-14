package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.TelemetryProperties;
import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.repository.SensorThresholdRepository;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ClassificationFixtureTest {

    @Test
    void sharedClassificationCasesMatchValidationAndProductionStatusLogic() throws Exception {
        ClassificationFixture fixture = new ObjectMapper().readValue(
                Path.of("tests/fixtures/classification-cases.json"), ClassificationFixture.class);
        TelemetryProperties properties = propertiesFrom(fixture.thresholds());
        TelemetryRepository telemetryRepository = mock(TelemetryRepository.class);
        SensorThresholdRepository thresholdRepository = mock(SensorThresholdRepository.class);
        TelemetryService service = new TelemetryService(telemetryRepository, properties, thresholdRepository);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            for (FixtureCase fixtureCase : fixture.cases()) {
                clearInvocations(telemetryRepository, thresholdRepository);
                Set<ConstraintViolation<TelemetryRequest>> violations = validator.validate(fixtureCase.input());

                if (fixtureCase.httpStatus() == 400) {
                    assertFalse(violations.isEmpty(), fixtureCase.name());
                    assertNull(fixtureCase.status(), fixtureCase.name());
                    verifyNoInteractions(telemetryRepository, thresholdRepository);
                    continue;
                }

                assertEquals(201, fixtureCase.httpStatus(), fixtureCase.name());
                assertTrue(violations.isEmpty(), fixtureCase.name());
                service.processTelemetry(fixtureCase.input());

                ArgumentCaptor<Telemetry> telemetryCaptor = ArgumentCaptor.forClass(Telemetry.class);
                verify(telemetryRepository).save(telemetryCaptor.capture());
                assertEquals(
                        fixtureCase.status(),
                        telemetryCaptor.getValue().getStatus().name(),
                        fixtureCase.name());
            }
        }
    }

    private TelemetryProperties propertiesFrom(FixtureThresholds thresholds) {
        TelemetryProperties properties = new TelemetryProperties();
        properties.getVoltage().setMin(thresholds.voltageMin());
        properties.getVoltage().setMax(thresholds.voltageMax());
        properties.getFrequency().setMin(thresholds.frequencyMin());
        properties.getFrequency().setMax(thresholds.frequencyMax());
        properties.getThresholds().setDefaultMaxTemperature(thresholds.maxTemperature());
        properties.getThresholds().setDefaultMaxVibration(thresholds.maxVibration());
        return properties;
    }

    private record ClassificationFixture(FixtureThresholds thresholds, List<FixtureCase> cases) {
    }

    private record FixtureThresholds(
            double voltageMin,
            double voltageMax,
            double frequencyMin,
            double frequencyMax,
            double maxTemperature,
            double maxVibration) {
    }

    private record FixtureCase(
            String name,
            TelemetryRequest input,
            int httpStatus,
            String status) {
    }
}
