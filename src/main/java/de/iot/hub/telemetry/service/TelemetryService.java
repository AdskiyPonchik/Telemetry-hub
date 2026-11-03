package de.iot.hub.telemetry.service;

import de.iot.hub.telemetry.config.TelemetryProperties;
import de.iot.hub.telemetry.dto.TelemetryRequest;
import de.iot.hub.telemetry.dto.TelemetryResponse;
import de.iot.hub.telemetry.dto.TelemetryReceiptResponse;
import de.iot.hub.telemetry.dto.TelemetryIngestionResult;
import de.iot.hub.telemetry.exception.TelemetryEventConflictException;
import de.iot.hub.telemetry.model.TemperatureIncident;
import de.iot.hub.telemetry.repository.TemperatureIncidentRepository;
import de.iot.hub.telemetry.model.SensorThresholds;
import de.iot.hub.telemetry.model.Telemetry;
import de.iot.hub.telemetry.model.TelemetryStatus;
import de.iot.hub.telemetry.model.TemperatureAlertState;
import de.iot.hub.telemetry.repository.SensorThresholdRepository;
import de.iot.hub.telemetry.repository.TelemetryRepository;
import de.iot.hub.telemetry.repository.TemperatureAlertStateRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {
    private final TelemetryRepository repository;
    private final TelemetryProperties properties;
    private final SensorThresholdRepository thresholdRepository;
    private final TemperatureAlertEvaluator evaluator;
    private final TemperatureAlertStateRepository temperatureAlertStateRepository;
    private final TemperatureIncidentRepository incidentRepository;
    private final Clock clock;


    @Transactional
    public TelemetryIngestionResult processTelemetry(TelemetryRequest request) {
        // The sensor row is the ordering boundary for ingestion, retries and retention.
        temperatureAlertStateRepository.initializeIfAbsent(request.getSensorId());
        TemperatureAlertState state = temperatureAlertStateRepository
                .findBySensorIdForUpdate(request.getSensorId()).orElseThrow();
        var existing = repository.findBySensorIdAndEventId(request.getSensorId(), request.getEventId());
        if (existing.isPresent()) {
            if (!sameContent(existing.get(), request)) {
                throw new TelemetryEventConflictException(request.getSensorId(), request.getEventId());
            }
            return new TelemetryIngestionResult(receipt(existing.get()), false);
        }

        SensorThresholds config = thresholdRepository.findById(request.getSensorId())
                .orElseGet(() -> new SensorThresholds(
                        request.getSensorId(),
                        properties.getThresholds().getDefaultMaxTemperature(),
                        properties.getThresholds().getDefaultMaxVibration()));

        TelemetryStatus finalStatus = statusCheck(request.getVoltage(), request.getFrequency(),
                request.getTemperature(), request.getVibration(), config);

        if (finalStatus != TelemetryStatus.OK) {
            log.warn("Sensor {} reported abnormal reading: status={}, voltage={}, frequency={}, temperature={}, vibration={}",
                    request.getSensorId(), finalStatus, request.getVoltage(), request.getFrequency(),
                    request.getTemperature(), request.getVibration());
        }

        Telemetry telemetry = Telemetry.builder()
                .eventId(request.getEventId())
                .sensorId(request.getSensorId())
                .location(request.getLocation())
                .voltage(request.getVoltage())
                .current(request.getCurrent())
                .frequency(request.getFrequency())
                .temperature(request.getTemperature())
                .vibration(request.getVibration())
                .status(finalStatus)
                .timestamp(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS))
                .build();

        updateTemperatureAlert(request, config, state);
        Telemetry stored = repository.saveAndFlush(telemetry);
        return new TelemetryIngestionResult(receipt(stored), true);
    }

    private TelemetryStatus statusCheck(Double voltage, Double frequency, Double temperature,
                                        Double vibration, SensorThresholds config) {
        boolean electricalAlarm = voltage < properties.getVoltage().getMin() ||
                voltage > properties.getVoltage().getMax() ||
                frequency < properties.getFrequency().getMin() ||
                frequency > properties.getFrequency().getMax();

        boolean mechanicalDamage = temperature > config.getMaxTemperature() ||
                vibration > config.getMaxVibration();

        if (electricalAlarm && mechanicalDamage) {
            return TelemetryStatus.ALARM_AND_MECHANICAL_DAMAGE;
        }
        if (electricalAlarm) {
            return TelemetryStatus.ALARM;
        }
        if (mechanicalDamage) {
            return TelemetryStatus.MECHANICAL_DAMAGE;
        }

        return TelemetryStatus.OK;
    }

    public Page<TelemetryResponse> getAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::convertToResponse);
    }


    private TelemetryResponse convertToResponse(Telemetry telemetry) {
        return new TelemetryResponse(
                telemetry.getId(),
                telemetry.getSensorId(),
                telemetry.getLocation(),
                telemetry.getVoltage(),
                telemetry.getCurrent(),
                telemetry.getFrequency(),
                telemetry.getTemperature(),
                telemetry.getVibration(),
                telemetry.getStatus(),
                telemetry.getTimestamp());
    }

    private void updateTemperatureAlert(TelemetryRequest request, SensorThresholds config,
                                        TemperatureAlertState state) {
        var active = incidentRepository.findActiveForUpdate(request.getSensorId());
        var next = evaluator.evaluateLifecycle(request.getTemperature(), config.getMaxTemperature(),
                state.getConsecutiveBreaches(), state.getConsecutiveNormalReadings(), active.isPresent());
        state.setConsecutiveBreaches(next.consecutiveBreaches());
        state.setConsecutiveNormalReadings(next.consecutiveNormalReadings());
        if (next.openIncident()) {
            incidentRepository.save(TemperatureIncident.builder()
                    .sensorId(request.getSensorId())
                    .openedAt(clock.instant().truncatedTo(ChronoUnit.MICROS))
                    .thresholdAtOpen(config.getMaxTemperature())
                    .temperatureAtOpen(request.getTemperature())
                    .migrated(false)
                    .build());
        } else if (next.recoverIncident()) {
            active.orElseThrow().setRecoveredAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        }
    }

    private boolean sameContent(Telemetry stored, TelemetryRequest incoming) {
        return Objects.equals(stored.getLocation(), incoming.getLocation())
                && Objects.equals(stored.getVoltage(), incoming.getVoltage())
                && Objects.equals(stored.getCurrent(), incoming.getCurrent())
                && Objects.equals(stored.getFrequency(), incoming.getFrequency())
                && Objects.equals(stored.getTemperature(), incoming.getTemperature())
                && Objects.equals(stored.getVibration(), incoming.getVibration());
    }

    private TelemetryReceiptResponse receipt(Telemetry telemetry) {
        return new TelemetryReceiptResponse(telemetry.getId(), telemetry.getEventId(),
                telemetry.getSensorId(), telemetry.getStatus(), telemetry.getTimestamp());
    }
}
