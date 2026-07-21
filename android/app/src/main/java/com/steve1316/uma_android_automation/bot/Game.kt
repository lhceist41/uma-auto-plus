package com.steve1316.uma_android_automation.bot

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.DiscordUtils
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.MyAccessibilityService
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.CareerLaunchNavigator
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.StartModule
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.SkillDatabase
import com.steve1316.uma_android_automation.bot.Task
import com.steve1316.uma_android_automation.bot.campaigns.Trackblazer
import com.steve1316.uma_android_automation.bot.campaigns.UnityCup
import com.steve1316.uma_android_automation.bot.campaigns.UraFinale
import com.steve1316.uma_android_automation.components.ButtonCompleteCareer
import com.steve1316.uma_android_automation.components.ButtonLog
import com.steve1316.uma_android_automation.components.ButtonRest
import com.steve1316.uma_android_automation.components.ButtonSkillListFullStats
import com.steve1316.uma_android_automation.components.ButtonTraining
import com.steve1316.uma_android_automation.components.IconRaceDayRibbon
import com.steve1316.uma_android_automation.components.LabelConnecting
import com.steve1316.uma_android_automation.components.LabelNowLoading
import com.steve1316.uma_android_automation.components.LabelSkillListScreenSkillPoints
import com.steve1316.uma_android_automation.components.LabelSkillListScreenSkillPointsV2
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
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

    /** The Accessibility Service for performing screen gestures. Resolved per access so a service
     * rebind (after the emulator wipes the accessibility grant) is picked up immediately instead of
     * dispatching gestures through the dead instance. */
    val gestureUtils: MyAccessibilityService get() = MyAccessibilityService.getInstance()

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
            "Daily Races" -> com.steve1316.uma_android_automation.bot.misc.DailyRaceTask(this)
            "Team Trials" -> com.steve1316.uma_android_automation.bot.misc.TeamTrialsTask(this)
            else -> throw InterruptedException("Invalid scenario: $scenario")
        }

    /** True if the currently selected task is a misc (non-career) mode. */
    val isMiscTask: Boolean = task is com.steve1316.uma_android_automation.bot.misc.MiscTask

    /** The maximum number of connection error retry attempts allowed. */
    internal val maxConnectionErrorRetryAttempts: Int = 3

    /** The current number of connection error retry attempts. */
    internal var connectionErrorRetryAttempts: Int = 0

    /** The timestamp of the last connection error retry. */
    internal var lastConnectionErrorRetryTimeMs: Long = 0

    /** The cooldown time between connection error retries. */
    internal val connectionErrorRetryCooldownTimeMs: Long = 10000 // 10 seconds

    /** One extended hold per career when the connection-error ladder exhausts: flakes cluster
     * around the daily-reset window and pass in minutes, and burning the queue slot for one is
     * a bad trade (El Condor 2026-07-11: 173k-fan career lost 9 minutes before reset). */
    internal var connectionErrorExtendedWaitUsed: Boolean = false

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]Game"

        /** Package name of the Umamusume game (Global). The restart net relaunches this. If the JP
         * client (jp.co.cygames.umamusume) is ever targeted this needs to change. */
        const val GAME_PACKAGE: String = "com.cygames.umamusume"

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
                            // MessageLog goes on a throwaway thread: its global lock can be the
                            // exact thing that wedged (EventBus subscribers run inside it), so a
                            // blocked MessageLog.e here can neuter the watchdog before killProcess.
                            try {
                                Thread {
                                    try {
                                        MessageLog.e(TAG, msg)
                                    } catch (_: Throwable) {
                                    }
                                }.apply {
                                    isDaemon = true
                                    start()
                                }
                            } catch (_: Throwable) {
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
                if (existing != null && existing.isHeld) {
                    // Non-reference-counted: re-acquiring only re-arms the safety timeout. Called
                    // at every run boundary so a queue longer than one timeout window never
                    // silently loses its OOM protection mid-session.
                    existing.acquire(WAKE_LOCK_TIMEOUT_MS)
                    Log.i(TAG, "[WAKELOCK] Re-armed the ${WAKE_LOCK_TIMEOUT_MS / 1000 / 60}m safety timeout.")
                    return
                }
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
     * Checks if the bot is currently on the in-career main screen (a normal training turn OR a
     * mandatory race day). Kept in sync with the CareerLaunchNavigator's ACTIVE_TRAINING_MENU
     * detection: the Training/Rest buttons mark a normal turn, and the Race Day ribbon marks a
     * race-day turn (which has no Training/Rest button). Either means the bot is already in the
     * career and needs no auto-navigation.
     */
    private fun isOnTrainingMenu(): Boolean {
        val bitmap = imageUtils.getSourceBitmap()
        return ButtonTraining.check(imageUtils, sourceBitmap = bitmap) ||
            ButtonRest.check(imageUtils, sourceBitmap = bitmap) ||
            IconRaceDayRibbon.check(imageUtils, sourceBitmap = bitmap)
    }

    /**
     * Warns loudly when key racing-plan settings deviate from what the last-applied preset set.
     *
     * The Home preset apply stores a snapshot of its racing-plan stance; a later manual toggle
     * (or any stray write) silently reshapes racing for the whole career - e.g. mandatory
     * racing-plan mode flipping to false mid-queue stops the planned races being entered until
     * the career fails its fan goal. Log-only: the live settings still win; this just makes the
     * deviation impossible to miss.
     */
    private fun warnOnRacingConfigDrift() {
        val snapshotJson = SettingsHelper.getStringSetting("racing", "appliedRacingSnapshot")
        if (snapshotJson.isEmpty()) return
        try {
            val snapshot = JSONObject(snapshotJson)
            val drifts = mutableListOf<String>()
            val livePlanEnabled = SettingsHelper.getBooleanSetting("racing", "enableRacingPlan")
            val liveMandatory = SettingsHelper.getBooleanSetting("racing", "enableMandatoryRacingPlan")
            val livePlanCount =
                try {
                    val plan = SettingsHelper.getStringSetting("racing", "racingPlan")
                    if (plan.isEmpty()) 0 else JSONArray(plan).length()
                } catch (e: Exception) {
                    -1
                }
            if (snapshot.optBoolean("enableRacingPlan") != livePlanEnabled) {
                drifts.add("enableRacingPlan: preset=${snapshot.optBoolean("enableRacingPlan")}, now=$livePlanEnabled")
            }
            if (snapshot.optBoolean("enableMandatoryRacingPlan") != liveMandatory) {
                drifts.add("enableMandatoryRacingPlan: preset=${snapshot.optBoolean("enableMandatoryRacingPlan")}, now=$liveMandatory")
            }
            if (livePlanCount != -1 && snapshot.optInt("plannedRaceCount") != livePlanCount) {
                drifts.add("planned races: preset=${snapshot.optInt("plannedRaceCount")}, now=$livePlanCount")
            }
            if (drifts.isNotEmpty()) {
                MessageLog.w(
                    TAG,
                    "[CONFIG_DRIFT] Racing settings deviate from the applied preset \"${snapshot.optString("presetName")}\" " +
                        "(${snapshot.optString("scenario")}): ${drifts.joinToString("; ")}. If unintended, re-apply the preset on the Home screen.",
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "[DEBUG] warnOnRacingConfigDrift:: Could not parse the preset snapshot: ${e.message}")
        }
    }

    /**
     * Checks if the bot is sitting on one of the career-end screens: the End screen with the
     * Complete Career button, or the career-end "Learn" skill purchase screen (the skill list
     * without the in-career Log button).
     *
     * Startup auto-navigation must not run from these screens. The navigator's generic
     * Confirm/Close handling closes the skill list and its CAREER_SUMMARY handler presses
     * Complete Career, so a bot started here would complete the career with skill points unspent.
     * The campaign loop handles both screens itself: it buys per the careerComplete plan and then
     * finishes the career bookkeeping. Between-run queue navigation is unaffected - it runs from
     * StartModule after a completed run, where the skill plan has already executed.
     */
    private fun isOnCareerEndScreen(): Boolean {
        val bitmap = imageUtils.getSourceBitmap()
        if (ButtonCompleteCareer.check(imageUtils, sourceBitmap = bitmap)) {
            return true
        }
        val labelConfidence = 0.60
        return ButtonSkillListFullStats.check(imageUtils, sourceBitmap = bitmap) &&
            !ButtonLog.check(imageUtils, sourceBitmap = bitmap) &&
            (
                LabelSkillListScreenSkillPoints.check(imageUtils, sourceBitmap = bitmap, confidence = labelConfidence) ||
                    LabelSkillListScreenSkillPointsV2.check(imageUtils, sourceBitmap = bitmap, confidence = labelConfidence)
            )
    }

    /**
     * Verifies the Accessibility Service grant is still present and restores it if the emulator
     * wiped it.
     *
     * MuMu sporadically clears enabled_accessibility_services while the bot is running, which kills
     * all gesture injection while screen capture keeps working - taps and swipes silently stop
     * registering. With WRITE_SECURE_SETTINGS granted once over adb (pm grant <package>
     * android.permission.WRITE_SECURE_SETTINGS), the bot can rewrite the setting and bring its own
     * service back within a few seconds.
     *
     * @param waitForRebind Seconds to wait after restoring the setting for the service to rebind.
     * @return True if the service grant is present (or was restored), false otherwise.
     */
    fun ensureAccessibilityService(waitForRebind: Double = 3.0): Boolean {
        val expected = "${myContext.packageName}/com.steve1316.automation_library.utils.MyAccessibilityService"
        val enabled: String = Settings.Secure.getString(myContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        if (enabled.split(':').any { it.equals(expected, ignoreCase = true) }) {
            return true
        }

        MessageLog.e(TAG, "[ERROR] ensureAccessibilityService:: The Accessibility Service grant is gone (the emulator wiped it). Attempting to restore...")
        return try {
            val restored = if (enabled.isEmpty()) expected else "$enabled:$expected"
            Settings.Secure.putString(myContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, restored)
            Settings.Secure.putString(myContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            wait(waitForRebind, skipWaitingForLoading = true)
            MessageLog.w(TAG, "[WARN] ensureAccessibilityService:: Accessibility Service grant restored. Gestures should resume.")
            true
        } catch (e: SecurityException) {
            MessageLog.e(
                TAG,
                "[ERROR] ensureAccessibilityService:: Cannot restore the Accessibility Service - WRITE_SECURE_SETTINGS is not granted. " +
                    "Run once: adb shell pm grant ${myContext.packageName} android.permission.WRITE_SECURE_SETTINGS",
            )
            false
        }
    }

    /**
     * Forces the Accessibility Service to unbind and rebind by toggling its entry in the secure
     * ENABLED_ACCESSIBILITY_SERVICES setting off, then back on.
     *
     * [ensureAccessibilityService] only rewrites the setting when our service string is MISSING, but
     * MuMu has a nastier failure mode: it leaves the string intact (the service still reports as
     * bound in `dumpsys accessibility`) while silently killing gesture dispatch, so every tap/swipe
     * no-ops even though screen capture keeps working - an adb InputManager tap at the same
     * coordinate still lands, so only dispatchGesture is dead, not the OS input path. The string-only
     * check cannot see this, and re-writing the same value is ignored by the framework, so the entry
     * must actually be removed (letting the framework tear the dead instance down) and then re-added
     * to bind a fresh one. [gestureUtils] resolves via MyAccessibilityService.getInstance() on every
     * access, so it picks up the new instance automatically once it connects.
     *
     * @param waitForRebind Seconds to wait after re-adding the service for it to rebind.
     * @return True if the toggle was issued, false if WRITE_SECURE_SETTINGS is missing.
     */
    fun forceRebindAccessibilityService(waitForRebind: Double = 3.0): Boolean {
        val expected = "${myContext.packageName}/com.steve1316.automation_library.utils.MyAccessibilityService"
        return try {
            val current: String = Settings.Secure.getString(myContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val others: List<String> = current.split(':').filter { it.isNotEmpty() && !it.equals(expected, ignoreCase = true) }

            // Off: drop our service so the framework destroys the (dead-gesture) instance. Keep any
            // other enabled services intact.
            Settings.Secure.putString(myContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others.joinToString(":"))
            wait(1.0, skipWaitingForLoading = true)

            // On: re-add ours so the framework binds a fresh instance with a working gesture dispatcher.
            Settings.Secure.putString(myContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (others + expected).joinToString(":"))
            Settings.Secure.putString(myContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            wait(waitForRebind, skipWaitingForLoading = true)
            MessageLog.w(TAG, "[WARN] forceRebindAccessibilityService:: Toggled the Accessibility Service off->on to recover silently-dead gesture dispatch.")
            true
        } catch (e: SecurityException) {
            MessageLog.e(
                TAG,
                "[ERROR] forceRebindAccessibilityService:: Cannot toggle the Accessibility Service - WRITE_SECURE_SETTINGS is not granted. " +
                    "Run once: adb shell pm grant ${myContext.packageName} android.permission.WRITE_SECURE_SETTINGS",
            )
            false
        }
    }

    /**
     * Relaunches the Umamusume game from scratch as a last-resort recovery from a screen no handler
     * can identify or advance (e.g. the game itself soft-locking, distinct from MuMu's gesture death
     * which [forceRebindAccessibilityService] handles). Fires the game's launcher intent with
     * CLEAR_TASK so the framework tears down the existing (wedged) task and recreates the entry
     * Activity, then waits for the title/loading screens to settle. Career progress is saved
     * server-side each turn, so the game comes back on its Continue-Career flow, which the campaign's
     * lobby re-entry path resumes in place - no career is lost (validated manually 2026-07-11 via an
     * adb force-stop + relaunch that resumed El Condor's career).
     *
     * An ordinary app cannot force-stop another package without root, so this is a best-effort
     * relaunch rather than a hard kill; it recovers a UI/task soft-lock but may not reset a crashed
     * native renderer. Falls through (returns false) if the launcher intent cannot be resolved, so
     * the caller's normal stop still applies - no new dead-end.
     *
     * @param waitAfterLaunch Seconds to wait after firing the intent for the game to come up.
     * @return True if the relaunch intent was dispatched, false if it could not be resolved.
     */
    fun restartGame(waitAfterLaunch: Double = 20.0): Boolean {
        val launchIntent: Intent? = myContext.packageManager.getLaunchIntentForPackage(GAME_PACKAGE)
        if (launchIntent == null) {
            MessageLog.e(TAG, "[ERROR] restartGame:: Could not resolve a launcher intent for $GAME_PACKAGE. Is the game installed under that package? Skipping the restart.")
            return false
        }
        return try {
            // CLEAR_TASK | NEW_TASK: destroy the existing (wedged) task and start the entry Activity fresh.
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            MessageLog.w(TAG, "[RECOVERY] Relaunching the game ($GAME_PACKAGE) from scratch to recover from an unrecognized/soft-locked screen. The career resumes via Continue Career.")
            myContext.startActivity(launchIntent)
            wait(waitAfterLaunch, skipWaitingForLoading = true)
            true
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] restartGame:: Failed to relaunch the game: ${e.message}")
            false
        }
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
        // toDoubleOrNull (not toDouble) — an empty/unset manual-scale setting threw NumberFormatException
        // and crashed the bot at startup. Mirrors the fix already in CustomImageUtils.
        val templateMatchCustomScale = SettingsHelper.getStringSetting("debug", "templateMatchCustomScale").toDoubleOrNull() ?: 1.0
        if (templateMatchCustomScale != 1.0) {
            MessageLog.w(TAG, "[WARN] Manual scale has been set to $templateMatchCustomScale")
        }
        MessageLog.w(
            TAG,
            "[WARN] ⚠️ Note that certain Android notification styles (like banners) are big enough that they cover the area that contains the Mood which will interfere with mood recovery logic in the beginning.",
        )
        val packageInfo = myContext.packageManager.getPackageInfo(myContext.packageName, 0)
        MessageLog.i(TAG, "[INFO] Bot version: ${packageInfo.versionName} (${packageInfo.versionCode})\n\n")

        // Start debug tests here if enabled, BEFORE any auto-navigation, so a test runs on the
        // screen the user has open (e.g. the career-end "Learn" screen) instead of being clobbered
        // by the CareerLaunchNavigator. If any test runs, the bot is done.
        // A small delay here to ensure any notifications are out of the way.
        wait(3.0)

        // The emulator can wipe the Accessibility grant even while idle - without it no gesture
        // lands. Verify (and restore if possible) before doing anything else.
        if (!ensureAccessibilityService()) {
            return TaskResult.Error(
                TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION,
                "The Accessibility Service is disabled and could not be restored automatically. Re-enable it in the Android settings or grant WRITE_SECURE_SETTINGS (see log).",
            )
        }

        if (task.startTests()) {
            MessageLog.i(TAG, "[INFO] Debug test(s) complete. Stopping bot...")
            return TaskResult.Success(TaskResultCode.TASK_RESULT_COMPLETE, "Debug tests completed.")
        }

        warnOnRacingConfigDrift()

        // Auto-navigate to the training menu if the bot is not already there.
        // This allows starting the bot from the home screen, scenario select, or any
        // other screen in the career launch flow - the navigator will find its way.
        //
        // Misc tasks (Daily Races, Team Trials) skip this entirely - they start from
        // the game's Home Screen and have their own state machines to navigate from
        // there to their target mode. The user is expected to have the game open on
        // the Home Screen (or any screen with the bottom nav visible) when starting.
        if (!isMiscTask && !isOnTrainingMenu()) {
            if (isOnCareerEndScreen()) {
                // Started on a career-end screen (End screen or the Learn skill list). The
                // campaign loop buys skills and completes the career bookkeeping from here;
                // the navigator would instead close the skill list and press Complete Career
                // with the points unspent.
                MessageLog.i(TAG, "[INFO] Bot started on a career-end screen. Skipping auto-navigation; the campaign will buy skills and finish the career.")
            } else {
                MessageLog.i(TAG, "[INFO] Bot is not on the training menu. Attempting auto-navigation...")
                val navigator = CareerLaunchNavigator(myContext)
                val reuseSetup = SettingsHelper.getBooleanSetting("runQueue", "reuseLastLaunchSetup", true)
                // Single (non-queue) runs verify Trainee Select against the applied preset's
                // trainee: the game preselects whoever was picked last, and an interrupted queue
                // once left El Condor preselected while Rudolf's preset was applied - this launch
                // path would have run her career under his settings (2026-07-09, twice). Queue
                // runs keep their rotation-managed targeting and pass no expectation.
                val singleRun = !SettingsHelper.getBooleanSetting("runQueue", "enableRunQueue", false)
                val expectedTrainee = if (singleRun) SettingsHelper.getStringSetting("general", "appliedPresetTrainee") else ""
                val expectedExcludes = if (singleRun) SettingsHelper.getStringSetting("general", "appliedPresetTraineeExcludes") else ""
                if (expectedTrainee.isNotBlank()) {
                    MessageLog.i(TAG, "[INFO] Single-run launch will verify Trainee Select against '$expectedTrainee' (applied preset).")
                }
                val navResult = navigator.navigate(reuseSetup, singleRunTrainee = expectedTrainee, singleRunTraineeExcludes = expectedExcludes)
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
        } else if (isMiscTask) {
            MessageLog.i(TAG, "[INFO] Misc task mode (\"$scenario\"). Starting from Home Screen - bot's state machine will navigate from there.")
        }

        // Debug tests (if any were enabled) already ran and returned above; this is a normal run.
        // Send Discord notification that the run has started.
        if (DiscordUtils.enableDiscordNotifications) {
            val enableRemoteLogViewer = SettingsHelper.getBooleanSetting("debug", "enableRemoteLogViewer", false)
            var logViewerString = ""
            if (enableRemoteLogViewer) {
                // Notify the user that the Remote Log Viewer is enabled and is viewable at the indicated address.
                val port = SettingsHelper.getIntSetting("debug", "remoteLogViewerPort", 9000)
                // The viewer now binds to loopback (127.0.0.1) for safety, so the device's LAN IP is no
                // longer reachable - advertise the adb-forward path instead of a dead LAN URL.
                logViewerString = "\nRemote Log Viewer enabled (loopback). From a computer: adb forward tcp:$port tcp:$port then open http://localhost:$port"
            }
            DiscordUtils.queue.add("```diff\n+ ${MessageLog.getSystemTimeString()} Bot run started! Scenario: $scenario```$logViewerString")
        }
        // CAREER ATTACHMENT: the bot is about to hand control to the career task, which is the
        // first point that proves a real career exists - exactly one of "already on the training
        // menu", "started on a career-end screen", or "auto-navigation reported reaching the
        // training menu" holds here. This is the ONLY place a spark reroll transaction is
        // created. Arming any earlier (the queue run loop used to) puts the transaction on the
        // wrong side of the cold-start launch navigation, whose legitimate pass through the
        // game's Home screen then destroyed it and left a whole live career unable to price its
        // redraw (2026-07-19). Misc tasks are not careers and never arm.
        if (!isMiscTask) {
            val queueRun =
                if (SettingsHelper.getBooleanSetting("runQueue", "enableRunQueue", false)) {
                    SettingsHelper.getIntSetting("queueState", "currentRun", 0)
                } else {
                    null
                }
            SparkRerollGate.beginCareer(
                nonce = java.util.UUID.randomUUID().toString().substring(0, 8),
                queueRun = queueRun,
                nowMs = System.currentTimeMillis(),
            )
            // Capture the launch-critical config identity for this career. The React Start barrier
            // verified this same settingsRevision on disk before launching; logging it here makes
            // the cross-layer identity explicit, so a mid-career settings drift is visible.
            val runConfig = RunConfigSnapshot.armFromSettings(System.currentTimeMillis())
            MessageLog.i(TAG, "[CONFIG_DRIFT] [KOTLIN] loaded_run_config ${RunConfigSnapshot.describe(runConfig)}")
        }

        // Read the per-run safety timeout from the run queue settings. Defaults to 180 min
        // (3 hours), matching the TS-side default. Single-run sessions (queue disabled)
        // also use this same setting since they call Game.start() the same way.
        val maxRuntimeMinutes = SettingsHelper.getIntSetting("runQueue", "maxRuntimePerRunMinutes", 180)
        MessageLog.i(TAG, "[INFO] Per-run max runtime timeout: $maxRuntimeMinutes minutes.")
        val taskResult: TaskResult = task.start(maxRuntimeMinutes = maxRuntimeMinutes)

        MessageLog.i(TAG, "[INFO] Total runtime of ${MessageLog.formatElapsedTime(startTime, System.currentTimeMillis())} and stopped at ${MessageLog.getSystemTimeString()}.")

        // Wait to make sure Discord webhook message queue gets fully processed before terminating Bot Thread.
        if (DiscordUtils.enableDiscordNotifications) {
            wait(1.0, skipWaitingForLoading = true)
        }

        return taskResult
    }
}
