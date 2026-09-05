/**
 * Defines button components.
 *
 * Buttons are any element on screen that can be clicked to perform an action.
 *
 * Do not add checkboxes or radio buttons to this file. Those have their own files.
 *
 * Some buttons may have multiple different states. These should use the MultiStateButtonInterface interface instead of ButtonInterface.
 */

package com.steve1316.uma_android_automation.components

object ButtonAgenda : ButtonInterface {
    override val template = Template("components/button/agenda", region = Region.bottomHalf)
}

object ButtonAutoSelect : ButtonInterface {
    // Auto-Select on the Legacy Select screen renders mid-bottom; restricting to bottomHalf
    // skips scanning the top half of a 1920px screen every check without any loss of accuracy.
    override val template = Template("components/button/auto_select", region = Region.bottomHalf)
}

object ButtonAutoFill : ButtonInterface {
    override val template = Template("components/button/auto_fill", region = Region.bottomHalf, confidence = 0.75)
}

object ButtonBorrowCardRemove : ButtonInterface {
    // "Remove" bar at the top of the Borrow Card list (opened from the empty friend slot).
    // Used as the positional anchor for tapping the first card row below it.
    override val template = Template("components/button/borrow_card_remove", region = Region.topHalf)
}

object ButtonBack : ButtonInterface {
    override val template = Template("components/button/back", region = Region.bottomHalf)
}

object ButtonBackGreen : ButtonInterface {
    override val template = Template("components/button/back_green", region = Region.bottomHalf)
}

object ButtonBeginShowdown : ButtonInterface {
    override val template = Template("components/button/begin_showdown")
}

object ButtonBorrowSupportCard : ButtonInterface {
    override val template = Template("components/button/borrow_support_card")
}

object ButtonBurger : ButtonInterface {
    override val template = Template("components/button/burger", region = Region.bottomHalf)
}

object ButtonCancel : ButtonInterface {
    override val template = Template("components/button/cancel", region = Region.bottomHalf)
}

object ButtonChangeRunningStyle : ButtonInterface {
    override val template = Template("components/button/change")
}

object ButtonClose : ButtonInterface {
    override val template = Template("components/button/close", region = Region.bottomHalf)
}

// Compact bordered Close pill used by event popups (e.g. the Search! Solve! Summer!
// "Rewards Collected" dialog). Visually distinct from the wide flat close.png style,
// which only scores ~0.85 on it and flaps around the match threshold.
object ButtonCloseDialog : ButtonInterface {
    override val template = Template("components/button/close_dialog", region = Region.bottomHalf)
}

object ButtonCollectAll : ButtonInterface {
    override val template = Template("components/button/collect_all", region = Region.bottomHalf)
}

object ButtonConfirm : ButtonInterface {
    override val template = Template("components/button/confirm", region = Region.bottomHalf)
}

object ButtonConfirmExclamation : ButtonInterface {
    override val template = Template("components/button/confirm_exclamation", region = Region.bottomHalf)
}

object ButtonDailyRaces : ButtonInterface {
    override val template = Template("components/button/daily_races")
}

object ButtonDailyRacesDisabled : ButtonInterface {
    override val template = Template("components/button/daily_races_disabled")
}

object ButtonDailyRacesJupiterCup : ButtonInterface {
    override val template = Template("components/button/daily_races_jupiter_cup_logo")
}

object ButtonDailyRacesMoonlightSho : ButtonInterface {
    override val template = Template("components/button/daily_races_moonlight_sho_logo")
}

object ButtonEditTeam : ButtonInterface {
    override val template = Template("components/button/edit_team")
}

object ButtonFollow : ButtonInterface {
    override val template = Template("components/button/follow")
}

object ButtonFinish : ButtonInterface {
    override val template = Template("components/button/finish")
}

object ButtonGiveUp : ButtonInterface {
    override val template = Template("components/button/give_up")
}

