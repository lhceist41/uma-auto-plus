"""Tests for the Game8 skill community-tier scraper (SkillScraper.scrape_skill_tier_list).

Run from the repo root:

    python -m unittest discover -s scripts/data-scraper -p "test_*.py"

Every fixture here is a small hand-built HTML page shaped like Game8's tier-list archive, not a captured page
(no third-party page content is committed). All network calls are replaced, so these tests never touch Game8.
"""

import logging
import sys
import unittest
from pathlib import Path
from unittest import mock

from bs4 import BeautifulSoup

sys.path.insert(0, str(Path(__file__).resolve().parent))

import main  # noqa: E402


def build_page(sections):
    """Builds a Game8-shaped tier-list page: one h3.a-header--3 heading per section, immediately followed by
    one Tier/Skill/Points table.

    Args:
        sections: list of (heading_text, rows). Each row is either a list of anchor texts for the Skill cell
            (one anchor per evolution/variant sharing the row), or None to render a malformed row whose Skill
            cell holds no link at all.
    """
    parts = ["<html><body>"]
    for heading_text, rows in sections:
        parts.append(f'<h3 class="a-header--3">{heading_text}</h3>')
        parts.append("<table><tr><th>Tier</th><th>Skill</th><th>Points</th></tr>")
        for row in rows:
            if row is None:
                parts.append('<tr><td class="center">rank-icon</td><td></td><td>points</td></tr>')
                continue
            anchors_html = "".join(f'<a class="a-link" href="#">{name}</a>' for name in row)
            parts.append(f'<tr><td class="center">rank-icon</td><td><div class="align">{anchors_html}</div></td><td>points</td></tr>')
        parts.append("</table>")
    parts.append("</body></html>")
    return BeautifulSoup("".join(parts), "lxml")


def scrape(sections):
    """Runs scrape_skill_tier_list() against a page built from `sections`, with fetch_soup replaced."""
    scraper = main.SkillScraper()
    with mock.patch.object(main, "fetch_soup", return_value=build_page(sections)):
        return scraper.scrape_skill_tier_list()


def padded_section(heading, names):
    """A section with one row per name in `names`, plus enough filler rows to clear the per-section coverage
    floor on its own, so tests that aren't exercising the integrity gate don't have to think about it. Filler
    names are seeded from the heading text so two sections never emit colliding filler names.
    """
    filler = [[f"Filler ({heading}) {i}"] for i in range(main.MIN_SKILL_TIER_NAMES_PER_SECTION)]
    return (heading, [[name] for name in names] + filler)


class RepresentativeParsingTest(unittest.TestCase):
    def test_representative_row_parses_correctly(self):
        """1. A single well-formed row resolves to the tier of its section."""
        result = scrape([padded_section("SS Tier Acceleration Skills", ["Angling and Scheming"])])
        self.assertEqual(0, result["Angling and Scheming"])

    def test_multiple_tier_labels_map_correctly(self):
        """2. All four tier letters map to their documented rank, independent of section order."""
        result = scrape(
            [
                padded_section("SS Tier Velocity Skills", ["Top Skill"]),
                padded_section("S Tier Velocity Skills", ["Second Skill"]),
                padded_section("A Tier Velocity Skills", ["Third Skill"]),
                padded_section("B Tier Velocity Skills", ["Fourth Skill"]),
            ]
        )
        self.assertEqual(0, result["Top Skill"])
        self.assertEqual(1, result["Second Skill"])
        self.assertEqual(2, result["Third Skill"])
        self.assertEqual(3, result["Fourth Skill"])

    def test_multi_anchor_row_tiers_every_linked_skill(self):
        """A base skill and its evolved form sharing one row both get the section's tier."""
        result = scrape(
            [
                (
                    "A Tier Passive Skills",
                    [["Base Form", "Evolved Form"]] + [[f"Filler {i}"] for i in range(main.MIN_SKILL_TIER_NAMES_PER_SECTION)],
                )
            ]
        )
        self.assertEqual(2, result["Base Form"])
        self.assertEqual(2, result["Evolved Form"])


class NormalizationTest(unittest.TestCase):
    def test_skill_name_normalization(self):
        """3. A packed aptitude cell ("X ◯ / X ◎") splits into two names with GameTora's circle character."""
        result = scrape(
            [
                (
                    "SS Tier Debuff Skills",
                    [["Long Straightaways ◯ / Long Straightaways ◎"]]
                    + [[f"Filler {i}"] for i in range(main.MIN_SKILL_TIER_NAMES_PER_SECTION)],
                )
            ]
        )
        self.assertEqual(0, result["Long Straightaways ○"])
        self.assertEqual(0, result["Long Straightaways ◎"])
        self.assertNotIn("Long Straightaways ◯", result)

    def test_known_alias_is_renamed_to_match_gametora(self):
        """4. A tier-list spelling in the alias table is renamed to the GameTora spelling."""
        result = scrape([padded_section("B Tier Recovery Skills", ["Let's Pump Some Iron"])])
        self.assertIn("Let's Pump Some Iron!", result)
        self.assertNotIn("Let's Pump Some Iron", result)


