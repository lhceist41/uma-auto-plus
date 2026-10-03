package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.CareerResult
import com.steve1316.uma_android_automation.KeptSpark
import com.steve1316.uma_android_automation.QueueReport
import com.steve1316.uma_android_automation.RunRecord
import com.steve1316.uma_android_automation.SessionEnd
import com.steve1316.uma_android_automation.SessionTally
import com.steve1316.uma_android_automation.bot.GrandConcertScenario
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The dashboard's STATUS: one builder, whitelisted keys only, every live field stamped with when it
 * was observed, and a key that follows the latest event. Built from plain published values, so these
 * tests drive [StatusBoard] the way the queue and bot threads do and read the JSON the page receives.
 */
@DisplayName("Dashboard STATUS builder")
class StatusBoardTest {
    private val tally = StatusBoard.Tally(tpItems = 2, tpCarats = 1, accessibility = 3, relaunches = 1, lobby = 1, connection = 0, recoveriesTotal = 5)

    @BeforeEach
    fun clean() = StatusBoard.reset()

    @AfterEach
    fun cleanAfter() = StatusBoard.reset()

    private fun status(
        sessionActive: Boolean = true,
        armed: Boolean = false,
        lastProgressAt: Long? = 1_000L,
        now: Long = 5_000L,
    ) = StatusBoard.statusJson(StatusBoard.snapshot(), now, sessionActive, armed, lastProgressAt, tally)

    private fun turn(
        day: Int = 2,
        trainee: String? = "El Condor Pasa",
        scenario: String = "URA Finale",
        stats: List<Int> = listOf(197, 104, 122, 138, 96),
        now: Long = 3_000L,
    ) {
        val (year, label) = StatusBoard.dateLabels("JUNIOR YEAR", "LATE", "JANUARY", day)
        StatusBoard.careerTurn(trainee, scenario, year, label, day, stats, 74, "GOOD", now)
    }

    private fun record(
        n: Int,
        code: String,
        trainee: String? = null,
        name: String? = null,
    ) = RunRecord(n, 100L * n, 100L * n + 50, code, trainee, "URA Finale", null, 75, traineeName = name)

    private fun keysOf(o: JSONObject) = o.keys().asSequence().toSet()

    @Test
    fun `only whitelisted keys leave, with no queue message, log text, TP total or code`() {
        StatusBoard.reset(900L)
        StatusBoard.queueProgress(1, 3, "completed", JSONObject().put("message", "raw exception text C:/secret/path").put("resultCode", "X").toString(), 1_500L)
        turn()
        StatusBoard.action("training", "SPEED", 3_100L)
        StatusBoard.goal(2, 10, 3_050L)
        StatusBoard.runRecorded(record(1, "TASK_RESULT_COMPLETE", "El_Condor_Pasa"))
        turn(day = 3)
        val s = status()
        val text = s.toString()

        assertEquals(
            setOf("type", "v", "sentAt", "sessionActive", "statusKey", "statusAt", "run", "career", "lastProgressAt", "runs", "session", "words"),
            keysOf(s),
        )
        assertEquals(
            setOf("trainee", "scenario", "action", "date", "course", "goal", "stats", "energy", "mood", "racesRun"),
            keysOf(s.getJSONObject("career")),
        )
        assertEquals(setOf("startedAt", "tpRestores", "recoveries", "stopAfterCareer"), keysOf(s.getJSONObject("session")))
        assertEquals(setOf("requested", "offered"), keysOf(s.getJSONObject("session").getJSONObject("stopAfterCareer")))
        assertEquals(setOf("items", "carats"), keysOf(s.getJSONObject("session").getJSONObject("tpRestores")))
        assertEquals(setOf("total", "accessibility", "relaunches", "lobby", "connection"), keysOf(s.getJSONObject("session").getJSONObject("recoveries")))
        for (leak in listOf("raw exception text", "secret", "message", "resultCode", "tpSpent", "tp_spent", "resultCode")) {
            assertFalse(text.contains(leak), "STATUS must not carry $leak")
        }
        assertEquals(
            setOf("n", "trainee", "scenario", "state", "outcome", "rank", "estScore", "fans", "finale", "finalStats", "sparks", "sparksNote", "startedAt", "endedAt", "words"),
            keysOf(s.getJSONArray("runs").getJSONObject(0)),
        )
    }

