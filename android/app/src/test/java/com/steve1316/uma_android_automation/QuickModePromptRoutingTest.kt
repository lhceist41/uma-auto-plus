package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.components.persistentSkipPillRegion
import com.steve1316.uma_android_automation.utils.PersistentSkipState
import com.steve1316.uma_android_automation.utils.classifyPersistentSkip
import com.steve1316.uma_android_automation.utils.isLaunchQuickModePrompt
import com.steve1316.uma_android_automation.utils.launchTapsSkipPill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Launch Quick Mode prompt vs in-career tap-to-continue routing.
 *
 * The persistent Skip pill is on screen for both, and the navigator told them apart with a
 * session-scoped "skip already maxed" latch that resets on every navigate() call. The in-career
 * loop's lobby re-entry calls navigate() DURING a running career, so that latch was false while the
 * career's own cutscene pill was on screen: the frame routed to the launch handler, which taps the
 * pill twice blind and walked an already-maxed pill back toward Off.
 *
 * These tests pin the entry-path decision and the wiring that carries it. Which chevron the pill
 * shows is deliberately not consulted for routing -- no recognizer can read it yet. The launch
 * handler's taps are the one exception: it taps only a pill that positively reads Off, because two
 * blind taps on a pill that was already "Skip >>" left a player's Skip mode on the slow "Skip >".
 */
@DisplayName("Quick Mode prompt routing")
class QuickModePromptRoutingTest {
    @Nested
    @DisplayName("routing decision")
    inner class RoutingDecision {
        @Test
        fun `a launch call whose Quick Mode prompt is still owed routes to the launch handler`() {
            assertTrue(isLaunchQuickModePrompt(resumingInProgressCareer = false, skipToggleAlreadyDone = false, previousCareerScreensAhead = false))
        }

        @Test
        fun `a launch call that already maxed skip routes to tap-to-continue`() {
            assertFalse(isLaunchQuickModePrompt(resumingInProgressCareer = false, skipToggleAlreadyDone = true, previousCareerScreensAhead = false))
        }

        @Test
        fun `a career resume never routes to the launch handler, whatever the latch says`() {
            // The regression: a fresh navigate() during a running career starts with the latch false.
            assertFalse(isLaunchQuickModePrompt(resumingInProgressCareer = true, skipToggleAlreadyDone = false, previousCareerScreensAhead = false))
            assertFalse(isLaunchQuickModePrompt(resumingInProgressCareer = true, skipToggleAlreadyDone = true, previousCareerScreensAhead = false))
        }

        @Test
        fun `the previous career's pill screens never route to the launch handler`() {
            assertFalse(isLaunchQuickModePrompt(resumingInProgressCareer = false, skipToggleAlreadyDone = false, previousCareerScreensAhead = true))
        }

        /** The live queue's second launch: a pill on the previous career's end screens, then Home, then the new career's prompt. */
        @Test
        fun `a second launch in the same queue still reaches its own Quick Mode prompt`() {
            var skipToggleAlreadyDone = false
            var launchFlowEntered = false
            val routed = mutableListOf<String>()
            for (screen in listOf("previous career pill", "Home", "new career prompt pill", "new career cutscene pill")) {
                if (screen == "Home") {
                    launchFlowEntered = true
                    continue
                }
                val prompt = isLaunchQuickModePrompt(false, skipToggleAlreadyDone, previousCareerScreensAhead = !launchFlowEntered)
                if (prompt) skipToggleAlreadyDone = true
                routed += if (prompt) "QUICK_MODE_PROMPT" else "TAP_TO_CONTINUE"
            }
            assertEquals(listOf("TAP_TO_CONTINUE", "QUICK_MODE_PROMPT", "TAP_TO_CONTINUE"), routed)
        }
    }

