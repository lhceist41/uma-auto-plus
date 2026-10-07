package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.FixturePng
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.grandConcertLessonConfirmationPresent
import com.steve1316.uma_android_automation.utils.grandConcertLessonListPresent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/** A run start that finds a career on a turn-committing confirmation or an in-career list cancels or backs out of it, never confirms. */
@DisplayName("Settling a career found on a sub-screen at run start")
class ResumeSettleTest {
    private fun step(screen: ResumeScreen) = resumeSettleStep(screen)?.action

    @Nested
    @DisplayName("the decision")
    inner class Decision {
        @Test
        fun `every turn-committing confirmation with a Cancel is cancelled`() {
            for (title in listOf("Rest", "Rest & Recreation", "Recreation", "Infirmary", "Race Details", "Confirm Use")) {
                assertEquals(ResumeSettleAction.CANCEL, step(ResumeScreen(dialogTitle = title, cancel = true)), title)
            }
        }

        @Test
        fun `a confirmation whose Cancel is not seen is left alone`() {
            assertNull(step(ResumeScreen(dialogTitle = "Rest")))
            assertNull(step(ResumeScreen(dialogTitle = "Confirmation", learn = true)))
            assertNull(step(ResumeScreen(grandConcertDialog = true)))
        }

        @Test
        fun `a Learn confirmation is cancelled, the other Confirmation dialogs are left to the campaign`() {
            assertEquals(ResumeSettleAction.CANCEL, step(ResumeScreen(dialogTitle = "Confirmation", learn = true, cancel = true)))
            // Borrow card, concert skip and the Unity Cup Begin Showdown share the title without Learn.
            assertNull(step(ResumeScreen(dialogTitle = "Confirmation", cancel = true)))
        }

        @Test
        fun `a Grand Concert lesson confirmation is cancelled`() {
            assertEquals(ResumeSettleAction.CANCEL, step(ResumeScreen(grandConcertDialog = true, cancel = true)))
        }

        @Test
        fun `the Race List and the lesson list are backed out of, only with their Back seen`() {
            assertEquals(ResumeSettleAction.BACK, step(ResumeScreen(raceList = true, back = true)))
            assertEquals(ResumeSettleAction.BACK, step(ResumeScreen(grandConcertLessonList = true, back = true)))
            assertNull(step(ResumeScreen(raceList = true)))
            assertNull(step(ResumeScreen(grandConcertLessonList = true)))
        }

        @Test
        fun `a Back or Cancel on any other screen is not pressed`() {
            // The Training selection screen (its own back-out owns it), the career-end skill list, and Home all carry Back;
            // other dialogs carry Cancel.
            assertNull(step(ResumeScreen(back = true, cancel = true, learn = true)))
            for (title in listOf("Unity Cup", "Notices", "Skills Learned", "Race Result", "Shop", "Training Items")) {
                assertNull(step(ResumeScreen(dialogTitle = title, cancel = true, back = true)), title)
            }
        }

        @Test
        fun `a commit dialog wins over a list behind it`() {
            assertEquals(ResumeSettleAction.CANCEL, step(ResumeScreen(dialogTitle = "Race Details", raceList = true, cancel = true, back = true)))
            assertEquals(ResumeSettleAction.CANCEL, step(ResumeScreen(grandConcertDialog = true, grandConcertLessonList = true, cancel = true, back = true)))
        }

        @Test
        fun `only Cancel and Back exist`() {
            assertEquals(listOf("Cancel", "Back"), ResumeSettleAction.entries.map { it.button })
        }
    }

    private fun settle(screens: MutableList<ResumeScreen?>, pressLands: Boolean = true): Pair<Int, List<ResumeSettleAction>> {
        val pressed = mutableListOf<ResumeSettleAction>()
        val presses =
            settleResumedCareer(
                readScreen = { screens.first() ?: ResumeScreen() },
                press = {
                    pressed += it.action
                    if (pressLands) screens.removeAt(0)
                    pressLands
                },
                settle = {},
                onTrainingMenu = { screens.first() == null },
            )
        return presses to pressed
    }

    private val rest = ResumeScreen(dialogTitle = "Rest", cancel = true)
    private val raceList = ResumeScreen(raceList = true, back = true)
    private val raceDetails = ResumeScreen(dialogTitle = "Race Details", raceList = true, cancel = true, back = true)

