package com.steve1316.uma_android_automation.bot

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.DiscordUtils
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.MyAccessibilityService
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.CareerLaunchNavigator
import com.steve1316.uma_android_automation.DebugTestGate
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.SessionTally
import com.steve1316.uma_android_automation.StartModule
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.SkillDatabase
import com.steve1316.uma_android_automation.bot.Task
import com.steve1316.uma_android_automation.bot.campaigns.GrandConcert
import com.steve1316.uma_android_automation.bot.campaigns.Trackblazer
import com.steve1316.uma_android_automation.bot.campaigns.UnityCup
import com.steve1316.uma_android_automation.bot.campaigns.UraFinale
import com.steve1316.uma_android_automation.components.ButtonBack
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonCompleteCareer
import com.steve1316.uma_android_automation.components.ButtonLearn
import com.steve1316.uma_android_automation.components.ButtonLog
import com.steve1316.uma_android_automation.components.ButtonRaceListFullStats
import com.steve1316.uma_android_automation.components.ButtonRest
import com.steve1316.uma_android_automation.components.ButtonSkillListFullStats
import com.steve1316.uma_android_automation.components.ButtonTraining
import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.components.IconRaceDayRibbon
import com.steve1316.uma_android_automation.components.LabelConnecting
import com.steve1316.uma_android_automation.components.LabelNowLoading
import com.steve1316.uma_android_automation.components.LabelSkillListScreenSkillPoints
import com.steve1316.uma_android_automation.components.LabelSkillListScreenSkillPointsV2
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import com.steve1316.uma_android_automation.utils.ProgressTracker
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.TrainingSelectionProbe
import com.steve1316.uma_android_automation.utils.grandConcertLessonConfirmationPresent
import com.steve1316.uma_android_automation.utils.grandConcertLessonListPresent
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
 * @property careerInFlight True when this run re-enters a career already in the game's slot (the
 *   queue's resumed in-flight career, or a run played again after an error), so its start never
 *   reads a Skip pill as the launch Quick Mode prompt.
 */
class Game(val myContext: Context, val diagnosticSelection: DebugTestGate.Selection? = null, val careerInFlight: Boolean = false) {
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

