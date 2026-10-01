package com.steve1316.uma_android_automation.utils

/**
 * Pure-pixel reader for one training stat-gain row: "+N" under a stat, or "^^+N" once the training
 * would take that stat past 1200, when the game draws the gain in gold behind a double chevron.
 *
 * The template matcher in [CustomImageUtils.determineStatGainFromTraining] matches orange grayscale
 * digits, so it reads every gold row as 0, and its crops sit on a 180 px pitch while the stat columns
 * are 169 px apart, so a gold Speed value also loses its last digit off the crop. This reader keys on
 * the glyph shape instead of its colour: every glyph, orange, red or gold, is a warm-hued silhouette
 * (fill plus its darker inner outline) inside a thick white outline. Each warm component enclosed by
 * white is resized to a fixed grid and matched against silhouettes measured from real Grand Concert
 * training captures; a glyph is accepted only when it clears a floor and beats the runner-up label
 * by a margin, and anything doubtful returns an unread value rather than a guess.
 */
object StatGainDigits {
    /** One row's reading: [value] is null when a "+" was found but its digits could not be read. */
    class Row(val value: Int?, val gold: Boolean)

    /** Column centres relative to the "Skill Pts" header anchor, at 1080 px width. */
    const val FIRST_COLUMN_CENTER_FROM_SKILL_POINTS = -847
    const val COLUMN_PITCH = 169
    const val COLUMN_WIDTH = 169

    private fun isWhite(r: Int, g: Int, b: Int): Boolean = r >= 225 && g >= 218 && b >= 200

    // Warm hue covers the orange, red and gold fills and their darker inner outlines, and excludes
    // the lavender bonus bubble.
    private fun isGlyph(r: Int, b: Int): Boolean = r >= 90 && r - b >= 45

    /** Minimum share of a component's surrounding ring that is white outline (glyphs read 0.62 and up). */
    private const val WHITE_ADJ_MIN = 0.6

    // Template grid and classifier acceptance, measured on the capture corpus.
    private const val TW = 18
    private const val TH = 26
    private const val SCORE_MIN = 0.80
    private const val MARGIN_MIN = 0.03

    /**
     * Reads the gain row inside one stat column's box, or returns null when the box holds no "+".
     * The size gates are measured at [COLUMN_WIDTH] and scale with [width].
     */
    fun readRow(sampler: SparkPixelSampler, left: Int, top: Int, width: Int, height: Int): Row? {
        val scale = width / COLUMN_WIDTH.toDouble()
        val glyph = BooleanArray(width * height)
        val white = BooleanArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = sampler.argb(left + x, top + y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val i = y * width + x
                if (isWhite(r, g, b)) white[i] = true else if (isGlyph(r, b)) glyph[i] = true
            }
        }

        val comps = components(glyph, white, width, height, scale)
        val plus = comps.indexOfFirst { it.label == '+' }
        if (plus < 0) return null
        val gold = comps.subList(0, plus).any { it.label == '^' }

