package com.steve1316.uma_android_automation.bot

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The `gc_skill_econ` record is observation only. What matters: a fact the bot could not read is null and listed under
 * `unknown` (never estimated), the record carries a `type` so every existing careers.jsonl reader skips it, and the
 * per-source Skill Point figures claim only what a clean interval or a fixed reward line proves.
 */
@DisplayName("GrandConcertSkillEconomy Tests")
class GrandConcertSkillEconomyTest {
    private val golds = setOf("Changing Gears", "See Ya Later!")
    private var chains: (String) -> Collection<String> = { emptyList() }

    private fun build(economy: GrandConcertSkillEconomy, finalSkills: List<String>? = null, unique: Int? = 3, spEnd: Int? = 244): JSONObject =
        economy.buildRecord(
            timestamp = 1L,
            app = "1.7.4",
            fp = "abc123",
            trainee = "Taiki_Shuttle",
            scenario = "Grand_Concert",
            outcome = "COMPLETED",
            turn = 75,
            launchTransactionId = "launch-1",
            finalSkills = finalSkills,
            uniqueLevel = unique,
            skillPtsEnd = spEnd,
            isGold = { it in golds },
            chainOf = chains,
        )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun fixture(): GrandConcertSkillEconomy =
        GrandConcertSkillEconomy().apply {
            noteBuy("Changing Gears", 200, 40, gold = true)
            noteBuy("Mile Corners ◎", 120, 60, gold = false)
            noteBuy("Right-Handed ○", 90, 61, gold = false)
            noteSkillOffered("See Ya Later!", 304, 10, 60)
            noteSkillOffered("Not A Gold", 100, 0, 60)
            noteLesson(20, "Make Debut!", "SONG")
            noteLesson(30, "GIRLS' LEGEND U", "SONG")
            noteLesson(31, "Run for Our Dream!", "TECHNIQUE")
            noteEvent(21, "Embrace Those Emotions! Dating starts", "Light Hello", 1, listOf("Skill Pts +10", "Speed +5"))
            noteEvent(47, "At Rainbow Cove", "Light Hello", 2, listOf("Skill Pts +20/30", "Obtain hint for See Ya Later!"))
            noteEvent(55, "Lyric Night", "Grand Live", 1, listOf("Skill Pts +15", "Learn Come What May"))
            closeConcert { 500 }
            noteConcertStart(24, 3, 11, 500)
            closeConcert { 560 }
            noteConcertStart(36, 2, 14, 400)
            noteBuy("Pace Chaser Savvy ○", 70, 37, gold = false)
            closeConcert { 380 }
            noteSongTotal(68, 15)
            noteSongTotal(69, 16)
            noteSongTotal(70, 16)
            noteSongTotal(72, 17)
        }

    @Test
    @DisplayName("learned skills carry tier, source and SP paid")
    fun skillsCarryTierSourceAndPrice() {
        val record =
            build(
                fixture(),
                finalSkills = listOf("Changing Gears", "Mile Corners ◎", "Come What May", "Corner Recovery ○"),
            )
        val skills = record.getJSONArray("skills").objects().associateBy { it.getString("name") }
        assertEquals("gold", skills.getValue("Changing Gears").getString("tier"))
        assertEquals("bought", skills.getValue("Changing Gears").getString("source"))
        assertEquals(200, skills.getValue("Changing Gears").getInt("sp"))
        assertEquals("◎", skills.getValue("Mile Corners ◎").getString("tier"))
        assertEquals("event", skills.getValue("Come What May").getString("source"))
        assertTrue(skills.getValue("Come What May").isNull("sp"))
        assertTrue(skills.getValue("Corner Recovery ○").isNull("source"))
        assertEquals("○", skills.getValue("Corner Recovery ○").getString("tier"))
        // Two buys the final read does not show were cancelled: not learned, not counted as spent.
        val unconfirmed = record.getJSONArray("unconfirmedBuys")
        assertEquals(setOf("Right-Handed ○", "Pace Chaser Savvy ○"), (0 until unconfirmed.length()).map { unconfirmed.getString(it) }.toSet())
        assertEquals(320, record.getJSONObject("sp").getInt("spentOnBuys"))
    }

    @Test
    @DisplayName("a hint line or a random branch never makes an event the source of a skill")
    fun hintOrRandomLinesProveNothing() {
        val economy =
            GrandConcertSkillEconomy().apply {
                noteEvent(10, "Hint Day", "Light Hello", 1, listOf("Obtain hint for Corner Recovery"))
                noteEvent(11, "Coin Flip", "Light Hello", 1, listOf("Randomly either", "Learn Straightaway Adept", "---", "Speed +5"))
            }
        val skills = build(economy, finalSkills = listOf("Corner Recovery", "Straightaway Adept")).getJSONArray("skills").objects()
        assertTrue(skills.all { it.isNull("source") })
    }