    @Test
    fun `a run marks its last-known final stats, and only then carries the key`() {
        StatusBoard.reset(900L)
        val marked = record(1, "TASK_RESULT_COMPLETE", "El_Condor_Pasa").copy(result = CareerResult("UG4", 18432, 221054, 2, 3, listOf(1248, 500, 1213, 395, 438), listOf("power")))
        StatusBoard.runRecorded(marked)
        StatusBoard.runRecorded(record(2, "TASK_RESULT_COMPLETE", "El_Condor_Pasa").copy(result = CareerResult("UG4", 18432, 221054, 2, 3, listOf(1248, 500, 816, 395, 438))))
        StatusBoard.queueProgress(2, 3, "completed", "{}", 1_000L)
        val runs = status().getJSONArray("runs")
        val lastKnown = runs.getJSONObject(0).getJSONArray("lastKnownStats")
        assertEquals(listOf("power"), (0 until lastKnown.length()).map { lastKnown.getString(it) })
        assertEquals(1213, runs.getJSONObject(0).getJSONObject("finalStats").getInt("power"), "the value stays, marked")
        assertFalse(runs.getJSONObject(1).has("lastKnownStats"), "a fully read career adds no key")
    }

    @Test
    fun `a finished run carries its result and, once read, its kept sparks, with the contract's names and types`() {
        StatusBoard.reset(900L)
        val result = CareerResult("UG4", 18432, 221054, 2, 3, listOf(1248, null, 816, 395, 438))
        val first = record(1, "TASK_RESULT_COMPLETE", "El_Condor_Pasa").copy(result = result)
        StatusBoard.runRecorded(first)
        StatusBoard.runRecorded(record(2, "TASK_RESULT_UNHANDLED_EXCEPTION"))
        StatusBoard.queueProgress(2, 3, "completed", "{}", 1_000L)

        var runs = status().getJSONArray("runs")
        val done = runs.getJSONObject(0)
        assertEquals("UG4", done.getString("rank"), "the producer's own rank label")
        assertEquals(18432, done.getInt("estScore"))
        assertEquals(221054, done.getInt("fans"))
        assertEquals(2, done.getJSONObject("finale").getInt("won"))
        assertEquals(3, done.getJSONObject("finale").getInt("of"))
        val finalStats = done.getJSONObject("finalStats")
        assertEquals(setOf("speed", "stamina", "power", "guts", "wit"), keysOf(finalStats))
        assertEquals(1248, finalStats.getInt("speed"))
        assertTrue(finalStats.isNull("stamina"), "an unread final stat is null")
        assertTrue(done.isNull("sparks") && done.isNull("sparksNote"), "sparks are not read yet")
        val errored = runs.getJSONObject(1)
        for (key in listOf("rank", "estScore", "fans", "finale", "finalStats", "sparks", "sparksNote")) {
            assertTrue(errored.has(key) && errored.isNull(key), "a run with no result sends $key as null")
        }

        StatusBoard.runUpdated(first.copy(sparks = listOf(KeptSpark("Power", "stat", 1), KeptSpark("Kikuka Sho", "other", 3)), sparksNote = "rerolled once, kept the original sparks"))
        runs = status().getJSONArray("runs")
        assertEquals(listOf("done", "errored", "next"), (0 until runs.length()).map { runs.getJSONObject(it).getString("state") }, "the update replaces the run, never adds one")
        val sparks = runs.getJSONObject(0).getJSONArray("sparks")
        assertEquals(2, sparks.length())
        assertEquals(setOf("name", "type", "stars"), keysOf(sparks.getJSONObject(0)))
        assertEquals("Kikuka Sho", sparks.getJSONObject(1).getString("name"))
        assertEquals("other", sparks.getJSONObject(1).getString("type"))
        assertEquals(3, sparks.getJSONObject(1).getInt("stars"))
        assertEquals("rerolled once, kept the original sparks", runs.getJSONObject(0).getString("sparksNote"))
        assertEquals("UG4", runs.getJSONObject(0).getString("rank"), "the result stays")

        StatusBoard.runUpdated(record(7, "TASK_RESULT_COMPLETE"))
        assertEquals(3, status().getJSONArray("runs").length(), "an update for a run never recorded is ignored")
    }

