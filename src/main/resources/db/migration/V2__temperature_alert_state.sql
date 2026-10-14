CREATE TABLE temperature_alert_states (
                                          sensor_id VARCHAR(255) PRIMARY KEY,
                                          consecutive_breaches INTEGER NOT NULL DEFAULT 0,
                                          CONSTRAINT chk_temperature_alert_breaches
                                              CHECK (consecutive_breaches >= 0)
);