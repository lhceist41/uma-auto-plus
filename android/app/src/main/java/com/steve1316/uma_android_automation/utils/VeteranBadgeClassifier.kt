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

/** Minimum normalized cross-correlation to accept a rank-medal template. The same tier's medal on two
 * independent captures correlates >=0.92 (measured over Veterans of every calibrated tier); unrelated
 * regions score near 0, so 0.85 accepts a real match and rejects an off-tier medal. */
private const val RANK_MATCH_MIN_NCC = 0.85

/** Minimum lead the winning template must hold over the next best of the SAME colour family. Within a
 * family the only difference is the "+" (A vs A+, S vs S+), so the loser is the sibling tier: measured
 * cross-sibling correlation tops out at 0.826 while same-tier stays >=0.92, a >=0.10 gap, so 0.04 both
 * separates the two and rejects a genuinely ambiguous medal rather than guessing which side of the "+"
 * it is. Cross-family medals (orange A vs gold S) never compete: the colour gate removes them first. */
private const val RANK_MATCH_MIN_MARGIN = 0.04

private class RankMedalTemplate(val label: String, val family: Char, val data: IntArray)

/**
 * Rank-medal references, each a 20x20 integer box-average luma downsample of [RANK_MEDAL_BOX]. The
 * four tiers the live PL-R1b roster actually carries are calibrated: A and A+ (orange, hue ~18) and S
 * and S+ (gold, hue ~40). The colour gate in [classifyRankMedal] splits the two families, and the
 * whole-medal correlation splits the "+" within a family. Any tier outside these four stays
 * unresolved, which fail-closed blocks the fingerprint rather than guessing a rank.
 *
 * Each template was generated from a live 1080x1920 detail capture (identities in the fixtures'
 * PROVENANCE) with the exact integer math [downsampleLuma] uses, so runtime and the offline fixture
 * test compute bit-identical downsamples. Every template is validated in [VeteranBadgeClassifierTest]
 * against a DIFFERENT Veteran of the same tier (the "_b" fixtures), so a template can never merely
 * echo its own source frame.
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
        RankMedalTemplate(
            "A+",
            'A',
            intArrayOf(
                205, 205, 205, 205, 222, 233, 195, 210, 212, 196, 175, 183, 211, 189, 182, 224, 193, 207, 207, 207,
                205, 205, 205, 216, 230, 203, 228, 214, 241, 251, 250, 173, 218, 221, 211, 179, 218, 201, 206, 207,
                204, 204, 206, 236, 205, 230, 230, 203, 251, 250, 251, 188, 188, 212, 225, 233, 153, 205, 205, 206,
                202, 202, 219, 208, 219, 226, 193, 225, 252, 228, 252, 205, 171, 182, 201, 252, 135, 148, 187, 204,
                200, 200, 221, 206, 219, 211, 169, 254, 238, 179, 254, 227, 158, 241, 246, 251, 240, 203, 150, 203,
                197, 194, 216, 212, 212, 172, 216, 254, 173, 162, 255, 249, 126, 187, 208, 232, 189, 159, 153, 200,
                194, 188, 212, 207, 206, 157, 230, 225, 157, 169, 228, 229, 137, 147, 169, 249, 124, 153, 170, 191,
                191, 187, 208, 205, 175, 207, 235, 235, 235, 235, 235, 235, 147, 171, 133, 160, 136, 185, 175, 183,
                189, 182, 209, 203, 162, 247, 241, 189, 188, 187, 239, 245, 157, 160, 150, 140, 183, 179, 173, 187,
                195, 187, 202, 169, 202, 250, 187, 135, 139, 123, 213, 250, 174, 152, 156, 163, 179, 160, 177, 193,
                186, 187, 203, 137, 237, 236, 129, 168, 166, 148, 183, 240, 185, 139, 155, 175, 176, 150, 188, 191,
                187, 188, 197, 148, 125, 124, 157, 164, 161, 157, 110, 103, 105, 146, 162, 168, 165, 174, 179, 189,
                190, 188, 117, 122, 126, 160, 165, 160, 148, 146, 144, 142, 143, 138, 141, 147, 114, 120, 126, 188,
                190, 192, 149, 91, 95, 218, 204, 156, 198, 195, 174, 214, 193, 168, 202, 168, 98, 89, 161, 193,
                184, 184, 135, 93, 115, 221, 213, 135, 212, 198, 187, 208, 191, 183, 219, 112, 108, 88, 145, 185,
                197, 193, 138, 103, 126, 159, 203, 179, 142, 194, 197, 153, 182, 196, 153, 152, 105, 106, 136, 188,
                201, 202, 203, 176, 147, 133, 119, 107, 127, 106, 104, 111, 102, 106, 128, 148, 167, 199, 211, 205,
                213, 212, 211, 234, 240, 232, 200, 199, 223, 222, 209, 219, 212, 193, 190, 190, 207, 203, 208, 217,
                203, 202, 205, 183, 134, 203, 235, 230, 221, 207, 196, 149, 239, 219, 232, 233, 188, 225, 195, 192,
                204, 204, 207, 183, 132, 219, 120, 142, 198, 150, 111, 140, 135, 193, 138, 163, 103, 212, 200, 199,
            ),
        ),
        RankMedalTemplate(
            "S",
            'S',
            intArrayOf(
                243, 243, 243, 243, 244, 232, 193, 210, 220, 225, 230, 227, 211, 175, 189, 202, 234, 242, 242, 241,
                238, 238, 239, 239, 228, 200, 224, 226, 235, 248, 248, 248, 248, 244, 180, 198, 184, 230, 239, 239,
                231, 231, 232, 236, 204, 233, 236, 225, 248, 239, 193, 187, 235, 243, 172, 212, 193, 185, 234, 234,
                221, 221, 228, 212, 225, 231, 223, 229, 250, 216, 177, 202, 172, 159, 194, 221, 219, 161, 208, 225,
                208, 209, 222, 214, 225, 209, 193, 219, 254, 252, 216, 181, 156, 216, 223, 217, 202, 156, 175, 214,
                187, 188, 216, 231, 232, 199, 220, 181, 244, 252, 252, 252, 231, 157, 189, 178, 199, 173, 155, 199,
                179, 183, 213, 235, 233, 228, 230, 212, 157, 175, 209, 218, 218, 203, 152, 163, 195, 185, 153, 179,
                168, 175, 199, 204, 208, 216, 207, 178, 169, 198, 149, 190, 227, 223, 135, 159, 197, 182, 157, 164,
                173, 175, 209, 225, 216, 194, 195, 240, 190, 177, 164, 186, 240, 209, 147, 206, 222, 183, 160, 165,
                172, 171, 193, 229, 230, 195, 198, 247, 248, 218, 216, 248, 246, 138, 147, 184, 208, 160, 167, 171,
                163, 163, 174, 218, 226, 213, 141, 216, 247, 248, 248, 242, 157, 128, 150, 175, 184, 147, 174, 168,
                163, 162, 164, 192, 217, 199, 165, 125, 134, 144, 132, 107, 131, 148, 165, 179, 165, 167, 157, 159,
                183, 182, 105, 98, 115, 173, 179, 157, 146, 145, 145, 152, 150, 148, 162, 158, 107, 108, 106, 174,
                182, 183, 135, 73, 87, 220, 206, 161, 202, 198, 177, 217, 197, 177, 206, 166, 85, 75, 150, 183,
                173, 173, 123, 75, 102, 218, 211, 135, 208, 195, 185, 207, 190, 182, 217, 96, 85, 74, 135, 175,
                170, 174, 114, 83, 117, 157, 200, 177, 129, 191, 195, 152, 178, 194, 149, 146, 82, 89, 119, 170,
                170, 168, 174, 143, 128, 105, 92, 87, 97, 85, 84, 92, 89, 91, 101, 117, 121, 159, 169, 172,
                175, 173, 171, 213, 227, 222, 178, 170, 212, 214, 196, 217, 196, 174, 182, 169, 189, 171, 172, 185,
                180, 172, 179, 183, 134, 203, 230, 226, 220, 207, 196, 149, 238, 210, 221, 222, 188, 214, 177, 192,
                171, 184, 178, 183, 132, 219, 120, 142, 198, 150, 111, 140, 135, 193, 138, 163, 103, 209, 179, 190,
            ),
        ),
        RankMedalTemplate(
            "S+",
            'S',
            intArrayOf(
                243, 243, 243, 243, 244, 232, 193, 209, 230, 232, 226, 200, 176, 198, 196, 202, 234, 242, 242, 241,
                238, 238, 239, 239, 228, 197, 222, 246, 248, 247, 248, 248, 209, 183, 214, 195, 185, 230, 239, 239,
                231, 231, 232, 236, 204, 225, 242, 248, 211, 180, 206, 248, 211, 174, 222, 235, 169, 185, 234, 234,
                221, 221, 228, 212, 225, 210, 251, 251, 172, 194, 171, 172, 169, 202, 210, 250, 161, 148, 207, 225,
                208, 209, 222, 214, 232, 198, 254, 254, 245, 199, 165, 174, 210, 243, 247, 251, 242, 204, 157, 214,
                187, 188, 216, 235, 234, 191, 202, 249, 249, 249, 248, 195, 150, 189, 205, 224, 184, 153, 139, 199,
                179, 183, 206, 197, 213, 202, 171, 160, 186, 215, 218, 219, 155, 176, 177, 246, 125, 150, 152, 179,
                168, 175, 212, 236, 193, 175, 173, 205, 182, 152, 228, 228, 160, 206, 137, 153, 134, 182, 158, 164,
                173, 175, 209, 237, 174, 236, 236, 161, 187, 157, 241, 240, 146, 193, 154, 152, 190, 175, 160, 165,
                172, 171, 193, 229, 172, 246, 247, 241, 216, 241, 247, 213, 143, 181, 156, 198, 173, 160, 167, 171,
                163, 163, 174, 218, 192, 170, 239, 248, 248, 245, 197, 113, 190, 195, 147, 175, 181, 147, 174, 168,
                163, 162, 164, 192, 217, 170, 125, 134, 133, 114, 123, 162, 177, 199, 168, 179, 167, 167, 157, 159,
                183, 182, 105, 98, 115, 173, 176, 153, 146, 152, 150, 152, 150, 152, 161, 158, 107, 108, 106, 174,
                182, 183, 135, 73, 87, 220, 206, 161, 202, 198, 177, 217, 197, 177, 206, 166, 85, 75, 150, 183,
                173, 173, 123, 75, 102, 218, 211, 135, 208, 195, 185, 207, 190, 182, 217, 96, 86, 74, 135, 175,
                170, 174, 114, 83, 117, 157, 200, 177, 129, 191, 195, 152, 178, 193, 149, 146, 82, 89, 119, 170,
                170, 168, 174, 143, 128, 105, 92, 87, 97, 85, 84, 92, 89, 91, 101, 117, 121, 159, 169, 172,
                175, 173, 171, 213, 227, 222, 178, 170, 212, 214, 196, 217, 196, 174, 182, 169, 189, 171, 172, 185,
                180, 172, 179, 183, 134, 203, 230, 226, 220, 207, 196, 149, 238, 210, 221, 222, 188, 214, 177, 192,
                171, 184, 178, 183, 132, 219, 120, 142, 198, 150, 111, 140, 135, 193, 138, 163, 103, 209, 179, 190,
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
 * The rank tier from the medal in [RANK_MEDAL_BOX], or null when no calibrated medal matches
 * confidently. Two stages, because grayscale correlation alone cannot tell an orange A medal from a
 * gold S one (their letters downsample too alike) and colour alone cannot tell A from A+:
 *  1. The medal's dominant colour picks the family (orange -> A/A+, gold -> S/S+); an off-palette or
 *     unreadable medal has no family and returns null.
 *  2. Within that family, the medal correlates against each tier's template; the winner is accepted
 *     only when it clears [RANK_MATCH_MIN_NCC] and leads its same-family sibling (the "+"/no-"+"
 *     counterpart) by [RANK_MATCH_MIN_MARGIN]. A medal that sits between the two stays unresolved.
 */
