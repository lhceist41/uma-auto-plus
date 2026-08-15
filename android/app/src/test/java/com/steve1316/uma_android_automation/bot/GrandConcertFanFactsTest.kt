package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.GrandConcertFanPressure.MatchStatus
import com.steve1316.uma_android_automation.bot.GrandConcertFanPressure.RequirementStatus
import com.steve1316.uma_android_automation.bot.GrandConcertFanPressure.RequirementType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The Grand Concert fan-facts reader and the pure fan-pressure calculation. Everything runs on
 * synthetic JSON and the shipped asset; the point is the identity contract, the requirement
 * selection, the exact-vs-ambiguous distinction, and the review-gated fail-closed production path.
 */
@DisplayName("Grand Concert fan facts + pressure")
class GrandConcertFanFactsTest {
    private val payload =
        """
        {
          "schemaVersion": 1,
          "universalCompletedRaceFanFloor": 7,
          "characters": {
            "Copano Rickey": {
              "fanGoals": [{ "turn": 24, "targetFans": 3000 }],
              "mandatoryRaces": [
                { "turn": 31, "isChoice": false, "options": [{ "raceName": "Fukuryu Stakes", "fansNeeded": 350 }] },
                { "turn": 47, "isChoice": false, "options": [{ "raceName": "Champions Cup", "fansNeeded": 12000 }] }
              ]
            },
            "Multi Goal": {
              "fanGoals": [
                { "turn": 37, "targetFans": 5000 },
                { "turn": 45, "targetFans": 9000 },
                { "turn": 48, "targetFans": 12000 }
              ],
              "mandatoryRaces": []
            },
            "Choice Char": {
              "fanGoals": [],
              "mandatoryRaces": [
                { "turn": 30, "isChoice": true, "options": [
                  { "raceName": "Spring Stakes", "fansNeeded": 1750 },
                  { "raceName": "Mainichi Hai", "fansNeeded": 1250 }
                ] }
              ]
            },
            "Same Choice": {
              "fanGoals": [],
              "mandatoryRaces": [
                { "turn": 30, "isChoice": true, "options": [
                  { "raceName": "A", "fansNeeded": 1000 },
                  { "raceName": "B", "fansNeeded": 1000 }
                ] }
              ]
            },
            "TM Opera O": { "fanGoals": [{ "turn": 20, "targetFans": 2000 }], "mandatoryRaces": [] },
            "T.M. Opera O": { "fanGoals": [{ "turn": 20, "targetFans": 2000 }], "mandatoryRaces": [] }
          }
        }
        """.trimIndent()

    private fun facts(): GrandConcertFanFacts = GrandConcertFanFacts.parse(payload) ?: error("payload should parse")

    private fun snapshot(name: String, turn: Int, fans: Int) = GrandConcertFanPressure.evaluate(facts(), name, turn, fans)

    // ---- parsing ----

    @Test
    @DisplayName("a malformed or unsupported-version payload parses to null, never throwing")
    fun parseNullOnBad() {
        assertNull(GrandConcertFanFacts.parse("not json"))
        assertNull(GrandConcertFanFacts.parse("""{ "schemaVersion": 999, "universalCompletedRaceFanFloor": 7, "characters": {} }"""))
        assertNull(GrandConcertFanFacts.parse("""{ "schemaVersion": 1, "universalCompletedRaceFanFloor": 0, "characters": {} }"""))
    }

    // ---- identity ----

    @Test
    @DisplayName("exact canonical name matches verbatim")
    fun identityExact() {
        val m = facts().match("Copano Rickey")
        assertTrue(m is GrandConcertFanFacts.Match.Matched && m.exact && m.canonicalName == "Copano Rickey")
    }

    @Test
    @DisplayName("a unique normalized name matches without fuzzy guessing")
    fun identityNormalized() {
        val m = facts().match("copano  rickey!")
        assertTrue(m is GrandConcertFanFacts.Match.Matched && !m.exact && m.canonicalName == "Copano Rickey")
    }

