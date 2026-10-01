package com.steve1316.uma_android_automation.bot

/** Effective gate for the factual per-turn corpus. Heavy diagnostics stay gated on `debugDiagnostics` alone. */
object DecisionCorpusGate {
    /** True when the operator opted into recording or debug diagnostics are active, so `decision_trace` and `career_state` always record together. */
    fun factualCorpusEnabled(recordDecisionData: Boolean, debugDiagnostics: Boolean): Boolean = recordDecisionData || debugDiagnostics

    /** Strictly the debug gate: the corpus setting records the machine-readable trace without the report. */
    fun humanReportEnabled(debugDiagnostics: Boolean): Boolean = debugDiagnostics
}
