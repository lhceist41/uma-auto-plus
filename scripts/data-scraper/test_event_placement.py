"""Tests for the `pl` (race placement) training-event reward.

Run from the repo root:

    python -m unittest discover -s scripts/data-scraper -p "test_*.py"

`pl` states the finishing placement its outcome group is conditional on. GameTora carries the value in `d`
(never `v`) and renders it as a `※` condition line, so these tests pin the three structural forms actually
observed in the live data, the degenerate shapes, and the fact that an unrelated unknown code still falls
through to the generic fallback instead of being treated as a placement.

Every fixture is invented. Nothing here touches the network.
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import main  # noqa: E402

# The renderer takes card-level multipliers; placement lines are conditions and must ignore both.
NO_SCALING = {"card_char": "Someone", "energy_mult": 1.0, "stat_mult": 1.0}


def _renderer():
    """A TrainingEventScraper whose render helpers can be called directly.

    The class is the never-instantiated base of the character and support scrapers, and its constructor is
    BaseScraper's (url, output filename). The render helpers use no instance state, so the instance is built
    without running that constructor: nothing is fetched and no output path is bound.
    """
    return object.__new__(main.TrainingEventScraper)


def render(reward, **overrides):
    """Renders one reward object through the real renderer."""
    kwargs = dict(NO_SCALING)
    kwargs.update(overrides)
    return _renderer()._render_reward(reward, kwargs["card_char"], kwargs["energy_mult"], kwargs["stat_mult"])


def render_choice(rewards, **overrides):
    """Renders a full choice, so divider and blank-line grouping is exercised the way the scraper does it."""
    kwargs = dict(NO_SCALING)
    kwargs.update(overrides)
    return _renderer()._render_choice(rewards, kwargs["card_char"], kwargs["energy_mult"], kwargs["stat_mult"])


class PlacementFormTest(unittest.TestCase):
    def test_single_element_range_is_an_exact_place(self):
        """Form 1: `[n]` is exactly nth."""
        self.assertEqual("※ 1st", render({"t": "pl", "v": None, "d": [1]}))
        self.assertEqual("※ 2nd", render({"t": "pl", "v": None, "d": [2]}))
        self.assertEqual("※ 3rd", render({"t": "pl", "v": None, "d": [3]}))
        self.assertEqual("※ 4th", render({"t": "pl", "v": None, "d": [4]}))

    def test_open_ended_range_is_that_place_or_worse(self):
        """Form 2: `[n, None]` is nth or worse."""
        self.assertEqual("※ 3rd or worse", render({"t": "pl", "v": None, "d": [3, None]}))
        self.assertEqual("※ 6th or worse", render({"t": "pl", "v": None, "d": [6, None]}))

    def test_closed_range_is_an_inclusive_span(self):
        """Form 3: `[a, b]` is an inclusive span joined by an en dash."""
        self.assertEqual("※ 2nd–5th", render({"t": "pl", "v": None, "d": [2, 5]}))
        self.assertEqual("※ 1st–12th", render({"t": "pl", "v": None, "d": [1, 12]}))

    def test_equal_bounds_collapse_to_a_single_place(self):
        """A range whose bounds match is the exact-place form, not `4th-4th`."""
        self.assertEqual("※ 4th", render({"t": "pl", "v": None, "d": [4, 4]}))

    def test_teen_placements_use_th(self):
        """The ordinal helper handles the 11/12/13 exception the same way the site does."""
        self.assertEqual("※ 11th", render({"t": "pl", "v": None, "d": [11]}))
        self.assertEqual("※ 12th", render({"t": "pl", "v": None, "d": [12]}))
        self.assertEqual("※ 13th", render({"t": "pl", "v": None, "d": [13]}))
        self.assertEqual("※ 21st", render({"t": "pl", "v": None, "d": [21]}))


class PlacementValueSourceTest(unittest.TestCase):
    def test_value_comes_from_d_and_v_is_ignored(self):
        """`v` is always null in the live data; even when populated it must not reach the output."""
        self.assertEqual("※ 2nd", render({"t": "pl", "v": None, "d": [2]}))
        self.assertEqual("※ 2nd", render({"t": "pl", "v": "+99", "d": [2]}))
        self.assertEqual("※ 2nd", render({"t": "pl", "d": [2]}))

    def test_placement_is_never_scaled_by_event_effectiveness(self):
        """A placement is a condition, so support-card scaling must leave it alone."""
        self.assertEqual("※ 2nd–5th", render({"t": "pl", "v": None, "d": [2, 5]}, stat_mult=1.5, energy_mult=1.5))

    def test_unsupported_payloads_fall_back_to_the_raw_code_form(self):
        """An unsupported shape must stay visible AND stay repairable, so it keeps the `pl ` fallback shape.

        A `※` line here would look like a real condition and would be invisible to the stale-render detector,
        freezing a bad rendering into the data forever.
        """
        for detail in (None, [], [1, 2, 3], "1st", {}, [None, 3], ["a", "b"], [0], [-1, None], [True], [2, "5"]):
            out = render({"t": "pl", "v": None, "d": detail})
            self.assertTrue(out.startswith("pl "), f"{detail!r} -> {out!r}")
            self.assertFalse(out.startswith("※"), f"{detail!r} -> {out!r}")
            self.assertTrue(main.TrainingEventScraper._has_raw_code_line(out, "pl"), f"{detail!r} -> {out!r}")

    def test_a_reversed_range_is_rejected_as_malformed(self):
        """`[5, 2]` is not a placement range; rendering it as "5th-2nd" would invent a placement."""
        out = render({"t": "pl", "v": None, "d": [5, 2]})
        self.assertEqual("pl [5, 2]", out)
        self.assertTrue(main.TrainingEventScraper._has_raw_code_line(out, "pl"))

    def test_supported_shapes_are_accepted_and_others_are_not(self):
        """The validity predicate itself, so the boundary is pinned independently of the rendered text."""
        for detail in ([1], [4, 4], [6, None], [2, 5], [1, 18]):
            self.assertTrue(main.TrainingEventScraper._is_placement_range(detail), detail)
        for detail in (None, [], [1, 2, 3], "1st", {}, [5, 2], [0], [-1], [None], [None, 3], ["a"], [True], [2, "5"]):
            self.assertFalse(main.TrainingEventScraper._is_placement_range(detail), detail)

    def test_random_prefixed_fallback_is_still_detected_as_stale(self):
        """A rolled reward's fallback carries the random marker, and must still enter the repair path."""
        out = render({"t": "pl", "v": None, "d": [5, 2], "r": True})
        self.assertEqual("(random) pl [5, 2]", out)
        self.assertTrue(main.TrainingEventScraper._has_raw_code_line(out, "pl"))

    def test_only_one_random_prefix_is_stripped_when_matching(self):
        """Stripping is exactly one marker, so matching stays anchored at a line boundary."""
        self.assertTrue(main.TrainingEventScraper._has_raw_code_line("(random) pl None", "pl"))
        self.assertFalse(main.TrainingEventScraper._has_raw_code_line("(random) (random) pl None", "pl"))
        self.assertFalse(main.TrainingEventScraper._has_raw_code_line("Mood +2 (random) pl None", "pl"))

    def test_random_flag_is_still_honoured(self):
        """The shared `(random)` prefix is not bypassed by the placement branch."""
        self.assertEqual("(random) ※ 2nd", render({"t": "pl", "v": None, "d": [2], "r": True}))