    /** The current campaign scenario (e.g., "URA Finale", "Unity Cup", "Trackblazer").
     * Normalized on read so every accepted Grand Concert spelling ("Grand Live", the punctuated
     * title variants, the client's own "Our Grand Concert") dispatches, persists, and logs under
     * the one canonical key. Other scenario strings pass through untouched. */
    val scenario: String = GrandConcertScenario.normalizeScenarioKey(diagnosticSelection?.scenario ?: SettingsHelper.getStringSetting("general", "scenario"))

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
            GrandConcertScenario.KEY -> GrandConcert(this)
            "Daily Races" -> com.steve1316.uma_android_automation.bot.misc.DailyRaceTask(this)
            "Team Trials" -> com.steve1316.uma_android_automation.bot.misc.TeamTrialsTask(this)
            else -> throw InterruptedException("Invalid scenario: $scenario")
        }

    /** True if the currently selected task is a misc (non-career) mode. */
    val isMiscTask: Boolean = task is com.steve1316.uma_android_automation.bot.misc.MiscTask

    /** This run's connection outage episode (see [ConnectionOutageBudget]). */
    internal val connectionBudget = ConnectionOutageBudget()

    /** Set once the run has given up on the connection. [wait] re-throws it on every tick, so a
     * broad catch that swallows the first [ConnectionLostException] cannot keep the run going. */
    @Volatile
    internal var connectionLostReason: String? = null

    /** When the dialog handler tapped OK on the game's Data Download dialog ([dataDownloadActive]), or null. */
    @Volatile
    internal var dataDownloadAcceptedAtMs: Long? = null

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]Game"

        /** How often a long load checks whether an error dialog is actually holding it up. */
        internal const val LOADING_DIALOG_CHECK_MS: Long = 90_000L

        /** Loading this long with no error dialog is treated as a lost connection. No legitimate
         * load has been measured anywhere near it; race playback is not a loading screen. */
        internal const val LOADING_HARD_LIMIT_MS: Long = 10 * 60_000L

        /** Dialogs that can sit under a loading indicator and need the outage handling. */
        internal val LOADING_ERROR_DIALOGS: Set<String> = setOf("connection_error", "download_error", "session_error")

        /**
         * Whether a data download accepted at [acceptedAtMs] may still be running. No capture of the
         * game's download screens exists, so they are not recognised: for [limitMs] after the OK they
         * are waited out like its loading screen, tapping nothing. At ~200 MB, [LOADING_HARD_LIMIT_MS]
         * covers any link faster than about 3 Mbit/s.
         */
        internal fun dataDownloadActive(acceptedAtMs: Long?, nowMs: Long, limitMs: Long = LOADING_HARD_LIMIT_MS): Boolean =
            acceptedAtMs != null && nowMs - acceptedAtMs in 0 until limitMs

        /**
         * Looks in a row at the Data Download prompt without finding its OK button before the bot
         * stops and asks for it by hand. The OK template is unverified on this dialog; each look is a
         * fresh capture seconds apart, so three misses outlast its opening animation and mean a
         * template mismatch, not a frame caught mid-draw.
         */
        internal const val DATA_DOWNLOAD_OK_MISS_LIMIT = 3

        internal fun loadingHardLimitMessage(ms: Long): String = "The game kept loading for ${ms / 60_000} minutes with no error dialog. Stopping the run as a connection error."

        /**
         * Waits while [isLoading] holds. Every [softCheckMs] it asks [handleErrorDialog] whether an
         * error dialog is holding the load up; a handled dialog restarts the hard window, because
         * the outage budget owns that case. Loading for [hardLimitMs] with no dialog calls
         * [onGiveUp] and throws [ConnectionLostException]. Returns only once loading has cleared.
         */
        internal fun awaitLoadingCleared(
            isLoading: () -> Boolean,
            now: () -> Long,
            pause: () -> Unit,
            handleErrorDialog: () -> Boolean,
            onGiveUp: (String) -> Unit = {},
            softCheckMs: Long = LOADING_DIALOG_CHECK_MS,
            hardLimitMs: Long = LOADING_HARD_LIMIT_MS,
        ) {
            var windowStart = now()
            var lastDialogCheck = windowStart
            while (isLoading()) {
                val t = now()
                if (t - lastDialogCheck >= softCheckMs) {
                    lastDialogCheck = t
                    if (handleErrorDialog()) {
                        windowStart = now()
                        lastDialogCheck = windowStart
                        continue
                    }
                }
                if (t - windowStart >= hardLimitMs) {
                    val reason = loadingHardLimitMessage(hardLimitMs)
                    onGiveUp(reason)
                    throw ConnectionLostException(reason)
                }
                pause()
            }
        }

        /**
         * Presses the game's Back once on the positively identified Training selection screen and
         * reports whether the training menu came back. It presses nothing on any other screen.
         */
        internal fun backOutOfTrainingSelection(onTrainingSelection: () -> Boolean, pressBack: () -> Boolean, settle: () -> Unit, onTrainingMenu: () -> Boolean): Boolean {
            if (!onTrainingSelection() || !pressBack()) return false
            settle()
            return onTrainingMenu()
        }

        /** Package name of the Umamusume game (Global). The restart net relaunches this. If the JP
         * client (jp.co.cygames.umamusume) is ever targeted this needs to change. */
        const val GAME_PACKAGE: String = "com.cygames.umamusume"

        // --- Stall watchdog ---
        // On MuMu (and other emulators) the AccessibilityService's gesture injector can
        // deadlock under load, causing the system InputDispatcher's queue to back up
        // until the whole emulator appears frozen. To recover automatically, the bot
        // loop updates a heartbeat every time it makes forward progress. A background
        // coroutine watches that heartbeat and, if nothing has moved for
        // WATCHDOG_KILL_AT_MS while the bot is supposedly running, kills the process.
        // The AccessibilityService is sticky so Android restarts it within ~1 second
        // and input dispatch unfreezes. Before the kill it tries the in-process rungs in
        // StallWatchdog.kt: an accessibility toggle, then interrupting the Game thread.

        /**
         * The watchdog's clock: monotonic, so a device clock change can neither trip a rung or the
         * kill on a healthy run nor hide a stall. Replaced only by tests. The queue ledger's own
         * heartbeat file stays on the wall clock, since it must survive a reboot.
         */
        @Volatile
        internal var watchdogClock: () -> Long = { SystemClock.elapsedRealtime() }

        /** [watchdogClock] milliseconds of the last recorded forward progress. */
        @Volatile
        private var lastHeartbeatMs: Long = watchdogClock()

        /** The watchdog coroutine job, or null if not running. */
        @Volatile
        private var watchdogJob: Job? = null

        /** The thread running the current run's [start], which the watchdog interrupts. */
        @Volatile
        private var gameThread: Thread? = null

        /** The application Context for the watchdog's rungs, and whether its accessibility rung is granted. */
        @Volatile
        private var watchdogContext: Context? = null

        @Volatile
        private var watchdogGrant: Boolean = false

        /** How often the watchdog checks the heartbeat. */
        private const val WATCHDOG_INTERVAL_MS: Long = 5_000L

        /**
         * Record forward progress. Called at safe boundaries in the bot loop
         * (e.g., every tick of [wait]). Cheap, just a volatile store.
         */
        fun heartbeat() {
            lastHeartbeatMs = watchdogClock()
        }

        /** How long ago the last heartbeat was, on [watchdogClock]. */
        internal fun heartbeatAgeMs(): Long = watchdogClock() - lastHeartbeatMs

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
                    var rungsDone = 0
                    while (isActive) {
                        delay(WATCHDOG_INTERVAL_MS)
                        if (!BotService.isRunning) {
                            // Bot isn't running, reset so we don't fire immediately on resume.
                            heartbeat()
                            rungsDone = 0
                            continue
                        }
                        val age = heartbeatAgeMs()
                        val thread = gameThread
                        val context = watchdogContext
                        val rung = decideWatchdogRung(age, rungsDone, thread?.isAlive == true, watchdogGrant)
                        rungsDone = rungsDoneAfter(rung, rungsDone)
                        when (rung) {
                            WatchdogRung.NONE -> {}
                            WatchdogRung.RESET -> {
                                Log.i(TAG, "[WATCHDOG] Bot progress resumed after a stall.")
                                recordWatchdogBreadcrumb(context, WATCHDOG_BREADCRUMB_CLEARED)
                            }
                            WatchdogRung.RECOVERED -> {
                                heartbeat()
                                Log.w(TAG, "[WATCHDOG] The interrupted Game thread has exited; the queue takes the run from here.")
                                recordWatchdogBreadcrumb(context, WATCHDOG_BREADCRUMB_CLEARED)
                            }
                            WatchdogRung.TOGGLE_ACCESSIBILITY -> {
                                runWatchdogRung("accessibility") {
                                    val toggled = context != null && toggleAccessibilityForWatchdog(context)
                                    Log.e(TAG, "[WATCHDOG] No bot progress for ${age / 1000}s. Toggled the Accessibility Service off and on: $toggled.")
                                    recordWatchdogBreadcrumb(context, encodeWatchdogBreadcrumb(WatchdogRung.TOGGLE_ACCESSIBILITY, age))
                                }
                            }
                            WatchdogRung.SKIP_TOGGLE -> {
                                Log.e(TAG, "[WATCHDOG] No bot progress for ${age / 1000}s. The accessibility toggle needs WRITE_SECURE_SETTINGS, so it is skipped.")
                                recordWatchdogBreadcrumb(context, encodeWatchdogBreadcrumb(WatchdogRung.SKIP_TOGGLE, age))
                            }
                            WatchdogRung.INTERRUPT_GAME_THREAD -> {
                                WatchdogReason.set(watchdogInterruptReason(age))
                                thread?.interrupt()
                                Log.e(TAG, "[WATCHDOG] No bot progress for ${age / 1000}s. Interrupted the Game thread; only the heartbeat shows whether that worked.")
                                recordWatchdogBreadcrumb(context, encodeWatchdogBreadcrumb(WatchdogRung.INTERRUPT_GAME_THREAD, age))
                            }
                            WatchdogRung.KILL -> {
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
                        // Detect-only, after the rung has acted: how long progress had been missing (lock-free).
                        if (rung == WatchdogRung.TOGGLE_ACCESSIBILITY || rung == WatchdogRung.SKIP_TOGGLE || rung == WatchdogRung.INTERRUPT_GAME_THREAD) ProgressTracker.noteWatchdogRung()
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

    /** This run's [GameGeneration] token; 0 for a Game that never starts a run (the navigator's). */
    @Volatile
    private var runGeneration = 0

    /**
     * Makes the calling thread, the run's own, the one the stall watchdog interrupts, and reads the
     * watchdog's permission here rather than on its thread. Claims this run's generation, so a
     * leftover thread from an earlier run stops at its next wait or tap.
     */
    private fun watchRun() {
        gameThread = Thread.currentThread()
        watchdogContext = myContext.applicationContext
        watchdogGrant = hasSecureSettingsGrant(myContext)
        WatchdogReason.clear()
        runGeneration = GameGeneration.claim()
    }

    /** Stops a thread still running a run the watchdog gave up on before it can tap or keep the heartbeat alive. */
    private fun checkCurrentRun() {
        if (GameGeneration.isStale(runGeneration)) throw InterruptedException("This run was replaced by a newer one.")
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
            checkCurrentRun()
            // Record forward progress for the stall watchdog. Putting it here means
            // every tick of any wait() call keeps the heartbeat fresh, so the watchdog
            // only fires if we're genuinely stuck outside the wait loop for 45s+
            // (most commonly a deadlocked gesture injector).
            heartbeat()

            if (!BotService.isRunning) {
                throw InterruptedException()
            }
            connectionLostReason?.let { throw ConnectionLostException(it) }

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
     * Waits for the game to finish loading, within the bounds of [awaitLoadingCleared]: an error
     * dialog showing under the loading indicator is handled, and loading with no dialog for
     * [LOADING_HARD_LIMIT_MS] ends the run as a connection error.
     *
     * Note that this function is responsible for dictating how fast the bot will run so adjusting this should be done with caution.
     */
    fun waitForLoading() {
        var loadingCounter = 0
        awaitLoadingCleared(
            isLoading = {
                val loading = checkLoading(suppressLogging = loadingCounter % 10 != 0)
                loadingCounter = (loadingCounter + 1) % 20
                loading
            },
            now = { SystemClock.elapsedRealtime() },
            // Avoid an infinite recursion by skipping the loading check inside the pause.
            pause = { wait(waitDelay, skipWaitingForLoading = true) },
            handleErrorDialog = { handleLoadingErrorDialog() },
            onGiveUp = { reason ->
                MessageLog.e(TAG, "[CONNECTION] $reason")
                connectionLostReason = reason
            },
        )
    }

    /** Hands a connection, download or session error dialog found under a loading indicator to the
     * task's dialog handler, which owns the outage budget. Returns true if one was handled. */
    private fun handleLoadingErrorDialog(): Boolean {
        val dialog = DialogUtils.getDialog(imageUtils) ?: return false
        if (dialog.name !in LOADING_ERROR_DIALOGS) return false
        MessageLog.w(TAG, "[CONNECTION] ${dialog.title} is showing while the game is loading. Handling it.")
        task.handleDialogs(dialog = dialog)
        return true
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
        checkCurrentRun()
        // Perform the tap.
        gestureUtils.tap(x, y, imageName, taps = taps)
        ProgressTracker.noteAction()

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
     * Intentional fixed-coordinate tap that keeps [tap]'s post-tap loading wait.
     *
     * Use this for a deliberate tap at a known coordinate whose [label] is descriptive tracing only
     * (no backing template asset). It jitters like the library's coordinate fallback but taps with
     * `imageName = null`, so there is no spurious missing-asset error. See [CoordinateTap].
     */
    fun tapCoordinate(x: Double, y: Double, label: String, taps: Int = 1, ignoreWaiting: Boolean = false) {
        val (jx, jy) = CoordinateTap.resolve(x, y, label)
        tap(jx.toDouble(), jy.toDouble(), null, taps = taps, ignoreWaiting = ignoreWaiting)
    }

    private var ownUiHoldNoted = false

    /**
     * Holds blind input while UMA Auto+'s own screen is in front ([OwnUiForeground]): waits a beat and
     * returns true, so the caller neither taps nor counts the tick as stuck. The wait keeps the stall
     * watchdog's heartbeat, which still fires on a real hang.
     */
    fun holdBlindInputForOwnUi(): Boolean {
        val held =
            holdForOwnUi(OwnUiForeground.resumed) {
                if (!ownUiHoldNoted) MessageLog.i(TAG, "[MISC] UMA Auto+ is in front of the game; waiting for the game before tapping.")
                ownUiHoldNoted = true
                wait(2.0, skipWaitingForLoading = true)
            }
        if (!held) ownUiHoldNoted = false
        return held
    }

    /** [holdBlindInputForOwnUi] for a loop with a wall-clock cap: the milliseconds held, or null when the game is in front. */
    fun heldMsForOwnUi(): Long? = heldMsForOwnUi(System::currentTimeMillis) { holdBlindInputForOwnUi() }

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

    /** True on the in-career Training selection screen ([TrainingSelectionProbe]). */
    private fun isOnTrainingSelection(): Boolean {
        val bitmap = imageUtils.getSourceBitmap()
        return TrainingSelectionProbe.isTrainingSelection(SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }, bitmap.width, bitmap.height)
    }

    /** The facts [resumeSettleStep] decides on, from one capture. The lesson probes are Grand Concert 1080x1920 only. */
    private fun readResumeScreen(): ResumeScreen {
        val bitmap = imageUtils.getSourceBitmap()
        val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
        val grandConcertFrame = GrandConcert.isGrandConcert(scenario) && bitmap.width == 1080 && bitmap.height == 1920
        return ResumeScreen(
            dialogTitle = if (DialogUtils.check(imageUtils, sourceBitmap = bitmap)) DialogUtils.getTitle(imageUtils, bitmap, logOnMiss = false) else null,
            grandConcertDialog = grandConcertFrame && grandConcertLessonConfirmationPresent(sampler),
            grandConcertLessonList = grandConcertFrame && grandConcertLessonListPresent(sampler),
            raceList = ButtonRaceListFullStats.check(imageUtils, sourceBitmap = bitmap),
            cancel = ButtonCancel.check(imageUtils, sourceBitmap = bitmap),
            back = ButtonBack.check(imageUtils, sourceBitmap = bitmap),
            learn = ButtonLearn.check(imageUtils, sourceBitmap = bitmap),
        )
    }

    private fun pressResumeSettle(step: ResumeSettleStep): Boolean {
        val pressed =
            when (step.action) {
                ResumeSettleAction.CANCEL -> ButtonCancel.click(imageUtils)
                ResumeSettleAction.BACK -> ButtonBack.click(imageUtils)
            }
        if (pressed) MessageLog.i(TAG, "[INFO] Bot started on ${step.screen}. Pressed ${step.action.button} so the campaign decides again from the training menu.")
        return pressed
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
            SessionTally.accessibilityRewrites.incrementAndGet()
            wait(waitForRebind, skipWaitingForLoading = true)
            MessageLog.w(TAG, "[WARN] ensureAccessibilityService:: Accessibility Service grant restored. Gestures should resume.")
            true
        } catch (e: SecurityException) {
            SessionTally.accessibilityRepairsRefused.incrementAndGet()
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
            SessionTally.accessibilityRebinds.incrementAndGet()
            wait(waitForRebind, skipWaitingForLoading = true)
            MessageLog.w(TAG, "[WARN] forceRebindAccessibilityService:: Toggled the Accessibility Service off->on to recover silently-dead gesture dispatch.")
            true
        } catch (e: SecurityException) {
            SessionTally.accessibilityRepairsRefused.incrementAndGet()
            MessageLog.e(
                TAG,
                "[ERROR] forceRebindAccessibilityService:: Cannot toggle the Accessibility Service - WRITE_SECURE_SETTINGS is not granted. " +
                    "Run once: adb shell pm grant ${myContext.packageName} android.permission.WRITE_SECURE_SETTINGS",
            )
            false
        }
    }

    /** Whether this run has used its one [strongToggleAccessibilityService]. */
    var strongToggleUsed: Boolean = false
        private set

    /**
     * A stronger accessibility toggle than [forceRebindAccessibilityService]: turns accessibility off
     * globally for 3 s, then back on with this app's entry present. Untested live, so it is tried once
     * per run, only after two rebinds changed nothing, and the ladder that asked for it stops a few
     * ticks later if taps did not come back. The re-enable sits in a finally so a stop during the
     * pause cannot leave accessibility switched off.
     *
     * @return True if the toggle was issued, false if WRITE_SECURE_SETTINGS is missing.
     */
    fun strongToggleAccessibilityService(): Boolean {
        strongToggleUsed = true
        val expected = "${myContext.packageName}/com.steve1316.automation_library.utils.MyAccessibilityService"
        val resolver = myContext.contentResolver
        try {
            Settings.Secure.putString(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, "0")
        } catch (e: SecurityException) {
            SessionTally.accessibilityRepairsRefused.incrementAndGet()
            MessageLog.e(TAG, "[A11Y] The stronger accessibility toggle needs WRITE_SECURE_SETTINGS, which is not granted.")
            return false
        }
        try {
            wait(3.0, skipWaitingForLoading = true)
        } finally {
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val services = current.split(':').filter { it.isNotEmpty() && !it.equals(expected, ignoreCase = true) } + expected
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services.joinToString(":"))
            Settings.Secure.putString(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
        }
        SessionTally.accessibilityStrongToggles.incrementAndGet()
        MessageLog.w(TAG, "[A11Y] Two accessibility rebinds changed nothing, so accessibility was turned off for 3 s and back on (once per run). The next ticks show whether taps came back.")
        wait(3.0, skipWaitingForLoading = true)
        return true
    }

    /**
     * Relaunches the Umamusume game as a last-resort recovery from a screen no handler can identify
     * or advance (e.g. the game itself soft-locking, distinct from MuMu's gesture death which
     * [forceRebindAccessibilityService] handles). Career progress is saved server-side each turn, so
     * the game comes back on its Continue-Career flow, which the campaign's lobby re-entry path
     * resumes in place - no career is lost (validated manually 2026-07-11 via an adb force-stop +
     * relaunch that resumed El Condor's career).
     *
     * Uses NEW_TASK | RESET_TASK_IF_NEEDED, NOT CLEAR_TASK. This deliberately does NOT tear the
     * game's task down: a live task is brought back to its front door, and a dead one is cold-started.
     * The earlier CLEAR_TASK variant killed a still-alive foreground game from this background service
     * without the follow-up cold start ever landing (a background activity launch after the task
     * teardown gets dropped) - a daily-reset run on 2026-07-21 went from an alive-but-unrecognized
     * lobby to a dead game with a foreign app on top, which the bot then stared at until it stopped.
     * Re-fronting is non-destructive, so a relaunch that does not help simply leaves the game where it
     * was rather than destroying it.
     *
     * An ordinary app cannot force-stop another package without root, so this is a best-effort
     * relaunch rather than a hard kill; it recovers a UI/task soft-lock but may not reset a crashed
     * native renderer. Falls through (returns false) if the launcher intent cannot be resolved, so
     * the caller's normal stop still applies - no new dead-end. The caller verifies whether the game
     * actually came back (a recognized game screen returning); a dispatched intent is not proof.
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
            // NEW_TASK is required to start an Activity from this (non-Activity) service context;
            // RESET_TASK_IF_NEEDED lands on the task's entry Activity if it is resumed from history.
            // No CLEAR_TASK: never tear down a live game task (that killed the game on 2026-07-21) -
            // re-front a live game, cold-start a dead one.
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            MessageLog.w(
                TAG,
                "[RECOVERY] Reopening the game ($GAME_PACKAGE) to recover from an unrecognized/soft-locked screen: a running game is " +
                    "brought to the front as it is, not restarted, and only a game that is not running starts fresh. A career in progress resumes via Continue Career.",
            )
            myContext.startActivity(launchIntent)
            SessionTally.gameRelaunches.incrementAndGet()
            wait(waitAfterLaunch, skipWaitingForLoading = true)
            true
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] restartGame:: Failed to relaunch the game: ${e.message}")
            false
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    internal fun runDiagnostic(): TaskResult? {
        if (diagnosticSelection?.key == null) return null
        // A frozen diagnostic choice never falls through to career navigation, even without a handler.
        check(task.startTests()) { "Requested diagnostic is unavailable for this campaign" }
        return TaskResult.Success(TaskResultCode.TASK_RESULT_COMPLETE, "Diagnostic completed.")
    }

    /** Begins automation and returns the task's result. */
    fun start(): TaskResult {
        watchRun()
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
            return accessibilityHaltResult(
                A11Y_GRANT_MISSING,
                "The Accessibility Service is disabled and could not be restored automatically. Re-enable it in the Android settings or grant WRITE_SECURE_SETTINGS (see log).",
            )
        }

        runDiagnostic()?.let { return it }

        warnOnRacingConfigDrift()

        // Auto-navigate to the training menu if the bot is not already there.
        // This allows starting the bot from the home screen, scenario select, or any
        // other screen in the career launch flow - the navigator will find its way.
        //
        // Misc tasks (Daily Races, Team Trials) skip this entirely - they start from
        // the game's Home Screen and have their own state machines to navigate from
        // there to their target mode. The user is expected to have the game open on
        // the Home Screen (or any screen with the bottom nav visible) when starting.
        //
        // A stop, crash or restart during the training analysis leaves the career on the Training
        // selection screen, whose Skip pill the navigator would read as the launch Quick Mode prompt
        // (2026-09-27: two pill taps, then body taps until the run failed). The game's Back returns to
        // the training menu there, as the training handler's own back-out does. A turn-committing
        // confirmation or an in-career list is cancelled or backed out of first (ResumeSettle.kt).
        if (!isMiscTask) settleResumedCareer(::readResumeScreen, ::pressResumeSettle, { wait(1.0) }, ::isOnTrainingMenu)
        val onTrainingSelection = !isMiscTask && isOnTrainingSelection()
        if (onTrainingSelection && backOutOfTrainingSelection(::isOnTrainingSelection, { ButtonBack.click(imageUtils) }, { wait(1.0) }, ::isOnTrainingMenu)) {
            MessageLog.i(TAG, "[INFO] Bot started on the Training selection screen. Pressed Back to return to the training menu.")
        }
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
                val singleRun = !SettingsHelper.getBooleanSetting("runQueue", "enableRunQueue", true)
                val expectedTrainee = if (singleRun) SettingsHelper.getStringSetting("general", "appliedPresetTrainee") else ""
                val expectedExcludes = if (singleRun) SettingsHelper.getStringSetting("general", "appliedPresetTraineeExcludes") else ""
                if (expectedTrainee.isNotBlank()) {
                    MessageLog.i(TAG, "[INFO] Single-run launch will verify Trainee Select against '$expectedTrainee' (applied preset).")
                }
                val navResult =
                    navigator.navigate(
                        reuseSetup,
                        singleRunTrainee = expectedTrainee,
                        singleRunTraineeExcludes = expectedExcludes,
                        careerInFlight = careerInFlight || onTrainingSelection,
                    )
                if (!navResult.success) {
                    MessageLog.e(TAG, "[INFO] Auto-navigation failed: ${navResult.failureReason}")
                    MessageLog.e(TAG, "[INFO] Last state: ${navResult.lastDetectedState}, transition: ${navResult.failedTransition}")
                    MessageLog.e(TAG, "[INFO] ${navResult.recommendedAction}")
                    return TaskResult.Error(
                        TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED,
                        "Auto-navigation to training menu failed: ${navResult.failureReason}",
                        reasonKey = navResult.reasonKey,
                        reasonTrainee = navResult.reasonTrainee,
                        reasonOutfit = navResult.reasonOutfit,
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
                if (SettingsHelper.getBooleanSetting("runQueue", "enableRunQueue", true)) {
                    SettingsHelper.getIntSetting("queueState", "currentRun", 0)
                } else {
                    null
                }
            SparkRerollGate.beginCareer(
                nonce = java.util.UUID.randomUUID().toString().substring(0, 8),
                queueRun = queueRun,
                nowMs = System.currentTimeMillis(),
            )
            // Adopt the pending launch-transaction id minted by the launch navigation into this
            // career's active correlation, so the career's own telemetry stamps its own launch id and
            // a lineage read taken during that launch can be joined to this career. A resumed career
            // (no launch navigation) mints a fresh active id here instead; no lineage event joins it.
            LaunchTransactionGate.adopt(System.currentTimeMillis())
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
