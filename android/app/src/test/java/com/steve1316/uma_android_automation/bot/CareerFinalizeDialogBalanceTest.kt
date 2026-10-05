package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A career-end BLOCK that rests on a skill-screen balance the game itself contradicted (a refused purchase the balance allowed) may be
 * settled by the Complete Career dialog's own balance: two agreeing reads below the cheapest candidate that was affordable at the
 * skill-screen read. Every other BLOCK, one read, a disagreement or a Grand Concert dialog keeps today's halt.
 */
@DisplayName("Career finalization settled by the Complete Career dialog balance")
class CareerFinalizeDialogBalanceTest {
    /** Shaped like the misread career: the skill screen read 99 (the game showed 66), Lucky Seven (99) refused, Firm Conditions 81 left. */
    private fun evidence(
        sp: Int? = 99,
        scan: Boolean = true,
        planner: Boolean = true,
        confirmation: Boolean = true,
        affordable: Int = 4,
        cheapestAffordable: Int? = 81,
        excluded: Map<String, Int> = mapOf(DEAD_TAP_EXCLUSION to 1, "wrong_axes" to 15),
        trigger: SkillCheckTrigger? = SkillCheckTrigger.CAREER_COMPLETE,
    ) = FinalizeEvidence(
        sessionOutcome = SkillSpendOutcome.COMMITTED,
        trigger = trigger,
        planKey = "careerComplete",
        scanComplete = scan,
        plannerComplete = planner,
        confirmationComplete = confirmation,
        fallbackAttempted = true,
        verifiedRemainingSp = sp,
        eligibleCandidateCount = 6,
        affordableEligibleCandidateCount = affordable,
        cheapestAffordableEligibleName = "Firm Conditions ○",
        cheapestAffordableEligiblePrice = cheapestAffordable,
        cheapestEligiblePrice = 81,
        excludedByReason = excluded,
        timestampMs = 1_791_000_000_000L,
    )

    private fun eval(ev: FinalizeEvidence?, retryUsed: Boolean = true, detailsSp: Int? = 99, mode: SkillSpendMode = SkillSpendMode.ADAPTIVE) =
        evaluateCareerFinalization(mode, detailsSp, ev, retryUsed)

    private fun verdict(sp: Int = 99, bound: Int? = 81, approved: Boolean = false) =
        FinalizeVerdict(
            careerToken = "[Autumn Cosmos] Gold City|URA Finale|run1|278d28fa",
            queueRun = 1,
            trainee = "[Autumn Cosmos] Gold City",
            scenario = "URA Finale",
            objective = "safe_completion",
            approved = approved,
            verifiedRemainingSp = sp,
            sessionTimestampMs = 1_791_000_000_000L,
            reason = "UNSPENT_SKILL_POINTS: test",
            armedAtMs = 1_791_000_010_000L,
            dialogResolvableBelowSp = bound,
        )

    private fun sp(value: Int) = CompleteCareerBalances.SkillPoints(value)

    private val unreadable = CompleteCareerBalances.Unreadable

    @Nested
    @DisplayName("which BLOCK the dialog may settle")
    inner class Evaluation {
        @Test
        fun `a refused purchase plus affordable candidates after the retry blocks with the cheapest affordable price as the bound`() {
            val result = eval(evidence())
            assertEquals(FinalizeDecision.BLOCK, result.decision)
            assertEquals(81, result.dialogResolvableBelowSp)
            assertTrue("Complete Career dialog decides" in result.reason, result.reason)
            assertTrue("fewer than 81 points" in result.reason, result.reason)
            assertFalse("left untouched" in result.reason, "the navigator may still press Finish, so the reason must not promise otherwise")
        }

        @Test
        fun `before the retry the same evidence re-runs the plan and carries no bound`() {
            val result = eval(evidence(), retryUsed = false)
            assertEquals(FinalizeDecision.RETRY_SPEND, result.decision)
            assertNull(result.dialogResolvableBelowSp)
        }

        @Test
        fun `affordable candidates without a refused purchase keep today's terminal BLOCK`() {
            val result = eval(evidence(excluded = mapOf("wrong_axes" to 15)))
            assertEquals(FinalizeDecision.BLOCK, result.decision)
            assertNull(result.dialogResolvableBelowSp)
            assertTrue("left untouched" in result.reason, result.reason)
        }

        @Test
        fun `a refused purchase never makes an incomplete or unverified session resolvable`() {
            val cases =
                mapOf(
                    "incomplete scan" to eval(evidence(scan = false)),
                    "incomplete planner" to eval(evidence(planner = false)),
                    "incomplete confirmation" to eval(evidence(confirmation = false)),
                    "no careerComplete session" to eval(null),
                    "other trigger" to eval(evidence(trigger = SkillCheckTrigger.HIGH_WATER)),
                    "no verified balance" to eval(evidence(sp = null)),
                    "stale details balance" to eval(evidence(), detailsSp = 120),
                    "no cheapest price" to eval(evidence(cheapestAffordable = null)),
                )
            for ((case, result) in cases) {
                assertEquals(FinalizeDecision.BLOCK, result.decision, case)
                assertNull(result.dialogResolvableBelowSp, case)
            }
        }

        @Test
        fun `nothing affordable still finishes on its own and Manual mode is untouched`() {
            assertEquals(FinalizeDecision.FINISH, eval(evidence(sp = 66, affordable = 0, cheapestAffordable = null), detailsSp = 66).decision)
            val manual = eval(evidence(), mode = SkillSpendMode.MANUAL)
            assertEquals(FinalizeDecision.FINISH, manual.decision)
            assertNull(manual.dialogResolvableBelowSp)
        }
    }

