package com.steve1316.uma_android_automation.bot

/**
 * Grand Concert fan-vs-training urgency: whether a detected fan requirement must be raced this turn, or a
 * training turn may be preferred because provable slack remains before the deadline.
 *
 * Grand Concert earns performance points only by training with support, so racing the instant a fan deficit
 * appears starves point income, yet a missed requirement ends the career. Hence FAIL-CLOSED: defer only when a
 * training turn provably still leaves room; race in every uncertain, urgent, or severe-deficit case.
 */
object GrandConcertFanPolicy {
    enum class FanRaceDecision {
        NO_REQUIREMENT,

        DEFER_TO_TRAINING,

        FORCE_RACE,

        /** Missing or insufficient deadline/deficit info: race now. The production caller passes null on purpose
         * while the committed-facts inputs stay review-gated. */
        FAIL_SAFE_FORCE_RACE,
    }

    /**
     * Deferral costs one action slot and is re-evaluated every turn, so it is allowed only when skipping this turn
     * still leaves room for every race the deficit needs. [concertBehindPace] false means training has no
     * time-sensitive value to protect, so the requirement is simply cleared.
     */
    fun decide(
        fanRequirementActive: Boolean,
        turnsUntilDeadline: Int?,
        racesStillNeeded: Int?,
        concertBehindPace: Boolean,
    ): FanRaceDecision {
        if (!fanRequirementActive) return FanRaceDecision.NO_REQUIREMENT
        // Fail closed: no proven deadline or deficit means no proven slack.
        if (turnsUntilDeadline == null || racesStillNeeded == null) return FanRaceDecision.FAIL_SAFE_FORCE_RACE
        if (turnsUntilDeadline <= 0) return FanRaceDecision.FORCE_RACE
        val needed = racesStillNeeded.coerceAtLeast(0)
        // A requirement needing no more races should not still be active; race rather than defer.
        if (needed == 0) return FanRaceDecision.FORCE_RACE
        if (turnsUntilDeadline - 1 - needed < 0) return FanRaceDecision.FORCE_RACE
        if (!concertBehindPace) return FanRaceDecision.FORCE_RACE
        return FanRaceDecision.DEFER_TO_TRAINING
    }

    fun forcesRace(decision: FanRaceDecision): Boolean =
        decision == FanRaceDecision.FORCE_RACE || decision == FanRaceDecision.FAIL_SAFE_FORCE_RACE
}
