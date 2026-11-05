package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.CLUB
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.CONFIRM_EXCHANGE
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.CONFIRM_REQUEST
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.DAILY_SALES
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.EXCHANGE_COMPLETE
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.GAME_UPDATE_PROMPT
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.HOME
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.ITEM_REQUEST_ENDED
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.ITEM_REQUEST_OTHER
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.ITEM_REQUEST_PICKER
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.MISSIONS
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.PRESENTS
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.REWARDS_COLLECTED
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.SHOP_MENU
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.SPENDING_DIALOG
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.UNKNOWN
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.BACK
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.CANCEL
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.CLOSE
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.CLUB_BUTTON
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.COLLECT_ALL
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.CONFIRM
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.DAILY_SALES_BANNER
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.EXCHANGE
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.HOME_TAB
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.ITEM_REQUEST_BUTTON
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.MISSIONS_BUTTON
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.MISSIONS_TAB_DAILY
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.MISSIONS_TAB_MAIN
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.MISSIONS_TAB_SPECIAL
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.MISSIONS_TAB_TITLES
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.PRESENTS_BUTTON
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.RESET
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.SELECT_ALL
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.SHOP_BUTTON
import com.steve1316.uma_android_automation.StartModule
import com.steve1316.uma_android_automation.components.DialogObjects
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A scripted game: each tap moves between screens the way the Global client does on the captured frames. */
private class FakeGame(
    var screen: ExtrasScreen = HOME,
    var request: String = "none",
    /** Rewards waiting on the tab Missions opens on. */
    var missionRewards: Boolean = true,
    /** Rewards waiting on the Titles tab. */
    var titlesRewards: Boolean = false,
    /** The tab Missions opens on. */
    var openTab: ExtrasTarget = MISSIONS_TAB_DAILY,
    /** Tabs whose template never matches, as when the game draws them differently. */
    val tabsMissing: Set<ExtrasTarget> = emptySet(),
    var presentBatches: Int = 1,
    val sashes: PickerScan = PickerScan(listOf(PickerRow("Kawasaki Kinen Winner's Sash", 12), PickerRow("Japan Cup Winner's Sash", 40)), complete = true),
    /** Overrides what the Confirm Request dialog names; null shows the picked item. */
    val confirmShows: String? = null,
    /** Shows a spending dialog once, on the first screen read after this tap. */
    val spendingAfter: ExtrasTarget? = null,
    /** After this tap the game shows a screen the step does not know, for good. */
    val unknownAfter: ExtrasTarget? = null,
    /** Presses of the Club button that miss while Home is still drawing. */
    var clubButtonMisses: Int = 0,
    /** A running request the step does not recognise that is really an expired one: closing it frees the slot. */
    val expiredMissed: Boolean = false,
    /** What the second read of the lowest sashes returns; null repeats the scan's counts. */
    val rereads: Map<String, Int?>? = null,
    /** After this tap a dialog with this title covers the screen, for good. */
    val dialogAfter: Pair<ExtrasTarget, String>? = null,
    /** What [spendingAfter] shows. */
    val spendingScreen: ExtrasScreen = SPENDING_DIALOG,
    /** Home's Shop button: true with the "Daily" badge, false without, null while it is not drawn. */
    var shopBadge: Boolean? = false,
    val sale: SaleScan = SaleScan(LIVE_SALE, complete = true),
    /** What the cost bar shows after Select All; null shows the sum of the buyable rows. */
    val barTotal: Int? = null,
    val balance: Int? = 5_683_820,
    /** After this tap a dialog the step does not know shows instead; its Close returns to the Daily Sales list. */
    val errorAfter: ExtrasTarget? = null,
) : ExtrasDevice {
    val taps = mutableListOf<ExtrasTarget>()
    var requested: String? = null
    var rereadCalls = 0
    val rereadAsked = mutableListOf<List<String>>()
    var shoesTab: Boolean? = null
    var collectedMissions = false
    val collectedTabs = mutableListOf<ExtrasTarget>()
    private var tab: ExtrasTarget = openTab
    var collectedPresents = 0
    private var spendingShowing = false
    private var rewardsFrom: ExtrasScreen = HOME
    private var picked: String? = null
    private var dialogTitle: String? = null
    private var selected = false
    var bought = false
    private var errorShowing = false

    override fun screen(): ExtrasScreen = dialogTitle?.let { extrasDialogScreen(it) ?: UNKNOWN } ?: if (spendingShowing) spendingScreen else screen

    override fun press(target: ExtrasTarget): Boolean {
        dialogTitle?.let { error("tapped $target on the \"$it\" dialog") }
        if (spendingShowing) {
            if (target != CANCEL) error("tapped $target on a spending dialog")
            taps += target
            spendingShowing = false
            return true
        }
        if (target == CLUB_BUTTON && clubButtonMisses > 0) {
            clubButtonMisses--
            return false
        }
        val next = transition(target) ?: return false
        taps += target
        screen = if (target == unknownAfter) UNKNOWN else next
        if (target == spendingAfter) spendingShowing = true
        if (target == dialogAfter?.first) dialogTitle = dialogAfter.second
        if (target == errorAfter) {
            screen = UNKNOWN
            errorShowing = true
        }
        return true
    }

    private fun transition(target: ExtrasTarget): ExtrasScreen? =
        when (screen to target) {
            HOME to CLUB_BUTTON -> CLUB
            HOME to MISSIONS_BUTTON -> MISSIONS.also { tab = openTab }
            HOME to PRESENTS_BUTTON -> PRESENTS
            CLUB to HOME_TAB -> HOME
            CLUB to ITEM_REQUEST_BUTTON ->
                when (request) {
                    "ended" -> ITEM_REQUEST_ENDED
                    "running" -> ITEM_REQUEST_OTHER
                    else -> ITEM_REQUEST_PICKER
                }
            ITEM_REQUEST_ENDED to CLOSE -> CLUB.also { request = "none" }
            ITEM_REQUEST_OTHER to CLOSE -> CLUB.also { if (expiredMissed) request = "none" }
            ITEM_REQUEST_PICKER to CANCEL -> CLUB
            CONFIRM_REQUEST to CANCEL -> ITEM_REQUEST_PICKER
            CONFIRM_REQUEST to CONFIRM ->
                CLUB.also {
                    requested = picked
                    request = "running"
                }
            MISSIONS to COLLECT_ALL -> if (rewardsOn(tab)) REWARDS_COLLECTED.also { rewardsFrom = MISSIONS } else MISSIONS
            // A tab template is the unselected look: it matches only on the other tabs.
            MISSIONS to MISSIONS_TAB_DAILY, MISSIONS to MISSIONS_TAB_MAIN, MISSIONS to MISSIONS_TAB_TITLES, MISSIONS to MISSIONS_TAB_SPECIAL ->
                if (target == tab || target in tabsMissing) null else MISSIONS.also { tab = target }
            MISSIONS to BACK -> HOME
            PRESENTS to COLLECT_ALL -> if (presentBatches > 0) REWARDS_COLLECTED.also { rewardsFrom = PRESENTS } else PRESENTS
            PRESENTS to CLOSE -> HOME
            HOME to SHOP_BUTTON -> if (shopBadge != null) SHOP_MENU else null
            SHOP_MENU to DAILY_SALES_BANNER -> DAILY_SALES
            SHOP_MENU to BACK -> HOME.also { if (bought) shopBadge = false }
            DAILY_SALES to SELECT_ALL -> DAILY_SALES.also { selected = true }
            DAILY_SALES to RESET -> DAILY_SALES.also { selected = false }
            // Confirm stays greyed out until something is selected.
            DAILY_SALES to CONFIRM -> if (selected) CONFIRM_EXCHANGE else DAILY_SALES
            DAILY_SALES to BACK -> SHOP_MENU.also { selected = false }
            CONFIRM_EXCHANGE to CANCEL -> DAILY_SALES
            CONFIRM_EXCHANGE to EXCHANGE ->
                EXCHANGE_COMPLETE.also {
                    bought = errorAfter != EXCHANGE
                    selected = false
                }
            EXCHANGE_COMPLETE to CLOSE -> DAILY_SALES
            UNKNOWN to CLOSE -> if (errorShowing) DAILY_SALES.also { errorShowing = false } else null
            REWARDS_COLLECTED to CLOSE ->
                rewardsFrom.also {
                    if (it == MISSIONS) {
                        if (tab == openTab) missionRewards = false
                        if (tab == MISSIONS_TAB_TITLES) titlesRewards = false
                        collectedMissions = true
                        collectedTabs += tab
                    } else {
                        presentBatches--
                        collectedPresents++
                    }
                }
            else -> null
        }

    override fun openPickerTab(shoes: Boolean) {
        shoesTab = shoes
    }

    override fun scanPicker(): PickerScan = sashes

    override fun rereadHeld(items: List<String>): Map<String, Int?> {
        rereadCalls++
        rereadAsked += items
        return rereads?.filterKeys { it in items } ?: sashes.rows.filter { it.name in items }.associate { it.name to it.held }
    }

    override fun tapPickerRow(item: String): Boolean {
        if (screen != ITEM_REQUEST_PICKER) return false
        val inTab = if (shoesTab == true) ClubRequestItems.SHOES.contains(item) else sashes.rows.any { it.name == item } || ClubRequestItems.SASHES.contains(item)
        if (!inTab) return false
        picked = item
        screen = CONFIRM_REQUEST
        return true
    }

    override fun confirmRequestItem(): String? = confirmShows ?: picked

    override fun collectAllEnabled(): Boolean? =
        when (screen) {
            MISSIONS -> rewardsOn(tab)
            PRESENTS -> presentBatches > 0
            else -> null
        }

    override fun shopBadge(): Boolean? = if (screen == HOME) shopBadge else null

    override fun readSaleRows(): SaleScan = if (screen == DAILY_SALES) sale else SaleScan(emptyList(), complete = false)

    override fun costBar(): Pair<Int?, Int?> = (if (selected) barTotal ?: sale.buyable.sumOf { it.cost ?: 0 } else 0) to balance

    private fun rewardsOn(on: ExtrasTarget): Boolean = (on == openTab && missionRewards) || (on == MISSIONS_TAB_TITLES && titlesRewards)

    override fun pause(seconds: Double) {}
}