    @Nested
    @DisplayName("what the dialog must read")
    inner class DialogReads {
        @Test
        fun `two agreeing reads below the bound settle the misread career`() {
            assertTrue(dialogBalanceResolvesBlock(verdict(), sp(66), sp(66)))
            assertTrue(dialogBalanceResolvesBlock(verdict(), sp(80), sp(80)), "one point below the cheapest affordable candidate")
            assertTrue(dialogBalanceResolvesBlock(verdict(), sp(0), sp(0)))
        }

        @Test
        fun `a balance that still affords the cheapest candidate never settles it`() {
            assertFalse(dialogBalanceResolvesBlock(verdict(), sp(81), sp(81)))
            assertFalse(dialogBalanceResolvesBlock(verdict(), sp(99), sp(99)), "the dialog agreeing with the skill screen confirms the BLOCK")
            assertFalse(dialogBalanceResolvesBlock(verdict(), sp(120), sp(120)))
        }

        @Test
        fun `one read, a disagreement or an unreadable read never settles it`() {
            assertFalse(dialogBalanceResolvesBlock(verdict(), sp(66), sp(99)))
            assertFalse(dialogBalanceResolvesBlock(verdict(), sp(66), sp(60)))
            assertFalse(dialogBalanceResolvesBlock(verdict(), sp(66), unreadable))
            assertFalse(dialogBalanceResolvesBlock(verdict(), unreadable, sp(66)))
            assertFalse(dialogBalanceResolvesBlock(verdict(), unreadable, unreadable))
        }

        @Test
        fun `a Grand Concert performance-point dialog never settles it`() {
            val pp = CompleteCareerBalances.PerformancePoints(mapOf("da" to 10, "pa" to 10, "vo" to 10))
            assertFalse(dialogBalanceResolvesBlock(verdict(), pp, pp))
            assertFalse(dialogBalanceResolvesBlock(verdict(), pp, sp(66)))
            assertFalse(dialogBalanceResolvesBlock(verdict(), CompleteCareerBalances.PerformancePoints(emptyMap()), sp(66)))
        }

        @Test
        fun `only a resolvable BLOCK verdict can be settled`() {
            assertFalse(dialogBalanceResolvesBlock(verdict(bound = null), sp(66), sp(66)), "a plain BLOCK")
            assertFalse(dialogBalanceResolvesBlock(verdict(approved = true), sp(66), sp(66)), "an approved verdict has its own popup check")
            assertFalse(verdict(bound = null).blockResolvableByDialog())
            assertFalse(verdict(approved = true).blockResolvableByDialog())
            assertTrue(verdict().blockResolvableByDialog())
        }

        @Test
        fun `the dialog must read below the skill-screen balance, not just below the bound`() {
            assertFalse(dialogBalanceResolvesBlock(verdict(sp = 60, bound = 81), sp(60), sp(60)))
            assertFalse(dialogBalanceResolvesBlock(verdict(sp = 60, bound = 81), sp(70), sp(70)))
        }
    }

    @Nested
    @DisplayName("telemetry")
    inner class Telemetry {
        @Test
        fun `a settled BLOCK records the dialog balance as verified next to the skill-screen read`() {
            val record = SkillSpendTelemetry.buildDialogBalanceFinalizeRecord(1_791_000_020_000L, "FINISH", "settled", verdict(), 66, 66)
            assertEquals("career_finalize_dialog", record.getString("type"), "a second career_finalize row per token breaks the 1:1 finalize join")
            assertEquals("FINISH", record.getString("finalizationDecision"))
            assertEquals(66, record.getInt("verifiedRemainingSp"))
            assertEquals(99, record.getInt("skillScreenSp"))
            assertEquals(81, record.getInt("cheapestAffordableEligiblePrice"))
            assertEquals(66, record.getJSONArray("dialogRemainingSp").getInt(1))
            assertEquals("[Autumn_Cosmos]_Gold_City", record.getString("trainee"))
        }

        @Test
        fun `an unsettled BLOCK records the reads it saw and claims no verified balance`() {
            val record = SkillSpendTelemetry.buildDialogBalanceFinalizeRecord(1_791_000_020_000L, "BLOCK", "not settled", verdict(), 66, null)
            assertEquals("career_finalize_dialog", record.getString("type"))
            assertEquals("BLOCK", record.getString("finalizationDecision"))
            assertFalse(record.has("verifiedRemainingSp"))
            assertEquals(99, record.getInt("skillScreenSp"))
            assertTrue(record.getJSONArray("dialogRemainingSp").isNull(1))
        }
    }

