import { createContext, useState, useMemo, useCallback } from "react"
import { startTiming } from "../lib/performanceLogger"
import racesData from "../data/races.json"
import { skillPlanSettingsPages } from "../pages/SkillPlanSettings/config"
import { DEFAULT_ACCOUNT_TIER, DEFAULT_SKILL_SPEND_MODE, DEFAULT_SKILL_SPEND_OBJECTIVE } from "../lib/adaptiveSkillPolicy"

/**
 * Configuration for an individual skill plan (e.g. preFinals, careerComplete).
 */
interface SkillPlanSettingsConfig {
    /** Whether this skill plan is enabled. */
    enabled: boolean
    /** The spending strategy for this plan. */
    strategy: string
    /** Whether to buy inherited unique skills. */
    enableBuyInheritedUniqueSkills: boolean
    /** Whether to buy negative skills. */
    enableBuyNegativeSkills: boolean
    /** The serialized skill plan data. */
    plan: string
}

/**
 * The complete application settings interface.
 * Organized into category-specific sub-objects for general, racing, skills,
 * training events, misc, training, stat targets, OCR, and debug settings.
 */
export interface Settings {
    // General settings
    general: {
        scenario: string
        enablePopupCheck: boolean
        enableCraneGameAttempt: boolean
        enableStopBeforeFinals: boolean
        enableStopAtDate: boolean
        stopAtDates: string[]
        waitDelay: number
        dialogWaitDelay: number
        // Support-card dating schedule: perform a recreation outing on the pinned career turns,
        // holding the chain's final outing for the Pure Passion turn (timed friendship-training buff).
        enableDatingSchedule: boolean
        // Selected schedule preset key from DATING_SCHEDULE_PRESETS, or "custom" after a manual edit.
        datingSchedulePreset: string
        // 1-indexed career turns (1-72) pinned for regular recreation outings.
        recreationTurns: number[]
        // The single turn pinned for the final outing / Pure Passion activation; non-positive = unset.
        purePassionTurn: number
        // Total outings in the active card's chain (Team Sirius 7, Heirs to the Throne 5). The bot
        // overrides this live from the in-game "Group Event Progress X/Y" once the dialog is read.
        recreationTotalOutings: number
        // Make up a missed pinned outing (pre-empted by a race) on the next available turn.
        enableRecreationCatchUp: boolean
        // Internal: the applied preset's trainee in the in-game "[Outfit] Name" form the Trainee
        // Select preview shows, plus sibling-outfit exclusions ("\n"-joined). Written by the Home
        // preset apply (and kept in sync by the rotation apply on the Kotlin side); a single
        // (non-queue) run verifies the Trainee Select screen against these instead of trusting
        // the game's sticky preselection. Not user-facing controls.
        appliedPresetTrainee: string
        appliedPresetTraineeExcludes: string
        // Internal: monotonic launch-config revision, bumped on every preset apply. The Start
        // barrier reads this row back out of SQLite to prove the selected preset persisted
        // before launching (a stale read once launched the wrong trainee). Not a user control.
        settingsRevision: number
    }

    // Racing settings
    racing: {
        enableFarmingFans: boolean
        ignoreConsecutiveRaceWarning: boolean
        ignoreLowEnergyRacingBlock: boolean
        daysToRunExtraRaces: number
        disableRaceRetries: boolean
        enableFreeRaceRetry: boolean
        alarmClockPolicy: "Never" | "G1Only" | "G1AndFinale" | "Always"
        enableCompleteCareerOnFailure: boolean
        enableStopOnMandatoryRaces: boolean
        enableForceRacing: boolean
        enableUserInGameRaceAgenda: boolean
        limitRacesToInGameAgenda: boolean
        skipSummerTrainingForAgenda: boolean
        selectedUserAgenda: string
        customAgendaTitle: string
        enableRacingPlan: boolean
        enableMandatoryRacingPlan: boolean
        racingPlan: string
        racingPlanData: string
        minFansThreshold: number
        preferredTerrain: string
        preferredGrades: string[]
        preferredDistances: string[]
        lookAheadDays: number
        smartRacingCheckInterval: number
        juniorYearRaceStrategy: string
        originalRaceStrategy: string
        minimumQualityThreshold: number
        timeDecayFactor: number
        improvementThreshold: number
        // Internal: JSON snapshot of the racing-plan stance the last-applied preset set
        // (written by the Home preset apply, read by the bot at career start for drift
        // warnings). Not a user-facing control.
        appliedRacingSnapshot: string
    }

    // Skill Settings
    skills: {
        // How the mid-career high-water threshold is chosen. "manual" (default) uses skillPointCheck
        // exactly as configured - the long-standing behavior. "adaptive" derives it from accountTier.
        // A user-global choice, like the threshold itself: presets must never set it.
        skillSpendMode: "manual" | "adaptive"
        // Account strength for adaptive mode: "auto" | "new" | "developing" | "established" | "endgame".
        // Labels describe roster/support quality, not literal Team Rank. "auto" resolves to
        // "developing" in V1 (conservative middle); ignored entirely in manual mode.
        accountTier: string
        // Phase 2A: what the applied preset's career is trying to achieve -
        // "safe_completion" | "rank" | "sparks" | "race_reward". PRESET-owned, unlike mode/tier:
        // every preset apply stamps it (absent -> "rank", the V1-identical behavior), so it can
        // never leak from one preset to the next. Gates the adaptive-only dynamic triggers;
        // Manual mode ignores it.
        skillSpendObjective: string
        enableSkillPointCheck: boolean
        skillPointCheck: number
        preferredRunningStyle: string
        preferredTrackDistance: string
        preferredTrackSurface: string
        // When true, the purchaser buys only the ○ version of a skill and skips its ◎ upgrade, spreading the SP budget across more distinct skills.
        skipDoubleCircleUpgrades: boolean
        plans: Record<string, SkillPlanSettingsConfig>
    }

