package com.steve1316.uma_android_automation

import android.database.Cursor
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The settings database has one owner: a single framework connection that JS reaches through the
 * SettingsDatabase module. Real SQLite behavior needs a device; these pin the pure decisions the
 * owner makes and the source constraints that keep it the only owner.
 */
@DisplayName("Settings database")
class SettingsDatabaseTest {
    @Nested
    @DisplayName("process ownership")
    inner class Ownership {
        @Test
        fun `only the app's main process owns the database`() {
            assertTrue(ownsSettingsDatabase("com.lhceist41.uma_auto_plus", "com.lhceist41.uma_auto_plus"))
        }

        @Test
        fun `the remote receiver process and an unknown process never do`() {
            assertFalse(ownsSettingsDatabase("com.lhceist41.uma_auto_plus:remote", "com.lhceist41.uma_auto_plus"))
            assertFalse(ownsSettingsDatabase(null, "com.lhceist41.uma_auto_plus"))
        }
    }

    @Nested
    @DisplayName("process-start recovery")
    inner class Recovery {
        private val everyBackup = BackupState.entries

        @Test
        fun `an intact, unmarked file is used as it is and the backup is not even checked`() {
            assertFalse(backupNeeded(LiveFile.OK, marked = false))
            for (backup in everyBackup) assertEquals(StartPlan(StartAction.USE, consumeMarker = false), processStartPlan(LiveFile.OK, false, backup))
        }

        @Test
        fun `a file that fails to open without evidence of damage keeps every file in place`() {
            for (marked in listOf(false, true)) {
                assertFalse(backupNeeded(LiveFile.OPEN_FAILED, marked), "no backup check, so nothing can move")
                for (backup in everyBackup) assertEquals(StartPlan(StartAction.KEEP, consumeMarker = false), processStartPlan(LiveFile.OPEN_FAILED, marked, backup), "$marked $backup")
            }
        }

        @Test
        fun `a damaged file is restored only from a backup proven good`() {
            for (marked in listOf(false, true)) {
                assertTrue(backupNeeded(LiveFile.CORRUPT, marked))
                assertEquals(StartPlan(StartAction.RESTORE_FROM_BACKUP, consumeMarker = true), processStartPlan(LiveFile.CORRUPT, marked, BackupState.VALID))
                for (backup in listOf(BackupState.INVALID, BackupState.UNREADABLE, BackupState.ABSENT)) {
                    assertEquals(StartPlan(StartAction.KEEP, consumeMarker = false), processStartPlan(LiveFile.CORRUPT, marked, backup), "$marked $backup")
                }
            }
        }

        @Test
        fun `a missing file is restored from a good backup and is never recreated while the backup could not be checked`() {
            assertTrue(backupNeeded(LiveFile.MISSING, marked = false))
            assertEquals(StartAction.RESTORE_FROM_BACKUP, processStartPlan(LiveFile.MISSING, false, BackupState.VALID).action)
            assertEquals(StartPlan(StartAction.KEEP, consumeMarker = false), processStartPlan(LiveFile.MISSING, false, BackupState.UNREADABLE))
            assertEquals(StartPlan(StartAction.FRESH, consumeMarker = true), processStartPlan(LiveFile.MISSING, false, BackupState.ABSENT), "a fresh install")
            assertEquals(StartPlan(StartAction.FRESH, consumeMarker = true), processStartPlan(LiveFile.MISSING, false, BackupState.INVALID))
        }

        @Test
        fun `the marker a damaged Start left restores a good backup at the next start, even over a file that now checks out`() {
            assertTrue(backupNeeded(LiveFile.OK, marked = true))
            assertEquals(StartPlan(StartAction.RESTORE_FROM_BACKUP, consumeMarker = true), processStartPlan(LiveFile.OK, true, BackupState.VALID))
            assertEquals(StartPlan(StartAction.KEEP, consumeMarker = false), processStartPlan(LiveFile.OK, true, BackupState.UNREADABLE), "retry at the next start")
            assertEquals(StartPlan(StartAction.USE, consumeMarker = true), processStartPlan(LiveFile.OK, true, BackupState.INVALID), "nothing better than the file")
            assertEquals(StartPlan(StartAction.USE, consumeMarker = true), processStartPlan(LiveFile.OK, true, BackupState.ABSENT))
        }

        @Test
        fun `the marker is spent only by a restore that produced a healthy file`() {
            val restore = StartPlan(StartAction.RESTORE_FROM_BACKUP, consumeMarker = true)
            assertTrue(markerConsumed(restore, SettingsDatabase.StartState.RESTORED))
            for (failed in listOf(SettingsDatabase.StartState.UNHEALTHY, SettingsDatabase.StartState.UNAVAILABLE)) {
                assertFalse(markerConsumed(restore, failed), "a failed restore keeps the marker for the next start")
            }
            assertTrue(markerConsumed(StartPlan(StartAction.USE, consumeMarker = true), SettingsDatabase.StartState.OPEN))
            assertFalse(markerConsumed(StartPlan(StartAction.KEEP, consumeMarker = false), SettingsDatabase.StartState.UNHEALTHY))
        }

        @Test
        fun `a backup is good only when it passes its check and holds settings`() {
            assertEquals(BackupState.VALID, backupState(Integrity.OK, hasSettingsTable = true, settingsRows = 312))
            assertEquals(BackupState.INVALID, backupState(Integrity.OK, hasSettingsTable = true, settingsRows = 0), "an empty backup would be a silent reset")
            assertEquals(BackupState.INVALID, backupState(Integrity.OK, hasSettingsTable = false, settingsRows = 0))
            assertEquals(BackupState.INVALID, backupState(Integrity.FAILED, hasSettingsTable = true, settingsRows = 312))
            assertEquals(BackupState.UNREADABLE, backupState(Integrity.ERROR, hasSettingsTable = true, settingsRows = 312), "a check that did not run proves nothing")
        }

        @Test
        fun `only a failed check or the framework's corruption error count as damage`() {
            assertEquals(Integrity.OK, classifyIntegrity("ok", null))
            assertEquals(Integrity.OK, classifyIntegrity("OK", null))
            assertEquals(Integrity.FAILED, classifyIntegrity("*** in database main ***\nPage 12: btreeInitPage() returns error code 11", null))
            assertEquals(Integrity.FAILED, classifyIntegrity(null, SQLiteDatabaseCorruptException("file is not a database")))
            assertEquals(Integrity.ERROR, classifyIntegrity(null, SQLiteFullException("database or disk is full")))
            assertEquals(Integrity.ERROR, classifyIntegrity(null, SQLiteDiskIOException("disk I/O error")))
            assertEquals(Integrity.ERROR, classifyIntegrity(null, null), "no answer is not a failure")
        }
    }

