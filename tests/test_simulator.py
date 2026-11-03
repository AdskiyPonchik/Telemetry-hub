import io
import math
from pathlib import Path
import random
import sys
import unittest
from unittest.mock import Mock, patch
from uuid import UUID

import requests


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import simulator


class ConfigTest(unittest.TestCase):
    def test_random_defaults_preserve_one_second_mode(self):
        config = simulator.load_config({})

        self.assertEqual("random", config.mode)
        self.assertEqual(1.0, config.interval_seconds)
        self.assertEqual(simulator.DEFAULT_API_URL, config.api_url)

    def test_demo_alias_uses_showcase_defaults_and_custom_identity(self):
        config = simulator.load_config(
            {
                "TELEMETRY_SIMULATOR_MODE": "demo",
                "TELEMETRY_DEMO_SENSOR_ID": "LINE-7",
                "TELEMETRY_DEMO_LOCATION": "Assembly",
            }
        )

        self.assertEqual("showcase", config.mode)
        self.assertEqual(5.0, config.interval_seconds)
        self.assertEqual("LINE-7", config.sensor_id)
        self.assertEqual("Assembly", config.location)

    def test_rejects_invalid_mode_url_and_non_finite_timing(self):
        invalid_environments = [
            {"TELEMETRY_SIMULATOR_MODE": "burst"},
            {"TELEMETRY_API_URL": "app:8080/api/v1/telemetry"},
            {"TELEMETRY_SIMULATOR_INTERVAL_SECONDS": "0"},
            {"TELEMETRY_REQUEST_TIMEOUT_SECONDS": "nan"},
            {"TELEMETRY_REQUEST_TIMEOUT_SECONDS": str(math.inf)},
        ]

        for environment in invalid_environments:
            with self.subTest(environment=environment):
                with self.assertRaises(ValueError):
                    simulator.load_config(environment)


class ShowcaseCycleTest(unittest.TestCase):
    def test_cycle_boundaries_follow_elapsed_monotonic_time(self):
        cases = {
            0.0: "normal",
            39.999: "normal",
            40.0: "electrical",
            59.999: "electrical",
            60.0: "mechanical",
            79.999: "mechanical",
            80.0: "combined",
            99.999: "combined",
            100.0: "normal",
            119.999: "normal",
            120.0: "normal",
            160.0: "electrical",
        }

        for elapsed, expected in cases.items():
            with self.subTest(elapsed=elapsed):
                self.assertEqual(expected, simulator.showcase_phase(elapsed))

    def test_showcase_jitter_cannot_cross_status_thresholds(self):
        for elapsed, electrical, mechanical in [
            (10, False, False),
            (45, True, False),
            (65, False, True),
            (85, True, True),
            (110, False, False),
        ]:
            for seed in range(20):
                reading = simulator.generate_showcase_telemetry(
                    elapsed, rng=random.Random(seed)
                )
                self.assertEqual(electrical, reading["voltage"] > 242.0)
                self.assertEqual(
                    mechanical,
                    reading["temperature"] > 80.0
                    or reading["vibration"] > 10.0,
                )


