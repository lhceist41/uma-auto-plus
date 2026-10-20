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

    private fun policy(read: () -> Long? = { rss }) =
        RssGc(
            thresholdKb = 700L * 1024,
            minIntervalMs = 10_000,
            readRssKb = read,
            now = { clock.get() },
            collect = {
                collections.incrementAndGet()
                rss = rss?.let { it / 2 }
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
    @DisplayName("at most one collection per 10 s, however high the memory stays")
    fun rateLimit() {
        val gc = policy()
        rss = 2_000_000
        assertTrue(gc.check("turn"))
        rss = 2_000_000
        clock.addAndGet(9_999)
        assertFalse(gc.check("capture"))
        clock.addAndGet(1)
        assertTrue(gc.check("capture"))
        assertEquals(2, collections.get())
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
        val collect = policy.substring(policy.indexOf("private fun collectGarbage()"))
        assertTrue(collect.indexOf("runtime.gc()") < collect.indexOf("runtime.runFinalization()"))
        assertTrue(collect.indexOf("runtime.runFinalization()") < collect.lastIndexOf("runtime.gc()"))
        assertFalse(collect.contains("System.gc()"), "libcore's System.gc() can skip the collection")
        val capture = src("utils/CustomImageUtils.kt").let { it.substring(it.indexOf("override fun getSourceBitmap("), it.indexOf("private var lastCrop")) }
        assertTrue(capture.contains("RssGc.shared.noteCapture(mayCollect = !Thread.holdsLock(ocrLock))"))
        val turn = src("bot/Campaign.kt").let { it.substring(it.indexOf("private fun onTurnBoundary()")) }
        assertTrue(turn.indexOf("RssGc.shared.check(\"turn\")") in 0 until turn.indexOf("game.memoryLog.turnEnd("))
        assertFalse(src("bot/StallWatchdog.kt").contains("RssGc"))
        val game = src("bot/Game.kt")
        assertFalse(game.substring(game.indexOf("fun heartbeat()"), game.indexOf("fun start(): TaskResult")).contains("RssGc"))
    }
}
