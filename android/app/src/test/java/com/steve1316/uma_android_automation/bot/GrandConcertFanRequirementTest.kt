package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.GrandConcertFanRequirement.MatchKind
import com.steve1316.uma_android_automation.bot.GrandConcertFanRequirement.Result
import com.steve1316.uma_android_automation.bot.GrandConcertFanRequirement.Type
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The pure current-scope Grand Concert fan-requirement helper and its authoritative-writer decision.
 * Everything runs on synthetic JSON and the shipped asset. The point is the current-scope period
 * rule (earliest requirement by turn including met ones, no lookahead past a met scope), the
 * gate-min ambiguity, the same-turn goal/gate handling, the unknown/missing behavior, and the
 * facts-vs-legacy precedence.
 */
@DisplayName("Grand Concert fan requirement (current-scope activation)")
class GrandConcertFanRequirementTest {
    // ---- synthetic-facts builders ----

    private fun facts(vararg characters: String): GrandConcertFanFacts =
        GrandConcertFanFacts.parse(
            """{ "schemaVersion": 1, "universalCompletedRaceFanFloor": 7, "characters": { ${characters.joinToString(",")} } }""",
        ) ?: error("payload should parse")

    private fun character(name: String, goals: String = "", gates: String = ""): String =
        """ "$name": { "fanGoals": [$goals], "mandatoryRaces": [$gates] } """

    private fun goal(turn: Int, target: Int): String = """{ "turn": $turn, "targetFans": $target }"""

    private fun gate(turn: Int, isChoice: Boolean, vararg options: Pair<String, Int>): String =
        """{ "turn": $turn, "isChoice": $isChoice, "options": [${options.joinToString(",") { (n, f) -> """{ "raceName": "$n", "fansNeeded": $f }""" }}] }"""

    /** A Copano-shaped character: fan goal 3000 by 24, gate 350 at 31, gate 12000 at 47. */
    private fun copanoShaped(name: String = "Tester"): GrandConcertFanFacts =
        facts(
            character(
                name,
                goals = goal(24, 3000),
                gates = "${gate(31, false, "Fukuryu Stakes" to 350)}, ${gate(47, false, "Champions Cup" to 12000)}",
            ),
        )

    private fun active(result: Result): Result.Active {
        assertTrue(result is Result.Active, "expected Active but was $result")
        return result as Result.Active
    }