    @Test
    @DisplayName("an unknown name is UNKNOWN, never a nearest guess")
    fun identityUnknown() {
        assertTrue(facts().match("Nobody At All") is GrandConcertFanFacts.Match.UnknownNoMatch)
        assertTrue(facts().match("") is GrandConcertFanFacts.Match.UnknownNoMatch)
    }

    @Test
    @DisplayName("a name that normalizes to more than one canonical character is ambiguous, not guessed")
    fun identityAmbiguous() {
        // Neither an exact key; both "TM Opera O" and "T.M. Opera O" normalize to "tmoperao".
        assertTrue(facts().match("tmopera o") is GrandConcertFanFacts.Match.UnknownAmbiguous)
    }

    // ---- fan goals ----

    @Test
    @DisplayName("Copano's 3000@24 goal reads as a future requirement with the right slack")
    fun goalFuture() {
        val s = snapshot("Copano Rickey", turn = 15, fans = 2100)
        assertEquals(MatchStatus.EXACT, s.matchStatus)
        assertEquals(RequirementStatus.FUTURE, s.goalStatus)
        assertEquals(3000, s.goalTarget)
        assertEquals(24, s.goalDeadline)
    }

    @Test
    @DisplayName("a goal whose deadline is this turn reads DUE_NOW; a satisfied goal is SATISFIED")
    fun goalDueAndSatisfied() {
        assertEquals(RequirementStatus.DUE_NOW, snapshot("Copano Rickey", 24, 2100).goalStatus)
        assertEquals(RequirementStatus.SATISFIED, snapshot("Copano Rickey", 20, 3000).goalStatus)
    }

    @Test
    @DisplayName("an unmet goal past its deadline reads OVERDUE, not silently dropped")
    fun goalOverdue() {
        assertEquals(RequirementStatus.OVERDUE, snapshot("Copano Rickey", 30, 2100).goalStatus)
    }

    @Test
    @DisplayName("multiple goals pick the earliest unmet one")
    fun goalMultiple() {
        val s = snapshot("Multi Goal", turn = 40, fans = 6000) // 5000 met; 9000@45 and 12000@48 unmet
        assertEquals(9000, s.goalTarget)
        assertEquals(45, s.goalDeadline)
    }

    @Test
    @DisplayName("a character with no fan goal reports NONE for the goal arm")
    fun goalNone() {
        assertEquals(RequirementStatus.NONE, snapshot("Choice Char", 15, 100).goalStatus)
    }

    // ---- mandatory gates ----

    @Test
    @DisplayName("the earliest gate not yet enterable is selected; a met one is skipped")
    fun gateSelection() {
        val s = snapshot("Copano Rickey", turn = 40, fans = 2100) // Fukuryu 350 met, Champions 12000 unmet
        assertEquals(47, s.gateTurn)
        assertEquals(12000, s.gateSharedThreshold)
        assertEquals(RequirementStatus.FUTURE, s.gateStatus)
    }

    @Test
    @DisplayName("a same-threshold choice turn collapses to one exact gate but keeps the range")
    fun gateChoiceSameThreshold() {
        val s = snapshot("Same Choice", turn = 20, fans = 500)
        assertEquals(1000, s.gateSharedThreshold)
        assertEquals(1000, s.gateMinThreshold)
        assertEquals(1000, s.gateMaxThreshold)
        assertTrue(s.effectiveExact)
    }

    @Test
    @DisplayName("a differing-threshold choice turn is ambiguous: no exact target, a min/max range instead")
    fun gateChoiceAmbiguous() {
        val s = snapshot("Choice Char", turn = 20, fans = 500)
        assertNull(s.gateSharedThreshold)
        assertEquals(1250, s.gateMinThreshold)
        assertEquals(1750, s.gateMaxThreshold)
        assertEquals(RequirementType.MANDATORY_GATE, s.effectiveType)
        assertFalse(s.effectiveExact)
        assertNull(s.effectiveTarget)
        assertNull(s.deficit) // no exact target -> no claimed deficit
        assertEquals("gate-choice-ambiguous", s.reason)
    }

