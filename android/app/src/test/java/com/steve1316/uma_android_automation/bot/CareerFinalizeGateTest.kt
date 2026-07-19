package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for the career-finalization guard. Pins the incident that motivated it - a sparks
 * career finished with 716 unspent points after a committed, successful-looking careerComplete
 * session - and the hardened decision model: acceptance is candidate-exhaustion EVIDENCE, never
 * a fixed balance threshold; the popup is a two-read consistency check, never the sole truth;
 * and a verdict is scoped to the exact career finalization it was armed for.
 */
@DisplayName("Career finalization guard")
class CareerFinalizeGateTest {
    /** Session evidence shaped like the real incident unless a test narrows it. */
    private fun evidence(
        sp: Int?,
        outcome: SkillSpendOutcome = SkillSpendOutcome.COMMITTED,
        scan: Boolean = true,
        planner: Boolean = true,
        confirmation: Boolean = true,
        fallback: Boolean = true,
        eligible: Int = 0,
        affordable: Int = 0,
        cheapestAffordableName: String? = null,
        cheapestAffordable: Int? = null,
        cheapestEligible: Int? = null,
        excluded: Map<String, Int> = mapOf("wrong_axes" to 12, "negative" to 3, "inherited_unique" to 2),
        trigger: SkillCheckTrigger? = SkillCheckTrigger.CAREER_COMPLETE,
    ) = FinalizeEvidence(
        sessionOutcome = outcome,
        trigger = trigger,
        planKey = "careerComplete",
        scanComplete = scan,
        plannerComplete = planner,
        confirmationComplete = confirmation,
        fallbackAttempted = fallback,
        verifiedRemainingSp = sp,
        eligibleCandidateCount = eligible,
        affordableEligibleCandidateCount = affordable,
        cheapestAffordableEligibleName = cheapestAffordableName,
        cheapestAffordableEligiblePrice = cheapestAffordable,
        cheapestEligiblePrice = cheapestEligible,
        excludedByReason = excluded,
        timestampMs = 1_784_395_067_186L,
    )

    private fun eval(
        detailsSp: Int?,
        ev: FinalizeEvidence?,
        retryUsed: Boolean,
        mode: SkillSpendMode = SkillSpendMode.ADAPTIVE,
    ) = evaluateCareerFinalization(mode, detailsSp, ev, retryUsed)

    @AfterEach
    fun clearGate() {
        CareerFinalizeGate.reset()
    }

    @Nested
    @DisplayName("affordable candidates always block")
    inner class AffordableBlocks {
        @Test
        fun `716 with affordable compatible candidates is not finishable before the retry`() {
            val ev = evidence(716, eligible = 5, affordable = 5, cheapestAffordableName = "Medium Straightaways ○", cheapestAffordable = 80, cheapestEligible = 80)
            val result = eval(716, ev, retryUsed = false)
            assertEquals(FinalizeDecision.RETRY_SPEND, result.decision)
        }

        @Test
        fun `716 with an affordable compatible candidate after the retry blocks with the exact candidate in the reason`() {
            val ev = evidence(716, eligible = 2, affordable = 1, cheapestAffordableName = "Up-Tempo", cheapestAffordable = 96, cheapestEligible = 96)
            val result = eval(716, ev, retryUsed = true)
            assertEquals(FinalizeDecision.BLOCK, result.decision)
            assertTrue("716" in result.reason)
            assertTrue("Up-Tempo" in result.reason, "the cheapest affordable candidate must be named")
            assertTrue("96" in result.reason, "its price must be reported")
        }

        @Test
        fun `350 with a 50-point compatible candidate blocks - a fixed balance bound would have finished here`() {
            val ev = evidence(350, eligible = 1, affordable = 1, cheapestAffordableName = "Hakodate Racecourse ○", cheapestAffordable = 50, cheapestEligible = 50)
            assertEquals(FinalizeDecision.RETRY_SPEND, eval(350, ev, retryUsed = false).decision)
            assertEquals(FinalizeDecision.BLOCK, eval(350, ev, retryUsed = true).decision)
        }
    }

