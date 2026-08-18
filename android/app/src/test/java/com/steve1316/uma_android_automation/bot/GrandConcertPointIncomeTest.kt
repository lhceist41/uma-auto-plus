package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The per-turn Grand Concert performance-point income record. These cases pin the truthfulness
 * invariants: the record reports the trained facility's own observed "+N" gain as the
 * training-attributable income, it never fabricates an amount it could not read, it never falls back
 * to the static facility map, and it never invents a before/after delta the hook cannot observe.
 */
@DisplayName("Grand Concert point income record")
class GrandConcertPointIncomeTest {
    private val da = PerformancePointType.DANCE
    private val pa = PerformancePointType.PASSION
    private val vo = PerformancePointType.VOCAL
    private val vi = PerformancePointType.VISUAL
    private val co = PerformancePointType.COMPOSURE

    private fun balances(d: Int?, p: Int?, v: Int?, i: Int?, c: Int?): Map<PerformancePointType, Int?> =
        mapOf(da to d, pa to p, vo to v, vi to i, co to c)

    private fun format(
        gains: Map<PerformancePointType, Int?>,
        selected: StatName = StatName.STAMINA,
        ppBefore: Map<PerformancePointType, Int?>? = balances(10, 20, 30, 40, 50),
        currentSongDemand: Map<PerformancePointType, Int> = emptyMap(),
        nextSongDemand: Map<PerformancePointType, Int> = emptyMap(),
        numRainbow: Int = 0,
        numSkillHints: Int = 0,
    ): String =
        GrandConcertPointIncome.format(
            turn = 59,
            selected = selected,
            gains = gains,
            ppBefore = ppBefore,
            concertIn = 3,
            songsBoughtThisCycle = 1,
            purchasedFloor = 3,
            songsBoughtThisCareer = 11,
            expectedSongsByNow = 15,
            currentSongDemand = currentSongDemand,
            nextSongDemand = nextSongDemand,
            numRainbow = numRainbow,
            numSkillHints = numSkillHints,
        )

    @Nested
    @DisplayName("Case 1: clean single-color gain")
    inner class CleanSingleColor {
        @Test
        fun `single readable color is attributed to training with the exact amount`() {
            val line = format(gains = mapOf(pa to 23))
            assertTrue(line.contains("income=[Pa+23]"), line)
            assertTrue(line.contains("observed=Pa"), line)
            assertTrue(line.contains("attribution=training"), line)
            assertEquals(GrandConcertPointIncome.Attribution.TRAINING, GrandConcertPointIncome.classify(mapOf(pa to 23)))
        }

        @Test
        fun `pre-training balance anchor is carried per color`() {
            val line = format(gains = mapOf(pa to 23), ppBefore = balances(2, 2, 69, 6, 22))
            assertTrue(line.contains("ppBefore=[Da=2,Pa=2,Vo=69,Vi=6,Co=22]"), line)
        }
    }

    @Nested
    @DisplayName("Case 2: multi-color gain")
    inner class MultiColor {
        @Test
        fun `friendship split reports every observed color and amount in enum order`() {
            val line = format(gains = mapOf(pa to 8, da to 13))
            assertTrue(line.contains("income=[Da+13,Pa+8]"), line)
            assertTrue(line.contains("observed=Da,Pa"), line)
            assertTrue(line.contains("attribution=training"), line)
        }
    }

    @Nested
    @DisplayName("Case 3: no PP gain read")
    inner class NoGain {
        @Test
        fun `empty preview is unknown and never a fabricated zero or number`() {
            val line = format(gains = emptyMap())
            assertTrue(line.contains("income=[none]"), line)
            assertTrue(line.contains("observed=none"), line)
            assertTrue(line.contains("attribution=unknown"), line)
            // No fabricated magnitude appears anywhere in the income field.
            assertFalse(line.contains("income=[Da+0"), line)
            assertEquals(GrandConcertPointIncome.Attribution.UNKNOWN, GrandConcertPointIncome.classify(emptyMap()))
        }
    }

    @Nested
    @DisplayName("Case 4: partial read is ambiguous, not clean attribution")
    inner class PartialRead {
        @Test
        fun `glyph seen but amount unread is marked ambiguous with no invented amount`() {
            val gains = mapOf(vi to null)
            val line = format(gains = gains)
            assertTrue(line.contains("income=[Vi+?]"), line)
            assertTrue(line.contains("observed=Vi"), line)
            assertTrue(line.contains("attribution=ambiguous"), line)
            assertEquals(GrandConcertPointIncome.Attribution.AMBIGUOUS, GrandConcertPointIncome.classify(gains))
        }

        @Test
        fun `a readable color mixed with an unread color stays ambiguous`() {
            val gains = mapOf(da to 13, pa to null)
            assertEquals(GrandConcertPointIncome.Attribution.AMBIGUOUS, GrandConcertPointIncome.classify(gains))
            val line = format(gains = gains)
            assertTrue(line.contains("income=[Da+13,Pa+?]"), line)
        }
    }

    @Nested
    @DisplayName("Case 5: observed color overrides the static facility prior")
    inner class ObservedOverridesStatic {
        @Test
        fun `Wit training that observed Dance reports Dance, not the static Composure prior`() {
            // GrandConcertFacilityModel maps WIT -> COMPOSURE. The record must report the observed
            // per-turn color and must never consult that static map.
            assertEquals(PerformancePointType.COMPOSURE, GrandConcertFacilityModel.staticPrimaryType(StatName.WIT))
            val line = format(gains = mapOf(da to 15), selected = StatName.WIT)
            assertTrue(line.contains("selected=WIT"), line)
            assertTrue(line.contains("income=[Da+15]"), line)
            assertTrue(line.contains("observed=Da"), line)
            assertFalse(line.contains("Co+"), line)
        }
    }

    @Nested
    @DisplayName("record shape")
    inner class RecordShape {
        @Test
        fun `demand, cycle, and correlation fields are carried on one line`() {
            val line =
                format(
                    gains = mapOf(pa to 12),
                    currentSongDemand = mapOf(pa to 19, vi to 21),
                    nextSongDemand = mapOf(co to 10),
                    numRainbow = 1,
                    numSkillHints = 2,
                )
            assertTrue(line.startsWith("[TRAINING] [GC_PP_INCOME] "), line)
            assertTrue(line.contains("turn=59"), line)
            assertTrue(line.contains("concertIn=3"), line)
            assertTrue(line.contains("cycleSongs=1/3"), line)
            assertTrue(line.contains("careerSongs=11/15"), line)
            assertTrue(line.contains("demand=[song:Pa:19,Vi:21 next:Co:10]"), line)
            assertTrue(line.contains("rainbows=1"), line)
            assertTrue(line.contains("hints=2"), line)
        }

        @Test
        fun `an unread balance component prints as a question mark, never a guess`() {
            val line = format(gains = mapOf(pa to 8), ppBefore = balances(10, null, 30, 40, 50))
            assertTrue(line.contains("ppBefore=[Da=10,Pa=?,Vo=30,Vi=40,Co=50]"), line)
        }

        @Test
        fun `a null balance map prints all components unknown`() {
            val line = format(gains = mapOf(pa to 8), ppBefore = null)
            assertTrue(line.contains("ppBefore=[Da=?,Pa=?,Vo=?,Vi=?,Co=?]"), line)
        }
    }
}
