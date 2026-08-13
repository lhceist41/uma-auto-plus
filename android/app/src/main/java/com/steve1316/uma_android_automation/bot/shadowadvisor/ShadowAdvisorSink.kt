package com.steve1316.uma_android_automation.bot.shadowadvisor

import android.content.Context
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.utils.OutcomeCorpus
import org.json.JSONObject

/**
 * The single live Shadow Advisor S3 invocation point. One instance per [com.steve1316.uma_android_automation.bot.Campaign],
 * created only under the same debug/telemetry gate as the decision tracer. It runs AFTER the factual decision_trace
 * record has already been appended, from the immutable serialized decision_trace and career_state strings, and:
 *
 *  - never influences gameplay - it only reads finished facts and appends its own separate stream;
 *  - is wrapped entirely in one try/catch, so any failure (missing/mismatched state, JSON parse, policy exception,
 *    serialization, or writer failure) leaves the bot unchanged and never rethrows, stops, or retries;
 *  - emits at most one record per (careerToken, seq): a reopened-turn retry that reuses the current seq is skipped;
 *  - warns at most once per career on failure and logs once when the stream first activates.
 *
 * The duplicate guard and warning flags are per-career instance state (no static/global cross-career leakage); a new
 * Campaign starts a fresh instance.
 */
class ShadowAdvisorSink {
    /** Last seq an advisor record was appended for, so a reopened-turn retry with the same seq is dropped. Per career. */
    internal var lastEmittedSeq: Int? = null
    private var warnedOnFailure: Boolean = false
    private var loggedActive: Boolean = false

    /**
     * Evaluate S1 from the just-appended decision_trace and its retained same-seq career_state, and append the
     * shadow record. Call this once, immediately after the factual decision_trace append. [traceSeq] and
     * [retainedStateSeq] are the Campaign's own seqs (no reparse needed for the guard); [serializedState] is the
     * exact serialized career_state string retained when it was appended, used only when its seq matches the trace.
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
     * Pure core (no Context, no I/O, no state mutation): the advisor record to append, or null when this turn must be
     * skipped - no join seq, a duplicate of the last emitted seq, or a trace that projects to no context. The retained
     * career_state is paired only when its seq matches the trace seq exactly; otherwise the state is dropped (the
     * policy then reports insufficient evidence). Throws only on unparseable JSON, which the caller isolates.
     */
    internal fun evaluate(serializedTrace: String, traceSeq: Int?, serializedState: String?, retainedStateSeq: Int?): JSONObject? {
        // No join seq -> nothing to shadow (the one trace shape S3 must never record).
        if (traceSeq == null) return null
        // At-most-one per seq: a reopened turn that re-emits the same seq is dropped.
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
