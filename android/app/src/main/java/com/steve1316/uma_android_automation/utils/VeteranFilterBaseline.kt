package com.steve1316.uma_android_automation.utils

/** One settled 1080x1920 Display Settings capture. OCR must read from the same pixels as [pixels]. */
data class VeteranFilterFrame(
    val width: Int,
    val height: Int,
    val pixels: SparkPixelSampler,
    val text: (Int, Int, Int, Int) -> String,
)

data class VeteranFilterAnchor(val dimension: VeteranFilterDimension, val y: Int, val commonRow: Int = 1)

data class VeteranFilterDimensionRead(val state: FilterDimensionState, val reasons: List<String> = emptyList())

fun mergeVeteranFilterDimensionReads(vararg observations: VeteranFilterDimensionRead): VeteranFilterDimensionRead {
    require(observations.isNotEmpty()) { "at least one filter-dimension observation is required" }
    val state =
        observations.maxBy {
            when (it.state) {
                FilterDimensionState.ACTIVE -> 2
                FilterDimensionState.UNKNOWN -> 1
                FilterDimensionState.NEUTRAL -> 0
            }
        }.state
    return VeteranFilterDimensionRead(state, observations.flatMap { it.reasons }.distinct())
}

internal class VeteranFilterViewportAggregation {
    private val accumulatedReadings = mutableMapOf<VeteranFilterDimension, VeteranFilterDimensionRead>()
    private val accumulatedDiagnostics = mutableListOf<String>()

    val readings: Map<VeteranFilterDimension, VeteranFilterDimensionRead> get() = accumulatedReadings
    val diagnostics: List<String> get() = accumulatedDiagnostics.distinct()

    fun add(viewport: VeteranFilterFrameRead) {
        accumulatedDiagnostics += viewport.errors
        for ((dimension, incomingRead) in viewport.dimensions) {
            accumulatedReadings[dimension] =
                accumulatedReadings[dimension]?.let { priorRead ->
                    mergeVeteranFilterDimensionReads(priorRead, incomingRead)
                } ?: incomingRead
        }
    }

    fun diagnostic(reason: String) {
        accumulatedDiagnostics += reason
    }
}

data class VeteranFilterFrameRead(
    val anchors: List<VeteranFilterAnchor>,
    val dimensions: Map<VeteranFilterDimension, VeteranFilterDimensionRead>,
    val errors: List<String>,
)

private const val FILTER_BODY_TOP = 255
private const val FILTER_BODY_BOTTOM = 1662
private val FILTER_ORDER = VeteranFilterDimension.entries

private fun normalized(raw: String): String = raw.uppercase().replace(Regex("[^A-Z0-9]+"), " ").trim()

// OCR swaps these glyphs in short labels ("AIl", "0K"); folding tolerates them without spending an edit.
private fun foldOcrGlyphs(text: String): String =
    text.map { when (it) { '0' -> 'O'; '1', 'I' -> 'L'; '5' -> 'S'; '8' -> 'B'; else -> it } }.joinToString("")

/**
 * Identity check for fixed dialog text (chrome and option labels). Containment always passes; otherwise a
 * run of whole words may differ from [expected] by one edit (4-7 letters) or two (8+), because live OCR
 * drops letters ("Reset Filtes"). Three letters or fewer get no edit, only glyph folding: one edit is a third of "OK".
 * Text that is itself a reading (section headings, spark names) must not use this.
 */
internal fun ocrTextMatches(raw: String, expected: String): Boolean {
    val text = normalized(raw)
    if (text.contains(expected)) return true
    val letters = expected.count { it != ' ' }
    val budget = if (letters <= 3) 0 else if (letters <= 7) 1 else 2
    if (budget == 0) return foldOcrGlyphs(text).contains(foldOcrGlyphs(expected))
    val words = text.split(' ').filter { it.isNotEmpty() }
    val size = expected.split(' ').size
    for (span in maxOf(1, size - 1)..size + 1) {
        for (start in 0..words.size - span) {
            if (editDistance(words.subList(start, start + span).joinToString(" "), expected) <= budget) return true
        }
    }
    return false
}


private fun pixelGreen(argb: Int): Boolean {
    val r = (argb shr 16) and 255
    val g = (argb shr 8) and 255
    val b = argb and 255
    return g > 120 && g - r > 40 && g - b > 60
}

