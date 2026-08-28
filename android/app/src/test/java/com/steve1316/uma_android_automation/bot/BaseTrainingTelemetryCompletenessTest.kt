package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Base-training telemetry completeness.
 *
 * The canonical five-facility contest (P01/P02) already records the picked training's failure chance and stat gains.
 * Four rare forced/fallback TRAIN paths did not: P03 FORCED_DEFAULT and P04 UNFORCED_DEFAULT recorded the facility but
 * no evidence, P05 pre-summer forced Wit recorded no TrainingSelection at all, and P06 forced Finale Wit recorded
 * selected=null then trained Wit. All four now attach same-turn analysis evidence through one pure helper, with no
 * schema change and no runtime behavior change. The pure helper is exercised behaviorally; the forced/fallback wiring
 * (which needs a live Game to run) is pinned with source guards.
 */
@DisplayName("Base-training telemetry completeness")
class BaseTrainingTelemetryCompletenessTest {
    private fun option(name: StatName, fail: Int, gains: Map<StatName, Int>): Training.TrainingOption =
        Training.TrainingOption(name = name, statGains = gains, failureChance = fail, relationshipBars = arrayListOf(), numRainbow = 0)

    @Nested
    @DisplayName("selectedTrainingEvidence helper")
    inner class HelperBehavior {
        @Test
        fun `trainingMap takes precedence over skippedTrainingMap`() {
            val primary = mapOf(StatName.WIT to option(StatName.WIT, 3, mapOf(StatName.WIT to 12)))
            val skipped = mapOf(StatName.WIT to option(StatName.WIT, 40, mapOf(StatName.WIT to 99)))
            val ev = Training.selectedTrainingEvidence(primary, skipped, StatName.WIT)
            assertEquals(3, ev.pickedFailureChance)
            assertEquals(mapOf(StatName.WIT to 12), ev.pickedStatGains)
        }

        @Test
        fun `skippedTrainingMap is used when the primary map lacks the facility`() {
            val skipped = mapOf(StatName.WIT to option(StatName.WIT, 25, mapOf(StatName.WIT to 8)))
            val ev = Training.selectedTrainingEvidence(emptyMap(), skipped, StatName.WIT)
            assertEquals(25, ev.pickedFailureChance)
            assertEquals(mapOf(StatName.WIT to 8), ev.pickedStatGains)
        }

        @Test
        fun `a negative failure chance maps to null (OCR did not measure it)`() {
            val primary = mapOf(StatName.SPEED to option(StatName.SPEED, -1, mapOf(StatName.SPEED to 15)))
            val ev = Training.selectedTrainingEvidence(primary, emptyMap(), StatName.SPEED)
            assertNull(ev.pickedFailureChance)
            assertEquals(mapOf(StatName.SPEED to 15), ev.pickedStatGains, "gains stay even when the failure chance is unmeasured")
        }

        @Test
        fun `a nonnegative failure chance is preserved, including zero`() {
            val primary = mapOf(StatName.GUTS to option(StatName.GUTS, 0, mapOf(StatName.GUTS to 5)))
            assertEquals(0, Training.selectedTrainingEvidence(primary, emptyMap(), StatName.GUTS).pickedFailureChance)
        }

        @Test
        fun `stat gains are preserved exactly`() {
            val gains = mapOf(StatName.POWER to 10, StatName.STAMINA to 4)
            val primary = mapOf(StatName.POWER to option(StatName.POWER, 7, gains))
            assertEquals(gains, Training.selectedTrainingEvidence(primary, emptyMap(), StatName.POWER).pickedStatGains)
        }

        @Test
        fun `a missing facility returns null gains and null failChance`() {
            val ev = Training.selectedTrainingEvidence(emptyMap(), emptyMap(), StatName.WIT)
            assertNull(ev.pickedFailureChance)
            assertNull(ev.pickedStatGains)
        }

        @Test
        fun `a null stat returns null gains and null failChance`() {
            val primary = mapOf(StatName.WIT to option(StatName.WIT, 3, mapOf(StatName.WIT to 12)))
            val ev = Training.selectedTrainingEvidence(primary, emptyMap(), null)
            assertNull(ev.pickedFailureChance)
            assertNull(ev.pickedStatGains)
        }

        @Test
        fun `the helper mutates neither input map`() {
            val primary = mapOf(StatName.WIT to option(StatName.WIT, 3, mapOf(StatName.WIT to 12)))
            val skipped = mapOf(StatName.SPEED to option(StatName.SPEED, 20, mapOf(StatName.SPEED to 9)))
            Training.selectedTrainingEvidence(primary, skipped, StatName.WIT)
            assertEquals(setOf(StatName.WIT), primary.keys)
            assertEquals(setOf(StatName.SPEED), skipped.keys)
        }
    }

