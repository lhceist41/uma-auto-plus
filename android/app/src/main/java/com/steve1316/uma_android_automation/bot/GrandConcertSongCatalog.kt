package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName

/**
 * Static catalog of the Grand Concert shop songs on Global, keyed by title.
 *
 * Exists because of an OCR asymmetry on the lesson list: TITLES read reliably, but the small
 * mastery/concert bonus lines frequently come back garbled. The catalog turns a good title read
 * into the song's bonus TYPES so the decider can score by type instead of trusting mangled text.
 * The live shop stays authoritative for costs: the vectors here are defaults for planning and
 * cross-checking only, and any live vs catalog disagreement is reported, never fought.
 *
 * Data source: the Global client's own `master.mdb` (`single_mode_live_square` joined to
 * `single_mode_live_song_list` and `text_data`), extracted 2026-07-25. Titles, costs, mastery
 * effects and concert bonuses below are the shipped values and supersede every community table:
 * guides were wrong on this scenario repeatedly, including a song this catalog itself had under a
 * JP-romanized name that could never have matched a live read. How much a bonus is WORTH is a
 * tunable hypothesis and lives in [GrandConcertPolicy], not here.
 */

/**
 * The mastery-bonus type of a song (applies immediately on learning). [compounding] marks
 * per-training gains, whose value scales with the training turns left; immediate bonuses do not
 * compound and gain relative value late.
 */
enum class MasteryBonusType(val compounding: Boolean) {
    /** The free fixed songs (Make Debut!, GIRLS' LEGEND U): all points or all stats +10. */
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

/** The concert-bonus family of a song. Queued on learning; activates only after the NEXT concert,
 * then persists, so its value depends on post-activation runway, not total turns. */
enum class ConcertBonusType {
    FRIENDSHIP_10,
    FRIENDSHIP_5,
    SPECIALTY_PRIORITY_5,
    SUPPORT_CHAIN_1,
    NONE,
}

/**
 * One shop song. [cost] is the catalog DEFAULT (order Da/Pa/Vo/Vi/Co); the live shop overrides.
 * [phase] is the first concert cycle the song can appear in (1 = before the first Promo Concert,
 * 5 = before the Grand Concert). [alwaysBuy] marks the research's buy-on-sight songs.
 */
data class CatalogSong(
    val title: String,
    val mastery: MasteryBonusType,
    val concert: ConcertBonusType,
    val cost: PerformancePointVector,
    val phase: Int,
    val free: Boolean = false,
    val alwaysBuy: Boolean = false,
    /** Observed or plausible alternate spellings of the Global title. */
    val aliases: List<String> = emptyList(),
)

object GrandConcertSongCatalog {
    private fun v(da: Int, pa: Int, vo: Int, vi: Int, co: Int) = PerformancePointVector.of(da, pa, vo, vi, co)

