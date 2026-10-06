package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Screen band mapping")
class ScreenBandsTest {
    private fun same(
        expected: Double,
        actual: Double,
        label: String,
    ) = assertEquals(expected.toRawBits(), actual.toRawBits(), "$label: expected $expected, got $actual")

    @Nested
    @DisplayName("1080x1920 is unchanged, bit for bit")
    inner class Identity {
        @Test
        @DisplayName("every band and inset returns the input")
        fun everyBand() {
            for (inset in listOf(0, 94)) {
                for (band in ScreenBand.entries) {
                    for (y in listOf(0.0, 30.0, 322.0, 879.36, 1772.16, 1873.0, 1919.0)) {
                        same(y, gameY(y, band, 1080, 1920, inset), "$band inset $inset y $y")
                    }
                }
            }
        }

        @Test
        @DisplayName("a sampler is returned as it is")
        fun sampler() {
            val sampler = SparkPixelSampler { x, y -> x * 7 + y }
            for (band in ScreenBand.entries) assertSame(sampler, sampler.onScreen(band, 1080, 1920))
        }

        @Test
        @DisplayName("every converted site gives the coordinate it gave before")
        fun convertedSites() {
            val height = 1920
            // Fractions of the capture height the sites used before.
            same(height * 0.458, gameY(1920 * 0.458, ScreenBand.MIDDLE, 1080, height), "scenario carousel chevron")
            same(height * 0.85, gameY(1920 * 0.85, ScreenBand.BOTTOM, 1080, height), "scenario Next fallback")
            same((height * 0.94).toInt().toDouble(), gameY(1920 * 0.94, ScreenBand.BOTTOM, 1080, height).toInt().toDouble(), "skip pill OCR top")
            same(height * SKIP_PILL_CENTRE_Y_FRACTION, gameY(1920 * SKIP_PILL_CENTRE_Y_FRACTION, ScreenBand.BOTTOM, 1080, height), "skip pill tap")
            same((height * 0.46f).toDouble(), gameY((1920 * 0.46f).toDouble(), ScreenBand.MIDDLE, 1080, height), "deck arrows")
            // Absolute 1080x1920 points the sites used before.
            same(FinalConfirmationTabGeometry.NORMAL_TAB_CLICK_Y.toDouble(), gameY(FinalConfirmationTabGeometry.NORMAL_TAB_CLICK_Y.toDouble(), ScreenBand.DIALOG, 1080, height), "Normal Career tab")
            same(110.0, gameY(110.0, ScreenBand.TOP, 1080, height), "goal text box top")
            for (y in QuickModeGeometry.ROW_YS) same(y.toDouble(), gameY(y.toDouble(), ScreenBand.DIALOG, 1080, height), "Quick Mode row $y")
            same(QuickModeGeometry.CONFIRM_Y.toDouble(), gameY(QuickModeGeometry.CONFIRM_Y.toDouble(), ScreenBand.DIALOG, 1080, height), "Quick Mode Confirm")
            same(PagedHelpGeometry.CLOSE_Y.toDouble(), gameY(PagedHelpGeometry.CLOSE_Y.toDouble(), ScreenBand.DIALOG, 1080, height), "paged help Close")
        }
    }

    @Nested
    @DisplayName("1080x2316 with a 94 px inset lands on the positions measured on a phone")
    inner class MeasuredPhone {
        private fun phone(
            y: Double,
            band: ScreenBand,
        ) = gameY(y, band, 1080, 2316, 94)

        private fun near(
            expected: Double,
            actual: Double,
            tolerance: Double,
            label: String,
        ) = assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "$label: expected $expected +-$tolerance, got $actual")

        @Test
        @DisplayName("Scenario Select: header, carousel chevron and Next")
        fun scenarioSelect() {
            near(354.0, phone(260.0, ScreenBand.TOP), 1.0, "Scenario Select header")
            near(204.0, phone(110.0, ScreenBand.TOP), 0.0, "goal text box top (MuMu 110, phone goal banner +94)")
            near(1124.0, phone(1920 * 0.458, ScreenBand.MIDDLE), 2.0, "carousel chevron")
            near(2011.0, phone(1615.0, ScreenBand.BOTTOM), 1.0, "Next button")
        }

        @Test
        @DisplayName("dialogs move by half the extra height")
        fun dialogs() {
            near(708.0, phone(510.0, ScreenBand.DIALOG), 1.0, "Continue Career dialog row")
            near(689.0, phone(QuickModeGeometry.HEADER_Y.toDouble(), ScreenBand.DIALOG), 3.0, "Quick Mode header")
            near(522.0, phone(FinalConfirmationTabGeometry.NORMAL_TAB_SAMPLE_Y.toDouble(), ScreenBand.DIALOG), 4.0, "Final Confirmation tabs (band 496-550)")
            near(2010.0, phone(PagedHelpGeometry.CLOSE_Y.toDouble(), ScreenBand.DIALOG), 2.0, "paged help Close")
        }

