/**
 * Defines label components.
 *
 * These are non-clickable regions of text on screen.
 */

package com.steve1316.uma_android_automation.components

object LabelSupportFormation : ComponentInterface {
    override val template = Template("components/label/support_formation", region = Region.topHalf, confidence = 0.8)
}

object LabelStatDistance : ComponentInterface {
    override val template = Template("components/label/stat_distance", region = Region.topHalf)
}

object LabelStatTrackSurface : ComponentInterface {
    override val template = Template("components/label/stat_track_surface", region = Region.topHalf)
}

object LabelStatStyle : ComponentInterface {
    override val template = Template("components/label/stat_style", region = Region.topHalf)
}

object LabelUmamusumeClassFans : ComponentInterface {
    override val template = Template("components/label/umamusume_class_fans", region = Region.middle)
}

object LabelStatTableHeaderSkillPoints : ComponentInterface {
    override val template = Template("components/label/stat_table_header_skill_points", region = Region.bottomHalf)
}

object LabelTrainingFailureChance : ComponentInterface {
    override val template = Template("components/label/training_failure_chance", region = Region.bottomHalf)
}

object LabelWinToBecomeRank : ComponentInterface {
    override val template = Template("components/label/win_to_become_rank")
}

object LabelUnityCupOpponentSelectionLaurel : ComponentInterface {
    // Recaptured 2026-07-06: the July patch replaced the laurel medals with per-rank colored
    // ones, so the asset is now the rank-agnostic "Win to become rank" pill text that sits on
    // every opponent card (name kept for history; one match per card, tap lands on the card).
    override val template = Template("components/label/unitycup_opponent_selection_laurel", region = Region.rightHalf)
}

object LabelScenarioSelectHeader : ComponentInterface {
    override val template = Template("components/label/scenario_select_header", region = Region.topHalf)
}

object LabelBorrowLastLogin : ComponentInterface {
    // Every row of the Borrow Card picker renders this pill at a fixed offset below the row's
    // card name, so findAll on it locates every visible row regardless of scroll position.
    override val template = Template("components/label/borrow_last_login")
}

object LabelScenarioSelectUra : ComponentInterface {
    override val template = Template("components/label/scenario_select_ura", region = Region.middle)
}

object LabelScenarioSelectUnityCup : ComponentInterface {
    override val template = Template("components/label/scenario_select_unity_cup", region = Region.middle)
}

object LabelScenarioSelectTrackblazer : ComponentInterface {
    override val template = Template("components/label/scenario_select_trackblazer", region = Region.middle)
}

object LabelVeteranUmamusumeMax : ComponentInterface {
    // The "Veteran Umamusume Max" popup shown when the veteran roster is full (e.g. 260/260) and a
    // career cannot start until one is transferred/released. Detected so the between-run queue stops
    // with a clear reason instead of looping - the popup's Close just bounces back to Scenario Select.
    override val template = Template("components/label/veteran_umamusume_max", region = Region.topHalf)
}

object LabelEnergy : ComponentInterface {
    override val template = Template("components/label/energy")
}

object LabelEnergyBarLeftPart : ComponentInterface {
    override val template = Template("components/label/energy_bar_left_part")
}

object LabelEnergyBarRightPart : ComponentInterface {
    override val template = Template("components/label/energy_bar_right_part_0")
}

object LabelEnergyBarExtendedRightPart : ComponentInterface {
    override val template = Template("components/label/energy_bar_right_part_1")
}

object LabelSkillListScreenSkillPoints : ComponentInterface {
    override val template = Template("components/label/skill_list_screen_skill_points", region = Region.topHalf)
}

/**
 * Teal/mint variant of the Skill Points banner used on the post-career skill purchase screen
 * (and possibly other variants after the Trackblazer-era UI refresh). Pixel-identical layout
 * to [LabelSkillListScreenSkillPoints], just with different banner color, so it can be used
 * interchangeably for screen detection and OCR localization.
 */
