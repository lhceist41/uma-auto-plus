package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@DisplayName("RssGc")
class RssGcTest {
    private val clock = AtomicLong(1_000_000)
    private val collections = AtomicInteger(0)
    private val lines = mutableListOf<String>()
    private var rss: Long? = 0

    /** Share of RssAnon a collection frees; garbage on MuMu freed 42-83 %. */
    private var freedShare = 0.5

    private fun policy(read: () -> Long? = { rss }) =
        RssGc(
            thresholdKb = 700L * 1024,
            minIntervalMs = 3_000,
            maxIntervalMs = 30_000,
            readRssKb = read,
            now = { clock.get() },
            collect = {
                collections.incrementAndGet()
                rss = rss?.let { (it * (1 - freedShare)).toLong() }
            },
            log = { synchronized(lines) { lines.add(it) } },
        )

    @Test
    @DisplayName("below the threshold nothing is collected; at it one collection runs and is logged with before and after")
    fun threshold() {
        val gc = policy()
        rss = 700L * 1024 - 1
        assertFalse(gc.check("turn"))
        assertEquals(0, collections.get())
        rss = 700L * 1024
        assertTrue(gc.check("turn"))
        assertEquals(1, collections.get())
        assertEquals(1, gc.runCount)
        assertTrue(lines.single().startsWith("[MEM] event=gc trigger=turn rssAnonKB=716800->358400 ms="), lines.single())
        assertTrue(lines.single().endsWith(" gcRuns=1"))
    }

    @Test
    @DisplayName("at most one collection per 3 s while collections free garbage")
    fun rateLimit() {
        val gc = policy()
        rss = 2_000_000
        assertTrue(gc.check("turn"))
        rss = 2_000_000
        clock.addAndGet(2_999)
        assertFalse(gc.check("capture"))
        clock.addAndGet(1)
        assertTrue(gc.check("capture"))
        assertEquals(2, collections.get())
        assertEquals(3_000, gc.currentIntervalMs)
        assertTrue(lines.none { it.contains("event=gc_interval") })
    }

    @Test
    @DisplayName("collections that free under 15 % double the interval up to 30 s, each change logged once")
    fun backOff() {
        val gc = policy()
        freedShare = 0.10
        val intervals = mutableListOf<Long>()
        repeat(6) {
            rss = 2_000_000
            clock.addAndGet(gc.currentIntervalMs)
            assertTrue(gc.check("turn"))
            intervals.add(gc.currentIntervalMs)
        }
        assertEquals(listOf(6_000L, 12_000L, 24_000L, 30_000L, 30_000L, 30_000L), intervals)
        assertEquals(4, lines.count { it.contains("event=gc_interval") })
        assertTrue(lines.any { it == "[MEM] event=gc_interval ms=6000 freedKB=200000" })
        rss = 2_000_000
        clock.addAndGet(29_999)
        assertFalse(gc.check("capture"), "the backed-off interval rate-limits")
    }

    @Test
    @DisplayName("a collection that frees a lot resets the interval to 3 s")
    fun resetAfterGarbage() {
        val gc = policy()
        freedShare = 0.05
        repeat(2) {
            rss = 1_500_000
            clock.addAndGet(gc.currentIntervalMs)
            gc.check("turn")
        }
        assertEquals(12_000, gc.currentIntervalMs)
        freedShare = 0.6
        rss = 1_500_000
        clock.addAndGet(12_000)
        assertTrue(gc.check("turn"))
        assertEquals(3_000, gc.currentIntervalMs)
        assertTrue(lines.last().startsWith("[MEM] event=gc_interval ms=3000 freedKB="))
    }

    @Test
    @DisplayName("an unreadable reading after a collection leaves the interval alone")
    fun unknownAfter() {
        var reads = 0
        val gc = policy { if (reads++ == 0) 2_000_000L else null }
        assertTrue(gc.check("turn"))
        assertEquals(3_000, gc.currentIntervalMs)
    }

