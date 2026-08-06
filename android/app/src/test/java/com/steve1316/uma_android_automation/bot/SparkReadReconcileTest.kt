package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The structural read-authority model for the Spark Selection pager.
 *
 * Anchored on the live 2026-08-06 defect: the pager's name OCR drops leading glyphs, the chooser
 * resolves the blue target by exact name, and one lost glyph ("Speed" read as "peed") demoted a
 * configured target to rank -1 and discarded the strictly better rerolled set. The reconciliation
 * repairs CONTENT NAMES ONLY, and only where the earlier capture of the same side in the same
 * transaction corroborates the pager read row for row.
 */
class SparkReadReconcileTest {
    private val tx = "abc12345"

    private fun stat(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.STAT)

    private fun apt(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.APTITUDE)

    private fun unique(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.UNIQUE)

    private fun white(name: String, stars: Int, cls: SparkWhiteClass) = SparkRowFact(name, stars, SparkRowKind.WHITE, cls)

    private fun reading(
        vararg rows: SparkRowFact,
        termination: SparkScanTermination = SparkScanTermination.COMPLETE_END_MARKER,
    ) = SparkSetReading(rows.toList(), termination, scrollsUsed = 2, sameFrameRetries = 1, sameFrameRecoveries = 1)

    private fun capture(
        reading: SparkSetReading,
        side: SparkSetSide = SparkSetSide.REROLLED,
        transactionId: String = tx,
    ) = SparkSideCapture(reading, side, transactionId)

    private fun reconcile(
        earlier: SparkSideCapture?,
        pager: SparkSetReading,
        side: SparkSetSide = SparkSetSide.REROLLED,
        transactionId: String = tx,
    ) = SparkReadReconcile.reconcile(side, transactionId, earlier, pager)

