package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pins [PostCareerScreenProbes] against the live 2026-08-19 capture of the event-period "REWARDS"
 * points summary (src/test/resources/fixtures/postcareer, see PROVENANCE.md) plus a spread of the
 * spark-flow fixtures as negatives. No emulator, no OpenCV, no OCR: the PNG is decoded by
 * [FixturePng] and read through the same [SparkPixelSampler] the runtime wraps a Bitmap in, so the
 * probe that passes here is the probe that runs live.
 */
@DisplayName("Post-career event-rewards summary pixel probe")
class PostCareerScreenProbeFixtureTest {
    private fun image(dir: String, name: String): FixturePng {
        val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/$dir/$name.png")) { "missing fixture $dir/$name.png" }
        return stream.use { FixturePng.read(it) }
    }

    private fun matches(dir: String, name: String): Boolean {
        val img = image(dir, name)
        return PostCareerScreenProbes.isEventPointsRewardsSummary(
            SparkPixelSampler { x, y -> img.getRGB(x, y) },
            img.width,
            img.height,
        )
    }

    @Test
    @DisplayName("recognizes the live event-points REWARDS summary")
    fun recognizesEventRewardsSummary() {
        assertTrue(matches("postcareer", "event_points_rewards_summary")) {
            "The event-period REWARDS summary must be recognized so the navigator can tap it through instead of failing UNKNOWN."
        }
    }

    @Test
    @DisplayName("does not fire on the career-end spark screens")
    fun ignoresSparkScreens() {
        // Every other screen in the between-run flow that has a real capture must read false: a
        // false positive here would blind-tap the body of some other screen.
        for (name in listOf("sparks_screen", "confirmation_original", "keep_confirmation_plain", "umamusume_details", "rating_record", "spark_selection_intro")) {
            assertFalse(matches("sparks", name)) { "$name must not be misread as the event-rewards summary." }
        }
    }

    @Test
    @DisplayName("rejects a blank surface")
    fun ignoresBlankSurface() {
        val white = SparkPixelSampler { _, _ -> 0xFFFFFFFF.toInt() }
        assertFalse(PostCareerScreenProbes.isEventPointsRewardsSummary(white, 1080, 1920))
        val black = SparkPixelSampler { _, _ -> 0xFF000000.toInt() }
        assertFalse(PostCareerScreenProbes.isEventPointsRewardsSummary(black, 1080, 1920))
    }

    @Test
    @DisplayName("rejects an unsupported resolution")
    fun ignoresUnsupportedResolution() {
        val green = SparkPixelSampler { _, _ -> (0xFF shl 24) or (117 shl 16) or (201 shl 8) or 12 }
        // Even a wall of the exact header green must not match below the supported surface size.
        assertFalse(PostCareerScreenProbes.isEventPointsRewardsSummary(green, 720, 1280))
    }
}
