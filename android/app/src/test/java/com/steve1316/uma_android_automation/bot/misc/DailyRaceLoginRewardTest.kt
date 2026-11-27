package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.uma_android_automation.components.ButtonSkip
import com.steve1316.uma_android_automation.utils.FixturePng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.sqrt

/**
 * The login reward screens after the daily reset (fixtures/loginreward/PROVENANCE.md) wait for a tap on their ">>"; left
 * UNKNOWN they ended a live Daily Races run on the safety rail. The matcher is OpenCV, which local unit tests lack, so this
 * reproduces its TM_CCOEFF_NORMED on the same red/blue-swapped grayscale inside ButtonSkip's region (the bottom half).
 */
@DisplayName("Daily Races login reward screens")
class DailyRaceLoginRewardTest {
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

    private val skip by lazy { gray(repoFile("android/app/src/main/assets/images/${ButtonSkip.template.path}.png").inputStream().use { FixturePng.read(it) }) }

    private class Gray(val width: Int, val height: Int, val v: DoubleArray)

    /** The matcher's grayscale from row [top] down: the library converts RGBA with BGR2GRAY, so red and blue weights are swapped. */
    private fun gray(img: FixturePng, top: Int = 0): Gray =
        Gray(
            img.width,
            img.height - top,
            DoubleArray(img.width * (img.height - top)) {
                val argb = img.getRGB(it % img.width, top + it / img.width)
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

    /** The library's default confidence, which ButtonSkip keeps. */
    private val threshold = 0.8

    @Test
    fun `Skip matches the round button on both login reward screens, where the button is`() {
        for (name in listOf("anniversary_countdown", "login_bonus")) {
            val m = bestMatch(gray(fixture("loginreward/$name")), skip)
            assertTrue(m.score >= 0.95, "$name scored ${m.score}")
            // The round button spans about x 925 to 1040 and rows 1765 to 1875 of the frame (rows 105 to 215 of the fixture).
            assertTrue(m.cx in 925..1040 && m.cy in 105..215, "$name matched at (${m.cx}, ${m.cy})")
        }
    }

    @Test
    fun `no Notices, title, loading or Daily Races screen shows it in the bottom half`() {
        val frames =
            listOf(
                "titlescreen/notices_over_home",
                "titlescreen/title_screen",
                "titlescreen/now_loading",
                "dailyraces/difficulty",
            )
        for (name in frames) {
            val img = fixture(name)
            val m = bestMatch(gray(img, top = img.height / 2), skip)
            assertTrue(m.score < threshold, "$name scored ${m.score}")
        }
    }

    @Test
    fun `Daily Races names the screen only when no other state claims the frame, and taps only Skip on it`() {
        val daily = source("bot/misc/DailyRaceTask.kt")
        val detect = daily.substring(daily.indexOf("private fun detectScreenState(sourceBitmap: Bitmap): DailyRaceScreenState {"))
        val named = detect.indexOf("if (ButtonSkip.check(game.imageUtils, sourceBitmap = sourceBitmap)) {\n            return DailyRaceScreenState.LOGIN_REWARD")
        val unknown = detect.indexOf("return DailyRaceScreenState.UNKNOWN\n    }")
        assertTrue(named >= 0)
        val checks = Regex("(?<=\\n {8})if \\(").findAll(detect.substring(0, unknown)).map { it.range.first }.toList()
        assertEquals(named, checks.last(), "the Skip check is the last check before UNKNOWN")
        assertEquals(1, Regex("ButtonSkip\\.").findAll(detect.substring(0, unknown)).count())
        val handler = daily.substring(daily.indexOf("DailyRaceScreenState.LOGIN_REWARD -> {"))
        val body = handler.substring(0, handler.indexOf("\n            }\n"))
        val pressed = Regex("\\b(Button|Label)[A-Z]\\w*\\.(click|check)\\(").findAll(body).map { it.value }.toList()
        assertEquals(listOf("ButtonSkip.click("), pressed)
        assertTrue(!daily.contains("currentState == DailyRaceScreenState.LOGIN_REWARD"), "not an own dialog: the shared handler still looks first")
    }
}
