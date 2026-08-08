package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.Aptitude
import com.steve1316.uma_android_automation.types.DateMonth
import com.steve1316.uma_android_automation.types.DatePhase
import com.steve1316.uma_android_automation.types.DateYear
import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.Mood
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.types.TrackDistance
import com.steve1316.uma_android_automation.types.Trainee
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Deterministic contract tests for Canonical CareerState v1 (Phase A).
 *
 * The invariants pinned here are the ones a Phase-B consumer would silently misread if they broke:
 * the snapshot is an immutable value copy (a later mutation of the live objects cannot reach it); a
 * group that was never read is unavailable rather than carrying its constructor default; identity
 * reuses the authoritative finalize-token machinery rather than an approximate duplicate; scenario
 * state stays out of the stable core; and the debug shadow comparison only holds the fields that are
 * semantically equal at both boundaries strict, classifying the rest as expected drift.
 *
 * The pure builder/comparator take no `Context`/`ImageUtils`, so these run headless over synthetic
 * `Trainee`/`GameDate` instances - which is itself the structural proof that construction triggers no
 * screen read.
 */
@DisplayName("CareerState Phase A Tests")
class CareerStateTest {
    private fun identity(): CareerIdentity =
        CareerStateBuilder.buildIdentity(
            scenario = "Trackblazer",
            traineeName = "Biwa Hayahide",
            presetRaw = "Biwa Hayahide",
            queueRunRaw = 0,
            careerNonce = "abc123",
            configFingerprint = "fp1",
        )

    /** A date whose day was read from the screen (dayObserved = true). */
    private fun observedDate(day: Int = 5): GameDate = GameDate(day).apply { updateDay(day) }

    /** A fully-read trainee: stats/skill points/aptitudes flagged observed. */
    private fun observedTrainee(): Trainee =
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

    private fun build(
        date: GameDate = observedDate(),
        trainee: Trainee = observedTrainee(),
        scenario: ScenarioState? = null,
        mandatoryRaceDay: Boolean = false,
        scheduledRaceDay: Boolean = false,
        goalRibbonDay: Boolean = false,
    ): CareerState =
        CareerStateBuilder.build(identity(), date, trainee, mandatoryRaceDay, scheduledRaceDay, goalRibbonDay, scenario)

    /** A DecisionTracer turn-open snapshot matching [observedTrainee] / [observedDate]. */
    private fun openSnapshot(
        energy: Int = 62,
        mood: Mood = Mood.GOOD,
        skillPoints: Int = 340,
        speed: Int = 412,
    ): DecisionTracer.StateSnapshot =
        DecisionTracer.StateSnapshot(
            energy = energy,
            mood = mood,
            negativeStatuses = emptyList(),
            inventory = emptyMap(),
            extra = emptyMap(),
            stats = mapOf(StatName.SPEED to speed, StatName.STAMINA to 300, StatName.POWER to 288, StatName.GUTS to 190, StatName.WIT to 260),
            skillPoints = skillPoints,
            fans = 12000,
            statsObserved = true,
            skillPointsObserved = true,
            aptitudesObserved = true,
        )

    // ---- Immutability ----

    @Test
    @DisplayName("A snapshot is unaffected by later mutation of the live sources it was built from")
    fun snapshotIsImmutable() {
        val date = observedDate(5)
        val trainee = observedTrainee()
        trainee.trackDistanceAptitudes[TrackDistance.MILE] = Aptitude.A
        trainee.currentNegativeStatuses.add("Headache")
        val inventory = mutableMapOf("Empowering Megaphone" to 2)
        val tb = TrackblazerState.snapshot(10, inventory, 1, true, false, false, false, 0, 0)

        val snapshot = build(date, trainee, tb)

        // Mutate every live source AFTER the snapshot was taken.
        date.updateDay(40)
        trainee.setTraineeStats(speed = 999, stamina = 999, power = 999, guts = 999, wit = 999)
        trainee.trackDistanceAptitudes[TrackDistance.MILE] = Aptitude.S
        trainee.currentNegativeStatuses.add("Injury")
        trainee.energy = 5
        inventory["Empowering Megaphone"] = 99

        assertEquals(5, snapshot.date.observedTurn)
        assertEquals(412, snapshot.stats!!.stats[StatName.SPEED])
        assertEquals(Aptitude.A, snapshot.aptitudes!!.distance[TrackDistance.MILE])
        assertEquals(listOf("Headache"), snapshot.condition.negativeStatuses)
        assertEquals(62, snapshot.condition.energy)
        assertEquals(mapOf("Empowering Megaphone" to 2), (snapshot.scenario as TrackblazerState).inventory)
    }

