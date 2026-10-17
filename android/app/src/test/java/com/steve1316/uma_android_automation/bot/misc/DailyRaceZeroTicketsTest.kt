package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.uma_android_automation.utils.parseTicketCount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** A Daily Races start with no tickets ends as a plain success that says so; an unreadable counter still never races. */
class DailyRaceZeroTicketsTest {
    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            for (root in listOf("src/main/java/com/steve1316/uma_android_automation", "android/app/src/main/java/com/steve1316/uma_android_automation")) {
                val file = File(dir, "$root/$relative")
                if (file.isFile) return file.readText().replace("\r\n", "\n")
            }
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val task by lazy { source("bot/misc/DailyRaceTask.kt") }
    private val counterBranch by lazy {
        val from = task.indexOf("        if (ticketsHeld == null) {\n            val held = readTickets(bitmap)")
        assertTrue(from >= 0)
        task.substring(from, task.indexOf("        if (onRacePick) pickRace() else pickDifficulty(bitmap)", from))
    }

    @Test
    fun `the counter read upside down at 0 tickets ends the session as a success that says no tickets are left`() {
        assertEquals(0, parseTicketCount("9/0")?.first, "the logged read of the 0/6 counter")
        val zero = counterBranch.indexOf("if (held == 0) {")
        assertTrue(zero >= 0)
        val zeroBranch = counterBranch.substring(zero, counterBranch.indexOf("}", zero))
        assertTrue(zeroBranch.contains("finish(\"No Daily Race tickets left.\", failed = false)"))
        assertTrue(zeroBranch.contains("return"), "no race is picked at 0 tickets")
        assertTrue(task.contains("finishFailed -> TaskResult.Error("), "only a failed finish is an error")
        assertTrue(task.contains("else -> TaskResult.Success(TaskResultCode.TASK_RESULT_COMPLETE, \"DailyRaceTask completed. Races run this session: \$races. \$reason\")"))
    }

    @Test
    fun `an unreadable counter never races and stops as a failure after its reads`() {
        val unread = counterBranch.indexOf("if (held == null) {")
        assertTrue(unread >= 0)
        val unreadBranch = counterBranch.substring(unread, counterBranch.indexOf("if (held == 0) {"))
        assertTrue(unreadBranch.contains("finish(\"Ticket counter unreadable after \$ticketReads reads; not racing.\", failed = true)"))
        assertTrue(unreadBranch.trimEnd().endsWith("return\n            }"), "it returns before ticketsHeld is set or a race is picked")
        assertTrue(counterBranch.indexOf("ticketsHeld = held") > counterBranch.indexOf("if (held == 0) {"))
    }
}
