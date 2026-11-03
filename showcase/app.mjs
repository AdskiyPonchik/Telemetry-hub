import {DEFAULT_THRESHOLDS, STATUSES, evaluateReading, breachedRules} from './rules.mjs';
import {messages} from './i18n.mjs';
import {validIncidentState, renderActiveIncident, renderIncidentHistory} from './incidents.mjs';
import {previewSnapshot} from './preview.mjs';

const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
}[c]));
const units = {voltage: 'V', current: 'A', frequency: 'Hz', temperature: '°C', vibration: ''};
const metrics = Object.keys(units);
const colors = {OK: '#438778', ALARM: '#aa702b', MECHANICAL_DAMAGE: '#8769ab', ALARM_AND_MECHANICAL_DAMAGE: '#b55364'};
const preview = new URLSearchParams(location.search).get('preview') === '1';
let lang = 'en';
try {
    if (localStorage.getItem('telemetry-language') === 'de') lang = 'de';
} catch {
}
let snapshot = null, receivedAt = 0, cacheAge = 0, loading = true, failed = false, inflight = false, metric = 'temperature',
    selectedReading = null;
let activeScenario = 'normal', sandboxHistory = [], acceptedCount = 0;
const t = key => messages[lang][key] ?? key;
const num = (value, digits = 1) => typeof value === 'number' && Number.isFinite(value) ? new Intl.NumberFormat(lang === 'de' ? 'de-DE' : 'en-GB', {
    maximumFractionDigits: digits,
    minimumFractionDigits: digits
}).format(value) : '—';
const time = value => new Intl.DateTimeFormat(lang === 'de' ? 'de-DE' : 'en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZone: 'UTC'
}).format(new Date(value));
const unit = key => units[key];
const statusName = status => t('status' + status);
const dot = status => `<span class="status-dot status-${esc(status)}" aria-hidden="true"></span>`;

function limitText(key, thresholds) {
    if (key === 'current') return t('noAlarmThreshold');
    if (key === 'voltage') return `${num(thresholds.voltageMin, 0)}–${num(thresholds.voltageMax, 0)} V`;
    if (key === 'frequency') return `${num(thresholds.frequencyMin, 0)}–${num(thresholds.frequencyMax, 0)} Hz`;
    return `≤ ${num(key === 'temperature' ? thresholds.maxTemperature : thresholds.maxVibration, 0)} ${unit(key)}`;
}

function breachText(reading, thresholds) {
    const breaches = breachedRules(reading, thresholds);
    return breaches.length ? breaches.map(b => `${t(b.metric)} ${num(reading[b.metric])} ${unit(b.metric)} ${b.operator} ${num(b.limit)} ${unit(b.metric)}`).join(' · ') : t('noBreaches');
}

function connectionState() {
    if (preview) return 'preview';
    if (failed) return 'offline';
    if (loading) return 'loading';
    if (!snapshot?.latest) return 'empty';
    const age = Math.max(0, Date.parse(snapshot.generatedAt) - Date.parse(snapshot.latest.timestamp)) + cacheAge + performance.now() - receivedAt;
    return age > 20000 ? 'stale' : 'live';
}

function renderFreshness() {
    const state = connectionState();
    $('current-status').innerHTML = snapshot?.latest ? `${dot(snapshot.latest.status)} ${esc(statusName(snapshot.latest.status))}` : '';
    $('source-note').textContent = t(preview ? 'previewSource' : 'liveSource');
    $('connection').className = 'connection ' + state;
    $('connection').textContent = t(state);
    const notice = state === 'offline' ? (snapshot ? 'offlineNotice' : 'offlineEmpty') : state === 'stale' ? 'staleNotice' : state === 'empty' ? 'emptyNotice' : state === 'loading' ? 'loadingNotice' : null;
    $('monitor-notice').hidden = !notice;
    $('monitor-notice').textContent = notice ? t(notice) : '';
    $('equipment-time').textContent = snapshot?.latest ? `${time(snapshot.latest.timestamp)} UTC` : t('noReading');
    $('updated').textContent = snapshot?.latest ? `${t('updated')} ${time(snapshot.latest.timestamp)} · ${t('localTime')}` : t('noReading');
}

