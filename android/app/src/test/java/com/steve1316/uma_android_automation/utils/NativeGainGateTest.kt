package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/** Native 1080 gain bands and every box the matcher searches in their columns (src/test/resources/fixtures/statgain/PROVENANCE.md). */
@DisplayName("Native 1080 training gains pass the equal-pixel gate")
class NativeGainGateTest {
    private class Box(
        val fixture: String,
        val stat: String,
        val row: String,
        val screen: String,
        val template: String,
        val x: Int,
        val y: Int,
        val pearson: Double,
        val share0: Double,
        val share2: Double,
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

    /** The bot's gray: COLOR_BGR2GRAY on Android's RGBA, so red takes the blue weight. */
    private fun gray(r: Int, g: Int, b: Int): Int = (r * 7470 + g * 38470 + b * 19596 + 32768) shr 16

    /** Bands are stored in true RGB; templates are decoded premultiplied, as Android does. */
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
            val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/statgain/$name.png")) { "missing fixture $name.png" }
            stream.use { read(FixturePng.read(it)) }
        }

    private val templates = mutableMapOf<String, Gray>()

    private fun template(name: String): Gray = templates.getOrPut(name) { repoFile("android/app/src/main/assets/images/$name.png").inputStream().use { read(FixturePng.read(it)) } }

    /** In the matcher's search order per template (the csv's rank). */
    private val boxes: List<Box> by lazy {
        val lines = requireNotNull(javaClass.getResourceAsStream("/fixtures/statgain/native_boxes.csv")).bufferedReader().readLines()
        val header = lines.first().split(",")
        lines.drop(1).filter { it.isNotBlank() }.map { line ->
            val c = header.zip(line.split(",")).toMap()
            Box(
                c.getValue("fixture"),
                c.getValue("stat"),
                c.getValue("row"),
                c.getValue("screen"),
                c.getValue("template"),
                c.getValue("x").toInt(),
                c.getValue("y").toInt(),
                c.getValue("pearson").toDouble(),
                c.getValue("share0").toDouble(),
                c.getValue("share2").toDouble(),
            )
        }
    }

    /** The absolute gray difference of a box, one unsigned byte per pixel, as Core.absdiff gives it. */
    private fun difference(box: Box): ByteArray {
        val b = band(box.fixture)
        val t = template(box.template)
        return ByteArray(t.width * t.height) { i -> abs(b.gray[(box.y + i / t.width) * b.width + box.x + i % t.width] - t.gray[i]).toByte() }
    }

    private fun share(box: Box, levels: Int): Double = StatGainDigits.equalRatio(box.template, StatGainDigits.withinGrayLevels(difference(box), levels), template(box.template).alpha)

    /** The matcher's Pearson over the template's non-transparent pixels, the others zero on both sides. */
    private fun pearson(box: Box): Double {
        val b = band(box.fixture)
        val t = template(box.template)
        val n = t.width * t.height
        val a = DoubleArray(n) { i -> if (t.alpha[i].toInt() != 0) t.gray[i].toDouble() else 0.0 }
        val r = DoubleArray(n) { i -> if (t.alpha[i].toInt() != 0) b.gray[(box.y + i / t.width) * b.width + box.x + i % t.width].toDouble() else 0.0 }
        val num = a.indices.sumOf { a[it] * r[it] } - a.sum() * r.sum() / n
        val den = sqrt((a.sumOf { it * it } - a.sum() * a.sum() / n) * (r.sumOf { it * it } - r.sum() * r.sum() / n))
        return if (den == 0.0) 0.0 else num / den
    }

    private val minEqual = 0.1
    private val minPearson = 0.85

    /** One row's matches as the matcher keeps them: accepted peaks per template, one match per glyph, then the "+" grid. */
    private fun kept(fixture: String, stat: String, row: String, levels: Int): List<StatGainDigits.TemplateMatch> {
        val scores = mutableListOf<StatGainDigits.TemplateMatch>()
        for ((name, searched) in boxes.filter { it.fixture == fixture && it.stat == stat && it.row == row }.groupBy { it.template }) {
            val accepted = mutableListOf<Box>()
            for (box in searched) {
                if (share(box, levels) < minEqual || pearson(box) < minPearson) continue
                if (accepted.any { abs(it.x - box.x) < 10 && abs(it.y - box.y) < 10 }) continue
                accepted.add(box)
                scores.add(StatGainDigits.TemplateMatch(name, (box.x + template(name).width / 2).toDouble(), pearson(box)))
                if (name.startsWith("+") || accepted.size >= StatGainDigits.MAX_MATCHES_PER_TEMPLATE) break
            }
        }
        val pitch = if (row == "row 1") StatGainDigits.GLYPH_PITCH_ROW_1 else StatGainDigits.GLYPH_PITCH_ROW_2
        val perGlyph = StatGainDigits.keepBestPerGlyph(scores, StatGainDigits.SAME_GLYPH_MAX_DX.toDouble())
        return StatGainDigits.onPlusGrid(perGlyph, pitch.toDouble(), StatGainDigits.GRID_MAX_DX.toDouble())
    }

    /** Each kept template's first character, as constructIntegerFromMatches joins them. */
    private fun reading(fixture: String, stat: String, row: String, levels: Int): String = kept(fixture, stat, row, levels).joinToString("") { it.template[0].toString() }

    private val cells: List<Triple<String, String, String>> by lazy { boxes.map { Triple(it.fixture, it.stat, it.row) }.distinct() }

