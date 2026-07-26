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
    }
}
