package com.steve1316.uma_android_automation

import org.json.JSONObject

/**
 * How a bot session ended, in player words: the outcome as a title, the reason with its counts, and
 * what to do next when something is needed.
 */
internal data class ReportText(val title: String, val reason: String, val nextAction: String?) {
    val body: String get() = if (nextAction == null) reason else "$reason $nextAction"
}

/**
 * The one mapping from a queue report to the words the end notification and the app show. It reads
 * the report's JSON (as stored, or [QueueReport.toJson]), so a report carries keys and counts,
 * never prose. An ending or key this version does not know falls back to a neutral line, never a
 * success.
 */
internal fun queueReportText(report: JSONObject?): ReportText {
    val kind = report?.optString("kind").orEmpty()
    val end = SessionEnd.entries.firstOrNull { it.name == kind } ?: return ReportText("Bot stopped", "The bot session ended.", null)
    val text = endingText(end, report!!)
    // A game exception has already replaced the notification with the library's error; nothing
    // that follows may read like a success.
    return if (report.optBoolean("errorPosted") && text.title in SUCCESS_TITLES) text.copy(title = "Ended with an error") else text
}

/**
 * What the app's bridge returns for the stored report: the report as stored, its words from
 * [queueReportText], and whether it ended a queue or run. The words are computed on every read and
 * never stored. A stored value that is not JSON comes back with a null report.
 */
internal fun lastReportPayload(raw: String?): String? {
    if (raw == null) return null
    val report = runCatching { JSONObject(raw) }.getOrNull()
    val text = queueReportText(report)
    val kind = SessionEnd.entries.firstOrNull { it.name == report?.optString("kind") }
    return JSONObject()
        .put("report", report ?: JSONObject.NULL)
        .put("text", JSONObject().put("title", text.title).put("reason", text.reason).put("nextAction", text.nextAction ?: JSONObject.NULL))
        .put("runEnding", kind != null && kind !in NOT_A_RUN_ENDINGS)
        .toString()
}

private val SUCCESS_TITLES = setOf("Queue finished", "Career finished", "Diagnostic ended", "Nothing to resume")