    @Test
    @DisplayName("the between-runs cleanup runs the same collection as the policy")
    fun cleanupUsesTheCollection() {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, "android/app/src/main/java").isDirectory) dir = dir.parentFile
        val game = File(dir!!, "android/app/src/main/java/com/steve1316/uma_android_automation/bot/Game.kt").readText().replace("\r\n", "\n")
        val cleanup = game.substring(game.indexOf("fun cleanupBetweenRuns()"), game.indexOf("init {", game.indexOf("fun cleanupBetweenRuns()")))
        assertTrue(cleanup.contains("RssGc.collectGarbage()"))
        assertFalse(cleanup.contains("System.gc()"), "libcore's System.gc() can skip the collection")
        assertEquals(listOf(3_000L, 30_000L, 15L), listOf(RssGc.MIN_INTERVAL_MS, RssGc.MAX_INTERVAL_MS, RssGc.WEAK_FREE_PERCENT))
    }

    @Test
    @DisplayName("an unreadable RssAnon never collects and is logged once")
    fun unknownRss() {
        val gc = policy { null }
        repeat(5) { assertFalse(gc.check("turn")) }
        assertEquals(0, collections.get())
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("unreadable"))
    }

    @Test
    @Timeout(10)
    @DisplayName("threads racing past the threshold at the same moment collect once")
    fun racingThreads() {
        val gc = policy { 5_000_000 }
        val start = CountDownLatch(1)
        val wins = AtomicInteger(0)
        val threads =
            (1..8).map {
                Thread {
                    start.await(5, TimeUnit.SECONDS)
                    if (gc.check("capture")) wins.incrementAndGet()
                }.apply { start() }
            }
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, wins.get())
        assertEquals(1, collections.get())
    }

    @Test
    @DisplayName("captures are counted always, checked every tenth, and never collect where the caller holds a lock")
    fun captureCadence() {
        val gc = policy()
        rss = 2_000_000
        repeat(9) { assertFalse(gc.noteCapture()) }
        assertEquals(0, collections.get())
        assertTrue(gc.noteCapture())
        assertEquals(1, collections.get())
        clock.addAndGet(60_000)
        rss = 2_000_000
        repeat(10) { assertFalse(gc.noteCapture(mayCollect = false)) }
        assertEquals(1, collections.get())
        assertEquals(20L, gc.captureCount)
    }

    @Test
    @DisplayName("the collection uses Runtime.gc around the finalizers, outside the OCR lock and the watchdogs")
    fun wiring() {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, "android/app/src/main/java").isDirectory) dir = dir.parentFile
        val base = File(dir!!, "android/app/src/main/java/com/steve1316/uma_android_automation")
        fun src(path: String) = File(base, path).readText().replace("\r\n", "\n")
        val policy = src("utils/RssGc.kt")
        val collect = policy.substring(policy.indexOf("fun collectGarbage()"))
        assertTrue(collect.indexOf("runtime.gc()") < collect.indexOf("runtime.runFinalization()"))
        assertTrue(collect.indexOf("runtime.runFinalization()") < collect.lastIndexOf("runtime.gc()"))
        assertFalse(collect.contains("System.gc()"), "libcore's System.gc() can skip the collection")
        val capture = src("utils/CustomImageUtils.kt").let { it.substring(it.indexOf("override fun getSourceBitmap("), it.indexOf("private var lastCrop")) }
        assertTrue(capture.contains("RssGc.shared.noteCapture(mayCollect = !Thread.holdsLock(ocrLock))"))
        val turn = src("bot/Campaign.kt").let { it.substring(it.indexOf("private fun onTurnBoundary()")) }
        assertTrue(turn.indexOf("RssGc.shared.check(\"turn\")") in 0 until turn.indexOf("game.memoryLog.turnEnd("))
        assertFalse(src("bot/StallWatchdog.kt").contains("RssGc"))
        val game = src("bot/Game.kt")
        // The heartbeat and watchdog block, and watchRun/checkCurrentRun; the between-runs cleanup is not a watchdog.
        assertFalse(game.substring(game.indexOf("fun heartbeat()"), game.indexOf("fun acquireWakeLock(")).contains("RssGc"))
        assertFalse(game.substring(game.indexOf("private fun watchRun()"), game.indexOf("fun start(): TaskResult")).contains("RssGc"))
    }
}
