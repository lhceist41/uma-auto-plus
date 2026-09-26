package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Connection outages mid-career: a wall-clock outage budget replaces the old burst detector, which
 * retried forever when errors came more than 10 seconds apart and gave up after four quick ones,
 * reporting the stop as an unhandled exception. Loading screens get a dialog check and a hard bound.
 */
@DisplayName("Connection outage handling")
class ConnectionOutageTest {
    private class FakeClock(var nowMs: Long = 1_000_000L) {
        fun advance(ms: Long) {
            nowMs += ms
        }
    }

    private fun budget(clock: FakeClock) = ConnectionOutageBudget { clock.nowMs }

    @Nested
    @DisplayName("Outage budget")
    inner class Budget {
        @Test
        fun `a fast burst of errors keeps retrying instead of dying within seconds`() {
            val clock = FakeClock()
            val b = budget(clock)
            b.beginIteration()
            val waits =
                (1..6).map {
                    val d = b.onError()
                    assertTrue(d is ConnectionOutageBudget.Decision.Retry, "burst error $it gave up: $d")
                    clock.advance(1_000)
                    (d as ConnectionOutageBudget.Decision.Retry).waitMs
                }
            assertEquals(listOf(0L, 30_000L, 60_000L, 120_000L, 120_000L, 120_000L), waits)
        }

        @Test
        fun `slow retries that never recover end within the budget, not forever`() {
            val clock = FakeClock()
            val b = budget(clock)
            var decisions = 0
            var gaveUp: ConnectionOutageBudget.Decision.GiveUp? = null
            // Each main-loop iteration hits one error, retries after the pause, and returns normally.
            while (gaveUp == null && decisions < 1_000) {
                b.beginIteration()
                when (val d = b.onError()) {
                    is ConnectionOutageBudget.Decision.Retry -> clock.advance(d.waitMs + 15_000)
                    is ConnectionOutageBudget.Decision.GiveUp -> gaveUp = d
                }
                b.endIterationNormally()
                decisions++
            }
            assertNotNull(gaveUp, "the outage never gave up")
            assertTrue(gaveUp!!.elapsedMs >= ConnectionOutageBudget.OUTAGE_BUDGET_MS)
            assertTrue(gaveUp.elapsedMs < ConnectionOutageBudget.OUTAGE_BUDGET_MS + 3 * 60_000, "overshot: ${gaveUp.elapsedMs}")
            assertTrue(decisions < 30, "took $decisions decisions")
        }

        @Test
        fun `an iteration with no error ends the episode and re-arms the budget`() {
            val clock = FakeClock()
            val b = budget(clock)
            b.beginIteration()
            b.onError()
            clock.advance(ConnectionOutageBudget.OUTAGE_BUDGET_MS - 60_000)
            b.onError()
            b.endIterationNormally()
            b.beginIteration()
            b.endIterationNormally()
            clock.advance(10 * 60_000)
            val fresh = b.onError()
            assertTrue(fresh is ConnectionOutageBudget.Decision.Retry)
            assertEquals(0L, (fresh as ConnectionOutageBudget.Decision.Retry).waitMs, "a new episode starts with an immediate Retry")
        }

        @Test
        fun `a pause never runs past the end of the budget`() {
            val clock = FakeClock()
            val b = budget(clock)
            b.onError()
            clock.advance(ConnectionOutageBudget.OUTAGE_BUDGET_MS - 10_000)
            b.onError()
            b.onError()
            val d = b.onError()
            assertTrue(d is ConnectionOutageBudget.Decision.Retry)
            assertEquals(10_000L, (d as ConnectionOutageBudget.Decision.Retry).waitMs)
        }
    }

    @Nested
    @DisplayName("Run result")
    inner class RunResult {
        @Test
        fun `an exhausted budget ends the run as a connection error`() {
            assertEquals(TaskResultCode.TASK_RESULT_CONNECTION_ERROR, Task.interruptResultCode(skipRequested = false, stopRequested = false, serviceRunning = true, connectionLost = true))
        }

        @Test
        fun `a user stop or queue skip still wins over a lost connection`() {
            assertEquals(TaskResultCode.TASK_RESULT_MANUALLY_STOPPED, Task.interruptResultCode(false, true, true, true))
            assertEquals(TaskResultCode.TASK_RESULT_MANUALLY_STOPPED, Task.interruptResultCode(false, false, false, true))
            assertEquals(TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE, Task.interruptResultCode(true, false, true, true))
        }

        @Test
        fun `other internal interrupts stay unhandled exceptions`() {
            assertEquals(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, Task.interruptResultCode(false, false, true, false))
        }

        @Test
        fun `the give-up exception unwinds every existing stop path`() {
            assertTrue(InterruptedException::class.java.isAssignableFrom(ConnectionLostException::class.java))
        }
    }