    @Test
    @DisplayName("alternative event outcomes are not summed as certain Skill Points")
    fun randomBranchSkillPointsAreNotCounted() {
        val economy = GrandConcertSkillEconomy().apply { noteEvent(10, "A Simp's Lifestyle", "Agnes Digital", 1, listOf("Randomly either", "Skill Pts +20", "---", "Skill Pts +15")) }
        val earned = build(economy).getJSONObject("sp").getJSONObject("earned")
        assertTrue(earned.isNull("event"))
        assertFalse(earned.getBoolean("eventComplete"))
    }

    @Test
    @DisplayName("Skill Points: events count fixed lines only, a clean concert interval gives a delta, a dirty one does not")
    fun skillPointSources() {
        val sp = build(fixture()).getJSONObject("sp")
        val earned = sp.getJSONObject("earned")
        // +10 and +15 are fixed lines; "+20/30" is a random outcome, so the total is a floor and not complete.
        assertEquals(25, earned.getInt("event"))
        assertFalse(earned.getBoolean("eventComplete"))
        // Income shares the interval between two reads, so no payout is claimed; the raw reads and a clean-interval delta are.
        assertTrue(earned.isNull("concertPayout"))
        val payouts = sp.getJSONArray("concertIntervals").objects()
        assertEquals(60, payouts[0].getInt("delta"))
        assertEquals(380, payouts[1].getInt("spAfter"))
        assertTrue(payouts[1].isNull("delta"))
        assertTrue(earned.isNull("training"))
        assertTrue(earned.isNull("race"))
        assertTrue(earned.isNull("lessonTechnique"))
        assertEquals(480, sp.getInt("spentOnBuys"))
    }

    @Test
    @DisplayName("song totals use the first observed turn at or past 69 and 71; concerts never claim a Great Success result")
    fun songsAndConcerts() {
        val songs = build(fixture()).getJSONObject("songs")
        assertEquals(16, songs.getJSONObject("boughtAtTurn69").getInt("bought"))
        assertEquals(69, songs.getJSONObject("boughtAtTurn69").getInt("turn"))
        assertEquals(17, songs.getJSONObject("boughtAtTurn71").getInt("bought"))
        assertEquals(72, songs.getJSONObject("boughtAtTurn71").getInt("turn"))
        val concerts = songs.getJSONArray("concerts").objects()
        assertTrue(concerts[0].getBoolean("greatSuccessSongFloorMet"))
        assertFalse(concerts[1].getBoolean("greatSuccessSongFloorMet"))
        assertTrue(concerts.all { it.isNull("greatSuccess") })
    }

    @Test
    @DisplayName("event golds: offered with discount and hint level, bought, granted, or never seen")
    fun eventGolds() {
        val record = build(fixture().apply { noteBuy("See Ya Later!", 274, 70, gold = true) }, finalSkills = listOf("See Ya Later!", "Come What May"))
        val golds = record.getJSONArray("golds").objects().associateBy { it.getString("name") }
        val seeYa = golds.getValue("See Ya Later!")
        assertTrue(seeYa.getBoolean("bought"))
        assertEquals(1, seeYa.getInt("hintLevel"))
        assertEquals(10, seeYa.getInt("discountPct"))
        val comeWhat = golds.getValue("Come What May")
        assertFalse(comeWhat.getBoolean("bought"))
        assertEquals(true, comeWhat.get("ownedWithoutBotBuy"))
        assertEquals(true, comeWhat.get("grantedByEvent"))
        assertEquals(true, comeWhat.get("hintLevelDerived"))
        val iWanna = golds.getValue("I Wanna Win with You")
        assertEquals(false, iWanna.get("offered"))
        assertEquals(false, iWanna.get("ownedWithoutBotBuy"))
        assertEquals(false, iWanna.get("grantedByEvent"))
    }

