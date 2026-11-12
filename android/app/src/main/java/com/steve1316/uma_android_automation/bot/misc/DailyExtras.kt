package com.steve1316.uma_android_automation.bot.misc

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.OwnUiForeground
import com.steve1316.uma_android_automation.components.ButtonBack
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.ButtonClubItemRequest
import com.steve1316.uma_android_automation.components.ButtonCollectAll
import com.steve1316.uma_android_automation.components.ButtonConfirm
import com.steve1316.uma_android_automation.components.ButtonDailySales
import com.steve1316.uma_android_automation.components.ButtonDailySalesReset
import com.steve1316.uma_android_automation.components.ButtonExchange
import com.steve1316.uma_android_automation.components.ButtonHomeClub
import com.steve1316.uma_android_automation.components.ButtonHomePresents
import com.steve1316.uma_android_automation.components.ButtonHomeShop
import com.steve1316.uma_android_automation.components.ButtonHomeSpecialMissions
import com.steve1316.uma_android_automation.components.ButtonItemRequestSashTab
import com.steve1316.uma_android_automation.components.ButtonItemRequestShoesTab
import com.steve1316.uma_android_automation.components.ButtonMenuBarHomeSelected
import com.steve1316.uma_android_automation.components.ButtonSelectAll
import com.steve1316.uma_android_automation.components.ButtonSpecialMissionsTabDaily
import com.steve1316.uma_android_automation.components.ButtonSpecialMissionsTabMain
import com.steve1316.uma_android_automation.components.ButtonSpecialMissionsTabSpecial
import com.steve1316.uma_android_automation.components.ButtonSpecialMissionsTabTitles
import com.steve1316.uma_android_automation.components.CheckboxShopItem
import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.DialogApplicationUpdated
import com.steve1316.uma_android_automation.components.DialogConfirmExchange
import com.steve1316.uma_android_automation.components.DialogExchangeComplete
import com.steve1316.uma_android_automation.components.DialogObjects
import com.steve1316.uma_android_automation.components.DialogRewardsCollected
import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.components.IconMonies
import com.steve1316.uma_android_automation.components.LabelClubMenu
import com.steve1316.uma_android_automation.components.LabelConfirmRequestHeader
import com.steve1316.uma_android_automation.components.LabelDailySalesCostPill
import com.steve1316.uma_android_automation.components.LabelDailySalesPrompt
import com.steve1316.uma_android_automation.components.LabelHomeShopDailyBadge
import com.steve1316.uma_android_automation.components.LabelItemRequestExpired
import com.steve1316.uma_android_automation.components.LabelItemRequestHeader
import com.steve1316.uma_android_automation.components.LabelItemRequestHeld
import com.steve1316.uma_android_automation.components.LabelItemRequestMaxDonations
import com.steve1316.uma_android_automation.components.LabelItemRequestSelectPrompt
import com.steve1316.uma_android_automation.components.LabelMissionsHeader
import com.steve1316.uma_android_automation.components.LabelPresentsHeader
import com.steve1316.uma_android_automation.components.LabelRewardsCollectedHeader
import org.opencv.core.Point
import kotlin.math.abs

/** The Club item request picker offers only these: five shoes and the Winner's Sashes. */
object ClubRequestItems {
    const val LOWEST_HELD_SASH = "Lowest held Winner's Sash"

    val SHOES: List<String> = listOf("Sprint Racing Shoes", "Mile Racing Shoes", "Medium Racing Shoes", "Long Racing Shoes", "Dirt Racing Shoes")

    val SASHES: List<String> =
        listOf(
            "February S.",
            "Takamatsunomiya Kinen",
            "Osaka Hai",
            "Oka Sho",
            "Satsuki Sho",
            "Tenno Sho (Spring)",
            "NHK Mile C.",
            "Victoria Mile",
            "Japanese Oaks",
            "Japanese Derby",
            "Yasuda Kinen",
            "Takarazuka Kinen",
            "Sprinters S.",
            "Shuka Sho",
            "Kikuka Sho",
            "Tenno Sho (Autumn)",
            "Mile Ch.",
            "Queen Elizabeth II Cup",
            "Japan Cup",
            "Champions Cup",
            "Hanshin J.F.",
            "Asahi Hai F.S.",
            "Arima Kinen",
            "Hopeful S.",
            "Japan Dirt Derby",
            "JBC Sprint",
            "JBC Classic",
            "JBC Ladies' Classic",
            "Teio Sho",
            "Tokyo Daishoten",
            "Kawasaki Kinen",
            "Zen-Nippon Junior Yushun",
            "Kashiwa Kinen",
            "M.C. Nambu Hai",
        ).map { "$it Winner's Sash" }

    fun isShoes(item: String): Boolean = normalizeItemName(item).endsWith("racingshoes")
}

/** Lower-case letters and digits only, so OCR that drops punctuation or spaces still compares equal. */
internal fun normalizeItemName(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]"), "")

private val QUEEN_ELIZABETH_II = normalizeItemName("Queen Elizabeth II Cup Winner's Sash")

/** ML Kit sometimes reads the Queen Elizabeth II Cup sash with its second "I" dropped. */
private val QUEEN_ELIZABETH_DROPPED_ROMAN = normalizeItemName("Queen Elizabeth I Cup Winner's Sash")

/**
 * Whether an OCR'd row title is [target]. Two dropped leading characters still match (a crop edge can cut a first letter),
 * but not a read that is also a front crop of three or more letters of another catalog name ("ka Sho" fits Oka, Shuka and Kikuka).
 */
internal fun matchItemName(ocr: String, target: String): Boolean {
    val read = normalizeItemName(ocr).let { if (it == QUEEN_ELIZABETH_DROPPED_ROMAN) QUEEN_ELIZABETH_II else it }
    val wanted = normalizeItemName(target)
    if (read.isEmpty() || !wanted.endsWith(read) || wanted.length - read.length > 2) return false
    if (read == wanted) return true
    return (ClubRequestItems.SHOES + ClubRequestItems.SASHES).map(::normalizeItemName).none { it != wanted && it.endsWith(read) && it.length - read.length >= 3 }
}

/** The catalog name an OCR'd row title stands for, or null. A sash the catalog lacks (a game update) also reads as null and is left out of the read. */
internal fun itemNameFromTitle(ocr: String): String? = (ClubRequestItems.SHOES + ClubRequestItems.SASHES).firstOrNull { matchItemName(ocr, it) }

internal fun isSashName(item: String): Boolean = normalizeItemName(item).endsWith("winnerssash")

/** The "Held" count of a picker row: its digits, ignoring thousands separators, or null when no plausible number was read. */
internal fun parseHeldCount(ocr: String): Int? {
    val digits = ocr.filter(Char::isDigit)
    return if (digits.length in 1..6) digits.toInt() else null
}

/** One row of the item picker as read. */
internal data class PickerRow(val name: String, val held: Int?)

/** A full read of one picker tab; [complete] only when every catalog sash was read with a consistent count. [conflicts] holds the first count and the largest later count of each sash read with different ones. */
internal data class PickerScan(val rows: List<PickerRow>, val complete: Boolean, val endReached: Boolean = complete, val conflicts: Map<String, Pair<Int, Int>> = emptyMap())

/** The title and "Held" text of each row in view on one screenful, top to bottom. */
internal typealias PickerPage = List<Pair<String, String>>

