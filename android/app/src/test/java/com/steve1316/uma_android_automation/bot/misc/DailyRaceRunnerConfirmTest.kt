package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.uma_android_automation.bot.misc.DailyRaceTask.Companion.runnerConfirmAllowed
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DailyRaceRunnerConfirmTest {
    @Test
    fun `Runner Selection is confirmed after this task's own verified Race with tickets held`() {
        assertTrue(runnerConfirmAllowed(multiRaceVerified = true, sessionOver = false, ticketsHeld = 6))
        assertTrue(runnerConfirmAllowed(multiRaceVerified = true, sessionOver = false, ticketsHeld = 1))
    }

    @Test
    fun `a Race the shared dialog handler tapped, or a run started on this screen, is never confirmed`() {
        assertFalse(runnerConfirmAllowed(multiRaceVerified = false, sessionOver = false, ticketsHeld = 6))
        assertFalse(runnerConfirmAllowed(multiRaceVerified = true, sessionOver = false, ticketsHeld = null))
        assertFalse(runnerConfirmAllowed(multiRaceVerified = true, sessionOver = false, ticketsHeld = 0))
    }

    @Test
    fun `after the multi-race or a finish the screen is left, not confirmed`() {
        assertFalse(runnerConfirmAllowed(multiRaceVerified = true, sessionOver = true, ticketsHeld = 6))
    }
}
