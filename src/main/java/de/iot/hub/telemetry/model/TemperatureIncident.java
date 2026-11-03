package de.iot.hub.telemetry.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "temperature_incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemperatureIncident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String sensorId;
    private Instant openedAt;
    private Instant acknowledgedAt;
    private Instant recoveredAt;
    private Double thresholdAtOpen;
    private Double temperatureAtOpen;
    @Column(nullable = false)
    private boolean migrated;
}
