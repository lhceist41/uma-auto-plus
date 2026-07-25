package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.LessonCardKind
import com.steve1316.uma_android_automation.bot.PerformancePointType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Replay tests over the launch-night Grand Concert captures (see
 * src/test/resources/fixtures/grandconcert/PROVENANCE.md). These pin the only two Grand Concert
 * screens that have actually been photographed on Global, and - just as importantly - pin that
 * the probes REFUSE screens they have no evidence for.
 */
@DisplayName("Grand Concert pixel probes on the launch-night captures")
class GrandConcertProbeFixtureTest {
    private val cache = mutableMapOf<String, FixturePng>()

    private fun image(name: String): FixturePng =
        cache.getOrPut(name) {
            val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/grandconcert/$name.png")) { "missing fixture $name.png" }
            stream.use { FixturePng.read(it) }
        }

    private fun sampler(name: String): SparkPixelSampler {
        val img = image(name)
        return SparkPixelSampler { x, y -> img.getRGB(x, y) }
    }

    private val allFixtures =
        listOf(
            "quickmode_dont_use", "quickmode_shorten_all", "career_main_turn1", "final_confirmation",
            "training_guts_before", "career_after_training", "technique_list", "song_list", "song_list_scheduled",
            "learn_confirm_technique", "schedule_confirm_technique", "schedule_confirm_song", "scheduling_complete",
            "concert_info", "career_scheduled", "career_complete", "concert_pending",
            "concert_confirm", "concert_playback", "concert_success_banner", "concert_overview",
            "bonuses_updated", "concert_on_stage", "active_bonuses_panel",
            "grand_confirm_unchecked", "grand_confirm_checked",
        )

    @Test
    fun `every fixture is the supported 1080x1920 capture size`() {
        for (name in allFixtures) {
            assertEquals(1080, image(name).width, name)
            assertEquals(1920, image(name).height, name)
        }
    }

    @Test
    fun `the fixtures are straight RGB, not BGR-swapped bot captures`() {
        // The career screen's stat-table label row is magenta: red and blue high, green low. A
        // BGR-swapped file would still look magenta (red and blue swap into each other), so the
        // discriminator is the green Confirm button on the Quick Mode dialog, which must read
        // green-dominant and would read blue-dominant if the channels were swapped.
        val img = image("quickmode_dont_use")
        val p = img.getRGB(QuickModeGeometry.CONFIRM_X, QuickModeGeometry.CONFIRM_Y)
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        assertTrue(g > r && g > b, "expected a green-dominant Confirm button in RGB order, got ($r,$g,$b)")
    }

    @Nested
    @DisplayName("concert-pending screen")
    inner class ConcertPending {
        @Test
        fun `the concert-pending screen is recognised on its capture`() {
            assertTrue(grandConcertConcertPendingScreenPresent(sampler("concert_pending")))
        }

        @Test
        fun `no other capture reads as concert-pending, including the Complete Career screen`() {
            // Both screens share the same purple banner ASSET; position must separate them.
            for (name in allFixtures.filter { it != "concert_pending" }) {
                assertFalse(grandConcertConcertPendingScreenPresent(sampler(name)), name)
            }
        }

        @Test
        fun `the concert-pending screen does not read as Complete Career`() {
            assertFalse(grandConcertCareerCompleteScreenPresent(sampler("concert_pending")))
        }
    }

