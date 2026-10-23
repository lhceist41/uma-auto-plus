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

object LabelDuplicateSupport : ComponentInterface {
    // Orange "! Duplicate Support" pill straddling the top edge of a Borrow Card row whose
    // character is already in the support deck. Interior-only crop: the dialog and deck layers
    // rasterize the same pill differently, so each context gets its own template.
    override val template = Template("components/label/duplicate_support")
}

object LabelDuplicateSupportDeck : ComponentInterface {
    // The same "! Duplicate Support" pill as rendered on the support deck screen's card slots.
    // Present on BOTH clashing cards, so a single check() detects an illegal deck.
    override val template = Template("components/label/duplicate_support_deck")
}

object LabelTraineeConflict : ComponentInterface {
    // Red "! Trainee" pill straddling the top edge of a Borrow Card row whose character IS the
    // active trainee (the game refuses such a deck: "Includes a character identical to the
    // Trainee."). Interior-only crop, same per-context split as the Duplicate Support pills.
    override val template = Template("components/label/trainee_conflict")
}

object LabelTraineeConflictDeck : ComponentInterface {
    // The same "! Trainee" pill as rendered on the support deck screen's friend slot. While it
    // is visible, Start Career is disabled and taps on it do nothing, so it must be resolved by
    // replacing the borrow, never by retrying the button.
    override val template = Template("components/label/trainee_conflict_deck")
}

object LabelFriendSlotBanner : ComponentInterface {
    // Pink "Friends" banner at the bottom of the FILLED friend slot on the support deck screen.
    // Used to reopen the Borrow Card picker when the slot is no longer empty (the empty-slot
    // icon is gone by then).
    override val template = Template("components/label/friend_slot_banner")
}

