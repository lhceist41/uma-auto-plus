package com.steve1316.uma_android_automation

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.steve1316.uma_android_automation.bot.SparkRowFact
import com.steve1316.uma_android_automation.bot.SparkRowKind
import com.steve1316.uma_android_automation.bot.SparkSetSide
import com.steve1316.uma_android_automation.bot.SparkWhiteClass
import com.steve1316.uma_android_automation.bot.WATCHDOG_BREADCRUMB_FILE
import com.steve1316.uma_android_automation.bot.WATCHDOG_KILL_AT_MS
import com.steve1316.uma_android_automation.bot.WatchdogBreadcrumb
import com.steve1316.uma_android_automation.bot.decodeWatchdogBreadcrumb
import com.steve1316.uma_android_automation.bot.readWatchdogBreadcrumbFile
import com.steve1316.uma_android_automation.utils.OutcomeCorpus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Why a bot session ended. The name is persisted in every queue report and read by the app, so a
 * value is never renamed or reused; add new ones at the end.
 *
 * [clearsQueueState] marks the endings whose code path clears the persisted resume record, so the
 * next Start cannot resume from them. Every other ending leaves whatever record existed in place.
 */
enum class SessionEnd(val clearsQueueState: Boolean = false) {
    /** The start request was refused before any game interaction: not started from the app, or the launch choice no longer matched. */
    REFUSED_NO_APP_START,

    /** The settings the bot read are not the ones the app checked at Start (changed after the check, or never reached the bot). Nothing was started. */
    REFUSED_LAUNCH_IDENTITY,

    /** An explicitly launched diagnostic ran and ended the session. */
    DIAGNOSTIC_ENDED,

    /** A rotation queue started outside the app, without its per-trainee snapshots. */
    ROTATION_NOT_PREPARED,

    /** A resumed queue was already at its last run. */
    NOTHING_TO_RESUME(clearsQueueState = true),

    /** The first trainee's rotation snapshot was missing. */
    FIRST_SNAPSHOT_MISSING,

    /** The career launch from the home screen failed before the first run. */
    LAUNCH_FAILED_BEFORE_RUN,

    /** The player stopped the queue. A stop in the middle of a career keeps the resume record; any other clears it. */
    STOPPED_BY_USER,

    /** The bot stopped the queue itself (trainee mismatch, or a navigation that stopped responding). */
    STOPPED_BY_BOT(clearsQueueState = true),

    /** The bot service went away without the app asking it to (overlay Stop, capture stop, or an error). */
    SERVICE_ENDED(clearsQueueState = true),

    /** A run hit a breakpoint; its career is still in the game's slot. */
    BREAKPOINT,

    /** The game could not be recovered to a screen the bot can drive. */
    GAME_UNRECOVERABLE,

    /** A run ended with an error and stop-on-error is on. */
    STOP_ON_ERROR,

    /** The rotation snapshot for the next trainee was missing. */
    NEXT_SNAPSHOT_MISSING,

    /** The navigation between two runs failed. */
    NAVIGATION_FAILED_BETWEEN_RUNS,

    /** The wait between runs was interrupted with no stop requested (usually the screen turning off). */
    WAIT_INTERRUPTED,

    /** Every queued run was played. */
    COMPLETED(clearsQueueState = true),

    /** A single run with the queue off ended; the run record says how. */
    SINGLE_RUN_ENDED,

    /** An unexpected error ended the session. */
    ENDED_WITH_ERROR,

    /** The app process died mid-session; found at the next app start. */
    PROCESS_ENDED,

    /** Start found the settings database damaged or unopenable and refused before anything ran. */
    REFUSED_DATABASE_UNHEALTHY,

    /** A run stopped for the reason its key names (an accessibility repair that could not help); its career is still in the slot. */
    RUN_HALTED,

    /** The player asked to stop after the current career; it finished, and the next run was saved for Start to continue. */
    STOPPED_AFTER_CAREER,
}

/**
 * What the session knew when it ended. Every field is a flag or a code the session already
 * tracked; no message text.
 *
 * @property haltEnd The halt the queue recorded (set with `queueHaltReason`), or null.
 * @property lastRunIncomplete True when the last run played ended in anything but a completed career.
 */
internal data class SessionEndFacts(
    val launchRefused: Boolean = false,
    val launchIdentityRefused: Boolean = false,
    val diagnosticRan: Boolean = false,
    val rotationNotPrepared: Boolean = false,
    val unexpectedError: Boolean = false,
    val queueEnabled: Boolean = false,
    val haltEnd: SessionEnd? = null,
    val haltCareerInFlight: Boolean = false,
    val nothingToResume: Boolean = false,
    val stoppedAfterCareer: Boolean = false,
    val stopRequested: Boolean = false,
    val stopByBot: Boolean = false,
    val serviceRunning: Boolean = true,
    val queueStateActive: Boolean = false,
    val lastRunIncomplete: Boolean = false,
)

internal data class SessionEndVerdict(val end: SessionEnd, val resumable: Boolean, val careerInFlight: Boolean)

/**
 * The single decision of how a session ended, mirroring the order the session itself exits in:
 * refusals and the diagnostic return before the queue starts, an escaped exception preempts
 * everything after it, a queue-off session is always one run, and the queue's own post-loop
 * branches (halt, then the player's stop after a career, bot stop, user stop, dead service,
 * completion) follow. A stop, or the service going away, after the stop point still wins over the
 * pause, as the post-loop branches decide it.
 *
 * `resumable` is whether the persisted resume record survives this ending: an ending that clears
 * it is never resumable, any other keeps what [SessionEndFacts.queueStateActive] reports.
 */
