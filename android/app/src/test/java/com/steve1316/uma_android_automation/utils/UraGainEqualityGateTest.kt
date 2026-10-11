package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.opencv.core.Point
import java.io.File

/** URA phone gain bands and the boxes the matcher judged in them (src/test/resources/fixtures/uragain/PROVENANCE.md). */
@DisplayName("URA training gains read their + and 1")
class UraGainEqualityGateTest {
    /** A judged box. The searched shares are the matcher's own: earlier rejected boxes of the same template are painted black in its copy. */
    private class Box(
        val fixture: String,
        val truth: String,
        val template: String,
        val x: Int,
        val y: Int,
        val whole: Double,
        val opaque: Double,
        val searchedWhole: Double,
        val searchedOpaque: Double,
        val pearson: Double,
    )

    private class Gray(val width: Int, val height: Int, val gray: IntArray, val alpha: ByteArray)

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found")
    }

    /** The bot's gray: COLOR_BGR2GRAY (OpenCV 4.12's 16-bit weights) on Android's RGBA, so red takes the blue weight. */
    private fun gray(r: Int, g: Int, b: Int): Int = (r * 7470 + g * 38470 + b * 19596 + 32768) shr 16

    /** Android decodes the template premultiplied and bitmapToMat keeps it. */
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

    private val bands = mutableMapOf<String, Gray>()

    private fun band(name: String): Gray =
        bands.getOrPut(name) {
            val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/uragain/$name.png")) { "missing fixture $name.png" }
            stream.use { read(FixturePng.read(it)) }
        }

    private val templates = mutableMapOf<String, Gray>()

    private fun template(name: String): Gray = templates.getOrPut(name) { repoFile("android/app/src/main/assets/images/$name.png").inputStream().use { read(FixturePng.read(it)) } }

    private val boxes: List<Box> by lazy {
        val lines = requireNotNull(javaClass.getResourceAsStream("/fixtures/uragain/candidates.csv")).bufferedReader().readLines()
        val header = lines.first().split(",")
        lines.drop(1).filter { it.isNotBlank() }.map { line ->
            val c = header.zip(line.split(",")).toMap()

            fun d(key: String) = c.getValue(key).toDouble()
            Box(
                c.getValue("fixture"),
                c.getValue("truth"),
                c.getValue("template"),
                c.getValue("x").toInt(),
                c.getValue("y").toInt(),
                d("whole_ratio"),
                d("opaque_ratio"),
                d("searched_whole_ratio"),
                d("searched_opaque_ratio"),
                d("pearson"),
            )
        }
    }

    /** Non-zero where the band's gray equals the template's, as Core.compare(CMP_EQ) gives it. */
    private fun equal(box: Box): ByteArray {
        val b = band(box.fixture)
        val t = template(box.template)
        return ByteArray(t.width * t.height) { i -> if (b.gray[(box.y + i / t.width) * b.width + box.x + i % t.width] == t.gray[i]) -1 else 0 }
    }

    private fun wholeBox(equal: ByteArray): Double = equal.count { it.toInt() != 0 }.toDouble() / equal.size

    private val minEqual = 0.1
    private val minPearson = 0.85

    /** The digits after the "+" that the matcher builds from the boxes that pass, as constructIntegerFromMatches joins them. */
    private fun reading(fixture: String, passes: (Box) -> Boolean): String {
        val centres =
            boxes.filter { it.fixture == fixture && it.pearson >= minPearson && passes(it) }.associate { box ->
                val t = template(box.template)
                StatGainDigits.TemplateMatch(box.template, (box.x + t.width / 2).toDouble(), box.pearson) to Point((box.x + t.width / 2).toDouble(), (box.y + t.height / 2).toDouble())
            }
        val kept = StatGainDigits.keepBestPerGlyph(centres.keys.toList(), StatGainDigits.SAME_GLYPH_MAX_DX.toDouble())
        val onLine = StatGainDigits.onBaseline(kept.map { it.template to centres.getValue(it) }, StatGainDigits.BASELINE_MAX_DY.toDouble())
        return onLine.joinToString("") { it.first }.substringAfter("+")
    }

    @Test
    @DisplayName("The fixtures give the bot's own equal-pixel shares")
    fun sharesMatchTheReplay() {
        for (box in boxes) {
            val equal = equal(box)
            val alpha = template(box.template).alpha
            assertEquals(box.whole, wholeBox(equal), 6e-4, "${box.fixture} ${box.template}@${box.x}")
            assertEquals(box.opaque, StatGainDigits.equalRatio(box.template, equal, alpha), 6e-4, "${box.fixture} ${box.template}@${box.x}")
        }
    }

    @Test
    @DisplayName("Every band reads the gain the screen shows, and the outfit art reads nothing")
    fun everyBandReadsItsGain() {
        val clean = { box: Box -> StatGainDigits.equalRatio(box.template, equal(box), template(box.template).alpha) >= minEqual }
        val searched = { box: Box -> box.searchedOpaque >= minEqual }
        for (fixture in boxes.map { it.fixture }.distinct()) {
            val truth = boxes.first { it.fixture == fixture }.truth
            val expected = if (truth == "empty") "" else truth.removePrefix("+")
            assertEquals(expected, reading(fixture, clean), fixture)
            assertEquals(expected, reading(fixture, searched), "$fixture in the matcher's painted copy")
        }
    }

    @Test
    @DisplayName("Over the whole box the same bands lost their + and 1, and a stray 7 passed")
    fun wholeBoxGateMisread() {
        val gate = { box: Box -> box.searchedWhole >= minEqual }
        assertEquals("4", reading("phone_ura_stamina_14", gate), "the bot's logged read of this frame")
        assertEquals("", reading("phone_ura_power_11", gate))
        assertEquals("3", reading("phone_ura_stamina_13", gate))
        assertEquals("9", reading("phone_ura_power_19", gate))
        assertEquals("7", reading("phone_ura_wit_empty", gate))
        assertEquals("7", reading("phone_ura_stamina_empty", gate))
    }

    @Test
    @DisplayName("Black paint over a rejected box no longer counts as a match")
    fun paintedPixelsDoNotPass() {
        // Transparent template pixels are 0 once premultiplied, like the black boxes the matcher paints over rejected matches.
        val alpha = byteArrayOf(-1, -1, 0, 0, 0, 0, 0, 0)
        val equal = byteArrayOf(0, 0, -1, -1, -1, -1, -1, -1)
        assertEquals(0.75, StatGainDigits.equalRatio("7_mini", equal, alpha))
        assertEquals(0.0, StatGainDigits.equalRatio("7", equal, alpha))
    }

    @Test
    @DisplayName("Two-row templates keep the whole-box share")
    fun twoRowTemplatesUnchanged() {
        for (box in boxes) {
            val equal = equal(box)
            val alpha = template(box.template).alpha
            assertEquals(wholeBox(equal), StatGainDigits.equalRatio("${box.template}_mini", equal, alpha), "${box.fixture} ${box.template}@${box.x}")
            assertEquals(wholeBox(equal), StatGainDigits.equalRatio("${box.template}_mini_bold", equal, alpha), "${box.fixture} ${box.template}@${box.x}")
        }
        val alpha = byteArrayOf(-1, -1, 0, 0)
        val equal = byteArrayOf(-1, 0, -1, -1)
        assertEquals(0.75, StatGainDigits.equalRatio("4_mini", equal, alpha))
        assertEquals(0.5, StatGainDigits.equalRatio("4", equal, alpha))
        assertEquals(0.0, StatGainDigits.equalRatio("4", equal, byteArrayOf(0, 0, 100, 0)), "no opaque pixel never passes")
    }

    @Test
    @DisplayName("The matcher judges each box with the shared rule")
    fun matcherUsesTheRule() {
        val source = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/utils/CustomImageUtils.kt").readText().replace("\r\n", "\n")
        val matcher = source.substringAfter("private fun processStatGainTemplateWithTransparency(").substringBefore("\n    }\n")
        assertTrue(matcher.contains("val pixelMatchRatio = StatGainDigits.equalRatio(templateName, equalPixels, alphaPixels)"))
        assertTrue(matcher.contains("if (pixelMatchRatio < minPixelMatchRatio) {"))
        assertTrue(matcher.contains("val minPixelMatchRatio = 0.1"))
        assertFalse(matcher.contains("/ (w * h)"), "no whole-box share outside the rule")
    }
}
