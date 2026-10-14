package de.iot.hub.telemetry.repository;

import de.iot.hub.telemetry.dto.AnalyticsResponse;
import de.iot.hub.telemetry.model.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {
    List<Telemetry> findBySensorIdAndTimestampAfter(String sensorId, LocalDateTime timestamp);

    Optional<Telemetry> findFirstBySensorIdOrderByTimestampDescIdDesc(String sensorId);

    @Query("SELECT t FROM Telemetry t " +
            "WHERE t.sensorId = :sensorId " +
            "AND t.timestamp >= :since AND t.timestamp <= :until " +
            "ORDER BY t.timestamp DESC, t.id DESC")
    List<Telemetry> findRecentForSensor(
            @Param("sensorId") String sensorId,
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Telemetry t " +
            "WHERE t.sensorId = :sensorId AND t.timestamp < :cutoff")
    int deleteDemoReadingsOlderThan(
            @Param("sensorId") String sensorId,
            @Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT new de.iot.hub.telemetry.dto.AnalyticsResponse(" +
            "t.sensorId, COUNT(t), " +
            "SUM(CASE WHEN t.status IN (" +
            "de.iot.hub.telemetry.model.TelemetryStatus.ALARM, " +
            "de.iot.hub.telemetry.model.TelemetryStatus.ALARM_AND_MECHANICAL_DAMAGE) THEN 1L ELSE 0L END), " +
            "AVG(t.voltage), AVG(t.frequency)) " +
            "FROM Telemetry t " +
            "WHERE t.sensorId = :sensorId AND t.timestamp >= :since " +
            "GROUP BY t.sensorId")
    AnalyticsResponse getAggregatedStats(@Param("sensorId") String sensorId, @Param("since") LocalDateTime timestamp);
}