    @Nested
    @DisplayName("repairs that the structure proves")
    inner class Repairs {
        @Test
        fun `identical complete reads leave the pager read untouched`() {
            val rows = arrayOf(stat("Speed", 2), apt("Sprint", 2), unique("Super-Duper Climax", 1))
            val result = reconcile(capture(reading(*rows)), reading(*rows))

            assertEquals(SparkReadAuthority.PAGER_UNCHANGED, result.authority)
            assertTrue(result.structurallyAgreed)
            assertEquals(SparkReconcileRefusal.NONE, result.refusal)
            assertTrue(result.repairs.isEmpty())
            assertSame(result.pager, result.effective, "an unrepaired side must score the pager read itself")
        }

        @Test
        fun `a stat name that lost its leading glyph is repaired from the earlier capture`() {
            val result =
                reconcile(
                    capture(reading(stat("Speed", 2), apt("Sprint", 2))),
                    reading(stat("peed", 2), apt("Sprint", 2)),
                )

            assertEquals(SparkReadAuthority.PAGER_WITH_STRUCTURAL_NAME_REPAIR, result.authority)
            assertEquals("Speed", result.effective.rows[0].name)
            assertEquals(listOf(0), result.repairedRowIndexes)
            assertEquals("peed", result.repairs.single().pagerName)
            assertEquals("Speed", result.repairs.single().repairedName)
            assertEquals(SparkRowKind.STAT, result.repairs.single().kind)
        }

        @Test
        fun `an aptitude name that lost its leading glyph is repaired`() {
            val result =
                reconcile(
                    capture(reading(stat("Power", 1), apt("Sprint", 2))),
                    reading(stat("Power", 1), apt("print", 2)),
                )

            assertEquals(SparkReadAuthority.PAGER_WITH_STRUCTURAL_NAME_REPAIR, result.authority)
            assertEquals("Sprint", result.effective.rows[1].name)
            assertEquals(listOf(1), result.repairedRowIndexes)
        }

        @Test
        fun `a two-word aptitude name is repaired the same way`() {
            val result =
                reconcile(
                    capture(reading(stat("Guts", 1), apt("Late Surger", 3))),
                    reading(stat("Guts", 1), apt("ate Surger", 3)),
                )

            assertEquals(SparkReadAuthority.PAGER_WITH_STRUCTURAL_NAME_REPAIR, result.authority)
            assertEquals("Late Surger", result.effective.rows[1].name)
        }

        @Test
        fun `an unreadable pager name is repaired when the structure corroborates it`() {
            val result =
                reconcile(
                    capture(reading(stat("Wit", 2), apt("Turf", 1))),
                    reading(stat(SPARK_UNREADABLE_NAME, 2), apt("Turf", 1)),
                )

            assertEquals(SparkReadAuthority.PAGER_WITH_STRUCTURAL_NAME_REPAIR, result.authority)
            assertEquals("Wit", result.effective.rows[0].name)
            assertFalse(result.effective.rows[0].unreadable)
        }

        @Test
        fun `a repair changes nothing but the name -- stars, kind, order, count, side and scan all survive`() {
            val pager =
                reading(
                    stat("peed", 2),
                    apt("print", 2),
                    unique("Super-Duper Climax", 1),
                    white("JBC Sprint", 1, SparkWhiteClass.RACE),
                    termination = SparkScanTermination.COMPLETE_NO_PROGRESS,
                )
            val earlier =
                reading(
                    stat("Speed", 2),
                    apt("Sprint", 2),
                    unique("Super-Duper Climax", 1),
                    white("JBC Sprint", 1, SparkWhiteClass.RACE),
                    termination = SparkScanTermination.COMPLETE_NO_PROGRESS,
                )
            val result = reconcile(capture(earlier), pager)

            assertEquals(SparkSetSide.REROLLED, result.side)
            assertEquals(pager.rows.size, result.effective.rows.size)
            assertEquals(pager.termination, result.effective.termination)
            assertEquals(pager.scrollsUsed, result.effective.scrollsUsed)
            assertEquals(pager.sameFrameRetries, result.effective.sameFrameRetries)
            assertEquals(pager.sameFrameRecoveries, result.effective.sameFrameRecoveries)
            pager.rows.forEachIndexed { i, row ->
                assertEquals(row.kind, result.effective.rows[i].kind, "kind at row $i")
                assertEquals(row.stars, result.effective.rows[i].stars, "stars at row $i")
                assertEquals(row.whiteClass, result.effective.rows[i].whiteClass, "white class at row $i")
            }
            // Only the two closed-vocabulary rows moved.
            assertEquals(listOf(0, 1), result.repairedRowIndexes)
            assertEquals("Super-Duper Climax", result.effective.rows[2].name)
            assertEquals("JBC Sprint", result.effective.rows[3].name)
        }

        @Test
        fun `reconciliation is idempotent -- feeding the effective read back repairs nothing further`() {
            val earlier = reading(stat("Speed", 2), apt("Sprint", 2))
            val once = reconcile(capture(earlier), reading(stat("peed", 2), apt("print", 2)))
            val twice = reconcile(capture(earlier), once.effective)

            assertEquals(SparkReadAuthority.PAGER_UNCHANGED, twice.authority)
            assertTrue(twice.repairs.isEmpty())
        }
    }

    @Nested
    @DisplayName("kinds that are never repaired")
    inner class UnsupportedKinds {
        @Test
        fun `a skill-name disagreement is left alone`() {
            val result =
                reconcile(
                    capture(reading(stat("Speed", 2), white("Ignited Spirit SPD", 2, SparkWhiteClass.SKILL))),
                    reading(stat("Speed", 2), white("gnited Spirit SPD", 2, SparkWhiteClass.SKILL)),
                )

            assertEquals(SparkReadAuthority.PAGER_UNCHANGED, result.authority)
            assertEquals("gnited Spirit SPD", result.effective.rows[1].name)
        }

        @Test
        fun `a race-name disagreement is left alone`() {
            val result =
                reconcile(
                    capture(reading(stat("Speed", 2), white("Japanese Derby", 3, SparkWhiteClass.RACE))),
                    reading(stat("Speed", 2), white("lapanese Derby", 3, SparkWhiteClass.RACE)),
                )

            assertEquals(SparkReadAuthority.PAGER_UNCHANGED, result.authority)
            assertEquals("lapanese Derby", result.effective.rows[1].name)
        }

        @Test
        fun `a scenario-name disagreement is left alone`() {
            val result =
                reconcile(
                    capture(reading(stat("Speed", 2), white("Unity Cup", 2, SparkWhiteClass.RACE))),
                    reading(stat("Speed", 2), white("Jnity Cup", 2, SparkWhiteClass.RACE)),
                )

            assertEquals(SparkReadAuthority.PAGER_UNCHANGED, result.authority)
            assertEquals("Jnity Cup", result.effective.rows[1].name)
        }

        @Test
        fun `a unique-name disagreement is left alone`() {
            val result =
                reconcile(
                    capture(reading(stat("Speed", 2), unique("Our Ticket to Win!", 1))),
                    reading(stat("Speed", 2), unique("Dur Ticket to Win!", 1)),
                )

            assertEquals(SparkReadAuthority.PAGER_UNCHANGED, result.authority)
            assertEquals("Dur Ticket to Win!", result.effective.rows[1].name)
        }
    }

