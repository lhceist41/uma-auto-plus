package com.steve1316.uma_android_automation.bot

/**
 * What a run start can leave behind when a stop or restart interrupted a career mid-decision. The
 * campaign's dialog handler confirms every one of these dialogs, because it normally meets them right
 * after its own decision to open them; at a run start that decision is gone, so they are cancelled or
 * backed out of instead, and the campaign decides again from the training menu.
 *
 * @property dialogTitle The known dialog title on screen, or null.
 * @property grandConcertDialog A Grand Concert lesson confirmation (Learn or Schedule) is up, by its technique or song pill.
 * @property grandConcertLessonList The Grand Concert lesson list is on screen.
 * @property raceList The in-career Race List is on screen (its Full Stats button).
 */
internal data class ResumeScreen(
    val dialogTitle: String? = null,
    val grandConcertDialog: Boolean = false,
    val grandConcertLessonList: Boolean = false,
    val raceList: Boolean = false,
    val cancel: Boolean = false,
    val back: Boolean = false,
    val learn: Boolean = false,
)

/** The only presses a run start may make here: neither commits anything. */
internal enum class ResumeSettleAction(val button: String) {
    CANCEL("Cancel"),
    BACK("Back"),
}

internal data class ResumeSettleStep(val action: ResumeSettleAction, val screen: String)

/** Confirmations whose OK spends the turn, enters a race or uses items. */
internal val TURN_COMMIT_DIALOG_TITLES: Set<String> = setOf("Rest", "Rest & Recreation", "Recreation", "Infirmary", "Race Details", "Confirm Use")

/** The step for [screen], or null when the campaign may take the screen as it is. */
internal fun resumeSettleStep(screen: ResumeScreen): ResumeSettleStep? =
    when {
        screen.dialogTitle in TURN_COMMIT_DIALOG_TITLES && screen.cancel -> ResumeSettleStep(ResumeSettleAction.CANCEL, "the ${screen.dialogTitle} confirmation")
        // Four dialogs share this title; only the skill Learn one carries Learn, and a Grand Concert
        // lesson confirmation resolves to it too.
        screen.dialogTitle == "Confirmation" && screen.learn && screen.cancel -> ResumeSettleStep(ResumeSettleAction.CANCEL, "a Learn confirmation")
        // The game's Options dialog can show a green section header bar where the lesson pill sits.
        screen.grandConcertDialog && screen.cancel && screen.dialogTitle != "Options" -> ResumeSettleStep(ResumeSettleAction.CANCEL, "a Grand Concert lesson confirmation")
        screen.raceList && screen.back -> ResumeSettleStep(ResumeSettleAction.BACK, "the Race List")
        screen.grandConcertLessonList && screen.back -> ResumeSettleStep(ResumeSettleAction.BACK, "the Grand Concert lesson list")
        else -> null
    }

internal const val MAX_RESUME_SETTLE_ACTIONS = 3

/**
 * Cancels or backs out of what [readScreen] shows, one press at a time, until nothing is left to
 * settle, a press is not found, the training menu is back, or [MAX_RESUME_SETTLE_ACTIONS] presses were
 * made. Returns the number of presses.
 */
internal fun settleResumedCareer(readScreen: () -> ResumeScreen, press: (ResumeSettleStep) -> Boolean, settle: () -> Unit, onTrainingMenu: () -> Boolean): Int {
    var presses = 0
    while (presses < MAX_RESUME_SETTLE_ACTIONS) {
        val step = resumeSettleStep(readScreen()) ?: break
        if (!press(step)) break
        presses++
        settle()
        if (onTrainingMenu()) break
    }
    return presses
}
