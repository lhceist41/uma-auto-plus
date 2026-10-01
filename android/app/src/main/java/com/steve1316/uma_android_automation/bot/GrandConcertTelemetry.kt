package com.steve1316.uma_android_automation.bot

import android.graphics.Bitmap
import android.util.Log
import com.steve1316.uma_android_automation.BuildConfig
import com.steve1316.uma_android_automation.types.StatName
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dev-only, read-only instrumentation: saves each facility's training analysis frame for offline
 * measurement.
 *
 * Grand Concert frames (`gc_train_*`) measure the performance-point income and the stat-gain reads;
 * the other scenarios' frames (`urafinale_train_*`, `unitycup_train_*`, `trackblazer_train_*`) let the
 * stat-gain reader be checked against their layouts, which no saved capture covered before. The frame
 * shows the per-type "+N" gains and the support portraits. It changes no decision and taps nothing.
 *
 * The gating is layered on purpose: it fires only in a debug build or with Debug Mode enabled, and it
 * logs through [android.util.Log] - never `MessageLog`, whose single process-wide lock holds across
 * both the buffer append and its EventBus post, so a subscriber doing blocking work while that lock is
 * held can deadlock or freeze anything else waiting to log, watchdog recovery included. A capture
 * failure is swallowed and logged: instrumentation must never be able to break a run.
 */
object GrandConcertTelemetry {
    private const val LOG_TAG = "GCTelemetry"

    /** Monotonic capture sequence so the saved frames sort in analysis order within one process. */
    private val sequence = AtomicInteger(0)

    /** True when the capture is allowed: a debug build or Debug Mode, in any scenario. Kept cheap so the
     * hot training loop pays almost nothing when it is off (the common case). */
    internal fun enabled(debugBuild: Boolean, debugMode: Boolean): Boolean = debugBuild || debugMode

    /** Grand Concert keeps its `gc_train_` prefix, which the offline measurement scripts read; other
     * scenarios use their name, lowercased with spaces removed. */
    internal fun frameName(scenario: String, sequence: Int, facility: StatName): String {
        val prefix = if (scenario == GrandConcertScenario.KEY) "gc" else scenario.lowercase().filter { it.isLetterOrDigit() }
        return "%s_train_%04d_%s".format(prefix, sequence, facility.name)
    }

    /**
     * Persists the currently selected facility's analysis frame for later offline measurement. Called
     * once per facility per turn, immediately after the facility is selected and its analysis
     * screenshot is taken, so the "+N" gains and the support portraits are on screen. [bitmap] is the
     * exact frame the analyzer used, so no extra screenshot is taken.
     */
    fun captureTrainingFacility(game: Game, facility: StatName, bitmap: Bitmap) {
        if (!enabled(BuildConfig.DEBUG, game.debugMode)) return
        val name = frameName(game.scenario, sequence.incrementAndGet(), facility)
        try {
            game.imageUtils.saveBitmap(bitmap = bitmap, filename = name, fullRes = true)
            Log.d(LOG_TAG, "[GC_TELEMETRY] saved=$name facility=${facility.name}")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "[GC_TELEMETRY] capture failed for ${facility.name}: ${e.message}")
        }
    }
}
