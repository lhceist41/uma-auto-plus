package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Tests for the generated Veteran identity domain: the parser's fail-closed behavior, and the
 * committed `veteran_identity.json` asset the roster reader actually ships.
 */
@DisplayName("VeteranIdentityCatalog")
class VeteranIdentityCatalogTest {
    private val shipped: VeteranIdentityCatalog by lazy {
        VeteranIdentityCatalog.parse(assetFile(VeteranIdentityCatalog.ASSET_NAME).readText()) ?: error("the shipped asset should parse")
    }

    @Test
    fun `a malformed, empty, or wrong-version payload parses to null instead of throwing`() {
        assertNull(VeteranIdentityCatalog.parse("not json"))
        assertNull(VeteranIdentityCatalog.parse("""{"schemaVersion":99,"characters":{"A":{"outfits":[]}}}"""))
        assertNull(VeteranIdentityCatalog.parse("""{"schemaVersion":1}"""))
        assertNull(VeteranIdentityCatalog.parse("""{"schemaVersion":1,"characters":{}}"""))
    }

    @Test
    fun `outfitsFor never leaks another trainee's costumes`() {
        val catalog = VeteranIdentityCatalog.parse("""{"schemaVersion":1,"characters":{"A":{"outfits":["X"]},"B":{"outfits":["Y"]}}}""")
        assertNotNull(catalog)
        assertEquals(listOf("X"), catalog!!.outfitsFor("A"))
        // An unknown trainee gets nothing, not the whole domain.
        assertEquals(emptyList<String>(), catalog.outfitsFor("Nobody"))
    }

    @Test
    fun `the shipped asset carries every trainee and materially more than the base costumes`() {
        assertEquals(VeteranIdentityCatalog.SUPPORTED_SCHEMA_VERSION, shipped.schemaVersion)
        // The hand-mirrored Kotlin snapshot this replaced carried 37 base outfits.
        assertTrue(shipped.outfitCount > 37, "expected more than 37 outfits, got ${shipped.outfitCount}")
        for (character in shipped.characters) {
            assertTrue(shipped.outfitsFor(character).isNotEmpty(), "$character has no costume in the shipped domain")
        }
    }

    @Test
    fun `the in-code character mirror matches the generated domain exactly`() {
        // VeteranIdentityNames.CHARACTERS is the fallback used when the asset does not load. If the
        // two ever disagree, a trainee resolves in one path and not the other, which is exactly the
        // kind of silent drift the generated asset exists to remove.
        assertEquals(shipped.characters.sorted(), VeteranIdentityNames.CHARACTERS.sorted())
    }

    @Test
    fun `the live-observed alternate costumes are in the shipped domain`() {
        assertTrue(shipped.outfitsFor("Symboli Rudolf").contains("Emperor's Path"))
        assertTrue(shipped.outfitsFor("Mihono Bourbon").contains("CODE: ICING"))
        assertTrue(shipped.outfitsFor("Taiki Shuttle").contains("Wild Frontier"))
    }

    @Test
    fun `every shipped costume resolves to itself against its own trainee`() {
        // The property the whole character-conditioned design rests on, checked against the real
        // shipped data with the real normalizer and the real thresholds: a clean read of any costume
        // in the domain lands on that exact costume, never on a sibling.
        for (character in shipped.characters) {
            for (outfit in shipped.outfitsFor(character)) {
                val read = resolveNameOutfit("[$outfit]\n$character", shipped)
                assertEquals(character, read.name, "\"$character\" did not resolve to itself")
                assertEquals(outfit, read.outfit, "$character: \"$outfit\" did not resolve to itself")
            }
        }
    }

    @Test
    fun `the live outfit residuals now resolve via the character-conditioned margin path`() {
        // The two costumes the absolute-floor-only rule failed closed on the live 257-scan, fed their
        // exact raw name/outfit OCR from the corpus. Each is a decisive winner inside its trainee's own
        // costumes (Formula R at 0.625 over a 0.14 runner-up; Saintly Jade Cleric at 0.647 over 0.12).
        val maru = resolveNameOutfit("Fomua ®J\nMaruzensky", shipped)
        assertEquals("Maruzensky", maru.name)
        assertEquals("Formula R", maru.outfit)
        assertEquals(OutfitAcceptancePath.MARGIN, maru.outfitAcceptancePath)

        val grass = resolveNameOutfit("(Saintly ade detd\nGrass Wonder", shipped)
        assertEquals("Grass Wonder", grass.name)
        assertEquals("Saintly Jade Cleric", grass.outfit)
        assertEquals(OutfitAcceptancePath.MARGIN, grass.outfitAcceptancePath)
    }

    @Test
    fun `garbage, empty, wrong-character, and unseen outfit reads stay unresolved`() {
        // Garbage outfit line, real character: the character still resolves, the outfit does not.
        val garbage = resolveNameOutfit("zxqw vbnm plkj\nMaruzensky", shipped)
        assertEquals("Maruzensky", garbage.name)
        assertNull(garbage.outfit)
        assertEquals(OutfitAcceptancePath.REJECT, garbage.outfitAcceptancePath)

        // Empty read: nothing resolves at all.
        assertNull(resolveNameOutfit("", shipped).outfit)

        // Another trainee's costume, scored inside this trainee: "Wild Frontier" is Taiki's, not
        // Maruzensky's, so it must not be minted as one of Maruzensky's costumes.
        val wrong = resolveNameOutfit("Wild Frontier\nMaruzensky", shipped)
        assertEquals("Maruzensky", wrong.name)
        assertNull(wrong.outfit)

        // A plausible but unseen costume name for a real trainee: no near match in her set.
        val unseen = resolveNameOutfit("Midnight Gala Serenade\nGrass Wonder", shipped)
        assertEquals("Grass Wonder", unseen.name)
        assertNull(unseen.outfit)
    }

    @Test
    fun `a single-candidate trainee still needs a meaningful score, and an ambiguous pair rejects`() {
        // One-candidate trainee: a very poor outfit read is rejected even though there is no rival
        // costume to disqualify it, because a resolved outfit still needs the relaxed absolute floor.
        val solo = VeteranIdentityCatalog.parse("""{"schemaVersion":1,"characters":{"Maruzensky":{"outfits":["Formula R"]}}}""")
        assertNotNull(solo)
        val poor = resolveNameOutfit("qqqq wwww\nMaruzensky", solo)
        assertEquals("Maruzensky", poor.name)
        assertNull(poor.outfit, "a very poor read against the sole costume must not be minted")

        // The relaxed-path boundary, exercised directly. (best, runner-up) pairs.
        assertEquals(OutfitAcceptancePath.STRONG, outfitAcceptancePath(0.90, 0.20))
        assertEquals(OutfitAcceptancePath.MARGIN, outfitAcceptancePath(0.625, 0.14)) // a real residual
        assertEquals(OutfitAcceptancePath.MARGIN, outfitAcceptancePath(0.60, null)) // sole costume, decent read
        assertEquals(OutfitAcceptancePath.REJECT, outfitAcceptancePath(0.45, null)) // sole costume, poor read
        assertEquals(OutfitAcceptancePath.REJECT, outfitAcceptancePath(0.65, 0.55)) // ambiguous: margin 0.10 < 0.15
        assertEquals(OutfitAcceptancePath.REJECT, outfitAcceptancePath(0.50, 0.10)) // below the relaxed floor
    }

    private fun assetFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/assets")
            if (a.isDirectory) return File(a, relative)
            val b = File(dir, "android/app/src/main/assets")
            if (b.isDirectory) return File(b, relative)
            dir = dir?.parentFile
        }
        error("could not locate the assets root")
    }
}
