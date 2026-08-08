package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.Aptitude
import com.steve1316.uma_android_automation.types.DateMonth
import com.steve1316.uma_android_automation.types.DatePhase
import com.steve1316.uma_android_automation.types.DateYear
import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.Mood
import com.steve1316.uma_android_automation.types.RunningStyle
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.types.TrackDistance
import com.steve1316.uma_android_automation.types.TrackSurface
import com.steve1316.uma_android_automation.types.Trainee

/**
 * Canonical CareerState v1 (Phase A, shadow-only).
 *
 * An immutable, single-point-in-time snapshot of the state the decision engine sees at the
 * main-screen turn boundary, taken once per turn immediately before `decideNextAction()` (after
 * `performGlobalChecks()` and `onMainScreenEntry()` have run). It reads only already-available live
 * state and copies every mutable value; it triggers no OCR, screenshot, navigation, scoring, or tap.
 *
 * Phase A is shadow-only: NOTHING in the gameplay path reads a [CareerState]. Gameplay stays sourced
 * from the live `Trainee`/`GameDate`/`Racing` objects. The snapshot exists so tests and a debug-gated
 * comparison can prove the boundary is coherent, and so Phase B can later migrate consumers onto it.
 *
 * Honesty rules mirror `DecisionTrace`: a value is exposed as observed only when an existing read
 * flag proves it was read this career. A group that was never read is represented as unavailable
 * (null) rather than carrying its constructor default, so the phantom turn-1 / stat-`-1` / all-`G`
 * classes of bug cannot reappear through this object.
 *
 * Not included in v1, deliberately: raw fan count and fan-count class (no per-field read flag exists,
 * so a default `fans = 1` cannot be honestly labelled observed); Grand Concert performance-point
 * balances (unknown at the main-screen boundary - they are only read during training-screen
 * analysis). Adding a read to populate either would be new work outside Phase A.
 */
data class CareerState(
    val identity: CareerIdentity,
    val date: DateState,
    val condition: ConditionState,
    /** The five core stats, present only when they were read this career. Null = never read (never the `-1` defaults). */
    val stats: StatState?,
    /** Skill points, present only when read this career. Null = never read (never the `120` default). */
    val skillPoints: Int?,
    /** Aptitude groups, present only when read this career. Null = never read (never the all-`G` defaults). */
    val aptitudes: AptitudeState?,
    /** Pre-decision race-day facts the decision engine consumes. Null only if the group was not refreshed for this turn. */
    val race: RaceContext?,
    /** Scenario-specific decision state, or null for scenarios with no persistent state at this boundary (URA, Unity Cup). */
    val scenario: ScenarioState?,
    val provenance: CareerStateProvenance,
)

/**
 * Availability class for a [CareerState] group. Grounded only in the boolean read flags the runtime
 * already maintains; no numeric confidence is invented, and no per-turn freshness is claimed where
 * only sticky career-level flags exist.
 *
 * Named `StateProvenance` rather than the audit's `Provenance` to avoid a package-level collision with
 * the unrelated research-verdict `Provenance` enum in `GrandConcertScenario.kt`; the four value names
 * the audit specified are unchanged.
 */
enum class StateProvenance {
    /** Read from the screen this career (an existing read flag proves it). */
    OBSERVED,

    /** Never read; the group is unavailable and carries no fabricated value. */
    UNREAD,

    /** A configured identity input (scenario, trainee, applied preset, queue run). */
    CONFIGURED,

    /** Derived from configured inputs plus the career nonce (career token, config fingerprint). */
    DERIVED,
}

/**
 * Decision-time career identity. Reuses the finalize token machinery ([buildCareerFinalizeToken]) and
 * the same nonce and preset/queue-run sources the finalized corpus uses, so a snapshot joins the
 * `career_finalize` and `decision_trace` rows on [careerToken] rather than inventing an identifier.
 *
 * [scenario]/[trainee]/[preset]/[queueRun] are CONFIGURED; [careerToken]/[configFingerprint] are
 * DERIVED from them plus the nonce.
 */