    @Nested
    @DisplayName("acceptance requires exhaustion proof")
    inner class AcceptanceProofs {
        @Test
        fun `351 with zero eligible candidates after a complete scan may finish`() {
            val result = eval(351, evidence(351, eligible = 0, affordable = 0), retryUsed = false)
            assertEquals(FinalizeDecision.FINISH, result.decision)
        }

        @Test
        fun `716 with zero eligible candidates may finish only with the exclusion reasons recorded`() {
            val result = eval(716, evidence(716, eligible = 0, affordable = 0), retryUsed = false)
            assertEquals(FinalizeDecision.FINISH, result.decision)
            assertTrue("wrong_axes=12" in result.reason, "the exclusion evidence must be explicit in the reason")
            assertTrue("negative=3" in result.reason)
        }

        @Test
        fun `a remainder below the cheapest eligible price may finish`() {
            val result = eval(100, evidence(100, eligible = 3, affordable = 0, cheapestEligible = 130), retryUsed = false)
            assertEquals(FinalizeDecision.FINISH, result.decision)
            assertTrue("130" in result.reason, "the cheapest eligible price is the proof and belongs in the reason")
        }

        @Test
        fun `no price-floor shortcut exists - even a tiny balance needs complete exhaustion evidence`() {
            // The packaged data prices purchasable negatives at 40 and never bounds hint
            // discounts, so no scan-free floor is provable (see SkillDataFloorTest). A tiny
            // balance without a complete scan is unproven like any other.
            assertEquals(
                FinalizeDecision.RETRY_SPEND,
                eval(10, evidence(10, scan = false, eligible = 0), retryUsed = false).decision,
            )
            // With complete evidence the same balance finishes through the normal proofs.
            assertEquals(
                FinalizeDecision.FINISH,
                eval(10, evidence(10, eligible = 4, affordable = 0, cheapestEligible = 42), retryUsed = false).decision,
                "10 points below the cheapest eligible candidate is proven unspendable",
            )
            assertEquals(
                FinalizeDecision.FINISH,
                eval(0, evidence(0, eligible = 0, affordable = 0), retryUsed = false).decision,
                "zero balance with zero eligible candidates is proven exhausted",
            )
        }

        @Test
        fun `inconsistent candidate evidence never finishes`() {
            // Eligible candidates counted, none affordable, yet no cheapest price - contradictory.
            val ev = evidence(500, eligible = 2, affordable = 0, cheapestEligible = null)
            assertEquals(FinalizeDecision.BLOCK, eval(500, ev, retryUsed = true).decision)
        }
    }

