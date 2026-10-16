package com.steve1316.uma_android_automation.bot

import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-task Grand Concert skill-economy facts, filled by observation hooks and written once at task end as a
 * `type:"gc_skill_econ"` record plus one `[GC_SKILL_ECON]` log line. Observation only: nothing here feeds a decision.
 * A fact the bot could not read is null and listed under `unknown`, never estimated. A resumed career starts a new task,
 * so each record covers one task (`outcome` says whether it reached the end screen).
 */
class GrandConcertSkillEconomy {
    /** [sp] is the committed Skill Points drop around the buy, not the listed price. */
    data class SkillBuy(val name: String, val sp: Int, val turn: Int?, val gold: Boolean)

    /** Latest sighting of a still-unlearned event gold in a parsed Learn list. */
    data class GoldOffer(val price: Int, val discountPct: Int, val turn: Int?)

    data class Lesson(val turn: Int?, val title: String, val kind: String)

    /** [spBefore]/[spAfter] are Skill Points reads either side of the concert; [clean] is false when a purchase or event fell between them. */
    data class Concert(val turn: Int?, val newSongs: Int, val careerSongs: Int, val spBefore: Int?, var spAfter: Int? = null, var clean: Boolean = true)

    data class EventNote(val turn: Int?, val title: String, val owner: String, val option: Int, val rewardLines: List<String>)

    private val buys = mutableListOf<SkillBuy>()
    private val goldOffers = linkedMapOf<String, GoldOffer>()
    private val lessons = mutableListOf<Lesson>()
    private val concerts = mutableListOf<Concert>()
    private val events = mutableListOf<EventNote>()
    private var pendingConcert: Concert? = null
    private var songsAtTurn69: Pair<Int, Int>? = null
    private var songsAtTurn71: Pair<Int, Int>? = null
    private var firstTurn: Int? = null
    private var listParsed = false

    val concertPending: Boolean get() = pendingConcert != null

    fun noteBuy(name: String, sp: Int, turn: Int?, gold: Boolean) {
        buys.add(SkillBuy(name, sp, turn, gold))
        pendingConcert?.clean = false
    }

    /** A row of a completely scanned Learn list proves the list was seen; only then can a missing gold mean "not offered". */
    fun noteSkillOffered(name: String, price: Int, discountPct: Int, turn: Int?, scanComplete: Boolean = true) {
        if (scanComplete) listParsed = true
        if (name in EVENT_GOLDS) goldOffers[name] = GoldOffer(price, discountPct, turn)
    }

    /** First observed turn of this task; a value above 1 marks a resumed (partial) career. */
    fun noteTurn(turn: Int) {
        if (firstTurn == null) firstTurn = turn
    }

    fun noteLesson(turn: Int?, title: String, kind: String) {
        lessons.add(Lesson(turn, title, kind))
    }

    /** One entry per (turn, title): a tap that did not advance re-handles the same event. */
    fun noteEvent(turn: Int?, title: String, owner: String, option: Int, rewardLines: List<String>) {
        if (events.any { it.turn == turn && it.title == title }) return
        events.add(EventNote(turn, title, owner, option, rewardLines))
        pendingConcert?.clean = false
    }

    fun noteConcertStart(turn: Int?, newSongs: Int, careerSongs: Int, spBefore: Int?) {
        val concert = Concert(turn, newSongs, careerSongs, spBefore)
        concerts.add(concert)
        pendingConcert = concert
    }

    /**
     * Closes the pending concert with [read]. An interrupt (stop, watchdog) propagates; any other failure or a negative
     * (unreadable) value leaves the after-read null.
     */
    fun closeConcert(read: () -> Int) {
        val concert = pendingConcert ?: return
        pendingConcert = null
        val sp =
            try {
                read()
            } catch (e: InterruptedException) {
                throw e
            } catch (_: Exception) {
                -1
            }
        if (sp >= 0) concert.spAfter = sp
    }