    @Nested
    @DisplayName("refusals: the structure does not prove the reads are the same list")
    inner class Refusals {
        private fun assertPagerOnly(result: SparkReadAuthorityResult, refusal: SparkReconcileRefusal) {
            assertEquals(SparkReadAuthority.PAGER_ONLY, result.authority)
            assertEquals(refusal, result.refusal)
            assertTrue(result.repairs.isEmpty())
            assertSame(result.pager, result.effective, "a refused side must score the raw pager read")
        }

        @Test
        fun `a star disagreement refuses the whole side`() {
            assertPagerOnly(
                reconcile(
                    capture(reading(stat("Speed", 3), apt("Sprint", 2))),
                    reading(stat("peed", 2), apt("Sprint", 2)),
                ),
                SparkReconcileRefusal.STAR_MISMATCH,
            )
        }

        @Test
        fun `a kind disagreement refuses the whole side`() {
            assertPagerOnly(
                reconcile(
                    capture(reading(stat("Speed", 2), apt("Sprint", 2))),
                    reading(stat("peed", 2), unique("Sprint", 2)),
                ),
                SparkReconcileRefusal.KIND_MISMATCH,
            )
        }

        @Test
        fun `a row-count disagreement refuses the whole side`() {
            assertPagerOnly(
                reconcile(
                    capture(reading(stat("Speed", 2), apt("Sprint", 2), unique("X", 1))),
                    reading(stat("peed", 2), apt("Sprint", 2)),
                ),
                SparkReconcileRefusal.ROW_COUNT_MISMATCH,
            )
        }

        @Test
        fun `a row-order disagreement refuses the whole side`() {
            // Same rows, swapped positions: the kinds no longer line up by index, so no positional
            // guess is ever attempted.
            assertPagerOnly(
                reconcile(
                    capture(reading(stat("Speed", 2), apt("Sprint", 2))),
                    reading(apt("Sprint", 2), stat("peed", 2)),
                ),
                SparkReconcileRefusal.KIND_MISMATCH,
            )
        }

        @Test
        fun `two different valid stat names are a contradiction, not a repair`() {
            val result =
                reconcile(
                    capture(reading(stat("Speed", 2), apt("Sprint", 2))),
                    reading(stat("Power", 2), apt("Sprint", 2)),
                )

            assertPagerOnly(result, SparkReconcileRefusal.VALID_NAME_CONTRADICTION)
            // The structure DID line up; it is the semantics that broke the same-set premise.
            assertTrue(result.structurallyAgreed)
        }

        @Test
        fun `two different valid aptitude names are a contradiction, not a repair`() {
            assertPagerOnly(
                reconcile(
                    capture(reading(stat("Speed", 2), apt("Sprint", 2))),
                    reading(stat("Speed", 2), apt("Mile", 2)),
                ),
                SparkReconcileRefusal.VALID_NAME_CONTRADICTION,
            )
        }

        @Test
        fun `a contradiction anywhere refuses the side even when an earlier row was repairable`() {
            val result =
                reconcile(
                    capture(reading(stat("Speed", 2), apt("Sprint", 2))),
                    reading(stat("peed", 2), apt("Mile", 2)),
                )

            assertPagerOnly(result, SparkReconcileRefusal.VALID_NAME_CONTRADICTION)
            assertEquals("peed", result.effective.rows[0].name, "no partial repair may leak out of a refused side")
        }

        @Test
        fun `two unresolvable names are left alone rather than guessed at`() {
            val result =
                reconcile(
                    capture(reading(stat("Zzz", 2), apt("Sprint", 2))),
                    reading(stat("Qqq", 2), apt("Sprint", 2)),
                )

            assertEquals(SparkReadAuthority.PAGER_UNCHANGED, result.authority)
            assertTrue(result.repairs.isEmpty())
            assertEquals("Qqq", result.effective.rows[0].name)
        }

        @Test
        fun `a capture from a different transaction is refused`() {
            assertPagerOnly(
                reconcile(
                    capture(reading(stat("Speed", 2)), transactionId = "other999"),
                    reading(stat("peed", 2)),
                ),
                SparkReconcileRefusal.DIFFERENT_TRANSACTION,
            )
        }

        @Test
        fun `a capture of the other side is refused`() {
            assertPagerOnly(
                reconcile(
                    capture(reading(stat("Speed", 2)), side = SparkSetSide.ORIGINAL),
                    reading(stat("peed", 2)),
                    side = SparkSetSide.REROLLED,
                ),
                SparkReconcileRefusal.DIFFERENT_SIDE,
            )
        }

        @Test
        fun `an incomplete earlier capture cannot repair anything`() {
            assertPagerOnly(
                reconcile(
                    capture(reading(stat("Speed", 2), termination = SparkScanTermination.TIMED_OUT_PARTIAL)),
                    reading(stat("peed", 2)),
                ),
                SparkReconcileRefusal.EARLIER_INCOMPLETE,
            )
        }

        @Test
        fun `an incomplete pager read is left to the degraded-Original path`() {
            assertPagerOnly(
                reconcile(
                    capture(reading(stat("Speed", 2))),
                    reading(stat("peed", 2), termination = SparkScanTermination.ALIGNMENT_FAILED),
                ),
                SparkReconcileRefusal.PAGER_INCOMPLETE,
            )
        }

        @Test
        fun `with no earlier capture at all the pager read stands alone`() {
            assertPagerOnly(reconcile(null, reading(stat("peed", 2))), SparkReconcileRefusal.NO_EARLIER_CAPTURE)
        }
    }

