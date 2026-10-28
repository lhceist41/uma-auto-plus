package com.steve1316.uma_android_automation.bot.misc

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.CoordinateTap
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.OwnUiForeground
import com.steve1316.uma_android_automation.components.ButtonBack
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.ButtonConfirm
import com.steve1316.uma_android_automation.components.ButtonDailyLegendRacesCard
import com.steve1316.uma_android_automation.components.ButtonDailyProgramTile
import com.steve1316.uma_android_automation.components.ButtonMenuBarHomeSelected
import com.steve1316.uma_android_automation.components.ButtonMenuBarHomeUnselected
import com.steve1316.uma_android_automation.components.ButtonMenuBarRaceSelected
import com.steve1316.uma_android_automation.components.ButtonMenuBarRaceUnselected
import com.steve1316.uma_android_automation.components.ButtonNext
import com.steve1316.uma_android_automation.components.ButtonNextWithImage
import com.steve1316.uma_android_automation.components.ButtonRaceConfirm
import com.steve1316.uma_android_automation.components.ButtonRaceExclamationShiftedUp
import com.steve1316.uma_android_automation.components.ButtonRaceManual
import com.steve1316.uma_android_automation.components.ButtonSkip
import com.steve1316.uma_android_automation.components.ButtonViewResults
import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.DialogItemsSelected
import com.steve1316.uma_android_automation.components.DialogObjects
import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.components.LabelDailyLegendLogos
import com.steve1316.uma_android_automation.components.LabelDailyLegendRacesHeader
import com.steve1316.uma_android_automation.components.LabelDailyPrograms
import com.steve1316.uma_android_automation.components.LabelDoneForToday
import com.steve1316.uma_android_automation.components.LabelItemsSelected
import com.steve1316.uma_android_automation.components.LabelLegendRunnerSelection
import com.steve1316.uma_android_automation.components.LabelMainRewards
import com.steve1316.uma_android_automation.components.LabelRaceDetails
import com.steve1316.uma_android_automation.components.LabelRaceResultsHeader
import com.steve1316.uma_android_automation.components.LabelStarUnlockText
import com.steve1316.uma_android_automation.components.Region
import com.steve1316.uma_android_automation.utils.ScreenBand
import com.steve1316.uma_android_automation.utils.TeamTrialsGeometry
import com.steve1316.uma_android_automation.utils.gameY
import com.steve1316.uma_android_automation.utils.parseTicketCount
import org.opencv.core.Point

internal enum class LegendScreen {
    HOME,
    RACE_TAB,
    DAILY_PROGRAMS,
    LEGEND_GRID,

    /** A legend's race list, one row per difficulty. */
    RACE_LIST,
    RACE_DETAILS,
    RUNNER_SELECTION,
    ITEMS_SELECTED,

    /** The race prep screen with View Results. */
    RACE_PREP,

    /** The race playing, with Skip. */
    RACE_PLAYING,

    /** A screen whose only known control is Next: the line-up before the race, the results and rewards after it. */
    NEXT,
    SPENDING_DIALOG,
    UNKNOWN,
}

internal enum class LegendTarget {
    RACE_TAB,
    DAILY_PROGRAM_TILE,
    LEGEND_CARD,
    TOP_RACE_ROW,
    RACE,
    CONFIRM,
    NEXT,
    ITEMS_RACE,
    VIEW_RESULTS,
    WATCH_RACE,
    SKIP,

    /** The "TAP" prompt under a result splash. */
    TAP_PROMPT,
    BACK,
    CANCEL,
    CLOSE,
    HOME_TAB,
}

/** The Daily Legend Races card on the Daily Programs screen: present, done for today, and its ticket counter (null when unreadable). */
internal data class LegendCardStatus(val found: Boolean, val done: Boolean, val tickets: Int?)

/** One page of the legend grid: the preferred legend's logo when on it, every card's "to Star Unlock" strip, and a key that tells pages apart. */
internal data class LegendGridPage(val preferredLogo: Pair<Int, Int>?, val strips: List<Pair<Int, Int>>, val key: String)

/** The game side of [DailyLegendRace]. One implementation drives the game; the tests drive a scripted fake. */
internal interface LegendDevice {
    fun screen(): LegendScreen

    /** Taps [target]; false when it is not on screen. */
    fun press(target: LegendTarget): Boolean

    fun legendCard(): LegendCardStatus

    fun gridPage(preferred: String?): LegendGridPage

