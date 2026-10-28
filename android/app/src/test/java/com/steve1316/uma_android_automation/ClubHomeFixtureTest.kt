package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.FixturePng
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The Club keeps the bottom bar's Home tab selected, so the launch's last-resort Home check reads it as Home; with no
 * CAREER button there, the navigator taps the Home tab once before it fails. Fixtures: see fixtures/clubhome/PROVENANCE.md.
 */
@DisplayName("Club screen read as Home")
class ClubHomeFixtureTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val navigator by lazy {
        repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt").readText().replace("\r\n", "\n")
    }

    private fun body(signature: String): String {
        val start = navigator.indexOf(signature)
        assertTrue(start >= 0, "$signature not found")
        val end = Regex("\n    (/\\*\\*|// |(private |internal )?fun )").find(navigator, start + signature.length)?.range?.first ?: navigator.length
        return navigator.substring(start, end)
    }

    private fun image(name: String): FixturePng = requireNotNull(javaClass.getResourceAsStream("/fixtures/clubhome/$name.png")) { "missing fixture $name.png" }.use { FixturePng.read(it) }

    private fun template(name: String): FixturePng = repoFile("android/app/src/main/assets/images/components/$name.png").inputStream().use { FixturePng.read(it) }

    /** The matcher's grayscale: the library converts RGBA with BGR2GRAY, so red and blue weights are swapped. */
    private fun gray(argb: Int): Double = 0.114 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.299 * (argb and 0xFF)

    private class Match(val score: Double, val cx: Int, val cy: Int)

    /** Best TM_CCOEFF_NORMED score of [tmpl] anywhere in [img]. */
    private fun bestMatch(img: FixturePng, tmpl: FixturePng): Match {
        val tw = tmpl.width
        val th = tmpl.height
        val t = DoubleArray(tw * th) { gray(tmpl.getRGB(it % tw, it / tw)) }
        val tMean = t.average()
        for (i in t.indices) t[i] -= tMean
        val tNorm = sqrt(t.sumOf { it * it })
        val w = img.width
        val src = DoubleArray(w * img.height) { gray(img.getRGB(it % w, it / w)) }
        var best = Match(-1.0, 0, 0)
        for (oy in 0..(img.height - th)) {
            for (ox in 0..(w - tw)) {
                var sum = 0.0
                var sq = 0.0
                var cross = 0.0
                for (ty in 0 until th) {
                    val row = (oy + ty) * w + ox
                    for (tx in 0 until tw) {
                        val v = src[row + tx]
                        sum += v
                        sq += v * v
                        cross += v * t[ty * tw + tx]
                    }
                }
                val variance = sq - sum * sum / (tw * th)
                if (variance <= 0.0) continue
                val score = cross / (sqrt(variance) * tNorm)
                if (score > best.score) best = Match(score, ox + tw / 2, oy + th / 2)
            }
        }
        return best
    }

    @Test
    fun `the Club's bottom bar passes the Home-tab check the launch falls back on`() {
        val m = bestMatch(image("club_bottom_bar"), template("button/menu_bar_home_selected"))
        assertTrue(m.score >= 0.8, "scored ${m.score}")
        // The Home tab's label, full-frame centre (540, 1882); the crop starts at row 1830.
        assertTrue(abs(m.cx - 540) <= 6 && abs(m.cy - 52) <= 6, "matched at (${m.cx}, ${m.cy})")
    }

    @Test
    fun `the Club Menu label tells the Club from Home`() {
        val club = bestMatch(image("club_menu_corner"), template("label/club_menu")).score
        val home = bestMatch(image("home_menu_corner"), template("label/club_menu")).score
        assertTrue(club >= 0.8, "Club scored $club")
        assertTrue(home < 0.6, "Home's Menu button scored $home")
    }

    @Test
    fun `with no CAREER button the Home tab is tapped once before the failure`() {
        val home = body("private fun handleHomeScreen(")
        val ocrMiss = home.indexOf("OCR did not find 'CAREER' or 'Event'.")
        val retry = home.indexOf("if (!homeTabRetried) {")
        val failed = home.indexOf("return TransitionResult.Failed(")
        assertTrue(ocrMiss in 0 until retry, "the retry follows every CAREER detector")
        assertTrue(retry < failed, "the retry precedes the failure")
        val block = home.substring(retry, failed)
        assertTrue(block.indexOf("homeTabRetried = true") < block.indexOf("ButtonMenuBarHomeSelected.click(iu"))
        assertTrue(block.contains("return TransitionResult.Continue"))
        assertTrue(block.contains("LabelClubMenu.check(iu, sourceBitmap = bitmap)"))
        assertTrue(home.substring(failed).contains("HOME_SCREEN detected but all CAREER button detectors failed"))
    }

    @Test
    fun `every navigation starts with the retry unused`() {
        val reset = navigator.indexOf("homeTabRetried = false\n")
        assertTrue(reset >= 0)
        assertTrue(navigator.lastIndexOf("legacyAutoSelectAlreadyDone = false", reset) in 0 until reset)
        assertTrue(navigator.indexOf("homeTabRetried = false", reset + 1) < 0, "reset only in the per-navigation block")
    }
}
