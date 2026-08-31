# Telemetry corpus collection

Durable, read-only host-side archiving of the app's JSONL telemetry so completed careers survive as reusable
offline-evaluation evidence (for `scripts/shadow-advisor.mjs` and `scripts/replay-lab.mjs`) instead of living
only in ephemeral Temp scratchpads.

The device writes three append-only JSONL streams under its app external files directory
(`OutcomeCorpus.kt`, `getExternalFilesDir(null)/outcomes/`):

- `decisions.jsonl` (DecisionTrace, per-turn) -- required for offline evaluation
- `career_state.jsonl` (CareerState, per-turn) -- required for offline evaluation
- `careers.jsonl` (career finalize) -- optional

On device (applicationId `com.lhceist41.uma_auto_plus`) they live at
`/storage/emulated/0/Android/data/com.lhceist41.uma_auto_plus/files/outcomes/`.

`scripts/collect-telemetry.mjs` copies their exact bytes into a hashed, manifested bundle under
`validation/corpus/<UTC-date>-<label>/`. That path is gitignored, so a live corpus is never committed. The
collector never mutates, normalizes, clears, or renames the device files.

## Before a run

- **Verify Record Decision Data is enabled before the career starts.** Per-turn DecisionTrace/CareerState
  production is gated on `recordDecisionData || debugDiagnostics`. Record Decision Data defaults on, so a
  release build records the factual corpus without Debug Mode; Debug Mode is not required for it. Debug Mode
  is still required for the debug-only artifacts (the human Decision Report, `shadow_advisor`). The collector
  never toggles either setting.
- **Do not uninstall the app while unarchived telemetry matters.** Uninstall wipes the app external files
  directory, taking the corpus with it.

## After a run or queue

1. Pull into a durable bundle (read-only `adb pull`):

   ```bash
   node scripts/collect-telemetry.mjs --label taiki-tb-run1
   ```

   If adb is not on PATH (e.g. Git Bash), pass it explicitly, and pass a serial when more than one device is
   attached:

   ```bash
   node scripts/collect-telemetry.mjs --label taiki-tb-run1 --device <serial> --adb <android-sdk>/platform-tools/adb.exe
   ```

   Or archive an already-copied directory without adb:

   ```bash
   node scripts/collect-telemetry.mjs --from-dir ./pulled --label taiki-tb-run1
   ```

2. Verify the bundle: open `validation/corpus/<bundle>/manifest.json` and check each file's `sha256` /
   `byteSize`, the per-`careerToken` `sharedSeqCount` (paired decision+state seqs), and `duplicate*SeqCount`
   (should be 0 for a clean run). `pairedCareerTokenCount` counts career tokens present in both telemetry
   streams; it does not imply any shared seq values -- use `sharedSeqCount` for actual per-seq overlap.

3. Run ReplayLab / the offline evaluator on the archived pair to confirm it is usable:

   ```bash
   node scripts/shadow-advisor.mjs --trace validation/corpus/<bundle>/decisions.jsonl --state validation/corpus/<bundle>/career_state.jsonl
   ```

4. Preserve the bundle. It is the durable evidence; the device files can then be reset separately.

## Record schemas

### Decision trace (`decisions.jsonl`)

One `decision_trace` record per main-screen turn, built by `DecisionTrace.buildRecord` (`bot/DecisionTrace.kt`).

