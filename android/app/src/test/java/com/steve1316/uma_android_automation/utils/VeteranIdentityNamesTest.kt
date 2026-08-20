package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for the Veteran name/outfit canonical matcher. Generic OCR reads the brown-on-illustration
 * header text approximately; snapping that onto the known character/outfit domain recovers the exact
 * string the roster fingerprint needs, and an off-domain or ambiguous read stays unresolved.
 */
@DisplayName("VeteranIdentityNames canonical matcher")
class VeteranIdentityNamesTest {
    @Test
    fun `garbled OCR snaps onto the canonical character name`() {
        assertEquals("Taiki Shuttle", canonicalMatch("Taikishuttle", VeteranIdentityNames.CHARACTERS))
        assertEquals("Copano Rickey", canonicalMatch("Copano Rlckey", VeteranIdentityNames.CHARACTERS))
    }

    @Test
    fun `garbled OCR snaps onto the canonical outfit title, symbols and brackets ignored`() {
        assertEquals("Wild Frontier", canonicalMatch("[Wild Fronttai]", VeteranIdentityNames.OUTFITS))
        assertEquals("Eightfold☆Fortune", canonicalMatch("Eightfold Fortune", VeteranIdentityNames.OUTFITS))
    }

    @Test
    fun `an OCR read close to nothing known stays unresolved`() {
        assertNull(canonicalMatch("Zxqwv Mnbvc", VeteranIdentityNames.CHARACTERS))
    }

    @Test
    fun `two near-equally-close candidates are rejected as ambiguous`() {
        // "Mejiro" alone sits almost equally close to every Mejiro trainee; no clear winner.
        assertNull(canonicalMatch("Mejiro", VeteranIdentityNames.CHARACTERS))
    }

    @Test
    fun `resolveNameOutfit assigns each line to the domain it fits, brackets optional`() {
        val taiki = resolveNameOutfit("[Wild Fronttai]\nTaikishuttle")
        assertEquals("Wild Frontier", taiki.outfit)
        assertEquals("Taiki Shuttle", taiki.name)
        // Outfit line with the bracket dropped still lands on the outfit domain, not the name.
        val copano = resolveNameOutfit("Eightfold Fortune\nCopano Rlckey")
        assertEquals("Eightfold☆Fortune", copano.outfit)
        assertEquals("Copano Rickey", copano.name)
    }
}
