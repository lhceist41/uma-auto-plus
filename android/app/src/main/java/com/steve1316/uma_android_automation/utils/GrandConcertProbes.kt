package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.LessonCardKind
import com.steve1316.uma_android_automation.bot.PerformancePointType

/**
 * Pixel probes and geometry for the Grand Concert scenario ("Brighter Together Our Grand
 * Concert", community name "Grand Live"), added to Global on 2026-07-22 22:00 UTC.
 *
 * Every constant here was measured on the maintainer's own launch-night captures at
 * 1080x1920 (see src/test/resources/fixtures/grandconcert/PROVENANCE.md) and is pinned by
 * fixture tests, following the same Android-free pattern as [SparkScreenProbes]: the runtime
 * wraps a Bitmap in a [SparkPixelSampler], the JUnit fixtures wrap a decoded PNG.
 *
 * Scope discipline: only screens the maintainer has actually captured are probed here. The
 * Lesson shop, the concert screens, and the unlocked Lesson button have no capture yet, so
 * they are deliberately absent rather than guessed - an unknown screen must stay unknown and
 * reach the manual-handoff boundary instead of being driven blind.
 */

/** Measured geometry of the mandatory "Quick Mode Settings" dialog shown when a career starts.
 * The dialog is new to the maintainer's captures (it is a general game feature rather than a
 * Grand Concert one), and it blocks career start until a choice is confirmed. */
object QuickModeGeometry {
    /** Green title band ("Quick Mode Settings"), measured (152, 215, 62). */
    const val HEADER_X = 540
    const val HEADER_Y = 491

    /** White card body between the header and the first option row. */
    const val CARD_X = 540
    const val CARD_Y = 700

    /** Radio-button column and the four row centers (pitch ~121 px). */
    const val RADIO_X = 104
    val ROW_YS = listOf(818, 941, 1062, 1182)

    /** Wide green Confirm button, measured (166, 220, 76). */
    const val CONFIRM_X = 540
    const val CONFIRM_Y = 1377

    /** The dialog dims its backdrop to pure black, unlike every in-career dialog, which is the
     * cheapest structural signal that this specific dialog is up. */
    const val BACKDROP_X = 540
    const val BACKDROP_Y = 200

    /** OCR band for the dialog title, used to positively identify the dialog by text. */
    val TITLE_OCR_REGION = intArrayOf(240, 455, 600, 76)

    /** OCR band for one option row's label (x offset past the radio button). */
    fun optionOcrRegion(index: Int): IntArray = intArrayOf(230, ROW_YS[index] - 32, 700, 64)
}

/** The four Quick Mode choices, in the order the dialog lists them. [wire] is the persisted
 * settings value and must not change once shipped. */
enum class QuickModeOption(val wire: String, val label: String) {
    DONT_USE("dont_use", "Don't use Quick Mode"),
    SHORTEN_ALL("shorten_all", "Shorten all events"),
    SCENARIO_ONLY("scenario_only", "Only shorten scenario events"),
    TRAINEE_ONLY("trainee_only", "Only shorten trainee events"),
    ;

    val rowIndex: Int get() = entries.indexOf(this)

    companion object {
        fun fromWire(wire: String?): QuickModeOption? = entries.firstOrNull { it.wire == wire?.trim() }
    }
}

private fun red(argb: Int): Int = (argb shr 16) and 0xFF

private fun green(argb: Int): Int = (argb shr 8) and 0xFF

private fun blue(argb: Int): Int = argb and 0xFF

/** 5x5 mean around (cx, cy), matching the smoothing the spark probes use. */
private fun mean(sampler: SparkPixelSampler, cx: Int, cy: Int): Triple<Int, Int, Int> {
    var r = 0
    var g = 0
    var b = 0
    for (dy in -2..2) {
        for (dx in -2..2) {
            val p = sampler.argb(cx + dx, cy + dy)
            r += red(p)
            g += green(p)
            b += blue(p)
        }
    }
    return Triple(r / 25, g / 25, b / 25)
}

/** Dialog header / title-band green, measured (152, 215, 62) on both Quick Mode captures. */
private fun isDialogGreen(r: Int, g: Int, b: Int): Boolean = g >= 150 && g - r >= 40 && g - b >= 100

/** Wide advance-button green, measured (166, 220, 76). */
private fun isButtonGreen(r: Int, g: Int, b: Int): Boolean = g >= 180 && r <= 210 && g - r >= 30 && g - b >= 80

/** A selected radio reads (133..135, 206..207, 34..35); an unselected one reads (217..218,
 * 216..217, 221..222). The two populations are separated by ~180 in the blue channel and by
 * the green-minus-blue sign, so no ambiguous band is needed here. */
private fun isRadioSelected(r: Int, g: Int, b: Int): Boolean = g >= 170 && g - b >= 100 && g - r >= 40

private fun isRadioUnselected(r: Int, g: Int, b: Int): Boolean = r >= 190 && g >= 190 && b >= 190

/** Near-black dimmed backdrop (0, 0, 0) behind the Quick Mode card. */
private fun isBlackBackdrop(r: Int, g: Int, b: Int): Boolean = r <= 24 && g <= 24 && b <= 24

/**
 * Structural signature of the Quick Mode Settings dialog: the green title band, the pale card
 * body, four radio slots that each read as exactly one of selected/unselected, the wide green
 * Confirm, and the pure-black backdrop above the card.
 */
fun quickModeDialogPresent(sampler: SparkPixelSampler): Boolean {
    val (hr, hg, hb) = mean(sampler, QuickModeGeometry.HEADER_X, QuickModeGeometry.HEADER_Y)
    if (!isDialogGreen(hr, hg, hb)) return false
    val (br, bg, bb) = mean(sampler, QuickModeGeometry.BACKDROP_X, QuickModeGeometry.BACKDROP_Y)
    if (!isBlackBackdrop(br, bg, bb)) return false
    val (cr, cg, cb) = mean(sampler, QuickModeGeometry.CONFIRM_X, QuickModeGeometry.CONFIRM_Y)
    if (!isButtonGreen(cr, cg, cb)) return false
    return QuickModeGeometry.ROW_YS.all { y ->
        val (r, g, b) = mean(sampler, QuickModeGeometry.RADIO_X, y)
        isRadioSelected(r, g, b) || isRadioUnselected(r, g, b)
    }
}

/**
 * Which Quick Mode row is currently selected, or null when the rows do not read as exactly one
 * selection. Null is the safe answer: the caller must never confirm a dialog whose state it
 * cannot prove, and must never infer "probably the first one".
 */
fun quickModeSelectedIndex(sampler: SparkPixelSampler): Int? {
    val selected =
        QuickModeGeometry.ROW_YS.indices.filter { i ->
            val (r, g, b) = mean(sampler, QuickModeGeometry.RADIO_X, QuickModeGeometry.ROW_YS[i])
            isRadioSelected(r, g, b)
        }
    return selected.singleOrNull()
}

