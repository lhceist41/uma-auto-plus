package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

@DisplayName("Spark list end confirmation")
class SparkScrollEndTest {
    private fun white(name: String, stars: Int = 1) = SparkRowFact(name, stars, SparkRowKind.WHITE, SparkWhiteClass.SKILL)

    private val lead = listOf(SparkRowFact("Stamina", 2, SparkRowKind.STAT), SparkRowFact("Pace Chaser", 2, SparkRowKind.APTITUDE), SparkRowFact("Unique", 1, SparkRowKind.UNIQUE))
    private val list13 = lead + (1..10).map { white("Skill $it", (it % 3) + 1) }
    private val top11 = list13.subList(0, 11)
    private val bottom11 = list13.subList(2, 13)

    /** One swipe's evidence: the first capture and, when it neither matches nor stitches, the settled same-position re-read. */
    private class Frame(val rows: List<SparkRowFact>, val endMarker: Boolean = false, val reread: List<SparkRowFact>? = null)

    private class Replay(val rows: List<SparkRowFact>, val termination: SparkScanTermination?, val swipes: Int)

    /** The reader's per-frame decisions with the device calls removed. */
    private fun replay(frames: List<Frame>): Replay {
        var merged = listOf<SparkRowFact>()
        var previous: List<SparkRowFact>? = null
        var run = 0
        var swipes = 0
        for (frame in frames) {
            val runBeforeSwipe = run
            run = SparkScrollMerge.unchangedSwipeRun(previous, frame.rows, run)
            if (run > 0) {
                if (SparkScrollMerge.listEnded(run)) return Replay(merged, SparkScanTermination.COMPLETE_NO_PROGRESS, swipes)
                swipes++
                continue
            }
            var used = frame.rows
            var next = SparkScrollMerge.merge(merged, used)
            if (next == null) {
                val reread = frame.reread ?: return Replay(merged, SparkScanTermination.ALIGNMENT_FAILED, swipes)
                run = SparkScrollMerge.unchangedSwipeRun(previous, reread, runBeforeSwipe)
                if (run > 0) {
                    if (SparkScrollMerge.listEnded(run)) return Replay(merged, SparkScanTermination.COMPLETE_NO_PROGRESS, swipes)
                    swipes++
                    continue
                }
                used = reread
                next = SparkScrollMerge.merge(merged, used) ?: return Replay(merged, SparkScanTermination.ALIGNMENT_FAILED, swipes)
            }
            merged = next
            if (frame.endMarker) return Replay(merged, SparkScanTermination.COMPLETE_END_MARKER, swipes)
            previous = used
            swipes++
        }
        return Replay(merged, null, swipes)
    }

    /** [rows] with one name misread, as a capture taken before the list settles can read it. */
    private fun misread(
        rows: List<SparkRowFact>,
        index: Int = rows.lastIndex,
        suffix: String = "P",
    ) = rows.mapIndexed { i, row -> if (i == index) row.copy(name = row.name + suffix) else row }

    @Nested
    @DisplayName("a dropped swipe")
    inner class DroppedSwipe {
        @Test
        fun `a swipe that moved nothing is retried and the scroll continues`() {
            val read = replay(listOf(Frame(top11), Frame(top11), Frame(bottom11, endMarker = true)))
            assertEquals(13, read.rows.size)
            assertEquals(list13.map { it.name }, read.rows.map { it.name })
            assertTrue(read.termination!!.complete)
            assertEquals(2, read.swipes)
        }

        @Test
        fun `the bottom is still found by two unchanged swipes when no end marker shows`() {
            val read = replay(listOf(Frame(top11), Frame(top11), Frame(bottom11), Frame(bottom11), Frame(bottom11)))
            assertEquals(13, read.rows.size)
            assertEquals(SparkScanTermination.COMPLETE_NO_PROGRESS, read.termination)
        }

        @Test
        fun `a moved frame between unchanged ones resets the run`() {
            val read = replay(listOf(Frame(top11), Frame(top11), Frame(bottom11), Frame(bottom11)))
            assertNull(read.termination, "one unchanged frame after the last move is not a confirmation")
        }
    }

    @Nested
    @DisplayName("a list that cannot move")
    inner class Stationary {
        @Test
        fun `two unchanged swipes end the read`() {
            val read = replay(listOf(Frame(top11), Frame(top11), Frame(top11)))
            assertEquals(SparkScanTermination.COMPLETE_NO_PROGRESS, read.termination)
            assertEquals(2, read.swipes)
        }

        @Test
        fun `an exact window stays complete with all its rows`() {
            val read = replay(listOf(Frame(top11), Frame(top11), Frame(top11)))
            assertTrue(read.termination!!.complete)
            assertEquals(11, read.rows.size)
        }

        @Test
        fun `one unchanged swipe alone never ends the read`() {
            assertFalse(SparkScrollMerge.listEnded(1))
            assertTrue(SparkScrollMerge.listEnded(SparkScrollMerge.UNCHANGED_SWIPES_TO_END))
            assertNull(replay(listOf(Frame(top11), Frame(top11))).termination)
        }
    }