    @Test
    fun `every live career field carries when it was observed`() {
        StatusBoard.reset(900L)
        turn(now = 3_000L)
        StatusBoard.goal(2, 10, 3_050L)
        StatusBoard.action("training", "SPEED", 3_100L)
        val c = status().getJSONObject("career")
        assertEquals(3_000L, c.getJSONObject("date").getLong("at"))
        assertEquals(3_000L, c.getJSONObject("stats").getLong("at"))
        assertEquals(3_000L, c.getJSONObject("energy").getLong("at"))
        assertEquals(3_000L, c.getJSONObject("mood").getLong("at"))
        assertEquals(3_050L, c.getJSONObject("goal").getLong("at"))
        assertEquals(3_100L, c.getJSONObject("action").getLong("at"))
        assertEquals(1_000L, status().getLong("lastProgressAt"))
        assertEquals(5_000L, status(now = 5_000L).getLong("sentAt"))

        assertEquals(74, c.getJSONObject("energy").getInt("percent"))
        assertEquals("GOOD", c.getJSONObject("mood").getString("level"))
        assertEquals(12, c.getJSONObject("goal").getInt("dueTurn"), "the turn read plus the turns left then")
        assertTrue(c.getJSONObject("goal").isNull("name"), "no goal name is read for the dashboard")
        assertEquals("Junior Year", c.getJSONObject("date").getString("year"))
        assertEquals("Late January", c.getJSONObject("date").getString("label"))
        assertEquals("SPEED", c.getJSONObject("action").getString("detail"))
    }

    @Test
    fun `the key follows the latest event and statusAt moves only when the key changes`() {
        StatusBoard.reset(900L)
        assertEquals("running", status().getString("statusKey"), "a session with no queue event is running")
        assertTrue(status().isNull("statusAt"))

        StatusBoard.queueProgress(1, 3, "starting", "{}", 1_000L)
        assertEquals("starting", status().getString("statusKey"))
        assertEquals(1_000L, status().getLong("statusAt"))

        turn(now = 2_000L)
        assertEquals("running", status().getString("statusKey"), "a career turn replaces the queue's key")
        assertEquals(2_000L, status().getLong("statusAt"))
        turn(day = 3, now = 2_500L)
        assertEquals(2_000L, status().getLong("statusAt"), "the same key keeps its time")

        StatusBoard.queueProgress(1, 3, "completed", "{}", 3_000L)
        StatusBoard.queueProgress(1, 3, "navigating", "{}", 3_100L)
        assertEquals("navigating", status().getString("statusKey"), "a later queue event replaces running")
        assertEquals(3_100L, status().getLong("statusAt"))
        turn(day = 1, now = 4_000L)
        assertEquals("running", status().getString("statusKey"))
    }

    @Test
    fun `every queue key passes through while the session runs, and the page gets the rest from the session state`() {
        for (key in listOf("starting", "resuming", "navigating", "waiting", "retrying", "completed", "queueComplete", "queueStopped", "queueHalted", "queueFailed", "someFutureKey")) {
            StatusBoard.reset(900L)
            StatusBoard.queueProgress(2, 3, key, "{}", 1_000L)
            assertEquals(key, status().getString("statusKey"), key)
        }

        StatusBoard.reset()
        assertEquals("armed", status(sessionActive = false, armed = true).getString("statusKey"))
        assertEquals("notRunning", status(sessionActive = false).getString("statusKey"))

        StatusBoard.reset(900L)
        StatusBoard.queueProgress(3, 3, "queueComplete", "{}", 1_000L)
        assertEquals("queueComplete", status(sessionActive = false).getString("statusKey"), "a finished queue keeps its terminal key")
        StatusBoard.queueProgress(2, 3, "navigating", "{}", 1_100L)
        assertEquals("notRunning", status(sessionActive = false).getString("statusKey"), "a session that ended between runs is not running")
    }

