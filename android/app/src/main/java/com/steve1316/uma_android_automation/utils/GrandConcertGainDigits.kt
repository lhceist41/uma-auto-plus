package com.steve1316.uma_android_automation.utils

/**
 * Pure-pixel reader for the Grand Concert training panel's floating "+N" performance-gain amount.
 *
 * The gain glyph is a warm gold-to-red vertical gradient with a thick white outline, in a fixed
 * game font, left-anchored in the same box on every capture (see [GrandConcertTrainingGeometry]).
 * The general OCR path reads it only ~68% of the time: after grayscale binarisation the warm digit
 * strokes fall on the same side of the threshold as the arbitrary background art behind the panel,
 * so the number fuses with its background and does not segment. Colour/glyph DETECTION is robust
 * (the white outline separates the glyph from art), but the numeric read was the fragile part.
 *
 * This reader keeps the read in pixel space, exploiting the two properties that make the glyph
 * separable without OCR: every digit is bounded by the white outline (background warm art is not),
 * and the font is fixed. It isolates each digit as a warm connected component that is enclosed by
 * white, resizes it to a fixed grid, and matches it against templates measured from real captures.
 * A digit is only accepted when its best template match clears an absolute floor AND beats the
 * runner-up by a margin; anything short returns null, so the caller keeps its conservative "amount
 * unread" fallback rather than a fabricated number. Measured on a full 291-glyph career corpus:
 * 286 exact reads, 0 false reads, 5 conservative nulls (all a background sliver that over-segments).
 *
 * Android-free by construction: it reads through [SparkPixelSampler] exactly like the probes, so it
 * is pinned by JUnit fixtures over decoded PNGs with no device or OCR engine involved.
 */
object GrandConcertGainDigits {
    // Glyph-fill (warm) and outline (white) pixel tests, in the same channel order the runtime
    // sampler and the probes use. Warm is slightly looser than the detection probe's isGainWarm so
    // the whole gold-to-red body is captured; the enclosure-by-white test below rejects warm art.
    private fun isWarm(r: Int, g: Int, b: Int): Boolean = r >= 200 && b <= 170 && r - b >= 45

    private fun isWhite(r: Int, g: Int, b: Int): Boolean = r >= 225 && g >= 218 && b >= 200

    // Component acceptance gates, all measured on the real-capture corpus.
    private const val COMP_MIN_SIZE = 120
    private const val COMP_MIN_H = 12
    private const val COMP_MAX_H = 66
    private const val COMP_MIN_W = 6
    private const val COMP_MAX_W = 40

    /** Minimum fraction of a component's dilated border that must be white outline. Real digits and
     * the "+" read 0.19..0.39; a warm background blob reads ~0.02, so 0.10 sits in the gap. */
    private const val WHITE_ADJ_MIN = 0.10

    // Template grid and the classifier's acceptance floor / margin.
    private const val TW = 18
    private const val TH = 26
    private const val SCORE_MIN = 0.82
    private const val MARGIN_MIN = 0.03

    /**
     * Reads the "+N" amount beside performance row [rowIndex] (0..4), or null when it cannot be read
     * confidently. The returned value is already range-checked to the plausible per-training band.
     */
    fun readGainAmount(sampler: SparkPixelSampler, rowIndex: Int): Int? {
        val region = GrandConcertTrainingGeometry.perfGainAmountOcrRegion(rowIndex)
        val cx = region[0]
        val cy = region[1]
        val w = region[2]
        val h = region[3]

        val warm = BooleanArray(w * h)
        val white = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = sampler.argb(cx + x, cy + y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val i = y * w + x
                if (isWarm(r, g, b)) warm[i] = true else if (isWhite(r, g, b)) white[i] = true
            }
        }

        val comps = warmComponents(warm, white, w, h)
        // Leftmost component is the "+" sign; drop it. Then exactly one or two digit components.
        if (comps.size < 2) return null
        val digits = comps.subList(1, comps.size)
        if (digits.size > 2) return null