    /** Scrolls the grid about two rows down, or up. */
    fun scrollGrid(down: Boolean)

    fun tapGrid(point: Pair<Int, Int>)

    /** True when View Results is greyed out and the race has to be watched; null when that cannot be told. */
    fun viewResultsLocked(): Boolean?

    /** Offers a frame the step does not know to the shared dialog handler; true when it closed something. */
    fun handlePopup(): Boolean

    fun pause(seconds: Double)
}

/** The strip sits just below and right of the card's logo. */
internal fun stripBelongsToLogo(logo: Pair<Int, Int>, strip: Pair<Int, Int>): Boolean = strip.first - logo.first in 70..140 && strip.second - logo.second in 50..100

/** The first point in reading order: top row first, then left to right. Points within [rowTolerance] px of y share a row. */
internal fun firstInReadingOrder(points: List<Pair<Int, Int>>, rowTolerance: Int = 30): Pair<Int, Int>? {
    val top = points.minOfOrNull { it.second } ?: return null
    return points.filter { it.second - top <= rowTolerance }.minByOrNull { it.first }
}

/**
 * Races the Daily Legend Race once with the free ticket, from Home and back to Home: the preferred legend while it can still
 * unlock a star, else the first legend in the grid that can. Never buys a ticket: every spending dialog is cancelled.
 */
internal class DailyLegendRace(private val device: LegendDevice, private val preferred: String, private val log: (String) -> Unit = {}) {
    companion object {
        const val ANY_LEGEND = "First legend that can still unlock a star"

        /** Polls of about a second each before a screen counts as missing. */
        const val SCREEN_POLLS = 15

        /** Home shows before its buttons finish drawing; polls of about a second for a button. */
        const val BUTTON_POLLS = 6

        /** From Race! to the grid again: a watched race with skips can take over a minute. */
        const val RACE_POLLS = 120

        const val MAX_GRID_PAGES = 10

        const val MAX_NEXT_TAPS = 8

        /** A View Results that stays on screen this often is locked, whatever its colour reads. */
        const val VIEW_RESULTS_TAPS = 3

        /** Race! taps on an Items Selected that comes back after the first one; each may have opened a dialog that was cancelled. */
        const val MAX_ITEMS_RACE_TAPS = 2

        /** From a "to Star Unlock" strip: a spot on the card's art above it. */
        const val STRIP_TO_CARD_DY = -70

        const val FIRST_UNLOCKABLE = "the first legend that can still unlock a star"
    }

    private var spendingDialogsCancelled = 0

    /** Grid pages looked at for a strip in the last pass. */
    private var pagesRead = 0

    /** Runs the race and returns one note for the log. */
    fun run(): String {
        var note = race()
        if (spendingDialogsCancelled > 0) note += "; cancelled $spendingDialogsCancelled spending dialog(s)"
        if (!backToHome()) note += "; could not get back to Home"
        return note
    }

    private fun race(): String {
        if (awaitScreen(LegendScreen.HOME, polls = 3) != LegendScreen.HOME) return "not on Home, skipped"
        if (!pressUntil(LegendTarget.RACE_TAB)) return "Race tab not found"
        if (awaitScreen(LegendScreen.RACE_TAB) != LegendScreen.RACE_TAB) return "Race tab did not open"
        if (!pressUntil(LegendTarget.DAILY_PROGRAM_TILE)) return "Daily Program tile not found"
        if (awaitScreen(LegendScreen.DAILY_PROGRAMS) != LegendScreen.DAILY_PROGRAMS) return "Daily Programs did not open"
        device.pause(1.0)

        val card = device.legendCard()
        if (!card.found) return "Daily Legend Races card not found"
        if (card.done) return "done for today"
        if (card.tickets == 0) return "no ticket left"
        log("Legend Race tickets: ${card.tickets ?: "unreadable"}.")
        if (!device.press(LegendTarget.LEGEND_CARD)) return "Daily Legend Races card not found"
        if (awaitScreen(LegendScreen.LEGEND_GRID) != LegendScreen.LEGEND_GRID) return "the legend grid did not open"
        device.pause(1.0)

        val legend = pickLegend() ?: return "no \"to Star Unlock\" strip found on $pagesRead grid page(s)"
        if (awaitScreen(LegendScreen.RACE_LIST) != LegendScreen.RACE_LIST) return "the race list of $legend did not open"
        if (!device.press(LegendTarget.TOP_RACE_ROW)) return "no race row found for $legend"
        if (awaitScreen(LegendScreen.RACE_DETAILS) != LegendScreen.RACE_DETAILS) return "Race Details did not open"
        if (!device.press(LegendTarget.RACE)) return "Race! not found on Race Details"
        if (awaitScreen(LegendScreen.RUNNER_SELECTION) != LegendScreen.RUNNER_SELECTION) return "Runner Selection did not open"
        if (!device.press(LegendTarget.CONFIRM)) return "Confirm not found on Runner Selection"
        when (awaitAny(LegendScreen.NEXT, LegendScreen.ITEMS_SELECTED)) {
            LegendScreen.NEXT -> {
                device.press(LegendTarget.NEXT)
                if (awaitScreen(LegendScreen.ITEMS_SELECTED) != LegendScreen.ITEMS_SELECTED) return "Items Selected did not open"
            }

            LegendScreen.ITEMS_SELECTED -> {}
            else -> return "the line-up did not appear"
        }
        if (!device.press(LegendTarget.ITEMS_RACE)) return "Race! not found on Items Selected"
        log("Racing $legend with the free ticket.")
        return finishRace(legend)
    }

