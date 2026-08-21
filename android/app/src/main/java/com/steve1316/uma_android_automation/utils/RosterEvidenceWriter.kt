package com.steve1316.uma_android_automation.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Writes the bounded field crops that explain an unresolved Veteran roster read.
 *
 * The 257-entry walk of 2026-08-21 left 26 entries with an unread stat and 68 with an unread outfit,
 * and kept no pixels at all - so the only way to learn WHY was to walk the whole roster again. This
 * closes that: for an entry's unresolved immutable fields only, the exact crop the reader scored is
 * persisted next to the scan's JSONL, named by scan, entry index, and field.
 *
 * Deliberately narrow:
 *  - failures only. A field that resolved writes nothing, so a clean full walk costs zero files;
 *  - capped at [MAX_CROPS_PER_SCAN] files per scan, because a pathological walk where every field
 *    fails would otherwise write thousands of PNGs to the device;
 *  - deterministic names, so a crop maps back to exactly one entry row in the corpus;
 *  - every failure is swallowed. A scan must never die because a debug PNG could not be written.
 *
 * The bitmap is compressed directly rather than going through OpenCV's `imwrite`, which writes the
 * RGBA buffer as BGR and produces red/blue-swapped PNGs (already paid for once by the Grand Concert
 * training captures). These crops exist to be looked at, so the colours have to be right.
 */
class RosterEvidenceWriter(context: Context, private val scanId: String) {
    private val dir = File(File(context.getExternalFilesDir(null), OutcomeCorpus.OUTCOMES_DIR), "$EVIDENCE_SUBDIR/$scanId")

    /** How many crops have actually been written, for the scan header and the summary line. */
    var cropCount: Int = 0
        private set

    private var capReported = false

    /**
     * Saves [box] out of [frame] as `<index>-<field>.png`, unless the per-scan cap is reached.
     * Returns true when a file was written. Never throws.
     */
    fun saveFieldCrop(frame: Bitmap, index: Int, field: String, box: GlyphBox): Boolean {
        if (cropCount >= MAX_CROPS_PER_SCAN) {
            if (!capReported) {
                capReported = true
                Log.w(TAG, "roster evidence cap of $MAX_CROPS_PER_SCAN crops reached for $scanId; later failures keep their raw OCR but no crop")
            }
            return false
        }
        return try {
            val x = box.x0.coerceIn(0, frame.width)
            val y = box.y0.coerceIn(0, frame.height)
            val w = (box.x1 - box.x0).coerceAtMost(frame.width - x)
            val h = (box.y1 - box.y0).coerceAtMost(frame.height - y)
            if (w <= 0 || h <= 0) return false
            dir.mkdirs()
            val file = File(dir, "$index-$field.png")
            FileOutputStream(file).use { out -> Bitmap.createBitmap(frame, x, y, w, h).compress(Bitmap.CompressFormat.PNG, 100, out) }
            // Same adb-pullability repair the JSONL writer does: app-written files can materialize
            // 0600 on this emulator image, which locks a non-root shell out of the evidence.
            try {
                file.setReadable(true, false)
            } catch (_: Exception) {
            }
            cropCount++
            true
        } catch (t: Throwable) {
            Log.w(TAG, "roster evidence crop $index-$field failed (non-fatal): ${t.message}")
            false
        }
    }

    companion object {
        private const val TAG = "RosterEvidenceWriter"

        /** Directory under the outcomes dir that holds one subdirectory per scan. */
        const val EVIDENCE_SUBDIR = "roster_evidence"

        /** Hard ceiling on crops written per scan. A full 260-entry walk with one bad field each
         * stays well inside it; a walk where everything fails stops filling the device. */
        const val MAX_CROPS_PER_SCAN = 600

        /**
         * The crop region for an unresolved immutable field name as it appears in
         * `unresolvedFields`, or null for a field with no single box worth keeping (the aptitude
         * grades are pixel-classified from ten tiny letter boxes and have never been the failure
         * class). The field names are the corpus's own, so a crop filename maps straight onto the
         * `unresolvedFields` entry that caused it.
         */
        fun boxForField(field: String): GlyphBox? =
            when {
                field == "character" || field == "outfit" ->
                    GlyphBox(
                        DETAIL_NAME_OUTFIT_X,
                        DETAIL_NAME_OUTFIT_Y,
                        DETAIL_NAME_OUTFIT_X + DETAIL_NAME_OUTFIT_W,
                        DETAIL_NAME_OUTFIT_Y + DETAIL_NAME_OUTFIT_H,
                    )
                field == "rank" -> RANK_MEDAL_BOX
                field == "rating" -> GlyphBox(DETAIL_RATING_X, DETAIL_RATING_Y, DETAIL_RATING_X + DETAIL_RATING_W, DETAIL_RATING_Y + DETAIL_RATING_H)
                field.startsWith("stat_") -> STAT_KEYS.indexOf(field.removePrefix("stat_")).takeIf { it >= 0 }?.let { STAT_VALUE_BOXES[it] }
                else -> null
            }
    }
}
