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
 * roster stays Filters: OFF. See validation/parentlab-plr2a-protection for the calibration captures.
 *
 * All coordinates are 1080x1920, measured live at the deterministic ABSOLUTE-BOTTOM scroll position
 * of the Filter list (Memo is the last section, so scrolling to the bottom pins Favorites and Memo at
 * fixed coordinates). Android-free on purpose: the classifiers take a [SparkPixelSampler] so they are
 * unit-testable against fixture pixels, exactly like the roster badge classifiers.
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

// -- Checkbox state classifier: green check = selected, grey check = not selected ------------------

enum class FilterCheckboxState { SELECTED, UNSELECTED }

/** Half-width of the sampled square around a checkbox centre. Kept inside the ~72 px box and clear of
 * the coloured favorite icon to its right, so only the checkmark ink is read. */
const val FILTER_CHECKBOX_SAMPLE_HALF = 22

/** Colour spread (max channel range) at or above which the checkmark is the saturated green "selected"
 * mark. Measured: a green check reaches spread ~196, a grey check ~8. 40 sits far above the grey
 * noise floor and far below real green. Same idiom as [classifyFavoriteMarker]. */
const val FILTER_CHECKBOX_SELECTED_SPREAD_MIN = 40

/**
 * Classifies one filter checkbox by the saturation of its checkmark: a green check is SELECTED, a
 * grey check is UNSELECTED. Never guesses which favorite icon sits beside it - that is irrelevant to
 * whether the box is ticked.
 */
fun classifyFilterCheckbox(sampler: SparkPixelSampler, cx: Int, cy: Int): FilterCheckboxState {
    var maxSpread = 0
    var dy = -FILTER_CHECKBOX_SAMPLE_HALF
    while (dy <= FILTER_CHECKBOX_SAMPLE_HALF) {
        var dx = -FILTER_CHECKBOX_SAMPLE_HALF
        while (dx <= FILTER_CHECKBOX_SAMPLE_HALF) {
            val argb = sampler.argb(cx + dx, cy + dy)
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val spread = maxOf(r, g, b) - minOf(r, g, b)
            if (spread > maxSpread) maxSpread = spread
            dx += 4
        }
        dy += 4
    }
    return if (maxSpread >= FILTER_CHECKBOX_SELECTED_SPREAD_MIN) FilterCheckboxState.SELECTED else FilterCheckboxState.UNSELECTED
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
    var sumG = 0L
    var n = 0
    var y = APPLY_BUTTON_SAMPLE_Y0
    while (y < APPLY_BUTTON_SAMPLE_Y1) {
        var x = APPLY_BUTTON_SAMPLE_X0
        while (x < APPLY_BUTTON_SAMPLE_X1) {
            sumG += ((sampler.argb(x, y) shr 8) and 0xFF).toLong()
            n++
            x += 3
        }
        y += 3
    }
    if (n == 0) return ApplyButtonState.UNKNOWN
    val avgG = (sumG / n).toInt()
    return when {
        avgG >= APPLY_ENABLED_GREEN_MIN -> ApplyButtonState.ENABLED
        avgG <= APPLY_DISABLED_GREEN_MAX -> ApplyButtonState.DISABLED
        else -> ApplyButtonState.UNKNOWN
    }
}

/** The dialog title reads as the Display Settings dialog. Tolerant of OCR noise: either word suffices. */
fun isDisplaySettingsTitle(titleRaw: String): Boolean {
    val upper = titleRaw.uppercase()
    return upper.contains("DISPLAY") || upper.contains("SETTING")
}
