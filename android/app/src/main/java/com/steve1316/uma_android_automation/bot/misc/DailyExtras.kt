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
import com.steve1316.uma_android_automation.components.ButtonHomeClub
import com.steve1316.uma_android_automation.components.ButtonHomePresents
import com.steve1316.uma_android_automation.components.ButtonHomeSpecialMissions
import com.steve1316.uma_android_automation.components.ButtonItemRequestSashTab
import com.steve1316.uma_android_automation.components.ButtonItemRequestShoesTab
import com.steve1316.uma_android_automation.components.ButtonMenuBarHomeSelected
import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.DialogObjects
import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.components.LabelClubMenu
import com.steve1316.uma_android_automation.components.LabelConfirmRequestHeader
import com.steve1316.uma_android_automation.components.LabelItemRequestExpired
import com.steve1316.uma_android_automation.components.LabelItemRequestHeader
import com.steve1316.uma_android_automation.components.LabelItemRequestHeld
import com.steve1316.uma_android_automation.components.LabelItemRequestMaxDonations
import com.steve1316.uma_android_automation.components.LabelItemRequestSelectPrompt
import com.steve1316.uma_android_automation.components.LabelMissionsHeader
import com.steve1316.uma_android_automation.components.LabelPresentsHeader
import com.steve1316.uma_android_automation.components.LabelRewardsCollectedHeader
import org.opencv.core.Point

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

/**
 * Whether an OCR'd row title is [target]. Two dropped leading characters still match (a crop edge can cut a first letter),
 * but not a read that is also a front crop of three or more letters of another catalog name ("ka Sho" fits Oka, Shuka and Kikuka).
 */
internal fun matchItemName(ocr: String, target: String): Boolean {
    val read = normalizeItemName(ocr)
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

/** A full read of one picker tab; [complete] only when every catalog sash was read with a consistent count. */
internal data class PickerScan(val rows: List<PickerRow>, val complete: Boolean, val endReached: Boolean = complete)

/** The title and "Held" text of each row in view on one screenful, top to bottom. */
internal typealias PickerPage = List<Pair<String, String>>

/**
 * Merges screenfuls into one read. The end is two pages with nothing new. Complete means every catalog sash was read
 * with a count and no sash was read twice with different counts. An unreadable title or a page without overlap does not
 * matter once the whole catalog is in hand: a row that misreads on one screenful is read right on the next.
 */
internal fun mergePickerPages(pages: List<PickerPage>): PickerScan {
    val rows = mutableListOf<PickerRow>()
    var contradiction = false
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
                held != null && held != rows[seen].held -> contradiction = true
            }
        }
        stalePages = if (newRows == 0) stalePages + 1 else 0
    }
    val wholeCatalog = rows.map { it.name }.toSet() == ClubRequestItems.SASHES.toSet() && rows.all { it.held != null }
    return PickerScan(rows, complete = wholeCatalog && !contradiction, endReached = stalePages >= 2)
}

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
    UNKNOWN,
}

internal enum class ExtrasTarget { CLUB_BUTTON, ITEM_REQUEST_BUTTON, MISSIONS_BUTTON, PRESENTS_BUTTON, COLLECT_ALL, CLOSE, CANCEL, CONFIRM, BACK, HOME_TAB }

/** The game side of [DailyExtras]. One implementation drives the game; the tests drive a scripted fake. */
internal interface ExtrasDevice {
    fun screen(): ExtrasScreen

    /** Taps [target]; false when it is not on screen. */
    fun press(target: ExtrasTarget): Boolean

    /** Opens the Shoes or Winner's Sash tab of the picker. */
    fun openPickerTab(shoes: Boolean)

    fun scanPicker(): PickerScan

    /** Taps the picker row of [item]; false when no row reads as it. */
    fun tapPickerRow(item: String): Boolean

    /** The item name the Confirm Request dialog shows, or null when unreadable. */
    fun confirmRequestItem(): String?

    /** False when Collect All is greyed out, null when that cannot be told. */
    fun collectAllEnabled(): Boolean?

    fun pause(seconds: Double)
}

/**
 * Collects Daily Missions and the present box and makes the Club item request, from Home and back to Home.
 * It never donates and never confirms anything but the item request; any spending dialog is cancelled.
 */