internal fun classifySessionEnd(facts: SessionEndFacts): SessionEndVerdict {
    val end =
        when {
            facts.launchRefused -> SessionEnd.REFUSED_NO_APP_START
            facts.launchIdentityRefused -> SessionEnd.REFUSED_LAUNCH_IDENTITY
            facts.diagnosticRan -> SessionEnd.DIAGNOSTIC_ENDED
            facts.rotationNotPrepared -> SessionEnd.ROTATION_NOT_PREPARED
            facts.unexpectedError -> SessionEnd.ENDED_WITH_ERROR
            !facts.queueEnabled -> SessionEnd.SINGLE_RUN_ENDED
            facts.haltEnd != null -> facts.haltEnd
            facts.nothingToResume -> SessionEnd.NOTHING_TO_RESUME
            facts.stoppedAfterCareer && !facts.stopRequested && facts.serviceRunning -> SessionEnd.STOPPED_AFTER_CAREER
            facts.stopRequested && facts.stopByBot -> SessionEnd.STOPPED_BY_BOT
            facts.stopRequested -> SessionEnd.STOPPED_BY_USER
            !facts.serviceRunning -> SessionEnd.SERVICE_ENDED
            else -> SessionEnd.COMPLETED
        }
    // A halt knows exactly whether it left a career in the slot; any other ending can only go by
    // how the last run it played ended (false when it played none).
    val careerInFlight = if (facts.haltEnd != null && end == facts.haltEnd) facts.haltCareerInFlight else facts.lastRunIncomplete
    return SessionEndVerdict(end, resumable = !end.clearsQueueState && facts.queueStateActive, careerInFlight = careerInFlight)
}

/**
 * One played run. Career facts are null unless this run itself produced the career-end record.
 * [retried] is true when the run ended with an error once and was played again; [resultCode] is
 * how the second attempt ended.
 */
internal data class RunRecord(
    val run: Int,
    val startedAt: Long,
    val endedAt: Long,
    val resultCode: String,
    val trainee: String?,
    val scenario: String?,
    val outcome: String?,
    val turn: Int?,
    val retried: Boolean = false,
    /** The run window's detect-only progress measurements ([com.steve1316.uma_android_automation.utils.ProgressTracker.endWindow]): keys and numbers. */
    val progress: JSONObject? = null,
    /** [trainee] as the game shows the name, for the Home card; [trainee] stays the stored identifier. */
    val traineeName: String? = null,
    val result: CareerResult? = null,
    /** The kept spark set, attached after the career-end flow read it; null when it was not read. */
    val sparks: List<KeptSpark>? = null,
    val sparksNote: String? = null,
    /** Report reason key for a launch that stopped before Start Career; null for a run that launched. */
    val reasonKey: String? = null,
    val reasonTrainee: String? = null,
    val reasonOutfit: String? = null,
    /** The career was played to its end, but the game kept it: the Finish never went through, so the run is not a finished one. */
    val finishLost: Boolean = false,
)

/**
 * A finished career's result as the bot computed it at its end; a value it did not read is null. [finalStats] is
 * speed, stamina, power, guts, wit. [lastKnownStats] names the entries the career-end reads did not confirm (the
 * last accepted value, not a final one). [finaleOf] counts the finale races seen, not the scenario's total.
 */
internal data class CareerResult(
    val rank: String?,
    val estScore: Int?,
    val fans: Int?,
    val finaleWon: Int?,
    val finaleOf: Int?,
    val finalStats: List<Int?>?,
    val lastKnownStats: List<String> = emptyList(),
)

private val FINAL_STAT_NAMES = listOf("speed", "stamina", "power", "guts", "wit")

private val FINAL_STAT_NAME_BY_LEDGER_KEY = mapOf("spd" to "speed", "sta" to "stamina", "pwr" to "power", "grt" to "guts", "wit" to "wit")

/** One kept spark; [type] is `stat`, `aptitude`, `unique`, `skill` or `other`. */
internal data class KeptSpark(val name: String, val type: String, val stars: Int)

/** The kept sparks the career-end flow recorded, tagged with the career-end sequence they followed. */
internal data class CareerEndSparks(val seq: Long, val sparks: List<KeptSpark>, val note: String?)

/** The career-end facts `Campaign.careerEndLedgerLine` stashed, with the sequence number it bumped. */
internal data class CareerEndStash(val seq: Long, val trainee: String?, val scenario: String?, val outcome: String?, val turn: Int?, val traineeName: String? = null, val result: CareerResult? = null)

/**
 * The result of a career that ended, or null for one that did not (a stop or error mid-career:
 * its stats and rank are not final). A stat of -1 is unread. Fans of 1 are not taken as a read:
 * 1 is both the trainee's default and every career's starting count.
 */
internal fun careerResultAtEnd(
    outcome: String,
    rankLabel: String?,
    estScore: Int?,
    fans: Int,
    finaleRaces: Int,
    finaleWins: Int,
    stats: List<Int>,
    lastKnownLedgerKeys: List<String> = emptyList(),
): CareerResult? {
    if (outcome == "INCOMPLETE") return null
    val finalStats = stats.map { v -> v.takeIf { it >= 0 } }.takeIf { list -> list.any { it != null } }
    val finale = finaleRaces > 0
    val lastKnown = lastKnownLedgerKeys.mapNotNull(FINAL_STAT_NAME_BY_LEDGER_KEY::get).filter { name -> finalStats?.getOrNull(FINAL_STAT_NAMES.indexOf(name)) != null }
    return CareerResult(rankLabel, estScore, fans.takeIf { it > 1 }, finaleWins.takeIf { finale }, finaleRaces.takeIf { finale }, finalStats, lastKnown)
}

/** A spark row as the dashboard types it. A white row is a skill spark only when the skill catalog knew its name; race, scenario and unreadable whites are `other`. */
internal fun keptSpark(row: SparkRowFact): KeptSpark {
    val type =
        when (row.kind) {
            SparkRowKind.WHITE -> if (row.whiteClass == SparkWhiteClass.SKILL) "skill" else "other"
            else -> row.kind.wire
        }
    return KeptSpark(row.name, type, row.stars)
}

/** Says which set was kept when the career's sparks were rerolled; null when they were not. */
internal fun sparksNoteFor(rerolled: Boolean, kept: SparkSetSide?): String? =
    when {
        !rerolled || kept == null -> null
        kept == SparkSetSide.ORIGINAL -> "rerolled once, kept the original sparks"
        else -> "rerolled once, kept the new sparks"
    }