class PlacementGroupingTest(unittest.TestCase):
    def test_placement_heads_each_blank_line_separated_branch(self):
        """The live Copano shape: two `nl`-separated branches, each opened by its own placement."""
        rendered = render_choice(
            [
                {"t": "pl", "v": None, "d": [2]},
                {"t": "mo", "v": "+2"},
                {"t": "nl"},
                {"t": "pl", "v": None, "d": [3, None]},
                {"t": "mo", "v": "+2"},
            ]
        )
        self.assertEqual("※ 2nd\nMood +2\n\n※ 3rd or worse\nMood +2", rendered)

    def test_placement_survives_divider_groups(self):
        """A `di` divider still splits groups, with each group keeping its own placement line."""
        rendered = render_choice(
            [
                {"t": "pl", "v": None, "d": [1]},
                {"t": "pt", "v": "+45"},
                {"t": "di"},
                {"t": "pl", "v": None, "d": [2, 5]},
                {"t": "pt", "v": "+35"},
            ]
        )
        self.assertIn("Randomly either", rendered)
        self.assertIn("※ 1st", rendered)
        self.assertIn("※ 2nd–5th", rendered)


class UnknownCodeTest(unittest.TestCase):
    def test_an_unknown_code_is_not_mistaken_for_a_placement(self):
        """Other unmapped codes must keep hitting the generic fallback, not the placement renderer.

        The fallback prints the code and its raw value, so a null-valued unknown still reads
        "<code> None". That is deliberately left alone here: it is the generic safety net, and only `pl`
        was verified against the source. No other unmapped code currently reaches the generated data.
        """
        self.assertEqual("zz +1", render({"t": "zz", "v": "+1", "d": [2]}))
        self.assertEqual("se_has None", render({"t": "se_has", "v": None, "d": [2, 5]}))
        for out in (render({"t": "zz", "v": "+1", "d": [2]}), render({"t": "se_has", "v": None, "d": [2, 5]})):
            self.assertNotIn("※", out)
            self.assertNotIn("2nd", out)

    def test_placement_code_is_matched_exactly(self):
        """A code that merely starts with `pl` is not a placement."""
        out = render({"t": "plx", "v": None, "d": [1]})
        self.assertNotIn("1st", out)


