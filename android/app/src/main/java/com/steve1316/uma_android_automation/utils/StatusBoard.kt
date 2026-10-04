package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.QueueReport
import com.steve1316.uma_android_automation.ReportText
import com.steve1316.uma_android_automation.RunRecord
import com.steve1316.uma_android_automation.finalStatsJson
import com.steve1316.uma_android_automation.bot.GrandConcertScenario
import com.steve1316.uma_android_automation.finaleJson
import com.steve1316.uma_android_automation.lastKnownStatsJson
import com.steve1316.uma_android_automation.queueReportText
import com.steve1316.uma_android_automation.runEndedWithError
import com.steve1316.uma_android_automation.runWords
import com.steve1316.uma_android_automation.sparksJson
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/**
 * What the dashboard shows. The queue and bot threads publish plain values into one immutable
 * [Snapshot]; [LogStreamServer] builds the STATUS JSON from it on its own thread with [statusJson].
 *
 * Publishing only swaps a reference: no lock, no log, no I/O, so it is safe from the bot thread and
 * never touches the MessageLog lock. Nothing here reads a live Trainee, GameDate or settings.
 */
internal object StatusBoard {
    data class Career(
        val trainee: String? = null,
        val scenario: String? = null,
        val year: String? = null,
        val dateLabel: String? = null,
        val turn: Int? = null,
        val dateAt: Long? = null,
        /** Speed, stamina, power, guts, wit; null for a stat not read yet. */
        val stats: List<Int?>? = null,
        val statsAt: Long? = null,
        val energyPercent: Int? = null,
        val energyAt: Long? = null,
        val mood: String? = null,
        val moodAt: Long? = null,
        val goalDueTurn: Int? = null,
        val goalName: String? = null,
        val goalAt: Long? = null,
        val actionKind: String? = null,
        val actionDetail: String? = null,
        val actionAt: Long? = null,
        val racesRun: Int = 0,
    )

    data class Snapshot(
        val statusKey: String? = null,
        val statusAt: Long? = null,
        val runCurrent: Int? = null,
        val runTotal: Int? = null,
        /** The last `RunQueueProgress` payload as sent to the app, for Home to re-read after a re-creation. Never part of STATUS. */
        val lastQueueProgress: String? = null,
        val sessionStartedAt: Long? = null,
        val career: Career? = null,
        val runs: List<RunRecord> = emptyList(),
        val endWords: ReportText? = null,
    )

    private val current = AtomicReference(Snapshot())

    fun snapshot(): Snapshot = current.get()

    private fun publish(change: (Snapshot) -> Snapshot) {
        current.updateAndGet(change)
    }

    /** A new Start, or a new session: nothing from the previous one carries over. */
    fun reset(sessionStartedAt: Long? = null) = publish { Snapshot(sessionStartedAt = sessionStartedAt) }

    /** Latest event wins: a queue event replaces the key the last turn set, and the next turn replaces it again. */
    private fun Snapshot.withKey(
        key: String,
        now: Long,
    ) = if (statusKey == key) this else copy(statusKey = key, statusAt = now)

    fun queueProgress(
        currentRun: Int,
        totalRuns: Int,
        status: String,
        payload: String,
        now: Long = System.currentTimeMillis(),
    ) = publish { it.withKey(status, now).copy(runCurrent = currentRun, runTotal = totalRuns, lastQueueProgress = payload) }

    /** Once per career turn, after the turn-start reads. Sets the key to `running`. A stat of -1 is one the bot has not read. */
    fun careerTurn(
        trainee: String?,
        scenario: String?,
        year: String?,
        dateLabel: String?,
        turn: Int,
        rawStats: List<Int>,
        energyPercent: Int,
        mood: String,
        now: Long = System.currentTimeMillis(),
    ) = publish { s ->
        val c = s.career ?: Career()
        val stats = rawStats.map { v -> v.takeIf { it >= 0 } }
        s.withKey("running", now).copy(
            career =
                c.copy(
                    trainee = trainee ?: c.trainee,
                    scenario = scenario,
                    year = year,
                    dateLabel = dateLabel,
                    turn = turn,
                    dateAt = now,
                    stats = stats.takeIf { list -> list.any { it != null } },
                    statsAt = now,
                    energyPercent = energyPercent,
                    energyAt = now,
                    mood = mood,
                    moodAt = now,
                ),
        )
    }