    /** The preferred legend's card while it still shows "to Star Unlock", else the first card that does. Null when none does. */
    private fun pickLegend(): String? {
        if (preferred != ANY_LEGEND) {
            scrollToTop()
            for (page in 1..MAX_GRID_PAGES) {
                val grid = device.gridPage(preferred)
                val logo = grid.preferredLogo
                if (logo != null) {
                    if (grid.strips.any { stripBelongsToLogo(logo, it) }) {
                        log("Racing $preferred.")
                        device.tapGrid(logo)
                        return preferred
                    }
                    log("$preferred shows no \"to Star Unlock\" strip; racing $FIRST_UNLOCKABLE.")
                    break
                }
                if (!scroll(down = true)) {
                    log("$preferred's logo was not found on $page grid page(s); racing $FIRST_UNLOCKABLE.")
                    break
                }
            }
        }
        scrollToTop()
        for (page in 1..MAX_GRID_PAGES) {
            pagesRead = page
            val first = firstInReadingOrder(device.gridPage(null).strips)
            if (first != null) {
                log("Racing $FIRST_UNLOCKABLE.")
                device.tapGrid(first.first to first.second + STRIP_TO_CARD_DY)
                return FIRST_UNLOCKABLE
            }
            if (!scroll(down = true)) return null
        }
        return null
    }

    /** One scroll of the grid; false when the page did not change, so the grid ends there. */
    private fun scroll(down: Boolean): Boolean {
        val before = device.gridPage(null).key
        device.scrollGrid(down)
        return device.gridPage(null).key != before
    }

    private fun scrollToTop() {
        for (page in 1..MAX_GRID_PAGES) if (!scroll(down = false)) return
    }

    /** From Race! on Items Selected to the grid or the Daily Programs screen: View Results, or the race with skips, then Next through the results. */
    private fun finishRace(legend: String): String {
        var raceSeen = false
        var nextTaps = 0
        var viewResultsTaps = 0
        var itemsRaceTaps = 0
        var unknowns = 0
        repeat(RACE_POLLS) {
            when (device.screen()) {
                LegendScreen.RACE_PREP -> {
                    raceSeen = true
                    unknowns = 0
                    if (device.viewResultsLocked() == true || ++viewResultsTaps > VIEW_RESULTS_TAPS) {
                        log("View Results is locked; watching the race.")
                        device.press(LegendTarget.WATCH_RACE)
                    } else {
                        device.press(LegendTarget.VIEW_RESULTS)
                    }
                }

                LegendScreen.RACE_PLAYING -> {
                    raceSeen = true
                    unknowns = 0
                    device.press(LegendTarget.SKIP)
                }

                LegendScreen.NEXT -> {
                    unknowns = 0
                    if (++nextTaps > MAX_NEXT_TAPS) return "raced $legend; the results did not end after $MAX_NEXT_TAPS Next taps"
                    device.press(LegendTarget.NEXT)
                }

                LegendScreen.ITEMS_SELECTED -> {
                    if (++itemsRaceTaps > MAX_ITEMS_RACE_TAPS) return "Race! on Items Selected did not start the race for $legend"
                    device.press(LegendTarget.ITEMS_RACE)
                }

                LegendScreen.SPENDING_DIALOG -> cancelSpendingDialog()

                LegendScreen.LEGEND_GRID, LegendScreen.RACE_LIST, LegendScreen.DAILY_PROGRAMS, LegendScreen.RACE_TAB, LegendScreen.HOME ->
                    return if (raceSeen && nextTaps > 0) "raced $legend" else "Race! was tapped for $legend, but no race screens were seen"

                LegendScreen.RACE_DETAILS, LegendScreen.RUNNER_SELECTION -> return "Race! was tapped for $legend, but the game went back to its race screens"

                // A result splash has no buttons, only a "TAP" prompt; a dialog over the results goes to the shared handler first.
                LegendScreen.UNKNOWN -> if (!device.handlePopup() && ++unknowns >= 2) device.press(LegendTarget.TAP_PROMPT)
            }
            device.pause(1.0)
        }
        return "raced $legend; the results did not end within $RACE_POLLS polls"
    }