class StaleOptionReplacementTest(unittest.TestCase):
    """The per-option decision: only a stale option is repairable, and only from its own rewards."""

    PL_REWARDS = [{"t": "pl", "v": None, "d": [1]}, {"t": "pt", "v": "+45"}]
    PLAIN_REWARDS = [{"t": "pt", "v": "+30"}]

    def test_stale_raw_code_text_is_replaced(self):
        self.assertTrue(main.TrainingEventScraper._supersedes_stale_option("pl None\nSkill points +45", "※ 1st\nSkill points +45", self.PL_REWARDS))

    def test_curated_drift_without_a_raw_code_is_preserved(self):
        """Ordinary value drift between curated Global text and GameTora's data must not be overwritten."""
        self.assertFalse(main.TrainingEventScraper._supersedes_stale_option("※ 1st\nSkill points +30", "※ 1st\nSkill points +45", self.PL_REWARDS))

    def test_not_replaced_when_the_new_render_still_has_the_raw_code(self):
        """If the renderer still cannot handle the code, there is nothing better to write."""
        self.assertFalse(main.TrainingEventScraper._supersedes_stale_option("pl None\nSkill points +45", "pl None\nSkill points +45", self.PL_REWARDS))

    def test_a_raw_code_not_present_in_this_option_is_ignored(self):
        """The check is scoped to codes this option actually carries, so unrelated text cannot trigger it."""
        self.assertFalse(main.TrainingEventScraper._supersedes_stale_option("zz None\nSkill points +45", "※ 1st\nSkill points +45", self.PL_REWARDS))

    def test_another_options_rewards_cannot_authorize_a_replacement(self):
        """Option 1 carries no `pl`, so a `pl` artifact in its stored text is not this option's to repair."""
        self.assertFalse(main.TrainingEventScraper._supersedes_stale_option("pl None\nSkill points +30", "Skill points +30", self.PLAIN_REWARDS))

    def test_rendered_labels_are_not_mistaken_for_raw_code_lines(self):
        """A normal label never looks like the fallback, so no curated line is misread as an artifact."""
        for line in ("Skill points +45", "※ 1st", "(random) Get Hot Topic status", "Randomly either", "or (~90%)", "Simple None of it", "A plan None"):
            self.assertFalse(main.TrainingEventScraper._has_raw_code_line(line, "pt"))
            self.assertFalse(main.TrainingEventScraper._has_raw_code_line(line, "pl"))


