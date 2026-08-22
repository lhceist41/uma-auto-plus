package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.SparkRowKind
import com.steve1316.uma_android_automation.bot.canonicalFactorToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The factor canonicalizer: the layer that turns the Inspiration/lineage factor OCR - which jitters
 * on ~3.5% of names across a re-read - into a deterministic canonical name, so the same visible factor
 * always produces the same fingerprint.
 *
 * The real committed `veteran_factor_domain.json` is loaded so the tests exercise the domain the app
 * actually ships, not a synthetic mirror; the synthetic-domain tests pin the exact-skeleton and
 * ambiguity behavior deterministically.
 */
@DisplayName("Veteran factor canonicalizer")
class VeteranFactorDomainTest {
    private val domain =
        VeteranFactorDomain.parse(assetFile(VeteranFactorDomain.ASSET_NAME).readText())
            ?: error("the shipped veteran_factor_domain.json should parse")

    @Nested
    @DisplayName("acceptance boundary")
    inner class Acceptance {
        @Test
        fun `strong, margin and reject fall where the PL-R1b model puts them`() {
            assertEquals(FactorAcceptancePath.STRONG, factorAcceptancePath(0.95, 0.30))
            assertEquals(FactorAcceptancePath.STRONG, factorAcceptancePath(0.70, null)) // sole candidate, good read
            assertEquals(FactorAcceptancePath.MARGIN, factorAcceptancePath(0.64, 0.45)) // below strong floor, wide lead
            assertEquals(FactorAcceptancePath.REJECT, factorAcceptancePath(0.66, 0.60)) // ambiguous: margin 0.06 < 0.08
            assertEquals(FactorAcceptancePath.REJECT, factorAcceptancePath(0.64, 0.55)) // margin 0.09 < relaxed 0.15
            assertEquals(FactorAcceptancePath.REJECT, factorAcceptancePath(0.50, null)) // below every floor
        }
    }

    @Nested
    @DisplayName("real OCR jitter collapses to one canonical name")
    inner class Jitter {
        private fun canon(raw: String, kind: SparkRowKind) = domain.resolve(raw, kind).canonicalName

        @Test
        fun `a glued trailing glyph and a clean read resolve to the same skill`() {
            // The exact pair from the PL-R1c validation: OCR glued the unreadable grade circle onto the
            // last word one pass and dropped it the next.
            assertEquals("Firm Conditions", canon("FIRM CONDITIONS", SparkRowKind.WHITE))
            assertEquals("Firm Conditions", canon("FIRM CONDITIONSO", SparkRowKind.WHITE))
        }

        @Test
        fun `an internal RN-to-M misread and a clean read resolve to the same skill`() {
            assertEquals("Long Corners", canon("LONG CORNERS", SparkRowKind.WHITE))
            assertEquals("Long Corners", canon("LONG COMERS", SparkRowKind.WHITE))
        }

        @Test
        fun `whitespace, case, and a trailing marker never change the canonical name`() {
            val base = canon("Calm in a Crowd", SparkRowKind.WHITE)
            assertEquals("Calm in a Crowd", base)
            assertEquals(base, canon("  calm   IN a Crowd ", SparkRowKind.WHITE))
            assertEquals(base, canon("Calm in a Crowd O", SparkRowKind.WHITE))
            assertEquals(base, canon("Calm in a Crowd ○", SparkRowKind.WHITE))
        }

        @Test
        fun `each family resolves its own fixture factors`() {
            assertEquals("Power", canon("Power", SparkRowKind.STAT))
            assertEquals("Mile", canon("Mile", SparkRowKind.APTITUDE))
            assertEquals("Pace Chaser", canon("Pace Chaser", SparkRowKind.APTITUDE))
            assertEquals("Shooting for Victory!", canon("Shooting for Victory!", SparkRowKind.UNIQUE))
            assertEquals("Behold Thine Emperor's Divine Might", canon("Behold Thine Emperors Divine Might", SparkRowKind.UNIQUE))
            assertEquals("Yasuda Kinen", canon("Yasuda Kinen", SparkRowKind.WHITE))
            assertEquals("URA Finale", canon("URA Finale", SparkRowKind.WHITE))
        }

        @Test
        fun `a clean in-domain read is accepted STRONG`() {
            val res = domain.resolve("Firm Conditions", SparkRowKind.WHITE)
            assertEquals(FactorAcceptancePath.STRONG, res.path)
            assertEquals(1.0, res.bestScore) // exact skeleton
        }
    }