    @Nested
    @DisplayName("concert escort states")
    inner class ConcertEscortStates {
        @Test
        fun `each escort state is recognised on exactly its own capture`() {
            assertTrue(grandConcertConcertConfirmPresent(sampler("concert_confirm")))
            assertTrue(grandConcertConcertConfirmPresent(sampler("grand_confirm_unchecked")))
            assertTrue(grandConcertConcertConfirmPresent(sampler("grand_confirm_checked")))
            assertTrue(grandConcertPlaybackSkipPresent(sampler("concert_playback")))
            assertTrue(grandConcertResultNextPresent(sampler("concert_success_banner")))
            assertTrue(grandConcertResultNextPresent(sampler("concert_overview")))
            assertTrue(grandConcertBonusesUpdatedPresent(sampler("bonuses_updated")))
            assertTrue(grandConcertOnStagePresent(sampler("concert_on_stage")))
            assertTrue(grandConcertActiveBonusesPanelPresent(sampler("active_bonuses_panel")))
        }

        @Test
        fun `the Grand finale confirm's cutscene checkbox reads three states`() {
            // The Grand variant (turn 72) carries the skip checkbox; the numbered concerts' dialog
            // must read ABSENT so the escort taps Start directly on them.
            assertEquals(GrandCutsceneCheckbox.UNCHECKED, grandConcertCutsceneCheckboxState(sampler("grand_confirm_unchecked")))
            assertEquals(GrandCutsceneCheckbox.CHECKED, grandConcertCutsceneCheckboxState(sampler("grand_confirm_checked")))
            assertEquals(GrandCutsceneCheckbox.ABSENT, grandConcertCutsceneCheckboxState(sampler("concert_confirm")))
        }

        @Test
        fun `no escort probe fires on a capture it does not own`() {
            val confirmCaptures = listOf("concert_confirm", "grand_confirm_unchecked", "grand_confirm_checked")
            for (name in allFixtures) {
                if (name !in confirmCaptures) {
                    assertFalse(grandConcertConcertConfirmPresent(sampler(name)), "confirm on $name")
                }
                if (name != "concert_playback") {
                    assertFalse(grandConcertPlaybackSkipPresent(sampler(name)), "skip on $name")
                }
                if (name !in listOf("concert_success_banner", "concert_overview")) {
                    assertFalse(grandConcertResultNextPresent(sampler(name)), "next on $name")
                }
                if (name != "bonuses_updated") {
                    assertFalse(grandConcertBonusesUpdatedPresent(sampler(name)), "bonuses on $name")
                }
                if (name != "concert_on_stage") {
                    assertFalse(grandConcertOnStagePresent(sampler(name)), "on-stage on $name")
                }
                if (name != "active_bonuses_panel") {
                    assertFalse(grandConcertActiveBonusesPanelPresent(sampler(name)), "active-bonuses on $name")
                }
            }
        }
    }

    @Nested
    @DisplayName("Complete Career screen")
    inner class CareerComplete {
        @Test
        fun `the Complete Career screen is recognised on its capture`() {
            assertTrue(grandConcertCareerCompleteScreenPresent(sampler("career_complete")))
        }

        @Test
        fun `no other capture reads as the Complete Career screen`() {
            for (name in allFixtures.filter { it != "career_complete" }) {
                assertFalse(grandConcertCareerCompleteScreenPresent(sampler(name)), name)
            }
        }

        @Test
        fun `the bot-saved capture is straight RGB - the Complete Career button reads red-dominant`() {
            // This fixture is the bot's own temp/source.png save, not a MuMu screenshot, so it gets
            // its own channel-order canary in addition to the shared one above.
            val img = image("career_complete")
            val p = img.getRGB(540, 1630)
            val r = (p shr 16) and 0xFF
            val b = p and 0xFF
            assertTrue(r > b, "expected the pink Complete Career button red-over-blue in RGB order, got r=$r b=$b")
        }
    }

    @Nested
    @DisplayName("Quick Mode Settings dialog")
    inner class QuickMode {
        @Test
        fun `the dialog is recognised structurally on both captures`() {
            assertTrue(quickModeDialogPresent(sampler("quickmode_dont_use")))
            assertTrue(quickModeDialogPresent(sampler("quickmode_shorten_all")))
        }

        @Test
        fun `the selected option is read exactly, and the two captures differ`() {
            assertEquals(0, quickModeSelectedIndex(sampler("quickmode_dont_use")), "capture 1 has option 1 selected")
            assertEquals(1, quickModeSelectedIndex(sampler("quickmode_shorten_all")), "capture 2 has option 2 selected")
            assertNotEquals(
                quickModeSelectedIndex(sampler("quickmode_dont_use")),
                quickModeSelectedIndex(sampler("quickmode_shorten_all")),
                "a probe that cannot tell the two captures apart proves nothing",
            )
        }

        @Test
        fun `the selected index maps onto the configured option enum`() {
            assertEquals(QuickModeOption.DONT_USE.rowIndex, quickModeSelectedIndex(sampler("quickmode_dont_use")))
            assertEquals(QuickModeOption.SHORTEN_ALL.rowIndex, quickModeSelectedIndex(sampler("quickmode_shorten_all")))
        }

        @Test
        fun `no other captured screen is mistaken for the dialog`() {
            for (name in listOf("career_main_turn1", "final_confirmation")) {
                assertFalse(quickModeDialogPresent(sampler(name)), name)
                assertNull(quickModeSelectedIndex(sampler(name)), name)
            }
        }

        @Test
        fun `the four option rows are evenly spaced, so a layout shift breaks a test not a career`() {
            val ys = QuickModeGeometry.ROW_YS
            assertEquals(4, ys.size)
            val gaps = ys.zipWithNext { a, b -> b - a }
            assertTrue(gaps.all { it in 118..124 }, "row pitch drifted: $gaps")
        }

        @Test
        fun `every OCR region stays inside the frame`() {
            val regions = listOf(QuickModeGeometry.TITLE_OCR_REGION) + (0..3).map { QuickModeGeometry.optionOcrRegion(it) }
            for (r in regions) {
                assertTrue(r[0] >= 0 && r[1] >= 0, r.joinToString())
                assertTrue(r[0] + r[2] <= 1080, "width overflow: ${r.joinToString()}")
                assertTrue(r[1] + r[3] <= 1920, "height overflow: ${r.joinToString()}")
            }
        }
    }