object ButtonToHome : ButtonInterface {
    // The "To Home" button only ever appears as a dialog action on the Career Complete dialog,
    // which renders in the lower half of the screen.
    override val template = Template("components/button/to_home", region = Region.bottomHalf)
}

object ButtonCareerHome : ButtonInterface {
    override val template = Template("components/button/career_home", region = Region.bottomHalf, confidence = 0.6)
}

object ButtonCareerHomeText : ButtonInterface {
    override val template = Template("components/button/career_home_text", region = Region.bottomHalf, confidence = 0.55)
}

/**
 * The CAREER button's wordmark as it renders while a career is IN PROGRESS.
 *
 * The two templates above were both cut from the no-career-in-progress button, which shows two fixed
 * chibi trainees. Once a career is running the game swaps that art for the ACTIVE trainee's portrait
 * and adds a date pill, so neither matches: measured against a live frame on 2026-07-25 they scored
 * 0.220 and 0.283 against thresholds of 0.60 and 0.55. That is why the daily-reset lobby re-entry
 * could never click CAREER, which is the one situation where it is needed, and it cost a career at
 * turn 49.
 *
 * Cropped to the wordmark alone, starting past the portrait, because the portrait is trainee-specific
 * and a template containing it would only ever match the one trainee it was cut from. The wordmark
 * itself does not move with the trainee. Scored 1.000 on the frame it came from and at most 0.284 on
 * lobby frames with no career running, so 0.6 sits in a wide gap rather than near either population.
 */
object ButtonCareerHomeTextActive : ButtonInterface {
    override val template = Template("components/button/career_home_text_active", region = Region.bottomHalf, confidence = 0.6)
}

/** The same in-progress CAREER wordmark on the anniversary event-skinned lobby, whose busy
 * backdrop (hanging stars behind the wordmark edges) dropped [ButtonCareerHomeTextActive] to
 * 0.52-0.56 against its 0.6 bar; the 2026-07-27 17:00 reset bounce failed all four detectors on
 * exactly that frame, which is what this template was cut from (self-match 1.000). */
object ButtonCareerHomeTextEvent : ButtonInterface {
    override val template = Template("components/button/career_home_text_event", region = Region.bottomHalf, confidence = 0.6)
}

object ButtonHomeSpecialMissions : ButtonInterface {
    override val template = Template("components/button/home_special_missions")
}

object ButtonHomePresents : ButtonInterface {
    override val template = Template("components/button/home_presents")
}

object ButtonSpecialMissionsTabDaily : ButtonInterface {
    override val template = Template("components/button/special_missions_tab_daily")
}

object ButtonSpecialMissionsTabMain : ButtonInterface {
    override val template = Template("components/button/special_missions_tab_main")
}

object ButtonSpecialMissionsTabTitles : ButtonInterface {
    override val template = Template("components/button/special_missions_tab_titles")
}

object ButtonSpecialMissionsTabSpecial : ButtonInterface {
    override val template = Template("components/button/special_missions_tab_special")
}

object ButtonLater : ButtonInterface {
    override val template = Template("components/button/later")
}

object ButtonLegendRace : ButtonInterface {
    override val template = Template("components/button/legend_race")
}

object ButtonLegendRaceDisabled : ButtonInterface {
    override val template = Template("components/button/legend_race_disabled")
}

object ButtonRaceHardInactive : ButtonInterface {
    override val template = Template("components/button/race_hard_inactive")
}

object ButtonRaceHardActive : ButtonInterface {
    override val template = Template("components/button/race_hard_active")
}

object ButtonLegendRaceHomeSpecialMissions : ButtonInterface {
    override val template = Template("components/button/legend_race_special_missions")
}

object ButtonLog : ButtonInterface {
    override val template = Template("components/button/log", region = Region.bottomHalf)
}

object ButtonNext : ButtonInterface {
    override val template = Template("components/button/next", region = Region.bottomHalf)
}

object ButtonNextWithImage : ButtonInterface {
    override val template = Template("components/button/next_with_image", region = Region.bottomHalf)
}

