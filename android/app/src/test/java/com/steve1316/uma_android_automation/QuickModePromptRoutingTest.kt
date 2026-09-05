package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.PersistentSkipState
import com.steve1316.uma_android_automation.utils.isLaunchQuickModePrompt
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
 * shows is deliberately not consulted -- no recognizer can read it yet.
 */
@DisplayName("Quick Mode prompt routing")
class QuickModePromptRoutingTest {
    @Nested
    @DisplayName("routing decision")
    inner class RoutingDecision {
        @Test
        fun `a launch call whose Quick Mode prompt is still owed routes to the launch handler`() {
            assertTrue(isLaunchQuickModePrompt(resumingInProgressCareer = false, skipToggleAlreadyDone = false))
        }

        @Test
        fun `a launch call that already maxed skip routes to tap-to-continue`() {
            assertFalse(isLaunchQuickModePrompt(resumingInProgressCareer = false, skipToggleAlreadyDone = true))
        }

        @Test
        fun `a career resume never routes to the launch handler, whatever the latch says`() {
            // The regression: a fresh navigate() during a running career starts with the latch false.
            assertFalse(isLaunchQuickModePrompt(resumingInProgressCareer = true, skipToggleAlreadyDone = false))
            assertFalse(isLaunchQuickModePrompt(resumingInProgressCareer = true, skipToggleAlreadyDone = true))
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
            val guard = nav.lastIndexOf("isLaunchQuickModePrompt(resumeInProgressCareerMode, skipToggleAlreadyDone)", emit)
            assertTrue(guard in (emit - 200) until emit, "the emission is guarded by the routing decision")
            val pillVisible = nav.lastIndexOf("if (skipState.pillVisible) {", emit)
            assertTrue(pillVisible in (emit - 400) until guard, "a frame with no pill never reaches the decision")
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
