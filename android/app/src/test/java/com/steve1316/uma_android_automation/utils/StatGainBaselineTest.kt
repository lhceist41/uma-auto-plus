package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.opencv.core.Point

/**
 * Replays 11,152 logged training-gain reads (src/test/resources/fixtures/statgainmatches, see PROVENANCE.md) through the baseline gate that
 * `constructIntegerFromMatches` applies before it joins the glyphs.
 */
@DisplayName("Stat gain glyphs off the digits' line")
class StatGainBaselineTest {
    private class Read(val source: String, val count: Int, val matches: List<Pair<String, Point>>, val logged: String)

    private val reads: List<Read> by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/statgainmatches/logged_gain_matches.csv")) { "missing logged_gain_matches.csv" }
        stream.bufferedReader().readLines().drop(1).filter { it.isNotBlank() }.map { line ->
            val (source, count, matches, logged) = line.split(",")
            val parsed =
                matches.split("|").map { m ->
                    val (glyph, at) = m.split("@")
                    val (x, y) = at.split(":")
                    glyph to Point(x.toDouble(), y.toDouble())
                }
            Read(source, count.toInt(), parsed, logged)
        }
    }

    private fun joined(matches: List<Pair<String, Point>>): String = matches.joinToString("") { it.first }

    private fun spread(r: Read): Double = r.matches.maxOf { it.second.y } - r.matches.minOf { it.second.y }

    private fun gated(r: Read): String = joined(StatGainDigits.onBaseline(r.matches, StatGainDigits.BASELINE_MAX_DY.toDouble()))

    @Test
    fun `the fixture is what the reader logged`() {
        assertEquals(11152, reads.sumOf { it.count })
        assertTrue(reads.all { joined(it.matches) == it.logged })
    }

    @Test
    fun `every outfit stray is dropped and the real gain kept`() {
        val strays = reads.filter { spread(it) > StatGainDigits.BASELINE_MAX_DY }
        assertEquals(27, strays.sumOf { it.count })
        assertTrue(strays.all { it.source.endsWith("single-row") && it.logged == "77" })
        strays.forEach { assertEquals("7", gated(it), "for ${it.logged} from ${it.source}") }
    }

    @Test
    fun `no clean read changes`() {
        val clean = reads.filter { spread(it) <= StatGainDigits.BASELINE_MAX_DY }
        assertEquals(11125, clean.sumOf { it.count })
        assertTrue(clean.all { spread(it) <= 7 })
        clean.forEach { assertEquals(it.logged, gated(it), "for ${it.logged} from ${it.source}") }
    }

    @Test
    fun `the reader gates template matches and leaves YOLO boxes alone`() {
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir")).absoluteFile
        var src: String? = null
        repeat(8) {
            val f = java.io.File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation/utils/CustomImageUtils.kt")
            if (src == null && f.isFile) src = f.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        val code = requireNotNull(src) { "CustomImageUtils.kt not found" }
        val body = code.substring(code.indexOf("private fun constructIntegerFromMatches("))
        assertTrue(body.contains("val onLine = if (useYolo) allMatches else StatGainDigits.onBaseline(allMatches, relHeight(StatGainDigits.BASELINE_MAX_DY).toDouble())"))
        assertTrue(body.contains("val constructedString = onLine.joinToString(\"\")"))
    }

    @Test
    fun `the plus sign sets the line when present`() {
        val stray = listOf("+" to Point(45.0, 44.0), "7" to Point(30.0, 23.0), "7" to Point(88.0, 44.0))
        assertEquals(listOf("+", "7"), StatGainDigits.onBaseline(stray, 11.0).map { it.first })
        val single = listOf("7" to Point(88.0, 44.0))
        assertEquals(single, StatGainDigits.onBaseline(single, 11.0))
    }
}