    @Nested
    @DisplayName("career screen theme and the locked scenario button")
    inner class CareerScreen {
        @Test
        fun `the stat-table label row is the scenario's pink`() {
            assertTrue(grandConcertPinkStatLabelRow(sampler("career_main_turn1")))
        }

        @Test
        fun `the stat VALUE cells stay light, which is why the shared stat OCR is unaffected`() {
            // This is the whole Layer-3 claim in one assertion: the theme recolours the label
            // row, not the cells the digits sit in. If a future patch tints the cells, this test
            // fails and the grayscale OCR gets re-audited before a career does.
            assertTrue(grandConcertStatValueCellsAreLight(sampler("career_main_turn1")))
        }

        @Test
        fun `the scenario button reads as locked on turn 1`() {
            assertEquals(LessonSlotState.LOCKED, grandConcertLessonSlotState(sampler("career_main_turn1")))
        }

        @Test
        fun `the lesson-slot states are the four now that unlocked captures exist`() {
            // Launch-night captures now cover LOCKED (turn 1) and UNLOCKED_SCHEDULED (after a song
            // was scheduled); UNLOCKED (lit, nothing scheduled) is proven by the classifier.
            assertEquals(setOf("LOCKED", "UNLOCKED", "UNLOCKED_SCHEDULED", "UNKNOWN"), LessonSlotState.entries.map { it.name }.toSet())
        }

        @Test
        fun `the pink-label probe does not fire on the Quick Mode dialog`() {
            assertFalse(grandConcertPinkStatLabelRow(sampler("quickmode_dont_use")))
        }
    }

