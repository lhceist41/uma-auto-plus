package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.bot.infirmaryReadsEnabled
import com.steve1316.uma_android_automation.utils.FixturePng
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Grand Concert draws the career screen's Infirmary pill smaller than every other scenario, so the stock label template never
 * finds it there and no injury was healed in that scenario. These checks pin the Grand Concert template on the committed
 * Grand Concert career screens (1080x1920, disabled Infirmary at (168, 1737)) against a standard-layout career screen, and
 * the luminance gap that lets a disabled pill read as disabled.
 */
@DisplayName("Infirmary button in Grand Concert")
class InfirmaryGrandConcertFixtureTest {
    private val grandConcert = listOf("grandconcert/career_main_turn1", "grandconcert/career_after_training", "grandconcert/career_scheduled")
    private val standard = "trainingselection/career_menu_after_back"

    /** The second button row of the career screen, full width. */
    private val buttonRow = intArrayOf(0, 1600, 1080, 1860)

    /** The whole search region of the templates. */
    private val bottomHalf = intArrayOf(0, 960, 1080, 1920)

    /** The left part of the 1080x200 button-row crops, where the Infirmary pill sits. */
    private val pillCrop = intArrayOf(0, 0, 420, 200)

    private val cache = mutableMapOf<String, FixturePng>()

    private fun image(name: String): FixturePng =
        cache.getOrPut(name) {
            val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/$name.png")) { "missing fixture $name.png" }
            stream.use { FixturePng.read(it) }
        }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun template(path: String): FixturePng = repoFile("android/app/src/main/assets/images/components/$path.png").inputStream().use { FixturePng.read(it) }

    /** The matcher's grayscale: the library converts RGBA with BGR2GRAY, so red and blue weights are swapped. */
    private fun gray(argb: Int): Double = 0.114 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.299 * (argb and 0xFF)

    /** The luminance `checkDisabled` compares (`CustomImageUtils.getLuminanceAtCoordinates`), 0 to 1. */
    private fun luminance(argb: Int): Double = (0.2126 * ((argb shr 16) and 0xFF) + 0.7152 * ((argb shr 8) and 0xFF) + 0.0722 * (argb and 0xFF)) / 255.0

    private class Match(val score: Double, val cx: Int, val cy: Int, val x: Int, val y: Int)

    /** Best TM_CCOEFF_NORMED score of [tmpl] placed fully inside the window [x0, x1) x [y0, y1) of [img]. */
    private fun bestMatch(img: FixturePng, tmpl: FixturePng, window: IntArray): Match {
        val (x0, y0, x1, y1) = window.toList()
        val tw = tmpl.width
        val th = tmpl.height
        val t = DoubleArray(tw * th) { gray(tmpl.getRGB(it % tw, it / tw)) }
        val tMean = t.average()
        for (i in t.indices) t[i] -= tMean
        val tNorm = sqrt(t.sumOf { it * it })
        val ww = x1 - x0
        val src = DoubleArray(ww * (y1 - y0)) { gray(img.getRGB(x0 + it % ww, y0 + it / ww)) }
        var best = Match(-1.0, 0, 0, 0, 0)
        for (oy in 0..(y1 - y0 - th)) {
            for (ox in 0..(ww - tw)) {
                var sum = 0.0
                for (ty in 0 until th) {
                    val row = (oy + ty) * ww + ox
                    for (tx in 0 until tw) sum += src[row + tx]
                }
                val mean = sum / (tw * th)
                var cross = 0.0
                var sq = 0.0
                for (ty in 0 until th) {
                    val row = (oy + ty) * ww + ox
                    for (tx in 0 until tw) {
                        val d = src[row + tx] - mean
                        cross += d * t[ty * tw + tx]
                        sq += d * d
                    }
                }
                val score = if (sq == 0.0) 0.0 else cross / (sqrt(sq) * tNorm)
                if (score > best.score) best = Match(score, x0 + ox + tw / 2, y0 + oy + th / 2, x0 + ox, y0 + oy)
            }
        }
        return best
    }

    private fun meanLuminance(img: FixturePng, x: Int, y: Int, w: Int, h: Int): Double {
        var sum = 0.0
        for (yy in y until y + h) for (xx in x until x + w) sum += luminance(img.getRGB(xx, yy))
        return sum / (w * h)
    }

