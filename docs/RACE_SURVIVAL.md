# Race Survival Shadow Model (STAM-1)

Offline, read-only, shadow. Nothing here is read by the bot at runtime: no training choice, no lesson
purchase, no skill buy, no career launch and no deck or parent recommendation depends on it.

It answers exactly one question:

> For this race, this running style, this Stamina, this recovery package and this debuff-risk budget,
> does the build survive, and what Stamina would?

It exists so that "Medium wants 800 Stamina" stops being a rule of thumb copied between documents and
becomes something computed from the race in front of you.

## What it is, and what it is not

It is a **survival envelope estimator**. It bounds the HP cost of a race from the game's own decoded
finish-time band and prices recovery and debuffs from the game's own decoded HP fractions.

It is **not** a race simulator. It does not model per-frame velocity, does not resolve the last spurt,
does not predict a finishing position and never reports a win probability. Every mechanic it cannot
price is named in its output rather than replaced with a plausible number.

The practical consequence, stated in every report: **the Stamina figures are a floor, not a target.**
Everything the model is blind to (the last spurt, uphills, being rushed, a soft track) costs HP, so the
real requirement is higher than the estimate, never lower.

## Evidence audit

| Mechanic | Source | Status | Used in STAM-1 |
|---|---|---|---|
| Race identity, distance, surface, track | `src/data/compiled/races.json` | VERIFIED | yes |
| Course set (track, distance, ground, inner/outer) | `master.mdb` `race_course_set` (121 rows) | VERIFIED | yes |
| Track names | `master.mdb` `text_data` category 35 (15 tracks) | VERIFIED | yes |
| Per-course race duration | `master.mdb` `race_course_set.finish_time_min/max` | PARTIALLY_DECODED | yes, as a range |
| Distance band (sprint/mile/medium/long) | derived from `races.json`, asserted against all 402 races | VERIFIED | yes |
| Recovery skill HP value | `master.mdb` `skill_data` `ability_type 9` | VERIFIED | yes |
| Debuff skill HP value | same, negative values on opponent target types | VERIFIED | yes |
| Skill race gates (distance, surface, running style) | `skill_data.condition_1/2` enums, witness-proven | VERIFIED | yes |
| Skill activation probability | not present anywhere in `master.mdb` | UNKNOWN | no |
| Stamina stat, Guts stat | caller-supplied | VERIFIED | Stamina yes, Guts carried only |
| MaxHP formula | external reference; absent from `master.mdb` | UNKNOWN locally | yes, labelled external |
| Strategy HP coefficients | external reference; absent from `master.mdb` | UNKNOWN locally | yes, labelled external |
| Cruise HP drain rate | external reference; absent from `master.mdb` | UNKNOWN locally | yes, labelled external |
| Aptitude multiplier tables | `race_proper_distance_rate`, `_ground_rate`, `_runningstyle_rate` | VERIFIED (values), UNKNOWN (application) | no |
| Course slope and corner geometry | not in `master.mdb`; lives in client course assets | UNKNOWN | no |
| Ground condition HP effect | not decoded | UNKNOWN | no |
| Guts late-race mitigation | not decoded | UNKNOWN | no |
| Target speed effect on HP cost | not decoded | UNKNOWN | no |
| Rush risk and its HP cost | not decoded | UNKNOWN | no |
| Per-strategy HP consumption rate | not decoded | UNKNOWN | no |

The "absent from `master.mdb`" claims were checked, not assumed: every integer column of all 416
tables was scanned for the coefficient values, and no table in the database carries a race-physics
constant, an HP formula or any slope geometry.

## Source boundaries

Three channels, never blended into one constant. Every number the model prints says which it is.

