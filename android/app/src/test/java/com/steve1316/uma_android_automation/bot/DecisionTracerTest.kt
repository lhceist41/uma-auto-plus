package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.Training.Companion.buildTracerRunnerUps
import com.steve1316.uma_android_automation.types.DateMonth
import com.steve1316.uma_android_automation.types.DatePhase
import com.steve1316.uma_android_automation.types.DateYear
import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.types.Trainee
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DecisionTracer] and the [Training.buildTracerRunnerUps] runner-up builder that feeds it.
 *
 * These lock the cheapest structural invariants of the per-turn decision report: that `record*` methods
 * append retrievable events, that `emit()` flushes exactly one block and is idempotent within a turn, that
 * the picked training is dropped from the runner-up list, and that a hard-excluded (-Infinity) training
 * surfaces as a scoreless "excluded (hard penalty)" entry rather than a raw "score=-Infinity".
 *
 * `emit()` routes through [MessageLog]; the report block is the last entry it appends to the process-wide
 * message buffer, so the rendering assertions read it back via [MessageLog.getMessageLogCopy] after a
 * [MessageLog.clearLog]. Android framework calls inside `MessageLog` return default values under the
 * module's `testOptions.unitTests.returnDefaultValues`, so no mocking is required.
 */
@DisplayName("DecisionTracer Tests")
class DecisionTracerTest {
    private fun date(): GameDate = GameDate(year = DateYear.CLASSIC, month = DateMonth.JANUARY, phase = DatePhase.EARLY)

    private fun option(stat: StatName, failureChance: Int = 0): Training.TrainingOption =
        Training.TrainingOption(
            name = stat,
            statGains = mapOf(stat to 5),
            failureChance = failureChance,
            relationshipBars = arrayListOf(),
            numRainbow = 0,
        )

    @Test
    @DisplayName("record* methods append retrievable events")
    fun `record methods append events`() {
        val tracer = DecisionTracer()
        // No turn open yet, and startTurn clears any prior events.
        assertNull(tracer.lastTrainingSelection())
        tracer.startTurn(date(), Trainee())
        assertNull(tracer.lastTrainingSelection())

        tracer.recordNote("scenario note")
        tracer.recordRaceEligibility(eligible = false, reason = "consecutive race cap")
        tracer.recordTrainingSelection(
            selected = StatName.SPEED,
            source = SelectionSource.ANALYSIS,
            reason = "won analysis",
            runnerUps = listOf(DecisionTracer.TrainingRunnerUp(stat = StatName.WIT, rejected = false, reason = "outscored", score = 12.0)),
        )

        // The training selection is retrievable through the public accessor.
        val selection = tracer.lastTrainingSelection()
        assertNotNull(selection)
        assertEquals(StatName.SPEED, selection!!.selected)
        assertEquals(SelectionSource.ANALYSIS, selection.source)
        assertEquals(1, selection.runnerUps.size)

        // All three recorded events show up in the emitted block.
        MessageLog.clearLog()
        tracer.emit()
        val report = MessageLog.getMessageLogCopy().single()
        assertTrue(report.contains("Note: scenario note"), report)
        assertTrue(report.contains("Race eligibility: NOT ELIGIBLE"), report)
        assertTrue(report.contains("Training selected: SPEED"), report)
    }

    @Test
    @DisplayName("emit logs one block and ignores repeat calls within a turn")
    fun `emit is idempotent per turn`() {
        // emit() before any startTurn is a no-op (nothing snapshotted).
        MessageLog.clearLog()
        DecisionTracer().emit()
        assertEquals(0, MessageLog.getMessageLogCopy().size)

        val tracer = DecisionTracer()
        tracer.startTurn(date(), Trainee())

        MessageLog.clearLog()
        tracer.emit()
        assertEquals(1, MessageLog.getMessageLogCopy().size)

        // Second emit within the same turn must add nothing.
        tracer.emit()
        assertEquals(1, MessageLog.getMessageLogCopy().size)
    }

    @Test
    @DisplayName("buildTracerRunnerUps omits the picked training")
    fun `picked training is excluded from runner-ups`() {
        val speed = option(StatName.SPEED)
        val wit = option(StatName.WIT)
        val scores = mapOf(speed to 100.0, wit to 50.0)

        val withoutPick = buildTracerRunnerUps(scores, skippedScores = emptyMap(), picked = StatName.SPEED)
        assertEquals(listOf(StatName.WIT), withoutPick.map { it.stat })

        // A null pick keeps every entry, in scored-then-skipped order.
        val all = buildTracerRunnerUps(scores, skippedScores = emptyMap(), picked = null)
        assertEquals(listOf(StatName.SPEED, StatName.WIT), all.map { it.stat })
    }

    @Test
    @DisplayName("buildTracerRunnerUps maps a non-finite score to a scoreless hard-penalty exclusion")
    fun `non-finite score becomes a scoreless exclusion`() {
        val wit = option(StatName.WIT)
        val runnerUps =
            buildTracerRunnerUps(
                trainingScores = emptyMap(),
                skippedScores = mapOf(wit to Double.NEGATIVE_INFINITY),
                picked = null,
            )

        val entry = runnerUps.single()
        assertEquals(StatName.WIT, entry.stat)
        assertTrue(entry.rejected)
        assertEquals("excluded (hard penalty)", entry.reason)
        assertNull(entry.score)
    }

    @Test
    @DisplayName("a scoreless excluded runner-up renders without a raw score")
    fun `excluded runner-up renders with no raw score`() {
        val tracer = DecisionTracer()
        tracer.startTurn(date(), Trainee())
        tracer.recordTrainingSelection(
            selected = StatName.SPEED,
            source = SelectionSource.ANALYSIS,
            reason = "won analysis",
            runnerUps = listOf(DecisionTracer.TrainingRunnerUp(stat = StatName.WIT, rejected = true, reason = "excluded (hard penalty)", score = null)),
        )

        MessageLog.clearLog()
        tracer.emit()
        val report = MessageLog.getMessageLogCopy().single()

        assertTrue(report.contains("- WIT (REJECTED): excluded (hard penalty)"), report)
        assertFalse(report.contains("score="), report)
        assertFalse(report.contains("-Infinity"), report)
    }
}
