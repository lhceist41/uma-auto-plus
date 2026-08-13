# Shadow Advisor (engineering)

The Shadow Advisor is an observational baseline that answers one question, and only records the answer:

> What would the static S1 policy `raw-gain-ranker-v1` have recommended from the same pre-decision facts,
> given the turn the bot actually saw?

It has **no gameplay authority**. It never influences `decideNextAction`, the committed `MainScreenAction`,
training-facility selection or candidate ordering, the bot's `candidate.score`, thresholds, REST/race/date
logic, queue state, navigation, OCR, TP spending, or the `DecisionTrace` / `CareerState` facts. The bot runs
exactly as it does without it.

## Stages

- **S1** (`src/lib/shadowAdvisor/`): the pure policy and context contract in TypeScript. `raw-gain-ranker-v1`
  ranks a complete five-facility training contest by weighted raw stat gains minus a failure penalty, with a
  state recovery guardrail (energy before mood) and a race-day suppression. It reuses none of the bot's scoring.
- **S2** (`scripts/shadow-advisor.mjs`): offline evaluation of an archived corpus. It joins the advisor's
  recommendation to the committed `DecisionTrace` on `(careerToken, seq)` and reports coverage / agreement, never
  accuracy or "would have done better".
- **S3** (live shadow): the Kotlin port of S1 evaluated at runtime, from the immutable serialized facts, writing
  a separate telemetry stream. This is what this doc covers.

## S3 runtime pipeline

1. Per turn, `Campaign` builds and appends the pre-decision `CareerState` (with its allocated `seq`), then makes
   and executes the bot decision, then appends the factual `decision_trace`.
2. **Strictly after** that factual `decision_trace` append -- the only S3 invocation point, in
   `Campaign.appendDecisionTrace` -- the sink runs. It is post-execution in wall-clock time but its input is
   strictly pre-decision in content: it reads only the serialized `decision_trace` candidates' raw
   `gains`/`failChance` and the serialized same-seq `career_state` (condition, stats, race flags, scenario). It
   never reads `selected`, `trainingSource`, `enteredRace`, `recovery`, observed transitions, the final outcome,
   or any `candidate.score`.
3. It evaluates the Kotlin S1 policy and appends one record to `outcomes/shadow_advisor.jsonl`.

The sink (`bot/shadowadvisor/ShadowAdvisorSink.kt`) is a per-`Campaign` instance under the same
`BuildConfig.DEBUG || game.debugMode` gate as the decision tracer -- **no new setting**. It is wrapped entirely
in one `try/catch`, so any failure (missing/mismatched state, JSON parse, policy exception, serialization, or
writer failure) leaves the run unchanged and never rethrows, stops, retries, or alters the factual telemetry. It
emits **at most one record per `(careerToken, seq)`** (a reopened-turn retry that reuses the seq is dropped), and
only pairs the retained `career_state` when its seq matches the trace seq exactly. Grand Concert's state-only
lesson/concert lifecycle seqs therefore receive no shadow record, because S3 only fires from the decision sink.

## `shadow_advisor.jsonl` (schema v1)

Append-only, under `outcomes/`, joined offline to `decisions.jsonl` by `(careerToken, seq)`. Fields:
`type` (`shadow_advisor`), `v` (1), `ts`, `careerToken`, `seq`, `turn` (when observed), `scenarioType` (when
known), `advisorVersion`, `policyId`, `source` (`live_shadow`), `status`, optional `recommended`
(`action` + optional `trainingType`), optional `scoreMargin`, `reasons`, `limitations`, optional `scoreBreakdown`.
It deliberately duplicates none of the committed bot action, selected training, `trainingSource`, candidate
score, `enteredRace`, or final outcome -- the offline comparison recovers those from the joined `decision_trace`.

## Live/offline parity

The Kotlin port must reproduce the TypeScript authority field-for-field, including reason detail strings and
their JS number formatting (an integer-valued double prints without a trailing `.0`; a half-step keeps one
decimal, via `JsNumber`). This is pinned by checked-in golden fixtures in `src/lib/shadowAdvisor/__fixtures__/`,
regenerated with `node scripts/generate-shadow-fixtures.mjs` and asserted by **both** the Jest suite
(`shadowAdvisorParity.test.ts`) and the JUnit suite (`ShadowAdvisorParityTest`). To re-derive a live record
offline for a proof, feed the archived `decision_trace` + `career_state` bytes through `buildContextFromRecords`
and `recommend` in `src/lib/shadowAdvisor/` -- the same record-based path the runtime mirrors.

## Collector

`scripts/collect-telemetry.mjs` archives `shadow_advisor.jsonl` as an **optional** file: it is hashed and
malformed-line-counted into the manifest when present, an archive without it stays valid, and older archives
that predate the stream remain readable. The collector never requires it.