/** The kept sparks belonging to the run whose career end had sequence [runCareerEndSeq], or null (no career end, or sparks from another career). */
internal fun sparksForRun(runCareerEndSeq: Long?, stash: CareerEndSparks?): CareerEndSparks? = stash?.takeIf { runCareerEndSeq != null && it.seq == runCareerEndSeq }

/**
 * The career-end facts belonging to a run, or null. The stash outlives its run on purpose (the
 * career-end sparks flow reads it after the Campaign is gone), so a run that died before its own
 * career end would otherwise inherit the previous run's trainee. Only a stash whose sequence moved
 * during the run belongs to it.
 */
internal fun careerEndForRun(seqBeforeRun: Long, stash: CareerEndStash): CareerEndStash? = if (stash.seq != seqBeforeRun) stash else null

/**
 * Process-wide recovery and TP-restore tallies for the current session. Lock-free and log-free,
 * so they are safe to bump from recovery code; reset when a session starts.
 */
object SessionTally {
    val accessibilityRebinds = AtomicInteger()
    val accessibilityRewrites = AtomicInteger()

    /** Accessibility repairs refused for lack of WRITE_SECURE_SETTINGS. */
    val accessibilityRepairsRefused = AtomicInteger()

    /** Issued rebinds the stuck ladder outlived: the screen did not change after them. */
    val accessibilityRebindsWithoutChange = AtomicInteger()
    val accessibilityStrongToggles = AtomicInteger()
    val gameRelaunches = AtomicInteger()
    val lobbyReentries = AtomicInteger()
    val connectionHolds = AtomicInteger()

    /** Rung is "Toughness 30", "Star Fruit", "Carats" or "unknown"; context is "launch", "reroll" or "unknown". */
    data class TpRestore(val rung: String, val context: String)

    val tpRestores = ConcurrentLinkedQueue<TpRestore>()

    fun recordTpRestore(rung: String, context: String) {
        tpRestores.add(TpRestore(rung, context))
    }

    fun reset() {
        accessibilityRebinds.set(0)
        accessibilityRewrites.set(0)
        accessibilityRepairsRefused.set(0)
        accessibilityRebindsWithoutChange.set(0)
        accessibilityStrongToggles.set(0)
        gameRelaunches.set(0)
        lobbyReentries.set(0)
        connectionHolds.set(0)
        tpRestores.clear()
    }

    internal fun recoveriesJson(): JSONObject =
        JSONObject()
            .put("accessibilityRebinds", accessibilityRebinds.get())
            .put("accessibilityRewrites", accessibilityRewrites.get())
            .put("accessibilityRepairsRefused", accessibilityRepairsRefused.get())
            .put("accessibilityRebindsWithoutChange", accessibilityRebindsWithoutChange.get())
            .put("accessibilityStrongToggles", accessibilityStrongToggles.get())
            .put("gameRelaunches", gameRelaunches.get())
            .put("lobbyReentries", lobbyReentries.get())
            .put("connectionHolds", connectionHolds.get())

    internal fun tpRestoresJson(): JSONArray = JSONArray().also { arr -> tpRestores.forEach { arr.put(JSONObject().put("rung", it.rung).put("context", it.context)) } }
}

/**
 * The durable record of one bot session. [toJson] is the one serialization: the history line and
 * the app's current report are built from it, so they cannot drift apart.
 */
internal data class QueueReport(
    val sessionId: String,
    val appVersion: String,
    val startedAt: Long,
    val endedAt: Long,
    /** "session" when the session wrote its own report; for a dead session, "exit_info" or "last_seen". */
    val endedAtSource: String,
    val kind: SessionEnd,
    val queueEnabled: Boolean,
    val totalRuns: Int,
    val startFromRun: Int,
    val completedRuns: Int,
    val runReached: Int,
    val careerInFlight: Boolean,
    val resumable: Boolean,
    val reasonKey: String,
    val breakpointDetail: String?,
    val errorPosted: Boolean,
    val runs: JSONArray,
    val recoveries: JSONObject,
    val tpRestores: JSONArray,
    val exitInfo: JSONObject?,
    val reasonTrainee: String? = null,
    val reasonOutfit: String? = null,
    /** Written only when true: the fix is in the rotation. */
    val reasonRotation: Boolean = false,
    /** A resumed queue's earlier sessions ([earlierQueueFor]); written only for a resumed queue. */
    val earlier: JSONObject? = null,
    /** The reason key of a career-end finalize that stopped before Home after the last career finished; written only then. */
    val finalizeStopKey: String? = null,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("v", 1)
            .put("sessionId", sessionId)
            .put("appVersion", appVersion)
            .put("startedAt", startedAt)
            .put("endedAt", endedAt)
            .put("endedAtSource", endedAtSource)
            .put("kind", kind.name)
            .put("queueEnabled", queueEnabled)
            .put("totalRuns", totalRuns)
            .put("startFromRun", startFromRun)
            .put("completedRuns", completedRuns)
            .put("runReached", runReached)
            .put("careerInFlight", careerInFlight)
            .put("resumable", resumable)
            .put("reasonKey", reasonKey)
            .put("breakpointDetail", breakpointDetail ?: JSONObject.NULL)
            .put("errorPosted", errorPosted)
            .put("runs", runs)
            .put("recoveries", recoveries)
            .put("tpRestores", tpRestores)
            .put("exitInfo", exitInfo ?: JSONObject.NULL)
            .apply { reasonTrainee?.let { put("reasonTrainee", it) } }
            .apply { reasonOutfit?.let { put("reasonOutfit", it) } }
            .apply { if (reasonRotation) put("reasonRotation", true) }
            .apply { earlier?.let { put("earlier", it) } }
            .apply { finalizeStopKey?.let { put("finalizeStopKey", it) } }

    /** The history line appended to the ledger file. */
    fun ledgerLine(): String = toJson().toString()

    /** The app's current report: the identical record plus the player's dismissal flag. */
    fun lastReportValue(): String = toJson().put("dismissed", false).toString()
}

