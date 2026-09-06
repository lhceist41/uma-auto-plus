package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Persistent Skip pill state observation.
 *
 * [classifyPersistentSkip] is the narrowest testable abstraction here: the template matches it
 * consumes run through OpenCV inside CustomImageUtils, which local unit tests cannot execute
 * (android.jar stubs, no native OpenCV), so asset-to-state recognition itself stays unproven
 * offline and is what the telemetry exists to observe live. What these tests do pin is the
 * part that decides behavior: which recognizer wins, that an unrecognized-but-present pill fails
 * closed to PRESENT_UNRESOLVED rather than NOT_VISIBLE, and that the observer performs no more
 * recognition work than the two call sites performed before it existed.
 */
@DisplayName("Persistent Skip pill state")
class PersistentSkipPillTest {
    /** Counts how often each recognizer was consulted, so short-circuit order is provable. */
    private class Recognizers(val off: Boolean, val on: Boolean, val ocr: Boolean) {
        var offCalls = 0
        var onCalls = 0
        var ocrCalls = 0

        fun classify(): PersistentSkipState =
            classifyPersistentSkip(
                offPillMatched = {
                    offCalls++
                    off
                },
                onPillMatched = {
                    onCalls++
                    on
                },
                skipTextFound = {
                    ocrCalls++
                    ocr
                },
            )
    }

    @Nested
    @DisplayName("classification")
    inner class Classification {
        @Test
        @DisplayName("the skip_off template match reads as OFF")
        fun offTemplate() {
            assertEquals(PersistentSkipState.OFF, Recognizers(off = true, on = false, ocr = false).classify())
        }

        @Test
        @DisplayName("the skip_on template match reads as ON_TEMPLATE_MATCH")
        fun onTemplate() {
            assertEquals(PersistentSkipState.ON_TEMPLATE_MATCH, Recognizers(off = false, on = true, ocr = false).classify())
        }

        @Test
        @DisplayName("a frame with neither pill template nor Skip text reads as NOT_VISIBLE")
        fun negativeFrame() {
            assertEquals(PersistentSkipState.NOT_VISIBLE, Recognizers(off = false, on = false, ocr = false).classify())
        }

        @Test
        @DisplayName("OCR-only presence fails closed to PRESENT_UNRESOLVED, never NOT_VISIBLE")
        fun ocrFallbackStaysPresent() {
            val state = Recognizers(off = false, on = false, ocr = true).classify()
            assertEquals(PersistentSkipState.PRESENT_UNRESOLVED, state)
            assertTrue(state.pillVisible)
        }

        @Test
        @DisplayName("repeat observation of the same frame yields the same state")
        fun deterministic() {
            val recognizers = Recognizers(off = false, on = false, ocr = true)
            assertEquals(recognizers.classify(), recognizers.classify())
        }
    }

    @Nested
    @DisplayName("behavior neutrality")
    inner class BehaviorNeutrality {
        @Test
        @DisplayName("pillVisible reproduces the old boolean for every state")
        fun visibilityMatchesOldBoolean() {
            // Old boolean: skip_off match || skip_on match || OCR found "SKIP".
            for (off in listOf(false, true)) {
                for (on in listOf(false, true)) {
                    for (ocr in listOf(false, true)) {
                        val expected = off || on || ocr
                        val state = Recognizers(off, on, ocr).classify()
                        assertEquals(expected, state.pillVisible, "off=$off on=$on ocr=$ocr")
                    }
                }
            }
        }

        @Test
        @DisplayName("a matched skip_off skips both the skip_on match and the OCR fallback")
        fun offShortCircuits() {
            val recognizers = Recognizers(off = true, on = true, ocr = true)
            recognizers.classify()
            assertEquals(1, recognizers.offCalls)
            assertEquals(0, recognizers.onCalls)
            assertEquals(0, recognizers.ocrCalls)
        }

        @Test
        @DisplayName("the OCR fallback runs only when both templates miss")
        fun ocrRunsOnlyOnTemplateMiss() {
            val matched = Recognizers(off = false, on = true, ocr = true)
            matched.classify()
            assertEquals(0, matched.ocrCalls)

            val missed = Recognizers(off = false, on = false, ocr = true)
            missed.classify()
            assertEquals(1, missed.ocrCalls)
        }

        @Test
        @DisplayName("NOT_VISIBLE is the only state that is not visible")
        fun onlyNotVisibleIsInvisible() {
            assertFalse(PersistentSkipState.NOT_VISIBLE.pillVisible)
            PersistentSkipState.entries
                .filter { it != PersistentSkipState.NOT_VISIBLE }
                .forEach { assertTrue(it.pillVisible, it.name) }
        }
    }
}