    @Test
    fun `words come from the report only after the session ended, and runs carry their own reason`() {
        StatusBoard.reset(900L)
        StatusBoard.runRecorded(record(1, "TASK_RESULT_COMPLETE"))
        StatusBoard.runRecorded(record(2, "TASK_RESULT_TIMED_OUT"))
        StatusBoard.runRecorded(record(3, "TASK_RESULT_MANUALLY_STOPPED"))
        StatusBoard.queueProgress(3, 3, "queueStopped", "{}", 1_000L)
        StatusBoard.sessionEnded(report(SessionEnd.entries.first()))

        assertTrue(status(sessionActive = true).isNull("words"), "no words while the session runs")
        assertTrue(status(sessionActive = false, armed = true).isNull("words"), "no old words once armed again")
        val words = status(sessionActive = false).getJSONObject("words")
        assertEquals(setOf("title", "reason", "nextAction"), keysOf(words))
        assertTrue(words.getString("reason").isNotEmpty())

        val runs = status(sessionActive = false).getJSONArray("runs")
        assertEquals(listOf("done", "errored", "stopped"), (0 until runs.length()).map { runs.getJSONObject(it).getString("state") })
        assertTrue(runs.getJSONObject(0).isNull("words"), "a finished career has no reason")
        assertEquals("The run timed out.", runs.getJSONObject(1).getJSONObject("words").getString("reason"))
        assertEquals("The run was stopped.", runs.getJSONObject(2).getJSONObject("words").getString("reason"))
    }

    @Test
    fun `display names, null fields and the upcoming runs`() {
        StatusBoard.reset(900L)
        StatusBoard.runRecorded(record(1, "TASK_RESULT_COMPLETE", "El_Condor_Pasa"))
        StatusBoard.runRecorded(record(2, "TASK_RESULT_COMPLETE", "Special_Week", "Special Week (Original)"))
        StatusBoard.queueProgress(3, 5, "starting", "{}", 1_000L)
        turn(stats = listOf(197, -1, -1, 138, 96), scenario = "Some Future Scenario")
        val s = status()
        val runs = s.getJSONArray("runs")
        assertEquals("El Condor Pasa", runs.getJSONObject(0).getString("trainee"), "the identifier's spaces come back")
        assertEquals("Special Week (Original)", runs.getJSONObject(1).getString("trainee"), "the shown name wins")
        assertEquals(listOf("done", "done", "running", "next", "waiting"), (0 until runs.length()).map { runs.getJSONObject(it).getString("state") })
        assertEquals((1..5).toList(), (0 until runs.length()).map { runs.getJSONObject(it).getInt("n") })
        assertTrue(runs.getJSONObject(3).isNull("trainee"), "an upcoming run's trainee is not known")

        val c = s.getJSONObject("career")
        assertTrue(c.isNull("course"), "no course for an unverified scenario")
        val stats = c.getJSONObject("stats")
        assertTrue(stats.isNull("stamina") && stats.isNull("power"), "unread stats are null, never -1")
        assertEquals(197, stats.getInt("speed"))
        assertTrue(c.isNull("goal") && c.isNull("action"), "never read: null")

        StatusBoard.reset(900L)
        turn(trainee = null, stats = listOf(-1, -1, -1, -1, -1))
        val empty = status().getJSONObject("career")
        assertTrue(empty.isNull("trainee") && empty.isNull("stats"))
        assertTrue(status(sessionActive = false).isNull("career"), "no career once the session ended")
        assertTrue(status(sessionActive = false).isNull("lastProgressAt"))
    }

