package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile
import kotlin.random.Random

/**
 * The bug report zip: which files go in, that a planted secret never appears in any of them (contents, report
 * lines or names), that logs are read from their end, the size cap, and the report fields. StartModule is
 * impractical to unit-test, so its wiring is a source guard.
 */
@DisplayName("Bug report")
class BugReportTest {
    @TempDir
    lateinit var dir: File

    private val token = "MTIzNDU2Nzg5.planted.token-value"
    private val userId = "998877665544332211"
    private val pairing = "v1.target.UEFJUkNPREU"

    private fun log(name: String, ageSeconds: Int, text: String): File =
        File(dir, name).apply {
            writeText(text)
            setLastModified(System.currentTimeMillis() - ageSeconds * 1000L)
        }

    private fun entries(zip: File): Map<String, String> =
        ZipFile(zip).use { z -> z.entries().asSequence().associate { it.name to z.getInputStream(it).readBytes().toString(Charsets.UTF_8) } }

    private fun build(
        careerLogs: List<File> = emptyList(),
        session: List<String> = emptyList(),
        settings: String = "{}",
        secrets: List<String> = emptyList(),
        maxBytes: Long = BugReport.MAX_BYTES,
        report: List<String> = listOf("App: test"),
        maxLogChars: Int = BugReport.MAX_LOG_CHARS,
    ) = BugReport.build(File(dir, "out/report.zip"), report, settings, careerLogs, session, secrets, maxBytes, maxLogChars)

    @Test
    fun `keeps only the newest three career logs`() {
        log("old.txt", 400, "old")
        log("older.txt", 300, "older")
        log("mid.txt", 200, "mid")
        log("new.txt", 100, "new")
        File(dir, "notes.md").writeText("no")
        assertEquals(listOf("new.txt", "mid.txt", "older.txt"), BugReport.newestCareerLogs(dir).map { it.name })
    }

    @Test
    fun `bundles the report, the settings, the career logs and a session log that adds something`() {
        val a = log("a.txt", 20, "career one")
        val b = log("b.txt", 10, "career two")
        val parts = entries(build(listOf(b, a), session = listOf("career two", "still running"), settings = "{\"general\":1}", report = listOf("App: UMA Auto+ 1.7.3 (123)")))
        assertEquals(setOf("report.txt", "settings.json", "b.txt", "a.txt", "session-log.txt"), parts.keys)
        assertTrue(parts.getValue("report.txt").contains("App: UMA Auto+ 1.7.3 (123)"))
        assertEquals("{\"general\":1}", parts.getValue("settings.json"))
        assertEquals("career two\nstill running", parts.getValue("session-log.txt"))
    }

    @Test
    fun `leaves out a session log that equals the newest career log`() {
        val b = log("b.txt", 10, "same text\nline two")
        val parts = entries(build(listOf(b), session = listOf("same text", "line two")))
        assertFalse(parts.containsKey("session-log.txt"))
    }

    @Test
    fun `a planted secret appears in no part of the zip, including the report lines, the names and the settings`() {
        val withSecret = log("Bamboo_$token.txt", 5, "login ok\nDiscord token $token for user $userId\npairing $pairing\nend")
        val settings = "{\"leak\":\"$token\",\"hostInputPairingCode\":\"$pairing\"}"
        val parts =
            entries(
                build(
                    listOf(withSecret),
                    session = listOf("live $token"),
                    settings = settings,
                    secrets = listOf(token, userId, pairing),
                    report = listOf("App: test", "Rotation 1: $token / $userId"),
                ),
            )
        assertTrue(parts.size >= 4)
        parts.forEach { (name, text) ->
            for (secret in listOf(token, userId, pairing)) {
                assertFalse(name.contains(secret), "the name $name still holds a secret")
                assertFalse(text.contains(secret), "$name still holds a secret")
            }
        }
        assertTrue(parts.getValue("report.txt").contains("Rotation 1: ${BugReport.REMOVED} / ${BugReport.REMOVED}"))
        assertTrue(parts.keys.any { it.startsWith("Bamboo_${BugReport.REMOVED}") })
    }

    @Test
    fun `two names that collide after redaction both stay in the zip`() {
        val a = log("x_$token.txt", 5, "one")
        val b = log("x_$userId.txt", 6, "two")
        val parts = entries(build(listOf(a, b), secrets = listOf(token, userId)))
        assertEquals(4, parts.size)
    }

