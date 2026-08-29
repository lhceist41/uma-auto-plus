package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Pins the effective factual-corpus gate and its wiring into [Campaign].
 *
 * The four-cell matrix (corpus on/off x debug on/off) is exercised against the pure
 * [DecisionCorpusGate] helper. The wiring that a live Game would be needed to run - which streams
 * read the factual gate and which stay debug-only - is pinned with source guards, in the same style
 * as the base-training telemetry architecture freeze.
 */
@DisplayName("DecisionCorpusGate")
class DecisionCorpusGateTest {
    @Nested
    @DisplayName("effective gate matrix")
    inner class GateMatrix {
        @Test
        fun `corpus ON, debug OFF records the factual corpus but not the human report`() {
            assertTrue(DecisionCorpusGate.factualCorpusEnabled(recordDecisionData = true, debugDiagnostics = false))
            assertFalse(DecisionCorpusGate.humanReportEnabled(debugDiagnostics = false))
        }

        @Test
        fun `corpus OFF, debug OFF records nothing`() {
            assertFalse(DecisionCorpusGate.factualCorpusEnabled(recordDecisionData = false, debugDiagnostics = false))
            assertFalse(DecisionCorpusGate.humanReportEnabled(debugDiagnostics = false))
        }

        @Test
        fun `corpus OFF, debug ON preserves historical Debug Mode behavior`() {
            // Factual corpus still records under debug, and the heavy report is on.
            assertTrue(DecisionCorpusGate.factualCorpusEnabled(recordDecisionData = false, debugDiagnostics = true))
            assertTrue(DecisionCorpusGate.humanReportEnabled(debugDiagnostics = true))
        }

        @Test
        fun `corpus ON, debug ON records the factual corpus and the human report`() {
            assertTrue(DecisionCorpusGate.factualCorpusEnabled(recordDecisionData = true, debugDiagnostics = true))
            assertTrue(DecisionCorpusGate.humanReportEnabled(debugDiagnostics = true))
        }

        @Test
        fun `the human report never records when the debug gate is off, whatever the corpus setting`() {
            for (record in listOf(true, false)) {
                assertFalse(DecisionCorpusGate.humanReportEnabled(debugDiagnostics = false), "report must stay off when debug is off (record=$record)")
            }
        }
    }

    @Nested
    @DisplayName("Campaign wiring (source guard)")
    inner class CampaignWiring {
        private val campaign by lazy { sourceFile("bot/Campaign.kt").readText().replace("\r\n", "\n") }
        private val tracer by lazy { sourceFile("bot/DecisionTracer.kt").readText().replace("\r\n", "\n") }

        @Test
        fun `the factual gate is the dedicated setting OR the debug gate`() {
            assertTrue(campaign.contains("""SettingsHelper.getBooleanSetting("misc", "recordDecisionData", true)"""), "reads the dedicated setting, default true")
            assertTrue(
                campaign.contains("DecisionCorpusGate.factualCorpusEnabled(recordDecisionData, debugDiagnosticsEnabled)"),
                "factual gate routes through the tested helper",
            )
            assertTrue(
                campaign.contains("val debugDiagnosticsEnabled: Boolean = com.steve1316.uma_android_automation.BuildConfig.DEBUG || game.debugMode"),
                "debug gate stays a debug build or Debug Mode",
            )
        }

        @Test
        fun `both factual per-turn streams gate on the same factualCorpusEnabled`() {
            // decision_trace: the tracer (which owns the trace sink) is created on the factual gate.
            assertTrue(campaign.contains("if (factualCorpusEnabled) DecisionTracer("), "decision_trace tracer gates on factualCorpusEnabled")
            // career_state: its per-turn build/append block gates on the same expression.
            assertTrue(campaign.contains("if (factualCorpusEnabled && careerStateLatch.shouldBuild())"), "career_state gates on factualCorpusEnabled")
        }

        @Test
        fun `the human Decision Report is created on the debug gate only`() {
            assertTrue(
                campaign.contains("DecisionTracer(humanReportEnabled = DecisionCorpusGate.humanReportEnabled(debugDiagnosticsEnabled))"),
                "the human report is bound to the debug gate, not the corpus gate",
            )
            // And the tracer actually gates its report write on that flag.
            assertTrue(tracer.contains("if (humanReportEnabled) MessageLog.i(TAG, formatReport())"), "the report write is gated on humanReportEnabled")
        }

        @Test
        fun `shadow_advisor, fixtures, and the career_state compare stay debug-only`() {
            assertTrue(
                campaign.contains("val shadowAdvisorSink: com.steve1316.uma_android_automation.bot.shadowadvisor.ShadowAdvisorSink? =\n        if (debugDiagnosticsEnabled) {"),
                "shadow_advisor stays on the debug gate",
            )
            assertTrue(campaign.contains("if (debugDiagnosticsEnabled) compareCareerStateToTracer(careerState)"), "career_state compare stays debug-only")
            // Fixture capture is left on the literal debug expression, untouched by the corpus gate.
            assertTrue(
                campaign.contains("if ((com.steve1316.uma_android_automation.BuildConfig.DEBUG || game.debugMode) && dateChanged) {"),
                "per-turn fixture capture stays on the literal debug gate",
            )
        }

        @Test
        fun `no schema version changed`() {
            val decisionTrace = sourceFile("bot/DecisionTrace.kt").readText().replace("\r\n", "\n")
            val careerStateSerializer = sourceFile("bot/CareerStateSerializer.kt").readText().replace("\r\n", "\n")
            assertTrue(decisionTrace.contains("const val SCHEMA_VERSION: Int = 1"), "DecisionTrace schema unchanged")
            assertTrue(careerStateSerializer.contains("const val SCHEMA_VERSION: Int = 1"), "CareerState schema unchanged")
        }
    }

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