fun classifyRankMedal(sampler: SparkPixelSampler): String? {
    val ringFamily = familyOf(glyphStats(sampler, RANK_MEDAL_BOX)) ?: return null
    val sample = downsampleLuma(sampler, RANK_MEDAL_BOX, RANK_TEMPLATE_N)
    var best: RankMedalTemplate? = null
    var bestScore = Double.NEGATIVE_INFINITY
    var secondScore = Double.NEGATIVE_INFINITY
    for (template in RANK_MEDAL_TEMPLATES) {
        if (template.family != ringFamily) continue
        val score = normalizedCrossCorrelation(sample, template.data)
        if (score > bestScore) {
            secondScore = bestScore
            bestScore = score
            best = template
        } else if (score > secondScore) {
            secondScore = score
        }
    }
    if (best == null || bestScore < RANK_MATCH_MIN_NCC) return null
    if (secondScore > Double.NEGATIVE_INFINITY && bestScore - secondScore < RANK_MATCH_MIN_MARGIN) return null
    return best.label
}

/** Lowest stat value the digit OCR is allowed to believe. Measured over the 1810 stat samples in the
 * career corpus: every value below 10 is an artifact (the bot's own -1 unread sentinel, or a 1/4/7
 * from a dropped-digit read), and the smallest genuine value is 90. A single-digit read on a
 * registered Veteran is therefore a digit dropout, not a real stat.
 *
 * This matters more than a missing field would: stat values feed the rosterFingerprint, so an
 * accepted dropout does not merely lose data, it mints a WRONG identity that silently fails to join
 * to the Veteran's real history. Rejecting it costs one unresolved entry and keeps identity honest.
 * Observed live on the 20-entry walk: Guts read as 1 and Wit as 4, both fingerprinted. */
const val STAT_VALUE_MIN = 10
const val STAT_VALUE_MAX = 2500

/** Digits-only stat value parse (e.g. "949" -> 949), rejecting an implausible read. Kept as a pure
 * parser so the digit-OCR result stays testable even though the OCR itself needs the device. */
fun parseStatValue(raw: String): Int? = raw.filter { it.isDigit() }.toIntOrNull()?.takeIf { it in STAT_VALUE_MIN..STAT_VALUE_MAX }
