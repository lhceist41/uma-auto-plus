package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.utils.QuickModeOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The Grand Concert scenario model: key normalization, caps, the manual-handoff contract, the
 * Quick Mode planner, and the read-only decision engine.
 */
@DisplayName("Grand Concert scenario model")
class GrandConcertScenarioTest {
    @Nested
    @DisplayName("scenario key and aliases")
    inner class Key {
        @Test
        fun `every accepted spelling normalizes to the one canonical key`() {
            val spellings =
                listOf(
                    "Grand Concert",
                    "Grand Live",
                    "Our Grand Concert",
                    "Brighter Together Our Grand Concert",
                    "Brighter Together! Our Grand Concert",
                    "Brighter Together: Our Grand Concert",
                    "  brighter together our grand concert  ",
                    "BRIGHTER TOGETHER OUR GRAND CONCERT",
                    "grand live",
                )
            for (s in spellings) {
                assertEquals(GrandConcertScenario.KEY, GrandConcertScenario.normalizeScenarioKey(s), s)
                assertTrue(GrandConcertScenario.matches(s), s)
            }
        }

        @Test
        fun `other scenarios pass through untouched`() {
            for (s in listOf("URA Finale", "Unity Cup", "Trackblazer", "Daily Races", "Team Trials")) {
                assertEquals(s, GrandConcertScenario.normalizeScenarioKey(s))
                assertFalse(GrandConcertScenario.matches(s))
            }
        }

        @Test
        fun `blank and null stay distinguishable from a real scenario`() {
            assertEquals("", GrandConcertScenario.normalizeScenarioKey(null))
            assertEquals("", GrandConcertScenario.normalizeScenarioKey("   "))
            assertFalse(GrandConcertScenario.matches(null))
            assertFalse(GrandConcertScenario.matches(""))
        }

        @Test
        fun `the canonical key is itself an accepted alias, so normalizing is idempotent`() {
            assertEquals(
                GrandConcertScenario.KEY,
                GrandConcertScenario.normalizeScenarioKey(GrandConcertScenario.normalizeScenarioKey("Grand Live")),
            )
        }

        @Test
        fun `an unrelated scenario that merely contains a word is not matched`() {
            assertFalse(GrandConcertScenario.matches("Grand Masters"))
            assertFalse(GrandConcertScenario.matches("Concert Hall"))
        }
    }

    @Nested
    @DisplayName("stat caps")
    inner class Caps {
        @Test
        fun `base caps match the Global denominators observed on 2026-07-23`() {
            assertEquals(1600, GrandConcertScenario.baseStatCap(StatName.SPEED))
            assertEquals(1300, GrandConcertScenario.baseStatCap(StatName.STAMINA))
            assertEquals(1300, GrandConcertScenario.baseStatCap(StatName.POWER))
            assertEquals(1500, GrandConcertScenario.baseStatCap(StatName.GUTS))
            assertEquals(1300, GrandConcertScenario.baseStatCap(StatName.WIT))
        }

        @Test
        fun `the shared scenario cap seam returns the same caps under every alias`() {
            for (alias in listOf("Grand Concert", "Grand Live", "Brighter Together Our Grand Concert")) {
                assertEquals(1600, Training.getScenarioStatCap(alias, StatName.SPEED), alias)
                assertEquals(1500, Training.getScenarioStatCap(alias, StatName.GUTS), alias)
                assertEquals(1300, Training.getScenarioStatCap(alias, StatName.WIT), alias)
            }
        }

        @Test
        fun `caps for the other scenarios are unchanged`() {
            assertEquals(1400, Training.getScenarioStatCap("URA Finale", StatName.SPEED))
            assertEquals(1800, Training.getScenarioStatCap("Unity Cup", StatName.WIT))
            assertEquals(1300, Training.getScenarioStatCap("Unity Cup", StatName.SPEED))
            assertEquals(1900, Training.getScenarioStatCap("Trackblazer", StatName.STAMINA))
            assertEquals(1500, Training.getScenarioStatCap("Trackblazer", StatName.WIT))
            assertEquals(1200, Training.getScenarioStatCap("Trackblazer", StatName.SPEED))
        }

        @Test
        fun `an inherited cap above the base is not clamped by the base cap`() {
            // A linked career was observed at 1641 Speed. The cap seam returns the BASE, and the
            // stat reader rejects only values above the cap it is given, so the base must not sit
            // below what a real career can legitimately show... which is exactly why the reader
            // takes maxOf(scenarioCap, manualCap) and why this test documents the interaction.
            val base = Training.getScenarioStatCap(GrandConcertScenario.KEY, StatName.SPEED)
            assertTrue(base >= 1600, "base Speed cap must not sit below the observed 1600 floor")
        }
    }

