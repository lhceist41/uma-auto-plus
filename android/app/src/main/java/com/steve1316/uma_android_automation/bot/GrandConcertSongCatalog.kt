package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName

/**
 * Static catalog of the Grand Concert shop songs on Global, keyed by title. Titles OCR reliably but the bonus lines
 * come back garbled, so a good title read yields the bonus TYPES. Values come from the client's `master.mdb`
 * (`single_mode_live_square`, `single_mode_live_song_list`, `text_data`) and supersede community tables, which were
 * wrong here. The live shop stays authoritative for costs; worth lives in [GrandConcertPolicy].
 */

/** Mastery-bonus type (applies on learning). [compounding] marks per-training gains, whose value scales with turns left. */
enum class MasteryBonusType(val compounding: Boolean) {
    FREE_ALL(false),
    SKILL_POINT_TRAINING_3(true),
    SKILL_POINT_TRAINING_2(true),
    SPEED_TRAINING_2(true),
    SPEED_TRAINING_1(true),
    WIT_TRAINING_2(true),
    WIT_TRAINING_1(true),
    POWER_TRAINING_2(true),
    POWER_TRAINING_1(true),
    STAMINA_TRAINING_2(true),
    STAMINA_TRAINING_1(true),
    GUTS_TRAINING_2(true),
    GUTS_TRAINING_1(true),
    IMMEDIATE_SKILL_POINTS(false),
    IMMEDIATE_SPEED_26(false),
    IMMEDIATE_SPEED_22(false),
    IMMEDIATE_WIT_22(false),
    IMMEDIATE_POWER_22(false),
    IMMEDIATE_STAMINA_22(false),
    IMMEDIATE_GUTS_26(false),
    IMMEDIATE_GUTS_22(false),
}

/** Concert-bonus family. Queued on learning, activates only after the NEXT concert, so value depends on post-activation runway. */
enum class ConcertBonusType {
    FRIENDSHIP_10,
    FRIENDSHIP_5,
    SPECIALTY_PRIORITY_5,
    SUPPORT_CHAIN_1,
    NONE,
}

/** One shop song. [cost] is the catalog default (Da/Pa/Vo/Vi/Co); [phase] is the first concert cycle it can appear in (1..5). */
data class CatalogSong(
    val title: String,
    val mastery: MasteryBonusType,
    val concert: ConcertBonusType,
    val cost: PerformancePointVector,
    val phase: Int,
    val free: Boolean = false,
    val alwaysBuy: Boolean = false,
    val aliases: List<String> = emptyList(),
)

object GrandConcertSongCatalog {
    private fun v(da: Int, pa: Int, vo: Int, vi: Int, co: Int) = PerformancePointVector.of(da, pa, vo, vi, co)