    @Nested
    @DisplayName("incomplete evidence blocks regardless of balance")
    inner class CompletenessBlocks {
        @Test
        fun `an incomplete scan blocks regardless of the balance`() {
            for (sp in listOf(42, 90, 716)) {
                val result = eval(sp, evidence(sp, scan = false), retryUsed = true)
                assertEquals(FinalizeDecision.BLOCK, result.decision, "sp=$sp")
                assertTrue("scan" in result.reason, "the reason must name the incomplete stage")
            }
        }

        @Test
        fun `a parse failure blocks regardless of the balance`() {
            for (sp in listOf(50, 716)) {
                val ev = evidence(sp, outcome = SkillSpendOutcome.ABORTED_PARSE, planner = false, scan = false)
                assertEquals(FinalizeDecision.BLOCK, eval(sp, ev, retryUsed = true).decision, "sp=$sp")
            }
        }

        @Test
        fun `an entry failure blocks - the session never produced careerComplete evidence`() {
            // ABORTED_ENTRY is emitted by Campaign when the Learn screen never opened: no
            // careerComplete evidence exists, which is the same missing-session block.
            assertEquals(FinalizeDecision.BLOCK, eval(716, null, retryUsed = true).decision)
        }

        @Test
        fun `a purchase confirmation failure blocks regardless of the balance`() {
            for ((outcome, confirmation) in listOf(SkillSpendOutcome.COMMIT_UNVERIFIED to false, SkillSpendOutcome.COMMITTED to false)) {
                val ev = evidence(716, outcome = outcome, confirmation = confirmation)
                val result = eval(716, ev, retryUsed = true)
                assertEquals(FinalizeDecision.BLOCK, result.decision, "$outcome")
                assertTrue("confirmation" in result.reason)
            }
        }

        @Test
        fun `a missing final session blocks`() {
            val result = eval(716, null, retryUsed = true)
            assertEquals(FinalizeDecision.BLOCK, result.decision)
            assertTrue("no careerComplete skill-spend session ran" in result.reason)
        }

        @Test
        fun `a session from the wrong trigger is not careerComplete evidence`() {
            val ev = evidence(716, trigger = SkillCheckTrigger.HIGH_WATER)
            assertEquals(FinalizeDecision.BLOCK, eval(716, ev, retryUsed = true).decision)
        }

        @Test
        fun `an unavailable verified balance blocks`() {
            val result = eval(716, evidence(sp = null), retryUsed = true)
            assertEquals(FinalizeDecision.BLOCK, result.decision)
            assertTrue("unavailable" in result.reason)
        }

        @Test
        fun `a stale balance - Details disagreeing with the session - blocks`() {
            val result = eval(716, evidence(55), retryUsed = true)
            assertEquals(FinalizeDecision.BLOCK, result.decision)
            assertTrue("716" in result.reason)
            assertTrue("55" in result.reason)
        }

        @Test
        fun `incomplete evidence never finishes - the unreadable-popup proceed path is unreachable without complete proof`() {
            // The navigator proceeds on an unreadable popup ONLY behind an approved verdict;
            // this property guarantees no approved verdict exists with incomplete evidence.
            val incomplete =
                listOf(
                    evidence(716, scan = false),
                    evidence(716, planner = false, outcome = SkillSpendOutcome.ABORTED_PARSE),
                    evidence(716, confirmation = false),
                    evidence(sp = null),
                    null,
                )
            for (ev in incomplete) {
                for (retryUsed in listOf(false, true)) {
                    val decision = eval(716, ev, retryUsed).decision
                    assertFalse(decision == FinalizeDecision.FINISH, "ev=$ev retryUsed=$retryUsed must not finish")
                }
            }
        }
    }

    @Nested
    @DisplayName("retry budget and parity")
    inner class RetryAndParity {
        @Test
        fun `a spent retry never yields another retry - no loop, no repeated Finish attempts`() {
            for (sp in listOf(42, 351, 716, 5000)) {
                for (ev in listOf(null, evidence(sp, eligible = 1, affordable = 1, cheapestAffordable = 60, cheapestEligible = 60), evidence(sp, scan = false))) {
                    val decision = eval(sp, ev, retryUsed = true).decision
                    assertFalse(decision == FinalizeDecision.RETRY_SPEND, "sp=$sp ev=$ev")
                }
            }
        }

        @Test
        fun `manual mode always finishes - the guard is adaptive-only`() {
            for (sp in listOf(0, 55, 716, 5000)) {
                for (retryUsed in listOf(false, true)) {
                    val result = eval(sp, null, retryUsed, mode = SkillSpendMode.MANUAL)
                    assertEquals(FinalizeDecision.FINISH, result.decision, "manual sp=$sp retryUsed=$retryUsed")
                }
            }
        }

        @Test
        fun `the block reason directs the operator to act manually and never suggests deleting the career`() {
            val result = eval(716, evidence(716, eligible = 1, affordable = 1, cheapestAffordable = 66, cheapestEligible = 66), retryUsed = true)
            assertTrue("Not pressing Finish" in result.reason)
            assertTrue("left untouched" in result.reason)
            assertFalse("delete" in result.reason.lowercase(), "the guard must never point toward career deletion")
        }
    }