private fun endingText(end: SessionEnd, r: JSONObject): ReportText {
    val total = r.optInt("totalRuns")
    val done = r.optInt("completedRuns")
    val reached = r.optInt("runReached")
    val resumable = r.optBoolean("resumable")
    val lastRun = r.optJSONArray("runs")?.let { runs -> runs.optJSONObject(runs.length() - 1) }
    val lastCode = lastRun?.optString("resultCode")
    val lastRetried = lastRun?.optBoolean("retried") == true
    val key = r.optString("reasonKey")
    return when (end) {
        SessionEnd.REFUSED_NO_APP_START ->
            ReportText(
                "Not started",
                "Not started, and nothing was spent: this start did not come from the Start button in UMA Auto+, or its launch choice changed after Start.",
                OPEN_APP_AND_START,
            )
        SessionEnd.REFUSED_LAUNCH_IDENTITY -> ReportText("Not started", LAUNCH_IDENTITY_REASON, LAUNCH_IDENTITY_NEXT)
        SessionEnd.REFUSED_DATABASE_UNHEALTHY -> ReportText("Not started", DATABASE_UNHEALTHY_REASON, DATABASE_UNHEALTHY_NEXT)
        SessionEnd.ROTATION_NOT_PREPARED ->
            ReportText(
                "Not started",
                "Not started, and nothing was spent: the rotation queue was not started from the Start button in UMA Auto+, so its trainee setups were not prepared.",
                OPEN_APP_AND_START,
            )
        SessionEnd.DIAGNOSTIC_ENDED -> ReportText("Diagnostic ended", "The diagnostic run ended.", null)
        SessionEnd.NOTHING_TO_RESUME -> ReportText("Nothing to resume", "The saved queue had already reached its last run, so there was nothing left to resume.", null)
        SessionEnd.COMPLETED -> {
            val summary = if (done >= total) (if (total == 1) "The run is done." else "All $total runs are done.") else "$done of ${runs(total)} are done."
            ReportText(if (done >= total) "Queue finished" else "Queue ended", summary + errorSentence(r), null)
        }
        SessionEnd.SINGLE_RUN_ENDED -> if (key == ONLY_OTHER_OUTFIT) singleRunOutfitText(r) else singleRunText(lastCode)
        SessionEnd.STOPPED_BY_USER -> ReportText("Queue stopped", "You stopped the queue with $done of ${runs(total)} done." + errorSentence(r), null)
        SessionEnd.STOPPED_BY_BOT -> {
            val why = keyText(key, r)
            ReportText("Queue stopped", "The bot stopped the queue with $done of ${runs(total)} done: ${why.reason}" + errorSentence(r), why.next(false))
        }
        SessionEnd.SERVICE_ENDED ->
            if (r.optBoolean("errorPosted")) {
                ReportText(
                    "Stopped by an error",
                    "The bot stopped after an unexpected error at run $reached, with $done of ${runs(total)} done." + errorSentence(r),
                    "Press Start in UMA Auto+ to run the queue again.",
                )
            } else {
                ReportText("Queue stopped", "The bot was stopped with $done of ${runs(total)} done." + errorSentence(r), null)
            }
        SessionEnd.BREAKPOINT -> {
            val detail = r.optString("breakpointDetail").takeUnless { r.isNull("breakpointDetail") || it.isBlank() }
            ReportText(
                haltTitle(resumable),
                "Run $reached stopped at a breakpoint" + (detail?.let { ": $it" } ?: "."),
                "Finish that screen in the game, then ${pressStart(resumable)}",
            )
        }
        SessionEnd.GAME_UNRECOVERABLE ->
            ReportText(
                haltTitle(resumable),
                "The game froze at run $reached, and UMA Auto+ could not close it. $CLOSE_FROZEN_GAME.",
                "Then ${pressStart(resumable)}",
            )
        SessionEnd.RUN_HALTED -> {
            val why = keyText(key, r)
            ReportText(haltTitle(resumable), "The queue stopped during run $reached of $total: ${why.reason}", why.next(resumable))
        }
        SessionEnd.STOPPED_AFTER_CAREER ->
            ReportText(
                haltTitle(resumable),
                "You paused the queue after run $reached of $total." + (if (resumable) " Start continues with run ${reached + 1}." else "") + errorSentence(r),
                if (resumable) pressStart(true).replaceFirstChar { it.uppercase() } else null,
            )
        SessionEnd.STOP_ON_ERROR ->
            ReportText(
                haltTitle(resumable),
                "Run $reached ${runErrorPhrase(lastCode)}${if (lastRetried) " again after a retry" else ""}, and Stop Queue on Error is on.",
                pressStart(resumable).replaceFirstChar { it.uppercase() },
            )
        SessionEnd.FIRST_SNAPSHOT_MISSING, SessionEnd.NEXT_SNAPSHOT_MISSING ->
            ReportText(
                haltTitle(resumable),
                "The queue stopped because the saved setup for the next trainee in the rotation was missing.",
                pressStart(resumable).replaceFirstChar { it.uppercase() } + " Start prepares the rotation's trainee setups before launching.",
            )
        SessionEnd.LAUNCH_FAILED_BEFORE_RUN -> {
            val why = keyText(key, r)
            ReportText(haltTitle(resumable), "The queue stopped before run ${reached + 1} of $total: ${why.reason}", why.next(resumable))
        }
        SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS -> {
            val why = keyText(key, r)
            ReportText(haltTitle(resumable), "The queue stopped after run $reached of $total: ${why.reason}", why.next(resumable))
        }
        SessionEnd.WAIT_INTERRUPTED ->
            ReportText(
                haltTitle(resumable),
                "The queue stopped while waiting after run $reached (often because the screen turned off).",
                "Leave the screen on while the bot runs (pressing the power button stops it), then ${pressStart(resumable)}",
            )
        SessionEnd.ENDED_WITH_ERROR ->
            ReportText(
                "Stopped by an error",
                "The bot stopped after an unexpected error.",
                "Press Start in UMA Auto+ to try again. If it keeps happening, please report it as a bug.",
            )
        SessionEnd.PROCESS_ENDED -> {
            val at = if (reached > 0) (if (r.optBoolean("queueEnabled")) " during run $reached of $total" else " during the run") else ""
            val exit = r.optJSONObject("exitInfo")
            val watchdog = exit?.optJSONObject("watchdog")
            val cause =
                if (watchdog != null) {
                    " The bot stopped itself after ${watchdog.optLong("stalledSeconds")} seconds without progress."
                } else {
                    exit?.optString("reason")?.let { EXIT_CAUSES[it] }?.let { " $it" }.orEmpty()
                }
            ReportText("App stopped unexpectedly", "UMA Auto+ stopped unexpectedly$at.$cause", if (resumable) pressStart(true).replaceFirstChar { it.uppercase() } else null)
        }
    }
}

