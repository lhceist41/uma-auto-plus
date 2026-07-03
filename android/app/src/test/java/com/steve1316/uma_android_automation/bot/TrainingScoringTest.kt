package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.Training.Companion.calculateMiscScore
import com.steve1316.uma_android_automation.bot.Training.Companion.calculateRawTrainingScore
import com.steve1316.uma_android_automation.bot.Training.Companion.calculateRelationshipScore
import com.steve1316.uma_android_automation.bot.Training.Companion.admitIrregularTraining
import com.steve1316.uma_android_automation.bot.Training.Companion.calculateStatEfficiencyScore
import com.steve1316.uma_android_automation.bot.Training.Companion.getFinaleStatBonus
import com.steve1316.uma_android_automation.bot.Training.Companion.getRemainingFinaleRaces
import com.steve1316.uma_android_automation.bot.Training.Companion.getScenarioStatCap
import com.steve1316.uma_android_automation.bot.Training.Companion.scoreFriendshipTraining
import com.steve1316.uma_android_automation.bot.Training.Companion.scoreUnityCupTraining
import com.steve1316.uma_android_automation.bot.Training.Companion.selectBestTrainingWithHintPriority
import com.steve1316.uma_android_automation.bot.Training.TrainingConfig
import com.steve1316.uma_android_automation.bot.Training.TrainingOption
import com.steve1316.uma_android_automation.types.DateMonth
import com.steve1316.uma_android_automation.types.DatePhase
import com.steve1316.uma_android_automation.types.DateYear
import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.utils.CustomImageUtils.BarFillResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Unit tests for the Training scoring functions.
 *
 * These tests verify the correctness of the scoring algorithms used to determine
 * the best training option based on various game state configurations.
 */
@DisplayName("Training Scoring Tests")
class TrainingScoringTest {
    /**
     * Returns the stat targets Map for the given distance.
     * Order: Speed, Stamina, Power, Guts, Wit
     *
     * @param distance The distance string: "Sprint", "Mile", "Medium", or "Long".
     *
     * @return Map<StatName, Int> of stat targets for that distance.
     */
    private fun getStatTargetsForDistance(distance: String): Map<StatName, Int> {
        val targets =
            when (distance) {
                "Sprint" -> intArrayOf(900, 300, 600, 300, 300)
                "Mile" -> intArrayOf(900, 300, 600, 300, 300)
                "Medium" -> intArrayOf(800, 450, 550, 300, 300)
                "Long" -> intArrayOf(700, 600, 450, 300, 300)
                else -> intArrayOf(600, 600, 600, 300, 300)
            }
        return mapOf(
            StatName.SPEED to targets[0],
            StatName.STAMINA to targets[1],
            StatName.POWER to targets[2],
            StatName.GUTS to targets[3],
            StatName.WIT to targets[4],
        )
    }

    /**
     * Converts an IntArray of stat gains (Speed, Stamina, Power, Guts, Wit) to a Map<StatName, Int>.
     *
     * @param gains The stat gains as an IntArray.
     *
     * @return Map<StatName, Int> of stat gains.
     */
    private fun statGainsToMap(gains: IntArray): Map<StatName, Int> {
        return mapOf(
            StatName.SPEED to gains[0],
            StatName.STAMINA to gains[1],
            StatName.POWER to gains[2],
            StatName.GUTS to gains[3],
            StatName.WIT to gains[4],
        )
    }

    /**
     * Converts a Map<String, Int> of stats to a Map<StatName, Int>.
     *
     * @param stats The stats as a Map<String, Int>.
     *
     * @return Map<StatName, Int> of stats.
     */
    private fun statsToMap(stats: Map<String, Int>): Map<StatName, Int> {
        return mapOf(
            StatName.SPEED to (stats["Speed"] ?: 0),
            StatName.STAMINA to (stats["Stamina"] ?: 0),
            StatName.POWER to (stats["Power"] ?: 0),
            StatName.GUTS to (stats["Guts"] ?: 0),
            StatName.WIT to (stats["Wit"] ?: 0),
        )
    }

    // Helper function to create a default TrainingOption for testing.
    private fun createDefaultTrainingOption(
        name: StatName = StatName.SPEED,
        statGains: Map<StatName, Int> = statGainsToMap(intArrayOf(15, 0, 5, 0, 0)),
        failureChance: Int = 5,
        relationshipBars: ArrayList<BarFillResult> = arrayListOf(),
        numRainbow: Int = 0,
        numSpiritGaugesCanFill: Int = 0,
        numSpiritGaugesReadyToBurst: Int = 0,
        numSkillHints: Int = 0,
    ): TrainingOption {
        return TrainingOption(
            name = name,
            statGains = statGains,
            failureChance = failureChance,
            relationshipBars = relationshipBars,
            numRainbow = numRainbow,
            numSpiritGaugesCanFill = numSpiritGaugesCanFill,
            numSpiritGaugesReadyToBurst = numSpiritGaugesReadyToBurst,
            numSkillHints = numSkillHints,
        )
    }

    // Helper function to create a default TrainingConfig for testing.
    private fun createDefaultConfig(
        trainingOptions: List<TrainingOption> = listOf(createDefaultTrainingOption()),
        currentStats: Map<StatName, Int> =
            mapOf(
                StatName.SPEED to 120,
                StatName.STAMINA to 120,
                StatName.POWER to 120,
                StatName.GUTS to 120,
                StatName.WIT to 120,
            ),
        statPrioritization: List<StatName> = listOf(StatName.SPEED, StatName.STAMINA, StatName.POWER, StatName.WIT, StatName.GUTS),
        preferredDistance: String = "Medium",
        currentDate: GameDate = GameDate(year = DateYear.JUNIOR, month = DateMonth.JANUARY, phase = DatePhase.EARLY),
        scenario: String = "URA Finale",
        enableRainbowTrainingBonus: Boolean = true,
        focusOnSparkStatTarget: List<StatName> = emptyList(),
        blacklist: List<StatName?> = emptyList(),
        disableTrainingOnMaxedStat: Boolean = false,
        skillHintsPerLocation: Map<StatName, Int> = StatName.entries.associateWith { 0 },
        enablePrioritizeSkillHints: Boolean = false,
        statsTrainedOverBuffer: Set<StatName> = emptySet(),
    ): TrainingConfig {
        return TrainingConfig(
            currentStats = currentStats,
            statPrioritization = statPrioritization,
            statTargets = getStatTargetsForDistance(preferredDistance),
            currentDate = currentDate,
            scenario = scenario,
            enableRainbowTrainingBonus = enableRainbowTrainingBonus,
            focusOnSparkStatTarget = focusOnSparkStatTarget,
            blacklist = blacklist,
            disableTrainingOnMaxedStat = disableTrainingOnMaxedStat,
            trainingOptions = trainingOptions,
            skillHintsPerLocation = skillHintsPerLocation,
            enablePrioritizeSkillHints = enablePrioritizeSkillHints,
            statsTrainedOverBuffer = statsTrainedOverBuffer,
        )
    }

    // Helper: a relationship bar of a given color and fill level (statName/segments irrelevant to admission).
    private fun bar(color: String, fill: Double): BarFillResult = BarFillResult(StatName.SPEED, fill, 2, color)