    @Nested
    @DisplayName("popup consistency check")
    inner class PopupChecks {
        @Test
        fun `a readable matching popup permits Finish`() {
            assertFalse(popupContradictsVerifiedBalance(40, 40, null))
        }

        @Test
        fun `a first contradiction alone does not block - the reread deciding read may agree`() {
            assertFalse(popupContradictsVerifiedBalance(40, 700, 40), "second read agreeing resolves the ghost")
            assertFalse(popupContradictsVerifiedBalance(40, 700, null), "one readable contradiction plus an unreadable read stays inconclusive")
        }

        @Test
        fun `two readable contradictions block`() {
            assertTrue(popupContradictsVerifiedBalance(40, 700, 700))
            assertTrue(popupContradictsVerifiedBalance(40, 700, 716), "the two reads need not agree with each other - both contradicting the verified balance is enough")
        }

        @Test
        fun `an unreadable popup does not contradict - the approved verdict's complete proof carries`() {
            assertFalse(popupContradictsVerifiedBalance(40, null, null))
        }
    }

    @Nested
    @DisplayName("popup line parser")
    inner class PopupParser {
        @Test
        fun `parses the exact dialog line`() {
            assertEquals(716, parseRemainingSkillPoints("Remaining Skill Points: 716 pts"))
        }

        @Test
        fun `tolerates casing, line breaks, commas, and missing colon`() {
            assertEquals(716, parseRemainingSkillPoints("remaining skill points 716"))
            assertEquals(716, parseRemainingSkillPoints("Remaining Skill\nPoints: 716 pts"))
            assertEquals(1024, parseRemainingSkillPoints("Remaining Skill Points: 1,024 pts"))
            assertEquals(55, parseRemainingSkillPoints("Complete the career?\nRemaining Skill Points: 55 pts\nCancel Finish"))
        }

        @Test
        fun `tolerates the l-for-i OCR swaps the game font produces`() {
            assertEquals(716, parseRemainingSkillPoints("Remaining Skil1 Polnts: 716 pts"))
            assertEquals(716, parseRemainingSkillPoints("Remalning SkiII Points: 716 pts"))
        }

        @Test
        fun `absent phrase or implausible number returns null rather than a guess`() {
            assertNull(parseRemainingSkillPoints(""))
            assertNull(parseRemainingSkillPoints("Complete the career?"))
            assertNull(parseRemainingSkillPoints("Skill Points: 716"), "the Remaining prefix is required - other screens show bare Skill Points")
            assertNull(parseRemainingSkillPoints("Remaining Skill Points: 7161234 pts"), "seven digits is OCR concatenation, not a balance")
        }

        @Test
        fun `does not grab unrelated digits from elsewhere in the dialog text`() {
            assertNull(parseRemainingSkillPoints("Fans: 280150\nCancel Finish"))
        }
    }

