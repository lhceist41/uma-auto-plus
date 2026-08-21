package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.SparkRowKind
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
        fun `a truncated race name stays unresolved rather than guessing`() {
            // The factor card abbreviates a long race name ("Mile Ch." for "Mile Championship"); with
            // no reliable canonical to reach, it fails closed instead of snapping onto the wrong race.
            assertNull(domain.resolve("Mile Ch.", SparkRowKind.WHITE).canonicalName)
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
