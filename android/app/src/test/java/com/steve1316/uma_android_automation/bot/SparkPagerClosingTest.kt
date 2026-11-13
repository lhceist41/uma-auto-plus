package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

@DisplayName("Spark pager after the final Confirm, and the original-set recheck before a spend")
class SparkPagerClosingTest {
    private fun row(kind: SparkRowKind, stars: Int, name: String = "n") = SparkRowFact(name, stars, kind)

    private fun rows(count: Int): List<SparkRowFact> =
        listOf(row(SparkRowKind.STAT, 2, "Stamina"), row(SparkRowKind.APTITUDE, 2, "Turf"), row(SparkRowKind.UNIQUE, 1, "u")) +
            (3 until count).map { row(SparkRowKind.WHITE, 1 + it % 3, "white $it") }

    private fun read(
        rowList: List<SparkRowFact>,
        termination: SparkScanTermination = SparkScanTermination.COMPLETE_END_MARKER,
    ) = SparkSetReading(rowList, termination)

    private val finalConfirmAt = 50_000L

    /** Walks a transaction along the happy path to COMPLETE and presses the final Confirm at [finalConfirmAt]. */
    private fun completed(): SparkRerollTransaction {
        SparkRerollGate.beginCareer("nonce1", queueRun = 1, nowMs = 1_000L)
        val tx = SparkRerollGate.transaction!!
        tx.captureOriginal(read(rows(3)), "trainee", "URA Finale")
        tx.approveSpend("test")
        tx.confirmSpend(nowMs = 2_000L)
        tx.captureRerolled(read(rows(3)))
        tx.introPassed()
        tx.recordPagerRead(SparkSetSide.ORIGINAL, read(rows(3)))
        tx.recordPagerRead(SparkSetSide.REROLLED, read(rows(3)))
        tx.setsVerified()
        val breakdown = SparkKeepPolicy.breakdown(read(rows(3)), SparkChooserProfile(null, null, emptyList(), null, null, null, emptyList()), true)
        tx.selectWinner(SparkChoice(SparkSetSide.ORIGINAL, "tie", "test", true, breakdown, breakdown))
        tx.verifyFinalConfirmation()
        tx.markKeptRecorded()
        tx.markChoiceRecorded()
        assertTrue(tx.complete().ok)
        tx.markFinalConfirmPressed(finalConfirmAt)
        return tx
    }

    @BeforeEach
    @AfterEach
    fun isolate() {
        SparkRerollGate.reset()
    }

    @Nested
    @DisplayName("closing window")
    inner class ClosingWindow {
        @Test
        fun `a completed transaction is closing from its final Confirm to the end of the window`() {
            val tx = completed()
            for (elapsed in listOf(0L, 2_600L, 4_740L, 10_000L, SPARK_PAGER_CLOSING_WINDOW_MS)) {
                assertTrue(sparkPagerClosing(tx, finalConfirmAt + elapsed), "$elapsed ms after the final Confirm")
            }
        }

        @Test
        fun `the window is shorter than seven stuck-screen looks`() {
            // Six looks at the 3 s handler wait plus the 2.5 s after the Confirm fit in the window; a seventh look
            // must already be past it, so the stuck-screen rebind (seventh repeat) never fires while waiting.
            assertTrue(SPARK_PAGER_CLOSING_WINDOW_MS <= 2_500L + 6 * 3_000L)
        }

        @Test
        fun `after the window, or with a clock running backwards, the pager is not closing`() {
            val tx = completed()
            assertFalse(sparkPagerClosing(tx, finalConfirmAt + SPARK_PAGER_CLOSING_WINDOW_MS + 1))
            assertFalse(sparkPagerClosing(tx, finalConfirmAt - 1))
        }

        @Test
        fun `a missing transaction is not closing`() {
            assertFalse(sparkPagerClosing(null, finalConfirmAt))
        }

        @Test
        fun `pre-spend, drivable, declined and blocked transactions are not closing`() {
            SparkRerollGate.beginCareer("n", queueRun = 1, nowMs = 1_000L)
            val idle = SparkRerollGate.transaction!!
            assertFalse(sparkPagerClosing(idle, 2_000L), "IDLE")
            idle.captureOriginal(read(rows(3)), "t", "s")
            assertFalse(sparkPagerClosing(idle, 2_000L), "ORIGINAL_CAPTURED")
            idle.declineSpend("negative")
            assertFalse(sparkPagerClosing(idle, 2_000L), "DECLINED")

            SparkRerollGate.beginCareer("n2", queueRun = 1, nowMs = 1_000L)
            val drivable = SparkRerollGate.transaction!!
            drivable.captureOriginal(read(rows(3)), "t", "s")
            drivable.approveSpend("positive")
            assertFalse(sparkPagerClosing(drivable, 2_000L), "SPEND_APPROVED")
            drivable.confirmSpend(nowMs = 2_000L)
            assertTrue(sparkSelectionDrivable(drivable, 3_000L))
            drivable.markFinalConfirmPressed(2_500L)
            assertFalse(sparkPagerClosing(drivable, 3_000L), "a drivable transaction is read, not waited out")
            drivable.block("contradiction")
            assertFalse(sparkPagerClosing(drivable, 3_000L), "BLOCKED")
        }

        @Test
        fun `a completed transaction without a pressed final Confirm is not closing`() {
            val tx = completed()
            SparkRerollGate.beginCareer("other", queueRun = 2, nowMs = 60_000L)
            assertFalse(sparkPagerClosing(SparkRerollGate.transaction, finalConfirmAt), "a new career's transaction has no final Confirm")
            assertTrue(sparkPagerClosing(tx, finalConfirmAt))
        }
    }

