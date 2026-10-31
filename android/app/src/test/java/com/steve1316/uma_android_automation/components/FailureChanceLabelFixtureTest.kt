package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.utils.FixturePng
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The per-tile failure-chance read crops the digits under the "Failure" label, so the label's location must come from the frame that is
 * read. These checks pin the label template on band crops (y 1300 to 1500 of 1080x1920 Training screens) taken from live runs: it matches
 * at the bubble on frames where the bubble has settled, and it stays under the matcher's confidence floor on a frame captured while the
 * bubble was still sliding between two tiles, which is what lets a failed match gate the recapture.
 */
@DisplayName("Failure chance label on settled and tweening frames")
class FailureChanceLabelFixtureTest {
    /** The first row of the band crops in full-frame coordinates. */
    private val bandTop = 1300

    /** Settled frames and the label's center on the full frame (tile centers x 159, 350, 541, 732, 923). */
    private val settled =
        mapOf(
            "failurechance/settled_guts_0" to Pair(732, 1392),
            "failurechance/settled_wit_0" to Pair(923, 1391),
            "failurechance/settled_guts_0_late" to Pair(732, 1400),
            "failurechance/settled_stamina_0" to Pair(350, 1389),
            "failurechance/settled_wit_12" to Pair(923, 1389),
        )

    private val tween = "failurechance/tween_power_to_guts"

    /** The default template-match confidence (`CustomImageUtils.match`). */
    private val confidenceFloor = 0.80

    private val cache = mutableMapOf<String, FixturePng>()

    private fun image(name: String): FixturePng =
        cache.getOrPut(name) {
            val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/$name.png")) { "missing fixture $name.png" }
            stream.use { FixturePng.read(it) }
        }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun template(): FixturePng = repoFile("android/app/src/main/assets/images/components/label/training_failure_chance.png").inputStream().use { FixturePng.read(it) }

    /** The matcher's grayscale: the library converts RGBA with BGR2GRAY, so red and blue weights are swapped. */
    private fun gray(argb: Int): Double = 0.114 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.299 * (argb and 0xFF)

    private class Match(val score: Double, val cx: Int, val cy: Int)

    /** Best TM_CCOEFF_NORMED score of [tmpl] placed fully inside [img]; the center is in full-frame coordinates. */
    private fun bestMatch(img: FixturePng, tmpl: FixturePng): Match {
        val tw = tmpl.width
        val th = tmpl.height
        val t = DoubleArray(tw * th) { gray(tmpl.getRGB(it % tw, it / tw)) }
        val tMean = t.average()
        for (i in t.indices) t[i] -= tMean
        val tNorm = sqrt(t.sumOf { it * it })
        val ww = img.width
        val src = DoubleArray(ww * img.height) { gray(img.getRGB(it % ww, it / ww)) }
        var best = Match(-1.0, 0, 0)
        for (oy in 0..(img.height - th)) {
            for (ox in 0..(ww - tw)) {
                var sum = 0.0
                for (ty in 0 until th) {
                    val row = (oy + ty) * ww + ox
                    for (tx in 0 until tw) sum += src[row + tx]
                }
                val mean = sum / (tw * th)
                var cross = 0.0
                var sq = 0.0
                for (ty in 0 until th) {
                    val row = (oy + ty) * ww + ox
                    for (tx in 0 until tw) {
                        val d = src[row + tx] - mean
                        cross += d * t[ty * tw + tx]
                        sq += d * d
                    }
                }
                val score = if (sq == 0.0) 0.0 else cross / (sqrt(sq) * tNorm)
                if (score > best.score) best = Match(score, ox + tw / 2, bandTop + oy + th / 2)
            }
        }
        return best
    }

    @Test
    fun `the label matches at the bubble on every settled frame`() {
        val tmpl = template()
        for ((name, center) in settled) {
            val m = bestMatch(image(name), tmpl)
            assertTrue(m.score >= 0.95, "$name scored ${m.score}")
            assertTrue(abs(m.cx - center.first) <= 3 && abs(m.cy - center.second) <= 3, "$name matched at (${m.cx}, ${m.cy}), expected $center")
        }
    }

    @Test
    fun `the label stays under the confidence floor while the bubble is still sliding between tiles`() {
        val m = bestMatch(image(tween), template())
        assertTrue(m.score < confidenceFloor - 0.2, "$tween scored ${m.score} at (${m.cx}, ${m.cy})")
    }
}
