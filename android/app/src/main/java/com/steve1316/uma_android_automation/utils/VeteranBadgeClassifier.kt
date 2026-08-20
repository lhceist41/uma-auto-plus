package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_scoring.rankLabelToImageIndex

/**
 * Pixel classifiers for the styled finite-domain fields on the read-only `Umamusume Details`
 * dialog: the circular rank medal, the five stat-grade badges, and the ten aptitude-grade letters.
 *
 * These fields defeated generic grayscale-threshold OCR on live MediaProjection captures (PL-R1a,
 * 2026-08-20: rank OCR'd the "RANK" ribbon word, stat grades and most aptitude grades came back
 * unresolved). The game renders every one of them as a fixed, saturated, colour-coded glyph, so a
 * deterministic colour/template classifier reads them far more reliably than OCR and - unlike the
 * native Tesseract/OpenCV OCR path - runs unchanged in a JVM unit test through [SparkPixelSampler].
 *
 * Grade colour ground truth (median hue of the glyph's saturated pixels, measured on the
 * 1080x1920 PL-R1 fixture set 02-details-skills-tab.png and 08-details-chevron-next-entry.png):
 * A ~25 (orange), S ~42 (gold), C ~106 (green), D ~204 (cyan), F ~245 (blue-purple),
 * E ~286 (magenta-purple), B ~342 (pink); G is achromatic (grey). Each grade also carries the
 * game's shared rank-tier vocabulary, so a composed token is validated against
 * [rankLabelToImageIndex] and returned only when it is a real tier (never a guess).
 */

/** A pixel rectangle in absolute 1080x1920 capture coordinates, half-open on the far edges. */
data class GlyphBox(val x0: Int, val y0: Int, val x1: Int, val y1: Int)

// -- Geometry (measured directly on the PL-R1 fixtures; the detail dialog is fixed-layout) --------

/** Stat-grade badge glyph boxes (the colour letter only, number excluded), index-aligned with
 * [STAT_LABELS]. The brown stat number shares orange's hue, so each box stops before it. */
val STAT_GRADE_GLYPH_BOXES: List<GlyphBox> =
    listOf(
        GlyphBox(46, 502, 116, 578),
        GlyphBox(250, 502, 303, 578),
        GlyphBox(452, 502, 503, 578),
        GlyphBox(651, 502, 705, 578),
        GlyphBox(851, 502, 906, 578),
    )

/** Stat value (number) boxes, to the right of each grade badge, for digit-only OCR. Index-aligned
 * with [STAT_LABELS]. Reading the number from its own box (not the whole cell) keeps the coloured
 * badge glyph out of the digit OCR - the miss that turned Speed 949 into "6V6" on the first pass. */
val STAT_VALUE_BOXES: List<GlyphBox> =
    listOf(
        GlyphBox(118, 505, 216, 576),
        GlyphBox(330, 505, 416, 576),
        GlyphBox(526, 505, 612, 576),
        GlyphBox(728, 505, 814, 576),
        GlyphBox(928, 505, 1016, 576),
    )

/** Aptitude-grade letter boxes keyed by role. Each box is tight around the grade letter at the
 * right of its pill, past the brown label word (same hue as an orange grade) and short of the green
 * next-entry chevron on the two outermost columns. */
val APTITUDE_GRADE_BOXES: Map<String, GlyphBox> =
    mapOf(
        "turf" to GlyphBox(386, 606, 424, 644),
        "dirt" to GlyphBox(576, 606, 614, 644),
        "sprint" to GlyphBox(386, 666, 424, 704),
        "mile" to GlyphBox(576, 666, 614, 704),
        "medium" to GlyphBox(768, 666, 804, 704),
        "long" to GlyphBox(956, 666, 992, 704),
        "front" to GlyphBox(386, 726, 424, 764),
        "pace" to GlyphBox(576, 726, 614, 764),
        "late" to GlyphBox(768, 726, 804, 764),
        "end" to GlyphBox(956, 726, 992, 764),
    )

/** The ten aptitude roles in [APTITUDE_GRADE_BOXES] order, so a positional aptitude list (the scan
 * records, the roster fingerprint) and the box map can never drift apart. */
val APTITUDE_ROLES: List<String> = APTITUDE_GRADE_BOXES.keys.toList()

/** The circular rank medal region (medal + "RANK" ribbon). */
val RANK_MEDAL_BOX: GlyphBox = GlyphBox(350, 168, 478, 288)

