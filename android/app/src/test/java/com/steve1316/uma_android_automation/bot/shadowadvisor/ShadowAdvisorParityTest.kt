package com.steve1316.uma_android_automation.bot.shadowadvisor

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

/**
 * Cross-language golden parity, Kotlin side. Loads the same `src/lib/shadowAdvisor/__fixtures__/parity.json` the
 * Jest suite asserts against and proves the Kotlin adapter + policy reproduce, field-for-field, the context and
 * recommendation the authoritative TypeScript S1 computed: status, recommended action/training, scoreMargin,
 * reasons (including exact detail strings and their JS-formatted numbers), limitation strings, scoreBreakdown,
 * advisorVersion, and policyId. The recommendation JSON is built by the real [ShadowAdvisorRecord] serializer, so
 * this also pins the runtime record's number formatting.
 */
@DisplayName("shadow advisor cross-language golden parity")
class ShadowAdvisorParityTest {
    private val fixtures: JSONArray by lazy {
        JSONArray(File(repoRoot(), "src/lib/shadowAdvisor/__fixtures__/parity.json").readText())
    }

    @TestFactory
    fun `every fixture reproduces the same context and recommendation`(): List<DynamicTest> {
        val out = ArrayList<DynamicTest>()
        assertTrue(fixtures.length() >= 17, "the fixture set must cover the required branches")
        for (i in 0 until fixtures.length()) {
            val fixture = fixtures.getJSONObject(i)
            out.add(
                DynamicTest.dynamicTest(fixture.getString("name")) {
                    val serializedTrace = fixture.getJSONObject("decisionTrace").toString()
                    val serializedState = fixture.opt("careerState").let { if (it is JSONObject) it.toString() else null }

                    val context = ShadowAdvisorContext.buildContextFromRecords(serializedTrace, serializedState)
                    assertNotNull(context, "context must not be null for a seq'd trace")

                    assertEquals(parseExpectedContext(fixture.getJSONObject("expectedContext")), context, "context mismatch")

                    val recommendation = ShadowAdvisorPolicy.recommend(context!!)
                    val actual = recommendationJson(recommendation)
                    val expected = fixture.getJSONObject("expectedRecommendation")
                    assertTrue(deepEqual(actual, expected), "recommendation mismatch\n actual=$actual\n expected=$expected")
                },
            )
        }
        return out
    }

    /** The recommendation as the TS ShadowRecommendation shape, via the real record serializer minus the record envelope. */
    private fun recommendationJson(recommendation: ShadowRecommendation): JSONObject {
        val record = ShadowAdvisorRecord.build(recommendation, scenarioType = null, timestamp = 0L)
        for (envelopeKey in listOf("type", "v", "ts", "source", "scenarioType")) record.remove(envelopeKey)
        return record
    }

    /** Rebuilds an AdvisorDecisionContext from the fixture's expectedContext JSON so a Kotlin structural compare applies. */
    private fun parseExpectedContext(json: JSONObject): AdvisorDecisionContext {
        val stateJson = json.getJSONObject("state")
        val contestJson = json.optJSONObject("trainingContest")
        return AdvisorDecisionContext(
            careerToken = json.getString("careerToken"),
            seq = json.getInt("seq"),
            turn = intOrNull(json, "turn"),
            scenarioType = stringOrNull(json, "scenarioType"),
            state =
                AdvisorState(
                    energy = doubleOrNull(stateJson, "energy"),
                    mood = stringOrNull(stateJson, "mood"),
                    negativeStatuses = stateJson.optJSONArray("negativeStatuses")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } },
                    stats = numberMapOrNull(stateJson.optJSONObject("stats")),
                    skillPts = doubleOrNull(stateJson, "skillPts"),
                    raceFlags = stateJson.optJSONObject("raceFlags")?.let { AdvisorRaceFlags(it.getBoolean("mandatory"), it.getBoolean("scheduled"), it.getBoolean("goalRibbon")) },
                ),
            trainingContest =
                contestJson?.let { c ->
                    val facilities = c.getJSONArray("facilities")
                    AdvisorTrainingContest(
                        complete = c.getBoolean("complete"),
                        facilities =
                            (0 until facilities.length()).map { idx ->
                                val f = facilities.getJSONObject(idx)
                                AdvisorFacilityFact(id = f.getString("id"), gains = numberMapOrNull(f.optJSONObject("gains")), failChance = doubleOrNull(f, "failChance"))
                            },
                    )
                },
        )
    }

    private fun numberMapOrNull(obj: JSONObject?): Map<String, Double>? {
        if (obj == null) return null
        val out = LinkedHashMap<String, Double>()
        for (key in obj.keys()) (obj.opt(key) as? Number)?.let { out[key] = it.toDouble() }
        return out
    }

    private fun intOrNull(obj: JSONObject, key: String): Int? = (obj.opt(key) as? Number)?.toInt()

    private fun doubleOrNull(obj: JSONObject, key: String): Double? = (obj.opt(key) as? Number)?.toDouble()

    private fun stringOrNull(obj: JSONObject, key: String): String? = obj.opt(key) as? String

    /** Deep JSON equality treating an absent key, JSON null, and JSONObject.NULL as equivalent, and comparing numbers numerically. */
    private fun deepEqual(actual: Any?, expected: Any?): Boolean {
        val a = normalizeNull(actual)
        val e = normalizeNull(expected)
        if (a == null || e == null) return a == null && e == null
        return when {
            a is JSONObject && e is JSONObject -> {
                val keys = a.keys().asSequence().toMutableSet().apply { addAll(e.keys().asSequence()) }
                keys.all { deepEqual(a.opt(it), e.opt(it)) }
            }
            a is JSONArray && e is JSONArray -> a.length() == e.length() && (0 until a.length()).all { deepEqual(a.opt(it), e.opt(it)) }
            a is Number && e is Number -> a.toDouble() == e.toDouble()
            a is Boolean && e is Boolean -> a == e
            a is String && e is String -> a == e
            else -> false
        }
    }

    private fun normalizeNull(v: Any?): Any? = if (v == null || v == JSONObject.NULL) null else v

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            if (File(dir, "src/lib/shadowAdvisor/__fixtures__/parity.json").isFile) return dir!!
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the repo root (parity.json) from ${System.getProperty("user.dir")}")
    }
}
