package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Telemetry adb-pullability repair.
 *
 * App-written files can materialize `0600 u0_aXX:u0_aXX` on this emulator image, which locks the
 * non-root adb shell out of the telemetry pull: `decisions.jsonl` and `career_state.jsonl` were
 * unpullable this way after the Mejiro Ryan career while `careers.jsonl` rode an older readable
 * inode, and the collector correctly refuses a partial required set. The fix mirrors the per-career
 * log's existing `setReadable(true, false)`: [OutcomeCorpus.appendLineAndMakeReadable] repairs the
 * mode after every append, and [OutcomeCorpus.ensureExistingFilesReadable] sweeps the corpus at app
 * start so opening the app (no bot run, no TP spend) heals a completed run's files.
 *
 * The file-selection and byte-preservation behavior is exercised against a real temp directory. The
 * readable-mode bit itself is not asserted here: `File.setReadable` has no POSIX-mode effect on the
 * Windows/JVM host, so the actual mode change is proven in the post-landing device pull, not faked.
 * The permission-call ordering and the app-start wiring are pinned with source guards.
 */
@DisplayName("Telemetry adb-pullability repair")
class OutcomeCorpusReadabilityTest {
    private val outcomeCorpus by lazy { sourceFile("utils/OutcomeCorpus.kt").readText().replace("\r\n", "\n") }
    private val mainApplication by lazy { sourceFile("MainApplication.kt").readText().replace("\r\n", "\n") }

    // ---- Path constants: still resolve under external outcomes/, unchanged (behavior on the compiled values) ----

    @Test
    fun `the three telemetry paths resolve under the single owned outcomes directory`() {
        assertEquals("outcomes", OutcomeCorpus.OUTCOMES_DIR)
        assertEquals("outcomes/careers.jsonl", OutcomeCorpus.CORPUS_PATH)
        assertEquals("outcomes/decisions.jsonl", OutcomeCorpus.DECISIONS_PATH)
        assertEquals("outcomes/career_state.jsonl", OutcomeCorpus.CAREER_STATE_PATH)
        for (path in listOf(OutcomeCorpus.CORPUS_PATH, OutcomeCorpus.DECISIONS_PATH, OutcomeCorpus.CAREER_STATE_PATH)) {
            assertTrue(path.startsWith(OutcomeCorpus.OUTCOMES_DIR + "/"), "$path is under ${OutcomeCorpus.OUTCOMES_DIR}/")
            assertTrue(path.endsWith(".jsonl"), "$path is a .jsonl file")
        }
    }

    // ---- appendLineAndMakeReadable: exact bytes, append-only, newline preserved ----

    @Test
    fun `append writes the exact bytes it is given`(
        @TempDir tempDir: File,
    ) {
        val file = File(tempDir, "careers.jsonl")
        val line = """{"result":"COMPLETE","trainee":"Mejiro_Ryan"}""" + "\n"
        OutcomeCorpus.appendLineAndMakeReadable(file, line)
        assertTrue(file.isFile, "the file was created")
        assertArrayEquals(line.toByteArray(Charsets.UTF_8), file.readBytes(), "bytes match the input exactly")
    }

    @Test
    fun `append is append-only and preserves each newline without rewriting prior lines`(
        @TempDir tempDir: File,
    ) {
        val file = File(tempDir, "decisions.jsonl")
        val line1 = """{"seq":1}""" + "\n"
        val line2 = """{"seq":2}""" + "\n"
        OutcomeCorpus.appendLineAndMakeReadable(file, line1)
        OutcomeCorpus.appendLineAndMakeReadable(file, line2)
        // Exact concatenation: no truncation, no duplicated line, no lost or added newline.
        assertEquals(line1 + line2, file.readText(Charsets.UTF_8))
        assertEquals(2, file.readText(Charsets.UTF_8).count { it == '\n' }, "each record kept its own trailing newline")
    }

    @Test
    fun `append leaves the written content intact and readable after the mode repair`(
        @TempDir tempDir: File,
    ) {
        val file = File(tempDir, "career_state.jsonl")
        val line = """{"career":"tok","seq":7}""" + "\n"
        OutcomeCorpus.appendLineAndMakeReadable(file, line)
        // The best-effort setReadable must never corrupt or truncate the line that was just written.
        assertTrue(file.canRead(), "the file remains readable")
        assertArrayEquals(line.toByteArray(Charsets.UTF_8), file.readBytes(), "content survives the mode repair unchanged")
    }

