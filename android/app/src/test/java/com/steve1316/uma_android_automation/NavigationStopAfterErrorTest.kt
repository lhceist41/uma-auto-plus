package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * An error that ends the service mid-navigation (the library treats an exception on any thread as a crash) halts the queue.
 * The halt reason names the error instead of saying the queue "was stopped"; a player's stop keeps its own words.
 */
@DisplayName("Navigation stopped by an error")
class NavigationStopAfterErrorTest {
    private val stopped =
        NavigationResult(success = false, lastDetectedState = "POST_RUN_RESULTS", failureReason = CareerLaunchNavigator.STOPPED_DURING_NAVIGATION, recommendedAction = "Restart the queue")

    @Test
    fun `an error that ended the service says the bot stopped after an error`() {
        val words = StartModule.stopAfterErrorWords(stopped, botRunning = false, runPostedException = true)
        assertTrue(words.failureReason.contains("stopped after an error"))
        assertFalse(words.failureReason.contains("Queue was stopped"))
        assertEquals(stopped.copy(failureReason = words.failureReason, recommendedAction = words.recommendedAction), words)
    }

    @Test
    fun `no posted error, a running service or another failure keeps the words`() {
        assertEquals(stopped, StartModule.stopAfterErrorWords(stopped, botRunning = false, runPostedException = false))
        assertEquals(stopped, StartModule.stopAfterErrorWords(stopped, botRunning = true, runPostedException = true))
        val other = stopped.copy(failureReason = "Navigation timed out.")
        assertEquals(other, StartModule.stopAfterErrorWords(other, botRunning = false, runPostedException = true))
    }

    private val base = "android/app/src/main/java/com/steve1316/uma_android_automation"

    @Test
    fun `a returned navigation passes the player-stop check and then the error words`() {
        val start = source("$base/StartModule.kt")
        val wrapper = start.substring(start.indexOf("private fun navigateWithDeadline("), start.indexOf("private fun stoppedNavigation()"))
        val navigated = wrapper.indexOf("noteStopByPlayer(navigator.navigate(")
        val words = wrapper.indexOf(".let { stopAfterErrorWords(it, BotService.isRunning, lastRunPostedException) }")
        assertTrue(navigated in 0 until words)
        assertTrue(words < wrapper.indexOf("} catch (e: InterruptedException) {"), "the returned navigation, not the interrupted one")
    }

    @Test
    fun `the navigator uses one stop text and builds no Game for a stopped bot`() {
        val nav = source("$base/CareerLaunchNavigator.kt")
        assertFalse(nav.contains("failureReason = \"Queue was stopped during navigation.\""))
        assertEquals(4, Regex("failureReason = STOPPED_DURING_NAVIGATION,").findAll(nav).count())
        val navigate = nav.substring(nav.indexOf("    fun navigate(\n"))
        val early = navigate.indexOf("if (!BotService.isRunning || StartModule.queueStopRequested) {")
        assertTrue(early in 0 until navigate.indexOf("if (!ensureInitialised()) {"))
    }

    private fun root(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, base).isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found")
    }

    private fun source(relative: String): String = File(root(), relative).readText().replace("\r\n", "\n")
}