object ButtonNextRaceEnd : ButtonInterface {
    override val template = Template("components/button/next_race_end", region = Region.bottomHalf)
}

object ButtonNo : ButtonInterface {
    override val template = Template("components/button/no", region = Region.bottomHalf)
}

object ButtonOk : ButtonInterface {
    override val template = Template("components/button/ok", region = Region.bottomHalf)
}

object ButtonOptions : ButtonInterface {
    override val template = Template("components/button/options", region = Region.bottomHalf)
}

object ButtonLearn : ButtonInterface {
    override val template = Template("components/button/learn")
}

object ButtonReset : ButtonInterface {
    override val template = Template("components/button/reset", region = Region.bottomHalf)
}

object ButtonRace : ButtonInterface {
    override val template = Template("components/button/race", region = Region.bottomHalf)
}

object ButtonRaceDayRace : ButtonInterface {
    override val template = Template("components/button/race_day_race", region = Region.bottomHalf)
}

object ButtonRaceAgain : ButtonInterface {
    override val template = Template("components/button/race_again", region = Region.bottomHalf)
}

object ButtonRaceDetails : ButtonInterface {
    override val template = Template("components/button/race_details", region = Region.bottomHalf)
}

object ButtonRaceEvents : ButtonInterface {
    override val template = Template("components/button/race_events")
}

object ButtonRaceExclamation : ButtonInterface {
    override val template = Template("components/button/race_exclamation", region = Region.bottomHalf)
}

object ButtonRaceExclamationShiftedUp : ButtonInterface {
    override val template = Template("components/button/race_exclamation_shifted_up", region = Region.middle)
}

object ButtonRaceManual : ButtonInterface {
    override val template = Template("components/button/race_manual", region = Region.bottomHalf)
}

object ButtonRaceRecommendationsCenterStage : ButtonInterface {
    override val template = Template("components/button/race_recommendations_center_stage")
}

object ButtonRaceRecommendationsPathToFame : ButtonInterface {
    override val template = Template("components/button/race_recommendations_path_to_fame")
}

object ButtonRaceRecommendationsForgeYourOwnPath : ButtonInterface {
    override val template = Template("components/button/race_recommendations_forge_your_own_path")
}

object ButtonRaceResults : ButtonInterface {
    override val template = Template("components/button/race_results")
}

object ButtonRestore : ButtonInterface {
    override val template = Template("components/button/restore")
}

object ButtonRetry : ButtonInterface {
    // Retry only appears as a dialog action (connection error, race retry) - always bottomHalf.
    override val template = Template("components/button/retry", region = Region.bottomHalf)
}

object ButtonResume : ButtonInterface {
    // Resume only appears on the Continue Career dialog (bottom-of-screen dialog action).
    override val template = Template("components/button/resume", region = Region.bottomHalf)
}

object ButtonSave : ButtonInterface {
    override val template = Template("components/button/save", region = Region.bottomHalf)
}

object ButtonSaveSchedule : ButtonInterface {
    override val template = Template("components/button/save_schedule", region = Region.bottomHalf)
}

object ButtonSaveAndExit : ButtonInterface {
    override val template = Template("components/button/save_and_exit", region = Region.bottomHalf)
}

object ButtonSeeResults : ButtonInterface {
    override val template = Template("components/button/see_results", region = Region.bottomHalf)
}

object ButtonSelectOpponent : ButtonInterface {
    // Recaptured 2026-07-06: the July patch restyled Team Showdown. The old asset was the
    // screen's blue header banner (top half); the new one is the green two-line confirm
    // button at the bottom, which is both the screen's gate and the click target. Shared
    // with TeamTrialsTask, whose screen uses the same restyled UI family.
    override val template = Template("components/button/select_opponent", region = Region.bottomHalf)
}

object ButtonSelectLegacy : ButtonInterface {
    override val template = Template("components/button/select_legacy")
}