    @Nested
    @DisplayName("Start and the backup")
    inner class StartAndBackup {
        private val healthyStates = listOf(SettingsDatabase.StartState.OPEN, SettingsDatabase.StartState.RESTORED, SettingsDatabase.StartState.NOT_OPENED)

        @Test
        fun `a database left unhealthy at process start refuses Start without asking for another restore`() {
            for (state in listOf(SettingsDatabase.StartState.UNHEALTHY, SettingsDatabase.StartState.UNAVAILABLE)) {
                assertEquals(StartVerdict.REFUSE, startVerdict(state, null, null))
                assertEquals(StartVerdict.REFUSE, startVerdict(state, Integrity.OK, SettingsDatabase.BackupResult.REFRESHED))
            }
        }

        @Test
        fun `damage seen at Start refuses and asks the next process start to restore`() {
            for (state in healthyStates) {
                assertEquals(StartVerdict.REFUSE_AND_RESTORE_NEXT, startVerdict(state, Integrity.FAILED, null))
                assertEquals(StartVerdict.REFUSE_AND_RESTORE_NEXT, startVerdict(state, Integrity.OK, SettingsDatabase.BackupResult.REPLACED))
            }
        }

        @Test
        fun `an integrity check that could not run refuses, but is no evidence for a restore`() {
            for (state in healthyStates) assertEquals(StartVerdict.REFUSE, startVerdict(state, Integrity.ERROR, null))
        }

        @Test
        fun `a backup that was not refreshed for any other reason never stops Start`() {
            val others = SettingsDatabase.BackupResult.entries - SettingsDatabase.BackupResult.REPLACED
            for (state in healthyStates) for (backup in others) assertEquals(StartVerdict.PROCEED, startVerdict(state, Integrity.OK, backup), "$state $backup")
        }

        @Test
        fun `the path names the connection's file only while device and inode are unchanged`() {
            val owned = FileId(device = 64_770, inode = 131_077)
            assertEquals(Identity.SAME, fileIdentity(owned, FileId(64_770, 131_077), pathExists = true))
            assertEquals(Identity.REPLACED, fileIdentity(owned, FileId(64_770, 131_990), pathExists = true), "deleted and recreated")
            assertEquals(Identity.REPLACED, fileIdentity(owned, FileId(64_771, 131_077), pathExists = true))
            assertEquals(Identity.REPLACED, fileIdentity(owned, null, pathExists = false), "no file at the path at all")
            assertEquals(Identity.UNKNOWN, fileIdentity(null, FileId(64_770, 131_077), pathExists = true), "an identity not recorded proves nothing")
            assertEquals(Identity.UNKNOWN, fileIdentity(owned, null, pathExists = true))
        }

        @Test
        fun `a replaced file is reported before any other reason to skip`() {
            assertEquals(SettingsDatabase.BackupResult.REPLACED, backupRefreshDecision(Identity.REPLACED, walLength = 4_128, settingsRows = 0, skillsRows = 0))
        }

        @Test
        fun `every doubt skips the refresh and keeps the old backup`() {
            assertEquals(SettingsDatabase.BackupResult.SKIPPED_IDENTITY_UNKNOWN, backupRefreshDecision(Identity.UNKNOWN, 0, 312, 697))
            assertEquals(SettingsDatabase.BackupResult.SKIPPED_WAL_NOT_EMPTY, backupRefreshDecision(Identity.SAME, 4_128, 312, 697))
            assertEquals(SettingsDatabase.BackupResult.SKIPPED_NOT_SEEDED, backupRefreshDecision(Identity.SAME, 0, 0, 697), "never back up an empty settings table")
            assertEquals(SettingsDatabase.BackupResult.SKIPPED_NOT_SEEDED, backupRefreshDecision(Identity.SAME, 0, 312, 0))
            assertEquals(SettingsDatabase.BackupResult.REFRESHED, backupRefreshDecision(Identity.SAME, 0, 312, 697))
        }

        @Test
        fun `the backup is copied only from a fully checkpointed file`() {
            assertTrue(backupCopyAllowed(0L))
            assertFalse(backupCopyAllowed(1L))
            assertFalse(backupCopyAllowed(4_128L))
        }
    }

