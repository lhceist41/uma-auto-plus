package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.utils.StatGainDigits.TemplateMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/** Score maps stand in for matchTemplate's result over a 150 x 55 gain row; match lists are logged rows (fixtures/statgainmatches). */
@DisplayName("Every glyph of a gain row is searched and kept on the glyph grid of its \"+\"")
class StatGainPeakSearchTest {
    private val cols = 132
    private val rows = 16

    /** A score map with background 0.5 and the given (top-left x, score) peaks on row 8. */
    private fun scoreMap(vararg peaks: Pair<Int, Float>): FloatArray {
        val map = FloatArray(cols * rows) { 0.5f }
        for ((x, score) in peaks) map[8 * cols + x] = score
        return map
    }

    private fun search(map: FloatArray, boxW: Int = 19, stopAfterFirst: Boolean = false, accept: (Int, Int) -> Boolean = { _, _ -> true }) =
        StatGainDigits.searchPeaks(map, cols, boxW, 38, 0.9, stopAfterFirst, accept).map { it.first }

    @Test
    @DisplayName("Both peaks are returned when the right glyph scores higher")
    fun leftGlyphOfBetterPeak() {
        assertEquals(listOf(75, 37), search(scoreMap(37 to 0.95f, 75 to 0.98f)))
    }

    @Test
    @DisplayName("The left glyph is returned when a rejected peak lies right of it")
    fun leftGlyphOfRejectedPeak() {
        val asked = mutableListOf<Int>()
        val kept =
            search(scoreMap(37 to 0.95f, 70 to 0.99f)) { x, _ ->
                asked.add(x)
                x != 70
            }
        assertEquals(listOf(37), kept)
        assertEquals(listOf(70, 37), asked)
    }

    @Test
    @DisplayName("A match's own box is blanked, a neighbouring glyph's is not, and non-finite scores are no match")
    fun blanking() {
        val map = scoreMap(37 to 0.95f, 75 to 0.98f, 77 to 0.97f, 66 to 0.96f)
        map[2 * cols + 5] = Float.POSITIVE_INFINITY
        map[3 * cols + 120] = Float.NaN
        assertEquals(listOf(75, 37), search(map))
    }

    @Test
    @DisplayName("A \"+\" stops after its first kept match; rejected peaks end at the pass cap")
    fun stops() {
        assertEquals(listOf(75), search(scoreMap(37 to 0.95f, 75 to 0.98f), stopAfterFirst = true))
        var asked = 0
        val everywhere = FloatArray(cols * rows) { 0.95f }
        val kept =
            search(everywhere, boxW = 1) { _, _ ->
                asked++
                false
            }
        assertTrue(kept.isEmpty())
        assertEquals(StatGainDigits.MAX_PEAK_PASSES, asked)
    }

    private val pitch1 = StatGainDigits.GLYPH_PITCH_ROW_1.toDouble()
    private val gridDx = StatGainDigits.GRID_MAX_DX.toDouble()

    private fun read(matches: List<TemplateMatch>, pitch: Double = pitch1): String =
        StatGainDigits.onPlusGrid(StatGainDigits.keepBestPerGlyph(matches, StatGainDigits.SAME_GLYPH_MAX_DX.toDouble()), pitch, gridDx).joinToString("") { it.template[0].toString() }

    /** Runs the search on a row holding [template] at the given centres and scores, the way the matcher builds its match list. */
    private fun found(template: String, width: Int, vararg centres: Pair<Int, Float>): List<TemplateMatch> {
        val half = width / 2
        val map = scoreMap(*centres.map { (x, score) -> x - half to score }.toTypedArray())
        val scoreAt = centres.associate { (x, score) -> x - half to score.toDouble() }
        return StatGainDigits.searchPeaks(map, cols, width, 38, 0.9, false) { _, _ -> true }.map { (x, _) -> TemplateMatch(template, (x + half).toDouble(), scoreAt.getValue(x)) }
    }

    @Test
    @DisplayName("Logged row \"+_mini@18, 1_mini@56, 1_mini@94\" reads +11 (720, Guts +11 read +1)")
    fun loggedPlusEleven() {
        val matches = listOf(TemplateMatch("+_mini", 18.0, 0.97)) + found("1_mini", 19, 56 to 0.95f, 94 to 0.98f)
        assertEquals(listOf(18.0, 56.0, 94.0), matches.map { it.x }.sorted())
        assertEquals("+11", read(matches))
    }

