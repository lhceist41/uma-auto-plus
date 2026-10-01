package com.steve1316.uma_android_automation.bot

import android.graphics.Bitmap
import android.util.Log
import com.steve1316.uma_android_automation.BuildConfig
import com.steve1316.uma_android_automation.types.StatName
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dev-only, read-only: saves each facility's training analysis frame for offline measurement. Runs
 * only in a debug build or with Debug Mode on. Logs through [android.util.Log], never `MessageLog`,
 * whose process-wide lock can deadlock anything else waiting to log (watchdog recovery included).
 * A capture failure is swallowed: instrumentation must never break a run.
 */
object GrandConcertTelemetry {
    private const val LOG_TAG = "GCTelemetry"

    /** Monotonic capture sequence so the saved frames sort in analysis order within one process. */
    private val sequence = AtomicInteger(0)

    internal fun enabled(debugBuild: Boolean, debugMode: Boolean): Boolean = debugBuild || debugMode

    /** Grand Concert keeps its `gc_train_` prefix, which the offline measurement scripts read. */
    internal fun frameName(scenario: String, sequence: Int, facility: StatName): String {
        val prefix = if (scenario == GrandConcertScenario.KEY) "gc" else scenario.lowercase().filter { it.isLetterOrDigit() }
        return "%s_train_%04d_%s".format(prefix, sequence, facility.name)
    }

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
