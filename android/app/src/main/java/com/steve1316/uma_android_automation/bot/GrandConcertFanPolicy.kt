package com.steve1316.uma_android_automation.bot

/**
 * Grand Concert fan-vs-training urgency: whether a detected fan requirement must be raced this turn, or a
 * training turn may be preferred because provable slack remains before the deadline.
 *
 * Grand Concert earns performance points only by training with support, so racing the instant a fan deficit
 * appears starves point income, yet a missed requirement ends the career. Hence FAIL-CLOSED: defer only when a
 * training turn provably still leaves room; race in every uncertain or short case.
 */
object GrandConcertFanPolicy {
    enum class FanRaceDecision {
        NO_REQUIREMENT,

        DEFER_TO_TRAINING,

        FORCE_RACE,

        /** The capacity check proves nothing (see [GrandConcertFanPressure.CapacityCheck.failSafeReason]) or payouts fell short of it: race now. */
        FAIL_SAFE_FORCE_RACE,
    }

    /**
     * Defers while every unmet requirement keeps slack, whatever the concert pace: training is the turn's normal path, so
     * the user's racing settings and agenda then apply. A short requirement still defers when today offers no race the
     * trainee can place in (C or better) but a later window turn does; otherwise it races.
     */
    fun decide(
        fanRequirementActive: Boolean,
        check: GrandConcertFanPressure.CapacityCheck,
        payoutsBelowAssumption: Boolean,
    ): FanRaceDecision {
        if (!fanRequirementActive) return FanRaceDecision.NO_REQUIREMENT
        if (check.failSafeReason != null) return FanRaceDecision.FAIL_SAFE_FORCE_RACE
        // Every requirement is met at the fans read this turn: the flag came from an older read.
        if (check.requirements.isEmpty()) return FanRaceDecision.DEFER_TO_TRAINING
        if (payoutsBelowAssumption) return FanRaceDecision.FAIL_SAFE_FORCE_RACE
        if (check.everyRequirementHasSlack) return FanRaceDecision.DEFER_TO_TRAINING
        if (check.todayCapacity == 0.0 && check.laterTurnHasCapacity) return FanRaceDecision.DEFER_TO_TRAINING
        return FanRaceDecision.FORCE_RACE
    }

    fun forcesRace(decision: FanRaceDecision): Boolean =
        decision == FanRaceDecision.FORCE_RACE || decision == FanRaceDecision.FAIL_SAFE_FORCE_RACE
}
