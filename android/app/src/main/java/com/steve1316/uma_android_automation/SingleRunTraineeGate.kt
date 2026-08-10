package com.steve1316.uma_android_automation

/**
 * Pure launch-identity policy for a single (non-rotation) career launch.
 *
 * A configured trainee is an EXPECTATION, never observed identity. The career token is labelled
 * from the configured `general.appliedPresetTrainee` (see RunConfigSnapshot / buildCareerFinalizeToken),
 * so if a launch advances into a career without factually selecting that trainee at the roster
 * boundary, the game's sticky preselection runs under a token that claims someone else. That is the
 * 2026-08-10 mislabel: a queued rotation-off URA run (enableRunQueue=true) launched the game's last
 * trainee (Taiki Shuttle) under a Symboli-labelled token, because neither a single-run target nor a
 * rotation target was ever armed.
 *
 * This object holds the two pure decisions that keep that impossible, split out so they can be
 * pinned by JUnit without a device:
 *   - [resolveTarget]/[resolveExcludes]: which trainee THIS launch must roster-verify.
 *   - [mustFailClosed]: whether a Start-Career-bearing screen must refuse to advance because the
 *     target has not been verified yet on this attempt.
 */
internal object SingleRunTraineeGate {
    /**
     * The trainee a launch must roster-verify before it may start a career, or "" when identity is
     * managed elsewhere (rotation targets `queueState.currentTrainee`) or nothing is being launched
     * (a finalize-to-home pass ends a career and selects no trainee).
     *
     * Precedence: an explicit caller-supplied target wins (Game.kt's manual single-run path); else,
     * when rotation is off, the applied preset is the launch identity for queue and non-queue runs
     * alike; rotation launches leave it blank so the rotation machinery keeps ownership.
     *
     * @param finalizeToHome True when the navigation ends a career (no launch, no trainee to verify).
     * @param passedTarget The trainee the caller passed explicitly (blank for the queue launch paths).
     * @param rotationEnabled Whether trainee rotation is on for this session.
     * @param appliedPresetTrainee The `general.appliedPresetTrainee` that labels the career token.
     */
    fun resolveTarget(
        finalizeToHome: Boolean,
        passedTarget: String,
        rotationEnabled: Boolean,
        appliedPresetTrainee: String,
    ): String =
        when {
            finalizeToHome -> ""
            passedTarget.isNotBlank() -> passedTarget
            rotationEnabled -> ""
            else -> appliedPresetTrainee
        }

    /**
     * Sibling-outfit names to skip for the resolved target, tracking [resolveTarget] branch for
     * branch so the excludes always belong to the trainee actually being verified.
     */
    fun resolveExcludes(
        finalizeToHome: Boolean,
        passedTarget: String,
        passedExcludes: String,
        rotationEnabled: Boolean,
        appliedPresetExcludes: String,
    ): String =
        when {
            finalizeToHome -> ""
            passedTarget.isNotBlank() -> passedExcludes
            rotationEnabled -> ""
            else -> appliedPresetExcludes
        }

    /**
     * True when a Start-Career-bearing screen (Legacy Select, Support Deck, Pre-Run Confirmation)
     * must fail closed: a target is armed but it has not been roster-verified on THIS launch
     * attempt. Verification is per attempt and is reset when navigation begins, so a trainee a
     * previous run selected cannot vouch for this one.
     */
    fun mustFailClosed(target: String, verifiedThisAttempt: Boolean): Boolean = target.isNotBlank() && !verifiedThisAttempt
}
