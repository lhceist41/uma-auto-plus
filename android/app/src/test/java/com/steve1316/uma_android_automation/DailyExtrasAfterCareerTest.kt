package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The Rewards and Club step also runs after the bot's last career reached Home (the last or only run, and a stop after
 * the career), never between runs, and never a second time after Daily Races or Team Trials, which run it themselves.
 */
@DisplayName("Rewards and Club step after a career")
class DailyExtrasAfterCareerTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val main = "android/app/src/main/java/com/steve1316/uma_android_automation"

    private val start by lazy { repoFile("$main/StartModule.kt").readText().replace("\r\n", "\n") }

    private val navigator by lazy { repoFile("$main/CareerLaunchNavigator.kt").readText().replace("\r\n", "\n") }

    private fun body(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue(start >= 0, "$signature not found")
        val end = Regex("\n    (/\\*\\*|// |(private |internal )?fun )").find(src, start + signature.length)?.range?.first ?: src.length
        return src.substring(start, end)
    }

    /** The text from [from] up to the next line that closes the block at [indent] spaces. */
    private fun block(src: String, from: Int, indent: Int): String {
        val end = src.indexOf("\n" + " ".repeat(indent) + "}", from)
        assertTrue(end > from)
        return src.substring(from, end)
    }

    @Test
    fun `career scenarios run the step, Daily Races and Team Trials do not`() {
        for (scenario in listOf("URA Finale", "Unity Cup", "Trackblazer", "Grand Concert")) assertTrue(StartModule.runsDailyExtrasAfterFinalize(scenario), scenario)
        for (scenario in listOf("Daily Races", "Team Trials")) assertFalse(StartModule.runsDailyExtrasAfterFinalize(scenario), scenario)
    }

    @Test
    fun `it runs only where a career's end flow reached Home`() {
        assertEquals(2, Regex("runDailyExtrasAfterCareer\\(\\)\n").findAll(start).count(), "two calls")
        assertEquals(3, Regex("runDailyExtrasAfterCareer\\(").findAll(start).count(), "two calls plus the helper")
    }

    @Test
    fun `the last run runs it after the parked-on-Home line only`() {
        val finalize = start.indexOf("if (postCareerAction == PostCareerAction.FINALIZE_TO_HOME) {")
        val branch = block(start, finalize, 20)
        assertTrue(branch.contains("navigateWithDeadline(reuseLastLaunchSetup, finalizeToHome = true)"))
        val success = branch.indexOf("if (finalizeResult.success) {")
        val failure = branch.indexOf("} else if (enableRunQueue && finalizeResult.reasonKey == \"CAREER_NOT_FINISHED\")")
        val call = branch.indexOf("runDailyExtrasAfterCareer()")
        assertTrue(branch.indexOf("the game is parked on the home screen") in success until call)
        assertTrue(call < failure, "only on success")
        assertTrue(branch.lastIndexOf("runsDailyExtrasAfterFinalize(SettingsHelper.getStringSetting(\"general\", \"scenario\"))", call) in success until call)
    }

    @Test
    fun `a stop after the career runs it once its end flow succeeded, before the queue pauses`() {
        val stop = start.indexOf("if (careerFinished && stopAfterCareerRequested) {")
        val branch = block(start, stop, 24)
        val gate = branch.indexOf("if (finalizeScenario != \"Daily Races\" && finalizeScenario != \"Team Trials\") {")
        val failed = branch.indexOf("if (!navResult.success) {")
        val call = branch.indexOf("runDailyExtrasAfterCareer()")
        assertTrue(branch.contains("navigateWithDeadline(reuseLastLaunchSetup, finalizeToHome = true)"))
        assertTrue(gate in 0 until failed && failed < call, "inside the career-scenario gate, after the failure exit")
        assertTrue(branch.substring(failed, call).contains("break"), "a failed end flow leaves before the step")
        assertTrue(call < branch.indexOf("stoppedAfterCareerRun = i"))
    }

    @Test
    fun `the step never changes the run's result`() {
        val helper = body(start, "private fun runDailyExtrasAfterCareer(")
        assertTrue(helper.contains("CareerLaunchNavigator(context).runDailyExtras()"))
        assertTrue(helper.contains("catch (e: InterruptedException)"))
        assertTrue(helper.contains("catch (e: Exception)"))
        for (word in listOf("ledger", "queueHalt", "return ", "throw ")) assertFalse(helper.contains(word), word)
    }

    @Test
    fun `it runs the dailies' step on a Game built like every navigation's`() {
        assertTrue(navigator.contains("fun runDailyExtras(): List<String>? = if (ensureInitialised()) tempGame?.let { GameExtrasDevice.runFromSettings(it) } else null"))
    }
}