    @Test
    @DisplayName("lyric event, GIRLS' LEGEND U and the Light Hello chain come from resolved events and learned lessons")
    fun lyricLegendAndLightHello() {
        val record = build(fixture())
        assertTrue(record.getJSONObject("lyricEvent").getBoolean("fired"))
        assertEquals(1, record.getJSONObject("lyricEvent").getInt("choice"))
        assertEquals("GIRLS' LEGEND U", record.getString("girlsLegendU"))
        val lightHello = record.getJSONObject("lightHello")
        assertEquals(2, lightHello.getJSONArray("events").length())
        assertEquals(2, lightHello.getJSONObject("rainbowCove").getInt("option"))
        assertTrue(lightHello.getJSONObject("rainbowCove").isNull("outcome"))
        assertEquals(3, record.getInt("uniqueLevel"))
    }

    @Test
    @DisplayName("a career with no readable facts records nulls and names them under unknown")
    fun unreadableFactsAreNullAndListed() {
        val record = build(GrandConcertSkillEconomy(), finalSkills = null, unique = 0, spEnd = null)
        assertTrue(record.getJSONObject("lyricEvent").isNull("fired"))
        assertTrue(record.isNull("girlsLegendU"))
        assertTrue(record.isNull("uniqueLevel"))
        assertTrue(record.getJSONObject("sp").isNull("end"))
        assertTrue(record.getJSONObject("songs").isNull("boughtAtTurn69"))
        assertTrue(record.getJSONObject("lightHello").isNull("rainbowCove"))
        val unknown = record.getJSONArray("unknown").let { arr -> (0 until arr.length()).map { arr.getString(it) } }
        assertTrue(
            unknown.containsAll(
                listOf(
                    "skills.final",
                    "lyricEvent.fired",
                    "girlsLegendU",
                    "uniqueLevel",
                    "songs.boughtAtTurn69",
                    "songs.boughtAtTurn71",
                    "sp.end",
                    "sp.earned.event",
                    "sp.earned.training",
                    "sp.earned.race",
                    "golds[].price",
                ),
            ),
        )
        assertEquals(0, record.getJSONArray("skills").length())
    }

    @Test
    @DisplayName("every null in a populated record is named under unknown")
    fun everyNullLeafIsListed() {
        val record = build(fixture(), finalSkills = listOf("Changing Gears"), unique = null, spEnd = null)
        val unknown = record.getJSONArray("unknown").let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() }

