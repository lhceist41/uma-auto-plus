package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.types.DateMonth
import com.steve1316.uma_android_automation.types.DatePhase
import com.steve1316.uma_android_automation.types.DateYear
import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.Mood
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.types.Trainee
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for the `decision_trace` record and for the sink hook that carries it out of
 * [DecisionTracer].
 *
 * The invariants worth pinning are the ones a later consumer would silently misread if they broke:
 * the schema name and version are always present; optional data that was not available at decision
 * time is OMITTED rather than filled in (an unread turn number must not appear as turn 1, the trap
 * the career outcome corpus already hit); candidate scores appear only where the decision code
 * handed them over; and the read flags travel with the values so an unobserved stat cannot be
 * mistaken for a fresh observation.
 *
 * The failure-isolation tests matter most. Tracing is observability: a sink that throws must not
 * change what the turn selected, must not stop the turn, and must not re-open an emitted block.
 */
@DisplayName("DecisionTrace Tests")
class DecisionTraceTest {
    private fun date(observed: Boolean = true): GameDate {
        val date = GameDate(year = DateYear.CLASSIC, month = DateMonth.JANUARY, phase = DatePhase.EARLY)
        if (observed) date.updateDay(date.day)
        return date
    }

    private fun trainee(): Trainee =
        Trainee().apply {
            name = "Biwa Hayahide"
            energy = 62
            mood = Mood.GOOD
            skillPoints = 340
            fans = 12000
            setTraineeStats(speed = 412, stamina = 300, power = 288, guts = 190, wit = 260)
            bHasUpdatedStats = true
            bHasUpdatedSkillPoints = true
            bHasUpdatedAptitudes = true
        }

    /** A tracer holding one representative turn: a TRAIN pick with a rejected alternative and a training contest. */
    private fun representativeTurn(observedDate: Boolean = true): DecisionTracer {
        val tracer = DecisionTracer()
        tracer.startTurn(
            date = date(observedDate),
            trainee = trainee(),
            settings = DecisionTracer.SettingsSnapshot().add("Mood Floor", Mood.GOOD),
        )
        tracer.recordRaceEligibility(eligible = false, reason = "not eligible for an extra race this turn")
        tracer.recordActionChoice(
            chosen = MainScreenAction.TRAIN,
            reason = "default action: no race required",
            rejected = listOf(DecisionTracer.RejectedAlternative("RECOVER_MOOD", "mood GOOD at/above floor GOOD")),
        )
        tracer.recordTrainingSelection(
            selected = StatName.SPEED,
            source = SelectionSource.ANALYSIS,
            reason = "won analysis with score 41.50",
            runnerUps =
                listOf(
                    DecisionTracer.TrainingRunnerUp(stat = StatName.WIT, rejected = false, reason = "outscored", score = 12.25, failureChance = 3),
                    DecisionTracer.TrainingRunnerUp(stat = StatName.GUTS, rejected = true, reason = "excluded (hard penalty)", score = null),
                ),
            pickedFailureChance = 8,
            pickedStatGains = mapOf(StatName.SPEED to 11, StatName.POWER to 2),
        )
        return tracer
    }

    private fun record(tracer: DecisionTracer, timestamp: Long = 1784213172197L): JSONObject =
        DecisionTrace.buildRecord(
            timestamp = timestamp,
            evidence = tracer.turnEvidence(),
            app = "1.4.0",
            fp = "1e681a57e1",
            scenario = "Trackblazer",
            trainee = "Biwa Hayahide",
            preset = "Biwa Hayahide",
            careerToken = "Biwa Hayahide|Trackblazer|run2|3f9a1c22",
            queueRun = 2,
        )

