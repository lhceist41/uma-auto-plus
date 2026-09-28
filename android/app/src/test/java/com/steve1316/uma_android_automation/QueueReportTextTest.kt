package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.bot.TaskResultCode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The player words for how a session ended, and the wiring that puts them on the end notification.
 * The text table is the shared fixture `src/lib/__fixtures__/queueReportText.json`; StartModule is
 * impractical to unit-test directly, so the notification wiring is pinned by source guards.
 */
@DisplayName("Queue report text")
class QueueReportTextTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val main = "android/app/src/main/java/com/steve1316/uma_android_automation"

    private fun source(relative: String) = repoFile(relative).readText().replace("\r\n", "\n")

    private val cases by lazy { JSONObject(repoFile("src/lib/__fixtures__/queueReportText.json").readText()).getJSONArray("cases") }

    private fun caseList() = (0 until cases.length()).map { cases.getJSONObject(it) }

    /** Every combination of the facts a report can carry, for each ending. */
    private fun sweep(errorPosted: Boolean? = null): Sequence<JSONObject> =
        sequence {
            val keys = REPORT_REASON_KEYS.keys + listOf("", "UNKNOWN_KEY")
            val codes = TaskResultCode.entries.map { it.name } + listOf("UNKNOWN_CODE", null)
            val postedValues = listOfNotNull(errorPosted).ifEmpty { listOf(false, true) }
            val counts = listOf(Triple(1, 1, 1), Triple(5, 2, 3), Triple(5, 5, 5), Triple(0, 0, 0))
            for (end in SessionEnd.entries) {
                for (key in keys) {
                    for (code in codes) {
                        for (posted in postedValues) {
                            for (resumable in listOf(false, true)) {
                                for ((total, done, reached) in counts) {
                                    val runs = JSONArray().also { if (code != null) it.put(JSONObject().put("run", reached).put("resultCode", code)) }
                                    yield(
                                        JSONObject()
                                            .put("kind", end.name)
                                            .put("queueEnabled", total > 1)
                                            .put("totalRuns", total)
                                            .put("startFromRun", 1)
                                            .put("completedRuns", done)
                                            .put("runReached", reached)
                                            .put("resumable", resumable)
                                            .put("reasonKey", key)
                                            .put("breakpointDetail", JSONObject.NULL)
                                            .put("errorPosted", posted)
                                            .put("runs", runs)
                                            .put("exitInfo", JSONObject.NULL),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    @Nested
    @DisplayName("the shared fixture")
    inner class Fixture {
        @Test
        fun `every case reads exactly as the fixture says`() {
            for (case in caseList()) {
                val expected = case.getJSONObject("text")
                val actual = queueReportText(case.getJSONObject("report"))
                val name = case.getString("name")
                assertEquals(expected.getString("title"), actual.title, name)
                assertEquals(expected.getString("reason"), actual.reason, name)
                assertEquals(if (expected.isNull("nextAction")) null else expected.getString("nextAction"), actual.nextAction, name)
            }
        }

        @Test
        fun `the fixture covers every ending and every reason key`() {
            val reports = caseList().map { it.getJSONObject("report") }
            assertEquals(SessionEnd.entries.map { it.name }.toSet(), reports.map { it.optString("kind") }.filter { k -> SessionEnd.entries.any { it.name == k } }.toSet())
            val keyed = reports.filter { it.optString("kind") in setOf("NAVIGATION_FAILED_BETWEEN_RUNS", "LAUNCH_FAILED_BEFORE_RUN", "STOPPED_BY_BOT") }.map { it.optString("reasonKey") }.toSet()
            assertTrue(keyed.containsAll(REPORT_REASON_KEYS.keys), "missing: ${REPORT_REASON_KEYS.keys - keyed}")
            assertTrue(keyed.contains(""), "the empty key")
            assertTrue(keyed.any { it.isNotEmpty() && it !in REPORT_REASON_KEYS }, "an unknown key")
            assertTrue(reports.any { it.optString("kind").isNotEmpty() && SessionEnd.entries.none { e -> e.name == it.optString("kind") } }, "an unknown ending")
        }

        @Test
        fun `the mapped keys are exactly the keys the bot sets`() {
            val set = mutableSetOf<String>()
            set += Regex("reasonKey = \"(\\w+)\"").findAll(source("$main/CareerLaunchNavigator.kt")).map { it.groupValues[1] }
            set += Regex("reasonKey = \"(\\w+)\"").findAll(source("$main/BetweenRunDialogs.kt")).map { it.groupValues[1] }
            set += Regex("(?:const val \\w+ = |-> )\"([A-Z0-9_]+)\"").findAll(source("$main/bot/AccessibilityRepair.kt")).map { it.groupValues[1] }
            val startModule = source("$main/StartModule.kt")
            set += Regex("reasonKey = \"(\\w+)\"").findAll(startModule).map { it.groupValues[1] }
            set += Regex("queueStopKey = \"(\\w+)\"").findAll(startModule).map { it.groupValues[1] }
            set += Regex("queueStopKey = \"(\\w+)\"").findAll(source("$main/bot/Campaign.kt")).map { it.groupValues[1] }
            assertEquals(set, REPORT_REASON_KEYS.keys)
        }
    }

    @Nested
    @DisplayName("every ending")
    inner class Endings {
        @Test
        fun `no report and an unknown ending read as a neutral stop`() {
            for (report in listOf(null, JSONObject(), JSONObject().put("kind", "SOMETHING_NEW"), JSONObject().put("kind", JSONObject.NULL))) {
                val text = queueReportText(report)
                assertEquals("Bot stopped", text.title)
                assertEquals("The bot session ended.", text.reason)
                assertNull(text.nextAction)
            }
        }

        @Test
        fun `the words never carry codes, raw values or the library's success line`() {
            val code = Regex("[A-Z]{2,}_[A-Z]")
            for (report in sweep()) {
                val text = queueReportText(report)
                val all = "${text.title} ${text.body}"
                assertFalse(code.containsMatchIn(all), all)
                for (raw in listOf("null", "Exception", "Completed successfully", "TASK_RESULT", "{", "}")) assertFalse(all.contains(raw), "$raw in $all")
                assertTrue(text.title.isNotBlank() && text.reason.endsWith(".") && (text.nextAction?.endsWith(".") ?: true), all)
                assertEquals(if (text.nextAction == null) text.reason else "${text.reason} ${text.nextAction}", text.body)
            }
        }

        @Test
        fun `every count of done runs is followed by how many runs ended with an error`() {
            val errorCodes = setOf("TASK_RESULT_UNHANDLED_EXCEPTION", "TASK_RESULT_CONNECTION_ERROR", "TASK_RESULT_TIMED_OUT", "TASK_RESULT_QUEUE_NAVIGATION_FAILED")
            val count = Regex("\\bruns? (are )?done|The run is done")
            var counted = 0
            for (report in sweep()) {
                val reason = queueReportText(report).reason
                if (!count.containsMatchIn(reason)) continue
                counted++
                val runs = report.getJSONArray("runs")
                val errored = runs.length() == 1 && runs.getJSONObject(0).getString("resultCode") in errorCodes
                assertEquals(errored, reason.endsWith(" 1 run ended with an error."), "${report.getString("kind")}: $reason")
                assertFalse(reason.contains("(1 ended with an error)"), "errored runs are not part of the done count")
            }
            assertTrue(counted > 0)
        }

        @Test
        fun `after a game error the title never reads like a success`() {
            val success = Regex("finish|success|complete|done|nothing to resume|diagnostic", RegexOption.IGNORE_CASE)
            for (report in sweep(errorPosted = true)) {
                val title = queueReportText(report).title
                assertFalse(success.containsMatchIn(title), "${report.getString("kind")}: $title")
            }
        }

        @Test
        fun `a resumable ending says how long Start can still continue it`() {
            for (report in sweep(errorPosted = false).filter { it.getBoolean("resumable") }) {
                val kind = SessionEnd.valueOf(report.getString("kind"))
                val neverResumed = setOf(SessionEnd.SINGLE_RUN_ENDED, SessionEnd.ENDED_WITH_ERROR, SessionEnd.ROTATION_NOT_PREPARED, SessionEnd.DIAGNOSTIC_ENDED)
                if (kind.clearsQueueState || kind in neverResumed || kind.name.startsWith("REFUSED")) continue
                val text = queueReportText(report)
                assertEquals("Queue paused".takeIf { kind != SessionEnd.PROCESS_ENDED } ?: "App stopped unexpectedly", text.title)
                assertTrue(text.nextAction!!.contains("within 24 hours, with Run Queue on and the same number of runs, to continue the queue"), "$kind: ${text.nextAction}")
            }
        }

        @Test
        fun `a retry is stated only when the run record says it happened`() {
            for (report in sweep()) {
                val text = queueReportText(report)
                assertFalse("${text.reason} ${text.nextAction}".contains("retry"), "no run in the sweep was retried: ${report.getString("kind")}")
            }
            val halt = { retried: Boolean ->
                JSONObject().put("kind", "STOP_ON_ERROR").put("runReached", 2).put("resumable", true).put("queueEnabled", true).put("totalRuns", 5)
                    .put("runs", JSONArray().put(JSONObject().put("run", 2).put("resultCode", "TASK_RESULT_TIMED_OUT").put("retried", retried)))
            }
            assertEquals("Run 2 timed out again after a retry, and Stop Queue on Error is on.", queueReportText(halt(true)).reason)
            assertEquals("Run 2 timed out, and Stop Queue on Error is on.", queueReportText(halt(false)).reason)
        }

        @Test
        fun `every refusal is a not-started that says nothing was spent and what to do`() {
            for (kind in listOf(SessionEnd.REFUSED_NO_APP_START, SessionEnd.REFUSED_LAUNCH_IDENTITY, SessionEnd.REFUSED_DATABASE_UNHEALTHY, SessionEnd.ROTATION_NOT_PREPARED)) {
                val text = queueReportText(JSONObject().put("kind", kind.name))
                assertEquals("Not started", text.title, kind.name)
                assertTrue(text.reason.startsWith("Not started, and nothing was spent: "), kind.name)
                assertTrue(!text.nextAction.isNullOrBlank(), kind.name)
            }
        }

        @Test
        fun `the refusal messages the app already shows are the same words`() {
            assertEquals(StartModule.SETTINGS_NOT_DELIVERED_MESSAGE, "$LAUNCH_IDENTITY_REASON $LAUNCH_IDENTITY_NEXT")
            assertEquals(StartModule.DATABASE_UNHEALTHY_MESSAGE, "$DATABASE_UNHEALTHY_REASON $DATABASE_UNHEALTHY_NEXT")
        }
    }

    @Nested
    @DisplayName("the end notification wiring")
    inner class Wiring {
        private val startModule by lazy { source("$main/StartModule.kt") }

        private val session by lazy {
            val start = startModule.indexOf("fun onStartEvent(event: StartEvent)")
            val end = startModule.indexOf("\n    /**\n     * Tests the Discord connection", start)
            assertTrue(start in 0 until end)
            startModule.substring(start, end)
        }

        private val notifier by lazy {
            val start = startModule.indexOf("private fun notifySessionEnd(libraryThread: Thread, report: QueueReport?) {")
            assertTrue(start >= 0)
            startModule.substring(start, startModule.indexOf("\n    }\n", start))
        }

        private val sessionTry by lazy { session.indexOf("\n            try {\n") }
        private val sessionCatch by lazy { session.indexOf("\n            } catch (e: Throwable) {\n") }
        private val sessionFinally by lazy { session.indexOf("\n            } finally {\n") }

        @Test
        fun `the library's thread is captured on entry, before the session can end`() {
            val capture = session.indexOf("val libraryThread = Thread.currentThread()")
            assertTrue(capture > session.indexOf("if (!sessionActive.compareAndSet(false, true)) {"), "after the duplicate-session return")
            assertTrue(capture in 0 until sessionTry)
            assertTrue(sessionTry < sessionCatch && sessionCatch < sessionFinally)
        }

        @Test
        fun `the notification starts only from the finally, with the report the writer just stored`() {
            val finallyBlock = session.substring(sessionFinally)
            val call = "notifySessionEnd(libraryThread, writeSessionReport(ledger))"
            assertEquals(1, startModule.split("notifySessionEnd(").size - 2, "one call site besides the declaration")
            assertTrue(finallyBlock.indexOf(call) in finallyBlock.indexOf("ledgerHeartbeat?.interrupt()") until finallyBlock.indexOf("sessionActive.set(false)"))
            val writer = startModule.substring(startModule.indexOf("private fun writeSessionReport(ledger: SessionLedger): QueueReport? {")).substringBefore("\n    }\n")
            assertTrue(writer.contains("ledger.report(verdict, System.currentTimeMillis()).also { QueueLedger.finishSession(context, it) }"), "the returned report is the stored one")
        }

        @Test
        fun `every way out of the session after entry passes the finally`() {
            val capture = session.indexOf("val libraryThread = Thread.currentThread()")
            val returns = Regex("\\breturn\\b(?!@)").findAll(session).map { it.range.first }.filter { it > capture }.toList()
            assertTrue(returns.size >= 3, "the refusal, diagnostic and rotation returns")
            for (at in returns) assertTrue(at in sessionTry until sessionCatch, "a return at $at leaves outside the try")
            assertTrue(session.substring(sessionCatch, sessionFinally).contains("throw e"), "a refused start rethrows through the finally")
            assertTrue(session.indexOf("dispatchDiagnostic(") in sessionTry until sessionCatch, "the not-started refusal throws inside the try")
            assertTrue(session.indexOf("ledger.launchIdentityRefused = true\n                    return") in sessionTry until sessionCatch)
            assertTrue(session.indexOf("ledger.rotationNotPrepared = true\n                        return") in sessionTry until sessionCatch)
        }

        @Test
        fun `a refused settings file never starts the library, so its success line never appears`() {
            val start = startModule.substring(startModule.indexOf("    fun start(launchId: String) {")).substringBefore("\n    }\n")
            val refuse = start.indexOf("refuseStartForDatabase()")
            assertTrue(refuse in 0 until start.indexOf("return", refuse))
            assertTrue(start.indexOf("return", refuse) < start.indexOf("startProjection()"))
            val dialog = startModule.substring(startModule.indexOf("private fun refuseStartForDatabase() {")).substringBefore("\n    }\n")
            assertTrue(dialog.contains(".setTitle(\"${queueReportText(JSONObject().put("kind", "REFUSED_DATABASE_UNHEALTHY")).title}\")"))
        }

        @Test
        fun `the notifier waits for the library's thread before it writes`() {
            val join = notifier.indexOf("libraryThread.join()")
            val sleep = notifier.indexOf("Thread.sleep(END_NOTIFICATION_DELAY_MS)")
            val indent = "\n                        "
            val guard = notifier.indexOf("finishEndNotification(${indent}captureRunning = { MediaProjectionService.isRunning },${indent}sessionRunning = { BotService.isRunning },")
            val post = notifier.indexOf("NotificationUtils.updateNotification(context, MainActivity::class.java, false, text.body, title = text.title, displayBigText = true)")
            assertTrue(join in 0 until sleep && sleep < guard && guard < post, "join $join, sleep $sleep, guard $guard, post $post")
            assertTrue(notifier.contains("val text = queueReportText(report?.toJson())"))
        }

        @Test
        fun `the notifier runs off the bot thread, cannot throw and never uses the app log`() {
            assertTrue(notifier.contains("isDaemon = true"))
            assertTrue(notifier.contains("start()"))
            assertEquals(2, Regex("\\} catch \\(e: Throwable\\) \\{").findAll(notifier).count())
            assertFalse(notifier.contains("MessageLog"))
            assertFalse(notifier.contains("ExceptionEvent"))
            assertFalse(notifier.contains("EventBus"))
        }

        @Test
        fun `only the notifier touches notifications, and no exception is synthesized`() {
            val users =
                repoFile("$main/StartModule.kt")
                    .parentFile
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "kt" && it.readText().contains("NotificationUtils") }
                    .map { it.name }
                    .toList()
            assertEquals(listOf("StartModule.kt"), users)
            // The end notifier, and the live progress line it stops first.
            assertEquals(2, Regex("NotificationUtils\\.").findAll(startModule).count())
            assertEquals(1, Regex("update = \\{ NotificationUtils\\.updateNotification\\(").findAll(startModule).count())
            val progressLine = Regex("ProgressNotification\\.begin\\(captureRunning = \\{ MediaProjectionService\\.isRunning \\}\\) \\{ text ->\\s+NotificationUtils\\.updateNotification\\(")
            assertEquals(1, progressLine.findAll(startModule).count())
            assertEquals(1, Regex("\\bExceptionEvent\\(").findAll(startModule).count(), "only the game thread's own post")
        }
    }
}