private fun singleRunText(code: String?): ReportText =
    when (code) {
        null -> ReportText("Run stopped", "The bot stopped before the run started.", null)
        "TASK_RESULT_COMPLETE" -> ReportText("Career finished", "The career finished.", null)
        "TASK_RESULT_MANUALLY_STOPPED" -> ReportText("Run stopped", "The run was stopped.", null)
        "TASK_RESULT_BREAKPOINT_REACHED" -> ReportText("Run paused", "The run stopped at a breakpoint.", "Finish that screen in the game, then press Start in UMA Auto+.")
        in RUN_ERROR_CODES -> ReportText("Run ended with an error", "The run ${runErrorPhrase(code)}.", "Open the game and check it, then press Start in UMA Auto+.")
        else -> ReportText("Run ended", "The run ended.", null)
    }

/** One finished run's reason in the same words, for a run that did not finish its career; null for a finished career. */
internal fun runWords(code: String?): String? = if (code == "TASK_RESULT_COMPLETE") null else singleRunText(code).reason

internal fun runEndedWithError(code: String?): Boolean = code in RUN_ERROR_CODES

private const val BETWEEN_RUNS = "Between runs"

private const val NOT_RUNNING = "The bot is not running"

/**
 * The one table from a status key to its words. The dashboard page's `statusPhase` holds the same
 * keys and labels (pinned by a test). An unknown key reads "Working", never a guess.
 */
internal val STATUS_LABELS: Map<String, String> =
    buildMap {
        put("armed", "Ready: tap the start button in the game")
        put("running", "Running")
        for (key in listOf("completed", "navigating", "waiting", "starting", "resuming", "retrying")) put(key, BETWEEN_RUNS)
        for (key in listOf("queueFailed", "queueHalted", "queueStopped", "queueComplete", "stoppedAfterCareer", "notRunning")) put(key, NOT_RUNNING)
    }

internal fun statusLabel(key: String?): String = STATUS_LABELS[key] ?: "Working"

/**
 * The notification's line while a session runs, e.g. "Run 2 of 4, Classic Year Late January". [date]
 * is the game date the bot last read, or null. [runRecorded] is whether [run] is already among the
 * session's finished runs: only then does a between-runs line say it ended (the queue also sends the
 * run about to start, e.g. its first run on a cold start). [pausePending] is whether "Stop after this
 * career" is requested: a running line then ends with [PAUSE_PENDING]. Built from the status key and
 * numbers only: never a queue event's message, a TP amount or a time estimate.
 */