    @Nested
    @DisplayName("forced/fallback wiring (source guard)")
    inner class ForcedFallbackWiring {
        private val training by lazy { sourceFile("bot/Training.kt").readText().replace("\r\n", "\n") }

        private fun window(fromAnchor: String, toAnchor: String): String {
            val start = training.indexOf(fromAnchor)
            require(start >= 0) { "missing anchor: $fromAnchor" }
            val end = training.indexOf(toAnchor, start)
            require(end > start) { "missing anchor: $toAnchor" }
            return training.substring(start, end)
        }

        // Body of handleTrainingWithOutcome only, so the P05 windows and indices below cannot be satisfied by the
        // identically named trainingMap.isEmpty() check that lives in another function earlier in the file.
        private val handleBody by lazy {
            val start = training.indexOf("fun handleTrainingWithOutcome(")
            require(start >= 0) { "missing handleTrainingWithOutcome" }
            val end = training.indexOf("fun executeTraining(trainingSelected: StatName?)", start)
            require(end > start) { "missing executeTraining declaration after handleTrainingWithOutcome" }
            training.substring(start, end)
        }

        @Test
        fun `P03 FORCED_DEFAULT attaches same-turn evidence without altering source or reason`() {
            assertTrue(training.contains("val defaultEvidence = selectedTrainingEvidence(trainingMap, skippedTrainingMap, defaulted)"))
            val call = window("val defaultEvidence = selectedTrainingEvidence", "return defaulted")
            assertTrue(call.contains("source = SelectionSource.FORCED_DEFAULT"), "source preserved")
            assertTrue(call.contains("reason = \"analysis produced no scored entries; forced first non-blacklisted training\""), "reason preserved")
            assertTrue(call.contains("runnerUps = buildTracerRunnerUps("), "existing runner-up semantics preserved (a contest ran)")
            assertTrue(call.contains("pickedFailureChance = defaultEvidence.pickedFailureChance"), "picked failChance now attached")
            assertTrue(call.contains("pickedStatGains = defaultEvidence.pickedStatGains"), "picked gains now attached")
        }

        @Test
        fun `P04 UNFORCED_DEFAULT attaches same-turn evidence without altering source or reason`() {
            assertTrue(training.contains("val unforcedEvidence = selectedTrainingEvidence(trainingMap, skippedTrainingMap, unforced)"))
            val call = window("val unforcedEvidence = selectedTrainingEvidence", "return unforced")
            assertTrue(call.contains("source = SelectionSource.UNFORCED_DEFAULT"), "source preserved")
            assertTrue(call.contains("runnerUps = buildTracerRunnerUps("), "existing runner-up semantics preserved")
            assertTrue(call.contains("pickedFailureChance = unforcedEvidence.pickedFailureChance"), "picked failChance now attached")
            assertTrue(call.contains("pickedStatGains = unforcedEvidence.pickedStatGains"), "picked gains now attached")
        }

        @Test
        fun `exactly one recordTrainingSelection remains at each of P01-P04 and one new event at P05 and P06`() {
            // 4 in recommendTraining (P01 ANALYSIS, P02 FORCED_FROM_SKIPPED, P03, P04) + 2 new (P05 forceStat, P06 Finale Wit).
            assertEquals(6, Regex("recordTrainingSelection\\(").findAll(training).count(), "no duplicate events introduced")
        }

        @Test
        fun `P05 forceStat records the committed pick inside the non-empty execution branch, gated on forceStat, before executeTraining`() {
            // The committed branch is the else of trainingMap.isEmpty(): the only path that actually trains.
            val branchStart = handleBody.indexOf("Now select the training option with the highest weight.")
            val execute = handleBody.indexOf("executeTraining(trainingSelected)")
            assertTrue(branchStart in 0 until execute, "the committed branch precedes executeTraining")
            val branch = handleBody.substring(branchStart, execute)
            assertTrue(branch.contains("tracer.recordTrainingSelection("), "the committed forced pick is recorded on the training path")
            assertTrue(branch.contains("selected = forceStat"), "selected is the forced facility")
            assertTrue(branch.contains("source = SelectionSource.FORCED_DEFAULT"), "reuses the existing forced/default source")
            assertTrue(branch.contains("reason = \"forced training override: \$forceStat\""), "factual forced-override reason, not a ranked-analyzer claim")
            assertTrue(branch.contains("selectedTrainingEvidence(trainingMap, skippedTrainingMap, forceStat)"), "evidence comes from the same-turn helper")
            assertFalse(branch.contains("runnerUps"), "no fabricated ranked contest for a forced pick")

            val gate = branch.indexOf("if (forceStat != null) {")
            val record = branch.indexOf("tracer.recordTrainingSelection(")
            assertTrue(gate in 0 until record, "the record is gated by forceStat != null")

            // Whole-function ordering: the empty-map decision resolves first, then (in its else) the record
            // fires, then executeTraining runs. That is what makes this a committed-choice record.
            val isEmpty = handleBody.indexOf("if (trainingMap.isEmpty()) {")
            val recordAbs = handleBody.indexOf("reason = \"forced training override:")
            assertTrue(isEmpty in 0 until recordAbs, "the forced record is emitted after the empty-map decision, not before it")
            assertTrue(recordAbs in 0 until execute, "the committed pick is recorded before the training mutation")
        }

        @Test
        fun `the forceStat selection branch no longer records a TrainingSelection before the empty-map decision`() {
            // Pre-repair bug guard: a record here mislabels an empty-map forced-Wit rest as a training.
            val branchStart = handleBody.indexOf("forceStat override active")
            val isEmpty = handleBody.indexOf("if (trainingMap.isEmpty()) {")
            assertTrue(branchStart in 0 until isEmpty, "the forceStat selection branch precedes the empty-map decision")
            assertFalse(
                handleBody.substring(branchStart, isEmpty).contains("recordTrainingSelection"),
                "no selection is recorded before trainingMap.isEmpty() is known",
            )
        }

        @Test
        fun `the empty-map recovery region emits no forced-override TrainingSelection`() {
            // Forced Finale Wit (P06) may record on its successful tap, but a pre-summer forced Wit that falls
            // through to recoverEnergy() must not, so the P05 forced-override reason is absent from this block.
            val isEmpty = handleBody.indexOf("if (trainingMap.isEmpty()) {")
            val commit = handleBody.indexOf("Now select the training option with the highest weight.")
            assertTrue(isEmpty in 0 until commit, "the empty-map block precedes the committed branch")
            assertFalse(
                handleBody.substring(isEmpty, commit).contains("forced training override:"),
                "the empty-map/recovery path records no forced override",
            )
        }

        @Test
        fun `exactly one forced-override reason exists after relocation`() {
            assertEquals(1, Regex("forced training override:").findAll(training).count(), "the P05 record was moved, not duplicated")
        }

        @Test
        fun `P06 forced Finale Wit records a late authoritative WIT selection only on the successful-tap branch`() {
            // Window runs from the success log to the recovery-branch log, so a record inside it is on the success path only.
            val p06 = window("Successfully forced Wit training during the Finale", "Could not find Wit training button")
            assertTrue(p06.contains("tracer.recordTrainingSelection("), "the successful forced Wit tap now records a TrainingSelection")
            assertTrue(p06.contains("selected = StatName.WIT"), "selected is WIT")
            assertTrue(p06.contains("source = SelectionSource.FORCED_DEFAULT"), "reuses the forced/default source")
            assertTrue(p06.contains("reason = \"forced Wit training during Finale\""), "reason explicitly identifies the forced Finale path")
            assertTrue(p06.contains("selectedTrainingEvidence(trainingMap, skippedTrainingMap, StatName.WIT)"), "evidence comes from the same-turn helper")
            assertFalse(p06.contains("runnerUps"), "no fabricated ranked contest for a forced pick")
        }

        @Test
        fun `neither empty-map recover-energy fallback records a training selection`() {
            // Spans the failed forced-Wit tap and both recover-energy fallbacks, up to (not including) the
            // non-empty commit branch. P06 sits above this window; the committed P05 sits below it, so a rest
            // is never mislabeled as a training.
            val recovery = window("Could not find Wit training button", "Now select the training option with the highest weight.")
            assertFalse(recovery.contains("recordTrainingSelection"), "the recover-energy fallbacks record no training selection")
        }
    }

