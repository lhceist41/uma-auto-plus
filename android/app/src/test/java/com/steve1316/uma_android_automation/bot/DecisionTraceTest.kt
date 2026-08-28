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

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // E1-1: additive training-candidate evidence enrichment (numRainbow, numSkillHints, trainingLevel,
    // spirit gauges, relationshipBars, performanceGains) on both the picked and runner-up candidates.

    private fun bar(
        fillPercent: Double,
        segments: Int,
        color: String,
        support: Boolean = false,
        trainer: String? = null,
    ): DecisionTracer.RelationshipBarEvidence =
        DecisionTracer.RelationshipBarEvidence(fillPercent = fillPercent, filledSegments = segments, dominantColor = color, isSupport = support, trainerName = trainer)

    private fun evidence(
        numRainbow: Int = 0,
        numSkillHints: Int = 0,
        trainingLevel: Int? = null,
        canFill: Int = 0,
        readyToBurst: Int = 0,
        bars: List<DecisionTracer.RelationshipBarEvidence> = emptyList(),
        performanceGains: Map<String, Int> = emptyMap(),
    ): DecisionTracer.TrainingCandidateEvidence =
        DecisionTracer.TrainingCandidateEvidence(
            numRainbow = numRainbow,
            numSkillHints = numSkillHints,
            trainingLevel = trainingLevel,
            numSpiritGaugesCanFill = canFill,
            numSpiritGaugesReadyToBurst = readyToBurst,
            relationshipBars = bars,
            performanceGains = performanceGains,
        )

    /** A turn whose picked and runner-up training candidates both carry enriched evidence. */
    private fun enrichedTurn(): DecisionTracer {
        val tracer = DecisionTracer()
        tracer.startTurn(date = date(), trainee = trainee())
        tracer.recordActionChoice(chosen = MainScreenAction.TRAIN, reason = "default action")
        tracer.recordTrainingSelection(
            selected = StatName.SPEED,
            source = SelectionSource.ANALYSIS,
            reason = "won analysis",
            runnerUps =
                listOf(
                    DecisionTracer.TrainingRunnerUp(
                        stat = StatName.WIT,
                        rejected = false,
                        reason = "outscored",
                        score = 12.25,
                        failureChance = 3,
                        evidence = evidence(numRainbow = 0, numSkillHints = 1, trainingLevel = 3, bars = listOf(bar(40.0, 2, "green"))),
                    ),
                    DecisionTracer.TrainingRunnerUp(
                        stat = StatName.GUTS,
                        rejected = true,
                        reason = "excluded (hard penalty)",
                        score = null,
                        evidence = evidence(numRainbow = 0, numSkillHints = 0),
                    ),
                ),
            pickedFailureChance = 8,
            pickedStatGains = mapOf(StatName.SPEED to 11),
            pickedEvidence =
                evidence(
                    numRainbow = 2,
                    numSkillHints = 1,
                    trainingLevel = 5,
                    bars = listOf(bar(88.0, 4, "blue", support = true, trainer = "Yayoi Akikawa"), bar(20.0, 1, "orange")),
                ),
        )
        return tracer
    }

    private fun trainingCandidates(json: JSONObject): Map<String, JSONObject> {
        val candidates = json.getJSONArray("candidates")
        return (0 until candidates.length())
            .map { candidates.getJSONObject(it) }
            .filter { it.getString("type") == "training" }
            .associateBy { it.getString("id") }
    }

    @Test
    @DisplayName("picked and runner-up candidates both carry the enriched scorer evidence")
    fun `enriched candidate evidence serializes for picked and runner-ups`() {
        val byId = trainingCandidates(record(enrichedTurn()))

        val speed = byId.getValue("SPEED")
        assertEquals(2, speed.getInt("numRainbow"))
        assertEquals(1, speed.getInt("numSkillHints"))
        assertEquals(5, speed.getInt("trainingLevel"))
        val speedBars = speed.getJSONArray("relationshipBars")
        assertEquals(2, speedBars.length())
        assertEquals(88.0, speedBars.getJSONObject(0).getDouble("fillPercent"))
        assertEquals("blue", speedBars.getJSONObject(0).getString("dominantColor"))
        assertTrue(speedBars.getJSONObject(0).getBoolean("support"))
        assertEquals("Yayoi Akikawa", speedBars.getJSONObject(0).getString("trainer"))

        // Symmetry: a runner-up exposes the same vocabulary where the source supports it.
        val wit = byId.getValue("WIT")
        assertEquals(0, wit.getInt("numRainbow"))
        assertEquals(1, wit.getInt("numSkillHints"))
        assertEquals(3, wit.getInt("trainingLevel"))
        assertEquals(1, wit.getJSONArray("relationshipBars").length())

        assertEquals(record(enrichedTurn()).toString(), JSONObject(record(enrichedTurn()).toString()).toString())
    }

    @Test
    @DisplayName("unavailable evidence is omitted, never placeholder-filled")
    fun `enriched evidence omits what the analysis did not compute`() {
        val byId = trainingCandidates(record(enrichedTurn()))

        // A non-support bar omits support and trainer rather than writing false / a fabricated name.
        val speedBars = byId.getValue("SPEED").getJSONArray("relationshipBars")
        val plainBar = speedBars.getJSONObject(1)
        assertFalse(plainBar.has("support"), "a non-support bar omits the support flag")
        assertFalse(plainBar.has("trainer"), "a non-support bar carries no trainer identity")

        // The hard-excluded runner-up had no level, no bars, no performance gains: all omitted, not zeroed.
        val guts = byId.getValue("GUTS")
        assertEquals(0, guts.getInt("numRainbow"))
        assertFalse(guts.has("trainingLevel"), "an unread level is omitted")
        assertFalse(guts.has("relationshipBars"), "an empty bar list is omitted")
        assertFalse(guts.has("performanceGains"), "empty performance gains are omitted")
    }

    @Test
    @DisplayName("spirit-gauge counts serialize only under Unity Cup")
    fun `spirit gauges gate on scenario`() {
        val tracer = DecisionTracer()
        tracer.startTurn(date = date(), trainee = trainee())
        tracer.recordActionChoice(chosen = MainScreenAction.TRAIN, reason = "default action")
        tracer.recordTrainingSelection(
            selected = StatName.SPEED,
            source = SelectionSource.ANALYSIS,
            reason = "won analysis",
            runnerUps =
                listOf(
                    DecisionTracer.TrainingRunnerUp(stat = StatName.WIT, rejected = false, reason = "outscored", score = 5.0, evidence = evidence(canFill = 0, readyToBurst = 0)),
                ),
            pickedEvidence = evidence(canFill = 2, readyToBurst = 1),
        )

        // Off Unity Cup: the uncomputed default is omitted on both picked and runner-up candidates.
        val offCup = trainingCandidates(DecisionTrace.buildRecord(timestamp = 1L, evidence = tracer.turnEvidence(), scenario = "Trackblazer"))
        assertFalse(offCup.getValue("SPEED").has("numSpiritGaugesCanFill"))
        assertFalse(offCup.getValue("WIT").has("numSpiritGaugesReadyToBurst"))

        // Under Unity Cup: the measured counts are written, including a genuine zero on the runner-up.
        val onCup = trainingCandidates(DecisionTrace.buildRecord(timestamp = 1L, evidence = tracer.turnEvidence(), scenario = "Unity Cup"))
        assertEquals(2, onCup.getValue("SPEED").getInt("numSpiritGaugesCanFill"))
        assertEquals(1, onCup.getValue("SPEED").getInt("numSpiritGaugesReadyToBurst"))
        assertEquals(0, onCup.getValue("WIT").getInt("numSpiritGaugesCanFill"))
    }

    @Test
    @DisplayName("Grand Concert performance gains serialize by canonical type name when present")
    fun `performance gains serialize when present`() {
        val tracer = DecisionTracer()
        tracer.startTurn(date = date(), trainee = trainee())
        tracer.recordActionChoice(chosen = MainScreenAction.TRAIN, reason = "default action")
        tracer.recordTrainingSelection(
            selected = StatName.SPEED,
            source = SelectionSource.ANALYSIS,
            reason = "won analysis",
            pickedEvidence = evidence(performanceGains = linkedMapOf("Dance" to 4, "Vocal" to 2)),
        )
        val speed = trainingCandidates(DecisionTrace.buildRecord(timestamp = 1L, evidence = tracer.turnEvidence(), scenario = "Grand Concert")).getValue("SPEED")
        val gains = speed.getJSONObject("performanceGains")
        assertEquals(4, gains.getInt("Dance"))
        assertEquals(2, gains.getInt("Vocal"))
        assertFalse(gains.has("Visual"), "a type the analysis did not read is absent")
    }

    @Test
    @DisplayName("both schema versions stay at 1 and no writer v2 is emitted")
    fun `schema versions are pinned`() {
        assertEquals(1, DecisionTrace.SCHEMA_VERSION)
        assertEquals(1, CareerStateSerializer.SCHEMA_VERSION)
        assertEquals(1, record(enrichedTurn()).getInt("v"))
    }

    @Test
    @DisplayName("the enrichment adds a modest, bounded number of bytes to a rich record")
    fun `record size impact stays within budget`() {
        // A rich five-facility contest: a picked facility plus four runner-ups, each with several support
        // bars and the full scalar evidence, serialized once with the evidence attached and once without.
        val bars3 = listOf(bar(90.0, 4, "blue", support = true, trainer = "Yayoi Akikawa"), bar(55.0, 3, "green", support = true), bar(15.0, 1, "orange"))
        val bars2 = listOf(bar(70.0, 3, "green", support = true), bar(10.0, 1, "orange"))
        val richEvidence = evidence(numRainbow = 2, numSkillHints = 2, trainingLevel = 5, canFill = 1, readyToBurst = 1, bars = bars3)

        fun turn(withEvidence: Boolean): DecisionTracer {
            val tracer = DecisionTracer()
            tracer.startTurn(date = date(), trainee = trainee())
            tracer.recordActionChoice(chosen = MainScreenAction.TRAIN, reason = "default action")
            tracer.recordTrainingSelection(
                selected = StatName.SPEED,
                source = SelectionSource.ANALYSIS,
                reason = "won analysis",
                runnerUps =
                    listOf(StatName.STAMINA, StatName.POWER, StatName.GUTS, StatName.WIT).mapIndexed { i, stat ->
                        DecisionTracer.TrainingRunnerUp(
                            stat = stat,
                            rejected = false,
                            reason = "outscored",
                            score = 20.0 - i,
                            failureChance = i,
                            statGains = mapOf(stat to 8 + i),
                            evidence = if (withEvidence) evidence(numRainbow = 1, numSkillHints = 1, trainingLevel = 4, bars = bars2) else null,
                        )
                    },
                pickedFailureChance = 6,
                pickedStatGains = mapOf(StatName.SPEED to 12),
                pickedEvidence = if (withEvidence) richEvidence else null,
            )
            return tracer
        }

        val before = record(turn(withEvidence = false)).toString().toByteArray(Charsets.UTF_8).size
        val after = record(turn(withEvidence = true)).toString().toByteArray(Charsets.UTF_8).size
        val increase = after - before

        println("[E1-1 size] before=$before after=$after increase=$increase (${"%.1f".format(100.0 * increase / before)}%)")

        // The adversarial review's stop-and-reassess line: the added evidence alone must not push a rich
        // record up by more than roughly 4 KB. A ~1-2 KB increase is the acceptable band.
        assertTrue(increase in 1 until 4096, "enrichment added $increase bytes; expected a bounded (<4 KB) increase")
    }

    @Test
    @DisplayName("an exact entered-race fact serializes with its name and no matchCount")
    fun `entered race exact serializes`() {
        val json =
            DecisionTrace.buildRecord(
                timestamp = 1L,
                evidence = representativeTurn().turnEvidence(),
                enteredRace = EnteredRace(34, EnteredRaceResolution.EXACT, EnteredRacePath.MANDATORY_GOAL, name = "Tokyo Yushun (Japanese Derby)"),
            )
        val entered = json.getJSONObject("enteredRace")
        assertEquals(34, entered.getInt("turnNumber"))
        assertEquals("exact", entered.getString("resolution"))
        assertEquals("mandatoryGoal", entered.getString("path"))
        assertEquals("Tokyo Yushun (Japanese Derby)", entered.getString("name"))
        // matchCount is omitted for a single exact match, not written as null or 1.
        assertFalse(entered.has("matchCount"))
        // Round-trips byte-identically (insertion order stable).
        assertEquals(json.toString(), JSONObject(json.toString()).toString())
    }

    @Test
    @DisplayName("an ambiguous set carries matchCount and no name")
    fun `entered race ambiguous omits name`() {
        val json =
            DecisionTrace.buildRecord(
                timestamp = 1L,
                evidence = representativeTurn().turnEvidence(),
                enteredRace = EnteredRace(55, EnteredRaceResolution.AMBIGUOUS_SET, EnteredRacePath.SCHEDULED, matchCount = 2),
            )
        val entered = json.getJSONObject("enteredRace")
        assertEquals("ambiguousSet", entered.getString("resolution"))
        assertEquals(2, entered.getInt("matchCount"))
        // No fabricated name for an unresolved-among-many set.
        assertFalse(entered.has("name"))
    }

    @Test
    @DisplayName("unresolved and non-catalog facts never carry a name")
    fun `entered race nameless resolutions`() {
        val standard =
            DecisionTrace
                .buildRecord(timestamp = 1L, evidence = representativeTurn().turnEvidence(), enteredRace = EnteredRace(40, EnteredRaceResolution.UNRESOLVED, EnteredRacePath.STANDARD))
                .getJSONObject("enteredRace")
        assertEquals("unresolved", standard.getString("resolution"))
        assertEquals("standard", standard.getString("path"))
        assertFalse(standard.has("name"))
        assertFalse(standard.has("matchCount"))

        val showdown =
            DecisionTrace
                .buildRecord(timestamp = 1L, evidence = representativeTurn().turnEvidence(), enteredRace = EnteredRace(60, EnteredRaceResolution.NON_CATALOG, EnteredRacePath.UNITY_CUP_SHOWDOWN))
                .getJSONObject("enteredRace")
        assertEquals("nonCatalog", showdown.getString("resolution"))
        assertEquals("unityCupShowdown", showdown.getString("path"))
        assertFalse(showdown.has("name"))
    }

    @Test
    @DisplayName("a turn with no completed race omits enteredRace entirely")
    fun `entered race omitted when absent`() {
        // A representative (TRAIN) turn built without an enteredRace argument, and an empty turn.
        assertFalse(record(representativeTurn()).has("enteredRace"))
        assertFalse(DecisionTrace.buildRecord(timestamp = 1L, evidence = DecisionTracer().turnEvidence()).has("enteredRace"))
    }

    @Test
    @DisplayName("the entered-race turn is exactly the one supplied, never a same-name race's other turn")
    fun `entered race turn is collision-safe`() {
        // Admire Vega's Arima Kinen recurs at turns 48 and 72 (same bare name, distinct turns). A fact
        // recorded for the turn-48 entry must serialize 48 even though the name also exists at turn 72:
        // the writer sources the turn from the current turn, never a bare-name-map RaceData.turnNumber.
        val entered =
            DecisionTrace
                .buildRecord(timestamp = 1L, evidence = representativeTurn().turnEvidence(), enteredRace = EnteredRace(48, EnteredRaceResolution.EXACT, EnteredRacePath.SMART, name = "Arima Kinen"))
                .getJSONObject("enteredRace")
        assertEquals(48, entered.getInt("turnNumber"))
        assertEquals("Arima Kinen", entered.getString("name"))
    }
}
