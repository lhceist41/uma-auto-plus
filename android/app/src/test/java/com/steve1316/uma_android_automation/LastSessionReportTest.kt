package com.steve1316.uma_android_automation

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
 * The app's "Last session" report: what the bridge returns, which session becomes the current
 * report, and the dismissal that keeps the record. The ledger store and the bridge touch SQLite and
 * React Native, so their wiring is pinned by source guards; the decisions are pure.
 */
@DisplayName("Last session report")
class LastSessionReportTest {
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

    private fun report(kind: String, dismissed: Boolean = false) = JSONObject().put("sessionId", "s-$kind").put("kind", kind).put("dismissed", dismissed).toString()

    @Nested
    @DisplayName("the bridge payload")
    inner class Payload {
        @Test
        fun `no stored report is no payload`() {
            assertNull(lastReportPayload(null))
        }

        @Test
        fun `every fixture report comes back unchanged with its words`() {
            val cases = JSONObject(repoFile("src/lib/__fixtures__/queueReportText.json").readText()).getJSONArray("cases")
            for (i in 0 until cases.length()) {
                val case = cases.getJSONObject(i)
                val stored = case.getJSONObject("report")
                val payload = JSONObject(lastReportPayload(stored.toString())!!)
                assertEquals(stored.toString(), payload.getJSONObject("report").toString(), case.getString("name"))
                val expected = case.getJSONObject("text")
                val text = payload.getJSONObject("text")
                assertEquals(expected.getString("title"), text.getString("title"), case.getString("name"))
                assertEquals(expected.getString("reason"), text.getString("reason"), case.getString("name"))
                assertEquals(expected.isNull("nextAction"), text.isNull("nextAction"), case.getString("name"))
                if (!expected.isNull("nextAction")) assertEquals(expected.getString("nextAction"), text.getString("nextAction"))
            }
        }

        @Test
        fun `only a known ending that played a queue or run is a run ending`() {
            for (end in SessionEnd.entries) {
                val payload = JSONObject(lastReportPayload(report(end.name))!!)
                assertEquals(end !in NOT_A_RUN_ENDINGS, payload.getBoolean("runEnding"), end.name)
            }
            assertFalse(JSONObject(lastReportPayload(report("SOMETHING_NEW"))!!).getBoolean("runEnding"))
        }

        @Test
        fun `a stored value that is not JSON comes back as no report with the neutral words`() {
            val payload = JSONObject(lastReportPayload("{broken")!!)
            assertTrue(payload.isNull("report"))
            assertEquals("Bot stopped", payload.getJSONObject("text").getString("title"))
            assertEquals("The bot session ended.", payload.getJSONObject("text").getString("reason"))
            assertTrue(payload.getJSONObject("text").isNull("nextAction"))
            assertFalse(payload.getBoolean("runEnding"))
        }

        @Test
        fun `the bridge returns the payload of the ledger's report`() {
            val bridge = source("$main/StartModule.kt").substringAfter("fun getLastQueueReport(promise: Promise) {").substringBefore("\n    }\n")
            assertEquals("promise.resolve(lastReportPayload(QueueLedger.lastReport(context)))", bridge.trim())
        }
    }

    @Nested
    @DisplayName("which session becomes the current report")
    inner class Precedence {
        private val runEndings = SessionEnd.entries.filter { it !in NOT_A_RUN_ENDINGS }

        @Test
        fun `the endings that played no run are the refusals and the diagnostic`() {
            assertEquals(
                setOf(
                    SessionEnd.REFUSED_NO_APP_START,
                    SessionEnd.REFUSED_LAUNCH_IDENTITY,
                    SessionEnd.REFUSED_DATABASE_UNHEALTHY,
                    SessionEnd.ROTATION_NOT_PREPARED,
                    SessionEnd.DIAGNOSTIC_ENDED,
                ),
                NOT_A_RUN_ENDINGS,
            )
        }

        @Test
        fun `a queue or run ending always becomes the current report`() {
            val currents = listOf(null, "{broken") + SessionEnd.entries.flatMap { listOf(report(it.name), report(it.name, dismissed = true)) } + report("SOMETHING_NEW")
            for (kind in runEndings) for (current in currents) assertTrue(replacesLastReport(kind, current), "$kind over $current")
        }

        @Test
        fun `a refusal or diagnostic never replaces an undismissed queue or run report`() {
            for (kind in NOT_A_RUN_ENDINGS) {
                for (existing in runEndings) assertFalse(replacesLastReport(kind, report(existing.name)), "$kind over $existing")
                assertFalse(replacesLastReport(kind, report("SOMETHING_NEW")), "$kind over an ending this version does not know")
            }
        }

        @Test
        fun `a refusal or diagnostic replaces nothing worth keeping`() {
            for (kind in NOT_A_RUN_ENDINGS) {
                assertTrue(replacesLastReport(kind, null), "$kind over no report")
                assertTrue(replacesLastReport(kind, "{broken"), "$kind over an unreadable report")
                for (existing in SessionEnd.entries) assertTrue(replacesLastReport(kind, report(existing.name, dismissed = true)), "$kind over dismissed $existing")
                for (existing in NOT_A_RUN_ENDINGS) assertTrue(replacesLastReport(kind, report(existing.name)), "$kind over $existing")
            }
        }

        @Test
        fun `the store asks before replacing the report, and always closes the open record and writes the history`() {
            val store = source("$main/QueueReport.kt").substringAfter("private fun store(context: Context, report: QueueReport) {").substringBefore("\n    }\n")
            val read = store.indexOf("val current = read(context, listOf(KEY_LAST_REPORT))[KEY_LAST_REPORT]")
            val decide = store.indexOf("if (replacesLastReport(report.kind, current)) mapOf(KEY_LAST_REPORT to report.lastReportValue()) else emptyMap()")
            val write = store.indexOf("write(context, values, delete = listOf(KEY_OPEN_SESSION))")
            assertTrue(read in 0 until decide && decide < write, "read $read, decide $decide, write $write")
            val history = store.indexOf("OutcomeCorpus.append(context, report.toJson(), OutcomeCorpus.QUEUE_LEDGER_PATH)")
            assertTrue(history > store.indexOf("} catch (e: Exception) {"), "the history line is written whatever the report decision")
        }
    }

    @Nested
    @DisplayName("dismissing")
    inner class Dismiss {
        private val ledger by lazy { source("$main/QueueReport.kt") }

        @Test
        fun `dismissing marks the report and keeps it`() {
            val dismiss = ledger.substringAfter("fun dismissLastReport(context: Context, sessionId: String): Boolean {").substringBefore("\n    }\n")
            assertTrue(dismiss.contains("if (report.optString(\"sessionId\") != sessionId) return false"), "only the report the app shows")
            assertTrue(dismiss.contains("write(context, mapOf(KEY_LAST_REPORT to report.put(\"dismissed\", true).toString()))"))
            assertFalse(dismiss.contains("delete"))
        }

        @Test
        fun `nothing deletes the current report`() {
            assertFalse(Regex("delete = listOf\\([^)]*KEY_LAST_REPORT").containsMatchIn(ledger))
            assertEquals(1, Regex("\\bdelete = listOf\\(").findAll(ledger).count(), "only the store's open-record delete")
        }

        @Test
        fun `the bridge dismisses through the ledger`() {
            val bridge = source("$main/StartModule.kt").substringAfter("fun dismissLastQueueReport(sessionId: String, promise: Promise) {").substringBefore("\n    }\n")
            assertEquals("promise.resolve(QueueLedger.dismissLastReport(context, sessionId))", bridge.trim())
        }
    }
}