data class CareerIdentity(
    val careerToken: String,
    val scenario: String,
    val trainee: String,
    /** The applied preset name, or null when no preset is configured. */
    val preset: String?,
    /** The queue run index, or null when the career is not part of a queue. */
    val queueRun: Int?,
    val configFingerprint: String,
)

/**
 * The game date by value. [observedTurn] is null when the date was never read from the screen, so an
 * unread default day can never be mistaken for a real turn (the trap the outcome corpus already hit).
 * When [dayObserved] is false every component is null.
 */
data class DateState(
    val observedTurn: Int?,
    val year: DateYear?,
    val month: DateMonth?,
    val phase: DatePhase?,
    val dayObserved: Boolean,
)

/**
 * Decision-time condition facts, refreshed every turn-start by `performTurnStartUpdates`. These carry
 * no dedicated read flag in the current runtime, so they are treated as best-effort observed at this
 * boundary; the status lists are defensively copied.
 */
data class ConditionState(
    val energy: Int,
    val mood: Mood,
    val negativeStatuses: List<String>,
    val positiveStatuses: List<String>,
)

/** The five core stats by value. Present in a [CareerState] only when the stats were read this career. */
data class StatState(
    val stats: Map<StatName, Int>,
)

/**
 * The trainee's aptitude groups by value, copied. Present only when the aptitudes were read this
 * career. Derived convenience preferences (preferred distance / style) are intentionally NOT frozen
 * here: they are recomputable from these authoritative maps.
 */
data class AptitudeState(
    val surface: Map<TrackSurface, Aptitude>,
    val distance: Map<TrackDistance, Aptitude>,
    val runningStyle: Map<RunningStyle, Aptitude>,
)

/**
 * Pre-decision race-day facts already refreshed by the time the snapshot is built - the three cached
 * flags `decideNextAction()` consumes. The later `RaceEligibility` decision result is deliberately
 * excluded; that stays DecisionTrace evidence.
 */
data class RaceContext(
    val mandatoryRaceDay: Boolean,
    val scheduledRaceDay: Boolean,
    val goalRibbonDay: Boolean,
)

/** Group-level provenance for a [CareerState]. See [StateProvenance]. */
data class CareerStateProvenance(
    val identityInputs: StateProvenance,
    val derivedIdentity: StateProvenance,
    val date: StateProvenance,
    val condition: StateProvenance,
    val stats: StateProvenance,
    val aptitudes: StateProvenance,
    val race: StateProvenance,
    val scenario: StateProvenance,
)

/**
 * Scenario-specific decision state at the main-screen boundary. Only scenarios that genuinely carry
 * persistent, decision-relevant state at turn open get a payload; a scenario subclass builds its own
 * payload from its private fields in its `scenarioStateSnapshot()` override, so no private scenario
 * field is widened for this feature.
 */
sealed interface ScenarioState

/**
 * Trackblazer decision-relevant state at turn open. [consecutiveRaceCountObserved] is Trackblazer's
 * `counterUpdatedByOCR` flag - the count is a carried value until it is true. [inventory] is copied.
 */
data class TrackblazerState(
    val shopCoins: Int,
    val inventory: Map<String, Int>,
    val consecutiveRaceCount: Int,
    val consecutiveRaceCountObserved: Boolean,
    val usedWhistleToday: Boolean,
    val usedCharmToday: Boolean,
    val usedHammerToday: Boolean,
    val recreationUsedCount: Int,
    /** The trainee's remaining megaphone-boost turns (0 when none active). */
    val megaphoneTurnCounter: Int,
) : ScenarioState {
    companion object {
        /**
         * Build a [TrackblazerState], defensively copying [inventory] so a later mutation of the live
         * `currentInventory` map cannot change the snapshot. Used by the scenario override; keeps the
         * copy in one pure, testable place.
         */
        fun snapshot(
            shopCoins: Int,
            inventory: Map<String, Int>,
            consecutiveRaceCount: Int,
            consecutiveRaceCountObserved: Boolean,
            usedWhistleToday: Boolean,
            usedCharmToday: Boolean,
            usedHammerToday: Boolean,
            recreationUsedCount: Int,
            megaphoneTurnCounter: Int,
        ): TrackblazerState =
            TrackblazerState(
                shopCoins = shopCoins,
                inventory = inventory.toMap(),
                consecutiveRaceCount = consecutiveRaceCount,
                consecutiveRaceCountObserved = consecutiveRaceCountObserved,
                usedWhistleToday = usedWhistleToday,
                usedCharmToday = usedCharmToday,
                usedHammerToday = usedHammerToday,
                recreationUsedCount = recreationUsedCount,
                megaphoneTurnCounter = megaphoneTurnCounter,
            )
    }
}

