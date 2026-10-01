package com.steve1316.uma_android_automation.bot

import java.util.Locale

/**
 * Keep-vs-reroll pricing for the career-end sparks screen (the 30 TP redraw). Odds from a ~9,770-factor
 * JP sample: a blue picks its stat uniformly among the five, and that stat's final value sets the star
 * odds (<600 -> expected 1.10 stars, 600-1099 -> 1.555, 1100+ -> 1.905); pink redraws flat ~20/70/10.
 * Blue upside is credited in full; pink/unique/white count only as holdings to protect. Whites regenerate
 * from the same sources, so only a visible 3-star white is worth protecting.
 */
object SparkRerollPolicy {
    /** Expected redraw stars for a blue landing on a stat with this final value. */
    fun expectedBlueStars(statValue: Int): Double =
        when {
            statValue >= 1100 -> 1.905
            statValue >= 600 -> 1.555
            else -> 1.10
        }

    /** Expected blue stars of a fresh redraw: uniform stat pick over the five final values. */
    fun expectedFreshBlueStars(finalStats: Collection<Int>): Double =
        if (finalStats.isEmpty()) 0.0 else finalStats.map { expectedBlueStars(it) }.average()

    /** Expected stars of a fresh pink (flat 20/70/10). */
    private const val FRESH_PINK_STARS = 1.90

    /** Unique/green star odds are unverified, so their holdings weigh half. */
    private const val UNIQUE_HOLDING_WEIGHT = 0.5

    /** Value protected per visible 3-star white: the specific spark rarely survives a redraw. */
    private const val WHITE_THREE_STAR_HOLDING = 0.75

    /** Net gain a redraw must clear before spending 30 TP, so ties don't churn. */
    private const val MARGIN = 0.05

    data class Verdict(val reroll: Boolean, val reason: String)

    /** Star counts come from row color samples; null pink/unique stars (unreadable rows) price neutral. */
    fun decide(
        blueStars: Int,
        pinkStars: Int?,
        uniqueStars: Int?,
        visibleWhiteThreeStars: Int,
        finalStats: Collection<Int>,
    ): Verdict {
        if (blueStars >= 3) {
            return Verdict(false, "blue spark is already 3-star - never re-gamble it")
        }
        val freshBlue = expectedFreshBlueStars(finalStats)
        if (blueStars >= 2) {
            return Verdict(false, "2-star blue beats a redraw's expected ${fmt(freshBlue)} - keeping the set")
        }
        if (finalStats.isNotEmpty() && finalStats.all { it < 600 }) {
            return Verdict(false, "every stat finished under 600 - a redraw cannot roll a 3-star blue")
        }
        val pinkLoss = ((pinkStars ?: 0).toDouble() - FRESH_PINK_STARS).coerceAtLeast(0.0)
        val uniqueLoss = UNIQUE_HOLDING_WEIGHT * (((uniqueStars ?: 0).toDouble() - FRESH_PINK_STARS).coerceAtLeast(0.0))
        val whiteHold = WHITE_THREE_STAR_HOLDING * visibleWhiteThreeStars
        val net = freshBlue - blueStars - pinkLoss - uniqueLoss - whiteHold
        val math = "fresh blue ${fmt(freshBlue)} vs 1-star, holdings -${fmt(pinkLoss + uniqueLoss + whiteHold)}, net ${fmt(net)}"
        return if (net > MARGIN) {
            Verdict(true, "1-star blue and the redraw prices positive: $math")
        } else {
            Verdict(false, "1-star blue but the redraw prices negative ($math) - keeping the set")
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)
}
