package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode
import com.steve1316.uma_android_automation.bot.misc.LegendScreen.DAILY_PROGRAMS
import com.steve1316.uma_android_automation.bot.misc.LegendScreen.HOME
import com.steve1316.uma_android_automation.bot.misc.LegendScreen.ITEMS_SELECTED
import com.steve1316.uma_android_automation.bot.misc.LegendScreen.LEGEND_GRID
import com.steve1316.uma_android_automation.bot.misc.LegendScreen.NEXT
import com.steve1316.uma_android_automation.bot.misc.LegendScreen.RACE_DETAILS
import com.steve1316.uma_android_automation.bot.misc.LegendScreen.RACE_LIST
import com.steve1316.uma_android_automation.bot.misc.LegendScreen.RACE_PLAYING
import com.steve1316.uma_android_automation.bot.misc.LegendScreen.RACE_PREP
import com.steve1316.uma_android_automation.bot.misc.LegendScreen.RACE_TAB
import com.steve1316.uma_android_automation.bot.misc.LegendScreen.RUNNER_SELECTION
import com.steve1316.uma_android_automation.bot.misc.LegendScreen.SPENDING_DIALOG
import com.steve1316.uma_android_automation.bot.misc.LegendScreen.UNKNOWN
import com.steve1316.uma_android_automation.bot.misc.LegendTarget.CANCEL
import com.steve1316.uma_android_automation.bot.misc.LegendTarget.CONFIRM
import com.steve1316.uma_android_automation.bot.misc.LegendTarget.DAILY_PROGRAM_TILE
import com.steve1316.uma_android_automation.bot.misc.LegendTarget.HOME_TAB
import com.steve1316.uma_android_automation.bot.misc.LegendTarget.ITEMS_RACE
import com.steve1316.uma_android_automation.bot.misc.LegendTarget.LEGEND_CARD
import com.steve1316.uma_android_automation.bot.misc.LegendTarget.RACE
import com.steve1316.uma_android_automation.bot.misc.LegendTarget.SKIP
import com.steve1316.uma_android_automation.bot.misc.LegendTarget.TAP_PROMPT
import com.steve1316.uma_android_automation.bot.misc.LegendTarget.TOP_RACE_ROW
import com.steve1316.uma_android_automation.bot.misc.LegendTarget.VIEW_RESULTS
import com.steve1316.uma_android_automation.bot.misc.LegendTarget.WATCH_RACE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** A legend card in the fake grid: its logo centre on the page and whether it still shows "to Star Unlock". */
private data class FakeCard(val name: String, val unlockable: Boolean, val x: Int, val y: Int)

/** Two columns of cards per page; the strip sits below and right of the logo as on the game's cards. */
private fun page(vararg cards: Pair<String, Boolean>): List<FakeCard> = cards.mapIndexed { i, (name, unlockable) -> FakeCard(name, unlockable, 270 + 540 * (i % 2), 500 + 400 * (i / 2)) }