    @Test
    @DisplayName("Anticipatory rainbow multiplier applies in Year 2+ with near-max bars and no real rainbow")
    fun testAnticipatoryRainbowMultiplier() {
        val classicDate = GameDate(year = DateYear.CLASSIC, month = DateMonth.JANUARY, phase = DatePhase.EARLY)
        val training = createDefaultTrainingOption(relationshipBars = arrayListOf(bar("green", 80.0), bar("blue", 50.0)))
        val configOn = createDefaultConfig(currentDate = classicDate)
        val configOff = configOn.copy(enablePrioritizeNearMaxFriendship = false)

        // contributions = 0.8 + 0.5 = 1.3 -> multiplier = 1.0 + min(0.6, 0.2 * 1.3) = 1.26.
        val scoreOn = calculateRawTrainingScore(configOn, training)
        val scoreOff = calculateRawTrainingScore(configOff, training)
        assertTrue(scoreOff > 0.0)
        assertEquals(scoreOff * 1.26, scoreOn, 0.01)

        // Junior year: never applies even when enabled.
        val juniorConfig = createDefaultConfig()
        assertEquals(
            calculateRawTrainingScore(juniorConfig.copy(enablePrioritizeNearMaxFriendship = false), training),
            calculateRawTrainingScore(juniorConfig, training),
            0.001,
        )

        // A real rainbow suppresses anticipation - the 2.0x real multiplier owns that case.
        val rainbowTraining = createDefaultTrainingOption(relationshipBars = arrayListOf(bar("green", 80.0)), numRainbow = 1)
        assertEquals(
            calculateRawTrainingScore(configOn.copy(enablePrioritizeNearMaxFriendship = false), rainbowTraining),
            calculateRawTrainingScore(configOn, rainbowTraining),
            0.001,
        )
    }

    @Test
    @DisplayName("Irregular C1: a strong main-stat gain is admitted on its own")
    fun testIrregularC1AdmitsStrongGain() {
        val r = admitIrregularTraining(StatName.SPEED, statGainsToMap(intArrayOf(21, 0, 9, 0, 0)), emptyList(), 0, 0, baseline = 20, year = DateYear.CLASSIC, day = 26, currentMainStat = 400)
        assertNotNull(r)
        assertTrue(r!!.startsWith("C1"))
    }

    @Test
    @DisplayName("Irregular: a weak gain with no secondary value is not admitted")
    fun testIrregularRejectsWeakGain() {
        assertNull(admitIrregularTraining(StatName.SPEED, statGainsToMap(intArrayOf(12, 0, 0, 0, 0)), emptyList(), 0, 0, baseline = 20, year = DateYear.CLASSIC, day = 26, currentMainStat = 400))
    }

    @Test
    @DisplayName("Irregular C2: a mid gain plus a rainbow is admitted")
    fun testIrregularC2AdmitsWithRainbow() {
        val r = admitIrregularTraining(StatName.SPEED, statGainsToMap(intArrayOf(14, 0, 0, 0, 0)), emptyList(), 1, 0, baseline = 20, year = DateYear.CLASSIC, day = 26, currentMainStat = 400)
        assertNotNull(r)
        assertTrue(r!!.startsWith("C2"))
    }

    @Test
    @DisplayName("Irregular C2: a mid gain with no secondary value is rejected")
    fun testIrregularC2RejectsNoSecondary() {
        assertNull(admitIrregularTraining(StatName.SPEED, statGainsToMap(intArrayOf(14, 0, 0, 0, 0)), emptyList(), 0, 0, baseline = 20, year = DateYear.CLASSIC, day = 26, currentMainStat = 400))
    }

    @Test
    @DisplayName("Irregular C2: a blue bond bar below 80% counts as secondary value")
    fun testIrregularC2AdmitsBuildableBond() {
        val r = admitIrregularTraining(StatName.SPEED, statGainsToMap(intArrayOf(13, 0, 0, 0, 0)), listOf(bar("blue", 50.0)), 0, 0, baseline = 20, year = DateYear.CLASSIC, day = 26, currentMainStat = 400)
        assertNotNull(r)
        assertTrue(r!!.startsWith("C2"))
    }

    @Test
    @DisplayName("Irregular: a bond bar at/above 80% does not count (near-rainbow, low build value)")
    fun testIrregularBondAtCapNotSecondary() {
        assertNull(admitIrregularTraining(StatName.SPEED, statGainsToMap(intArrayOf(13, 0, 0, 0, 0)), listOf(bar("blue", 85.0)), 0, 0, baseline = 20, year = DateYear.CLASSIC, day = 26, currentMainStat = 400))
    }

    @Test
    @DisplayName("Irregular: an orange (rainbow-achieved) bar does not count as buildable")
    fun testIrregularOrangeBarNotBuildable() {
        assertNull(admitIrregularTraining(StatName.SPEED, statGainsToMap(intArrayOf(13, 0, 0, 0, 0)), listOf(bar("orange", 50.0)), 0, 0, baseline = 20, year = DateYear.CLASSIC, day = 26, currentMainStat = 400))
    }

    @Test
    @DisplayName("Irregular C3: a below-floor Stamina is rescued by a real gain")
    fun testIrregularC3RescuesLowStamina() {
        val r = admitIrregularTraining(StatName.STAMINA, statGainsToMap(intArrayOf(0, 13, 0, 0, 0)), emptyList(), 0, 0, baseline = 20, year = DateYear.SENIOR, day = 70, currentMainStat = 500)
        assertNotNull(r)
        assertTrue(r!!.startsWith("C3"))
    }

    @Test
    @DisplayName("Irregular C3: a Stamina above the floor is not rescued")
    fun testIrregularC3NoRescueAboveFloor() {
        assertNull(admitIrregularTraining(StatName.STAMINA, statGainsToMap(intArrayOf(0, 13, 0, 0, 0)), emptyList(), 0, 0, baseline = 20, year = DateYear.SENIOR, day = 70, currentMainStat = 700))
    }

    @Test
    @DisplayName("Irregular phase curve: the same gain admits in early Classic but not late Senior")
    fun testIrregularPhaseCurve() {
        val gains = statGainsToMap(intArrayOf(22, 0, 0, 0, 0))
        // Early Classic T_base = 20-3 = 17, so 22 is admitted (C1).
        assertNotNull(admitIrregularTraining(StatName.SPEED, gains, emptyList(), 0, 0, baseline = 20, year = DateYear.CLASSIC, day = 26, currentMainStat = 400))
        // Late Senior T_base = 20+5 = 25, so 22 is rejected (no secondary, no rescue).
        assertNull(admitIrregularTraining(StatName.SPEED, gains, emptyList(), 0, 0, baseline = 20, year = DateYear.SENIOR, day = 70, currentMainStat = 400))
    }

