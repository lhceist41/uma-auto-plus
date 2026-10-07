package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.LessonCardKind
import com.steve1316.uma_android_automation.bot.SparkPagerNav
import com.steve1316.uma_android_automation.bot.SparkRowKind
import com.steve1316.uma_android_automation.bot.SparkSetSide
import com.steve1316.uma_android_automation.components.DialogBonusesUpdated
import com.steve1316.uma_android_automation.components.DialogUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** The Grand Concert and career-end spark probes on the tall phone captures (1080x2316, cutout inset 94; see fixtures/tallphone/PROVENANCE.md). */
@DisplayName("Grand Concert and spark screens on a tall phone")
class GrandConcertAndSparksOnTallPhoneTest {
    private val w = 1080
    private val h = 2316
    private var savedInset = 0

    @BeforeEach
    fun phoneInset() {
        savedInset = screenTopInset
        screenTopInset = 94
    }

    @AfterEach
    fun restoreInset() {
        screenTopInset = savedInset
    }

    private val cache = mutableMapOf<String, FixturePng>()

    private fun image(name: String): FixturePng =
        cache.getOrPut(name) {
            val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/tallphone/$name.png")) { "missing fixture $name.png" }
            stream.use { FixturePng.read(it) }
        }

    /** [dy] moves every read by that many pixels: the same frame read as if a site used a different band. */
    private fun sampler(
        name: String,
        dy: Int = 0,
    ): SparkPixelSampler {
        val img = image(name)
        return SparkPixelSampler { x, y -> img.getRGB(x, (y + dy).coerceIn(0, img.height - 1)) }
    }

    private val fixtures =
        listOf(
            "career_scheduled", "technique_list", "song_list", "learn_confirm_technique", "schedule_confirm_technique",
            "concert_pending", "concert_confirm", "concert4_pending", "concert4_confirm", "concert_playback", "concert_success_banner", "bonuses_updated",
            "active_bonuses_panel", "career_complete", "training_guts_selected",
            "sparks_screen", "keep_confirmation_plain", "sparks_rerolled_result", "spark_selection_intro",
            "pager_original", "pager_rerolled", "confirmation_rerolled",
        )

    /** Band distances on this phone: TOP +94, DIALOG +198, MIDDLE +245, BOTTOM +396. */
    private val towardTop = -104
    private val dialogToMiddle = 47
    private val middleToBottom = 151

    private class Probe(
        val positives: Set<String>,
        val wrongBandShifts: List<Int>,
        val read: (SparkPixelSampler, Int, Int) -> Boolean,
    )

