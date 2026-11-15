package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.Training.Companion.applyContextualStatGainBoost
import com.steve1316.uma_android_automation.bot.Training.TrainingAnalysisResult
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch

/** Gains are frame-checked Unity Cup and Grand Concert reads and master.mdb base gains; stats sit far below their caps unless a test sets one. */
@DisplayName("The contextual gain correction keeps correct reads and estimates unread ones from game data")
class ContextualStatGainBoostTest {
    private fun boost(
        training: StatName,
        vararg gains: Pair<StatName, Int>,
        scenario: String = "Unity Cup",
        bars: List<CustomImageUtils.BarFillResult> = emptyList(),
        gold: Set<StatName> = emptySet(),
        stats: Map<StatName, Int> = emptyMap(),
    ): TrainingAnalysisResult {
        val result = TrainingAnalysisResult(training, CountDownLatch(0), 0L)
        result.statGains = StatName.entries.associateWith { 0 } + gains
        result.relationshipBars = ArrayList(bars)
        result.goldStatGains = gold
        applyContextualStatGainBoost(result, scenario, StatName.entries.associateWith { 300 } + stats, { 1200 })
        return result
    }

    /** A master.mdb single_mode_training_effect row (result_state 2): the training's own gain and its side gains. */
    private class GameRow(val scenario: String, val training: StatName, val main: Int, vararg val sides: Pair<StatName, Int>)

    private fun levels(scenario: String, training: StatName, side: StatName, level1: Pair<Int, Int>, level5: Pair<Int, Int>) =
        listOf(level1, level5).map { (main, sideGain) -> GameRow(scenario, training, main, side to sideGain) }

    /** Level 1 and level 5 (summer) rows of every scenario. */
    private val gameRows =
        levels("URA Finale", StatName.SPEED, StatName.POWER, 11 to 6, 15 to 8) +
            levels("URA Finale", StatName.STAMINA, StatName.GUTS, 10 to 6, 14 to 8) +
            levels("URA Finale", StatName.POWER, StatName.STAMINA, 9 to 6, 13 to 8) +
            levels("URA Finale", StatName.WIT, StatName.SPEED, 10 to 2, 14 to 4) +
            levels("Unity Cup", StatName.SPEED, StatName.POWER, 8 to 4, 12 to 6) +
            levels("Unity Cup", StatName.STAMINA, StatName.GUTS, 8 to 6, 12 to 8) +
            levels("Unity Cup", StatName.POWER, StatName.STAMINA, 9 to 4, 13 to 6) +
            levels("Unity Cup", StatName.WIT, StatName.SPEED, 6 to 2, 10 to 4) +
            levels(GrandConcertScenario.KEY, StatName.SPEED, StatName.POWER, 8 to 4, 12 to 6) +
            levels(GrandConcertScenario.KEY, StatName.STAMINA, StatName.GUTS, 8 to 6, 12 to 8) +
            levels(GrandConcertScenario.KEY, StatName.POWER, StatName.STAMINA, 9 to 4, 13 to 6) +
            levels(GrandConcertScenario.KEY, StatName.WIT, StatName.SPEED, 6 to 2, 10 to 4) +
            levels("Trackblazer", StatName.SPEED, StatName.POWER, 8 to 4, 12 to 6) +
            levels("Trackblazer", StatName.STAMINA, StatName.GUTS, 7 to 3, 11 to 5) +
            levels("Trackblazer", StatName.POWER, StatName.STAMINA, 6 to 4, 10 to 6) +
            levels("Trackblazer", StatName.WIT, StatName.SPEED, 6 to 2, 10 to 4) +
            listOf(
                GameRow("URA Finale", StatName.GUTS, 8, StatName.SPEED to 5, StatName.POWER to 5),
                GameRow("URA Finale", StatName.GUTS, 13, StatName.SPEED to 6, StatName.POWER to 5),
                GameRow("Unity Cup", StatName.GUTS, 6, StatName.SPEED to 3, StatName.POWER to 3),
                GameRow("Unity Cup", StatName.GUTS, 10, StatName.SPEED to 4, StatName.POWER to 4),
                GameRow(GrandConcertScenario.KEY, StatName.GUTS, 7, StatName.SPEED to 2, StatName.POWER to 2),
                GameRow(GrandConcertScenario.KEY, StatName.GUTS, 11, StatName.SPEED to 3, StatName.POWER to 3),
                GameRow("Trackblazer", StatName.GUTS, 6, StatName.SPEED to 3, StatName.POWER to 3),
                GameRow("Trackblazer", StatName.GUTS, 10, StatName.SPEED to 4, StatName.POWER to 4),
            )

    @Test
    @DisplayName("Guts training read right (Speed 7, Power 5, Guts 11) keeps Power 5")
    fun gutsPowerBelowSpeed() {
        val result = boost(StatName.GUTS, StatName.SPEED to 7, StatName.POWER to 5, StatName.GUTS to 11)
        assertEquals(5, result.statGains[StatName.POWER])
        assertEquals(11, result.statGains[StatName.GUTS])
        assertTrue(result.correctedStats.isEmpty())
    }