    // ---- Default / unread protection ----

    @Test
    @DisplayName("Unread stats and skill points are unavailable, never the -1 / 120 constructor defaults")
    fun unreadStatsAreUnavailable() {
        val fresh = Trainee() // bHasUpdatedStats = false, stats default -1, skillPoints default 120
        val snapshot = build(trainee = fresh)
        assertNull(snapshot.stats)
        assertNull(snapshot.skillPoints)
        assertEquals(StateProvenance.UNREAD, snapshot.provenance.stats)
    }

    @Test
    @DisplayName("Unread aptitudes are unavailable, never the all-G defaults")
    fun unreadAptitudesAreUnavailable() {
        val trainee = observedTrainee().apply { bHasUpdatedAptitudes = false }
        val snapshot = build(trainee = trainee)
        assertNull(snapshot.aptitudes)
        assertEquals(StateProvenance.UNREAD, snapshot.provenance.aptitudes)
    }

    @Test
    @DisplayName("An unobserved date exposes no turn (guards the phantom turn-1 bug)")
    fun unobservedDateExposesNoTurn() {
        val date = GameDate(year = DateYear.JUNIOR, month = DateMonth.JANUARY, phase = DatePhase.EARLY)
        assertFalse(date.dayObserved)
        val snapshot = build(date = date)
        assertNull(snapshot.date.observedTurn)
        assertNull(snapshot.date.year)
        assertFalse(snapshot.date.dayObserved)
        assertEquals(StateProvenance.UNREAD, snapshot.provenance.date)
    }

    // ---- Provenance ----

    @Test
    @DisplayName("Group provenance classifies configured / derived / observed / unread correctly")
    fun provenanceClassifiesGroups() {
        val snapshot = build(scenario = null)
        assertEquals(StateProvenance.CONFIGURED, snapshot.provenance.identityInputs)
        assertEquals(StateProvenance.DERIVED, snapshot.provenance.derivedIdentity)
        assertEquals(StateProvenance.OBSERVED, snapshot.provenance.date)
        assertEquals(StateProvenance.OBSERVED, snapshot.provenance.condition)
        assertEquals(StateProvenance.OBSERVED, snapshot.provenance.stats)
        assertEquals(StateProvenance.OBSERVED, snapshot.provenance.aptitudes)
        assertEquals(StateProvenance.OBSERVED, snapshot.provenance.race)
        assertEquals(StateProvenance.UNREAD, snapshot.provenance.scenario)
    }

    // ---- Identity ----

    @Test
    @DisplayName("Identity token is the exact output of the authoritative finalize-token machinery")
    fun identityReusesFinalizeToken() {
        val id =
            CareerStateBuilder.buildIdentity(
                scenario = "Unity Cup",
                traineeName = "Taiki Shuttle",
                presetRaw = "Taiki Shuttle",
                queueRunRaw = 2,
                careerNonce = "deadbeef",
                configFingerprint = "fpX",
            )
        assertEquals(buildCareerFinalizeToken("Taiki Shuttle", "Unity Cup", 2, "deadbeef"), id.careerToken)
        assertEquals(2, id.queueRun)
        assertEquals("Taiki Shuttle", id.preset)
    }

