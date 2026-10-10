package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Daily Races templates and places on the phone captures (1080x2316, cutout inset 94; see fixtures/dailyraces/PROVENANCE.md). */
@DisplayName("Daily Races on a tall phone")
class DailyRaceProbesFixtureTest {
    private val w = 1080
    private val h = 2316
    private var savedInset = 0

    @BeforeEach
    fun phoneInset() {
        savedInset = screenTopInset
        screenTopInset = 94
    }

    @AfterEach
    fun restoreInset() {
        screenTopInset = savedInset
    }

    private val fixtures =
        listOf(
            "race_tab", "daily_program", "daily_program_0", "daily_races_6", "daily_races_0", "difficulty", "race_details",
            "runner_selection", "multi_race_popup", "race_result", "total_rewards", "daily_sale",
        )

    private val cache = mutableMapOf<String, FixturePng>()

    private fun image(name: String): FixturePng =
        cache.getOrPut(name) {
            val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/dailyraces/$name.png")) { "missing fixture $name.png" }
            stream.use { FixturePng.read(it) }
        }

    private fun template(path: String): FixturePng {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (candidate in listOf("src/main/assets/images/components/$path.png", "android/app/src/main/assets/images/components/$path.png")) {
                val file = File(dir, candidate)
                if (file.isFile) return file.inputStream().use { FixturePng.read(it) }
            }
            dir = dir.parentFile
        }
        error("template $path not found")
    }

    /** The matcher's grayscale: the library converts RGBA with BGR2GRAY, so red and blue weights are swapped. */
    private fun gray(argb: Int): Double = 0.114 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.299 * (argb and 0xFF)

    private class Match(val score: Double, val cx: Int, val cy: Int)

    /** Best TM_CCOEFF_NORMED score of [tmpl] placed fully inside the window [x0, x1) x [y0, y1) of [img]. */
    private fun bestMatch(
        img: FixturePng,
        tmpl: FixturePng,
        window: IntArray,
    ): Match {
        val (x0, y0, x1, y1) = window.toList()
        val tw = tmpl.width
        val th = tmpl.height
        val t = DoubleArray(tw * th) { gray(tmpl.getRGB(it % tw, it / tw)) }
        val tMean = t.average()
        for (i in t.indices) t[i] -= tMean
        val tNorm = sqrt(t.sumOf { it * it })
        val ww = x1 - x0
        val src = DoubleArray(ww * (y1 - y0)) { gray(img.getRGB(x0 + it % ww, y0 + it / ww)) }
        var best = Match(-1.0, 0, 0)
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
                if (score > best.score) best = Match(score, x0 + ox + tw / 2, y0 + oy + th / 2)
            }
        }
        return best
    }

    @Test
    fun `every fixture is the phone capture size`() {
        for (name in fixtures) {
            assertEquals(w, image(name).width, name)
            assertEquals(h, image(name).height, name)
        }
    }

    @Nested
    @DisplayName("New templates")
    inner class Templates {
        /** Template, the fixture window it is searched in (the kept area), the screens it belongs to, its centre there. */
        private fun check(
            path: String,
            window: IntArray,
            own: Map<String, Pair<Int, Int>>,
        ) {
            val tmpl = template(path)
            for (name in fixtures) {
                val m = bestMatch(image(name), tmpl, window)
                val expected = own[name]
                if (expected != null) {
                    assertTrue(m.score >= 0.95, "$path on $name scored ${m.score}")
                    assertTrue(abs(m.cx - expected.first) <= 3 && abs(m.cy - expected.second) <= 3, "$path on $name at (${m.cx}, ${m.cy})")
                } else {
                    assertTrue(m.score < 0.8, "$path matched $name at ${m.score}")
                }
            }
        }

        @Test
        fun `Daily Program label on the Race tab tile`() = check("button/daily_program_tile", intArrayOf(132, 1950, 502, 2086), mapOf("race_tab" to (317 to 2018)))

        @Test
        fun `Daily Races label on its tile, with tickets and at 0`() =
            check("button/daily_races", intArrayOf(152, 1836, 482, 1962), mapOf("daily_program" to (317 to 1899), "daily_program_0" to (317 to 1899)))

        @Test
        fun `Race Details header`() = check("label/race_details_header", intArrayOf(378, 228, 702, 354), mapOf("race_details" to (540 to 291)))

        @Test
        fun `Race Results header on a result and on Total Rewards, not on Race Details`() =
            check("label/race_results_header", intArrayOf(378, 228, 702, 354), mapOf("race_result" to (540 to 291), "total_rewards" to (540 to 291)))

        @Test
        fun `Complete under a race result, not the Total Rewards Close`() = check("button/complete", intArrayOf(400, 1906, 678, 2044), mapOf("race_result" to (539 to 1975)))
    }

    @Test
    fun `Race Details pill reads Multi-Race On, not Off`() {
        val window = intArrayOf(380, 1800, 800, 1936)
        assertTrue(bestMatch(image("race_details"), template("button/multi_race_on"), window).score >= 0.8)
        assertTrue(bestMatch(image("race_details"), template("button/multi_race_off"), window).score < 0.8)
    }

    @Nested
    @DisplayName("Ticket counter")
    inner class TicketCounter {
        private fun isDigit(argb: Int): Boolean {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val brown = r in 61..159 && g < 110 && b < 80 && r - b > 30
            val orange = r > 220 && g in 111..189 && b < 80
            return brown || orange
        }

        @Test
        fun `the OCR region holds every digit of the header counter`() {
            val x0 = DailyRaceGeometry.TICKET_OCR_X
            val y0 = gameY(DailyRaceGeometry.TICKET_OCR_Y.toDouble(), ScreenBand.TOP, w, h).roundToInt()
            val x1 = x0 + DailyRaceGeometry.TICKET_OCR_W
            val y1 = y0 + DailyRaceGeometry.TICKET_OCR_H
            for (name in listOf("daily_races_6", "daily_races_0", "difficulty")) {
                val img = image(name)
                var inside = 0
                for (y in 90 until 180) {
                    for (x in 880 until 1070) {
                        if (!isDigit(img.getRGB(x, y))) continue
                        assertTrue(x in x0 until x1 && y in y0 until y1, "$name digit pixel ($x, $y) outside the OCR region")
                        inside++
                    }
                }
                assertTrue(inside > 300, "$name: $inside digit pixels")
            }
        }

        @Test
        fun `counts parse, with an event's doubled tickets and OCR's letter O`() {
            assertEquals(6 to 6, parseTicketCount("6/6"))
            assertEquals(0 to 6, parseTicketCount("0/6"))
            assertEquals(0 to 3, parseTicketCount(" 0 / 3 "))
            assertEquals(0 to 6, parseTicketCount("O/6"))
            assertEquals(2 to 3, parseTicketCount("DAILY 2/3"))
        }

        @Test
        fun `anything that is not a whole count is unreadable`() {
            assertNull(parseTicketCount(""))
            assertNull(parseTicketCount("6"))
            assertNull(parseTicketCount("7/6"))
            assertNull(parseTicketCount("1/0"))
            // Never a count cut out of a longer number: "100/6" is not 0 of 6.
            assertNull(parseTicketCount("100/6"))
            assertNull(parseTicketCount("6/120"))
        }
    }

    @Test
    fun `popup Race tap lands on the green Race button, and on 1920 stays the older tap`() {
        val y = gameY(DailyRaceGeometry.POPUP_RACE_Y.toDouble(), ScreenBand.DIALOG, w, h).roundToInt()
        assertEquals(1448, y)
        val x = DailyRaceGeometry.POPUP_RACE_X
        fun green(
            px: Int,
            py: Int,
        ): Boolean {
            val p = image("multi_race_popup").getRGB(px, py)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            return g >= 150 && g - b >= 80 && g >= r
        }
        // The button is green from y 1398 to 1498 and x 582 to 972; its label and "Consumes n" sit inside.
        for ((px, py) in listOf(x to y, x - 180 to y, x + 180 to y, x to y - 45, x to y + 50)) assertTrue(green(px, py), "not button green at ($px, $py)")
        for ((px, py) in listOf(x to y - 60, x to y + 60, x - 210 to y, x + 210 to y)) assertTrue(!green(px, py), "button green outside the button at ($px, $py)")
        assertEquals(1250.0, gameY(DailyRaceGeometry.POPUP_RACE_Y.toDouble(), ScreenBand.DIALOG, 1080, 1920))
    }

    @Nested
    @DisplayName("Difficulty rows")
    inner class DifficultyRows {
        private fun isWhite(argb: Int): Boolean {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            return minOf(r, g, b) >= 225 && maxOf(r, g, b) - minOf(r, g, b) <= 20
        }

        @Test
        fun `settings map to rows, unknown to Very Hard`() {
            assertEquals(0, difficultyRow("VERY_HARD"))
            assertEquals(1, difficultyRow("HARD"))
            assertEquals(2, difficultyRow("normal"))
            assertEquals(3, difficultyRow("EASY"))
            assertEquals(0, difficultyRow("LUNATIC"))
        }

        @Test
        fun `all four rows show on the phone, each tap inside its card`() {
            val img = image("difficulty")
            val taps = (0..3).map { requireNotNull(difficultyRowTapY(it, w, h)).roundToInt() }
            assertEquals(listOf(1137, 1355, 1573, 1791), taps)
            for (y in taps) {
                // Card body around the tap: white except the 2-3 px divider line under the course name.
                val breaks = (y - 80..y + 80).count { !isWhite(img.getRGB(1000, it)) }
                assertTrue(breaks <= 4, "tap $y: $breaks non-white rows in the card body")
                assertTrue((y + 85..y + 125).any { !isWhite(img.getRGB(1000, it)) }, "tap $y: no card edge below")
            }
        }

        @Test
        fun `on 1080x1920 the rows sit by the older taps, and Easy is past the list bottom`() {
            val taps = (0..2).map { requireNotNull(difficultyRowTapY(it, 1080, 1920)) }
            assertEquals(listOf(1043.0, 1261.0, 1479.0), taps)
            for ((tap, older) in taps.zip(listOf(1018.0, 1248.0, 1478.0))) assertTrue(abs(tap - older) <= 25.0)
            assertNull(difficultyRowTapY(3, 1080, 1920))
        }
    }
}
