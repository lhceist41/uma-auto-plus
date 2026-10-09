package com.steve1316.uma_android_automation.types

import com.steve1316.uma_android_automation.utils.FixturePng
import com.steve1316.uma_android_automation.utils.argMaxAboveFloor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.math.sqrt

/**
 * Pins the shipped Details tier-glyph templates and their score floor against live Skills-tab cells (src/test/resources/fixtures/detailsskills, see PROVENANCE.md). The runtime
 * matcher is OpenCV, which local unit tests lack, so this reproduces its TM_CCOEFF_NORMED on the same red/blue-swapped grayscale and feeds the runtime argmax rule.
 */
@DisplayName("Details Skills-tab tier glyphs")
class DetailsSkillGlyphFixtureTest {
    private class Gray(val width: Int, val height: Int, val v: DoubleArray)

    private fun gray(png: FixturePng): Gray {
        val v = DoubleArray(png.width * png.height)
        for (y in 0 until png.height) {
            for (x in 0 until png.width) {
                val p = png.getRGB(x, y)
                // The runtime runs COLOR_BGR2GRAY on RGBA data, so red takes blue's weight.
                v[y * png.width + x] = 0.114 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.299 * (p and 0xFF)
            }
        }
        return Gray(png.width, png.height, v)
    }

    private fun bestCorrelation(cell: Gray, template: Gray): Double {
        val n = template.width * template.height
        val tMean = template.v.average()
        val tDev = DoubleArray(n) { template.v[it] - tMean }
        val tNorm = sqrt(tDev.sumOf { it * it })
        var best = -1.0
        for (oy in 0..cell.height - template.height) {
            for (ox in 0..cell.width - template.width) {
                var sum = 0.0
                for (ty in 0 until template.height) for (tx in 0 until template.width) sum += cell.v[(oy + ty) * cell.width + ox + tx]
                val mean = sum / n
                var cross = 0.0
                var norm = 0.0
                for (ty in 0 until template.height) {
                    for (tx in 0 until template.width) {
                        val d = cell.v[(oy + ty) * cell.width + ox + tx] - mean
                        cross += d * tDev[ty * template.width + tx]
                        norm += d * d
                    }
                }
                if (norm > 0) best = maxOf(best, cross / (sqrt(norm) * tNorm))
            }
        }
        return best
    }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found")
    }

    private val templates: Map<String, Gray> by lazy {
        mapOf("◎" to "double_circle", "○" to "circle", "×" to "x").mapValues { (_, name) ->
            gray(repoFile("android/app/src/main/assets/images/components/icon/details_skill_$name.png").inputStream().use { FixturePng.read(it) })
        }
    }

    private val expected =
        mapOf(
            "double_medium_straightaways" to "◎",
            "double_medium_corners" to "◎",
            "double_end_closer_straightaways_two_lines" to "◎",
            "double_pace_chaser_straightaways_two_lines" to "◎",
            "double_firm_conditions_phone" to "◎",
            "circle_firm_conditions" to "○",
            "circle_wet_conditions" to "○",
            "circle_pace_chaser_straightaways_two_lines" to "○",
            "cross_sapporo_racecourse" to "×",
            "none_prudent_positioning" to null,
            "none_unyielding_gold" to null,
            "none_top_pick" to null,
        )

    @TestFactory
    fun `each cell resolves to its glyph or to none`(): List<DynamicTest> =
        expected.map { (name, glyph) ->
            DynamicTest.dynamicTest(name) {
                val cell = gray(requireNotNull(javaClass.getResourceAsStream("/fixtures/detailsskills/$name.png")) { "missing fixture $name" }.use { FixturePng.read(it) })
                val scores = templates.mapValues { (_, t) -> bestCorrelation(cell, t) }
                assertEquals(glyph, argMaxAboveFloor(scores, DETAILS_GLYPH_MIN_CONFIDENCE), "scores $scores")
            }
        }
}
