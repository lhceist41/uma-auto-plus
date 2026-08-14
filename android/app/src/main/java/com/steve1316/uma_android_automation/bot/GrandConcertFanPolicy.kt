package com.steve1316.uma_android_automation.bot

/**
 * The Grand Concert fan-vs-training urgency policy: a pure decision over whether a detected fan
 * requirement must be raced THIS turn, or whether a training turn may be preferred because provable
 * schedule slack remains before the requirement's deadline.
 *
 * Why a scenario-aware branch exists at all: Grand Concert generates its scenario currency
 * (performance points) only by TRAINING with support, while the game's fan requirement is met
 * overwhelmingly by the concerts themselves. A career that spends its whole early window racing the
 * instant a fan deficit appears starves point income - a real career reached only 12 of 18 songs
 * after eleven consecutive Junior fan-forced races. But the requirement is real and career-ending
 * when missed (a prior career ended 618 fans short), so this policy is FAIL-CLOSED: it defers only
 * when it can PROVE a training turn still leaves room to satisfy the requirement, and races in every
 * uncertain, urgent, or severe-deficit case.
 *
 * This object knows nothing about non-Grand-Concert scenarios; the caller gates on the scenario and
 * on the requirement being the fan arm (a trophy or goal-points requirement is never deferred).
 */
object GrandConcertFanPolicy {
    /** The outcome of one fan-requirement evaluation. */
    enum class FanRaceDecision {
        /** No fan requirement is active this turn: nothing to force. */
        NO_REQUIREMENT,

        /** Provable slack remains before the deadline; prefer a training turn for point income. */
        DEFER_TO_TRAINING,

        /** The deadline is close enough (or the deficit large enough) that the requirement must be
         * raced now. */
        FORCE_RACE,

        /** Deadline or deficit information is missing or insufficient to PROVE safe deferral, so the
         * requirement is raced now. This is the current Grand Concert reality - the goal-deadline
         * indicator is deliberately stood down for the scenario - and it is what preserves the
         * historical force-race behaviour until a live-validated deadline reader supplies the input. */
        FAIL_SAFE_FORCE_RACE,
    }

    /**
     * Decides whether a detected fan requirement may be deferred for a training turn.
     *
     * Both [turnsUntilDeadline] and [racesStillNeeded] must be known to defer: without the deadline
     * there is no proven slack, and without the race estimate a severe deficit could be deferred
     * past the point it can still be cleared. Deferral costs exactly one action slot and is
     * re-evaluated every turn while the banner persists, so it is allowed only when skipping this
     * one turn still leaves room for every race the deficit needs before the deadline.
     *
     * @param fanRequirementActive whether the game shows an unmet fan requirement this turn.
     * @param turnsUntilDeadline action turns remaining before the fan requirement must be satisfied,
     *   or null when it could not be determined.
     * @param racesStillNeeded races still estimated to clear the fan deficit, or null when unknown.
     * @param concertBehindPace whether the current concert cycle is behind its song floor - i.e.
     *   whether a training turn here actually has time-sensitive point value worth protecting. When
     *   false there is nothing to gain by not racing, so the requirement is simply cleared.
     */
    fun decide(
        fanRequirementActive: Boolean,
        turnsUntilDeadline: Int?,
        racesStillNeeded: Int?,
        concertBehindPace: Boolean,
    ): FanRaceDecision {
        if (!fanRequirementActive) return FanRaceDecision.NO_REQUIREMENT
        // Fail closed on missing inputs: no proven deadline or no proven deficit means no proven
        // slack, so never defer.
        if (turnsUntilDeadline == null || racesStillNeeded == null) return FanRaceDecision.FAIL_SAFE_FORCE_RACE
        // Deadline already reached: race now, and never through it.
        if (turnsUntilDeadline <= 0) return FanRaceDecision.FORCE_RACE
        val needed = racesStillNeeded.coerceAtLeast(0)
        // A requirement that needs no more races should not still be active; treat defensively as a
        // race rather than a deferral.
        if (needed == 0) return FanRaceDecision.FORCE_RACE
        // Deferring spends one slot. Safe only if every needed race still fits afterward:
        // (turnsUntilDeadline - 1) >= needed.
        if (turnsUntilDeadline - 1 - needed < 0) return FanRaceDecision.FORCE_RACE
        // Slack is proven, but only defer when a training turn is actually worth protecting.
        if (!concertBehindPace) return FanRaceDecision.FORCE_RACE
        return FanRaceDecision.DEFER_TO_TRAINING
    }

    /** True when [decision] resolves to any race (forced, deadline-urgent, or fail-safe). */
    fun forcesRace(decision: FanRaceDecision): Boolean =
        decision == FanRaceDecision.FORCE_RACE || decision == FanRaceDecision.FAIL_SAFE_FORCE_RACE
}
