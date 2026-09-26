package com.steve1316.uma_android_automation

import android.app.Application
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteProgram
import android.os.Build
import android.system.Os
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * The single owner of `settings.db`: one framework connection, opened in the main process before
 * any service or React Native code and never closed. Two SQLite copies in one process cannot see
 * each other's locks, so a second copy (as expo-sqlite was) can flip the journal mode and unlink
 * the WAL under the other and split the settings into two views. Every app-owned reader and
 * writer goes through [get].
 *
 * Repair happens only here, at process start, while no other connection can exist, and only on
 * positive evidence: a failed integrity check, the framework's corruption error, a missing file,
 * or the marker Start leaves when it saw damage. The backup is proven good on a temp copy before
 * anything moves; the damaged file is renamed aside, never deleted. Any other failure keeps every
 * file where it is and Start refuses. [refreshBackup] only ever copies the connection's own file out.
 */
object SettingsDatabase {
    private const val TAG = "SettingsDatabase"
    const val DIR = "SQLite"
    const val FILE = "settings.db"
    const val BACKUP = "settings.db.bak"
    private const val BACKUP_TEMP = "settings.db.bak.tmp"
    private const val RESTORE_TEMP = "settings.db.restore.tmp"

    /** Left by a Start that saw damage, so the next process start restores the backup instead of trusting the file. */
    private const val RESTORE_MARKER = "settings.db.restore-needed"

    private val COMPANIONS = listOf("-wal", "-shm", "-journal")

    /** What happened at process start. Only [OPEN] and [RESTORED] leave a healthy database. */
    enum class StartState {
        NOT_OPENED,
        OPEN,
        RESTORED,
        UNHEALTHY,
        UNAVAILABLE,
    }

    @Volatile
    var startState: StartState = StartState.NOT_OPENED
        private set

    @Volatile
    private var db: SQLiteDatabase? = null

    /** The file the shared connection opened, so [refreshBackup] can tell if the path now names another. */
    @Volatile
    private var ownedFile: FileId? = null

    /** Corruption is reported by [integrityCheck] and acted on only at process start; the default handler would delete the file. */
    private val keepFileOnCorruption = DatabaseErrorHandler { Log.e(TAG, "SQLite reported corruption on the settings database.") }

    private fun dir(context: Context) = File(context.filesDir, DIR)

