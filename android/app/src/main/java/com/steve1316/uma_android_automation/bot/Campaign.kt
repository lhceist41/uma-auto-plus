package com.steve1316.uma_android_automation.bot

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.DiscordUtils
import com.steve1316.automation_library.utils.ImageUtils.ScaleConfidenceResult
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SQLiteSettingsManager
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.BuildConfig
import com.steve1316.uma_android_automation.CareerLaunchNavigator
import com.steve1316.uma_android_automation.SessionTally
import com.steve1316.uma_android_automation.StartModule
import com.steve1316.uma_android_automation.VeteranInspirationReader
import com.steve1316.uma_android_automation.VeteranInspirationScanner
import com.steve1316.uma_android_automation.VeteranProtectionScanner
import com.steve1316.uma_android_automation.VeteranRosterReader
import com.steve1316.uma_android_automation.VeteranRosterScanner
import com.steve1316.uma_android_automation.careerResultAtEnd
import com.steve1316.uma_android_automation.components.ButtonBack
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonCareerEndSkills
import com.steve1316.uma_android_automation.components.ButtonChangeRunningStyle
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.ButtonCloseWide
import com.steve1316.uma_android_automation.components.ButtonCompleteCareer
import com.steve1316.uma_android_automation.components.ButtonConfirm
import com.steve1316.uma_android_automation.components.ButtonCraneGame
import com.steve1316.uma_android_automation.components.ButtonCraneGameOk
import com.steve1316.uma_android_automation.components.ButtonDetails
import com.steve1316.uma_android_automation.components.ButtonEventProgressChevron
import com.steve1316.uma_android_automation.components.ButtonHomeFansInfo
import com.steve1316.uma_android_automation.components.ButtonHomeFullStats
import com.steve1316.uma_android_automation.components.ButtonInfirmary
import com.steve1316.uma_android_automation.components.ButtonInheritance
import com.steve1316.uma_android_automation.components.ButtonNext
import com.steve1316.uma_android_automation.components.ButtonNextRaceEnd
import com.steve1316.uma_android_automation.components.ButtonOk
import com.steve1316.uma_android_automation.components.ButtonRace
import com.steve1316.uma_android_automation.components.ButtonRaceExclamation
import com.steve1316.uma_android_automation.components.ButtonRaceStrategyEnd
import com.steve1316.uma_android_automation.components.ButtonRaceStrategyFront
import com.steve1316.uma_android_automation.components.ButtonRaceStrategyLate
import com.steve1316.uma_android_automation.components.ButtonRaceStrategyPace
import com.steve1316.uma_android_automation.components.ButtonRecreation
import com.steve1316.uma_android_automation.components.ButtonRest
import com.steve1316.uma_android_automation.components.ButtonRestAndRecreation
import com.steve1316.uma_android_automation.components.ButtonShop
import com.steve1316.uma_android_automation.components.ButtonSkills
import com.steve1316.uma_android_automation.components.ButtonSkip
import com.steve1316.uma_android_automation.components.ButtonSkipOff
import com.steve1316.uma_android_automation.components.ButtonSkipOn
import com.steve1316.uma_android_automation.components.ButtonTraining
import com.steve1316.uma_android_automation.components.ButtonTryAgain
import com.steve1316.uma_android_automation.components.ButtonUnityCupRace
import com.steve1316.uma_android_automation.components.DialogInterface
import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.components.IconGoalRibbon
import com.steve1316.uma_android_automation.components.IconInfirmaryEventHeader
import com.steve1316.uma_android_automation.components.IconOneFreePerDayTooltip
import com.steve1316.uma_android_automation.components.IconRaceDayRibbon
import com.steve1316.uma_android_automation.components.IconRaceNotEnoughFans
import com.steve1316.uma_android_automation.components.IconRecreationDate
import com.steve1316.uma_android_automation.components.IconRecreationDateOpen
import com.steve1316.uma_android_automation.components.IconTazuna
import com.steve1316.uma_android_automation.components.IconTrainingEventHorseshoe
import com.steve1316.uma_android_automation.components.LabelEnergy
import com.steve1316.uma_android_automation.components.LabelEventProgress
import com.steve1316.uma_android_automation.components.LabelOrdinaryCuties
import com.steve1316.uma_android_automation.components.LabelRecreationDateComplete
import com.steve1316.uma_android_automation.components.LabelRecreationUmamusume
import com.steve1316.uma_android_automation.components.LabelScheduledRace
import com.steve1316.uma_android_automation.components.LabelStatTableHeaderSkillPoints
import com.steve1316.uma_android_automation.components.LabelUmamusumeClassFans
import com.steve1316.uma_android_automation.types.Aptitude
import com.steve1316.uma_android_automation.types.BoundingBox
import com.steve1316.uma_android_automation.types.DateMonth
import com.steve1316.uma_android_automation.types.DatePhase
import com.steve1316.uma_android_automation.types.DateYear
import com.steve1316.uma_android_automation.types.FanCountClass
import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.Mood
import com.steve1316.uma_android_automation.types.RunningStyle
import com.steve1316.uma_android_automation.types.SkillList
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.types.TrackDistance
import com.steve1316.uma_android_automation.types.TrackSurface
import com.steve1316.uma_android_automation.types.Trainee
import com.steve1316.uma_android_automation.utils.OutcomeCorpus
import com.steve1316.uma_android_automation.utils.InCareerSkipFix
import com.steve1316.uma_android_automation.utils.PersistentSkipState
import com.steve1316.uma_android_automation.utils.PersistentSkipStateLog
import com.steve1316.uma_android_automation.utils.SKIP_PILL_CENTRE_X_FRACTION
import com.steve1316.uma_android_automation.utils.SKIP_PILL_CENTRE_Y_FRACTION
import com.steve1316.uma_android_automation.utils.SkipFixOutcome
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.PagedHelpGeometry
import com.steve1316.uma_android_automation.utils.miscNextBackSwapStreak
import com.steve1316.uma_android_automation.utils.pagedHelpDialogPresent
import com.steve1316.uma_android_automation.utils.skipOffPillByColour
import com.steve1316.uma_android_automation.utils.ProgressEvent
import com.steve1316.uma_android_automation.utils.ProgressNotification
import com.steve1316.uma_android_automation.utils.ProgressTracker
import com.steve1316.uma_android_automation.utils.StatReadPlausibility
import com.steve1316.uma_android_automation.utils.StatusBoard
import com.steve1316.uma_android_automation.utils.pillVisible
import com.steve1316.uma_android_automation.utils.classifyPersistentSkip
import com.steve1316.uma_android_automation.utils.ScrollList
import com.steve1316.uma_android_automation.utils.TraineeNameMatcher
import com.steve1316.uma_android_automation.utils.VeteranIdentityCatalog
import com.steve1316.uma_scoring.RankAptitudes
import com.steve1316.uma_scoring.SkillScoreInput
import com.steve1316.uma_scoring.estimateRank
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.core.Point
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Defines an exception for breaking from the main loop when conditions are met.
 *
 * @param message A helpful message describing what breakpoint we hit.
 */
class CampaignBreakpointException(message: String) : Exception(message)

/** Defines an enum representing the various actions the bot can take when at the Main screen.
 */
enum class MainScreenAction {
    /** Indicates a racing action. */
    RACE,

    /** Indicates a training action. */
    TRAIN,

    /** Indicates a resting action. */
    REST,

    /** Indicates a mood recovery action. */
    RECOVER_MOOD,

    /** Indicates a scheduled support-card recreation ("dating") outing. */
    DATE,

    /** Indicates no action. */
    NONE,
}

/** [turnAdvanced] lets the RACE branch rearm the shadow CareerState latch when the fallback trained or recovered instead of just backing out. */
data class RaceFallbackOutcome(
    val shouldStopForMandatoryRace: Boolean,
    val turnAdvanced: Boolean,
)

/** The Campaign subclass is fixed when Game is built, so a slot whose scenario differs from the running one cannot be repaired mid-career. */
internal fun rotationSlotScenarioMismatch(slotScenario: String, runningScenario: String): Boolean =
    slotScenario.isNotEmpty() && GrandConcertScenario.normalizeScenarioKey(slotScenario) != runningScenario

// Position of the "Group Event Progress X/Y" text relative to the right edge of the matched "Group Event Progress" pill ([LabelEventProgress]), for OCR. Tune if the "GroupEventProgress" debug crop misses the digits.
private const val GROUP_PROGRESS_GAP_X = 15
private const val GROUP_PROGRESS_WIDTH = 120

/**
 * Defines the base campaign class that contains all shared logic for campaign automation.
 *
 * Campaign-specific logic should be implemented in subclasses by overriding the appropriate methods.
 *
 * @property game The [Game] instance for interacting with the game state.
 */
abstract class Campaign(game: Game) : Task(game) {
    /** Required instance of the Racing class. */
    protected var racing: Racing = Racing(game, this)

    /** Required instance of the SkillPlan class. */
    protected var skillPlan: SkillPlan = SkillPlan(game, this)

    private val skipStateLog = PersistentSkipStateLog(TAG, "cutscene")

    /** A Campaign is one career, so a pill that would not leave Off is left alone until the next one. */
    private val skipFix = InCareerSkipFix()

    /** Lazy: constructing a SkillList generates the full skill entries map from the database. */
    private val careerEndScreenChecker: SkillList by lazy { SkillList(game, this) }

    /** Stops the End-screen and Learn-screen handlers from running the careerComplete plan twice. */
    private var bCareerEndSkillsHandled: Boolean = false

    /** Set on a lost mandatory race the game will not let us retry, or at the career end when it came before the Finale season ([endedBeforeFinale]):
     * a goal race lost again after the last retry shows no Try Again, and fan / Result-Pts checkpoint misses are not observable at their trigger. */
    private var careerForceEnded: Boolean = false

    private var forceEndReason: String? = null

    /** Idempotent: keeps the first reason so a later dialog redraw cannot overwrite it. */
    private fun markCareerForceEnded(reason: String) {
        if (!careerForceEnded) {
            careerForceEnded = true
            forceEndReason = reason
        }
    }

    /** Finale-race results for the ledger win/lose signal: finaleRaces == 0 never reached a finale, finaleRaces1st < finaleRaces lost one. */
    private var finaleRaces: Int = 0
    private var finaleRaces1st: Int = 0

    fun noteFinaleRaceResult(won: Boolean) {
        finaleRaces++
        if (won) finaleRaces1st++
    }

    /** Whether FINALE-graded races show the 1st-place banner [Racing.finalizeRaceResults] reads. Only URA Finale is verified: Trackblazer's Climax
     * races use a different result UI and would mislabel a good career FINALE_LOST. */
    open val capturesFinaleWins: Boolean = false

    private var careerEndExitAttempts: Int = 0

    private val maxCareerEndExitAttempts: Int = 5

    /** The Learn screen can take several seconds to load, so entry is screen-confirmed rather than a fixed wait (a slow career-end once dropped 544 SP).
     * Bounded so an abnormal Learn button completes the career instead of looping. */
    private var careerEndEntryAttempts: Int = 0

    private val maxCareerEndEntryAttempts: Int = 6

    /** One controlled re-run of the careerComplete plan per career; the second large-balance verdict is terminal. */
    private var careerEndSpendRetryUsed: Boolean = false

    private var careerEndLastKnownStats: List<String> = emptyList()

    /** Fallback when running outside a real career task (debug harness, helper instance); throwaway Campaigns must never mint the primary identity. */
    private val fallbackFinalizeNonce: String = java.util.UUID.randomUUID().toString().substring(0, 8)

    private val careerFinalizeNonce: String
        get() = CareerFinalizeGate.context?.nonce ?: fallbackFinalizeNonce

    /** A long streak means the back-press changes nothing (10+ minutes observed on a wedged career-end skill screen). */
    private var consecutiveMiscBackPresses: Int = 0

    /** Stop bound, ~75s at the observed tick rate. */
    private val maxConsecutiveMiscBackPresses: Int = 25

    private var bMiscBackPressedThisTick: Boolean = false

    /** Each Next undoes a Back, so a Next/Back ping-pong never reaches the Back-press bound. Stop bound, ~80s at the observed tick rate. */
    private var miscNextBackSwaps: Int = 0
    private val maxMiscNextBackSwaps: Int = 12
    private var lastMiscStepWasNext: Boolean? = null

    private var pagedHelpCloseTaps: Int = 0
    private val maxPagedHelpCloseTaps: Int = 5

    /** A tick without a misc Next, Back or help Close reached some other screen, which ends both streaks. */
    private var bMiscStepTakenLastTick: Boolean = false

    /** Required instance of the Trainee class. */
    val trainee: Trainee = Trainee()

    /** Reassignable so [reloadTraineeConfig] can rebuild it onto a resynced preset; Training and TrainingEvent cache their config at construction. */
    var training: Training = Training(game, this)

    protected var trainingEvent: TrainingEvent = TrainingEvent(game, this)

    /** Rebuilds [training] and [trainingEvent] after a rotation resync: both cache their config at construction, so a mid-flight snapshot swap
     * would otherwise leave the career on the wrong preset. */
    private fun reloadTraineeConfig() {
        // The Trainee and this Campaign outlive the swap, so re-read every preset-owned setting they cache.
        trainee.setStatTargetsByDistances()
        mustRestBeforeSummer = readMustRestBeforeSummer()
        moodFloor = readMoodFloor()
        skillSpendObjective = readSkillSpendObjective()
        resolvedSkillThreshold = resolveAndLogSkillThreshold()
        training = Training(game, this)
        trainingEvent = TrainingEvent(game, this)
        // Racing and SkillPlan also cache career-shaping config at construction. Reconstruction resets their per-career heuristics (e.g. the
        // consecutive-race counter), acceptable at the turn-1/re-entry point where the resync fires.
        racing = Racing(game, this)
        skillPlan = SkillPlan(game, this)
        // Refresh so the outcome record fingerprints the config the career now runs on.
        outcomeConfigSnapshot = buildOutcomeConfigSnapshot()
    }

    /** Null when the slot has no stored scenario, so callers skip the comparison instead of warning on noise. */
    private fun rotationSlotFingerprint(index: Int): String? {
        if (index < 0) return null
        val slotScenario = SettingsHelper.getStringSetting("rot${index}_general", "scenario").ifEmpty { return null }
        return outcomeConfigFingerprint(BuildConfig.VERSION_NAME, buildOutcomeConfigSnapshot("rot${index}_", slotScenario))
    }

    /** The explicit OK line is the observable proof a career runs the intended preset; silence proves nothing in a rotated-away log. */
    private fun warnOnTraineeConfigDrift(slotIndex: Int, context: String) {
        // Compared on its own: the fingerprint does not cover the scenario, and presets often match across scenarios.
        val slotScenario = if (slotIndex >= 0) SettingsHelper.getStringSetting("rot${slotIndex}_general", "scenario", "") else ""
        if (rotationSlotScenarioMismatch(slotScenario, game.scenario)) stopOnSlotScenarioMismatch(slotIndex, slotScenario)
        val slotFp = rotationSlotFingerprint(slotIndex) ?: return
        val liveFp = outcomeConfigFingerprint(BuildConfig.VERSION_NAME, buildOutcomeConfigSnapshot())
        if (liveFp == slotFp) {
            MessageLog.i(TAG, "[CONFIG_DRIFT] $context: live config fp=$liveFp matches rotation slot #${slotIndex + 1}'s snapshot. This career runs the intended preset.")
        } else {
            MessageLog.w(
                TAG,
                "[CONFIG_DRIFT] $context: live config fp=$liveFp does NOT match rotation slot #${slotIndex + 1}'s snapshot fp=$slotFp - " +
                    "this career may be running another preset's settings.",
            )
        }
    }

    /** Stops before any further tap: playing on would run the career under another scenario's campaign logic. */
    private fun stopOnSlotScenarioMismatch(slotIndex: Int, slotScenario: String): Nothing {
        val reason =
            "Stopped on a scenario mismatch: rotation slot #${slotIndex + 1} plays $slotScenario, but this run loaded ${game.scenario} settings. " +
                "The career is kept in the game."
        MessageLog.e(TAG, "[CONFIG_DRIFT] $reason")
        StartModule.queueStopKey = "SCENARIO_MISMATCH"
        StartModule.queueStopReason = reason
        StartModule.queueStopRequested = true
        throw InterruptedException(reason)
    }

    /** Required instance of the GameDate class. */
    var date: GameDate = GameDate(day = 1)

    /** Operator opt-in for the lightweight per-turn decision corpus (decision_trace + career_state), independent of Debug Mode. */
    private val recordDecisionData: Boolean = SettingsHelper.getBooleanSetting("misc", "recordDecisionData", true)

    /** A debug build or Debug Mode: enables the heavy bundle (Decision Report, fixture capture, shadow_advisor stream). */
    private val debugDiagnosticsEnabled: Boolean = com.steve1316.uma_android_automation.BuildConfig.DEBUG || game.debugMode

    /** Opted in or debug active; decision_trace and career_state share this gate so they stay joinable. */
    private val factualCorpusEnabled: Boolean = DecisionCorpusGate.factualCorpusEnabled(recordDecisionData, debugDiagnosticsEnabled)

    /** Non-null whenever the factual corpus records; the heavy Decision Report block is written only under [debugDiagnosticsEnabled]. */
    val decisionTracer: DecisionTracer? =
        if (factualCorpusEnabled) DecisionTracer(humanReportEnabled = DecisionCorpusGate.humanReportEnabled(debugDiagnosticsEnabled)) else null

    init {
        // emit() fires after the turn's action has executed, so a failure in this sink cannot reach the decision path.
        decisionTracer?.traceSink = { evidence -> appendDecisionTrace(evidence) }
    }

    /** Observational only. Stays on the debug gate even though the tracer records on the broader corpus gate; never influences a decision and
     * swallows its own failures. */
    val shadowAdvisorSink: com.steve1316.uma_android_automation.bot.shadowadvisor.ShadowAdvisorSink? =
        if (debugDiagnosticsEnabled) {
            com.steve1316.uma_android_automation.bot.shadowadvisor.ShadowAdvisorSink()
        } else {
            null
        }

    /** Retained so the Shadow Advisor sink can pair the serialized pre-decision state to this turn's decision_trace by seq. */
    private var retainedShadowCareerStateJson: String? = null
    private var retainedShadowCareerStateSeq: Int? = null

    /** Failures propagate to [DecisionTracer.emit], which swallows them. Identity is read at emit time so the trace joins the career_finalize and
     * career-outcome rows on `careerToken` and `fp`. */
    private fun appendDecisionTrace(evidence: TurnEvidence) {
        val preset: String = SettingsHelper.getStringSetting("general", "appliedPresetTrainee").trim()
        val traineeIdentity: String = preset.ifEmpty { trainee.name }
        val queueRun: Int = CareerFinalizeGate.context?.queueRun ?: SettingsHelper.getIntSetting("queueState", "currentRun", 0)
        val record =
            DecisionTrace.buildRecord(
                timestamp = System.currentTimeMillis(),
                evidence = evidence,
                app = BuildConfig.VERSION_NAME,
                fp = currentConfigFingerprint(),
                scenario = game.scenario,
                trainee = trainee.name,
                preset = preset,
                careerToken = buildCareerFinalizeToken(traineeIdentity, game.scenario, queueRun.takeIf { it > 0 }, careerFinalizeNonce),
                queueRun = queueRun.takeIf { it > 0 },
                // Joins this trace to its career_state record; null when no CareerState was built this turn.
                seq = careerStateSeq.current(),
                // Null unless a proven completion tail recorded a race this turn.
                enteredRace = pendingEnteredRace.current(),
            )
        OutcomeCorpus.append(game.myContext, record, OutcomeCorpus.DECISIONS_PATH, DecisionTrace.MAX_FILE_BYTES)

        // Shadow Advisor: invoked strictly after the factual append and isolated from the turn; record.toString() hands over an immutable copy.
        shadowAdvisorSink?.onDecisionTraceAppended(
            context = game.myContext,
            serializedTrace = record.toString(),
            traceSeq = careerStateSeq.current(),
            serializedState = retainedShadowCareerStateJson,
            retainedStateSeq = retainedShadowCareerStateSeq,
        )
    }

    /** Pure serialization plus [OutcomeCorpus.append]; a telemetry failure here can never change a turn. */
    private fun appendCareerState(careerState: CareerState, seq: Int) {
        val record = CareerStateSerializer.buildRecord(careerState, seq, System.currentTimeMillis())
        // Retained before the append so it is captured regardless of the write result.
        retainedShadowCareerStateJson = record.toString()
        retainedShadowCareerStateSeq = seq
        OutcomeCorpus.append(game.myContext, record, OutcomeCorpus.CAREER_STATE_PATH, CareerStateSerializer.MAX_FILE_BYTES)
    }

    /** Pure over live in-memory state: no OCR, screenshot, navigation or tap. Shadow-only: no decision path reads it. */
    protected fun buildCareerState(): CareerState {
        val presetRaw: String = SettingsHelper.getStringSetting("general", "appliedPresetTrainee").trim()
        val queueRunRaw: Int = CareerFinalizeGate.context?.queueRun ?: SettingsHelper.getIntSetting("queueState", "currentRun", 0)
        val identity =
            CareerStateBuilder.buildIdentity(
                scenario = game.scenario,
                traineeName = trainee.name,
                presetRaw = presetRaw,
                queueRunRaw = queueRunRaw,
                careerNonce = careerFinalizeNonce,
                configFingerprint = currentConfigFingerprint(),
            )
        return CareerStateBuilder.build(
            identity = identity,
            date = date,
            trainee = trainee,
            mandatoryRaceDay = cachedMandatoryRaceDay,
            scheduledRaceDay = cachedScheduledRaceDay,
            goalRibbonDay = cachedGoalRibbonDay,
            scenario = scenarioStateSnapshot(),
        )
    }

    /** Debug-only and non-fatal; emits one compact `[CAREER_STATE]` line. */
    private fun compareCareerStateToTracer(careerState: CareerState) {
        val turnLabel: String = careerState.date.observedTurn?.toString() ?: "?"
        val scenarioName = careerState.scenario?.let { it::class.simpleName } ?: "none"
        // Compare only when the tracer opened a window this turn: its startTurn is skipped when date OCR failed, and an earlier-turn snapshot would
        // manufacture a mismatch.
        val open = if (careerStateLatch.tracerWindowFresh()) decisionTracer?.turnEvidence()?.state else null
        if (open == null) {
            MessageLog.i(TAG, "[CAREER_STATE] turn=$turnLabel built compare=unavailable scenario=$scenarioName")
            return
        }
        val openTurn = decisionTracer?.currentTurnDate()?.let { if (it.dayObserved) it.day else null }
        val result = CareerStateShadow.compare(careerState, open, openTurn)
        if (result.strictMismatches.isNotEmpty()) {
            MessageLog.w(
                TAG,
                "[CAREER_STATE] turn=$turnLabel STRICT-MISMATCH ${result.strictMismatches.joinToString("; ")}" +
                    if (result.expectedDrift.isNotEmpty()) " | drift ${result.expectedDrift.joinToString(", ")}" else "",
            )
        } else {
            MessageLog.i(
                TAG,
                "[CAREER_STATE] turn=$turnLabel built strict=ok scenario=$scenarioName" +
                    if (result.expectedDrift.isNotEmpty()) " drift=[${result.expectedDrift.joinToString(", ")}]" else "",
            )
        }
    }

    /** Flag to track whether the bot should force Wit training during the pre-summer turn. */
    var bForcedWitTraining: Boolean = false

    /** Flag to track if the bot should force a specific target mood during recovery. */
    var forcedTargetMood: Mood? = null

    /** Recovers mood below this floor ("Normal", "Good", "Great"). "Great" guards trainees with single-option mood-trap events (e.g. Agnes Tachyon's
     * NHK Mile redirect), at the cost of more Recreation/Date turns. */
    protected var moodFloor: Mood = readMoodFloor()
        private set

    /** Unrecognized strings fall back to GOOD. */
    private fun readMoodFloor(): Mood =
        when (SettingsHelper.getStringSetting("training", "moodFloor", "Good").lowercase()) {
            "normal" -> Mood.NORMAL
            "great" -> Mood.GREAT
            else -> Mood.GOOD
        }

    /** Informational pre-career check that the preferred-distance/style aptitudes meet [deckValidationMinAptitude]; warns, never halts the run. */
    protected val enableDeckValidation: Boolean = SettingsHelper.getBooleanSetting("training", "enableDeckValidation", true)

    /** Captured at construction while THIS run's settings are live: a rotation switch rewrites the active settings between runs. A field added
     * here changes every fingerprint, deliberately starting new arms. */
    private var outcomeConfigSnapshot: Map<String, String> = buildOutcomeConfigSnapshot()

    /** With [categoryPrefix] (e.g. "rot2_") reads a rotation slot's stored snapshot over the identical key set, so the two fingerprints compare
     * directly for [CONFIG_DRIFT]. */
    private fun buildOutcomeConfigSnapshot(categoryPrefix: String = "", scenarioForKeys: String = game.scenario): Map<String, String> {
        val training = "${categoryPrefix}training"
        val racing = "${categoryPrefix}racing"
        val skills = "${categoryPrefix}skills"
        val scenarioOverrides = "${categoryPrefix}scenarioOverrides"
        val cfg =
            linkedMapOf(
                "statPrioritization" to SettingsHelper.getStringArraySetting(training, "statPrioritization").joinToString(","),
                "preferredDistanceOverride" to SettingsHelper.getStringSetting(training, "preferredDistanceOverride"),
                "maximumFailureChance" to SettingsHelper.getIntSetting(training, "maximumFailureChance").toString(),
                "focusOnSparkStatTarget" to SettingsHelper.getStringArraySetting(training, "focusOnSparkStatTarget").joinToString(","),
                "enableRainbowTrainingBonus" to SettingsHelper.getBooleanSetting(training, "enableRainbowTrainingBonus").toString(),
                "enablePrioritizeNearMaxFriendship" to SettingsHelper.getBooleanSetting(training, "enablePrioritizeNearMaxFriendship", true).toString(),
                "enableRiskyTraining" to SettingsHelper.getBooleanSetting(training, "enableRiskyTraining").toString(),
                "moodFloor" to SettingsHelper.getStringSetting(training, "moodFloor", "Good"),
                "enableFarmingFans" to SettingsHelper.getBooleanSetting(racing, "enableFarmingFans").toString(),
                "daysToRunExtraRaces" to SettingsHelper.getIntSetting(racing, "daysToRunExtraRaces").toString(),
                "minFansThreshold" to SettingsHelper.getIntSetting(racing, "minFansThreshold").toString(),
                "enableRacingPlan" to SettingsHelper.getBooleanSetting(racing, "enableRacingPlan").toString(),
                "enableMandatoryRacingPlan" to SettingsHelper.getBooleanSetting(racing, "enableMandatoryRacingPlan").toString(),
                "disableRaceRetries" to SettingsHelper.getBooleanSetting(racing, "disableRaceRetries").toString(),
                "skillPointCheck" to SettingsHelper.getIntSetting(skills, "skillPointCheck").toString(),
            )
        // Plan content, not just the flag, shapes how the career races, so it must split arms.
        val racingPlan = SettingsHelper.getStringSetting(racing, "racingPlan")
        cfg["racingPlanDigest"] = if (racingPlan.isEmpty()) "none" else shortSha1(racingPlan)
        if (scenarioForKeys == "Trackblazer") {
            cfg["trackblazerEnergyThreshold"] = SettingsHelper.getIntSetting(scenarioOverrides, "trackblazerEnergyThreshold", 40).toString()
            cfg["trackblazerConsecutiveRacesLimit"] = SettingsHelper.getIntSetting(scenarioOverrides, "trackblazerConsecutiveRacesLimit", 2).toString()
            cfg["trackblazerEnableIrregularTraining"] = SettingsHelper.getBooleanSetting(scenarioOverrides, "trackblazerEnableIrregularTraining", false).toString()
        }
        return cfg
    }