    @Nested
    @DisplayName("Loading bounds")
    inner class Loading {
        private val step = 1_000L

        @Test
        fun `returns once loading clears`() {
            val clock = FakeClock()
            var checks = 0
            Game.awaitLoadingCleared(isLoading = { ++checks < 5 }, now = { clock.nowMs }, pause = { clock.advance(step) }, handleErrorDialog = { false })
            assertEquals(5, checks)
        }

        @Test
        fun `loading with no dialog stops at the hard bound as a lost connection`() {
            val clock = FakeClock()
            var reason: String? = null
            val start = clock.nowMs
            // Without a hard bound this would spin forever; fail fast instead of hanging the suite.
            val pause = {
                clock.advance(step)
                if (clock.nowMs - start > 2 * Game.LOADING_HARD_LIMIT_MS) throw AssertionError("no hard bound on loading")
            }
            val e =
                assertThrows(ConnectionLostException::class.java) {
                    Game.awaitLoadingCleared(isLoading = { true }, now = { clock.nowMs }, pause = pause, handleErrorDialog = { false }, onGiveUp = { reason = it })
                }
            assertEquals(Game.LOADING_HARD_LIMIT_MS, clock.nowMs - start)
            assertEquals(e.message, reason)
            assertTrue(e.message!!.contains("kept loading for 10 minutes"), e.message)
        }

        @Test
        fun `the dialog check runs on the soft interval while loading persists`() {
            val clock = FakeClock()
            val checkedAt = mutableListOf<Long>()
            val start = clock.nowMs
            assertThrows(ConnectionLostException::class.java) {
                Game.awaitLoadingCleared(
                    isLoading = { true },
                    now = { clock.nowMs },
                    pause = {
                        clock.advance(step)
                        if (clock.nowMs - start > 2 * Game.LOADING_HARD_LIMIT_MS) throw AssertionError("no hard bound on loading")
                    },
                    handleErrorDialog = {
                        checkedAt += clock.nowMs - start
                        false
                    },
                )
            }
            assertEquals(Game.LOADING_DIALOG_CHECK_MS, checkedAt.first())
            assertEquals((Game.LOADING_HARD_LIMIT_MS / Game.LOADING_DIALOG_CHECK_MS).toInt(), checkedAt.size)
        }

        @Test
        fun `an error dialog under the loading indicator is handled and restarts the hard window`() {
            val clock = FakeClock()
            val start = clock.nowMs
            var handled = 0
            // Loading lasts 11 minutes; one error dialog is found at the first soft check (90s),
            // so the hard window restarts there and the load clears before it runs out.
            Game.awaitLoadingCleared(
                isLoading = { clock.nowMs - start < 11 * 60_000 },
                now = { clock.nowMs },
                pause = { clock.advance(step) },
                handleErrorDialog = { (handled == 0).also { if (it) handled++ } },
            )
            assertEquals(1, handled)
        }
    }

    /**
     * The pure pieces above only protect a run if the handlers use them. These pin that Retry never
     * falls back to Title Screen, the give-up is sticky across swallowing catches, and the main loop
     * drives the episode.
     */
    @Nested
    @DisplayName("Wiring")
    inner class Wiring {
        private fun source(relative: String): String {
            var dir: File? = File(".").absoluteFile
            repeat(8) {
                listOf(File(dir, relative), File(dir, "app/$relative")).firstOrNull { it.isFile }?.let { return it.readText() }
                dir = dir?.parentFile
            }
            throw AssertionError("$relative not found")
        }

        private val base = "src/main/java/com/steve1316/uma_android_automation"

        private fun block(text: String, start: String, end: String): String {
            val s = text.indexOf(start)
            assertTrue(s >= 0, "missing: $start")
            val e = text.indexOf(end, s + start.length)
            return text.substring(s, if (e < 0) text.length else e)
        }

        @Test
        fun `the connection and download route taps Retry only, never Title Screen`() {
            val handler = source("$base/bot/DialogHandler.kt")
            assertTrue(handler.contains("\"connection_error\", \"download_error\" -> {\n                handleConnectionError(dialog)"))
            val route = block(handler, "private fun handleConnectionError(", "\n    }\n")
            assertTrue(route.contains("ButtonRetry.click("))
            assertFalse(route.contains("TitleScreen"), "the outage route must never tap Title Screen")
            assertFalse(route.contains(".ok("), "dialog.ok() is not the route: it must be Retry by name")
        }

        @Test
        fun `the Connection Error dialog's ok() has no Title Screen fallback`() {
            val dialog = block(source("$base/components/Dialog.kt"), "object DialogConnectionError", "\n}\n")
            assertFalse(dialog.contains("ButtonTitleScreen.click"), dialog)
        }

        @Test
        fun `a swallowed give-up is re-raised on the next wait tick`() {
            val wait = block(source("$base/bot/Game.kt"), "fun wait(seconds: Double", "fun waitForLoading()")
            assertTrue(wait.contains("connectionLostReason?.let { throw ConnectionLostException(it) }"))
        }

        @Test
        fun `the main loop opens and closes the outage episode around each iteration`() {
            val start = block(source("$base/bot/Task.kt"), "open fun start(", "private fun interruptResult(")
            val begin = start.indexOf("game.connectionBudget.beginIteration()")
            val process = start.indexOf("process()", begin)
            val end = start.indexOf("game.connectionBudget.endIterationNormally()", process)
            assertTrue(begin in 0 until process && end > process, "begin=$begin process=$process end=$end")
        }

        @Test
        fun `the settled result clears the give-up before teardown waits can re-raise it`() {
            // handleTaskEnd waits for the Discord flush; if the give-up were still set, that wait
            // would throw out of start() and a lost connection would be reported as unhandled.
            val start = block(source("$base/bot/Task.kt"), "open fun start(", "private fun interruptResult(")
            val clear = "game.connectionLostReason = null"
            assertEquals(1, Regex(Regex.escape(clear)).findAll(start).count(), "expected exactly one clear in start()")
            val loopEnd = start.lastIndexOf("result = interruptResult(")
            val at = start.indexOf(clear)
            val teardown = start.indexOf("handleTaskEnd(result)")
            assertTrue(loopEnd in 0 until at && at < teardown, "loop ends at $loopEnd, clear at $at, teardown at $teardown")
        }
    }
}