/**
 * The session this process is running: the flags and facts the session sets as it goes, which the
 * open-session snapshot and the final report are both built from. Created before the session's
 * `try`, so its `catch` and `finally` can read it on every exit path.
 */
internal class SessionLedger(val sessionId: String, val startedAt: Long, val appVersion: String, val pid: Int) {
    @Volatile var dispatchReturned = false

    @Volatile var launchRefused = false

    @Volatile var launchIdentityRefused = false

    @Volatile var diagnosticRan = false

    @Volatile var rotationNotPrepared = false

    @Volatile var unexpectedError = false

    @Volatile var nothingToResume = false

    /** Set when the queue left its loop at the player's stop after a finished career. */
    @Volatile var stoppedAfterCareer = false

    @Volatile var queueEnabled = false

    @Volatile var totalRuns = 0

    @Volatile var startFromRun = 1

    @Volatile var completedRuns = 0

    @Volatile var currentRun = 0

    @Volatile var phase = StartModule.PHASE_CAREER

    /** Set beside every `queueHaltReason` assignment. */
    @Volatile var haltEnd: SessionEnd? = null

    @Volatile var haltCareerInFlight = false

    @Volatile var haltRun = 0

    /** The breakpoint's own message: developer-authored, the one text the report carries. */
    @Volatile var breakpointDetail: String? = null

    /** The navigation `reasonKey` of a failed launch, or the bot's own stop key. */
    @Volatile var reasonKey = ""

    @Volatile var reasonTrainee = ""

    @Volatile var reasonOutfit = ""

    @Volatile var reasonRotation = false

    /** Whether the last run ended by posting an ExceptionEvent (splits an error from an overlay Stop). */
    @Volatile var errorPosted = false

    /** What a resumed queue played before this session ([earlierQueueFor]), or null. */
    @Volatile var earlier: JSONObject? = null

    /** Set when the last career's finalize stopped before Home: the navigation's reason key. */
    @Volatile var finalizeStopKey: String? = null

    private val runs = mutableListOf<RunRecord>()

    @Synchronized
    fun addRun(record: RunRecord) {
        runs.add(record)
    }

    /** Marks the latest record of [run] as a career the game did not finish, returning the updated record, or null when [run] has none. */
    @Synchronized
    fun markFinishLost(run: Int): RunRecord? {
        val index = runs.indexOfLast { it.run == run }
        if (index < 0) return null
        return runs[index].copy(finishLost = true).also { runs[index] = it }
    }

    /** Adds [kept] to the latest record of [run], returning the updated record, or null when [run] has none. */
    @Synchronized
    fun attachSparks(
        run: Int,
        kept: CareerEndSparks,
    ): RunRecord? {
        val index = runs.indexOfLast { it.run == run }
        if (index < 0) return null
        return runs[index].copy(sparks = kept.sparks, sparksNote = kept.note).also { runs[index] = it }
    }

    @Synchronized
    fun attachLaunchStop(
        run: Int,
        reasonKey: String,
        reasonTrainee: String,
        reasonOutfit: String,
        trainee: String,
    ): RunRecord? {
        val index = runs.indexOfLast { it.run == run }
        if (index < 0) return null
        val record = runs[index]
        return record
            .copy(
                reasonKey = reasonKey,
                reasonTrainee = reasonTrainee.ifEmpty { null },
                reasonOutfit = reasonOutfit.ifEmpty { null },
                traineeName = record.traineeName ?: trainee.ifEmpty { null },
            ).also { runs[index] = it }
    }

    @Synchronized
    private fun runsJson(): JSONArray = JSONArray().also { arr -> runs.forEach { arr.put(runRecordJson(it)) } }

    /** The snapshot a later app start turns into a report if this process dies. */
    @Synchronized
    fun openJson(now: Long = System.currentTimeMillis()): JSONObject =
        JSONObject()
            .put("sessionId", sessionId)
            .put("pid", pid)
            .put("appVersion", appVersion)
            .put("startedAt", startedAt)
            .put("updatedAt", now)
            .put("queueEnabled", queueEnabled)
            .put("totalRuns", totalRuns)
            .put("startFromRun", startFromRun)
            .put("completedRuns", completedRuns)
            .put("currentRun", currentRun)
            .put("phase", phase)
            .put("runs", runsJson())
            .put("recoveries", SessionTally.recoveriesJson())
            .put("tpRestores", SessionTally.tpRestoresJson())
            .apply { earlier?.let { put("earlier", it) } }

    @Synchronized
    fun facts(stopRequested: Boolean, stopByBot: Boolean, serviceRunning: Boolean, queueStateActive: Boolean): SessionEndFacts =
        SessionEndFacts(
            launchRefused = launchRefused,
            launchIdentityRefused = launchIdentityRefused,
            diagnosticRan = diagnosticRan,
            rotationNotPrepared = rotationNotPrepared,
            unexpectedError = unexpectedError,
            queueEnabled = queueEnabled,
            haltEnd = haltEnd,
            haltCareerInFlight = haltCareerInFlight,
            nothingToResume = nothingToResume,
            stoppedAfterCareer = stoppedAfterCareer,
            stopRequested = stopRequested,
            stopByBot = stopByBot,
            serviceRunning = serviceRunning,
            queueStateActive = queueStateActive,
            lastRunIncomplete = runs.lastOrNull()?.let { it.resultCode != "TASK_RESULT_COMPLETE" } ?: false,
        )

