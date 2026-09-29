package com.steve1316.uma_android_automation.bot

import android.os.SystemClock
import android.util.Log
import com.steve1316.automation_library.utils.DiscordUtils
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.SessionTally
import com.steve1316.uma_android_automation.StartModule
import com.steve1316.uma_android_automation.components.ButtonOk
import com.steve1316.uma_android_automation.components.ButtonRaceRecommendationsCenterStage
import com.steve1316.uma_android_automation.components.ButtonRetry
import com.steve1316.uma_android_automation.components.Checkbox
import com.steve1316.uma_android_automation.components.DialogInterface
import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.components.IconHorseshoe
import com.steve1316.uma_android_automation.components.LabelRecreationUmamusume
import com.steve1316.uma_android_automation.components.RadioCareerQuickShortenAllEvents
import com.steve1316.uma_android_automation.components.RadioPortrait
import com.steve1316.uma_android_automation.types.BoundingBox
import com.steve1316.uma_android_automation.types.RaceGrade
import org.opencv.core.Point

/** Represents the result of a dialog handling operation. */
sealed class DialogHandlerResult {
    /** Indicates that the dialog was successfully detected and handled. */
    data class Handled(val dialog: DialogInterface) : DialogHandlerResult()

    /** Indicates that a dialog was detected but no handling logic was found for it. */
    data class Unhandled(val dialog: DialogInterface) : DialogHandlerResult()

    /** Indicates that a dialog was detected but handling was deferred to the calling function. */
    data class Deferred(val dialog: DialogInterface) : DialogHandlerResult()

    /** Indicates that no dialog popups were detected on the screen. */
    data object NoDialogDetected : DialogHandlerResult()
}

/**
 * One connection outage, timed on the monotonic clock so a device clock change cannot stretch or
 * cut it. The episode starts at the first Connection or Download Error and ends after a main-loop
 * iteration that sees none. Each error gets Retry after a growing pause; once the episode has
 * lasted [OUTAGE_BUDGET_MS] the run gives up as a connection error.
 */
class ConnectionOutageBudget(private val now: () -> Long = { SystemClock.elapsedRealtime() }) {
    sealed interface Decision {
        /** Wait [waitMs], then tap Retry. [attempt] counts from 1 within the episode. */
        data class Retry(val waitMs: Long, val attempt: Int, val elapsedMs: Long) : Decision

        data class GiveUp(val elapsedMs: Long, val attempts: Int) : Decision
    }

    companion object {
        /** Outage length the run rides out mid-career. The career is saved server-side, and the
         * outages seen so far (around the daily reset) passed within minutes; this is an estimate
         * to tune from how often connection-error endings actually occur. */
        const val OUTAGE_BUDGET_MS: Long = 20 * 60_000L

        /** Pause before each Retry within an episode: the first is immediate, then it backs off. */
        val RETRY_BACKOFF_MS: List<Long> = listOf(0L, 30_000L, 60_000L, 120_000L)

        fun backoffFor(attemptIndex: Int): Long = RETRY_BACKOFF_MS[attemptIndex.coerceIn(0, RETRY_BACKOFF_MS.lastIndex)]
    }

    private var episodeStartMs: Long? = null
    private var attempts = 0
    private var errorSeenThisIteration = false

    /** Records one error dialog and decides what to do with it. */
    fun onError(): Decision {
        val t = now()
        val start = episodeStartMs ?: t.also { episodeStartMs = it }
        errorSeenThisIteration = true
        val elapsed = t - start
        if (elapsed >= OUTAGE_BUDGET_MS) return Decision.GiveUp(elapsed, attempts)
        val wait = minOf(backoffFor(attempts), OUTAGE_BUDGET_MS - elapsed)
        attempts++
        return Decision.Retry(wait, attempts, elapsed)
    }

    /** Called before each main-loop iteration. */
    fun beginIteration() {
        errorSeenThisIteration = false
    }

    /** Called when an iteration returns normally; one without any error ends the episode. */
    fun endIterationNormally() {
        if (!errorSeenThisIteration) {
            episodeStartMs = null
            attempts = 0
        }
    }
}

/**
 * Base class for handling various dialogs in the game.
 *
 * This class centralizes all dialog handling logic from different bot modules to provide a single, maintainable source of truth for dialog interactions.
 *
 * Example usage:
 * ```kotlin
 * // Basic usage
 * val (handled, dialog) = game.task.handleDialogs()
 *
 * // Usage with arguments
 * val args = mapOf("overrideIgnoreConsecutiveRaceWarning" to true)
 * game.task.handleDialogs(args = args)
 * ```
 *
 * @property game Reference to the bot's [Game] instance for state access and utilities.
 */