    @Nested
    @DisplayName("the loop")
    inner class Loop {
        @Test
        fun `one press that returns the training menu ends it`() {
            assertEquals(1 to listOf(ResumeSettleAction.CANCEL), settle(mutableListOf(rest, null)))
        }

        @Test
        fun `a dialog over a list is cancelled, then the list is backed out of`() {
            assertEquals(2 to listOf(ResumeSettleAction.CANCEL, ResumeSettleAction.BACK), settle(mutableListOf(raceDetails, raceList, null)))
        }

        @Test
        fun `nothing is pressed on a screen with nothing to settle`() {
            assertEquals(0 to emptyList<ResumeSettleAction>(), settle(mutableListOf(ResumeScreen(back = true), null)))
            assertEquals(0 to emptyList<ResumeSettleAction>(), settle(mutableListOf(null)))
        }

        @Test
        fun `it stops after three presses`() {
            assertEquals(MAX_RESUME_SETTLE_ACTIONS, settle(mutableListOf(rest, rest, rest, rest, rest, null)).first)
            assertEquals(3, MAX_RESUME_SETTLE_ACTIONS)
        }

        @Test
        fun `it stops once the training menu is back, even if the capture still reads something to settle`() {
            var presses = 0
            val total =
                settleResumedCareer(
                    readScreen = { rest },
                    press = {
                        presses++
                        true
                    },
                    settle = {},
                    onTrainingMenu = { presses >= 1 },
                )
            assertEquals(1, total)
            assertEquals(1, presses)
        }

        @Test
        fun `a press that is not found ends it without a retry`() {
            assertEquals(0 to listOf(ResumeSettleAction.CANCEL), settle(mutableListOf(rest, null), pressLands = false))
        }

        @Test
        fun `a press that lands on another screen with nothing to settle ends it`() {
            assertEquals(1 to listOf(ResumeSettleAction.BACK), settle(mutableListOf(raceList, ResumeScreen(dialogTitle = "Notices", cancel = true))))
        }
    }

    @Nested
    @DisplayName("the Grand Concert captures")
    inner class GrandConcertCaptures {
        private fun sampler(resource: String): SparkPixelSampler {
            val png = requireNotNull(javaClass.getResourceAsStream(resource)) { "missing fixture $resource" }.use { FixturePng.read(it) }
            return SparkPixelSampler { x, y -> png.getRGB(x, y) }
        }

        private fun facts(resource: String, cancel: Boolean, back: Boolean = false, title: String? = null, learn: Boolean = false): ResumeScreen {
            val s = sampler(resource)
            return ResumeScreen(
                dialogTitle = title,
                grandConcertDialog = grandConcertLessonConfirmationPresent(s),
                grandConcertLessonList = grandConcertLessonListPresent(s),
                cancel = cancel,
                back = back,
                learn = learn,
            )
        }

        private fun gc(name: String) = "/fixtures/grandconcert/$name.png"

        // Titles as the captures show them ("Schedule" matches the known title "Schedule Race").
        @Test
        fun `the Learn and Schedule confirmations are cancelled`() {
            assertEquals(ResumeSettleAction.CANCEL, step(facts(gc("learn_confirm_technique"), cancel = true, title = "Confirmation", learn = true)))
            for (name in listOf("schedule_confirm_technique", "schedule_confirm_song")) {
                assertEquals(ResumeSettleAction.CANCEL, step(facts(gc(name), cancel = true, title = "Schedule Race")), name)
            }
        }

        @Test
        fun `the lesson lists are backed out of`() {
            for (name in listOf("song_list", "technique_list", "song_list_scheduled")) {
                assertEquals(ResumeSettleAction.BACK, step(facts(gc(name), cancel = false, back = true)), name)
            }
        }

        @Test
        fun `the career, concert and career-end screens are left to the campaign`() {
            for (name in listOf("career_main_turn1", "career_after_training", "career_scheduled", "concert_pending", "career_complete", "bonuses_updated", "concert_info", "scheduling_complete", "concert_playback", "concert_overview")) {
                assertNull(step(facts(gc(name), cancel = false)), name)
            }
            assertNull(step(facts(gc("concert_on_stage"), cancel = false, back = true)), "the stage's Back is not a lesson list")
            assertNull(step(facts(gc("training_guts_before"), cancel = false, back = true)), "Training selection belongs to the Training selection back-out")
        }

        @Test
        fun `a full-height green dialog with Cancel that is not a lesson confirmation is left alone`() {
            assertNull(step(facts(gc("final_confirmation"), cancel = true, title = "Final Confirmation")), "the launch Final Confirmation")
            for (name in listOf("concert_confirm", "grand_confirm_checked", "grand_confirm_unchecked")) {
                assertNull(step(facts(gc(name), cancel = true)), name)
            }
            for (name in listOf("confirm_reroll_dialog", "confirmation_original", "keep_confirmation_guts2", "keep_confirmation_medium3", "keep_confirmation_plain")) {
                assertNull(step(facts("/fixtures/sparks/$name.png", cancel = true, title = "Confirmation")), "spark $name")
            }
        }

        @Test
        fun `the skill list's Learn confirmation is not a lesson confirmation`() {
            val skill = facts("/fixtures/skilllist/skill_list_confirmation_top.png", cancel = true, title = "Confirmation")
            assertTrue(!skill.grandConcertDialog)
            assertNull(step(skill), "the lesson rule does not fire")
            assertEquals(ResumeSettleAction.CANCEL, step(skill.copy(learn = true)))
        }

        @Test
        fun `the Options dialog is never cancelled as a lesson confirmation, though a section header bar fools the pill`() {
            for (name in listOf("options_skip_settings_top", "options_career_scenario_top", "options_career_scenario_on_top")) {
                val options = facts("/fixtures/resumesettle/$name.png", cancel = true, title = "Options")
                assertTrue(options.grandConcertDialog, "$name: the green section header bar reads as a lesson pill")
                assertNull(step(options), name)
                assertEquals(ResumeSettleAction.CANCEL, step(options.copy(dialogTitle = null)), "$name: only the title keeps it out")
            }
        }
    }

