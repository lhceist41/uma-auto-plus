package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.StatName
import org.json.JSONArray
import org.json.JSONObject

/**
 * Immutable copy of one turn's decision evidence, produced by [DecisionTracer.turnEvidence] and
 * handed to the trace sink by [DecisionTracer.emit].
 *
 * Copied rather than shared so a sink cannot mutate the tracer's live buffers, and so the record
 * a sink serializes is exactly what the turn's Decision Report rendered.
 */
data class TurnEvidence(
    /** Game date captured when the turn opened, or null when no turn was ever opened. */
    val date: GameDate?,
    /** State snapshot captured when the turn opened, or null when no turn was ever opened. */
    val state: DecisionTracer.StateSnapshot?,
    /** Decision-relevant settings captured when the turn opened. */
    val settings: Map<String, String>,
    /** Decision events recorded during the turn, in recording order. */
    val events: List<DecisionTracer.DecisionEvent>,
)

/**
 * Serializes one turn's [TurnEvidence] into a single append-only JSON record.
 *
 * This is observability only. It reads what [DecisionTracer] already captured for the turn's
 * human-readable Decision Report and re-renders it machine-readably; it performs no screen reads,
 * asks the scorers nothing, and never participates in choosing an action. The record is built
 * after the turn's action has already executed, so nothing here can reach gameplay control flow.
 *
 * Honesty rules, matching [SkillSpendTelemetry]:
 * - Optional identity and optional numbers are OMITTED when unavailable, never filled with a
 *   placeholder. A missing field means "not known at decision time"; a fabricated one silently
 *   mis-attributes the turn.
 * - `turn` is written only when the date was actually read from the screen. `observation` carries
 *   the read flags explicitly so a consumer can tell an unobserved value from an absent one.
 * - Candidate scores, failure chances and stat gains appear only where the existing decision code
 *   already handed them to the tracer. No scorer is re-run to manufacture them.
 *
 * Pure and Context-free so JUnit can pin the shape without a live Campaign. [Campaign] appends the
 * result via `OutcomeCorpus.append` from inside the trace sink; isolation comes from two layers, not
 * a runCatching here: `DecisionTracer.emit()` wraps the sink in a try/catch that bounds a serializer
 * exception to one warning per career, and `OutcomeCorpus.append` swallows its own disk-append
 * failures. So a telemetry failure cannot change a turn.
 */
object DecisionTrace {
    /** Record type discriminator, matching the `type` field the other corpus records carry. */
    const val SCHEMA: String = "decision_trace"

    /**
     * Schema version. Bump on any change that a reader cannot absorb by tolerating new fields:
     * renaming or removing a field, or changing the meaning or units of an existing one. Purely
     * additive fields keep the current version, so readers must ignore fields they do not know.
     */
    const val SCHEMA_VERSION: Int = 1

    /**
     * Byte cap for the decision-trace file. A debug-gated per-turn record runs about 2 KB in practice
     * (roughly 1,900 bytes measured live), so 32 MB still holds a few hundred careers; past it the
     * writer drops records rather than filling the device. Nothing is rotated or deleted - the cap
     * exists so an unattended debug session cannot grow the file without bound.
     */
    const val MAX_FILE_BYTES: Long = 32L * 1024 * 1024

    /** Candidate type token for a main-screen action considered this turn. */
    private const val CANDIDATE_ACTION: String = "action"

    /** Candidate type token for a training considered this turn. */
    private const val CANDIDATE_TRAINING: String = "training"

    /** Fixed stat order so every record's stat maps read the same way. */
    private val STAT_KEYS: List<Pair<StatName, String>> =
        listOf(StatName.SPEED to "spd", StatName.STAMINA to "sta", StatName.POWER to "pwr", StatName.GUTS to "grt", StatName.WIT to "wit")