    val songs: List<CatalogSong> =
        listOf(
            CatalogSong("Make Debut!", MasteryBonusType.FREE_ALL, ConcertBonusType.SPECIALTY_PRIORITY_5, v(0, 0, 0, 0, 0), phase = 1, free = true, alwaysBuy = true),
            CatalogSong("Believe in Miracles!", MasteryBonusType.WIT_TRAINING_1, ConcertBonusType.SPECIALTY_PRIORITY_5, v(0, 21, 0, 0, 21), phase = 1),
            CatalogSong("Zero Is Where the Center Stands!", MasteryBonusType.SPEED_TRAINING_1, ConcertBonusType.SUPPORT_CHAIN_1, v(21, 0, 0, 21, 0), phase = 1),
            // The Global client titles "Run Away! Fallin' Love" as "Getaway! Fallin' Love".
            CatalogSong("Getaway! Fallin' Love", MasteryBonusType.GUTS_TRAINING_1, ConcertBonusType.SUPPORT_CHAIN_1, v(21, 0, 0, 21, 0), phase = 1, aliases = listOf("Run Away! Fallin' Love")),
            CatalogSong("Go This Way", MasteryBonusType.POWER_TRAINING_1, ConcertBonusType.SUPPORT_CHAIN_1, v(0, 0, 21, 0, 21), phase = 1),
            CatalogSong("Ring Ring Diary", MasteryBonusType.STAMINA_TRAINING_1, ConcertBonusType.SUPPORT_CHAIN_1, v(0, 21, 0, 21, 0), phase = 1),
            CatalogSong("Here Comes Our Time", MasteryBonusType.IMMEDIATE_POWER_22, ConcertBonusType.FRIENDSHIP_5, v(0, 0, 32, 0, 12), phase = 1),
            // Research reports render this "RUNxRUN"; the client reads "Run n' Run!".
            CatalogSong("Run n' Run!", MasteryBonusType.IMMEDIATE_SKILL_POINTS, ConcertBonusType.FRIENDSHIP_5, v(14, 0, 0, 16, 14), phase = 1, aliases = listOf("RUNxRUN")),
            CatalogSong("Full Speed Ahead! Umadol Power", MasteryBonusType.IMMEDIATE_SPEED_22, ConcertBonusType.FRIENDSHIP_5, v(32, 0, 0, 12, 0), phase = 1),
            CatalogSong("Run for Our Dream!", MasteryBonusType.SKILL_POINT_TRAINING_2, ConcertBonusType.SPECIALTY_PRIORITY_5, v(0, 21, 0, 21, 0), phase = 2, alwaysBuy = true),
            // The Global client titles "A NO NE" as "Hey, Guess What!" (same bonus profile).
            CatalogSong("Hey, Guess What!", MasteryBonusType.GUTS_TRAINING_2, ConcertBonusType.SPECIALTY_PRIORITY_5, v(42, 0, 0, 21, 0), phase = 2, aliases = listOf("A NO NE")),
            CatalogSong("Our Blue Bird Days", MasteryBonusType.SPEED_TRAINING_2, ConcertBonusType.SPECIALTY_PRIORITY_5, v(21, 0, 0, 42, 0), phase = 2),
            // The Global title is "Grow Up and Shine!", not "Grow Up, Shine!".
            CatalogSong("Grow Up and Shine!", MasteryBonusType.SKILL_POINT_TRAINING_3, ConcertBonusType.SUPPORT_CHAIN_1, v(21, 0, 21, 0, 21), phase = 3, alwaysBuy = true, aliases = listOf("Grow Up, Shine!")),
            CatalogSong("Sunbeam Cheer", MasteryBonusType.WIT_TRAINING_2, ConcertBonusType.SUPPORT_CHAIN_1, v(0, 42, 0, 0, 21), phase = 3),
            CatalogSong("Hoppity Sunny Days", MasteryBonusType.STAMINA_TRAINING_2, ConcertBonusType.SPECIALTY_PRIORITY_5, v(0, 42, 21, 0, 0), phase = 3),
            CatalogSong("Seven Colors Scenery", MasteryBonusType.POWER_TRAINING_2, ConcertBonusType.SPECIALTY_PRIORITY_5, v(0, 0, 21, 0, 42), phase = 3),
            CatalogSong("Dream Sky", MasteryBonusType.IMMEDIATE_WIT_22, ConcertBonusType.FRIENDSHIP_5, v(0, 22, 0, 0, 22), phase = 4),
            CatalogSong("Present March", MasteryBonusType.IMMEDIATE_POWER_22, ConcertBonusType.FRIENDSHIP_5, v(0, 0, 22, 0, 22), phase = 4),
            // Catalogued under the JP name "My Favourite Treasure Box" this was 9 edits from the real title, past the cap of 2, so it could never match.
            CatalogSong("Precious Treasure Box", MasteryBonusType.IMMEDIATE_SPEED_26, ConcertBonusType.FRIENDSHIP_10, v(42, 0, 0, 26, 0), phase = 4, aliases = listOf("My Favourite Treasure Box", "Daisuki no Takarabako")),
            CatalogSong("The World's at Our Whim", MasteryBonusType.IMMEDIATE_STAMINA_22, ConcertBonusType.FRIENDSHIP_5, v(0, 32, 12, 0, 0), phase = 4),
            CatalogSong("Sky-Blue Spring", MasteryBonusType.IMMEDIATE_GUTS_22, ConcertBonusType.FRIENDSHIP_5, v(12, 0, 0, 32, 0), phase = 4),
            CatalogSong("Fanfare for the Future!", MasteryBonusType.IMMEDIATE_GUTS_26, ConcertBonusType.FRIENDSHIP_10, v(26, 0, 0, 42, 0), phase = 4),
            CatalogSong("GIRLS' LEGEND U", MasteryBonusType.FREE_ALL, ConcertBonusType.FRIENDSHIP_10, v(0, 0, 0, 0, 0), phase = 5, free = true, alwaysBuy = true),
        )

