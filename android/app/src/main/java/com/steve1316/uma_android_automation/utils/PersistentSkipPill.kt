package com.steve1316.uma_android_automation.utils

import com.steve1316.automation_library.utils.MessageLog

/**
 * State of the persistent bottom-left Skip pill, the control that cycles
 * "Skip Off" -> "Skip >" -> "Skip >>" and stays on screen across career launch and every in-career
 * tap-to-continue screen.
 *
 * A distinct one-chevron state is deliberately absent. It has no template asset:
 * `skip_off` is the white "Skip Off" pill and `skip_on` visually depicts the green two-chevron
 * "Skip >>" pill, but it also matches a one-chevron pill above threshold on live frames.
 * [ON_TEMPLATE_MATCH] therefore only claims the template matched,
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
     * fastest/max state, or that it is safe to treat as a stop condition for future actuation: the
     * same template matches a one-chevron pill.
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

/**
 * Whether the launch handler may tap the pill: only a positively matched "Skip Off" pill, which two
 * taps take to "Skip >>". The pill can already be on when a career launches, and two blind taps on
 * a "Skip >>" pill cycle it to the slow "Skip >"; an on or unresolved pill is left as the player set
 * it. `skip_off` is the reliable detector here: it matches Off pills and none of the one- or
 * two-chevron pills measured on live frames.
 */
fun launchTapsSkipPill(state: PersistentSkipState): Boolean = state == PersistentSkipState.OFF

/**
 * Centre of the persistent Skip pill as a fraction of the capture: (387, 1873) on 1080x1920, the
 * same on the launch prompt, the main screen, cutscenes and event choices. The pill spans x 275 to
 * 496 and y 1842 to 1901 there, so a jittered tap at the centre always lands on it.
 */
const val SKIP_PILL_CENTRE_X_FRACTION = 387.0 / 1080.0
const val SKIP_PILL_CENTRE_Y_FRACTION = 1873.0 / 1920.0

/**
 * Reads a white "Skip Off" pill from its colours: a near-white fill at the pill's left end and brown
 * lettering across its text band. The `skip_off` template scores only 0.60 to 0.77 on the same pill
 * over the light launch backgrounds (the game resets Skip to Off at every career start), below its
 * threshold and below the 0.70 it gives chevron pills on event screens, so a template threshold
 * cannot separate them. Measured: every Off pill reads fill 1.00 and lettering 0.195; every `>` and
 * `>>` pill reads 0.00 and 0.000; a blank white screen has the fill but no lettering; none of 631
 * other screenshots matched. Gated on the 1080x1920 surface the boxes were measured on.
 */
fun skipOffPillByColour(
    sampler: SparkPixelSampler,
    width: Int,
    height: Int,
): Boolean {
    if (width != 1080 || height != 1920) return false
    return fraction(sampler, 282..300, 1858..1885, ::isPillWhite) >= 0.9 &&
        fraction(sampler, 310..470, 1852..1892, ::isPillLettering) >= 0.10
}

private fun fraction(
    sampler: SparkPixelSampler,
    xs: IntRange,
    ys: IntRange,
    test: (Int) -> Boolean,
): Double {
    var hits = 0
    var total = 0
    for (y in ys step 2) {
        for (x in xs step 2) {
            total++
            if (test(sampler.argb(x, y))) hits++
        }
    }
    return hits.toDouble() / total
}

private fun isPillWhite(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return minOf(r, g, b) > 200 && maxOf(r, g, b) - minOf(r, g, b) < 45
}

private fun isPillLettering(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return r in 71..189 && g in 26..129 && b < 80 && r - g > 25 && g - b > 5
}

/** How one attempt to turn an in-career "Skip Off" pill back to fast ended. */
enum class SkipFixOutcome {
    /** The pill did not read Off: nothing tapped. */
    NOT_OFF,

    /** An earlier attempt this career did not take: nothing tapped. */
    GAVE_UP_EARLIER,

    /** Two taps, and the pill no longer reads Off. */
    LEFT_OFF,

    /** Two taps, and the pill still reads Off or is gone: no more attempts this career. */
    GIVING_UP,
}

/**
 * Turns the persistent pill from Off to fast, at the launch prompt and on in-career screens. The
 * game resets it to Off at every career start, so without this every career plays its events at
 * full length. Taps only a pill read as Off ([launchTapsSkipPill]); two taps cycle Off to ">" to
 * ">>". The re-read only has to
 * show the pill left Off: `skip_on` stays under its threshold on the main screen's chevron pills
 * (0.508 to 0.755 measured), so a fast pill there reads PRESENT_UNRESOLVED. A pill that still reads
 * Off, or is gone, ends the attempts for the rest of the career, so it is never tapped in a loop.
 * One instance per career.
 */
class InCareerSkipFix {
    var gaveUp: Boolean = false
        private set

    fun attempt(
        state: PersistentSkipState,
        tapPillTwice: () -> Unit,
        reRead: () -> PersistentSkipState,
    ): SkipFixOutcome {
        if (!launchTapsSkipPill(state)) return SkipFixOutcome.NOT_OFF
        if (gaveUp) return SkipFixOutcome.GAVE_UP_EARLIER
        tapPillTwice()
        val after = reRead()
        if (after != PersistentSkipState.OFF && after.pillVisible) return SkipFixOutcome.LEFT_OFF
        gaveUp = true
        return SkipFixOutcome.GIVING_UP
    }
}

/**
 * Whether a visible Skip pill is the launch-time Quick Mode prompt rather than an in-career
 * tap-to-continue screen.
 *
 * Both screens show the same pill, and within a career launch the prompt is always the FIRST one,
 * so [skipToggleAlreadyDone] separates them. That latch cannot separate anything when the navigator
 * was called to resume a career that is already running: it starts false on every call, so the
 * first in-career cutscene pill read as the launch prompt and received the launch handler's two
 * blind pill taps, cycling an already-maxed pill back down toward Off.
 */
fun isLaunchQuickModePrompt(resumingInProgressCareer: Boolean, skipToggleAlreadyDone: Boolean): Boolean =
    !resumingInProgressCareer && !skipToggleAlreadyDone