    // ---- ensureJsonlReadable: targets only outcomes/*.jsonl, mode-only, idempotent, dir-tolerant ----

    @Test
    fun `the sweep touches only jsonl files and never alters their bytes`(
        @TempDir tempDir: File,
    ) {
        val jsonl = File(tempDir, "careers.jsonl").apply { writeText("""{"a":1}""" + "\n") }
        val other = File(tempDir, "notes.txt").apply { writeText("leave me\n") }
        val noExt = File(tempDir, "README").apply { writeText("keep\n") }
        val jsonlBytes = jsonl.readBytes()
        val otherBytes = other.readBytes()
        val noExtBytes = noExt.readBytes()

        OutcomeCorpus.ensureJsonlReadable(tempDir)

        // Nothing deleted, renamed, copied, or truncated - every file is byte-for-byte intact.
        assertTrue(jsonl.isFile && other.isFile && noExt.isFile, "all files still present")
        assertArrayEquals(jsonlBytes, jsonl.readBytes(), "the .jsonl file is unchanged")
        assertArrayEquals(otherBytes, other.readBytes(), "the non-.jsonl file is unchanged")
        assertArrayEquals(noExtBytes, noExt.readBytes(), "the extensionless file is unchanged")
    }

    @Test
    fun `the sweep is idempotent across repeated runs`(
        @TempDir tempDir: File,
    ) {
        val decisions = File(tempDir, "decisions.jsonl").apply { writeText("""{"seq":1}""" + "\n") }
        val careerState = File(tempDir, "career_state.jsonl").apply { writeText("""{"seq":1}""" + "\n") }
        val decisionsBytes = decisions.readBytes()
        val careerStateBytes = careerState.readBytes()

        OutcomeCorpus.ensureJsonlReadable(tempDir)
        OutcomeCorpus.ensureJsonlReadable(tempDir)
        OutcomeCorpus.ensureJsonlReadable(tempDir)

        assertEquals(2, tempDir.listFiles()!!.size, "no files added or removed")
        assertArrayEquals(decisionsBytes, decisions.readBytes())
        assertArrayEquals(careerStateBytes, careerState.readBytes())
    }

    @Test
    fun `the sweep skips a directory that happens to end in jsonl`(
        @TempDir tempDir: File,
    ) {
        val dir = File(tempDir, "archive.jsonl").apply { mkdirs() }
        val real = File(tempDir, "careers.jsonl").apply { writeText("""{"a":1}""" + "\n") }

        OutcomeCorpus.ensureJsonlReadable(tempDir)

        assertTrue(dir.isDirectory, "the .jsonl-named directory is left as a directory, not touched as a file")
        assertTrue(real.isFile, "the real .jsonl file is still present")
    }

    @Test
    fun `the sweep is a no-op when the directory is absent`(
        @TempDir tempDir: File,
    ) {
        val absent = File(tempDir, "outcomes")
        assertFalse(absent.exists())
        // Must not throw.
        OutcomeCorpus.ensureJsonlReadable(absent)
        assertFalse(absent.exists(), "the sweep never creates the directory")
    }

    @Test
    fun `the sweep is a no-op when the directory is empty`(
        @TempDir tempDir: File,
    ) {
        val empty = File(tempDir, "outcomes").apply { mkdirs() }
        // Must not throw.
        OutcomeCorpus.ensureJsonlReadable(empty)
        assertEquals(0, empty.listFiles()!!.size, "the empty directory stays empty")
    }

    @Test
    fun `the sweep attempts every jsonl file in the directory`(
        @TempDir tempDir: File,
    ) {
        val files = (1..5).map { File(tempDir, "file$it.jsonl").apply { writeText("""{"n":$it}""" + "\n") } }
        OutcomeCorpus.ensureJsonlReadable(tempDir)
        // A per-file failure must not abort the loop; with no failure injectable on the JVM host, prove
        // the loop covers the whole set - all five remain present and unchanged (structural isolation is
        // pinned by the source guard below).
        files.forEachIndexed { i, f -> assertEquals("""{"n":${i + 1}}""" + "\n", f.readText(), "file${i + 1} untouched") }
    }

    // ---- Source contract: writer ordering, non-fatal repair, no schema/byte change ----