    /** Cheapest unpurchased non-free song in [currentPhase] or earlier, for the one-step next-song lookahead. Purchased titles and [excludeTitle] (the song on offer) are canonicalized through [match]. Null when none remain. */
    fun cheapestUnpurchasedInStage(currentPhase: Int, purchasedTitles: Set<String>, excludeTitle: String? = null): CatalogSong? {
        val purchasedCanonical = purchasedTitles.mapNotNull { match(it)?.title }.toSet()
        val excludedCanonical = excludeTitle?.let { match(it)?.title }
        return songs
            .filter { !it.free && it.phase <= currentPhase && it.title !in purchasedCanonical && it.title != excludedCanonical && (it.cost.total() ?: 0) > 0 }
            .minByOrNull { it.cost.total() ?: Int.MAX_VALUE }
    }

    /** Finds the catalog song for an OCR'd title: exact fold, then prefix (cards truncate long titles), then small edit distance. Fuzzy layers need a UNIQUE winner; a wrong catalog hit is worse than none. */
    fun match(ocrTitle: String?): CatalogSong? {
        if (ocrTitle.isNullOrBlank()) return null
        val f = fold(ocrTitle)
        if (f.length < 4) return null

        songs.firstOrNull { s -> fold(s.title) == f || s.aliases.any { fold(it) == f } }?.let { return it }

        if (f.length >= 8) {
            val prefixHits = songs.filter { s -> fold(s.title).startsWith(f) || s.aliases.any { fold(it).startsWith(f) } }
            if (prefixHits.size == 1) return prefixHits.first()
            if (prefixHits.size > 1) return null
        }

        // Below six folded characters one edit is too permissive ("None" is one edit from the "A NO NE" alias).
        if (f.length < 6) return null

        val cap = if (f.length >= 12) 2 else 1
        val fuzzyHits =
            songs.filter { s ->
                boundedEditDistance(fold(s.title), f, cap) <= cap ||
                    s.aliases.any { boundedEditDistance(fold(it), f, cap) <= cap }
            }
        return fuzzyHits.singleOrNull()
    }

    /** Same fold as [lessonTitlesCompatible] so the two matchers cannot disagree on identity. */
    private fun fold(s: String) = s.lowercase().replace('0', 'o').replace('1', 'i').replace('l', 'i').filter { it.isLetterOrDigit() }