function renderMetrics() {
    const focused = document.activeElement?.dataset.metric;
    $('metrics').innerHTML = metrics.map(key => `<button type="button" class="metric" data-metric="${key}" aria-pressed="${metric === key}" aria-label="${esc(t('selectMetric') + ': ' + t(key))}"><span class="metric-name">${t(key)}<span class="metric-arrow" aria-hidden="true">${metric === key ? '↗' : '·'}</span></span><div class="metric-value">${num(snapshot?.latest?.[key])}<small>${unit(key)}</small></div><div class="metric-rule">${snapshot ? limitText(key, snapshot.thresholds) : '—'}${key === 'vibration' ? ' · ' + t('vibrationUnitUnknown') : ''}</div></button>`).join('');
    if (focused) document.querySelector(`[data-metric="${focused}"]`)?.focus({preventScroll: true});
}

function renderChartDetail(reading) {
    if (!reading) {
        $('chart-detail').textContent = t('inspectDot');
        return;
    }
    $('chart-detail').innerHTML = `<strong>${esc(time(reading.timestamp))} · ${esc(statusName(reading.status))}</strong> &nbsp; ${esc(breachText(reading, snapshot.thresholds))}`;
}

function renderChart() {
    $('chart-title').textContent = t(metric + 'Trend');
    $('chart-value').textContent = num(snapshot?.latest?.[metric]);
    $('chart-unit').textContent = unit(metric);
    $('chart-current-note').textContent = snapshot?.latest
        ? `${t('showLatest')} · ${statusName(snapshot.latest.status)}` : t('noReading');
    const focusedId = document.activeElement?.dataset.readingId;
    const rows = snapshot?.readings ?? [];
    if (!rows.some(row => row.id === selectedReading)) selectedReading = null;
    const activeId = selectedReading ?? rows.at(-1)?.id;
    if (!rows.length) {
        $('chart').innerHTML = `<p class="chart-empty">${t(snapshot ? 'chartEmpty' : 'chartUnavailable')}</p>`;
        renderChartDetail(snapshot?.latest);
        return;
    }
    const values = rows.map(r => r[metric]);
    const thresholds = snapshot.thresholds;
    const limits = metric === 'voltage' ? [thresholds.voltageMin, thresholds.voltageMax] : metric === 'frequency' ? [thresholds.frequencyMin, thresholds.frequencyMax] : metric === 'temperature' ? [thresholds.maxTemperature] : metric === 'vibration' ? [thresholds.maxVibration] : [];
    let min = Math.min(...values, ...limits), max = Math.max(...values, ...limits);
    const pad = Math.max((max - min) * .25, metric === 'frequency' ? .4 : metric === 'vibration' ? .8 : 2);
    min -= pad;
    max += pad;
    if (['current', 'vibration'].includes(metric)) min = Math.max(0, min);
    const w = $('chart').clientWidth || 400, h = 260, left = 43, right = w - 34, top = 20,
        bottom = h - 32;
    const end = Date.parse(snapshot.generatedAt), start = end - 900000;
    const x = r => left + Math.max(0, Math.min(1, (Date.parse(r.timestamp) - start) / 900000)) * (right - left),
        y = v => bottom - (v - min) / (max - min) * (bottom - top);
    let content = '<defs><linearGradient id="chart-area" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#4b79d8" stop-opacity=".16"/><stop offset="1" stop-color="#4b79d8" stop-opacity="0"/></linearGradient></defs>';
    for (let i = 0; i <= 4; i++) {
        const v = min + (max - min) * i / 4;
        content += `<line class="chart-grid" x1="${left}" x2="${right}" y1="${y(v)}" y2="${y(v)}"/><text class="chart-axis" x="${left - 10}" y="${y(v) + 3}" text-anchor="end">${num(v, metric === 'frequency' || metric === 'current' || metric === 'vibration' ? 1 : 0)}</text>`;
    }
    if (limits.length === 2) content += `<rect x="${left}" y="${y(limits[1])}" width="${right - left}" height="${y(limits[0]) - y(limits[1])}" fill="#a6b6c8" opacity=".035"/>`;
    limits.forEach(limit => content += `<line class="chart-threshold" x1="${left}" x2="${right}" y1="${y(limit)}" y2="${y(limit)}"/><text class="chart-threshold-label" x="${right + 8}" y="${y(limit) + 3}">${num(limit, 0)}</text>`);
    for (let i = 0; i <= 3; i++) {
        const date = new Date(start + 900000 * i / 3);
        content += `<text class="chart-axis" x="${left + (right - left) * i / 3}" y="${bottom + 22}" text-anchor="${i === 0 ? 'start' : i === 3 ? 'end' : 'middle'}">${time(date).slice(0, 5)}</text>`;
    }
    content += `<path class="chart-area" d="M${x(rows[0])},${bottom} ${rows.map(r => `L${x(r)},${y(r[metric])}`).join(' ')} L${x(rows.at(-1))},${bottom} Z"/>`;
    content += `<path class="chart-line" d="${rows.map((r, i) => `${i ? 'L' : 'M'}${x(r).toFixed(2)},${y(r[metric]).toFixed(2)}`).join(' ')}"/>`;
    content += rows.map((r, i) => `<circle class="chart-dot${r.status === 'OK' ? ' normal' : ''}${r.id === activeId ? ' selected' : ''}" tabindex="${r.id === activeId ? 0 : -1}" role="button" data-reading="${i}" data-reading-id="${r.id}" cx="${x(r)}" cy="${y(r[metric])}" r="${r.id === activeId ? 4 : 2.4}" fill="${colors[r.status]}" aria-label="${esc(`${time(r.timestamp)} ${t(metric)} ${num(r[metric])} ${unit(metric)}, ${statusName(r.status)}. ${breachText(r, thresholds)}`)}"><title>${esc(`${time(r.timestamp)} · ${num(r[metric])} ${unit(metric)} · ${statusName(r.status)}`)}</title></circle>`).join('');
    $('chart').innerHTML = `<svg viewBox="0 0 ${w} ${h}" role="group" aria-label="${esc(t(metric) + ' ' + t('chartLabel'))}"><text class="chart-axis" x="${left}" y="8">${unit(metric)}</text>${content}</svg>`;
    renderChartDetail(rows.find(r => r.id === selectedReading) ?? snapshot.latest);
    if (focusedId != null) {
        const i = rows.findIndex(r => String(r.id) === focusedId);
        $('chart').querySelector(`[data-reading="${i}"]`)?.focus({preventScroll: true});
    }
}

