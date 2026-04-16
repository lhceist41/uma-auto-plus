package com.steve1316.uma_android_automation.bot

import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
import android.util.Log
import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.BotService
import com.steve1316.uma_android_automation.CareerLaunchNavigator
import com.steve1316.uma_android_automation.StartModule
import com.steve1316.uma_android_automation.components.ButtonTraining
import com.steve1316.uma_android_automation.components.ButtonRest
import com.steve1316.automation_library.utils.DiscordUtils
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.MyAccessibilityService
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.SkillDatabase
import com.steve1316.uma_android_automation.bot.Task
import com.steve1316.uma_android_automation.bot.campaigns.Trackblazer
import com.steve1316.uma_android_automation.bot.campaigns.UnityCup
import com.steve1316.uma_android_automation.bot.campaigns.UraFinale
import com.steve1316.uma_android_automation.components.LabelConnecting
import com.steve1316.uma_android_automation.components.LabelNowLoading
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.opencv.core.Point
import java.text.DecimalFormat
import kotlin.intArrayOf

/**
 * Main driver for bot activity and navigation.
 *
 * @property myContext The Android [Context] for the application.
 */
class Game(val myContext: Context) {
    /** The current Android notification message to display. */
    var notificationMessage: String = ""

    /** The utility class for image processing and template matching. */
    val imageUtils: CustomImageUtils = CustomImageUtils(myContext, this)

    /** The Accessibility Service for performing screen gestures. */
    val gestureUtils: MyAccessibilityService = MyAccessibilityService.getInstance()

    /** The database for skill-related information. */
    val skillDatabase: SkillDatabase = SkillDatabase(this)

    /** The formatter for decimal values. */
    val decimalFormat = DecimalFormat("#.##")

    /** The current campaign scenario (e.g., "URA Finale", "Unity Cup", "Trackblazer"). */
    val scenario: String = SettingsHelper.getStringSetting("general", "scenario")

    /** Whether debug mode is enabled for additional logging and saving debugging images to storage. */
    val debugMode: Boolean = SettingsHelper.getBooleanSetting("debug", "enableDebugMode")

    /** Whether to check for certain popups to stop at during execution. */
    val enablePopupCheck: Boolean = SettingsHelper.getBooleanSetting("general", "enablePopupCheck")

    /** The default wait delay between common actions. */
    val waitDelay: Double = SettingsHelper.getDoubleSetting("general", "waitDelay")

    /** The wait delay specifically for dialog interactions. */
    val dialogWaitDelay: Double = SettingsHelper.getDoubleSetting("general", "dialogWaitDelay")

    /** Holds the task instance corresponding to the selected scenario. */
    val task: Task =
        when (scenario) {
            "URA Finale" -> UraFinale(this)
            "Unity Cup" -> UnityCup(this)
            "Trackblazer" -> Trackblazer(this)
            else -> throw InterruptedException("Invalid scenario: $scenario")
        }

    /** The maximum number of connection error retry attempts allowed. */
    internal val maxConnectionErrorRetryAttempts: Int = 3

    /** The current number of connection error retry attempts. */
    internal var connectionErrorRetryAttempts: Int = 0

    /** The timestamp of the last connection error retry. */
    internal var lastConnectionErrorRetryTimeMs: Long = 0

