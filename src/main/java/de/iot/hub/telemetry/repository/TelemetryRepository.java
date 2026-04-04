package de.iot.hub.telemetry.repository;
import de.iot.hub.telemetry.model.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {
    List<Telemetry> findBySensorIdAndTimestampAfter(String sensorId, LocalDateTime timestamp);
}