    @Synchronized
    fun report(verdict: SessionEndVerdict, endedAt: Long): QueueReport =
        QueueReport(
            sessionId = sessionId,
            appVersion = appVersion,
            startedAt = startedAt,
            endedAt = endedAt,
            endedAtSource = "session",
            kind = verdict.end,
            queueEnabled = queueEnabled,
            totalRuns = totalRuns,
            startFromRun = startFromRun,
            completedRuns = completedRuns,
            runReached = if (verdict.end == haltEnd && haltRun > 0) haltRun else runs.lastOrNull()?.run ?: 0,
            careerInFlight = verdict.careerInFlight,
            resumable = verdict.resumable,
            reasonKey = if (verdict.end in ENDINGS_WITH_REASON_KEY) reasonKey else "",
            // A queue-off session ends as one run even when that run stopped at a breakpoint, so it keeps the breakpoint's own words too.
            breakpointDetail = if (verdict.end == SessionEnd.BREAKPOINT || (verdict.end == SessionEnd.SINGLE_RUN_ENDED && haltEnd == SessionEnd.BREAKPOINT)) breakpointDetail else null,
            errorPosted = errorPosted,
            runs = runsJson(),
            recoveries = SessionTally.recoveriesJson(),
            tpRestores = SessionTally.tpRestoresJson(),
            exitInfo = null,
            reasonTrainee = reasonTrainee.takeIf { verdict.end in ENDINGS_WITH_REASON_KEY && reasonKey.isNotEmpty() && it.isNotEmpty() },
            reasonOutfit = reasonOutfit.takeIf { verdict.end in ENDINGS_WITH_REASON_KEY && reasonKey.isNotEmpty() && it.isNotEmpty() },
            reasonRotation = reasonRotation && verdict.end in ENDINGS_WITH_REASON_KEY && reasonKey.isNotEmpty() && reasonTrainee.isNotEmpty() && reasonOutfit.isNotEmpty(),
            earlier = earlier,
            finalizeStopKey = finalizeStopKey.takeIf { verdict.end == SessionEnd.COMPLETED || verdict.end == SessionEnd.SINGLE_RUN_ENDED },
        )
}

/**
 * A resumed queue's runs before [startFromRun], recoveries, TP restores and stops, from the report that left it resumable; kept
 * apart from the session's own fields so no ledger line counts one twice. Null when [lastReport] is not this queue's.
 */
internal fun earlierQueueFor(lastReport: JSONObject?, totalRuns: Int, startFromRun: Int): JSONObject? {
    val r = lastReport ?: return null
    val kind = SessionEnd.entries.firstOrNull { it.name == r.optString("kind") } ?: return null
    if (!r.optBoolean("resumable") || !r.optBoolean("queueEnabled") || r.optInt("totalRuns") != totalRuns || kind in NOT_A_RUN_ENDINGS) return null
    val before = r.optJSONObject("earlier") ?: JSONObject()
    val runs = JSONArray()
    for (played in listOf(before.optJSONArray("runs"), r.optJSONArray("runs"))) {
        for (i in 0 until (played?.length() ?: 0)) played?.optJSONObject(i)?.takeIf { it.optInt("run") in 1 until startFromRun }?.let { runs.put(it) }
    }
    val recoveries = JSONObject()
    for (counts in listOf(before.optJSONObject("recoveries"), r.optJSONObject("recoveries"))) {
        counts?.keys()?.forEach { key -> recoveries.put(key, recoveries.optInt(key) + counts.optInt(key)) }
    }
    val restores = JSONArray()
    for (spent in listOf(before.optJSONArray("tpRestores"), r.optJSONArray("tpRestores"))) {
        for (i in 0 until (spent?.length() ?: 0)) spent?.optJSONObject(i)?.let { restores.put(it) }
    }
    val stops = JSONArray()
    before.optJSONArray("stops")?.let { for (i in 0 until it.length()) it.optJSONObject(i)?.let { stop -> stops.put(stop) } }
    stops.put(JSONObject().put("kind", kind.name).put("run", r.optInt("runReached")).put("reasonKey", r.optString("reasonKey")))
    return JSONObject().put("runs", runs).put("recoveries", recoveries).put("tpRestores", restores).put("stops", stops)
}

private val ENDINGS_WITH_REASON_KEY = setOf(SessionEnd.LAUNCH_FAILED_BEFORE_RUN, SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS, SessionEnd.SINGLE_RUN_ENDED, SessionEnd.STOPPED_BY_BOT, SessionEnd.RUN_HALTED)

internal fun runRecordJson(r: RunRecord): JSONObject =
    JSONObject()
        .put("run", r.run)
        .put("startedAt", r.startedAt)
        .put("endedAt", r.endedAt)
        .put("resultCode", r.resultCode)
        .put("trainee", r.trainee ?: JSONObject.NULL)
        .put("scenario", r.scenario ?: JSONObject.NULL)
        .put("outcome", r.outcome ?: JSONObject.NULL)
        .put("turn", r.turn ?: JSONObject.NULL)
        .put("retried", r.retried)
        .apply { r.progress?.let { put("progress", it) } }
        .apply { r.traineeName?.let { put("traineeName", it) } }
        .apply { if (r.finishLost) put("finishLost", true) }
        .apply {
            // Additive and present only when known, so records written before these keys read the same.
            r.result?.let { result ->
                result.rank?.let { put("rank", it) }
                result.estScore?.let { put("estScore", it) }
                result.fans?.let { put("fans", it) }
                finaleJson(result)?.let { put("finale", it) }
                finalStatsJson(result)?.let { put("finalStats", it) }
                lastKnownStatsJson(result)?.let { put("lastKnownStats", it) }
            }
            r.sparks?.let { put("sparks", sparksJson(it)) }
            r.sparksNote?.let { put("sparksNote", it) }
            r.reasonKey?.let { put("reasonKey", it) }
            r.reasonTrainee?.let { put("reasonTrainee", it) }
            r.reasonOutfit?.let { put("reasonOutfit", it) }
        }

internal fun finaleJson(result: CareerResult): JSONObject? {
    val won = result.finaleWon ?: return null
    val of = result.finaleOf ?: return null
    return JSONObject().put("won", won).put("of", of)
}

internal fun finalStatsJson(result: CareerResult): JSONObject? {
    val stats = result.finalStats ?: return null
    return JSONObject().apply { FINAL_STAT_NAMES.forEachIndexed { i, name -> put(name, stats.getOrNull(i) ?: JSONObject.NULL) } }
}