    @Test
    @DisplayName("a representative turn produces a complete, parseable record")
    fun `representative turn serializes`() {
        val json = record(representativeTurn())

        assertEquals(DecisionTrace.SCHEMA, json.getString("type"))
        assertEquals(DecisionTrace.SCHEMA_VERSION, json.getInt("v"))
        assertEquals(1784213172197L, json.getLong("ts"))
        assertEquals("Trackblazer", json.getString("scenario"))
        assertEquals("Biwa Hayahide|Trackblazer|run2|3f9a1c22", json.getString("careerToken"))
        assertEquals(2, json.getInt("queueRun"))
        assertEquals("CLASSIC", json.getString("year"))

        val state = json.getJSONObject("state")
        assertEquals(62, state.getInt("energy"))
        assertEquals("GOOD", state.getString("mood"))
        assertEquals(412, state.getInt("spd"))
        assertEquals(260, state.getInt("wit"))
        assertEquals(340, state.getInt("skillPts"))

        // The turn's committed action, and the training the analyzer picked inside it.
        val selected = json.getJSONObject("selected")
        assertEquals("TRAIN", selected.getString("action"))
        assertEquals("action_choice", selected.getString("source"))
        assertEquals("SPEED", selected.getString("training"))
        assertEquals("ANALYSIS", selected.getString("trainingSource"))

        assertFalse(json.getJSONObject("raceEligibility").getBoolean("eligible"))
        assertEquals("GOOD", json.getJSONObject("settings").getString("Mood Floor"))

        // The whole record survives a JSONL round trip unchanged.
        assertEquals(json.toString(), JSONObject(json.toString()).toString())
    }

    @Test
    @DisplayName("candidates carry both the action cascade and the training contest")
    fun `candidates serialize when available`() {
        val candidates = record(representativeTurn()).getJSONArray("candidates")
        val byId = (0 until candidates.length()).associate { candidates.getJSONObject(it).getString("id") to candidates.getJSONObject(it) }

        // The chosen action, its one rejected alternative, the picked training, and two runner-ups.
        assertEquals(5, candidates.length())
        assertTrue(byId.getValue("TRAIN").getBoolean("selected"))
        assertEquals("action", byId.getValue("TRAIN").getString("type"))
        assertFalse(byId.getValue("RECOVER_MOOD").getBoolean("selected"))
        assertTrue(byId.getValue("RECOVER_MOOD").getBoolean("rejected"))

        val speed = byId.getValue("SPEED")
        assertEquals("training", speed.getString("type"))
        assertTrue(speed.getBoolean("selected"))
        assertEquals(8, speed.getInt("failChance"))
        assertEquals(11, speed.getJSONObject("gains").getInt("spd"))
        // Only the stats the caller supplied are written; the rest are absent, not zero.
        assertFalse(speed.getJSONObject("gains").has("sta"))

        assertEquals(12.25, byId.getValue("WIT").getDouble("score"))

        // A hard-excluded training keeps its reason but carries no score: the tracer never had a
        // real ranking for it, and a fabricated one would rank it against the others.
        val guts = byId.getValue("GUTS")
        assertFalse(guts.has("score"))
        assertEquals("excluded (hard penalty)", guts.getString("reason"))
    }

    @Test
    @DisplayName("only the final training selection is serialized when a turn records two (Trackblazer irregular training)")
    fun `irregular training serializes only the final contest`() {
        // Trackblazer Irregular Training calls recommendTraining twice in one turn: the pre-screen
        // evaluation, then the executed fast path. Both record a non-null TrainingSelection. The
        // serialized contest must describe only the FINAL selection - otherwise the record carries two
        // selected:true training candidates, the contradiction the analyzer exits 3 on.
        val tracer = DecisionTracer()
        tracer.startTurn(date = date(), trainee = trainee())
        tracer.recordActionChoice(chosen = MainScreenAction.TRAIN, reason = "Trackblazer: irregular training hijack (STAMINA)")
        // First (provisional) evaluation: picks STAMINA with a WIT runner-up.
        tracer.recordTrainingSelection(
            selected = StatName.STAMINA,
            source = SelectionSource.ANALYSIS,
            reason = "provisional irregular evaluation",
            runnerUps = listOf(DecisionTracer.TrainingRunnerUp(stat = StatName.WIT, rejected = false, reason = "outscored", score = 9.0)),
            pickedFailureChance = 4,
            pickedStatGains = mapOf(StatName.STAMINA to 8),
        )
        // Final executed fast path: picks SPEED with a GUTS runner-up. Distinguishable by stat and score.
        tracer.recordTrainingSelection(
            selected = StatName.SPEED,
            source = SelectionSource.ANALYSIS,
            reason = "executed irregular fast path",
            runnerUps = listOf(DecisionTracer.TrainingRunnerUp(stat = StatName.GUTS, rejected = false, reason = "outscored", score = 15.5)),
            pickedFailureChance = 6,
            pickedStatGains = mapOf(StatName.SPEED to 12),
        )

        val json = DecisionTrace.buildRecord(timestamp = 1L, evidence = tracer.turnEvidence(), scenario = "Trackblazer", trainee = "Biwa Hayahide")
        val candidates = json.getJSONArray("candidates")
        val training = (0 until candidates.length()).map { candidates.getJSONObject(it) }.filter { it.getString("type") == "training" }

        // Exactly one selected training candidate, and it is the FINAL pick (SPEED), not the provisional (STAMINA).
        val selectedTraining = training.filter { it.getBoolean("selected") }
        assertEquals(1, selectedTraining.size, training.toString())
        assertEquals("SPEED", selectedTraining.single().getString("id"))
        assertEquals(6, selectedTraining.single().getInt("failChance"))

        // Only the final contest is serialized: SPEED (pick) + GUTS (runner-up). The provisional contest -
        // STAMINA (its pick) and WIT (its runner-up) - is absent, so no phantom second selected contest.
        val ids = training.map { it.getString("id") }.toSet()
        assertEquals(setOf("SPEED", "GUTS"), ids)
        assertFalse(ids.contains("STAMINA"), "provisional pick STAMINA must not appear in the serialized contest: $ids")
        assertFalse(ids.contains("WIT"), "provisional runner-up WIT must not appear in the serialized contest: $ids")

        // selected.training agrees with the single selected candidate, and the record round-trips.
        assertEquals("SPEED", json.getJSONObject("selected").getString("training"))
        assertEquals(json.toString(), JSONObject(json.toString()).toString())
    }

