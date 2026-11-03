package de.iot.hub.telemetry.repository;

import de.iot.hub.telemetry.model.TemperatureIncident;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TemperatureIncidentRepository extends JpaRepository<TemperatureIncident, Long> {
    Page<TemperatureIncident> findBySensorIdOrderByIdDesc(String sensorId, Pageable pageable);

    Optional<TemperatureIncident> findBySensorIdAndRecoveredAtIsNull(String sensorId);

    boolean existsBySensorIdAndRecoveredAtIsNull(String sensorId);

    List<TemperatureIncident> findTop5BySensorIdAndRecoveredAtIsNotNullOrderByIdDesc(String sensorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM TemperatureIncident i WHERE i.sensorId = :sensorId AND i.recoveredAt IS NULL")
    Optional<TemperatureIncident> findActiveForUpdate(@Param("sensorId") String sensorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM TemperatureIncident i WHERE i.id = :id")
    Optional<TemperatureIncident> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM TemperatureIncident i WHERE i.sensorId = :sensorId AND i.recoveredAt < :cutoff")
    int deleteRecoveredOlderThan(@Param("sensorId") String sensorId, @Param("cutoff") Instant cutoff);
}
