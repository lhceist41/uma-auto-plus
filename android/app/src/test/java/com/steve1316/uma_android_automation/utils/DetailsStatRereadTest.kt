package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The Details dialog stat read's re-read gate, replayed over 440 live dialog reads (src/test/resources/fixtures/statreads, see PROVENANCE.md). The OCR itself is ML Kit and needs
 * the device, so this pins the decision around it: which first reads earn the one re-read.
 */
@DisplayName("Details dialog stat re-read")
class DetailsStatRereadTest {
    private data class Read(val stat: String, val raw: String, val held: Int)

    private val reads: List<Read> by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/statreads/details_dialog_reads.csv")) { "missing details_dialog_reads.csv" }
        stream.bufferedReader().readLines().drop(1).filter { it.isNotBlank() }.map { line ->
            val (stat, raw, held) = line.split(",")
            Read(stat, raw, held.toInt())
        }
    }

    // The dialog reader's own parse: highest in-range number, then the floor against the held value.
    private fun firstRead(r: Read): Int {
        val best = StatReadPlausibility.bestInRange(StatReadPlausibility.statNumbers(r.raw), 2500) ?: return -1
        return if (StatReadPlausibility.isImplausibleDrop(best, r.held)) -1 else best
    }

    @Test
    fun `the gate fires on every lost-digit read and on no correct one`() {
        assertEquals(440, reads.size)
        val fired = reads.filter { StatReadPlausibility.needsDialogReread(firstRead(it)) }
        val wrong = reads.filter { it.raw != it.held.toString() }
        assertEquals(wrong, fired)
        assertEquals(34, fired.size)
        assertTrue(fired.all { it.raw == "1" && it.held.toString().startsWith("1") })
    }

    @Test
    fun `values that do not start with 1 never needed a re-read`() {
        val others = reads.filter { !it.held.toString().startsWith("1") }
        assertEquals(288, others.size)
        assertTrue(others.none { StatReadPlausibility.needsDialogReread(firstRead(it)) })
    }

    @Test
    fun `the gate's bounds`() {
        assertTrue(StatReadPlausibility.needsDialogReread(-1))
        assertTrue(StatReadPlausibility.needsDialogReread(1))
        assertTrue(StatReadPlausibility.needsDialogReread(9))
        assertFalse(StatReadPlausibility.needsDialogReread(10))
        assertFalse(StatReadPlausibility.needsDialogReread(84))
    }

    @Test
    fun `number picking keeps the old dialog parse`() {
        assertEquals(listOf(1, 200), StatReadPlausibility.statNumbers("1 200"))
        assertEquals(emptyList<Int>(), StatReadPlausibility.statNumbers("MAX"))
        assertEquals(1200, StatReadPlausibility.bestInRange(listOf(1200, 7), 1600))
        assertEquals(null, StatReadPlausibility.bestInRange(listOf(9999), 1600))
    }

    @Test
    fun `only the dialog branch reads at 2x and re-reads, the main-screen branch is unchanged`() {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var src: String? = null
        repeat(8) {
            val f = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation/utils/CustomImageUtils.kt")
            if (src == null && f.isFile) src = f.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        val code = requireNotNull(src) { "CustomImageUtils.kt not found" }
        val body = code.substring(code.indexOf("fun determineStatValues("), code.indexOf("fun determineStatGainFromTraining("))
        assertTrue(body.contains("var value = readStat(if (isAptitudeDialog) DIALOG_STAT_READ_SCALE else 1.0)"))
        assertTrue(body.contains("if (isAptitudeDialog && StatReadPlausibility.needsDialogReread(value)) {"))
        assertEquals(2, Regex("readStat\\(").findAll(body).count() - 1, "one first read and one re-read")
        assertEquals(2.0, DIALOG_STAT_READ_SCALE)
        assertEquals(3.0, DIALOG_STAT_REREAD_SCALE)
    }
}