    // Training Event settings
    trainingEvent: {
        enablePrioritizeEnergyOptions: boolean
        enableAutomaticOCRRetry: boolean
        ocrConfidence: number
        enableHideOCRComparisonResults: boolean
        specialEventOverrides: Record<string, { selectedOption: string; requiresConfirmation: boolean }>
        characterEventOverrides: Record<string, number>
        supportEventOverrides: Record<string, number>
        scenarioEventOverrides: Record<string, number>
    }

    // Misc settings
    misc: {
        enableSettingsDisplay: boolean
        formattedSettingsString: string
        enableMessageIdDisplay: boolean
        currentProfileName: string
        messageLogFontSize: number
        overlayButtonSizeDP: number
    }

    // Training settings
    training: {
        trainingBlacklist: string[]
        statPrioritization: string[]
        maximumFailureChance: number
        manualStatCap: number
        disableTrainingOnMaxedStat: boolean
        focusOnSparkStatTarget: string[]
        enableRainbowTrainingBonus: boolean
        // Year 2+: multiply a rainbow-less training's score by up to 1.6x when it has near-max
        // (green/blue) friendship bars, anticipating the rainbow they are about to become.
        // Capped below the real 2.0x rainbow multiplier so anticipation never outranks one.
        enablePrioritizeNearMaxFriendship: boolean
        preferredDistanceOverride: string
        mustRestBeforeSummer: boolean
        enableRiskyTraining: boolean
        riskyTrainingMinStatGain: number
        riskyTrainingMaxFailureChance: number
        trainWitDuringFinale: boolean
        enablePrioritizeSkillHints: boolean
        // When enabled (Year 2+), the bot reads each training's level (1-5) via OCR and amplifies the
        // score for trainings whose primary stat sits in the top 3 of the active priority list, so it
        // sticks with stats the user has invested in. OCR is skipped during Pre-Debut, Junior, and
        // Summer where the boost wouldn't differentiate.
        enableTrainingLevelWeighting: boolean
        enableTrainingAnalysisValidation: boolean
        enableYoloStatDetection: boolean
        // Bot recovers mood when current mood drops below this level. "Normal" | "Good" | "Great".
        // "Good" matches base Campaign.shouldRecoverMood (`mood < Mood.GOOD`). "Great" is the strict
        // guard for single-option mood-trap events (Agnes Tachyon's "Report: A Clear Gaze" diverts the
        // objective to NHK Mile if mood is Normal or worse on the trigger date).
        moodFloor: string
        // Pre-career deck validation: on the first aptitude read of a run, log a warning if the
        // trainee's preferred-distance and preferred-style aptitudes are below the floor.
        // Informational only; the run continues either way.
        enableDeckValidation: boolean
        // Aptitude letter floor for deck validation. "B" matches the in-game soft
        // requirement for race-bonus uplift; "A" is the strict meta-deck floor.
        deckValidationMinAptitude: string
        // Deck concentration check ([DECK] advisory): at career start, read the support-deck
        // composition off the deck screen and warn if the build's core stat type has too few cards
        // (a spread deck generates few rainbows -> weak training). Informational only. URA Finale uses
        // the floor; Unity Cup uses floor-1; Trackblazer is exempt (decks are intentionally spread and
        // run on Race Bonus, checked separately).
        enableDeckConcentrationCheck: boolean
        // Minimum same-type support cards wanted on the build's core stat before warning. 4 matches
        // the meta "4-5 of the build type" shell for URA/Unity rainbow stacking.
        deckConcentrationCardFloor: number
    }

    // Training Stat Target settings
    trainingStatTarget: {
        // Sprint
        trainingSprintStatTarget_speedStatTarget: number
        trainingSprintStatTarget_staminaStatTarget: number
        trainingSprintStatTarget_powerStatTarget: number
        trainingSprintStatTarget_gutsStatTarget: number
        trainingSprintStatTarget_witStatTarget: number

        // Mile
        trainingMileStatTarget_speedStatTarget: number
        trainingMileStatTarget_staminaStatTarget: number
        trainingMileStatTarget_powerStatTarget: number
        trainingMileStatTarget_gutsStatTarget: number
        trainingMileStatTarget_witStatTarget: number

        // Medium
        trainingMediumStatTarget_speedStatTarget: number
        trainingMediumStatTarget_staminaStatTarget: number
        trainingMediumStatTarget_powerStatTarget: number
        trainingMediumStatTarget_gutsStatTarget: number
        trainingMediumStatTarget_witStatTarget: number

        // Long
        trainingLongStatTarget_speedStatTarget: number
        trainingLongStatTarget_staminaStatTarget: number
        trainingLongStatTarget_powerStatTarget: number
        trainingLongStatTarget_gutsStatTarget: number
        trainingLongStatTarget_witStatTarget: number
    }

    // Debug settings
    debug: {
        enableDebugMode: boolean
        ocrThreshold: number
        templateMatchConfidence: number
        templateMatchCustomScale: number
        debugMode_startTemplateMatchingTest: boolean
        debugMode_startSingleTrainingOCRTest: boolean
        debugMode_startComprehensiveTrainingOCRTest: boolean
        debugMode_startRaceListDetectionTest: boolean
        debugMode_startMainScreenUpdateTest: boolean
        debugMode_startSkillListBuyTest: boolean
        debugMode_startScrollBarDetectionTest: boolean
        debugMode_startTrackblazerRaceSelectionTest: boolean
        debugMode_startTrackblazerInventorySyncTest: boolean
        debugMode_startTrackblazerBuyItemsTest: boolean
        debugMode_startTraineeSelectTest: boolean
        debugMode_startDeckStatReadTest: boolean
        debugMode_startRainbowDetectionTest: boolean
        enableScreenRecording: boolean
        recordingBitRate: number
        recordingFrameRate: number
        recordingResolutionScale: number
        enableRemoteLogViewer: boolean
        remoteLogViewerPort: number
    }

