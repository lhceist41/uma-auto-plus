package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Final Confirmation mode-tab pixel probes on the promoted Final Confirmation crops (see
 * src/test/resources/fixtures/finalconfirmation/PROVENANCE.md) and, as cross-screen negatives, on the
 * existing Grand Concert full-screen fixtures. Pins that Normal Career and Independent Training are each
 * positively classified, that the two-tab recognizer accepts both modes, and that the probe REFUSES
 * screens it has no two-tab evidence for.
 */
@DisplayName("Final Confirmation mode-tab pixel probes")
class FinalConfirmationProbesFixtureTest {
    private val cache = mutableMapOf<String, FixturePng>()

    private fun image(path: String): FixturePng =
        cache.getOrPut(path) {
            val stream = requireNotNull(javaClass.getResourceAsStream(path)) { "missing fixture $path" }
            stream.use { FixturePng.read(it) }
        }

    private fun modeSampler(name: String): SparkPixelSampler {
        val img = image("/fixtures/finalconfirmation/$name.png")
        return SparkPixelSampler { x, y -> img.getRGB(x, y) }
    }

    private fun grandConcertSampler(name: String): SparkPixelSampler {
        val img = image("/fixtures/grandconcert/$name.png")
        return SparkPixelSampler { x, y -> img.getRGB(x, y) }
    }

    // Synthetic samplers for the malformed / ambiguous cases that cannot be photographed cleanly.
    private val greenArgb = (0xFF shl 24) or (137 shl 16) or (210 shl 8) or 8
    private val whiteArgb = (0xFF shl 24) or (246 shl 16) or (245 shl 8) or 249

    private fun uniform(argb: Int) = SparkPixelSampler { _, _ -> argb }

    private fun tabs(left: Int, right: Int) =
        SparkPixelSampler { x, _ ->
            if (x < FinalConfirmationTabGeometry.INDEPENDENT_TAB_SAMPLE_X - FinalConfirmationTabGeometry.SAMPLE_HALF) left else right
        }

    @Test
    fun `the promoted crops are the expected 1080x360 top strip`() {
        for (name in listOf("normal_career", "independent_training")) {
            val img = image("/fixtures/finalconfirmation/$name.png")
            assertEquals(1080, img.width, name)
            assertEquals(360, img.height, name)
        }
    }

    // --- test 1: Normal Career fixture -> NORMAL_CAREER_VERIFIED -------------------------------------
    @Test
    fun `normal career fixture classifies as NORMAL_CAREER_VERIFIED`() {
        assertEquals(FinalConfirmationMode.NORMAL_CAREER_VERIFIED, classifyFinalConfirmationMode(modeSampler("normal_career")))
    }

    // --- test 2: Independent Training fixture -> INDEPENDENT_TRAINING_VERIFIED -----------------------
    @Test
    fun `independent training fixture classifies as INDEPENDENT_TRAINING_VERIFIED`() {
        assertEquals(
            FinalConfirmationMode.INDEPENDENT_TRAINING_VERIFIED,
            classifyFinalConfirmationMode(modeSampler("independent_training")),
        )
    }

    // --- test 16 (evidence list): the discriminator green fractions are pinned ----------------------
    @Test
    fun `the tab green fractions are fully separated on both fixtures`() {
        val normal = modeSampler("normal_career")
        val indep = modeSampler("independent_training")
        // Normal Career: left green, right white.
        assertEquals(
            1.0,
            finalConfirmationTabGreenFraction(normal, FinalConfirmationTabGeometry.NORMAL_TAB_SAMPLE_X, FinalConfirmationTabGeometry.NORMAL_TAB_SAMPLE_Y),
            1e-9,
        )
        assertEquals(
            0.0,
            finalConfirmationTabGreenFraction(normal, FinalConfirmationTabGeometry.INDEPENDENT_TAB_SAMPLE_X, FinalConfirmationTabGeometry.INDEPENDENT_TAB_SAMPLE_Y),
            1e-9,
        )
        // Independent Training: mirror image.
        assertEquals(
            0.0,
            finalConfirmationTabGreenFraction(indep, FinalConfirmationTabGeometry.NORMAL_TAB_SAMPLE_X, FinalConfirmationTabGeometry.NORMAL_TAB_SAMPLE_Y),
            1e-9,
        )
        assertEquals(
            1.0,
            finalConfirmationTabGreenFraction(indep, FinalConfirmationTabGeometry.INDEPENDENT_TAB_SAMPLE_X, FinalConfirmationTabGeometry.INDEPENDENT_TAB_SAMPLE_Y),
            1e-9,
        )
    }