    fun action(
        kind: String,
        detail: String?,
        now: Long = System.currentTimeMillis(),
    ) = publish { it.copy(career = (it.career ?: Career()).copy(actionKind = kind, actionDetail = detail, actionAt = now)) }

    /**
     * The goal countdown the bot read on [turn] anyway, as the absolute turn it falls on. [name] is the
     * race name only when the goal text was already classified; it stays while the deadline is unchanged.
     */
    fun goal(
        turn: Int,
        turnsLeft: Int,
        now: Long = System.currentTimeMillis(),
        name: String? = null,
    ) {
        if (turnsLeft < 0) return
        publish {
            val career = it.career ?: Career()
            val due = turn + turnsLeft
            it.copy(career = career.copy(goalDueTurn = due, goalAt = now, goalName = name ?: career.goalName.takeIf { career.goalDueTurn == due }))
        }
    }

    fun raceRun() = publish { it.copy(career = (it.career ?: Career()).copy(racesRun = (it.career?.racesRun ?: 0) + 1)) }

    /** A run finished: it joins the list and the next career starts empty. */
    fun runRecorded(record: RunRecord) = publish { it.copy(runs = it.runs + record, career = null) }

    /** A recorded run gained facts after it ended (its kept sparks): replaces its latest entry. */
    fun runUpdated(record: RunRecord) =
        publish { s ->
            val index = s.runs.indexOfLast { it.run == record.run }
            if (index < 0) s else s.copy(runs = s.runs.toMutableList().also { it[index] = record })
        }