    // Discord settings
    discord: {
        enableDiscordNotifications: boolean
        discordToken: string
        discordUserID: string
    }

    // Run Queue settings
    runQueue: {
        enableRunQueue: boolean
        totalRuns: number
        delayBetweenRunsSeconds: number
        maxRuntimePerRunMinutes: number
        stopOnError: boolean
        reuseLastLaunchSetup: boolean
        autoFillSupports: boolean
        // Smart Borrow: when the queue fills the empty friend slot, scroll down through the Borrow
        // Card list and borrow the best card found from the bot's curated priority list. The scan
        // is bounded (a very deep followed pool can run past it), so it takes the best card it
        // reaches, not necessarily the best card that exists. A pick the game refuses (e.g. a
        // duplicate of a card already in the deck) falls back to the default pick on the retry.
        // Off = the default pick only (the strong friend card when spotted, otherwise the top row).
        enableSmartBorrow: boolean
        // Advanced/no UI yet: a card or character name treated as priority zero ahead of the
        // curated Smart Borrow list. Empty = list order only.
        preferredBorrowName: string
        // When the game asks "Restore TP?" between queued runs, refill TP and continue instead
        // of ending the queue. Ladder: Toughness 30, then Star Fruit, then Carats as the last
        // resort - every rung Max-fills to the cap.
        enableTpRestoreWithItems: boolean
        // Tick "Event Boost (TP Usage x2)" on the Final Confirmation screen before each career.
        // Doubles event rewards (and the TP cost) - only worth it while a TP event is live; turn
        // it off once the event ends. The Max TP restore above covers the doubled cost.
        enableEventBoost: boolean
        // On the career-end SPARKS screen, spend 30 TP to reroll the set once when it prices
        // below a fresh redraw (SparkRerollPolicy.kt): a 2/3-star stat spark is always kept; a
        // 1-star stat spark is redrawn unless every stat finished under 600 (a redraw can't roll
        // a 3-star there) or the set holds 3-star aptitude/skill sparks worth protecting. After
        // the spend, both sets are read in full on the game's Spark Selection pager and the
        // better one is kept (SparkChooser.kt); an unverifiable selection stops safely with the
        // original set recoverable by hand. OFF by default - it spends TP; supervise it until
        // the selection has been watched live.
        enableSparkReroll: boolean
        // Tick "Include Guests" on the Confirm Auto-Select legacy dialog so Auto-Select may borrow a
        // guest (rental) parent. Borrowing a guest costs in-game monies. OFF by default -> Auto-Select
        // uses only OWNED umas (free), which suits farming your own spark parents. New players with weak
        // owned umas may prefer it ON to inherit stronger borrowed 3-star parents.
        enableLegacyIncludeGuests: boolean
        // Trainee rotation: instead of repeating one trainee, cycle through a list, switching every
        // switchEveryNRuns careers, each playing under HER own preset. Default off -> the validated
        // same-trainee queue is byte-for-byte unchanged. When on, the frontend precomputes each
        // trainee's full settings snapshot up front (separate runQueueRotation category rows) so the
        // bot never reopens its own UI mid-session; the Kotlin queue swaps the active settings + selects
        // the trainee in-game at each switch boundary, verifying the on-screen name matches or stopping.
        enableTraineeRotation: boolean
        switchEveryNRuns: number
        // Ordered cycle. inGameName is the full "[Outfit] Name" the in-game trainee-select preview shows
        // (what the navigator OCR-matches); presetKey is the characterPresets key ("Name" or
        // "Name (Outfit)"); scenario picks which of that trainee's per-scenario presets to apply.
        // excludeOutfits: sibling-outfit names (the "(Outfit)" suffixes of this character's other
        // presets) the in-game Trainee Select matcher must skip. A bare base-name target is
        // outfit-insensitive, so without this the bot can pick the wrong owned outfit. Derived on
        // preset pick; empty/absent for outfit-specific entries and pre-existing configs.
        traineeRotation: { inGameName: string; presetKey: string; scenario: string; excludeOutfits?: string[] }[]
    }

    // Scenario specific overrides
    scenarioOverrides: {
        trackblazerConsecutiveRacesLimit: number
        trackblazerEnergyThreshold: number
        trackblazerShopCheckGrades: string[]
        trackblazerMinStatGainForCharm: number
        trackblazerLowMainStatGainItemFloor: number
        trackblazerSkipEmpoweringMegaphoneBelowGain: number
        trackblazerSkipMotivatingMegaphoneBelowGain: number
        trackblazerSkipCoachingMegaphoneBelowGain: number
        trackblazerMaxRetriesPerRace: number
        trackblazerWhistleForcesTraining: boolean
        trackblazerRetryRacesBeforeFinalGrades: string[]
        trackblazerEnableIrregularTraining: boolean
        trackblazerIrregularTrainingMinStatGain: number
        trackblazerExcludedItems: string[]
        trackblazerShopCheckFrequency: number
        trackblazerPreferredDistances: string[]
        trackblazerPreferredSurfaces: string[]
    }
}