/** A real Winner's Sash tab as the picker lists it (Default sort), with its held counts. */
private val LIVE_SASHES =
    listOf(
        "February S." to 65,
        "Takamatsunomiya Kinen" to 21,
        "Osaka Hai" to 487,
        "Oka Sho" to 64,
        "Satsuki Sho" to 317,
        "Tenno Sho (Spring)" to 35,
        "NHK Mile C." to 113,
        "Victoria Mile" to 249,
        "Japanese Oaks" to 38,
        "Japanese Derby" to 197,
        "Yasuda Kinen" to 505,
        "Takarazuka Kinen" to 295,
        "Sprinters S." to 215,
        "Shuka Sho" to 32,
        "Kikuka Sho" to 76,
        "Tenno Sho (Autumn)" to 604,
        "Mile Ch." to 122,
        "Queen Elizabeth II Cup" to 666,
        "Japan Cup" to 692,
        "Champions Cup" to 68,
        "Hanshin J.F." to 61,
        "Asahi Hai F.S." to 97,
        "Arima Kinen" to 703,
        "Hopeful S." to 287,
        "Japan Dirt Derby" to 56,
        "JBC Sprint" to 63,
        "JBC Classic" to 55,
        "JBC Ladies’ Classic" to 56,
        "Teio Sho" to 55,
        "Tokyo Daishoten" to 69,
        "Kawasaki Kinen" to 4,
        "Zen-Nippon Junior Yushun" to 4,
        "Kashiwa Kinen" to 4,
        "M.C. Nambu Hai" to 2,
    ).map { (race, held) -> "$race Winner's Sash" to held.toString() }

/** Daily Sales rows seen on the 2026-11-05 account 1 frames, in list order (not the whole list). */
private val LIVE_SALE =
    listOf(
        "[LOVE☆4EVER] Smart Falcon Star Piece" to 15_000,
        "[RUN! RUIN! LAUNCHER!] Gold Ship Star Piece" to 15_000,
        "[Wild Top Gear] Vodka Star Piece" to 10_000,
        "[King of Emeralds] King Halo Star Piece" to 5_000,
        "[Down the Line] Mejiro Ryan Star Piece" to 5_000,
        "[El☆Número 1] El Condor Pasa Star Piece" to 10_000,
        "Pleasing Parfait" to 5_000,
        "Support Points" to 3_000,
        "Takamatsunomiya Kinen Winner's Sash" to 5_000,
        "Kikuka Sho Winner's Sash" to 5_000,
        "Mile Ch. Winner's Sash" to 5_000,
    ).map { (title, cost) -> SaleRow(title, monies = true, cost = cost) }

private val LIVE_SALE_TOTAL = LIVE_SALE.sumOf { it.cost!! }

/** Screenfuls of [size] rows starting at [firsts], the way the bot reads them after each one-row swipe. */
private fun salePages(vararg firsts: Int, size: Int = 2, rows: List<SaleRow> = LIVE_SALE): List<List<SaleRow>> = firsts.map { rows.subList(it, minOf(it + size, rows.size)) }

private fun runSales(game: FakeGame): List<String> = DailyExtras(game, collectRewards = false, clubRequest = false, requestItem = "Dirt Racing Shoes", buyDailySales = true).run()

/** Screenfuls of six rows starting at [firsts] (row indexes), the way the bot reads them after each swipe. */
private fun livePages(vararg firsts: Int): List<PickerPage> = firsts.map { LIVE_SASHES.subList(it, minOf(it + 6, LIVE_SASHES.size)) }

