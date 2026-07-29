## What this changes

<!-- One or two sentences on the behavior change, and why. -->

## Publication checklist

Read your own diff before ticking these. `yarn test:hygiene` catches paths; it cannot judge
content. See CONTRIBUTING.md.

- [ ] No personal or account data: trainer name, player or friend IDs, decks, parents, borrowed
      supports, inheritance, currencies, inventory, or private settings.
- [ ] No logs, telemetry, databases, or local captures: no `.log`, `.jsonl`, `.db`, SQLite
      journals, screenshots, or recordings outside sanitized fixtures.
- [ ] No generated build output: no APK, AAB, JS bundle, coverage, or Gradle and Metro caches.
- [ ] No prompts, transcripts, task files, or one-off investigation write-ups.
- [ ] Any fixture added or changed is cropped to what the test needs, has unrelated account UI
      removed, and is referenced by an active test.
- [ ] Fixture provenance records only what is technically necessary, not personal chronology.
- [ ] Player-facing documentation describes shipped behavior, with no internal task identifiers,
      phase numbers, or audit language, and no conclusion drawn from a single run.
- [ ] Any new ignore rule or hygiene allowlist entry has a written justification below.
- [ ] Commit messages contain no local paths, account values, or private diagnostics.

## Verification

- [ ] `yarn test:hygiene`
- [ ] `node_modules/.bin/tsc --noEmit`
- [ ] `yarn jest`
- [ ] `cd android && ./gradlew testDebugUnitTest` (when Kotlin changed)

## Justification for new exceptions

<!-- Leave empty if none. Name each new ignore rule or allowlist entry and why it is needed. -->