    /** First observed turn at or past 69 and 71 (Senior Early Nov and Dec), the lyric-event and special-finale song gates. */
    fun noteSongTotal(turn: Int, bought: Int) {
        if (turn >= 69 && songsAtTurn69 == null) songsAtTurn69 = turn to bought
        if (turn >= 71 && songsAtTurn71 == null) songsAtTurn71 = turn to bought
    }

    @Suppress("LongParameterList")
    fun buildRecord(
        timestamp: Long,
        app: String,
        fp: String?,
        trainee: String,
        scenario: String,
        outcome: String,
        turn: Int?,
        launchTransactionId: String?,
        finalSkills: Collection<String>?,
        uniqueLevel: Int?,
        skillPtsEnd: Int?,
        isGold: (String) -> Boolean,
        chainOf: (String) -> Collection<String> = { emptyList() },
    ): JSONObject {
        // A buy absent from the final Details read (itself or a higher version of it, as a bought white base shows only as its gold) is not counted as learned.
        val confirmed = if (finalSkills == null) buys else buys.filter { buy -> finalSkills.any { reaches(buy.name, it, chainOf) } }
        val unconfirmed = buys - confirmed.toSet()

        val record = JSONObject()
        record.put("type", RECORD_TYPE)
        record.put("v", 1)
        record.put("ts", timestamp)
        record.put("app", app)
        fp?.let { record.put("fp", it) }
        record.put("trainee", trainee)
        record.put("scenario", scenario)
        record.put("outcome", outcome)
        record.put("turn", turn ?: JSONObject.NULL)
        record.put("firstTurn", firstTurn ?: JSONObject.NULL)
        launchTransactionId?.let { record.put("launchTransactionId", it) }

        record.put("skills", skillsJson(finalSkills, confirmed, isGold, chainOf))
        record.put("buysVerifiedByFinalRead", finalSkills != null)
        record.put("unconfirmedBuys", JSONArray(unconfirmed.map { it.name }))

        val eventSp = eventSkillPoints()
        val earned =
            JSONObject().apply {
                put("event", eventSp.first ?: JSONObject.NULL)
                put("eventComplete", eventSp.second)
                put("concertPayout", JSONObject.NULL)
                put("training", JSONObject.NULL)
                put("race", JSONObject.NULL)
                put("lessonTechnique", JSONObject.NULL)
            }
        record.put(
            "sp",
            JSONObject().apply {
                put("spentOnBuys", confirmed.sumOf { it.sp })
                put("end", skillPtsEnd ?: JSONObject.NULL)
                put("endIsLastHeldValue", true)
                put("earned", earned)
                put("concertIntervals", JSONArray(concerts.map { concertIntervalJson(it) }))
            },
        )

        record.put(
            "songs",
            JSONObject().apply {
                put("boughtAtTurn69", songTotalJson(songsAtTurn69))
                put("boughtAtTurn71", songTotalJson(songsAtTurn71))
                put("floorNote", "bought counts exclude the free Make Debut and any song learned before the bot attached")
                put("concerts", JSONArray(concerts.map { concertJson(it) }))
            },
        )

        val lyric = events.firstOrNull { it.title.contains("lyric", ignoreCase = true) }
        record.put(
            "lyricEvent",
            JSONObject().apply {
                put("fired", if (lyric != null) true else JSONObject.NULL)
                put("choice", lyric?.option ?: JSONObject.NULL)
            },
        )

        val legendU = lessons.firstOrNull { it.title.startsWith("GIRLS' LEGEND U", ignoreCase = true) }
        record.put("girlsLegendU", legendU?.title ?: JSONObject.NULL)

        record.put("golds", JSONArray(EVENT_GOLDS.map { goldJson(it, finalSkills, confirmed) }))

        val lightHello = events.filter { it.owner.contains("Light Hello", ignoreCase = true) }
        val cove = events.firstOrNull { it.title.contains("Rainbow Cove", ignoreCase = true) }
        val coveJson: Any =
            if (cove == null) {
                JSONObject.NULL
            } else {
                JSONObject().put("turn", cove.turn ?: JSONObject.NULL).put("option", cove.option).put("outcome", JSONObject.NULL)
            }
        record.put(
            "lightHello",
            JSONObject().apply {
                put("events", JSONArray(lightHello.map { eventJson(it) }))
                put("rainbowCove", coveJson)
            },
        )

        record.put("lessons", JSONArray(lessons.map { JSONObject().put("lastReadTurn", it.turn ?: JSONObject.NULL).put("title", it.title).put("kind", it.kind) }))
        record.put("uniqueLevel", uniqueLevel?.takeIf { it > 0 } ?: JSONObject.NULL)

        val unknown = sortedSetOf<String>()
        if (finalSkills == null) unknown.add("skills.final")
        collectNulls("", record, unknown)
        record.put("unknown", JSONArray(unknown.toList()))
        return record
    }

