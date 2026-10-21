package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.ButtonToHome
import com.steve1316.uma_android_automation.components.DialogObjects
import com.steve1316.uma_android_automation.components.DialogStoryUnlocked
import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.utils.FixturePng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The "Story Unlocked" dialog that opens over the Team Trials results when a trainee's episode unlocks is closed by the shared
 * dialog handler with Close, never the episode card or To Home. The fixture is the dialog's Close row, rows 1180 to 1320 of a
 * 1080x1920 frame (fixtures/storyunlocked/PROVENANCE.md).
 */
@DisplayName("Story Unlocked dialog")
class StoryUnlockedDialogFixtureTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun source(relative: String) = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/$relative").readText().replace("\r\n", "\n")

    private fun image(name: String): FixturePng = requireNotNull(javaClass.getResourceAsStream("/fixtures/storyunlocked/$name.png")) { "missing fixture $name.png" }.use { FixturePng.read(it) }

    private fun template(name: String): FixturePng = repoFile("android/app/src/main/assets/images/components/button/$name.png").inputStream().use { FixturePng.read(it) }

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

    /** The fixture's first row in the frame. */
    private val rowOffset = 1180

    @Test
    fun `the title read from the banner names this dialog and no other`() {
        assertEquals(DialogStoryUnlocked.title, DialogUtils.matchDialogTitle("Story Unlocked"))
        assertEquals(listOf(DialogStoryUnlocked), DialogObjects.items.filter { it.title == DialogStoryUnlocked.title })
        assertEquals(DialogStoryUnlocked, DialogObjects.map["story_unlocked"])
    }

    @Test
    fun `close taps Close, and To Home is only a later entry`() {
        assertEquals(ButtonClose, DialogStoryUnlocked.closeButton)
        assertEquals(ButtonClose, DialogStoryUnlocked.buttons.first())
        assertTrue(ButtonToHome in DialogStoryUnlocked.buttons)
    }

    @Test
    fun `the Close template finds the dialog's button in the bottom half, and To Home matches nothing on it`() {
        val row = image("story_unlocked_close_row")
        val close = bestMatch(row, template("close"))
        assertTrue(close.score >= 0.90, "Close scored ${close.score}")
        assertTrue(abs(close.cx - 539) <= 3 && abs(close.cy + rowOffset - 1250) <= 3, "Close matched at (${close.cx}, ${close.cy + rowOffset})")
        assertTrue(close.cy + rowOffset >= 1920 / 2, "Close is outside the bottom half")
        val toHome = bestMatch(row, template("to_home"))
        assertTrue(toHome.score < 0.80, "To Home scored ${toHome.score}")
    }

    @Test
    fun `the shared handler closes the dialog before cascading to a campaign or task`() {
        val handler = source("bot/DialogHandler.kt")
        val branch = handler.indexOf("\"story_unlocked\" -> {")
        assertTrue(branch >= 0, "no story_unlocked branch in the shared handler")
        val body = handler.substring(branch, handler.indexOf("}", branch))
        assertTrue(body.contains("dialog.close(game.imageUtils)"), body)
        assertTrue(branch < handler.indexOf("else -> {", handler.indexOf("open fun handleDialogs(")), "the branch sits before the shared handler's cascade")
    }
}