    @Nested
    @DisplayName("trainingCandidateEvidence converter")
    inner class CandidateEvidenceConverter {
        @Test
        fun `copies the already-computed scalar evidence off the option`() {
            val option =
                Training.TrainingOption(
                    name = StatName.SPEED,
                    statGains = mapOf(StatName.SPEED to 12),
                    failureChance = 6,
                    relationshipBars = arrayListOf(),
                    numRainbow = 2,
                    numSpiritGaugesCanFill = 1,
                    numSpiritGaugesReadyToBurst = 3,
                    numSkillHints = 4,
                    trainingLevel = 5,
                )
            val ev = Training.trainingCandidateEvidence(option)
            assertEquals(2, ev.numRainbow)
            assertEquals(4, ev.numSkillHints)
            assertEquals(5, ev.trainingLevel)
            // Spirit gauges are carried raw; the serializer, not the converter, gates them on Unity Cup.
            assertEquals(1, ev.numSpiritGaugesCanFill)
            assertEquals(3, ev.numSpiritGaugesReadyToBurst)
        }

        @Test
        fun `an unread training level stays null`() {
            val option = Training.TrainingOption(name = StatName.WIT, statGains = emptyMap(), failureChance = 0, relationshipBars = arrayListOf(), numRainbow = 0, trainingLevel = null)
            assertNull(Training.trainingCandidateEvidence(option).trainingLevel)
        }

        @Test
        fun `reduces relationship bars to the scorer-relevant fields`() {
            val bars = arrayListOf(CustomImageUtils.BarFillResult(statName = StatName.SPEED, fillPercent = 62.5, filledSegments = 3, dominantColor = "green"))
            val option = Training.TrainingOption(name = StatName.SPEED, statGains = emptyMap(), failureChance = 0, relationshipBars = bars, numRainbow = 0)
            val out = Training.trainingCandidateEvidence(option).relationshipBars
            assertEquals(1, out.size)
            assertEquals(62.5, out[0].fillPercent)
            assertEquals(3, out[0].filledSegments)
            assertEquals("green", out[0].dominantColor)
            // No stat block -> not a trainer support, and no fabricated trainer identity.
            assertFalse(out[0].isSupport)
            assertNull(out[0].trainerName)
        }

        @Test
        fun `drops an unreadable (null) performance-gain value rather than emitting zero`() {
            val option =
                Training.TrainingOption(
                    name = StatName.SPEED,
                    statGains = emptyMap(),
                    failureChance = 0,
                    relationshipBars = arrayListOf(),
                    numRainbow = 0,
                    performanceGains = mapOf(PerformancePointType.DANCE to 4, PerformancePointType.VOCAL to null),
                )
            val gains = Training.trainingCandidateEvidence(option).performanceGains
            assertEquals(mapOf("Dance" to 4), gains, "a null (unreadable) type value is omitted, not zero-filled")
        }
    }

