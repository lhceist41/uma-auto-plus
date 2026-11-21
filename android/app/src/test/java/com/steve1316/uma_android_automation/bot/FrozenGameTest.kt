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

/** The energy stop's breakpoint, as Campaign throws it. */
private class Stopped(message: String) : RuntimeException(message)

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

    @Nested
    @DisplayName("Same turn on the Main screen")
    inner class SameTurn {
        private val still = IntArray(HOME_GRID_W * HOME_GRID_H) { 100 }

        /** A live Main screen: stats, energy or the turn box moved since the last pass. */
        private fun live(pass: Int) = IntArray(HOME_GRID_W * HOME_GRID_H) { if (it == 200 + pass % 50) 200 else 100 }

        /** Main screen passes 14 s apart (the measured pace of a looping pass), each with [tapsPerPass] taps, as watchSameTurn drives the watch. */
        private fun drive(
            passes: Int,
            probeResult: OwnInputProbeResult,
            frameOf: (Int) -> IntArray = { still },
            tapsPerPass: Long = 4L,
            otherAppInFront: Boolean = false,
        ): StreakRun {
            val watch = SameTurnWatch()
            val steps = mutableListOf<BackStreakStep>()
            var probes = 0
            for (pass in 0 until passes) {
                val now = pass * 14_000L
                val reads = watch.dateRead(12, frameOf(pass), pass * tapsPerPass, now)
                val probe =
                    if (watch.probeNow(now) { otherAppInFront }) {
                        probes++
                        probeResult
                    } else {
                        null
                    }
                val step = backStreakStep(reads, SAME_TURN_STOP_READS, probe)
                steps += step
                if (step != BackStreakStep.CONTINUE) break
            }
            return StreakRun(steps, probes)
        }

        @Test
        fun `a frozen Main screen whose taps arrive is probed once at the third read and goes to the reopen`() {
            val run = drive(passes = 40, probeResult = OwnInputProbeResult.ARRIVED)
            assertEquals(listOf(BackStreakStep.CONTINUE, BackStreakStep.CONTINUE, BackStreakStep.REOPEN_FROZEN_GAME), run.steps)
            assertEquals(1, run.probes)
        }

        @Test
        fun `Grand Concert pre-debut, every turn read as 12 on a live screen, is never probed and only stops at 30`() {
            val run = drive(passes = 40, probeResult = OwnInputProbeResult.ARRIVED, frameOf = ::live)
            assertEquals(0, run.probes)
            assertEquals(30, run.steps.size)
            assertTrue(run.steps.take(14 + 9).all { it == BackStreakStep.CONTINUE }, "the measured 14 reads and a 9-read loop on top stay below the bound")
            assertTrue(run.steps.dropLast(1).all { it == BackStreakStep.CONTINUE })
            assertEquals(BackStreakStep.STOP, run.steps.last())
        }

        @Test
        fun `a live loop of 9 same-day reads is never probed, and a changed day counts from 1 again`() {
            val watch = SameTurnWatch()
            for (pass in 0 until 9) {
                assertEquals(pass + 1, watch.dateRead(40, live(pass), pass * 4L, pass * 14_000L))
                assertFalse(watch.probeNow(pass * 14_000L) { false }, "read ${pass + 1}")
            }
            assertEquals(1, watch.dateRead(41, live(9), 36L, 126_000L))
        }

        @Test
        fun `a Main screen that holds still without taps in between is not frozen`() {
            val run = drive(passes = 40, probeResult = OwnInputProbeResult.ARRIVED, tapsPerPass = 0L)
            assertEquals(0, run.probes)
            assertEquals(BackStreakStep.STOP, run.steps.last())
        }

        @Test
        fun `a LOST or INCONCLUSIVE probe never reopens, is asked once, and the count runs on to the stop at 30`() {
            for (result in listOf(OwnInputProbeResult.LOST, OwnInputProbeResult.INCONCLUSIVE)) {
                val run = drive(passes = 40, probeResult = result)
                assertEquals(1, run.probes, "$result")
                assertEquals(SAME_TURN_STOP_READS, run.steps.size, "$result")
                assertTrue(run.steps.dropLast(1).all { it == BackStreakStep.CONTINUE }, "$result")
                assertEquals(BackStreakStep.STOP, run.steps.last(), "$result")
            }
        }

        @Test
        fun `another app in front is never probed`() {
            val run = drive(passes = 40, probeResult = OwnInputProbeResult.ARRIVED, otherAppInFront = true)
            assertEquals(0, run.probes)
            assertEquals(BackStreakStep.STOP, run.steps.last())
        }

        @Test
        fun `read 29 goes on and read 30 stops`() {
            assertEquals(30, SAME_TURN_STOP_READS)
            assertEquals(BackStreakStep.CONTINUE, backStreakStep(29, SAME_TURN_STOP_READS, null))
            assertEquals(BackStreakStep.STOP, backStreakStep(30, SAME_TURN_STOP_READS, null))
        }

        @Test
        fun `after a probe that proved nothing, a reopen or a new day lets a later freeze probe again`() {
            for (newDay in listOf(false, true)) {
                val watch = SameTurnWatch()
                var probes = 0
                for (pass in 0 until 3) {
                    watch.dateRead(30, still, pass * 4L, pass * 14_000L)
                    if (watch.probeNow(pass * 14_000L) { false }) probes++ // LOST: nothing happens
                }
                assertEquals(1, probes, "newDay=$newDay")
                if (!newDay) watch.reset()
                for (pass in 3 until 6) {
                    watch.dateRead(if (newDay) 31 else 30, still, pass * 4L, pass * 14_000L)
                    if (watch.probeNow(pass * 14_000L) { false }) probes++
                }
                assertEquals(2, probes, "newDay=$newDay: the second freeze is probed too")
            }
        }

        /** Two frozen reads of turn 30, then [between], then frozen reads of [nextDay]: how many of those reads the probe needed. */
        private fun readsToProbeAfter(
            nextDay: Int,
            between: (SameTurnWatch) -> Unit,
        ): Int {
            val watch = SameTurnWatch()
            watch.dateRead(30, still, 0L, 0L)
            watch.dateRead(30, still, 4L, 14_000L)
            between(watch)
            for (pass in 2 until 10) {
                watch.dateRead(nextDay, still, pass * 4L, pass * 14_000L)
                if (watch.probeNow(pass * 14_000L) { false }) return pass - 1
            }
            throw AssertionError("never probed")
        }

        @Test
        fun `a changed day and a reopen start the count and the frames over, an unknown tick does not`() {
            // Unknown-screen ticks never call the watch: the next read of the same frozen turn is its third, and probes.
            assertEquals(1, readsToProbeAfter(nextDay = 30) {})
            // A new day or a reopen needs three frozen reads of its own.
            assertEquals(3, readsToProbeAfter(nextDay = 31) {}, "a changed day")
            assertEquals(3, readsToProbeAfter(nextDay = 30) { it.reset() }, "a reopen")
            val watch = SameTurnWatch()
            watch.dateRead(30, still, 0L, 0L)
            watch.dateRead(30, still, 4L, 14_000L)
            watch.reset()
            assertEquals(1, watch.dateRead(30, still, 8L, 28_000L))
        }

        @Test
        fun `the changed-cell count is null on the first read of a turn and counts the cells that moved`() {
            val watch = SameTurnWatch()
            watch.dateRead(30, still, 0L, 0L)
            assertEquals(null, watch.changedCells)
            watch.dateRead(30, live(1), 4L, 14_000L)
            assertEquals(1, watch.changedCells)
            watch.dateRead(31, still, 8L, 28_000L)
            assertEquals(null, watch.changedCells, "a new day starts over")
        }

        /**
         * Two Main screen passes on turn 30 whose TRAIN found nothing worth taking, so each rests, with the watch wired into the energy stop as
         * Campaign wires it. Returns the reopens and the breakpoint text, if any.
         */
        private fun restTwice(
            frameOf: (Int) -> IntArray,
            probeResult: OwnInputProbeResult,
        ): Pair<Int, String?> {
            val watch = SameTurnWatch()
            val unmoved = UnmovedEnergyRecoveries()
            var taps = 0L
            var now = 0L
            var frame = 0
            var reopens = 0
            try {
                repeat(2) {
                    val reads = watch.dateRead(30, frameOf(frame++), taps, now)
                    val probe = if (watch.probeNow(now) { false }) probeResult else null
                    assertEquals(BackStreakStep.CONTINUE, backStreakStep(reads, SAME_TURN_STOP_READS, probe))
                    taps += 3
                    now += 7_000L
                    val moved =
                        recoverEnergyAndConfirmTurn(
                            day = 30,
                            unmoved = unmoved,
                            tryOuting = { false },
                            rest = {
                                taps += 2
                                true
                            },
                            raceDay = { false },
                            turnMoved = { false },
                            warn = {},
                            reopenFrozenGame = {
                                watch.frame(frameOf(frame++), taps, now)
                                val energyProbe = if (watch.probeNow(now) { false }) probeResult else null
                                if (energyProbe == OwnInputProbeResult.ARRIVED) {
                                    reopens++
                                    watch.reset()
                                    true
                                } else {
                                    false
                                }
                            },
                            stop = { throw Stopped(it) },
                        )
                    assertFalse(moved, "a reopen does not claim the turn moved")
                    now += 7_000L
                }
            } catch (e: Stopped) {
                return reopens to e.message
            }
            assertEquals(0, unmoved.on(30), "a reopen starts the Rest bound over")
            return reopens to null
        }

        @Test
        fun `the energy stop on a frozen Main screen whose taps arrive reopens the game instead of the breakpoint`() {
            assertEquals(1 to null, restTwice({ still }, OwnInputProbeResult.ARRIVED))
        }

        @Test
        fun `the energy stop on a live screen, or with taps not proven to arrive, keeps the old breakpoint and its text`() {
            val cases: List<Pair<(Int) -> IntArray, OwnInputProbeResult>> =
                listOf(
                    { pass: Int -> live(pass) } to OwnInputProbeResult.ARRIVED,
                    { _: Int -> still } to OwnInputProbeResult.LOST,
                    { _: Int -> still } to OwnInputProbeResult.INCONCLUSIVE,
                )
            for ((frames, result) in cases) {
                val (reopens, text) = restTwice(frames, result)
                assertEquals(0, reopens, "$result")
                assertEquals("Energy recovery did not move turn 30 after 2 tries, so the run stopped instead of looping. Rest once in the game, then press Start.", text)
            }
        }

        @Test
        fun `a third freeze in one run halts as not responding instead of reopening again`() {
            val key = stuckInputKey(RebindEpisode().stopKey(), OwnInputProbeResult.ARRIVED)
            assertEquals(GAME_NOT_RESPONDING, key)
            var used = 0
            repeat(MAX_UNRESPONSIVE_GAME_REOPENS_PER_RUN) {
                assertTrue(reopensUnresponsiveGame(key, careerObserved = true, reopensThisRun = used))
                used = unresponsiveReopensAfter(GameReopen.RESTARTED, used + 1, sdk = 30)
            }
            assertEquals(2, used)
            assertFalse(reopensUnresponsiveGame(key, careerObserved = true, reopensThisRun = used))
        }

        private fun source(relative: String): String {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            repeat(8) {
                val file = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation/$relative")
                if (file.isFile) return file.readText().replace("\r\n", "\n")
                dir = dir?.parentFile
            }
            throw AssertionError("$relative not found")
        }

        @Test
        fun `the Main screen read and the energy stop reach the reopen before anything is logged, and never hold the gesture service`() {
            val campaign = source("bot/Campaign.kt")
            // Fed right after the date read; one watch per Campaign, so a new run (a resume) starts empty.
            assertTrue(campaign.contains("val dateChanged = updateDate()\n            if (watchSameTurn(sourceBitmap)) return true\n"))
            assertTrue(campaign.contains("    private val sameTurn = SameTurnWatch()\n"))

            val watch = campaign.substring(campaign.indexOf("private fun watchSameTurn("), campaign.indexOf("private fun sameTurnProbe("))
            // The date after the read: a failed read leaves it as it was, so it counts as the same day.
            assertTrue(watch.contains("sameTurn.dateRead(date.day, game.frozenFrameGridOf(sourceBitmap), ProgressTracker.taps(), System.currentTimeMillis())"))
            val trip = watch.substring(watch.indexOf("val probe = sameTurnProbe()"), watch.indexOf("stopForStuckInput("))
            assertTrue(trip.contains("BackStreakStep.REOPEN_FROZEN_GAME ->"))
            assertFalse(trip.contains("MessageLog."), "nothing goes through MessageLog between the trip and the reopen")
            assertEquals(1, watch.split("stopForStuckInput(").size - 1)
            assertTrue(watch.contains(", probe)\n"), "the stop reuses the watch's probe")
            val stop = watch.substring(watch.indexOf("BackStreakStep.STOP ->"), watch.indexOf("BackStreakStep.CONTINUE ->"))
            assertTrue(stop.contains("turn \${date.day}"), "the stop names the repeated turn")
            assertTrue(stop.contains("game.notificationMessage = reason\n                throw CampaignBreakpointException(reason)"), "a breakpoint halts the queue")
            assertFalse(stop.contains("InterruptedException"), "a run error would let the next queue entry loop on the same turn")
            assertFalse(watch.contains("MessageLog."), "anything said before the reopen goes through android.util.Log")
            assertFalse(stop.contains("responding") || stop.contains("frozen"), "a live loop is never called a frozen game")

            val probeLine = campaign.substring(campaign.indexOf("private fun sameTurnProbe(")).substringBefore('\n')
            assertTrue(probeLine.contains("game.otherAppInFront()") && probeLine.contains("game.ownInputReachesScreen()"), "fetched per call")

            val energy = campaign.substring(campaign.indexOf("reopenFrozenGame = {"), campaign.indexOf("stop = { reason ->"))
            val fresh = energy.indexOf("sameTurn.frame(game.frozenFrameGridOf(game.imageUtils.getSourceBitmap()), ProgressTracker.taps(), System.currentTimeMillis())")
            assertTrue(fresh in 0 until energy.indexOf("val probe = sameTurnProbe()"), "a fresh frame goes in before the check")
            assertTrue(energy.indexOf("if (probe == OwnInputProbeResult.ARRIVED) {") in 0 until energy.indexOf("stopForStuckInput("), "only ARRIVED reopens")
            assertFalse(energy.contains("MessageLog."))

            // Every reset follows a reopen that acted: the known-screen restart, the ANR close and the unknown-screen rung.
            assertEquals(3, campaign.split("sameTurn.reset()").size - 1)
            val stuck = campaign.substring(campaign.indexOf("private fun stopForStuckInput("), campaign.indexOf("private fun stopIfTapsStopped("))
            assertTrue(stuck.indexOf("game.reopenGame(attempt = 2)") in 0 until stuck.indexOf("sameTurn.reset()"))
            val unknown = campaign.substring(campaign.indexOf("private fun recoverFromUnknownScreen("))
            assertTrue(unknown.indexOf("game.relaunchAfterAnrClose()") in 0 until unknown.indexOf("sameTurn.reset()"))
            assertTrue(unknown.indexOf("game.reopenGame(gameRestartAttemptsThisEpisode)") in 0 until unknown.lastIndexOf("sameTurn.reset()"))
            assertTrue(unknown.indexOf("sameTurn.reset()") < unknown.indexOf("game.reopenGame(gameRestartAttemptsThisEpisode)"))

            val stored = Regex("""(val|var) \w+(\s*:\s*MyAccessibilityService\b|\s*=\s*(game\.)?gestureUtils\b(?![.?(])|\s*=\s*MyAccessibilityService\.getInstance\(\))""")
            assertFalse(stored.containsMatchIn(campaign), "Campaign stores no gesture service")
            assertFalse(source("bot/FrozenGame.kt").contains("gesture"))
        }
    }
}
