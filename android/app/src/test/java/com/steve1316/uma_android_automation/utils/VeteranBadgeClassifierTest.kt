package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
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

    /** All white: stands in for a blank/wrong region so every classifier's fail-closed path is pinned. */
    private val blank = SparkPixelSampler { _, _ -> 0xFFFFFFFF.toInt() }

    @Nested
    @DisplayName("Rank medal - whole-medal template correlation")
    inner class RankMedal {
        @Test
        fun `both fixtures classify the A medal as A`() {
            assertEquals("A", classifyRankMedal(taiki))
            assertEquals("A", classifyRankMedal(copano))
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
