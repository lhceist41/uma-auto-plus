package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/** Source-level pins: where the memory lines and the Tesseract re-creation run, and that every engine access stays counted. */
@DisplayName("MemoryLog wiring")
class MemoryLogWiringTest {
    private val base = "android/app/src/main/java/com/steve1316/uma_android_automation"

    @Test
    @DisplayName("each queue run logs its start before Game.start() and its end in a finally")
    fun runStartAndEnd() {
        val startModule = source("$base/StartModule.kt")
        val run = startModule.substring(startModule.indexOf("private fun runSingleGame("))
        val started = run.indexOf("entryPoint.memoryLog.gameStart()")
        val start = run.indexOf("taskResult = entryPoint.start()")
        assertTrue(started in 0 until start)
        assertTrue(run.indexOf("finally {", start) in start until run.indexOf("entryPoint.memoryLog.gameEnd()"))
        assertEquals(1, Regex("memoryLog\\.gameStart\\(\\)").findAll(startModule).count())
    }

    @Test
    @DisplayName("the turn hook runs only after a handled main-screen tick, and the re-creation only when the schedule is due")
    fun turnHook() {
        val campaign = source("$base/bot/Campaign.kt")
        assertEquals(1, Regex("onTurnBoundary\\(\\)\n").findAll(campaign).count())
        val tick = campaign.substring(campaign.indexOf("if (handleMainScreen()) {"))
        assertTrue(tick.indexOf("onTurnBoundary()") < tick.indexOf("return null"))
        val hook = campaign.substring(campaign.indexOf("private fun onTurnBoundary()"))
        val logged = hook.indexOf("if (!game.memoryLog.turnEnd(")
        val due = hook.indexOf("if (!game.tesseractReinit.turnEnded()) return")
        val reinit = hook.indexOf("game.imageUtils.reinitTesseractWhenIdle()")
        assertTrue(logged in 0 until due && due < reinit)
        val throwable = hook.indexOf("catch (t: Throwable)")
        assertTrue(hook.indexOf("catch (e: InterruptedException)") in reinit until throwable, "a Stop must still end the run")
        assertTrue(hook.indexOf("game.tesseractReinit.stop()") > throwable, "a failed re-creation stops further tries")
    }

    @Test
    @DisplayName("nothing else re-creates or recycles the engines")
    fun singleReinitCaller() {
        val all = File(root(), base).walkTopDown().filter { it.extension == "kt" }.map { it.name to it.readText() }.toList()
        assertEquals(listOf("Campaign.kt"), all.filter { it.second.contains("reinitTesseractWhenIdle()") && !it.second.contains("fun reinitTesseractWhenIdle()") }.map { it.first })
        assertEquals(listOf("CustomImageUtils.kt"), all.filter { it.second.contains("initTesseract(") }.map { it.first })
        val imageUtils = source("$base/utils/CustomImageUtils.kt")
        assertFalse(imageUtils.contains("BaseAPI.recycle()"))
        val reinit = imageUtils.substring(imageUtils.indexOf("fun reinitTesseractWhenIdle()"))
        assertEquals(1, Regex("recycle = \\{ it\\.recycle\\(\\) \\}").findAll(imageUtils).count(), "engines are freed only through replaceEngines")
        assertTrue(reinit.indexOf("replaceEngines(") in 0 until reinit.indexOf("initTesseract("))
        assertTrue(reinit.contains("ready = { it.initLanguagesAsString == \"eng\" }"), "a failed native init must be caught before the old pair goes")
    }

    @Test
    @DisplayName("every Tesseract engine access is inside a counted OCR call or the re-creation")
    fun engineAccessIsCounted() {
        val imageUtils = source("$base/utils/CustomImageUtils.kt")
        assertFalse(imageUtils.contains("synchronized(ocrLock)"), "an uncounted lock would let a re-creation free an engine mid-read")
        val blocks = Regex("withOcrLock \\{").findAll(imageUtils).map { it.range.first to blockEnd(imageUtils, it.range.last) }.toList()
        assertEquals(6, blocks.size)
        val reinitStart = imageUtils.indexOf("fun reinitTesseractWhenIdle()")
        val reinit = reinitStart to blockEnd(imageUtils, imageUtils.indexOf("{", imageUtils.indexOf("reinitWhenOcrIdle(ocrLock", reinitStart)))
        val uses = Regex("tess(Digits)?BaseAPI\\.").findAll(imageUtils).map { it.range.first }.toList()
        assertTrue(uses.isNotEmpty())
        val outside = uses.filter { use -> (blocks + reinit).none { (s, e) -> use in s..e } }
        assertEquals(emptyList<Int>(), outside.map { imageUtils.substring(0, it).count { c -> c == '\n' } + 1 })
        val findText = imageUtils.indexOf("findText(\n")
        assertTrue(blocks.any { (s, e) -> findText in s..e }, "the library findText drives the shared engine too")
    }

    @Test
    @DisplayName("the memory line stays out of the watchdogs")
    fun notInWatchdogs() {
        assertFalse(source("$base/bot/StallWatchdog.kt").contains("memoryLog"))
        val game = source("$base/bot/Game.kt")
        // From the heartbeat through startWatchdog and watchRun up to start().
        val watch = game.substring(game.indexOf("fun heartbeat()"), game.indexOf("fun start(): TaskResult"))
        assertTrue(watch.contains("private fun startWatchdog()") && watch.contains("private fun watchRun()"))
        assertFalse(watch.contains("memoryLog"))
    }

    private fun blockEnd(text: String, open: Int): Int {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return i
            }
        }
        error("unbalanced block at $open")
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