    @Test
    @DisplayName("Two levels of gray difference count as equal, three do not")
    fun tolerance() {
        assertEquals(2, StatGainDigits.EQUAL_GRAY_LEVELS)
        val difference = byteArrayOf(0, 1, 2, 3, 6, 200.toByte(), 255.toByte())
        assertArrayEquals(byteArrayOf(-1, -1, -1, 0, 0, 0, 0), StatGainDigits.withinGrayLevels(difference, StatGainDigits.EQUAL_GRAY_LEVELS))
        assertArrayEquals(byteArrayOf(-1, 0, 0, 0, 0, 0, 0), StatGainDigits.withinGrayLevels(difference, 0), "0 is exact equality")
        val box = byteArrayOf(2, 3, 9, 9, 9, 9, 9, 9, 9, 9)
        assertEquals(0.1, StatGainDigits.equalRatio("3_mini", StatGainDigits.withinGrayLevels(box, StatGainDigits.EQUAL_GRAY_LEVELS), ByteArray(box.size) { -1 }))
    }

    @Test
    @DisplayName("The fixtures give the replay's shares and correlations")
    fun boxesMatchTheReplay() {
        assertTrue(boxes.size > 600)
        for (box in boxes) {
            val label = "${box.fixture} ${box.stat} ${box.row} ${box.template}@${box.x}"
            assertEquals(box.share0, share(box, 0), 6e-4, label)
            assertEquals(box.share2, share(box, 2), 6e-4, label)
            assertEquals(box.pearson, pearson(box), 1e-3, label)
        }
    }

    @Test
    @DisplayName("Every gain reads the value on screen: +13, +15, +11, side +3 and +6, and the +2 bonus row")
    fun everyGainReadsItsValue() {
        assertEquals(14, cells.size)
        for ((fixture, stat, row) in cells) {
            val screen = boxes.first { it.fixture == fixture && it.stat == stat && it.row == row }.screen
            assertEquals(screen, reading(fixture, stat, row, StatGainDigits.EQUAL_GRAY_LEVELS), "$fixture $stat $row")
        }
    }

    @Test
    @DisplayName("Exact equality read the second digit as missing and side gains as a lone +")
    fun exactEqualityMisread() {
        assertEquals("+1", reading("native_speed_13_2", "SPEED", "row 1", 0))
        assertEquals("2", reading("native_speed_13_2", "SPEED", "row 2", 0))
        assertEquals("+", reading("native_speed_13_2", "POWER", "row 1", 0))
        assertEquals("+1", reading("native_speed_15_2", "SPEED", "row 1", 0))
        assertEquals("+", reading("native_guts_8", "SPEED", "row 1", 0))
        assertEquals("+", reading("native_guts_8_power_6", "POWER", "row 1", 0))
        assertEquals("1", reading("native_stamina_13", "STAMINA", "row 1", 0))
        assertEquals("11", reading("native_stamina_13", "GUTS", "row 1", 0))
    }

    @Test
    @DisplayName("Every real glyph passes the share; a 1 off the glyphs that passes the correlation stays under it")
    fun realGlyphsPassFalsePeakStaysRejected() {
        // The kept matches are the real glyphs: every row reads its screen value (everyGainReadsItsValue).
        val real =
            cells.flatMap { (fixture, stat, row) ->
                kept(fixture, stat, row, StatGainDigits.EQUAL_GRAY_LEVELS).map { match ->
                    val searched = boxes.filter { it.fixture == fixture && it.stat == stat && it.row == row && it.template == match.template }
                    searched.single { it.x + template(it.template).width / 2 == match.x.toInt() && pearson(it) == match.score }
                }
            }
        assertEquals(cells.sumOf { (fixture, stat, row) -> boxes.first { it.fixture == fixture && it.stat == stat && it.row == row }.screen.length }, real.size)
        for (box in real) assertTrue(share(box, StatGainDigits.EQUAL_GRAY_LEVELS) >= minEqual, "${box.fixture} ${box.stat} ${box.template}@${box.x}")
        assertTrue(real.any { share(it, 0) < minEqual }, "exact equality left real glyphs under the share")

        val falsePeak = boxes.single { it.fixture == "native_guts_8_power_6" && it.stat == "POWER" && it.row == "row 1" && it.template == "1_mini" && it.x == 521 }
        assertTrue(pearson(falsePeak) >= minPearson)
        assertTrue(share(falsePeak, StatGainDigits.EQUAL_GRAY_LEVELS) < minEqual)
    }

    @Test
    @DisplayName("Matches off the grid are dropped whenever any match was dropped, not only after a double match")
    fun gridDropGuard() {
        val source = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/utils/CustomImageUtils.kt").readText().replace("\r\n", "\n")
        val reader = source.substringAfter("fun determineStatGainFromTraining(").substringBefore("private fun processStatGainTemplateWithTransparency(")
        assertTrue(Regex("""if \(onGrid\.size < rowScores\.size\) \{\s*rowMatches\.forEach \{ \(template, points\) -> points\.retainAll""").containsMatchIn(reader))
    }

    @Test
    @DisplayName("The matcher judges each box within the shared gray tolerance")
    fun matcherUsesTheTolerance() {
        val source = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/utils/CustomImageUtils.kt").readText().replace("\r\n", "\n")
        val matcher = source.substringAfter("private fun processStatGainTemplateWithTransparency(").substringBefore("\n    }\n")
        assertTrue(matcher.contains("Core.absdiff(matchedRegion, templateGray, difference)"))
        assertTrue(matcher.contains("val equalPixels = StatGainDigits.withinGrayLevels(differences, equalGrayLevels)"))
        assertEquals(StatGainDigits.EQUAL_GRAY_LEVELS, StatGainDigits.equalGrayLevels(resized = false), "a 1080 wide capture keeps the native tolerance")
        assertTrue(!matcher.contains("CMP_EQ"), "no exact-equality share beside the tolerance")
    }
}