    @Test
    @DisplayName("Empty preset falls back to the trainee name and queueRun 0 becomes null, matching finalize semantics")
    fun identityPresetAndQueueFallback() {
        val id =
            CareerStateBuilder.buildIdentity(
                scenario = "Trackblazer",
                traineeName = "Biwa Hayahide",
                presetRaw = "",
                queueRunRaw = 0,
                careerNonce = "nonce",
                configFingerprint = "f",
            )
        assertEquals(buildCareerFinalizeToken("Biwa Hayahide", "Trackblazer", null, "nonce"), id.careerToken)
        assertNull(id.preset)
        assertNull(id.queueRun)
    }

    // ---- Scenario extensions ----

    @Test
    @DisplayName("A scenario with no boundary state yields a null extension (base / URA / Unity Cup)")
    fun noScenarioExtension() {
        val snapshot = build(scenario = null)
        assertNull(snapshot.scenario)
        assertEquals(StateProvenance.UNREAD, snapshot.provenance.scenario)
    }

    @Test
    @DisplayName("The Trackblazer extension is a defensive copy, and passes through the snapshot")
    fun trackblazerExtensionCopiedAndCarried() {
        val inventory = mutableMapOf("Empowering Megaphone" to 1)
        val tb = TrackblazerState.snapshot(5, inventory, 3, true, true, false, false, 2, 4)
        inventory["Empowering Megaphone"] = 99
        inventory["New Item"] = 1
        assertEquals(mapOf("Empowering Megaphone" to 1), tb.inventory)

        val snapshot = build(scenario = tb)
        assertSame(tb, snapshot.scenario)
        assertEquals(StateProvenance.OBSERVED, snapshot.provenance.scenario)
    }

    @Test
    @DisplayName("The Grand Concert extension carries its known counters and structurally omits PP balances")
    fun grandConcertExtensionKnownState() {
        val gc = GrandConcertState(songsBoughtThisCycle = 2, songsBoughtThisCareer = 9, lastConcertBoundary = 24)
        val snapshot = build(scenario = gc)
        assertSame(gc, snapshot.scenario)
        assertEquals(2, (snapshot.scenario as GrandConcertState).songsBoughtThisCycle)
    }

    // ---- Race context ----

    @Test
    @DisplayName("Race context copies the current turn's race-day flags")
    fun raceContextCopiesFlags() {
        val snapshot = build(mandatoryRaceDay = true, scheduledRaceDay = false, goalRibbonDay = true)
        assertNotNull(snapshot.race)
        assertTrue(snapshot.race!!.mandatoryRaceDay)
        assertFalse(snapshot.race.scheduledRaceDay)
        assertTrue(snapshot.race.goalRibbonDay)
    }

    @Test
    @DisplayName("Two snapshots do not share race state, so a stale prior-turn context cannot leak in")
    fun raceContextIsPerSnapshot() {
        val date = observedDate()
        val trainee = observedTrainee()
        val racy = CareerStateBuilder.build(identity(), date, trainee, true, true, true, null)
        val calm = CareerStateBuilder.build(identity(), date, trainee, false, false, false, null)
        assertTrue(racy.race!!.mandatoryRaceDay)
        assertFalse(calm.race!!.mandatoryRaceDay)
    }

    // ---- One-per-turn latch ----

    @Test
    @DisplayName("The latch builds exactly once per armed turn and re-arms for the next turn")
    fun latchBuildsOncePerTurn() {
        val latch = CareerStateTurnLatch()
        latch.armForNewTurn()
        assertTrue(latch.shouldBuild()) // first decision tick of the turn
        assertFalse(latch.shouldBuild()) // a RECOVER_MOOD re-tick on the same date must not rebuild
        assertFalse(latch.shouldBuild())
        latch.armForNewTurn() // next turn opens
        assertTrue(latch.shouldBuild())
    }