// Tab-background columns on DISPLAY_SETTINGS_TAB_Y, clear of the "Sort" (x 254-312) and "Filter"
// (x 758-830) labels: the tab tap targets are label centres, whose pixels are glyphs in either state.
// Measured on 1080x1920 captures: selected tab (136,209,8), unselected (245,244,247).
private val SORT_TAB_BACKGROUND_XS = listOf(100, 180, 400, 480)
private val FILTER_TAB_BACKGROUND_XS = listOf(610, 690, 910, 990)

private fun greenTabSamples(frame: VeteranFilterFrame, xs: List<Int>): Int = xs.count { pixelGreen(frame.pixels.argb(it, DISPLAY_SETTINGS_TAB_Y)) }

fun filterDialogChromeRecognized(frame: VeteranFilterFrame): Boolean {
    if (frame.width != 1080 || frame.height != 1920) return false
    val title = frame.text(DISPLAY_SETTINGS_TITLE_X, DISPLAY_SETTINGS_TITLE_Y, DISPLAY_SETTINGS_TITLE_W, DISPLAY_SETTINGS_TITLE_H)
    if (!isDisplaySettingsTitle(title)) return false
    if (!ocrTextMatches(frame.text(155, 170, 260, 65), "SORT") || !ocrTextMatches(frame.text(665, 170, 260, 65), "FILTER")) return false
    if (greenTabSamples(frame, FILTER_TAB_BACKGROUND_XS) < 3 || greenTabSamples(frame, SORT_TAB_BACKGROUND_XS) > 1) return false
    if (!ocrTextMatches(frame.text(750, 1560, 285, 75), "RESET FILTERS")) return false
    if (!ocrTextMatches(frame.text(180, 1730, 240, 85), "CANCEL")) return false
    if (!ocrTextMatches(frame.text(705, 1730, 155, 85), "OK")) return false
    return true
}

private fun heading(raw: String, y: Int): VeteranFilterAnchor? {
    val name = normalized(raw)
    return when {
        name.contains("ATTRIBUTE SPARKS") -> VeteranFilterAnchor(VeteranFilterDimension.ATTRIBUTE_SPARKS, y)
        name.contains("APTITUDE SPARKS") -> VeteranFilterAnchor(VeteranFilterDimension.APTITUDE_SPARKS, y)
        name.contains("UNIQUE SPARKS") -> VeteranFilterAnchor(VeteranFilterDimension.UNIQUE_SPARKS, y)
        name.contains("COMMON SPARKS") || name.contains("COMMON SPARK") ->
            VeteranFilterAnchor(VeteranFilterDimension.COMMON_SPARKS, y, Regex("COMMON SPARKS? ([0-9]+)").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 1)
        name.contains("FAVORITES") -> VeteranFilterAnchor(VeteranFilterDimension.FAVORITES, y)
        name.contains("DISTANCE") -> VeteranFilterAnchor(VeteranFilterDimension.DISTANCE, y)
        name.contains("TRACK") -> VeteranFilterAnchor(VeteranFilterDimension.TRACK, y)
        name.contains("STYLE") -> VeteranFilterAnchor(VeteranFilterDimension.STYLE, y)
        name.contains("MEMO") -> VeteranFilterAnchor(VeteranFilterDimension.MEMO, y)
        else -> null
    }
}

/** The section rule crosses all three sample columns. A missing or unfamiliar heading is an error. */
fun readVeteranFilterAnchors(frame: VeteranFilterFrame): Pair<List<VeteranFilterAnchor>, List<String>> {
    if (!filterDialogChromeRecognized(frame)) return emptyList<VeteranFilterAnchor>() to listOf("dialog geometry, title, tab or fixed controls unrecognized")
    val anchors = mutableListOf<VeteranFilterAnchor>()
    val errors = mutableListOf<String>()
    var inRule = false
    for (y in FILTER_BODY_TOP until FILTER_BODY_BOTTOM) {
        val rule = listOf(250, 600, 900).all { x -> pixelGreen(frame.pixels.argb(x, y)) }
        if (rule && !inRule) {
            val raw = frame.text(102, y - 58, 550, 58)
            val found = heading(raw, y)
            if (found == null) errors += "unrecognized filter section at y=$y OCR='$raw'" else anchors += found
        }
        inRule = rule
    }
    if (anchors.isEmpty()) errors += "no filter section anchors"
    for (pair in anchors.zipWithNext()) {
        val (a, b) = pair
        val adjacent = if (a.dimension == VeteranFilterDimension.COMMON_SPARKS && b.dimension == a.dimension)
            b.commonRow == a.commonRow + 1 else FILTER_ORDER.indexOf(b.dimension) == FILTER_ORDER.indexOf(a.dimension) + 1
        if (!adjacent) errors += "section discontinuity ${a.dimension} to ${b.dimension}"
    }
    return anchors to errors
}

