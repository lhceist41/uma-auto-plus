package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for the Veteran name/outfit canonical matcher. Generic OCR reads the brown-on-illustration
 * header text approximately; snapping that onto the known character domain, and then onto that
 * character's own costumes, recovers the exact strings the roster fingerprint needs. An off-domain,
 * ambiguous, or wrong-trainee read stays unresolved.
 */
@DisplayName("VeteranIdentityNames canonical matcher")
class VeteranIdentityNamesTest {
    /** A hand-built catalog, so these tests pin the matcher's behavior rather than the shipped data.
     * The committed asset is separately pinned by [VeteranIdentityCatalogTest]. */
    private val catalog =
        VeteranIdentityCatalog(
            schemaVersion = 1,
            outfitSource = "test",
            outfitsByCharacter =
                mapOf(
                    "Taiki Shuttle" to listOf("Wild Frontier", "Bubblegum☆Memories"),
                    "Copano Rickey" to listOf("Eightfold☆Fortune"),
                    "Symboli Rudolf" to listOf("Emperor's Path", "Archer by Moonlight"),
                    "Mihono Bourbon" to listOf("MB-19890425", "CODE: ICING"),
                    "Mejiro Ryan" to listOf("Down the Line", "Marguerite Latte"),
                    "Mejiro Dober" to listOf("Off the Line", "Hungry Veil"),
                    "El Condor Pasa" to listOf("El☆Número 1", "Kukulkan Warrior"),
                ),
        )

    @Test
    fun `garbled OCR snaps onto the canonical character name`() {
        assertEquals("Taiki Shuttle", canonicalMatch("Taikishuttle", VeteranIdentityNames.CHARACTERS))
        assertEquals("Copano Rickey", canonicalMatch("Copano Rlckey", VeteranIdentityNames.CHARACTERS))
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
    fun `accented titles fold to their base letters instead of losing the character`() {
        // OCR has no accents to give: it renders "El☆Número 1" as "El Numero 1". Folding both sides
        // makes that an exact skeleton match rather than an edit away.
        assertEquals(normalizeIdentityText("El☆Número 1"), normalizeIdentityText("El Numero 1"))
        assertEquals("elnumero1", normalizeIdentityText("El☆Número 1"))
    }

    @Test
    fun `base outfits still resolve, brackets and symbols optional`() {
        val taiki = resolveNameOutfit("[Wild Fronttai]\nTaikishuttle", catalog)
        assertEquals("Wild Frontier", taiki.outfit)
        assertEquals("Taiki Shuttle", taiki.name)
        // Outfit line with the bracket dropped still lands on the outfit domain, not the name.
        val copano = resolveNameOutfit("Eightfold Fortune\nCopano Rlckey", catalog)
        assertEquals("Eightfold☆Fortune", copano.outfit)
        assertEquals("Copano Rickey", copano.name)
    }

    @Test
    fun `alternate costumes resolve, which the base-card-only domain could not do`() {
        val rudolf = resolveNameOutfit("[Emperor's Path]\nSymboli Rudolf", catalog)
        assertEquals("Symboli Rudolf", rudolf.name)
        assertEquals("Emperor's Path", rudolf.outfit)

        val bourbon = resolveNameOutfit("[CODE: ICING]\nMihono Bourbon", catalog)
        assertEquals("Mihono Bourbon", bourbon.name)
        assertEquals("CODE: ICING", bourbon.outfit)

        // Punctuation loss and a mangled glyph, on the non-base card.
        val moonlight = resolveNameOutfit("Archer by Moonllght\nSymboll Rudolf", catalog)
        assertEquals("Symboli Rudolf", moonlight.name)
        assertEquals("Archer by Moonlight", moonlight.outfit)
    }

    @Test
    fun `another trainee's costume never matches`() {
        // "Down the Line" (Mejiro Ryan) and "Off the Line" (Mejiro Dober) are the closest real
        // cross-trainee pair in the shipped domain at 0.73 similarity, close enough that a flat
        // outfit list has to separate them. Conditioned on the resolved trainee they never compete:
        // a Dober read can only ever land on a Dober costume.
        val dober = resolveNameOutfit("Down the Line\nMejiro Dober", catalog)
        assertEquals("Mejiro Dober", dober.name)
        assertEquals("Off the Line", dober.outfit)

        // A costume belonging to nobody in this trainee's list stays unresolved rather than reaching
        // across to its real owner.
        val alien = resolveNameOutfit("Eightfold Fortune\nMihono Bourbon", catalog)
        assertEquals("Mihono Bourbon", alien.name)
        assertNull(alien.outfit)
    }

    @Test
    fun `an unresolved trainee leaves the outfit unresolved too`() {
        // Without a trainee there is no costume domain to score inside, and guessing from a global
        // list could name a costume this Veteran cannot wear. Both stay null.
        val read = resolveNameOutfit("Wild Frontier\nZxqwv Mnbvc", catalog)
        assertNull(read.name)
        assertNull(read.outfit)
    }

    @Test
    fun `a missing catalog leaves outfits unresolved rather than guessing`() {
        val read = resolveNameOutfit("[Wild Fronttai]\nTaikishuttle", null)
        assertEquals("Taiki Shuttle", read.name)
        assertNull(read.outfit)
    }

    @Test
    fun `a noisy read only resolves when it is confidently one costume`() {
        // Far from every Taiki costume: rejected on similarity, and the evidence still names what it
        // came closest to so the failure can be diagnosed offline.
        val noise = resolveNameOutfit("Qqzzxx Vvbbnn\nTaiki Shuttle", catalog)
        assertEquals("Taiki Shuttle", noise.name)
        assertNull(noise.outfit)
        assertNotNull(noise.outfitCandidate)
        assertTrue(noise.outfitScore!! < CANONICAL_MIN_SIMILARITY)
    }

    @Test
    fun `the scoring evidence is diagnostics only and never promotes a rejected candidate`() {
        val rejected = resolveNameOutfit("Qqzzxx Vvbbnn\nTaiki Shuttle", catalog)
        assertNull(rejected.outfit)
        // The near-miss candidate is recorded, but the identity field stays null.
        assertNotNull(rejected.outfitCandidate)

        val accepted = resolveNameOutfit("[Wild Fronttai]\nTaikishuttle", catalog)
        assertEquals(accepted.outfitCandidate, accepted.outfit)
        assertTrue(accepted.outfitScore!! >= CANONICAL_MIN_SIMILARITY)
    }

    @Test
    fun `a single-costume trainee has no runner-up to be ambiguous against`() {
        // Copano owns exactly one costume, so the margin rule must not reject it for lack of a
        // second candidate.
        val copano = resolveNameOutfit("Eightfold Fortune\nCopano Rickey", catalog)
        assertEquals("Eightfold☆Fortune", copano.outfit)
        assertNull(copano.outfitSecondCandidate)
    }
}