    /** The only open of a settings database file. [create] is true only for a fresh install at process start. */
    private fun open(file: File, create: Boolean): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING or (if (create) SQLiteDatabase.CREATE_IF_NECESSARY else 0),
            keepFileOnCorruption,
        )

    private fun integrityOf(connection: SQLiteDatabase): Integrity =
        try {
            classifyIntegrity(connection.rawQuery("PRAGMA integrity_check(1)", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }, null)
        } catch (e: Exception) {
            classifyIntegrity(null, e)
        }

    private fun fileIdOf(file: File): FileId? =
        try {
            Os.stat(file.absolutePath).let { FileId(it.st_dev, it.st_ino) }
        } catch (_: Exception) {
            null
        }

    private fun adopt(connection: SQLiteDatabase, file: File) {
        ownedFile = fileIdOf(file)
        db = connection
    }

    /** Copies [from] to [to] and syncs the copy's data to storage, so a power loss cannot leave it truncated behind a rename. */
    private fun copySynced(from: File, to: File) {
        FileInputStream(from).use { input ->
            FileOutputStream(to).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    private fun deleteTemp(dir: File, name: String) {
        File(dir, name).delete()
        for (suffix in COMPANIONS) File(dir, name + suffix).delete()
    }

    /**
     * Copies the backup to a synced temp file and proves that copy good: it opens, passes the
     * integrity check, and has a non-empty settings table. A good copy is left in place for the
     * restore; any other result removes it. Only the backup's own damage makes it [BackupState.INVALID].
     */
    private fun prepareRestoreCopy(dir: File): BackupState {
        val backup = File(dir, BACKUP)
        if (!backup.isFile) return BackupState.ABSENT
        val temp = File(dir, RESTORE_TEMP)
        val state =
            try {
                copySynced(backup, temp)
                val connection = open(temp, create = false)
                try {
                    val integrity = integrityOf(connection)
                    val hasTable = connection.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'settings'", null).use { it.moveToFirst() }
                    val rows = if (hasTable) connection.rawQuery("SELECT COUNT(*) FROM settings", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } else 0L
                    backupState(integrity, hasTable, rows)
                } finally {
                    connection.close()
                }
            } catch (e: SQLiteDatabaseCorruptException) {
                BackupState.INVALID
            } catch (e: Exception) {
                Log.e(TAG, "Could not check the settings backup: ${e.message}")
                BackupState.UNREADABLE
            }
        // The check's own WAL and shared-memory files belong to the temp copy, not to the database.
        for (suffix in COMPANIONS) File(dir, RESTORE_TEMP + suffix).delete()
        if (state != BackupState.VALID) File(dir, RESTORE_TEMP).delete()
        return state
    }

    /**
     * Opens the shared connection at process start, restoring the backup first when the file is
     * damaged, missing, or marked by a Start that saw damage. Main process only: the app's `:remote`
     * receiver process also runs `Application.onCreate`, and a repair there would rename the file
     * the main process has open. Never throws.
     */
    @Synchronized
    fun openAtProcessStart(application: Application) {
        if (db != null) return
        if (!ownsSettingsDatabase(currentProcessName(application), application.packageName)) return
        try {
            val dir = dir(application).apply { mkdirs() }
            deleteTemp(dir, BACKUP_TEMP)
            deleteTemp(dir, RESTORE_TEMP)
            val file = File(dir, FILE)
            val marked = File(dir, RESTORE_MARKER).exists()

            var first: SQLiteDatabase? = null
            val live =
                if (!file.exists()) {
                    LiveFile.MISSING
                } else {
                    try {
                        first = open(file, create = false)
                        when (integrityOf(first)) {
                            Integrity.OK -> LiveFile.OK
                            Integrity.FAILED -> LiveFile.CORRUPT
                            Integrity.ERROR -> LiveFile.OPEN_FAILED
                        }
                    } catch (e: SQLiteDatabaseCorruptException) {
                        LiveFile.CORRUPT
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not open the settings database at process start; keeping it as it is: ${e.message}")
                        LiveFile.OPEN_FAILED
                    }
                }
            val backup = if (backupNeeded(live, marked)) prepareRestoreCopy(dir) else BackupState.ABSENT
            val plan = processStartPlan(live, marked, backup)

            when (plan.action) {
                StartAction.USE -> {
                    adopt(first!!, file)
                    startState = StartState.OPEN
                }
                StartAction.FRESH -> {
                    // No file and nothing to restore: a fresh install. The app seeds a new database.
                    adopt(open(file, create = true), file)
                    startState = StartState.OPEN
                }
                StartAction.KEEP -> {
                    // Every file stays where it is; the connection, if any, still serves the app, and Start refuses.
                    first?.let { adopt(it, file) }
                    startState = if (first == null) StartState.UNAVAILABLE else StartState.UNHEALTHY
                    Log.e(TAG, "The settings database could not be verified or restored (${live.name}, backup ${backup.name}); every file is kept as it is.")
                }
                StartAction.RESTORE_FROM_BACKUP -> {
                    first?.close()
                    startState = restoreFromCheckedCopy(dir, file, live)
                }
            }
            if (markerConsumed(plan, startState)) File(dir, RESTORE_MARKER).delete()
            if (live == LiveFile.OK && marked && plan.action == StartAction.USE) {
                Log.w(TAG, "A Start saw damage, but no valid backup exists; keeping the current settings file.")
            }
        } catch (e: Exception) {
            if (db == null) startState = StartState.UNAVAILABLE
            Log.e(TAG, "Could not open the settings database at process start: ${e.message}")
        }
    }

    /**
     * Swaps the checked restore copy in for the live file. The live file and its companions are set
     * aside first; if that fails, nothing is replaced. Returns the resulting state.
     */
    private fun restoreFromCheckedCopy(dir: File, file: File, live: LiveFile): StartState {
        val temp = File(dir, RESTORE_TEMP)
        val aside = File(dir, "$FILE.corrupt-${System.currentTimeMillis()}")
        val moved = setAside(dir, aside)
        if (moved == null) {
            temp.delete()
            Log.e(TAG, "Could not set the damaged settings database aside; it was left in place and not restored.")
            return if (file.exists()) reopenUnhealthy(file) else StartState.UNAVAILABLE
        }
        if (!temp.renameTo(file)) {
            // Put the damaged files back rather than leave the path empty for something to recreate.
            putBack(moved)
            temp.delete()
            Log.e(TAG, "Could not move the restored settings backup into place.")
            return if (file.exists()) reopenUnhealthy(file) else StartState.UNAVAILABLE
        }
        val restored =
            try {
                open(file, create = false)
            } catch (e: Exception) {
                null
            }
        val state =
            when {
                restored == null -> StartState.UNAVAILABLE
                integrityOf(restored) == Integrity.OK -> StartState.RESTORED
                else -> StartState.UNHEALTHY
            }
        restored?.let { adopt(it, file) }
        Log.e(
            TAG,
            if (live == LiveFile.MISSING) {
                "The settings database was missing; restored the backup (${state.name})."
            } else {
                "The settings database was damaged; restored the backup (${state.name}). The damaged file was kept as ${aside.name}."
            },
        )
        return state
    }

    private fun reopenUnhealthy(file: File): StartState =
        try {
            adopt(open(file, create = false), file)
            StartState.UNHEALTHY
        } catch (_: Exception) {
            StartState.UNAVAILABLE
        }

    /**
     * Renames the live database's companions, then the database itself, to [aside] and returns what
     * moved. A failure puts back what moved and returns null, so a restore never lands next to a
     * stale WAL or over a file that is still in place. A missing live file only moves its companions.
     */
    private fun setAside(dir: File, aside: File): List<Pair<File, File>>? {
        val moved = mutableListOf<Pair<File, File>>()
        for (name in COMPANIONS.map { FILE + it } + FILE) {
            val from = File(dir, name)
            if (!from.exists()) continue
            val to = File(dir, aside.name + name.removePrefix(FILE))
            if (!from.renameTo(to)) {
                putBack(moved)
                return null
            }
            moved.add(from to to)
        }
        return moved
    }

    private fun putBack(moved: List<Pair<File, File>>) {
        moved.asReversed().forEach { (original, renamed) -> renamed.renameTo(original) }
    }

    /**
     * The shared connection. Outside the main process, or after a process-start open that failed, it
     * opens the existing file on first use, never creating one: a new file at the live path would
     * be seeded with defaults and later backed up over the player's settings.
     */
    fun get(context: Context): SQLiteDatabase {
        db?.let { return it }
        synchronized(this) {
            db?.let { return it }
            check(startState != StartState.UNAVAILABLE) { "The settings database could not be opened at app start." }
            val file = File(dir(context), FILE)
            return open(file, create = false).also { adopt(it, file) }
        }
    }

    /** The shared connection's integrity now. Never repairs. */
    fun integrityCheck(context: Context): Integrity =
        try {
            integrityOf(get(context))
        } catch (e: Exception) {
            classifyIntegrity(null, e)
        }

    enum class BackupResult { REFRESHED, SKIPPED_WAL_NOT_EMPTY, SKIPPED_NOT_SEEDED, SKIPPED_IDENTITY_UNKNOWN, REPLACED, FAILED }

    /**
     * Copies the connection's database to [BACKUP], never the other way round, and only when the
     * path still names the file the connection opened: the foundation's settings helper can delete
     * and recreate the path under the shared connection, and copying that replacement would put an
     * empty database over the good backup. The WAL is checkpointed, a transaction holds the write
     * lock, and the copy happens only with an empty WAL and seeded settings and skills tables. The
     * copy is synced to a temp file and renamed into place.
     */
    fun refreshBackup(context: Context): BackupResult =
        try {
            val connection = get(context)
            val dir = dir(context)
            val file = File(dir, FILE)
            connection.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            connection.beginTransaction()
            try {
                val wal = File(dir, "$FILE-wal")
                val decision =
                    backupRefreshDecision(
                        identity = fileIdentity(ownedFile, fileIdOf(file), file.exists()),
                        walLength = if (wal.exists()) wal.length() else 0L,
                        settingsRows = countRows(connection, "settings"),
                        skillsRows = countRows(connection, "skills"),
                    )
                if (decision == BackupResult.REFRESHED) {
                    val temp = File(dir, BACKUP_TEMP)
                    val backup = File(dir, BACKUP)
                    copySynced(file, temp)
                    // Rename replaces the old backup atomically; if it fails, the old backup stays and the temp file is cleaned at the next start.
                    check(temp.renameTo(backup)) { "could not move the new backup into place" }
                } else {
                    Log.w(TAG, "Settings backup not refreshed this time: ${decision.name}.")
                }
                decision
            } finally {
                connection.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh the settings backup: ${e.message}")
            BackupResult.FAILED
        }

    private fun countRows(connection: SQLiteDatabase, table: String): Long =
        try {
            connection.rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        } catch (_: Exception) {
            0L
        }

    /**
     * The checks a Start runs before the foundation's settings helper opens the file. Returns true
     * when Start may go on. A Start that sees damage (a failed integrity check, or the path no longer
     * naming the connection's file) leaves the marker, so the next process start restores the backup.
     */
    fun checkBeforeStart(context: Context): Boolean {
        val healthy = startState != StartState.UNHEALTHY && startState != StartState.UNAVAILABLE
        val integrity = if (healthy) integrityCheck(context) else null
        val backup = if (integrity == Integrity.OK) refreshBackup(context) else null
        return when (startVerdict(startState, integrity, backup)) {
            StartVerdict.PROCEED -> true
            StartVerdict.REFUSE -> false
            StartVerdict.REFUSE_AND_RESTORE_NEXT -> {
                markRestoreNeeded(context)
                false
            }
        }
    }

    private fun markRestoreNeeded(context: Context) {
        try {
            FileOutputStream(File(dir(context), RESTORE_MARKER)).use { out ->
                out.write("${System.currentTimeMillis()}\n".toByteArray())
                out.fd.sync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not record that the settings database needs a restore: ${e.message}")
        }
    }

    private fun currentProcessName(application: Application): String? =
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                Application.getProcessName()
            } else {
                File("/proc/self/cmdline").readText().trimEnd('\u0000').substringBefore('\u0000')
            }
        } catch (_: Exception) {
            null
        }
}

/** Whether this process owns the settings database: only the app's main process, whose name is the package name. */
internal fun ownsSettingsDatabase(processName: String?, packageName: String): Boolean = processName == packageName

/** An integrity check's answer: intact, damaged (a failed check or the framework's corruption error), or not run to an answer. */
enum class Integrity { OK, FAILED, ERROR }

internal fun classifyIntegrity(result: String?, error: Throwable?): Integrity =
    when {
        error is SQLiteDatabaseCorruptException -> Integrity.FAILED
        error != null -> Integrity.ERROR
        result == null -> Integrity.ERROR
        result.equals("ok", ignoreCase = true) -> Integrity.OK
        else -> Integrity.FAILED
    }

/** The live settings file at process start. Only [CORRUPT] and [MISSING] are evidence the file itself is bad. */
internal enum class LiveFile { OK, CORRUPT, OPEN_FAILED, MISSING }

/** The backup, proven on a temp copy: good, proven bad, not checkable right now, or not there. */
internal enum class BackupState { VALID, INVALID, UNREADABLE, ABSENT }

internal fun backupState(integrity: Integrity, hasSettingsTable: Boolean, settingsRows: Long): BackupState =
    when {
        integrity == Integrity.ERROR -> BackupState.UNREADABLE
        integrity == Integrity.FAILED || !hasSettingsTable || settingsRows <= 0 -> BackupState.INVALID
        else -> BackupState.VALID
    }

internal enum class StartAction { USE, FRESH, RESTORE_FROM_BACKUP, KEEP }

internal data class StartPlan(val action: StartAction, val consumeMarker: Boolean)

/** The backup is only checked when it might be used: the live file is bad, missing, or marked. */
internal fun backupNeeded(live: LiveFile, marked: Boolean): Boolean = live == LiveFile.CORRUPT || live == LiveFile.MISSING || (live == LiveFile.OK && marked)

/**
 * Process-start recovery. Files move only on positive evidence: a damaged or missing live file, or
 * the marker a Start left when it saw damage. An empty or partly seeded database is not evidence
 * (the app reseeds on every launch), so restoring it would silently roll the player's settings back.
 * A file that fails to open for any other reason (a full disk, a read-only mount, an I/O error)
 * stays exactly where it is, and so does everything else. The backup is used only once proven good.
 */
internal fun processStartPlan(live: LiveFile, marked: Boolean, backup: BackupState): StartPlan =
    when (live) {
        LiveFile.OPEN_FAILED -> StartPlan(StartAction.KEEP, consumeMarker = false)
        LiveFile.OK ->
            when {
                !marked -> StartPlan(StartAction.USE, consumeMarker = false)
                backup == BackupState.VALID -> StartPlan(StartAction.RESTORE_FROM_BACKUP, consumeMarker = true)
                // Could not check the backup now: keep the marker and try again at the next start.
                backup == BackupState.UNREADABLE -> StartPlan(StartAction.KEEP, consumeMarker = false)
                // Nothing better exists than the file in place.
                else -> StartPlan(StartAction.USE, consumeMarker = true)
            }
        LiveFile.CORRUPT ->
            if (backup == BackupState.VALID) {
                StartPlan(StartAction.RESTORE_FROM_BACKUP, consumeMarker = true)
            } else {
                StartPlan(StartAction.KEEP, consumeMarker = false)
            }
        LiveFile.MISSING ->
            when (backup) {
                BackupState.VALID -> StartPlan(StartAction.RESTORE_FROM_BACKUP, consumeMarker = true)
                BackupState.UNREADABLE -> StartPlan(StartAction.KEEP, consumeMarker = false)
                // No backup, or one proven damaged: nothing to keep, so a fresh database.
                else -> StartPlan(StartAction.FRESH, consumeMarker = true)
            }
    }

/**
 * Whether the marker is spent. A restore that did not produce a healthy file keeps it, so the next
 * start tries again instead of trusting the file the marker was left for.
 */
internal fun markerConsumed(plan: StartPlan, result: SettingsDatabase.StartState): Boolean =
    plan.consumeMarker && (plan.action != StartAction.RESTORE_FROM_BACKUP || result == SettingsDatabase.StartState.RESTORED)

/** A file's identity on disk: the device and inode, which a delete-and-recreate changes. */
internal data class FileId(val device: Long, val inode: Long)

internal enum class Identity { SAME, REPLACED, UNKNOWN }

/**
 * Whether the path still names the file the connection opened. A different inode, or no file at the
 * path at all, is positive evidence of replacement; an identity that could not be read proves nothing.
 */
internal fun fileIdentity(owned: FileId?, current: FileId?, pathExists: Boolean): Identity =
    when {
        !pathExists -> Identity.REPLACED
        owned == null || current == null -> Identity.UNKNOWN
        owned == current -> Identity.SAME
        else -> Identity.REPLACED
    }

/**
 * Whether the backup may be refreshed. A replaced file is reported first, since it must stop Start
 * whatever else holds. Otherwise every doubt skips the refresh and leaves the old backup in place.
 */
internal fun backupRefreshDecision(identity: Identity, walLength: Long, settingsRows: Long, skillsRows: Long): SettingsDatabase.BackupResult =
    when {
        identity == Identity.REPLACED -> SettingsDatabase.BackupResult.REPLACED
        identity == Identity.UNKNOWN -> SettingsDatabase.BackupResult.SKIPPED_IDENTITY_UNKNOWN
        !backupCopyAllowed(walLength) -> SettingsDatabase.BackupResult.SKIPPED_WAL_NOT_EMPTY
        settingsRows <= 0 || skillsRows <= 0 -> SettingsDatabase.BackupResult.SKIPPED_NOT_SEEDED
        else -> SettingsDatabase.BackupResult.REFRESHED
    }

/** The backup is copied only from a checkpointed file: a WAL with anything in it means the main file alone is not the whole database. */
internal fun backupCopyAllowed(walLength: Long): Boolean = walLength == 0L

internal enum class StartVerdict { PROCEED, REFUSE, REFUSE_AND_RESTORE_NEXT }

/**
 * Whether a Start may go on. The process-start state refuses as it is (repair already ran). Damage
 * seen now refuses and asks the next process start to restore. An integrity check that could not
 * run refuses without that request: it is no evidence the file is bad. A backup that could not be
 * refreshed for any other reason never stops Start.
 */
internal fun startVerdict(startState: SettingsDatabase.StartState, integrity: Integrity?, backup: SettingsDatabase.BackupResult?): StartVerdict =
    when {
        startState == SettingsDatabase.StartState.UNHEALTHY || startState == SettingsDatabase.StartState.UNAVAILABLE -> StartVerdict.REFUSE
        integrity == Integrity.FAILED -> StartVerdict.REFUSE_AND_RESTORE_NEXT
        integrity != Integrity.OK -> StartVerdict.REFUSE
        backup == SettingsDatabase.BackupResult.REPLACED -> StartVerdict.REFUSE_AND_RESTORE_NEXT
        else -> StartVerdict.PROCEED
    }

/** One bound SQL argument, typed the way expo-sqlite binds a JS value. */
internal sealed interface SqlArg {
    data object Null : SqlArg

    data class Integer(val value: Long) : SqlArg

    data class Real(val value: Double) : SqlArg

    data class Text(val value: String) : SqlArg
}

/**
 * A JS value as a SQL argument: null is NULL, a boolean is 1 or 0, an integral number is an
 * integer, any other number a real, a string text. React Native hands every JS number over as a
 * Double. Anything else (an object or array) is refused rather than stringified.
 */
internal fun sqlArgOf(value: Any?): SqlArg =
    when (value) {
        null -> SqlArg.Null
        is Boolean -> SqlArg.Integer(if (value) 1L else 0L)
        is String -> SqlArg.Text(value)
        is Int -> SqlArg.Integer(value.toLong())
        is Long -> SqlArg.Integer(value)
        is Number -> {
            val d = value.toDouble()
            if (d.isFinite() && d == Math.floor(d) && d >= Long.MIN_VALUE.toDouble() && d < Long.MAX_VALUE.toDouble()) SqlArg.Integer(d.toLong()) else SqlArg.Real(d)
        }
        else -> throw IllegalArgumentException("Unsupported SQL argument type: ${value::class.java.simpleName}")
    }

internal fun sqlArgsOf(values: List<Any?>): List<SqlArg> = values.map(::sqlArgOf)

/** Binds [args] to a compiled statement or query, 1-based. */
internal fun SQLiteProgram.bindAll(args: List<SqlArg>) {
    args.forEachIndexed { i, arg ->
        when (arg) {
            SqlArg.Null -> bindNull(i + 1)
            is SqlArg.Integer -> bindLong(i + 1, arg.value)
            is SqlArg.Real -> bindDouble(i + 1, arg.value)
            is SqlArg.Text -> bindString(i + 1, arg.value)
        }
    }
}

/**
 * Returns [sql] as exactly one statement, or throws. Android runs only the first statement of a
 * string and silently drops the rest, so a multi-statement string must never reach it. Semicolons
 * inside quoted strings, quoted identifiers and comments do not count; trailing ones are allowed.
 */
internal fun singleStatement(sql: String): String {
    var i = 0
    var end = -1
    while (i < sql.length) {
        val c = sql[i]
        val startsComment = (c == '-' || c == '/') && i + 1 < sql.length && sql[i + 1] == (if (c == '-') '-' else '*')
        if (end >= 0 && !c.isWhitespace() && c != ';' && !startsComment) {
            throw IllegalArgumentException("More than one SQL statement in one string; pass each statement separately.")
        }
        when {
            c == '\'' || c == '"' || c == '`' -> {
                i++
                while (i < sql.length) {
                    if (sql[i] == c) {
                        if (i + 1 < sql.length && sql[i + 1] == c) i += 2 else break
                    } else {
                        i++
                    }
                }
            }
            c == '[' -> while (i < sql.length && sql[i] != ']') i++
            c == '-' && i + 1 < sql.length && sql[i + 1] == '-' -> while (i < sql.length && sql[i] != '\n') i++
            c == '/' && i + 1 < sql.length && sql[i + 1] == '*' -> {
                i += 2
                while (i + 1 < sql.length && !(sql[i] == '*' && sql[i + 1] == '/')) i++
                i++
            }
            c == ';' -> if (end < 0) end = i
        }
        i++
    }
    val statement = (if (end >= 0) sql.substring(0, end) else sql).trim()
    require(statement.isNotEmpty()) { "Empty SQL statement." }
    return statement
}

/**
 * A result cell as the JS value expo-sqlite returned: INTEGER and REAL as a number (JS has only
 * doubles), TEXT as a string, NULL as null. The settings database holds no BLOBs; one is refused.
 */
internal fun columnValue(cursorType: Int, readLong: () -> Long, readDouble: () -> Double, readString: () -> String): Any? =
    when (cursorType) {
        android.database.Cursor.FIELD_TYPE_NULL -> null
        android.database.Cursor.FIELD_TYPE_INTEGER -> readLong().toDouble()
        android.database.Cursor.FIELD_TYPE_FLOAT -> readDouble()
        android.database.Cursor.FIELD_TYPE_STRING -> readString()
        else -> throw IllegalArgumentException("Unsupported column type $cursorType in a settings query.")
    }

/** One step of a transaction: one statement run once per row of arguments. */
internal data class TransactionStep(val sql: String, val rows: List<List<SqlArg>>)

/**
 * Reads the JS `transaction` payload, `[{ sql, rows: [[...], ...] }, ...]`, into checked steps.
 * A step with no rows runs zero times; a step that must run once without arguments passes `[[]]`.
 */
internal fun transactionStepsOf(steps: List<Any?>): List<TransactionStep> =
    steps.mapIndexed { index, raw ->
        val step = raw as? Map<*, *> ?: throw IllegalArgumentException("Transaction step $index is not an object.")
        val sql = step["sql"] as? String ?: throw IllegalArgumentException("Transaction step $index has no sql string.")
        val rows = step["rows"] as? List<*> ?: throw IllegalArgumentException("Transaction step $index has no rows array.")
        TransactionStep(
            singleStatement(sql),
            rows.mapIndexed { r, row -> sqlArgsOf(row as? List<Any?> ?: throw IllegalArgumentException("Transaction step $index row $r is not an array.")) },
        )
    }
