package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.RaceGrade
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Tests for special Training Event candidate selection and the option index it feeds.
 *
 * The defect under repair: the common race-result events live under graded two-option keys
 * ("Victory! (G1)\n1st", identical across every character), while the only plain-name
 * "Victory!" / "Solid Showing" / "Defeat" keys are Gold City's one-option, placement-conditioned
 * card events. Choosing between them by title or by ownership alone cannot work, because for Gold
 * City both are hers and both share a title; only the number of option rows on screen separates
 * them. Selecting first and repairing the option index afterwards is what silently forced a
 * configured Option 2 back to Option 1 on every race.
 *
 * The matrix below is therefore asserted against the REAL shipped data rather than a fixture, so a
 * data refresh that moves an option count or a key's ownership fails these tests instead of quietly
 * re-opening the defect.
 */
@DisplayName("Training Event special selection")
class TrainingEventSpecialSelectionTest {
    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // Shipped data

    /**
     * Gradle runs JVM tests with the module directory as CWD; walk up until the repo's data file is
     * found so the test also works from the repo root.
     */
    private fun loadShipped(fileName: String): JSONObject {
        val workingDir = System.getProperty("user.dir") ?: "."
        var dir: File? = File(workingDir)
        var found: File? = null
        repeat(5) {
            val candidate = File(dir, "src/data/$fileName")
            if (candidate.isFile) {
                found = candidate
                return@repeat
            }
            dir = dir?.parentFile
        }
        val file = requireNotNull(found) { "src/data/$fileName not found from $workingDir" }
        return JSONObject(file.readText())
    }

    private val shippedCharacters: JSONObject by lazy { loadShipped("characters.json") }
    private val shippedSupports: JSONObject by lazy { loadShipped("supports.json") }

    /** Selects against the real shipped data. [visibleOptionCount] is the on-screen option row count. */
    private fun selectShipped(
        specialEventName: String,
        ocrTitle: String,
        activeTrainee: String,
        visibleOptionCount: Int?,
        grade: RaceGrade? = RaceGrade.G1,
    ): TrainingEventRecognizer.SpecialEventSelection? =
        TrainingEventRecognizer.selectSpecialEvent(
            specialEventName = specialEventName,
            ocrTitle = ocrTitle,
            scenarioEvents = null,
            scenarioName = "URA Finale",
            characterEventData = shippedCharacters,
            supportEventData = shippedSupports,
            activeTraineeName = activeTrainee,
            lastRaceGrade = grade,
            visibleOptionCount = visibleOptionCount,
        )

    /**
     * One race-result family. Every expected key is written out literally so the test states its own
     * expectation instead of recomputing it the way the implementation does.
     */
    private data class RaceFamily(
        val special: String,
        val goldCityCardKey: String,
        val g1Key: String,
        val g2g3Key: String,
        val preOpKey: String,
        val g1SecondOptionPrefix: String,
    )

    private val raceFamilies =
        listOf(
            RaceFamily("Victory!", "Victory!", "Victory! (G1)\n1st", "Victory! (G2/G3)\n1st", "Victory! (Pre/OP)\n1st", "Energy -5/-20"),
            RaceFamily(
                "Solid Showing",
                "Solid Showing",
                "Solid Showing (G1)\n2nd-5th",
                "Solid Showing (G2/G3)\n2nd-5th",
                "Solid Showing (Pre/OP)\n2nd-5th",
                "Energy -10/-30",
            ),
            RaceFamily(
                "Defeat",
                "Defeat",
                "Defeat (G1)\n6th or worse",
                "Defeat (G2/G3)\n6th or worse",
                "Defeat (Pre/OP)\n6th or worse",
                "Energy -15/-35",
            ),
        )

    private val otherTrainee = "Daiwa Scarlet"

    private fun readable(key: String): String = key.replace("\n", "\\n")

