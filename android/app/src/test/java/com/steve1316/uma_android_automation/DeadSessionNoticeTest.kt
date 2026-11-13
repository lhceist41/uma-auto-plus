package com.steve1316.uma_android_automation

import android.app.ApplicationExitInfo
import com.steve1316.uma_android_automation.bot.WatchdogBreadcrumb
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The notice a restarted process posts for a session the previous process lost. The decision runs
 * against scripted ledger, permission and notification calls; the Android wiring (thread, channel,
 * notification, the ledger's store) is pinned by source guards.
 */
@DisplayName("The notice after the bot process died mid-session")
class DeadSessionNoticeTest {
    private fun open(queue: Boolean = false) =
        JSONObject()
            .put("sessionId", "dead")
            .put("startedAt", 1_000L)
            .put("updatedAt", 2_000L)
            .put("phase", StartModule.PHASE_CAREER)
            .put("currentRun", 2)
            .put("totalRuns", 3)
            .put("queueEnabled", queue)

    private fun exit(reason: Int) = ExitRecord(pid = 7, timestamp = 3_000L, reason = reason, status = 0)

    private fun lowMemory() = processEndedReport(open(), exit(ApplicationExitInfo.REASON_LOW_MEMORY), null, resumable = false)

    /** Plays [DeadSessionNotice.notifyDeadSession] and records every call in order. */
    private class Run(
        val report: () -> QueueReport?,
        val sdk: Int = 31,
        val granted: Boolean = true,
        val running: Boolean = false,
        val channelFails: Boolean = false,
        val postFails: Boolean = false,
    ) {
        val calls = mutableListOf<String>()
        val posted = mutableListOf<ReportText>()
        val result =
            DeadSessionNotice.notifyDeadSession(
                reportDeadSession = {
                    calls += "report"
                    report()
                },
                sdkInt = sdk,
                canPost = {
                    calls += "permission"
                    granted
                },
                sessionRunning = {
                    calls += "running"
                    running
                },
                ensureChannel = {
                    calls += "channel"
                    if (channelFails) throw IllegalStateException("no channel")
                },
                post = {
                    calls += "post"
                    if (postFails) throw SecurityException("denied")
                    posted += it
                },
            )
    }

    @Nested
    @DisplayName("when it posts")
    inner class Decision {
        @Test
        fun `a report this call stored posts exactly one notice with its words`() {
            val run = Run({ lowMemory() })
            assertTrue(run.result)
            assertEquals(1, run.posted.size)
            assertEquals("App stopped unexpectedly", run.posted[0].title)
            assertEquals("UMA Auto+ stopped unexpectedly during the run. Android closed it to free memory. Press Start in UMA Auto+.", run.posted[0].body)
        }

        @Test
        fun `the channel is ensured before the post`() {
            assertEquals(listOf("report", "running", "channel", "post"), Run({ lowMemory() }).calls)
        }

        @Test
        fun `no stored report posts nothing`() {
            val run = Run({ null })
            assertFalse(run.result)
            assertEquals(listOf("report"), run.calls)
        }

        @Test
        fun `a force stop by the player posts nothing`() {
            val run = Run({ processEndedReport(open(), exit(ApplicationExitInfo.REASON_USER_REQUESTED), null, resumable = false) })
            assertFalse(run.result)
            assertTrue(run.posted.isEmpty())
        }

        @Test
        fun `an ending that is not a process death posts nothing`() {
            val ended = lowMemory().copy(kind = SessionEnd.ENDED_WITH_ERROR)
            assertNull(DeadSessionNotice.noticeText(ended))
            assertTrue(Run({ ended }).posted.isEmpty())
        }

        @Test
        fun `a denied notification permission on Android 13 or later posts nothing and does not throw`() {
            val run = Run({ lowMemory() }, sdk = 33, granted = false)
            assertFalse(run.result)
            assertEquals(listOf("report", "permission"), run.calls, "the report is still recorded")
        }

        @Test
        fun `below Android 13 there is no runtime permission to ask`() {
            val run = Run({ lowMemory() }, sdk = 32, granted = false)
            assertTrue(run.result)
            assertFalse("permission" in run.calls)
        }

        @Test
        fun `a granted permission on Android 13 or later posts`() {
            assertTrue(Run({ lowMemory() }, sdk = 34, granted = true).result)
        }

        @Test
        fun `a session that is already running gets no stale notice`() {
            val run = Run({ lowMemory() }, running = true)
            assertFalse(run.result)
            assertTrue(run.posted.isEmpty())
        }
    }

    @Nested
    @DisplayName("failures")
    inner class Failures {
        @Test
        fun `a ledger failure is swallowed`() {
            val run = Run({ throw IllegalStateException("database closed") })
            assertFalse(run.result)
            assertTrue(run.posted.isEmpty())
        }

        @Test
        fun `a channel failure is swallowed and nothing is posted`() {
            val run = Run({ lowMemory() }, channelFails = true)
            assertFalse(run.result)
            assertFalse("post" in run.calls)
        }

        @Test
        fun `a refused post is swallowed`() {
            assertFalse(Run({ lowMemory() }, postFails = true).result)
        }
    }

    @Nested
    @DisplayName("words")
    inner class Words {
        @Test
        fun `the stall watchdog's kill says the bot stopped itself`() {
            val report = processEndedReport(open(), exit(ApplicationExitInfo.REASON_SIGNALED), null, resumable = false, watchdog = WatchdogBreadcrumb("KILL"))
            val text = DeadSessionNotice.noticeText(report)!!
            assertTrue(text.body.contains("The bot stopped itself after"), text.body)
            assertTrue(text.body.endsWith("Press Start in UMA Auto+."), text.body)
        }

        @Test
        fun `a death without an exit record keeps the words without a cause`() {
            val text = DeadSessionNotice.noticeText(processEndedReport(open(), null, 2_500L, resumable = false))!!
            assertEquals("UMA Auto+ stopped unexpectedly during the run. Press Start in UMA Auto+.", text.body)
        }

        @Test
        fun `a resumable queue keeps the report's own resume instruction`() {
            val report = processEndedReport(open(queue = true), exit(ApplicationExitInfo.REASON_LOW_MEMORY), null, resumable = true)
            val text = DeadSessionNotice.noticeText(report)!!
            assertEquals(queueReportText(report.toJson()), text, "the same words Home shows")
            assertTrue(text.body.startsWith("UMA Auto+ stopped unexpectedly during run 2 of 3. Android closed it to free memory. Press Start"), text.body)
        }

        @Test
        fun `the notice adds nothing to the report's words but the plain Start instruction`() {
            val report = lowMemory()
            val home = queueReportText(report.toJson())
            val notice = DeadSessionNotice.noticeText(report)!!
            assertNull(home.nextAction)
            assertEquals(home.title, notice.title)
            assertEquals(home.reason, notice.reason)
            assertEquals("Press Start in UMA Auto+.", notice.nextAction)
        }

        @Test
        fun `a process death always becomes the app's current report, so Home shows the same one`() {
            for (current in listOf(null, JSONObject().put("kind", "DIAGNOSTIC_ENDED").toString(), JSONObject().put("kind", "STOPPED_BY_USER").toString())) {
                assertTrue(replacesLastReport(SessionEnd.PROCESS_ENDED, current), "$current")
            }
        }
    }

    @Nested
    @DisplayName("wiring")
    inner class Wiring {
        private fun repoFile(relative: String): File {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            repeat(8) {
                if (File(dir, relative).isFile) return File(dir, relative)
                dir = dir?.parentFile
            }
            throw AssertionError("$relative not found from the test working directory")
        }

        private val main = "android/app/src/main/java/com/steve1316/uma_android_automation"

        private fun source(name: String) = repoFile("$main/$name").readText().replace("\r\n", "\n")

        private fun body(
            text: String,
            signature: String,
        ) = text.substringAfter(signature).substringBefore("\n    }\n")

        @Test
        fun `app start runs the check after the database opens, before React Native loads`() {
            val onCreate = source("MainApplication.kt").substringAfter("override fun onCreate() {")
            val open = onCreate.indexOf("SettingsDatabase.openAtProcessStart(this)")
            val notice = onCreate.indexOf("DeadSessionNotice.postAtProcessStart(this)")
            assertTrue(open in 0 until notice && notice < onCreate.indexOf("loadReactNative(this)"), "open $open, notice $notice")
        }

        @Test
        fun `only a healthy database opened by this process is checked at start`() {
            val checked = SettingsDatabase.StartState.entries.filter { DeadSessionNotice.checksAtStart(it) }
            assertEquals(listOf(SettingsDatabase.StartState.OPEN, SettingsDatabase.StartState.RESTORED), checked)
            val start = body(source("DeadSessionNotice.kt"), "fun postAtProcessStart(application: Application) {")
            assertTrue(start.indexOf("if (!checksAtStart(SettingsDatabase.startState)) return") in 0 until start.indexOf("Thread {"))
        }

        @Test
        fun `the work runs on its own daemon thread inside a catch`() {
            val start = body(source("DeadSessionNotice.kt"), "fun postAtProcessStart(application: Application) {")
            assertTrue(start.contains("isDaemon = true") && start.contains("start()"))
            assertTrue(start.indexOf("try {") in 0 until start.indexOf("Thread {"))
            assertTrue(start.contains("} catch (e: Throwable) {"))
            assertTrue(start.contains("reportDeadSession = { QueueLedger.reportDeadSession(application) }"))
        }

        @Test
        fun `the notice never logs through MessageLog`() {
            val notice = source("DeadSessionNotice.kt")
            assertFalse(notice.contains("MessageLog.") || notice.contains("utils.MessageLog"))
        }

        @Test
        fun `the ledger hands back only a report this call made current`() {
            val ledger = source("QueueReport.kt")
            val detect = body(ledger, "private fun reportDeadSessionLocked(context: Context): QueueReport? {")
            assertTrue(detect.contains("return report.takeIf { store(context, it) }"))
            val store = body(ledger, "private fun store(context: Context, report: QueueReport): Boolean {")
            val write = store.indexOf("write(context, values, delete = listOf(KEY_OPEN_SESSION))")
            assertTrue(write in 0 until store.indexOf("stored = values.isNotEmpty()"), "set only after the write returned")
            assertTrue(store.trimEnd().endsWith("return stored"))
            assertTrue(ledger.contains("internal fun reportDeadSession(context: Context): QueueReport? = synchronized(lock) { reportDeadSessionLocked(context) }"))
        }

        @Test
        fun `the notice has its own id, opens the app, clears on tap and has no Stop action`() {
            val notice = source("DeadSessionNotice.kt")
            assertNotEquals(1, DeadSessionNotice.NOTIFICATION_ID, "id 1 is the library's ongoing status notification")
            val post = body(notice, "private fun post(context: Context, text: ReportText) {")
            assertTrue(post.contains("Intent(context, MainActivity::class.java)"))
            assertTrue(post.contains(".setAutoCancel(true)") && post.contains(".setOngoing(false)"))
            assertFalse(post.contains("addAction"))
            assertTrue(post.contains("notify(NOTIFICATION_ID, notification)"))
        }

        @Test
        fun `the channel is the library's and is created only when missing`() {
            assertEquals("STATUS", DeadSessionNotice.CHANNEL_ID)
            val channel = body(source("DeadSessionNotice.kt"), "private fun ensureChannel(context: Context) {")
            assertTrue(channel.indexOf("if (manager.getNotificationChannel(CHANNEL_ID) != null) return") in 0 until channel.indexOf("createNotificationChannel("))
        }

        @Test
        fun `a new session removes the notice as it begins`() {
            val start = source("StartModule.kt").substringAfter("fun onStartEvent(event: StartEvent) {")
            val duplicate = start.indexOf("if (!sessionActive.compareAndSet(false, true)) {")
            val cancel = start.indexOf("DeadSessionNotice.cancel(context)")
            assertTrue(duplicate in 0 until cancel && cancel < start.indexOf("Game.acquireWakeLock(context)"), "duplicate $duplicate, cancel $cancel")
            assertTrue(body(source("DeadSessionNotice.kt"), "fun cancel(context: Context) {").contains("cancel(NOTIFICATION_ID)"))
        }
    }
}
