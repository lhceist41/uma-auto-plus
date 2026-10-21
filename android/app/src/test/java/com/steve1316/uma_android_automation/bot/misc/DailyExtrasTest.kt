package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.CLUB
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.CONFIRM_REQUEST
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.HOME
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.ITEM_REQUEST_ENDED
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.ITEM_REQUEST_OTHER
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.ITEM_REQUEST_PICKER
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.MISSIONS
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.PRESENTS
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.REWARDS_COLLECTED
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.SPENDING_DIALOG
import com.steve1316.uma_android_automation.bot.misc.ExtrasScreen.UNKNOWN
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.BACK
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.CANCEL
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.CLOSE
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.CLUB_BUTTON
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.COLLECT_ALL
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.CONFIRM
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.HOME_TAB
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.ITEM_REQUEST_BUTTON
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.MISSIONS_BUTTON
import com.steve1316.uma_android_automation.bot.misc.ExtrasTarget.PRESENTS_BUTTON
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A scripted game: each tap moves between screens the way the Global client does on the captured frames. */
private class FakeGame(
    var screen: ExtrasScreen = HOME,
    var request: String = "none",
    var missionRewards: Boolean = true,
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
) : ExtrasDevice {
    val taps = mutableListOf<ExtrasTarget>()
    var requested: String? = null
    var shoesTab: Boolean? = null
    var collectedMissions = false
    var collectedPresents = 0
    private var spendingShowing = false
    private var rewardsFrom: ExtrasScreen = HOME
    private var picked: String? = null

    override fun screen(): ExtrasScreen = if (spendingShowing) SPENDING_DIALOG else screen

    override fun press(target: ExtrasTarget): Boolean {
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
        return true
    }

    private fun transition(target: ExtrasTarget): ExtrasScreen? =
        when (screen to target) {
            HOME to CLUB_BUTTON -> CLUB
            HOME to MISSIONS_BUTTON -> MISSIONS
            HOME to PRESENTS_BUTTON -> PRESENTS
            CLUB to HOME_TAB -> HOME
            CLUB to ITEM_REQUEST_BUTTON ->
                when (request) {
                    "ended" -> ITEM_REQUEST_ENDED
                    "running" -> ITEM_REQUEST_OTHER
                    else -> ITEM_REQUEST_PICKER
                }
            ITEM_REQUEST_ENDED to CLOSE -> CLUB.also { request = "none" }
            ITEM_REQUEST_OTHER to CLOSE -> CLUB
            ITEM_REQUEST_PICKER to CANCEL -> CLUB
            CONFIRM_REQUEST to CANCEL -> ITEM_REQUEST_PICKER
            CONFIRM_REQUEST to CONFIRM ->
                CLUB.also {
                    requested = picked
                    request = "running"
                }
            MISSIONS to COLLECT_ALL -> if (missionRewards) REWARDS_COLLECTED.also { rewardsFrom = MISSIONS } else MISSIONS
            MISSIONS to BACK -> HOME
            PRESENTS to COLLECT_ALL -> if (presentBatches > 0) REWARDS_COLLECTED.also { rewardsFrom = PRESENTS } else PRESENTS
            PRESENTS to CLOSE -> HOME
            REWARDS_COLLECTED to CLOSE ->
                rewardsFrom.also {
                    if (it == MISSIONS) {
                        missionRewards = false
                        collectedMissions = true
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
            MISSIONS -> missionRewards
            PRESENTS -> presentBatches > 0
            else -> null
        }

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
        assertEquals(listOf(CLUB_BUTTON, ITEM_REQUEST_BUTTON, CLOSE, HOME_TAB), game.taps)
        assertEquals(listOf("Club: a request is still running"), notes)
    }

    @Test
    fun `lowest held picks the sash with the fewest held`() {
        val rows = listOf(PickerRow("Japan Cup Winner's Sash", 40), PickerRow("Kashiwa Kinen Winner's Sash", 7), PickerRow("Arima Kinen Winner's Sash", 9))
        val game = FakeGame(sashes = PickerScan(rows, complete = true))
        runClub(game, item = ClubRequestItems.LOWEST_HELD_SASH)
        assertEquals("Kashiwa Kinen Winner's Sash", game.requested)
        assertEquals(false, game.shoesTab)
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
        assertEquals(listOf("Presents: collected", "Missions: collected"), notes)
        assertFalse(CLUB_BUTTON in game.taps)
    }

    @Test
    fun `nothing to collect taps no Collect All and still returns to Home`() {
        val game = FakeGame(missionRewards = false, presentBatches = 0)
        val notes = DailyExtras(game, collectRewards = true, clubRequest = false, requestItem = "Dirt Racing Shoes").run()
        assertFalse(COLLECT_ALL in game.taps)
        assertEquals(listOf(PRESENTS_BUTTON, CLOSE, MISSIONS_BUTTON, BACK), game.taps)
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

    @Test
    fun `a sash read twice with different counts stays incomplete and requests nothing`() {
        val pages = livePages(0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 28, 28, 28) + listOf(listOf(LIVE_SASHES[2].first to "999"))
        val scan = mergePickerPages(pages)
        assertEquals(34, scan.rows.size)
        assertFalse(scan.complete)
        assertNull(lowestHeldItem(scan))
        val game = FakeGame(sashes = scan)
        runClub(game, item = ClubRequestItems.LOWEST_HELD_SASH)
        assertNull(game.requested)
        assertFalse(CONFIRM in game.taps)
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
}
