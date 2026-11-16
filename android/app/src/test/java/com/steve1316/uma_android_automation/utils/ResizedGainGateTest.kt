package com.steve1316.uma_android_automation.utils

import com.steve1316.automation_library.data.SharedData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.opencv.core.Point
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Training gain bands seen through resized captures (src/test/resources/fixtures/resizedgain/PROVENANCE.md). Unit tests have no
 * OpenCV, so this replays processStatGainTemplateWithTransparency on the bot's gray: the masked TM_CCORR_NORMED score map, the
 * peak search, both pixel gates, and the row's one-match-per-glyph and "+" grid filters.
 */
@DisplayName("Training gains read on resized captures")
class ResizedGainGateTest {
    private class Gray(val width: Int, val height: Int, val gray: IntArray, val alpha: ByteArray)

    /** A box the matcher judged: top-left in the row, masked correlation, equal-pixel share and pixel correlation. */
    private class Judged(val template: String, val x: Int, val y: Int, val ccorr: Double, val share: Double, val pearson: Double)

    /** One gain row and the gain the screen shows (0 for none); [load] gives the row as a capture of that width shows it. */
    private class Band(val label: String, val suffix: String, val truth: Int, val load: (Int) -> Gray)

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found")
    }

    /** The bot's gray: COLOR_BGR2GRAY on Android's RGBA, so red takes the blue weight. */
    private fun gray(r: Int, g: Int, b: Int): Int = (r * 7470 + g * 38470 + b * 19596 + 32768) shr 16

    /** Android decodes templates premultiplied and bitmapToMat keeps it; the opaque bands are unchanged by it. */
    private fun read(png: FixturePng): Gray {
        val n = png.width * png.height
        val gray = IntArray(n)
        val alpha = ByteArray(n)
        for (y in 0 until png.height) {
            for (x in 0 until png.width) {
                val p = png.getRGB(x, y)
                val a = (p ushr 24) and 0xFF

                fun premultiplied(c: Int) = Math.round(c * a / 255.0).toInt()
                gray[y * png.width + x] = gray(premultiplied((p shr 16) and 0xFF), premultiplied((p shr 8) and 0xFF), premultiplied(p and 0xFF))
                alpha[y * png.width + x] = a.toByte()
            }
        }
        return Gray(png.width, png.height, gray, alpha)
    }

    private fun crop(
        g: Gray,
        x0: Int,
        y0: Int,
        w: Int,
        h: Int,
    ): Gray = Gray(w, h, IntArray(w * h) { g.gray[(y0 + it / w) * g.width + x0 + it % w] }, ByteArray(w * h) { g.alpha[(y0 + it / w) * g.width + x0 + it % w] })

    private val images = mutableMapOf<String, Gray>()

    private fun fixture(path: String): Gray =
        images.getOrPut(path) {
            requireNotNull(javaClass.getResourceAsStream(path)) { "missing fixture $path" }.use { read(FixturePng.read(it)) }
        }

    private fun template(name: String): Gray = images.getOrPut("template $name") { repoFile("android/app/src/main/assets/images/$name.png").inputStream().use { read(FixturePng.read(it)) } }

    private val ura =
        linkedMapOf(
            "phone_ura_stamina_14" to 14,
            "phone_ura_guts_7" to 7,
            "phone_ura_power_11" to 11,
            "phone_ura_stamina_13" to 13,
            "phone_ura_power_19" to 19,
            "phone_ura_guts_10_plus_clipped" to 10,
            "phone_ura_power_18" to 18,
            "phone_ura_speed_24" to 24,
            "phone_ura_wit_empty" to 0,
            "phone_ura_stamina_empty" to 0,
        )

    /** The stat column's two rows, 150x110 from y 14 of the band: row 2 ("_mini_bold") on top, row 1 ("_mini") below. */
    private fun twoRowWindow(
        name: String,
        column: Int,
        width: Int,
    ): Gray = if (width == 1080) crop(fixture("/fixtures/statgain/$name.png"), 37 + 180 * column, 14, 150, 110) else fixture("/fixtures/resizedgain/${name}_col${column}_w$width.png")

    private val bands: List<Band> by lazy {
        val single =
            ura.map { (name, truth) ->
                Band(name, "", truth) { width -> fixture(if (width == 1080) "/fixtures/uragain/$name.png" else "/fixtures/resizedgain/${name}_w$width.png") }
            }
        val twoRow =
            listOf(Triple("gold_speed_1281", 2, 31 to 17), Triple("gold_speed_1221", 2, 19 to 10), Triple("orange_speed_1169_guts", 0, 2 to 1), Triple("wit_art_between_digits", 4, 10 to null))
                .flatMap { (name, column, truths) ->
                    listOfNotNull(
                        Band("$name row 1", "_mini", truths.first) { width -> crop(twoRowWindow(name, column, width), 0, 55, 150, 55) },
                        truths.second?.let { truth -> Band("$name row 2", "_mini_bold", truth) { width -> crop(twoRowWindow(name, column, width), 0, 0, 150, 55) } },
                    )
                }
        single + twoRow
    }

    /** CustomImageUtils.calculateCorrelation. */
    private fun correlation(
        a: DoubleArray,
        b: DoubleArray,
    ): Double {
        val n = a.size
        val sumA = a.sum()
        val sumB = b.sum()
        val num = a.indices.sumOf { a[it] * b[it] } - sumA * sumB / n
        val den = sqrt((a.sumOf { it * it } - sumA * sumA / n) * (b.sumOf { it * it } - sumB * sumB / n))
        return if (den == 0.0) 0.0 else num / den
    }

    /** The search for one template; [judged] receives every box it judged, the result is the valid matches (template match and centre). */
    private fun search(
        name: String,
        row: Gray,
        grayLevels: Int,
        judged: MutableList<Judged>? = null,
    ): List<Pair<StatGainDigits.TemplateMatch, Point>> {
        val t = template(name)
        val w = t.width
        val h = t.height
        if (row.width < w || row.height < h) return emptyList()
        val px = row.gray
        val opaque = (0 until w * h).filter { t.alpha[it].toInt() != 0 }
        val offsets = IntArray(opaque.size) { (opaque[it] / w) * row.width + opaque[it] % w }
        val values = IntArray(opaque.size) { t.gray[opaque[it]] }
        val templateEnergy = values.sumOf { it.toLong() * it }.toDouble()

        // An 8-bit mask is binary in OpenCV's masked TM_CCORR_NORMED: sum(T * I) / sqrt(sum(T^2) * sum(I^2)) over the opaque pixels.
        fun ccorr(
            x: Int,
            y: Int,
        ): Double {
            val origin = y * row.width + x
            var cross = 0L
            var energy = 0L
            for (k in offsets.indices) {
                val v = px[origin + offsets[k]]
                cross += v.toLong() * values[k]
                energy += v.toLong() * v
            }
            val den = sqrt(templateEnergy * energy)
            return if (den == 0.0) 0.0 else cross / den
        }

        val cols = row.width - w + 1
        val scores = FloatArray(cols * (row.height - h + 1)) { ccorr(it % cols, it / cols).toFloat() }
        val hits = mutableListOf<Pair<StatGainDigits.TemplateMatch, Point>>()
        StatGainDigits.searchPeaks(scores, cols, w, h, 0.9, name == "+" || name == "+_mini") { bx, by ->
            fun at(i: Int) = px[(by + i / w) * row.width + bx + i % w]
            val equal = ByteArray(w * h) { if (abs(at(it) - t.gray[it]) <= grayLevels) -1 else 0 }
            val share = StatGainDigits.equalRatio(name, equal, t.alpha)
            val templateMasked = DoubleArray(w * h) { if (t.alpha[it].toInt() != 0) t.gray[it].toDouble() else 0.0 }
            val regionMasked = DoubleArray(w * h) { if (t.alpha[it].toInt() != 0) at(it).toDouble() else 0.0 }
            val pearson = correlation(templateMasked, regionMasked)
            judged?.add(Judged(name, bx, by, scores[by * cols + bx].toDouble(), share, pearson))
            val cx = bx + w / 2
            val cy = by + h / 2
            if (share < 0.1 || pearson < 0.85 || hits.any { abs(it.second.x - cx) < 10 && abs(it.second.y - cy) < 10 }) return@searchPeaks false
            hits += StatGainDigits.TemplateMatch(name, cx.toDouble(), pearson) to Point(cx.toDouble(), cy.toDouble())
            true
        }
        return hits
    }

    /** The gain the bot builds from the row's valid matches, as determineStatGainFromTraining and constructIntegerFromMatches do. */
    private fun gain(
        row: Gray,
        suffix: String,
        grayLevels: Int,
    ): Int {
        val found = "+0123456789".flatMap { search("$it$suffix", row, grayLevels) }
        val points = found.toMap()
        val pitch =
            when (suffix) {
                "_mini" -> StatGainDigits.GLYPH_PITCH_ROW_1
                "_mini_bold" -> StatGainDigits.GLYPH_PITCH_ROW_2
                else -> StatGainDigits.GLYPH_PITCH_SINGLE_ROW
            }
        val kept = StatGainDigits.keepBestPerGlyph(found.map { it.first }, StatGainDigits.SAME_GLYPH_MAX_DX.toDouble())
        val onGrid = StatGainDigits.onPlusGrid(kept, pitch.toDouble(), StatGainDigits.GRID_MAX_DX.toDouble())
        val sorted = onGrid.map { it.template to points.getValue(it) }.sortedBy { it.second.x }
        val text = StatGainDigits.onBaseline(sorted, StatGainDigits.BASELINE_MAX_DY.toDouble()).joinToString("") { it.first[0].toString() }
        if (text.isEmpty() || text == "+") return 0
        val plus = text.indexOf('+')
        val value = (if (plus != -1 && plus < text.length - 1) text.substring(plus + 1) else text).toIntOrNull() ?: return 0
        return if (value > 100) value / 10 else value
    }

    private val resizedLevels = StatGainDigits.equalGrayLevels(resized = true)
    private val nativeLevels = StatGainDigits.equalGrayLevels(resized = false)

    @Test
    @DisplayName("The replay judges the boxes OpenCV judged in the URA bands")
    fun replayMatchesOpenCv() {
        val lines = requireNotNull(javaClass.getResourceAsStream("/fixtures/uragain/candidates.csv")).bufferedReader().readLines()
        val header = lines.first().split(",")
        val expected = lines.drop(1).filter { it.isNotBlank() }.map { header.zip(it.split(",")).toMap() }
        var compared = 0
        for (name in ura.keys) {
            val judged = mutableListOf<Judged>()
            "+0123456789".forEach { search(it.toString(), fixture("/fixtures/uragain/$name.png"), 0, judged) }
            val rows = expected.filter { it.getValue("fixture") == name }
            // The csv came from a former matcher that crossed the row left to right; both start at each template's best box.
            assertEquals(
                rows.groupBy { it.getValue("template") }.mapValues { (_, r) -> "${r[0]["x"]},${r[0]["y"]}" },
                judged.groupBy { it.template }.mapValues { (_, j) -> "${j[0].x},${j[0].y}" },
                name,
            )
            for (box in judged) {
                val row = rows.firstOrNull { it["template"] == box.template && it["x"] == "${box.x}" && it["y"] == "${box.y}" } ?: continue
                compared++
                assertEquals(row.getValue("ccorr").toDouble(), box.ccorr, 2e-3, "$name ${box.template}@${box.x}")
                assertEquals(row.getValue("opaque_ratio").toDouble(), box.share, 6e-4, "$name ${box.template}@${box.x}")
                // Its Pearson was taken in the former matcher's copy, where black paint can cover part of a box.
                if (row["whole_ratio"] == row["searched_whole_ratio"]) assertEquals(row.getValue("pearson").toDouble(), box.pearson, 1e-3, "$name ${box.template}@${box.x}")
            }
        }
        assertEquals(142, compared)
    }

    @Test
    @DisplayName("Through 720 and 1440 wide captures every band reads the gain the screen shows")
    fun resizedBandsReadTheirGain() {
        assertEquals(4, resizedLevels)
        for (width in listOf(720, 1440)) {
            for (band in bands) assertEquals(band.truth, gain(band.load(width), band.suffix, resizedLevels), "${band.label} at $width")
        }
    }

    @Test
    @DisplayName("With the 1080 tolerance or exact equality the resized bands lose gains")
    fun nativeGateMissesResizedBands() {
        fun wrong(levels: Int) =
            listOf(720, 1440).flatMap { width ->
                bands.filter { it.truth != 0 }.mapNotNull { band -> gain(band.load(width), band.suffix, levels).takeIf { it != band.truth }?.let { "${band.label} at $width: $it" } }
            }
        assertEquals(listOf("gold_speed_1281 row 2 at 720: 1"), wrong(nativeLevels))
        assertEquals(
            listOf(
                "phone_ura_stamina_14 at 720: 4",
                "phone_ura_stamina_13 at 720: 0",
                "phone_ura_speed_24 at 720: 4",
                "gold_speed_1281 row 1 at 720: 0",
                "gold_speed_1281 row 2 at 720: 0",
                "gold_speed_1221 row 1 at 720: 0",
                "gold_speed_1221 row 2 at 720: 0",
                "orange_speed_1169_guts row 1 at 720: 0",
                "orange_speed_1169_guts row 2 at 720: 0",
                "wit_art_between_digits row 1 at 720: 0",
                "gold_speed_1281 row 1 at 1440: 0",
                "gold_speed_1281 row 2 at 1440: 0",
                "gold_speed_1221 row 2 at 1440: 0",
                "orange_speed_1169_guts row 1 at 1440: 0",
                "orange_speed_1169_guts row 2 at 1440: 0",
                "wit_art_between_digits row 1 at 1440: 0",
            ),
            wrong(0),
        )
    }

    @Test
    @DisplayName("A 1080 wide capture keeps the 1080 tolerance and reads every band")
    fun nativeBandsKeepTheirTolerance() {
        assertEquals(StatGainDigits.EQUAL_GRAY_LEVELS, nativeLevels)
        for (band in bands) assertEquals(band.truth, gain(band.load(1080), band.suffix, nativeLevels), band.label)
        // One level more than the resized value lets the stamina band's outfit art read as a gain.
        val art = bands.first { it.label == "phone_ura_stamina_empty" }
        for (width in listOf(720, 1080, 1440)) assertEquals(7, gain(art.load(width), art.suffix, resizedLevels + 1), "at $width")
    }

    @Test
    @DisplayName("Only a capture that is resized counts as resized")
    fun resizedFollowsTheCaptureWidth() {
        val saved = SharedData.displayWidth to SharedData.displayHeight
        try {
            for ((size, resized) in listOf((1080 to 1920) to false, (1080 to 2316) to false, (720 to 1280) to true, (1440 to 2560) to true, (0 to 0) to false)) {
                SharedData.displayWidth = size.first
                SharedData.displayHeight = size.second
                assertEquals(resized, CanonicalScreen.resized, "$size")
            }
        } finally {
            SharedData.displayWidth = saved.first
            SharedData.displayHeight = saved.second
        }
    }

    @Test
    @DisplayName("The matcher picks the tolerance by capture and keeps its other gates")
    fun matcherWiring() {
        val source = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/utils/CustomImageUtils.kt").readText().replace("\r\n", "\n")
        val matcher = source.substringAfter("private fun processStatGainTemplateWithTransparency(").substringBefore("\n    }\n")
        assertTrue(matcher.contains("val equalGrayLevels = StatGainDigits.equalGrayLevels(CanonicalScreen.resized)"))
        assertTrue(matcher.contains("val equalPixels = StatGainDigits.withinGrayLevels(differences, equalGrayLevels)"))
        assertFalse(matcher.contains("StatGainDigits.EQUAL_GRAY_LEVELS"))
        assertTrue(matcher.contains("val matchConfidence = 0.9\n"))
        assertTrue(matcher.contains("val minPixelCorrelation = 0.85\n"))
        assertFalse(matcher.contains("equalGrayLevels(true)") || matcher.contains("equalGrayLevels(resized = true)"))
    }
}
