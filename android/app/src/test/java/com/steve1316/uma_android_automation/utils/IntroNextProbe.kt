package com.steve1316.uma_android_automation.utils

/** True when the button green covers the tap point and its 25x25 jitter box (sampled on its edges, clear of the white "Next" label). */
internal fun introNextUnder(
    sampler: SparkPixelSampler,
    x: Int,
    y: Int,
): Boolean =
    (y - 12..y + 12).all { dy ->
        listOf(x - 70, x + 70).all { px ->
            val p = sampler.argb(px, dy)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            g >= 180 && g - r >= 30 && g - b >= 80
        }
    }