    /** Leaves any screen of this step for Home. Close, Cancel and Back never accept anything; an unknown screen gets nothing else. */
    private fun backToHome(): Boolean {
        repeat(8) {
            when (awaitAny(*LegendScreen.entries.filter { it != LegendScreen.UNKNOWN }.toTypedArray(), polls = 3)) {
                LegendScreen.HOME -> return true
                LegendScreen.RACE_TAB, LegendScreen.DAILY_PROGRAMS, LegendScreen.LEGEND_GRID, LegendScreen.RACE_LIST -> device.press(LegendTarget.HOME_TAB)
                LegendScreen.RACE_DETAILS, LegendScreen.ITEMS_SELECTED, LegendScreen.SPENDING_DIALOG -> device.press(LegendTarget.CANCEL)
                LegendScreen.RUNNER_SELECTION, LegendScreen.RACE_PREP -> device.press(LegendTarget.BACK)
                LegendScreen.RACE_PLAYING -> device.press(LegendTarget.SKIP)
                LegendScreen.NEXT -> device.press(LegendTarget.NEXT)
                else -> device.handlePopup() || device.press(LegendTarget.CLOSE) || device.press(LegendTarget.CANCEL) || device.press(LegendTarget.BACK)
            }
        }
        return false
    }

    private fun pressUntil(target: LegendTarget): Boolean {
        repeat(BUTTON_POLLS) {
            if (device.press(target)) return true
            device.pause(1.0)
        }
        return false
    }

    private fun cancelSpendingDialog() {
        spendingDialogsCancelled++
        log("A spending dialog appeared; cancelling it.")
        if (!device.press(LegendTarget.CANCEL)) device.press(LegendTarget.CLOSE)
    }

    private fun awaitScreen(screen: LegendScreen, polls: Int = SCREEN_POLLS): LegendScreen? = awaitAny(screen, polls = polls)

    /** Polls until one of [screens] shows, cancelling spending dialogs on the way; null when none showed. */
    private fun awaitAny(vararg screens: LegendScreen, polls: Int = SCREEN_POLLS): LegendScreen? {
        repeat(polls) {
            val now = device.screen()
            if (now == LegendScreen.SPENDING_DIALOG && LegendScreen.SPENDING_DIALOG !in screens) {
                cancelSpendingDialog()
            } else if (now in screens) {
                return now
            }
            device.pause(1.0)
        }
        return null
    }
}

// The counter box and the Done strip offset are measured on 1080x1920 MuMu frames, from the centre of the Daily Programs
// label; the grid, swipe and race row values are upstream's, not yet seen on our frames.

/** From the "Daily Programs" label: the Daily Legend Races ticket counter's digits (x, y, w, h). */
private val LEGEND_COUNTER = intArrayOf(315, 68, 105, 50)

/** From the "Daily Programs" label: the centre of the Daily Legend Races card's "Done for today!" strip, and how far a match may sit from it. */
private const val LEGEND_STRIP_DX = 215
private const val LEGEND_STRIP_DY = 266
private const val LEGEND_STRIP_SLACK_X = 80
private const val LEGEND_STRIP_SLACK_Y = 40

/** From the "Daily Legend Races" header: the top of the card grid; its bottom sits above the menu bar. */
private const val GRID_TOP_FROM_HEADER = 60
private const val GRID_BOTTOM = 1700

/** How far one swipe moves the grid, about two rows of cards so each page overlaps the last. */
private const val GRID_SCROLL_TRAVEL = 500
private const val GRID_SWIPE_MS = 800L