    private val probes =
        mapOf(
            "lesson slot lit" to
                Probe(setOf("career_scheduled"), listOf(-middleToBottom)) { s, w, h ->
                    grandConcertLessonSlotState(s, w, h) == LessonSlotState.UNLOCKED_SCHEDULED
                },
            // Presence alone survives a shift (a dark top band and any coloured card bar); the card reads below carry the band.
            "lesson list" to Probe(setOf("technique_list", "song_list"), emptyList()) { s, w, h -> grandConcertLessonListPresent(s, w, h) },
            // The spark keep dialogs share the green header band, on MuMu too; the lesson confirmation adds the kind pill.
            "lesson dialog header" to
                Probe(setOf("learn_confirm_technique", "schedule_confirm_technique", "keep_confirmation_plain", "confirmation_rerolled"), listOf(towardTop, dialogToMiddle + middleToBottom)) { s, w, h ->
                    grandConcertDialogHeaderPresent(s, w, h)
                },
            "lesson confirmation" to
                Probe(setOf("learn_confirm_technique", "schedule_confirm_technique"), listOf(towardTop, dialogToMiddle)) { s, w, h ->
                    grandConcertLessonConfirmationPresent(s, w, h)
                },
            // Read only on an open lesson dialog, as the lesson reader does.
            "schedule shortfall" to
                Probe(setOf("schedule_confirm_technique"), listOf(towardTop, dialogToMiddle)) { s, w, h ->
                    grandConcertDialogHeaderPresent(s, w, h) && grandConcertScheduleShortfallPresent(s, w, h)
                },
            "concert pending" to Probe(setOf("concert_pending", "concert4_pending"), listOf(dialogToMiddle + middleToBottom, -middleToBottom)) { s, w, h -> grandConcertConcertPendingScreenPresent(s, w, h) },
            "concert start confirmation" to Probe(setOf("concert_confirm", "concert4_confirm"), listOf(towardTop, dialogToMiddle)) { s, w, h -> grandConcertConcertConfirmPresent(s, w, h) },
            "playback skip" to Probe(setOf("concert_playback"), listOf(-middleToBottom)) { s, w, h -> grandConcertPlaybackSkipPresent(s, w, h) },
            "result Next" to Probe(setOf("concert_success_banner"), listOf(-middleToBottom)) { s, w, h -> grandConcertResultNextPresent(s, w, h) },
            "Bonuses Updated" to Probe(setOf("bonuses_updated"), listOf(towardTop, dialogToMiddle)) { s, w, h -> grandConcertBonusesUpdatedPresent(s, w, h) },
            "Active Concert Bonuses" to Probe(setOf("active_bonuses_panel"), listOf(towardTop, dialogToMiddle)) { s, w, h -> grandConcertActiveBonusesPanelPresent(s, w, h) },
            "Complete Career" to Probe(setOf("career_complete"), listOf(-middleToBottom)) { s, w, h -> grandConcertCareerCompleteScreenPresent(s, w, h) },
            // The career screen shows the same Performance Points panel; the reader runs on training frames only.
            "performance panel" to Probe(setOf("training_guts_selected", "career_scheduled"), listOf(-towardTop)) { s, w, h -> grandConcertPerformancePanelPresent(s, w, h) },
            "spark keep confirmation" to
                Probe(setOf("keep_confirmation_plain", "confirmation_rerolled"), listOf(towardTop, dialogToMiddle + middleToBottom)) { s, w, h ->
                    sparkConfirmationStructurePresent(s, w, h)
                },
            "spark selection intro" to Probe(setOf("spark_selection_intro"), listOf(towardTop, dialogToMiddle + middleToBottom)) { s, w, h -> sparkIntroStructurePresent(s, w, h) },
            "spark pager" to Probe(setOf("pager_original", "pager_rerolled"), listOf(-dialogToMiddle, middleToBottom)) { s, w, h -> sparkPagerStructurePresent(s, w, h) },
            "Sparks Rerolled" to Probe(setOf("sparks_rerolled_result"), listOf(middleToBottom)) { s, w, h -> sparkRerolledStructurePresent(s, w, h) },
        )

    @Test
    fun `every phone fixture is a 1080x2316 capture`() {
        for (name in fixtures) {
            assertEquals(w, image(name).width, name)
            assertEquals(h, image(name).height, name)
        }
    }

    @Nested
    @DisplayName("each probe reads its screen on the phone and no other phone screen")
    inner class Matrix {
        @Test
        fun classification() {
            for ((probe, p) in probes) {
                for (name in fixtures) {
                    assertEquals(name in p.positives, p.read(sampler(name), w, h), "$probe on $name")
                }
            }
        }

        @Test
        @DisplayName("read on the old 1080x1920 points, the phone screens are not recognised")
        fun unmappedPointsMiss() {
            for ((probe, p) in probes) {
                if (p.wrongBandShifts.isEmpty()) continue
                for (name in p.positives) assertFalse(p.read(sampler(name), 1080, 1920), "$probe on $name without its band")
            }
        }

        @Test
        @DisplayName("a site read with a neighbouring band's offset misses")
        fun wrongBandMisses() {
            for ((probe, p) in probes) {
                for (name in p.positives) {
                    for (dy in p.wrongBandShifts) assertFalse(p.read(sampler(name, dy), w, h), "$probe on $name shifted by $dy")
                }
            }
        }
    }