    @Test
    fun `values too short to be a secret are not blanked out of the logs`() {
        assertEquals("a b c", BugReport.redact("a b c", listOf("b", "")))
    }

    @Test
    fun `reads a log larger than the raw cap by its tail and the report names the cut`() {
        val first = "FIRST-LINE-MARKER " + "x".repeat(200)
        val lines = listOf(first) + (1..30_000).map { "line $it ${Random(it).nextLong()}" }
        val big = log("big.txt", 5, lines.joinToString("\n") + "\nLAST-LINE-MARKER")
        val parts = entries(build(listOf(big), maxLogChars = 100_000))
        val kept = parts.getValue("big.txt")
        assertFalse(kept.contains("FIRST-LINE-MARKER"))
        assertTrue(kept.endsWith("LAST-LINE-MARKER"))
        assertTrue(kept.length <= 100_000)
        assertTrue(kept.startsWith("line "), "the cut must start on a whole line")
        assertTrue(parts.getValue("report.txt").contains("Cut big.txt: it is ${big.length()} bytes, only its last ${kept.length} characters are included."))
    }

    @Test
    fun `builds the session text from the end of the buffer and names the cut`() {
        val lines = (1..5_000).map { "session line $it" }
        val parts = entries(build(session = lines, maxLogChars = 20_000))
        val kept = parts.getValue("session-log.txt").split("\n")
        assertEquals("session line 5000", kept.last())
        assertTrue(kept.size in 1 until lines.size)
        assertTrue(parts.getValue("report.txt").contains("Cut session-log.txt: only its last ${kept.size} of ${lines.size} lines are included."))
    }

    @Test
    fun `stays under the cap by cutting the oldest lines and says so in the report`() {
        val random = Random(7)
        val lines = (1..40_000).map { "line $it ${random.nextLong()} ${random.nextLong()}" }
        val big = log("big.txt", 5, lines.joinToString("\n"))
        val cap = 400_000L
        val zip = build(listOf(big), maxBytes = cap)
        assertTrue(zip.length() <= cap, "zip is ${zip.length()} bytes")
        val parts = entries(zip)
        val kept = parts.getValue("big.txt").split("\n")
        assertTrue(kept.size < lines.size && kept.size > 1000)
        assertEquals(lines.last(), kept.last())
        assertTrue(parts.getValue("report.txt").contains("Trimmed big.txt: kept the last ${kept.size} of ${lines.size} lines"))
    }

    @Test
    fun `fails clearly when even an empty report cannot fit`() {
        assertThrows(IllegalStateException::class.java) { build(listOf(log("a.txt", 1, "x")), maxBytes = 10) }
    }

    @Test
    fun `device lines carry the version, Android, device and the screen the bot sees`() {
        val lines = BugReport.deviceLines("1.7.3", 123, "16", 36, "samsung", "SM-S931B", 1080, 2340)
        assertEquals(
            listOf("App: UMA Auto+ 1.7.3 (123)", "Android: 16 (API 36)", "Device: samsung SM-S931B", "Screen the bot sees: 1080x2340"),
            lines,
        )
        assertTrue(BugReport.deviceLines("1", 1, "16", 36, "a", "b", 0, 0).last().contains("not captured yet"))
    }

    @Test
    fun `the module hands the settings, the secrets and the logs to the builder and survives any failure`() {
        fun repoFile(relative: String): File {
            var d: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            repeat(8) {
                if (File(d, relative).isFile) return File(d, relative)
                d = d?.parentFile
            }
            throw AssertionError("$relative not found from the test working directory")
        }
        val module = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt").readText()
        val body = module.substringAfter("fun createBugReport(").substringBefore("fun getLastQueueProgress")
        assertTrue(body.contains("BugReport.build("))
        assertTrue(body.contains("BugReport.newestCareerLogs(logsDir)"))
        assertTrue(body.contains("MessageLog.getMessageLogCopy(),"))
        assertFalse(body.contains("joinToString"), "the session buffer must not be joined whole")
        assertTrue(body.contains("secretValues"))
        assertTrue(body.contains("catch (e: Throwable)"), "an out-of-memory error must reach the alert, not kill the app")
        val home = repoFile("src/pages/Home/index.tsx").readText()
        assertTrue(home.contains("Share bug report"))
        assertFalse(home.contains("Send bug report"))
    }
}
