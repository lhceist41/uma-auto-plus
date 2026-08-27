package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.FinalConfirmationMode

/**
 * Pure decision policy for the Normal Career Final Confirmation mode gate (N0).
 *
 * The Final Confirmation screen defaults to the LAST-STARTED mode, so it can open on either the
 * Normal Career or the Independent Training tab. A career-launch flow must positively verify Normal
 * Career before the irreversible Start Career press; "not Independent Training" is not equivalent to
 * "Normal Career". Independent Training earns exactly ONE corrective tap to the Normal Career tab
 * followed by a fresh re-read; anything else fails closed.
 *
 * The pixel classification lives in [FinalConfirmationMode] / classifyFinalConfirmationMode; this
 * object holds only the enum-level flow decisions so they can be pinned by JUnit without a device.
 * The runtime is a thin adapter: it reads the mode, applies [decide]; on [Decision.CorrectToNormalCareer]
 * it taps once, re-reads a FRESH capture, and applies [decideAfterCorrection] -- which never asks for
 * another correction, so the corrective tap is structurally bounded to one.
 */
internal object FinalConfirmationModeGate {
    /** Corrective tab taps allowed per Final Confirmation attempt. Bounded to one; there is no retry loop. */
    const val MAX_MODE_CORRECTION_TAPS = 1

    /** The flow action a caller must take for an observed mode. */
    sealed class Decision {
        /** Normal Career positively verified; continue through the existing gates to Start Career. */
        object Proceed : Decision()

        /** Independent Training verified; tap the Normal Career tab once, then re-verify with a fresh capture. */
        object CorrectToNormalCareer : Decision()

        /** Fail closed with a named reason; the final Start Career press is never reached. */
        data class Refuse(val reason: String) : Decision()
    }

    /** Only NORMAL_CAREER_VERIFIED may authorize the final Start Career press. */
    fun finalStartCareerAuthorized(mode: FinalConfirmationMode): Boolean =
        mode == FinalConfirmationMode.NORMAL_CAREER_VERIFIED

    /** First-look decision from the mode observed when the screen is reached. */
    fun decide(mode: FinalConfirmationMode): Decision =
        when (mode) {
            FinalConfirmationMode.NORMAL_CAREER_VERIFIED -> Decision.Proceed
            FinalConfirmationMode.INDEPENDENT_TRAINING_VERIFIED -> Decision.CorrectToNormalCareer
            FinalConfirmationMode.MODE_UNRECOGNIZED ->
                Decision.Refuse(
                    "Reached the Final Confirmation screen but neither career-mode tab read as positively " +
                        "selected (MODE_UNRECOGNIZED); Start Career refused so no TP is spent on an unverified mode.",
                )
        }

    /**
     * Decision after the single corrective tab tap and a FRESH re-read. Never returns
     * [Decision.CorrectToNormalCareer]: the correction is one-shot, so a still-Independent or
     * unrecognized fresh read fails closed rather than tapping again.
     */
    fun decideAfterCorrection(freshMode: FinalConfirmationMode): Decision =
        when (freshMode) {
            FinalConfirmationMode.NORMAL_CAREER_VERIFIED -> Decision.Proceed
            FinalConfirmationMode.INDEPENDENT_TRAINING_VERIFIED ->
                Decision.Refuse(
                    "Final Confirmation was on Independent Training and the single corrective tap to the Normal " +
                        "Career tab did not switch it (MODE_SWITCH_FAILED); Start Career refused so no TP is spent on the wrong mode.",
                )
            FinalConfirmationMode.MODE_UNRECOGNIZED ->
                Decision.Refuse(
                    "After the single corrective tap to the Normal Career tab the Final Confirmation mode still " +
                        "did not read as Normal Career (MODE_UNRECOGNIZED); Start Career refused so no TP is spent on an unverified mode.",
                )
        }
}