object LabelSkillListScreenSkillPointsV2 : ComponentInterface {
    override val template = Template("components/label/skill_list_screen_skill_points_v2", region = Region.topHalf)
}

object LabelScheduledRace : ComponentInterface {
    override val template = Template("components/label/scheduled_race", region = Region.bottomHalf)
}

object LabelTrainingCannotPerform : ComponentInterface {
    override val template = Template("components/label/training_cannot_perform", region = Region.middle)
}

object LabelTrophyWonDialogTitle : ComponentInterface {
    override val template = Template("components/label/trophy_won")
}

object LabelConnecting : ComponentInterface {
    override val template = Template("components/label/connecting", region = Region.topHalf)
}

object LabelNowLoading : ComponentInterface {
    override val template = Template("components/label/now_loading", region = Region.bottomHalf)
}

object LabelOrdinaryCuties : ComponentInterface {
    override val template = Template("components/label/ordinary_cuties", region = Region.middle)
}

object LabelStatMaxed : ComponentInterface {
    override val template = Template("components/label/stat_maxed")
}

object LabelStatAptitudeA : ComponentInterface {
    override val template = Template("components/label/stat_aptitude_A")
}

object LabelStatAptitudeB : ComponentInterface {
    override val template = Template("components/label/stat_aptitude_B")
}

object LabelStatAptitudeC : ComponentInterface {
    override val template = Template("components/label/stat_aptitude_C")
}

object LabelStatAptitudeD : ComponentInterface {
    override val template = Template("components/label/stat_aptitude_D")
}

object LabelStatAptitudeE : ComponentInterface {
    override val template = Template("components/label/stat_aptitude_E")
}

object LabelStatAptitudeF : ComponentInterface {
    override val template = Template("components/label/stat_aptitude_F")
}

object LabelStatAptitudeG : ComponentInterface {
    override val template = Template("components/label/stat_aptitude_G")
}

object LabelStatAptitudeS : ComponentInterface {
    override val template = Template("components/label/stat_aptitude_S")
}

object LabelRecreationDateComplete : ComponentInterface {
    override val template = Template("components/label/recreation_date_complete", region = Region.middle)
}

object LabelRaceSelectionFans : ComponentInterface {
    override val template = Template("components/label/race_selection_fans", region = Region.bottomHalf)
}

object LabelRaceCriteriaFans : ComponentInterface {
    override val template = Template("components/label/race_criteria_fans", region = Region.topHalf)
}

object LabelRaceCriteriaG3OrAbove : ComponentInterface {
    override val template = Template("components/label/race_criteria_g3_or_above", region = Region.topHalf)
}

object LabelRaceCriteriaMaiden : ComponentInterface {
    override val template = Template("components/label/race_criteria_maiden", region = Region.topHalf)
}

object LabelRaceCriteriaPreOpOrAbove : ComponentInterface {
    override val template = Template("components/label/race_criteria_pre_op_or_above", region = Region.topHalf)
}

object LabelRaceCriteriaTrophies : ComponentInterface {
    override val template = Template("components/label/race_criteria_trophies", region = Region.topHalf)
}

object LabelThereAreNoRacesToCompeteIn : ComponentInterface {
    override val template = Template("components/label/there_are_no_races_to_compete_in", region = Region.middle)
}

object LabelEventProgress : ComponentInterface {
    override val template = Template("components/label/event_progress", region = Region.middle)
}

object LabelRecreationUmamusume : ComponentInterface {
    override val template = Template("components/label/recreation_umamusume", region = Region.middle)
}

object LabelOnSale : ComponentInterface {
    override val template = Template("components/label/on_sale", region = Region.topHalf)
}

object LabelRivalRacer : ComponentInterface {
    override val template = Template("components/label/rival_racer", region = Region.rightHalf)
}

// -----------------------------------------------------------------------------
// Misc automation (bot/misc/*) - screen-identification labels for Daily Races,
// Team Trials, and TP/RP recharge flows. See bot/misc/MiscTask.kt.
// -----------------------------------------------------------------------------