class AmbiguityTest(unittest.TestCase):
    def test_conflicting_tier_across_sections_stays_unresolved(self):
        """5. A name that two sections disagree on is dropped rather than assigned either tier."""
        result = scrape(
            [
                padded_section("SS Tier Recovery Skills", ["Familiar Ground"]),
                padded_section("A Tier Debuff Skills", ["Familiar Ground"]),
            ]
        )
        self.assertNotIn("Familiar Ground", result)

    def test_conflict_does_not_reappear_after_a_third_matching_section(self):
        """A name poisoned by a conflict stays out even if a later section would have agreed with the first."""
        result = scrape(
            [
                padded_section("SS Tier Recovery Skills", ["Familiar Ground"]),
                padded_section("A Tier Debuff Skills", ["Familiar Ground"]),
                padded_section("SS Tier Passive Skills", ["Familiar Ground"]),
            ]
        )
        self.assertNotIn("Familiar Ground", result)


class MalformedRowTest(unittest.TestCase):
    def test_malformed_row_is_reported_and_skipped(self):
        """6. A row whose Skill cell has no link is logged and skipped, not fatal, and other rows still parse."""
        records = []

        class _Capture(logging.Handler):
            def emit(self, record):
                records.append(record)

        heading, rows = padded_section("A Tier Mixed Skills", ["Good Skill"])
        section = (heading, rows + [None])  # a malformed row: no anchor in its Skill cell

        logger = logging.getLogger()
        previous_handlers, previous_level = logger.handlers[:], logger.level
        logger.handlers = [_Capture()]
        logger.setLevel(logging.DEBUG)  # setLevel(), not a raw attribute set, so the isEnabledFor cache clears
        try:
            result = scrape([section])
        finally:
            logger.handlers = previous_handlers
            logger.setLevel(previous_level)

        self.assertEqual(2, result["Good Skill"])
        self.assertTrue(any("no skill link" in r.getMessage() for r in records))


class IntegrityGateTest(unittest.TestCase):
    def test_zero_recognized_sections_raises(self):
        """7. A page with no recognizable tier heading fails loudly instead of returning an empty map."""
        with self.assertRaises(RuntimeError):
            scrape([("Some Unrelated Heading", [["Whatever"]])])

    def test_implausibly_low_coverage_raises(self):
        """8. Headings are recognized but almost no rows resolve to a name: fails rather than shipping nulls."""
        sections = [(f"{letter} Tier Velocity Skills", [["Only One Skill"]]) for letter in ("SS", "S", "A", "B")]
        with self.assertRaises(RuntimeError):
            scrape(sections)

    def test_high_unresolved_row_ratio_raises_even_with_enough_names(self):
        """A section with plenty of resolved names but mostly malformed rows still fails the row-ratio gate."""
        good = [[f"Skill {i}"] for i in range(main.MIN_SKILL_TIER_NAMES_PER_SECTION)]
        malformed = [None] * (len(good) * 4)
        with self.assertRaises(RuntimeError):
            scrape([("SS Tier Mixed Skills", good + malformed)])


class DeterminismTest(unittest.TestCase):
    def test_output_is_identical_across_two_runs(self):
        """9. The same page produces byte-identical results on repeated scrapes."""
        sections = [
            padded_section("SS Tier Acceleration Skills", ["Alpha", "Bravo"]),
            padded_section("B Tier Acceleration Skills", ["Charlie"]),
        ]
        first = scrape(sections)
        second = scrape(sections)
        self.assertEqual(first, second)


class EndToEndIntegrationTest(unittest.TestCase):
    def test_generated_skills_json_has_nonnull_community_tier(self):
        """10. Running the full scraper with a tiered skill in GameTora's data yields a non-null community_tier
        in the generated record, proving the tier map actually reaches skills.json's schema, not just the
        intermediate name->tier dict.
        """
        tier_sections = [padded_section("SS Tier Velocity Skills", ["Tiered Skill"])]
        skills = [
            {"id": 900101, "name_en": "Tiered Skill", "desc_en": "desc", "iconid": 10011, "cost": 150},
            {"id": 900102, "name_en": "Untiered Skill", "desc_en": "desc", "iconid": 10011, "cost": 150},
        ]

        scraper = main.SkillScraper()
        with mock.patch.object(main, "fetch_soup", return_value=build_page(tier_sections)), mock.patch.object(
            main, "fetch_gametora_manifest_data", return_value=skills
        ), mock.patch.object(main.SkillScraper, "scrape_skill_evaluation_points", return_value={}), mock.patch.object(
            main.SkillScraper, "save_data"
        ), mock.patch.object(main, "download_image"), mock.patch.object(main, "write_skill_icon_index"):
            scraper.start()

        self.assertEqual(0, scraper.data["Tiered Skill"]["community_tier"])
        self.assertIsNone(scraper.data["Untiered Skill"]["community_tier"])


if __name__ == "__main__":
    unittest.main()
