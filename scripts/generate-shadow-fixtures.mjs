// generate-shadow-fixtures - deterministic golden-fixture generator for the Shadow Advisor S1 cross-language
// parity suite. It is the single source of truth pinned by BOTH the Jest suite (TS builder/policy) and the JUnit
// suite (Kotlin adapter/policy): each case is a real-shaped (decision_trace record, career_state record) pair, and
// the expected AdvisorDecisionContext + ShadowRecommendation are computed here by the authoritative TS S1 code
// (buildContextFromRecords + recommend). Regenerate with:
//
//   node scripts/generate-shadow-fixtures.mjs
//
// It writes src/lib/shadowAdvisor/__fixtures__/parity.json. Pure and deterministic: no clock, RNG, or device.
// Requires node >= 23.6 (native TypeScript type stripping).

import { writeFileSync, mkdirSync } from "node:fs"
import { join, dirname } from "node:path"
import { fileURLToPath } from "node:url"
import { buildContextFromRecords } from "../src/lib/shadowAdvisor/context.ts"
import { recommend } from "../src/lib/shadowAdvisor/policy.ts"

const HERE = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(HERE, "..", "src", "lib", "shadowAdvisor", "__fixtures__")
const OUT_FILE = join(OUT_DIR, "parity.json")

const TOKEN = "Copano Rickey|Grand Concert|run1|f9b1bc4b"

/** A training candidate as DecisionTrace.buildCandidates writes it (advisor reads type/id/gains/failChance only). */
function train(id, gains, failChance, extra = {}) {
    const c = { type: "training", id, selected: false, reason: "candidate", ...extra }
    if (gains !== null) c.gains = gains
    if (failChance !== null) c.failChance = failChance
    return c
}

/** A decision_trace record shaped like DecisionTrace.buildRecord's output. */
function trace(candidates, { seq = 1, turn = 20, careerToken = TOKEN } = {}) {
    return { type: "decision_trace", v: 1, ts: 1, seq, turn, careerToken, candidates, selected: { action: "TRAIN" } }
}

/** A career_state record shaped like CareerStateSerializer.buildRecord's output. */
function state({ energy = 70, mood = "GREAT", negativeStatuses = [], stats = { spd: 300, sta: 200, pwr: 150, grt: 120, wit: 110 }, skillPts = 120, race = { mandatory: false, scheduled: false, goalRibbon: false }, scenario = "URA Finale", seq = 1, careerToken = TOKEN } = {}) {
    return {
        type: "career_state",
        v: 1,
        ts: 1,
        seq,
        identity: { careerToken },
        turn: 20,
        observation: { turnObserved: true },
        condition: { energy, mood, negativeStatuses },
        stats,
        skillPts,
        race,
        scenario: { type: scenario },
    }
}

const FIVE = (over = {}) => [
    train("SPEED", { spd: 20, pwr: 4 }, 10, over.SPEED),
    train("STAMINA", { sta: 12, grt: 2 }, 8, over.STAMINA),
    train("POWER", { pwr: 10 }, 6, over.POWER),
    train("GUTS", { grt: 8 }, 4, over.GUTS),
    train("WIT", { wit: 6, spd: 1 }, 2, over.WIT),
]

