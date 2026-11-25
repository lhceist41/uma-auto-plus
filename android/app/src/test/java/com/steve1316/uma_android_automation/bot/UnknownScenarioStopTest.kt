package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.REPORT_REASON_KEYS
import com.steve1316.uma_android_automation.StartModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A scenario setting this version cannot play ends the run as the bot's own stop with its key and reason, so the queue
 * report says why instead of reading it as the player's Stop.
 */
@DisplayName("Unknown scenario stop")
class UnknownScenarioStopTest {
    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val f = File(dir, relative)
            if (f.isFile) return f.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    @Test
    fun `an unknown scenario sets the bot's own stop with its key and reason, then interrupts the run`() {
        val before = Triple(StartModule.queueStopKey, StartModule.queueStopReason, StartModule.queueStopRequested)
        try {
            StartModule.queueStopKey = null
            StartModule.queueStopReason = null
            StartModule.queueStopRequested = false
            val e = assertThrows(InterruptedException::class.java) { Game.stopForUnknownScenario("Grandmasters") }
            assertEquals("SCENARIO_UNSUPPORTED", StartModule.queueStopKey)
            assertTrue(StartModule.queueStopRequested)
            val reason = requireNotNull(StartModule.queueStopReason)
            assertEquals(reason, e.message)
            assertFalse(reason.contains("Grandmasters"), "the reason reaches Home's banner, so it carries no raw setting value")
            assertTrue(reason.contains("scenario"), reason)
        } finally {
            StartModule.queueStopKey = before.first
            StartModule.queueStopReason = before.second
            StartModule.queueStopRequested = before.third
        }
    }

    @Test
    fun `building a run with an unknown scenario goes through that stop, not a bare interrupt`() {
        val game = source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Game.kt")
        val task = game.substring(game.indexOf("val task: Task ="), game.indexOf("val isMiscTask"))
        assertTrue(task.contains("else -> stopForUnknownScenario(scenario)"), task)
        assertFalse(game.contains("InterruptedException(\"Invalid scenario"), "the old unkeyed interrupt is gone")
    }

    @Test
    fun `the queue report has its own words for it`() {
        val text = requireNotNull(REPORT_REASON_KEYS["SCENARIO_UNSUPPORTED"])
        assertTrue(text.reason.contains("scenario"), text.reason)
        assertFalse(text.reason.contains("Grandmasters"), "no raw value in the report words")
    }
}
