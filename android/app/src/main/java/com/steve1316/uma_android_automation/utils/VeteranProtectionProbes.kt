package com.steve1316.uma_android_automation.utils

/**
 * Geometry and pure classifiers for the Veteran Roster's Display Settings > Filter dialog, used by
 * the read-only protection probe ([com.steve1316.uma_android_automation.VeteranProtectionScanner]).
 *
 * The probe never releases, favorites, memos, or transfers anything. It answers two population
 * questions - "is any Veteran favorited?" and "does any Veteran have a memo?" - by setting a filter
 * partition IN the dialog and reading a single fact the game already computes for it: the game
 * disables the OK/Apply button when the current (un-applied) selection would return zero rows. So the
 * probe reads OK-enabled without ever tapping OK; the applied filter state is never changed and the
 * roster stays Filters: OFF. A disabled OK reading is usable only after the full dialog verifier has
 * established a neutral baseline and an enabled complementary selection.
 *
 * The favorite/memo coordinates are measured at the 1080x1920 absolute bottom position. The full
 * verifier reads other sections while traversing the dialog. Pixel classifiers use [SparkPixelSampler]
 * so a blank or displaced control cannot be accepted as neutral.
 */

// -- Opening / navigating the dialog (roster list is the entry screen) -----------------------------

/** The "Rating [display-settings]" pill on the roster status bar. Tapping it opens Display Settings.
 * Deliberately NOT the "Desc" pill next to it, which flips the sort direction the roster scan needs. */
const val OPEN_DISPLAY_SETTINGS_X = 720
const val OPEN_DISPLAY_SETTINGS_Y = 1488

/** The Sort / Filter tab toggles inside the Display Settings dialog. */
const val DISPLAY_SETTINGS_SORT_TAB_X = 283
const val DISPLAY_SETTINGS_TAB_Y = 203
const val DISPLAY_SETTINGS_FILTER_TAB_X = 795

/** The green banner title region. OCR of this decides the frame is the Display Settings dialog before
 * any checkbox is touched, the same fail-closed screen assertion the roster walk makes on its title. */
const val DISPLAY_SETTINGS_TITLE_X = 300
const val DISPLAY_SETTINGS_TITLE_Y = 60
const val DISPLAY_SETTINGS_TITLE_W = 480
const val DISPLAY_SETTINGS_TITLE_H = 80

/** The dialog's bottom bar. Cancel closes WITHOUT applying (the probe's clean exit: the roster keeps
 * whatever filter it had, which is OFF); OK would apply the current selection. Reset Filters returns
 * every checkbox to its default (all grey = no constraint). */
const val DIALOG_CANCEL_X = 300
const val DIALOG_CANCEL_Y = 1772
const val DIALOG_OK_X = 778
const val DIALOG_OK_Y = 1772
const val DIALOG_RESET_FILTERS_X = 885
const val DIALOG_RESET_FILTERS_Y = 1596

// -- Filter list scrolling: reach the deterministic bottom before touching a checkbox ---------------

/** A vertical gutter clear of every checkbox and control, so a scroll swipe never toggles anything. */
const val FILTER_SCROLL_GUTTER_X = 960
const val FILTER_SCROLL_SWIPE_FROM_Y = 1300
const val FILTER_SCROLL_SWIPE_TO_Y = 500
const val FILTER_SCROLL_SWIPE_DURATION_MS = 400L

/** How many hard swipes to guarantee the absolute bottom. Memo is the last section, so once the list
 * bottoms out further swipes are no-ops; the count only needs to exceed the list's scroll span. */
const val FILTER_SCROLL_TO_BOTTOM_SWIPES = 5

// -- Checkbox grid at the absolute bottom (3 columns, 114 px row pitch) -----------------------------

/** One checkbox's checkmark centre. */
data class FilterCheckbox(val label: String, val cx: Int, val cy: Int)

