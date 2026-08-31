# Bundled game data

This directory holds the game data the app ships with, the character preset sources, the settings
search registry, and one coordinate-finding utility. The scrapers that regenerate the JSON no longer
live here.

## Data files

- `characters.json`: training events and options for all characters.
- `supports.json`: support card event data.
- `skills.json`: skill IDs, names, costs, and tier rankings.
- `races.json`: race calendar data, with turn numbers for the in-game calendar.
- `scenarios.json`: scenario-specific event data (URA Finale, Unity Cup, Trackblazer, Grand Concert). Maintained by hand, since it carries the special event overrides and per-scenario logic.
- `character_objectives.json`: goal turns per character. Read by `scripts/generate-racing-plan.mjs` and never bundled into the app.
- `succession_relations.json`: the game's own inheritance relation tables (relation types, their point values, their character membership, and the three rank bands). Read by `scripts/parent-lab-affinity.mjs` and never bundled into the app. Regenerated from an installed `master.mdb` with `node scripts/generate-succession-relation-data.mjs --db <path>`; `--check` verifies the committed file is current. Two characters sharing a relation type share its points, so summing the shared types gives their base relation. That is not the affinity total the game displays over a lineage, and nothing in this repository computes one.
- `support_cards.json`: the game's own support-card catalogue (every shipped card with its character, rarity,
  support type, per-level effect curves, unique perk and unlock level, hint-skill pool, group members and scenario
  restrictions), plus the effect-type names, the level cap at each limit-break step, and the characters each scenario
  treats as its own. Read by `scripts/deck-lab.mjs` and never bundled into the app. Regenerated from an installed
  `master.mdb` with `node scripts/generate-support-card-data.mjs --db <path>`; `--check` verifies the committed file is
  current. Written as pure ASCII: several shipped card titles contain characters this repository does not write by
  hand, so they are stored as escapes and parse back to the exact game string. Note `supports.json` is a different
  file with a different job: it holds support-card training *events*, keyed by character display name.

`characterPresets.ts` and `presetMeta.ts` are the preset sources. They are the authority for the
preset inventory; [`PRESETS_GUIDE.md`](../../PRESETS_GUIDE.md) documents the player-facing view and
[`docs/PRESET_AUTHORING.md`](../../docs/PRESET_AUTHORING.md) documents the contributor authoring
rules. Update the relevant one in the same change.

`searchConfig.ts` is the registry the in-app settings search reads. A new setting has to be listed
there or it cannot be found from the search box, even though it works everywhere else.

## Refreshing the data

The scraper lives in `scripts/data-scraper/`. See [its README](../../scripts/data-scraper/README.md)
for setup, the delta-vs-full refresh, and how a data refresh reaches installed apps. Hand-editing a
JSON here is fine for a one-off correction, but a refresh regenerates the file.

## imageDetection.py

A utility for finding screen coordinates for new UI elements. It opens interactive windows with
sliders for the OpenCV detection parameters (blur, Canny, thresholds) and runs against the sample
screenshots in this directory.

```bash
pip install -r requirements.txt   # opencv-python, numpy
python imageDetection.py
```