    val songs: List<CatalogSong> =
        listOf(
            CatalogSong("Make Debut!", MasteryBonusType.FREE_ALL, ConcertBonusType.SPECIALTY_PRIORITY_5, v(0, 0, 0, 0, 0), phase = 1, free = true, alwaysBuy = true),
            CatalogSong("Believe in Miracles!", MasteryBonusType.WIT_TRAINING_1, ConcertBonusType.SPECIALTY_PRIORITY_5, v(0, 21, 0, 0, 21), phase = 1),
            CatalogSong("Zero Is Where the Center Stands!", MasteryBonusType.SPEED_TRAINING_1, ConcertBonusType.SUPPORT_CHAIN_1, v(21, 0, 0, 21, 0), phase = 1),
            // Observed live 2026-07-24: the Global client titles the reports' "Run Away! Fallin'
            // Love" (JP nuance) "Getaway! Fallin' Love".
            CatalogSong("Getaway! Fallin' Love", MasteryBonusType.GUTS_TRAINING_1, ConcertBonusType.SUPPORT_CHAIN_1, v(21, 0, 0, 21, 0), phase = 1, aliases = listOf("Run Away! Fallin' Love")),
            CatalogSong("Go This Way", MasteryBonusType.POWER_TRAINING_1, ConcertBonusType.SUPPORT_CHAIN_1, v(0, 0, 21, 0, 21), phase = 1),
            CatalogSong("Ring Ring Diary", MasteryBonusType.STAMINA_TRAINING_1, ConcertBonusType.SUPPORT_CHAIN_1, v(0, 21, 0, 21, 0), phase = 1),
            CatalogSong("Here Comes Our Time", MasteryBonusType.IMMEDIATE_POWER_22, ConcertBonusType.FRIENDSHIP_5, v(0, 0, 32, 0, 12), phase = 1),
            // The research reports render this title "RUNxRUN"; the launch-night capture reads "Run n' Run!".
            CatalogSong("Run n' Run!", MasteryBonusType.IMMEDIATE_SKILL_POINTS, ConcertBonusType.FRIENDSHIP_5, v(14, 0, 0, 16, 14), phase = 1, aliases = listOf("RUNxRUN")),
            CatalogSong("Full Speed Ahead! Umadol Power", MasteryBonusType.IMMEDIATE_SPEED_22, ConcertBonusType.FRIENDSHIP_5, v(32, 0, 0, 12, 0), phase = 1),
            CatalogSong("Run for Our Dream!", MasteryBonusType.SKILL_POINT_TRAINING_2, ConcertBonusType.SPECIALTY_PRIORITY_5, v(0, 21, 0, 21, 0), phase = 2, alwaysBuy = true),
            // Observed live 2026-07-23: the Global client titles the reports' "A NO NE" (JP song
            // name) "Hey, Guess What!" - same bonus profile (Guts +2, Specialty +5, Da 42 / Vi 21).
            CatalogSong("Hey, Guess What!", MasteryBonusType.GUTS_TRAINING_2, ConcertBonusType.SPECIALTY_PRIORITY_5, v(42, 0, 0, 21, 0), phase = 2, aliases = listOf("A NO NE")),
            CatalogSong("Our Blue Bird Days", MasteryBonusType.SPEED_TRAINING_2, ConcertBonusType.SPECIALTY_PRIORITY_5, v(21, 0, 0, 42, 0), phase = 2),
            // Observed live 2026-07-24 (album art): the Global title is "Grow Up and Shine!", not
            // the reports' "Grow Up, Shine!".
            CatalogSong("Grow Up and Shine!", MasteryBonusType.SKILL_POINT_TRAINING_3, ConcertBonusType.SUPPORT_CHAIN_1, v(21, 0, 21, 0, 21), phase = 3, alwaysBuy = true, aliases = listOf("Grow Up, Shine!")),
            CatalogSong("Sunbeam Cheer", MasteryBonusType.WIT_TRAINING_2, ConcertBonusType.SUPPORT_CHAIN_1, v(0, 42, 0, 0, 21), phase = 3),
            CatalogSong("Hoppity Sunny Days", MasteryBonusType.STAMINA_TRAINING_2, ConcertBonusType.SPECIALTY_PRIORITY_5, v(0, 42, 21, 0, 0), phase = 3),
            CatalogSong("Seven Colors Scenery", MasteryBonusType.POWER_TRAINING_2, ConcertBonusType.SPECIALTY_PRIORITY_5, v(0, 0, 21, 0, 42), phase = 3),
            CatalogSong("Dream Sky", MasteryBonusType.IMMEDIATE_WIT_22, ConcertBonusType.FRIENDSHIP_5, v(0, 22, 0, 0, 22), phase = 4),
            CatalogSong("Present March", MasteryBonusType.IMMEDIATE_POWER_22, ConcertBonusType.FRIENDSHIP_5, v(0, 0, 22, 0, 22), phase = 4),
            // The client ships this as "Precious Treasure Box". It was catalogued under the JP
            // romanization "My Favourite Treasure Box", which folds 9 edits away from the real
            // title against a cap of 2, so a live read could NEVER match it and one of the two
            // best songs in the shop (Speed +26 plus the only other Friendship +10%) scored as an
            // unknown. Found by diffing the catalog against master.mdb on 2026-07-25.
            CatalogSong("Precious Treasure Box", MasteryBonusType.IMMEDIATE_SPEED_26, ConcertBonusType.FRIENDSHIP_10, v(42, 0, 0, 26, 0), phase = 4, aliases = listOf("My Favourite Treasure Box", "Daisuki no Takarabako")),
            CatalogSong("The World's at Our Whim", MasteryBonusType.IMMEDIATE_STAMINA_22, ConcertBonusType.FRIENDSHIP_5, v(0, 32, 12, 0, 0), phase = 4),
            CatalogSong("Sky-Blue Spring", MasteryBonusType.IMMEDIATE_GUTS_22, ConcertBonusType.FRIENDSHIP_5, v(12, 0, 0, 32, 0), phase = 4),
            CatalogSong("Fanfare for the Future!", MasteryBonusType.IMMEDIATE_GUTS_26, ConcertBonusType.FRIENDSHIP_10, v(26, 0, 0, 42, 0), phase = 4),
            CatalogSong("GIRLS' LEGEND U", MasteryBonusType.FREE_ALL, ConcertBonusType.FRIENDSHIP_10, v(0, 0, 0, 0, 0), phase = 5, free = true, alwaysBuy = true),
        )