    /** The navigator needs a live screen, so its wiring is pinned by source guards like the other between-run tests. */
    @Nested
    @DisplayName("navigator wiring")
    inner class NavigatorWiring {
        private fun repoFile(relative: String): File {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            repeat(8) {
                if (File(dir, relative).isFile) return File(dir, relative)
                dir = dir?.parentFile
            }
            throw AssertionError("$relative not found from the test working directory")
        }

        private val main = "android/app/src/main/java/com/steve1316/uma_android_automation"

        private val navigator by lazy { repoFile("$main/CareerLaunchNavigator.kt").readText().replace("\r\n", "\n") }

        private val campaign by lazy { repoFile("$main/bot/Campaign.kt").readText().replace("\r\n", "\n") }

        private fun body(signature: String): String {
            val start = navigator.indexOf(signature)
            assertTrue(start >= 0, "$signature not found")
            val end = Regex("\n    (/\\*\\*|// |(private |internal )?fun )").find(navigator, start + signature.length)?.range?.first ?: navigator.length
            return navigator.substring(start, end)
        }

        @Test
        fun `settling reads twice, decides with the pure rule, presses Cancel when unsettled and never presses Finish itself`() {
            val settle = body("private fun settleBlockWithDialogBalance(")
            assertEquals(2, Regex("readCompleteCareerBalances\\(\\)").findAll(settle).count())
            assertTrue("dialogBalanceResolvesBlock(verdict, first, second)" in settle)
            assertTrue("ButtonCancel.click(iu)" in settle)
            assertTrue("reasonKey = \"UNSPENT_SKILL_POINTS\"" in settle)
            assertFalse("ButtonFinish" in settle)
            assertTrue(settle.indexOf("return null") < settle.indexOf("ButtonCancel.click(iu)"), "Cancel is only on the unsettled path")
        }

        @Test
        fun `the dialog handler settles a resolvable BLOCK before Finish and blocks every other one`() {
            val handler = body("private fun handleCompleteCareerConfirmation(")
            val settle = handler.indexOf("settleBlockWithDialogBalance(resolvable)?.let { return it }")
            val block = handler.indexOf("finalizeBlockFailure(\"COMPLETE_CAREER_CONFIRMATION -> POST_RUN_RESULTS\")?.let { return it }")
            val finish = handler.indexOf("ButtonFinish.click(iu)")
            assertTrue(settle in 0 until finish, "settling must come before the Finish click")
            assertTrue(block in 0 until finish, "the plain BLOCK still fails before the Finish click")
            assertTrue("usableFinalizeVerdict()?.takeIf { it.blockResolvableByDialog() }" in handler)
            assertTrue("usableFinalizeVerdict()?.takeIf { it.approved }?.let { verdict ->" in handler, "the popup cross-check stays for approved verdicts only")
        }

        @Test
        fun `only the transitions into the dialog may pass a resolvable BLOCK`() {
            assertEquals(2, Regex("opensDialog = true").findAll(navigator).count())
            assertTrue("finalizeBlockFailure(\"CAREER_SUMMARY -> COMPLETE_CAREER_CONFIRMATION\", opensDialog = true)" in navigator)
            assertTrue("finalizeBlockFailure(\"POST_RUN_RESULTS -> COMPLETE_CAREER_CONFIRMATION\", opensDialog = true)" in navigator)
            val gate = body("private fun finalizeBlockFailure(")
            assertTrue("opensDialog && verdict != null && verdict.blockResolvableByDialog()" in gate)
        }

        @Test
        fun `the career task arms the bound and marks the ledger balance disputed`() {
            assertTrue("dialogResolvableBelowSp = evaluation.dialogResolvableBelowSp," in campaign)
            assertTrue("careerEndSkillPointsDisputed = verdict.blockResolvableByDialog()" in campaign)
            assertTrue("if (careerEndSkillPointsDisputed) append(\" skillPtsDisputed=true\")" in campaign)
            assertTrue("if (careerEndSkillPointsDisputed) put(\"skillPtsDisputed\", true)" in campaign)
        }
    }
}
