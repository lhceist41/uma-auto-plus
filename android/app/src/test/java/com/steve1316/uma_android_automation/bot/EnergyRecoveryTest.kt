package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.Mood
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/** A low-energy recovery that does not move the turn is reported as failed, retried once with Rest alone, then stops the run instead of looping. */
@DisplayName("Energy recovery must move the turn")
class EnergyRecoveryTest {
    private class Stopped(message: String) : RuntimeException(message)

    private class Screen(
        var outingStarts: Boolean = false,
        var restFound: Boolean = true,
        var turnMoves: Boolean? = false,
        var raceDay: Boolean = false,
    ) {
        val calls = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        fun recover(day: Int, unmoved: UnmovedEnergyRecoveries): Boolean =
            recoverEnergyAndConfirmTurn(
                day = day,
                unmoved = unmoved,
                tryOuting = {
                    calls += "outing"
                    outingStarts
                },
                rest = {
                    calls += "rest"
                    restFound
                },
                raceDay = { raceDay },
                turnMoved = {
                    calls += "turnMoved"
                    turnMoves
                },
                warn = { warnings += it },
                stop = { throw Stopped(it) },
            )
    }

    @Nested
    @DisplayName("the decision")
    inner class Decision {
        @Test
        fun `an outing back-out followed by Rest on an unchanged screen is not a success`() {
            val screen = Screen(outingStarts = false, turnMoves = false)
            val unmoved = UnmovedEnergyRecoveries()
            assertFalse(screen.recover(30, unmoved))
            assertEquals(listOf("outing", "rest", "turnMoved"), screen.calls)
            assertEquals(1, unmoved.on(30))
            assertTrue(screen.warnings.single().contains("unchanged"))
        }

        @Test
        fun `a second try on the same date skips the outing and goes straight to Rest`() {
            val screen = Screen(turnMoves = false)
            val unmoved = UnmovedEnergyRecoveries()
            screen.recover(30, unmoved)
            screen.calls.clear()
            screen.turnMoves = true
            assertTrue(screen.recover(30, unmoved))
            assertEquals(listOf("rest", "turnMoved"), screen.calls)
        }

        @Test
        fun `a second unmoved recovery on the same date stops the run instead of looping`() {
            val screen = Screen(turnMoves = false)
            val unmoved = UnmovedEnergyRecoveries()
            assertFalse(screen.recover(30, unmoved))
            val stop = assertThrows(Stopped::class.java) { screen.recover(30, unmoved) }
            assertTrue(stop.message!!.contains("turn 30"))
            assertFalse(screen.calls.drop(3).contains("outing"))
        }

        @Test
        fun `a missing Rest button on an unchanged screen counts as unmoved, and the second one stops`() {
            val screen = Screen(restFound = false)
            val unmoved = UnmovedEnergyRecoveries()
            assertFalse(screen.recover(30, unmoved))
            assertEquals(listOf("outing", "rest", "turnMoved"), screen.calls)
            assertThrows(Stopped::class.java) { screen.recover(30, unmoved) }
        }

        @Test
        fun `an outing that read as not started but opened its event is a move, not a missing Rest`() {
            // Live t43, t52, t57: "Popup still open ... Treating the date as completed", no Rest button, then the Light Hello event.
            val screen = Screen(outingStarts = false, restFound = false, turnMoves = true)
            val unmoved = UnmovedEnergyRecoveries()
            assertTrue(screen.recover(43, unmoved))
            assertEquals(0, unmoved.on(43))
            assertTrue(screen.warnings.isEmpty())
        }

        @Test
        fun `a missing Rest button on a race day does not look at the turn, and a repeat on the same turn is bounded`() {
            val screen = Screen(restFound = false, raceDay = true)
            val unmoved = UnmovedEnergyRecoveries()
            assertFalse(screen.recover(43, unmoved))
            assertFalse("turnMoved" in screen.calls)
            assertTrue(screen.warnings.first().contains("race day"))
            // A ribbon the turn-start read keeps missing would send the bot back here; the third time stops instead of looping.
            assertFalse(screen.recover(43, unmoved))
            assertThrows(Stopped::class.java) { screen.recover(43, unmoved) }
        }

        @Test
        fun `a declined outing keeps the energy proof, so a dropped Rest after it is proven unmoved, not unconfirmed`() {
            val unmoved = UnmovedEnergyRecoveries()
            var mainScreenChecked = false
            val started = outingStarted(handlerStarted = false) {
                mainScreenChecked = true
                true
            }
            assertFalse(started)
            assertTrue(mainScreenChecked)
            val baseline = 20.takeUnless { started }
            val moved =
                recoverEnergyAndConfirmTurn(
                    day = 12,
                    unmoved = unmoved,
                    tryOuting = { false },
                    rest = { true },
                    raceDay = { false },
                    turnMoved = { confirmTurnMoved(12, baseline, Mood.NORMAL, read = { TurnScreen(mainScreen = true, day = 12, energy = 20) }, wait = {}) },
                    warn = {},
                    stop = { throw Stopped(it) },
                )
            assertFalse(moved, "proven unmoved returns false; with the baseline dropped it read as unconfirmed (true)")
        }

        @Test
        fun `an outing counts as started when the handler says so or when its event hides the main screen`() {
            assertTrue(outingStarted(handlerStarted = true) { error("not asked when the handler started it") })
            assertTrue(outingStarted(handlerStarted = false) { false })
            assertFalse(outingStarted(handlerStarted = false) { true })
        }

        @Test
        fun `a started outing or Rest that moves the turn succeeds and counts nothing`() {
            val unmoved = UnmovedEnergyRecoveries()
            val outing = Screen(outingStarts = true, turnMoves = true)
            assertTrue(outing.recover(30, unmoved))
            assertEquals(listOf("outing", "turnMoved"), outing.calls)
            assertTrue(Screen(turnMoves = true).recover(30, unmoved))
            assertEquals(0, unmoved.on(30))
        }

        @Test
        fun `an unconfirmed recovery counts as spent, skips the outing next, and the third one in a row stops`() {
            val screen = Screen(turnMoves = null)
            val unmoved = UnmovedEnergyRecoveries()
            assertTrue(screen.recover(30, unmoved))
            assertTrue(screen.warnings.single().contains("could not be confirmed"))
            screen.calls.clear()
            assertTrue(screen.recover(30, unmoved))
            assertEquals(listOf("rest", "turnMoved"), screen.calls)
            val stop = assertThrows(Stopped::class.java) { screen.recover(30, unmoved) }
            assertTrue(stop.message!!.contains("after 3 tries"))
        }

        @Test
        fun `one unconfirmed and one unchanged recovery do not stop yet, a third try does`() {
            val screen = Screen(turnMoves = null)
            val unmoved = UnmovedEnergyRecoveries()
            assertTrue(screen.recover(30, unmoved))
            screen.turnMoves = false
            assertFalse(screen.recover(30, unmoved))
            screen.turnMoves = null
            assertThrows(Stopped::class.java) { screen.recover(30, unmoved) }
        }

        @Test
        fun `a proven move or an executed training ends the run of failures, on the same date too`() {
            val screen = Screen(turnMoves = false)
            val unmoved = UnmovedEnergyRecoveries()
            screen.recover(30, unmoved)
            screen.turnMoves = true
            assertTrue(screen.recover(30, unmoved))
            assertEquals(0, unmoved.on(30))
            screen.turnMoves = false
            screen.recover(30, unmoved)
            unmoved.clear()
            screen.calls.clear()
            assertFalse(screen.recover(30, unmoved))
            assertEquals(listOf("outing", "rest", "turnMoved"), screen.calls)
        }

        @Test
        fun `a new date starts the count again`() {
            val screen = Screen(turnMoves = false)
            val unmoved = UnmovedEnergyRecoveries()
            screen.recover(30, unmoved)
            screen.calls.clear()
            assertFalse(screen.recover(31, unmoved))
            assertEquals(listOf("outing", "rest", "turnMoved"), screen.calls)
            assertEquals(1, unmoved.on(31))
            assertEquals(1, unmoved.on(30), "an earlier date is not a new turn")
        }
    }

