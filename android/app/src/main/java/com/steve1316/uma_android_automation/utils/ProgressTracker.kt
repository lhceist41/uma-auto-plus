package com.steve1316.uma_android_automation.utils

import android.graphics.Bitmap
import android.os.SystemClock
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// Detect-only progress measurement. The heartbeat only shows that something called wait(); these
// record when the bot last made real progress and whether the captured frame froze while it acted,
// for the queue ledger. Nothing in the bot reads them back to stop, retry, tap or relaunch.

/** The meaningful events that count as progress, with their ledger key. */
internal enum class ProgressEvent(val key: String) {
    DATE_CHANGE("dateChanges"),
    NAV_NEW_STATE("navNewStates"),
    SCREEN_CHANGE("screenChanges"),
    CAREER_END("careerEnds"),
}

/**
 * The progress measurements of one window: a run, and the navigation that launched it. Not
 * thread-safe on its own; [ProgressTracker] guards it.
 */
internal class ProgressWindow(startMs: Long) {
    companion object {
        /** A gap without progress this long counts as an alive-but-no-progress episode. */
        const val STALL_MS: Long = 5 * 60_000L

        /** Identical samples, each after an action, that make a frozen-frame episode. */
        const val FROZEN_MIN_SAMPLES: Int = 3
    }

    private var lastProgressMs = startMs
    private var longestGapMs = 0L
    private var stalls = 0
    private val events = IntArray(ProgressEvent.entries.size)

    private var frameSamples = 0
    private var lastHash: Long? = null
    private var lastSampleMs = startMs
    private var frozenStartMs = 0L
    private var frozenSamples = 0
    private var frozenEpisodes = 0
    private var longestFrozenMs = 0L

    private fun closeGap(nowMs: Long) {
        val gap = nowMs - lastProgressMs
        if (gap > longestGapMs) longestGapMs = gap
        if (gap >= STALL_MS) stalls++
    }

    fun progress(event: ProgressEvent, nowMs: Long) {
        closeGap(nowMs)
        lastProgressMs = nowMs
        events[event.ordinal]++
    }

    /**
     * One sampled frame. An identical frame after an action extends a frozen run; a different frame
     * ends it; an identical frame with no action since the last sample (the bot idle, waiting)
     * neither extends nor ends it.
     */
    fun frame(hash: Long, actedSinceLastSample: Boolean, nowMs: Long) {
        frameSamples++
        if (hash != lastHash) {
            frozenSamples = 0
        } else if (actedSinceLastSample) {
            if (frozenSamples == 0) frozenStartMs = lastSampleMs
            frozenSamples++
            if (frozenSamples == FROZEN_MIN_SAMPLES) frozenEpisodes++
            if (frozenSamples >= FROZEN_MIN_SAMPLES) longestFrozenMs = maxOf(longestFrozenMs, nowMs - frozenStartMs)
        }
        lastHash = hash
        lastSampleMs = nowMs
    }

    /** Keys and numbers only; the gap still open at [nowMs] counts too. */
    fun snapshot(nowMs: Long): JSONObject {
        val openGap = nowMs - lastProgressMs
        val json =
            JSONObject()
                .put("longestGapMs", maxOf(longestGapMs, openGap))
                .put("stallsOver5Min", stalls + if (openGap >= STALL_MS) 1 else 0)
                .put("frameSamples", frameSamples)
                .put("frozenEpisodes", frozenEpisodes)
                .put("longestFrozenMs", longestFrozenMs)
        ProgressEvent.entries.forEach { json.put(it.key, events[it.ordinal]) }
        return json
    }
}

/** The 32x18 luminance grid sampled from a frame. */
internal const val FRAME_GRID_W = 32
internal const val FRAME_GRID_H = 18

/**
 * A 64-bit FNV-1a hash of the frame's luminance on a [FRAME_GRID_W] x [FRAME_GRID_H] grid of cell
 * centres, read through [pixel] (an ARGB int). Identical frames hash the same; the channel order
 * does not matter for identity.
 */
internal fun frameHash(width: Int, height: Int, pixel: (Int, Int) -> Int): Long {
    var hash = -0x340d631b7bdddcdbL
    for (gy in 0 until FRAME_GRID_H) {
        val y = ((2 * gy + 1) * height) / (2 * FRAME_GRID_H)
        for (gx in 0 until FRAME_GRID_W) {
            val x = ((2 * gx + 1) * width) / (2 * FRAME_GRID_W)
            val c = pixel(x, y)
            val luma = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            hash = (hash xor luma.toLong()) * 0x100000001b3L
        }
    }
    return hash
}

/**
 * The process-wide tracker. Every call is cheap and never throws; its only lock is its own, never
 * MessageLog's or `ocrLock`, and [noteWatchdogRung] takes none at all.
 */
internal object ProgressTracker {
    /** Sample one capture in this many. */
    const val SAMPLE_EVERY: Int = 10

    /** Monotonic, as the watchdog's; replaced only by tests. */
    @Volatile
    internal var clock: () -> Long = { SystemClock.elapsedRealtime() }

    private val lock = Any()
    private var window = ProgressWindow(0L)
    private var actionsAtLastSample = 0L

    @Volatile
    private var lastProgressMs = 0L
    private val actions = AtomicLong()
    private val captures = AtomicInteger()
    private val watchdogRungs = AtomicInteger()
    private val watchdogRungMaxAgeMs = AtomicLong()

    /** Starts a new measurement window (a session start, and after each recorded run). */
    fun beginWindow() {
        synchronized(lock) {
            val now = clock()
            window = ProgressWindow(now)
            lastProgressMs = now
            actionsAtLastSample = actions.get()
        }
        watchdogRungs.set(0)
        watchdogRungMaxAgeMs.set(0L)
    }

    fun noteProgress(event: ProgressEvent) {
        synchronized(lock) {
            val now = clock()
            window.progress(event, now)
            lastProgressMs = now
        }
    }

    /** The bot issued a tap. */
    fun noteAction() {
        actions.incrementAndGet()
    }

    /** Samples every [SAMPLE_EVERY]th capture: a grid of pixel reads and a hash, never blocking the capture for long. */
    fun noteCapture(bitmap: Bitmap) {
        if (captures.incrementAndGet() % SAMPLE_EVERY != 0) return
        val hash =
            try {
                frameHash(bitmap.width, bitmap.height) { x, y -> bitmap.getPixel(x, y) }
            } catch (_: Throwable) {
                return
            }
        noteFrame(hash)
    }

    internal fun noteFrame(hash: Long) {
        synchronized(lock) {
            val acted = actions.get()
            window.frame(hash, acted != actionsAtLastSample, clock())
            actionsAtLastSample = acted
        }
    }

    /** A watchdog rung acted: records how long progress had been missing. Lock-free, for the watchdog's thread. */
    fun noteWatchdogRung() {
        val age = clock() - lastProgressMs
        watchdogRungs.incrementAndGet()
        watchdogRungMaxAgeMs.accumulateAndGet(age) { a, b -> maxOf(a, b) }
    }

    /** The window's ledger fields, keys and numbers only; a new window starts. */
    fun endWindow(): JSONObject {
        val json = synchronized(lock) { window.snapshot(clock()) }
        json.put("watchdogRungs", watchdogRungs.get()).put("watchdogRungMaxAgeMs", watchdogRungMaxAgeMs.get())
        beginWindow()
        return json
    }
}
