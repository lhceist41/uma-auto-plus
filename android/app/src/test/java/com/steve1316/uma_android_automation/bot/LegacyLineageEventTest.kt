package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.VeteranFactorDomain
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

@DisplayName("Legacy lineage event assembly and serialization")
class LegacyLineageEventTest {
    private fun factor(kind: SparkRowKind, name: String, stars: Int, ambiguous: Boolean = false, clipped: Boolean = false) =
        LineageFactorObservation(kind, name, stars, ambiguous, clipped)

    /** A complete ancestor: stat, aptitude, unique, plus a white row, all clean. */
    private fun completeAncestor(seed: String, portrait: Boolean = true) =
        LineageAncestorObservation(
            portraitObserved = portrait,
            factors =
                listOf(
                    factor(SparkRowKind.STAT, "Power $seed", 3),
                    factor(SparkRowKind.APTITUDE, "Mile $seed", 2),
                    factor(SparkRowKind.UNIQUE, "Unique $seed", 3),
                    factor(SparkRowKind.WHITE, "Arima Kinen", 1),
                ),
        )

    private fun sixComplete() = (1..6).map { completeAncestor("g$it") }

    @Nested
    @DisplayName("assembly")
    inner class Assembly {
        @Test
        fun `six observed blocks map to the six roles in deterministic capture order`() {
            val event = assembleLineageEvent("tx-1", 1000L, "URA Finale", "Special Week", null, guestsIncluded = false, sixComplete())
            assertEquals(
                listOf(
                    LineageAncestorRole.LEGACY1_PARENT,
                    LineageAncestorRole.LEGACY1_GRANDPARENT_A,
                    LineageAncestorRole.LEGACY1_GRANDPARENT_B,
                    LineageAncestorRole.LEGACY2_PARENT,
                    LineageAncestorRole.LEGACY2_GRANDPARENT_A,
                    LineageAncestorRole.LEGACY2_GRANDPARENT_B,
                ),
                event.ancestors.map { it.role },
            )
            assertEquals(listOf(0, 1, 2, 3, 4, 5), event.ancestors.map { it.slotIndex })
        }

        @Test
        fun `all six complete ancestors reads as CAPTURED`() {
            val event = assembleLineageEvent("tx", 1L, "URA Finale", "Special Week", null, guestsIncluded = false, sixComplete())
            assertEquals(LineageCaptureStatus.CAPTURED, event.captureStatus)
            assertTrue(event.ancestors.all { it.completeness >= 1.0 })
        }

        @Test
        fun `fewer than six observed blocks is PARTIAL`() {
            val event = assembleLineageEvent("tx", 1L, "URA Finale", "Special Week", null, guestsIncluded = false, sixComplete().take(4))
            assertEquals(4, event.ancestors.size)
            assertEquals(LineageCaptureStatus.PARTIAL, event.captureStatus)
        }

        @Test
        fun `a block missing its lead triple is reported partial, not dropped`() {
            val ancestors = sixComplete().toMutableList()
            // The last grandparent's stat row scrolled off: only aptitude/unique/white captured.
            ancestors[5] =
                LineageAncestorObservation(
                    portraitObserved = false,
                    factors = listOf(factor(SparkRowKind.APTITUDE, "Mile", 2), factor(SparkRowKind.UNIQUE, "U", 1)),
                )
            val event = assembleLineageEvent("tx", 1L, "URA Finale", "Special Week", null, guestsIncluded = false, ancestors)
            assertEquals(6, event.ancestors.size, "the partial ancestor is kept, not dropped")
            assertFalse(event.ancestors[5].hasLeadTriple)
            assertTrue(event.ancestors[5].completeness < 1.0)
            assertEquals(LineageCaptureStatus.PARTIAL, event.captureStatus)
        }

        @Test
        fun `a clipped or ambiguous row lowers completeness and forces PARTIAL`() {
            val ancestors = sixComplete().toMutableList()
            ancestors[2] =
                LineageAncestorObservation(
                    portraitObserved = true,
                    factors =
                        listOf(
                            factor(SparkRowKind.STAT, "Power", 2, ambiguous = true),
                            factor(SparkRowKind.APTITUDE, "Mile", 2),
                            factor(SparkRowKind.UNIQUE, "U", 1, clipped = true),
                        ),
                )
            val event = assembleLineageEvent("tx", 1L, "URA Finale", "SW", null, guestsIncluded = false, ancestors)
            assertTrue(event.ancestors[2].completeness < 1.0)
            assertEquals(LineageCaptureStatus.PARTIAL, event.captureStatus)
        }

        @Test
        fun `no observed blocks is FAILED`() {
            val event = assembleLineageEvent("tx", 1L, "URA Finale", "SW", null, guestsIncluded = false, emptyList())
            assertEquals(LineageCaptureStatus.FAILED, event.captureStatus)
            assertTrue(event.ancestors.isEmpty())
        }

        @Test
        fun `guests off supports owned probable match, guests on stays unknown and unresolved`() {
            val owned = assembleLineageEvent("tx", 1L, "URA Finale", "SW", null, guestsIncluded = false, sixComplete())
            assertTrue(owned.ancestors.all { it.ownership == LineageOwnership.OWNED && it.matchStatus == LineageMatch.PROBABLE_OWNED_MATCH })
            val guests = assembleLineageEvent("tx", 1L, "URA Finale", "SW", null, guestsIncluded = true, sixComplete())
            assertTrue(guests.ancestors.all { it.ownership == LineageOwnership.UNKNOWN && it.matchStatus == LineageMatch.UNRESOLVED })
            assertTrue(owned.ancestors.all { it.probableVeteranId == null }, "never promoted to exact without a game identifier")
        }
    }

