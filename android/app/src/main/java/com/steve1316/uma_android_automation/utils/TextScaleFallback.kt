package com.steve1316.uma_android_automation.utils

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Some devices draw text labels 4 to 12 percent smaller than 1080x1920 while every sprite stays at 1.0 (MuMu at 480 DPI;
 * a 1080x2408 phone at 480 DPI drew the stat table's "Skill Pts" at 0.92). After one of [LABELS] misses at 1.0, the
 * same frame is searched at [SCALES], same confidence. Per run it remembers what each label needed, so a device that
 * draws a label at full size never pays for the retries again.
 *
 * The shrink differs per text element on the same screen (on that phone "Energy" stays at 1.0), so one scale cannot be
 * applied to all text. Instead [textShrinks], measured at Start, decides whether this device draws text smaller at all;
 * only then [ALLOWED] gets the finer [LADDER] with the stricter [LADDER_CONFIDENCE].
 */
class TextScaleFallback {
    private val fullSize = ConcurrentHashMap.newKeySet<String>()
    private val learned = ConcurrentHashMap<String, Double>()
    private val misses = ConcurrentHashMap<String, AtomicInteger>()
    private val firstFound = AtomicBoolean(false)

    /** A hit below full size; [firstOfRun] marks the run's first, which is logged; [opensGate] marks a label hit that turned [textShrinks] on. */
    class Hit<T>(val scale: Double, val value: T, val firstOfRun: Boolean, val opensGate: Boolean)

    /** [template] matched at 1.0 this run: its later misses are real absences. */
    fun matchedAtFullSize(template: String) {
        if (template in ALLOWED) fullSize.add(template)
    }

    /** Scales to try after [template] missed at 1.0; empty when it gets no retry. */
    internal fun scalesAfterMiss(
        template: String,
        shrinks: Boolean = textShrinks == true,
    ): List<Double> {
        if (template !in (if (shrinks) ALLOWED else LABELS) || template in fullSize) return emptyList()
        learned[template]?.let { return listOf(it) }
        if ((misses[template]?.get() ?: 0) >= MAX_FULL_MISSES) return emptyList()
        return if (shrinks) LADDER else SCALES
    }

    /**
     * After [template] missed at 1.0: the first of its scales [attempt] matches at, or null. [attempt] gets the minimum
     * confidence for the retry: null keeps the caller's, as before the gate. A label never seen stops after [MAX_FULL_MISSES] misses.
     */
    fun <T : Any> afterMiss(
        template: String,
        attempt: (scale: Double, minConfidence: Double?) -> T?,
    ): Hit<T>? {
        val shrinks = textShrinks == true
        val scales = scalesAfterMiss(template, shrinks)
        val minConfidence = if (shrinks) LADDER_CONFIDENCE else null
        for (scale in scales) {
            val value = attempt(scale, minConfidence) ?: continue
            learned[template] = scale
            // A label found smaller after Start on a screen without an anchor is the same evidence the Start measurement looks for.
            val opensGate = !shrinks && gate.compareAndSet(null, true)
            return Hit(scale, value, firstFound.compareAndSet(false, true), opensGate)
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

        /**
         * Text templates retried on [LADDER] once [textShrinks] is true. On captured MuMu 240 DPI and 1080x2316 frames none
         * of them scores 0.80 or more at any ladder scale where 1.0 missed. Families with near-identical variants (selected
         * and unselected menu tabs, strategy, training, the Special Missions tabs, yes and no) stay out: scaled, one matches
         * where it is absent (`special_missions_tab_daily` scored 0.96 at 0.92 on a shop screen).
         */
        val ALLOWED: Set<String> =
            LABELS +
                setOf(
                    "components/button/auto_select",
                    "components/button/details",
                    "components/button/back",
                    "components/button/cancel",
                    "components/button/close",
                    "components/button/close_wide",
                    "components/button/home_full_stats",
                    "components/button/skill_list_full_stats",
                    "components/button/home_fans_info",
                )

        val LADDER: List<Double> = listOf(0.98, 0.96, 0.94, 0.92, 0.90)

        /** Every real scaled hit measured scored 0.879 or more at its first ladder step; absent text reached 0.84 on 240 DPI frames. */
        const val LADDER_CONFIDENCE = 0.85

        /** A best scale must beat 1.0 by this much to count as smaller text, not noise. */
        const val MIN_GAIN = 0.03

        val GATE_SCALES: List<Double> = listOf(1.0) + LADDER

        /** Start-frame anchors in the order they are tried, with the name the log gives them. */
        val ANCHORS: List<Pair<String, String>> =
            listOf(
                "components/label/home_menu" to "\"Menu\" (Home)",
                "components/label/stat_table_header_skill_points" to "\"Skill Pts\" (career Main)",
            )

        const val NOT_MEASURED = "[LAYOUT] Text scale not measured on this screen; label retries as before."

        private val gate = AtomicReference<Boolean?>(null)

        /** Whether this device draws text smaller than 1080x1920: null until measured, for every run of this process. */
        val textShrinks: Boolean? get() = gate.get()

        /** Clears the measurement at the start of a session: the density can change between sessions. */
        fun resetGate() = gate.set(null)

        /**
         * One anchor's verdict from its best score at each of [GATE_SCALES]: null when it is not on screen (below [confidence]
         * at 1.0 and below [LADDER_CONFIDENCE] on the ladder), true when its best scale is 0.98 or less and beats 1.0 by
         * [MIN_GAIN], false otherwise.
         */
        internal fun anchorShrinks(
            scores: Map<Double, Double>,
            confidence: Double,
        ): Boolean? {
            val atFullSize = scores[1.0] ?: return null
            if (atFullSize < confidence && scores.none { (scale, score) -> scale < 1.0 && score >= LADDER_CONFIDENCE }) return null
            val best = scores.maxBy { it.value }
            return best.key <= 0.98 && best.value - atFullSize >= MIN_GAIN
        }

        /** Sets [textShrinks] from the first [ANCHORS] entry on screen ([scores] gives null when it cannot score one) and returns the log line. */
        fun measureAtStart(
            confidence: Double,
            scores: (template: String) -> Map<Double, Double>?,
        ): String {
            for ((template, name) in ANCHORS) {
                val anchorScores = scores(template) ?: continue
                val shrinks = anchorShrinks(anchorScores, confidence) ?: continue
                gate.set(shrinks)
                val scale = String.format(Locale.ROOT, "%.2f", anchorScores.maxBy { it.value }.key)
                return if (shrinks) {
                    "[LAYOUT] Text scale $scale from $name: text draws smaller here, scaled retries on for ${ALLOWED.size} text templates."
                } else {
                    "[LAYOUT] Text scale $scale from $name: no scaled retries."
                }
            }
            gate.set(null)
            return NOT_MEASURED
        }
    }
}
