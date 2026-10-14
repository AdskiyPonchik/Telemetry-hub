"""Generate telemetry readings and submit them to Telemetry Hub."""

from __future__ import annotations

from dataclasses import dataclass
import math
import os
import random
import time
from typing import Callable, Mapping
from urllib.parse import urlparse

import requests


DEFAULT_API_URL = "http://localhost:8080/api/v1/telemetry"
DEFAULT_SENSOR_ID = "MGD-PWR-01"
DEFAULT_LOCATION = "Magdeburg-Nord"
SHOWCASE_CYCLE_SECONDS = 120.0

# Keep the original module-level names available to existing local scripts.
API_URL = DEFAULT_API_URL
SENSOR_ID = DEFAULT_SENSOR_ID
LOCATION = DEFAULT_LOCATION


@dataclass(frozen=True)
class SimulatorConfig:
    api_url: str
    sensor_id: str
    location: str
    mode: str
    interval_seconds: float
    request_timeout_seconds: float


def _positive_finite(name: str, value: str) -> float:
    try:
        parsed = float(value)
    except ValueError as exc:
        raise ValueError(f"{name} must be a number") from exc
    if not math.isfinite(parsed) or parsed <= 0:
        raise ValueError(f"{name} must be finite and greater than zero")
    return parsed


def _required_text(name: str, value: str) -> str:
    cleaned = value.strip()
    if not cleaned:
        raise ValueError(f"{name} must not be blank")
    if len(cleaned) > 255:
        raise ValueError(f"{name} must not exceed 255 characters")
    return cleaned


def load_config(environ: Mapping[str, str] | None = None) -> SimulatorConfig:
    env = os.environ if environ is None else environ
    raw_mode = env.get("TELEMETRY_SIMULATOR_MODE", "random").strip().lower()
    mode = "showcase" if raw_mode == "demo" else raw_mode
    if mode not in {"random", "showcase"}:
        raise ValueError("TELEMETRY_SIMULATOR_MODE must be random, showcase, or demo")

    api_url = env.get("TELEMETRY_API_URL", DEFAULT_API_URL).strip()
    parsed_url = urlparse(api_url)
    if parsed_url.scheme not in {"http", "https"} or not parsed_url.netloc:
        raise ValueError("TELEMETRY_API_URL must be an absolute HTTP(S) URL")
    if parsed_url.query or parsed_url.fragment:
        raise ValueError("TELEMETRY_API_URL must not contain a query or fragment")

    default_interval = "5" if mode == "showcase" else "1"
    return SimulatorConfig(
        api_url=api_url,
        sensor_id=_required_text(
            "TELEMETRY_DEMO_SENSOR_ID",
            env.get("TELEMETRY_DEMO_SENSOR_ID", DEFAULT_SENSOR_ID),
        ),
        location=_required_text(
            "TELEMETRY_DEMO_LOCATION",
            env.get("TELEMETRY_DEMO_LOCATION", DEFAULT_LOCATION),
        ),
        mode=mode,
        interval_seconds=_positive_finite(
            "TELEMETRY_SIMULATOR_INTERVAL_SECONDS",
            env.get("TELEMETRY_SIMULATOR_INTERVAL_SECONDS", default_interval),
        ),
        request_timeout_seconds=_positive_finite(
            "TELEMETRY_REQUEST_TIMEOUT_SECONDS",
            env.get("TELEMETRY_REQUEST_TIMEOUT_SECONDS", "3"),
        ),
    )


def _reading(
    sensor_id: str,
    location: str,
    voltage: float,
    temperature: float,
    vibration: float,
    rng=random,
) -> dict[str, float | str]:
    return {
        "sensorId": sensor_id,
        "location": location,
        "voltage": round(voltage, 2),
        "current": round(rng.uniform(4.8, 5.2), 2),
        "frequency": round(50.0 + rng.uniform(-0.03, 0.03), 2),
        "temperature": round(temperature, 2),
        "vibration": round(vibration, 2),
    }


