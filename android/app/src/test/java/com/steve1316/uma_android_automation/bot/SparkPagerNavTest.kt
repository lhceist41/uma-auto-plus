package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The Spark Selection pager's swipe navigation: direction planning, safe-lane coordinates,
 * repaint classification, and the source-guarded actuation rules. Background: the 2026-07-20
 * supervised run proved that two correctly-aimed taps on the pager's right chevron (990, 228)
 * never changed the page, so the pager is now paged with a central drag, following the
 * Scenario Select carousel precedent.
 *
 * These tests prove the planning, coordinates, verification logic, retry count, and Confirm
 * gating deterministically. They deliberately cannot prove Android gesture DELIVERY -- whether
 * a dispatched swipe actually pages the live game is exactly what the next supervised career
 * must show.
 */
@DisplayName("Spark pager swipe navigation")
class SparkPagerNavTest {
    private val w = SparkPagerNav.REFERENCE_WIDTH
    private val h = SparkPagerNav.REFERENCE_HEIGHT

    private fun endpoints(plan: SparkPagerSwipePlan) = listOf(plan.startX to plan.startY, plan.endX to plan.endY)

    @Nested
    @DisplayName("direction planning")
    inner class Planning {
        @Test
        fun `rerolled to original swipes left, the carousel's forward drag`() {
            val plan = SparkPagerNav.plan(SparkSetSide.REROLLED, SparkSetSide.ORIGINAL, 1, w, h)
            assertEquals(SparkPagerAction.SWIPE_LEFT, plan.action)
            assertTrue(plan.startX > plan.endX, "the finger must travel right-to-left to reveal page 2")
            assertEquals(SparkSetSide.ORIGINAL, plan.expectedPage)
        }

        @Test
        fun `original to rerolled swipes right, the opposite direction`() {
            val plan = SparkPagerNav.plan(SparkSetSide.ORIGINAL, SparkSetSide.REROLLED, 1, w, h)
            assertEquals(SparkPagerAction.SWIPE_RIGHT, plan.action)
            assertTrue(plan.startX < plan.endX, "the finger must travel left-to-right to reveal page 1")
            assertEquals(SparkSetSide.REROLLED, plan.expectedPage)
        }

        @Test
        fun `current equals target plans no gesture at all`() {
            for (side in SparkSetSide.entries) {
                val plan = SparkPagerNav.plan(side, side, 1, w, h)
                assertEquals(SparkPagerAction.NONE, plan.action)
                assertEquals(0L, plan.durationMs)
            }
        }

        @Test
        fun `the retry uses a different lane and a longer, slower drag`() {
            val first = SparkPagerNav.plan(SparkSetSide.REROLLED, SparkSetSide.ORIGINAL, 1, w, h)
            val second = SparkPagerNav.plan(SparkSetSide.REROLLED, SparkSetSide.ORIGINAL, 2, w, h)
            assertNotEquals(first.startY, second.startY, "the retry must not release on the same pixel band")
            assertTrue(
                kotlin.math.abs(second.endX - second.startX) > kotlin.math.abs(first.endX - first.startX),
                "the retry drag must cover more distance",
            )
            assertTrue(second.durationMs > first.durationMs)
        }

        @Test
        fun `plans scale linearly with the capture size`() {
            val ref = SparkPagerNav.plan(SparkSetSide.REROLLED, SparkSetSide.ORIGINAL, 1, w, h)
            val doubled = SparkPagerNav.plan(SparkSetSide.REROLLED, SparkSetSide.ORIGINAL, 1, w * 2, h * 2)
            assertEquals(ref.startX * 2, doubled.startX)
            assertEquals(ref.startY * 2, doubled.startY)
            assertEquals(ref.endX * 2, doubled.endX)
            assertEquals(ref.endY * 2, doubled.endY)
        }
    }