    @Test
    @DisplayName("Speed rainbow training should be selected despite high current stat")
    fun testSpeedRainbowTrainingSelectedWithHighStats() {
        // Current stats with Speed already at 1100.
        val currentStats =
            mapOf(
                StatName.SPEED to 1100,
                StatName.STAMINA to 700,
                StatName.POWER to 800,
                StatName.GUTS to 400,
                StatName.WIT to 300,
            )

        val speedTraining =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(60, 0, 30, 0, 0)),
                numRainbow = 1,
            )
        val staminaTraining =
            createDefaultTrainingOption(
                name = StatName.STAMINA,
                statGains = statGainsToMap(intArrayOf(0, 15, 0, 7, 0)),
                numRainbow = 0,
            )
        val powerTraining =
            createDefaultTrainingOption(
                name = StatName.POWER,
                statGains = statGainsToMap(intArrayOf(0, 25, 45, 0, 0)),
                numRainbow = 1,
            )
        val gutsTraining =
            createDefaultTrainingOption(
                name = StatName.GUTS,
                statGains = statGainsToMap(intArrayOf(0, 5, 0, 10, 0)),
                numRainbow = 0,
            )
        val witTraining =
            createDefaultTrainingOption(
                name = StatName.WIT,
                statGains = statGainsToMap(intArrayOf(5, 0, 0, 0, 10)),
                numRainbow = 0,
            )

        val trainingOptions = listOf(speedTraining, staminaTraining, powerTraining, gutsTraining, witTraining)

        val config =
            createDefaultConfig(
                trainingOptions = trainingOptions,
                currentStats = currentStats,
                preferredDistance = "Medium",
                currentDate = GameDate(year = DateYear.CLASSIC, month = DateMonth.JUNE, phase = DatePhase.EARLY),
                enableRainbowTrainingBonus = true,
            )

        // Speed training should have the highest score due to rainbow bonus.
        val scores = trainingOptions.associateWith { calculateRawTrainingScore(config, it) }
        val bestTraining = scores.maxByOrNull { it.value }?.key
        assertEquals(StatName.SPEED, bestTraining?.name, "Speed rainbow training should be selected despite high current stat")
        assertTrue(scores[speedTraining]!! > 0, "Speed training score should be positive")
    }

    // ============================================================================
    // scoreFriendshipTraining Tests
    // ============================================================================

    @Test
    @DisplayName("Blue and green bars are prioritized with priority order blue > green > orange")
    fun testBarColorPriority() {
        // Blue bar should contribute most, green next, orange nothing.
        val blueBar = BarFillResult(statName = StatName.SPEED, fillPercent = 50.0, filledSegments = 2, dominantColor = "blue")
        val greenBar = BarFillResult(statName = StatName.SPEED, fillPercent = 50.0, filledSegments = 2, dominantColor = "green")
        val orangeBar = BarFillResult(statName = StatName.SPEED, fillPercent = 50.0, filledSegments = 2, dominantColor = "orange")

        val trainingWithBlue =
            createDefaultTrainingOption(
                relationshipBars = arrayListOf(blueBar),
            )
        val trainingWithGreen =
            createDefaultTrainingOption(
                relationshipBars = arrayListOf(greenBar),
            )
        val trainingWithOrange =
            createDefaultTrainingOption(
                relationshipBars = arrayListOf(orangeBar),
            )

        val blueScore = scoreFriendshipTraining(trainingWithBlue)
        val greenScore = scoreFriendshipTraining(trainingWithGreen)
        val orangeScore = scoreFriendshipTraining(trainingWithOrange)

        // Verify priority order: blue > green > orange.
        assertTrue(blueScore > greenScore, "Blue friendship bar should score higher than green")
        assertTrue(greenScore > orangeScore, "Green friendship bar should score higher than orange")
        assertTrue(blueScore > orangeScore, "Blue friendship bar should score higher than orange")
    }

    @Test
    @DisplayName("No bars returns negative infinity")
    fun testNoBarsReturnsNegativeInfinity() {
        val trainingWithNoBars =
            createDefaultTrainingOption(
                relationshipBars = arrayListOf(),
            )

        val score = scoreFriendshipTraining(trainingWithNoBars)

        assertEquals(Double.NEGATIVE_INFINITY, score, "Empty relationship bars should return negative infinity")
    }

    @Test
    @DisplayName("Only orange bars returns zero score")
    fun testOnlyOrangeBarsReturnsZero() {
        val orangeBar1 = BarFillResult(statName = StatName.SPEED, fillPercent = 85.0, filledSegments = 3, dominantColor = "orange")
        val orangeBar2 = BarFillResult(statName = StatName.SPEED, fillPercent = 95.0, filledSegments = 3, dominantColor = "orange")
        val orangeBar3 = BarFillResult(statName = StatName.SPEED, fillPercent = 100.0, filledSegments = 4, dominantColor = "orange")

        val trainingWithOnlyOrange =
            createDefaultTrainingOption(
                relationshipBars = arrayListOf(orangeBar1, orangeBar2, orangeBar3),
            )

        val score = scoreFriendshipTraining(trainingWithOnlyOrange)

        assertEquals(0.0, score, "A zero score should be given with only orange bars for the training")
    }

    @Test
    @DisplayName("On an equal bond score, Wit is preferred (free-energy tiebreak)")
    fun testWitPreferredOnBondTie() {
        val witScore = scoreFriendshipTraining(createDefaultTrainingOption(name = StatName.WIT, relationshipBars = arrayListOf(BarFillResult(StatName.SPEED, 50.0, 2, "blue"))))
        val speedScore = scoreFriendshipTraining(createDefaultTrainingOption(name = StatName.SPEED, relationshipBars = arrayListOf(BarFillResult(StatName.SPEED, 50.0, 2, "blue"))))
        assertTrue(witScore > speedScore, "Wit should edge out Speed on an equal bond score since it costs no energy")
    }

    @Test
    @DisplayName("On an equal bond score, Guts is avoided (costly-energy penalty)")
    fun testGutsAvoidedOnBondTie() {
        val gutsScore = scoreFriendshipTraining(createDefaultTrainingOption(name = StatName.GUTS, relationshipBars = arrayListOf(BarFillResult(StatName.SPEED, 50.0, 2, "blue"))))
        val speedScore = scoreFriendshipTraining(createDefaultTrainingOption(name = StatName.SPEED, relationshipBars = arrayListOf(BarFillResult(StatName.SPEED, 50.0, 2, "blue"))))
        assertTrue(speedScore > gutsScore, "Guts should fall below Speed on an equal bond score since it costs the most energy")
    }

    @Test
    @DisplayName("The energy tiebreak never overrides a genuine bond advantage")
    fun testEnergyTiebreakDoesNotOverrideBond() {
        // Speed carries an extra green bar (>= 0.5 more bond); Wit's small free-energy tiebreak must not flip it.
        val speedScore = scoreFriendshipTraining(createDefaultTrainingOption(name = StatName.SPEED, relationshipBars = arrayListOf(BarFillResult(StatName.SPEED, 50.0, 2, "blue"), BarFillResult(StatName.SPEED, 50.0, 2, "green"))))
        val witScore = scoreFriendshipTraining(createDefaultTrainingOption(name = StatName.WIT, relationshipBars = arrayListOf(BarFillResult(StatName.SPEED, 50.0, 2, "blue"))))
        assertTrue(speedScore > witScore, "A genuinely better bond (extra green bar) must beat Wit's tiebreak")
    }

    // ============================================================================
    // calculateStatEfficiencyScore Tests
    // ============================================================================

    @Test
    @DisplayName("Stats furthest behind target get highest multiplier")
    fun testStatsBehindTargetGetHigherMultiplier() {
        val currentStats =
            mapOf(
                StatName.SPEED to 300,
                StatName.STAMINA to 600,
                StatName.POWER to 300,
                StatName.GUTS to 300,
                StatName.WIT to 300,
            )

        val speedTraining =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(30, 0, 15, 0, 0)),
            )
        val staminaTraining =
            createDefaultTrainingOption(
                name = StatName.STAMINA,
                statGains = statGainsToMap(intArrayOf(0, 45, 0, 20, 0)),
            )

        val config =
            createDefaultConfig(
                trainingOptions = listOf(speedTraining, staminaTraining),
                currentStats = currentStats,
                preferredDistance = "Medium",
            )

        val speedScore = calculateStatEfficiencyScore(config, speedTraining)
        val staminaScore = calculateStatEfficiencyScore(config, staminaTraining)

        assertTrue(speedScore > staminaScore, "Speed should score higher than Stamina due to being more behind target and is higher in the stat priority list")
    }

    @Test
    @DisplayName("High main stat gains get bonus multiplier")
    fun testHighMainStatGainsGetBonus() {
        val currentStats =
            mapOf(
                StatName.SPEED to 600,
                StatName.STAMINA to 600,
                StatName.POWER to 600,
                StatName.GUTS to 600,
                StatName.WIT to 600,
            )

        val highMainStatTraining =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(35, 0, 10, 0, 0)),
            )
        val lowMainStatTraining =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(20, 0, 10, 0, 0)),
            )

        val config =
            createDefaultConfig(
                trainingOptions = listOf(highMainStatTraining, lowMainStatTraining),
                currentStats = currentStats,
            )

        val highScore = calculateStatEfficiencyScore(config, highMainStatTraining)
        val lowScore = calculateStatEfficiencyScore(config, lowMainStatTraining)

        val expectedRatio = 35.0 / 20.0
        val actualRatio = highScore / lowScore
        assertTrue(actualRatio > expectedRatio, "High main stat gains (30+) should get bonus beyond just stat gain difference")
    }

    @Test
    @DisplayName("Spark bonus applies for stats below 600 when enabled")
    fun testSparkBonusAppliesForLowStats() {
        val currentStats =
            mapOf(
                StatName.SPEED to 400,
                StatName.STAMINA to 400,
                StatName.POWER to 400,
                StatName.GUTS to 400,
                StatName.WIT to 400,
            )

        val speedTraining =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(20, 0, 10, 0, 0)),
            )

        val configWithSpark =
            createDefaultConfig(
                trainingOptions = listOf(speedTraining),
                currentStats = currentStats,
                focusOnSparkStatTarget = listOf(StatName.SPEED),
            )
        val configWithoutSpark =
            createDefaultConfig(
                trainingOptions = listOf(speedTraining),
                currentStats = currentStats,
                focusOnSparkStatTarget = emptyList(),
            )

        val sparkScore = calculateStatEfficiencyScore(configWithSpark, speedTraining)
        val noSparkScore = calculateStatEfficiencyScore(configWithoutSpark, speedTraining)

        assertTrue(sparkScore > noSparkScore, "Spark bonus should increase score for stats below 600")
    }

    @Test
    @DisplayName("Zero stat gains return zero score")
    fun testZeroStatGainsReturnZero() {
        val training =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(0, 0, 0, 0, 0)),
            )

        val config = createDefaultConfig(trainingOptions = listOf(training))
        val score = calculateStatEfficiencyScore(config, training)

        assertEquals(0.0, score, "Training with no stat gains should return zero")
    }

    // ============================================================================
    // calculateRelationshipScore Tests
    // ============================================================================

    @Test
    @DisplayName("Diminishing returns apply as bars fill up")
    fun testDiminishingReturnsForFilledBars() {
        val lowFillBar = BarFillResult(statName = StatName.SPEED, fillPercent = 20.0, filledSegments = 1, dominantColor = "blue")
        val highFillBar = BarFillResult(statName = StatName.SPEED, fillPercent = 70.0, filledSegments = 3, dominantColor = "green")

        val lowFillTraining =
            createDefaultTrainingOption(
                relationshipBars = arrayListOf(lowFillBar),
            )
        val highFillTraining =
            createDefaultTrainingOption(
                relationshipBars = arrayListOf(highFillBar),
            )

        val config = createDefaultConfig(trainingOptions = listOf(lowFillTraining, highFillTraining))

        val lowFillScore = calculateRelationshipScore(config, lowFillTraining)
        val highFillScore = calculateRelationshipScore(config, highFillTraining)

        assertTrue(lowFillScore > highFillScore, "Lower fill bars should score higher due to diminishing returns")
    }

    // ============================================================================
    // calculateMiscScore Tests
    // ============================================================================

    @Test
    @DisplayName("Trainings with skill hints score higher than those without")
    fun testSkillHintsAdd10PointsEach() {
        val speedTraining = createDefaultTrainingOption(name = StatName.SPEED)
        val staminaTraining = createDefaultTrainingOption(name = StatName.STAMINA)

        // Speed has 2 skill hints, Stamina has 0.
        val config =
            createDefaultConfig(
                trainingOptions = listOf(speedTraining, staminaTraining),
                skillHintsPerLocation =
                    mapOf(
                        StatName.SPEED to 2,
                        StatName.STAMINA to 0,
                        StatName.POWER to 0,
                        StatName.GUTS to 0,
                        StatName.WIT to 0,
                    ),
            )

        val speedScore = calculateMiscScore(config, speedTraining)
        val staminaScore = calculateMiscScore(config, staminaTraining)

        assertTrue(speedScore > staminaScore, "A training with skill hints should score higher than a training with no skill hints")
    }

    @Test
    @DisplayName("Prioritized skill hints return massive score")
    fun testPrioritizedSkillHintsReturnMassiveScore() {
        val training = createDefaultTrainingOption(name = StatName.SPEED)

        val configWithPriority =
            createDefaultConfig(
                trainingOptions = listOf(training),
                skillHintsPerLocation =
                    mapOf(
                        StatName.SPEED to 1,
                        StatName.STAMINA to 0,
                        StatName.POWER to 0,
                        StatName.GUTS to 0,
                        StatName.WIT to 0,
                    ),
                enablePrioritizeSkillHints = true,
            )
        val configWithoutPriority =
            createDefaultConfig(
                trainingOptions = listOf(training),
                skillHintsPerLocation =
                    mapOf(
                        StatName.SPEED to 1,
                        StatName.STAMINA to 0,
                        StatName.POWER to 0,
                        StatName.GUTS to 0,
                        StatName.WIT to 0,
                    ),
                enablePrioritizeSkillHints = false,
            )

        val priorityScore = calculateMiscScore(configWithPriority, training)
        val normalScore = calculateMiscScore(configWithoutPriority, training)

        assertTrue(priorityScore > normalScore, "Prioritized skill hints should return higher score than normal skill hints")
    }

    @Test
    @DisplayName("Prioritization does not boost a training that carries zero skill hints (issue #372 gate)")
    fun testPrioritizationDoesNotBoostHintlessTraining() {
        // Regression guard for the issue #372 fix. A hinted training that fails the failure-rate/energy
        // gate is dropped from trainingMap, so recommendTraining() reads 0 hints for it. With zero hints
        // the priority boost must NOT apply even when prioritization is enabled — otherwise a gated-out
        // high-failure hint could still dominate the recommendation. This verifies the 10000+ boost is
        // conditional on numSkillHints > 0, keeping a hintless training in the normal 0..100 score band.
        val training = createDefaultTrainingOption(name = StatName.SPEED)

        val config =
            createDefaultConfig(
                trainingOptions = listOf(training),
                skillHintsPerLocation =
                    mapOf(
                        StatName.SPEED to 0,
                        StatName.STAMINA to 0,
                        StatName.POWER to 0,
                        StatName.GUTS to 0,
                        StatName.WIT to 0,
                    ),
                enablePrioritizeSkillHints = true,
            )

        val score = calculateMiscScore(config, training)

        assertTrue(score <= 100.0, "A training with zero skill hints must not receive the 10000+ priority boost even when prioritization is enabled")
    }

    // ============================================================================
    // selectBestTrainingWithHintPriority Tests (issue #372 — all-years gated hint priority)
    // ============================================================================

    @Test
    @DisplayName("Hint priority picks a hinted training over a higher-scored non-hinted one")
    fun testHintPriorityPrefersHintedOverHigherScore() {
        val hinted = createDefaultTrainingOption(name = StatName.SPEED, numSkillHints = 1)
        val plain = createDefaultTrainingOption(name = StatName.STAMINA, numSkillHints = 0)
        // The non-hinted training has the higher mode score, but prioritization must still pick the hinted one.
        val scores = mapOf(hinted to 10.0, plain to 99.0)

        val best = selectBestTrainingWithHintPriority(scores, enablePrioritizeSkillHints = true)

        assertEquals(StatName.SPEED, best?.name, "With prioritization on, a hinted training should win even with a lower mode score")
    }

    @Test
    @DisplayName("Hint priority is ignored when prioritization is disabled")
    fun testHintPriorityIgnoredWhenDisabled() {
        val hinted = createDefaultTrainingOption(name = StatName.SPEED, numSkillHints = 1)
        val plain = createDefaultTrainingOption(name = StatName.STAMINA, numSkillHints = 0)
        val scores = mapOf(hinted to 10.0, plain to 99.0)

        val best = selectBestTrainingWithHintPriority(scores, enablePrioritizeSkillHints = false)

        assertEquals(StatName.STAMINA, best?.name, "With prioritization off, the highest-scored training should win regardless of hints")
    }

    @Test
    @DisplayName("Hint priority falls back to the top score when no training has a hint")
    fun testHintPriorityFallsBackWhenNoHints() {
        val a = createDefaultTrainingOption(name = StatName.SPEED, numSkillHints = 0)
        val b = createDefaultTrainingOption(name = StatName.POWER, numSkillHints = 0)
        val scores = mapOf(a to 30.0, b to 80.0)

        val best = selectBestTrainingWithHintPriority(scores, enablePrioritizeSkillHints = true)

        assertEquals(StatName.POWER, best?.name, "When no training carries a hint, the highest-scored training should win")
    }

    @Test
    @DisplayName("Hint priority picks the highest-scored training among the hinted ones")
    fun testHintPriorityPicksBestAmongHinted() {
        val lowHinted = createDefaultTrainingOption(name = StatName.SPEED, numSkillHints = 1)
        val highHinted = createDefaultTrainingOption(name = StatName.POWER, numSkillHints = 2)
        val plain = createDefaultTrainingOption(name = StatName.STAMINA, numSkillHints = 0)
        val scores = mapOf(lowHinted to 20.0, highHinted to 40.0, plain to 99.0)

        val best = selectBestTrainingWithHintPriority(scores, enablePrioritizeSkillHints = true)

        assertEquals(StatName.POWER, best?.name, "Among hinted trainings the highest-scored one should win")
    }

    // ============================================================================
    // calculateRawTrainingScore Tests
    // ============================================================================

    @Test
    @DisplayName("Blacklisted training returns zero score")
    fun testBlacklistedTrainingReturnsZero() {
        val training = createDefaultTrainingOption(name = StatName.SPEED)

        val config =
            createDefaultConfig(
                trainingOptions = listOf(training),
                blacklist = listOf(StatName.SPEED),
            )

        val score = calculateRawTrainingScore(config, training)

        assertEquals(0.0, score, "Blacklisted training should return zero score")
    }

    @Test
    @DisplayName("Training at stat cap returns zero score")
    fun testTrainingAtStatCapReturnsZero() {
        val currentStats =
            mapOf(
                StatName.SPEED to 1200,
                StatName.STAMINA to 400,
                StatName.POWER to 400,
                StatName.GUTS to 400,
                StatName.WIT to 400,
            )

        val training =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(60, 0, 30, 0, 0)),
            )

        val config =
            createDefaultConfig(
                trainingOptions = listOf(training),
                currentStats = currentStats,
                // Pinned like the finale tests: at scenario="Unknown" the cap stays 1200, so a
                // 1200 stat exercises the at-cap branch this test is named for.
                scenario = "Unknown",
            )

        val score = calculateRawTrainingScore(config, training)

        assertEquals(0.0, score, "Training that would exceed stat cap should return zero score")
    }

    @Test
    @DisplayName("Maxed stat with disableTrainingOnMaxedStat returns zero")
    fun testMaxedStatWithDisableSettingReturnsZero() {
        val currentStats =
            mapOf(
                StatName.SPEED to 1999,
                StatName.STAMINA to 400,
                StatName.POWER to 400,
                StatName.GUTS to 400,
                StatName.WIT to 400,
            )

        val training =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(60, 0, 30, 0, 0)),
            )

        val config =
            createDefaultConfig(
                trainingOptions = listOf(training),
                currentStats = currentStats,
                disableTrainingOnMaxedStat = true,
            )

        val score = calculateRawTrainingScore(config, training)

        assertEquals(0.0, score, "Training for would-be maxed stat should return zero when disableTrainingOnMaxedStat is true")
    }

    @Test
    @DisplayName("Rainbow training scores higher")
    fun testRainbowMultiplierInYear2Plus() {
        val rainbowTraining =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(30, 0, 15, 0, 0)),
                numRainbow = 1,
            )
        val normalTraining =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(30, 0, 15, 0, 0)),
                numRainbow = 0,
            )

        val config =
            createDefaultConfig(
                trainingOptions = listOf(rainbowTraining, normalTraining),
                currentDate = GameDate(year = DateYear.CLASSIC, month = DateMonth.DECEMBER, phase = DatePhase.LATE),
                enableRainbowTrainingBonus = true,
            )

        val rainbowScore = calculateRawTrainingScore(config, rainbowTraining)
        val normalScore = calculateRawTrainingScore(config, normalTraining)

        assertTrue(rainbowScore > normalScore, "Rainbow training should score higher")
    }

    @Test
    @DisplayName("Training with relationship bars uses different weights")
    fun testRelationshipBarsChangeWeightDistribution() {
        val bar = BarFillResult(statName = StatName.SPEED, fillPercent = 20.0, filledSegments = 2, dominantColor = "blue")
        val trainingWithBars =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(20, 0, 10, 0, 0)),
                relationshipBars = arrayListOf(bar),
            )
        val trainingWithoutBars =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(20, 0, 10, 0, 0)),
                relationshipBars = arrayListOf(),
            )

        val config =
            createDefaultConfig(
                trainingOptions = listOf(trainingWithBars, trainingWithoutBars),
            )

        val withBarsScore = calculateRawTrainingScore(config, trainingWithBars)
        val withoutBarsScore = calculateRawTrainingScore(config, trainingWithoutBars)

        // Both should have positive scores, and the relationship bar contribution should affect total.
        assertTrue(withBarsScore > 0, "Training with bars should have positive score")
        assertTrue(withoutBarsScore > 0, "Training without bars should have positive score")
        // The training with bars gets relationship contribution.
        assertNotEquals(withBarsScore, withoutBarsScore, "Scores should differ based on relationship bars presence")
    }

    @Test
    @DisplayName("Rainbow bonus is reduced when enableRainbowTrainingBonus is false")
    fun testReducedRainbowBonusWhenDisabled() {
        val rainbowTraining =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(30, 0, 15, 0, 0)),
                numRainbow = 1,
            )
        val normalTraining =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(30, 0, 15, 0, 0)),
                numRainbow = 0,
            )

        val config =
            createDefaultConfig(
                trainingOptions = listOf(rainbowTraining, normalTraining),
                currentDate = GameDate(year = DateYear.CLASSIC, month = DateMonth.DECEMBER, phase = DatePhase.LATE),
                enableRainbowTrainingBonus = false,
            )

        val rainbowScore = calculateRawTrainingScore(config, rainbowTraining)
        val normalScore = calculateRawTrainingScore(config, normalTraining)

        assertTrue(rainbowScore > normalScore, "Rainbow training should still score higher when bonus is disabled")
    }

    // ============================================================================
    // scoreUnityCupTraining Tests
    // ============================================================================

    @Test
    @DisplayName("Spirit gauges ready to burst get highest priority")
    fun testSpiritGaugesReadyToBurstHighestPriority() {
        val trainingWithBurst =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                numSpiritGaugesReadyToBurst = 1,
                numSpiritGaugesCanFill = 0,
            )
        val trainingWithFill =
            createDefaultTrainingOption(
                name = StatName.STAMINA,
                numSpiritGaugesReadyToBurst = 0,
                numSpiritGaugesCanFill = 3,
            )
        val trainingWithNoGauges =
            createDefaultTrainingOption(
                name = StatName.POWER,
                numSpiritGaugesReadyToBurst = 0,
                numSpiritGaugesCanFill = 0,
            )

        val config =
            createDefaultConfig(
                trainingOptions = listOf(trainingWithBurst, trainingWithFill, trainingWithNoGauges),
                scenario = "Unity Cup",
            )

        val burstScore = scoreUnityCupTraining(config, trainingWithBurst)
        val fillScore = scoreUnityCupTraining(config, trainingWithFill)
        val noGaugeScore = scoreUnityCupTraining(config, trainingWithNoGauges)

        assertTrue(burstScore > fillScore, "Training with gauges ready to burst should score higher than training that can fill gauges")
        assertTrue(fillScore > noGaugeScore, "Training that can fill gauges should score higher than training with no gauges")
    }

    @Test
    @DisplayName("Speed and Wit get facility preference bonuses when spirit gauge bursting")
    fun testFacilityPreferenceBonusesForBursting() {
        // Zero out stat gains to isolate facility bonuses.
        val speedTraining =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(0, 0, 0, 0, 0)),
                numSpiritGaugesReadyToBurst = 1,
            )
        val witTraining =
            createDefaultTrainingOption(
                name = StatName.WIT,
                statGains = statGainsToMap(intArrayOf(0, 0, 0, 0, 0)),
                numSpiritGaugesReadyToBurst = 1,
            )
        val gutsTraining =
            createDefaultTrainingOption(
                name = StatName.GUTS,
                statGains = statGainsToMap(intArrayOf(0, 0, 0, 0, 0)),
                numSpiritGaugesReadyToBurst = 1,
                numSpiritGaugesCanFill = 0,
            )

        val config =
            createDefaultConfig(
                trainingOptions = listOf(speedTraining, witTraining, gutsTraining),
                scenario = "Unity Cup",
            )

        val speedScore = scoreUnityCupTraining(config, speedTraining)
        val witScore = scoreUnityCupTraining(config, witTraining)
        val gutsScore = scoreUnityCupTraining(config, gutsTraining)

        // Speed and Wit should have same bonuses (both get +500 facility bonus).
        assertEquals(speedScore, witScore, 0.01, "Speed and Wit should have equal facility bonuses")
        // Guts should score lower since it doesn't have the facility bonus.
        assertTrue(speedScore > gutsScore, "Speed should score higher than Guts for facility preference")
    }

    @Test
    @DisplayName("Early game provides spirit gauge filling bonus")
    fun testEarlyGameGaugeFillingBonus() {
        val training =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                numSpiritGaugesCanFill = 2,
            )

        val earlyConfig =
            createDefaultConfig(
                trainingOptions = listOf(training),
                scenario = "Unity Cup",
                currentDate = GameDate(year = DateYear.JUNIOR, month = DateMonth.JANUARY, phase = DatePhase.EARLY),
            )
        val lateConfig =
            createDefaultConfig(
                trainingOptions = listOf(training),
                scenario = "Unity Cup",
                currentDate = GameDate(year = DateYear.CLASSIC, month = DateMonth.JUNE, phase = DatePhase.EARLY),
            )

        val earlyScore = scoreUnityCupTraining(earlyConfig, training)
        val lateScore = scoreUnityCupTraining(lateConfig, training)

        assertTrue(earlyScore > lateScore, "Early game should provide bonus for spirit gauge filling")
    }

    @Test
    @DisplayName("Rainbow training provides bonus when spirit gauge bursting")
    fun testRainbowBonusWhenBursting() {
        val rainbowBurstTraining =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                numSpiritGaugesReadyToBurst = 1,
                numRainbow = 1,
            )
        val normalBurstTraining =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                numSpiritGaugesReadyToBurst = 1,
                numRainbow = 0,
            )

        val config =
            createDefaultConfig(
                trainingOptions = listOf(rainbowBurstTraining, normalBurstTraining),
                scenario = "Unity Cup",
                currentDate = GameDate(year = DateYear.CLASSIC, month = DateMonth.JANUARY, phase = DatePhase.EARLY),
            )

        val rainbowScore = scoreUnityCupTraining(config, rainbowBurstTraining)
        val normalScore = scoreUnityCupTraining(config, normalBurstTraining)

        assertTrue(rainbowScore > normalScore, "Rainbow training should score higher when spirit gauge bursting")
    }

    // ============================================================================
    // Training Example Cases (Parameterized)
    // ============================================================================

    /**
     * Data class representing a training scenario test case.
     */
    data class TrainingTestCase(
        val description: String,
        val currentStats: Map<String, Int>,
        val trainings: List<TrainingDef>,
        val preferredDistance: String,
        val date: GameDate,
        val expectedTraining: StatName,
    ) {
        // Override toString() to only show the description in test names.
        override fun toString(): String = description
    }

    /**
     * Simplified training definition for test cases.
     */
    data class TrainingDef(
        val name: StatName,
        val statGains: IntArray,
        val relationshipBars: List<BarDef> = emptyList(),
        val numSpiritGaugesCanFill: Int = 0,
        val numSpiritGaugesReadyToBurst: Int = 0,
        val numRainbow: Int = 0,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TrainingDef

            if (numSpiritGaugesCanFill != other.numSpiritGaugesCanFill) return false
            if (numSpiritGaugesReadyToBurst != other.numSpiritGaugesReadyToBurst) return false
            if (numRainbow != other.numRainbow) return false
            if (name != other.name) return false
            if (!statGains.contentEquals(other.statGains)) return false
            if (relationshipBars != other.relationshipBars) return false

            return true
        }

        override fun hashCode(): Int {
            var result = numSpiritGaugesCanFill
            result = 31 * result + numSpiritGaugesReadyToBurst
            result = 31 * result + numRainbow
            result = 31 * result + name.hashCode()
            result = 31 * result + statGains.contentHashCode()
            result = 31 * result + relationshipBars.hashCode()
            return result
        }
    }

    /**
     * Simplified bar definition for test cases.
     */
    data class BarDef(
        val fillPercent: Double = 50.0,
        val filledSegments: Int = 2,
        val color: String,
    )

    // Note: Stat prioritization follows the default of [Speed, Stamina, Power, Wit, Guts].
    companion object {
        /**
         * Provides Unity Cup test cases for parameterized testing.
         * Add new test cases here - each one will automatically be tested.
         */
        @JvmStatic
        fun unityCupTestCases(): Stream<TrainingTestCase> =
            Stream.of(
                TrainingTestCase(
                    description = "Junior Year Early Dec - Guts with the only burstable gauge",
                    currentStats = mapOf("Speed" to 358, "Stamina" to 217, "Power" to 258, "Guts" to 168, "Wit" to 168),
                    trainings =
                        listOf(
                            TrainingDef(StatName.SPEED, intArrayOf(15, 0, 6, 0, 0), listOf(BarDef(color = "green")), numSpiritGaugesCanFill = 1),
                            TrainingDef(StatName.STAMINA, intArrayOf(0, 8, 0, 4, 0)),
                            TrainingDef(StatName.POWER, intArrayOf(0, 4, 8, 0, 0)),
                            TrainingDef(
                                StatName.GUTS,
                                intArrayOf(11, 0, 10, 31, 0),
                                listOf(BarDef(color = "green"), BarDef(color = "green"), BarDef(color = "green")),
                                numSpiritGaugesCanFill = 1,
                                numSpiritGaugesReadyToBurst = 1,
                            ),
                            TrainingDef(StatName.WIT, intArrayOf(4, 0, 0, 0, 17), numSpiritGaugesReadyToBurst = 1),
                        ),
                    preferredDistance = "Medium",
                    date = GameDate(year = DateYear.JUNIOR, month = DateMonth.DECEMBER, phase = DatePhase.EARLY),
                    expectedTraining = StatName.GUTS,
                ),
                TrainingTestCase(
                    description = "Classic Year Early Aug - Power with rainbow bonus, fillable gauge and stat gains",
                    currentStats = mapOf("Speed" to 453, "Stamina" to 372, "Power" to 483, "Guts" to 244, "Wit" to 214),
                    trainings =
                        listOf(
                            TrainingDef(StatName.SPEED, intArrayOf(22, 0, 10, 0, 0), listOf(BarDef(color = "green")), numSpiritGaugesCanFill = 1),
                            TrainingDef(StatName.STAMINA, intArrayOf(0, 25, 0, 13, 0), listOf(BarDef(color = "orange"), BarDef(color = "green"), BarDef(color = "green")), numSpiritGaugesCanFill = 1),
                            TrainingDef(StatName.POWER, intArrayOf(0, 15, 23, 0, 0), listOf(BarDef(color = "orange")), numSpiritGaugesCanFill = 1, numRainbow = 1),
                            TrainingDef(StatName.GUTS, intArrayOf(5, 0, 5, 15, 0)),
                            TrainingDef(StatName.WIT, intArrayOf(5, 0, 0, 0, 12)),
                        ),
                    preferredDistance = "Medium",
                    date = GameDate(year = DateYear.CLASSIC, month = DateMonth.AUGUST, phase = DatePhase.EARLY),
                    expectedTraining = StatName.POWER,
                ),
                TrainingTestCase(
                    description = "Senior Year Early Jul - Speed with high main stat gain, rainbow bonus and fillable gauges",
                    currentStats = mapOf("Speed" to 834, "Stamina" to 588, "Power" to 724, "Guts" to 335, "Wit" to 283),
                    trainings =
                        listOf(
                            TrainingDef(StatName.SPEED, intArrayOf(33, 0, 13, 0, 0), listOf(BarDef(color = "orange")), numSpiritGaugesCanFill = 2, numRainbow = 1),
                            TrainingDef(StatName.STAMINA, intArrayOf(0, 47, 0, 22, 0), listOf(BarDef(color = "orange")), numSpiritGaugesReadyToBurst = 1),
                            TrainingDef(StatName.POWER, intArrayOf(0, 8, 14, 0, 0), numSpiritGaugesCanFill = 1),
                            TrainingDef(StatName.GUTS, intArrayOf(12, 0, 9, 35, 0), numSpiritGaugesReadyToBurst = 1),
                            TrainingDef(StatName.WIT, intArrayOf(6, 0, 0, 0, 13)),
                        ),
                    preferredDistance = "Medium",
                    date = GameDate(year = DateYear.SENIOR, month = DateMonth.JULY, phase = DatePhase.EARLY),
                    expectedTraining = StatName.SPEED,
                ),
            )

        /**
         * Provides URA Finale test cases for parameterized testing.
         * Add new test cases here - each one will automatically be tested.
         */
        @JvmStatic
        fun uraFinaleTestCases(): Stream<TrainingTestCase> =
            Stream.of(
                TrainingTestCase(
                    description = "URA Finale Qualifier - Speed with high main stat gain and rainbow bonus",
                    currentStats = mapOf("Speed" to 1042, "Stamina" to 615, "Power" to 841, "Guts" to 362, "Wit" to 315),
                    trainings =
                        listOf(
                            TrainingDef(StatName.SPEED, intArrayOf(31, 0, 15, 0, 0), numRainbow = 1),
                            TrainingDef(StatName.STAMINA, intArrayOf(0, 15, 0, 6, 0)),
                            TrainingDef(StatName.POWER, intArrayOf(0, 7, 15, 0, 0)),
                            TrainingDef(StatName.GUTS, intArrayOf(6, 0, 4, 16, 0)),
                            TrainingDef(StatName.WIT, intArrayOf(5, 0, 0, 0, 15)),
                        ),
                    preferredDistance = "Medium",
                    date = GameDate(day = 73),
                    expectedTraining = StatName.SPEED,
                ),
                TrainingTestCase(
                    description = "Classic Year Early Aug - Speed with high main stat gain and rainbow bonus",
                    currentStats = mapOf("Speed" to 537, "Stamina" to 386, "Power" to 388, "Guts" to 228, "Wit" to 255),
                    trainings =
                        listOf(
                            TrainingDef(StatName.SPEED, intArrayOf(29, 0, 12, 0, 0), listOf(BarDef(color = "orange")), numRainbow = 1),
                            TrainingDef(StatName.STAMINA, intArrayOf(0, 25, 0, 10, 0), listOf(BarDef(color = "orange"))),
                            TrainingDef(StatName.POWER, intArrayOf(0, 8, 12, 0, 0)),
                            TrainingDef(StatName.GUTS, intArrayOf(7, 0, 7, 15, 0), listOf(BarDef(color = "green"))),
                            TrainingDef(StatName.WIT, intArrayOf(6, 0, 0, 0, 14)),
                        ),
                    preferredDistance = "Medium",
                    date = GameDate(year = DateYear.CLASSIC, month = DateMonth.AUGUST, phase = DatePhase.EARLY),
                    expectedTraining = StatName.SPEED,
                ),
                TrainingTestCase(
                    description = "Junior Year Pre-Debut - Power with the most relationship bars",
                    currentStats = mapOf("Speed" to 136, "Stamina" to 189, "Power" to 160, "Guts" to 76, "Wit" to 135),
                    trainings =
                        listOf(
                            TrainingDef(StatName.SPEED, intArrayOf(10, 0, 4, 0, 0), listOf(BarDef(color = "blue"), BarDef(color = "blue"))),
                            TrainingDef(StatName.STAMINA, intArrayOf(0, 8, 0, 3, 0)),
                            TrainingDef(StatName.POWER, intArrayOf(0, 8, 12, 0, 0), listOf(BarDef(color = "blue"), BarDef(color = "blue"), BarDef(color = "blue"))),
                            TrainingDef(StatName.GUTS, intArrayOf(3, 0, 3, 6, 0)),
                            TrainingDef(StatName.WIT, intArrayOf(3, 0, 0, 0, 9)),
                        ),
                    preferredDistance = "Medium",
                    date = GameDate(day = 2),
                    expectedTraining = StatName.POWER,
                ),
            )
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("unityCupTestCases")
    @DisplayName("Unity Cup Training Selection")
    fun testUnityCupTrainingSelection(testCase: TrainingTestCase) {
        // Convert TrainingDef to TrainingOption.
        val trainingOptions =
            testCase.trainings.map { def ->
                createDefaultTrainingOption(
                    name = def.name,
                    statGains = statGainsToMap(def.statGains),
                    relationshipBars =
                        ArrayList(
                            def.relationshipBars.map { bar ->
                                BarFillResult(statName = StatName.SPEED, bar.fillPercent, bar.filledSegments, bar.color)
                            },
                        ),
                    numSpiritGaugesCanFill = def.numSpiritGaugesCanFill,
                    numSpiritGaugesReadyToBurst = def.numSpiritGaugesReadyToBurst,
                    numRainbow = def.numRainbow,
                )
            }

        val config =
            createDefaultConfig(
                trainingOptions = trainingOptions,
                currentStats = statsToMap(testCase.currentStats),
                preferredDistance = testCase.preferredDistance,
                currentDate = testCase.date,
                scenario = "Unity Cup",
            )

        // Score all trainings using Unity Cup scoring.
        val scores =
            if (testCase.date.year < DateYear.SENIOR) {
                trainingOptions.associateWith { scoreUnityCupTraining(config, it) }
            } else {
                trainingOptions.associateWith { calculateRawTrainingScore(config, it) }
            }
        val bestTraining = scores.maxByOrNull { it.value }?.key

        assertEquals(testCase.expectedTraining, bestTraining?.name, testCase.description)
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("uraFinaleTestCases")
    @DisplayName("URA Finale Training Selection")
    fun testURAFinaleTrainingSelection(testCase: TrainingTestCase) {
        // Convert TrainingDef to TrainingOption.
        val trainingOptions =
            testCase.trainings.map { def ->
                createDefaultTrainingOption(
                    name = def.name,
                    statGains = statGainsToMap(def.statGains),
                    relationshipBars =
                        ArrayList(
                            def.relationshipBars.map { bar ->
                                BarFillResult(statName = StatName.SPEED, bar.fillPercent, bar.filledSegments, bar.color)
                            },
                        ),
                    numRainbow = def.numRainbow,
                )
            }

        val config =
            createDefaultConfig(
                trainingOptions = trainingOptions,
                currentStats = statsToMap(testCase.currentStats),
                preferredDistance = testCase.preferredDistance,
                currentDate = testCase.date,
                scenario = "URA Finale",
            )

        // Use friendship training scoring for Junior Year, otherwise use standard scoring.
        val scores =
            if (testCase.date.year == DateYear.JUNIOR) {
                trainingOptions.associateWith { scoreFriendshipTraining(it) }
            } else {
                trainingOptions.associateWith { calculateRawTrainingScore(config, it) }
            }
        val bestTraining = scores.maxByOrNull { it.value }?.key

        assertEquals(testCase.expectedTraining, bestTraining?.name, testCase.description)
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////
    // Finale Race Stat Bonus Tests
    // ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    @DisplayName("getRemainingFinaleRaces returns correct values for boundary turns")
    fun testGetRemainingFinaleRaces() {
        assertEquals(3, getRemainingFinaleRaces(1), "Turn 1: all 3 finale races remaining")
        assertEquals(3, getRemainingFinaleRaces(60), "Turn 60: all 3 finale races remaining")
        assertEquals(3, getRemainingFinaleRaces(72), "Turn 72: all 3 finale races remaining")
        assertEquals(2, getRemainingFinaleRaces(73), "Turn 73: 2 finale races remaining")
        assertEquals(1, getRemainingFinaleRaces(74), "Turn 74: 1 finale race remaining")
        assertEquals(0, getRemainingFinaleRaces(75), "Turn 75: no finale races remaining")
    }

    @Test
    @DisplayName("getFinaleStatBonus returns correct bonus values")
    fun testGetFinaleStatBonus() {
        assertEquals(45, getFinaleStatBonus(60), "Turn 60: 3 races * 15 = 45")
        assertEquals(30, getFinaleStatBonus(73), "Turn 73: 2 races * 15 = 30")
        assertEquals(15, getFinaleStatBonus(74), "Turn 74: 1 race * 15 = 15")
        assertEquals(0, getFinaleStatBonus(75), "Turn 75: 0 races * 15 = 0")
    }

    @Test
    @DisplayName("Stat near cap blocked by finale bonus adjustment (turn 60, 3 races remaining)")
    fun testFinaleAdjustmentBlocksTrainingNearCap() {
        // With 3 finale races remaining, effective cap = 1200 - 100 - 45 = 1055.
        // A stat at 1060 should be blocked.
        val currentStats =
            mapOf(
                StatName.SPEED to 1060,
                StatName.STAMINA to 400,
                StatName.POWER to 400,
                StatName.GUTS to 400,
                StatName.WIT to 400,
            )

        val training =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(60, 0, 30, 0, 0)),
            )

        val config =
            createDefaultConfig(
                trainingOptions = listOf(training),
                currentStats = currentStats,
                disableTrainingOnMaxedStat = true,
                currentDate = GameDate(day = 60),
                // Pinned to the flat-1200 fallback scenario so these tests isolate the finale
                // adjustment math from the per-scenario cap table (URA is 1400 now).
                scenario = "Unknown",
            )

        val score = calculateRawTrainingScore(config, training)

        assertEquals(0.0, score, "Stat at 1060 should be blocked when effective cap is 1055 (turn 60, 3 finale races)")
    }

    @Test
    @DisplayName("Same stat allowed when fewer finale races remain (turn 74, 1 race remaining)")
    fun testFinaleAdjustmentAllowsTrainingWithFewerRaces() {
        // With 1 finale race remaining, effective cap = 1200 - 100 - 15 = 1085.
        // A stat at 1060 should NOT be blocked.
        val currentStats =
            mapOf(
                StatName.SPEED to 1060,
                StatName.STAMINA to 400,
                StatName.POWER to 400,
                StatName.GUTS to 400,
                StatName.WIT to 400,
            )

        val training =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(20, 0, 10, 0, 0)),
            )

        val config =
            createDefaultConfig(
                trainingOptions = listOf(training),
                currentStats = currentStats,
                disableTrainingOnMaxedStat = true,
                currentDate = GameDate(day = 74),
                scenario = "Unknown",
            )

        val score = calculateRawTrainingScore(config, training)

        assertTrue(score > 0.0, "Stat at 1060 should be allowed when effective cap is 1085 (turn 74, 1 finale race)")
    }

    @Test
    @DisplayName("No finale adjustment on turn 75 (all races done)")
    fun testNoFinaleAdjustmentOnFinalTurn() {
        // With 0 finale races remaining, effective cap = 1200 - 100 = 1100 (unchanged).
        // A stat at 1060 with potential 1080 should NOT be blocked.
        val currentStats =
            mapOf(
                StatName.SPEED to 1060,
                StatName.STAMINA to 400,
                StatName.POWER to 400,
                StatName.GUTS to 400,
                StatName.WIT to 400,
            )

        val training =
            createDefaultTrainingOption(
                name = StatName.SPEED,
                statGains = statGainsToMap(intArrayOf(20, 0, 10, 0, 0)),
            )

        val config =
            createDefaultConfig(
                trainingOptions = listOf(training),
                currentStats = currentStats,
                disableTrainingOnMaxedStat = true,
                currentDate = GameDate(day = 75),
                scenario = "Unknown",
            )

        val score = calculateRawTrainingScore(config, training)

        assertTrue(score > 0.0, "Stat at 1060 should be allowed on turn 75 with no finale adjustment (effective cap = 1100)")
    }

    @Test
    @DisplayName("Per-scenario stat cap table matches the July 2026 rebalance values")
    fun testScenarioStatCapTable() {
        // URA Finale: 1400 across the board.
        for (stat in StatName.entries) {
            assertEquals(1400, getScenarioStatCap("URA Finale", stat), "URA cap for $stat")
        }
        // Unity Cup: 1300 with Wit 1800.
        assertEquals(1800, getScenarioStatCap("Unity Cup", StatName.WIT), "Unity Cup Wit cap")
        assertEquals(1300, getScenarioStatCap("Unity Cup", StatName.SPEED), "Unity Cup Speed cap")
        assertEquals(1300, getScenarioStatCap("Unity Cup", StatName.GUTS), "Unity Cup Guts cap")
        // Trackblazer: 1200 except Stamina 1900 and Wit 1500.
        assertEquals(1900, getScenarioStatCap("Trackblazer", StatName.STAMINA), "Trackblazer Stamina cap")
        assertEquals(1500, getScenarioStatCap("Trackblazer", StatName.WIT), "Trackblazer Wit cap")
        assertEquals(1200, getScenarioStatCap("Trackblazer", StatName.SPEED), "Trackblazer Speed cap")
        // Unknown scenarios keep the conservative flat 1200.
        for (stat in StatName.entries) {
            assertEquals(1200, getScenarioStatCap("Unknown", stat), "Fallback cap for $stat")
        }
    }
}