// -- Detail dialog chevrons: enabled/disabled by counting the chevron's vivid green outline --------

/**
 * The next/prev chevron sample boxes. Deliberately generous rather than a point sample: the glyph
 * pulses horizontally by several pixels between frames (the two committed fixtures happen to catch
 * it at different offsets), so the stable signal is how much chevron green lives inside the box, not
 * what colour any one pixel is.
 */
val CHEVRON_NEXT_BOX: GlyphBox = GlyphBox(1010, 630, 1074, 740)
val CHEVRON_PREV_BOX: GlyphBox = GlyphBox(6, 630, 70, 740)

/** Whether the chevron is pressable. UNKNOWN is a real answer, not a failure: the walk keeps going
 * on UNKNOWN and lets the account's own Registered count decide the end. */
enum class ChevronState { ENABLED, DISABLED, UNKNOWN }

/** Measured on both committed fixtures: 174 and 178 green samples inside the enabled chevron box,
 * and 0 in every control region beside and below it. 60 sits an order of magnitude clear of the
 * background and well under the observed population, so a partially clipped or recoloured chevron
 * still reads ENABLED, while a greyed-out or absent one reads DISABLED. */
const val CHEVRON_ENABLED_MIN_GREEN = 60
const val CHEVRON_DISABLED_MAX_GREEN = 10

private fun isChevronGreen(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return g > 110 && (g - r) > 40 && (g - b) > 60
}

/** How many sampled pixels in [box] carry the chevron's vivid green. Exposed so a calibration run
 * can log the raw population next to the verdict instead of only the verdict. */
fun countChevronGreen(sampler: SparkPixelSampler, box: GlyphBox): Int {
    var count = 0
    var y = box.y0
    while (y < box.y1) {
        var x = box.x0
        while (x < box.x1) {
            if (isChevronGreen(sampler.argb(x, y))) count++
            x += 2
        }
        y += 2
    }
    return count
}

/**
 * The chevron's state from its green population. The DISABLED branch is the one PL-R1 could not
 * capture a fixture for (no last-entry frame existed), so it is written to need positive evidence of
 * an absent chevron; anything in between reports UNKNOWN and the walk continues.
 */
fun classifyChevron(sampler: SparkPixelSampler, box: GlyphBox = CHEVRON_NEXT_BOX): ChevronState {
    val green = countChevronGreen(sampler, box)
    return when {
        green >= CHEVRON_ENABLED_MIN_GREEN -> ChevronState.ENABLED
        green <= CHEVRON_DISABLED_MAX_GREEN -> ChevronState.DISABLED
        else -> ChevronState.UNKNOWN
    }
}

// -- Colour classification --------------------------------------------------------------------

private const val GRADE_SAT_MIN = 0.32
private const val GRADE_VAL_MIN = 0.30
private const val GRAY_MAX = 205
private const val GRAY_MIN = 40
private const val GRAY_CHROMA_MAX = 45

/** Minimum saturated-pixel count for a coloured grade letter to be trusted (letters measure 290+;
 * anti-alias noise is well under this). */
private const val COLORED_MIN = 150

/** A grey grade letter (G) has this many achromatic glyph pixels and almost no coloured ones. */
private const val GRAY_LETTER_MIN = 150
private const val COLORED_SUPPRESS = 60

/** Max hue distance (degrees) from a centroid before the colour is rejected as not-a-grade. Every
 * observed grade sits within ~1 degree of its centroid; the nearest two centroids (A 25, S 42) are
 * 17 degrees apart, so 22 both separates them and rejects off-palette colours. */
private const val HUE_GATE = 22.0

/** Stat-grade "+" is a separate blob in the glyph's top-right: colour mass biased to the top of the
 * rightmost band, and the whole glyph runs wider than a plain letter. Both hold together only for a
 * real "+" (A+ 1.20/w56, SS+ 1.17/w61; plain B/C/D <=0.68/w<=46). */
private const val PLUS_TOP_BOTTOM_RATIO = 0.9
private const val PLUS_MIN_WIDTH = 50

/** Within the gold (S) family, a doubled "SS" body runs wider than a single "S". Calibrated on the
 * one SS+ fixture (w61) against single-letter widths (42-46); single gold tiers await live proof. */
private const val DOUBLE_MIN_WIDTH = 58

/** Fraction of the glyph width, from the right, that the "+" probe inspects. */
private const val PLUS_RIGHT_BAND = 0.62

