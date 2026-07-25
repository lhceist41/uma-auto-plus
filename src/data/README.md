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

`characterPresets.ts` and `presetMeta.ts` are the preset sources. They are the authority for the
preset inventory; `PRESETS_GUIDE.md` is their documented view and must be updated in the same change.

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