    @Test
    @DisplayName("Cadence follows action-completion turns, not date OCR: two unknown-date turns each build once, both with turn=null")
    fun latchRebuildsAcrossUnknownDateTurns() {
        val latch = CareerStateTurnLatch()
        val unread = GameDate(year = DateYear.JUNIOR, month = DateMonth.JANUARY, phase = DatePhase.EARLY) // dayObserved = false
        assertFalse(unread.dayObserved)

        // Turn A: the first pre-decision pass builds even though the tracer never opened a window and the
        // date was never read. A same-turn re-tick must not rebuild.
        assertTrue(latch.shouldBuild())
        assertFalse(latch.shouldBuild())
        val a = CareerStateBuilder.build(identity(), unread, observedTrainee(), false, false, false, null)
        assertNull(a.date.observedTurn)

        // The action advanced the game to a new turn while date OCR is still failing. The latch takes no
        // date input, so it rearms regardless of dayObserved. Turn B builds exactly once and honestly
        // keeps turn = null. This is the exact class of failure the first live run exposed.
        latch.armForNewTurn()
        assertTrue(latch.shouldBuild())
        assertFalse(latch.shouldBuild())
        val b = CareerStateBuilder.build(identity(), unread, observedTrainee(), false, false, false, null)
        assertNull(b.date.observedTurn)
    }

    @Test
    @DisplayName("Cadence does not require a DecisionTracer window (markTracerWindowOpened is never called)")
    fun latchCadenceIsIndependentOfTracerWindow() {
        val latch = CareerStateTurnLatch()
        // No tracer window is ever opened; the build cadence is unaffected.
        assertTrue(latch.shouldBuild())
        latch.armForNewTurn()
        assertTrue(latch.shouldBuild())
        // ...and the comparison is honestly reported as having no fresh evidence rather than comparing.
        assertFalse(latch.tracerWindowFresh())
    }

    @Test
    @DisplayName("Tracer-window freshness tracks the tracer window, so a snapshot is never compared against a prior turn's evidence")
    fun tracerWindowFreshnessTracksTheWindow() {
        val latch = CareerStateTurnLatch()
        assertFalse(latch.tracerWindowFresh()) // no window opened yet

        // Turn A: the tracer opened its window this turn, so the shadow comparison is meaningful.
        latch.markTracerWindowOpened()
        assertTrue(latch.tracerWindowFresh())
        assertTrue(latch.shouldBuild())

        // An action advances to turn B; the tracer has NOT reopened (its startTurn gate did not fire, e.g.
        // date OCR failed). The snapshot still builds, but the tracer window is now stale, so freshness is
        // false and the comparison must be treated as unavailable rather than compared against turn A.
        latch.armForNewTurn()
        assertFalse(latch.tracerWindowFresh())
        assertTrue(latch.shouldBuild())
    }

    @Test
    @DisplayName("A non-advancing RACE (aborted by the consecutive-race warning) does not rearm, so the next same-turn pass builds no duplicate")
    fun nonAdvancingRaceDoesNotRebuildSameTurn() {
        val latch = CareerStateTurnLatch()
        // The prior turn's advancing action armed the latch; this turn builds its one pre-decision snapshot.
        latch.armForNewTurn()
        assertTrue(latch.shouldBuild())

        // executeAction(RACE): handleRaceEvents returned false (consecutive-race warning aborted the race).
        // The RACE branch resets bHasCheckedDateThisTurn but arms CareerState only on the real advance signal.
        latch.armForNewTurnIf(false)

        // The next main-screen pass is still the same logical turn (the bot re-evaluates and picks another
        // action). No second snapshot may be built - this is the turn-25 live duplicate, now prevented.
        assertFalse(latch.shouldBuild())
        // Freshness is untouched by the no-op arm, so a stale tracer window cannot masquerade as fresh.
        assertFalse(latch.tracerWindowFresh())
    }

