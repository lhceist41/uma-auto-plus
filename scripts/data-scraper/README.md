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

Outputs land in `src/data/`: `characters.json`, `supports.json`, `skills.json`, `races.json`, `character_outfits.json` (every EN outfit title per playable character; read by `scripts/generate-veteran-identity-data.mjs`, never bundled into the app), and `character_objectives.json` (goal turns per character; read by `scripts/generate-racing-plan.mjs`, never bundled into the app). Races are a full rebuild rather than a delta, because EN keeps receiving calendar additions; the rebuild is idempotent, so running it every time is cheap. The epithet and scraped-preset outputs feed the upstream project's solver and are disabled here.

`character_objectives.json` also carries `fanGoals`, a repo-owned augmentation this scraper does not produce: `scripts/extract-master-route-data.mjs` writes it separately from the installed game's master.mdb (Grand Concert fan-count goals GameTora does not expose). Because the objectives scraper is a full rebuild, it automatically carries `fanGoals` over from the file on disk before rewriting it, so a plain `python update.py` never destroys it - re-run `extract-master-route-data.mjs` against a current master.mdb only when the underlying GC route data itself has changed.

After any refresh that changes `character_outfits.json` (a new costume, or a new character), regenerate the Veteran identity runtime asset the roster reader ships: `node scripts/generate-veteran-identity-data.mjs`. It has a `--check` flag to detect staleness, and CI runs that check.

After any refresh that changes `character_objectives.json` (a new character, or corrected mandatory-race data), also regenerate the derived artifacts that read it: `node scripts/compile-master-data.mjs` (offline master-data tooling) and `node scripts/generate-gc-fan-runtime-data.mjs` (the native GC runtime asset). Both have a `--check` flag to detect staleness.

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
