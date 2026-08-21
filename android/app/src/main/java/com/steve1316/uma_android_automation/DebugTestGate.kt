package com.steve1316.uma_android_automation

/**
 * Canonical registry + pure resolution for the read-only debug diagnostics ("debug tests").
 *
 * Why: a debug test is armed by a Debug Settings toggle and, when set, MUST run instead of normal
 * bot operation. On 2026-08-13 an intended read-only deck-number diagnostic did not arm at runtime
 * (a debug-test toggle that did not survive an app restart), so Campaign.startTests() ran nothing
 * and Game.kt fell through into normal career navigation -- which pressed Start Career and spent TP.
 *
 * This object makes "a diagnostic was requested" a single source of truth the runtime can check
 * independently of which campaign's startTests() fnMap happens to provide a handler. Game.kt logs
 * the armed set at session start (so the operator can confirm on the Home screen, BEFORE any
 * dangerous screen) and, if a diagnostic is requested but startTests() ran none, stops FAIL-CLOSED
 * instead of starting normal navigation. Pure and JVM-testable: the settings reader is injected.
 *
 * [ALL_KEYS] mirrors the Debug Settings `debugTestKeys` UI list (a source-guard test pins the two in
 * sync). It is the union across every campaign's tests, so a test the running campaign does not
 * provide still counts as "requested" and trips the fail-closed rather than being silently ignored.
 */
internal object DebugTestGate {
    /** Every debug-test setting key, mirroring DebugSettings' `debugTestKeys` (kept in sync by test). */
    val ALL_KEYS: List<String> =
        listOf(
            "debugMode_startTemplateMatchingTest",
            "debugMode_startSingleTrainingOCRTest",
            "debugMode_startComprehensiveTrainingOCRTest",
            "debugMode_startRaceListDetectionTest",
            "debugMode_startMainScreenUpdateTest",
            "debugMode_startSkillListBuyTest",
            "debugMode_startScrollBarDetectionTest",
            "debugMode_startTrackblazerRaceSelectionTest",
            "debugMode_startTrackblazerInventorySyncTest",
            "debugMode_startTrackblazerBuyItemsTest",
            "debugMode_startTraineeSelectTest",
            "debugMode_startDeckStatReadTest",
            "debugMode_startDeckNumberReadTest",
            "debugMode_startSupportDeckRehearsalTest",
            "debugMode_startSmartBorrowRehearsalTest",
            "debugMode_startRainbowDetectionTest",
            "debugMode_startVeteranRosterReadTest",
            "debugMode_startVeteranRosterScanTest",
        )

    /** The armed debug-test keys, in [ALL_KEYS] order. Reader injected so this stays unit-testable. */
    fun requested(isSet: (String) -> Boolean): List<String> = ALL_KEYS.filter(isSet)

    /** True when any debug diagnostic is armed. */
    fun anyRequested(isSet: (String) -> Boolean): Boolean = ALL_KEYS.any(isSet)
}
