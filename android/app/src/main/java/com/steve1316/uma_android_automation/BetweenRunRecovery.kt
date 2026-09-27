package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.CareerLaunchNavigator.LaunchScreenState

/**
 * How one between-run navigation gets the game back by itself: Title Screen on a Session Error, a
 * tap through the title screen, and one relaunch of the game when no screen is recognised.
 *
 * Title Screen and the relaunch need no career in flight, from what the queue itself knows: a cold
 * Start it made on Home without re-entering an interrupted career ([coldStartOnHome]), or a
 * finished career ([previousCareerComplete]) whose end screens have reached Home. Never once the
 * launch has passed Start Career, once the game has shown Continue Career or a career-end screen
 * since Home, on the queue's final pass to Home ([finalizeToHome]) or while the campaign drives the
 * navigator inside its own career ([campaignOwnsCareer]). Each happens at most once per navigation;
 * one instance lives for the whole navigation, across the launch starting over.
 */
internal class BetweenRunRecovery(
    private val coldStartOnHome: Boolean,
    private val previousCareerComplete: Boolean,
    private val finalizeToHome: Boolean,
    private val campaignOwnsCareer: Boolean,
) {
    var titleScreenTapped = false
        private set
    var relaunched = false
        private set

    /** True from Title Screen, a relaunch or a tap on the title until Home is back: the game is loading its way back. */
    var gameComingBack = false
        private set

    private var homeSeen = false
    private var careerEndSinceHome = false
    private var continueCareerSeen = false
    private var lastTapToStartMs: Long? = null

    /** Records each recognised screen. */
    fun onScreen(state: LaunchScreenState) {
        when (state) {
            LaunchScreenState.HOME_SCREEN -> {
                homeSeen = true
                careerEndSinceHome = false
                gameComingBack = false
            }
            LaunchScreenState.CONTINUE_CAREER_DIALOG -> continueCareerSeen = true
            in CAREER_END_STATES -> careerEndSinceHome = true
            else -> Unit
        }
    }

    fun noCareerInFlight(careerLaunchInitiated: Boolean): Boolean =
        !careerLaunchInitiated &&
            !continueCareerSeen &&
            !careerEndSinceHome &&
            !finalizeToHome &&
            !campaignOwnsCareer &&
            (coldStartOnHome || (previousCareerComplete && homeSeen))

    fun mayTapTitleScreen(careerLaunchInitiated: Boolean): Boolean = !titleScreenTapped && noCareerInFlight(careerLaunchInitiated)

    fun tappedTitleScreen() {
        titleScreenTapped = true
        gameComingBack = true
    }

    /** The title stays up while the game logs in after a tap, so a second tap waits [TAP_TO_START_COOLDOWN_MS]. */
    fun mayTapToStart(nowMs: Long): Boolean = lastTapToStartMs.let { it == null || nowMs - it >= TAP_TO_START_COOLDOWN_MS }

    fun tappedToStart(nowMs: Long) {
        lastTapToStartMs = nowMs
        gameComingBack = true
    }

    /** Unknown frames in a row that stop the navigation: more while the game loads its way back through its title. */
    fun unknownScreenLimit(normal: Int): Int = if (gameComingBack) maxOf(normal, COMING_BACK_UNKNOWN_LIMIT) else normal

    /** What to do when [unknownScreenLimit] is reached. A relaunch is spent here, before the caller performs it. */
    fun onUnknownScreenLimit(careerLaunchInitiated: Boolean): UnknownScreenLimitStep =
        when {
            relaunched -> UnknownScreenLimitStep.GAME_UNRECOVERABLE
            noCareerInFlight(careerLaunchInitiated) -> {
                relaunched = true
                gameComingBack = true
                UnknownScreenLimitStep.RELAUNCH
            }
            else -> UnknownScreenLimitStep.STOP
        }

    companion object {
        const val TAP_TO_START_COOLDOWN_MS = 20_000L

        // Unmeasured: about 2 minutes of unknown frames at the navigator's pace, for the splash, the
        // Notice to Minors and the logo a relaunched game shows before its title (20 to 30 s on MuMu).
        const val COMING_BACK_UNKNOWN_LIMIT = 40

        /** The career-end screens, from the career summary to Career Complete. */
        val CAREER_END_STATES =
            setOf(
                LaunchScreenState.CAREER_SUMMARY,
                LaunchScreenState.CAREER_END_SKILL_SCREEN,
                LaunchScreenState.COMPLETE_CAREER_CONFIRMATION,
                LaunchScreenState.SPARKS_SCREEN,
                LaunchScreenState.CONFIRM_REROLL_DIALOG,
                LaunchScreenState.SPARKS_REROLLED_RESULT,
                LaunchScreenState.SPARK_SELECTION_INTRO,
                LaunchScreenState.SPARK_SELECTION_PAGER,
                LaunchScreenState.SPARK_SELECTION_CONFIRMATION,
                LaunchScreenState.SPARKS_KEEP_CONFIRMATION,
                LaunchScreenState.CAREER_COMPLETE_DIALOG,
            )
    }
}

/** At the unknown-screen limit: relaunch the game, stop as unrecoverable after a relaunch, or stop as a screen the bot could not pass. */
internal enum class UnknownScreenLimitStep { RELAUNCH, GAME_UNRECOVERABLE, STOP }
