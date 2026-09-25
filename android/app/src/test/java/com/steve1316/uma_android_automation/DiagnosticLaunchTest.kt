package com.steve1316.uma_android_automation

import android.content.Context
import android.content.ContextWrapper
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.PromiseImpl
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.DialogHandler
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.LaunchIdentityGate
import com.steve1316.uma_android_automation.bot.Racing
import com.steve1316.uma_android_automation.bot.SkillPlan
import com.steve1316.uma_android_automation.bot.Training
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CyclicBarrier

class DiagnosticLaunchTest {
    private val roster = "debugMode_startVeteranRosterScanTest"
    private val wrong = "debugMode_startSkillListBuyTest"

    @AfterEach
    fun clear() {
        DebugTestGate.cancel()
        DebugTestGate.finish()
        LaunchIdentityGate.clear()
    }

    private fun payload(key: String? = roster, scenario: String = "URA Finale") = JSONObject()
        .put("key", key ?: JSONObject.NULL).put("scenario", scenario)
        .put("veteranRosterScanLimit", 5).put("veteranRosterScanEvidence", true)
        .put("veteranInspirationScanLimit", 3).put("veteranInspirationScanStartIndex", 2).toString()

    private fun rows(key: String? = roster, scenario: String = "URA Finale") = DebugTestGate.ALL_KEYS.associate { "debug/$it" to (it == key).toString() }.toMutableMap().apply {
        put("general/scenario", scenario)
        put("general/settingsRevision", "10")
        put("debug/veteranRosterScanLimit", "5")
        put("debug/veteranRosterScanEvidence", "true")
        put("debug/veteranInspirationScanLimit", "3")
        put("debug/veteranInspirationScanStartIndex", "2")
    }

    private fun prepare(key: String? = roster, scenario: String = "URA Finale") = DebugTestGate.prepare(payload(key, scenario)).also { DebugTestGate.start(it) }

    private fun revoke(token: String): Boolean {
        var result: Any? = null
        module.revokeDiagnosticLaunch(token, PromiseImpl(object : Callback {
            override fun invoke(vararg args: Any?) { result = args.single() }
        }, null))
        return result as Boolean
    }

