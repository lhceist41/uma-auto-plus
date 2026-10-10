package com.steve1316.uma_android_automation.utils

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The zip behind the Home "Share bug report" button: the newest career logs, the current session log,
 * a short report.txt and the settings with every secret removed. Pure file and text work so it can be
 * tested without Android; the module glue in StartModule supplies the device lines and the settings.
 */
object BugReport {
    /** Discord's free upload limit is 10 MB as far as we know; stay under it. */
    const val MAX_BYTES = 8L * 1024 * 1024

    /** Three, not two: the library's session save and the per-career log share a name pattern, so the newest two can be one career twice. */
    const val KEPT_CAREER_LOGS = 3
    const val REMOVED = "[removed]"

    /** Per log. Career logs reach 15 MB and compress about 10:1, so this keeps the zip small and a log is never read whole. */
    const val MAX_LOG_CHARS = 3_000_000

    private const val MIN_SECRET_LENGTH = 4

    /** Every secret value replaced, wherever it shows up in the text. */
    fun redact(text: String, secrets: List<String>): String =
        secrets.filter { it.length >= MIN_SECRET_LENGTH }.fold(text) { acc, secret -> acc.replace(secret, REMOVED) }

    /** The newest [count] saved career logs. */
    fun newestCareerLogs(logsDir: File, count: Int = KEPT_CAREER_LOGS): List<File> =
        logsDir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }.orEmpty().sortedByDescending { it.lastModified() }.take(count)

    /** The lines of report.txt that describe the app and the device. */
    fun deviceLines(
        versionName: String,
        versionCode: Long,
        androidRelease: String,
        sdk: Int,
        manufacturer: String,
        model: String,
        screenWidth: Int,
        screenHeight: Int,
    ): List<String> =
        listOf(
            "App: UMA Auto+ $versionName ($versionCode)",
            "Android: $androidRelease (API $sdk)",
            "Device: $manufacturer $model",
            if (screenWidth > 0 && screenHeight > 0) "Screen the bot sees: ${screenWidth}x$screenHeight" else "Screen the bot sees: not captured yet (the bot has not been started)",
        )

    /** One log's lines, and what was left out before any size trimming ([cut], for report.txt). */
    private class Log(val name: String, val lines: List<String>, val cut: String?)

    /** The last [maxChars] of a file, read from the end and starting on a whole line; never the whole file. */
    private fun readTail(file: File, secrets: List<String>, maxChars: Int): Log {
        val size = file.length()
        val start = maxOf(0L, size - maxChars)
        val bytes =
            RandomAccessFile(file, "r").use { f ->
                f.seek(start)
                ByteArray((size - start).toInt()).also { f.readFully(it) }
            }
        var text = String(bytes, Charsets.UTF_8)
        if (start > 0) text = text.substringAfter('\n', "")
        val cut = if (start > 0) "Cut ${file.name}: it is $size bytes, only its last ${text.length} characters are included." else null
        return Log(file.name, redact(text, secrets).split("\n"), cut)
    }

    /** The last lines of the in-memory log that fit in [maxChars]; the buffer is never joined whole. */
    private fun readSessionTail(lines: List<String>, secrets: List<String>, maxChars: Int): Log {
        val kept = ArrayDeque<String>()
        var chars = 0
        for (i in lines.indices.reversed()) {
            if (chars + lines[i].length + 1 > maxChars) break
            kept.addFirst(redact(lines[i], secrets))
            chars += lines[i].length + 1
        }
        val cut = if (kept.size < lines.size) "Cut session-log.txt: only its last ${kept.size} of ${lines.size} lines are included." else null
        return Log("session-log.txt", kept.toList(), cut)
    }

    /**
     * Writes the zip to [out] and returns it. Every log is read from its end up to [maxLogChars]; if the zip is still
     * over [maxBytes], logs are cut again from their start (the same share for each) until it fits. report.txt names
     * every cut. Throws if even an empty-log zip does not fit.
     */
    fun build(
        out: File,
        reportLines: List<String>,
        settingsJson: String,
        careerLogs: List<File>,
        sessionLines: List<String>,
        secrets: List<String>,
        maxBytes: Long = MAX_BYTES,
        maxLogChars: Int = MAX_LOG_CHARS,
    ): File {
        val career = careerLogs.map { readTail(it, secrets, maxLogChars) }
        val session = readSessionTail(sessionLines, secrets, maxLogChars)
        // The saved career log is a slice of the session log, so list the session log only when it adds something.
        val addSession = session.lines.any { it.isNotEmpty() } && session.lines != career.firstOrNull()?.lines
        val logs = career + if (addSession) listOf(session) else emptyList()
        val settings = redact(settingsJson, secrets)
        val cuts = logs.mapNotNull { it.cut }

        var keep = logs.maxOfOrNull { it.lines.size } ?: 0
        while (true) {
            val kept = logs.map { Log(it.name, it.lines.takeLast(keep), it.cut) }
            val trims =
                kept.filterIndexed { i, log -> log.lines.size < logs[i].lines.size }.map { log ->
                    val total = logs.first { it.name == log.name }.lines.size
                    "Trimmed ${log.name}: kept the last ${log.lines.size} of $total lines to stay under the upload limit."
                }
            val bytes = zip(reportLines + cuts + trims, settings, kept, secrets)
            if (bytes.size <= maxBytes) {
                out.parentFile?.mkdirs()
                out.writeBytes(bytes)
                return out
            }
            check(keep > 0) { "The bug report does not fit in $maxBytes bytes even without logs." }
            // Shrink in proportion to how far over the zip is (with a margin), at least one line per round.
            keep = minOf(keep - 1, (keep * maxBytes / bytes.size * 9 / 10).toInt())
        }
    }

    private fun zip(report: List<String>, settings: String, logs: List<Log>, secrets: List<String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            val used = mutableSetOf<String>()

            fun add(name: String, text: String) {
                // A redacted name can collide with another one; a duplicate entry would throw.
                var entry = redact(name, secrets)
                if (!used.add(entry)) entry = "${used.size}-$entry".also { used.add(it) }
                zip.putNextEntry(ZipEntry(entry))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            add("report.txt", report.joinToString("\n") { redact(it, secrets) } + "\n")
            add("settings.json", settings)
            logs.forEach { add(it.name, it.lines.joinToString("\n")) }
        }
        return bytes.toByteArray()
    }
}