    private fun finaleSegment(scenario: String): JSONObject {
        val course = StatusBoard.courseJson(scenario)
        assertNotNull(course, "course for $scenario")
        course!!
        assertEquals(75, course.getInt("finalTurn"))
        val segments: JSONArray = course.getJSONArray("segments")
        assertEquals(listOf(1 to 24, 25 to 48, 49 to 72, 73 to 75), (0 until segments.length()).map { segments.getJSONObject(it).getInt("from") to segments.getJSONObject(it).getInt("to") })
        assertEquals(listOf("Junior Year", "Classic Year", "Senior Year"), (0 until 3).map { segments.getJSONObject(it).getString("label") })
        assertTrue((0 until 3).none { segments.getJSONObject(it).has("finale") })
        return segments.getJSONObject(3).also { assertTrue(it.getBoolean("finale")) }
    }

    private fun finaleDates(scenario: String?) = listOf(73, 74, 75).map { StatusBoard.dateLabels("SENIOR YEAR", "LATE", "DECEMBER", it, scenario) }

    @Test
    fun `URA Finale's course ends in the URA Finale`() {
        assertEquals("URA Finale", finaleSegment("URA Finale").getString("label"))
        assertEquals(listOf(null to "Finale Qualifier", null to "Finale Semi-Final", null to "Finale Finals"), finaleDates("URA Finale"))
        assertEquals(null to "Finale Finals", StatusBoard.dateLabels("SENIOR YEAR", "LATE", "DECEMBER", 75))
    }

    @Test
    fun `Unity Cup's course ends in the URA Finale`() {
        assertEquals("URA Finale", finaleSegment("Unity Cup").getString("label"))
        assertEquals(listOf(null to "Finale Qualifier", null to "Finale Semi-Final", null to "Finale Finals"), finaleDates("Unity Cup"))
    }

    @Test
    fun `Grand Concert's course ends in the URA Finale`() {
        assertEquals("URA Finale", finaleSegment(GrandConcertScenario.KEY).getString("label"))
        assertEquals(listOf(null to "Finale Qualifier", null to "Finale Semi-Final", null to "Finale Finals"), finaleDates(GrandConcertScenario.KEY))
    }

    @Test
    fun `Trackblazer's course ends in the Twinkle Star Climax`() {
        assertEquals("Twinkle Star Climax", finaleSegment("Trackblazer").getString("label"))
        assertEquals(listOf(null to "Climax Race 1", null to "Climax Race 2", null to "Climax Race 3"), finaleDates("Trackblazer"))
    }

    @Test
    fun `turns before the finale keep their calendar labels in every scenario`() {
        for (scenario in listOf("URA Finale", "Unity Cup", GrandConcertScenario.KEY, "Trackblazer")) {
            assertEquals("Senior Year" to "Late December", StatusBoard.dateLabels("SENIOR YEAR", "LATE", "DECEMBER", 72, scenario), scenario)
        }
    }

    @Test
    fun `no course is sent for a scenario without a career calendar`() {
        for (other in listOf("Daily Races", "Team Trials", "", null)) {
            assertEquals(null, StatusBoard.courseJson(other), "course for $other")
        }
        assertEquals("Senior Year" to "Late December", StatusBoard.dateLabels("SENIOR YEAR", "LATE", "DECEMBER", 73, "Daily Races"))
    }

