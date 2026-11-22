package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.utils.FixturePng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.sqrt

/**
 * Daily Sales finds Home's Shop button on the older "Shop" layout and on the 2nd Anniversary "Special Shop!" one, then
 * reads the "Daily" badge in the band above the matched centre (fixtures/homeshop/PROVENANCE.md). The matcher is OpenCV,
 * which local unit tests lack, so this reproduces its TM_CCOEFF_NORMED on the same red/blue-swapped grayscale and takes
 * the first template that reaches the default confidence, as [MultiStateButtonInterface] does.
 */
@DisplayName("Home Shop button and its Daily badge")
class HomeShopButtonFixtureTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun fixture(name: String): Gray = gray(requireNotNull(javaClass.getResourceAsStream("/fixtures/homeshop/$name.png")) { "missing fixture $name.png" }.use { FixturePng.read(it) })

    private fun template(path: String): Gray = gray(repoFile("android/app/src/main/assets/images/$path.png").inputStream().use { FixturePng.read(it) })

    /** The badge band's offsets, read from the source so the test follows them. */
    private val badgeBand: Map<String, Int> by lazy {
        val source = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/misc/DailyExtras.kt").readText()
        listOf("SHOP_BADGE_HALF_W", "SHOP_BADGE_TOP", "SHOP_BADGE_H").associateWith { name ->
            requireNotNull(Regex("const val $name = (-?\\d+)").find(source)) { "$name not found" }.groupValues[1].toInt()
        }
    }

    private class Gray(val width: Int, val height: Int, val v: DoubleArray) {
        fun crop(x: Int, y: Int, w: Int, h: Int): Gray = Gray(w, h, DoubleArray(w * h) { v[(y + it / w) * width + x + it % w] })
    }

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

    /** Best TM_CCOEFF_NORMED score of [t] anywhere in [img], and its centre. */
    private fun bestMatch(img: Gray, t: Gray): Match {
        val tMean = t.v.average()
        val tc = DoubleArray(t.v.size) { t.v[it] - tMean }
        val tNorm = sqrt(tc.sumOf { it * it })
        val n = t.v.size.toDouble()
        var best = Match(-1.0, 0, 0)
        for (oy in 0..(img.height - t.height)) {
            for (ox in 0..(img.width - t.width)) {
                var s = 0.0
                var s2 = 0.0
                var cross = 0.0
                for (ty in 0 until t.height) {
                    val row = (oy + ty) * img.width + ox
                    for (tx in 0 until t.width) {
                        val p = img.v[row + tx]
                        s += p
                        s2 += p * p
                        cross += p * tc[ty * t.width + tx]
                    }
                }
                val variance = s2 - s * s / n
                if (variance <= 1e-9) continue
                val score = cross / (sqrt(variance) * tNorm)
                if (score > best.score) best = Match(score, ox + t.width / 2, oy + t.height / 2)
            }
        }
        return best
    }

    /** The library's default confidence, which every template here keeps. */
    private val threshold = 0.8

    /** The first Shop template that matches, in the button's order, with its match. */
    private fun findShop(img: Gray): Pair<String, Match>? =
        ButtonHomeShop.templates.firstNotNullOfOrNull { t -> bestMatch(img, template(t.path)).takeIf { it.score >= threshold }?.let { t.path to it } }

    /** The badge's best score inside the band the bot reads above [shop]'s centre. */
    private fun badgeScore(img: Gray, shop: Match): Double {
        val halfW = badgeBand.getValue("SHOP_BADGE_HALF_W")
        val band = img.crop(shop.cx - halfW, shop.cy + badgeBand.getValue("SHOP_BADGE_TOP"), 2 * halfW, badgeBand.getValue("SHOP_BADGE_H"))
        return bestMatch(band, template(LabelHomeShopDailyBadge.template.path)).score
    }

    @Test
    fun `the anniversary Special Shop button is found on its cart and the Daily badge is read`() {
        val img = fixture("special_badge")
        val (path, shop) = requireNotNull(findShop(img)) { "no Shop template matched" }
        assertEquals("components/button/home_shop_special", path)
        // The cart's centre in the frame is (361, 1632); the fixture starts at (240, 1460).
        assertEquals(121 to 172, shop.cx to shop.cy)
        val badge = badgeScore(img, shop)
        assertTrue(badge >= threshold, "badge scored $badge")
    }

    @Test
    fun `the older Shop button still matches its own template first and reads the badge`() {
        val img = fixture("classic_badge")
        val (path, shop) = requireNotNull(findShop(img)) { "no Shop template matched" }
        assertEquals("components/button/home_shop", path)
        assertEquals(132 to 193, shop.cx to shop.cy)
        val badge = badgeScore(img, shop)
        assertTrue(badge >= threshold, "badge scored $badge")
    }

    @Test
    fun `with the badge gone the Shop button is still found and no badge is read`() {
        val img = fixture("classic_no_badge")
        val (_, shop) = requireNotNull(findShop(img)) { "no Shop template matched" }
        val badge = badgeScore(img, shop)
        assertTrue(badge < threshold, "badge scored $badge")
    }

    @Test
    fun `the older template alone misses the anniversary button`() {
        val score = bestMatch(fixture("special_badge"), template("components/button/home_shop")).score
        assertTrue(score < threshold, "scored $score")
    }
}
