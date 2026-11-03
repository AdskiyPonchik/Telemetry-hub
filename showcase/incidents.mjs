// The public demo only reads persisted incident state. It never acknowledges incidents.
const validTime = value => typeof value === 'string' && value.endsWith('Z') && Number.isFinite(Date.parse(value));
const finite = value => typeof value === 'number' && Number.isFinite(value);
const counter = value => Number.isInteger(value) && value >= 0 && value <= 3;
const esc = value => String(value ?? '').replace(/[&<>"']/g, character => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[character]));

export function validIncident(incident, sensorId) {
    return Boolean(incident && Number.isSafeInteger(incident.id) && incident.id > 0
        && incident.sensorId === sensorId && typeof incident.migrated === 'boolean'
        && typeof incident.active === 'boolean'
        && (finite(incident.thresholdAtOpen) || (incident.migrated && incident.thresholdAtOpen === null))
        && (finite(incident.temperatureAtOpen) || (incident.migrated && incident.temperatureAtOpen === null))
        && (validTime(incident.openedAt) || (incident.migrated && incident.openedAt === null))
        && (incident.acknowledgedAt === null || validTime(incident.acknowledgedAt))
        && (incident.active ? incident.recoveredAt === null : validTime(incident.recoveredAt)));
}

export function validIncidentState(snapshot) {
    const sensorId = snapshot?.sensor?.id;
    const state = snapshot?.temperatureAlert;
    return Boolean(typeof sensorId === 'string'
        && (state === null || (state?.sensorID === sensorId && counter(state.consecutiveBreaches)
            && counter(state.consecutiveNormalReadings) && typeof state.alertActive === 'boolean'))
        && (snapshot.activeIncident === null || (validIncident(snapshot.activeIncident, sensorId) && snapshot.activeIncident.active))
        && Array.isArray(snapshot.recentIncidents) && snapshot.recentIncidents.length <= 5
        && snapshot.recentIncidents.every(incident => validIncident(incident, sensorId) && !incident.active)
        && snapshot.recentIncidents.every((incident, index, rows) => index === 0
            || rows[index - 1].id > incident.id));
}

export function incidentTime(value, lang = 'en', unknown = 'Unknown') {
    if (!validTime(value)) return unknown;
    return new Intl.DateTimeFormat(lang === 'de' ? 'de-DE' : 'en-GB', {
        day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit', timeZone: 'UTC'
    }).format(new Date(value)) + ' UTC';
}

function presentation(t, lang) {
    const date = value => esc(incidentTime(value, lang, t('incidentUnknown')));
    const temperature = value => value === null ? t('incidentUnknown')
        : `${new Intl.NumberFormat(lang === 'de' ? 'de-DE' : 'en-GB', {maximumFractionDigits: 2}).format(value)} °C`;
    const details = incident => `<dl class="incident-details"><div><dt>${esc(t('incidentOpened'))}</dt><dd>${date(incident.openedAt)}</dd></div><div><dt>${esc(t('incidentOnset'))}</dt><dd>${esc(temperature(incident.temperatureAtOpen))} / ${esc(t('incidentLimit'))} ${esc(temperature(incident.thresholdAtOpen))}</dd></div><div class="incident-ack"><dt>${esc(t('incidentAcknowledged'))}</dt><dd>${incident.acknowledgedAt ? date(incident.acknowledgedAt) : esc(t('incidentUnacknowledged'))}</dd></div></dl>${incident.migrated ? `<p class="incident-migration">${esc(t('incidentMigrated'))}</p>` : ''}`;
    return {date, details};
}

export function renderActiveIncident(snapshot, t, lang = 'en') {
    if (!snapshot) return `<p class="incident-empty">${esc(t('incidentsUnavailable'))}</p>`;
    const state = snapshot.temperatureAlert, active = snapshot.activeIncident;
    const progress = active ? `${state?.consecutiveNormalReadings ?? 0} / 3 · ${t('incidentNormalProgress')}`
        : state ? `${state.consecutiveBreaches} / 3 · ${t('incidentBreachProgress')}` : t('incidentNoState');
    if (!active) return `<div class="incident-current"><h4>${esc(t('incidentNone'))}</h4><p class="incident-progress">${esc(progress)}</p></div>`;
    const count = state?.consecutiveNormalReadings ?? 0;
    return `<article class="incident-current"><div class="incident-state"><span class="status-dot status-ALARM" aria-hidden="true"></span><h4>${esc(t('incidentActive'))} <span class="incident-id">#${esc(active.id)}</span></h4></div><p class="incident-recovery-label">${esc(t(count ? 'incidentRecovering' : 'incidentAwaitingRecovery'))}</p><p class="incident-progress">${esc(progress)}</p><div class="recovery-track" role="progressbar" aria-label="${esc(t('incidentNormalProgress'))}" aria-valuemin="0" aria-valuemax="3" aria-valuenow="${count}">${[0, 1, 2].map(i => `<span class="recovery-step${i < count ? ' is-complete' : ''}"></span>`).join('')}</div>${presentation(t, lang).details(active)}</article>`;
}

export function renderIncidentHistory(snapshot, t, lang = 'en') {
    if (!snapshot) return `<p class="incident-empty">${esc(t('incidentsUnavailable'))}</p>`;
    if (!snapshot.recentIncidents.length) return `<p class="incident-empty">${esc(t('incidentNoHistory'))}</p>`;
    const {date, details} = presentation(t, lang);
    return `<ol class="incident-history">${snapshot.recentIncidents.map(incident => `<li><details data-incident-id="${esc(incident.id)}"><summary class="incident-history-heading"><strong>${esc(t('incidentRecovered'))} <span class="incident-id">#${esc(incident.id)}</span></strong><time datetime="${esc(incident.recoveredAt)}">${date(incident.recoveredAt)}</time></summary>${details(incident)}</details></li>`).join('')}</ol>`;
}

// Kept as a composed renderer for callers that need both sections together.
export function renderIncidentPanel(snapshot, t, lang = 'en') {
    return `${renderActiveIncident(snapshot, t, lang)}<div class="incident-recent"><h4>${esc(t('incidentRecent'))}</h4>${renderIncidentHistory(snapshot, t, lang)}</div>`;
}