// Set the default settings.
//
// Defaults are tuned for a zero-config Trackblazer run: pick a Character, hit Start,
// and the rest is handled. Each change from the upstream baseline has a short inline
// note on what it does. Users running other scenarios (URA Finale, Unity Cup) can
// override any of these via the Settings UI - these are defaults, not locks.
export const defaultSettings: Settings = {
    general: {
        // Trackblazer pre-selected so the Home screen is ready on first launch.
        // Existing installs keep their last-used scenario (SQLite takes precedence over defaults).
        scenario: "Trackblazer",
        enablePopupCheck: false,
        enableCraneGameAttempt: false,
        enableStopBeforeFinals: false,
        enableStopAtDate: false,
        stopAtDates: ["Senior January Early"],
        waitDelay: 0.5,
        dialogWaitDelay: 0.5,
        enableDatingSchedule: false,
        datingSchedulePreset: "siriusSenior",
        recreationTurns: [29, 35, 43, 47, 52, 55, 58],
        purePassionTurn: -1,
        recreationTotalOutings: 7,
        enableRecreationCatchUp: true,
        appliedPresetTrainee: "",
        appliedPresetTraineeExcludes: "",
        settingsRevision: 0,
    },
    racing: {
        enableFarmingFans: false,
        ignoreConsecutiveRaceWarning: false,
        // Trackblazer guide: "In MANT, you'll play with an empty energy bar most of
        // the time - it's totally normal." The low-energy racing block (Trackblazer.kt
        // shouldAllowConsecutiveRace) is overly conservative for this scenario, so
        // bypass it by default. Non-Trackblazer scenarios aren't affected - the block
        // only exists in the Trackblazer override.
        ignoreLowEnergyRacingBlock: true,
        daysToRunExtraRaces: 5,
        disableRaceRetries: false,
        enableFreeRaceRetry: false,
        // Policy for when the bot runs out of free Alarm Clocks (5 per career) and the game
        // offers a paid retry for 10 carats. Four-option policy:
        //   - "Never"        -> always cancel the popup, never spend carats.
        //   - "G1Only"       -> spend carats only if the failed race was G1.
        //   - "G1AndFinale"  -> spend carats for G1 races OR Twinkle Star Climax finale races
        //                       (turns 73-75 in Trackblazer, tagged as RaceGrade.FINALE).
        //   - "Always"       -> always spend the 10 carats and retry.
        // Defaults to "Never" so the bot never spends premium currency without explicit opt-in.
        alarmClockPolicy: "Never",
        enableCompleteCareerOnFailure: false,
        enableStopOnMandatoryRaces: false,
        enableForceRacing: false,
        enableUserInGameRaceAgenda: false,
        limitRacesToInGameAgenda: true,
        skipSummerTrainingForAgenda: false,
        selectedUserAgenda: "Agenda 1",
        customAgendaTitle: "",
        enableRacingPlan: false,
        enableMandatoryRacingPlan: false,
        racingPlan: JSON.stringify(
            Object.values(racesData).map((race, index) => ({
                raceName: race.name,
                date: race.date,
                priority: index,
            }))
        ),
        racingPlanData: JSON.stringify(racesData),
        minFansThreshold: 0,
        preferredTerrain: "Any",
        preferredGrades: ["G1", "G2", "G3"],
        preferredDistances: ["Short", "Mile", "Medium", "Long"],
        lookAheadDays: 10,
        smartRacingCheckInterval: 2,
        juniorYearRaceStrategy: "Default",
        originalRaceStrategy: "Default",
        minimumQualityThreshold: 50.0,
        timeDecayFactor: 0.7,
        improvementThreshold: 50.0,
        appliedRacingSnapshot: "",
    },
    skills: {
        // Out-of-box default matching the baseline every character preset applies: mid-run buying at a
        // 350 SP threshold (buys skills in waves once points accumulate) plus preFinals + careerComplete
        // as safety nets. This is a global default the user can override in Skill Settings, and that
        // override now survives preset and rotation switches (see handlePresetChange /
        // buildRotationSnapshotRows) rather than being silently reset to a preset's value. Strategy
        // optimize_skills across all three plans filters by the trainee's aptitudes and sorts by
        // community-tier ranking from skills.json. enableBuyInheritedUniqueSkills: true buys inherited
        // uniques when affordable (almost always worth it); enableBuyNegativeSkills stays false so the
        // bot never buys debuffs.
        // Manual by default: adaptive threshold selection is strictly opt-in, and these defaults
        // reproduce the pre-adaptive behavior exactly. accountTier is inert while mode is manual.
        // The constants live in lib/adaptiveSkillPolicy so the Jest suite pins the actual defaults.
        skillSpendMode: DEFAULT_SKILL_SPEND_MODE,
        accountTier: DEFAULT_ACCOUNT_TIER,
        skillSpendObjective: DEFAULT_SKILL_SPEND_OBJECTIVE,
        enableSkillPointCheck: true,
        skillPointCheck: 350,
        preferredRunningStyle: "inherit",
        preferredTrackDistance: "inherit",
        preferredTrackSurface: "no_preference",
        skipDoubleCircleUpgrades: false,
        plans: Object.keys(skillPlanSettingsPages).reduce(
            (acc, curr) => {
                acc[curr] = {
                    enabled: true,
                    strategy: "optimize_skills",
                    enableBuyInheritedUniqueSkills: true,
                    enableBuyNegativeSkills: false,
                    plan: "",
                }
                return acc
            },
            {} as Record<string, SkillPlanSettingsConfig>
        ),
    },
    trainingEvent: {
        // Prefer energy rewards in training events. Energy is scenario-critical in Trackblazer
        // ("empty energy bar is normal"), and prioritizing energy rewards (scored 100x in
        // TrainingEvent.kt:558) keeps item spend lower. Every character preset ships this true.
        enablePrioritizeEnergyOptions: true,
        enableAutomaticOCRRetry: true,
        ocrConfidence: 90,
        enableHideOCRComparisonResults: true,
        specialEventOverrides: {
            "New Year's Resolutions": {
                selectedOption: "Option 2: Energy +20",
                requiresConfirmation: false,
            },
            "New Year's Shrine Visit": {
                selectedOption: "Option 1: Energy +30",
                requiresConfirmation: false,
            },
            "Victory!": {
                selectedOption: "Option 2: Energy -5/-20 and random stat gain",
                requiresConfirmation: false,
            },
            "Solid Showing": {
                // Match every character preset's pick: Option 1 has a deterministic Energy -15 outcome,
                // whereas Option 2's -5/-20 random branch can swing into a 20-energy hit. Aligns the
                // no-preset default with what users get the moment they apply any character preset.
                selectedOption: "Option 1: Energy -15 and random stat gain",
                requiresConfirmation: false,
            },
            Defeat: {
                selectedOption: "Option 1: Energy -25 and random stat gain",
                requiresConfirmation: false,
            },
            "Get Well Soon!": {
                selectedOption: "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                requiresConfirmation: false,
            },
            "Don't Overdo It!": {
                // Match every character preset's pick: Option 1 actually recovers Energy +10, while Option 2
                // is a -3 mood hit with no recovery. Aligns the no-preset default with the consensus pick.
                selectedOption: "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                requiresConfirmation: false,
            },
            "Extra Training": {
                selectedOption: "Option 2: Energy +5",
                requiresConfirmation: false,
            },
            "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                selectedOption: "Option 5: Energy +10",
                requiresConfirmation: true,
            },
            "Etsuko's Exhaustive Coverage": {
                selectedOption: "Option 2: Energy Down / Gain skill points",
                requiresConfirmation: false,
            },
            "A Team at Last": {
                selectedOption: "Default",
                requiresConfirmation: false,
            },
        },
        characterEventOverrides: {},
        // Support card event picks for the Auto-Fill default deck. Decision rules:
        // priority stats (Speed > Wit/Stamina/Power > Guts) beat low-tier skill
        // hints; gold-tier skills beat stats; energy-positive options preferred when
        // energy is tight; "Event chain ended" branches always avoided; "Can start
        // dating" branches preferred when safe.
        supportEventOverrides: {
            // Satono Diamond (Special Dreamers!)
            "Satono Diamond|I Love New Things!": 1, // Stamina +20 > Guts +10 (energy -10 negligible in TB)
            "Satono Diamond|I Love Complicated Things!": 0, // Concrete stats > Hesitant Front Runners (situational debuff)
            "Satono Diamond|(❯❯)\nDiamond Fixation": 0, // Reliable Wit +15 > random Mood-1/Energy+25 gamble
            "Satono Diamond|(❯❯❯)\nOnly for You": 0, // Stamina +40 (huge) + Iron Will hint > Guts +5 + Iron Will
            // Vodka (Wild Rider)
            "Vodka|(❯)\nSlacking Off at Sundown": 0, // Reliable Max Energy +4 + Power +10 > random Speed/hint
            "Vodka|The Coolest Line": 1, // Power +5 + SP +15 (more flexible) > pure Power +10
            "Vodka|Enemies on Main Street": 0, // Nimble Navigator GOLD HINT (per scenarioEventOverrides priority list)
            // Air Shakur (Mag!c Number)
            "Air Shakur|//Verification Required": 0, // Energy +10 preserved (Trackblazer runs empty)
            "Air Shakur|//Absolute Desire": 1, // Max Energy +4 (permanent) > Pace Strategy hint (low tier)
            "Air Shakur|(❯)\nBoth High and Low": 0, // Speed +10/Power +10 > Eager hint
            // Sakura Bakushin O (Eat Fast! Yum Fast!)
            "Sakura Bakushin O|(❯)\nA Bakushin Greeting!": 0, // Speed +30 > SP +30 (~3x value)
            "Sakura Bakushin O|Genius Efficiency!": 0, // Pure Speed +15 > Speed +5 / Power +10 (Speed is #1 priority)
            "Sakura Bakushin O|Enough to Break into a Dash!": 1, // Speed +10 / Power +5 > Gap Closer hint (debuff prevention, situational)
            "Sakura Bakushin O|(❯)\nUma Kids Footrace Meet": 0, // Groundwork hint (gold FR accel) - Sakura is Front Runner per preset
            "Sakura Bakushin O|(❯❯)\nChasing My Sister": 0, // Avoid "Event chain ended" in Opt 2
            // Smart Falcon (Almost... an Umadol?!)
            "Smart Falcon|Chants Are the Life of a Concert ☆": 1, // Wit +15 (single high-priority stat) > Stamina +5 / Guts +10
            "Smart Falcon|If I'm Cute, Come to My Show! ☆": 0, // Power +10 + Final Push hint > Energy +10 / Wit +5
            "Smart Falcon|(❯)\nAlways on Stage ☆": 0, // Avoid "Event chain ended" in Opt 2
            "Smart Falcon|(❯)\nWhat Is an Umadol?": 1, // Energy +10 + Power +5 > Leader's Pride hint
            // Riko Kashimoto (Planned Perfection) - friend slot, energy/mood Pal card
            "Riko Kashimoto|(❯❯❯)\nPicture Their Joy": 0, // Energy +24 / Mood +1 / Stamina +12 / Guts +6 > SP +37 / Mood +1 (energy is liquid gold in TB)
            "Riko Kashimoto|The Kashimoto Art of Tidying Up": 0, // Stamina +18 (broadly useful) > Guts +18 (only End Closer chars)
            "Riko Kashimoto|Unexpected Side\nDating starts": 1, // Safe path: Mood +1 / Guts +18 / Can start dating (avoids random Event-chain-ended branch)
        },
        // Scenario-wide event picks for Trackblazer. Default framework: +6/+6 stats
        // (~12 stat pts, ~60-80 SP-equivalent) beats a +1 skill hint unless the skill
        // is build-essential, expensive, gold, or unobtainable elsewhere.
        //
        // Unconditional Option 2 (skill hint wins): only "Leave It to the Great Detective!"
        // (Nimble Navigator - best last-spurt lane-change skill, community staple).
        //
        // Conditional Option 2 picks - handled per-character via characterEventOverrides
        // in characterPresets.ts (applied based on running style / distance):
        //   #5  A Grandkid Get-Together     -> Pace Chasers
        //   #7  The Inescapable Ardan       -> Front Runners
        //   #12 The Exciting Fruit Fest     -> Front Runners
        //   #15 Vega and Spica              -> Front Runners
        //   #17 Expressing My Feelings...   -> Mile runners (Shifting Gears upgrades to gold)
        //   #18 Worldwide Windy             -> End Closers
        scenarioEventOverrides: {
            "Trackblazer|Ordinary? Or Full Throttle?!": 0,
            "Trackblazer|All on the Line": 0,
            "Trackblazer|Pondering over Perennials": 0,
            "Trackblazer|Living Alone": 0,
            "Trackblazer|A Grandkid Get-Together": 0,
            "Trackblazer|Agnes Digital Explodes": 0,
            "Trackblazer|The Inescapable Ardan": 0,
            "Trackblazer|Tamamo's Struggle at the Arcade": 0,
            "Trackblazer|The Strongest, Mightiest Cinderella!": 0,
            "Trackblazer|A Trip to the Victory Club!": 0,
            "Trackblazer|Golshi Games: Who Is the Werewolf?!": 0,
            "Trackblazer|The Exciting Fruit Fest": 0,
            "Trackblazer|Taiki and Pearl's Language Exchange": 0,
            "Trackblazer|Brian Gets Jealous?": 0,
            "Trackblazer|Vega and Spica": 0,
            "Trackblazer|The Little Marvelous Match Girl": 0,
            "Trackblazer|Expressing My Feelings to Maruzen": 0,
            "Trackblazer|Worldwide Windy": 0,
            "Trackblazer|Keeping It Cute!": 0,
            "Trackblazer|Mission: Big Surprise!": 0,
            "Trackblazer|What's Wrong with Golshi?!": 0,
            "Trackblazer|The Perfect Combo": 0,
            "Trackblazer|Idle Committee Banter": 0,
            "Trackblazer|A Family (?) Shopping Trip!": 0,
            "Trackblazer|An Unusual Addition": 0,
            "Trackblazer|Under the Same Umbrella": 0,
            "Trackblazer|Hungry for Affection": 0,
            "Trackblazer|Fear the Fearless": 0,
            "Trackblazer|Leave It to the Great Detective!": 1,
            "Trackblazer|Gullible Socialites": 1,
            "Trackblazer|I Wanna Join the Discussion!": 0,
            // URA Finale - 3 events. Stamina > Guts universally for the 3-race finale
            // gauntlet; Speed is #1 at every distance; Best Foot Forward Opt 2 is the
            // safe (no-energy-cost) compound-reward path with gold Breath of Fresh Air
            // (solid stamina heal).
            "URA Finale|Exhilarating! What a Scoop!": 0,
            "URA Finale|A Trainer's Knowledge": 1,
            "URA Finale|Best Foot Forward!": 1,
        },
    },
    misc: {
        enableSettingsDisplay: false,
        formattedSettingsString: "",
        enableMessageIdDisplay: false,
        currentProfileName: "",
        messageLogFontSize: 8,
        overlayButtonSizeDP: 40,
    },
    training: {
        trainingBlacklist: [],
        // Wit-first is the meta tiebreaker: Wit training restores energy and is the only stat
        // where a "training" turn is also a near-zero-cost stamina recovery. When two trainings
        // tie on score, picking Wit first keeps the engine running. Speed/Power/Stamina follow
        // for build prioritization on non-tied turns.
        statPrioritization: ["Wit", "Speed", "Power", "Stamina", "Guts"],
        maximumFailureChance: 20,
        // Floor for the per-stat cap and OCR sanity ceiling. Since the July 2026 patch the caps
        // are per-scenario (URA 1400; UC 1300/Wit 1800; TB 1200/Sta 1900/Wit 1500 — see
        // Training.getScenarioStatCap); Kotlin uses maxOf(scenarioCap, manualStatCap) for the
        // maxed-stat check and rejection ceiling, so 1200 keeps scenario caps authoritative while
        // letting presets raise the floor for cap-raising-item builds.
        manualStatCap: 1200,
        disableTrainingOnMaxedStat: true,
        focusOnSparkStatTarget: ["Speed", "Stamina", "Power"],
        // Rainbow training matters most during summer camp. Code at Training.kt:730-740
        // applies a 2.0x multiplier to rainbow training scoring when on vs 1.5x when off.
        // The extra decisiveness matters during the 4 high-value camp turns where stat
        // gains compound with Megaphones + Ankle Weights. Only applies Classic year
        // onward (Junior has no rainbows).
        enableRainbowTrainingBonus: true,
        // Anticipatory rainbow multiplier (Year 2+): favor trainings whose friendship bars are
        // about to turn rainbow-capable, up to 1.6x. Ported from upstream with its default ON.
        enablePrioritizeNearMaxFriendship: true,
        preferredDistanceOverride: "Auto",
        // Force a rest the turn before summer camp begins so the trainee enters camp at full
        // energy and gets the camp's high-value rainbow turns at peak return. Without this the
        // bot can burn the camp on a depleted trainee and leave a lot of stat gain on the table.
        mustRestBeforeSummer: true,
        // Risky training: take a high-failure-chance training when the stat reward is large enough.
        // Min gain 30 + max fail 25% defines a sweet spot where the EV is positive after the
        // expected-failure penalty. Pairs with `enableRainbowTrainingBonus: true` to chase
        // big rainbow turns even when fail % is uncomfortable.
        enableRiskyTraining: true,
        riskyTrainingMinStatGain: 30,
        riskyTrainingMaxFailureChance: 25,
        trainWitDuringFinale: false,
        // Prioritize training facilities that offer skill hints. Training.kt:656 adds a weight
        // bonus to facilities with hint icons when this is true, helping the bot pick the
        // training that also teaches a hinted skill (skill-point efficient across any scenario).
        // Every character preset in the codebase already ships with this true.
        enablePrioritizeSkillHints: true,
        // Default true: improves Year 2+ scoring across every trainee/scenario with no downside when
        // OCR fails (multiplier falls back to 1.0).
        enableTrainingLevelWeighting: true,
        enableTrainingAnalysisValidation: false,
        enableYoloStatDetection: false,
        // Default Good matches base Campaign.shouldRecoverMood (`mood < Mood.GOOD`).
        // Override to "Great" only for trainees with single-option mood-trap events.
        moodFloor: "Good",
        // Pre-career deck validation is on by default — purely informational, no
        // behavior change beyond a one-time log line per career.
        enableDeckValidation: true,
        // "B" matches the in-game soft floor for race-bonus uplift. Tighten to "A"
        // for meta-deck runs where any sub-A aptitude indicates a build mistake.
        deckValidationMinAptitude: "B",
        // Off by default until the deck-screen count read is live-calibrated; enable once the
        // debugMode_startDeckStatReadTest diagnostic confirms the counts read correctly.
        enableDeckConcentrationCheck: false,
        // 4 = the meta "4-5 of the build type" shell for URA/Unity. Unity Cup is checked at floor-1.
        deckConcentrationCardFloor: 4,
    },
    trainingStatTarget: {
        trainingSprintStatTarget_speedStatTarget: 1200,
        trainingSprintStatTarget_staminaStatTarget: 450,
        trainingSprintStatTarget_powerStatTarget: 900,
        trainingSprintStatTarget_gutsStatTarget: 500,
        trainingSprintStatTarget_witStatTarget: 1200,
        trainingMileStatTarget_speedStatTarget: 1200,
        trainingMileStatTarget_staminaStatTarget: 650,
        trainingMileStatTarget_powerStatTarget: 1000,
        trainingMileStatTarget_gutsStatTarget: 400,
        trainingMileStatTarget_witStatTarget: 800,
        trainingMediumStatTarget_speedStatTarget: 1200,
        trainingMediumStatTarget_staminaStatTarget: 800,
        trainingMediumStatTarget_powerStatTarget: 900,
        trainingMediumStatTarget_gutsStatTarget: 400,
        trainingMediumStatTarget_witStatTarget: 600,
        trainingLongStatTarget_speedStatTarget: 1200,
        trainingLongStatTarget_staminaStatTarget: 1100,
        trainingLongStatTarget_powerStatTarget: 1000,
        trainingLongStatTarget_gutsStatTarget: 500,
        trainingLongStatTarget_witStatTarget: 600,
    },
    debug: {
        enableDebugMode: false,
        ocrThreshold: 230,
        templateMatchConfidence: 0.8,
        templateMatchCustomScale: 1.0,
        debugMode_startTemplateMatchingTest: false,
        debugMode_startSingleTrainingOCRTest: false,
        debugMode_startComprehensiveTrainingOCRTest: false,
        debugMode_startRaceListDetectionTest: false,
        debugMode_startMainScreenUpdateTest: false,
        debugMode_startSkillListBuyTest: false,
        debugMode_startScrollBarDetectionTest: false,
        debugMode_startTrackblazerRaceSelectionTest: false,
        debugMode_startTrackblazerInventorySyncTest: false,
        debugMode_startTrackblazerBuyItemsTest: false,
        debugMode_startTraineeSelectTest: false,
        debugMode_startDeckStatReadTest: false,
        debugMode_startRainbowDetectionTest: false,
        enableScreenRecording: false,
        recordingBitRate: 6,
        recordingFrameRate: 30,
        recordingResolutionScale: 1.0,
        enableRemoteLogViewer: false,
        remoteLogViewerPort: 9000,
    },
    discord: {
        enableDiscordNotifications: false,
        discordToken: "",
        discordUserID: "",
    },
    runQueue: {
        enableRunQueue: true,
        totalRuns: 5,
        delayBetweenRunsSeconds: 15,
        maxRuntimePerRunMinutes: 180,
        stopOnError: false,
        reuseLastLaunchSetup: true,
        autoFillSupports: true,
        enableSmartBorrow: true,
        preferredBorrowName: "",
        enableTpRestoreWithItems: false,
        enableEventBoost: false,
        enableSparkReroll: false,
        enableLegacyIncludeGuests: false,
        enableTraineeRotation: false,
        switchEveryNRuns: 3,
        traineeRotation: [],
    },
    scenarioOverrides: {
        // Trackblazer guide: "After 3 consecutive races, you could gamble on a fourth,
        // but the risk is high" - the -30 stat penalty can apply. Code at
        // Trackblazer.kt:543 checks `consecutiveRaceCount < (limit + 1)`, so limit=2
        // permits counts 0/1/2 (three races) and aborts at 4+. Previous default of 5
        // silently allowed six consecutive races, well past the penalty threshold.
        trackblazerConsecutiveRacesLimit: 2,
        trackblazerEnergyThreshold: 40,
        trackblazerShopCheckGrades: ["G1", "G2", "G3"],
        // Lower threshold (25 vs prior 30) burns Good-Luck Charm on more trainings. The charm
        // converts a risky training into a guaranteed pick, so any time the projected gain is
        // 25+ it's worth the consumable. Steve1316's settings ship with 25 and it's the meta.
        trackblazerMinStatGainForCharm: 25,
        // When mood is BAD or AWFUL, refuse to use Reset Whistle / Good-Luck Charm / Megaphone
        // if main-stat gain is below this floor. Prevents wasting items on structurally low-return
        // turns where the mood multiplier caps the stat gain. Default 15 matches upstream.
        trackblazerLowMainStatGainItemFloor: 15,
        // Per-tier minimum selected-training main-stat gain before each megaphone is spent (mood-independent,
        // stacks on the floor above). 0 = always allowed. Raise Empowering/Motivating to reserve the strong
        // tiers for high-gain turns like Classic/Senior summer camp; leave Coaching low for an opening-burst style.
        trackblazerSkipEmpoweringMegaphoneBelowGain: 0,
        trackblazerSkipMotivatingMegaphoneBelowGain: 0,
        trackblazerSkipCoachingMegaphoneBelowGain: 0,
        trackblazerMaxRetriesPerRace: 1,
        trackblazerWhistleForcesTraining: true,
        trackblazerRetryRacesBeforeFinalGrades: ["G1", "G2", "G3"],
        trackblazerEnableIrregularTraining: false,
        trackblazerIrregularTrainingMinStatGain: 30,
        // Reserve top-tier energy items (Energy Drink MAX/MAX EX) for the Twinkle Star Climax
        // races on days 73-75, where +50 energy from a single drink can rescue a critical
        // training pass. Yummy Cat Food is excluded for the same reason — its mood-restore +
        // energy combination is a Finale-tier item that's wasted earlier in the run.
        // Coaching Megaphone is the trainer guide's "trap" item — its 5-turn skill-point bonus
        // doesn't pay back in Trackblazer's compressed schedule and burns a shop slot the bot
        // would otherwise spend on a stat scroll or hint book that converts directly to gains.
        trackblazerExcludedItems: ["Energy Drink MAX", "Energy Drink MAX EX", "Yummy Cat Food", "Coaching Megaphone"],
        // Trackblazer guide's "Order Mantra" - Check Training, Check Shop, Go Race - is per-turn.
        // Frequency 1 means the bot checks the shop after every race (not every 3 races),
        // matching the guide's constant-shop-interaction tempo. Every character preset already
        // ships with 1; this aligns the non-preset default with that tempo.
        trackblazerShopCheckFrequency: 1,
        // Empty-by-default filters for distance/surface preferences. Bot treats empty arrays as
        // "no filter applied" - all distances/surfaces are eligible. Character presets override
        // these per-trainee where a focused build wants to lock to e.g. ["Sprint","Mile"] only.
        trackblazerPreferredDistances: [],
        trackblazerPreferredSurfaces: [],
    },
}

