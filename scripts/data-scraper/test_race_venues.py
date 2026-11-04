"""Tests for the alternate race courses the race scraper files under a race.

Run from the repo root:

    python -m unittest discover -s scripts/data-scraper -p "test_*.py"

Every fixture is invented. Nothing here touches the network.
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import main  # noqa: E402

JBC_SPRINT = {
    "name_en": "JBC Sprint",
    "terrain": 2,
    "distance": 1200,
    "alts": [
        {"track": 10103, "direction": 2, "distance": 1400},
        {"track": 10104, "direction": 2, "distance": 1000},
        {"track": 10105, "direction": 2},
        {"track": 10103, "direction": 2, "distance": 1400, "did_not_exist": "pre_2_5th_anni"},
    ],
}


class RaceVenuesTest(unittest.TestCase):
    def test_gametora_alts_become_labelled_venues_and_a_missing_distance_is_the_base_distance(self):
        venues = main.RaceScraper._venues(JBC_SPRINT, 2)
        self.assertEqual(
            [(v["nameFormatted"], v["distanceType"]) for v in venues],
            [
                ("Kawasaki Dirt 1400m (Sprint) Left", "Sprint"),
                ("Funabashi Dirt 1000m (Sprint) Left", "Sprint"),
                ("Morioka Dirt 1200m (Sprint) Left", "Sprint"),
            ],
        )

    def test_trainee_courses_are_filed_for_their_career_year_only(self):
        takarazuka = {"name_en": "Takarazuka Kinen", "terrain": 1, "distance": 2200}
        self.assertEqual(
            [v["nameFormatted"] for v in main.RaceScraper._venues(takarazuka, 3)],
            ["Kyoto Turf 2200m (Med) Right / Outer"],
        )
        self.assertEqual(main.RaceScraper._venues(takarazuka, 2), [])
        tenno = {"name_en": "Tenno Sho (Spring)", "terrain": 1, "distance": 3200}
        self.assertEqual(
            [v["nameFormatted"] for v in main.RaceScraper._venues(tenno, 3)],
            ["Hanshin Turf 3200m (Long) Right / Outer-Inner"],
        )
        satsuki = {"name_en": "Satsuki Sho", "terrain": 1, "distance": 2000}
        self.assertEqual(
            [v["nameFormatted"] for v in main.RaceScraper._venues(satsuki, 2)],
            ["Tokyo Turf 2000m (Med) Left"],
        )

    def test_a_race_without_alternates_has_none(self):
        self.assertEqual(main.RaceScraper._venues({"name_en": "Arima Kinen", "terrain": 1, "distance": 2500}, 3), [])

    def test_the_label_spells_ooi_as_oi_and_keeps_the_course_suffix(self):
        self.assertEqual(main.RaceScraper._name_formatted("Ooi", "Dirt", 2000, "Right", None), "Oi Dirt 2000m (Med) Right")
        self.assertEqual(main.RaceScraper._name_formatted("Kyoto", "Turf", 2200, "Right", "Outer"), "Kyoto Turf 2200m (Med) Right / Outer")


if __name__ == "__main__":
    unittest.main()