| Channel | What belongs in it | Examples here |
|---|---|---|
| `DECODED_GAME_DATA` | read out of the installed game's `master.mdb` | recovery is 1.5% (white) or 5.5% (gold) of MaxHP; Mystifying Murmur removes 3.0%; Oi 2000m dirt runs 121.9s to 129.0s |
| `EXTERNAL_MECHANICS_REFERENCE` | race-engine formulas this repository has not decoded locally | `MaxHP = 0.8 * strategyCoefficient * Stamina + distance`; the five strategy coefficients; 20 HP/s at cruise |
| `EDITORIAL_RISK_POLICY` | judgement calls that are the operator's, not the game's | "insure against two stamina debuffs"; a safety margin as a fraction of MaxHP |

## The model

### MaxHP

```
MaxHP = 0.8 * strategyCoefficient * Stamina + courseDistance
```

`front 0.95, pace 0.89, late 1.00, end 0.995, runaway 0.86`. All external.

The additive distance term matters more than it looks: a zero-Stamina runner already starts a 2000m
race with 2000 HP. That is why a Stamina requirement is not proportional to distance and has to be
solved rather than looked up.

### Cruise cost

```
requiredHp = 20 * raceDurationSeconds
```

`raceDurationSeconds` comes from the decoded per-course finish-time band, which is why every answer is
a range: the fast end and the slow end of the same course want different builds. `20` is external.

### Recovery

Recovery is modelled in HP, never as an "effective Stamina" conversion. A skill contributes only when
its HP effect is aimed at its own runner and its hard race gates admit the race and the running style.
An ineligible skill contributes exactly zero, and the report still prints its nominal effect so a
reader can see what was thrown away and why.

No activation probability is decoded for any skill, so the output is `totalPotentialHp` (each eligible
skill counted once, which the decoded cooldowns support: every one is longer than any race) and
`expectedHp` is always `null`. Skills are classified by how their activation is gated (`PHASE_ONLY`,
`POSITION_CONDITIONAL`, `GEOMETRY_CONDITIONAL`, `HP_CONDITIONAL`, `EVENT_CONDITIONAL`,
`SKILL_CHAIN_CONDITIONAL`, `MIXED`), never assigned a percentage.

`effectiveStaminaEquivalent` exists as an optional diagnostic only.

### Debuff budget

`BASE`, `ONE_STAMINA_DEBUFF`, `TWO_STAMINA_DEBUFFS`, `CUSTOM`. The named budgets set a count; the
threat itself is resolved from the decoded debuff pool as the worst HP drain that can legally target
the race, so no debuff is hardcoded. On a Medium race that resolves to a 3.0% threat; the model prints
which skill it picked.

All three budgets are always reported, whichever one was selected, so the question "what if one more
debuff lands" never needs a second run.

### Inverse solve

Recovery and debuffs are both fractions of MaxHP, which keeps the survival condition linear in
Stamina:

```
maxHp * (1 + recovery - debuff - margin)  >=  requiredHp + flatDebuffHp
```

