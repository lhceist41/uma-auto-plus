package com.steve1316.uma_android_automation.bot

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
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
import com.steve1316.uma_android_automation.utils.ScreenBand
import com.steve1316.uma_android_automation.utils.gameY
import com.steve1316.uma_android_automation.utils.rememberScreenTopInset
import com.steve1316.uma_android_automation.utils.ProgressTracker
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.TitleScreenProbe
import com.steve1316.uma_android_automation.utils.OwnInputProbeResult
import com.steve1316.uma_android_automation.utils.ownInputReachesScreen
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
 * @property careerLaunched True when the navigation before this run started its career.
 */
class Game(val myContext: Context, val diagnosticSelection: DebugTestGate.Selection? = null, val careerInFlight: Boolean = false, careerLaunched: Boolean = false) {
    /** False when this run plays a career it did not start (a resume, a retry, or a Start on a career in progress); its scenario is checked at its first career screen. */
    var careerLaunched: Boolean = careerLaunched
        private set

    /** The current Android notification message to display. */
    var notificationMessage: String = ""

    /** The utility class for image processing and template matching. */
    val imageUtils: CustomImageUtils = CustomImageUtils(myContext, this)

    /**
     * Resolved per access, never cached: MuMu kills and rebinds the accessibility service mid-run, and a cached
     * reference would keep dispatching taps into the dead instance while screen capture still works.
     */
    val gestureUtils: MyAccessibilityService get() = MyAccessibilityService.getInstance()

    /** The database for skill-related information. */
    val skillDatabase: SkillDatabase = SkillDatabase(this)

    /** The formatter for decimal values. */
    val decimalFormat = DecimalFormat("#.##")

    /** The current campaign scenario; normalized on read so every accepted Grand Concert spelling maps to one canonical key. */
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

    val isMiscTask: Boolean = task is com.steve1316.uma_android_automation.bot.misc.MiscTask

    internal val connectionBudget = ConnectionOutageBudget()

    /** Set once the run gives up on the connection; [wait] re-throws it every tick so a broad catch cannot keep the run going. */
    @Volatile
    internal var connectionLostReason: String? = null

