package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.RunRecord
import com.steve1316.uma_android_automation.STATUS_LABELS
import com.steve1316.uma_android_automation.postsProgressLine
import com.steve1316.uma_android_automation.progressLineDue
import com.steve1316.uma_android_automation.progressLineText
import com.steve1316.uma_android_automation.statusLabel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

@DisplayName("Notification progress line")
class ProgressNotificationTest {
    private val posts = mutableListOf<String>()
    private var capture = true

    @BeforeEach
    fun start() {
        StatusBoard.reset(0L)
        ProgressNotification.begin(captureRunning = { capture }) { posts.add(it) }
    }

    @AfterEach
    fun stop() {
        ProgressNotification.end()
        StatusBoard.reset()
    }

    @Test
    fun `every status key has its words, and an unknown key reads Working`() {
        val expected =
            mapOf(
                "armed" to "Run 2 of 4, Ready: tap the start button in the game",
                "running" to "Run 2 of 4, Classic Year Late January",
                "completed" to "Between runs: starting run 2 of 4",
                "navigating" to "Between runs: starting run 2 of 4",
                "waiting" to "Between runs: starting run 2 of 4",
                "starting" to "Between runs: starting run 2 of 4",
                "resuming" to "Between runs: starting run 2 of 4",
                "retrying" to "Between runs: starting run 2 of 4",
                "queueFailed" to "Run 2 of 4, The bot is not running",
                "queueHalted" to "Run 2 of 4, The bot is not running",
                "queueStopped" to "Run 2 of 4, The bot is not running",
                "queueComplete" to "Run 2 of 4, The bot is not running",
                "stoppedAfterCareer" to "Run 2 of 4, The bot is not running",
                "notRunning" to "Run 2 of 4, The bot is not running",
            )
        assertEquals(STATUS_LABELS.keys, expected.keys, "every key in the table is covered")
        for ((key, text) in expected) assertEquals(text, progressLineText(key, 2, 4, "Classic Year Late January"), key)
        for (key in STATUS_LABELS.filterValues { it == "Between runs" }.keys) {
            assertEquals("Between runs: run 2 of 4 ended", progressLineText(key, 2, 4, null, runRecorded = true), key)
        }
        assertEquals("Run 2 of 4, Classic Year Late January", progressLineText("running", 2, 4, "Classic Year Late January", runRecorded = true))
        assertEquals("Working", statusLabel("someFutureKey"))
        assertEquals("Run 2 of 4, Working", progressLineText("someFutureKey", 2, 4, "Classic Year Late January"))
        assertEquals("Working", progressLineText(null, null, null, null))
    }

    @Test
    fun `a single run has no position, and no date yet reads Running`() {
        assertEquals("Classic Year Late January", progressLineText("running", null, null, "Classic Year Late January"))
        assertEquals("Run 1 of 3, Running", progressLineText("running", 1, 3, null))
        assertEquals("Running", progressLineText("running", 0, 0, null))
        assertEquals("Between runs", progressLineText("waiting", null, null, null))
    }

    @Test
    fun `the line is posted for live, between-runs and unknown keys only`() {
        for (key in listOf("running", "completed", "navigating", "waiting", "starting", "resuming", "retrying", "someFutureKey")) assertTrue(postsProgressLine(key), key)
        for (key in listOf("armed", "queueFailed", "queueHalted", "queueStopped", "queueComplete", "notRunning")) assertFalse(postsProgressLine(key), key)
    }

    @Test
    fun `at most one post every 30 seconds, and never the text already showing`() {
        assertTrue(progressLineDue("a", null, 1_000L, null), "the first post goes")
        assertFalse(progressLineDue("a", "a", 100_000L, 1_000L), "the same text is not posted again")
        assertFalse(progressLineDue("b", "a", 30_999L, 1_000L), "a change inside the window waits")
        assertTrue(progressLineDue("b", "a", 31_000L, 1_000L), "a change at the window's end goes")
    }

    @Test
    fun `run changes and dates reach the notification, throttled`() {
        StatusBoard.queueProgress(1, 3, "starting", "{}", 0L)
        ProgressNotification.refresh(now = 1_000L)
        careerTurn(day = 25)
        ProgressNotification.refresh(now = 10_000L)
        ProgressNotification.refresh(now = 31_000L)
        careerTurn(day = 26)
        ProgressNotification.refresh(now = 40_000L)
        StatusBoard.runRecorded(RunRecord(1, 0L, 50_000L, "TASK_RESULT_COMPLETE", null, null, null, null))
        StatusBoard.queueProgress(1, 3, "completed", "{}", 0L)
        ProgressNotification.refresh(now = 61_000L)
        assertEquals(
            listOf("Between runs: starting run 1 of 3", "Run 1 of 3, Classic Year Early January", "Between runs: run 1 of 3 ended"),
            posts,
        )
    }

    @Test
    fun `a queue that starts on the home screen is starting its first run, not ending it`() {
        StatusBoard.queueProgress(1, 4, "navigating", "{}", 0L)
        ProgressNotification.refresh(now = 1_000L)
        assertEquals(listOf("Between runs: starting run 1 of 4"), posts)
    }

