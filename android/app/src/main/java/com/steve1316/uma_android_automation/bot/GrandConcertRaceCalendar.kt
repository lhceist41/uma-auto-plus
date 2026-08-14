package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.GameDate

/**
 * The Grand Concert career calendar expressed as which turns can actually host a fan-earning race.
 *
 * This is the "calendar-aware slack" term a fan-goal deadline model needs. The naive assumption that
 * every remaining turn before a deadline is a free race opportunity is wrong: Summer camp, the
 * pre-debut window, and the finale season host no enterable regular races, so counting them as slack
 * would let a deferral defer through a deadline it cannot actually still meet. This object answers
 * only the factual question "is turn N a raceable career turn, and how many raceable turns lie in a
 * window", entirely from structured [GameDate] semantics. It makes no defer/force decision and reads
 * no pixels.
 *
 * It is deliberately NOT wired into a live deferral decision. The fan-goal deadline this slack would
 * be measured against is not obtainable from current local data (fan-count goals with deadlines are
 * absent from the objective master data and the compiler, and the goal-deadline OCR is stood down
 * for Grand Concert), so [GrandConcertFanPolicy] still fail-safe races. This model is the proven,
 * reusable piece for when that deadline exists; today it is exercised only as [GC_FAN] telemetry.
 *
 * Turn semantics (all structured, none OCR'd):
 * - pre-debut: turns below [FIRST_RACEABLE_TURN] host no regular races (GameDate.bIsPreDebut = day < 12);
 * - Summer camp: GLOBAL_CONFIRMED turns 37-40 and 61-64 host no races ([GameDate.isSummer]);
 * - finale: turns above [LAST_CAREER_TURN] are the finals season, no fan-goal races (GameDate.bIsFinaleSeason = day > 72);
 * - concert turns (24/36/48/60/72) stay ordinary raceable turns: a concert does not consume the
 *   turn (an observed career's decision trace carried normal TRAIN/REST actions on turns 24/36/48).
 */
object GrandConcertRaceCalendar {
    /** First turn a regular race can be entered; below it GameDate.bIsPreDebut is true. */
    const val FIRST_RACEABLE_TURN = 12

    /** Last ordinary career turn; turns above it are the finals season (GameDate.bIsFinaleSeason). */
    const val LAST_CAREER_TURN = 72

    /** Whether a fan-earning regular race can be entered on [turn]: inside the career window and not
     * a Summer-camp turn. */
    fun isRaceableTurn(turn: Int): Boolean = turn in FIRST_RACEABLE_TURN..LAST_CAREER_TURN && !GameDate.isSummer(turn)

    /**
     * The number of raceable career turns strictly after [afterTurn] and up to and including
     * [throughTurn]. Zero when the window is empty or inverted.
     *
     * This is the raceable-slack term: a deadline model passes the current turn and the goal's
     * deadline turn and gets back how many real race opportunities lie between them, not the raw
     * turn count. Whether the deadline turn itself is usable is the caller's convention, expressed
     * by what it passes as [throughTurn]; this function counts [throughTurn] inclusively.
     */
    fun raceableTurnsBetween(afterTurn: Int, throughTurn: Int): Int {
        if (throughTurn <= afterTurn) return 0
        return ((afterTurn + 1)..throughTurn).count { isRaceableTurn(it) }
    }
}
