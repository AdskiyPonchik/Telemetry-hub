# Reliable ingestion and temperature incidents

Every new measurement supplies a sender-generated UUID `eventId`. The sender keeps
that UUID and all input fields unchanged across retries. The identity is the pair
`(sensorId, eventId)`; another sensor may use the same UUID.

## Ingestion contract

| Request | Result |
| --- | --- |
| New valid event | `201 Created` and a receipt |
| Same key and parsed input values | `200 OK` and the original receipt |
| Same key, changed input including location | `409 Conflict`, no changes |
| Missing/null/malformed UUID or invalid measurement | `400 Bad Request` |

The receipt contains `id`, `eventId`, `sensorId`, `status`, and `timestamp`.
All five measurements must be finite: NaN and infinities (including coerced strings
and overflowing numeric literals) are rejected before processing.
JSON field order and writing `230` instead of `230.0` do not change identity.
A retry does not reclassify the reading after threshold changes or modify counts.
Protection lasts while the original reading exists. Demo retention defaults to
seven days; a key can be accepted again after its row is deleted. Legacy rows
keep `event_id = NULL`, but all new HTTP submissions require it.

## Incident lifecycle

Three consecutive unique temperatures strictly above the effective threshold open
one incident. A normal value before opening interrupts that series. While active,
three consecutive temperatures at or below the current threshold recover the
incident; another hot reading interrupts recovery. Recovery resets the counters.
A later series can open a new incident. Classification of each reading remains
independent: an `OK` reading can coexist with an incident still recovering.

Acknowledgement records attention independently of recovery. It is allowed before
or after recovery; repeating it retains the first timestamp. This release exposes
it only through the internal API and records no authenticated operator identity.

| API | Behavior |
| --- | --- |
| `GET /api/v1/temperature-alerts/{sensorID}` | `sensorID`, `consecutiveBreaches`, `alertActive`, `consecutiveNormalReadings`; unknown state → `404` |
| `GET /api/v1/temperature-incidents?sensorId=...&page=0&size=20` | Required nonblank sensor; page >=0, size 1–100; newest ID first |
| `GET /api/v1/temperature-incidents/{id}` | Details; unknown ID → `404` |
| `PUT /api/v1/temperature-incidents/{id}/acknowledgement` | Idempotent acknowledgement; returns details; unknown ID → `404` |

Incident details contain `id`, `sensorId`, `openedAt`, `acknowledgedAt`,
`recoveredAt`, `thresholdAtOpen`, `temperatureAtOpen`, `migrated`, and `active`.
An absent acknowledgement/recovery is `null`. New times are UTC instants with
microsecond precision. The legacy reading `timestamp` retains its JVM-local
`LocalDateTime` meaning. Device measurement time and late-event policies are not
part of this release; order is the order of acquiring the sensor state lock.

## Storage and upgrade

V3 adds the nullable event UUID and unique `(sensor_id,event_id)` key. V4 adds the
recovery counter and incident table, enforcing one active incident per sensor.
Existing readings and breach counters remain. Existing counts >=3 become migrated
active incidents: opening time, opening temperature and threshold are all unknown
and therefore null. The API and UI identify that boundary explicitly. No earlier
incident history or proof of unique legacy events can be reconstructed.

Ingestion locks sensor state, checks event identity, reads current thresholds and
updates readings/counters/incidents in one transaction. A failure rolls everything
back. Acknowledgement locks the incident; ingestion locks state then incident.
Demo cleanup uses the same state lock, removes old readings and recovered
incidents for only its configured sensor, and preserves active incidents.

## Local acceptance scenario

Against a local application with its PostgreSQL running (this writes synthetic
data under a fresh sensor ID):

```bash
python3 tests/check_incident_lifecycle.py http://127.0.0.1:8080 --save-state /tmp/telemetry-incident-state.json
```

It checks creation, duplicate receipt, content conflict, three unique breaches,
acknowledgement, recovery and reopening. Restart only the application while keeping
the same database, then verify persistence:

```bash
python3 tests/check_incident_lifecycle.py http://127.0.0.1:8080 --verify-state /tmp/telemetry-incident-state.json
```

The showcase reads only its configured sensor. To demonstrate the same scenario
there, use a disposable showcase environment, stop its simulator, and pass
`--sensor MGD-PWR-01` (or the configured sensor ID). Its public proxy intentionally
blocks ingestion, full incident APIs and acknowledgement. Validate that boundary:

```bash
python3 tests/check_showcase_http.py http://127.0.0.1:8082
```

The browser snapshot includes one active incident and at most five recovered
incidents. Preview and sandbox remain explicitly illustrative; the browser never
submits telemetry or acknowledgement. A network failure preserves the last snapshot.
