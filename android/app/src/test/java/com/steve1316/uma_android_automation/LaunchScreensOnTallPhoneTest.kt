package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.ScreenBand
import com.steve1316.uma_android_automation.utils.gameY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/** Trainee Select, the post-career Details card, the Home CAREER fallback and the lineage capture gate on a 1080x2316 phone (cutout inset 94). */
@DisplayName("Launch screens on a tall phone")
class LaunchScreensOnTallPhoneTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val nav by lazy {
        repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt").readText().replace("\r\n", "\n")
    }

    private fun phone(
        y1920: Double,
        band: ScreenBand,
    ) = gameY(y1920, band, 1080, 2316, 94)

    private fun same(
        expected: Double,
        actual: Double,
        label: String,
    ) = assertEquals(expected.toRawBits(), actual.toRawBits(), label)

    // The fractions as CareerLaunchNavigator declares them (Float arrays and literals).
    private val traineeHeaderY = 0.125f
    private val traineePreviewY = 0.295f
    private val traineeRow0 = 0.585f
    private val traineeRowStep = 0.099f
    private val detailsTitleY = 0.02f

    @Nested
    @DisplayName("1080x1920 is unchanged, bit for bit")
    inner class Identity {
        @Test
        fun everyConvertedSite() {
            val h = 1920
            same((h * traineeHeaderY).toInt().toDouble(), gameY((1920 * traineeHeaderY).toDouble(), ScreenBand.TOP, 1080, h).toInt().toDouble(), "Trainee Select header")
            same((h * traineePreviewY).toInt().toDouble(), gameY((1920 * traineePreviewY).toDouble(), ScreenBand.MIDDLE, 1080, h).toInt().toDouble(), "trainee preview")
            for (row in 0..1) {
                same((h * (traineeRow0 + row * traineeRowStep)).toDouble(), gameY((1920 * (traineeRow0 + row * traineeRowStep)).toDouble(), ScreenBand.MIDDLE, 1080, h), "grid row $row")
            }
            same((h * detailsTitleY).toInt().toDouble(), gameY((1920 * detailsTitleY).toDouble(), ScreenBand.MIDDLE, 1080, h).toInt().toDouble(), "Details card title")
            same((h * 0.80).toInt().toDouble(), gameY(1920 * 0.80, ScreenBand.BOTTOM, 1080, h).toInt().toDouble(), "Home CAREER OCR")
            same(h * 0.86, gameY(1920 * 0.86, ScreenBand.BOTTOM, 1080, h), "Home CAREER tap")
        }
    }

    @Nested
    @DisplayName("1080x2316 lands on the measured phone frames")
    inner class MeasuredPhone {
        @Test
        @DisplayName("Trainee Select: header strip 338-372, preview pill 828-911, grid rows from 1226 and 1477")
        fun traineeSelect() {
            val header = phone((1920 * traineeHeaderY).toDouble(), ScreenBand.TOP).toInt()
            assertTrue(header <= 338 && header + (2316 * 0.05f).toInt() >= 372, "header crop $header")
            val preview = phone((1920 * traineePreviewY).toDouble(), ScreenBand.MIDDLE).toInt()
            assertTrue(preview <= 828 + 20 && preview + (2316 * 0.05f).toInt() >= 911 - 20, "preview crop $preview covers the name pill")
            val row0 = phone((1920 * traineeRow0).toDouble(), ScreenBand.MIDDLE)
            val row1 = phone((1920 * (traineeRow0 + traineeRowStep)).toDouble(), ScreenBand.MIDDLE)
            assertTrue(row0 in 1226.0 + 20..1440.0 - 20, "row 0 tap $row0")
            assertTrue(row1 in 1477.0 + 20..1690.0 - 20, "row 1 tap $row1")
        }

        @Test
        @DisplayName("post-career Details card title band 295-384 and Home CAREER text 2006-2071")
        fun detailsAndHome() {
            val title = phone((1920 * detailsTitleY).toDouble(), ScreenBand.MIDDLE).toInt()
            assertTrue(title <= 295 && title + (2316 * 0.07f).toInt() >= 384, "title crop $title")
            val ocr = phone(1920 * 0.80, ScreenBand.BOTTOM).toInt()
            assertTrue(ocr <= 2006 && ocr + (2316 * 0.12).toInt() >= 2071, "CAREER OCR crop $ocr")
            assertTrue(phone(1920 * 0.86, ScreenBand.BOTTOM) in 2006.0..2071.0, "CAREER tap")
        }
    }

    @Nested
    @DisplayName("source wiring")
    inner class Wiring {
        @Test
        fun sitesUseTheirBands() {
            assertTrue(nav.contains("gameY((1920 * traineeHeaderRegion[1]).toDouble(), ScreenBand.TOP, bitmap.width, bitmap.height).toInt(),"))
            assertTrue(nav.contains("gameY((1920 * traineePreviewRegion[1]).toDouble(), ScreenBand.MIDDLE, bitmap.width, bitmap.height).toInt(),"))
            assertEquals(3, Regex("gameY\\(\\(1920 \\* \\(?traineeRow0Fraction").findAll(nav).count(), "remembered, scanned and anchor grid taps")
            assertTrue(nav.contains("gameY((1920 * detailsTitleRegion[1]).toDouble(), ScreenBand.MIDDLE, bitmap.width, bitmap.height).toInt(),"))
            assertTrue(nav.contains("gameY(1920 * 0.80, ScreenBand.BOTTOM, bitmap.width, bitmap.height).toInt(),"))
            assertTrue(nav.contains("val tapY = gameY(1920 * 0.86, ScreenBand.BOTTOM, bitmap.width, bitmap.height)"))
        }

        @Test
        @DisplayName("lineage capture never opens its Sparks view outside 1080x1920")
        fun lineageGated() {
            val gate = nav.indexOf("if (SharedData.displayWidth != 1080 || SharedData.displayHeight != 1920) {\n                    MessageLog.i(TAG, \"[LINEAGE] Passive lineage capture is skipped")
            assertTrue(gate >= 0)
            assertTrue(gate < nav.indexOf("captureLineageTelemetry()", gate), "the gate comes before the capture")
        }
    }
}