- Identity: `type`, `v` (schema version), wall-clock `ts`, app version (`app`), config fingerprint (`fp`), `scenario`, `trainee`, applied `preset`, `careerToken` (the same identity the career-finalize records use, so traces join to them directly), `queueRun`.
- Date: `turn` (present only when the date was actually read this career), `year`, `month`, `phase`.
- `state`: energy, mood, the five stats, skill points, fans, negative statuses, and any scenario inventory or extra state -- the snapshot taken when the turn opened, not live state at write time.
- `observation`: whether the turn number, stats, skill points and aptitudes came from an actual read this career rather than a carried-over or default value. These are the existing readers' read flags, not confidence scores.
- `candidates`: main-screen actions carry the chosen one plus each alternative the priority cascade explicitly ruled out; trainings carry the analyzer's pick plus its runner-ups with their scores, failure chances, stat gains and evidence (rainbow/skill-hint counts, relationship bars, Unity Cup spirit gauges where applicable).
- `selected`: the committed action and its reason, the training pick and its source, and a `recovery` block when the turn abandoned its pick and executed a recovery instead.
- `raceEligibility`, `items` and `notes` when the turn recorded them.
- `enteredRace`: identity of a race that actually completed this turn (turn number, resolution, path, and an optional catalog name/match count) -- present only on a turn whose race completed.
- `seq`: additive per-career sequence, present when a `career_state` record was built for the same turn; the join key to it (see below).

`v` is the schema version. Purely additive fields keep the current version, so a reader must ignore fields it does not know; renaming or removing a field, or changing its meaning or units, bumps `v`. Every field beyond `type`, `v`, `ts`, `observation` and `selected` is conditional and omitted rather than filled in when unavailable -- for example `turn` is absent when the date was never read (the constructed default is turn 1), and a candidate `score` is absent for a hard-excluded training because no real ranking existed for it.

Only the main-screen turn boundary emits traces, covering the action choice, the training contest and extra-race eligibility for every scenario. Decisions resolved outside that window stay in their existing chronological log tags: race selection and running-style resolution (`[RACE]` / `[DIALOG]`), skill purchasing (`[SKILLS]` / `[KNAPSACK]`), training-event choices (`[TRAINING_EVENT]`), and spark reroll (`[SPARKS]`).

Example (redacted; key order is not stable, since the writer uses a hash map, and is shown here grouped for readability):

```json
{
  "type": "decision_trace", "v": 1, "ts": 1785312000000, "app": "1.4.0",
  "fp": "1e681a57e1", "scenario": "Trackblazer", "trainee": "Biwa Hayahide",
  "preset": "Biwa Hayahide", "careerToken": "Biwa Hayahide|Trackblazer|run2|3f9a1c22",
  "queueRun": 2, "turn": 25, "year": "CLASSIC", "month": "JANUARY", "phase": "EARLY",
  "state": {"energy": 62, "mood": "GOOD", "skillPts": 340, "fans": 12000,
            "spd": 412, "sta": 300, "pwr": 288, "grt": 190, "wit": 260},
  "observation": {"turnObserved": true, "statsObserved": true,
                  "skillPointsObserved": true, "aptitudesObserved": true},
  "settings": {"Mood Floor": "GOOD"},
  "candidates": [
    {"type": "action", "id": "TRAIN", "selected": true, "reason": "default action: no race required, no recovery needed, no extra race eligible"},
    {"type": "action", "id": "RECOVER_MOOD", "selected": false, "rejected": true, "reason": "mood GOOD at/above floor GOOD"},
    {"type": "training", "id": "SPEED", "selected": true, "failChance": 8, "gains": {"spd": 11, "pwr": 2}, "reason": "won analysis (Year 2+) with score 41.50"},
    {"type": "training", "id": "WIT", "selected": false, "rejected": false, "score": 12.25, "failChance": 3, "reason": "outscored"},
    {"type": "training", "id": "GUTS", "selected": false, "rejected": true, "reason": "excluded (hard penalty)"}
  ],
  "raceEligibility": {"eligible": false, "reason": "not eligible for an extra race this turn"},
  "selected": {"action": "TRAIN", "source": "action_choice", "reason": "default action: no race required, no recovery needed, no extra race eligible",
               "training": "SPEED", "trainingSource": "ANALYSIS", "trainingReason": "won analysis (Year 2+) with score 41.50"}
}
```