private const val COL1_X = 106
private const val COL2_X = 444
private const val COL3_X = 782

/** The "Not Set" favorite checkbox: a Veteran with no favorite icon. Selecting only this shows the
 * NOT-favorited partition; it is deliberately excluded from [FAVORITE_ICON_CHECKBOXES]. */
val FAVORITE_NOT_SET_CHECKBOX = FilterCheckbox("favorite_not_set", COL1_X, 712)

/**
 * The 15 favorite-icon categories (carrot, egg, drink, box, cake, diamond, spade, heart, club, and
 * five shoe/handshake variants), left to right, top to bottom. Selecting ALL of these and leaving
 * Not Set unselected shows the FAVORITED partition. This is a measured snapshot of the game's current
 * favorite-icon set (2026-08-22), like the outfit domain: if the game adds a category, extend this.
 */
val FAVORITE_ICON_CHECKBOXES: List<FilterCheckbox> =
    listOf(
        FilterCheckbox("favorite_icon_01", COL2_X, 712), FilterCheckbox("favorite_icon_02", COL3_X, 712),
        FilterCheckbox("favorite_icon_03", COL1_X, 826), FilterCheckbox("favorite_icon_04", COL2_X, 826), FilterCheckbox("favorite_icon_05", COL3_X, 826),
        FilterCheckbox("favorite_icon_06", COL1_X, 940), FilterCheckbox("favorite_icon_07", COL2_X, 940), FilterCheckbox("favorite_icon_08", COL3_X, 940),
        FilterCheckbox("favorite_icon_09", COL1_X, 1054), FilterCheckbox("favorite_icon_10", COL2_X, 1054), FilterCheckbox("favorite_icon_11", COL3_X, 1054),
        FilterCheckbox("favorite_icon_12", COL1_X, 1168), FilterCheckbox("favorite_icon_13", COL2_X, 1168), FilterCheckbox("favorite_icon_14", COL3_X, 1168),
        FilterCheckbox("favorite_icon_15", COL1_X, 1282),
    )

/** The Memo partition: "Has Memo" vs "No Memo". Selecting only Has Memo shows the memo'd partition. */
val MEMO_HAS_CHECKBOX = FilterCheckbox("memo_has", COL1_X, 1492)
val MEMO_NO_CHECKBOX = FilterCheckbox("memo_no", COL2_X, 1492)

/** Every favorite checkbox (Not Set first), for the baseline all-unselected sanity check. */
val ALL_FAVORITE_CHECKBOXES: List<FilterCheckbox> = listOf(FAVORITE_NOT_SET_CHECKBOX) + FAVORITE_ICON_CHECKBOXES

/** A dimension is neutral only when all its controls, including nested spark choices, were read. */
enum class VeteranFilterDimension { TRACK, DISTANCE, STYLE, ATTRIBUTE_SPARKS, APTITUDE_SPARKS, UNIQUE_SPARKS, COMMON_SPARKS, FAVORITES, MEMO }

enum class FilterControlState { NEUTRAL, ACTIVE, UNKNOWN }

enum class FilterDimensionState { NEUTRAL, ACTIVE, UNKNOWN }

enum class FilterBaselineState { NEUTRAL_VERIFIED, NOT_NEUTRAL, UNKNOWN }

fun filterBaselineState(readings: Map<VeteranFilterDimension, FilterDimensionState>, unexpectedSection: Boolean = false): FilterBaselineState =
    when {
        readings.values.any { it == FilterDimensionState.ACTIVE } -> FilterBaselineState.NOT_NEUTRAL
        !unexpectedSection && readings.size == VeteranFilterDimension.entries.size &&
            VeteranFilterDimension.entries.all { readings[it] == FilterDimensionState.NEUTRAL } -> FilterBaselineState.NEUTRAL_VERIFIED
        else -> FilterBaselineState.UNKNOWN
    }

