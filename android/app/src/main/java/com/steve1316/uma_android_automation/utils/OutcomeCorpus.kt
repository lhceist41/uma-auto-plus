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

    /** Relative path of the corpus file under the external files dir. */
    const val CORPUS_PATH = "outcomes/careers.jsonl"

    /**
     * Relative path of the per-turn decision-trace file. Kept out of [CORPUS_PATH] deliberately:
     * a career writes one outcome record but ~75 decision traces, so interleaving them would bury
     * the outcome rows the analyzer and the manual greps read.
     */
    const val DECISIONS_PATH = "outcomes/decisions.jsonl"

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
            file.appendText(record.toString() + "\n")
        } catch (e: Exception) {
            MessageLog.w(TAG, "[OUTCOME] Failed to append the outcome record: $e")
        }
    }

    /** Paths already reported as capped, so the drop warns once per path per process. */
    private val droppedPaths: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf<String>())
}