    @Nested
    @DisplayName("two reads of the original set")
    inner class ReadsAgree {
        @Test
        fun `identical complete reads agree, also across both complete terminations`() {
            assertTrue(sparkReadsAgree(read(rows(4)), read(rows(4))))
            assertTrue(sparkReadsAgree(read(rows(9), SparkScanTermination.COMPLETE_NO_PROGRESS), read(rows(9), SparkScanTermination.COMPLETE_NO_PROGRESS)))
            assertTrue(sparkReadsAgree(read(rows(8)), read(rows(8), SparkScanTermination.COMPLETE_NO_PROGRESS)))
        }

        @Test
        fun `a dropped swipe pair (9 rows) against the full list (13 rows) disagrees`() {
            assertFalse(sparkReadsAgree(read(rows(9), SparkScanTermination.COMPLETE_NO_PROGRESS), read(rows(13), SparkScanTermination.COMPLETE_NO_PROGRESS)))
            assertFalse(sparkReadsAgree(read(rows(13), SparkScanTermination.COMPLETE_NO_PROGRESS), read(rows(9), SparkScanTermination.COMPLETE_NO_PROGRESS)))
        }

        @Test
        fun `an incomplete read on either side disagrees even with the same rows`() {
            for (partial in listOf(SparkScanTermination.TIMED_OUT_PARTIAL, SparkScanTermination.ALIGNMENT_FAILED, SparkScanTermination.FAILED)) {
                assertFalse(sparkReadsAgree(read(rows(5)), read(rows(5), partial)), partial.name)
                assertFalse(sparkReadsAgree(read(rows(5), partial), read(rows(5))), partial.name)
            }
            assertFalse(sparkReadsAgree(read(emptyList()), read(emptyList())), "an empty read is never complete")
        }

        @Test
        fun `a star or kind difference in any row disagrees`() {
            val base = rows(6)
            val starOff = base.toMutableList().also { it[5] = it[5].copy(stars = it[5].stars % 3 + 1) }
            val kindOff = base.toMutableList().also { it[4] = it[4].copy(kind = SparkRowKind.UNIQUE) }
            val blueOff = base.toMutableList().also { it[0] = it[0].copy(stars = 3) }
            assertFalse(sparkReadsAgree(read(base), read(starOff)))
            assertFalse(sparkReadsAgree(read(base), read(kindOff)))
            assertFalse(sparkReadsAgree(read(base), read(blueOff)))
        }

        @Test
        fun `name-only differences agree`() {
            val base = rows(6)
            val renamed = base.map { it.copy(name = it.name + " ocr") }.toMutableList().also { it[2] = it[2].copy(name = SPARK_UNREADABLE_NAME) }
            assertTrue(sparkReadsAgree(read(base), read(renamed)))
        }
    }

    @Nested
    @DisplayName("source guards")
    inner class SourceGuards {
        private fun body(navigator: String, start: String): String {
            val at = navigator.indexOf(start)
            assertTrue(at >= 0, "missing $start")
            val end = navigator.indexOf("    private fun ", at + start.length)
            return navigator.substring(at, if (end > at) end else navigator.length)
        }

        @Test
        fun `the pager handler waits out a closing pager before any block, with no tap`() {
            val handler = body(navigator(), "private fun handleSparkSelectionPager(")
            val closing = handler.indexOf("sparkPagerClosing(")
            val firstBlock = handler.indexOf("sparkSelectionBlocked(")
            assertTrue(closing in 0 until firstBlock, "the closing branch must come before the first block")
            val branchEnd = handler.indexOf("return TransitionResult.Continue", closing)
            assertTrue(branchEnd in closing until firstBlock, "the closing branch returns Continue before any block")
            val branch = handler.substring(closing, branchEnd).lowercase()
            for (token in listOf("click", "tap(", "swipe", "gestureutils")) {
                assertFalse(token in branch, "the closing branch must not touch the screen ($token)")
            }
        }

        @Test
        fun `the final Confirm stamps its time on the transaction`() {
            val handler = body(navigator(), "private fun handleSparkSelectionConfirmation(")
            val click = handler.indexOf("ButtonConfirm.click")
            val stamp = handler.indexOf("markFinalConfirmPressed(")
            assertTrue(click >= 0 && stamp > click, "the time is stamped after the final Confirm is pressed")
        }

        @Test
        fun `the original set is read a second time between the EV gate and the spend approval`() {
            val handler = body(navigator(), "private fun handleSparksScreen(")
            val decide = handler.indexOf("SparkRerollPolicy.decide(")
            val approve = handler.indexOf("approveSpend(")
            assertTrue(decide in 0 until approve)
            val between = handler.substring(decide, approve)
            assertTrue("readCompleteSparkSet(" in between, "a second read must sit between the verdict and the approval")
            assertTrue("sparkReadsAgree(" in between, "the two reads must be compared before the approval")
            assertTrue("declineSpend(" in between, "a disagreement declines the spend")
        }
    }

    private fun navigator(): String = File(sourceRoot(), "CareerLaunchNavigator.kt").readText()

    private fun sourceRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(5) {
            val candidate = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (candidate.isDirectory) return candidate
            val fromRepoRoot = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (fromRepoRoot.isDirectory) return fromRepoRoot
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
