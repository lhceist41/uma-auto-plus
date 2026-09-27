package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.bot.ConnectionLostException
import com.steve1316.uma_android_automation.bot.ConnectionOutageBudget
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.ButtonCloseWide
import com.steve1316.uma_android_automation.components.ButtonInterface
import com.steve1316.uma_android_automation.components.ButtonOk
import com.steve1316.uma_android_automation.components.ButtonRetry

/**
 * The titled game dialogs the career-launch navigator handles itself between runs, by the title
 * `DialogUtils.getTitle` resolves (the titles of the same-named objects in `components/Dialog.kt`).
 * The in-career `DialogHandler` is not used there: its connection route waits and throws
 * InterruptedException, which the navigation deadline would report as a hang, and it throws on the
 * purchase dialogs.
 */
internal enum class BetweenRunDialog(val title: String) {
    NOTICES("Notices"),
    DATE_CHANGED("Date Changed"),
    FOLLOW_TRAINER("Follow Trainer"),
    CONNECTION_ERROR("Connection Error"),
    DOWNLOAD_ERROR("Download Error"),
    SESSION_ERROR("Session Error"),
    PURCHASE_CARATS("Purchase Carats"),
    AGE_CONFIRMATION("Age Confirmation"),
    ;

    companion object {
        fun forTitle(title: String?): BetweenRunDialog? = entries.firstOrNull { it.title == title }
    }
}

/**
 * The dialog on screen, or null. [bannerUp] is the OCR-free title-banner check and [title] the
 * title OCR, which runs only under a banner. Off once the launch has passed Start Career: the
 * campaign's DialogHandler owns dialogs from there.
 */
internal fun readBetweenRunDialog(careerLaunchInitiated: Boolean, bannerUp: () -> Boolean, title: () -> String?): BetweenRunDialog? {
    if (careerLaunchInitiated || !bannerUp()) return null
    return BetweenRunDialog.forTitle(
        try {
            title()
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
            null
        },
    )
}

/**
 * What the navigator does about one [BetweenRunDialog]. [taps] are the only controls a step may
 * press, first match wins: Close, OK, Cancel or Retry, never a spend, a launch or Title Screen.
 */
internal sealed class BetweenRunDialogStep(val taps: List<ButtonInterface>) {
    /** The wide list-dialog Close first, as `DialogNotices.close` does. */
    data object CloseNotices : BetweenRunDialogStep(listOf(ButtonCloseWide, ButtonClose))

    data object ConfirmDateChanged : BetweenRunDialogStep(listOf(ButtonOk))

    /** Cancel, as `DialogFollowTrainer.close` does. */
    data object CancelFollowTrainer : BetweenRunDialogStep(listOf(ButtonCancel))

    /** Wait [waitMs], then Retry. [attempt] counts from 1 within the outage. */
    data class Retry(val waitMs: Long, val attempt: Int) : BetweenRunDialogStep(listOf(ButtonRetry))

    /** Stop the navigation with [reasonKey], tapping nothing. */
    data class Fail(val reasonKey: String) : BetweenRunDialogStep(emptyList())
}

/**
 * How long before the navigation deadline a lost connection gives up on its own, so the queue
 * reports the connection instead of the deadline reporting a navigation that took too long.
 */
internal const val CONNECTION_DEADLINE_MARGIN_MS = 60_000L

/**
 * The step for [dialog]. A connection or download error is ridden out like one mid-career, on the
 * same [ConnectionOutageBudget] rules ([onConnectionError] records the error), but only while the
 * wait still fits before the navigation deadline, [msBeforeDeadline] from now. A session error
 * needs the game's title screen and the purchase screens spend real money, so those stop.
 */
internal fun planBetweenRunDialog(dialog: BetweenRunDialog, onConnectionError: () -> ConnectionOutageBudget.Decision, msBeforeDeadline: Long): BetweenRunDialogStep =
    when (dialog) {
        BetweenRunDialog.NOTICES -> BetweenRunDialogStep.CloseNotices
        BetweenRunDialog.DATE_CHANGED -> BetweenRunDialogStep.ConfirmDateChanged
        BetweenRunDialog.FOLLOW_TRAINER -> BetweenRunDialogStep.CancelFollowTrainer
        BetweenRunDialog.CONNECTION_ERROR -> retryOrFail(onConnectionError(), msBeforeDeadline) ?: BetweenRunDialogStep.Fail(reasonKey = "CONNECTION_LOST")
        BetweenRunDialog.DOWNLOAD_ERROR -> retryOrFail(onConnectionError(), msBeforeDeadline) ?: BetweenRunDialogStep.Fail(reasonKey = "DOWNLOAD_FAILED")
        BetweenRunDialog.SESSION_ERROR -> BetweenRunDialogStep.Fail(reasonKey = "SESSION_EXPIRED")
        BetweenRunDialog.PURCHASE_CARATS, BetweenRunDialog.AGE_CONFIRMATION -> BetweenRunDialogStep.Fail(reasonKey = "PURCHASE_PROMPT")
    }

/**
 * A connection ride-out: Connection or Download Error, or the game's loading screen (the reconnect
 * after a Retry). The outage budget, the loading limit and the navigation deadline bound it, so its
 * repeats are no stuck screen and no lack of progress.
 */
internal fun isConnectionRideOut(state: CareerLaunchNavigator.LaunchScreenState, dialog: BetweenRunDialog?): Boolean =
    state == CareerLaunchNavigator.LaunchScreenState.GAME_LOADING ||
        (state == CareerLaunchNavigator.LaunchScreenState.DIALOG_HANDLED && (dialog == BetweenRunDialog.CONNECTION_ERROR || dialog == BetweenRunDialog.DOWNLOAD_ERROR))

/**
 * How long one stretch of loading is waited out between runs: the in-career [Game.LOADING_HARD_LIMIT_MS],
 * cut short so the queue reports the lost connection before the navigation deadline would.
 */
internal fun betweenRunLoadingLimitMs(msBeforeDeadline: Long): Long = minOf(Game.LOADING_HARD_LIMIT_MS, msBeforeDeadline - CONNECTION_DEADLINE_MARGIN_MS).coerceAtLeast(0L)

/**
 * Waits while [isLoading] holds, on the in-career [Game.awaitLoadingCleared] rules, tapping nothing.
 * [isLoading] reports false once a dialog banner is up, so the navigator handles an error dialog on
 * its next look, sooner than the in-career soft check. Returns null when the loading ended, or the
 * lost-connection failure once it lasted [limitMs].
 */
internal fun waitOutBetweenRunLoading(isLoading: () -> Boolean, now: () -> Long, pause: () -> Unit, limitMs: Long): BetweenRunDialogStep.Fail? =
    try {
        Game.awaitLoadingCleared(isLoading, now, pause, handleErrorDialog = { false }, hardLimitMs = limitMs)
        null
    } catch (_: ConnectionLostException) {
        // A subclass of InterruptedException: caught here so the navigation deadline never sees it.
        BetweenRunDialogStep.Fail(reasonKey = "CONNECTION_LOST")
    }

/** A Retry when the budget allows one that still fits before the deadline, else null. */
private fun retryOrFail(decision: ConnectionOutageBudget.Decision, msBeforeDeadline: Long): BetweenRunDialogStep.Retry? =
    when (decision) {
        is ConnectionOutageBudget.Decision.GiveUp -> null
        is ConnectionOutageBudget.Decision.Retry ->
            if (decision.waitMs + CONNECTION_DEADLINE_MARGIN_MS > msBeforeDeadline) null else BetweenRunDialogStep.Retry(decision.waitMs, decision.attempt)
    }