    @Nested
    @DisplayName("off-domain reads fail closed")
    inner class FailClosed {
        @Test
        fun `an empty read resolves to nothing`() {
            assertNull(domain.resolve("", SparkRowKind.WHITE).canonicalName)
            assertNull(domain.resolve("   ", SparkRowKind.STAT).canonicalName)
        }

        @Test
        fun `pure garbage resolves to nothing`() {
            assertNull(domain.resolve("Xqzzptdf Wglmr", SparkRowKind.WHITE).canonicalName)
            assertNull(domain.resolve("zzzzzzzz", SparkRowKind.UNIQUE).canonicalName)
        }

        @Test
        fun `a stat name scored against the white domain does not snap onto a skill`() {
            // Kind conditioning: the pixel classifier said STAT, so a stat's short name is never scored
            // against the hundreds of white skills where a coincidental near-match could live.
            assertEquals("Power", domain.resolve("Power", SparkRowKind.STAT).canonicalName)
        }

        @Test
        fun `an ambiguous truncated race stays unresolved rather than guessing`() {
            // A truncation that fits two races ("Kyoto K." -> Kyoto Kimpai / Kyoto Kinen) carries no
            // unique identity, so it fails closed instead of snapping onto one arbitrarily. A uniquely
            // identifying truncation does resolve - see the abbreviation tests below.
            assertNull(domain.resolve("Kyoto K.", SparkRowKind.WHITE).canonicalName)
        }
    }

    @Nested
    @DisplayName("truncated race abbreviations resolve deterministically")
    inner class Abbreviation {
        private fun res(raw: String, kind: SparkRowKind = SparkRowKind.WHITE) = domain.resolve(raw, kind)
        private fun canon(raw: String, kind: SparkRowKind = SparkRowKind.WHITE) = res(raw, kind).canonicalName

        @Test
        fun `a uniquely identifying truncated race resolves to its full name`() {
            // The factor card truncates the last word of a race to a leading letter or two followed by a
            // period. Each of these identifies exactly one race and now resolves; "Hopeful S." and
            // "Mile Ch." are too short to clear the fuzzy floor and are recovered by the abbreviation
            // path, while "NHK Mile C." / "Japan C." already cleared it on the fuzzy path. All are
            // truncated-abbreviation reads that must map to the full canonical name.
            assertEquals("Hopeful Stakes", canon("Hopeful S."))
            assertEquals("Mile Championship", canon("Mile Ch."))
            assertEquals("NHK Mile Cup", canon("NHK Mile C."))
            assertEquals("Japan Cup", canon("Japan C."))
        }

        @Test
        fun `the abbreviation path records its own acceptance path`() {
            // "Hopeful S." specifically reaches the new path (fuzzy rejects it at 0.62), so it is the one
            // that carries the ABBREVIATION marker rather than STRONG/MARGIN.
            val r = res("Hopeful S.")
            assertEquals("Hopeful Stakes", r.canonicalName)
            assertEquals(FactorAcceptancePath.ABBREVIATION, r.path)
            assertEquals("race", r.sourceFamily)
        }

        @Test
        fun `the two live spellings of Hopeful Stakes converge on one canonical name`() {
            // The exact residual PL-R1c targets: one run reads "Hopeful S." (recovered by the abbreviation
            // path), the other "Hopeful Ss." (already recovered by the fuzzy path). Both must land on the
            // same canonical name so the re-reads stop splitting the factor's identity.
            assertEquals("Hopeful Stakes", canon("Hopeful S."))
            assertEquals("Hopeful Stakes", canon("Hopeful Ss."))
            assertEquals(canon("Hopeful S."), canon("Hopeful Ss."))
        }

        @Test
        fun `both spellings produce the same factor fingerprint token`() {
            // Same visible race, same stars and kind -> same canonical token, so the two re-reads hash
            // identically and no longer split a lineage fingerprint.
            val a = canonicalFactorToken(SparkRowKind.WHITE, canon("Hopeful S."), 3)
            val b = canonicalFactorToken(SparkRowKind.WHITE, canon("Hopeful Ss."), 3)
            assertNotNull(a)
            assertEquals(a, b)
        }

        @Test
        fun `a bare single-token abbreviation carries no identity and fails closed`() {
            assertNull(canon("S."))
            assertNull(canon("C."))
            assertNull(canon("Stakes."))
        }

        @Test
        fun `an ambiguous truncated race fails closed`() {
            // A single-letter tail fits two races, so the uniqueness guard refuses it rather than
            // guessing: "Kyoto K." -> Kimpai/Kinen, "Nakayama K." -> Kimpai/Kinen.
            assertNull(canon("Kyoto K."))
            assertNull(canon("Nakayama K."))
        }

        @Test
        fun `a longer exact prefix that separates the pair resolves`() {
            // Extending the tail to an exact prefix that only one race carries makes it unique: "Kim" is
            // an exact prefix of Kimpai but not Kinen, so it resolves where the single letter could not.
            assertEquals("Kyoto Kimpai", canon("Kyoto Kim."))
        }

        @Test
        fun `garbage ending in a period does not snap onto a race`() {
            assertNull(canon("Xqzzptdf W."))
            assertNull(canon("Wglmr Qz."))
        }

        @Test
        fun `the abbreviation path is race-domain only and never fires off the white kind`() {
            // Stat, aptitude, and unique cards are pixel-classified and never carry a race name, so a race
            // abbreviation presented under one of those kinds must not resolve.
            assertNull(canon("Hopeful S.", SparkRowKind.STAT))
            assertNull(canon("Hopeful S.", SparkRowKind.APTITUDE))
            assertNull(canon("Hopeful S.", SparkRowKind.UNIQUE))
        }

        @Test
        fun `a read without the abbreviation period is not treated as an abbreviation`() {
            // The trailing period is the truncation signal. Without it, a too-short read fails closed on
            // the ordinary fuzzy path rather than being snapped by prefix.
            assertNull(canon("Hopeful S"))
        }

        @Test
        fun `an existing strong race read is unchanged by the abbreviation path`() {
            // A full clean race name still resolves exactly as before (the abbreviation path only runs
            // after a fuzzy reject, so it can never override a strong read).
            val r = res("Yasuda Kinen")
            assertEquals("Yasuda Kinen", r.canonicalName)
            assertEquals(FactorAcceptancePath.STRONG, r.path)
        }
    }

