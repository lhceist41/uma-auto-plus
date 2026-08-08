package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes an immutable [CareerState] into one append-only `career_state` v1 JSON record.
 *
 * This is a **separate durable record type** from `decision_trace`. CareerState captures the
 * pre-decision world facts the engine sees immediately before `decideNextAction()`; DecisionTrace
 * captures the turn-open state plus the decision evidence. They are joined offline by
 * `careerToken + seq`, never by observed turn/date. Keeping them in separate files
 * ([OutcomeCorpus.CAREER_STATE_PATH] vs [OutcomeCorpus.DECISIONS_PATH]) preserves each parser's
 * right to reject the other record type.
 *
 * Honesty rules mirror [CareerState] and [DecisionTrace]:
 * - A group that was never read this career ([CareerState.stats] / [CareerState.skillPoints] /
 *   [CareerState.aptitudes] / [CareerState.race] null) is OMITTED, never filled with a placeholder.
 * - Date components appear only when the date was actually read (`dayObserved`).
 * - Fans are deliberately absent: [CareerState] excludes them because no per-field read flag exists,
 *   so a default fan count could not be labelled observed.
 * - No candidate/score/selection evidence appears here; that stays DecisionTrace-owned.
 *
 * Pure and Context-free: it reads only the supplied immutable [CareerState], seq, and timestamp. It
 * does no OCR, no settings read, no mutable runtime read, no tap, and no file I/O of its own. The
 * append belongs to [Campaign], which passes the record to `OutcomeCorpus.append`. Identical input
 * (excluding the caller-supplied timestamp) yields byte-identical JSON.
 */
object CareerStateSerializer {
    /** Record type discriminator; a `career_state` reader accepts only this. */
    const val SCHEMA: String = "career_state"

    /**
     * Schema version. Bump only on a change a reader cannot absorb by tolerating new fields (renaming
     * or removing a field, or changing an existing field's meaning/units). Additive fields keep v1.
     */
    const val SCHEMA_VERSION: Int = 1

    /**
     * Byte cap for the `career_state` file, matching [DecisionTrace.MAX_FILE_BYTES] (one debug-gated
     * record per decision turn, comparable volume). Past it the writer drops records rather than
     * filling the device; nothing is rotated or deleted.
     */
    const val MAX_FILE_BYTES: Long = 32L * 1024 * 1024

    /** Fixed stat order, matching [DecisionTrace] so every corpus reads stat maps the same way. */
    private val STAT_KEYS: List<Pair<StatName, String>> =
        listOf(StatName.SPEED to "spd", StatName.STAMINA to "sta", StatName.POWER to "pwr", StatName.GUTS to "grt", StatName.WIT to "wit")

    /**
     * Builds the `career_state` record for one logical decision turn.
     *
     * @param careerState The immutable snapshot built at the pre-decision boundary.
     * @param seq This turn's per-career monotonic decision sequence (positive; the join key with `decision_trace`).
     * @param timestamp Wall-clock epoch milliseconds at build time.
     * @return The record, ready to append as one JSONL line.
     */
    fun buildRecord(careerState: CareerState, seq: Int, timestamp: Long): JSONObject {
        val record = JSONObject()
        record.put("type", SCHEMA)
        record.put("v", SCHEMA_VERSION)
        record.put("ts", timestamp)
        record.put("seq", seq)

        record.put("identity", buildIdentity(careerState.identity))

        val date = careerState.date
        // Only a date the bot actually read becomes a turn number; an unobserved date exposes nothing.
        if (date.dayObserved) {
            date.observedTurn?.let { record.put("turn", it) }
            date.year?.let { record.put("year", it.name) }
            date.month?.let { record.put("month", it.name) }
            date.phase?.let { record.put("phase", it.name) }
        }
        record.put("observation", JSONObject().apply { put("turnObserved", date.dayObserved) })

        record.put("condition", buildCondition(careerState.condition))
        careerState.stats?.let { record.put("stats", buildStats(it)) }
        careerState.skillPoints?.let { record.put("skillPts", it) }
        careerState.aptitudes?.let { record.put("aptitudes", buildAptitudes(it)) }
        careerState.race?.let { record.put("race", buildRace(it)) }
        buildScenario(careerState.scenario)?.let { record.put("scenario", it) }
        record.put("provenance", buildProvenance(careerState.provenance))

        return record
    }