private fun runClub(game: FakeGame, item: String = "Dirt Racing Shoes"): List<String> = DailyExtras(game, collectRewards = false, clubRequest = true, requestItem = item).run()

class DailyExtrasTest {
    @Test
    fun `with no request running, the configured item is requested and the step ends on Home`() {
        val game = FakeGame()
        val notes = runClub(game)
        assertEquals("Dirt Racing Shoes", game.requested)
        assertEquals(true, game.shoesTab)
        assertEquals(HOME, game.screen)
        assertEquals(listOf("Club: requested Dirt Racing Shoes"), notes)
    }

    @Test
    fun `an ended request is closed first, then a new one is made`() {
        val game = FakeGame(request = "ended")
        runClub(game, item = "Kawasaki Kinen Winner's Sash")
        assertEquals(listOf(CLUB_BUTTON, ITEM_REQUEST_BUTTON, CLOSE, ITEM_REQUEST_BUTTON, CONFIRM, HOME_TAB), game.taps)
        assertEquals("Kawasaki Kinen Winner's Sash", game.requested)
        assertEquals(false, game.shoesTab)
    }

    @Test
    fun `a running request is left alone and nothing new is asked for`() {
        val game = FakeGame(request = "running")
        val notes = runClub(game)
        assertNull(game.requested)
        assertFalse(CONFIRM in game.taps)
        assertEquals(listOf(CLUB_BUTTON, ITEM_REQUEST_BUTTON, CLOSE, ITEM_REQUEST_BUTTON, CLOSE, HOME_TAB), game.taps)
        assertEquals(listOf("Club: a request is still running"), notes)
    }

    @Test
    fun `an unrecognised request that frees the slot on close is reopened once and a new item is requested`() {
        val game = FakeGame(request = "running", expiredMissed = true)
        runClub(game, item = "Kawasaki Kinen Winner's Sash")
        assertEquals(listOf(CLUB_BUTTON, ITEM_REQUEST_BUTTON, CLOSE, ITEM_REQUEST_BUTTON, CONFIRM, HOME_TAB), game.taps)
        assertEquals("Kawasaki Kinen Winner's Sash", game.requested)
    }

    @Test
    fun `an unrecognised request twice stops after one reopen`() {
        val game = FakeGame(request = "running")
        runClub(game)
        assertEquals(2, game.taps.count { it == ITEM_REQUEST_BUTTON })
        assertNull(game.requested)
    }

    @Test
    fun `lowest held picks the sash with the fewest held`() {
        val rows = listOf(PickerRow("Japan Cup Winner's Sash", 40), PickerRow("Kashiwa Kinen Winner's Sash", 7), PickerRow("Arima Kinen Winner's Sash", 9))
        val game = FakeGame(sashes = PickerScan(rows, complete = true))
        runClub(game, item = ClubRequestItems.LOWEST_HELD_SASH)
        assertEquals("Kashiwa Kinen Winner's Sash", game.requested)
        assertEquals(false, game.shoesTab)
    }

    private fun lowestRequest(rows: List<PickerRow>, rereads: Map<String, Int?>? = null, complete: Boolean = true): Triple<FakeGame, List<String>, List<String>> {
        val game = FakeGame(sashes = PickerScan(rows, complete = complete), rereads = rereads)
        val logs = mutableListOf<String>()
        val notes = DailyExtras(game, collectRewards = false, clubRequest = true, requestItem = ClubRequestItems.LOWEST_HELD_SASH, log = logs::add).run()
        return Triple(game, logs, notes)
    }

    @Test
    fun `a high count misread low that is not the lowest is logged and the pick stays`() {
        val rows = listOf(PickerRow("Kawasaki Kinen Winner's Sash", 30), PickerRow("Japan Cup Winner's Sash", 96), PickerRow("Zen-Nippon Junior Yushun Winner's Sash", 120))
        val (game, logs, _) = lowestRequest(rows, mapOf("Kawasaki Kinen Winner's Sash" to 30, "Japan Cup Winner's Sash" to 196))
        assertEquals("Kawasaki Kinen Winner's Sash", game.requested)
        assertEquals(1, logs.count { "second time" in it && "Japan Cup 96 then 196" in it })
        assertEquals(1, game.rereadCalls)
    }

    @Test
    fun `a first row misread low is replaced by its larger second read and the next sash is requested`() {
        val rows = listOf(PickerRow("Japan Cup Winner's Sash", 96), PickerRow("Kawasaki Kinen Winner's Sash", 150), PickerRow("Zen-Nippon Junior Yushun Winner's Sash", 300))
        val (game, logs, _) = lowestRequest(rows, mapOf("Japan Cup Winner's Sash" to 196, "Kawasaki Kinen Winner's Sash" to 150))
        assertEquals("Kawasaki Kinen Winner's Sash", game.requested)
        assertEquals(1, logs.count { "Japan Cup 96 then 196" in it })
    }

    @Test
    fun `counts that read the same twice change nothing and log nothing`() {
        val rows = listOf(PickerRow("Japan Cup Winner's Sash", 40), PickerRow("Kawasaki Kinen Winner's Sash", 150))
        val (game, logs, notes) = lowestRequest(rows)
        assertEquals("Japan Cup Winner's Sash", game.requested)
        assertTrue(logs.none { "second time" in it })
        assertEquals(listOf("Club: requested Japan Cup Winner's Sash"), notes)
    }

    @Test
    fun `a lower or unreadable second read never lowers a count`() {
        val rows = listOf(PickerRow("Japan Cup Winner's Sash", 40), PickerRow("Kawasaki Kinen Winner's Sash", 150))
        val (game, _, _) = lowestRequest(rows, mapOf("Japan Cup Winner's Sash" to 4, "Kawasaki Kinen Winner's Sash" to null))
        assertEquals("Japan Cup Winner's Sash", game.requested)
    }

    @Test
    fun `a row seen on two pages keeps its larger count and an unreadable sighting finds nothing`() {
        val japanCup = "Japan Cup Winner's Sash"
        val items = listOf(japanCup, "Kawasaki Kinen Winner's Sash")
        val found = mutableMapOf<String, Int>()
        noteRereads(found, listOf(japanCup to "196", "Kawasaki Kinen Winner's Sash" to "unreadable"), items)
        noteRereads(found, listOf(japanCup to "96", "Satsuki Sho Winner's Sash" to "50"), items)
        noteRereads(found, listOf(japanCup to ""), items)
        assertEquals(mapOf(japanCup to 196), found)
        noteRereads(found, listOf("Kawasaki Kinen Winner's Sash" to "30"), items)
        assertEquals(setOf(japanCup, "Kawasaki Kinen Winner's Sash"), found.keys)
    }

    @Test
    fun `an incomplete read asks for nothing and reads nothing again`() {
        val (game, _, _) = lowestRequest(listOf(PickerRow("Japan Cup Winner's Sash", 40)), complete = false)
        assertNull(game.requested)
        assertEquals(0, game.rereadCalls)
    }