    @Nested
    @DisplayName("wiring")
    inner class Wiring {
        private val game by lazy { source("bot/Game.kt") }
        private val settleSource by lazy { source("bot/ResumeSettle.kt") }

        @Test
        fun `Game start settles before the Training selection back-out and the navigator, never for a misc task`() {
            val start = game.substring(game.indexOf("fun start(): TaskResult"))
            val settle = start.indexOf("if (!isMiscTask) settleResumedCareer(::readResumeScreen, ::pressResumeSettle, { wait(1.0) }, ::isOnTrainingMenu)")
            assertTrue(settle > start.indexOf("runDiagnostic()?.let { return it }"))
            assertTrue(settle < start.indexOf("val onTrainingSelection = !isMiscTask && isOnTrainingSelection()"))
            assertTrue(settle < start.indexOf("navigator.navigate("))
            assertEquals(2, Regex(Regex.escape("settleResumedCareer(")).findAll(game + settleSource).count(), "its definition and one call")
        }

        @Test
        fun `the settle presses only Cancel or Back`() {
            val press = game.substringAfter("private fun pressResumeSettle(step: ResumeSettleStep): Boolean {").substringBefore("\n    }\n")
            assertEquals(2, Regex("""\.click\(""").findAll(press).count())
            assertTrue(press.contains("ResumeSettleAction.CANCEL -> ButtonCancel.click(imageUtils)"))
            assertTrue(press.contains("ResumeSettleAction.BACK -> ButtonBack.click(imageUtils)"))
            assertTrue(!settleSource.contains(".click(") && !settleSource.contains("tap("), "the decision file never touches the screen")
        }

        @Test
        fun `the lesson probes read only Grand Concert full-size frames`() {
            val read = game.substringAfter("private fun readResumeScreen(): ResumeScreen {").substringBefore("\n    }\n")
            assertTrue(read.contains("val grandConcertFrame = GrandConcert.isGrandConcert(scenario) && isMappedSurface(bitmap.width, bitmap.height)"))
            assertTrue(read.contains("grandConcertDialog = grandConcertFrame && grandConcertLessonConfirmationPresent(sampler, bitmap.width, bitmap.height)"))
            assertTrue(read.contains("grandConcertLessonList = grandConcertFrame && grandConcertLessonListPresent(sampler, bitmap.width, bitmap.height)"))
        }

        private fun source(relative: String): String = File(kotlinRoot(), relative).readText().replace("\r\n", "\n")

        private fun kotlinRoot(): File {
            var dir: File? = File(System.getProperty("user.dir") ?: ".")
            repeat(5) {
                val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
                if (a.isDirectory) return a
                val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
                if (b.isDirectory) return b
                dir = dir?.parentFile
            }
            throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
        }
    }
}