    /**
     * Builds the `decision_trace` record for one turn.
     *
     * @param timestamp Wall-clock epoch milliseconds at emit time.
     * @param evidence The turn's captured evidence.
     * @param app App versionName, or null when unavailable.
     * @param fp Config-arm fingerprint shared with the outcome records, or null when unavailable.
     * @param scenario Scenario name, or null/blank when unavailable.
     * @param trainee Trainee name as read in-career, or null/blank when unavailable.
     * @param preset Applied preset identity, or null/blank when no preset was applied.
     * @param careerToken The career identity token shared with the finalize records, or null.
     * @param queueRun Run queue index when a queue is driving this career, or null.
     * @param seq This turn's per-career CareerState decision sequence, or null when no CareerState was
     * built for the turn (release/non-debug, or a swallowed build). Additive and version-neutral: it
     * is the offline join key to the separate `career_state` records and replaces no existing field.
     * @return The record, ready to append as one JSONL line.
     */
    @Suppress("LongParameterList")
    fun buildRecord(
        timestamp: Long,
        evidence: TurnEvidence,
        app: String? = null,
        fp: String? = null,
        scenario: String? = null,
        trainee: String? = null,
        preset: String? = null,
        careerToken: String? = null,
        queueRun: Int? = null,
        seq: Int? = null,
    ): JSONObject {
        val record = JSONObject()
        record.put("type", SCHEMA)
        record.put("v", SCHEMA_VERSION)
        record.put("ts", timestamp)
        // Additive join key to the separate career_state stream; omitted when no CareerState was built.
        seq?.let { record.put("seq", it) }
        app?.takeIf { it.isNotBlank() }?.let { record.put("app", it) }
        fp?.takeIf { it.isNotBlank() }?.let { record.put("fp", it) }
        scenario?.takeIf { it.isNotBlank() }?.let { record.put("scenario", it) }
        trainee?.takeIf { it.isNotBlank() }?.let { record.put("trainee", it) }
        preset?.takeIf { it.isNotBlank() }?.let { record.put("preset", it) }
        careerToken?.takeIf { it.isNotBlank() }?.let { record.put("careerToken", it) }
        queueRun?.let { record.put("queueRun", it) }

        val date = evidence.date
        if (date != null) {
            // Only a date the bot actually read becomes a turn number. An unread date still holds the
            // constructed default (turn 1), which the outcome corpus already learned not to record.
            if (date.dayObserved) record.put("turn", date.day)
            record.put("year", date.year.name)
            record.put("month", date.month.name)
            record.put("phase", date.phase.name)
        }

        evidence.state?.let { record.put("state", buildState(it)) }
        record.put("observation", buildObservation(date, evidence.state))
        if (evidence.settings.isNotEmpty()) {
            record.put("settings", JSONObject().apply { evidence.settings.forEach { (key, value) -> put(key, value) } })
        }

        val candidates = buildCandidates(evidence.events)
        if (candidates.length() > 0) record.put("candidates", candidates)
        record.put("selected", buildSelected(evidence.events))

        buildRaceEligibility(evidence.events)?.let { record.put("raceEligibility", it) }
        val items = buildItems(evidence.events)
        if (items.length() > 0) record.put("items", items)
        val notes = evidence.events.filterIsInstance<DecisionTracer.DecisionEvent.Note>().map { it.message }
        if (notes.isNotEmpty()) record.put("notes", JSONArray().apply { notes.forEach { put(it) } })

        return record
    }

    /**
     * Renders the state the decision engine saw when the turn opened.
     *
     * Deliberately the turn-open snapshot, not live state at emit time: the turn's action has
     * already executed by then, so reading the trainee again would record the consequence of the
     * decision as though it were its input.
     */
    private fun buildState(state: DecisionTracer.StateSnapshot): JSONObject =
        JSONObject().apply {
            put("energy", state.energy)
            put("mood", state.mood.name)
            put("skillPts", state.skillPoints)
            put("fans", state.fans)
            STAT_KEYS.forEach { (stat, key) -> state.stats[stat]?.let { put(key, it) } }
            if (state.negativeStatuses.isNotEmpty()) {
                put("negativeStatuses", JSONArray().apply { state.negativeStatuses.forEach { put(it) } })
            }
            if (state.inventory.isNotEmpty()) {
                put(
                    "inventory",
                    JSONObject().apply {
                        state.inventory.forEach { (group, items) ->
                            put(group, JSONObject().apply { items.forEach { (name, count) -> put(name, count) } })
                        }
                    },
                )
            }
            if (state.extra.isNotEmpty()) {
                put("extra", JSONObject().apply { state.extra.forEach { (key, value) -> put(key, value) } })
            }
        }