    @Test
    @DisplayName("An advancing RACE (a race actually ran) rearms, so the next logical turn builds exactly one snapshot")
    fun advancingRaceRearmsForNextTurn() {
        val latch = CareerStateTurnLatch()
        latch.armForNewTurn()
        assertTrue(latch.shouldBuild()) // this turn's snapshot

        // executeAction(RACE): handleRaceEvents returned true (a race ran and advanced the game turn).
        latch.armForNewTurnIf(true)

        // The following logical turn builds once, and a same-turn re-tick still does not rebuild.
        assertTrue(latch.shouldBuild())
        assertFalse(latch.shouldBuild())
    }

    @Test
    @DisplayName("An aborted RACE whose fallback backs out (no training or recovery) does not rearm, so no same-turn duplicate")
    fun abortedRaceWithBackoutFallbackDoesNotRebuild() {
        val latch = CareerStateTurnLatch()
        latch.armForNewTurn()
        assertTrue(latch.shouldBuild()) // this turn's snapshot

        // executeAction(RACE): bDidRace=false, and the fallback merely backed out onto the same turn.
        val bDidRace = false
        val fallback = RaceFallbackOutcome(shouldStopForMandatoryRace = false, turnAdvanced = false)
        val turnAdvanced = bDidRace || fallback.turnAdvanced
        latch.armForNewTurnIf(turnAdvanced)

        // Still the same logical turn (the bot re-evaluates and picks another action). No duplicate.
        assertFalse(latch.shouldBuild())
        assertFalse(latch.tracerWindowFresh())
    }

    @Test
    @DisplayName("An aborted RACE whose fallback training advances the turn rearms once and invalidates freshness, so the next logical turn builds exactly one snapshot")
    fun abortedRaceWithAdvancingFallbackRearms() {
        val latch = CareerStateTurnLatch()
        latch.armForNewTurn()
        latch.markTracerWindowOpened() // this turn opened its DecisionTracer window
        assertTrue(latch.shouldBuild()) // this turn's snapshot
        assertTrue(latch.tracerWindowFresh())

        // executeAction(RACE): bDidRace=false, but the fallback trained/recovered and advanced the turn.
        val bDidRace = false
        val fallback = RaceFallbackOutcome(shouldStopForMandatoryRace = false, turnAdvanced = true)
        val turnAdvanced = bDidRace || fallback.turnAdvanced
        latch.armForNewTurnIf(turnAdvanced)

        // Freshness is invalidated by the rearm, so the next snapshot cannot compare against stale evidence.
        assertFalse(latch.tracerWindowFresh())
        // The next logical turn (previously missing, e.g. live turns 58/59) now builds exactly one snapshot.
        assertTrue(latch.shouldBuild())
        assertFalse(latch.shouldBuild())
    }

    /** Mirror of the base handleRaceEventFallback mapping: a training outcome becomes a fallback outcome. */
    private fun baseFallbackOutcome(training: TrainingActionOutcome): RaceFallbackOutcome =
        RaceFallbackOutcome(shouldStopForMandatoryRace = false, turnAdvanced = training.turnAdvanced)

    @Test
    @DisplayName("Base fallback: a normal facility training advances the turn, so CareerState rearms once")
    fun baseFallbackNormalTrainingRearms() {
        val latch = CareerStateTurnLatch()
        latch.armForNewTurn()
        assertTrue(latch.shouldBuild())

        // handleTrainingWithOutcome ran a facility training: selectedTraining non-null, turnAdvanced true.
        val bDidRace = false
        val fallback = baseFallbackOutcome(TrainingActionOutcome(selectedTraining = StatName.SPEED, turnAdvanced = true))
        latch.armForNewTurnIf(bDidRace || fallback.turnAdvanced)

        assertTrue(latch.shouldBuild()) // next turn builds
        assertFalse(latch.shouldBuild()) // only once
    }

