package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.campaigns.Trackblazer
import com.steve1316.uma_android_automation.types.DateYear
import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.types.Trainee
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * The Trackblazer irregular pre-screen and the turn's regular analysis share one read of the five facilities: the irregular filter
 * only decides which facilities land in the maps and never changes the cached results. Runs the real `processAnalysisResults` and
 * the `analyzeTrainings` cache hit on a Training built without its constructor (it needs a live Game).
 */
class IrregularPreScreenCacheTest {
    private val unsafeType = Class.forName("sun.misc.Unsafe")
    private val unsafe = unsafeType.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)

    private fun <T> allocate(type: Class<T>): T = type.cast(unsafeType.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type))!!

    private fun Any.set(owner: Class<*>, name: String, value: Any?) = owner.getDeclaredField(name).apply { isAccessible = true }.set(this, value)

    private val turn = 50

    /** A Classic-year turn: the irregular baseline is the setting's default 30 (no settings database in a unit test). */
    private fun training(): Training {
        val campaign: Campaign = allocate(Trackblazer::class.java)
        campaign.set(Campaign::class.java, "date", GameDate(day = turn).apply { year = DateYear.CLASSIC })
        campaign.set(Campaign::class.java, "trainee", Trainee())

        val training = allocate(Training::class.java)
        training.set(Training::class.java, "campaign", campaign)
        training.set(Training::class.java, "trainingMap", mutableMapOf<StatName, Training.TrainingOption>())
        training.set(Training::class.java, "skippedTrainingMap", mutableMapOf<StatName, Training.TrainingOption>())
        training.set(Training::class.java, "restrictedTrainingNames", mutableSetOf<StatName>())
        training.set(Training::class.java, "maximumFailureChance", 20)
        training.set(Training::class.java, "enableRiskyTraining", false)
        training.set(Training::class.java, "riskyTrainingMaxFailureChance", 20)
        training.set(Training::class.java, "riskyTrainingMinStatGain", 20)
        training.set(Training::class.java, "minStatGainForCharm", 25)
        return training
    }

    private fun result(stat: StatName, failureChance: Int, mainGain: Int): Training.TrainingAnalysisResult =
        Training.TrainingAnalysisResult(stat, CountDownLatch(0), 0L).apply {
            this.failureChance = failureChance
            statGains = StatName.entries.associateWith { if (it == stat) mainGain else 0 }
            numRainbow = 0
        }

    /** Speed is admitted for irregular training (gain 35 >= 30), Wit is not (gain 10, nothing else), Guts is over the failure limit. */
    private fun read(): List<Training.TrainingAnalysisResult> =
        listOf(result(StatName.SPEED, 5, 35), result(StatName.STAMINA, 8, 32), result(StatName.GUTS, 40, 30), result(StatName.WIT, 0, 10))

    private fun Training.process(results: List<Training.TrainingAnalysisResult>, ignoreFailureChance: Boolean, irregular: Boolean) {
        Training::class.java
            .getDeclaredMethod("processAnalysisResults", List::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(this, results, ignoreFailureChance, irregular, false)
    }

    private fun snapshot(results: List<Training.TrainingAnalysisResult>) =
        results.map { listOf(it.name, it.statGains, it.failureChance, it.relationshipBars.toList(), it.numRainbow, it.numSkillHints, it.correctedStats, it.performanceGains) }

    @Test
    fun `the regular pass over the irregular pre-screen's results equals a fresh regular pass and leaves the results unchanged`() {
        for (ignoreFailureChance in listOf(false, true)) {
            val shared = read()
            val before = snapshot(shared)
            val reused = training()
            reused.process(shared, ignoreFailureChance, irregular = true)
            val irregularPicks = reused.trainingMap.keys.toSet()
            assertEquals(before, snapshot(shared), "the irregular pass changed the cached results (ignore=$ignoreFailureChance)")
            reused.process(shared, ignoreFailureChance, irregular = false)

            val fresh = training()
            fresh.process(read(), ignoreFailureChance, irregular = false)

            assertNotEquals(fresh.trainingMap.keys, irregularPicks, "the irregular filter must actually drop a facility here (ignore=$ignoreFailureChance)")
            assertEquals(fresh.trainingMap, reused.trainingMap, "ignore=$ignoreFailureChance")
            assertEquals(fresh.skippedTrainingMap, reused.skippedTrainingMap, "ignore=$ignoreFailureChance")
        }
    }

    @Test
    fun `a regular analysis right after the irregular pre-screen uses its cached read instead of reading again`() {
        // Covers the hit path's replay only; keeping the irregular flag out of the cache key is guarded by the source check below.
        val training = training()
        // As the pre-screen leaves the cache after its read: a charm on hand, this turn, Speed within its limit.
        training.set(Training::class.java, "cachedAnalysisResults", read())
        training.set(Training::class.java, "cachedIgnoreFailureChance", true)
        training.set(Training::class.java, "cachedSpeedOverLimit", false)
        training.set(Training::class.java, "cachedAnalysisTurn", turn)

        MessageLog.clearLog()
        // A miss would go on to read the five facilities through the Game, which this Training does not have.
        training.analyzeTrainings(mapOf("ignoreFailureChance" to true))

        assertTrue(MessageLog.getMessageLogCopy().any { "[TRAINING] Using cached training analysis results for this turn." in it })
        assertTrue(Training::class.java.getDeclaredField("analysisCompleted").apply { isAccessible = true }.getBoolean(training))
        val fresh = training()
        fresh.process(read(), ignoreFailureChance = true, irregular = false)
        assertEquals(fresh.trainingMap, training.trainingMap)
        assertEquals(fresh.skippedTrainingMap, training.skippedTrainingMap)
    }

    private val source by lazy { kotlinFile("bot/Training.kt").readText().replace("\r\n", "\n") }

    private fun between(from: String, to: String): String {
        val start = source.indexOf(from)
        require(start >= 0) { "missing anchor: $from" }
        val end = source.indexOf(to, start)
        require(end > start) { "missing anchor: $to" }
        return source.substring(start, end)
    }

    @Test
    fun `the cache key does not include the irregular flag, and the filter that makes this safe never writes to a result`() {
        assertFalse(source.contains("cachedIsIrregularEvaluation"), "the regular analysis must reuse the pre-screen's read")
        val filter = between("    private fun processAnalysisResults(", "    private fun markRestIfNoTrainingSurvived(")
        assertFalse(Regex("""\bresult\.\w+\s*(=|\+=|-=)[^=]""").containsMatchIn(filter), "processAnalysisResults must not change the cached results")
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
