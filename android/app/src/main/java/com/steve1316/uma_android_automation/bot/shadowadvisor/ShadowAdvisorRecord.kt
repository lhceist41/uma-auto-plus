package com.steve1316.uma_android_automation.bot.shadowadvisor

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes a [ShadowRecommendation] into the append-only `outcomes/shadow_advisor.jsonl` record (schema v1). It
 * duplicates NONE of the committed bot action, selected training, trainingSource, candidate.score, enteredRace, or
 * final outcome: the offline comparison recovers those by joining the decision_trace on (careerToken, seq).
 * Optional fields are omitted, never null-filled. Numeric leaves are stored as doubles; org.json (both the Android
 * runtime and the JVM test dependency) renders an integer-valued double without a trailing `.0` and keeps a
 * half-step decimal, matching the TypeScript authority's JS number formatting.
 */
object ShadowAdvisorRecord {
    const val SCHEMA: String = "shadow_advisor"
    const val SCHEMA_VERSION: Int = 1
    const val SOURCE: String = "live_shadow"

    fun build(recommendation: ShadowRecommendation, scenarioType: String?, timestamp: Long): JSONObject {
        val record = JSONObject()
        record.put("type", SCHEMA)
        record.put("v", SCHEMA_VERSION)
        record.put("ts", timestamp)
        record.put("careerToken", recommendation.careerToken)
        record.put("seq", recommendation.seq)
        recommendation.turn?.let { record.put("turn", it) }
        scenarioType?.takeIf { it.isNotEmpty() }?.let { record.put("scenarioType", it) }
        record.put("advisorVersion", recommendation.advisorVersion)
        record.put("policyId", recommendation.policyId)
        record.put("source", SOURCE)
        record.put("status", recommendation.status.wire)

        recommendation.recommendedAction?.let { action ->
            val rec = JSONObject()
            rec.put("action", action.wire)
            recommendation.recommendedTrainingType?.let { rec.put("trainingType", it) }
            record.put("recommended", rec)
        }

        recommendation.scoreMargin?.let { margin ->
            record.put(
                "scoreMargin",
                JSONObject().apply {
                    put("value", margin.value)
                    put("over", margin.over)
                },
            )
        }

        record.put(
            "reasons",
            JSONArray().apply {
                recommendation.reasons.forEach {
                    put(
                        JSONObject().apply {
                            put("code", it.code.wire)
                            put("detail", it.detail)
                        },
                    )
                }
            },
        )
        record.put("limitations", JSONArray().apply { recommendation.limitations.forEach { put(it) } })

        recommendation.scoreBreakdown?.let { breakdown ->
            val perStat = JSONObject()
            breakdown.perStat.forEach { (key, value) -> perStat.put(key, value) }
            record.put(
                "scoreBreakdown",
                JSONObject().apply {
                    put("weightedGain", breakdown.weightedGain)
                    put("failurePenalty", breakdown.failurePenalty)
                    put("total", breakdown.total)
                    put("perStat", perStat)
                },
            )
        }

        return record
    }
}