    @Test
    @DisplayName("A main gain at or below a side gain stays as read: concert side bonuses put it there on correct screens")
    fun mainAtOrBelowSide() {
        val gc = GrandConcertScenario.KEY
        val reads =
            listOf(
                boost(StatName.WIT, StatName.SPEED to 21, StatName.WIT to 21, scenario = gc) to 21,
                boost(StatName.WIT, StatName.SPEED to 21, StatName.WIT to 15, scenario = gc) to 15,
                boost(StatName.GUTS, StatName.SPEED to 11, StatName.POWER to 15, StatName.GUTS to 15, scenario = gc) to 15,
                boost(StatName.STAMINA, StatName.GUTS to 11, StatName.STAMINA to 10, scenario = gc) to 10,
            )
        for ((result, main) in reads) {
            assertEquals(main, result.statGains[result.name], "${result.name} training")
            assertTrue(result.correctedStats.isEmpty(), "${result.name} training")
        }
    }

    @Test
    @DisplayName("A small side gain beside a large main gain stays as read")
    fun smallSideBesideLargeMain() {
        val gc = GrandConcertScenario.KEY
        val reads =
            listOf(
                boost(StatName.GUTS, StatName.SPEED to 9, StatName.POWER to 8, StatName.GUTS to 29) to mapOf(StatName.SPEED to 9, StatName.POWER to 8, StatName.GUTS to 29),
                boost(StatName.GUTS, StatName.SPEED to 7, StatName.POWER to 7, StatName.GUTS to 29) to mapOf(StatName.SPEED to 7, StatName.POWER to 7, StatName.GUTS to 29),
                boost(StatName.POWER, StatName.STAMINA to 6, StatName.POWER to 27, scenario = gc) to mapOf(StatName.STAMINA to 6, StatName.POWER to 27),
                boost(StatName.POWER, StatName.STAMINA to 8, StatName.POWER to 30, scenario = gc) to mapOf(StatName.STAMINA to 8, StatName.POWER to 30),
            )
        for ((result, gains) in reads) {
            assertEquals(gains, result.statGains.filterKeys { it in gains }, "${result.name} training")
            assertTrue(result.correctedStats.isEmpty(), "${result.name} training")
        }
    }

    @Test
    @DisplayName("An unread gold main gain is estimated from game data and the log says the gold digits were not read")
    fun unreadGoldMain() {
        MessageLog.clearLog()
        val result = boost(StatName.SPEED, StatName.POWER to 20, gold = setOf(StatName.SPEED))
        assertEquals(27, result.statGains[StatName.SPEED])
        assertEquals(listOf(StatName.SPEED), result.correctedStats)
        val logged = MessageLog.getMessageLogCopy().single { "[TRAINING] SPEED training" in it }
        assertTrue(logged.contains("its gold gain digits could not be read"), logged)
    }

    @Test
    @DisplayName("An expected side gain of 0 below its cap is half the main gain; at its cap it stays 0")
    fun zeroExpectedSide() {
        val belowCap = boost(StatName.GUTS, StatName.POWER to 9, StatName.GUTS to 18)
        assertEquals(9, belowCap.statGains[StatName.SPEED])
        assertEquals(listOf(StatName.SPEED), belowCap.correctedStats)

        val atCap = boost(StatName.GUTS, StatName.POWER to 9, StatName.GUTS to 18, stats = mapOf(StatName.SPEED to 1200))
        assertEquals(0, atCap.statGains[StatName.SPEED])
        assertTrue(atCap.correctedStats.isEmpty())
    }

    @Test
    @DisplayName("An unread main gain of 0 is estimated from game data; the estimate stays above every side gain")
    fun unreadMain() {
        assertEquals(8, boost(StatName.SPEED, StatName.POWER to 6).statGains[StatName.SPEED])
        assertEquals(7, boost(StatName.STAMINA, StatName.GUTS to 6, scenario = GrandConcertScenario.KEY).statGains[StatName.STAMINA])
        assertEquals(2, boost(StatName.STAMINA, StatName.GUTS to 1, scenario = GrandConcertScenario.KEY).statGains[StatName.STAMINA])
    }

    @Test
    @DisplayName("Wit training's small Speed side is game data, not a misread")
    fun witSpeedSide() {
        val result = boost(StatName.WIT, StatName.SPEED to 6, StatName.WIT to 28)
        assertEquals(6, result.statGains[StatName.SPEED])
        assertTrue(result.correctedStats.isEmpty())
    }

    @Test
    @DisplayName("At level 1 and in summer, in every scenario, an unread main gain is never estimated above the real one")
    fun estimateNeverAboveGameData() {
        for (row in gameRows) {
            val result = boost(row.training, *row.sides, row.training to 0, scenario = row.scenario)
            val estimate = result.statGains.getValue(row.training)
            val label = "${row.scenario} ${row.training} main ${row.main} sides ${row.sides.toList()}: estimate $estimate"
            assertTrue(estimate <= row.main, label)
            assertTrue(estimate > row.sides.maxOf { it.second }, label)
        }
    }

    @Test
    @DisplayName("Support bars do not change a gain: an unread main is still estimated and logged, a read main stays as read")
    fun supportBarsDoNotChangeGain() {
        val bar = listOf(CustomImageUtils.BarFillResult(StatName.SPEED, 40.0, 2, "green"))
        MessageLog.clearLog()
        val unread = boost(StatName.SPEED, StatName.POWER to 3, bars = bar)
        val used = unread.statGains.getValue(StatName.SPEED)
        val logged = MessageLog.getMessageLogCopy().single { "[TRAINING] SPEED training" in it }
        assertTrue(logged.contains("estimated at $used ("), logged)
        assertEquals(4, used)

        assertEquals(2, boost(StatName.SPEED, StatName.POWER to 3, StatName.SPEED to 2, bars = bar).statGains[StatName.SPEED])
        assertEquals(6, boost(StatName.SPEED, StatName.POWER to 3, StatName.SPEED to 6, bars = bar).statGains[StatName.SPEED])
    }
}
