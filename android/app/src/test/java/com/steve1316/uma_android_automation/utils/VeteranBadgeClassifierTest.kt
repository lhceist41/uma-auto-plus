package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Fixture-pinned tests for the styled-field pixel classifiers ([classifyRankMedal],
 * [classifyStatGrade], [classifyAptitudeGrade]) and the name/outfit canonical matcher.
 *
 * The grade and rank tests run the real image-processing path against two committed detail-dialog
 * crops (Taiki Shuttle and Copano Rickey, `fixtures/veteranroster/`, decoded by [FixturePng] the same
 * way the runtime reads a Bitmap through [SparkPixelSampler]). The two Veterans overlap enough to
 * catch a one-frame overfit: the B and C grades and the A/G aptitudes each appear at more than one
 * screen position across the pair, and Copano supplies the SS+ / D anti-overfit that a Taiki-only
 * build would miss.
 */
@DisplayName("VeteranBadgeClassifier Tests")
class VeteranBadgeClassifierTest {
    private fun sampler(name: String): SparkPixelSampler {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/fixtures/veteranroster/$name")) { "missing fixture $name" }
        val png = stream.use { FixturePng.read(it) }
        return SparkPixelSampler { x, y -> png.getRGB(x, y) }
    }

    private val taiki get() = sampler("veteran_taiki_details_top.png")
    private val copano get() = sampler("veteran_copano_details_top.png")

    // Rank-tier fixtures, two distinct Veterans per tier. The "_a" frame is the one each template was
    // baked from; the "_b" frame is a different Veteran of the same tier, so a passing "_b" assertion
    // proves the template generalizes rather than echoing its own source. Identities are in PROVENANCE.
    private val aplusA get() = sampler("veteran_aplus_a_details_top.png")
    private val aplusB get() = sampler("veteran_aplus_b_details_top.png")
    private val sA get() = sampler("veteran_s_a_details_top.png")
    private val sB get() = sampler("veteran_s_b_details_top.png")
    private val splusA get() = sampler("veteran_splus_a_details_top.png")
    private val splusB get() = sampler("veteran_splus_b_details_top.png")

    /** All white: stands in for a blank/wrong region so every classifier's fail-closed path is pinned. */
    private val blank = SparkPixelSampler { _, _ -> 0xFFFFFFFF.toInt() }

    @Nested
    @DisplayName("Rank medal - colour family then whole-medal template correlation")
    inner class RankMedal {
        @Test
        fun `both A fixtures classify the A medal as A`() {
            assertEquals("A", classifyRankMedal(taiki))
            assertEquals("A", classifyRankMedal(copano))
        }

        @Test
        fun `each calibrated tier classifies on a Veteran the template was not baked from`() {
            // _b is a different Veteran than the template source, so these prove generalization, not
            // self-recognition: A+ vs A share the orange family and S+ vs S share the gold family, and
            // only the "+" separates each pair.
            assertEquals("A+", classifyRankMedal(aplusA))
            assertEquals("A+", classifyRankMedal(aplusB))
            assertEquals("S", classifyRankMedal(sA))
            assertEquals("S", classifyRankMedal(sB))
            assertEquals("S+", classifyRankMedal(splusA))
            assertEquals("S+", classifyRankMedal(splusB))
        }

        @Test
        fun `the plus is never confused with its sibling in either colour family`() {
            // The failure this guards: an A read as A+ (or S as S+) would mint a wrong immutable
            // identity. Every fixture must land on exactly its own tier, never the sibling.
            assertEquals("A", classifyRankMedal(taiki))
            assertEquals("A", classifyRankMedal(copano))
            assertEquals("A+", classifyRankMedal(aplusB))
            assertEquals("S", classifyRankMedal(sB))
            assertEquals("S+", classifyRankMedal(splusB))
        }

        @Test
        fun `a blank medal region stays unresolved`() {
            assertNull(classifyRankMedal(blank))
        }
    }