private class DimensionSamples(private val dimension: VeteranFilterDimension) {
    private val reasons = mutableListOf<String>()
    private var active = false
    private var unknown = false

    fun control(label: String, actual: FilterControlState) {
        when {
            actual == FilterControlState.UNKNOWN -> { unknown = true; reasons += "$label unreadable" }
            actual == FilterControlState.ACTIVE -> { active = true; reasons += "$label active" }
        }
    }

    fun unknown(reason: String) { unknown = true; reasons += reason }
    fun active(reason: String) { active = true; reasons += reason }
    fun result(): VeteranFilterDimensionRead = VeteranFilterDimensionRead(
        when { active -> FilterDimensionState.ACTIVE; unknown -> FilterDimensionState.UNKNOWN; else -> FilterDimensionState.NEUTRAL },
        reasons.map { "${dimension.name}: $it" },
    )
}

private fun checkbox(frame: VeteranFilterFrame, x: Int, y: Int): FilterControlState =
    if (y - 46 < FILTER_BODY_TOP || y + 46 >= FILTER_BODY_BOTTOM) FilterControlState.UNKNOWN else classifyFilterCheckbox(frame.pixels, x, y)

private fun radio(frame: VeteranFilterFrame, x: Int, y: Int): FilterControlState =
    if (y - 46 < FILTER_BODY_TOP || y + 46 >= FILTER_BODY_BOTTOM) FilterControlState.UNKNOWN else classifyFilterRadio(frame.pixels, x, y)

private fun label(frame: VeteranFilterFrame, x: Int, y: Int, width: Int, expected: String): Boolean =
    ocrTextMatches(frame.text(x, y - 35, width, 70), expected)

private fun ordinary(
    frame: VeteranFilterFrame,
    anchor: VeteranFilterAnchor,
    names: List<String>,
    extraSlots: List<Pair<Int, Int>>,
): VeteranFilterDimensionRead {
    val sample = DimensionSamples(anchor.dimension)
    val columns = listOf(106, 444, 782)
    names.forEachIndexed { index, name ->
        val x = columns[index % 3]
        val y = anchor.y + 80 + 114 * (index / 3)
        if (!label(frame, x + 55, y, if (x == 782) 185 else 245, name.uppercase())) sample.unknown("$name label/order unrecognized")
        sample.control(name, checkbox(frame, x, y))
    }
    for ((x, offset) in extraSlots) {
        if (hasNativeFilterCheckbox(frame.pixels, x, anchor.y + offset)) sample.unknown("unexpected option at $x,$offset")
    }
    return sample.result()
}

private fun sparkChildren(frame: VeteranFilterFrame, anchor: VeteranFilterAnchor, radioOffset: Int, sample: DimensionSamples) {
    val y = anchor.y + radioOffset
    if (!label(frame, 160, y, 210, "ALL")) sample.unknown("All grade label unrecognized")
    if (!label(frame, 500, y, 240, "OR ABOVE")) sample.unknown("two-star grade label unrecognized")
    if (!label(frame, 840, y, 185, "ONLY")) sample.unknown("three-star grade label unrecognized")
    when (radio(frame, 106, y)) {
        FilterControlState.ACTIVE -> Unit
        FilterControlState.NEUTRAL -> sample.unknown("grade All not selected")
        FilterControlState.UNKNOWN -> sample.unknown("grade All unreadable")
    }
    sample.control("grade two-star", radio(frame, 444, y))
    sample.control("grade three-star", radio(frame, 782, y))
    val originY = y + 114
    if (!label(frame, 160, originY, 650, "INCLUDE SPARKS FROM ORIGIN LEGACIES")) sample.unknown("origin label unrecognized")
    sample.control("origin", checkbox(frame, 106, originY))
}