    @Test
    fun `mid-queue, a run says ended only once it is among the finished runs`() {
        StatusBoard.runRecorded(RunRecord(1, 0L, 50L, "TASK_RESULT_COMPLETE", null, null, null, null))
        StatusBoard.queueProgress(1, 4, "waiting", "{}", 0L)
        ProgressNotification.refresh(now = 1_000L)
        StatusBoard.queueProgress(2, 4, "retrying", "{}", 0L)
        ProgressNotification.refresh(now = 40_000L)
        assertEquals(listOf("Between runs: run 1 of 4 ended", "Between runs: starting run 2 of 4"), posts)
    }

    @Test
    fun `nothing is posted after the session end, while the capture service is down, or for an ending key`() {
        StatusBoard.queueProgress(1, 3, "starting", "{}", 0L)
        capture = false
        ProgressNotification.refresh(now = 1_000L)
        assertTrue(posts.isEmpty(), "no post while the capture service is not running")

        capture = true
        StatusBoard.queueProgress(3, 3, "queueComplete", "{}", 0L)
        ProgressNotification.refresh(now = 2_000L)
        assertTrue(posts.isEmpty(), "the end notification owns the ending")

        StatusBoard.queueProgress(1, 3, "starting", "{}", 0L)
        ProgressNotification.end()
        ProgressNotification.refresh(now = 3_000L)
        assertTrue(posts.isEmpty(), "no post after the session end")
    }

    @Test
    fun `the page's status table is this table`() {
        val logic = repoFile("android/app/src/main/assets/dashboard/logic.js").readText().replace("\r\n", "\n")

        fun keysOf(name: String) =
            Regex("var $name = \\[([^\\]]*)]").find(logic)!!.groupValues[1].split(',').map { it.trim().trim('\'') }.toSet()
        assertEquals(STATUS_LABELS.filterValues { it == "Between runs" }.keys, keysOf("BETWEEN_KEYS"))
        assertEquals(STATUS_LABELS.filterValues { it == "The bot is not running" }.keys, keysOf("TERMINAL_KEYS"))
        val statusPhase = logic.substringAfter("function statusPhase(statusKey) {").substringBefore("\n  }\n")
        for (label in STATUS_LABELS.values.toSet() + "Working") assertTrue(statusPhase.contains("label: '$label'"), label)
        assertTrue(statusPhase.contains("if (statusKey === 'armed') {\n      return { label: '${STATUS_LABELS["armed"]}'"))
        assertTrue(statusPhase.contains("if (statusKey === 'running') {\n      return { label: '${STATUS_LABELS["running"]}'"))
    }

    @Test
    fun `the session end stops the line first, and only the session's own paths refresh it`() {
        val start = kotlinSource("StartModule.kt")
        val endFirst = "private fun notifySessionEnd(libraryThread: Thread, report: QueueReport?) {\n        ProgressNotification.end()\n"
        assertTrue(start.contains(endFirst), "the end notifier stops the line before anything else")
        assertTrue(start.contains("ProgressNotification.begin(captureRunning = { MediaProjectionService.isRunning })"), "posts need the capture service")
        val refreshes =
            kotlinRoot()
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "ProgressNotification.kt" }
                .flatMap { f -> Regex("ProgressNotification\\.refresh\\(").findAll(f.readText()).map { f.name } }
                .toList()
                .sorted()
        assertEquals(listOf("Campaign.kt", "StartModule.kt"), refreshes)
        assertTrue(start.contains("StatusBoard.queueProgress(currentRun, totalRuns, status, payload.toString())\n        ProgressNotification.refresh()\n"))
        val campaign = kotlinSource("bot/Campaign.kt")
        assertTrue(campaign.contains("trainee.energy, trainee.mood.name)\n        ProgressNotification.refresh()\n    }"), "after the turn's reads, in publishTurnStatus")
        // The session itself runs in the StartEvent subscriber on the library's thread, which holds no
        // MessageLog lock; every other subscriber (the log and JS events run inside that lock) must not post.
        var subscribers = 0
        for (file in kotlinRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            val text = file.readText().replace("\r\n", "\n")
            for (subscriber in Regex("@Subscribe[^\\n]*\\n\\s*fun (\\w+)\\([^)]*\\)[^{]*\\{").findAll(text)) {
                if (subscriber.groupValues[1] == "onStartEvent") continue
                subscribers++
                val body = text.substring(subscriber.range.last).substringBefore("\n    }\n")
                for (post in listOf("ProgressNotification", "sendQueueProgressEvent", "publishTurnStatus")) {
                    assertFalse(body.contains(post), "${file.name} ${subscriber.groupValues[1]} must not reach $post")
                }
            }
        }
        assertTrue(subscribers >= 3, "the scan found the subscribers ($subscribers)")
        val notifier = kotlinSource("utils/ProgressNotification.kt").lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/") }.joinToString("\n")
        for (forbidden in listOf("MessageLog", "Log.", "EventBus", "Thread")) assertFalse(notifier.contains(forbidden), "the notifier must not use $forbidden")
    }

    private fun careerTurn(day: Int) {
        val (year, label) = StatusBoard.dateLabels("CLASSIC YEAR", if (day == 25) "EARLY" else "LATE", "JANUARY", day)
        StatusBoard.careerTurn("El Condor Pasa", "URA Finale", year, label, day, listOf(100, 100, 100, 100, 100), 80, "GOOD", 0L)
    }

    private fun kotlinSource(relative: String) = File(kotlinRoot(), relative).readText().replace("\r\n", "\n")

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val file = File(dir, relative)
            if (file.isFile) return file
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate $relative")
    }

    private fun kotlinRoot(): File = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt").parentFile
}