    @Nested
    @DisplayName("synthetic domain: exact-skeleton and ambiguity")
    inner class Synthetic {
        private val json =
            """
            {
              "schemaVersion": 1,
              "source": "test",
              "counts": {},
              "families": {
                "stat": ["Speed"],
                "aptitude": ["Mile"],
                "unique": ["Alpha One", "Alpha One Two"],
                "skill": ["Track Blazer"],
                "race": ["All Comers"],
                "scenario": ["Trackblazer"]
              }
            }
            """.trimIndent()
        private val d = VeteranFactorDomain.parse(json) ?: error("synthetic domain should parse")

        @Test
        fun `an exact skeleton hit is STRONG at score one`() {
            val res = d.resolve("alpha one", SparkRowKind.UNIQUE)
            assertEquals("Alpha One", res.canonicalName)
            assertEquals(FactorAcceptancePath.STRONG, res.path)
            assertEquals(1.0, res.bestScore)
        }

        @Test
        fun `a skeleton reached by two different canonical names fails closed`() {
            // "Track Blazer" (skill) and "Trackblazer" (scenario) both skeletonize to "trackblazer".
            // The exact path must refuse it, and the fuzzy fallback rejects on zero margin, so a WHITE
            // read of that skeleton resolves to nothing rather than picking one arbitrarily.
            assertNull(d.resolve("trackblazer", SparkRowKind.WHITE).canonicalName)
        }

        @Test
        fun `the domain fails closed for a kind whose asset family was empty is impossible - every kind has candidates`() {
            assertTrue(d.candidateCount(SparkRowKind.STAT) > 0)
            assertTrue(d.candidateCount(SparkRowKind.WHITE) >= 3)
        }
    }

    @Nested
    @DisplayName("synthetic domain: abbreviation algorithm in isolation")
    inner class AbbreviationSynthetic {
        // Race final words are long enough that the abbreviated reads below fall well under the fuzzy
        // floor, so every accept here comes from the abbreviation path, not a coincidental fuzzy hit -
        // and the cases stay deterministic no matter how the shipped races.json evolves.
        private val json =
            """
            {
              "schemaVersion": 1,
              "source": "test",
              "counts": {},
              "families": {
                "stat": ["Speed"],
                "aptitude": ["Mile"],
                "unique": ["Alpha One"],
                "skill": ["Zulu Skillcraft"],
                "race": ["Alpha Championship", "Alpha Charity", "Bravo Invitational"],
                "scenario": ["Trackblazer"]
              }
            }
            """.trimIndent()
        private val d = VeteranFactorDomain.parse(json) ?: error("synthetic domain should parse")
        private fun canon(raw: String, kind: SparkRowKind = SparkRowKind.WHITE) = d.resolve(raw, kind).canonicalName

        @Test
        fun `a unique exact-prefix truncation resolves through the abbreviation path`() {
            val r = d.resolve("Bravo I.", SparkRowKind.WHITE)
            assertEquals("Bravo Invitational", r.canonicalName)
            assertEquals(FactorAcceptancePath.ABBREVIATION, r.path)
        }

        @Test
        fun `a misread in the final abbreviation fails closed under exact-prefix matching`() {
            // "Bravo Ix." mis-reads the "n" of "Invitational" as an "x". The final token must be an EXACT
            // prefix of the canonical word, and "ix" is not a prefix of "invitational", so it fails
            // closed rather than being snapped by a fuzzy tail - the longer, noisier reads that carry
            // real identity are the fuzzy path's job, not this one's.
            assertNull(canon("Bravo Ix."))
        }

        @Test
        fun `a prefix shared by two races fails closed until it separates them`() {
            // "Alpha C." fits both Alpha Championship and Alpha Charity, so the uniqueness guard refuses
            // it; extending the prefix to "Champ" leaves only Championship, so it resolves.
            assertNull(canon("Alpha C."))
            assertEquals("Alpha Championship", canon("Alpha Champ."))
        }

        @Test
        fun `a complete token that does not match its canonical counterpart is rejected`() {
            // "Zzz I." shares no leading token with Bravo Invitational, so the abbreviation is not that
            // race even though the final letter would prefix "Invitational".
            assertNull(canon("Zzz I."))
        }

        @Test
        fun `a truncated skill or scenario is never rescued as a race`() {
            // The abbreviation path scores only against the race family, so a WHITE read that is really a
            // truncated skill/scenario is not snapped onto a race with a matching shape.
            assertNull(canon("Zulu S."))
            assertNull(canon("Track B."))
        }
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
