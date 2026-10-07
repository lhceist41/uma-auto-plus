"""Tests for reading GameTora character names after its `en_name` -> `name_en` rename.

Run from the repo root:

    python -m unittest discover -s scripts/data-scraper -p "test_*.py"

Every fixture is invented; nothing here touches the network.
"""

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import main  # noqa: E402


def _run_objectives(characters):
    """Drives CharacterObjectivesScraper.start() against in-memory manifests, returns the written data."""
    objectives = [{"char_id": 1, "objectives": [{"target_type": 1, "turn": 47, "races": [{"name_en": "Some Cup", "grade": 100, "terrain": 1, "distance": 2000, "fans_gained": 1000}]}]}]
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


class CharacterNameTest(unittest.TestCase):
    def test_prefers_name_en_and_falls_back_to_en_name(self):
        self.assertEqual(main.character_name({"name_en": "A", "en_name": "B"}), "A")
        self.assertEqual(main.character_name({"en_name": "B"}), "B")
        self.assertIsNone(main.character_name({}))

    def test_objectives_keep_a_character_that_only_has_name_en(self):
        data = _run_objectives([{"char_id": 1, "name_en": "Test Char", "playable_en": True}])
        self.assertIn("Test Char", data)

    def test_objectives_still_read_an_older_en_name_manifest(self):
        data = _run_objectives([{"char_id": 1, "en_name": "Test Char", "playable_en": True}])
        self.assertIn("Test Char", data)


if __name__ == "__main__":
    unittest.main()