    @Nested
    @DisplayName("concert schedule from master data")
    inner class ConcertSchedule {
        @Test
        fun `the five concert turns are Global-confirmed from the master database`() {
            assertEquals(listOf(24, 36, 48, 60, 72), GrandConcertScenario.CONCERT_TURNS.value)
            assertEquals(5, GrandConcertScenario.CONCERT_TURNS.value.size)
            assertEquals(72, GrandConcertScenario.CONCERT_TURNS.value.last())
            assertEquals(Provenance.GLOBAL_CONFIRMED, GrandConcertScenario.CONCERT_TURNS.provenance)
        }

        @Test
        fun `the Grand Concert song threshold is twenty, Global-confirmed and distinct from the community 18`() {
            assertEquals(20, GrandConcertScenario.GRAND_CONCERT_SONG_THRESHOLD.value)
            assertEquals(Provenance.GLOBAL_CONFIRMED, GrandConcertScenario.GRAND_CONCERT_SONG_THRESHOLD.provenance)
            // The community "learned songs" reporting target is intentionally a different number (18),
            // because total setlist size and learned-song count may not coincide.
            assertEquals(18, GrandConcertPolicy.SPECIAL_SONG_TARGET.value)
        }
    }

    @Nested
    @DisplayName("manual handoff")
    inner class Handoff {
        @Test
        fun `a handoff never permits a game relaunch, because the game is alive`() {
            for (reason in GrandConcertHandoffReason.entries) {
                val h = GrandConcertHandoff(reason)
                assertTrue(h.gameIsAlive, reason.name)
                assertFalse(h.permitsGameRelaunch, reason.name)
                assertFalse(h.permitsGenericClick, reason.name)
                assertTrue(h.preservesCareer, reason.name)
            }
        }

        @Test
        fun `the player message names the screen, the preservation, and the resume path`() {
            val msg = GrandConcertHandoff(GrandConcertHandoffReason.LESSON_SHOP_NOT_AUTOMATED).playerMessage()
            assertTrue(msg.contains("career is preserved", ignoreCase = true), msg)
            assertTrue(msg.contains("press Start to resume", ignoreCase = true), msg)
        }

        @Test
        fun `repeated handoffs are independent and carry no accumulated state`() {
            val a = GrandConcertHandoff(GrandConcertHandoffReason.LESSON_SHOP_NOT_AUTOMATED, "turn 5")
            val b = GrandConcertHandoff(GrandConcertHandoffReason.CONCERT_NOT_AUTOMATED, "turn 24")
            assertTrue(a.preservesCareer && b.preservesCareer)
            assertFalse(a.permitsGameRelaunch || b.permitsGameRelaunch)
            assertTrue(b.playerMessage().contains("turn 24"))
        }
    }

    @Nested
    @DisplayName("Quick Mode planner")
    inner class QuickMode {
        @Test
        fun `with nothing configured it hands off instead of choosing for the player`() {
            for (unset in listOf(null, "", "   ", "nonsense")) {
                val action = QuickModePlanner.plan(unset, 0)
                assertTrue(action is QuickModeAction.HandOff, "input=$unset gave $action")
                assertEquals(
                    GrandConcertHandoffReason.QUICK_MODE_UNCONFIGURED,
                    (action as QuickModeAction.HandOff).handoff.reason,
                )
            }
        }

        @Test
        fun `an unreadable dialog hands off rather than guessing the current selection`() {
            val action = QuickModePlanner.plan(QuickModeOption.SHORTEN_ALL.wire, null)
            assertTrue(action is QuickModeAction.HandOff)
            assertEquals(
                GrandConcertHandoffReason.QUICK_MODE_UNREADABLE,
                (action as QuickModeAction.HandOff).handoff.reason,
            )
        }

        @Test
        fun `an already-correct selection confirms without tapping a row`() {
            assertEquals(QuickModeAction.ConfirmOnly, QuickModePlanner.plan(QuickModeOption.DONT_USE.wire, 0))
            assertEquals(QuickModeAction.ConfirmOnly, QuickModePlanner.plan(QuickModeOption.TRAINEE_ONLY.wire, 3))
        }

        @Test
        fun `a different selection targets exactly the configured row`() {
            assertEquals(QuickModeAction.Select(1), QuickModePlanner.plan(QuickModeOption.SHORTEN_ALL.wire, 0))
            assertEquals(QuickModeAction.Select(2), QuickModePlanner.plan(QuickModeOption.SCENARIO_ONLY.wire, 3))
        }

        @Test
        fun `the four options map to the four rows in dialog order`() {
            assertEquals(0, QuickModeOption.DONT_USE.rowIndex)
            assertEquals(1, QuickModeOption.SHORTEN_ALL.rowIndex)
            assertEquals(2, QuickModeOption.SCENARIO_ONLY.rowIndex)
            assertEquals(3, QuickModeOption.TRAINEE_ONLY.rowIndex)
            assertEquals(4, QuickModeOption.entries.size)
        }

        @Test
        fun `wire values round-trip and are stable`() {
            for (o in QuickModeOption.entries) {
                assertEquals(o, QuickModeOption.fromWire(o.wire))
            }
            assertNull(QuickModeOption.fromWire("shorten_everything"))
        }
    }