/** From a race row's "Main Rewards" label: the race banner to tap. */
private const val ROW_BANNER_DX = -222
private const val ROW_BANNER_DY = 10

/** Looks at the Daily Programs counter this many frames before it counts as unreadable. */
private const val COUNTER_READS = 3

/**
 * The screen a frame shows, from the step's labels and buttons checked in this order. Spending dialogs first: one stacked on
 * Items Selected leaves that header visible beneath it. A legend's race list keeps the grid's header, so the grid is that
 * header without race rows. The Items Selected Race! is read only after the Race Details header, whose own Race! sits at the
 * bottom of the screen.
 */
internal fun legendScreenOf(spendingDialog: () -> Boolean, shows: (ComponentInterface) -> Boolean): LegendScreen =
    when {
        spendingDialog() -> LegendScreen.SPENDING_DIALOG
        shows(LabelItemsSelected) -> LegendScreen.ITEMS_SELECTED
        shows(LabelDailyPrograms) -> LegendScreen.DAILY_PROGRAMS
        shows(LabelDailyLegendRacesHeader) && !shows(LabelMainRewards) -> LegendScreen.LEGEND_GRID
        shows(LabelRaceResultsHeader) -> LegendScreen.NEXT
        shows(LabelRaceDetails) -> LegendScreen.RACE_DETAILS
        shows(LabelLegendRunnerSelection) -> LegendScreen.RUNNER_SELECTION
        shows(LabelMainRewards) -> LegendScreen.RACE_LIST
        shows(ButtonRaceExclamationShiftedUp) -> LegendScreen.ITEMS_SELECTED
        shows(ButtonViewResults) -> LegendScreen.RACE_PREP
        shows(ButtonSkip) -> LegendScreen.RACE_PLAYING
        shows(ButtonNext) || shows(ButtonNextWithImage) -> LegendScreen.NEXT
        shows(ButtonMenuBarRaceSelected) -> LegendScreen.RACE_TAB
        shows(ButtonMenuBarHomeSelected) -> LegendScreen.HOME
        else -> LegendScreen.UNKNOWN
    }

/** Drives [DailyLegendRace] on the game. */
internal class GameLegendDevice(private val game: Game, private val handlePopup: () -> Boolean) : LegendDevice {
    private val imageUtils get() = game.imageUtils

    override fun screen(): LegendScreen {
        val bitmap = imageUtils.getSourceBitmap()
        return legendScreenOf({ spendingDialogShown(bitmap) }) { bitmap.shows(it) }
    }

    private fun Bitmap.shows(component: ComponentInterface): Boolean = component.check(imageUtils, sourceBitmap = this)

    /** Reads the dialog title quietly: most unknown frames are transitions, not dialogs. */
    private fun spendingDialogShown(bitmap: Bitmap): Boolean {
        val title = DialogUtils.getTitle(imageUtils, bitmap, logOnMiss = false) ?: return false
        return DialogObjects.items.any { it.title == title && it.name in SPENDING_DIALOG_NAMES }
    }

    override fun press(target: LegendTarget): Boolean {
        val clicked =
            when (target) {
                LegendTarget.RACE_TAB -> ButtonMenuBarRaceUnselected.click(imageUtils, region = Region.bottomHalf)
                LegendTarget.DAILY_PROGRAM_TILE -> ButtonDailyProgramTile.click(imageUtils)
                LegendTarget.LEGEND_CARD -> ButtonDailyLegendRacesCard.click(imageUtils)
                LegendTarget.TOP_RACE_ROW -> tapTopRaceRow()
                LegendTarget.RACE -> ButtonRaceConfirm.click(imageUtils, region = Region.bottomHalf)
                LegendTarget.CONFIRM -> ButtonConfirm.click(imageUtils, region = Region.bottomHalf)
                LegendTarget.NEXT -> tapNext()
                LegendTarget.ITEMS_RACE -> tapItemsRace()
                LegendTarget.VIEW_RESULTS -> ButtonViewResults.click(imageUtils)
                LegendTarget.WATCH_RACE -> ButtonRaceManual.click(imageUtils)
                LegendTarget.SKIP -> ButtonSkip.click(imageUtils)
                LegendTarget.TAP_PROMPT -> tapPrompt()
                LegendTarget.BACK -> ButtonBack.click(imageUtils)
                LegendTarget.CANCEL -> ButtonCancel.click(imageUtils)
                LegendTarget.CLOSE -> ButtonClose.click(imageUtils)
                LegendTarget.HOME_TAB -> ButtonMenuBarHomeUnselected.click(imageUtils, region = Region.bottomHalf)
            }
        if (clicked) game.wait(1.0)
        return clicked
    }