internal fun lastKnownStatsJson(result: CareerResult): JSONArray? = result.lastKnownStats.takeIf { it.isNotEmpty() }?.let { JSONArray(it) }

internal fun sparksJson(sparks: List<KeptSpark>): JSONArray = JSONArray().also { arr -> sparks.forEach { arr.put(JSONObject().put("name", it.name).put("type", it.type).put("stars", it.stars)) } }

/**
 * Whether a stored open-session record belongs to a session that died without writing its report.
 * It does unless it is the session this process opened (held in memory, so a new process has none,
 * whatever pid it was given), or the session whose report was already written (its finally
 * reported, then failed to delete the record).
 */
internal fun isOrphanedSession(openSessionId: String?, reportedSessionId: String?, liveSessionId: String?): Boolean =
    !openSessionId.isNullOrEmpty() && openSessionId != reportedSessionId && openSessionId != liveSessionId

/** An `ApplicationExitInfo`, reduced to the fields the report uses; [summary] is its process-state summary. */
internal data class ExitRecord(val pid: Int, val timestamp: Long, val reason: Int, val status: Int, val summary: String? = null)

/**
 * The stall-watchdog rung the dead process had taken, when that explains its exit: its exit record's
 * summary when Android killed it by signal (the watchdog's kill; any other exit reason is not the
 * watchdog's), or, below API 30 where there is no exit record, the breadcrumb file it left.
 */
internal fun watchdogBreadcrumbFor(exit: ExitRecord?, file: WatchdogBreadcrumb?): WatchdogBreadcrumb? =
    if (exit != null) decodeWatchdogBreadcrumb(exit.summary)?.takeIf { exit.reason == ApplicationExitInfo.REASON_SIGNALED } else file

/** The exit facts the report keeps: the exit record, and the watchdog's stall when it stopped the app. */
internal fun exitInfoJson(exit: ExitRecord?, watchdog: WatchdogBreadcrumb?): JSONObject? {
    if (exit == null && watchdog == null) return null
    val json = JSONObject()
    if (exit != null) json.put("reason", exitReasonKey(exit.reason)).put("status", exit.status).put("timestamp", exit.timestamp)
    if (watchdog != null) json.put("watchdog", JSONObject().put("rung", watchdog.rung).put("stalledSeconds", WATCHDOG_KILL_AT_MS / 1000))
    return json
}

/**
 * The exit record of the dead session's own process: same pid, and not older than the session
 * (a pid can be reused). Null when there is no such record, or none at all (API below 30).
 */
internal fun pickExitRecord(records: List<ExitRecord>?, deadPid: Int, sessionStartedAt: Long): ExitRecord? =
    records?.firstOrNull { it.pid == deadPid && it.timestamp >= sessionStartedAt }

/**
 * A stable key for an exit reason. `SIGNALED` with signal 9 is most likely the stall watchdog's own
 * kill, but other SIGKILLs look the same, so the key stays neutral and the status is kept beside it.
 */
internal fun exitReasonKey(reason: Int): String =
    when (reason) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        else -> "OTHER"
    }

/**
 * Builds the report for a session that died without its finally, from its last open-session
 * snapshot. The stop time is the exit record's when Android kept one; otherwise the last heartbeat
 * the session wrote, which is as close as the evidence gets after a host or emulator crash.
 */
internal fun processEndedReport(open: JSONObject, exit: ExitRecord?, lastSeenAt: Long?, resumable: Boolean, watchdog: WatchdogBreadcrumb? = null): QueueReport {
    val updatedAt = open.optLong("updatedAt", open.optLong("startedAt"))
    val seen = maxOf(updatedAt, lastSeenAt ?: 0L)
    val phase = open.optString("phase", StartModule.PHASE_CAREER)
    return QueueReport(
        sessionId = open.optString("sessionId"),
        appVersion = open.optString("appVersion"),
        startedAt = open.optLong("startedAt"),
        endedAt = exit?.timestamp ?: seen,
        endedAtSource = if (exit != null) "exit_info" else "last_seen",
        kind = SessionEnd.PROCESS_ENDED,
        queueEnabled = open.optBoolean("queueEnabled"),
        totalRuns = open.optInt("totalRuns"),
        startFromRun = open.optInt("startFromRun"),
        completedRuns = open.optInt("completedRuns"),
        runReached = open.optInt("currentRun"),
        // The open record is refreshed as each run starts; a death in the launching phase may or
        // may not have started the next career, and that phase is kept in the snapshot.
        careerInFlight = phase == StartModule.PHASE_CAREER && open.optInt("currentRun") > 0,
        resumable = resumable,
        reasonKey = "",
        breakpointDetail = null,
        errorPosted = false,
        runs = open.optJSONArray("runs") ?: JSONArray(),
        recoveries = open.optJSONObject("recoveries") ?: JSONObject(),
        tpRestores = open.optJSONArray("tpRestores") ?: JSONArray(),
        exitInfo = exitInfoJson(exit, watchdog),
        earlier = open.optJSONObject("earlier"),
    )
}

/**
 * Whether an exception that escaped before dispatch returned is one of `DebugTestGate.consume`'s
 * refusals: its `check` for a start not made from the app (an `IllegalStateException`, thrown before
 * the settings snapshot is read) or one of its `require`s once the snapshot is read (an
 * `IllegalArgumentException`: the arms, scenario or parameters no longer match the launch choice).
 * A throw from inside the snapshot read itself (started, not finished) is a failed read, and any
 * other exception type is an error.
 */
internal fun isLaunchGateRefusal(error: Throwable, snapshotReadStarted: Boolean, snapshotReadFinished: Boolean): Boolean {
    val insideSnapshotRead = snapshotReadStarted && !snapshotReadFinished
    return !insideSnapshotRead && (error is IllegalStateException || error is IllegalArgumentException)
}

