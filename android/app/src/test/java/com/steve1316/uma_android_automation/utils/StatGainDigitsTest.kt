package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.Training.Companion.trustedGoldGains
import com.steve1316.uma_android_automation.types.StatName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Real Grand Concert captures (src/test/resources/fixtures/statgain/PROVENANCE.md); each fixture is the stat-gain band of one analysis frame. */
@DisplayName("Training stat-gain row reader on real captures")
class StatGainDigitsTest {
    private val cache = mutableMapOf<String, FixturePng>()

    private fun sampler(name: String): SparkPixelSampler {
        val img =
            cache.getOrPut(name) {
                val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/statgain/$name.png")) { "missing fixture $name.png" }
                stream.use { FixturePng.read(it) }
            }
        return SparkPixelSampler { x, y -> img.getRGB(x, y) }
    }

    private val anchorX = 971
    private val anchorY = 134

    /** Row 1 is the bonus row above the gain row. */
    private fun read(name: String, stat: StatName, row: Int): StatGainDigits.Row? {
        val left = anchorX + StatGainDigits.FIRST_COLUMN_CENTER_FROM_SKILL_POINTS + stat.ordinal * StatGainDigits.COLUMN_PITCH - StatGainDigits.COLUMN_WIDTH / 2
        val rowStartY = if (row == 0) anchorY - 65 else anchorY - 65 - 55
        return StatGainDigits.readRow(sampler(name), left, rowStartY - 5, StatGainDigits.COLUMN_WIDTH, 55 + 5)
    }

    private fun assertGold(expected: Int, actual: StatGainDigits.Row?) {
        assertNotNull(actual)
        assertTrue(actual!!.gold, "row should be gold")
        assertEquals(expected, actual.value)
    }

    @Test
    @DisplayName("Gold double-chevron Speed gains read on both rows")
    fun goldSpeedRows() {
        assertGold(31, read("gold_speed_1281", StatName.SPEED, 0))
        assertGold(16, read("gold_speed_1281", StatName.SPEED, 1))
        assertGold(32, read("gold_speed_1169", StatName.SPEED, 0))
        assertGold(6, read("gold_speed_1169", StatName.SPEED, 1))
        assertGold(16, read("gold_speed_1221", StatName.SPEED, 0))
        assertGold(8, read("gold_speed_1221", StatName.SPEED, 1))
        assertGold(36, read("gold_speed_1166", StatName.SPEED, 0))
        assertGold(7, read("gold_speed_1166", StatName.SPEED, 1))
    }

    @Test
    @DisplayName("A gold read equals the stat change the next turn showed")
    fun goldReadMatchesNextStatChange() {
        val total = listOf(0, 1).sumOf { StatGainDigits.resolveRowValue(0, read("gold_speed_1218", StatName.SPEED, it)) }
        assertEquals(19, total)
    }

    @Test
    @DisplayName("Orange and red rows next to a gold one are read but not flagged gold")
    fun orangeRowsAreNotGold() {
        val power = read("gold_speed_1281", StatName.POWER, 0)
        assertEquals(31, power?.value)
        assertFalse(power!!.gold)
        assertEquals(17, read("gold_speed_1281", StatName.POWER, 1)?.value)
        assertEquals(10, read("gold_speed_1221", StatName.POWER, 1)?.value)
        // Speed 1169 + 3 stays below 1200, so the game keeps it orange.
        assertFalse(read("orange_speed_1169_guts", StatName.SPEED, 0)!!.gold)
        assertFalse(read("orange_speed_1169_guts", StatName.SPEED, 1)!!.gold)
    }

    @Test
    @DisplayName("Background art inside the outline between digits does not break the read")
    fun artBetweenDigits() {
        assertEquals(10, read("wit_art_between_digits", StatName.WIT, 0)?.value)
    }

    @Test
    @DisplayName("A column with no gain glyph reads as absent")
    fun emptyColumn() {
        for (row in 0..1) {
            assertNull(read("gold_speed_1281", StatName.STAMINA, row), "row $row")
            assertNull(read("gold_speed_1281", StatName.GUTS, row), "row $row")
        }
    }

    @Test
    @DisplayName("Only a gold row replaces the template read")
    fun resolveRowValue() {
        assertEquals(31, StatGainDigits.resolveRowValue(0, StatGainDigits.Row(31, gold = true)))
        assertEquals(12, StatGainDigits.resolveRowValue(12, StatGainDigits.Row(13, gold = false)))
        assertEquals(12, StatGainDigits.resolveRowValue(12, null))
        assertEquals(0, StatGainDigits.resolveRowValue(9, StatGainDigits.Row(null, gold = true)))
    }

    @Test
    @DisplayName("Only gold gains that read are exempt from the OCR-failure corrections")
    fun trustedGold() {
        val gains = mapOf(StatName.SPEED to 19, StatName.POWER to 20, StatName.GUTS to 0)
        assertEquals(setOf(StatName.SPEED), trustedGoldGains(gains, setOf(StatName.SPEED, StatName.GUTS)))
        assertEquals(emptySet<StatName>(), trustedGoldGains(gains, emptySet()))
    }
}