    @Nested
    @DisplayName("no false refusal on normal paths")
    inner class NoFalseRefusal {
        private fun startWith(state: SettingsDatabase.StartState, settingsRows: Long, skillsRows: Long, walLength: Long = 0) =
            startVerdict(state, Integrity.OK, backupRefreshDecision(Identity.SAME, walLength, settingsRows, skillsRows))

        @Test
        fun `a fresh install opens a new file and its first Start proceeds, seeded or not`() {
            assertEquals(StartAction.FRESH, processStartPlan(LiveFile.MISSING, false, BackupState.ABSENT).action)
            assertEquals(StartVerdict.PROCEED, startWith(SettingsDatabase.StartState.OPEN, settingsRows = 0, skillsRows = 0))
            assertEquals(StartVerdict.PROCEED, startWith(SettingsDatabase.StartState.OPEN, settingsRows = 312, skillsRows = 697))
        }

        @Test
        fun `the first Start of a player who never changed a setting proceeds and backs up the barrier's rows`() {
            // The Start barrier writes every launch setting before Start, and each launch reseeds skills.
            assertEquals(SettingsDatabase.BackupResult.REFRESHED, backupRefreshDecision(Identity.SAME, 0, 312, 697))
            assertEquals(StartVerdict.PROCEED, startWith(SettingsDatabase.StartState.OPEN, settingsRows = 312, skillsRows = 697))
        }

        @Test
        fun `the first Start after a restore proceeds`() {
            assertEquals(StartVerdict.PROCEED, startWith(SettingsDatabase.StartState.RESTORED, settingsRows = 312, skillsRows = 697))
        }

        @Test
        fun `an upgrade from a WAL file, a rollback file with an empty journal, or a replayed hot journal is used as it is`() {
            // All three open and pass the check, so they are an intact, unmarked file.
            assertEquals(StartAction.USE, processStartPlan(LiveFile.OK, false, BackupState.ABSENT).action)
            assertEquals(StartVerdict.PROCEED, startWith(SettingsDatabase.StartState.OPEN, settingsRows = 312, skillsRows = 697))
        }

        @Test
        fun `a Start while the bot or the app is writing proceeds, only skipping the refresh`() {
            assertEquals(StartVerdict.PROCEED, startWith(SettingsDatabase.StartState.OPEN, settingsRows = 312, skillsRows = 697, walLength = 8_272))
        }
    }