object LabelLegacySelectTitle : ButtonInterface {
    // The "Legacy Select" title banner, top-left. A stable co-signal for the Legacy Select screen:
    // the July 2026 patch restyled the Auto-Select button enough to drop it under the match
    // threshold (0.78), so detection fell through to the greyed Next and the screen was mistaken for
    // a generic results screen. The title text is unaffected by the button restyle and by whether
    // the legacy slots are filled.
    override val template = Template("components/button/legacy_select_title", region = Region.topHalf)
}

object ButtonShop : ButtonInterface {
    override val template = Template("components/button/shop")
}

object ButtonSkip : ButtonInterface {
    override val template = Template("components/button/skip", region = Region.bottomHalf)
}

object ButtonSkipCinematic : ButtonInterface {
    override val template = Template("components/button/skip_cinematic", region = Region.bottomHalf)
}

object ButtonSkipOff : ButtonInterface {
    override val template = Template("components/button/skip_off", region = Region.persistentSkipPill)
}

object ButtonSkipOn : ButtonInterface {
    override val template = Template("components/button/skip_on", region = Region.persistentSkipPill)
}

object ButtonSkills : ButtonInterface {
    override val template = Template("components/button/skills", region = Region.bottomHalf)
}

object ButtonStartCareer : ButtonInterface {
    override val template = Template("components/button/start_career", region = Region.bottomHalf)
}

object ButtonStartCareerOffset : ButtonInterface {
    override val template = Template("components/button/start_career_offset", region = Region.bottomHalf)
}

object ButtonStartCareerRight : ButtonInterface {
    // Right-side crop of the button: the trainee chibi idles over its LEFT edge on the deck screen
    // and can pin full-button matches below threshold for a whole session (0.60 observed live while
    // this crop scored 0.89 on the same frame). Chibis never cover the right side.
    override val template = Template("components/button/start_career_right", region = Region.bottomHalf)
}

object ButtonTeamRace : ButtonInterface {
    override val template = Template("components/button/team_race")
}

object ButtonTeamTrials : ButtonInterface {
    override val template = Template("components/button/team_trials")
}

object ButtonTitleScreen : ButtonInterface {
    override val template = Template("components/button/title_screen")
}

object ButtonTryAgain : ButtonInterface {
    override val template = Template("components/button/try_again", region = Region.bottomHalf)
}

object ButtonTryAgainAlt : ButtonInterface {
    override val template = Template("components/button/try_again_alt", region = Region.bottomHalf)
}

object ButtonViewResults : ButtonInterface {
    override val template = Template("components/button/view_results", region = Region.bottomHalf)
}

/** Wide white "Close" on full-height list dialogs (Notices, the Recover TP picker). Styled
 * differently from the standard dialog Close - the standard template scores ~0.36 on it. */
object ButtonCloseWide : ButtonInterface {
    override val template = Template("components/button/close_wide", region = Region.bottomHalf)
}

/** "Reroll Sparks / Consumes 30 TP" on the career-end SPARKS screen - unique to that screen,
 * so it doubles as the screen's detection anchor. Recaptured 2026-07-10 from a live career-end
 * frame: the original capture under-scored the live button (0.783 vs the 0.80 bar) so the sparks
 * screen was never detected and the generic post-run path confirmed straight past it. The old
 * "dimmed under the Confirm Reroll dialog scores ~0.69" measurement predates the recapture -
 * re-verify the fresh-match-implies-dialog-closed inference at the first supervised reroll. */
object ButtonRerollSparks : ButtonInterface {
    override val template = Template("components/button/reroll_sparks", region = Region.bottomHalf)
}

/** The GREEN "Reroll Sparks" on the Confirm Reroll dialog. This is the SPEND action - the one
 * career-end screen where green does not mean safe-advance. Only the explicit reroll flow may
 * click it; it must never join a generic green-button policy. */
