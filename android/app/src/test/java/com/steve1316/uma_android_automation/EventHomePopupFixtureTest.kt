package com.steve1316.uma_android_automation

import com.steve1316.automation_library.utils.TextUtils
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.DialogEventHome
import com.steve1316.uma_android_automation.components.DialogObjects
import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.utils.FixturePng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The story event's "Event Home" popup ("Return to the event home screen?") is dismissed with Cancel, never Event Home.
 * The fixtures are its title bar (rows 436 to 536) and its button row (rows 1316 to 1436) from the halted queues on both
 * accounts; see fixtures/eventhome/PROVENANCE.md.
 */
@DisplayName("Event Home popup")
class EventHomePopupFixtureTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun source(relative: String) = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/$relative").readText().replace("\r\n", "\n")

    private fun image(name: String): FixturePng = requireNotNull(javaClass.getResourceAsStream("/fixtures/eventhome/$name.png")) { "missing fixture $name.png" }.use { FixturePng.read(it) }

    private fun template(name: String): FixturePng = repoFile("android/app/src/main/assets/images/components/$name.png").inputStream().use { FixturePng.read(it) }

    /** The matcher's grayscale: the library converts RGBA with BGR2GRAY, so red and blue weights are swapped. */
    private fun gray(argb: Int): Double = 0.114 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.299 * (argb and 0xFF)

    private class Match(val score: Double, val cx: Int, val cy: Int)

    /** Best TM_CCOEFF_NORMED score of [tmpl] anywhere in [img]. */
    private fun bestMatch(img: FixturePng, tmpl: FixturePng): Match {
        val tw = tmpl.width
        val th = tmpl.height
        val t = DoubleArray(tw * th) { gray(tmpl.getRGB(it % tw, it / tw)) }
        val tMean = t.average()
        for (i in t.indices) t[i] -= tMean
        val tNorm = sqrt(t.sumOf { it * it })
        val w = img.width
        val src = DoubleArray(w * img.height) { gray(img.getRGB(it % w, it / w)) }
        var best = Match(-1.0, 0, 0)
        for (oy in 0..(img.height - th)) {
            for (ox in 0..(w - tw)) {
                var sum = 0.0
                var sq = 0.0
                var cross = 0.0
                for (ty in 0 until th) {
                    val row = (oy + ty) * w + ox
                    for (tx in 0 until tw) {
                        val v = src[row + tx]
                        sum += v
                        sq += v * v
                        cross += v * t[ty * tw + tx]
                    }
                }
                val variance = sq - sum * sum / (tw * th)
                if (variance <= 0.0) continue
                val score = cross / (sqrt(variance) * tNorm)
                if (score > best.score) best = Match(score, ox + tw / 2, oy + th / 2)
            }
        }
        return best
    }

    private val accounts = listOf("a", "b")

    @Test
    fun `its title bar carries the dialog banner the title read starts from`() {
        for (account in accounts) {
            val m = bestMatch(image("event_home_title_row_$account"), template("dialog/dialog_title_gradient_2"))
            assertTrue(m.score >= 0.9, "$account scored ${m.score}")
        }
    }

    @Test
    fun `the title the bot read live resolves to its own dialog`() {
        // "Event Home" is what the live runs logged as the unmatched title.
        for (read in listOf("Event Home", "EventHome", "Event Hom", "Event Home.")) {
            assertEquals(DialogEventHome.title, DialogUtils.matchDialogTitle(read), read)
        }
        assertEquals(DialogEventHome, DialogObjects.map["event_home"])
        assertTrue(source("components/Dialog.kt").contains("val match: String? = matchDialogTitle(text)"))
    }

    @Test
    fun `another event's dialog title is not read as Event Home`() {
        val titles = DialogObjects.items.map { it.title }
        for (read in listOf("Event", "Event Shop", "Event Notice", "Event Rewards", "Event Details", "Event Missions", "Event Ranking")) {
            // The plain fuzzy match alone picks Event Home for these.
            assertEquals(DialogEventHome.title, TextUtils.matchStringInList(read, titles), read)
            assertNull(DialogUtils.matchDialogTitle(read), read)
        }
    }

    @Test
    fun `every other title still reads as itself`() {
        for (title in DialogObjects.items.map { it.title }) assertEquals(title, DialogUtils.matchDialogTitle(title), title)
        assertEquals("Date Changed", DialogUtils.matchDialogTitle("Date Chanqed"))
    }

    @Test
    fun `between runs it is its own dialog, dismissed with Cancel only`() {
        assertEquals(BetweenRunDialog.EVENT_HOME, readBetweenRunDialog(false, { true }, { DialogEventHome.title }))
        assertNull(readBetweenRunDialog(true, { true }, { DialogEventHome.title }))
        val step = planBetweenRunDialog(BetweenRunDialog.EVENT_HOME, { throw AssertionError("no outage budget for the event popup") }, StartModule.NAV_DEADLINE_MS)
        assertEquals(BetweenRunDialogStep.CancelEventHome, step)
        assertEquals(listOf(ButtonCancel), step.taps)
    }

    @Test
    fun `in a run the shared handler closes it, and Cancel is the only button close or ok can reach`() {
        assertEquals(listOf(ButtonCancel), DialogEventHome.buttons)
        assertNull(DialogEventHome.closeButton)
        assertNull(DialogEventHome.okButton)
        assertTrue(source("bot/DialogHandler.kt").contains("\"event_home\" -> {\n                dialog.close(game.imageUtils)\n            }"))
    }

    @Test
    fun `the Cancel template lands on Cancel, left of the Event Home button`() {
        for (account in accounts) {
            val m = bestMatch(image("event_home_button_row_$account"), template("button/cancel"))
            assertTrue(m.score >= 0.95, "$account scored ${m.score}")
            // Cancel spans x 90 to 515; Event Home starts at x 565. Full-frame centre (302, 1376).
            assertTrue(abs(m.cx - 302) <= 3 && abs(m.cy - 60) <= 3, "$account Cancel matched at (${m.cx}, ${m.cy})")
        }
    }
}
