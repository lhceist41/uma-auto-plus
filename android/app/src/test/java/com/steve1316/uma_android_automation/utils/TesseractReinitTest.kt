package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The re-creation test must never free an engine that an OCR call can still reach: a recycled TessBaseAPI used again is a
 * native crash, not an exception. Engines here are fakes that record use after recycle.
 */
@DisplayName("Tesseract re-creation")
class TesseractReinitTest {
    private class FakeEngine(val id: Int) {
        @Volatile var recycled = false
        val usedAfterRecycle = AtomicBoolean(false)

        fun read(): Int {
            if (recycled) usedAfterRecycle.set(true)
            return id
        }
    }

    private val lock = Any()
    private val inFlight = AtomicInteger(0)
    private var engine = FakeEngine(1)
    private val retired = mutableListOf<FakeEngine>()

    private fun swap() {
        val old = engine
        engine = FakeEngine(old.id + 1)
        old.recycled = true
        retired.add(old)
    }

    @Test
    @DisplayName("off: never due, however many turns pass")
    fun offNeverDue() {
        val schedule = TesseractReinitSchedule(enabled = false, everyTurns = 1)
        assertTrue((1..500).none { schedule.turnEnded() })
    }

    @Test
    @DisplayName("on: due every N turns, counted from the last re-creation")
    fun dueEveryNTurns() {
        val schedule = TesseractReinitSchedule(enabled = true, everyTurns = 3)
        assertEquals(listOf(false, false, true), (1..3).map { schedule.turnEnded() })
        schedule.reinitDone()
        assertEquals(listOf(false, false, true), (1..3).map { schedule.turnEnded() })
    }

    @Test
    @DisplayName("a skipped re-creation stays due at the next turn boundary")
    fun skippedStaysDue() {
        val schedule = TesseractReinitSchedule(enabled = true, everyTurns = 2)
        schedule.turnEnded()
        assertTrue(schedule.turnEnded())
        assertTrue(schedule.turnEnded())
    }

    @Test
    @DisplayName("an interval below one turn means every turn")
    fun intervalFloor() {
        val schedule = TesseractReinitSchedule(enabled = true, everyTurns = 0)
        assertTrue(schedule.turnEnded())
    }

    @Test
    @DisplayName("idle: the swap runs and the next OCR call uses the new engine")
    fun idleSwapsAndNewEngineWorks() {
        val old = engine
        assertTrue(CustomImageUtils.reinitWhenOcrIdle(lock, inFlight) { swap() })
        assertTrue(old.recycled)
        assertEquals(2, CustomImageUtils.runOcrCall(lock, inFlight) { engine.read() })
        assertFalse(old.usedAfterRecycle.get())
        assertEquals(0, inFlight.get())
    }