    // --- test 4: both tabs green -> MODE_UNRECOGNIZED ------------------------------------------------
    @Test
    fun `both tabs green is MODE_UNRECOGNIZED`() {
        assertEquals(FinalConfirmationMode.MODE_UNRECOGNIZED, classifyFinalConfirmationMode(uniform(greenArgb)))
        assertEquals(FinalConfirmationMode.MODE_UNRECOGNIZED, classifyFinalConfirmationMode(tabs(greenArgb, greenArgb)))
    }

    // --- test 5: neither tab green -> MODE_UNRECOGNIZED ----------------------------------------------
    @Test
    fun `neither tab green is MODE_UNRECOGNIZED`() {
        assertEquals(FinalConfirmationMode.MODE_UNRECOGNIZED, classifyFinalConfirmationMode(uniform(whiteArgb)))
        assertEquals(FinalConfirmationMode.MODE_UNRECOGNIZED, classifyFinalConfirmationMode(tabs(whiteArgb, whiteArgb)))
    }

    // --- test 3: ambiguous / malformed colours -> MODE_UNRECOGNIZED ----------------------------------
    @Test
    fun `ambiguous mid colours are MODE_UNRECOGNIZED`() {
        // A dim grey-green that never clears the dominance margin on either tab.
        val murky = (0xFF shl 24) or (90 shl 16) or (110 shl 8) or 95
        assertEquals(FinalConfirmationMode.MODE_UNRECOGNIZED, classifyFinalConfirmationMode(uniform(murky)))
        assertEquals(FinalConfirmationMode.MODE_UNRECOGNIZED, classifyFinalConfirmationMode(tabs(murky, whiteArgb)))
    }

    // --- test 7 + Normal recognition: the two-tab recognizer accepts BOTH real modes ----------------
    @Test
    fun `finalConfirmationScreenPresent is true on both real modes`() {
        assertTrue(finalConfirmationScreenPresent(modeSampler("normal_career")))
        assertTrue(finalConfirmationScreenPresent(modeSampler("independent_training")))
    }

    @Test
    fun `finalConfirmationScreenPresent is false when no single tab is selected`() {
        assertFalse(finalConfirmationScreenPresent(uniform(whiteArgb)))
        assertFalse(finalConfirmationScreenPresent(uniform(greenArgb)))
    }

    // --- cross-screen negatives: the probe refuses in-career screens that are not this control ------
    // These include screens that ARE green-dominant at a tab sample point (the concert result banner,
    // the training screens) but are not the two-tab pill strip -- the green+white signature rejects them.
    @Test
    fun `non-confirmation grand concert screens are not misclassified as a career mode`() {
        for (name in listOf(
            "career_main_turn1",
            "concert_pending",
            "career_complete",
            "concert_success_banner",
            "training_guts_before",
            "training_panel_hidden",
        )) {
            val sampler = grandConcertSampler(name)
            assertEquals(
                FinalConfirmationMode.MODE_UNRECOGNIZED,
                classifyFinalConfirmationMode(sampler),
                "grandconcert/$name should not read as a career-mode tab",
            )
            assertFalse(finalConfirmationScreenPresent(sampler), "grandconcert/$name should not be a mode screen")
        }
    }

    // A Grand Concert career launch is a Normal Career: its Final Confirmation carries the same tab
    // strip with Normal Career selected, so the probe correctly reads it as NORMAL_CAREER_VERIFIED.
    @Test
    fun `a grand concert final confirmation reads as NORMAL_CAREER_VERIFIED`() {
        assertEquals(
            FinalConfirmationMode.NORMAL_CAREER_VERIFIED,
            classifyFinalConfirmationMode(grandConcertSampler("final_confirmation")),
        )
    }
}
