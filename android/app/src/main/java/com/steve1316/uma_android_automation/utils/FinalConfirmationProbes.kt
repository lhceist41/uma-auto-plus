package com.steve1316.uma_android_automation.utils

/**
 * Pixel probes for the career-launch Final Confirmation screen's mode tabs.
 *
 * The Final Confirmation screen carries two mutually exclusive tabs: LEFT "Normal Career" and RIGHT
 * "Independent Training". Only the Normal Career mode may reach the irreversible Start Career press.
 * The selected tab is a solid green pill; the unselected one is near-white. That colour is read here
 * directly, exactly like the Event Boost checkbox reader in CareerLaunchNavigator: no template asset,
 * no OCR. On the Q1 device captures (1080x1920 MuMu, validation/n0-final-confirmation-mode) the
 * selected-tab sample reads green fraction 1.000 and the unselected one 0.000 -- complete separation.
 *
 * Every function is Android-free and takes a [SparkPixelSampler] so the runtime wraps a Bitmap and the
 * JUnit fixtures wrap a decoded PNG; the thresholds are pinned by fixture tests against those pixels.
 *
 * This selects and verifies the launch MODE only. It does not add any Independent Training gameplay.
 */

/** Positively verified Final Confirmation mode. Only NORMAL_CAREER_VERIFIED may reach Start Career. */
enum class FinalConfirmationMode {
    /** Left "Normal Career" tab is green-selected and the right tab is not. */
    NORMAL_CAREER_VERIFIED,

    /** Right "Independent Training" tab is green-selected and the left tab is not. */
    INDEPENDENT_TRAINING_VERIFIED,

    /** Neither tab (or both) read as positively green-selected; the mode cannot be trusted. */
    MODE_UNRECOGNIZED,
}

/**
 * Tab geometry and colour thresholds, measured on the Q1 1080x1920 captures. Device coordinates
 * (top-left origin), so they read correctly on a full screen and on the top-strip test fixtures alike.
 */
object FinalConfirmationTabGeometry {
    /** Sample centre inside the LEFT "Normal Career" pill (left of the glyphs). */
    const val NORMAL_TAB_SAMPLE_X = 110
    const val NORMAL_TAB_SAMPLE_Y = 322

    /** Sample centre inside the RIGHT "Independent Training" pill. */
    const val INDEPENDENT_TAB_SAMPLE_X = 980
    const val INDEPENDENT_TAB_SAMPLE_Y = 322

    /** Reliable tap centre of the LEFT "Normal Career" tab, for the one-shot corrective switch. */
    const val NORMAL_TAB_CLICK_X = 285
    const val NORMAL_TAB_CLICK_Y = 322

    /** Half-width of the square colour sample box (matches the Event Boost reader's 18px probe). */
    const val SAMPLE_HALF = 18

    /** A pixel counts as green when green dominates red and blue by more than this (measured mean
     * green pill = (137,210,8): g-max(r,b) ~= 73; white = (246,245,248): g-max(r,b) < 0). */
    const val GREEN_DOMINANCE = 40

    /** Fraction of green-dominant pixels required to call a tab selected. Measured 1.000 selected vs
     * 0.000 unselected, so 0.6 sits in the middle of an enormous margin. */
    const val GREEN_FRACTION_MIN = 0.6

    /** A pixel counts as near-white when every channel exceeds this (measured unselected pill min
     * channel ~= 245). */
    const val WHITE_BRIGHTNESS_MIN = 210

    /** ...and the channel spread stays within this (measured unselected pill spread ~= 3). Together
     * these reject bright in-career art that is green-dominant at one tab point but not a white pill
     * at the other (training screens, the concert result banner). */
    const val WHITE_SPREAD_MAX = 30

    /** Fraction of near-white pixels required to call the OTHER tab unselected. Measured 1.000. */
    const val WHITE_FRACTION_MIN = 0.6
}

private fun red(argb: Int): Int = (argb shr 16) and 0xFF

private fun green(argb: Int): Int = (argb shr 8) and 0xFF

private fun blue(argb: Int): Int = argb and 0xFF

private inline fun tabPixelFraction(
    sampler: SparkPixelSampler,
    cx: Int,
    cy: Int,
    half: Int,
    predicate: (Int, Int, Int) -> Boolean,
): Double {
    var total = 0
    var hits = 0
    var y = cy - half
    while (y <= cy + half) {
        var x = cx - half
        while (x <= cx + half) {
            if (x >= 0 && y >= 0) {
                val p = sampler.argb(x, y)
                total++
                if (predicate(red(p), green(p), blue(p))) hits++
            }
            x++
        }
        y++
    }
    return if (total == 0) 0.0 else hits.toDouble() / total
}

