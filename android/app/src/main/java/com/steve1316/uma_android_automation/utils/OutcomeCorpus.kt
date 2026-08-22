package com.steve1316.uma_android_automation.utils

import android.content.Context
import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.MessageLog
import org.json.JSONObject
import java.io.File

/**
 * Append-only JSONL corpus of per-career outcomes (Stage 3 of the outcome-measurement plan).
 *
 * One line per career end, written under the app's external files dir so the existing log pull
 * path covers it. Records carry the `[CAREER_END]` ledger fields plus the app version and a
 * config fingerprint so runs group into comparable arms offline (`scripts/analyze-outcomes.mjs`).
 * Not every started run produces a record: hard terminators (stall-watchdog killProcess,
 * pre-start failures) never reach the task-end path, so the corpus must never be read as
 * one-row-per-start.
 */
object OutcomeCorpus {
    private const val TAG: String = "${SharedData.loggerTag}OutcomeCorpus"

    /**
     * Directory (under the external files dir) that holds every telemetry JSONL file. Owned here so
     * the writer paths below and the startup self-heal share one definition of where the corpus
     * lives instead of repeating the literal in more than one place.
     */
    const val OUTCOMES_DIR = "outcomes"

    /** Relative path of the corpus file under the external files dir. */
    const val CORPUS_PATH = "$OUTCOMES_DIR/careers.jsonl"

    /**
     * Relative path of the per-turn decision-trace file. Kept out of [CORPUS_PATH] deliberately:
     * a career writes one outcome record but ~75 decision traces, so interleaving them would bury
     * the outcome rows the analyzer and the manual greps read.
     */
    const val DECISIONS_PATH = "$OUTCOMES_DIR/decisions.jsonl"

    /**
     * Relative path of the per-turn `career_state` file. A separate durable record type from
     * [DECISIONS_PATH] on purpose: `decision_trace` and `career_state` are joined offline by
     * `careerToken + seq`, and each parser rejects the other's records, so they must not interleave.
     */
    const val CAREER_STATE_PATH = "$OUTCOMES_DIR/career_state.jsonl"

    /**
     * Relative path of the per-turn Shadow Advisor S3 stream. A separate append-only record type joined offline to
     * [DECISIONS_PATH] by `careerToken + seq`: it carries only what the observational S1 policy would have
     * recommended from the same pre-decision facts, never the bot's committed action or outcome. Kept out of the
     * decision/state files so their parsers never see a foreign record, and it inherits the shared append-only,
     * public-readable, and startup-readability-sweep behavior of this object.
     */
    const val SHADOW_ADVISOR_PATH = "$OUTCOMES_DIR/shadow_advisor.jsonl"

    /**
     * Relative path of the passive lineage stream (`type:"lineage_selected"` records): one record
     * per career launch that read the populated Legacy Select summary, carrying the six ancestor
     * observations correlated by `launchTransactionId`. A separate record type joined offline to the
     * career outcome by that id; kept out of the other files so their parsers never see it.
     */
    const val LINEAGE_PATH = "$OUTCOMES_DIR/lineage.jsonl"

    /**
     * Relative path of the read-only Veteran roster scan stream: one `type:"roster_scan"` header
     * followed by its `type:"roster_entry"` rows per chevron walk of the roster. A separate record
     * type from every stream above and never joined by career token - a roster scan is a snapshot of
     * what the account owns right now, not an observation of a career - so it gets its own file and
     * the other parsers never see it.
     */
    const val ROSTER_SCAN_PATH = "$OUTCOMES_DIR/roster_scan.jsonl"

    /**
     * Relative path of the read-only Veteran Inspiration stream: one `type:"veteran_inspiration"`
     * record per Veteran whose Inspiration panel was read, plus one `type:"veteran_inspiration_scan"`
     * header per batch. Joined to the roster snapshot by `rosterFingerprint`, never by career token.
     *
     * Deliberately not [LINEAGE_PATH]. That file records what a career LAUNCH selected as its parents;
     * this one records what a REGISTERED Veteran carries. They describe different moments of the same
     * inheritance system and must stay separately readable, so this never overwrites or appends into
     * the historical lineage stream.
     */
    const val VETERAN_INSPIRATION_PATH = "$OUTCOMES_DIR/veteran_inspiration.jsonl"