        var value = 0
        for (comp in digits) {
            val d = classify(resizeToGrid(comp, w)) ?: return null
            value = value * 10 + d
        }
        return if (value in 1..40) value else null
    }

    /** One accepted glyph component: its bounding box and its warm-cell membership on the crop grid. */
    private class Comp(val x0: Int, val x1: Int, val y0: Int, val y1: Int, val mask: BooleanArray)

    /** 8-connected warm components, filtered to glyph-like ones (size, dimensions, white-enclosure),
     * returned left to right. */
    private fun warmComponents(warm: BooleanArray, white: BooleanArray, w: Int, h: Int): MutableList<Comp> {
        val labels = IntArray(w * h) { -1 }
        val stack = ArrayDeque<Int>()
        val result = mutableListOf<Comp>()
        var next = 0
        for (start in 0 until w * h) {
            if (!warm[start] || labels[start] != -1) continue
            // Flood-fill this component (8-connectivity).
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
                val cxp = c % w
                val cyp = c / w
                if (cxp < minX) minX = cxp
                if (cxp > maxX) maxX = cxp
                if (cyp < minY) minY = cyp
                if (cyp > maxY) maxY = cyp
                var dy = -1
                while (dy <= 1) {
                    var dx = -1
                    while (dx <= 1) {
                        if (dx != 0 || dy != 0) {
                            val nx = cxp + dx
                            val ny = cyp + dy
                            if (nx in 0 until w && ny in 0 until h) {
                                val ni = ny * w + nx
                                if (warm[ni] && labels[ni] == -1) {
                                    labels[ni] = next
                                    stack.addLast(ni)
                                }
                            }
                        }
                        dx++
                    }
                    dy++
                }
            }
            next++
            if (cells.size < COMP_MIN_SIZE) continue
            val cw = maxX - minX + 1
            val ch = maxY - minY + 1
            if (ch < COMP_MIN_H || ch > COMP_MAX_H || cw < COMP_MIN_W || cw > COMP_MAX_W) continue
            if (whiteAdjacency(cells, warm, white, w, h) < WHITE_ADJ_MIN) continue
            val mask = BooleanArray(w * h)
            for (c in cells) mask[c] = true
            result.add(Comp(minX, maxX, minY, maxY, mask))
        }
        result.sortBy { it.x0 }
        return result
    }

    /** Fraction of the component's Manhattan-distance<=2 border ring (cells outside the component)
     * that is white outline. Mirrors two 4-connected dilations minus the component. */
    private fun whiteAdjacency(cells: List<Int>, warm: BooleanArray, white: BooleanArray, w: Int, h: Int): Double {
        val ring = HashSet<Int>()
        for (c in cells) {
            val x = c % w
            val y = c / w
            var dy = -2
            while (dy <= 2) {
                var dx = -2
                while (dx <= 2) {
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) <= 2) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until w && ny in 0 until h) ring.add(ny * w + nx)
                    }
                    dx++
                }
                dy++
            }
        }
        // The ring must exclude the component's own cells.
        for (c in cells) ring.remove(c)
        if (ring.isEmpty()) return 0.0
        var whiteCount = 0
        for (ni in ring) if (white[ni]) whiteCount++
        return whiteCount.toDouble() / ring.size
    }

    /** Nearest-neighbour resize of one component's warm mask (within its bbox) to the [TW]x[TH]
     * template grid. */
    private fun resizeToGrid(comp: Comp, w: Int): BooleanArray {
        val bw = comp.x1 - comp.x0 + 1
        val bh = comp.y1 - comp.y0 + 1
        val out = BooleanArray(TW * TH)
        for (ty in 0 until TH) {
            val sy = comp.y0 + (ty * bh) / TH
            for (tx in 0 until TW) {
                val sx = comp.x0 + (tx * bw) / TW
                out[ty * TW + tx] = comp.mask[sy * w + sx]
            }
        }
        return out
    }

    /** Best-matching digit for a resized glyph, or null when the match is not confident enough. */
    private fun classify(bmp: BooleanArray): Int? {
        var bestDigit = -1
        var best = -1.0
        var second = -1.0
        for (d in 0..9) {
            val tmpl = TEMPLATES[d]
            var diff = 0
            for (i in 0 until TW * TH) if (bmp[i] != tmpl[i]) diff++
            val score = 1.0 - diff.toDouble() / (TW * TH)
            if (score > best) {
                second = best
                best = score
                bestDigit = d
            } else if (score > second) {
                second = score
            }
        }
        if (best < SCORE_MIN || best - second < MARGIN_MIN) return null
        return bestDigit
    }

    /** Digit templates (18x26), measured from real Grand Concert training captures. Row strings of
     * '1'/'0'; index 0..9 is the digit. */
    private val TEMPLATES: Array<BooleanArray> = arrayOf(
        rows(
            "000000001110000000", "000001111111100000", "000110111111111100", "001111111111111110",
            "000111111111111100", "001111111111111100", "001111111111111110", "011111111011111110",
            "011111100001111111", "011111100000111111", "011111000000111111", "111111000000011111",
            "111111000000011111", "111111000000011111", "111111000000011111", "111111000000011111",
            "111111000000011111", "111111000000011111", "011111000000111111", "011111000000111111",
            "011111100001111111", "001111110001111110", "001111111111111110", "001111111111111110",
            "000111111111111100", "000011111111110000",
        ),
        rows(
            "000000000111111110", "000000000111111111", "000000001111111111", "000000011111111111",
            "000001111111111111", "001111111111111110", "010011111111111110", "101111111111111100",
            "101111111111111100", "000111111111111100", "000111111111111100", "000010000111111100",
            "000000000111111100", "000000000111111100", "000000000111111100", "000000000111111100",
            "000000000111111100", "000000000111111100", "000000000111111100", "000000000111111100",
            "000000000111111100", "000000000111111100", "000000000111111100", "000000000111111100",
            "000000000111111100", "000000000111111100",
        ),
        rows(
            "000000001110000000", "000001111111100000", "001111111111111000", "001111111111111010",
            "011111111111111111", "101111111111111111", "011111111111111111", "011111110111111110",
            "111111100001111110", "111111000001111110", "000011000001111110", "000000000001111110",
            "000000000111111110", "000000001111111100", "000000111111111000", "000001111111100000",
            "000011111111000000", "000111111100000000", "001111111000000000", "001111110000000000",
            "011111100000000000", "011111111111111110", "011111111111111110", "111111111111111110",
            "111111111111111110", "111111111111111110",
        ),
        rows(
            "000000111111110000", "000011111111111000", "001111111111111010", "011111111111111110",
            "101111111111111111", "001111111111111110", "011111111111111110", "011111100001111110",
            "000011000001111110", "000000000001111110", "000000000111111110", "000000111111111100",
            "000000111111111000", "000000111111110000", "000000111111111100", "000000111111111110",
            "000000000001111110", "000000000000111110", "000000000000111111", "000110000000111111",
            "111111000001111110", "111111000011111110", "011111111111111110", "011111111111111110",
            "001111111111111000", "000111111111111000",
        ),
        rows(
            "000000000111111100", "000000001111111100", "000000001111111110", "000000011111111110",
            "000000111111111110", "000000011111111110", "000000011111111110", "000000111111111000",
            "000001111111111000", "000001111111111000", "000011111111111000", "000011111011111000",
            "000111111011111000", "001111100011111000", "001111100011111000", "011111100011111000",
            "111110000011111000", "111111111111111111", "111111111111111111", "111111111111111111",
            "111111111111111111", "111111111111111111", "000000000011111000", "000000000011111000",
            "000000000011111000", "000000000011111000",
        ),
        rows(
            "001111111111111110", "011111111111111111", "101111111111111111", "111111111111111110",
            "111111111111111110", "001111111111111110", "011111111111111110", "001111100110000110",
            "001111100000000000", "001111100000000000", "001111111111111000", "001111111111111100",
            "011111111111111110", "011111111111111111", "011111110001111111", "000011000000011111",
            "000000000000011111", "000000000000011111", "000000000000011111", "001111000000011111",
            "011111100000111111", "011111111111111111", "001111111111111110", "001111111111111100",
            "000111111111111100", "000000111111100000",
        ),
        rows(
            "000000111110110000", "000001111111111000", "000111111111111100", "001111111111111110",
            "001111111111111111", "011111111111111111", "101111111001111110", "001111100000111100",
            "001111000000010000", "001111000000000000", "011111011111100000", "011111111111110000",
            "011111111111111100", "011111111111111100", "011111111111111110", "011111100001111110",
            "011111000000111110", "011111000000011110", "011111000000011110", "011111000000011110",
            "001111100000111110", "001111100000111110", "000111111111111100", "000111111111111100",
            "000011111111111000", "000001111111110000",
        ),
        rows(
            "001111111111111110", "011111111111111111", "111111111111111111", "111111111111111111",
            "111111111111111111", "111111111111111111", "101111111111111111", "101111111111111101",
            "000000000011111000", "000000000111111000", "000000000111110000", "000000001111100000",
            "000000011111100000", "000000011111000000", "000000011111000000", "000000011111000000",
            "000000111111000000", "000000111110000000", "000000111110000000", "000000111110000000",
            "000001111100000000", "000001111100000000", "000001111100000000", "000001111100000000",
            "000001111100000000", "000001111100000000",
        ),
        rows(
            "000001111111100000", "000011111111111000", "001111111111111100", "001111111111111010",
            "011111111111111111", "011111111111111111", "101111110001111111", "001111100000111110",
            "001111000000111110", "001111100001111110", "001111110011111100", "000111111111111100",
            "000011111111111000", "000011111111111000", "001111111111111110", "001111111111111110",
            "011111100000111110", "011111000000111111", "011111000000111111", "011111000000111111",
            "011111000000111110", "011111100001111110", "001111111111111110", "001111111111111110",
            "000111111111111000", "000001111111110000",
        ),
        rows(
            "000001111111100000", "000011111111110000", "001111111111111000", "000111111111111100",
            "111111111111111100", "001111111111111101", "001111110011111111", "011111100001111110",
            "011111000000111110", "011111000000111110", "011111000000111111", "011111000000111111",
            "011111100001111111", "001111110011111111", "001111111111111111", "001111111111111111",
            "000011111111011111", "000001111110011110", "000000000000111110", "000000000000111110",
            "000111000000111110", "001111000001111110", "001111111111111100", "001111111111111000",
            "000111111111110000", "000011111111110000",
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
