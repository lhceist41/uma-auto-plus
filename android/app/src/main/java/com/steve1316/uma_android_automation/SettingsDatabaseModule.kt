package com.steve1316.uma_android_automation

import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The app's JS access to [SettingsDatabase]'s shared connection. Every call runs on one dedicated
 * thread, never React Native's own module thread, so calls keep their order, a long batch never
 * blocks StartModule's methods, and each transaction begins, runs and ends on the same thread
 * (Android binds a transaction to its thread). Errors reject with the SQLite message unchanged.
 */
class SettingsDatabaseModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    companion object {
        /** One per process, shared by every module instance, so a JS reload cannot reorder or overlap calls. */
        private val executor: ExecutorService =
            Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "SettingsDatabase").apply { isDaemon = true } }
    }

    override fun getName(): String = "SettingsDatabase"

    private fun submit(
        promise: Promise,
        work: (SQLiteDatabase) -> Any?,
    ) {
        executor.execute {
            try {
                promise.resolve(work(SettingsDatabase.get(reactContext)))
            } catch (e: Throwable) {
                promise.reject("SQLITE_ERROR", e.message ?: e.toString(), e)
            }
        }
    }

    private inline fun <T> SQLiteDatabase.inTransaction(block: () -> T): T {
        beginTransaction()
        try {
            return block().also { setTransactionSuccessful() }
        } finally {
            endTransaction()
        }
    }

    /** Resolves once the shared connection is open. Idempotent. */
    @ReactMethod
    fun open(promise: Promise) = submit(promise) { null }

    /** Runs each statement (one per element; DDL) in one transaction. */
    @ReactMethod
    fun exec(
        statements: ReadableArray,
        promise: Promise,
    ) = submit(promise) { db ->
        val sql = statements.toArrayList().mapIndexed { i, s -> singleStatement(s as? String ?: throw IllegalArgumentException("Statement $i is not a string.")) }
        db.inTransaction { sql.forEach(db::execSQL) }
        null
    }

    /** Runs one statement and reports `{ changes, lastInsertRowId }`, read on the same connection inside one transaction. */
    @ReactMethod
    fun run(
        sql: String,
        params: ReadableArray,
        promise: Promise,
    ) = submit(promise) { db ->
        val args = sqlArgsOf(params.toArrayList())
        val (changes, rowId) =
            db.inTransaction {
                db.compileStatement(singleStatement(sql)).use { statement ->
                    statement.bindAll(args)
                    val changed = statement.executeUpdateDelete()
                    changed to db.compileStatement("SELECT last_insert_rowid()").use { it.simpleQueryForLong() }
                }
            }
        Arguments.createMap().apply {
            putDouble("changes", changes.toDouble())
            putDouble("lastInsertRowId", rowId.toDouble())
        }
    }

    /** Runs one query with typed arguments and returns its rows as objects keyed by column name. */
    @ReactMethod
    fun query(
        sql: String,
        params: ReadableArray,
        promise: Promise,
    ) = submit(promise) { db ->
        val args = sqlArgsOf(params.toArrayList())
        val rows = Arguments.createArray()
        db.rawQueryWithFactory({ _, driver, editTable, query ->
            query.bindAll(args)
            SQLiteCursor(driver, editTable, query)
        }, singleStatement(sql), null, "").use { cursor ->
            val names = cursor.columnNames
            while (cursor.moveToNext()) {
                val row = Arguments.createMap()
                names.forEachIndexed { i, name ->
                    when (val value = columnValue(cursor.getType(i), { cursor.getLong(i) }, { cursor.getDouble(i) }, { cursor.getString(i) })) {
                        null -> row.putNull(name)
                        is Double -> row.putDouble(name, value)
                        else -> row.putString(name, value as String)
                    }
                }
                rows.pushMap(row)
            }
        }
        rows
    }

    /** Runs `[{ sql, rows }]` all or nothing: each statement compiled once and run once per row. */
    @ReactMethod
    fun transaction(
        steps: ReadableArray,
        promise: Promise,
    ) = submit(promise) { db ->
        val checked = transactionStepsOf(steps.toArrayList())
        db.inTransaction {
            for (step in checked) {
                db.compileStatement(step.sql).use { statement ->
                    for (row in step.rows) {
                        statement.clearBindings()
                        statement.bindAll(row)
                        statement.executeUpdateDelete()
                    }
                }
            }
        }
        null
    }
}
