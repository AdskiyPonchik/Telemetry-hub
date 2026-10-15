export const DEFAULT_THRESHOLDS = Object.freeze({
    voltageMin: 198,
    voltageMax: 242,
    frequencyMin: 49,
    frequencyMax: 51,
    maxTemperature: 80,
    maxVibration: 10
});
export const STATUSES = ['OK', 'ALARM', 'MECHANICAL_DAMAGE', 'ALARM_AND_MECHANICAL_DAMAGE'];

export function evaluateReading(input, thresholds = DEFAULT_THRESHOLDS) {
    const errors = [];
    if (typeof input?.sensorId !== 'string' || !input.sensorId.trim() || input.sensorId.length > 255) errors.push('sensorId');
    if (input?.location != null && (typeof input.location !== 'string' || input.location.length > 255)) errors.push('location');
    for (const key of ['voltage', 'current', 'frequency', 'temperature', 'vibration']) {
        if (typeof input?.[key] !== 'number' || !Number.isFinite(input[key]) || (['voltage', 'current', 'frequency'].includes(key) && input[key] <= 0) || (key === 'vibration' && input[key] < 0)) errors.push(key);
    }
    if (errors.length) return {httpStatus: 400, status: null, errors};
    const electrical = input.voltage < thresholds.voltageMin || input.voltage > thresholds.voltageMax || input.frequency < thresholds.frequencyMin || input.frequency > thresholds.frequencyMax;
    const mechanical = input.temperature > thresholds.maxTemperature || input.vibration > thresholds.maxVibration;
    return {
        httpStatus: 201,
        status: electrical && mechanical ? STATUSES[3] : electrical ? STATUSES[1] : mechanical ? STATUSES[2] : STATUSES[0],
        errors: []
    };
}

export function breachedRules(input, t = DEFAULT_THRESHOLDS) {
    const breaches = [];
    for (const [metric, min, max] of [['voltage', t.voltageMin, t.voltageMax], ['frequency', t.frequencyMin, t.frequencyMax], ['temperature', null, t.maxTemperature], ['vibration', null, t.maxVibration]]) {
        if (min != null && input[metric] < min) breaches.push({metric, operator: '<', limit: min});
        if (input[metric] > max) breaches.push({metric, operator: '>', limit: max});
    }
    return breaches;
}