    @Test
    @Timeout(10)
    @DisplayName("an OCR call holding the lock: no swap, and none until it leaves")
    fun heldLockBlocksSwap() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reader =
            Thread {
                CustomImageUtils.runOcrCall(lock, inFlight) {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    engine.read()
                }
            }
        reader.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertFalse(CustomImageUtils.reinitWhenOcrIdle(lock, inFlight) { swap() })
        assertEquals(1, engine.id)
        release.countDown()
        reader.join()
        assertTrue(CustomImageUtils.reinitWhenOcrIdle(lock, inFlight) { swap() })
        assertEquals(2, engine.id)
        assertFalse(retired.single().usedAfterRecycle.get())
    }

    @Test
    @DisplayName("an analysis thread waiting for OCR: no swap")
    fun waitingCallBlocksSwap() {
        inFlight.incrementAndGet()
        assertFalse(CustomImageUtils.reinitWhenOcrIdle(lock, inFlight) { swap() })
        assertTrue(retired.isEmpty())
        inFlight.decrementAndGet()
        assertTrue(CustomImageUtils.reinitWhenOcrIdle(lock, inFlight) { swap() })
    }

    @Test
    @DisplayName("an OCR call that throws still leaves the in-flight count at zero")
    fun failedCallIsUncounted() {
        runCatching { CustomImageUtils.runOcrCall<Unit>(lock, inFlight) { error("OCR failed") } }
        assertEquals(0, inFlight.get())
        assertTrue(CustomImageUtils.reinitWhenOcrIdle(lock, inFlight) { swap() })
    }

    /** A text/digits pair as the library holds it; [init] replaces it in place like initTesseract does. */
    private var pair = FakeEngine(1) to FakeEngine(2)
    private val ready = mutableSetOf<FakeEngine>()

    private fun replace(init: () -> Unit) =
        CustomImageUtils.replaceEngines(
            read = { pair },
            write = { pair = it },
            init = init,
            ready = { it in ready },
            recycle = { it.recycled = true },
        )

    @Test
    @DisplayName("good new engines: the old pair is freed only after both passed")
    fun goodInitSwaps() {
        val old = pair
        replace {
            pair = FakeEngine(3) to FakeEngine(4)
            assertFalse(old.first.recycled || old.second.recycled)
            ready.addAll(listOf(pair.first, pair.second))
        }
        assertEquals(3 to 4, pair.first.id to pair.second.id)
        assertTrue(old.first.recycled && old.second.recycled)
        assertFalse(pair.first.recycled || pair.second.recycled)
    }

    @Test
    @DisplayName("a failed native init keeps the old engines usable, frees the dead ones and stops further re-creations")
    fun failedInitKeepsOldEngines() {
        val old = pair
        val schedule = TesseractReinitSchedule(enabled = true, everyTurns = 1)
        assertTrue(schedule.turnEnded())
        var dead: Pair<FakeEngine, FakeEngine>? = null
        val failure =
            runCatching {
                replace {
                    pair = FakeEngine(3) to FakeEngine(4)
                    dead = pair
                    ready.add(pair.first)
                }
            }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        schedule.stop()
        assertTrue(pair === old)
        assertFalse(old.first.recycled || old.second.recycled)
        assertEquals(1, old.first.read())
        assertFalse(old.first.usedAfterRecycle.get())
        assertTrue(dead!!.first.recycled && dead!!.second.recycled)
        assertTrue((1..50).none { schedule.turnEnded() })
    }

    @Test
    @DisplayName("an OutOfMemoryError half way through puts the old pair back, frees the half-built engine and rethrows")
    fun throwableMidInitRestores() {
        val old = pair
        val half = FakeEngine(3)
        val failure =
            runCatching {
                replace {
                    pair = half to pair.second
                    throw OutOfMemoryError("native init")
                }
            }.exceptionOrNull()
        assertTrue(failure is OutOfMemoryError)
        assertTrue(pair === old)
        assertTrue(half.recycled)
        assertFalse(old.first.recycled || old.second.recycled)
    }

    @Test
    @Timeout(10)
    @DisplayName("a Throwable inside the swap releases the OCR lock and leaves the in-flight count at zero")
    fun throwableReleasesLock() {
        val failure = runCatching { CustomImageUtils.reinitWhenOcrIdle(lock, inFlight) { replace { throw OutOfMemoryError("native init") } } }.exceptionOrNull()
        assertTrue(failure is OutOfMemoryError)
        assertFalse(Thread.holdsLock(lock))
        val other = Thread { CustomImageUtils.runOcrCall(lock, inFlight) { engine.read() } }
        other.start()
        other.join(5_000)
        assertFalse(other.isAlive, "another thread can take the lock")
        assertEquals(0, inFlight.get())
    }

    @Test
    @Timeout(30)
    @DisplayName("parallel analysis threads never touch a recycled engine while swaps are attempted")
    fun noUseAfterRecycleUnderLoad() {
        val stop = AtomicBoolean(false)
        val readers =
            (1..6).map {
                Thread {
                    while (!stop.get()) CustomImageUtils.runOcrCall(lock, inFlight) { engine.read() }
                }.apply { start() }
            }
        var swaps = 0
        repeat(2_000) { if (CustomImageUtils.reinitWhenOcrIdle(lock, inFlight) { swap() }) swaps++ }
        stop.set(true)
        readers.forEach { it.join() }
        assertTrue(retired.none { it.usedAfterRecycle.get() })
        assertEquals(swaps, retired.size)
        assertEquals(0, inFlight.get())
    }
}
