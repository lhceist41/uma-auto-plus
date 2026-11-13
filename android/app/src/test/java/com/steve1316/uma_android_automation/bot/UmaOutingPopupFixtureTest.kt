package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.components.LabelUmaOutingBanner
import com.steve1316.uma_android_automation.utils.FixturePng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.sqrt

/**
 * The Uma Outing popups (fixtures/umaouting/PROVENANCE.md) are recognised by their banner alone, and only the TAP bar or
 * Next is pressed. The matcher is OpenCV, which local unit tests lack, so this reproduces its TM_CCOEFF_NORMED on the
 * same red/blue-swapped grayscale inside the label's region (the top quarter).
 */
@DisplayName("Uma Outing popups")
class UmaOutingPopupFixtureTest {
    private val main = "android/app/src/main/java/com/steve1316/uma_android_automation"

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun source(relative: String) = repoFile("$main/$relative").readText().replace("\r\n", "\n")

    private fun fixture(path: String): FixturePng = requireNotNull(javaClass.getResourceAsStream("/fixtures/$path.png")) { "missing fixture $path.png" }.use { FixturePng.read(it) }

    private val banner by lazy { gray(repoFile("android/app/src/main/assets/images/${LabelUmaOutingBanner.template.path}.png").inputStream().use { FixturePng.read(it) }) }

    private class Gray(val width: Int, val height: Int, val v: DoubleArray)

    /** The matcher's grayscale: the library converts RGBA with BGR2GRAY, so red and blue weights are swapped. */
    private fun gray(img: FixturePng, rows: Int = img.height): Gray =
        Gray(
            img.width,
            rows,
            DoubleArray(img.width * rows) {
                val argb = img.getRGB(it % img.width, it / img.width)
                0.114 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.299 * (argb and 0xFF)
            },
        )

    private class Match(val score: Double, val cx: Int, val cy: Int)

