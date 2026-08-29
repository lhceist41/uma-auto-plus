package com.steve1316.uma_android_automation.bot.shadowadvisor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Static non-interference proof for Shadow Advisor S3. It pins, from the production source, that the advisor is
 * invoked at exactly one point strictly AFTER the factual decision_trace append, that no gameplay decision /
 * training-selection / race / queue path references it, that its runtime path is fully isolated in try/catch, and
 * that it stays on the debug-diagnostics gate (decoupled from the broader factual-corpus setting the decision tracer
 * now records under) with no advisor setting of its own. These are the invariants that keep the advisor
 * observational-only and debug-only; the parity suite pins its output.
 */
@DisplayName("shadow advisor static non-interference")
class ShadowAdvisorNonInterferenceTest {
    private val campaign by lazy { source("bot/Campaign.kt") }
    private val outcomeCorpus by lazy { source("utils/OutcomeCorpus.kt") }
    private val sink by lazy { source("bot/shadowadvisor/ShadowAdvisorSink.kt") }

    @Test
    fun `S3 is invoked at exactly one point, inside appendDecisionTrace, after the factual decision_trace append`() {
        assertEquals(1, occurrences(campaign, "onDecisionTraceAppended("), "exactly one live S3 invocation")

        val method = campaign.indexOf("private fun appendDecisionTrace(")
        assertTrue(method >= 0, "appendDecisionTrace exists")
        // The next method after appendDecisionTrace bounds its body.
        val methodEnd = campaign.indexOf("private fun appendCareerState(", method)
        val call = campaign.indexOf("shadowAdvisorSink?.onDecisionTraceAppended(", method)
        assertTrue(call in method until methodEnd, "the S3 call lives inside appendDecisionTrace")

        val factualAppend = campaign.indexOf("OutcomeCorpus.append(game.myContext, record, OutcomeCorpus.DECISIONS_PATH", method)
        assertTrue(factualAppend in method until call, "the S3 call runs strictly AFTER the factual decision_trace append")
    }

    @Test
    fun `no decision, training, race, or queue path references the advisor`() {
        // The advisor decision family and its selection helpers must be free of any S3 symbol.
        for ((relative, label) in listOf("bot/Training.kt" to "training selection", "bot/Racing.kt" to "race logic", "StartModule.kt" to "queue/navigation")) {
            val text = source(relative)
            assertFalse(text.contains("ShadowAdvisor") || text.contains("shadowAdvisor"), "$label must not reference the shadow advisor")
        }
        // Inside Campaign, decideNextAction and executeAction must not reference the advisor: every S3 mention sits in
        // the field declaration block or the appendDecisionTrace/appendCareerState sink, never a decision method.
        val decide = campaign.indexOf("open fun decideNextAction(")
        val decideEnd = campaign.indexOf("\n    open fun executeAction(", decide)
        assertTrue(decide in 0 until decideEnd)
        assertFalse(campaign.substring(decide, decideEnd).contains("shadowAdvisor"), "decideNextAction must not reference the advisor")
    }

    @Test
    fun `only Campaign and the shadowadvisor package reference the advisor classes in production`() {
        val root = kotlinRoot()
        val offenders = ArrayList<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val rel = file.relativeTo(root).path.replace('\\', '/')
            if (rel.startsWith("bot/shadowadvisor/")) return@forEach
            val text = file.readText()
            val referencesClass = listOf("ShadowAdvisorSink", "ShadowAdvisorPolicy", "ShadowAdvisorContext", "ShadowAdvisorRecord").any { text.contains(it) }
            // OutcomeCorpus only owns the path constant (no advisor class); Campaign owns the sole invocation.
            if (referencesClass && rel != "bot/Campaign.kt") offenders.add(rel)
        }
        assertEquals(emptyList<String>(), offenders, "advisor classes are referenced only by Campaign and the shadowadvisor package")
    }

    @Test
    fun `the sink runtime path is fully isolated in try catch and never rethrows`() {
        val method = sink.substring(sink.indexOf("fun onDecisionTraceAppended("))
        val body = method.substring(0, method.indexOf("\n    internal fun evaluate("))
        assertTrue(body.indexOf("try {") in 0 until body.indexOf("OutcomeCorpus.append("), "the append is inside the try block")
        assertTrue(body.contains("catch (e: Exception)"), "failures are caught")
        assertFalse(body.contains("throw "), "the sink never rethrows")
    }

    @Test
    fun `S3 stays on the debug gate, decoupled from the corpus setting, with no advisor setting of its own`() {
        // The sink is created on the debug-diagnostics gate, NOT the broader factual-corpus gate the
        // decision tracer now records under, so a corpus-only run (Record Decision Data on, Debug Mode
        // off) never writes a shadow_advisor record.
        assertTrue(campaign.contains("com.steve1316.uma_android_automation.bot.shadowadvisor.ShadowAdvisorSink()"))
        val ctor = campaign.indexOf("ShadowAdvisorSink()")
        val gateWindow = campaign.substring(maxOf(0, ctor - 200), ctor)
        assertTrue(gateWindow.contains("if (debugDiagnosticsEnabled) {"), "the sink is gated on the debug-diagnostics gate")
        assertFalse(
            gateWindow.contains("factualCorpusEnabled") || gateWindow.contains("recordDecisionData"),
            "the sink is not gated on the factual-corpus setting",
        )
        // And the debug-diagnostics gate is exactly a debug build or Debug Mode.
        assertTrue(
            campaign.contains("val debugDiagnosticsEnabled: Boolean = com.steve1316.uma_android_automation.BuildConfig.DEBUG || game.debugMode"),
            "the debug gate is a debug build or Debug Mode",
        )
        assertFalse(sink.contains("getBooleanSetting") || sink.contains("SettingsHelper"), "the sink introduces no setting")
    }

    @Test
    fun `the shadow advisor path is a separate outcomes jsonl file`() {
        assertTrue(outcomeCorpus.contains("const val SHADOW_ADVISOR_PATH = \"\$OUTCOMES_DIR/shadow_advisor.jsonl\""))
    }

    private fun occurrences(text: String, needle: String): Int {
        var count = 0
        var i = text.indexOf(needle)
        while (i >= 0) {
            count++
            i = text.indexOf(needle, i + needle.length)
        }
        return count
    }

    private fun source(relative: String): String = File(kotlinRoot(), relative).readText().replace("\r\n", "\n")

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
