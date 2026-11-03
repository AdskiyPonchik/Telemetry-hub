"""Exercise a LOCAL application's write API with a synthetic, isolated sensor.

Run against the application, not the public read-only showcase proxy:
  python3 tests/check_incident_lifecycle.py http://127.0.0.1:8080
Use --save-state /tmp/incident-state.json, restart the app, then --verify-state
with that file to verify durable state. The database must stay running.
"""
import argparse
import json
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen
import uuid


def request(base, path, method="GET", body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = Request(base + path, data=data, method=method,
                  headers={"Content-Type": "application/json"} if data else {})
    try:
        response = urlopen(req, timeout=10)
    except HTTPError as error:
        response = error
    with response:
        return response.code, json.loads(response.read())


def get(base, path):
    code, body = request(base, path)
    assert code == 200, (path, code, body)
    return body


def check(base, sensor):
    def reading(temperature):
        return {"eventId": str(uuid.uuid4()), "sensorId": sensor, "location": "Local acceptance",
                "voltage": 230.0, "current": 5.0, "frequency": 50.0,
                "temperature": temperature, "vibration": 2.0}

    def submit(temperature):
        body = reading(temperature)
        code, receipt = request(base, "/api/v1/telemetry", "POST", body)
        assert code == 201, (code, receipt)
        return body, receipt

    first, receipt = submit(81)
    assert request(base, "/api/v1/telemetry", "POST", first) == (200, receipt)
    conflicting = {**first, "temperature": 82.0}
    assert request(base, "/api/v1/telemetry", "POST", conflicting)[0] == 409
    state = get(base, "/api/v1/temperature-alerts/" + sensor)
    assert state["consecutiveBreaches"] == 1 and not state["alertActive"], state
    submit(82)
    submit(83)
    history_path = "/api/v1/temperature-incidents?sensorId=" + sensor
    incident = get(base, history_path)["content"][0]
    incident_path = "/api/v1/temperature-incidents/" + str(incident["id"])
    code, acknowledged = request(base, incident_path + "/acknowledgement", "PUT")
    assert code == 200 and acknowledged["active"] and acknowledged["acknowledgedAt"], acknowledged
    assert request(base, incident_path + "/acknowledgement", "PUT") == (200, acknowledged)
    for normal in range(1, 4):
        submit(80)
        state = get(base, "/api/v1/temperature-alerts/" + sensor)
        assert state["alertActive"] == (normal < 3), state
        if normal < 3:
            assert state["consecutiveNormalReadings"] == normal, state
    recovered = get(base, incident_path)
    assert recovered["recoveredAt"] and not recovered["active"], recovered
    assert recovered["acknowledgedAt"] == acknowledged["acknowledgedAt"]
    for _ in range(3):
        submit(90)
    active = get(base, history_path)["content"][0]
    assert active["active"] and active["id"] != recovered["id"], active
    print("PASS: 201/200/409, unique-event streak, acknowledgement, three-step recovery and reopening")
    return {"sensor": sensor, "firstEvent": first, "receipt": receipt,
            "recovered": recovered, "active": active}


def verify(base, state):
    assert request(base, "/api/v1/telemetry", "POST", state["firstEvent"]) == (200, state["receipt"])
    for name in ["active", "recovered"]:
        incident = state[name]
        assert get(base, "/api/v1/temperature-incidents/" + str(incident["id"])) == incident
    assert get(base, "/api/v1/temperature-alerts/" + state["sensor"])["alertActive"]
    print("PASS: original receipt, counters, active/recovered incidents and acknowledgement survived app restart")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base", nargs="?", default="http://127.0.0.1:8080")
    parser.add_argument("--sensor", default="HTTP-CHECK-" + uuid.uuid4().hex[:16])
    parser.add_argument("--save-state", type=Path)
    parser.add_argument("--verify-state", type=Path)
    args = parser.parse_args()
    if args.verify_state:
        verify(args.base.rstrip("/"), json.loads(args.verify_state.read_text()))
    else:
        state = check(args.base.rstrip("/"), args.sensor)
        if args.save_state:
            args.save_state.write_text(json.dumps(state, indent=2))