    @Nested
    @DisplayName("verdict scoping and lifecycle")
    inner class VerdictScoping {
        private fun verdict(token: String, armedAt: Long, approved: Boolean = true) =
            FinalizeVerdict(
                careerToken = token,
                queueRun = 2,
                trainee = "Super Creek",
                scenario = "Unity Cup",
                objective = "sparks",
                approved = approved,
                verifiedRemainingSp = 40,
                sessionTimestampMs = armedAt - 60_000L,
                reason = "test",
                armedAtMs = armedAt,
            )

        @Test
        fun `a fresh verdict matching the captured token is usable`() {
            val now = 2_000_000_000L
            assertTrue(finalizeVerdictUsable(verdict("Super Creek|Unity Cup|1", now - 5_000), "Super Creek|Unity Cup|1", now))
        }

        @Test
        fun `a verdict from a previous career is rejected by its token`() {
            val now = 2_000_000_000L
            val previous = verdict("Copano Rickey|URA Finale|0", now - 5_000)
            assertFalse(finalizeVerdictUsable(previous, "Super Creek|Unity Cup|1", now))
        }

        @Test
        fun `a verdict from a previous queue run of the same trainee is rejected - the token timestamp differs`() {
            val now = 2_000_000_000L
            val previousRun = verdict("Super Creek|Unity Cup|100", now - 5_000)
            assertFalse(finalizeVerdictUsable(previousRun, "Super Creek|Unity Cup|200", now))
        }

        @Test
        fun `a verdict armed after this navigation began never matches the captured token`() {
            val now = 2_000_000_000L
            // Navigation captured no token (gate was empty when it started); a verdict armed
            // later can never be adopted by it.
            assertFalse(finalizeVerdictUsable(verdict("Super Creek|Unity Cup|300", now - 1_000), null, now))
        }

        @Test
        fun `an expired verdict is rejected - a service restart or abandoned finalization cannot reuse it`() {
            val now = 2_000_000_000L
            val old = verdict("Super Creek|Unity Cup|1", now - FINALIZE_VERDICT_MAX_AGE_MS - 1)
            assertFalse(finalizeVerdictUsable(old, "Super Creek|Unity Cup|1", now))
            val future = verdict("Super Creek|Unity Cup|1", now + 60_000)
            assertFalse(finalizeVerdictUsable(future, "Super Creek|Unity Cup|1", now), "a clock-skewed future verdict is equally unusable")
        }

        @Test
        fun `absence is never usable - process recreation loses the verdict and Finish is refused, not clicked on faith`() {
            assertFalse(finalizeVerdictUsable(null, "Super Creek|Unity Cup|1", 2_000_000_000L))
        }

        @Test
        fun `arming, clearing, and consumption round-trip`() {
            assertNull(CareerFinalizeGate.verdict, "gate starts clear")
            val v = verdict("Super Creek|Unity Cup|1", 1_000L, approved = false)
            CareerFinalizeGate.arm(v)
            assertEquals(false, CareerFinalizeGate.verdict?.approved)
            CareerFinalizeGate.clear()
            assertNull(CareerFinalizeGate.verdict, "cleared verdict must not leak into the next flow (manual stop, abort, Home return, and Finish consumption all clear)")
        }

        @Test
        fun `re-arming replaces the previous verdict entirely`() {
            CareerFinalizeGate.arm(verdict("A|X|1", 1_000L, approved = false))
            CareerFinalizeGate.arm(verdict("B|Y|2", 2_000L, approved = true))
            assertEquals("B|Y|2", CareerFinalizeGate.verdict?.careerToken)
            assertEquals(true, CareerFinalizeGate.verdict?.approved)
        }
    }