/**
 * The Grand Concert career screen paints its stat-table LABEL row pink (measured (255, 115,
 * 214) at y=1246..1252) where URA/Unity Cup paint it white-on-blue. The VALUE cells below it
 * stay white with the usual dark digits, which is why the shared grayscale stat OCR is
 * unaffected by the theme - this probe exists so that fact is pinned by a fixture instead of
 * assumed, and so a future theme change that DOES reach the value cells fails a test.
 */
object GrandConcertTheme {
    /** A point inside the pink stat-table label row. */
    const val STAT_LABEL_X = 300
    const val STAT_LABEL_Y = 1249

    /** Points inside the stat VALUE cells' background (between digit strokes), which must stay
     * near-white for the shared OCR to keep working. */
    val STAT_VALUE_BG_POINTS = listOf(140 to 1300, 650 to 1300, 800 to 1300)

    /** The locked scenario button ("?") sits between Recreation and Races on the career screen's
     * second action row. Only its LOCKED appearance has been captured. */
    const val LESSON_SLOT_X = 663
    const val LESSON_SLOT_Y = 1690
}

fun grandConcertPinkStatLabelRow(sampler: SparkPixelSampler): Boolean {
    val (r, g, b) = mean(sampler, GrandConcertTheme.STAT_LABEL_X, GrandConcertTheme.STAT_LABEL_Y)
    // Strong magenta: red and blue high, green clearly suppressed.
    return r >= 230 && b >= 190 && r - g >= 90 && b - g >= 60
}

/** True when every sampled stat-value cell background is still near-white (the condition the
 * shared grayscale stat OCR depends on). */
fun grandConcertStatValueCellsAreLight(sampler: SparkPixelSampler): Boolean =
    GrandConcertTheme.STAT_VALUE_BG_POINTS.all { (x, y) ->
        val (r, g, b) = mean(sampler, x, y)
        r >= 235 && g >= 235 && b >= 235
    }

/** Observed state of the scenario / Lessons button on the career screen. All four states now
 * have a real launch-night capture (LOCKED on turn 1; UNLOCKED_SCHEDULED after a song was
 * scheduled). UNLOCKED (unlocked, nothing scheduled yet) has no dedicated capture, so it is
 * detected by composition - lit button, no Scheduled badge - and is proven by the pure
 * classifier rather than a fixture; that gap is documented in PROVENANCE. */
enum class LessonSlotState {
    /** Grey, unsaturated disc: the scenario mechanic has not unlocked yet. */
    LOCKED,

    /** The Lessons button is lit, with nothing scheduled. */
    UNLOCKED,

    /** The Lessons button is lit and carries a pink "Scheduled" badge (a lesson is queued). */
    UNLOCKED_SCHEDULED,

    /** Unreadable. Must NOT be coerced to LOCKED. */
    UNKNOWN,
}

/** The pink "Scheduled" badge above the Lessons button, measured (255, 105, 154). */
const val LESSON_SCHEDULED_BADGE_X = 663
const val LESSON_SCHEDULED_BADGE_Y = 1610

/** The song-note (♪) marker to the right of the Lessons label when a lesson is scheduled,
 * measured (216, 130, 210). */
const val LESSON_NOTE_MARKER_X = 760
const val LESSON_NOTE_MARKER_Y = 1655

private fun isScheduledBadgePink(r: Int, g: Int, b: Int): Boolean = r >= 230 && g <= 150 && b in 110..200 && r - g >= 90

/** True when the pink "Scheduled" badge is present above the Lessons button. Detected
 * independently of the button state so the two signals cannot mask each other. */
fun grandConcertScheduledBadgePresent(sampler: SparkPixelSampler): Boolean {
    val (r, g, b) = mean(sampler, LESSON_SCHEDULED_BADGE_X, LESSON_SCHEDULED_BADGE_Y)
    return isScheduledBadgePink(r, g, b)
}

/** True when the scheduled song-note marker is present by the Lessons label. */
fun grandConcertLessonNoteMarkerPresent(sampler: SparkPixelSampler): Boolean {
    val (r, g, b) = mean(sampler, LESSON_NOTE_MARKER_X, LESSON_NOTE_MARKER_Y)
    // Magenta/purple note glyph: red and blue elevated, green suppressed.
    return r >= 170 && b >= 170 && r - g >= 50 && b - g >= 50
}

/**
 * Reads the scenario / Lessons button slot into one of the four states. The locked disc is a flat
 * mid-grey (measured (99, 97, 100)); the unlocked Lessons button reads much lighter (measured
 * (186, 188, 205)). The Scheduled badge, checked independently, upgrades UNLOCKED to
 * UNLOCKED_SCHEDULED. A too-dark-to-be-lit but not-grey sample is UNKNOWN, never coerced to
 * LOCKED.
 */
fun grandConcertLessonSlotState(sampler: SparkPixelSampler): LessonSlotState {
    val (r, g, b) = mean(sampler, GrandConcertTheme.LESSON_SLOT_X, GrandConcertTheme.LESSON_SLOT_Y)
    val maxC = maxOf(r, g, b)
    val minC = minOf(r, g, b)
    val lit = maxC >= 150
    if (lit) {
        return if (grandConcertScheduledBadgePresent(sampler)) LessonSlotState.UNLOCKED_SCHEDULED else LessonSlotState.UNLOCKED
    }
    val greyish = (maxC - minC) <= 18
    val midLuma = maxC in 60..149
    return if (greyish && midLuma) LessonSlotState.LOCKED else LessonSlotState.UNKNOWN
}

/**
 * The Lesson list and Concert-Info screen geometry, measured on the 2026-07-23 captures. These
 * screens are ultimately distinguished by their header OCR text; the pixel probes here corroborate
 * the layout so a fixture can pin it and a mis-detection fails a test instead of a career.
 */
object GrandConcertLessonGeometry {
    /** The dialog family (Confirmation / Schedule / Scheduling Complete / Concert Info) shares a
     * bright green header band, measured (130, 206, 11) at x=180. The full-screen dialogs put it at
     * the very top; the smaller Scheduling Complete dialog puts it mid-screen. */
    const val HEADER_GREEN_X = 180
    const val HEADER_TOP_Y = 88
    const val HEADER_MID_Y = 610

    /** The Lesson list's dark-blue top band (its "Performance Points" pill sits on it), measured
     * (44, 60, 121). */
    const val LIST_TOP_BAND_X = 180
    const val LIST_TOP_BAND_Y = 78

    /** The five balance badges across the top of the lesson list. */
    val BALANCE_BADGE_XS = listOf(120, 305, 490, 675, 860)
    const val BALANCE_ROW_Y = 185

    /** Card header centres (green=technique, purple=song), the gold "Learnable!" marker above the
     * kind badge, and the coloured/greyed cost strip. */
    val CARD_HEADER_YS = listOf(375, 783, 1190)
    val CARD_LEARNABLE_YS = listOf(338, 746, 1154)
    val CARD_COST_STRIP_YS = listOf(675, 1083, 1490)
    const val CARD_HEADER_X = 300
    const val CARD_KIND_BADGE_X = 880
    const val CARD_COST_STRIP_X = 460