internal fun progressLineText(status: String?, run: Int?, total: Int?, date: String?, runRecorded: Boolean = false, pausePending: Boolean = false): String {
    val label = statusLabel(status)
    val position = if (run != null && total != null && run > 0 && total > 0) "run $run of $total" else null
    val line =
        when {
            label == BETWEEN_RUNS && position != null -> if (runRecorded) "$label: $position ended" else "$label: starting $position"
            status == "running" -> listOfNotNull(position?.replaceFirstChar(Char::uppercase), date ?: label).joinToString(", ")
            position != null -> "${position.replaceFirstChar(Char::uppercase)}, $label"
            else -> label
        }
    return if (pausePending && status == "running") "$line. $PAUSE_PENDING" else line
}

/** Ends the running line while "Stop after this career" is requested; gone again once it is cancelled. */
internal const val PAUSE_PENDING = "Pausing after this career."

/** The line is posted only for a live or between-runs key, or an unknown one; never before the overlay tap or once the queue has ended. */
internal fun postsProgressLine(status: String?): Boolean = status != "armed" && statusLabel(status) != NOT_RUNNING

/** At most one post per [minIntervalMs], and never the text already showing. */
internal fun progressLineDue(text: String, lastText: String?, now: Long, lastPostAt: Long?, minIntervalMs: Long = 30_000L): Boolean =
    text != lastText && (lastPostAt == null || now - lastPostAt >= minIntervalMs)

private fun runs(n: Int) = if (n == 1) "1 run" else "$n runs"

/**
 * Done counts only finished careers, so every count of done runs is followed by how many runs ended
 * with an error instead. The report's runs are this session's only, which a resumed queue has to say.
 */
private fun errorSentence(r: JSONObject): String {
    val errors = r.optJSONArray("runs")?.let { runs -> (0 until runs.length()).count { runs.optJSONObject(it)?.optString("resultCode") in RUN_ERROR_CODES } } ?: 0
    return when {
        errors == 0 -> ""
        r.optInt("startFromRun") > 1 -> " ${runs(errors)} since the queue resumed ended with an error."
        else -> " ${runs(errors)} ended with an error."
    }
}

private fun haltTitle(resumable: Boolean) = if (resumable) "Queue paused" else "Queue stopped"

/**
 * Resumable endings keep the resume record, which Start honors for 24 hours
 * (`QUEUE_STATE_STALE_MS`) while Run Queue is on with the same number of runs. The words carry
 * those conditions, so they agree with the Home banner when changed settings rule a resume out.
 */
private fun pressStart(resumable: Boolean) =
    if (resumable) "press Start in UMA Auto+ within 24 hours, with Run Queue on and the same number of runs, to continue the queue." else "press Start in UMA Auto+."

private val RUN_ERROR_CODES = setOf("TASK_RESULT_UNHANDLED_EXCEPTION", "TASK_RESULT_CONNECTION_ERROR", "TASK_RESULT_TIMED_OUT", "TASK_RESULT_QUEUE_NAVIGATION_FAILED")

private fun runErrorPhrase(code: String?) =
    when (code) {
        "TASK_RESULT_TIMED_OUT" -> "timed out"
        "TASK_RESULT_CONNECTION_ERROR" -> "lost its connection to the game server"
        else -> "ended with an unexpected error"
    }

private val EXIT_CAUSES =
    mapOf(
        "LOW_MEMORY" to "Android closed it to free memory.",
        "CRASH" to "It crashed.",
        "CRASH_NATIVE" to "It crashed.",
        "ANR" to "It stopped responding.",
        "USER_REQUESTED" to "It was force stopped.",
    )

/** Why a navigation or the bot stopped the queue, and the fix, which ends in pressing Start. */
internal class KeyText(val reason: String, private val fix: String?) {
    fun next(resumable: Boolean): String = if (fix == null) pressStart(resumable).replaceFirstChar { it.uppercase() } else "$fix, then ${pressStart(resumable)}"
}