    /** "B" matches the in-game soft requirement for race-bonus uplift; "A" is the strict meta-deck floor. */
    private val deckValidationMinString: String = SettingsHelper.getStringSetting("training", "deckValidationMinAptitude", "B")

    protected val deckValidationMinAptitude: Aptitude = Aptitude.fromName(deckValidationMinString) ?: Aptitude.B

    private var bDeckValidationChecked: Boolean = false

    /** Independent of [bDeckValidationChecked]: the verify runs regardless of the deck-validation setting. */
    private var bRotationTraineeVerified: Boolean = false

    /** Jaro-Winkler floor for the rotation trainee verify; slightly lenient versus the navigator's 0.86 select threshold because a STOP halts the
     * whole unattended queue. */
    private val rotationVerifyMatchThreshold: Double = 0.85

    /** Drives [recoverFromUnknownScreen]'s escalation; reset when any known screen or dialog is handled. */
    private var consecutiveUnknownScreenCount: Int = 0

    /** A long streak means taps are not landing (MuMu's enabled-but-dispatch-dead mode); mirrors the 13/19/25 ladder of recoverFromUnknownScreen. */
    private var consecutiveDialogTicks: Int = 0

    /** A mid-career bounce to the outer lobby is re-entered in place; capped so dead gesture dispatch falls through to the stop instead of thrashing. */
    private var lobbyReentryAttempts: Int = 0

    /** True once in-career UI was seen. A bot started at the lobby must not drive the launch flow: a stale trainee target could silently start a
     * career for the wrong trainee. */
    private var careerScreenObservedThisTask: Boolean = false

    /** ~25 ticks is roughly a minute of being stuck. */
    private val maxUnknownScreenBeforeStop: Int = 25

    private val maxLobbyReentryAttempts: Int = 3

    /** MuMu can silently kill gesture dispatch while the service still reads "enabled", so blind taps no-op. These sit above the observed
     * healthy-transition max (~12 unknown cycles for a long event animation). */
    private val gestureRebindThresholds: Set<Int> = setOf(13, 19)

    /** Relaunch rung for a game-side soft-lock that is not gesture death: after both rebinds (13, 19), before the stop (25). */
    private val gameRestartThreshold: Int = 22

    /** Bounded so a relaunch that restores no driveable screen cannot storm; once spent, the stop is flagged game-unrecoverable so the queue pauses.
     * Each attempt gets a fresh unknown-screen budget so a cold boot can land. */
    private var gameRestartAttemptsThisEpisode: Int = 0

    private val maxGameRestartAttempts: Int = 3

    private var unresponsiveGameReopens: Int = 0

    /** Higher than [maxUnknownScreenBeforeStop]: a support-card chain event can run 20+ dialogue bubbles before its choices render. */
    private val maxCutsceneAdvanceBeforeStop: Int = 50

    /** Two rebind attempts in case gesture dispatch silently died and the dialogue never moves. */
    private val cutsceneRebindThresholds: Set<Int> = setOf(15, 30)

    /** The rebinds each stuck ladder asked for in its current episode, for its stop reason and the ledger. */
    private val dialogRebinds = RebindEpisode { SessionTally.accessibilityRebindsWithoutChange.incrementAndGet() }
    private val cutsceneRebinds = RebindEpisode { SessionTally.accessibilityRebindsWithoutChange.incrementAndGet() }
    private val unknownScreenRebinds = RebindEpisode { SessionTally.accessibilityRebindsWithoutChange.incrementAndGet() }

    /** Where the dialog-tick and cutscene ladders stop; the run's one stronger toggle moves this out once. */
    private val dialogTicksBeforeStop: Int = 25
    private var dialogStopAt: Int = dialogTicksBeforeStop
    private var cutsceneStopAt: Int = maxCutsceneAdvanceBeforeStop

    /** Whether the bot should attempt the crane game. On by default: it spends nothing. */
    protected val enableCraneGameAttempt: Boolean = SettingsHelper.getBooleanSetting("general", "enableCraneGameAttempt", true)

    /** Whether the bot should check for a skill point threshold. */
    protected val enableSkillPointCheck: Boolean = SettingsHelper.getBooleanSetting("skills", "enableSkillPointCheck")

    /** Whether the bot should stop at a specified date. */
    protected val enableStopAtDate: Boolean = SettingsHelper.getBooleanSetting("general", "enableStopAtDate")

    /** Whether the bot should stop before the final race. */
    protected val enableStopBeforeFinals: Boolean = SettingsHelper.getBooleanSetting("general", "enableStopBeforeFinals")

    /** Whether the bot must rest before Summer. */
    protected var mustRestBeforeSummer: Boolean = readMustRestBeforeSummer()
        private set

    private fun readMustRestBeforeSummer(): Boolean = SettingsHelper.getBooleanSetting("training", "mustRestBeforeSummer")

    /** Preset-owned; a preset that never set it reads back `rank`, which keeps both dynamic triggers inert. Manual mode ignores it. */
    internal var skillSpendObjective: SkillSpendObjective = readSkillSpendObjective()
        private set

    private fun readSkillSpendObjective(): SkillSpendObjective =
        SkillSpendObjective.fromPersisted(SettingsHelper.getStringSetting("skills", "skillSpendObjective", "rank"))

    /** Deliberately NOT part of the outcome-config fingerprint: resolving there would rotate every arm and flag phantom [CONFIG_DRIFT],
     * so the skill_spend records carry the resolved threshold/tier/reason instead. */
    internal var resolvedSkillThreshold: ResolvedSkillThreshold = resolveAndLogSkillThreshold()
        private set

    private fun resolveAndLogSkillThreshold(): ResolvedSkillThreshold =
        resolveSkillThresholdFromSettings().also {
            MessageLog.i(TAG, "[SKILLS] Skill spend policy: ${it.reason}; objective: ${skillSpendObjective.token()}.")
        }

    /** The number of skill points required to trigger a check. */
    protected val skillPointsRequired: Int
        get() = resolvedSkillThreshold.value

    /** Instance fields so a new career resets them; never move these to the companion. */

    /** Valid only while its `turn` equals `date.day`. */
    private var currentGoalSnapshot: GoalDeadlineSnapshot? = null

    /** Both CRITICAL_RACE arms share this key, so firing 2 turns out suppresses the 1-turn re-fire. */
    private var lastCriticalRaceTurnHandled: Int? = null

    internal val plannedSkillEvidence = PlannedSkillEvidenceStore()

    internal var activeTriggerContext: SkillTriggerContext? = null

    /** Empty on any failure so the matcher stays inert. */
    private val goalRaceNameCandidates: Set<String> by lazy { loadGoalRaceNameCandidates() }

    private fun loadGoalRaceNameCandidates(): Set<String> {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        return try {
            if (!settingsManager.isAvailable()) return emptySet()
            val database = settingsManager.readableDatabase ?: return emptySet()
            val names = mutableSetOf<String>()
            database.query("races", arrayOf("name", "nameFormatted"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { names.add(it) }
                    cursor.getString(1)?.takeIf { it.isNotBlank() }?.let { names.add(it) }
                }
            }
            names
        } catch (e: Exception) {
            MessageLog.w(TAG, "[SKILLS] Failed to load race names for goal matching: ${e.message}. Critical-race goal arm stays inert.")
            emptySet()
        } finally {
            settingsManager.close()
        }
    }

    /** The list of date strings at which the bot should stop. */
    protected val stopAtDates: List<String> =
        run {
            val json = SettingsHelper.getStringSetting("general", "stopAtDates", "[\"Senior January Early\"]")
            try {
                org.json.JSONArray(json).let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
            } catch (_: Exception) {
                listOf()
            }
        }

    /** Whether a recreation date event has been completed today. */
    protected var recreationDateCompleted: Boolean = false

    /** Whether the support-card recreation ("dating") schedule is enabled. */
    protected val enableDatingSchedule: Boolean = SettingsHelper.getBooleanSetting("general", "enableDatingSchedule", false)

    /** Whether a scheduled recreation outing that got pre-empted (a race, or recreation not yet available) should be made up on the next available turn. */
    protected val enableRecreationCatchUp: Boolean = SettingsHelper.getBooleanSetting("general", "enableRecreationCatchUp", true)

    /** The set of 1-indexed career turns (1-72) pinned for regular recreation outings. */
    protected val recreationTurns: Set<Int> =
        run {
            val json = SettingsHelper.getStringSetting("general", "recreationTurns", "[29,35,43,47,52,55,58]")
            try {
                org.json.JSONArray(json).let { arr -> (0 until arr.length()).map { arr.getInt(it) }.toSet() }
            } catch (_: Exception) {
                setOf()
            }
        }

    /** The single career turn pinned for the final outing / Pure Passion activation, or a non-positive value when unset. */
    protected val purePassionTurn: Int = SettingsHelper.getIntSetting("general", "purePassionTurn", -1)

    /** The total outings in the active support card's recreation chain (Team Sirius 7, Heirs to the Throne 5). */
    protected val recreationTotalOutings: Int = SettingsHelper.getIntSetting("general", "recreationTotalOutings", 7)

    /** Run-lifetime chain state the schedule keys on. Distinct from [recreationDateCompleted], which means "handled TODAY" and resets every turn
     * (load-bearing for the recovery paths); upstream overloads one flag for both. */
    protected var recreationChainComplete: Boolean = false

    /** The number of recreation outings actually started this run. Used to hold the final outing for the Pure Passion turn. */
    protected var recreationOutingsStarted: Int = 0

    /** The group-event chain length as last read from the game's "X/Y" progress, or the configured fallback until the partner dialog is first read. */
    protected var recreationTotalOutingsKnown: Int = recreationTotalOutings

    /** Backing out does not advance the game turn, so without this latch the decision loop would re-choose DATE and reopen the same dialog forever. */
    protected var recreationAttemptFailedThisTurn: Boolean = false

    /** The turn number when the stop-at-date check first started. */
    protected var stopAtDateInitialTurnNumber: Int = -1

    /** The turn number when the pre-finals stop check first started. */
    protected var stopBeforeFinalsInitialTurnNumber: Int = -1

    /** Flag indicating if the bot needs to check its fan count. */
    protected var bNeedToCheckFans: Boolean = true

    /** Flag indicating if the bot has already tried checking fans today. */
    protected var bHasTriedCheckingFansToday: Boolean = false

    /** Flag indicating if the skill point threshold has been handled.
     * This is necessary since the user may have enabled the skill point check
     * skill spending plan. If their plan ends up not purchasing many skills,
     * then it is possible that we could get stuck in a loop of hitting the
     * skill point threshold and attempting to buy skills every single turn.
     * To resolve this, we only allow the skill point check to be handled
     * once per run.
     */
    protected var bHasHandledSkillPointCheck: Boolean = false

    protected var skillPointCheckAttempts: Int = 0

    protected val skillPointCheckMaxAttempts: Int = 3

    /** Flag indicating if the pre-finals check has been handled. */
    protected var bHasHandledPreFinalsCheck: Boolean = false

    protected var preFinalsCheckAttempts: Int = 0

    protected val preFinalsCheckMaxAttempts: Int = 3

    /** Flag indicating if the bot has checked for a maiden race today. */
    var bHasCheckedForMaidenRaceToday: Boolean = false

    /** Flag indicating if the date has been checked during the current turn.
     * This is necessary to prevent redundant date checks when no game-advancing action was taken.
     * Reset to false when training, resting, racing, or other game-advancing actions complete.
     */
    protected var bHasCheckedDateThisTurn: Boolean = false

    /** Computed once at the top of [handleMainScreen] and reused, avoiding three template-match scans per turn; reset on every fresh turn. */
    protected var cachedScheduledRaceDay: Boolean = false
    protected var cachedMandatoryRaceDay: Boolean = false

    /** Separate from [cachedMandatoryRaceDay]: the goal ribbon stays visible for any active objective, so it must not feed the forced-RACE branch.
     * Backs only the Trackblazer irregular-training gate, where over-blocking is conservative. */
    protected var cachedGoalRibbonDay: Boolean = false

    /** Shadow-only: the latest pre-decision main-screen snapshot, read by no gameplay path. */
    var shadowCareerState: CareerState? = null
        private set

    private val careerStateLatch = CareerStateTurnLatch()

    /** The `careerToken + seq` join authority between the career_state and decision_trace streams; restarts at seq 1 per Campaign. */
    private val careerStateSeq = CareerStateDecisionSequence()

    /** Cleared at the start of each decision turn and written only from a proven race-completion tail, so a completed-race fact never leaks into a
     * turn that completed no race. */
    private val pendingEnteredRace = PendingEnteredRace()

    /** Last write wins, so a scenario override can replace the base path's weaker identity. Observability only: read solely at trace-emit time. */
    fun recordEnteredRace(entry: EnteredRace) {
        pendingEnteredRace.record(entry)
        StatusBoard.raceRun()
    }

    /** The same per-career seq stamped on decision_trace and career_state, read-only so it cannot advance the sequence. Grand Concert's
     * `[GC_PP_INCOME]` uses it to tell apart Pre-Debut trainings that share one canonical turn number. */
    fun currentDecisionSeq(): Int? = careerStateSeq.current()

