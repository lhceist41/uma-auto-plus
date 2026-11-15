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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Every engine pair is recycled by its owner, so Android's finalizer never frees one. A recycled engine reached again is a
 * native crash, so the release must wait for a running OCR call and every later call must skip the engines.
 */
@DisplayName("Tesseract release")
class TesseractReleaseTest {
    private class FakeEngine {
        val recycles = AtomicInteger(0)
        val usedAfterRecycle = AtomicBoolean(false)

        fun read(): String {
            if (recycles.get() > 0) usedAfterRecycle.set(true)
            return "text"
        }
    }

    private val lock = Any()
    private val inFlight = AtomicInteger(0)
    private val released = AtomicBoolean(false)
    private val text = FakeEngine()
    private val digits = FakeEngine()

    private fun release(waitMs: Long = 5_000, recycle: (FakeEngine) -> Unit = { it.recycles.incrementAndGet() }): Boolean =
        CustomImageUtils.releaseEnginesOnce(lock, inFlight, released, waitMs, { text to digits }, recycle)

    private fun read(engine: FakeEngine = text): String = CustomImageUtils.runOcrUnlessReleased(lock, inFlight, released, "") { engine.read() }

    @Test
    @DisplayName("the first release recycles both engines; a second one recycles nothing")
    fun releaseIsIdempotent() {
        assertTrue(release())
        assertFalse(release())
        assertEquals(1 to 1, text.recycles.get() to digits.recycles.get())
        assertEquals(0, inFlight.get())
    }

    @Test
    @DisplayName("OCR after the release reads nothing and never touches an engine")
    fun noOcrAfterRelease() {
        assertEquals("text", read())
        release()
        assertEquals("", read())
        assertEquals("", read(digits))
        assertFalse(text.usedAfterRecycle.get() || digits.usedAfterRecycle.get())
        assertEquals(0, inFlight.get())
    }