    /** The red "Not enough performance points" band, present only on the Schedule dialog. */
    const val SHORTFALL_BAND_Y = 1596
    const val SHORTFALL_X_START = 300
    const val SHORTFALL_X_END = 780

    /** Concert Info's pale-green "Set List" sub-header, measured (225, 243, 202). */
    const val CONCERT_SETLIST_HEADER_X = 60
    const val CONCERT_SETLIST_HEADER_Y = 826

    /** The Lesson list's bottom-left "Back" button, its own exit to the career screen. Measured on
     * song_list.png. This is the read-only prowl's safe way out: it leaves the shop without learning
     * or scheduling, unlike a stray tap on a card or the Learn/Schedule button. */
    const val LIST_BACK_X = 125
    const val LIST_BACK_Y = 1848

    // OCR regions (x, y, w, h) on 1080x1920, read live.
    val LIST_HEADER_OCR_REGION = intArrayOf(0, 10, 360, 60)
    val DIALOG_HEADER_OCR_REGION = intArrayOf(300, 66, 480, 62)
    fun balanceOcrRegion(i: Int): IntArray = intArrayOf(BALANCE_BADGE_XS[i] + 40, BALANCE_ROW_Y - 30, 110, 60)
    fun cardTitleOcrRegion(i: Int): IntArray = intArrayOf(80, CARD_HEADER_YS[i] - 30, 640, 60)
    fun cardMasteryOcrRegion(i: Int): IntArray = intArrayOf(400, CARD_HEADER_YS[i] + 98, 620, 60)
    fun cardConcertOcrRegion(i: Int): IntArray = intArrayOf(400, CARD_HEADER_YS[i] + 200, 620, 60)

    /** One of card [card]'s five per-type cost cells (type 0..4 = Da, Pa, Vo, Vi, Co). The cost strip
     * spans x[300,1020] in five even 144px cells; the number sits in the right half of each. Width 68
     * (not the full cell) so the rightmost Co cell clears the strip's rounded purple border, which was
     * bleeding into the crop and nulling the Co read on affordable cards. Cost digits are high-contrast
     * dark-brown on an affordable card; an unaffordable card greys them out and OCR of those is best
     * effort, but the decider skips unaffordable cards anyway. */
    fun cardCostCellOcrRegion(card: Int, type: Int): IntArray =
        intArrayOf(300 + 144 * type + 66, CARD_COST_STRIP_YS[card] - 28, 68, 58)

    val POINTS_LEFT_OVER_OCR_REGION = intArrayOf(170, 1500, 740, 60)

    /** The learn/schedule confirmation dialog. The card title is OCR'd for the caller's verifyAgainst
     * gate and the kind pill corroborates it; the "Confirmation" vs "Schedule" header uses
     * DIALOG_HEADER_OCR_REGION, and the affordable-vs-shortfall split uses
     * grandConcertScheduleShortfallPresent. Measured on the 2026-07-24 song-confirm capture. */
    val CONFIRM_TITLE_OCR_REGION = intArrayOf(90, 150, 660, 70)
    val CONFIRM_KIND_PILL_OCR_REGION = intArrayOf(760, 150, 250, 60)

    /** The two confirmation buttons: the green Learn/Schedule (bottom-right) and Cancel (bottom-left). */
    const val CONFIRM_AFFIRMATIVE_X = 775
    const val CONFIRM_AFFIRMATIVE_Y = 1772
    const val CONFIRM_CANCEL_X = 300
    const val CONFIRM_CANCEL_Y = 1772

    val CONCERT_INDEX_OCR_REGION = intArrayOf(300, 185, 480, 90)
    val CONCERT_HYPE_OCR_REGION = intArrayOf(380, 305, 320, 62)
    val CONCERT_SONGS_OCR_REGION = intArrayOf(620, 388, 140, 56)
}

/** Bright green dialog header, measured (130, 206, 11): green dominant, red mid, blue near zero. */
private fun isLessonDialogGreen(r: Int, g: Int, b: Int): Boolean = g >= 170 && g - r >= 55 && g - b >= 130

/** True when the Lesson list is on screen: the dark-blue top band plus ANY card header that reads
 * as a technique (green) or song (purple) card.
 *
 * Any of the three headers proves the list; requiring specifically card 0 cost a real career-end
 * drain twice on 2026-07-26. The career-end list greys out a whole unaffordable card (header
 * included), card 0 happened to be unaffordable, its header read UNKNOWN, and "the list did not
 * open" fired with the list fully painted and a Learnable card sitting in slot 2
 * (fixture technique_list_career_end_dimmed). */
fun grandConcertLessonListPresent(sampler: SparkPixelSampler): Boolean {
    val (tr, tg, tb) = mean(sampler, GrandConcertLessonGeometry.LIST_TOP_BAND_X, GrandConcertLessonGeometry.LIST_TOP_BAND_Y)
    val darkBlueTop = tb >= 100 && tb - tr >= 40 && tb - tg >= 30 && tr < 120
    if (!darkBlueTop) return false
    return GrandConcertLessonGeometry.CARD_HEADER_YS.indices.any {
        grandConcertLessonCardKind(sampler, it) != LessonCardKind.UNKNOWN
    }
}

/** True when a full-screen lesson/concert dialog (Confirmation / Schedule / Concert Info) is up:
 * the green header band at the very top. */
fun grandConcertDialogHeaderPresent(sampler: SparkPixelSampler): Boolean {
    val (r, g, b) = mean(sampler, GrandConcertLessonGeometry.HEADER_GREEN_X, GrandConcertLessonGeometry.HEADER_TOP_Y)
    return isLessonDialogGreen(r, g, b)
}

/** True when the "Scheduling Complete" dialog is up: the green header sits mid-screen (a smaller
 * centred dialog over the dimmed list) rather than at the very top. */
fun grandConcertSchedulingCompletePresent(sampler: SparkPixelSampler): Boolean {
    val (tr, tg, tb) = mean(sampler, GrandConcertLessonGeometry.HEADER_GREEN_X, GrandConcertLessonGeometry.HEADER_TOP_Y)
    val (mr, mg, mb) = mean(sampler, GrandConcertLessonGeometry.HEADER_GREEN_X, GrandConcertLessonGeometry.HEADER_MID_Y)
    return !isLessonDialogGreen(tr, tg, tb) && isLessonDialogGreen(mr, mg, mb)
}

/** True when the Schedule dialog's red "Not enough performance points" shortfall band is present -
 * the signal that separates the unaffordable Schedule dialog from the affordable Learn dialog. */
