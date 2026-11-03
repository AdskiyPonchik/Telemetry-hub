# First release — local validation, 2026-10-23

This record describes local verification of the reliability/temperature-incident
release. It is not evidence of a remote GitHub Actions run or Raspberry Pi deployment.

## Automated checks

- `./mvnw --batch-mode --no-transfer-progress clean verify`: **110 tests**, zero
  failures/errors/skips; JAR and JaCoCo report produced. PostgreSQL ran in disposable
  Testcontainers instances.
- Java coverage includes 100 unique events + 20 duplicates, concurrent identical
  events/content conflicts, independent acknowledgement/recovery, rollback, upgrade
  from V2 with legacy data, retention/key expiration, and generated OpenAPI contracts.
- HTTP validation covers all five measurements with quoted NaN/infinities and
  overflowing literals; requests are rejected before service processing.
- Node 22 container (the CI command): **36 JavaScript tests passed**.
- Python unittest discovery: **12 tests passed**.
- Application, Nginx showcase and simulator Docker images built successfully.
- V1/V2 migration sources were preserved; `git diff --check` passed.

## Local end-to-end checks

A separate `telemetry-release-qa` Compose environment used a disposable test database
and loopback ports 18080 (internal API for testing) and 18082 (public proxy).

- The executable incident scenario passed: `201/200/409`, unique breach sequence,
  acknowledgement, three-step recovery and reopening.
- Restarting and then recreating the application against the same PostgreSQL volume
  preserved original receipts, counters, active/recovered incidents and acknowledgement.
- The final image rejected 20 non-finite HTTP inputs; no sensor state was written.
- The simulator image sent one event and safely repeated it, receiving creation and
  duplicate receipts with the same UUID.
- Public proxy checks passed: bounded UTC snapshot, scoped incident history and
  blocked telemetry/incident writes, full private APIs, Actuator and Swagger.
- Browser QA at 1440×1000 and 390×844 checked EN/DE incident layout, independent
  acknowledgement/recovery, explicit illustrative preview, and unknown migrated
  opening time/temperature/threshold. No browser console errors were observed.

The runtime scenario is reproducible with
[`check_incident_lifecycle.py`](../tests/check_incident_lifecycle.py); proxy checks
use [`check_showcase_http.py`](../tests/check_showcase_http.py). API and upgrade
boundaries are documented in [temperature-incidents.md](temperature-incidents.md).

Remote CI, ARM64 runtime and publication on Raspberry Pi were not checked in this run.
