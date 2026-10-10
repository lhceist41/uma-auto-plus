package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.FixturePng
import com.steve1316.uma_android_automation.utils.OwnInputProbeResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/** What one Back streak decided, tick by tick, as the Campaign Back branch drives the watch. */
private class StreakRun(val steps: List<BackStreakStep>, val probes: Int)

/** Frozen-game detection on MuMu captures (see fixtures/frozengame/PROVENANCE.md). */
@DisplayName("Frozen game")
class FrozenGameTest {
    /** The floating button where it sat in the freeze captures. */
    private val button = Triple(626, 33, 62)

    private val cache = mutableMapOf<String, IntArray>()

    private fun grid(
        name: String,
        withButton: Triple<Int, Int, Int>? = button,
    ): IntArray =
        cache.getOrPut("$name/${withButton != null}") {
            val png = requireNotNull(javaClass.getResourceAsStream("/fixtures/frozengame/$name.png")) { "missing fixture $name.png" }.use { FixturePng.read(it) }
            frozenFrameGrid(png.width, png.height, withButton, topInset = 0) { x, y -> png.getRGB(x, y) }
        }

    private val freezes =
        mapOf(
            "event dialogue" to (1..4).map { "freeze_a_$it" },
            "Home lobby, idle" to (1..4).map { "freeze_b_$it" },
            "Training screen" to (1..3).map { "freeze_c_$it" },
            "Home lobby, engine hang" to (1..3).map { "freeze_d_$it" },
        )

    /** Captures 4 s apart with one Back press between them, as the Back streak sends them. */
    private fun trackedFrozen(frames: List<String>): Boolean {
        val tracker = FrozenFrameTracker()
        frames.forEachIndexed { i, name -> tracker.record(grid(name), actions = i.toLong(), nowMs = i * 4_000L) }
        return tracker.frozen((frames.size - 1) * 4_000L)
    }

    @Nested
    @DisplayName("On the captures")
    inner class Captures {
        @Test
        fun `every frame of each freeze matches the first, with the floating button left out`() {
            for ((what, frames) in freezes) {
                for (name in frames.drop(1)) assertTrue(framesStill(grid(frames.first()), grid(name)), "$what: $name")
            }
        }

        @Test
        fun `the floating button alone would make a frozen frame look changed`() {
            assertFalse(framesStill(grid("freeze_a_3", withButton = null), grid("freeze_a_4", withButton = null)))
        }

        @Test
        fun `three or more captures of each freeze with Back presses read frozen`() {
            for ((what, frames) in freezes) {
                val repeated = if (frames.size >= 4) frames else frames + frames.last()
                assertTrue(trackedFrozen(repeated), what)
            }
        }

        @Test
        fun `a live Training screen never holds still`() {
            assertFalse(framesStill(grid("live_training_1"), grid("live_training_2")))
            assertFalse(framesStill(grid("live_training_3"), grid("live_training_4")))
            assertFalse(trackedFrozen(listOf("live_training_1", "live_training_2", "live_training_1", "live_training_2")))
        }

        @Test
        fun `a quiet live screen drifts against the window's first frame, so even taps do not make it frozen`() {
            // Still from one capture to the next, but two cells moved over the 4 s.
            assertTrue(framesStill(grid("still_event_1"), grid("still_event_2")))
            assertTrue(framesStill(grid("still_event_2"), grid("still_event_3")))
            assertFalse(framesStill(grid("still_event_1"), grid("still_event_3")))
            val tracker = FrozenFrameTracker()
            listOf("still_event_1", "still_event_2", "still_event_3", "still_event_3").forEachIndexed { i, name ->
                tracker.record(grid(name), actions = i.toLong(), nowMs = i * 4_000L)
            }
            assertFalse(tracker.frozen(12_000L))
        }

        @Test
        fun `a screen that truly holds still is not frozen without taps that should change it`() {
            val tracker = FrozenFrameTracker()
            listOf("still_event_1", "still_event_2", "still_event_1", "still_event_2").forEachIndexed { i, name ->
                tracker.record(grid(name), actions = 0L, nowMs = i * 4_000L)
            }
            assertFalse(tracker.frozen(12_000L))
        }
    }

