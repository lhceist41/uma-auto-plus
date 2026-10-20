package com.steve1316.uma_android_automation.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Some devices draw text labels 4 to 12 percent smaller than 1080x1920 while every sprite stays at 1.0 (MuMu at 480 DPI;
 * a 1080x2408 phone at 480 DPI drew the stat table's "Skill Pts" at 0.92). After one of [LABELS] misses at 1.0, the
 * same frame is searched at [SCALES], same confidence. Per run it remembers what each label needed, so a device that
 * draws a label at full size never pays for the retries again.
 */
class TextScaleFallback {
    private val fullSize = ConcurrentHashMap.newKeySet<String>()
    private val learned = ConcurrentHashMap<String, Double>()
    private val misses = ConcurrentHashMap<String, AtomicInteger>()
    private val firstFound = AtomicBoolean(false)

    /** A hit below full size; [firstOfRun] marks the run's first, which is logged. */
    class Hit<T>(val scale: Double, val value: T, val firstOfRun: Boolean)

    /** [template] matched at 1.0 this run: its later misses are real absences. */
    fun matchedAtFullSize(template: String) {
        if (template in LABELS) fullSize.add(template)
    }

    /** Scales to try after [template] missed at 1.0; empty when it gets no retry. */
    internal fun scalesAfterMiss(template: String): List<Double> {
        if (template !in LABELS || template in fullSize) return emptyList()
        learned[template]?.let { return listOf(it) }
        if ((misses[template]?.get() ?: 0) >= MAX_FULL_MISSES) return emptyList()
        return SCALES
    }

    /** After [template] missed at 1.0: the first of its scales [attempt] matches at, or null. A label never seen stops after [MAX_FULL_MISSES] misses. */
    fun <T : Any> afterMiss(
        template: String,
        attempt: (scale: Double) -> T?,
    ): Hit<T>? {
        val scales = scalesAfterMiss(template)
        for (scale in scales) {
            val value = attempt(scale) ?: continue
            learned[template] = scale
            return Hit(scale, value, firstFound.compareAndSet(false, true))
        }
        if (scales.isNotEmpty() && !learned.containsKey(template)) misses.getOrPut(template) { AtomicInteger(0) }.incrementAndGet()
        return null
    }

    companion object {
        /**
         * Read anchors whose callers only look on the screen the label belongs to, so a smaller-scale hit elsewhere is never
         * asked for. A false hit is possible on other screens: "Standard Distance" in the Sparks keep confirmation matches
         * `stat_distance` at 0.96, which stays safe only while its one caller is the Details dialog. Labels behind
         * launch, borrow, scenario, shop, Club, dailies, race criteria, loading, popup dismissals, the career-end skill
         * screen, and single-letter or tiny templates stay at 1.0 only.
         */
        val LABELS: Set<String> =
            setOf(
                "components/label/stat_table_header_skill_points",
                "components/label/stat_track_surface",
                "components/label/stat_distance",
                "components/label/stat_style",
                "components/label/training_failure_chance",
                "components/label/energy",
            )

        val SCALES: List<Double> = listOf(0.96, 0.92, 0.88)

        const val MAX_FULL_MISSES = 3
    }
}
