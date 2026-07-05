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
     * Appends one outcome [record] as a JSON line. Writing must never disturb the task-end
     * path: any failure is swallowed after a MessageLog warning. MessageLog is safe here -
     * this runs on the task-end path that logs the ledger line via MessageLog immediately
     * after, not in a watchdog/recovery context - and it puts the data loss in the pulled
     * session log where an empty corpus can actually be diagnosed.
     */
    fun append(context: Context, record: JSONObject) {
        try {
            val file = File(context.getExternalFilesDir(null), CORPUS_PATH)
            file.parentFile?.mkdirs()
            file.appendText(record.toString() + "\n")
        } catch (e: Exception) {
            MessageLog.w(TAG, "[OUTCOME] Failed to append the outcome record: $e")
        }
    }
}