class OptionLocalMergeTest(unittest.TestCase):
    """End-to-end merge policy through `_ingest_events`, which is where the previous version overreached."""

    @staticmethod
    def _event(name, option_rewards):
        return {"n": name, "c": [{"o": "", "r": rewards} for rewards in option_rewards]}

    def _ingest(self, stored, option_rewards, name="Race Result"):
        """Runs the real merge over one pre-populated event and returns the resulting option list."""
        card_events = {name: list(stored)} if stored is not None else {}
        event = self._event(name, option_rewards)
        scraper = _renderer()
        scraper._ingest_events(card_events, [("secret", [event])], "Someone", 1.0, 1.0)
        return card_events.get(name), event, scraper

    def _fresh(self, event, scraper):
        return [scraper._render_choice(c["r"], "Someone", 1.0, 1.0) for c in event["c"]]

    STALE_FIRST = [[{"t": "pl", "v": None, "d": [1]}, {"t": "pt", "v": "+45"}], [{"t": "pt", "v": "+30"}]]

    def test_a_brand_new_event_stores_every_fresh_option(self):
        result, event, scraper = self._ingest(None, self.STALE_FIRST)
        self.assertEqual(self._fresh(event, scraper), result)

    def test_only_the_stale_option_zero_changes(self):
        """The exact case the previous event-level rule got wrong."""
        stored = ["pl None\nSkill points +45", "Skill points +20"]
        result, _, _ = self._ingest(stored, self.STALE_FIRST)
        self.assertEqual("※ 1st\nSkill points +45", result[0])
        self.assertEqual("Skill points +20", result[1])
        self.assertIs(stored[1], result[1])

    def test_only_the_stale_option_one_changes(self):
        """Mirrored: the stale option is the second one, and option 0's curated drift survives."""
        rewards = [[{"t": "pt", "v": "+30"}], [{"t": "pl", "v": None, "d": [6, None]}, {"t": "pt", "v": "+25"}]]
        stored = ["Skill points +12", "pl None\nSkill points +25"]
        result, _, _ = self._ingest(stored, rewards)
        self.assertEqual("Skill points +12", result[0])
        self.assertEqual("※ 6th or worse\nSkill points +25", result[1])

    def test_several_stale_options_are_each_repaired_at_their_own_index(self):
        rewards = [
            [{"t": "pl", "v": None, "d": [1]}],
            [{"t": "pt", "v": "+30"}],
            [{"t": "pl", "v": None, "d": [2, 5]}],
        ]
        stored = ["pl None", "Skill points +11", "pl None"]
        result, _, _ = self._ingest(stored, rewards)
        self.assertEqual(["※ 1st", "Skill points +11", "※ 2nd–5th"], result)

    def test_an_option_count_mismatch_leaves_the_stored_event_untouched(self):
        """Indexes can no longer be paired, so nothing is rewritten and no text moves between options."""
        stored = ["pl None\nSkill points +45"]
        result, _, _ = self._ingest(stored, self.STALE_FIRST)
        self.assertEqual(stored, result)

    def test_a_stale_line_in_one_option_cannot_rewrite_a_sibling(self):
        """Option 1's stored text is curated drift; option 0 being stale must not touch it."""
        stored = ["pl None\nSkill points +45", "Skill points +20"]
        result, event, scraper = self._ingest(stored, self.STALE_FIRST)
        self.assertNotEqual(self._fresh(event, scraper)[1], result[1])

    def test_ordinary_drift_alone_never_triggers_a_rewrite(self):
        """With no stale line anywhere, every stored option is preserved byte-for-byte."""
        rewards = [[{"t": "pl", "v": None, "d": [1]}, {"t": "pt", "v": "+45"}], [{"t": "pt", "v": "+30"}]]
        stored = ["※ 1st\nSkill points +11", "Skill points +12"]
        result, _, _ = self._ingest(stored, rewards)
        self.assertEqual(stored, result)


class LiveOccurrenceTest(unittest.TestCase):
    """The five placements present in the generated data, as their raw objects and expected English.

    Raw shapes were read off GameTora's own event payloads; the expected strings are what its frontend
    renders for `lang=en`. Fixtures only, so this stays offline.
    """

    CASES = [
        ({"t": "pl", "v": None, "d": [1]}, "※ 1st"),
        ({"t": "pl", "v": None, "d": [2, 5]}, "※ 2nd–5th"),
        ({"t": "pl", "v": None, "d": [6, None]}, "※ 6th or worse"),
        ({"t": "pl", "v": None, "d": [2]}, "※ 2nd"),
        ({"t": "pl", "v": None, "d": [3, None]}, "※ 3rd or worse"),
    ]

    def test_every_live_occurrence_renders_its_authoritative_english(self):
        for reward, expected in self.CASES:
            self.assertEqual(expected, render(reward))

    def test_no_live_occurrence_renders_the_old_placeholder(self):
        for reward, _ in self.CASES:
            self.assertNotIn("pl None", render(reward))


if __name__ == "__main__":
    unittest.main()