    /** The results pages end on Next; a Race Results page that offers only Close, as the Daily Races total does, closes instead. */
    private fun tapNext(): Boolean {
        if (ButtonNextWithImage.click(imageUtils) || ButtonNext.click(imageUtils)) return true
        return LabelRaceResultsHeader.check(imageUtils) && ButtonClose.click(imageUtils, region = Region.bottomHalf)
    }

    /** The top row of the race list, which upstream races as the hardest difficulty. */
    private fun tapTopRaceRow(): Boolean {
        val top = LabelMainRewards.findAll(imageUtils).minByOrNull { it.y } ?: return false
        game.tap(imageUtils.relX(top.x, ROW_BANNER_DX).toDouble(), imageUtils.relY(top.y, ROW_BANNER_DY).toDouble())
        return true
    }

    /**
     * No item is picked. Only while the dialog title reads Items Selected: a dialog stacked on the popup keeps its header
     * and often its Race! visible beneath. Race! by its template, else at its place, as Team Trials does on the same popup.
     */
    private fun tapItemsRace(): Boolean {
        val bitmap = imageUtils.getSourceBitmap()
        if (DialogUtils.getTitle(imageUtils, bitmap, logOnMiss = false) != DialogItemsSelected.title) return false
        if (ButtonRaceExclamationShiftedUp.click(imageUtils, sourceBitmap = bitmap)) return true
        val y = gameY(TeamTrialsGeometry.ITEMS_RACE_Y.toDouble(), ScreenBand.DIALOG, bitmap.width, bitmap.height)
        CoordinateTap.tap(game.gestureUtils, TeamTrialsGeometry.ITEMS_RACE_X.toDouble(), y, "legend_items_selected_race")
        return true
    }

    private fun tapPrompt(): Boolean {
        val bitmap = imageUtils.getSourceBitmap()
        val y = gameY(TeamTrialsGeometry.SPLASH_TAP_Y.toDouble(), ScreenBand.BOTTOM, bitmap.width, bitmap.height)
        CoordinateTap.tap(game.gestureUtils, TeamTrialsGeometry.SPLASH_TAP_X.toDouble(), y, "legend_result_splash_tap")
        return true
    }

    /** The counter is read on a few frames: an event can raise the daily tickets, so the count is read, never assumed. */
    override fun legendCard(): LegendCardStatus {
        var tickets: Int? = null
        repeat(COUNTER_READS) { read ->
            val bitmap = imageUtils.getSourceBitmap()
            val anchor = LabelDailyPrograms.findImageWithBitmap(imageUtils, bitmap) ?: return LegendCardStatus(found = false, done = false, tickets = null)
            if (!ButtonDailyLegendRacesCard.check(imageUtils, sourceBitmap = bitmap)) return LegendCardStatus(found = false, done = false, tickets = null)
            if (LabelDoneForToday.findAll(imageUtils, sourceBitmap = bitmap).any { doneStripOnLegendCard(anchor, it) }) return LegendCardStatus(found = true, done = true, tickets = 0)
            tickets = readTickets(bitmap, anchor)
            if (tickets != null) return LegendCardStatus(found = true, done = false, tickets = tickets)
            if (read < COUNTER_READS - 1) game.wait(1.0)
        }
        return LegendCardStatus(found = true, done = false, tickets = null)
    }

    private fun doneStripOnLegendCard(anchor: Point, strip: Point): Boolean =
        kotlin.math.abs(strip.x - imageUtils.relX(anchor.x, LEGEND_STRIP_DX)) <= imageUtils.relWidth(LEGEND_STRIP_SLACK_X) &&
            kotlin.math.abs(strip.y - imageUtils.relY(anchor.y, LEGEND_STRIP_DY)) <= imageUtils.relHeight(LEGEND_STRIP_SLACK_Y)