    /** Serializes the immutable identity as-is; omits the optional preset/queueRun when unavailable. */
    private fun buildIdentity(identity: CareerIdentity): JSONObject =
        JSONObject().apply {
            put("careerToken", identity.careerToken)
            put("scenario", identity.scenario)
            put("trainee", identity.trainee)
            identity.preset?.takeIf { it.isNotBlank() }?.let { put("preset", it) }
            identity.queueRun?.let { put("queueRun", it) }
            put("fp", identity.configFingerprint)
        }

    /** Condition facts; the status lists are omitted when empty so an absent list reads as "none". */
    private fun buildCondition(condition: ConditionState): JSONObject =
        JSONObject().apply {
            put("energy", condition.energy)
            put("mood", condition.mood.name)
            if (condition.negativeStatuses.isNotEmpty()) {
                put("negativeStatuses", JSONArray().apply { condition.negativeStatuses.forEach { put(it) } })
            }
            if (condition.positiveStatuses.isNotEmpty()) {
                put("positiveStatuses", JSONArray().apply { condition.positiveStatuses.forEach { put(it) } })
            }
        }

    /** The five stats under the canonical short keys, omitting any stat the map does not carry. */
    private fun buildStats(stats: StatState): JSONObject =
        JSONObject().apply {
            STAT_KEYS.forEach { (stat, key) -> stats.stats[stat]?.let { put(key, it) } }
        }

    /** Aptitude groups as {enumName: aptitudeName}, one object per group. */
    private fun buildAptitudes(aptitudes: AptitudeState): JSONObject =
        JSONObject().apply {
            put("surface", JSONObject().apply { aptitudes.surface.forEach { (k, v) -> put(k.name, v.name) } })
            put("distance", JSONObject().apply { aptitudes.distance.forEach { (k, v) -> put(k.name, v.name) } })
            put("style", JSONObject().apply { aptitudes.runningStyle.forEach { (k, v) -> put(k.name, v.name) } })
        }

    /** The three cached race-day flags the decision engine consumes at this boundary. */
    private fun buildRace(race: RaceContext): JSONObject =
        JSONObject().apply {
            put("mandatory", race.mandatoryRaceDay)
            put("scheduled", race.scheduledRaceDay)
            put("goalRibbon", race.goalRibbonDay)
        }

    /**
     * Scenario extension with an explicit discriminator, or null for scenarios that carry no persistent
     * state at this boundary (URA, Unity Cup) - the caller then omits the field entirely, so an absent
     * `scenario` unambiguously means "no extension".
     */
    private fun buildScenario(scenario: ScenarioState?): JSONObject? =
        when (scenario) {
            null -> null
            is TrackblazerState ->
                JSONObject().apply {
                    put("type", "trackblazer")
                    put("shopCoins", scenario.shopCoins)
                    put("inventory", JSONObject().apply { scenario.inventory.forEach { (name, count) -> put(name, count) } })
                    put("consecutiveRaceCount", scenario.consecutiveRaceCount)
                    put("consecutiveRaceCountObserved", scenario.consecutiveRaceCountObserved)
                    put("usedWhistleToday", scenario.usedWhistleToday)
                    put("usedCharmToday", scenario.usedCharmToday)
                    put("usedHammerToday", scenario.usedHammerToday)
                    put("recreationUsedCount", scenario.recreationUsedCount)
                    put("megaphoneTurnCounter", scenario.megaphoneTurnCounter)
                }
            is GrandConcertState ->
                JSONObject().apply {
                    put("type", "grandConcert")
                    put("songsBoughtThisCycle", scenario.songsBoughtThisCycle)
                    put("songsBoughtThisCareer", scenario.songsBoughtThisCareer)
                    put("lastConcertBoundary", scenario.lastConcertBoundary)
                }
        }

    /** Group provenance as stable lowercase enum strings (observed / unread / configured / derived). */
    private fun buildProvenance(provenance: CareerStateProvenance): JSONObject =
        JSONObject().apply {
            put("identityInputs", provenance.identityInputs.name.lowercase())
            put("derivedIdentity", provenance.derivedIdentity.name.lowercase())
            put("date", provenance.date.name.lowercase())
            put("condition", provenance.condition.name.lowercase())
            put("stats", provenance.stats.name.lowercase())
            put("aptitudes", provenance.aptitudes.name.lowercase())
            put("race", provenance.race.name.lowercase())
            put("scenario", provenance.scenario.name.lowercase())
        }
}
