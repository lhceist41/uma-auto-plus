package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.uma_android_automation.bot.misc.DailyRaceTask.Companion.difficultyListStep
import com.steve1316.uma_android_automation.bot.misc.DailyRaceTask.Companion.multiRaceFailure
import com.steve1316.uma_android_automation.bot.misc.DailyRaceTask.DifficultyListStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DailyRaceSessionTest {
    @Test
    fun `a difficulty list is raced only after this task tapped the configured race`() {
        assertEquals(DifficultyListStep.TAP, difficultyListStep(racePicked = true, startedOver = false))
        assertEquals(DifficultyListStep.TAP, difficultyListStep(racePicked = true, startedOver = true))
    }

    @Test
    fun `another race's difficulty list starts over from Home once, then stops`() {
        assertEquals(DifficultyListStep.START_OVER, difficultyListStep(racePicked = false, startedOver = false))
        assertEquals(DifficultyListStep.STOP, difficultyListStep(racePicked = false, startedOver = true))
    }

    @Test
    fun `a committed multi-race with no results seen is not reported done`() {
        assertNotNull(multiRaceFailure(committed = true, resultsSeen = false, ticketsLeft = null))
        assertNotNull(multiRaceFailure(committed = true, resultsSeen = false, ticketsLeft = 0))
    }

    @Test
    fun `tickets still held after the results contradict a done multi-race`() {
        assertNotNull(multiRaceFailure(committed = true, resultsSeen = true, ticketsLeft = 2))
    }

    @Test
    fun `results seen and no tickets left, or no read back, is done`() {
        assertNull(multiRaceFailure(committed = true, resultsSeen = true, ticketsLeft = 0))
        assertNull(multiRaceFailure(committed = true, resultsSeen = true, ticketsLeft = null))
        assertNull(multiRaceFailure(committed = false, resultsSeen = false, ticketsLeft = null))
    }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(8) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${System.getProperty("user.dir")}")
    }

    @Test
    fun `Multi-Race counts as verified only once this task's Race tap went through`() {
        val lines = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/misc/DailyRaceTask.kt").readLines()
        val writes = lines.withIndex().filter { it.value.trim() == "multiRaceVerified = true" }
        assertEquals(1, writes.size, "one writer of multiRaceVerified")
        assertTrue(lines[writes.single().index - 1].trim().startsWith("if (ButtonRaceConfirm.click("), "the flag is set inside the successful Race! click")
    }

    @Test
    fun `Target Race text says a race out of rotation ends the run as an error`() {
        val settings = repoFile("src/pages/Settings/index.tsx").readText()
        val description = Regex("label=\"Target Race\"\\s+description=\"([^\"]*)\"").find(settings)?.groupValues?.get(1)
        assertNotNull(description)
        assertTrue(!description!!.contains("stops cleanly"), description)
        assertTrue(description.contains("reports the run as an error"), description)
    }
}
