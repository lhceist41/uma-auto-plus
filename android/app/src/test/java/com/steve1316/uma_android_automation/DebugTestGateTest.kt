package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/** Registry and call-site guards complement DiagnosticLaunchTest's behavioral coverage. */
@DisplayName("Debug-test diagnostic gate")
class DebugTestGateTest {
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
        fun `the immutable selection is checked before startTests`() {
            val resolve = game.indexOf("if (diagnosticSelection?.key == null) return null")
            val startTests = game.indexOf("task.startTests()")
            assertTrue(resolve in 0 until startTests, "dispatch must use the frozen selection")
        }

        @Test
        fun `a requested-but-unran diagnostic fails closed before normal navigation`() {
            val gate = game.indexOf("runDiagnostic()?.let { return it }")
            val navigation = game.indexOf("warnOnRacingConfigDrift()", gate)
            assertTrue(gate >= 0 && navigation > gate)
            assertTrue(game.contains("check(task.startTests())"), "missing handler must reject")
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