object ButtonRerollSparksConfirm : ButtonInterface {
    override val template = Template("components/button/reroll_sparks_confirm", region = Region.bottomHalf)
}

object ButtonWatchConcert : ButtonInterface {
    override val template = Template("components/button/watch_concert", region = Region.bottomHalf)
}

object ButtonRaceStrategyFront : ButtonInterface {
    override val template = Template("components/button/strategy_front_select", region = Region.middle)
}

object ButtonRaceStrategyPace : ButtonInterface {
    override val template = Template("components/button/strategy_pace_select", region = Region.middle)
}

object ButtonRaceStrategyLate : ButtonInterface {
    override val template = Template("components/button/strategy_late_select", region = Region.middle)
}

object ButtonRaceStrategyEnd : ButtonInterface {
    override val template = Template("components/button/strategy_end_select", region = Region.middle)
}

// More complex buttons

object ButtonMenuBarHomeSelected : ButtonInterface {
    // The game's bottom nav bar renders in the bottom ~10% of the screen on every menu.
    override val template = Template("components/button/menu_bar_home_selected", region = Region.bottomHalf)
}

object ButtonMenuBarHomeUnselected : ButtonInterface {
    override val template = Template("components/button/menu_bar_home_unselected")
}

object ButtonMenuBarHome : MultiStateButtonInterface {
    override val templates: List<Template> =
        listOf(
            Template("components/button/menu_bar_home_unselected"),
            Template("components/button/menu_bar_home_selected"),
        )
}

object ButtonMenuBarRaceSelected : ButtonInterface {
    override val template = Template("components/button/menu_bar_race_selected")
}

object ButtonMenuBarRaceUnselected : ButtonInterface {
    override val template = Template("components/button/menu_bar_race_unselected")
}

object ButtonMenuBarRace : MultiStateButtonInterface {
    override val templates: List<Template> =
        listOf(
            Template("components/button/menu_bar_race_unselected"),
            Template("components/button/menu_bar_race_selected"),
        )
}

object ButtonCompleteCareer : ButtonInterface {
    override val template = Template("components/button/complete_career", region = Region.bottomHalf)
}

object ButtonCareerEndSkills : ButtonInterface {
    override val template = Template("components/button/career_end_skills")
}

object ButtonCraneGame : ButtonInterface {
    override val template = Template("components/button/crane_game", region = Region.bottomHalf)
}

object ButtonCraneGameOk : ButtonInterface {
    override val template = Template("components/button/crane_game_ok", region = Region.bottomHalf)
}

object ButtonInheritance : ButtonInterface {
    override val template = Template("components/button/inheritance", region = Region.bottomHalf)
}

object ButtonPredictions : ButtonInterface {
    override val template = Template("components/button/predictions", region = Region.bottomHalf)
}

object ButtonRunners : ButtonInterface {
    override val template = Template("components/button/runners", region = Region.middle)
}

object ButtonUnityCupRace : ButtonInterface {
    override val template = Template("components/button/unitycup_race", region = Region.bottomHalf)
}

object ButtonUnityCupRaceFinal : ButtonInterface {
    override val template = Template("components/button/unitycup_race_final", region = Region.bottomHalf)
}

object ButtonUnityCupSeeAllRaceResults : ButtonInterface {
    override val template = Template("components/button/unitycup_see_all_race_results", region = Region.bottomHalf)
}

object ButtonUnityCupTeam : ButtonInterface {
    override val template = Template("components/button/unitycup_team", region = Region.bottomHalf)
}

object ButtonUnityCupWatchMainRace : ButtonInterface {
    override val template = Template("components/button/unitycup_watch_main_race", region = Region.bottomHalf)
}

object ButtonRest : ButtonInterface {
    override val template = Template("components/button/rest", region = Region.bottomHalf)
}

object ButtonRestAndRecreation : ButtonInterface {
    override val template = Template("components/button/rest_and_recreation", region = Region.bottomHalf)
}