    @Nested
    @DisplayName("factor fingerprint")
    inner class Fingerprint {
        @Test
        fun `is order-independent and stable`() {
            val a = listOf(factor(SparkRowKind.STAT, "Power", 3), factor(SparkRowKind.APTITUDE, "Mile", 2), factor(SparkRowKind.UNIQUE, "Zed", 1))
            val b = a.reversed()
            assertEquals(ancestorFactorFingerprint(a), ancestorFactorFingerprint(b))
        }

        @Test
        fun `normalizes OCR casing and whitespace but keeps stars distinct`() {
            val one = listOf(factor(SparkRowKind.WHITE, "  arima   kinen ", 2))
            val two = listOf(factor(SparkRowKind.WHITE, "ARIMA KINEN", 2))
            val three = listOf(factor(SparkRowKind.WHITE, "ARIMA KINEN", 1))
            assertEquals(ancestorFactorFingerprint(one), ancestorFactorFingerprint(two))
            assertFalse(ancestorFactorFingerprint(two) == ancestorFactorFingerprint(three))
        }
    }

    @Nested
    @DisplayName("serialization")
    inner class Serialization {
        @Test
        fun `emits a lineage_selected record with the id, status, and six ancestors`() {
            val event = assembleLineageEvent("tx-42", 12345L, "URA Finale", "Special Week", "double", guestsIncluded = false, sixComplete())
            val json = serializeLineageEvent(event)
            assertEquals("lineage_selected", json.getString("type"))
            assertEquals(LINEAGE_SCHEMA_VERSION, json.getInt("schemaVersion"))
            assertEquals("tx-42", json.getString("launchTransactionId"))
            assertEquals(12345L, json.getLong("ts"))
            assertEquals("captured", json.getString("captureStatus"))
            assertEquals("double", json.getString("overallAffinity"))
            assertEquals(6, json.getJSONArray("ancestors").length())
            val first = json.getJSONArray("ancestors").getJSONObject(0)
            assertEquals("legacy1_parent", first.getString("role"))
            assertEquals("owned", first.getString("ownership"))
            assertTrue(first.getJSONArray("factors").length() >= 3)
        }

        @Test
        fun `an unreadable OCR row is retained as a factor with empty text, never dropped`() {
            val ancestors =
                listOf(
                    LineageAncestorObservation(
                        portraitObserved = true,
                        factors =
                            listOf(
                                factor(SparkRowKind.STAT, "Power", 2),
                                factor(SparkRowKind.APTITUDE, "", 2, ambiguous = true), // OCR failed on the name
                                factor(SparkRowKind.UNIQUE, "U", 1),
                            ),
                    ),
                )
            val json = serializeLineageEvent(assembleLineageEvent("tx", 1L, "URA Finale", "SW", null, false, ancestors))
            val factors = json.getJSONArray("ancestors").getJSONObject(0).getJSONArray("factors")
            assertEquals(3, factors.length(), "the unreadable row is kept as evidence")
            assertEquals("", factors.getJSONObject(1).getString("displayText"))
            assertTrue(factors.getJSONObject(1).getBoolean("ambiguous"))
        }

        @Test
        fun `a null launch id omits the field rather than writing null`() {
            val json = serializeLineageEvent(assembleLineageEvent(null, 1L, "URA Finale", "SW", null, false, sixComplete()))
            assertFalse(json.has("launchTransactionId"))
        }
    }