/** The player-readable log line for a [isLaunchGateRefusal] refusal; the raw reason goes to logcat only. */
internal fun launchRefusalLine(error: Throwable): String =
    if (error is IllegalStateException) {
        "[START] Nothing was started: each session needs a fresh Start in UMA Auto+. Press Start there, then tap the overlay button."
    } else {
        "[START] Nothing was started: the settings did not pass the launch check. Press Start in UMA Auto+ again; if this repeats, check the selected diagnostic test and scenario."
    }

/** The running session's heartbeat file under `filesDir`, kept out of settings.db so the periodic write never opens the database. */
internal const val HEARTBEAT_FILE = "queue_heartbeat"

/**
 * Records that [sessionId] was alive at [now] as `sessionId:millis`, written to a temp file and
 * renamed over the old one so a reader never sees a torn value. Android's rename replaces the
 * target atomically; the delete-and-retry only serves filesystems whose rename refuses to replace.
 */
internal fun writeHeartbeat(dir: File, sessionId: String, now: Long): Boolean {
    val target = File(dir, HEARTBEAT_FILE)
    val temp = File(dir, "$HEARTBEAT_FILE.tmp")
    temp.writeText("$sessionId:$now")
    if (temp.renameTo(target)) return true
    target.delete()
    return temp.renameTo(target)
}

/** When [sessionId] last recorded itself alive, or null when the file is missing, unreadable or another session's. */
internal fun readHeartbeat(dir: File, sessionId: String): Long? =
    try {
        File(dir, HEARTBEAT_FILE).readText().split(':').takeIf { it.size == 2 && it[0] == sessionId }?.get(1)?.toLongOrNull()
    } catch (_: Exception) {
        null
    }

/** The record of a Start refused because the settings database is unhealthy: nothing ran, so there are no runs and no settings to report. */
internal fun databaseRefusalReport(sessionId: String, appVersion: String, now: Long): QueueReport =
    QueueReport(
        sessionId = sessionId,
        appVersion = appVersion,
        startedAt = now,
        endedAt = now,
        endedAtSource = "session",
        kind = SessionEnd.REFUSED_DATABASE_UNHEALTHY,
        queueEnabled = false,
        totalRuns = 0,
        startFromRun = 0,
        completedRuns = 0,
        runReached = 0,
        careerInFlight = false,
        resumable = false,
        reasonKey = "",
        breakpointDetail = null,
        errorPosted = false,
        runs = JSONArray(),
        recoveries = JSONObject(),
        tpRestores = JSONArray(),
        exitInfo = null,
    )

/**
 * Endings that played no queue or run: refused starts, diagnostics, and a Start that found its
 * saved queue already at its end.
 */
internal val NOT_A_RUN_ENDINGS =
    setOf(
        SessionEnd.REFUSED_NO_APP_START,
        SessionEnd.REFUSED_LAUNCH_IDENTITY,
        SessionEnd.REFUSED_DATABASE_UNHEALTHY,
        SessionEnd.ROTATION_NOT_PREPARED,
        SessionEnd.DIAGNOSTIC_ENDED,
        SessionEnd.NOTHING_TO_RESUME,
    )

/**
 * Whether a finished session's report becomes the app's current report. An ending that played no
 * run never replaces an undismissed report of one, so a stray overlay tap in the morning cannot
 * erase the overnight summary; it still reaches the history file. A current report that cannot be
 * read is replaced; one of an ending this version does not know is kept.
 */
internal fun replacesLastReport(kind: SessionEnd, current: String?): Boolean {
    if (kind !in NOT_A_RUN_ENDINGS) return true
    val existing = current?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return true
    if (existing.optBoolean("dismissed")) return true
    val existingKind = SessionEnd.entries.firstOrNull { it.name == existing.optString("kind") } ?: return false
    return existingKind in NOT_A_RUN_ENDINGS
}

/**
 * Exit records for this package, or null below API 30, where Android keeps none. [fetch] is only
 * called when the API exists.
 */
internal fun exitRecordsFor(sdkInt: Int, fetch: () -> List<ExitRecord>): List<ExitRecord>? = if (sdkInt >= 30) fetch() else null

/**
 * Durable storage for the queue ledger. The open-session record and the app's current report live
 * in `settings.db` under the Kotlin-owned `queueState` category (JS settings loading skips it, and
 * `loadQueueState` ignores keys it does not know), written only when a session starts, at run
 * boundaries and when it ends; the minute heartbeat is a file ([HEARTBEAT_FILE]). Every finished
 * report is also appended to [OutcomeCorpus.QUEUE_LEDGER_PATH]. Every write swallows its own
 * failure: a lost report must never break the session that is ending.
 */
object QueueLedger {
    private const val TAG = "QueueLedger"
    private const val CATEGORY = "queueState"
    const val KEY_OPEN_SESSION = "openSession"
    const val KEY_LAST_REPORT = "lastReport"

    /** How often a running session records that it is still alive. */
    const val HEARTBEAT_MS = 60_000L

    /** Serializes open-session writes, the final report and dead-session detection. */
    private val lock = Any()

    /** The session this process is running now, or null. */
    @Volatile
    private var liveSessionId: String? = null

    private fun dbFile(context: Context) = File(context.filesDir, "SQLite/settings.db")

