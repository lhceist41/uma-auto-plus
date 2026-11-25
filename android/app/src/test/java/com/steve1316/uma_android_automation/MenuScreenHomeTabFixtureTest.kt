package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.FixturePng
import com.steve1316.uma_android_automation.utils.OwnInputProbeResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Event Missions and the Veteran Roster show the Home tab unselected, so the launch reads them as unknown screens; with the tab on
 * screen and the bot's taps arriving, the navigator taps it once. Missions keeps the tab selected and reads as Home instead.
 * Fixtures: see fixtures/menuhometab/PROVENANCE.md.
 */
@DisplayName("Home tab on unknown menu screens")
class MenuScreenHomeTabFixtureTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun fixture(path: String): FixturePng = requireNotNull(javaClass.getResourceAsStream("/fixtures/$path.png")) { "missing fixture $path.png" }.use { FixturePng.read(it) }

    private fun template(name: String): FixturePng = repoFile("android/app/src/main/assets/images/components/button/$name.png").inputStream().use { FixturePng.read(it) }

    /** The matcher's grayscale: the library converts RGBA with BGR2GRAY, so red and blue weights are swapped. */
    private fun gray(argb: Int): Double = 0.114 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.299 * (argb and 0xFF)

    private class Match(val score: Double, val cx: Int)

    /** Best TM_CCOEFF_NORMED score of [tmpl] in rows [top] until [bottom] of [img]. */
    private fun bestMatch(img: FixturePng, tmpl: FixturePng, top: Int = 0, bottom: Int = img.height): Match {
        val tw = tmpl.width
        val th = tmpl.height
        val t = DoubleArray(tw * th) { gray(tmpl.getRGB(it % tw, it / tw)) }
        val tMean = t.average()
        for (i in t.indices) t[i] -= tMean
        val tNorm = sqrt(t.sumOf { it * it })
        val w = img.width
        val h = bottom - top
        val src = DoubleArray(w * h) { gray(img.getRGB(it % w, top + it / w)) }
        var best = Match(-1.0, 0)
        for (oy in 0..(h - th)) {
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
                if (score > best.score) best = Match(score, ox + tw / 2)
            }
        }
        return best
    }

    /** The library's bar for templates without their own confidence. */
    private val bar = 0.8

    private val unselected by lazy { template("menu_bar_home_unselected") }

    private fun tapsHomeTab(tabScore: Double, probe: OwnInputProbeResult, retried: Boolean = false): Boolean =
        unknownScreenTapsHomeTab(retried, { tabScore >= bar }, { probe })

    @Test
    fun `Event Missions gets the Home-tab tap`() {
        val m = bestMatch(fixture("menuhometab/event_missions_bottom_bar"), unselected)
        assertTrue(m.score >= bar && abs(m.cx - 541) <= 6, "scored ${m.score} at x ${m.cx}")
        assertTrue(tapsHomeTab(m.score, OwnInputProbeResult.ARRIVED))
    }

    @Test
    fun `the Veteran Roster gets the Home-tab tap`() {
        val m = bestMatch(fixture("menuhometab/veteran_roster_bottom_bar"), unselected)
        assertTrue(m.score >= bar && abs(m.cx - 541) <= 6, "scored ${m.score} at x ${m.cx}")
        assertTrue(tapsHomeTab(m.score, OwnInputProbeResult.ARRIVED))
    }

    @Test
    fun `Missions keeps the selected tab and is left to the Home handler`() {
        val bottomBar = fixture("menuhometab/missions_bottom_bar")
        val unselectedScore = bestMatch(bottomBar, unselected).score
        assertTrue(unselectedScore < 0.6, "unselected scored $unselectedScore")
        assertFalse(tapsHomeTab(unselectedScore, OwnInputProbeResult.ARRIVED))
        val selected = bestMatch(bottomBar, template("menu_bar_home_selected")).score
        assertTrue(selected >= bar, "the selected tab scored $selected, so the screen reads as Home")
    }

    @Test
    fun `an unknown screen with no bottom bar gets no tap and no probe`() {
        // An in-career event frame: the bottom bar is gone, so the band the bar would fill holds no Home tab.
        val score = bestMatch(fixture("frozengame/still_event_1"), unselected, top = 1770, bottom = 1905).score
        assertTrue(score < 0.6, "scored $score")
        var probes = 0
        assertFalse(unknownScreenTapsHomeTab(false, { score >= bar }) { OwnInputProbeResult.ARRIVED.also { probes++ } })
        assertTrue(probes == 0, "the probe runs only with the tab on screen")
    }

    @Test
    fun `a lost or unread tap keeps the rebind path, and the tap happens once`() {
        assertFalse(tapsHomeTab(1.0, OwnInputProbeResult.LOST))
        assertFalse(tapsHomeTab(1.0, OwnInputProbeResult.INCONCLUSIVE))
        var asked = 0
        assertFalse(unknownScreenTapsHomeTab(true, { true.also { asked++ } }) { OwnInputProbeResult.ARRIVED.also { asked++ } })
        assertTrue(asked == 0, "a used retry checks nothing")
    }

    @Test
    fun `the unknown branch taps after the accessibility step, marks the retry first and logs after acting`() {
        val navigator = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt").readText().replace("\r\n", "\n")
        val start = navigator.indexOf("Unknown screen state (\$consecutiveUnknowns/\$unknownLimit)")
        val branch = navigator.substring(start, navigator.indexOf("waitSafe(2.0)", start))
        val rebind = branch.indexOf("if (unknownScreenNeedsRebind(ownInputProbe()))")
        val gate = branch.indexOf("unknownScreenTapsHomeTab(homeTabRetried, { ButtonMenuBarHomeUnselected.check(iu, region = Region.bottomHalf) }, { ownInputProbe() })")
        assertTrue(rebind in 0 until gate, branch)
        assertTrue(branch.lastIndexOf("!betweenRunRecovery.gameComingBack", gate) > rebind, "not while the game comes back through its title")
        val retried = branch.indexOf("homeTabRetried = true", gate)
        val click = branch.indexOf("ButtonMenuBarHomeUnselected.click(iu, region = Region.bottomHalf)", gate)
        val log = branch.indexOf("[NAV] Unknown screen with the Home tab unselected", gate)
        assertTrue(retried in gate until click && click < log, branch)
        val gone = branch.indexOf("[NAV] Unknown screen: the Home tab was gone at the tap", click)
        assertTrue(gone > click, "a click that finds no tab still logs that the retry is used")
    }

    private val unknownBranch by lazy {
        val navigator = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt").readText().replace("\r\n", "\n")
        val start = navigator.indexOf("Unknown screen state (\$consecutiveUnknowns/\$unknownLimit)")
        navigator.substring(start, navigator.indexOf("waitSafe(2.0)", start))
    }

    @Test
    fun `the first unknown gets no Home-tab tap`() {
        val gate = unknownBranch.indexOf("unknownScreenTapsHomeTab(")
        val condition = unknownBranch.substring(unknownBranch.lastIndexOf("if (", gate), gate)
        assertTrue(Regex("""^if \(consecutiveUnknowns >= 2 &&\s+!betweenRunRecovery\.gameComingBack &&\s+$""").matches(condition), condition)
    }

    @Test
    fun `a throw at the check or the tap is caught, an interrupt is not`() {
        val gate = unknownBranch.indexOf("unknownScreenTapsHomeTab(")
        val tryAt = unknownBranch.lastIndexOf("try {", gate)
        assertTrue(tryAt > unknownBranch.indexOf("if (unknownScreenNeedsRebind(ownInputProbe()))"), "the guard opens after the accessibility step")
        val head = unknownBranch.substring(tryAt, gate)
        assertTrue(Regex("""^try \{\s+if \(consecutiveUnknowns >= 2 &&\s+!betweenRunRecovery\.gameComingBack &&\s+$""").matches(head), "nothing but the gate inside the guard before it: $head")
        val catches = unknownBranch.substring(unknownBranch.indexOf("[NAV] Unknown screen: the Home tab was gone at the tap", gate))
        assertTrue(Regex("""\} catch \(e: InterruptedException\) \{\s+throw e\s+\} catch \(e: Exception\) \{\s+MessageLog\.w\(TAG, "\[NAV] [^"]*"\)\s+\}\s+$""").containsMatchIn(catches), catches)
    }
}