    @Nested
    @DisplayName("canonical reconciliation (PL-4 <-> Inspiration)")
    inner class CanonicalReconciliation {
        private val domain =
            VeteranFactorDomain.parse(assetFile(VeteranFactorDomain.ASSET_NAME).readText())
                ?: error("the shipped veteran_factor_domain.json should parse")

        /** One ancestor's four factors, real in-domain names, with the white row's OCR supplied by the
         * caller so a jittered read can be compared against a clean one. */
        private fun taikiLikeAncestor(whiteOcr: String) =
            listOf(
                LineageAncestorObservation(
                    portraitObserved = true,
                    factors =
                        listOf(
                            factor(SparkRowKind.STAT, "Speed", 2),
                            factor(SparkRowKind.APTITUDE, "Mile", 2),
                            factor(SparkRowKind.UNIQUE, "Shooting for Victory!", 1),
                            factor(SparkRowKind.WHITE, whiteOcr, 1),
                        ),
                ),
            )

        @Test
        fun `raw jitter breaks the raw fingerprint but the canonical fingerprint holds`() {
            val clean = assembleLineageEvent("tx", 1L, "URA Finale", "Taiki Shuttle", null, false, taikiLikeAncestor("Firm Conditions"), domain).ancestors[0]
            val jittered = assembleLineageEvent("tx", 1L, "URA Finale", "Taiki Shuttle", null, false, taikiLikeAncestor("FIRM CONDITIONSO"), domain).ancestors[0]
            // The whole PL-R1c problem: the raw fingerprints diverge on the glued glyph...
            assertNotEquals(clean.factorFingerprint, jittered.factorFingerprint)
            // ...but both snap onto the same canonical name, so the canonical fingerprints agree.
            assertNotNull(clean.canonicalFactorFingerprint)
            assertEquals(clean.canonicalFactorFingerprint, jittered.canonicalFactorFingerprint)
            assertTrue(clean.factorSetTrusted && jittered.factorSetTrusted)
        }

        @Test
        fun `a lineage ancestor and an owned Veteran's self block cross-link on the canonical fingerprint`() {
            // The PL-4b Taiki cross-link: a career launched off an owned Taiki should join back to that
            // Veteran's Inspiration self block. The two screens read the same factors, but the OCR
            // differs; canonicalization is what makes the join hold.
            val lineage = assembleLineageEvent("tx", 1L, "URA Finale", "Taiki Shuttle", null, false, taikiLikeAncestor("FIRM CONDITIONSO"), domain).ancestors[0]
            val selfFactors =
                listOf(
                    Triple(SparkRowKind.STAT, "Speed", 2),
                    Triple(SparkRowKind.APTITUDE, "Mile", 2),
                    Triple(SparkRowKind.UNIQUE, "Shooting for Victory!", 1),
                    Triple(SparkRowKind.WHITE, "Firm Conditions", 1),
                ).mapIndexed { i, (kind, name, stars) ->
                    val res = domain.resolve(name, kind)
                    InspirationFactor(i, InspirationColumn.LEFT, kind, name, stars, ambiguous = false, canonicalName = res.canonicalName, canonicalPath = res.path)
                }
            val self = InspirationAncestor(0, true, null, selfFactors)
            assertNotNull(self.factorFingerprint)
            assertEquals(self.factorFingerprint, lineage.canonicalFactorFingerprint, "the two sources produce byte-identical canonical set fingerprints")
        }

        @Test
        fun `an unresolved factor leaves the canonical fingerprint null but keeps the structural one`() {
            val garbage = assembleLineageEvent("tx", 1L, "URA Finale", "SW", null, false, taikiLikeAncestor("Xqzzptdf"), domain).ancestors[0]
            assertNull(garbage.canonicalFactorFingerprint, "one off-domain name blocks the trusted set fingerprint")
            assertFalse(garbage.factorSetTrusted)
            assertTrue(garbage.structuralFactorFingerprint.isNotEmpty(), "the name-free fallback identity still stands")
            assertTrue(garbage.factorFingerprint.isNotEmpty(), "raw evidence is preserved unchanged")
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