    @Nested
    @DisplayName("architecture freeze (source guard)")
    inner class ArchitectureFreeze {
        private val training by lazy { sourceFile("bot/Training.kt").readText().replace("\r\n", "\n") }
        private val decisionTrace by lazy { sourceFile("bot/DecisionTrace.kt").readText().replace("\r\n", "\n") }

        @Test
        fun `DecisionTrace SCHEMA_VERSION is unchanged`() {
            assertTrue(decisionTrace.contains("const val SCHEMA_VERSION: Int = 1"), "no schema version bump")
        }

        @Test
        fun `the final TrainingSelection stays authoritative via lastOrNull in both builders`() {
            assertEquals(
                2,
                Regex("filterIsInstance<DecisionTracer\\.DecisionEvent\\.TrainingSelection>\\(\\)\\.lastOrNull\\(\\)").findAll(decisionTrace).count(),
                "buildSelected and buildCandidates both take the last TrainingSelection, so a late forced event wins",
            )
        }

        @Test
        fun `picked evidence still serializes onto the selected candidate as failChance and gains`() {
            assertTrue(decisionTrace.contains("event.pickedFailureChance?.let { put(\"failChance\", it) }"))
            assertTrue(decisionTrace.contains("event.pickedStatGains?.let { put(\"gains\", statGains(it)) }"))
        }

        @Test
        fun `P01 ANALYSIS and P02 FORCED_FROM_SKIPPED remain complete`() {
            assertTrue(training.contains("pickedFailureChance = best.failureChance.takeIf { it >= 0 }"), "P01 still carries evidence")
            assertTrue(training.contains("pickedStatGains = best.statGains"), "P01 still carries gains")
            assertTrue(training.contains("pickedFailureChance = pick.failureChance.takeIf { it >= 0 }"), "P02 still carries evidence")
            assertTrue(training.contains("pickedStatGains = pick.statGains"), "P02 still carries gains")
        }

        @Test
        fun `the evidence helper stays pure over its argument maps (no OCR, tap, wait, or map mutation in its body)`() {
            val start = training.indexOf("internal fun selectedTrainingEvidence(")
            assertTrue(start >= 0)
            val body = training.substring(start, training.indexOf("\n        }\n", start) + 1)
            for (forbidden in listOf("getSourceBitmap", ".tap(", "game.wait", ".clear()", ".put(", "recommendTraining")) {
                assertFalse(body.contains(forbidden), "helper must not $forbidden")
            }
        }
    }

    private fun sourceFile(relative: String): File = File(kotlinRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
