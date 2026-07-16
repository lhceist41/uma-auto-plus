package com.steve1316.uma_android_automation.bot

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.DiscordUtils
import com.steve1316.automation_library.utils.ImageUtils.ScaleConfidenceResult
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.BuildConfig
import com.steve1316.uma_android_automation.CareerLaunchNavigator
import com.steve1316.uma_android_automation.StartModule
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
import com.steve1316.uma_android_automation.types.TrackDistance
import com.steve1316.uma_android_automation.types.TrackSurface
import com.steve1316.uma_scoring.RankAptitudes
import com.steve1316.uma_scoring.SkillScoreInput
import com.steve1316.uma_scoring.estimateRank
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.types.Trainee
import com.steve1316.uma_android_automation.utils.OutcomeCorpus
import com.steve1316.uma_android_automation.utils.ScrollList
import com.steve1316.uma_android_automation.utils.TraineeNameMatcher
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

    /** Lazily-built [SkillList] used only for career-end screen detection in [process]. Lazy and
     * shared because the constructor generates the full skill entries map from the database. */
    private val careerEndScreenChecker: SkillList by lazy { SkillList(game, this) }

    /** Set once the careerComplete skill plan has run this career, so the End-screen handler and
     * the direct Learn-screen handler do not re-enter the list and run the full plan twice. */
    private var bCareerEndSkillsHandled: Boolean = false

    /** Set true the moment the bot confirms a force-end it can actually observe at its source: a lost
     * mandatory race the game will not let us retry past. Read by [careerEndLedgerLine] to emit
     * outcome=FORCE_END. Most force-ends (fan / Result-Pts checkpoint misses) are NOT observable at
     * their trigger and stay outcome=COMPLETED (only `turn` distinguishes those from a win); this
     * flag only catches the mandatory-race-loss class. A fresh Campaign instance runs each career, so
     * the false default is the per-career reset. */
    private var careerForceEnded: Boolean = false

    /** Reason paired with [careerForceEnded] (e.g. "MANDATORY_RACE_LOST"), emitted in the ledger. */
    private var forceEndReason: String? = null

    /** Record a confirmed career force-end once. Idempotent - keeps the first reason so a later
     * dialog redraw on the same failure cannot overwrite it. */
    private fun markCareerForceEnded(reason: String) {
        if (!careerForceEnded) {
            careerForceEnded = true
            forceEndReason = reason
        }
    }

    /** Finale-race results observed this career, for the outcome ledger's win/lose signal. The URA
     * finale (days 73-75, any race tagged RaceGrade.FINALE) runs through the mandatory-race path, so
     * [Racing.finalizeRaceResults] reports each result here via [noteFinaleRaceResult]. [finaleRaces]
     * counts finale races seen; [finaleRaces1st] how many were taken at 1st place (the Congratulations
     * banner). A swept URA arc reads 3/3 (=> quality WIN); a COMPLETED career with finaleRaces==0 never
     * reached a finale; finaleRaces1st < finaleRaces means it reached the finale but lost a race. A
     * fresh Campaign runs each career, so the zero default is the per-career reset. */
    private var finaleRaces: Int = 0
    private var finaleRaces1st: Int = 0

    /** Record one finale-race result. Called from [Racing.finalizeRaceResults] on a FINALE-grade race. */
    fun noteFinaleRaceResult(won: Boolean) {
        finaleRaces++
        if (won) finaleRaces1st++
    }

    /** Whether this scenario's FINALE-graded races show the 1st-place "Congratulations" banner that
     * [Racing.finalizeRaceResults] reads for the win/lose ledger signal. Only URA Finale is verified.
     * Trackblazer also tags its Twinkle Star Climax races RaceGrade.FINALE (Trackblazer.kt) but uses a
     * different result UI, so recording there could mislabel a good Climax career FINALE_LOST. Off by
     * default; a scenario opts in only once its finale banner is confirmed on a live device. */
    open val capturesFinaleWins: Boolean = false

    /** Attempts made to actively exit the career-end skill screen after the plan already ran. */
    private var careerEndExitAttempts: Int = 0

    /** Bound for [careerEndExitAttempts] before stopping with a diagnostic capture. */
    private val maxCareerEndExitAttempts: Int = 5

    /** Attempts made to open the career-end "Learn" skill screen from the result screen. The
     * Learn screen can take several seconds to load after the click, so the buy is routed through
     * the screen-confirmed checkCareerEndSkillListScreen branch rather than a single fixed wait in
     * the End-screen branch (a slow career-end otherwise dropped 544 SP). Bounded so a
     * missing/abnormal Learn button completes the career instead of looping. */
    private var careerEndEntryAttempts: Int = 0

    /** Bound for [careerEndEntryAttempts] before completing the career without the careerComplete plan. */
    private val maxCareerEndEntryAttempts: Int = 6

    /** Consecutive process() ticks resolved ONLY by the misc back-press. A long unbroken streak
     * means the press is not changing the screen and the loop would otherwise spin forever (10+
     * minutes of back-presses observed on a wedged career-end skill screen). */
    private var consecutiveMiscBackPresses: Int = 0

    /** Bound for [consecutiveMiscBackPresses] (~75s at the observed tick rate) before stopping. */
    private val maxConsecutiveMiscBackPresses: Int = 25

    /** Tick-local marker: this tick was handled by the misc back-press branch. */
    private var bMiscBackPressedThisTick: Boolean = false

    /** Required instance of the Trainee class. */
    val trainee: Trainee = Trainee()

    /** Required instance of the Training class. Reassignable so [reloadTraineeConfig] can rebuild it
     * onto a resynced preset - both Training and TrainingEvent cache their config at construction. */
    var training: Training = Training(game, this)

    /** Required instance of the TrainingEvent class. Reassignable; see [reloadTraineeConfig]. */
    protected var trainingEvent: TrainingEvent = TrainingEvent(game, this)

    /**
     * Rebuilds [training] and [trainingEvent] from the current settings DB. Called after the rotation
     * mismatch guard resyncs onto a different trainee's snapshot: both classes cache their config
     * (stat priorities/targets, failure cap, the four event-override maps) at construction, so a
     * mid-flight snapshot swap would otherwise leave the career running the wrong preset's cached
     * values for the rest of the run (the pre-2026-07-10 defect that ran Winning Ticket under Symboli
     * Rudolf's config). The resync fires at the umamusume_details dialog - ~turn 1 on a fresh career,
     * or immediately on a re-entered one - before any training decision, and both constructors are
     * pure config reads whose per-tick caches reset to empty harmlessly.
     */
    private fun reloadTraineeConfig() {
        training = Training(game, this)
        trainingEvent = TrainingEvent(game, this)
        // Racing and SkillPlan construction-cache career-shaping config too (the curated racing
        // plan, fan-farming policy, the skill plans map) - a resync that misses them races and
        // buys skills on the wrong preset. Reconstruction resets their per-career heuristics
        // (e.g. the consecutive-race counter) to a fresh start, which is acceptable at the
        // turn-1/re-entry point the resync fires at and strictly better than wrong config.
        racing = Racing(game, this)
        skillPlan = SkillPlan(game, this)
        // The outcome record must report the config the career actually RUNS on from here out -
        // without this refresh a resynced career plays correctly but fingerprints as the old arm.
        outcomeConfigSnapshot = buildOutcomeConfigSnapshot()
    }

    /**
     * Fingerprint of rotation slot [index]'s STORED snapshot (its rot{i}_-prefixed settings rows),
     * over the same key set as the live fingerprint. Null when the slot has no stored scenario
     * (no snapshot captured), so callers skip the comparison instead of warning on noise.
     */
    private fun rotationSlotFingerprint(index: Int): String? {
        if (index < 0) return null
        val slotScenario = SettingsHelper.getStringSetting("rot${index}_general", "scenario").ifEmpty { return null }
        return outcomeConfigFingerprint(BuildConfig.VERSION_NAME, buildOutcomeConfigSnapshot("rot${index}_", slotScenario))
    }

    /**
     * Compares the LIVE config fingerprint against rotation slot [slotIndex]'s stored snapshot and
     * logs the verdict under [CONFIG_DRIFT]. The explicit OK line is deliberate - it is the
     * observable proof that a career is running the intended preset (silence proves nothing in a
     * rotated-away log). A mismatch means the settings DB no longer holds what the slot's preset
     * captured: the wrong-preset class of failure that ran Winning Ticket under Rudolf's config.
     */
    private fun warnOnTraineeConfigDrift(slotIndex: Int, context: String) {
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

    /** Required instance of the GameDate class. */
    var date: GameDate = GameDate(day = 1)

    /**
     * Per-turn structured decision logger. Records WHY each turn's action / training / race / skill
     * decision was made and emits one consolidated Decision Report block at turn end. Null unless this
     * is a debug build or Debug Mode is enabled, so every `decisionTracer?.…` call compiles to a
     * null-safe no-op. The block is a heavy diagnostic (one multi-line MessageLog write per turn),
     * gated off by default in release for performance and log size, mirroring the fixture-capture gate.
     */
    val decisionTracer: DecisionTracer? =
        if (com.steve1316.uma_android_automation.BuildConfig.DEBUG || game.debugMode) DecisionTracer() else null

    /** Flag to track whether the bot should force Wit training during the pre-summer turn. */
    var bForcedWitTraining: Boolean = false

    /** Flag to track if the bot should force a specific target mood during recovery. */
    var forcedTargetMood: Mood? = null

    /**
     * Configurable mood floor: bot recovers mood when current mood drops below this level.
     *
     * Values: "Normal", "Good", "Great". Default "Good" matches historical
     * `shouldRecoverMood` behavior (`mood < Mood.GOOD`). Setting to "Great" is the strict
     * guard for trainees with single-option mood-trap events (e.g. Agnes Tachyon's
     * "Report: A Clear Gaze" event redirects the objective race to NHK Mile Cup if mood
     * is Normal or worse on the trigger date — running with a Great floor keeps mood
     * at or above Good across the trigger window).
     *
     * Trade-off: stricter floors burn more turns on Recreation/Date and reduce training
     * pixels. Only enable when the trainee actually has the trap event.
     */
    private val moodFloorString: String = SettingsHelper.getStringSetting("training", "moodFloor", "Good")

    /** Resolved Mood enum form of [moodFloorString]. Falls back to GOOD on unrecognized strings. */
    protected val moodFloor: Mood =
        when (moodFloorString.lowercase()) {
            "normal" -> Mood.NORMAL
            "great" -> Mood.GREAT
            else -> Mood.GOOD
        }

    /**
     * Pre-career deck validation: when enabled, the first time aptitudes are read for a
     * career run, the bot checks that the trainee's preferred-distance and preferred-style
     * aptitudes meet [deckValidationMinAptitude]. If the deck is below the floor, the bot
     * logs a high-visibility MessageLog warning so the user knows the trainee/scenario
     * combo will fight the chosen race lineup.
     *
     * Validation is informational — it does NOT halt the run. The user can interrupt and
     * pick a better deck if they care, or let it ride.
     */
    protected val enableDeckValidation: Boolean = SettingsHelper.getBooleanSetting("training", "enableDeckValidation", true)

    /**
     * Config snapshot for the outcome corpus (see PLAN_OUTCOME_MEASUREMENT.md Stage 3), captured
     * at construction while THIS run's settings are live - a rotation switch rewrites the active
     * settings between runs, so reading them at task end could tag the record with the NEXT
     * trainee's config. The set is enumerated deliberately: the tunables that shape play quality.
     * A field added here changes every fingerprint, deliberately starting new arms.
     */
    private var outcomeConfigSnapshot: Map<String, String> = buildOutcomeConfigSnapshot()

    /**
     * Builds the enumerated config snapshot from the settings DB. With [categoryPrefix] set (e.g.
     * "rot2_") it reads a rotation slot's STORED snapshot rows instead of the live settings, over
     * the identical key set - which makes the two fingerprints directly comparable for the
     * [CONFIG_DRIFT] check. [scenarioForKeys] gates the scenario-conditional keys (the slot's own
     * scenario for slot reads; the live scenario otherwise).
     */
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
        // The plan CONTENT matters, not just the flag: editing a curated racing plan changes how
        // the career races, so it must split arms. A digest keeps the record small.
        val racingPlan = SettingsHelper.getStringSetting(racing, "racingPlan")
        cfg["racingPlanDigest"] = if (racingPlan.isEmpty()) "none" else shortSha1(racingPlan)
        if (scenarioForKeys == "Trackblazer") {
            cfg["trackblazerEnergyThreshold"] = SettingsHelper.getIntSetting(scenarioOverrides, "trackblazerEnergyThreshold", 40).toString()
            cfg["trackblazerConsecutiveRacesLimit"] = SettingsHelper.getIntSetting(scenarioOverrides, "trackblazerConsecutiveRacesLimit", 2).toString()
            cfg["trackblazerEnableIrregularTraining"] = SettingsHelper.getBooleanSetting(scenarioOverrides, "trackblazerEnableIrregularTraining", false).toString()
        }
        return cfg
    }

    /**
     * Minimum aptitude letter required for the trainee's preferred distance and running
     * style to clear deck validation. Default "B" matches the in-game soft requirement
     * for race-bonus uplift; "A" is the strict meta-deck floor.
     */
    private val deckValidationMinString: String = SettingsHelper.getStringSetting("training", "deckValidationMinAptitude", "B")

    /** Resolved Aptitude floor for deck validation. Falls back to B on unrecognized strings. */
    protected val deckValidationMinAptitude: Aptitude = Aptitude.fromName(deckValidationMinString) ?: Aptitude.B

    /**
     * Set once after the first successful aptitude read so the validation check fires only
     * once per career run (not on every aptitude refresh).
     */
    private var bDeckValidationChecked: Boolean = false

    /**
     * Set once after the rotation trainee verify runs so it fires only once per career run.
     * Independent of [bDeckValidationChecked]: the verify must run regardless of the deck-validation
     * setting. A fresh Campaign is built per queue run, so this resets to false each career.
     */
    private var bRotationTraineeVerified: Boolean = false

    /**
     * Jaro-Winkler floor (over de-outfitted names) for the rotation trainee verify to treat two
     * names as the same character. A clear read of the correct trainee scores ~1.0; a different
     * character scores well under this. Deliberately near the navigator's select threshold (0.86)
     * but slightly lenient, because a STOP halts the whole unattended queue.
     */
    private val rotationVerifyMatchThreshold: Double = 0.85

    /**
     * Number of consecutive process() ticks that ended without detecting any known screen. Drives
     * [recoverFromUnknownScreen]'s escalation instead of blind-tapping a fixed point forever. Reset
     * to 0 whenever any known screen or dialog is handled.
     */
    private var consecutiveUnknownScreenCount: Int = 0

    /** Consecutive process() ticks that resolved as "a dialog was handled". A dialog normally
     * clears in a tick or two; a long streak means the taps are not landing (MuMu's
     * enabled-but-dispatch-dead mode) while the dialog stays up. Mirrors the
     * recoverFromUnknownScreen 13/19/25 ladder. Reset on any non-dialog tick. */
    private var consecutiveDialogTicks: Int = 0

    /**
     * Home-lobby re-entry attempts used within the current unknown-screen streak. A mid-career bounce
     * to the game's outer lobby (daily-reset reload) is re-entered in place via the navigator, but if
     * that cannot advance (e.g. silently dead gesture dispatch) it is capped at [maxLobbyReentryAttempts]
     * so the loop falls through to the standard stop instead of thrashing. Reset with
     * [consecutiveUnknownScreenCount].
     */
    private var lobbyReentryAttempts: Int = 0

    /**
     * True once this task has recognized in-career UI (a Main training screen tick). Gates the
     * Home-lobby re-entry: a bot STARTED with the game parked at the lobby (no career in flight)
     * must fall through to the normal ladder/stop instead of driving the launch flow - an
     * interrupted queue can leave a stale trainee target armed there, and reusing it would silently
     * start a career for the wrong trainee (2026-07-09: El Condor selected instead of the applied
     * Rudolf preset). A daily-reset bounce always happens after main-screen ticks, so the gate
     * never blocks the case this recovery exists for.
     */
    private var careerScreenObservedThisTask: Boolean = false

    /**
     * Upper bound on [consecutiveUnknownScreenCount] before the bot stops with a diagnostic rather
     * than loop on an unrecognized screen forever. ~25 ticks is roughly a minute of being stuck.
     */
    private val maxUnknownScreenBeforeStop: Int = 25

    /** Max in-place Home-lobby re-entries per unknown-screen streak (see [lobbyReentryAttempts]). */
    private val maxLobbyReentryAttempts: Int = 3

    /**
     * Stuck-cycle counts at which [recoverFromUnknownScreen] force-rebinds the Accessibility Service.
     * MuMu can silently kill gesture dispatch while leaving the service "enabled" in secure settings,
     * so the per-tick [Game.ensureAccessibilityService] string check passes and every blind tap
     * no-ops - the bot then loops to [maxUnknownScreenBeforeStop] and stops. These thresholds sit
     * above the observed healthy-transition max (~12 unknown cycles for a long event animation) so
     * a normal transition never triggers an unnecessary rebind, but a real gesture-death self-heals
     * well before the stop.
     */
    private val gestureRebindThresholds: Set<Int> = setOf(13, 19)

    /**
     * Stuck-cycle count at which [recoverFromUnknownScreen] relaunches the whole game as a last resort.
     * Sits AFTER both gesture rebinds (13, 19) - so it only fires once a dead-dispatch rebind has
     * demonstrably not helped - and BEFORE the stop (25), so there is room for the relaunch + a fresh
     * cycle before giving up. This is the rung for a game-side soft-lock (an un-driveable screen that
     * is not MuMu gesture death), e.g. a race the account has never run that wedged before the play
     * path could handle it (2026-07-11).
     */
    private val gameRestartThreshold: Int = 22

    /**
     * Guards [Game.restartGame] to one attempt per stuck episode. Reset to false whenever a known
     * screen is handled (progress made), so a later, distinct wedge can restart again, but a restart
     * that did NOT clear the current wedge cannot loop into a relaunch storm - the episode falls
     * through to the normal stop instead.
     */
    private var gameRestartAttemptedThisEpisode: Boolean = false

    /**
     * Cap on [consecutiveUnknownScreenCount] while a story-event intro cutscene is being tapped
     * through (Skip pill present). Higher than [maxUnknownScreenBeforeStop] because a support-card
     * chain event (e.g. "Both High and Low") can run 20+ dialogue bubbles before its choices render;
     * the choices end the cutscene well before this, so reaching it means the cutscene is genuinely
     * frozen. Without this path the generic cap fired mid-intro and stopped the run before the
     * choices ever appeared.
     */
    private val maxCutsceneAdvanceBeforeStop: Int = 50

    /**
     * Counts at which the cutscene-advance path force-rebinds the Accessibility Service once, in case
     * gesture dispatch silently died (taps no-op so the dialogue never moves). Two attempts leave room
     * for a revived dispatch to clear the intro before [maxCutsceneAdvanceBeforeStop].
     */
    private val cutsceneRebindThresholds: Set<Int> = setOf(15, 30)

    /** Whether the bot should attempt the crane game. */
    protected val enableCraneGameAttempt: Boolean = SettingsHelper.getBooleanSetting("general", "enableCraneGameAttempt")

    /** Whether the bot should check for a skill point threshold. */
    protected val enableSkillPointCheck: Boolean = SettingsHelper.getBooleanSetting("skills", "enableSkillPointCheck")

    /** Whether the bot should stop at a specified date. */
    protected val enableStopAtDate: Boolean = SettingsHelper.getBooleanSetting("general", "enableStopAtDate")

    /** Whether the bot should stop before the final race. */
    protected val enableStopBeforeFinals: Boolean = SettingsHelper.getBooleanSetting("general", "enableStopBeforeFinals")

    /** Whether the bot must rest before Summer. */
    protected val mustRestBeforeSummer: Boolean = SettingsHelper.getBooleanSetting("training", "mustRestBeforeSummer")

    /** The number of skill points required to trigger a check. */
    protected val skillPointsRequired: Int = SettingsHelper.getIntSetting("skills", "skillPointCheck")

    /** The list of date strings at which the bot should stop. */
    protected val stopAtDates: List<String> =
        run {
            val json = SettingsHelper.getStringSetting("general", "stopAtDates", "[]")
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
            val json = SettingsHelper.getStringSetting("general", "recreationTurns", "[]")
            try {
                org.json.JSONArray(json).let { arr -> (0 until arr.length()).map { arr.getInt(it) }.toSet() }
            } catch (_: Exception) {
                setOf()
            }
        }

    /** The single career turn pinned for the final outing / Pure Passion activation, or a non-positive value when unset. */
    protected val purePassionTurn: Int = SettingsHelper.getIntSetting("general", "purePassionTurn", 60)

    /** The total outings in the active support card's recreation chain (Team Sirius 7, Heirs to the Throne 5). */
    protected val recreationTotalOutings: Int = SettingsHelper.getIntSetting("general", "recreationTotalOutings", 7)

    /** Whether the recreation chain is complete for this run - no more dates are available. Latched true once the in-game
     * complete label is seen and never reset. Distinct from [recreationDateCompleted] on purpose: that flag means "a date
     * was handled TODAY" and resets every turn (it short-circuits re-checks within one recovery sequence), while this one
     * is the run-lifetime chain state the schedule keys on. Upstream overloads one flag for both; our per-turn reset is
     * load-bearing for the recovery paths, so the two meanings get two fields. */
    protected var recreationChainComplete: Boolean = false

    /** The number of recreation outings actually started this run. Used to hold the final outing for the Pure Passion turn. */
    protected var recreationOutingsStarted: Int = 0

    /** The group-event chain length as last read from the game's "X/Y" progress, or the configured fallback until the partner dialog is first read. */
    protected var recreationTotalOutingsKnown: Int = recreationTotalOutings

    /** Latch: a scheduled recreation attempt backed out without starting an outing this turn (no rows, held final, dead
     * pill). Backing out does not advance the game turn, so without this the decision loop would re-choose DATE and
     * reopen the same dialog forever (the Staticwitt-reported livelock). Cleared when the date actually advances. */
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

    /** The number of consecutive failed attempts at handling the skill point check for this run.
     * Used in conjunction with [skillPointCheckMaxAttempts] to prevent an infinite retry loop
     * if [handleSkillListScreen] repeatedly fails to respond (e.g. UI state we never recover from).
     * Reset when skill points drop below the threshold or when the check succeeds.
     */
    protected var skillPointCheckAttempts: Int = 0

    /** The maximum number of consecutive failed attempts at handling the skill point check
     * before we give up for this run and allow normal turn execution to resume.
     */
    protected val skillPointCheckMaxAttempts: Int = 3

    /** Flag indicating if the pre-finals check has been handled. */
    protected var bHasHandledPreFinalsCheck: Boolean = false

    /** The number of consecutive failed attempts at handling the pre-finals skill purchase for this run.
     * Used in conjunction with [preFinalsCheckMaxAttempts] to prevent an infinite retry loop if
     * [handleSkillListScreen] repeatedly fails (e.g. UI state we never recover from while the bot keeps
     * clicking ButtonSkills on a wrong screen). Resets when the check succeeds or run advances past day 72.
     */
    protected var preFinalsCheckAttempts: Int = 0

    /** The maximum number of consecutive failed attempts at handling the pre-finals skill purchase before
     * we give up for this run and allow normal turn execution to resume.
     */
    protected val preFinalsCheckMaxAttempts: Int = 3

    /** Flag indicating if the bot has checked for a maiden race today. */
    var bHasCheckedForMaidenRaceToday: Boolean = false

    /** Flag indicating if the date has been checked during the current turn.
     * This is necessary to prevent redundant date checks when no game-advancing action was taken.
     * Reset to false when training, resting, racing, or other game-advancing actions complete.
     */
    protected var bHasCheckedDateThisTurn: Boolean = false

    /**
     * Per-turn caches for race-day detection. Computed once at the top of [handleMainScreen]
     * (where we already have a fresh main-screen bitmap from [performTurnStartUpdates]) and
     * reused by [decideNextAction] and the post-decision tail of [handleMainScreen]. Prevents
     * three separate template-match scans per turn for the same two checks.
     *
     * Lifetime: same as [bHasCheckedDateThisTurn]. Reset to false at the start of every fresh
     * turn so that stale results from a prior turn never leak into the new turn's decisions.
     */
    protected var cachedScheduledRaceDay: Boolean = false
    protected var cachedMandatoryRaceDay: Boolean = false

    /**
     * Per-turn cache of the goal-ribbon ([IconGoalRibbon]) detection. Kept separate from
     * [cachedMandatoryRaceDay] on purpose: the goal ribbon stays visible on the Main screen for an
     * active objective (see [checkMandatoryRacePrepScreen]), so it is NOT safe to feed into the
     * forced-RACE branch of [decideNextAction]. This flag exists solely as a cache backup for the
     * Trackblazer irregular-training gate, whose live mandatory check is `IconRaceDayRibbon ||
     * IconGoalRibbon` — so a single missed goal-ribbon read on a real goal day cannot let irregular
     * training hijack the turn. Over-blocking the gate is conservative; only the gate reads it.
     * Lifetime: same as [cachedMandatoryRaceDay].
     */
    protected var cachedGoalRibbonDay: Boolean = false

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
                "debugMode_startRainbowDetectionTest" to ::startRainbowDetectionTest,
            )

        var bDidAnyTestsRun = false
        for ((settingName, fn) in fnMap) {
            if (SettingsHelper.getBooleanSetting("debug", settingName)) {
                fn()
                bDidAnyTestsRun = true
            }
        }

        return bDidAnyTestsRun
    }

    /**
     * Debug test for rainbow-training detection. Run this while on the Training screen: it repeatedly detects the rainbow glow ring on each support face circle for ~5 seconds,
     * logs the per-support hue metrics and the derived rainbow count, and saves an annotated crop of the support region for calibrating the geometry and thresholds.
     */
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

    /**
     * Read-only Trainee Select OCR diagnostic for rotation calibration. Reuses the running bot's
     * image utils and logs what the header detector + name-banner color OCR read (plus the computed
     * grid tap targets) without tapping anything. Park the game on Trainee Select before running.
     */
    open fun startTraineeSelectTest() {
        MessageLog.i(TAG, "\n[TEST] Running read-only Trainee Select OCR diagnostic...")
        CareerLaunchNavigator(game.myContext).debugTraineeSelectRead(game.imageUtils)
    }

    /**
     * Read-only support-deck composition diagnostic for calibrating the [DECK] concentration read.
     * Reuses the running bot's image utils and logs each stat-type count off the deck screen without
     * tapping anything. Park the game on the deck-selection screen (Start Career! / Perks) first.
     */
    open fun startDeckStatReadTest() {
        MessageLog.i(TAG, "\n[TEST] Running read-only deck composition OCR diagnostic...")
        CareerLaunchNavigator(game.myContext).debugDeckStatRead(game.imageUtils)
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
                    trainee.fans = fans
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

                // Rotation backstop: confirm this career's trainee matches the preset the queue
                // loaded for it. This is the ONLY trainee check on the resume path (a resume
                // re-enters a career without going through Trainee Select), and it completes the
                // match-or-stop guarantee for every rotation career. Once per career, independent
                // of the deck-validation setting.
                if (!bRotationTraineeVerified) {
                    verifyRotationTrainee()
                    bRotationTraineeVerified = true
                }

                if (trainee.runningStyle != prevRunningStyle) {
                    // Reset this flag since our preferred running style has changed.
                    trainee.bHasSetRunningStyle = false
                }

                // First-pass deck validation: warn the user if the trainee's preferred
                // distance/style aptitude is below the configured floor. Runs once per
                // career so we don't spam the log on every aptitude refresh.
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
     * @return True if the retry was initiated (button clicked), false to close the dialog without retrying.
     */
    open fun shouldRetryRace(dialog: DialogInterface, args: Map<String, Any>): Boolean {
        if (racing.raceRetries > 0 && racing.retriesThisRace < racing.maxRetriesPerRace) {
            MessageLog.i(TAG, "[RACE] Retrying the race. Retries remaining: ${racing.raceRetries}")
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
            // Proceeding (or deferring to re-check) is NOT a reason to suppress racing — clear the gate so a
            // downstream race-entry abort that doesn't advance the day can still retry this turn. The old
            // unconditional `= true` left it set and silently blocked the legitimate same-turn retry.
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
            // Declined on the warning — suppress further extra-race attempts this turn.
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

        if (shouldRetryRace(dialog, args)) {
            // Retry was initiated by the hook.
        } else {
            MessageLog.w(TAG, "[WARN] handleDialogs:: No retries remaining but Try Again dialog detected. Closing dialog...")
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

        // Recover when current mood is strictly below the configured floor.
        // Default floor is GOOD which preserves historical behavior. A "Great" floor
        // is the strict guard for trap-event trainees like Agnes Tachyon (NHK Mile).
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

    /**
     * One-shot deck validation: log a high-visibility warning if the trainee's preferred
     * distance and running-style aptitudes are below [deckValidationMinAptitude].
     *
     * Called from the `umamusume_details` dialog handler the first time aptitudes are
     * successfully read. The check is informational only — it does not halt the run.
     *
     * Logs:
     *  - INFO when both aptitudes meet the floor (one line: "deck OK").
     *  - WARN with the specific shortfall (distance, style, or both) when below the floor.
     *
     * Subclasses MAY override to extend the check (e.g. add scenario-specific terrain
     * checks for Trackblazer's mixed turf/dirt schedule), but should call super() first.
     */
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

        // Prediction-visibility check. This replaces the old "Junior fan-farm impossible if
        // Sprint+Mile both <B" warning, which was wrong on the mechanism twice over: Junior year
        // has Medium/Long races too (a Medium=A trainee can clear the 3000-fan checkpoint off
        // Kyoto Junior Stakes and Hopeful Stakes alone), and the race finder is not aptitude-gated
        // but prediction-gated — the game computes prediction stars from stats AND aptitudes at
        // runtime. The real risk: a trainee with no strong distance aptitude draws single-star
        // predictions across its whole early pool. Those races are enterable via the fan-emergency
        // policy near goal deadlines, but placements and fan payouts will be weak, so the
        // checkpoint can still be missed. Runs for every trainee, not just decks below the floor.
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

    /**
     * Rotation backstop: confirm the trainee actually in this career matches the preset the queue
     * loaded for it. The Trainee Select handler already verifies the trainee it picks, but a resume
     * after a process death re-enters a career WITHOUT going through Trainee Select, so this is the
     * only check on that path — and it completes the match-or-stop guarantee for every rotation
     * career.
     *
     * The in-career name ([Trainee.readName]) is the bare character name (no "[Outfit]" prefix); the
     * rotation target is stored as "[Outfit] Name". Matching de-outfits both sides so the comparison
     * is character-level. Outfit-level discrimination is impossible from the in-career name and is
     * left to the phase-aware resume that loads the correct snapshot index.
     *
     * Conservative by design, because a STOP halts the whole unattended queue:
     *  - loaded preset's trainee matches the career     -> pass.
     *  - career CONFIDENTLY matches a DIFFERENT roster
     *    trainee than the one loaded                    -> RESYNC the rotation onto her entry and
     *    continue (an externally interrupted queue restarted from entry 0 while the game resumed
     *    the old in-flight career); STOP only when the resync itself fails (snapshot missing).
     *  - nothing matches well (unreadable / off-roster) -> WARN and continue; never halt on noise.
     *
     * When the same character appears at multiple rotation slots (different outfits), the resync
     * is refused and the queue stops: outfit-level discrimination is impossible from the bare
     * in-career name, and guessing the wrong slot would apply the wrong preset.
     */
    private fun verifyRotationTrainee() {
        if (!SettingsHelper.getBooleanSetting("runQueue", "enableTraineeRotation", false)) return

        val inCareer = trainee.name.trim()
        if (inCareer.isEmpty() || inCareer.equals("null", ignoreCase = true)) {
            MessageLog.w(TAG, "[ROTATION] Trainee verify skipped: in-career name unreadable. Continuing without the match check.")
            return
        }

        val target = SettingsHelper.getStringSetting("queueState", "currentTrainee", "").trim()
        if (target.isEmpty()) return // No rotation target recorded for this career; nothing to check against.

        // De-outfit so the bare in-career name compares character-to-character; also score the full
        // form in case a screen ever does include the outfit, and take the better of the two.
        fun matchScore(candidate: String): Double =
            maxOf(
                TraineeNameMatcher.score(inCareer, candidate),
                TraineeNameMatcher.score(inCareer, deOutfit(candidate)),
            )

        val targetScore = matchScore(target)
        if (targetScore >= rotationVerifyMatchThreshold) {
            MessageLog.i(TAG, "[ROTATION] Trainee verify OK: career '$inCareer' matches the loaded preset for '$target' (score=${"%.2f".format(targetScore)}).")
            // Identity matched - now verify the SETTINGS did too. Identity and config can diverge
            // (a resume that re-applied the wrong slot, a stale Home preset overwrite): the
            // fingerprint comparison catches what the name check structurally cannot.
            warnOnTraineeConfigDrift(StartModule.loadRotationConfig().inGameNames.indexOf(target), "career-start check")
            return
        }

        // The loaded preset doesn't clearly match. Act only if the career CONFIDENTLY matches some
        // OTHER roster trainee — the unambiguous "wrong preset loaded" case. A name that matches its
        // own (noisy) target best, or nothing well, is treated as OCR noise: warn, don't halt.
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
            // The career on screen IS a rotation trainee — the signature of an externally
            // interrupted queue restarted from entry 0 while the game resumed the old in-flight
            // career. Resync the queue onto her entry (swap in her snapshot, fast-forward the
            // cursor) and keep playing instead of killing the whole unattended queue. Refused when
            // the character occupies multiple rotation slots: the bare in-career name cannot tell
            // the outfits apart, and guessing the wrong slot would apply the wrong preset.
            val duplicateSlots = rotationNames.count { deOutfit(it) == deOutfit(matched) }
            if (duplicateSlots == 1 && StartModule.resyncRotationOntoCareer(game.myContext, bestIndex)) {
                // Rebuild the training config from the resynced DB - without this the career keeps the
                // wrong preset's construction-cached stat priorities and event overrides to the end.
                reloadTraineeConfig()
                MessageLog.w(
                    TAG,
                    "[ROTATION] Resynced onto interrupted career: this career is '$inCareer' (rotation entry #${bestIndex + 1} '$matched', " +
                        "score=${"%.2f".format(bestScore)}) but the queue had loaded the preset for '$target'. Applied the snapshot for " +
                        "'$matched', fast-forwarded the rotation cursor, and rebuilt the training config so the career now runs on her preset.",
                )
                // Prove the reload landed: the live fingerprint must now equal the resynced slot's.
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

    /** Strips a leading "[Outfit]" prefix so a bare in-career name matches an outfit-tagged target. */
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
        // Single screenshot shared across all four checks. Each check otherwise grabs its own
        // bitmap, costing 4 MediaProjection screenshots per Campaign.process() iteration.
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
            // The goal ribbon also stays visible on the Main screen and behind blocking info popups (e.g. the
            // "Umamusume Class" fan-class popup the game shows around debut, which only has a Close button). A
            // goal-ribbon match alone therefore does NOT prove we're on the Race Selection screen. Only treat this
            // as the race-prep flow if a Back button is actually present and gets clicked. Re-capture a fresh
            // screenshot (the start banner has had 2s to clear) so the check reflects the current screen. If there
            // is no Back button, return false so process() falls through to performMiscChecks for real recovery
            // instead of looping forever on a no-op back tap.
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
            // The lineup screen (roster of entrants + green "Race!" button) also belongs to the race
            // flow, but has no ButtonChangeRunningStyle. Resuming a career that was interrupted mid-race
            // lands here, which every other screen check misses - it killed the queue on 2026-07-11
            // when a Continue-Career resume dropped onto this screen and the loop counted it unknown to
            // the stop. handleStandaloneRace clicks Race and rides the race out. Checked AFTER the
            // strategy-screen match so the normal prep flow is unaffected.
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

    /**
     * Checks if the bot is on the career-end "Learn" skill purchase screen with the careerComplete
     * plan enabled.
     *
     * Covers starting (or restarting) the bot directly on that screen, where [checkEndScreen]
     * cannot match because the Complete Career button is not reliably visible from inside the
     * list. The branch decides via [bCareerEndSkillsHandled] whether to run the plan (first time)
     * or actively exit the screen (plan already ran but the bot is still here - a failed commit
     * or a wedged screen).
     */
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

    // Forced-infirmary tracking. A negative status that persists while the Infirmary button reads
    // disabled is either a misread of the button state or a condition the infirmary can't cure
    // (e.g. Super Creek's story-locked "Under the Weather"). One forced click per episode resolves
    // the misread case cheaply (a click on a genuinely disabled button is a no-op); the loud log
    // captures the incurable case for later tuning.
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
                if (ButtonInfirmary.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
                    game.wait(game.dialogWaitDelay)
                    ButtonOk.click(game.imageUtils, region = game.imageUtils.regionMiddle)
                    game.wait(game.dialogWaitDelay)

                    // Infirmary button click already succeeded above, which means the in-game
                    // heal has fired server-side. The follow-up event-header template match is a
                    // best-effort visual confirmation only — when it misses (template drift,
                    // animation timing, "Connecting" overlay), the heal still happened. Treat
                    // the button click as authoritative so a failed visual match doesn't make
                    // the bot believe injuries persist across turns.
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

    /**
     * Public accessor for the grade of the most recent race processed in this career.
     *
     * Exposes the protected [racing.lastRaceGrade] field so that base classes like [DialogHandler]
     * can make grade-aware decisions (e.g., the alarm clock carat policy) without using reflection
     * or relaxing the encapsulation of the [Racing] property.
     *
     * @return The [RaceGrade] of the last processed race, or null if no race has been processed yet
     *   in this career run.
     */
    fun getLastRaceGrade(): com.steve1316.uma_android_automation.types.RaceGrade? = racing.lastRaceGrade

    /**
     * Marks the current race as having had its alarm-clock retry option declined per
     * [Racing.bAlarmClockPolicySkippedThisRace]. Exposed so [DialogHandler]'s purchase_alarm_clock
     * branch can write the flag without breaking the protected encapsulation of [racing] — same
     * cross-class-access pattern as [getLastRaceGrade].
     *
     * Reset automatically when the race state is cleared (in `Racing` post-race cleanup).
     */
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
            // With an active dating schedule the chain belongs to the scheduler - an energy-recovery
            // recreation goes to the trainee instead of consuming a scheduled outing.
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
                // With an active dating schedule the chain belongs to the scheduler - a mood-recovery
                // recreation goes to the trainee instead of consuming a scheduled outing.
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
        // A back-out this turn (held final, no rows) does not advance the game, so retrying before the
        // turn changes would reopen the same dialog forever. The latch clears when the date advances.
        if (recreationAttemptFailedThisTurn) return false
        // Do an outing on a pinned turn, or - when catch-up is on - on any turn where a missed outing has left us behind schedule.
        val pinnedOrBehind =
            DatingSchedule.isPinnedRecreationTurn(date.day, recreationTurns, purePassionTurn) ||
                (enableRecreationCatchUp && DatingSchedule.isBehindSchedule(date.day, recreationTurns, recreationOutingsStarted))
        if (!pinnedOrBehind) return false
        // If only the final outing remains and this is not the Pure Passion turn, hold it: spend the turn on a normal action instead of opening the recreation.
        if (DatingSchedule.shouldHoldFinalOuting(recreationOutingsStarted, recreationTotalOutingsKnown, allowFinalOutingNow())) return false
        val bitmap = sourceBitmap ?: game.imageUtils.getSourceBitmap()
        // Mandatory career-goal races cannot be skipped, so they still outrank a recreation. Scheduled (in-game agenda) races
        // do not - the recreation overrides them. Upstream also blocks on IconGoalRibbon here; deliberately dropped: on this
        // fork the goal ribbon persists on the Main screen for any active objective (see cachedGoalRibbonDay), so keying on
        // it would dead-block the schedule on most turns. The race-day ribbon is the reliable race-day signal here.
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

    /** Whether the recreation schedule is actively driving decisions: enabled and not abandoned (the Pure Passion window has not passed with the chain
     * unfinished). Protected rather than upstream's private so Trackblazer's budget override can exempt scheduled outings. */
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
            // Check if all of the possible dates have been completed. Multiple tries: a
            // single-frame check against the popup's open animation can miss and send the flow
            // down the dead Event Progress pill below.
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
                    // Return false: no recreation date was actually consumed (we just confirmed it was already
                    // completed and cancelled out). The recoverEnergy caller falls through to ButtonRest, and the
                    // Trackblazer override no longer increments its recreationUsedCount budget on this no-op path.
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
                        // Read the in-game group-event progress (e.g. "3/4"). This is the authoritative chain position, so it holds correctly even after a bot restart or manual play.
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
                                // Back out rather than leave the dialog open to desync the next turn (the choose_recreation_partner dialog case is the backstop).
                                MessageLog.e(TAG, "[ERROR] handleRecreationDate:: Failed to find any date progress labels in the partner selection dialog. Backing out.")
                                cancelPartnerDialog()
                            }
                        }
                    }
                } else if (LabelEventProgress.click(game.imageUtils)) {
                    // Legacy support cards or situations where the dialog doesn't apply.
                    game.waitForLoading()
                    // A completed Pal row keeps its "Event Progress" pill but silently ignores
                    // taps, so verify the popup actually closed before declaring success (the
                    // complete-check can miss a frame, leaving this branch to click the dead pill,
                    // report success, and loop the campaign on the open popup).
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
        MessageLog.v(TAG, "[SKILLS] Beginning process to purchase skills...")
        return skillPlan.start(skillPlanName, trigger)
    }

    /**
     * The config-arm fingerprint of the career currently running, computed from the same snapshot and
     * the same helper the [CAREER_END] record uses, so a mid-career record joins the arm its career
     * will land in. Reuses [outcomeConfigFingerprint] rather than re-deriving the digest - two
     * fingerprint implementations would silently split arms the day one of them drifted.
     */
    internal fun currentConfigFingerprint(): String = outcomeConfigFingerprint(BuildConfig.VERSION_NAME, outcomeConfigSnapshot)

    /**
     * Records the careerComplete pass that never got to run because the Learn screen would not open
     * within its bounded attempts. [SkillPlan] cannot report this one: its session never started, so
     * the corpus would otherwise show a career whose final purchase silently vanished.
     *
     * Best-effort like every other skill-spend write - a telemetry failure must not change the
     * career-completion path this sits on.
     */
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
                    // The last per-turn OCR is the only points reading available: the Learn screen never
                    // opened, so there is no screen-authoritative total to quote.
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
     * @return True if the bot should break out of the main loop, false otherwise.
     */
    open fun handleRaceEventFallback(): Boolean {
        if (racing.detectedMandatoryRaceCheck) {
            MessageLog.v(TAG, "\n[END] Stopping bot due to detection of Mandatory Race.")
            game.notificationMessage = "Stopping bot due to detection of Mandatory Race."
            if (DiscordUtils.enableDiscordNotifications) {
                DiscordUtils.queue.add("```diff\n- ${MessageLog.getSystemTimeString()} Stopping bot due to detection of Mandatory Race.\n```")
            }
            return true
        }
        ButtonBack.click(game.imageUtils)
        ButtonCancel.click(game.imageUtils)
        ButtonClose.click(game.imageUtils)
        game.wait(1.0)
        training.handleTraining()
        return false
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
            // Dismiss the "Umamusume Class" fan-pyramid popup ONLY. The bot opens this itself (openFansDialog)
            // to read the fan count, but its title bar is blue and the title-gradient detector only knows the
            // green header, so handleDialogs can't recognize it — without this it traps the bot.
            //
            // CRITICAL: scope this strictly to the class popup via LabelUmamusumeClassFans. A blanket ButtonClose
            // here also closes the green-header Umamusume Details / Strategy dialogs the bot opens to read
            // aptitudes / set the running style. handleDialogs reads those on a later pass once the dialog is
            // stable+open; closing them here first leaves aptitudes unread (all "G") and spins the bot in an
            // open/close loop. Leave non-class dialogs alone so the real dialog handler can read them.
            MessageLog.i(TAG, "[MISC] Dismissed the Umamusume Class popup via its Close button.")
            game.wait(0.5)
            return true
        } else if (ButtonBack.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
            bMiscBackPressedThisTick = true
            consecutiveMiscBackPresses++
            if (consecutiveMiscBackPresses == 2) {
                // Two presses without progress: if an open notification shade is eating the
                // taps, clear it before the streak runs on. An open shade covers the top anchors,
                // absorbs back-presses, and can let a misc template match shade content and tap
                // the bot's own STOP BOT notification action.
                dismissNotificationShade("misc back-press streak")
            }
            if (consecutiveMiscBackPresses >= maxConsecutiveMiscBackPresses) {
                game.imageUtils.saveBitmap(filename = "misc_backpress_stuck", fullRes = true)
                throw InterruptedException(
                    "Bot pressed Back $consecutiveMiscBackPresses consecutive times without reaching a known screen - the press is not changing anything. Stopping. " +
                        "A screenshot was saved to the temp folder as misc_backpress_stuck.",
                )
            }
            MessageLog.i(TAG, "[MISC] Navigating back a screen since all the other misc checks have been completed. (consecutive back-presses: $consecutiveMiscBackPresses)")
            // ButtonBack.click does NOT auto-wait (Component.click goes through Components.tap which
            // calls the accessibility service directly, not Game.tap). A back-navigation is almost
            // always a pure UI transition with no server round-trip, so 0.5s is enough to let the
            // animation settle before the next iteration re-scans. game.wait() also includes a
            // waitForLoading() poll at the end, so any actual server call is still covered.
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

        // Re-verify we're still on the main screen. The hook above may have navigated
        // away (e.g. Trackblazer's shop check opens and tries to navigate the shop UI).
        // If the hook left us on a non-main screen - for example because shop entry
        // misfired - bail out instead of running updateDate() against the wrong UI.
        // This prevents the IllegalArgumentException("y must be >= 0") crash that
        // happens when date-OCR offset calculations land outside the source bitmap
        // on a shop or dialog screen. The main process loop will re-detect screen
        // state on the next iteration and converge.
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
                // Reset recreation-date check so a fresh icon detection runs every turn.
                // The flag is only meant to short-circuit re-checks within a single recovery sequence,
                // not to permanently disable recreation date detection for the rest of the run.
                recreationDateCompleted = false
                // The turn advanced, so a failed scheduled-outing attempt may be retried fresh.
                recreationAttemptFailedThisTurn = false

                // Reset scenario-specific daily flags.
                resetDailyFlags()

                // Perform parallel turn-start updates (stats, mood, energy, fans, etc.).
                performTurnStartUpdates(sourceBitmap)

                // Open this turn's Decision Report window now that trainee/date state is fresh. Any
                // record* calls during decideNextAction/executeAction (and skill buys in
                // performGlobalChecks, which run in the same turn before the action) append here, and
                // emit() flushes the block after the action executes.
                decisionTracer?.startTurn(
                    date = date,
                    trainee = trainee,
                    settings = DecisionTracer.SettingsSnapshot().add("Mood Floor", moodFloor),
                )

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

            // Cache for downstream consumers (decideNextAction, executeAction tail) so we
            // don't re-run the same template scans 2-3 more times per turn.
            cachedScheduledRaceDay = bIsScheduledRaceDayInitial
            cachedMandatoryRaceDay = bIsMandatoryRaceDayInitial
            // Separate goal-ribbon cache for the Trackblazer irregular-training gate only. Deliberately
            // NOT folded into cachedMandatoryRaceDay: the goal ribbon persists on the Main screen for an
            // active objective and must never reach decideNextAction's forced-RACE branch.
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

        // Scenario-specific main screen entry hook (e.g. for item usage).
        onMainScreenEntry()

        // Decision-making process.
        val action = decideNextAction()
        // Reuse the cached value populated above instead of re-running LabelScheduledRace.check -
        // nothing between performTurnStartUpdates() and here would have changed scheduled-race
        // status (no game-advancing actions occurred), and the third scan was redundant.
        val actionExecuted = executeAction(action, cachedScheduledRaceDay)
        // Flush the consolidated Decision Report after the action ran, so training/race selections
        // recorded inside executeAction land in the block. emit() is idempotent per turn.
        decisionTracer?.emit()
        return actionExecuted
    }

    /**
     * Recomputes the trainee's estimated overall rank from her current stats, aptitudes, owned skills, and unique-skill level. The estimate mirrors the UmaTools calculator
     * (an approximation of the game's unpublished formula), so it is labeled "Est." wherever shown; it lands in [Trainee.estimatedRank] for the turn log and the career ledger.
     */
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
        // Re-arm the high-water check once points fall back under the bar. Owned here because the flag
        // is this instance's mutable state; decideSkillCheck only reads the resulting value.
        if (trainee.skillPoints < skillPointsRequired) {
            bHasHandledSkillPointCheck = false
            skillPointCheckAttempts = 0
        }

        // Which skill check (if any) is due this turn, and why. Pure decision - navigation, the
        // Main-screen confirmation, the attempt counters and the flags all stay below.
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
            )

        // Now check if we need to handle skills before finals.
        if (skillCheck.action == SkillCheckAction.RUN_PLAN && skillCheck.trigger == SkillCheckTrigger.SCENARIO_FINALS) {
            ButtonSkills.click(game.imageUtils)
            game.wait(1.0)
            // Plan name stays null so start() resolves it from the screen exactly as before; only the
            // trigger is threaded through, for telemetry.
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

        // The high-water threshold has been reached: stop the bot, or run the skill plan if enabled.
        if (skillCheck.trigger == SkillCheckTrigger.HIGH_WATER) {
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

    /**
     * Decides the next action to take based on the current trainee and game state.
     *
     * @return The decided [MainScreenAction].
     */
    open fun decideNextAction(): MainScreenAction {
        // DecisionTracer: accumulate the alternatives ruled out as the priority cascade descends, and
        // record the chosen action plus its reason at the point it wins. Null-safe no-op in release.
        val tracerRejected = mutableListOf<DecisionTracer.RejectedAlternative>()

        fun choose(action: MainScreenAction, reason: String): MainScreenAction {
            decisionTracer?.recordActionChoice(action, reason, tracerRejected.toList())
            return action
        }

        // Use cached race-day flags populated in handleMainScreen rather than re-running the
        // same two template scans. The bitmap is captured lazily - only the late branches
        // (checkInjury, shouldRecoverMood) actually use it, so race/popup/maiden/etc. fast
        // paths can return before paying the MediaProjection screenshot cost (~50-150 ms).
        // Split mandatory from scheduled so a pinned recreation outing can sit between them:
        // mandatory career-goal races outrank everything, but a pinned recreation outranks a
        // scheduled (in-game agenda) race. shouldDoRecreationToday is a settings-only fast
        // no-op while the dating schedule is disabled (no screenshot cost).
        if (cachedMandatoryRaceDay) {
            return choose(MainScreenAction.RACE, "mandatory race day")
        }

        if (racing.encounteredRacingPopup) {
            // Consume the flag at decision time. If the resulting race succeeds the date advances and
            // the daily reset would clear it anyway; if the race attempt fails or finds no suitable
            // race, we don't want to spin on RACE decisions turn after turn just because a popup
            // appeared two turns ago. A fresh popup on a future turn will simply set the flag again.
            // Sits above DATE: an open popup must be consumed before any Recreation tap can land.
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

        // From here on the downstream branches need a screenshot - capture it now.
        val sourceBitmap = game.imageUtils.getSourceBitmap()

        // A mandatory career requirement (fan / trophy / goal-pts) can only be met by racing, so it
        // outranks the pre-summer prep below - the forced rest/mood turn would otherwise eat the very
        // turn the requirement needed. If no races turn out to be available, Racing resets the flags
        // and the turn falls back to training, so routing here is safe on a raceless day.
        val isRacingRequirementActive = racing.hasFanRequirement || racing.hasTrophyRequirement || racing.hasInsufficientGoalRacePtsRequirement
        if (isRacingRequirementActive) {
            MessageLog.i(TAG, "[INFO] Racing requirement is active. Bypassing health and mood checks.")
            return choose(MainScreenAction.RACE, "racing requirement active (fan/trophy/goal-pts)")
        }

        if (mustRestBeforeSummer && (date.year == DateYear.CLASSIC || date.year == DateYear.SENIOR) && date.month == DateMonth.JUNE && date.phase == DatePhase.LATE) {
            // An explicit mandatory plan entry or a due fan goal outranks summer prep. This forced
            // rest once consumed the exact turn of a mandatory planned race (Unicorn Stakes) while a
            // 5000-fan goal was due, and the career force-ended. bFanEmergencyActive carries the
            // previous turn's evaluation, which is current enough across a multi-turn emergency window.
            if (racing.hasMandatoryPlannedRaceToday() || racing.bFanEmergencyActive) {
                MessageLog.i(
                    TAG,
                    "[INFO] Skipping pre-summer prep: ${if (racing.bFanEmergencyActive) "a fan emergency is active" else "a mandatory planned race is scheduled for today"}.",
                )
            } else if (trainee.energy < 70) {
                MessageLog.i(TAG, "[INFO] Energy is low (${trainee.energy}% < 70%). Forcing rest during $date in preparation for Summer Training.")
                return choose(MainScreenAction.REST, "pre-summer prep: energy ${trainee.energy}% < 70%")
            } else if (trainee.mood < Mood.GREAT) {
                // If firstTrainingCheck is active, mood recovery will be refused. Do a
                // training first to clear the flag, then the next turn can recover mood.
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

        val extraRaceEligible = racing.checkEligibilityToStartExtraRacingProcess()
        // Record eligibility from the caller so it fires on every turn an extra race is considered,
        // across all scenarios. checkEligibility has many early returns (Trackblazer interval, fan
        // emergency, mandatory plan) that bypass its standard-racing block, so recording inside it
        // missed Trackblazer entirely. The rich decline reason stays in the [RACE] log one line up.
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
        // Force Wit Training if requested by the pre-summer logic.
        if (action == MainScreenAction.TRAIN && bForcedWitTraining) {
            MessageLog.i(TAG, "[INFO] Executing forced Wit training as requested by pre-summer logic.")
            training.handleTraining(StatName.WIT)
            bForcedWitTraining = false
            bHasCheckedDateThisTurn = false
            return true
        }

        when (action) {
            MainScreenAction.RACE -> {
                MessageLog.i(TAG, "[INFO] All checks are cleared for racing.")
                if (!handleRaceEvents(bIsScheduledRaceDay) && handleRaceEventFallback()) {
                    throw CampaignBreakpointException("Mandatory race detected. Stopping bot...")
                }
                bHasCheckedDateThisTurn = false
            }

            MainScreenAction.TRAIN -> {
                MessageLog.i(TAG, "[INFO] Decision made to train.")
                training.handleTraining()
                bHasCheckedDateThisTurn = false
            }

            MainScreenAction.REST -> {
                // Capture the bitmap only when REST/RECOVER_MOOD actually use it. RACE/TRAIN
                // (the vast majority of turns) do not need a fresh screenshot here.
                recoverEnergy(game.imageUtils.getSourceBitmap())
                bHasCheckedDateThisTurn = false
            }

            MainScreenAction.RECOVER_MOOD -> {
                // Target the configured floor, not a hardcoded GOOD: with moodFloor=GREAT the
                // decision gate keeps choosing RECOVER_MOOD while a GOOD-targeted recovery no-ops
                // (mood GOOD < GOOD is false), a livelock that burned the full runtime cap doing
                // nothing on every moodFloor=GREAT preset.
                val target = forcedTargetMood ?: moodFloor
                val recovered = performMoodRecovery(game.imageUtils.getSourceBitmap(), targetMood = target)
                // Always clear bHasCheckedDateThisTurn so the next main-screen pass re-runs updateDate/stats
                // and can pick a different action based on fresh state. Previously this only reset on success,
                // which meant a failed mood recovery (e.g. Recreation/RestAndRecreation buttons briefly missing
                // due to a mid-transition screenshot) could spin indefinitely: shouldRecoverMood keeps returning
                // true, decideNextAction keeps returning RECOVER_MOOD, and the same screenshot produced the same
                // failure.
                bHasCheckedDateThisTurn = false
                if (recovered) {
                    forcedTargetMood = null
                } else if (trainee.mood >= Mood.GOOD) {
                    // Recovery made no progress while only a high floor (GREAT) is unmet. The floor
                    // is a preference; training is progress. Train this turn rather than letting any
                    // future target/floor mismatch degrade back into the RECOVER_MOOD spin. Re-dispatch
                    // through executeAction so a scenario override's TRAIN handling stays in effect.
                    MessageLog.w(TAG, "[WARN] Mood recovery made no progress toward the $target floor. Training this turn instead.")
                    return executeAction(MainScreenAction.TRAIN, bIsScheduledRaceDay)
                }
            }

            MainScreenAction.DATE -> {
                MessageLog.i(TAG, "[INFO] Decision made to perform a scheduled recreation outing.")
                val started = handleRecreationDate(recoverMoodIfCompleted = false, allowFinalOuting = allowFinalOutingNow(), doDateRecreation = true)
                if (!started) {
                    // Backing out (held final, no selectable rows) does not advance the turn, so latch the
                    // failure - otherwise decideNextAction re-picks DATE and reopens the dialog forever.
                    recreationAttemptFailedThisTurn = true
                    MessageLog.i(TAG, "[RECREATION_DATE] Scheduled outing did not start. Deferring to the normal action flow for the rest of this turn.")
                }
                bHasCheckedDateThisTurn = false
            }

            MainScreenAction.NONE -> {
                return false
            }
        }
        return true
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Size of the process-lifetime message buffer at this Campaign's construction (= this career's
     * start). [writePerCareerLog] slices from here so the per-career .txt holds only THIS career's
     * lines. The buffer deliberately survives across queued careers (clearing it would blank the
     * on-screen RN log), which used to bleed the prior career's tail into every file.
     */
    private val careerLogStartIndex: Int = MessageLog.getMessageLogCopy().size

    /**
     * One structured outcome line per career run, logged when any run ends.
     *
     * Greppable as `[CAREER_END]` to build a per-preset completion ledger across runs. The game shows
     * the SAME end screen for a clean finish and an early force-end (a missed fan/goal deadline ends
     * the career on the spot), and there is no win/lose template to read - so [result] is COMPLETE for
     * both, and `turn` is the real discriminator: a full arc ends near the scenario's last turn (URA
     * finals = 75), a force-end ends early (a Junior fan-checkpoint death lands around turn 24). The
     * `outcome=` field interprets the run: INCOMPLETE for a non-COMPLETE result (a user stop or bot
     * failure - never a force-end), FORCE_END for a force-end confirmed at its source
     * ([careerForceEnded], currently only a lost mandatory race), and COMPLETED for the still-ambiguous
     * rest - a true win OR an unflagged early force-end, which `turn` must split. Every field comes
     * from memory or already-OCR'd state, so building the line triggers no extra capture.
     *
     * `spd/sta/pwr/grt/wit/fans` reflect the post-finale career-complete screen: after the finale the
     * bot re-opens the Umamusume Details dialog (`ButtonDetails`, confidence lowered to 0.65 so the
     * match lands) and re-reads stats + fans, so the line carries the true final values rather than the
     * pre-finale in-career snapshot it logged before that fix. If the Details re-read fails (logged
     * `[WARN] Could not find ButtonDetails`), the fields fall back to the last in-career OCR and will
     * understate the finale rewards (~+40 per stat, large fan injection) — trust `turn`/`result` then.
     */
    override fun careerEndLedgerLine(result: TaskResult): String {
        val resolvedName =
            trainee.name.ifEmpty {
                SettingsHelper.getStringSetting("misc", "currentProfileName").ifEmpty { "unknown" }
            }.replace(" ", "_")
        val scenarioToken = game.scenario.ifEmpty { "unknown" }.replace(" ", "_")
        val st = trainee.stats
        // Snapshot for the career-end SPARKS screen: the reroll gate reads the final stat values
        // after this Campaign instance is gone. Keyed by the statPrioritization display names.
        StartModule.lastCareerEndStats =
            mapOf(
                "Speed" to st.speed,
                "Stamina" to st.stamina,
                "Power" to st.power,
                "Guts" to st.guts,
                "Wit" to st.wit,
            )
        StartModule.lastCareerEndTrainee = resolvedName
        // Snapshot the same config-arm fingerprint + scenario the career-end record below carries, so
        // the SPARKS records the navigator appends later join to this exact career/arm directly (not
        // only positionally). Computed once here and reused for the record - never recomputed from
        // settings a queued run may have changed between this career's end and its spark recording.
        val careerEndFp = outcomeConfigFingerprint(BuildConfig.VERSION_NAME, outcomeConfigSnapshot)
        StartModule.lastCareerEndScenario = scenarioToken
        StartModule.lastCareerEndFp = careerEndFp
        val outcome = classifyCareerOutcome(result.code, careerForceEnded)
        val quality = classifyCareerQuality(outcome, finaleRaces, finaleRaces1st)

        // Stage 3 of the outcome-measurement plan: the same fields as the ledger line, appended
        // as one JSON record to the on-device corpus with the app version and the config-arm
        // fingerprint. The write swallows its own failures, so the ledger line below always logs.
        val record =
            JSONObject().apply {
                put("ts", System.currentTimeMillis())
                put("app", BuildConfig.VERSION_NAME)
                put("fp", careerEndFp)
                put("result", result.code.name.removePrefix("TASK_RESULT_"))
                put("outcome", outcome)
                forceEndReason?.let { put("forceEndReason", it) }
                put("trainee", resolvedName)
                put("scenario", scenarioToken)
                put("turn", date.day)
                put("fans", trainee.fans)
                put("spd", st.speed)
                put("sta", st.stamina)
                put("pwr", st.power)
                put("grt", st.guts)
                put("wit", st.wit)
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
                }
                put("cfg", JSONObject(outcomeConfigSnapshot as Map<*, *>))
            }
        OutcomeCorpus.append(game.myContext, record)

        return buildString {
            append("[CAREER_END] result=").append(result.code.name.removePrefix("TASK_RESULT_"))
            append(" outcome=").append(outcome)
            forceEndReason?.let { append(" forceEndReason=\"").append(it).append('"') }
            append(" trainee=").append(resolvedName)
            append(" scenario=").append(scenarioToken)
            append(" turn=").append(date.day)
            append(" fans=").append(trainee.fans)
            append(" spd=").append(st.speed)
            append(" sta=").append(st.stamina)
            append(" pwr=").append(st.power)
            append(" grt=").append(st.guts)
            append(" wit=").append(st.wit)
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
            }
        }
    }

    /**
     * The automation_library writes a per-career .txt log itself, but that silently stopped on a
     * long-lived queue session (2026-07-09: an 11h run produced zero .txt logs while the outcome
     * corpus kept appending). Write our own copy from the readable buffer so every career leaves a
     * triage log regardless of the library's state. Runs AFTER the [CAREER_END] line is logged
     * (Task.handleTaskEnd ordering) so the file contains its own ledger line, and slices from
     * [careerLogStartIndex] so a long app session does not bleed prior careers' tails into the file.
     * Best-effort: a logging failure must never abort the run.
     */
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
            // Defensive: if the buffer was ever trimmed below the start snapshot, write the full
            // copy (old behavior) rather than a wrong slice.
            val lines = if (careerLogStartIndex <= buffer.size) buffer.subList(careerLogStartIndex, buffer.size) else buffer
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH_mm_ss", java.util.Locale.US).format(java.util.Date())
            val logFile = java.io.File(logsDir, "${resolvedName}_$stamp.txt")
            logFile.writeText(lines.joinToString("\n"))
            // App-written files land u0_a75:u0_a75 on this emulator image, which locks the adb
            // shell out of triage pulls (observed 2026-07-10: cat/pull Permission denied while the
            // outcome corpus stayed readable). Best-effort world-read so logs stay pullable.
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
            // The emulator can wipe the Accessibility grant mid-run (gestures silently die while
            // screen reads keep working - every historical "stall" traced back to this). Detect
            // and self-heal BEFORE the dialog and main-screen ticks: both early-return, so a
            // gesture-death while a dialog was up previously looped unbounded (taps no-op, the
            // dialog never closes) all the way to the runtime cap.
            if (!game.ensureAccessibilityService()) {
                throw InterruptedException(
                    "The Accessibility Service was disabled mid-run and could not be restored automatically. " +
                        "Re-enable it in the Android settings or grant WRITE_SECURE_SETTINGS (see log).",
                )
            }

            // We always check for dialogs first.
            if (tryHandleAllDialogs()) {
                consecutiveUnknownScreenCount = 0
                consecutiveDialogTicks++
                // The string-only ensure above cannot see MuMu's enabled-but-dispatch-dead mode.
                // A long dialog streak is that mode's signature here: hard-rebind at the ladder
                // points, and stop cleanly rather than spin to the runtime cap.
                if (consecutiveDialogTicks == 13 || consecutiveDialogTicks == 19) {
                    MessageLog.w(TAG, "[WARN] process:: $consecutiveDialogTicks consecutive dialog ticks without progress - forcing an accessibility service rebind.")
                    game.forceRebindAccessibilityService()
                } else if (consecutiveDialogTicks >= 25) {
                    throw InterruptedException(
                        "Dialog handling made no progress for $consecutiveDialogTicks ticks - gestures are likely dead and could not be revived.",
                    )
                }
                return null
            }
            consecutiveDialogTicks = 0

            if (handleMainScreen()) {
                consecutiveUnknownScreenCount = 0
                careerScreenObservedThisTask = true
                return null
            }

            // Tracks whether this tick resolved to a known screen. The unknown-screen counter is
            // only reset below when something was actually handled, so a transient blip does not
            // accumulate toward the stuck-screen stop.
            var detectedKnownScreen = true
            bMiscBackPressedThisTick = false

            if (checkTrainingEventScreen()) {
                // If the bot is at the Training Event screen, that means there are selectable options for rewards.
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
                    // Open the career-end "Learn" skill screen, then hand off: the next tick lands on
                    // that screen and the checkCareerEndSkillListScreen branch below runs the plan once
                    // the screen is actually present. The old path bought inline here after a single
                    // fixed 1s wait and terminal-failed the whole career when the screen loaded slower
                    // (a slow career-end dropped 544 SP and ended the run with an exception). Only mark
                    // handled on a confirmed buy (done in that branch), so this stays retryable.
                    careerEndEntryAttempts++
                    if (careerEndEntryAttempts <= maxCareerEndEntryAttempts) {
                        MessageLog.i(
                            TAG,
                            "[INFO] Career end reached. Opening the Learn skill screen for the careerComplete plan (attempt $careerEndEntryAttempts/$maxCareerEndEntryAttempts)...",
                        )
                        game.wait(0.5)
                        ButtonCareerEndSkills.click(game.imageUtils)
                        game.wait(1.0)
                        // Let the next tick detect the Learn screen and buy. If the click did not
                        // navigate (still on the result screen), this branch fires again and retries.
                        return null
                    }
                    // Out of attempts: the Learn button never took us to the skill screen. Complete the
                    // career rather than hang; unspent skill points are a smaller loss than a wedged run.
                    MessageLog.e(
                        TAG,
                        "[ERROR] process:: Could not open the career-end skill screen after $maxCareerEndEntryAttempts attempts. Completing the career without the careerComplete plan (skill points may remain unspent).",
                    )
                    // The one skill-spend outcome SkillPlan cannot report: its session never began, so
                    // record the lost pass here - only now that every bounded attempt is spent, never
                    // on the earlier retryable attempts.
                    recordAbortedSkillEntry()
                    bCareerEndSkillsHandled = true
                }

                // Perform a final update of the fan count.
                // ButtonDetails carries a lowered match confidence (see Button.kt): on the career-end
                // screen it renders just under the default 0.8 threshold, which silently skipped this
                // whole post-finale fan+stat re-read every career and left [CAREER_END] on the stale
                // pre-finale values (~+40/stat short of the real result screen). A few retries also
                // cover a mid-render capture.
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

                    // toIntOrNull (not toInt): isNotEmpty() does not bound the digit count, so OCR
                    // noise (>10 digits) overflowed Int and threw NumberFormatException - which unwinds
                    // past Task.start()'s InterruptedException/IllegalStateException-only catch and ends
                    // the whole run (aborting a stopOnError queue) at career end. Keep the last value on a
                    // bad read instead of crashing.
                    val cleanedFans = fansText.replace(Regex("[^0-9]"), "")
                    cleanedFans.toIntOrNull()?.let { trainee.fans = it }
                        ?: MessageLog.w(TAG, "[WARN] process:: Could not detect final fan count for the end of the Career from OCR: $fansText")

                    // Now click the button to open the details dialog for aptitude and stat updates.
                    game.gestureUtils.tap(buttonLocation.x, buttonLocation.y, ButtonDetails.template.path)
                    game.wait(1.0)
                    ButtonDetails.click(game.imageUtils)
                    game.wait(1.0)
                } else {
                    MessageLog.w(TAG, "[WARN] process:: Could not find ButtonDetails to perform final updates for the end of the Career.")
                }

                handleDialogs()

                // Re-open the Details dialog to read the owned skills from its Skills tab - the first open was consumed by the standard dialog handler (final stats + aptitudes),
                // which closes the dialog on its way out. The owned skills and unique level feed the estimated rank below.
                // Gate on the re-click actually landing: if it misses (screen already moved on), the tab
                // tap, scroll swipes, and close-fallback tap would all fire blind on an unknown screen.
                if (buttonLocation != null && ButtonDetails.click(game.imageUtils)) {
                    game.wait(1.0)
                    val ownedSkills = SkillList(game, this).parseDetailsSkillsTab()
                    // The Skills tab always holds at least the unique skill, so an empty read is a failed
                    // read (tab never rendered, OCR blackout) - keep the purchase-tracked set instead of
                    // wiping it, or the final estimate silently drops every skill the career bought.
                    if (ownedSkills.skillNames.isNotEmpty()) {
                        trainee.ownedSkillNames.clear()
                        trainee.ownedSkillNames.addAll(ownedSkills.skillNames)
                        trainee.uniqueSkillLevel = ownedSkills.uniqueLevel
                    }
                    // Dismiss the dialog directly - it now shows the Skills tab, which the generic details handler must not process as a stats read. Same close idiom the
                    // between-run navigator uses for this card (wide Close template, else the card's fixed bottom-center Close position).
                    val closeBitmap = game.imageUtils.getSourceBitmap()
                    if (!ButtonCloseWide.click(game.imageUtils, sourceBitmap = closeBitmap)) {
                        game.gestureUtils.tap(closeBitmap.width * 0.5, closeBitmap.height * 0.86, "umamusume_details_close")
                    }
                    game.wait(1.0)
                }

                // Recompute the estimated rank from the final stats, aptitudes, and owned skills so the end-of-run log and the [CAREER_END] ledger reflect the completed career.
                updateEstimatedRank()

                // Print the final Trainee information.
                trainee.logInfo()

                // Reaching here means the careerComplete plan already committed (via the
                // checkCareerEndSkillListScreen branch on an earlier tick), was disabled, or was
                // skipped after exhausting the Learn-screen open attempts - all clean completions.
                return TaskResult.Success(
                    TaskResultCode.TASK_RESULT_COMPLETE,
                    "Bot has reached end of run. Stopping bot...",
                )
            } else if (checkCareerEndSkillListScreen()) {
                if (!bCareerEndSkillsHandled) {
                    // Started or restarted directly on the career-end "Learn" skill purchase screen.
                    // Buy per the careerComplete plan; once the plan confirms and exits the list,
                    // the End screen branch above performs the final bookkeeping on a later tick.
                    MessageLog.i(TAG, "[INFO] Bot is on the career-end skill purchase screen. Running the careerComplete skill plan...")
                    bCareerEndSkillsHandled = true
                    // Plan name stays null so start() resolves it from the screen exactly as before.
                    if (!handleSkillListScreen(trigger = SkillCheckTrigger.CAREER_COMPLETE)) {
                        MessageLog.w(TAG, "[WARN] process:: careerComplete skill plan failed on the career-end skill purchase screen.")
                    }
                } else {
                    // The plan already ran but the bot is STILL on the Learn screen - confirmAndExit
                    // reported success without actually leaving (e.g. a transient gone-read). This used
                    // to fall through to the misc back-press forever (10+ minute livelock). Actively
                    // exit, bounded. The plan's purchases are already committed, so Confirm here was
                    // WRONG: with nothing selected it is a no-op that never leaves, and because its
                    // click "succeeds" the Back fallback never fired - an infinite loop to the throw that
                    // wedged the career end and aborted the whole queue. cancelAndExit resets any stray
                    // selection, presses Back, and drains the "unused skill points - exit anyway?" dialog
                    // - the real way off this screen.
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
            } else {
                detectedKnownScreen = false
                consecutiveUnknownScreenCount++
                // Debug build or Debug Mode: capture genuinely-stuck unknown screens (skip short benign animations).
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
                consecutiveUnknownScreenCount = 0
                lobbyReentryAttempts = 0
                gameRestartAttemptedThisEpisode = false
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

    /**
     * Attempts to close the Android notification shade via the accessibility service.
     *
     * A pulled-down shade covers the top-region detection anchors and absorbs taps, so screen
     * detection goes blind and template matching runs against a contaminated frame — a misc
     * template can match shade content and tap the bot's own STOP BOT notification action, ending
     * the session mid-career. Dismissal is a free no-op when the shade is already closed. Requires
     * API 31+; older devices skip silently.
     *
     * @param reason Short context string for the log line.
     */
    protected fun dismissNotificationShade(reason: String) {
        if (Build.VERSION.SDK_INT >= 31) {
            val dispatched = game.gestureUtils.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            MessageLog.i(TAG, "[INFO] Dismissed the notification shade in case it was open ($reason, dispatched=$dispatched).")
            game.wait(0.5)
        }
    }

    /**
     * Recovers from a process() tick where no known screen was detected.
     *
     * The previous behavior was a single unconditional tap at (350, 450) every tick with no bound,
     * so any persistent unrecognized overlay wedged the bot forever — most notably an open dialog
     * whose title OCR returned empty (low-contrast banner), so [DialogUtils.getDialog] returned null,
     * the dialog was never closed, and no screen matched underneath it. Escalate instead:
     *
     *  1. If a dialog title banner is present (the gradient match still succeeds even when the title
     *     OCR fails), the bot is stuck on an unidentified dialog — close it.
     *  2. Otherwise nudge with the legacy tap to clear transient/intermediate screens.
     *  3. If still unrecovered after [maxUnknownScreenBeforeStop] consecutive ticks, stop with a
     *     diagnostic capture rather than loop forever.
     *
     * @param count The current [consecutiveUnknownScreenCount] for this stuck streak.
     */

    /**
     * Whether the bot is on a story / event cutscene that must be body-tapped through to reach its
     * choices. Detects the in-career "Skip" affordance (skip_off / skip_on template, then an OCR
     * fallback over the pill band).
     *
     * The catch: that green "Skip >>" affordance is NOT cutscene-exclusive - it also sits on the
     * main screen, the skill list, and race-day screens. Those each carry an interactive control a
     * genuine cutscene never shows (Training / Rest / Confirm / the race-day ribbon), so a leading
     * guard rules them out first. Without it the bot mistook a momentarily-unrecognized normal screen
     * for a cutscene and body-tapped it, wasting cycles and occasionally nudging the persistent Skip
     * toggle to a slower speed (the misfire was frequent in Unity Cup, which churns through the most
     * transient multi-screen states). A real cutscene has none of those controls, so it still fires.
     */
    private fun isEventCutsceneSkipPillVisible(): Boolean {
        val sourceBitmap = game.imageUtils.getSourceBitmap()

        // Not a cutscene if a normal-screen interactive control is present: this is a real screen
        // that merely also shows the ubiquitous "Skip >>" button, not a dialogue to tap through.
        if (ButtonTraining.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
            ButtonRest.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
            ButtonConfirm.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
            IconRaceDayRibbon.check(game.imageUtils, sourceBitmap = sourceBitmap)
        ) {
            return false
        }

        if (ButtonSkipOff.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
            ButtonSkipOn.check(game.imageUtils, sourceBitmap = sourceBitmap)
        ) {
            return true
        }
        return try {
            // Scan 22%-53% width, 94%-98% height - centered on the event-INTRO Skip pill.
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
                // Day-end event / result screens (support-card & scenario event dialogue, hint-level-up,
                // goal-result) render the green "Skip >>" button lower and further left than the intro
                // pill, so the band above missed it and the screen churned to the unknown-screen stop.
                // A wider bottom-left scan catches them; they advance with the same body-tap.
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

    private fun recoverFromUnknownScreen(count: Int) {
        if (count == 1) {
            // First unrecognized tick: clear the notification shade in case it is covering the
            // top-region anchors (free no-op when closed).
            dismissNotificationShade("unknown screen")
        }

        // Story / chain support-card events (e.g. Air Shakur's "Both High and Low") open with an
        // intro cutscene that must be tapped through before the choices render. During the intro
        // there is no event-choice horseshoe, so checkTrainingEventScreen() is false and every other
        // screen check misses too - the tick lands here. The bottom-left Skip pill (Skip Off / Skip
        // On) renders only during these cutscenes; use it as the signal to body-tap the dialogue
        // toward the choices rather than treating it as stuck. The misc skip handler only knows the
        // distinct `skip` template and misses this pill. Bounded by maxCutsceneAdvanceBeforeStop so a
        // truly frozen cutscene still stops, with defensive rebinds in case dispatch silently died.
        if (isEventCutsceneSkipPillVisible()) {
            if (count >= maxCutsceneAdvanceBeforeStop) {
                game.imageUtils.saveBitmap(filename = "event_cutscene_stuck", fullRes = true)
                throw InterruptedException(
                    "Bot stuck advancing an event cutscene for $count consecutive cycles. Stopping. " +
                        "A screenshot was saved to the temp folder as event_cutscene_stuck.",
                )
            }
            if (count in cutsceneRebindThresholds) {
                MessageLog.w(
                    TAG,
                    "[WARN] recoverFromUnknownScreen:: Event cutscene not advancing after $count taps - forcing an Accessibility Service rebind in case gesture dispatch died.",
                )
                game.forceRebindAccessibilityService()
            }
            MessageLog.i(TAG, "[MISC] Event cutscene intro detected (Skip pill present); tapping to advance the dialogue toward the choices (tap $count).")
            game.tap(540.0, 1300.0, taps = 1)
            return
        }

        // A mid-career bounce to the game's outer Home lobby - the 17:00 JST daily-reset reload, an
        // app resume, or a crash-to-title - is invisible to every in-career screen check, so without
        // this the loop spirals to the maxUnknownScreenBeforeStop stop and only recovers via the
        // queue's between-run path. That path treats the crash as a finished run: it advances the
        // rotation cursor onto the NEXT preset, writes a phantom CAREER_END, and rebuilds Training/
        // TrainingEvent against the wrong preset (both cache their config at construction). Detect the
        // lobby and re-enter THIS career in place through the navigator (HOME -> CAREER -> Continue ->
        // Resume), keeping the task alive so none of that happens. Gated at >=2 cycles so a one-frame
        // misdetect during a normal transition cannot trigger it, AND on careerScreenObservedThisTask
        // so a bot started at the lobby never launches a career on stale state; any failure falls
        // through to the standard ladder below.
        if (count >= 2 && careerScreenObservedThisTask && lobbyReentryAttempts < maxLobbyReentryAttempts) {
            val navigator = CareerLaunchNavigator(game.myContext)
            navigator.attachLiveGame(game)
            if (navigator.isOnHomeScreen()) {
                lobbyReentryAttempts++
                MessageLog.w(TAG, "[RECOVERY] Detected the game's Home lobby mid-career (likely a daily-reset bounce). Re-entering the in-progress career in place (attempt $lobbyReentryAttempts/$maxLobbyReentryAttempts)...")
                val result = navigator.navigate(reuseLastLaunchSetup = true)
                if (result.success) {
                    MessageLog.i(TAG, "[RECOVERY] Re-entered the career via the navigator; resuming the in-career loop.")
                    consecutiveUnknownScreenCount = 0
                    lobbyReentryAttempts = 0
                    return
                }
                MessageLog.w(TAG, "[RECOVERY] Lobby re-entry failed (${result.failureReason}); falling through to the standard recovery ladder.")
            }
        }

        // MuMu can leave the Accessibility Service "enabled" in secure settings while its gesture
        // dispatch silently dies, so the per-tick ensureAccessibilityService() string check passes
        // and every blind tap below no-ops - which can wedge a recognizable screen (e.g. a post-race
        // scenario event) to the stop cap. Once we have been stuck longer than a normal transition,
        // force a hard off->on rebind to revive dispatch; the next blind tap then lands and advances
        // the screen, resetting the counter. Falls through to the stop below if it cannot help (e.g.
        // WRITE_SECURE_SETTINGS missing), so there is no new dead-end.
        if (count in gestureRebindThresholds) {
            MessageLog.w(
                TAG,
                "[WARN] recoverFromUnknownScreen:: Stuck for $count cycles - forcing an Accessibility Service rebind in case gesture dispatch died silently.",
            )
            game.forceRebindAccessibilityService()
        }

        // Last resort before the stop: relaunch the whole game. The gesture rebinds above cover MuMu's
        // dead-dispatch mode; this covers a GAME-side soft-lock (an un-driveable screen that a rebind
        // cannot fix - e.g. the game wedged on a first-time race). Gated to a career actually in
        // progress (careerScreenObservedThisTask) so a bot parked at the lobby never relaunches, and
        // to one attempt per stuck episode so a restart that does not clear the wedge falls through to
        // the stop instead of looping. The career is server-saved and resumes via the lobby re-entry
        // path (Continue Career) on the next ticks.
        if (count == gameRestartThreshold && careerScreenObservedThisTask && !gameRestartAttemptedThisEpisode) {
            gameRestartAttemptedThisEpisode = true
            MessageLog.w(TAG, "[RECOVERY] Stuck for $count cycles and gesture rebinds did not help - relaunching the game as a last resort before stopping.")
            if (game.restartGame()) {
                // Give the relaunch a fresh window: the next ticks land on the game's title/lobby,
                // which the lobby re-entry branch above resumes into the interrupted career. The
                // re-entry budget resets too - the relaunched game is a fresh lobby, not the one any
                // earlier failed re-entries were fighting.
                consecutiveUnknownScreenCount = 0
                lobbyReentryAttempts = 0
                return
            }
            MessageLog.w(TAG, "[RECOVERY] Game relaunch could not be dispatched; falling through to the standard stop.")
        }

        if (DialogUtils.check(game.imageUtils)) {
            MessageLog.w(TAG, "[WARN] recoverFromUnknownScreen:: A dialog banner is present but could not be identified (tick $count). Closing it.")
            if (ButtonClose.click(game.imageUtils)) {
                game.wait(0.5)
                return
            }
            // The Trackblazer Shop "lineup has been refreshed" dialog has Cancel/Shop buttons (no Close or
            // OK) and its title-bar OCR is flaky, so getDialog frequently fails to name it and it lands
            // here unclosable - it killed the queue after 25 stuck cycles. ButtonShop is the dialog's
            // reliable green button and renders only on shop dialogs, so tapping it enters the shop and
            // the campaign's shop handling takes over on the next tick. Safe: it no-ops (returns false)
            // on any non-shop dialog, so it cannot mis-fire on other unidentified popups.
            if (ButtonShop.click(game.imageUtils)) {
                MessageLog.i(TAG, "[INFO] recoverFromUnknownScreen:: Entered the Shop via its button on an unidentified shop dialog.")
                game.wait(1.0)
                return
            }
            MessageLog.w(TAG, "[WARN] recoverFromUnknownScreen:: No Close or Shop button found on the unidentified dialog; nudging instead.")
        }

        if (count >= maxUnknownScreenBeforeStop) {
            game.imageUtils.saveBitmap(filename = "unknown_screen_stuck", fullRes = true)
            throw InterruptedException(
                "Bot stuck on an unrecognized screen for $count consecutive cycles. Stopping. " +
                    "A screenshot was saved to the temp folder as unknown_screen_stuck.",
            )
        }

        // Award/ceremony screens (the first-time trophy popup after a finals win, ending cards)
        // have no dialog banner and ignore the legacy nudge spot - a finals trophy sat through 25
        // nudges at (350, 450) and forced a stop. They do dismiss on a standard OK or a tap near
        // the bottom-center, so try those too.
        if (ButtonOk.click(game.imageUtils)) {
            MessageLog.i(TAG, "[INFO] recoverFromUnknownScreen:: Dismissed an unrecognized screen via its OK button.")
            game.wait(1.0)
            return
        }
        // First-win trophy popups use a Close button instead of OK (the URA Finals dirt-champion
        // trophy sat through OK attempts and both nudges, which land just above its button).
        if (ButtonClose.click(game.imageUtils)) {
            MessageLog.i(TAG, "[INFO] recoverFromUnknownScreen:: Dismissed an unrecognized screen via its Close button.")
            game.wait(1.0)
            return
        }
        // Post-turn result / event screens (GOAL COMPLETE!, race results, achievement & hint popups,
        // day-end event results) advance via a Next or Skip button, not OK/Close - without these the
        // loop blind-nudges them for several cycles before stumbling past. Tap the affordance directly
        // when present; each no-ops when absent, and the screen is already unknown so advancing is safe.
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
            // Legacy nudge to progress transient/intermediate screens.
            game.tap(350.0, 450.0, taps = 1)
        }
    }
}
