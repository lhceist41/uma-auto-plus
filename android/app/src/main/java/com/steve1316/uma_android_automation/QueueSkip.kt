package com.steve1316.uma_android_automation

/**
 * Launch stops that belong to one trainee: the game will not start her career, but another trainee's
 * can. Every other launch stop (TP, deck setup, Veterans, the connection) would stop the next trainee
 * the same way. Nothing is spent before any of these: each stops before Start Career.
 */
internal val TRAINEE_SKIP_REASON_KEYS: Set<String> = setOf("TRAINEE_IN_DECK", "TRAINEE_ONLY_OTHER_OUTFIT", "TRAINEE_NOT_FOUND")

/** What a queue does with a run whose launch stopped on one of [TRAINEE_SKIP_REASON_KEYS]. */
internal enum class UnplayableRunStep {
    /** Record the run as skipped with its reason and go on with the next trainee in the rotation. */
    SKIP,

    /** Without a rotation every run is the same trainee and would stop the same way: halt with the reason. */
    HALT,
}

/** The step for a launch that stopped on [reasonKey], or null when the stop is not a trainee's own. */
internal fun unplayableRunStep(reasonKey: String, rotationEnabled: Boolean): UnplayableRunStep? =
    when {
        reasonKey !in TRAINEE_SKIP_REASON_KEYS -> null
        rotationEnabled -> UnplayableRunStep.SKIP
        else -> UnplayableRunStep.HALT
    }

/**
 * Leaves a launch that stopped before Start Career by pressing the game's Back until Home shows, at
 * most [maxBacks] times, so the next trainee's launch starts from Home through Trainee Select. Stops at
 * the first Back not found. True once Home is showing.
 */
internal fun backOutOfLaunch(isHome: () -> Boolean, pressBack: () -> Boolean, settle: () -> Unit, maxBacks: Int): Boolean {
    repeat(maxBacks) {
        if (isHome()) return true
        if (!pressBack()) return false
        settle()
    }
    return isHome()
}

/** Back presses from the furthest launch screen a trainee stop leaves (Final Confirmation) to Home, with one to spare. */
internal const val LAUNCH_BACK_OUT_MAX_PRESSES = 6
