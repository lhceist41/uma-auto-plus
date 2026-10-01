package com.steve1316.uma_android_automation.bot.shadowadvisor

import org.json.JSONArray
import org.json.JSONObject

/** Append-only `outcomes/shadow_advisor.jsonl` record; omits fields the decision_trace join already carries (by careerToken, seq). Optional fields are omitted, never null-filled. */
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