    private fun meanLuminance(tmpl: FixturePng): Double = meanLuminance(tmpl, 0, 0, tmpl.width, tmpl.height)

    @Test
    fun `the Grand Concert template finds the smaller pill on every Grand Concert career screen`() {
        val tmpl = template("button/infirmary_grandconcert")
        for (name in grandConcert) {
            val m = bestMatch(image(name), tmpl, buttonRow)
            assertTrue(m.score >= 0.85, "$name scored ${m.score}")
            assertTrue(abs(m.cx - 168) <= 3 && abs(m.cy - 1737) <= 3, "$name matched at (${m.cx}, ${m.cy})")
        }
    }

    @Test
    fun `the Grand Concert template matches nothing on a standard career screen`() {
        val m = bestMatch(image(standard), template("button/infirmary_grandconcert"), bottomHalf)
        assertTrue(m.score < 0.70, "scored ${m.score} at (${m.cx}, ${m.cy})")
    }

    @Test
    fun `the stock template misses the Grand Concert pill and still finds the standard one`() {
        val tmpl = template("button/infirmary")
        for (name in grandConcert) {
            val m = bestMatch(image(name), tmpl, buttonRow)
            assertTrue(m.score < 0.80, "$name scored ${m.score}")
        }
        val m = bestMatch(image(standard), tmpl, buttonRow)
        assertTrue(m.score >= 0.95, "standard scored ${m.score}")
        assertTrue(abs(m.cx - 252) <= 3 && abs(m.cy - 1737) <= 3, "standard matched at (${m.cx}, ${m.cy})")
    }

    /**
     * `checkDisabled` reads a button as disabled only when the screen is more than 0.05 darker than the template, so a template cut
     * from a disabled capture would read every disabled pill as enabled and tap the Infirmary on every turn. The template keeps the
     * stock enabled luminance; the fixtures show the pill disabled.
     */
    @Test
    fun `a disabled Grand Concert pill is clearly darker than the template`() {
        val tmpl = template("button/infirmary_grandconcert")
        assertTrue(abs(meanLuminance(tmpl) - meanLuminance(template("button/infirmary"))) < 0.02, "the shrunk template changed luminance")
        for (name in grandConcert) {
            val m = bestMatch(image(name), tmpl, buttonRow)
            val gap = meanLuminance(tmpl) - meanLuminance(image(name), m.x, m.y, tmpl.width, tmpl.height)
            assertTrue(gap >= 0.20, "$name gap $gap")
        }
    }

    /** The read `checkDisabled` makes once the pill is found: disabled when the on-screen crop is more than 0.05 darker than the template, else enabled. */
    private fun disabledRead(name: String, tmpl: FixturePng): Boolean {
        val m = bestMatch(image(name), tmpl, pillCrop)
        assertTrue(m.score >= 0.85, "$name scored ${m.score} at (${m.cx}, ${m.cy})")
        return meanLuminance(tmpl) - meanLuminance(image(name), m.x, m.y, tmpl.width, tmpl.height) > 0.05
    }

    /**
     * Grand Concert's turn-start "Concert in N turns" banner washes the whole screen white. The disabled pill under it still matches the template
     * and reads enabled, so a single read would tap the Infirmary on a healthy turn; the next capture of the same screen reads it disabled.
     */
    @Test
    fun `a banner-washed pill reads enabled on its frame and disabled on the next capture, so the tap is not confirmed`() {
        val tmpl = template("button/infirmary_grandconcert")
        val washed = disabledRead("grandconcert/infirmary_pill_banner_washed", tmpl)
        val clear = disabledRead("grandconcert/infirmary_pill_banner_clear", tmpl)
        assertTrue(infirmaryReadsEnabled(washed), "the washed frame is the hazard: a disabled pill reads enabled")
        assertFalse(infirmaryReadsEnabled(clear), "the next capture of the same pill reads disabled")
        assertFalse(listOf(washed, clear).all { infirmaryReadsEnabled(it) }, "the tap needs every read enabled")
        assertFalse(infirmaryReadsEnabled(null), "a pill missing from the fresh capture is not an enabled read")
    }