    @Test
    fun `append repairs readability after the append, inside its own catch so a failure cannot lose the write`() {
        val body = functionBody("internal fun appendLineAndMakeReadable(file: File, line: String) {")
        val appendIdx = body.indexOf("file.appendText(line)")
        val readableIdx = body.indexOf("file.setReadable(true, false)")
        assertTrue(appendIdx >= 0, "the line is appended")
        assertTrue(readableIdx > appendIdx, "the readable-mode repair runs after the append, not before")
        // The setReadable sits in its own try/catch, so a permission failure cannot propagate and discard
        // the already-completed append.
        val tail = body.substring(appendIdx)
        assertTrue(tail.contains("try {") && tail.contains("catch"), "the mode repair is wrapped in a best-effort try/catch")
    }

    @Test
    fun `the public append routes through the readable-mode writer and keeps the exact newline format`() {
        val body = functionBody("fun append(context: Context, record: JSONObject, path: String = CORPUS_PATH, maxBytes: Long? = null) {")
        assertTrue(
            body.contains("appendLineAndMakeReadable(file, record.toString() + \"\\n\")"),
            "append delegates the write to the readable-mode helper with the unchanged `record + newline` format",
        )
        assertFalse(body.contains("file.appendText("), "append no longer writes directly without the mode repair")
    }

    @Test
    fun `the sweep body performs no content read, rewrite, rename, copy, or delete`() {
        val body = functionBody("internal fun ensureJsonlReadable(outcomesDir: File) {")
        // Mode-only: the only file operations are listing, the isFile/name filter, and setReadable.
        for (forbidden in listOf("readText", "readBytes", "writeText", "writeBytes", "renameTo", "copyTo", "delete(", "mkdir")) {
            assertFalse(body.contains(forbidden), "the sweep must not call $forbidden (mode-only, byte-preserving)")
        }
        assertTrue(body.contains(".jsonl"), "only .jsonl files are targeted")
        assertTrue(body.contains("file.isFile"), "only regular files are targeted, not subdirectories")
        assertTrue(body.contains("file.setReadable(true, false)"), "the sweep applies world-read")
        // Per-file isolation: the setReadable is inside a per-entry try so one failure cannot abort the rest.
        val perEntry = body.substring(body.indexOf("forEach"))
        assertTrue(perEntry.contains("try {") && perEntry.contains("catch"), "each file's repair is individually guarded")
    }

    // ---- Source contract: app-start wiring, unconditional, no session/BotService gate ----

    @Test
    fun `the migration sweep is invoked unconditionally from MainApplication onCreate`() {
        val onCreate = functionBodyIn(mainApplication, "override fun onCreate() {")
        val call = "OutcomeCorpus.ensureExistingFilesReadable(this)"
        assertTrue(onCreate.contains(call), "app start invokes the telemetry self-heal")
        // Unconditional and early: after super.onCreate(), before the ML Kit init block, not behind an `if`.
        val superIdx = onCreate.indexOf("super.onCreate()")
        val callIdx = onCreate.indexOf(call)
        val mlkitIdx = onCreate.indexOf("MlKit.initialize(this)")
        assertTrue(superIdx in 0 until callIdx, "the call runs after super.onCreate()")
        assertTrue(callIdx < mlkitIdx, "the call runs at the top of onCreate, before other initialization")
        val precedingLine = onCreate.substring(0, callIdx).substringAfterLast('\n')
        assertFalse(precedingLine.trimStart().startsWith("if"), "the call is not gated behind a condition")
    }

    @Test
    fun `app-start healing does not depend on a bot session or BotService`() {
        // The zero-TP contract: the only unconditional startup call site is MainApplication (the Application
        // class), so opening the app is sufficient. The session-start log sweep in StartModule is a separate,
        // bot-run-gated path and must not be the sole home of telemetry healing.
        assertTrue(mainApplication.contains("class MainApplication : Application()"), "MainApplication is the Application class")
        assertTrue(
            mainApplication.contains("OutcomeCorpus.ensureExistingFilesReadable(this)"),
            "the Application onCreate owns the unconditional telemetry heal",
        )
    }

    // ---- helpers ----

    /** Returns the brace-balanced body of the top-level `object OutcomeCorpus` function whose signature is [signature]. */
    private fun functionBody(signature: String): String = functionBodyIn(outcomeCorpus, signature)

    private fun functionBodyIn(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "missing signature: $signature" }
        var depth = 0
        var i = source.indexOf('{', start)
        val bodyStart = i
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(bodyStart, i + 1)
                }
            }
            i++
        }
        throw IllegalStateException("unbalanced braces for: $signature")
    }

    private fun sourceFile(relative: String): File = File(kotlinRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