    @Nested
    @DisplayName("career 9 (Haru Urara, 2026-08-06 02:21) end to end")
    inner class CareerNine {
        // The exact sets from the preserved corpus records. Original: 6 rows / 9 stars.
        // Rerolled: 9 rows / 17 stars, holding both configured targets.
        private val originalPager =
            reading(
                stat("Power", 1),
                apt("Dirt", 2),
                unique("Super-Duper Climax", 1),
                white("-ay Low", 2, SparkWhiteClass.SKILL),
                white("gnited Spirit PWR", 1, SparkWhiteClass.SKILL),
                white("Jnity Cup", 2, SparkWhiteClass.RACE),
            )
        private val originalEarlier =
            reading(
                stat("Power", 1),
                apt("Dirt", 2),
                unique("Super-Duper Climax", 1),
                white("Lay Low", 2, SparkWhiteClass.SKILL),
                white("Ignited Spirit PWR", 1, SparkWhiteClass.SKILL),
                white("Unity Cup", 2, SparkWhiteClass.RACE),
            )

        /** The result-screen read, clean, exactly as the corpus holds it. */
        private val rerolledEarlier =
            reading(
                stat("Speed", 2),
                apt("Sprint", 2),
                unique("Super-Duper Climax", 1),
                white("JBC Sprint", 1, SparkWhiteClass.RACE),
                white("Outer Post Proficiency O", 3, SparkWhiteClass.SKILL),
                white("Lay Low", 2, SparkWhiteClass.SKILL),
                white("Ramp Up", 2, SparkWhiteClass.SKILL),
                white("Pace Strategy", 2, SparkWhiteClass.SKILL),
                white("Unity Cup", 2, SparkWhiteClass.RACE),
                termination = SparkScanTermination.COMPLETE_NO_PROGRESS,
            )

        /** The same set as the pager read it: identical kinds and stars, leading glyphs lost. */
        private val rerolledPager =
            reading(
                stat("peed", 2),
                apt("print", 2),
                unique("Super-Duper Climax", 1),
                white("JBC Sprint", 1, SparkWhiteClass.RACE),
                white("Outer Post Proficiency O", 3, SparkWhiteClass.SKILL),
                white("-ay Low", 2, SparkWhiteClass.SKILL),
                white("Ramp Up", 2, SparkWhiteClass.SKILL),
                white("Pace Strategy", 2, SparkWhiteClass.SKILL),
                white("Jnity Cup", 2, SparkWhiteClass.RACE),
                termination = SparkScanTermination.COMPLETE_NO_PROGRESS,
            )

        private val profile =
            SparkChooserProfile(
                traineeIdentity = "Haru Urara",
                objective = "rank",
                blueTargetsOrdered = listOf("Speed", "Power"),
                preferredDistance = "Sprint",
                preferredStyle = "Late Surger",
                preferredSurface = null,
                plannedSkillNames = emptyList(),
            )

        @Test
        fun `the shipped behavior kept the worse original -- the damaged blue scored as a non-target`() {
            val choice = SparkKeepPolicy.choose(originalPager, rerolledPager, profile)

            assertEquals(SparkSetSide.ORIGINAL, choice.side)
            assertEquals("blue", choice.decidedBy)
            assertEquals(-1, choice.rerolled.blueTargetRank, "the lost glyph is what demoted Speed")
            assertEquals(0, choice.rerolled.targetBlueStars)
            assertEquals(9, choice.original.totalStars)
            assertEquals(17, choice.rerolled.totalStars)
        }

        @Test
        fun `reconciled reads choose the rerolled set`() {
            val original = SparkReadReconcile.reconcile(SparkSetSide.ORIGINAL, tx, capture(originalEarlier, SparkSetSide.ORIGINAL), originalPager)
            val rerolled = SparkReadReconcile.reconcile(SparkSetSide.REROLLED, tx, capture(rerolledEarlier), rerolledPager)

            assertEquals("Speed", rerolled.effective.rows[0].name)
            assertEquals("Sprint", rerolled.effective.rows[1].name)
            assertEquals(listOf(0, 1), rerolled.repairedRowIndexes)
            // The original page needed no repair: its blue and pink read cleanly even on the pager.
            assertEquals(SparkReadAuthority.PAGER_UNCHANGED, original.authority)

            val choice = SparkKeepPolicy.choose(original.effective, rerolled.effective, profile)
            assertEquals(SparkSetSide.REROLLED, choice.side)
            assertTrue(choice.certain)
            assertEquals("blue", choice.decidedBy)
            assertEquals(0, choice.rerolled.blueTargetRank, "Speed is the first configured target")
            assertEquals(2, choice.rerolled.targetBlueStars)
        }

        @Test
        fun `the repair is what flips it -- the pink repair alone does not decide the tier`() {
            // Sprint matters for the pink tier, but the blue tier is compared first and already
            // separates the sides. Pinning this keeps the outcome attributable.
            val rerolled = SparkReadReconcile.reconcile(SparkSetSide.REROLLED, tx, capture(rerolledEarlier), rerolledPager)
            val choice = SparkKeepPolicy.choose(originalPager, rerolled.effective, profile)

            assertEquals(SparkSetSide.REROLLED, choice.side)
            assertEquals("blue", choice.decidedBy)
        }

        @Test
        fun `without structural corroboration no repair is fabricated and the original still wins`() {
            val rerolled = SparkReadReconcile.reconcile(SparkSetSide.REROLLED, tx, null, rerolledPager)

            assertEquals(SparkReadAuthority.PAGER_ONLY, rerolled.authority)
            assertEquals("peed", rerolled.effective.rows[0].name)
            val choice = SparkKeepPolicy.choose(originalPager, rerolled.effective, profile)
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
        }

        @Test
        fun `an incomplete pager read still takes the degraded keep-original path, not a repair`() {
            val short = rerolledPager.copy(termination = SparkScanTermination.ALIGNMENT_FAILED)
            val rerolled = SparkReadReconcile.reconcile(SparkSetSide.REROLLED, tx, capture(rerolledEarlier), short)

            assertEquals(SparkReadAuthority.PAGER_ONLY, rerolled.authority)
            assertEquals(SparkReconcileRefusal.PAGER_INCOMPLETE, rerolled.refusal)

            val choice = SparkKeepPolicy.choose(originalPager, rerolled.effective, profile)
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
            assertFalse(choice.certain, "an incomplete side must stay the uncertain keep-original fallback")
            assertEquals("incomplete_read", choice.decidedBy)
        }

        @Test
        fun `a repaired but uncertain evaluation can still never commit the rerolled page`() {
            // Content repair must not be able to reach a Rerolled commit through the degraded path:
            // the selection policy only ever commits ORIGINAL when the comparison is not certain.
            val short = rerolledPager.copy(termination = SparkScanTermination.TIMED_OUT_PARTIAL)
            val rerolled = SparkReadReconcile.reconcile(SparkSetSide.REROLLED, tx, capture(rerolledEarlier), short)
            val choice = SparkKeepPolicy.choose(originalPager, rerolled.effective, profile)

            val decision =
                SparkSelectionPolicy.decide(
                    choice,
                    SparkSelectionInputs(
                        originalTermination = originalPager.termination,
                        rerolledTermination = short.termination,
                        currentPageSide = SparkSetSide.ORIGINAL,
                        originalPageVerifiedByNavigation = true,
                        originalControlAvailable = true,
                        currentPageRescanAvailable = false,
                    ),
                )

            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, decision.action)
            assertFalse(decision.certain)
        }
    }

    @Nested
    @DisplayName("telemetry and vocabulary")
    inner class Telemetry {
        @Test
        fun `both raw reads and the effective read are all retained side by side`() {
            val earlier = reading(stat("Speed", 2), apt("Sprint", 2))
            val pager = reading(stat("peed", 2), apt("Sprint", 2))
            val result = reconcile(capture(earlier), pager)

            assertEquals("Speed", result.earlier!!.rows[0].name)
            assertEquals("peed", result.pager.rows[0].name, "the raw pager read must never be overwritten")
            assertEquals("Speed", result.effective.rows[0].name)
            assertTrue(result.summarize().contains("peed"))
            assertTrue(result.summarize().contains("Speed"))
            assertTrue(result.summarize().contains(SparkReadAuthority.PAGER_WITH_STRUCTURAL_NAME_REPAIR.name))
        }

        @Test
        fun `a refusal summary names the reason`() {
            val result = reconcile(null, reading(stat("peed", 2)))
            assertTrue(result.summarize().contains(SparkReconcileRefusal.NO_EARLIER_CAPTURE.wire))
        }

        @Test
        fun `the closed vocabularies cover exactly the five stats and ten aptitudes`() {
            assertEquals(5, SPARK_STAT_NAMES.size)
            assertEquals(10, SPARK_APTITUDE_NAMES.size)
            assertEquals("Speed", SparkReadReconcile.canonicalNameFor(SparkRowKind.STAT, "Speed"))
            // Only exact (fold-tolerant) resolution counts: a damaged name resolves to nothing.
            assertEquals(null, SparkReadReconcile.canonicalNameFor(SparkRowKind.STAT, "peed"))
            assertEquals(null, SparkReadReconcile.canonicalNameFor(SparkRowKind.APTITUDE, "print"))
            assertEquals("Late Surger", SparkReadReconcile.canonicalNameFor(SparkRowKind.APTITUDE, "late surger"))
            // Kinds without a closed vocabulary never resolve, so they can never be repaired.
            assertEquals(null, SparkReadReconcile.canonicalNameFor(SparkRowKind.WHITE, "Unity Cup"))
            assertEquals(null, SparkReadReconcile.canonicalNameFor(SparkRowKind.UNIQUE, "Speed"))
        }
    }

    @Nested
    @DisplayName("source guards: where the reconciliation may and may not act")
    inner class SourceGuards {
        private fun navigator(): String = sourceFile("CareerLaunchNavigator.kt").readText()

        private fun pagerHandlerBody(): String {
            val text = navigator()
            val start = text.indexOf("private fun handleSparkSelectionPager(")
            assertTrue(start >= 0, "the pager handler must exist")
            val end = text.indexOf("private fun", start + 10)
            return text.substring(start, if (end > start) end else text.length)
        }

        @Test
        fun `the comparison consumes the reconciled reads, and reconciliation precedes it`() {
            val body = pagerHandlerBody()
            val reconcileAt = body.indexOf("SparkReadReconcile.reconcile(")
            val chooseAt = body.indexOf("SparkKeepPolicy.choose(")
            assertTrue(reconcileAt >= 0, "the pager handler must reconcile the reads")
            assertTrue(chooseAt > reconcileAt, "reconciliation must run before the comparison")
            assertTrue(
                "SparkKeepPolicy.choose(originalAuthority.effective, rerolledAuthority.effective" in body,
                "the comparison must score the reconciled reads, not the raw pager reads",
            )
        }

        @Test
        fun `the raw pager read is persisted before any reconciliation can touch it`() {
            val body = pagerHandlerBody()
            val recordAt = body.indexOf("recordSparkRows(reading.rows, \"pager_")
            val reconcileAt = body.indexOf("SparkReadReconcile.reconcile(")
            assertTrue(recordAt >= 0, "each pager page read must be recorded raw")
            assertTrue(recordAt < reconcileAt, "the raw read must reach the corpus before the repair exists")
        }

        @Test
        fun `reconciliation never touches side identity, navigation or the Confirm target`() {
            val body = pagerHandlerBody()
            // The winner and the page the Confirm lands on are still resolved from the pager alone.
            assertTrue("val winner = transaction.winner!!" in body)
            assertTrue("if (side != winner)" in body)
            // No authority value may be consulted once the winner is fixed: everything from the
            // winner-page gate to the handler's last statement must be pager-only. The slice stops
            // at that statement so the next function's doc comment cannot leak into it.
            val winnerGate = body.indexOf("if (side != winner)")
            val handlerEnd = body.lastIndexOf("return TransitionResult.Continue")
            assertTrue(handlerEnd > winnerGate, "the handler must end after the winner-page gate")
            val tail = body.substring(winnerGate, handlerEnd)
            assertFalse("Authority" in tail, "no read-authority value may influence navigation or the Confirm press")
            assertFalse("SparkReadReconcile" in tail, "reconciliation must not run after the winner is fixed")
            assertFalse("effective" in tail, "the Confirm path must not consult a reconciled read")
        }

        @Test
        fun `the choice record persists both raw reads and the repaired one, never replacing the pager read`() {
            val text = navigator()
            val start = text.indexOf("private fun sparkReadAuthorityJson(")
            assertTrue(start >= 0, "the authority record writer must exist")
            val writer = text.substring(start, text.indexOf("private fun", start + 10))
            assertTrue("\"pager_rows\"" in writer, "the raw pager read must be persisted verbatim")
            assertTrue("\"earlier_rows\"" in writer, "the earlier capture must be persisted verbatim")
            assertTrue("\"effective_rows\"" in writer, "the read actually scored must be persisted")
            assertTrue("\"repairs\"" in writer && "\"pager_name\"" in writer && "\"repaired_name\"" in writer)
            assertTrue("\"authority\"" in writer && "\"refusal\"" in writer && "\"structurally_agreed\"" in writer)
            // The whole block is additive on spark_choice, so existing readers are unaffected.
            assertTrue("record.put(\n                    \"read_authority\"," in text || "\"read_authority\"" in text)
        }

        @Test
        fun `reconciliation is single-pass -- it introduces no rescan, retry or loop`() {
            val chooser = sourceFile("bot/SparkChooser.kt").readText()
            val start = chooser.indexOf("object SparkReadReconcile {")
            assertTrue(start >= 0)
            val body = chooser.substring(start)
            assertFalse("while (" in body, "no loop other than the single indexed pass may exist")
            assertFalse("usePagerRescan" in body, "reconciliation must never consume a rescan budget")
            assertFalse("readCompleteSparkSet" in body, "reconciliation must never read the screen")
        }
    }

    // Source-tree access, same walk as the other lifecycle guards.
    private fun sourceFile(relative: String): File = File(sourceRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

    private fun sourceRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(5) {
            val candidate = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (candidate.isDirectory) return candidate
            val fromRepoRoot = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (fromRepoRoot.isDirectory) return fromRepoRoot
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the source root from ${System.getProperty("user.dir")}")
    }

    @Test
    fun `the reconcile entry point is reachable and total for an empty pager read`() {
        val result = reconcile(capture(reading()), reading())
        assertNotNull(result)
        // An empty reading is never complete, so it can only ever fall through to the pager.
        assertEquals(SparkReadAuthority.PAGER_ONLY, result.authority)
    }
}
