# Porting from upstream

How to bring work across from `steve1316/uma-android-automation`. This is the method. The dated
`UPSTREAM_TRIAGE_*.md` files are the *records* — what was decided about a specific commit range on a
specific day. Read the method here, then write a new record for the range you triage.

## Establish the real baseline first

There is no clean merge base. Our history is squashed and edited, so `git merge-base` and
`--not HEAD` return garbage — they count upstream's entire history. **A plain `git merge` is never
the move.**

The fork point is the tag `v5.4.8`. Work from it:

```bash
git fetch upstream --tags

# what upstream added since our fork point
git log --oneline v5.4.8..upstream/master

# cumulative change to one file or directory
git diff --stat v5.4.8 upstream/master -- <path>

# one commit's effect on one file
git show <sha> -- path/to/file.kt

# read upstream's current version of a file
git show upstream/master:path/to/file.kt
```

**Trust `upstream/master` refs only.** This clone also fetches sibling forks, so the `v5.*` tag
namespace is polluted — sibling forks reuse the same tag names, and a local `git tag` will list
versions that are not steve1316's. Tags lie about the upstream tip; refs do not.

## The one hard rule

**Never blind-merge a file where our fork is ahead.** `Game.kt`, `Campaign.kt`, `TrainingEvent.kt`
and others carry features upstream does not have — the run queue, the watchdogs, Daily Races, Team
Trials, deck validation, the OCR gate. Overwriting them silently deletes working functionality.

"Ahead" is a fact about the code, not a preference. Check before you overwrite.

## Files that conflict by default

When porting, audit interactions with these first. Conflicts here are the rule, not the exception:

| File | Local additions that collide |
|---|---|
| `Campaign.kt` | `moodFloor`, `runDeckValidation`, the alarm-clock skip-marker bridge, `Aptitude` import |
| `Racing.kt` | `bAlarmClockPolicySkippedThisRace`, `resolveStrategyForCurrentRace`, ungated grade OCR on mandatory and scheduled races |
| `Training.kt` | the `SelectionSource` enum, `lastSelectionSource`, the scenario bonding bonus |
| `SkillPlan.kt` | the `OPTIMIZE_KNAPSACK` strategy, the knapsack DP, `KnapsackGroup` / `KnapsackChoice` |
| `Trackblazer.kt` | energy snapshot via `passStartEnergy`, the asset-path fix at the race-list tap, the alarm-clock retry guard |
| `DialogHandler.kt` | the alarm-clock skip-marker bridge call |

## Two strategies — choose deliberately

**Cherry-pick** individual improvements onto our tree. Cheap, low risk, keeps us diverging, and we
pay a re-triage tax every few months.

**Re-baseline** — reset onto an upstream tag and re-apply our value-adds on top. Expensive, but the
only way the large welded features arrive cleanly, and it ends the divergence tax. See
`docs/ENGINEERING_NOTES.md` for the standing position, which is currently *declined* with reasons.

## Triage guidance

This is not a ban list. Judge each item.

**Take freely.** Game-data updates (`characters.json`, `supports.json`, `skills.json`), isolated bug
fixes, scenario-general decision-quality improvements (event-choice stat priority, energy-aware event
selection, training-failure recovery), and foundation-library bumps.

**Evaluate large features on merit, do not reflex-skip.** They are usually a re-baseline decision
rather than a cherry-pick because they are welded to upstream's restructured package — but measure
the payoff before committing to either. A feature whose value is concentrated in one scenario is not
a general win just because it sounds like one.

**Watch for already-have.** Several things were built here independently — the `SelectionSource`
fallbacks and training-level weighting among them. **Diff the function, not the file**, before
porting, or you will double-implement.

**Document the call.** One line of rationale in the commit message, whether you ported or skipped.

## Recording a triage

Write a new `UPSTREAM_TRIAGE_<date>.md` for each range you work through. Include the commit range,
the upstream tip it corresponds to, a per-commit verdict table with reasons, any port detail worth
preserving, and gaps you consciously left unported. Those records are what stop the next triage from
re-deriving the same conclusions.