def generate_random_telemetry(
    step: int,
    sensor_id: str = DEFAULT_SENSOR_ID,
    location: str = DEFAULT_LOCATION,
    rng=random,
) -> dict[str, float | str]:
    """Preserve the original random, one-reading-per-step simulator behavior."""
    voltage = 230.0 + 5 * math.sin(step / 10) + rng.uniform(-0.5, 0.5)
    if rng.random() < 0.02:
        voltage = rng.choice([190.0, 260.0])
    return _reading(
        sensor_id,
        location,
        voltage,
        rng.uniform(-20.0, 50.0),
        rng.uniform(0.0, 15.0),
        rng,
    )


def showcase_phase(elapsed_seconds: float) -> str:
    position = elapsed_seconds % SHOWCASE_CYCLE_SECONDS
    if position < 40:
        return "normal"
    if position < 60:
        return "electrical"
    if position < 80:
        return "mechanical"
    if position < 100:
        return "combined"
    return "normal"


def generate_showcase_telemetry(
    elapsed_seconds: float,
    sensor_id: str = DEFAULT_SENSOR_ID,
    location: str = DEFAULT_LOCATION,
    rng=random,
) -> dict[str, float | str]:
    """Generate a deterministic-status 120-second showcase cycle with safe jitter."""
    phase = showcase_phase(elapsed_seconds)
    voltage = 230.0 + rng.uniform(-1.0, 1.0)
    temperature = 45.0 + rng.uniform(-1.0, 1.0)
    vibration = 2.0 + rng.uniform(-0.15, 0.15)

    if phase in {"electrical", "combined"}:
        voltage = 260.0 + rng.uniform(-0.5, 0.5)
    if phase in {"mechanical", "combined"}:
        temperature = 90.0 + rng.uniform(-0.5, 0.5)
        vibration = 12.0 + rng.uniform(-0.15, 0.15)

    return _reading(sensor_id, location, voltage, temperature, vibration, rng)


# Backward-compatible import for scripts that used the old function name.
generate_telemetry = generate_random_telemetry


def submit_reading(
    config: SimulatorConfig,
    data: Mapping[str, float | str],
    post: Callable[..., requests.Response] = requests.post,
) -> bool:
    """Submit once. A failed observation is not retried because POST is not idempotent."""
    try:
        response = post(
            config.api_url,
            json=dict(data),
            timeout=config.request_timeout_seconds,
        )
    except requests.RequestException as exc:
        print(f"send failed: {exc}")
        return False

    if response.status_code == 201:
        print(
            "accepted: "
            f"voltage={data['voltage']}V temperature={data['temperature']}C "
            f"vibration={data['vibration']}"
        )
        return True

    message = response.text.strip().replace("\n", " ")[:160]
    suffix = f" - {message}" if message else ""
    print(f"rejected: HTTP {response.status_code}{suffix}")
    return False


def run(config: SimulatorConfig) -> None:
    print(
        f"simulator started: sensor={config.sensor_id} mode={config.mode} "
        f"interval={config.interval_seconds:g}s"
    )
    started_at = time.monotonic()
    next_send_at = started_at
    step = 0

    while True:
        elapsed = time.monotonic() - started_at
        if config.mode == "showcase":
            data = generate_showcase_telemetry(
                elapsed, config.sensor_id, config.location
            )
        else:
            data = generate_random_telemetry(step, config.sensor_id, config.location)
        submit_reading(config, data)
        step += 1

        next_send_at += config.interval_seconds
        now = time.monotonic()
        if next_send_at < now:
            next_send_at = now
        time.sleep(max(0.0, next_send_at - now))


def main() -> int:
    try:
        config = load_config()
    except ValueError as exc:
        print(f"configuration error: {exc}")
        return 2

    try:
        run(config)
    except KeyboardInterrupt:
        print("simulator stopped")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
