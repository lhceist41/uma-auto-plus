"""Tests for the mandatory-objective race-entry fan gate (`fansNeeded`).

Run from the repo root:

    python -m unittest discover -s scripts/data-scraper -p "test_*.py"

The objective scraper keeps two distinct fan values per mandatory race: `fans` is the fan REWARD
(GameTora `fans_gained`) and `fansNeeded` is the fan ENTRY GATE (`fans_needed`) required before the
race. Every fixture is invented; nothing here touches the network.
"""

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import main  # noqa: E402


def _run_scraper(objectives, characters):
    """Drives CharacterObjectivesScraper.start() against in-memory manifests, returns the written data."""
    manifests = {"ura-objectives": objectives, "characters": characters}
    original = main.fetch_gametora_manifest_data
    main.fetch_gametora_manifest_data = lambda name: manifests[name]
    try:
        scraper = main.CharacterObjectivesScraper()
        with tempfile.TemporaryDirectory() as tmp:
            scraper.output_filename = str(Path(tmp) / "character_objectives.json")
            scraper.start()
            return json.loads(Path(scraper.output_filename).read_text(encoding="utf-8"))
    finally:
        main.fetch_gametora_manifest_data = original


def _chars(char_id=1, name="Test Char"):
    return [{"char_id": char_id, "en_name": name, "playable_en": True}]


def _race(name, *, gained=1000, needed=0, grade=100, terrain=1, distance=2000, include_needed=True):
    r = {"name_en": name, "grade": grade, "terrain": terrain, "distance": distance, "fans_gained": gained}
    if include_needed:
        r["fans_needed"] = needed
    return r


def _objectives(races, *, turn=47, char_id=1):
    return [{"char_id": char_id, "objectives": [{"target_type": 1, "turn": turn, "races": races}]}]


class ObjectiveFansNeededTest(unittest.TestCase):
    def _only_option(self, data, name="Test Char"):
        races = data[name]["mandatoryRaces"]
        self.assertEqual(len(races), 1)
        return races[0]["options"]

    def test_gated_race_keeps_reward_and_gate_separate(self):
        data = _run_scraper(_objectives([_race("Champions Cup", gained=10000, needed=12000)]), _chars())
        opt = self._only_option(data)[0]
        self.assertEqual(opt["fans"], 10000, "fans is the reward (fans_gained)")
        self.assertEqual(opt["fansNeeded"], 12000, "fansNeeded is the entry gate (fans_needed)")
        self.assertNotEqual(opt["fans"], opt["fansNeeded"], "reward and gate are distinct fields")

    def test_ungated_race_emits_zero(self):
        data = _run_scraper(_objectives([_race("Some Cup", gained=1800, needed=0)]), _chars())
        self.assertEqual(self._only_option(data)[0]["fansNeeded"], 0)

    def test_missing_fans_needed_defaults_to_zero(self):
        # A source row lacking the key falls back to the explicit 0 default contract.
        data = _run_scraper(_objectives([_race("No Gate Race", gained=1200, include_needed=False)]), _chars())
        opt = self._only_option(data)[0]
        self.assertEqual(opt["fansNeeded"], 0)
        self.assertEqual(opt["fans"], 1200)

    def test_duplicate_identity_same_gate_dedupes(self):
        # Same (raceName, distanceType, surface) with the SAME gate collapses to one option.
        races = [
            _race("Champions Cup", gained=10000, needed=12000, distance=2000),
            _race("Champions Cup", gained=10000, needed=12000, distance=2000),
        ]
        data = _run_scraper(_objectives(races), _chars())
        self.assertEqual(len(self._only_option(data)), 1)

    def test_duplicate_identity_conflicting_gate_raises(self):
        # Same dedup identity with a DIFFERENT gate must fail loudly, not silently drop one.
        races = [
            _race("Champions Cup", gained=10000, needed=12000, distance=2000),
            _race("Champions Cup", gained=10000, needed=9000, distance=2000),
        ]
        with self.assertRaises(ValueError):
            _run_scraper(_objectives(races), _chars())


if __name__ == "__main__":
    unittest.main()