private fun attributes(frame: VeteranFilterFrame, anchor: VeteranFilterAnchor): VeteranFilterDimensionRead {
    val sample = DimensionSamples(anchor.dimension)
    listOf("SPEED", "STAMINA", "POWER", "GUTS", "WIT").forEachIndexed { index, name ->
        val x = listOf(106, 444, 782)[index % 3]
        val y = anchor.y + 80 + 114 * (index / 3)
        if (!label(frame, x + 55, y, 245, name)) sample.unknown("$name label/order unrecognized")
        sample.control(name, checkbox(frame, x, y))
    }
    if (hasNativeFilterCheckbox(frame.pixels, 782, anchor.y + 194)) sample.unknown("unexpected attribute option")
    sparkChildren(frame, anchor, 363, sample)
    return sample.result()
}

private fun aptitudes(frame: VeteranFilterFrame, anchor: VeteranFilterAnchor): VeteranFilterDimensionRead {
    val sample = DimensionSamples(anchor.dimension)
    listOf("TURF", "DIRT", "SPRINT", "MILE", "MEDIUM", "LONG", "FRONT", "PACE", "LATE", "END").forEachIndexed { index, name ->
        val x = listOf(106, 444, 782)[index % 3]
        val y = anchor.y + 80 + 114 * (index / 3)
        if (!label(frame, x + 55, y, 245, name)) sample.unknown("$name label/order unrecognized")
        sample.control(name, checkbox(frame, x, y))
    }
    for (x in listOf(444, 782)) if (hasNativeFilterCheckbox(frame.pixels, x, anchor.y + 422)) sample.unknown("unexpected aptitude option")
    sparkChildren(frame, anchor, 592, sample)
    return sample.result()
}

private fun unique(frame: VeteranFilterFrame, anchor: VeteranFilterAnchor): VeteranFilterDimensionRead {
    val sample = DimensionSamples(anchor.dimension)
    val name = normalized(frame.text(160, anchor.y + 43, 385, 74))
    if (ocrTextMatches(name, "ALL UMAMUSUME")) sample.control("All Umamusume", checkbox(frame, 106, anchor.y + 80))
    else if (name.isNotBlank()) sample.active("named Unique Spark '$name'")
    else sample.unknown("Unique name unreadable")
    if (!label(frame, 630, anchor.y + 80, 150, "RESET") || !label(frame, 850, anchor.y + 80, 150, "SELECT")) sample.unknown("Unique row controls unrecognized")
    sparkChildren(frame, anchor, 249, sample)
    return sample.result()
}

private fun common(frame: VeteranFilterFrame, anchor: VeteranFilterAnchor, next: VeteranFilterAnchor): VeteranFilterDimensionRead {
    val sample = DimensionSamples(anchor.dimension)
    if (next.dimension == VeteranFilterDimension.FAVORITES && next.y - anchor.y in 242..258 && anchor.commonRow == 1) {
        if (!ocrTextMatches(frame.text(340, anchor.y + 55, 430, 105), "ADD SPARK FILTER")) sample.unknown("initial Add Spark Filter slot unreadable")
        val outside = (frame.pixels.argb(500, anchor.y + 30) shr 16) and 255
        val top = (frame.pixels.argb(500, anchor.y + 45) shr 16) and 255
        val bottom = (frame.pixels.argb(500, anchor.y + 172) shr 16) and 255
        val inside = (frame.pixels.argb(500, anchor.y + 80) shr 16) and 255
        if (outside - top < 10 || outside - bottom < 10 || inside < 245) sample.unknown("Add Spark Filter slot geometry unrecognized")
        return sample.result()
    }
    val name = normalized(frame.text(100, anchor.y + 45, 450, 85))
    if (name.isBlank() || name.contains("ADD SPARK FILTER")) sample.unknown("populated Common row unreadable")
    else sample.active("populated Common Spark row $name")
    if (!label(frame, 625, anchor.y + 80, 160, "RESET") || !label(frame, 850, anchor.y + 80, 150, "SELECT")) sample.unknown("Common row controls unrecognized")
    sparkChildren(frame, anchor, 249, sample)
    return sample.result()
}