    @Nested
    @DisplayName("the turn check")
    inner class TurnCheck {
        private fun check(
            vararg screens: TurnScreen,
            day: Int = 30,
            energyBefore: Int? = 20,
            moodBefore: Mood? = Mood.NORMAL,
        ): Pair<Boolean?, Int> {
            var reads = 0
            var waits = 0
            val result = confirmTurnMoved(day, energyBefore, moodBefore, read = { screens[minOf(reads++, screens.size - 1)] }, wait = { waits++ })
            assertEquals(reads - 1, waits, "one wait between reads")
            return result to reads
        }

        private val unchanged = TurnScreen(mainScreen = true, day = 30, energy = 21)
        private val transition = TurnScreen(mainScreen = false)

        @Test
        fun `a later date, a training event or risen energy is a moved turn`() {
            assertEquals(true to 1, check(TurnScreen(mainScreen = true, day = 31, energy = 20)))
            assertEquals(true to 1, check(TurnScreen(mainScreen = false, trainingEvent = true)))
            assertEquals(true to 1, check(TurnScreen(mainScreen = true, day = 30, energy = 50)))
            assertEquals(true to 3, check(transition, transition, TurnScreen(mainScreen = true, day = 31)))
        }

        @Test
        fun `energy that did not rise on every read is an unmoved turn, after every read`() {
            assertEquals(false to TURN_CHECK_UNCHANGED_READS, check(unchanged))
            assertEquals(true to 2, check(unchanged, TurnScreen(mainScreen = true, day = 30, energy = 52)))
        }

        @Test
        fun `an event takes precedence over a dialog on the same read, and a dialog alone ends the poll`() {
            assertEquals(true to 1, check(TurnScreen(mainScreen = false, eventScene = true, dialog = true)))
            assertEquals(null to 1, check(TurnScreen(mainScreen = false, trainingEvent = false, eventScene = false, dialog = true)))
        }

        @Test
        fun `without a pre-tap mood a mood read proves nothing`() {
            val betterMood = TurnScreen(mainScreen = true, day = 12, energy = 20, mood = Mood.GREAT)
            assertEquals(null to TURN_CHECK_MAX_READS, check(betterMood, day = 12, energyBefore = null, moodBefore = null))
        }

        @Test
        fun `unchanged reads count only in a row on a settled main screen`() {
            assertEquals(null to TURN_CHECK_MAX_READS, check(unchanged, transition, unchanged, transition, unchanged, transition))
            assertEquals(false to 5, check(unchanged, transition, unchanged, unchanged, unchanged))
            assertEquals(null to TURN_CHECK_MAX_READS, check(unchanged, unchanged, transition))
        }

        @Test
        fun `a dialog left open is not proof the turn moved, and waiting longer cannot settle it`() {
            assertEquals(null to 1, check(TurnScreen(mainScreen = false, dialog = true)))
        }

        @Test
        fun `a transition is waited out until the screen settles`() {
            // Live t18: a Rest's connection wait and result scene outlasted the old four reads.
            val settled = check(transition, transition, transition, transition, transition, transition, TurnScreen(mainScreen = true, day = 31, energy = 96))
            assertEquals(true to 7, settled)
            assertEquals(null to TURN_CHECK_MAX_READS, check(transition))
        }

        @Test
        fun `an event cutscene after the tap is a moved turn`() {
            // Live t18, t22, t64: the Rest led to an event cutscene that only the main loop advances.
            assertEquals(true to 3, check(transition, transition, TurnScreen(mainScreen = false, eventScene = true)))
        }

        @Test
        fun `an outing is judged by mood, date or its event, never by energy`() {
            val sameEnergy = TurnScreen(mainScreen = true, day = 12, energy = 20, mood = Mood.NORMAL)
            assertEquals(null to TURN_CHECK_MAX_READS, check(sameEnergy, day = 12, energyBefore = null))
            assertEquals(true to 1, check(TurnScreen(mainScreen = true, day = 12, energy = 20, mood = Mood.GOOD), day = 12, energyBefore = null))
        }

        @Test
        fun `the same date with an unreadable energy bar proves nothing either way`() {
            assertEquals(null to TURN_CHECK_MAX_READS, check(TurnScreen(mainScreen = true, day = 30)))
            assertEquals(null to TURN_CHECK_MAX_READS, check(unchanged, energyBefore = null))
            assertEquals(null to TURN_CHECK_MAX_READS, check(unchanged, transition))
        }

        @Test
        fun `a backward date or a read fallback is not a move`() {
            // Turn 74 or 75 whose finale read fails comes back as 73.
            assertEquals(false to TURN_CHECK_UNCHANGED_READS, check(TurnScreen(mainScreen = true, day = 73, energy = 20), day = 74))
            // A failed pre-debut turns-left read comes back as 12.
            assertEquals(false to TURN_CHECK_UNCHANGED_READS, check(TurnScreen(mainScreen = true, day = 12, energy = 20), day = 5))
            assertEquals(null to TURN_CHECK_MAX_READS, check(TurnScreen(mainScreen = true, day = 73), day = 72))
        }
    }

