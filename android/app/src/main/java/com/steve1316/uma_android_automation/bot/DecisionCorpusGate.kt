package com.steve1316.uma_android_automation.bot

/**
 * Effective gate for the factual per-turn decision corpus (`decision_trace` + `career_state`).
 *
 * The dedicated "Record Decision Data" setting lets the lightweight factual corpus record during
 * normal release play. A debug build and Debug Mode continue to enable the full heavy diagnostic
 * bundle (the multi-line human Decision Report, fixture capture, the shadow_advisor stream); those
 * stay gated on [factualCorpusEnabled]'s `debugDiagnostics` term alone, so turning the corpus on
 * never pulls the heavy diagnostics with it.
 *
 * Pure boolean logic, split out from [Campaign] so the four-cell gate matrix can be pinned by a unit
 * test without a live Game.
 */
object DecisionCorpusGate {
    /**
     * Whether the factual per-turn corpus records this run. True when the operator opted into
     * recording OR any debug diagnostics are active (a debug build or Debug Mode), so historical
     * Debug Mode behavior is preserved as a superset. Both factual streams read this one gate, so
     * `decision_trace` and `career_state` always record together and stay joinable by seq.
     *
     * @param recordDecisionData The dedicated Record Decision Data setting's value.
     * @param debugDiagnostics `BuildConfig.DEBUG || game.debugMode`.
     */
    fun factualCorpusEnabled(recordDecisionData: Boolean, debugDiagnostics: Boolean): Boolean = recordDecisionData || debugDiagnostics

    /**
     * Whether the heavy multi-line human Decision Report block is written. Strictly the debug gate:
     * the dedicated corpus setting records the machine-readable trace with the report suppressed.
     *
     * @param debugDiagnostics `BuildConfig.DEBUG || game.debugMode`.
     */
    fun humanReportEnabled(debugDiagnostics: Boolean): Boolean = debugDiagnostics
}