/**
 * Merges screenfuls into one read. The end is two pages with nothing new. Complete means every catalog sash was read
 * with a count and no sash was read twice with different counts. An unreadable title or a page without overlap does not
 * matter once the whole catalog is in hand: a row that misreads on one screenful is read right on the next.
 */
internal fun mergePickerPages(pages: List<PickerPage>): PickerScan {
    val rows = mutableListOf<PickerRow>()
    val conflicts = linkedMapOf<String, Pair<Int, Int>>()
    var stalePages = 0
    for (page in pages) {
        var newRows = 0
        for ((title, heldText) in page) {
            val name = itemNameFromTitle(title) ?: continue
            val held = parseHeldCount(heldText)
            val seen = rows.indexOfFirst { it.name == name }
            when {
                seen < 0 -> {
                    rows += PickerRow(name, held)
                    newRows++
                }
                rows[seen].held == null -> rows[seen] = PickerRow(name, held)
                held != null && held != rows[seen].held -> conflicts[name] = rows[seen].held!! to maxOf(conflicts[name]?.second ?: held, held)
            }
        }
        stalePages = if (newRows == 0) stalePages + 1 else 0
    }
    return PickerScan(rows, complete = catalogRead(rows) && conflicts.isEmpty(), endReached = stalePages >= 2, conflicts = conflicts)
}

private fun catalogRead(rows: List<PickerRow>): Boolean = rows.map { it.name }.toSet() == ClubRequestItems.SASHES.toSet() && rows.all { it.held != null }