fun exactFilterTargetState(readings: Map<VeteranFilterDimension, FilterDimensionState>, target: VeteranFilterDimension, unexpectedSection: Boolean = false): Boolean =
    !unexpectedSection && readings.size == VeteranFilterDimension.entries.size && VeteranFilterDimension.entries.all { dimension ->
        readings[dimension] == if (dimension == target) FilterDimensionState.ACTIVE else FilterDimensionState.NEUTRAL
    }

// -- Native checkbox/radio glyphs ---------------------------------------------------------------

private fun light(argb: Int): Int = (((argb shr 16) and 255) + ((argb shr 8) and 255) + (argb and 255)) / 3

private fun green(argb: Int): Boolean {
    val r = (argb shr 16) and 255
    val g = (argb shr 8) and 255
    val b = argb and 255
    return g >= 105 && g - r >= 20 && g - b >= 35
}

private fun darkest(sampler: SparkPixelSampler, cx: Int, cy: Int): Int =
    (-4..4 step 2).minOf { dy -> (-4..4 step 2).minOf { dx -> light(sampler.argb(cx + dx, cy + dy)) } }

/** A blank crop must not look like an unchecked box. The native square has a top rim and darker
 * bottom shadow in both the normal and dimmed Spark panels. */
fun hasNativeFilterCheckbox(sampler: SparkPixelSampler, cx: Int, cy: Int): Boolean {
    val backgroundTop = light(sampler.argb(cx, cy - 45))
    val backgroundBottom = light(sampler.argb(cx, cy + 46))
    val topContrast = (-38..-31).maxOf { backgroundTop - light(sampler.argb(cx, cy + it)) }
    val bottomContrast = (32..40).maxOf { backgroundBottom - light(sampler.argb(cx, cy + it)) }
    val leftContrast = (-38..-31).maxOf { light(sampler.argb(cx - 45, cy)) - light(sampler.argb(cx + it, cy)) }
    val rightContrast = (31..38).maxOf { light(sampler.argb(cx + 45, cy)) - light(sampler.argb(cx + it, cy)) }
    return topContrast >= 12 && bottomContrast >= 35 && leftContrast >= 12 && rightContrast >= 12
}

fun classifyFilterCheckbox(sampler: SparkPixelSampler, cx: Int, cy: Int): FilterControlState {
    if (!hasNativeFilterCheckbox(sampler, cx, cy)) return FilterControlState.UNKNOWN
    var greenInk = 0
    for (dy in -22..20 step 2) for (dx in -22..22 step 2) {
        if (green(sampler.argb(cx + dx, cy + dy))) greenInk++
    }
    if (greenInk >= 15) return FilterControlState.ACTIVE
    if (greenInk > 0) return FilterControlState.UNKNOWN
    val background = light(sampler.argb(cx + 20, cy + 20))
    val tickPoints = listOf(-16 to 4, -7 to 13, 18 to -13)
    return if (tickPoints.all { (dx, dy) -> background - darkest(sampler, cx + dx, cy + dy) >= 10 })
        FilterControlState.NEUTRAL else FilterControlState.UNKNOWN
}

/** Grade All is the neutral *choice*, but its native radio is visibly selected. */
fun classifyFilterRadio(sampler: SparkPixelSampler, cx: Int, cy: Int): FilterControlState {
    val background = light(sampler.argb(cx - 45, cy))
    val left = background - light(sampler.argb(cx - 35, cy))
    val right = light(sampler.argb(cx + 45, cy)) - light(sampler.argb(cx + 35, cy))
    val top = light(sampler.argb(cx, cy - 45)) - light(sampler.argb(cx, cy - 35))
    val bottom = light(sampler.argb(cx, cy + 45)) - light(sampler.argb(cx, cy + 35))
    if (listOf(left, right, top, bottom).count { it >= 10 } < 3) return FilterControlState.UNKNOWN
    var greens = 0
    var grey = 0
    for (dy in -10..10 step 2) for (dx in -10..10 step 2) {
        val pixel = sampler.argb(cx + dx, cy + dy)
        if (green(pixel)) greens++ else if (light(pixel) in 70..230 &&
            kotlin.math.abs(((pixel shr 16) and 255) - ((pixel shr 8) and 255)) < 15) grey++
    }
    return when {
        greens >= 20 -> FilterControlState.ACTIVE
        greens > 0 -> FilterControlState.UNKNOWN
        grey >= 45 -> FilterControlState.NEUTRAL
        else -> FilterControlState.UNKNOWN
    }
}