    /**
     * The cheapest unpurchased, non-free song available in the current stage ([CatalogSong.phase] at
     * or below [currentPhase]) for the training scorer's one-step next-song lookahead. Excludes free
     * songs (no point cost, and GIRLS' LEGEND U is out of the mission count), any song already bought
     * this career (each purchased title matched back to the catalog by [match], so OCR spelling
     * differences still exclude the right song), and, when supplied, [excludeTitle] - the song
     * currently on offer, which is still technically unpurchased and must not be returned as its own
     * "next" song (which would double-count its colors). [excludeTitle] is canonicalized through the
     * same [match] path as the purchased titles. Null when every stage song is already bought, free, or
     * excluded. Pure: it reads only this static catalog and makes no purchase decision.
     */
    fun cheapestUnpurchasedInStage(currentPhase: Int, purchasedTitles: Set<String>, excludeTitle: String? = null): CatalogSong? {
        val purchasedCanonical = purchasedTitles.mapNotNull { match(it)?.title }.toSet()
        val excludedCanonical = excludeTitle?.let { match(it)?.title }
        return songs
            .filter { !it.free && it.phase <= currentPhase && it.title !in purchasedCanonical && it.title != excludedCanonical && (it.cost.total() ?: 0) > 0 }
            .minByOrNull { it.cost.total() ?: Int.MAX_VALUE }
    }

    /**
     * Finds the catalog song for an OCR'd title, or null. Matching is deliberately layered for
     * the failure modes the captures actually showed: exact fold, then prefix (the card truncates
     * long titles, observed as "...Where the Cent"), then a small edit distance (observed
     * "Run n' Run!" vs the reports' "RUNxRUN"). Every fuzzy layer requires a UNIQUE winner;
     * anything ambiguous returns null because a wrong catalog hit is worse than none.
     */
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

        // Below six folded characters an edit distance of one is too permissive (a stray "None"
        // read sits one edit from the "A NO NE" alias), so short strings must match exactly.
        if (f.length < 6) return null

        val cap = if (f.length >= 12) 2 else 1
        val fuzzyHits =
            songs.filter { s ->
                boundedEditDistance(fold(s.title), f, cap) <= cap ||
                    s.aliases.any { boundedEditDistance(fold(it), f, cap) <= cap }
            }
        return fuzzyHits.singleOrNull()
    }

    /** Same fold as [lessonTitlesCompatible] so the two matchers can never disagree on identity:
     * lowercase, digit/letter lookalikes collapsed, everything else dropped. */
    private fun fold(s: String) = s.lowercase().replace('0', 'o').replace('1', 'i').replace('l', 'i').filter { it.isLetterOrDigit() }

    /** Levenshtein distance, abandoned early once it must exceed [cap]. */
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

    /** What a technique card does, as far as its effect line and cost signature reveal. */
    enum class TechniqueEffectKind { ENERGY, SKILL_POINTS, STAT_PLUS_SKILL_POINTS, SINGLE_STAT, TWO_STATS, SKILL_HINT, UNKNOWN }

    /** [coreStat] is true for Speed/Wit/Power, false for Stamina/Guts, null when no stat named.
     * [stat] is the exact stat a SINGLE_STAT technique grants, when identifiable; it is what lets
     * the scorer weigh the technique by the run's own stat priority instead of the coarse
     * core/secondary split. [viaCostSignature] marks a classification made from the cost alone
     * (unreadable text). */
    data class TechniqueRead(
        val kind: TechniqueEffectKind,
        val magnitude: Int?,
        val coreStat: Boolean? = null,
        val viaCostSignature: Boolean = false,
        val stat: StatName? = null,
    )

    private val CORE_STATS = listOf("speed", "wit", "power")
    private val SECONDARY_STATS = listOf("stamina", "guts")