    @Test
    @DisplayName("a failing recycle of the text engine still recycles the digits engine and counts as released")
    fun failingRecycleStillFreesTheOther() {
        val failure = runCatching { release(recycle = { if (it === text) error("native") else it.recycles.incrementAndGet() }) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(1, digits.recycles.get())
        assertTrue(released.get())
        assertEquals("", read())
        assertEquals(0, inFlight.get())
    }

    @Test
    @Timeout(10)
    @DisplayName("a release waits for the OCR call holding the lock and frees the engines only after it left")
    fun releaseWaitsForRunningOcr() {
        val entered = CountDownLatch(1)
        val leave = CountDownLatch(1)
        val reader =
            Thread {
                read {
                    entered.countDown()
                    leave.await(5, TimeUnit.SECONDS)
                    text.read()
                }
            }
        reader.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val releaser = Thread { release() }.apply { start() }
        releaser.join(300)
        assertTrue(releaser.isAlive, "the release must wait for the lock")
        assertEquals(0, text.recycles.get())
        leave.countDown()
        reader.join()
        releaser.join(5_000)
        assertFalse(releaser.isAlive)
        assertEquals(1, text.recycles.get())
        assertFalse(text.usedAfterRecycle.get())
    }

    @Test
    @Timeout(30)
    @DisplayName("parallel OCR threads never touch an engine after a release lands among them")
    fun noUseAfterReleaseUnderLoad() {
        val stop = AtomicBoolean(false)
        val readers =
            (1..6).map { n ->
                Thread {
                    // A read that comes back empty saw the release; like a finished analysis thread, it stops reading.
                    while (!stop.get() && read(if (n % 2 == 0) text else digits).isNotEmpty()) Unit
                }.apply { start() }
            }
        Thread.sleep(50)
        val releasers = (1..3).map { Thread { release() }.apply { start() } }
        releasers.forEach { it.join() }
        Thread.sleep(50)
        stop.set(true)
        readers.forEach { it.join() }
        assertEquals(1 to 1, text.recycles.get() to digits.recycles.get())
        assertFalse(text.usedAfterRecycle.get() || digits.usedAfterRecycle.get())
        assertEquals(0, inFlight.get())
    }

    private fun read(block: () -> String): String = CustomImageUtils.runOcrUnlessReleased(lock, inFlight, released, "", block)

    /** A reader that holds the lock inside a read until [leave] opens: a wedged native call while [leave] stays shut. */
    private fun holdLock(leave: CountDownLatch): Thread {
        val entered = CountDownLatch(1)
        val holder =
            Thread {
                read {
                    entered.countDown()
                    leave.await()
                    text.read()
                }
            }.apply {
                isDaemon = true
                start()
            }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        return holder
    }

    @Test
    @Timeout(10)
    @DisplayName("behind a wedged read the release gives up within its wait, recycles nothing, and later OCR still reads nothing")
    fun wedgedReadBoundsTheRelease() {
        val leave = CountDownLatch(1)
        val holder = holdLock(leave)
        var result: Boolean? = null
        var tookMs = -1L
        // On its own daemon thread: an unbounded release would hang the test JVM instead of failing.
        val caller =
            Thread {
                val started = System.nanoTime()
                result = release(waitMs = 300)
                tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            }.apply {
                isDaemon = true
                start()
            }
        caller.join(5_000)
        assertFalse(caller.isAlive, "the release must return while the read is still wedged")
        assertEquals(false, result)
        assertTrue(tookMs in 250..2_000, "took $tookMs ms")
        assertTrue(released.get())
        assertEquals(0 to 0, text.recycles.get() to digits.recycles.get())
        leave.countDown()
        holder.join(5_000)
        assertEquals("", read())
        assertFalse(release())
        assertEquals(0 to 0, text.recycles.get() to digits.recycles.get(), "the pair stays with the finalizer")
        assertFalse(text.usedAfterRecycle.get())
        assertEquals(0, inFlight.get())
    }

    @Test
    @Timeout(10)
    @DisplayName("an interrupted caller still waits for a finishing read, recycles after it, and keeps its interrupt flag")
    fun interruptedCallerWaitsAndKeepsFlag() {
        val leave = CountDownLatch(1)
        val holder = holdLock(leave)
        var result: Boolean? = null
        var tookMs = -1L
        var stillInterrupted = false
        var recycledWhileHeld = -1
        val caller =
            Thread {
                Thread.currentThread().interrupt()
                val started = System.nanoTime()
                result = release()
                tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                stillInterrupted = Thread.currentThread().isInterrupted
            }
        caller.isDaemon = true
        caller.start()
        Thread.sleep(300)
        recycledWhileHeld = text.recycles.get()
        assertTrue(caller.isAlive, "an interrupt must not end the wait")
        leave.countDown()
        holder.join(5_000)
        caller.join(5_000)
        assertEquals(0, recycledWhileHeld)
        assertEquals(true, result)
        assertTrue(tookMs >= 250, "took $tookMs ms")
        assertTrue(stillInterrupted, "the stop's interrupt is not swallowed")
        assertEquals(1 to 1, text.recycles.get() to digits.recycles.get())
        assertFalse(text.usedAfterRecycle.get())
    }

    @Test
    @Timeout(10)
    @DisplayName("an interrupted caller behind a wedged read still returns within the wait, interrupt kept")
    fun interruptedCallerBehindWedgeIsBounded() {
        val leave = CountDownLatch(1)
        val holder = holdLock(leave)
        var result: Boolean? = null
        var tookMs = -1L
        var stillInterrupted = false
        val caller =
            Thread {
                Thread.currentThread().interrupt()
                val started = System.nanoTime()
                result = release(waitMs = 300)
                tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                stillInterrupted = Thread.currentThread().isInterrupted
            }
        caller.isDaemon = true
        caller.start()
        caller.join(5_000)
        assertFalse(caller.isAlive)
        assertEquals(false, result)
        assertTrue(tookMs in 250..2_000, "took $tookMs ms")
        assertTrue(stillInterrupted)
        assertEquals(0 to 0, text.recycles.get() to digits.recycles.get())
        leave.countDown()
        holder.join(5_000)
    }

    private val base = "android/app/src/main/java/com/steve1316/uma_android_automation"

    @Test
    @DisplayName("each queue run releases its Game's engines in a finally after Game.start()")
    fun runReleasesItsGame() {
        val startModule = source("$base/StartModule.kt")
        val run = startModule.substring(startModule.indexOf("private fun runSingleGame("), startModule.indexOf("private fun interruptibleWait("))
        val start = run.indexOf("taskResult = entryPoint.start()")
        val release = run.indexOf("entryPoint.imageUtils.releaseTesseract()")
        assertTrue(start > 0 && release > start)
        assertTrue(run.lastIndexOf("finally {", release) > start, "the release must run when start() throws")
    }

    @Test
    @DisplayName("the navigator frees only a Game it built itself, and forgets it so a later call builds a new one")
    fun navigatorClosesOwnGame() {
        val nav = source("$base/CareerLaunchNavigator.kt")
        assertTrue(nav.contains("class CareerLaunchNavigator(private val context: Context) : AutoCloseable {"))
        val ensure = nav.substring(nav.indexOf("private fun ensureInitialised()"), nav.indexOf("fun attachLiveGame("))
        assertTrue(ensure.indexOf("ownsTempGame = true") > ensure.indexOf("val game = Game(context)"))
        val attach = nav.substring(nav.indexOf("fun attachLiveGame("), nav.indexOf("override fun close()"))
        assertFalse(attach.contains("ownsTempGame = true"))
        assertTrue(attach.indexOf("close()") in 0 until attach.indexOf("tempGame = game"), "a Game it built is freed and disowned before the live one is attached")
        val close = nav.substring(nav.indexOf("override fun close()"), nav.indexOf("/** The Rewards and Club step"))
        assertTrue(close.indexOf("if (!ownsTempGame) return") in 0 until close.indexOf("releaseTesseract()"))
        assertTrue(close.contains("tempGame = null") && close.contains("imageUtils = null"))
        assertEquals(1, Regex("ownsTempGame = true").findAll(nav).count())
    }

    @Test
    @DisplayName("every navigator that may build its own Game is closed by its owner")
    fun everyOwnerClosesItsNavigator() {
        for (file in listOf("StartModule.kt", "bot/Game.kt", "bot/misc/MiscTask.kt")) {
            val text = source("$base/$file")
            val sites = Regex("CareerLaunchNavigator\\((context|myContext|game\\.myContext)\\)").findAll(text).toList()
            assertTrue(sites.isNotEmpty(), file)
            for (site in sites) {
                val after = text.substring(site.range.last + 1)
                val line = text.substring(text.lastIndexOf('\n', site.range.first) + 1, site.range.first)
                val name = Regex("val (\\w+) =").find(line)?.groupValues?.get(1)
                val closed =
                    after.startsWith(".use {") ||
                        line.contains("?: CareerLaunchNavigator(") ||
                        (name != null && Regex("$name(\\?)?\\.(close\\(\\)|use \\{)").containsMatchIn(after))
                assertTrue(closed, "$file: ${line.trim()}${site.value}")
            }
        }
        val start = source("$base/StartModule.kt")
        val wrapper = start.substring(start.indexOf("private fun navigateWithDeadline("), start.indexOf("private fun stoppedNavigation()"))
        assertTrue(wrapper.contains("val navigator = launchNavigator ?: CareerLaunchNavigator(context)"))
        assertTrue(wrapper.lastIndexOf("finally {") < wrapper.indexOf("if (launchNavigator == null) navigator.close()"), "a passed-in navigator stays its caller's")
        val cold = start.substring(start.indexOf("val coldStartNavigator ="))
        assertTrue(cold.indexOf("} finally {") < cold.indexOf("coldStartNavigator?.close()"))
    }

    @Test
    @DisplayName("campaign navigators only borrow the live Game's tools and build none of their own")
    fun campaignNavigatorsBorrow() {
        val campaign = source("$base/bot/Campaign.kt")
        val lines = campaign.lines()
        val sites = lines.indices.filter { lines[it].contains("CareerLaunchNavigator(game.myContext)") }
        assertTrue(sites.isNotEmpty())
        for (i in sites) {
            val here = lines[i] + lines.getOrElse(i + 1) { "" }
            assertTrue(here.contains("(game.imageUtils)") || here.contains("attachLiveGame(game)"), lines[i].trim())
        }
    }

    private fun root(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, base).isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found")
    }

    private fun source(relative: String): String = File(root(), relative).readText().replace("\r\n", "\n")
}
