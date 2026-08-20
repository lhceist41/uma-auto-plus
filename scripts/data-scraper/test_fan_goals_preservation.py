"""Tests for the `fanGoals` preservation guard in CharacterObjectivesScraper.

Run from the repo root:

    python -m unittest discover -s scripts/data-scraper -p "test_*.py"

`fanGoals` is a repo-owned augmentation written into `character_objectives.json` by the separate
scripts/extract-master-route-data.mjs (Grand Concert fan goals mined from master.mdb), not by this
Python scraper. CharacterObjectivesScraper does a full rebuild every run, so without an explicit
carry-over step a bare `python update.py` silently deletes every character's fanGoals. These tests
pin the carry-over behavior so that bug cannot recur silently. Every fixture is invented; nothing
here touches the network.
"""

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import main  # noqa: E402


def _run_scraper(objectives, characters, existing=None):
    """Drives CharacterObjectivesScraper.start() against in-memory manifests, optionally seeding the
    output file with `existing` content first (simulating the committed file before a rebuild).

    Returns the written data.
    """
    manifests = {"ura-objectives": objectives, "characters": characters}
    original = main.fetch_gametora_manifest_data
    main.fetch_gametora_manifest_data = lambda name: manifests[name]
    try:
        scraper = main.CharacterObjectivesScraper()
        with tempfile.TemporaryDirectory() as tmp:
            scraper.output_filename = str(Path(tmp) / "character_objectives.json")
            if existing is not None:
                Path(scraper.output_filename).write_text(json.dumps(existing, ensure_ascii=False), encoding="utf-8")
            scraper.start()
            return json.loads(Path(scraper.output_filename).read_text(encoding="utf-8"))
    finally:
        main.fetch_gametora_manifest_data = original


def _chars(*entries):
    return [{"char_id": cid, "en_name": name, "playable_en": True} for cid, name in entries]


def _race(name, *, gained=1000, needed=0, grade=100, terrain=1, distance=2000):
    return {"name_en": name, "grade": grade, "terrain": terrain, "distance": distance, "fans_gained": gained, "fans_needed": needed}


def _objectives(char_id, races, *, turn=47):
    return [{"char_id": char_id, "objectives": [{"target_type": 1, "turn": turn, "races": races}]}]


_SAMPLE_FAN_GOALS = [{"turn": 23, "targetFans": 3000, "scenarioGroupId": 100, "appliesToScenarioIds": [1, 2, 3, 4]}]


class FanGoalsPreservationTest(unittest.TestCase):
    def test_existing_fan_goals_survive_rebuild(self):
        existing = {"Test Char": {"name": "Test Char", "mandatoryRaces": [], "fanGoals": _SAMPLE_FAN_GOALS}}
        data = _run_scraper(_objectives(1, [_race("Champions Cup")]), _chars((1, "Test Char")), existing=existing)
        self.assertEqual(data["Test Char"]["fanGoals"], _SAMPLE_FAN_GOALS, "fanGoals must survive byte-for-byte")

    def test_gametora_fields_still_update_alongside_preserved_fan_goals(self):
        # The old record's mandatoryRaces (a GameTora-owned field) is deliberately stale/wrong here;
        # the fresh scrape must overwrite it with the new race while fanGoals is untouched.
        existing = {
            "Test Char": {
                "name": "Test Char",
                "mandatoryRaces": [{"turn": 1, "isChoice": False, "options": [{"raceName": "Old Stale Race", "grade": "G1", "surface": "Turf", "distanceType": "Medium", "fans": 1, "fansNeeded": 0}]}],
                "fanGoals": _SAMPLE_FAN_GOALS,
            }
        }
        data = _run_scraper(_objectives(1, [_race("Champions Cup", gained=9999)]), _chars((1, "Test Char")), existing=existing)
        races = data["Test Char"]["mandatoryRaces"]
        self.assertEqual(len(races), 1)
        self.assertEqual(races[0]["options"][0]["raceName"], "Champions Cup", "GameTora-owned field must take the fresh value")
        self.assertEqual(data["Test Char"]["fanGoals"], _SAMPLE_FAN_GOALS, "augmentation field must be untouched by the field update")

    def test_new_character_without_augmentation_gets_no_fabricated_fan_goals(self):
        existing = {"Old Char": {"name": "Old Char", "mandatoryRaces": [], "fanGoals": _SAMPLE_FAN_GOALS}}
        data = _run_scraper(
            _objectives(1, [_race("Champions Cup")]) + _objectives(2, [_race("Rose Stakes")], turn=41),
            _chars((1, "Old Char"), (2, "New Char")),
            existing=existing,
        )
        self.assertNotIn("fanGoals", data["New Char"], "a character absent from the augmentation map must not get a fabricated fanGoals")
        self.assertEqual(data["Old Char"]["fanGoals"], _SAMPLE_FAN_GOALS)

    def test_removed_character_is_not_resurrected_to_keep_fan_goals(self):
        # "Gone Char" carried fanGoals but no longer appears in the fresh EN-playable objectives.
        existing = {"Gone Char": {"name": "Gone Char", "mandatoryRaces": [], "fanGoals": _SAMPLE_FAN_GOALS}}
        data = _run_scraper(_objectives(1, [_race("Champions Cup")]), _chars((1, "Test Char")), existing=existing)
        self.assertNotIn("Gone Char", data, "a character absent from the fresh scrape must not be resurrected merely to keep fanGoals")

    def test_only_fan_goals_is_preserved_not_arbitrary_keys(self):
        # An unrelated, non-augmentation key on the old record must never survive the rebuild.
        existing = {"Test Char": {"name": "Test Char", "mandatoryRaces": [], "fanGoals": _SAMPLE_FAN_GOALS, "someUnrelatedStaleField": "should not survive"}}
        data = _run_scraper(_objectives(1, [_race("Champions Cup")]), _chars((1, "Test Char")), existing=existing)
        self.assertNotIn("someUnrelatedStaleField", data["Test Char"], "only the explicitly-owned fanGoals key may be carried over")
        self.assertEqual(data["Test Char"]["fanGoals"], _SAMPLE_FAN_GOALS)

    def test_character_with_no_prior_fan_goals_is_unaffected(self):
        data = _run_scraper(_objectives(1, [_race("Champions Cup")]), _chars((1, "Test Char")), existing={})
        self.assertNotIn("fanGoals", data["Test Char"])


if __name__ == "__main__":
    unittest.main()
