import { createContext, useState, useMemo, useCallback } from "react"
import { startTiming } from "../lib/performanceLogger"
import racesData from "../data/races.json"
import { skillPlanSettingsPages } from "../pages/SkillPlanSettings/config"

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
    }

    // Skill Settings
    skills: {
        enableSkillPointCheck: boolean
        skillPointCheck: number
        preferredRunningStyle: string
        preferredTrackDistance: string
        preferredTrackSurface: string
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
        preferredDistanceOverride: string
        mustRestBeforeSummer: boolean
        enableRiskyTraining: boolean
        riskyTrainingMinStatGain: number
        riskyTrainingMaxFailureChance: number
        trainWitDuringFinale: boolean
        enablePrioritizeSkillHints: boolean
        enableTrainingAnalysisValidation: boolean
        enableYoloStatDetection: boolean
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
    }

    // Scenario specific overrides
    scenarioOverrides: {
        trackblazerConsecutiveRacesLimit: number
        trackblazerEnergyThreshold: number
        trackblazerShopCheckGrades: string[]
        trackblazerMinStatGainForCharm: number
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
    },
    skills: {
        // Out-of-box defaults matching the safe baseline every character preset applies: mid-run
        // buying with a conservative 1200 SP threshold (triggers only in late Senior year, when hints
        // have accumulated and skills are cheaper) plus preFinals + careerComplete as safety nets.
        // Strategy optimize_skills across all three plans filters by the trainee's aptitudes and sorts
        // by community-tier ranking from skills.json. enableBuyInheritedUniqueSkills: true buys
        // inherited uniques when affordable (almost always worth it); enableBuyNegativeSkills stays
        // false so the bot never buys debuffs.
        enableSkillPointCheck: true,
        skillPointCheck: 1200,
        preferredRunningStyle: "inherit",
        preferredTrackDistance: "inherit",
        preferredTrackSurface: "no_preference",
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
                selectedOption: "Option 2: Energy -5 and random stat gain",
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
        // OCR sanity ceiling per stat. The Global EN hard stat cap is 1200 unless raised
        // by Trackblazer shop items (Speed Shoes, etc.). CustomImageUtils.kt uses this to
        // reject OCR misreads above the ceiling. 1200 is the safe fleet-wide value;
        // per-character overrides (e.g. 1300 for cap-raising-item builds) can be applied
        // via character presets.
        manualStatCap: 1200,
        disableTrainingOnMaxedStat: true,
        focusOnSparkStatTarget: ["Speed", "Stamina", "Power"],
        // Rainbow training matters most during summer camp. Code at Training.kt:730-740
        // applies a 2.0x multiplier to rainbow training scoring when on vs 1.5x when off.
        // The extra decisiveness matters during the 4 high-value camp turns where stat
        // gains compound with Megaphones + Ankle Weights. Only applies Classic year
        // onward (Junior has no rainbows).
        enableRainbowTrainingBonus: true,
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
        enableTrainingAnalysisValidation: false,
        enableYoloStatDetection: false,
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
        autoFillSupports: false,
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
        trackblazerMaxRetriesPerRace: 1,
        trackblazerWhistleForcesTraining: true,
        trackblazerRetryRacesBeforeFinalGrades: ["G1", "G2", "G3"],
        trackblazerEnableIrregularTraining: false,
        trackblazerIrregularTrainingMinStatGain: 30,
        // Reserve top-tier energy items (Energy Drink MAX/MAX EX) for the Twinkle Star Climax
        // races on days 73-75, where +50 energy from a single drink can rescue a critical
        // training pass. Yummy Cat Food is excluded for the same reason — its mood-restore +
        // energy combination is a Finale-tier item that's wasted earlier in the run.
        trackblazerExcludedItems: ["Energy Drink MAX", "Energy Drink MAX EX", "Yummy Cat Food"],
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
