package de.iot.hub.telemetry.model;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sensor_configs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SensorThresholds {
    @Id
    private String sensorId;

    // Metrics
    private Double maxTemperature;
    private Double maxVibration;
}
