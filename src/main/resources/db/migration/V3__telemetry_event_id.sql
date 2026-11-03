-- Historical rows deliberately retain NULL event IDs.
ALTER TABLE telemetry_data ADD COLUMN event_id UUID;
ALTER TABLE telemetry_data ADD CONSTRAINT uq_telemetry_sensor_event UNIQUE (sensor_id, event_id);
