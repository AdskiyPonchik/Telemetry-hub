"""Smoke-check a running showcase through its PUBLIC proxy (stdlib only).

Usage: python3 tests/check_showcase_http.py http://127.0.0.1:8082
Does not send a valid reading or alter the database.
"""
import json
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def request(base, path, method="GET", body=None):
    req = Request(base + path, data=body, method=method,
                  headers={"Content-Type": "application/json"} if body else {})
    try:
        with urlopen(req, timeout=10) as response:
            return response.status, response.read(), response.headers
    except HTTPError as error:
        return error.code, error.read(), error.headers


def check(base):
    code, body, _ = request(base, "/")
    assert code == 200 and b"Telemetry" in body, ("static page", code)
    code, body, headers = request(base, "/api/v1/demo/snapshot")
    assert code == 200, ("snapshot", code, body[:200])
    snapshot = json.loads(body)
    assert {"sensor", "thresholds", "generatedAt", "latest", "readings", "analytics"} <= snapshot.keys()
    assert snapshot["generatedAt"].endswith(("Z", "+00:00"))
    readings = snapshot["readings"]
    assert len(readings) <= 180
    assert readings == sorted(readings, key=lambda r: (r["timestamp"], r["id"]))
    assert all(r["sensorId"] == snapshot["sensor"]["id"] for r in readings)
    assert all(r["timestamp"].endswith(("Z", "+00:00")) for r in readings)
    assert "json" in headers.get("Content-Type", "")
    code, _, headers = request(base, "/app.mjs")
    assert code == 200 and "javascript" in headers.get("Content-Type", ""), "module MIME type"
    code, _, _ = request(base, "/api/v1/demo/snapshot?size=100000")
    assert code == 400, ("snapshot query parameters must be rejected", code)
    for path in ["/api/v1/telemetry", "/api/v1/analytics/MGD-PWR-01",
                 "/actuator/health", "/v3/api-docs", "/swagger-ui/index.html"]:
        code, _, _ = request(base, path)
        assert code in (403, 404), ("private route exposed", path, code)
    for path in ["/api/v1/telemetry", "/api/v1/demo/snapshot"]:
        code, _, _ = request(base, path, "POST", b"{}")
        assert code in (403, 404, 405), ("write route exposed", path, code)
    print(f"PASS: public page, bounded UTC snapshot ({len(readings)} points), private routes and writes blocked")


if __name__ == "__main__":
    check(sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://127.0.0.1:8082")
