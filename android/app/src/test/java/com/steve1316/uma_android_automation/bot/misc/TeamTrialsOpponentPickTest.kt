package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.uma_android_automation.bot.misc.TeamTrialsTask.OpponentPick
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TeamTrialsOpponentPickTest {
    @Test
    fun `fixed picks map to their rows`() {
        assertEquals(0, OpponentPick.TOP.rowIndex())
        assertEquals(1, OpponentPick.MIDDLE.rowIndex())
        assertEquals(2, OpponentPick.BOTTOM.rowIndex())
    }

    @Test
    fun `random stays on the three rows and uses all of them`() {
        val rng = Random(7)
        val rows = List(300) { OpponentPick.RANDOM.rowIndex(rng) }
        assertTrue(rows.all { it in 0..2 })
        assertEquals(setOf(0, 1, 2), rows.toSet())
    }

    @Test
    fun `the setting is read case-insensitively and falls back to bottom`() {
        assertEquals(OpponentPick.RANDOM, OpponentPick.fromSetting("random"))
        assertEquals(OpponentPick.TOP, OpponentPick.fromSetting(" Top "))
        assertEquals(OpponentPick.BOTTOM, OpponentPick.fromSetting("BOTTOM"))
        assertEquals(OpponentPick.BOTTOM, OpponentPick.fromSetting(""))
        assertEquals(OpponentPick.BOTTOM, OpponentPick.fromSetting("SIDEWAYS"))
    }
}