    @Test
    @DisplayName("read flags travel with the observed state")
    fun `observation flags serialize`() {
        val observed = record(representativeTurn()).getJSONObject("observation")
        assertTrue(observed.getBoolean("turnObserved"))
        assertTrue(observed.getBoolean("statsObserved"))
        assertTrue(observed.getBoolean("skillPointsObserved"))
        assertTrue(observed.getBoolean("aptitudesObserved"))

        // A trainee whose stats were never read reports so explicitly rather than passing the
        // constructor defaults off as a reading.
        val unread = DecisionTracer()
        unread.startTurn(date = date(observed = false), trainee = Trainee())
        val unreadRecord = record(unread).getJSONObject("observation")
        assertFalse(unreadRecord.getBoolean("turnObserved"))
        assertFalse(unreadRecord.getBoolean("statsObserved"))
    }

    @Test
    @DisplayName("unavailable optional data is omitted rather than fabricated")
    fun `unavailable data is omitted`() {
        // No turn ever opened, and no identity supplied.
        val empty = DecisionTrace.buildRecord(timestamp = 1L, evidence = DecisionTracer().turnEvidence())

        assertEquals(DecisionTrace.SCHEMA, empty.getString("type"))
        assertEquals(DecisionTrace.SCHEMA_VERSION, empty.getInt("v"))
        listOf("app", "fp", "scenario", "trainee", "preset", "careerToken", "queueRun", "turn", "year", "state", "candidates", "settings", "notes")
            .forEach { field -> assertFalse(empty.has(field), "expected $field to be absent, got ${empty.opt(field)}") }
        // The selection object is present but empty: the turn committed to nothing.
        assertEquals(0, empty.getJSONObject("selected").length())

        // An unread date must not report a turn number. GameDate holds a constructed default of 1
        // until something reads the screen, and writing that as a real turn already produced
        // phantom turn-1 rows in the career outcome corpus.
        val unreadTurn = record(representativeTurn(observedDate = false))
        assertFalse(unreadTurn.has("turn"))
        assertEquals("CLASSIC", unreadTurn.getString("year"))

        // Blank identity is treated the same as missing identity.
        val blankIdentity = DecisionTrace.buildRecord(timestamp = 1L, evidence = DecisionTracer().turnEvidence(), scenario = "", trainee = "  ")
        assertFalse(blankIdentity.has("scenario"))
        assertFalse(blankIdentity.has("trainee"))
    }