The corpus exists so later analysis work has something to read: comparing what the bot chose against what it should have chosen, studying which observations preceded bad turns, or checking a scoring change turn by turn instead of by career outcome. Nothing in the app reads the file back.

### Career state (`career_state.jsonl`)

One `career_state` record per turn, built by `CareerStateBuilder` (`bot/CareerState.kt`): the career identity (`careerToken`, scenario, trainee, preset, queueRun, `fp`), the observed date, condition (energy, mood, statuses), the five stats, skill points, aptitudes, the three cached race-day flags (mandatory / scheduled / goal-ribbon), the scenario extension (Trackblazer or Grand Concert state), and group `provenance`.

It follows the same honesty rules as the decision trace: a group that was never read this career is omitted rather than filled with a default, date components appear only when the date was actually read, and `provenance` labels each group `observed` / `unread` / `configured` / `derived`. Fans are deliberately excluded -- no per-field read flag exists for them, so a default fan count could not be labelled observed -- and no candidate, score or selection evidence appears here; that stays decision-trace-owned.

It rides the same `factualCorpusEnabled` gate as the decision trace and the same non-fatal, shadow-only policy: it is written at the pre-decision boundary, nothing in the gameplay path reads it, and a serialization or append failure is swallowed so a telemetry fault can never change a turn.

### Joining the two streams

Each career-state record carries a per-career monotonic `seq`, allocated exactly once when the build opportunity for a new logical decision turn is consumed (same-turn re-ticks do not advance it, and it does not depend on date OCR). The same `seq` is added as an optional additive field on the decision-trace record for that turn. Offline analysis joins the two streams by **`careerToken + seq`**, never by the observed turn or date -- the turn number is a diagnostic that is absent whenever date OCR failed. A resumed career starts a new `careerToken`, so restarting `seq` at 1 keeps the composite key unique.

A career-state record with no matching trace, or a trace with a `seq` but no matching state, is legitimate coverage -- not corruption -- and the offline analyzer reports these as diagnostics. Only a duplicate `(careerToken, seq)` composite key, which the writer cannot legitimately produce, is a consistency error. `career_finalize` remains the owner of the career outcome.

## Safety

- **Pull-only.** Device mode runs a plain `adb [-s <serial>] pull <remote> <local>`. It never uses root,
  `su`, `adb shell`, `cp` into the outcomes directory, `chmod`, `chown`, truncate, delete, clear, or a rename
  of the source device files.
- **No root writes into telemetry.** A previous file-ownership incident came from root-side scratch tooling
  writing into the outcomes directory. Normal collection never touches device file ownership or permissions.
- **Byte-cap warning.** The per-turn files stop appending once they pass `MAX_FILE_BYTES` (32 MB each);
  nothing already written is deleted, and the writer logs `[OUTCOME] <path> reached its <n> byte cap` once.
  Archive before a long accumulator approaches the cap. `careers.jsonl` is uncapped.
- **Uninstall wipes external files.** `adb install -r` (an in-place reinstall) retains the app external files
  directory; a full uninstall/reinstall does not. Archive before any uninstall.
- **Atomic-ish.** The collector builds into a sibling `.tmp` directory and renames it to the final bundle only
  after every file and the manifest are written. On failure it removes only its own temp directory and never
  touches source telemetry or an existing final bundle. An existing destination is never overwritten.

## Reset

Resetting or clearing device telemetry is a **separate, explicit, manual** step, done only after a verified
archive. The collector never resets or clears anything.

## What a strong evidence set looks like

When accumulating archived careers for broader offline evaluation, aim for:

- at least 3 full careers per supported scenario family (URA, Trackblazer, Grand Concert)
- at least 3 distinct trainees overall
- at least 150 comparable contexts and 100 ANALYSIS bot-TRAIN decisions
- at least 20 recovery-state turns
- 0 corpus issues, and byte-identical evaluator reruns

Collect with the existing bot/queue tooling; do not tune farming settings for evaluation metrics.