object LabelBorrowSelected : ComponentInterface {
    // Pink "Selected" pill straddling the top-left edge of the Borrow Card row currently borrowed
    // into the friend slot. Only present when the picker is reopened over a FILLED slot; findAll
    // locates it per row at the SAME top-of-row offset as the Duplicate/Trainee pills. Used to
    // verify which live row the committed borrow is, since the Support Formation grid shows only the
    // card portrait, level and limit-break pips and no textual card name.
    override val template = Template("components/label/borrow_selected")
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

object LabelScenarioSelectGrandConcert : ComponentInterface {
    override val template = Template("components/label/scenario_select_grand_concert", region = Region.middle)
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

object LabelRaceCriteriaG1 : ComponentInterface {
    override val template = Template("components/label/race_criteria_g1", region = Region.topHalf)
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

/** The pink "Used" tag on an "Exchange Complete" row that the game used automatically on purchase. */
object LabelItemUsed : ComponentInterface {
    override val template = Template("components/label/item_used", region = Region.rightHalf)
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

/** "Race Details" header on the Daily Races pre-race confirmation screen (with Multi-Race toggle + Race!). */
object LabelRaceDetails : ComponentInterface {
    override val template = Template("components/label/race_details_header", region = Region.topHalf)
}

/** "Race Results" header over each Daily Races result and the Total Rewards screen. */
object LabelRaceResultsHeader : ComponentInterface {
    override val template = Template("components/label/race_results_header", region = Region.topHalf)
}

/** "Daily Races" purple header banner visible at top of the Daily Races screen group (race-pick + difficulty-pick). */
object LabelDailyRacesHeader : ComponentInterface {
    override val template = Template("components/label/daily_races_header", region = Region.topHalf)
}

/** "Runner Selection" purple header visible after picking a difficulty - the horse-picker step before Race Details. */
object LabelRunnerSelection : ComponentInterface {
    override val template = Template("components/label/runner_selection", region = Region.topHalf)
}

/** "Done for today!" strip on a Daily Program card whose tickets are spent. */
object LabelDoneForToday : ComponentInterface {
    override val template = Template("components/label/done_for_today", region = Region.bottomHalf)
}

/** "Daily Legend Races" purple header over the legend grid. */
object LabelDailyLegendRacesHeader : ComponentInterface {
    override val template = Template("components/label/daily_legend_races_header", region = Region.topQuarter, confidence = 0.9)
}

/** "to Star Unlock" strip under a legend card that can still unlock a star. Search with the grid's region. */
object LabelStarUnlockText : ComponentInterface {
    override val template = Template("components/label/star_unlock_text", confidence = 0.85)
}

/** "Main Rewards" label on each row of a legend's race list. */
object LabelMainRewards : ComponentInterface {
    override val template = Template("components/label/main_rewards", region = Region.middle)
}

/** A legend's name logo on its Daily Legend Races card. */
private fun legendLogo(template: Template): ComponentInterface =
    object : ComponentInterface {
        override val template = template
    }

/** Name logos on the Daily Legend Races cards, keyed by the legend's name as stored in settings. Search with the grid's region. */
val LabelDailyLegendLogos: Map<String, ComponentInterface> =
    mapOf(
        "Agnes Digital" to legendLogo(Template("components/label/daily_legend_agnes_digital", confidence = 0.8)),
        "Air Groove" to legendLogo(Template("components/label/daily_legend_air_groove", confidence = 0.8)),
        "Biwa Hayahide" to legendLogo(Template("components/label/daily_legend_biwa_hayahide", confidence = 0.8)),
        "Curren Chan" to legendLogo(Template("components/label/daily_legend_curren_chan", confidence = 0.8)),
        "Eishin Flash" to legendLogo(Template("components/label/daily_legend_eishin_flash", confidence = 0.8)),
        "El Condor Pasa" to legendLogo(Template("components/label/daily_legend_el_condor_pasa", confidence = 0.8)),
        "Fuji Kiseki" to legendLogo(Template("components/label/daily_legend_fuji_kiseki", confidence = 0.8)),
        "Gold Ship" to legendLogo(Template("components/label/daily_legend_gold_ship", confidence = 0.8)),
        "Grass Wonder" to legendLogo(Template("components/label/daily_legend_grass_wonder", confidence = 0.8)),
        "King Halo" to legendLogo(Template("components/label/daily_legend_king_halo", confidence = 0.8)),
        "Maruzensky" to legendLogo(Template("components/label/daily_legend_maruzensky", confidence = 0.8)),
        "Matikanefukukitaru" to legendLogo(Template("components/label/daily_legend_matikanefukukitaru", confidence = 0.8)),
        "Mayano Top Gun" to legendLogo(Template("components/label/daily_legend_mayano_top_gun", confidence = 0.8)),
        "Mejiro McQueen" to legendLogo(Template("components/label/daily_legend_mejiro_mcqueen", confidence = 0.8)),
        "Mejiro Ryan" to legendLogo(Template("components/label/daily_legend_mejiro_ryan", confidence = 0.8)),
        "Mihono Bourbon" to legendLogo(Template("components/label/daily_legend_mihono_bourbon", confidence = 0.8)),
        "Nice Nature" to legendLogo(Template("components/label/daily_legend_nice_nature", confidence = 0.8)),
        "Oguri Cap" to legendLogo(Template("components/label/daily_legend_oguri_cap", confidence = 0.8)),
        "Sakura Bakushin O" to legendLogo(Template("components/label/daily_legend_sakura_bakushin_o", confidence = 0.8)),
        "Seiun Sky" to legendLogo(Template("components/label/daily_legend_seiun_sky", confidence = 0.8)),
        "Silence Suzuka" to legendLogo(Template("components/label/daily_legend_silence_suzuka", confidence = 0.8)),
        "Special Week" to legendLogo(Template("components/label/daily_legend_special_week", confidence = 0.8)),
        "Super Creek" to legendLogo(Template("components/label/daily_legend_super_creek", confidence = 0.8)),
        "Symboli Rudolf" to legendLogo(Template("components/label/daily_legend_symboli_rudolf", confidence = 0.8)),
        "T.M. Opera O" to legendLogo(Template("components/label/daily_legend_t_m_opera_o", confidence = 0.8)),
        "Taiki Shuttle" to legendLogo(Template("components/label/daily_legend_taiki_shuttle", confidence = 0.8)),
        "Tokai Teio" to legendLogo(Template("components/label/daily_legend_tokai_teio", confidence = 0.8)),
        "Vodka" to legendLogo(Template("components/label/daily_legend_vodka", confidence = 0.8)),
        "Winning Ticket" to legendLogo(Template("components/label/daily_legend_winning_ticket", confidence = 0.8)),
    )

/** "Multi-Race" green header on the popup that asks how many races to run (1/3, 2/3, 3/3). Bot commits 3/3. */
object LabelMultiRacePopup : ComponentInterface {
    override val template = Template("components/label/multi_race_popup", region = Region.middle)
}

/** "Daily Sale" green header on the shop popup that appears after daily races finish. Bot always taps Cancel (user shops manually). */
object LabelDailySale : ComponentInterface {
    override val template = Template("components/label/daily_sale", region = Region.middle)
}

/** The gold "1st" laurel: unlike the "Congratulations!" banner, it also appears on the finale Qualifier/Semi-Final wins ("You did it!"). */
object LabelFirstPlace : ComponentInterface {
    override val template = Template("components/label/first_place", region = Region.topHalf)
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

/** The "Club Menu" button at the top right of the Club screen. */
object LabelClubMenu : ComponentInterface {
    override val template = Template("components/label/club_menu", region = Region.topQuarter)
}

/** "Item Request" dialog title. 0.9 because the Confirm Request title scores 0.85 against it. */
object LabelItemRequestHeader : ComponentInterface {
    override val template = Template("components/label/item_request_header", region = Region.topQuarter, confidence = 0.9)
}

/** "The item request has expired." on the result of the account's own finished request. */
object LabelItemRequestExpired : ComponentInterface {
    override val template = Template("components/label/item_request_expired", region = Region.bottomHalf)
}

/** "...ended because it reached max donations." on the result of a request that filled up. */
object LabelItemRequestMaxDonations : ComponentInterface {
    override val template = Template("components/label/item_request_max_donations", region = Region.bottomHalf)
}

/** "Select an item to request." under the item picker. */
object LabelItemRequestSelectPrompt : ComponentInterface {
    override val template = Template("components/label/item_request_select_prompt", region = Region.bottomHalf)
}

/** The "Held" pill of each item picker row. 0.9 because the Club chat scores 0.79 against it. */
object LabelItemRequestHeld : ComponentInterface {
    override val template = Template("components/label/item_request_held", confidence = 0.9)
}

object LabelConfirmRequestHeader : ComponentInterface {
    override val template = Template("components/label/confirm_request_header", region = Region.middle, confidence = 0.9)
}

/** "Rewards Collected" title, shown after Collect All in Missions and Presents. */
object LabelRewardsCollectedHeader : ComponentInterface {
    override val template = Template("components/label/rewards_collected_header", region = Region.topQuarter, confidence = 0.9)
}

object LabelPresentsHeader : ComponentInterface {
    override val template = Template("components/label/presents_header", region = Region.topQuarter, confidence = 0.9)
}

object LabelMissionsHeader : ComponentInterface {
    override val template = Template("components/label/missions_header", region = Region.topQuarter)
}