    @Nested
    @DisplayName("a date read that cannot see the turn change")
    inner class StuckDate {
        /** Real turns on a screen whose date read never changes: each Rest that lands raises the energy bar by [restGain]. */
        private fun restsInARow(
            stuckDay: Int,
            rests: Int,
            restGain: Int,
            energyBeforeRest: Int = 5,
        ): List<Boolean> {
            val unmoved = UnmovedEnergyRecoveries()
            var energy = 0
            return (1..rests).map {
                // Each real turn between the rests spends energy again (a low-energy turn is why the bot rests).
                energy = energyBeforeRest
                val before = energy
                recoverEnergyAndConfirmTurn(
                    day = stuckDay,
                    unmoved = unmoved,
                    tryOuting = { false },
                    rest = {
                        energy = (energy + restGain).coerceAtMost(100)
                        true
                    },
                    raceDay = { false },
                    turnMoved = { confirmTurnMoved(stuckDay, before, null, read = { TurnScreen(mainScreen = true, day = stuckDay, energy = energy) }, wait = {}) },
                    warn = {},
                    stop = { throw Stopped(it) },
                )
            }
        }

        @Test
        fun `Grand Concert pre-debut, where every turn reads as 12, never stops on rests that land`() {
            assertEquals(List(5) { true }, restsInARow(stuckDay = 12, rests = 5, restGain = 30))
        }

        @Test
        fun `a finale read stuck on its default 73 never stops on rests that land`() {
            assertEquals(List(3) { true }, restsInARow(stuckDay = 73, rests = 3, restGain = 30))
        }

        @Test
        fun `on a stuck date, Rest taps that never land still stop`() {
            assertThrows(Stopped::class.java) { restsInARow(stuckDay = 12, rests = 2, restGain = 0) }
        }

        @Test
        fun `on a stuck date, two Rests that land on a nearly full bar do not stop`() {
            // 95 to 100 cannot show the proof margin, so each Rest is unconfirmed (spent), never proven unmoved.
            assertEquals(List(2) { true }, restsInARow(stuckDay = 12, rests = 2, restGain = 30, energyBeforeRest = 95))
        }

        @Test
        fun `a read flipping to a fallback date and back does not reset the bound`() {
            val screen = Screen(turnMoves = false)
            val unmoved = UnmovedEnergyRecoveries()
            assertFalse(screen.recover(5, unmoved))
            assertEquals(1, unmoved.on(12), "a fallback 12 is not a later turn")
            assertThrows(Stopped::class.java) { screen.recover(12, unmoved) }

            val unconfirmed = Screen(turnMoves = null)
            val bound = UnmovedEnergyRecoveries()
            unconfirmed.recover(5, bound)
            unconfirmed.recover(12, bound)
            assertThrows(Stopped::class.java) { unconfirmed.recover(5, bound) }
        }
    }

