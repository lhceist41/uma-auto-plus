package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.SkillData
import com.steve1316.uma_scoring.RankAptitudes
import com.steve1316.uma_scoring.SkillScoreInput
import com.steve1316.uma_scoring.estimateRank
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

/**
 * Rebuilds live Grand Concert careers from their true final inputs (stats, aptitudes, owned skills with their tiers, unique level) and asserts the game's own rating, to the point.
 * Skill sets are the bot's verified buys (gold upgrades replace their lower skill) plus skills only the Details screen showed.
 */
@DisplayName("Estimated rating rebuilds game-rated careers")
class CareerRatingGoldenTest {
    private data class Career(val name: String, val gameRating: Int, val stats: List<Int>, val aptitudes: RankAptitudes, val uniqueLevel: Int, val skills: List<String>)

    private fun apt(sprint: String, mile: String, medium: String, long: String, front: String, pace: String, late: String, end: String) =
        RankAptitudes(sprint, mile, medium, long, front, pace, late, end)

    private val skillData: Map<String, SkillData> by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var file: File? = null
        repeat(5) {
            if (file == null && File(dir, "src/data/skills.json").isFile) file = File(dir, "src/data/skills.json")
            dir = dir?.parentFile
        }
        val root = JSONObject(requireNotNull(file) { "src/data/skills.json not found" }.readText())
        root.keys().asSequence().associateWith { name ->
            val s = root.getJSONObject(name)
            SkillData(
                s.getInt("id"),
                name,
                "",
                s.getInt("icon_id"),
                s.getInt("cost"),
                s.getInt("eval_pt"),
                s.getString("condition"),
                s.getString("precondition"),
                s.getBoolean("inherited"),
                null,
                null,
                null,
            )
        }
    }

    @TestFactory
    fun `each career rebuilds to the game rating`(): List<DynamicTest> =
        careers.map { c ->
            DynamicTest.dynamicTest(c.name) {
                val inputs = c.skills.map { name -> requireNotNull(skillData[name]) { "unknown skill $name" }.let { SkillScoreInput(it.evalPt, SkillDatabase.rankCheckType(it)) } }
                val (spd, sta, pwr, gut, wit) = c.stats
                assertEquals(c.gameRating, estimateRank(spd, sta, pwr, gut, wit, inputs, c.aptitudes, c.uniqueLevel).totalScore)
            }
        }

    private val careers =
        listOf(
            Career(
                "Biwa Hayahide 13,676",
                13676,
                listOf(1310, 650, 912, 492, 391),
                apt("F", "A", "A", "A", "E", "S", "B", "E"),
                1,
                listOf(
                    "Dancing in the Leaves",
                    "The Duty of Dignity Calls",
                    "Concentration",
                    "VIP Pass",
                    "Homestretch Haste",
                    "Race Planner",
                    "Prepared to Pass",
                    "Pace Chaser Straightaways ○",
                    "Tail Held High",
                    "Head-On",
                    "Medium Corners ○",
                    "Pace Chaser Corners ◎",
                    "Pace Chaser Savvy ◎",
                ),
            ),
            Career(
                "Rice Shower 12,487",
                12487,
                listOf(1223, 617, 801, 586, 434),
                apt("E", "S", "A", "A", "B", "A", "C", "G"),
                1,
                listOf(
                    "The Duty of Dignity Calls",
                    "Dancing in the Leaves",
                    "Cooldown",
                    "Hydrate",
                    "Pace Chaser Savvy ◎",
                    "Summer Runner ◎",
                    "Corner Recovery ○",
                    "Race Planner",
                    "Up-Tempo",
                    "Pace Chaser Corners ◎",
                    "Ignited Spirit STA",
                    "Medium Straightaways ○",
                    "Hawkeye",
                ),
            ),
            Career(
                "Hishi Amazon 16,132",
                16132,
                listOf(1346, 563, 1178, 625, 445),
                apt("D", "A", "A", "B", "G", "A", "C", "A"),
                4,
                listOf(
                    "Dancing in the Leaves",
                    "The Duty of Dignity Calls",
                    "Corner Recovery ○",
                    "Homestretch Haste",
                    "End Closer Straightaways ◎",
                    "Masterful Gambit",
                    "Straightaway Spurt",
                    "Unyielding",
                    "Hawkeye",
                    "End Closer Corners ◎",
                    "See Ya Later!",
                    "Target in Sight ○",
                    "Sprint Straightaways ○",
                    "Opening Gambit",
                    "Strategist",
                ),
            ),
            Career(
                "Biwa Hayahide 14,740",
                14740,
                listOf(1305, 599, 1030, 675, 420),
                apt("F", "A", "A", "A", "E", "S", "B", "E"),
                2,
                listOf(
                    "Dancing in the Leaves",
                    "The Duty of Dignity Calls",
                    "Preferred Position",
                    "VIP Pass",
                    "Firm Conditions ○",
                    "Homestretch Haste",
                    "Prepared to Pass",
                    "Pace Chaser Straightaways ◎",
                    "Pace Chaser Corners ◎",
                    "Pace Chaser Savvy ◎",
                    "Unstoppable",
                    "Competitive Spirit ○",
                    "Playtime's Over!",
                    "Ignited Spirit SPD",
                ),
            ),
            Career(
                "Mihono Bourbon 13,587",
                13587,
                listOf(1246, 723, 934, 604, 383),
                apt("C", "A", "A", "B", "A", "C", "G", "G"),
                3,
                listOf(
                    "A Kiss for Courage",
                    "The Duty of Dignity Calls",
                    "Dancing in the Leaves",
                    "Wet Conditions ○",
                    "Extra Tank",
                    "Corner Recovery ○",
                    "Concentration",
                    "Early Lead",
                    "Escape Artist",
                    "Up-Tempo",
                    "Tether",
                    "Medium Straightaways ◎",
                    "Front Runner Savvy ◎",
                ),
            ),
            Career(
                "Taiki Shuttle 16,685",
                16685,
                listOf(1537, 495, 1031, 561, 463),
                apt("A", "A", "E", "G", "C", "A", "E", "G"),
                1,
                listOf(
                    "Dancing in the Leaves",
                    "The Duty of Dignity Calls",
                    "Mile Maven",
                    "Changing Gears",
                    "Corner Recovery ○",
                    "Prepared to Pass",
                    "Mile Straightaways ◎",
                    "Pace Chaser Straightaways ◎",
                    "Summer Runner ◎",
                    "Funabashi Racecourse ○",
                    "Lay Low",
                    "Medium Straightaways ○",
                    "Pace Chaser Savvy ◎",
                    "Playtime's Over!",
                    "On the Attack",
                ),
            ),
            Career(
                "Biwa Hayahide 15,684",
                15684,
                listOf(1320, 708, 1017, 527, 592),
                apt("F", "S", "A", "A", "E", "A", "B", "E"),
                3,
                listOf(
                    "Dancing in the Leaves",
                    "The Duty of Dignity Calls",
                    "Resplendent Red Ace",
                    "VIP Pass",
                    "Left-Handed ○",
                    "Winter Runner ○",
                    "Corner Recovery ○",
                    "Homestretch Haste",
                    "Race Planner",
                    "Prepared to Pass",
                    "Pace Chaser Straightaways ○",
                    "Pace Chaser Corners ◎",
                    "Pace Chaser Savvy ○",
                    "Nakayama Racecourse ○",
                    "Competitive Spirit ○",
                    "Medium Corners ○",
                    "Fighting Spirit",
                ),
            ),
            Career(
                "Rice Shower 13,969",
                13969,
                listOf(1238, 692, 1012, 543, 374),
                apt("E", "A", "A", "A", "B", "A", "C", "G"),
                2,
                listOf(
                    "Dancing in the Leaves",
                    "The Duty of Dignity Calls",
                    "Resplendent Red Ace",
                    "Cooldown",
                    "Hydrate",
                    "Tail Held High",
                    "Corner Adept ○",
                    "Corner Recovery ○",
                    "Concentration",
                    "Pace Chaser Straightaways ◎",
                    "Unstoppable",
                    "Kyoto Racecourse ○",
                    "Up-Tempo",
                    "Ignited Spirit SPD",
                ),
            ),
            Career(
                "Mihono Bourbon 15,196",
                15196,
                listOf(1276, 874, 932, 575, 410),
                apt("C", "A", "A", "B", "A", "C", "G", "G"),
                3,
                listOf(
                    "Dancing in the Leaves",
                    "A Kiss for Courage",
                    "The Duty of Dignity Calls",
                    "Resplendent Red Ace",
                    "Front Runner Savvy ○",
                    "Firm Conditions ○",
                    "Concentration",
                    "Early Lead",
                    "Escape Artist",
                    "Up-Tempo",
                    "Extra Tank",
                    "Medium Straightaways ○",
                    "Medium Corners ○",
                    "Groundwork",
                    "Take the Chance",
                    "Nakayama Racecourse ○",
                    "Standard Distance ○",
                    "Murmur",
                ),
            ),
            Career(
                "Hishi Amazon 16,699 (Power 1200 from the Details frame)",
                16699,
                listOf(1423, 487, 1200, 451, 514),
                apt("D", "S", "A", "B", "G", "A", "C", "A"),
                3,
                listOf(
                    "The Duty of Dignity Calls",
                    "Dancing in the Leaves",
                    "Maverick ○",
                    "Sturm und Drang",
                    "Medium Corners ◎",
                    "End Closer Straightaways ◎",
                    "Left-Handed ○",
                    "Firm Conditions ○",
                    "Winter Runner ○",
                    "Homestretch Haste",
                    "Standing By",
                    "Medium Straightaways ◎",
                    "Murmur",
                    "Levelheaded",
                    "Straightaway Spurt",
                    "Unyielding",
                ),
            ),
            Career(
                "Taiki Shuttle 16,005 on a phone (unique Lvl 1 as in the emulator Taiki careers)",
                16005,
                listOf(1413, 521, 1110, 521, 597),
                apt("A", "A", "E", "G", "C", "A", "E", "G"),
                1,
                listOf(
                    "Dancing in the Leaves",
                    "The Duty of Dignity Calls",
                    "A Kiss for Courage",
                    "Pace Chaser Savvy ○",
                    "Resplendent Red Ace",
                    "Mile Maven",
                    "Pace Chaser Straightaways ◎",
                    "See Ya Later!",
                    "Unstoppable",
                    "Left-Handed ◎",
                    "Race Planner",
                    "Pace Chaser Corners ◎",
                ),
            ),
            Career(
                "Copano Rickey 11,035 on a phone (unique Lvl 1 from the Details frame)",
                11035,
                listOf(1137, 513, 866, 468, 346),
                apt("C", "A", "A", "G", "A", "S", "C", "G"),
                1,
                listOf(
                    "The Duty of Dignity Calls",
                    "Dancing in the Leaves",
                    "Standard Distance ○",
                    "Focus",
                    "Updrafters",
                    "Chance of Victory",
                    "Unstoppable",
                    "Non-Standard Distance ○",
                    "Firm Conditions ◎",
                    "Flustered Pace Chasers",
                    "Pace Chaser Savvy ◎",
                    "Prudent Positioning",
                    "Prepared to Pass",
                    "Up-Tempo",
                ),
            ),
            Career(
                "Copano Rickey 9,362 (Sapporo Racecourse × from the Details frame)",
                9362,
                listOf(1006, 372, 780, 336, 370),
                apt("C", "A", "A", "G", "A", "A", "C", "G"),
                2,
                listOf(
                    "Dancing in the Leaves",
                    "A Kiss for Courage",
                    "The Duty of Dignity Calls",
                    "Pace Chaser Savvy ○",
                    "Head-On",
                    "Chance of Victory",
                    "Homestretch Haste",
                    "Medium Corners ○",
                    "Murmur",
                    "Pace Chaser Straightaways ○",
                    "Top Pick",
                    "Solid Steps",
                    "On the Attack",
                    "On the Way to Our Dream",
                    "Sapporo Racecourse ×",
                ),
            ),
            Career(
                "Taiki Shuttle 15,148",
                15148,
                listOf(1466, 571, 928, 384, 480),
                apt("A", "S", "E", "G", "C", "A", "E", "G"),
                1,
                listOf(
                    "Dancing in the Leaves",
                    "A Kiss for Courage",
                    "The Duty of Dignity Calls",
                    "Prepared to Pass",
                    "Mile Maven",
                    "Tail Held High",
                    "Concentration",
                    "Mile Straightaways ◎",
                    "Changing Gears",
                    "Pace Chaser Savvy ○",
                    "Firm Conditions ○",
                    "Flustered Pace Chasers",
                    "Pace Chaser Straightaways ◎",
                    "Ignited Spirit SPD",
                ),
            ),
            Career(
                "Taiki Shuttle 15,556",
                15556,
                listOf(1427, 540, 1071, 458, 548),
                apt("A", "A", "E", "G", "C", "S", "E", "G"),
                1,
                listOf(
                    "Dancing in the Leaves",
                    "The Duty of Dignity Calls",
                    "Mile Maven",
                    "Changing Gears",
                    "Homestretch Haste",
                    "Up-Tempo",
                    "Mile Straightaways ◎",
                    "Pace Chaser Straightaways ◎",
                    "Pace Chaser Savvy ○",
                    "Head-On",
                    "Rainy Days ○",
                    "Prudent Positioning",
                    "Medium Corners ○",
                    "Take the Chance",
                    "Ignited Spirit SPD",
                ),
            ),
        )
}