    @Nested
    @DisplayName("Stat grade badges - colour family plus structural + and SS")
    inner class StatGrades {
        @Test
        fun `Taiki stat grades read A+ B B B C`() {
            val s = taiki
            val expected = listOf("A+", "B", "B", "B", "C")
            for (i in STAT_GRADE_GLYPH_BOXES.indices) {
                assertEquals(expected[i], classifyStatGrade(s, STAT_GRADE_GLYPH_BOXES[i]), "stat cell $i")
            }
        }

        @Test
        fun `Copano stat grades read SS+ D B C B, keeping the doubled SS and the plus`() {
            val s = copano
            val expected = listOf("SS+", "D", "B", "C", "B")
            for (i in STAT_GRADE_GLYPH_BOXES.indices) {
                assertEquals(expected[i], classifyStatGrade(s, STAT_GRADE_GLYPH_BOXES[i]), "stat cell $i")
            }
        }

        @Test
        fun `a blank stat cell stays unresolved`() {
            assertNull(classifyStatGrade(blank, STAT_GRADE_GLYPH_BOXES[0]))
        }
    }

    @Nested
    @DisplayName("Aptitude grade letters - one shared colour classifier for all ten cells")
    inner class AptitudeGrades {
        @Test
        fun `Taiki resolves all ten aptitude grades`() {
            val s = taiki
            val expected =
                mapOf(
                    "turf" to "A", "dirt" to "B", "sprint" to "A", "mile" to "A", "medium" to "E",
                    "long" to "G", "front" to "C", "pace" to "A", "late" to "E", "end" to "G",
                )
            for ((role, box) in APTITUDE_GRADE_BOXES) {
                assertEquals(expected[role], classifyAptitudeGrade(s, box), role)
            }
        }

        @Test
        fun `Copano resolves all ten, including F S and the cyan-vs-purple families`() {
            val s = copano
            val expected =
                mapOf(
                    "turf" to "F", "dirt" to "A", "sprint" to "C", "mile" to "A", "medium" to "A",
                    "long" to "G", "front" to "A", "pace" to "S", "late" to "C", "end" to "G",
                )
            for ((role, box) in APTITUDE_GRADE_BOXES) {
                assertEquals(expected[role], classifyAptitudeGrade(s, box), role)
            }
        }

        @Test
        fun `a blank aptitude cell stays unresolved`() {
            assertNull(classifyAptitudeGrade(blank, APTITUDE_GRADE_BOXES.getValue("turf")))
        }
    }

