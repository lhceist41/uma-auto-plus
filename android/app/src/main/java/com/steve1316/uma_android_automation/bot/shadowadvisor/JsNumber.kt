package com.steve1316.uma_android_automation.bot.shadowadvisor

import java.math.BigDecimal

/**
 * JS-style number formatting for advisor-visible numeric output, so the Kotlin S1 port serializes byte-for-byte
 * like the TypeScript authority. The trap this closes: the TS policy interpolates raw JS numbers into reason
 * detail strings and serializes JSON numbers with JS semantics, where an integer-valued double prints without a
 * trailing `.0` (`72`, not `72.0`) and a half-step keeps its one decimal (`71.5`). Kotlin's `Double.toString`
 * produces `72.0`, which would break the golden-fixture parity the whole S3 pipeline is pinned to.
 *
 * The advisor's numeric domain is integer stat gains, `failChance * 0.5`, and one-decimal `round1` outputs, i.e.
 * integers and half-integers, all exactly representable in an IEEE double. This formatter reproduces JS
 * `Number` -> string for that domain: an integer value prints as the integer; anything else uses Java's
 * shortest round-trip (`Double.toString` via `BigDecimal.valueOf`) with trailing zeros stripped, matching JS for
 * the short decimals the advisor emits.
 */
object JsNumber {
    /** Formats [value] the way JS renders a number in a template string / JSON: no trailing `.0`, real decimals kept. */
    fun format(value: Double): String {
        require(value.isFinite()) { "advisor numeric output is never non-finite: $value" }
        val asLong = value.toLong()
        // `-0.0 == 0L.toDouble()` is true, so negative zero folds to "0" exactly as JS does.
        if (asLong.toDouble() == value) return asLong.toString()
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }

    /**
     * Deterministic one-decimal rounding matching the TS policy's `round1` (`Math.round(n * 10) / 10`). Java's
     * `Math.round(double)` is `floor(x + 0.5)`, identical to JS `Math.round`, and the advisor only rounds
     * integers and half-integers here, so the result is exact with no locale or float drift.
     */
    fun round1(value: Double): Double = Math.round(value * 10.0).toDouble() / 10.0
}
