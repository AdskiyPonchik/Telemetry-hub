package de.iot.hub.telemetry.repository;


import de.iot.hub.telemetry.model.SensorThresholds;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SensorThresholdRepository extends JpaRepository<SensorThresholds, String> {

}
