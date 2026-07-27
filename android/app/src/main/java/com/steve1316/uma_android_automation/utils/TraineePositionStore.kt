package com.steve1316.uma_android_automation.utils

import android.content.Context
import java.io.File

/**
 * Remembered Trainee Select grid positions, stored in a plain file OUTSIDE the settings
 * database.
 *
 * They lived in settings rows first (queueState.traineePos_*) and never survived to the next
 * queue: the session log proved the saves executed and committed (three 1.000-score matches on
 * 2026-07-11, zero write failures in logcat), yet every later read found nothing - each failed
 * read had an app relaunch between save and read, and the wipe mechanism resisted
 * identification (the RN batch save is upsert-only, the DB safeguard's restore path never
 * logged, the library queries per call with no cache). A flat file in filesDir sidesteps the
 * whole settings lifecycle: nothing else reads, rewrites, restores, or reseeds it.
 *
 * Format: one `name=page,col,row` line per trainee, names normalized to lowercase alphanumerics
 * (same normalization the navigator applies to its scan dedup). Stale entries are harmless -
 * the navigator verifies every jump by preview OCR before trusting it - so the file is never
 * pruned, only overwritten per-entry.
 */
object TraineePositionStore {
    private const val FILE_NAME = "trainee_positions.txt"

    /** One remembered grid cell. */
    data class Cell(val page: Int, val col: Int, val row: Int)

    /** Parses the file body: malformed lines are skipped, later duplicates win. */
    fun parse(body: String): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        for (line in body.lineSequence()) {
            val idx = line.indexOf('=')
            if (idx <= 0 || idx == line.length - 1) continue
            val name = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            if (name.isEmpty() || value.isEmpty()) continue
            map[name] = value
        }
        return map
    }

    /** Serializes entries sorted by name so the file diffs stably. */
    fun serialize(entries: Map<String, String>): String = entries.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }

    /**
     * Validates a stored "page,col,row" value against the caller's grid bounds. Returns null on
     * malformed values or out-of-range cells (e.g. after the grid constants change in an update).
     */
    fun parseCell(value: String?, maxPage: Int, colCount: Int, rowCount: Int): Cell? {
        if (value.isNullOrBlank()) return null
        val parts = value.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size != 3) return null
        val (page, col, row) = parts
        if (page !in 0..maxPage || col !in 0 until colCount || row !in 0 until rowCount) return null
        return Cell(page, col, row)
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Reads one trainee's stored position string, or null. Best-effort: any I/O problem reads as absent. */
    fun get(context: Context, normalizedName: String): String? =
        try {
            val f = file(context)
            if (f.exists()) parse(f.readText())[normalizedName] else null
        } catch (_: Exception) {
            null
        }

    /**
     * Stores one trainee's position. Best-effort and atomic: written to a temp file and renamed
     * over the original, so a mid-write kill can't corrupt existing entries.
     */
    fun put(context: Context, normalizedName: String, value: String): Boolean = putAll(context, mapOf(normalizedName to value))

    /**
     * Stores many positions in one atomic write. The scan path uses this to remember EVERY
     * trainee it read on the way to its target, not just the target itself, so the next queue's
     * different trainee gets a direct jump instead of a fresh scan. Same temp-file-and-rename
     * discipline as [put].
     */
    fun putAll(context: Context, positions: Map<String, String>): Boolean {
        if (positions.isEmpty()) return true
        return try {
            val f = file(context)
            val entries = if (f.exists()) parse(f.readText()) else mutableMapOf()
            entries.putAll(positions)
            val tmp = File(f.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(serialize(entries))
            if (!tmp.renameTo(f)) {
                // renameTo cannot replace an existing file on every filesystem; retry after a delete.
                f.delete()
                tmp.renameTo(f)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
