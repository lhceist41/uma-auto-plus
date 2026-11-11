package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.FixturePng
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Frozen game restart")
class GameRestartTest {
    @Nested
    @DisplayName("when a reopen closes the game")
    inner class KillRoute {
        @Test
        fun `the first attempt only brings the game to the front`() {
            for (sdk in 26..36) assertFalse(reopenClosesGame(attempt = 1, sdk = sdk), "sdk $sdk")
        }

        @Test
        fun `later attempts close it below Android 14 only`() {
            for (attempt in 2..3) {
                for (sdk in 26..33) assertTrue(reopenClosesGame(attempt, sdk), "attempt $attempt sdk $sdk")
                for (sdk in 34..36) assertFalse(reopenClosesGame(attempt, sdk), "attempt $attempt sdk $sdk")
            }
        }
    }

    @Nested
    @DisplayName("did Home leave the game (real MuMu captures)")
    inner class HomeCheck {
        private val grids: Map<String, IntArray> by lazy {
            val text = requireNotNull(javaClass.getResourceAsStream("/fixtures/gamerestart/home_grids.json")).bufferedReader().readText()
            val frames = JSONObject(text).getJSONArray("frames")
            (0 until frames.length()).associate { i ->
                val f = frames.getJSONObject(i)
                val g = f.getJSONArray("grid")
                f.getString("name") to IntArray(g.length()) { g.getInt(it) }
            }
        }

        @Test
        fun `the extracted grids match the Kotlin grid on a shared capture`() {
            val img = requireNotNull(javaClass.getResourceAsStream("/fixtures/titlescreen/title_screen.png")).use { FixturePng.read(it) }
            assertArrayEquals(grids.getValue("repo_title_screen"), homeLumaGrid(img.width, img.height) { x, y -> img.getRGB(x, y) })
        }

        @Test
        fun `Home from the game and from a frozen screen leaves the game`() {
            assertTrue(screenLeftGame(grids.getValue("home_before"), grids.getValue("home_after")))
            assertTrue(screenLeftGame(grids.getValue("frozen_event"), grids.getValue("frozen_event_after_home")))
        }

        @Test
        fun `a frozen game and an animated title do not read as having left the game`() {
            assertFalse(screenLeftGame(grids.getValue("frozen_tap_1"), grids.getValue("frozen_tap_2")))
            assertFalse(screenLeftGame(grids.getValue("title_20s"), grids.getValue("title_40s")))
            assertFalse(screenLeftGame(grids.getValue("title_40s"), grids.getValue("title_60s")))
        }

        @Test
        fun `an animated game that changes enough between frames still does not count as Home landing`() {
            assertEquals(0.837, changedShare(grids.getValue("title_20s"), grids.getValue("title_60s")), 0.001)
            assertTrue(screenLeftGame(grids.getValue("title_20s"), grids.getValue("title_60s")))
            assertFalse(homeLanded(grids.getValue("title_20s"), grids.getValue("title_60s"), grids.getValue("title_40s")))
        }

        @Test
        fun `Home onto a still launcher lands, its rotating banner included`() {
            assertTrue(homeLanded(grids.getValue("home_before"), grids.getValue("home_after"), grids.getValue("home_after_kill")))
            assertTrue(homeLanded(grids.getValue("frozen_event"), grids.getValue("frozen_event_after_home"), grids.getValue("frozen_event_after_kill")))
            assertEquals(0.0, changedShare(grids.getValue("frozen_event_after_home"), grids.getValue("frozen_event_after_kill")), 1e-9)
            assertTrue(changedShare(grids.getValue("home_after"), grids.getValue("home_after_kill")) in 0.05..HOME_STILL_MAX_SHARE)
        }

        @Test
        fun `a grid of another size counts as changed`() {
            assertTrue(screenLeftGame(IntArray(4), IntArray(6)))
            assertEquals(1.0, changedShare(IntArray(0), IntArray(0)), 1e-9)
        }
    }