fun grandConcertScheduleShortfallPresent(sampler: SparkPixelSampler): Boolean {
    var hits = 0
    var x = GrandConcertLessonGeometry.SHORTFALL_X_START
    while (x <= GrandConcertLessonGeometry.SHORTFALL_X_END) {
        val p = sampler.argb(x, GrandConcertLessonGeometry.SHORTFALL_BAND_Y)
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        if (r > 180 && g < 130 && b < 110) hits++
        x += 6
    }
    return hits >= 10
}

/** True when the Concert Info screen is up: its top green header plus the pale-green "Set List"
 * sub-header. */
fun grandConcertConcertInfoPresent(sampler: SparkPixelSampler): Boolean {
    if (!grandConcertDialogHeaderPresent(sampler)) return false
    val (r, g, b) = mean(sampler, GrandConcertLessonGeometry.CONCERT_SETLIST_HEADER_X, GrandConcertLessonGeometry.CONCERT_SETLIST_HEADER_Y)
    // Pale green sub-header: green elevated over red and blue, but far paler than the top band.
    return g >= 210 && g - b >= 25 && g - r >= 10
}

/** The kind of a lesson-list card from its header-bar colour: green bar = technique, purple bar =
 * song. Returns UNKNOWN when neither.
 *
 * The bar carries white title text, so a single fixed sample point lands on a letter for long titles
 * (e.g. "Zero Is Where the Center Stands!") and misreads as UNKNOWN. Instead the whole bar row is
 * polled and only the SATURATED pixels are averaged: the bar colour is saturated while the white text
 * and the pale kind pill are not, so the average is the bar colour regardless of where the text sits. */
fun grandConcertLessonCardKind(sampler: SparkPixelSampler, cardIndex: Int): LessonCardKind {
    if (cardIndex !in GrandConcertLessonGeometry.CARD_HEADER_YS.indices) return LessonCardKind.UNKNOWN
    val y = GrandConcertLessonGeometry.CARD_HEADER_YS[cardIndex]
    var rs = 0
    var gs = 0
    var bs = 0
    var n = 0
    var x = 80
    while (x <= 860) {
        val (r, g, b) = mean(sampler, x, y)
        if (maxOf(r, g, b) - minOf(r, g, b) >= 40) {
            rs += r
            gs += g
            bs += b
            n++
        }
        x += 20
    }
    if (n < 4) return LessonCardKind.UNKNOWN
    val r = rs / n
    val g = gs / n
    val b = bs / n
    return when {
        g >= 170 && g - b >= 60 && g - r >= 20 -> LessonCardKind.TECHNIQUE
        b >= 190 && b - g >= 20 && r >= 140 -> LessonCardKind.SONG
        // Career-end grey-out tiers: the career-end list dims a whole unaffordable card to
        // roughly 0.6x brightness with the hue preserved, which drops the header below the
        // absolute gates above while the channel differences survive. Measured dim technique
        // headers (101,138,65) and (97,137,58) against bright 215-222 on the same frame
        // (fixture technique_list_career_end_dimmed). The dim song tier is scaled from the
        // bright song numbers (172,144,235), not yet observed dim in the wild. Both tiers
        // hold zero false positives across every grandconcert fixture (2026-07-26 sweep).
        g in 110..169 && g - b >= 45 && g - r >= 25 -> LessonCardKind.TECHNIQUE
        b in 110..189 && b - g >= 10 && b - r >= 25 && r >= 80 -> LessonCardKind.SONG
        else -> LessonCardKind.UNKNOWN
    }
}

/** True when card [cardIndex] is affordable, read from its cost strip: an affordable strip is
 * bright coloured (max channel high), an unaffordable one is darkened. The gold "Learnable!"
 * marker is the corroborating signal in [grandConcertCardLearnableMarker]. */
fun grandConcertCardAffordable(sampler: SparkPixelSampler, cardIndex: Int): Boolean {
    if (cardIndex !in GrandConcertLessonGeometry.CARD_COST_STRIP_YS.indices) return false
    val (r, g, b) = mean(sampler, GrandConcertLessonGeometry.CARD_COST_STRIP_X, GrandConcertLessonGeometry.CARD_COST_STRIP_YS[cardIndex])
    return maxOf(r, g, b) >= 200
}

/** True when card [cardIndex] shows the gold "Learnable!" marker above its kind badge. */
fun grandConcertCardLearnableMarker(sampler: SparkPixelSampler, cardIndex: Int): Boolean {
    if (cardIndex !in GrandConcertLessonGeometry.CARD_LEARNABLE_YS.indices) return false
    val (r, g, b) = mean(sampler, GrandConcertLessonGeometry.CARD_KIND_BADGE_X, GrandConcertLessonGeometry.CARD_LEARNABLE_YS[cardIndex])
    // Gold marker text: red and green high, blue low (measured (203, 161, 64)).
    return r >= 180 && g >= 140 && b <= 110 && r - b >= 90
}

/**
 * Geometry and probes for the Grand Concert TRAINING screen (facility selected, preview shown).
 * Measured on the 2026-07-23 capture MuMu-20260723-022350-839.png (Guts Lvl 1 selected). All
 * coordinates are 1080x1920.
 *
 * The load-bearing probe here is [selectedTrainingPerformanceRows]: the performance type a
 * training grants is RANDOM PER TURN, so it is read from the screen (which Performance Points
 * row carries the gold "+N" gain annotation) rather than inferred from the facility. This is the
 * pixel half of the same rule the model enforces in GrandConcertFacilityModel.
 */
object GrandConcertTrainingGeometry {
    /** The five Performance Points rows in the left panel, top to bottom: Da, Pa, Vo, Vi, Co. The
     * row order is fixed and is how a row maps to a [PerformancePointType]. */
    val PERF_ROW_YS = listOf(575, 672, 772, 868, 965)
    val PERF_ROW_TYPES =
        listOf(
            PerformancePointType.DANCE,
            PerformancePointType.PASSION,
            PerformancePointType.VOCAL,
            PerformancePointType.VISUAL,
            PerformancePointType.COMPOSURE,
        )

    /** The coloured type tab on the left edge of each performance row (used to cross-check the
     * row order against the tab colour). */
    const val PERF_TAB_X = 48

    /** The full box a "+N" gain glyph occupies relative to its row's y. The glyph is a warm
     * gradient (gold at the top fading to red-orange at the bottom) and its baseline floats up
     * to ~45 px above the row line (measured across the 2026-07-27 telemetry corpus: 0008_POWER
     * put the whole glyph at rowY-41..rowY+13), so a single-line scan at the row y grazes the
     * faded edge and misses. The box is scanned per-pixel instead. */
    const val PERF_GAIN_BOX_X_START = 195
    const val PERF_GAIN_BOX_X_END = 335
    const val PERF_GAIN_BOX_Y_ABOVE = 45
    const val PERF_GAIN_BOX_Y_BELOW = 25

    /** Minimum warm-pixel samples (stride 2) for a row box to count as carrying a gain glyph.
     * Corpus calibration over 1150 row boxes: the smallest real one-digit glyph measures 81+,
     * so 90 keeps every observed glyph. Warm alone is NOT sufficient - see the white floor. */
    const val PERF_GAIN_MIN_WARM_SAMPLES = 90

