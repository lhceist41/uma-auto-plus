package com.steve1316.uma_android_automation

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** One verified launch choice survives settings changes through single-handler dispatch. */
object DebugTestGate {
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
            "debugMode_startHostBorrowSwipeTest",
            "debugMode_startHostLegacySwipeTest",
            "debugMode_startSupportDeckRehearsalTest",
            "debugMode_startSmartBorrowRehearsalTest",
            "debugMode_startSmartBorrowLocateTest",
            "debugMode_startBorrowRemoveProbeTest",
            "debugMode_startSmartBorrowSelectRollbackTest",
            "debugMode_startBuildAwareLaunchGateTest",
            "debugMode_startBorrowPoolScanTest",
            "debugMode_startRainbowDetectionTest",
            "debugMode_startVeteranRosterReadTest",
            "debugMode_startVeteranRosterScanTest",
            "debugMode_startVeteranInspirationReadTest",
            "debugMode_startVeteranInspirationScanTest",
            "debugMode_startVeteranProtectionScanTest",
        )

    /** Enumerates stored flags for the navigator's dry-run conflict check, not launch authorization. */
    fun requested(isSet: (String) -> Boolean): List<String> = ALL_KEYS.filter(isSet)

    class Selection internal constructor(
        val key: String?,
        val scenario: String,
        val rosterLimit: Int,
        val rosterEvidence: Boolean,
        val inspirationLimit: Int,
        val inspirationStartIndex: Int,
    ) {
        private val used = AtomicBoolean(false)

        internal fun cancel() {
            used.set(true)
        }

        fun dispatch(handlers: Map<String, () -> Unit>): Boolean {
            val selected = key ?: return false
            val handler = handlers[selected] ?: return false
            check(used.compareAndSet(false, true)) { "Diagnostic request already consumed" }
            handler()
            return true
        }
    }

    private var expected: Pair<String, Selection>? = null
    private var started = false
    private var active: Selection? = null

    @Synchronized
    fun prepare(json: String): String {
        check(expected == null && active == null) { "A launch is already pending or active" }
        val data = JSONObject(json)
        require(data.has("key")) { "Launch mode missing" }
        require(data.isNull("key") || data.get("key") is String) { "Malformed launch mode" }
        val key = if (data.isNull("key")) null else data.getString("key")
        require(key == null || key in ALL_KEYS) { "Unsupported diagnostic" }
        require(data.get("scenario") is String && data.get("veteranRosterScanEvidence") is Boolean) { "Malformed launch settings" }
        for (name in listOf("veteranRosterScanLimit", "veteranInspirationScanLimit", "veteranInspirationScanStartIndex")) {
            require(data.get(name) is Int) { "Malformed diagnostic parameter" }
        }
        val selection = Selection(key, data.getString("scenario"), data.getInt("veteranRosterScanLimit"), data.getBoolean("veteranRosterScanEvidence"),
            data.getInt("veteranInspirationScanLimit"), data.getInt("veteranInspirationScanStartIndex"))
        require(selection.rosterLimit >= 0 && selection.inspirationLimit >= 0 && selection.inspirationStartIndex >= 0) { "Invalid diagnostic parameters" }
        val token = java.util.UUID.randomUUID().toString()
        expected = token to selection
        started = false
        return token
    }

    @Synchronized
    fun start(token: String) {
        check(expected?.first == token && !started) { "Missing or stale launch request" }
        started = true
    }

    @Synchronized
    fun cancel() {
        expected?.second?.cancel()
        active?.cancel()
        expected = null
        started = false
    }

    /** Revokes only this still-pending request; false once the overlay consumed it or a newer request replaced it. */
    @Synchronized
    fun revoke(token: String): Boolean {
        val pending = expected ?: return false
        if (pending.first != token) return false
        pending.second.cancel()
        expected = null
        started = false
        return true
    }

    /** Drops a pending request whose acknowledging JS instance is gone; a dispatched session is untouched. */
    @Synchronized
    fun revokePending() {
        expected?.second?.cancel()
        expected = null
        started = false
    }

    @Synchronized
    fun finish() {
        active?.cancel()
        active = null
    }

    @Synchronized
    fun consume(readSnapshot: () -> Map<String, String>): Selection {
        val selection = expected?.second
        val wasStarted = started
        expected = null
        started = false
        check(selection != null && wasStarted) { "Start again from UMA Auto+ with an explicit launch choice" }
        active = selection
        val rows = readSnapshot()
        val armed = ALL_KEYS.filter { key ->
            val value = rows["debug/$key"]
            require(value == "true" || value == "false") { "Missing or malformed diagnostic arm" }
            value == "true"
        }
        require(armed == listOfNotNull(selection.key)) { "Diagnostic arms do not match the requested handler" }
        require(rows.none { (key, value) -> key.startsWith("debug/debugMode_start") && key.removePrefix("debug/") !in ALL_KEYS && value != "false" }) { "Unsupported diagnostic arm" }
        val scenario = rows["general/scenario"]
        require(scenario == selection.scenario) { "Scenario changed after launch verification" }
        val campaigns = setOf("URA Finale", "Unity Cup", "Trackblazer", com.steve1316.uma_android_automation.bot.GrandConcertScenario.KEY)
        if (selection.key != null) {
            require(com.steve1316.uma_android_automation.bot.GrandConcertScenario.normalizeScenarioKey(selection.scenario) in campaigns) { "Diagnostic unsupported by this campaign" }
            require(!selection.key.startsWith("debugMode_startTrackblazer") || scenario == "Trackblazer") { "Trackblazer diagnostic in another campaign" }
        }
        fun integer(key: String, expected: Int) {
            val value = rows["debug/$key"]
            require(value != null && value.toIntOrNull() == expected && value == expected.toString()) { "Missing, malformed or changed diagnostic parameter" }
        }
        integer("veteranRosterScanLimit", selection.rosterLimit)
        integer("veteranInspirationScanLimit", selection.inspirationLimit)
        integer("veteranInspirationScanStartIndex", selection.inspirationStartIndex)
        require(rows["debug/veteranRosterScanEvidence"] == selection.rosterEvidence.toString()) { "Missing, malformed or changed evidence setting" }
        return selection
    }
}