    @Nested
    @DisplayName("a misread first capture after each swipe")
    inner class TransientCapture {
        private val keep11 =
            lead +
                listOf(
                    white("Japanese Derby"),
                    white("Sprinters S.", 2),
                    white("Hopeful S."),
                    white("Medium Corners O"),
                    white("Late Surger Corners O", 2),
                    white("Groundwork"),
                    white("Ignited Spirit: Speed +", 2),
                    white("Unity Cup"),
                )
        private val transient = Frame(misread(keep11), reread = keep11)

        @Test
        fun `an exact window whose re-reads match ends complete after two swipes`() {
            val read = replay(listOf(Frame(keep11)) + List(6) { transient })
            assertEquals(SparkScanTermination.COMPLETE_NO_PROGRESS, read.termination)
            assertEquals(keep11, read.rows)
            assertEquals(2, read.swipes)
        }

        @Test
        fun `one matching re-read alone never ends the read`() {
            assertNull(replay(listOf(Frame(keep11), transient)).termination)
        }

        @Test
        fun `a list that moved is stitched from the re-read and not ended by it`() {
            val moved = Frame(misread(bottom11, index = 0), reread = bottom11)
            val read = replay(listOf(Frame(top11), moved, moved))
            assertNull(read.termination, "one unchanged swipe at the real bottom is not a confirmation")
            assertEquals(list13, read.rows)
            val ended = replay(listOf(Frame(top11), moved, moved, moved))
            assertEquals(SparkScanTermination.COMPLETE_NO_PROGRESS, ended.termination)
            assertEquals(list13, ended.rows)
        }

        @Test
        fun `a re-read that neither matches nor stitches still fails the read`() {
            val read = replay(listOf(Frame(keep11), Frame(misread(keep11), reread = misread(keep11, suffix = "Q"))))
            assertEquals(SparkScanTermination.ALIGNMENT_FAILED, read.termination)
        }

        @Test
        fun `a re-read showing a move resets the run built before it`() {
            val read = replay(listOf(Frame(top11), Frame(top11), Frame(misread(bottom11, index = 0), reread = bottom11), Frame(bottom11)))
            assertNull(read.termination)
            assertEquals(list13, read.rows)
        }
    }

    @Nested
    @DisplayName("unchanged swipe run")
    inner class Run {
        @Test
        fun `no earlier frame, a moved frame and a different size all start from zero`() {
            assertEquals(0, SparkScrollMerge.unchangedSwipeRun(null, top11, 0))
            assertEquals(0, SparkScrollMerge.unchangedSwipeRun(top11, bottom11, 1))
            assertEquals(0, SparkScrollMerge.unchangedSwipeRun(top11, top11.dropLast(1), 1))
        }

        @Test
        fun `a matching frame extends the run`() {
            assertEquals(1, SparkScrollMerge.unchangedSwipeRun(top11, top11, 0))
            assertEquals(2, SparkScrollMerge.unchangedSwipeRun(top11, top11, 1))
        }
    }

