package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * A facility whose failure chance could not be read (-1) is left out of the turn instead of borrowing another facility's
 * value: a copied low value let a high-failure facility train. Runs the real `processAnalysisResults` on a Training built
 * without its constructor (it needs a live Game), with only the fields that method reads set.
 */
class UnreadableFailureChanceSkipTest {
    private val unsafeType = Class.forName("sun.misc.Unsafe")
    private val unsafe = unsafeType.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)

    private fun training(maximumFailureChance: Int = 20): Training {
        val training = unsafeType.getMethod("allocateInstance", Class::class.java).invoke(unsafe, Training::class.java) as Training

        fun set(name: String, value: Any) = Training::class.java.getDeclaredField(name).apply { isAccessible = true }.set(training, value)
        set("trainingMap", mutableMapOf<StatName, Training.TrainingOption>())
        set("skippedTrainingMap", mutableMapOf<StatName, Training.TrainingOption>())
        set("maximumFailureChance", maximumFailureChance)
        set("riskyTrainingMaxFailureChance", maximumFailureChance)
        set("minStatGainForCharm", 25)
        return training
    }

    private fun result(stat: StatName, failureChance: Int, mainGain: Int = 20): Training.TrainingAnalysisResult =
        Training.TrainingAnalysisResult(stat, CountDownLatch(0), 0L).apply {
            this.failureChance = failureChance
            statGains = StatName.entries.associateWith { if (it == stat) mainGain else 0 }
        }

    private fun Training.process(results: List<Training.TrainingAnalysisResult>, ignoreFailureChance: Boolean = false, test: Boolean = false) {
        Training::class.java
            .getDeclaredMethod("processAnalysisResults", List::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(this, results, ignoreFailureChance, false, test)
    }

    @Test
    fun `an unread facility is skipped with its own reason and never takes another facility's value`() {
        val training = training()
        training.process(
            listOf(
                result(StatName.SPEED, 4),
                result(StatName.STAMINA, -1),
                result(StatName.POWER, 6),
                result(StatName.GUTS, -1, mainGain = 40),
                result(StatName.WIT, 0),
            ),
        )
        assertEquals(setOf(StatName.SPEED, StatName.POWER, StatName.WIT), training.trainingMap.keys)
        assertEquals(setOf(StatName.STAMINA, StatName.GUTS), training.skippedTrainingMap.keys)
        for (stat in listOf(StatName.STAMINA, StatName.GUTS)) {
            val skipped = training.skippedTrainingMap.getValue(stat)
            assertEquals(Training.SKIP_UNREADABLE_FAILURE_CHANCE, skipped.skipReason)
            assertEquals(-1, skipped.failureChance, "$stat must not carry a value read on another facility")
        }
    }

    @Test
    fun `all five unread leaves no training to pick, which is the Finale Wit or rest path`() {
        val training = training()
        training.process(StatName.entries.map { result(it, -1) })
        assertTrue(training.trainingMap.isEmpty())
        assertEquals(StatName.entries.toSet(), training.skippedTrainingMap.keys)
        assertTrue(training.skippedTrainingMap.values.all { it.skipReason == Training.SKIP_UNREADABLE_FAILURE_CHANCE })
    }

    @Test
    fun `a Good-Luck Charm turn still skips an unread facility, since the charm check reads its failure chance`() {
        val training = training()
        training.process(listOf(result(StatName.SPEED, 30, mainGain = 40), result(StatName.STAMINA, -1, mainGain = 40)), ignoreFailureChance = true)
        assertEquals(setOf(StatName.SPEED), training.trainingMap.keys)
        assertEquals(Training.SKIP_UNREADABLE_FAILURE_CHANCE, training.skippedTrainingMap.getValue(StatName.STAMINA).skipReason)
    }

    @Test
    fun `test mode keeps every facility`() {
        val training = training()
        training.process(StatName.entries.map { result(it, -1) }, test = true)
        assertEquals(StatName.entries.toSet(), training.trainingMap.keys)
    }

    @Test
    fun `a same-turn replay of the cached results skips the same facilities`() {
        val training = training()
        val results = listOf(result(StatName.SPEED, 5), result(StatName.WIT, -1))
        training.process(results)
        training.process(results)
        assertEquals(setOf(StatName.SPEED), training.trainingMap.keys)
        assertEquals(setOf(StatName.WIT), training.skippedTrainingMap.keys)
    }

    @Test
    fun `no failure chance read is copied from the first read any more, and an empty map still reaches Finale Wit or rest`() {
        val source = kotlinFile("bot/Training.kt").readText().replace("\r\n", "\n")
        assertFalse(source.contains("result.failureChance = failureChance"), "a per-tile miss must not take the first read's value")
        val start = source.indexOf("fun handleTrainingWithOutcome(")
        val body = source.substring(start, source.indexOf("fun executeTraining(trainingSelected: StatName?)", start))
        val analyze = body.indexOf("analyzeTrainings()")
        val empty = body.indexOf("if (trainingMap.isEmpty()) {")
        val finaleWit = body.indexOf("if ((trainWitDuringFinale && campaign.date.day > 72) || campaign.date.day == 75) {")
        assertTrue(analyze in 0 until empty && empty < finaleWit, "an empty training map after analysis goes to the Finale Wit or rest branch")
    }

    private fun skipped(stat: StatName, failureChance: Int, reason: String): Training.TrainingOption =
        Training.TrainingOption(name = stat, statGains = mapOf(stat to 30), failureChance = failureChance, relationshipBars = arrayListOf(), numRainbow = 0, skipReason = reason)

    @Test
    fun `a forced pick never takes the top-scored skipped facility when its failure chance is unread`() {
        val unread = skipped(StatName.SPEED, -1, Training.SKIP_UNREADABLE_FAILURE_CHANCE)
        val risky = skipped(StatName.POWER, 45, "high failure chance")
        val pick = Training.topForcedSkipped(mapOf(unread to 90.0, risky to 40.0))
        assertEquals(risky, pick?.key, "the best read facility is forced, so its own failure chance meets the forced-pick guards")
    }

    @Test
    fun `all rejected with only unread facilities leaves nothing to force`() {
        val pool = StatName.entries.associate { skipped(it, -1, Training.SKIP_UNREADABLE_FAILURE_CHANCE) to 50.0 }
        assertEquals(null, Training.topForcedSkipped(pool))
        assertEquals(null, Training.topForcedSkipped(emptyMap()))
    }

    @Test
    fun `the forced branch draws from the read-only pool, and with nothing there it can only default to a passing training`() {
        val source = kotlinFile("bot/Training.kt").readText().replace("\r\n", "\n")
        val start = source.indexOf("        if (forceSelection) {")
        val end = source.indexOf("return defaulted", start)
        require(start >= 0 && end > start) { "forced branch not found" }
        val branch = source.substring(start, end)
        assertTrue(branch.contains("val topSkipped = topForcedSkipped(skippedScores)"), "the forced pool must leave unread facilities out")
        assertFalse(branch.contains("skippedScores.maxByOrNull"), "no forced pick straight from every skipped score")
        assertTrue(branch.contains("val defaulted = trainingMap.keys.firstOrNull { it !in blacklist }"), "the fallback reads only trainings that passed")
    }

    private fun kotlinFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val candidate = File(dir, "src/main/java/com/steve1316/uma_android_automation/$relative")
            if (candidate.isFile) return candidate
            val nested = File(dir, "app/src/main/java/com/steve1316/uma_android_automation/$relative")
            if (nested.isFile) return nested
            dir = dir?.parentFile
        }
        error("missing $relative")
    }
}