    // ---- effective requirement + calculation ----

    @Test
    @DisplayName("an earlier fan goal beats a later race gate, with exact deficit and calendar slack")
    fun effectiveGoalBeatsGate() {
        val s = snapshot("Copano Rickey", turn = 15, fans = 2100)
        assertEquals(RequirementType.FAN_GOAL, s.effectiveType)
        assertEquals(3000, s.effectiveTarget)
        assertEquals(24, s.effectiveTurn)
        assertEquals(900, s.deficit)
        assertEquals(9, s.turnsUntilRequirement)
        // Calendar slack is exclusive of the current turn, inclusive of the requirement turn: 16..24.
        assertEquals(9, s.raceableOpportunities)
        assertEquals(7, s.universalFloor)
        // Conservative upper bound: ceil(900 / 7) = 129 completed races guarantee the deficit.
        assertEquals(129, s.guaranteedRacesUpperBound)
    }

    @Test
    @DisplayName("once the goal is satisfied the later gate becomes the effective requirement")
    fun effectiveGateAfterGoal() {
        val s = snapshot("Copano Rickey", turn = 30, fans = 3000)
        assertEquals(RequirementType.MANDATORY_GATE, s.effectiveType)
        assertEquals(12000, s.effectiveTarget)
        assertEquals(47, s.effectiveTurn)
        assertEquals(9000, s.deficit)
        assertEquals(1286, s.guaranteedRacesUpperBound) // ceil(9000 / 7)
    }

    @Test
    @DisplayName("everything satisfied reports no effective requirement")
    fun effectiveNone() {
        val s = snapshot("Copano Rickey", turn = 50, fans = 20000)
        assertEquals(RequirementType.NONE, s.effectiveType)
        assertNull(s.deficit)
    }

    @Test
    @DisplayName("a differing-threshold gate contributes a min/max range but no guaranteed-races bound")
    fun ambiguousGateNoBound() {
        val s = snapshot("Choice Char", turn = 20, fans = 500)
        assertNull(s.guaranteedRacesUpperBound)
        assertNull(s.raceableOpportunities)
    }

    // ---- unknown / fail-safe snapshots ----

    @Test
    @DisplayName("a missing asset, an unmatched trainee, and an ambiguous identity all degrade to UNKNOWN")
    fun unknownSnapshots() {
        assertEquals(MatchStatus.NO_FACTS_ASSET, GrandConcertFanPressure.evaluate(null, "x", 10, 100).matchStatus)
        assertEquals(MatchStatus.UNKNOWN_NO_MATCH, snapshot("Nobody", 10, 100).matchStatus)
        assertEquals(MatchStatus.UNKNOWN_AMBIGUOUS, snapshot("tmopera o", 10, 100).matchStatus)
    }

    // ---- the review-gated fail-closed invariant (release-blocking) ----

    @Test
    @DisplayName("even a fully-known snapshot yields null policy inputs and a fail-safe force-race")
    fun reviewGatedFailClosed() {
        val rich = snapshot("Copano Rickey", turn = 15, fans = 2100)
        // The snapshot genuinely knows the exact facts...
        assertEquals(900, rich.deficit)
        assertEquals(129, rich.guaranteedRacesUpperBound)
        // ...yet the production policy inputs are review-gated to null.
        val inputs = GrandConcertFanPressure.reviewGatedPolicyInputs(rich)
        assertNull(inputs.turnsUntilDeadline)
        assertNull(inputs.racesStillNeeded)
        // ...so the policy fail-safe races and the deferral hook cannot return true.
        val decision =
            GrandConcertFanPolicy.decide(
                fanRequirementActive = true,
                turnsUntilDeadline = inputs.turnsUntilDeadline,
                racesStillNeeded = inputs.racesStillNeeded,
                concertBehindPace = true,
            )
        assertEquals(GrandConcertFanPolicy.FanRaceDecision.FAIL_SAFE_FORCE_RACE, decision)
        assertTrue(GrandConcertFanPolicy.forcesRace(decision))
        assertFalse(decision == GrandConcertFanPolicy.FanRaceDecision.DEFER_TO_TRAINING)
    }

