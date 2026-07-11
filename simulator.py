import requests
import time
import random
import math

# Config
API_URL = "http://localhost:8080/api/v1/telemetry"
SENSOR_ID = "MGD-PWR-01"
LOCATION = "Magdeburg-Nord"

def generate_telemetry(step):
    # Basic stats
    base_voltage = 230.0
    base_frequency = 50.0

    # 1. Voltage as sinusoid and shift
    voltage = base_voltage + 5 * math.sin(step / 10) + random.uniform(-0.5, 0.5)

    # 2. Some accident once per ~50 iterations
    if random.random() < 0.02:
        voltage = random.choice([190.0, 260.0])
        print(f"!!! Alarm: {voltage}V")

    temperature = random.uniform(-20.0, 50.0)
    vibration = random.uniform(0, 15.0)
    return {
        "sensorId": SENSOR_ID,
        "location": LOCATION,
        "voltage": round(voltage, 2),
        "current": round(random.uniform(4.5, 5.5), 2),
        "frequency": round(base_frequency + random.uniform(-0.05, 0.05), 2),
        "temperature": temperature,
        "vibration": vibration
    }

def main():
    print(f"Start simulator for {SENSOR_ID}...")
    step = 0
    while True:
        data = generate_telemetry(step)
        try:
            response = requests.post(API_URL, json=data)
            if response.status_code == 201:
                print(f"[sent] V: {data['voltage']}V, F: {data['frequency']}Hz -> OK")
            else:
                print(f"API error: {response.status_code} - {response.text}")
        except Exception as e:
            print(f"Connection error: {e}")

        step += 1
        time.sleep(1) # Sending data every second

if __name__ == "__main__":
    main()