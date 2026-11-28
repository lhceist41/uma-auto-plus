package com.steve1316.uma_android_automation

import com.steve1316.automation_library.utils.TextUtils
import com.steve1316.uma_android_automation.components.DialogDataDownload
import com.steve1316.uma_android_automation.components.DialogDataUpdate
import com.steve1316.uma_android_automation.components.DialogObjects
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The game's "Data Update" prompt: its only button, "Title Screen", leaves the loaded career, so every reader
 * stops the run with its own reason and taps nothing. Text-level only: no capture of the prompt exists here.
 */
@DisplayName("Data Update prompt")
class DataUpdatePromptTest {
    private val titles = DialogObjects.items.map { it.title }

    @Test
    fun `its title reads as its own dialog, never as Data Download, and it has no button the bot may press`() {
        for (read in listOf("Data Update", "Data Update.", "DataUpdate", "Data Updale", "Dala Update")) {
            assertEquals(DialogDataUpdate.title, TextUtils.matchStringInList(read, titles), read)
        }
        for (title in titles - DialogDataUpdate.title) {
            assertTrue(TextUtils.matchStringInList(title, titles) != DialogDataUpdate.title, title)
        }
        assertEquals(DialogDataDownload.title, TextUtils.matchStringInList("Data Download", titles))
        assertEquals(DialogDataUpdate, DialogObjects.map["data_update"])
        assertNull(DialogDataUpdate.okButton)
        assertNull(DialogDataUpdate.closeButton)
        assertTrue(DialogDataUpdate.buttons.isEmpty())
    }

    @Test
    fun `between runs it stops the queue with its own reason and taps nothing, even with no career in flight`() {
        assertEquals(BetweenRunDialog.DATA_UPDATE, readBetweenRunDialog(false, { true }, { DialogDataUpdate.title }))
        for (mayReturnToTitle in listOf(false, true)) {
            val step = planBetweenRunDialog(BetweenRunDialog.DATA_UPDATE, { throw AssertionError("no outage budget for a data update") }, StartModule.NAV_DEADLINE_MS, mayReturnToTitle)
            assertEquals(BetweenRunDialogStep.Fail(reasonKey = "DATA_UPDATE"), step)
            assertTrue(step.taps.isEmpty())
        }
        assertTrue("DATA_UPDATE" in REPORT_REASON_KEYS, "the stop has its own words")
    }

    @Test
    fun `every getDialog caller stops on it with the bot's own stop and taps nothing`() {
        val dialog = source("components/Dialog.kt")
        val stop = block(dialog, "fun stopForDataUpdate(", "\n    }\n")
        for (line in listOf("StartModule.queueStopKey = \"DATA_UPDATE\"", "StartModule.queueStopReason = reason", "StartModule.queueStopRequested = true", "throw InterruptedException(reason)")) {
            assertTrue(stop.contains(line), line)
        }
        for (never in listOf(".click(", "tap(", ".ok(", ".close(")) assertFalse(stop.contains(never), never)
        assertTrue(block(dialog, "private fun handleDangerousDialogs(", "\n    }\n").contains("if (dialog == DialogDataUpdate) stopForDataUpdate()"))
    }

    private fun block(
        src: String,
        startMarker: String,
        endMarker: String,
    ): String {
        val start = src.indexOf(startMarker)
        assertTrue(start >= 0, "missing $startMarker")
        val end = src.indexOf(endMarker, start)
        assertTrue(end > start, "missing the end of $startMarker")
        return src.substring(start, end)
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val path = "android/app/src/main/java/com/steve1316/uma_android_automation/$relative"
        repeat(8) {
            listOf(File(dir, path), File(dir, "src/main/java/com/steve1316/uma_android_automation/$relative")).firstOrNull { it.isFile }?.let { return it.readText().replace("\r\n", "\n") }
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found")
    }
}
