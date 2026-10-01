package com.steve1316.uma_android_automation.bot.shadowadvisor

import android.content.Context
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.utils.OutcomeCorpus
import org.json.JSONObject

/**
 * Live Shadow Advisor invocation point, one per [com.steve1316.uma_android_automation.bot.Campaign], created only under
 * the decision tracer's debug/telemetry gate. Runs after the decision_trace record is appended, from the immutable
 * serialized decision_trace and career_state strings.
 * Never influences gameplay: it only reads finished facts and appends its own stream. Everything sits inside one
 * try/catch, so any failure leaves the bot unchanged (no rethrow, stop, or retry). One record per (careerToken, seq)
 * and one warning per career; state is per-instance, with no cross-career leakage.
 */
class ShadowAdvisorSink {
    /** Last seq an advisor record was appended for, so a reopened-turn retry with the same seq is dropped. Per career. */
    internal var lastEmittedSeq: Int? = null
    private var warnedOnFailure: Boolean = false
    private var loggedActive: Boolean = false

    /**
     * Appends the shadow record for the just-appended decision_trace. [serializedState] is the career_state retained at
     * that append, used only when [retainedStateSeq] matches [traceSeq].
     */
    fun onDecisionTraceAppended(context: Context, serializedTrace: String, traceSeq: Int?, serializedState: String?, retainedStateSeq: Int?) {
        try {
            val record = evaluate(serializedTrace, traceSeq, serializedState, retainedStateSeq) ?: return
            OutcomeCorpus.append(context, record, OutcomeCorpus.SHADOW_ADVISOR_PATH, MAX_FILE_BYTES)
            lastEmittedSeq = traceSeq

            if (!loggedActive) {
                loggedActive = true
                MessageLog.i(TAG, "[SHADOW_ADVISOR] live shadow telemetry active (policy ${DEFAULT_SHADOW_POLICY.policyId} v${DEFAULT_SHADOW_POLICY.advisorVersion}, observational only)")
            }
        } catch (e: Exception) {
            // Observability must never surface as a run failure. One diagnosable line per career instead of per turn.
            if (!warnedOnFailure) {
                warnedOnFailure = true
                MessageLog.w(TAG, "[SHADOW_ADVISOR] failed to record the shadow recommendation this career (further failures are not repeated): $e")
            }
        }
    }

    /**
     * Pure core: the record to append, or null when the turn must be skipped (no join seq, a duplicate of the last
     * emitted seq, or a trace that projects to no context). The retained career_state is paired only on an exact seq
     * match; otherwise it is dropped and the policy reports insufficient evidence. Throws only on unparseable JSON,
     * which the caller isolates.
     */
    internal fun evaluate(serializedTrace: String, traceSeq: Int?, serializedState: String?, retainedStateSeq: Int?): JSONObject? {
        if (traceSeq == null) return null
        if (traceSeq == lastEmittedSeq) return null

        val stateForContext = if (retainedStateSeq != null && retainedStateSeq == traceSeq) serializedState else null
        val context = ShadowAdvisorContext.buildContextFromRecords(serializedTrace, stateForContext) ?: return null
        val recommendation = ShadowAdvisorPolicy.recommend(context)
        return ShadowAdvisorRecord.build(recommendation, context.scenarioType, System.currentTimeMillis())
    }

    companion object {
        private const val TAG: String = "ShadowAdvisorSink"

        /** Per-file byte cap, matching the decision_trace / career_state streams (32 MiB, hard ceiling not rotation). */
        const val MAX_FILE_BYTES: Long = 32L * 1024 * 1024
    }
}