    @Test
    fun `lowest held requests nothing when a count is unreadable or the list was not read to its end`() {
        for (scan in listOf(
            PickerScan(listOf(PickerRow("Japan Cup Winner's Sash", 40), PickerRow("Arima Kinen Winner's Sash", null)), complete = true),
            PickerScan(listOf(PickerRow("Japan Cup Winner's Sash", 40)), complete = false),
            PickerScan(emptyList(), complete = true),
        )) {
            val game = FakeGame(sashes = scan)
            val notes = runClub(game, item = ClubRequestItems.LOWEST_HELD_SASH)
            assertNull(game.requested)
            assertFalse(CONFIRM in game.taps)
            assertTrue(CANCEL in game.taps)
            assertEquals(HOME, game.screen)
            assertEquals(listOf("Club: no request: the Winner's Sash list was not fully read"), notes)
        }
    }

    @Test
    fun `lowest held requests nothing when the scan read shoe rows because the Sash tab tap missed`() {
        val shoes = PickerScan(listOf(PickerRow("Sprint Racing Shoes", 254), PickerRow("Dirt Racing Shoes", 279)), complete = true)
        val game = FakeGame(sashes = shoes)
        val notes = runClub(game, item = ClubRequestItems.LOWEST_HELD_SASH)
        assertNull(game.requested)
        assertFalse(CONFIRM in game.taps)
        assertEquals(listOf("Club: no request: the Winner's Sash list was not fully read"), notes)
        val mixed = PickerScan(listOf(PickerRow("Kawasaki Kinen Winner's Sash", 12), PickerRow("Sprint Racing Shoes", 3)), complete = true)
        assertNull(lowestHeldItem(mixed))
    }

    @Test
    fun `lowest held keeps the list order on a tie`() {
        val scan = PickerScan(listOf(PickerRow("Oka Sho Winner's Sash", 5), PickerRow("Osaka Hai Winner's Sash", 5)), complete = true)
        assertEquals("Oka Sho Winner's Sash", lowestHeldItem(scan))
    }

    @Test
    fun `Confirm Request naming another item is cancelled, never confirmed`() {
        val game = FakeGame(confirmShows = "Mile Racing Shoes")
        val notes = runClub(game)
        assertNull(game.requested)
        assertFalse(CONFIRM in game.taps)
        assertEquals(listOf(CLUB_BUTTON, ITEM_REQUEST_BUTTON, CANCEL, CANCEL, HOME_TAB), game.taps)
        assertEquals(HOME, game.screen)
        assertTrue(notes.single().contains("instead of Dirt Racing Shoes"))
    }

    @Test
    fun `an item missing from the picker cancels the picker`() {
        val game = FakeGame()
        val notes = runClub(game, item = "Imaginary Racing Shoes")
        assertNull(game.requested)
        assertEquals(listOf(CLUB_BUTTON, ITEM_REQUEST_BUTTON, CANCEL, HOME_TAB), game.taps)
        assertEquals(listOf("Club: no request: Imaginary Racing Shoes is not in the picker"), notes)
    }

    @Test
    fun `missions and presents are collected and the step returns to Home`() {
        val game = FakeGame(presentBatches = 2)
        val notes = DailyExtras(game, collectRewards = true, clubRequest = false, requestItem = "Dirt Racing Shoes").run()
        assertTrue(game.collectedMissions)
        assertEquals(2, game.collectedPresents)
        assertEquals(HOME, game.screen)
        assertEquals(listOf("Presents: collected", "Missions: collected on the opened tab"), notes)
        assertFalse(CLUB_BUTTON in game.taps)
    }

    private fun runMissions(game: FakeGame) = DailyExtras(game, collectRewards = true, clubRequest = false, requestItem = "Dirt Racing Shoes").run().single { it.startsWith("Missions: ") }