    @Nested
    @DisplayName("the campaign wiring")
    inner class Wiring {
        private val body by lazy {
            val campaign = sourceFile("bot/Campaign.kt").readText().replace("\r\n", "\n")
            val start = campaign.indexOf("open fun recoverEnergy(")
            campaign.substring(start, campaign.indexOf("\n    }\n", start))
        }

        @Test
        fun `Rest is found on a fresh capture after the outing popup backs out`() {
            val backOut = body.indexOf("bitmap = game.imageUtils.getSourceBitmap()")
            assertTrue(backOut > body.indexOf("handleRecreationDate("), "the recapture follows the outing attempt")
            assertTrue(body.indexOf("game.waitForLoading()") in body.indexOf("handleRecreationDate(") until backOut, "it waits for the popup to close first")
            assertTrue(body.contains("ButtonRest.click(game.imageUtils, sourceBitmap = bitmap)"))
            assertFalse(body.contains("sourceBitmap = sourceBitmap"), "nothing is found on the pre-popup capture")
        }

        @Test
        fun `a recreation date that cannot start is closed and not reopened on the same turn`() {
            val campaign = sourceFile("bot/Campaign.kt").readText().replace("\r\n", "\n")
            val handler = campaign.indexOf("open fun handleRecreationDate(")
            val failed = campaign.indexOf("Failed to find a way to start the recreation date", handler)
            assertTrue(failed > handler)
            // The open popup would take the next Rest tap as a backdrop tap.
            val branch = campaign.substring(failed, campaign.indexOf("\n                }\n", failed))
            assertTrue(branch.contains("if (!ButtonCancel.click("), "the branch closes the popup and knows when it could not")
            assertTrue(branch.contains("MessageLog.w("), "a missing Cancel is named in the log")
            assertTrue(branch.contains("recreationAttemptFailedThisTurn = true"), "the branch latches the failure for the turn")

            fun body(function: String): String {
                val start = campaign.indexOf(function)
                return campaign.substring(start, campaign.indexOf("\n    }\n", start))
            }

            fun dateGuard(function: String): String = body(function).lines().single { it.contains("IconRecreationDate.check(") }
            val energyGuard = dateGuard("open fun recoverEnergy(")
            assertTrue(energyGuard.contains("|| recreationAttemptFailedThisTurn ||"), "energy recovery skips the failed date")
            assertTrue(dateGuard("open fun recoverMood(").contains("!recreationAttemptFailedThisTurn"), "mood recovery skips the failed date")

            // The same mood pass falls through to the trainee recreation instead of reporting a recovery after the Cancel.
            val mood = body("open fun recoverMood(")
            val traineeBranch = mood.indexOf("} else", mood.indexOf("Successfully recovered mood via recreation date"))
            assertTrue(mood.substring(traineeBranch, mood.indexOf('\n', traineeBranch)).contains("recreationAttemptFailedThisTurn"))
            // That tap waits out the popup's close and finds Recreation on a fresh capture, and the log does not call a failed date completed.
            val recreationTap = mood.indexOf("ButtonRecreation.click(game.imageUtils")
            assertTrue(mood.indexOf("game.wait(game.dialogWaitDelay)", traineeBranch) in traineeBranch until recreationTap, "it settles before the tap")
            assertTrue(mood.indexOf("game.imageUtils.getSourceBitmap()", traineeBranch) in traineeBranch until recreationTap, "it taps on a fresh capture")
            assertFalse(mood.substring(recreationTap, mood.indexOf('\n', recreationTap)).contains("sourceBitmap = sourceBitmap"))
            assertTrue(mood.substring(traineeBranch).contains("could not be started"), "a failed date is named as such")
        }

        @Test
        fun `success goes through the turn check and the stop uses the campaign breakpoint`() {
            assertTrue(body.contains("recoverEnergyAndConfirmTurn("))
            assertTrue(body.contains("turnMoved = { turnMovedSince(day, energyBefore.takeUnless { outingOpened }, moodBefore).also { moved = it } }"))
            // Only a started outing drops the energy proof; a declined one is decided by whether the main screen came back.
            assertFalse(body.contains(".also { outingOpened = true }"))
            assertTrue(body.contains("outingOpened = outingStarted(handlerStarted = false) { checkMainScreen() }"))
            assertTrue(body.contains("throw CampaignBreakpointException(reason)"))
        }

        @Test
        fun `a pass whose date did not change still refreshes the energy the failure-chance check reads`() {
            val campaign = sourceFile("bot/Campaign.kt").readText().replace("\r\n", "\n")
            val gate = campaign.indexOf("if (dateChanged || !trainee.bHasUpdatedStats) {")
            val hook = campaign.indexOf("onAfterTurnStartUpdates()\n            } else {", gate)
            assertTrue(hook > gate, "the turn-start update block has an else branch")
            assertTrue(campaign.indexOf("trainee.updateEnergy(game.imageUtils)", hook) in hook until campaign.indexOf("\n            }\n", hook + 40))
        }

        @Test
        fun `the forced Finale Wit training clears the streak like an executed training`() {
            val training = sourceFile("bot/Training.kt").readText().replace("\r\n", "\n")
            val forced = training.indexOf("Successfully forced Wit training during the Finale")
            val fallback = training.indexOf("Could not find Wit training button", forced)
            assertTrue(training.indexOf("campaign.clearUnmovedEnergyRecoveries()", forced) in forced until fallback)
        }

        @Test
        fun `every main-screen pass emits its decision report, so a stop cannot strand a deferred one`() {
            val campaign = sourceFile("bot/Campaign.kt").readText().replace("\r\n", "\n")
            assertFalse(campaign.contains("energyRecoveryLeftTurnOpen"))
            val pass = campaign.substring(campaign.indexOf("val actionExecuted = executeAction(action, cachedScheduledRaceDay)"))
            assertTrue(pass.substringBefore("return actionExecuted").trimEnd().endsWith("decisionTracer?.emit()"))
        }
    }

    private fun sourceFile(relative: String): File = File(kotlinRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