function renderIncidents() {
    const history = $('incident-history');
    const openIds = [...history.querySelectorAll('details[open]')].map(el => el.dataset.incidentId);
    const focusedId = history.contains(document.activeElement)
        ? document.activeElement.closest('[data-incident-id]')?.dataset.incidentId : null;
    $('incident-content').innerHTML = renderActiveIncident(snapshot, t, lang);
    history.innerHTML = renderIncidentHistory(snapshot, t, lang);
    history.querySelectorAll('details').forEach(el => {
        el.open = openIds.includes(el.dataset.incidentId);
        if (el.dataset.incidentId === focusedId) el.querySelector('summary')?.focus({preventScroll: true});
    });
}

function renderData() {
    $('sensor-name').textContent = snapshot?.sensor.id ?? t('unknownSensor');
    $('sensor-location').textContent = preview ? t('previewLocation') : (snapshot?.sensor.location ?? t('unknownLocation'));
    renderFreshness();
    renderMetrics();
    renderChart();
    $('incident-source').textContent = t(preview ? 'incidentPreviewSource' : 'incidentLiveSource');
    renderIncidents();
    const a = snapshot?.analytics;
    $('analytics').innerHTML = [['totalReadings', a?.totalReadings, '', 0, 'in24h'], ['electricalAlarms', a?.alarmCount, '', 0, 'alarmNote'], ['avgVoltage', a?.avgVoltage, 'V', 1, 'in24h'], ['avgFrequency', a?.avgFrequency, 'Hz', 2, 'in24h']].map(([label, value, unit, digits, description]) => `<div class="analytics-item"><div class="analytics-label">${t(label)}</div><div class="analytics-value">${num(value, digits)} <small>${unit}</small></div><div class="analytics-description">${t(a ? description : 'noAnalytics')}</div></div>`).join('');
    $('readings-body').innerHTML = (snapshot?.readings ?? []).slice(-20).reverse().map(r => `<tr><td>${time(r.timestamp)}</td>${metrics.map(key => `<td>${num(r[key])} ${unit(key)}</td>`).join('')}<td><span class="table-status">${dot(r.status)}${esc(statusName(r.status))}</span></td></tr>`).join('') || `<tr><td colspan="7">${t(snapshot ? 'chartEmpty' : 'chartUnavailable')}</td></tr>`;
}