    @Nested
    @DisplayName("safe lanes")
    inner class SafeLanes {
        private fun allSwipePlans(): List<SparkPagerSwipePlan> =
            buildList {
                for (attempt in 1..2) {
                    add(SparkPagerNav.plan(SparkSetSide.REROLLED, SparkSetSide.ORIGINAL, attempt, w, h))
                    add(SparkPagerNav.plan(SparkSetSide.ORIGINAL, SparkSetSide.REROLLED, attempt, w, h))
                }
            }

        @Test
        fun `no endpoint enters either edge-overlay strip`() {
            // The floating overlay bubble snaps to either edge and slides along it, so the
            // full-height strips are excluded, not just the bubble's last seen spot.
            for (plan in allSwipePlans()) {
                for ((x, _) in endpoints(plan)) {
                    assertTrue(x > SparkPagerNav.EDGE_OVERLAY_WIDTH, "endpoint x=$x is inside the left edge strip")
                    assertTrue(x < w - SparkPagerNav.EDGE_OVERLAY_WIDTH, "endpoint x=$x is inside the right edge strip")
                }
            }
        }

        @Test
        fun `no endpoint touches the list scrollbar lane`() {
            for (plan in allSwipePlans()) {
                for ((x, _) in endpoints(plan)) {
                    assertTrue(x < SparkPagerNav.SCROLLBAR_MIN_X, "endpoint x=$x rides the scrollbar")
                }
            }
        }

        @Test
        fun `no endpoint enters the Confirm zone`() {
            for (plan in allSwipePlans()) {
                for ((_, y) in endpoints(plan)) {
                    assertTrue(y < SparkPagerNav.CONFIRM_ZONE_MIN_Y, "endpoint y=$y is in the bottom Confirm band")
                }
            }
        }

        @Test
        fun `no endpoint enters the header zone with the chevrons and page dots`() {
            for (plan in allSwipePlans()) {
                for ((_, y) in endpoints(plan)) {
                    assertTrue(y > SparkPagerNav.HEADER_ZONE_MAX_Y, "endpoint y=$y is in the chevron/heading band")
                }
            }
        }

        @Test
        fun `swipes are flat -- no vertical component that could scroll the spark list`() {
            for (plan in allSwipePlans()) {
                assertTrue(
                    kotlin.math.abs(plan.endY - plan.startY) < SparkPagerNav.LIST_SCROLL_DY_LIMIT,
                    "vertical delta would scroll the list instead of paging",
                )
            }
        }

        @Test
        fun `the failed live tap point is itself inside the exclusion zones`() {
            // Negative control: the coordinate that failed live (right chevron, 990x228) is
            // excluded twice over -- edge strip and header band -- so no plan can recreate it.
            assertTrue(990 >= w - SparkPagerNav.EDGE_OVERLAY_WIDTH)
            assertTrue(228 <= SparkPagerNav.HEADER_ZONE_MAX_Y)
        }
    }

    @Nested
    @DisplayName("repaint classification")
    inner class Repaint {
        private val current = SparkSetSide.REROLLED
        private val target = SparkSetSide.ORIGINAL

        @Test
        fun `heading and dot both naming the target is the only success`() {
            assertEquals(
                SparkPagerRepaint.VERIFIED,
                classifySparkPagerRepaint(SparkSetSide.ORIGINAL, 2, current, target),
            )
        }

        @Test
        fun `heading and dot still naming the current page reads as unchanged`() {
            assertEquals(
                SparkPagerRepaint.UNCHANGED,
                classifySparkPagerRepaint(SparkSetSide.REROLLED, 1, current, target),
            )
        }

        @Test
        fun `an unreadable heading blocks even when the dot names the target`() {
            assertEquals(
                SparkPagerRepaint.HEADING_UNREADABLE,
                classifySparkPagerRepaint(null, 2, current, target),
            )
        }

        @Test
        fun `unreadable dots block even when the heading names the target`() {
            for (dotIndex in listOf(null, 0, 3)) {
                assertEquals(
                    SparkPagerRepaint.DOTS_UNREADABLE,
                    classifySparkPagerRepaint(SparkSetSide.ORIGINAL, dotIndex, current, target),
                )
            }
        }

        @Test
        fun `a target heading with the wrong dot is a contradiction, never a success`() {
            assertEquals(
                SparkPagerRepaint.CONTRADICTION,
                classifySparkPagerRepaint(SparkSetSide.ORIGINAL, 1, current, target),
            )
            assertEquals(
                SparkPagerRepaint.CONTRADICTION,
                classifySparkPagerRepaint(SparkSetSide.REROLLED, 2, current, target),
            )
        }

        @Test
        fun `the dot mapping matches the live-proven page order and resolvePagerSide`() {
            assertEquals(SparkSetSide.REROLLED, sparkPagerDotSide(1))
            assertEquals(SparkSetSide.ORIGINAL, sparkPagerDotSide(2))
            assertNull(sparkPagerDotSide(null))
            assertNull(sparkPagerDotSide(0))
            assertNull(sparkPagerDotSide(3))
            // resolvePagerSide and the repaint classifier must never disagree on the mapping.
            assertEquals(
                SparkPagerResolution.Resolved(SparkSetSide.ORIGINAL),
                resolvePagerSide(SparkSetSide.ORIGINAL, 2),
            )
        }
    }

