package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A forced-race popup closed in the current turn, before or after that turn's date read, forces the race for that turn; one from an
 * earlier turn never re-arms.
 */
@DisplayName("Racing popup turn")
class RacingPopupTurnTest {
    /** The Campaign lifecycle around the popup flag: the date read, the popup handlers, the decision that consumes it, and an action that ends the turn. */
    private class Turns {
        val popup = RacingPopupTurn()
        var armed = false
        var dateReadThisTurn = false

        fun popupClosed() {
            armed = true
            popup.closed(dateReadThisTurn)
        }

        fun dateRead(newTurn: Boolean = true) {
            armed = popup.armedAfterDateRead(newTurn, armed)
            dateReadThisTurn = true
        }

        /** decideNextAction: true when the popup sends this turn to the race list. */
        fun decideRaces(): Boolean = armed.also { armed = false }

        fun actionEndsTurn() {
            dateReadThisTurn = false
        }
    }

    @Test
    @DisplayName("a popup closed at the start of a turn, before its date read, still forces that turn's race")
    fun popupBeforeTheRead() {
        val t = Turns()
        t.dateRead()
        assertFalse(t.decideRaces())
        t.actionEndsTurn()
        t.popupClosed()
        t.dateRead()
        assertTrue(t.decideRaces())
    }

    @Test
    @DisplayName("a popup closed after the date read forces the race of the turn already read")
    fun popupAfterTheRead() {
        val t = Turns()
        t.dateRead()
        t.popupClosed()
        assertTrue(t.decideRaces())
    }

    @Test
    @DisplayName("a popup two turns old never re-arms, consumed or not")
    fun oldPopupNeverReArms() {
        val consumed = Turns()
        consumed.popupClosed()
        consumed.dateRead()
        assertTrue(consumed.decideRaces())
        repeat(2) {
            consumed.actionEndsTurn()
            consumed.dateRead()
            assertFalse(consumed.decideRaces())
        }

        val notConsumed = Turns()
        notConsumed.dateRead()
        notConsumed.popupClosed()
        repeat(2) {
            notConsumed.actionEndsTurn()
            notConsumed.dateRead()
            assertFalse(notConsumed.decideRaces(), "a popup from after an earlier read belongs to that turn")
        }
    }

    @Test
    @DisplayName("a same-turn re-read keeps the arm, and its pending popup does not reach the next turn")
    fun sameTurnReRead() {
        val t = Turns()
        t.popupClosed()
        t.dateRead(newTurn = false)
        assertTrue(t.armed)
        t.armed = false
        t.actionEndsTurn()
        t.dateRead()
        assertFalse(t.decideRaces())
    }

    @Test
    @DisplayName("after a restart nothing is armed until a popup of the new session shows")
    fun restart() {
        val fresh = Turns()
        fresh.dateRead()
        assertFalse(fresh.decideRaces())

        val popupOnRestartTurn = Turns()
        popupOnRestartTurn.popupClosed()
        popupOnRestartTurn.dateRead()
        assertTrue(popupOnRestartTurn.decideRaces())
    }

    private fun grandConcertDefers(
        deadline: Int,
        racesNeeded: Int,
    ): Boolean = GrandConcertFanPolicy.decide(true, deadline, racesNeeded, true) == GrandConcertFanPolicy.FanRaceDecision.DEFER_TO_TRAINING

    @Test
    @DisplayName("Grand Concert: a popup does not force the race when the fan policy defers, and races when it does not")
    fun grandConcertFanPolicyStaysInCharge() {
        assertTrue(grandConcertDefers(deadline = 10, racesNeeded = 1))
        assertFalse(RacingPopupTurn.forcesRace(armed = true) { grandConcertDefers(deadline = 10, racesNeeded = 1) })
        assertFalse(grandConcertDefers(deadline = 1, racesNeeded = 1))
        assertTrue(RacingPopupTurn.forcesRace(armed = true) { grandConcertDefers(deadline = 1, racesNeeded = 1) })
    }

    @Test
    @DisplayName("URA and the other scenarios never defer, so a popup races; without a popup the policy is not asked")
    fun otherScenariosRace() {
        assertTrue(RacingPopupTurn.forcesRace(armed = true) { false })
        assertFalse(RacingPopupTurn.forcesRace(armed = false) { error("the fan policy runs only for an armed popup") })
    }

    @Test
    @DisplayName("decideNextAction asks the fan policy at most once, for the popup and the requirement branch alike")
    fun deferralAskedOnce() {
        val campaign = campaignSource()
        assertEquals(1, Regex("""considerFanRaceDeferral\(\)""").findAll(campaign.substringAfter("open fun decideNextAction")).count())
        val lazy = campaign.indexOf("val fanRaceDeferred by lazy { considerFanRaceDeferral() }")
        val popup = campaign.indexOf("RacingPopupTurn.forcesRace(armed = true) { fanRaceDeferred }")
        val requirement = campaign.indexOf("            if (fanRaceDeferred) {")
        assertTrue(lazy in 1 until popup && popup < requirement)
        assertTrue(campaign.indexOf("if (cachedMandatoryRaceDay) {") in 1 until lazy, "a mandatory race day never asks the policy")
    }

    private fun campaignSource(): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, "android/app/src/main/assets").isDirectory) dir = dir.parentFile
        return File(requireNotNull(dir), "android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt").readText().replace("\r\n", "\n")
    }

    @Test
    @DisplayName("every popup handler goes through markRacingPopup, and the date read passes the arm through the helper")
    fun wiring() {
        val campaign = campaignSource()
        assertEquals(1, Regex("""racing\.encounteredRacingPopup = true""").findAll(campaign).count(), "only markRacingPopup sets the flag")
        assertEquals(3, Regex("""markRacingPopup\("the game showed""").findAll(campaign).count())
        assertTrue(campaign.contains("racingPopupTurn.closed(dateReadThisTurn = bHasCheckedDateThisTurn)"))
        val read = campaign.indexOf("val dateChanged = updateDate()")
        val arm = campaign.indexOf("racing.encounteredRacingPopup = racingPopupTurn.armedAfterDateRead(newTurn, racing.encounteredRacingPopup)")
        assertTrue(read in 1 until arm)
        assertTrue(campaign.indexOf("if (newTurn) {") > arm)
        assertFalse(campaign.contains("racing.encounteredRacingPopup = false\n                racing.raceRepeatWarningCheck = false"), "the daily reset no longer drops a popup")
        assertTrue(campaign.contains("racing.encounteredRacingPopup = false\n            if (popupForcesRace) {"), "the decision consumes it whether or not it races")
    }
}