/**
 * Fraction of green-dominant pixels in a [FinalConfirmationTabGeometry.SAMPLE_HALF] box centred on
 * (cx, cy). Returns 0.0 for a sampler that yields no pixels (defensive; never happens on a real screen).
 */
fun finalConfirmationTabGreenFraction(
    sampler: SparkPixelSampler,
    cx: Int,
    cy: Int,
    half: Int = FinalConfirmationTabGeometry.SAMPLE_HALF,
    dominance: Int = FinalConfirmationTabGeometry.GREEN_DOMINANCE,
): Double = tabPixelFraction(sampler, cx, cy, half) { r, g, b -> g - maxOf(r, b) > dominance }

/** Fraction of near-white pixels in the same box: every channel bright and near-neutral. */
fun finalConfirmationTabWhiteFraction(
    sampler: SparkPixelSampler,
    cx: Int,
    cy: Int,
    half: Int = FinalConfirmationTabGeometry.SAMPLE_HALF,
): Double =
    tabPixelFraction(sampler, cx, cy, half) { r, g, b ->
        minOf(r, g, b) > FinalConfirmationTabGeometry.WHITE_BRIGHTNESS_MIN &&
            maxOf(r, g, b) - minOf(r, g, b) <= FinalConfirmationTabGeometry.WHITE_SPREAD_MAX
    }

private fun tabGreen(sampler: SparkPixelSampler, cx: Int, cy: Int): Boolean =
    finalConfirmationTabGreenFraction(sampler, cx, cy) >= FinalConfirmationTabGeometry.GREEN_FRACTION_MIN

private fun tabWhite(sampler: SparkPixelSampler, cx: Int, cy: Int): Boolean =
    finalConfirmationTabWhiteFraction(sampler, cx, cy) >= FinalConfirmationTabGeometry.WHITE_FRACTION_MIN

/** LEFT "Normal Career" tab reads green-selected. */
fun normalCareerTabGreen(sampler: SparkPixelSampler): Boolean =
    tabGreen(sampler, FinalConfirmationTabGeometry.NORMAL_TAB_SAMPLE_X, FinalConfirmationTabGeometry.NORMAL_TAB_SAMPLE_Y)

/** RIGHT "Independent Training" tab reads green-selected. */
fun independentTrainingTabGreen(sampler: SparkPixelSampler): Boolean =
    tabGreen(sampler, FinalConfirmationTabGeometry.INDEPENDENT_TAB_SAMPLE_X, FinalConfirmationTabGeometry.INDEPENDENT_TAB_SAMPLE_Y)

/**
 * Positive mode classification: exactly one tab is a green pill AND the OTHER is a near-white pill.
 * Requiring both halves of the two-tab signature (not merely "the other tab is not green") is what
 * keeps in-career screens that happen to be green-dominant at one tab point -- training screens, the
 * concert result banner -- from reading as a career mode. "Not Independent Training" is never treated
 * as "Normal Career": both-green, neither-green, and any half-match are MODE_UNRECOGNIZED.
 */
fun classifyFinalConfirmationMode(sampler: SparkPixelSampler): FinalConfirmationMode {
    val leftGreen = normalCareerTabGreen(sampler)
    val rightGreen = independentTrainingTabGreen(sampler)
    val leftWhite = tabWhite(sampler, FinalConfirmationTabGeometry.NORMAL_TAB_SAMPLE_X, FinalConfirmationTabGeometry.NORMAL_TAB_SAMPLE_Y)
    val rightWhite = tabWhite(sampler, FinalConfirmationTabGeometry.INDEPENDENT_TAB_SAMPLE_X, FinalConfirmationTabGeometry.INDEPENDENT_TAB_SAMPLE_Y)
    return when {
        leftGreen && rightWhite -> FinalConfirmationMode.NORMAL_CAREER_VERIFIED
        rightGreen && leftWhite -> FinalConfirmationMode.INDEPENDENT_TRAINING_VERIFIED
        else -> FinalConfirmationMode.MODE_UNRECOGNIZED
    }
}

/**
 * Tab-independent Final Confirmation screen recognizer: exactly one of the two mode tabs is
 * green-selected. Lets detectScreen return PRE_RUN_CONFIRMATION on the Independent Training tab too
 * (whose bottom button reads "Start!", so none of the "Start Career!" templates match it), so the
 * mode gate can refuse or correct it instead of the screen falling to UNKNOWN.
 */
fun finalConfirmationScreenPresent(sampler: SparkPixelSampler): Boolean =
    classifyFinalConfirmationMode(sampler) != FinalConfirmationMode.MODE_UNRECOGNIZED
