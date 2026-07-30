"""Tests for the skill scraper's release gating and cost handling.

Run from the repo root:

    python -m unittest discover -s scripts/data-scraper -p "test_*.py"

Every fixture here is invented. The tests drive SkillScraper.start() with the network calls replaced, so they
never touch GameTora and never write into src/data.
"""

import logging
import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))

import main  # noqa: E402


def make_skill(skill_id, name, cost=None, unreleased=None, iconid=10011, versions=None, **extra):
    """Builds one GameTora-shaped skill record. Omitting `cost` models a skill the shop never sells."""
    skill = {"id": skill_id, "name_en": name, "desc_en": f"{name} description", "iconid": iconid}
    if cost is not None:
        skill["cost"] = cost
    if unreleased is not None:
        skill["unreleased"] = unreleased
    if versions is not None:
        skill["versions"] = versions
    skill.update(extra)
    return skill


class ScrapeResult:
    """The data one patched SkillScraper run produced, plus the log records it emitted."""

    def __init__(self, data, records):
        self.data = data
        self.records = records

    def tier_warning_level(self, name):
        """The level the 'Skill Tier Unknown' line was logged at for `name`, or None if it was not logged."""
        for record in self.records:
            if record.getMessage() == f"Skill Tier Unknown: {name}":
                return record.levelno
        return None


def run_scraper(skills, tier_map=None, evaluation_points=None):
    """Runs SkillScraper.start() against `skills` with every network call and disk write patched out."""
    scraper = main.SkillScraper()
    with mock.patch.object(main, "fetch_gametora_manifest_data", return_value=skills), mock.patch.object(
        main.SkillScraper, "scrape_skill_evaluation_points", return_value=evaluation_points or {}
    ), mock.patch.object(main.SkillScraper, "scrape_skill_tier_list", return_value=tier_map or {}), mock.patch.object(
        main.SkillScraper, "save_data"
    ), mock.patch.object(main, "download_image"), mock.patch.object(main, "write_skill_icon_index"):
        # Swap the root logger's handlers for the capture handler so the assertions can read severities and the
        # run stays quiet on the console. Restored below whatever happens.
        logger = logging.getLogger()
        records = []
        previous_handlers = logger.handlers[:]
        previous_level = logger.level
        logger.handlers = [_Capture(records)]
        logger.setLevel(logging.DEBUG)
        try:
            scraper.start()
        finally:
            logger.handlers = previous_handlers
            logger.setLevel(previous_level)
    return ScrapeResult(scraper.data, records)


class _Capture(logging.Handler):
    def __init__(self, sink):
        super().__init__(level=logging.DEBUG)
        self.sink = sink

    def emit(self, record):
        self.sink.append(record)