    @Test
    fun `a lit pill reads enabled on a clean capture`() {
        assertTrue(infirmaryReadsEnabled(disabledRead("grandconcert/infirmary_pill_lit", template("button/infirmary_grandconcert"))))
    }

    @Test
    fun `the heal arm confirms the enabled read on fresh captures and taps from a fresh capture`() {
        val campaign = campaignSource()
        val body = campaign.substring(campaign.indexOf("open fun checkInjury("), campaign.indexOf("fun checkFinals("))
        val heal = body.indexOf("false -> {")
        val confirm = body.indexOf("(1..2).all {", heal)
        val wait = body.indexOf("game.wait(3.0)", confirm)
        val read = body.indexOf("infirmaryReadsEnabled(infirmary.checkDisabled(game.imageUtils))", wait)
        val publish = body.indexOf("StatusBoard.action(\"infirmary\", null)", read)
        val tap = body.indexOf("if (infirmary.click(game.imageUtils)) {", publish)
        assertTrue(heal >= 0 && confirm > heal && wait > confirm && read > wait && publish > read && tap > publish, "two settled confirm reads sit between the first read and the tap")
        assertTrue(body.substring(confirm, tap).contains("return false"), "an unconfirmed read leaves without tapping")
        assertFalse(body.substring(heal).contains("sourceBitmap = sourceBitmap"), "the tap never uses the frame that read enabled")
    }

    @Test
    fun `a heal starts a fresh turn like every executed action`() {
        val campaign = campaignSource()
        val block = campaign.indexOf("if (hasInjury) {")
        val body = campaign.substring(block, campaign.indexOf("\n        }\n", block))
        val armed = body.indexOf("careerStateLatch.armForNewTurnIf(bInfirmaryVisitUsedTurn)")
        assertTrue(body.indexOf("bHasCheckedDateThisTurn = false") in 0 until armed, "the date is re-read on the next pass")
        assertTrue(armed in 0 until body.indexOf("return choose(MainScreenAction.NONE"), "the shadow state is rearmed before the turn ends")
        assertFalse(body.contains("armForNewTurn()"), "never an unconditional rearm")
    }

    /** A disabled pill opens no confirmation, so the Ok click's result is the only truthful sign that the visit used the turn. */
    @Test
    fun `a forced tap on a disabled pill does not rearm the turn`() {
        val campaign = campaignSource()
        val body = campaign.substring(campaign.indexOf("open fun checkInjury("), campaign.indexOf("fun checkFinals("))
        assertTrue(body.indexOf("bInfirmaryVisitUsedTurn = false") in 0 until body.indexOf("checkDisabled("), "the flag is cleared before any read")
        val okClicks = Regex("""ButtonOk\.click\(game\.imageUtils, region = game\.imageUtils\.regionMiddle\)""").findAll(body).toList()
        assertTrue(okClicks.size == 2, "the forced attempt and the heal each accept the confirmation once: ${okClicks.size}")
        for (m in okClicks) assertTrue(body.substring(0, m.range.first).endsWith("bInfirmaryVisitUsedTurn = "), "the Ok result is what sets the flag")
    }

    private fun campaignSource(): String = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt").readText().replace("\r\n", "\n")

    @Test
    fun `checkInjury resolves the button per scenario, Grand Concert first with the stock fallback`() {
        val campaign = campaignSource()
        assertTrue(campaign.indexOf("private val infirmaryButtons") in 0 until campaign.indexOf("open fun checkInjury("), "the resolver sits before checkInjury")
        val resolver = campaign.substring(campaign.indexOf("private val infirmaryButtons"), campaign.indexOf("open fun checkInjury("))
        assertTrue(resolver.contains("get() = if (GrandConcertScenario.matches(game.scenario)) listOf(ButtonInfirmaryGrandConcert, ButtonInfirmary) else listOf(ButtonInfirmary)"))
        val body = campaign.substring(campaign.indexOf("open fun checkInjury("), campaign.indexOf("fun checkFinals("))
        assertTrue(body.contains("infirmaryButtons.firstNotNullOfOrNull"))
        assertFalse(Regex("""ButtonInfirmary\.""").containsMatchIn(body), "checkInjury must go through the resolved button")
    }
}