object ButtonInfirmary : ButtonInterface {
    override val template = Template("components/button/infirmary", region = Region.bottomHalf)
}

object ButtonRecreation : ButtonInterface {
    override val template = Template("components/button/recreation", region = Region.bottomHalf)
}

object ButtonEndCareer : ButtonInterface {
    override val template = Template("components/button/end_career", region = Region.bottomHalf)
}

object ButtonRaceListFullStats : ButtonInterface {
    override val template = Template("components/button/race_list_full_stats", region = Region.middle)
}

object ButtonSkillListFullStats : ButtonInterface {
    override val template = Template("components/button/skill_list_full_stats", region = Region.topHalf)
}

object ButtonHomeFullStats : ButtonInterface {
    override val template = Template("components/button/home_full_stats", region = Region.middle)
}

object ButtonTrainingSpeed : ButtonInterface {
    override val template = Template("components/button/training_speed", region = Region.bottomHalf)
}

object ButtonTrainingStamina : ButtonInterface {
    override val template = Template("components/button/training_stamina", region = Region.bottomHalf)
}

object ButtonTrainingPower : ButtonInterface {
    override val template = Template("components/button/training_power", region = Region.bottomHalf)
}

object ButtonTrainingGuts : ButtonInterface {
    override val template = Template("components/button/training_guts", region = Region.bottomHalf)
}

object ButtonTrainingWit : ButtonInterface {
    override val template = Template("components/button/training_wit", region = Region.bottomHalf)
}

// Grand Concert restyles the five facility buttons (smaller circles, a "Lvl N" sublabel, and an
// animated performance-type badge), so the URA button-label templates above score ~0.62-0.75 there
// and every goToStat/executeTraining click silently found nothing (the bot then rested every turn).
// These variants are cut from live Grand Concert captures: the facility-name strip inside the
// circle, below the badge zone and excluding the mutable "Lvl N" line. They match the unselected
// and the enlarged selected state of their button, and nothing else on the screen.

object ButtonTrainingSpeedGrandConcert : ButtonInterface {
    override val template = Template("components/button/training_speed_grandconcert", region = Region.bottomHalf)
}

object ButtonTrainingStaminaGrandConcert : ButtonInterface {
    override val template = Template("components/button/training_stamina_grandconcert", region = Region.bottomHalf)
}

object ButtonTrainingPowerGrandConcert : ButtonInterface {
    override val template = Template("components/button/training_power_grandconcert", region = Region.bottomHalf)
}

object ButtonTrainingGutsGrandConcert : ButtonInterface {
    override val template = Template("components/button/training_guts_grandconcert", region = Region.bottomHalf)
}

object ButtonTrainingWitGrandConcert : ButtonInterface {
    override val template = Template("components/button/training_wit_grandconcert", region = Region.bottomHalf)
}

object ButtonTraining : ButtonInterface {
    override val template = Template("components/button/training", region = Region.bottomHalf)
}

object ButtonRaces : ButtonInterface {
    override val template = Template("components/button/races", region = Region.bottomHalf)
}

// Grand Concert restyles the career screen's Races button (different label weight and pink), so the
// stock template scores 0.707 on it - just under the 0.80 bar. Every voluntary race goes through a
// click on this button, so the miss made maiden races, extra races and the fan-shortfall safety net
// silently impossible: a Taiki run died in the Classic year 618 fans short of its goal while the
// bot retried a race it could never enter. Mandatory races were unaffected (they enter via the
// race-day ribbon), which is why it stayed hidden for two full careers.
object ButtonRacesGrandConcert : ButtonInterface {
    override val template = Template("components/button/races_grandconcert", region = Region.bottomHalf)
}

object ButtonHomeFansInfo : ButtonInterface {
    override val template = Template("components/button/home_fans_info", region = Region.leftHalf)
}

object ButtonSkillUp : ButtonInterface {
    override val template = Template("components/button/skill_up", region = Region.rightHalf)
}

