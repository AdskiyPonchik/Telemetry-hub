package de.iot.hub.telemetry.repository;

import de.iot.hub.telemetry.model.TemperatureAlertState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TemperatureAlertStateRepository extends JpaRepository<TemperatureAlertState, String> {
    @Modifying
    @Query(value = """
            INSERT  INTO temperature_alert_states(sensor_id, consecutive_breaches) VALUES (:sensorID, 0)
            ON CONFLICT (sensor_id) DO NOTHING """, nativeQuery = true)
    void initializeIfAbsent(@Param("sensorID") String sensorID);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT state
            FROM TemperatureAlertState state
            WHERE state.sensorId = :sensorId
            """)
    Optional<TemperatureAlertState> findBySensorIdForUpdate(
            @Param("sensorId") String sensorId
    );
}