    @Nested
    @DisplayName("navigator wiring (source guard)")
    inner class NavigatorWiring {
        private val nav by lazy { sourceFile("CareerLaunchNavigator.kt").readText().replace("\r\n", "\n") }

        @Test
        fun `the only QUICK_MODE_PROMPT emission is guarded by the routing decision, inside the pill-visible branch`() {
            assertEquals(
                1,
                nav.occurrences("return LaunchScreenState.QUICK_MODE_PROMPT"),
                "a second emission point would bypass the guard",
            )
            val emit = nav.indexOf("return LaunchScreenState.QUICK_MODE_PROMPT")
            val guard = nav.lastIndexOf("isLaunchQuickModePrompt(resumeInProgressCareerMode || careerInFlightMode, skipToggleAlreadyDone, previousCareerScreensAhead)", emit)
            assertTrue(guard in (emit - 200) until emit, "the emission is guarded by the routing decision")
            val pillVisible = nav.lastIndexOf("if (skipState.pillVisible) {", emit)
            assertTrue(pillVisible in (emit - 600) until guard, "a frame with no pill never reaches the decision")
        }

        @Test
        fun `only a between-run or finalize call before Home holds the prompt back`() {
            assertTrue(nav.contains("val previousCareerScreensAhead = (previousCareerCompleteMode || finalizeToHomeMode) && !launchFlowEntered"))
            assertTrue(nav.contains("launchFlowEntered = false"), "reset per navigate() call")
        }

        @Test
        fun `the resume flag comes from the caller and is set per navigate call`() {
            assertTrue(nav.contains("resumeInProgressCareer: Boolean = false,"), "launch callers keep their existing signature")
            assertTrue(nav.contains("resumeInProgressCareerMode = resumeInProgressCareer"), "the flag is set per navigate() call, never inferred")
            assertEquals(
                1,
                nav.occurrences("resumeInProgressCareerMode = "),
                "one assignment only, so no path can flip the flag mid-navigation",
            )
        }

        @Test
        fun `the launch skip taps live only in the launch handler`() {
            val taps = listOf("skip_toggle_tap_1", "skip_toggle_tap_2")
            assertTrue(taps.all { nav.occurrences(it) == 1 }, "the two blind launch taps exist exactly once each")
            val handler = nav.indexOf("private fun handleQuickModePrompt(")
            val nextFun = nav.indexOf("\n    private fun ", handler + 1)
            assertTrue(taps.all { nav.indexOf(it) in handler until nextFun }, "no other path actuates the persistent pill")
        }

        @Test
        fun `the launch handler is reachable only from its own state`() {
            assertTrue(nav.contains("LaunchScreenState.QUICK_MODE_PROMPT -> handleQuickModePrompt()"), "the state dispatch is intact")
            assertEquals(
                2,
                nav.occurrences("handleQuickModePrompt("),
                "the declaration and that one dispatch are its only references",
            )
        }
    }

    @Nested
    @DisplayName("caller wiring (source guard)")
    inner class CallerWiring {
        private val campaign by lazy { sourceFile("bot/Campaign.kt").readText().replace("\r\n", "\n") }

        @Test
        fun `the lobby re-entry declares itself a career resume`() {
            assertTrue(
                campaign.contains("navigator.navigate(reuseLastLaunchSetup = true, resumeInProgressCareer = true)"),
                "the mid-career lobby re-entry is the call that must never reach the launch handler",
            )
        }

        @Test
        fun `no launch caller declares a career resume`() {
            for (relative in listOf("StartModule.kt", "bot/Game.kt")) {
                assertFalse(
                    sourceFile(relative).readText().contains("resumeInProgressCareer"),
                    "$relative launches careers and must keep the launch routing",
                )
            }
        }
    }

    @Nested
    @DisplayName("no chevron inference (source guard)")
    inner class NoChevronInference {
        @Test
        fun `the pill states stay observation-only`() {
            assertEquals(
                listOf("OFF", "ON_TEMPLATE_MATCH", "PRESENT_UNRESOLVED", "NOT_VISIBLE"),
                PersistentSkipState.entries.map { it.name },
                "a chevron-count state needs live evidence first",
            )
        }

        @Test
        fun `the routing decision reads no pill state at all`() {
            val pill = sourceFile("utils/PersistentSkipPill.kt").readText().replace("\r\n", "\n")
            val decl = pill.indexOf("fun isLaunchQuickModePrompt(")
            assertTrue(decl >= 0)
            val body = pill.substring(decl)
            assertFalse(body.contains("PersistentSkipState"), "the entry-path decision is independent of what the pill shows")
        }
    }

