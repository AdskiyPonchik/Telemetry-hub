# ⚡ Telemetry Hub

**Industrial IoT telemetry ingestion & analytics service** built with Spring Boot.

Receives readings from power-grid sensors (voltage, current, frequency, temperature, vibration), validates them, detects anomalies in real time and exposes aggregated analytics over a REST API. Ships with a Python sensor simulator that generates realistic telemetry — including random fault injection — so the whole pipeline can be demoed locally.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-runtime-336791?logo=postgresql&logoColor=white)
![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven)

## Interactive showcase

The English/German showcase in `showcase/` makes the complete sensor-to-database
path visible: a live chart, effective thresholds, four classification states,
recent readings, and PostgreSQL aggregates. A separate browser-only sandbox lets
visitors explore faults, threshold boundaries, and rejected input without changing
the shared live stream. Measurements are synthetic; live processing and storage
use the real service.

For a local visual preview, run `python3 -m http.server 8092 --bind 127.0.0.1 --directory showcase`
and open `http://127.0.0.1:8092/?preview=1`. Preview data is explicitly labelled;
the ordinary page never replaces an unavailable backend with generated readings.

The dedicated Raspberry Pi deployment uses `compose.showcase.yml`: Nginx serves
the page and a bounded read-only snapshot; the Python simulator writes through
the private container network. The `demo` Spring profile adds the snapshot and
seven-day retention for the configured demo sensor. Existing APIs remain unchanged.

See [showcase behavior and tests](docs/showcase.md) and
[Raspberry Pi / Cloudflare deployment](docs/showcase-deployment.md).

The [architecture guide (RU)](docs/architecture.md) explains the data flow,
application layers and deployment boundaries. The reusable
[visual design guide (RU)](DESIGN_SYSTEM.md) defines the palette, typography and
component principles for this and future project sites.
The intended public address is `https://telemetry.adskiyponchik.dev`; follow the
deployment checks before treating it as live.

## ✨ Features

- 📥 **Telemetry ingestion** — validated `POST` endpoint (Jakarta Bean Validation) for sensor readings
- 🚨 **Anomaly detection** — every reading is classified as `OK`, `ALARM`, `MECHANICAL_DAMAGE` or both, based on configurable voltage/frequency corridors and per-sensor temperature/vibration thresholds
- 📊 **Analytics API** — per-sensor aggregates over a sliding time window (reading count, alarm count, average voltage & frequency)
- ⚙️ **Externalized configuration** — all limits (`198–242 V`, `49–51 Hz`, max temperature/vibration, analytics window) live in `application.yaml` via `@ConfigurationProperties`
- 🧩 **Per-sensor thresholds** — override defaults for individual sensors, stored in the database
- 🩺 **Observability** — Spring Boot Actuator (`health`, `info`, `metrics`)
- 🛰️ **Sensor simulator** — `simulator.py` streams sinusoidal voltage with noise and ~2% random fault spikes
- 🧪 **Tests** — service-layer unit tests + Spring Boot integration tests
- 🌗 **Profiles** — separate `dev` / `prod` configurations

## 🏗️ Architecture

```
simulator.py ──POST──▶ TelemetryController ──▶ TelemetryService ──▶ PostgreSQL
                                                    │  (validation + status
                                                    │   classification)
                       AnalyticsController ◀──── AnalyticsService
                        GET /analytics/{id}        (windowed aggregation)
```

Classic layered design: `controller → service → repository`, DTOs decoupled from JPA entities, centralized error handling via `@RestControllerAdvice` (`GlobalExceptionHandler`).

## 🔌 API

| Method | Endpoint                        | Description                                        |
|--------|---------------------------------|----------------------------------------------------|
| `POST` | `/api/v1/telemetry`             | Ingest a sensor reading → `201 Created`            |
| `GET`  | `/api/v1/telemetry`             | Paginated list of stored telemetry (`Pageable`)    |
| `GET`  | `/api/v1/analytics/{sensorId}`  | Aggregated stats for a sensor (24h window default) |

**Example request:**

```bash
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{
    "sensorId": "MGD-PWR-01",
    "location": "Magdeburg-Nord",
    "voltage": 231.4,
    "current": 5.1,
    "frequency": 50.02,
    "temperature": 36.5,
    "vibration": 3.2
  }'
```

**Example analytics response:**

```json
{
  "sensorId": "MGD-PWR-01",
  "totalReadings": 1440,
  "alarmCount": 27,
  "avgVoltage": 230.12,
  "avgFrequency": 49.98
}
```

## 🚀 Getting started

**Prerequisites:** JDK 21, PostgreSQL, Python 3 (for the simulator).

```bash
# 1. Start the service (dev profile is active by default)
./mvnw spring-boot:run

# 2. Feed it with simulated sensor data
pip install requests
python simulator.py
```

Run tests (requires a running Docker daemon accessible to your user;
Testcontainers creates a temporary PostgreSQL instance):

```bash
./mvnw test
```

## Continuous integration

GitHub Actions runs on pushes to `main`, pull requests targeting `main`, and manual
runs from the **Actions → CI → Run workflow** page once the workflow is on the default branch.

- **Build and test** uses Temurin JDK 21 and the Maven Wrapper to run
  `./mvnw --batch-mode --no-transfer-progress clean verify`. This includes unit,
  MVC, and PostgreSQL integration tests, Flyway migrations, JAR packaging, and JaCoCo coverage.
- The build job also runs the JavaScript classification fixtures and Python simulator tests.
- Testcontainers manages PostgreSQL on the runner. No `.env`, repository secrets,
  external database, or `docker compose up` is required.
- **Build Docker image** checks the multi-stage Java Dockerfile and the showcase /
  simulator images after tests pass. Images are not pushed to a registry or deployed.
- Each run saves available test/coverage reports for 14 days, including test reports
  after failures, and the application JAR for 7 days after a successful build.
  Coverage is generated during `verify`, so it may be absent when tests fail.
- Older runs for the same branch or pull request are cancelled. Jobs have a
  15-minute timeout and the workflow token has read-only repository access.

Mockito is loaded explicitly as a test JVM agent on Java 21, alongside JaCoCo.
No extra `JAVA_TOOL_OPTIONS` are needed. To reproduce both checks locally:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
docker build --tag telemetry-service:ci .
```

After the first successful GitHub run, configure a repository ruleset or branch
protection for `main` requiring **Build and test** and **Build Docker image** before
merging. This is a repository setting, not something the workflow enables itself.

Dependabot checks GitHub Actions, Maven dependencies, and Docker base images weekly.
Action revisions are pinned to commit hashes; review and merge update pull requests
to keep them current.

## ⚙️ Configuration

Key settings in `src/main/resources/application.yaml`:

```yaml
telemetry:
  voltage:    { min: 198.0, max: 242.0 }   # nominal grid corridor
  frequency:  { min: 49.0,  max: 51.0 }
  thresholds:
    default-max-temperature: 80.0
    default-max-vibration: 10.0
  analytics:
    window-hours: 24
```

## 🛠️ Tech stack

Java 21 · Spring Boot 4 (Web MVC, Data JPA, Validation, Actuator) · PostgreSQL · Lombok · Maven · Python (simulator)

## 📄 License

See [LICENCE](LICENCE).