    @Nested
    @DisplayName("binding JS values")
    inner class Binding {
        @Test
        fun `null, booleans and strings bind as expo bound them`() {
            assertEquals(SqlArg.Null, sqlArgOf(null))
            assertEquals(SqlArg.Integer(1), sqlArgOf(true))
            assertEquals(SqlArg.Integer(0), sqlArgOf(false))
            assertEquals(SqlArg.Text("5"), sqlArgOf("5"), "a numeric-looking string stays text")
            assertEquals(SqlArg.Text(""), sqlArgOf(""))
        }

        @Test
        fun `an integral JS number binds as an integer, any other as a real`() {
            assertEquals(SqlArg.Integer(42), sqlArgOf(42.0))
            assertEquals(SqlArg.Integer(0), sqlArgOf(-0.0))
            assertEquals(SqlArg.Integer(-7), sqlArgOf(-7.0))
            assertEquals(SqlArg.Integer(9_007_199_254_740_992), sqlArgOf(9_007_199_254_740_992.0))
            assertEquals(SqlArg.Real(1.5), sqlArgOf(1.5))
            assertEquals(SqlArg.Real(1e300), sqlArgOf(1e300), "beyond the integer range stays real")
            assertTrue(sqlArgOf(Double.NaN) is SqlArg.Real)
            assertEquals(SqlArg.Integer(3), sqlArgOf(3))
            assertEquals(SqlArg.Integer(3), sqlArgOf(3L))
        }

        @Test
        fun `objects and arrays are refused, not stringified`() {
            assertThrows(IllegalArgumentException::class.java) { sqlArgOf(mapOf("a" to 1)) }
            assertThrows(IllegalArgumentException::class.java) { sqlArgOf(listOf(1)) }
        }
    }

    @Nested
    @DisplayName("one statement per string")
    inner class SingleStatement {
        @Test
        fun `a plain statement, with or without a trailing semicolon or comment, passes`() {
            assertEquals("SELECT 1", singleStatement("SELECT 1"))
            assertEquals("SELECT 1", singleStatement("  SELECT 1 ;  "))
            assertEquals("SELECT 1", singleStatement("SELECT 1; -- done\n"))
            assertEquals("SELECT 1", singleStatement("SELECT 1; /* done */ ;"))
        }

        @Test
        fun `two statements in one string are refused, since Android would drop the second`() {
            assertThrows(IllegalArgumentException::class.java) { singleStatement("DROP TABLE IF EXISTS skills; CREATE TABLE skills (id INTEGER)") }
            assertThrows(IllegalArgumentException::class.java) { singleStatement("SELECT 1; 'x'") }
            assertThrows(IllegalArgumentException::class.java) { singleStatement("SELECT 1;SELECT 2") }
        }

        @Test
        fun `semicolons inside strings, identifiers and comments do not split`() {
            assertEquals("SELECT 'a;b'", singleStatement("SELECT 'a;b'"))
            assertEquals("SELECT 'it''s; fine'", singleStatement("SELECT 'it''s; fine'"))
            assertEquals("SELECT \"odd;name\" FROM t", singleStatement("SELECT \"odd;name\" FROM t"))
            assertEquals("SELECT [x;y] FROM t", singleStatement("SELECT [x;y] FROM t"))
            assertEquals("SELECT 1 -- a;b\nFROM t", singleStatement("SELECT 1 -- a;b\nFROM t"))
            assertEquals("SELECT /* a;b */ 1", singleStatement("SELECT /* a;b */ 1"))
        }

        @Test
        fun `an empty statement is refused`() {
            assertThrows(IllegalArgumentException::class.java) { singleStatement("") }
            assertThrows(IllegalArgumentException::class.java) { singleStatement(" ; ") }
        }
    }