    /**
     * Renders which parts of the state were genuinely read this turn.
     *
     * These are the read flags the existing readers already maintain. They are not confidence
     * scores: the stat/skill-point/aptitude readers do not expose one, so none is invented here.
     * A false flag means the corresponding value is a carried-over or default value rather than a
     * fresh observation, and must not be read as evidence of what was on screen.
     */
    private fun buildObservation(date: GameDate?, state: DecisionTracer.StateSnapshot?): JSONObject =
        JSONObject().apply {
            put("turnObserved", date?.dayObserved ?: false)
            put("statsObserved", state?.statsObserved ?: false)
            put("skillPointsObserved", state?.skillPointsObserved ?: false)
            put("aptitudesObserved", state?.aptitudesObserved ?: false)
        }

    /**
     * Flattens every candidate the turn's events exposed into one list.
     *
     * The main-screen cascade only names the alternatives it explicitly ruled out on the way down,
     * so this list is the honest subset the decision code hands over - not an exhaustive action
     * space. Training candidates carry the analyzer's own scores because the analyzer already
     * passes them to the tracer.
     */
    private fun buildCandidates(events: List<DecisionTracer.DecisionEvent>): JSONArray {
        val candidates = JSONArray()
        // Only the FINAL training contest is authoritative. Trackblazer Irregular Training legitimately
        // calls recommendTraining twice in one turn - the pre-screen evaluation and then the executed
        // fast path - so the turn holds two TrainingSelection events, each with a non-null pick. Rendering
        // both would emit two selected:true training candidates, a self-contradiction the analyzer rightly
        // rejects. Serialize only the last event, matching buildSelected's lastOrNull(), so the candidate
        // contest and selected.training describe the same final selection.
        val finalTraining = events.filterIsInstance<DecisionTracer.DecisionEvent.TrainingSelection>().lastOrNull()
        events.forEach { event ->
            when (event) {
                is DecisionTracer.DecisionEvent.ActionChoice -> {
                    candidates.put(
                        JSONObject().apply {
                            put("type", CANDIDATE_ACTION)
                            put("id", event.chosen.name)
                            put("selected", true)
                            put("reason", event.reason)
                        },
                    )
                    event.rejected.forEach { alternative ->
                        candidates.put(
                            JSONObject().apply {
                                put("type", CANDIDATE_ACTION)
                                put("id", alternative.action)
                                put("selected", false)
                                put("rejected", true)
                                put("reason", alternative.reason)
                            },
                        )
                    }
                }

                is DecisionTracer.DecisionEvent.TrainingSelection -> {
                    // Skip earlier provisional evaluations; only the final selection is the authoritative
                    // contest for the turn, matching buildSelected's lastOrNull() (see note above).
                    if (event !== finalTraining) return@forEach
                    event.selected?.let { picked ->
                        candidates.put(
                            JSONObject().apply {
                                put("type", CANDIDATE_TRAINING)
                                put("id", picked.name)
                                put("selected", true)
                                put("reason", event.reason)
                                event.pickedFailureChance?.let { put("failChance", it) }
                                event.pickedStatGains?.let { put("gains", statGains(it)) }
                            },
                        )
                    }
                    event.runnerUps.forEach { runnerUp ->
                        candidates.put(
                            JSONObject().apply {
                                put("type", CANDIDATE_TRAINING)
                                put("id", runnerUp.stat.name)
                                put("selected", false)
                                put("rejected", runnerUp.rejected)
                                put("reason", runnerUp.reason)
                                // Absent for a hard-excluded training: the tracer drops the -Infinity
                                // sentinel rather than pass a score that is not a real ranking.
                                runnerUp.score?.takeIf { it.isFinite() }?.let { put("score", it) }
                                runnerUp.failureChance?.let { put("failChance", it) }
                                runnerUp.statGains?.let { put("gains", statGains(it)) }
                            },
                        )
                    }
                }

                else -> Unit
            }
        }
        return candidates
    }