    @Test
    @DisplayName("A false 1_mini 20 px right of the \"+\" is dropped by the grid")
    fun falseOneOnPlusArm() {
        val matches = listOf(TemplateMatch("+_mini", 18.0, 0.97), TemplateMatch("1_mini", 38.0, 0.91), TemplateMatch("1_mini", 56.0, 0.95), TemplateMatch("1_mini", 94.0, 0.98))
        assertEquals("+11", read(matches))
    }

    @Test
    @DisplayName("Two-row +22 at 1440 (+@49, 2@88, 2@126) reads whole")
    fun loggedPlusTwentyTwo() {
        val matches = listOf(TemplateMatch("+_mini", 49.0, 0.97)) + found("2_mini", 27, 88 to 0.94f, 126 to 0.97f)
        assertEquals("+22", read(matches))
    }

    @Test
    @DisplayName("Without a \"+\" the grid keeps every match; extra pluses and digits left of the \"+\" are dropped")
    fun gridEdges() {
        val noPlus = listOf(TemplateMatch("1_mini", 56.0, 0.95), TemplateMatch("4_mini", 94.0, 0.95))
        assertEquals(noPlus, StatGainDigits.onPlusGrid(noPlus, pitch1, gridDx))
        assertEquals("+4", read(listOf(TemplateMatch("+_mini", 18.0, 0.97), TemplateMatch("+_mini", 70.0, 0.91), TemplateMatch("4_mini", 57.0, 0.95), TemplateMatch("7_mini", 5.0, 0.95))))
    }

    @Test
    @DisplayName("Every logged read keeps all its digits on the grid of its row kind")
    fun loggedReadsOnGrid() {
        val lines = requireNotNull(javaClass.getResourceAsStream("/fixtures/statgainmatches/logged_gain_matches.csv")).bufferedReader().readLines().drop(1)
        assertEquals(1158, lines.size)
        val single = StatGainDigits.GLYPH_PITCH_SINGLE_ROW.toDouble()
        val pitch2 = StatGainDigits.GLYPH_PITCH_ROW_2.toDouble()
        for (line in lines) {
            val (source, _, matchField) = line.split(",")
            val matches =
                matchField.split("|").map { match ->
                    TemplateMatch(match.substringBefore("@"), match.substringAfter("@").substringBefore(":").toDouble(), 0.95)
                }
            val distinct = StatGainDigits.keepBestPerGlyph(matches, StatGainDigits.SAME_GLYPH_MAX_DX.toDouble())
            val pitches = if (source.endsWith("single-row")) listOf(single) else listOf(pitch1, pitch2)
            assertTrue(pitches.any { StatGainDigits.onPlusGrid(distinct, it, gridDx) == distinct }, line)
        }
    }

    @Test
    @DisplayName("The matcher searches every peak, and each row reads on the grid of its own pitch")
    fun matcherWiring() {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        val path = "android/app/src/main/java/com/steve1316/uma_android_automation/utils/CustomImageUtils.kt"
        while (dir != null && !File(dir, path).isFile) dir = dir.parentFile
        val source = File(requireNotNull(dir) { "$path not found" }, path).readText().replace("\r\n", "\n")
        val matcher = source.substringAfter("private fun processStatGainTemplateWithTransparency(").substringBefore("\n    }\n")
        assertTrue(matcher.contains("StatGainDigits.searchPeaks(scoreMap, result.cols(), w, h, matchConfidence, stopAfterFirst)"))

        val reader = source.substringAfter("fun determineStatGainFromTraining(").substringBefore("private fun processStatGainTemplateWithTransparency(")

        fun pitchOf(rowName: String) = Regex("""StatGainRowConfig\([^\n]*"$rowName", "[^"]*", StatGainDigits\.(\w+)\)""").findAll(reader).map { it.groupValues[1] }.toList()
        assertEquals(listOf("GLYPH_PITCH_ROW_1"), pitchOf("row 1"))
        assertEquals(listOf("GLYPH_PITCH_ROW_2"), pitchOf("row 2"))
        assertEquals(listOf("GLYPH_PITCH_SINGLE_ROW"), pitchOf(""))
        assertTrue(reader.contains("val onGrid = StatGainDigits.onPlusGrid(kept, relWidth(row.glyphPitch).toDouble(), relWidth(StatGainDigits.GRID_MAX_DX).toDouble())"))
        assertTrue(reader.contains("points.retainAll { point -> onGrid.any { it.template == template && it.x == point.x } }"))
    }
}