private fun redOf(argb: Int): Int = (argb shr 16) and 0xFF

private fun greenOf(argb: Int): Int = (argb shr 8) and 0xFF

private fun blueOf(argb: Int): Int = argb and 0xFF

/** Hue in [0, 360) for a colour, or -1.0 when it is achromatic (max == min). */
private fun hueOf(r: Int, g: Int, b: Int): Double {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    if (delta == 0) return -1.0
    val h =
        when (max) {
            r -> 60.0 * (((g - b).toDouble() / delta) % 6.0)
            g -> 60.0 * (((b - r).toDouble() / delta) + 2.0)
            else -> 60.0 * (((r - g).toDouble() / delta) + 4.0)
        }
    return if (h < 0) h + 360.0 else h
}

/** Circular distance between two hues in degrees. */
private fun hueDistance(a: Double, b: Double): Double {
    val d = kotlin.math.abs(a - b) % 360.0
    return if (d > 180.0) 360.0 - d else d
}

private val GRADE_HUE_CENTROIDS: List<Pair<Char, Double>> =
    listOf('A' to 25.0, 'S' to 42.0, 'C' to 106.0, 'D' to 204.0, 'F' to 245.0, 'E' to 286.0, 'B' to 342.0)

/** Nearest grade-family letter for a hue, or null when nothing is within [HUE_GATE]. */
private fun nearestGradeFamily(hue: Double): Char? {
    var best: Char? = null
    var bestDist = HUE_GATE
    for ((letter, centroid) in GRADE_HUE_CENTROIDS) {
        val d = hueDistance(hue, centroid)
        if (d <= bestDist) {
            bestDist = d
            best = letter
        }
    }
    return best
}

private class GlyphColorStats(
    val coloredCount: Int,
    val grayCount: Int,
    val medianHue: Double?,
    val width: Int,
    val topRightColored: Int,
    val bottomRightColored: Int,
)

/** One pass over [box]: counts coloured vs grey glyph pixels, their bounding box, the median hue of
 * the coloured ones, and the top-vs-bottom coloured split in the rightmost band (the "+" probe). */
