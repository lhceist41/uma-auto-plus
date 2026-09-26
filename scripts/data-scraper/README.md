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

`character_objectives.json` also carries `fanGoals`, a repo-owned augmentation this scraper does not produce: `scripts/extract-master-route-data.mjs` writes it separately from the installed game's master.mdb (Grand Concert fan-count goals GameTora does not expose). Because the objectives scraper is a full rebuild, it automatically carries `fanGoals` over from the file on disk before rewriting it, so a plain `python update.py` never destroys it. Carrying over cannot add goals, though: a new character's goals exist only after the extractor runs.

After any refresh that changes `character_outfits.json` (a new costume, or a new character), regenerate the Veteran identity runtime asset the roster reader ships: `node scripts/generate-veteran-identity-data.mjs`. It has a `--check` flag to detect staleness, and CI runs that check. The generator does not update the asset's hand-kept Kotlin fallback: when the asset gains or loses a character, edit `VeteranIdentityNames.CHARACTERS` to match. `yarn test:kt` (`VeteranIdentityCatalogTest`) fails until the two agree.

After any refresh that changes `character_objectives.json` (a new character, or corrected mandatory-race data), first re-run `node scripts/extract-master-route-data.mjs --db <master.mdb>` against a current game install, then regenerate the derived artifacts that read it: `node scripts/compile-master-data.mjs` (offline master-data tooling) and `node scripts/generate-gc-fan-runtime-data.mjs` (the native GC runtime asset). All three have a `--check` flag to detect staleness; the extractor's check exits 3 when the committed fan goals differ from the game's.

After any refresh that changes `skills.json` or `races.json` (new skills, a corrected race), or after a hand-edit to `src/data/scenarios.json`, regenerate the Veteran factor-name domain the Inspiration reader snaps its factor OCR onto: `node scripts/generate-veteran-factor-domain.mjs`. It has a `--check` flag too, and CI runs it.

### Regeneration checklist

A data refresh can silently miss one of these; check all five whenever any `src/data` raw file changes: `characters.json`, `character_outfits.json`, `character_objectives.json`, `skills.json`, `races.json`, `supports.json`, or `scenarios.json`.

| Generator | Reads | `--check` in CI |
|---|---|---|
| `generate-veteran-identity-data.mjs` | `characters.json`, `character_outfits.json` | yes |
| `extract-master-route-data.mjs` | a live game install's `master.mdb` | no (run by hand) |
| `compile-master-data.mjs` | `skills.json`, `races.json`, and every other raw source | no |
| `generate-gc-fan-runtime-data.mjs` | `character_objectives.json`, `races.json` | yes |
| `generate-veteran-factor-domain.mjs` | `skills.json`, `races.json`, `scenarios.json` | yes |

`yarn data:check` runs every `--check` this table marks "yes", in one command; `extract-master-route-data.mjs` and `compile-master-data.mjs` still need running by hand first, since the extractor needs a live game install and the compiler has no CI check to fail closed.

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