private fun favorites(frame: VeteranFilterFrame, anchor: VeteranFilterAnchor): VeteranFilterDimensionRead {
    val sample = DimensionSamples(anchor.dimension)
    if (!label(frame, 160, anchor.y + 80, 190, "NOT SET")) sample.unknown("Not Set label unrecognized")
    val boxes = ALL_FAVORITE_CHECKBOXES
    if (boxes.size != 16) sample.unknown("favorite option count changed")
    boxes.forEachIndexed { index, box ->
        val x = listOf(106, 444, 782)[index % 3]
        val y = anchor.y + 80 + 114 * (index / 3)
        sample.control(box.label, checkbox(frame, x, y))
        if (index > 0 && !label(frame, x + 100, y, if (x == 782) 125 else 200, "FAVORITES")) sample.unknown("favorite option $index label unrecognized")
    }
    for (x in listOf(444, 782)) if (hasNativeFilterCheckbox(frame.pixels, x, anchor.y + 650)) sample.unknown("unexpected favorite option")
    return sample.result()
}

private fun memo(frame: VeteranFilterFrame, anchor: VeteranFilterAnchor): VeteranFilterDimensionRead {
    val sample = DimensionSamples(anchor.dimension)
    for ((name, x) in listOf("HAS MEMO" to 106, "NO MEMO" to 444)) {
        if (!label(frame, x + 55, anchor.y + 80, 245, name)) sample.unknown("$name label unrecognized")
        sample.control(name, checkbox(frame, x, anchor.y + 80))
    }
    if (hasNativeFilterCheckbox(frame.pixels, 782, anchor.y + 80)) sample.unknown("unexpected memo option")
    return sample.result()
}

private fun expectedSpan(a: VeteranFilterAnchor, b: VeteranFilterAnchor): Int = when (a.dimension) {
    VeteranFilterDimension.TRACK -> 211
    VeteranFilterDimension.DISTANCE -> 325
    VeteranFilterDimension.STYLE -> 325
    VeteranFilterDimension.ATTRIBUTE_SPARKS -> 621
    VeteranFilterDimension.APTITUDE_SPARKS -> 848
    VeteranFilterDimension.UNIQUE_SPARKS -> 505
    VeteranFilterDimension.COMMON_SPARKS -> if (b.dimension == VeteranFilterDimension.COMMON_SPARKS) 505 else 250
    VeteranFilterDimension.FAVORITES -> 781
    VeteranFilterDimension.MEMO -> 0
}

/** Returns only dimensions whose complete section and following anchor are visible in this frame. */
fun readVeteranFilterFrame(frame: VeteranFilterFrame): VeteranFilterFrameRead {
    val (anchors, anchorErrors) = readVeteranFilterAnchors(frame)
    val errors = anchorErrors.toMutableList()
    val readings = mutableMapOf<VeteranFilterDimension, VeteranFilterDimensionRead>()
    for ((a, b) in anchors.zipWithNext()) {
        val span = b.y - a.y
        if (kotlin.math.abs(span - expectedSpan(a, b)) > 9) {
            errors += "${a.dimension} section span $span unrecognized"
            continue
        }
        val result = when (a.dimension) {
            VeteranFilterDimension.TRACK -> ordinary(frame, a, listOf("TURF", "DIRT"), listOf(782 to 80))
            VeteranFilterDimension.DISTANCE -> ordinary(frame, a, listOf("SPRINT", "MILE", "MEDIUM", "LONG"), listOf(444 to 194, 782 to 194))
            VeteranFilterDimension.STYLE -> ordinary(frame, a, listOf("FRONT", "PACE", "LATE", "END"), listOf(444 to 194, 782 to 194))
            VeteranFilterDimension.ATTRIBUTE_SPARKS -> attributes(frame, a)
            VeteranFilterDimension.APTITUDE_SPARKS -> aptitudes(frame, a)
            VeteranFilterDimension.UNIQUE_SPARKS -> unique(frame, a)
            VeteranFilterDimension.COMMON_SPARKS -> common(frame, a, b)
            VeteranFilterDimension.FAVORITES -> favorites(frame, a)
            VeteranFilterDimension.MEMO -> continue
        }
        if (a.dimension == VeteranFilterDimension.COMMON_SPARKS && (a.commonRow > 1 || b.dimension == VeteranFilterDimension.COMMON_SPARKS)) {
            readings[a.dimension] = VeteranFilterDimensionRead(FilterDimensionState.ACTIVE, result.reasons + "COMMON_SPARKS: multiple Common rows")
        } else readings[a.dimension] = result
    }
    anchors.lastOrNull()?.takeIf { it.dimension == VeteranFilterDimension.MEMO && it.y in 1403..1422 }?.let {
        readings[VeteranFilterDimension.MEMO] = memo(frame, it)
    }
    return VeteranFilterFrameRead(anchors, readings, errors)
}
