package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.utils.StatGainDigits.TemplateMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Match lists are the template matcher's logged rows (fixtures/statgain/PROVENANCE.md); scores are set per case. */
@DisplayName("One template match per gain glyph")
class StatGainTemplateMatchTest {
    private val maxDx = StatGainDigits.SAME_GLYPH_MAX_DX.toDouble()

    private fun m(template: String, x: Int, score: Double = 0.95) = TemplateMatch(template, x.toDouble(), score)

    /** The string constructIntegerFromMatches builds: each kept template's first character, left to right. */
    private fun read(matches: List<TemplateMatch>): String = StatGainDigits.keepBestPerGlyph(matches, maxDx).joinToString("") { it.template[0].toString() }

    @Test
    @DisplayName("A 1 on the stem of a 4 is dropped (phone and MuMu two-row reads)")
    fun oneOnFour() {
        assertEquals("+4", read(listOf(m("+_mini", 36), m("4_mini", 76, 0.97), m("1_mini", 78, 0.86))))
        assertEquals("+14", read(listOf(m("+_mini", 49), m("1_mini", 87), m("4_mini", 127, 0.97), m("1_mini", 128, 0.86))))
        assertEquals("+24", read(listOf(m("+_mini", 49), m("2_mini", 89), m("4_mini", 127, 0.97), m("1_mini", 128, 0.86))))
    }

    @Test
    @DisplayName("A 0 and a 9 on one glyph keep the better-scoring template (MuMu URA reads)")
    fun zeroAndNine() {
        assertEquals("+9", read(listOf(m("+", 66), m("0", 108, 0.86), m("9", 108, 0.95))))
        assertEquals("+19", read(listOf(m("+", 45), m("1", 85), m("0", 129, 0.86), m("9", 129, 0.95))))
        assertEquals("+10", read(listOf(m("+", 45), m("1", 85), m("0", 129, 0.95), m("9", 129, 0.86))))
        assertEquals("+68", read(listOf(m("+", 45), m("6", 87), m("6", 128, 0.88), m("8", 129, 0.93))))
    }

    @Test
    @DisplayName("Repeated and neighbouring real digits stay")
    fun realDigitsStay() {
        assertEquals("+11", read(listOf(m("+_mini", 40), m("1_mini", 73), m("1_mini", 108))))
        assertEquals("+11", read(listOf(m("+", 45), m("1", 85), m("1", 127))))
        assertEquals("+14", read(listOf(m("+_mini", 47), m("1_mini", 87), m("4_mini", 127))))
        assertEquals("+4", read(listOf(m("+_mini", 47), m("4_mini", 87))))
    }

    @Test
    @DisplayName("Matches 12 px apart are separate glyphs, 11 px apart one glyph")
    fun threshold() {
        assertEquals(2, StatGainDigits.keepBestPerGlyph(listOf(m("4", 100), m("1", 112)), maxDx).size)
        assertEquals(listOf("4"), StatGainDigits.keepBestPerGlyph(listOf(m("4", 100, 0.97), m("1", 111, 0.86)), maxDx).map { it.template })
    }

    @Test
    @DisplayName("Every logged list without a double match is unchanged, whatever the scores")
    fun loggedListsUnchanged() {
        val lines = requireNotNull(javaClass.getResourceAsStream("/fixtures/statgain/logged_template_matches.txt")).bufferedReader().readLines().filter { it.isNotBlank() }
        assertEquals(329, lines.size)
        var smallestPitch = Double.MAX_VALUE
        for (line in lines) {
            val parsed = line.split(" ").map { it.substringBefore("@") to it.substringAfter("@").toDouble() }
            parsed.zipWithNext { a, b -> smallestPitch = minOf(smallestPitch, b.second - a.second) }
            for (order in listOf(1.0, -1.0)) {
                val matches = parsed.mapIndexed { i, (template, x) -> TemplateMatch(template, x, 0.9 + order * i * 0.01) }
                assertEquals(matches.map { it.template to it.x }, StatGainDigits.keepBestPerGlyph(matches, maxDx).map { it.template to it.x }, line)
            }
        }
        assertTrue(smallestPitch >= 33.0, "smallest logged pitch $smallestPitch")
    }
}