/**
 * Grand Concert decision-relevant state at turn open. Performance-point balances are intentionally
 * absent: they are unknown at the main-screen boundary (read only during training-screen analysis),
 * so exposing them here would fabricate state.
 */
data class GrandConcertState(
    val songsBoughtThisCycle: Int,
    val songsBoughtThisCareer: Int,
    val lastConcertBoundary: Int,
) : ScenarioState

/**
 * Once-per-turn latch for the shadow CareerState build, plus a companion freshness flag for the debug
 * comparison. Cadence is driven by the action-completion lifecycle, NOT by the DecisionTracer window:
 * [armForNewTurn] is called when an action advances the game to a new main-screen decision turn, and
 * [shouldBuild] returns true for the first pre-decision pass of that turn. So a new turn still builds a
 * snapshot when date OCR failed (`dayObserved=false`) and when the tracer opened no window, and a
 * same-turn re-tick after a non-advancing action (a RECOVER_MOOD spin, a failed outing) does not
 * rebuild. Pure and independently testable.
 *
 * [markTracerWindowOpened] / [tracerWindowFresh] track, separately, whether the DecisionTracer opened
 * its own turn window this turn (its `startTurn` ran). The debug shadow comparison uses this so it
 * never compares a snapshot against a tracer snapshot left over from an earlier turn.
 */
class CareerStateTurnLatch {
    private var built: Boolean = false
    private var tracerFresh: Boolean = false

    /**
     * Arm for a new main-screen decision turn: the next [shouldBuild] returns true. Called from the
     * action-completion boundary when an action advanced gameplay, so the cadence does not depend on
     * date OCR or on the tracer opening a window. Also clears the tracer-fresh flag, because the new
     * turn's tracer window has not opened yet.
     */
    fun armForNewTurn() {
        built = false
        tracerFresh = false
    }

    /**
     * Arm only when [advanced] is true. For action outcomes that advance the turn on some paths but
     * not others - a RACE that runs vs. one aborted by the consecutive-race warning - pass the real
     * runtime outcome so a non-advancing outcome leaves the latch consumed and cannot produce a
     * duplicate same-turn snapshot on the next pass. A no-op when [advanced] is false.
     */
    fun armForNewTurnIf(advanced: Boolean) {
        if (advanced) armForNewTurn()
    }

    /** Record that the DecisionTracer opened its window this turn (its `startTurn` ran), so the shadow comparison has fresh turn-open evidence. */
    fun markTracerWindowOpened() {
        tracerFresh = true
    }

    /** True exactly once per armed turn - the first call after [armForNewTurn]; false on same-turn re-ticks until re-armed. */
    fun shouldBuild(): Boolean {
        if (built) return false
        built = true
        return true
    }

    /** Whether a fresh DecisionTracer turn window exists for this turn. False after [armForNewTurn] until [markTracerWindowOpened]; the shadow comparison must not run against stale evidence when this is false. */
    fun tracerWindowFresh(): Boolean = tracerFresh
}