    @Volatile
    internal var dataDownloadAcceptedAtMs: Long? = null

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]Game"

        internal const val LOADING_DIALOG_CHECK_MS: Long = 90_000L

        /** Loading this long with no error dialog is treated as a lost connection (no legitimate load comes near it). */
        internal const val LOADING_HARD_LIMIT_MS: Long = 10 * 60_000L

        internal val LOADING_ERROR_DIALOGS: Set<String> = setOf("connection_error", "download_error", "session_error")

        /** Whether a data download accepted at [acceptedAtMs] may still be running. Its screens are not recognised, so for [limitMs] they are waited out, tapping nothing. */
        internal fun dataDownloadActive(acceptedAtMs: Long?, nowMs: Long, limitMs: Long = LOADING_HARD_LIMIT_MS): Boolean =
            acceptedAtMs != null && nowMs - acceptedAtMs in 0 until limitMs

        /** Consecutive looks at the Data Download prompt without finding OK before asking for it by hand; three outlast the opening animation, so it means a template mismatch. */
        internal const val DATA_DOWNLOAD_OK_MISS_LIMIT = 3

        internal fun loadingHardLimitMessage(ms: Long): String = "The game kept loading for ${ms / 60_000} minutes with no error dialog. Stopping the run as a connection error."

        /** Waits while [isLoading] holds, polling [handleErrorDialog] every [softCheckMs]; a handled dialog restarts the hard window. At [hardLimitMs] it calls [onGiveUp] and throws [ConnectionLostException]. */
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

        internal fun backOutOfTrainingSelection(onTrainingSelection: () -> Boolean, pressBack: () -> Boolean, settle: () -> Unit, onTrainingMenu: () -> Boolean): Boolean {
            if (!onTrainingSelection() || !pressBack()) return false
            settle()
            return onTrainingMenu()
        }

        /** Package name of the Global game; change if the JP client is targeted. */
        const val GAME_PACKAGE: String = "com.cygames.umamusume"

        // --- Stall watchdog ---
        // MuMu's gesture injector can deadlock under load and freeze the emulator. The bot loop updates a heartbeat;
        // if nothing moves for WATCHDOG_KILL_AT_MS the watchdog tries the StallWatchdog.kt rungs, then kills the
        // process (the sticky AccessibilityService restarts within ~1 s and input unfreezes).

        /** Monotonic so a device clock change can neither trip nor hide a stall; replaced only by tests. The queue ledger's heartbeat file stays on the wall clock to survive a reboot. */
        @Volatile
        internal var watchdogClock: () -> Long = { SystemClock.elapsedRealtime() }

        @Volatile
        private var lastHeartbeatMs: Long = watchdogClock()

        @Volatile
        private var watchdogJob: Job? = null

        @Volatile
        private var gameThread: Thread? = null

        @Volatile
        private var watchdogContext: Context? = null

        @Volatile
        private var watchdogGrant: Boolean = false

        private const val WATCHDOG_INTERVAL_MS: Long = 5_000L

        fun heartbeat() {
            lastHeartbeatMs = watchdogClock()
        }

        internal fun heartbeatAgeMs(): Long = watchdogClock() - lastHeartbeatMs

        private fun startWatchdog() {
            if (watchdogJob?.isActive == true) return
            heartbeat()
            watchdogJob =
                CoroutineScope(Dispatchers.Default).launch {
                    var rungsDone = 0
                    while (isActive) {
                        delay(WATCHDOG_INTERVAL_MS)
                        if (!BotService.isRunning) {
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
                                // MessageLog goes on a throwaway thread: its global lock can be what wedged (EventBus subscribers run inside it), so a blocked MessageLog.e could neuter the watchdog before killProcess.
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
                                // Brief window for the log line to flush, then self-terminate.
                                delay(250)
                                android.os.Process.killProcess(android.os.Process.myPid())
                                return@launch
                            }
                        }
                        // Detect-only, after the rung has acted (lock-free).
                        if (rung == WatchdogRung.TOGGLE_ACCESSIBILITY || rung == WatchdogRung.SKIP_TOGGLE || rung == WatchdogRung.INTERRUPT_GAME_THREAD) ProgressTracker.noteWatchdogRung()
                    }
                }
        }

        // --- WakeLock ---
        // A PARTIAL_WAKE_LOCK while a run is active keeps Android's OomAdjuster from SIGKILLing us under TRIM_EMPTY;
        // the timeout stops it leaking if the release path is skipped. Pairs with the dataSync FGS type on BotService.

        @Volatile
        private var wakeLock: PowerManager.WakeLock? = null

        private const val WAKE_LOCK_TAG: String = "UmaAutoPlus:BotRun"

        /** Hard cap on one WakeLock acquisition. */
        private const val WAKE_LOCK_TIMEOUT_MS: Long = 6 * 60 * 60 * 1000L

        @Synchronized
        fun acquireWakeLock(context: Context) {
            try {
                val existing = wakeLock
                if (existing != null && existing.isHeld) {
                    // Non-reference-counted: re-acquiring re-arms the timeout, so a queue longer than one window keeps its OOM protection.
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
                Log.w(TAG, "[WAKELOCK] Acquire failed: ${e.message}")
            }
        }

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

        /** Reset per-run soft state and suggest a GC at the between-run boundary; do NOT call mid-run. */
        fun cleanupBetweenRuns() {
            try {
                // Refresh the heartbeat so the watchdog does not fire during cleanup (no wait() runs between runs).
                heartbeat()
                System.gc()
                Log.i(TAG, "[CLEANUP] Between-run cleanup completed.")
            } catch (_: Throwable) {
            }
        }
    }

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

        startWatchdog()
    }

    @Volatile
    private var runGeneration = 0

    /** Makes the calling thread the one the stall watchdog interrupts and claims this run's generation, so a leftover thread from an earlier run stops at its next wait or tap. */
    private fun watchRun() {
        gameThread = Thread.currentThread()
        watchdogContext = myContext.applicationContext
        watchdogGrant = hasSecureSettingsGrant(myContext)
        WatchdogReason.clear()
        runGeneration = GameGeneration.claim()
    }

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
            // Every wait() tick records progress, so the watchdog fires only when stuck outside the wait loop.
            heartbeat()

            if (!BotService.isRunning) {
                throw InterruptedException()
            }
            connectionLostReason?.let { throw ConnectionLostException(it) }

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
            pause = { wait(waitDelay, skipWaitingForLoading = true) },
            handleErrorDialog = { handleLoadingErrorDialog() },
            onGiveUp = { reason ->
                MessageLog.e(TAG, "[CONNECTION] $reason")
                connectionLostReason = reason
            },
        )
    }

    /** Hands a connection, download or session error dialog under a loading indicator to the task's dialog handler, which owns the outage budget. */
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
        OwnUiForeground.waitForGame()
        checkCurrentRun()
        // Perform the tap.
        gestureUtils.tap(x, y, imageName, taps = taps)
        ProgressTracker.noteAction()

        // If the gesture injector had deadlocked, the call above would have blocked past the watchdog threshold.
        heartbeat()

        if (!ignoreWaiting) {
            // Now check if the game is waiting for a server response from the tap and wait if necessary.
            wait(0.20)
            waitForLoading()
        }
    }

    /** Fixed-coordinate tap that keeps [tap]'s post-tap loading wait; [label] is tracing only, so there is no missing-asset error. See [CoordinateTap]. */
    fun tapCoordinate(x: Double, y: Double, label: String, taps: Int = 1, ignoreWaiting: Boolean = false) {
        val (jx, jy) = CoordinateTap.resolve(x, y, label)
        tap(jx.toDouble(), jy.toDouble(), null, taps = taps, ignoreWaiting = ignoreWaiting)
    }

    private var ownUiHoldNoted = false

    /** Holds blind input while UMA Auto+'s own screen is in front ([OwnUiForeground]): waits a beat, keeping the watchdog heartbeat, and returns true. */
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

    private fun isOnTrainingSelection(): Boolean {
        val bitmap = imageUtils.getSourceBitmap()
        return TrainingSelectionProbe.isTrainingSelection(SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }, bitmap.width, bitmap.height)
    }

    /** The lesson probes are Grand Concert 1080x1920 only. */
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

    /** On the in-career main screen: a normal training turn (Training/Rest buttons) or a mandatory race day (ribbon). Keep in sync with CareerLaunchNavigator's ACTIVE_TRAINING_MENU detection. */
    private fun isOnTrainingMenu(): Boolean {
        val bitmap = imageUtils.getSourceBitmap()
        return ButtonTraining.check(imageUtils, sourceBitmap = bitmap) ||
            ButtonRest.check(imageUtils, sourceBitmap = bitmap) ||
            IconRaceDayRibbon.check(imageUtils, sourceBitmap = bitmap)
    }

    /** Log-only warning when key racing-plan settings deviate from the last-applied preset snapshot; a stray toggle can silently reshape racing for the whole career. */
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
     * On a career-end screen: the End screen, or the career-end Learn skill list. Startup auto-navigation must not run
     * here, since the navigator would close the list and press Complete Career with skill points unspent.
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
     * Restores the Accessibility Service grant if the emulator wiped it. MuMu sporadically clears
     * enabled_accessibility_services mid-run, which silently kills gesture injection while capture keeps working.
     * Needs WRITE_SECURE_SETTINGS granted once over the debug bridge.
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
     * Forces an unbind/rebind by toggling the secure ENABLED_ACCESSIBILITY_SERVICES entry off and on. MuMu can leave
     * the string intact while dispatchGesture is dead (shell input still lands), and rewriting the same value is ignored,
     * so the entry must really be removed. [gestureUtils] resolves per access and picks up the new instance.
     */
    fun forceRebindAccessibilityService(waitForRebind: Double = 3.0): Boolean {
        val expected = "${myContext.packageName}/com.steve1316.automation_library.utils.MyAccessibilityService"
        return try {
            val current: String = Settings.Secure.getString(myContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val others: List<String> = current.split(':').filter { it.isNotEmpty() && !it.equals(expected, ignoreCase = true) }

            // Off: drop only our service so the framework destroys the dead instance.
            Settings.Secure.putString(myContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others.joinToString(":"))
            wait(1.0, skipWaitingForLoading = true)

            // On: re-add ours so a fresh instance binds.
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

    var strongToggleUsed: Boolean = false
        private set

    /** Stronger toggle: accessibility off globally for 3 s, then on. Untested live, so tried once per run after two rebinds changed nothing. The re-enable sits in a finally so a stop cannot leave accessibility off. */
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
     * Brings the game back to the front when no handler can identify the screen: the first attempt of [reopenGame]. The career is saved
     * server-side, so it resumes through Continue Career. Returns whether the intent was dispatched.
     */
    fun restartGame(waitAfterLaunch: Double = 20.0): Boolean = reopenGame(attempt = 1, waitAfterLaunch = waitAfterLaunch) != GameReopen.NOT_DISPATCHED

    /**
     * Reopens the game for attempt [attempt] of a stuck episode.
     *
     * The first attempt, and every attempt on Android 14+, only brings the game's task to the front: a live game comes back as it was,
     * so a frozen one stays frozen.
     *
     * From the second attempt below Android 14, [restartFrozenGame] closes it: Home, `killBackgroundProcesses` across the settle window,
     * then a launch into a fresh task. CLEAR_TASK comes only after that window: an earlier CLEAR_TASK against a live foreground game killed
     * it with no relaunch landing. An ordinary app cannot see another package's process, so the title screen is the only proof of a restart.
     *
     * Each step acts first and logs after, with [Log]: this is recovery code.
     */
    internal fun reopenGame(attempt: Int, waitAfterLaunch: Double = 20.0): GameReopen {
        if (myContext.packageManager.getLaunchIntentForPackage(GAME_PACKAGE) == null) {
            MessageLog.e(TAG, "[ERROR] reopenGame:: Could not resolve a launcher intent for $GAME_PACKAGE. Is the game installed under that package? Skipping the reopen.")
            return GameReopen.NOT_DISPATCHED
        }
        if (!reopenClosesGame(attempt, Build.VERSION.SDK_INT)) {
            if (!launchGame(clearTask = false)) return GameReopen.NOT_DISPATCHED
            SessionTally.gameRelaunches.incrementAndGet()
            val why = if (attempt >= 2) "Android 14 and later do not let an app close another app" else "the first try only brings it back"
            MessageLog.w(TAG, "[RECOVERY] Reopening the game ($GAME_PACKAGE): brought it to the front as it was ($why); it was not restarted. A career in progress resumes via Continue Career.")
            wait(waitAfterLaunch, skipWaitingForLoading = true)
            return GameReopen.REFRONTED
        }

        val result =
            restartFrozenGame(
                captureGrid = { imageUtils.getSourceBitmap().let { b -> homeLumaGrid(b.width, b.height) { x, y -> b.getPixel(x, y) } } },
                pressHome = {
                    val pressed = gestureUtils.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    Log.w(TAG, "[RECOVERY] Pressed Home to close the game (dispatched=$pressed).")
                    pressed
                },
                frontPackage = {
                    val front = frontWindowPackage()
                    Log.w(TAG, "[RECOVERY] Window in front after Home: ${front ?: "unreadable, so the screen decides"}.")
                    front
                },
                killGame = { killGameProcess() },
                launch = { clearTask -> launchGame(clearTask) },
                titleShowing = {
                    imageUtils.getSourceBitmap().let { b -> TitleScreenProbe.isTitleScreen(SparkPixelSampler { x, y -> b.getPixel(x, y) }, b.width, b.height) }
                },
                sleep = { wait(it, skipWaitingForLoading = true) },
            )
        return when (result) {
            FrozenGameRestart.RESTARTED, FrozenGameRestart.RESTARTED_ON_PLAIN_LAUNCH -> {
                SessionTally.gameRelaunches.incrementAndGet()
                val launch = if (result == FrozenGameRestart.RESTARTED) "a launch into a fresh task" else "a second, plain launch"
                MessageLog.w(TAG, "[RECOVERY] The game restarted: it was sent Home, asked to close and brought up by $launch, and its title screen came up. A career in progress resumes via Continue Career.")
                GameReopen.RESTARTED
            }
            FrozenGameRestart.NOT_RESTARTED -> {
                SessionTally.gameRelaunches.incrementAndGet()
                MessageLog.w(
                    TAG,
                    "[RECOVERY] The game was sent Home, asked to close and launched again, but its title screen did not come up within " +
                        "${GAME_TITLE_WAIT_SECONDS.toInt()} s of a launch. Android does not show whether it closed; it was not restarted.",
                )
                GameReopen.NOT_RESTARTED
            }
            FrozenGameRestart.SCREEN_UNCHANGED_AFTER_HOME -> {
                if (!launchGame(clearTask = false)) return GameReopen.NOT_DISPATCHED
                SessionTally.gameRelaunches.incrementAndGet()
                MessageLog.w(
                    TAG,
                    "[RECOVERY] Home did not clearly leave the game (not dispatched, the game's window still in front, or the screen did not " +
                        "change and hold still), so the game was not closed. It was brought to the front as it was, not restarted.",
                )
                wait(waitAfterLaunch, skipWaitingForLoading = true)
                GameReopen.REFRONTED
            }
            FrozenGameRestart.NOT_DISPATCHED -> {
                MessageLog.e(TAG, "[ERROR] reopenGame:: The game was sent Home but no launch could be dispatched.")
                GameReopen.NOT_DISPATCHED
            }
        }
    }

    /** NEW_TASK is required to start an Activity from this non-Activity service context; RESET_TASK_IF_NEEDED lands on the entry Activity. */
    private fun launchGame(clearTask: Boolean): Boolean {
        val intent = myContext.packageManager.getLaunchIntentForPackage(GAME_PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            myContext.startActivity(intent)
            Log.w(TAG, "[RECOVERY] Launched the game (${if (clearTask) "fresh task" else "existing task"}).")
            true
        } catch (e: Exception) {
            Log.e(TAG, "[RECOVERY] Launching the game failed: ${e.message}")
            false
        }
    }

    /** Null when unreadable. Asks the app in front for its window root, so a hung app can hold the call for the accessibility timeout. */
    internal fun frontWindowPackage(): String? =
        try {
            gestureUtils.rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            Log.w(TAG, "[RECOVERY] The window in front could not be read: ${e.javaClass.simpleName}")
            null
        }

    /** ARRIVED proves a game ignoring the bot's taps has stopped responding; LOST means the taps are dead. */
    internal fun ownInputReachesScreen(): OwnInputProbeResult =
        ownInputReachesScreen(myContext, runCatching { gestureUtils }.getOrNull(), MyAccessibilityService.isGestureAllowed)

    /** Asks Android to end the game's process. A no-op while the game ranks above a cached background app, and on Android 14+. */
    private fun killGameProcess() {
        try {
            myContext.getSystemService(ActivityManager::class.java)?.killBackgroundProcesses(GAME_PACKAGE)
            Log.w(TAG, "[RECOVERY] Asked Android to end the game's background process.")
        } catch (e: SecurityException) {
            Log.e(TAG, "[RECOVERY] Ending the game's background process was refused: ${e.message}")
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    internal fun runDiagnostic(): TaskResult? {
        if (diagnosticSelection?.key == null) return null
        check(task.startTests()) { "Requested diagnostic is unavailable for this campaign" }
        return TaskResult.Success(TaskResultCode.TASK_RESULT_COMPLETE, "Diagnostic completed.")
    }

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
        val topInset = rememberScreenTopInset(myContext)
        val bandOffsets = ScreenBand.entries.joinToString { "${it.name.lowercase()} +${gameY(0.0, it, SharedData.displayWidth, SharedData.displayHeight).toInt()}" }
        MessageLog.i(TAG, "[LAYOUT] Top cutout inset $topInset px; offsets from 1080x1920: $bandOffsets.")
        if (SharedData.displayWidth != 1080) {
            MessageLog.w(TAG, "[LAYOUT] A ${SharedData.displayWidth}-pixel-wide screen is not supported. Set the phone's screen resolution to FHD+ (1080 wide).")
        }
        val isConfig1 = SharedData.displayWidth == 1080 && SharedData.displayHeight == 1920 && SharedData.displayDPI == 240
        val isConfig2 = SharedData.displayWidth == 1080 && SharedData.displayHeight == 2340 && SharedData.displayDPI == 450
        if (!isConfig1 && !isConfig2) {
            MessageLog.w(
                TAG,
                "[WARN] ⚠️ Bot performance will be severely degraded since display configuration is not 1080x1920 @ 240 DPI or 1080x2340 @ 450 DPI unless an appropriate scale is set for your device.",
            )
        }
        if (debugMode) MessageLog.w(TAG, "[WARN] ⚠️ Debug Mode is enabled. All bot operations will be significantly slower as a result.")
        // toDoubleOrNull: an empty/unset manual-scale setting threw NumberFormatException at startup.
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

        // The emulator can wipe the Accessibility grant even while idle; verify and restore it first.
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
        // A stop, crash or restart during the training analysis leaves the career on the Training selection screen, whose Skip pill the
        // navigator would read as the launch Quick Mode prompt. The game's Back returns to the training menu; a turn-committing
        // confirmation or an in-career list is cancelled or backed out of first (ResumeSettle.kt).
        if (!isMiscTask) settleResumedCareer(::readResumeScreen, ::pressResumeSettle, { wait(1.0) }, ::isOnTrainingMenu)
        val onTrainingSelection = !isMiscTask && isOnTrainingSelection()
        if (onTrainingSelection && backOutOfTrainingSelection(::isOnTrainingSelection, { ButtonBack.click(imageUtils) }, { wait(1.0) }, ::isOnTrainingMenu)) {
            MessageLog.i(TAG, "[INFO] Bot started on the Training selection screen. Pressed Back to return to the training menu.")
        }
        if (!isMiscTask && !isOnTrainingMenu()) {
            if (isOnCareerEndScreen()) {
                // Started on a career-end screen: the campaign loop buys skills and finishes the bookkeeping; the navigator would press Complete Career with points unspent.
                MessageLog.i(TAG, "[INFO] Bot started on a career-end screen. Skipping auto-navigation; the campaign will buy skills and finish the career.")
            } else {
                MessageLog.i(TAG, "[INFO] Bot is not on the training menu. Attempting auto-navigation...")
                val navigator = CareerLaunchNavigator(myContext)
                val reuseSetup = SettingsHelper.getBooleanSetting("runQueue", "reuseLastLaunchSetup", true)
                // Single runs verify Trainee Select against the applied preset's trainee: the game preselects the last pick, and an interrupted queue once left the wrong trainee preselected. Queue runs pass no expectation.
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
                if (navResult.careerLaunched) careerLaunched = true
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
                // The viewer binds to loopback, so advertise the port-forward path instead of a dead LAN URL.
                logViewerString = "\nRemote Log Viewer enabled (loopback). From a computer: adb forward tcp:$port tcp:$port then open http://localhost:$port"
            }
            DiscordUtils.queue.add("```diff\n+ ${MessageLog.getSystemTimeString()} Bot run started! Scenario: $scenario```$logViewerString")
        }
        // CAREER ATTACHMENT: the first point proving a real career exists, and the ONLY place a spark reroll transaction is created. Arming earlier lets the cold-start navigation's pass through Home destroy it. Misc tasks never arm.
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
            // Adopt the launch navigation's pending transaction id so the career's telemetry and lineage reads join; a resumed career mints a fresh id.
            LaunchTransactionGate.adopt(System.currentTimeMillis())
            // Capture the launch-critical config identity (the React Start barrier verified this settingsRevision) so mid-career drift is visible.
            val runConfig = RunConfigSnapshot.armFromSettings(System.currentTimeMillis())
            MessageLog.i(TAG, "[CONFIG_DRIFT] [KOTLIN] loaded_run_config ${RunConfigSnapshot.describe(runConfig)}")
        }

        // Per-run safety timeout (default 180 min, matching the TS side); single runs use it too.
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
