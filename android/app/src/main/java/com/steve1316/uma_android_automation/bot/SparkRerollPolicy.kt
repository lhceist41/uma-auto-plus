package com.steve1316.uma_android_automation.bot

import java.util.Locale

/**
 * Keep-vs-reroll pricing for the career-end sparks screen (the 30 TP redraw).
 *
 * Replaces the old ">= 1100 core stat" gate, which predates the corrected spark model and could
 * never pass on URA farm careers (the account's URA best is 1089). Verified odds (2026-07-11,
 * ~9,770-factor JP sample cross-checked against two databases):
 *  - The blue spark picks its stat UNIFORMLY AT RANDOM among the five, then that stat's final
 *    value sets the star odds: <600 -> 90/10/0 (expected 1.10 stars, 3-star impossible),
 *    600-1099 -> ~50/45/5.5 (expected 1.555), 1100+ -> ~20/70/10.5 (expected 1.905).
 *  - Pink rerolls flat ~20/70/10 among A/S aptitudes (expected 1.90).
 *  - Green/unique star behavior is unverified; assumed the same flat draw, discounted.
 *  - Whites regenerate from the same sources on a redraw (~20% per distinct G1 won), so their
 *    count is EV-neutral; only a visible 3-star white is a holding a redraw would likely lose.
 *
 * Shape of the rule: the blue's redraw upside is credited in full (blues are what the farm
 * program exists for), while pink/unique/white value counts only as a HOLDING to protect -
 * chasing pink upside is not the program, but losing a 3-star pink to a blue gamble is real.
 * Consequences: a 2/3-star blue is always kept (a redraw's expected blue tops out at 1.905),
 * and a 1-star blue rerolls unless the redraw pool is dead (every stat under 600) or the set
 * holds jackpot pinks/whites.
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

    /** Unique/green holdings weigh half: the star rule is unverified and its breeding value is
     * secondary to blues. */
    private const val UNIQUE_HOLDING_WEIGHT = 0.5

    /** Value protected per visible 3-star white - the specific spark rarely survives a redraw. */
    private const val WHITE_THREE_STAR_HOLDING = 0.75

    /** The redraw must clear this net gain before spending 30 TP, so ties don't churn. */
    private const val MARGIN = 0.05

    data class Verdict(val reroll: Boolean, val reason: String)

    /**
     * Prices keeping the visible set against a fresh redraw. Star counts come from the row
     * color samples (no OCR involved); [pinkStars]/[uniqueStars] may be null when those rows
     * were unreadable - unknown holdings price neutral rather than blocking the decision.
     */
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
