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
 * Immutable snapshot of the state the decision engine sees at the main-screen turn boundary, built once per turn
 * just before `decideNextAction()`. It copies live state and triggers no OCR, screenshot, navigation, scoring or
 * tap. Shadow-only: nothing in the gameplay path reads it.
 *
 * A group is exposed as observed only when an existing read flag proves it was read this career; otherwise it is
 * null rather than its constructor default, so phantom turn-1 / stat `-1` / all-`G` values cannot reappear.
 * Raw fan count (no per-field read flag) and Grand Concert performance-point balances (unknown at this
 * boundary) are deliberately absent.
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

/** Availability class for a [CareerState] group, grounded only in the boolean read flags the runtime keeps; no confidence or per-turn freshness is invented. */
enum class StateProvenance {
    OBSERVED,

    UNREAD,

    /** A configured identity input (scenario, trainee, applied preset, queue run). */
    CONFIGURED,

    /** Derived from configured inputs plus the career nonce (career token, config fingerprint). */
    DERIVED,
}

/**
 * Decision-time career identity; reuses the finalize token machinery so a snapshot joins `career_finalize` and
 * `decision_trace` rows on [careerToken]. [scenario]/[trainee]/[preset]/[queueRun] are configured;
 * [careerToken]/[configFingerprint] are derived from them plus the nonce.
 */
data class CareerIdentity(
    val careerToken: String,
    val scenario: String,
    val trainee: String,
    val preset: String?,
    val queueRun: Int?,
    val configFingerprint: String,
)

/** The game date by value; components are null when [dayObserved] is false, so an unread default day is never mistaken for a real turn. */
data class DateState(
    val observedTurn: Int?,
    val year: DateYear?,
    val month: DateMonth?,
    val phase: DatePhase?,
    val dayObserved: Boolean,
)

/** Decision-time condition facts refreshed every turn start; they have no read flag, so they are best-effort observed. */
data class ConditionState(
    val energy: Int,
    val mood: Mood,
    val negativeStatuses: List<String>,
    val positiveStatuses: List<String>,
)

data class StatState(
    val stats: Map<StatName, Int>,
)

/** The trainee's aptitude groups by value. Derived preferences (distance/style) are not frozen: they are recomputable from these maps. */
data class AptitudeState(
    val surface: Map<TrackSurface, Aptitude>,
    val distance: Map<TrackDistance, Aptitude>,
    val runningStyle: Map<RunningStyle, Aptitude>,
)

/** Pre-decision race-day facts: the three cached flags `decideNextAction()` consumes. The later `RaceEligibility` result stays DecisionTrace evidence. */
data class RaceContext(
    val mandatoryRaceDay: Boolean,
    val scheduledRaceDay: Boolean,
    val goalRibbonDay: Boolean,
)

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

/** Scenario-specific decision state; a scenario subclass builds its payload in `scenarioStateSnapshot()` so no private scenario field is widened. */
sealed interface ScenarioState

/** [consecutiveRaceCountObserved] is Trackblazer's `counterUpdatedByOCR` flag: the count is a carried value until it is true. */
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
        /** Copies [inventory] so a later mutation of the live `currentInventory` cannot change the snapshot. */
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

/** Grand Concert state at turn open. Performance-point balances are absent: they are read only during training-screen analysis, so exposing them here would fabricate state. */
data class GrandConcertState(
    val songsBoughtThisCycle: Int,
    val songsBoughtThisCareer: Int,
    val lastConcertBoundary: Int,
) : ScenarioState

/**
 * Once-per-turn latch for the shadow CareerState build, plus a freshness flag for the debug comparison. Cadence
 * follows the action-completion lifecycle, not the DecisionTracer window: [armForNewTurn] runs when an action
 * advances to a new decision turn and [shouldBuild] is true for the first pre-decision pass, so a turn still
 * builds when date OCR failed or the tracer opened no window, and a same-turn re-tick (RECOVER_MOOD spin, failed
 * outing) does not rebuild. [tracerWindowFresh] tracks whether the tracer opened its window this turn, so the
 * comparison never uses a stale tracer snapshot.
 */
class CareerStateTurnLatch {
    private var built: Boolean = false
    private var tracerFresh: Boolean = false

    /** Arms the next [shouldBuild]; also clears the tracer-fresh flag, since the new turn's tracer window has not opened. */
    fun armForNewTurn() {
        built = false
        tracerFresh = false
    }

    /** Arms only when [advanced]: a RACE that runs advances the turn but one aborted by the consecutive-race warning does not, and must not yield a duplicate same-turn snapshot. */
    fun armForNewTurnIf(advanced: Boolean) {
        if (advanced) armForNewTurn()
    }

    fun markTracerWindowOpened() {
        tracerFresh = true
    }

    fun shouldBuild(): Boolean {
        if (built) return false
        built = true
        return true
    }

    /** False after [armForNewTurn] until [markTracerWindowOpened]; the shadow comparison must not run on stale evidence. */
    fun tracerWindowFresh(): Boolean = tracerFresh
}

/**
 * Per-career monotonic decision-sequence holder: the join authority between the `career_state` and `decision_trace`
 * streams, so `careerToken + seq` is unique. [allocate] advances the counter on every consumed build opportunity
 * and clears [current]; [retain] pins it only on a successful build. Seq N is never reused after a failed build,
 * so a gap is honest and joins stay unambiguous. Trace emit runs after the action re-armed the turn latch, but
 * nothing between [retain] and emit touches [current], so the trace stamps the current turn's seq.
 */
class CareerStateDecisionSequence {
    private var counter: Int = 0
    private var current: Int? = null

    fun allocate(): Int {
        current = null
        return ++counter
    }

    fun retain(seq: Int) {
        current = seq
    }

    fun current(): Int? = current
}

/** Pure builder for [CareerState]: takes no Context, ImageUtils or OCR handle, so by construction it cannot read the screen or influence gameplay. */
object CareerStateBuilder {
    /** Reuses [buildCareerFinalizeToken] and the finalized corpus's fallback semantics: the trainee component is the applied preset when set, else the live trainee name; the queue run is absent when not > 0. */
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

        // Gate each group on its read flag so a constructor default (-1 / 120 / all-G) is never promoted to observed truth.
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

data class ShadowComparison(
    /** Fields that cannot legitimately change between turn-open and pre-decision but did - a real defect. */
    val strictMismatches: List<String>,
    /** Fields the current turn legitimately mutates between the two boundaries (skill buys, item use). Not defects. */
    val expectedDrift: List<String>,
)

/**
 * Debug-shadow comparison of a pre-decision [CareerState] against the earlier DecisionTracer turn-open snapshot.
 * Only fields that cannot change between them are compared strictly (five stats, observed turn, read-flag
 * no-regression); skill points and energy/mood legitimately drift (skill buys, item use) and count as expected drift.
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