    @Nested
    @DisplayName("training screen and the per-turn performance type")
    inner class TrainingScreen {
        @Test
        fun `the training screen shows its blue Failure pill`() {
            assertTrue(grandConcertTrainingFailurePillPresent(sampler("training_guts_before")))
        }

        @Test
        fun `the Failure pill is not seen on the after-turn career screen`() {
            assertFalse(grandConcertTrainingFailurePillPresent(sampler("career_after_training")))
        }

        @Test
        fun `the selected training's performance type is read from the annotated row, not the facility`() {
            // Guts is selected, but the gold "+13" gain annotation sits on the Dance row. This is
            // the pixel proof of the per-turn override: nothing about the facility is consulted.
            val rows = selectedTrainingPerformanceRows(sampler("training_guts_before"))
            assertEquals(listOf(0), rows, "only the Da row should carry the gold gain annotation")
            assertEquals(listOf(PerformancePointType.DANCE), selectedTrainingPerformanceTypes(sampler("training_guts_before")))
        }

        @Test
        fun `the four unselected facility icons read their per-turn types, and at least one differs from the static prior`() {
            val icons = grandConcertFacilityIconTypes(sampler("training_guts_before"))
            assertEquals(PerformancePointType.VISUAL, icons[StatNameSlot.SPEED], "Speed icon is Visual this turn")
            assertEquals(PerformancePointType.PASSION, icons[StatNameSlot.STAMINA])
            assertEquals(PerformancePointType.VOCAL, icons[StatNameSlot.POWER])
            assertEquals(PerformancePointType.COMPOSURE, icons[StatNameSlot.WIT])
            // Speed's per-turn Visual differs from its documented primary (Dance): the icon, not a
            // static table, is the source of truth.
            assertNotEquals(PerformancePointType.DANCE, icons[StatNameSlot.SPEED])
        }

        @Test
        fun `the colour classifier maps each measured type icon and rejects the unsaturated`() {
            assertEquals(PerformancePointType.PASSION, classifyPerformanceIconColor(255, 93, 89))
            assertEquals(PerformancePointType.VOCAL, classifyPerformanceIconColor(255, 138, 193))
            assertEquals(PerformancePointType.VISUAL, classifyPerformanceIconColor(237, 171, 40))
            assertEquals(PerformancePointType.COMPOSURE, classifyPerformanceIconColor(170, 140, 243))
            assertEquals(PerformancePointType.DANCE, classifyPerformanceIconColor(50, 165, 240))
            assertNull(classifyPerformanceIconColor(228, 228, 230), "a near-grey sample must not be forced into a type")
        }

        @Test
        fun `the after-turn career screen shows the Mild Hype gauge`() {
            assertTrue(grandConcertHypeGaugePresent(sampler("career_after_training")))
            assertFalse(grandConcertHypeGaugePresent(sampler("training_guts_before")), "the training screen has no Hype gauge in this corner")
        }

        @Test
        fun `the training OCR regions stay inside the frame`() {
            val regions =
                listOf(
                    GrandConcertTrainingGeometry.FAILURE_OCR_REGION,
                    GrandConcertTrainingGeometry.SELECTED_FACILITY_BANNER_OCR_REGION,
                    GrandConcertHypeGauge.TITLE_OCR_REGION,
                ) + (0..4).flatMap { listOf(GrandConcertTrainingGeometry.perfBalanceOcrRegion(it), GrandConcertTrainingGeometry.perfMorePillOcrRegion(it)) }
            for (r in regions) {
                assertTrue(r[0] >= 0 && r[1] >= 0, r.joinToString())
                assertTrue(r[0] + r[2] <= 1080, "width overflow: ${r.joinToString()}")
                assertTrue(r[1] + r[3] <= 1920, "height overflow: ${r.joinToString()}")
            }
        }
    }

    @Nested
    @DisplayName("lesson list, confirmations, and concert info")
    inner class LessonScreens {
        @Test
        fun `the lesson list is recognised on both the technique and song variants`() {
            assertTrue(grandConcertLessonListPresent(sampler("technique_list")))
            assertTrue(grandConcertLessonListPresent(sampler("song_list")))
            assertTrue(grandConcertLessonListPresent(sampler("song_list_scheduled")))
        }

        @Test
        fun `the lesson list is not confused with a career or dialog screen`() {
            for (name in listOf("career_scheduled", "concert_info", "learn_confirm_technique", "schedule_confirm_song")) {
                assertFalse(grandConcertLessonListPresent(sampler(name)), name)
            }
        }

        @Test
        fun `technique cards read as techniques and song cards as songs`() {
            for (i in 0..2) {
                assertEquals(LessonCardKind.TECHNIQUE, grandConcertLessonCardKind(sampler("technique_list"), i), "technique card $i")
                assertEquals(LessonCardKind.SONG, grandConcertLessonCardKind(sampler("song_list"), i), "song card $i")
            }
        }

        @Test
        fun `affordability and the Learnable marker agree on the technique list`() {
            // Cards 0 and 2 are learnable (Learnable! marker, coloured cost strip); card 1
            // (Group Lesson Basics, Da 15 vs balance 10) is not.
            val s = sampler("technique_list")
            assertTrue(grandConcertCardAffordable(s, 0))
            assertFalse(grandConcertCardAffordable(s, 1))
            assertTrue(grandConcertCardAffordable(s, 2))
            assertTrue(grandConcertCardLearnableMarker(s, 0))
            assertFalse(grandConcertCardLearnableMarker(s, 1))
            assertTrue(grandConcertCardLearnableMarker(s, 2))
        }

        @Test
        fun `the dialog header separates confirmations and concert info from the lesson list`() {
            for (name in listOf("learn_confirm_technique", "schedule_confirm_technique", "schedule_confirm_song", "concert_info")) {
                assertTrue(grandConcertDialogHeaderPresent(sampler(name)), name)
            }
            assertFalse(grandConcertDialogHeaderPresent(sampler("technique_list")))
        }

        @Test
        fun `the red shortfall band separates the Schedule dialog from the Learn dialog`() {
            assertTrue(grandConcertScheduleShortfallPresent(sampler("schedule_confirm_technique")))
            assertTrue(grandConcertScheduleShortfallPresent(sampler("schedule_confirm_song")))
            assertFalse(grandConcertScheduleShortfallPresent(sampler("learn_confirm_technique")))
        }

        @Test
        fun `the scheduling-complete dialog is recognised by its mid-screen header`() {
            assertTrue(grandConcertSchedulingCompletePresent(sampler("scheduling_complete")))
            for (name in listOf("learn_confirm_technique", "concert_info", "technique_list")) {
                assertFalse(grandConcertSchedulingCompletePresent(sampler(name)), name)
            }
        }

        @Test
        fun `concert info is recognised and not confused with the other dialogs`() {
            assertTrue(grandConcertConcertInfoPresent(sampler("concert_info")))
            for (name in listOf("learn_confirm_technique", "schedule_confirm_song", "scheduling_complete", "technique_list")) {
                assertFalse(grandConcertConcertInfoPresent(sampler(name)), name)
            }
        }

        @Test
        fun `the lesson OCR regions stay inside the frame`() {
            val regions =
                listOf(
                    GrandConcertLessonGeometry.LIST_HEADER_OCR_REGION,
                    GrandConcertLessonGeometry.DIALOG_HEADER_OCR_REGION,
                    GrandConcertLessonGeometry.POINTS_LEFT_OVER_OCR_REGION,
                    GrandConcertLessonGeometry.CONCERT_INDEX_OCR_REGION,
                    GrandConcertLessonGeometry.CONCERT_HYPE_OCR_REGION,
                    GrandConcertLessonGeometry.CONCERT_SONGS_OCR_REGION,
                ) +
                    (0..4).map { GrandConcertLessonGeometry.balanceOcrRegion(it) } +
                    (0..2).flatMap {
                        listOf(
                            GrandConcertLessonGeometry.cardTitleOcrRegion(it),
                            GrandConcertLessonGeometry.cardMasteryOcrRegion(it),
                            GrandConcertLessonGeometry.cardConcertOcrRegion(it),
                        )
                    }
            for (r in regions) {
                assertTrue(r[0] >= 0 && r[1] >= 0, r.joinToString())
                assertTrue(r[0] + r[2] <= 1080, "width overflow: ${r.joinToString()}")
                assertTrue(r[1] + r[3] <= 1920, "height overflow: ${r.joinToString()}")
            }
        }
    }