    private fun read(context: Context, keys: List<String>): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val file = dbFile(context)
        if (!file.exists()) return out
        SettingsDatabase.get(context).let { db ->
            db.rawQuery(
                "SELECT key, value FROM settings WHERE category = ? AND key IN (${keys.joinToString(",") { "?" }})",
                arrayOf(CATEGORY, *keys.toTypedArray()),
            ).use { cursor ->
                while (cursor.moveToNext()) out[cursor.getString(0)] = cursor.getString(1)
            }
        }
        return out
    }

    /** One transaction: a report is never stored while its open record survives, or the reverse. */
    private fun write(context: Context, values: Map<String, String>, delete: List<String> = emptyList()) {
        val file = dbFile(context)
        if (!file.exists()) return
        SettingsDatabase.get(context).let { db ->
            db.beginTransaction()
            try {
                for ((key, value) in values) {
                    db.execSQL("INSERT OR REPLACE INTO settings (category, key, value) VALUES (?, ?, ?)", arrayOf(CATEGORY, key, value))
                }
                for (key in delete) {
                    db.execSQL("DELETE FROM settings WHERE category = ? AND key = ?", arrayOf(CATEGORY, key))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun markAliveLocked(context: Context, sessionId: String) {
        try {
            if (!writeHeartbeat(context.filesDir, sessionId, System.currentTimeMillis())) Log.w(TAG, "Failed to replace the session heartbeat file.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record the session heartbeat: ${e.message}")
        }
    }

    /** Reports any dead session first, then records this one as open. */
    fun beginSession(context: Context, sessionId: String, open: JSONObject) {
        synchronized(lock) {
            reportDeadSessionLocked(context)
            liveSessionId = sessionId
            try {
                write(context, mapOf(KEY_OPEN_SESSION to open.toString()))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record the open session: ${e.message}")
            }
            markAliveLocked(context, sessionId)
        }
    }

    /** Rewrites the open-session snapshot at a run boundary. */
    fun refreshOpenSession(context: Context, sessionId: String, open: JSONObject) {
        synchronized(lock) {
            if (liveSessionId != sessionId) return
            try {
                write(context, mapOf(KEY_OPEN_SESSION to open.toString()))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh the open session: ${e.message}")
            }
            markAliveLocked(context, sessionId)
        }
    }

    /** Heartbeat: records that [sessionId] is still alive, so a later death can be dated. Touches only the heartbeat file. */
    fun markAlive(context: Context, sessionId: String) {
        synchronized(lock) {
            if (liveSessionId != sessionId) return
            markAliveLocked(context, sessionId)
        }
    }

    /** Writes the finished session's report to both stores and closes its open record. */
    internal fun finishSession(context: Context, report: QueueReport) {
        synchronized(lock) {
            // A dead session found now is older than this one, so it is reported first and this
            // report ends up as the current one.
            reportDeadSessionLocked(context)
            store(context, report)
            if (liveSessionId == report.sessionId) liveSessionId = null
        }
    }

    private fun store(context: Context, report: QueueReport) {
        try {
            val current = read(context, listOf(KEY_LAST_REPORT))[KEY_LAST_REPORT]
            val values = if (replacesLastReport(report.kind, current)) mapOf(KEY_LAST_REPORT to report.lastReportValue()) else emptyMap()
            write(context, values, delete = listOf(KEY_OPEN_SESSION))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to store the queue report: ${e.message}")
        }
        File(context.filesDir, HEARTBEAT_FILE).delete()
        File(context.filesDir, WATCHDOG_BREADCRUMB_FILE).delete()
        OutcomeCorpus.append(context, report.toJson(), OutcomeCorpus.QUEUE_LEDGER_PATH)
    }

    /**
     * Records a Start refused for an unhealthy settings database in the history file only: writing
     * the app's current report into a database that just failed its integrity check could make the
     * damage worse, and the app could not trust what it read back anyway.
     */
    fun recordDatabaseRefusal(context: Context, appVersion: String) {
        OutcomeCorpus.append(context, databaseRefusalReport(java.util.UUID.randomUUID().toString(), appVersion, System.currentTimeMillis()).toJson(), OutcomeCorpus.QUEUE_LEDGER_PATH)
    }

    /** Reports a session that died without its report, if one is recorded. */
    fun reportDeadSession(context: Context) {
        synchronized(lock) { reportDeadSessionLocked(context) }
    }

    private fun reportDeadSessionLocked(context: Context) {
        try {
            val stored = read(context, listOf(KEY_OPEN_SESSION, KEY_LAST_REPORT))
            val open = stored[KEY_OPEN_SESSION]?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
            val reportedId = stored[KEY_LAST_REPORT]?.let { runCatching { JSONObject(it).optString("sessionId") }.getOrNull() }
            val sessionId = open.optString("sessionId")
            if (!isOrphanedSession(sessionId, reportedId, liveSessionId)) return
            val deadPid = open.optInt("pid")
            val seen = readHeartbeat(context.filesDir, sessionId)
            val records = exitRecordsFor(Build.VERSION.SDK_INT) { readExitRecords(context) }
            val exit = pickExitRecord(records, deadPid, open.optLong("startedAt"))
            val file = if (records == null) readWatchdogBreadcrumbFile(context.filesDir, deadPid, open.optLong("startedAt")) else null
            store(context, processEndedReport(open, exit, seen, resumable = StartModule.loadQueueState(context) != null, watchdog = watchdogBreadcrumbFor(exit, file)))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check for a dead session: ${e.message}")
        }
    }

    @RequiresApi(30)
    private fun readExitRecords(context: Context): List<ExitRecord> {
        val am = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        return am.getHistoricalProcessExitReasons(context.packageName, 0, 10).map { ExitRecord(it.pid, it.timestamp, it.reason, it.status, it.processStateSummary?.toString(Charsets.US_ASCII)) }
    }

    /** The app's current report as stored (JSON text), or null. Reports a dead session first. */
    fun lastReport(context: Context): String? {
        synchronized(lock) {
            reportDeadSessionLocked(context)
            return try {
                read(context, listOf(KEY_LAST_REPORT))[KEY_LAST_REPORT]
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read the queue report: ${e.message}")
                null
            }
        }
    }

    /** Marks the current report dismissed, only while it is still the report [sessionId] names. */
    fun dismissLastReport(context: Context, sessionId: String): Boolean {
        synchronized(lock) {
            return try {
                val raw = read(context, listOf(KEY_LAST_REPORT))[KEY_LAST_REPORT] ?: return false
                val report = JSONObject(raw)
                if (report.optString("sessionId") != sessionId) return false
                write(context, mapOf(KEY_LAST_REPORT to report.put("dismissed", true).toString()))
                true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to dismiss the queue report: ${e.message}")
                false
            }
        }
    }
}