so the required Stamina is closed-form. No search, no iteration, no floating-point nondeterminism, and
an integer answer. When the bracket is zero or negative the answer is `null` ("no Stamina survives
this"), not an enormous number.

The tests assert the three monotonicity properties directly: more recovery never raises the
requirement, more debuff pressure never lowers it, and a more HP-efficient strategy never costs more
Stamina.

## Anchor validation

The external guidance for the current Oi 2000m dirt target is roughly "800 Stamina plus one reliable
gold recovery, or 1000+ raw Stamina", discussed with two-debuff insurance. The model was not tuned to
reproduce those numbers. Encoding the race and computing:

| Scenario (pace chaser, two-debuff budget) | Fast end of band | Midpoint | Slow end of band |
|---|---|---|---|
| No recovery | 834 | 940 | 1046 |
| One ungated gold recovery (5.5%) | 633 | 733 | 833 |

The guide's raw-Stamina arm (1000+) sits between the model's midpoint and slow-end figures. Its
gold-recovery arm (800) sits between the same two. Both land inside the decoded band, above the
midpoint, which is exactly where a cruise-only estimate should place them: the model omits the last
spurt, and the omission pushes the real requirement up.

That is agreement, not confirmation. The model shares the MaxHP coefficients with the same external
body of work the guidance came from, so the two are not fully independent. What the check does
establish is that the decoded half (course duration, recovery fractions, debuff fractions) is
consistent with observed play rather than contradicting it.

## Shadow examples

Pace chaser at 800 Stamina with one gold and one white ungated recovery, two-debuff budget:

| Distance | Race | Band | Required Stamina (fast / mid / slow) | Survives at 800 |
|---|---|---|---|---|
| Mile | Yasuda Kinen, Tokyo 1600m turf | 90.8s to 95.0s | 279 / 337 / 395 | yes |
| Medium | Tokyo Daishoten, Oi 2000m dirt | 121.9s to 129.0s | 582 / 680 / 779 | yes |
| Long | Tenno Sho (Spring), Kyoto 3200m turf | 193.0s to 204.0s | 778 / 914 / 1050 | no |

Confidence is `moderate` on all three (the course resolved exactly and every recovery id resolved).
Unpriced mechanics are the same set on all three, and every one of them pushes the requirement up.

## Course resolution coverage

All 402 races in the compiled catalogue resolve onto a decoded course set: 380 exactly, 22 ambiguously.
The 22 are the three courses the game ships in inner and outer variants (Kyoto 1400m turf, Kyoto 1600m
turf, Niigata 2000m turf); the catalogue does not always record which variant a race uses, so the model
widens the finish-time band to cover both and reports the resolution as ambiguous.

The race catalogue writes the Tokyo City Keiba track "Ooi" and the game database writes "Oi". That one
alias is the only track-name divergence and it is declared in `evidence.ts`.

## Running it

```bash
node scripts/race-survival.mjs --race "Tokyo Daishoten" --turn 48 --strategy pace --stamina 800 --recovery 200481 --debuff-budget 2
```

Other useful forms:

```bash
node scripts/race-survival.mjs --find-skill "recover endurance"
node scripts/race-survival.mjs --track Oi --distance 2000 --surface dirt --strategy pace --stamina 800 --compare-stamina 600,800,1000
node scripts/race-survival.mjs --race "Yasuda Kinen" --turn 35 --strategy pace --stamina 700 --json
```

Exit codes: 0 the build survives the selected risk policy, 1 it does not, 2 a usage or load error.

## Regenerating the decoded evidence

`src/data/race_survival_data.json` is committed and generated. After a game patch:

```bash
node scripts/generate-race-survival-data.mjs --db "<path>/master.mdb"
node scripts/generate-race-survival-data.mjs --db "<path>/master.mdb" --check
```

The generator re-proves its own decoding on every run and fails loudly rather than emitting a wrong
file: the 10000 fixed-point scale, the `ability_type 9` sign convention (an effect aimed at the field
is always negative, and a self-aimed effect agrees with the skill's own English description), the
`distance_type` / `running_style` / `ground_type` enums against witness skills, the `ground` enum
against the game's dirt-only tracks, and the ascending order of the aptitude tables that fixes the
G..S row mapping. A target type it does not recognise is a hard failure, not a silent pass.

Tests: `node --test scripts/generate-race-survival-data.test.mjs` for the extractor,
`yarn test` for the model.

## STAM-2 handoff

`buildSurvivalConstraint()` emits a `SurvivalConstraint`: the minimum Stamina, the preferred range
across the decoded band, the recovery skill ids that minimum assumes, the risk policy it was solved
under, the confidence, and the unpriced mechanics. Nothing consumes it yet. The Build Budget Planner
that will combine it with inheritance, deck, trainee growth, scenario bonuses and recovery access is a
later phase, and it should read this contract rather than a number copied out of a report.

## Known limits

- The estimate is a cruise-pace floor. The last spurt is the largest single omission.
- Race duration comes from `race_course_set`'s finish-time band. Whether that band is the simulated
  race duration or a display reference is not proven here, which is why the answer is a range.
- Ground condition, Guts, target speed and rush risk are carried through the whole model and reported,
  but take no part in any calculation.
- Confidence is never `high`, by construction.