/** The reason keys the navigator and the bot's own stops set; any other key reads as a screen the bot could not pass. */
internal val REPORT_REASON_KEYS =
    mapOf(
        "TP_EMPTY" to KeyText("TP ran out.", "Wait for TP to refill, or turn on Restore TP with Items in Run Queue Settings"),
        "TP_RESTORE_CAP" to KeyText("it reached its limit of TP restores for one Start.", null),
        "TP_NO_RESTORE_ITEM" to KeyText("no TP restore item was found.", "Restore TP by hand"),
        "TP_CARATS_NOT_ALLOWED" to
            KeyText(
                "no Toughness 30 or Star Fruit was left, and Carats are not allowed for TP restores.",
                "Restock Toughness 30 or Star Fruit, restore TP by hand, or turn on Allow Carats for TP Restore in Run Queue Settings",
            ),
        "VETERAN_ROSTER_FULL" to KeyText("your Veteran list is full.", "Transfer or release Veterans in the game"),
        "UNSPENT_SKILL_POINTS" to KeyText("the career ended with skill points left, so the bot did not press Finish.", "Spend the points (or press Finish yourself)"),
        "SPARKS_NEED_HAND" to KeyText("the Spark selection needs you.", "Finish the Spark selection in the game (keeping the original set is always safe)"),
        "REUSE_OFF" to KeyText("Reuse Last Launch Setup is off, so the next career could not be set up.", "Turn on Reuse Last Launch Setup in Run Queue Settings, or set up the career by hand"),
        "REQUIRED_DECK" to DECK_TEXT,
        "DECK_INCOMPLETE" to DECK_TEXT,
        "BORROW_NEEDS_HAND" to DECK_TEXT,
        "TRAINEE_IN_DECK" to
            KeyText(
                "the support deck has a card of the trainee's own character, so the game would not start the career. Nothing was spent.",
                "Swap that card or choose another deck in the game, or turn on Auto-Fill Support Deck with Required Support Deck off",
            ),
        "CAPTURE_OR_ACCESSIBILITY" to KeyText("the bot lost screen capture or its accessibility service.", "Check that both are on"),
        "STUCK_ON_SCREEN" to STUCK_TEXT,
        "TRAINEE_NOT_FOUND" to KeyText("the next trainee in the rotation was not found on the trainee list.", "Check the rotation list, or pick the trainee by hand"),
        ONLY_OTHER_OUTFIT to
            KeyText(
                "the next trainee is on the trainee list only in an outfit that has its own preset.",
                "Apply that outfit's own preset on Home, or pick it for this trainee under Rotate Trainees in Run Queue Settings",
            ),
        "NAVIGATION_TIMEOUT" to KeyText("getting to the next career took too long.", "Check that the game is responding"),
        "NAVIGATION_UNRESPONSIVE" to KeyText("getting to the next career stopped responding.", "Check that the game is responding"),
        "TRAINEE_MISMATCH" to KeyText("the trainee in the career was not the one the rotation expected.", "Check the rotation list, return the game to its home screen"),
        "CONNECTION_LOST" to KeyText("the game lost its connection to its server and did not reconnect in time.", "Check the device's internet connection and clear the error in the game"),
        "DOWNLOAD_FAILED" to KeyText("the game could not finish downloading its data.", "Check the device's internet connection and let the game finish its download"),
        "DATA_DOWNLOAD_PROMPT" to
            KeyText(
                "the game asked to download additional data, and the bot could not find the prompt's OK button.",
                "Answer the game's data prompt (OK, or Title Screen for a data update) and let any download finish",
            ),
        "SESSION_EXPIRED" to KeyText("the game ended its session and needs to go back to its title screen.", "Tap Title Screen in the game and wait for its home screen"),
        "GAME_UNRECOVERABLE" to KeyText("the game froze on a screen the bot could not recognise, and UMA Auto+ could not close it. $CLOSE_FROZEN_GAME.", null),
        "A11Y_GRANT_MISSING" to
            KeyText(
                "its accessibility service needed a repair, and UMA Auto+ does not have the permission to repair it.",
                "Grant the self-repair permission shown on Home, and turn the accessibility service back on if it is off",
            ),
        "A11Y_INPUT_DEAD" to KeyText("its taps stopped having any effect, even after it restarted its accessibility service.", "Restart MuMu or the device"),
        "PURCHASE_PROMPT" to KeyText("the game opened a Carat purchase or age check, which the bot never touches.", "Close it in the game"),
    )

