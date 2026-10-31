package com.steve1316.uma_android_automation.types

import com.steve1316.uma_android_automation.utils.FixturePng
import com.steve1316.uma_android_automation.utils.argMaxAboveFloor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pins the shipped Learn-list title glyph templates, their upscale and their score floor against live title crops (src/test/resources/fixtures/skilllist, see PROVENANCE.md).
 * The runtime matcher is OpenCV, which local unit tests lack, so this reproduces its bilinear upscale and TM_CCOEFF_NORMED on the same red/blue-swapped grayscale.
 */
@DisplayName("Skill List title glyphs")
class SkillListGlyphFixtureTest {
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

    private fun scaled(src: Gray, width: Int, height: Int): Gray {
        val sx = src.width.toDouble() / width
        val sy = src.height.toDouble() / height
        val v = DoubleArray(width * height)
        for (y in 0 until height) {
            val fy = ((y + 0.5) * sy - 0.5).coerceIn(0.0, src.height - 1.0)
            val y0 = floor(fy).toInt()
            val y1 = min(y0 + 1, src.height - 1)
            val wy = fy - y0
            for (x in 0 until width) {
                val fx = ((x + 0.5) * sx - 0.5).coerceIn(0.0, src.width - 1.0)
                val x0 = floor(fx).toInt()
                val x1 = min(x0 + 1, src.width - 1)
                val wx = fx - x0
                val top = src.v[y0 * src.width + x0] * (1 - wx) + src.v[y0 * src.width + x1] * wx
                val bottom = src.v[y1 * src.width + x0] * (1 - wx) + src.v[y1 * src.width + x1] * wx
                v[y * width + x] = top * (1 - wy) + bottom * wy
            }
        }
        return Gray(width, height, v)
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
            gray(repoFile("android/app/src/main/assets/images/components/icon/skill_title_$name.png").inputStream().use { FixturePng.read(it) })
        }
    }

    // Every capture is 1080 wide, so the runtime scale is the upscale alone.
    private val expected: Map<String, String?> =
        mapOf(
            "double_maverick" to "◎",
            "double_outer_post_proficiency" to "◎",
            "double_mile_straightaways_obtained" to "◎",
            "double_firm_conditions_obtained" to "◎",
            "circle_corner_adept" to "○",
            "circle_corner_adept_obtained" to "○",
            "circle_corner_recovery" to "○",
            "circle_tokyo_racecourse" to "○",
            "circle_nakayama_racecourse" to "○",
            "none_productive_plan" to null,
            "none_up_tempo" to null,
            "none_on_the_attack_obtained" to null,
            "none_professor_of_curvature_gold" to null,
            "none_professor_of_curvature_gold_obtained" to null,
            "none_unstoppable_gold" to null,
        )

    @TestFactory
    fun `each title resolves to its glyph or to none`(): List<DynamicTest> =
        expected.map { (name, glyph) ->
            DynamicTest.dynamicTest(name) {
                val crop = gray(requireNotNull(javaClass.getResourceAsStream("/fixtures/skilllist/$name.png")) { "missing fixture $name" }.use { FixturePng.read(it) })
                val cell = scaled(crop, (crop.width * SKILL_LIST_GLYPH_UPSCALE).toInt(), (crop.height * SKILL_LIST_GLYPH_UPSCALE).toInt())
                val scores = templates.mapValues { (_, t) -> bestCorrelation(cell, t) }
                assertEquals(glyph, argMaxAboveFloor(scores, SKILL_LIST_GLYPH_MIN_CONFIDENCE), "scores $scores")
                // The single ring also fits the outer ring of a double circle, so the wrong circle must stay under the floor on its own.
                assertTrue(scores.filterKeys { it != glyph }.values.all { it < SKILL_LIST_GLYPH_MIN_CONFIDENCE }, "scores $scores")
            }
        }
}