    private val unsafeType = Class.forName("sun.misc.Unsafe")
    private val unsafe = unsafeType.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    private fun allocate(type: Class<*>): Any = unsafeType.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type)
    private fun newModule() = StartModule(object : BridgeReactContext(object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }) {
        override fun getApplicationContext(): Context = this
    })
    private val module = newModule()

    private class Effects {
        var chosen = 0
        var wrong = 0
        var navigation = 0
        var queue = 0
        var career = 0
    }

    private fun launch(effects: Effects, reader: () -> Map<String, String>) {
        val selected = module.dispatchDiagnostic(reader) { selection ->
            val campaign = campaign(selection)
            val game = DialogHandler::class.java.getDeclaredField("game").apply { isAccessible = true }.get(campaign) as Game
            Game::class.java.getDeclaredField("task").apply { isAccessible = true }.set(game, campaign)
            assertNotNull(game.runDiagnostic())
            effects.chosen += campaign.rosterCalls
            effects.wrong += campaign.otherCalls
        } ?: return
        if (selected.key != null) return
        effects.navigation++
        effects.queue++
        effects.career++
    }

    @Test
    fun `revocation before the overlay event rejects an old matching snapshot for diagnostic and normal requests`() {
        for (key in listOf(roster, null)) {
            LaunchIdentityGate.setExpected(10, "verified")
            val token = prepare(key)
            assertTrue(revoke(token))
            val effects = Effects()
            assertThrows(Exception::class.java) { launch(effects) { rows(key) } }
            assertEquals(0, effects.chosen + effects.wrong + effects.navigation + effects.queue + effects.career)
            LaunchIdentityGate.clear()
            DebugTestGate.finish()
        }
    }

    @Test
    fun `revocation after overlay consumption reports false and leaves the dispatched handler alone`() {
        val token = prepare()
        val effects = Effects()
        launch(effects) { rows() }
        assertFalse(revoke(token))
        assertEquals(1, effects.chosen)
        assertEquals(0, effects.wrong + effects.navigation + effects.queue + effects.career)
    }

    @Test
    fun `stale or duplicate revocation cannot revoke a newer request`() {
        val old = prepare()
        assertTrue(revoke(old))
        assertFalse(revoke(old))
        val replaced = prepare(wrong)
        DebugTestGate.cancel()
        prepare()
        assertFalse(revoke(old))
        assertFalse(revoke(replaced))
        val effects = Effects()
        launch(effects) { rows() }
        assertEquals(1, effects.chosen)
        assertEquals(0, effects.wrong + effects.navigation + effects.queue + effects.career)
    }

    @Test
    fun `a request acknowledged by a previous JS instance cannot run after the module is recreated`() {
        prepare()
        newModule()
        val effects = Effects()
        assertThrows(Exception::class.java) { launch(effects) { rows() } }
        assertEquals(0, effects.chosen + effects.wrong + effects.navigation + effects.queue + effects.career)
    }

    @Test
    fun `revocation racing the overlay event runs the chosen handler exactly when revocation lost`() {
        repeat(1000) {
            val token = prepare()
            val effects = Effects()
            val barrier = CyclicBarrier(2)
            var revoked = false
            val revoker = Thread { barrier.await(); revoked = revoke(token) }
            val overlay = Thread { barrier.await(); runCatching { launch(effects) { rows() } } }
            revoker.start()
            overlay.start()
            revoker.join()
            overlay.join()
            assertEquals(if (revoked) 0 else 1, effects.chosen)
            assertEquals(0, effects.wrong + effects.navigation + effects.queue + effects.career)
            DebugTestGate.finish()
        }
    }

    @Test
    fun `revision mismatch rejects the actual dispatch chain including other diagnostics and normal Start`() {
        for (key in listOf(roster, "debugMode_startRainbowDetectionTest", null)) {
            for (revision in listOf("9", null, "malformed", "2147483648")) {
                LaunchIdentityGate.setExpected(10, "verified")
                prepare(key)
                val snapshot = rows(key).apply {
                    if (revision == null) remove("general/settingsRevision") else put("general/settingsRevision", revision)
                }
                val effects = Effects()
                launch(effects) { snapshot }
                assertEquals(0, effects.chosen + effects.wrong + effects.navigation + effects.queue + effects.career)
                assertNull(LaunchIdentityGate.current)
                assertTrue(LaunchIdentityGate.isBlockedAfterMismatch())
                DebugTestGate.finish()
            }
        }
    }

    @Test
    fun `valid revision dispatches once and consumes identity for every launch kind`() {
        for (key in listOf(roster, "debugMode_startRainbowDetectionTest", null)) {
            LaunchIdentityGate.setExpected(10, "verified")
            prepare(key)
            val effects = Effects()
            launch(effects) { rows(key) }
            assertEquals(if (key == null) 0 else 1, effects.chosen + effects.wrong)
            assertEquals(if (key == null) 1 else 0, effects.navigation)
            assertEquals(if (key == null) 1 else 0, effects.queue)
            assertEquals(if (key == null) 1 else 0, effects.career)
            assertNull(LaunchIdentityGate.current)
            assertFalse(LaunchIdentityGate.isBlockedAfterMismatch())
            assertThrows(Exception::class.java) { launch(effects) { rows(key) } }
            DebugTestGate.finish()
        }
    }

    @Test
    fun `a mismatch stays blocked until a fresh verified identity rearms dispatch`() {
        LaunchIdentityGate.setExpected(10, "verified")
        prepare()
        val effects = Effects()
        launch(effects) { rows().apply { put("general/settingsRevision", "9") } }
        DebugTestGate.finish()
        prepare()
        launch(effects) { rows() }
        assertEquals(0, effects.chosen + effects.wrong + effects.navigation + effects.queue + effects.career)
        DebugTestGate.finish()
        LaunchIdentityGate.setExpected(10, "fresh")
        prepare()
        launch(effects) { rows() }
        assertEquals(1, effects.chosen)
    }

    @Test
    fun `snapshot read failure consumes the request without any handler or fallback`() {
        LaunchIdentityGate.setExpected(10, "verified")
        prepare()
        val effects = Effects()
        assertThrows(IllegalStateException::class.java) { launch(effects) { error("SQLite read failed") } }
        assertThrows(IllegalStateException::class.java) { launch(effects) { rows() } }
        assertEquals(0, effects.chosen + effects.wrong + effects.navigation + effects.queue + effects.career)
    }

    @Test
    fun `unblocked normal continuation retains the existing default revision and single use gate semantics`() {
        LaunchIdentityGate.setExpected(0, "default")
        prepare(null)
        val effects = Effects()
        launch(effects) { rows(null).apply { remove("general/settingsRevision") } }
        assertNull(LaunchIdentityGate.current)
        assertEquals(1, effects.navigation)
        DebugTestGate.finish()
        prepare(null)
        launch(effects) { rows(null) }
        assertEquals(2, effects.navigation)
        assertFalse(LaunchIdentityGate.isBlockedAfterMismatch())
    }

    @Test
    fun `actual outer Game and Campaign chain chooses once with zero consequential fallback`() {
        val effects = Effects()
        prepare()
        val snapshot = rows()
        launch(effects) { snapshot }
        assertEquals(1, effects.chosen)
        assertEquals(0, effects.wrong + effects.navigation + effects.queue + effects.career)
        assertThrows(Exception::class.java) { launch(effects) { rows(null) } }
        assertEquals(1, effects.chosen)
        assertEquals(0, effects.wrong + effects.navigation + effects.queue + effects.career)
    }

    @Test
    fun `actual outer dispatch rejects defaults stale writers and failed reads before any effect`() {
        val cases = listOf<() -> Map<String, String>>(
            { emptyMap() }, { rows(null) }, { rows(wrong) }, { error("read failed") },
            { rows().apply { remove("debug/$roster") } },
            { rows().apply { put("debug/$roster", "1") } },
            { rows().apply { put("debug/$wrong", "true") } },
            { rows().apply { put("debug/veteranRosterScanLimit", "6") } },
            { rows().apply { put("debug/veteranInspirationScanLimit", "4") } },
            { rows().apply { put("debug/veteranInspirationScanStartIndex", "0") } },
            { rows().apply { put("debug/veteranRosterScanEvidence", "false") } },
        )
        for (reader in cases) {
            prepare()
            val effects = Effects()
            assertThrows(Exception::class.java) { launch(effects, reader) }
            assertEquals(0, effects.chosen + effects.wrong + effects.navigation + effects.queue + effects.career)
            DebugTestGate.finish()
        }
    }

    @Test
    fun `explicit normal choice reaches normal continuation across scenarios`() {
        for (scenario in listOf("URA Finale", "Unity Cup", "Trackblazer", "Grand Live", "Daily Races", "Team Trials")) {
            prepare(null, scenario)
            val effects = Effects()
            launch(effects) { rows(null, scenario) }
            assertEquals(0, effects.chosen + effects.wrong)
            assertEquals(1, effects.navigation)
            assertEquals(1, effects.queue)
            assertEquals(1, effects.career)
            DebugTestGate.finish()
        }
    }

    @Test
    fun `projection refusal and recreation cannot replay an acknowledged request`() {
        prepare()
        module.onActivityResult(allocate(android.app.Activity::class.java) as android.app.Activity, 100, android.app.Activity.RESULT_CANCELED, null)
        val effects = Effects()
        assertThrows(Exception::class.java) { launch(effects) { rows() } }
        assertEquals(0, effects.chosen + effects.wrong + effects.navigation + effects.queue + effects.career)
    }

    @Test
    fun `Game cannot fall through when requested handler is unavailable`() {
        prepare("debugMode_startTrackblazerBuyItemsTest", "Trackblazer")
        val selection = DebugTestGate.consume { rows("debugMode_startTrackblazerBuyItemsTest", "Trackblazer") }
        val campaign = campaign(selection)
        val game = DialogHandler::class.java.getDeclaredField("game").apply { isAccessible = true }.get(campaign) as Game
        Game::class.java.getDeclaredField("task").apply { isAccessible = true }.set(game, campaign)
        assertThrows(IllegalStateException::class.java) { game.runDiagnostic() }
        assertEquals(0, campaign.rosterCalls + campaign.otherCalls)
    }

    @Test
    fun `request JSON rejects coercible malformed types`() {
        for ((name, value) in listOf("key" to 3, "scenario" to 5, "veteranRosterScanLimit" to "5", "veteranRosterScanEvidence" to "true", "veteranInspirationScanLimit" to 3.5)) {
            assertThrows(Exception::class.java) { DebugTestGate.prepare(JSONObject(payload()).put(name, value).toString()) }
        }
    }

    @Test
    fun `all supported scenarios retain explicit normal Start`() {
        for (scenario in listOf("URA Finale", "Unity Cup", "Trackblazer", "Grand Live", "Daily Races", "Team Trials")) {
            prepare(null, scenario)
            assertNull(DebugTestGate.consume { rows(null, scenario) }.key)
            DebugTestGate.finish()
        }
    }

    @Test
    fun `missing malformed false substituted and multiple arms reject before dispatch`() {
        for (corrupt in listOf<(MutableMap<String, String>) -> Unit>(
            { it.remove("debug/$roster") }, { it["debug/$roster"] = "junk" }, { it["debug/$roster"] = "false" },
            { it["debug/$roster"] = "false"; it["debug/$wrong"] = "true" }, { it["debug/$wrong"] = "true" },
            { it["debug/debugMode_startUnknownTest"] = "true" },
            { it.remove("debug/veteranRosterScanLimit") }, { it["debug/veteranRosterScanLimit"] = "0" },
            { it["debug/veteranRosterScanEvidence"] = "yes" }, { it["general/scenario"] = "Daily Races" },
        )) {
            prepare()
            val snapshot = rows().apply(corrupt)
            var dispatches = 0
            assertThrows(Exception::class.java) {
                DebugTestGate.consume { snapshot }.dispatch(mapOf(roster to { dispatches++ }, wrong to { dispatches += 100 }))
            }
            assertEquals(0, dispatches)
            assertThrows(Exception::class.java) { DebugTestGate.consume { rows() } }
            DebugTestGate.finish()
        }
    }

    @Test
    fun `read failure consumes request and cannot fall back on retry`() {
        prepare()
        assertThrows(Exception::class.java) { DebugTestGate.consume { error("SQLite read failed") } }
        assertThrows(Exception::class.java) { DebugTestGate.consume { rows(null) } }
    }

    @Test
    fun `unsupported campaigns reject a diagnostic before handler dispatch`() {
        for (scenario in listOf("Daily Races", "Team Trials", "unknown")) {
            prepare(roster, scenario)
            assertThrows(Exception::class.java) { DebugTestGate.consume { rows(roster, scenario) } }
            DebugTestGate.finish()
        }
    }

    @Test
    fun `restore with same revision and different diagnostic arms rejects`() {
        prepare()
        assertThrows(Exception::class.java) { DebugTestGate.consume { rows(null) } }
    }

    @Test
    fun `cancel service recreation and missing request never become normal Start`() {
        prepare()
        DebugTestGate.cancel()
        assertThrows(Exception::class.java) { DebugTestGate.consume { rows(null) } }
        prepare()
        val selection = DebugTestGate.consume { rows() }
        var calls = 0
        selection.dispatch(mapOf(roster to { calls++ }))
        DebugTestGate.finish()
        assertThrows(Exception::class.java) { DebugTestGate.consume { rows() } }
        assertThrows(Exception::class.java) { selection.dispatch(mapOf(roster to { calls++ })) }
        assertEquals(1, calls)
    }

    @Test
    fun `Stop after snapshot validation revokes dispatch before the handler starts`() {
        prepare()
        val selection = DebugTestGate.consume { rows() }
        DebugTestGate.cancel()
        val campaign = campaign(selection)
        assertThrows(IllegalStateException::class.java) { campaign.startTests() }
        assertEquals(0, campaign.rosterCalls + campaign.otherCalls)
    }

    @Test
    fun `overlapping starts cannot replace pending or active selection`() {
        val token = DebugTestGate.prepare(payload())
        assertThrows(Exception::class.java) { DebugTestGate.prepare(payload(wrong)) }
        DebugTestGate.start(token)
        assertThrows(Exception::class.java) { DebugTestGate.start(token) }
        val selected = DebugTestGate.consume { rows() }
        assertEquals(roster, selected.key)
        assertThrows(Exception::class.java) { DebugTestGate.prepare(payload(wrong)) }
    }

    @Test
    fun `Campaign executes the frozen selected handler once after flags and parameters change`() {
        prepare()
        val snapshot = rows()
        val selected = DebugTestGate.consume { snapshot }
        snapshot["debug/$roster"] = "false"
        snapshot["debug/$wrong"] = "true"
        snapshot["debug/veteranRosterScanLimit"] = "0"
        val campaign = campaign(selected)
        assertTrue(campaign.startTests())
        assertEquals(1, campaign.rosterCalls)
        assertEquals(0, campaign.otherCalls)
        assertEquals(5, campaign.observedLimit)
        assertThrows(Exception::class.java) { campaign.startTests() }
        assertEquals(1, campaign.rosterCalls)
    }

    @Test
    fun `Campaign normal path ignores later persisted diagnostic flags`() {
        val campaign = campaign(null)
        assertFalse(campaign.startTests())
        assertEquals(0, campaign.rosterCalls + campaign.otherCalls)
    }

    @Test
    fun `every ParentLab choice stays exclusive across supported campaigns`() {
        for (scenario in listOf("URA Finale", "Unity Cup", "Trackblazer", "Grand Live")) {
            for (key in DebugTestGate.ALL_KEYS.filter { it.startsWith("debugMode_startVeteran") }) {
                prepare(key, scenario)
                val campaign = campaign(DebugTestGate.consume { rows(key, scenario) })
                assertTrue(campaign.startTests())
                assertEquals(key, campaign.observedKey)
                assertEquals(1, campaign.rosterCalls + campaign.otherCalls)
                assertThrows(Exception::class.java) { campaign.startTests() }
                assertEquals(1, campaign.rosterCalls + campaign.otherCalls)
                DebugTestGate.finish()
            }
        }
    }

    @Test
    fun `a failing handler still consumes the choice without a second handler`() {
        prepare()
        val selected = DebugTestGate.consume { rows() }
        var wrongCalls = 0
        assertThrows(IllegalStateException::class.java) {
            selected.dispatch(mapOf(roster to { error("handler failed") }, wrong to { wrongCalls++ }))
        }
        assertThrows(IllegalStateException::class.java) { selected.dispatch(mapOf(roster to { wrongCalls++ })) }
        assertEquals(0, wrongCalls)
    }

    private class RecordingCampaign(game: Game) : Campaign(game) {
        var rosterCalls = 0
        var otherCalls = 0
        var observedLimit = -1
        var observedKey: String? = null
        override fun startRainbowDetectionTest() { otherCalls++; observedKey = "debugMode_startRainbowDetectionTest" }
        override fun startVeteranRosterScanTest() { rosterCalls++; observedLimit = game.diagnosticSelection!!.rosterLimit; observedKey = "debugMode_startVeteranRosterScanTest" }
        override fun startVeteranRosterReadTest() { otherCalls++; observedKey = "debugMode_startVeteranRosterReadTest" }
        override fun startVeteranInspirationReadTest() { otherCalls++; observedKey = "debugMode_startVeteranInspirationReadTest" }
        override fun startVeteranInspirationScanTest() { otherCalls++; observedKey = "debugMode_startVeteranInspirationScanTest" }
        override fun startVeteranProtectionScanTest() { otherCalls++; observedKey = "debugMode_startVeteranProtectionScanTest" }
    }

    // Bypass Android-heavy constructors, retaining the production dispatch and its handler map.
    private fun campaign(selection: DebugTestGate.Selection?): RecordingCampaign {
        val game = allocate(Game::class.java) as Game
        Game::class.java.getDeclaredField("diagnosticSelection").apply { isAccessible = true }.set(game, selection)
        val campaign = allocate(RecordingCampaign::class.java) as RecordingCampaign
        DialogHandler::class.java.getDeclaredField("game").apply { isAccessible = true }.set(campaign, game)
        for ((name, type) in listOf("training" to Training::class.java, "racing" to Racing::class.java, "skillPlan" to SkillPlan::class.java)) {
            Campaign::class.java.getDeclaredField(name).apply { isAccessible = true }.set(campaign, allocate(type))
        }
        return campaign
    }
}