/** One debug line per screenful: its rows with counts, how many rows it shares with the page before, and any title that is no known item. */
internal fun pickerPageSummary(page: PickerPage, previous: PickerPage?): String {
    val names = page.map { (title, _) -> itemNameFromTitle(title) }
    val rows = page.zip(names).joinToString { (row, name) -> "${name?.removeSuffix(" Winner's Sash") ?: "?"}=${parseHeldCount(row.second) ?: "?"}" }
    val before = previous?.mapNotNull { (title, _) -> itemNameFromTitle(title) }.orEmpty().toSet()
    val unknown = page.zip(names).filter { it.second == null }.map { "\"${it.first.first}\"" }
    return "rows [$rows]" + (if (previous == null) "" else ", overlap ${names.count { it != null && it in before }}") + if (unknown.isEmpty()) "" else ", unknown ${unknown.joinToString()}"
}

/**
 * The sash with the fewest held, or null unless every row of a complete scan is a sash with a count.
 * A shoe row means the Sash tab tap missed. Ties keep the list's order.
 */
internal fun lowestHeldItem(scan: PickerScan): String? {
    if (!scan.complete || scan.rows.isEmpty() || scan.rows.any { it.held == null || !isSashName(it.name) }) return null
    return scan.rows.minByOrNull { it.held!! }?.name
}

/** Adds the readable counts of [items] on [page] to [found], keeping the larger when a row is seen twice. */
internal fun noteRereads(found: MutableMap<String, Int>, page: PickerPage, items: List<String>) {
    for ((title, heldText) in page) {
        val name = itemNameFromTitle(title)?.takeIf { it in items } ?: continue
        parseHeldCount(heldText)?.let { found.merge(name, it, ::maxOf) }
    }
}

/**
 * Reads each sash that showed different counts once more and keeps the largest of its reads: a count read too high can only
 * skip the sash, so the largest read never makes it the fewest. Null unless the rest of the catalog was read.
 */
private fun settleConflicts(scan: PickerScan, reread: (List<String>) -> Map<String, Int?>, lines: MutableList<String>): PickerScan? {
    if (!catalogRead(scan.rows)) return null
    val again = reread(scan.conflicts.keys.toList())
    val rows =
        scan.rows.map { row ->
            val (a, b) = scan.conflicts[row.name] ?: return@map row
            val third = again[row.name]
            val largest = maxOf(a, b, third ?: 0)
            lines += "Winner's Sash ${row.name.removeSuffix(" Winner's Sash")} was read $a and $b on different pages and ${third ?: "?"} the third time, the largest ($largest) is used."
            row.copy(held = largest)
        }
    return scan.copy(rows = rows, complete = true, conflicts = emptyMap())
}

/**
 * The sash to request, and the log lines of the counts the second reads changed. A sash with two disagreeing counts is settled
 * first, then the two lowest counts are read again; a count that differs the second time becomes the larger of the two, since a
 * misread only drops a digit.
 */
internal fun lowestAfterReread(scan: PickerScan, reread: (List<String>) -> Map<String, Int?>): Pair<String, List<String>>? {
    val lines = mutableListOf<String>()
    val settled = if (scan.conflicts.isEmpty()) scan else settleConflicts(scan, reread, lines) ?: return null
    val first = lowestHeldItem(settled) ?: return null
    val held = settled.rows.associate { it.name to it.held!! }
    val lowest = settled.rows.sortedBy { it.held!! }.take(2).map { it.name }
    val again = reread(lowest)
    val changed = lowest.filter { name -> again[name]?.let { it != held[name] } == true }
    if (changed.isEmpty()) return first to lines
    val fixed = settled.rows.map { if (it.name in changed) it.copy(held = maxOf(it.held!!, again.getValue(it.name)!!)) else it }
    lines += "Winner's Sash counts read differently the second time, the larger one is used: " + changed.joinToString { "${it.removeSuffix(" Winner's Sash")} ${held[it]} then ${again[it]}" } + "."
    return (lowestHeldItem(settled.copy(rows = fixed)) ?: first) to lines
}

/** Digits of a Monies amount, ignoring thousands separators, or null when no plausible number was read. */
internal fun parseMonies(ocr: String): Int? {
    val digits = ocr.filter(Char::isDigit)
    return if (digits.length in 1..9) digits.toInt() else null
}

/** One Daily Sales row as read: its title (any "[epithet]" line, then the name line), whether its price shows the Monies coin, the price, and whether it has a checkbox (a sold-out row has none). */
internal data class SaleRow(val title: String, val monies: Boolean, val cost: Int?, val buyable: Boolean = true)

/** The rows of the Daily Sales list. [complete] once the scroll bar reached its end; [gap] and [reason] when the pages could not be placed in the list. */
internal data class SaleScan(val rows: List<SaleRow>, val complete: Boolean, val gap: Boolean = false, val reason: String? = null) {
    val buyable: List<SaleRow> get() = rows.filter { it.buyable }
}

/** The list's scroll bar thumb: its top within the track, its length, and whether it touches the track's end. */
internal data class SaleScroll(val thumbTop: Int, val thumbLength: Int, val atEnd: Boolean)

/** One screenful: its rows top to bottom, each pill's y measured from where the first row's pill sits with the list at its top, and the scroll bar (null when none was found). */
internal data class SalePage(val rows: List<SaleRow>, val pillYs: List<Int>, val scroll: SaleScroll?)

/** Daily Sales rows are 240 px apart; a page whose pills land more than 60 px off the row grid the scroll bar predicts is refused. */
internal const val SALE_ROW_PITCH = 240
internal const val SALE_ROW_SLACK = 60

/** A thumb length of scroll bar stands for 709 px of list (measured on 7- and 21-row lists at 1080x1920). */
internal const val SALE_LIST_VIEW_H = 709

/** At the list's end the last row's pill sits 406 px below where the first row's pill sits at the top (every list measured), and the thumb ends within 3 px of the track's end. */
internal const val SALE_LAST_PILL_AT_END = 406
internal const val SALE_THUMB_END_SLACK = 3

internal fun saleRowText(row: SaleRow): String = "${row.title.replace('\n', ' ')}=${row.cost ?: "?"}" + (if (row.monies) "" else " (not Monies)") + (if (row.buyable) "" else " (sold out)")

/** OCR reads a row's "[epithet]" unreliably (dropped, "Lunsigned]", "Winning!l") and breaks the title into lines differently, so rows compare by the name after it. */
private fun saleKey(row: SaleRow): Pair<String, Int?> {
    val flat = row.title.replace('\n', ' ')
    return normalizeItemName(if (']' in flat) flat.substringAfterLast(']') else row.title.substringAfterLast('\n')) to row.cost
}

/** The thumb of a scroll bar track read top to bottom ([thumb] true where a pixel is the thumb's colour), or null unless it is one run. */
internal fun saleScroll(thumb: List<Boolean>, endSlack: Int = SALE_THUMB_END_SLACK): SaleScroll? {
    val top = thumb.indexOf(true)
    val end = thumb.lastIndexOf(true)
    if (top < 0 || (top..end).any { !thumb[it] }) return null
    return SaleScroll(top, end - top + 1, atEnd = end >= thumb.size - 1 - endSlack)
}

/**
 * Places every row of the pages read top to bottom at its index in the list: the scroll bar says how far each swipe moved the list,
 * and the pills snap that onto the row grid. Content alone cannot place rows, because a list repeats identical rows.
 */
internal fun mergeSalePages(
    pages: List<SalePage>,
    pitch: Int = SALE_ROW_PITCH,
    slack: Int = SALE_ROW_SLACK,
    viewHeight: Int = SALE_LIST_VIEW_H,
    lastPillAtEnd: Int = SALE_LAST_PILL_AT_END,
): SaleScan {
    val rows = sortedMapOf<Int, SaleRow>()

    fun refused(reason: String) = SaleScan(rows.values.toList(), complete = false, gap = true, reason = reason)
    var offset = 0
    var before: SaleScroll? = null
    for ((i, page) in pages.withIndex()) {
        val scroll = page.scroll ?: return refused("page ${i + 1} shows no scroll bar")
        val last = before
        if (last != null && scroll.thumbTop == last.thumbTop && !scroll.atEnd) return refused("a swipe did not move the list")
        val estimate = if (last == null) scroll.thumbTop * viewHeight / scroll.thumbLength else offset + (scroll.thumbTop - last.thumbTop) * viewHeight / scroll.thumbLength
        before = scroll
        if (page.pillYs.isEmpty()) return refused("page ${i + 1} shows no row")
        val first = page.pillYs.first() + estimate
        val off = first - Math.floorDiv(first + pitch / 2, pitch) * pitch
        if (abs(off) > slack) return refused("page ${i + 1} is $off px off the row grid")
        offset = estimate - off
        for ((row, y) in page.rows.zip(page.pillYs)) {
            val position = y + offset
            val index = Math.floorDiv(position + pitch / 2, pitch)
            if (index < 0 || abs(position - index * pitch) > slack) return refused("page ${i + 1} is off the row grid")
            val known = rows[index]
            if (known == null) {
                rows[index] = row
            } else if (saleKey(known) != saleKey(row)) {
                return refused("row ${index + 1} reads \"${saleRowText(known)}\" and \"${saleRowText(row)}\"")
            }
        }
    }
    if (rows.isEmpty()) return SaleScan(emptyList(), complete = false)
    val missing = (0..rows.lastKey()).firstOrNull { it !in rows }
    if (missing != null) return refused("row ${missing + 1} was not read")
    val tail = pages.last()
    val complete = tail.scroll?.atEnd == true
    if (complete && abs(tail.pillYs.last() - lastPillAtEnd) > slack) return refused("the last row of the list was not read")
    return SaleScan(rows.values.toList(), complete = complete)
}

/** Why the read list must not be bought, or null when it can be paid for in Monies. Sold-out rows are checked too: a missed checkbox must not hide another currency. */
internal fun saleRowsProblem(scan: SaleScan): String? =
    when {
        !scan.complete -> "the list was not read to its end" + (scan.reason?.let { ": $it" } ?: "")
        scan.rows.isEmpty() -> "no row was read"
        scan.rows.any { it.title.isBlank() || it.cost == null } -> "a row could not be read"
        scan.rows.any { !it.monies } -> "a row is not priced in Monies"
        scan.buyable.isEmpty() -> "every row is sold out"
        else -> null
    }

/** Why the selection must not be confirmed, or null when the cost bar's [total] is the sum of the buyable [rows] and the held [balance] covers it. */
internal fun saleTotalProblem(rows: List<SaleRow>, total: Int?, balance: Int?): String? {
    val sum = rows.sumOf { it.cost ?: 0 }
    return when {
        total == null || balance == null -> "the cost bar could not be read"
        total != sum -> "the cost bar shows $total, the rows add up to $sum"
        total <= 0 -> "the selection costs nothing"
        total > balance -> "it costs $total Monies and $balance are held"
        else -> null
    }
}

internal enum class ExtrasScreen {
    HOME,
    CLUB,
    ITEM_REQUEST_ENDED,
    ITEM_REQUEST_PICKER,

    /** The Item Request dialog with neither an ended result nor the picker: a request still running. */
    ITEM_REQUEST_OTHER,
    CONFIRM_REQUEST,
    PRESENTS,
    REWARDS_COLLECTED,
    MISSIONS,
    SPENDING_DIALOG,
    SHOP_MENU,
    DAILY_SALES,

    /** The Daily Sales purchase dialog: accepted only by the Daily Sales part, cancelled like a spending dialog everywhere else. */
    CONFIRM_EXCHANGE,
    EXCHANGE_COMPLETE,

    /** The game's forced update prompt: the step stops on it, tapping nothing. */
    GAME_UPDATE_PROMPT,
    UNKNOWN,
}

internal enum class ExtrasTarget {
    CLUB_BUTTON,
    ITEM_REQUEST_BUTTON,
    MISSIONS_BUTTON,
    PRESENTS_BUTTON,
    MISSIONS_TAB_DAILY,
    MISSIONS_TAB_MAIN,
    MISSIONS_TAB_TITLES,
    MISSIONS_TAB_SPECIAL,
    COLLECT_ALL,
    CLOSE,
    CANCEL,
    CONFIRM,
    BACK,
    HOME_TAB,
    SHOP_BUTTON,
    DAILY_SALES_BANNER,
    SELECT_ALL,
    RESET,
    EXCHANGE,
}

/** The game side of [DailyExtras]. One implementation drives the game; the tests drive a scripted fake. */
internal interface ExtrasDevice {
    fun screen(): ExtrasScreen

    /** Taps [target]; false when it is not on screen. */
    fun press(target: ExtrasTarget): Boolean

    /** Opens the Shoes or Winner's Sash tab of the picker. */
    fun openPickerTab(shoes: Boolean)

    fun scanPicker(): PickerScan

    /** The "Held" count of each named sash read again from fresh screenshots; the larger of its sightings, and a sash with no readable count is absent. */
    fun rereadHeld(items: List<String>): Map<String, Int?>

    /** Taps the picker row of [item]; false when no row reads as it. */
    fun tapPickerRow(item: String): Boolean

    /** The item name the Confirm Request dialog shows, or null when unreadable. */
    fun confirmRequestItem(): String?

    /** False when Collect All is greyed out, null when that cannot be told. */
    fun collectAllEnabled(): Boolean?

    /** Whether Home's Shop button shows the "Daily" badge; null while the button is not drawn. */
    fun shopBadge(): Boolean?

    /** Every buyable row of the Daily Sales list, read from where it opened down to its end. */
    fun readSaleRows(): SaleScan

    /** The cost bar under the Daily Sales list: the selection's total and the Monies held. */
    fun costBar(): Pair<Int?, Int?>

    fun pause(seconds: Double)
}

/**
 * Collects Daily Missions and the present box, makes the Club item request and buys the Daily Sales, from Home and back to Home.
 * It never donates and confirms only the item request and a Daily Sales purchase paid in Monies; any other spending dialog is cancelled.
 */
internal class DailyExtras(
    private val device: ExtrasDevice,
    private val collectRewards: Boolean,
    private val clubRequest: Boolean,
    private val requestItem: String,
    private val buyDailySales: Boolean = false,
    private val log: (String) -> Unit = {},
) {
    companion object {
        /** Polls of about a second each before a screen counts as missing. */
        const val SCREEN_POLLS = 15

        /** A Collect All that pays nothing opens no popup; this many polls tell the two apart. */
        const val REWARD_POLLS = 5

        /** Presents collects at most 100 gifts per tap. */
        const val MAX_PRESENT_COLLECTS = 3

        /** Home shows before its buttons finish drawing; polls of about a second for a Home button. */
        const val HOME_BUTTON_POLLS = 6

        private val MISSION_TABS =
            listOf(
                "Daily" to ExtrasTarget.MISSIONS_TAB_DAILY,
                "Main" to ExtrasTarget.MISSIONS_TAB_MAIN,
                "Titles" to ExtrasTarget.MISSIONS_TAB_TITLES,
                "Special" to ExtrasTarget.MISSIONS_TAB_SPECIAL,
            )

        private val SPENDING_SCREENS = setOf(ExtrasScreen.SPENDING_DIALOG, ExtrasScreen.CONFIRM_EXCHANGE)
    }

    private var spendingDialogsCancelled = 0

    /** Runs the enabled parts and returns one note per part. */
    fun run(): List<String> {
        if (awaitScreen(ExtrasScreen.HOME, polls = 3) != ExtrasScreen.HOME) return listOf("not on Home, skipped")
        // Best-proven screens first, so a part that strands the step costs the fewest others.
        val parts = mutableListOf<Pair<String, () -> String>>()
        if (clubRequest) parts += "Club" to ::club
        if (collectRewards) {
            parts += "Presents" to ::presents
            parts += "Missions" to ::missions
        }
        if (buyDailySales) parts += "Daily Sales" to ::dailySales
        val notes = mutableListOf<String>()
        for ((name, body) in parts) {
            val note = body()
            log("$name: $note")
            // Every part starts from Home; off Home the step stops rather than tap on a screen it does not know.
            if (!backToHome()) {
                notes += "$name: $note; could not get back to Home, stopped"
                break
            }
            notes += "$name: $note"
        }
        if (spendingDialogsCancelled > 0) notes += "cancelled $spendingDialogsCancelled spending dialog(s)"
        return notes
    }

    /** Collect All covers the visible tab only: collects the opened tab, then each tab by its template. */
    private fun missions(): String {
        if (!pressOnHome(ExtrasTarget.MISSIONS_BUTTON)) return "Missions button not found"
        if (awaitScreen(ExtrasScreen.MISSIONS) != ExtrasScreen.MISSIONS) return "Missions screen did not appear"
        val results = mutableListOf("the opened tab" to collect(ExtrasScreen.MISSIONS))
        // A tab template is the unselected look, so the selected tab misses it: a second pass reaches it once another tab is selected.
        var pending = MISSION_TABS
        repeat(2) {
            val missed = mutableListOf<Pair<String, ExtrasTarget>>()
            for ((name, target) in pending) {
                if (device.press(target)) results += name to collect(ExtrasScreen.MISSIONS) else missed += name to target
            }
            pending = missed
        }
        device.press(ExtrasTarget.BACK)
        val collected = results.filter { it.second == "collected" }.map { it.first }
        val problems = results.filter { it.second != "collected" && it.second != "nothing to collect" }.map { "${it.first}: ${it.second}" }
        val notFound = pending.map { "${it.first} tab not found" }
        val parts = listOfNotNull(if (collected.isEmpty()) null else "collected on ${collected.joinToString()}") + problems + notFound
        return if (parts.isEmpty()) "nothing to collect" else parts.joinToString("; ")
    }

    private fun presents(): String {
        if (!pressOnHome(ExtrasTarget.PRESENTS_BUTTON)) return "Presents button not found"
        if (awaitScreen(ExtrasScreen.PRESENTS) != ExtrasScreen.PRESENTS) return "Presents did not open"
        var note = "nothing to collect"
        for (round in 1..MAX_PRESENT_COLLECTS) {
            val result = collect(ExtrasScreen.PRESENTS)
            if (result != "collected") {
                if (round == 1) note = result
                break
            }
            note = "collected"
        }
        device.press(ExtrasTarget.CLOSE)
        return note
    }

    /** Taps Collect All on [home] and closes the Rewards Collected popup. */
    private fun collect(home: ExtrasScreen): String {
        if (device.collectAllEnabled() == false) return "nothing to collect"
        if (!device.press(ExtrasTarget.COLLECT_ALL)) return "Collect All not found"
        if (awaitScreen(ExtrasScreen.REWARDS_COLLECTED, polls = REWARD_POLLS) != ExtrasScreen.REWARDS_COLLECTED) return "nothing to collect"
        device.press(ExtrasTarget.CLOSE)
        return if (awaitScreen(home) == home) "collected" else "collected, then lost the screen"
    }

    private fun club(): String {
        if (!pressOnHome(ExtrasTarget.CLUB_BUTTON)) return "Club button not found"
        if (awaitScreen(ExtrasScreen.CLUB, polls = 20) != ExtrasScreen.CLUB) return "Club screen did not appear"
        // A finished request shows its result first; closing it frees the slot, then the button opens the picker.
        // An unrecognised request may be an expired one whose label was missed: closing collects it, so reopen once.
        var ended = false
        var reopened = false
        repeat(3) {
            if (!device.press(ExtrasTarget.ITEM_REQUEST_BUTTON)) return "Item Request button not found"
            when (awaitAny(ExtrasScreen.ITEM_REQUEST_ENDED, ExtrasScreen.ITEM_REQUEST_PICKER, ExtrasScreen.ITEM_REQUEST_OTHER)) {
                ExtrasScreen.ITEM_REQUEST_ENDED -> {
                    log("The last item request has ended; closing its result.")
                    device.press(ExtrasTarget.CLOSE)
                    if (awaitScreen(ExtrasScreen.CLUB) != ExtrasScreen.CLUB) return "Club screen did not come back after the ended request"
                    if (ended) return "no item picker after closing the ended request"
                    ended = true
                }

                ExtrasScreen.ITEM_REQUEST_PICKER -> return request()

                ExtrasScreen.ITEM_REQUEST_OTHER -> {
                    device.press(ExtrasTarget.CLOSE)
                    if (reopened || awaitScreen(ExtrasScreen.CLUB) != ExtrasScreen.CLUB) return "a request is still running"
                    reopened = true
                }

                else -> return "Item Request did not open"
            }
        }
        return "no item picker after closing the ended request"
    }

    /** Picks the item and confirms only when the Confirm Request dialog names that item. */
    private fun request(): String {
        val item =
            if (requestItem == ClubRequestItems.LOWEST_HELD_SASH) {
                device.openPickerTab(shoes = false)
                val scan = device.scanPicker()
                log("Winner's Sash held counts: " + scan.rows.joinToString { "${it.name}=${it.held ?: "?"}" } + if (scan.complete) "" else if (scan.conflicts.isEmpty()) " (incomplete, ${scan.rows.size} of ${ClubRequestItems.SASHES.size} read)" else " (${scan.rows.size} of ${ClubRequestItems.SASHES.size} read, ${scan.conflicts.size} read with different counts)")
                val (lowest, lines) = lowestAfterReread(scan, device::rereadHeld) ?: return cancelPicker("the Winner's Sash list was not fully read")
                lines.forEach(log)
                lowest
            } else {
                device.openPickerTab(shoes = ClubRequestItems.isShoes(requestItem))
                requestItem
            }
        if (!device.tapPickerRow(item)) return cancelPicker("$item is not in the picker")
        if (awaitScreen(ExtrasScreen.CONFIRM_REQUEST, polls = 5) != ExtrasScreen.CONFIRM_REQUEST) return cancelPicker("Confirm Request did not open for $item")
        val shown = device.confirmRequestItem()
        if (shown == null || !matchItemName(shown, item)) {
            device.press(ExtrasTarget.CANCEL)
            return cancelPicker("Confirm Request showed \"${shown ?: "?"}\" instead of $item")
        }
        device.press(ExtrasTarget.CONFIRM)
        return if (awaitScreen(ExtrasScreen.CLUB) == ExtrasScreen.CLUB) "requested $item" else "confirmed $item, then lost the screen"
    }

    /** Buys the whole Daily Sales list when Home's Shop shows the "Daily" badge, only when every row is priced in Monies and the cost bar agrees. */
    private fun dailySales(): String {
        val badge = awaitShopBadge() ?: return "Shop button not found"
        if (!badge) return "no Daily badge on the Shop"
        if (!device.press(ExtrasTarget.SHOP_BUTTON)) return "Shop button not found"
        if (awaitScreen(ExtrasScreen.SHOP_MENU) != ExtrasScreen.SHOP_MENU) return "the Shop did not open"
        if (!device.press(ExtrasTarget.DAILY_SALES_BANNER) || awaitScreen(ExtrasScreen.DAILY_SALES) != ExtrasScreen.DAILY_SALES) return "Daily Sales did not open"
        // Read before Select All, so a list that fails the checks is never selected.
        val scan = device.readSaleRows()
        log("Daily Sales rows: " + scan.rows.joinToString(transform = ::saleRowText))
        saleRowsProblem(scan)?.let { return "nothing bought: $it" }
        if (!device.press(ExtrasTarget.SELECT_ALL)) return "nothing bought: Select All not found"
        val (total, balance) = device.costBar()
        saleTotalProblem(scan.buyable, total, balance)?.let {
            device.press(ExtrasTarget.RESET)
            return "nothing bought: $it"
        }
        if (!device.press(ExtrasTarget.CONFIRM)) {
            device.press(ExtrasTarget.RESET)
            return "nothing bought: Confirm not found"
        }
        if (awaitScreen(ExtrasScreen.CONFIRM_EXCHANGE, polls = 5) != ExtrasScreen.CONFIRM_EXCHANGE) return "nothing bought: no Confirm Exchange dialog after Confirm"
        if (!device.press(ExtrasTarget.EXCHANGE)) return "nothing bought: Exchange not found"
        if (awaitScreen(ExtrasScreen.EXCHANGE_COMPLETE) != ExtrasScreen.EXCHANGE_COMPLETE) return "Exchange pressed for $total Monies, but no Exchange Complete was seen"
        device.press(ExtrasTarget.CLOSE)
        return "bought ${scan.buyable.size} item(s) for $total Monies"
    }

    /** Home draws its buttons late: waits for the Shop button, then reads its badge. */
    private fun awaitShopBadge(): Boolean? {
        repeat(HOME_BUTTON_POLLS) {
            device.shopBadge()?.let { return it }
            device.pause(1.0)
        }
        return null
    }

    private fun cancelPicker(reason: String): String {
        if (awaitScreen(ExtrasScreen.ITEM_REQUEST_PICKER, polls = 3) == ExtrasScreen.ITEM_REQUEST_PICKER) device.press(ExtrasTarget.CANCEL)
        return "no request: $reason"
    }

    /** Leaves any screen of this step for Home: closes dialogs with Close or Cancel, then Back or the Home tab. */
    private fun backToHome(): Boolean {
        repeat(6) {
            when (awaitAny(*ExtrasScreen.entries.filter { it != ExtrasScreen.UNKNOWN }.toTypedArray(), polls = 3)) {
                ExtrasScreen.HOME -> return true
                ExtrasScreen.CLUB -> device.press(ExtrasTarget.HOME_TAB)
                ExtrasScreen.MISSIONS, ExtrasScreen.SHOP_MENU, ExtrasScreen.DAILY_SALES -> device.press(ExtrasTarget.BACK)
                ExtrasScreen.CONFIRM_REQUEST, ExtrasScreen.ITEM_REQUEST_PICKER, ExtrasScreen.SPENDING_DIALOG, ExtrasScreen.CONFIRM_EXCHANGE -> device.press(ExtrasTarget.CANCEL)
                // Close, Cancel and Back never accept anything; an unknown screen gets nothing else.
                else -> device.press(ExtrasTarget.CLOSE) || device.press(ExtrasTarget.CANCEL) || device.press(ExtrasTarget.BACK)
            }
        }
        return false
    }

    private fun pressOnHome(target: ExtrasTarget): Boolean {
        repeat(HOME_BUTTON_POLLS) {
            if (device.press(target)) return true
            device.pause(1.0)
        }
        return false
    }

    private fun awaitScreen(screen: ExtrasScreen, polls: Int = SCREEN_POLLS): ExtrasScreen? = awaitAny(screen, polls = polls)

    /** Polls until one of [screens] shows, cancelling spending dialogs on the way; null when none showed. */
    private fun awaitAny(vararg screens: ExtrasScreen, polls: Int = SCREEN_POLLS): ExtrasScreen? {
        repeat(polls) {
            val now = device.screen()
            if (now == ExtrasScreen.GAME_UPDATE_PROMPT) DialogUtils.stopForGameUpdate()
            if (now in SPENDING_SCREENS && now !in screens) {
                spendingDialogsCancelled++
                log("A spending dialog appeared; cancelling it.")
                if (!device.press(ExtrasTarget.CANCEL)) device.press(ExtrasTarget.CLOSE)
            } else if (now in screens) {
                return now
            }
            device.pause(1.0)
        }
        return null
    }
}

/** Measured on 1080x1920 picker frames, from the centre of a row's "Held" pill. */
private const val ROW_TITLE_DX = -90
private const val ROW_TITLE_DY = -79
private const val ROW_TITLE_W = 720
private const val ROW_TITLE_H = 45
private const val ROW_HELD_DX = 115
private const val ROW_HELD_DY = -26
private const val ROW_HELD_W = 170
private const val ROW_HELD_H = 52
private const val ROW_TAP_DX = 130
private const val ROW_TAP_DY = -57

/** From the centre of the "Confirm Request" title: the item name line of its card. */
private const val CONFIRM_ITEM_DX = -296
private const val CONFIRM_ITEM_DY = 117
private const val CONFIRM_ITEM_W = 760
private const val CONFIRM_ITEM_H = 50

/** The list shows rows between the tabs and the sort buttons; from the "Select an item" prompt, pills above this offset have their title in view. */
private const val LIST_TOP_FROM_PROMPT = -1330
private const val LIST_BOTTOM_FROM_PROMPT = -140

/** About three 214 px rows per slow swipe, so pages overlap even when the list coasts (LIVE frames showed a fast swipe skip a row). */
private const val SWIPE_FROM_PROMPT = -400
private const val SWIPE_TO_PROMPT = -1040
private const val SWIPE_MS = 1200L
private const val MAX_PICKER_PAGES = 20

/** Measured on 1080x1920 Daily Sales frames, rows 240 px apart: from the "Select an item" prompt, a "Cost" pill in this band has its title and price in view. */
private const val SALE_ROWS_TOP_FROM_PROMPT = -604
private const val SALE_ROWS_BOTTOM_FROM_PROMPT = -73

/** From the prompt: the first row's pill with the list at its top, and the scroll bar's track column (thumb grey under 170, track over it). */
private const val SALE_FIRST_PILL_FROM_PROMPT = -574
private const val SALE_TRACK_DX = 512
private const val SALE_TRACK_TOP_FROM_PROMPT = -699
private const val SALE_TRACK_BOTTOM_FROM_PROMPT = -74
private const val SALE_THUMB_MAX_GREY = 170
private const val SALE_SWIPE_FROM = -250
private const val SALE_SWIPE_TO = -490
private const val MAX_SALE_PAGES = 30

/** From a row's "Cost" pill centre: the price coin, the checkbox, the price digits and the one- or two-line title. */
private const val SALE_COIN_DX = 36
private const val SALE_COIN_DY = -41
private const val SALE_COIN_SIZE = 80
private const val SALE_BOX_DX = 574
private const val SALE_BOX_DY = -85
private const val SALE_BOX_SIZE = 120
private const val SALE_COST_DX = 122
private const val SALE_COST_DY = -27
private const val SALE_COST_W = 160
private const val SALE_COST_H = 52
private const val SALE_TITLE_DX = -58
private const val SALE_TITLE_DY = -125
private const val SALE_TITLE_W = 575
private const val SALE_TITLE_H = 88

/** From the prompt's centre: the selection total and the Monies held in the cost bar. */
private const val COST_BAR_DY = 49
private const val COST_BAR_H = 50
private const val COST_BAR_W = 300
private const val COST_BAR_TOTAL_DX = -305
private const val COST_BAR_HELD_DX = 210

/** From the Home Shop button's centre: the band its "Daily" badge sits in. */
private const val SHOP_BADGE_HALF_W = 90
private const val SHOP_BADGE_TOP = -130
private const val SHOP_BADGE_H = 100

/** Dialogs that would spend RP, money or items; the Home steps cancel them. */
internal val SPENDING_DIALOG_NAMES =
    setOf("confirm_exchange", "confirm_restore_rp", "daily_sale", "purchase_alarm_clock", "purchase_carats", "purchase_daily_race_ticket", "shop")

/** What a dialog title stands for: the Rewards Collected popup (when its header template missed), a Daily Sales purchase dialog, a spending dialog or the forced update prompt. */
internal fun extrasDialogScreen(title: String?): ExtrasScreen? =
    when {
        title == null -> null
        title == DialogApplicationUpdated.title -> ExtrasScreen.GAME_UPDATE_PROMPT
        title == DialogRewardsCollected.title -> ExtrasScreen.REWARDS_COLLECTED
        title == DialogConfirmExchange.title -> ExtrasScreen.CONFIRM_EXCHANGE
        title == DialogExchangeComplete.title -> ExtrasScreen.EXCHANGE_COMPLETE
        DialogObjects.items.any { it.title == title && it.name in SPENDING_DIALOG_NAMES } -> ExtrasScreen.SPENDING_DIALOG
        else -> null
    }

/** Drives [DailyExtras] on the game. Templates and offsets come from 1080x1920 Global frames. */
internal class GameExtrasDevice(private val game: Game) : ExtrasDevice {
    private val imageUtils get() = game.imageUtils

    override fun screen(): ExtrasScreen {
        val bitmap = imageUtils.getSourceBitmap()
        return when {
            bitmap.shows(LabelConfirmRequestHeader) -> ExtrasScreen.CONFIRM_REQUEST
            bitmap.shows(LabelRewardsCollectedHeader) -> ExtrasScreen.REWARDS_COLLECTED
            bitmap.shows(LabelItemRequestExpired) || bitmap.shows(LabelItemRequestMaxDonations) -> ExtrasScreen.ITEM_REQUEST_ENDED
            bitmap.shows(LabelItemRequestSelectPrompt) -> ExtrasScreen.ITEM_REQUEST_PICKER
            bitmap.shows(LabelItemRequestHeader) -> ExtrasScreen.ITEM_REQUEST_OTHER
            bitmap.shows(LabelPresentsHeader) -> ExtrasScreen.PRESENTS
            bitmap.shows(LabelMissionsHeader) -> ExtrasScreen.MISSIONS
            bitmap.shows(LabelClubMenu) -> ExtrasScreen.CLUB
            bitmap.shows(LabelDailySalesPrompt) -> ExtrasScreen.DAILY_SALES
            bitmap.shows(ButtonDailySales) -> ExtrasScreen.SHOP_MENU
            ButtonHomePresents.check(imageUtils, sourceBitmap = bitmap, confidence = 0.9) && bitmap.shows(ButtonMenuBarHomeSelected) -> ExtrasScreen.HOME
            else -> dialogScreen(bitmap) ?: ExtrasScreen.UNKNOWN
        }
    }

    private fun Bitmap.shows(component: ComponentInterface): Boolean = component.check(imageUtils, sourceBitmap = this)

    /** Reads the dialog title quietly: most unknown frames are transitions, not dialogs. */
    private fun dialogScreen(bitmap: Bitmap): ExtrasScreen? = extrasDialogScreen(DialogUtils.getTitle(imageUtils, bitmap, logOnMiss = false))

    override fun press(target: ExtrasTarget): Boolean {
        val clicked =
            when (target) {
                ExtrasTarget.CLUB_BUTTON -> ButtonHomeClub.click(imageUtils)
                ExtrasTarget.ITEM_REQUEST_BUTTON -> ButtonClubItemRequest.click(imageUtils)
                ExtrasTarget.MISSIONS_BUTTON -> ButtonHomeSpecialMissions.click(imageUtils, confidence = 0.9)
                ExtrasTarget.PRESENTS_BUTTON -> ButtonHomePresents.click(imageUtils, confidence = 0.9)
                ExtrasTarget.MISSIONS_TAB_DAILY -> ButtonSpecialMissionsTabDaily.click(imageUtils)
                ExtrasTarget.MISSIONS_TAB_MAIN -> ButtonSpecialMissionsTabMain.click(imageUtils)
                ExtrasTarget.MISSIONS_TAB_TITLES -> ButtonSpecialMissionsTabTitles.click(imageUtils)
                ExtrasTarget.MISSIONS_TAB_SPECIAL -> ButtonSpecialMissionsTabSpecial.click(imageUtils)
                ExtrasTarget.COLLECT_ALL -> ButtonCollectAll.click(imageUtils)
                ExtrasTarget.CLOSE -> ButtonClose.click(imageUtils)
                ExtrasTarget.CANCEL -> ButtonCancel.click(imageUtils)
                ExtrasTarget.CONFIRM -> ButtonConfirm.click(imageUtils)
                ExtrasTarget.BACK -> ButtonBack.click(imageUtils)
                ExtrasTarget.HOME_TAB -> ButtonMenuBarHomeSelected.click(imageUtils)
                ExtrasTarget.SHOP_BUTTON -> ButtonHomeShop.click(imageUtils)
                ExtrasTarget.DAILY_SALES_BANNER -> ButtonDailySales.click(imageUtils)
                ExtrasTarget.SELECT_ALL -> ButtonSelectAll.click(imageUtils)
                ExtrasTarget.RESET -> ButtonDailySalesReset.click(imageUtils)
                ExtrasTarget.EXCHANGE -> ButtonExchange.click(imageUtils)
            }
        if (clicked) game.wait(1.0)
        return clicked
    }

    override fun openPickerTab(shoes: Boolean) {
        // Each tab template is the unselected look, so it matches only while the other tab is open.
        if ((if (shoes) ButtonItemRequestShoesTab else ButtonItemRequestSashTab).click(imageUtils)) game.wait(1.0)
    }

    override fun scanPicker(): PickerScan {
        scrollPickerToTop()
        val pages = mutableListOf<PickerPage>()
        while (pages.size < MAX_PICKER_PAGES) {
            val page = readVisibleRows()
            MessageLog.d(TAG, "[DAILY_EXTRAS] Picker page ${pages.size + 1}: ${pickerPageSummary(page, pages.lastOrNull())}")
            pages += page
            if (mergePickerPages(pages).endReached || !swipePicker(up = true)) break
        }
        return mergePickerPages(pages)
    }

    override fun rereadHeld(items: List<String>): Map<String, Int?> {
        scrollPickerToTop()
        val found = mutableMapOf<String, Int>()
        val pages = mutableListOf<PickerPage>()
        while (pages.size < MAX_PICKER_PAGES) {
            val page = readVisibleRows()
            pages += page
            noteRereads(found, page, items)
            if (found.keys.containsAll(items) || mergePickerPages(pages).endReached || !swipePicker(up = true)) break
        }
        return found
    }

    override fun tapPickerRow(item: String): Boolean {
        scrollPickerToTop()
        var lastTitles: List<String>? = null
        repeat(MAX_PICKER_PAGES) {
            val bitmap = imageUtils.getSourceBitmap()
            val pills = visiblePills(bitmap) ?: return false
            val titles = pills.map { readTitle(it, bitmap) }
            pills.zip(titles).firstOrNull { (_, title) -> matchItemName(title, item) }?.let { (pill, _) ->
                MessageLog.i(TAG, "[DAILY_EXTRAS] Picking $item.")
                game.tap(imageUtils.relX(pill.x, ROW_TAP_DX).toDouble(), imageUtils.relY(pill.y, ROW_TAP_DY).toDouble())
                return true
            }
            if (titles == lastTitles) return false
            lastTitles = titles
            if (!swipePicker(up = true)) return false
        }
        return false
    }

    override fun confirmRequestItem(): String? {
        val bitmap = imageUtils.getSourceBitmap()
        val header = LabelConfirmRequestHeader.findImageWithBitmap(imageUtils, bitmap) ?: return null
        // The item name sits on the first line of the card under the header.
        val text =
            imageUtils.performOCRFromReference(
                header,
                CONFIRM_ITEM_DX,
                CONFIRM_ITEM_DY,
                imageUtils.relWidth(CONFIRM_ITEM_W),
                imageUtils.relHeight(CONFIRM_ITEM_H),
                useThreshold = false,
                debugName = "club_confirm_item",
                sourceBitmap = bitmap,
            )
        return text.takeIf { it.isNotBlank() }
    }

    override fun collectAllEnabled(): Boolean? = ButtonCollectAll.checkDisabled(imageUtils)?.not()

    override fun shopBadge(): Boolean? {
        val bitmap = imageUtils.getSourceBitmap()
        val shop = ButtonHomeShop.findImageWithBitmap(imageUtils, bitmap) ?: return null
        val above = intArrayOf(imageUtils.relX(shop.x, -SHOP_BADGE_HALF_W), imageUtils.relY(shop.y, SHOP_BADGE_TOP), imageUtils.relWidth(2 * SHOP_BADGE_HALF_W), imageUtils.relHeight(SHOP_BADGE_H))
        return LabelHomeShopDailyBadge.check(imageUtils, region = above, sourceBitmap = bitmap)
    }

    override fun readSaleRows(): SaleScan {
        val pages = mutableListOf<SalePage>()

        fun merge() =
            mergeSalePages(
                pages,
                imageUtils.relHeight(SALE_ROW_PITCH),
                imageUtils.relHeight(SALE_ROW_SLACK),
                imageUtils.relHeight(SALE_LIST_VIEW_H),
                imageUtils.relHeight(SALE_LAST_PILL_AT_END),
            )
        while (pages.size < MAX_SALE_PAGES) {
            pages += readSalePage() ?: break
            val scan = merge()
            if (scan.complete || scan.gap || !swipeSales()) break
        }
        return merge()
    }

    override fun costBar(): Pair<Int?, Int?> {
        val bitmap = imageUtils.getSourceBitmap()
        val prompt = LabelDailySalesPrompt.findImageWithBitmap(imageUtils, bitmap) ?: return null to null

        fun read(dx: Int, name: String): Int? =
            parseMonies(
                imageUtils.performOCRFromReference(
                    prompt,
                    dx,
                    COST_BAR_DY,
                    imageUtils.relWidth(COST_BAR_W),
                    imageUtils.relHeight(COST_BAR_H),
                    useThreshold = false,
                    ocrEngine = "tesseract_digits",
                    debugName = name,
                    sourceBitmap = bitmap,
                ),
            )
        return read(COST_BAR_TOTAL_DX, "daily_sales_total") to read(COST_BAR_HELD_DX, "daily_sales_held")
    }

    /** The rows whose title and price are in view, top to bottom, each found by its "Cost" pill, with the scroll bar. Null when the list is not on screen. */
    private fun readSalePage(): SalePage? {
        val bitmap = imageUtils.getSourceBitmap()
        val prompt = LabelDailySalesPrompt.findImageWithBitmap(imageUtils, bitmap) ?: return null
        val top = imageUtils.relY(prompt.y, SALE_ROWS_TOP_FROM_PROMPT)
        val bottom = imageUtils.relY(prompt.y, SALE_ROWS_BOTTOM_FROM_PROMPT)
        val pills = LabelDailySalesCostPill.findAll(imageUtils, sourceBitmap = bitmap).filter { it.y.toInt() in top..bottom }.sortedBy { it.y }
        val page =
            pills.map { pill ->
                fun near(dx: Int, dy: Int, size: Int) = intArrayOf(imageUtils.relX(pill.x, dx), imageUtils.relY(pill.y, dy), imageUtils.relWidth(size), imageUtils.relHeight(size))
                val title =
                    imageUtils.performOCRFromReference(
                        pill,
                        SALE_TITLE_DX,
                        SALE_TITLE_DY,
                        imageUtils.relWidth(SALE_TITLE_W),
                        imageUtils.relHeight(SALE_TITLE_H),
                        useThreshold = false,
                        debugName = "daily_sales_title",
                        sourceBitmap = bitmap,
                    )
                val cost =
                    imageUtils.performOCRFromReference(
                        pill,
                        SALE_COST_DX,
                        SALE_COST_DY,
                        imageUtils.relWidth(SALE_COST_W),
                        imageUtils.relHeight(SALE_COST_H),
                        useThreshold = false,
                        ocrEngine = "tesseract_digits",
                        debugName = "daily_sales_cost",
                        sourceBitmap = bitmap,
                    )
                SaleRow(
                    title.trim(),
                    monies = IconMonies.check(imageUtils, region = near(SALE_COIN_DX, SALE_COIN_DY, SALE_COIN_SIZE), sourceBitmap = bitmap),
                    cost = parseMonies(cost),
                    buyable = CheckboxShopItem.check(imageUtils, region = near(SALE_BOX_DX, SALE_BOX_DY, SALE_BOX_SIZE), sourceBitmap = bitmap),
                )
            }
        val firstPill = imageUtils.relY(prompt.y, SALE_FIRST_PILL_FROM_PROMPT)
        val scroll = readSaleScroll(bitmap, prompt)
        val pillYs = pills.map { it.y.toInt() - firstPill }
        val where =
            if (scroll == null) {
                "no scroll bar"
            } else {
                val offset = scroll.thumbTop * imageUtils.relHeight(SALE_LIST_VIEW_H) / scroll.thumbLength
                val pitch = imageUtils.relHeight(SALE_ROW_PITCH)
                val first = pillYs.firstOrNull()?.let { Math.floorDiv(it + offset + pitch / 2, pitch) + 1 }
                "thumb ${scroll.thumbTop}/${scroll.thumbLength}${if (scroll.atEnd) " at the end" else ""}, first row ~${first ?: "-"}"
            }
        MessageLog.d(TAG, "[DAILY_EXTRAS] Daily Sales page ($where): " + page.joinToString(transform = ::saleRowText))
        return SalePage(page, pillYs, scroll)
    }

    /** Reads the list's scroll bar down its track column; the thumb is a dark grey (about 125,120,142) on a light track (211,209,219). */
    private fun readSaleScroll(bitmap: Bitmap, prompt: Point): SaleScroll? {
        val x = imageUtils.relX(prompt.x, SALE_TRACK_DX)
        val top = imageUtils.relY(prompt.y, SALE_TRACK_TOP_FROM_PROMPT)
        val bottom = imageUtils.relY(prompt.y, SALE_TRACK_BOTTOM_FROM_PROMPT)
        if (x !in 0 until bitmap.width || top < 0 || bottom >= bitmap.height) return null
        return saleScroll(
            (top..bottom).map { y ->
                val pixel = bitmap.getPixel(x, y)
                ((pixel shr 16 and 0xFF) + (pixel shr 8 and 0xFF) + (pixel and 0xFF)) / 3 < SALE_THUMB_MAX_GREY
            },
            imageUtils.relHeight(SALE_THUMB_END_SLACK),
        )
    }

    /** Drags the Daily Sales list up by one row, so each page repeats a row of the one before. False when the list is not on screen. */
    private fun swipeSales(): Boolean {
        val prompt = LabelDailySalesPrompt.findImageWithBitmap(imageUtils, imageUtils.getSourceBitmap()) ?: return false
        val x = prompt.x.toFloat()
        OwnUiForeground.waitForGame()
        game.gestureUtils.swipe(x, imageUtils.relY(prompt.y, SALE_SWIPE_FROM).toFloat(), x, imageUtils.relY(prompt.y, SALE_SWIPE_TO).toFloat(), SWIPE_MS)
        game.wait(1.0)
        return true
    }

    override fun pause(seconds: Double) = game.wait(seconds)

    /** Title and held-count text of every row whose title is in view, top to bottom. */
    private fun readVisibleRows(): List<Pair<String, String>> {
        val bitmap = imageUtils.getSourceBitmap()
        val pills = visiblePills(bitmap) ?: return emptyList()
        return pills.map { pill ->
            val held =
                imageUtils.performOCRFromReference(
                    pill,
                    ROW_HELD_DX,
                    ROW_HELD_DY,
                    imageUtils.relWidth(ROW_HELD_W),
                    imageUtils.relHeight(ROW_HELD_H),
                    useThreshold = false,
                    ocrEngine = "tesseract_digits",
                    debugName = "club_row_held",
                    sourceBitmap = bitmap,
                )
            Pair(readTitle(pill, bitmap), held)
        }
    }

    private fun readTitle(pill: Point, bitmap: Bitmap): String =
        imageUtils.performOCRFromReference(
            pill,
            ROW_TITLE_DX,
            ROW_TITLE_DY,
            imageUtils.relWidth(ROW_TITLE_W),
            imageUtils.relHeight(ROW_TITLE_H),
            useThreshold = false,
            debugName = "club_row_title",
            sourceBitmap = bitmap,
        )

    /** "Held" pills whose row title is fully in view, top to bottom; null when the picker prompt is not on screen. */
    private fun visiblePills(bitmap: Bitmap): List<Point>? {
        val prompt = LabelItemRequestSelectPrompt.findImageWithBitmap(imageUtils, bitmap) ?: return null
        val top = imageUtils.relY(prompt.y, LIST_TOP_FROM_PROMPT)
        val bottom = imageUtils.relY(prompt.y, LIST_BOTTOM_FROM_PROMPT)
        return LabelItemRequestHeld.findAll(imageUtils, sourceBitmap = bitmap).filter { it.y.toInt() in top..bottom }.sortedBy { it.y }
    }

    /** One swipe through the list; up = toward later rows. False when the picker is not on screen. */
    private fun swipePicker(up: Boolean): Boolean {
        val prompt = LabelItemRequestSelectPrompt.findImageWithBitmap(imageUtils, imageUtils.getSourceBitmap()) ?: return false
        val x = prompt.x.toFloat()
        val near = imageUtils.relY(prompt.y, SWIPE_FROM_PROMPT).toFloat()
        val far = imageUtils.relY(prompt.y, SWIPE_TO_PROMPT).toFloat()
        val (start, end) = if (up) near to far else far to near
        OwnUiForeground.waitForGame()
        game.gestureUtils.swipe(x, start, x, end, SWIPE_MS)
        game.wait(1.0)
        return true
    }

    private fun scrollPickerToTop() {
        var lastFirst: String? = null
        repeat(MAX_PICKER_PAGES) {
            val bitmap = imageUtils.getSourceBitmap()
            val first = visiblePills(bitmap)?.firstOrNull()?.let { readTitle(it, bitmap) } ?: return
            if (first == lastFirst) return
            lastFirst = first
            if (!swipePicker(up = false)) return
        }
    }

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]DailyExtras"

        /** Reads the settings and runs the step; null when no part is enabled. */
        fun runFromSettings(game: Game): List<String>? {
            val collectRewards = SettingsHelper.getBooleanSetting("misc", "collectDailyRewards", false)
            val clubRequest = SettingsHelper.getBooleanSetting("misc", "enableClubItemRequest", false)
            val buyDailySales = SettingsHelper.getBooleanSetting("misc", "buyDailySales", false)
            if (!collectRewards && !clubRequest && !buyDailySales) return null
            val item = SettingsHelper.getStringSetting("misc", "clubRequestItem", ClubRequestItems.LOWEST_HELD_SASH)
            MessageLog.i(TAG, "[DAILY_EXTRAS] Rewards=$collectRewards, Club request=$clubRequest ($item), Daily Sales=$buyDailySales.")
            return DailyExtras(GameExtrasDevice(game), collectRewards, clubRequest, item, buyDailySales) { MessageLog.i(TAG, "[DAILY_EXTRAS] $it") }.run()
        }
    }
}