    /** True when a bought [buyName] is [target] itself or a lower version that upgrades into it (the chain runs lowest to highest). */
    private fun reaches(buyName: String, target: String, chainOf: (String) -> Collection<String>): Boolean {
        if (skillFamily(buyName) == skillFamily(target)) return true
        val chain = chainOf(buyName).toList()
        val from = chain.indexOf(buyName)
        return from >= 0 && chain.drop(from).any { skillFamily(it) == skillFamily(target) }
    }

    /** With no final read, one row per skill line: a bought version is dropped when a later buy is the same skill or a higher version of it. */
    private fun topVersions(confirmed: List<SkillBuy>, chainOf: (String) -> Collection<String>): List<String> =
        confirmed.withIndex().filter { (i, buy) ->
            confirmed.withIndex().none { (j, other) ->
                j != i && reaches(buy.name, other.name, chainOf) && (skillFamily(other.name) != skillFamily(buy.name) || j > i)
            }
        }.map { it.value.name }

    private fun skillsJson(finalSkills: Collection<String>?, confirmed: List<SkillBuy>, isGold: (String) -> Boolean, chainOf: (String) -> Collection<String>): JSONArray {
        val out = JSONArray()
        val names = finalSkills?.toList() ?: topVersions(confirmed, chainOf)
        for (name in names) {
            val paid = confirmed.filter { reaches(it.name, name, chainOf) }
            // Bought means a buy of this very skill; a payment for only its white base proves nothing about how it was acquired.
            val buy = paid.lastOrNull { skillFamily(it.name) == skillFamily(name) }
            val source =
                when {
                    buy != null -> "bought"
                    finalSkills != null && grantedByFixedEventLine(name) -> "event"
                    else -> null
                }
            out.put(
                JSONObject().apply {
                    put("name", name)
                    put("tier", tierOf(name, buy?.gold ?: isGold(name)))
                    put("source", source ?: JSONObject.NULL)
                    // Every payment that built this skill (a white base plus its upgrade), so it matches spentOnBuys.
                    put("sp", if (buy == null) JSONObject.NULL else paid.sumOf { it.sp })
                },
            )
        }
        return out
    }

    /** A skill named on a non-hint line of an event option with no random branch: the only text that proves the event handed it over. */
    private fun grantedByFixedEventLine(name: String): Boolean {
        val family = skillFamily(name).lowercase()
        return events.any { note ->
            !hasRandomBranch(note.rewardLines) &&
                note.rewardLines.any { !isRandomLine(it) && it.lowercase().contains(family) && !it.contains("hint", ignoreCase = true) }
        }
    }

    private fun eventSkillPoints(): Pair<Int?, Boolean> {
        var total: Int? = null
        var complete = true
        for (note in events) {
            val random = hasRandomBranch(note.rewardLines)
            for (line in note.rewardLines) {
                if (!isSkillPointsLine(line)) continue
                val value = if (random || isRandomLine(line)) null else skillPointsValue(line)
                if (value == null) complete = false else total = (total ?: 0) + value
            }
        }
        return total to complete
    }

