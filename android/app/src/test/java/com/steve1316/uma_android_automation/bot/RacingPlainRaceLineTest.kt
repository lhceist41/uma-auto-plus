package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.Racing.Companion.PLAIN_MAIDEN_REASON
import com.steve1316.uma_android_automation.bot.Racing.Companion.PLAIN_OPEN_RACE_REASON
import com.steve1316.uma_android_automation.bot.Racing.Companion.plainAgendaReason
import com.steve1316.uma_android_automation.bot.Racing.Companion.plainDeadlineLabel
import com.steve1316.uma_android_automation.bot.Racing.Companion.plainFanReason
import com.steve1316.uma_android_automation.bot.Racing.Companion.plainFanSnapshotReason
import com.steve1316.uma_android_automation.bot.Racing.Companion.plainGoalRaceReason
import com.steve1316.uma_android_automation.bot.Racing.Companion.plainPlannedReason
import com.steve1316.uma_android_automation.bot.Racing.Companion.plainRaceLine
import com.steve1316.uma_android_automation.bot.Racing.Companion.plainRaceNames
import com.steve1316.uma_android_automation.bot.Racing.Companion.plainRequirementReason
import com.steve1316.uma_android_automation.bot.Racing.Companion.plainSmartReason
import com.steve1316.uma_android_automation.bot.Racing.Companion.plainTrackblazerReason
import com.steve1316.uma_android_automation.bot.Racing.RaceData
import com.steve1316.uma_android_automation.types.RaceGrade
import com.steve1316.uma_android_automation.utils.LogStreamServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/** The one plain line a player reads for each committed race: which race and why. */
@DisplayName("Racing Plain Race Line Tests")
class RacingPlainRaceLineTest {
    private fun requirementReason(
        scenarioFanReason: String? = null,
        hasFanRequirement: Boolean = false,
        fanEmergency: Boolean = false,
        fanGoalTarget: Int? = null,
        turnsToGoal: Int? = null,
        fans: Int = 15124,
        hasResultPtsRequirement: Boolean = false,
        trophyCriteria: String? = null,
        popupNote: String? = null,
        forceRacing: Boolean = false,
        farmingInterval: Int? = null,
    ) = plainRequirementReason(
        scenarioFanReason,
        hasFanRequirement,
        fanEmergency,
        fanGoalTarget,
        turnsToGoal,
        fans,
        hasResultPtsRequirement,
        trophyCriteria,
        popupNote,
        forceRacing,
        farmingInterval,
    )