class SubmissionTest(unittest.TestCase):
    def setUp(self):
        self.config = simulator.load_config(
            {
                "TELEMETRY_API_URL": "http://app:8080/api/v1/telemetry",
                "TELEMETRY_REQUEST_TIMEOUT_SECONDS": "2.5",
            }
        )
        self.reading = simulator.generate_showcase_telemetry(0, rng=random.Random(1))

    def test_201_is_logged_as_accepted_without_claiming_domain_ok(self):
        response = Mock(status_code=201, text="")
        post = Mock(return_value=response)

        with patch("sys.stdout", new_callable=io.StringIO) as output:
            accepted = simulator.submit_reading(self.config, self.reading, post)

        self.assertTrue(accepted)
        self.assertEqual(1, post.call_count)
        self.assertEqual(2.5, post.call_args.kwargs["timeout"])
        self.assertIn("accepted:", output.getvalue())
        self.assertNotIn("-> OK", output.getvalue())

    def test_new_measurements_have_fresh_valid_uuids_even_with_identical_rng(self):
        for generator in [simulator.generate_showcase_telemetry, simulator.generate_random_telemetry]:
            first = generator(0, rng=random.Random(1))
            second = generator(0, rng=random.Random(1))
            self.assertEqual(4, UUID(first["eventId"]).version)
            self.assertEqual(4, UUID(second["eventId"]).version)
            self.assertNotEqual(first["eventId"], second["eventId"])
            self.assertEqual(
                {key: value for key, value in first.items() if key != "eventId"},
                {key: value for key, value in second.items() if key != "eventId"},
            )

    def test_timeout_and_5xx_retry_with_same_identity_and_payload_then_accept_200(self):
        original = self.reading.copy()
        post = Mock(side_effect=[
            requests.Timeout("response lost"),
            Mock(status_code=503, text="unavailable"),
            Mock(status_code=200, text=""),
        ])
        sleep = Mock()

        with patch("sys.stdout", new_callable=io.StringIO) as output:
            accepted = simulator.submit_reading(self.config, self.reading, post, sleep)

        self.assertTrue(accepted)
        self.assertEqual(3, post.call_count)
        self.assertEqual(original, self.reading)
        for call in post.call_args_list:
            self.assertEqual(original, call.kwargs["json"])
            self.assertEqual(2.5, call.kwargs["timeout"])
        self.assertEqual([0.5, 1.0], [call.args[0] for call in sleep.call_args_list])
        self.assertIn("accepted: duplicate receipt", output.getvalue())
        self.assertNotIn("-> OK", output.getvalue())

    def test_transient_failure_stops_after_three_total_attempts_and_reports_loss(self):
        for outcome in [requests.ConnectionError("offline"), Mock(status_code=500, text="failed")]:
            with self.subTest(outcome=outcome):
                post = Mock(side_effect=outcome) if isinstance(outcome, Exception) else Mock(return_value=outcome)
                sleep = Mock()
                with patch("sys.stdout", new_callable=io.StringIO) as output:
                    accepted = simulator.submit_reading(self.config, self.reading, post, sleep)
                self.assertFalse(accepted)
                self.assertEqual(3, post.call_count)
                self.assertEqual(2, sleep.call_count)
                self.assertIn("observation discarded locally", output.getvalue())
                self.assertIn("delivery unconfirmed", output.getvalue())
                self.assertIn("no persistent queue", output.getvalue())

    def test_4xx_including_conflict_are_never_retried(self):
        for status in [400, 401, 403, 404, 409, 429]:
            with self.subTest(status=status):
                post = Mock(return_value=Mock(status_code=status, text="rejected"))
                sleep = Mock()
                with patch("sys.stdout", new_callable=io.StringIO) as output:
                    accepted = simulator.submit_reading(self.config, self.reading, post, sleep)
                self.assertFalse(accepted)
                self.assertEqual(1, post.call_count)
                sleep.assert_not_called()
                self.assertIn(f"rejected: HTTP {status}", output.getvalue())

    def test_201_after_retry_is_reported_as_created(self):
        post = Mock(side_effect=[requests.Timeout("timeout"), Mock(status_code=201, text="")])
        with patch("sys.stdout", new_callable=io.StringIO) as output:
            self.assertTrue(simulator.submit_reading(self.config, self.reading, post, Mock()))
        self.assertEqual(2, post.call_count)
        self.assertIn("accepted: created", output.getvalue())


class MainTest(unittest.TestCase):
    def test_keyboard_interrupt_stops_cleanly(self):
        with (
            patch("simulator.load_config", return_value=simulator.load_config({})),
            patch("simulator.run", side_effect=KeyboardInterrupt),
            patch("sys.stdout", new_callable=io.StringIO) as output,
        ):
            exit_code = simulator.main()

        self.assertEqual(0, exit_code)
        self.assertIn("simulator stopped", output.getvalue())


if __name__ == "__main__":
    unittest.main()