    private fun assetFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/assets")
            if (a.isDirectory) return File(a, relative)
            val b = File(dir, "android/app/src/main/assets")
            if (b.isDirectory) return File(b, relative)
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the assets root from ${System.getProperty("user.dir")}")
    }

    // ---- current-scope: fan goal ----

    @Test
    @DisplayName("turn 14, 1151 fans: the current fan goal (3000 by 24) is active with deficit 1849")
    fun copanoTurn14Unmet() {
        val a = active(GrandConcertFanRequirement.evaluate(copanoShaped(), "Tester", currentTurn = 14, currentFans = 1151))
        assertEquals(Type.FAN_GOAL, a.type)
        assertEquals(3000, a.targetFans)
        assertEquals(24, a.requirementTurn)
        assertEquals(1849, a.deficit)
        assertEquals(MatchKind.EXACT, a.match)
        assertTrue(a.goalUnmet)
        assertFalse(a.gateUnmet)
    }

    @Test
    @DisplayName("turn 14, goal already met: inactive, and never jumps ahead to the 12000 gate")
    fun copanoTurn14Satisfied() {
        val r = GrandConcertFanRequirement.evaluate(copanoShaped(), "Tester", currentTurn = 14, currentFans = 3000)
        assertTrue(r is Result.Inactive, "expected Inactive but was $r")
        assertEquals("current-scope-met", (r as Result.Inactive).reason)
    }

    @Test
    @DisplayName("current scope met while a much larger later requirement is unmet: no lookahead activation")
    fun currentScopeMetNoLookahead() {
        // Turn 25: the goal at 24 has passed; the earliest requirement at or after 25 is the 350 gate
        // at 31, which 3000 fans satisfies. The 12000 gate at 47 must not activate yet.
        val r = GrandConcertFanRequirement.evaluate(copanoShaped(), "Tester", currentTurn = 25, currentFans = 3000)
        assertTrue(r is Result.Inactive, "expected Inactive but was $r")
    }

    @Test
    @DisplayName("turn 31 gate met: inactive")
    fun turn31GateMet() {
        val r = GrandConcertFanRequirement.evaluate(copanoShaped(), "Tester", currentTurn = 31, currentFans = 3000)
        assertTrue(r is Result.Inactive, "expected Inactive but was $r")
    }

    @Test
    @DisplayName("turn 32: the 31 gate has passed, so the 12000 gate at 47 becomes the active scope")
    fun turn32GateActive() {
        val a = active(GrandConcertFanRequirement.evaluate(copanoShaped(), "Tester", currentTurn = 32, currentFans = 3000))
        assertEquals(Type.MANDATORY_GATE, a.type)
        assertEquals(12000, a.targetFans)
        assertEquals(47, a.requirementTurn)
        assertEquals(9000, a.deficit)
    }

    // ---- gate-min ambiguity ----

    @Test
    @DisplayName("ambiguous choice gate below the minimum option: active at the minimum threshold")
    fun gateBelowMinActive() {
        val f = facts(character("Tester", gates = gate(30, true, "A" to 1250, "B" to 1750)))
        val a = active(GrandConcertFanRequirement.evaluate(f, "Tester", currentTurn = 28, currentFans = 1000))
        assertEquals(Type.MANDATORY_GATE, a.type)
        assertEquals(1250, a.targetFans)
        assertEquals(30, a.requirementTurn)
        assertEquals(250, a.deficit)
    }

    @Test
    @DisplayName("ambiguous choice gate between min and max: inactive (the minimum is already satisfiable)")
    fun gateBetweenMinMaxInactive() {
        val f = facts(character("Tester", gates = gate(30, true, "A" to 1250, "B" to 1750)))
        val r = GrandConcertFanRequirement.evaluate(f, "Tester", currentTurn = 28, currentFans = 1500)
        assertTrue(r is Result.Inactive, "expected Inactive but was $r")
    }

    // ---- same-turn goal + gate ----

    @Test
    @DisplayName("same-turn goal and gate both met: inactive")
    fun sameTurnBothMet() {
        val f = facts(character("Tester", goals = goal(30, 3000), gates = gate(30, false, "R" to 4500)))
        val r = GrandConcertFanRequirement.evaluate(f, "Tester", currentTurn = 20, currentFans = 5000)
        assertTrue(r is Result.Inactive, "expected Inactive but was $r")
    }

    @Test
    @DisplayName("same-turn: only the goal is unmet -> active as the goal")
    fun sameTurnGoalUnmetOnly() {
        val f = facts(character("Tester", goals = goal(30, 5000), gates = gate(30, false, "R" to 4500)))
        val a = active(GrandConcertFanRequirement.evaluate(f, "Tester", currentTurn = 20, currentFans = 4700))
        assertEquals(Type.FAN_GOAL, a.type)
        assertEquals(5000, a.targetFans)
        assertEquals(300, a.deficit)
        assertTrue(a.goalUnmet)
        assertFalse(a.gateUnmet)
    }

    @Test
    @DisplayName("same-turn: only the gate is unmet -> active as the gate")
    fun sameTurnGateUnmetOnly() {
        val f = facts(character("Tester", goals = goal(30, 3000), gates = gate(30, false, "R" to 5000)))
        val a = active(GrandConcertFanRequirement.evaluate(f, "Tester", currentTurn = 20, currentFans = 4700))
        assertEquals(Type.MANDATORY_GATE, a.type)
        assertEquals(5000, a.targetFans)
        assertEquals(300, a.deficit)
        assertFalse(a.goalUnmet)
        assertTrue(a.gateUnmet)
    }

    @Test
    @DisplayName("same-turn: both unmet, gate is the larger threshold -> active at the gate, both flags true")
    fun sameTurnBothUnmetLargerGate() {
        val f = facts(character("Tester", goals = goal(30, 3000), gates = gate(30, false, "R" to 5000)))
        val a = active(GrandConcertFanRequirement.evaluate(f, "Tester", currentTurn = 20, currentFans = 2000))
        assertEquals(Type.MANDATORY_GATE, a.type)
        assertEquals(5000, a.targetFans)
        assertEquals(3000, a.deficit)
        assertTrue(a.goalUnmet)
        assertTrue(a.gateUnmet)
    }

    @Test
    @DisplayName("same-turn: both unmet, goal is the larger threshold -> active at the goal, both flags true")
    fun sameTurnBothUnmetLargerGoal() {
        val f = facts(character("Tester", goals = goal(30, 6000), gates = gate(30, false, "R" to 5000)))
        val a = active(GrandConcertFanRequirement.evaluate(f, "Tester", currentTurn = 20, currentFans = 2000))
        assertEquals(Type.FAN_GOAL, a.type)
        assertEquals(6000, a.targetFans)
        assertEquals(4000, a.deficit)
        assertTrue(a.goalUnmet)
        assertTrue(a.gateUnmet)
    }

    // ---- no requirement / unknown / missing ----

    @Test
    @DisplayName("no requirement at or after the current turn: inactive")
    fun noRemainingRequirement() {
        val r = GrandConcertFanRequirement.evaluate(copanoShaped(), "Tester", currentTurn = 73, currentFans = 100)
        assertTrue(r is Result.Inactive, "expected Inactive but was $r")
        assertEquals("no-remaining-requirement", (r as Result.Inactive).reason)
    }

    @Test
    @DisplayName("unmatched trainee: unknown (never an invented target)")
    fun unknownIdentity() {
        val r = GrandConcertFanRequirement.evaluate(copanoShaped(), "Nobody At All", currentTurn = 14, currentFans = 1151)
        assertTrue(r is Result.Unknown, "expected Unknown but was $r")
        assertEquals("trainee-unmatched", (r as Result.Unknown).reason)
    }

    @Test
    @DisplayName("ambiguous normalized identity: unknown")
    fun ambiguousIdentity() {
        val f =
            facts(
                character("TM Opera O", goals = goal(24, 3000)),
                character("T.M. Opera O", goals = goal(24, 3000)),
            )
        val r = GrandConcertFanRequirement.evaluate(f, "tmopera o", currentTurn = 14, currentFans = 1151)
        assertTrue(r is Result.Unknown, "expected Unknown but was $r")
        assertEquals("trainee-ambiguous", (r as Result.Unknown).reason)
    }

    @Test
    @DisplayName("missing facts asset: unknown")
    fun missingFacts() {
        val r = GrandConcertFanRequirement.evaluate(null, "Copano Rickey", currentTurn = 14, currentFans = 1151)
        assertTrue(r is Result.Unknown, "expected Unknown but was $r")
        assertEquals("facts-unavailable", (r as Result.Unknown).reason)
    }

    @Test
    @DisplayName("blank trainee name: unknown")
    fun blankName() {
        val f = copanoShaped()
        assertEquals("trainee-name-empty", (GrandConcertFanRequirement.evaluate(f, "", 14, 1151) as Result.Unknown).reason)
        assertEquals("trainee-name-empty", (GrandConcertFanRequirement.evaluate(f, "   ", 14, 1151) as Result.Unknown).reason)
    }

    @Test
    @DisplayName("normalized (non-exact) identity match still resolves the requirement, tagged NORMALIZED")
    fun normalizedMatch() {
        val a = active(GrandConcertFanRequirement.evaluate(copanoShaped("Copano Rickey"), "copano  rickey!", 14, 1151))
        assertEquals(MatchKind.NORMALIZED, a.match)
        assertEquals(3000, a.targetFans)
    }

    // ---- the shipped asset: a real Copano regression ----

    @Test
    @DisplayName("shipped asset: Copano turn 14 / 1151 fans -> active goal 3000@24 deficit 1849")
    fun shippedAssetCopanoActive() {
        val parsed = GrandConcertFanFacts.parse(assetFile("gc_fan_runtime.json").readText()) ?: error("shipped asset should parse")
        val a = active(GrandConcertFanRequirement.evaluate(parsed, "Copano Rickey", currentTurn = 14, currentFans = 1151))
        assertEquals(Type.FAN_GOAL, a.type)
        assertEquals(3000, a.targetFans)
        assertEquals(24, a.requirementTurn)
        assertEquals(1849, a.deficit)
        assertEquals(MatchKind.EXACT, a.match)
    }

    @Test
    @DisplayName("shipped asset: Copano turn 14 with 3000 fans -> inactive, and turn 32 below 12000 -> active gate")
    fun shippedAssetCopanoScopeTransitions() {
        val parsed = GrandConcertFanFacts.parse(assetFile("gc_fan_runtime.json").readText()) ?: error("shipped asset should parse")
        assertTrue(GrandConcertFanRequirement.evaluate(parsed, "Copano Rickey", 14, 3000) is Result.Inactive)
        val a = active(GrandConcertFanRequirement.evaluate(parsed, "Copano Rickey", 32, 500))
        assertEquals(Type.MANDATORY_GATE, a.type)
        assertEquals(12000, a.targetFans)
        assertEquals(47, a.requirementTurn)
        assertEquals(11500, a.deficit)
    }

    // ---- authoritative writer precedence (the Racing.checkRacingRequirements arm) ----

    @Test
    @DisplayName("Active overrides a false template result (the BK dead-template regression)")
    fun writerActiveOverridesTemplateFalse() {
        val activeResult = Result.Active(Type.FAN_GOAL, 3000, 24, 1849, MatchKind.EXACT, goalUnmet = true, gateUnmet = false)
        assertTrue(GrandConcertFanRequirement.resolveHasFanRequirement(legacyValue = false, result = activeResult))
    }

    @Test
    @DisplayName("Inactive clears a stale true (a met current scope must not keep forcing)")
    fun writerInactiveClearsStaleTrue() {
        val inactive = Result.Inactive("current-scope-met", MatchKind.EXACT)
        assertFalse(GrandConcertFanRequirement.resolveHasFanRequirement(legacyValue = true, result = inactive))
    }

    @Test
    @DisplayName("Unknown preserves the legacy/template value in both directions")
    fun writerUnknownPreservesLegacy() {
        val unknown = Result.Unknown("trainee-unmatched")
        assertFalse(GrandConcertFanRequirement.resolveHasFanRequirement(legacyValue = false, result = unknown))
        assertTrue(GrandConcertFanRequirement.resolveHasFanRequirement(legacyValue = true, result = unknown))
    }
}
