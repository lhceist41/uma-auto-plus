package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.utils.FixturePng
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.sqrt

/**
 * A resumed Grand Concert career is proven by its Races button, so a career screen without it stops the run. These checks pin that
 * template at the 0.8 scenario-button bar on the committed Grand Concert career screens of three different turns, and below it on a
 * standard-layout career screen.
 */
@DisplayName("Grand Concert Races button on career screens")
class RacesGrandConcertFixtureTest {
    private val grandConcert = listOf("grandconcert/career_main_turn1", "grandconcert/career_after_training", "grandconcert/career_scheduled")
    private val standard = "trainingselection/career_menu_after_back"

    /** The right half of the career screen's second button row, where Races sits. */
    private val racesWindow = intArrayOf(540, 1600, 1080, 1860)

    private fun image(name: String): FixturePng = requireNotNull(javaClass.getResourceAsStream("/fixtures/$name.png")) { "missing fixture $name.png" }.use { FixturePng.read(it) }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val template by lazy { repoFile("android/app/src/main/assets/images/components/button/races_grandconcert.png").inputStream().use { FixturePng.read(it) } }

    /** The matcher's grayscale: the library converts RGBA with BGR2GRAY, so red and blue weights are swapped. */
    private fun gray(argb: Int): Double = 0.114 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.299 * (argb and 0xFF)

    private class Match(val score: Double, val cx: Int, val cy: Int)

    /** Best TM_CCOEFF_NORMED score of [tmpl] placed fully inside the window [x0, x1) x [y0, y1) of [img]. */
    private fun bestMatch(img: FixturePng, tmpl: FixturePng, window: IntArray): Match {
        val (x0, y0, x1, y1) = window.toList()
        val tw = tmpl.width
        val th = tmpl.height
        val t = DoubleArray(tw * th) { gray(tmpl.getRGB(it % tw, it / tw)) }
        val tMean = t.average()
        for (i in t.indices) t[i] -= tMean
        val tNorm = sqrt(t.sumOf { it * it })
        val ww = x1 - x0
        val src = DoubleArray(ww * (y1 - y0)) { gray(img.getRGB(x0 + it % ww, y0 + it / ww)) }
        var best = Match(-1.0, 0, 0)
        for (oy in 0..(y1 - y0 - th)) {
            for (ox in 0..(ww - tw)) {
                var sum = 0.0
                var sq = 0.0
                var cross = 0.0
                for (ty in 0 until th) {
                    val row = (oy + ty) * ww + ox
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
                if (score > best.score) best = Match(score, x0 + ox + tw / 2, y0 + oy + th / 2)
            }
        }
        return best
    }

    @Test
    fun `the Races button clears the scenario-button bar on every Grand Concert career screen`() {
        for (name in grandConcert) {
            val m = bestMatch(image(name), template, racesWindow)
            assertTrue(m.score >= 0.8, "$name scored ${m.score} at (${m.cx}, ${m.cy})")
        }
    }

    @Test
    fun `the Races button stays under the bar on a standard career screen`() {
        val m = bestMatch(image(standard), template, racesWindow)
        assertTrue(m.score < 0.8, "scored ${m.score} at (${m.cx}, ${m.cy})")
    }
}