open class DialogHandler(val game: Game) {
    /** Looks in a row at the Data Download prompt without finding its OK button ([Game.DATA_DOWNLOAD_OK_MISS_LIMIT]). */
    private var dataDownloadOkMisses = 0

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]${this::class.simpleName}"
    }

    /**
     * Detects and handles any dialog popups.
     *
     * This is the centralized implementation that handles generic, campaign, racing, and skill-related dialogs. To prevent the bot from moving too fast, a 500ms delay is added whenever a dialog is
     * closed. This gives the dialog animation time to finish.
     *
     * List of valid arguments passed to the [args] parameter:
     * - `bShouldWait`: Whether to add a delay before handling the dialog.
     * - `bShouldWaitForLoading`: Whether to wait for the game to load or connect to the server prior to checking.
     * - `dialogNameToDefer`: A single dialog name to not handle here and instead return to the caller.
     * - `dialogNamesToDefer`: A list of dialog names to not handle here and instead return to the caller.
     * - `bShouldDefer`: Whether to not handle ANY dialogs and instead return them to the caller.
     *
     * @param dialog An optional dialog to evaluate. This allows chaining dialog handler calls for improved performance.
     * @param args Optional arguments mapping for dialog handling behavior.
     * @return The [DialogHandlerResult] for this operation.
     */
    open fun handleDialogs(dialog: DialogInterface? = null, args: Map<String, Any> = mapOf()): DialogHandlerResult {
        val bShouldWait = args["bShouldWait"] as? Boolean ?: false
        val bShouldWaitForLoading = args["bShouldWaitForLoading"] as? Boolean ?: false
        if (bShouldWait || bShouldWaitForLoading) {
            Log.d(
                TAG,
                "[DEBUG] handleDialogs:: Waiting before handling dialog due to passed args: dialogWaitDelay=${game.dialogWaitDelay}, bShouldWait=$bShouldWait, bShouldWaitForLoading=$bShouldWaitForLoading",
            )
            game.wait(game.dialogWaitDelay, skipWaitingForLoading = !bShouldWaitForLoading)
        }

        val dialog: DialogInterface? = dialog ?: DialogUtils.getDialog(game.imageUtils)
        if (dialog == null) {
            Log.d(TAG, "[DEBUG] handleDialogs:: No dialog found.")
            dataDownloadOkMisses = 0
            return DialogHandlerResult.NoDialogDetected
        }
        if (dialog.name != "data_download") dataDownloadOkMisses = 0

        Log.d(TAG, "[DEBUG] handleDialogs:: Handle dialog: ${dialog.name}")

        val dialogNameToDefer: String? = args["dialogNameToDefer"] as? String ?: null
        val dialogNamesToDefer: List<String> = args["dialogNamesToDefer"] as? List<String> ?: listOf()
        var bShouldDefer = args["bShouldDefer"] as? Boolean ?: false
        if (dialogNamesToDefer.contains(dialog.name) || dialogNameToDefer == dialog.name) {
            bShouldDefer = true
        }

        if (bShouldDefer) {
            Log.d(TAG, "[DEBUG] handleDialogs:: Dialog handling deferred to calling function.")
            return DialogHandlerResult.Deferred(dialog)
        }

        when (dialog.name) {
            // Generic Dialogs.
            "connection_error", "download_error" -> {
                handleConnectionError(dialog)
            }

            "data_download" -> {
                handleDataDownload(dialog)
            }

            "display_settings" -> {
                dialog.close(game.imageUtils)
            }

            "help_and_glossary" -> {
                dialog.close(game.imageUtils)
            }

            "notices" -> {
                // Daily-reset announcements (00:00 JST = 17:00 CEST) - pops mid-career on whatever
                // screen is up. Left unrouted it burned 5 recovery cycles and ended the run.
                dialog.close(game.imageUtils)
            }

            "date_changed" -> {
                // Real-world midnight date-rollover popup; its only control is OK. Left unhandled it
                // spun the recover loop until the watchdog killed any overnight run that crossed midnight.
                dialog.ok(game.imageUtils)
            }

            "session_error" -> {
                throw InterruptedException("Session error. Stopping bot...")
            }

            // Racing Dialogs.
            "overwrite" -> {
                dialog.ok(game.imageUtils)
            }

            "race_playback" -> {
                // Select portrait mode to prevent game from switching to landscape.
                RadioPortrait.click(game.imageUtils)

                // Click the checkbox to prevent this popup in the future.
                Checkbox.click(game.imageUtils)
                dialog.ok(game.imageUtils)
            }

            "runners" -> {
                dialog.close(game.imageUtils)
            }

            "schedule_cancellation" -> {
                dialog.close(game.imageUtils)
            }

            "schedule_race" -> {
                dialog.close(game.imageUtils)
            }

            "trophy_won" -> {
                dialog.close(game.imageUtils)
            }

            "unlock_requirements" -> {
                dialog.close(game.imageUtils)
            }

            "agenda_details" -> {
                dialog.close(game.imageUtils)
            }

            "bonus_umamusume_details" -> {
                dialog.close(game.imageUtils)
            }

            "career" -> {
                dialog.close(game.imageUtils)
            }

            "career_event_details" -> {
                dialog.close(game.imageUtils)
            }

            "career_profile" -> {
                dialog.close(game.imageUtils)
            }

            "choices" -> {
                dialog.close(game.imageUtils)
            }

            "concert_skip_confirmation" -> {
                // Click the checkbox to prevent this popup in the future.
                Checkbox.click(game.imageUtils)
                dialog.ok(game.imageUtils)
            }

            "epithets" -> {
                dialog.close(game.imageUtils)
            }

            "fans" -> {
                dialog.close(game.imageUtils)
            }

            "featured_cards" -> {
                dialog.close(game.imageUtils)
            }

            "follow_trainer" -> {
                // Appears after a run when Auto-Fill borrowed a support card from a trainer you hadn't
                // used before, asking whether to follow them. There's no rule to when it shows. The
                // DialogFollowTrainer object existed but had no handler case here, so it fell through to
                // the else branch (Unhandled) and the bot wedged on the open popup. Default to Cancel
                // (close() clicks the first button, ButtonCancel) so the run continues to a known screen.
                dialog.close(game.imageUtils)
            }

            "give_up" -> {
                dialog.close(game.imageUtils)
            }

            "goals" -> {
                dialog.close(game.imageUtils)
            }

            "infirmary" -> {
                Checkbox.click(game.imageUtils)
                dialog.ok(game.imageUtils)
            }

            "log" -> {
                dialog.close(game.imageUtils)
            }

            "menu" -> {
                dialog.close(game.imageUtils)
            }

            "mood_effect" -> {
                dialog.close(game.imageUtils)
            }

            "my_agendas" -> {
                dialog.close(game.imageUtils)
            }

            "no_retries" -> {
                dialog.ok(game.imageUtils)
            }

            "options" -> {
                dialog.close(game.imageUtils)
            }

            "perks" -> {
                dialog.close(game.imageUtils)
            }

            "placing" -> {
                dialog.close(game.imageUtils)
            }

            "purchase_alarm_clock" -> {
                // Player ran out of free Alarm Clocks for the career. The game offers to buy one
                // for 10 carats. Decision is driven by `racing.alarmClockPolicy` and the grade
                // of the most recent race (tracked on Racing.lastRaceGrade):
                //   - "Never"        -> always cancel.
                //   - "G1Only"       -> spend only if last race was G1.
                //   - "G1AndFinale"  -> spend for G1 or Twinkle Star Climax finale (RaceGrade.FINALE).
                //   - "Always"       -> always spend.
                // Falls back to "Never" for unknown policies or if race grade is somehow unavailable
                // (e.g., misc tasks where this dialog shouldn't fire anyway).
                val policy = SettingsHelper.getStringSetting("racing", "alarmClockPolicy", "Never")
                val grade = (game.task as? Campaign)?.getLastRaceGrade()
                val shouldSpend =
                    when (policy) {
                        "Never" -> false
                        "G1Only" -> grade == RaceGrade.G1
                        "G1AndFinale" -> grade == RaceGrade.G1 || grade == RaceGrade.FINALE
                        "Always" -> true
                        else -> false
                    }
                if (shouldSpend) {
                    MessageLog.i(TAG, "[DIALOG] Out of free Alarm Clocks. Spending 10 carats to retry (policy='$policy', grade=$grade).")
                    dialog.ok(game.imageUtils)
                } else {
                    MessageLog.i(TAG, "[DIALOG] Out of free Alarm Clocks. Skipping carats spend (policy='$policy', grade=$grade). Continuing without retry.")
                    // Mark this race as having had its retry option exhausted via policy. Without this
                    // flag the bot loops: cancel popup → game returns to retry screen → bot taps retry
                    // button again → popup reopens → policy rejects again. shouldRetryRace short-circuits
                    // when the flag is set so the loop exits after one rejection. Routed through a
                    // public Campaign method since racing is `protected` and not accessible via cast.
                    (game.task as? Campaign)?.markAlarmClockPolicySkipped()
                    // ButtonCancel is the first entry in the dialog's buttons list, so close() clicks it.
                    dialog.close(game.imageUtils)
                }
            }

            "quick_mode_settings" -> {
                val bbox =
                    BoundingBox(
                        x = game.imageUtils.relX(0.0, 160),
                        y = game.imageUtils.relY(0.0, 770),
                        w = game.imageUtils.relWidth(70),
                        h = game.imageUtils.relHeight(460),
                    )
                val optionLocations: ArrayList<Point> =
                    IconHorseshoe.findAll(
                        game.imageUtils,
                        region = bbox.toIntArray(),
                        confidence = 0.0,
                    )

                if (optionLocations.size == 4) {
                    Log.d(TAG, "[DEBUG] handleDialogs:: quick_mode_settings: Using findAll method.")
                    val loc: Point = optionLocations[1]
                    game.tap(loc.x, loc.y, IconHorseshoe.template.path)
                } else {
                    Log.d(TAG, "[DEBUG] handleDialogs:: quick_mode_settings: Using image OCR method.")
                    // Fallback to image detection.
                    RadioCareerQuickShortenAllEvents.click(game.imageUtils)
                }

                dialog.ok(game.imageUtils)
            }

            "race_details" -> {
                dialog.ok(game.imageUtils)
            }

            "race_recommendations" -> {
                ButtonRaceRecommendationsCenterStage.click(game.imageUtils)
                Checkbox.click(game.imageUtils)
                dialog.ok(game.imageUtils)
            }

            "recreation" -> {
                // Two different dialogs share the "Recreation" title: the classic confirmation
                // (OK/Cancel + skip checkbox) and the Pal-card partner picker, which has no OK
                // button - on that one the clicks below are silent no-ops and the campaign loops
                // on the open popup forever. If the partner rows are visible, take the trainee
                // outing: always selectable, unlike the Pal row, which goes dark once its event
                // chain completes.
                if (LabelRecreationUmamusume.check(game.imageUtils)) {
                    if (!LabelRecreationUmamusume.click(game.imageUtils)) {
                        dialog.close(game.imageUtils)
                    }
                    game.waitForLoading()
                } else {
                    Checkbox.click(game.imageUtils)
                    dialog.ok(game.imageUtils)
                }
            }

            "rest" -> {
                Checkbox.click(game.imageUtils)
                dialog.ok(game.imageUtils)
            }

            "rest_and_recreation" -> {
                // Does not have a checkbox unlike the other rest patterns.
                dialog.ok(game.imageUtils)
            }

            "scheduled_races" -> {
                dialog.close(game.imageUtils)
            }

            "schedule_settings" -> {
                dialog.close(game.imageUtils)
            }

            "skill_details" -> {
                dialog.close(game.imageUtils)
            }

            "song_acquired" -> {
                dialog.close(game.imageUtils)
            }

            "spark_details" -> {
                dialog.close(game.imageUtils)
            }

            "sparks" -> {
                dialog.close(game.imageUtils)
            }

            "team_info" -> {
                dialog.close(game.imageUtils)
            }

            "unity_cup_available" -> {
                dialog.close(game.imageUtils)
            }

            "unmet_requirements" -> {
                dialog.close(game.imageUtils)
            }

            // Skill List Dialogs.
            "skill_list_confirmation" -> {
                dialog.ok(game.imageUtils)

                // This dialog takes longer to close than others. Add an extra delay to make sure we don't skip anything.
                game.wait(1.0)
            }

            "skill_list_confirm_exit" -> {
                dialog.ok(game.imageUtils)
            }

            "skills_learned" -> {
                dialog.close(game.imageUtils)
            }

            else -> {
                // This is the shared-dialog level (the deepest handler in the chain). Reaching here only
                // means the dialog is not one of the cross-cutting shared dialogs, so it cascades up to the
                // campaign/scenario handler that owns it - the normal path (e.g. consecutive_race_warning is
                // handled by Campaign). Log at DEBUG, not WARN: the real "nobody handled it" warning is
                // emitted by the most-derived handler's else, so warning here was a false alarm.
                Log.d(TAG, "[DEBUG] handleDialogs:: Dialog \"${dialog.name}\" is not a shared dialog; cascading to the campaign handler.")
                return DialogHandlerResult.Unhandled(dialog)
            }
        }

        game.wait(0.5)
        return DialogHandlerResult.Handled(dialog)
    }

    /**
     * Accepts the game's Data Download prompt with OK, never Cancel, and starts the no-tap wait the
     * campaign keeps while the download runs ([Game.dataDownloadActive]). A Download Error after it
     * is the outage route's, as always.
     */
    private fun handleDataDownload(dialog: DialogInterface) {
        if (!ButtonOk.click(game.imageUtils)) {
            dataDownloadOkMisses++
            if (dataDownloadOkMisses >= Game.DATA_DOWNLOAD_OK_MISS_LIMIT) stopForDataDownloadPrompt(dialog)
            MessageLog.w(TAG, "[DIALOG] ${dialog.title} shows no OK button ($dataDownloadOkMisses/${Game.DATA_DOWNLOAD_OK_MISS_LIMIT}). Leaving it up.")
            return
        }
        dataDownloadOkMisses = 0
        game.dataDownloadAcceptedAtMs = SystemClock.elapsedRealtime()
        MessageLog.i(TAG, "[DIALOG] ${dialog.title}: tapped OK. Waiting up to ${Game.LOADING_HARD_LIMIT_MS / 60_000} minutes for the game data, tapping nothing.")
    }

    /**
     * The OK button was not found on [Game.DATA_DOWNLOAD_OK_MISS_LIMIT] looks in a row: stops the run
     * with its own reason, as the trainee-mismatch stop does, rather than let the dialog streak end it
     * as dead gestures. Nothing is tapped.
     */
    private fun stopForDataDownloadPrompt(dialog: DialogInterface): Nothing {
        val reason = "The game asked to download additional data, and the ${dialog.title} prompt's OK button was not found. Answer the prompt in the game (OK, or Title Screen for a data update), let any download finish, then press Start again."
        StartModule.queueStopKey = "DATA_DOWNLOAD_PROMPT"
        StartModule.queueStopReason = reason
        StartModule.queueStopRequested = true
        MessageLog.e(TAG, "[DIALOG] $reason")
        throw InterruptedException(reason)
    }

    /**
     * Rides out a Connection or Download Error within the outage budget: Retry only, after the
     * budget's pause. Title Screen is never tapped mid-career - it abandons the loaded career and
     * nothing here can navigate back. A dialog with no Retry is left up and keeps counting against
     * the budget, which ends the run as a connection error.
     */
    private fun handleConnectionError(dialog: DialogInterface) {
        when (val decision = game.connectionBudget.onError()) {
            is ConnectionOutageBudget.Decision.Retry -> {
                if (decision.attempt == 1) SessionTally.connectionHolds.incrementAndGet()
                val outageSeconds = decision.elapsedMs / 1000
                if (decision.waitMs > 0) {
                    MessageLog.w(TAG, "[CONNECTION] ${dialog.title} (attempt ${decision.attempt}, outage ${outageSeconds}s so far). Waiting ${decision.waitMs / 1000}s before Retry.")
                    game.wait(decision.waitMs / 1000.0, skipWaitingForLoading = true)
                } else {
                    MessageLog.w(TAG, "[CONNECTION] ${dialog.title} (attempt ${decision.attempt}). Tapping Retry.")
                }
                if (!ButtonRetry.click(game.imageUtils)) {
                    MessageLog.w(TAG, "[CONNECTION] ${dialog.title} shows no Retry button. Leaving it up; the outage budget keeps counting.")
                }
                game.wait(0.5)
            }
            is ConnectionOutageBudget.Decision.GiveUp -> {
                val reason =
                    "The game could not reconnect: ${dialog.title} for ${decision.elapsedMs / 60_000} minutes over ${decision.attempts} retries. " +
                        "Stopping the run as a connection error."
                MessageLog.e(TAG, "[CONNECTION] $reason")
                if (DiscordUtils.enableDiscordNotifications) {
                    DiscordUtils.queue.add("```diff\n- ${MessageLog.getSystemTimeString()} $reason\n```")
                }
                game.connectionLostReason = reason
                throw ConnectionLostException(reason)
            }
        }
    }
}
