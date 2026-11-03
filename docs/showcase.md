# Telemetry Hub showcase

The public showcase combines a **live view of real backend processing** with an
explicitly separate **browser-only sandbox**. All measurements are synthetic.
The live view reads PostgreSQL data received from the Python simulator; sandbox
buttons never send telemetry to the backend.

The page lives in `showcase/`, uses HTML/CSS/JavaScript and an SVG chart, and has
complete English and German interfaces. The light equipment monitor uses muted
teal chrome, opaque pale panels, a blue signal trace and separate status colors.
Monitor, Sandbox and About are separate views with direct hash links; the monitor
opens on temperature and places the active incident beside the chart. A compact
sensor header shows its identity, location, latest reading time and status from
the selected data source. Recovered incidents expand to show their details.
Analytics and recent readings remain available in disclosure panels.
See [the visual standard](../DESIGN_SYSTEM.md) for the current palette.
The public host is intended to be
`telemetry.adskiyponchik.dev`. Repository contents alone do not prove deployment.

## Local visual preview

From the project root on your development computer:

```bash
python3 -m http.server 8092 --bind 127.0.0.1 --directory showcase
```

Open `http://127.0.0.1:8092/?preview=1`. The permanent preview notice identifies
illustrative data. Without `?preview=1`, the page requests the real API and shows
an unavailable state when no backend is connected. It never substitutes generated
data after a connection failure. `showcase/preview.mjs` builds a deterministic
illustrative scenario: the third hot reading opens an incident and the latest
normal reading advances recovery to 1/3. Preview statistics are explicitly
labelled as generated. No preview request is sent to the API. Stop the preview
server with Ctrl+C.

## Public data contract

`GET /api/v1/demo/snapshot` is available only with the `demo` Spring profile.
It has no visitor-controlled sensor, window or page-size parameters.

| Field | Meaning |
| --- | --- |
| `sensor` | Configured demo sensor `id` and `location` |
| `thresholds` | `voltageMin`, `voltageMax`, `frequencyMin`, `frequencyMax`, `maxTemperature`, `maxVibration`; effective sensor overrides are respected |
| `generatedAt` | Response creation time in UTC |
| `latest` | Latest stored reading for the demo sensor, including when older than the chart window; `null` before the first reading |
| `readings` | At most 180 most recent readings within 15 minutes, presented in ascending timestamp/ID order |
| `analytics` | Existing aggregate fields `sensorId`, `totalReadings`, `alarmCount`, `avgVoltage`, `avgFrequency`; `null` when the analytics window has no data |
| `temperatureAlert` | Persisted `sensorID`, `consecutiveBreaches`, `alertActive`, `consecutiveNormalReadings`; `null` before state exists |
| `activeIncident` | One active temperature incident for this sensor, or `null` |
| `recentIncidents` | At most five recovered incidents for this sensor, newest ID first |

Reading fields match the existing telemetry DTO, except this endpoint explicitly
serializes timestamps in UTC. The dedicated deployment uses a new database and
UTC JVM; do not reuse a database containing local timestamps with an unknown zone.
Existing reading timestamps retain their format; ingestion now requires `eventId`.

`alarmCount` counts `ALARM` and `ALARM_AND_MECHANICAL_DAMAGE`, **not** standalone
`MECHANICAL_DAMAGE`. The latter indicates a temperature/vibration threshold breach,
not a diagnosed physical failure. Current is recorded but has no alarm threshold.
Equality with every configured threshold is accepted as normal. The API does not
define a vibration unit; the UI displays its value without inventing one.

The UI polls every five seconds while visible. Data older than 20 seconds is
marked stale. API failures preserve the last displayed data with an explicit
connection warning. Empty data and server errors remain distinct.

## Simulator and retention

The showcase mode sends one reading every five seconds. Each two-minute cycle is:

| Elapsed seconds | Phase |
| --- | --- |
| 0–39 | Normal |
| 40–59 | Electrical anomaly |
| 60–79 | Temperature/vibration anomaly |
| 80–99 | Combined anomaly |
| 100–119 | Recovery to normal |

HTTP `201` means a new event was accepted; it does not mean domain status `OK`.
An identical retry returns `200` with the original receipt. The simulator generates
one UUID per observation and makes at most three attempts for network errors or
`5xx`, preserving that payload; delays are 0.5 and 1 second. Other HTTP failures
are not retried. There is no persistent delivery queue and exhausted attempts
leave delivery unconfirmed.

Only the demo profile schedules retention. Under the demo sensor's state lock,
it deletes that sensor's readings and recovered incidents older than the configured
retention (seven days by default); active incidents and other sensors are retained.
Duplicate protection ends when the original reading is deleted.

The incident panel displays opening details, recovery progress and acknowledgement
independently. Migrated incidents can have unknown opening time, temperature and
threshold; the UI labels these as unknown. The browser never acknowledges or sends
readings. Preview incidents are explicitly illustrative. See
[the incident API and acceptance scenario](temperature-incidents.md).

## Sandbox and verification

Sandbox examples use fixed illustrative defaults: 198–242 V, 49–51 Hz, maximum
80 °C and vibration 10. These values remain available offline and are labelled
separately from the live sensor configuration. Simulated HTTP outcomes are not
presented as actual network responses. An invalid sample does not enter history.

`tests/fixtures/classification-cases.json` is shared by Java and JavaScript tests.
It covers four statuses, inclusive boundaries, parameter-specific violations and
DTO validation. The Java side exercises the actual service/validator.

Run from the project root:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
node --test showcase/tests/*.test.mjs
python3 -m unittest discover -s tests -p 'test_*.py'
```

Java integration tests require Docker and create temporary PostgreSQL containers.
The Python tests require the simulator's `requests` dependency. See
[deployment instructions](showcase-deployment.md) for the complete runtime.