    @Nested
    @DisplayName("source guards")
    inner class SourceGuard {
        private fun source(relative: String): String {
            val f = File(kotlinRoot(), relative)
            require(f.isFile) { "missing ${f.path}" }
            return f.readText()
        }

        private fun kotlinRoot(): File {
            var dir: File? = File(System.getProperty("user.dir") ?: ".")
            repeat(5) {
                val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
                if (a.isDirectory) return a
                val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
                if (b.isDirectory) return b
                dir = dir?.parentFile
            }
            error("could not locate the Kotlin source root")
        }

        /** Resolves a path under the app's assets directory, so a test can assert a template the
         * code references is actually shipped rather than only declared. */
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

        @Test
        fun `the scenario dispatches to its own campaign class under the canonical key`() {
            val game = source("bot/Game.kt")
            assertTrue(game.contains("GrandConcertScenario.KEY -> GrandConcert(this)"), "dispatch entry missing")
            assertTrue(
                game.contains("GrandConcertScenario.normalizeScenarioKey(SettingsHelper.getStringSetting(\"general\", \"scenario\"))"),
                "the scenario string must be normalized before dispatch",
            )
        }

        @Test
        fun `an unknown scenario still aborts rather than defaulting to a campaign`() {
            val game = source("bot/Game.kt")
            assertTrue(game.contains("else -> throw InterruptedException(\"Invalid scenario:"), "unknown-scenario abort was removed")
        }

        /**
         * The queue and rotation gates were removed once the scenario stopped needing a supervisor:
         * careers play the Lesson shop, all five concerts, the career-end sequence and the spark
         * selection unattended, and the navigator pages the carousel to Grand Concert like any other
         * scenario. This asserts they stay removed, and that the carousel branch they depend on is
         * still present, so the queue can never be re-enabled without a way to launch the career.
         */
        @Test
        fun `the queue and rotation gates are gone and the carousel can reach this scenario`() {
            val start = source("StartModule.kt")
            assertFalse(start.contains("queueSupported"), "the queue capability gate is back")
            val rotationIdx = start.indexOf("fun loadRotationConfig()")
            assertTrue(rotationIdx >= 0, "loadRotationConfig went missing")
            val rotationBody = start.substring(rotationIdx, minOf(start.length, rotationIdx + 1200))
            assertFalse(rotationBody.contains("GrandConcertScenario.matches"), "the rotation gate is back")

            val nav = source("CareerLaunchNavigator.kt")
            assertTrue(
                nav.contains("GrandConcertScenario.matches(target) -> LabelScenarioSelectGrandConcert"),
                "the scenario carousel has no Grand Concert branch, so a queue could never launch one",
            )
        }

        /**
         * The Complete Career screen routes to the campaign so its Lessons drain can run, but
         * ACTIVE_TRAINING_MENU is a terminal success state, so that routing is only safe while a
         * campaign is actually coming back for the screen. Two passes have no campaign behind them:
         * the finalize-to-home pass and the queue's between-run pass. Missing the second cost a whole
         * queued run on 2026-07-26 (navigation reported success without launching, run 2 attached to
         * the finished career and wrote a phantom CAREER_END at turn=1 with run 1's exact stats).
         */
        @Test
        fun `the Complete Career routing is gated on a campaign actually driving it`() {
            val nav = source("CareerLaunchNavigator.kt")
            assertTrue(
                nav.contains("val campaignWillDriveThisScreen = !finalizeToHomeMode && !previousCareerCompleteMode"),
                "the Complete Career routing must require BOTH no-campaign flags to be clear",
            )
            assertTrue(
                nav.contains("if (campaignWillDriveThisScreen && grandConcertCareerCompleteScreenPresent("),
                "the routing no longer consults the combined guard",
            )
        }

        @Test
        fun `the queue tells the navigator when the previous career is already finished`() {
            val start = source("StartModule.kt")
            assertTrue(
                start.contains("navigateWithDeadline(nextReuse, previousCareerComplete = true)"),
                "the between-run navigation must declare that the previous career is complete",
            )
            // The cold-start pass must NOT claim it: a career launched there is driven by a campaign
            // afterwards, and the Lessons drain on a resumed end screen still belongs to it.
            val coldStart = start.substringAfter("navigateWithDeadline(coldStartReuse")
            assertFalse(
                coldStart.take(80).contains("previousCareerComplete"),
                "the cold-start pass must not declare the previous career complete",
            )
        }

        @Test
        fun `the scenario select label points at a template that actually ships`() {
            val label = source("components/Label.kt")
            assertTrue(label.contains("object LabelScenarioSelectGrandConcert"), "the carousel label is missing")
            val asset = assetFile("images/components/label/scenario_select_grand_concert.png")
            assertTrue(asset.isFile, "carousel template asset is missing at ${asset.path}")
            assertTrue(asset.length() > 0, "carousel template asset is empty")
        }

        @Test
        fun `the campaign class claims no finale-win capture it cannot read`() {
            val campaign = source("bot/campaigns/GrandConcert.kt")
            assertTrue(campaign.contains("override val capturesFinaleWins: Boolean = false"))
        }

        @Test
        fun `the campaign ships no Lesson or concert screen handler yet`() {
            val campaign = source("bot/campaigns/GrandConcert.kt")
            for (forbidden in listOf("ButtonConfirm", "ButtonNext", "ButtonOk", "gestureUtils.tap", "gestureUtils.swipe")) {
                assertFalse(campaign.contains(forbidden), "the campaign must not tap anything yet, found: $forbidden")
            }
        }

        /**
         * The 2026-07-26 career-end incident, part 1: the Complete Career screen fades in, the
         * scenario pixel probe recognises it before the complete_career button template clears
         * its confidence gate, and the campaign-specific fallback used to win that race and hand
         * the career off with every skill point unspent. The probe must feed checkEndScreen (so
         * both detectors reach the one good path), and the fallback handoff must stay deleted.
         */
        @Test
        fun `career-end detection runs through checkEndScreen and the fallback handoff stays deleted`() {
            val campaign = source("bot/campaigns/GrandConcert.kt")
            val endScreenIdx = campaign.indexOf("override fun checkEndScreen()")
            assertTrue(endScreenIdx >= 0, "the campaign no longer overrides checkEndScreen")
            val endScreenBody = campaign.substring(endScreenIdx, minOf(campaign.length, endScreenIdx + 700))
            assertTrue(
                endScreenBody.contains("grandConcertCareerCompleteScreenPresent"),
                "checkEndScreen must consult the scenario probe alongside the shared template",
            )
            assertFalse(
                source("bot/GrandConcertPolicy.kt").contains("CAREER_COMPLETE_NOT_AUTOMATED("),
                "the Complete Career handoff reason is back - the fade-in race would stall careers again",
            )
        }

        /**
         * Part 2: the drain itself must stay retryable. A drain that never saw the Lessons list
         * (fade-in frame, tap landed on nothing) must not consume the once-per-career flag,
         * because a skipped drain expires the leftover performance points at Finish.
         */
        @Test
        fun `a drain that never saw the list does not consume the once-only flag`() {
            val campaign = source("bot/campaigns/GrandConcert.kt")
            assertTrue(
                campaign.contains("careerEndDrainDone = spent >= 0"),
                "the drain flag must key on the list actually having been seen",
            )
            assertTrue(campaign.contains("DRAIN_LIST_NEVER_SEEN = -1"), "the never-seen sentinel is gone")
        }

        /**
         * Part 3: the plan-and-buy rounds. The scan can list an already-owned skill as buyable;
         * the DP then plans that phantom, its taps die, and a single-plan session used to end
         * "satisfied" while real candidates sat unbought - which the finalization guard then
         * correctly refused to Finish over, stalling the queue. The session must re-plan over
         * the live budget with dead-tapped names excluded from every candidate source.
         */
        @Test
        fun `the skill session re-plans after refused taps instead of concluding satisfied`() {
            val plan = source("bot/SkillPlan.kt")
            assertTrue(plan.contains("for (planRound in 1..maxPlanRounds)"), "the plan-round loop is gone")
            assertTrue(
                plan.contains("if (!bIsCareerComplete) break"),
                "extra plan rounds must stay career-end only - mid-career leftover SP is deliberate reserve, not a defect",
            )
            val deadTapFilters = Regex("""!in skillList\.deadTapSkills""").findAll(plan).count()
            assertTrue(
                deadTapFilters >= 4,
                "dead-tapped names must be excluded from the knapsack, the fallback, the buy callback, and the pass filter (found $deadTapFilters)",
            )
            assertTrue(
                plan.contains("deadTapExhausted = name in skillList.deadTapSkills"),
                "the finalization evidence no longer records dead-tapped rows",
            )
            assertTrue(
                source("types/SkillList.kt").contains("deadTapSkills.add(name)"),
                "buySkill no longer records refused taps",
            )
        }
    }
}
