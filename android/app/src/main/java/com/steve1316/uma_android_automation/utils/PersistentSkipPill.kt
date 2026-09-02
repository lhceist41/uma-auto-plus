package com.steve1316.uma_android_automation.utils

import com.steve1316.automation_library.utils.MessageLog

/**
 * State of the persistent bottom-left Skip pill, the control that cycles
 * "Skip Off" -> "Skip >" -> "Skip >>" and stays on screen across career launch and every in-career
 * tap-to-continue screen.
 *
 * A distinct one-chevron state is deliberately absent. It has no template asset:
 * `skip_off` is the white "Skip Off" pill and `skip_on` visually depicts the green two-chevron
 * "Skip >>" pill, but whether `skip_on` also matches a one-chevron pill above threshold has never
 * been measured on a live frame. [ON_TEMPLATE_MATCH] therefore only claims the template matched,
 * not which chevron count it represents; reporting a guessed chevron count there would be worse
 * than reporting nothing, so an unrecognized-but-present pill stays [PRESENT_UNRESOLVED].
 *
 * The wordless fast-forward glyphs `skip` and `skip_cinematic` belong to the opening movie, not to
 * this pill, and are never inputs to it.
 */
enum class PersistentSkipState {
    /** White "Skip Off" pill: events play at full speed. */
    OFF,

    /**
     * The `skip_on` template matched. This proves only the template match, not two chevrons, a
     * fastest/max state, or that it is safe to treat as a stop condition for future actuation -
     * that requires measuring a live one-chevron frame against `skip_on` first.
     */
    ON_TEMPLATE_MATCH,

    /** A pill is on screen but its chevron count is not established. */
    PRESENT_UNRESOLVED,

    /** No pill on screen. */
    NOT_VISIBLE,
}

/** The boolean the pill recognizers have always answered: is a persistent Skip pill on screen? */
val PersistentSkipState.pillVisible: Boolean
    get() = this != PersistentSkipState.NOT_VISIBLE

/**
 * Classifies the persistent Skip pill from the recognizers the callers already run.
 *
 * Every input is a lambda so this performs exactly the work the callers performed before: the
 * `skip_on` template match is skipped once `skip_off` matches, and the OCR fallback runs only when
 * both templates miss. That last one is load-bearing rather than cosmetic - the OCR path takes the
 * process-wide OCR lock, so running it on frames that already matched a template would change
 * timing and lock contention on every pill screen.
 */
fun classifyPersistentSkip(
    offPillMatched: () -> Boolean,
    onPillMatched: () -> Boolean,
    skipTextFound: () -> Boolean,
): PersistentSkipState =
    when {
        offPillMatched() -> PersistentSkipState.OFF
        onPillMatched() -> PersistentSkipState.ON_TEMPLATE_MATCH
        skipTextFound() -> PersistentSkipState.PRESENT_UNRESOLVED
        else -> PersistentSkipState.NOT_VISIBLE
    }

/**
 * Logs pill-state transitions for one recognition context.
 *
 * Both recognizers run on high-frequency screen ticks, so only changes are logged; a steady state
 * would otherwise emit a line per tick for a whole cutscene.
 */
class PersistentSkipStateLog(private val tag: String, private val context: String) {
    private var previous: PersistentSkipState? = null

    fun record(state: PersistentSkipState) {
        val prior = previous
        if (prior == state) return
        previous = state
        MessageLog.i(tag, "[SKIP_PILL] $context state=${state.name} previous=${prior?.name ?: "none"}")
    }
}