    @Test
    fun `the dashboard counts the recoveries the Home card counts, and no others`() {
        val card = repoSource("src/lib/queueReportPresentation.ts").substringAfter("function recoveriesLine(").substringBefore("\nfunction ")
        val counted = Regex("count\\(recoveries\\.(\\w+)\\)").findAll(card).map { it.groupValues[1] }.toSet()
        val counters = SessionTally.recoveriesJson().keys().asSequence().toList()
        assertTrue(counted.isNotEmpty() && counters.containsAll(counted), "the card reads counters SessionTally reports: $counted")
        StatusBoard.reset(900L)
        try {
            for (key in counters) {
                SessionTally.reset()
                counter(key).set(1)
                val shown = LogStreamServer.statusJson().getJSONObject("session").getJSONObject("recoveries")
                val onCard = if (key in counted) 1 else 0
                assertEquals(onCard, shown.getInt("total"), "total with only $key")
                assertEquals(if (key.startsWith("accessibility")) onCard else 0, shown.getInt("accessibility"), "accessibility with only $key")
                assertEquals(if (key == "gameRelaunches") 1 else 0, shown.getInt("relaunches"), "relaunches with only $key")
                assertEquals(if (key == "lobbyReentries") 1 else 0, shown.getInt("lobby"), "lobby with only $key")
                assertEquals(if (key == "connectionHolds") 1 else 0, shown.getInt("connection"), "connection with only $key")
                val buckets = listOf("accessibility", "relaunches", "lobby", "connection").sumOf { shown.getInt(it) }
                assertEquals(shown.getInt("total"), buckets, "the shown parts add up to the total with only $key")
            }
        } finally {
            SessionTally.reset()
        }
    }

    private fun counter(key: String) =
        when (key) {
            "accessibilityRebinds" -> SessionTally.accessibilityRebinds
            "accessibilityRewrites" -> SessionTally.accessibilityRewrites
            "accessibilityRepairsRefused" -> SessionTally.accessibilityRepairsRefused
            "accessibilityRebindsWithoutChange" -> SessionTally.accessibilityRebindsWithoutChange
            "accessibilityStrongToggles" -> SessionTally.accessibilityStrongToggles
            "gameRelaunches" -> SessionTally.gameRelaunches
            "lobbyReentries" -> SessionTally.lobbyReentries
            "connectionHolds" -> SessionTally.connectionHolds
            else -> error("decide whether the Home card adds up $key, and whether the dashboard does")
        }

    @Test
    fun `the goal name shows only for a classified race, and stays while the deadline is unchanged`() {
        StatusBoard.reset(900L)
        turn()

        fun goalName() = status().getJSONObject("career").getJSONObject("goal").let { if (it.isNull("name")) null else it.getString("name") }
        StatusBoard.goal(10, 2, 3_000L)
        assertEquals(null, goalName(), "no name without a classified goal text")
        StatusBoard.goal(10, 2, 3_100L, name = "Satsuki Sho")
        assertEquals("Satsuki Sho", goalName())
        StatusBoard.goal(11, 1, 3_200L)
        assertEquals("Satsuki Sho", goalName(), "the same deadline keeps its name")
        StatusBoard.goal(12, 5, 3_300L)
        assertEquals(null, goalName(), "a new deadline has no name until it is classified")

        val produce = kotlinSource("bot/Campaign.kt").substringAfter("private fun produceGoalSnapshotIfDue(").substringBefore("\n    }\n")
        val classified = produce.indexOf("if (kind == GoalKind.RACE) {")
        val named = produce.indexOf("StatusBoard.goal(date.day, turnsRemaining, name = raceName)")
        assertTrue(named > classified && classified >= 0, "the name comes only from the classified race")
    }

    @Test
    fun `the skills, infirmary, shop and Trackblazer training screens publish what the bot is doing`() {
        val campaign = kotlinSource("bot/Campaign.kt")
        assertTrue(campaign.substringAfter("open fun handleSkillListScreen(").substringBefore("\n    }\n").contains("StatusBoard.action(\"skills\", null)"))
        val injury = campaign.substringAfter("open fun checkInjury(").substringBefore("\n    }\n").lines()
        val published = injury.indices.filter { injury[it].contains("StatusBoard.action(\"infirmary\", null)") }
        assertEquals(2, published.size, "the forced attempt and the ordinary heal")
        for (i in published) assertTrue(injury[i + 1].contains("if (ButtonInfirmary.click("), "published right before the tap")
        val trackblazer = kotlinSource("bot/campaigns/Trackblazer.kt")
        assertTrue(trackblazer.substringAfter("fun openShop(").substringBefore("\n    }\n").contains("StatusBoard.action(\"shop\", null)"))
        val fastPath = trackblazer.substringAfter("override fun executeAction(action: MainScreenAction").substringBefore("\n    }\n")
        assertTrue(fastPath.indexOf("StatusBoard.action(\"training\", null)") in 0 until fastPath.indexOf("handleTrackblazerTraining()"), "the fast path that skips the base action publishes its own")
    }

