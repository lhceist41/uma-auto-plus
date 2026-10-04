package com.steve1316.uma_android_automation

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
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
import com.steve1316.automation_library.utils.DiscordUtils
import com.steve1316.automation_library.utils.MediaProjectionService
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.MyAccessibilityService
import com.steve1316.automation_library.utils.NotificationUtils
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.bot.CareerFinalizeGate
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.GrandConcertScenario
import com.steve1316.uma_android_automation.bot.SparkRerollGate
import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode
import com.steve1316.uma_android_automation.bot.shouldClearSparkTransactionForRunResult
import com.steve1316.uma_android_automation.bot.shouldClearVerdictForRunResult
import com.steve1316.uma_android_automation.utils.KeepScreenOn
import com.steve1316.uma_android_automation.utils.LogStreamServer
import com.steve1316.uma_android_automation.utils.ProgressNotification
import com.steve1316.uma_android_automation.utils.ProgressTracker
import com.steve1316.uma_android_automation.utils.StatusBoard
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

        /** Single-flight latch for the bot session. The overlay play button can multi-fire
         * StartEvents (five sessions in 100 ms observed live 2026-07-27, each dying on the same
         * error and spraying log files); only the first entry may run, the rest are ignored
         * until the session's finally releases the latch. */
        private val sessionActive = java.util.concurrent.atomic.AtomicBoolean(false)

        internal fun isSessionActive(): Boolean = sessionActive.get()

        /**
         * Requests the same stop as [stop] when screen capture ends under a running session, so the
         * loop exits at its next wait instead of acting on the library's last cached frame. Sets the
         * flag before logging, and logs with android.util.Log so a blocked message log cannot delay the stop.
         */
        internal fun stopForLostCapture() {
            if (!shouldStopForLostCapture(sessionActive.get(), queueStopRequested)) return
            queueStopRequested = true
            Log.w(TAG, "[STOP] Screen capture stopped while the bot was running (for example the notification's Stop). Stopping the run.")
        }

        /**
         * Human-readable reason for an internal/deliberate queue stop (e.g. the trainee-mismatch guard),
         * or null when the stop is a genuine user Stop. Lets the result and queue logs say WHY the queue
         * stopped instead of always blaming the user (a trainee-mismatch guard stop reported as "manually
         * stopped by the user" masks the real cause).
         */
        @Volatile
        var queueStopReason: String? = null

        /**
         * Shown when the settings the bot read differ from the ones the app checked at Start: a write
         * landed in between, or the two sides did not see the same data. A full restart of UMA Auto+
         * clears either, where pressing Start again might not. An enabled accessibility service keeps
         * the process alive after the app is swiped away, hence the force stop.
         */
        const val SETTINGS_NOT_DELIVERED_MESSAGE =
            "Not started, and nothing was spent: the settings UMA Auto+ checked when you pressed Start did not reach the bot. " +
                "Force stop UMA Auto+ (Android Settings, Apps, UMA Auto+, Force stop), reopen it, turn its accessibility service back on " +
                "if it was switched off, then press Start again."

        /**
         * Shown when Start finds the settings file damaged, replaced, or not openable. The app checks
         * the file again when its process starts and restores the backup only if the file is damaged;
         * an enabled accessibility service keeps the process alive after the app is swiped away, hence
         * the force stop.
         */
        const val DATABASE_UNHEALTHY_MESSAGE =
            "Not started, and nothing was spent: UMA Auto+ could not safely use its saved settings file. " +
                "If your device's storage is full, free some space first. Then force stop UMA Auto+ (Android Settings, Apps, UMA Auto+, Force stop), " +
                "reopen it, turn its accessibility service back on if it was switched off, and press Start again. " +
                "When it opens, it checks the file again and restores it from the last good backup if it is damaged. " +
                "If this message comes back, please report it as a bug."

        /** Player-safe key for [queueStopReason], set with it: the queue report carries the key, never the prose. */
        @Volatile
        var queueStopKey: String? = null

        /** Final stat values of the last completed career, snapshotted when the [CAREER_END]
         * ledger line is emitted. The sparks reroll gate reads them after the Campaign instance
         * is gone (the navigator owns the career-end SPARKS screen). */
        @Volatile
        var lastCareerEndStats: Map<String, Int>? = null

        /** In-career trainee name of the last completed career, snapshotted alongside
         * [lastCareerEndStats] so the navigator's sparks corpus records are self-contained. */
        @Volatile
        var lastCareerEndTrainee: String? = null

        /** The same trainee as the game shows the name ("El Condor Pasa"), for player-facing text; null when unread. */
        @Volatile
        var lastCareerEndTraineeName: String? = null

        /** Scenario token and config-arm fingerprint of the last completed career, snapshotted
         * alongside [lastCareerEndTrainee] at [CAREER_END] so the navigator's sparks corpus records
         * carry the SAME fp/scenario as that career's outcome record. Snapshotting (not recomputing
         * later) prevents a queued run's settings change between career completion and spark recording
         * from mis-attributing the set. Null until the first career-end of the session. */
        @Volatile
        var lastCareerEndScenario: String? = null

        @Volatile
        var lastCareerEndFp: String? = null

        /** Outcome label and observed turn (null when no date was read) of the last completed career, stashed with [lastCareerEndTrainee]. */
        @Volatile
        var lastCareerEndOutcome: String? = null

        @Volatile
        var lastCareerEndTurn: Int? = null

        /** Rank, score, fans, finale and final stats of the last career that ended, stashed with [lastCareerEndTrainee]; null for a career stopped mid-way. */
        @Volatile
        internal var lastCareerEndResult: CareerResult? = null

        /** The kept sparks the career-end flow last recorded, tagged with [lastCareerEndSeq] as it stood then. */
        @Volatile
        internal var lastCareerEndSparks: CareerEndSparks? = null

        /** Bumped after every career-end stash, so a run can tell its own stash from one a previous run left. */
        @Volatile
        var lastCareerEndSeq: Long = 0L

        /** When true, the current run should be skipped and the queue should advance. */
        @Volatile
        var queueSkipRequested: Boolean = false

        /**
         * The player asked to stop once the current career has finished: the queue pauses at the
         * next launch point instead, with the next run saved for Start. In memory only: a process
         * death ends the session anyway, and its saved state resumes the run that was playing.
         */
        @Volatile
        var stopAfterCareerRequested: Boolean = false

        /**
         * Set by the campaign when a run stops because the game could not be recovered to a driveable
         * state (it crashed/was killed and a relaunch never brought it back, or a live screen is
         * genuinely un-driveable). The queue then PAUSES after this run regardless of stopOnError:
         * launching the next run onto a dead or foreign screen can only fail. Reset at the start of
         * every session. Distinct from a generic per-run error, which stopOnError governs as before.
         */
        @Volatile
        var gameRecoveryFailed: Boolean = false

        /** Set by a run whose taps changed nothing and whose repair could not help; the queue halts after it, keeping the saved queue for Start. Reset every session. */
        @Volatile
        var accessibilityHaltKey: String? = null

        /**
         * Wall-clock budget for one between-run navigation. Normal navigation (career summary
         * through deck setup to the training menu, cinematic included) takes 2-5 minutes; a
         * navigate() call that hasn't returned by this deadline is wedged below the FSM loop,
         * where its own per-iteration bail-outs can never fire.
         */
        internal const val NAV_DEADLINE_MS: Long = 10 * 60 * 1000L

        /** How long after the deadline interrupt to wait before escalating to a queue stop. */
        private const val NAV_INTERRUPT_GRACE_MS: Long = 60 * 1000L

        /**
         * How long the between-run wait keeps looking for a stop after this thread is interrupted.
         * The overlay Stop interrupts first and tears the service down afterwards, so without a
         * short settle the queue's terminal classification can run before BotService.isRunning
         * turns false.
         */
        private const val STOP_EVIDENCE_SETTLE_MS: Long = 1500L

        /** Gap after the library's own end notification, so Android does not drop the replacement as a too-fast update. */
        private const val END_NOTIFICATION_DELAY_MS: Long = 500L

        /** The foundation library's notification id (a private constant there, so repeated here). */
        private const val LIBRARY_NOTIFICATION_ID = 1

        /**
         * What the end notifier does once the library's thread, whose cleanup posts "Completed
         * successfully with no errors." last, has finished. With the capture service up, it shows how
         * the session ended. With the service gone (the app's Stop, the overlay's dismiss), that late
         * post would stay as a false standalone notification, so it is removed. The service can also go
         * down right after the update, before its own cancel-all, so the update is checked again and
         * removed then. A new session's notification is never touched.
         */
        internal fun finishEndNotification(captureRunning: () -> Boolean, sessionRunning: () -> Boolean, update: () -> Unit, remove: () -> Unit) {
            if (sessionRunning()) return
            if (captureRunning()) {
                update()
                if (captureRunning() || sessionRunning()) return
            }
            remove()
        }

        /**
         * Persists the current queue state to SQLite so it can survive app crashes.
         * Writes directly to the settings database using INSERT OR REPLACE.
         */
        fun saveQueueState(context: Context, active: Boolean, currentRun: Int = 0, totalRuns: Int = 0, phase: String = PHASE_CAREER, completedRuns: Int? = null) {
            try {
                val dbFile = File(context.filesDir, "SQLite/settings.db")
                if (!dbFile.exists()) return
                val db = SettingsDatabase.get(context)
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
                if (completedRuns != null) {
                    db.execSQL(
                        "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                        arrayOf("queueState", "completedRuns", completedRuns.toString()),
                    )
                }
                db.execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("queueState", "timestamp", System.currentTimeMillis().toString()),
                )
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
         * @property completedRuns Careers the queue had finished when this was saved, or null for a
         *           state saved before the count was persisted.
         */
        data class QueueState(
            val active: Boolean,
            val currentRun: Int,
            val totalRuns: Int,
            val ageMs: Long,
            val phase: String,
            val completedRuns: Int? = null,
        )

        /**
         * Saved queue state older than this is ignored. A day, so a queue interrupted overnight can
         * still be resumed the next morning; the Home banner reads the same state through
         * [loadQueueState], so it cannot disagree.
         */
        private const val QUEUE_STATE_STALE_MS: Long = 24 * 60 * 60 * 1000L

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
                val raw = mutableMapOf<String, String>()
                SettingsDatabase.get(context).rawQuery(
                    "SELECT key, value FROM settings WHERE category = ?",
                    arrayOf("queueState"),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        raw[cursor.getString(0)] = cursor.getString(1)
                    }
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

                return QueueState(active, currentRun, totalRuns, ageMs, phase, raw["completedRuns"]?.toIntOrNull())
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

            /** 0-based rotation slot for run [run] with a resync [offset] folded in (cycling). */
            fun indexForRun(run: Int, offset: Int): Int {
                if (count <= 0) return 0
                return (indexForRun(run) + offset).mod(count)
            }

            /** Offset that makes [indexForRun] map [run] onto [targetIndex] (a mid-career resync). */
            fun resyncOffsetFor(run: Int, targetIndex: Int): Int {
                if (count <= 0) return 0
                return (targetIndex - indexForRun(run)).mod(count)
            }
        }

        /** What the run loop does once a career playthrough returns. See [decidePostCareerAction]. */
        enum class PostCareerAction {
            /** Walk Complete Career -> sparks (+ opt-in reroll) -> veteran registration -> Home, then stop. */
            FINALIZE_TO_HOME,

            /** The queue has another run: run the between-run navigation and launch the next career. */
            LAUNCH_NEXT,

            /** Leave the game on whatever screen the run ended on (non-clean end, stop, or dead service). */
            STOP,
        }

        /**
         * Decides what to do after run [runIndex] of [totalRuns] returns [resultCode].
         *
         * Mandatory post-career cleanup is NOT queue-gated: every cleanly completed last/only career
         * yields [PostCareerAction.FINALIZE_TO_HOME] so its sparks are read, recorded, optionally
         * rerolled, and the game returns Home. The queue-disabled single run used to skip all of that
         * and stop on the results screen, because finalize was reached only when [enableRunQueue] was
         * true. Launching the NEXT career stays queue-gated - only a run queue with runs remaining
         * yields [PostCareerAction.LAUNCH_NEXT] - so a single run can never start a second career. A
         * stop request, a torn-down service, or any non-COMPLETE ending yields [PostCareerAction.STOP]
         * and leaves the screen untouched.
         *
         * Pure: no Android, no settings reads. The caller passes already-resolved state so this is unit
         * tested without standing up the module (see PostCareerDecisionTest).
         */
        fun decidePostCareerAction(
            resultCode: TaskResultCode,
            runIndex: Int,
            totalRuns: Int,
            enableRunQueue: Boolean,
            queueStopRequested: Boolean,
            botRunning: Boolean,
        ): PostCareerAction {
            // A stop request or a dead service: never navigate, leave the screen as-is.
            if (queueStopRequested || !botRunning) return PostCareerAction.STOP
            // The queue has more runs to play -> launch the next one (queue-only, unchanged).
            if (enableRunQueue && runIndex < totalRuns) return PostCareerAction.LAUNCH_NEXT
            // Last or only run: a clean completion runs the career-end flow through to Home; any
            // other ending (skip, error, breakpoint) leaves the game where it stopped.
            return if (resultCode == TaskResultCode.TASK_RESULT_COMPLETE) PostCareerAction.FINALIZE_TO_HOME else PostCareerAction.STOP
        }

        /**
         * Runs already completed before this process started, given the persisted queue state's
         * [phase] and the [currentRun] it recorded. Feeds the resumed queue's completedRuns count
         * so a queue resumed at run 4 of 6 that goes on to finish runs 4-6 reports 6/6 completed
         * to Home, not 3/6 (this-session-only count, the 2026-07-28 undercount).
         *
         * PHASE_LAUNCHING means currentRun's career had already finished (it counts). PHASE_CAREER
         * means currentRun was still in flight when interrupted; the resume re-enters it, so it
         * never counts as already done. This is the most the saved position can prove; a state that
         * saved its own count ([QueueState.completedRuns]) is trusted up to this ceiling.
         *
         * Pure: no Android, no settings reads. Unit tested without standing up the module (see
         * PriorCompletedRunsTest).
         */
        fun priorCompletedRunsFor(phase: String, currentRun: Int): Int =
            if (phase == PHASE_LAUNCHING) currentRun else currentRun - 1

        /** Where a resumed queue starts and how many of its careers are already finished. */
        data class ResumePlan(val startFromRun: Int, val priorCompletedRuns: Int)

        /**
         * A resume re-enters `currentRun` whenever its career was in flight ([PHASE_CAREER]),
         * rotation or not: that career still occupies the game's single slot, so starting the next
         * run would only finish it under the next run's number (and, in a rotation, the next
         * trainee's preset). After a launch boundary ([PHASE_LAUNCHING]) the next run starts.
         */
        fun resumePlanFor(phase: String, currentRun: Int, savedCompletedRuns: Int?): ResumePlan {
            val ceiling = priorCompletedRunsFor(phase, currentRun)
            val prior = savedCompletedRuns?.coerceIn(0, ceiling) ?: ceiling
            return ResumePlan(if (phase == PHASE_CAREER) currentRun else currentRun + 1, prior)
        }

        /**
         * A stop keeps the resume record so Start re-enters the saved run: a bot stop always, a player's stop only when it ended a run whose
         * career is still in the slot ([stopLeftCareer]). Both clear it after the last career, which it would replay.
         */
        fun keepsResumeRecordAfterStop(queueStopRequested: Boolean, botStopReason: String?, lastCareerFinished: Boolean, stopLeftCareer: Boolean): Boolean =
            queueStopRequested && !lastCareerFinished && (botStopReason != null || stopLeftCareer)

        /** A Stop landing inside launch navigation comes back as an error; report the player's own Stop as MANUALLY_STOPPED. */
        fun resultForStoppedRun(result: TaskResult, botStopReason: String?): TaskResult =
            if (botStopReason == null && result is TaskResult.Error) {
                TaskResult.Success(TaskResultCode.TASK_RESULT_MANUALLY_STOPPED, "Bot was manually stopped by the user.")
            } else {
                result
            }

        /** The overlay Stop ends the service without setting [queueStopRequested]; no bot stop reason and no posted exception means the player. */
        fun isOverlayStop(botRunning: Boolean, botStopReason: String?, runPostedException: Boolean): Boolean =
            !botRunning && botStopReason == null && !runPostedException

        /** The library treats an InterruptedException as a manual stop; any other posted exception is a crash. */
        fun isCrash(exception: Throwable): Boolean = exception !is InterruptedException

        /** Judged from the run's own result: an earlier errored or skipped run is not counted as completed. */
        fun finishesLastCareer(runIndex: Int, totalRuns: Int, resultCode: TaskResultCode): Boolean =
            runIndex == totalRuns && resultCode == TaskResultCode.TASK_RESULT_COMPLETE

        /** Retries a queue may spend per Start: enough to ride out a one-off error, too few to loop on a career that keeps failing. */
        const val RUN_RETRY_BUDGET = 2

        /** Endings that leave the run's career unfinished in the game's slot after an error inside it. */
        private val RETRYABLE_RUN_RESULTS =
            setOf(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, TaskResultCode.TASK_RESULT_CONNECTION_ERROR, TaskResultCode.TASK_RESULT_TIMED_OUT)

        /**
         * Whether a queue run that just ended with [resultCode] is played again as the same run. Its
         * career is still in the slot, and moving on would let the next run finish it under the next
         * run's number. Within the [RUN_RETRY_BUDGET] (the run loop asks once per run), and never for
         * a stop, a skip, a dead service, a game that could not be recovered, an accessibility halt,
         * a diagnostic, or the career-less misc modes.
         *
         * Pure: unit tested in RunRetryAndResumeTest.
         */
        fun decideRunRetry(
            resultCode: TaskResultCode,
            enableRunQueue: Boolean,
            miscMode: Boolean,
            diagnostic: Boolean,
            queueStopRequested: Boolean,
            skipRequested: Boolean,
            botRunning: Boolean,
            gameRecoveryFailed: Boolean,
            accessibilityHalt: Boolean,
            retriesLeft: Int,
        ): Boolean =
            resultCode in RETRYABLE_RUN_RESULTS &&
                enableRunQueue &&
                !miscMode &&
                !diagnostic &&
                !queueStopRequested &&
                !skipRequested &&
                botRunning &&
                !gameRecoveryFailed &&
                !accessibilityHalt &&
                retriesLeft > 0

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
                excludes =
                    (0 until arr.length()).map { i ->
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
        /** True when at least one snapshot row exists for rotation [index]. Read-only existence
         * probe for the non-UI start preflight; never applies anything. Fails toward "missing",
         * which only ever produces the actionable start-from-the-app error. */
        fun rotationSnapshotExists(context: Context, index: Int): Boolean =
            try {
                val dbFile = File(context.filesDir, "SQLite/settings.db")
                if (!dbFile.exists()) {
                    false
                } else {
                    SettingsDatabase.get(context).rawQuery("SELECT 1 FROM settings WHERE category GLOB ? LIMIT 1", arrayOf("rot${index}_*")).use { it.moveToFirst() }
                }
            } catch (_: Exception) {
                false
            }

        fun applyRotationSnapshot(context: Context, index: Int): Boolean {
            return try {
                val dbFile = File(context.filesDir, "SQLite/settings.db")
                if (!dbFile.exists()) return false
                val db = SettingsDatabase.get(context)
                run {
                    val prefix = "rot${index}_"
                    fun readRows(): List<Triple<String, String, String>> {
                        val out = mutableListOf<Triple<String, String, String>>()
                        db.rawQuery(
                            "SELECT category, key, value FROM settings WHERE category GLOB ?",
                            arrayOf("rot${index}_*"),
                        ).use { cursor ->
                            while (cursor.moveToNext()) {
                                out.add(
                                    Triple(
                                        cursor.getString(0).substring(prefix.length),
                                        cursor.getString(1),
                                        cursor.getString(2) ?: "",
                                    ),
                                )
                            }
                        }
                        return out
                    }

                    var rows = readRows()
                    // The frontend persists settings on a debounce, so a queue started right after
                    // configuring the rotation can read the slots before the snapshot flush lands
                    // (live 2026-07-27: "set up and started" died before run 1 on an empty rot0_).
                    // A short settle-retry outlasts the debounce; a genuinely unconfigured slot
                    // still fails, just a few seconds later.
                    var attempt = 0
                    while (rows.isEmpty() && attempt < 3) {
                        attempt++
                        MessageLog.w(
                            TAG,
                            "[ROTATION] No snapshot rows for index $index yet; waiting for the settings flush (attempt $attempt/3).",
                        )
                        Thread.sleep(2000)
                        rows = readRows()
                    }
                    if (rows.isEmpty()) {
                        MessageLog.e(TAG, "[ROTATION] No snapshot rows for index $index (prefix '$prefix'). Cannot switch trainee.")
                        return false
                    }
                    db.beginTransaction()
                    try {
                        for ((category, key, value) in rows) {
                            // Device-level categories must never ride a per-trainee snapshot: stale
                            // debug rows silently reverted the user's Debug Mode toggle (and OCR
                            // tuning) at every run start while the UI kept showing it enabled.
                            // Skipped on apply as well as build so old snapshots stay harmless.
                            if (category == "debug") continue
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
                }
            } catch (e: Exception) {
                MessageLog.e(TAG, "[ROTATION] Failed to apply snapshot for index $index: ${e.message}")
                false
            }
        }

        /** `category.key` of every snapshot row whose live value differs or is missing. `debug` rows never ride a snapshot. */
        internal fun rotationSlotDrift(slotRows: List<Triple<String, String, String>>, live: Map<String, String>): List<String> =
            slotRows.filter { (category, key, value) -> category != "debug" && live["$category.$key"] != value }.map { "${it.first}.${it.second}" }.sorted()

        /**
         * Re-applies slot [index] when the live settings drifted from it since the switch. The app saves its whole in-memory config when it
         * leaves the foreground, which can land between the switch and the run start, and the scenario is fixed once the run's Game is built.
         */
        fun reapplyRotationSlotIfDrifted(context: Context, index: Int, run: Int) {
            try {
                val prefix = "rot${index}_"
                val slotRows = mutableListOf<Triple<String, String, String>>()
                val live = mutableMapOf<String, String>()
                SettingsDatabase.get(context).rawQuery(
                    "SELECT substr(s.category, ?), s.key, s.value, l.value FROM settings s " +
                        "LEFT JOIN settings l ON l.category = substr(s.category, ?) AND l.key = s.key WHERE s.category GLOB ?",
                    arrayOf((prefix.length + 1).toString(), (prefix.length + 1).toString(), "$prefix*"),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val category = cursor.getString(0)
                        val key = cursor.getString(1)
                        slotRows.add(Triple(category, key, cursor.getString(2) ?: ""))
                        if (!cursor.isNull(3)) live["$category.$key"] = cursor.getString(3)
                    }
                }
                if (slotRows.isEmpty()) {
                    MessageLog.e(TAG, "[ROTATION] Run $run start: no snapshot rows for rotation slot #${index + 1}, so its settings could not be re-checked.")
                    return
                }
                val drift = rotationSlotDrift(slotRows, live)
                if (drift.isEmpty()) {
                    MessageLog.i(TAG, "[CONFIG_DRIFT] Run $run start: live settings match rotation slot #${index + 1}'s snapshot.")
                    return
                }
                val shown = drift.take(8).joinToString(", ") + if (drift.size > 8) " and ${drift.size - 8} more" else ""
                MessageLog.w(
                    TAG,
                    "[CONFIG_DRIFT] Run $run start: ${drift.size} live setting(s) differ from rotation slot #${index + 1}'s snapshot ($shown). " +
                        "Re-applying the snapshot before the run.",
                )
                if (!applyRotationSnapshot(context, index)) {
                    MessageLog.e(TAG, "[ROTATION] Run $run start: re-applying rotation slot #${index + 1} failed; the career-start check still compares the scenario.")
                }
            } catch (e: Exception) {
                MessageLog.e(TAG, "[ROTATION] Run $run start: could not re-check rotation slot #${index + 1}: ${e.message}")
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
                SettingsDatabase.get(context).execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("queueState", "currentTrainee", inGameName),
                )
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
                SettingsDatabase.get(context).execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("queueState", "currentTraineeExcludes", excludes.joinToString("\n")),
                )
            } catch (e: Exception) {
                Log.w(TAG, "[ROTATION] Failed to record current trainee excludes: ${e.message}")
            }
        }

        /**
         * Keeps the SINGLE-RUN trainee expectation (general.appliedPresetTrainee, normally written
         * by the Home preset apply) in sync when a rotation snapshot becomes the live settings.
         * Without this, snapshots built before the fields existed re-prefix everything EXCEPT the
         * expectation, leaving whatever the last Home apply wrote - and a later single run would
         * then hunt the wrong trainee on purpose, the exact bug the expectation exists to prevent.
         */
        fun setAppliedPresetTrainee(context: Context, inGameName: String, excludes: List<String>) {
            try {
                val dbFile = File(context.filesDir, "SQLite/settings.db")
                if (!dbFile.exists()) return
                val db = SettingsDatabase.get(context)
                db.execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("general", "appliedPresetTrainee", inGameName),
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("general", "appliedPresetTraineeExcludes", excludes.joinToString("\n")),
                )
            } catch (e: Exception) {
                Log.w(TAG, "[ROTATION] Failed to record applied-preset trainee: ${e.message}")
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
            setQueueStateValue(context, "rotationSwitchPending", pending.toString())
        }

        /**
         * Writes one queueState key straight to the settings DB. Kotlin-side state only: queueState is
         * excluded from the RN settings load and serialization, so nothing written here reaches the
         * frontend or exported profiles.
         */
        fun setQueueStateValue(context: Context, key: String, value: String) {
            try {
                val dbFile = File(context.filesDir, "SQLite/settings.db")
                if (!dbFile.exists()) return
                SettingsDatabase.get(context).execSQL(
                    "INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)",
                    arrayOf("queueState", key, value),
                )
            } catch (e: Exception) {
                Log.w(TAG, "[ROTATION] Failed to write queueState.$key: ${e.message}")
            }
        }

        /**
         * Cursor shift folded into every rotation slot lookup this session. Normally 0; set by
         * [resyncRotationOntoCareer] so the run the mismatch guard resynced counts as the
         * on-screen trainee's slot and later runs continue the cycle from her entry.
         */
        @Volatile
        private var rotationCursorOffset: Int = 0

        /**
         * Rotation slot the mismatch guard resynced the in-flight run onto, or -1. Consumed (and
         * cleared) by the next [applyRotationForRun] so its switch-boundary check compares against
         * the entry actually live in settings — the resynced one — instead of the entry the cursor
         * had loaded before the resync (which would skip the snapshot swap when the next slot
         * happens to equal the pre-resync one, e.g. a 2-trainee rotation).
         */
        @Volatile
        private var rotationResyncPrevIndex: Int = -1

        /**
         * 1-based run number currently in flight, mirrored from the queue loop for
         * [resyncRotationOntoCareer]'s cursor math (the mismatch guard lives in Campaign, which
         * doesn't know the loop counter).
         */
        @Volatile
        private var queueCurrentRun: Int = 1

        /**
         * Rotation slot index of the previously launched run this session, or -1 before the first run.
         * Drives switch-boundary detection in [applyRotationForRun]; reset at the top of onStartEvent so
         * the first launched run of every session always (re)loads its trainee snapshot.
         */
        @Volatile
        private var rotationPrevIndex: Int = -1

        /** The rotation slot whose snapshot the in-flight run loaded (a mid-career resync's, else the queue's), or -1 when no queue rotation applied one. */
        fun liveRotationSlot(): Int = if (rotationResyncPrevIndex >= 0) rotationResyncPrevIndex else rotationPrevIndex

        /**
         * Mid-career rotation resync, called by the Campaign trainee-mismatch guard when the career
         * on screen confidently belongs to rotation entry [index] instead of the entry the queue
         * loaded. This is the signature of an externally interrupted queue (game update, kill
         * without resumable state) restarted from entry 0 while the game resumes the old in-flight
         * career.
         *
         * Swaps the live settings to [index]'s snapshot, retargets the match-or-stop bookkeeping,
         * and fast-forwards the cursor so this run counts as [index]'s slot and later runs continue
         * the cycle from it. The in-flight Campaign keeps whatever values it already read at
         * startup until this career ends; every later read (and every later run) sees the resynced
         * trainee's settings. The offset is session-scoped by design: if the process dies after a
         * resync, the next session's guard simply resyncs again.
         *
         * Refused when [index]'s snapshot runs a different scenario than the career in flight: the
         * running Game/Campaign were constructed for the current scenario and cannot switch
         * mid-career, so applying the snapshot would put the wrong campaign class in charge of the
         * rest of the run.
         *
         * @return false when [index] is out of range, has no snapshot rows, or runs a different
         *         scenario — nothing was changed and the caller must fall back to stopping the queue.
         */
        fun resyncRotationOntoCareer(context: Context, index: Int): Boolean {
            val rotation = loadRotationConfig()
            val target = rotation.inGameNames.getOrNull(index) ?: return false
            val currentScenario = SettingsHelper.getStringSetting("general", "scenario")
            val snapshotScenario = SettingsHelper.getStringSetting("rot${index}_general", "scenario", "")
            if (snapshotScenario.isNotEmpty() && currentScenario.isNotEmpty() && snapshotScenario != currentScenario) {
                MessageLog.e(
                    TAG,
                    "[ROTATION] Resync refused: entry #${index + 1} '$target' runs scenario '$snapshotScenario' but this career " +
                        "is running '$currentScenario', and the campaign cannot switch scenarios mid-career.",
                )
                return false
            }
            if (!applyRotationSnapshot(context, index)) return false
            setCurrentTrainee(context, target)
            setCurrentTraineeExcludes(context, rotation.excludesForIndex(index))
            setAppliedPresetTrainee(context, target, rotation.excludesForIndex(index))
            // Already in-career: no trainee switch can happen for this run, so disarm the backstop.
            setRotationSwitchPending(context, false)
            rotationCursorOffset = rotation.resyncOffsetFor(queueCurrentRun, index)
            rotationResyncPrevIndex = index
            MessageLog.i(
                TAG,
                "[ROTATION] Cursor fast-forwarded: run $queueCurrentRun now maps to trainee #${index + 1} '$target' " +
                    "(offset=$rotationCursorOffset); subsequent runs continue the rotation from her entry.",
            )
            return true
        }
    }

    private val context: Context = reactContext.applicationContext
    private var messageId = 1

    /** Bounded hand-off between MessageLog's lock-held EventBus post and the bridge worker. */
    private val jsEventQueue = java.util.concurrent.ArrayBlockingQueue<JSEvent>(512)

    init {
        StartModule.reactContext = reactContext
        StartModule.reactContext?.addActivityEventListener(this)
        // A request acknowledged by a previous JS instance can no longer be revoked from the UI.
        DebugTestGate.revokePending()
        Log.d(TAG, "StartModule is now initialized.")
    }

    override fun getName(): String {
        return "StartModule"
    }

    override fun onNewIntent(intent: Intent) {
        // Empty implementation
    }

    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 100 && (resultCode != Activity.RESULT_OK || data == null)) {
            DebugTestGate.cancel()
            return
        }
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
    /**
     * Stores the launch identity the React Start barrier just verified on disk (the
     * preset-apply revision plus the content hash). The bot session entry re-reads the
     * revision from SQLite and aborts before any game interaction if it no longer matches --
     * a write landing between React's verification and the Kotlin load would otherwise launch
     * a configuration nobody verified. Single-use; consumed by the session's verdict.
     */
    @ReactMethod
    fun setVerifiedLaunchIdentity(revision: Double, hash: String, launch: String, promise: Promise) {
        try {
            check(!sessionActive.get()) { "A session is already active" }
            val token = DebugTestGate.prepare(launch)
            com.steve1316.uma_android_automation.bot.LaunchIdentityGate.setExpected(revision.toInt(), hash)
            promise.resolve(token)
        } catch (e: Exception) {
            promise.reject("LAUNCH_REJECTED", e)
        }
    }

    /** Called when the launch choice changes after acknowledgment; resolves whether the request was still pending. */
    @ReactMethod
    fun revokeDiagnosticLaunch(launchId: String, promise: Promise) {
        promise.resolve(DebugTestGate.revoke(launchId))
    }

    @ReactMethod
    fun start(launchId: String) {
        try {
            DebugTestGate.start(launchId)
        } catch (e: Exception) {
            Log.e(TAG, "[DEBUG-TEST] Launch request rejected", e)
            return
        }
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

            // Before the foundation's settings helper opens the file: its default error handler
            // deletes a corrupt database outright. Repair only ever happens at process start.
            if (!SettingsDatabase.checkBeforeStart(context)) {
                refuseStartForDatabase()
                DebugTestGate.cancel()
                return
            }

            // Initialize the SettingsHelper's connection to the SQLite database.
            // This is required to correctly fetch the flag for enabling the Remote Log Viewer.
            if (!SettingsHelper.isAvailable()) {
                SettingsHelper.initialize(context)
            }

            // Start the remote log stream server if enabled in settings.
            val enableRemoteLogViewer = SettingsHelper.getBooleanSetting("debug", "enableRemoteLogViewer", false)
            Log.d(TAG, "Able to start Remote Log Viewer in start(): $enableRemoteLogViewer")
            StatusBoard.reset()
            if (enableRemoteLogViewer) {
                val port = SettingsHelper.getIntSetting("debug", "remoteLogViewerPort", 9000)
                LogStreamServer.start(context, port)
            }

            startProjection()
        } else {
            DebugTestGate.cancel()
        }
    }

    /**
     * Refuses a Start because the settings database is damaged or cannot be opened. Nothing has
     * run yet, so nothing was spent. The player sees why in a dialog, and the refusal is recorded
     * in the queue ledger's history (not in the database it could not trust).
     */
    private fun refuseStartForDatabase() {
        Log.e(TAG, "[START] Refused: the settings database failed its integrity check (state ${SettingsDatabase.startState.name}).")
        QueueLedger.recordDatabaseRefusal(context, BuildConfig.VERSION_NAME)
        MessageLog.e(TAG, "[START] $DATABASE_UNHEALTHY_MESSAGE")
        val activity = reactApplicationContext.currentActivity ?: return
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle("Not started")
                .setMessage(DATABASE_UNHEALTHY_MESSAGE)
                .setPositiveButton(android.R.string.ok, null)
                .show()
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
        DebugTestGate.cancel()
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
     *
     * Delegates to [loadQueueState], the same validated source the auto-resume decision in
     * [onStartEvent] reads, instead of maintaining a second copy of the active/stale/malformed
     * checks. Returns a WritableMap with {currentRun, totalRuns, ageMinutes, phase}, or null if
     * there is nothing resumable.
     */
    @ReactMethod
    fun getInterruptedQueueState(promise: Promise) {
        val saved = loadQueueState(context)
        if (saved == null) {
            promise.resolve(null)
            return
        }
        val map = Arguments.createMap()
        map.putInt("currentRun", saved.currentRun)
        map.putInt("totalRuns", saved.totalRuns)
        map.putDouble("ageMinutes", saved.ageMs / 60000.0)
        map.putString("phase", saved.phase)
        promise.resolve(map)
    }

    /**
     * Whether Start is armed (the capture service is up) and whether the bot is running, from the
     * same flags the update guard reads. Home starts from idle each time its activity is created, so
     * it reads these on mount and on return to the foreground: an activity re-created while armed
     * otherwise showed Start with the overlay still up.
     */
    @ReactMethod
    fun getSessionState(promise: Promise) {
        val map = Arguments.createMap()
        map.putBoolean("armed", MediaProjectionService.isRunning)
        map.putBoolean("botRunning", BotService.isRunning)
        map.putBoolean("stopAfterCareer", stopAfterCareerRequested)
        promise.resolve(map)
    }

    /**
     * The last `RunQueueProgress` payload of the running session, or null. Home reads it on mount and
     * on return to the foreground, so a screen re-created mid-queue shows its progress line again
     * instead of waiting for the next event.
     */
    @ReactMethod
    fun getLastQueueProgress(promise: Promise) {
        promise.resolve(if (isSessionActive()) StatusBoard.snapshot().lastQueueProgress else null)
    }

    /** Clears any persisted interrupted queue state. */
    @ReactMethod
    fun clearInterruptedQueueState() {
        clearQueueState(context)
    }

    /**
     * The last finished session's report with its player words ([lastReportPayload]) as JSON text,
     * or null. A session that died without writing one (the process was killed) is turned into a
     * report first, so this also dates and explains it.
     */
    @ReactMethod
    fun getLastQueueReport(promise: Promise) {
        promise.resolve(lastReportPayload(QueueLedger.lastReport(context)))
    }

    /** Marks the report of [sessionId] dismissed; resolves false when a newer report replaced it. */
    @ReactMethod
    fun dismissLastQueueReport(sessionId: String, promise: Promise) {
        promise.resolve(QueueLedger.dismissLastReport(context, sessionId))
    }

    /** Asks for (or withdraws) a stop once the current career has finished; resolves the request as it now stands. */
    @ReactMethod
    fun setStopAfterCareer(requested: Boolean, promise: Promise) {
        stopAfterCareerRequested = requested
        promise.resolve(stopAfterCareerRequested)
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

    /**
     * Whether WRITE_SECURE_SETTINGS is granted (once, over adb). Without it the bot cannot restore
     * its own accessibility service after an emulator wipes the grant ([Game.ensureAccessibilityService]).
     * Read-only; used by the Start warnings.
     */
    @ReactMethod
    fun hasSecureSettingsGrant(promise: Promise) {
        try {
            promise.resolve(reactApplicationContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
        } catch (e: Exception) {
            promise.reject("SECURE_SETTINGS_GRANT_ERROR", "Failed to read the secure settings grant: ${e.message}")
        }
    }

    /** Whether this app may post notifications (off by default for new installs on Android 13+). */
    @ReactMethod
    fun areNotificationsEnabled(promise: Promise) {
        try {
            promise.resolve(NotificationManagerCompat.from(reactApplicationContext).areNotificationsEnabled())
        } catch (e: Exception) {
            promise.reject("NOTIFICATIONS_ENABLED_ERROR", "Failed to read the notification setting: ${e.message}")
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
     * @param status "starting", "resuming", "navigating", "waiting", "completed" (per-run,
     *   non-terminal), or one of the terminal statuses: "queueComplete", "queueStopped" (plain
     *   user Stop), "queueHalted" (a controlled internal stop with a known reason), "queueFailed"
     *   (breakpoint or an unexpected failure, distinguished by resultCode).
     * @param resultCode Optional TaskResultCode name; a stable category the JS side can map to
     *   safe copy without trusting free text.
     * @param message Optional descriptive text. Whether it is safe to show a player depends on
     *   [status]: the terminal statuses carry a dev-authored sentence or a breakpoint's own
     *   description, while the per-run "completed" carries the task's own message, which can be
     *   raw exception text. A UI consumer must surface it only for the statuses whose producer
     *   marks it player-safe.
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
        StatusBoard.queueProgress(currentRun, totalRuns, status, payload.toString())
        ProgressNotification.refresh()
        sendEvent("RunQueueProgress", payload.toString())
    }

    /**
     * Pushes an out-of-band alert when the queue halts with runs still owed.
     *
     * An unattended queue that stops is only expensive because nobody finds out: the 2026-07-26
     * halt cost 6h21m and two careers, and every minute of that was waiting for a human. Discord is
     * the one channel this app can reach on its own (the foreground notification belongs to the
     * foundation library), and the other breakpoints already use it, so a halt should too. It is
     * opt-in and best-effort: a failure here must never mask the halt itself, which is already on
     * the log above this call.
     */
    private fun notifyQueueHalted(completedRuns: Int, totalRuns: Int, unrun: Int, reason: String, careerInFlight: Boolean) {
        if (!DiscordUtils.enableDiscordNotifications) return
        try {
            DiscordUtils.queue.add(
                "```diff\n- ${MessageLog.getSystemTimeString()} QUEUE HALTED after $completedRuns of $totalRuns runs " +
                    "($unrun not started).\n- Reason: $reason\n- " +
                    (
                        if (careerInFlight) {
                            "The career slot is occupied; no further run can start until this is handled in-game."
                        } else {
                            "No career is in flight; clear whatever screen the game is parked on, then restart to resume."
                        }
                    ) + "\n```",
            )
        } catch (e: Exception) {
            Log.e(TAG, "[ERROR] notifyQueueHalted:: Could not queue the Discord alert: ${e.message}")
        }
    }

    /** Set when the session starts reading its launch snapshot, so a refusal the gate throws before that is told apart from a failed read. */
    @Volatile
    private var launchSnapshotReadStarted = false

    /** Set when the launch snapshot read returns; with [launchSnapshotReadStarted] it tells a throw inside the read from the gate's refusals after it. */
    @Volatile
    private var launchSnapshotReadFinished = false

    /** Whether the last [runSingleGame] ended by posting an ExceptionEvent, which also stops the service. */
    @Volatile
    private var lastRunPostedException = false

    /** Set just before [runSingleGame] when that run re-enters a career already in the game's slot; the call consumes it. */
    @Volatile
    private var nextRunCareerInFlight = false

    /** Set when the navigation before the next run started its career itself; the next [runSingleGame] consumes it. */
    @Volatile
    private var nextRunCareerLaunched = false

    /**
     * Runs a single Game instance on a background thread and returns its TaskResult.
     *
     * @return The TaskResult from Game.start(), or an Error result if an exception occurred.
     */
    private fun runSingleGame(selection: DebugTestGate.Selection? = null): TaskResult {
        var taskResult: TaskResult? = null
        lastRunPostedException = false
        val careerInFlight = nextRunCareerInFlight.also { nextRunCareerInFlight = false }
        val careerLaunched = nextRunCareerLaunched.also { nextRunCareerLaunched = false }

        val botThread =
            Thread {
                try {
                    val entryPoint = Game(context, selection, careerInFlight, careerLaunched)
                    taskResult = entryPoint.start()
                } catch (e: Exception) {
                    EventBus.getDefault().postSticky(ExceptionEvent(e))
                    lastRunPostedException = isCrash(e)
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
     * Returns false if the wait was cut short by a stop, true if it ran to completion.
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
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                // The overlay Stop interrupts this thread and only then tears the service down, so
                // the interrupt arrives before either flag above, and letting it escape skipped the
                // queue's terminal report entirely. Thread.sleep leaves the interrupt flag clear;
                // keep it clear while the stop attribution settles.
                awaitStopEvidence()
                return false
            }
            elapsed += 100
        }
        return true
    }

    /**
     * Polls for up to [STOP_EVIDENCE_SETTLE_MS] until a stop becomes visible in
     * [queueStopRequested] or BotService.isRunning, after an interrupt that landed ahead of the
     * flag it belongs to. Without it the post-loop classifier can still read a running service and
     * report an aborted queue as complete. Task.interruptResult settles the same race at run level.
     */
    private fun awaitStopEvidence() {
        val deadline = System.currentTimeMillis() + STOP_EVIDENCE_SETTLE_MS
        while (System.currentTimeMillis() < deadline && !queueStopRequested && BotService.isRunning) {
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                // A second interrupt changes nothing about what the classifier needs to see.
            }
        }
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
        // Fold in a mid-career resync: the boundary check must compare against the entry actually
        // live in settings (the resynced one), not the entry the cursor loaded before the resync.
        if (rotationResyncPrevIndex >= 0) {
            rotationPrevIndex = rotationResyncPrevIndex
            rotationResyncPrevIndex = -1
        }
        val index = rotation.indexForRun(upcomingRun, rotationCursorOffset)
        val target = rotation.inGameNames.getOrElse(index) { "" }
        val switching = index != rotationPrevIndex
        if (switching) {
            if (!applyRotationSnapshot(context, index)) return null
            MessageLog.i(TAG, "[ROTATION] Run $upcomingRun -> switching to trainee #${index + 1} '$target' (snapshot loaded).")
        }
        setCurrentTrainee(context, target)
        setCurrentTraineeExcludes(context, rotation.excludesForIndex(index))
        setAppliedPresetTrainee(context, target, rotation.excludesForIndex(index))
        // Arm the missed-detection backstop only on an actual switch; the navigator clears it when
        // Trainee Select is handled, and fails at Legacy Select if it is still armed.
        setRotationSwitchPending(context, switching)
        rotationPrevIndex = index
        return userReuse
    }

    /** Re-applies the live rotation slot before a run builds its Game. A mid-career resync made its slot the live one; the cursor's slot applies otherwise. */
    private fun reapplyLiveRotationSlot(run: Int) {
        val liveSlot = liveRotationSlot()
        if (liveSlot >= 0) reapplyRotationSlotIfDrifted(context, liveSlot, run)
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
    private fun navigateWithDeadline(
        reuseLastLaunchSetup: Boolean,
        navigator: CareerLaunchNavigator = CareerLaunchNavigator(context),
        finalizeToHome: Boolean = false,
        previousCareerComplete: Boolean = false,
        coldStartOnHome: Boolean = false,
        careerInFlight: Boolean = false,
    ): NavigationResult {
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
                    queueStopKey = "NAVIGATION_UNRESPONSIVE"
                    queueStopReason = "Between-run navigation did not respond to the interrupt within the grace period."
                    queueStopRequested = true
                    Log.e(TAG, "[QUEUE] Navigation thread did not respond to the interrupt. Queue stop requested; the stall watchdog is the next net.")
                }
            }
        deadlineThread.name = "NavDeadline"
        deadlineThread.isDaemon = true
        deadlineThread.start()

        return try {
            navigator.navigate(reuseLastLaunchSetup, finalizeToHome, previousCareerComplete = previousCareerComplete, coldStartOnHome = coldStartOnHome, careerInFlight = careerInFlight)
                .copy(careerResumed = navigator.careerResumed)
        } catch (e: InterruptedException) {
            // Clear the interrupt flag so queue teardown (log saving, events) is not poisoned.
            Thread.interrupted()
            // An interrupted navigation may have died anywhere in the career-end flow, including
            // mid-Spark-Selection. The transaction no longer describes what is on screen, so it
            // is invalidated: a later restart then blocks on the selection screens instead of
            // resuming a choice it cannot verify.
            SparkRerollGate.invalidate("between-run navigation interrupted")
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
                        reasonKey = "NAVIGATION_TIMEOUT",
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
            }.copy(careerResumed = navigator.careerResumed)
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

    internal fun dispatchDiagnostic(readSnapshot: () -> Map<String, String>, run: (DebugTestGate.Selection) -> Unit): DebugTestGate.Selection? {
        var loadedRevision = 0
        var snapshot: Map<String, String> = emptyMap()
        val selection = DebugTestGate.consume {
            readSnapshot().also {
                snapshot = it
                loadedRevision = it["general/settingsRevision"]?.toIntOrNull() ?: 0
            }
        }
        val loadedHash = com.steve1316.uma_android_automation.bot.LaunchIdentityGate.snapshotIdentityHash(snapshot)
        if (!verifyLaunchIdentity(loadedRevision, loadedHash)) return null
        if (selection.key != null) run(selection)
        return selection
    }

    private fun verifyLaunchIdentity(loadedRevision: Int, loadedHash: String): Boolean {
        val expectedIdentity = com.steve1316.uma_android_automation.bot.LaunchIdentityGate.current
        when (com.steve1316.uma_android_automation.bot.LaunchIdentityGate.verdict(loadedRevision, loadedHash)) {
            com.steve1316.uma_android_automation.bot.LaunchIdentityGate.Verdict.MISMATCH -> {
                MessageLog.e(TAG, "[START] $SETTINGS_NOT_DELIVERED_MESSAGE")
                MessageLog.i(
                    TAG,
                    "[START] launch identity mismatch: the bot read revision $loadedRevision, settings hash $loadedHash; " +
                        "the app verified revision ${expectedIdentity?.revision}, hash ${expectedIdentity?.hash}. Aborted before any game interaction.",
                )
                return false
            }
            com.steve1316.uma_android_automation.bot.LaunchIdentityGate.Verdict.PASS -> {
                MessageLog.i(TAG, "[START] launch identity verified: revision=$loadedRevision hash=${expectedIdentity?.hash}")
            }
            com.steve1316.uma_android_automation.bot.LaunchIdentityGate.Verdict.NOT_SET -> {
                // A mismatch consumes the expectation. Keep later unverified starts blocked until
                // a fresh UI-verified identity re-arms the gate.
                if (com.steve1316.uma_android_automation.bot.LaunchIdentityGate.isBlockedAfterMismatch()) {
                    MessageLog.e(TAG, "[START] $SETTINGS_NOT_DELIVERED_MESSAGE")
                    MessageLog.i(TAG, "[START] launch blocked after an earlier launch identity mismatch in this process (revision on disk: $loadedRevision).")
                    return false
                }
                MessageLog.w(TAG, "[START] session started without a verified launch identity (non-UI entry); revision on disk: $loadedRevision.")
            }
        }
        return true
    }

    private fun readLaunchSnapshot(): Map<String, String> {
        launchSnapshotReadStarted = true
        val rows = mutableMapOf<String, String>()
        SettingsDatabase.get(context).let { db ->
            // The same rows the app's Start check reads back and hashes (loadSettingsRowsSnapshot), so
            // the bot can compare its own view of the settings with the one the app verified.
            db.rawQuery("SELECT category, key, value FROM settings WHERE category NOT GLOB 'rot[0-9]*' AND category != 'queueState'", null).use { cursor ->
                while (cursor.moveToNext()) {
                    check(!cursor.isNull(2)) { "Null launch setting" }
                    val key = "${cursor.getString(0)}/${cursor.getString(1)}"
                    check(rows.put(key, cursor.getString(2)) == null) { "Duplicate launch setting" }
                }
            }
        }
        launchSnapshotReadFinished = true
        return rows
    }

    private fun rotationTraineeFor(rotation: RotationConfig, run: Int): String =
        if (rotation.enabled) rotation.inGameNames.getOrElse(rotation.indexForRun(run, rotationCursorOffset)) { "" } else ""

    private fun skipsTrainee(navResult: NavigationResult, rotation: RotationConfig): Boolean =
        !navResult.success && navResult.lastDetectedState != "STOPPED" && unplayableRunStep(navResult.reasonKey, rotation.enabled) == UnplayableRunStep.SKIP

    private fun attachLaunchStop(ledger: SessionLedger, run: Int, stop: TaskResult.Error, trainee: String) {
        val record = ledger.attachLaunchStop(run, stop.reasonKey, stop.reasonTrainee, stop.reasonOutfit, trainee) ?: return
        StatusBoard.runUpdated(record)
        QueueLedger.refreshOpenSession(context, ledger.sessionId, ledger.openJson())
    }

    /** Saves the resume record past skipped run [run] (no career of it is in the slot) and backs out to Home; true once Home shows. */
    private fun leaveSkippedRun(ledger: SessionLedger, run: Int, totalRuns: Int, completedRuns: Int): Boolean {
        saveQueueState(context, active = true, currentRun = run, totalRuns = totalRuns, phase = PHASE_LAUNCHING, completedRuns = completedRuns)
        ledger.phase = StartModule.PHASE_LAUNCHING
        QueueLedger.refreshOpenSession(context, ledger.sessionId, ledger.openJson())
        val home = CareerLaunchNavigator(context).backOutToHome()
        if (!home) MessageLog.e(TAG, "[QUEUE] Could not return the game to its home screen after skipping run $run.")
        return home
    }

    /**
     * Adds run [run]'s record, with the career-end facts only if this run produced them. Returns the
     * sequence of this run's career end, or null when it had none.
     */
    private fun recordRun(ledger: SessionLedger, run: Int, startedAt: Long, careerEndSeqBeforeRun: Long, code: TaskResultCode, retried: Boolean): Long? {
        val stash =
            CareerEndStash(lastCareerEndSeq, lastCareerEndTrainee, lastCareerEndScenario, lastCareerEndOutcome, lastCareerEndTurn, lastCareerEndTraineeName, lastCareerEndResult)
        val careerEnd = careerEndForRun(careerEndSeqBeforeRun, stash)
        val progress = ProgressTracker.endWindow()
        val record =
            RunRecord(
                run,
                startedAt,
                System.currentTimeMillis(),
                code.name,
                careerEnd?.trainee,
                careerEnd?.scenario,
                careerEnd?.outcome,
                careerEnd?.turn,
                retried,
                progress,
                careerEnd?.traineeName,
                careerEnd?.result,
            )
        ledger.addRun(record)
        StatusBoard.runRecorded(record)
        ledger.errorPosted = lastRunPostedException
        QueueLedger.refreshOpenSession(context, ledger.sessionId, ledger.openJson())
        return careerEnd?.seq
    }

    /** The career-end flow reads the sparks after [recordRun]: adds the kept set to run [run] when it followed that run's own career end. */
    private fun attachCareerEndSparks(ledger: SessionLedger, run: Int, runCareerEndSeq: Long?) {
        val kept = sparksForRun(runCareerEndSeq, lastCareerEndSparks) ?: return
        val record = ledger.attachSparks(run, kept) ?: return
        StatusBoard.runUpdated(record)
        QueueLedger.refreshOpenSession(context, ledger.sessionId, ledger.openJson())
    }

    /** Records that the session is alive every [QueueLedger.HEARTBEAT_MS], so a later death is dated. */
    private fun startLedgerHeartbeat(sessionId: String): Thread =
        Thread {
            while (true) {
                try {
                    Thread.sleep(QueueLedger.HEARTBEAT_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                QueueLedger.markAlive(context, sessionId)
            }
        }.apply {
            name = "QueueLedgerHeartbeat"
            isDaemon = true
            start()
        }

    /**
     * Classifies how the session ended, stores its report and returns it (null if it could not be
     * built). Never throws, not even an Error: it runs in the session's finally, ahead of the latch
     * release.
     */
    private fun writeSessionReport(ledger: SessionLedger): QueueReport? {
        return try {
            val facts =
                ledger.facts(
                    stopRequested = queueStopRequested,
                    stopByBot = queueStopReason != null,
                    serviceRunning = BotService.isRunning,
                    queueStateActive = loadQueueState(context) != null,
                )
            val verdict = classifySessionEnd(facts)
            if (verdict.end == SessionEnd.STOPPED_BY_BOT) ledger.reasonKey = queueStopKey.orEmpty()
            ledger.report(verdict, System.currentTimeMillis()).also { QueueLedger.finishSession(context, it) }.also { StatusBoard.sessionEnded(it) }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to write the queue report: ${e.message}")
            null
        }
    }

    /**
     * Replaces the library's end notification, which reads "Completed successfully with no errors."
     * however the session ended, with how it did end, or removes it. The library posts its text from
     * [libraryThread] after the session returns, so a daemon thread waits for that thread to finish
     * first; [finishEndNotification] then updates it while the capture service is still up (a tap on
     * the overlay's Stop), and removes it once the service is gone (the app's Stop, the overlay's
     * dismiss), whose cancel-all can run before that late post. Never throws.
     */
    private fun notifySessionEnd(libraryThread: Thread, report: QueueReport?) {
        ProgressNotification.end()
        try {
            val text = queueReportText(report?.toJson())
            Thread {
                try {
                    libraryThread.join()
                    Thread.sleep(END_NOTIFICATION_DELAY_MS)
                    finishEndNotification(
                        captureRunning = { MediaProjectionService.isRunning },
                        sessionRunning = { BotService.isRunning },
                        update = { NotificationUtils.updateNotification(context, MainActivity::class.java, false, text.body, title = text.title, displayBigText = true) },
                        remove = { NotificationManagerCompat.from(context).cancel(LIBRARY_NOTIFICATION_ID) },
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to update the end notification: ${e.message}")
                }
            }.apply {
                name = "SessionEndNotification"
                isDaemon = true
                start()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to start the end notification update: ${e.message}")
        }
    }

    /**
     * Logs a failed keep-screen-on hold from a short daemon thread, never the main looper the
     * [KeepScreenOn.start] callback itself runs on: MessageLog holds a single process-wide lock, and
     * the UI thread must never be the one waiting on it.
     */
    private fun logKeepScreenOnHoldFailure() {
        Thread {
            MessageLog.w(TAG, "[START] Could not keep the screen on; the screen timeout can stop this session.")
        }.apply {
            name = "KeepScreenOnFailedLog"
            isDaemon = true
            start()
        }
    }

    @Subscribe
    fun onStartEvent(event: StartEvent) {
        if (event.message == "Entry Point ON") {
            if (!sessionActive.compareAndSet(false, true)) {
                MessageLog.w(TAG, "[START] A bot session is already active; ignoring the duplicate start event.")
                return
            }
            // Acquire a PARTIAL_WAKE_LOCK for the entire bot session so Android's OomAdjuster
            // doesn't mark the process as 'empty' and SIGKILL it under memory pressure (TRIM_EMPTY).
            // Released in the finally below regardless of how the session ends.
            Game.acquireWakeLock(context)
            // The library's thread: it posts its own end notification once this session returns.
            val libraryThread = Thread.currentThread()
            val ledger = SessionLedger(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), BuildConfig.VERSION_NAME, android.os.Process.myPid())
            var ledgerHeartbeat: Thread? = null
            try {
                // Removed in the finally, so every exit releases the screen.
                if (!KeepScreenOn.start(context, onHoldFailed = { logKeepScreenOnHoldFailure() })) {
                    MessageLog.w(TAG, "[START] UMA Auto+ cannot draw over other apps, so it cannot keep the screen on; the screen timeout can stop this session.")
                }
                // Reset queue control flags at the start of every new session.
                // Before diagnostic dispatch: Game.wait aborts on a Stop left over from the previous session.
                queueStopRequested = false
                queueStopReason = null
                queueStopKey = null
                queueSkipRequested = false
                stopAfterCareerRequested = false
                gameRecoveryFailed = false
                accessibilityHaltKey = null
                SessionTally.reset()
                ProgressTracker.beginWindow()
                StatusBoard.reset(ledger.startedAt)
                ProgressNotification.begin(captureRunning = { MediaProjectionService.isRunning }) { text ->
                    NotificationUtils.updateNotification(context, MainActivity::class.java, true, text)
                }
                launchSnapshotReadStarted = false
                launchSnapshotReadFinished = false

                // BotService has initialized SettingsHelper; read one SQLite snapshot after restoration.
                val nonUiEntry = com.steve1316.uma_android_automation.bot.LaunchIdentityGate.current == null
                val launchSelection = dispatchDiagnostic(::readLaunchSnapshot) { selection -> runSingleGame(selection) }
                ledger.dispatchReturned = true
                if (launchSelection == null) {
                    ledger.launchIdentityRefused = true
                    return
                }
                val armedDebugTests = listOfNotNull(launchSelection.key)
                val debugDiagnosticArmed = armedDebugTests.isNotEmpty()
                // Dispatch before queue-state writes, rotation preparation or career navigation.
                if (debugDiagnosticArmed) {
                    ledger.diagnosticRan = true
                    return
                }
                // Report a session a previous process lost before this one reads or clears the
                // resume record, so that report says what was resumable when it died.
                QueueLedger.reportDeadSession(context)
                // The library's session-end log save doesn't world-read its file the way
                // writePerCareerLog does, which locks adb triage pulls out of exactly the
                // segment that holds the between-run navigation and the sparks screens
                // (observed 2026-07-12). Best-effort sweep at session start keeps every
                // log file pullable, including ones written by past sessions.
                try {
                    context.getExternalFilesDir(null)?.let { filesDir ->
                        java.io.File(filesDir, "logs").listFiles()?.forEach { it.setReadable(true, false) }
                    }
                } catch (_: Exception) {
                }

                // Reset rotation boundary tracking so the first launched run of this session always
                // (re)loads its trainee snapshot, even within the same app process as a prior queue.
                rotationPrevIndex = -1
                rotationCursorOffset = 0
                rotationResyncPrevIndex = -1
                queueCurrentRun = 1
                nextRunCareerLaunched = false
                setRotationSwitchPending(context, false)

                // Reset the log stream mute to ensure logs for the new run are broadcasted.
                LogStreamServer.resetMute()

                // Read queue settings from SQLite.
                //
                // Grand Concert was capability-gated to a single supervised run while its Lesson
                // and concert screens still stopped for manual input. They no longer do: careers
                // now play the Lesson shop, all five concerts, the career-end sequence and the
                // spark selection unattended, and the navigator pages the scenario carousel to
                // Grand Concert like any other scenario, so a queue can launch the next career.
                // The gate is therefore gone and the queue, trainee rotation and TP restore all
                // honour the user's stored settings here as they do elsewhere.
                val enableRunQueue = SettingsHelper.getBooleanSetting("runQueue", "enableRunQueue", true)
                val totalRuns = if (enableRunQueue) SettingsHelper.getIntSetting("runQueue", "totalRuns", 5) else 1
                val delayBetweenRuns = SettingsHelper.getIntSetting("runQueue", "delayBetweenRunsSeconds", 15)
                val stopOnError = SettingsHelper.getBooleanSetting("runQueue", "stopOnError", false)
                val reuseLastLaunchSetup = SettingsHelper.getBooleanSetting("runQueue", "reuseLastLaunchSetup", true)

                // Reset the session-scoped TP restore counter and size its budget from the
                // configured queue length (companion-held so it survives the per-handoff
                // navigator reconstruction). Sits below the settings read because it needs
                // totalRuns, and before the first navigator construction, which consumes it.
                CareerLaunchNavigator.resetTpRestoresForSession(totalRuns)
                ledger.queueEnabled = enableRunQueue
                ledger.totalRuns = totalRuns

                // Trainee rotation: parse the cycle once, up here so the auto-resume decision below
                // can distinguish a rotation queue (which must re-enter an interrupted career, never
                // skip to the next trainee's snapshot) from a normal single-trainee queue. Also used
                // by the cold-start snapshot load and the between-run switch further down.
                val rotation = loadRotationConfig()
                if (enableRunQueue && rotation.enabled) {
                    MessageLog.i(TAG, "[ROTATION] Enabled: ${rotation.count} trainees, switching every ${rotation.switchEvery} run(s).")
                    // A rotation queue depends on the per-trainee snapshots the app's Start flow
                    // precomputes. A non-UI start (the floating overlay's play button) skips that
                    // flow entirely, so if the snapshots are absent the queue can only die at run
                    // 1 with a cryptic error (lived 2026-07-27, twice). Fail here with the actual
                    // remedy instead.
                    if (nonUiEntry && !rotationSnapshotExists(context, 0)) {
                        MessageLog.e(
                            TAG,
                            "[ROTATION] The rotation's per-trainee snapshots are missing, and this session was started " +
                                "outside the app (the floating overlay skips the preparation step). Press Start on the " +
                                "app's Home page instead: it builds the snapshots before launching.",
                        )
                        ledger.rotationNotPrepared = true
                        return
                    }
                }

                if (enableRunQueue) {
                    MessageLog.i(
                        TAG,
                        "[QUEUE] Run queue enabled. Total runs: $totalRuns, delay: ${delayBetweenRuns}s, stopOnError: $stopOnError, " +
                            "tpRestoreBudget: ${CareerLaunchNavigator.sessionRestoreCapFor(totalRuns)}",
                    )
                }

                // --- Layer 4: auto-resume after process death ---
                // If a queue was running when the previous process was killed (TRIM_EMPTY,
                // watchdog self-restart, etc.), SQLite still has queueState.active=true with
                // the run number that was in flight. Re-enter that run if its career was in
                // flight, else start the next one. Only applies when queueing is currently enabled AND the saved totalRuns
                // matches the current setting. If the user changed queue config after the
                // crash, the saved state is no longer applicable and we ignore it.
                // Careers already finished before this process started, per the persisted queue
                // state (resumePlanFor). Feeds completedRuns below so a queue resumed at run 4 of 6
                // that goes on to finish runs 4-6 reports 6/6 completed, not 3/6 (2026-07-28 undercount).
                var priorCompletedRuns = 0
                // True when the resume re-enters a career that was in flight: the cold start below then
                // has a career in the slot, so the navigator may not treat it as a career-free Start.
                var resumeReEntersCareer = false
                var resumedQueue = false

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
                        val plan = resumePlanFor(saved.phase, saved.currentRun, saved.completedRuns)
                        val reEnter = saved.phase == PHASE_CAREER
                        resumeReEntersCareer = reEnter
                        val next = plan.startFromRun
                        priorCompletedRuns = plan.priorCompletedRuns
                        if (next > totalRuns) {
                            MessageLog.i(
                                TAG,
                                "[RESUME] Saved queue was at its last run (${saved.currentRun}/${saved.totalRuns}); nothing to resume. Treating as complete.",
                            )
                            clearQueueState(context)
                            ledger.nothingToResume = true
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
                        resumedQueue = true
                        next
                    }

                // Seeded from the persisted state so a resumed queue counts the whole queue,
                // not just what this process launch played.
                var completedRuns = priorCompletedRuns

                // The session is now open: a process death from here on is reported at the next
                // app start, dated by the heartbeat or Android's exit record.
                ledger.startFromRun = startFromRun
                ledger.completedRuns = completedRuns
                if (resumedQueue) ledger.earlier = runCatching { earlierQueueFor(QueueLedger.lastReport(context)?.let { JSONObject(it) }, totalRuns, startFromRun) }.getOrNull()
                QueueLedger.beginSession(context, ledger.sessionId, ledger.openJson())
                ledgerHeartbeat = startLedgerHeartbeat(ledger.sessionId)

                // Non-null once the queue exits for a reason the user did not ask for. The post-loop
                // block used to log "Queue finished" and emit queueComplete no matter how the loop
                // ended, so a queue abandoned mid-way reported success: the 2026-07-26 breakpoint
                // logged "Queue finished. Completed 2 of 4 runs" at 23:45 and nothing said otherwise
                // for the next 6h21m. The navigation-failure paths were equally misreported - they
                // emit queueFailed and then the post-loop queueComplete immediately overwrote it.
                // Declared above the two pre-loop failure sites below, not just above the loop: a
                // failure there has to reach the same halt branch, or it falls through to the
                // success branch and clears a resumable queue's saved state on its way out.
                var queueHaltReason: String? = null
                // Safe, source-grounded category for the JS-facing event only (a TaskResultCode
                // name). queueHaltReason above can carry raw navigation-exception text destined
                // for MessageLog/Discord; Home must never see that, so it gets this instead.
                var queueHaltResultCode: String? = null
                // A specific, already-safe reason to show inline in Home, when one exists. Only
                // ever set for breakpoints: CampaignBreakpointException.message describes WHY the
                // breakpoint fired and is developer-authored, not a caught generic exception, so
                // it is safe to surface verbatim. Every other halt site leaves this null and Home
                // falls back to a resultCode-keyed generic reason.
                var queueHaltDetail: String? = null
                // The run index the queue had reached when it halted: the run being played for an
                // in-flight halt, the last finished run for a halt between runs, and startFromRun - 1
                // for a pre-loop failure that reached nothing. Deliberately not completedRuns - the
                // "$unrun not started" tally below counts from the run reached, not from careers finished.
                var queueHaltRun = 0
                // True when the halt leaves a career still occupying the game's single slot. A
                // breakpoint does; a between-run navigation failure after a COMPLETED career does
                // not, and telling the operator to go clear a slot that is already empty sends them
                // looking for the wrong thing.
                var queueHaltCareerInFlight = false
                var lastCareerFinished = false
                // True when a stop ended a run mid-career: that career is still in the slot, so the saved run is kept for Start to re-enter.
                var stopLeftCareer = false
                // The run after which the player's stop after this career paused the queue, or null.
                var stoppedAfterCareerRun: Int? = null
                // True once a career is actually confirmed to exist: the cold-start probe below
                // found the game already off the Home screen (an existing career), or launched one
                // itself. Run queues always reach this before the first run's own Game.start(), so a
                // run past the first one also has it, from a prior run's own launch. A diagnostic or
                // misc-mode run never probes at all, and neither does a plain single run with the
                // queue disabled, so those stay false rather than claim a slot that was never
                // actually confirmed.
                var coldStartConfirmedCareer = false

                fun haltSkipping(run: Int, snapshotMissing: Boolean) {
                    if (snapshotMissing) {
                        queueHaltReason = "missing rotation snapshot for the trainee of run $run"
                        ledger.haltEnd = SessionEnd.NEXT_SNAPSHOT_MISSING
                        queueHaltRun = run - 1
                    } else {
                        queueHaltReason = "run $run was skipped, and the game could not be returned to its home screen"
                        ledger.haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS
                        ledger.reasonKey = "STUCK_ON_SCREEN"
                        queueHaltRun = run
                    }
                    queueHaltResultCode = TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name
                    queueHaltCareerInFlight = false
                }

                // The game never took the Finish of [run]'s career, so it is still in the slot: Start re-enters that run under its own slot.
                fun haltCareerNotFinished(run: Int) {
                    queueHaltReason = "run $run's career is still in progress after its Finish"
                    ledger.haltEnd = SessionEnd.RUN_HALTED
                    ledger.reasonKey = "CAREER_NOT_FINISHED"
                    queueHaltResultCode = TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name
                    queueHaltRun = run
                    queueHaltCareerInFlight = true
                    lastCareerFinished = false
                    completedRuns--
                    saveQueueState(context, active = true, currentRun = run, totalRuns = totalRuns, phase = PHASE_CAREER, completedRuns = completedRuns)
                    ledger.phase = StartModule.PHASE_CAREER
                }

                // Rotation cycle parsed above (before the resume block). The cold-start snapshot for
                // the first launched run is applied just below, before the home-screen probe reads
                // the scenario, so a rotation that switches scenarios launches the correct campaign.
                var coldStartReuse = reuseLastLaunchSetup
                if (enableRunQueue && rotation.enabled && startFromRun <= totalRuns && BotService.isRunning && !queueStopRequested) {
                    val r = applyRotationForRun(rotation, startFromRun, reuseLastLaunchSetup)
                    if (r == null) {
                        queueHaltReason = "missing rotation snapshot for the first trainee (run $startFromRun)"
                        ledger.haltEnd = SessionEnd.FIRST_SNAPSHOT_MISSING
                        queueHaltResultCode = TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name
                        queueHaltRun = startFromRun - 1
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
                if (debugDiagnosticArmed) {
                    // Defense in depth: an explicit diagnostic never authorizes a career launch.
                    MessageLog.i(
                        TAG,
                        "[DEBUG-TEST] StartModule launch suppressed; diagnostic armed: ${armedDebugTests.joinToString(", ")}. " +
                            "No cold-start career navigation is allowed.",
                    )
                } else if (enableRunQueue && startFromRun <= totalRuns && BotService.isRunning && !queueStopRequested) {
                    val scenarioSetting = SettingsHelper.getStringSetting("general", "scenario")
                    val isMiscMode = scenarioSetting == "Daily Races" || scenarioSetting == "Team Trials"
                    // Reuse the probe's navigator for the launch - its Game/CV initialisation is
                    // the expensive part, and navigate() resets all session-scoped flags itself.
                    val coldStartNavigator = if (isMiscMode) null else CareerLaunchNavigator(context)
                    if (coldStartNavigator != null && coldStartNavigator.isOnHomeScreen()) {
                        MessageLog.i(TAG, "[QUEUE] Game is on the home screen. Launching a career for run $startFromRun...")
                        sendQueueProgressEvent(startFromRun, totalRuns, "navigating")
                        val navResult = navigateWithDeadline(coldStartReuse, coldStartNavigator, coldStartOnHome = !resumeReEntersCareer, careerInFlight = resumeReEntersCareer)
                        if (!resumeReEntersCareer && skipsTrainee(navResult, rotation) && coldStartNavigator.backOutToHome()) {
                            MessageLog.w(TAG, "[QUEUE] Run $startFromRun cannot start its trainee (${navResult.reasonKey}): ${navResult.failureReason} Back on the home screen; the run's own launch records the skip.")
                        } else if (!navResult.success) {
                            logNavigationFailure(navResult)
                            // A user Stop mid-navigation is a clean cancellation, not a navigation
                            // failure - no halt reason, so the post-loop queueComplete reports the
                            // ending, same as a Stop during a run.
                            if (navResult.lastDetectedState != "STOPPED") {
                                queueHaltReason = "cold-start career launch failed before run $startFromRun: ${navResult.failureReason}"
                                ledger.haltEnd = SessionEnd.LAUNCH_FAILED_BEFORE_RUN
                                ledger.reasonKey = navResult.reasonKey
                                ledger.reasonTrainee = navResult.reasonTrainee
                                ledger.reasonOutfit = navResult.reasonOutfit
                                ledger.reasonRotation = navResult.reasonRotation
                                queueHaltResultCode = TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name
                                queueHaltRun = startFromRun - 1
                                queueHaltCareerInFlight = resumeReEntersCareer || navResult.careerResumed
                            }
                            queueStopRequested = true
                        } else {
                            coldStartConfirmedCareer = true
                            // Resume re-entered a career already in the slot, so the first run carries it on.
                            if (navResult.careerResumed) resumeReEntersCareer = true
                            nextRunCareerLaunched = navResult.careerLaunched
                        }
                    } else if (coldStartNavigator != null) {
                        // Not confirmed on Home: assume a career already exists, as the unconditional
                        // true this replaced always did. isOnHomeScreen() == false does not prove the
                        // training menu specifically - it also covers a screen the probe did not
                        // recognize - but it is still evidence against an empty Home lobby.
                        coldStartConfirmedCareer = true
                    }
                }

                // True when the previous run left its career in the slot (not finished), so the next
                // run carries it on rather than launching. Read once, by that next run.
                var previousRunLeftCareer = false
                var runRetriesLeft = RUN_RETRY_BUDGET
                for (i in startFromRun..totalRuns) {
                    // Check stop flag before starting each run.
                    if (queueStopRequested || !BotService.isRunning) {
                        MessageLog.i(TAG, "[QUEUE] Queue stop requested before run $i. Exiting queue.")
                        break
                    }

                    // Reset the skip flag for this run.
                    queueSkipRequested = false

                    // Mirror the in-flight run number for the mismatch guard's resync cursor math.
                    queueCurrentRun = i

                    // Re-arm the wake lock's safety timeout at every run boundary - the session
                    // acquire's 6h cap otherwise expires silently partway through a long queue.
                    Game.acquireWakeLock(context)

                    if (enableRunQueue) {
                        // Reset log stream mute for each subsequent run.
                        LogStreamServer.resetMute()
                        // Persist queue state so it can survive crashes. Phase CAREER: run i's
                        // career is about to play, so a kill here resumes by re-entering run i.
                        saveQueueState(context, active = true, currentRun = i, totalRuns = totalRuns, phase = PHASE_CAREER, completedRuns = completedRuns)
                        sendQueueProgressEvent(i, totalRuns, "starting")
                        MessageLog.i(TAG, "\n[QUEUE] ========================================")
                        MessageLog.i(TAG, "[QUEUE] Starting run $i of $totalRuns")
                        MessageLog.i(TAG, "[QUEUE] ========================================\n")
                    }

                    // A REAL career task is about to start: this is the one place a career
                    // finalization identity is created. It drops any verdict the previous career
                    // left and installs this career's nonce, which the career task's Campaign
                    // reads when it arms its verdict. Object constructors (the throwaway Game and
                    // Campaign the navigator builds during its own startup) deliberately do not
                    // touch the gate - a constructor-side clear once erased the verdict the
                    // navigator was about to consume.
                    CareerFinalizeGate.beginCareer(
                        nonce = java.util.UUID.randomUUID().toString().substring(0, 8),
                        queueRun = if (enableRunQueue) i else null,
                        nowMs = System.currentTimeMillis(),
                    )

                    // The spark reroll transaction is deliberately NOT armed here. Run start is
                    // not proof that a career exists: Game.start() still has to run the
                    // cold-start launch navigation, whose legitimate pass through the game's
                    // Home screen destroyed a transaction armed at this point and left a whole
                    // live career unable to price its redraw (2026-07-19). It is armed at the
                    // career attachment boundary inside Game.start() instead; this loop only
                    // invalidates it below, on any non-COMPLETE result.

                    ledger.currentRun = i
                    ledger.phase = StartModule.PHASE_CAREER
                    QueueLedger.refreshOpenSession(context, ledger.sessionId, ledger.openJson())
                    val runStartedAt = System.currentTimeMillis()
                    val careerEndSeqBeforeRun = lastCareerEndSeq

                    if (enableRunQueue && rotation.enabled) reapplyLiveRotationSlot(i)

                    // Run the game. An errored run whose career is still in the slot is played once
                    // more as this same run: the saved phase stays CAREER, the rotation snapshot is
                    // unchanged, and Game.start() re-enters the career without treating it as
                    // finished. Moving on instead would let the next run finish this career.
                    // A resumed in-flight career, a run played again, and a career the previous run
                    // left unfinished are all still in the slot.
                    nextRunCareerInFlight = (i == startFromRun && resumeReEntersCareer) || previousRunLeftCareer
                    previousRunLeftCareer = false
                    var result = runSingleGame()
                    val runScenario = SettingsHelper.getStringSetting("general", "scenario")
                    val retried =
                        decideRunRetry(
                            resultCode = result.code,
                            enableRunQueue = enableRunQueue,
                            miscMode = runScenario == "Daily Races" || runScenario == "Team Trials",
                            diagnostic = debugDiagnosticArmed,
                            queueStopRequested = queueStopRequested,
                            skipRequested = queueSkipRequested,
                            botRunning = BotService.isRunning,
                            gameRecoveryFailed = gameRecoveryFailed,
                            accessibilityHalt = accessibilityHaltKey != null,
                            retriesLeft = runRetriesLeft,
                        )
                    if (retried) {
                        runRetriesLeft--
                        MessageLog.w(TAG, "[QUEUE] Run $i ended with ${result.code} while its career was in progress. Playing run $i once more ($runRetriesLeft retries left this Start).")
                        SparkRerollGate.invalidate("run result ${result.code.name}, run $i retried")
                        CareerFinalizeGate.beginCareer(
                            nonce = java.util.UUID.randomUUID().toString().substring(0, 8),
                            queueRun = i,
                            nowMs = System.currentTimeMillis(),
                        )
                        sendQueueProgressEvent(i, totalRuns, "retrying")
                        if (enableRunQueue && rotation.enabled) reapplyLiveRotationSlot(i)
                        nextRunCareerInFlight = true
                        result = runSingleGame()
                    }

                    if (isOverlayStop(BotService.isRunning, queueStopReason, lastRunPostedException)) queueStopRequested = true

                    // A launch stopped by its trainee's own conflict started no career: a rotation skips it, else it halts.
                    val launchStop = (result as? TaskResult.Error)?.takeIf { enableRunQueue && it.reasonKey.isNotEmpty() }
                    val unplayable = if (queueSkipRequested || queueStopRequested) null else launchStop?.let { unplayableRunStep(it.reasonKey, rotation.enabled) }

                    // Determine the effective result considering queue flags.
                    val effectiveResult =
                        when {
                            queueSkipRequested -> {
                                MessageLog.i(TAG, "[QUEUE] Run $i was skipped by queue.")
                                TaskResult.Success(TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE, "Run was skipped by queue.")
                            }
                            queueStopRequested -> {
                                MessageLog.i(TAG, "[QUEUE] Run $i stopped: ${queueStopReason ?: "user stop"}.")
                                resultForStoppedRun(result, queueStopReason)
                            }
                            unplayable == UnplayableRunStep.SKIP -> {
                                MessageLog.w(TAG, "[QUEUE] Run $i cannot start its trainee (${launchStop?.reasonKey}). Skipping it; the rotation goes on with the next trainee.")
                                TaskResult.Success(TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE, "Run $i was skipped: its trainee cannot start.")
                            }
                            else -> result
                        }
                    if (finishesLastCareer(i, totalRuns, effectiveResult.code)) lastCareerFinished = true
                    val runError = effectiveResult as? TaskResult.Error
                    if (!enableRunQueue && runError != null && runError.reasonKey.isNotEmpty()) {
                        ledger.reasonKey = runError.reasonKey
                        ledger.reasonTrainee = runError.reasonTrainee
                        ledger.reasonOutfit = runError.reasonOutfit
                    }
                    val runCareerEndSeq = recordRun(ledger, i, runStartedAt, careerEndSeqBeforeRun, effectiveResult.code, retried)
                    launchStop?.let { attachLaunchStop(ledger, i, it, it.reasonTrainee.ifEmpty { rotationTraineeFor(rotation, i) }) }

                    if (enableRunQueue) {
                        sendQueueProgressEvent(i, totalRuns, "completed", effectiveResult.code.name, effectiveResult.message)
                    }

                    // A run that did not complete must never leave a finalization verdict armed:
                    // the verdict describes a career whose finalization is no longer the next
                    // thing that happens (manual stop, abort, error, breakpoint, skip). Only a
                    // COMPLETE run's verdict survives into the finalize navigation that follows.
                    // The decision itself is the pure shouldClearVerdictForRunResult, pinned by
                    // JUnit across every result code.
                    if (shouldClearVerdictForRunResult(effectiveResult.code)) {
                        CareerFinalizeGate.clear()
                    }
                    // The spark transaction follows the identical rule (pure, JUnit-pinned):
                    // only a COMPLETE career's spark flow is the next thing on screen.
                    if (shouldClearSparkTransactionForRunResult(effectiveResult.code)) {
                        SparkRerollGate.invalidate("run result ${effectiveResult.code.name}")
                    }

                    if (unplayable == UnplayableRunStep.HALT && launchStop != null) {
                        // Without a rotation every run is this trainee and would stop the same way.
                        MessageLog.e(TAG, "[QUEUE] Run $i cannot start its trainee (${launchStop.reasonKey}). Without a rotation every run is this trainee, so the queue stops here.")
                        queueHaltReason = "run $i could not start its trainee (${launchStop.reasonKey})"
                        ledger.haltEnd = SessionEnd.LAUNCH_FAILED_BEFORE_RUN
                        ledger.reasonKey = launchStop.reasonKey
                        ledger.reasonTrainee = launchStop.reasonTrainee
                        ledger.reasonOutfit = launchStop.reasonOutfit
                        ledger.reasonRotation = false
                        queueHaltResultCode = effectiveResult.code.name
                        queueHaltRun = i - 1
                        queueHaltCareerInFlight = false
                        if (i > 1) saveQueueState(context, active = true, currentRun = i - 1, totalRuns = totalRuns, phase = PHASE_LAUNCHING, completedRuns = completedRuns) else clearQueueState(context)
                        break
                    }

                    // Evaluate the result.
                    when (effectiveResult.code) {
                        TaskResultCode.TASK_RESULT_MANUALLY_STOPPED -> {
                            // A genuine user Stop OR a deliberate internal queue-stop (e.g. the
                            // trainee-mismatch guard, which sets queueStopReason). Either way we exit the
                            // queue; the reason makes the log honest about which one it actually was.
                            if (!queueSkipRequested) {
                                MessageLog.i(TAG, "[QUEUE] ${queueStopReason ?: "User stopped the bot"}. Exiting queue.")
                                stopLeftCareer = runScenario != "Daily Races" && runScenario != "Team Trials"
                                break
                            }
                        }
                        // completedRuns counts finished careers only. A skipped, errored or
                        // breakpointed run is in the ledger's run records with its own result.
                        TaskResultCode.TASK_RESULT_COMPLETE -> {
                            completedRuns++
                        }
                        TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE -> {
                            MessageLog.i(TAG, "[QUEUE] Run $i was skipped; it is not counted as a finished career.")
                        }
                        TaskResultCode.TASK_RESULT_BREAKPOINT_REACHED -> {
                            // Breakpoints stop the queue, and that part is not negotiable: the game
                            // has a single career slot, so the preserved career blocks every later
                            // run whether the breakpoint was user-set (skill-point threshold, a
                            // mandatory race) or a screen the bot could not drive. What this must
                            // NOT do is stop quietly - see queueHaltReason.
                            if (enableRunQueue) {
                                MessageLog.e(TAG, "[QUEUE] Run $i hit a breakpoint. Stopping queue: ${effectiveResult.message}")
                            }
                            queueHaltReason = "run $i hit a breakpoint: ${effectiveResult.message}"
                            ledger.haltEnd = SessionEnd.BREAKPOINT
                            queueHaltResultCode = TaskResultCode.TASK_RESULT_BREAKPOINT_REACHED.name
                            queueHaltDetail = effectiveResult.message
                            queueHaltRun = i
                            queueHaltCareerInFlight = true
                            break
                        }
                        else -> {
                            // Error, timeout, connection error, etc.
                            if (gameRecoveryFailed) {
                                // The game could not be recovered to a driveable state this run (a crash
                                // or kill with no successful relaunch, or a genuinely un-driveable
                                // screen). Continuing the queue would launch the next run onto a dead or
                                // foreign screen, which can only fail - so pause regardless of stopOnError
                                // and leave the game where it is for the user to look at.
                                MessageLog.e(TAG, "[QUEUE] Run $i stopped because the game could not be recovered. Pausing the queue instead of starting the next run on a dead or foreign screen.")
                                queueHaltReason = "run $i stopped because the game could not be recovered"
                                ledger.haltEnd = SessionEnd.GAME_UNRECOVERABLE
                                queueHaltResultCode = effectiveResult.code.name
                                queueHaltRun = i
                                queueHaltCareerInFlight = i > startFromRun || coldStartConfirmedCareer
                                break
                            }
                            val accessibilityKey = accessibilityHaltKey
                            if (accessibilityKey != null) {
                                // The repair could not help, so the next run would fail the same way: pause regardless of
                                // stopOnError, keeping the saved queue.
                                MessageLog.e(TAG, "[QUEUE] Run $i stopped because its taps changed nothing and could not be repaired ($accessibilityKey). Pausing the queue.")
                                queueHaltReason = "run $i stopped because its taps changed nothing and could not be repaired ($accessibilityKey)"
                                ledger.haltEnd = SessionEnd.RUN_HALTED
                                ledger.reasonKey = accessibilityKey
                                queueHaltResultCode = effectiveResult.code.name
                                queueHaltRun = i
                                queueHaltCareerInFlight = i > startFromRun || coldStartConfirmedCareer
                                break
                            }
                            if (stopOnError) {
                                MessageLog.e(TAG, "[QUEUE] Run $i ended with ${effectiveResult.code}. Stopping queue (stopOnError=true).")
                                queueHaltReason = "run $i ended with ${effectiveResult.code} and stopOnError is on"
                                ledger.haltEnd = SessionEnd.STOP_ON_ERROR
                                queueHaltResultCode = effectiveResult.code.name
                                queueHaltRun = i
                                queueHaltCareerInFlight = true
                                break
                            } else {
                                MessageLog.w(TAG, "[QUEUE] Run $i ended with ${effectiveResult.code}. Continuing queue (stopOnError=false); the run is recorded as errored.")
                            }
                        }
                    }

                    if (unplayable == UnplayableRunStep.SKIP) {
                        // No career was played: no career end and no wait.
                        if (!leaveSkippedRun(ledger, i, totalRuns, completedRuns)) {
                            haltSkipping(i, snapshotMissing = false)
                            break
                        }
                        if (i < totalRuns && applyRotationForRun(rotation, i + 1, reuseLastLaunchSetup) == null) {
                            haltSkipping(i + 1, snapshotMissing = true)
                            break
                        }
                        continue
                    }

                    // Debug diagnostics are single-shot: the one diagnostic execution has returned, so
                    // end the session now. Breaking BEFORE decidePostCareerAction makes both the
                    // FINALIZE_TO_HOME (career-end) and the LAUNCH_NEXT (next career) navigations below
                    // unreachable, so no career-launching or career-end navigation runs regardless of
                    // totalRuns, queue mode, reuse, scenario, or trainee. This is the outer-orchestration
                    // counterpart to Game.kt's inner fail-closed gate; the stored totalRuns is untouched.
                    if (debugDiagnosticArmed) {
                        MessageLog.i(TAG, "[DEBUG-TEST] Diagnostic run complete; ending the session single-shot. No further runs, no career-launching or career-end navigation.")
                        break
                    }

                    // Post-career routing. The career-end flow (Complete Career -> results -> sparks
                    // and its reroll -> veteran registration -> Home) lives in the navigator and used
                    // to run ONLY between queued careers, so a queue-disabled single run stopped on the
                    // summary screen and skipped its sparks read, reroll, and Home return. Route through
                    // one decision instead: a cleanly completed last/only career always finalizes to
                    // Home (queued or not); launching the NEXT career stays queue-gated, so a single run
                    // can never start a second one. Stops, errors, skips, and breakpoints leave the
                    // screen as-is.
                    val postCareerAction =
                        decidePostCareerAction(
                            resultCode = effectiveResult.code,
                            runIndex = i,
                            totalRuns = totalRuns,
                            enableRunQueue = enableRunQueue,
                            queueStopRequested = queueStopRequested,
                            botRunning = BotService.isRunning,
                        )

                    if (postCareerAction == PostCareerAction.FINALIZE_TO_HOME) {
                        MessageLog.i(TAG, "[QUEUE] Career complete. Finishing the career-end flow through to the home screen...")
                        val finalizeResult = navigateWithDeadline(reuseLastLaunchSetup, finalizeToHome = true)
                        attachCareerEndSparks(ledger, i, runCareerEndSeq)
                        if (finalizeResult.success) {
                            MessageLog.i(TAG, "[QUEUE] Career-end flow finished; the game is parked on the home screen.")
                        } else if (enableRunQueue && finalizeResult.reasonKey == "CAREER_NOT_FINISHED") {
                            logNavigationFailure(finalizeResult)
                            haltCareerNotFinished(i)
                        } else {
                            logNavigationFailure(finalizeResult)
                            if (finalizeResult.lastDetectedState != "STOPPED") ledger.finalizeStopKey = finalizeResult.reasonKey
                            MessageLog.w(TAG, "[QUEUE] Career-end finalize did not reach the home screen; the game stays where it stopped.")
                        }
                    }

                    // If the queue has another run, navigate back and wait.
                    if (postCareerAction == PostCareerAction.LAUNCH_NEXT) {
                        // Check stop again before navigation.
                        if (queueStopRequested || !BotService.isRunning) {
                            MessageLog.i(TAG, "[QUEUE] Queue stop requested. Exiting queue.")
                            break
                        }

                        // Phase LAUNCHING only after a finished career: run i is done and the launch
                        // of run i+1 is starting, so a kill from here resumes at run i+1 and (under
                        // rotation) i+1's snapshot is the correct one. A run that ended without
                        // finishing its career (skipped, or errored with Stop Queue on Error off)
                        // leaves that career in the slot: the saved phase stays CAREER for run i, so
                        // a kill resumes by re-entering it under run i's own snapshot.
                        val careerFinished = effectiveResult.code == TaskResultCode.TASK_RESULT_COMPLETE
                        if (careerFinished) {
                            saveQueueState(context, active = true, currentRun = i, totalRuns = totalRuns, phase = PHASE_LAUNCHING, completedRuns = completedRuns)
                            ledger.phase = StartModule.PHASE_LAUNCHING
                        }
                        ledger.completedRuns = completedRuns
                        QueueLedger.refreshOpenSession(context, ledger.sessionId, ledger.openJson())
                        previousRunLeftCareer = !careerFinished

                        // The player's stop after this career: run i finished and the resume record
                        // above already points Start at run i+1, so finish the career-end steps (as
                        // the last run does) and leave the loop without launching. Only after a
                        // finished career: an unfinished one plays on under the usual rules, and the
                        // request waits for the next finished career.
                        if (careerFinished && stopAfterCareerRequested) {
                            val finalizeScenario = SettingsHelper.getStringSetting("general", "scenario")
                            if (finalizeScenario != "Daily Races" && finalizeScenario != "Team Trials") {
                                MessageLog.i(TAG, "[QUEUE] Stop after this career: run $i finished. Finishing its career-end flow, then pausing the queue.")
                                val navResult = navigateWithDeadline(reuseLastLaunchSetup, finalizeToHome = true)
                                attachCareerEndSparks(ledger, i, runCareerEndSeq)
                                if (!navResult.success) {
                                    logNavigationFailure(navResult)
                                    // The career-end steps did not finish: the between-run navigation
                                    // failure's halt, not a pause. A user Stop during it is a Stop.
                                    if (navResult.reasonKey == "CAREER_NOT_FINISHED") {
                                        haltCareerNotFinished(i)
                                    } else if (navResult.lastDetectedState != "STOPPED") {
                                        sendQueueProgressEvent(i, totalRuns, "queueFailed", TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name, "Between-run navigation failed after run $i.")
                                        queueHaltReason = "career-end navigation failed after run $i: ${navResult.failureReason}"
                                        ledger.haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS
                                        ledger.reasonKey = navResult.reasonKey
                                        ledger.reasonTrainee = navResult.reasonTrainee
                                        ledger.reasonOutfit = navResult.reasonOutfit
                                        ledger.reasonRotation = navResult.reasonRotation
                                        queueHaltResultCode = TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name
                                        queueHaltRun = i
                                        queueHaltCareerInFlight = false
                                    }
                                    break
                                }
                            }
                            stoppedAfterCareerRun = i
                            ledger.stoppedAfterCareer = true
                            break
                        }

                        // Trainee rotation: swap to the next run's trainee (settings + select mode)
                        // before reading the scenario or navigating. Stop the queue if its snapshot
                        // is missing rather than launch the wrong trainee under stale settings.
                        val nextReuse = applyRotationForRun(rotation, i + 1, reuseLastLaunchSetup)
                        if (nextReuse == null) {
                            sendQueueProgressEvent(i, totalRuns, "queueFailed", TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name, "Missing rotation snapshot for the next trainee.")
                            queueHaltReason = "missing rotation snapshot for the trainee after run $i"
                            ledger.haltEnd = SessionEnd.NEXT_SNAPSHOT_MISSING
                            queueHaltResultCode = TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name
                            queueHaltRun = i
                            queueHaltCareerInFlight = !careerFinished
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

                            // A finished career is already recorded, so no campaign is coming back for
                            // its end screens: without saying so the Grand Concert Complete Career
                            // screen routes to the campaign and navigation reports success without
                            // having launched anything. An unfinished one must not be claimed
                            // finished; the next run carries on with it.
                            val navResult = navigateWithDeadline(nextReuse, previousCareerComplete = careerFinished, careerInFlight = !careerFinished)
                            attachCareerEndSparks(ledger, i, runCareerEndSeq)
                            if (navResult.careerResumed) previousRunLeftCareer = true
                            nextRunCareerLaunched = navResult.success && navResult.careerLaunched

                            if (skipsTrainee(navResult, rotation) && CareerLaunchNavigator(context).backOutToHome()) {
                                MessageLog.w(TAG, "[QUEUE] Run ${i + 1} cannot start its trainee (${navResult.reasonKey}): ${navResult.failureReason} Back on the home screen; the run's own launch records the skip.")
                            } else if (!navResult.success) {
                                logNavigationFailure(navResult)
                                // Same as the cold-start path: a user Stop mid-navigation is not a
                                // navigation failure - no failure event, the post-loop queueComplete
                                // reports the ending.
                                if (navResult.reasonKey == "CAREER_NOT_FINISHED") {
                                    haltCareerNotFinished(i)
                                } else if (navResult.lastDetectedState != "STOPPED") {
                                    // navResult.failureReason can carry a raw caught-exception string
                                    // (CareerLaunchNavigator's "threw <Exception>: ..." reasons) - safe
                                    // only for MessageLog/Discord above, never for the JS-facing event.
                                    sendQueueProgressEvent(i, totalRuns, "queueFailed", TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name, "Between-run navigation failed after run $i.")
                                    // Includes the finalize gate refusing to press Finish on unspent
                                    // skill points, which is the protection working: halt, never
                                    // auto-continue, and now say so instead of reporting completion.
                                    queueHaltReason = "between-run navigation failed after run $i: ${navResult.failureReason}"
                                    ledger.haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS
                                    ledger.reasonKey = navResult.reasonKey
                                    ledger.reasonTrainee = navResult.reasonTrainee
                                    ledger.reasonOutfit = navResult.reasonOutfit
                                    ledger.reasonRotation = navResult.reasonRotation
                                    queueHaltResultCode = TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED.name
                                    queueHaltRun = i
                                    queueHaltCareerInFlight = !careerFinished || navResult.careerResumed
                                }
                                break
                            }
                        }

                        // Wait between runs.
                        sendQueueProgressEvent(i, totalRuns, "waiting")
                        MessageLog.i(TAG, "[QUEUE] Waiting ${delayBetweenRuns}s before next run...")

                        if (!interruptibleWait(delayBetweenRuns)) {
                            // Nothing claimed the abort: the device going to sleep interrupts the
                            // bot thread through the library without setting either flag. Halt on
                            // the shared path so the launch record saved above survives for the
                            // next Start, instead of falling through to the branch that clears it
                            // and reports the queue complete.
                            if (!queueStopRequested && BotService.isRunning) {
                                queueHaltReason = "the wait after run $i was interrupted with no stop requested"
                                ledger.haltEnd = SessionEnd.WAIT_INTERRUPTED
                                queueHaltResultCode = TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION.name
                                queueHaltRun = i
                                queueHaltCareerInFlight = !isMiscQueue
                            }
                            MessageLog.i(TAG, "[QUEUE] Between-run wait aborted. Exiting queue.")
                            break
                        }
                    }
                }

                ledger.completedRuns = completedRuns
                ledger.haltRun = queueHaltRun
                ledger.haltCareerInFlight = queueHaltCareerInFlight
                ledger.breakpointDetail = queueHaltDetail

                if (enableRunQueue) {
                    val halt = queueHaltReason
                    // A Stop or the service going away after the pause point wins over the pause.
                    val pausedAfterRun = stoppedAfterCareerRun?.takeIf { !queueStopRequested && BotService.isRunning }
                    if (halt != null) {
                        // Do NOT clear the persisted queue state: the queue did not finish, and the
                        // remaining runs are still owed. The career occupying the game's one slot has
                        // to be dealt with by hand before any of them can start.
                        // Count from the halted run index, not this session's completions: runs
                        // before startFromRun finished in an earlier session and are still done.
                        val doneRuns = if (queueHaltRun > 0) queueHaltRun else completedRuns
                        val unrun = (totalRuns - doneRuns).coerceAtLeast(0)
                        // The JS-facing message is queueHaltDetail (only ever set, and only ever
                        // safe, for a breakpoint) or a plain numeric summary - never $halt, which
                        // can carry raw navigation-exception text and is for MessageLog/Discord
                        // below only.
                        sendQueueProgressEvent(
                            doneRuns,
                            totalRuns,
                            "queueFailed",
                            resultCode = queueHaltResultCode,
                            message = queueHaltDetail ?: "Halted after $doneRuns of $totalRuns runs.",
                        )
                        MessageLog.e(TAG, "\n[QUEUE] ========================================")
                        MessageLog.e(TAG, "[QUEUE] Queue HALTED after $doneRuns of $totalRuns runs ($unrun not started).")
                        MessageLog.e(TAG, "[QUEUE] Reason: $halt")
                        if (queueHaltCareerInFlight) {
                            MessageLog.e(TAG, "[QUEUE] A career is still occupying the game's single slot; no further run can start until it is finished or abandoned in-game.")
                        } else {
                            MessageLog.e(TAG, "[QUEUE] No career is in flight; the game is parked on whatever screen the queue stopped at. Clear that screen, then restart to resume.")
                        }
                        MessageLog.e(TAG, "[QUEUE] ========================================\n")
                        notifyQueueHalted(doneRuns, totalRuns, unrun, halt, queueHaltCareerInFlight)
                    } else if (pausedAfterRun != null) {
                        // Paused at the player's request after a finished career. Keep the resume
                        // record saved at the launch point: Start continues with the next run.
                        val pausedMessage = "Paused after run $pausedAfterRun of $totalRuns. Start continues with run ${pausedAfterRun + 1}."
                        sendQueueProgressEvent(pausedAfterRun, totalRuns, "stoppedAfterCareer", message = pausedMessage)
                        MessageLog.i(TAG, "\n[QUEUE] ========================================")
                        MessageLog.i(TAG, "[QUEUE] Queue paused after run $pausedAfterRun of $totalRuns, as asked. Start continues with run ${pausedAfterRun + 1}.")
                        MessageLog.i(TAG, "[QUEUE] ========================================\n")
                    } else {
                        // Clear persisted queue state since queue finished normally.
                        val stopReason = queueStopReason
                        if (!keepsResumeRecordAfterStop(queueStopRequested, stopReason, lastCareerFinished, stopLeftCareer)) clearQueueState(context)
                        when {
                            queueStopRequested && stopReason != null -> {
                                // A controlled internal stop rather than a user Stop or failure. stopReason is always
                                // developer-authored prose, never exception text, so it is safe to show verbatim.
                                sendQueueProgressEvent(completedRuns, totalRuns, "queueHalted", message = stopReason)
                                MessageLog.w(TAG, "\n[QUEUE] ========================================")
                                MessageLog.w(TAG, "[QUEUE] Queue halted after $completedRuns of $totalRuns runs.")
                                MessageLog.w(TAG, "[QUEUE] Reason: $stopReason")
                                MessageLog.w(TAG, "[QUEUE] ========================================\n")
                            }
                            queueStopRequested -> {
                                // Only the app's Stop button and stopQueue() set this flag, so the
                                // user attribution is provable on this branch.
                                sendQueueProgressEvent(completedRuns, totalRuns, "queueStopped", message = "Stopped by the user after $completedRuns of $totalRuns runs.")
                                MessageLog.i(TAG, "\n[QUEUE] ========================================")
                                MessageLog.i(TAG, "[QUEUE] Queue stopped by the user after $completedRuns of $totalRuns runs.")
                                MessageLog.i(TAG, "[QUEUE] ========================================\n")
                            }
                            !BotService.isRunning -> {
                                // The service went away without the app asking it to: the overlay
                                // Stop interrupts this thread and tears the service down itself,
                                // and the library's exception cleanup does the same, so this is
                                // reported without attributing it to the user. It cannot swallow a
                                // genuine finish - the library clears isRunning only after this
                                // subscriber returns, so a normal completion still reads as running
                                // here.
                                sendQueueProgressEvent(completedRuns, totalRuns, "queueStopped", message = "Stopped after $completedRuns of $totalRuns runs.")
                                MessageLog.w(TAG, "\n[QUEUE] ========================================")
                                MessageLog.w(TAG, "[QUEUE] Queue stopped after $completedRuns of $totalRuns runs: the bot service is no longer running.")
                                MessageLog.w(TAG, "[QUEUE] ========================================\n")
                            }
                            else -> {
                                // currentRun was hard-coded to totalRuns, and the banner rendered
                                // "Queue complete: {currentRun}/{totalRuns} runs" from these fields
                                // rather than from the message. A queue stopped before its first
                                // run therefore showed "Queue complete: 4/4 runs" over a log
                                // reading "Completed 0 of 4" (2026-07-28 12:39). Report what
                                // actually completed.
                                sendQueueProgressEvent(completedRuns, totalRuns, "queueComplete", message = "Completed $completedRuns of $totalRuns runs.")
                                MessageLog.i(TAG, "\n[QUEUE] ========================================")
                                val notHome = if (ledger.finalizeStopKey != null) " The bot stopped before the game was back on its home screen." else ""
                                MessageLog.i(TAG, "[QUEUE] Queue finished. Completed $completedRuns of $totalRuns runs.$notHome")
                                MessageLog.i(TAG, "[QUEUE] ========================================\n")
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                // Only the gate's own refusal before dispatch returned counts as a refused launch. It is expected, so it logs
                // one readable line; a rethrow would reach EventBus's exception handler, which logs the whole stack trace.
                if (!ledger.dispatchReturned && isLaunchGateRefusal(e, launchSnapshotReadStarted, launchSnapshotReadFinished)) {
                    ledger.launchRefused = true
                    Log.i(TAG, "[START] Launch refused: ${e.message}")
                    MessageLog.w(TAG, launchRefusalLine(e))
                    return
                }
                ledger.unexpectedError = true
                throw e
            } finally {
                // Always release the wake lock and the session latch, even on exception or break paths.
                Game.releaseWakeLock()
                KeepScreenOn.stop()
                DebugTestGate.finish()
                ledgerHeartbeat?.interrupt()
                notifySessionEnd(libraryThread, writeSessionReport(ledger))
                sessionActive.set(false)
                // Bot execution truthfully ends here, on every exit path (including the early
                // launch-identity-mismatch returns above). Enqueued through the same FIFO as the
                // externally-posted "Running" event so a fast-abort session can never have this
                // event overtake its own start event on the JS side.
                enqueueJsEvent(JSEvent("BotService", "Not Running", false))
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
                    promise.reject("DISCORD_ERROR", "Could not connect to Discord. Check the bot token and your internet connection.")
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
     * The Remote Log Viewer's access code for this session, or null while the viewer is not running.
     * Only the Debug page shows it; it is never logged or saved.
     *
     * @param promise The React Native promise that resolves with the code or null.
     */
    @ReactMethod
    fun getRemoteLogViewerAccessCode(promise: Promise) {
        promise.resolve(LogStreamServer.accessCode)
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
     * Enqueues [event] onto [jsEventQueue] for the [ensureJsEventWorker] worker to relay to the
     * JS bridge. The single shared entry point for that queue, so every JSEvent -- whether
     * sourced from EventBus via [onJSEvent] or emitted directly by this module -- is forwarded
     * in the same FIFO order.
     */
    private fun enqueueJsEvent(event: JSEvent) {
        ensureJsEventWorker()
        if (!jsEventQueue.offer(event)) {
            // Queue full: the UI cannot keep up. Drop the oldest line rather than block the bot.
            jsEventQueue.poll()
            jsEventQueue.offer(event)
        }
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
        if (isCaptureStoppedEvent(event.eventName, event.message)) stopForLostCapture()
        // Only send the event to the React Native frontend if it's not internal.
        // This prevents flooding the bridge during parallel operations where disableOutput is true.
        if (event.isInternal) return
        enqueueJsEvent(event)
    }

    /** A crash ends the service like the overlay Stop; priority 1 records it before the library's subscriber clears [BotService.isRunning]. */
    @Subscribe(priority = 1)
    fun onExceptionEvent(event: ExceptionEvent) {
        if (isCrash(event.exception)) lastRunPostedException = true
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