    private fun boundedEditDistance(a: String, b: String, cap: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > cap) return cap + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val sub = prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(sub, prev[j] + 1, cur[j - 1] + 1)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > cap) return cap + 1
            prev = cur
        }
        return prev[b.length]
    }

    // ---- Technique effect classification ----------------------------------------------------

    enum class TechniqueEffectKind { ENERGY, SKILL_POINTS, STAT_PLUS_SKILL_POINTS, SINGLE_STAT, TWO_STATS, SKILL_HINT, UNKNOWN }

    /** [coreStat] is true for Speed/Wit/Power, false for Stamina/Guts. [viaCostSignature] marks a classification made from the cost alone. */
    data class TechniqueRead(
        val kind: TechniqueEffectKind,
        val magnitude: Int?,
        val coreStat: Boolean? = null,
        val viaCostSignature: Boolean = false,
        val stat: StatName? = null,
    )

    private val CORE_STATS = listOf("speed", "wit", "power")
    private val SECONDARY_STATS = listOf("stamina", "guts")

    private val STAT_WORDS: Map<String, StatName> =
        mapOf(
            "speed" to StatName.SPEED,
            "wit" to StatName.WIT,
            "power" to StatName.POWER,
            "stamina" to StatName.STAMINA,
            "guts" to StatName.GUTS,
        )

    /** Cost token -> the stat its single-stat technique grants (Da=Speed, Pa=Stamina, Vo=Power, Vi=Guts, Co=Wit). */
    private val COST_TYPE_STAT: Map<PerformancePointType, StatName> =
        mapOf(
            PerformancePointType.DANCE to StatName.SPEED,
            PerformancePointType.PASSION to StatName.STAMINA,
            PerformancePointType.VOCAL to StatName.POWER,
            PerformancePointType.VISUAL to StatName.GUTS,
            PerformancePointType.COMPOSURE to StatName.WIT,
        )

    /**
     * Global technique titles -> effect identity, from `master.mdb` (`single_mode_live_square`, square_type 1 and 3).
     * Used when OCR garbles the effect line. Covers 8 of 36 families; the two-stat, stat-plus-Skill-Point and range
     * ("+3 to 7") families are omitted because a range needs a model change and a midpoint would be invented. Unlisted
     * titles fall through to effect-text and cost-signature parsing.
     */
    private val TECHNIQUE_TITLES: Map<String, TechniqueRead> =
        mapOf(
            // Energy: three tiers, 25/30/35 of a single type for +20/+30/+40.
            "Facial-Slimming Massage" to TechniqueRead(TechniqueEffectKind.ENERGY, 20),
            "Relaxing Body Massage" to TechniqueRead(TechniqueEffectKind.ENERGY, 30),
            "Full-Body Detox" to TechniqueRead(TechniqueEffectKind.ENERGY, 40),
            // Skill Points: each tier exists once per cost type, so the type is the shop's choice.
            "Watch an Up-and-Coming Idol's Concert" to TechniqueRead(TechniqueEffectKind.SKILL_POINTS, 5),
            "Watch a Mid-Career Idol's Concert" to TechniqueRead(TechniqueEffectKind.SKILL_POINTS, 8),
            "Watch a Top-Tier Idol's Concert" to TechniqueRead(TechniqueEffectKind.SKILL_POINTS, 12),
            // Single-stat families at 10/16/24 for +5/+8/+12.
            "Dance Step Basics" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 5, coreStat = true, stat = StatName.SPEED),
            "Dance Step Intermediate Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 8, coreStat = true, stat = StatName.SPEED),
            "Dance Step Advanced Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 12, coreStat = true, stat = StatName.SPEED),
            "Audience Involvement Basics" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 5, coreStat = false, stat = StatName.STAMINA),
            "Audience Involvement Intermediate Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 8, coreStat = false, stat = StatName.STAMINA),
            "Audience Involvement Advanced Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 12, coreStat = false, stat = StatName.STAMINA),
            "Vocal Training Basics" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 5, coreStat = true, stat = StatName.POWER),
            "Vocal Training Intermediate Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 8, coreStat = true, stat = StatName.POWER),
            "Vocal Training Advanced Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 12, coreStat = true, stat = StatName.POWER),
            "Makeup Basics" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 5, coreStat = false, stat = StatName.GUTS),
            "Makeup Intermediate Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 8, coreStat = false, stat = StatName.GUTS),
            "Makeup Advanced Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 12, coreStat = false, stat = StatName.GUTS),
            "Composure Training Basics" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 5, coreStat = true, stat = StatName.WIT),
            "Composure Training Intermediate Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 8, coreStat = true, stat = StatName.WIT),
            "Composure Training Advanced Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 12, coreStat = true, stat = StatName.WIT),
            "Mic Performance Basics" to TechniqueRead(TechniqueEffectKind.TWO_STATS, 4, coreStat = true),
            "Mic Performance Intermediate Class" to TechniqueRead(TechniqueEffectKind.TWO_STATS, 6, coreStat = true),
            "Mic Performance Advanced Class" to TechniqueRead(TechniqueEffectKind.TWO_STATS, 8, coreStat = true),
            "Group Lesson Basics" to TechniqueRead(TechniqueEffectKind.SKILL_HINT, 1),
            "Group Lesson Intermediate" to TechniqueRead(TechniqueEffectKind.SKILL_HINT, 2),
            "Group Lesson Advanced" to TechniqueRead(TechniqueEffectKind.SKILL_HINT, 3),
        ).mapKeys { fold(it.key) }

    /** Identification chain: readable effect text, then the title catalog, then the cost signature; otherwise UNKNOWN, never guessed. */
    fun parseTechnique(title: String?, effectText: String?, cost: PerformancePointVector): TechniqueRead {
        val fromText = parseTechniqueEffect(effectText, cost)
        // A title match overrides the cost-signature guess (Skill Point techniques share the 10/16/24 amounts on a random type).
        if (fromText.kind != TechniqueEffectKind.UNKNOWN && !fromText.viaCostSignature) return fromText
        matchTechniqueTitle(title)?.let { return it }
        return fromText
    }

    /** Technique-title lookup with the same layered matching as the song catalog. */
    internal fun matchTechniqueTitle(title: String?): TechniqueRead? {
        if (title.isNullOrBlank()) return null
        val f = fold(title)
        if (f.length < 6) return null
        TECHNIQUE_TITLES[f]?.let { return it }
        if (f.length >= 8) {
            val prefixHits = TECHNIQUE_TITLES.entries.filter { it.key.startsWith(f) }
            if (prefixHits.size == 1) return prefixHits.first().value
            if (prefixHits.size > 1) return null
        }
        val cap = if (f.length >= 12) 2 else 1
        return TECHNIQUE_TITLES.entries.filter { boundedEditDistance(it.key, f, cap) <= cap }.singleOrNull()?.value
    }

    /**
     * Classifies a technique from its effect text, falling back to the cost signature. A single-type cost of 10/16/24
     * is a stat technique OR a Skill Point technique (random type), so it defaults to the stat reading and
     * [parseTechnique] lets a title match override it. Hint and energy amounts collide (a single-type 30 can be a hint),
     * so those are never inferred from cost alone. Never classify by community names.
     */
    fun parseTechniqueEffect(effectText: String?, cost: PerformancePointVector): TechniqueRead {
        val t = effectText?.lowercase()?.trim().orEmpty()
        val magnitude = Regex("""\+\s*(\d+)""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        fun hasWord(w: String) = Regex("""\b$w\b""").containsMatchIn(t)
        val coreHits = CORE_STATS.count { hasWord(it) }
        val secondaryHits = SECONDARY_STATS.count { hasWord(it) }
        val statHits = coreHits + secondaryHits

        if (t.isNotEmpty()) {
            when {
                t.contains("energy") -> return TechniqueRead(TechniqueEffectKind.ENERGY, magnitude)
                t.contains("hint") -> return TechniqueRead(TechniqueEffectKind.SKILL_HINT, magnitude)
                t.contains("skill pt") || t.contains("skill point") ->
                    return if (statHits > 0) {
                        TechniqueRead(TechniqueEffectKind.STAT_PLUS_SKILL_POINTS, magnitude, coreStat = coreHits > 0)
                    } else {
                        TechniqueRead(TechniqueEffectKind.SKILL_POINTS, magnitude)
                    }
                statHits >= 2 -> return TechniqueRead(TechniqueEffectKind.TWO_STATS, magnitude, coreStat = coreHits > 0)
                statHits == 1 -> {
                    val namedStat = STAT_WORDS.entries.firstOrNull { (word, _) -> hasWord(word) }?.value
                    return TechniqueRead(TechniqueEffectKind.SINGLE_STAT, magnitude, coreStat = coreHits > 0, stat = namedStat)
                }
            }
        }

        val single = singleCost(cost)
        if (single != null) {
            val tierMagnitude = STAT_TECHNIQUE_TIERS[single.second]
            if (tierMagnitude != null) {
                return TechniqueRead(
                    TechniqueEffectKind.SINGLE_STAT,
                    tierMagnitude,
                    coreStat = single.first in CORE_STAT_COST_TYPES,
                    viaCostSignature = true,
                    stat = COST_TYPE_STAT[single.first],
                )
            }
        }
        return TechniqueRead(TechniqueEffectKind.UNKNOWN, magnitude)
    }

    /** Single-type cost tiers 10/16/24 grant +5/+8/+12 of the stat that type maps to; disjoint from hint and energy costs. */
    private val STAT_TECHNIQUE_TIERS = mapOf(10 to 5, 16 to 8, 24 to 12)

    /** Cost types whose mapped stat is core (Da=Speed, Vo=Power, Co=Wit). */
    private val CORE_STAT_COST_TYPES = setOf(PerformancePointType.DANCE, PerformancePointType.VOCAL, PerformancePointType.COMPOSURE)

    private fun singleCost(cost: PerformancePointVector): Pair<PerformancePointType, Int>? {
        if (!cost.fullyKnown) return null
        val positives = PerformancePointType.entries.mapNotNull { type -> cost[type]?.takeIf { it > 0 }?.let { type to it } }
        return positives.singleOrNull()
    }
}