    @Nested
    @DisplayName("Grand Concert reads")
    inner class GrandConcertReads {
        @Test
        fun lessonCardKinds() {
            for (card in 0..2) {
                assertEquals(LessonCardKind.TECHNIQUE, grandConcertLessonCardKind(sampler("technique_list"), card, w, h), "technique card $card")
                assertEquals(LessonCardKind.SONG, grandConcertLessonCardKind(sampler("song_list"), card, w, h), "song card $card")
            }
        }

        @Test
        @DisplayName("card bars read with a neighbouring band's offset miss")
        fun lessonCardKindsWrongBand() {
            // The song cards' lavender body is saturated enough to read as a song bar, so the technique list carries this check.
            for (dy in listOf(-dialogToMiddle, middleToBottom, towardTop - dialogToMiddle)) {
                val kinds = (0..2).map { grandConcertLessonCardKind(sampler("technique_list", dy), it, w, h) }
                assertTrue(LessonCardKind.UNKNOWN in kinds, "technique_list shifted by $dy read $kinds")
            }
        }

        @Test
        @DisplayName("the training panel's gain row and its +10")
        fun trainingGain() {
            assertEquals(listOf(3), selectedTrainingPerformanceRows(sampler("training_guts_selected"), w, h))
            assertEquals(10, GrandConcertGainDigits.readGainAmount(sampler("training_guts_selected"), 3, w, h))
            assertNotEquals(10, GrandConcertGainDigits.readGainAmount(sampler("training_guts_selected", dialogToMiddle), 3, w, h))
        }

        @Test
        @DisplayName("the Bonuses Updated popup is named from its pixels")
        fun bonusesUpdatedTitle() {
            assertEquals(DialogBonusesUpdated.title, DialogUtils.titleFromPixels(sampler("bonuses_updated").onScreen(ScreenBand.DIALOG, w, h)))
            for (name in fixtures - "bonuses_updated") assertEquals(null, DialogUtils.titleFromPixels(sampler(name).onScreen(ScreenBand.DIALOG, w, h)), name)
        }
    }

