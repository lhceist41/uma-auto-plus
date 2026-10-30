"""Tests for the per-card retry in fetch_gametora_event_data.

Run from the repo root:

    python -m unittest discover -s scripts/data-scraper -p "test_*.py"

`requests.get` and `time.sleep` are replaced, so nothing touches GameTora or waits.
"""

import sys
import unittest
from pathlib import Path
from unittest import mock

import requests

sys.path.insert(0, str(Path(__file__).resolve().parent))

import main  # noqa: E402


class FakeResponse:
    def __init__(self, payload=None, status_error=None):
        self.payload = payload if payload is not None else {"pageProps": {"eventData": {"en": '{"random": []}'}}}
        self.status_error = status_error

    def raise_for_status(self):
        if self.status_error:
            raise self.status_error

    def json(self):
        return self.payload


class EventFetchRetryTest(unittest.TestCase):
    def setUp(self):
        patches = [
            mock.patch.object(main, "_gametora_build_id", "build"),
            mock.patch.object(main.time, "sleep"),
        ]
        self.sleep = patches[1].start()
        patches[0].start()
        for patch in patches:
            self.addCleanup(patch.stop)

    def test_dropped_connection_is_retried(self):
        get = mock.Mock(side_effect=[requests.exceptions.ConnectionError("closed"), FakeResponse()])
        with mock.patch.object(main.requests, "get", get):
            self.assertEqual(main.fetch_gametora_event_data("supports", "card"), {"random": []})
        self.assertEqual(get.call_count, 2)
        self.sleep.assert_called_once_with(3.0)

    def test_timeout_is_retried(self):
        get = mock.Mock(side_effect=[requests.exceptions.Timeout("slow"), requests.exceptions.Timeout("slow"), FakeResponse()])
        with mock.patch.object(main.requests, "get", get):
            self.assertEqual(main.fetch_gametora_event_data("characters", "card"), {"random": []})
        self.assertEqual(get.call_count, 3)

    def test_gives_up_after_retries_and_raises(self):
        get = mock.Mock(side_effect=requests.exceptions.ConnectionError("no route"))
        with mock.patch.object(main.requests, "get", get):
            with self.assertRaises(requests.exceptions.ConnectionError):
                main.fetch_gametora_event_data("supports", "card")
        self.assertEqual(get.call_count, 3)
        self.assertEqual(self.sleep.call_count, 2)

    def test_http_error_is_not_retried(self):
        error = requests.exceptions.HTTPError("404")
        get = mock.Mock(return_value=FakeResponse(status_error=error))
        with mock.patch.object(main.requests, "get", get):
            with self.assertRaises(requests.exceptions.HTTPError):
                main.fetch_gametora_event_data("supports", "card")
        self.assertEqual(get.call_count, 1)
        self.sleep.assert_not_called()


if __name__ == "__main__":
    unittest.main()