    /** Best TM_CCOEFF_NORMED score of [t] anywhere in [img], and its centre; window sums come from integral images. */
    private fun bestMatch(img: Gray, t: Gray): Match {
        val tMean = t.v.average()
        val tc = DoubleArray(t.v.size) { t.v[it] - tMean }
        val tNorm = sqrt(tc.sumOf { it * it })
        val iw = img.width + 1
        val sum = DoubleArray(iw * (img.height + 1))
        val sq = DoubleArray(iw * (img.height + 1))
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val p = img.v[y * img.width + x]
                val i = (y + 1) * iw + x + 1
                sum[i] = p + sum[i - 1] + sum[i - iw] - sum[i - iw - 1]
                sq[i] = p * p + sq[i - 1] + sq[i - iw] - sq[i - iw - 1]
            }
        }
        val n = t.v.size.toDouble()
        var best = Match(-1.0, 0, 0)
        for (oy in 0..(img.height - t.height)) {
            for (ox in 0..(img.width - t.width)) {
                val a = oy * iw + ox
                val b = (oy + t.height) * iw + ox
                val s = sum[b + t.width] - sum[b] - sum[a + t.width] + sum[a]
                val s2 = sq[b + t.width] - sq[b] - sq[a + t.width] + sq[a]
                val variance = s2 - s * s / n
                if (variance <= 1e-9) continue
                var cross = 0.0
                for (ty in 0 until t.height) {
                    val row = (oy + ty) * img.width + ox
                    val trow = ty * t.width
                    for (tx in 0 until t.width) cross += img.v[row + tx] * tc[trow + tx]
                }
                val score = cross / (sqrt(variance) * tNorm)
                if (score > best.score) best = Match(score, ox + t.width / 2, oy + t.height / 2)
            }
        }
        return best
    }

    /** The library's default confidence, which the label keeps. */
    private val threshold = 0.8

    /** Each fixture is rows 240 to 470 of its 1080x1920 frame. */
    private val outingFixtureTop = 240

    @Test
    fun `the banner label matches both Outing popups, where the banner is`() {
        for (name in listOf("login_bonus", "gauge_max")) {
            val m = bestMatch(gray(fixture("umaouting/$name")), banner)
            assertTrue(m.score >= 0.95, "$name scored ${m.score}")
        }
    }

    @Test
    fun `the TAP bar point under the banner lands on the Login Bonus TAP bar`() {
        val m = bestMatch(gray(fixture("umaouting/login_bonus")), banner)
        // The TAP bar spans rows 1544 to 1640 of the frame, its "TAP" text rows 1565 to 1612.
        val tapY = m.cy + outingFixtureTop + UMA_OUTING_TAP_BELOW_BANNER
        assertTrue(tapY in 1565.0..1612.0, "TAP bar point at y $tapY")
    }

    @Test
    fun `no Home or race frame shows the banner`() {
        val frames =
            listOf(
                "titlescreen/notices_over_home",
                "teamtrials/home_rp0",
                "dailyraces/runner_selection",
                "dailyraces/race_result",
            )
        for (name in frames) {
            val img = fixture(name)
            val m = bestMatch(gray(img, rows = img.height / 4), banner)
            assertTrue(m.score < threshold, "$name scored ${m.score}")
        }
    }

    @Test
    fun `it presses only Next or the TAP bar`() {
        val helper = source("bot/UmaOutingPopup.kt")
        val pressed = Regex("\\b(Button|Label|Dialog)[A-Z]\\w*").findAll(helper).map { it.value }.toSet()
        assertEquals(setOf("ButtonNext", "LabelUmaOutingBanner"), pressed)
        assertEquals(1, Regex("CoordinateTap\\.tap\\(").findAll(helper).count())
        assertEquals(1, Regex("\\.click\\(").findAll(helper).count())
    }

    @Test
    fun `the navigator handles it between runs, before the generic advance buttons`() {
        val nav = source("CareerLaunchNavigator.kt")
        val detect = nav.substring(nav.indexOf("private fun detectScreenState("))
        val check = detect.indexOf("if (!careerLaunchInitiated && LabelUmaOutingBanner.check(iu, sourceBitmap = bitmap)) {\n            return LaunchScreenState.UMA_OUTING_POPUP")
        assertTrue(check >= 0)
        assertTrue(check < detect.indexOf("if (ButtonNext.check(iu, sourceBitmap = bitmap) ||"))
        assertTrue(nav.contains("LaunchScreenState.UMA_OUTING_POPUP -> {\n                if (!dismissUmaOutingPopup(iu, iu.getSourceBitmap()))"))
    }

    @Test
    fun `the misc tasks dismiss it before the shared dialog handler, and Daily Races names it before UNKNOWN`() {
        val misc = source("bot/misc/MiscTask.kt")
        val incidental = misc.substring(misc.indexOf("protected open fun handleIncidentalPopups(): Boolean {"))
        assertTrue(incidental.indexOf("dismissUmaOutingPopup(game.imageUtils, game.imageUtils.getSourceBitmap())") in 0 until incidental.indexOf("tryHandleAllDialogs("))
        val daily = source("bot/misc/DailyRaceTask.kt")
        val detect = daily.substring(daily.indexOf("private fun detectScreenState(sourceBitmap: Bitmap): DailyRaceScreenState {"))
        val named = detect.indexOf("if (LabelUmaOutingBanner.check(game.imageUtils, sourceBitmap = sourceBitmap)) {\n            return DailyRaceScreenState.UMA_OUTING_POPUP")
        assertTrue(named in 0 until detect.indexOf("return DailyRaceScreenState.UNKNOWN"))
        assertTrue(named < detect.indexOf("ButtonMenuBarHomeSelected.check("), "before the Home check under the popup")
        assertTrue(daily.contains("currentState == DailyRaceScreenState.UMA_OUTING_POPUP\n"), "a named state, kept from the shared handler")
        assertTrue(daily.contains("DailyRaceScreenState.UMA_OUTING_POPUP -> {\n                if (!dismissUmaOutingPopup(game.imageUtils, sourceBitmap))"))
    }
}