/** Header on the Daily Programs container screen (contains Daily Races + Daily Legend Races tiles). */
object LabelDailyPrograms : ComponentInterface {
    override val template = Template("components/label/daily_programs", region = Region.middle)
}

/** Header on the Daily Legend Races grid screen. */
object LabelDailyLegendRaces : ComponentInterface {
    override val template = Template("components/label/daily_legend_races", region = Region.topHalf)
}

/** "Race Details" header on the Daily Races pre-race confirmation screen (with Multi-Race toggle + Race!). */
object LabelRaceDetails : ComponentInterface {
    override val template = Template("components/label/race_details_header", region = Region.topHalf)
}

/** "Daily Races" purple header banner visible at top of the Daily Races screen group (race-pick + difficulty-pick). */
object LabelDailyRacesHeader : ComponentInterface {
    override val template = Template("components/label/daily_races_header", region = Region.topHalf)
}

/** "Runner Selection" purple header visible after picking a difficulty - the horse-picker step before Race Details. */
object LabelRunnerSelection : ComponentInterface {
    override val template = Template("components/label/runner_selection", region = Region.topHalf)
}

/** "Multi-Race" green header on the popup that asks how many races to run (1/3, 2/3, 3/3). Bot commits 3/3. */
object LabelMultiRacePopup : ComponentInterface {
    override val template = Template("components/label/multi_race_popup", region = Region.middle)
}

/** "Daily Sale" green header on the shop popup that appears after daily races finish. Bot always taps Cancel (user shops manually). */
object LabelDailySale : ComponentInterface {
    override val template = Template("components/label/daily_sale", region = Region.middle)
}

/** "Congratulations!" banner shown on a 1st-place race result. Guards the mandatory-race
 * retry so a win is never retried. */
object LabelCongratulations : ComponentInterface {
    override val template = Template("components/label/congratulations", region = Region.topHalf)
}

/** The gold "1st" laurel shown on any 1st-place race result, independent of the victory-text banner
 * (the URA Finals shows "Congratulations!" but the Qualifier/Semi-Final show "You did it!"). Trainee-
 * agnostic, so it detects a finale win where the text-only [LabelCongratulations] misses the two
 * qualifier banners - used by the finale-result win capture in Racing.finalizeRaceResults. */
object LabelFirstPlace : ComponentInterface {
    override val template = Template("components/label/first_place", region = Region.topHalf)
}

/** "Recover TP" header on the TP recharge popup. */
object LabelRecoverTP : ComponentInterface {
    override val template = Template("components/label/recover_tp", region = Region.topHalf)
}

/** "Recover RP" header on the RP recharge popup. */
object LabelRecoverRP : ComponentInterface {
    override val template = Template("components/label/recover_rp", region = Region.topHalf)
}

/**
 * The "Event Boost (TP Usage x2)" bar on the Final Confirmation screen, used purely as a position
 * anchor for the checkbox to its left. The template is the OFF (dim) capture, but template matching
 * normalises out colour, so it matches the bright ON state too (~0.94) - it cannot tell ticked from
 * un-ticked. The navigator decides that from the checkbox colour (green vs grey), not this match.
 * confidence 0.8 keeps the anchor reliable in either state.
 */
object LabelEventBoostOff : ComponentInterface {
    override val template = Template("components/label/event_boost_off", region = Region.bottomHalf, confidence = 0.8)
}

/** "Team Trials" text label at the top-left of all Team Trials screens. Used to detect we're still in-mode during post-match cascades. */
object LabelTeamTrials : ComponentInterface {
    override val template = Template("components/label/team_trials_header", region = Region.topHalf)
}

/** "Items Selected" header on the pre-match item-picker popup. Confidence raised to 0.9 to avoid false-matching the green Race-N header bands on the post-match Standby screen. */
object LabelItemsSelected : ComponentInterface {
    override val template = Template("components/label/items_selected", region = Region.middle, confidence = 0.9)
}