const scenarios = [['normal', '○'], ['surge', '↗'], ['overheat', '↑'], ['vibrationScenario', '≈'], ['combined', '⚡'], ['boundary', '='], ['invalid', '×']];

function scenarioInput(key) {
    const input = {
        eventId: '6aef80c8-16bf-4bfd-b392-cbda5f12bb25',
        sensorId: 'sandbox-motor',
        location: 'Local sandbox',
        voltage: 220,
        current: 12.4,
        frequency: 50,
        temperature: 45,
        vibration: 2.5
    };
    if (key === 'surge') input.voltage = 260;
    if (key === 'overheat') input.temperature = 95;
    if (key === 'vibrationScenario') input.vibration = 14;
    if (key === 'combined') {
        input.voltage = 260;
        input.temperature = 95;
        input.vibration = 14;
    }
    if (key === 'boundary') Object.assign(input, {voltage: 242, frequency: 51, temperature: 80, vibration: 10});
    if (key === 'invalid') input.voltage = 0;
    return input;
}

function renderSandbox() {
    const focused = document.activeElement?.dataset.scenario;
    $('scenarios').innerHTML = scenarios.map(([key, symbol]) => `<button type="button" class="scenario" data-scenario="${key}" aria-pressed="${activeScenario === key}"><span class="scenario-symbol" aria-hidden="true">${symbol}</span>${t(key)}<span class="scenario-cue" aria-hidden="true">↗</span></button>`).join('');
    if (focused) document.querySelector(`[data-scenario="${focused}"]`)?.focus({preventScroll: true});
    const input = scenarioInput(activeScenario), result = evaluateReading(input);
    $('sandbox-http').innerHTML = `<span class="http-badge ${result.httpStatus === 400 ? 'error' : ''}">${result.httpStatus} · ${t('simulated')}</span>`;
    $('sandbox-input').innerHTML = metrics.map(key => `<div class="input-value"><span>${t(key)}</span>${num(input[key])} ${unit(key)}</div>`).join('');
    $('sandbox-event-id').textContent = `eventId: ${input.eventId}`;
    $('sandbox-rule').textContent = activeScenario === 'invalid' ? t('invalidRule') : activeScenario === 'boundary' ? t('boundaryRule') : `${t('validRule')} ${breachText(input, DEFAULT_THRESHOLDS)}`;
    $('sandbox-result').innerHTML = `<div class="result-status ${result.status ? 'status-' + result.status : ''}">${result.status ? dot(result.status) : ''}${result.status ? statusName(result.status) : t('invalidResult')}</div><p class="result-explanation">${result.status ? `<code>${result.status}</code> · ` : ''}${t(result.status ? 'localAccepted' : 'localRejected')}</p>`;
    $('sandbox-count').textContent = `${t('sampleCount')}: ${acceptedCount}`;
    const level = {OK: 48, ALARM: 32, MECHANICAL_DAMAGE: 20, ALARM_AND_MECHANICAL_DAMAGE: 8};
    const dots = sandboxHistory.map((r, i) => ({x: 12 + i * 22, y: level[r.status], status: r.status}));
    $('sandbox-chart').innerHTML = `<svg viewBox="0 0 540 62" role="img" aria-label="${esc(t('sandboxChartLabel'))}"><line x1="12" x2="530" y1="49" y2="49" stroke="#30363f" stroke-dasharray="3 5"/><path d="${dots.map((r, i) => `${i ? 'L' : 'M'}${r.x},${r.y}`).join(' ')}" fill="none" stroke="#5d6878" stroke-width="1.2"/>${dots.map(r => `<circle cx="${r.x}" cy="${r.y}" r="4" fill="${colors[r.status]}"><title>${esc(statusName(r.status))}</title></circle>`).join('')}</svg>`;
}