    @Nested
    @DisplayName("source guards: actuation and Confirm gating")
    inner class SourceGuards {
        private fun navigator(): String = sourceFile("CareerLaunchNavigator.kt").readText()

        private fun navigateBody(): String {
            val text = navigator()
            val start = text.indexOf("private fun navigateToPagerPage(")
            assertTrue(start >= 0, "navigateToPagerPage must exist")
            val end = text.indexOf("private fun handleSparkSelectionPager(", start)
            assertTrue(end > start, "handleSparkSelectionPager must follow navigateToPagerPage")
            return text.substring(start, end)
        }

        private fun pagerHandlerBody(): String {
            val text = navigator()
            val start = text.indexOf("private fun handleSparkSelectionPager(")
            assertTrue(start >= 0)
            val end = text.indexOf("private fun", start + 10)
            return text.substring(start, if (end > start) end else text.length)
        }

        @Test
        fun `pager navigation is swipe-only -- no tap is dispatched`() {
            val body = navigateBody()
            assertTrue("gestureUtils.swipe(" in body, "the pager must page with a central swipe")
            assertFalse("gestureUtils.tap(" in body, "no tap may be dispatched; taps at the chevron failed live")
        }

        @Test
        fun `the navigator no longer references the chevron tap coordinates at all`() {
            // The chevron constants stay in SparkScreenProbes for DETECTION (the structural
            // probe that recognizes the pager); the navigator must not import them for taps.
            assertFalse("SPARK_PAGER_CHEVRON" in navigator())
        }

        @Test
        fun `the swipe is verified by fresh captures, not by the settle timer`() {
            val body = navigateBody()
            val swipeAt = body.indexOf("gestureUtils.swipe(")
            val classifyAt = body.indexOf("classifySparkPagerRepaint(", swipeAt)
            val freshCapture = body.indexOf("getSourceBitmap()", swipeAt)
            assertTrue(classifyAt > swipeAt, "the repaint classifier must run after the swipe")
            assertTrue(freshCapture in (swipeAt + 1) until classifyAt, "classification must read a capture taken after the swipe")
        }

        @Test
        fun `navigateToPagerPage never presses Confirm`() {
            assertFalse("ButtonConfirm" in navigateBody())
            assertFalse("SPARK_PAGER_CONFIRM" in navigateBody())
        }

        @Test
        fun `the pager handler presses Confirm exactly once, only after the winner page check`() {
            val body = pagerHandlerBody()
            val clicks = Regex("ButtonConfirm\\.click").findAll(body).count()
            assertEquals(1, clicks, "exactly one Confirm press site in the pager handler")
            val winnerGate = body.indexOf("if (side != winner)")
            val confirmAt = body.indexOf("ButtonConfirm.click")
            assertTrue(winnerGate in 0 until confirmAt, "Confirm must sit after the winner-page navigation gate")
        }

        @Test
        fun `only an unchanged page consumes the single retry -- unprovable pages block immediately`() {
            val body = pagerHandlerBody()
            assertEquals(2, Regex("usePagerNavRetry\\(\\)").findAll(body).count(), "one retry gate per navigation call site")
            assertEquals(
                2,
                Regex("is PagerNavOutcome\\.Unverifiable -> return sparkSelectionBlocked").findAll(body).count(),
                "an unprovable page must block without another gesture at both call sites",
            )
        }

        @Test
        fun `a degraded decision refuses the fixed Confirm coordinate, and the policy seam sits between the comparison and the winner`() {
            // Both properties are wiring, not policy: the pure decision tests would still pass if
            // the navigator called the seam in the wrong place or let a degraded commit fall
            // through to the anchor tap, so they are pinned here against the source.
            val body = pagerHandlerBody()

            // 1. A degraded evaluation must never commit on the fixed coordinate. The refusal has
            // to be reached BEFORE the anchor tap, which stays available to a certain comparison.
            val degradedRefusal = body.indexOf("transaction.choice?.certain == false")
            val anchorTap = body.indexOf("gestureUtils.tap(SPARK_PAGER_CONFIRM_X")
            assertTrue(degradedRefusal >= 0, "the degraded-commit refusal must exist in the pager handler")
            assertTrue(anchorTap >= 0, "the certain path keeps its fixed Confirm anchor")
            assertTrue(degradedRefusal < anchorTap, "an uncertain evaluation must be refused before the anchor tap is reachable")

            // 2. The seam decides after the comparison and before anything is committed, so no
            // path can select a winner without passing through it.
            val compareAt = body.indexOf("SparkKeepPolicy.choose(")
            val decideAt = body.indexOf("SparkSelectionPolicy.decide(")
            val selectAt = body.indexOf("selectWinner(")
            assertTrue(compareAt >= 0, "the comparison must still run")
            assertTrue(decideAt > compareAt, "the decision seam must consume the comparison, not replace it")
            assertTrue(selectAt > decideAt, "no winner may be selected before the seam has decided")
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
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