    /** The cooldown time between connection error retries. */
    internal val connectionErrorRetryCooldownTimeMs: Long = 10000 // 10 seconds

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]Game"

        // --- Stall watchdog ---
        // On MuMu (and other emulators) the AccessibilityService's gesture injector can
        // deadlock under load, causing the system InputDispatcher's queue to back up
        // until the whole emulator appears frozen. To recover automatically, the bot
        // loop updates a heartbeat every time it makes forward progress. A background
        // coroutine watches that heartbeat and, if nothing has moved for
        // HEARTBEAT_TIMEOUT_MS while the bot is supposedly running, kills the process.
        // The AccessibilityService is sticky so Android restarts it within ~1 second
        // and input dispatch unfreezes.

        /** Milliseconds since boot of the last recorded forward progress. */
        @Volatile
        private var lastHeartbeatMs: Long = System.currentTimeMillis()

        /** The watchdog coroutine job, or null if not running. */
        @Volatile
        private var watchdogJob: Job? = null

        /**
         * Kill the process if no heartbeat in this many ms while the bot is running.
         * Set to 3 minutes so popup animations and dialog chains don't false-trigger.
         */
        private const val HEARTBEAT_TIMEOUT_MS: Long = 180_000L

        /** How often the watchdog checks the heartbeat. */
        private const val WATCHDOG_INTERVAL_MS: Long = 5_000L

        /**
         * Record forward progress. Called at safe boundaries in the bot loop
         * (e.g., every tick of [wait]). Cheap, just a volatile store.
         */
        fun heartbeat() {
            lastHeartbeatMs = System.currentTimeMillis()
        }

        /**
         * Start the watchdog. Idempotent: if already running, does nothing. Runs for
         * the lifetime of the process; the check inside accounts for the bot being
         * stopped/restarted.
         */
        private fun startWatchdog() {
            if (watchdogJob?.isActive == true) return
            heartbeat()
            watchdogJob =
                CoroutineScope(Dispatchers.Default).launch {
                    while (isActive) {
                        delay(WATCHDOG_INTERVAL_MS)
                        if (!BotService.isRunning) {
                            // Bot isn't running, reset so we don't fire immediately on resume.
                            heartbeat()
                            continue
                        }
                        val age = System.currentTimeMillis() - lastHeartbeatMs
                        if (age >= HEARTBEAT_TIMEOUT_MS) {
                            val msg =
                                "[WATCHDOG] No bot progress for ${age / 1000}s while BotService.isRunning=true. " +
                                    "Likely a stalled gesture injector / input-dispatch freeze. Self-restarting process to recover."
                            Log.e(TAG, msg)
                            try {
                                MessageLog.e(TAG, msg)
                            } catch (_: Exception) {
                                // MessageLog may be unusable if the JS bridge is gone; don't block the restart on it.
                            }
                            // Give the log line a brief window to flush, then self-terminate.
                            // AccessibilityService is sticky, Android will restart it.
                            delay(250)
                            android.os.Process.killProcess(android.os.Process.myPid())
                            return@launch
                        }
                    }
                }
        }

        // --- WakeLock ---
        // Paired with the FGS foregroundServiceType="dataSync" on BotService in the manifest.
        // Holding a PARTIAL_WAKE_LOCK while a run is active keeps the process-bucket
        // classification stable so Android's OomAdjuster doesn't mark us as 'empty' and SIGKILL
        // the process to reclaim memory under TRIM_EMPTY. The lock has a safety timeout so it
        // can't leak indefinitely if the release path is skipped.

        @Volatile
        private var wakeLock: PowerManager.WakeLock? = null

        /** WakeLock tag (visible in `adb shell dumpsys power`). */
        private const val WAKE_LOCK_TAG: String = "UmaAutoPlus:BotRun"

        /** Hard cap on a single WakeLock acquisition. If we're still running after 6h something's wrong. */
        private const val WAKE_LOCK_TIMEOUT_MS: Long = 6 * 60 * 60 * 1000L

        /**
         * Acquire a partial wake lock. Safe to call repeatedly: if one is already held,
         * this is a no-op. Call from the bot-run entry point.
         */
        @Synchronized
        fun acquireWakeLock(context: Context) {
            try {
                val existing = wakeLock
                if (existing != null && existing.isHeld) return
                val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm == null) {
                    Log.w(TAG, "[WAKELOCK] PowerManager unavailable; cannot acquire wake lock.")
                    return
                }
                val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                lock.setReferenceCounted(false)
                lock.acquire(WAKE_LOCK_TIMEOUT_MS)
                wakeLock = lock
                Log.i(TAG, "[WAKELOCK] Acquired PARTIAL_WAKE_LOCK (timeout ${WAKE_LOCK_TIMEOUT_MS / 1000 / 60}m).")
            } catch (e: Throwable) {
                // Never let wake-lock plumbing crash the bot.
                Log.w(TAG, "[WAKELOCK] Acquire failed: ${e.message}")
            }
        }

        /** Release the wake lock if held. Safe to call multiple times. */
        @Synchronized
        fun releaseWakeLock() {
            try {
                val lock = wakeLock ?: return
                if (lock.isHeld) {
                    lock.release()
                    Log.i(TAG, "[WAKELOCK] Released.")
                }
                wakeLock = null
            } catch (e: Throwable) {
                Log.w(TAG, "[WAKELOCK] Release failed: ${e.message}")
                wakeLock = null
            }
        }

        // --- Between-run cleanup ---
        // Called between queued runs to reduce RSS drift. Hints a GC at the known idle
        // boundary to lower peak PSS before the next run's allocation spike.

        /**
         * Reset per-run soft state and suggest a GC. Intended for between-run boundaries in the
         * queue loop; do NOT call mid-run. Cheap; never throws.
         */
        fun cleanupBetweenRuns() {
            try {
                // Refresh the watchdog heartbeat so it doesn't false-trigger during the cleanup
                // window (the bot loop is not calling wait() between runs).
                heartbeat()
                // Suggest a GC. Generally discouraged in hot paths, but fine at this idle boundary.
                System.gc()
                Log.i(TAG, "[CLEANUP] Between-run cleanup completed.")
            } catch (_: Throwable) {
                // Cleanup must never be the thing that kills the bot.
            }
        }
    }

    // Initialize Discord settings from SQLite and start the stall watchdog.
    init {
        DiscordUtils.enableDiscordNotifications = SettingsHelper.getBooleanSetting("discord", "enableDiscordNotifications", false)
        if (DiscordUtils.enableDiscordNotifications) {
            try {
                DiscordUtils.discordToken = SettingsHelper.getStringSetting("discord", "discordToken")
                DiscordUtils.discordUserID = SettingsHelper.getStringSetting("discord", "discordUserID")
            } catch (e: Exception) {
                Log.w(TAG, "[WARN] Failed to read Discord settings: ${e.message}")
                DiscordUtils.enableDiscordNotifications = false
            }
        }

        // Kick the watchdog. This is idempotent; if the user starts a new run in the same
        // process, the existing watchdog just picks up where it left off.
        startWatchdog()
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Waits the specified seconds to account for ping or loading.
     *
     * It also checks for interruption every 100ms to allow faster interruption and checks if the game is still in the middle of loading.
     *
     * @param seconds Number of seconds to pause execution.
     * @param skipWaitingForLoading If true, then it will skip the loading check. Defaults to false.
     */
    fun wait(seconds: Double, skipWaitingForLoading: Boolean = false) {
        val totalMillis = (seconds * 1000).toLong()
        // Check for interruption every 100ms.
        val checkInterval = 100L

        var remainingMillis = totalMillis
        while (remainingMillis > 0) {
            // Record forward progress for the stall watchdog. Putting it here means
            // every tick of any wait() call keeps the heartbeat fresh, so the watchdog
            // only fires if we're genuinely stuck outside the wait loop for 45s+
            // (most commonly a deadlocked gesture injector).
            heartbeat()

            if (!BotService.isRunning) {
                throw InterruptedException()
            }

            // Check queue control flags at safe boundaries.
            if (StartModule.queueStopRequested) {
                throw InterruptedException()
            }
            if (StartModule.queueSkipRequested) {
                throw InterruptedException()
            }

            val sleepTime = minOf(checkInterval, remainingMillis)
            runBlocking {
                delay(sleepTime)
            }
            remainingMillis -= sleepTime
        }

        if (!skipWaitingForLoading) {
            // Check if the game is still loading as well.
            waitForLoading()
        }
    }

    /**
     * Waits for the game to finish loading.
     *
     * Note that this function is responsible for dictating how fast the bot will run so adjusting this should be done with caution.
     */
    fun waitForLoading() {
        var loadingCounter = 0
        while (checkLoading(suppressLogging = loadingCounter % 10 != 0)) {
            // Avoid an infinite loop by setting the flag to true.
            wait(waitDelay, skipWaitingForLoading = true)
            loadingCounter++
            if (loadingCounter >= 20) {
                loadingCounter = 0
            }
        }
    }

    /**
     * Finds and taps the specified image.
     *
     * @param imageName Name of the button image file in the /assets/images/ folder.
     * @param sourceBitmap The source bitmap to find the image on. This is optional and defaults to null which will fetch its own source bitmap.
     * @param tries Number of tries to find the specified button. Defaults to 3.
     * @param region Specify the region consisting of (x, y, width, height) of the source screenshot to template match. Defaults to (0, 0, 0, 0) which is equivalent to searching the full image.
     * @param taps Specify the number of taps on the specified image. Defaults to 1.
     * @param suppressError Whether to suppress saving error messages to the log in failing to find the button. Defaults to false.
     * @return True if the button was found and clicked. False otherwise.
     */
    fun findAndTapImage(imageName: String, sourceBitmap: Bitmap? = null, tries: Int = 3, region: IntArray = intArrayOf(0, 0, 0, 0), taps: Int = 1, suppressError: Boolean = false): Boolean {
        if (debugMode) {
            MessageLog.d(TAG, "[DEBUG] findAndTapImage:: Now attempting to find and click the \"$imageName\" button.")
        }

        val tempLocation: Point? =
            if (sourceBitmap == null) {
                imageUtils.findImage(imageName, tries = tries, region = region, suppressError = suppressError).first
            } else {
                imageUtils.findImageWithBitmap(imageName, sourceBitmap, region = region, suppressError = suppressError)
            }

        return if (tempLocation != null) {
            Log.d(TAG, "[DEBUG] findAndTapImage:: Found and going to tap: $imageName")
            tap(tempLocation.x, tempLocation.y, imageName, taps = taps)
            true
        } else {
            false
        }
    }

    /**
     * Performs a tap on the screen at the coordinates and then will wait until the game processes the server request and gets a response back.
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @param imageName The template image name to use for tap location randomization.
     * @param taps The number of taps.
     * @param ignoreWaiting Flag to ignore checking if the game is busy loading.
     */
    fun tap(x: Double, y: Double, imageName: String? = null, taps: Int = 1, ignoreWaiting: Boolean = false) {
        // Perform the tap.
        gestureUtils.tap(x, y, imageName, taps = taps)

        // Mark forward progress for the watchdog. If the gesture injector deadlocked
        // the call above would have blocked past the watchdog threshold and we'd
        // already be restarting; if it returned, we made progress.
        heartbeat()

        if (!ignoreWaiting) {
            // Now check if the game is waiting for a server response from the tap and wait if necessary.
            wait(0.20)
            waitForLoading()
        }
    }

    /**
     * Checks if the bot is at a "Now Loading..." screen or if the game is awaiting a server response.
     *
     * This may cause significant delays in normal bot processes.
     *
     * @param suppressLogging Whether to suppress logging for this function. Defaults to false.
     * @return True if the game is still loading or is awaiting a server response. Otherwise, false.
     */
    fun checkLoading(suppressLogging: Boolean = false): Boolean {
        if (!suppressLogging) MessageLog.i(TAG, "[LOADING] Now checking if the game is still loading...")
        val sourceBitmap = imageUtils.getSourceBitmap()
        return if (LabelConnecting.check(imageUtils, sourceBitmap = sourceBitmap)) {
            if (!suppressLogging) MessageLog.i(TAG, "[LOADING] Detected that the game is awaiting a response from the server from the \"Connecting\" text at the top of the screen. Waiting...")
            true
        } else if (LabelNowLoading.check(imageUtils, sourceBitmap = sourceBitmap)) {
            if (!suppressLogging) MessageLog.i(TAG, "[LOADING] Detected that the game is still loading from the \"Now Loading\" text at the bottom of the screen. Waiting...")
            true
        } else {
            false
        }
    }

    /**
     * Checks if the bot is currently on the in-career training menu.
     * Uses the same detection as the CareerLaunchNavigator's ACTIVE_TRAINING_MENU state:
     * looks for the Training or Rest buttons which are unique to that screen.
     */
    private fun isOnTrainingMenu(): Boolean {
        val bitmap = imageUtils.getSourceBitmap()
        return ButtonTraining.check(imageUtils, sourceBitmap = bitmap) ||
               ButtonRest.check(imageUtils, sourceBitmap = bitmap)
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Begins automation here.
     *
     * @return The [TaskResult] from the task's execution.
     */
    fun start(): TaskResult {
        MessageLog.i(TAG, "Started at ${MessageLog.getSystemTimeString()}.")
        val startTime: Long = System.currentTimeMillis()

        // Print current app settings at the start of the run.
        try {
            val formattedSettingsString = SettingsHelper.getStringSetting("misc", "formattedSettingsString")
            MessageLog.i(TAG, "\n[SETTINGS] Current Bot Configuration:")
            MessageLog.i(TAG, "=====================================")
            formattedSettingsString.split("\n").forEach { line ->
                if (line.isNotEmpty()) {
                    MessageLog.i(TAG, line)
                }
            }
            MessageLog.i(TAG, "=====================================\n")
        } catch (e: Exception) {
            MessageLog.w(TAG, "[WARN] start:: Failed to load formatted settings from SQLite: ${e.message}")
            MessageLog.i(TAG, "[INFO] Using fallback settings display...")

            // Fallback to basic settings display if formatted string is not available.
            MessageLog.i(TAG, "[INFO] Scenario: $scenario")
            MessageLog.i(TAG, "[INFO] Debug Mode: $debugMode")
        }

        // Print device and version information.
        MessageLog.i(TAG, "[INFO] Device Information: ${SharedData.displayWidth}x${SharedData.displayHeight}, DPI ${SharedData.displayDPI}")
        val isConfig1 = SharedData.displayWidth == 1080 && SharedData.displayHeight == 1920 && SharedData.displayDPI == 240
        val isConfig2 = SharedData.displayWidth == 1080 && SharedData.displayHeight == 2340 && SharedData.displayDPI == 450
        if (!isConfig1 && !isConfig2) {
            MessageLog.w(
                TAG,
                "[WARN] ⚠️ Bot performance will be severely degraded since display configuration is not 1080x1920 @ 240 DPI or 1080x2340 @ 450 DPI unless an appropriate scale is set for your device.",
            )
        }
        if (debugMode) MessageLog.w(TAG, "[WARN] ⚠️ Debug Mode is enabled. All bot operations will be significantly slower as a result.")
        if (SettingsHelper.getStringSetting("debug", "templateMatchCustomScale").toDouble() != 1.0) {
            MessageLog.w(
                TAG,
                "[WARN] Manual scale has been set to ${SettingsHelper.getStringSetting("debug", "templateMatchCustomScale").toDouble()}",
            )
        }
        MessageLog.w(
            TAG,
            "[WARN] ⚠️ Note that certain Android notification styles (like banners) are big enough that they cover the area that contains the Mood which will interfere with mood recovery logic in the beginning.",
        )
        val packageInfo = myContext.packageManager.getPackageInfo(myContext.packageName, 0)
        MessageLog.i(TAG, "[INFO] Bot version: ${packageInfo.versionName} (${packageInfo.versionCode})\n\n")

        // Start debug tests here if enabled. Otherwise, proceed with regular bot operations.
        // A small delay here to ensure any notifications are out of the way.
        wait(3.0)

        // Auto-navigate to the training menu if the bot is not already there.
        // This allows starting the bot from the home screen, scenario select, or any
        // other screen in the career launch flow — the navigator will find its way.
        if (!isOnTrainingMenu()) {
            MessageLog.i(TAG, "[INFO] Bot is not on the training menu. Attempting auto-navigation...")
            val navigator = CareerLaunchNavigator(myContext)
            val reuseSetup = SettingsHelper.getBooleanSetting("runQueue", "reuseLastLaunchSetup", true)
            val navResult = navigator.navigate(reuseSetup)
            if (!navResult.success) {
                MessageLog.e(TAG, "[INFO] Auto-navigation failed: ${navResult.failureReason}")
                MessageLog.e(TAG, "[INFO] Last state: ${navResult.lastDetectedState}, transition: ${navResult.failedTransition}")
                MessageLog.e(TAG, "[INFO] ${navResult.recommendedAction}")
                return TaskResult.Error(
                    TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED,
                    "Auto-navigation to training menu failed: ${navResult.failureReason}",
                )
            }
            MessageLog.i(TAG, "[INFO] Auto-navigation complete. Bot is now on the training menu.")
            wait(2.0)
        }

        val taskResult: TaskResult
        if (!task.startTests()) {
            // Send Discord notification that the run has started.
            if (DiscordUtils.enableDiscordNotifications) {
                val enableRemoteLogViewer = SettingsHelper.getBooleanSetting("debug", "enableRemoteLogViewer", false)
                var logViewerString = ""
                if (enableRemoteLogViewer) {
                    // Notify the user that the Remote Log Viewer is enabled and is viewable at the indicated address.
                    val port = SettingsHelper.getIntSetting("debug", "remoteLogViewerPort", 9000)
                    val ipAddress = com.steve1316.uma_android_automation.utils.LogStreamServer.getDeviceIpAddress(myContext)
                    val finalIpAddress = if (ipAddress == "10.0.2.15") "localhost" else ipAddress
                    logViewerString = "Remote Log Viewer is enabled at http://$finalIpAddress:$port"
                }
                DiscordUtils.queue.add("```diff\n+ ${MessageLog.getSystemTimeString()} Bot run started! Scenario: $scenario```$logViewerString")
            }
            // Read the per-run safety timeout from the run queue settings. Defaults to 180 min
            // (3 hours), matching the TS-side default. Single-run sessions (queue disabled)
            // also use this same setting since they call Game.start() the same way.
            val maxRuntimeMinutes = SettingsHelper.getIntSetting("runQueue", "maxRuntimePerRunMinutes", 180)
            MessageLog.i(TAG, "[INFO] Per-run max runtime timeout: $maxRuntimeMinutes minutes.")
            taskResult = task.start(maxRuntimeMinutes = maxRuntimeMinutes)
        } else {
            taskResult = TaskResult.Success(TaskResultCode.TASK_RESULT_COMPLETE, "Debug tests completed.")
        }

        MessageLog.i(TAG, "[INFO] Total runtime of ${MessageLog.formatElapsedTime(startTime, System.currentTimeMillis())} and stopped at ${MessageLog.getSystemTimeString()}.")

        // Wait to make sure Discord webhook message queue gets fully processed before terminating Bot Thread.
        if (DiscordUtils.enableDiscordNotifications) {
            wait(1.0, skipWaitingForLoading = true)
        }

        return taskResult
    }
}