    @Test
    @DisplayName("Base fallback: a recovery-only training advances the turn even with a null selected stat, so CareerState still rearms (the residual gap, now closed)")
    fun baseFallbackRecoveryAdvanceRearms() {
        val latch = CareerStateTurnLatch()
        latch.armForNewTurn()
        latch.markTracerWindowOpened()
        assertTrue(latch.shouldBuild())
        assertTrue(latch.tracerWindowFresh())

        // handleTrainingWithOutcome recovered energy/mood: NO facility executed (selectedTraining null) but
        // the turn advanced. The old `selectedTraining != null` test would have read this as non-advancing.
        val training = TrainingActionOutcome(selectedTraining = null, turnAdvanced = true)
        assertNull(training.selectedTraining)
        assertTrue(training.turnAdvanced)

        val bDidRace = false
        val fallback = baseFallbackOutcome(training)
        assertTrue(fallback.turnAdvanced) // truthful advancement despite the null stat
        latch.armForNewTurnIf(bDidRace || fallback.turnAdvanced)

        assertFalse(latch.tracerWindowFresh()) // freshness invalidated through the normal rearm
        assertTrue(latch.shouldBuild()) // the next logical turn builds exactly one snapshot
        assertFalse(latch.shouldBuild())
    }

    @Test
    @DisplayName("Base fallback: a non-advancing outcome (nav/backout) does not rearm, so no same-turn duplicate")
    fun baseFallbackNonAdvanceDoesNotRebuild() {
        val latch = CareerStateTurnLatch()
        latch.armForNewTurn()
        assertTrue(latch.shouldBuild())

        // handleTrainingWithOutcome could not open the Training screen / return to Main: no advance.
        val bDidRace = false
        val fallback = baseFallbackOutcome(TrainingActionOutcome(selectedTraining = null, turnAdvanced = false))
        latch.armForNewTurnIf(bDidRace || fallback.turnAdvanced)

        assertFalse(latch.shouldBuild()) // same turn: no duplicate
        assertFalse(latch.tracerWindowFresh())
    }

    // ---- Decision sequence (career_state <-> decision_trace join key) ----

    @Test
    @DisplayName("Decision seq is monotonic per career and emit stamps the current turn's seq even after the action re-armed the latch for the next turn")
    fun decisionSeqIsMonotonicAndEmitStampsTheCurrentTurn() {
        val latch = CareerStateTurnLatch()
        val seq = CareerStateDecisionSequence()
        latch.armForNewTurn()

        // Turn 1 build boundary: consume the build opportunity, allocate seq 1, build succeeds -> retain.
        assertTrue(latch.shouldBuild())
        val s1 = seq.allocate()
        assertEquals(1, s1)
        seq.retain(s1)
        assertEquals(1, seq.current())

        // executeAction advances and RE-ARMS the latch for turn 2 - and this happens BEFORE turn 1's
        // trace emits. The emit-ordering trap: the trace must still stamp turn 1's seq, not turn 2's.
        latch.armForNewTurn()
        assertEquals(1, seq.current()) // emit reads the retained current-turn seq, unaffected by the re-arm

        // Turn 2 build boundary: allocate clears current until the new build retains it.
        assertTrue(latch.shouldBuild())
        val s2 = seq.allocate()
        assertEquals(2, s2)
        assertNull(seq.current())
        seq.retain(s2)
        assertEquals(2, seq.current())
    }

    @Test
    @DisplayName("A same-turn re-tick does not reach the build boundary again, so it allocates no new seq")
    fun sameTurnRetickDoesNotAllocateNewSeq() {
        val latch = CareerStateTurnLatch()
        val seq = CareerStateDecisionSequence()
        latch.armForNewTurn()

        assertTrue(latch.shouldBuild())
        seq.retain(seq.allocate())
        assertEquals(1, seq.current())

        // A same-turn re-tick: shouldBuild is false, so the build block (and allocate) never runs.
        assertFalse(latch.shouldBuild())
        assertEquals(1, seq.current())
    }

