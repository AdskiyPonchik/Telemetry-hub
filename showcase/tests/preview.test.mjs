import test from 'node:test';
import assert from 'node:assert/strict';
import {previewSnapshot} from '../preview.mjs';
import {DEFAULT_THRESHOLDS, evaluateReading} from '../rules.mjs';
import {validIncidentState} from '../incidents.mjs';

const now = Date.parse('2026-10-26T12:00:00.000Z');

test('preview is a deterministic bounded snapshot with unique valid accepted readings', () => {
    const snapshot = previewSnapshot(now);
    assert.deepEqual(snapshot, previewSnapshot(now));
    assert.equal(snapshot.generatedAt, new Date(now).toISOString());
    assert.deepEqual(snapshot.sensor, {id: 'MOTOR-001', location: 'Assembly line · Drive unit 04'});
    assert.deepEqual(snapshot.thresholds, DEFAULT_THRESHOLDS);
    assert.equal(validIncidentState(snapshot), true);
    assert.equal(snapshot.readings.length, 180);
    assert.equal(new Set(snapshot.readings.map(row => row.eventId)).size, 180);
    assert.equal(new Set(snapshot.readings.map(row => row.id)).size, 180);
    snapshot.readings.forEach((row, index) => {
        assert.equal(row.sensorId, snapshot.sensor.id);
        assert.equal(row.location, snapshot.sensor.location);
        assert.equal(Date.parse(row.timestamp), now - (179 - index) * 5000);
        assert.ok(row.timestamp.endsWith('Z'));
        assert.ok(Date.parse(row.timestamp) > now - 15 * 60000);
        for (const metric of ['voltage', 'current', 'frequency', 'temperature', 'vibration']) {
            assert.equal(Number.isFinite(row[metric]), true, metric);
        }
        const evaluated = evaluateReading(row, snapshot.thresholds);
        assert.equal(evaluated.httpStatus, 201);
        assert.equal(row.status, evaluated.status);
    });
    assert.deepEqual(snapshot.latest, snapshot.readings.at(-1));
});

test('normal latest values coexist with an active acknowledged incident recovering at one of three', () => {
    const snapshot = previewSnapshot(now), {latest} = snapshot;
    assert.deepEqual(
        [latest.voltage, latest.current, latest.frequency, latest.temperature, latest.vibration, latest.status],
        [228.1, 8.4, 50, 78.4, 4.8, 'OK']
    );
    assert.deepEqual(snapshot.temperatureAlert, {
        sensorID: snapshot.sensor.id, consecutiveBreaches: 0,
        consecutiveNormalReadings: 1, alertActive: true
    });
    const firstHot = snapshot.readings.findIndex(row => row.temperature > 80);
    assert.equal(firstHot, 90);
    assert.ok(snapshot.readings.slice(0, firstHot).every(row => row.temperature >= 71 && row.temperature <= 78));
    assert.ok(snapshot.readings.slice(firstHot, -1).every(row => row.temperature > 80));
    const thirdHot = snapshot.readings[firstHot + 2];
    assert.equal(snapshot.activeIncident.openedAt, thirdHot.timestamp);
    assert.equal(snapshot.activeIncident.temperatureAtOpen, thirdHot.temperature);
    assert.equal(snapshot.activeIncident.thresholdAtOpen, 80);
    assert.equal(snapshot.activeIncident.recoveredAt, null);
    assert.ok(Date.parse(snapshot.activeIncident.acknowledgedAt) > Date.parse(thirdHot.timestamp));
    assert.ok(Date.parse(snapshot.activeIncident.acknowledgedAt) < now);
});

test('recovered history predates the visible window and preserves unknown migrated onset', () => {
    const snapshot = previewSnapshot(now);
    for (const incident of snapshot.recentIncidents) {
        assert.equal(incident.active, false);
        assert.ok(Date.parse(incident.recoveredAt) < now - 15 * 60000);
        if (incident.openedAt !== null) {
            assert.ok(Date.parse(incident.openedAt) < Date.parse(incident.recoveredAt));
        }
    }
    const migrated = snapshot.recentIncidents.find(incident => incident.migrated);
    assert.ok(migrated);
    assert.equal(migrated.openedAt, null);
    assert.equal(migrated.temperatureAtOpen, null);
    assert.equal(migrated.thresholdAtOpen, null);
});

test('illustrative daily analytics remain separate from the chart and refresh shifts all known times', () => {
    const first = previewSnapshot(now), next = previewSnapshot(now + 30000);
    assert.equal(first.analytics.sensorId, first.sensor.id);
    assert.equal(first.analytics.totalReadings, 24 * 60 * 60 / 5);
    assert.ok(first.analytics.alarmCount >= 0 && first.analytics.alarmCount <= first.analytics.totalReadings);
    assert.equal(Number.isFinite(first.analytics.avgVoltage), true);
    assert.equal(Number.isFinite(first.analytics.avgFrequency), true);
    assert.equal(Date.parse(next.latest.timestamp) - Date.parse(first.latest.timestamp), 30000);
    assert.equal(Date.parse(next.activeIncident.openedAt) - Date.parse(first.activeIncident.openedAt), 30000);
    assert.equal(Date.parse(next.recentIncidents[0].recoveredAt) - Date.parse(first.recentIncidents[0].recoveredAt), 30000);
    assert.equal(next.recentIncidents[1].openedAt, null);
});