    private fun readTickets(bitmap: Bitmap, anchor: Point): Int? {
        val raw =
            try {
                imageUtils.performOCROnRegion(
                    bitmap,
                    imageUtils.relX(anchor.x, LEGEND_COUNTER[0]),
                    imageUtils.relY(anchor.y, LEGEND_COUNTER[1]),
                    imageUtils.relWidth(LEGEND_COUNTER[2]),
                    imageUtils.relHeight(LEGEND_COUNTER[3]),
                    scale = 3.0,
                    debugName = "legend_race_tickets",
                )
            } catch (e: InterruptedException) {
                throw e
            } catch (_: Exception) {
                ""
            }
        val count = parseTicketCount(raw)
        MessageLog.i(TAG, "[LEGEND_RACE] Ticket counter OCR \"$raw\" -> ${count?.let { "${it.first}/${it.second}" } ?: "unreadable"}.")
        return count?.first
    }

    override fun gridPage(preferred: String?): LegendGridPage {
        val bitmap = imageUtils.getSourceBitmap()
        val region = gridRegion(bitmap) ?: return LegendGridPage(null, emptyList(), "")
        val logo = preferred?.let { LabelDailyLegendLogos[it] }?.findImageWithBitmap(imageUtils, bitmap, region = region)
        // A strip whose card is cut off at the top of the grid is left for the next page up.
        val cardTop = imageUtils.relY(region[1].toDouble(), -DailyLegendRace.STRIP_TO_CARD_DY)
        val strips = LabelStarUnlockText.findAllWithBitmap(imageUtils, bitmap, region = region).filter { it.y >= cardTop }.map { it.x.toInt() to it.y.toInt() }
        return LegendGridPage(logo?.let { it.x.toInt() to it.y.toInt() }, strips, gridKey(bitmap, region))
    }

    /** The card grid between the header and the menu bar; null when the header is not on screen. */
    private fun gridRegion(bitmap: Bitmap): IntArray? {
        val header = LabelDailyLegendRacesHeader.findImageWithBitmap(imageUtils, bitmap) ?: return null
        val top = imageUtils.relY(header.y, GRID_TOP_FROM_HEADER)
        val bottom = gameY(GRID_BOTTOM.toDouble(), ScreenBand.BOTTOM, bitmap.width, bitmap.height).toInt()
        if (bottom <= top) return null
        return intArrayOf(0, top, bitmap.width, bottom - top)
    }

    /** A coarse fingerprint of the grid, enough to tell whether a swipe moved it. */
    private fun gridKey(bitmap: Bitmap, region: IntArray): String {
        val crop = imageUtils.createSafeBitmap(bitmap, region[0], region[1], region[2], region[3], "legend grid") ?: return ""
        val small = Bitmap.createScaledBitmap(crop, 12, 16, true)
        val pixels = IntArray(12 * 16)
        small.getPixels(pixels, 0, 12, 0, 0, 12, 16)
        return pixels.joinToString(",") { (it shr 4 and 0x0F0F0F).toString(16) }
    }

    override fun scrollGrid(down: Boolean) {
        val bitmap = imageUtils.getSourceBitmap()
        val region = gridRegion(bitmap) ?: return
        val x = (bitmap.width / 2).toFloat()
        val low = (region[1] + region[3] * 3 / 4).toFloat()
        val high = low - imageUtils.relHeight(GRID_SCROLL_TRAVEL)
        val (start, end) = if (down) low to high else high to low
        OwnUiForeground.waitForGame()
        game.gestureUtils.swipe(x, start, x, end, GRID_SWIPE_MS)
        game.wait(1.0)
    }

    override fun tapGrid(point: Pair<Int, Int>) {
        game.tap(point.first.toDouble(), point.second.toDouble())
        game.wait(1.0)
    }

    override fun viewResultsLocked(): Boolean? = ButtonViewResults.checkDisabled(imageUtils)

    override fun handlePopup(): Boolean = handlePopup.invoke()

    override fun pause(seconds: Double) = game.wait(seconds)

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]DailyLegendRace"

        /** Reads the settings and runs the step; null when it is turned off. */
        fun runFromSettings(game: Game, handlePopup: () -> Boolean): String? {
            if (!SettingsHelper.getBooleanSetting("misc", "enableDailyLegendRace", false)) return null
            val legend = SettingsHelper.getStringSetting("misc", "dailyLegendRaceLegend", "First legend that can still unlock a star")
            MessageLog.i(TAG, "[LEGEND_RACE] Legend to race: $legend.")
            return DailyLegendRace(GameLegendDevice(game, handlePopup), legend) { MessageLog.i(TAG, "[LEGEND_RACE] $it") }.run()
        }
    }
}
