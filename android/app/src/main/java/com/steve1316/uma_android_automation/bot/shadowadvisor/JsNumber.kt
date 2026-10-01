package com.steve1316.uma_android_automation.bot.shadowadvisor

import java.math.BigDecimal

/**
 * JS-style number formatting so the Kotlin port serializes byte-for-byte like the TypeScript authority: an
 * integer-valued double prints without `.0` (`72`, not `72.0`) and a half-step keeps its decimal (`71.5`). Covers the
 * advisor's integer and half-integer domain; other values use the shortest round-trip with trailing zeros stripped.
 */
object JsNumber {
    fun format(value: Double): String {
        require(value.isFinite()) { "advisor numeric output is never non-finite: $value" }
        val asLong = value.toLong()
        // `-0.0 == 0L.toDouble()` is true, so negative zero folds to "0" exactly as JS does.
        if (asLong.toDouble() == value) return asLong.toString()
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }

    /**
     * One-decimal rounding matching the TS policy's `round1`; Java's `Math.round` equals JS `Math.round`, and only
     * integers and half-integers are rounded here, so the result is exact.
     */
    fun round1(value: Double): Double = Math.round(value * 10.0).toDouble() / 10.0
}
