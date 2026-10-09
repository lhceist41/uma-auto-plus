package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.RaceGrade
import com.steve1316.uma_android_automation.types.TrackDistance
import com.steve1316.uma_android_automation.types.TrackSurface
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/** Same-turn races on one course read as the trainee's goal, and never take a grade or fan count from row order. */
@DisplayName("Shared-course race lookup")
class SharedCourseRaceLookupTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val goalAsset: String by lazy { repoFile("android/app/src/main/assets/gc_fan_runtime.json").readText() }
    private val facts: GrandConcertFanFacts by lazy { GrandConcertFanFacts.parse(goalAsset) ?: error("goal asset should parse") }
    private val trainees: List<String> by lazy { JSONObject(goalAsset).getJSONObject("characters").keys().asSequence().toList() }

    /** Every race grouped by turn and course label, in database (alphabetical) order. */
    private val racesByTurnAndCourse: Map<Pair<Int, String>, List<Racing.RaceData>> by lazy {
        val races = JSONObject(repoFile("src/data/compiled/races.json").readText()).getJSONArray("races")
        (0 until races.length())
            .map { races.getJSONObject(it) }
            .map {
                Racing.RaceData(
                    it.getString("name"),
                    it.getString("grade"),
                    it.getInt("fans"),
                    it.getString("nameFormatted"),
                    it.getString("terrain"),
                    it.getString("distanceType"),
                    it.getInt("turnNumber"),
                )
            }.sortedBy { it.name }
            .groupBy { it.turnNumber to it.nameFormatted }
    }

    private val sharedCourses get() = racesByTurnAndCourse.filterValues { it.size > 1 }

    private fun goalNames(
        trainee: String,
        turn: Int,
    ): Set<String> {
        val match = facts.match(trainee) as? GrandConcertFanFacts.Match.Matched ?: error("$trainee has no goal facts")
        return Racing.goalRaceNames(match.facts.mandatoryGates, turn)
    }

    private fun resolve(
        trainee: String,
        turn: Int,
        course: String,
    ): List<Racing.RaceData> = Racing.narrowToGoalRace(racesByTurnAndCourse.getValue(turn to course), goalNames(trainee, turn))

    @Test
    @DisplayName("the race data has 13 same-turn, same-course pairs")
    fun thirteenPairs() {
        assertEquals(13, sharedCourses.size)
        assertTrue(sharedCourses.values.all { it.size == 2 })
    }

    @Test
    @DisplayName("Oka Sho no longer reads as Arlington Cup G3, nor the Derby as the Oaks")
    fun namedGoals() {
        val okaCourse = "Hanshin Turf 1600m (Mile) Right / Outer"
        val oka = resolve("Hishi Amazon", 31, okaCourse).single()
        assertEquals("Oka Sho", oka.name)
        assertEquals(RaceGrade.G1 to 10500, Racing.sharedGradeAndFans(listOf(oka)))

        val derbyCourse = "Tokyo Turf 2400m (Med) Left"
        val derby = resolve("Zenno Rob Roy", 34, derbyCourse).single()
        assertEquals("Tokyo Yushun (Japanese Derby)", derby.name)
        assertEquals(RaceGrade.G1 to 20000, Racing.sharedGradeAndFans(listOf(derby)))
        assertEquals("Japanese Oaks", resolve("Hishi Amazon", 34, derbyCourse).single().name)

        assertEquals("Spring Stakes", resolve("Rice Shower", 30, "Nakayama Turf 1800m (Mile) Right / Inner").single().name)
    }

    @Test
    @DisplayName("every goal trainee on a shared course resolves to its own goal race")
    fun everyGoalTraineeResolves() {
        var resolved = 0
        for ((key, pair) in sharedCourses) {
            val (turn, course) = key
            for (trainee in trainees) {
                val goal = goalNames(trainee, turn).intersect(pair.map { it.name }.toSet())
                if (goal.size != 1) continue
                val result = resolve(trainee, turn, course)
                assertEquals(listOf(goal.single()), result.map { it.name }, "$trainee t$turn")
                assertEquals(result.single().grade, Racing.sharedGradeAndFans(result).first)
                resolved++
            }
        }
        assertEquals(85, resolved, "recount after a game-data refresh")
    }

    @Test
    @DisplayName("a goal turn offering both races keeps both and their shared G1 grade")
    fun choiceGoalKeepsBoth() {
        val result = resolve("Daiwa Scarlet", 34, "Tokyo Turf 2400m (Med) Left")
        assertEquals(2, result.size)
        assertEquals(RaceGrade.G1 to null, Racing.sharedGradeAndFans(result))
    }

    @Test
    @DisplayName("a trainee without a goal on the turn keeps both races and only a grade they share")
    fun nonGoalTraineeStaysAmbiguous() {
        val noGoal = emptySet<String>()
        for ((key, pair) in sharedCourses) {
            val result = Racing.narrowToGoalRace(pair, noGoal)
            assertEquals(pair, result, "t${key.first} ${key.second}")
            val (grade, fans) = Racing.sharedGradeAndFans(result)
            val grades = pair.map { it.grade }.toSet()
            if (grades.size == 1) assertEquals(grades.single(), grade)
            val fanCounts = pair.map { it.fans }.toSet()
            if (fanCounts.size == 1) assertEquals(fanCounts.single(), fans) else assertNull(fans, "t${key.first}: fans differ")
        }
        // A trainee whose t31 goal is elsewhere: Oka Sho vs Arlington Cup stays ungraded.
        val zennoT31 = resolve("Zenno Rob Roy", 31, "Hanshin Turf 1600m (Mile) Right / Outer")
        assertEquals(2, zennoT31.size)
        assertNull(Racing.sharedGradeAndFans(zennoT31).first)
    }

    @Test
    @DisplayName("differing grades that act alike keep the lower grade; the others stay unknown")
    fun gradeBands() {
        fun gradeOn(
            turn: Int,
            course: String,
        ) = Racing.sharedGradeAndFans(racesByTurnAndCourse.getValue(turn to course)).first
        // Momiji Stakes OP / Rindo Sho Pre-OP and Flower Cup G3 / Spring Stakes G2.
        assertEquals(RaceGrade.PRE_OP, gradeOn(19, "Kyoto Turf 1400m (Sprint) Right / Outer"))
        assertEquals(RaceGrade.G3, gradeOn(30, "Nakayama Turf 1800m (Mile) Right / Inner"))
        // Fairy Stakes G3 / Junior Cup OP, Oka Sho G1 / Arlington Cup G3, Unicorn Stakes G3 / Akhalteke Stakes OP.
        assertNull(gradeOn(25, "Nakayama Turf 1600m (Mile) Right / Outer"))
        assertNull(gradeOn(31, "Hanshin Turf 1600m (Mile) Right / Outer"))
        assertNull(gradeOn(36, "Tokyo Dirt 1600m (Mile) Left"))
        val differing = sharedCourses.filterValues { pair -> pair.map { it.grade }.toSet().size > 1 }
        assertEquals(setOf(19, 25, 30, 31, 36), differing.keys.map { it.first }.toSet())

        fun band(vararg grades: RaceGrade) = Racing.sharedGradeAndFans(grades.map { race(it) }).first
        assertEquals(RaceGrade.PRE_OP, band(RaceGrade.OP, RaceGrade.PRE_OP))
        assertEquals(RaceGrade.G3, band(RaceGrade.G2, RaceGrade.G3))
        assertNull(band(RaceGrade.G1, RaceGrade.G2))
        assertNull(band(RaceGrade.OP, RaceGrade.G3))
        assertNull(band(RaceGrade.DEBUT, RaceGrade.MAIDEN))
        assertNull(band(RaceGrade.MAIDEN, RaceGrade.PRE_OP))
        assertNull(band(RaceGrade.G1, RaceGrade.FINALE))
    }

    private fun race(grade: RaceGrade) =
        Racing.RaceData("Race ${grade.name}", grade, 1000, "Course", TrackSurface.TURF, TrackDistance.MILE, 19)

    @Test
    @DisplayName("an unambiguous lookup is unchanged")
    fun unambiguousUnchanged() {
        val anyGoals = setOf("Oka Sho", "Tokyo Yushun (Japanese Derby)", "Arima Kinen")
        for ((_, races) in racesByTurnAndCourse.filterValues { it.size == 1 }) {
            assertEquals(races, Racing.narrowToGoalRace(races, anyGoals))
            assertEquals(races.single().grade to races.single().fans, Racing.sharedGradeAndFans(races))
        }
        assertEquals(null to null, Racing.sharedGradeAndFans(emptyList()))
    }

    @Test
    @DisplayName("the ambiguity log says when the grade stays unknown")
    fun ambiguityLog() {
        val oka = racesByTurnAndCourse.getValue(31 to "Hanshin Turf 1600m (Mile) Right / Outer")
        val okaNote = Racing.sharedCourseNote(oka, Racing.sharedGradeAndFans(oka).first)
        assertTrue(okaNote.contains("\"Arlington Cup\" / \"Oka Sho\""), okaNote)
        assertTrue(okaNote.contains("grades differ"), okaNote)

        val spring = racesByTurnAndCourse.getValue(30 to "Nakayama Turf 1800m (Mile) Right / Inner")
        val springNote = Racing.sharedCourseNote(spring, Racing.sharedGradeAndFans(spring).first)
        assertTrue(springNote.endsWith("act alike, so grade G3 is used."), springNote)

        val derby = racesByTurnAndCourse.getValue(34 to "Tokyo Turf 2400m (Med) Left")
        val derbyNote = Racing.sharedCourseNote(derby, Racing.sharedGradeAndFans(derby).first)
        assertTrue(derbyNote.endsWith("Grade: G1."), derbyNote)
        assertFalse(derbyNote.contains("grades differ"), derbyNote)
    }

    @Test
    @DisplayName("no race label takes its grade or fans from the first lookup row")
    fun noFirstRowGrade() {
        val racing =
            repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Racing.kt").readText().replace("\r\n", "\n")
        assertFalse(racing.contains("raceDataList[0].grade"))
        assertFalse(racing.contains("raceDataList.firstOrNull()?.grade"))
        assertTrue(racing.contains("narrowToGoalRace(lookupRaceInDatabase(campaign.date.day, raceName), goalRaceNamesForTurn(campaign.date.day))"))
        assertEquals(3, Regex("sharedGradeAndFans\\(raceDataList\\)").findAll(racing).count())
    }
}
