package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.Aptitude
import com.steve1316.uma_android_automation.types.DateMonth
import com.steve1316.uma_android_automation.types.DatePhase
import com.steve1316.uma_android_automation.types.DateYear
import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.Mood
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.types.TrackDistance
import com.steve1316.uma_android_automation.types.Trainee
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pure serialization contract tests for `career_state` v1. The serializer reads only the immutable
 * [CareerState] plus the caller-supplied seq/timestamp, so these run headless. They pin the wire shape
 * a Phase-B / ReplayLab consumer joins on, and the honesty rules (omit-when-unread, no fans, no
 * decision evidence) that keep the record from fabricating state.
 */
@DisplayName("CareerState v1 serialization")
class CareerStateSerializerTest {
    private fun identity(): CareerIdentity =
        CareerStateBuilder.buildIdentity(
            scenario = "Trackblazer",
            traineeName = "Biwa Hayahide",
            presetRaw = "Biwa Hayahide",
            queueRunRaw = 2,
            careerNonce = "abc123",
            configFingerprint = "fp1",
        )

    private fun observedDate(day: Int = 5): GameDate = GameDate(day).apply { updateDay(day) }

    private fun observedTrainee(): Trainee =
        Trainee().apply {
            name = "Biwa Hayahide"
            energy = 62
            mood = Mood.GOOD
            skillPoints = 340
            fans = 12000
            setTraineeStats(speed = 412, stamina = 300, power = 288, guts = 190, wit = 260)
            bHasUpdatedStats = true
            bHasUpdatedSkillPoints = true
            bHasUpdatedAptitudes = true
        }

    private fun build(
        date: GameDate = observedDate(),
        trainee: Trainee = observedTrainee(),
        scenario: ScenarioState? = null,
        mandatoryRaceDay: Boolean = false,
        scheduledRaceDay: Boolean = false,
        goalRibbonDay: Boolean = false,
    ): CareerState =
        CareerStateBuilder.build(identity(), date, trainee, mandatoryRaceDay, scheduledRaceDay, goalRibbonDay, scenario)

    private fun serialize(state: CareerState, seq: Int = 1, ts: Long = 1_700_000_000_000L): JSONObject =
        CareerStateSerializer.buildRecord(state, seq, ts)

    // ---- Envelope ----

    @Test
    @DisplayName("The record carries the required type / version / timestamp / positive seq")
    fun envelopeFieldsArePresent() {
        val json = serialize(build(), seq = 7, ts = 1_700_000_000_123L)
        assertEquals("career_state", json.getString("type"))
        assertEquals(1, json.getInt("v"))
        assertEquals(1_700_000_000_123L, json.getLong("ts"))
        assertEquals(7, json.getInt("seq"))
        assertTrue(json.getInt("seq") > 0)
    }

    // ---- Identity ----

    @Test
    @DisplayName("Identity serializes the immutable CareerState identity exactly, without re-deriving it")
    fun identityIsExact() {
        val state = build()
        val id = serialize(state).getJSONObject("identity")
        assertEquals(state.identity.careerToken, id.getString("careerToken"))
        assertEquals("Trackblazer", id.getString("scenario"))
        assertEquals("Biwa Hayahide", id.getString("trainee"))
        assertEquals("Biwa Hayahide", id.getString("preset"))
        assertEquals(2, id.getInt("queueRun"))
        assertEquals(state.identity.configFingerprint, id.getString("fp"))
    }

    @Test
    @DisplayName("Optional preset and queueRun are omitted when the identity has none")
    fun identityOmitsAbsentOptionals() {
        val identityNoPreset =
            CareerStateBuilder.buildIdentity(
                scenario = "URA",
                traineeName = "Biwa Hayahide",
                presetRaw = "",
                queueRunRaw = 0,
                careerNonce = "n",
                configFingerprint = "fp",
            )
        val state = CareerStateBuilder.build(identityNoPreset, observedDate(), observedTrainee(), false, false, false, null)
        val id = serialize(state).getJSONObject("identity")
        assertFalse(id.has("preset"))
        assertFalse(id.has("queueRun"))
    }

    // ---- Date honesty ----

    @Test
    @DisplayName("An observed date serializes turn + year/month/phase and observation.turnObserved=true")
    fun observedDateSerializes() {
        val json = serialize(build(date = observedDate(9)))
        assertEquals(9, json.getInt("turn"))
        assertTrue(json.has("year"))
        assertTrue(json.has("month"))
        assertTrue(json.has("phase"))
        assertTrue(json.getJSONObject("observation").getBoolean("turnObserved"))
    }

    @Test
    @DisplayName("An unobserved date exposes no turn/year/month/phase and observation.turnObserved=false")
    fun unobservedDateIsHonest() {
        val unread = GameDate(year = DateYear.JUNIOR, month = DateMonth.JANUARY, phase = DatePhase.EARLY)
        val json = serialize(build(date = unread))
        assertFalse(json.has("turn"))
        assertFalse(json.has("year"))
        assertFalse(json.has("month"))
        assertFalse(json.has("phase"))
        assertFalse(json.getJSONObject("observation").getBoolean("turnObserved"))
    }

    // ---- Stats / skill points / aptitudes availability ----

