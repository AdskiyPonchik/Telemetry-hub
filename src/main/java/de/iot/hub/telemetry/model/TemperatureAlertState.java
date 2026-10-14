package de.iot.hub.telemetry.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "temperature_alert_states")
@Getter
@Setter
@NoArgsConstructor
public class TemperatureAlertState {

    @Id
    @Column(name = "sensor_id", length = 255)
    private String sensorId;

    @Column(name = "consecutive_breaches", nullable = false)
    private int consecutiveBreaches;
}