        @Test
        @DisplayName("skip pill and deck arrows")
        fun bottomAndMiddle() {
            near(2266.0, phone(1920 * SKIP_PILL_CENTRE_Y_FRACTION, ScreenBand.BOTTOM), 4.0, "skip pill")
            val arrow = phone((1920 * 0.46f).toDouble(), ScreenBand.MIDDLE)
            assertTrue(arrow in 1064.0..1142.0, "deck arrow $arrow inside the measured 1058-1148 span with tap jitter")
        }
    }

    @Nested
    @DisplayName("a capture padded past the screen width")
    inner class PaddedCapture {
        private fun repoFile(relative: String): java.io.File {
            var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
            repeat(8) {
                if (java.io.File(dir, relative).isFile) return java.io.File(dir, relative)
                dir = dir?.parentFile
            }
            throw AssertionError("$relative not found from the test working directory")
        }

        @Test
        @DisplayName("a 1088-wide phone capture is not a mapped surface, which is why it must be cropped first")
        fun paddedCaptureIsNotMapped() {
            // The live miss: the phone's ImageReader rows are 4352 bytes, so the library built a 1088x2316 Bitmap and the
            // carousel chevron went to 0.458 * 2316 = 1060 instead of 1124.
            assertTrue(!isMappedSurface(1088, 2316))
            assertEquals(1920 * 0.458 * 2316 / 1920, gameY(1920 * 0.458, ScreenBand.MIDDLE, 1088, 2316, 94), 1e-9)
            assertEquals(1124.36, gameY(1920 * 0.458, ScreenBand.MIDDLE, 1080, 2316, 94), 0.01)
        }

        @Test
        @DisplayName("padding is detected only when the capture is wider than the screen")
        fun paddingDetection() {
            assertTrue(capturePaddedPastScreen(1088, 1080))
            assertTrue(!capturePaddedPastScreen(1080, 1080))
            assertTrue(!capturePaddedPastScreen(1088, 0))
            assertTrue(!capturePaddedPastScreen(1000, 1080))
        }

        @Test
        @DisplayName("every capture the bot reads is cropped to the screen width before use")
        fun capturesAreCropped() {
            val src = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/utils/CustomImageUtils.kt").readText().replace("\r\n", "\n")
            assertTrue(src.contains("val bitmap = cropToScreenWidth(super.getSourceBitmap(saveImage), SharedData.displayWidth)"))
            val crop = src.substring(src.indexOf("private fun cropToScreenWidth("))
            assertTrue(crop.contains("if (!capturePaddedPastScreen(capture.width, screenWidth)) return capture"), "MuMu frames are returned as they are")
            assertTrue(crop.contains("Bitmap.createBitmap(capture, 0, 0, screenWidth, capture.height)"))
        }
    }

    @Nested
    @DisplayName("other shapes follow the same rule")
    inner class OtherShapes {
        @Test
        @DisplayName("taller 1080-wide screens with and without an inset")
        fun tallerScreens() {
            for ((height, inset) in listOf(2340 to 0, 2340 to 94, 2400 to 0, 2400 to 110)) {
                val extra = height - 1920.0
                same(100.0 + inset, gameY(100.0, ScreenBand.TOP, 1080, height, inset), "TOP $height/$inset")
                same(1000.0 + (inset + extra) / 2.0, gameY(1000.0, ScreenBand.MIDDLE, 1080, height, inset), "MIDDLE $height/$inset")
                same(1800.0 + extra, gameY(1800.0, ScreenBand.BOTTOM, 1080, height, inset), "BOTTOM $height/$inset")
                same(900.0 + extra / 2.0, gameY(900.0, ScreenBand.DIALOG, 1080, height, inset), "DIALOG $height/$inset")
            }
        }

        @Test
        @DisplayName("other widths are not mapped yet and keep the old proportional placement and pixels")
        fun otherWidths() {
            assertEquals(1615.0 * 3088 / 1920, gameY(1615.0, ScreenBand.BOTTOM, 1440, 3088, 125), 1e-9)
            assertEquals(879.36 * 1544 / 1920, gameY(879.36, ScreenBand.MIDDLE, 720, 1544, 63), 1e-9)
            val sampler = SparkPixelSampler { x, y -> x + y }
            assertSame(sampler, sampler.onScreen(ScreenBand.BOTTOM, 1440, 3088))
            assertSame(sampler, sampler.onScreen(ScreenBand.TOP, 720, 1544))
            assertTrue(isMappedSurface(1080, 2316) && isMappedSurface(1080, 1920))
            assertTrue(!isMappedSurface(1440, 3088) && !isMappedSurface(720, 1544) && !isMappedSurface(1080, 1728))
        }

        @Test
        @DisplayName("a screen shorter than 16:9 keeps the old proportional placement")
        fun shorterScreen() {
            assertEquals(879.36 * 1728 / 1920, gameY(879.36, ScreenBand.MIDDLE, 1080, 1728, 0), 1e-9)
            assertEquals(1615.0 * 1728 / 1920, gameY(1615.0, ScreenBand.BOTTOM, 1080, 1728, 94), 1e-9)
            val sampler = SparkPixelSampler { x, y -> x + y }
            assertSame(sampler, sampler.onScreen(ScreenBand.DIALOG, 1080, 1728))
            assertSame(sampler, sampler.onScreen(ScreenBand.TOP, 1600, 2560))
        }

        @Test
        @DisplayName("a mapped sampler reads the moved pixel")
        fun mappedSampler() {
            val sampler = SparkPixelSampler { x, y -> x * 10_000 + y }
            assertEquals(540 * 10_000 + 2269, sampler.onScreen(ScreenBand.BOTTOM, 1080, 2316).argb(540, 1873))
            val saved = screenTopInset
            try {
                screenTopInset = 94
                assertEquals(110 * 10_000 + 124, sampler.onScreen(ScreenBand.TOP, 1080, 2316).argb(110, 30))
                assertEquals(110 * 10_000 + 520, sampler.onScreen(ScreenBand.DIALOG, 1080, 2316).argb(110, 322))
            } finally {
                screenTopInset = saved
            }
        }
    }
}