    private fun shippedFacts(): GrandConcertFanFacts {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val assets = File(dir, "src/main/assets")
            if (assets.isDirectory) return GrandConcertFanFacts.parse(File(assets, "gc_fan_runtime.json").readText()) ?: error("shipped asset should parse")
            dir = dir?.parentFile
        }
        error("could not locate the assets root")
    }

    /** The fan check's snapshot for Hishi Amazon, read from the shipped route facts. */
    private fun hishi(turn: Int, fans: Int) = GrandConcertFanPressure.evaluate(shippedFacts(), "Hishi Amazon", turn, fans)

    private fun snapshotReason(
        type: GrandConcertFanPressure.RequirementType,
        target: Int = 20000,
        turn: Int = 56,
    ) = plainFanSnapshotReason(
        hishi(24, 15124).copy(
            effectiveType = type,
            effectiveTarget = target,
            effectiveTurn = turn,
            effectiveExact = true,
            tightestLaterUsableTurns = null,
            tightestLaterDeficit = null,
        ),
    )

    private fun race(name: String, grade: String = "G3") = RaceData(name, grade, 3800, name, "Turf", "Mile", 25)

    @Test
    @DisplayName("Grand Concert fan gate names the target, the turn with its date, and the current fans")
    fun grandConcertGateLine() {
        val line = plainRaceLine("Keisei Hai", RaceGrade.G3, 3800, requirementReason(snapshotReason(GrandConcertFanPressure.RequirementType.MANDATORY_GATE), hasFanRequirement = true))
        assertEquals(
            "[RACE] Racing Keisei Hai (G3, 3,800 fans): Grand Concert needs 20,000 fans by turn 56 (SENIOR YEAR LATE APRIL); you have 15,124.",
            line,
        )
    }

    @Test
    @DisplayName("A Grand Concert fan goal reads as the career fan goal, not the gate")
    fun grandConcertGoalLine() {
        assertEquals(
            "the career fan goal needs 20,000 fans by turn 56 (SENIOR YEAR LATE APRIL); you have 15,124",
            requirementReason(snapshotReason(GrandConcertFanPressure.RequirementType.FAN_GOAL), hasFanRequirement = true),
        )
    }

    @Test
    @DisplayName("A fan requirement read without route facts gives the target and turns left when known")
    fun genericFanRequirement() {
        assertEquals("the career fan goal needs 4,500 fans, due in 3 turn(s); you have 1,114", requirementReason(hasFanRequirement = true, fanGoalTarget = 4500, turnsToGoal = 3, fans = 1114))
        assertEquals("the career fan goal needs more fans; you have 900", requirementReason(fanEmergency = true, fans = 900))
    }

    @Test
    @DisplayName("An unread fan count is said so")
    fun unreadFanCount() {
        assertEquals("the career fan goal needs more fans; your fan count is unread", plainFanReason("the career fan goal", null, null, null, 0))
    }

    @Test
    @DisplayName("Result Points, trophy, Force Racing and interval reasons each carry their own facts")
    fun otherRequirementReasons() {
        assertEquals("the career goal still needs Result Points, due in 2 turn(s)", requirementReason(hasResultPtsRequirement = true, turnsToGoal = 2))
        assertEquals("the career goal still needs Result Points", requirementReason(hasResultPtsRequirement = true))
        assertEquals("the career goal needs a win (G1 criteria)", requirementReason(trophyCriteria = "G1 criteria"))
        assertEquals("Force Racing is on, so the bot races whenever a race is open", requirementReason(forceRacing = true))
        assertEquals("fan farming is on and this turn falls on your racing interval (every 3 turn(s))", requirementReason(farmingInterval = 3))
        assertEquals("your racing settings allow a race this turn", requirementReason())
    }

    @Test
    @DisplayName("The goal outranks Force Racing and the interval")
    fun goalOutranksSettings() {
        assertEquals(
            "the career goal needs a win (G3 or above criteria)",
            requirementReason(trophyCriteria = "G3 or above criteria", forceRacing = true, farmingInterval = 2),
        )
    }

    @Test
    @DisplayName("Mandatory goal, agenda and racing plan reasons carry the turn and its date")
    fun turnDatedReasons() {
        assertEquals("it is the mandatory career goal race on JUNIOR YEAR LATE JUNE (Turn 12)", plainGoalRaceReason(12))
        assertEquals("it is a scheduled race (from your in-game race agenda or a career goal) on CLASSIC YEAR EARLY JANUARY (Turn 25)", plainAgendaReason(25))
        assertEquals("your racing plan lists it for CLASSIC YEAR EARLY JANUARY (Turn 25)", plainPlannedReason(25))
    }

    @Test
    @DisplayName("Finale goal races are dated as the finale, not as Senior Late December")
    fun finaleDates() {
        assertEquals("it is the mandatory career goal race on Finale Qualifier (Turn 73)", plainGoalRaceReason(73))
        assertEquals("it is the mandatory career goal race on Finale Semi-Final (Turn 74)", plainGoalRaceReason(74))
        assertEquals("it is a scheduled race (from your in-game race agenda or a career goal) on Finale Finals (Turn 75)", plainAgendaReason(75))
        assertEquals("your racing plan lists it for Finale Qualifier (Turn 73)", plainPlannedReason(73))
    }

    @Test
    @DisplayName("A deadline never carries the (Turn N) token the log viewer reads as the current turn")
    fun deadlineHasNoTurnToken() {
        assertEquals("turn 56 (SENIOR YEAR LATE APRIL)", plainDeadlineLabel(56))
        assertEquals("turn 73", plainDeadlineLabel(73))
        val line = plainRaceLine("Keisei Hai", RaceGrade.G3, 3800, requirementReason(snapshotReason(GrandConcertFanPressure.RequirementType.MANDATORY_GATE), hasFanRequirement = true))
        assertFalse(line.contains("(Turn"), line)
        assertNull(LogStreamServer.parseDateInfo(line), "the dashboard turn parser must ignore the race line")
        assertNotNull(LogStreamServer.parseDateInfo("Detected something (Turn 56)"), "control: the parser does read a (Turn N) token")
    }

    @Test
    @DisplayName("Turn 24 with 11,258 fans names the live 25,000-by-turn-48 gate, not the 4,500 gate met long before")
    fun liveGateAtTurn24() {
        val atTurn24 = hishi(24, 11258)
        assertEquals(25000, atTurn24.effectiveTarget)
        assertEquals(48, atTurn24.effectiveTurn)
        assertEquals(
            "Grand Concert needs 25,000 fans by turn 48 (CLASSIC YEAR LATE DECEMBER); you have 11,258",
            plainFanSnapshotReason(atTurn24),
        )
        assertEquals(plainFanSnapshotReason(atTurn24), requirementReason(plainFanSnapshotReason(atTurn24), hasFanRequirement = true, fans = 11258))
    }

    @Test
    @DisplayName("The Grand Concert campaign records the fan check's snapshot and falls back to a fresh one on a turn that skipped the check")
    fun campaignHandsOffTheSnapshot() {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        var source: String? = null
        repeat(5) {
            val file = File(dir, "src/main/java/com/steve1316/uma_android_automation/bot/campaigns/GrandConcert.kt")
            if (source == null && file.isFile) source = file.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        val gc = source ?: error("could not locate GrandConcert.kt")
        val deferral = gc.substring(gc.indexOf("override fun considerFanRaceDeferral"))
        assertTrue(deferral.indexOf("lastFanSnapshot = snapshot") in 0 until deferral.indexOf("GrandConcertFanPolicy.decide("), "the snapshot is kept before the decision")
        val handoff = gc.substring(gc.indexOf("override fun fanRaceReasonForLine"))
        assertTrue(handoff.contains("it.currentTurn == date.day"), "only this turn's snapshot is reused")
        assertTrue(handoff.contains("GrandConcertFanPressure.evaluate(fanFacts, trainee.name, date.day, trainee.fans)"), "a turn without the check evaluates fresh")
    }

    @Test
    @DisplayName("When the later goal is the one that forced the race, the line adds it with the numbers the fan check used")
    fun laterGoalBinds() {
        val hishiTurn14 = hishi(14, 1801)
        assertEquals(23199, hishiTurn14.tightestLaterDeficit)
        val later = GrandConcertFanPressure.bindingLaterRequirement(hishiTurn14)
        assertEquals(23199, later?.first)
        val inputs = GrandConcertFanPressure.policyInputs(hishiTurn14, recentFanRacesPaidLow = false)
        assertEquals(later?.second?.minus(1), inputs.turnsUntilDeadline)
        assertEquals(
            GrandConcertFanPolicy.FanRaceDecision.FORCE_RACE,
            GrandConcertFanPolicy.decide(true, inputs.turnsUntilDeadline, inputs.racesStillNeeded, concertBehindPace = true),
        )
        assertEquals(
            "Grand Concert needs 4,500 fans by turn 31 (CLASSIC YEAR EARLY APRIL); you have 1,801; a later fan requirement may need up to 23,199 more fans with ${later?.second} free race turns left",
            plainFanSnapshotReason(hishiTurn14),
        )
    }

    @Test
    @DisplayName("A later choice gate is worded as an upper bound: Mejiro Ardan at turn 14 needs 5,699 more for Kikuka Sho, not 18,199")
    fun laterChoiceGateIsAnUpperBound() {
        val ardan = GrandConcertFanPressure.evaluate(shippedFacts(), "Mejiro Ardan", 14, 1801)
        assertEquals(6000, ardan.effectiveTarget)
        assertEquals(30, ardan.effectiveTurn)
        assertEquals(18199, ardan.tightestLaterDeficit)
        assertEquals(18199, GrandConcertFanPressure.bindingLaterRequirement(ardan)?.first)
        assertEquals(
            "the career fan goal needs 6,000 fans by turn 30 (CLASSIC YEAR LATE MARCH); you have 1,801; a later fan requirement may need up to 18,199 more fans with ${ardan.tightestLaterUsableTurns} free race turns left",
            plainFanSnapshotReason(ardan),
        )
    }

    @Test
    @DisplayName("After two low-paying fan races the policy weighs neither goal, so the line adds no later goal")
    fun lowPayoutsAddNoLaterGoal() {
        val hishiTurn14 = hishi(14, 1801)
        assertNull(GrandConcertFanPressure.bindingLaterRequirement(hishiTurn14, recentFanRacesPaidLow = true))
        assertEquals(null, GrandConcertFanPressure.policyInputs(hishiTurn14, recentFanRacesPaidLow = true).turnsUntilDeadline)
        assertEquals(
            "Grand Concert needs 4,500 fans by turn 31 (CLASSIC YEAR EARLY APRIL); you have 1,801",
            plainFanSnapshotReason(hishiTurn14, recentFanRacesPaidLow = true),
        )
    }

    @Test
    @DisplayName("When the effective requirement binds, or no later one exists, the line stays as it was")
    fun effectiveGoalBinds() {
        val hishiTurn14 = hishi(14, 1801)
        val roomy = hishiTurn14.copy(tightestLaterUsableTurns = 500)
        assertNull(GrandConcertFanPressure.bindingLaterRequirement(roomy))
        val roomyInputs = GrandConcertFanPressure.policyInputs(roomy, recentFanRacesPaidLow = false)
        assertEquals(roomy.usableRaceTurnsInWindow?.minus(1), roomyInputs.turnsUntilDeadline)
        assertEquals("Grand Concert needs 4,500 fans by turn 31 (CLASSIC YEAR EARLY APRIL); you have 1,801", plainFanSnapshotReason(roomy))
        val none = hishiTurn14.copy(tightestLaterUsableTurns = null, tightestLaterDeficit = null)
        assertNull(GrandConcertFanPressure.bindingLaterRequirement(none))
        assertEquals("Grand Concert needs 4,500 fans by turn 31 (CLASSIC YEAR EARLY APRIL); you have 1,801", plainFanSnapshotReason(none))
    }

    @Test
    @DisplayName("The 1 fan sentinel before the first fan read is said to be unread")
    fun fanSentinelIsUnread() {
        assertEquals(
            "Grand Concert needs 4,500 fans by turn 31 (CLASSIC YEAR EARLY APRIL); your fan count is unread",
            plainFanSnapshotReason(hishi(14, 1801).copy(currentFans = 1, tightestLaterUsableTurns = null, tightestLaterDeficit = null)),
        )
    }

    @Test
    @DisplayName("A requirement with no exact target, or none left, says so instead of naming an old one")
    fun failClosedFanReasons() {
        val base = hishi(24, 11258)
        val choiceGate = GrandConcertFanPressure.evaluate(shippedFacts(), "Mejiro Ardan", 36, 7000)
        assertEquals(false, choiceGate.effectiveExact)
        assertEquals(
            "Grand Concert needs at least 7,500 fans by turn 44 (CLASSIC YEAR LATE OCTOBER) (the exact figure is not known); you have 7,000",
            plainFanSnapshotReason(choiceGate),
        )
        assertEquals(
            "a fan requirement armed earlier no longer applies at your current fans; you have 11,258",
            plainFanSnapshotReason(base.copy(effectiveType = GrandConcertFanPressure.RequirementType.NONE, effectiveTarget = null, effectiveTurn = null)),
        )
        assertEquals(
            "Grand Concert's fan requirement could not be worked out, so the bot races to be safe; you have 11,258",
            plainFanSnapshotReason(GrandConcertFanPressure.evaluate(shippedFacts(), "No Such Trainee", 24, 11258)),
        )
    }

    @Test
    @DisplayName("A race the game's popup forced gets that reason, after a goal and before settings")
    fun popupReason() {
        val popup = "the game showed its insufficient-fans popup"
        assertEquals(popup, requirementReason(popupNote = popup, farmingInterval = 3))
        assertEquals(popup, requirementReason(popupNote = popup, forceRacing = true))
        assertEquals("the career goal needs a win (G1 criteria)", requirementReason(popupNote = popup, trophyCriteria = "G1 criteria"))
    }

    @Test
    @DisplayName("Smart racing gives its score and candidate count")
    fun smartReason() {
        assertEquals("smart racing scored it highest of 4 candidate(s), with a score of 83.5", plainSmartReason("83.5", 4))
    }

    @Test
    @DisplayName("Trackblazer names a Rival pick or its best pick, with the consecutive race count")
    fun trackblazerReason() {
        assertEquals("Trackblazer picks a Rival race first (2 race(s) in a row so far)", plainTrackblazerReason(true, 2, null))
        assertEquals(
            "Trackblazer races whenever it can and picks by Rival, then prediction stars, then grade, then fans (0 race(s) in a row so far)",
            plainTrackblazerReason(false, 0, null),
        )
        assertEquals(
            "the career fan goal needs more fans; you have 900 (1 race(s) in a row so far)",
            plainTrackblazerReason(false, 1, requirementReason(fanEmergency = true, fans = 900)),
        )
    }

    @Test
    @DisplayName("An unread name and grade are called unread, never guessed")
    fun unreadRace() {
        assertEquals("[RACE] Racing an unread race: $PLAIN_OPEN_RACE_REASON.", plainRaceLine(null, null, 0, PLAIN_OPEN_RACE_REASON))
        assertEquals("[RACE] Racing an unread race (Maiden): $PLAIN_MAIDEN_REASON.", plainRaceLine(null, RaceGrade.MAIDEN, 0, PLAIN_MAIDEN_REASON))
        assertEquals("[RACE] Racing the Unity Cup showdown: x.", plainRaceLine("the Unity Cup showdown", null, 0, "x"))
    }

    @Test
    @DisplayName("Grades read as the game writes them and fans group by thousands")
    fun gradeAndFansText() {
        assertEquals("[RACE] Racing A (Pre-OP, 1,000,000 fans): x.", plainRaceLine("A", RaceGrade.PRE_OP, 1000000, "x"))
        assertEquals("[RACE] Racing A (G1, 950 fans): x.", plainRaceLine("A", RaceGrade.G1, 950, "x"))
        assertEquals("[RACE] Racing A (Finale): x.", plainRaceLine("A", RaceGrade.FINALE, 0, "x"))
    }

    @Test
    @DisplayName("A lookup that stayed ambiguous names every candidate and an empty one names none")
    fun raceNames() {
        assertEquals("Keisei Hai", plainRaceNames(listOf(race("Keisei Hai"))))
        assertEquals("Keisei Hai or Nikkei Sho", plainRaceNames(listOf(race("Keisei Hai"), race("Nikkei Sho"), race("Keisei Hai"))))
        assertNull(plainRaceNames(emptyList()))
    }
}
