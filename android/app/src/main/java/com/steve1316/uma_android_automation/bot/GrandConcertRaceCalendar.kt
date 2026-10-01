package com.steve1316.uma_android_automation.bot

/**
 * Which Grand Concert turns can host a fan-earning race. Not wired into a live deferral decision; exercised only
 * as [GC_FAN] telemetry. Source: `single_mode_turn.race_entry_type` (turn_set_id 3) in `master.mdb` marks turns
 * 12..72 race-entry legal, Summer and concert turns included; pre-debut 1-11 and finale off-turns 73/75/77 are not.
 * This is base legality, not guaranteed free slack after mandatory actions.
 */
object GrandConcertRaceCalendar {
    const val FIRST_RACEABLE_TURN = 12

    /** Finale off-turns above this are not raceable; finale races (74/76/78) sit outside the fan-goal window. */
    const val LAST_CAREER_TURN = 72

    fun isRaceableTurn(turn: Int): Boolean = turn in FIRST_RACEABLE_TURN..LAST_CAREER_TURN

    /** Raceable career turns in (afterTurn, throughTurn], inclusive of throughTurn. Zero when the window is empty or inverted. */
    fun raceableTurnsBetween(afterTurn: Int, throughTurn: Int): Int {
        if (throughTurn <= afterTurn) return 0
        return ((afterTurn + 1)..throughTurn).count { isRaceableTurn(it) }
    }
}