private val DECK_TEXT get() = KeyText("the support deck for the next career could not be set up.", "Set up the deck in the game (or change Required Support Deck)")

// In the reason itself: the interrupted-queue banner shows only the reason.
private const val CLOSE_FROZEN_GAME = "Close the game fully (swipe it away in Recent apps) and open it again"

private val STUCK_TEXT get() = KeyText("it reached a screen it could not get past.", "Open the game and clear the screen it stopped on")

private fun keyText(key: String, r: JSONObject): KeyText =
    namedOutfit(key, r)?.let { (trainee, outfit) ->
        // A rotation's trainee is fixed in its rotation row; a queue without rotation runs the preset applied on Home.
        val fix =
            if (r.optBoolean("reasonRotation")) {
                "Pick the $trainee ($outfit) preset for this trainee under Rotate Trainees in Run Queue Settings"
            } else {
                "Apply the $trainee ($outfit) preset on Home"
            }
        KeyText("$trainee is on your roster only as $outfit, which has its own preset.", fix)
    } ?: REPORT_REASON_KEYS[key] ?: STUCK_TEXT

/** Set when Trainee Select found the trainee only in an outfit her plain preset skips; the report names both. */
private const val ONLY_OTHER_OUTFIT = "TRAINEE_ONLY_OTHER_OUTFIT"

/** The preset trainee and outfit an [ONLY_OTHER_OUTFIT] report names, or null for any other key or a report without them. */
private fun namedOutfit(key: String, r: JSONObject): Pair<String, String>? {
    if (key != ONLY_OTHER_OUTFIT) return null
    val trainee = r.optString("reasonTrainee").trim()
    val outfit = r.optString("reasonOutfit").trim()
    return if (trainee.isEmpty() || outfit.isEmpty()) null else trainee to outfit
}

/** A single run stops at Trainee Select before anything is spent; its fix is on Home, not in a rotation. */
private fun singleRunOutfitText(r: JSONObject): ReportText {
    val named = namedOutfit(ONLY_OTHER_OUTFIT, r)
    val why =
        named?.let { (trainee, outfit) -> KeyText("$trainee is on your roster only as $outfit, which has its own preset.", "Apply the $trainee ($outfit) preset on Home") }
            ?: KeyText("the trainee is on the trainee list only in an outfit that has its own preset.", "Apply that outfit's own preset on Home")
    return ReportText("Run stopped", "The run stopped at Trainee Select: ${why.reason}", why.next(false))
}

private const val OPEN_APP_AND_START = "Open UMA Auto+, press Start, then tap the overlay button."

/** Together these are [StartModule.SETTINGS_NOT_DELIVERED_MESSAGE], the refusal's log line. */
internal const val LAUNCH_IDENTITY_REASON = "Not started, and nothing was spent: the settings UMA Auto+ checked when you pressed Start did not reach the bot."
internal const val LAUNCH_IDENTITY_NEXT =
    "Force stop UMA Auto+ (Android Settings, Apps, UMA Auto+, Force stop), reopen it, turn its accessibility service back on if it was switched off, then press Start again."

/** Together these are [StartModule.DATABASE_UNHEALTHY_MESSAGE], the refusal's dialog. */
internal const val DATABASE_UNHEALTHY_REASON = "Not started, and nothing was spent: UMA Auto+ could not safely use its saved settings file."
internal const val DATABASE_UNHEALTHY_NEXT =
    "If your device's storage is full, free some space first. Then force stop UMA Auto+ (Android Settings, Apps, UMA Auto+, Force stop), " +
        "reopen it, turn its accessibility service back on if it was switched off, and press Start again. " +
        "When it opens, it checks the file again and restores it from the last good backup if it is damaged. " +
        "If this message comes back, please report it as a bug."