    @Nested
    @DisplayName("transaction payload")
    inner class Transaction {
        @Test
        fun `steps keep their order, rows and typed arguments`() {
            val steps =
                transactionStepsOf(
                    listOf(
                        mapOf("sql" to "DELETE FROM profiles", "rows" to listOf(emptyList<Any?>())),
                        mapOf("sql" to "INSERT INTO skills (id, inherited) VALUES (?, ?)", "rows" to listOf(listOf(10.0, true), listOf(11.0, false))),
                        mapOf("sql" to "INSERT INTO races (name) VALUES (?)", "rows" to emptyList<Any?>()),
                    ),
                )
            assertEquals(listOf("DELETE FROM profiles", "INSERT INTO skills (id, inherited) VALUES (?, ?)", "INSERT INTO races (name) VALUES (?)"), steps.map { it.sql })
            assertEquals(listOf(emptyList<SqlArg>()), steps[0].rows, "[[]] runs once without arguments")
            assertEquals(listOf(listOf(SqlArg.Integer(10), SqlArg.Integer(1)), listOf(SqlArg.Integer(11), SqlArg.Integer(0))), steps[1].rows)
            assertEquals(emptyList<List<SqlArg>>(), steps[2].rows, "[] runs zero times")
        }

        @Test
        fun `a malformed step fails the whole call before anything runs`() {
            assertThrows(IllegalArgumentException::class.java) { transactionStepsOf(listOf(mapOf("rows" to listOf(emptyList<Any?>())))) }
            assertThrows(IllegalArgumentException::class.java) { transactionStepsOf(listOf(mapOf("sql" to "DELETE FROM t"))) }
            assertThrows(IllegalArgumentException::class.java) { transactionStepsOf(listOf(mapOf("sql" to "DELETE FROM t", "rows" to listOf("not a row")))) }
            assertThrows(IllegalArgumentException::class.java) { transactionStepsOf(listOf("not a step")) }
            assertThrows(IllegalArgumentException::class.java) { transactionStepsOf(listOf(mapOf("sql" to "DELETE FROM a; DELETE FROM b", "rows" to listOf(emptyList<Any?>())))) }
        }
    }

    @Nested
    @DisplayName("result cells")
    inner class Cells {
        private fun cell(type: Int) = columnValue(type, { 7L }, { 2.5 }, { "text" })

        @Test
        fun `integers and reals come back as numbers, text as a string, NULL as null`() {
            assertEquals(7.0, cell(Cursor.FIELD_TYPE_INTEGER))
            assertEquals(2.5, cell(Cursor.FIELD_TYPE_FLOAT))
            assertEquals("text", cell(Cursor.FIELD_TYPE_STRING))
            assertNull(cell(Cursor.FIELD_TYPE_NULL))
        }

        @Test
        fun `a blob is refused`() {
            assertThrows(IllegalArgumentException::class.java) { cell(Cursor.FIELD_TYPE_BLOB) }
        }
    }