    @Test
    @DisplayName("A swallowed CareerState build consumes its seq position, stamps no seq on the trace, and never reuses that position")
    fun buildFailureStampsNoSeqAndNeverReusesThePosition() {
        val latch = CareerStateTurnLatch()
        val seq = CareerStateDecisionSequence()
        latch.armForNewTurn()

        // Turn 1: build opportunity consumed (seq 1 allocated) but buildCareerState throws -> no retain.
        assertTrue(latch.shouldBuild())
        assertEquals(1, seq.allocate())
        assertNull(seq.current()) // the trace for this turn carries no seq
        latch.armForNewTurn()

        // Turn 2 succeeds: allocates seq 2, NOT reusing the failed position 1.
        assertTrue(latch.shouldBuild())
        val s2 = seq.allocate()
        assertEquals(2, s2)
        seq.retain(s2)
        assertEquals(2, seq.current())
    }

    @Test
    @DisplayName("A non-advancing action (aborted RACE) does not re-arm, so the next seq is not allocated until a real next decision turn")
    fun nonAdvancingActionDoesNotAllocateTheNextSeq() {
        val latch = CareerStateTurnLatch()
        val seq = CareerStateDecisionSequence()
        latch.armForNewTurn()

        assertTrue(latch.shouldBuild())
        seq.retain(seq.allocate())
        assertEquals(1, seq.current())

        // Aborted RACE: non-advancing, so the latch is not re-armed. The same-turn re-evaluation reaches
        // the build boundary again but shouldBuild is false, so no seq is allocated.
        latch.armForNewTurnIf(false)
        assertFalse(latch.shouldBuild())
        assertEquals(1, seq.current())

        // A later advancing action (fallback training, a real next turn) re-arms -> seq 2 is allocated.
        latch.armForNewTurn()
        assertTrue(latch.shouldBuild())
        assertEquals(2, seq.allocate())
    }

    @Test
    @DisplayName("Decision seq takes no date input, so two unknown-date turns still allocate 1 then 2 independent of any turn number")
    fun decisionSeqIsIndependentOfObservedTurnNumber() {
        val seq = CareerStateDecisionSequence()
        assertEquals(1, seq.allocate())
        seq.retain(1)
        assertEquals(1, seq.current())
        assertEquals(2, seq.allocate())
        seq.retain(2)
        assertEquals(2, seq.current())
    }

    // ---- Pure construction ----

    @Test
    @DisplayName("Construction runs headless with no Context / ImageUtils / OCR dependency")
    fun builderIsPure() {
        // If build() required a Context/ImageUtils/screenshot handle this test could not run headless.
        assertNotNull(build())
    }

    // ---- Shadow comparison semantics ----

    @Test
    @DisplayName("Comparison reports no strict mismatch and no drift when both boundaries agree")
    fun shadowComparisonAgrees() {
        val result = CareerStateShadow.compare(build(), openSnapshot(), openObservedTurn = 5)
        assertTrue(result.strictMismatches.isEmpty())
        assertTrue(result.expectedDrift.isEmpty())
    }

    @Test
    @DisplayName("Expected pre-decision drift (skill buys, item use) is classified as drift, not a mismatch")
    fun shadowComparisonClassifiesDrift() {
        // Pre-decision trainee: skill points dropped by a purchase, energy raised by an item.
        val postPrep =
            observedTrainee().apply {
                skillPoints = 315
                energy = 80
            }
        val snapshot = build(trainee = postPrep)
        val result = CareerStateShadow.compare(snapshot, openSnapshot(energy = 62, skillPoints = 340), openObservedTurn = 5)
        assertTrue(result.strictMismatches.isEmpty())
        assertTrue(result.expectedDrift.any { it.contains("skillPts 340->315") })
        assertTrue(result.expectedDrift.any { it.contains("energy 62->80") })
    }

    @Test
    @DisplayName("A changed strict field (a core stat) is reported as a real mismatch")
    fun shadowComparisonCatchesStrictMismatch() {
        val mutated = observedTrainee().apply { setTraineeStats(speed = 999) }
        val snapshot = build(trainee = mutated)
        val result = CareerStateShadow.compare(snapshot, openSnapshot(speed = 412), openObservedTurn = 5)
        assertTrue(result.strictMismatches.any { it.contains("stat.SPEED 412->999") })
    }
}
