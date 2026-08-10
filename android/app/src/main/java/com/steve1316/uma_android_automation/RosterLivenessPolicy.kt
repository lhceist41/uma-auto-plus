package com.steve1316.uma_android_automation

/**
 * Pure decisions for the cold-start Trainee Select liveness net.
 *
 * The 2026-08-10 cold-start failure: from a popup-heavy Home, a pre-career scenario/event intro Skip
 * button was misread as CINEMATIC_INTRO and latched `careerLaunchInitiated`, disabling the roster and
 * scenario detectors; the real Scenario Select / Trainee Select frames then fell through to
 * POST_RUN_RESULTS, whose generic Next tap advanced the game's sticky trainee. The landed identity
 * backstops stopped the wrong launch at Legacy Select, but the launch failed instead of selecting the
 * intended trainee.
 *
 * These predicates are split out so the state-machine wiring in CareerLaunchNavigator can be pinned by
 * JUnit without a device. They never relax the identity invariant: verification at Trainee Select
 * remains authoritative, and a launch that cannot recognize the roster fails closed.
 */
internal object RosterLivenessPolicy {
    /**
     * True when THIS launch owes a Trainee Select verification: rotation is on OR a single-run target
     * is armed, and a career is actually being launched. A finalize-to-home pass selects no trainee.
     */
    fun rosterSelectionPending(finalizeToHome: Boolean, rotationEnabled: Boolean, singleRunTargetArmed: Boolean): Boolean =
        !finalizeToHome && (rotationEnabled || singleRunTargetArmed)

    /**
     * Whether the `careerLaunchInitiated` latch may fire from a proxy state (PRE_RUN_CONFIRMATION,
     * CINEMATIC_INTRO, QUICK_MODE_PROMPT, TAP_TO_CONTINUE). It must NOT fire while a Trainee Select is
     * still owed: the landed Legacy/Deck/Pre-Run identity backstops make passing Start Career without
     * verification impossible, so such a proxy state seen pre-verification is a false positive (a
     * pre-career intro Skip button) that would otherwise disable the roster + scenario detectors.
     */
    fun mayLatchCareerLaunch(rosterSelectionPending: Boolean): Boolean = !rosterSelectionPending

    /**
     * Whether the roster-liveness expectation is active: the launch owes a roster verification, has
     * entered the career-creation flow (past Home, so the between-run results Next is never
     * suppressed), and has not started the career. While active, a POST_RUN_RESULTS is re-probed as a
     * churn-misread roster/scenario rather than tapped through.
     */
    fun expectationActive(launchFlowEntered: Boolean, rosterSelectionPending: Boolean, careerLaunchInitiated: Boolean): Boolean =
        launchFlowEntered && rosterSelectionPending && !careerLaunchInitiated

    /** Whether the bounded settle-and-reprobe budget is exhausted and the expectation must fail closed. */
    fun expectationTimedOut(reprobes: Int, maxReprobes: Int): Boolean = reprobes >= maxReprobes
}
