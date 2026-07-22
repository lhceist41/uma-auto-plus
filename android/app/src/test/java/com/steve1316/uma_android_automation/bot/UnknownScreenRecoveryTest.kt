package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The unknown-screen recovery ladder's game-relaunch decisions, plus source guards that pin the
 * 2026-07-21 incident fix in place: the in-app relaunch must not tear down a live game task
 * (CLEAR_TASK killed a live foreground game), and a stop after a failed relaunch must pause the
 * queue rather than march the next run onto a dead or foreign screen.
 */
@DisplayName("Unknown-screen recovery")
class UnknownScreenRecoveryTest {
    private val threshold = 22
    private val maxAttempts = 3

    @Nested
    @DisplayName("shouldRelaunchGame")
    inner class ShouldRelaunch {
        @Test
        fun `fires exactly at the threshold with budget left and a career observed`() {
            assertTrue(shouldRelaunchGame(threshold, threshold, attemptsUsed = 0, maxAttempts, careerObserved = true))
        }

        @Test
        fun `does not fire off the threshold count`() {
            assertFalse(shouldRelaunchGame(threshold - 1, threshold, 0, maxAttempts, true))
            assertFalse(shouldRelaunchGame(threshold + 1, threshold, 0, maxAttempts, true))
        }

        @Test
        fun `never fires before a career was observed (a parked pre-career lobby is not relaunched)`() {
            assertFalse(shouldRelaunchGame(threshold, threshold, 0, maxAttempts, careerObserved = false))
        }

        @Test
        fun `retries across the episode up to the budget, then stops relaunching`() {
            assertTrue(shouldRelaunchGame(threshold, threshold, attemptsUsed = 0, maxAttempts, true))
            assertTrue(shouldRelaunchGame(threshold, threshold, attemptsUsed = 1, maxAttempts, true))
            assertTrue(shouldRelaunchGame(threshold, threshold, attemptsUsed = 2, maxAttempts, true))
            // Budget spent: the 4th climb to the threshold no longer relaunches (falls through to stop).
            assertFalse(shouldRelaunchGame(threshold, threshold, attemptsUsed = 3, maxAttempts, true))
        }
    }

    @Nested
    @DisplayName("stopIsGameUnrecoverable")
    inner class StopUnrecoverable {
        @Test
        fun `a stop after at least one relaunch attempt pauses the queue`() {
            assertTrue(stopIsGameUnrecoverable(1))
            assertTrue(stopIsGameUnrecoverable(maxAttempts))
        }

        @Test
        fun `a stop with no relaunch attempted stays a generic error`() {
            assertFalse(stopIsGameUnrecoverable(0))
        }
    }

    @Nested
    @DisplayName("source guard")
    inner class SourceGuard {
        @Test
        fun `restartGame re-fronts the game and never tears down a live task with CLEAR_TASK`() {
            val game = sourceFile("bot/Game.kt").readText()
            val body = game.substring(game.indexOf("fun restartGame("), game.indexOf("fun start()"))
            assertFalse("FLAG_ACTIVITY_CLEAR_TASK" in body, "the relaunch must not CLEAR_TASK a live game (it killed the game on 2026-07-21)")
            assertTrue("FLAG_ACTIVITY_NEW_TASK" in body, "the relaunch still starts the game task from this service context")
        }

        @Test
        fun `the relaunch rung is bounded by the retry helper, not a one-shot boolean`() {
            val campaign = sourceFile("bot/Campaign.kt").readText()
            assertTrue("shouldRelaunchGame(" in campaign, "the ladder gates the relaunch through the bounded helper")
            assertTrue("gameRestartAttemptsThisEpisode++" in campaign, "each attempt is counted against the budget")
            assertFalse("gameRestartAttemptedThisEpisode" in campaign, "the old one-shot boolean is gone")
        }

        @Test
        fun `an unrecoverable stop flags the queue-pause before throwing`() {
            val campaign = sourceFile("bot/Campaign.kt").readText()
            val cap = campaign.indexOf("count >= maxUnknownScreenBeforeStop")
            val flag = campaign.indexOf("StartModule.gameRecoveryFailed = true", cap)
            val guard = campaign.indexOf("stopIsGameUnrecoverable(", cap)
            val throwAt = campaign.indexOf("throw InterruptedException(", cap)
            assertTrue(guard in cap until throwAt, "the pause flag is gated on stopIsGameUnrecoverable")
            assertTrue(flag in cap until throwAt, "the queue-pause flag is set before the stop throw")
        }

        @Test
        fun `the queue pauses on game-recovery failure regardless of stopOnError`() {
            val start = sourceFile("StartModule.kt").readText()
            // Inside the queue result evaluation's else-branch, the gameRecoveryFailed check must come
            // before the stopOnError branch so it wins regardless of the user's stopOnError setting.
            val elseBranch = start.indexOf("// Error, timeout, connection error, etc.")
            val recoveryCheck = start.indexOf("if (gameRecoveryFailed)", elseBranch)
            val stopOnErrorCheck = start.indexOf("if (stopOnError)", elseBranch)
            assertTrue(recoveryCheck in elseBranch until stopOnErrorCheck, "the recovery-failure pause is checked before stopOnError")
            assertTrue(start.indexOf("break", recoveryCheck) < stopOnErrorCheck, "a recovery failure breaks the queue loop")
        }

        @Test
        fun `the queue-pause flag is reset at the start of every session`() {
            val start = sourceFile("StartModule.kt").readText()
            val reset = start.indexOf("Reset queue control flags at the start of every new session.")
            assertTrue(reset > 0)
            assertTrue(start.indexOf("gameRecoveryFailed = false", reset) in reset until (reset + 400), "the flag is reset alongside the other queue flags")
        }
    }

    private fun sourceFile(relative: String): File = File(kotlinRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

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
