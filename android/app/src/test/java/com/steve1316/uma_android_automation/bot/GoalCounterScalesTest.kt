package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.FixturePng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.sqrt

/**
 * On a tall 1080-wide screen the "time(s) left" and "fan(s) to go" goal counters are also tried below template size, at the
 * same 0.9 bar; 1080x1920 keeps its single full-size lookup.
 */
@DisplayName("Goal counter scales")
class GoalCounterScalesTest {
    private val confidence = 0.9
    private val trophies = "race_criteria_trophies"
    private val fans = "race_criteria_fans"

    private fun root(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "android/app/src/main/assets").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found")
    }

    private fun fixture(name: String): Gray =
        gray(requireNotNull(javaClass.getResourceAsStream("/fixtures/goalcounter/$name.png")) { "missing fixture $name.png" }.use { FixturePng.read(it) })

    private fun template(name: String): Gray = gray(File(root(), "android/app/src/main/assets/images/components/label/$name.png").inputStream().use { FixturePng.read(it) })

    private class Gray(val width: Int, val height: Int, val v: DoubleArray)

    /** The matcher's grayscale: the library converts RGBA with BGR2GRAY, so red and blue weights are swapped. */
    private fun gray(img: FixturePng): Gray =
        Gray(
            img.width,
            img.height,
            DoubleArray(img.width * img.height) {
                val argb = img.getRGB(it % img.width, it / img.width)
                0.114 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.299 * (argb and 0xFF)
            },
        )

    /** Bilinear resize to the truncated size, like the template shrink in matchTemplateAtScales. */
    private fun scaled(g: Gray, scale: Double): Gray {
        if (scale == 1.0) return g
        val w = (g.width * scale).toInt()
        val h = (g.height * scale).toInt()
        val out = DoubleArray(w * h)
        for (y in 0 until h) {
            val sy = ((y + 0.5) * g.height / h - 0.5).coerceIn(0.0, g.height - 1.0)
            val y0 = sy.toInt()
            val y1 = minOf(y0 + 1, g.height - 1)
            val fy = sy - y0
            for (x in 0 until w) {
                val sx = ((x + 0.5) * g.width / w - 0.5).coerceIn(0.0, g.width - 1.0)
                val x0 = sx.toInt()
                val x1 = minOf(x0 + 1, g.width - 1)
                val fx = sx - x0
                val top = g.v[y0 * g.width + x0] * (1 - fx) + g.v[y0 * g.width + x1] * fx
                val bottom = g.v[y1 * g.width + x0] * (1 - fx) + g.v[y1 * g.width + x1] * fx
                out[y * w + x] = top * (1 - fy) + bottom * fy
            }
        }
        return Gray(w, h, out)
    }

    /** Best TM_CCOEFF_NORMED score of [t] anywhere in [img]; window sums come from integral images. */
    private fun bestScore(img: Gray, t: Gray): Double {
        val tMean = t.v.average()
        val tc = DoubleArray(t.v.size) { t.v[it] - tMean }
        val tNorm = sqrt(tc.sumOf { it * it })
        val iw = img.width + 1
        val sum = DoubleArray(iw * (img.height + 1))
        val sq = DoubleArray(iw * (img.height + 1))
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val p = img.v[y * img.width + x]
                val i = (y + 1) * iw + x + 1
                sum[i] = p + sum[i - 1] + sum[i - iw] - sum[i - iw - 1]
                sq[i] = p * p + sq[i - 1] + sq[i - iw] - sq[i - iw - 1]
            }
        }
        val n = t.v.size.toDouble()
        var best = -1.0
        for (oy in 0..(img.height - t.height)) {
            for (ox in 0..(img.width - t.width)) {
                val a = oy * iw + ox
                val b = (oy + t.height) * iw + ox
                val s = sum[b + t.width] - sum[b] - sum[a + t.width] + sum[a]
                val s2 = sq[b + t.width] - sq[b] - sq[a + t.width] + sq[a]
                val variance = s2 - s * s / n
                if (variance <= 1e-9) continue
                // The template is zero-mean, so the window mean drops out of the cross term.
                var cross = 0.0
                for (ty in 0 until t.height) {
                    val row = (oy + ty) * img.width + ox
                    val trow = ty * t.width
                    for (tx in 0 until t.width) cross += img.v[row + tx] * tc[trow + tx]
                }
                val score = cross / (sqrt(variance) * tNorm)
                if (score > best) best = score
            }
        }
        return best
    }

    /** The tall lookup's first passing factor in order, or null. */
    private fun firstPassing(
        frame: Gray,
        t: Gray,
    ): Double? = TALL_GOAL_COUNTER_SHRINK_FACTORS.firstOrNull { bestScore(frame, scaled(t, it)) >= confidence }

    @Test
    @DisplayName("only a 1080-wide screen taller than 1920 gets the smaller counter sizes")
    fun tallScreensOnly() {
        assertFalse(hasTallGoalCounters(1080, 1920))
        assertTrue(hasTallGoalCounters(1080, 2316))
        assertTrue(hasTallGoalCounters(1080, 2340))
        assertTrue(hasTallGoalCounters(1080, 2408))
        assertFalse(hasTallGoalCounters(720, 1280))
        assertFalse(hasTallGoalCounters(1440, 3120))
        assertFalse(hasTallGoalCounters(1080, 1800))
        assertEquals(listOf(1.0, 0.98, 0.96, 0.94, 0.92, 0.90, 0.88), TALL_GOAL_COUNTER_SHRINK_FACTORS)
    }

    @Test
    @DisplayName("MuMu 1080x1920 counters match at full size, so the tall lookup stops at 1.0 too")
    fun fullSizeCounter() {
        for ((name, label) in listOf("trophy_counter_1080x1920" to trophies, "fan_counter_1080x1920" to fans)) {
            val frame = fixture(name)
            val score = bestScore(frame, template(label))
            assertTrue(score >= 0.95, "$name scored $score")
            assertEquals(1.0, firstPassing(frame, template(label)), name)
        }
    }

    @Test
    @DisplayName("counters drawn at 0.94, 0.92 and 0.88 miss at full size and are found within 0.02 of their drawn size")
    fun smallerCounterIsFound() {
        for ((drawn, tag) in listOf(0.94 to "094", 0.92 to "092", 0.88 to "088")) {
            for ((counter, label) in listOf("trophy_counter" to trophies, "fan_counter" to fans)) {
                val name = "${counter}_scaled_$tag"
                val frame = fixture(name)
                val t = template(label)
                val atOne = bestScore(frame, t)
                assertTrue(atOne < confidence, "the 1.0 lookup must miss on $name, scored $atOne")
                val factor = requireNotNull(firstPassing(frame, t)) { "no factor found $name" }
                assertTrue(factor - drawn in -0.001..0.021, "$name first matched at $factor")
                assertTrue(bestScore(frame, scaled(t, drawn)) >= 0.93, "$drawn is the drawn size of $name")
            }
        }
    }

    @Test
    @DisplayName("no counter template reaches the bar at any factor where that counter is absent")
    fun noFalseHit() {
        val cases =
            listOf(
                "goal_box_no_counter_1080x2316" to trophies,
                "goal_box_no_counter_1080x2316" to fans,
                "trophy_counter_1080x1920" to fans,
                "trophy_counter_scaled_094" to fans,
                "fan_counter_1080x1920" to trophies,
                "fan_counter_scaled_094" to trophies,
            )
        for ((name, label) in cases) {
            val frame = fixture(name)
            val t = template(label)
            for (factor in TALL_GOAL_COUNTER_SHRINK_FACTORS) {
                val score = bestScore(frame, scaled(t, factor))
                assertTrue(score < 0.6, "$label on $name at $factor scored $score")
            }
        }
    }

    @Test
    @DisplayName("the tall branch is the only new path; 1080x1920 keeps the original single-size calls")
    fun wiring() {
        val racing = File(root(), "android/app/src/main/java/com/steve1316/uma_android_automation/bot/Racing.kt").readText().replace("\r\n", "\n")
        assertTrue(racing.contains("private fun hasTallGoalCounters(): Boolean = hasTallGoalCounters(SharedData.displayWidth, SharedData.displayHeight)"))
        assertTrue(racing.contains("label.findAtScales(game.imageUtils, sourceBitmap, TALL_GOAL_COUNTER_SHRINK_FACTORS, confidence = 0.9)"))
        assertEquals(1, Regex("""LabelRaceCriteriaFans\.check\(game\.imageUtils, sourceBitmap = sourceBitmapToUse, confidence = 0\.9\)""").findAll(racing).count())
        assertEquals(1, Regex("""LabelRaceCriteriaTrophies\.findImageWithBitmap\(game\.imageUtils, sourceBitmap = sourceBitmapToUse, confidence = 0\.9\)""").findAll(racing).count())
        assertEquals(1, Regex("""LabelRaceCriteriaFans\.check\(game\.imageUtils, confidence = 0\.9\)""").findAll(racing).count())
        assertEquals(3, Regex("""findTallGoalCounter\(LabelRaceCriteria(Fans|Trophies), """).findAll(racing).count())
        assertEquals(3, Regex("""if \((tallGoalCounters|hasTallGoalCounters\(\))\) \{\n\s+findTallGoalCounter\(""").findAll(racing).count(), "each counter call goes tall only behind the check")
        val utils = File(root(), "android/app/src/main/java/com/steve1316/uma_android_automation/utils/TextScaleFallback.kt").readText()
        assertFalse(utils.contains("race_criteria"), "race criteria stay out of the label text-scale fallback")
    }
}
