import test from 'node:test';
import assert from 'node:assert/strict';
import {validIncident, validIncidentState, incidentTime, renderIncidentPanel} from '../incidents.mjs';
import {messages} from '../i18n.mjs';

const sensorId = 'LINE-7';
const incident = {
    id: 14, sensorId, openedAt: '2026-10-23T10:00:00Z', acknowledgedAt: null,
    recoveredAt: null, thresholdAtOpen: 80, temperatureAtOpen: 87, migrated: false, active: true
};
const recovered = {...incident, id: 13, active: false, recoveredAt: '2026-10-23T10:01:00Z'};
const snapshot = {
    sensor: {id: sensorId},
    temperatureAlert: {sensorID: sensorId, consecutiveBreaches: 0, alertActive: true, consecutiveNormalReadings: 2},
    activeIncident: incident, recentIncidents: [recovered]
};
const t = key => messages.en[key];

test('snapshot includes persisted state, one active incident, and bounded recovered history', () => {
    assert.equal(validIncidentState(snapshot), true);
    assert.equal(validIncidentState({...snapshot, temperatureAlert: null, activeIncident: null, recentIncidents: []}), true);
    for (const key of ['temperatureAlert', 'activeIncident', 'recentIncidents']) {
        const missing = {...snapshot};
        delete missing[key];
        assert.equal(validIncidentState(missing), false, `missing ${key}`);
    }
    assert.equal(validIncidentState({...snapshot, recentIncidents: Array(6).fill(recovered)}), false);
    assert.equal(validIncidentState({...snapshot, recentIncidents: [recovered, {...recovered, id: 14}]}), false);
    assert.equal(validIncidentState({...snapshot, recentIncidents: [incident]}), false);
    assert.equal(validIncidentState({...snapshot, activeIncident: recovered}), false);
});

test('foreign sensors and malformed state cannot replace a valid cached snapshot', () => {
    assert.equal(validIncidentState({...snapshot, activeIncident: {...incident, sensorId: 'other'}}), false);
    assert.equal(validIncidentState({...snapshot, temperatureAlert: {...snapshot.temperatureAlert, sensorID: 'other'}}), false);
    for (const consecutiveNormalReadings of [-1, 4, 1.5, null]) {
        assert.equal(validIncidentState({...snapshot, temperatureAlert: {...snapshot.temperatureAlert, consecutiveNormalReadings}}), false);
    }
    assert.equal(validIncident({...incident, openedAt: '2026-10-23T10:00:00'}, sensorId), false);
    assert.equal(validIncident({...incident, thresholdAtOpen: NaN}, sensorId), false);
});

test('migration permits unknown onset only when explicitly marked; recovered time stays required', () => {
    const migrated = {...incident, migrated: true, openedAt: null, thresholdAtOpen: null, temperatureAtOpen: null};
    assert.equal(validIncident(migrated, sensorId), true);
    assert.equal(validIncident({...migrated, migrated: false}, sensorId), false);
    assert.equal(validIncident({...incident, thresholdAtOpen: null}, sensorId), false);
    assert.equal(validIncident({...incident, temperatureAtOpen: null}, sensorId), false);
    assert.equal(validIncidentState({...snapshot, activeIncident: migrated}), true);
    assert.equal(validIncident({...migrated, active: false}, sensorId), false);
    const html = renderIncidentPanel({...snapshot, activeIncident: migrated}, t);
    assert.match(html, /Unknown/);
    assert.match(html, /original opening time, temperature, and threshold are unknown/);
    assert.doesNotMatch(html, /1970|limit 0 °C/);
    for (const lang of ['en', 'de']) {
        const legacyHtml = renderIncidentPanel({...snapshot, activeIncident: migrated, recentIncidents: []}, key => messages[lang][key], lang);
        assert.match(legacyHtml, new RegExp(`${messages[lang].incidentLimit} ${messages[lang].incidentUnknown}`));
        assert.doesNotMatch(legacyHtml, /0 °C|1970|NaN/);
    }
});

test('acknowledgement remains visible separately from active recovery progress', () => {
    const acknowledged = {...incident, acknowledgedAt: '2026-10-23T10:00:10Z'};
    const html = renderIncidentPanel({...snapshot, activeIncident: acknowledged}, t);
    assert.match(html, /Active temperature incident/);
    assert.match(html, /2 \/ 3 · consecutive normal readings toward recovery/);
    assert.match(html, /Acknowledgement/);
    assert.match(html, /10:00:10 UTC/);
    assert.match(html, /Recovered/);
    assert.doesNotMatch(html, /<button|<form|onclick=/);
});

test('unknown, loading, no incident and no recovered history have distinct honest labels', () => {
    assert.match(renderIncidentPanel(null, t), /unavailable until a snapshot/);
    assert.match(renderIncidentPanel({...snapshot, activeIncident: null, recentIncidents: []}, t), /No active temperature incident/);
    assert.match(renderIncidentPanel({...snapshot, activeIncident: null, recentIncidents: []}, t), /No recovered temperature incidents/);
    assert.equal(incidentTime(null, 'de', 'Unbekannt'), 'Unbekannt');
    assert.match(incidentTime('2026-10-23T10:00:00Z', 'en'), /10:00:00 UTC/);
});

test('incident panel preserves date, temperature, status and escaping in EN and DE', () => {
    for (const lang of ['en', 'de']) {
        const translate = key => messages[lang][key];
        const html = renderIncidentPanel(snapshot, translate, lang);
        assert.match(html, /87/);
        assert.match(html, /80/);
        assert.match(html, /10:00:00 UTC/);
        assert.doesNotMatch(html, /undefined|NaN/);
        assert.match(html, new RegExp(messages[lang].incidentUnacknowledged));
    }
    assert.match(renderIncidentPanel(snapshot, () => '<img onerror="bad">'), /&lt;img onerror=&quot;bad&quot;&gt;/);
});

test('recovered history follows descending incident identity even if recovery timestamps differ', () => {
    const earlierOpenedLaterRecovered = {...recovered, id: 12, recoveredAt: '2026-10-23T11:00:00Z'};
    assert.equal(validIncidentState({...snapshot, recentIncidents: [recovered, earlierOpenedLaterRecovered]}), true);
    assert.equal(validIncidentState({...snapshot, recentIncidents: [earlierOpenedLaterRecovered, recovered]}), false);
});