    private class Script(
        val homePressed: Boolean = true,
        val leavesGame: Boolean = true,
        val keepsAnimating: Boolean = false,
        val front: String? = null,
        val launchDispatches: List<Boolean> = listOf(true, true, true),
        val titleAfterLaunch: Int? = 1,
        val titleAfterPolls: Int = 1,
        /** Android's "isn't responding" dialog is up from this not-responding check on, until Close app is pressed. */
        val anrFromCheck: Int? = null,
    ) {
        var now = 0.0
        val steps = mutableListOf<String>()
        val killTimes = mutableListOf<Double>()
        var anrChecks = 0
            private set
        private var launches = 0
        private var homeAt = -1.0
        private var titleFrom = -1
        private var polls = 0
        var captures = 0
            private set

        fun run(): FrozenGameRestart =
            restartFrozenGame(
                captureGrid = {
                    captures++
                    when {
                        captures == 1 || !leavesGame -> IntArray(576) { 10 }
                        captures >= 3 && keepsAnimating -> IntArray(576) { 120 }
                        else -> IntArray(576) { 200 }
                    }
                },
                pressHome = {
                    homeAt = now
                    steps += "home"
                    homePressed
                },
                frontPackage = { front },
                killGame = {
                    killTimes += now - homeAt
                    steps += "kill"
                },
                launch = { clearTask ->
                    launches++
                    steps += if (clearTask) "launch-fresh" else "launch-plain"
                    if (titleAfterLaunch == launches) titleFrom = polls + titleAfterPolls
                    launchDispatches[launches - 1]
                },
                titleShowing = {
                    polls++
                    titleFrom in 1..polls
                },
                closeAnr = {
                    anrChecks++
                    val up = anrFromCheck != null && anrChecks >= anrFromCheck && "close-anr" !in steps
                    if (up) steps += "close-anr"
                    up
                },
                sleep = { now += it },
            )
    }

    @Nested
    @DisplayName("did Home leave the game (window in front)")
    inner class HomeByWindow {
        @Test
        fun `the package in front decides, and only an unreadable one falls back to pixels`() {
            assertEquals(false, homeLeftGameByWindow(Game.GAME_PACKAGE))
            assertEquals(true, homeLeftGameByWindow("com.android.launcher3"))
            assertEquals(null, homeLeftGameByWindow(null))
        }

        @Test
        fun `the launcher in front goes on without the still-screen captures`() {
            val s = Script(front = "com.android.launcher3", keepsAnimating = true)
            assertEquals(FrozenGameRestart.RESTARTED, s.run())
            assertEquals(1, s.captures, "only the before-Home grid")
            assertEquals(listOf(5.0, 15.0, 25.0, 35.0, 45.0, 55.0), s.killTimes)
            assertEquals(GAME_LAUNCH_SECONDS_AFTER_HOME + GAME_TITLE_POLL_SECONDS, s.now, 1e-9)
        }

        @Test
        fun `the game still in front closes nothing, whatever the pixels say`() {
            val s = Script(front = Game.GAME_PACKAGE)
            assertEquals(FrozenGameRestart.SCREEN_UNCHANGED_AFTER_HOME, s.run())
            assertEquals(listOf("home"), s.steps)
        }
    }

