package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.automation_library.utils.TextUtils
import com.steve1316.uma_android_automation.bot.misc.DailyRaceTask.Companion.RESULTS_WAIT_FRAMES
import com.steve1316.uma_android_automation.bot.misc.DailyRaceTask.Companion.difficultyListStep
import com.steve1316.uma_android_automation.bot.misc.DailyRaceTask.Companion.multiRaceFailure
import com.steve1316.uma_android_automation.bot.misc.DailyRaceTask.Companion.sharedHandlerAllowed
import com.steve1316.uma_android_automation.bot.misc.DailyRaceTask.DifficultyListStep
import com.steve1316.uma_android_automation.components.DialogObjects
import com.steve1316.uma_android_automation.components.DialogRaceDetails
import com.steve1316.uma_android_automation.components.DialogUtils
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
    fun `a committed multi-race with no results seen and no counter read back is not reported done`() {
        assertNotNull(multiRaceFailure(committed = true, resultsSeen = false, ticketsLeft = null))
        assertNotNull(multiRaceFailure(committed = true, resultsSeen = false, ticketsLeft = 3))
    }

    @Test
    fun `the counter read back at 0 after the commit proves the races ran without a results frame`() {
        assertNull(multiRaceFailure(committed = true, resultsSeen = false, ticketsLeft = 0))
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

    @Test
    fun `before the commit and after results the shared dialog handler looks as before`() {
        assertTrue(sharedHandlerAllowed(committed = false, resultsSeen = false, framesAwaitingResults = 0))
        assertTrue(sharedHandlerAllowed(committed = true, resultsSeen = true, framesAwaitingResults = 3))
    }

    @Test
    fun `MuMu 0's run, three frames outside the task's dialogs then the animating results, never reaches the shared handler in the window`() {
        // 19:12:28, :30, :33 read UNKNOWN; the shared handler then closed the results it read as Race Details.
        val allowed = (1..RESULTS_WAIT_FRAMES).map { sharedHandlerAllowed(committed = true, resultsSeen = false, framesAwaitingResults = it) }
        assertEquals(List(RESULTS_WAIT_FRAMES) { false }, allowed)
        assertTrue(RESULTS_WAIT_FRAMES >= 3, "both instances needed three frames before the results matched")
        assertTrue(sharedHandlerAllowed(committed = true, resultsSeen = false, framesAwaitingResults = RESULTS_WAIT_FRAMES + 1), "a real popup still gets handled after the window")
    }

    @Test
    fun `the Race Results title the shared handler read live is no longer Race Details`() {
        val titles = DialogObjects.items.map { it.title }
        assertEquals(DialogRaceDetails.title, TextUtils.matchStringInList("Race Results", titles), "the plain fuzzy match picks Race Details")
        assertNull(DialogUtils.matchDialogTitle("Race Results"))
        for (read in listOf("Race Details", "Race Detail", "Race Detais", "RaceDetails", "Race Details.", "Race Detalls")) {
            assertEquals(DialogRaceDetails.title, DialogUtils.matchDialogTitle(read), read)
        }
    }

    @Test
    fun `only frames outside the task's own dialogs wait, only after the commit, and no new Race tap exists`() {
        val task = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/misc/DailyRaceTask.kt").readText().replace("\r\n", "\n")
        val process = task.substring(task.indexOf("override fun process()"), task.indexOf("return when (currentState)"))
        assertTrue(process.contains("if (!ownDialog) {"))
        assertTrue(process.contains("if (raceSequenceCommitted && !resultsSeen && ++framesAwaitingResults =="))
        assertTrue(process.contains("if (sharedHandlerAllowed(raceSequenceCommitted, resultsSeen, framesAwaitingResults) && handleIncidentalPopups()) return null"))
        assertEquals(1, Regex("handleIncidentalPopups\\(\\)").findAll(task).count())
        assertEquals(1, Regex("raceSequenceCommitted = true").findAll(task).count(), "one commit, at the popup's Race! tap")
        assertEquals(1, Regex("\"multi_race_popup_race_confirm\"").findAll(task).count())
    }

    @Test
    fun `the wait window stays below the consecutive-UNKNOWN safety rail`() {
        assertTrue(RESULTS_WAIT_FRAMES < MiscTask.MAX_CONSECUTIVE_UNKNOWNS, "the rail would end the task before the shared handler may look")
    }

    @Test
    fun `all-UNKNOWN frames after the commit reach the shared handler before the safety rail fires`() {
        // Replays process() per frame in its own order: checkSafetyRails, capture, trackProgress, then the window gate.
        var consecutiveUnknowns = 0 // the commit happened on an own-dialog frame, which resets the count
        var framesAwaitingResults = 0
        var reachedAt: Int? = null
        for (frame in 1..MiscTask.MAX_CONSECUTIVE_UNKNOWNS + 2) {
            if (consecutiveUnknowns > MiscTask.MAX_CONSECUTIVE_UNKNOWNS) break
            consecutiveUnknowns++
            framesAwaitingResults++
            if (sharedHandlerAllowed(committed = true, resultsSeen = false, framesAwaitingResults = framesAwaitingResults)) {
                reachedAt = frame
                break
            }
        }
        assertEquals(RESULTS_WAIT_FRAMES + 1, reachedAt, "the shared handler must get a frame before the rail ends the task")
        assertTrue(RESULTS_WAIT_FRAMES >= 3, "both instances needed three frames before Race Results matched")
    }

    @Test
    fun `process keeps the order the replay assumes, and the rail counts as the replay does`() {
        val misc = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/misc/MiscTask.kt").readText().replace("\r\n", "\n")
        assertTrue(misc.contains("if (consecutiveUnknowns > MAX_CONSECUTIVE_UNKNOWNS) {"))
        val track = misc.substring(misc.indexOf("protected fun trackProgress("))
        assertTrue(track.indexOf("consecutiveUnknowns += 1") in 0 until track.indexOf("consecutiveUnknowns = 0"))
        val task = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/misc/DailyRaceTask.kt").readText().replace("\r\n", "\n")
        val process = task.substring(task.indexOf("override fun process()"), task.indexOf("return when (currentState)"))
        val rails = process.indexOf("checkSafetyRails()?.let { return it }")
        val tracked = process.indexOf("trackProgress(currentState.name, currentState == DailyRaceScreenState.UNKNOWN)")
        val counted = process.indexOf("++framesAwaitingResults")
        val gated = process.indexOf("sharedHandlerAllowed(raceSequenceCommitted, resultsSeen, framesAwaitingResults)")
        assertTrue(rails in 0 until tracked && tracked < counted && counted < gated, "rails, then tracking, then the window count, then the gate")
    }
}
