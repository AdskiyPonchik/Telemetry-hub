import {DEFAULT_THRESHOLDS, evaluateReading} from './rules.mjs';

const SENSOR = {id: 'MOTOR-001', location: 'Assembly line · Drive unit 04'};
const round = value => Math.round(value * 10) / 10;

// Illustrative data only: every refresh moves the same scenario to the supplied time.
// The 180 samples cover [now - 14m55s, now], inside the 15-minute chart window.
export function previewSnapshot(now = Date.now()) {
    const time = offset => new Date(now + offset).toISOString();
    const readings = Array.from({length: 180}, (_, i) => {
        const temperature = i < 90
            ? round(74.5 + 3.4 * Math.sin(i * .09))
            : i === 179 ? 78.4 : round(80.8 + 8.2 * Math.sin((i - 90) / 88 * Math.PI));
        const row = {
            id: i + 1,
            eventId: `00000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`,
            sensorId: SENSOR.id,
            location: SENSOR.location,
            voltage: round(228.1 + Math.sin((i - 179) * .29) * 2.1),
            current: round(8.4 + Math.sin((i - 179) * .19) * .5),
            frequency: round(50 + Math.sin((i - 179) * .11) * .1),
            temperature,
            vibration: round(4.8 + Math.sin((i - 179) * .25) * .5),
            timestamp: time(-(179 - i) * 5000)
        };
        row.status = evaluateReading(row).status;
        return row;
    });

    // Fold the accepted unique readings so the visible onset and counters agree.
    let consecutiveBreaches = 0, consecutiveNormalReadings = 0, activeIncident = null;
    for (const row of readings) {
        const hot = row.temperature > DEFAULT_THRESHOLDS.maxTemperature;
        consecutiveBreaches = hot ? Math.min(consecutiveBreaches + 1, 3) : 0;
        if (activeIncident) {
            consecutiveNormalReadings = hot ? 0 : consecutiveNormalReadings + 1;
            if (consecutiveNormalReadings === 3) {
                activeIncident = null;
                consecutiveBreaches = 0;
                consecutiveNormalReadings = 0;
            }
        } else if (consecutiveBreaches === 3) {
            activeIncident = {
                id: 24, sensorId: SENSOR.id, openedAt: row.timestamp,
                acknowledgedAt: readings[108].timestamp, recoveredAt: null,
                thresholdAtOpen: DEFAULT_THRESHOLDS.maxTemperature,
                temperatureAtOpen: row.temperature, migrated: false, active: true
            };
        }
    }

    return {
        sensor: {...SENSOR},
        thresholds: {...DEFAULT_THRESHOLDS},
        generatedAt: time(0),
        latest: readings.at(-1),
        readings,
        temperatureAlert: {
            sensorID: SENSOR.id, consecutiveBreaches, consecutiveNormalReadings,
            alertActive: activeIncident !== null
        },
        activeIncident,
        // These older incidents predate the chart; unknown migrated onset stays unknown.
        recentIncidents: [{
            id: 23, sensorId: SENSOR.id, openedAt: time(-3 * 3600000),
            acknowledgedAt: null, recoveredAt: time(-2 * 3600000),
            thresholdAtOpen: 80, temperatureAtOpen: 84.2, migrated: false, active: false
        }, {
            id: 22, sensorId: SENSOR.id, openedAt: null,
            acknowledgedAt: null, recoveredAt: time(-4 * 3600000),
            thresholdAtOpen: null, temperatureAtOpen: null, migrated: true, active: false
        }],
        // A separate illustrative 24-hour aggregate, not a rollup of the 15-minute chart.
        analytics: {
            sensorId: SENSOR.id, totalReadings: 17280, alarmCount: 143,
            avgVoltage: 228.2, avgFrequency: 50.01
        }
    };
}
