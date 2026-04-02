package de.iot.hub.telemetry.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;


@Entity
@Table(name = "telemetry_data")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Telemetry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sensorId;
    private String location;
    private String sensorType;

    // Metrics
    private Double voltage;
    private Double current;
    private Double frequency;

    private String status;
    private LocalDateTime timestamp;
}