    /** Minimum white-outline samples that must accompany the warm fill. Warm background art can
     * flood a row box (the launch fixture's trainee wears a red jacket right of the panel: warm
     * 164 on a row with no gain; Bakushin's stage art reads warm 315), but art has no thick
     * white outline: measured art tops out at 37 white samples while the faintest real glyph
     * reads 115+, so 100 sits in a 3x gap. */
    const val PERF_GAIN_MIN_WHITE_SAMPLES = 100

    /** Pink "Performance Points" header band centre, flat (183, 155, 249) on every capture. */
    const val PANEL_HEADER_X = 105
    const val PANEL_HEADER_Y = 492

    /** The coloured type chips on the panel's left edge, top to bottom Da/Pa/Vo/Vi/Co. Measured
     * pixel-identical across launch-night and 2026-07-27 telemetry frames. */
    val PANEL_CHIP_YS = listOf(580, 676, 772, 868, 964)

    /** The five facility buttons' per-turn type-icon centres (the small coloured hex badge on the
     * upper-left of each button). The SELECTED facility hides its badge behind the training's
     * special-effect art, so its type is read from the gain annotation instead. */
    val FACILITY_ICON_POINTS =
        listOf(
            StatNameSlot.SPEED to (100 to 1502),
            StatNameSlot.STAMINA to (286 to 1502),
            StatNameSlot.POWER to (474 to 1502),
            StatNameSlot.GUTS to (690 to 1480),
            StatNameSlot.WIT to (853 to 1502),
        )

    /** The blue "Failure N%" pill above the facility row. */
    const val FAILURE_PILL_X = 730
    const val FAILURE_PILL_Y = 1415

    // OCR regions (x, y, width, height) on 1080x1920, fed to the live OCR path. The fixture tests
    // pin that each stays in-frame and sits on the right content; the numbers themselves are read
    // live, matching how every other OCR region in this codebase is handled.
    val FAILURE_OCR_REGION = intArrayOf(640, 1392, 180, 60)
    val SELECTED_FACILITY_BANNER_OCR_REGION = intArrayOf(150, 300, 620, 48)

    /** The balance VALUE digits only. The "/N" cap line sits directly below (measured: value
     * digits span rowY-38..rowY+10, the cap line rowY+15..rowY+45), and the original 62-tall
     * region caught both: a small value like "5" OCR'd as the cap ("200"), which zeroed every
     * deficit the training bias needed and disarmed the point steering on the 2026-07-27
     * validation run. The region now ends at rowY+6, clear of the cap line by 9 px. */
    fun perfBalanceOcrRegion(rowIndex: Int): IntArray = intArrayOf(72, PERF_ROW_YS[rowIndex] - 38, 108, 44)

    fun perfMorePillOcrRegion(rowIndex: Int): IntArray = intArrayOf(70, PERF_ROW_YS[rowIndex] - 72, 130, 40)

    /** OCR band for the floating "+N" gain amount beside row [rowIndex], sized to the measured
     * glyph box (see [PERF_GAIN_BOX_Y_ABOVE]). */
    fun perfGainAmountOcrRegion(rowIndex: Int): IntArray = intArrayOf(185, PERF_ROW_YS[rowIndex] - 48, 165, 78)
}

/** A facility slot, kept probe-local so the Android-free probe layer does not depend on the
 * bot's StatName enum ordering. Maps to StatName by name in the caller. */
enum class StatNameSlot { SPEED, STAMINA, POWER, GUTS, WIT }

/** Blue "Failure" pill background, measured (14, 150, 252). */
private fun isFailurePillBlue(r: Int, g: Int, b: Int): Boolean = b >= 200 && b - r >= 120 && g in 110..210

/** True when the training screen's Failure pill is present where expected. Cheap structural
 * confirmation that a training preview is actually on screen before trusting the OCR regions. */
fun grandConcertTrainingFailurePillPresent(sampler: SparkPixelSampler): Boolean {
    val (r, g, b) = mean(sampler, GrandConcertTrainingGeometry.FAILURE_PILL_X, GrandConcertTrainingGeometry.FAILURE_PILL_Y)
    return isFailurePillBlue(r, g, b)
}

/** A "+N" gain-glyph fill pixel. The glyph is a warm vertical gradient, gold (253, 198, 117) at
 * the top fading to red-orange (240, 113, 80) at the bottom, so the test spans the whole warm
 * family; the white outline (g > 235) and the blue-leaning art behind the panel both fail it.
 * The original launch-night calibration only saw the gold mid-band and silently missed glyphs
 * sampled on their red half - measured 2026-07-27 when the telemetry corpus read zero gains on
 * frames whose "+N" was plainly visible. */
private fun isGainWarm(r: Int, g: Int, b: Int): Boolean = r >= 225 && b <= 175 && r - b >= 60 && g <= 235

/**
 * The Performance Points rows that carry a "+N" gain annotation, i.e. the type(s) the currently
 * selected training grants this turn. Normally one row (single type); two when friendship
 * training splits the gain. Read from the screen precisely because the mapping is not fixed to
 * the facility.
 *
 * Detection is a per-pixel count over each row's measured glyph box (the glyph floats above the
 * row line and grades gold to red, so a single-line single-hue scan misses; see
 * [GrandConcertTrainingGeometry.PERF_GAIN_BOX_Y_ABOVE]) and a row must show BOTH the warm fill
 * and the glyph's thick white outline - warm background art (a red jacket, stage lighting) has
 * no outline and is rejected by the white floor. At most two rows are returned, best counts
 * first, because a training never grants more than two types; any third candidate is art noise
 * by construction.
 */
fun selectedTrainingPerformanceRows(sampler: SparkPixelSampler): List<Int> {
    val counted = mutableListOf<Pair<Int, Int>>()
    for (i in GrandConcertTrainingGeometry.PERF_ROW_YS.indices) {
        val y = GrandConcertTrainingGeometry.PERF_ROW_YS[i]
        var warm = 0
        var white = 0
        var oy = y - GrandConcertTrainingGeometry.PERF_GAIN_BOX_Y_ABOVE
        while (oy <= y + GrandConcertTrainingGeometry.PERF_GAIN_BOX_Y_BELOW) {
            var ox = GrandConcertTrainingGeometry.PERF_GAIN_BOX_X_START
            while (ox <= GrandConcertTrainingGeometry.PERF_GAIN_BOX_X_END) {
                val p = sampler.argb(ox, oy)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (isGainWarm(r, g, b)) {
                    warm++
                } else if (r >= 236 && g >= 236 && b >= 231) {
                    white++
                }
                ox += 2
            }
            oy += 2
        }
        if (warm >= GrandConcertTrainingGeometry.PERF_GAIN_MIN_WARM_SAMPLES &&
            white >= GrandConcertTrainingGeometry.PERF_GAIN_MIN_WHITE_SAMPLES
        ) {
            counted.add(i to warm)
        }
    }
    return counted.sortedByDescending { it.second }.take(2).map { it.first }.sorted()
}

