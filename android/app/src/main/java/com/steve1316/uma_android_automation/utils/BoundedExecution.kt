package com.steve1316.uma_android_automation.utils

import java.util.concurrent.atomic.AtomicReference

/**
 * Runs a blocking [block] under a wall-clock ceiling so a native or monitor wait that ignores
 * thread interruption cannot hang the caller.
 *
 * The motivating failure (2026-08-19, Taiki Shuttle Grand Concert): the career-end "Keep this set
 * of Sparks?" confirmation reads text with Tesseract, which runs behind a process-wide OCR lock and
 * inside a JNI call. A frame that wedged there produced a silent block - the queue thread stopped
 * logging and stopped tapping, the interrupt-based navigation deadline could not kill it (monitor
 * entry and native calls ignore [Thread.interrupt]), and the operator cleared the screen by hand
 * before the 3-minute stall watchdog would have hard-killed the process.
 *
 * The block runs on a daemon worker. If it finishes within [timeoutMs] its result is returned;
 * otherwise the worker is abandoned - it may still hold whatever lock it wedged on, so callers turn
 * a timeout into a bounded, queue-stopping failure rather than a retry - and [onTimeout] is
 * returned. Because interrupting the worker would not free a native wait, the worker is never
 * interrupted; it is simply left behind (daemon, so it never keeps the process alive).
 */
object BoundedExecution {
    private val PENDING = Any()

    /**
     * @param timeoutMs wall-clock ceiling for [block].
     * @param pollMs how often the waiting thread wakes to re-check the deadline and [shouldAbort].
     * @param shouldAbort checked on each poll; when it returns true the wait is abandoned and
     *   [onTimeout] is returned (used to honour a queue stop without waiting out the full deadline).
     * @param onTimeout produced when [block] overruns [timeoutMs] or [shouldAbort] trips. Runs on
     *   the caller's thread.
     * @param block the blocking work. Runs on a daemon worker thread.
     * @return [block]'s result, or [onTimeout]'s result if it did not finish in time.
     * @throws InterruptedException if the WAITING thread is interrupted (the navigation deadline
     *   relies on this propagating); the worker is left behind.
     */
    fun <T> runWithDeadline(
        timeoutMs: Long,
        pollMs: Long = 1_000L,
        shouldAbort: () -> Boolean = { false },
        onTimeout: () -> T,
        block: () -> T,
    ): T {
        val holder = AtomicReference<Any?>(PENDING)
        val failure = AtomicReference<Throwable?>(null)
        val worker =
            Thread {
                try {
                    holder.set(block())
                } catch (t: Throwable) {
                    failure.set(t)
                }
            }
        worker.name = "BoundedExecution"
        worker.isDaemon = true
        worker.start()

        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (shouldAbort()) return onTimeout()
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) return onTimeout()
            // join() throws InterruptedException if the waiting thread is interrupted; let it
            // propagate so the navigation deadline can still unwind a genuinely wedged queue.
            worker.join(minOf(pollMs, remaining))
            if (!worker.isAlive) break
        }

        failure.get()?.let { throw it }
        val result = holder.get()
        // A block that legitimately returns null is distinguished from "still pending" by the
        // worker having finished (loop broke on !isAlive); only the deadline path returns onTimeout.
        @Suppress("UNCHECKED_CAST")
        return if (result === PENDING) onTimeout() else result as T
    }
}