    @Test
    fun `rewards on the Titles tab are collected when the opened tab has none`() {
        val game = FakeGame(missionRewards = false, titlesRewards = true, presentBatches = 0)
        val note = runMissions(game)
        assertEquals(listOf(MISSIONS_TAB_TITLES), game.collectedTabs)
        val taps = game.taps.dropWhile { it != MISSIONS_TAB_TITLES }
        assertEquals(listOf(MISSIONS_TAB_TITLES, COLLECT_ALL, CLOSE), taps.take(3))
        assertEquals("Missions: collected on Titles", note)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `rewards on the opened tab and on Titles are both collected and named`() {
        val game = FakeGame(titlesRewards = true, presentBatches = 0)
        val note = runMissions(game)
        assertEquals(listOf(MISSIONS_TAB_DAILY, MISSIONS_TAB_TITLES), game.collectedTabs)
        assertEquals("Missions: collected on the opened tab, Titles", note)
    }

    @Test
    fun `a tab that cannot be opened is named, never reported as nothing to collect`() {
        val game = FakeGame(missionRewards = false, presentBatches = 0, tabsMissing = setOf(MISSIONS_TAB_SPECIAL))
        val note = runMissions(game)
        assertEquals("Missions: Special tab not found", note)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `the tab Missions opens on is reached again once another tab is selected`() {
        val game = FakeGame(missionRewards = false, presentBatches = 0, openTab = MISSIONS_TAB_MAIN, titlesRewards = true)
        val note = runMissions(game)
        assertEquals(listOf(MISSIONS_TAB_TITLES), game.collectedTabs)
        assertEquals(MISSIONS_TAB_MAIN, game.taps.filter { it == MISSIONS_TAB_MAIN }.single())
        assertEquals("Missions: collected on Titles", note)
    }

    @Test
    fun `a Rewards Collected title counts as that popup and a spending title as a spending dialog`() {
        assertEquals(REWARDS_COLLECTED, extrasDialogScreen("Rewards Collected"))
        assertEquals(SPENDING_DIALOG, extrasDialogScreen(DialogObjects.items.first { it.name == "shop" }.title))
        assertEquals(GAME_UPDATE_PROMPT, extrasDialogScreen("Application Updated"))
        assertNull(extrasDialogScreen(null))
    }

    @Test
    fun `the forced update prompt during the missions visit stops the run with its own key and gets no tap`() {
        val game = FakeGame(missionRewards = false, presentBatches = 0, dialogAfter = MISSIONS_BUTTON to "Application Updated")
        val before = Triple(StartModule.queueStopKey, StartModule.queueStopReason, StartModule.queueStopRequested)
        try {
            StartModule.queueStopKey = null
            StartModule.queueStopRequested = false
            assertThrows(InterruptedException::class.java) { DailyExtras(game, collectRewards = true, clubRequest = false, requestItem = "Dirt Racing Shoes").run() }
            assertEquals("GAME_UPDATE_REQUIRED", StartModule.queueStopKey)
            assertTrue(StartModule.queueStopRequested)
            assertEquals(listOf(PRESENTS_BUTTON, CLOSE, MISSIONS_BUTTON), game.taps, "no tap once the prompt shows")
        } finally {
            StartModule.queueStopKey = before.first
            StartModule.queueStopReason = before.second
            StartModule.queueStopRequested = before.third
        }
    }

    @Test
    fun `nothing to collect taps no Collect All and still returns to Home`() {
        val game = FakeGame(missionRewards = false, presentBatches = 0)
        val notes = DailyExtras(game, collectRewards = true, clubRequest = false, requestItem = "Dirt Racing Shoes").run()
        assertFalse(COLLECT_ALL in game.taps)
        assertEquals(listOf(PRESENTS_BUTTON, CLOSE, MISSIONS_BUTTON, MISSIONS_TAB_MAIN, MISSIONS_TAB_TITLES, MISSIONS_TAB_SPECIAL, MISSIONS_TAB_DAILY, BACK), game.taps)
        assertEquals(listOf("Presents: nothing to collect", "Missions: nothing to collect"), notes)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `a spending dialog on the way is cancelled and the step goes on`() {
        val game = FakeGame(spendingAfter = CLUB_BUTTON)
        val notes = runClub(game)
        assertEquals(listOf(CLUB_BUTTON, CANCEL, ITEM_REQUEST_BUTTON, CONFIRM, HOME_TAB), game.taps)
        assertEquals("Dirt Racing Shoes", game.requested)
        assertEquals("cancelled 1 spending dialog(s)", notes.last())
    }

    @Test
    fun `an unknown screen ends the step without accepting anything`() {
        val game = FakeGame(unknownAfter = CLUB_BUTTON)
        val notes = DailyExtras(game, collectRewards = true, clubRequest = true, requestItem = "Dirt Racing Shoes").run()
        assertEquals(listOf(CLUB_BUTTON), game.taps)
        assertEquals(listOf("Club: Club screen did not appear; could not get back to Home, stopped"), notes)
        assertNull(game.requested)
    }

    @Test
    fun `off Home the step taps nothing`() {
        val game = FakeGame(screen = CLUB)
        assertEquals(listOf("not on Home, skipped"), DailyExtras(game, collectRewards = true, clubRequest = true, requestItem = "Dirt Racing Shoes").run())
        assertTrue(game.taps.isEmpty())
    }

    @Test
    fun `item names match through dropped punctuation and two lost leading letters only`() {
        assertTrue(matchItemName("Kawasaki Kinen Winners Sash", "Kawasaki Kinen Winner's Sash"))
        assertTrue(matchItemName("wasaki Kinen Winner's Sash", "Kawasaki Kinen Winner's Sash"))
        assertFalse(matchItemName("saki Kinen Winner's Sash", "Kawasaki Kinen Winner's Sash"))
        assertFalse(matchItemName("JBC Classic Winner's Sash", "JBC Ladies' Classic Winner's Sash"))
        assertFalse(matchItemName("", "Dirt Racing Shoes"))
        assertEquals("M.C. Nambu Hai Winner's Sash", itemNameFromTitle("MC Nambu Hai Winner's Sash"))
        assertNull(itemNameFromTitle("Hokkaido Cup Winner's Sash"))
        assertNull(itemNameFromTitle("Held"))
    }

    @Test
    fun `a front crop of Shuka or Kikuka that is also the tail of another sash name reads as unknown`() {
        for (crop in listOf("uka Sho", "ka Sho")) assertNull(itemNameFromTitle("$crop Winner's Sash"), crop)
        assertFalse(matchItemName("ka Sho Winner's Sash", "Oka Sho Winner's Sash"))
        assertFalse(matchItemName("uka Sho Winner's Sash", "Shuka Sho Winner's Sash"))
    }

    @Test
    fun `Oka Sho, Shuka Sho and Kikuka Sho still match in full and with one lost letter`() {
        assertEquals("Oka Sho Winner's Sash", itemNameFromTitle("Oka Sho Winner's Sash"))
        assertEquals("Shuka Sho Winner's Sash", itemNameFromTitle("Shuka Sho Winner's Sash"))
        assertEquals("Kikuka Sho Winner's Sash", itemNameFromTitle("Kikuka Sho Winner's Sash"))
        assertEquals("Kikuka Sho Winner's Sash", itemNameFromTitle("kuka Sho Winner's Sash"))
        assertEquals("Shuka Sho Winner's Sash", itemNameFromTitle("huka Sho Winner's Sash"))
        assertEquals("Kikuka Sho Winner's Sash", itemNameFromTitle("ikuka Sho Winner's Sash"))
    }

    @Test
    fun `no catalog name with up to two lost leading letters reads as a different name`() {
        val names = ClubRequestItems.SHOES + ClubRequestItems.SASHES
        for (name in names) {
            for (lost in 0..2) {
                val read = itemNameFromTitle(name.drop(lost))
                assertTrue(read == null || read == name, "$name minus $lost read as $read")
            }
        }
    }

    @Test
    fun `a cropped Shuka row beside the real row still gives a complete read`() {
        val shuka = LIVE_SASHES.first { it.first.startsWith("Shuka") }
        val pages = livePages(0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 28, 28, 28).map { page -> page.map { if (it == shuka) "ka Sho Winner's Sash" to "1" else it } }
        val withReal = pages + listOf(listOf(shuka))
        assertTrue(mergePickerPages(withReal).complete)
        assertEquals(34, mergePickerPages(withReal).rows.size)
        val without = mergePickerPages(pages)
        assertEquals(33, without.rows.size)
        assertFalse(without.complete)
    }

    @Test
    fun `every sash the live picker lists is named, including the six the first live scan missed`() {
        for ((title, _) in LIVE_SASHES) assertTrue(itemNameFromTitle(title) != null, title)
        for (ocr in listOf(
            "NHK Mile C Winners Sash",
            "Sprinters S Winner’s Sash",
            "huka Sho Winner's Sash",
            "Hanshin JF Winner's Sash",
            "JBC Classic Winners Sash",
            "JBC Ladies Classic Winner's Sash",
        )) {
            assertTrue(itemNameFromTitle(ocr) != null, ocr)
        }
        assertEquals("JBC Classic Winner's Sash", itemNameFromTitle("JBC Classic Winners Sash"))
        assertEquals("JBC Ladies' Classic Winner's Sash", itemNameFromTitle("JBC Ladies Classic Winner's Sash"))
    }

    @Test
    fun `overlapping screenfuls read all 34 sashes and pick the true lowest`() {
        val scan = mergePickerPages(livePages(0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 28, 28, 28))
        assertTrue(scan.complete)
        assertEquals(34, scan.rows.size)
        assertEquals("M.C. Nambu Hai Winner's Sash", lowestHeldItem(scan))
    }

    @Test
    fun `a swipe that skips a row leaves the read incomplete and requests nothing`() {
        // A swipe that coasted one row too far: rows 1 to 6, then 8 to 13, so row 7 (NHK Mile C.) was never in view.
        val scan = mergePickerPages(livePages(0, 7, 10, 13, 16, 19, 22, 25, 28, 28, 28))
        assertTrue(scan.endReached)
        assertFalse(scan.complete)
        assertNull(lowestHeldItem(scan))
    }

    @Test
    fun `a row that reads as no known item leaves the read incomplete`() {
        val pages = livePages(0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 28, 28, 28).toMutableList()
        pages[0] = pages[0].toMutableList().also { it[0] = "Hokkaido Cup Winner's Sash" to "1" }
        val scan = mergePickerPages(pages)
        assertFalse(scan.complete)
        assertNull(lowestHeldItem(scan))
        val game = FakeGame(sashes = scan)
        runClub(game, item = ClubRequestItems.LOWEST_HELD_SASH)
        assertNull(game.requested)
        assertFalse(CONFIRM in game.taps)
    }

    @Test
    fun `a title misread on one page does not veto a read that holds every sash`() {
        val pages = livePages(0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 28, 28, 28).toMutableList()
        pages[5] = pages[5].toMutableList().also { it[2] = "Queen Elizabeth I Cup Winner's Sash" to "666" }
        val scan = mergePickerPages(pages)
        assertTrue(scan.complete)
        assertEquals(34, scan.rows.size)
        assertEquals("M.C. Nambu Hai Winner's Sash", lowestHeldItem(scan))
        val game = FakeGame(sashes = scan)
        assertEquals(listOf("Club: requested M.C. Nambu Hai Winner's Sash"), runClub(game, item = ClubRequestItems.LOWEST_HELD_SASH))
    }

    @Test
    fun `a page without a shared row does not veto a read that holds every sash`() {
        val scan = mergePickerPages(livePages(0, 6, 12, 18, 24, 28, 28, 28))
        assertTrue(scan.complete)
        assertEquals("M.C. Nambu Hai Winner's Sash", lowestHeldItem(scan))
    }

    @Test
    fun `33 of 34 sashes with a misread title stay incomplete and request nothing`() {
        val missing = "Yasuda Kinen Winner's Sash"
        val pages = livePages(0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 28, 28, 28).map { page -> page.filter { it.first != missing } }.toMutableList()
        pages[5] = pages[5].toMutableList().also { it[2] = "Queen Elizabeth I Cup Winner's Sash" to "666" }
        val scan = mergePickerPages(pages)
        assertEquals(33, scan.rows.size)
        assertFalse(scan.complete)
        assertNull(lowestHeldItem(scan))
        val game = FakeGame(sashes = scan)
        runClub(game, item = ClubRequestItems.LOWEST_HELD_SASH)
        assertNull(game.requested)
        assertFalse(CONFIRM in game.taps)
    }

    private val shukaSash = "Shuka Sho Winner's Sash"
    private val nambuSash = "M.C. Nambu Hai Winner's Sash"

    /** The live read where [name] shows [reads] on the pages that hold it, in order, and the last one on any further page. */
    private fun sequencedPages(name: String, reads: List<String>): List<PickerPage> {
        var seen = 0
        return livePages(0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 28, 28, 28).map { page -> page.map { if (it.first == name) name to reads[minOf(seen++, reads.lastIndex)] else it } }
    }

    private fun conflictingPages(name: String, first: String, later: String) = sequencedPages(name, listOf(first, later))

    private fun conflictRequest(pages: List<PickerPage>, rereads: Map<String, Int?>): Triple<FakeGame, List<String>, List<String>> {
        val game = FakeGame(sashes = mergePickerPages(pages), rereads = rereads)
        val logs = mutableListOf<String>()
        val notes = DailyExtras(game, collectRewards = false, clubRequest = true, requestItem = ClubRequestItems.LOWEST_HELD_SASH, log = logs::add).run()
        return Triple(game, logs, notes)
    }

    @Test
    fun `a sash read 99 and 66 on two pages, in either order, keeps 99 and the request goes ahead without picking it`() {
        for ((first, later) in listOf("99" to "66", "66" to "99")) {
            val scan = mergePickerPages(conflictingPages(shukaSash, first, later))
            assertFalse(scan.complete)
            assertEquals(mapOf(shukaSash to (first.toInt() to later.toInt())), scan.conflicts)
            for (third in listOf(66, 99, null)) {
                val (game, logs, notes) = conflictRequest(conflictingPages(shukaSash, first, later), mapOf(shukaSash to third))
                assertEquals(nambuSash, game.requested, "$first then $later, third $third")
                assertEquals(listOf(shukaSash), game.rereadAsked.first())
                assertEquals(1, logs.count { "Shuka Sho" in it && "$first and $later" in it && "the largest (99) is used" in it })
                assertEquals(listOf("Club: requested $nambuSash"), notes)
            }
        }
    }

    @Test
    fun `a true-low sash with one high misread is skipped as its larger read`() {
        // True count 2 read as 20 on one page: the larger read stands even when the third read repeats 2.
        val (game, logs, _) = conflictRequest(conflictingPages(nambuSash, "2", "20"), mapOf(nambuSash to 2))
        assertEquals("Kawasaki Kinen Winner's Sash", game.requested)
        assertEquals(1, logs.count { "Nambu Hai" in it && "2 and 20" in it && "2 the third time, the largest (20) is used" in it })
    }

    @Test
    fun `a third read larger than both earlier reads is the one kept`() {
        val (game, logs, _) = conflictRequest(conflictingPages(nambuSash, "2", "20"), mapOf(nambuSash to 200))
        assertEquals("Kawasaki Kinen Winner's Sash", game.requested)
        assertEquals(1, logs.count { "Nambu Hai" in it && "the largest (200) is used" in it })
    }

    @Test
    fun `a sash read three different ways keeps the largest of all reads and the scan line is not called incomplete`() {
        val kashiwa = "Kashiwa Kinen Winner's Sash"
        val pages = sequencedPages(kashiwa, listOf("5", "9", "559"))
        assertEquals(mapOf(kashiwa to (5 to 559)), mergePickerPages(pages).conflicts)
        val (game, logs, _) = conflictRequest(pages, mapOf(kashiwa to 5))
        assertEquals(nambuSash, game.requested)
        assertEquals(1, logs.count { "Kashiwa Kinen" in it && "5 and 559" in it && "the largest (559) is used" in it })
        assertEquals(1, logs.count { "held counts" in it && "(34 of 34 read, 1 read with different counts)" in it && "incomplete" !in it })
    }

    @Test
    fun `a clean catalog is read again only at the two lowest and logs nothing`() {
        val (game, logs, notes) = conflictRequest(livePages(0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 28, 28, 28), emptyMap())
        assertEquals(nambuSash, game.requested)
        assertEquals(1, game.rereadCalls)
        assertEquals(listOf(listOf(nambuSash, "Kawasaki Kinen Winner's Sash")), game.rereadAsked)
        assertTrue(logs.none { "third time" in it || "second time" in it })
        assertEquals(listOf("Club: requested $nambuSash"), notes)
    }

    @Test
    fun `a disagreement on a read that also lacks a sash requests nothing and reads nothing again`() {
        val pages = conflictingPages(shukaSash, "99", "66").map { page -> page.filter { it.first != "Yasuda Kinen Winner's Sash" } }
        val (game, _, notes) = conflictRequest(pages, mapOf(shukaSash to 99))
        assertNull(game.requested)
        assertEquals(0, game.rereadCalls)
        assertEquals(listOf("Club: no request: the Winner's Sash list was not fully read"), notes)
    }

    @Test
    fun `a count that failed to read on one page is taken from the next, and one that never reads leaves the read incomplete`() {
        val pages = livePages(0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 28, 28, 28).toMutableList()
        pages[0] = pages[0].toMutableList().also { it[4] = it[4].first to "" }
        assertTrue(mergePickerPages(pages).complete)
        val never = livePages(0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 28, 28, 28).map { page -> page.map { (name, held) -> if (name == LIVE_SASHES[4].first) name to "" else name to held } }
        assertFalse(mergePickerPages(never).complete)
    }

    @Test
    fun `the page line names the counts, the shared rows and a title that is no known item`() {
        val previous = livePages(0)[0]
        val page = livePages(3)[0].toMutableList().also { it[1] = "Queen Elizabeth I Cup Winner's Sash" to "666" }
        assertEquals(
            "rows [Oka Sho=64, ?=666, Tenno Sho (Spring)=35, NHK Mile C.=113, Victoria Mile=249, Japanese Oaks=38], overlap 2, unknown \"Queen Elizabeth I Cup Winner's Sash\"",
            pickerPageSummary(page, previous),
        )
        assertEquals("rows [February S.=65, Takamatsunomiya Kinen=21, Osaka Hai=487, Oka Sho=64, Satsuki Sho=317, Tenno Sho (Spring)=35]", pickerPageSummary(previous, null))
    }

    @Test
    fun `two empty pages after a partial read are incomplete, not the list end`() {
        val pages = livePages(0, 3) + listOf(emptyList<Pair<String, String>>(), emptyList())
        val scan = mergePickerPages(pages)
        assertFalse(scan.complete)
        assertNull(lowestHeldItem(scan))
        val game = FakeGame(sashes = scan)
        runClub(game, item = ClubRequestItems.LOWEST_HELD_SASH)
        assertNull(game.requested)
        assertFalse(CONFIRM in game.taps)
    }

    @Test
    fun `a read that misses one catalog sash is incomplete even when every page overlaps`() {
        val missing = "Yasuda Kinen Winner's Sash"
        val pages = livePages(0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 28, 28, 28).map { page -> page.filter { it.first != missing } }
        val scan = mergePickerPages(pages)
        assertEquals(33, scan.rows.size)
        assertTrue(scan.endReached)
        assertFalse(scan.complete)
        assertNull(lowestHeldItem(scan))
    }

    @Test
    fun `all 34 sashes in the Held ascending sort read complete and the top row is the pick`() {
        val byHeld = LIVE_SASHES.sortedBy { it.second.toInt() }
        val pages = listOf(0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 28, 28, 28).map { byHeld.subList(it, minOf(it + 6, byHeld.size)) }
        val scan = mergePickerPages(pages)
        assertTrue(scan.complete)
        assertEquals(34, scan.rows.size)
        assertEquals(byHeld.first().first, lowestHeldItem(scan))
    }

    @Test
    fun `a list that never shows its end is incomplete`() {
        assertFalse(mergePickerPages(livePages(0, 3)).complete)
        assertFalse(mergePickerPages(emptyList()).complete)
    }

    @Test
    fun `the Club button is pressed once Home has finished drawing it`() {
        val game = FakeGame(clubButtonMisses = 2)
        assertEquals(listOf("Club: requested Dirt Racing Shoes"), runClub(game))
        assertEquals("Dirt Racing Shoes", game.requested)
    }

    @Test
    fun `a Club button that never draws ends the part safely on Home`() {
        val game = FakeGame(clubButtonMisses = 100)
        assertEquals(listOf("Club: Club button not found"), runClub(game))
        assertTrue(game.taps.isEmpty())
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `held counts keep their digits and drop separators`() {
        assertEquals(12, parseHeldCount("12"))
        assertEquals(6322, parseHeldCount("6,322"))
        assertNull(parseHeldCount(""))
        assertNull(parseHeldCount("Held"))
    }

    @Test
    fun `with Buy the Daily Sales off the Shop is never opened`() {
        val game = FakeGame(shopBadge = true)
        val notes = DailyExtras(game, collectRewards = true, clubRequest = false, requestItem = "Dirt Racing Shoes").run()
        assertFalse(SHOP_BUTTON in game.taps)
        assertFalse(notes.any { it.startsWith("Daily Sales") })
    }

    @Test
    fun `without the Daily badge the Shop is not opened`() {
        val game = FakeGame(shopBadge = false)
        assertEquals(listOf("Daily Sales: no Daily badge on the Shop"), runSales(game))
        assertTrue(game.taps.isEmpty())
    }

    @Test
    fun `a Shop button that never draws ends the part without a tap`() {
        val game = FakeGame(shopBadge = null)
        assertEquals(listOf("Daily Sales: Shop button not found"), runSales(game))
        assertTrue(game.taps.isEmpty())
    }

    @Test
    fun `with the badge every row is bought for Monies and the step ends on Home, after the other parts`() {
        val game = FakeGame(shopBadge = true, missionRewards = false, presentBatches = 0)
        val notes = DailyExtras(game, collectRewards = true, clubRequest = false, requestItem = "Dirt Racing Shoes", buyDailySales = true).run()
        assertEquals(listOf("Presents: nothing to collect", "Missions: nothing to collect", "Daily Sales: bought 11 item(s) for $LIVE_SALE_TOTAL Monies"), notes)
        assertEquals(listOf(SHOP_BUTTON, DAILY_SALES_BANNER, SELECT_ALL, CONFIRM, EXCHANGE, CLOSE, BACK, BACK), game.taps.dropWhile { it != SHOP_BUTTON })
        assertTrue(game.bought)
        assertEquals(HOME, game.screen)
        assertEquals(false, game.shopBadge)
    }

    @Test
    fun `a sold-out row is left out of the total and does not block the purchase`() {
        val rows = LIVE_SALE + SaleRow("Medium Racing Shoes", monies = true, cost = 4_000, buyable = false)
        val game = FakeGame(shopBadge = true, sale = SaleScan(rows, complete = true))
        assertEquals(listOf("Daily Sales: bought 11 item(s) for $LIVE_SALE_TOTAL Monies"), runSales(game))
        assertTrue(game.bought)
    }

    @Test
    fun `a Carats row whose checkbox was missed still buys nothing`() {
        val rows = LIVE_SALE + SaleRow("Carat Pack", monies = false, cost = 500, buyable = false)
        val game = FakeGame(shopBadge = true, sale = SaleScan(rows, complete = true))
        assertEquals(listOf("Daily Sales: nothing bought: a row is not priced in Monies"), runSales(game))
        assertFalse(SELECT_ALL in game.taps)
        assertFalse(game.bought)
    }

    @Test
    fun `a row not priced in Monies leaves the whole list unselected`() {
        val rows = LIVE_SALE.mapIndexed { i, row -> if (i == 3) row.copy(monies = false) else row }
        val game = FakeGame(shopBadge = true, sale = SaleScan(rows, complete = true))
        assertEquals(listOf("Daily Sales: nothing bought: a row is not priced in Monies"), runSales(game))
        assertEquals(listOf(SHOP_BUTTON, DAILY_SALES_BANNER, BACK, BACK), game.taps)
        assertFalse(game.bought)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `a list not read to its end, an unread price or an all sold-out list selects nothing`() {
        val cases =
            listOf(
                SaleScan(LIVE_SALE, complete = false, gap = true) to "the list was not read to its end",
                SaleScan(LIVE_SALE.mapIndexed { i, row -> if (i == 5) row.copy(cost = null) else row }, complete = true) to "a row could not be read",
                SaleScan(LIVE_SALE.map { it.copy(buyable = false) }, complete = true) to "every row is sold out",
                SaleScan(emptyList(), complete = true) to "no row was read",
            )
        for ((scan, reason) in cases) {
            val game = FakeGame(shopBadge = true, sale = scan)
            assertEquals(listOf("Daily Sales: nothing bought: $reason"), runSales(game))
            assertFalse(SELECT_ALL in game.taps, reason)
            assertFalse(game.bought, reason)
        }
    }

    @Test
    fun `a cost bar that disagrees with the rows is reset and never confirmed`() {
        val game = FakeGame(shopBadge = true, barTotal = LIVE_SALE_TOTAL + 500)
        assertEquals(listOf("Daily Sales: nothing bought: the cost bar shows ${LIVE_SALE_TOTAL + 500}, the rows add up to $LIVE_SALE_TOTAL"), runSales(game))
        assertEquals(listOf(SHOP_BUTTON, DAILY_SALES_BANNER, SELECT_ALL, RESET, BACK, BACK), game.taps)
        assertFalse(game.bought)
    }

    @Test
    fun `too few Monies or an unreadable cost bar is never confirmed`() {
        for ((balance, reason) in listOf(50_000 to "it costs $LIVE_SALE_TOTAL Monies and 50000 are held", null to "the cost bar could not be read")) {
            val game = FakeGame(shopBadge = true, balance = balance)
            assertEquals(listOf("Daily Sales: nothing bought: $reason"), runSales(game))
            assertFalse(CONFIRM in game.taps, reason)
            assertFalse(game.bought, reason)
        }
    }

    @Test
    fun `an unknown dialog after Confirm is closed and nothing is claimed as bought`() {
        val game = FakeGame(shopBadge = true, errorAfter = CONFIRM)
        assertEquals(listOf("Daily Sales: nothing bought: no Confirm Exchange dialog after Confirm"), runSales(game))
        assertFalse(EXCHANGE in game.taps)
        assertFalse(game.bought)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `an unknown dialog after Exchange is reported as unconfirmed, never as bought`() {
        val game = FakeGame(shopBadge = true, errorAfter = EXCHANGE)
        val note = runSales(game).single()
        assertEquals("Daily Sales: Exchange pressed for $LIVE_SALE_TOTAL Monies, but no Exchange Complete was seen", note)
        assertFalse(note.contains("bought "))
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `a Confirm Exchange dialog outside the Daily Sales part is cancelled like any spending dialog`() {
        val game = FakeGame(spendingAfter = MISSIONS_BUTTON, spendingScreen = CONFIRM_EXCHANGE, presentBatches = 0)
        val notes = DailyExtras(game, collectRewards = true, clubRequest = false, requestItem = "Dirt Racing Shoes").run()
        assertEquals(CANCEL, game.taps[game.taps.indexOf(MISSIONS_BUTTON) + 1])
        assertEquals("cancelled 1 spending dialog(s)", notes.last())
    }

    @Test
    fun `the forced update prompt in the Shop stops the run with no tap on it`() {
        val game = FakeGame(shopBadge = true, dialogAfter = DAILY_SALES_BANNER to "Application Updated")
        val before = Triple(StartModule.queueStopKey, StartModule.queueStopReason, StartModule.queueStopRequested)
        try {
            StartModule.queueStopKey = null
            StartModule.queueStopRequested = false
            assertThrows(InterruptedException::class.java) { runSales(game) }
            assertEquals("GAME_UPDATE_REQUIRED", StartModule.queueStopKey)
            assertEquals(listOf(SHOP_BUTTON, DAILY_SALES_BANNER), game.taps)
        } finally {
            StartModule.queueStopKey = before.first
            StartModule.queueStopReason = before.second
            StartModule.queueStopRequested = before.third
        }
    }

    @Test
    fun `the Daily Sales dialogs are told apart by their titles`() {
        assertEquals(CONFIRM_EXCHANGE, extrasDialogScreen("Confirm Exchange"))
        assertEquals(EXCHANGE_COMPLETE, extrasDialogScreen("Exchange Complete"))
    }

    @Test
    fun `one-row swipes read the whole list, and a repeated page is its end`() {
        val scan = mergeSalePages(salePages(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 9))
        assertTrue(scan.complete)
        assertEquals(LIVE_SALE, scan.rows)
        val three = mergeSalePages(salePages(0, 1, 3, 4, 6, 8, 8, size = 3))
        assertTrue(three.complete)
        assertEquals(LIVE_SALE, three.rows)
    }

    @Test
    fun `a swipe that skips past every row on screen leaves the read incomplete`() {
        val scan = mergeSalePages(salePages(0, 1, 4, 5))
        assertFalse(scan.complete)
        assertTrue(scan.gap)
        assertEquals(LIVE_SALE.take(3), scan.rows)
        assertFalse(mergeSalePages(salePages(0, 1, 2)).complete, "never reached the end")
        assertFalse(mergeSalePages(emptyList()).complete)
    }

    @Test
    fun `sold-out rows keep pages overlapping`() {
        val rows = LIVE_SALE.mapIndexed { i, row -> if (i in 3..6) row.copy(buyable = false) else row }
        val scan = mergeSalePages(salePages(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 9, rows = rows))
        assertTrue(scan.complete)
        assertEquals(7, scan.buyable.size)
        assertEquals(rows, scan.rows)
    }

    @Test
    fun `Monies amounts keep every digit and drop separators`() {
        assertEquals(5_683_820, parseMonies("5,683,820"))
        assertEquals(108_000, parseMonies("108,000"))
        assertNull(parseMonies(""))
        assertNull(parseMonies("Monies"))
    }
}