/** True when the training screen's Performance Points panel is on screen: the pink header band
 * plus at least four of the five coloured type chips on the panel's left edge (one chip may be
 * grazed by a floating overlay without invalidating the panel). This is the structural gate for
 * every panel read; the Failure-pill probe is NOT suitable because the pill follows the selected
 * facility across the screen. */
fun grandConcertPerformancePanelPresent(sampler: SparkPixelSampler): Boolean {
    val (hr, hg, hb) = mean(sampler, GrandConcertTrainingGeometry.PANEL_HEADER_X, GrandConcertTrainingGeometry.PANEL_HEADER_Y)
    if (!(hr in 160..210 && hg in 130..180 && hb >= 225)) return false
    val checks =
        listOf<(Int, Int, Int) -> Boolean>(
            { r, g, b -> b >= 210 && g in 150..210 && r <= 110 },
            { r, g, b -> r >= 225 && g <= 120 && b in 80..140 },
            { r, g, b -> r >= 225 && g in 105..165 && b in 160..220 },
            { r, g, b -> r >= 220 && g in 160..215 && b in 90..150 },
            { r, g, b -> r in 135..195 && g <= 150 && b >= 220 },
        )
    var ok = 0
    for (i in 0..4) {
        val (r, g, b) = mean(sampler, GrandConcertTrainingGeometry.PERF_TAB_X - 2, GrandConcertTrainingGeometry.PANEL_CHIP_YS[i])
        if (checks[i](r, g, b)) ok++
    }
    return ok >= 4
}

/** The performance type(s) the selected training grants this turn, mapped from the annotated
 * rows. Empty when none could be read. */
fun selectedTrainingPerformanceTypes(sampler: SparkPixelSampler): List<PerformancePointType> =
    selectedTrainingPerformanceRows(sampler).map { GrandConcertTrainingGeometry.PERF_ROW_TYPES[it] }

/**
 * Classifies a performance-type icon colour into its [PerformancePointType]. Thresholds measured
 * on the five live icons: Dance blue (37..90, 150..180, 228..250), Passion red (255, 93, 89),
 * Vocal pink (255, 138, 193), Visual yellow (237, 171, 40), Composure violet (170, 140, 243).
 * Returns null on an unsaturated or ambiguous sample rather than guessing.
 */
fun classifyPerformanceIconColor(r: Int, g: Int, b: Int): PerformancePointType? {
    if (maxOf(r, g, b) - minOf(r, g, b) < 45) return null
    // VOCAL (pink) must be tested before COMPOSURE (violet): both are blue-elevated, but pink
    // keeps red high (r >= 225) while violet lets blue dominate red (b - r >= 40). Testing
    // COMPOSURE first would swallow pink.
    return when {
        r >= 220 && g < 135 && b < 135 -> PerformancePointType.PASSION
        b >= 200 && r < 160 && g < 190 -> PerformancePointType.DANCE
        r >= 225 && b >= 150 && r - g >= 70 && b > g -> PerformancePointType.VOCAL
        b >= 195 && b - r >= 40 -> PerformancePointType.COMPOSURE
        r >= 200 && g >= 135 && b < 110 -> PerformancePointType.VISUAL
        else -> null
    }
}

/** The per-turn performance type shown on each facility button, by facility slot. A facility
 * whose badge is hidden (the selected one) or unreadable maps to null. This is the direct pixel
 * evidence that the per-turn mapping is read from the screen, not assumed. */
fun grandConcertFacilityIconTypes(sampler: SparkPixelSampler): Map<StatNameSlot, PerformancePointType?> {
    val out = LinkedHashMap<StatNameSlot, PerformancePointType?>()
    for ((slot, xy) in GrandConcertTrainingGeometry.FACILITY_ICON_POINTS) {
        val dominant = dominantSaturated(sampler, xy.first, xy.second, 14)
        out[slot] = dominant?.let { classifyPerformanceIconColor(it.first, it.second, it.third) }
    }
    return out
}

/** The mean of the saturated pixels in a window, used for the small hex badges: a single
 * strongest-pixel sample grabs whatever edge pixel is most saturated (the red art abutting the
 * Wit badge fooled it), whereas the badge dominates its own window, so its mean is stable.
 * Null when too few pixels clear the saturation floor to be a real badge. */
private fun dominantSaturated(sampler: SparkPixelSampler, cx: Int, cy: Int, rad: Int): Triple<Int, Int, Int>? {
    var rs = 0
    var gs = 0
    var bs = 0
    var n = 0
    var oy = -rad
    while (oy <= rad) {
        var ox = -rad
        while (ox <= rad) {
            val p = sampler.argb(cx + ox, cy + oy)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (maxOf(r, g, b) - minOf(r, g, b) >= 55) {
                rs += r
                gs += g
                bs += b
                n++
            }
            ox += 2
        }
        oy += 2
    }
    return if (n >= 12) Triple(rs / n, gs / n, bs / n) else null
}

/** The "Hype Level" gauge that appears at the top-left of the career screen once the scenario
 * mechanic is active (the purple titled gauge, measured (211, 191, 255) title over a violet
 * gauge). Structural presence only. */
object GrandConcertHypeGauge {
    const val TITLE_X = 120
    const val TITLE_Y = 305
    val TITLE_OCR_REGION = intArrayOf(20, 285, 210, 44)
}

fun grandConcertHypeGaugePresent(sampler: SparkPixelSampler): Boolean {
    val (r, g, b) = mean(sampler, GrandConcertHypeGauge.TITLE_X, GrandConcertHypeGauge.TITLE_Y)
    // Pale violet title band: blue highest, red elevated, green suppressed relative to blue.
    return b >= 220 && r >= 170 && b - g >= 30 && r - g >= 10
}

/**
 * The "Complete Career" screen a finished (or failed) Grand Concert career lands on. None of the
 * shared screen checks recognise it and the URA career-end template scores ~0.55 on it, so before
 * this probe existed the run spiraled through the unknown-screen ladder here (observed 2026-07-23,
 * twelve consecutive unknown-screen ticks on a failed Tosen Jordan career).
 *
 * Anchors, all measured on the career_complete.png fixture (the bot's own RGB-verified capture):
 * the flat purple "Remaining Performance Points" banner fill, the five balance-type icons sitting
 * between the white rows of the balance strip, and the pink Complete Career button. Position
 * separates this from the lesson list, which shows the same balance-strip artwork near the top of
 * the screen rather than at y ~1350.
 */
object GrandConcertCareerComplete {
    /** Interior points of the purple banner, avoiding the white label text. */
    val BANNER_POINTS = listOf(400 to 1250, 540 to 1250, 700 to 1250, 400 to 1290, 540 to 1290, 700 to 1290)

