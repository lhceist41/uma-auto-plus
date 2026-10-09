package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.uma_android_automation.bot.misc.TeamTrialsTask.Companion.splashTapAllowed
import com.steve1316.uma_android_automation.bot.misc.TeamTrialsTask.TeamTrialsScreenState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TeamTrialsSplashTapTest {
    @Test
    fun `an unknown screen right after the standby or the reveal is tapped during a match`() {
        assertTrue(splashTapAllowed(true, 2, TeamTrialsScreenState.STANDBY.name))
        assertTrue(splashTapAllowed(true, 3, TeamTrialsScreenState.RESULT_REVEAL.name))
    }

    @Test
    fun `an unknown screen after any other state is never tapped`() {
        val others = TeamTrialsScreenState.entries - setOf(TeamTrialsScreenState.STANDBY, TeamTrialsScreenState.RESULT_REVEAL)
        for (state in others) assertFalse(splashTapAllowed(true, 4, state.name), state.name)
        assertFalse(splashTapAllowed(true, 4, ""))
    }

    @Test
    fun `no tap outside a committed match or before two unknowns`() {
        assertFalse(splashTapAllowed(false, 4, TeamTrialsScreenState.STANDBY.name))
        assertFalse(splashTapAllowed(true, 1, TeamTrialsScreenState.RESULT_REVEAL.name))
    }
}