    @Nested
    @DisplayName("post-purchase candidate freshness")
    inner class CandidateFreshness {
        private fun candidate(
            name: String,
            price: Int,
            obtained: Boolean = false,
            virtual: Boolean = false,
            negative: Boolean = false,
            inherited: Boolean = false,
            doubleCircle: Boolean = false,
            matchesAxes: Boolean = true,
        ) = RemainingCandidate(name, price, obtained, virtual, negative, inherited, doubleCircle, matchesAxes)

        @Test
        fun `confirmed purchases disappear from the remaining eligible set`() {
            // The incident's two career-end buys, marked obtained by the verified purchase
            // flow, plus one genuinely remaining candidate.
            val result =
                classifyRemainingCandidates(
                    listOf(
                        candidate("Medium Corners ○", 66, obtained = true),
                        candidate("Long Corners ○", 99, obtained = true),
                        candidate("Up-Tempo", 96),
                    ),
                    remainingSp = 716,
                    skipDoubleCircleUpgrades = false,
                )
            assertEquals(1, result.eligibleCount, "the two purchased skills must not be counted")
            assertEquals("Up-Tempo", result.cheapestAffordableName)
        }

        @Test
        fun `an unconfirmed proposed purchase remains an affordable candidate - proposing is not buying`() {
            // A skill the planner proposed but whose purchase never confirmed stays
            // obtained=false on screen, so it must still count (and therefore block Finish).
            val result = classifyRemainingCandidates(listOf(candidate("Soft Step", 96)), remainingSp = 716, skipDoubleCircleUpgrades = false)
            assertEquals(1, result.affordableCount)
        }

        @Test
        fun `a purchased base drops out and only its now-rendered upgrade tier counts`() {
            // buy() swaps in-place tiers: the base becomes obtained, the real next tier stops
            // being virtual. A still-virtual deeper tier must not count as purchasable.
            val result =
                classifyRemainingCandidates(
                    listOf(
                        candidate("Corner Recovery ○", 170, obtained = true),
                        candidate("Swinging Maestro", 340, virtual = false),
                        candidate("Deep Pockets", 500, virtual = true),
                    ),
                    remainingSp = 400,
                    skipDoubleCircleUpgrades = false,
                )
            assertEquals(1, result.eligibleCount, "only the rendered upgrade tier remains a candidate")
            assertEquals(340, result.cheapestAffordablePrice)
        }

        @Test
        fun `affordability is computed against the verified post-purchase balance`() {
            val candidates = listOf(candidate("Pressure", 190))
            val before = classifyRemainingCandidates(candidates, remainingSp = 200, skipDoubleCircleUpgrades = false)
            assertEquals(1, before.affordableCount)
            val after = classifyRemainingCandidates(candidates, remainingSp = 150, skipDoubleCircleUpgrades = false)
            assertEquals(0, after.affordableCount, "the same candidate stops being affordable once the balance dropped below its price")
            assertEquals(190, after.cheapestEligiblePrice)
        }

        @Test
        fun `exclusion counts describe only the remaining candidates, never purchased or unrendered rows`() {
            val result =
                classifyRemainingCandidates(
                    listOf(
                        candidate("Bought Negative", 40, obtained = true, negative = true),
                        candidate("Virtual Wrong Axis", 120, virtual = true, matchesAxes = false),
                        candidate("Remaining Wrong Axis", 120, matchesAxes = false),
                        candidate("Remaining Negative", 40, negative = true),
                    ),
                    remainingSp = 716,
                    skipDoubleCircleUpgrades = false,
                )
            assertEquals(0, result.eligibleCount)
            assertEquals(mapOf("wrong_axes" to 1, "negative" to 1), result.excludedByReason, "purchased and virtual rows contribute to no bucket at all")
        }

        @Test
        fun `every remaining candidate lands exactly once - eligible or one recorded exclusion`() {
            val result =
                classifyRemainingCandidates(
                    listOf(
                        candidate("A", 100),
                        candidate("B", 100, negative = true),
                        candidate("C", 100, inherited = true),
                        candidate("D ◎", 100, doubleCircle = true),
                        candidate("E", 100, matchesAxes = false),
                    ),
                    remainingSp = 50,
                    skipDoubleCircleUpgrades = true,
                )
            assertEquals(1, result.eligibleCount)
            assertEquals(4, result.excludedByReason.values.sum())
            assertEquals(setOf("negative", "inherited_unique", "double_circle", "wrong_axes"), result.excludedByReason.keys)
        }
    }

