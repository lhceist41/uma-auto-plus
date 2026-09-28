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

    /**
     * The in-career Off-to-fast fix. The states stand for the measured frames: an Off pill reads OFF
     * on the main screen, cutscenes and event choices (skip_off 0.890 to 0.988); after the taps a fast
     * pill reads ON_TEMPLATE_MATCH on event screens but PRESENT_UNRESOLVED on the main screen
     * (skip_on 0.508 to 0.755 there); a Race Day screen shows no pill.
     */
    @Nested
    @DisplayName("in-career Off to fast")
    inner class InCareerFix {
        private inner class Run(val after: PersistentSkipState) {
            var tapRounds = 0
            var reads = 0

            fun on(
                fix: InCareerSkipFix,
                state: PersistentSkipState,
            ): SkipFixOutcome =
                fix.attempt(state, tapPillTwice = { tapRounds++ }, reRead = {
                    reads++
                    after
                })
        }

        @Test
        @DisplayName("Off on an event screen: two taps, one re-read, and the fast pill ends it")
        fun offOnEventScreen() {
            val run = Run(after = PersistentSkipState.ON_TEMPLATE_MATCH)
            assertEquals(SkipFixOutcome.LEFT_OFF, run.on(InCareerSkipFix(), PersistentSkipState.OFF))
            assertEquals(1, run.tapRounds)
            assertEquals(1, run.reads)
        }

        @Test
        @DisplayName("Off on the main screen: a fast pill that reads unresolved still counts as fixed")
        fun offOnMainScreen() {
            val fix = InCareerSkipFix()
            assertEquals(SkipFixOutcome.LEFT_OFF, Run(after = PersistentSkipState.PRESENT_UNRESOLVED).on(fix, PersistentSkipState.OFF))
            assertFalse(fix.gaveUp)
        }

        @Test
        @DisplayName("an on, unresolved or missing pill is never tapped")
        fun notOffNeverTapped() {
            for (state in listOf(PersistentSkipState.ON_TEMPLATE_MATCH, PersistentSkipState.PRESENT_UNRESOLVED, PersistentSkipState.NOT_VISIBLE)) {
                val run = Run(after = PersistentSkipState.OFF)
                assertEquals(SkipFixOutcome.NOT_OFF, run.on(InCareerSkipFix(), state), state.name)
                assertEquals(0, run.tapRounds, state.name)
                assertEquals(0, run.reads, state.name)
            }
        }

        @Test
        @DisplayName("still Off after the taps: give up once, and never tap again this career")
        fun stillOffGivesUpOnce() {
            val fix = InCareerSkipFix()
            val first = Run(after = PersistentSkipState.OFF)
            assertEquals(SkipFixOutcome.GIVING_UP, first.on(fix, PersistentSkipState.OFF))
            assertTrue(fix.gaveUp)
            val later = Run(after = PersistentSkipState.ON_TEMPLATE_MATCH)
            assertEquals(SkipFixOutcome.GAVE_UP_EARLIER, later.on(fix, PersistentSkipState.OFF))
            assertEquals(0, later.tapRounds)
            assertEquals(0, later.reads)
        }

        @Test
        @DisplayName("a pill gone after the taps is not taken as fixed")
        fun goneIsNotFixed() {
            assertEquals(SkipFixOutcome.GIVING_UP, Run(after = PersistentSkipState.NOT_VISIBLE).on(InCareerSkipFix(), PersistentSkipState.OFF))
        }

        @Test
        @DisplayName("a fixed pill reset again later is fixed again; a new career starts fresh")
        fun laterResetAndNewCareer() {
            val fix = InCareerSkipFix()
            assertEquals(SkipFixOutcome.LEFT_OFF, Run(after = PersistentSkipState.ON_TEMPLATE_MATCH).on(fix, PersistentSkipState.OFF))
            assertEquals(SkipFixOutcome.LEFT_OFF, Run(after = PersistentSkipState.ON_TEMPLATE_MATCH).on(fix, PersistentSkipState.OFF))
            Run(after = PersistentSkipState.OFF).on(fix, PersistentSkipState.OFF)
            assertEquals(SkipFixOutcome.LEFT_OFF, Run(after = PersistentSkipState.ON_TEMPLATE_MATCH).on(InCareerSkipFix(), PersistentSkipState.OFF))
        }
    }

    /** The Campaign wiring, guarded on source (the calls need a live screen). */
    @Nested
    @DisplayName("Campaign wiring")
    inner class Wiring {
        private val campaign by lazy {
            val rel = "android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt"
            var dir: java.io.File? = java.io.File("").absoluteFile
            while (dir != null && !java.io.File(dir, rel).exists()) dir = dir.parentFile
            java.io.File(dir, rel).readText().replace("\r\n", "\n")
        }

        @Test
        @DisplayName("the main screen checks the pill once per turn, before the scenario hook")
        fun mainScreen() {
            assertTrue(campaign.contains("        setFastSkipIfOff(\"main screen\")\n\n        // Scenario-specific main screen entry hook (e.g. for item usage).\n        onMainScreenEntry()"))
        }

        @Test
        @DisplayName("the cutscene handler checks it before the body tap, never inside the rebind ladder")
        fun cutscene() {
            val call = "if (count < cutsceneRebindThresholds.min()) setFastSkipIfOff(\"event screen\")"
            val i = campaign.indexOf(call)
            assertTrue(i >= 0)
            assertTrue(campaign.indexOf("game.tap(540.0, 1300.0, taps = 1)", i) > i)
        }

        @Test
        @DisplayName("it reads Off by template or colour, taps the pill centre, and the fix is one per career")
        fun tapsThePillCentre() {
            assertTrue(campaign.contains("if (!skipOffPill(bitmap)) return"))
            assertTrue(campaign.contains("game.tapCoordinate(bitmap.width * SKIP_PILL_CENTRE_X_FRACTION, bitmap.height * SKIP_PILL_CENTRE_Y_FRACTION, \"skip_pill\")"))
            assertTrue(campaign.contains("    private val skipFix = InCareerSkipFix()"))
            assertTrue(
                campaign.contains(
                    "ButtonSkipOff.check(game.imageUtils, sourceBitmap = bitmap) ||\n            skipOffPillByColour(SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }, bitmap.width, bitmap.height)",
                ),
            )
            assertEquals(1, Regex("classifyPersistentSkip\\(").findAll(campaign).count(), "every Campaign pill read goes through readSkipPill")
        }

        @Test
        @DisplayName("the pill is tapped exactly twice (Off to > to >>; a third tap would cycle it back to Off)")
        fun twoTaps() {
            val start = campaign.indexOf("    private fun setFastSkipIfOff(")
            val body = campaign.substring(start, campaign.indexOf("\n    }\n", start))
            assertEquals(1, Regex("repeat\\(2\\) \\{").findAll(body).count())
            assertEquals(1, Regex("game\\.tapCoordinate\\(").findAll(body).count(), "one tap inside the repeat, none outside it")
            assertEquals(0, Regex("game\\.tap\\(").findAll(body).count())

            val rel = "android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt"
            var dir: java.io.File? = java.io.File("").absoluteFile
            while (dir != null && !java.io.File(dir, rel).exists()) dir = dir.parentFile
            val nav = java.io.File(dir, rel).readText().replace("\r\n", "\n")
            val handler = nav.substring(nav.indexOf("private fun handleQuickModePrompt("))
            val taps = handler.substring(handler.indexOf("tapPillTwice = {"), handler.indexOf("reRead = "))
            assertEquals(2, Regex("CoordinateTap\\.tap\\(").findAll(taps).count())
            assertFalse(taps.contains("repeat("))
        }

        @Test
        @DisplayName("the launch navigator reads Off the same way at detection and at the prompt")
        fun navigator() {
            val rel = "android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt"
            var dir: java.io.File? = java.io.File("").absoluteFile
            while (dir != null && !java.io.File(dir, rel).exists()) dir = dir.parentFile
            val nav = java.io.File(dir, rel).readText().replace("\r\n", "\n")
            assertEquals(1, Regex("classifyPersistentSkip\\(").findAll(nav).count(), "every navigator pill read goes through readSkipPill")
            assertTrue(nav.contains("skipOffPillByColour(SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }, bitmap.width, bitmap.height)"))
            assertTrue(nav.contains("reRead = { readSkipPill(iu.getSourceBitmap()) },"), "the launch taps are verified")
        }
    }

    /**
     * The colour reading of a white "Skip Off" pill on real frames: crops of the pill band
     * (x 203 to 565, y 1771 to 1920 of a 1080x1920 capture), including the light launch backgrounds
     * where the `skip_off` template scores only about 0.62.
     */
    @Nested
    @DisplayName("Skip Off by colour, on real frames")
    inner class ByColour {
        private fun sampler(name: String): SparkPixelSampler {
            val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/skippill/$name.png")) { "missing fixture $name" }
            val crop = stream.use { FixturePng.read(it) }
            return SparkPixelSampler { x, y -> crop.getRGB(x - 203, y - 1771) }
        }

        private fun read(name: String): Boolean = skipOffPillByColour(sampler(name), 1080, 1920)

        @Test
        @DisplayName("every Off pill reads Off, including over the light launch backgrounds")
        fun offPills() {
            for (name in listOf("off_launch_light", "off_launch_light_2", "off_quick_mode_prompt", "off_main", "off_cutscene", "off_event_choice")) {
                assertTrue(read(name), name)
            }
        }

        @Test
        @DisplayName("a > or >> pill never reads Off")
        fun chevronPills() {
            for (name in listOf("on_event_one", "on_event_two", "on_main_one", "on_main_two")) assertFalse(read(name), name)
        }

        @Test
        @DisplayName("no pill, a blank white screen or a pill still fading in never reads Off")
        fun noPill() {
            for (name in listOf("none_blank_white", "none_cinematic", "none_fading_in")) assertFalse(read(name), name)
        }

        @Test
        @DisplayName("another capture size is not read at all, even over an Off pill")
        fun otherSize() {
            assertFalse(skipOffPillByColour(sampler("off_main"), 1080, 1921))
            assertFalse(skipOffPillByColour(sampler("off_main"), 1081, 1920))
        }

        @Test
        @DisplayName("brown lettering colours without the white fill are not a pill")
        fun letteringAloneIsNotAPill() {
            assertFalse(skipOffPillByColour(SparkPixelSampler { _, _ -> 0xFF7A3C14.toInt() }, 1080, 1920))
        }
    }
}