/**
 * Per-career monotonic decision-sequence holder: the join authority between the `career_state` and
 * `decision_trace` streams. Owned by a single Campaign (one career), so a fresh career/resume starts a
 * new instance at seq 1 under a new career token, keeping `careerToken + seq` unique.
 *
 * Lifecycle, mirroring the build/emit ordering in `Campaign.handleMainScreen`:
 * - [allocate] once per consumed CareerState build opportunity: advances the counter, returns the new
 *   seq, and clears [current] (so an unbuilt/failed turn carries no seq).
 * - [retain] only when the build succeeded: pins [current] to that seq.
 * - [current] read at trace-emit time. Emit runs AFTER the action re-armed the turn latch for the next
 *   turn, but the latch is a separate object; nothing between [retain] and emit touches [current], so
 *   the trace stamps the current turn's seq, never the next turn's or a stale one.
 *
 * The counter advances on every [allocate] even when the build later fails, so seq N is never reused for
 * a later turn - a gap after a swallowed build is honest and keeps joins unambiguous. Pure and testable.
 */
class CareerStateDecisionSequence {
    private var counter: Int = 0
    private var current: Int? = null

    /** Consume a build opportunity: advance the counter, clear [current], and return the new seq. */
    fun allocate(): Int {
        current = null
        return ++counter
    }

    /** Pin [current] to [seq] after a successful build, so emit stamps this turn's seq. */
    fun retain(seq: Int) {
        current = seq
    }

    /** The seq retained for the current turn, or null when no CareerState was built/retained this turn. */
    fun current(): Int? = current
}

/**
 * Pure builder for [CareerState]. Reads the given live objects' fields and returns an immutable
 * snapshot with defensive copies. It takes no `Context`, `ImageUtils`, or any OCR/screenshot handle,
 * so by construction it cannot read the screen or influence gameplay; it is exercised directly by the
 * unit tests over synthetic `Trainee`/`GameDate` instances.
 */
object CareerStateBuilder {
    /**
     * Build the identity block, reusing [buildCareerFinalizeToken] and the finalized corpus's
     * preset/queue-run fallback semantics: the token's trainee component is the applied preset when
     * one is set, else the live trainee name, and the queue run is treated as absent when not > 0.
     */
    fun buildIdentity(
        scenario: String,
        traineeName: String,
        presetRaw: String,
        queueRunRaw: Int,
        careerNonce: String,
        configFingerprint: String,
    ): CareerIdentity {
        val queueRun: Int? = queueRunRaw.takeIf { it > 0 }
        val traineeIdentity: String = presetRaw.ifEmpty { traineeName }
        return CareerIdentity(
            careerToken = buildCareerFinalizeToken(traineeIdentity, scenario, queueRun, careerNonce),
            scenario = scenario,
            trainee = traineeName,
            preset = presetRaw.ifEmpty { null },
            queueRun = queueRun,
            configFingerprint = configFingerprint,
        )
    }

    /**
     * Build the full snapshot from live sources.
     *
     * @param identity Already-derived identity (see [buildIdentity]).
     * @param date Live game date; copied by value, never retained.
     * @param trainee Live trainee; only fields are read and mutable collections are copied.
     * @param mandatoryRaceDay / @param scheduledRaceDay / @param goalRibbonDay The cached race-day flags this turn.
     * @param scenario The scenario payload from the subclass hook, or null.
     */
    fun build(
        identity: CareerIdentity,
        date: GameDate,
        trainee: Trainee,
        mandatoryRaceDay: Boolean,
        scheduledRaceDay: Boolean,
        goalRibbonDay: Boolean,
        scenario: ScenarioState?,
    ): CareerState {
        val observed: Boolean = date.dayObserved
        val dateState =
            DateState(
                observedTurn = if (observed) date.day else null,
                year = if (observed) date.year else null,
                month = if (observed) date.month else null,
                phase = if (observed) date.phase else null,
                dayObserved = observed,
            )

        val condition =
            ConditionState(
                energy = trainee.energy,
                mood = trainee.mood,
                negativeStatuses = trainee.currentNegativeStatuses.toList(),
                positiveStatuses = trainee.currentPositiveStatuses.toList(),
            )

        // Gate each numeric group on its existing read flag; a group that was never read stays null so
        // its constructor default (-1 / 120 / all-G) is never promoted to observed truth.
        val stats: StatState? = if (trainee.bHasUpdatedStats) StatState(trainee.stats.asMap()) else null
        val skillPoints: Int? = if (trainee.bHasUpdatedSkillPoints) trainee.skillPoints else null
        val aptitudes: AptitudeState? =
            if (trainee.bHasUpdatedAptitudes) {
                AptitudeState(
                    surface = trainee.trackSurfaceAptitudes.toMap(),
                    distance = trainee.trackDistanceAptitudes.toMap(),
                    runningStyle = trainee.runningStyleAptitudes.toMap(),
                )
            } else {
                null
            }

        val race =
            RaceContext(
                mandatoryRaceDay = mandatoryRaceDay,
                scheduledRaceDay = scheduledRaceDay,
                goalRibbonDay = goalRibbonDay,
            )

        val provenance =
            CareerStateProvenance(
                identityInputs = StateProvenance.CONFIGURED,
                derivedIdentity = StateProvenance.DERIVED,
                date = if (observed) StateProvenance.OBSERVED else StateProvenance.UNREAD,
                condition = StateProvenance.OBSERVED,
                stats = if (stats != null) StateProvenance.OBSERVED else StateProvenance.UNREAD,
                aptitudes = if (aptitudes != null) StateProvenance.OBSERVED else StateProvenance.UNREAD,
                race = StateProvenance.OBSERVED,
                scenario = if (scenario != null) StateProvenance.OBSERVED else StateProvenance.UNREAD,
            )

        return CareerState(
            identity = identity,
            date = dateState,
            condition = condition,
            stats = stats,
            skillPoints = skillPoints,
            aptitudes = aptitudes,
            race = race,
            scenario = scenario,
            provenance = provenance,
        )
    }
}