    @Test
    fun `Home reads the retained queue progress on mount and on every return to the app`() {
        assertEquals(2, Regex("refreshQueueProgress\\(\\)").findAll(repoSource("src/pages/Home/index.tsx")).count())
    }

    @Test
    fun `publishing never logs, locks or reads settings, and STATUS is built only by the server`() {
        val board = kotlinSource("utils/StatusBoard.kt").lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/") }.joinToString("\n")
        for (forbidden in listOf("MessageLog", "Log.", "synchronized", "@Synchronized", "SettingsHelper", "File(", "sendEvent", "EventBus", "getLastQueueReport", "QueueLedger")) {
            assertFalse(board.contains(forbidden), "StatusBoard must not use $forbidden")
        }
        val others =
            kotlinRoot()
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name !in setOf("StatusBoard.kt", "LogStreamServer.kt") && it.readText().contains("statusJson(") }
                .map { it.name }
                .toList()
        assertEquals(emptyList<String>(), others, "only the server builds STATUS")
    }

    private fun report(end: SessionEnd) =
        QueueReport(
            sessionId = "s",
            appVersion = "1.6.0",
            startedAt = 900L,
            endedAt = 2_000L,
            endedAtSource = "session",
            kind = end,
            queueEnabled = true,
            totalRuns = 3,
            startFromRun = 1,
            completedRuns = 1,
            runReached = 3,
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

    private fun kotlinSource(relative: String) = File(kotlinRoot(), relative).readText().replace("\r\n", "\n")

    private fun repoSource(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val file = File(dir, relative)
            if (file.isFile) return file.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${System.getProperty("user.dir")}")
    }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }

    @Test
    fun `stop after this career is offered for a career starting, playing, retried or ending, with a run after it`() {
        fun offered(key: String?, current: Int = 2, total: Int = 4, active: Boolean = true): Boolean {
            StatusBoard.reset(900L)
            if (key != null) StatusBoard.queueProgress(current, total, key, "{}", 1_000L)
            return StatusBoard.stopAfterCareerOffered(StatusBoard.snapshot(), active)
        }
        for (key in listOf("starting", "resuming", "retrying", "completed")) assertTrue(offered(key), key)
        StatusBoard.reset(900L)
        StatusBoard.queueProgress(2, 4, "starting", "{}", 1_000L)
        turn()
        assertTrue(StatusBoard.stopAfterCareerOffered(StatusBoard.snapshot(), true), "a career turn (running)")
        for (key in listOf("navigating", "waiting", "queueComplete", "stoppedAfterCareer", "someFutureKey")) assertFalse(offered(key), key)
        assertFalse(offered("starting", current = 4, total = 4), "the last run just finishes the queue")
        assertFalse(offered("starting", active = false), "no session")
        assertFalse(offered(null), "a single run sends no queue events")
    }

    @Test
    fun `STATUS carries the request as it stands and whether it is offered`() {
        StatusBoard.reset(900L)
        StatusBoard.queueProgress(2, 4, "starting", "{}", 1_000L)
        val requested = StatusBoard.statusJson(StatusBoard.snapshot(), 5_000L, true, false, null, tally, stopAfterCareerRequested = true)
        val stop = requested.getJSONObject("session").getJSONObject("stopAfterCareer")
        assertTrue(stop.getBoolean("requested") && stop.getBoolean("offered"))
        StatusBoard.queueProgress(2, 4, "navigating", "{}", 2_000L)
        val between = status().getJSONObject("session").getJSONObject("stopAfterCareer")
        assertFalse(between.getBoolean("requested") || between.getBoolean("offered"))
    }
}
