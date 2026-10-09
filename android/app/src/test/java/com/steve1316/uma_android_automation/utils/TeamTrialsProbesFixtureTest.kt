package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Team Trials probes on the phone captures (1080x2316, cutout inset 94; see fixtures/teamtrials/PROVENANCE.md). */
@DisplayName("Team Trials probes on a tall phone")
class TeamTrialsProbesFixtureTest {
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

    private val cache = mutableMapOf<String, FixturePng>()

    private fun image(name: String): FixturePng =
        cache.getOrPut(name) {
            val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/teamtrials/$name.png")) { "missing fixture $name.png" }
            stream.use { FixturePng.read(it) }
        }

    private fun sampler(name: String): SparkPixelSampler {
        val img = image(name)
        return SparkPixelSampler { x, y -> img.getRGB(x.coerceIn(0, img.width - 1), y.coerceIn(0, img.height - 1)) }
    }

    private val fixtures =
        listOf(
            "select_opponent", "select_opponent_race_again", "team_trials_home_rp5", "team_trials_home_rp4", "team_trials_home_rp3",
            "team_trials_home_rp2", "team_trials_home_rp0", "home_rp0", "race_tab_rp5", "team_preview", "items_selected", "standby",
            "result_splash", "race_finished", "winnings",
        )

    @Test
    fun `every fixture is the phone capture size`() {
        for (name in fixtures) {
            assertEquals(w, image(name).width, name)
            assertEquals(h, image(name).height, name)
        }
    }

    @Nested
    @DisplayName("Select Opponent cards")
    inner class OpponentCards {
        /** Rows measured on the phone: TOP 600-960, MIDDLE 1000-1365, BOTTOM 1410-1770. */
        private val rows = listOf(600..960, 1000..1365, 1410..1770)

        @Test
        fun `the three cards are found inside their measured rows`() {
            for (name in listOf("select_opponent", "select_opponent_race_again")) {
                val centres = requireNotNull(findOpponentCardCentres(sampler(name), w, h)) { name }
                assertEquals(3, centres.size, name)
                centres.zip(rows).forEach { (c, row) -> assertTrue(c in row, "$name centre $c outside $row") }
            }
        }

        @Test
        fun `no other Team Trials screen reads as Select Opponent`() {
            for (name in fixtures - listOf("select_opponent", "select_opponent_race_again")) {
                assertNull(findOpponentCardCentres(sampler(name), w, h), name)
            }
        }

        @Test
        fun `the scan does not depend on the band, so a shorter screen keeps the same cards`() {
            // The frame moved up by the MIDDLE offset (245 px) and read as a 1920-tall screen.
            val tall = sampler("select_opponent")
            val cut = SparkPixelSampler { x, y -> tall.argb(x, y + 245) }
            val centres = requireNotNull(findOpponentCardCentres(cut, w, 1920))
            assertEquals(findOpponentCardCentres(tall, w, h)!!.map { it - 245 }, centres)
        }
    }

    @Nested
    @DisplayName("RP pips")
    inner class RpPips {
        @Test
        fun `the pips read the RP the counter shows`() {
            val expected =
                mapOf(
                    "team_trials_home_rp5" to 5, "team_trials_home_rp4" to 4, "team_trials_home_rp3" to 3, "team_trials_home_rp2" to 2,
                    "team_trials_home_rp0" to 0, "home_rp0" to 0, "race_tab_rp5" to 5, "select_opponent" to 5,
                )
            for ((name, rp) in expected) assertEquals(rp, readRpPips(sampler(name), w, h), name)
        }

        @Test
        fun `screens without the top bar do not read as an RP count`() {
            for (name in listOf("standby", "result_splash", "race_finished", "winnings", "items_selected")) {
                assertNull(readRpPips(sampler(name), w, h), name)
            }
        }

        @Test
        fun `without the cutout inset the pips are missed and refused`() {
            screenTopInset = 0
            assertNull(readRpPips(sampler("team_trials_home_rp5"), w, h))
        }
    }

    @Nested
    @DisplayName("Quick Mode pill")
    inner class QuickMode {
        @Test
        fun `the standby pill reads ON`() {
            assertTrue(quickModePillOn(sampler("standby"), w, h))
        }

        @Test
        fun `screens without the pill do not read ON`() {
            // The race field is green too, which is why the task reads the pill only on the standby screen.
            for (name in listOf("result_splash", "race_finished", "winnings")) {
                assertFalse(quickModePillOn(sampler(name), w, h), name)
            }
        }
    }

    @Nested
    @DisplayName("tap places")
    inner class TapPlaces {
        @Test
        fun `1080x1920 keeps the measured 1920 places`() {
            assertEquals(1362.0, gameY(TeamTrialsGeometry.ITEMS_RACE_Y.toDouble(), ScreenBand.DIALOG, 1080, 1920))
            assertEquals(1775.0, gameY(TeamTrialsGeometry.SEE_ALL_Y.toDouble(), ScreenBand.BOTTOM, 1080, 1920))
            assertEquals(1584.0, gameY(TeamTrialsGeometry.SPLASH_TAP_Y.toDouble(), ScreenBand.BOTTOM, 1080, 1920))
        }

        @Test
        fun `on the phone each place lands on the button measured there`() {
            // Items Selected Race! was tapped at (771, 1560); See All spans y 2090-2250; the splash TAP prompt sits at y 1980.
            assertEquals(1560.0, gameY(TeamTrialsGeometry.ITEMS_RACE_Y.toDouble(), ScreenBand.DIALOG, w, h), 1.0)
            assertTrue(gameY(TeamTrialsGeometry.SEE_ALL_Y.toDouble(), ScreenBand.BOTTOM, w, h) in 2090.0..2250.0)
            assertEquals(1980.0, gameY(TeamTrialsGeometry.SPLASH_TAP_Y.toDouble(), ScreenBand.BOTTOM, w, h), 1.0)
        }
    }

    @Test
    fun `the RP counter parses only a whole count out of 5`() {
        assertEquals(3, parseRpCount("3/5"))
        assertEquals(0, parseRpCount(" 0 / 5 "))
        assertEquals(5, parseRpCount("1:49:09 5/5"))
        assertNull(parseRpCount(""))
        assertNull(parseRpCount("55/100"))
        assertNull(parseRpCount("7/5"))
    }
}