    @Nested
    @DisplayName("the close-and-relaunch sequence")
    inner class Sequence {
        @Test
        fun `nothing is closed or launched when the screen did not leave the game`() {
            val s = Script(leavesGame = false)
            assertEquals(FrozenGameRestart.SCREEN_UNCHANGED_AFTER_HOME, s.run())
            assertEquals(listOf("home"), s.steps)
        }

        @Test
        fun `nothing is closed when Home was not dispatched, and nothing waits`() {
            val s = Script(homePressed = false)
            assertEquals(FrozenGameRestart.SCREEN_UNCHANGED_AFTER_HOME, s.run())
            assertEquals(listOf("home"), s.steps)
            assertEquals(0.0, s.now, 1e-9)
        }

        @Test
        fun `nothing is closed when the screen keeps changing after Home`() {
            val s = Script(keepsAnimating = true)
            assertEquals(FrozenGameRestart.SCREEN_UNCHANGED_AFTER_HOME, s.run())
            assertEquals(listOf("home"), s.steps)
            assertEquals(GAME_HOME_SETTLE_SECONDS + GAME_HOME_STILL_SECONDS, s.now, 1e-9)
        }

        @Test
        fun `Home comes first, the kill repeats across the settle window, then one fresh-task launch`() {
            val s = Script()
            assertEquals(FrozenGameRestart.RESTARTED, s.run())
            assertEquals(listOf("home") + List(6) { "kill" } + "launch-fresh", s.steps)
            assertEquals(listOf(5.0, 15.0, 25.0, 35.0, 45.0, 55.0), s.killTimes)
            assertEquals(GAME_LAUNCH_SECONDS_AFTER_HOME + GAME_TITLE_POLL_SECONDS, s.now, 1e-9)
        }

        @Test
        fun `a fresh launch with no title gets exactly one plain launch`() {
            val s = Script(titleAfterLaunch = 2)
            assertEquals(FrozenGameRestart.RESTARTED_ON_PLAIN_LAUNCH, s.run())
            assertEquals(listOf("launch-fresh", "launch-plain"), s.steps.filter { it.startsWith("launch") })
        }

        @Test
        fun `no title after either launch is not a restart, and the waits are bounded`() {
            val s = Script(titleAfterLaunch = null)
            assertEquals(FrozenGameRestart.NOT_RESTARTED, s.run())
            assertEquals(listOf("launch-fresh", "launch-plain"), s.steps.filter { it.startsWith("launch") })
            assertEquals(GAME_LAUNCH_SECONDS_AFTER_HOME + 2 * GAME_TITLE_WAIT_SECONDS, s.now, 1e-9)
        }

        @Test
        fun `a title that shows only late in the wait still counts`() {
            val polls = (GAME_TITLE_WAIT_SECONDS / GAME_TITLE_POLL_SECONDS).toInt()
            assertEquals(FrozenGameRestart.RESTARTED, Script(titleAfterPolls = polls).run())
            assertEquals(FrozenGameRestart.RESTARTED_ON_PLAIN_LAUNCH, Script(titleAfterPolls = polls + 1).run())
        }

        @Test
        fun `a fresh launch that cannot be dispatched falls back to the plain launch`() {
            val s = Script(launchDispatches = listOf(false, true), titleAfterLaunch = 2)
            assertEquals(FrozenGameRestart.RESTARTED_ON_PLAIN_LAUNCH, s.run())
        }

        @Test
        fun `no launch dispatched at all is reported as such`() {
            assertEquals(FrozenGameRestart.NOT_DISPATCHED, Script(launchDispatches = listOf(false, false), titleAfterLaunch = null).run())
        }
    }

