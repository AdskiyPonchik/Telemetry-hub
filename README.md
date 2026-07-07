# ⚡ Telemetry Hub

**Industrial IoT telemetry ingestion & analytics service** built with Spring Boot.

Receives readings from power-grid sensors (voltage, current, frequency, temperature, vibration), validates them, detects anomalies in real time and exposes aggregated analytics over a REST API. Ships with a Python sensor simulator that generates realistic telemetry — including random fault injection — so the whole pipeline can be demoed locally.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-runtime-336791?logo=postgresql&logoColor=white)
![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven)

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

Run tests:

```bash
./mvnw test
```

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