    private fun concertIntervalJson(c: Concert): JSONObject =
        JSONObject().apply {
            put("turn", c.turn ?: JSONObject.NULL)
            put("spBefore", c.spBefore ?: JSONObject.NULL)
            put("spAfter", c.spAfter ?: JSONObject.NULL)
            val before = c.spBefore
            val after = c.spAfter
            val delta = if (c.clean && before != null && before >= 0 && after != null) after - before else null
            put("delta", delta ?: JSONObject.NULL)
        }

    private fun concertJson(c: Concert): JSONObject =
        JSONObject().apply {
            put("turn", c.turn ?: JSONObject.NULL)
            put("newSongs", c.newSongs)
            put("careerSongsBought", c.careerSongs)
            put("greatSuccessSongFloorMet", c.newSongs >= GrandConcertPolicy.GREAT_SUCCESS_SONG_FLOOR.value)
            put("greatSuccess", JSONObject.NULL)
        }

    private fun songTotalJson(total: Pair<Int, Int>?): Any =
        if (total == null) JSONObject.NULL else JSONObject().put("turn", total.first).put("bought", total.second)

    private fun goldJson(name: String, finalSkills: Collection<String>?, confirmed: List<SkillBuy>): JSONObject {
        val bought = confirmed.lastOrNull { skillFamily(it.name) == name }
        val offer = goldOffers[name]
        val learned = finalSkills?.any { skillFamily(it) == name }
        return JSONObject().apply {
            put("name", name)
            put("offered", offeredOrNull(offer != null || bought != null))
            put("price", offer?.price ?: bought?.sp ?: JSONObject.NULL)
            put("discountPct", offer?.discountPct ?: JSONObject.NULL)
            put("hintLevel", offer?.let { hintLevelOf(it.discountPct) } ?: JSONObject.NULL)
            put("hintLevelDerived", true)
            put("bought", bought != null)
            // Learned without a bot buy in this task: an event grant, an earlier session, or a pre-attach purchase; only a fixed event line proves the first.
            put("ownedWithoutBotBuy", if (bought != null) false else learned ?: JSONObject.NULL)
            put("grantedByEvent", grantedByEventOrNull(bought != null, learned, name))
        }
    }

    /** True once seen; false only when a Learn list was parsed without it; otherwise unknown. */
    private fun offeredOrNull(seen: Boolean): Any =
        when {
            seen -> true
            listParsed -> false
            else -> JSONObject.NULL
        }

    /** False when bought or not owned; true on a fixed grant line; unknown (null) for an owned gold nothing explains or when no final list was read. */
    private fun grantedByEventOrNull(bought: Boolean, learned: Boolean?, name: String): Any =
        when {
            bought || learned == false -> false
            learned == null -> JSONObject.NULL
            grantedByFixedEventLine(name) -> true
            else -> JSONObject.NULL
        }

    private fun eventJson(e: EventNote): JSONObject =
        JSONObject().put("turn", e.turn ?: JSONObject.NULL).put("title", e.title).put("option", e.option)