    /** Centre of each balance-type icon in the strip, in row order Da, Pa, Vo, Vi, Co. */
    val ICON_CELLS =
        listOf(
            (112 to 1352) to PerformancePointType.DANCE,
            (306 to 1355) to PerformancePointType.PASSION,
            (498 to 1352) to PerformancePointType.VOCAL,
            (684 to 1354) to PerformancePointType.VISUAL,
            (868 to 1352) to PerformancePointType.COMPOSURE,
        )

    /** Interior of the pink Complete Career button. */
    val COMPLETE_BUTTON_POINTS = listOf(450 to 1630, 540 to 1630, 630 to 1630)

    /** The Lessons button on THIS screen (bottom right, magenta interior (233, 92, 243)). Not the
     * career main screen's scenario slot, which sits elsewhere. */
    const val LESSONS_X = 900
    const val LESSONS_Y = 1615

    /** The Skills button on THIS screen (bottom left, cyan interior (53, 202, 220)). The URA
     * career-end Learn-button template does not exist on this layout. */
    const val SKILLS_X = 175
    const val SKILLS_Y = 1585
}

/**
 * The concert-pending screen ("Nth Concert" with the Goal ribbon over the Concert button), where
 * the career waits for the player to run a concert. Anchors measured identically on the 2nd and
 * 3rd Concert captures: the flat purple "Hype Level" banner (the same (183, 150, 255) asset the
 * Complete Career screen uses, but at y ~500 instead of ~1270) and the red Goal ribbon. The
 * concert-number text deliberately plays no part, so one probe covers all five concerts.
 */
object GrandConcertConcertPending {
    val HYPE_BANNER_POINTS = listOf(440 to 500, 540 to 500, 640 to 500, 440 to 516, 540 to 516, 640 to 516)

    /** Corners of the Goal ribbon, avoiding the folded/darker centre. */
    val GOAL_RIBBON_POINTS = listOf(700 to 1495, 840 to 1495, 700 to 1505, 840 to 1505)
}

fun grandConcertConcertPendingScreenPresent(sampler: SparkPixelSampler): Boolean {
    val banner =
        GrandConcertConcertPending.HYPE_BANNER_POINTS.count { (x, y) ->
            val (r, g, b) = mean(sampler, x, y)
            b >= 225 && b - g >= 70 && r in 150..215
        } >= 5
    if (!banner) return false
    val ribbon =
        GrandConcertConcertPending.GOAL_RIBBON_POINTS.count { (x, y) ->
            val (r, g, b) = mean(sampler, x, y)
            r >= 225 && g <= 95 && b <= 95
        } >= 3
    return ribbon
}

/**
 * The concert escort's screen states, all measured on the 2026-07-24 3rd Concert captures. The
 * escort only ever taps a control whose screen probe passed on the current frame; everything else
 * waits and eventually hands off.
 */
object GrandConcertEscort {
    /** The Concert button on the pending screen (tap target only; the probe is the pending screen). */
    const val CONCERT_BUTTON_X = 765
    const val CONCERT_BUTTON_Y = 1635

    /** "Ready to start the concert?" dialog: green header band and the green Start button. */
    val CONFIRM_HEADER_POINTS = listOf(300 to 490, 780 to 490)
    val CONFIRM_START_POINTS = listOf(700 to 1377, 850 to 1377)
    const val CONFIRM_START_X = 775
    const val CONFIRM_START_Y = 1377
    val CONFIRM_READY_OCR_REGION = intArrayOf(250, 1205, 580, 62)

    /** The playback skip control: a white disc at the bottom right with a brown glyph. */
    const val SKIP_GLYPH_X = 1000
    const val SKIP_GLYPH_Y = 1850
    val SKIP_DISC_WHITE_POINTS = listOf(975 to 1870, 1025 to 1840)

    /** The green Next button shared by the result banner and the schedule overview. */
    val NEXT_BUTTON_GREEN_POINTS = listOf(420 to 1668, 660 to 1668)
    const val NEXT_BUTTON_X = 540
    const val NEXT_BUTTON_Y = 1668

    /** The "Bonuses Updated!" acknowledgment that follows a concert once queued concert bonuses
     * activate (Close / Confirm over the career screen). Close DISMISSES it; Confirm opens the
     * Active Concert Bonuses detail panel instead (learned the hard way on the second validation
     * career's first concert, where the escort confirmed itself onto the panel). */
    val BONUSES_TITLE_POINTS = listOf(200 to 610, 880 to 610)
    val BONUSES_CONFIRM_POINTS = listOf(700 to 1250, 850 to 1250)
    const val BONUSES_CLOSE_X = 300
    const val BONUSES_CLOSE_Y = 1250

    /** The "Active Concert Bonuses" detail panel (green title band, Close-only). Reached via the
     * Bonuses Updated dialog's Confirm; closed with its wide white Close button. The purple
     * "Bonus Effects" band is the discriminator against the lesson Learn dialog, whose card
     * header band sits at the same height as this panel's title. */
    val ACTIVE_BONUSES_TITLE_POINTS = listOf(150 to 490, 930 to 490)
    val ACTIVE_BONUSES_EFFECTS_BAND_POINTS = listOf(300 to 577, 780 to 577)
    val ACTIVE_BONUSES_CLOSE_POINTS = listOf(420 to 1378, 660 to 1378)
    const val ACTIVE_BONUSES_CLOSE_X = 540
    const val ACTIVE_BONUSES_CLOSE_Y = 1378

    /** The Grand Concert's "ON STAGE!" huddle (the scenario's Inspiration-style interstitial):
     * a vivid pink-purple medallion centred at (540, 1100). One tap on it proceeds. */
    val ON_STAGE_MEDALLION_POINTS = listOf(460 to 1100, 540 to 1030, 620 to 1100, 540 to 1170)
    const val ON_STAGE_TAP_X = 540
    const val ON_STAGE_TAP_Y = 1100

    /** The Grand finale's start confirmation is taller than the numbered concerts' and carries a
     * "Skip the Grand Concert cutscene" checkbox. The purple "Hype Level" banner above the
     * checkbox marks the variant; the points sit in the banner's side margins because the white
     * banner text washes out a neighborhood mean taken through it. The checkbox glyph means
     * (211,212,212) unchecked and (157,211,77) checked on the finale captures. */
    val GRAND_CONFIRM_HYPE_BANNER_POINTS = listOf(452 to 982, 628 to 982)
    const val GRAND_CONFIRM_CHECKBOX_X = 258
    const val GRAND_CONFIRM_CHECKBOX_Y = 1185
}

/** Escort-dialog green: the same family as the lesson dialogs' header and affirmative. */
private fun isEscortGreen(r: Int, g: Int, b: Int): Boolean = g >= 180 && g - r >= 55 && g - b >= 120

/** True when the "Ready to start the concert?" confirmation dialog is up: its green header band
 * (vertically centred, unlike the full-height lesson dialogs) plus the green Start button. */