    @Nested
    @DisplayName("Tracker thresholds")
    inner class Thresholds {
        private val still = IntArray(HOME_GRID_W * HOME_GRID_H) { 100 }
        private val moved = IntArray(HOME_GRID_W * HOME_GRID_H) { if (it == 300) 200 else 100 }

        /** Each capture: the grid, the running tap count, the time. */
        private fun tracker(vararg captures: Triple<IntArray, Long, Long>): FrozenFrameTracker =
            FrozenFrameTracker().apply { for ((grid, taps, at) in captures) record(grid, taps, at) }

        @Test
        fun `three captures, eight seconds and three taps are all needed`() {
            assertTrue(tracker(Triple(still, 10L, 0L), Triple(still, 12L, 4_000L), Triple(still, 13L, 8_000L)).frozen(8_000L))
            assertFalse(tracker(Triple(still, 10L, 0L), Triple(still, 13L, 8_000L)).frozen(8_000L), "two captures")
            assertFalse(tracker(Triple(still, 10L, 0L), Triple(still, 12L, 3_000L), Triple(still, 13L, 7_900L)).frozen(7_900L), "under 8 s")
            assertFalse(tracker(Triple(still, 10L, 0L), Triple(still, 11L, 4_000L), Triple(still, 12L, 8_000L)).frozen(8_000L), "two taps")
        }

        @Test
        fun `one changed cell starts the still window over`() {
            val t = tracker(Triple(still, 0L, 0L), Triple(still, 1L, 4_000L), Triple(moved, 2L, 8_000L), Triple(moved, 3L, 12_000L))
            assertFalse(t.frozen(12_000L))
            t.record(moved, 4L, 16_000L)
            t.record(moved, 5L, 20_000L)
            assertTrue(t.frozen(20_000L))
        }

        @Test
        fun `a fresh frame that moved, taken just before the decision, clears a freeze`() {
            val t = tracker(Triple(still, 0L, 0L), Triple(still, 2L, 4_000L), Triple(still, 3L, 8_000L))
            assertTrue(t.frozen(8_000L))
            t.record(moved, 3L, 9_000L)
            assertFalse(t.frozen(9_000L))
        }

        @Test
        fun `a reset forgets the still window`() {
            val t = tracker(Triple(still, 0L, 0L), Triple(still, 2L, 4_000L), Triple(still, 3L, 8_000L))
            t.reset()
            assertFalse(t.frozen(8_000L))
        }

        @Test
        fun `the cutout inset and the floating button are left out, the game's top bar is not`() {
            val black = { _: Int, _: Int -> 0xFF000000.toInt() }
            // MuMu: the first grid row (y 53) is the game's top bar, below any status bar.
            val mumu = frozenFrameGrid(1080, 1920, null, topInset = 0, pixel = black)
            assertEquals(0, mumu.count { it == FROZEN_GRID_MASKED })
            // Phone: the first row (y 64) sits inside the 94 px cutout inset.
            val phone = frozenFrameGrid(1080, 2316, null, topInset = 94, pixel = black)
            assertEquals(HOME_GRID_W, phone.count { it == FROZEN_GRID_MASKED })
            // The button's own sample point (658, 53) and nothing far from it.
            val withButton = frozenFrameGrid(1080, 1920, button, topInset = 0, pixel = black)
            assertEquals(FROZEN_GRID_MASKED, withButton[19])
            assertTrue(withButton.count { it == FROZEN_GRID_MASKED } <= 4)
        }
    }

