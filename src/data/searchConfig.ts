/**
 * Static search registry containing all searchable settings items.
 * This is used to pre-populate the search index at app initialization
 * without rendering any components, avoiding the UI freeze caused by
 * the HeadlessRenderer approach.
 *
 * To add a new searchable item, add an entry here with the same `id`
 * as the `searchId` prop on the component. The `page` must match the
 * Stack.Screen name / SearchPageProvider `page` value.
 */

import { SearchOption } from "../context/SearchRegistryContext"

/** All searchable items across all settings pages. */
const searchConfig: SearchOption[] = [
    // ============================================================
    // Settings (SettingsMain)
    // ============================================================
    {
        id: "settings-popup-check",
        title: "Stop on Unexpected Popups",
        description: "Stops the bot when an unexpected popup with a Cancel button is detected (e.g. lack of fans or trophies). You will need to dismiss the popup and restart the bot manually.",
        page: "SettingsMain",
    },
    {
        id: "settings-stop-before-finals",
        title: "Stop before Finals",
        description: "Stops the bot on turn 72 so you can purchase skills before the final races.",
        page: "SettingsMain",
    },
    {
        id: "settings-crane-game-attempt",
        title: "Enable Crane Game Attempt",
        description: "When enabled, the bot will attempt to complete the crane game. By default, the bot will stop when it is detected.",
        page: "SettingsMain",
    },
    {
        id: "settings-dating-schedule",
        title: "Enable Support Card Dating Schedule",
        description:
            "Performs a recreation outing on the selected preset's pinned career turns to advance a Group support card's outing chain, holding the final outing for the Pure Passion turn where the preset times it.",
        page: "SettingsMain",
    },
    {
        id: "settings-dating-schedule-preset",
        title: "Dating Schedule Preset",
        description: "Pinned recreation turns for the equipped Group support card (Team Sirius, Heirs to the Throne).",
        page: "SettingsMain",
    },
    {
        id: "settings-dating-catch-up",
        title: "Catch Up Missed Outings",
        description: "If a pinned turn gets pre-empted (e.g. by a race), makes up the outing on the next available turn.",
        page: "SettingsMain",
    },
    {
        id: "settings-enable-settings-display",
        title: "Enable Settings Display in Message Log",
        description: "Shows current bot configuration settings at the top of the message log.",
        page: "SettingsMain",
    },
    {
        id: "settings-enable-message-id-display",
        title: "Enable Message ID Display",
        description: "Shows message IDs in the message log to help with debugging.",
        page: "SettingsMain",
    },
    {
        id: "settings-wait-delay",
        title: "Wait Delay",
        description: "Sets the delay between actions and imaging operations. Lowering this will make the bot run much faster.",
        page: "SettingsMain",
    },
    {
        id: "settings-overlay-button-size",
        title: "Overlay Button Size",
        description: "Sets the size of the floating overlay button in density-independent pixels (dp). Higher values make the button easier to tap.",
        page: "SettingsMain",
    },
    {
        id: "settings-management-title",
        title: "Settings Management",
        description: "Import and export settings from JSON file or access the app's data directory.",
        page: "SettingsMain",
    },

    // ============================================================
    // Training Settings
    // ============================================================
    {
        id: "training-settings-profile-selector",
        title: "Profile Selector",
        description: "Profiles constitute only the Training settings and stat targets.",
        page: "TrainingSettings",
    },
    {
        id: "training-blacklist",
        title: "Blacklist",
        description: "Select which stats to exclude from training. These stats will be skipped during training sessions.",
        page: "TrainingSettings",
    },
    {
        id: "training-prioritization",
        title: "Prioritization",
        description: "Select the priority order of the stats. The stats will be trained in the order they are selected. If none are selected, then the default order will be used.",
        page: "TrainingSettings",
    },
    {
        id: "disable-training-on-maxed-stats",
        title: "Disable Training on Maxed Stats",
        description: "When enabled, training will be skipped for stats that have reached their maximum value.",
        page: "TrainingSettings",
    },
    {
        id: "manual-stat-cap",
        title: "Manual Stat Cap",
        description:
            "Floor for the per-stat cap and OCR sanity ceiling. The bot uses the higher of this value and the scenario's own cap (URA 1400; Unity Cup 1300, Wit 1800; Trackblazer 1200, Stamina 1900, Wit 1500) to decide when a stat is maxed and to reject impossible stat reads.",
        page: "TrainingSettings",
        parentId: "disable-training-on-maxed-stats",
    },
    {
        id: "maximum-failure-chance",
        title: "Set Maximum Failure Chance",
        description: "Set the maximum acceptable failure chance for training sessions. Training with higher failure rates will be avoided.",
        page: "TrainingSettings",
    },
    {
        id: "enable-riskier-training",
        title: "Enable Riskier Training",
        description: "When enabled, trainings with high main stat gains will use a separate, higher maximum failure chance threshold.",
        page: "TrainingSettings",
    },
    {
        id: "risky-training-min-stat-gain",
        title: "Minimum Main Stat Gain Threshold",
        description: "When a training's main stat gain meets or exceeds this value, it will be considered for risky training.",
        page: "TrainingSettings",
        parentId: "enable-riskier-training",
    },
    {
        id: "risky-training-max-failure-chance",
        title: "Risky Training Maximum Failure Chance",
        description: "Set the maximum acceptable failure chance for risky training sessions with high main stat gains.",
        page: "TrainingSettings",
        parentId: "enable-riskier-training",
    },
    {
        id: "focus-on-sparks",
        title: "Focus on Sparks",
        description: "Select which stats should receive priority to get to at least 600 to get the best chance to receive 3* sparks.",
        page: "TrainingSettings",
    },
    {
        id: "enable-prioritize-skill-hints",
        title: "Prioritize Skill Hints",
        description:
            "When enabled, the bot prioritizes trainings that show a skill hint (overriding your stat prioritization), in every year. Hints still respect the failure-chance and energy limits and your training blacklist — a blacklisted stat is never trained even when it shows a hint.",
        page: "TrainingSettings",
    },
    {
        id: "enable-training-level-weighting",
        title: "Weight Score by Training Level",
        description:
            "When enabled (Year 2+), the bot reads each training's level (1-5) via OCR and boosts the score for trainings whose stat sits in the top 3 of your Stat Prioritization list. OCR is skipped during Pre-Debut, Junior, and Summer.",
        page: "TrainingSettings",
    },
    {
        id: "enable-deck-concentration-check",
        title: "Warn on Spread Support Deck",
        description:
            "At career start the bot reads your support-deck composition and logs a [DECK] warning if your build's core stat type has too few cards. Advisory only; the run continues. URA uses the floor, Unity Cup floor-1, Trackblazer skipped.",
        page: "TrainingSettings",
    },
    {
        id: "deck-concentration-card-floor",
        title: "Minimum Core-Type Support Cards",
        description: "Warn if the build's core stat type has fewer than this many support cards. 4 matches the meta 4-5 build-type rainbow-stacking shell.",
        page: "TrainingSettings",
        parentId: "enable-deck-concentration-check",
    },
    {
        id: "must-rest-before-summer",
        title: "Must Rest before Summer",
        description: "Forces the bot to rest during June Late Phase in Classic and Senior Years to ensure enough energy for Summer Training in July.",
        page: "TrainingSettings",
    },
    {
        id: "train-wit-during-finale",
        title: "Train Wit During Finale",
        description: "When enabled, the bot will train Wit during URA finale turns (73, 74, 75) instead of recovering energy or mood, even if the failure chance is high.",
        page: "TrainingSettings",
    },
    {
        id: "enable-rainbow-training-bonus",
        title: "Enable Rainbow Training Bonus",
        description:
            "When enabled (Year 2+), rainbow trainings receive a significant bonus to their score, making them more likely to be selected. This is highly dependent on device configuration and may result in false positives.",
        page: "TrainingSettings",
    },
    {
        id: "enable-training-analysis-validation",
        title: "Enable Training Analysis Validation",
        description:
            "When enabled, the bot will validate the current selected stat during training analysis. This helps prevent the bot from accidentally training a stat during analysis at the cost of a significant increase in scenario completion time.",
        page: "TrainingSettings",
    },
    {
        id: "preferred-distance-override",
        title: "Preferred Distance Override",
        description: "Set the preferred race distance for training targets.",
        page: "TrainingSettings",
    },
    {
        id: "mood-floor",
        title: "Mood Floor",
        description:
            "The mood condition the bot keeps the trainee at or above: Normal, Good, or Great. When her mood drops below the floor, the bot spends a turn on recreation instead of training. Default is Good.",
        page: "TrainingSettings",
    },
    {
        id: "stat-targets-by-distance",
        title: "Stat Targets by Distance",
        description:
            "Set target values for each stat based on race distance. These stat targets are derived from past Champion Meetings. The bot will prioritize training stats that are below these targets.",
        page: "TrainingSettings",
    },

    // ============================================================
    // Training Event Settings
    // ============================================================
    {
        id: "prioritize-energy-options",
        title: "Prioritize Energy Options",
        description:
            "When enabled, the bot will prioritize training event choices that provide energy recovery or avoid energy consumption, helping to maintain optimal energy levels for training sessions.",
        page: "TrainingEventSettings",
    },
    {
        id: "training-event-option-overrides",
        title: "Training Event Option Overrides",
        description:
            "Force the bot to select a specific option for character or support training events. Search through all available events and select which option to use. This overrides the normal stat prioritization logic.",
        page: "TrainingEventSettings",
    },
    {
        id: "special-event-overrides",
        title: "Special Event Overrides",
        description: "Override the bot's normal stat prioritization for specific training events. These settings bypass the standard weight calculation system.",
        page: "TrainingEventSettings",
    },
    {
        id: "ocr-recognition-settings-title",
        title: "OCR Recognition Settings",
        description: "Configure settings for detecting and recognizing Training Event titles using OCR.",
        page: "TrainingEventSettings",
    },
    {
        id: "automatic-ocr-retry-training",
        title: "Enable Automatic OCR Retry for Training Events",
        description: "When enabled, the bot will automatically retry OCR detection if the initial attempt for a training event title fails or has low confidence.",
        page: "TrainingEventSettings",
        parentId: "ocr-recognition-settings-title",
    },
    {
        id: "ocr-confidence-training",
        title: "OCR Confidence for Training Events",
        description: "The minimum confidence level required for a Training Event title to be considered a match.",
        page: "TrainingEventSettings",
        parentId: "ocr-recognition-settings-title",
    },
    {
        id: "hide-ocr-comparison-results-training",
        title: "Hide OCR String Comparison Results",
        description: "If enabled, the bot will suppress detailed logging of individual string similarity scores during training event detection to keep the logs cleaner.",
        page: "TrainingEventSettings",
        parentId: "ocr-recognition-settings-title",
    },

    // ============================================================
    // OCR Settings
    // ============================================================
    {
        id: "ocr-threshold",
        title: "OCR Threshold",
        description:
            "Adjust the threshold for OCR text detection. Higher values make text detection more strict, lower values make it more lenient. Note: This setting does not affect high-precision features like Stat Detection or Training Failure Chance detection.",
        page: "DebugSettings",
    },

    // ============================================================
    // Racing Settings
    // ============================================================
    {
        id: "enable-farming-fans",
        title: "Enable Farming Fans",
        description: "When enabled, the bot will start running extra races to gain fans.",
        page: "RacingSettings",
    },
    {
        id: "days-to-run-extra-races",
        title: "Days to Run Extra Races",
        description: "Controls when extra races can be run using modulo arithmetic.",
        page: "RacingSettings",
    },
    {
        id: "ignore-consecutive-race-warning",
        title: "Ignore Consecutive Race Warning",
        description: "When enabled, the bot will ignore the warning popup about consecutive races and continue racing.",
        page: "RacingSettings",
    },
    {
        id: "ignore-low-energy-racing-block",
        title: "Ignore Low Energy Racing Block",
        description: "When enabled, the Trackblazer bot will not block racing when energy is critically low with consecutive races.",
        page: "RacingSettings",
    },
    {
        id: "disable-race-retries",
        title: "Disable Race Retries",
        description: "When enabled, the bot will not retry mandatory races if they fail and will stop.",
        page: "RacingSettings",
    },
    {
        id: "enable-free-race-retry",
        title: "Allow Daily Free Race Retry",
        description: "When enabled, the bot will attempt to retry a failed mandatory race only if the daily free race retry is available.",
        page: "RacingSettings",
        parentId: "disable-race-retries",
    },
    {
        id: "enable-complete-career-on-failure",
        title: "Complete Career on Failure",
        description:
            "When enabled, the bot will proceed to the career completion screen when a mandatory race is failed and it has run out of retries (or if retries are disabled). This is as opposed to the bot stopping at the Try Again dialog.",
        page: "RacingSettings",
    },
    {
        id: "enable-stop-on-mandatory-races",
        title: "Stop on Mandatory Races",
        description: "When enabled, the bot will automatically stop when it encounters a mandatory race, allowing you to manually handle them.",
        page: "RacingSettings",
    },
    {
        id: "junior-year-race-strategy",
        title: "Junior Year Race Strategy",
        description: "The race strategy to use for all races during Junior Year.",
        page: "RacingSettings",
    },
    {
        id: "original-race-strategy",
        title: "Original Race Strategy",
        description: "The race strategy to reset to after Junior Year. The bot will use this strategy for races in Year 2 and beyond.",
        page: "RacingSettings",
    },
    {
        id: "enable-force-racing",
        title: "Force Racing",
        description: "When enabled, the bot will skip all training, rest, and mood recovery activities and focus exclusively on racing every day.",
        page: "RacingSettings",
    },
    {
        id: "enable-user-in-game-race-agenda",
        title: "Enable User In-Game Race Agenda",
        description:
            "When enabled, the bot will load your selected in-game race agenda instead of using the racing plan settings. Note that this will disable the farming fans and racing plan settings.",
        page: "RacingSettings",
    },
    {
        id: "user-in-game-race-agenda",
        title: "Select User In-Game Race Agenda",
        description: "The in-game race agenda to use when 'Enable User In-Game Race Agenda' is enabled.",
        page: "RacingSettings",
        parentId: "enable-user-in-game-race-agenda",
    },
    {
        id: "custom-agenda-title",
        title: "Custom Agenda Title",
        description: "If you renamed your agenda in-game, enter the custom title here. Leave blank to use the selected agenda name above.",
        page: "RacingSettings",
        parentId: "enable-user-in-game-race-agenda",
    },
    {
        id: "limit-races-to-in-game-agenda",
        title: "Limit Extra Races to Agenda",
        description:
            "When enabled, the bot will override the racing behavior of any scenario such that it will not run any extra races except for the ones scheduled by the selected user's in-game racing agenda.",
        page: "RacingSettings",
        parentId: "enable-user-in-game-race-agenda",
    },
    {
        id: "skip-summer-training-for-agenda",
        title: "Skip Summer Training for Agenda",
        description:
            "When enabled, the bot will perform scheduled races from the in-game racing agenda during Summer instead of prioritizing Summer training. Note that this requires 'Enable User In-Game Race Agenda' to be enabled.",
        page: "RacingSettings",
        parentId: "enable-user-in-game-race-agenda",
    },

    // ============================================================
    // Racing Plan Settings
    // ============================================================
    {
        id: "enable-racing-plan",
        title: "Enable Racing Plan (Beta)",
        description: "When enabled, the bot will use smart race planning to optimize race selection.",
        page: "RacingPlanSettings",
    },
    {
        id: "enable-mandatory-racing-plan",
        title: "Treat Planned Races as Mandatory",
        description:
            "When enabled, the bot will prioritize the specific planned race that matches the current turn number, bypassing opportunity cost analysis. Note that it will only run the races if the racer's aptitudes are double predictions (both terrain and distance must be B or greater).",
        page: "RacingPlanSettings",
        parentId: "enable-racing-plan",
    },
    {
        id: "minimum-fans-threshold",
        title: "Minimum Fans Threshold",
        description: "Bot will prioritize races with at least this many fans.",
        page: "RacingPlanSettings",
        parentId: "enable-racing-plan",
    },
    {
        id: "look-ahead-days",
        title: "Look-Ahead Days",
        description: "Number of days to look ahead when making smart racing decisions.",
        page: "RacingPlanSettings",
        parentId: "enable-racing-plan",
    },
    {
        id: "smart-racing-check-interval",
        title: "Smart Racing Check Interval",
        description: "Interval in seconds between smart racing checks.",
        page: "RacingPlanSettings",
        parentId: "enable-racing-plan",
    },
    {
        id: "minimum-quality-threshold",
        title: "Minimum Quality Threshold",
        description:
            'The core "Quality Floor" for a race today. If the best race available right now scores below this value, the bot will choose to wait for a future opportunity instead (even if the future looks worse).',
        page: "RacingPlanSettings",
        parentId: "enable-racing-plan",
    },
    {
        id: "time-decay-factor",
        title: "Time Decay Factor",
        description: 'A multiplier applied to future race scores to account for the risk of waiting. Lower values make the bot more "impatient" by discounting future rewards more heavily.',
        page: "RacingPlanSettings",
        parentId: "enable-racing-plan",
    },
    {
        id: "improvement-threshold",
        title: "Improvement Threshold",
        description: 'The "Surplus Value" required to justify waiting. The bot will only wait if a discounted future race scores at least this many points higher than the best race today.',
        page: "RacingPlanSettings",
        parentId: "enable-racing-plan",
    },
    {
        id: "preferred-terrain",
        title: "Preferred Terrain",
        description: "The preferred terrain for races. The bot will prioritize races with this terrain when selecting races to enter.",
        page: "RacingPlanSettings",
        parentId: "enable-racing-plan",
    },
    {
        id: "preferred-race-grades",
        title: "Preferred Race Grades",
        description: "Select which race grades the bot should prioritize.",
        page: "RacingPlanSettings",
        parentId: "enable-racing-plan",
    },
    {
        id: "preferred-race-distances",
        title: "Preferred Race Distances",
        description: "Select which race distances the bot should prioritize.",
        page: "RacingPlanSettings",
        parentId: "enable-racing-plan",
    },
    {
        id: "planned-races",
        title: "Planned Races",
        description: "Select which races the bot should prioritize using opportunity cost analysis.",
        page: "RacingPlanSettings",
        parentId: "enable-racing-plan",
    },

    // ============================================================
    // Skill Settings
    // ============================================================
    {
        id: "skill-spend-mode",
        title: "Skill Spend Mode",
        description: "Manual uses the configured Skill Point threshold (the current behavior). Adaptive derives the mid-career threshold from your account-strength tier instead.",
        page: "SkillSettings",
    },
    {
        id: "account-strength",
        title: "Account Strength",
        description: "How developed the account's supports and roster are, for Adaptive skill spending: Auto, New, Developing, Established, or Endgame. Support quality matters more than Team Rank.",
        page: "SkillSettings",
        parentId: "skill-spend-mode",
    },
    {
        id: "skill-point-check",
        title: "Spend Skill Points",
        description: "When the bot spends skill points mid-career: a preset (0 / 350 / 700 / 1200 SP), a custom threshold, or Career end to save them all for the Pre-Finals and end-of-career buys.",
        page: "SkillSettings",
    },
    {
        id: "skill-plan-running-style",
        title: "Running Style for Skills",
        description: "Dictates which skills are considered for purchase based on the preferred running style.",
        page: "SkillSettings",
    },
    {
        id: "preferred-track-surface",
        title: "Track Surface for Skills",
        description: "Dictates which skills are considered for purchase based on the terrain.",
        page: "SkillSettings",
    },
    {
        id: "skip-double-circle-upgrades",
        title: "Skip ◎ Skill Upgrades",
        description: "Buy only the ○ version of a skill and skip its ◎ upgrade, spreading the same skill points across more distinct skills.",
        page: "SkillSettings",
    },

    // ============================================================
    // Skill Plan Settings - Skill Point Check
    // ============================================================
    {
        id: "enable-skill-plan-skillPointCheck",
        title: "Enable Skill Point Check Plan (Beta)",
        description: "When enabled, the bot will attempt to purchase skills based on the following configuration.",
        page: "SkillPlanSettingsSkillPointCheck",
    },
    {
        id: "enable-buy-inherited-unique-skills-SkillPlanSettingsSkillPointCheck",
        title: "Purchase All Inherited Unique Skills",
        description: "When enabled, the bot will attempt to purchase all inherited unique skills regardless of their evaluated rating or community tier list rating.",
        page: "SkillPlanSettingsSkillPointCheck",
        parentId: "enable-skill-plan-skillPointCheck",
    },
    {
        id: "enable-buy-negative-skills-SkillPlanSettingsSkillPointCheck",
        title: "Purchase All Negative Skills",
        description: "When enabled, the bot will attempt to purchase all negative skills (i.e. Firm Conditions ×).",
        page: "SkillPlanSettingsSkillPointCheck",
        parentId: "enable-skill-plan-skillPointCheck",
    },

    // ============================================================
    // Skill Plan Settings - Pre-Finals
    // ============================================================
    {
        id: "enable-skill-plan-preFinals",
        title: "Enable Pre-Finals Plan (Beta)",
        description: "When enabled, the bot will attempt to purchase skills based on the following configuration.",
        page: "SkillPlanSettingsPreFinals",
    },
    {
        id: "enable-buy-inherited-unique-skills-SkillPlanSettingsPreFinals",
        title: "Purchase All Inherited Unique Skills",
        description: "When enabled, the bot will attempt to purchase all inherited unique skills regardless of their evaluated rating or community tier list rating.",
        page: "SkillPlanSettingsPreFinals",
        parentId: "enable-skill-plan-preFinals",
    },
    {
        id: "enable-buy-negative-skills-SkillPlanSettingsPreFinals",
        title: "Purchase All Negative Skills",
        description: "When enabled, the bot will attempt to purchase all negative skills (i.e. Firm Conditions ×).",
        page: "SkillPlanSettingsPreFinals",
        parentId: "enable-skill-plan-preFinals",
    },

    // ============================================================
    // Skill Plan Settings - Career Complete
    // ============================================================
    {
        id: "enable-skill-plan-careerComplete",
        title: "Enable Career Complete Plan (Beta)",
        description: "When enabled, the bot will attempt to purchase skills based on the following configuration.",
        page: "SkillPlanSettingsCareerComplete",
    },
    {
        id: "enable-buy-inherited-unique-skills-SkillPlanSettingsCareerComplete",
        title: "Purchase All Inherited Unique Skills",
        description: "When enabled, the bot will attempt to purchase all inherited unique skills regardless of their evaluated rating or community tier list rating.",
        page: "SkillPlanSettingsCareerComplete",
        parentId: "enable-skill-plan-careerComplete",
    },
    {
        id: "enable-buy-negative-skills-SkillPlanSettingsCareerComplete",
        title: "Purchase All Negative Skills",
        description: "When enabled, the bot will attempt to purchase all negative skills (i.e. Firm Conditions ×).",
        page: "SkillPlanSettingsCareerComplete",
        parentId: "enable-skill-plan-careerComplete",
    },

    // ============================================================
    // Scenario Overrides Settings
    // ============================================================
    {
        id: "grand-concert-quick-mode",
        title: "Grand Concert Quick Mode",
        description: "Which option the bot picks on the Quick Mode Settings dialog when a Grand Concert career starts.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-consecutive-races-limit",
        title: "Trackblazer Consecutive Races Limit",
        description: "Sets the maximum number of consecutive races the bot is allowed to run in the Trackblazer scenario before stopping.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-energy-threshold",
        title: "Trackblazer Energy Threshold",
        description: "Sets the energy threshold below which the bot will use energy recovery items in the Trackblazer scenario.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-shop-check-grades",
        title: "Trackblazer Shop Check Grades",
        description: "Select which race grades should trigger a shop check after the race in the Trackblazer scenario.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-min-stat-gain-for-charm",
        title: "Trackblazer Min Main Stat Gain for Good-Luck Charm",
        description: "Sets the minimum main stat gain required to justify using a Good-Luck Charm during training in the Trackblazer scenario.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-low-main-stat-gain-item-floor",
        title: "Trackblazer Low Main Stat Gain Item Floor",
        description:
            "When mood is BAD or AWFUL, refuse to use Reset Whistle, Good-Luck Charm, or Megaphone if main-stat gain is below this floor. Prevents wasting items on structurally low-return turns where the mood multiplier caps the stat gains.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-skip-empowering-megaphone-below-gain",
        title: "Trackblazer Skip Empowering Megaphone Below Main Stat Gain",
        description:
            "Hold the Empowering Megaphone (+60% for 2 turns) unless the selected training's main-stat gain meets this threshold. 0 = always allowed. Reserve the strongest tier for high-gain turns like summer camp.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-skip-motivating-megaphone-below-gain",
        title: "Trackblazer Skip Motivating Megaphone Below Main Stat Gain",
        description:
            "Hold the Motivating Megaphone (+40% for 3 turns) unless the selected training's main-stat gain meets this threshold. 0 = always allowed. A blocked higher tier falls through to a lower one.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-skip-coaching-megaphone-below-gain",
        title: "Trackblazer Skip Coaching Megaphone Below Main Stat Gain",
        description:
            "Hold the Coaching Megaphone (+20% for 4 turns) unless the selected training's main-stat gain meets this threshold. 0 = always allowed. Leave low for an opening-burst style, or raise it to conserve coins for the summer shop.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-max-retries-per-race",
        title: "Trackblazer Max Retries per Race",
        description: "Sets the maximum number of retries allowed for a single race in the Trackblazer scenario.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-whistle-forces-training",
        title: "Trackblazer Reset Whistle Forces Training",
        description:
            "Whether or not using a Reset Whistle means it can ignore the failure chance thresholds in the Training Settings page. If enabled, the bot will pick the best available training after usage even if it's risky.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-retry-races-before-final-grades",
        title: "Trackblazer Race Grades to use Race Retries on",
        description: "Select which race grades should allow using a Race Retry in the Trackblazer scenario.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-enable-irregular-training",
        title: "Trackblazer Enable Irregular Training",
        description: "When enabled, the bot will check for highly profitable training sessions before opting for extra races.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-irregular-training-min-stat-gain",
        title: "Trackblazer Irregular Training Minimum Stat Gain",
        description: "Sets the minimum main stat gain required to skip racing and perform Irregular Training instead.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-excluded-items",
        title: "Trackblazer Items to Exclude from Shop",
        description: "Select items that the bot will never purchase from the shop in the Trackblazer scenario.",
        page: "ScenarioOverridesSettings",
    },
    {
        id: "trackblazer-shop-check-frequency",
        title: "Trackblazer Shop Check Frequency",
        description: "Sets the frequency of shop checks after races in the Trackblazer scenario. 1 = every race, 2 = 1 day after, 3 = 2 days after, etc.",
        page: "ScenarioOverridesSettings",
    },

    // ============================================================
    // Debug Settings
    // ============================================================
    {
        id: "enable-debug-mode",
        title: "Enable Debug Mode",
        description: "Allows debugging messages in the log and test images to be created in the /temp/ folder.",
        page: "DebugSettings",
    },
    {
        id: "template-match-confidence",
        title: "Adjust Confidence for Template Matching",
        description:
            "Sets the minimum confidence level for template matching with 1080p as the baseline. Consider lowering this to something like 0.7 or 70% at lower resolutions. Making it too low will cause the bot to match on too many things as false positives.",
        page: "DebugSettings",
    },
    {
        id: "template-match-custom-scale",
        title: "Set the Custom Image Scale for Template Matching",
        description:
            "Manually set the scale to do template matching. The Basic Template Matching Test can help find your recommended scale. Making it too low or too high will cause the bot to match on too little or too many things as false positives.",
        page: "DebugSettings",
    },
    {
        id: "enable-screen-recording",
        title: "Enable Screen Recording",
        description:
            "Records the screen while the bot is running. The mp4 file will be saved to the /recordings folder of the app's data directory. Note that performance and battery life may be impacted while recording.",
        page: "DebugSettings",
    },
    {
        id: "recording-bit-rate",
        title: "Recording Quality (Bit Rate)",
        description: "Sets the video bit rate for screen recording. Higher values produce better quality but larger file sizes.",
        page: "DebugSettings",
        parentId: "enable-screen-recording",
    },
    {
        id: "recording-frame-rate",
        title: "Recording Frame Rate",
        description: "Sets the frame rate for screen recording.",
        page: "DebugSettings",
        parentId: "enable-screen-recording",
    },
    {
        id: "recording-resolution-scale",
        title: "Recording Resolution Scale",
        description: "Scales the recording resolution. Lower values produce smaller file sizes but lower quality. 1.0 = full resolution, 0.5 = half resolution.",
        page: "DebugSettings",
        parentId: "enable-screen-recording",
    },
    {
        id: "debug-accessibility-service-check",
        title: "Accessibility Service Check",
        description: "The Accessibility Service allows the bot to perform clicks and gestures on your behalf. Check the current registration and initialization status here.",
        page: "DebugSettings",
    },
    {
        id: "debug-template-matching-test",
        title: "Start Basic Template Matching Test",
        description:
            "Disables normal bot operations and starts the template match test. Only on the Home screen and will check if it can find certain essential buttons on the screen. It will also output what scale it had the most success with.",
        page: "DebugSettings",
    },
    {
        id: "debug-single-training-ocr-test",
        title: "Start Single Training OCR Test",
        description:
            "Disables normal bot operations and starts the single training OCR test. Only on the Training screen and tests the current training on display for stat gains and failure chances.",
        page: "DebugSettings",
    },
    {
        id: "debug-comprehensive-training-ocr-test",
        title: "Start Comprehensive Training OCR Test",
        description: "Disables normal bot operations and starts the comprehensive training OCR test. Only on the Training screen and tests all 5 trainings for their stat gains and failure chances.",
        page: "DebugSettings",
    },
    {
        id: "debug-race-list-detection-test",
        title: "Start Race List Detection Test",
        description:
            "Disables normal bot operations and starts the Race List detection test. Only on the Race List screen and tests detecting the races with double star predictions currently on display.",
        page: "DebugSettings",
    },
    {
        id: "debug-main-screen-update-test",
        title: "Start Main Screen Update Test",
        description: "Disables normal bot operations and starts the Main Screen update test. This test will go through all Main Screen updates and then print the Trainee information.",
        page: "DebugSettings",
    },
    {
        id: "debug-skill-list-buy-test",
        title: "Start Skill List Buy Test",
        description:
            "Processes the list of skills in the Skills screen, reads all skills in the list, logs a summary and then logs another summary of which skills it will buy to bring down the current Skill Points as close to zero as possible and then it will stop there without actually doing the buying.",
        page: "DebugSettings",
    },
    {
        id: "debug-scrollbar-detection-test",
        title: "Start Scrollbar Detection Test",
        description:
            "Disables normal bot operations and starts the Scrollbar detection test. Detects the scrollbar on the current screen and attempts to scroll it up and down to verify functionality.",
        page: "DebugSettings",
    },
    {
        id: "debug-rainbow-detection-test",
        title: "Start Rainbow Detection Test",
        description:
            "Disables normal bot operations and starts the Rainbow detection test. Run it while the game shows the Training screen: it detects the rainbow glow ring on each support face circle, logs the per-support hue metrics and the derived rainbow count, and saves an annotated crop for calibration.",
        page: "DebugSettings",
    },
    {
        id: "debug-veteran-roster-read-test",
        title: "Start Veteran Roster Read Test",
        description:
            "Disables normal bot operations and starts the read-only Veteran Roster calibration test. Park the game on the Veteran Roster list or an open Umamusume Details dialog: it logs every field it can read (name, outfit, rank, Rating, stats, aptitudes, Career Info block) without tapping, swiping, or changing tabs.",
        page: "DebugSettings",
    },
    {
        id: "debug-veteran-roster-scan-test",
        title: "Start Veteran Roster Scan",
        description:
            "Disables normal bot operations and enumerates the whole Veteran roster read-only. Park the game on the Veteran Roster list with Filters: OFF: it opens the first card and walks the roster with the detail dialog's next chevron, recording each Veteran's identity fields. Nothing is transferred, favorited, or edited.",
        page: "DebugSettings",
    },
    {
        id: "veteran-roster-scan-limit",
        title: "Veteran Roster Scan Limit",
        description: "How many Veterans the read-only roster scan reads before it stops. Set 0 to walk the entire roster.",
        page: "DebugSettings",
    },
    {
        id: "debug-trackblazer-race-selection-test",
        title: "Start Trackblazer Race Selection Test",
        description:
            "Disables normal bot operations and starts the Trackblazer race selection test. Navigates to the Race List if on the Main Screen and identifies the best race to run, including Rivals.",
        page: "DebugSettings",
    },
    {
        id: "debug-trackblazer-inventory-sync-test",
        title: "Start Trackblazer Inventory Sync Test",
        description:
            "Disables normal bot operations and starts the Trackblazer inventory sync test. Opens the Training Items dialog if on the Main Screen and logs inventory contents and quick-use intentions.",
        page: "DebugSettings",
    },
    {
        id: "debug-trackblazer-buy-items-test",
        title: "Start Trackblazer Buy Items Test",
        description:
            "Disables normal bot operations and starts the Trackblazer buy items test. Opens the Shop if on the Main Screen and logs shop contents and purchase intentions without actually buying anything.",
        page: "DebugSettings",
    },

    // ============================================================
    // Run Queue Settings
    // ============================================================
    {
        id: "run-queue-smart-borrow",
        title: "Smart Borrow",
        description:
            "When the bot fills the empty friend slot before a career (queued or single run), scrolls down through the Borrow Card list and borrows the best card it finds from a curated list of great picks, skipping duplicates of your own deck.",
        page: "RunQueueSettings",
    },
    {
        id: "run-queue-support-deck-index",
        title: "Required Support Deck",
        description:
            "Require a specific saved support formation (Deck 1-10) at career start. The bot selects and verifies that deck on the Support Formation screen before and after the friend borrow, refusing to start (no TP spent) if it cannot, and suppresses Auto-Fill. 0 = off.",
        page: "RunQueueSettings",
    },
    {
        id: "run-queue-lineage-capture",
        title: "Capture Lineage Data",
        description:
            "After Auto-Select fills the legacy slots, briefly open the Legacy Select Sparks view and record the six ancestors (both parents and all four grandparents) with their inherited factors as data for later analysis. Off by default. Observational only: it never changes the selected parents and never blocks a career.",
        page: "RunQueueSettings",
    },
]

export default searchConfig
