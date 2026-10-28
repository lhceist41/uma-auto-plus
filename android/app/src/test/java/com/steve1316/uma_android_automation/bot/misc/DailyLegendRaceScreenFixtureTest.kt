package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.LabelDailyLegendRacesHeader
import com.steve1316.uma_android_automation.components.LabelItemsSelected
import com.steve1316.uma_android_automation.components.LabelLegendRunnerSelection
import com.steve1316.uma_android_automation.components.LabelMainRewards
import com.steve1316.uma_android_automation.components.LabelRaceDetails
import com.steve1316.uma_android_automation.components.LabelRunnerSelection
import com.steve1316.uma_android_automation.utils.FixturePng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.sqrt

/**
 * The Legend Race screen read on MuMu 1080x1920 frames (fixtures/legend/PROVENANCE.md). The matcher is OpenCV, which local
 * unit tests lack, so this reproduces its TM_CCOEFF_NORMED on the same red/blue-swapped grayscale, inside the rows the
 * library's regions cover on this display. Only the labels that decide a read are matched: the step's other templates stay
 * far below their bars on these frames (at most 0.62 against 0.8), and matching all of them would take minutes.
 */
@DisplayName("Legend Race screens on MuMu frames")
class DailyLegendRaceScreenFixtureTest {
    private val width = 1080
    private val height = 1920

    /** The header's region is the top quarter, the row label's the middle half. */
    private val topQuarter = 0 until height / 4
    private val middle = height / 4 until height * 3 / 4

    /** The Runner Selection header sits in the top eighth; a false match anywhere in the race list's top half would misread the list. */
    private val headerRows = 0 until height / 8
    private val topHalf = 0 until height / 2

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun image(name: String): Gray = gray(requireNotNull(javaClass.getResourceAsStream("/fixtures/legend/$name.png")) { "missing fixture $name.png" }.use { FixturePng.read(it) })

    private fun template(component: ComponentInterface): Gray = gray(repoFile("android/app/src/main/assets/images/${component.template.path}.png").inputStream().use { FixturePng.read(it) })

    private class Gray(val width: Int, val height: Int, val v: DoubleArray)

    /** The matcher's grayscale: the library converts RGBA with BGR2GRAY, so red and blue weights are swapped. */
    private fun gray(img: FixturePng): Gray =
        Gray(
            img.width,
            img.height,
            DoubleArray(img.width * img.height) {
                val argb = img.getRGB(it % img.width, it / img.width)
                0.114 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.299 * (argb and 0xFF)
            },
        )

    private class Match(val score: Double, val cx: Int, val cy: Int)

    /** Best TM_CCOEFF_NORMED score of [t] with its top edge in [rows] of [img], and its centre; window sums come from integral images. */
    private fun bestMatch(img: Gray, t: Gray, rows: IntRange): Match {
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
        for (oy in rows.first..minOf(rows.last, img.height - t.height)) {
            for (ox in 0..(img.width - t.width)) {
                val a = oy * iw + ox
                val b = (oy + t.height) * iw + ox
                val s = sum[b + t.width] - sum[b] - sum[a + t.width] + sum[a]
                val s2 = sq[b + t.width] - sq[b] - sq[a + t.width] + sq[a]
                val variance = s2 - s * s / n
                if (variance <= 1e-9) continue
                // The template is zero-mean, so the window mean drops out of the cross term.
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

    /** The library's default bar when a template sets none. */
    private fun bar(component: ComponentInterface): Double = component.template.confidence.takeIf { it > 0.0 } ?: 0.8

    private fun header(frame: Gray): Match = bestMatch(frame, template(LabelDailyLegendRacesHeader), topQuarter)

    private fun rewardsRow(frame: Gray): Match = bestMatch(frame, template(LabelMainRewards), middle)

    private fun screenWith(vararg present: ComponentInterface): LegendScreen = legendScreenOf({ false }) { it in present }

    @Test
    fun `a race list with one row keeps the grid's header, and its row makes it the race list`() {
        val frame = image("race_list_one_row")
        assertEquals(width to height, frame.width to frame.height)
        val header = header(frame)
        assertTrue(header.score >= bar(LabelDailyLegendRacesHeader), "header scored ${header.score}")
        val row = rewardsRow(frame)
        assertTrue(row.score >= bar(LabelMainRewards), "Main Rewards scored ${row.score}")
        assertTrue(row.cy in middle, "the row's label at y ${row.cy} is where the top row is looked for")
        assertEquals(LegendScreen.RACE_LIST, screenWith(LabelDailyLegendRacesHeader, LabelMainRewards))
    }

    @Test
    fun `the grid after a tap that opened no list has the header and no row, so it is still the grid`() {
        val frame = image("legend_grid")
        assertEquals(width to height, frame.width to frame.height)
        val header = header(frame)
        assertTrue(header.score >= bar(LabelDailyLegendRacesHeader), "header scored ${header.score}")
        val row = rewardsRow(frame)
        assertTrue(row.score < bar(LabelMainRewards), "Main Rewards scored ${row.score} on the grid")
        assertEquals(LegendScreen.LEGEND_GRID, screenWith(LabelDailyLegendRacesHeader))
    }

    @Test
    fun `the Legend Runner Selection is read from its header text, which the full header template misses`() {
        val frame = image("runner_selection")
        assertEquals(width to height, frame.width to frame.height)
        val full = bestMatch(frame, template(LabelRunnerSelection), headerRows)
        assertTrue(full.score < bar(LabelRunnerSelection), "the full header template scored ${full.score}")
        val text = bestMatch(frame, template(LabelLegendRunnerSelection), headerRows)
        assertTrue(text.score >= bar(LabelLegendRunnerSelection), "the header text scored ${text.score}")
        assertEquals(LegendScreen.RUNNER_SELECTION, screenWith(LabelLegendRunnerSelection))
    }

    @Test
    fun `the header text is not on the race list, and the screens read before Runner Selection keep their reads`() {
        val text = bestMatch(image("race_list_one_row"), template(LabelLegendRunnerSelection), topHalf)
        assertTrue(text.score < bar(LabelLegendRunnerSelection), "the header text scored ${text.score} on the race list")
        assertEquals(LegendScreen.LEGEND_GRID, screenWith(LabelDailyLegendRacesHeader, LabelLegendRunnerSelection))
        assertEquals(LegendScreen.RACE_DETAILS, screenWith(LabelRaceDetails, LabelLegendRunnerSelection, LabelMainRewards))
        assertEquals(LegendScreen.ITEMS_SELECTED, screenWith(LabelItemsSelected, LabelLegendRunnerSelection))
    }

    @Test
    fun `Race Details over the race list still reads as Race Details`() {
        assertEquals(LegendScreen.RACE_DETAILS, screenWith(LabelDailyLegendRacesHeader, LabelRaceDetails, LabelMainRewards))
        assertEquals(LegendScreen.SPENDING_DIALOG, legendScreenOf({ true }) { true })
    }
}