    @Nested
    @DisplayName("launch taps only an Off pill")
    inner class LaunchTapsOnlyAnOffPill {
        private val nav by lazy { sourceFile("CareerLaunchNavigator.kt").readText().replace("\r\n", "\n") }

        @Test
        fun `only a pill that reads Off is tapped`() {
            assertEquals(
                listOf(PersistentSkipState.OFF),
                PersistentSkipState.entries.filter { launchTapsSkipPill(it) },
            )
        }

        /**
         * Template outcomes at the 0.8 threshold in the pill region, measured on live 1080x1920
         * frames: `skip_off` scored 0.890-0.988 on every Off pill and at most 0.704 on every one- or
         * two-chevron pill. The launch frame that cycled a player's pill read 0.529 / 0.673.
         */
        @Test
        fun `measured pill frames tap only when Off`() {
            data class Frame(val name: String, val off: Boolean, val on: Boolean, val text: Boolean, val taps: Boolean)
            val frames =
                listOf(
                    Frame("Skip Off, main screen", off = true, on = false, text = true, taps = true),
                    Frame("Skip Off, event cutscene", off = true, on = false, text = true, taps = true),
                    Frame("Skip >, main screen", off = false, on = false, text = true, taps = false),
                    Frame("Skip >>, main screen", off = false, on = false, text = true, taps = false),
                    Frame("Skip >>, event choice", off = false, on = true, text = true, taps = false),
                    Frame("launch pill that was cycled to slow", off = false, on = false, text = true, taps = false),
                )
            for (frame in frames) {
                val state = classifyPersistentSkip({ frame.off }, { frame.on }, { frame.text })
                assertEquals(frame.taps, launchTapsSkipPill(state), frame.name)
            }
        }

        @Test
        fun `the two launch taps sit inside the Off gate and anything else only logs`() {
            val handler = nav.substring(nav.indexOf("private fun handleQuickModePrompt("))
            // InCareerSkipFix.attempt runs tapPillTwice only for a pill launchTapsSkipPill accepts (tested in PersistentSkipPillTest).
            val gate = handler.indexOf("InCareerSkipFix().attempt(\n                pillState,\n                tapPillTwice = {")
            val lambdaEnd = handler.indexOf("\n                },\n                reRead = ", gate)
            assertTrue(gate >= 0 && lambdaEnd > gate, "the taps are gated on the pill state")
            for (tap in listOf("skip_toggle_tap_1", "skip_toggle_tap_2")) {
                assertTrue(handler.indexOf(tap) in gate until lambdaEnd, "$tap runs only inside the Off gate")
            }
            val notOff = handler.substring(handler.indexOf("SkipFixOutcome.NOT_OFF ->"), handler.indexOf("SkipFixOutcome.LEFT_OFF ->"))
            assertFalse(notOff.contains("CoordinateTap"), "a pill that is not Off is never tapped")
            assertTrue(notOff.contains("leaving the player's Skip mode as it is"))
        }

        @Test
        fun `the resume and in-career body taps land outside the pill`() {
            val (x, y, w, h) = persistentSkipPillRegion(1080, 1920).toList()
            val bodyTaps = listOf("tap-to-continue" to (540.0 to 1920 * 0.677), "event cutscene" to (540.0 to 1300.0))
            for ((name, point) in bodyTaps) {
                val inside = point.first in x.toDouble()..(x + w).toDouble() && point.second in y.toDouble()..(y + h).toDouble()
                assertFalse(inside, "the $name tap must not hit the Skip pill")
            }
            assertTrue(nav.contains("CoordinateTap.tap(gestureUtils, (bitmap.width * 0.5).toDouble(), (bitmap.height * 0.677).toDouble(), \"tap_to_continue_advance\")"))
        }
    }

    private fun String.occurrences(needle: String): Int = split(needle).size - 1

    private fun sourceFile(relative: String): File = File(kotlinRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
