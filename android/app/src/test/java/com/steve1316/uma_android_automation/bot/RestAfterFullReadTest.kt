package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.types.StatName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * Speed's failure chance over the limit no longer rests the turn by itself: every facility is read, each one over its own limit is
 * dropped, and the turn rests only when none survives. Runs the real `processAnalysisResults` and `markRestIfNoTrainingSurvived` on a
 * Training built without its constructor (it needs a live Game); the read loop's wiring is pinned with source guards.
 */
class RestAfterFullReadTest {
    private val unsafeType = Class.forName("sun.misc.Unsafe")
    private val unsafe = unsafeType.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)

    private fun training(maximumFailureChance: Int = 15, risky: Boolean = false, riskyMax: Int = 30, riskyMinGain: Int = 20): Training {
        val training = unsafeType.getMethod("allocateInstance", Class::class.java).invoke(unsafe, Training::class.java) as Training

        fun set(name: String, value: Any) = Training::class.java.getDeclaredField(name).apply { isAccessible = true }.set(training, value)
        set("trainingMap", mutableMapOf<StatName, Training.TrainingOption>())
        set("skippedTrainingMap", mutableMapOf<StatName, Training.TrainingOption>())
        set("maximumFailureChance", maximumFailureChance)
        set("enableRiskyTraining", risky)
        set("riskyTrainingMaxFailureChance", if (risky) riskyMax else maximumFailureChance)
        set("riskyTrainingMinStatGain", riskyMinGain)
        set("minStatGainForCharm", 25)
        return training
    }

    private fun result(stat: StatName, failureChance: Int, mainGain: Int = 20): Training.TrainingAnalysisResult =
        Training.TrainingAnalysisResult(stat, CountDownLatch(0), 0L).apply {
            this.failureChance = failureChance
            statGains = StatName.entries.associateWith { if (it == stat) mainGain else 0 }
        }

    /** One full read: the per-facility filter, then the rest decision, as `analyzeTrainings` runs them after the five reads. */
    private fun Training.read(results: List<Training.TrainingAnalysisResult>, speedOverLimit: Boolean) {
        Training::class.java
            .getDeclaredMethod("processAnalysisResults", List::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(this, results, false, false, false)
        Training::class.java
            .getDeclaredMethod("markRestIfNoTrainingSurvived", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(this, speedOverLimit)
    }

    private fun turn(vararg failureChances: Pair<StatName, Int>) = failureChances.map { (stat, fail) -> result(stat, fail) }

    @Test
    fun `Speed over the limit with Wit at 0 percent trains Wit instead of resting`() {
        val training = training()
        training.read(turn(StatName.SPEED to 30, StatName.STAMINA to 28, StatName.POWER to 27, StatName.GUTS to 31, StatName.WIT to 0), speedOverLimit = true)
        assertEquals(setOf(StatName.WIT), training.trainingMap.keys)
        assertFalse(training.needsEnergyRecovery, "a surviving facility is a training turn, not an energy recovery")
        assertEquals(setOf(StatName.SPEED, StatName.STAMINA, StatName.POWER, StatName.GUTS), training.skippedTrainingMap.keys)
    }

    @Test
    fun `all five over the limit rests with needsEnergyRecovery set, each facility kept with its own failure chance and logged once`() {
        MessageLog.clearLog()
        val training = training()
        val fails = mapOf(StatName.SPEED to 30, StatName.STAMINA to 25, StatName.POWER to 28, StatName.GUTS to 33, StatName.WIT to 18)
        training.read(fails.map { (stat, fail) -> result(stat, fail) }, speedOverLimit = true)
        assertTrue(training.trainingMap.isEmpty())
        assertTrue(training.needsEnergyRecovery)
        assertEquals(fails, training.skippedTrainingMap.mapValues { it.value.failureChance }, "the decision trace's runner-ups carry each facility's own value")
        assertTrue(training.skippedTrainingMap.values.all { it.skipReason == "high failure chance" })
        val logged = MessageLog.getMessageLogCopy().filter { "[TRAINING] No training is within its failure chance limit" in it }
        assertEquals(1, logged.size, logged.toString())
        for ((stat, fail) in fails) assertTrue(logged.single().contains("$stat $fail%"), logged.single())
    }

    @Test
    fun `Speed within the limit keeps needsEnergyRecovery false even when every facility is dropped, as before`() {
        val training = training()
        training.read(turn(StatName.SPEED to 10, StatName.WIT to -1), speedOverLimit = false)
        assertEquals(setOf(StatName.SPEED), training.trainingMap.keys)
        assertFalse(training.needsEnergyRecovery)

        val dropped = training(maximumFailureChance = 15, risky = true, riskyMax = 30, riskyMinGain = 40)
        dropped.read(turn(StatName.SPEED to 20, StatName.WIT to 16), speedOverLimit = false)
        assertTrue(dropped.trainingMap.isEmpty())
        assertFalse(dropped.needsEnergyRecovery, "Speed inside the risky limit passed the old gate, so a Trackblazer Reset Whistle stays allowed")
    }

    @Test
    fun `the Finals keep their path, all over the limit leaves an empty map and no energy-recovery flag`() {
        val training = training()
        training.read(StatName.entries.map { result(it, 40) }, speedOverLimit = false)
        assertTrue(training.trainingMap.isEmpty(), "an empty map reaches the Finale Wit or rest branch as before")
        assertFalse(training.needsEnergyRecovery)
    }

    @Test
    fun `risky training over Speed's risky limit still trains a facility inside it`() {
        val training = training(maximumFailureChance = 15, risky = true, riskyMax = 30, riskyMinGain = 20)
        training.read(
            listOf(result(StatName.SPEED, 40, mainGain = 30), result(StatName.POWER, 28, mainGain = 30), result(StatName.GUTS, 20, mainGain = 10), result(StatName.WIT, 35, mainGain = 30)),
            speedOverLimit = true,
        )
        assertEquals(setOf(StatName.POWER), training.trainingMap.keys)
        assertFalse(training.needsEnergyRecovery)
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
    fun `Speed's value no longer skips the read, the gate condition only feeds the rest decision`() {
        val analyze = between("    fun analyzeTrainings(args: Map<String, Any?> = emptyMap()) {", "    private fun processAnalysisResults(")
        assertTrue(
            analyze.contains("val speedOverLimit = !(test || isWithinRegularThreshold || isWithinRiskyThreshold || isFinals || ignoreFailureChance)"),
            "the old gate condition, unchanged, decides only whether an empty result rests",
        )
        assertFalse(analyze.contains("if (test || isWithinRegularThreshold || isWithinRiskyThreshold || isFinals || ignoreFailureChance) {"), "no early exit on Speed's value")
        assertFalse(analyze.contains("early exit"), "the old early-exit comment is gone")
        val probe = analyze.indexOf("val speedOverLimit")
        val loop = analyze.indexOf("for (statName in StatName.entries) {")
        val decide = analyze.indexOf("markRestIfNoTrainingSurvived(speedOverLimit)")
        assertTrue(probe in 0 until loop && loop < decide, "Speed probe, then every facility, then the rest decision")
        assertEquals(1, Regex("""needsEnergyRecovery = true""").findAll(analyze).count(), "only the energy-0 skip sets the flag directly")
        assertTrue(analyze.contains("exceeds every failure chance limit. Reading every training"), "the over-limit line names every limit, risky included")
    }

    @Test
    fun `a read counts as completed only once it reaches the rest decision`() {
        val training = training()
        val completed = Training::class.java.getDeclaredField("analysisCompleted").apply { isAccessible = true }
        assertFalse(completed.getBoolean(training))
        training.read(turn(StatName.SPEED to 30, StatName.WIT to 0), speedOverLimit = true)
        assertTrue(completed.getBoolean(training))

        // Every early return (energy 0, Speed unread, a failed tab switch, the bot stopping) comes after the reset and before the decision.
        val analyze = between("    fun analyzeTrainings(args: Map<String, Any?> = emptyMap()) {", "    private fun processAnalysisResults(")
        val reset = analyze.indexOf("analysisCompleted = false")
        assertTrue(reset in 0 until analyze.indexOf("return"), "the flag is reset before the first return")
        assertFalse(between("fun clearAnalysisCache() {", "    }").contains("analysisCompleted"), "the item dialog's cache clear must not undo a completed read")
    }

    @Test
    fun `a Trackblazer Reset Whistle needs a completed read this turn`() {
        val trackblazer = kotlinFile("bot/campaigns/Trackblazer.kt").readText().replace("\r\n", "\n")
        val gate = trackblazer.lines().single { it.contains("if (date.day >= 13 && !bUsedWhistleToday && trainingSelected == null") }
        assertTrue(gate.contains("!training.needsEnergyRecovery && training.analysisCompleted"), gate)
    }

    @Test
    fun `a same-turn cache hit replays the rest decision under the same Speed result`() {
        val cacheHit = between("[TRAINING] Using cached training analysis results for this turn.", "return")
        assertTrue(cacheHit.contains("markRestIfNoTrainingSurvived(cachedSpeedOverLimit)"))
        val write = between("cachedAnalysisResults = analysisResults.toList()", "cachedAnalysisTurn = campaign.date.day")
        assertTrue(write.contains("cachedSpeedOverLimit = speedOverLimit"))
        val clear = between("fun clearAnalysisCache() {", "    }")
        assertTrue(clear.contains("cachedSpeedOverLimit = false"))
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