internal class DailyExtras(
    private val device: ExtrasDevice,
    private val collectRewards: Boolean,
    private val clubRequest: Boolean,
    private val requestItem: String,
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

    private fun missions(): String {
        if (!pressOnHome(ExtrasTarget.MISSIONS_BUTTON)) return "Missions button not found"
        if (awaitScreen(ExtrasScreen.MISSIONS) != ExtrasScreen.MISSIONS) return "Missions screen did not appear"
        val note = collect(ExtrasScreen.MISSIONS)
        device.press(ExtrasTarget.BACK)
        return note
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
                log("Winner's Sash held counts: " + scan.rows.joinToString { "${it.name}=${it.held ?: "?"}" } + if (scan.complete) "" else " (incomplete, ${scan.rows.size} of ${ClubRequestItems.SASHES.size} read)")
                lowestHeldItem(scan) ?: return cancelPicker("the Winner's Sash list was not fully read")
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
                ExtrasScreen.MISSIONS -> device.press(ExtrasTarget.BACK)
                ExtrasScreen.CONFIRM_REQUEST, ExtrasScreen.ITEM_REQUEST_PICKER, ExtrasScreen.SPENDING_DIALOG -> device.press(ExtrasTarget.CANCEL)
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
            if (now == ExtrasScreen.SPENDING_DIALOG && ExtrasScreen.SPENDING_DIALOG !in screens) {
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

/** Dialogs that would spend RP, money or items; the Home steps cancel them. */
internal val SPENDING_DIALOG_NAMES =
    setOf("confirm_exchange", "confirm_restore_rp", "daily_sale", "purchase_alarm_clock", "purchase_carats", "purchase_daily_race_ticket", "shop")

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
            ButtonHomePresents.check(imageUtils, sourceBitmap = bitmap, confidence = 0.9) && bitmap.shows(ButtonMenuBarHomeSelected) -> ExtrasScreen.HOME
            spendingDialogShown(bitmap) -> ExtrasScreen.SPENDING_DIALOG
            else -> ExtrasScreen.UNKNOWN
        }
    }

    private fun Bitmap.shows(component: ComponentInterface): Boolean = component.check(imageUtils, sourceBitmap = this)

    /** Reads the dialog title quietly: most unknown frames are transitions, not dialogs. */
    private fun spendingDialogShown(bitmap: Bitmap): Boolean {
        val title = DialogUtils.getTitle(imageUtils, bitmap, logOnMiss = false) ?: return false
        return DialogObjects.items.any { it.title == title && it.name in SPENDING_DIALOG_NAMES }
    }

    override fun press(target: ExtrasTarget): Boolean {
        val clicked =
            when (target) {
                ExtrasTarget.CLUB_BUTTON -> ButtonHomeClub.click(imageUtils)
                ExtrasTarget.ITEM_REQUEST_BUTTON -> ButtonClubItemRequest.click(imageUtils)
                ExtrasTarget.MISSIONS_BUTTON -> ButtonHomeSpecialMissions.click(imageUtils, confidence = 0.9)
                ExtrasTarget.PRESENTS_BUTTON -> ButtonHomePresents.click(imageUtils, confidence = 0.9)
                ExtrasTarget.COLLECT_ALL -> ButtonCollectAll.click(imageUtils)
                ExtrasTarget.CLOSE -> ButtonClose.click(imageUtils)
                ExtrasTarget.CANCEL -> ButtonCancel.click(imageUtils)
                ExtrasTarget.CONFIRM -> ButtonConfirm.click(imageUtils)
                ExtrasTarget.BACK -> ButtonBack.click(imageUtils)
                ExtrasTarget.HOME_TAB -> ButtonMenuBarHomeSelected.click(imageUtils)
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
            if (!collectRewards && !clubRequest) return null
            val item = SettingsHelper.getStringSetting("misc", "clubRequestItem", ClubRequestItems.LOWEST_HELD_SASH)
            MessageLog.i(TAG, "[DAILY_EXTRAS] Rewards=$collectRewards, Club request=$clubRequest ($item).")
            return DailyExtras(GameExtrasDevice(game), collectRewards, clubRequest, item) { MessageLog.i(TAG, "[DAILY_EXTRAS] $it") }.run()
        }
    }
}
