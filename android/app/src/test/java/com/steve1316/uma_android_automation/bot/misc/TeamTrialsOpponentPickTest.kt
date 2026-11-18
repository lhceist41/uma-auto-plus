package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.uma_android_automation.bot.misc.TeamTrialsTask.OpponentPick
import com.steve1316.uma_android_automation.components.LabelWithEveryWin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
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

    @Test
    fun `the bonus opponent replaces only Bottom and Random, a chosen row stays`() {
        assertTrue(TeamTrialsTask.bonusMayOverride(OpponentPick.BOTTOM))
        assertTrue(TeamTrialsTask.bonusMayOverride(OpponentPick.RANDOM))
        assertFalse(TeamTrialsTask.bonusMayOverride(OpponentPick.TOP))
        assertFalse(TeamTrialsTask.bonusMayOverride(OpponentPick.MIDDLE))
    }

    @Test
    fun `the banner belongs to the lowest card above it`() {
        val centres = listOf(700, 1100, 1500)
        assertEquals(0, TeamTrialsTask.bonusRow(centres, 850.0))
        assertEquals(1, TeamTrialsTask.bonusRow(centres, 1250.0))
        assertEquals(2, TeamTrialsTask.bonusRow(centres, 1650.0))
        assertNull(TeamTrialsTask.bonusRow(centres, 600.0), "above every card")
    }

    @Test
    fun `the banner template ships with the app`() {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val path = "android/app/src/main/assets/images/${LabelWithEveryWin.template.path}.png"
        repeat(8) {
            listOf(File(dir, path), File(dir, path.removePrefix("android/"))).firstOrNull { it.isFile }?.let { return }
            dir = dir?.parentFile
        }
        throw AssertionError("$path not found")
    }
}