    @Test
    @DisplayName("the production deferral path wires the policy through the review-gated null seam")
    fun productionPathIsReviewGated() {
        val src = source("bot/campaigns/GrandConcert.kt")
        val body = src.substring(src.indexOf("override fun considerFanRaceDeferral"), src.indexOf("private fun spendVisit"))
        assertTrue(body.contains("GrandConcertFanPressure.reviewGatedPolicyInputs(snapshot)"), "must derive policy inputs from the review gate")
        assertTrue(body.contains("turnsUntilDeadline = policyInputs.turnsUntilDeadline"), "deadline must come from the review-gated seam")
        assertTrue(body.contains("racesStillNeeded = policyInputs.racesStillNeeded"), "races-needed must come from the review-gated seam")
        // The snapshot's own figures must not be handed to the policy.
        assertFalse(body.contains("turnsUntilDeadline = snapshot"), "snapshot figures must not be wired into the policy")
        assertFalse(body.contains("racesStillNeeded = snapshot"), "snapshot figures must not be wired into the policy")
    }

    // ---- telemetry ----

    @Test
    @DisplayName("the GC_FAN line renders the facts and marks the policy inputs review-gated")
    fun telemetry() {
        val s = snapshot("Copano Rickey", turn = 15, fans = 2100)
        val inputs = GrandConcertFanPressure.reviewGatedPolicyInputs(s)
        val line = GrandConcertFanPressure.telemetryLine(s, concertBehindPace = true, inputs, GrandConcertFanPolicy.FanRaceDecision.FAIL_SAFE_FORCE_RACE)
        assertTrue(line.contains("[GRAND_CONCERT] [GC_FAN]"))
        assertTrue(line.contains("match=EXACT(Copano Rickey)"))
        assertTrue(line.contains("eff=FAN_GOAL"))
        assertTrue(line.contains("deficit=900"))
        assertTrue(line.contains("floor=7"))
        assertTrue(line.contains("policyDeadline=review-gated"))
        assertTrue(line.contains("policyRacesNeeded=review-gated"))
        assertTrue(line.contains("decision=FAIL_SAFE_FORCE_RACE"))
    }

    @Test
    @DisplayName("an ambiguous choice gate is rendered as a min/max range, not a false exact target")
    fun telemetryAmbiguous() {
        val s = snapshot("Choice Char", turn = 20, fans = 500)
        val inputs = GrandConcertFanPressure.reviewGatedPolicyInputs(s)
        val line = GrandConcertFanPressure.telemetryLine(s, concertBehindPace = true, inputs, GrandConcertFanPolicy.FanRaceDecision.FAIL_SAFE_FORCE_RACE)
        assertTrue(line.contains("ambiguous"), "line was: $line")
        assertTrue(line.contains("min1250/max1750"))
    }

    // ---- the shipped asset parses and carries the expected committed facts ----

    @Test
    @DisplayName("the committed gc_fan_runtime.json asset parses and carries Copano's facts")
    fun shippedAsset() {
        val text = assetFile("gc_fan_runtime.json").readText()
        val parsed = GrandConcertFanFacts.parse(text) ?: error("shipped asset should parse")
        assertEquals(7, parsed.universalCompletedRaceFanFloor)
        val s = GrandConcertFanPressure.evaluate(parsed, "Copano Rickey", currentTurn = 15, currentFans = 2100)
        assertEquals(3000, s.goalTarget)
        assertEquals(24, s.goalDeadline)
    }

    // ---- helpers ----

    private fun source(relative: String): String {
        val f = File(kotlinRoot(), relative)
        require(f.isFile) { "missing ${f.path}" }
        return f.readText()
    }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        error("could not locate the Kotlin source root")
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
        error("could not locate the assets root")
    }
}
