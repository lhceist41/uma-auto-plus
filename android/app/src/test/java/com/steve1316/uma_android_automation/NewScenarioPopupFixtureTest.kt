package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.FixturePng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The first-time "New Scenario" popup is dismissed with Close, the only button it has. The fixture is the popup's
 * Close row, rows 1240 to 1480 of a 1080x1920 frame.
 */
@DisplayName("New Scenario popup")
class NewScenarioPopupFixtureTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun image(name: String): FixturePng = requireNotNull(javaClass.getResourceAsStream("/fixtures/$name.png")) { "missing fixture $name.png" }.use { FixturePng.read(it) }

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

    @Test
    fun `the popup is dismissed with Close only, and the Close template finds its button`() {
        val step = planBetweenRunDialog(BetweenRunDialog.NEW_SCENARIO, { throw AssertionError("no outage budget") }, StartModule.NAV_DEADLINE_MS)
        assertEquals(BetweenRunDialogStep.CloseNewScenario, step)
        assertEquals(1, step.taps.size)
        val m = bestMatch(image("newscenario/new_scenario_close_row"), template("close"))
        assertTrue(m.score >= 0.90, "scored ${m.score}")
        assertTrue(abs(m.cx - 539) <= 3 && abs(m.cy - 136) <= 3, "Close matched at (${m.cx}, ${m.cy})")
    }
}
