package com.steve1316.uma_android_automation.utils

import android.os.SystemClock
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.MainActivity
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Collects garbage when the bot's anonymous resident memory passes [thresholdKb]. On Android 12 Scudo's mallinfo drifts
 * to 0 as a process ages, so ART's native-allocation GC trigger goes blind and capture bitmaps, crops and Mats wait for a
 * Java-heap GC; RssAnon from /proc does not drift. Checked every [CHECK_EVERY] captures and once per main-screen tick,
 * at most once per [minIntervalMs] across threads.
 */
class RssGc(
    private val thresholdKb: Long = THRESHOLD_KB,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val readRssKb: () -> Long? = MemoryLog::readRssAnonKb,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val collect: () -> Unit = ::collectGarbage,
    private val log: (String) -> Unit = { MessageLog.i(TAG, it) },
) {
    private val lastRunMs = AtomicLong(NEVER)
    private val runs = AtomicInteger(0)
    private val captures = AtomicLong(0)
    private val unreadableLogged = AtomicBoolean(false)

    /** Collections run so far in this process. */
    val runCount: Int get() = runs.get()

    /** Captures counted so far in this process. */
    val captureCount: Long get() = captures.get()

    /** Counts one capture and checks every [CHECK_EVERY]; [mayCollect] is false on a thread that holds a lock a collection must not hold. */
    fun noteCapture(mayCollect: Boolean = true): Boolean {
        val n = captures.incrementAndGet()
        return mayCollect && n % CHECK_EVERY == 0L && check("capture")
    }

    /** Collects when RssAnon is at or above the threshold and the last collection is old enough; true when this call collected. */
    fun check(trigger: String): Boolean {
        val before = readRssKb()
        if (before == null) {
            if (unreadableLogged.compareAndSet(false, true)) log("[MEM] RssAnon is unreadable here, so the memory GC stays off.")
            return false
        }
        if (before < thresholdKb) return false
        val start = now()
        val last = lastRunMs.get()
        if (last != NEVER && start - last < minIntervalMs) return false
        if (!lastRunMs.compareAndSet(last, start)) return false
        collect()
        val after = readRssKb()
        val n = runs.incrementAndGet()
        log("[MEM] event=gc trigger=$trigger rssAnonKB=$before->${after ?: "-"} ms=${now() - start} gcRuns=$n")
        return true
    }

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]RssGc"

        private const val NEVER = Long.MIN_VALUE

        /**
         * 700 MB of RssAnon (native heap plus 20-50 MB of Java heap and other private memory). On MuMu, fresh processes
         * peaked at 0.38-0.93 GB of native heap in a career and aged ones at 1.5-4.7 GB; this keeps the 1 GB target with
         * margin and fires in a fresh process only near its peak.
         */
        const val THRESHOLD_KB = 700L * 1024

        const val MIN_INTERVAL_MS = 10_000L

        const val CHECK_EVERY = 10L

        /** The process-wide policy: captures and main-screen ticks from every thread share one rate limit. */
        val shared = RssGc()

        /**
         * GC, finalizers, GC: the second pass frees what the finalizers released (OpenCV Mats free their memory in finalize).
         * Runtime.gc(), not System.gc(): libcore's System.gc() only collects right after runFinalization().
         */
        private fun collectGarbage() {
            val runtime = Runtime.getRuntime()
            runtime.gc()
            runtime.runFinalization()
            runtime.gc()
        }
    }
}
