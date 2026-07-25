package com.steve1316.uma_android_automation.bot

import android.graphics.Bitmap
import android.util.Log
import com.steve1316.uma_android_automation.BuildConfig
import com.steve1316.uma_android_automation.types.StatName
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dev-only, read-only instrumentation for a supervised Grand Concert exploration career.
 *
 * Grand Concert adds a fifth training output - performance points - whose income formula is not yet
 * settled: two competing INFERRED models are carried in [GrandConcertResearchVerdict], and the only
 * captured multi-support figure floors to the same value on both. The cheapest way to settle it is
 * to watch a real career. This saves each facility's analysis frame, on which the per-type "+N"
 * performance gain and the support portraits are both visible, so the (support count, gain) pairs
 * can be read off afterward and the two models compared. It changes no decision and taps nothing.
 *
 * The gating is layered on purpose: it fires only on a Grand Concert career, only in a debug build
 * or with Debug Mode enabled, and it logs through [android.util.Log] - never `MessageLog`, whose
 * single process-wide lock is the watchdog-deadlock hazard documented in docs/INCIDENTS.md. A capture
 * failure is swallowed and logged: instrumentation must never be able to break a run.
 */
object GrandConcertTelemetry {
    private const val LOG_TAG = "GCTelemetry"

    /** Monotonic capture sequence so the saved frames sort in analysis order within one process. */
    private val sequence = AtomicInteger(0)

    /** True when the exploration capture is allowed. Kept cheap so the hot training loop pays almost
     * nothing when it is off (the common case). */
    private fun enabled(game: Game): Boolean =
        game.scenario == GrandConcertScenario.KEY && (BuildConfig.DEBUG || game.debugMode)

    /**
     * Persists the currently selected facility's analysis frame for later offline measurement of the
     * performance-point economy. Called once per facility per turn, immediately after the facility is
     * selected and its analysis screenshot is taken, so the "+N" gain and the support portraits are
     * on screen. [bitmap] is the exact frame the analyzer used, so no extra screenshot is taken.
     */
    fun captureTrainingFacility(game: Game, facility: StatName, bitmap: Bitmap) {
        if (!enabled(game)) return
        val name = "gc_train_%04d_%s".format(sequence.incrementAndGet(), facility.name)
        try {
            game.imageUtils.saveBitmap(bitmap = bitmap, filename = name, fullRes = true)
            Log.d(LOG_TAG, "[GC_TELEMETRY] saved=$name facility=${facility.name}")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "[GC_TELEMETRY] capture failed for ${facility.name}: ${e.message}")
        }
    }
}