        fun walk(path: String, node: Any?) {
            when (node) {
                is JSONObject ->
                    node.keys().forEach { key ->
                        val child = if (path.isEmpty()) key else "$path.$key"
                        if (node.isNull(key)) assertTrue(child in unknown, "$child is null but not listed") else walk(child, node.get(key))
                    }
                is JSONArray -> for (i in 0 until node.length()) walk("$path[]", node.get(i))
            }
        }
        walk("", record)
        assertTrue("songs.concerts[].greatSuccess" in unknown)
    }

    @Test
    @DisplayName("the record is typed so every existing careers.jsonl reader skips it, and the log line carries the headline facts")
    fun typedRecordAndLogLine() {
        val record = build(fixture(), finalSkills = listOf("Changing Gears"))
        assertEquals("gc_skill_econ", record.getString("type"))
        assertFalse(record.has("result"))
        val line = GrandConcertSkillEconomy.logLine(record)
        assertTrue(line.startsWith("[GC_SKILL_ECON] outcome=COMPLETED "))
        assertTrue(line.contains("songs69=16"))
        assertTrue(line.contains("golds=See_Ya_Later!:offered"))
        assertTrue(line.contains("unique=3"))
        assertFalse(line.contains(0x2014.toChar()) || line.contains(0x2013.toChar()))
    }

    @Test
    @DisplayName("an owned gold with no fixed grant line is unknown, and a gold never seen without any parsed list is not claimed unoffered")
    fun unexplainedGoldAndUnparsedListAreUnknown() {
        val record = build(GrandConcertSkillEconomy(), finalSkills = listOf("See Ya Later!"))
        val golds = record.getJSONArray("golds").objects().associateBy { it.getString("name") }
        assertEquals(true, golds.getValue("See Ya Later!").get("ownedWithoutBotBuy"))
        assertTrue(golds.getValue("See Ya Later!").isNull("grantedByEvent"))
        assertTrue(golds.getValue("Come What May").isNull("offered"))
        val parsed = GrandConcertSkillEconomy().apply { noteSkillOffered("Mile Corners", 100, 0, 20) }
        val seen = build(parsed, finalSkills = emptyList()).getJSONArray("golds").objects()
        assertTrue(seen.all { it.get("offered") == false })
    }

    @Test
    @DisplayName("(random) reward lines are never counted as certain Skill Points or as a grant")
    fun randomPrefixedLinesAreUncertain() {
        val economy =
            GrandConcertSkillEconomy().apply {
                noteEvent(10, "Rest Up", "Support", 1, listOf("(random) Skill points +4", "Energy +10"))
                noteEvent(11, "Gatekept", "Character", 1, listOf("(random) Obtain Gatekept skill"))
            }
        val record = build(economy, finalSkills = listOf("Gatekept"))
        val earned = record.getJSONObject("sp").getJSONObject("earned")
        assertTrue(earned.isNull("event"))
        assertFalse(earned.getBoolean("eventComplete"))
        assertTrue(record.getJSONArray("skills").getJSONObject(0).isNull("source"))
    }

    @Test
    @DisplayName("a white base bought before its gold still counts when only the gold shows in the final read")
    fun upgradeChainConfirmsTheWhiteBase() {
        chains = { if (it == "Pursuit Basics") listOf("Pursuit Basics", "See Ya Later!") else emptyList() }
        val economy =
            GrandConcertSkillEconomy().apply {
                noteBuy("Pursuit Basics", 80, 30, gold = false)
                noteBuy("Right-Handed ○", 90, 31, gold = false)
            }
        val record = build(economy, finalSkills = listOf("See Ya Later!"))
        assertEquals(80, record.getJSONObject("sp").getInt("spentOnBuys"))
        val unconfirmed = record.getJSONArray("unconfirmedBuys")
        assertEquals(listOf("Right-Handed ○"), (0 until unconfirmed.length()).map { unconfirmed.getString(it) })
    }

    @Test
    @DisplayName("a cancelled gold is not confirmed by its white predecessor, and a white plus its upgrade report every payment")
    fun chainDirectionAndAggregatedPayments() {
        chains = { listOf("Pursuit Basics", "See Ya Later!") }
        val cancelledGold = GrandConcertSkillEconomy().apply { noteBuy("See Ya Later!", 200, 50, gold = true) }
        val rejected = build(cancelledGold, finalSkills = listOf("Pursuit Basics"))
        assertEquals(0, rejected.getJSONObject("sp").getInt("spentOnBuys"))
        assertEquals(1, rejected.getJSONArray("unconfirmedBuys").length())

        val upgraded =
            GrandConcertSkillEconomy().apply {
                noteBuy("Pursuit Basics", 80, 30, gold = false)
                noteBuy("See Ya Later!", 120, 50, gold = true)
            }
        val record = build(upgraded, finalSkills = listOf("See Ya Later!"))
        assertEquals(200, record.getJSONObject("sp").getInt("spentOnBuys"))
        assertEquals(200, record.getJSONArray("skills").getJSONObject(0).getInt("sp"))
    }

    @Test
    @DisplayName("buying only a gold's white base never marks the gold bought (Playtime's Over! -> See Ya Later!)")
    fun whiteBaseDoesNotMakeTheGoldBought() {
        chains = { listOf("Playtime's Over!", "See Ya Later!") }
        val whiteOnly = GrandConcertSkillEconomy().apply { noteBuy("Playtime's Over!", 160, 30, gold = false) }
        val seeYa = build(whiteOnly, finalSkills = listOf("Playtime's Over!")).getJSONArray("golds").objects().first { it.getString("name") == "See Ya Later!" }
        assertEquals(false, seeYa.get("bought"))
        assertTrue(seeYa.isNull("price"))
        assertTrue(seeYa.isNull("offered"))

        // A final gold nobody bought, with only its white base paid: the skill row claims no source, tier or payment from the white.
        val goldRow = build(whiteOnly, finalSkills = listOf("See Ya Later!")).getJSONArray("skills").getJSONObject(0)
        assertTrue(goldRow.isNull("source"))
        assertTrue(goldRow.isNull("sp"))
        assertEquals("gold", goldRow.getString("tier"))

        val both =
            GrandConcertSkillEconomy().apply {
                noteBuy("Playtime's Over!", 160, 30, gold = false)
                noteBuy("See Ya Later!", 120, 50, gold = true)
            }
        val bought = build(both, finalSkills = listOf("See Ya Later!")).getJSONArray("golds").objects().first { it.getString("name") == "See Ya Later!" }
        assertEquals(true, bought.get("bought"))
        assertEquals(120, bought.getInt("price"))
    }

    @Test
    @DisplayName("with no final read, a white base and its gold are one row carrying both payments")
    fun noFinalReadListsOneRowPerSkillLine() {
        chains = { listOf("Playtime's Over!", "See Ya Later!") }
        val economy =
            GrandConcertSkillEconomy().apply {
                noteBuy("Playtime's Over!", 160, 30, gold = false)
                noteBuy("See Ya Later!", 160, 50, gold = true)
                noteBuy("Right-Handed ○", 90, 31, gold = false)
                noteBuy("Right-Handed ◎", 20, 52, gold = false)
            }
        val record = build(economy, finalSkills = null)
        val rows = record.getJSONArray("skills").objects()
        assertEquals(setOf("See Ya Later!", "Right-Handed ◎"), rows.map { it.getString("name") }.toSet())
        assertEquals(320, rows.first { it.getString("name") == "See Ya Later!" }.getInt("sp"))
        assertEquals(110, rows.first { it.getString("name") == "Right-Handed ◎" }.getInt("sp"))
        assertEquals(rows.sumOf { it.getInt("sp") }, record.getJSONObject("sp").getInt("spentOnBuys"))
    }

    @Test
    @DisplayName("an incompletely scanned Learn list never proves a gold was not offered")
    fun incompleteScanProvesNothing() {
        val partial = GrandConcertSkillEconomy().apply { noteSkillOffered("Mile Corners", 100, 0, 20, scanComplete = false) }
        val golds = build(partial, finalSkills = emptyList()).getJSONArray("golds").objects()
        assertTrue(golds.all { it.isNull("offered") })
    }

    @Test
    @DisplayName("without a final read the buys are marked unverified, and the log line says unknown for an unseen gold")
    fun unverifiedBuysAndUnknownLogToken() {
        val record = build(GrandConcertSkillEconomy().apply { noteBuy("Right-Handed ○", 90, 31, gold = false) })
        assertEquals(false, record.get("buysVerifiedByFinalRead"))
        assertTrue(GrandConcertSkillEconomy.logLine(record).contains("See_Ya_Later!:unknown"))
    }

    @Test
    @DisplayName("a tap that did not advance counts its event once")
    fun repeatedEventCountsOnce() {
        val economy =
            GrandConcertSkillEconomy().apply {
                noteEvent(21, "Embrace Those Emotions!", "Light Hello", 1, listOf("Skill Pts +10"))
                noteEvent(21, "Embrace Those Emotions!", "Light Hello", 1, listOf("Skill Pts +10"))
            }
        val record = build(economy)
        assertEquals(10, record.getJSONObject("sp").getJSONObject("earned").getInt("event"))
        assertEquals(1, record.getJSONObject("lightHello").getJSONArray("events").length())
    }

    @Test
    @DisplayName("an interrupt during the concert read propagates; any other failure leaves the after-read null")
    fun interruptDuringConcertReadPropagates() {
        val interrupted = GrandConcertSkillEconomy().apply { noteConcertStart(24, 3, 11, 500) }
        assertThrows(InterruptedException::class.java) { interrupted.closeConcert { throw InterruptedException("Screen capture stopped") } }
        val failed = GrandConcertSkillEconomy().apply { noteConcertStart(24, 3, 11, 500) }
        failed.closeConcert { throw IllegalStateException("ocr") }
        assertFalse(failed.concertPending)
        val interval = build(failed).getJSONObject("sp").getJSONArray("concertIntervals").getJSONObject(0)
        assertTrue(interval.isNull("spAfter"))
        assertTrue(interval.isNull("delta"))
    }

    @Test
    @DisplayName("a resumed career is marked by firstTurn above 1 and joins by launchTransactionId")
    fun resumedCareerIsMarkedAndJoinable() {
        val economy =
            GrandConcertSkillEconomy().apply {
                noteTurn(41)
                noteTurn(42)
            }
        val record = build(economy)
        assertEquals(41, record.getInt("firstTurn"))
        assertEquals("launch-1", record.getString("launchTransactionId"))
        assertTrue(build(GrandConcertSkillEconomy()).isNull("firstTurn"))
    }

    @Test
    @DisplayName("lesson turns are named for what they are, the last turn read")
    fun lessonTurnIsTheLastReadTurn() {
        val lesson = build(fixture()).getJSONArray("lessons").getJSONObject(0)
        assertTrue(lesson.has("lastReadTurn"))
        assertFalse(lesson.has("turn"))
    }

    @Test
    @DisplayName("hint levels follow the discount ladder and an off-ladder discount gives none")
    fun hintLadder() {
        assertEquals(0, GrandConcertSkillEconomy.hintLevelOf(0))
        assertEquals(3, GrandConcertSkillEconomy.hintLevelOf(30))
        assertEquals(5, GrandConcertSkillEconomy.hintLevelOf(40))
        assertNull(GrandConcertSkillEconomy.hintLevelOf(25))
    }
}
