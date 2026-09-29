package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.types.DateMonth
import com.steve1316.uma_android_automation.types.DatePhase
import com.steve1316.uma_android_automation.types.DateYear
import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.types.Trainee
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The item, Good-Luck Charm, Reset Whistle and recovery records reach the Decision Report and the
 * decision trace, and Trackblazer and Training call their recorders where the bot uses an item or
 * falls back to recovery. The call sites need a device, so they are pinned in source.
 */
@DisplayName("Decision trace item and recovery wiring")
class DecisionTraceItemWiringTest {
    private val trackblazer by lazy { source("bot/campaigns/Trackblazer.kt") }
    private val training by lazy { source("bot/Training.kt") }

    @Test
    fun `used items, the charm, the whistle and a recovery reach the report and the trace`() {
        val tracer = DecisionTracer()
        tracer.startTurn(GameDate(year = DateYear.SENIOR, month = DateMonth.MARCH, phase = DatePhase.EARLY), Trainee())
        tracer.recordItemDecision("Empowering Megaphone", DecisionTracer.ItemVerdict.USED, "Boosting training.")
        tracer.recordCharmGate(queued = true)
        tracer.recordWhistleOutcome(DecisionTracer.WhistleVerdict.USED, "No suitable training found.", postRollSelection = StatName.WIT)
        tracer.recordRecoveryExecuted("RECOVER_ENERGY", "No training worth taking this turn.")

        val record = DecisionTrace.buildRecord(timestamp = 1L, evidence = tracer.turnEvidence(), scenario = "Trackblazer")
        val items = record.getJSONArray("items")
        val names = (0 until items.length()).map { items.getJSONObject(it).getString("item") }
        assertEquals(listOf("Empowering Megaphone", "Good-Luck Charm", "Reset Whistle"), names)
        assertEquals("RECOVER_ENERGY", record.getJSONObject("selected").getJSONObject("recovery").getString("action"))

        MessageLog.clearLog()
        tracer.emit()
        val report = MessageLog.getMessageLogCopy().single()
        assertFalse(report.contains("Items Used: None"), report)
        for (name in names) assertTrue(report.contains(name), report)
    }

    @Test
    fun `every confirmed item use is traced`() {
        assertTrue(Regex("""usedItems\.forEach \{ useInventoryItem\(it\.first\) \}\s+traceItemsUsed\(usedItems\)""").containsMatchIn(trackblazer))
        assertTrue(Regex("""else if \(confirmAndCloseItemDialog\(itemsUsed\.size\)\) \{\s+itemsUsed\.forEach \{ \(name, _\) ->[\s\S]{0,400}?useInventoryItem\(name\)[\s\S]{0,200}?traceItemsUsed\(itemsUsed\)""").containsMatchIn(trackblazer))
        assertTrue(Regex("""if \(confirmAndCloseItemDialog\(itemsUsed\.size\)\) \{\s+itemsUsed\.forEach \{ \(name, _\) -> useInventoryItem\(name\) \}\s+traceItemsUsed\(itemsUsed\)""").containsMatchIn(trackblazer))
        assertTrue(Regex("""if \(confirmAndCloseItemDialog\(itemsUsedCount\)\) \{\s+traceItemsUsed\(itemsUsedWithReasons\)""").containsMatchIn(trackblazer))
        assertTrue(Regex("""autoUsedItems\.forEach \{ useInventoryItem\(it\) \}\s+traceItemsUsed\(autoUsedItems\.map""").containsMatchIn(trackblazer))
        assertEquals(5, Regex("""\btraceItemsUsed\(""").findAll(trackblazer).count() - 1, "one definition plus five call sites")
    }

    @Test
    fun `a greyed Confirm Use leaves the cache and the trace untouched at every caller`() {
        // One definition plus four callers, each one gating its bookkeeping on the result.
        assertEquals(5, Regex("""\bconfirmAndCloseItemDialog\(""").findAll(trackblazer).count())
        assertEquals(emptyList<String>(), Regex("""(?m)^\s+confirmAndCloseItemDialog\(.*$""").findAll(trackblazer).map { it.value.trim() }.toList())
        assertTrue(Regex("""if \(bWhistleQueued && confirmAndCloseItemDialog\(1\)\) \{\s+useInventoryItem\("Reset Whistle"\)""").containsMatchIn(trackblazer))
        assertTrue(Regex("""\} else \{\s+// Nothing was used[^\n]*\s+currentInventory = TrackblazerShopList\.returnItemsToInventory\(currentInventory, itemsUsedWithReasons\.map \{ it\.first \}\)""").containsMatchIn(trackblazer))
        // The emergency and race-item paths no longer decrement before the confirm.
        assertFalse(Regex("""bForceUseReservedItem = false\s+itemsUsed\.forEach""").containsMatchIn(trackblazer))
        assertFalse(Regex("""reason = "Race bonus for \${'$'}grade\."\)\s+itemsUsed\.forEach""").containsMatchIn(trackblazer))
        assertTrue(Regex("""return false\s+\}\s+MessageLog\.i\(TAG, "\[TRACKBLAZER\] Confirming usage""").containsMatchIn(trackblazer))
    }

    @Test
    fun `the charm and the whistle get their own records and are not listed twice`() {
        assertTrue(trackblazer.contains("\"Good-Luck Charm\" -> decisionTracer?.recordCharmGate(queued = true)"))
        assertTrue(trackblazer.contains("\"Reset Whistle\" -> Unit"))
        assertTrue(Regex("""Conserving Charm[^\n]*\n\s+\)\s+decisionTracer\?\.recordCharmGate\(queued = false, [^)]*\)\s+return null""").containsMatchIn(trackblazer))
        for (verdict in DecisionTracer.WhistleVerdict.entries) {
            assertTrue(trackblazer.contains("recordWhistleOutcome(DecisionTracer.WhistleVerdict.${verdict.name}"), verdict.name)
        }
        assertTrue(Regex("""recordWhistleOutcome\(DecisionTracer\.WhistleVerdict\.USED, [^\n]*postRollSelection = trainingSelected\)""").containsMatchIn(trackblazer))
    }

    @Test
    fun `every training backout that recovers records the recovery it ran`() {
        val bare = Regex("""(?m)^\s+(campaign\.)?recover(Mood|Energy)\(\)\s*$""")
        assertEquals(emptyList<String>(), bare.findAll(trackblazer).map { it.value.trim() }.toList())
        assertEquals(emptyList<String>(), bare.findAll(training).map { it.value.trim() }.toList())
        assertEquals(4, Regex("""if \(recover(Mood|Energy)\(\)\) decisionTracer\?\.recordRecoveryExecuted\(""").findAll(trackblazer).count())
        assertEquals(2, Regex("""if \(campaign\.recoverEnergy\(\)\) campaign\.decisionTracer\?\.recordRecoveryExecuted\("RECOVER_ENERGY"""").findAll(training).count())
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