// -- OK/Apply button classifier: the enumeration-free population signal ----------------------------

/** Whether the dialog's OK/Apply button is tappable. DISABLED means the current selection would
 * return zero Veterans (an empty partition); ENABLED means at least one. UNKNOWN guards against
 * reading the button on a frame that is not the dialog - the caller must treat it as a failure. */
enum class ApplyButtonState { ENABLED, DISABLED, UNKNOWN }

/** A fill-only slab of the OK button, left of its white "OK" text. Fixed on screen (outside the
 * scroll area), so scroll position is irrelevant. */
const val APPLY_BUTTON_SAMPLE_X0 = 600
const val APPLY_BUTTON_SAMPLE_Y0 = 1745
const val APPLY_BUTTON_SAMPLE_X1 = 690
const val APPLY_BUTTON_SAMPLE_Y1 = 1800

/** Average green-channel bands for the OK fill. Measured: enabled fill green ~208, disabled ~130.
 * The [155, 185] gap between the two bands never occurs on the real button, so a reading inside it
 * means the frame is not the dialog and the probe fails closed. */
const val APPLY_ENABLED_GREEN_MIN = 185
const val APPLY_DISABLED_GREEN_MAX = 155

/**
 * Classifies the OK button as ENABLED (bright green) or DISABLED (dark olive) by the average green
 * channel of its fill. A value in the dead band between the two returns UNKNOWN so the caller aborts
 * rather than trusting a reading taken off the wrong screen.
 */
fun classifyApplyButton(sampler: SparkPixelSampler): ApplyButtonState {
    var sumR = 0L
    var sumG = 0L
    var sumB = 0L
    var n = 0
    var y = APPLY_BUTTON_SAMPLE_Y0
    while (y < APPLY_BUTTON_SAMPLE_Y1) {
        var x = APPLY_BUTTON_SAMPLE_X0
        while (x < APPLY_BUTTON_SAMPLE_X1) {
            val pixel = sampler.argb(x, y)
            sumR += ((pixel shr 16) and 0xFF).toLong()
            sumG += ((pixel shr 8) and 0xFF).toLong()
            sumB += (pixel and 0xFF).toLong()
            n++
            x += 3
        }
        y += 3
    }
    if (n == 0) return ApplyButtonState.UNKNOWN
    val avgR = (sumR / n).toInt()
    val avgG = (sumG / n).toInt()
    val avgB = (sumB / n).toInt()
    if (avgR !in 60..160 || avgB > 70 || avgG - avgR < 30 || avgG - avgB < 75) return ApplyButtonState.UNKNOWN
    return when {
        avgG >= APPLY_ENABLED_GREEN_MIN -> ApplyButtonState.ENABLED
        avgG <= APPLY_DISABLED_GREEN_MAX -> ApplyButtonState.DISABLED
        else -> ApplyButtonState.UNKNOWN
    }
}

/** The dialog title reads as the Display Settings dialog. Tolerant of OCR noise: either word suffices,
 * or the whole phrase within two edits. */
fun isDisplaySettingsTitle(titleRaw: String): Boolean {
    val upper = titleRaw.uppercase()
    return upper.contains("DISPLAY") || upper.contains("SETTING") || ocrTextMatches(titleRaw, "DISPLAY SETTINGS")
}
