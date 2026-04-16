package com.steve1316.uma_android_automation

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
import android.database.sqlite.SQLiteDatabase
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

        /** When true, the current run should be skipped and the queue should advance. */
        @Volatile
        var queueSkipRequested: Boolean = false

        /**
         * Persists the current queue state to SQLite so it can survive app crashes.
         * Writes directly to the settings database using INSERT OR REPLACE.
         */
        fun saveQueueState(context: Context, active: Boolean, currentRun: Int = 0, totalRuns: Int = 0) {
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
         */
        data class QueueState(
            val active: Boolean,
            val currentRun: Int,
            val totalRuns: Int,
            val ageMs: Long,
        )

        /** Stale queue state older than this is ignored — 6 hours matches the UI-side check. */
        private const val QUEUE_STATE_STALE_MS: Long = 6 * 60 * 60 * 1000L

        /**
         * Load the persisted queue state, if any, and return it only if it represents a
         * genuinely resumable session (active, recent, with sensible run numbers). Returns
         * null in all the cases where we shouldn't auto-resume — no state, explicitly
         * cleared, stale, or malformed.
         *
         * Used on bot-session entry (`onStartEvent`) to detect and resume a queue that
         * was interrupted by a SIGKILL / TRIM_EMPTY in a previous process lifetime.
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

                return QueueState(active, currentRun, totalRuns, ageMs)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load queue state: ${e.message}")
                return null
            }
        }
    }

    private val context: Context = reactContext.applicationContext
    private var messageId = 1

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
        Log.d(TAG, "stopQueue() called — requesting full queue stop.")
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
            val cursor = db.rawQuery(
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
        Log.d(TAG, "skipQueueRun() called — requesting skip of current run.")
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
        val payload = JSONObject().apply {
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

        val botThread = Thread {
            try {
                val entryPoint = Game(context)
                taskResult = entryPoint.start()
            } catch (e: Exception) {
                EventBus.getDefault().postSticky(ExceptionEvent(e))
                taskResult = TaskResult.Error(
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
     *
     * Ticks [Game.heartbeat] each iteration so the stall watchdog doesn't false-fire
     * during user-configured between-runs delays (which can be longer than the watchdog
     * timeout). The bot is genuinely alive and idle here, not stuck.
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

    @Subscribe
    fun onStartEvent(event: StartEvent) {
        if (event.message == "Entry Point ON") {
            // Acquire a PARTIAL_WAKE_LOCK for the entire bot session. This keeps the process-bucket
            // classification stable so Android's OomAdjuster can't mark the process as 'empty' and
            // send SIGKILL under memory pressure (observed as ApplicationExitInfo reason=SIGNALED,
            // subreason=TRIM_EMPTY on MuMu). The lock is released in the finally below regardless of
            // how the session ends (normal completion, stop request, unhandled exception).
            Game.acquireWakeLock(context)
            try {
            // Reset queue control flags at the start of every new session.
            queueStopRequested = false
            queueSkipRequested = false

            // Reset the log stream mute to ensure logs for the new run are broadcasted.
            LogStreamServer.resetMute()

            // Read queue settings from SQLite.
            val enableRunQueue = SettingsHelper.getBooleanSetting("runQueue", "enableRunQueue", false)
            val totalRuns = if (enableRunQueue) SettingsHelper.getIntSetting("runQueue", "totalRuns", 2) else 1
            val delayBetweenRuns = SettingsHelper.getIntSetting("runQueue", "delayBetweenRunsSeconds", 15)
            val stopOnError = SettingsHelper.getBooleanSetting("runQueue", "stopOnError", false)
            val reuseLastLaunchSetup = SettingsHelper.getBooleanSetting("runQueue", "reuseLastLaunchSetup", true)

            if (enableRunQueue) {
                MessageLog.i(TAG, "[QUEUE] Run queue enabled. Total runs: $totalRuns, delay: ${delayBetweenRuns}s, stopOnError: $stopOnError")
            }

            // --- Layer 4: auto-resume after process death ---
            // If a queue was running when the previous process was killed (TRIM_EMPTY,
            // watchdog self-restart, etc.), SQLite still has queueState.active=true with
            // the run number that was in flight. Skip past that run and pick up the next
            // one. Only applies when queueing is currently enabled AND the saved totalRuns
            // matches the current setting — if the user changed queue config after the
            // crash, the saved state is no longer applicable and we ignore it.
            val startFromRun: Int = run {
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
                val next = saved.currentRun + 1
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
                    "[RESUME] Detected interrupted queue from ${saved.ageMs / 60_000}m ago — resuming at run $next of $totalRuns (run ${saved.currentRun} was in flight when the previous process died).",
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
                    // Persist queue state so it can survive crashes.
                    saveQueueState(context, active = true, currentRun = i, totalRuns = totalRuns)
                    sendQueueProgressEvent(i, totalRuns, "starting")
                    MessageLog.i(TAG, "\n[QUEUE] ========================================")
                    MessageLog.i(TAG, "[QUEUE] Starting run $i of $totalRuns")
                    MessageLog.i(TAG, "[QUEUE] ========================================\n")
                }

                // Run the game.
                val result = runSingleGame()

                // Determine the effective result considering queue flags.
                val effectiveResult = when {
                    queueSkipRequested -> {
                        MessageLog.i(TAG, "[QUEUE] Run $i was skipped by queue.")
                        TaskResult.Success(TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE, "Run was skipped by queue.")
                    }
                    queueStopRequested -> {
                        MessageLog.i(TAG, "[QUEUE] Run $i was stopped by user (queue stop).")
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
                        // If user stopped and we didn't request skip, it's a full stop.
                        if (!queueSkipRequested) {
                            MessageLog.i(TAG, "[QUEUE] User stopped the bot. Exiting queue.")
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
                        // Breakpoints stop the queue — the user set them for a reason.
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

                    // Between-run cleanup: hint GC and refresh the watchdog heartbeat so the
                    // next run starts with lower PSS. Every KB we save here reduces the chance
                    // of a TRIM_EMPTY kill at end-of-next-run.
                    Game.cleanupBetweenRuns()

                    sendQueueProgressEvent(i, totalRuns, "navigating")
                    MessageLog.i(TAG, "[QUEUE] Navigating back to career start for next run...")

                    val navigator = CareerLaunchNavigator(context)
                    val navResult = navigator.navigate(reuseLastLaunchSetup)

                    if (!navResult.success) {
                        MessageLog.e(TAG, "[QUEUE] Navigation failed: ${navResult.failureReason}")
                        MessageLog.e(TAG, "[QUEUE] Last detected state: ${navResult.lastDetectedState}")
                        MessageLog.e(TAG, "[QUEUE] Failed transition: ${navResult.failedTransition}")
                        MessageLog.e(TAG, "[QUEUE] Recommended action: ${navResult.recommendedAction}")
                        if (navResult.screenshotPath.isNotEmpty()) {
                            MessageLog.e(TAG, "[QUEUE] Failure screenshot: ${navResult.screenshotPath}")
                        }
                        sendQueueProgressEvent(i, totalRuns, "queueFailed", TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name, navResult.failureReason)
                        break
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
                // Clear persisted queue state — queue finished normally.
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

        emitter?.emit(eventName, params)
    }

    /**
     * Listener function to call the inner event sending function in order to send the message back to the Javascript frontend.
     *
     * @param event The JSEvent object to parse its event name and message.
     */
    @Subscribe
    fun onJSEvent(event: JSEvent) {
        // Only send the event to the React Native frontend if it's not internal.
        // This prevents flooding the bridge during parallel operations where disableOutput is true.
        if (!event.isInternal) {
            sendEvent(event.eventName, event.message)
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
