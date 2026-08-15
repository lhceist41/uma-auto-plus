"""Tests for the race placement -> fans payout curve preservation.

Run from the repo root:

    python -m unittest discover -s scripts/data-scraper -p "test_*.py"

The race scraper keeps the full placement-to-fans curve, not only the first-place value. Every
fixture is invented. Nothing here touches the network.
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import main  # noqa: E402


class FanPayoutsByPlaceTest(unittest.TestCase):
    def test_keeps_every_listed_placement_sorted_by_place(self):
        # Out-of-order input: all placements are kept and sorted ascending by place.
        entry_fans = [{"order": 3, "fans": 125}, {"order": 1, "fans": 500}, {"order": 2, "fans": 200}]
        curve = main.RaceScraper._fan_payouts_by_place(entry_fans)
        self.assertEqual(
            curve,
            [{"place": 1, "fans": 500}, {"place": 2, "fans": 200}, {"place": 3, "fans": 125}],
        )

    def test_does_not_collapse_to_first_place_only(self):
        # The old behaviour kept only order 1; the curve must retain the full listed field.
        entry_fans = [{"order": o, "fans": 100 - o} for o in range(1, 19)]  # placements 1..18
        curve = main.RaceScraper._fan_payouts_by_place(entry_fans)
        self.assertEqual(len(curve), 18)
        self.assertEqual([p["place"] for p in curve], list(range(1, 19)))

    def test_first_place_equals_the_scalar_fans_field(self):
        # The scalar `fans` the scraper stores is the order-1 value; the curve's place-1 must match it.
        entry_fans = [{"order": 2, "fans": 200}, {"order": 1, "fans": 500}]
        curve = main.RaceScraper._fan_payouts_by_place(entry_fans)
        scalar_first = next((f["fans"] for f in entry_fans if f["order"] == 1), 0)
        curve_first = next(p["fans"] for p in curve if p["place"] == 1)
        self.assertEqual(curve_first, scalar_first)

    def test_empty_entry_yields_empty_curve(self):
        self.assertEqual(main.RaceScraper._fan_payouts_by_place([]), [])


if __name__ == "__main__":
    unittest.main()