/**
 * Context value interface for the BotState provider.
 * Exposes application-wide state including readiness, settings, and app metadata.
 */
export interface BotStateProviderProps {
    /** Whether the bot/app is ready (initialized and settings loaded). */
    readyStatus: boolean
    /** Setter for the ready status. */
    setReadyStatus: (readyStatus: boolean) => void
    /** The default settings used for reset and comparison. */
    defaultSettings: Settings
    /** The current application settings. */
    settings: Settings
    /** Setter for the application settings. */
    setSettings: (settings: Settings | ((prev: Settings) => Settings)) => void
    /** The application name. */
    appName: string
    /** Setter for the application name. */
    setAppName: (appName: string) => void
    /** The application version string. */
    appVersion: string
    /** Setter for the application version. */
    setAppVersion: (appVersion: string) => void
}

export const BotStateContext = createContext<BotStateProviderProps>({} as BotStateProviderProps)

/**
 * Provider component for the BotState context.
 * Manages application-wide state including readiness, settings, and metadata.
 * Settings updates are wrapped with performance timing.
 * @param children The child components to render within the provider.
 * @returns The bot state context provider.
 */
export const BotStateProvider = ({ children }: any): React.ReactElement => {
    const [readyStatus, setReadyStatus] = useState<boolean>(false)
    const [appName, setAppName] = useState<string>("")
    const [appVersion, setAppVersion] = useState<string>("")

    // Create a deep copy of default settings to avoid reference issues.
    const [settings, setSettings] = useState<Settings>(() => JSON.parse(JSON.stringify(defaultSettings)))

    /**
     * Wrapped setSettings with performance logging.
     * @param update The update to apply to the settings.
     */
    const setSettingsWithLogging = useCallback((update: Settings | ((prev: Settings) => Settings)) => {
        const endTiming = startTiming("bot_state_set_settings", "state")

        try {
            if (typeof update === "function") {
                setSettings((prev) => {
                    const newSettings = update(prev)
                    endTiming({ status: "success" })
                    return newSettings
                })
            } else {
                setSettings(update)
                endTiming({ status: "success" })
            }
        } catch (error) {
            endTiming({ status: "error", error: error instanceof Error ? error.message : String(error) })
            throw error
        }
    }, [])

    // Memoize the provider value to prevent cascading re-renders.
    const providerValues = useMemo<BotStateProviderProps>(
        () => ({
            readyStatus,
            setReadyStatus,
            defaultSettings,
            settings,
            setSettings: setSettingsWithLogging,
            appName,
            setAppName,
            appVersion,
            setAppVersion,
        }),
        [readyStatus, settings, appName, appVersion, setSettingsWithLogging]
    )

    return <BotStateContext.Provider value={providerValues}>{children}</BotStateContext.Provider>
}