    @Nested
    @DisplayName("ownership wiring")
    inner class Wiring {
        private fun source(relative: String): String {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            repeat(8) {
                for (candidate in listOf(File(dir, relative), File(dir, "app/$relative"), File(dir, "android/app/$relative"))) {
                    if (candidate.isFile) return candidate.readText().replace("\r\n", "\n")
                }
                dir = dir?.parentFile
            }
            throw AssertionError("$relative not found")
        }

        private val main = "src/main/java/com/steve1316/uma_android_automation"
        private val owner by lazy { source("$main/SettingsDatabase.kt") }
        private val module by lazy { source("$main/SettingsDatabaseModule.kt") }

        @Test
        fun `the connection opens first in the application, before React Native loads`() {
            val app = source("$main/MainApplication.kt")
            val onCreate = app.substring(app.indexOf("override fun onCreate() {"))
            val open = onCreate.indexOf("SettingsDatabase.openAtProcessStart(this)")
            assertTrue(open in 0 until onCreate.indexOf("loadReactNative(this)"))
            assertTrue(open < onCreate.indexOf("OutcomeCorpus.ensureExistingFilesReadable(this)"))
            assertTrue(source("$main/StartPackage.java").contains("modules.add(new SettingsDatabaseModule(reactContext));"))
        }

        @Test
        fun `repair is refused outside the owning process`() {
            val start = owner.substring(owner.indexOf("fun openAtProcessStart("))
            val gate = start.indexOf("if (!ownsSettingsDatabase(currentProcessName(application), application.packageName)) return")
            assertTrue(gate in 0 until start.indexOf("processStartPlan("))
        }

        @Test
        fun `the connection is WAL with an error handler that keeps the file, and is never closed or versioned`() {
            assertTrue(owner.contains("SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING or (if (create) SQLiteDatabase.CREATE_IF_NECESSARY else 0)"))
            assertTrue(owner.contains("keepFileOnCorruption,"))
            assertFalse(Regex("user_version|\\.version\\s*=|setVersion\\(").containsMatchIn(owner + module), "the foundation helpers own the version")
            assertFalse(module.contains(".close()"), "the module never closes the shared connection")
            assertEquals(
                listOf("connection.close()", "first?.close()"),
                Regex("[\\w?]+\\.close\\(\\)").findAll(owner).map { it.value }.toList(),
                "only the damaged file's connection before it is set aside, and the backup check's own connection, are closed",
            )
        }

        @Test
        fun `only a fresh install creates a file, and a failed start open never lets get create one`() {
            assertEquals(1, Regex("create = true").findAll(owner).count())
            val fresh = owner.substring(owner.indexOf("StartAction.FRESH -> {"), owner.indexOf("}", owner.indexOf("StartAction.FRESH -> {")))
            assertTrue(fresh.contains("open(file, create = true)"))
            val get = owner.substring(owner.indexOf("fun get(context: Context): SQLiteDatabase {"), owner.indexOf("\n    }\n", owner.indexOf("fun get(context: Context): SQLiteDatabase {")))
            assertTrue(get.indexOf("check(startState != StartState.UNAVAILABLE)") in 0 until get.indexOf("open(file, create = false)"))
        }

        @Test
        fun `every connection the owner keeps records its file's identity`() {
            assertEquals(listOf("db = connection"), Regex("\\bdb = \\w+").findAll(owner).map { it.value }.toList(), "the shared connection is only ever set in adopt")
            val adopt = owner.substring(owner.indexOf("private fun adopt("), owner.indexOf("\n    }\n", owner.indexOf("private fun adopt(")))
            assertTrue(adopt.indexOf("ownedFile = fileIdOf(file)") in 0 until adopt.indexOf("db = connection"))
        }

        @Test
        fun `every copy is synced before it is renamed into place, and the marker is synced too`() {
            val copy = owner.substring(owner.indexOf("private fun copySynced("), owner.indexOf("\n    }\n", owner.indexOf("private fun copySynced(")))
            assertTrue(copy.indexOf("input.copyTo(output)") in 0 until copy.indexOf("output.fd.sync()"))
            assertEquals(setOf("copySynced(backup, temp)", "copySynced(file, temp)"), Regex("copySynced\\(\\w+, \\w+\\)").findAll(owner).map { it.value }.toSet())
            assertFalse(Regex("\\bFile\\([^)]*\\)\\.copyTo\\(|\\b(backup|file|temp)\\.copyTo\\(").containsMatchIn(owner), "no unsynced file copy")
            val mark = owner.substring(owner.indexOf("private fun markRestoreNeeded("), owner.indexOf("\n    }\n", owner.indexOf("private fun markRestoreNeeded(")))
            assertTrue(mark.contains("out.fd.sync()"))
        }

        @Test
        fun `a Start that sees damage leaves the marker the next process start reads`() {
            val checkAt = owner.indexOf("fun checkBeforeStart(context: Context): Boolean {")
            val check = owner.substring(checkAt, owner.indexOf("\n    }\n", checkAt))
            val branch = check.substring(check.indexOf("StartVerdict.REFUSE_AND_RESTORE_NEXT ->"))
            assertTrue(branch.contains("markRestoreNeeded(context)"))
            val start = owner.substring(owner.indexOf("fun openAtProcessStart("))
            assertTrue(start.indexOf("val marked = File(dir, RESTORE_MARKER).exists()") in 0 until start.indexOf("processStartPlan("))
            assertTrue(start.contains("if (markerConsumed(plan, startState)) File(dir, RESTORE_MARKER).delete()"))
        }

        @Test
        fun `a damaged file is renamed aside, never deleted`() {
            // Temp files and their companions and the consumed marker: nothing else. The backup is never deleted.
            val deletes = Regex("(File\\([^()]*\\)|\\w+)\\.delete\\(\\)").findAll(owner).map { it.groupValues[1] }.toSet()
            assertEquals(
                setOf("File(dir, name)", "File(dir, name + suffix)", "File(dir, RESTORE_TEMP + suffix)", "File(dir, RESTORE_TEMP)", "temp", "File(dir, RESTORE_MARKER)"),
                deletes,
            )
            assertFalse(Regex("\\bbackup\\.delete\\(\\)|File\\(dir, BACKUP\\)\\.delete\\(\\)").containsMatchIn(owner), "the backup is never deleted")
            assertEquals(setOf("BACKUP_TEMP", "RESTORE_TEMP"), Regex("deleteTemp\\(dir, (\\w+)\\)").findAll(owner).map { it.groupValues[1] }.toSet())
            assertEquals(
                Regex("val temp = File\\(dir, \\w+\\)").findAll(owner).count(),
                Regex("val temp = File\\(dir, (RESTORE_TEMP|BACKUP_TEMP)\\)").findAll(owner).count(),
                "temp only ever names a temp file",
            )
            val aside = owner.substring(owner.indexOf("private fun setAside("), owner.indexOf("\n    }\n", owner.indexOf("private fun setAside(")))
            assertTrue(aside.contains("if (!from.renameTo(to)) {") && aside.contains("putBack(moved)") && aside.contains("return null"), "a failed move puts everything back and stops the restore")
            val restore = owner.substring(owner.indexOf("private fun restoreFromCheckedCopy("))
            assertTrue(restore.indexOf("if (moved == null) {") in 0 until restore.indexOf("temp.renameTo(file)"), "no restore unless the damaged file moved aside")
        }

        @Test
        fun `every JS call runs on the module's own executor`() {
            val methods = Regex("@ReactMethod\\s+fun (\\w+)\\(").findAll(module).map { it.groupValues[1] }.toList()
            assertEquals(listOf("open", "exec", "run", "query", "transaction"), methods)
            for (name in methods) {
                val body = module.substring(module.indexOf("fun $name("), module.indexOf("@ReactMethod", module.indexOf("fun $name(")).let { if (it < 0) module.length else it })
                assertTrue(body.contains("= submit(promise)"), "$name must run on the executor")
            }
            assertTrue(module.contains("Executors.newSingleThreadExecutor"))
        }

        @Test
        fun `the backup refresh checkpoints, locks, decides, then copies to a synced temp file and renames it`() {
            val refresh = owner.substring(owner.indexOf("fun refreshBackup(context: Context)"), owner.indexOf("\n    }\n", owner.indexOf("fun refreshBackup(context: Context)")))
            val order =
                listOf(
                    "PRAGMA wal_checkpoint(TRUNCATE)",
                    "connection.beginTransaction()",
                    "backupRefreshDecision(",
                    "fileIdentity(ownedFile, fileIdOf(file), file.exists())",
                    "copySynced(file, temp)",
                    "temp.renameTo(backup)",
                    "connection.endTransaction()",
                ).map { it to refresh.indexOf(it) }
            for ((text, at) in order) assertTrue(at >= 0, "missing: $text")
            assertEquals(order.sortedBy { it.second }, order, "the steps must run in this order")
            assertTrue(refresh.substring(refresh.indexOf("} finally {")).contains("connection.endTransaction()"), "the lock is released even if the copy fails")
            assertFalse(refresh.contains("setTransactionSuccessful"), "the transaction only holds the lock; it changes nothing")
            assertTrue(refresh.contains("if (decision == BackupResult.REFRESHED) {"), "only a refresh decision copies")
        }

        @Test
        fun `Start refuses an unhealthy database before the foundation helper opens it, and never repairs`() {
            val start = source("$main/StartModule.kt")
            val method = start.substring(start.indexOf("fun start(launchId: String) {"))
            val check = method.indexOf("if (!SettingsDatabase.checkBeforeStart(context)) {")
            val refusal = method.substring(check, method.indexOf("}", check))
            assertTrue(check >= 0)
            assertTrue(refusal.contains("refuseStartForDatabase()") && refusal.contains("DebugTestGate.cancel()") && refusal.trimEnd().endsWith("return"))
            assertFalse(start.contains("SettingsDatabase.refreshBackup("), "the refresh runs inside the check, after integrity passed")
            assertTrue(check < method.indexOf("SettingsHelper.initialize(context)"), "the foundation helper's error handler deletes a corrupt file")
            assertTrue(check < method.indexOf("startProjection()"))
            assertFalse(start.contains("safeguardSettingsDatabase") || start.contains(".copyTo("), "no restore at Start")

            val refuse = start.substring(start.indexOf("private fun refuseStartForDatabase() {"), start.indexOf("\n    }\n", start.indexOf("private fun refuseStartForDatabase() {")))
            assertTrue(refuse.contains("QueueLedger.recordDatabaseRefusal(context, BuildConfig.VERSION_NAME)"))
            assertTrue(refuse.contains(".setMessage(DATABASE_UNHEALTHY_MESSAGE)"), "the player sees why")
            assertTrue(Regex("DATABASE_UNHEALTHY_MESSAGE =\\s+\"Not started, and nothing was spent").containsMatchIn(start))
        }
    }