    /**
     * Relative path of the read-only Veteran protection stream: one `type:"veteran_protection"`
     * record per filter-partition probe of the roster. It answers whether any Veteran is favorited
     * and whether any has a memo (the two markers that block a release), never mutating either.
     * Bound to a roster snapshot offline by `registeredUsed`, not by career token.
     */
    const val VETERAN_PROTECTION_PATH = "$OUTCOMES_DIR/veteran_protection.jsonl"

    /**
     * Appends one [record] as a JSON line to [path]. Writing must never disturb the calling
     * path: any failure is swallowed after a MessageLog warning. MessageLog is safe here -
     * the career path that logs the ledger line via MessageLog immediately after, and the
     * decision-trace path that has already executed its turn's action, are both ordinary
     * worker-thread contexts rather than watchdog/recovery ones - and it puts the data loss in
     * the pulled session log where an empty corpus can actually be diagnosed.
     *
     * [maxBytes], when set, stops appending once the file has grown past it. This is a ceiling on
     * disk use for the high-volume per-turn file, not rotation: nothing already written is
     * deleted, and the drop is reported once so a truncated corpus is never mistaken for a quiet
     * run. The career corpus passes null and is unaffected.
     */
    fun append(context: Context, record: JSONObject, path: String = CORPUS_PATH, maxBytes: Long? = null) {
        try {
            val file = File(context.getExternalFilesDir(null), path)
            if (maxBytes != null && file.length() >= maxBytes) {
                if (droppedPaths.add(path)) {
                    MessageLog.w(TAG, "[OUTCOME] $path reached its $maxBytes byte cap; further records this session are dropped.")
                }
                return
            }
            file.parentFile?.mkdirs()
            appendLineAndMakeReadable(file, record.toString() + "\n")
        } catch (e: Exception) {
            MessageLog.w(TAG, "[OUTCOME] Failed to append the outcome record: $e")
        }
    }

    /**
     * Appends [line] to [file] exactly as given, then best-effort marks the file world-readable.
     *
     * The readable-mode repair fixes a real device pathology: app-written files can materialize
     * `0600 u0_aXX:u0_aXX` on this emulator image, which locks the non-root adb shell out of the
     * telemetry pull (decisions.jsonl and career_state.jsonl were unpullable this way while
     * careers.jsonl happened to ride an older readable inode). Marking every appended file readable
     * mirrors the per-career log's existing `setReadable(true, false)` so the whole corpus stays
     * adb-pullable. The repair runs after the append and in its own catch, so a permission failure
     * can never discard the telemetry line that was already written. Split out so the byte-append
     * plus readable-mode contract can be pinned by a real-filesystem test without an Android Context.
     */
    internal fun appendLineAndMakeReadable(file: File, line: String) {
        file.appendText(line)
        try {
            file.setReadable(true, false)
        } catch (_: Exception) {
        }
    }

    /**
     * Best-effort, mode-only startup self-heal: marks every existing `.jsonl` file under the
     * `outcomes` dir world-readable so a non-root adb shell can pull a completed run's telemetry after simply
     * opening the app - no bot run and no TP spend. It opens no file for reading, rewrites no bytes,
     * and creates, renames, or deletes nothing, so already-written records stay byte-for-byte intact.
     * Failures are swallowed (per file and around the directory scan) so app startup can never be
     * aborted by it. Idempotent: re-running only re-applies the same mode.
     */
    fun ensureExistingFilesReadable(context: Context) {
        ensureJsonlReadable(File(context.getExternalFilesDir(null), OUTCOMES_DIR))
    }

    /**
     * Marks every regular `*.jsonl` file directly inside [outcomesDir] world-readable, best-effort.
     * Non-`.jsonl` entries and subdirectories are left untouched; a missing or empty directory is a
     * no-op. Split from [ensureExistingFilesReadable] so the file selection and mode-only behavior can
     * be tested against a real temp directory without an Android Context.
     */
    internal fun ensureJsonlReadable(outcomesDir: File) {
        try {
            outcomesDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".jsonl")) {
                    try {
                        file.setReadable(true, false)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    /** Paths already reported as capped, so the drop warns once per path per process. */
    private val droppedPaths: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf<String>())
}
