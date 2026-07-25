# Game-data scraper

Regenerates the bundled game data (`src/data/*.json`) from gametora, game8, and umamusu.wiki. Plain HTTP, no browser required.

## Setup

```bash
pip install -r scripts/data-scraper/requirements.txt   # requests, beautifulsoup4, Deprecated
pip install lxml                                       # parser used by the skill scraper
```

## Usage

```bash
python update.py            # from the repo root; delta mode, only new/changed entries
```

Outputs land in `src/data/`: `characters.json`, `supports.json`, `skills.json`, `races.json`, and `character_objectives.json` (goal turns per character; read by `scripts/generate-racing-plan.mjs`, never bundled into the app). Races are a full rebuild rather than a delta, because EN keeps receiving calendar additions; the rebuild is idempotent, so running it every time is cheap. The epithet and scraped-preset outputs feed the upstream project's solver and are disabled here.

## Shipping a data refresh

The data JSONs ship inside the JS bundle, so a refresh reaches devices via a normal release:

```bash
python update.py
yarn test                   # data-shape canaries
yarn build:bundle           # rebundle + release APK
# bump the version, commit, tag - CI builds and publishes the release,
# and the in-app update checker notifies installed apps.
```

## When it breaks

Each dataset retries twice on network errors, then is skipped without failing the others. The most fragile piece is the gametora Next.js build id (regex-scraped from one HTML page) - if every gametora dataset fails at once, that regex is the first place to look.