    companion object {
        const val RECORD_TYPE = "gc_skill_econ"
        const val LOG_TAG = "[GC_SKILL_ECON]"

        /** The three Grand Concert scenario golds, named as the Learn list shows them. */
        val EVENT_GOLDS = listOf("See Ya Later!", "I Wanna Win with You", "Come What May")

        private val GLYPHS = listOf(" ○", " ◎", " ×")

        internal fun skillFamily(name: String): String = GLYPHS.fold(name.trim()) { n, g -> n.removeSuffix(g) }

        internal fun tierOf(name: String, gold: Boolean): String =
            when {
                gold -> "gold"
                name.endsWith("◎") -> "◎"
                name.endsWith("○") -> "○"
                else -> "none"
            }

        /** The game's discount ladder: 10 percent per hint level up to Lv3, then 5 percent steps to 40 at Lv5; null for any other value. */
        internal fun hintLevelOf(discountPct: Int): Int? =
            when (discountPct) {
                0 -> 0
                10 -> 1
                20 -> 2
                30 -> 3
                35 -> 4
                40 -> 5
                else -> null
            }

        /** Option text with a "Randomly either" header or a divider line holds alternative outcomes, so none of its amounts is certain. */
        internal fun hasRandomBranch(lines: List<String>): Boolean = lines.any { it.contains("randomly", ignoreCase = true) || it.trim().startsWith("---") }

        /** The data marks a single uncertain reward line with a "(random)" prefix. */
        internal fun isRandomLine(line: String): Boolean = line.trim().startsWith("(random)", ignoreCase = true)

        internal fun isSkillPointsLine(line: String): Boolean {
            val t = line.lowercase()
            return t.contains("skill") && (t.contains("pt") || t.contains("point"))
        }

        /** The signed amount on a fixed reward line; a slash-joined range is a random outcome and gives null. */
        internal fun skillPointsValue(line: String): Int? {
            if (line.contains("/")) return null
            val match = Regex("([+-]?)\\s*(\\d+)").find(line) ?: return null
            val value = match.groupValues[2].toIntOrNull() ?: return null
            return if (match.groupValues[1] == "-") -value else value
        }

        /** Dotted path of every null leaf; array elements share one `[]` path. */
        private fun collectNulls(path: String, node: Any?, out: MutableSet<String>) {
            when (node) {
                is JSONObject ->
                    node.keys().forEach { key ->
                        val child = if (path.isEmpty()) key else "$path.$key"
                        if (node.isNull(key)) out.add(child) else collectNulls(child, node.get(key), out)
                    }
                is JSONArray -> for (i in 0 until node.length()) collectNulls("$path[]", node.get(i), out)
            }
        }

        private fun goldLogToken(gold: JSONObject): String {
            val state =
                when {
                    gold.optBoolean("bought") -> "bought"
                    gold.opt("grantedByEvent") == true -> "granted"
                    gold.opt("ownedWithoutBotBuy") == true -> "owned"
                    gold.opt("offered") == true -> "offered"
                    gold.isNull("offered") -> "unknown"
                    else -> "none"
                }
            return gold.getString("name").replace(' ', '_') + ":" + state
        }

        /** One greppable line carrying the record's headline facts; the full detail lives in the JSON record. */
        fun logLine(record: JSONObject): String {
            val skills = record.optJSONArray("skills") ?: JSONArray()
            val bought = (0 until skills.length()).count { skills.getJSONObject(it).optString("source") == "bought" }
            val golds = record.optJSONArray("golds") ?: JSONArray()
            val goldText = (0 until golds.length()).joinToString(",") { goldLogToken(golds.getJSONObject(it)) }
            val sp = record.getJSONObject("sp")
            val songs = record.getJSONObject("songs")
            val s69 = songs.optJSONObject("boughtAtTurn69")?.optInt("bought")?.toString() ?: "unknown"
            val s71 = songs.optJSONObject("boughtAtTurn71")?.optInt("bought")?.toString() ?: "unknown"
            val unique = if (record.isNull("uniqueLevel")) "unknown" else record.get("uniqueLevel").toString()
            val spEnd = if (sp.isNull("end")) "unknown" else sp.get("end").toString()
            val unknownCount = record.optJSONArray("unknown")?.length() ?: 0
            return "$LOG_TAG outcome=${record.optString("outcome")} skills=${skills.length()} bought=$bought spSpent=${sp.optInt("spentOnBuys")} spEnd=$spEnd " +
                "songs69=$s69 songs71=$s71 golds=$goldText unique=$unique unknown=$unknownCount"
        }
    }
}