/** A scripted game: each tap moves between screens the way the Global client does. */
private class FakeLegendGame(
    val card: LegendCardStatus = LegendCardStatus(found = true, done = false, tickets = 1),
    /** The grid, top page first. */
    val pages: List<List<FakeCard>> = listOf(page("Special Week" to true, "Silence Suzuka" to true)),
    /** The real ticket count; a Race! with none opens a purchase dialog. */
    val realTickets: Int = 1,
    /** The game checks the ticket at the Items Selected Race! instead of the Race Details one. */
    val ticketCheckedAtItemsRace: Boolean = false,
    val viewResultsLocked: Boolean? = false,
    val lineUp: Boolean = true,
    /** After this tap the game shows a screen the step does not know, for good. */
    val unknownAfter: LegendTarget? = null,
    /** Shows a spending dialog over the next screen after every one of these taps. */
    val spendingAfter: LegendTarget? = null,
    /** Where Cancel on that stacked dialog leads; null keeps the screen beneath. */
    val spendingCancelTo: LegendScreen? = null,
    /** Whether a card tap opens its race list; false leaves the grid as it is. */
    val listOpens: Boolean = true,
) : LegendDevice {
    var screen: LegendScreen = HOME
    val taps = mutableListOf<LegendTarget>()
    var raced: String? = null
    var pageIndex = 0
    private var tappedCard: FakeCard? = null
    private var splash = false
    private var previous: LegendScreen = HOME
    private var stackedDialog = false

    override fun screen(): LegendScreen = if (stackedDialog) SPENDING_DIALOG else screen

    override fun press(target: LegendTarget): Boolean {
        if (stackedDialog) {
            if (target != CANCEL && target != LegendTarget.CLOSE) error("tapped $target on a spending dialog")
            taps += target
            stackedDialog = false
            spendingCancelTo?.let { screen = it }
            return true
        }
        if (screen == SPENDING_DIALOG && target != CANCEL && target != LegendTarget.CLOSE) error("tapped $target on a spending dialog")
        val next = transition(target) ?: return false
        taps += target
        previous = screen
        screen = if (target == unknownAfter) UNKNOWN else next
        if (target == spendingAfter) stackedDialog = true
        return true
    }

    private fun transition(target: LegendTarget): LegendScreen? =
        when {
            screen == HOME && target == LegendTarget.RACE_TAB -> RACE_TAB
            screen == RACE_TAB && target == DAILY_PROGRAM_TILE -> DAILY_PROGRAMS
            screen == DAILY_PROGRAMS && target == LEGEND_CARD -> if (card.found) LEGEND_GRID else null
            screen == RACE_LIST && target == TOP_RACE_ROW -> RACE_DETAILS
            screen == RACE_DETAILS && target == RACE -> if (realTickets > 0 || ticketCheckedAtItemsRace) RUNNER_SELECTION else SPENDING_DIALOG
            screen == RUNNER_SELECTION && target == CONFIRM -> if (lineUp) NEXT else ITEMS_SELECTED
            screen == NEXT && target == LegendTarget.NEXT -> if (raced == null) ITEMS_SELECTED else LEGEND_GRID
            screen == ITEMS_SELECTED && target == ITEMS_RACE && realTickets == 0 -> ITEMS_SELECTED.also { stackedDialog = true }
            screen == ITEMS_SELECTED && target == ITEMS_RACE -> {
                raced = tappedCard?.name
                RACE_PREP
            }
            screen == RACE_PREP && target == VIEW_RESULTS -> if (viewResultsLocked != false) RACE_PREP else UNKNOWN.also { splash = true }
            screen == RACE_PREP && target == WATCH_RACE -> RACE_PLAYING
            screen == RACE_PLAYING && target == SKIP -> UNKNOWN.also { splash = true }
            screen == UNKNOWN && splash && target == TAP_PROMPT -> NEXT.also { splash = false }
            screen == SPENDING_DIALOG && target == CANCEL -> previous
            screen == RACE_DETAILS && target == CANCEL -> RACE_LIST
            screen == ITEMS_SELECTED && target == CANCEL -> RACE_LIST
            screen == RUNNER_SELECTION && target == LegendTarget.BACK -> RACE_DETAILS
            (screen == RACE_TAB || screen == DAILY_PROGRAMS || screen == LEGEND_GRID || screen == RACE_LIST) && target == HOME_TAB -> HOME
            else -> null
        }

    override fun legendCard(): LegendCardStatus = card

    override fun gridPage(preferred: String?): LegendGridPage {
        if (screen != LEGEND_GRID) return LegendGridPage(null, emptyList(), "")
        val cards = pages[pageIndex]
        val logo = cards.firstOrNull { it.name == preferred }?.let { it.x to it.y }
        return LegendGridPage(logo, cards.filter { it.unlockable }.map { it.x + 100 to it.y + 75 }, "page$pageIndex")
    }

    override fun scrollGrid(down: Boolean) {
        pageIndex = (pageIndex + if (down) 1 else -1).coerceIn(0, pages.size - 1)
    }

    override fun tapGrid(point: Pair<Int, Int>) {
        check(screen() == LEGEND_GRID) { "tapped the grid on ${screen()}" }
        tappedCard = pages[pageIndex].firstOrNull { kotlin.math.abs(point.first - it.x) <= 160 && kotlin.math.abs(point.second - it.y) <= 120 } ?: error("tapped no card at $point")
        if (listOpens) screen = RACE_LIST
    }

    override fun viewResultsLocked(): Boolean? = viewResultsLocked

    override fun handlePopup(): Boolean = false

    override fun pause(seconds: Double) {}
}

class DailyLegendRaceTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun run(game: FakeLegendGame, preferred: String = DailyLegendRace.ANY_LEGEND): String = DailyLegendRace(game, preferred).run()

    @Test
    fun `the free ticket is spent on the first legend in reading order that can still unlock a star`() {
        val game = FakeLegendGame(pages = listOf(page("Special Week" to false, "Silence Suzuka" to true, "Tokai Teio" to true)))
        assertEquals("raced ${DailyLegendRace.FIRST_UNLOCKABLE}", run(game))
        assertEquals("Silence Suzuka", game.raced)
        assertEquals(1, game.taps.count { it == ITEMS_RACE }, "one Race! on Items Selected")
        assertEquals(listOf(LegendTarget.RACE_TAB, DAILY_PROGRAM_TILE, LEGEND_CARD, TOP_RACE_ROW, RACE, CONFIRM, LegendTarget.NEXT, ITEMS_RACE), game.taps.take(8))
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `the preferred legend is raced when it can still unlock a star, even on a later page`() {
        val game = FakeLegendGame(pages = listOf(page("Special Week" to true, "Silence Suzuka" to true), page("Oguri Cap" to true, "Gold Ship" to true)))
        assertEquals("raced Oguri Cap", run(game, preferred = "Oguri Cap"))
        assertEquals("Oguri Cap", game.raced)
    }

    @Test
    fun `a preferred legend with every star unlocked gives way to the first unlockable one from the top`() {
        val game = FakeLegendGame(pages = listOf(page("Special Week" to false, "Silence Suzuka" to true), page("Oguri Cap" to false, "Gold Ship" to true)))
        assertEquals("raced ${DailyLegendRace.FIRST_UNLOCKABLE}", run(game, preferred = "Oguri Cap"))
        assertEquals("Silence Suzuka", game.raced)
    }

    @Test
    fun `a preferred legend missing from the grid gives way to the first unlockable one`() {
        val game = FakeLegendGame(pages = listOf(page("Special Week" to true), page("Gold Ship" to true)))
        assertEquals("raced ${DailyLegendRace.FIRST_UNLOCKABLE}", run(game, preferred = "Vodka"))
        assertEquals("Special Week", game.raced)
    }

    @Test
    fun `nothing is raced when every legend has every star unlocked`() {
        val game = FakeLegendGame(pages = listOf(page("Special Week" to false, "Silence Suzuka" to false), page("Gold Ship" to false)))
        assertEquals("no \"to Star Unlock\" strip found on 2 grid page(s)", run(game))
        assertNull(game.raced)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `a card whose race list never opens backs out with nothing spent`() {
        val game = FakeLegendGame(listOpens = false)
        assertEquals("the race list of ${DailyLegendRace.FIRST_UNLOCKABLE} did not open", run(game))
        assertEquals(listOf(LegendTarget.RACE_TAB, DAILY_PROGRAM_TILE, LEGEND_CARD, HOME_TAB), game.taps)
        assertNull(game.raced)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `a counter at 0 stops before the card is opened`() {
        val game = FakeLegendGame(card = LegendCardStatus(found = true, done = false, tickets = 0))
        assertEquals("no ticket left", run(game))
        assertFalse(LEGEND_CARD in game.taps)
        assertNull(game.raced)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `a card that says Done for today stops before it is opened`() {
        val game = FakeLegendGame(card = LegendCardStatus(found = true, done = true, tickets = 0))
        assertEquals("done for today", run(game))
        assertFalse(LEGEND_CARD in game.taps)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `a card that is not on the Daily Programs screen stops the step there`() {
        val game = FakeLegendGame(card = LegendCardStatus(found = false, done = false, tickets = null))
        assertEquals("Daily Legend Races card not found", run(game))
        assertEquals(listOf(LegendTarget.RACE_TAB, DAILY_PROGRAM_TILE, HOME_TAB), game.taps)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `an unreadable counter with no ticket reaches only a purchase dialog, which is cancelled`() {
        val game = FakeLegendGame(card = LegendCardStatus(found = true, done = false, tickets = null), realTickets = 0)
        val note = run(game)
        assertTrue(note.startsWith("Runner Selection did not open; cancelled 1 spending dialog(s)"), note)
        assertNull(game.raced)
        assertEquals(CANCEL, game.taps[game.taps.indexOf(RACE) + 1], "the dialog gets Cancel and nothing else")
        assertFalse(ITEMS_RACE in game.taps)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `a spending dialog stacked on Items Selected gets Cancel and never a Race!`() {
        val game = FakeLegendGame(spendingAfter = LegendTarget.NEXT, spendingCancelTo = RACE_LIST)
        val note = run(game)
        assertEquals("Items Selected did not open; cancelled 1 spending dialog(s)", note)
        assertFalse(ITEMS_RACE in game.taps)
        assertEquals(CANCEL, game.taps[game.taps.indexOf(LegendTarget.NEXT) + 1])
        assertNull(game.raced)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `a dialog that keeps coming back after Race! on Items Selected is cancelled and the re-presses are capped`() {
        val game = FakeLegendGame(realTickets = 0, ticketCheckedAtItemsRace = true)
        val note = run(game)
        assertTrue(note.startsWith("Race! on Items Selected did not start the race for"), note)
        assertNull(game.raced)
        assertEquals(1 + DailyLegendRace.MAX_ITEMS_RACE_TAPS, game.taps.count { it == ITEMS_RACE })
        assertTrue(note.endsWith("cancelled ${1 + DailyLegendRace.MAX_ITEMS_RACE_TAPS} spending dialog(s)"), note)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `a dangerous dialog met by a follow-up ends the run as an error, a user Stop does not`() {
        val dangerous = MiscTask.followUpStopResult(InterruptedException("Stopping bot due to a dangerous dialog: purchase_carats"))
        assertTrue(dangerous is TaskResult.Error && dangerous.code == TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, "$dangerous")
        assertNull(MiscTask.followUpStopResult(InterruptedException("Stopped by the user")))
        assertNull(MiscTask.followUpStopResult(InterruptedException()))
    }

    @Test
    fun `a locked View Results is watched with Skip and still ends on Home`() {
        val game = FakeLegendGame(viewResultsLocked = true)
        assertEquals("raced ${DailyLegendRace.FIRST_UNLOCKABLE}", run(game))
        assertTrue(WATCH_RACE in game.taps)
        assertTrue(SKIP in game.taps)
        assertEquals(HOME, game.screen)
    }

    @Test
    fun `a View Results that stays on screen is treated as locked`() {
        val game = FakeLegendGame(viewResultsLocked = null, lineUp = false)
        assertEquals("raced ${DailyLegendRace.FIRST_UNLOCKABLE}", run(game))
        assertEquals(DailyLegendRace.VIEW_RESULTS_TAPS, game.taps.count { it == VIEW_RESULTS })
        assertTrue(WATCH_RACE in game.taps)
    }

    @Test
    fun `a result splash is advanced at its TAP prompt only after the race was committed`() {
        val game = FakeLegendGame()
        run(game)
        assertTrue(game.taps.indexOf(TAP_PROMPT) > game.taps.indexOf(ITEMS_RACE))
        assertEquals(1, game.taps.count { it == TAP_PROMPT })
    }

    @Test
    fun `a screen the step does not know before the race ends it without a Race!`() {
        val game = FakeLegendGame(unknownAfter = CONFIRM)
        val note = run(game)
        assertTrue(note.startsWith("the line-up did not appear"), note)
        assertFalse(ITEMS_RACE in game.taps)
        assertNull(game.raced)
    }

    @Test
    fun `a strip belongs to the logo just above and left of it`() {
        assertTrue(stripBelongsToLogo(270 to 500, 370 to 575))
        assertFalse(stripBelongsToLogo(270 to 500, 910 to 575), "the next column's strip")
        assertFalse(stripBelongsToLogo(270 to 500, 370 to 975), "the next row's strip")
    }

    @Test
    fun `reading order is top row first, then left to right`() {
        assertEquals(100 to 510, firstInReadingOrder(listOf(600 to 500, 100 to 510, 50 to 900)))
        assertNull(firstInReadingOrder(emptyList()))
    }

    @Test
    fun `the settings page offers exactly the legends the bot has logo templates for`() {
        val kotlin = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/components/Label.kt").readText()
        val logos = Regex(""""([^"]+)" to legendLogo\(Template\("components/label/daily_legend_([a-z_]+)"""").findAll(kotlin).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertEquals(29, logos.size)
        for ((_, file) in logos) assertTrue(repoFile("android/app/src/main/assets/images/components/label/daily_legend_$file.png").isFile, file)
        val settings = repoFile("src/pages/Settings/index.tsx").readText()
        val list = settings.substring(settings.indexOf("const DAILY_LEGEND_RACE_LEGENDS = ["), settings.indexOf("] as const", settings.indexOf("const DAILY_LEGEND_RACE_LEGENDS = [")))
        val offered = Regex("\"([^\"]+)\"").findAll(list).map { it.groupValues[1] }.toList()
        assertEquals(listOf(DailyLegendRace.ANY_LEGEND) + logos.map { it.first }, offered)
        val defaults = repoFile("src/context/BotStateContext.tsx").readText()
        assertTrue(defaults.contains("dailyLegendRaceLegend: \"${DailyLegendRace.ANY_LEGEND}\","), "the default is the sentinel the Kotlin side knows")
    }

    @Test
    fun `the step runs after the mode and before the rewards and Club step, and a Stop skips the rest`() {
        val src = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/misc/MiscTask.kt").readText().replace("\r\n", "\n")
        val legend = src.indexOf("runFollowUp(\"[LEGEND_RACE]\")")
        val extras = src.indexOf("?: runFollowUp(\"[DAILY_EXTRAS]\")")
        assertTrue(legend in 1 until extras, "the extras run only when the Legend step returned no interrupt")
        assertTrue(src.contains("return stop?.let { followUpStopResult(it) } ?: result"))
        assertTrue(src.indexOf("if (result !is TaskResult.Success) return result") < legend, "only after a successful mode")
    }
}