    /** The session's words, from the same [queueReportText] as the notification and Home. Never throws. */
    fun sessionEnded(report: QueueReport?) {
        val words = runCatching { queueReportText(report?.toJson()) }.getOrNull()
        publish { it.copy(endWords = words) }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // STATUS

    /** The session-wide counts STATUS carries, read by the caller from [com.steve1316.uma_android_automation.SessionTally]. */
    data class Tally(
        val tpItems: Int,
        val tpCarats: Int,
        val accessibility: Int,
        val relaunches: Int,
        val lobby: Int,
        val connection: Int,
        val recoveriesTotal: Int,
    )

    private val TERMINAL_KEYS = setOf("queueComplete", "queueStopped", "queueHalted", "queueFailed", "stoppedAfterCareer")

    /**
     * The keys for which "Stop after this career" is offered: a career starting, being played,
     * retried or finishing. Home's rule on the same queue events (`offersStopAfterCareer`); `running`
     * is the per-turn key only STATUS carries. Not while navigating or waiting between runs: the queue
     * has passed its stop point, so the request would land after the next career.
     */
    private val STOP_AFTER_CAREER_KEYS = setOf("running", "starting", "resuming", "retrying", "completed")

    /** Whether the running queue can take "Stop after this career" now: a queued run with a run after it. */
    fun stopAfterCareerOffered(
        s: Snapshot,
        sessionActive: Boolean,
    ): Boolean {
        val current = s.runCurrent ?: return false
        val total = s.runTotal ?: return false
        return sessionActive && (s.statusKey ?: "running") in STOP_AFTER_CAREER_KEYS && current < total
    }

    /** The display labels of a GameDate's parts, e.g. "Junior Year" and "Late January"; the finale turns have their own names. */
    fun dateLabels(
        yearName: String,
        phaseName: String,
        monthName: String,
        turn: Int,
        scenario: String? = null,
    ): Pair<String?, String?> {
        finaleTurnNames(scenario)?.get(turn)?.let { return null to it }
        return titleCase(yearName) to "${titleCase(phaseName)} ${titleCase(monthName)}"
    }

    private val URA_FINALE_TURNS = mapOf(73 to "Finale Qualifier", 74 to "Finale Semi-Final", 75 to "Finale Finals")
    private val CLIMAX_TURNS = mapOf(73 to "Climax Race 1", 74 to "Climax Race 2", 75 to "Climax Race 3")

    /** Unity Cup and Grand Concert end in the URA Finale races; Trackblazer ends in the Twinkle Star Climax. */
    private fun finaleTurnNames(scenario: String?): Map<Int, String>? =
        when (scenario) {
            null, "URA Finale", "Unity Cup", GrandConcertScenario.KEY -> URA_FINALE_TURNS
            "Trackblazer" -> CLIMAX_TURNS
            else -> null
        }

    private fun titleCase(value: String) = value.lowercase().split(' ', '_').filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    /**
     * The course for [scenario]: three 24-turn years, then the finale on turns 73-75. Every career scenario
     * runs this calendar; other strings (Daily Races, Team Trials, unknown) stay null.
     */
    fun courseJson(scenario: String?): JSONObject? {
        val finaleLabel =
            when (scenario) {
                "URA Finale", "Unity Cup", GrandConcertScenario.KEY -> "URA Finale"
                "Trackblazer" -> "Twinkle Star Climax"
                else -> return null
            }
        val segments = JSONArray()
        listOf("Junior Year", "Classic Year", "Senior Year").forEachIndexed { i, label ->
            segments.put(JSONObject().put("label", label).put("from", i * 24 + 1).put("to", i * 24 + 24))
        }
        segments.put(JSONObject().put("label", finaleLabel).put("from", 73).put("to", 75).put("finale", true))
        return JSONObject().put("finalTurn", 75).put("segments", segments)
    }

    /** The career-end record stores a scenario with underscores ("Grand_Concert"); the page shows the name. */
    private fun scenarioName(stored: String?): Any = stored?.replace('_', ' ') ?: JSONObject.NULL

    /** A run's display name: the name the game showed, else the stored identifier with its spaces back; null when the career-end line had no name ("unknown"). */
    private fun displayName(record: RunRecord): String? = record.traineeName ?: record.trainee?.takeUnless { it == "unknown" }?.replace('_', ' ')

    private fun runState(code: String): String =
        when {
            code == "TASK_RESULT_COMPLETE" -> "done"
            runEndedWithError(code) -> "errored"
            else -> "stopped"
        }

    private fun runsJson(
        s: Snapshot,
        key: String,
        sessionActive: Boolean,
    ): JSONArray {
        val runs = JSONArray()
        for (r in s.runs) {
            val result = r.result
            runs.put(
                JSONObject()
                    .put("n", r.run)
                    .put("trainee", displayName(r) ?: JSONObject.NULL)
                    .put("scenario", scenarioName(r.scenario))
                    .put("state", runState(r.resultCode))
                    .put("outcome", r.outcome ?: JSONObject.NULL)
                    .put("rank", result?.rank ?: JSONObject.NULL)
                    .put("estScore", result?.estScore ?: JSONObject.NULL)
                    .put("fans", result?.fans ?: JSONObject.NULL)
                    .put("finale", result?.let(::finaleJson) ?: JSONObject.NULL)
                    .put("finalStats", result?.let(::finalStatsJson) ?: JSONObject.NULL)
                    .apply { result?.let(::lastKnownStatsJson)?.let { put("lastKnownStats", it) } }
                    .put("sparks", r.sparks?.let(::sparksJson) ?: JSONObject.NULL)
                    .put("sparksNote", r.sparksNote ?: JSONObject.NULL)
                    .put("startedAt", r.startedAt)
                    .put("endedAt", r.endedAt)
                    .put("words", runWords(r)?.let { JSONObject().put("reason", it) } ?: JSONObject.NULL),
            )
        }
        val current = s.runCurrent ?: return runs
        val total = s.runTotal ?: return runs
        if (!sessionActive) return runs
        val recorded = s.runs.map { it.run }.toSet()
        val live = key == "running" && current !in recorded
        if (live) {
            runs.put(
                JSONObject()
                    .put("n", current)
                    .put("trainee", s.career?.trainee ?: JSONObject.NULL)
                    .put("scenario", s.career?.scenario ?: JSONObject.NULL)
                    .put("state", "running"),
            )
        }
        val firstUpcoming = if (live || current in recorded) current + 1 else current
        for (n in firstUpcoming..total) {
            if (n in recorded) continue
            runs.put(JSONObject().put("n", n).put("trainee", JSONObject.NULL).put("scenario", JSONObject.NULL).put("state", if (n == firstUpcoming) "next" else "waiting"))
        }
        return runs
    }

    private fun careerJson(c: Career): JSONObject {
        fun stamp(
            at: Long?,
            build: JSONObject.() -> Unit,
        ): Any = if (at == null) JSONObject.NULL else JSONObject().apply(build).put("at", at)
        val stats = c.stats
        return JSONObject()
            .put("trainee", c.trainee ?: JSONObject.NULL)
            .put("scenario", c.scenario ?: JSONObject.NULL)
            .put("action", if (c.actionKind == null) JSONObject.NULL else stamp(c.actionAt) { put("kind", c.actionKind).put("detail", c.actionDetail ?: JSONObject.NULL) })
            .put("date", if (c.turn == null) JSONObject.NULL else stamp(c.dateAt) { put("year", c.year ?: JSONObject.NULL).put("label", c.dateLabel ?: JSONObject.NULL).put("turn", c.turn) })
            .put("course", courseJson(c.scenario) ?: JSONObject.NULL)
            .put("goal", if (c.goalDueTurn == null) JSONObject.NULL else stamp(c.goalAt) { put("name", c.goalName ?: JSONObject.NULL).put("dueTurn", c.goalDueTurn) })
            .put(
                "stats",
                if (stats == null) {
                    JSONObject.NULL
                } else {
                    stamp(c.statsAt) {
                        listOf("speed", "stamina", "power", "guts", "wit").forEachIndexed { i, name -> put(name, stats.getOrNull(i) ?: JSONObject.NULL) }
                    }
                },
            ).put("energy", if (c.energyPercent == null) JSONObject.NULL else stamp(c.energyAt) { put("percent", c.energyPercent) })
            .put("mood", if (c.mood == null) JSONObject.NULL else stamp(c.moodAt) { put("level", c.mood) })
            .put("racesRun", c.racesRun)
    }

    /**
     * The one STATUS builder. Only the keys below ever leave the device: no log text, no queue event
     * `message`, no settings, no access code, no file paths and no TP amount (restores are counted by kind).
     *
     * Key: while a session runs, the latest event's key (`running` until a queue event says otherwise);
     * before the overlay tap, `armed`; afterwards, the queue's terminal key or `notRunning`.
     */
    fun statusJson(
        s: Snapshot,
        now: Long,
        sessionActive: Boolean,
        armed: Boolean,
        lastProgressAt: Long?,
        tally: Tally,
        stopAfterCareerRequested: Boolean = false,
    ): JSONObject {
        val key =
            when {
                sessionActive -> s.statusKey ?: "running"
                armed -> "armed"
                s.statusKey in TERMINAL_KEYS -> s.statusKey!!
                else -> "notRunning"
            }
        val terminal = !sessionActive && !armed
        val words = s.endWords.takeIf { terminal }
        return JSONObject()
            .put("type", "status")
            .put("v", 1)
            .put("sentAt", now)
            .put("sessionActive", sessionActive)
            .put("statusKey", key)
            .put("statusAt", if (key == s.statusKey) s.statusAt ?: JSONObject.NULL else JSONObject.NULL)
            .put("run", if (s.runCurrent != null && s.runTotal != null) JSONObject().put("current", s.runCurrent).put("total", s.runTotal) else JSONObject.NULL)
            .put("career", if (sessionActive && s.career != null) careerJson(s.career) else JSONObject.NULL)
            .put("lastProgressAt", if (sessionActive && lastProgressAt != null) lastProgressAt else JSONObject.NULL)
            .put("runs", if (armed && !sessionActive) JSONArray() else runsJson(s, key, sessionActive))
            .put(
                "session",
                if (s.sessionStartedAt == null) {
                    JSONObject.NULL
                } else {
                    JSONObject()
                        .put("startedAt", s.sessionStartedAt)
                        .put("stopAfterCareer", JSONObject().put("requested", stopAfterCareerRequested).put("offered", stopAfterCareerOffered(s, sessionActive)))
                        .put("tpRestores", JSONObject().put("items", tally.tpItems).put("carats", tally.tpCarats))
                        .put(
                            "recoveries",
                            JSONObject()
                                .put("total", tally.recoveriesTotal)
                                .put("accessibility", tally.accessibility)
                                .put("relaunches", tally.relaunches)
                                .put("lobby", tally.lobby)
                                .put("connection", tally.connection),
                        )
                },
            ).put("words", if (words == null) JSONObject.NULL else JSONObject().put("title", words.title).put("reason", words.reason).put("nextAction", words.nextAction ?: JSONObject.NULL))
    }
}
