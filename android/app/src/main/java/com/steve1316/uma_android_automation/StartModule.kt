package com.steve1316.uma_android_automation

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.net.toUri
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.steve1316.automation_library.events.ExceptionEvent
import com.steve1316.automation_library.events.JSEvent
import com.steve1316.automation_library.events.StartEvent
import com.steve1316.automation_library.utils.BatteryOptimizationUtils
import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.MediaProjectionService
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.MyAccessibilityService
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode
import com.steve1316.uma_android_automation.utils.LogStreamServer
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import kotlinx.coroutines.runBlocking
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.SubscriberExceptionEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Takes care of setting up internal processes such as the Accessibility and MediaProjection services, receiving and sending messages over to the Javascript frontend, and handle tests involving
 * Discord and Twitter API integrations if needed.
 *
 * Loaded into the React PackageList via MainApplication's instantiation of the StartPackage.
 */
class StartModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), ActivityEventListener {
    companion object {
        private val TAG = "[${MainActivity.loggerTag}]StartModule"
        private var reactContext: ReactApplicationContext? = null
        private var emitter: DeviceEventManagerModule.RCTDeviceEventEmitter? = null

        /** When true, the entire queue should stop after the current run. */
        @Volatile
        var queueStopRequested: Boolean = false

        /**
         * Human-readable reason for an internal/deliberate queue stop (e.g. the trainee-mismatch guard),
         * or null when the stop is a genuine user Stop. Lets the result and queue logs say WHY the queue
         * stopped instead of always blaming the user (a trainee-mismatch guard stop reported as "manually
         * stopped by the user" masks the real cause).
         */
        @Volatile
        var queueStopReason: String? = null

        /** When true, the current run should be skipped and the queue should advance. */
        @Volatile
        var queueSkipRequested: Boolean = false

        /**
         * Wall-clock budget for one between-run navigation. Normal navigation (career summary
         * through deck setup to the training menu, cinematic included) takes 2-5 minutes; a
         * navigate() call that hasn't returned by this deadline is wedged below the FSM loop,
         * where its own per-iteration bail-outs can never fire.
         */
        private const val NAV_DEADLINE_MS: Long = 10 * 60 * 1000L

        /** How long after the deadline interrupt to wait before escalating to a queue stop. */
        private const val NAV_INTERRUPT_GRACE_MS: Long = 60 * 1000L

        /**
         * Persists the current queue state to SQLite so it can survive app crashes.
         * Writes directly to the settings database using INSERT OR REPLACE.
         */
        fun saveQueueState(context: Context, active: Boolean, currentRun: Int = 0, totalRuns: Int = 0, phase: String = PHASE_CAREER) {
            try {
                val dbFile = File(context.filesDir, "SQLite/settings.db")
                if (!dbFile.exists()) return
                val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                db.execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("queueState", "active", active.toString()),
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("queueState", "currentRun", currentRun.toString()),
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("queueState", "totalRuns", totalRuns.toString()),
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("queueState", "phase", phase),
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("queueState", "timestamp", System.currentTimeMillis().toString()),
                )
                db.close()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save queue state: ${e.message}")
            }
        }

        /**
         * Clears the persisted queue state (called when queue finishes normally).
         */
        fun clearQueueState(context: Context) {
            saveQueueState(context, active = false)
        }

        /**
         * Snapshot of an interrupted queue, loaded from SQLite.
         *
         * @property active True if the queue was in progress (matches the `active` column).
         * @property currentRun 1-indexed run number that was in flight when the process died.
         * @property totalRuns Total runs the user requested when the queue started.
         * @property ageMs Milliseconds between when the state was persisted and now.
         * @property phase What was in flight: [PHASE_CAREER] (playing `currentRun`) or
         *           [PHASE_LAUNCHING] (`currentRun` done, launching `currentRun + 1`).
         */
        data class QueueState(
            val active: Boolean,
            val currentRun: Int,
            val totalRuns: Int,
            val ageMs: Long,
            val phase: String,
        )

        /** Stale queue state older than this is ignored. 6 hours matches the UI-side check. */
        private const val QUEUE_STATE_STALE_MS: Long = 6 * 60 * 60 * 1000L

        /**
         * Queue phase persisted next to the run number so a rotation resume can tell what the
         * in-flight work actually was. CAREER = playing `currentRun`'s career; LAUNCHING =
         * `currentRun`'s career finished and the launch of `currentRun + 1` was in progress.
         *
         * Without this, a rotation queue killed mid-career resumes at `currentRun + 1` (the
         * single-trainee default) and finishes the running career under the NEXT trainee's preset.
         * The phase lets the resume re-enter the interrupted career under its own trainee instead.
         */
        const val PHASE_CAREER = "career"
        const val PHASE_LAUNCHING = "launching"