    @Nested
    @DisplayName("spark reads")
    inner class SparkReads {
        private fun rows(
            name: String,
            geometry: SparkListGeometry,
            dy: Int = 0,
        ) = parseSparkRowCells(sampler(name, dy).onScreen(geometry.band, w, h), geometry, h).map { it.kind to it.stars }

        private val rolled = listOf(SparkRowKind.STAT to 1, SparkRowKind.APTITUDE to 2, SparkRowKind.UNIQUE to 1)
        private val rerolled = rolled + (SparkRowKind.WHITE to 1)

        @Test
        fun listBands() {
            assertEquals(ScreenBand.MIDDLE, SPARKS_SCREEN_GEOMETRY.band)
            assertEquals(ScreenBand.MIDDLE, SPARK_PAGER_GEOMETRY.band)
            assertEquals(ScreenBand.DIALOG, SPARKS_CONFIRM_GEOMETRY.band)
        }

        @Test
        @DisplayName("rows and stars on every list screen")
        fun rowsAndStars() {
            assertEquals(rolled, rows("sparks_screen", SPARKS_SCREEN_GEOMETRY))
            assertEquals(rerolled, rows("sparks_rerolled_result", SPARKS_SCREEN_GEOMETRY))
            assertEquals(rolled, rows("pager_original", SPARK_PAGER_GEOMETRY))
            assertEquals(rerolled, rows("pager_rerolled", SPARK_PAGER_GEOMETRY))
            assertEquals(rerolled, rows("confirmation_rerolled", SPARKS_CONFIRM_GEOMETRY).take(4))
            // The keep dialog's grey body reads as starless rows past the set; the navigator ends the set on them.
            val keep = rows("keep_confirmation_plain", SPARKS_CONFIRM_GEOMETRY)
            assertEquals(rolled, keep.take(3))
            assertEquals(0, keep[3].second)
        }

        @Test
        @DisplayName("the scrolled-frame grid anchor keeps an unscrolled phone frame's rows")
        fun gridOffset() {
            for ((name, geometry) in listOf("sparks_screen" to SPARKS_SCREEN_GEOMETRY, "pager_rerolled" to SPARK_PAGER_GEOMETRY, "confirmation_rerolled" to SPARKS_CONFIRM_GEOMETRY)) {
                val list = sampler(name).onScreen(geometry.band, w, h)
                val offset = sparkRowGridOffset(list, geometry, h)
                assertTrue(offset != null && offset in -12..12, "$name offset $offset (the MuMu captures allow the same 12)")
                assertEquals(rows(name, geometry).take(4), parseSparkRowCellsAligned(list, geometry, h)?.map { it.kind to it.stars }?.take(4), name)
            }
        }

        @Test
        @DisplayName("a list read with the dialog band's offset loses the stars")
        fun wrongBandRows() {
            assertNotEquals(rolled, rows("sparks_screen", SPARKS_SCREEN_GEOMETRY, -dialogToMiddle))
            assertNotEquals(rerolled, rows("confirmation_rerolled", SPARKS_CONFIRM_GEOMETRY, dialogToMiddle).take(4))
        }

        @Test
        @DisplayName("the intro's fallback tap lands on its green Next")
        fun introFallbackTap() {
            val y = gameY(SPARK_INTRO_BUTTON_Y.toDouble(), ScreenBand.DIALOG, w, h).toInt()
            assertTrue(introNextUnder(sampler("spark_selection_intro"), SPARK_INTRO_BUTTON_X, y), "y $y")
        }

        @Test
        @DisplayName("the lit page dot names each pager page")
        fun pagerDots() {
            assertEquals(2, sparkPagerActiveDotIndex(sampler("pager_original"), w, h))
            assertEquals(1, sparkPagerActiveDotIndex(sampler("pager_rerolled"), w, h))
            assertEquals(null, sparkPagerActiveDotIndex(sampler("pager_original", -dialogToMiddle), w, h))
        }

        @Test
        @DisplayName("the pager swipe lanes stay on the rows, clear of the header and the Confirm")
        fun pagerLanes() {
            for (attempt in 1..2) {
                val plan = SparkPagerNav.plan(SparkSetSide.REROLLED, SparkSetSide.ORIGINAL, attempt, w, h)
                assertEquals(plan.startY, plan.endY)
                assertTrue(plan.startY > gameY(SparkPagerNav.HEADER_ZONE_MAX_Y.toDouble(), ScreenBand.MIDDLE, w, h), "attempt $attempt below the header")
                assertTrue(plan.startY < gameY(SparkPagerNav.CONFIRM_ZONE_MIN_Y.toDouble(), ScreenBand.BOTTOM, w, h), "attempt $attempt above the Confirm")
            }
            assertEquals(SparkPagerNav.LANE1_Y + 245f, SparkPagerNav.plan(SparkSetSide.REROLLED, SparkSetSide.ORIGINAL, 1, w, h).startY)
        }
    }

    @Nested
    @DisplayName("1080x1920 is unchanged")
    inner class Identity {
        @Test
        fun regionsAndSamplers() {
            val region = intArrayOf(110, 265, 650, 84)
            assertSame(region, region.onScreen(ScreenBand.MIDDLE, 1080, 1920))
            assertSame(region, region.onScreen(ScreenBand.DIALOG, 1440, 3088), "an unmapped width keeps its region")
            assertEquals(listOf(110, 265 + 245, 650, 84), region.onScreen(ScreenBand.MIDDLE, w, h).toList())
            assertEquals(listOf(110, 265 + 198, 650, 84), region.onScreen(ScreenBand.DIALOG, w, h).toList())
            val raw = sampler("sparks_screen")
            assertSame(raw, raw.onScreen(ScreenBand.BOTTOM, 1080, 1920))
        }

        @Test
        fun pagerLanes() {
            val plan = SparkPagerNav.plan(SparkSetSide.REROLLED, SparkSetSide.ORIGINAL, 1, 1080, 1920)
            assertEquals(SparkPagerNav.LANE1_Y.toFloat().toRawBits(), plan.startY.toRawBits())
        }
    }
}