class SkillCostRetentionTest(unittest.TestCase):
    def test_priced_global_skill_keeps_its_real_cost(self):
        """1. A skill with a real shop cost is retained at exactly that cost."""
        result = run_scraper([make_skill(900001, "Alpha Dash", cost=170)])
        self.assertIn("Alpha Dash", result.data)
        self.assertEqual(170, result.data["Alpha Dash"]["cost"])
        self.assertNotEqual(main.DEFAULT_SKILL_COST, result.data["Alpha Dash"]["cost"])

    def test_priced_skill_is_kept_even_when_flagged_unreleased(self):
        """2. Priced skills are not gated on the Global flag, which lags reality; behavior is unchanged."""
        result = run_scraper([make_skill(900002, "Bravo Burst", cost=110, unreleased=["en"])])
        self.assertIn("Bravo Burst", result.data)
        self.assertEqual(110, result.data["Bravo Burst"]["cost"])

    def test_costless_global_skill_is_retained_with_the_baseline_cost(self):
        """3. A shop-less skill Global has is kept, carrying the baseline metadata cost."""
        result = run_scraper([make_skill(900003, "Charlie Chant")])
        self.assertIn("Charlie Chant", result.data)
        self.assertEqual(main.DEFAULT_SKILL_COST, result.data["Charlie Chant"]["cost"])

    def test_costless_unreleased_skill_is_excluded(self):
        """4. A shop-less skill Global does not have yet stays out of the database."""
        result = run_scraper([make_skill(900004, "Delta Drift", unreleased=["en"])])
        self.assertNotIn("Delta Drift", result.data)

    def test_costless_skill_unreleased_on_other_servers_only_is_retained(self):
        """4b. Only the Global flag matters; other servers lagging is irrelevant."""
        result = run_scraper([make_skill(900005, "Echo Ember", unreleased=["ko", "zh_tw"])])
        self.assertIn("Echo Ember", result.data)
        self.assertEqual(main.DEFAULT_SKILL_COST, result.data["Echo Ember"]["cost"])

    def test_skill_without_english_name_is_excluded(self):
        """5. No English name means there is no key to store the record under."""
        result = run_scraper([{"id": 900006, "desc_en": "no name", "iconid": 10011, "cost": 90}])
        self.assertEqual({}, result.data)

    def test_tier_warning_severity_differs_for_purchasable_and_costless_skills(self):
        """6. Only a purchasable Global skill missing from the tier list is worth a warning."""
        result = run_scraper(
            [
                make_skill(900007, "Foxtrot Flash", cost=160),
                make_skill(900008, "Golf Gleam"),
                make_skill(900009, "Hotel Haze", cost=160, unreleased=["en"]),
            ]
        )
        self.assertEqual(logging.WARNING, result.tier_warning_level("Foxtrot Flash"))
        self.assertEqual(logging.DEBUG, result.tier_warning_level("Golf Gleam"))
        self.assertEqual(logging.DEBUG, result.tier_warning_level("Hotel Haze"))

    def test_negative_skill_never_warns_about_the_tier_list(self):
        """6b. Negative skills (icon id ending in 4) are never ranked, so a miss is not notable."""
        result = run_scraper([make_skill(900010, "India Inertia", cost=40, iconid=10014)])
        self.assertIsNone(result.tier_warning_level("India Inertia"))

    def test_upgrade_chain_stays_valid_when_a_costless_skill_participates(self):
        """7. A retained shop-less skill links into its chain exactly like a priced one.

        GameTora's convention: the lower id is the higher tier, and `versions` lists the other links only. Here the
        base tier is the shop-less one, so the chain only resolves if it was retained.
        """
        result = run_scraper(
            [
                make_skill(900011, "Juliet Jog Sharp", cost=100, versions=[900012]),
                make_skill(900012, "Juliet Jog", versions=[900011]),
            ]
        )
        self.assertIn("Juliet Jog Sharp", result.data)
        self.assertIn("Juliet Jog", result.data)
        self.assertEqual(main.DEFAULT_SKILL_COST, result.data["Juliet Jog"]["cost"])
        self.assertEqual(900011, result.data["Juliet Jog"]["upgrade"])
        self.assertEqual(900012, result.data["Juliet Jog Sharp"]["downgrade"])

    def test_chain_pointer_is_dropped_when_the_other_link_is_excluded(self):
        """7b. An excluded link never leaves a pointer to a skill that is not in the database."""
        result = run_scraper(
            [
                make_skill(900013, "Kilo Kick Sharp", cost=100, versions=[900014]),
                make_skill(900014, "Kilo Kick", unreleased=["en"], versions=[900013]),
            ]
        )
        self.assertIn("Kilo Kick Sharp", result.data)
        self.assertNotIn("Kilo Kick", result.data)
        self.assertIsNone(result.data["Kilo Kick Sharp"]["downgrade"])

    def test_output_is_identical_across_two_identical_runs(self):
        """8. The same input produces byte-identical sorted output every time."""
        skills = [
            make_skill(900017, "Zulu Zip", cost=180),
            make_skill(900015, "Lima Leap"),
            make_skill(900016, "Mike Mend", cost=70, unreleased=["ko"]),
        ]
        first = run_scraper(list(skills))
        second = run_scraper(list(skills))
        self.assertEqual(first.data, second.data)
        self.assertEqual(
            [key for key in sorted(first.data.keys())],
            [key for key in sorted(second.data.keys())],
        )
        import json

        self.assertEqual(
            json.dumps({k: first.data[k] for k in sorted(first.data)}, ensure_ascii=False, sort_keys=True),
            json.dumps({k: second.data[k] for k in sorted(second.data)}, ensure_ascii=False, sort_keys=True),
        )


class ExistsOnGlobalTest(unittest.TestCase):
    def test_accepts_both_field_spellings_and_defaults_to_released(self):
        self.assertTrue(main.exists_on_global({}))
        self.assertTrue(main.exists_on_global({"unreleased": []}))
        self.assertTrue(main.exists_on_global({"unreleased": ["ko", "zh_tw"]}))
        self.assertFalse(main.exists_on_global({"unreleased": ["en"]}))
        self.assertFalse(main.exists_on_global({"unreleased_servers": ["en"]}))
        self.assertTrue(main.exists_on_global({"unreleased": None}))


if __name__ == "__main__":
    unittest.main()
