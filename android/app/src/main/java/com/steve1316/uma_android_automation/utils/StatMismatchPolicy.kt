package com.steve1316.uma_android_automation.utils

import kotlin.math.abs

/**
 * Decides what `Trainee.updateStats` does with one freshly read stat value.
 *
 * A reading that differs wildly from the last accepted one is rejected at first, but if the same
 * out-of-range value keeps reading it is eventually trusted, on the theory that the OLD value was
 * the misread. That recovery is worth keeping. What was wrong is how the first such reading was
 * scored: the tracker seeded its "last mismatched value" at -1, so `abs(newValue - (-1))` put every
 * value under about 49 inside the consistency window on arrival. A misread of 7 therefore agreed
 * with a baseline that did not exist, scored a free strike, and the second identical 7 promoted it
 * to trusted (GUTS 684 -> 7, live 2026-07-26 23:42).
 *
 * The rule here is that nothing can corroborate a baseline until a baseline has been observed. The
 * first differing reading only records itself, scoring no strike; strikes accrue from readings that
 * agree with a value actually seen. [STRIKES_TO_PROMOTE] and the positive-value guard are unchanged,
 * so the cost is one extra confirming read before a genuine drift is trusted.
 *
 * Kept free of Android types so the sequences are unit-testable; `updateStats` itself needs a live
 * `CustomImageUtils` and cannot be driven from a unit test.
 */
object StatMismatchPolicy {
    /** A change smaller than this is taken at face value. */
    const val ACCEPT_WINDOW: Int = 150

    /** How close a reading must be to the recorded baseline to corroborate it. */
    const val CONSISTENT_WINDOW: Int = 50

    /** Corroborations required before the new value replaces the old one. */
    const val STRIKES_TO_PROMOTE: Int = 2

    sealed class Decision {
        /**
         * The reading is not a stat value at all: the readers return -1 for "no usable reading",
         * including the plausibility floor's rejection. Keep whatever is held and change nothing.
         *
         * This has to be decided before the accept window, not inside it. abs(-1 - old) is under
         * [ACCEPT_WINDOW] for any held value below 149, so a sentinel used to be taken at face
         * value and written as the stat: live on 2026-07-28 the floor rejected a WIT read of 1
         * against a verified 126, logged "Keeping 126", and the tracker then accepted the -1, so
         * that turn scored with a WIT of -1 and a completion of -0.25%.
         */
        object Discard : Decision()

        /** Take the new value; it is close enough to the old one, or there was no old one. */
        object Accept : Decision()

        /** Keep the old value and remember [value] as the baseline. Scores no strike. */
        data class Baseline(val value: Int) : Decision()

        /** Keep the old value; the baseline now has [strikes] corroborations, not yet enough. */
        data class Hold(val strikes: Int) : Decision()

        /** Take the new value; the baseline has been corroborated [strikes] times. */
        data class Promote(val strikes: Int) : Decision()
    }

    /**
     * @param oldValue the currently held value, <= 0 when the stat has never been read.
     * @param newValue the fresh reading.
     * @param recordedMismatch the baseline from a previous differing reading, or null when none has
     *   been observed since the last accepted value.
     * @param strikes corroborations the baseline has already collected.
     */
    fun decide(oldValue: Int, newValue: Int, recordedMismatch: Int?, strikes: Int): Decision {
        // Real stats are always >= 1, so anything below that is a sentinel, never a reading.
        if (newValue < 1) return Decision.Discard
        if (oldValue <= 0 || abs(newValue - oldValue) < ACCEPT_WINDOW) return Decision.Accept
        if (recordedMismatch == null || abs(newValue - recordedMismatch) >= CONSISTENT_WINDOW) {
            return Decision.Baseline(newValue)
        }
        val next = strikes + 1
        // Sentinels never reach here; Discard above owns them.
        return if (next >= STRIKES_TO_PROMOTE) Decision.Promote(next) else Decision.Hold(next)
    }
}
