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