    @Nested
    @DisplayName("one owner")
    inner class OneOwner {
        private val mainRoot: File by lazy {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            repeat(8) {
                for (candidate in listOf(File(dir, "src/main/java"), File(dir, "app/src/main/java"), File(dir, "android/app/src/main/java"))) {
                    if (File(candidate, "com/steve1316/uma_android_automation/SettingsDatabase.kt").isFile) return@lazy candidate
                }
                dir = dir?.parentFile
            }
            throw AssertionError("android/app/src/main/java not found")
        }

        private fun sourcesContaining(pattern: Regex): Map<String, Int> =
            mainRoot.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.mapNotNull { f ->
                val count = pattern.findAll(f.readText()).count()
                if (count > 0) f.name to count else null
            }.toMap()

        @Test
        fun `only SettingsDatabase opens a database connection of its own`() {
            assertEquals(mapOf("SettingsDatabase.kt" to 1), sourcesContaining(Regex("SQLiteDatabase\\.openDatabase\\(|openOrCreateDatabase\\(|SQLiteOpenHelper")))
        }

        @Test
        fun `the foundation settings helper is constructed only at its known sites`() {
            assertEquals(
                mapOf("Campaign.kt" to 1, "Racing.kt" to 3, "SkillDatabase.kt" to 1),
                sourcesContaining(Regex("SQLiteSettingsManager\\(")),
                "the foundation helper keeps its own connection; a new construction site needs a decision",
            )
        }
    }
}
