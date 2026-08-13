package com.steve1316.uma_android_automation.bot.shadowadvisor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Behavioral tests for the live sink's pure core and for the JS-style number formatter. The append and gameplay
 * isolation of [ShadowAdvisorSink.onDecisionTraceAppended] itself are pinned by [ShadowAdvisorNonInterferenceTest]
 * (source contract), since the write path needs an Android Context; here we exercise the decision logic that
 * decides whether and what to record.
 */
@DisplayName("shadow advisor sink + formatting")
class ShadowAdvisorSinkTest {
    private val token = "Copano Rickey|URA Finale|run1|abc"

    private fun trainCandidate(id: String, stat: String, gain: Int, failChance: Int): String =
        """{"type":"training","id":"$id","gains":{"$stat":$gain},"failChance":$failChance}"""

    private val completeTrace: String =
        """{"type":"decision_trace","seq":7,"turn":20,"careerToken":"$token","candidates":[${
            listOf(
                trainCandidate("SPEED", "spd", 20, 10),
                trainCandidate("STAMINA", "sta", 12, 8),
                trainCandidate("POWER", "pwr", 10, 6),
                trainCandidate("GUTS", "grt", 8, 4),
                trainCandidate("WIT", "wit", 6, 2),
            ).joinToString(",")
        }]}"""

    private val noContestTrace: String =
        """{"type":"decision_trace","seq":7,"turn":20,"careerToken":"$token","candidates":[{"type":"action","id":"REST","selected":true}]}"""

    private val stateWithRace: String =
        """{"seq":7,"identity":{"careerToken":"$token"},"condition":{"energy":70,"mood":"GREAT","negativeStatuses":[]},"stats":{"spd":300},"skillPts":100,"race":{"mandatory":false,"scheduled":false,"goalRibbon":false},"scenario":{"type":"URA Finale"}}"""

    @Nested
    @DisplayName("evaluate() decision logic")
    inner class Evaluate {
        @Test
        fun `a null trace seq is never recorded`() {
            assertNull(ShadowAdvisorSink().evaluate(completeTrace, traceSeq = null, serializedState = stateWithRace, retainedStateSeq = 7))
        }

        @Test
        fun `a seq equal to the last emitted seq is skipped (at most one per seq)`() {
            val sink = ShadowAdvisorSink()
            sink.lastEmittedSeq = 7
            assertNull(sink.evaluate(completeTrace, traceSeq = 7, serializedState = stateWithRace, retainedStateSeq = 7))
        }

        @Test
        fun `a fresh seq builds a recommendationAvailable TRAIN record`() {
            val record = ShadowAdvisorSink().evaluate(completeTrace, traceSeq = 7, serializedState = stateWithRace, retainedStateSeq = 7)!!
            assertEquals("shadow_advisor", record.getString("type"))
            assertEquals("live_shadow", record.getString("source"))
            assertEquals(7, record.getInt("seq"))
            assertEquals("recommendationAvailable", record.getString("status"))
            assertEquals("TRAIN", record.getJSONObject("recommended").getString("action"))
            assertEquals("SPEED", record.getJSONObject("recommended").getString("trainingType"))
        }

        @Test
        fun `the retained state is used only when its seq matches the trace seq`() {
            // Matching seq: no-contest + state present + no race -> notApplicable (state was read).
            val matched = ShadowAdvisorSink().evaluate(noContestTrace, traceSeq = 7, serializedState = stateWithRace, retainedStateSeq = 7)!!
            assertEquals("notApplicable", matched.getString("status"))
            // Mismatched seq: the state is dropped, so the same no-contest trace becomes stateUnavailable.
            val mismatched = ShadowAdvisorSink().evaluate(noContestTrace, traceSeq = 7, serializedState = stateWithRace, retainedStateSeq = 4)!!
            assertEquals("insufficientEvidence", mismatched.getString("status"))
            assertEquals("stateUnavailable", mismatched.getJSONArray("reasons").getJSONObject(0).getString("code"))
        }

        @Test
        fun `post-execution blocks on the trace and state are ignored (purity)`() {
            val plain = ShadowAdvisorSink().evaluate(completeTrace, 7, stateWithRace, 7)!!
            // The same contest with a committed selection, candidate score, enteredRace and recovery attached.
            val poisonedTrace =
                completeTrace.dropLast(1) +
                    ""","selected":{"action":"TRAIN","trainingType":"GUTS","trainingSource":"FORCED_DEFAULT","score":999},"enteredRace":{"valid":true},"recovery":{"kind":"rest"}}"""
            val withBlocks = ShadowAdvisorSink().evaluate(poisonedTrace, 7, stateWithRace, 7)!!
            // Compare everything except the wall-clock ts stamped inside build().
            plain.remove("ts")
            withBlocks.remove("ts")
            assertEquals(plain.toString(), withBlocks.toString(), "forbidden post-execution blocks must not change the recommendation")
        }

        @Test
        fun `unparseable trace JSON throws so the caller's isolation can catch it`() {
            assertThrows(org.json.JSONException::class.java) {
                ShadowAdvisorSink().evaluate("{ not json", traceSeq = 7, serializedState = null, retainedStateSeq = null)
            }
        }
    }

    @Nested
    @DisplayName("JsNumber JS-style formatting")
    inner class Formatting {
        @Test
        fun `an integer-valued double drops the trailing point-zero`() {
            assertEquals("72", JsNumber.format(72.0))
            assertEquals("0", JsNumber.format(0.0))
            assertEquals("0", JsNumber.format(-0.0))
            assertEquals("-3", JsNumber.format(-3.0))
            assertEquals("40", JsNumber.format(40.0))
        }

        @Test
        fun `a half-step double keeps one decimal`() {
            assertEquals("71.5", JsNumber.format(71.5))
            assertEquals("-3.5", JsNumber.format(-3.5))
            assertEquals("0.5", JsNumber.format(0.5))
            assertEquals("4.5", JsNumber.format(4.5))
        }

        @Test
        fun `round1 stays exact on the advisor domain of integers and half-steps`() {
            // Integers and half-integers are exactly representable, so round1 is a no-op on the advisor's domain.
            assertEquals("72", JsNumber.format(JsNumber.round1(72.0)))
            assertEquals("71.5", JsNumber.format(JsNumber.round1(71.5)))
            assertEquals("-2.5", JsNumber.format(JsNumber.round1(-2.5)))
            assertEquals("0", JsNumber.format(JsNumber.round1(0.0)))
        }
    }
}