    @Nested
    @DisplayName("career token identity")
    inner class TokenIdentity {
        @Test
        fun `the token distinguishes queue runs, trainees, scenarios, and career instances`() {
            val base = buildCareerFinalizeToken("[Blue Farm] Super Creek", "Unity Cup", 2, "nonce1")
            assertEquals(base, buildCareerFinalizeToken("[Blue Farm] Super Creek", "Unity Cup", 2, "nonce1"), "stable for one career")
            assertFalse(base == buildCareerFinalizeToken("[Blue Farm] Super Creek", "Unity Cup", 3, "nonce1"), "run N cannot satisfy run N+1")
            assertFalse(base == buildCareerFinalizeToken("[Emperor's Path] Symboli Rudolf", "Unity Cup", 2, "nonce1"), "one trainee cannot satisfy another")
            assertFalse(base == buildCareerFinalizeToken("[Blue Farm] Super Creek", "URA Finale", 2, "nonce1"), "one scenario cannot satisfy another")
            assertFalse(base == buildCareerFinalizeToken("[Blue Farm] Super Creek", "Unity Cup", 2, "nonce2"), "two careers with identical identity still differ by the construction nonce")
        }

        @Test
        fun `outfit identity is part of the token - two outfits of one character never share it`() {
            val jade = buildCareerFinalizeToken("[Saintly Jade Cleric] Grass Wonder", "Unity Cup", 1, "n")
            val basecard = buildCareerFinalizeToken("Grass Wonder", "Unity Cup", 1, "n")
            assertFalse(jade == basecard)
        }

        @Test
        fun `single runs tokenize with run zero`() {
            assertTrue("run0" in buildCareerFinalizeToken("Super Creek", "Unity Cup", null, "n"))
        }
    }

    @Nested
    @DisplayName("run-result verdict clears")
    inner class RunResultClears {
        @Test
        fun `every non-COMPLETE run result clears the verdict - only a completed career finalizes`() {
            for (code in TaskResultCode.entries) {
                val expected = code != TaskResultCode.TASK_RESULT_COMPLETE
                assertEquals(expected, shouldClearVerdictForRunResult(code), code.name)
            }
        }

        @Test
        fun `manual stop, abort, error, breakpoint, and skip all clear`() {
            for (code in listOf(
                TaskResultCode.TASK_RESULT_MANUALLY_STOPPED,
                TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION,
                TaskResultCode.TASK_RESULT_BREAKPOINT_REACHED,
                TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE,
            )) {
                assertTrue(shouldClearVerdictForRunResult(code), code.name)
            }
            assertFalse(shouldClearVerdictForRunResult(TaskResultCode.TASK_RESULT_COMPLETE))
        }
    }

    @Nested
    @DisplayName("durable record shape")
    inner class RecordShape {
        @Test
        fun `the career_finalize record carries the full decision evidence`() {
            val ev = evidence(716, eligible = 2, affordable = 1, cheapestAffordableName = "Up-Tempo", cheapestAffordable = 96, cheapestEligible = 96)
            val record =
                SkillSpendTelemetry.buildCareerFinalizeRecord(
                    timestamp = 1_784_395_100_000L,
                    decision = "BLOCK",
                    reason = "UNSPENT_SKILL_POINTS: test",
                    careerToken = "Super Creek|Unity Cup|123",
                    trainee = "Super_Creek",
                    scenario = "Unity_Cup",
                    objective = "sparks",
                    queueRun = 2,
                    verifiedRemainingSp = 716,
                    retryUsed = true,
                    evidence = ev,
                )
            assertEquals("career_finalize", record.getString("type"))
            assertEquals("BLOCK", record.getString("finalizationDecision"))
            assertEquals(716, record.getInt("verifiedRemainingSp"))
            assertEquals(true, record.getBoolean("scanComplete"))
            assertEquals(true, record.getBoolean("constrainedFallbackAttempted"))
            assertEquals(false, record.getBoolean("constrainedFallbackExhausted"), "an affordable candidate remains, so the fallback is not exhausted")
            assertEquals(1, record.getInt("affordableEligibleCandidateCount"))
            assertEquals(96, record.getInt("cheapestAffordableEligiblePrice"))
            assertEquals("Up-Tempo", record.getString("cheapestAffordableEligibleName"))
            assertEquals(12, record.getJSONObject("excludedCandidateCountsByReason").getInt("wrong_axes"))
            assertEquals(true, record.getBoolean("retryUsed"))
            assertEquals("Super Creek|Unity Cup|123", record.getString("careerToken"))
        }
    }
}