    /** Word -> exact stat, for identifying which single stat an effect line names. */
    private val STAT_WORDS: Map<String, StatName> =
        mapOf(
            "speed" to StatName.SPEED,
            "wit" to StatName.WIT,
            "power" to StatName.POWER,
            "stamina" to StatName.STAMINA,
            "guts" to StatName.GUTS,
        )

    /** Cost token -> the exact stat its single-stat technique grants (Da=Speed, Pa=Stamina,
     * Vo=Power, Vi=Guts, Co=Wit; same mapping [CORE_STAT_COST_TYPES] classifies coarsely). */
    private val COST_TYPE_STAT: Map<PerformancePointType, StatName> =
        mapOf(
            PerformancePointType.DANCE to StatName.SPEED,
            PerformancePointType.PASSION to StatName.STAMINA,
            PerformancePointType.VOCAL to StatName.POWER,
            PerformancePointType.VISUAL to StatName.GUTS,
            PerformancePointType.COMPOSURE to StatName.WIT,
        )

    /**
     * Global technique titles -> effect identity. The effect text on the lesson list is frequently
     * garbled by OCR while titles read reliably (the same asymmetry the song catalog works around),
     * so on a garbled effect line the TITLE identifies the technique. Titles and effects below come
     * from the client's own `master.mdb` (`single_mode_live_square`, square_type 1 and 3). The
     * naming pattern is "Basics" / "Intermediate Class" / "Advanced Class" for tiers I to III.
     *
     * INCOMPLETE BY DESIGN: the client ships 36 technique families and this covers the 8 whose
     * shape the current [TechniqueRead] models exactly. The remainder are two-stat families
     * (Acting, Harmony, Rap, Jazz Dance, Isolation, Flexibility, Expression, Vocal Expression,
     * Dancing and Singing), stat-plus-Skill-Point families (Formation, Yoga, Vocal Theory,
     * Visuals Research, Idol History) and six RANGE families whose effect is written "+3 to 7"
     * (Rhythm, Abdominal Breathing, Fan Interaction, Hair Styling, Solo Performance, Autograph).
     * The range ones need a magnitude range rather than a single Int, so adding them is a model
     * change, not a data fill, and inventing a midpoint would be fabricating a number the client
     * does not state. Unlisted titles fall through to effect-text and cost-signature parsing,
     * which is the pre-existing behaviour, so this is a coverage gap and not a regression.
     */
    private val TECHNIQUE_TITLES: Map<String, TechniqueRead> =
        mapOf(
            // Energy: three tiers, 25/30/35 of a single type for +20/+30/+40.
            "Facial-Slimming Massage" to TechniqueRead(TechniqueEffectKind.ENERGY, 20),
            "Relaxing Body Massage" to TechniqueRead(TechniqueEffectKind.ENERGY, 30),
            "Full-Body Detox" to TechniqueRead(TechniqueEffectKind.ENERGY, 40),
            // Skill Points: each tier exists once per cost type, so the type is the shop's choice
            // rather than a property of the technique.
            "Watch an Up-and-Coming Idol's Concert" to TechniqueRead(TechniqueEffectKind.SKILL_POINTS, 5),
            "Watch a Mid-Career Idol's Concert" to TechniqueRead(TechniqueEffectKind.SKILL_POINTS, 8),
            "Watch a Top-Tier Idol's Concert" to TechniqueRead(TechniqueEffectKind.SKILL_POINTS, 12),
            // Single-stat families, one per token: Dance pays Speed, Passion Stamina, Vocal Power,
            // Visual Guts, Composure Wit, at 10/16/24 for +5/+8/+12.
            "Dance Step Basics" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 5, coreStat = true, stat = StatName.SPEED),
            "Dance Step Intermediate Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 8, coreStat = true, stat = StatName.SPEED),
            "Dance Step Advanced Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 12, coreStat = true, stat = StatName.SPEED),
            "Audience Involvement Basics" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 5, coreStat = false, stat = StatName.GUTS),
            "Audience Involvement Intermediate Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 8, coreStat = false, stat = StatName.GUTS),
            "Audience Involvement Advanced Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 12, coreStat = false, stat = StatName.GUTS),
            "Vocal Training Basics" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 5, coreStat = true, stat = StatName.POWER),
            "Vocal Training Intermediate Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 8, coreStat = true, stat = StatName.POWER),
            "Vocal Training Advanced Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 12, coreStat = true, stat = StatName.POWER),
            "Makeup Basics" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 5, coreStat = false, stat = StatName.STAMINA),
            "Makeup Intermediate Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 8, coreStat = false, stat = StatName.STAMINA),
            "Makeup Advanced Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 12, coreStat = false, stat = StatName.STAMINA),
            "Composure Training Basics" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 5, coreStat = true, stat = StatName.WIT),
            "Composure Training Intermediate Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 8, coreStat = true, stat = StatName.WIT),
            "Composure Training Advanced Class" to TechniqueRead(TechniqueEffectKind.SINGLE_STAT, 12, coreStat = true, stat = StatName.WIT),
            // Two-stat family observed live; the rest of the two-stat and range-effect families are
            // catalogued in master.mdb but not yet here (see the note above this map).
            "Mic Performance Basics" to TechniqueRead(TechniqueEffectKind.TWO_STATS, 4, coreStat = true),
            "Mic Performance Intermediate Class" to TechniqueRead(TechniqueEffectKind.TWO_STATS, 6, coreStat = true),
            "Mic Performance Advanced Class" to TechniqueRead(TechniqueEffectKind.TWO_STATS, 8, coreStat = true),
            "Group Lesson Basics" to TechniqueRead(TechniqueEffectKind.SKILL_HINT, 1),
            "Group Lesson Intermediate" to TechniqueRead(TechniqueEffectKind.SKILL_HINT, 2),
            "Group Lesson Advanced" to TechniqueRead(TechniqueEffectKind.SKILL_HINT, 3),
        ).mapKeys { fold(it.key) }

    /**
     * Full technique identification chain: readable effect text first (exact when OCR cooperates),
     * then the observed-title catalog (titles read reliably even when the effect line is garbage,
     * which the first live spend run proved), then the cost signature. Anything still unresolved
     * stays UNKNOWN rather than guessed.
     */
    fun parseTechnique(title: String?, effectText: String?, cost: PerformancePointVector): TechniqueRead {
        val fromText = parseTechniqueEffect(effectText, cost)
        // Readable effect TEXT is authoritative; a cost-signature inference is weaker than a
        // title match (Skill Point techniques share the 10/16/24 amounts on a random type, so a
        // known title must override the stat guess).
        if (fromText.kind != TechniqueEffectKind.UNKNOWN && !fromText.viaCostSignature) return fromText
        matchTechniqueTitle(title)?.let { return it }
        return fromText
    }

    /** Finds the technique-title catalog entry for an OCR'd title, with the same layered matching
     * the song catalog uses: exact fold, unique prefix (card titles truncate), then a small unique
     * edit distance. */
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
     * Classifies a technique from its effect text, falling back to the cost signature when the
     * text is unreadable. Identification is by effect, title, and cost structure, never by
     * community names, exactly as the research directs for the energy techniques ("Taking a Nap"
     * is NOT a verified name; the live Global name turned out to be "Facial-Slimming Massage").
     *
     * Cost-signature fallback: a single-type cost of 10/16/24 is a stat technique of the type's
     * mapped stat (cost type = the granting stat's primary token: Da=Speed, Pa=Stamina, Vo=Power,
     * Vi=Guts, Co=Wit; verified across every observed stat technique on 2026-07-24) OR a Skill
     * Point technique, which shares those amounts on a random type - so this inference defaults
     * to the stat reading and [parseTechnique] lets a title match override it. Hint and energy
     * amounts collide with each other (Group Lesson Advanced proved hints reach a single-type 30,
     * once assumed unique to Energy II), so those are never inferred from cost alone.
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

    /** Stat-technique cost tiers: a single-type cost of 10/16/24 grants +5/+8/+12 of the stat
     * whose facility pays that type. Disjoint from every hint and energy cost. */
    private val STAT_TECHNIQUE_TIERS = mapOf(10 to 5, 16 to 8, 24 to 12)

    /** Cost types whose mapped stat is core (Da=Speed, Vo=Power, Co=Wit); Pa=Stamina and
     * Vi=Guts map to secondary stats. */
    private val CORE_STAT_COST_TYPES = setOf(PerformancePointType.DANCE, PerformancePointType.VOCAL, PerformancePointType.COMPOSURE)

    /** The (type, amount) pair when exactly one type is positive and every component is
     * readable, else null. */
    private fun singleCost(cost: PerformancePointVector): Pair<PerformancePointType, Int>? {
        if (!cost.fullyKnown) return null
        val positives = PerformancePointType.entries.mapNotNull { type -> cost[type]?.takeIf { it > 0 }?.let { type to it } }
        return positives.singleOrNull()
    }
}