function activateScenario(key) {
    activeScenario = key;
    const result = evaluateReading(scenarioInput(key));
    if (result.status) {
        sandboxHistory.push(result);
        sandboxHistory = sandboxHistory.slice(-24);
        acceptedCount++;
    }
    renderSandbox();
}

function renderLanguage() {
    document.documentElement.lang = lang;
    document.title = 'Telemetry Hub · ' + t('liveMonitor');
    document.querySelectorAll('[data-i18n]').forEach(el => el.textContent = t(el.dataset.i18n));
    document.querySelectorAll('[data-i18n-html]').forEach(el => el.innerHTML = t(el.dataset.i18nHtml));
    document.querySelectorAll('[data-i18n-alt]').forEach(el => el.setAttribute('alt', t(el.dataset.i18nAlt)));
    document.querySelectorAll('[data-i18n-aria]').forEach(el => el.setAttribute('aria-label', t(el.dataset.i18nAria)));
    document.querySelectorAll('[data-lang]').forEach(el => el.setAttribute('aria-pressed', String(el.dataset.lang === lang)));
    $('preview-banner').hidden = !preview;
    $('preview-banner').textContent = t('previewBanner');
    $('status-legend').innerHTML = STATUSES.map(status => `<span class="legend-item">${dot(status)}${statusName(status)}</span>`).join('');
    $('status-guide').innerHTML = STATUSES.map(status => `<div class="status-guide-item status-${status}">${dot(status)}<div><strong>${statusName(status)} <span class="sr-only">${status}</span></strong><p>${t('statusDescription' + status)}</p></div></div>`).join('');
    document.querySelector('[data-i18n=analyticsNote]').textContent = t(preview ? 'previewAnalytics' : 'analyticsNote');
    $('architecture-flow').innerHTML = [0, 1, 2, 3, 4].map(n => `<div class="flow-step"><span class="micro">0${n + 1} / ${['PYTHON', 'HTTP', 'SERVICE', 'JPA', 'SQL'][n]}</span><h3>${t('flow' + n + 'Title')}</h3><p>${t('flow' + n + 'Body')}</p></div>`).join('');
    $('engineering-notes').innerHTML = [1, 2, 3].map(n => `<article class="engineering-note"><h3>${t('note' + n + 'Title')}</h3><p>${t('note' + n + 'Body')}</p></article>`).join('');
    renderData();
    renderSandbox();
}

function validSnapshot(value) {
    const validTime = v => typeof v === 'string' && v.endsWith('Z') && Number.isFinite(Date.parse(v));
    const validReading = r => r && Number.isSafeInteger(r.id) && typeof r.sensorId === 'string' && (r.location == null || typeof r.location === 'string') && metrics.every(k => typeof r[k] === 'number' && Number.isFinite(r[k])) && STATUSES.includes(r.status) && validTime(r.timestamp);
    return validIncidentState(value) && value && typeof value.sensor?.id === 'string' && (value.sensor.location == null || typeof value.sensor.location === 'string') && validTime(value.generatedAt) && Object.keys(DEFAULT_THRESHOLDS).every(k => typeof value.thresholds?.[k] === 'number' && Number.isFinite(value.thresholds[k])) && (value.latest === null || validReading(value.latest)) && Array.isArray(value.readings) && value.readings.length <= 180 && value.readings.every(validReading) && (value.analytics === null || (['totalReadings', 'alarmCount', 'avgVoltage', 'avgFrequency'].every(k => typeof value.analytics?.[k] === 'number' && Number.isFinite(value.analytics[k])) && typeof value.analytics.sensorId === 'string'));
}