    @Test
    @DisplayName("Read stats/skillPts/aptitudes serialize; unread ones are omitted entirely")
    fun availabilityIsHonest() {
        val present = serialize(build())
        assertEquals(412, present.getJSONObject("stats").getInt("spd"))
        assertEquals(260, present.getJSONObject("stats").getInt("wit"))
        assertEquals(340, present.getInt("skillPts"))
        assertTrue(present.getJSONObject("aptitudes").getJSONObject("distance").length() > 0)

        val unread =
            observedTrainee().apply {
                bHasUpdatedStats = false
                bHasUpdatedSkillPoints = false
                bHasUpdatedAptitudes = false
            }
        val absent = serialize(build(trainee = unread))
        assertFalse(absent.has("stats"))
        assertFalse(absent.has("skillPts"))
        assertFalse(absent.has("aptitudes"))
    }

    // ---- Condition ----

    @Test
    @DisplayName("Condition serializes energy/mood; status lists are omitted when empty")
    fun conditionSerializes() {
        val json = serialize(build())
        val condition = json.getJSONObject("condition")
        assertEquals(62, condition.getInt("energy"))
        assertEquals("GOOD", condition.getString("mood"))
        assertFalse(condition.has("negativeStatuses"))
        assertFalse(condition.has("positiveStatuses"))

        val withStatus = observedTrainee().apply { currentNegativeStatuses.add("Headache") }
        val cond2 = serialize(build(trainee = withStatus)).getJSONObject("condition")
        assertEquals("Headache", cond2.getJSONArray("negativeStatuses").getString(0))
    }

    // ---- Race context ----

    @Test
    @DisplayName("Race context serializes the three cached race-day flags")
    fun raceContextSerializes() {
        val json = serialize(build(mandatoryRaceDay = true, scheduledRaceDay = false, goalRibbonDay = true))
        val race = json.getJSONObject("race")
        assertTrue(race.getBoolean("mandatory"))
        assertFalse(race.getBoolean("scheduled"))
        assertTrue(race.getBoolean("goalRibbon"))
    }

    // ---- Scenario extension ----

    @Test
    @DisplayName("Trackblazer state serializes under the trackblazer discriminator with a copied inventory")
    fun trackblazerScenarioSerializes() {
        val tb = TrackblazerState.snapshot(50, mapOf("Empowering Megaphone" to 2), 3, true, false, true, false, 1, 4)
        val scenario = serialize(build(scenario = tb)).getJSONObject("scenario")
        assertEquals("trackblazer", scenario.getString("type"))
        assertEquals(50, scenario.getInt("shopCoins"))
        assertEquals(2, scenario.getJSONObject("inventory").getInt("Empowering Megaphone"))
        assertEquals(3, scenario.getInt("consecutiveRaceCount"))
        assertTrue(scenario.getBoolean("consecutiveRaceCountObserved"))
        assertEquals(4, scenario.getInt("megaphoneTurnCounter"))
    }

    @Test
    @DisplayName("Grand Concert state serializes under the grandConcert discriminator")
    fun grandConcertScenarioSerializes() {
        val gc = GrandConcertState(songsBoughtThisCycle = 1, songsBoughtThisCareer = 5, lastConcertBoundary = 24)
        val scenario = serialize(build(scenario = gc)).getJSONObject("scenario")
        assertEquals("grandConcert", scenario.getString("type"))
        assertEquals(1, scenario.getInt("songsBoughtThisCycle"))
        assertEquals(5, scenario.getInt("songsBoughtThisCareer"))
        assertEquals(24, scenario.getInt("lastConcertBoundary"))
    }

    @Test
    @DisplayName("A scenario with no persistent state (URA / Unity Cup) omits the scenario field entirely")
    fun noExtensionScenarioOmitsField() {
        val json = serialize(build(scenario = null))
        assertFalse(json.has("scenario"))
    }

    // ---- Provenance ----

    @Test
    @DisplayName("Provenance serializes as stable lowercase enum strings across all eight groups")
    fun provenanceMaps() {
        val prov = serialize(build()).getJSONObject("provenance")
        assertEquals("configured", prov.getString("identityInputs"))
        assertEquals("derived", prov.getString("derivedIdentity"))
        assertEquals("observed", prov.getString("date"))
        assertEquals("observed", prov.getString("condition"))
        assertEquals("observed", prov.getString("stats"))
        assertEquals("observed", prov.getString("aptitudes"))
        assertEquals("observed", prov.getString("race"))
        // No scenario extension -> its group provenance is unread.
        assertEquals("unread", prov.getString("scenario"))
    }

    // ---- Exclusions ----

    @Test
    @DisplayName("The record carries no fans and no candidate/score/selection decision evidence")
    fun exclusionsHold() {
        val json = serialize(build(scenario = TrackblazerState.snapshot(0, emptyMap(), 0, false, false, false, false, 0, 0)))
        assertFalse(json.has("fans"))
        assertFalse(json.getJSONObject("condition").has("fans"))
        assertFalse(json.has("candidates"))
        assertFalse(json.has("selected"))
        assertFalse(json.has("raceEligibility"))
    }

    // ---- Determinism ----

    @Test
    @DisplayName("Serializing the same immutable state with the same seq/timestamp is byte-identical")
    fun serializationIsDeterministic() {
        val state = build(scenario = TrackblazerState.snapshot(50, mapOf("Hammer" to 1), 2, true, false, false, false, 0, 3))
        val a = CareerStateSerializer.buildRecord(state, 4, 1_700_000_000_000L).toString()
        val b = CareerStateSerializer.buildRecord(state, 4, 1_700_000_000_000L).toString()
        assertEquals(a, b)
    }
}