object ButtonSkillDown : ButtonInterface {
    override val template = Template("components/button/skill_down", region = Region.rightHalf)
}

object ButtonOverwrite : ButtonInterface {
    override val template = Template("components/button/overwrite", region = Region.bottomHalf)
}

object ButtonMyAgendas : ButtonInterface {
    override val template = Template("components/button/my_agendas", region = Region.bottomHalf)
}

object ButtonRaceAgendaLoadList : ButtonInterface {
    override val template = Template("components/button/race_agenda_load_list", region = Region.rightHalf)
}

object ButtonDetails : ButtonInterface {
    // Lowered from the 0.8 engine default: on the career-end Complete Career screen the Details
    // button renders at ~0.95x scale, so the color match peaks at ~0.70 (verified via cv2 on a live
    // capture). At 0.8 the find silently failed every career, skipping the post-finale stat/fan
    // re-read and leaving [CAREER_END] ~40/stat short of the true result screen. Only used there.
    override val template = Template("components/button/details", region = Region.middle, confidence = 0.65)
}

object ButtonShopTrackblazer : ButtonInterface {
    override val template = Template("components/button/shop_trackblazer", region = Region.bottomHalf)
}

object ButtonTrainingItems : ButtonInterface {
    override val template = Template("components/button/training_items")
}

object ButtonExchange : ButtonInterface {
    override val template = Template("components/button/exchange", region = Region.bottomHalf)
}

object ButtonConfirmUse : ButtonInterface {
    override val template = Template("components/button/confirm_use", region = Region.bottomHalf)
}

object ButtonUseTrainingItems : ButtonInterface {
    override val template = Template("components/button/use_training_items", region = Region.bottomHalf)
}

object ButtonConditions : ButtonInterface {
    override val template = Template("components/button/conditions", region = Region.middle)
}

object ButtonEventProgressChevron : ButtonInterface {
    override val template = Template("components/button/event_progress_chevron")
}

// -----------------------------------------------------------------------------
// Misc automation (bot/misc/*) - components for Daily Races, Team Trials, and
// TP/RP recharge flows. See bot/misc/MiscTask.kt for the architecture.
// -----------------------------------------------------------------------------

/** The "Daily Program" tile on the Race tab (chibi character + trophy). */
object ButtonDailyProgramTile : ButtonInterface {
    override val template = Template("components/button/daily_program_tile", region = Region.bottomHalf)
}

/** The big green "Race!" button on the Race Details confirmation screen. Distinct from the bottom-nav Race tab. */
object ButtonRaceConfirm : ButtonInterface {
    override val template = Template("components/button/race_confirm", region = Region.bottomHalf)
}

/** The "Multi-Race: On" pill (green) on the Race Details screen. */
object ButtonMultiRaceOn : ButtonInterface {
    override val template = Template("components/button/multi_race_on", region = Region.bottomHalf)
}

/** The "Multi-Race: Off" pill (white/grey) on the Race Details screen. */
object ButtonMultiRaceOff : ButtonInterface {
    override val template = Template("components/button/multi_race_off", region = Region.bottomHalf)
}

/** Generic "Use" button on the Recover TP/RP item-select popup. Shared across Carats and F2P item rows. */
object ButtonUseItem : ButtonInterface {
    override val template = Template("components/button/use_item", region = Region.middle)
}

/** "Max" button on the Recover TP/RP quantity popup. */
object ButtonMax : ButtonInterface {
    override val template = Template("components/button/max", region = Region.middle)
}

/** The refresh (circular arrow) button on the Team Trials Select Opponent screen. */
object ButtonRefreshOpponents : ButtonInterface {
    override val template = Template("components/button/refresh_opponents", region = Region.bottomHalf)
}

/** The "See All Race Results" chibi-decorated button on the post-Team-Trials-match summary screen. */
object ButtonSeeAllRaceResults : ButtonInterface {
    override val template = Template("components/button/see_all_race_results", region = Region.bottomHalf)
}