    /**
     * Renders the action the turn actually committed to.
     *
     * `action` is the main-screen cascade's pick and `source` names the recorder that produced it.
     * A `recovery` block means the turn abandoned that pick and executed a recovery instead, so a
     * consumer reading only `action` would be wrong about what ran. Everything is omitted when the
     * turn recorded no selection at all (the tracer opened but a dialog ended the tick).
     */
    private fun buildSelected(events: List<DecisionTracer.DecisionEvent>): JSONObject {
        val selected = JSONObject()
        events.filterIsInstance<DecisionTracer.DecisionEvent.ActionChoice>().lastOrNull()?.let { choice ->
            selected.put("action", choice.chosen.name)
            selected.put("reason", choice.reason)
            selected.put("source", "action_choice")
        }
        events.filterIsInstance<DecisionTracer.DecisionEvent.TrainingSelection>().lastOrNull()?.let { training ->
            training.selected?.let { selected.put("training", it.name) }
            training.source?.let { selected.put("trainingSource", it.name) }
            selected.put("trainingReason", training.reason)
        }
        events.filterIsInstance<DecisionTracer.DecisionEvent.RecoveryExecuted>().lastOrNull()?.let { recovery ->
            selected.put(
                "recovery",
                JSONObject().apply {
                    put("action", recovery.action)
                    put("reason", recovery.reason)
                },
            )
        }
        return selected
    }

    /** Renders the turn's extra-race eligibility verdict, or null when none was recorded. */
    private fun buildRaceEligibility(events: List<DecisionTracer.DecisionEvent>): JSONObject? {
        val eligibility = events.filterIsInstance<DecisionTracer.DecisionEvent.RaceEligibility>().lastOrNull() ?: return null
        return JSONObject().apply {
            put("eligible", eligibility.eligible)
            put("reason", eligibility.reason)
        }
    }

    /** Renders the turn's item, charm and whistle verdicts. Empty when the campaign tracks no items. */
    private fun buildItems(events: List<DecisionTracer.DecisionEvent>): JSONArray {
        val items = JSONArray()
        events.forEach { event ->
            when (event) {
                is DecisionTracer.DecisionEvent.ItemDecision ->
                    items.put(
                        JSONObject().apply {
                            put("item", event.item)
                            put("verdict", event.verdict.name)
                            put("reason", event.reason)
                        },
                    )

                is DecisionTracer.DecisionEvent.CharmGate ->
                    items.put(
                        JSONObject().apply {
                            put("item", "Good-Luck Charm")
                            put("verdict", if (event.queued) DecisionTracer.ItemVerdict.USED.name else DecisionTracer.ItemVerdict.SKIPPED.name)
                            event.blockingGate?.let { put("reason", it) }
                        },
                    )

                is DecisionTracer.DecisionEvent.WhistleOutcome ->
                    items.put(
                        JSONObject().apply {
                            put("item", "Reset Whistle")
                            put("verdict", event.verdict.name)
                            put("reason", event.reason)
                            event.postRollSelection?.let { put("postRollSelection", it.name) }
                        },
                    )

                else -> Unit
            }
        }
        return items
    }

    /** Renders a stat-gain map with the canonical short keys, omitting stats the caller did not supply. */
    private fun statGains(gains: Map<StatName, Int>): JSONObject =
        JSONObject().apply {
            STAT_KEYS.forEach { (stat, key) -> gains[stat]?.let { put(key, it) } }
        }
}