        // Digits follow the "+" left to right, at most a glyph-sized gap apart.
        val maxGap = 22 * scale
        val noiseArea = 450 * scale * scale
        var value = 0
        var digits = 0
        var prev = comps[plus]
        for (c in comps.subList(plus + 1, comps.size)) {
            if (c.x0 - prev.x1 > maxGap) break
            if (c.label != null && c.label.isDigit()) {
                if (c.x1 >= width - 1) return Row(null, gold)
                value = value * 10 + (c.label - '0')
                digits++
                prev = c
                continue
            }
            // Background art can sit inside the outline between glyphs: skip it when it is too small to
            // be a digit or overlaps a glyph that did read; anything digit-sized and unread stops the read.
            val overlapsRead = comps.any { o -> o !== c && o.label != null && o.x0 - 2 <= c.x1 && c.x0 <= o.x1 + 2 }
            if (c.area < noiseArea || overlapsRead) continue
            return Row(null, gold)
        }
        if (digits !in 1..2) return Row(null, gold)
        return Row(value, gold)
    }

    /** The value a gain row contributes: a gold row's pixel read replaces the template read, which cannot
     * see gold digits; every other row keeps the template read. */
    fun resolveRowValue(templateValue: Int, pixel: Row?): Int = if (pixel != null && pixel.gold) pixel.value ?: 0 else templateValue

    private class Comp(val x0: Int, val x1: Int, val area: Int, val label: Char?)

    /** 8-connected glyph components enclosed by white outline, labelled where confident, left to right. */
    private fun components(glyph: BooleanArray, white: BooleanArray, w: Int, h: Int, scale: Double): List<Comp> {
        val labels = IntArray(w * h) { -1 }
        val stack = ArrayDeque<Int>()
        val result = mutableListOf<Comp>()
        var next = 0
        for (start in 0 until w * h) {
            if (!glyph[start] || labels[start] != -1) continue
            val cells = ArrayList<Int>()
            labels[start] = next
            stack.addLast(start)
            var minX = w
            var maxX = 0
            var minY = h
            var maxY = 0
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                cells.add(c)
                val cx = c % w
                val cy = c / w
                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = cx + dx
                        val ny = cy + dy
                        if ((dx != 0 || dy != 0) && nx in 0 until w && ny in 0 until h) {
                            val ni = ny * w + nx
                            if (glyph[ni] && labels[ni] == -1) {
                                labels[ni] = next
                                stack.addLast(ni)
                            }
                        }
                    }
                }
            }
            val id = next++
            val cw = maxX - minX + 1
            val ch = maxY - minY + 1
            if (cells.size < 120 * scale * scale || ch < 18 * scale || ch > 50 * scale) continue
            if (whiteAdjacency(cells, white, w, h) < WHITE_ADJ_MIN) continue
            val label = if (cw >= 6 * scale && cw <= 45 * scale) classify(toGrid(labels, id, minX, maxX, minY, maxY, w)) else null
            result.add(Comp(minX, maxX, cells.size, label))
        }
        result.sortBy { it.x0 }
        return result
    }

    /** Share of the component's Manhattan-distance<=2 ring (cells outside it) that is white outline. */
    private fun whiteAdjacency(cells: List<Int>, white: BooleanArray, w: Int, h: Int): Double {
        val ring = HashSet<Int>()
        for (c in cells) {
            val x = c % w
            val y = c / w
            for (dy in -2..2) {
                for (dx in -2..2) {
                    val nx = x + dx
                    val ny = y + dy
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) <= 2 && nx in 0 until w && ny in 0 until h) ring.add(ny * w + nx)
                }
            }
        }
        ring.removeAll(cells.toSet())
        if (ring.isEmpty()) return 0.0
        return ring.count { white[it] }.toDouble() / ring.size
    }

    /** Nearest-neighbour resize of one component's mask (within its box) to the [TW]x[TH] grid. */
    private fun toGrid(labels: IntArray, id: Int, x0: Int, x1: Int, y0: Int, y1: Int, w: Int): BooleanArray {
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1
        val out = BooleanArray(TW * TH)
        for (ty in 0 until TH) {
            val sy = y0 + (ty * bh) / TH
            for (tx in 0 until TW) {
                out[ty * TW + tx] = labels[sy * w + x0 + (tx * bw) / TW] == id
            }
        }
        return out
    }

    /** Best label for a resized glyph, or null when it is not confident enough. A label scores its best
     * template variant, and the margin is to the best other label. */
    private fun classify(grid: BooleanArray): Char? {
        val best = HashMap<Char, Double>()
        for ((label, tmpl) in TEMPLATES) {
            var diff = 0
            for (i in 0 until TW * TH) if (grid[i] != tmpl[i]) diff++
            val score = 1.0 - diff.toDouble() / (TW * TH)
            if (score > (best[label] ?: -1.0)) best[label] = score
        }
        val ranked = best.entries.sortedByDescending { it.value }
        if (ranked[0].value < SCORE_MIN || ranked[0].value - ranked[1].value < MARGIN_MIN) return null
        return ranked[0].key
    }

    /** Glyph silhouettes (18x26) measured from real Grand Concert training captures: digits, the "+", and
     * the gold double chevron ('^'). A second variant of a label is its shape in the bonus row above the
     * gain row. */
    private val TEMPLATES: List<Pair<Char, BooleanArray>> = listOf(
        '0' to rows(
            "000000111111000000", "000001111111110000", "000111111111111000", "000111111111111100",
            "001111111111111110", "001111111111111110", "001111111111111110", "011111111111111110",
            "011111111011111111", "111111110001111111", "111111110001111111", "111111110001111111",
            "111111110001111111", "111111110001111111", "111111110001111111", "111111110001111111",
            "111111110001111111", "011111110001111111", "011111111011111111", "011111111111111110",
            "001111111111111110", "001111111111111110", "001111111111111100", "000111111111111100",
            "000011111111111000", "000000111111100000",
        ),
        '0' to rows(
            "000000111111100000", "000001111111110000", "000011111111111000", "000111111111111100",
            "001111111111111110", "001111111111111110", "001111111111111110", "011111111111111111",
            "011111111111111111", "011111111011111111", "011111111011111111", "111111111011111111",
            "111111111011111111", "111111111011111111", "111111111011111111", "111111111011111111",
            "011111111011111111", "011111111011111111", "011111111111111111", "011111111111111111",
            "001111111111111110", "001111111111111110", "001111111111111110", "000111111111111100",
            "000011111111111000", "000000111111100000",
        ),
        '1' to rows(
            "000000000111111111", "000000001111111111", "000000011111111111", "000000111111111111",
            "000111111111111111", "011111111111111111", "111111111111111111", "111111111111111111",
            "011111111111111111", "011111111111111111", "001111111111111111", "001111111111111111",
            "000000011111111111", "000000011111111111", "000000011111111111", "000000011111111111",
            "000000011111111111", "000000011111111111", "000000011111111111", "000000011111111111",
            "000000011111111111", "000000011111111111", "000000011111111111", "000000011111111111",
            "000000011111111111", "000000001111111111",
        ),
        '1' to rows(
            "000000000111111110", "000000000111111111", "000000001111111111", "000000011111111111",
            "000001111111111111", "000111111111111111", "011111111111111111", "011111111111111111",
            "011111111111111111", "011111111111111111", "001111111111111111", "001111111111111111",
            "000111111111111111", "000000011111111111", "000000011111111111", "000000011111111111",
            "000000011111111111", "000000011111111111", "000000011111111111", "000000011111111111",
            "000000011111111111", "000000011111111111", "000000011111111111", "000000011111111111",
            "000000011111111111", "000000001111111111",
        ),
        '2' to rows(
            "000000011111000000", "000001111111111000", "000111111111111100", "001111111111111110",
            "001111111111111110", "001111111111111111", "011111111111111111", "111111111111111111",
            "111111111011111111", "011111110011111111", "001111110111111111", "000000101111111111",
            "000000111111111111", "000001111111111110", "000111111111111100", "000111111111111000",
            "001111111111110000", "001111111111000000", "011111111110011110", "011111111111111110",
            "111111111111111111", "111111111111111111", "111111111111111111", "111111111111111111",
            "111111111111111111", "111111111111111111",
        ),
        '3' to rows(
            "000000011110000000", "000001111111100000", "000111111111111000", "000111111111111100",
            "001111111111111110", "011111111111111110", "011111111111111111", "011111111111111111",
            "011111111111111111", "001111111111111110", "000001111111111110", "000001111111111110",
            "000001111111111110", "000001111111111110", "000001111111111111", "000001111111111111",
            "000011111111111111", "001111110011111111", "111111111111111111", "111111111111111111",
            "111111111111111111", "111111111111111111", "011111111111111110", "001111111111111100",
            "000111111111111100", "000001111111110000",
        ),
        '4' to rows(
            "000000000111111000", "000000001111111000", "000000001111111100", "000000011111111100",
            "000000111111111100", "000000111111111100", "000001111111111100", "000001111111111100",
            "000011111111111100", "000011111111111100", "000111111111111100", "000111111111111100",
            "001111111111111100", "001111111111111100", "011111111111111111", "111111111111111111",
            "111111111111111111", "111111111111111111", "111111111111111111", "111111111111111111",
            "111111111111111111", "001111111111111110", "000000000111111100", "000000000111111100",
            "000000000111111100", "000000000111111000",
        ),
        '5' to rows(
            "001111111111111110", "001111111111111110", "011111111111111110", "011111111111111110",
            "011111111111111110", "011111111111111110", "011111111111111110", "011111111111111100",
            "011111111111110000", "011111111111111100", "011111111111111100", "111111111111111110",
            "111111111111111110", "111111111111111111", "111111111011111111", "011111110011111111",
            "000011100001111111", "001111100001111111", "111111110011111111", "111111111111111111",
            "111111111111111111", "011111111111111110", "011111111111111110", "001111111111111100",
            "000111111111111000", "000001111111110000",
        ),
        '5' to rows(
            "001111111111111100", "001111111111111110", "001111111111111110", "001111111111111110",
            "011111111111111110", "011111111111111110", "011111111111111110", "011111111111111110",
            "011111111111111110", "011111111111111100", "011111111111111110", "011111111111111110",
            "111111111111111110", "111111111111111111", "111111111111111111", "011111111111111111",
            "001111110011111111", "011111110011111111", "111111111111111111", "111111111111111111",
            "111111111111111111", "011111111111111110", "001111111111111110", "001111111111111100",
            "000111111111111100", "000001111111110000",
        ),
        '6' to rows(
            "000000001111100000", "000000111111111000", "000011111111111100", "000111111111111110",
            "001111111111111110", "001111111111111111", "001111111111111111", "011111111111111110",
            "011111111111111110", "011111111111111000", "111111111111111100", "111111111111111110",
            "111111111111111110", "111111111111111111", "111111111111111111", "111111110001111111",
            "111111110001111111", "011111110001111111", "011111111001111111", "001111111111111111",
            "001111111111111111", "001111111111111111", "001111111111111110", "000111111111111100",
            "000011111111111100", "000000111111110000",
        ),
        '6' to rows(
            "000000011110000000", "000000111111110000", "000011111111111100", "000111111111111100",
            "001111111111111110", "001111111111111111", "001111111111111111", "011111111111111111",
            "011111111111111110", "011111111111111100", "111111111111111100", "111111111111111110",
            "111111111111111110", "111111111111111111", "111111111111111111", "111111111111111111",
            "111111110011111111", "111111111001111111", "011111111111111111", "011111111111111111",
            "001111111111111110", "001111111111111110", "001111111111111110", "000111111111111100",
            "000011111111111100", "000000111111110000",
        ),
        '7' to rows(
            "011111111111111111", "111111111111111111", "111111111111111111", "111111111111111111",
            "111111111111111111", "111111111111111111", "111111111111111111", "111111111111111110",
            "000000001111111100", "000000011111111000", "000000111111111000", "000000111111110000",
            "000000111111110000", "000001111111100000", "000001111111100000", "000001111111000000",
            "000011111111000000", "000011111111000000", "000111111111000000", "000111111111000000",
            "000111111111000000", "000111111110000000", "000111111110000000", "000111111110000000",
            "000111111110000000", "000111111110000000",
        ),
        '8' to rows(
            "000000011111000000", "000001111111110000", "000111111111111100", "000111111111111100",
            "001111111111111110", "001111111111111110", "001111111111111110", "001111111011111110",
            "001111110001111110", "001111111111111110", "001111111111111110", "001111111111111110",
            "001111111111111110", "001111111111111110", "011111111111111111", "011111111111111111",
            "011111111111111111", "111111110001111111", "011111110001111111", "011111111111111111",
            "011111111111111111", "011111111111111111", "001111111111111110", "000111111111111100",
            "000111111111111100", "000000111111100000",
        ),
        '8' to rows(
            "000000111111100000", "000001111111110000", "000111111111111100", "000111111111111100",
            "001111111111111110", "001111111111111110", "001111111111111110", "001111111111111110",
            "001111111111111110", "001111111111111110", "001111111111111110", "001111111111111110",
            "001111111111111110", "001111111111111110", "011111111111111111", "011111111111111111",
            "111111111111111111", "111111110001111111", "111111111111111111", "111111111111111111",
            "011111111111111111", "011111111111111111", "001111111111111110", "000111111111111100",
            "000111111111111100", "000001111111110000",
        ),
        '9' to rows(
            "000000111110000000", "000001111111100000", "000111111111111000", "001111111111111100",
            "001111111111111110", "011111111111111110", "011111111111111110", "011111111111111110",
            "111111110011111111", "111111110001111111", "111111110001111111", "111111111111111111",
            "011111111111111111", "001111111111111111", "001111111111111111", "001111111111111111",
            "000111111111111111", "000111111111111111", "001111110011111110", "011111111111111110",
            "011111111111111110", "011111111111111110", "001111111111111100", "000111111111111100",
            "000111111111111000", "000001111111100000",
        ),
        '+' to rows(
            "000000011111000000", "000000111111100000", "000000111111100000", "000000111111100000",
            "000000111111100000", "000000111111100000", "000000111111100000", "000000111111100000",
            "011111111111111111", "111111111111111111", "111111111111111111", "111111111111111111",
            "111111111111111111", "111111111111111111", "111111111111111111", "111111111111111111",
            "111111111111111111", "011111111111111111", "001111111111111110", "000000111111100000",
            "000000111111100000", "000000111111100000", "000000111111100000", "000000111111100000",
            "000000111111100000", "000000111111100000",
        ),
        '+' to rows(
            "000000111111000000", "000000111111000000", "000000111111100000", "000001111111100000",
            "000001111111100000", "000001111111100000", "000001111111100000", "001111111111111100",
            "011111111111111111", "111111111111111111", "111111111111111111", "111111111111111111",
            "111111111111111111", "111111111111111111", "111111111111111111", "111111111111111111",
            "111111111111111111", "111111111111111111", "011111111111111111", "001111111111111100",
            "000001111111100000", "000001111111100000", "000001111111100000", "000001111111100000",
            "000000111111000000", "000000111111000000",
        ),
        '^' to rows(
            "000000000100000000", "000000001110000000", "000000011111000000", "000001111111100000",
            "000011110001110000", "000011110001111000", "001111110000011110", "001111111000001110",
            "011111111110000111", "111111111111111111", "111111111111111111", "111111111111111111",
            "111111111111111111", "111111111111111111", "111111111111111111", "011111111111111111",
            "011111111111111110", "011111111111111111", "111111111111111111", "111111111111111111",
            "111111111111111111", "111111111111111111", "111111110011111111", "111111100000111111",
            "111111000000011111", "011110000000001111",
        ),
    )

    private fun rows(vararg lines: String): BooleanArray {
        val out = BooleanArray(TW * TH)
        for (y in 0 until TH) {
            val line = lines[y]
            for (x in 0 until TW) out[y * TW + x] = line[x] == '1'
        }
        return out
    }
}