fun grandConcertConcertConfirmPresent(sampler: SparkPixelSampler): Boolean {
    val header =
        GrandConcertEscort.CONFIRM_HEADER_POINTS.count { (x, y) ->
            val (r, g, b) = mean(sampler, x, y)
            isEscortGreen(r, g, b)
        } == 2
    if (!header) return false
    return GrandConcertEscort.CONFIRM_START_POINTS.count { (x, y) ->
        val (r, g, b) = mean(sampler, x, y)
        isEscortGreen(r, g, b)
    } == 2
}

/** State of the Grand finale confirm's cutscene-skip checkbox. ABSENT on the numbered concerts'
 * dialog, which has no Hype Level banner and no checkbox. */
enum class GrandCutsceneCheckbox { ABSENT, UNCHECKED, CHECKED }

/** Reads the cutscene-skip checkbox on an open start confirmation. The purple Hype Level banner
 * gates the read so the numbered concerts' shorter dialog reports ABSENT; the checkbox itself is
 * classified by its glyph color (gray unchecked, escort-green checked). */
fun grandConcertCutsceneCheckboxState(sampler: SparkPixelSampler): GrandCutsceneCheckbox {
    val banner =
        GrandConcertEscort.GRAND_CONFIRM_HYPE_BANNER_POINTS.count { (x, y) ->
            val (r, g, b) = mean(sampler, x, y)
            b >= 225 && b - g >= 70 && r in 150..215
        } == 2
    if (!banner) return GrandCutsceneCheckbox.ABSENT
    // The white box around the glyph dilutes the mean below the escort-green thresholds, so the
    // checked state gets its own softer classifier (checked mean (157,211,77), unchecked gray).
    val (r, g, b) = mean(sampler, GrandConcertEscort.GRAND_CONFIRM_CHECKBOX_X, GrandConcertEscort.GRAND_CONFIRM_CHECKBOX_Y)
    val checked = g >= 180 && g - b >= 90 && g - r >= 35
    return if (checked) GrandCutsceneCheckbox.CHECKED else GrandCutsceneCheckbox.UNCHECKED
}

/** True when the concert playback is on screen, identified by its skip control: the brown glyph
 * inside a white disc at the bottom right. The 3D scene itself is too dynamic to anchor on. */
fun grandConcertPlaybackSkipPresent(sampler: SparkPixelSampler): Boolean {
    val glyph = dominantSaturated(sampler, GrandConcertEscort.SKIP_GLYPH_X, GrandConcertEscort.SKIP_GLYPH_Y, 12)
    val brown = glyph != null && glyph.first >= 100 && glyph.first - glyph.third >= 60 && glyph.second in 40..110
    if (!brown) return false
    return GrandConcertEscort.SKIP_DISC_WHITE_POINTS.all { (x, y) ->
        val (r, g, b) = mean(sampler, x, y)
        minOf(r, g, b) >= 225
    }
}

/** True when a concert result screen's green Next button is present (the Great/Success banner and
 * the schedule overview share the same control). */
fun grandConcertResultNextPresent(sampler: SparkPixelSampler): Boolean =
    GrandConcertEscort.NEXT_BUTTON_GREEN_POINTS.count { (x, y) ->
        val (r, g, b) = mean(sampler, x, y)
        isEscortGreen(r, g, b)
    } == 2

/** True when the "ON STAGE!" huddle is up: all four medallion points read the vivid pink-purple
 * disc. Observed on the Grand Concert (the finale's Inspiration-style interstitial); one tap on
 * the medallion proceeds to playback. */
fun grandConcertOnStagePresent(sampler: SparkPixelSampler): Boolean =
    GrandConcertEscort.ON_STAGE_MEDALLION_POINTS.count { (x, y) ->
        val (r, g, b) = mean(sampler, x, y)
        r >= 200 && b >= 230 && r - g >= 25 && b - g >= 25
    } == 4

/** True when the post-concert "Bonuses Updated!" acknowledgment is up: its green title band (a
 * softer gradient green than the dialog headers) plus the green Confirm at its own height. */
fun grandConcertBonusesUpdatedPresent(sampler: SparkPixelSampler): Boolean {
    val title =
        listOf(540 to 655, 880 to 610).count { (x, y) ->
            val (r, g, b) = mean(sampler, x, y)
            g >= 170 && g - b >= 60 && g - r >= 40
        } == 2
    if (!title) return false
    return GrandConcertEscort.BONUSES_CONFIRM_POINTS.count { (x, y) ->
        val (r, g, b) = mean(sampler, x, y)
        isEscortGreen(r, g, b)
    } == 2
}

/** True when the "Active Concert Bonuses" detail panel is up: the strong green title band at its
 * own height plus the wide WHITE Close button (which separates it from the Start confirmation,
 * whose button at a similar height is green). */
fun grandConcertActiveBonusesPanelPresent(sampler: SparkPixelSampler): Boolean {
    val title =
        GrandConcertEscort.ACTIVE_BONUSES_TITLE_POINTS.count { (x, y) ->
            val (r, g, b) = mean(sampler, x, y)
            isEscortGreen(r, g, b)
        } == 2
    if (!title) return false
    val effectsBand =
        GrandConcertEscort.ACTIVE_BONUSES_EFFECTS_BAND_POINTS.count { (x, y) ->
            val (r, g, b) = mean(sampler, x, y)
            b >= 225 && b - g >= 70 && r in 150..215
        } == 2
    if (!effectsBand) return false
    return GrandConcertEscort.ACTIVE_BONUSES_CLOSE_POINTS.count { (x, y) ->
        val (r, g, b) = mean(sampler, x, y)
        minOf(r, g, b) >= 225
    } == 2
}

fun grandConcertCareerCompleteScreenPresent(sampler: SparkPixelSampler): Boolean {
    // Banner: flat (183, 150, 255) fill; require most points so one text stroke cannot veto.
    val banner =
        GrandConcertCareerComplete.BANNER_POINTS.count { (x, y) ->
            val (r, g, b) = mean(sampler, x, y)
            b >= 225 && b - g >= 70 && r in 150..215
        } >= 4
    if (!banner) return false

    // Balance-type icons: each cell's saturated-pixel mean must classify as its expected type
    // (the icons carry white glyph text, so a plain point mean washes out - the saturated mean is
    // what the facility badges already use). Four of five, so one glare cannot veto the screen.
    val icons =
        GrandConcertCareerComplete.ICON_CELLS.count { (xy, expected) ->
            val dominant = dominantSaturated(sampler, xy.first, xy.second, 20)
            dominant?.let { classifyPerformanceIconColor(it.first, it.second, it.third) } == expected
        }
    if (icons < 4) return false

    val pinkButton =
        GrandConcertCareerComplete.COMPLETE_BUTTON_POINTS.count { (x, y) ->
            val (r, g, b) = mean(sampler, x, y)
            r >= 225 && g in 70..160 && b in 110..200 && r - g >= 90
        } >= 2
    return pinkButton
}