    // //////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("Gold City matrix, on the real shipped data")
    inner class GoldCityMatrix {
        @Test
        fun `Gold City with one visible option selects her own one-option card event`() {
            for (family in raceFamilies) {
                val selection = selectShipped(family.special, family.special, "Gold City", visibleOptionCount = 1)
                assertNotNull(selection, "${family.special}: nothing selected")
                assertEquals(family.goldCityCardKey, selection!!.eventTitle, "${family.special}: wrong key for Gold City on a one-option screen")
                assertEquals(1, selection.eventOptionRewards.size, "${family.special}: expected her one-option card copy")
                assertEquals("Gold City", selection.ownerName, "${family.special}: wrong owner")
                assertEquals(TrainingEventRecognizer.OptionCountVerdict.MATCHED, selection.optionCountVerdict, "${family.special}: screen should corroborate the pick")
                // Her copy is the placement-conditioned one; that data must survive the choice.
                assertTrue(selection.eventOptionRewards[0].contains("※"), "${family.special}: expected the placement-conditioned reward text")
            }
        }

        @Test
        fun `Gold City with two visible options selects the graded common event, not her card event`() {
            // The blocker: her one-option card copy previously won on any screen because she owned it,
            // which clamped the configured Option 2 back to Option 1 on every race she ran.
            for (family in raceFamilies) {
                val selection = selectShipped(family.special, family.special, "Gold City", visibleOptionCount = 2)
                assertNotNull(selection, "${family.special}: nothing selected")
                assertEquals(family.g1Key, selection!!.eventTitle, "${family.special}: expected the graded common key")
                assertNotEquals(family.goldCityCardKey, selection.eventTitle, "${family.special}: her one-option card copy must not win a two-option screen")
                assertEquals(2, selection.eventOptionRewards.size, "${family.special}: expected the two-option chooser data")
                assertEquals("Gold City", selection.ownerName, "${family.special}: the common copy is still attributed to the active trainee")
                assertEquals(TrainingEventRecognizer.OptionCountVerdict.MATCHED, selection.optionCountVerdict, "${family.special}: screen should corroborate the pick")
            }
        }

        @Test
        fun `another trainee with two visible options selects the graded common event`() {
            for (family in raceFamilies) {
                val selection = selectShipped(family.special, family.special, otherTrainee, visibleOptionCount = 2)
                assertNotNull(selection, "${family.special}: nothing selected")
                assertEquals(family.g1Key, selection!!.eventTitle, "${family.special}: expected the graded common key")
                assertEquals(2, selection.eventOptionRewards.size, "${family.special}: expected the two-option chooser data")
                assertEquals(otherTrainee, selection.ownerName, "${family.special}: common copies attribute to the active trainee")
                assertEquals(TrainingEventRecognizer.OptionCountVerdict.MATCHED, selection.optionCountVerdict, "${family.special}: screen should corroborate the pick")
            }
        }

        @Test
        fun `another trainee with one visible option never selects Gold City's event`() {
            // Inconsistent evidence: no copy of the family has one option for this trainee. The
            // requirement is only that the answer is safe and flagged, never Gold City's copy.
            for (family in raceFamilies) {
                val selection = selectShipped(family.special, family.special, otherTrainee, visibleOptionCount = 1)
                assertNotNull(selection, "${family.special}: nothing selected")
                assertNotEquals(family.goldCityCardKey, selection!!.eventTitle, "${family.special}: another trainee must never reach Gold City's copy")
                assertNotEquals("Gold City", selection.ownerName, "${family.special}: another trainee must never be attributed to Gold City")
                assertEquals(
                    TrainingEventRecognizer.OptionCountVerdict.MISMATCHED,
                    selection.optionCountVerdict,
                    "${family.special}: a count no copy has must be reported as a mismatch",
                )
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("Race grade picks the graded copy")
    inner class GradeCoverage {
        @Test
        fun `every grade maps to its graded copy in every family`() {
            for (family in raceFamilies) {
                val expectedByGrade: List<Pair<RaceGrade?, String>> =
                    listOf(
                        RaceGrade.G1 to family.g1Key,
                        RaceGrade.FINALE to family.g1Key,
                        RaceGrade.EX to family.g1Key,
                        null to family.g1Key,
                        RaceGrade.G2 to family.g2g3Key,
                        RaceGrade.G3 to family.g2g3Key,
                        RaceGrade.PRE_OP to family.preOpKey,
                        RaceGrade.OP to family.preOpKey,
                        RaceGrade.DEBUT to family.preOpKey,
                        RaceGrade.MAIDEN to family.preOpKey,
                    )
                for ((grade, expectedKey) in expectedByGrade) {
                    val selection = selectShipped(family.special, family.special, otherTrainee, visibleOptionCount = 2, grade = grade)
                    assertNotNull(selection, "${family.special} @ ${grade?.name ?: "unknown"}: nothing selected")
                    assertEquals(expectedKey, selection!!.eventTitle, "${family.special} @ ${grade?.name ?: "unknown"}: wrong graded copy")
                    assertEquals(2, selection.eventOptionRewards.size, "${family.special} @ ${grade?.name ?: "unknown"}: expected two options")
                }
            }
        }

        @Test
        fun `an unknown grade keeps the preserved G1 fallback and still yields a two-option copy`() {
            for (family in raceFamilies) {
                val selection = selectShipped(family.special, family.special, otherTrainee, visibleOptionCount = 2, grade = null)
                assertEquals(family.g1Key, selection!!.eventTitle, "${family.special}: unknown grade should fall back to the G1 copy")
                assertEquals(2, selection.eventOptionRewards.size)
            }
        }

        @Test
        fun `the grade choice never changes the option count, so a wrong grade cannot move the option index`() {
            // Why the unknown-grade fallback is safe rather than merely convenient.
            for (family in raceFamilies) {
                val counts =
                    listOf(RaceGrade.G1, RaceGrade.G2, RaceGrade.G3, RaceGrade.OP, RaceGrade.PRE_OP, RaceGrade.DEBUT, RaceGrade.MAIDEN, RaceGrade.FINALE, RaceGrade.EX, null)
                        .map { selectShipped(family.special, family.special, otherTrainee, visibleOptionCount = 2, grade = it)!!.eventOptionRewards.size }
                        .distinct()
                assertEquals(listOf(2), counts, "${family.special}: every graded copy must carry the same option count")
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("Configured Option 2 survives to the tap")
    inner class ConfiguredOptionIndex {
        @Test
        fun `Option 2 is in bounds on every two-option race result, for Gold City and for another trainee`() {
            // The shipped default: specialEventOverrides["Victory!"] = "Option 2: Energy -5/-20 ...".
            val configuredIndex = TrainingEvent.parseSpecialOverrideOptionIndex("Option 2: Energy -5/-20 and random stat gain")
            assertEquals(1, configuredIndex)

            for (family in raceFamilies) {
                for (trainee in listOf("Gold City", otherTrainee)) {
                    val selection = selectShipped(family.special, family.special, trainee, visibleOptionCount = 2)!!
                    val decision = TrainingEvent.decideSpecialEventOption(configuredIndex!!, null, selection.eventOptionRewards.size)
                    assertEquals(1, decision.optionIndex, "${family.special} / $trainee: Option 2 must remain index 1")
                    assertFalse(decision.clamped, "${family.special} / $trainee: Option 2 must not be clamped")
                    assertTrue(
                        selection.eventOptionRewards[decision.optionIndex].startsWith(family.g1SecondOptionPrefix),
                        "${family.special} / $trainee: Option 2 should be the energy-preserving branch",
                    )
                }
            }
        }

        @Test
        fun `Gold City's one-option card event legitimately clamps Option 2, and only there`() {
            // Not a defect: on a genuinely one-option screen there is no second option to take.
            val selection = selectShipped("Victory!", "Victory!", "Gold City", visibleOptionCount = 1)!!
            assertEquals(1, selection.eventOptionRewards.size)
            val decision = TrainingEvent.decideSpecialEventOption(1, null, selection.eventOptionRewards.size)
            assertEquals(0, decision.optionIndex)
            assertTrue(decision.clamped)
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("Runtime option-index decision")
    inner class RuntimeDecision {
        @Test
        fun `the special override index is used when no per-trainee override applies`() {
            val decision = TrainingEvent.decideSpecialEventOption(specialOverrideIndex = 1, characterOverrideIndex = null, eventOptionCount = 2)
            assertEquals(1, decision.optionIndex)
            assertFalse(decision.usedCharacterOverride)
            assertFalse(decision.clamped)
        }

        @Test
        fun `a per-trainee override outranks the special override`() {
            // Maruzensky's shipped Acupuncture pick (character override Option 1) against her own
            // generic special default (Option 3), on the five-option Acupuncture event.
            val decision = TrainingEvent.decideSpecialEventOption(specialOverrideIndex = 2, characterOverrideIndex = 0, eventOptionCount = 5)
            assertEquals(0, decision.optionIndex)
            assertTrue(decision.usedCharacterOverride)
            assertFalse(decision.clamped)
        }

        @Test
        fun `an index past the end of the data clamps to the last option and says so`() {
            val decision = TrainingEvent.decideSpecialEventOption(specialOverrideIndex = 1, characterOverrideIndex = null, eventOptionCount = 1)
            assertEquals(0, decision.optionIndex)
            assertTrue(decision.clamped)
        }

        @Test
        fun `a per-trainee override past the end clamps too`() {
            val decision = TrainingEvent.decideSpecialEventOption(specialOverrideIndex = 0, characterOverrideIndex = 3, eventOptionCount = 2)
            assertEquals(1, decision.optionIndex)
            assertTrue(decision.usedCharacterOverride)
            assertTrue(decision.clamped)
        }

        @Test
        fun `an unknown option count passes the requested index through untouched`() {
            val decision = TrainingEvent.decideSpecialEventOption(specialOverrideIndex = 1, characterOverrideIndex = null, eventOptionCount = 0)
            assertEquals(1, decision.optionIndex)
            assertFalse(decision.clamped)
        }

        @Test
        fun `a negative index is corrected to the first option`() {
            val decision = TrainingEvent.decideSpecialEventOption(specialOverrideIndex = -1, characterOverrideIndex = null, eventOptionCount = 2)
            assertEquals(0, decision.optionIndex)
            assertTrue(decision.clamped)
        }

        @Test
        fun `special override option parsing handles Default and unparseable strings`() {
            assertEquals(0, TrainingEvent.parseSpecialOverrideOptionIndex("Default"))
            assertEquals(4, TrainingEvent.parseSpecialOverrideOptionIndex("Option 5: Energy +10"))
            assertNull(TrainingEvent.parseSpecialOverrideOptionIndex("garbled text"))
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("Option-count evidence rules")
    inner class OptionCountEvidence {
        @Test
        fun `a zero or unreadable count is not evidence for a one-option event, Gold City included`() {
            // The rule that matters most for Gold City, who OWNS the one-option copy: an absent or
            // failed observation must never be read as "the screen has one option".
            for (family in raceFamilies) {
                for (count in listOf(0, null)) {
                    for (trainee in listOf("Gold City", otherTrainee)) {
                        val selection = selectShipped(family.special, family.special, trainee, visibleOptionCount = count)!!
                        assertNotEquals(
                            family.goldCityCardKey,
                            selection.eventTitle,
                            "${family.special} / $trainee @ count=$count: the card event must not win without a trusted count",
                        )
                        assertEquals(2, selection.eventOptionRewards.size, "${family.special} / $trainee @ count=$count: expected the graded two-option copy")
                    }
                }
            }
        }

        @Test
        fun `withholding the card event without count evidence is reported`() {
            for (family in raceFamilies) {
                val selection = selectShipped(family.special, family.special, "Gold City", visibleOptionCount = null)!!
                assertEquals(
                    TrainingEventRecognizer.OptionCountVerdict.UNVERIFIED,
                    selection.optionCountVerdict,
                    "${family.special}: an unusable count over differently shaped copies must not read as corroborated",
                )
                assertEquals(
                    family.goldCityCardKey,
                    selection.withheldCardEventKey,
                    "${family.special}: the refused card copy must be named so the decision is diagnosable",
                )
            }
        }

        @Test
        fun `the on-screen count, not ownership, is what decides Gold City's race results`() {
            // Differential proof that the count participates in the decision. Ownership is identical
            // in all three calls; only the number of rows changes, and only a trusted one-row
            // observation reaches her card event.
            for (family in raceFamilies) {
                val noCount = selectShipped(family.special, family.special, "Gold City", visibleOptionCount = null)!!
                val oneRow = selectShipped(family.special, family.special, "Gold City", visibleOptionCount = 1)!!
                val twoRows = selectShipped(family.special, family.special, "Gold City", visibleOptionCount = 2)!!

                assertEquals(family.goldCityCardKey, oneRow.eventTitle, "${family.special}: a trusted single row is her card event")
                assertEquals(family.g1Key, twoRows.eventTitle, "${family.special}: two rows select the graded common copy")
                assertEquals(family.g1Key, noCount.eventTitle, "${family.special}: no count must fall to the graded copy, never the card copy")
                assertNotEquals(
                    oneRow.eventTitle,
                    twoRows.eventTitle,
                    "${family.special}: if these agree, the option count is not participating in selection",
                )
            }
        }

        @Test
        fun `a card copy that is the only candidate for its title still resolves without a count`() {
            // The withholding rule must not strand events whose card copy has no rival shape:
            // dropping these would push them onto unrelated data instead.
            val maruzensky = selectShipped("Victory!", "The Road to a Rad Victory!", "Maruzensky", visibleOptionCount = null)!!
            assertEquals("The Road to a Rad Victory!", maruzensky.eventTitle)
            assertNull(maruzensky.withheldCardEventKey)

            val taishin = selectShipped("Extra Training", "Extra Training to Blow Off Steam", "Narita Taishin", visibleOptionCount = null)!!
            assertEquals("Extra Training to Blow Off Steam", taishin.eventTitle)
        }

        @Test
        fun `a uniform family needs no count and is not flagged`() {
            val selection = selectShipped("Acupuncture (Just an Acupuncturist, No Worries! ☆)", "Acupuncture (Just an Acupuncturist, No Worries! ☆)", otherTrainee, visibleOptionCount = null)!!
            assertEquals(5, selection.eventOptionRewards.size)
            assertEquals(TrainingEventRecognizer.OptionCountVerdict.NOT_APPLICABLE, selection.optionCountVerdict)
        }

        @Test
        fun `only a positive count is authoritative`() {
            assertTrue(TrainingEventRecognizer.isAuthoritativeOptionCount(1))
            assertTrue(TrainingEventRecognizer.isAuthoritativeOptionCount(5))
            assertFalse(TrainingEventRecognizer.isAuthoritativeOptionCount(0))
            assertFalse(TrainingEventRecognizer.isAuthoritativeOptionCount(null))
            assertFalse(TrainingEventRecognizer.isAuthoritativeOptionCount(-1))
        }

        @Test
        fun `a card copy is considered only for its owner and only when the count does not contradict it`() {
            // Owner matches and the screen agrees.
            assertTrue(TrainingEventRecognizer.singleOwnerCopyIsEligible("Gold City", "Gold City", dataOptionCount = 1, visibleOptionCount = 1))
            // Owner matches but the screen shows the two-option chooser: the card copy is out.
            assertFalse(TrainingEventRecognizer.singleOwnerCopyIsEligible("Gold City", "Gold City", dataOptionCount = 1, visibleOptionCount = 2))
            // No usable count: still considerable, because a copy with no rival shape must survive.
            assertTrue(TrainingEventRecognizer.singleOwnerCopyIsEligible("Gold City", "Gold City", dataOptionCount = 1, visibleOptionCount = null))
            // Wrong trainee is never eligible, whatever the screen shows.
            assertFalse(TrainingEventRecognizer.singleOwnerCopyIsEligible("Gold City", otherTrainee, dataOptionCount = 1, visibleOptionCount = 1))
            assertFalse(TrainingEventRecognizer.singleOwnerCopyIsEligible("Gold City", otherTrainee, dataOptionCount = 1, visibleOptionCount = null))
            // An outfit-bearing active name still matches its base character.
            assertTrue(TrainingEventRecognizer.singleOwnerCopyIsEligible("Daiwa Scarlet", "[Bubblegum Memories] Daiwa Scarlet", 2, 2))
            // A same-first-word different trainee does not.
            assertFalse(TrainingEventRecognizer.singleOwnerCopyIsEligible("Gold City", "Gold Ship", 1, 1))
        }

        @Test
        fun `a card copy is count-confirmed only by a trusted count that matches its shape`() {
            // This is the predicate that governs wherever the card copy competes with another shape.
            assertTrue(TrainingEventRecognizer.singleOwnerCopyIsCountConfirmed("Gold City", "Gold City", dataOptionCount = 1, visibleOptionCount = 1))
            assertFalse(TrainingEventRecognizer.singleOwnerCopyIsCountConfirmed("Gold City", "Gold City", dataOptionCount = 1, visibleOptionCount = 2))
            assertFalse(TrainingEventRecognizer.singleOwnerCopyIsCountConfirmed("Gold City", "Gold City", dataOptionCount = 1, visibleOptionCount = null))
            assertFalse(TrainingEventRecognizer.singleOwnerCopyIsCountConfirmed("Gold City", "Gold City", dataOptionCount = 1, visibleOptionCount = 0))
            assertFalse(TrainingEventRecognizer.singleOwnerCopyIsCountConfirmed("Gold City", otherTrainee, dataOptionCount = 1, visibleOptionCount = 1))
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("Option-row count stability")
    inner class CountStability {
        private fun accept(vararg observations: Int) = TrainingEvent.acceptStableOptionCount(observations.toList())

        @Test
        fun `two or more rows settle as soon as two consecutive captures agree`() {
            assertEquals(2, accept(2, 2)?.count)
            assertEquals(1, accept(2, 2)?.acceptedAtIndex)
            assertEquals(3, accept(3, 3)?.count)
            assertEquals(1, accept(3, 3)?.acceptedAtIndex)
        }

        @Test
        fun `a single row is never accepted from two early captures`() {
            // The false-stability case: a two-option screen whose second row had not drawn yet can
            // report one twice in a row, so two ones prove nothing on their own.
            assertNull(accept(1, 1), "[1,1] must not certify a one-option event")
            assertNull(accept(1, 1, 1), "[1,1,1] is still short of the full window")
        }

        @Test
        fun `a single row is accepted only when it holds for the whole window`() {
            val settled = accept(1, 1, 1, 1)
            assertEquals(1, settled?.count)
            assertEquals(3, settled?.acceptedAtIndex, "the accepted frame is the last of the full window")
        }

        @Test
        fun `a window that ever saw anything but one row cannot certify one row`() {
            assertNull(accept(1, 1, 1, 2), "a late second row denies the one-option reading")
            assertNull(accept(0, 1, 1, 1), "a failed first capture leaves the window incomplete as evidence")
            assertNull(accept(0, 1, 1, 2))
        }

        @Test
        fun `a two-option screen first captured as one row still settles on two`() {
            val settled = accept(1, 1, 2, 2)
            assertEquals(2, settled?.count, "[1,1,2,2] must resolve to 2, never 1")
            assertEquals(3, settled?.acceptedAtIndex)
        }

        @Test
        fun `a rising sequence is accepted at the repeat, not the first sighting`() {
            val settled = accept(1, 2, 2)
            assertEquals(2, settled?.count, "a 1 that became 2 must settle on 2")
            assertEquals(2, settled?.acceptedAtIndex, "the confirming capture is the third one")

            val fromEmpty = accept(0, 1, 2, 2)
            assertEquals(2, fromEmpty?.count)
            assertEquals(3, fromEmpty?.acceptedAtIndex)
        }

        @Test
        fun `a count below an earlier peak is a dropped read, not a new shape`() {
            // Rows appear as a screen draws; they do not disappear. Once two rows have been seen,
            // a later pair of ones is a failing capture rather than evidence of a smaller event.
            assertNull(accept(2, 1, 1))
            assertNull(accept(3, 2, 2))
            assertNull(accept(2, 1, 1, 1))
        }

        @Test
        fun `a flapping sequence never settles`() {
            assertNull(accept(1, 2, 1, 2))
        }

        @Test
        fun `zero is never a stable observation`() {
            assertNull(accept(0, 0, 0, 0))
        }

        @Test
        fun `a single capture and an empty sequence are both unresolved`() {
            assertNull(accept(1))
            assertNull(TrainingEvent.acceptStableOptionCount(emptyList()))
        }

        @Test
        fun `the accepted capture index points at the frame whose rows should be reused`() {
            assertEquals(1, accept(3, 3)?.acceptedAtIndex)

            val settled = accept(1, 2, 2, 2)
            assertEquals(2, settled?.count)
            assertEquals(2, settled?.acceptedAtIndex, "acceptance happens at the first repeat, not the last")
        }

        @Test
        fun `the window size that certifies one row is the caller's budget, not a constant`() {
            // Under the real budget (4, the acquisition loop's own) two ones certify nothing; these
            // cases only demonstrate that the parameter is honored, using budgets the bot never uses.
            assertNull(accept(1, 1), "under the runtime budget, two ones must stay unresolved")
            assertEquals(
                1,
                TrainingEvent.acceptStableOptionCount(listOf(1, 1), requiredObservationCount = 2)?.count,
                "a hypothetical two-capture budget makes [1,1] a complete window",
            )
            assertNull(
                TrainingEvent.acceptStableOptionCount(listOf(1, 1), requiredObservationCount = 3),
                "a three-capture budget leaves [1,1] one capture short",
            )
        }

        @Test
        fun `the capture budget stays inside the mission's bound`() {
            assertEquals(4, TrainingEvent.OPTION_ROW_MAX_CAPTURES)
            assertTrue(
                (TrainingEvent.OPTION_ROW_MAX_CAPTURES - 1) * TrainingEvent.OPTION_ROW_CAPTURE_INTERVAL <= 1.0,
                "worst-case added wait must stay at or under one second",
            )
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("Transient render, end to end through the stability policy")
    inner class TransientRender {
        /** Runs a capture sequence through the real acceptance rule, then selects with its verdict. */
        private fun selectAfterFrames(family: RaceFamily, trainee: String, vararg frames: Int) =
            selectShipped(
                family.special,
                family.special,
                trainee,
                visibleOptionCount = TrainingEvent.acceptStableOptionCount(frames.toList())?.count,
            )!!

        @Test
        fun `a two-option screen first captured as one option still selects the graded event`() {
            // The exact partial-render sequence: row 2 had not rendered when the first capture ran.
            for (family in raceFamilies) {
                val selection = selectAfterFrames(family, "Gold City", 1, 2, 2)
                assertEquals(family.g1Key, selection.eventTitle, "${family.special}: a transient 1 must not reach the card event")
                assertEquals(2, selection.eventOptionRewards.size)
            }
        }

        @Test
        fun `a two-option screen captured as one row TWICE still selects the graded event`() {
            // The false-stability case that reopened the original defect: two early ones in a row.
            for (family in raceFamilies) {
                val selection = selectAfterFrames(family, "Gold City", 1, 1, 2, 2)
                assertEquals(family.g1Key, selection.eventTitle, "${family.special}: [1,1,2,2] must reach the graded event")
                assertEquals(2, selection.eventOptionRewards.size, "${family.special}: and must carry both options")
                assertNull(selection.withheldCardEventKey, "${family.special}: nothing is withheld once the count settles on 2")
            }
        }

        @Test
        fun `a window that ends on a second row withholds the card event`() {
            for (family in raceFamilies) {
                for (frames in listOf(intArrayOf(1, 1, 1, 2), intArrayOf(0, 1, 1, 2))) {
                    val selection = selectAfterFrames(family, "Gold City", *frames)
                    assertEquals(family.g1Key, selection.eventTitle, "${family.special} @ ${frames.joinToString(",")}: expected the graded event")
                    assertEquals(
                        family.goldCityCardKey,
                        selection.withheldCardEventKey,
                        "${family.special} @ ${frames.joinToString(",")}: the refused card copy must be named",
                    )
                }
            }
        }

        @Test
        fun `a flapping capture sequence never reaches the card event`() {
            for (family in raceFamilies) {
                val selection = selectAfterFrames(family, "Gold City", 1, 2, 1, 2)
                assertNotEquals(family.goldCityCardKey, selection.eventTitle, "${family.special}: an unsettled count must withhold the card event")
                assertEquals(family.goldCityCardKey, selection.withheldCardEventKey, "${family.special}: and must say which copy it refused")
            }
        }

        @Test
        fun `only a full window of single-row captures reaches the card event`() {
            for (family in raceFamilies) {
                val fullWindow = selectAfterFrames(family, "Gold City", 1, 1, 1, 1)
                assertEquals(family.goldCityCardKey, fullWindow.eventTitle, "${family.special}: a whole window of one row is real evidence")
                assertEquals(1, fullWindow.eventOptionRewards.size)

                // The same trainee, one capture short of the window, must not get there.
                val shortWindow = selectAfterFrames(family, "Gold City", 1, 1)
                assertEquals(family.g1Key, shortWindow.eventTitle, "${family.special}: two ones alone must not reach the card event")
            }
        }

        @Test
        fun `an all-zero scan never reaches the card event`() {
            for (family in raceFamilies) {
                val selection = selectAfterFrames(family, "Gold City", 0, 0, 0, 0)
                assertEquals(family.g1Key, selection.eventTitle, "${family.special}: a failed scan falls to the graded copy")
            }
        }

        @Test
        fun `another trainee never receives Gold City's event under any partial sequence`() {
            val sequences = listOf(intArrayOf(1, 1), intArrayOf(1, 1, 2, 2), intArrayOf(1, 1, 1, 2), intArrayOf(0, 1, 1, 2), intArrayOf(1, 2, 1, 2), intArrayOf(1, 1, 1, 1))
            for (family in raceFamilies) {
                for (frames in sequences) {
                    val selection = selectAfterFrames(family, otherTrainee, *frames)
                    assertNotEquals(family.goldCityCardKey, selection.eventTitle, "${family.special} @ ${frames.joinToString(",")}")
                    assertNotEquals("Gold City", selection.ownerName, "${family.special} @ ${frames.joinToString(",")}")
                }
            }
        }

        @Test
        fun `Option 2 stays index 1 and in bounds once a partial sequence resolves to the graded event`() {
            // Ties the acquisition fix back to the setting it exists to protect, including the tap
            // planner seeing an in-bounds index for the two-row screen.
            val configuredIndex = TrainingEvent.parseSpecialOverrideOptionIndex("Option 2: Energy -5/-20 and random stat gain")!!
            for (family in raceFamilies) {
                val selection = selectAfterFrames(family, "Gold City", 1, 1, 2, 2)
                val decision = TrainingEvent.decideSpecialEventOption(configuredIndex, null, selection.eventOptionRewards.size)
                assertEquals(1, decision.optionIndex, "${family.special}: Option 2 must survive a [1,1,2,2] window")
                assertFalse(decision.clamped, "${family.special}: and must not be clamped")

                val plan = TrainingEvent.planOptionTap(decision.optionIndex, availableRows = 2, rescanned = false)
                assertEquals(TrainingEvent.OptionTapAction.USE_ROW, plan.action, "${family.special}: the two-row screen taps its own row")
                assertEquals(1, plan.rowIndex)
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("Tap-row resolution")
    inner class TapResolution {
        @Test
        fun `an in-bounds selection taps its own row without rescanning`() {
            val plan = TrainingEvent.planOptionTap(selectedIndex = 1, availableRows = 2, rescanned = false)
            assertEquals(TrainingEvent.OptionTapAction.USE_ROW, plan.action)
            assertEquals(1, plan.rowIndex)
        }

        @Test
        fun `a genuine one-option card event taps row one without rescanning`() {
            val plan = TrainingEvent.planOptionTap(selectedIndex = 0, availableRows = 1, rescanned = false)
            assertEquals(TrainingEvent.OptionTapAction.USE_ROW, plan.action)
            assertEquals(0, plan.rowIndex)
        }

        @Test
        fun `a stale short list asks for a rescan instead of falling to the first row`() {
            val plan = TrainingEvent.planOptionTap(selectedIndex = 1, availableRows = 1, rescanned = false)
            assertEquals(TrainingEvent.OptionTapAction.RESCAN, plan.action)
        }

        @Test
        fun `an empty list asks for a rescan`() {
            assertEquals(TrainingEvent.OptionTapAction.RESCAN, TrainingEvent.planOptionTap(0, 0, rescanned = false).action)
        }

        @Test
        fun `a rescan that revealed the missing row resolves the selected option`() {
            val plan = TrainingEvent.planOptionTap(selectedIndex = 1, availableRows = 2, rescanned = true)
            assertEquals(TrainingEvent.OptionTapAction.USE_ROW, plan.action)
            assertEquals(1, plan.rowIndex)
        }

        @Test
        fun `a still-short list after a rescan reports the shortfall instead of hiding it`() {
            val plan = TrainingEvent.planOptionTap(selectedIndex = 1, availableRows = 1, rescanned = true)
            assertEquals(TrainingEvent.OptionTapAction.CLAMP_TO_LAST_ROW, plan.action)
            assertEquals(0, plan.rowIndex)
        }

        @Test
        fun `no rows after a rescan defers to the single retrying search`() {
            val plan = TrainingEvent.planOptionTap(selectedIndex = 1, availableRows = 0, rescanned = true)
            assertEquals(TrainingEvent.OptionTapAction.NO_ROWS_AVAILABLE, plan.action)
            assertEquals(-1, plan.rowIndex)
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("Family routing and collisions")
    inner class FamilyRouting {
        @Test
        fun `Victory Conditions and other title-containing events are not race results`() {
            assertNull(TrainingEventRecognizer.detectSpecialEvent("Victory Conditions"))
            assertFalse(TrainingEventRecognizer.isSpecialFamilyKey("Victory Conditions", "Victory!"))
            assertFalse(TrainingEventRecognizer.isSpecialFamilyKey("After the Arima Kinen: Victory", "Victory!"))
            assertFalse(TrainingEventRecognizer.isSpecialFamilyKey("After the Satsuki Sho: An Unsatisfying Victory", "Victory!"))

            // And the real selection never returns one on a race-result screen.
            val selection = selectShipped("Victory!", "Victory!", "Air Shakur", visibleOptionCount = 2)!!
            assertEquals("Victory! (G1)\n1st", selection.eventTitle)
        }

        @Test
        fun `a trainee's own card race event still wins when it is the one on screen`() {
            val selection = selectShipped("Victory!", "The Road to a Rad Victory!", "Maruzensky", visibleOptionCount = 2)!!
            assertEquals("The Road to a Rad Victory!", selection.eventTitle)
            assertEquals("Maruzensky", selection.ownerName)

            val plain = selectShipped("Victory!", "Victory!", "Maruzensky", visibleOptionCount = 2)!!
            assertEquals("Victory! (G1)\n1st", plain.eventTitle)
        }

        @Test
        fun `Tosen Jordan's card race event does not leak to other trainees`() {
            val hers = selectShipped("Victory!", "The Taste of Victory!", "Tosen Jordan", visibleOptionCount = 2)!!
            assertEquals("The Taste of Victory!", hers.eventTitle)

            val someoneElse = selectShipped("Victory!", "The Taste of Victory!", otherTrainee, visibleOptionCount = 2)!!
            assertNotEquals("The Taste of Victory!", someoneElse.eventTitle, "another trainee must not reach Tosen Jordan's card event")
        }

        @Test
        fun `support card special titles select their own data`() {
            val sasami = selectShipped("Acupuncture (Just an Acupuncturist, No Worries! ☆)", "The Applications of Acupuncture", otherTrainee, visibleOptionCount = 1)!!
            assertEquals("support", sasami.source)
            assertEquals("Sasami Anshinzawa", sasami.ownerName)
            assertEquals("The Applications of Acupuncture", sasami.eventTitle)

            val marvelous = selectShipped("Victory!", "Marvelous☆Victory!", otherTrainee, visibleOptionCount = 1)!!
            assertEquals("support", marvelous.source)
            assertEquals("Marvelous Sunday", marvelous.ownerName)
        }

        @Test
        fun `Get Well Soon resolves to the wrapped Failed training key`() {
            val selection = selectShipped("Get Well Soon!", "Get Well Soon!", otherTrainee, visibleOptionCount = 2)!!
            assertEquals("Failed training (Get Well Soon!)", selection.eventTitle)
            assertEquals(2, selection.eventOptionRewards.size)
            assertTrue(selection.confidence >= 0.90, "the pattern pre-filter pinned the identity, got ${selection.confidence}")
        }

        @Test
        fun `Don't Overdo It resolves to its own wrapped key`() {
            val selection = selectShipped("Don't Overdo It!", "Don't Overdo It!", otherTrainee, visibleOptionCount = 2)!!
            assertEquals("Failed training (Don't Overdo It!)", selection.eventTitle)
            assertEquals(2, selection.eventOptionRewards.size)
        }

        @Test
        fun `Elated and Exhaustive Coverage route to their own families`() {
            assertEquals("Etsuko's Elated Coverage", TrainingEventRecognizer.detectSpecialEvent("Etsuko's Elated Coverage"))
            assertEquals("Etsuko's Exhaustive Coverage", TrainingEventRecognizer.detectSpecialEvent("Etsuko's Exhaustive Coverage"))

            val elated = selectShipped("Etsuko's Elated Coverage", "Etsuko's Elated Coverage", otherTrainee, visibleOptionCount = 1)!!
            assertEquals("Etsuko's Elated Coverage (G1)", elated.eventTitle)
            assertEquals(1, elated.eventOptionRewards.size)

            val exhaustive = selectShipped("Etsuko's Exhaustive Coverage", "Etsuko's Exhaustive Coverage", otherTrainee, visibleOptionCount = 2)!!
            assertEquals("Etsuko's Exhaustive Coverage (G1)", exhaustive.eventTitle)
            assertEquals(2, exhaustive.eventOptionRewards.size)
        }

        @Test
        fun `the on-screen title separates the common Extra Training from Taishin's variants`() {
            val common = selectShipped("Extra Training", "Extra Training", "Narita Taishin", visibleOptionCount = 2)!!
            assertEquals("Extra Training", common.eventTitle)
            assertEquals(2, common.eventOptionRewards.size)

            val variant = selectShipped("Extra Training", "Extra Training to Blow Off Steam", "Narita Taishin", visibleOptionCount = 3)!!
            assertEquals("Extra Training to Blow Off Steam", variant.eventTitle)
            assertEquals(3, variant.eventOptionRewards.size)
        }

        @Test
        fun `special events without any data return null so dedicated branches keep the pattern name`() {
            assertNull(selectShipped("Tutorial", "Tutorial", otherTrainee, visibleOptionCount = 2))
            assertNull(selectShipped("A Team at Last", "A Team at Last", otherTrainee, visibleOptionCount = 5))
        }

        @Test
        fun `variant decorations strip to the screen-equivalent title`() {
            assertEquals("Victory!", TrainingEventRecognizer.stripVariantDecorations("Victory! (G1)\n1st"))
            assertEquals("Etsuko's Exhaustive Coverage", TrainingEventRecognizer.stripVariantDecorations("Etsuko's Exhaustive Coverage (G2/G3)"))
            assertEquals("Failed training (Get Well Soon!)", TrainingEventRecognizer.stripVariantDecorations("Failed training (Get Well Soon!)"))
            assertEquals("Marvelous☆Victory!", TrainingEventRecognizer.stripVariantDecorations("(❯❯❯)\nMarvelous☆Victory!"))
            assertEquals("Good Job!", TrainingEventRecognizer.stripVariantDecorations("Good Job!\nRandomly after training (repeatable)"))
            assertEquals("Extra Training to Blow Off Steam", TrainingEventRecognizer.stripVariantDecorations("Extra Training to Blow Off Steam"))
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("Acupuncture multi-stage gate")
    inner class AcupunctureGate {
        private val eventKey = TrainingEvent.ACUPUNCTURE_TREATMENT_EVENT_KEY
        private val trainee = "Gold City"
        private val tappedAt = 1_000L

        private fun pending(
            key: String = eventKey,
            who: String = trainee,
            index: Int = 2,
            expected: Int = 5,
            at: Long = tappedAt,
            tapped: Boolean = true,
        ) = TrainingEvent.AcupuncturePendingTreatment(key, who, index, expected, at, tapped)

        private fun act(
            state: TrainingEvent.AcupuncturePendingTreatment?,
            key: String = eventKey,
            who: String = trainee,
            count: Int? = 2,
            now: Long = tappedAt + 5_400L,
        ) = TrainingEvent.decideAcupunctureGateAction(state, TrainingEvent.AcupunctureGateInput(key, who, count, now))

        @Test
        fun `the recorded live sequence declines once and never reaches the clamp`() {
            // Steps 1 and 2: the five-row choice screen, and the per-trainee pick the presets ship.
            val decision = TrainingEvent.decideSpecialEventOption(specialOverrideIndex = 2, characterOverrideIndex = 2, eventOptionCount = 5)
            assertEquals(2, decision.optionIndex, "the configured Option 3 is index 2")
            assertTrue(decision.usedCharacterOverride)
            assertFalse(decision.clamped)

            // Step 3: the initial tap takes row 2 of five, needing no repair.
            val initialPlan = TrainingEvent.planOptionTap(decision.optionIndex, availableRows = 5, rescanned = false)
            assertEquals(TrainingEvent.OptionTapAction.USE_ROW, initialPlan.action)
            assertEquals(2, initialPlan.rowIndex)

            // Steps 4 and 5: that tap is remembered, and the same-title two-row gate follows it.
            val action = act(pending(), count = 2, now = tappedAt + 4_400L)

            // Steps 6 and 7: the gate is recognized, and answered on row 0.
            assertEquals(TrainingEvent.AcupunctureGateAction.DECLINE_AND_CLEAR, action)
            assertEquals(0, TrainingEvent.ACUPUNCTURE_GATE_DECLINE_ROW)

            // Step 8: nothing survives the action, so the following pass has no state to act on.
            assertEquals(TrainingEvent.AcupunctureGateAction.NONE, act(null, count = 2))

            // Steps 9 to 11: what the generic repair would otherwise do here. It asks for a rescan,
            // then clamps onto row 1, which is the Reconsider row that put the five choices back on
            // screen and began the next cycle. The gate action runs first, so neither happens and
            // the configured option is never tapped a second time.
            assertEquals(TrainingEvent.OptionTapAction.RESCAN, TrainingEvent.planOptionTap(decision.optionIndex, availableRows = 2, rescanned = false).action)
            val clamped = TrainingEvent.planOptionTap(decision.optionIndex, availableRows = 2, rescanned = true)
            assertEquals(TrainingEvent.OptionTapAction.CLAMP_TO_LAST_ROW, clamped.action)
            assertEquals(1, clamped.rowIndex, "the clamp tapped Reconsider, which is the loop")
            assertNotEquals(TrainingEvent.ACUPUNCTURE_GATE_DECLINE_ROW, clamped.rowIndex)
        }

        @Test
        fun `the gate cannot be handled twice and a cleared state cannot be reused`() {
            assertEquals(TrainingEvent.AcupunctureGateAction.DECLINE_AND_CLEAR, act(pending(), count = 2))
            // The runtime clears before tapping, so every later look starts from no state at all.
            assertEquals(TrainingEvent.AcupunctureGateAction.NONE, act(null, count = 2, now = tappedAt + 6_000L))
            assertEquals(TrainingEvent.AcupunctureGateAction.NONE, act(null, count = 5, now = tappedAt + 7_000L))
        }

        @Test
        fun `a pending tap expires after its bounded window`() {
            assertEquals(
                TrainingEvent.AcupunctureGateAction.DECLINE_AND_CLEAR,
                act(pending(), count = 2, now = tappedAt + TrainingEvent.ACUPUNCTURE_GATE_WINDOW_MILLIS),
            )
            assertEquals(
                TrainingEvent.AcupunctureGateAction.CLEAR_STALE,
                act(pending(), count = 2, now = tappedAt + TrainingEvent.ACUPUNCTURE_GATE_WINDOW_MILLIS + 1),
                "an expired tap must be dropped, never declined",
            )
        }

        @Test
        fun `a different trainee on the same screen never declines`() {
            assertEquals(TrainingEvent.AcupunctureGateAction.CLEAR_STALE, act(pending(), who = "Daiwa Scarlet", count = 2))
        }

        @Test
        fun `an unreadable trainee name on both passes still answers its own gate`() {
            // resolveActiveTraineeName yields "" when neither the applied preset row nor the career
            // header can be read. Blank on the arming pass AND on the gate pass is the same unknown
            // career seen twice, so the name neither proves nor contradicts identity and the other
            // checks (exact event title, bounded window, option count) decide. Pinning that: the
            // gate is still answered, rather than dropping the tap into the clamp-to-Reconsider loop.
            assertEquals(
                TrainingEvent.AcupunctureGateAction.DECLINE_AND_CLEAR,
                act(pending(who = ""), who = "", count = 2),
                "blank on both sides is not a contradiction",
            )
            // A blank on one side only IS a contradiction and still discards the tap.
            assertEquals(
                TrainingEvent.AcupunctureGateAction.CLEAR_STALE,
                act(pending(who = ""), who = trainee, count = 2),
                "the name became readable, so this is a different screen",
            )
            assertEquals(
                TrainingEvent.AcupunctureGateAction.CLEAR_STALE,
                act(pending(), who = "", count = 2),
                "the name stopped being readable, so identity is no longer proven",
            )
        }

        @Test
        fun `a different event title never declines`() {
            assertEquals(TrainingEvent.AcupunctureGateAction.CLEAR_STALE, act(pending(), key = "Victory! (G1)\n1st", count = 2))
        }

        @Test
        fun `the acupuncture support events never reach this state machine`() {
            // The special-event pattern table matches the bare word "Acupuncture", so identity here
            // is exact instead of pattern-based; these are ordinary single-stage support events.
            for (other in listOf(
                "The Applications of Acupuncture",
                "An Accurate Acupuncturist ☆\nRandomly after training (repeatable)",
                "An Assuring Acupuncturist Appears! ☆",
            )) {
                assertFalse(TrainingEvent.isAcupunctureTreatmentEvent(other), "$other must not count as the multi-stage event")
                assertEquals(TrainingEvent.AcupunctureGateAction.CLEAR_STALE, act(pending(), key = other, count = 2), other)
            }
            assertTrue(TrainingEvent.isAcupunctureTreatmentEvent(TrainingEvent.ACUPUNCTURE_TREATMENT_EVENT_KEY))
        }

        @Test
        fun `with no pending tap an ordinary two-row mismatch keeps the existing planner behavior`() {
            assertEquals(TrainingEvent.AcupunctureGateAction.NONE, act(null, count = 2))
            assertEquals(TrainingEvent.OptionTapAction.RESCAN, TrainingEvent.planOptionTap(2, availableRows = 2, rescanned = false).action)
            assertEquals(TrainingEvent.OptionTapAction.CLAMP_TO_LAST_ROW, TrainingEvent.planOptionTap(2, availableRows = 2, rescanned = true).action)
        }

        @Test
        fun `the choice screen itself is never mistaken for the gate`() {
            assertEquals(TrainingEvent.AcupunctureGateAction.NONE, act(pending(), count = 5))
        }

        @Test
        fun `an unreadable count neither infers the gate nor discards the state`() {
            for (count in listOf(null, 0)) {
                assertEquals(TrainingEvent.AcupunctureGateAction.NONE, act(pending(), count = count), "count=$count must decide nothing")
            }
        }

        @Test
        fun `a shape that is neither the gate nor the choice list drops the state`() {
            for (count in listOf(1, 3, 4, 6)) {
                assertEquals(TrainingEvent.AcupunctureGateAction.CLEAR_STALE, act(pending(), count = count), "count=$count")
            }
        }

        @Test
        fun `a state that does not record an issued tap is discarded rather than acted on`() {
            assertEquals(TrainingEvent.AcupunctureGateAction.CLEAR_STALE, act(pending(tapped = false), count = 2))
        }

        @Test
        fun `the per-trainee override still supplies the index on the five-row screen`() {
            // Against the shipped generic default of Option 5, exactly as the presets configure it.
            val decision = TrainingEvent.decideSpecialEventOption(specialOverrideIndex = 4, characterOverrideIndex = 2, eventOptionCount = 5)
            assertEquals(2, decision.optionIndex)
            assertTrue(decision.usedCharacterOverride)
            assertFalse(decision.clamped, "Option 3 is in bounds on the five-option data")
        }

        @Test
        fun `the decline row is not the configured option, so the fallback cannot read as success`() {
            val state = pending(index = 2)
            assertEquals(TrainingEvent.AcupunctureGateAction.DECLINE_AND_CLEAR, act(state, count = 2))
            assertNotEquals(
                state.optionIndex,
                TrainingEvent.ACUPUNCTURE_GATE_DECLINE_ROW,
                "row 0 is the decline; the configured option is a different row and was not applied",
            )
        }

        @Test
        fun `the shipped Acupuncture data still carries five options for every character`() {
            // The state only ever arms on a five-row screen, so a data refresh that moved this count
            // would silently disarm the whole path.
            for (owner in shippedCharacters.keys().asSequence().toList()) {
                val events = shippedCharacters.getJSONObject(owner)
                assertTrue(events.has(TrainingEvent.ACUPUNCTURE_TREATMENT_EVENT_KEY), "$owner is missing the Acupuncture event")
                assertEquals(
                    TrainingEvent.ACUPUNCTURE_TREATMENT_OPTION_COUNT,
                    events.getJSONArray(TrainingEvent.ACUPUNCTURE_TREATMENT_EVENT_KEY).length(),
                    "$owner's Acupuncture option count moved; the arming condition assumes five",
                )
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("Determinism")
    inner class Determinism {
        private fun fixture(owners: List<String>): JSONObject {
            val root = JSONObject()
            for (owner in owners) {
                val events = JSONObject()
                events.put("Victory! (G1)\n1st", JSONArray(listOf("Energy -15\nstat +10", "Energy -5/-20\nstat +10")))
                events.put("Victory! (G2/G3)\n1st", JSONArray(listOf("Energy -15\nstat +8", "Energy -5/-20\nstat +8")))
                root.put(owner, events)
            }
            return root
        }

        private fun selectFromFixture(owners: List<String>, activeTrainee: String) =
            TrainingEventRecognizer.selectSpecialEvent(
                specialEventName = "Victory!",
                ocrTitle = "Victory!",
                scenarioEvents = null,
                scenarioName = "URA Finale",
                characterEventData = fixture(owners),
                supportEventData = null,
                activeTraineeName = activeTrainee,
                lastRaceGrade = RaceGrade.G1,
                visibleOptionCount = 2,
            )

        @Test
        fun `the result does not depend on JSON insertion order`() {
            val forward = listOf("Agnes Tachyon", "Biwa Hayahide", "Curren Chan", "Daiwa Scarlet")
            val a = selectFromFixture(forward, activeTrainee = "")
            val b = selectFromFixture(forward.reversed(), activeTrainee = "")
            assertNotNull(a)
            assertEquals(a!!.eventTitle, b!!.eventTitle)
            assertEquals(a.ownerName, b.ownerName)
            // With no active trainee the tiebreak is lexicographic, never iteration order.
            assertEquals("Agnes Tachyon", a.ownerName)
        }

        @Test
        fun `an active trainee always wins attribution over the lexicographic fallback`() {
            val owners = listOf("Agnes Tachyon", "Biwa Hayahide", "Curren Chan", "Daiwa Scarlet")
            assertEquals("Daiwa Scarlet", selectFromFixture(owners, activeTrainee = "Daiwa Scarlet")!!.ownerName)
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    @DisplayName("Shipped-data contract")
    inner class ShippedDataContract {
        @Test
        fun `the graded race-result families exist under every character with two options`() {
            val owners = shippedCharacters.keys().asSequence().toList()
            assertTrue(owners.size >= 60, "expected the full character roster, got ${owners.size}")
            for (owner in owners) {
                val events = shippedCharacters.getJSONObject(owner)
                for (family in raceFamilies) {
                    for (key in listOf(family.g1Key, family.g2g3Key, family.preOpKey)) {
                        assertTrue(events.has(key), "$owner is missing \"${readable(key)}\"")
                        assertEquals(2, events.getJSONArray(key).length(), "$owner's \"${readable(key)}\" option count moved")
                    }
                }
            }
        }

        @Test
        fun `the plain-name race-result keys stay Gold City's one-option card events`() {
            val owners = shippedCharacters.keys().asSequence().toList()
            for (family in raceFamilies) {
                val cardOwners = owners.filter { shippedCharacters.getJSONObject(it).has(family.goldCityCardKey) }
                assertEquals(listOf("Gold City"), cardOwners, "\"${family.goldCityCardKey}\" ownership moved; revisit the card-copy gate")
                assertEquals(
                    1,
                    shippedCharacters.getJSONObject("Gold City").getJSONArray(family.goldCityCardKey).length(),
                    "Gold City's \"${family.goldCityCardKey}\" option count moved; the matrix assumes one option",
                )
            }
        }

        @Test
        fun `the other special families the pattern table routes to still exist`() {
            val sample = shippedCharacters.getJSONObject(shippedCharacters.keys().asSequence().first())
            assertTrue(sample.has("Failed training (Get Well Soon!)"))
            assertTrue(sample.has("Failed training (Don't Overdo It!)"))
            assertEquals(5, sample.getJSONArray("Acupuncture (Just an Acupuncturist, No Worries! ☆)").length())
            assertTrue(sample.has("Etsuko's Elated Coverage (G1)"))
            assertTrue(sample.has("Etsuko's Exhaustive Coverage (G1)"))
        }

        @Test
        fun `the single-owner gate covers Gold City's trio and never a graded common copy`() {
            val gated = TrainingEventRecognizer.computeSingleOwnerSpecialFamilyKeys(shippedCharacters)
            assertTrue(gated.containsAll(setOf("Victory!", "Solid Showing", "Defeat")), "Gold City's card trio must be gated, got $gated")
            assertTrue(
                gated.none { it.contains("(G1)") || it.contains("(G2/G3)") || it.contains("(Pre/OP)") },
                "graded common copies must never be gated, got $gated",
            )
        }
    }
}