        /**
         * Load the persisted queue state, if any, and return it only if it represents a
         * genuinely resumable session (active, recent, with sensible run numbers). Returns
         * null in all the cases where we shouldn't auto-resume: no state, explicitly cleared,
         * stale, or malformed.
         *
         * Used on bot-session entry (`onStartEvent`) to detect and resume a queue that was
         * interrupted by a SIGKILL / TRIM_EMPTY in a previous process lifetime.
         */
        fun loadQueueState(context: Context): QueueState? {
            try {
                val dbFile = File(context.filesDir, "SQLite/settings.db")
                if (!dbFile.exists()) return null
                val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                val raw = mutableMapOf<String, String>()
                try {
                    db.rawQuery(
                        "SELECT key, value FROM settings WHERE category = ?",
                        arrayOf("queueState"),
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            raw[cursor.getString(0)] = cursor.getString(1)
                        }
                    }
                } finally {
                    db.close()
                }
                val active = raw["active"] == "true"
                if (!active) return null

                val currentRun = raw["currentRun"]?.toIntOrNull() ?: return null
                val totalRuns = raw["totalRuns"]?.toIntOrNull() ?: return null
                if (currentRun <= 0 || totalRuns <= 0) return null

                val timestamp = raw["timestamp"]?.toLongOrNull() ?: 0L
                val ageMs = System.currentTimeMillis() - timestamp
                if (ageMs < 0 || ageMs > QUEUE_STATE_STALE_MS) return null

                // Pre-phase states (and the conservative default) read as CAREER: re-enter rather
                // than skip. A redundant re-run is harmless; skipping the wrong way is the bug.
                val phase = raw["phase"] ?: PHASE_CAREER

                return QueueState(active, currentRun, totalRuns, ageMs, phase)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load queue state: ${e.message}")
                return null
            }
        }

        /**
         * Trainee-rotation config for a queue session, parsed from the runQueue settings.
         *
         * @property enabled True only when rotation is on AND at least one trainee is configured.
         * @property switchEvery Number of consecutive runs each trainee plays before the next.
         * @property inGameNames Ordered "[Outfit] Name" strings; the index is the rotation slot.
         */
        data class RotationConfig(
            val enabled: Boolean,
            val switchEvery: Int,
            val inGameNames: List<String>,
            // Per-slot sibling-outfit names to skip at Trainee Select. A bare base-name target is
            // outfit-insensitive in the matcher, so when the user owns the same character in another
            // outfit the navigator must skip that outfit's banner. Parallel to inGameNames; empty for
            // outfit-specific entries and for configs saved before this field existed.
            val excludeOutfitsByIndex: List<List<String>> = emptyList(),
        ) {
            val count: Int get() = inGameNames.size

            /** Sibling outfits to exclude for rotation slot [index], or empty if none / out of range. */
            fun excludesForIndex(index: Int): List<String> = excludeOutfitsByIndex.getOrElse(index) { emptyList() }

            /** 0-based rotation slot for a 1-based run number (blocks of [switchEvery], cycling). */
            fun indexForRun(run: Int): Int {
                if (count <= 0 || switchEvery <= 0) return 0
                return ((run - 1) / switchEvery) % count
            }
        }

        /**
         * Reads the trainee-rotation config from settings. Returns a disabled config when rotation
         * is off, the list is empty, or the JSON is malformed — the queue then runs as a normal
         * single-trainee queue.
         */
        fun loadRotationConfig(): RotationConfig {
            if (!SettingsHelper.getBooleanSetting("runQueue", "enableTraineeRotation", false)) {
                return RotationConfig(false, 1, emptyList())
            }
            val switchEvery = maxOf(1, SettingsHelper.getIntSetting("runQueue", "switchEveryNRuns", 3))
            var names: List<String> = emptyList()
            var excludes: List<List<String>> = emptyList()
            try {
                val arr = JSONArray(SettingsHelper.getStringSetting("runQueue", "traineeRotation"))
                names = (0 until arr.length()).map { arr.getJSONObject(it).optString("inGameName", "") }
                excludes = (0 until arr.length()).map { i ->
                    val ex = arr.getJSONObject(i).optJSONArray("excludeOutfits")
                    if (ex == null) {
                        emptyList()
                    } else {
                        (0 until ex.length()).mapNotNull { j -> ex.optString(j, "").trim().takeIf { it.isNotEmpty() } }
                    }
                }
            } catch (e: Exception) {
                MessageLog.w(TAG, "[ROTATION] Could not parse traineeRotation list: ${e.message}")
                names = emptyList()
                excludes = emptyList()
            }
            return RotationConfig(names.isNotEmpty(), switchEvery, names, excludes)
        }

        /**
         * Copies the precomputed `rot{index}_*` snapshot rows into the live `settings` rows,
         * swapping the active gameplay config to the rotation trainee. Returns false when no
         * snapshot exists for the index — the caller must then stop the queue rather than run the
         * wrong trainee under stale settings.
         *
         * GLOB (not LIKE) is used so `_` stays literal: `rot1_*` must not also match `rot10_*`.
         */
        fun applyRotationSnapshot(context: Context, index: Int): Boolean {
            return try {
                val dbFile = File(context.filesDir, "SQLite/settings.db")
                if (!dbFile.exists()) return false
                val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                try {
                    val prefix = "rot${index}_"
                    val rows = mutableListOf<Triple<String, String, String>>()
                    db.rawQuery(
                        "SELECT category, key, value FROM settings WHERE category GLOB ?",
                        arrayOf("rot${index}_*"),
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            rows.add(
                                Triple(
                                    cursor.getString(0).substring(prefix.length),
                                    cursor.getString(1),
                                    cursor.getString(2) ?: "",
                                ),
                            )
                        }
                    }
                    if (rows.isEmpty()) {
                        MessageLog.e(TAG, "[ROTATION] No snapshot rows for index $index (prefix '$prefix'). Cannot switch trainee.")
                        return false
                    }
                    db.beginTransaction()
                    try {
                        for ((category, key, value) in rows) {
                            db.execSQL(
                                "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                                arrayOf(category, key, value),
                            )
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                    MessageLog.i(TAG, "[ROTATION] Applied snapshot for trainee index $index ($prefix): ${rows.size} settings rows.")
                    true
                } finally {
                    db.close()
                }
            } catch (e: Exception) {
                MessageLog.e(TAG, "[ROTATION] Failed to apply snapshot for index $index: ${e.message}")
                false
            }
        }

        /**
         * Records the target trainee's in-game name so the launch navigator's Trainee Select
         * handler knows who to pick — and to verify against (match-or-stop).
         */
        fun setCurrentTrainee(context: Context, inGameName: String) {
            try {
                val dbFile = File(context.filesDir, "SQLite/settings.db")
                if (!dbFile.exists()) return
                val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                db.execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("queueState", "currentTrainee", inGameName),
                )
                db.close()
            } catch (e: Exception) {
                Log.w(TAG, "[ROTATION] Failed to record current trainee: ${e.message}")
            }
        }

        /**
         * Records the sibling-outfit names the launch navigator must skip at Trainee Select for the
         * current rotation target. A bare base-name target would otherwise match an owned outfit's
         * banner. Newline-joined (outfit names never contain newlines); an empty list clears it.
         */
        fun setCurrentTraineeExcludes(context: Context, excludes: List<String>) {
            try {
                val dbFile = File(context.filesDir, "SQLite/settings.db")
                if (!dbFile.exists()) return
                val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                db.execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("queueState", "currentTraineeExcludes", excludes.joinToString("\n")),
                )
                db.close()
            } catch (e: Exception) {
                Log.w(TAG, "[ROTATION] Failed to record current trainee excludes: ${e.message}")
            }
        }

        /**
         * Marks whether the upcoming launch must switch the in-game trainee. The launch navigator
         * clears this once its Trainee Select handler has selected + verified the target; if it is
         * still set when the navigator reaches Legacy Select, the swap was missed (e.g. a Trainee
         * Select detection miss tapped through the screen keeping the wrong trainee) and the queue
         * stops before the career starts rather than run the wrong trainee.
         */
        fun setRotationSwitchPending(context: Context, pending: Boolean) {
            try {
                val dbFile = File(context.filesDir, "SQLite/settings.db")
                if (!dbFile.exists()) return
                val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                db.execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("queueState", "rotationSwitchPending", pending.toString()),
                )
                db.close()
            } catch (e: Exception) {
                Log.w(TAG, "[ROTATION] Failed to set rotationSwitchPending: ${e.message}")
            }
        }
    }

    private val context: Context = reactContext.applicationContext
    private var messageId = 1

    /**
     * Rotation slot index of the previously launched run this session, or -1 before the first run.
     * Drives switch-boundary detection in [applyRotationForRun]; reset at the top of onStartEvent so
     * the first launched run of every session always (re)loads its trainee snapshot.
     */
    private var rotationPrevIndex: Int = -1

    /** Bounded hand-off between MessageLog's lock-held EventBus post and the bridge worker. */
    private val jsEventQueue = java.util.concurrent.ArrayBlockingQueue<JSEvent>(512)

    init {
        StartModule.reactContext = reactContext
        StartModule.reactContext?.addActivityEventListener(this)
        Log.d(TAG, "StartModule is now initialized.")
    }

    override fun getName(): String {
        return "StartModule"
    }

    override fun onNewIntent(intent: Intent) {
        // Empty implementation
    }

    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            // Start up the MediaProjection service after the user accepts the onscreen prompt.
            reactContext?.startService(
                MediaProjectionService.getStartIntent(reactContext!!, resultCode, data!!),
            )
            sendEvent("MediaProjectionService", "Running")
            Log.d(TAG, "MediaProjectionService is now running.")
        }
    }

    // //////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////
    // Interaction with the Start / Stop button.

    /** This is called when the Start button is pressed back at the Javascript frontend and starts up the MediaProjection service along with the BotService attached to it. */
    @ReactMethod
    fun start() {
        if (readyCheck()) {
            // Initialize SQLite settings.
            Log.d(TAG, "Starting SQLite settings initialization...")

            // Check if the database file exists.
            val dbFile = File(context.filesDir, "SQLite/settings.db")
            Log.d(TAG, "Database file path: ${dbFile.absolutePath}")
            Log.d(TAG, "Database file exists: ${dbFile.exists()}")
            Log.d(TAG, "Database file can read: ${dbFile.canRead()}")
            Log.d(TAG, "Database file size: ${if (dbFile.exists()) dbFile.length() else "N/A"} bytes")

            // List the contents of the files directory to see what's actually there.
            val filesDir = context.filesDir
            Log.d(TAG, "Files directory: ${filesDir.absolutePath}")
            val files = filesDir.listFiles()
            if (files != null) {
                Log.d(TAG, "Files in files directory:")
                for (file in files) {
                    Log.d(TAG, "  - ${file.name} (${if (file.isDirectory) "dir" else "file"})")
                }
            }

            // Check if SQLite subdirectory exists.
            val sqliteDir = File(context.filesDir, "SQLite")
            Log.d(TAG, "SQLite directory exists: ${sqliteDir.exists()}")
            if (sqliteDir.exists()) {
                val sqliteFiles = sqliteDir.listFiles()
                if (sqliteFiles != null) {
                    Log.d(TAG, "Files in SQLite directory:")
                    for (file in sqliteFiles) {
                        Log.d(TAG, "  - ${file.name} (${file.length()} bytes)")
                    }
                }
            }

            // Validate the database and maintain the backup BEFORE anything opens it with
            // Android's default error handler, which deletes the file outright on corruption.
            safeguardSettingsDatabase()

            // Initialize the SettingsHelper's connection to the SQLite database.
            // This is required to correctly fetch the flag for enabling the Remote Log Viewer.
            if (!SettingsHelper.isAvailable()) {
                SettingsHelper.initialize(context)
            }

            // Start the remote log stream server if enabled in settings.
            val enableRemoteLogViewer = SettingsHelper.getBooleanSetting("debug", "enableRemoteLogViewer", false)
            Log.d(TAG, "Able to start Remote Log Viewer in start(): $enableRemoteLogViewer")
            if (enableRemoteLogViewer) {
                val port = SettingsHelper.getIntSetting("debug", "remoteLogViewerPort", 9000)
                LogStreamServer.start(context, port)
            }

            startProjection()
        }
    }

    /**
     * Backup/restore guard for the settings database, run on every Start press before the
     * automation library opens it.
     *
     * Android's DefaultDatabaseErrorHandler reacts to corruption by deleting the database
     * file outright (an install force-killing the app mid-write can corrupt it, losing every
     * setting and seed table). This guard validates the file with a no-op error handler so the
     * probe itself cannot trigger a wipe, restores the last known-good backup when validation
     * fails, and refreshes the backup after every successful validation. WAL mode (set on the
     * React Native side) prevents the corruption; this recovers from whatever slips through anyway.
     */
    private fun safeguardSettingsDatabase() {
        val dbFile = File(context.filesDir, "SQLite/settings.db")
        val backupFile = File(context.filesDir, "SQLite/settings.db.bak")
        if (!dbFile.exists()) {
            Log.d(TAG, "Settings database does not exist yet. Nothing to safeguard.")
            return
        }

        // A no-op handler: corruption is reported by the validation below, never acted on here.
        val noopErrorHandler = DatabaseErrorHandler { Log.e(TAG, "Settings database reported corruption during validation.") }

        fun validate(): Boolean =
            try {
                SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE, noopErrorHandler).use { db ->
                    val bIntact: Boolean =
                        db.rawQuery("PRAGMA integrity_check(1)", null).use { c -> c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true) }
                    // A wipe can leave a recreated settings table with the seed tables missing,
                    // so an intact file is not enough - the data has to be there too.
                    val bSeeded: Boolean =
                        bIntact &&
                            db.rawQuery("SELECT COUNT(*) FROM settings", null).use { c -> c.moveToFirst() && c.getInt(0) > 0 } &&
                            db.rawQuery("SELECT COUNT(*) FROM skills", null).use { c -> c.moveToFirst() && c.getInt(0) > 0 }
                    bSeeded
                }
            } catch (e: Exception) {
                Log.e(TAG, "Settings database failed validation: ${e.message}")
                false
            }

        if (validate()) {
            try {
                // Checkpoint the WAL so the main file is self-contained before copying it.
                SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE, noopErrorHandler).use { db ->
                    db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c -> c.moveToFirst() }
                }
                dbFile.copyTo(backupFile, overwrite = true)
                Log.d(TAG, "Settings database validated. Backup refreshed (${backupFile.length()} bytes).")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh the settings database backup: ${e.message}")
            }
            return
        }

        if (backupFile.exists()) {
            Log.e(TAG, "Settings database is unhealthy. Restoring the last known-good backup (${backupFile.length()} bytes)...")
            try {
                // Drop journal leftovers so the restored main file is authoritative.
                File(dbFile.absolutePath + "-wal").delete()
                File(dbFile.absolutePath + "-shm").delete()
                File(dbFile.absolutePath + "-journal").delete()
                backupFile.copyTo(dbFile, overwrite = true)
                Log.d(TAG, "Settings database restored from backup. Healthy: ${validate()}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore the settings database backup: ${e.message}")
            }
        } else {
            Log.e(TAG, "Settings database is unhealthy and no backup exists yet. Reopen the app so it reseeds before starting the bot.")
        }
    }

    /** Register this module with EventBus in order to allow listening to certain events and then begin starting up the MediaProjection service. */
    private fun startProjection() {
        // This extra call to unregister is to account for the user stopping the service from the notification which bypasses the call to
        // unregister in stopProjection().
        EventBus.getDefault().unregister(this)
        EventBus.getDefault().register(this)
        Log.d(TAG, "Event Bus registered for StartModule")

        // Use the library's helper which applies MediaProjectionConfig on Android 14+ to prefer full screen capture.
        val screenCaptureIntent = MediaProjectionService.getScreenCaptureIntent(reactContext!!)
        reactContext?.startActivityForResult(screenCaptureIntent, 100, null)
    }

    /** Unregister this module with EventBus and then stops the MediaProjection service. */
    private fun stopProjection() {
        EventBus.getDefault().unregister(this)
        Log.d(TAG, "Event Bus unregistered for StartModule")
        reactContext?.startService(MediaProjectionService.getStopIntent(reactContext!!))
        sendEvent("MediaProjectionService", "Not Running")
    }

    /** This is called when the Stop button is pressed and will begin stopping the MediaProjection service. */
    @ReactMethod
    fun stop() {
        // Also signal the queue to stop so it doesn't continue after the current run.
        queueStopRequested = true
        stopProjection()
    }

    /** Stops the entire queue after the current run finishes. The current run is interrupted. */
    @ReactMethod
    fun stopQueue() {
        Log.d(TAG, "stopQueue() called: requesting full queue stop.")
        queueStopRequested = true
    }

    /**
     * Checks if there is an interrupted queue state from a previous crash.
     * Returns a WritableMap with {active, currentRun, totalRuns, timestamp} or null values if no state exists.
     */
    @ReactMethod
    fun getInterruptedQueueState(promise: Promise) {
        try {
            val dbFile = File(context.filesDir, "SQLite/settings.db")
            if (!dbFile.exists()) {
                promise.resolve(null)
                return
            }
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor =
                db.rawQuery(
                    "SELECT key, value FROM settings WHERE category = 'queueState'",
                    null,
                )
            val state = mutableMapOf<String, String>()
            while (cursor.moveToNext()) {
                state[cursor.getString(0)] = cursor.getString(1)
            }
            cursor.close()
            db.close()

            val active = state["active"]?.toBoolean() ?: false
            if (!active) {
                promise.resolve(null)
                return
            }

            // Check that the crash wasn't too long ago (stale state = older than 6 hours).
            val timestamp = state["timestamp"]?.toLongOrNull() ?: 0
            val ageMs = System.currentTimeMillis() - timestamp
            if (ageMs > 6 * 60 * 60 * 1000) {
                // State is stale, clear it.
                clearQueueState(context)
                promise.resolve(null)
                return
            }

            val map = Arguments.createMap()
            map.putInt("currentRun", state["currentRun"]?.toIntOrNull() ?: 0)
            map.putInt("totalRuns", state["totalRuns"]?.toIntOrNull() ?: 0)
            map.putDouble("ageMinutes", ageMs / 60000.0)
            promise.resolve(map)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read queue state: ${e.message}")
            promise.resolve(null)
        }
    }

    /** Clears any persisted interrupted queue state. */
    @ReactMethod
    fun clearInterruptedQueueState() {
        clearQueueState(context)
    }

    /** Skips the current run and advances to the next one in the queue. */
    @ReactMethod
    fun skipQueueRun() {
        Log.d(TAG, "skipQueueRun() called: requesting skip of current run.")
        queueSkipRequested = true
    }

    /** Opens the system Accessibility settings page to allow the user to toggle the service off and on. */
    @ReactMethod
    fun openAccessibilitySettings() {
        Log.d(TAG, "Opening Accessibility Settings...")
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        this.reactApplicationContext.currentActivity?.startActivity(intent)
    }

    /**
     * Checks the status of the Accessibility Service, checking both if it is enabled in settings and if it is actually initialized.
     *
     * @param promise The React Native promise that resolves the WritableMap of metrics.
     */
    @ReactMethod
    fun getAccessibilityStatus(promise: Promise) {
        try {
            val map = Arguments.createMap()
            val context = reactApplicationContext

            // Method 1: Check Settings.Secure
            val prefString = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val serviceName = context.packageName + "/" + MyAccessibilityService::class.java.name
            val enabledInSettings = prefString?.contains(serviceName) == true
            Log.d(TAG, "Accessibility enabled in Settings: $enabledInSettings")

            // Method 2: Check AccessibilityManager
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            var enabledInManager = false
            for (info in enabledServices) {
                if (info.resolveInfo.serviceInfo.packageName == context.packageName &&
                    info.resolveInfo.serviceInfo.name == MyAccessibilityService::class.java.name
                ) {
                    enabledInManager = true
                    break
                }
            }
            Log.d(TAG, "Accessibility enabled in Manager: $enabledInManager")

            map.putBoolean("enabled", enabledInSettings || enabledInManager)

            // Check if active (initialized).
            var active = false
            try {
                MyAccessibilityService.getInstance()
                active = true
            } catch (e: IllegalStateException) {
                // If the message is "not running" but initialized, it means it is actually ready.
                if (e.message?.contains("not running") == true) {
                    active = true
                } else {
                    Log.d(TAG, "Accessibility Service is not initialized: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Accessibility Service instance check failed: ${e.message}")
            }
            map.putBoolean("active", active)

            promise.resolve(map)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve accessibility status: ${e.message}")
            promise.reject("ACCESSIBILITY_STATUS_ERROR", "Failed to retrieve accessibility status: ${e.message}")
        }
    }

    // //////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////
    // Permissions

    /**
     * Checks the permissions for both overlay and accessibility for this app.
     *
     * @return True if both permissions were already granted and false otherwise.
     */
    private fun readyCheck(): Boolean {
        return checkForOverlayPermission() && checkForAccessibilityPermission() && checkForBatteryOptimization()
    }

    /**
     * Checks for overlay permission and guides the user to enable it if it has not been granted yet.
     *
     * @return True if the overlay permission has already been granted.
     */
    private fun checkForOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this.reactApplicationContext.currentActivity)) {
            Log.d(TAG, "Application is missing overlay permission.")

            val builder = AlertDialog.Builder(this.reactApplicationContext.currentActivity)
            builder.setTitle(R.string.overlay_disabled)
            builder.setMessage(R.string.overlay_disabled_message)

            builder.setPositiveButton(R.string.go_to_settings) { _, _ ->
                // Send the user to the Overlay Settings.
                val uri = "package:${reactContext?.packageName}"
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri.toUri())
                this.reactApplicationContext.currentActivity?.startActivity(intent)
            }

            builder.setNegativeButton(android.R.string.cancel, null)

            builder.show()
            return false
        }

        Log.d(TAG, "Application has permission to draw overlay.")
        return true
    }

    /**
     * Checks for accessibility permission and guides the user to enable it if it has not been granted yet.
     *
     * @return True if the accessibility permission has already been granted.
     */
    private fun checkForAccessibilityPermission(): Boolean {
        val prefString = Settings.Secure.getString(reactContext?.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

        if (prefString != null && prefString.isNotEmpty()) {
            // Check the string of enabled accessibility services to see if this application's accessibility service is there.
            val enabled = prefString.contains(reactContext?.packageName.toString() + "/" + MyAccessibilityService::class.java.name)

            if (enabled) {
                Log.d(TAG, "This application's Accessibility Service is currently turned on.")
                return true
            }
        }

        // Shows a dialog explaining how to enable Accessibility Service when restricted settings are detected.
        // The dialog provides options to navigate to App Info or Accessibility Settings to complete the setup.
        AlertDialog.Builder(this.reactApplicationContext.currentActivity).apply {
            setTitle(R.string.accessibility_disabled)
            setMessage(
                """
                To enable Accessibility Service:
                
                1. Tap "Go to App Info".
                2. Tap the 3-dot menu in the top right. If not available, you can skip to step 4.
                3. Tap "Allow restricted settings".
                4. Return to Accessibility Settings and enable the service.
                """.trimIndent(),
            )
            setPositiveButton("Go to App Info") { _, _ ->
                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${reactContext?.packageName}".toUri()
                    }
                this@StartModule.reactApplicationContext.currentActivity?.startActivity(intent)
            }
            setNeutralButton("Accessibility Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                this@StartModule.reactApplicationContext.currentActivity?.startActivity(intent)
            }
            setNegativeButton(android.R.string.cancel, null)
        }.show()

        return false
    }

    /**
     * Checks if battery optimization is disabled for this app and guides the user to enable it if needed.
     *
     * This ensures the app can run reliably in the background without being killed by Android's battery optimization features during long-running automation tasks.
     *
     * @return True if battery optimization is already disabled for this app.
     */
    private fun checkForBatteryOptimization(): Boolean {
        if (BatteryOptimizationUtils.isIgnoringBatteryOptimizations(context)) {
            Log.d(TAG, "Application is already ignoring battery optimizations.")
            return true
        }

        Log.d(TAG, "Application is not ignoring battery optimizations.")

        AlertDialog.Builder(this.reactApplicationContext.currentActivity).apply {
            setTitle(R.string.battery_optimization_title)
            setMessage(R.string.battery_optimization_message)
            setPositiveButton(R.string.go_to_settings) { _, _ ->
                BatteryOptimizationUtils.requestIgnoreBatteryOptimizations(context)
            }
            setNegativeButton(android.R.string.cancel, null)
        }.show()

        return false
    }

    // //////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////
    // Event interaction

    /**
     * Listener function to start this module's entry point.
     *
     * @param event The StartEvent object to parse its message.
     */

    /**
     * Sends a structured queue progress event to the JS frontend.
     *
     * @param currentRun The current run index (1-based).
     * @param totalRuns The total number of runs in the queue.
     * @param status The current status string (e.g. "starting", "completed", "navigating", "waiting", "queueComplete", "queueFailed").
     * @param resultCode Optional result code name from the completed run.
     * @param message Optional descriptive message.
     */
    private fun sendQueueProgressEvent(currentRun: Int, totalRuns: Int, status: String, resultCode: String? = null, message: String? = null) {
        val payload =
            JSONObject().apply {
                put("currentRun", currentRun)
                put("totalRuns", totalRuns)
                put("status", status)
                if (resultCode != null) put("resultCode", resultCode)
                if (message != null) put("message", message)
            }
        sendEvent("RunQueueProgress", payload.toString())
    }

    /**
     * Runs a single Game instance on a background thread and returns its TaskResult.
     *
     * @return The TaskResult from Game.start(), or an Error result if an exception occurred.
     */
    private fun runSingleGame(): TaskResult {
        var taskResult: TaskResult? = null

        val botThread =
            Thread {
                try {
                    val entryPoint = Game(context)
                    taskResult = entryPoint.start()
                } catch (e: Exception) {
                    EventBus.getDefault().postSticky(ExceptionEvent(e))
                    taskResult =
                        TaskResult.Error(
                            TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION,
                            "Unhandled exception: ${e.message}",
                        )
                }
            }

        botThread.start()

        try {
            botThread.join()
        } catch (e: InterruptedException) {
            Log.d(TAG, "EventBus StartEvent subscriber was interrupted. Propagating to Bot Thread...")
            botThread.interrupt()
            try {
                botThread.join()
            } catch (_: InterruptedException) {
            }
        }

        return taskResult ?: TaskResult.Error(
            TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION,
            "Game did not return a result.",
        )
    }

    /**
     * Waits for the specified number of seconds, checking queue control flags every 100ms.
     * Returns false if the wait was interrupted by a stop request.
     * Ticks [Game.heartbeat] each iteration so user-configured between-runs delays don't
     * false-trigger the stall watchdog.
     */
    private fun interruptibleWait(seconds: Int): Boolean {
        val totalMs = seconds * 1000L
        var elapsed = 0L
        while (elapsed < totalMs) {
            if (queueStopRequested || !BotService.isRunning) {
                return false
            }
            Game.heartbeat()
            Thread.sleep(100)
            elapsed += 100
        }
        return true
    }

    /**
     * Prepares the active settings for [upcomingRun] under trainee rotation.
     *
     * At a switch boundary (the rotation slot changes, including the first launched run of a
     * session) it swaps in that trainee's settings snapshot. It always records the target name so
     * the launch navigator's Trainee Select handler can pick + verify her: the game shows Trainee
     * Select on every launch (the navigator otherwise taps through it via POST_RUN_RESULTS, keeping
     * the last trainee), and the rotation-gated detector catches it and selects the target instead.
     *
     * The user's reuse preference is returned unchanged. Reuse must stay on: the deck handler fails
     * fast without it, and the support deck persists across a trainee change, so it is still valid
     * at a switch. The trainee swap rides on the Trainee Select handler, not the reuse flag.
     *
     * @return the reuse flag to pass to the navigator, or null to STOP the queue (snapshot missing —
     *         must never run the wrong trainee under stale settings).
     */
    private fun applyRotationForRun(rotation: RotationConfig, upcomingRun: Int, userReuse: Boolean): Boolean? {
        if (!rotation.enabled) return userReuse
        val index = rotation.indexForRun(upcomingRun)
        val target = rotation.inGameNames.getOrElse(index) { "" }
        val switching = index != rotationPrevIndex
        if (switching) {
            if (!applyRotationSnapshot(context, index)) return null
            MessageLog.i(TAG, "[ROTATION] Run $upcomingRun -> switching to trainee #${index + 1} '$target' (snapshot loaded).")
        }
        setCurrentTrainee(context, target)
        setCurrentTraineeExcludes(context, rotation.excludesForIndex(index))
        // Arm the missed-detection backstop only on an actual switch; the navigator clears it when
        // Trainee Select is handled, and fails at Legacy Select if it is still armed.
        setRotationSwitchPending(context, switching)
        rotationPrevIndex = index
        return userReuse
    }

    /**
     * Runs one CareerLaunchNavigator.navigate() call under the navigation deadline.
     *
     * Navigation runs outside the per-run timeout in Task.start, and the 3-minute stall
     * watchdog stays calm as long as anything ticks the heartbeat - which the navigator's
     * wait loops do. So a single wedged call below navigate() (e.g. MuMu killing the
     * accessibility service mid-navigation) can hang the queue forever with zero log output.
     * The deadline thread interrupts the queue thread if navigate() overruns; if the interrupt
     * doesn't land within the grace window, it requests a queue stop so the session still ends
     * with a saved log instead of an invisible hang.
     */
    private fun navigateWithDeadline(reuseLastLaunchSetup: Boolean, navigator: CareerLaunchNavigator = CareerLaunchNavigator(context)): NavigationResult {
        val navDone = java.util.concurrent.atomic.AtomicBoolean(false)
        // Set true ONLY when the deadline thread itself interrupts the queue thread. The catch
        // below uses this to tell a genuine wedge from a stop/teardown interrupt: an
        // InterruptedException with deadlineFired==false means navigation was cut short by
        // something other than the 10-minute deadline (a user Stop, or the service teardown a
        // Stop triggers), so reporting it as "exceeded 10 minutes / emulator died" would be a
        // false alarm that sends us chasing emulator failures that never happened.
        val deadlineFired = java.util.concurrent.atomic.AtomicBoolean(false)
        val queueThread = Thread.currentThread()
        val deadlineThread =
            Thread {
                val deadline = System.currentTimeMillis() + NAV_DEADLINE_MS
                while (!navDone.get() && System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(2_000)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                }
                if (navDone.get()) return@Thread
                // Act FIRST, log via logcat only. MessageLog must never appear on this thread:
                // its global lock is the very thing the wedged queue thread may be holding, so a
                // MessageLog.e here before the interrupt deadlocks and zombies the queue.
                deadlineFired.set(true)
                queueThread.interrupt()
                Log.e(TAG, "[QUEUE] Career launch navigation exceeded ${NAV_DEADLINE_MS / 60000} minutes. Navigation thread interrupted.")
                try {
                    Thread.sleep(NAV_INTERRUPT_GRACE_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (!navDone.get()) {
                    // Volatile writes only on this thread (no MessageLog - see above). Setting the
                    // reason first keeps the eventual stop from rendering as "user stop" in the log.
                    queueStopReason = "Between-run navigation did not respond to the interrupt within the grace period."
                    queueStopRequested = true
                    Log.e(TAG, "[QUEUE] Navigation thread did not respond to the interrupt. Queue stop requested; the stall watchdog is the next net.")
                }
            }
        deadlineThread.name = "NavDeadline"
        deadlineThread.isDaemon = true
        deadlineThread.start()

        return try {
            navigator.navigate(reuseLastLaunchSetup)
        } catch (e: InterruptedException) {
            // Clear the interrupt flag so queue teardown (log saving, events) is not poisoned.
            Thread.interrupted()
            // Attribute the interrupt by its ACTUAL source, not by guessing from queueStopRequested.
            // Three cases, in order of certainty:
            //  - deadlineFired: the NavDeadline thread genuinely tripped the 10-minute budget. The
            //    only case where the "wedged / capture-pipeline-died" boilerplate is true.
            //  - a user Stop: queueStopRequested set, or the service torn down (BotService not running).
            //    The interrupt rides in on the service teardown a Stop triggers; reading the absent flag
            //    on this path as "deadline expired" cried emulator-death on every manual Stop.
            //  - neither: an unexpected interrupt BEFORE the deadline. Not a wedge - report it plainly
            //    instead of inventing a 10-minute timeout that did not occur.
            when {
                deadlineFired.get() ->
                    NavigationResult(
                        success = false,
                        lastDetectedState = "WEDGED",
                        failureReason = "Navigation did not return within ${NAV_DEADLINE_MS / 60000} minutes and was interrupted by the deadline watchdog.",
                        failedTransition = "career launch navigation",
                        isRecoverable = true,
                        recommendedAction = "Check the emulator - the capture pipeline or accessibility service likely died mid-navigation. Restart the queue once the game is stable.",
                    )
                queueStopRequested || !BotService.isRunning ->
                    NavigationResult(
                        success = false,
                        lastDetectedState = "STOPPED",
                        failureReason = "Between-run navigation cancelled by user stop.",
                        failedTransition = "career launch navigation",
                        isRecoverable = true,
                        recommendedAction = "No action needed - the bot was stopped by the user.",
                    )
                else ->
                    NavigationResult(
                        success = false,
                        lastDetectedState = "INTERRUPTED",
                        failureReason = "Between-run navigation was interrupted before the deadline (likely a stop or service teardown), not a wedge.",
                        failedTransition = "career launch navigation",
                        isRecoverable = true,
                        recommendedAction = "If this was not a manual Stop, check the logs around the interrupt; the navigation did not actually time out.",
                    )
            }
        } finally {
            navDone.set(true)
            deadlineThread.interrupt()
        }
    }

    /** Logs a failed [NavigationResult] with its full diagnostics. */
    private fun logNavigationFailure(navResult: NavigationResult) {
        if (navResult.lastDetectedState == "STOPPED") {
            // A user-requested Stop during between-run navigation is a clean cancellation, not a
            // failure. Keep it out of the ERROR channel so it doesn't read as an emulator/capture
            // crash during triage (the "Recommended action: check the emulator" boilerplate is wrong here).
            MessageLog.i(TAG, "[QUEUE] ${navResult.failureReason}")
            return
        }
        MessageLog.e(TAG, "[QUEUE] Navigation failed: ${navResult.failureReason}")
        MessageLog.e(TAG, "[QUEUE] Last detected state: ${navResult.lastDetectedState}")
        MessageLog.e(TAG, "[QUEUE] Failed transition: ${navResult.failedTransition}")
        MessageLog.e(TAG, "[QUEUE] Recommended action: ${navResult.recommendedAction}")
        if (navResult.screenshotPath.isNotEmpty()) {
            MessageLog.e(TAG, "[QUEUE] Failure screenshot: ${navResult.screenshotPath}")
        }
    }

    @Subscribe
    fun onStartEvent(event: StartEvent) {
        if (event.message == "Entry Point ON") {
            // Acquire a PARTIAL_WAKE_LOCK for the entire bot session so Android's OomAdjuster
            // doesn't mark the process as 'empty' and SIGKILL it under memory pressure (TRIM_EMPTY).
            // Released in the finally below regardless of how the session ends.
            Game.acquireWakeLock(context)
            try {
                // Reset queue control flags at the start of every new session.
                queueStopRequested = false
                queueStopReason = null
                queueSkipRequested = false

                // Reset rotation boundary tracking so the first launched run of this session always
                // (re)loads its trainee snapshot, even within the same app process as a prior queue.
                rotationPrevIndex = -1
                setRotationSwitchPending(context, false)

                // Reset the session-scoped TP restore counter (companion-held so it survives
                // the per-handoff navigator reconstruction).
                CareerLaunchNavigator.resetTpRestoresForSession()

                // Reset the log stream mute to ensure logs for the new run are broadcasted.
                LogStreamServer.resetMute()

                // Read queue settings from SQLite.
                val enableRunQueue = SettingsHelper.getBooleanSetting("runQueue", "enableRunQueue", false)
                val totalRuns = if (enableRunQueue) SettingsHelper.getIntSetting("runQueue", "totalRuns", 2) else 1
                val delayBetweenRuns = SettingsHelper.getIntSetting("runQueue", "delayBetweenRunsSeconds", 15)
                val stopOnError = SettingsHelper.getBooleanSetting("runQueue", "stopOnError", false)
                val reuseLastLaunchSetup = SettingsHelper.getBooleanSetting("runQueue", "reuseLastLaunchSetup", true)

                // Trainee rotation: parse the cycle once, up here so the auto-resume decision below
                // can distinguish a rotation queue (which must re-enter an interrupted career, never
                // skip to the next trainee's snapshot) from a normal single-trainee queue. Also used
                // by the cold-start snapshot load and the between-run switch further down.
                val rotation = loadRotationConfig()
                if (enableRunQueue && rotation.enabled) {
                    MessageLog.i(TAG, "[ROTATION] Enabled: ${rotation.count} trainees, switching every ${rotation.switchEvery} run(s).")
                }

                if (enableRunQueue) {
                    MessageLog.i(TAG, "[QUEUE] Run queue enabled. Total runs: $totalRuns, delay: ${delayBetweenRuns}s, stopOnError: $stopOnError")
                }

                // --- Layer 4: auto-resume after process death ---
                // If a queue was running when the previous process was killed (TRIM_EMPTY,
                // watchdog self-restart, etc.), SQLite still has queueState.active=true with
                // the run number that was in flight. Skip past that run and pick up the next
                // one. Only applies when queueing is currently enabled AND the saved totalRuns
                // matches the current setting. If the user changed queue config after the
                // crash, the saved state is no longer applicable and we ignore it.
                val startFromRun: Int =
                    run {
                        if (!enableRunQueue) return@run 1
                        val saved = loadQueueState(context) ?: return@run 1
                        if (saved.totalRuns != totalRuns) {
                            MessageLog.i(
                                TAG,
                                "[RESUME] Ignoring saved queue state (saved totalRuns=${saved.totalRuns} differs from current totalRuns=$totalRuns).",
                            )
                            clearQueueState(context)
                            return@run 1
                        }
                        // Phase-aware resume for rotation. A rotation queue killed mid-career must
                        // re-enter that SAME run so the in-flight trainee finishes under her own
                        // preset — resuming at currentRun+1 would load the next trainee's snapshot
                        // onto the running career. A kill at the launch boundary (career done,
                        // launching the next) resumes at currentRun+1 as usual. Non-rotation queues
                        // keep the original "skip the interrupted run" behavior: same trainee either
                        // way, and not re-entering a possibly-wedged career is the safer default.
                        val reEnter = rotation.enabled && saved.phase == PHASE_CAREER
                        val next = if (reEnter) saved.currentRun else saved.currentRun + 1
                        if (next > totalRuns) {
                            MessageLog.i(
                                TAG,
                                "[RESUME] Saved queue was at its last run (${saved.currentRun}/${saved.totalRuns}); nothing to resume. Treating as complete.",
                            )
                            clearQueueState(context)
                            return@run totalRuns + 1 // skips the for-loop entirely
                        }
                        MessageLog.w(
                            TAG,
                            if (reEnter) {
                                "[RESUME] Detected interrupted queue from ${saved.ageMs / 60_000}m ago. Re-entering run $next of $totalRuns; its career was in flight and finishes under the same trainee's preset."
                            } else {
                                "[RESUME] Detected interrupted queue from ${saved.ageMs / 60_000}m ago. Resuming at run $next of $totalRuns (run ${saved.currentRun} was interrupted)."
                            },
                        )
                        sendQueueProgressEvent(
                            next,
                            totalRuns,
                            "resuming",
                            message = "Auto-resuming: starting at run $next of $totalRuns (previous run was interrupted)",
                        )
                        next
                    }

                var completedRuns = 0

                // Rotation cycle parsed above (before the resume block). The cold-start snapshot for
                // the first launched run is applied just below, before the home-screen probe reads
                // the scenario, so a rotation that switches scenarios launches the correct campaign.
                var coldStartReuse = reuseLastLaunchSetup
                if (enableRunQueue && rotation.enabled && startFromRun <= totalRuns && BotService.isRunning && !queueStopRequested) {
                    val r = applyRotationForRun(rotation, startFromRun, reuseLastLaunchSetup)
                    if (r == null) {
                        sendQueueProgressEvent(startFromRun, totalRuns, "queueFailed", TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name, "Missing rotation snapshot for the first trainee.")
                        queueStopRequested = true
                    } else {
                        coldStartReuse = r
                    }
                }

                // Cold start: the navigator otherwise only runs BETWEEN careers, so a queue
                // started while the game is parked on the home screen (e.g. a previous queue
                // failed out during navigation and ended there) used to burn run 1 on failed
                // screen detection inside the career loop. Detect that one unambiguous case
                // and drive a career launch first. Probe failures fall through to the old
                // behavior of starting the run directly.
                if (enableRunQueue && startFromRun <= totalRuns && BotService.isRunning && !queueStopRequested) {
                    val scenarioSetting = SettingsHelper.getStringSetting("general", "scenario")
                    val isMiscMode = scenarioSetting == "Daily Races" || scenarioSetting == "Team Trials"
                    // Reuse the probe's navigator for the launch - its Game/CV initialisation is
                    // the expensive part, and navigate() resets all session-scoped flags itself.
                    val coldStartNavigator = if (isMiscMode) null else CareerLaunchNavigator(context)
                    if (coldStartNavigator != null && coldStartNavigator.isOnHomeScreen()) {
                        MessageLog.i(TAG, "[QUEUE] Game is on the home screen. Launching a career for run $startFromRun...")
                        sendQueueProgressEvent(startFromRun, totalRuns, "navigating")
                        val navResult = navigateWithDeadline(coldStartReuse, coldStartNavigator)
                        if (!navResult.success) {
                            logNavigationFailure(navResult)
                            sendQueueProgressEvent(startFromRun, totalRuns, "queueFailed", TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name, navResult.failureReason)
                            queueStopRequested = true
                        }
                    }
                }

                for (i in startFromRun..totalRuns) {
                    // Check stop flag before starting each run.
                    if (queueStopRequested || !BotService.isRunning) {
                        MessageLog.i(TAG, "[QUEUE] Queue stop requested before run $i. Exiting queue.")
                        break
                    }

                    // Reset the skip flag for this run.
                    queueSkipRequested = false

                    if (enableRunQueue) {
                        // Reset log stream mute for each subsequent run.
                        LogStreamServer.resetMute()
                        // Persist queue state so it can survive crashes. Phase CAREER: run i's
                        // career is about to play, so a kill here resumes by re-entering run i.
                        saveQueueState(context, active = true, currentRun = i, totalRuns = totalRuns, phase = PHASE_CAREER)
                        sendQueueProgressEvent(i, totalRuns, "starting")
                        MessageLog.i(TAG, "\n[QUEUE] ========================================")
                        MessageLog.i(TAG, "[QUEUE] Starting run $i of $totalRuns")
                        MessageLog.i(TAG, "[QUEUE] ========================================\n")
                    }

                    // Run the game.
                    val result = runSingleGame()

                    // Determine the effective result considering queue flags.
                    val effectiveResult =
                        when {
                            queueSkipRequested -> {
                                MessageLog.i(TAG, "[QUEUE] Run $i was skipped by queue.")
                                TaskResult.Success(TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE, "Run was skipped by queue.")
                            }
                            queueStopRequested -> {
                                MessageLog.i(TAG, "[QUEUE] Run $i stopped: ${queueStopReason ?: "user stop"}.")
                                result // Use original result
                            }
                            else -> result
                        }

                    if (enableRunQueue) {
                        sendQueueProgressEvent(i, totalRuns, "completed", effectiveResult.code.name, effectiveResult.message)
                    }

                    // Evaluate the result.
                    when (effectiveResult.code) {
                        TaskResultCode.TASK_RESULT_MANUALLY_STOPPED -> {
                            // A genuine user Stop OR a deliberate internal queue-stop (e.g. the
                            // trainee-mismatch guard, which sets queueStopReason). Either way we exit the
                            // queue; the reason makes the log honest about which one it actually was.
                            if (!queueSkipRequested) {
                                MessageLog.i(TAG, "[QUEUE] ${queueStopReason ?: "User stopped the bot"}. Exiting queue.")
                                break
                            }
                            completedRuns++
                        }
                        TaskResultCode.TASK_RESULT_COMPLETE -> {
                            completedRuns++
                        }
                        TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE -> {
                            completedRuns++
                        }
                        TaskResultCode.TASK_RESULT_BREAKPOINT_REACHED -> {
                            completedRuns++
                            // Breakpoints stop the queue. The user set them for a reason.
                            if (enableRunQueue) {
                                MessageLog.i(TAG, "[QUEUE] Run $i hit a breakpoint. Stopping queue.")
                            }
                            break
                        }
                        else -> {
                            // Error, timeout, connection error, etc.
                            if (stopOnError) {
                                MessageLog.e(TAG, "[QUEUE] Run $i ended with ${effectiveResult.code}. Stopping queue (stopOnError=true).")
                                break
                            } else {
                                MessageLog.w(TAG, "[QUEUE] Run $i ended with ${effectiveResult.code}. Continuing queue (stopOnError=false).")
                                completedRuns++
                            }
                        }
                    }

                    // If this is not the last run, navigate back and wait.
                    if (i < totalRuns && enableRunQueue) {
                        // Check stop again before navigation.
                        if (queueStopRequested || !BotService.isRunning) {
                            MessageLog.i(TAG, "[QUEUE] Queue stop requested. Exiting queue.")
                            break
                        }

                        // Phase LAUNCHING: run i's career is done and the launch of run i+1 is
                        // starting. A kill from here resumes at run i+1 — the finished career i is
                        // not re-played, and (under rotation) i+1's snapshot is the correct one.
                        saveQueueState(context, active = true, currentRun = i, totalRuns = totalRuns, phase = PHASE_LAUNCHING)

                        // Trainee rotation: swap to the next run's trainee (settings + select mode)
                        // before reading the scenario or navigating. Stop the queue if its snapshot
                        // is missing rather than launch the wrong trainee under stale settings.
                        val nextReuse = applyRotationForRun(rotation, i + 1, reuseLastLaunchSetup)
                        if (nextReuse == null) {
                            sendQueueProgressEvent(i, totalRuns, "queueFailed", TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name, "Missing rotation snapshot for the next trainee.")
                            break
                        }

                        // Between-run cleanup: hint GC and refresh the watchdog heartbeat so the
                        // next run starts with lower PSS. Every KB we save here reduces the chance
                        // of a TRIM_EMPTY kill at end-of-next-run.
                        Game.cleanupBetweenRuns()

                        sendQueueProgressEvent(i, totalRuns, "navigating")

                        // Misc task modes (Daily Races, Team Trials) skip the career
                        // navigator entirely - their own state machines handle navigation
                        // from whatever screen the previous run left the game on.
                        val currentScenario = SettingsHelper.getStringSetting("general", "scenario")
                        val isMiscQueue = currentScenario == "Daily Races" || currentScenario == "Team Trials"

                        if (isMiscQueue) {
                            MessageLog.i(TAG, "[QUEUE] Misc task queue - skipping career navigator for next run.")
                        } else {
                            MessageLog.i(TAG, "[QUEUE] Navigating back to career start for next run...")

                            val navResult = navigateWithDeadline(nextReuse)

                            if (!navResult.success) {
                                logNavigationFailure(navResult)
                                sendQueueProgressEvent(i, totalRuns, "queueFailed", TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name, navResult.failureReason)
                                break
                            }
                        }

                        // Wait between runs.
                        sendQueueProgressEvent(i, totalRuns, "waiting")
                        MessageLog.i(TAG, "[QUEUE] Waiting ${delayBetweenRuns}s before next run...")

                        if (!interruptibleWait(delayBetweenRuns)) {
                            MessageLog.i(TAG, "[QUEUE] Queue stop requested during wait. Exiting queue.")
                            break
                        }
                    }
                }

                if (enableRunQueue) {
                    // Clear persisted queue state since queue finished normally.
                    clearQueueState(context)
                    sendQueueProgressEvent(totalRuns, totalRuns, "queueComplete", message = "Completed $completedRuns of $totalRuns runs.")
                    MessageLog.i(TAG, "\n[QUEUE] ========================================")
                    MessageLog.i(TAG, "[QUEUE] Queue finished. Completed $completedRuns of $totalRuns runs.")
                    MessageLog.i(TAG, "[QUEUE] ========================================\n")
                }
            } finally {
                // Always release the wake lock, even on exception or break paths.
                Game.releaseWakeLock()
            }
        }
    }

    /**
     * Tests the Discord connection by creating a temporary Kord client, looking up the user, opening a DM channel, and sending a test message.
     *
     * @param token The Discord bot token.
     * @param userID The Discord user ID to send the test message to.
     * @param promise The React Native promise to resolve or reject.
     */
    @ReactMethod
    fun testDiscordConnection(token: String, userID: String, promise: Promise) {
        Log.d(TAG, "testDiscordConnection called - token length: ${token.length}, userID: '$userID'")
        Thread {
            runBlocking {
                try {
                    val client = Kord(token)

                    val user =
                        try {
                            client.getUser(Snowflake(userID.toLong()))
                        } catch (e: Exception) {
                            client.shutdown()
                            promise.reject("DISCORD_USER_ERROR", "Failed to find user with the provided user ID.")
                            return@runBlocking
                        }

                    if (user == null) {
                        client.shutdown()
                        promise.reject("DISCORD_USER_ERROR", "Failed to find user with the provided user ID.")
                        return@runBlocking
                    }

                    val dmChannel =
                        try {
                            user.getDmChannel()
                        } catch (e: Exception) {
                            client.shutdown()
                            promise.reject("DISCORD_DM_ERROR", "Failed to open DM channel with user.")
                            return@runBlocking
                        }

                    // Prepend a timestamp to the test message.
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    dmChannel.createMessage("[$timestamp] \u2705 Test message from Uma Android Automation! Discord integration is working.")
                    client.shutdown()
                    promise.resolve("Test message sent successfully!")
                } catch (e: Exception) {
                    Log.e(TAG, "Discord connection test failed: ${e.message}")
                    promise.reject("DISCORD_ERROR", "Failed to connect to Discord: ${e.message}")
                }
            }
        }.start()
    }

    /**
     * Retrieves the device's exact width, height, and DPI metrics.
     *
     * @param promise The React Native promise that resolves the WritableMap of metrics.
     */
    @ReactMethod
    fun getDeviceDimensions(promise: Promise) {
        try {
            val metrics = android.util.DisplayMetrics()

            @Suppress("DEPRECATION")
            val display = reactApplicationContext.getSystemService(android.view.WindowManager::class.java).defaultDisplay
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            val map = Arguments.createMap()
            map.putInt("width", metrics.widthPixels)
            map.putInt("height", metrics.heightPixels)
            map.putInt("dpi", metrics.densityDpi)
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("DEVICE_INFO_ERROR", "Failed to retrieve device dimensions: ${e.message}")
        }
    }

    /**
     * Retrieves the device's WiFi IP address for the Remote Log Viewer.
     *
     * @param promise The React Native promise that resolves with the IP address string.
     */
    @ReactMethod
    fun getDeviceIpAddress(promise: Promise) {
        try {
            val ipAddress = LogStreamServer.getDeviceIpAddress(context)
            promise.resolve(ipAddress)
        } catch (e: Exception) {
            promise.reject("IP_ADDRESS_ERROR", "Failed to retrieve device IP address: ${e.message}")
        }
    }

    /**
     * Sends the message back to the Javascript frontend along with its event name to be listened on.
     *
     * @param eventName The name of the event to be picked up on as defined in the developer's JS frontend.
     * @param message The message string to pass on.
     */
    fun sendEvent(eventName: String, message: String) {
        val params = Arguments.createMap()
        params.putString("message", message)
        params.putInt("id", messageId++)
        if (emitter == null) {
            // Register the event emitter to send messages to JS.
            Log.d(TAG, "Event emitter not found to be able to send messages to the frontend. Registering now.")
            emitter = reactContext?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        }

        try {
            emitter?.emit(eventName, params)
        } catch (e: RuntimeException) {
            // A dead or tearing-down React context throws here. Swallow it: letting it escape
            // turns into EventBus's SubscriberExceptionEvent, whose handler logs via MessageLog,
            // which posts another JSEvent - a feedback loop on the logging path.
            Log.w(TAG, "sendEvent:: emit failed: ${e.message}")
        }
    }

    /**
     * Single daemon worker that drains [jsEventQueue] onto the React Native bridge.
     *
     * MessageLog posts JSEvents synchronously from INSIDE its global log lock, so the subscriber
     * must never do bridge IO on the posting thread: one blocked emit freezes every thread that
     * ever logs (a parked queue thread held that lock and deadlocked both the stall watchdog and
     * the navigation deadline behind it before they could recover anything).
     */
    private val jsEventWorkerStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun ensureJsEventWorker() {
        if (!jsEventWorkerStarted.compareAndSet(false, true)) return
        val worker =
            Thread {
                while (true) {
                    try {
                        val event = jsEventQueue.take()
                        sendEvent(event.eventName, event.message)
                    } catch (_: InterruptedException) {
                        return@Thread
                    } catch (e: Exception) {
                        Log.w(TAG, "JS event forwarding failed: ${e.message}")
                    }
                }
            }
        worker.name = "JsEventForwarder"
        worker.isDaemon = true
        worker.start()
    }

    /**
     * Listener function to forward MessageLog events to the Javascript frontend.
     *
     * Runs synchronously inside MessageLog's lock - enqueue only, never emit here.
     *
     * @param event The JSEvent object to parse its event name and message.
     */
    @Subscribe
    fun onJSEvent(event: JSEvent) {
        // Only send the event to the React Native frontend if it's not internal.
        // This prevents flooding the bridge during parallel operations where disableOutput is true.
        if (event.isInternal) return
        ensureJsEventWorker()
        if (!jsEventQueue.offer(event)) {
            // Queue full: the UI cannot keep up. Drop the oldest line rather than block the bot.
            jsEventQueue.poll()
            jsEventQueue.offer(event)
        }
    }

    /**
     * Listener function to send Exception messages back to the Javascript frontend.
     *
     * @param event The SubscriberExceptionEvent object to parse its event name and message.
     */
    @Subscribe
    fun onSubscriberExceptionEvent(event: SubscriberExceptionEvent) {
        Log.e(TAG, "Received exception event to send: ${event.throwable}")
        MessageLog.e(MainActivity.loggerTag, event.throwable.toString())
        for (line in event.throwable.stackTrace) {
            MessageLog.e(MainActivity.loggerTag, "\t$line", skipPrintTime = true)
        }
        MessageLog.d(MainActivity.loggerTag, "", skipPrintTime = true)
    }
}