/** Result of comparing a pre-decision [CareerState] against the DecisionTracer turn-open snapshot. */
data class ShadowComparison(
    /** Fields that cannot legitimately change between turn-open and pre-decision but did - a real defect. */
    val strictMismatches: List<String>,
    /** Fields the current turn legitimately mutates between the two boundaries (skill buys, item use). Not defects. */
    val expectedDrift: List<String>,
)

/**
 * Pure debug-shadow comparison between a pre-decision [CareerState] and the earlier DecisionTracer
 * turn-open [DecisionTracer.StateSnapshot]. The two snapshots are taken at different points in the
 * turn, so only fields that cannot change between them are compared strictly (the five stats, the
 * observed turn, and read-flag no-regression). Fields the turn legitimately mutates in between - skill
 * points after `performGlobalChecks()` skill buys, energy/mood after `onMainScreenEntry()` item use -
 * are classified as expected drift, never strict mismatches. Extracted so it is testable without a
 * live `Campaign`.
 */
object CareerStateShadow {
    fun compare(careerState: CareerState, open: DecisionTracer.StateSnapshot, openObservedTurn: Int?): ShadowComparison {
        val mismatches = mutableListOf<String>()
        val drift = mutableListOf<String>()

        val csStats = careerState.stats
        if (csStats != null && open.statsObserved) {
            for (stat in StatName.entries) {
                val a = open.stats[stat]
                val b = csStats.stats[stat]
                if (a != null && b != null && a != b) mismatches.add("stat.$stat $a->$b")
            }
        }
        val csTurn = careerState.date.observedTurn
        if (openObservedTurn != null && csTurn != null && openObservedTurn != csTurn) mismatches.add("turn $openObservedTurn->$csTurn")
        if (open.statsObserved && careerState.stats == null) mismatches.add("statsObserved regressed")
        if (open.skillPointsObserved && careerState.skillPoints == null) mismatches.add("skillPointsObserved regressed")
        if (open.aptitudesObserved && careerState.aptitudes == null) mismatches.add("aptitudesObserved regressed")

        val csSkill = careerState.skillPoints
        if (open.skillPointsObserved && csSkill != null && open.skillPoints != csSkill) drift.add("skillPts ${open.skillPoints}->$csSkill")
        if (open.energy != careerState.condition.energy) drift.add("energy ${open.energy}->${careerState.condition.energy}")
        if (open.mood != careerState.condition.mood) drift.add("mood ${open.mood}->${careerState.condition.mood}")

        return ShadowComparison(mismatches, drift)
    }
}