async function refresh() {
    if (preview || inflight || document.hidden) return;
    inflight = true;
    $('refresh').disabled = true;
    const controller = new AbortController(), timeout = setTimeout(() => controller.abort(), 7000);
    try {
        const response = await fetch('/api/v1/demo/snapshot', {
            method: 'GET',
            cache: 'no-store',
            signal: controller.signal,
            headers: {Accept: 'application/json'}
        });
        if (!response.ok) throw new Error('snapshot');
        const next = await response.json();
        if (!validSnapshot(next)) throw new Error('contract');
        snapshot = next;
        receivedAt = performance.now();
        const age = Number(response.headers.get('Age'));
        const serverDate = Date.parse(response.headers.get('Date') ?? '');
        cacheAge = Math.max(Number.isFinite(age) ? age * 1000 : 0, Number.isFinite(serverDate) ? serverDate - Date.parse(next.generatedAt) : 0, 0);
        failed = false;
        loading = false;
        renderData();
    } catch {
        failed = true;
        loading = false;
        renderFreshness();
        if (!snapshot) renderData();
    } finally {
        clearTimeout(timeout);
        inflight = false;
        $('refresh').disabled = false;
    }
}

function renderView() {
    const requested = location.hash.slice(1);
    const view = ['monitor', 'sandbox', 'architecture'].includes(requested) ? requested : 'monitor';
    document.querySelectorAll('.view-panel').forEach(panel => { panel.hidden = panel.id !== view; });
    document.querySelectorAll('[data-view]').forEach(link => {
        if (link.dataset.view === view) link.setAttribute('aria-current', 'page');
        else link.removeAttribute('aria-current');
    });
    if (view === 'monitor') renderChart();
}
window.addEventListener('hashchange', renderView);
document.querySelector('.skip').addEventListener('click', event => {
    event.preventDefault();
    $('main').focus();
});

document.querySelectorAll('[data-lang]').forEach(button => button.addEventListener('click', () => {
    lang = button.dataset.lang;
    try {
        localStorage.setItem('telemetry-language', lang);
    } catch {
    }
    renderLanguage();
}));
$('metrics').addEventListener('click', event => {
    const button = event.target.closest('[data-metric]');
    if (button) {
        metric = button.dataset.metric;
        renderMetrics();
        renderChart();
    }
});
$('scenarios').addEventListener('click', event => {
    const button = event.target.closest('[data-scenario]');
    if (button) activateScenario(button.dataset.scenario);
});

function inspect(event) {
    const node = event.target.closest('[data-reading]');
    if (node) {
        const row = snapshot?.readings[Number(node.dataset.reading)];
        if (row) {
            selectedReading = row.id;
            $('chart').querySelectorAll('[data-reading]').forEach(dot => {
                dot.setAttribute('tabindex', dot === node ? '0' : '-1');
                dot.classList.toggle('selected', dot === node);
                dot.setAttribute('r', dot === node ? '4' : '2.4');
            });
            renderChartDetail(row);
        }
    }
}

$('chart').addEventListener('click', inspect);
$('chart').addEventListener('focusin', inspect);
$('chart').addEventListener('keydown', event => {
    const dots = Array.from($('chart').querySelectorAll('[data-reading]'));
    const current = dots.indexOf(event.target);
    if (current < 0) return;
    const next = event.key === 'ArrowLeft' ? Math.max(0, current - 1) : event.key === 'ArrowRight' ? Math.min(dots.length - 1, current + 1) : event.key === 'Home' ? 0 : event.key === 'End' ? dots.length - 1 : null;
    if (next !== null) {
        event.preventDefault();
        dots[next].focus();
    } else if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        inspect(event);
    }
});
let chartWidth = 0;
new ResizeObserver(entries => {
    const width = entries[0].contentRect.width;
    if (width > 0 && width !== chartWidth) {
        chartWidth = width;
        renderChart();
    }
}).observe($('chart'));
$('refresh').addEventListener('click', () => {
    if (preview) {
        snapshot = previewSnapshot();
        renderData();
    } else refresh();
});
document.addEventListener('visibilitychange', () => {
    if (!document.hidden) refresh();
});
if (preview) {
    snapshot = previewSnapshot();
    receivedAt = performance.now();
    loading = false;
}
sandboxHistory = [evaluateReading(scenarioInput('normal'))];
acceptedCount = 1;
renderLanguage();
renderView();
refresh();
setInterval(refresh, 5000);
setInterval(() => {
    if (!document.hidden) renderFreshness();
}, 1000);
