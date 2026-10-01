package com.steve1316.uma_android_automation

/** Launch stops that belong to one trainee: another trainee's career can start. Any other stop (TP, deck setup, Veterans, connection) would stop the next trainee the same way. */
internal val TRAINEE_SKIP_REASON_KEYS: Set<String> = setOf("TRAINEE_IN_DECK", "TRAINEE_ONLY_OTHER_OUTFIT", "TRAINEE_NOT_FOUND")

internal enum class UnplayableRunStep {
    SKIP,

    /** Without a rotation every run is the same trainee and would stop the same way. */
    HALT,
}

internal fun unplayableRunStep(reasonKey: String, rotationEnabled: Boolean): UnplayableRunStep? =
    when {
        reasonKey !in TRAINEE_SKIP_REASON_KEYS -> null
        rotationEnabled -> UnplayableRunStep.SKIP
        else -> UnplayableRunStep.HALT
    }

/** Stops at the first Back not found. */
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
