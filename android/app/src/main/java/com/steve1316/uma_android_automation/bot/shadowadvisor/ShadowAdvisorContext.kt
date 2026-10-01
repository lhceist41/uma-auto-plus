package com.steve1316.uma_android_automation.bot.shadowadvisor

import org.json.JSONArray
import org.json.JSONObject

/**
 * Live twin of the offline `context.ts` projection. Reads only pre-decision facts, never `selected`,
 * `enteredRace`, `recovery`, outcomes or `candidate.score`; a training contest is complete only with all five facilities.
 */
object ShadowAdvisorContext {
    /** Returns null when the trace carries no join seq; a null [serializedState] yields all-null state facts. */
    fun buildContextFromRecords(serializedTrace: String, serializedState: String?): AdvisorDecisionContext? {
        val trace = JSONObject(serializedTrace)
        val seq = intOrNull(trace, "seq") ?: return null

        val traceToken = asString(trace, "careerToken")
        val stateObj = serializedState?.let { JSONObject(it) }
        val stateToken = stateObj?.let { asString(optObject(it, "identity"), "careerToken") }
        val careerToken = traceToken ?: stateToken ?: ""
        // Reject a state row whose identity token is present and mismatched; no turn fallback.
        val careerState = if (stateObj != null && !(stateToken != null && stateToken != careerToken)) stateObj else null

        return AdvisorDecisionContext(
            careerToken = careerToken,
            seq = seq,
            turn = intOrNull(trace, "turn"),
            scenarioType = asString(optObject(careerState, "scenario"), "type"),
            state = projectState(careerState),
            trainingContest = projectTrainingContest(trace),
        )
    }

    private fun projectState(careerState: JSONObject?): AdvisorState {
        if (careerState == null) {
            return AdvisorState(energy = null, mood = null, negativeStatuses = null, stats = null, skillPts = null, raceFlags = null)
        }
        val condition = optObject(careerState, "condition")
        val statsObj = optObject(careerState, "stats")
        val raceObj = optObject(careerState, "race")

        val stats: Map<String, Double>? =
            if (statsObj != null) {
                LinkedHashMap<String, Double>().apply {
                    for (key in ADVISOR_GAIN_KEYS) asFiniteNumber(statsObj, key)?.let { put(key, it) }
                }
            } else {
                null
            }

        val negativeStatuses: List<String>? =
            if (condition != null) {
                val arr = condition.optJSONArray("negativeStatuses")
                if (arr != null) stringList(arr) else emptyList()
            } else {
                null
            }

        return AdvisorState(
            energy = if (condition != null) asFiniteNumber(condition, "energy") else null,
            mood = if (condition != null) asString(condition, "mood") else null,
            negativeStatuses = negativeStatuses,
            stats = stats,
            skillPts = asFiniteNumber(careerState, "skillPts"),
            raceFlags =
                if (raceObj != null) {
                    AdvisorRaceFlags(
                        mandatory = raceObj.opt("mandatory") == true,
                        scheduled = raceObj.opt("scheduled") == true,
                        goalRibbon = raceObj.opt("goalRibbon") == true,
                    )
                } else {
                    null
                },
        )
    }

    private fun projectTrainingContest(trace: JSONObject): AdvisorTrainingContest? {
        val candidates = trace.optJSONArray("candidates") ?: JSONArray()
        val facilities = ArrayList<AdvisorFacilityFact>()
        for (i in 0 until candidates.length()) {
            val c = candidates.optJSONObject(i) ?: continue
            if (asString(c, "type") != "training") continue
            facilities.add(
                AdvisorFacilityFact(
                    id = asString(c, "id") ?: "",
                    gains = projectGains(optObject(c, "gains")),
                    failChance = asFiniteNumber(c, "failChance"),
                ),
            )
        }
        if (facilities.isEmpty()) return null
        // Per-facility gains/failChance nullity is checked in the policy, as offline.
        val distinct = facilities.map { it.id }.filter { it in ADVISOR_FACILITIES }.toSet()
        val complete = ADVISOR_FACILITIES.all { it in distinct } && facilities.size >= 5
        return AdvisorTrainingContest(complete, facilities)
    }

    private fun projectGains(raw: JSONObject?): Map<String, Double>? {
        if (raw == null) return null
        val gains = LinkedHashMap<String, Double>()
        for (key in ADVISOR_GAIN_KEYS) asFiniteNumber(raw, key)?.let { gains[key] = it }
        return gains
    }

    private fun optObject(obj: JSONObject?, key: String): JSONObject? = obj?.optJSONObject(key)

    /** Mirrors context.ts `asFiniteNumber`. */
    private fun asFiniteNumber(obj: JSONObject?, key: String): Double? {
        val v = obj?.opt(key) ?: return null
        if (v !is Number) return null
        val d = v.toDouble()
        return if (d.isFinite()) d else null
    }

    /** Mirrors context.ts `asString`. */
    private fun asString(obj: JSONObject?, key: String): String? {
        val v = obj?.opt(key) ?: return null
        return if (v is String && v.isNotEmpty()) v else null
    }

    private fun intOrNull(obj: JSONObject, key: String): Int? {
        val v = obj.opt(key)
        return if (v is Number) v.toInt() else null
    }

    private fun stringList(arr: JSONArray): List<String> {
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.opt(i)
            if (v is String) out.add(v)
        }
        return out
    }
}