    @Nested
    @DisplayName("Stat value recovery-box geometry - holds the number, cuts in the gap")
    inner class StatValueRecoveryGeometry {
        // The five detail fixtures that keep the stat row (Taiki/Copano at 820 rows, the four 600-row
        // rank fixtures). All stat numbers here are 3-digit in the widened columns, so these pin the
        // no-regression / no-badge invariants offline; the 4-digit clip recovery is proven live.
        private val statFixtures =
            listOf("taiki" to taiki, "copano" to copano, "aplusA" to aplusA, "sA" to sA, "splusA" to splusA)

        /** A brown stat-number glyph pixel (same rule as the offline crop analysis): red-dominant,
         * moderate saturation, not white background. */
        private fun isBrown(argb: Int): Boolean {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val mx = maxOf(r, g, b)
            val mn = minOf(r, g, b)
            if (mx < 60) return false
            if (mx > 245 && mn > 225) return false
            val sat = if (mx == 0) 0.0 else (mx - mn).toDouble() / mx
            return r >= g && r - b >= 28 && sat >= 0.20
        }

        /** The brown-number bounding box searched from the recovery box's own left edge rightward. That
         * edge is proven to sit in the white gap clear of the grade badge by the whitespace test, so the
         * search never picks up a badge glyph; a number clipped on the right or trimmed top/bottom by the
         * box would still show up touching the box edge. */
        private fun numberBox(s: SparkPixelSampler, box: GlyphBox): GlyphBox? {
            var x0 = Int.MAX_VALUE
            var y0 = Int.MAX_VALUE
            var x1 = Int.MIN_VALUE
            var y1 = Int.MIN_VALUE
            for (y in box.y0 until box.y1) {
                for (x in box.x0 until (box.x1 + 4)) {
                    if (isBrown(s.argb(x, y))) {
                        if (x < x0) x0 = x
                        if (y < y0) y0 = y
                        if (x > x1) x1 = x
                        if (y > y1) y1 = y
                    }
                }
            }
            return if (x1 < x0) null else GlyphBox(x0, y0, x1, y1)
        }

        @Test
        fun `every recovery box fully contains its stat number`() {
            for ((name, s) in statFixtures) {
                for (i in STAT_VALUE_RECOVERY_BOXES.indices) {
                    val box = STAT_VALUE_RECOVERY_BOXES[i] ?: continue
                    val num = numberBox(s, box)
                    assertNotNull(num, "$name ${STAT_LABELS[i]}: no number found near recovery box")
                    assertTrue(
                        num!!.x0 >= box.x0 && num.x1 <= box.x1 && num.y0 >= box.y0 && num.y1 <= box.y1,
                        "$name ${STAT_LABELS[i]}: number $num escapes recovery box $box",
                    )
                }
            }
        }

        @Test
        fun `every recovery box left edge cuts through the white gap, never a glyph`() {
            // The widened left edge must land in the whitespace between the grade badge (whose "+"
            // shares the number's hue) and the number, so it can never grab a badge pixel or bisect a
            // digit. Checked across the digit y-band on every fixture.
            for ((name, s) in statFixtures) {
                for (i in STAT_VALUE_RECOVERY_BOXES.indices) {
                    val box = STAT_VALUE_RECOVERY_BOXES[i] ?: continue
                    var glyphPixels = 0
                    for (y in 523..554) {
                        for (x in box.x0 - 1..box.x0 + 1) {
                            if (isBrown(s.argb(x, y))) glyphPixels++
                        }
                    }
                    assertTrue(glyphPixels <= 2, "$name ${STAT_LABELS[i]}: recovery left edge ${box.x0} sits on a glyph ($glyphPixels px)")
                }
            }
        }

        @Test
        fun `every widened recovery box is wide enough for a four-digit value`() {
            // A single digit is ~21px wide here; a 4-digit value needs ~84px plus margins. The primary
            // boxes that clipped are all narrower than this, which is exactly why they clipped.
            for (i in STAT_VALUE_RECOVERY_BOXES.indices) {
                val box = STAT_VALUE_RECOVERY_BOXES[i] ?: continue
                assertTrue(box.x1 - box.x0 >= 88, "${STAT_LABELS[i]} recovery box only ${box.x1 - box.x0}px wide")
            }
        }
    }

    @Nested
    @DisplayName("Detail dialog chevrons - green population, not a point sample")
    inner class Chevrons {
        @Test
        fun `both fixtures read both chevrons as enabled`() {
            for ((name, s) in listOf("taiki" to taiki, "copano" to copano)) {
                assertEquals(ChevronState.ENABLED, classifyChevron(s, CHEVRON_NEXT_BOX), "$name next")
                assertEquals(ChevronState.ENABLED, classifyChevron(s, CHEVRON_PREV_BOX), "$name prev")
            }
        }

        @Test
        fun `the enabled population sits far above the accept threshold on both fixtures`() {
            // The chevron pulses horizontally between frames, so the two fixtures catch it at
            // different offsets; the population is what stays stable, which is why the classifier
            // counts rather than sampling a point.
            for ((name, s) in listOf("taiki" to taiki, "copano" to copano)) {
                val green = countChevronGreen(s, CHEVRON_NEXT_BOX)
                assertTrue(green > CHEVRON_ENABLED_MIN_GREEN * 2, "$name next green=$green")
            }
        }

        @Test
        fun `a region beside the chevron carries no chevron green at all`() {
            // The separation the thresholds rely on: control boxes level with and below the chevron.
            assertEquals(0, countChevronGreen(taiki, GlyphBox(930, 630, 994, 740)))
            assertEquals(0, countChevronGreen(taiki, GlyphBox(1010, 745, 1074, 800)))
        }

        @Test
        fun `an absent chevron classifies disabled instead of unknown`() {
            assertEquals(ChevronState.DISABLED, classifyChevron(blank, CHEVRON_NEXT_BOX))
        }
    }
}
