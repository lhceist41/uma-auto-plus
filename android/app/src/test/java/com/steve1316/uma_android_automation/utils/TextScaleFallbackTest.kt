package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.sqrt

/**
 * Text labels drawn smaller than 1080x1920 are retried at 0.96, 0.92 and 0.88 after a miss at 1.0, same 0.8 bar. A label drawn at
 * full size must never trigger a retry; labels whose false hit could spend, choose or flip a screen decision never get one.
 */
@DisplayName("Text scale fallback")
class TextScaleFallbackTest {
    private val confidence = 0.8
    private val header = "components/label/stat_table_header_skill_points"

    private fun root(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "android/app/src/main/assets").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found")
    }

    private fun fixture(name: String): FixturePng =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/statheader/$name.png")) { "missing fixture $name.png" }.use { FixturePng.read(it) }

    private val template: FixturePng =
        File(root(), "android/app/src/main/assets/images/$header.png").inputStream().use { FixturePng.read(it) }

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

    /** Bilinear resize to the library's truncated size, like the template scaling in its match(). */
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

    /** Best TM_CCOEFF_NORMED score of [t] anywhere in [img]. */
    private fun bestScore(img: Gray, t: Gray): Double {
        val tMean = t.v.average()
        val tc = DoubleArray(t.v.size) { t.v[it] - tMean }
        val tNorm = sqrt(tc.sumOf { it * it })
        var best = -1.0
        for (oy in 0..(img.height - t.height)) {
            for (ox in 0..(img.width - t.width)) {
                var sum = 0.0
                for (ty in 0 until t.height) for (tx in 0 until t.width) sum += img.v[(oy + ty) * img.width + ox + tx]
                val mean = sum / t.v.size
                var cross = 0.0
                var sq = 0.0
                for (ty in 0 until t.height) {
                    for (tx in 0 until t.width) {
                        val d = img.v[(oy + ty) * img.width + ox + tx] - mean
                        cross += d * tc[ty * t.width + tx]
                        sq += d * d
                    }
                }
                val score = if (sq == 0.0) 0.0 else cross / (sqrt(sq) * tNorm)
                if (score > best) best = score
            }
        }
        return best
    }

    /** The override's flow on one frame: 1.0 as today, then the fallback; returns (scale, score) of the hit and every retry scale tried. */
    private fun lookUp(
        state: TextScaleFallback,
        frame: Gray,
    ): Pair<Pair<Double, Double>?, List<Double>> {
        val tmpl = gray(template)
        val atOne = bestScore(frame, tmpl)
        if (atOne >= confidence) {
            state.matchedAtFullSize(header)
            return (1.0 to atOne) to emptyList()
        }
        val tried = mutableListOf<Double>()
        val hit =
            state.afterMiss(header) { scale ->
                tried.add(scale)
                bestScore(frame, scaled(tmpl, scale)).takeIf { it >= confidence }
            }
        return hit?.let { it.scale to it.value } to tried
    }

    @Test
    @DisplayName("a frame drawn at 1080x1920 size matches at 1.0, and later misses of that label get no retry")
    fun fullSizeNeedsNoRetry() {
        val state = TextScaleFallback()
        val (hit, tried) = lookUp(state, gray(fixture("header_1080x1920")))
        assertEquals(1.0, hit!!.first)
        assertTrue(hit.second >= 0.95, "scored ${hit.second}")
        assertEquals(emptyList<Double>(), tried)
        assertEquals(emptyList<Double>(), state.scalesAfterMiss(header), "a real absence later costs nothing")
    }

    @Test
    @DisplayName("a frame whose text is drawn at 0.92 misses at 1.0 and is found by the retry, in the scale order")
    fun smallerTextIsFound() {
        val frame = gray(fixture("header_scaled_092"))
        val tmpl = gray(template)
        assertTrue(bestScore(frame, tmpl) < confidence, "the 1.0 lookup must miss, as on the phone")
        val firstPassing = TextScaleFallback.SCALES.first { bestScore(frame, scaled(tmpl, it)) >= confidence }
        val (hit, tried) = lookUp(TextScaleFallback(), frame)
        assertNotNull(hit)
        assertEquals(firstPassing, hit!!.first)
        assertTrue(hit.first <= 0.96 && hit.second >= confidence)
        assertEquals(TextScaleFallback.SCALES.takeWhile { it != firstPassing } + firstPassing, tried)
        assertTrue(bestScore(frame, scaled(tmpl, 0.92)) >= 0.9, "0.92 is the drawn size")
    }

    @Test
    @DisplayName("the phone's own frame: the header misses at 1.0 and the retry finds it at 0.92")
    fun phoneFrame() {
        val frame = gray(fixture("header_a13_1080x2408"))
        val tmpl = gray(template)
        assertTrue(bestScore(frame, tmpl) < confidence)
        val (hit, tried) = lookUp(TextScaleFallback(), frame)
        assertEquals(0.92, hit!!.first)
        assertTrue(hit.second >= 0.85, "scored ${hit.second}")
        assertEquals(listOf(0.96, 0.92), tried)
    }

    @Test
    @DisplayName("the retry keeps the 0.8 bar: a smaller scale on a full-size frame is not accepted")
    fun retryKeepsTheBar() {
        val frame = gray(fixture("header_1080x1920"))
        assertTrue(bestScore(frame, scaled(gray(template), 0.88)) < confidence)
        assertTrue(bestScore(frame, scaled(gray(template), 0.92)) < confidence)
        assertEquals(listOf(0.96, 0.92, 0.88), TextScaleFallback.SCALES)
    }

    @Test
    @DisplayName("after a hit the label retries only its learned scale, and only the run's first hit is logged")
    fun learnedScale() {
        val state = TextScaleFallback()
        val first = state.afterMiss(header) { if (it == 0.92) "found" else null }
        assertEquals(0.92, first!!.scale)
        assertTrue(first.firstOfRun)
        assertEquals(listOf(0.92), state.scalesAfterMiss(header))
        val again = state.afterMiss("components/label/energy") { if (it == 0.96) "found" else null }
        assertFalse(again!!.firstOfRun)
    }

    @Test
    @DisplayName("a label never seen stops retrying after three full misses")
    fun boundedMisses() {
        val state = TextScaleFallback()
        var attempts = 0
        repeat(5) {
            assertNull(
                state.afterMiss<String>(header) {
                    attempts++
                    null
                },
            )
        }
        assertEquals(TextScaleFallback.MAX_FULL_MISSES * TextScaleFallback.SCALES.size, attempts)
        assertEquals(emptyList<Double>(), state.scalesAfterMiss(header))
    }

    @Test
    @DisplayName("buttons, icons and labels whose false hit could change a decision never get a retry")
    fun excludedTemplates() {
        val state = TextScaleFallback()
        for (t in listOf(
            "components/button/training",
            "components/icon/tazuna",
            "components/label/stat_aptitude_A",
            "components/label/stat_maxed",
            "components/label/skill_list_screen_skill_points",
            "components/label/skill_list_screen_skill_points_v2",
            "components/label/support_formation",
            "components/label/event_boost_off",
            "components/label/scenario_select_ura",
            "components/label/scheduled_race",
            "components/label/race_criteria_fans",
            "components/label/now_loading",
            "components/label/connecting",
            "components/label/multi_race_popup",
            "components/label/item_request_held",
            "components/label/on_sale",
            "components/label/training_cannot_perform",
            "components/label/first_place",
            "components/label/umamusume_class_fans",
        )) {
            assertEquals(emptyList<Double>(), state.scalesAfterMiss(t), t)
            assertNull(state.afterMiss<String>(t) { error("no retry for $t") })
        }
    }

    @Test
    @DisplayName("every allowed label is a real template, and the override only widens the default 1080-wide lookup")
    fun wiring() {
        for (path in TextScaleFallback.LABELS) assertTrue(File(root(), "android/app/src/main/assets/images/$path.png").isFile, path)
        val imageUtils = File(root(), "android/app/src/main/java/com/steve1316/uma_android_automation/utils/CustomImageUtils.kt").readText().replace("\r\n", "\n")
        val override = imageUtils.substring(imageUtils.indexOf("override fun match("), imageUtils.indexOf("/** Converts this bitmap to a new OpenCV [Mat]. */"))
        val guard = override.indexOf("if (testScale != 0.0 || customScale != 1.0 || !is1080p) return result")
        assertTrue(guard > 0)
        assertTrue(override.indexOf("textScaleFallback.matchedAtFullSize(templateName)") > guard)
        assertTrue(override.indexOf("textScaleFallback.afterMiss(templateName)") > override.indexOf("if (result.first) {"))
        assertTrue(override.contains("customConfidence, scale).takeIf { it.first }"), "the retry keeps the caller's confidence")
        assertTrue(override.contains("if (hit.firstOfRun) {") && override.contains("[LAYOUT] Text scale"))
        assertFalse(imageUtils.contains("override fun matchAll("), "multi-hit counts stay single-scale")
    }

    @Test
    @DisplayName("the Sparks keep confirmation false-hits stat_distance at 0.96, so that label stays behind the Details dialog")
    fun sparksFalseHit() {
        val frame = requireNotNull(javaClass.getResourceAsStream("/fixtures/sparks/keep_confirmation_12row_top.png")) { "missing sparks fixture" }.use { FixturePng.read(it) }
        // "Standard Distance" spark name around (247, 889); scored in a window there, inside the label's top-half region.
        val window = gray(frame).let { g -> Gray(350, 170, DoubleArray(350 * 170) { g.v[(820 + it / 350) * g.width + 150 + it % 350] }) }
        val tmpl = gray(File(root(), "android/app/src/main/assets/images/components/label/stat_distance.png").inputStream().use { FixturePng.read(it) })
        assertTrue(bestScore(window, tmpl) < confidence, "the 1.0 lookup misses, as today")
        val hit = TextScaleFallback().afterMiss("components/label/stat_distance") { scale -> bestScore(window, scaled(tmpl, scale)).takeIf { it >= confidence } }
        assertEquals(0.96, hit!!.scale, "the retry would accept the spark name if asked on this screen")
        val base = "android/app/src/main/java/com/steve1316/uma_android_automation"
        val all = File(root(), base).walkTopDown().filter { it.extension == "kt" && it.name != "Label.kt" }.associate { it.name to it.readText().replace("\r\n", "\n") }
        assertEquals(listOf("Trainee.kt"), all.filter { Regex("""\bLabelStatDistance\b""").containsMatchIn(it.value) }.keys.toList())
        val trainee = all.getValue("Trainee.kt")
        assertEquals(1, Regex("""label = LabelStatDistance\)""").findAll(trainee).count())
        assertEquals(1, Regex("""updateTrackDistanceAptitudes\(imageUtils = imageUtils\)""").findAll(trainee).count(), "only updateAptitudes calls the distance read")
        val callers = all.filter { Regex("""\.updateAptitudes\(""").containsMatchIn(it.value) }
        assertEquals(listOf("Campaign.kt"), callers.keys.toList())
        val campaign = callers.getValue("Campaign.kt")
        val call = campaign.indexOf("trainee.updateAptitudes(game.imageUtils)")
        assertEquals(1, Regex("""\.updateAptitudes\(""").findAll(campaign).count())
        val branch = Regex(""""(\w+)" -> \{""").findAll(campaign.substring(0, call)).last().groupValues[1]
        assertEquals("umamusume_details", branch, "the one caller is the Details dialog branch")
    }
}