    @Nested
    @DisplayName("unlocked Lessons detector")
    inner class LessonSlot {
        @Test
        fun `the turn-1 career screen reads LOCKED`() {
            assertEquals(LessonSlotState.LOCKED, grandConcertLessonSlotState(sampler("career_main_turn1")))
        }

        @Test
        fun `the post-schedule career screen reads UNLOCKED_SCHEDULED`() {
            assertEquals(LessonSlotState.UNLOCKED_SCHEDULED, grandConcertLessonSlotState(sampler("career_scheduled")))
            assertEquals(LessonSlotState.UNLOCKED_SCHEDULED, grandConcertLessonSlotState(sampler("career_after_training")))
        }

        @Test
        fun `the scheduled badge and note marker are detected independently and only when present`() {
            assertTrue(grandConcertScheduledBadgePresent(sampler("career_scheduled")))
            assertTrue(grandConcertLessonNoteMarkerPresent(sampler("career_scheduled")))
            assertFalse(grandConcertScheduledBadgePresent(sampler("career_main_turn1")))
            assertFalse(grandConcertLessonNoteMarkerPresent(sampler("career_main_turn1")))
        }

        @Test
        fun `the classifier covers UNLOCKED without coercing an unreadable slot to LOCKED`() {
            // No dedicated unlocked-unscheduled career capture exists (the player scheduled on
            // unlock), so UNLOCKED is proven by construction: a lit button with no Scheduled badge.
            // A dark non-grey sample must land on UNKNOWN, never LOCKED.
            val litNoBadge = SparkPixelSampler { _, _ -> (0xFF shl 24) or (186 shl 16) or (188 shl 8) or 205 }
            assertEquals(LessonSlotState.UNLOCKED, grandConcertLessonSlotState(litNoBadge))
            val darkColoured = SparkPixelSampler { _, _ -> (0xFF shl 24) or (30 shl 16) or (10 shl 8) or 120 }
            assertEquals(LessonSlotState.UNKNOWN, grandConcertLessonSlotState(darkColoured))
        }
    }

}
