package com.steve1316.uma_android_automation

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A Support Formation whose own card is of the trainee's character stops the launch before the first
 * Start Career tap, with its own reason; a tag on the Friends card stays with the borrow replacement.
 * The navigator needs a live game, so its wiring is pinned by source guards; the probe itself is
 * pinned on captures in utils/TraineeInDeckProbeFixtureTest.
 */
@DisplayName("Stopping on a deck card of the trainee's own character")
class TraineeInDeckHaltTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val navigator by lazy {
        repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt").readText().replace("\r\n", "\n")
    }

    private val handler by lazy {
        val start = navigator.indexOf("private fun handleSupportDeckScreen(")
        navigator.substring(start, navigator.indexOf("\n    private fun ", start + 1))
    }

    private val refusal by lazy {
        val start = navigator.indexOf("private fun traineeInDeckFailure(")
        assertTrue(start >= 0)
        navigator.substring(start, navigator.indexOf("\n    }\n", start))
    }

    @Test
    fun `the check runs on every pass before any Start Career tap, borrow or build-aware launch`() {
        val check = handler.indexOf("traineeInDeckFailure(bitmap, requiredDeck)?.let { return it }")
        assertTrue(check >= 0, "the handler asks on every pass")
        assertTrue(check < handler.indexOf("handleBuildAwareLaunch(requiredDeck)"), "before the build-aware launch")
        assertTrue(check < handler.indexOf("runBorrowStep(bitmap)"), "before the borrow")
        assertTrue(check < handler.indexOf("ButtonStartCareer.click("), "before the first Start Career tap")
        assertTrue(check < handler.indexOf("startCareerClickAttempts++"), "before any tap is counted")
        assertEquals(1, Regex("traineeInDeckFailure\\(").findAll(navigator).count() - 1, "called from one place")
    }

    @Test
    fun `only an owned slot stops the launch, with its own key and the deck named`() {
        assertTrue(refusal.contains(".firstOrNull { it != TraineeInDeckProbe.FRIEND_SLOT } ?: return null"), "the Friends slot stays with the borrow replacement")
        assertTrue(refusal.contains("reasonKey = \"TRAINEE_IN_DECK\""))
        assertTrue(refusal.contains("(requiredDeck ?: readDeckNumber(bitmap))"), "the required deck, or the one on screen")
        assertTrue(refusal.contains("Start Career was not pressed."))
        assertTrue(refusal.contains("Auto-Fill Support Deck") && refusal.contains("Required Support Deck"), "names the real settings")
    }

    @Test
    fun `the queue report says what happened and what to do`() {
        val text =
            queueReportText(
                JSONObject().put("kind", "LAUNCH_FAILED_BEFORE_RUN").put("queueEnabled", true).put("totalRuns", 3).put("runReached", 0).put("resumable", false).put("reasonKey", "TRAINEE_IN_DECK"),
            )
        assertTrue(text.reason.contains("a card of the trainee's own character"), text.reason)
        assertTrue(text.reason.contains("Nothing was spent."), text.reason)
        assertTrue(!text.reason.contains("empty"), text.reason)
        assertTrue(text.nextAction!!.startsWith("Swap that card or choose another deck in the game, or turn on Auto-Fill Support Deck with Required Support Deck off, then press Start"), text.nextAction)
    }
}