    /** Every turn-advancing path that resets [bHasCheckedDateThisTurn] must also rearm CareerState, or that turn produces no shadow snapshot.
     * [executeAction] does this inline; overrides that bypass it (e.g. Trackblazer's TRAIN fast path) call this directly. */
    protected fun armCareerStateForNewTurn() {
        careerStateLatch.armForNewTurn()
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // Debug Tests

    /**
     * Starts the automated tests for the campaign.
     *
     * @return True if any tests were run, false otherwise.
     */
    override fun startTests(): Boolean {
        val fnMap: Map<String, () -> Unit> =
            mapOf(
                "debugMode_startTemplateMatchingTest" to ::startTemplateMatchingTest,
                "debugMode_startSingleTrainingOCRTest" to training::startSingleTrainingOCRTest,
                "debugMode_startComprehensiveTrainingOCRTest" to training::startComprehensiveTrainingOCRTest,
                "debugMode_startRaceListDetectionTest" to racing::startRaceListDetectionTest,
                "debugMode_startMainScreenUpdateTest" to this::startMainScreenUpdateTest,
                "debugMode_startScrollBarDetectionTest" to ::startScrollBarDetectionTest,
                "debugMode_startSkillListBuyTest" to skillPlan::startSkillListBuyTest,
                "debugMode_startTraineeSelectTest" to ::startTraineeSelectTest,
                "debugMode_startDeckStatReadTest" to ::startDeckStatReadTest,
                "debugMode_startDeckNumberReadTest" to ::startDeckNumberReadTest,
                "debugMode_startHostBorrowSwipeTest" to ::startHostBorrowSwipeTest,
                "debugMode_startHostLegacySwipeTest" to ::startHostLegacySwipeTest,
                "debugMode_startSupportDeckRehearsalTest" to ::startSupportDeckRehearsalTest,
                "debugMode_startSmartBorrowRehearsalTest" to ::startSmartBorrowRehearsalTest,
                "debugMode_startSmartBorrowLocateTest" to ::startSmartBorrowLocateTest,
                "debugMode_startBorrowRemoveProbeTest" to ::startBorrowRemoveProbeTest,
                "debugMode_startSmartBorrowSelectRollbackTest" to ::startSmartBorrowSelectRollbackTest,
                "debugMode_startBuildAwareLaunchGateTest" to ::startBuildAwareLaunchGateTest,
                "debugMode_startBorrowPoolScanTest" to ::startBorrowPoolScanTest,
                "debugMode_startRainbowDetectionTest" to ::startRainbowDetectionTest,
                "debugMode_startVeteranRosterReadTest" to ::startVeteranRosterReadTest,
                "debugMode_startVeteranRosterScanTest" to ::startVeteranRosterScanTest,
                "debugMode_startVeteranInspirationReadTest" to ::startVeteranInspirationReadTest,
                "debugMode_startVeteranInspirationScanTest" to ::startVeteranInspirationScanTest,
                "debugMode_startVeteranProtectionScanTest" to ::startVeteranProtectionScanTest,
                "debugMode_startGameRestartTest" to ::startGameRestartTest,
            )

        return game.diagnosticSelection?.dispatch(fnMap) ?: false
    }

    /**
     * Live check of the stuck-game restart on a healthy game: the own-input probe, then the restart the unknown-screen ladder's
     * second attempt makes ([Game.reopenGame], attempt 2). Taps nothing in the game; the career resumes via Continue Career. Tagged [RESTART-TEST].
     */
    open fun startGameRestartTest() {
        MessageLog.i(TAG, "\n[TEST] [RESTART-TEST] Checking the bot's own taps, then restarting the game the way a stuck episode's second attempt does. Nothing in the game is tapped.")
        // The Home check trusts the window in front, so this proves the read names the game while it is in front.
        val front = game.frontWindowPackage()
        MessageLog.i(TAG, "[TEST] [RESTART-TEST] Window in front before Home: ${front ?: "unreadable"}.")
        if (front != Game.GAME_PACKAGE) {
            MessageLog.w(TAG, "[TEST] [RESTART-TEST] Stopping: the game must be in front, and the window read named ${front ?: "nothing"}. Nothing was tapped or closed.")
            return
        }
        val ownInput = game.ownInputReachesScreen()
        MessageLog.i(TAG, "[TEST] [RESTART-TEST] Own-input probe: $ownInput (ARRIVED: the bot's taps reach the screen; LOST: they do not; INCONCLUSIVE: not proven either way).")
        val reopen = game.reopenGame(attempt = 2)
        MessageLog.i(TAG, "[TEST] [RESTART-TEST] Result $reopen: ${reopenOutcomeWords(reopen)}. Start the bot normally to resume a career in progress.")
    }

    /** Debug: samples rainbow-ring detection on the Training screen for ~5s and saves an annotated crop for calibration. */
    open fun startRainbowDetectionTest() {
        MessageLog.i(TAG, "\n[TEST] Now beginning the Rainbow Detection test. Point the game at the Training screen so the support face circles are visible.")
        val passes = 5
        for (pass in 1..passes) {
            MessageLog.i(TAG, "[TEST] Rainbow detection pass $pass/$passes:")
            game.imageUtils.debugRainbowDetection()
            if (pass < passes) game.wait(1.0)
        }
        MessageLog.i(TAG, "[TEST] Rainbow Detection test complete. Check the logged metrics and the saved debugRainbowDetection.png crop to calibrate geometry/thresholds.")
    }

    /** Read-only Trainee Select OCR diagnostic: logs the header detector and name-banner reads without tapping. Park on Trainee Select first. */
    open fun startTraineeSelectTest() {
        MessageLog.i(TAG, "\n[TEST] Running read-only Trainee Select OCR diagnostic...")
        CareerLaunchNavigator(game.myContext).debugTraineeSelectRead(game.imageUtils)
    }

    /** Read-only deck composition diagnostic: logs stat-type counts off the deck screen. Park on the deck-selection screen first. */
    open fun startDeckStatReadTest() {
        MessageLog.i(TAG, "\n[TEST] Running read-only deck composition OCR diagnostic...")
        CareerLaunchNavigator(game.myContext).debugDeckStatRead(game.imageUtils)
    }

    /** Read-only diagnostic: logs the raw OCR and parsed deck number off the career-start Support Formation screen. */
    open fun startDeckNumberReadTest() {
        MessageLog.i(TAG, "\n[TEST] Running read-only Deck-number OCR diagnostic...")
        CareerLaunchNavigator(game.myContext).debugDeckNumberRead(game.imageUtils)
    }

    /** Runs one authenticated host swipe on an already-open Borrow Card list, then stops. */
    open fun startHostBorrowSwipeTest() {
        MessageLog.i(TAG, "\n[HOST-INPUT] Borrow list diagnostic starting. Park the game on the open Borrow Card list.")
        val report = CareerLaunchNavigator(game.myContext).rehearseHostBorrowSwipe(game.imageUtils)
        MessageLog.i(TAG, "[HOST-INPUT] scope=${report.scope.wire} transport=${report.execution.status.wire} foreground=${report.execution.foreground} movement=${report.movement} result=${report.execution.detailCode}")
    }

    /** Runs one authenticated host swipe on an already-open Legacy Sparks list, then stops. */
    open fun startHostLegacySwipeTest() {
        MessageLog.i(TAG, "\n[HOST-INPUT] Legacy Sparks diagnostic starting. Park the game on the already-open Sparks list.")
        val report = CareerLaunchNavigator(game.myContext).rehearseHostLegacySwipe(game.imageUtils)
        MessageLog.i(TAG, "[HOST-INPUT] scope=${report.scope.wire} transport=${report.execution.status.wire} foreground=${report.execution.foreground} movement=${report.movement} result=${report.execution.detailCode}")
    }

    /** Read-only Veteran Roster / Umamusume Details diagnostic: logs every readable field off the parked screen without tapping. */
    open fun startVeteranRosterReadTest() {
        MessageLog.i(TAG, "\n[TEST] Running read-only Veteran Roster OCR diagnostic...")
        VeteranRosterReader(game.imageUtils, VeteranIdentityCatalog.loadFromAssets(game.myContext)).debugRead()
    }

    /** Read-only roster enumeration: opens the first card once, walks the next chevron, and writes a roster_scan header plus roster_entry rows to
     * outcomes/roster_scan.jsonl. Every coordinate is checked against the deny list; `veteranRosterScanLimit` caps entries (0 = whole roster). */
    open fun startVeteranRosterScanTest() {
        val selected = requireNotNull(game.diagnosticSelection)
        val limit = selected.rosterLimit
        val evidence = selected.rosterEvidence
        MessageLog.i(
            TAG,
            "\n[TEST] Running read-only Veteran Roster enumeration (entryLimit=${if (limit > 0) limit.toString() else "none"}, evidence=${if (evidence) "on" else "off"})...",
        )
        VeteranRosterScanner(game).runScan(limit, evidence)
    }

    /** Read-only: logs every Inspiration factor name, kind and star count off an open Umamusume Details dialog, tagged `[INSPIRATION-TEST]`.
     * Swipes only inside the panel and persists nothing. */
    open fun startVeteranInspirationReadTest() {
        MessageLog.i(TAG, "\n[TEST] Running read-only Veteran Inspiration diagnostic...")
        VeteranInspirationReader(game, com.steve1316.uma_android_automation.utils.VeteranFactorDomain.loadFromAssets(game.myContext)).debugRead()
    }

    /** Read-only: walks the roster with the next chevron and writes veteran_inspiration records plus a scan header to outcomes/veteran_inspiration.jsonl.
     * `veteranInspirationScanLimit` caps captures (0 = all); `veteranInspirationScanStartIndex` resumes a stopped crawl. */
    open fun startVeteranInspirationScanTest() {
        val selected = requireNotNull(game.diagnosticSelection)
        val limit = selected.inspirationLimit
        val startIndex = selected.inspirationStartIndex
        MessageLog.i(
            TAG,
            "\n[TEST] Running read-only Veteran Inspiration capture (entryLimit=${if (limit > 0) limit.toString() else "none"}, startIndex=$startIndex)...",
        )
        VeteranInspirationScanner(game).runScan(limit, startIndex)
    }

    /** Read-only: probes via Display Settings > Filter whether any Veteran is favorited or has a memo (the markers that block a release), using the
     * game's "OK disabled when the selection is empty" signal without applying a filter. Writes outcomes/veteran_protection.jsonl and leaves through Cancel. */
    open fun startVeteranProtectionScanTest() {
        MessageLog.i(TAG, "\n[TEST] Running read-only Veteran protection probe...")
        VeteranProtectionScanner(game).runScan()
    }

    /** Rehearses the production saved-deck selector (real OCR and arrow taps) for runQueue.supportDeckIndex, then stops. Never borrows, never presses
     * Start Career, spends no TP. Tagged `[DECK-REHEARSAL]`. */
    open fun startSupportDeckRehearsalTest() {
        MessageLog.i(TAG, "\n[TEST] Running support-deck selector rehearsal diagnostic...")
        CareerLaunchNavigator(game.myContext).rehearseRequiredSupportDeck(game.imageUtils)
    }

    /** Runs the production Smart Borrow sub-flow on the Support Formation screen, then verifies the required deck is still active. Never presses
     * Start Career, spends no TP. Tagged `[BORROW-REHEARSAL]`. */
    open fun startSmartBorrowRehearsalTest() {
        MessageLog.i(TAG, "\n[TEST] Running Smart Borrow rehearsal diagnostic...")
        CareerLaunchNavigator(game.myContext).rehearseSmartBorrowForRequiredDeck(game.imageUtils)
    }

    /** Read-only: reads the offline borrow intent, opens the Borrow Card picker and reports which live row is the recommended card. Taps no card,
     * spends nothing. Tagged `[BORROW-LOCATE]`. */
    open fun startSmartBorrowLocateTest() {
        MessageLog.i(TAG, "\n[TEST] Running read-only Smart Borrow locate rehearsal...")
        CareerLaunchNavigator(game.myContext).apply { attachLiveGame(game) }.locateSmartBorrowIntentReadOnly(game.imageUtils)
    }

    /** Probes the picker's Remove control on a throwaway card the operator borrowed first; the only borrow diagnostic that mutates anything. Never
     * presses Start Career. Tagged `[BORROW-REMOVE-PROBE]`. */
    open fun startBorrowRemoveProbeTest() {
        MessageLog.i(TAG, "\n[TEST] Running Borrow Remove behaviour probe...")
        CareerLaunchNavigator(game.myContext).probeBorrowRemoveBehavior(game.imageUtils)
    }

    /** Selects the intent's row, verifies the committed slot via the picker's "Selected" marker, Removes it and confirms the slot is empty, twice.
     * Both steps are reversible; never presses Start Career. Tagged `[BORROW-SELECT]`. */
    open fun startSmartBorrowSelectRollbackTest() {
        MessageLog.i(TAG, "\n[TEST] Running Smart Borrow select-verify-rollback rehearsal...")
        CareerLaunchNavigator(game.myContext).apply { attachLiveGame(game) }.rehearseSmartBorrowSelectAndRollback(game.imageUtils)
    }

    /** Dry-runs the production build-aware launch transaction to READY_TO_START_CAREER, then rolls the borrow back via Remove instead of pressing
     * Start Career. Fails closed with no legacy fallback. Tagged `[LAUNCH-GATE]`. */
    open fun startBuildAwareLaunchGateTest() {
        MessageLog.i(TAG, "\n[TEST] Running build-aware launch-gate dry-run...")
        CareerLaunchNavigator(game.myContext).apply { attachLiveGame(game) }.dryRunBuildAwareLaunchGate(game.imageUtils)
    }

    /** Read-only census of the Borrow Card pool into outcomes/borrow_pool.jsonl; never taps a row or presses Start Career. `borrowPoolScanLimit`
     * caps rows (0 = all); `borrowPoolScanEvidence` enables raw-read logging. Tagged `[BORROW-POOL]`. */
    open fun startBorrowPoolScanTest() {
        val limit = SettingsHelper.getIntSetting("debug", "borrowPoolScanLimit", 0)
        val evidence = SettingsHelper.getBooleanSetting("debug", "borrowPoolScanEvidence", false)
        MessageLog.i(TAG, "\n[TEST] Running read-only Borrow pool census (entryLimit=${if (limit > 0) limit.toString() else "none"}, evidence=${if (evidence) "on" else "off"})...")
        CareerLaunchNavigator(game.myContext).apply { attachLiveGame(game) }.scanBorrowPoolReadOnly(game.imageUtils, entryLimit = limit, captureEvidence = evidence)
    }

    /**
     * Performs a basic template matching test on the Home screen to determine the best scale for the device.
     */
    open fun startTemplateMatchingTest() {
        MessageLog.i(TAG, "\n[TEST] Now beginning basic template match test on the Home screen.")
        MessageLog.i(TAG, "[TEST] Template match confidence setting will be overridden for the test.\n")
        var results =
            mutableMapOf<String, MutableList<ScaleConfidenceResult>>(
                LabelEnergy.template.path to mutableListOf(),
                IconTazuna.template.path to mutableListOf(),
                LabelStatTableHeaderSkillPoints.template.path to mutableListOf(),
            )
        results = game.imageUtils.startTemplateMatchingTest(results)
        MessageLog.i(TAG, "\n[TEST] Basic template match test complete.")

        // Print all scale/confidence combinations that worked for each template.
        for ((templateName, scaleConfidenceResults) in results) {
            if (scaleConfidenceResults.isNotEmpty()) {
                MessageLog.i(TAG, "[TEST] All working scale/confidence combinations for $templateName:")
                for (result in scaleConfidenceResults) {
                    MessageLog.i(TAG, "[TEST]\tScale: ${result.scale}, Confidence: ${result.confidence}")
                }
            } else {
                MessageLog.w(TAG, "[WARN] startTemplateMatchingTest:: No working scale/confidence combinations found for $templateName")
            }
        }

        // Then print the median scales and confidences.
        val medianScales = mutableListOf<Double>()
        val medianConfidences = mutableListOf<Double>()
        for ((templateName, scaleConfidenceResults) in results) {
            if (scaleConfidenceResults.isNotEmpty()) {
                val sortedScales = scaleConfidenceResults.map { it.scale }.sorted()
                val sortedConfidences = scaleConfidenceResults.map { it.confidence }.sorted()
                val medianScale = sortedScales[sortedScales.size / 2]
                val medianConfidence = sortedConfidences[sortedConfidences.size / 2]
                medianScales.add(medianScale)
                medianConfidences.add(medianConfidence)
                MessageLog.i(TAG, "[TEST] Median scale for $templateName: $medianScale")
                MessageLog.i(TAG, "[TEST] Median confidence for $templateName: $medianConfidence")
            }
        }

        if (medianScales.isNotEmpty()) {
            MessageLog.i(TAG, "\n[TEST] The following are the recommended scales to set: $medianScales.")
            MessageLog.i(TAG, "[TEST] The following are the recommended confidences to set: $medianConfidences.")
        } else {
            MessageLog.e(TAG, "\n[ERROR] startTemplateMatchingTest:: No median scale/confidence can be found.")
        }
    }

    /**
     * Performs a comprehensive update test on the Main screen and perform all Main screen updates.
     */
    open fun startMainScreenUpdateTest() {
        MessageLog.i(TAG, "\n[TEST] Now beginning the Main Screen update test.")

        // Update the date.
        updateDate()

        // Perform parallel turn-start updates (stats, mood, energy, skill points, etc.).
        val sourceBitmap = game.imageUtils.getSourceBitmap()
        performTurnStartUpdates(sourceBitmap)

        // Update the aptitudes.
        openAptitudesDialog()
        handleDialogs()

        // Update the fan count.
        openFansDialog()
        handleDialogs()

        trainee.logInfo()
        MessageLog.i(TAG, "\n[TEST] Main Screen update test complete.")
    }

    /**
     * Performs a scrollbar detection and functionality test on the current screen.
     *
     * Detects the scrollbar and attempts to scroll it up and down.
     */
    fun startScrollBarDetectionTest() {
        MessageLog.i(TAG, "\n[TEST] Now beginning scrollbar detection test on the current screen.")

        // Initial detection pass.
        val scrollList = ScrollList.create(game)
        if (scrollList == null) {
            MessageLog.i(TAG, "[TEST] Could not detect a list on the current screen.")
            return
        }

        val scrollBarRegion = scrollList.getListScrollBarBoundingRegion()
        if (scrollBarRegion.first != null) {
            MessageLog.i(TAG, "[TEST] Scrollbar detected at: ${scrollBarRegion.first}")
            if (scrollBarRegion.second != null) {
                MessageLog.i(TAG, "[TEST] Scrollbar thumb detected at: ${scrollBarRegion.second}")
            } else {
                MessageLog.i(TAG, "[TEST] No scrollbar thumb detected.")
            }

            // Try scrolling down.
            MessageLog.i(TAG, "[TEST] Attempting to scroll DOWN...")
            scrollList.scrollDown()
            MessageLog.i(TAG, "[TEST] Scroll DOWN attempted.")

            game.wait(1.0)

            // Try scrolling up.
            MessageLog.i(TAG, "[TEST] Attempting to scroll UP...")
            scrollList.scrollUp()
            MessageLog.i(TAG, "[TEST] Scroll UP attempted.")

            MessageLog.i(TAG, "[TEST] Scrollbar detection test complete.")
        } else {
            MessageLog.i(TAG, "[TEST] No scrollbar detected on the current screen.")
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Handles game dialogs by identifying them and performing the appropriate responses.
     *
     * @param dialog The optional dialog interface to handle.
     * @param args Additional arguments for dialog handling logic.
     * @return The result of the dialog handling operation.
     */
    override fun handleDialogs(dialog: DialogInterface?, args: Map<String, Any>): DialogHandlerResult {
        val result: DialogHandlerResult = super.handleDialogs(dialog, args)
        if (result !is DialogHandlerResult.Unhandled) {
            return result
        }

        when (result.dialog.name) {
            "consecutive_race_warning" -> {
                return handleConsecutiveRaceWarning(result.dialog, args)
            }

            "insufficient_goal_race_result_pts" -> {
                if (!bHasCheckedDateThisTurn) {
                    MessageLog.i(TAG, "[RACE] Insufficient Goal Race Result Pts dialog detected before turn-start updates. Closing it to perform checks first.")
                    result.dialog.close(game.imageUtils)
                } else {
                    MessageLog.i(TAG, "[RACE] Insufficient Goal Race Result Pts dialog! Forced to race...")
                    racing.hasInsufficientGoalRacePtsRequirement = true
                    result.dialog.ok(game.imageUtils)
                    game.wait(2.0)
                }
            }

            "goal_not_reached" -> {
                // We are handling the logic for when to race on our own. Thus, we just close this warning.
                racing.encounteredRacingPopup = true
                result.dialog.close(game.imageUtils)
            }

            "insufficient_fans" -> {
                // We are handling the logic for when to race on our own. Thus, we just close this warning.
                racing.encounteredRacingPopup = true
                result.dialog.close(game.imageUtils)
            }

            "scheduled_race_available" -> {
                MessageLog.i(TAG, "[INFO] There is a scheduled race today. Closing to perform turn-start updates...")
                result.dialog.close(game.imageUtils)
                game.waitForLoading()
            }

            "strategy" -> {
                if (!trainee.bHasUpdatedAptitudes) {
                    trainee.bTemporaryRunningStyleAptitudesUpdated = racing.updateRaceScreenRunningStyleAptitudes()
                }

                if (date.day == 1) {
                    MessageLog.i(TAG, "[DIALOG] Unknown date. Using Original race strategy.")
                }

                var runningStyle: RunningStyle?
                val runningStyleString: String =
                    when {
                        // Special case for when the bot has not been able to check the date i.e. when the bot starts at the race screen.
                        date.day == 1 -> racing.resolveStrategyForCurrentRace(isJuniorYear = false)

                        date.year == DateYear.JUNIOR -> racing.resolveStrategyForCurrentRace(isJuniorYear = true)

                        else -> racing.resolveStrategyForCurrentRace(isJuniorYear = false)
                    }
                when (runningStyleString.uppercase()) {
                    // Do not select a strategy. Use what is already selected.
                    "DEFAULT" -> {
                        MessageLog.i(TAG, "[DIALOG] Using the default running style.")
                        result.dialog.ok(game.imageUtils)
                        // Confirming this dialog triggers connection to server.
                        game.waitForLoading()
                        // If date is unknown we want to set style next time we're at race prep screen.
                        trainee.bHasSetRunningStyle = date.day != 1
                        racing.bHasSetTemporaryRunningStyle = true
                        return DialogHandlerResult.Handled(result.dialog)
                    }

                    // Auto-select the optimal running style based on trainee aptitudes.
                    "AUTO" -> {
                        MessageLog.i(TAG, "[DIALOG] Auto-selecting the trainee's optimal running style.")
                        runningStyle = trainee.runningStyle
                    }

                    else -> {
                        MessageLog.i(TAG, "[DIALOG] Using user-specified running style: $runningStyleString")
                        runningStyle = RunningStyle.fromShortName(runningStyleString)
                    }
                }

                when (runningStyle) {
                    RunningStyle.FRONT_RUNNER -> {
                        ButtonRaceStrategyFront.click(game.imageUtils)
                    }

                    RunningStyle.PACE_CHASER -> {
                        ButtonRaceStrategyPace.click(game.imageUtils)
                    }

                    RunningStyle.LATE_SURGER -> {
                        ButtonRaceStrategyLate.click(game.imageUtils)
                    }

                    RunningStyle.END_CLOSER -> {
                        ButtonRaceStrategyEnd.click(game.imageUtils)
                    }

                    null -> {
                        // This indicates programmer error.
                        MessageLog.e(TAG, "[ERROR] handleDialogs:: Invalid running style: $runningStyle")
                        result.dialog.close(game.imageUtils)
                        trainee.bHasSetRunningStyle = false
                        return DialogHandlerResult.Handled(result.dialog)
                    }
                }

                // We only want to set this flag if the date has been checked.
                // Otherwise, if the day is still 1, that means we probably started the bot at the racing screen.
                // In this case, we still want to set the running style the next time we get back to the race selection screen after verifying the date.
                if (date.day != 1) {
                    trainee.bHasSetRunningStyle = true
                }
                racing.bHasSetTemporaryRunningStyle = true
                result.dialog.ok(game.imageUtils)
            }

            "try_again" -> {
                return handleTryAgainDialog(result.dialog, args)
            }

            "umamusume_class" -> {
                val bitmap: Bitmap = game.imageUtils.getSourceBitmap()
                val templateBitmap: Bitmap? = game.imageUtils.getBitmaps(LabelUmamusumeClassFans.template.path).second
                if (templateBitmap == null) {
                    MessageLog.e(TAG, "[ERROR] handleDialogs:: Could not get template bitmap for LabelUmamusumeClassFans: ${LabelUmamusumeClassFans.template.path}.")
                    result.dialog.close(game.imageUtils)
                    return DialogHandlerResult.Handled(result.dialog)
                }
                val point: Point? = LabelUmamusumeClassFans.find(game.imageUtils).first
                if (point == null) {
                    MessageLog.w(TAG, "[WARN] handleDialogs:: Could not find LabelUmamusumeClassFans.")
                    result.dialog.close(game.imageUtils)
                    return DialogHandlerResult.Handled(result.dialog)
                }

                // Add a small 8px buffer to vertical component.
                val bbox =
                    BoundingBox(
                        x = game.imageUtils.relX(0.0, (point.x + (templateBitmap.width / 2)).toInt()),
                        y = game.imageUtils.relY(0.0, (point.y - (templateBitmap.height / 2) - 4).toInt()),
                        w = game.imageUtils.relWidth(300),
                        h = game.imageUtils.relHeight(templateBitmap.height + 4),
                    )

                val croppedBitmap =
                    game.imageUtils.createSafeBitmap(
                        bitmap,
                        bbox.x,
                        bbox.y,
                        bbox.w,
                        bbox.h,
                        "dialog::umamusume_class: Cropped bitmap.",
                    )
                if (croppedBitmap == null) {
                    MessageLog.e(TAG, "[ERROR] handleDialogs:: Failed to crop bitmap.")
                    result.dialog.close(game.imageUtils)
                    return DialogHandlerResult.Handled(result.dialog)
                }
                val fans = game.imageUtils.getUmamusumeClassDialogFanCount(croppedBitmap)
                if (fans != null) {
                    trainee.observeFanCount(fans)
                    bNeedToCheckFans = false
                    MessageLog.i(TAG, "[INFO] Updated fan count: ${trainee.fans}")
                } else {
                    MessageLog.w(TAG, "[WARN] handleDialogs:: getUmamusumeClassDialogFanCount returned null.")
                }

                result.dialog.close(game.imageUtils)
            }

            "umamusume_details" -> {
                val prevRunningStyle = trainee.runningStyle
                trainee.updateAptitudes(game.imageUtils)
                trainee.updateStats(game.imageUtils, isAptitudeDialog = true)
                trainee.bTemporaryRunningStyleAptitudesUpdated = false

                // Read the trainee's name once per run while the dialog is still open.
                if (trainee.name.isEmpty()) {
                    trainee.readName(game.imageUtils)
                }

                // Rotation backstop: the only trainee check on the resume path (no Trainee Select). Runs once per career regardless of deck validation.
                if (!bRotationTraineeVerified) {
                    verifyRotationTrainee()
                    bRotationTraineeVerified = true
                }

                if (trainee.runningStyle != prevRunningStyle) {
                    // Reset this flag since our preferred running style has changed.
                    trainee.bHasSetRunningStyle = false
                }

                if (enableDeckValidation && !bDeckValidationChecked && trainee.bHasUpdatedAptitudes) {
                    runDeckValidation()
                    bDeckValidationChecked = true
                }

                result.dialog.close(game.imageUtils)
            }

            "choose_recreation_partner" -> {
                // The recreation was opened but the outing is being held (reserved for the Pure Passion turn), leaving this dialog up. Close it.
                MessageLog.i(TAG, "[RECREATION_DATE] Choose Recreation Partner dialog left open. Closing it.")
                result.dialog.close(game.imageUtils)
            }

            else -> {
                Log.w(TAG, "[WARN] handleDialogs:: Unknown dialog \"${result.dialog.name}\" detected so it will not be handled.")
                return DialogHandlerResult.Unhandled(result.dialog)
            }
        }

        game.wait(0.5)
        return DialogHandlerResult.Handled(result.dialog)
    }

    /**
     * Performs campaign-specific checks for special screens or conditions.
     *
     * @return True if the conditions are met, false otherwise.
     */
    open fun checkCampaignSpecificConditions(): Boolean {
        return false
    }

    /** The default is the URA-style Learn button; Grand Concert's Complete Career screen overrides it and may run scenario steps that must precede
     * the skill spend. */
    open fun openCareerEndSkillScreen() {
        ButtonCareerEndSkills.click(game.imageUtils)
    }

    /**
     * Handles campaign-specific Training Events.
     */
    open fun handleTrainingEvent() {
        trainingEvent.handleTrainingEvent()
    }

    /**
     * Handles campaign-specific race events.
     *
     * @param isScheduledRace True if the race is scheduled, false otherwise.
     * @return True if the race was handled successfully, false otherwise.
     */
    open fun handleRaceEvents(isScheduledRace: Boolean = false): Boolean {
        val bDidRace: Boolean = racing.handleRaceEvents(isScheduledRace)
        bNeedToCheckFans = bDidRace
        return bDidRace
    }

    /**
     * Performs campaign-specific logic to handle a race win.
     */
    open fun onRaceWin() {
        return
    }

    /**
     * Executes logic at the very beginning of [handleMainScreen].
     */
    open fun onBeforeMainScreenUpdate() {
        return
    }

    /**
     * Resets any scenario-specific daily flags when a new day is detected.
     */
    open fun resetDailyFlags() {
        return
    }

    /**
     * Called when a consecutive race warning dialog is first detected, before any decision is made.
     *
     * Subclasses can override this to perform pre-processing such as OCR reads.
     * This is called regardless of whether force-race flags are active.
     *
     * @param dialog The detected dialog.
     * @param args Additional arguments from dialog handling.
     */
    open fun onConsecutiveRaceWarningDetected(dialog: DialogInterface, args: Map<String, Any>) {
        return
    }

    /**
     * Determines whether to proceed with a consecutive race despite the warning.
     *
     * Called after [onConsecutiveRaceWarningDetected] and after force-race flags have been checked.
     * This is only called when force-race flags are NOT active - if they are, the race proceeds unconditionally.
     *
     * @param args Additional arguments from dialog handling.
     * @return True to proceed with the race, false to abort and clear racing requirement flags.
     */
    open fun shouldAllowConsecutiveRace(args: Map<String, Any>): Boolean {
        // Default behavior: if force-race flags are not active, abort.
        return false
    }

    /**
     * Determines whether to retry a race after failing.
     *
     * Called when [Racing.disableRaceRetries] is false (non-mandatory race retries).
     * The implementation should handle clicking the retry button if returning true.
     *
     * @param dialog The Try Again dialog.
     * @param args Additional arguments from dialog handling.
     * @param lostGoalRace True after a lost goal race, which may use the career's whole retry budget.
     * @return True if the retry was initiated (button clicked), false to close the dialog without retrying.
     */
    open fun shouldRetryRace(dialog: DialogInterface, args: Map<String, Any>, lostGoalRace: Boolean): Boolean {
        if (racing.bAlarmClockPolicySkippedThisRace) return false
        val raceLimit = Racing.raceRetryLimit(lostGoalRace, racing.retriesThisRace, racing.raceRetries, racing.maxRetriesPerRace)
        if (Racing.retryAllowed(lostGoalRace, racing.retriesThisRace, racing.raceRetries, racing.maxRetriesPerRace)) {
            val policy = SettingsHelper.getStringSetting("racing", "alarmClockPolicy", "Never")
            val freeRetryShown = IconOneFreePerDayTooltip.check(game.imageUtils)
            MessageLog.i(TAG, Racing.raceRetryText(freeRetryShown, racing.retriesThisRace + 1, raceLimit, racing.raceRetries - 1, policy, racing.lastRaceGrade, lostGoalRace))
            if (lostGoalRace) racing.bRetryingLostGoalRace = true
            racing.raceRetries--
            racing.retriesThisRace++
            game.wait(0.5)
            ButtonTryAgain.click(game.imageUtils)
            return true
        }
        return false
    }

    /**
     * Handles the consecutive race warning dialog using hook methods for extensibility.
     *
     * @param dialog The detected dialog.
     * @param args Additional arguments from dialog handling.
     * @return The result of the dialog handling operation.
     */
    private fun handleConsecutiveRaceWarning(dialog: DialogInterface, args: Map<String, Any>): DialogHandlerResult {
        val overrideIgnoreConsecutiveRaceWarning = args["overrideIgnoreConsecutiveRaceWarning"] as? Boolean ?: false

        // Pre-processing hook (e.g. Trackblazer OCR).
        onConsecutiveRaceWarningDetected(dialog, args)

        val forceRace = overrideIgnoreConsecutiveRaceWarning || racing.enableForceRacing || racing.ignoreConsecutiveRaceWarning

        val shouldProceed = forceRace || shouldAllowConsecutiveRace(args)

        if (shouldProceed) {
            // Clear the gate so a downstream race-entry abort that does not advance the day can still retry this turn.
            racing.raceRepeatWarningCheck = false
            // If the bot hasn't checked the date yet, it usually means it started on the prep screen or it is the Finale season.
            // If we are explicitly overriding the warning (mandatory race), we should proceed even if the date check hasn't finished.
            if (!bHasCheckedDateThisTurn && !overrideIgnoreConsecutiveRaceWarning && !date.bIsFinaleSeason) {
                MessageLog.i(TAG, "[RACE] Consecutive race warning detected before turn-start updates. Closing it to perform checks first.")
                dialog.close(game.imageUtils)
            } else {
                val isScheduledRace = args["isScheduledRace"] as? Boolean ?: false
                val isMandatoryRace = args["isMandatoryRace"] as? Boolean ?: false

                when {
                    isScheduledRace -> MessageLog.i(TAG, "[RACE] Consecutive race warning! Racing anyway as this is a scheduled race...")
                    isMandatoryRace -> MessageLog.i(TAG, "[RACE] Consecutive race warning! Racing anyway as this is a required race...")
                    else -> MessageLog.i(TAG, "[RACE] Consecutive race warning! Racing anyway...")
                }

                dialog.ok(game.imageUtils)
                game.wait(2.0)
            }
        } else {
            racing.raceRepeatWarningCheck = true
            MessageLog.i(TAG, "[RACE] Consecutive race warning! Aborting racing...")
            racing.clearRacingRequirementFlags()
            dialog.close(game.imageUtils)
        }

        game.wait(0.5)
        return DialogHandlerResult.Handled(dialog)
    }

    /**
     * Handles the Try Again dialog using a hook method for the retry decision.
     *
     * The mandatory-race-failure path (disableRaceRetries == true) is handled here as shared logic.
     * The non-mandatory retry decision is delegated to [shouldRetryRace].
     *
     * @param dialog The Try Again dialog.
     * @param args Additional arguments from dialog handling.
     * @return The result of the dialog handling operation.
     */
    private fun handleTryAgainDialog(dialog: DialogInterface, args: Map<String, Any>): DialogHandlerResult {
        // All branches need a slight delay to allow the dialog to close since the runRaceWithRetries() loop handles dialogs at the start of each iteration.
        // Can cause problem where we handle one branch then immediately handle dialogs again and handle a second branch for the same dialog instance.
        if (racing.disableRaceRetries) {
            if (racing.enableFreeRaceRetry && IconOneFreePerDayTooltip.check(game.imageUtils)) {
                MessageLog.i(TAG, "[RACE] Failed mandatory race. Using daily free race retry...")
                racing.raceRetries--
                dialog.ok(game.imageUtils)
                game.wait(0.5)
                return DialogHandlerResult.Handled(dialog)
            }
            if (racing.enableCompleteCareerOnFailure) {
                MessageLog.i(TAG, "[RACE] Failed a mandatory race and no retries remaining. Completing career...")
                markCareerForceEnded("MANDATORY_RACE_LOST")
                // Manually set retries to -1 to break the race retry loop.
                racing.raceRetries = -1
                dialog.close(game.imageUtils)
                game.wait(0.5)
                return DialogHandlerResult.Handled(dialog)
            }
            MessageLog.v(TAG, "\n[END] Stopping the bot due to failing a mandatory race.")
            MessageLog.v(TAG, "********************")
            game.notificationMessage = "Stopping the bot due to failing a mandatory race."
            markCareerForceEnded("MANDATORY_RACE_LOST")
            if (DiscordUtils.enableDiscordNotifications) {
                DiscordUtils.queue.add("```diff\n- ${MessageLog.getSystemTimeString()} Stopping the bot due to failing a mandatory race.\n```")
            }
            throw IllegalStateException()
        }

        // The game offers Try Again on a goal race only when the goal failed.
        val lostGoalRace = racing.bRunningGoalRace
        if (shouldRetryRace(dialog, args, lostGoalRace)) {
            // Retry was initiated by the hook.
        } else {
            val policy = SettingsHelper.getStringSetting("racing", "alarmClockPolicy", "Never")
            val raceLimit = Racing.raceRetryLimit(lostGoalRace, racing.retriesThisRace, racing.raceRetries, racing.maxRetriesPerRace)
            MessageLog.i(TAG, Racing.raceRetryDeclinedText(racing.bAlarmClockPolicySkippedThisRace, racing.retriesThisRace, raceLimit, racing.raceRetries, policy))
            if (Racing.declinedRetryFailsGoal(racing.bRunningGoalRace, date.bIsFinaleSeason)) markCareerForceEnded("MANDATORY_RACE_LOST")
            dialog.close(game.imageUtils)
        }

        game.wait(0.5)
        return DialogHandlerResult.Handled(dialog)
    }

    /**
     * Executes logic after the parallel turn-start updates (stat, mood, energy, etc.) have completed.
     */
    open fun onAfterTurnStartUpdates() {
        return
    }

    /**
     * Executes logic after all updates and global checks have completed, but before decision-making.
     */
    open fun onMainScreenEntry() {
        return
    }

    /** Null when the scenario carries no persistent decision state; subclasses build it from their own fields. */
    open fun scenarioStateSnapshot(): ScenarioState? {
        return null
    }

    /**
     * Determines whether item-based mood recovery should override the default mood recovery logic.
     *
     * Called when mood is below Good and the firstTrainingCheck guard has passed.
     * Subclasses can override this to make item-aware mood recovery decisions.
     *
     * @param sourceBitmap Current screen bitmap.
     * @return True to proceed with rest/recreation recovery, false to skip recovery (items will handle it),
     *         or null to fall through to the default Campaign behavior.
     */
    open fun shouldRecoverMoodFromItems(sourceBitmap: Bitmap): Boolean? {
        return null
    }

    /**
     * Determines if mood recovery should be attempted.
     *
     * @param sourceBitmap Current screen bitmap.
     * @return True if mood recovery is needed and possible, false otherwise.
     */
    open fun shouldRecoverMood(sourceBitmap: Bitmap): Boolean {
        // Guard: During the first training check, skip mood recovery for Normal mood to allow training analysis first.
        if (training.firstTrainingCheck && trainee.mood == Mood.NORMAL && !ButtonRestAndRecreation.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            MessageLog.i(
                TAG,
                "[MOOD] Current mood is Normal. Not recovering mood due to firstTrainingCheck flag being active. Will need to complete a training first before being allowed to recover mood.",
            )
            return false
        }

        // Allow subclasses to make item-aware mood recovery decisions.
        if (trainee.mood <= Mood.NORMAL) {
            val itemDecision = shouldRecoverMoodFromItems(sourceBitmap)
            if (itemDecision != null) {
                return itemDecision
            }
        }

        return (trainee.mood < moodFloor)
    }

    /**
     * Performs mood recovery for the trainee.
     *
     * @param sourceBitmap Current screen bitmap.
     * @param targetMood The mood level to recover to. Defaults to GOOD.
     * @return True if mood was successfully recovered, false otherwise.
     */
    open fun performMoodRecovery(sourceBitmap: Bitmap, targetMood: Mood = Mood.GOOD): Boolean {
        return recoverMood(sourceBitmap, targetMood = targetMood)
    }

    /** Informational only. Subclasses may extend it (e.g. Trackblazer's mixed turf/dirt schedule) but should call super() first. */
    protected open fun runDeckValidation() {
        val distance = trainee.trackDistance
        val style = trainee.runningStyle
        val distAptitude = trainee.trackDistanceAptitudes[distance] ?: Aptitude.G
        val styleAptitude = trainee.runningStyleAptitudes[style] ?: Aptitude.G

        val distOk = distAptitude >= deckValidationMinAptitude
        val styleOk = styleAptitude >= deckValidationMinAptitude

        if (distOk && styleOk) {
            MessageLog.i(
                TAG,
                "[DECK_VALIDATION] Deck OK — preferred distance ${distance.name} aptitude=$distAptitude, " +
                    "preferred style ${style.name} aptitude=$styleAptitude, floor=$deckValidationMinAptitude.",
            )
        } else {
            val shortfalls =
                buildList {
                    if (!distOk) add("distance ${distance.name}=$distAptitude (need $deckValidationMinAptitude+)")
                    if (!styleOk) add("style ${style.name}=$styleAptitude (need $deckValidationMinAptitude+)")
                }.joinToString(", ")

            MessageLog.w(
                TAG,
                "[DECK_VALIDATION] [WARN] Deck below aptitude floor: $shortfalls. The bot will continue, " +
                    "but expect lower race-finishing positions and reduced fan/skill-point gains. " +
                    "Consider rebuilding the deck with stronger support cards for this distance/style, " +
                    "or pick a scenario that better matches the trainee's signature aptitudes.",
            )
        }

        // Prediction visibility, not aptitude, gates the race finder (the game computes prediction stars from stats AND aptitudes): a trainee with no
        // strong distance aptitude draws single-star predictions across its early pool, so the fan checkpoint can still be missed. Runs for every trainee.
        val bestDistAptitude = trainee.trackDistanceAptitudes.values.maxOrNull() ?: Aptitude.G
        if (bestDistAptitude < Aptitude.B) {
            MessageLog.w(
                TAG,
                "[DECK_VALIDATION] [WARN] Prediction-visibility risk: best distance aptitude is " +
                    "$bestDistAptitude (below B). Expect mostly single-star race predictions early on. " +
                    "The bot will still enter the best available race when a fan goal deadline is near, " +
                    "but placements and fan gains will be poor and the checkpoint may still be missed. " +
                    "Consider stronger support cards or 7+ pink aptitude sparks before relying on this deck.",
            )
        }
    }

    /** The only trainee check on the resume path (a resume after process death skips Trainee Select). Names are compared de-outfitted because the
     * in-career name has no "[Outfit]" prefix; outfit-level discrimination is impossible there. Conservative because a STOP halts the whole
     * unattended queue: a match passes; a confident match to a different roster trainee resyncs the rotation onto her entry (stop only if the
     * snapshot is missing or the character holds several slots, where a wrong guess would apply the wrong preset); an unreadable or off-roster
     * name warns and continues. */
    private fun verifyRotationTrainee() {
        if (!SettingsHelper.getBooleanSetting("runQueue", "enableTraineeRotation", false)) return

        val inCareer = trainee.name.trim()
        if (inCareer.isEmpty() || inCareer.equals("null", ignoreCase = true)) {
            MessageLog.w(TAG, "[ROTATION] Trainee verify skipped: in-career name unreadable. Continuing without the match check.")
            return
        }

        val target = SettingsHelper.getStringSetting("queueState", "currentTrainee", "").trim()
        if (target.isEmpty()) return // No rotation target recorded for this career; nothing to check against.

        // De-outfit, and also score the full form in case a screen includes the outfit.
        fun matchScore(candidate: String): Double =
            maxOf(
                TraineeNameMatcher.score(inCareer, candidate),
                TraineeNameMatcher.score(inCareer, deOutfit(candidate)),
            )

        val targetScore = matchScore(target)
        if (targetScore >= rotationVerifyMatchThreshold) {
            MessageLog.i(TAG, "[ROTATION] Trainee verify OK: career '$inCareer' matches the loaded preset for '$target' (score=${"%.2f".format(targetScore)}).")
            // Identity and config can diverge (a resume that re-applied the wrong slot); the fingerprint comparison catches what the name check cannot.
            warnOnTraineeConfigDrift(StartModule.loadRotationConfig().inGameNames.indexOf(target), "career-start check")
            return
        }

        // Act only on a CONFIDENT match to a different roster trainee; a noisy or off-roster read warns instead of halting.
        var best: String? = null
        var bestScore = 0.0
        var bestIndex = -1
        val rotationNames = StartModule.loadRotationConfig().inGameNames
        for ((i, candidate) in rotationNames.withIndex()) {
            val s = matchScore(candidate)
            if (s > bestScore) {
                bestScore = s
                best = candidate
                bestIndex = i
            }
        }

        val matched = best
        if (matched != null && bestScore >= rotationVerifyMatchThreshold && deOutfit(matched) != deOutfit(target)) {
            // An externally interrupted queue restarted from entry 0 while the game resumed the old career: resync onto her entry. Refused when the
            // character holds several rotation slots (the bare in-career name cannot tell outfits apart).
            val duplicateSlots = rotationNames.count { deOutfit(it) == deOutfit(matched) }
            if (duplicateSlots == 1 && StartModule.resyncRotationOntoCareer(game.myContext, bestIndex)) {
                // Without this the career keeps the wrong preset's construction-cached stat priorities and event overrides.
                reloadTraineeConfig()
                MessageLog.w(
                    TAG,
                    "[ROTATION] Resynced onto interrupted career: this career is '$inCareer' (rotation entry #${bestIndex + 1} '$matched', " +
                        "score=${"%.2f".format(bestScore)}) but the queue had loaded the preset for '$target'. Applied the snapshot for " +
                        "'$matched', fast-forwarded the rotation cursor, and rebuilt the training config so the career now runs on her preset.",
                )
                val targets = trainee.getStatTargetsByDistance().entries.joinToString(", ") { "${it.key}=${it.value}" }
                MessageLog.i(
                    TAG,
                    "[CONFIG_DRIFT] trainee settings reloaded: distance=${trainee.trackDistance} targets=[$targets] " +
                        "mustRestBeforeSummer=$mustRestBeforeSummer moodFloor=$moodFloor skillPointCheck=$skillPointsRequired " +
                        "objective=${skillSpendObjective.token()}",
                )
                warnOnTraineeConfigDrift(bestIndex, "post-resync verification")
                return
            }
            if (duplicateSlots > 1) {
                MessageLog.e(
                    TAG,
                    "[ROTATION] Resync refused: '${deOutfit(matched)}' occupies $duplicateSlots rotation slots and the in-career name " +
                        "cannot tell their outfits apart, so the queue cannot know which entry this career belongs to.",
                )
            }
            MessageLog.e(
                TAG,
                "[ROTATION] Trainee MISMATCH: this career is '$inCareer' (best roster match '$matched', score=${"%.2f".format(bestScore)}) " +
                    "but the queue loaded the preset for '$target', and the rotation could not be resynced onto '$matched' " +
                    "(missing snapshot, different scenario, or duplicate rotation slots). " +
                    "Stopping the queue rather than play a career under the wrong trainee's settings — restart the queue from the game's home screen.",
            )
            StartModule.queueStopKey = "TRAINEE_MISMATCH"
            StartModule.queueStopReason =
                "Stopped on trainee mismatch - career was '$inCareer' but the queue loaded the preset for '$target' and the resync onto '$matched' failed. Restart from the home screen."
            StartModule.queueStopRequested = true
            return
        }

        MessageLog.w(
            TAG,
            "[ROTATION] Trainee verify inconclusive: career '$inCareer' vs loaded target '$target' scored ${"%.2f".format(targetScore)} " +
                "(best roster match ${"%.2f".format(bestScore)}). Likely an OCR misread of the name — continuing without stopping.",
        )
    }

    private fun deOutfit(name: String): String {
        val stripped = name.replace(Regex("^\\s*\\[[^\\]]*\\]\\s*"), "").trim()
        return stripped.ifEmpty { name.trim() }
    }

    /**
     * Checks if the bot is currently at the Main screen or the screen with available options.
     *
     * This also ensures that the Main screen does not contain the option to select a race.
     *
     * @return True if the bot is at the Main screen, false otherwise.
     */
    open fun checkMainScreen(): Boolean {
        // One screenshot shared across the four checks; each would otherwise cost its own MediaProjection capture per process() iteration.
        val bitmap: Bitmap = game.imageUtils.getSourceBitmap()

        // If there is a dialog on the screen, then we are not directly on the Main screen.
        if (DialogUtils.check(game.imageUtils, sourceBitmap = bitmap)) {
            return false
        }

        return ButtonHomeFullStats.check(game.imageUtils, sourceBitmap = bitmap) &&
            IconTazuna.check(game.imageUtils, sourceBitmap = bitmap) &&
            ButtonTraining.check(game.imageUtils, sourceBitmap = bitmap)
    }

    /**
     * Checks if the bot is currently at the Training Event screen with an active event.
     *
     * @return True if the bot is at the Training Event screen, false otherwise.
     */
    open fun checkTrainingEventScreen(): Boolean {
        MessageLog.i(TAG, "\n[INFO] Checking if the bot is sitting on the Training Event screen.")
        return if (IconTrainingEventHorseshoe.check(game.imageUtils)) {
            MessageLog.v(TAG, "[INFO] Bot is at the Training Event screen.")
            true
        } else {
            MessageLog.i(TAG, "[INFO] Bot is not at the Training Event screen.")
            false
        }
    }

    /**
     * Checks if the bot is currently at the preparation screen for a mandatory race.
     *
     * @return True if the bot is at the Race Preparation screen for a mandatory race, false otherwise.
     */
    open fun checkMandatoryRacePrepScreen(): Boolean {
        MessageLog.i(TAG, "\n[INFO] Checking if the bot is sitting on the Race Preparation screen for a mandatory race.")
        val sourceBitmap = game.imageUtils.getSourceBitmap()
        return if (IconRaceDayRibbon.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            MessageLog.v(TAG, "[INFO] Bot is at the preparation screen with a mandatory race ready to be completed.")
            if (game.scenario == "Unity Cup") game.wait(1.0)
            true
        } else if (IconGoalRibbon.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            // Most likely the user started the bot here so a delay will need to be placed to allow the start banner of the Service to disappear.
            game.wait(2.0)
            // The goal ribbon also shows on Main and behind blocking popups (e.g. the "Umamusume Class" popup), so it does not prove Race Selection:
            // require a Back button that is actually clicked, else return false so process() falls through to real recovery instead of looping on a no-op tap.
            if (ButtonBack.click(game.imageUtils)) {
                MessageLog.v(TAG, "[INFO] Bot is at the Race Selection screen with a mandatory race needing to be selected.")
                game.wait(1.0)
                true
            } else {
                MessageLog.w(TAG, "[WARN] checkMandatoryRacePrepScreen:: Goal ribbon detected but no Back button is present — not the Race Selection screen. Deferring to misc recovery.")
                false
            }
        } else if (game.scenario == "Unity Cup" && ButtonUnityCupRace.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            MessageLog.v(TAG, "[INFO] Bot is awaiting opponent selection for a Unity Cup race.")
            true
        } else {
            MessageLog.i(TAG, "[INFO] Bot is not at the Race Preparation screen for a mandatory race.")
            false
        }
    }

    /**
     * Checks if the bot is currently at the Racing screen.
     *
     * @return True if the bot is at the Racing screen, false otherwise.
     */
    open fun checkRacingScreen(): Boolean {
        MessageLog.i(TAG, "\n[INFO] Checking if the bot is sitting on the Racing screen.")
        return if (ButtonChangeRunningStyle.check(game.imageUtils)) {
            MessageLog.v(TAG, "[INFO] Bot is at the Racing screen waiting to be skipped or done manually.")
            true
        } else if (ButtonRace.check(game.imageUtils) || ButtonRaceExclamation.check(game.imageUtils)) {
            // The lineup screen (entrants + green "Race!" button) has no ButtonChangeRunningStyle and no other check matches it; a Continue-Career resume
            // lands here. Checked after the strategy screen so the normal prep flow is unaffected.
            MessageLog.v(TAG, "[INFO] Bot is at the race lineup screen (Race! button present); entering the race.")
            true
        } else {
            MessageLog.i(TAG, "[INFO] Bot is not at the Racing screen.")
            false
        }
    }

    /**
     * Checks if the bot is currently at the Ending screen detailing overall results.
     *
     * @return True if the bot is at the Ending screen, false otherwise.
     */
    open fun checkEndScreen(): Boolean {
        MessageLog.i(TAG, "\n[INFO] Checking if the bot is sitting on the End screen.")
        return if (ButtonCompleteCareer.check(game.imageUtils)) {
            MessageLog.v(TAG, "[INFO] Bot is at the End screen.")
            true
        } else {
            MessageLog.i(TAG, "[INFO] Bot is not at the End screen and can keep going.")
            false
        }
    }

    /** Covers starting the bot directly on that screen, where [checkEndScreen] cannot match because Complete Career is not reliably visible
     * inside the list. */
    private fun checkCareerEndSkillListScreen(): Boolean {
        if (!(skillPlan.skillPlans["careerComplete"]?.bIsEnabled ?: false)) return false
        return careerEndScreenChecker.checkCareerCompleteSkillListScreen()
    }

    /**
     * Checks if the bot should stop before the finals on turn 72.
     *
     * @return True if the bot should stop, false otherwise.
     */
    open fun checkFinalsStop(): Boolean {
        if (!enableStopBeforeFinals) {
            Log.d(TAG, "\n[DEBUG] checkFinalsStop:: Flag is false so skipping Finals check.")
            return false
        } else if (date.day > 72) {
            // If already past turn 72, skip the check to prevent re-checking.
            Log.d(TAG, "\n[DEBUG] checkFinalsStop:: Turn is greater than 72 so skipping Finals check.")
            return false
        }

        MessageLog.i(TAG, "\n[FINALS] Checking if bot should stop before the finals.")

        // Check if turn is 72, but only stop if we progressed to turn 72 during this run.
        if (date.day == 72 && stopBeforeFinalsInitialTurnNumber != -1) {
            MessageLog.v(TAG, "\n[END] Detected turn 72. Stopping bot before the finals.")
            game.notificationMessage = "Stopping bot before the finals on turn 72."
            return true
        }

        // Track initial turn number on first check to avoid stopping if bot starts on turn 72.
        if (stopBeforeFinalsInitialTurnNumber == -1) {
            stopBeforeFinalsInitialTurnNumber = date.day
        }

        return false
    }

    /**
     * Checks if the bot should stop at any of the user-specified dates.
     *
     * @return True if the bot should stop, false otherwise.
     */
    open fun checkStopAtDate(): Boolean {
        if (!enableStopAtDate) {
            Log.d(TAG, "\n[DEBUG] checkStopAtDate:: Flag is false so skipping Stop at Date check.")
            return false
        }

        MessageLog.i(TAG, "\n[DATE] Checking if bot should stop at any specified date. Current date: $date.")

        // Track initial turn number on first check to avoid stopping immediately if bot starts after the target date
        if (stopAtDateInitialTurnNumber == -1) {
            stopAtDateInitialTurnNumber = date.day
        }

        for (stopAtDate in stopAtDates) {
            val parts = stopAtDate.split(" ")
            if (parts.size != 3) {
                MessageLog.e(TAG, "[ERROR] checkStopAtDate:: Invalid Stop at Date format for '$stopAtDate'. Expected 'YEAR MONTH PHASE'")
                continue
            }

            val targetYear =
                try {
                    DateYear.valueOf(parts[0].uppercase())
                } catch (_: IllegalArgumentException) {
                    null
                }
            val targetMonth =
                try {
                    DateMonth.valueOf(parts[1].uppercase())
                } catch (_: IllegalArgumentException) {
                    null
                }
            val targetPhase =
                try {
                    DatePhase.valueOf(parts[2].uppercase())
                } catch (_: IllegalArgumentException) {
                    null
                }

            if (targetYear == null || targetMonth == null || targetPhase == null) {
                MessageLog.e(TAG, "[ERROR] checkStopAtDate:: Invalid Stop at Date components for '$stopAtDate'.")
                continue
            }

            val targetDay = GameDate.toDay(targetYear, targetMonth, targetPhase)

            if (date.day >= targetDay && stopAtDateInitialTurnNumber <= targetDay) {
                MessageLog.v(TAG, "\n[END] Reached target date: $stopAtDate (Turn $targetDay). Stopping bot.")
                game.notificationMessage = "Stopping bot at the specified date: $stopAtDate (Turn $targetDay)"
                return true
            }
        }

        return false
    }

    // A negative status that persists while the Infirmary reads disabled is a button-state misread or an incurable condition (e.g. Super Creek's
    // story-locked "Under the Weather"). One forced click per episode settles the misread; the loud log captures the rest.
    private var turnsWithPersistentNegativeStatus = 0
    private var bForcedInfirmaryAttempted = false

    /**
     * Checks if the trainee has an injury and attempts to heal it.
     *
     * @param sourceBitmap Optional pre-captured bitmap to analyze.
     * @return True if an injury was detected and healing was attempted, false otherwise.
     */
    open fun checkInjury(sourceBitmap: Bitmap? = null): Boolean {
        MessageLog.i(TAG, "\n[INJURY] Checking if there is an injury that needs healing on $date.")
        val sourceBitmap = sourceBitmap ?: game.imageUtils.getSourceBitmap()

        return when (ButtonInfirmary.checkDisabled(game.imageUtils, sourceBitmap)) {
            true -> {
                val statuses = trainee.currentNegativeStatuses
                if (statuses.isEmpty()) {
                    turnsWithPersistentNegativeStatus = 0
                    bForcedInfirmaryAttempted = false
                    MessageLog.i(TAG, "[INJURY] No injury detected.")
                    false
                } else {
                    turnsWithPersistentNegativeStatus++
                    if (turnsWithPersistentNegativeStatus >= 2 && !bForcedInfirmaryAttempted) {
                        bForcedInfirmaryAttempted = true
                        MessageLog.w(
                            TAG,
                            "[INJURY] Infirmary button reads disabled but negative status (${statuses.joinToString(", ")}) has persisted for " +
                                "$turnsWithPersistentNegativeStatus turns. Forcing one infirmary attempt in case the disabled read is wrong.",
                        )
                        StatusBoard.action("infirmary", null)
                        if (ButtonInfirmary.click(game.imageUtils)) {
                            game.wait(game.dialogWaitDelay)
                            ButtonOk.click(game.imageUtils, region = game.imageUtils.regionMiddle)
                            game.wait(game.dialogWaitDelay)
                            MessageLog.i(TAG, "[INJURY] Forced infirmary attempt clicked through. If the status persists next turn, the infirmary cannot cure it.")
                            true
                        } else {
                            MessageLog.i(
                                TAG,
                                "[INJURY] Forced infirmary attempt found no clickable button - the disabled read was genuine and the current status is not infirmary-curable. Continuing without healing.",
                            )
                            false
                        }
                    } else {
                        MessageLog.i(
                            TAG,
                            "[INJURY] No injury detected (Infirmary disabled; negative status \"${statuses.joinToString(", ")}\" present, turn $turnsWithPersistentNegativeStatus of episode).",
                        )
                        false
                    }
                }
            }

            false -> {
                MessageLog.v(TAG, "[INJURY] Injury detected. Attempting to heal...")
                StatusBoard.action("infirmary", null)
                if (ButtonInfirmary.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
                    game.wait(game.dialogWaitDelay)
                    ButtonOk.click(game.imageUtils, region = game.imageUtils.regionMiddle)
                    game.wait(game.dialogWaitDelay)

                    // The click already fired the heal server-side; the event-header match is only a best-effort visual confirmation, so a miss (template drift,
                    // "Connecting" overlay) must not make the bot believe injuries persist.
                    if (IconInfirmaryEventHeader.check(game.imageUtils)) {
                        MessageLog.v(TAG, "[INJURY] Injury detected and attempted to heal.")
                    } else {
                        MessageLog.v(TAG, "[INJURY] Injury detected and no follow-up Infirmary event appeared.")
                    }
                    true
                } else {
                    MessageLog.w(TAG, "[WARN] checkInjury:: Injury detected but failed to click Infirmary button.")
                    false
                }
            }

            null -> {
                MessageLog.w(TAG, "[WARN] checkInjury:: Failed to detect the Infirmary button.")
                false
            }
        }
    }

    /**
     * Returns whether the trainee is currently in the finale season.
     *
     * @return True if in the finale season, false otherwise.
     */
    open fun checkFinals(): Boolean {
        return date.bIsFinaleSeason
    }

    /** [balances] is what the training panel showed this turn; non-Grand Concert campaigns return null, which disarms the bias. */
    open fun grandConcertPointContext(balances: Map<PerformancePointType, Int?>?): GrandConcertPointContext? = null

    /** The base returns false (race a fan requirement immediately); Grand Concert overrides with a fail-closed slack policy. Only the fan arm is
     * eligible: trophy and goal-points requirements always race. */
    open fun considerFanRaceDeferral(): Boolean = false

    /** The base returns the no-scenario-facts sentinel, keeping the legacy template-driven requirement; Grand Concert overrides it because its
     * `race_criteria_fans` template is dead. Pure. */
    open fun currentFanRequirementFromScenarioFacts(): GrandConcertFanRequirement.Result =
        GrandConcertFanRequirement.Result.Unknown(GrandConcertFanRequirement.REASON_NO_SCENARIO_FACTS)

    /** Exposes the protected [racing.lastRaceGrade] to [DialogHandler] (alarm-clock carat policy) without reflection. */
    fun getLastRaceGrade(): com.steve1316.uma_android_automation.types.RaceGrade? = racing.lastRaceGrade

    fun isRetryingLostGoalRace(): Boolean = racing.bRetryingLostGoalRace

    /** Lets [DialogHandler]'s purchase_alarm_clock branch set Racing's protected skipped flag; reset in Racing's post-race cleanup. */
    fun markAlarmClockPolicySkipped() {
        racing.bAlarmClockPolicySkippedThisRace = true
    }

    /**
     * Updates the current date by detecting it on screen.
     *
     * @param isOnMainScreen If true, checks the Main screen for the date directly. Defaults to true.
     * @return True if the date changed, false otherwise.
     */
    open fun updateDate(isOnMainScreen: Boolean = true): Boolean {
        MessageLog.i(TAG, "[DATE] Attempting to update the current date.")
        val prevDay: Int = date.day
        if (!date.update(game.imageUtils, scenario = game.scenario, isOnMainScreen = isOnMainScreen)) {
            MessageLog.e(TAG, "[ERROR] updateDate:: date.update() failed to update date.")
            return false
        }

        if (date.day == prevDay) {
            Log.d(TAG, "[DEBUG] updateDate:: Date did not change.")
            return false
        } else {
            MessageLog.v(TAG, "[DATE] New date: $date")
            return true
        }
    }

    /**
     * Handles the Inheritance event if detected on the screen.
     *
     * @return True if the Inheritance event occurred and was accepted, false otherwise.
     */
    open fun handleInheritanceEvent(): Boolean {
        // Stop checking after Senior Year Early Apr.
        return if (date.day <= 56) {
            if (ButtonInheritance.click(game.imageUtils)) {
                MessageLog.v(TAG, "\n[INFO] Claimed an inheritance on $date.")
                trainee.bHasUpdatedAptitudes = false
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    /**
     * Attempts to recover the trainee's energy.
     *
     * @param sourceBitmap Optional pre-captured bitmap to analyze.
     * @return True if energy was successfully recovered, false otherwise.
     */
    open fun recoverEnergy(sourceBitmap: Bitmap? = null): Boolean {
        MessageLog.v(TAG, "\n[ENERGY] Now starting attempt to recover energy on $date.")
        val sourceBitmap: Bitmap = sourceBitmap ?: game.imageUtils.getSourceBitmap()

        // First, try to handle recreation date which also recovers energy if a date is available.
        // Skip recreation date if it's already completed (will only be used for mood recovery).
        if (
            !recreationDateCompleted &&
            IconRecreationDate.check(game.imageUtils, sourceBitmap = sourceBitmap) &&
            // With an active dating schedule the chain is the scheduler's: recover on the trainee instead of consuming a scheduled outing.
            handleRecreationDate(recoverMoodIfCompleted = false, doDateRecreation = !isScheduleActive())
        ) {
            MessageLog.v(TAG, "[ENERGY] Successfully recovered energy via recreation date.")
            return true
        }

        // Otherwise, fall back to the regular energy recovery logic.
        return when {
            ButtonRest.click(game.imageUtils, sourceBitmap = sourceBitmap) -> {
                ButtonOk.click(game.imageUtils, region = game.imageUtils.regionMiddle)
                // Another OK tap for the possibility of a scheduled race warning popup.
                game.wait(game.dialogWaitDelay)
                ButtonOk.click(game.imageUtils, region = game.imageUtils.regionMiddle)
                game.waitForLoading()
                MessageLog.v(TAG, "[ENERGY] Successfully recovered energy via rest.")
                true
            }

            ButtonRestAndRecreation.click(game.imageUtils, sourceBitmap = sourceBitmap) -> {
                ButtonOk.click(game.imageUtils, region = game.imageUtils.regionMiddle)
                // Another OK tap for the possibility of a scheduled race warning popup.
                game.wait(game.dialogWaitDelay)
                ButtonOk.click(game.imageUtils, region = game.imageUtils.regionMiddle)
                game.waitForLoading()
                MessageLog.v(TAG, "[ENERGY] Successfully recovered energy via Summer rest.")
                true
            }

            else -> {
                MessageLog.w(TAG, "[WARN] recoverEnergy:: Failed to recover energy. Moving on...")
                false
            }
        }
    }

    /**
     * Attempts to recover mood to maintain at least "Above Normal" status.
     *
     * @param sourceBitmap Optional pre-captured bitmap to analyze.
     * @param targetMood The mood level to recover to. Defaults to GREAT.
     * @return True if mood was successfully recovered, false otherwise.
     */
    open fun recoverMood(sourceBitmap: Bitmap? = null, targetMood: Mood = Mood.GOOD): Boolean {
        MessageLog.v(TAG, "\n[MOOD] Detecting current mood on $date.")

        val sourceBitmap = sourceBitmap ?: game.imageUtils.getSourceBitmap()

        // Make sure the trainee's mood is up to date.
        trainee.updateMood(game.imageUtils, sourceBitmap)

        MessageLog.v(TAG, "[MOOD] Detected mood to be ${trainee.mood}.")

        // Only recover mood if its below target mood and it's not Summer.
        return if (training.firstTrainingCheck && trainee.mood == Mood.NORMAL && !ButtonRestAndRecreation.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            MessageLog.v(
                TAG,
                "[MOOD] Current mood is Normal. Not recovering mood due to firstTrainingCheck flag being active. Will need to complete a training first before being allowed to recover mood.",
            )
            false
        } else if ((trainee.mood < targetMood) &&
            (
                ButtonRecreation.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
                    ButtonRestAndRecreation.check(
                        game.imageUtils,
                        sourceBitmap = sourceBitmap,
                    )
            )
        ) {
            MessageLog.v(TAG, "[MOOD] Current mood is not good (${trainee.mood}). Recovering mood now.")

            // Check if a date is available.
            if (!recreationDateCompleted && IconRecreationDate.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
                // With an active dating schedule the chain is the scheduler's: recover on the trainee instead of consuming a scheduled outing.
                if (handleRecreationDate(recoverMoodIfCompleted = true, doDateRecreation = !isScheduleActive())) {
                    MessageLog.v(TAG, "[MOOD] Successfully recovered mood via recreation date.")
                }
            } else {
                // Otherwise, recover mood as normal.
                // Note that if a date was already completed, the Recreation popup will still show so it will require an additional step to recover mood.
                // Do NOT speculatively set `recreationDateCompleted = true` here - `IconRecreationDate.check` may have
                // returned false due to a transient OCR / timing miss rather than the date actually being consumed.
                // Setting the flag here permanently disables recreation-date checking for the rest of the run; instead,
                // we rely on the genuine "complete" detection at line ~1274 (LabelRecreationDateComplete.check) AND
                // the daily reset in handleMainScreen so a fresh icon-check happens every turn.
                if (!ButtonRecreation.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
                    ButtonRestAndRecreation.click(game.imageUtils, sourceBitmap = sourceBitmap)
                }

                // Tap OK for the possibility of a scheduled race warning popup.
                game.wait(game.dialogWaitDelay)
                if (ButtonOk.click(game.imageUtils, region = game.imageUtils.regionMiddle)) {
                    game.waitForLoading()
                }

                // The Recreation popup is now open so an additional step is required to recover mood.
                if (LabelRecreationUmamusume.click(game.imageUtils)) {
                    MessageLog.v(TAG, "[MOOD] Recreation date is already completed. Recovering mood with the Umamusume now...")
                    game.waitForLoading()
                } else {
                    // Otherwise, dismiss the popup that says to confirm recreation if the user has not set it to skip the confirmation in their in-game settings.
                    ButtonOk.click(game.imageUtils, region = game.imageUtils.regionMiddle)
                    game.waitForLoading()
                }
                if (ButtonRestAndRecreation.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
                    MessageLog.v(TAG, "[MOOD] Successfully recovered mood via Summer rest.")
                } else {
                    MessageLog.v(TAG, "[MOOD] Successfully recovered mood.")
                }
            }
            true
        } else {
            MessageLog.i(TAG, "[MOOD] Current mood is good enough or its the Summer event. Moving on...")
            false
        }
    }

    /**
     * Whether the bot should spend this turn on a scheduled recreation outing. True only when the dating schedule is enabled, the chain is not yet
     * complete, a recreation date is on screen, the current turn is pinned (regular or Pure Passion), and no mandatory career-goal race is present.
     * Scheduled (in-game agenda) races do NOT block it - a pinned recreation outranks them.
     *
     * @param sourceBitmap An already-captured screen frame to reuse, or null to capture lazily only after the cheap pinned-turn checks pass.
     * @return True if the bot should perform a recreation outing this turn.
     */
    open fun shouldDoRecreationToday(sourceBitmap: Bitmap? = null): Boolean {
        if (!isScheduleActive() || recreationChainComplete || recreationDateCompleted) return false
        // A back-out (held final, no rows) does not advance the game; retrying before the date changes would reopen the same dialog forever.
        if (recreationAttemptFailedThisTurn) return false
        // Do an outing on a pinned turn, or - when catch-up is on - on any turn where a missed outing has left us behind schedule.
        val pinnedOrBehind =
            DatingSchedule.isPinnedRecreationTurn(date.day, recreationTurns, purePassionTurn) ||
                (enableRecreationCatchUp && DatingSchedule.isBehindSchedule(date.day, recreationTurns, recreationOutingsStarted))
        if (!pinnedOrBehind) return false
        // If only the final outing remains and this is not the Pure Passion turn, hold it: spend the turn on a normal action instead of opening the recreation.
        if (DatingSchedule.shouldHoldFinalOuting(recreationOutingsStarted, recreationTotalOutingsKnown, allowFinalOutingNow())) return false
        val bitmap = sourceBitmap ?: game.imageUtils.getSourceBitmap()
        // Mandatory goal races outrank a recreation; scheduled (in-game agenda) races do not. Upstream also blocks on IconGoalRibbon, deliberately
        // dropped: here the ribbon persists for any active objective (see cachedGoalRibbonDay) and would dead-block the schedule on most turns.
        if (cachedMandatoryRaceDay || IconRaceDayRibbon.check(game.imageUtils, sourceBitmap = bitmap)) {
            return false
        }
        if (!IconRecreationDate.check(game.imageUtils, sourceBitmap = bitmap)) return false
        return true
    }

    /**
     * Reads the in-game "Group Event Progress X/Y" (e.g. "3/4") from the open Choose Recreation Partner dialog by OCR-ing just to the right of the [LabelEventProgress] label.
     * This is the authoritative chain position - unlike the per-run counter it survives a bot restart and any dates done manually. The offset region is display-dependent,
     * so it may need on-device calibration. The debugName dumps the cropped region for tuning.
     *
     * @param sourceBitmap The current screen capture with the partner dialog open.
     * @return The (completed, total) outing counts, or null when the label or the numbers could not be read.
     */
    open fun getGroupEventProgress(sourceBitmap: Bitmap): Pair<Int, Int>? {
        val templateBitmap = LabelEventProgress.template.getBitmap(game.imageUtils) ?: return null
        val point = LabelEventProgress.findImageWithBitmap(game.imageUtils, sourceBitmap = sourceBitmap) ?: return null
        // The "X/Y" sits just right of the "Group Event Progress" pill, so anchor to the pill's right edge and match its height. This survives scrolling and resolution changes.
        val text =
            game.imageUtils.performOCROnRegion(
                sourceBitmap,
                game.imageUtils.relX(0.0, (point.x + (templateBitmap.width / 2)).toInt() + GROUP_PROGRESS_GAP_X),
                game.imageUtils.relY(0.0, (point.y - (templateBitmap.height / 2) - 4).toInt()),
                game.imageUtils.relWidth(GROUP_PROGRESS_WIDTH),
                game.imageUtils.relHeight(templateBitmap.height + 8),
                useThreshold = true,
                useGrayscale = true,
                scale = 2.0,
                ocrEngine = "tesseract",
                debugName = "GroupEventProgress",
            )
        val numbers = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
        if (numbers.size < 2) {
            MessageLog.w(TAG, "[WARN] getGroupEventProgress:: Could not read an X/Y progress from \"$text\".")
            return null
        }
        val completed = numbers[0]
        val total = numbers[1]
        if (total < 1 || completed < 0 || completed > total) {
            MessageLog.w(TAG, "[WARN] getGroupEventProgress:: Implausible progress $completed/$total from \"$text\".")
            return null
        }
        return Pair(completed, total)
    }

    /**
     * Backs out of the open Choose Recreation Partner dialog and waits for the screen to settle.
     *
     * @return Always false, so a caller can return it directly as the "did not start an outing" result.
     */
    private fun cancelPartnerDialog(): Boolean {
        ButtonCancel.click(game.imageUtils)
        game.waitForLoading()
        return false
    }

    /** Whether the final chain outing may be taken right now - only on the Pure Passion turn (or when the schedule or Pure Passion turn is off). */
    protected fun allowFinalOutingNow(): Boolean = DatingSchedule.allowFinalOuting(enableDatingSchedule, purePassionTurn, date.day)

    /** Enabled and not abandoned (the Pure Passion window passed with the chain unfinished). Protected so Trackblazer's budget override can exempt scheduled outings. */
    protected fun isScheduleActive(): Boolean = enableDatingSchedule && !DatingSchedule.isScheduleAbandoned(purePassionTurn, date.day, recreationChainComplete)

    /**
     * Handles the Recreation date event if detected on the screen.
     *
     * @param recoverMoodIfCompleted If true, recovers mood if the date was already completed.
     * @param allowFinalOuting If false, the final outing in the chain is held back (the bot backs out of the partner dialog) so it can be done on the Pure Passion turn.
     * @param doDateRecreation If true, advance the support-card date chain (tap the group event). If false, recreate with the trainee instead - used for opportunistic mood / energy recovery so the schedule alone drives the date chain.
     * @return True if the Recreation date event was successfully completed, false otherwise.
     */
    open fun handleRecreationDate(recoverMoodIfCompleted: Boolean = false, allowFinalOuting: Boolean = true, doDateRecreation: Boolean = true): Boolean {
        return if (ButtonRecreation.click(game.imageUtils)) {
            // Tap OK for the possibility of a scheduled race warning popup.
            game.wait(game.dialogWaitDelay)
            ButtonOk.click(game.imageUtils, region = game.imageUtils.regionMiddle)

            MessageLog.v(TAG, "\n[RECREATION_DATE] Recreation has a possible date available.")
            game.wait(1.0)
            // Multiple tries: a single-frame check against the popup's open animation can miss and send the flow to the dead Event Progress pill below.
            if (LabelRecreationDateComplete.check(game.imageUtils, tries = 3)) {
                MessageLog.v(TAG, "[RECREATION_DATE] Recreation date is already completed.")
                recreationDateCompleted = true
                recreationChainComplete = true
                if (recoverMoodIfCompleted) {
                    MessageLog.v(TAG, "[RECREATION_DATE] Mood requires recovery. Recovering mood with the Umamusume now...")
                    LabelRecreationUmamusume.click(game.imageUtils)
                    game.waitForLoading()
                    true
                } else {
                    MessageLog.i(TAG, "[RECREATION_DATE] Mood does not require recovery. Moving on...")
                    ButtonCancel.click(game.imageUtils)
                    // No date was consumed (already completed), so the Trackblazer override must not count it against its recreation budget.
                    false
                }
            } else {
                // If not complete, handle both regular support dates and Group Support Card dates.
                // Group Support Cards open a "Choose Recreation Partner" dialog.
                if (IconRecreationDateOpen.click(game.imageUtils)) {
                    game.wait(1.0)
                    MessageLog.v(TAG, "[RECREATION_DATE] Choose Recreation Partner dialog opened.")

                    if (!doDateRecreation) {
                        // The schedule drives the date chain, so an opportunistic recovery recreation goes to the trainee (normal recreation) and leaves the chain alone.
                        if (LabelRecreationUmamusume.click(game.imageUtils)) {
                            MessageLog.v(TAG, "[RECREATION_DATE] Recreating with the trainee (normal recreation), leaving the date chain for the schedule.")
                            game.waitForLoading()
                            true
                        } else {
                            // The trainee option was not found, so back out of the partner dialog rather than leave it open to desync the next turn.
                            MessageLog.w(TAG, "[WARN] handleRecreationDate:: Could not find the trainee recreation option. Backing out of the partner dialog.")
                            cancelPartnerDialog()
                        }
                    } else {
                        // Authoritative chain position (e.g. "3/4"), correct even after a restart or manual play.
                        getGroupEventProgress(game.imageUtils.getSourceBitmap())?.let { (completed, total) ->
                            recreationOutingsStarted = completed
                            recreationTotalOutingsKnown = total
                            MessageLog.i(TAG, "[RECREATION_DATE] Group event progress read as $completed/$total.")
                        }

                        if (DatingSchedule.shouldHoldFinalOuting(recreationOutingsStarted, recreationTotalOutingsKnown, allowFinalOuting)) {
                            // The next outing would complete the chain and trigger Pure Passion. This is not the Pure Passion turn, so back out and leave the final for that turn.
                            MessageLog.i(TAG, "[RECREATION_DATE] Next outing is the final one. Holding it for the Pure Passion turn. Backing out.")
                            cancelPartnerDialog()
                        } else {
                            // Use the ScrollList processor to find and click the first available date progress label.
                            val bResult =
                                ScrollList.processWithFallback(
                                    game,
                                    fallbackComponent = ButtonEventProgressChevron,
                                    bForceComponentDetection = true,
                                    onEntry = { _, entry ->
                                        MessageLog.i(TAG, "[INFO] Found entry: $entry at ${entry.bbox.cx}, ${entry.bbox.cy}")
                                        game.tap(entry.bbox.cx.toDouble(), entry.bbox.cy.toDouble())
                                        game.waitForLoading()
                                        true
                                    },
                                )

                            if (bResult) {
                                recreationOutingsStarted++
                                MessageLog.v(TAG, "[RECREATION_DATE] Started a date from the partner selection dialog. Outings started this run: $recreationOutingsStarted.")
                                game.waitForLoading()
                                true
                            } else {
                                // Back out rather than leave the dialog open to desync the next turn.
                                MessageLog.e(TAG, "[ERROR] handleRecreationDate:: Failed to find any date progress labels in the partner selection dialog. Backing out.")
                                cancelPartnerDialog()
                            }
                        }
                    }
                } else if (LabelEventProgress.click(game.imageUtils)) {
                    // Legacy support cards or situations where the dialog doesn't apply.
                    game.waitForLoading()
                    // A completed Pal row keeps its "Event Progress" pill but ignores taps; verify the popup closed before declaring success, or the campaign
                    // loops on the open popup.
                    if (LabelRecreationUmamusume.check(game.imageUtils)) {
                        MessageLog.w(
                            TAG,
                            "[RECREATION_DATE] Popup still open after tapping Event Progress - the date row is not selectable. Treating the date as completed.",
                        )
                        recreationDateCompleted = true
                        recreationChainComplete = true
                        if (recoverMoodIfCompleted) {
                            LabelRecreationUmamusume.click(game.imageUtils)
                            game.waitForLoading()
                            true
                        } else {
                            ButtonCancel.click(game.imageUtils)
                            false
                        }
                    } else {
                        recreationOutingsStarted++
                        MessageLog.v(TAG, "[RECREATION_DATE] Recreation date can be done.")
                        true
                    }
                } else {
                    MessageLog.e(TAG, "[ERROR] handleRecreationDate:: Failed to find a way to start the recreation date.")
                    game.waitForLoading()
                    false
                }
            }
        } else {
            false
        }
    }

    /**
     * Handles the Crane Game event by attempting to complete it with three long-press attempts.
     *
     * @return True if the crane game was successfully completed, false otherwise.
     */
    open fun handleCraneGame(): Boolean {
        MessageLog.v(TAG, "\n[CRANE_GAME] Starting Crane Game attempt...")

        // Find the Crane Game button location.
        val buttonLocation = ButtonCraneGame.find(game.imageUtils)
        val buttonPoint = buttonLocation.first
        if (buttonPoint == null) {
            MessageLog.w(TAG, "[WARN] handleCraneGame:: Could not find the Crane Game button. Aborting.")
            return false
        }

        val imageName = ButtonCraneGame.template.path
        val pressDurations = listOf(1.90, 1.00, 0.65)

        // Perform three attempts with different press durations.
        for (attempt in 1..3) {
            val pressDuration = pressDurations[attempt - 1]
            MessageLog.i(TAG, "[CRANE_GAME] Attempt $attempt: Long pressing for ${pressDuration}s...")

            // Perform long press on the button.
            game.gestureUtils.tap(buttonPoint.x, buttonPoint.y, imageName, longPress = true, pressDuration = pressDuration)

            if (attempt < 3) {
                // After attempts 1 and 2, wait for the button to reappear.
                MessageLog.i(TAG, "[CRANE_GAME] Waiting for the Crane Game button to reappear after attempt $attempt...")
                var buttonReappeared = false
                val maxWaitTime = 30.0
                val checkInterval = 1.0
                var elapsedTime = 0.0

                while (elapsedTime < maxWaitTime) {
                    if (ButtonCraneGame.check(game.imageUtils)) {
                        buttonReappeared = true
                        break
                    }
                    game.wait(checkInterval, skipWaitingForLoading = true)
                    elapsedTime += checkInterval
                }

                if (!buttonReappeared) {
                    MessageLog.w(TAG, "[WARN] handleCraneGame:: The Crane Game button did not reappear within $maxWaitTime seconds after attempt $attempt.")
                }

                game.wait(1.0)
            } else {
                MessageLog.v(TAG, "[CRANE_GAME] Final attempt completed.")
                return true
            }
        }

        return false
    }

    /**
     * Handles the skill list screen to purchase skills.
     *
     * This function initiates the skill purchasing process using the specified
     * skill plan. If no plan name is provided, the default skill plan is used.
     *
     * @param skillPlanName The optional name of the skill plan to use.
     * @param trigger Why the skill screen was opened, recorded on the skill-spend telemetry record.
     *   Null when the caller has no reason to name (the debug harness).
     * @return True if the skill purchasing process was successful, false otherwise.
     */
    open fun handleSkillListScreen(skillPlanName: String? = null, trigger: SkillCheckTrigger? = null): Boolean {
        StatusBoard.action("skills", null)
        MessageLog.v(TAG, "[SKILLS] Beginning process to purchase skills...")
        return skillPlan.start(skillPlanName, trigger)
    }

    /** Same helper as the [CAREER_END] record, so a mid-career record joins the arm its career lands in; two fingerprint implementations would
     * silently split arms. */
    internal fun currentConfigFingerprint(): String = outcomeConfigFingerprint(BuildConfig.VERSION_NAME, outcomeConfigSnapshot)

    /** Re-reads Skill Points from the Main screen to confirm a candidate high-water crossing: the per-turn read is the one trigger input nothing
     * else validates (a contaminated read once dispatched a purchase whose skill screen showed 71 points against a 350 bar). Not the skill screen:
     * opening it is the navigation this gate prevents. A rejection rewrites the trusted value so the next turn re-arms, and never marks the
     * threshold handled, so a genuine later crossing still fires. Returns true to dispatch. */
    private fun confirmHighWaterCrossing(): Boolean {
        val candidate: Int = trainee.skillPoints
        val fresh: Int = game.imageUtils.determineSkillPoints()
        return when (confirmHighWater(fresh, skillPointsRequired)) {
            SkillPointConfirmation.CONFIRMED -> {
                if (fresh != candidate) {
                    trainee.skillPoints = fresh
                }
                true
            }
            SkillPointConfirmation.REJECTED -> {
                MessageLog.w(
                    TAG,
                    "[SKILLS] High-water candidate $candidate rejected: a fresh read says $fresh, below the $skillPointsRequired threshold. " +
                        "Not opening the skill screen. The threshold stays eligible for a later crossing.",
                )
                trainee.skillPoints = fresh
                false
            }
            SkillPointConfirmation.UNREADABLE -> {
                MessageLog.w(
                    TAG,
                    "[SKILLS] High-water candidate $candidate could not be confirmed (fresh read failed). Skipping this turn; the threshold stays eligible.",
                )
                false
            }
        }
    }

    /** Records a careerComplete pass whose Learn screen never opened; [SkillPlan] cannot report it because its session never started. Best-effort:
     * must not change the completion path. */
    private fun recordAbortedSkillEntry() {
        runCatching {
            val record =
                SkillSpendTelemetry.buildRecord(
                    timestamp = System.currentTimeMillis(),
                    outcome = SkillSpendOutcome.ABORTED_ENTRY,
                    trigger = SkillCheckTrigger.CAREER_COMPLETE,
                    planKey = PLAN_CAREER_COMPLETE,
                    strategy = skillPlan.skillPlans[PLAN_CAREER_COMPLETE]?.strategy?.name,
                    trainee = trainee.name.ifEmpty { null }?.replace(" ", "_"),
                    scenario = game.scenario.ifEmpty { null }?.replace(" ", "_"),
                    fp = currentConfigFingerprint(),
                    turn = date.day,
                    // The Learn screen never opened, so the last per-turn OCR is the only points reading.
                    spBefore = trainee.skillPoints,
                    spAfter = trainee.skillPoints,
                    proposed = emptyList(),
                    confirmed = emptyList(),
                    skipped = emptyList(),
                )
            OutcomeCorpus.append(game.myContext, record)
        }.onFailure {
            MessageLog.w(TAG, "[SKILL_SPEND] Failed to append the aborted-entry record: $it")
        }
    }

    /**
     * Opens the Umamusume Details dialog to update trainee aptitudes.
     *
     * This function only opens the dialog - the actual aptitude update is performed
     * by [handleDialogs] when it processes the "umamusume_details" dialog.
     */
    open fun openAptitudesDialog() {
        MessageLog.d(TAG, "[DEBUG] openAptitudesDialog:: Opening aptitudes dialog...")
        ButtonHomeFullStats.click(game.imageUtils)
        game.wait(game.dialogWaitDelay, skipWaitingForLoading = true)
    }

    /**
     * Opens the Umamusume Class dialog to update trainee fan count.
     *
     * This function only opens the dialog - the actual fan count update is performed
     * by [handleDialogs] when it processes the "umamusume_class" dialog.
     */
    open fun openFansDialog() {
        MessageLog.d(TAG, "[DEBUG] openFansDialog:: Opening fans dialog...")
        ButtonHomeFansInfo.click(game.imageUtils, region = game.imageUtils.regionBottomHalf, tries = 10)
        bHasTriedCheckingFansToday = true
        game.wait(game.dialogWaitDelay, skipWaitingForLoading = true)
    }

    /**
     * Detects the trainee's current fan count class from the main screen.
     *
     * This reads the fan count class label directly from the screen using OCR
     * without opening any dialogs.
     *
     * @param bitmap Optional pre-captured bitmap to analyze.
     * @return The detected [FanCountClass], or null if detection failed.
     */
    open fun getFanCountClass(bitmap: Bitmap? = null): FanCountClass? {
        val bitmap: Bitmap = bitmap ?: game.imageUtils.getSourceBitmap()
        val templateBitmap: Bitmap? = ButtonHomeFansInfo.template.getBitmap(game.imageUtils)
        if (templateBitmap == null) {
            MessageLog.e(TAG, "[ERROR] getFanCountClass:: Could not get template bitmap for ButtonHomeFansInfo: ${ButtonHomeFansInfo.template.path}.")
            return null
        }
        val point: Point? = ButtonHomeFansInfo.findImageWithBitmap(game.imageUtils, sourceBitmap = bitmap)
        if (point == null) {
            MessageLog.w(TAG, "[WARN] getFanCountClass:: Could not find ButtonHomeFansInfo.")
            return null
        }

        val bbox =
            BoundingBox(
                x = game.imageUtils.relX(0.0, (point.x - (templateBitmap.width / 2)).toInt() - 180),
                // Add a small buffer to vertical component.
                y = game.imageUtils.relY(0.0, (point.y - 16).toInt()),
                w = game.imageUtils.relWidth(180),
                // 32px minimum for Google ML Kit.
                h = game.imageUtils.relHeight(32),
            )

        val text: String =
            game.imageUtils.performOCROnRegion(
                bitmap,
                bbox.x,
                bbox.y,
                bbox.w,
                bbox.h,
                useThreshold = false,
                useGrayscale = true,
                scale = 1.0,
                ocrEngine = "tesseract",
                debugName = "getFanCountClass",
            )
        val fanCountClass: FanCountClass? = FanCountClass.fromName(text.replace(" ", "_"))
        if (fanCountClass == null) {
            MessageLog.w(TAG, "[WARN] getFanCountClass:: Failed to match text to a FanCountClass: $text")
        }
        return fanCountClass
    }

    /**
     * Called when the bot encounters a scheduled race and reaches the Race Prep screen
     * before starting the race.
     *
     * This provides a hook for scenarios to perform actions such as using race items.
     */
    open fun onScheduledRacePrepScreen() {}

    /**
     * Handles the fallback logic when racing fails.
     *
     * This includes checking for mandatory race detection and falling back to training.
     *
     * @return A [RaceFallbackOutcome] whose [RaceFallbackOutcome.shouldStopForMandatoryRace] is true when
     * a mandatory race forces the bot to stop, and whose [RaceFallbackOutcome.turnAdvanced] is true when
     * the fallback trained or recovered (advancing the turn) rather than backing out onto the same turn.
     */
    open fun handleRaceEventFallback(): RaceFallbackOutcome {
        if (racing.detectedMandatoryRaceCheck) {
            MessageLog.v(TAG, "\n[END] Stopping bot due to detection of Mandatory Race.")
            game.notificationMessage = "Stopping bot due to detection of Mandatory Race."
            if (DiscordUtils.enableDiscordNotifications) {
                DiscordUtils.queue.add("```diff\n- ${MessageLog.getSystemTimeString()} Stopping bot due to detection of Mandatory Race.\n```")
            }
            return RaceFallbackOutcome(shouldStopForMandatoryRace = true, turnAdvanced = false)
        }
        ButtonBack.click(game.imageUtils)
        ButtonCancel.click(game.imageUtils)
        ButtonClose.click(game.imageUtils)
        game.wait(1.0)
        // turnAdvanced covers facility training, forced Wit and recovery; the selected-stat return is null on recovery paths that still advance.
        val trainingOutcome = training.handleTrainingWithOutcome()
        return RaceFallbackOutcome(shouldStopForMandatoryRace = false, turnAdvanced = trainingOutcome.turnAdvanced)
    }

    /**
     * Performs miscellaneous checks to resolve instances where the bot might be stuck.
     *
     * @return True if the checks passed, false if the bot encountered a warning popup and needs to exit.
     */
    open fun performMiscChecks(): Boolean {
        MessageLog.i(TAG, "\n[MISC] Beginning check for misc cases...")

        val sourceBitmap = game.imageUtils.getSourceBitmap()

        if (game.enablePopupCheck && ButtonCancel.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            MessageLog.v(TAG, "\n[END] Bot may have encountered a warning popup. Exiting now...")
            game.notificationMessage = "Bot may have encountered a warning popup"
            if (DiscordUtils.enableDiscordNotifications) {
                DiscordUtils.queue.add("```diff\n- ${MessageLog.getSystemTimeString()} Bot may have encountered a warning popup. Exiting now...\n```")
            }
            throw CampaignBreakpointException(game.notificationMessage)
        } else if (ButtonNext.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
            // Now confirm the completion of a Training Goal popup.
            MessageLog.i(TAG, "[MISC] Popup detected that needs to be dismissed with the \"Next\" button.")
            recordMiscNextOrBack(nextNow = true)
            game.wait(2.0)
            ButtonNext.click(game.imageUtils)
            game.wait(1.0)
            return true
        } else if (ButtonCraneGame.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            if (enableCraneGameAttempt) {
                handleCraneGame()
                return true
            } else {
                // Stop when the bot has reached the Crane Game Event.
                MessageLog.v(TAG, "\n[END] Bot will stop due to the detection of the Crane Game Event.")
                game.notificationMessage = "Bot will stop due to the detection of the Crane Game Event."
                if (DiscordUtils.enableDiscordNotifications) {
                    DiscordUtils.queue.add("```diff\n- ${MessageLog.getSystemTimeString()} Bot will stop due to the detection of the Crane Game Event.\n```")
                }
                throw CampaignBreakpointException(game.notificationMessage)
            }
        } else if (
            LabelOrdinaryCuties.check(game.imageUtils, sourceBitmap = sourceBitmap) &&
            ButtonCraneGameOk.check(game.imageUtils, sourceBitmap = sourceBitmap)
        ) {
            ButtonCraneGameOk.click(game.imageUtils, sourceBitmap = sourceBitmap)
            game.waitForLoading()
            MessageLog.v(TAG, "[CRANE_GAME] Event exited.")
            return true
        } else if (ButtonNextRaceEnd.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
            MessageLog.i(TAG, "[MISC] Ended a leftover race.")
            // Clicking this button triggers connection to server.
            game.waitForLoading()
            return true
        } else if (IconRaceNotEnoughFans.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            MessageLog.i(TAG, "[MISC] There was a popup about insufficient fans.")
            racing.encounteredRacingPopup = true
            ButtonCancel.click(game.imageUtils, sourceBitmap = sourceBitmap)
            return true
        } else if (LabelUmamusumeClassFans.check(game.imageUtils, sourceBitmap = sourceBitmap) && ButtonClose.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
            // Dismiss ONLY the "Umamusume Class" popup the bot opens itself: its blue title bar is invisible to the green-header detector, so it would trap
            // the bot. Scope strictly via LabelUmamusumeClassFans: a blanket ButtonClose would also close the Details / Strategy dialogs handleDialogs must
            // read later (aptitudes stay "G" in an open/close loop).
            MessageLog.i(TAG, "[MISC] Dismissed the Umamusume Class popup via its Close button.")
            game.wait(0.5)
            return true
        } else if (sourceBitmap.width == 1080 && sourceBitmap.height == 1920 && pagedHelpDialogPresent(SparkPixelSampler { x, y -> sourceBitmap.getPixel(x, y) })) {
            // Its Back button only turns to the previous page, which the Next branch then turns forward again.
            bMiscStepTakenLastTick = true
            pagedHelpCloseTaps++
            if (pagedHelpCloseTaps > maxPagedHelpCloseTaps) {
                game.imageUtils.saveBitmap(filename = "misc_paged_help_stuck", fullRes = true)
                throw InterruptedException(
                    "Bot tapped Close on a paged help dialog $maxPagedHelpCloseTaps times without it closing. Stopping. " +
                        "A screenshot was saved to the temp folder as misc_paged_help_stuck.",
                )
            }
            MessageLog.i(TAG, "[MISC] Paged help dialog detected; closing it with its Close button (tap $pagedHelpCloseTaps).")
            game.tapCoordinate(PagedHelpGeometry.CLOSE_X.toDouble(), PagedHelpGeometry.CLOSE_Y.toDouble(), "paged_help_close")
            game.wait(1.0)
            return true
        } else if (ButtonBack.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
            bMiscBackPressedThisTick = true
            consecutiveMiscBackPresses++
            if (consecutiveMiscBackPresses == 2) {
                // An open notification shade absorbs back-presses and can let a misc match tap the bot's own STOP BOT notification action; clear it.
                dismissNotificationShade("misc back-press streak")
            }
            if (consecutiveMiscBackPresses >= maxConsecutiveMiscBackPresses) {
                game.imageUtils.saveBitmap(filename = "misc_backpress_stuck", fullRes = true)
                throw InterruptedException(
                    "Bot pressed Back $consecutiveMiscBackPresses consecutive times without reaching a known screen - the press is not changing anything. Stopping. " +
                        "A screenshot was saved to the temp folder as misc_backpress_stuck.",
                )
            }
            recordMiscNextOrBack(nextNow = false)
            MessageLog.i(TAG, "[MISC] Navigating back a screen since all the other misc checks have been completed. (consecutive back-presses: $consecutiveMiscBackPresses)")
            // ButtonBack.click does not auto-wait (Components.tap bypasses Game.tap). 0.5s settles the animation; game.wait() still polls waitForLoading().
            game.wait(0.5)
            return true
        } else if (ButtonSkip.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
            MessageLog.i(TAG, "[MISC] Clicked skip button.")
            return true
        } else if (!BotService.isRunning) {
            MessageLog.v(TAG, "\n[END] BotService is not running. Exiting now...")
            throw InterruptedException()
        } else {
            MessageLog.i(TAG, "[MISC] Did not detect any popups or the Crane Game on the screen. Moving on...")
        }

        return false
    }

    private fun recordMiscNextOrBack(nextNow: Boolean) {
        bMiscStepTakenLastTick = true
        miscNextBackSwaps = miscNextBackSwapStreak(miscNextBackSwaps, lastMiscStepWasNext, nextNow)
        lastMiscStepWasNext = nextNow
        if (miscNextBackSwaps >= maxMiscNextBackSwaps) {
            game.imageUtils.saveBitmap(filename = "misc_next_back_loop", fullRes = true)
            throw InterruptedException(
                "Bot alternated Next and Back $miscNextBackSwaps times without reaching a known screen - each press undid the other. Stopping. " +
                    "A screenshot was saved to the temp folder as misc_next_back_loop.",
            )
        }
    }

    /**
     * Handles all main screen logic including daily updates, racing decisions, and training.
     *
     * This is the primary decision-making function that determines what action the bot
     * should take when at the main screen. It handles date changes, aptitude/fan updates,
     * race detection, mood recovery, and training.
     *
     * @return True if the main screen was detected and handled, false otherwise.
     */
    open fun handleMainScreen(): Boolean {
        if (!checkMainScreen()) {
            return false
        }

        // Scenario-specific pre-update hook.
        onBeforeMainScreenUpdate()

        // The hook above may have left the main screen (e.g. a misfired Trackblazer shop entry): bail out rather than run updateDate() against the
        // wrong UI, where date-OCR offsets can fall outside the bitmap ("y must be >= 0").
        if (!checkMainScreen()) {
            MessageLog.w(TAG, "[WARN] handleMainScreen:: After onBeforeMainScreenUpdate, bot is no longer on the main screen. Bailing out so the main loop can re-detect.")
            return false
        }

        // Perform first-time setup of loading the user's race agenda if needed.
        racing.loadUserRaceAgenda()

        val sourceBitmap = game.imageUtils.getSourceBitmap()

        // Operations to be done every time the date changes.
        // Skip if we've already checked the date this turn and no game-advancing action was taken.
        if (!bHasCheckedDateThisTurn) {
            val dateChanged = updateDate()
            if (dateChanged || !trainee.bHasUpdatedStats) {
                // Reset common daily flags.
                racing.encounteredRacingPopup = false
                racing.raceRepeatWarningCheck = false
                bHasTriedCheckingFansToday = false
                bHasCheckedForMaidenRaceToday = false
                // The flag only short-circuits re-checks within one recovery sequence; reset it so a fresh icon detection runs every turn.
                recreationDateCompleted = false
                recreationAttemptFailedThisTurn = false

                // Reset scenario-specific daily flags.
                resetDailyFlags()

                // Perform parallel turn-start updates (stats, mood, energy, fans, etc.).
                performTurnStartUpdates(sourceBitmap)

                // Opens this turn's Decision Report window; emit() flushes it after the action executes.
                decisionTracer?.startTurn(
                    date = date,
                    trainee = trainee,
                    settings = DecisionTracer.SettingsSnapshot().add("Mood Floor", moodFloor),
                )

                // The CareerState build latch is rearmed from the action-completion lifecycle (see executeAction), not here, so a turn still snapshots when
                // date OCR failed and the tracer opened no window.
                careerStateLatch.markTracerWindowOpened()

                // Debug build or Debug Mode: one labeled positive fixture per new turn for the offline replay corpus.
                if ((com.steve1316.uma_android_automation.BuildConfig.DEBUG || game.debugMode) && dateChanged) {
                    game.imageUtils.saveFixture(
                        "turn_t${date.day}",
                        sourceBitmap,
                        mapOf(
                            "scenario" to game.scenario,
                            "trainee" to trainee.name,
                            "turn" to date.day,
                            "date" to date.toString(),
                            "spd" to trainee.stats.speed,
                            "sta" to trainee.stats.stamina,
                            "pwr" to trainee.stats.power,
                            "grt" to trainee.stats.guts,
                            "wit" to trainee.stats.wit,
                            "energy" to trainee.energy,
                            "mood" to trainee.mood.name,
                            "fans" to trainee.fans,
                            "skillPts" to trainee.skillPoints,
                        ),
                    )
                }

                // Scenario-specific post-update hook.
                onAfterTurnStartUpdates()
            }

            // Since we're at the main screen, we don't need to worry about this
            // flag anymore since we will update our aptitudes here if needed.
            trainee.bTemporaryRunningStyleAptitudesUpdated = false

            if (!trainee.bHasUpdatedAptitudes) {
                openAptitudesDialog()
                if (tryHandleAllDialogs()) return true
            }

            val bIsScheduledRaceDayInitial = LabelScheduledRace.check(game.imageUtils, sourceBitmap = sourceBitmap)
            val bIsMandatoryRaceDayInitial = IconRaceDayRibbon.check(game.imageUtils, sourceBitmap = sourceBitmap)

            cachedScheduledRaceDay = bIsScheduledRaceDayInitial
            cachedMandatoryRaceDay = bIsMandatoryRaceDayInitial
            // Not folded into cachedMandatoryRaceDay: the goal ribbon persists for any active objective and must never reach decideNextAction's forced-RACE branch.
            cachedGoalRibbonDay = IconGoalRibbon.check(game.imageUtils, sourceBitmap = sourceBitmap)

            if (!date.bIsFinaleSeason && !bIsMandatoryRaceDayInitial && !bIsScheduledRaceDayInitial && bNeedToCheckFans && !bHasTriedCheckingFansToday) {
                openFansDialog()
                if (tryHandleAllDialogs()) return true
            }

            // Mark that we've checked the date this turn.
            bHasCheckedDateThisTurn = true
        }

        // Perform global checks (skill point check, stop at date, finals stop).
        // These can throw CampaignBreakpointException or InterruptedException to stop the bot.
        if (performGlobalChecks()) {
            return true
        }

        // Compute the estimated overall rank, then print the trainee info after all turn-start updates and potential fan count updates.
        updateEstimatedRank()
        trainee.logInfo()

        setFastSkipIfOff("main screen")

        // Scenario-specific main screen entry hook (e.g. for item usage).
        onMainScreenEntry()

        // Snapshot the pre-decision state once per turn, after all state-changing prep. It shares the decision_trace gate so both streams record
        // together and stay joinable by seq. Non-fatal: it must never change the decision.
        if (factualCorpusEnabled && careerStateLatch.shouldBuild()) {
            // currentTurnSeq stays null until the build succeeds, so a swallowed build leaves the trace without a seq rather than a stale one; the
            // counter still advances so seq N is never reused.
            val seq = careerStateSeq.allocate()
            try {
                val careerState = buildCareerState()
                shadowCareerState = careerState
                // Retain the seq before the append so a later serialize/append failure still leaves the trace correctly stamped.
                careerStateSeq.retain(seq)
                appendCareerState(careerState, seq)
                // Debug gate keeps corpus-only runs free of per-turn log noise.
                if (debugDiagnosticsEnabled) compareCareerStateToTracer(careerState)
            } catch (e: Exception) {
                // Swallowed so a snapshot/compare fault cannot stop or alter the turn.
                Log.e(TAG, "[CAREER_STATE] shadow snapshot failed (ignored): ${e.message}")
            }
        }

        publishTurnStatus()

        // Cleared so a completed-race fact from a prior turn can never attach to this turn's trace.
        pendingEnteredRace.clear()

        // Decision-making process.
        val action = decideNextAction()
        // Reuse the cached value: nothing since performTurnStartUpdates() could have changed scheduled-race status.
        val actionExecuted = executeAction(action, cachedScheduledRaceDay)
        // Flushed after the action so selections recorded inside executeAction land in the block; emit() is idempotent per turn.
        decisionTracer?.emit()
        return actionExecuted
    }

    private fun publishTurnStatus() {
        val (year, label) = StatusBoard.dateLabels(date.year.longName, date.phase.name, date.month.name, date.day, game.scenario)
        val stats = listOf(trainee.stats.speed, trainee.stats.stamina, trainee.stats.power, trainee.stats.guts, trainee.stats.wit)
        StatusBoard.careerTurn(trainee.name.ifEmpty { null }, game.scenario, year, label, date.day, stats, trainee.energy, trainee.mood.name)
        ProgressNotification.refresh()
    }

    /** Mirrors the UmaTools calculator (an approximation of the game's unpublished formula), so it is labeled "Est." wherever shown. */
    fun updateEstimatedRank() {
        if (!trainee.bHasUpdatedStats) return
        val aptitudes =
            RankAptitudes(
                turf = trainee.trackSurfaceAptitudes[TrackSurface.TURF]?.name ?: "G",
                dirt = trainee.trackSurfaceAptitudes[TrackSurface.DIRT]?.name ?: "G",
                sprint = trainee.trackDistanceAptitudes[TrackDistance.SPRINT]?.name ?: "G",
                mile = trainee.trackDistanceAptitudes[TrackDistance.MILE]?.name ?: "G",
                medium = trainee.trackDistanceAptitudes[TrackDistance.MEDIUM]?.name ?: "G",
                long = trainee.trackDistanceAptitudes[TrackDistance.LONG]?.name ?: "G",
                front = trainee.runningStyleAptitudes[RunningStyle.FRONT_RUNNER]?.name ?: "G",
                pace = trainee.runningStyleAptitudes[RunningStyle.PACE_CHASER]?.name ?: "G",
                late = trainee.runningStyleAptitudes[RunningStyle.LATE_SURGER]?.name ?: "G",
                end = trainee.runningStyleAptitudes[RunningStyle.END_CLOSER]?.name ?: "G",
            )
        val skillInputs =
            trainee.ownedSkillNames.toList().mapNotNull { skillName ->
                val data = game.skillDatabase.getSkillData(skillName) ?: return@mapNotNull null
                SkillScoreInput(data.evalPt, SkillDatabase.deriveCheckType(data.condition, data.precondition))
            }
        trainee.estimatedRank =
            estimateRank(
                trainee.stats.speed,
                trainee.stats.stamina,
                trainee.stats.power,
                trainee.stats.guts,
                trainee.stats.wit,
                skillInputs,
                aptitudes,
                trainee.uniqueSkillLevel,
            )
    }

    /**
     * Performs parallel turn-start updates for stats, skill points, mood, energy, and racing requirements.
     *
     * @param sourceBitmap Current screen bitmap.
     */
    open fun performTurnStartUpdates(sourceBitmap: Bitmap) {
        // Update the fan count class every time we're at the main screen.
        val fanCountClass: FanCountClass? = getFanCountClass(sourceBitmap)
        if (fanCountClass != null) {
            trainee.fanCountClass = fanCountClass
        }

        val skillPointsLocation = LabelStatTableHeaderSkillPoints.findImageWithBitmap(game.imageUtils, sourceBitmap = sourceBitmap)

        if (!BotService.isRunning) {
            return
        }

        // Use CountDownLatch to run the operations in parallel.
        // 1 racingRequirements (skipped during summer) + 5 stats + 1 skill points + 1 mood + 1 energy = 9 (or 8) threads.
        val latch = if (date.isSummer() && !(racing.skipSummerTrainingForAgenda && racing.enableUserInGameRaceAgenda)) CountDownLatch(8) else CountDownLatch(9)

        MessageLog.disableOutput = true

        // Threads 1-5: Update stats.
        trainee.updateStats(game.imageUtils, sourceBitmap, skillPointsLocation, latch)

        // Thread 6: Update skill points.
        Thread {
            try {
                trainee.updateSkillPoints(game.imageUtils, sourceBitmap, skillPointsLocation)
            } catch (e: Exception) {
                MessageLog.e(TAG, "[ERROR] performTurnStartUpdates:: Error in updateSkillPoints thread: ${e.stackTraceToString()}")
            } finally {
                latch.countDown()
            }
        }.apply { isDaemon = true }.start()

        // Thread 7: Update mood.
        Thread {
            try {
                trainee.updateMood(game.imageUtils, sourceBitmap)
            } catch (e: Exception) {
                MessageLog.e(TAG, "[ERROR] performTurnStartUpdates:: Error in updateMood thread: ${e.stackTraceToString()}")
            } finally {
                latch.countDown()
            }
        }.apply { isDaemon = true }.start()

        // Thread 8: Update racing requirements.
        if (!date.isSummer() || (racing.skipSummerTrainingForAgenda && racing.enableUserInGameRaceAgenda)) {
            Thread {
                try {
                    racing.checkRacingRequirements(sourceBitmap)
                } catch (e: Exception) {
                    MessageLog.e(TAG, "[ERROR] performTurnStartUpdates:: Error in checkRacingRequirements thread: ${e.stackTraceToString()}")
                } finally {
                    latch.countDown()
                }
            }.apply { isDaemon = true }.start()
        }

        // Thread 9: Update energy.
        Thread {
            try {
                trainee.updateEnergy(game.imageUtils)
            } catch (e: Exception) {
                MessageLog.e(TAG, "[ERROR] performTurnStartUpdates:: Error in updateEnergy thread: ${e.stackTraceToString()}")
            } finally {
                latch.countDown()
            }
        }.apply { isDaemon = true }.start()

        // Wait for all threads to complete.
        // 5s is the worst-case bound for the parallel update set (5 stat OCRs + skill points + mood +
        // racing reqs + energy on a single bitmap typically completes in well under 2 s on a healthy
        // device). The previous 10s timeout served only to bound a hung thread - narrowing it to 5s
        // halves the worst-case turn-start stall when something genuinely doesn't decrement the latch
        // (e.g. an OCR thread stuck inside Tesseract). On timeout the bot logs and proceeds with stale
        // values for one turn rather than crashing.
        try {
            latch.await(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            MessageLog.e(TAG, "[ERROR] performTurnStartUpdates:: Date change operations threads timed out.")
        } finally {
            MessageLog.disableOutput = false
        }
    }

    /**
     * Performs global bot checks such as skill point thresholds and target date stops.
     *
     * @return True if a check was handled, false otherwise.
     */
    open fun performGlobalChecks(): Boolean {
        // Owned here because the flag is this instance's mutable state; decideSkillCheck only reads the resulting value.
        if (trainee.skillPoints < skillPointsRequired) {
            bHasHandledSkillPointCheck = false
            skillPointCheckAttempts = 0
        }

        // The two adaptive-only trigger inputs. Everything defaults inert (manual mode, rank objective, disabled plan, OCR failure, finals adjacency).
        val adaptive = resolvedSkillThreshold.mode == SkillSpendMode.ADAPTIVE
        produceGoalSnapshotIfDue(adaptive)
        val critical = computeCriticalRace(adaptive)
        val affordableCandidate =
            if (adaptive && skillSpendObjective.allowsPlannedSkillAffordable()) {
                val planNames = skillPlan.skillPlans[PLAN_SKILL_POINT_CHECK]?.skillNames ?: emptyList()
                plannedSkillEvidence.affordableCandidate(planNames, trainee.skillPoints)
            } else {
                null
            }

        // Pure decision: navigation, the Main-screen confirmation, the attempt counters and the flags all stay below.
        val skillCheck: SkillCheckDecision =
            decideSkillCheck(
                skillPoints = trainee.skillPoints,
                highWaterThreshold = skillPointsRequired,
                enableSkillPointCheck = enableSkillPointCheck,
                highWaterPlanEnabled = skillPlan.skillPlans[PLAN_SKILL_POINT_CHECK]?.bIsEnabled ?: false,
                alreadyHandledHighWater = bHasHandledSkillPointCheck,
                day = date.day,
                preFinalsPlanEnabled = skillPlan.skillPlans[PLAN_PRE_FINALS]?.bIsEnabled ?: false,
                alreadyHandledPreFinals = bHasHandledPreFinalsCheck,
                criticalRaceDue = critical != null,
                affordableSkillDue = affordableCandidate != null,
            )

        // Handled/re-arm bookkeeping differs per trigger, so each gets its own branch instead of piggybacking on the high-water flags.
        if (skillCheck.action == SkillCheckAction.RUN_PLAN && skillCheck.trigger == SkillCheckTrigger.CRITICAL_RACE && critical != null) {
            if (!checkMainScreen()) {
                MessageLog.i(TAG, "[SKILLS] Skipping the critical-race skill session for now - not confirmed on the Main screen.")
                return false
            }
            MessageLog.i(TAG, "[SKILLS] Critical race '${critical.raceName}' in ${critical.turnsUntil} turn(s) (${critical.source}). Spending before it...")
            activeTriggerContext =
                SkillTriggerContext(
                    trigger = SkillCheckTrigger.CRITICAL_RACE,
                    criticalRace = critical.raceName,
                    criticalRaceSource = critical.source,
                    turnsUntilRace = critical.turnsUntil,
                )
            ButtonSkills.click(game.imageUtils)
            game.wait(1.0)
            val handled = handleSkillListScreen(PLAN_SKILL_POINT_CHECK, SkillCheckTrigger.CRITICAL_RACE)
            activeTriggerContext = null
            if (handled) {
                // A completed session (even one that bought nothing) covers this race turn; an aborted one leaves the window open for a retry next turn.
                lastCriticalRaceTurnHandled = critical.raceTurn
            } else {
                MessageLog.w(TAG, "[WARN] performGlobalChecks:: Critical-race skill session did not complete. The 1-2 turn window allows one retry next turn.")
            }
            return true
        }
        if (skillCheck.action == SkillCheckAction.RUN_PLAN && skillCheck.trigger == SkillCheckTrigger.PLANNED_SKILL_AFFORDABLE && affordableCandidate != null) {
            if (!checkMainScreen()) {
                MessageLog.i(TAG, "[SKILLS] Skipping the planned-skill session for now - not confirmed on the Main screen.")
                return false
            }
            val (skillName, observedPrice) = affordableCandidate
            MessageLog.i(TAG, "[SKILLS] Planned skill '$skillName' affordable at observed $observedPrice SP (have ${trainee.skillPoints}). Locking it in...")
            // Arms on the firing itself, so even an aborted or no-buy session cannot re-fire until SP grows: the bound on repeated opens.
            plannedSkillEvidence.markAffordableFired(trainee.skillPoints)
            activeTriggerContext =
                SkillTriggerContext(
                    trigger = SkillCheckTrigger.PLANNED_SKILL_AFFORDABLE,
                    plannedSkill = skillName,
                    plannedSkillObservedPrice = observedPrice,
                )
            ButtonSkills.click(game.imageUtils)
            game.wait(1.0)
            handleSkillListScreen(PLAN_SKILL_POINT_CHECK, SkillCheckTrigger.PLANNED_SKILL_AFFORDABLE)
            activeTriggerContext = null
            return true
        }

        // Now check if we need to handle skills before finals.
        if (skillCheck.action == SkillCheckAction.RUN_PLAN && skillCheck.trigger == SkillCheckTrigger.SCENARIO_FINALS) {
            ButtonSkills.click(game.imageUtils)
            game.wait(1.0)
            // Plan name stays null so start() resolves it from the screen; only the trigger is threaded through, for telemetry.
            if (!handleSkillListScreen(trigger = SkillCheckTrigger.SCENARIO_FINALS)) {
                preFinalsCheckAttempts++
                if (preFinalsCheckAttempts >= preFinalsCheckMaxAttempts) {
                    MessageLog.w(
                        TAG,
                        "[WARN] performGlobalChecks:: Pre-Finals skill purchase exhausted max attempts ($preFinalsCheckMaxAttempts). Marking it handled for this run so execution can continue.",
                    )
                    bHasHandledPreFinalsCheck = true
                } else {
                    MessageLog.w(
                        TAG,
                        "[WARN] performGlobalChecks:: handleSkillList() for Pre-Finals failed (attempt $preFinalsCheckAttempts/$preFinalsCheckMaxAttempts). Will retry next turn...",
                    )
                }
                return false
            }
            bHasHandledPreFinalsCheck = true
            preFinalsCheckAttempts = 0
            return true
        }

        // The confirmation gates BOTH branches: an unconfirmed reading must not open the skill screen, and must not throw the breakpoint either (with
        // the plan disabled a bad read would kill a healthy run). It only re-reads once the trigger fired; an unconfirmed turn falls through to the stop
        // checks below.
        if (skillCheck.trigger == SkillCheckTrigger.HIGH_WATER && confirmHighWaterCrossing()) {
            if (skillCheck.action == SkillCheckAction.RUN_PLAN) {
                // Ensure we are actually at the Main screen before attempting to navigate.
                // If not, we skip the skill purchase for now and retry on the next turn.
                if (checkMainScreen()) {
                    MessageLog.i(TAG, "[SKILLS] Beginning process to purchase skills...")
                    ButtonSkills.click(game.imageUtils)
                    game.wait(1.0)
                    if (!handleSkillListScreen(PLAN_SKILL_POINT_CHECK, SkillCheckTrigger.HIGH_WATER)) {
                        skillPointCheckAttempts++
                        if (skillPointCheckAttempts >= skillPointCheckMaxAttempts) {
                            MessageLog.w(
                                TAG,
                                "[WARN] performGlobalChecks:: Skill Point Check exhausted max attempts ($skillPointCheckMaxAttempts). Marking it handled for this run so execution can continue.",
                            )
                            bHasHandledSkillPointCheck = true
                        } else {
                            MessageLog.e(
                                TAG,
                                "[ERROR] performGlobalChecks:: Failed to handle Skill Point Check (attempt $skillPointCheckAttempts/$skillPointCheckMaxAttempts). Will retry next turn...",
                            )
                        }
                        return true
                    }
                    bHasHandledSkillPointCheck = true
                    skillPointCheckAttempts = 0
                    return true
                } else {
                    MessageLog.i(TAG, "[SKILLS] Skipping skill purchase check for now since we are not confirmed to be sitting on the Main screen.")
                }
            } else {
                throw CampaignBreakpointException("Bot reached skill point check threshold. Stopping bot...")
            }
        }

        // Check if bot should stop before the finals.
        if (checkFinalsStop()) {
            throw InterruptedException(game.notificationMessage)
        }

        // Check if bot should stop at the user specified date.
        if (checkStopAtDate()) {
            throw InterruptedException(game.notificationMessage)
        }

        return false
    }

    /** At most once per `date.day`, and only when the critical-race gate is open. Racing's own goal read is not consumed: it refreshes after this
     * runs and can skip turns, handing the skill check stale data. The goal-text OCR runs only when the countdown reads 1-2 turns. */
    private fun produceGoalSnapshotIfDue(adaptive: Boolean) {
        val gateOpen =
            adaptive &&
                skillSpendObjective.allowsCriticalRace() &&
                date.day < PRE_FINALS_DAY - 1
        if (!gateOpen || currentGoalSnapshot?.turn == date.day) return

        val turnsRemaining = game.imageUtils.determineTurnsRemainingBeforeNextGoal()
        StatusBoard.goal(date.day, turnsRemaining)
        currentGoalSnapshot =
            if (turnsRemaining < 0) {
                // OCR failed: inert for the whole turn, never a guess.
                GoalDeadlineSnapshot(date.day, null, null, GoalKind.UNKNOWN, null)
            } else if (turnsRemaining !in CRITICAL_RACE_MIN_TURNS..CRITICAL_RACE_MAX_TURNS) {
                GoalDeadlineSnapshot(date.day, turnsRemaining, null, GoalKind.UNKNOWN, null)
            } else {
                val text = game.imageUtils.getGoalText()
                val (kind, raceName) = classifyGoalText(text, goalRaceNameCandidates)
                if (kind == GoalKind.RACE) {
                    MessageLog.i(TAG, "[SKILLS] Critical race '$raceName' in $turnsRemaining turn(s) from goal OCR.")
                    StatusBoard.goal(date.day, turnsRemaining, name = raceName)
                }
                GoalDeadlineSnapshot(date.day, turnsRemaining, text, kind, raceName)
            }
    }

    private data class CriticalRaceDue(val raceName: String, val raceTurn: Int, val turnsUntil: Int, val source: String)

    /** The mandatory goal-OCR arm wins over the planned-race arm; they share the handled key, so the same race never fires twice. */
    private fun computeCriticalRace(adaptive: Boolean): CriticalRaceDue? {
        if (!adaptive || !skillSpendObjective.allowsCriticalRace()) return null
        if (date.day >= PRE_FINALS_DAY - 1) return null
        if (trainee.skillPoints < MIN_CRITICAL_SPEND) return null

        val snapshot = currentGoalSnapshot?.takeIf { it.turn == date.day }
        if (snapshot != null && snapshot.kind == GoalKind.RACE && snapshot.raceName != null) {
            val turns = snapshot.turnsRemaining
            if (turns != null && turns in CRITICAL_RACE_MIN_TURNS..CRITICAL_RACE_MAX_TURNS) {
                val raceTurn = date.day + turns
                if (lastCriticalRaceTurnHandled != raceTurn) {
                    return CriticalRaceDue(snapshot.raceName, raceTurn, turns, "goal_ocr")
                }
            }
        }

        val planned =
            racing.plannedRacesForTriggers
                .filter { it.turnNumber - date.day in CRITICAL_RACE_MIN_TURNS..CRITICAL_RACE_MAX_TURNS }
                .minByOrNull { it.turnNumber }
        if (planned != null && lastCriticalRaceTurnHandled != planned.turnNumber) {
            return CriticalRaceDue(planned.raceName, planned.turnNumber, planned.turnNumber - date.day, "racing_plan")
        }
        return null
    }

    /**
     * Decides the next action to take based on the current trainee and game state.
     *
     * @return The decided [MainScreenAction].
     */
    open fun decideNextAction(): MainScreenAction {
        // DecisionTracer: accumulate ruled-out alternatives down the priority cascade and record the chosen action where it wins.
        val tracerRejected = mutableListOf<DecisionTracer.RejectedAlternative>()

        fun choose(action: MainScreenAction, reason: String): MainScreenAction {
            decisionTracer?.recordActionChoice(action, reason, tracerRejected.toList())
            return action
        }

        // Use the cached race-day flags from handleMainScreen. The bitmap is captured lazily: only the late branches (checkInjury, shouldRecoverMood)
        // use it, so earlier fast paths skip the MediaProjection cost (~50-150 ms). Mandatory is split from scheduled so a pinned recreation outing can
        // sit between them: shouldDoRecreationToday is a settings-only no-op while the dating schedule is disabled.
        if (cachedMandatoryRaceDay) {
            return choose(MainScreenAction.RACE, "mandatory race day")
        }

        if (racing.encounteredRacingPopup) {
            // Consume the flag at decision time so a failed race attempt does not spin on RACE turn after turn over a popup from two turns ago. Sits
            // above DATE: an open popup must be consumed before any Recreation tap can land.
            racing.encounteredRacingPopup = false
            return choose(MainScreenAction.RACE, "a racing popup was encountered")
        }

        if (shouldDoRecreationToday()) {
            return choose(MainScreenAction.DATE, "dating schedule: pinned recreation turn ${date.day}")
        }

        if (cachedScheduledRaceDay) {
            return choose(MainScreenAction.RACE, "scheduled race day")
        }

        if (racing.enableForceRacing) {
            MessageLog.i(TAG, "[INFO] Force racing enabled - skipping all other activities and going straight to racing.")
            return choose(MainScreenAction.RACE, "force racing is enabled")
        }

        if (!bHasCheckedForMaidenRaceToday && !date.bIsPreDebut && !trainee.bHasCompletedMaidenRace) {
            MessageLog.i(TAG, "[INFO] Bot has not yet completed maiden race. Checking for valid maiden race...")
            return choose(MainScreenAction.RACE, "maiden race not yet completed")
        }

        val sourceBitmap = game.imageUtils.getSourceBitmap()

        // A mandatory requirement (fan / trophy / goal-pts) can only be met by racing, so it outranks the pre-summer prep, whose forced rest/mood turn
        // would eat the turn it needed. With no races available Racing resets the flags and the turn falls back to training.
        val isRacingRequirementActive = racing.hasFanRequirement || racing.hasTrophyRequirement || racing.hasInsufficientGoalRacePtsRequirement
        // True only when the fan-requirement arm was deferred this turn; the later extra-race eligibility gate uses it so the same fan requirement
        // cannot re-force the race it was just deferred from.
        var fanRequirementDeferredThisTurn = false
        if (isRacingRequirementActive) {
            if (considerFanRaceDeferral()) {
                // A scenario fan policy proved enough slack to train instead of racing the fan requirement; the base never defers.
                fanRequirementDeferredThisTurn = true
                MessageLog.i(TAG, "[INFO] Fan requirement deferred for a training turn by the scenario policy.")
            } else {
                MessageLog.i(TAG, "[INFO] Racing requirement is active. Bypassing health and mood checks.")
                return choose(MainScreenAction.RACE, "racing requirement active (fan/trophy/goal-pts)")
            }
        }

        if (mustRestBeforeSummer && (date.year == DateYear.CLASSIC || date.year == DateYear.SENIOR) && date.month == DateMonth.JUNE && date.phase == DatePhase.LATE) {
            // An explicit mandatory plan entry or a due fan goal outranks summer prep: this forced rest once consumed the turn of a mandatory planned race
            // (Unicorn Stakes) while a 5000-fan goal was due. bFanEmergencyActive carries the previous turn's evaluation, current enough across the window.
            if (racing.hasMandatoryPlannedRaceToday() || racing.bFanEmergencyActive) {
                MessageLog.i(
                    TAG,
                    "[INFO] Skipping pre-summer prep: ${if (racing.bFanEmergencyActive) "a fan emergency is active" else "a mandatory planned race is scheduled for today"}.",
                )
            } else if (trainee.energy < 70) {
                MessageLog.i(TAG, "[INFO] Energy is low (${trainee.energy}% < 70%). Forcing rest during $date in preparation for Summer Training.")
                return choose(MainScreenAction.REST, "pre-summer prep: energy ${trainee.energy}% < 70%")
            } else if (trainee.mood < Mood.GREAT) {
                // firstTrainingCheck refuses mood recovery: train first to clear it, then recover next turn.
                if (training.firstTrainingCheck) {
                    MessageLog.i(TAG, "[INFO] Mood is ${trainee.mood} but firstTrainingCheck is active. Doing a training first to clear the flag before mood recovery can proceed.")
                    return choose(MainScreenAction.TRAIN, "pre-summer prep: train first to clear firstTrainingCheck before mood recovery")
                }
                MessageLog.i(TAG, "[INFO] Energy is sufficient (>= 70%) but Mood is not Great (${trainee.mood}). Forcing mood recovery during $date in preparation for Summer Training.")
                forcedTargetMood = Mood.GREAT
                return choose(MainScreenAction.RECOVER_MOOD, "pre-summer prep: mood ${trainee.mood} below Great")
            } else {
                MessageLog.i(TAG, "[INFO] Energy is sufficient (>= 70%) and mood is Great. Performing Wit training during $date in preparation for Summer Training.")
                bForcedWitTraining = true
                return choose(MainScreenAction.TRAIN, "pre-summer prep: forced Wit training (energy and mood sufficient)")
            }
        }

        val isFinals = checkFinals()
        val hasInjury =
            if (isFinals) {
                MessageLog.i(TAG, "[INFO] Skipping injury check due to it being the Finals.")
                false
            } else {
                checkInjury(sourceBitmap)
            }

        if (hasInjury) {
            // Injury handled internally in checkInjury, but returning NONE as turn is likely over or needs re-evaluation.
            return choose(MainScreenAction.NONE, "injury handled; re-evaluating next tick")
        }

        if (shouldRecoverMood(sourceBitmap)) {
            return choose(MainScreenAction.RECOVER_MOOD, "mood ${trainee.mood} below floor $moodFloor")
        }
        tracerRejected.add(DecisionTracer.RejectedAlternative("RECOVER_MOOD", "mood ${trainee.mood} at/above floor $moodFloor"))

        val extraRaceEligible = racing.checkEligibilityToStartExtraRacingProcess(ignoreFanRequirement = fanRequirementDeferredThisTurn)
        // Recorded from the caller so it fires on every turn an extra race is considered: checkEligibility has early returns (Trackblazer interval,
        // fan emergency, mandatory plan) that bypass its standard-racing block, so recording inside it missed Trackblazer.
        decisionTracer?.recordRaceEligibility(
            extraRaceEligible,
            if (extraRaceEligible) "extra races can be run today" else "not eligible for an extra race this turn (see [RACE] log for the gate)",
        )
        if (extraRaceEligible) {
            MessageLog.i(TAG, "[INFO] Bot has no injuries, mood is sufficient and extra races can be run today. Setting the action to RACE.")
            return choose(MainScreenAction.RACE, "extra races can be run today")
        }
        tracerRejected.add(DecisionTracer.RejectedAlternative("RACE", "extra-race eligibility gate not met (see Race eligibility)"))

        return choose(MainScreenAction.TRAIN, "default action: no race required, no recovery needed, no extra race eligible")
    }

    /**
     * Executes the specified action.
     *
     * @param action The action to execute.
     * @param bIsScheduledRaceDay Whether it is a scheduled race day.
     * @return True if the action was executed successfully, false otherwise.
     */
    open fun executeAction(action: MainScreenAction, bIsScheduledRaceDay: Boolean): Boolean {
        StatusBoard.action(
            when (action) {
                MainScreenAction.RACE -> "race"
                MainScreenAction.TRAIN -> "training"
                MainScreenAction.REST -> "rest"
                MainScreenAction.RECOVER_MOOD, MainScreenAction.DATE -> "recreation"
                MainScreenAction.NONE -> "other"
            },
            null,
        )
        // Force Wit Training if requested by the pre-summer logic.
        if (action == MainScreenAction.TRAIN && bForcedWitTraining) {
            MessageLog.i(TAG, "[INFO] Executing forced Wit training as requested by pre-summer logic.")
            training.handleTraining(StatName.WIT)
            bForcedWitTraining = false
            bHasCheckedDateThisTurn = false
            // Shadow-only: forced-Wit training advances to a new decision turn; rearm the CareerState build latch.
            careerStateLatch.armForNewTurn()
            return true
        }

        when (action) {
            MainScreenAction.RACE -> {
                MessageLog.i(TAG, "[INFO] All checks are cleared for racing.")
                // bDidRace is true only when a race actually ran. An aborted race returns false but its fallback may train/recover and advance the turn, so
                // track advancement from both sources.
                val bDidRace = handleRaceEvents(bIsScheduledRaceDay)
                var turnAdvanced = bDidRace
                if (!bDidRace) {
                    val fallback = handleRaceEventFallback()
                    if (fallback.shouldStopForMandatoryRace) {
                        throw CampaignBreakpointException("Mandatory race detected. Stopping bot...")
                    }
                    turnAdvanced = fallback.turnAdvanced
                }
                // Always re-evaluate the same turn (a failed race must pick another action); rearm CareerState only when the race or its fallback advanced it.
                bHasCheckedDateThisTurn = false
                careerStateLatch.armForNewTurnIf(turnAdvanced)
            }

            MainScreenAction.TRAIN -> {
                MessageLog.i(TAG, "[INFO] Decision made to train.")
                training.handleTraining()
                bHasCheckedDateThisTurn = false
                careerStateLatch.armForNewTurn() // shadow-only: training advances the turn
            }

            MainScreenAction.REST -> {
                // RACE/TRAIN (most turns) need no fresh screenshot here; capture only for REST/RECOVER_MOOD.
                recoverEnergy(game.imageUtils.getSourceBitmap())
                bHasCheckedDateThisTurn = false
                careerStateLatch.armForNewTurn() // shadow-only: resting advances the turn
            }

            MainScreenAction.RECOVER_MOOD -> {
                // Target the configured floor, not a hardcoded GOOD: with moodFloor=GREAT a GOOD-targeted recovery no-ops (GOOD < GOOD is false) while the
                // decision gate keeps choosing RECOVER_MOOD, a livelock that ran to the runtime cap.
                val target = forcedTargetMood ?: moodFloor
                val recovered = performMoodRecovery(game.imageUtils.getSourceBitmap(), targetMood = target)
                // Always clear so the next main-screen pass re-runs updateDate/stats and can pick another action; clearing only on success let a failed mood
                // recovery (buttons missing from a mid-transition screenshot) spin on the same screenshot forever.
                bHasCheckedDateThisTurn = false
                if (recovered) {
                    forcedTargetMood = null
                    // Shadow-only: a successful recovery advances the turn; a failed one re-dispatches to TRAIN, which rearms.
                    careerStateLatch.armForNewTurn()
                } else if (trainee.mood >= Mood.GOOD) {
                    // Recovery made no progress while only a high floor (GREAT) is unmet: the floor is a preference, training is progress. Re-dispatch through
                    // executeAction so a scenario override's TRAIN handling stays in effect.
                    MessageLog.w(TAG, "[WARN] Mood recovery made no progress toward the $target floor. Training this turn instead.")
                    return executeAction(MainScreenAction.TRAIN, bIsScheduledRaceDay)
                }
            }

            MainScreenAction.DATE -> {
                MessageLog.i(TAG, "[INFO] Decision made to perform a scheduled recreation outing.")
                val started = handleRecreationDate(recoverMoodIfCompleted = false, allowFinalOuting = allowFinalOutingNow(), doDateRecreation = true)
                if (!started) {
                    // Backing out (held final, no selectable rows) does not advance the turn; latch it or decideNextAction re-picks DATE and reopens the dialog forever.
                    recreationAttemptFailedThisTurn = true
                    MessageLog.i(TAG, "[RECREATION_DATE] Scheduled outing did not start. Deferring to the normal action flow for the rest of this turn.")
                }
                bHasCheckedDateThisTurn = false
                if (started) careerStateLatch.armForNewTurn()
            }

            MainScreenAction.NONE -> {
                return false
            }
        }
        return true
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /** The process-lifetime buffer survives across queued careers (clearing it would blank the on-screen RN log); [writePerCareerLog] slices from
     * this size so each per-career file holds only that career's lines. */
    private val careerLogStartIndex: Int = MessageLog.getMessageLogCopy().size

    /** One structured outcome line per career, greppable as `[CAREER_END]`. The game shows the SAME end screen for a clean finish and an early
     * force-end, so [result] is COMPLETE for both and `turn` is the real discriminator (a full arc ends near the scenario's last turn, URA finals = 75;
     * a Junior fan-checkpoint death lands around turn 24). `outcome=` is INCOMPLETE for a non-COMPLETE result (user stop or bot failure), FORCE_END for a
     * force-end confirmed at its source ([careerForceEnded]), and COMPLETED for the still-ambiguous rest. Fields come from memory or already-OCR'd
     * state, so building the line triggers no capture.
     *
     * `spd/sta/pwr/grt/wit/fans` come from the post-finale re-read of the Umamusume Details dialog (`ButtonDetails`, confidence lowered to 0.65 so the
     * match lands). If that re-read fails the fields fall back to the last in-career OCR and understate the finale rewards (~+40 per stat, large fan
     * injection), so trust `turn`/`result`. A stat the result screen contradicts is logged as -1 and no estRank/estScore is written. */
    override fun careerEndLedgerLine(result: TaskResult): String {
        val shownName = trainee.name.ifEmpty { SettingsHelper.getStringSetting("misc", "currentProfileName") }
        val resolvedName = shownName.ifEmpty { "unknown" }.replace(" ", "_")
        val scenarioToken = game.scenario.ifEmpty { "unknown" }.replace(" ", "_")
        val st = trainee.stats
        // For the career-end SPARKS screen: the reroll gate reads the final stats after this Campaign instance is gone.
        StartModule.lastCareerEndStats =
            mapOf(
                "Speed" to st.speed,
                "Stamina" to st.stamina,
                "Power" to st.power,
                "Guts" to st.guts,
                "Wit" to st.wit,
            )
        StartModule.lastCareerEndTrainee = resolvedName
        StartModule.lastCareerEndTraineeName = shownName.ifEmpty { null }
        // The same fingerprint and scenario the career-end record carries, so SPARKS records appended later join this exact career/arm; computed once,
        // never from settings a queued run may have changed since.
        val careerEndFp = outcomeConfigFingerprint(BuildConfig.VERSION_NAME, outcomeConfigSnapshot)
        StartModule.lastCareerEndScenario = scenarioToken
        StartModule.lastCareerEndFp = careerEndFp
        if (endedBeforeFinale(result.code, if (date.dayObserved) date.day else null)) markCareerForceEnded("ENDED_BEFORE_FINALE")
        val outcome = classifyCareerOutcome(result.code, careerForceEnded)
        StartModule.lastCareerEndOutcome = outcome
        StartModule.lastCareerEndTurn = if (date.dayObserved) date.day else null
        StartModule.lastCareerEndResult =
            careerResultAtEnd(
                outcome,
                trainee.estimatedRank?.rankLabel,
                trainee.estimatedRank?.totalScore,
                trainee.fans,
                finaleRaces,
                finaleRaces1st,
                listOf(st.speed, st.stamina, st.power, st.guts, st.wit),
                careerEndLastKnownStats,
            )
        // Bumped last, after every stash above, so the queue report attributes them to this run only.
        StartModule.lastCareerEndSeq++
        if (outcome != "INCOMPLETE") ProgressTracker.noteProgress(ProgressEvent.CAREER_END)
        val quality = classifyCareerQuality(outcome, finaleRaces, finaleRaces1st)

        // Same fields as the ledger line, appended to the on-device corpus; the write swallows its own failures, so the ledger line always logs.
        val record =
            JSONObject().apply {
                put("ts", System.currentTimeMillis())
                put("app", BuildConfig.VERSION_NAME)
                put("fp", careerEndFp)
                // The launch-transaction id adopted at attachment (Game.start), joining this career's Veteran to its launch lineage read; absent for hand-played or restart-resumed careers.
                LaunchTransactionGate.active?.id?.let { put("launchTransactionId", it) }
                put("result", careerLedgerResult(result.code, StartModule.queueStopReason))
                put("outcome", outcome)
                forceEndReason?.let { put("forceEndReason", it) }
                put("trainee", resolvedName)
                put("scenario", scenarioToken)
                // Only record a turn the bot actually read. A career resumed at its Complete Career screen never reads a date and GameDate.day keeps its
                // initial 1, which produced COMPLETED rows at turn 1 for full arcs. A null turn is kept out of the arm summaries.
                put("turn", if (date.dayObserved) date.day else JSONObject.NULL)
                put("fans", trainee.fans)
                put("spd", st.speed)
                put("sta", st.stamina)
                put("pwr", st.power)
                put("grt", st.guts)
                put("wit", st.wit)
                if (careerEndLastKnownStats.isNotEmpty()) put("lastKnown", JSONArray(careerEndLastKnownStats))
                put("skillPts", trainee.skillPoints)
                put("finaleRaces", finaleRaces)
                put("finaleWins", finaleRaces1st)
                put("quality", quality)
                trainee.estimatedRank?.let {
                    put("estRank", it.rankLabel)
                    put("estScore", it.totalScore)
                }
                if (result.code == TaskResultCode.TASK_RESULT_MANUALLY_STOPPED) {
                    StartModule.queueStopReason?.let { put("stopReason", it) }
                    StartModule.queueStopKey?.let { put("stopKey", it) }
                }
                put("cfg", JSONObject(outcomeConfigSnapshot as Map<*, *>))
            }
        OutcomeCorpus.append(game.myContext, record)

        return buildString {
            append("[CAREER_END] result=").append(careerLedgerResult(result.code, StartModule.queueStopReason))
            append(" outcome=").append(outcome)
            forceEndReason?.let { append(" forceEndReason=\"").append(it).append('"') }
            append(" trainee=").append(resolvedName)
            append(" scenario=").append(scenarioToken)
            append(" turn=").append(if (date.dayObserved) date.day.toString() else "unknown")
            append(" fans=").append(trainee.fans)
            append(" spd=").append(st.speed)
            append(" sta=").append(st.stamina)
            append(" pwr=").append(st.power)
            append(" grt=").append(st.guts)
            append(" wit=").append(st.wit)
            if (careerEndLastKnownStats.isNotEmpty()) append(" lastKnown=").append(careerEndLastKnownStats.joinToString(","))
            append(" skillPts=").append(trainee.skillPoints)
            append(" finaleRaces=").append(finaleRaces)
            append(" finaleWins=").append(finaleRaces1st)
            append(" quality=").append(quality)
            trainee.estimatedRank?.let {
                append(" estRank=").append(it.rankLabel)
                append(" estScore=").append(it.totalScore)
            }
            if (result.code == TaskResultCode.TASK_RESULT_MANUALLY_STOPPED) {
                StartModule.queueStopReason?.let { append(" stopReason=\"").append(it).append('"') }
                StartModule.queueStopKey?.let { append(" stopKey=").append(it) }
            }
        }
    }

    /** The library's own per-career .txt silently stopped on a long-lived queue session, so write our own copy from the readable buffer. Runs after
     * the [CAREER_END] line (Task.handleTaskEnd ordering) so the file contains it, and slices from [careerLogStartIndex]. Best-effort: must never
     * abort the run. */
    override fun writePerCareerLog(result: TaskResult) {
        try {
            val filesDir = game.myContext.getExternalFilesDir(null) ?: return
            val logsDir = java.io.File(filesDir, "logs")
            if (!logsDir.exists() && !logsDir.mkdirs()) return
            val resolvedName =
                trainee.name.ifEmpty {
                    SettingsHelper.getStringSetting("misc", "currentProfileName").ifEmpty { "unknown" }
                }.replace(" ", "_")
            val buffer = MessageLog.getMessageLogCopy()
            // Defensive: write the full copy rather than a wrong slice if the buffer was trimmed below the start snapshot.
            val lines = if (careerLogStartIndex <= buffer.size) buffer.subList(careerLogStartIndex, buffer.size) else buffer
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH_mm_ss", java.util.Locale.US).format(java.util.Date())
            val logFile = java.io.File(logsDir, "${resolvedName}_$stamp.txt")
            logFile.writeText(lines.joinToString("\n"))
            // App-written files land u0_a75:u0_a75 on this emulator image, which locks the adb shell out of triage pulls; best-effort world-read.
            logFile.setReadable(true, false)
        } catch (e: Exception) {
            MessageLog.w(TAG, "[WARN] writePerCareerLog:: Failed to write the per-career log file: ${e.message}")
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Executes the main processing loop for the campaign task.
     *
     * @return The result of the task execution, or null if the loop should continue.
     */
    override fun process(): TaskResult? {
        try {
            // The emulator can wipe the Accessibility grant mid-run (gestures die while screen reads keep working). Self-heal BEFORE the dialog and
            // main-screen ticks: both early-return, so a gesture-death during a dialog looped unbounded to the runtime cap.
            if (!game.ensureAccessibilityService()) {
                val reason =
                    "The Accessibility Service was disabled mid-run and could not be restored automatically. " +
                        "Re-enable it in the Android settings or grant WRITE_SECURE_SETTINGS (see log)."
                requestAccessibilityHalt(A11Y_GRANT_MISSING)
                throw InterruptedException(reason)
            }

            // Reset here, not at the end of the tick: dialog and main-screen ticks return early.
            if (!bMiscStepTakenLastTick) {
                miscNextBackSwaps = 0
                lastMiscStepWasNext = null
                pagedHelpCloseTaps = 0
            }
            bMiscStepTakenLastTick = false

            // We always check for dialogs first.
            if (tryHandleAllDialogs()) {
                consecutiveUnknownScreenCount = 0
                consecutiveDialogTicks++
                // The string-only ensure above cannot see MuMu's enabled-but-dispatch-dead mode; a long dialog streak is its signature, so hard-rebind at the
                // ladder points and stop cleanly.
                if (consecutiveDialogTicks == 13 || consecutiveDialogTicks == 19) {
                    if (consecutiveDialogTicks == 13) dialogRebinds.start()
                    MessageLog.w(TAG, "[WARN] process:: $consecutiveDialogTicks consecutive dialog ticks without progress - forcing an accessibility service rebind.")
                    dialogRebinds.record(game.forceRebindAccessibilityService())
                } else if (consecutiveDialogTicks >= dialogStopAt) {
                    dialogRebinds.closeLast()
                    if (shouldTryStrongToggle(dialogRebinds, game.strongToggleUsed)) {
                        dialogRebinds.record(game.strongToggleAccessibilityService())
                        dialogStopAt = consecutiveDialogTicks + STRONG_TOGGLE_GRACE_TICKS
                        return null
                    }
                    stopForStuckInput(dialogRebinds, "Dialog handling made no progress for $consecutiveDialogTicks ticks, and accessibility rebinds did not help.")
                }
                return null
            }
            consecutiveDialogTicks = 0
            dialogStopAt = dialogTicksBeforeStop

            if (handleMainScreen()) {
                consecutiveUnknownScreenCount = 0
                careerScreenObservedThisTask = true
                endDataDownloadWait()
                return null
            }

            // Reset the unknown-screen counter only when something was actually handled, so a transient blip does not accumulate toward the stop.
            var detectedKnownScreen = true
            bMiscBackPressedThisTick = false

            if (checkTrainingEventScreen()) {
                // If the bot is at the Training Event screen, that means there are selectable options for rewards.
                StatusBoard.action("event", null)
                handleTrainingEvent()
            } else if (checkMandatoryRacePrepScreen()) {
                // If the bot is at the Main screen with the button to select a race visible, that means the bot needs to handle a mandatory race.
                // Race screens only exist in-career, so they count as having observed the career -
                // without this, a task resumed directly onto a race day never arms the lobby
                // re-entry or the game-restart net, both gated on this flag.
                careerScreenObservedThisTask = true
                if (!handleRaceEvents() && racing.detectedMandatoryRaceCheck) {
                    return TaskResult.Success(
                        TaskResultCode.TASK_RESULT_BREAKPOINT_REACHED,
                        "Mandatory race detected. Stopping bot...",
                    )
                }
            } else if (checkRacingScreen()) {
                // If the bot is already at the Racing screen, then complete this standalone race.
                careerScreenObservedThisTask = true
                racing.handleStandaloneRace()
            } else if (checkEndScreen()) {
                // Stop when the bot has reached the screen where it details the overall result of the run.
                if (!bCareerEndSkillsHandled && (skillPlan.skillPlans["careerComplete"]?.bIsEnabled ?: false)) {
                    // Open the Learn screen, then hand off: the next tick runs the plan once the screen is present (an inline buy after a fixed 1s wait failed when
                    // it loaded slower). Marked handled only on a confirmed buy in that branch, so this stays retryable.
                    careerEndEntryAttempts++
                    if (careerEndEntryAttempts <= maxCareerEndEntryAttempts) {
                        MessageLog.i(
                            TAG,
                            "[INFO] Career end reached. Opening the Learn skill screen for the careerComplete plan (attempt $careerEndEntryAttempts/$maxCareerEndEntryAttempts)...",
                        )
                        game.wait(0.5)
                        openCareerEndSkillScreen()
                        game.wait(1.0)
                        // If the click did not navigate, this branch fires again and retries.
                        return null
                    }
                    // Out of attempts: complete the career rather than hang; unspent skill points are a smaller loss than a wedged run.
                    MessageLog.e(
                        TAG,
                        "[ERROR] process:: Could not open the career-end skill screen after $maxCareerEndEntryAttempts attempts. Completing the career without the careerComplete plan (skill points may remain unspent).",
                    )
                    // SkillPlan cannot report this pass (its session never began); record it only once every bounded attempt is spent.
                    recordAbortedSkillEntry()
                    bCareerEndSkillsHandled = true
                }

                // Perform a final update of the fan count.
                // ButtonDetails carries a lowered match confidence (see Button.kt): on the career-end
                // screen it renders just under the default 0.8 threshold, which silently skipped this
                // whole post-finale fan+stat re-read every career and left [CAREER_END] on the stale
                // pre-finale values (~+40/stat short of the real result screen). A few retries also
                // cover a mid-render capture.
                trainee.detailsFloorRejections.clear()
                trainee.detailsUnacceptedReads.clear()
                trainee.detailsUnacceptedReads.addAll(StatName.entries)
                game.wait(1.0)
                val buttonLocation = ButtonDetails.find(game.imageUtils, tries = 5).first
                if (buttonLocation != null) {
                    val fansText =
                        game.imageUtils.performOCROnRegion(
                            game.imageUtils.getSourceBitmap(),
                            game.imageUtils.relX(buttonLocation.x, 280),
                            game.imageUtils.relY(buttonLocation.y, -735),
                            game.imageUtils.relWidth(220),
                            game.imageUtils.relHeight(50),
                            useThreshold = false,
                            useGrayscale = true,
                            scale = 2.0,
                            ocrEngine = "tesseract",
                            debugName = "final_fan_count",
                        )

                    // toIntOrNull: OCR noise (>10 digits) overflowed Int and threw NumberFormatException, which unwinds past Task.start()'s catch and ends the whole
                    // run (aborting a stopOnError queue). Keep the last value on a bad read.
                    val cleanedFans = fansText.replace(Regex("[^0-9]"), "")
                    cleanedFans.toIntOrNull()?.let { trainee.observeFanCount(it) }
                        ?: MessageLog.w(TAG, "[WARN] process:: Could not detect final fan count for the end of the Career from OCR: $fansText")

                    // Now click the button to open the details dialog for aptitude and stat updates.
                    game.gestureUtils.tap(buttonLocation.x, buttonLocation.y, ButtonDetails.template.path)
                    game.wait(1.0)
                    ButtonDetails.click(game.imageUtils)
                    game.wait(1.0)
                } else {
                    MessageLog.w(TAG, "[WARN] process:: Could not find ButtonDetails to perform final updates for the end of the Career.")
                }

                val notAccepted =
                    readCareerEndStats(
                        read = {
                            handleDialogs()
                            trainee.detailsUnacceptedReads.toSet()
                        },
                        reopen = { unread ->
                            MessageLog.i(TAG, "[CAREER_END] The final Details read left ${unread.joinToString { it.name }} without a usable value. Reading once more.")
                            game.wait(1.0)
                            val opened = buttonLocation != null && ButtonDetails.click(game.imageUtils)
                            if (opened) {
                                trainee.detailsFloorRejections.clear()
                                game.wait(1.0)
                            }
                            opened
                        },
                    )

                // Re-open Details to read the owned skills from its Skills tab: the first open was consumed by the standard dialog handler. The skills and
                // unique level feed the estimated rank. Gate on the re-click landing, else the tab tap, swipes and close-fallback would fire blind.
                if (buttonLocation != null && ButtonDetails.click(game.imageUtils)) {
                    game.wait(1.0)
                    val ownedSkills = SkillList(game, this).parseDetailsSkillsTab()
                    // The Skills tab always holds at least the unique skill, so an empty read is a failed read: keep the purchase-tracked set rather than
                    // dropping every skill the career bought.
                    if (ownedSkills.skillNames.isNotEmpty()) {
                        trainee.ownedSkillNames.clear()
                        trainee.ownedSkillNames.addAll(ownedSkills.skillNames)
                        trainee.uniqueSkillLevel = ownedSkills.uniqueLevel
                    }
                    // Dismiss directly: the dialog now shows the Skills tab, which the generic details handler must not read as stats. Same close idiom as the
                    // between-run navigator (wide Close template, else the card's fixed bottom-center Close).
                    val closeBitmap = game.imageUtils.getSourceBitmap()
                    if (!ButtonCloseWide.click(game.imageUtils, sourceBitmap = closeBitmap)) {
                        CoordinateTap.tap(game.gestureUtils, closeBitmap.width * 0.5, closeBitmap.height * 0.86, "umamusume_details_close")
                    }
                    game.wait(1.0)
                }

                // A final Details read the floor rejected leaves the result screen and the in-career value disagreeing: the stat goes out unread and no rank is claimed.
                val contradicted = trainee.detailsFloorRejections.filter { (stat, read) -> StatReadPlausibility.contradictsHeldValue(read, trainee.getStat(stat)) }
                if (contradicted.isEmpty()) {
                    updateEstimatedRank()
                } else {
                    contradicted.forEach { (stat, read) ->
                        MessageLog.w(TAG, "[STAT_FLOOR] The result screen reads $stat as $read but the career held ${trainee.getStat(stat)}. Recording $stat as unread and no estimated rank.")
                        trainee.stats.setStat(stat, -1)
                    }
                    trainee.estimatedRank = null
                }
                careerEndLastKnownStats = lastKnownLedgerKeys(notAccepted, contradicted.keys)
                if (careerEndLastKnownStats.isNotEmpty()) {
                    MessageLog.w(TAG, "[CAREER_END] No usable final read for ${careerEndLastKnownStats.joinToString(",")}; the ledger reports their last-known values as lastKnown.")
                }

                // Print the final Trainee information.
                trainee.logInfo()

                // Finalization guard (Adaptive mode only): the game DISCARDS every unspent skill point at Finish (one sparks career handed 716 points to the
                // Finish click). Decide from EVIDENCE (the careerComplete session's scan/planner/confirmation completeness and candidate-exhaustion counts),
                // never a fixed balance threshold. An unproven balance gets one re-run of the plan through the Learn-screen machinery; after that the gate
                // is armed with a career-scoped verdict the between-run navigator consults before pressing Finish. Manual mode never arms it.
                if (resolvedSkillThreshold.mode == SkillSpendMode.ADAPTIVE) {
                    val evidence =
                        skillPlan.lastSessionEvidence?.takeIf { it.trigger == SkillCheckTrigger.CAREER_COMPLETE }
                    val evaluation =
                        evaluateCareerFinalization(
                            mode = resolvedSkillThreshold.mode,
                            detailsSp = trainee.skillPoints,
                            evidence = evidence,
                            retryUsed = careerEndSpendRetryUsed,
                        )
                    if (evaluation.decision == FinalizeDecision.RETRY_SPEND) {
                        MessageLog.w(TAG, "[FINALIZE] ${evaluation.reason} Re-running the careerComplete skill plan once...")
                        careerEndSpendRetryUsed = true
                        bCareerEndSkillsHandled = false
                        careerEndEntryAttempts = 0
                        careerEndExitAttempts = 0
                        return null
                    }
                    val approved = evaluation.decision == FinalizeDecision.FINISH
                    if (approved) {
                        MessageLog.i(TAG, "[FINALIZE] ${evaluation.reason} Finish is approved.")
                    } else {
                        MessageLog.e(TAG, "[FINALIZE] ${evaluation.reason}")
                    }
                    // The verdict token combines the applied preset's outfit-bearing trainee identity (else the OCR'd name), the scenario, the queue run and the
                    // per-career construction nonce. The navigator captures the token when its finalization starts; a non-matching verdict (previous career, run
                    // or arming) is unusable. The run number comes from the career task, else the persisted queue cursor.
                    val queueRun: Int =
                        CareerFinalizeGate.context?.queueRun ?: SettingsHelper.getIntSetting("queueState", "currentRun", 0)
                    val traineeIdentity: String =
                        SettingsHelper.getStringSetting("general", "appliedPresetTrainee").trim().ifEmpty { trainee.name }
                    val verdict =
                        FinalizeVerdict(
                            careerToken = buildCareerFinalizeToken(traineeIdentity, game.scenario, queueRun.takeIf { it > 0 }, careerFinalizeNonce),
                            queueRun = queueRun.takeIf { it > 0 },
                            trainee = traineeIdentity,
                            scenario = game.scenario,
                            objective = skillSpendObjective.token(),
                            approved = approved,
                            verifiedRemainingSp = evidence?.verifiedRemainingSp ?: trainee.skillPoints,
                            sessionTimestampMs = evidence?.timestampMs,
                            reason = evaluation.reason,
                            armedAtMs = System.currentTimeMillis(),
                        )
                    CareerFinalizeGate.arm(verdict)
                    MessageLog.i(TAG, "[FINALIZE] Verdict armed for token ${verdict.careerToken} (queueRun=${verdict.queueRun ?: "-"}).")
                    runCatching {
                        OutcomeCorpus.append(
                            game.myContext,
                            SkillSpendTelemetry.buildCareerFinalizeRecord(
                                timestamp = System.currentTimeMillis(),
                                decision = evaluation.decision.name,
                                reason = evaluation.reason,
                                careerToken = verdict.careerToken,
                                trainee = trainee.name.ifEmpty { null }?.replace(" ", "_"),
                                scenario = game.scenario.ifEmpty { null }?.replace(" ", "_"),
                                objective = skillSpendObjective.token(),
                                queueRun = verdict.queueRun,
                                verifiedRemainingSp = verdict.verifiedRemainingSp,
                                retryUsed = careerEndSpendRetryUsed,
                                evidence = evidence,
                            ),
                        )
                    }.onFailure {
                        MessageLog.w(TAG, "[FINALIZE] Failed to append the career_finalize record: $it")
                    }
                }

                // Reaching here means the plan already committed, was disabled, or was skipped after exhausting the Learn-screen open attempts.
                return TaskResult.Success(
                    TaskResultCode.TASK_RESULT_COMPLETE,
                    "Bot has reached end of run. Stopping bot...",
                )
            } else if (checkCareerEndSkillListScreen()) {
                if (!bCareerEndSkillsHandled) {
                    // Started directly on the career-end Learn screen: buy per the plan; the End screen branch above does the final bookkeeping on a later tick.
                    MessageLog.i(TAG, "[INFO] Bot is on the career-end skill purchase screen. Running the careerComplete skill plan...")
                    bCareerEndSkillsHandled = true
                    if (!handleSkillListScreen(trigger = SkillCheckTrigger.CAREER_COMPLETE)) {
                        MessageLog.w(TAG, "[WARN] process:: careerComplete skill plan failed on the career-end skill purchase screen.")
                    }
                } else {
                    // The plan already ran but the bot is STILL on the Learn screen (confirmAndExit reported success without leaving), which used to livelock on
                    // the misc back-press for 10+ minutes. Confirm is wrong here: with nothing selected it is a no-op whose "success" starved the Back fallback.
                    // cancelAndExit resets any stray selection, presses Back and drains the "unused skill points - exit anyway?" dialog.
                    careerEndExitAttempts++
                    if (careerEndExitAttempts >= maxCareerEndExitAttempts) {
                        game.imageUtils.saveBitmap(filename = "career_end_exit_stuck", fullRes = true)
                        throw InterruptedException(
                            "Bot could not exit the career-end skill screen after $maxCareerEndExitAttempts attempts. Stopping. " +
                                "A screenshot was saved to the temp folder as career_end_exit_stuck.",
                        )
                    }
                    MessageLog.w(
                        TAG,
                        "[WARN] process:: Still on the career-end skill screen after the plan ran (exit attempt $careerEndExitAttempts/$maxCareerEndExitAttempts). Resetting and backing out...",
                    )
                    careerEndScreenChecker.cancelAndExit()
                }
            } else if (checkCampaignSpecificConditions()) {
                MessageLog.i(TAG, "[INFO] Campaign-specific checks complete.")
            } else if (handleInheritanceEvent()) {
                // If the bot is at the Inheritance screen, then accept the inheritance.
            } else if (performMiscChecks()) {
                MessageLog.i(TAG, "[INFO] Misc checks complete.")
            } else if (game.holdBlindInputForOwnUi()) {
                // The capture is our own screen, not the game: no blind tap, and the streak holds.
                detectedKnownScreen = false
            } else if (dataDownloadRunning()) {
                // The game's download screens after its Data Download OK: waited out, tapping nothing.
                detectedKnownScreen = false
                game.wait(2.0, skipWaitingForLoading = true)
            } else {
                detectedKnownScreen = false
                consecutiveUnknownScreenCount++
                if ((com.steve1316.uma_android_automation.BuildConfig.DEBUG || game.debugMode) &&
                    (consecutiveUnknownScreenCount == 6 || consecutiveUnknownScreenCount == 13 || consecutiveUnknownScreenCount == 22)
                ) {
                    game.imageUtils.saveFixture(
                        "unknown_t${date.day}_n$consecutiveUnknownScreenCount",
                        null,
                        mapOf(
                            "scenario" to game.scenario,
                            "trainee" to trainee.name,
                            "turn" to date.day,
                            "date" to date.toString(),
                            "stuckCount" to consecutiveUnknownScreenCount,
                            "spd" to trainee.stats.speed,
                            "sta" to trainee.stats.stamina,
                            "pwr" to trainee.stats.power,
                            "grt" to trainee.stats.guts,
                            "wit" to trainee.stats.wit,
                            "energy" to trainee.energy,
                            "mood" to trainee.mood.name,
                            "fans" to trainee.fans,
                        ),
                    )
                }
                MessageLog.i(
                    TAG,
                    "[INFO] Did not detect the bot being at the following screens: Main, Training Event, Inheritance, Mandatory Race Preparation, Racing and Career End. (unknown screen #$consecutiveUnknownScreenCount)",
                )
                recoverFromUnknownScreen(consecutiveUnknownScreenCount)
            }

            if (detectedKnownScreen) {
                endDataDownloadWait()
                consecutiveUnknownScreenCount = 0
                lobbyReentryAttempts = 0
                gameRestartAttemptsThisEpisode = 0
            }
            if (!bMiscBackPressedThisTick) {
                consecutiveMiscBackPresses = 0
            }
        } catch (e: CampaignBreakpointException) {
            return TaskResult.Success(
                TaskResultCode.TASK_RESULT_BREAKPOINT_REACHED,
                e.message ?: "Campaign breakpoint reached. Stopping bot...",
            )
        }

        return null
    }

    /** A pulled-down shade covers the top-region detection anchors and absorbs taps, and a misc template can match shade content and tap the bot's
     * own STOP BOT notification action. Free no-op when already closed; needs API 31+, older devices skip silently. */
    protected fun dismissNotificationShade(reason: String) {
        if (Build.VERSION.SDK_INT >= 31) {
            val dispatched = game.gestureUtils.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            MessageLog.i(TAG, "[INFO] Dismissed the notification shade in case it was open ($reason, dispatched=$dispatched).")
            settleAfterShadeDismiss(dispatched, waitForShadeClose = { game.wait(0.5) }, waitForLoading = { game.waitForLoading() })
        }
    }

    private fun dataDownloadRunning(): Boolean {
        val acceptedAt = game.dataDownloadAcceptedAtMs ?: return false
        if (Game.dataDownloadActive(acceptedAt, SystemClock.elapsedRealtime())) return true
        game.dataDownloadAcceptedAtMs = null
        MessageLog.w(TAG, "[DIALOG] The game data download did not finish within ${Game.LOADING_HARD_LIMIT_MS / 60_000} minutes. Back to the usual unknown-screen handling.")
        return false
    }

    private fun endDataDownloadWait() {
        val acceptedAt = game.dataDownloadAcceptedAtMs ?: return
        game.dataDownloadAcceptedAtMs = null
        MessageLog.i(TAG, "[DIALOG] The game data download finished after ${(SystemClock.elapsedRealtime() - acceptedAt) / 1000}s.")
    }

    /** The escalation replaces a single unbounded blind tap at (350, 450) that wedged the bot forever on an unrecognized overlay (notably an open
     * dialog whose title OCR returned empty, so [DialogUtils.getDialog] returned null). A visible dialog title banner means an unidentified dialog:
     * close it; otherwise nudge; after [maxUnknownScreenBeforeStop] ticks stop with a diagnostic capture. */

    /** The green "Skip >>" affordance is NOT cutscene-exclusive: it also sits on the main screen, skill list and race-day screens. A leading guard
     * on controls a real cutscene never shows (Training / Rest / Confirm / race-day ribbon) rules them out; without it the bot body-tapped
     * momentarily unrecognized normal screens and nudged the persistent Skip toggle to a slower speed (frequent in Unity Cup). */
    private fun isEventCutsceneSkipPillVisible(): Boolean {
        val sourceBitmap = game.imageUtils.getSourceBitmap()

        // A normal-screen control is present: a real screen that merely shows the ubiquitous "Skip >>" button.
        if (ButtonTraining.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
            ButtonRest.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
            ButtonConfirm.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
            IconRaceDayRibbon.check(game.imageUtils, sourceBitmap = sourceBitmap)
        ) {
            return false
        }

        // A dialog dims every control above it but leaves its own Skip pill readable, so none vetoes: a Grand Concert popup was once
        // advanced as a cutscene. A cutscene shows no dialog banner.
        if (DialogUtils.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return false
        }

        val skipState = readSkipPill(sourceBitmap)
        skipStateLog.record(skipState)
        return skipState.pillVisible
    }

    /** The persistent Skip pill's state on [bitmap]; Off is the template or the pill's colours ([skipOffPillByColour]). */
    private fun readSkipPill(bitmap: Bitmap): PersistentSkipState =
        classifyPersistentSkip(
            offPillMatched = { skipOffPill(bitmap) },
            onPillMatched = { ButtonSkipOn.check(game.imageUtils, sourceBitmap = bitmap) },
            skipTextFound = { skipPillTextFound(bitmap) },
        )

    private fun skipOffPill(bitmap: Bitmap): Boolean =
        ButtonSkipOff.check(game.imageUtils, sourceBitmap = bitmap) ||
            skipOffPillByColour(SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }, bitmap.width, bitmap.height)

    /** Tapping the pill's measured centre. A screen without an Off pill costs one template match and a colour read, and taps nothing. */
    private fun setFastSkipIfOff(screen: String) {
        val bitmap = game.imageUtils.getSourceBitmap()
        if (!skipOffPill(bitmap)) return
        val outcome =
            skipFix.attempt(
                PersistentSkipState.OFF,
                tapPillTwice = {
                    repeat(2) {
                        game.tapCoordinate(bitmap.width * SKIP_PILL_CENTRE_X_FRACTION, bitmap.height * SKIP_PILL_CENTRE_Y_FRACTION, "skip_pill")
                        game.wait(0.6)
                    }
                },
                reRead = { readSkipPill(game.imageUtils.getSourceBitmap()) },
            )
        when (outcome) {
            SkipFixOutcome.LEFT_OFF -> MessageLog.i(TAG, "[SKIP_PILL] The Skip pill read Off on the $screen; switched it to fast.")
            SkipFixOutcome.GIVING_UP -> MessageLog.w(TAG, "[SKIP_PILL] The Skip pill still reads Off on the $screen after two taps; leaving it for the rest of this career.")
            SkipFixOutcome.NOT_OFF, SkipFixOutcome.GAVE_UP_EARLIER -> {}
        }
    }

    private fun skipPillTextFound(sourceBitmap: Bitmap): Boolean {
        return try {
            val skipPillOcr =
                game.imageUtils.performOCROnRegion(
                    sourceBitmap,
                    (sourceBitmap.width * 0.22).toInt(),
                    (sourceBitmap.height * 0.94).toInt(),
                    (sourceBitmap.width * 0.31).toInt(),
                    (sourceBitmap.height * 0.04).toInt(),
                    useThreshold = false,
                    useGrayscale = false,
                    scale = 2.0,
                    debugName = "unknown_skip_pill_ocr",
                )
            if (skipPillOcr.uppercase().contains("SKIP")) {
                true
            } else {
                // Day-end event / result screens (support-card and scenario event dialogue, hint-level-up, goal-result) render the Skip button lower and
                // further left than the intro pill; the wider bottom-left scan catches them and they advance with the same body-tap.
                val skipButtonOcr =
                    game.imageUtils.performOCROnRegion(
                        sourceBitmap,
                        (sourceBitmap.width * 0.03).toInt(),
                        (sourceBitmap.height * 0.86).toInt(),
                        (sourceBitmap.width * 0.52).toInt(),
                        (sourceBitmap.height * 0.13).toInt(),
                        useThreshold = false,
                        useGrayscale = false,
                        scale = 2.0,
                        debugName = "unknown_skip_button_ocr",
                    )
                skipButtonOcr.uppercase().contains("SKIP")
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ends a ladder whose taps changed nothing on a known screen. If the own-input probe shows the bot's taps still land, the game has
     * stopped responding: it is restarted ([Game.reopenGame]) or, on Android 14+, brought to the front once ([unresponsiveReopensAfter]).
     * Otherwise, or when the run's tries are spent, the run stops with the truthful key.
     */
    private fun stopForStuckInput(episode: RebindEpisode, message: String) {
        val ownInput = game.ownInputReachesScreen()
        val key = stuckInputKey(episode.stopKey(), ownInput)
        if (reopensUnresponsiveGame(key, careerScreenObservedThisTask, unresponsiveGameReopens)) {
            val reopen = game.reopenGame(attempt = 2)
            val attempt = unresponsiveGameReopens + 1
            unresponsiveGameReopens = unresponsiveReopensAfter(reopen, attempt, Build.VERSION.SDK_INT)
            if (reopen != GameReopen.NOT_DISPATCHED) {
                val tried =
                    if (unresponsiveGameReopens > attempt) {
                        "a re-front, the run's last try (this Android version does not let the bot close the game)"
                    } else {
                        "closing try $attempt/$MAX_UNRESPONSIVE_GAME_REOPENS_PER_RUN"
                    }
                MessageLog.w(TAG, "[RECOVERY] The game stopped responding on a screen the bot knows (its own taps still reach the screen): $tried ${reopenOutcomeWords(reopen)}.")
                consecutiveUnknownScreenCount = 0
                lobbyReentryAttempts = 0
                consecutiveDialogTicks = 0
                dialogStopAt = dialogTicksBeforeStop
                cutsceneStopAt = maxCutsceneAdvanceBeforeStop
                return
            }
        }
        MessageLog.w(TAG, "[RECOVERY] Stopping for taps that changed nothing: own-input probe $ownInput, reason ${key ?: "none"}.")
        key?.let { requestAccessibilityHalt(it) }
        throw InterruptedException(if (key == GAME_NOT_RESPONDING) "$message The bot's own taps still reached the screen, so the game stopped responding." else message)
    }

    private fun recoverFromUnknownScreen(count: Int) {
        if (count == 1) {
            // First unrecognized tick: clear a notification shade that may be covering the top-region anchors.
            dismissNotificationShade("unknown screen")
        }

        // Story / chain support-card events (e.g. Air Shakur's "Both High and Low") open with an intro cutscene to tap through before the choices
        // render. There is no choice horseshoe then, so every other screen check misses; the bottom-left Skip pill renders only during these cutscenes
        // and is the signal to body-tap (the misc skip handler only knows the distinct `skip` template). Bounded by maxCutsceneAdvanceBeforeStop.
        if (isEventCutsceneSkipPillVisible()) {
            if (count >= cutsceneStopAt) {
                cutsceneRebinds.closeLast()
                if (shouldTryStrongToggle(cutsceneRebinds, game.strongToggleUsed)) {
                    cutsceneRebinds.record(game.strongToggleAccessibilityService())
                    cutsceneStopAt = count + STRONG_TOGGLE_GRACE_TICKS
                } else {
                    game.imageUtils.saveBitmap(filename = "event_cutscene_stuck", fullRes = true)
                    stopForStuckInput(
                        cutsceneRebinds,
                        "Bot stuck advancing an event cutscene for $count consecutive cycles. Stopping. " +
                            "A screenshot was saved to the temp folder as event_cutscene_stuck.",
                    )
                    return
                }
            }
            if (count in cutsceneRebindThresholds) {
                if (count == cutsceneRebindThresholds.min()) {
                    cutsceneRebinds.start()
                    cutsceneStopAt = maxCutsceneAdvanceBeforeStop
                }
                MessageLog.w(
                    TAG,
                    "[WARN] recoverFromUnknownScreen:: Event cutscene not advancing after $count taps - forcing an Accessibility Service rebind in case gesture dispatch died.",
                )
                cutsceneRebinds.record(game.forceRebindAccessibilityService())
            }
            // Before the rebind ladder starts only: a stuck-input episode must not gain pill taps.
            if (count < cutsceneRebindThresholds.min()) setFastSkipIfOff("event screen")
            MessageLog.i(TAG, "[MISC] Event cutscene intro detected (Skip pill present); tapping to advance the dialogue toward the choices (tap $count).")
            game.tap(540.0, 1300.0, taps = 1)
            return
        }

        // A mid-career bounce to the outer Home lobby (the 17:00 JST daily-reset reload, an app resume, a crash-to-title) is invisible to every
        // in-career check; the between-run path would treat it as a finished run, advance the rotation cursor and rebuild Training/TrainingEvent
        // on the wrong preset. Re-enter THIS career in place through the navigator instead. Gated at >=2 cycles (a one-frame misdetect must not
        // trigger it) and on careerScreenObservedThisTask (a bot started at the lobby must not launch a career on stale state); a failure falls
        // through to the standard ladder.
        if (count >= 2 && careerScreenObservedThisTask && lobbyReentryAttempts < maxLobbyReentryAttempts) {
            val navigator = CareerLaunchNavigator(game.myContext)
            navigator.attachLiveGame(game)
            if (navigator.isOnHomeScreen()) {
                lobbyReentryAttempts++
                SessionTally.lobbyReentries.incrementAndGet()
                MessageLog.w(TAG, "[RECOVERY] Detected the game's Home lobby mid-career (likely a daily-reset bounce). Re-entering the in-progress career in place (attempt $lobbyReentryAttempts/$maxLobbyReentryAttempts)...")
                val result = navigator.navigate(reuseLastLaunchSetup = true, resumeInProgressCareer = true)
                if (result.success) {
                    MessageLog.i(TAG, "[RECOVERY] Re-entered the career via the navigator; resuming the in-career loop.")
                    consecutiveUnknownScreenCount = 0
                    lobbyReentryAttempts = 0
                    return
                }
                MessageLog.w(TAG, "[RECOVERY] Lobby re-entry failed (${result.failureReason}); falling through to the standard recovery ladder.")
            }
        }

        // MuMu can leave the Accessibility Service "enabled" while gesture dispatch silently dies, so the string check passes and blind taps no-op,
        // wedging even a recognizable screen to the stop cap. Past a normal transition, force a hard off->on rebind. Falls through to the stop if it
        // cannot help (e.g. WRITE_SECURE_SETTINGS missing).
        if (count in gestureRebindThresholds) {
            if (count == gestureRebindThresholds.min()) unknownScreenRebinds.start()
            MessageLog.w(
                TAG,
                "[WARN] recoverFromUnknownScreen:: Stuck for $count cycles - forcing an Accessibility Service rebind in case gesture dispatch died silently.",
            )
            unknownScreenRebinds.record(game.forceRebindAccessibilityService())
        }

        // Last resort: reopen the game, for a GAME-side soft-lock a rebind cannot fix or a game that has gone away. Gated to a career in
        // progress (careerScreenObservedThisTask) so a bot parked at the lobby never relaunches, bounded by [maxGameRestartAttempts].
        // Each attempt resets the unknown-screen budget so a cold boot has time to land; the career resumes via Continue Career.
        // The reopen acts before anything is logged.
        if (shouldRelaunchGame(count, gameRestartThreshold, gameRestartAttemptsThisEpisode, maxGameRestartAttempts, careerScreenObservedThisTask)) {
            unknownScreenRebinds.closeLast()
            gameRestartAttemptsThisEpisode++
            val reopen = game.reopenGame(gameRestartAttemptsThisEpisode)
            if (reopen != GameReopen.NOT_DISPATCHED) {
                MessageLog.w(
                    TAG,
                    "[RECOVERY] Stuck for $count cycles and gesture rebinds did not help - reopening the game " +
                        "(attempt $gameRestartAttemptsThisEpisode/$maxGameRestartAttempts): ${reopenOutcomeWords(reopen)}.",
                )
                // Fresh window for the reopened game: the lobby re-entry branch resumes the career. If the game did not come back,
                // the counter climbs to the threshold again and the next attempt fires.
                consecutiveUnknownScreenCount = 0
                lobbyReentryAttempts = 0
                return
            }
            MessageLog.w(TAG, "[RECOVERY] The game could not be reopened; falling through to the standard stop.")
        }

        if (DialogUtils.check(game.imageUtils)) {
            MessageLog.w(TAG, "[WARN] recoverFromUnknownScreen:: A dialog banner is present but could not be identified (tick $count). Closing it.")
            if (ButtonClose.click(game.imageUtils)) {
                game.wait(0.5)
                return
            }
            // The Trackblazer Shop "lineup has been refreshed" dialog has Cancel/Shop buttons and flaky title OCR, so getDialog fails to name it and it
            // would be unclosable (it killed a queue after 25 stuck cycles). ButtonShop renders only on shop dialogs, so tapping it enters the shop; it
            // no-ops on any other dialog.
            if (ButtonShop.click(game.imageUtils)) {
                MessageLog.i(TAG, "[INFO] recoverFromUnknownScreen:: Entered the Shop via its button on an unidentified shop dialog.")
                game.wait(1.0)
                return
            }
            MessageLog.w(TAG, "[WARN] recoverFromUnknownScreen:: No Close or Shop button found on the unidentified dialog; nudging instead.")
        }

        if (count >= maxUnknownScreenBeforeStop) {
            unknownScreenRebinds.closeLast()
            game.imageUtils.saveBitmap(filename = "unknown_screen_stuck", fullRes = true)
            // If a relaunch was tried and no game screen came back, the game is unrecoverable: flag it so the queue PAUSES instead of launching the next
            // run onto a dead/foreign screen, regardless of stopOnError. A stop with no relaunch attempted stays a generic error.
            if (stopIsGameUnrecoverable(gameRestartAttemptsThisEpisode)) {
                StartModule.gameRecoveryFailed = true
                MessageLog.e(
                    TAG,
                    "[RECOVERY] The game could not be recovered after $gameRestartAttemptsThisEpisode reopen " +
                        "attempt(s); the bot is on an unrecognized screen. Pausing the queue.",
                )
            }
            val reason =
                "Bot stuck on an unrecognized screen for $count consecutive cycles. Stopping. " +
                    "A screenshot was saved to the temp folder as unknown_screen_stuck."
            // An unrecognized screen may be the game's fault, so issued rebinds prove nothing about input here; a refused one proves the repair could not be tried.
            if (unknownScreenRebinds.refused > 0 && !StartModule.gameRecoveryFailed) requestAccessibilityHalt(A11Y_GRANT_MISSING)
            throw InterruptedException(reason)
        }

        // Award/ceremony screens (the first-time trophy popup after a finals win, ending cards) have no dialog banner and ignore the legacy nudge
        // spot; they dismiss on a standard OK or a tap near the bottom-center.
        if (ButtonOk.click(game.imageUtils)) {
            MessageLog.i(TAG, "[INFO] recoverFromUnknownScreen:: Dismissed an unrecognized screen via its OK button.")
            game.wait(1.0)
            return
        }
        // First-win trophy popups use Close instead of OK (the URA Finals dirt-champion trophy sat through OK attempts and both nudges).
        if (ButtonClose.click(game.imageUtils)) {
            MessageLog.i(TAG, "[INFO] recoverFromUnknownScreen:: Dismissed an unrecognized screen via its Close button.")
            game.wait(1.0)
            return
        }
        // Post-turn result / event screens (GOAL COMPLETE!, race results, achievement and hint popups) advance via Next or Skip, not OK/Close. Tap
        // the affordance directly; each no-ops when absent, and the screen is already unknown so advancing is safe.
        if (ButtonNext.click(game.imageUtils)) {
            MessageLog.i(TAG, "[INFO] recoverFromUnknownScreen:: Advanced a result/continue screen via its Next button.")
            game.wait(1.0)
            return
        }
        if (ButtonNextRaceEnd.click(game.imageUtils)) {
            MessageLog.i(TAG, "[INFO] recoverFromUnknownScreen:: Advanced a race-result screen via its Next button.")
            game.wait(1.0)
            return
        }
        if (ButtonSkip.click(game.imageUtils)) {
            MessageLog.i(TAG, "[INFO] recoverFromUnknownScreen:: Skipped through a result/event screen via its Skip button.")
            game.wait(1.0)
            return
        }
        if (count % 2 == 0) {
            game.tap(540.0, 1300.0, taps = 1)
        } else {
            game.tap(350.0, 450.0, taps = 1)
        }
    }
}
