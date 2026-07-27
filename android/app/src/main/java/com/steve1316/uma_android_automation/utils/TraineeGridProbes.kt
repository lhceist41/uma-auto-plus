package com.steve1316.uma_android_automation.utils

/**
 * Cross-frame scroll measurement for the Trainee Select roster grid.
 *
 * The roster page swipe is a short drag whose real effect depends on the list's touch physics:
 * sometimes it advances a full row, sometimes it is eaten entirely and the list does not move.
 * The navigator used to assume the swipe worked and re-scanned whatever was on screen, paying a
 * tap-and-OCR pass over rows it had just read whenever a swipe under-delivered. This probe gives
 * the navigator ground truth instead: compare the frame before a swipe with the frame after it
 * and measure how many pixels the grid content actually moved, by sliding the after-frame's
 * probe columns against the before-frame's and taking the offset with the lowest mean luminance
 * difference.
 *
 * Fail-open by design: a measurement without a confident, well-separated minimum returns null,
 * and the caller falls back to the old conservative behavior (scan every visible row, dedup by
 * name). A wrong-but-confident answer is the only dangerous outcome, so the confidence gates
 * lean strict; the fallback is merely slow, never wrong.
 *
 * Android-free and JUnit-tested with synthetic frames, same pattern as the spark probes: the
 * runtime wraps Bitmaps in [SparkPixelSampler]s, the tests wrap generated patterns.
 */
object TraineeGridScroll {
    /** The tile band the measurement samples, as height fractions: below the preview pane, above
     * the filter bar (the same band the scan's two tap rows live in). */
    const val BAND_TOP_FRACTION = 0.54f
    const val BAND_BOTTOM_FRACTION = 0.80f

    /** Probe columns (width fractions) through tile art. Three columns so a single flat column
     * (background between portraits) cannot blind the measurement; costs are summed across all
     * three. Aligned with the roster's second, third and fourth tile columns. */
    val PROBE_COL_FRACTIONS = floatArrayOf(0.315f, 0.50f, 0.685f)

    /** Vertical sampling stride inside the band, and the candidate-offset stride. */
    const val SAMPLE_STRIDE = 2

    /** Candidate range: content may move up to ~2.3 rows down-list per swipe, and a few pixels
     * the other way on a bounce-back. Fractions of frame height. */
    const val MAX_DELTA_FRACTION = 0.23f
    const val MAX_BOUNCE_FRACTION = 0.03f

    /** Confidence gates, in mean-absolute-luminance-difference units (0..255). A true match of
     * identical content re-rendered lands near zero; unrelated tile art lands far higher. The
     * runner-up is only considered outside this pixel neighborhood of the winner, because
     * adjacent offsets of the true match are trivially almost as good. */
    const val MAX_ACCEPTED_COST = 14.0
    const val MIN_RUNNER_UP_MARGIN = 8.0
    const val RUNNER_UP_NEIGHBORHOOD_PX = 24

    /** A frame whose probe band is nearly uniform (mean absolute deviation below this) carries
     * too little texture to measure against: every offset matches everything. */
    const val MIN_BAND_TEXTURE = 6.0

    private fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /**
     * Measures how far the roster content moved between [before] and [after], in pixels.
     * Positive = the list advanced down-roster (a page-down swipe took effect); zero = the swipe
     * did not move the content; null = no confident measurement (caller must fall back to the
     * conservative full-row scan).
     */
    fun measureDeltaPx(before: SparkPixelSampler, after: SparkPixelSampler, width: Int, height: Int): Int? {
        val yTop = (height * BAND_TOP_FRACTION).toInt()
        val yBottom = (height * BAND_BOTTOM_FRACTION).toInt()
        if (yBottom - yTop < 60) return null
        val cols = PROBE_COL_FRACTIONS.map { (width * it).toInt() }

        val ys = ArrayList<Int>((yBottom - yTop) / SAMPLE_STRIDE + 1)
        var y = yTop
        while (y <= yBottom) {
            ys.add(y)
            y += SAMPLE_STRIDE
        }
        // Pre-extract the luminance sequences once per frame; the search then runs on plain arrays.
        val beforeCols = cols.map { x -> IntArray(ys.size) { i -> luma(before.argb(x, ys[i])) } }
        val afterCols = cols.map { x -> IntArray(ys.size) { i -> luma(after.argb(x, ys[i])) } }

        // Texture gate: a nearly flat band (loading fade, uniform backdrop) matches every offset.
        val flat =
            beforeCols.sumOf { col ->
                val mean = col.average()
                col.sumOf { v -> Math.abs(v - mean) } / col.size
            } / beforeCols.size
        if (flat < MIN_BAND_TEXTURE) return null

        val maxDelta = (height * MAX_DELTA_FRACTION).toInt()
        val minDelta = -(height * MAX_BOUNCE_FRACTION).toInt()
        val minOverlap = ys.size / 2

        // after[y] should equal before[y + d] when the content moved d pixels up-screen. The
        // candidate grid is aligned to the sampling stride so zero itself is always a candidate
        // (an unaligned start made every answer off by one).
        val costs = ArrayList<Pair<Int, Double>>()
        var d = (minDelta / SAMPLE_STRIDE) * SAMPLE_STRIDE
        while (d <= maxDelta) {
            val shift = d / SAMPLE_STRIDE
            var sum = 0L
            var n = 0
            for (c in beforeCols.indices) {
                val b = beforeCols[c]
                val a = afterCols[c]
                var i = maxOf(0, -shift)
                val end = minOf(a.size, b.size - shift)
                while (i < end) {
                    sum += Math.abs(a[i] - b[i + shift])
                    i++
                    n++
                }
            }
            if (n >= minOverlap) costs.add(d to sum.toDouble() / n)
            d += SAMPLE_STRIDE
        }
        if (costs.isEmpty()) return null

        val best = costs.minByOrNull { it.second }!!
        if (best.second > MAX_ACCEPTED_COST) return null
        val runnerUp =
            costs
                .filter { Math.abs(it.first - best.first) > RUNNER_UP_NEIGHBORHOOD_PX }
                .minByOrNull { it.second }
        if (runnerUp != null && runnerUp.second - best.second < MIN_RUNNER_UP_MARGIN) return null
        return best.first
    }
}
