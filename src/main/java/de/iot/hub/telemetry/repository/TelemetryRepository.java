package de.iot.hub.telemetry.repository;
import de.iot.hub.telemetry.dto.AnalyticsResponse;
import de.iot.hub.telemetry.model.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {
    List<Telemetry> findBySensorIdAndTimestampAfter(String sensorId, LocalDateTime timestamp);

    @Query("SELECT new de.iot.hub.telemetry.dto.AnalyticsResponse(" +
            "t.sensorId, COUNT(t), SUM(CASE WHEN t.status = 'ALARM' THEN 1L ELSE 0L END), " +
            "AVG(t.voltage), AVG(t.frequency)) " +
            "FROM Telemetry t " +
            "WHERE t.sensorId = :sensorId AND t.timestamp >= :since " +
            "GROUP BY t.sensorId")
    AnalyticsResponse getAggregatedStats(@Param("sensorId") String sensorId, @Param("since") LocalDateTime timestamp);
}