    @Nested
    @DisplayName("a row read differently at another scroll position")
    inner class PositionVariants {
        private fun stat(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.STAT)

        private fun apt(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.APTITUDE)

        private fun unique(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.UNIQUE)

        private fun renamed(rows: List<SparkRowFact>, from: String, to: String) = rows.map { if (it.name == from) it.copy(name = to) else it }

        // Live pager Original at 1080: the second frame read the same Left-Handed row without its dash.
        private val pagerOriginal =
            listOf(
                stat("Stamina", 1),
                apt("Pace Chaser", 1),
                unique("Blazing Pride", 2),
                white("Shuka Sho", 1),
                white("-eft-Handed O", 1),
                white("Firm Conditions O", 1),
                white("Corner Adept O", 2),
                white("Corner Recovery O", 1),
                white("Pace Chaser\nStraightaways O", 2),
            )
        private val lowerFrame = pagerOriginal.drop(1)
        private val lowerFrameVariant = renamed(lowerFrame, "-eft-Handed O", "eft-Handed O")

        @Test
        fun `a dash lost on one row still stitches the frame onto the rows already read`() {
            assertEquals(pagerOriginal, SparkScrollMerge.merge(pagerOriginal, lowerFrameVariant))
        }

        @Test
        fun `the same frame with that row read differently counts as an unchanged swipe`() {
            assertEquals(1, SparkScrollMerge.unchangedSwipeRun(lowerFrame, lowerFrameVariant, 0))
        }

        @Test
        fun `the 1440 Savvy rows stitch into the ten rows the keep dialog showed`() {
            val powerSetFirst =
                listOf(
                    stat("Power", 2),
                    apt("Front Runner", 2),
                    unique("Flashy*Landin", 2),
                    white("Tenno Sho (Spring)", 2),
                    white("Kikuka Sho", 1),
                    white("Arima Kinen", 2),
                    white("Straightaway Recovery", 2),
                    white("Pace Chaser SavvyO", 2),
                    white("On the Attack", 1),
                )
            val powerSetSecond = renamed(powerSetFirst.drop(1), "Pace Chaser SavvyO", "Pace Chaser Savvy O") + white("Ignited Spirit STA", 2)
            val powerSetDialog = powerSetFirst + white("Ignited Spirit STA", 2)
            val powerSet = SparkScrollMerge.merge(powerSetFirst, powerSetSecond)
            assertEquals(10, powerSet?.size)
            assertEquals(powerSetDialog.map { it.kind to it.stars }, powerSet!!.map { it.kind to it.stars })

            val staminaSetFirst =
                listOf(
                    stat("Stamina", 1),
                    apt("Turf", 2),
                    unique("Flashy*Landin", 2),
                    white("Tenno Sho (Spring)", 1),
                    white("Arima Kinen", 2),
                    white("Corner Recovery O", 1),
                    white("Straightaway Recovery", 2),
                    white("Pace Chaser SavvyO", 1),
                    white("Lucky Seven", 1),
                )
            val staminaSetSecond = renamed(staminaSetFirst.drop(1), "Pace Chaser SavvyO", "Pace Chaser Savvy O") + white("Head-On", 1)
            val staminaSetDialog = staminaSetFirst + white("Head-On", 1)
            val staminaSet = SparkScrollMerge.merge(staminaSetFirst, staminaSetSecond)
            assertEquals(10, staminaSet?.size)
            assertEquals(staminaSetDialog.map { it.kind to it.stars }, staminaSet!!.map { it.kind to it.stars })
        }

        @Test
        fun `the live sequence that ended ALIGNMENT_FAILED now ends complete with all nine rows`() {
            val read =
                replay(
                    listOf(
                        Frame(pagerOriginal),
                        Frame(pagerOriginal),
                        Frame(lowerFrame),
                        Frame(lowerFrameVariant, reread = lowerFrameVariant),
                        Frame(lowerFrame),
                    ),
                )
            assertEquals(SparkScanTermination.COMPLETE_NO_PROGRESS, read.termination)
            assertEquals(pagerOriginal, read.rows)
        }

        @Test
        fun `a folded name that makes a second overlap align still refuses the merge`() {
            val merged = listOf(white("Corner Adept O"), white("Corner Adept 0"))
            val next = listOf(white("Corner Adept 0"), white("Corner Adept O"))
            assertNull(SparkScrollMerge.merge(merged, next))
        }

        @Test
        fun `a name that lost a letter, not just a glyph shape, still refuses the stitch`() {
            val merged = listOf(stat("Stamina", 1), apt("Mile", 2), white("Mile Straightaways O"))
            val next = listOf(apt("Mile", 2), white("ile Straightaways O"), white("Groundwork"))
            assertNull(SparkScrollMerge.merge(merged, next))
            assertEquals(0, SparkScrollMerge.unchangedSwipeRun(merged.drop(1), next.take(2), 0))
        }

        @Test
        fun `a different star or kind on the variant row still refuses the stitch`() {
            assertNull(SparkScrollMerge.merge(pagerOriginal, lowerFrameVariant.mapIndexed { i, r -> if (i == 3) r.copy(stars = 2) else r }))
            assertNull(SparkScrollMerge.merge(pagerOriginal, lowerFrameVariant.mapIndexed { i, r -> if (i == 3) r.copy(kind = SparkRowKind.UNIQUE) else r }))
        }
    }

    @Nested
    @DisplayName("reader wiring")
    inner class Wiring {
        private fun readerBody(): String {
            var dir: File? = File(System.getProperty("user.dir"))
            repeat(5) {
                for (rel in listOf("src/main/java", "android/app/src/main/java")) {
                    val f = File(dir, "$rel/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt")
                    if (f.isFile) {
                        val text = f.readText().replace("\r\n", "\n")
                        val start = text.indexOf("private fun readCompleteSparkSet(")
                        assertTrue(start >= 0, "the reader must exist")
                        return text.substring(start, text.indexOf("\n    }\n", start))
                    }
                }
                dir = dir?.parentFile
            }
            throw AssertionError("CareerLaunchNavigator.kt not found")
        }

        @Test
        fun `every unchanged frame goes through the confirmation rule`() {
            val body = readerBody()
            assertEquals(2, Regex("""SparkScrollMerge\.unchangedSwipeRun\(""").findAll(body).count(), "first read and same-position re-read")
            assertEquals(1, Regex("""SparkScrollMerge\.listEnded\(""").findAll(body).count())
            assertEquals(1, Regex("""SparkScanTermination\.COMPLETE_NO_PROGRESS""").findAll(body).count(), "the only no-progress ending sits behind listEnded")
        }

        @Test
        fun `the same-position re-read extends the run from before the swipe`() {
            val body = readerBody()
            val saved = body.indexOf("val runBeforeSwipe = unchangedRun")
            assertTrue(saved >= 0, "the run is saved before the first capture is scored")
            assertTrue(saved < body.indexOf("SparkScrollMerge.unchangedSwipeRun("), "saved before the first capture's call")
            assertTrue(body.contains("SparkScrollMerge.unchangedSwipeRun(previousRows, retryFrame.rows, runBeforeSwipe)"), "the re-read must not extend the run its transient capture reset")
        }
    }
}