// Each entry: name, decisionTrace, careerState (or null). Expected context/recommendation are computed below.
const CASES = [
    // 1. complete five-facility TRAIN, positive margin.
    { name: "complete-train-positive-margin", decisionTrace: trace(FIVE()), careerState: state() },
    // 2. complete TRAIN, negative margin: the highest-total facility is over the failChance limit and excluded, so
    //    the winner scores lower than the best (over-limit) alternative -> trainingAlternativeExcludedByFailureRisk.
    {
        name: "complete-train-negative-margin",
        decisionTrace: trace([
            train("SPEED", { spd: 60 }, 55), // total 60 - 27.5 = 32.5 but failChance 55 > 40 -> excluded
            train("STAMINA", { sta: 12 }, 8), // total 12 - 4 = 8 (under limit winner)
            train("POWER", { pwr: 10 }, 6),
            train("GUTS", { grt: 8 }, 4),
            train("WIT", { wit: 6 }, 2),
        ]),
        careerState: state(),
    },
    // 3. deterministic tie-break: two under-limit facilities with equal total AND equal failChance -> canonical order.
    {
        name: "train-tie-break-canonical",
        decisionTrace: trace([
            train("SPEED", { spd: 20 }, 10), // total 15
            train("STAMINA", { sta: 20 }, 10), // total 15, same failChance -> SPEED wins by canonical order
            train("POWER", { pwr: 8 }, 6),
            train("GUTS", { grt: 6 }, 4),
            train("WIT", { wit: 4 }, 2),
        ]),
        careerState: state(),
    },
    // 4. equal totals, different failChance -> lower failChance wins the tie.
    {
        name: "train-tie-break-failchance",
        decisionTrace: trace([
            train("SPEED", { spd: 25 }, 20), // total 25 - 10 = 15
            train("STAMINA", { sta: 20 }, 10), // total 20 - 5 = 15, lower failChance -> STAMINA wins
            train("POWER", { pwr: 8 }, 6),
            train("GUTS", { grt: 6 }, 4),
            train("WIT", { wit: 4 }, 2),
        ]),
        careerState: state(),
    },
    // 5. all-over-limit least-risk fallback.
    {
        name: "train-all-over-limit-least-risk",
        decisionTrace: trace([
            train("SPEED", { spd: 30 }, 60),
            train("STAMINA", { sta: 28 }, 45), // lowest failChance among the over-limit set -> least-risk winner
            train("POWER", { pwr: 26 }, 50),
            train("GUTS", { grt: 24 }, 55),
            train("WIT", { wit: 22 }, 52),
        ]),
        careerState: state(),
    },
    // 6. one facility missing gains -> insufficientEvidence.
    { name: "train-missing-gains", decisionTrace: trace([train("SPEED", null, 10), ...FIVE().slice(1)]), careerState: state() },
    // 7. one facility missing failChance -> insufficientEvidence.
    { name: "train-missing-failchance", decisionTrace: trace([train("SPEED", { spd: 20 }, null), ...FIVE().slice(1)]), careerState: state() },
    // 8. incomplete facility set (only four) -> incomplete contest.
    { name: "train-incomplete-four-facilities", decisionTrace: trace(FIVE().slice(0, 4)), careerState: state() },
    // 9. REST from the energy threshold.
    { name: "rest-energy-below-threshold", decisionTrace: trace(FIVE()), careerState: state({ energy: 18 }) },
    // 10. RECOVER_MOOD (energy fine, mood below the NORMAL floor).
    { name: "recover-mood-below-floor", decisionTrace: trace(FIVE()), careerState: state({ energy: 70, mood: "BAD" }) },
    // 11. race-day notApplicable.
    { name: "race-day-mandatory-not-applicable", decisionTrace: trace(FIVE()), careerState: state({ race: { mandatory: true, scheduled: false, goalRibbon: true } }) },
    // 12. stateUnavailable: no training candidates and no career_state at all.
    { name: "state-unavailable-no-contest-no-state", decisionTrace: trace([]), careerState: null },
    // 13. no-contest notApplicable: no training candidates, state present, no race, energy/mood fine.
    { name: "no-contest-not-applicable", decisionTrace: trace([{ type: "action", id: "REST", selected: true }]), careerState: state({ energy: 80, mood: "GOOD" }) },
    // 14. FORCED_DEFAULT record shape: a forced trainingSource + committed selection present but IGNORED by S1.
    {
        name: "forced-default-selection-ignored",
        decisionTrace: {
            ...trace(FIVE()),
            selected: { action: "TRAIN", trainingType: "GUTS", trainingSource: "FORCED_DEFAULT", score: 999 },
        },
        careerState: state(),
    },
    // 15. forced-from-skipped shape: forced onto a facility other than the advisor pick; still ignored by S1.
    {
        name: "forced-from-skipped-selection-ignored",
        decisionTrace: {
            ...trace(FIVE()),
            selected: { action: "TRAIN", trainingType: "WIT", trainingSource: "FORCED_FROM_SKIPPED", score: 1 },
        },
        careerState: state(),
    },
    // 16a. numeric formatting: an integer-valued total (no trailing ".0").
    { name: "numeric-integer-total", decisionTrace: trace([train("SPEED", { spd: 30 }, 8), train("STAMINA", { sta: 10 }, 8), train("POWER", { pwr: 8 }, 8), train("GUTS", { grt: 6 }, 8), train("WIT", { wit: 4 }, 8)]), careerState: state() },
    // 16b. numeric formatting: a half-step total (odd failChance -> ".5" penalty).
    { name: "numeric-half-step-total", decisionTrace: trace([train("SPEED", { spd: 30 }, 9), train("STAMINA", { sta: 10 }, 9), train("POWER", { pwr: 8 }, 9), train("GUTS", { grt: 6 }, 9), train("WIT", { wit: 4 }, 9)]), careerState: state() },
    // 17. forbidden post-execution blocks (selected/score/enteredRace/recovery/transitions) present but ignored: the
    //     result must equal the same contest with none of them, proving they are not read as policy input.
    {
        name: "forbidden-post-execution-blocks-ignored",
        decisionTrace: {
            ...trace(FIVE()),
            selected: { action: "TRAIN", trainingType: "GUTS", trainingSource: "ANALYSIS", score: 42 },
            enteredRace: { name: "Some Race", valid: true },
            recovery: { kind: "rest" },
            observedTransition: { spd: 5 },
        },
        careerState: { ...state(), recovery: { kind: "rest" }, enteredRace: { valid: false } },
    },
]

const fixtures = CASES.map((c) => {
    const context = buildContextFromRecords(c.decisionTrace, c.careerState)
    if (context === null) throw new Error(`case ${c.name} produced a null context (unexpected for a seq'd trace)`)
    const recommendation = recommend(context)
    return { name: c.name, decisionTrace: c.decisionTrace, careerState: c.careerState, expectedContext: context, expectedRecommendation: recommendation }
})

mkdirSync(OUT_DIR, { recursive: true })
writeFileSync(OUT_FILE, JSON.stringify(fixtures, null, 2) + "\n", "utf8")
process.stdout.write(`wrote ${fixtures.length} fixtures -> ${OUT_FILE}\n`)