private fun glyphStats(sampler: SparkPixelSampler, box: GlyphBox): GlyphColorStats {
    val hues = ArrayList<Double>()
    var colored = 0
    var gray = 0
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var maxY = Int.MIN_VALUE
    for (y in box.y0 until box.y1) {
        for (x in box.x0 until box.x1) {
            val argb = sampler.argb(x, y)
            val r = redOf(argb)
            val g = greenOf(argb)
            val b = blueOf(argb)
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val chroma = max - min
            val isColored = max / 255.0 >= GRADE_VAL_MIN && (if (max == 0) 0.0 else chroma.toDouble() / max) >= GRADE_SAT_MIN
            val isGray = max in (GRAY_MIN + 1)..(GRAY_MAX - 1) && chroma < GRAY_CHROMA_MAX
            if (isColored) {
                colored++
                hues.add(hueOf(r, g, b))
            } else if (isGray) {
                gray++
            } else {
                continue
            }
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
    }
    if (colored == 0 && gray == 0) return GlyphColorStats(0, 0, null, 0, 0, 0)
    val medianHue =
        if (hues.isEmpty()) {
            null
        } else {
            hues.sort()
            hues[hues.size / 2]
        }
    val width = maxX - minX
    val height = maxY - minY
    val bandX = minX + (width * PLUS_RIGHT_BAND).toInt()
    val midY = minY + height / 2
    var top = 0
    var bottom = 0
    for (y in box.y0 until box.y1) {
        for (x in bandX until box.x1) {
            val argb = sampler.argb(x, y)
            val r = redOf(argb)
            val g = greenOf(argb)
            val b = blueOf(argb)
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val isColored = max / 255.0 >= GRADE_VAL_MIN && (if (max == 0) 0.0 else (max - min).toDouble() / max) >= GRADE_SAT_MIN
            if (!isColored) continue
            if (y < midY - 2) top++ else if (y > midY + 2) bottom++
        }
    }
    return GlyphColorStats(colored, gray, medianHue, width, top, bottom)
}

private fun familyOf(stats: GlyphColorStats): Char? =
    when {
        stats.coloredCount >= COLORED_MIN -> stats.medianHue?.let { nearestGradeFamily(it) }
        stats.grayCount >= GRAY_LETTER_MIN && stats.coloredCount < COLORED_SUPPRESS -> 'G'
        else -> null
    }

/**
 * The single-letter aptitude grade (S/A/B/C/D/E/F/G) in [box], or null when the colour is off-palette
 * or the cell is blank/ambiguous. Aptitude grades never carry a "+", so colour alone determines them.
 */
fun classifyAptitudeGrade(sampler: SparkPixelSampler, box: GlyphBox): String? {
    val family = familyOf(glyphStats(sampler, box)) ?: return null
    return family.toString().takeIf { it in APTITUDE_GRADES }
}

/**
 * The full stat-grade token in [box] - base letter from colour, "+" and gold "SS" doubling from
 * glyph structure - or null when unresolved. The token is validated against the real rank ladder so
 * an impossible combination (or an off-palette colour) returns null rather than a guess.
 */
fun classifyStatGrade(sampler: SparkPixelSampler, box: GlyphBox): String? {
    val stats = glyphStats(sampler, box)
    val family = familyOf(stats) ?: return null
    val plus = stats.topRightColored.toDouble() / maxOf(stats.bottomRightColored, 1) >= PLUS_TOP_BOTTOM_RATIO && stats.width >= PLUS_MIN_WIDTH
    val doubled = family == 'S' && stats.width >= DOUBLE_MIN_WIDTH
    val token = (if (doubled) "SS" else family.toString()) + if (plus) "+" else ""
    return token.takeIf { rankLabelToImageIndex(it) >= 0 }
}

// -- Rank medal: whole-medal grayscale template correlation -----------------------------------

private const val RANK_TEMPLATE_N = 20

/** Minimum normalized cross-correlation to accept a rank-medal template. The same A medal on two
 * independent captures correlates 0.95; unrelated regions score near 0, so 0.85 both accepts a real
 * match and rejects a different medal (whose letter and colour change the grayscale pattern). */
private const val RANK_MATCH_MIN_NCC = 0.85

private class RankMedalTemplate(val label: String, val family: Char, val data: IntArray)

/**
 * Rank-medal references, each a 20x20 integer box-average luma downsample of [RANK_MEDAL_BOX]. Only
 * the "A" medal is calibrated (both PL-R1 fixtures are A rank); other tiers stay unresolved until a
 * fixture supplies them, which fail-closed blocks the fingerprint rather than guessing a rank. The A
 * template was generated from 02-details-skills-tab.png with the exact integer math [downsampleLuma]
 * uses, so runtime and the offline fixture test compute bit-identical downsamples.
 */
private val RANK_MEDAL_TEMPLATES: List<RankMedalTemplate> =
    listOf(
        RankMedalTemplate(
            "A",
            'A',
            intArrayOf(
                210, 210, 211, 211, 225, 233, 195, 213, 230, 222, 203, 189, 163, 174, 182, 224, 208, 211, 211, 211,
                212, 212, 214, 222, 230, 203, 228, 231, 235, 214, 251, 251, 243, 178, 210, 169, 217, 206, 211, 213,
                216, 215, 218, 237, 204, 230, 231, 221, 197, 223, 250, 250, 251, 159, 213, 213, 172, 207, 212, 214,
                239, 235, 230, 208, 219, 227, 215, 205, 173, 250, 240, 237, 252, 156, 208, 212, 202, 173, 193, 207,
                247, 244, 228, 206, 219, 213, 204, 183, 205, 254, 187, 221, 254, 168, 174, 208, 210, 164, 184, 205,
                243, 234, 216, 212, 212, 201, 196, 162, 251, 247, 128, 197, 255, 187, 163, 184, 204, 182, 170, 204,
                231, 215, 212, 207, 209, 201, 185, 183, 228, 204, 152, 188, 228, 195, 148, 169, 193, 189, 170, 222,
                231, 214, 208, 205, 205, 198, 152, 231, 235, 235, 235, 235, 235, 223, 131, 158, 188, 185, 176, 222,
                227, 211, 209, 203, 201, 178, 180, 245, 219, 188, 187, 190, 245, 244, 125, 157, 186, 179, 173, 191,
                229, 219, 202, 194, 195, 143, 241, 249, 135, 139, 137, 113, 246, 251, 133, 163, 179, 161, 178, 203,
                219, 217, 207, 180, 180, 174, 240, 203, 138, 164, 162, 126, 223, 240, 145, 175, 176, 150, 199, 224,
                205, 205, 206, 179, 181, 129, 120, 120, 159, 160, 158, 144, 104, 102, 124, 168, 164, 174, 196, 211,
                206, 206, 123, 123, 127, 160, 165, 159, 148, 146, 145, 143, 143, 137, 141, 147, 113, 121, 145, 235,
                240, 240, 178, 91, 95, 218, 204, 156, 198, 195, 174, 214, 193, 168, 202, 168, 98, 90, 193, 233,
                225, 224, 161, 94, 115, 221, 213, 135, 212, 198, 188, 208, 191, 183, 219, 112, 108, 89, 173, 210,
                199, 198, 138, 103, 126, 159, 203, 179, 142, 194, 197, 153, 182, 196, 153, 152, 105, 114, 160, 222,
                222, 222, 222, 190, 153, 131, 113, 106, 126, 106, 108, 119, 112, 116, 136, 152, 171, 209, 242, 230,
                204, 205, 206, 226, 233, 229, 211, 208, 224, 223, 227, 228, 241, 225, 233, 238, 236, 235, 243, 231,
                218, 218, 221, 183, 134, 203, 249, 239, 222, 207, 196, 149, 246, 236, 246, 248, 188, 244, 244, 234,
                229, 230, 229, 183, 132, 219, 120, 142, 198, 150, 111, 140, 135, 193, 138, 163, 103, 217, 243, 232,
            ),
        ),
    )

/** Integer box-average luma downsample of [box] to an n-by-n grid. Matches the generator that baked
 * [RANK_MEDAL_TEMPLATES] exactly (same block partition, same integer luma), so no bilinear drift. */
private fun downsampleLuma(sampler: SparkPixelSampler, box: GlyphBox, n: Int): IntArray {
    val w = box.x1 - box.x0
    val h = box.y1 - box.y0
    val out = IntArray(n * n)
    for (by in 0 until n) {
        val sy0 = box.y0 + by * h / n
        val sy1 = box.y0 + (by + 1) * h / n
        for (bx in 0 until n) {
            val sx0 = box.x0 + bx * w / n
            val sx1 = box.x0 + (bx + 1) * w / n
            var sum = 0L
            var count = 0
            for (y in sy0 until sy1) {
                for (x in sx0 until sx1) {
                    val argb = sampler.argb(x, y)
                    sum += (redOf(argb) * 299 + greenOf(argb) * 587 + blueOf(argb) * 114) / 1000
                    count++
                }
            }
            out[by * n + bx] = if (count == 0) 0 else (sum / count).toInt()
        }
    }
    return out
}

/** Normalized cross-correlation of two equal-length signals, in [-1, 1]; 0 when either is flat. */
private fun normalizedCrossCorrelation(a: IntArray, b: IntArray): Double {
    val n = a.size
    var meanA = 0.0
    var meanB = 0.0
    for (i in 0 until n) {
        meanA += a[i]
        meanB += b[i]
    }
    meanA /= n
    meanB /= n
    var num = 0.0
    var varA = 0.0
    var varB = 0.0
    for (i in 0 until n) {
        val da = a[i] - meanA
        val db = b[i] - meanB
        num += da * db
        varA += da * da
        varB += db * db
    }
    val den = kotlin.math.sqrt(varA * varB)
    return if (den == 0.0) 0.0 else num / den
}

/**
 * The rank tier from the medal in [RANK_MEDAL_BOX], or null when no calibrated medal matches. A
 * template must clear [RANK_MATCH_MIN_NCC] and the medal's dominant colour must match the template's
 * grade family (grayscale correlation alone would not tell an orange A medal from a pink B one).
 */
fun classifyRankMedal(sampler: SparkPixelSampler): String? {
    val sample = downsampleLuma(sampler, RANK_MEDAL_BOX, RANK_TEMPLATE_N)
    val ringFamily = familyOf(glyphStats(sampler, RANK_MEDAL_BOX))
    var best: RankMedalTemplate? = null
    var bestScore = RANK_MATCH_MIN_NCC
    for (template in RANK_MEDAL_TEMPLATES) {
        val score = normalizedCrossCorrelation(sample, template.data)
        if (score >= bestScore && template.family == ringFamily) {
            bestScore = score
            best = template
        }
    }
    return best?.label
}

/** Digits-only stat value parse (e.g. "949" -> 949), rejecting an implausible read. Kept as a pure
 * parser so the digit-OCR result stays testable even though the OCR itself needs the device. */
fun parseStatValue(raw: String): Int? = raw.filter { it.isDigit() }.toIntOrNull()?.takeIf { it in 0..2500 }
