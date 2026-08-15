package com.steve1316.uma_android_automation.bot

/**
 * The Grand Concert career calendar expressed as which turns can actually host a fan-earning race.
 *
 * This is the "calendar-aware slack" term a fan-goal deadline model needs. The naive assumption that
 * every remaining turn before a deadline is a free race opportunity is wrong: the pre-debut window
 * and the finale off-turns host no enterable regular races, so counting them as slack would let a
 * deferral defer through a deadline it cannot actually still meet. This object answers only the
 * factual question "is turn N a raceable career turn, and how many raceable turns lie in a window".
 * It makes no defer/force decision and reads no pixels.
 *
 * It is deliberately NOT wired into a live deferral decision. The fan-goal deadline this slack is
 * measured against now ships in committed data ([GrandConcertFanFacts]), and [GrandConcertFanPressure]
 * consumes this calendar for the raceable-slack term, but the production policy inputs stay review-
 * gated to null, so [GrandConcertFanPolicy] still fail-safe races. Today this model is exercised only
 * as [GC_FAN] telemetry.
 *
 * Raceability source: the game's own `single_mode_turn.race_entry_type` for the Grand Concert turn
 * set (turn_set_id 3), read from `master.mdb`. It marks EVERY turn 12..72 as race-entry legal
 * (`race_entry_type = 1`), Summer (37-40, 61-64) and concert turns (24/36/48/60/72) included; only
 * the pre-debut turns 1-11 and the finale off-turns (73/75/77) are non-raceable. So the raceable
 * career window is exactly 12..72. An earlier version wrongly excluded Summer here; the game data
 * disproves that (it under-counted slack, so the error was conservative, but it was still wrong).
 * Note: raceable here is base race-entry legality, NOT guaranteed free slack after mandatory actions.
 */
object GrandConcertRaceCalendar {
    /** First race-entry-legal turn; turns 1-11 are pre-debut (`race_entry_type = 0`). */
    const val FIRST_RACEABLE_TURN = 12

    /** Last ordinary career turn. Turns 12..72 are all race-entry legal; the finale off-turns above
     * it (73/75/77) are not, and the finale races (74/76/78) sit outside the fan-goal career window. */
    const val LAST_CAREER_TURN = 72

    /** Whether a fan-earning regular race can be entered on [turn]. Per the Grand Concert turn table,
     * every turn in the career window is race-entry legal, so this is exactly the window check. */
    fun isRaceableTurn(turn: Int): Boolean = turn in FIRST_RACEABLE_TURN..LAST_CAREER_TURN

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
