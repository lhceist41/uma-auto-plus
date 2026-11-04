package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.RaceGrade
import com.steve1316.uma_android_automation.types.TrackDistance
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/** A race list label that names another course the race can be drawn on files as that race with the course's distance type. */
@DisplayName("Race venue lookup")
class RaceVenueLookupTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun raceData(
        race: JSONObject,
        label: String,
        distanceType: String,
    ) = Racing.RaceData(race.getString("name"), race.getString("grade"), race.getInt("fans"), label, race.getString("terrain"), distanceType, race.getInt("turnNumber"))

    private val raceJson: List<JSONObject> by lazy {
        val all = JSONObject(repoFile("src/data/races.json").readText())
        all.keys().asSequence().map { all.getJSONObject(it) }.toList()
    }

    private val baseRaces: List<Racing.RaceData> by lazy { raceJson.map { raceData(it, it.getString("nameFormatted"), it.getString("distanceType")) } }

    /** What the database join returns: each base race refiled under its alternate course's label and distance type. */
    private val venueRaces: List<Racing.RaceData> by lazy {
        raceJson.flatMap { race ->
            val venues = race.optJSONArray("venues") ?: return@flatMap emptyList()
            (0 until venues.length()).map { raceData(race, venues.getJSONObject(it).getString("nameFormatted"), venues.getJSONObject(it).getString("distanceType")) }
        }
    }

    /** The lookup order: an exact label of the races table first, then the venues. */
    private fun resolve(
        turn: Int,
        label: String,
    ): List<Racing.RaceData> {
        val exact = baseRaces.filter { it.turnNumber == turn && it.nameFormatted == label }
        return exact.ifEmpty { Racing.venueMatches(label, venueRaces.filter { it.turnNumber == turn }) }
    }

    private data class Expected(val turn: Int, val label: String, val name: String, val distance: TrackDistance)

    private val jbcLabels =
        listOf(
            Triple("JBC Classic", "Kawasaki Dirt 2100m (Med) Left", TrackDistance.MEDIUM),
            Triple("JBC Classic", "Funabashi Dirt 1800m (Mile) Left", TrackDistance.MILE),
            Triple("JBC Classic", "Morioka Dirt 2000m (Med) Left", TrackDistance.MEDIUM),
            Triple("JBC Sprint", "Kawasaki Dirt 1400m (Sprint) Left", TrackDistance.SPRINT),
            Triple("JBC Sprint", "Funabashi Dirt 1000m (Sprint) Left", TrackDistance.SPRINT),
            Triple("JBC Sprint", "Morioka Dirt 1200m (Sprint) Left", TrackDistance.SPRINT),
            Triple("JBC Ladies", "Kawasaki Dirt 1600m (Mile) Left", TrackDistance.MILE),
            Triple("JBC Ladies", "Funabashi Dirt 1600m (Mile) Left", TrackDistance.MILE),
            Triple("JBC Ladies", "Morioka Dirt 1800m (Mile) Left", TrackDistance.MILE),
        )

    private val oiLabels =
        listOf(
            Triple("JBC Classic", "Oi Dirt 2000m (Med) Right", TrackDistance.MEDIUM),
            Triple("JBC Sprint", "Oi Dirt 1200m (Sprint) Right", TrackDistance.SPRINT),
            Triple("JBC Ladies", "Oi Dirt 1800m (Mile) Right", TrackDistance.MILE),
        )

    private val expectedVenues: List<Expected> by lazy {
        val jbc =
            listOf(45, 69).flatMap { turn ->
                (jbcLabels + oiLabels).map { (prefix, label, distance) ->
                    val name = baseRaces.single { it.turnNumber == turn && it.name.startsWith(prefix) }.name
                    Expected(turn, label, name, distance)
                }
            }
        jbc +
            listOf(
                Expected(31, "Tokyo Turf 2000m (Med) Left", "Satsuki Sho", TrackDistance.MEDIUM),
                Expected(56, "Hanshin Turf 3200m (Long) Right / Outer-Inner", "Tenno Sho (Spring)", TrackDistance.LONG),
                Expected(60, "Kyoto Turf 2200m (Med) Right / Outer", "Takarazuka Kinen", TrackDistance.MEDIUM),
            )
    }

    @Test
    @DisplayName("the data lists 21 alternate courses on 9 races")
    fun twentyOneVenues() {
        assertEquals(21, venueRaces.size)
        assertEquals(9, raceJson.count { it.has("venues") })
    }

    @Test
    @DisplayName("every venue label and every Oi label files as its race with the course's distance type")
    fun everyVenueResolves() {
        assertEquals(21 + 6, expectedVenues.size)
        expectedVenues.forEach { expected ->
            val hit = resolve(expected.turn, expected.label).singleOrNull()
            assertTrue(hit != null, "${expected.label} at turn ${expected.turn} should file as one race")
            assertEquals(expected.name, hit!!.name, expected.label)
            assertEquals(expected.distance, hit.trackDistance, expected.label)
        }
    }

    @Test
    @DisplayName("a Funabashi 1800 label is the JBC Classic at Mile with its own fans, not the Ladies' Classic")
    fun funabashiMile() {
        val hit = resolve(45, "Funabashi Dirt 1800m (Mile) Left").single()
        assertEquals("JBC Classic", hit.name)
        assertEquals(TrackDistance.MILE, hit.trackDistance)
        assertEquals(RaceGrade.G1, hit.grade)
        assertEquals(8000, hit.fans)
    }

    @Test
    @DisplayName("a base label still files as its base race")
    fun baseLabelUnchanged() {
        val satsuki = resolve(31, "Nakayama Turf 2000m (Med) Right / Inner").single()
        assertEquals("Satsuki Sho", satsuki.name)
        assertEquals(11000, satsuki.fans)
        assertEquals("JBC Classic", resolve(45, "Oi Dirt 2000m (Med) Right").single().name)
        assertEquals(TrackDistance.LONG, resolve(56, "Kyoto Turf 3200m (Long) Right / Outer").single().trackDistance)
    }

    @Test
    @DisplayName("no Oi race keeps the Ooi spelling the EN screen does not use")
    fun oiSpelling() {
        val oiRows = raceJson.filter { it.getString("raceTrack") == "Ooi" }
        assertEquals(16, oiRows.size)
        assertTrue(oiRows.all { it.getString("nameFormatted").startsWith("Oi ") })
        assertFalse(baseRaces.any { it.nameFormatted.startsWith("Ooi ") })
    }

    @Test
    @DisplayName("a venue label never equals another label on its turn")
    fun venueLabelsAreUnique() {
        val labelsByTurn = (baseRaces + venueRaces).groupBy { it.turnNumber to it.nameFormatted }
        venueRaces.forEach { venue ->
            val sameLabel = labelsByTurn.getValue(venue.turnNumber to venue.nameFormatted)
            assertEquals(1, sameLabel.size, "${venue.nameFormatted} at turn ${venue.turnNumber} is shared by ${sameLabel.map { it.name }}")
        }
    }
}