    @Test
    @DisplayName("special characters cannot break the JSONL line")
    fun `special characters stay escaped`() {
        val tracer = DecisionTracer()
        tracer.startTurn(date = date(), trainee = Trainee().apply { name = "Quote\" Back\\slash" })
        tracer.recordActionChoice(
            chosen = MainScreenAction.REST,
            reason = "line one\nline two\ttabbed bell",
            rejected = listOf(DecisionTracer.RejectedAlternative("TRAIN", "brace } bracket ]  separator")),
        )
        tracer.recordNote("trailing backslash \\")

        val line =
            DecisionTrace
                .buildRecord(timestamp = 1L, evidence = tracer.turnEvidence(), trainee = "Quote\" Back\\slash", scenario = "Scenario\nBreak")
                .toString()

        // One JSONL line means exactly one line: no raw newline, carriage return or control byte survives.
        assertFalse(line.contains('\n'), line)
        assertFalse(line.contains('\r'), line)
        assertFalse(line.any { it.code < 0x20 }, line)

        val reparsed = JSONObject(line)
        assertEquals("Quote\" Back\\slash", reparsed.getString("trainee"))
        assertEquals("Scenario\nBreak", reparsed.getString("scenario"))
        assertEquals("line one\nline two\ttabbed bell", reparsed.getJSONObject("selected").getString("reason"))
        assertEquals("trailing backslash \\", reparsed.getJSONArray("notes").getString(0))
    }

    @Test
    @DisplayName("multiple records stay independently parseable")
    fun `records are independently parseable`() {
        val lines =
            listOf(
                record(representativeTurn(), timestamp = 1L),
                DecisionTrace.buildRecord(timestamp = 2L, evidence = DecisionTracer().turnEvidence()),
                record(representativeTurn(observedDate = false), timestamp = 3L),
            ).joinToString("\n") { it.toString() }

        val parsed = lines.split("\n").map { JSONObject(it) }
        assertEquals(3, parsed.size)
        assertEquals(listOf(1L, 2L, 3L), parsed.map { it.getLong("ts") })
        parsed.forEach { assertEquals(DecisionTrace.SCHEMA, it.getString("type")) }
        // The middle record carries no turn, and that does not affect its neighbours.
        assertTrue(parsed[0].has("turn"))
        assertFalse(parsed[1].has("turn"))
    }

    @Test
    @DisplayName("the sink receives one evidence copy per emitted turn")
    fun `sink fires once per turn`() {
        val received = mutableListOf<TurnEvidence>()
        val tracer = representativeTurn()
        tracer.traceSink = { received.add(it) }

        MessageLog.clearLog()
        tracer.emit()
        tracer.emit()

        assertEquals(1, received.size)
        assertEquals(1, MessageLog.getMessageLogCopy().size)
        assertEquals(3, received.single().events.size)
        assertNotNull(received.single().state)

        // The copy is detached: opening the next turn does not rewrite what the sink was handed.
        tracer.startTurn(date = date(), trainee = Trainee())
        assertEquals(3, received.single().events.size)
    }

    @Test
    @DisplayName("a throwing sink is non-fatal and leaves the decision untouched")
    fun `sink failure is isolated`() {
        // The same turn, recorded three ways: no sink, a working sink, and a sink that always throws.
        val noSink = representativeTurn()
        val working = representativeTurn().apply { traceSink = { } }
        val failing = representativeTurn().apply { traceSink = { error("disk is gone") } }

        MessageLog.clearLog()
        noSink.emit()
        working.emit()
        // Must not propagate: emit() runs after the turn's action, and a telemetry fault is not a run fault.
        failing.emit()

        // The selection each tracer holds is byte-identical across all three.
        val selections = listOf(noSink, working, failing).map { it.lastTrainingSelection() }
        assertTrue(selections.all { it?.selected == StatName.SPEED && it.source == SelectionSource.ANALYSIS })
        assertEquals(1, selections.map { it?.runnerUps?.size }.distinct().size)

        // And so is the record built from each one's evidence.
        val serialized = listOf(noSink, working, failing).map { record(it).toString() }
        assertEquals(1, serialized.distinct().size, serialized.toString())

        // Three Decision Report blocks plus exactly one bounded failure warning.
        val log = MessageLog.getMessageLogCopy()
        assertEquals(4, log.size, log.toString())
        assertEquals(1, log.count { it.contains("Failed to record the decision trace") }, log.toString())

        // A second failure on the same tracer stays silent, and the emitted block is still not repeated.
        failing.startTurn(date = date(), trainee = trainee())
        MessageLog.clearLog()
        failing.emit()
        failing.emit()
        val secondTurn = MessageLog.getMessageLogCopy()
        assertEquals(1, secondTurn.size, secondTurn.toString())
        assertFalse(secondTurn.single().contains("Failed to record the decision trace"), secondTurn.toString())
    }
}