    @Nested
    @DisplayName("Android's \"isn't responding\" dialog during the title wait")
    inner class AnrDuringTitleWait {
        private val launches = { s: Script -> s.steps.filter { it.startsWith("launch") || it == "close-anr" } }

        @Test
        fun `the title wait closes the dialog once, launches fresh again and then sees the title`() {
            val s = Script(titleAfterLaunch = 2, anrFromCheck = 3)
            assertEquals(FrozenGameRestart.RESTARTED, s.run())
            assertEquals(listOf("launch-fresh", "close-anr", "launch-fresh"), launches(s))
            assertEquals(GAME_LAUNCH_SECONDS_AFTER_HOME + 3 * GAME_TITLE_POLL_SECONDS + ANR_CLOSE_SETTLE_SECONDS + GAME_TITLE_POLL_SECONDS, s.now, 1e-9)
        }

        @Test
        fun `a dialog that comes up during the plain launch's wait is closed there`() {
            val polls = (GAME_TITLE_WAIT_SECONDS / GAME_TITLE_POLL_SECONDS).toInt()
            val s = Script(titleAfterLaunch = 3, anrFromCheck = polls + 1)
            assertEquals(FrozenGameRestart.RESTARTED_ON_PLAIN_LAUNCH, s.run())
            assertEquals(listOf("launch-fresh", "launch-plain", "close-anr", "launch-fresh"), launches(s))
        }

        @Test
        fun `a closed dialog and still no title is not a restart, the waits are bounded and the dialog is closed only once`() {
            val s = Script(titleAfterLaunch = null, anrFromCheck = 3)
            assertEquals(FrozenGameRestart.NOT_RESTARTED, s.run())
            assertEquals(listOf("launch-fresh", "close-anr", "launch-fresh", "launch-plain"), launches(s))
            assertEquals(3, s.anrChecks, "no check after the close: a second dialog is left to the unknown-screen ladder")
            assertEquals(GAME_LAUNCH_SECONDS_AFTER_HOME + 3 * GAME_TITLE_POLL_SECONDS + ANR_CLOSE_SETTLE_SECONDS + 2 * GAME_TITLE_WAIT_SECONDS, s.now, 1e-9)
        }

        @Test
        fun `without the dialog the launches and waits are unchanged`() {
            val s = Script(titleAfterLaunch = null)
            assertEquals(FrozenGameRestart.NOT_RESTARTED, s.run())
            assertEquals(listOf("launch-fresh", "launch-plain"), launches(s))
            assertEquals(2 * (GAME_TITLE_WAIT_SECONDS / GAME_TITLE_POLL_SECONDS).toInt(), s.anrChecks)
        }

        @Test
        fun `after Close app one fresh launch and the title wait decide the outcome`() {
            fun relaunch(dispatched: Boolean, titleAt: Int?): Triple<GameReopen, List<Boolean>, Double> {
                var now = 0.0
                var polls = 0
                val launched = mutableListOf<Boolean>()
                val reopen = relaunchClosedGame(launch = { launched += it; dispatched }, titleShowing = { ++polls == titleAt }, sleep = { now += it })
                return Triple(reopen, launched, now)
            }
            assertEquals(Triple(GameReopen.RESTARTED, listOf(true), ANR_CLOSE_SETTLE_SECONDS + GAME_TITLE_POLL_SECONDS), relaunch(true, 1))
            assertEquals(Triple(GameReopen.NOT_RESTARTED, listOf(true), ANR_CLOSE_SETTLE_SECONDS + GAME_TITLE_WAIT_SECONDS), relaunch(true, null))
            assertEquals(Triple(GameReopen.NOT_DISPATCHED, listOf(true), ANR_CLOSE_SETTLE_SECONDS), relaunch(false, 1))
        }
    }

    @Nested
    @DisplayName("which node closes Android's \"isn't responding\" dialog")
    inner class AnrCloseNode {
        private val names = listOf("Umamusume", Game.GAME_PACKAGE)
        private val dialog =
            listOf(
                WindowNode(null, null),
                WindowNode("android:id/alertTitle", "Umamusume isn't responding"),
                WindowNode("android:id/aerr_close", "Close app"),
                WindowNode("android:id/aerr_wait", "Wait"),
            )

        private fun titled(title: String) = dialog.map { if (it.viewId == "android:id/alertTitle") it.copy(text = title) else it }

        @Test
        fun `the game's dialog is closed by its Close app button, never Wait`() {
            assertEquals(2, anrCloseNode("android", dialog, names))
        }

        @Test
        fun `a dialog of another app, this one included, is never clicked`() {
            for (app in listOf("Chrome", "UMA Auto+")) assertEquals(null, anrCloseNode("android", titled("$app isn't responding"), names), app)
            assertEquals(null, anrCloseNode(Game.GAME_PACKAGE, dialog, names), "the game's own window")
            assertEquals(null, anrCloseNode(null, dialog, names), "an unreadable window")
        }

        @Test
        fun `without the Close app id only its exact label is clicked`() {
            val renamed = dialog.map { it.copy(viewId = null) }
            assertEquals(2, anrCloseNode("android", renamed, names))
            assertEquals(null, anrCloseNode("android", renamed.filter { it.text != "Close app" }, names), "Wait alone is never pressed")
        }

        @Test
        fun `an unreadable game label leaves the dialog alone`() {
            assertEquals(null, anrCloseNode("android", dialog, listOf(Game.GAME_PACKAGE)))
        }
    }

    @Test
    fun `the ladder names a restart only when the title proved it`() {
        assertTrue(reopenOutcomeWords(GameReopen.RESTARTED).contains("restarted"))
        for (other in GameReopen.entries - GameReopen.RESTARTED) {
            assertFalse(reopenOutcomeWords(other).contains("restarted the game"), other.name)
        }
        assertTrue(reopenOutcomeWords(GameReopen.REFRONTED).contains("not restarted"))
    }
}
