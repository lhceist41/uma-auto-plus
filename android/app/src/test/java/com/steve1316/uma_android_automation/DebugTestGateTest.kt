package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Debug-diagnostic arming fail-closed gate.
 *
 * On 2026-08-13 an intended read-only deck-number diagnostic did not arm at runtime, so
 * Campaign.startTests() ran nothing and Game.kt fell through into normal career navigation, which
 * pressed Start Career and spent TP. [DebugTestGate] gives the runtime one canonical answer to "was
 * a diagnostic requested" so Game.kt can log the armed set at session start (operator confirms on
 * Home) and stop FAIL-CLOSED if a diagnostic was requested but no test ran, instead of navigating.
 * The pure resolution is pinned here; source guards prove the registry stays in sync and the wiring
 * stops before navigation.
 */
@DisplayName("Debug-test diagnostic gate")
class DebugTestGateTest {
    @Nested
    @DisplayName("resolution (pure)")
    inner class Resolution {
        @Test
        fun `nothing armed means nothing requested`() {
            assertTrue(DebugTestGate.requested { false }.isEmpty())
            assertFalse(DebugTestGate.anyRequested { false })
        }

        @Test
        fun `an armed key is reported as requested`() {
            assertEquals(listOf("debugMode_startDeckNumberReadTest"), DebugTestGate.requested { it == "debugMode_startDeckNumberReadTest" })
            assertTrue(DebugTestGate.anyRequested { it == "debugMode_startDeckNumberReadTest" })
        }

        @Test
        fun `requested reports every armed key in registry order`() {
            val armed = setOf("debugMode_startRainbowDetectionTest", "debugMode_startTemplateMatchingTest")
            assertEquals(
                listOf("debugMode_startTemplateMatchingTest", "debugMode_startRainbowDetectionTest"),
                DebugTestGate.requested { it in armed },
            )
        }

        @Test
        fun `a key outside the registry never counts as requested`() {
            assertFalse(DebugTestGate.anyRequested { it == "debugMode_notARealTest" })
        }
    }

    @Nested
    @DisplayName("registry stays in sync (source guard)")
    inner class RegistrySync {
        @Test
        fun `ALL_KEYS mirrors the Debug Settings debugTestKeys UI list exactly`() {
            assertEquals(uiDebugTestKeys(), DebugTestGate.ALL_KEYS.toSet(), "DebugTestGate.ALL_KEYS must mirror DebugSettings debugTestKeys")
        }

        @Test
        fun `every Campaign startTests handler key is registered in ALL_KEYS`() {
            val campaign = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt").readText()
            val fnKeys = Regex("\"(debugMode_start\\w+)\" to ").findAll(campaign).map { it.groupValues[1] }.toSet()
            assertTrue(fnKeys.isNotEmpty(), "the Campaign fnMap should register debug tests")
            assertTrue(DebugTestGate.ALL_KEYS.containsAll(fnKeys), "every Campaign fnMap debug-test key must be in ALL_KEYS: missing ${fnKeys - DebugTestGate.ALL_KEYS.toSet()}")
        }
    }

    @Nested
    @DisplayName("Game.kt fail-closed wiring (source guard)")
    inner class GameWiring {
        private val game by lazy { repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Game.kt").readText().replace("\r\n", "\n") }

        @Test
        fun `the armed set is resolved and logged before startTests`() {
            val resolve = game.indexOf("DebugTestGate.requested")
            val startTests = game.indexOf("task.startTests()")
            assertTrue(resolve in 0 until startTests, "the armed diagnostic set must be resolved (for the Home-first log) before startTests")
            assertTrue(game.contains("[DEBUG-TEST]"), "the armed state is logged so the operator can confirm it on Home")
        }

        @Test
        fun `a requested-but-unran diagnostic fails closed before normal navigation`() {
            val startTests = game.indexOf("task.startTests()")
            val failClosed = game.indexOf("armedDebugTests.isNotEmpty()", startTests)
            val navigation = game.indexOf("warnOnRacingConfigDrift()", startTests)
            assertTrue(startTests >= 0 && navigation > startTests)
            assertTrue(failClosed in startTests until navigation, "the fail-closed gate must run after startTests and before normal navigation")
        }
    }

    private fun uiDebugTestKeys(): Set<String> {
        val ui = repoFile("src/pages/DebugSettings/index.tsx").readText()
        val block =
            Regex("const debugTestKeys = \\[(.*?)] as const", RegexOption.DOT_MATCHES_ALL).find(ui)?.groupValues?.get(1)
                ?: error("could not find the debugTestKeys array in DebugSettings")
        return Regex("\"(debugMode_\\w+)\"").findAll(block).map { it.groupValues[1] }.toSet()
    }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val f = File(dir, relative)
            if (f.isFile) return f
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${System.getProperty("user.dir")}")
    }
}