    @Nested
    @DisplayName("Decisions")
    inner class Decisions {
        private val still = IntArray(HOME_GRID_W * HOME_GRID_H) { 100 }

        /**
         * Ticks 2 s apart. [backOnTick] says whether that tick pressed Back (false: a dialog or main-screen tick returned
         * early); [frameOf] gives the frame before the press. The probe answers [probeResult].
         */
        private fun drive(
            ticks: Int,
            probeResult: OwnInputProbeResult,
            otherAppInFront: Boolean = false,
            backOnTick: (Int) -> Boolean = { true },
            frameOf: (Int) -> IntArray = { still },
        ): StreakRun {
            val watch = BackStreakWatch()
            val steps = mutableListOf<BackStreakStep>()
            var presses = 0
            var probes = 0
            for (tick in 0 until ticks) {
                val now = tick * 2_000L
                watch.startTick()
                if (!backOnTick(tick)) {
                    presses = 0
                    continue
                }
                watch.backPressed(frameOf(tick), presses.toLong(), now)
                presses++
                val probe =
                    if (watch.probeNow(now) { otherAppInFront }) {
                        probes++
                        probeResult
                    } else {
                        null
                    }
                val step = backStreakStep(presses, maxPresses = 25, probe = probe)
                steps += step
                if (step != BackStreakStep.CONTINUE) break
            }
            return StreakRun(steps, probes)
        }

        @Test
        fun `a frozen frame whose taps arrive goes to the restart as soon as it reads frozen`() {
            val run = drive(ticks = 30, probeResult = OwnInputProbeResult.ARRIVED)
            assertEquals(BackStreakStep.REOPEN_FROZEN_GAME, run.steps.last())
            assertEquals(1, run.probes)
            assertTrue(run.steps.size in 4..6, "press ${run.steps.size}")
        }

        @Test
        fun `a LOST or INCONCLUSIVE probe keeps the old streak, one probe and then on to the 25-press stop`() {
            for (result in listOf(OwnInputProbeResult.LOST, OwnInputProbeResult.INCONCLUSIVE)) {
                val run = drive(ticks = 30, probeResult = result)
                assertEquals(1, run.probes, "$result")
                assertEquals(25, run.steps.size, "$result")
                assertTrue(run.steps.dropLast(1).all { it == BackStreakStep.CONTINUE }, "$result")
                assertEquals(BackStreakStep.STOP, run.steps.last(), "$result")
            }
        }

        @Test
        fun `a live screen Back does not leave is never probed and stops at the bound`() {
            val moved = IntArray(HOME_GRID_W * HOME_GRID_H) { 160 }
            val run = drive(ticks = 30, probeResult = OwnInputProbeResult.ARRIVED, frameOf = { if (it % 2 == 0) still else moved })
            assertEquals(0, run.probes)
            assertEquals(BackStreakStep.STOP, run.steps.last())
            assertEquals(25, run.steps.size)
        }

        @Test
        fun `another app in front is never probed`() {
            val run = drive(ticks = 30, probeResult = OwnInputProbeResult.ARRIVED, otherAppInFront = true)
            assertEquals(0, run.probes)
            assertEquals(BackStreakStep.STOP, run.steps.last())
        }

        @Test
        fun `a tick without Back ends the streak, so a static screen revisited between progress ticks never reads frozen`() {
            // Back on the same static screen, then a main-screen or dialog tick, over and over: a healthy career.
            val run = drive(ticks = 60, probeResult = OwnInputProbeResult.ARRIVED, backOnTick = { it % 2 == 0 })
            assertEquals(0, run.probes)
            assertTrue(run.steps.all { it == BackStreakStep.CONTINUE })
        }

        @Test
        fun `a misc mode restarts only a frozen game whose taps provably arrive, once`() {
            assertTrue(miscFrozenGameReopens(true, { false }, { OwnInputProbeResult.ARRIVED }, reopensUsed = 0))
            assertFalse(miscFrozenGameReopens(true, { false }, { OwnInputProbeResult.LOST }, reopensUsed = 0))
            assertFalse(miscFrozenGameReopens(true, { false }, { OwnInputProbeResult.INCONCLUSIVE }, reopensUsed = 0))
            assertFalse(miscFrozenGameReopens(true, { true }, { OwnInputProbeResult.ARRIVED }, reopensUsed = 0))
            assertFalse(miscFrozenGameReopens(true, { false }, { OwnInputProbeResult.ARRIVED }, reopensUsed = MAX_MISC_FROZEN_GAME_REOPENS))
        }

        @Test
        fun `the Back streak and the misc rails reach the restart through their own call sites`() {
            fun source(relative: String): String {
                var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
                repeat(8) {
                    val file = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation/$relative")
                    if (file.isFile) return file.readText().replace("\r\n", "\n")
                    dir = dir?.parentFile
                }
                throw AssertionError("$relative not found")
            }
            val campaign = source("bot/Campaign.kt")
            val back = campaign.substring(campaign.indexOf("} else if (ButtonBack.click(game.imageUtils, sourceBitmap = sourceBitmap)) {"))
            val streak = back.substring(0, back.indexOf("recordMiscNextOrBack(nextNow = false)"))
            assertTrue(streak.contains("backStreak.backPressed(game.frozenFrameGridOf(sourceBitmap), consecutiveMiscBackPresses.toLong()"))
            assertTrue(streak.contains("val probe = if (backStreak.probeNow(System.currentTimeMillis()) { game.otherAppInFront() }) game.ownInputReachesScreen() else null"))
            assertTrue(streak.indexOf("BackStreakStep.REOPEN_FROZEN_GAME") < streak.indexOf("stopForStuckInput(RebindEpisode(), "), "only the ARRIVED step reaches the stop")
            assertTrue(streak.contains(", probe)\n"), "the stop reuses the streak's probe")
            assertEquals(1, streak.split("stopForStuckInput(").size - 1)
            assertTrue(streak.contains("BackStreakStep.STOP"))
            // At the start of every tick, before the dialog and main-screen handlers return early.
            val tickStart = campaign.indexOf("bMiscStepTakenLastTick = false\n            backStreak.startTick()\n")
            assertTrue(tickStart in 0 until campaign.indexOf("// We always check for dialogs first."))

            val misc = source("bot/misc/MiscTask.kt")
            val rails = misc.substring(misc.indexOf("protected fun checkSafetyRails()"), misc.indexOf("private fun recoverFrozenGame()"))
            assertEquals(2, rails.split("if (recoverFrozenGame()) return null").size - 1, "both the UNKNOWN and the stuck bailout")
            assertTrue(rails.indexOf("MAX_ITERATIONS_SAFETY) {") < rails.indexOf("if (recoverFrozenGame())"), "the iteration cap never restarts")
            val recover = misc.substring(misc.indexOf("private fun recoverFrozenGame()"))
            assertTrue(recover.indexOf("game.reopenGame(attempt = 2)") < recover.indexOf("MessageLog.w(TAG, \"[RECOVERY]"), "the reopen acts before anything is logged")
            assertTrue(recover.contains("navigate(reuseLastLaunchSetup = true, finalizeToHome = true)"))
            assertTrue(misc.contains("frames.record(game.frozenFrameGridOf(it), actedIterations"))
            val fresh = recover.indexOf("frames.record(game.frozenFrameGridOf(game.imageUtils.getSourceBitmap()), actedIterations")
            assertTrue(fresh in 0 until recover.indexOf("val frozen = frames.frozen("), "a fresh frame is recorded before the decision")
        }

        @Test
        fun `nothing is probed or read while the frame is not frozen`() {
            var asked = 0
            val inFront = {
                asked++
                false
            }
            val probe = {
                asked++
                OwnInputProbeResult.ARRIVED
            }
            assertFalse(miscFrozenGameReopens(false, inFront, probe, reopensUsed = 0))
            assertEquals(0, asked)
        }
    }
}
