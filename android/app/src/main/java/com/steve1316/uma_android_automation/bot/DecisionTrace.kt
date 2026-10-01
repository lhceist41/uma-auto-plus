package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.StatName
import org.json.JSONArray
import org.json.JSONObject

/** Immutable copy, so a sink cannot mutate the tracer's live buffers and the record matches the rendered Decision Report. */
data class TurnEvidence(
    val date: GameDate?,
    val state: DecisionTracer.StateSnapshot?,
    val settings: Map<String, String>,
    val events: List<DecisionTracer.DecisionEvent>,
)

/**
 * Serializes one turn's [TurnEvidence] into one append-only JSON record. Observability only: built after the turn's
 * action executed, never re-reads the screen or re-runs scorers. Unavailable optional fields are OMITTED, never
 * placeholder-filled, because a fabricated value silently mis-attributes the turn.
 *
 * A telemetry failure cannot change a turn: `DecisionTracer.emit()` try/catches the sink and `OutcomeCorpus.append`
 * swallows its own disk failures.
 */
object DecisionTrace {
    const val SCHEMA: String = "decision_trace"

    /** Bump on a change readers cannot absorb by tolerating new fields (rename, removal, new meaning); additive fields keep it. */
    const val SCHEMA_VERSION: Int = 1

    /** Past 32 MiB the writer drops records rather than filling the device (about 1.9 KB per turn measured live). Nothing is rotated. */
    const val MAX_FILE_BYTES: Long = 32L * 1024 * 1024

    private const val CANDIDATE_ACTION: String = "action"

    private const val CANDIDATE_TRAINING: String = "training"

    private val STAT_KEYS: List<Pair<StatName, String>> =
        listOf(StatName.SPEED to "spd", StatName.STAMINA to "sta", StatName.POWER to "pwr", StatName.GUTS to "grt", StatName.WIT to "wit")

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
        enteredRace: EnteredRace? = null,
    ): JSONObject {
        val record = JSONObject()
        record.put("type", SCHEMA)
        record.put("v", SCHEMA_VERSION)
        record.put("ts", timestamp)
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
            // An unread date still holds the constructed default (turn 1), so only a date actually read becomes a turn number.
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

        // Only Unity Cup computes Spirit Explosion gauges; elsewhere they are an uncomputed default, omitted rather than a measured zero.
        val candidates = buildCandidates(evidence.events, unityCup = scenario == "Unity Cup")
        if (candidates.length() > 0) record.put("candidates", candidates)
        record.put("selected", buildSelected(evidence.events))

        buildRaceEligibility(evidence.events)?.let { record.put("raceEligibility", it) }
        val items = buildItems(evidence.events)
        if (items.length() > 0) record.put("items", items)
        val notes = evidence.events.filterIsInstance<DecisionTracer.DecisionEvent.Note>().map { it.message }
        if (notes.isNotEmpty()) record.put("notes", JSONArray().apply { notes.forEach { put(it) } })

        enteredRace?.let { record.put("enteredRace", buildEnteredRace(it)) }

        return record
    }

    private fun buildEnteredRace(entered: EnteredRace): JSONObject =
        JSONObject().apply {
            put("turnNumber", entered.turnNumber)
            put("resolution", entered.resolution.wire)
            put("path", entered.path.wire)
            entered.name?.takeIf { it.isNotBlank() }?.let { put("name", it) }
            entered.matchCount?.let { put("matchCount", it) }
        }

    /** Turn-open snapshot, not live state: the action already executed, so a re-read would record the consequence as the input. */
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

    /** Read flags are not confidence scores. A false flag means a carried-over or default value, not a fresh observation. */
    private fun buildObservation(date: GameDate?, state: DecisionTracer.StateSnapshot?): JSONObject =
        JSONObject().apply {
            put("turnObserved", date?.dayObserved ?: false)
            put("statsObserved", state?.statsObserved ?: false)
            put("skillPointsObserved", state?.skillPointsObserved ?: false)
            put("aptitudesObserved", state?.aptitudesObserved ?: false)
        }

    /** The honest subset the decision code hands over, not an exhaustive action space: the cascade names only what it ruled out. */
    private fun buildCandidates(events: List<DecisionTracer.DecisionEvent>, unityCup: Boolean): JSONArray {
        val candidates = JSONArray()
        // Only the FINAL training contest is authoritative: Trackblazer Irregular Training calls recommendTraining twice in a
        // turn (pre-screen, then the executed fast path), and rendering both would emit two selected:true candidates. Matches
        // buildSelected's lastOrNull().
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
                                event.pickedEvidence?.let { putCandidateEvidence(this, it, unityCup) }
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
                                // Absent for a hard-excluded training: the tracer drops the -Infinity sentinel.
                                runnerUp.score?.takeIf { it.isFinite() }?.let { put("score", it) }
                                runnerUp.failureChance?.let { put("failChance", it) }
                                runnerUp.statGains?.let { put("gains", statGains(it)) }
                                runnerUp.evidence?.let { putCandidateEvidence(this, it, unityCup) }
                            },
                        )
                    }
                }

                else -> Unit
            }
        }
        return candidates
    }

    /** A `recovery` block means the turn abandoned the cascade's pick, so reading only `action` would be wrong about what ran. */
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

    private fun buildRaceEligibility(events: List<DecisionTracer.DecisionEvent>): JSONObject? {
        val eligibility = events.filterIsInstance<DecisionTracer.DecisionEvent.RaceEligibility>().lastOrNull() ?: return null
        return JSONObject().apply {
            put("eligible", eligibility.eligible)
            put("reason", eligibility.reason)
        }
    }

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

    private fun statGains(gains: Map<StatName, Int>): JSONObject =
        JSONObject().apply {
            STAT_KEYS.forEach { (stat, key) -> gains[stat]?.let { put(key, it) } }
        }

    /** `numRainbow`/`numSkillHints` always written (a real zero); gauge counts only when [unityCup]; bars and gains only when non-empty. */
    private fun putCandidateEvidence(target: JSONObject, evidence: DecisionTracer.TrainingCandidateEvidence, unityCup: Boolean) {
        target.put("numRainbow", evidence.numRainbow)
        target.put("numSkillHints", evidence.numSkillHints)
        evidence.trainingLevel?.let { target.put("trainingLevel", it) }
        if (unityCup) {
            target.put("numSpiritGaugesCanFill", evidence.numSpiritGaugesCanFill)
            target.put("numSpiritGaugesReadyToBurst", evidence.numSpiritGaugesReadyToBurst)
        }
        if (evidence.relationshipBars.isNotEmpty()) {
            target.put("relationshipBars", buildRelationshipBars(evidence.relationshipBars))
        }
        if (evidence.performanceGains.isNotEmpty()) {
            target.put(
                "performanceGains",
                JSONObject().apply { evidence.performanceGains.forEach { (type, amount) -> put(type, amount) } },
            )
        }
    }

    private fun buildRelationshipBars(bars: List<DecisionTracer.RelationshipBarEvidence>): JSONArray =
        JSONArray().apply {
            bars.forEach { bar ->
                put(
                    JSONObject().apply {
                        put("fillPercent", bar.fillPercent)
                        put("filledSegments", bar.filledSegments)
                        put("dominantColor", bar.dominantColor)
                        if (bar.isSupport) put("support", true)
                        bar.trainerName?.takeIf { it.isNotBlank() }?.let { put("trainer", it) }
                    },
                )
            }
        }
}
