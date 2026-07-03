package com.steve1316.uma_android_automation.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [stripTrailingGlyphNoise], which recovers skill base names when OCR misreads
 * a trailing rank glyph (○/×) as a letter or leaves stray single-character noise.
 */
@DisplayName("Strip Trailing Glyph Noise Tests")
class StripTrailingGlyphNoiseTest {
    @Test
    @DisplayName("Glued misread circle is stripped")
    fun testGluedCircle() {
        assertEquals("Corner Recovery", stripTrailingGlyphNoise("Corner RecoveryO"))
    }

    @Test
    @DisplayName("Glued misread cross is stripped")
    fun testGluedCross() {
        assertEquals("Corner Recovery", stripTrailingGlyphNoise("Corner Recoveryx"))
    }

    @Test
    @DisplayName("Standalone trailing letter after a space is stripped")
    fun testStandaloneTrailingLetter() {
        assertEquals("Corner Recovery", stripTrailingGlyphNoise("Corner Recovery O"))
        assertEquals("Corner Recovery", stripTrailingGlyphNoise("Corner Recovery x"))
    }

    @Test
    @DisplayName("Standalone trailing digit after a space is stripped")
    fun testStandaloneTrailingDigit() {
        assertEquals("Corner Recovery", stripTrailingGlyphNoise("Corner Recovery 0"))
    }

    @Test
    @DisplayName("Trailing whitespace alone is trimmed without touching the name")
    fun testTrailingWhitespaceOnly() {
        assertEquals("Professor of Curvature", stripTrailingGlyphNoise("Professor of Curvature  "))
    }

    @Test
    @DisplayName("Names whose last word is longer than one character are untouched")
    fun testMultiCharLastWordUntouched() {
        assertEquals("Straightaway Adept", stripTrailingGlyphNoise("Straightaway Adept"))
        assertEquals("Slick Surge", stripTrailingGlyphNoise("Slick Surge"))
    }

    @Test
    @DisplayName("Glued strip does not fire on lowercase o or mid-word letters")
    fun testGluedStripIsCaseSpecific() {
        assertEquals("Tempo", stripTrailingGlyphNoise("Tempo"))
    }

    @Test
    @DisplayName("Single-character input collapses to empty")
    fun testSingleCharacterInput() {
        assertEquals("", stripTrailingGlyphNoise("O"))
    }
}
