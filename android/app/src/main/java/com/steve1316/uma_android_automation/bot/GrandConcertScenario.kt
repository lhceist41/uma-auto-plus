package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName

/**
 * Pure model for the Grand Concert scenario ("Brighter Together Our Grand Concert", community
 * name "Grand Live"), added to Global on 2026-07-22 22:00 UTC.
 *
 * Everything in this file is total, Android-free, and JUnit-pinned. Pixel work lives in
 * [com.steve1316.uma_android_automation.utils.GrandConcertProbes]; screen handling lives in the
 * campaign class.
 *
 * Support status: the shared career loop (dates, training scoring, racing plan, events, skill
 * buying) drives a Grand Concert career exactly as it drives URA Finale. The scenario's own
 * systems - the Lesson shop, the five performance point types, and the concerts - are automated
 * on top of that by the campaign class. What remains manual is the final Complete Career tap,
 * plus any concert screen the escort does not recognize, which reaches the manual-handoff
 * boundary rather than being clicked blind.
 *
 * Data provenance is tracked per fact ([Provenance]) because this file mixes three very
 * different evidence classes: strings pulled from the Global client's own master database,
 * mechanics published for the Japanese version months earlier, and community models.
 */

/** How well-established one fact is. Every seeded value carries one. */
enum class Provenance {
    /** Read out of the Global client's own data or observed on a Global screen capture. */
    GLOBAL_CONFIRMED,

    /** Published for the Japanese version and not yet re-verified on Global. */
    JP_CONFIRMED,

    /** A current published strategy guide's synthesis (uma.guide, GameTora, and similar). More
     * specific than a bare community model, but still not an official statement and not observed on
     * Global: a guide figure may not survive a live Global capture. */
    GUIDE,

    /** A community guide or simulator's model, not an official statement. */
    COMMUNITY_MODEL,

    /** Derived by reasoning from the above rather than observed. */
    INFERRED,

    /** Not known. Must never be treated as a number. */
    UNKNOWN,
}

/** One fact plus where it came from. */
data class Sourced<T>(val value: T, val provenance: Provenance)

object GrandConcertScenario {
    /**
     * The canonical scenario key. Chosen to match the short, human-readable style of the
     * existing keys ("URA Finale", "Unity Cup", "Trackblazer") rather than the full rendered
     * title, because this string is persisted in settings, hashed into the launch identity,
     * matched by presets, and printed in logs.
     */
    const val KEY = "Grand Concert"

    /** The title as the Global client renders it on Scenario Select and Final Confirmation:
     * two lines, no colon and no exclamation mark. Display only - never a persistence key. */
    const val DISPLAY_TITLE = "Brighter Together Our Grand Concert"

    /**
     * Every spelling that must resolve to [KEY]. Covers the community name, the two punctuated
     * forms that pre-launch research predicted, the unpunctuated form the client actually
     * renders, and the client's own internal name for the scenario ("Our Grand Concert", which
     * is also the name of its inheritance spark).
     */
    val ALIASES =
        listOf(
            "Grand Concert",
            "Grand Live",
            "Our Grand Concert",
            "Brighter Together Our Grand Concert",
            "Brighter Together! Our Grand Concert",
            "Brighter Together: Our Grand Concert",
        )

    /**
     * Base stat caps. GLOBAL_CONFIRMED: read directly off the Trainee Select and career screen
     * denominators on 2026-07-23 (1600 / 1300 / 1300 / 1500 / 1300). Blue inheritance sparks
     * raise these per-career (observed 1641 / 1300 / 1325 / 1500 / 1309 on the same career), so
     * these are the FLOOR, never an assertion about a specific run.
     */
    fun baseStatCap(statName: StatName): Int =
        when (statName) {
            StatName.SPEED -> 1600
            StatName.GUTS -> 1500
            StatName.STAMINA, StatName.POWER, StatName.WIT -> 1300
        }

    /**
     * The five concerts land on these fixed career turns: four Promo Concerts then the Grand
     * Concert. GLOBAL_CONFIRMED, read from the Global client's own master database
     * (single_mode_live_live_data.turn). Every concert shares the same Great Success song floor
     * ([GrandConcertPolicy.GREAT_SUCCESS_SONG_FLOOR] = 3; the same table's great_success_songs).
     */
    val CONCERT_TURNS = Sourced(listOf(24, 36, 48, 60, 72), Provenance.GLOBAL_CONFIRMED)

    /**
     * The Grand Concert (the fifth concert) gates its special song at this TOTAL setlist size.
     * GLOBAL_CONFIRMED from single_mode_live_live_data.grand_song_threshold = 20 (the four Promo
     * Concerts carry 0). Whether this equals the community "18 learned songs" figure plus automatic
     * setlist songs is NOT confirmed on Global; see [GrandConcertPolicy.SPECIAL_SONG_TARGET].
     */
    val GRAND_CONCERT_SONG_THRESHOLD = Sourced(20, Provenance.GLOBAL_CONFIRMED)

    /** The scenario's inheritance spark, as the Global client names it. */
    const val SPARK_NAME = "Our Grand Concert"

    /** Trainees with a scenario link. GLOBAL_CONFIRMED for the badge itself (observed on the
     * Trainee Select portrait); the roster is JP_CONFIRMED plus Global guide agreement. */
    val SCENARIO_LINK_TRAINEES =
        Sourced(
            listOf("Light Hello", "Agnes Tachyon", "Silence Suzuka", "Mihono Bourbon", "Smart Falcon"),
            Provenance.JP_CONFIRMED,
        )

    /**
     * Normalizes any accepted spelling to [KEY], leaving every other scenario string untouched.
     * Applied before dispatch, persistence comparison, queue and rotation validation, and logs,
     * so one career cannot be persisted under one spelling and dispatched under another.
     */
    fun normalizeScenarioKey(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return trimmed
        val folded = fold(trimmed)
        return if (ALIASES.any { fold(it) == folded }) KEY else trimmed
    }

    /** True when [raw] names this scenario under any accepted spelling. */
    fun matches(raw: String?): Boolean = normalizeScenarioKey(raw) == KEY

    /** Casing, punctuation, and whitespace are all OCR- and localisation-fragile; the fold
     * keeps only letters and digits so "Brighter Together! Our Grand Concert" and
     * "brighter together our grand concert" are the same key. */
    private fun fold(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }
}

/** The five performance point types. Global names come from the client's own text data (the
 * fifth is "Composure" there; several community guides call it "Mental", so that spelling is
 * accepted as an alias but never emitted). */
enum class PerformancePointType(val displayName: String, val aliases: List<String> = emptyList()) {
    DANCE("Dance"),
    PASSION("Passion"),
    VOCAL("Vocal"),
    VISUAL("Visual"),
    COMPOSURE("Composure", listOf("Mental")),
    ;

    companion object {
        fun fromText(text: String?): PerformancePointType? {
            val t = text?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { e ->
                e.displayName.lowercase() == t || e.aliases.any { it.lowercase() == t }
            }
        }
    }
}

/**
 * The five balances as last read off the screen. A null entry means "not readable", which is
 * different from zero and must stay different all the way through the policy.
 */
data class PerformanceBalances(val values: Map<PerformancePointType, Int?> = emptyMap()) {
    operator fun get(type: PerformancePointType): Int? = values[type]

    val complete: Boolean get() = PerformancePointType.entries.all { values[it] != null }

    val unknownTypes: List<PerformancePointType> get() = PerformancePointType.entries.filter { values[it] == null }
}

/** What one training choice is expected to pay out in performance points. Seeded from the
 * facility-to-type mapping, which is COMMUNITY_MODEL: the Global client does not publish it and
 * at least one Global guide states there is no fixed correspondence. */
data class PerformanceRewardPreview(
    val primary: PerformancePointType?,
    val secondary: PerformancePointType?,
    val provenance: Provenance,
)

/** A lesson's price in performance points. A null component means the cost could not be read. */
data class LessonCost(val amounts: Map<PerformancePointType, Int?> = emptyMap()) {
    val fullyKnown: Boolean get() = amounts.values.none { it == null }

    /** Affordability is a three-valued question: yes, no, or unknown. Never collapse unknown. */
    fun affordableWith(balances: PerformanceBalances): Boolean? {
        if (!fullyKnown) return null
        for ((type, needed) in amounts) {
            val have = balances[type] ?: return null
            if (have < (needed ?: return null)) return false
        }
        return true
    }
}

enum class LessonCardKind { TECHNIQUE, SONG, UNKNOWN }

/** A technique's immediate effect, as the client's effect text states it. */
data class TechniqueEffect(val text: String?, val provenance: Provenance = Provenance.UNKNOWN)

/** A song's Mastery Bonus (immediate on learning). */
data class SongEffect(val masteryText: String?, val provenance: Provenance = Provenance.UNKNOWN)

/** A song's Concert Bonus (activates after the next concert; same types stack additively). */
data class ConcertBonus(val text: String?, val provenance: Provenance = Provenance.UNKNOWN)

/** One offered card as read off the shop. Every field is nullable because every field can fail
 * to read, and an unreadable card must never be recommended. */
data class LessonCard(
    val slot: Int,
    val kind: LessonCardKind = LessonCardKind.UNKNOWN,
    val name: String? = null,
    val cost: LessonCost = LessonCost(),
    val techniqueEffect: TechniqueEffect? = null,
    val songEffect: SongEffect? = null,
    val concertBonus: ConcertBonus? = null,
    /** True when the shop offers to schedule this card for later instead of learning it now
     * (the Global help text calls this "schedule it for later"). */
    val scheduleOnly: Boolean? = null,
) {
    val readable: Boolean get() = kind != LessonCardKind.UNKNOWN && !name.isNullOrBlank() && cost.fullyKnown
}

/** The three cards currently on offer. */
data class LessonOfferSet(val cards: List<LessonCard> = emptyList()) {
    val complete: Boolean get() = cards.size == 3 && cards.all { it.readable }
}

/** Where the run sits in the technique-count pattern that gates the next song. JP_CONFIRMED
 * counts; the pattern is not published on Global. */
data class LessonPatternState(
    val techniquesSinceLastSong: Int?,
    val techniquesNeededForNextSong: Sourced<Int?>,
)

/** Which concert window the run is in. The Global help text calls the first four "Promo
 * Concerts" and the fifth the "Grand Concert". */
enum class ConcertSegment(val displayName: String) {
    BEFORE_PROMO_1("before the first Promo Concert"),
    BEFORE_PROMO_2("before the second Promo Concert"),
    BEFORE_PROMO_3("before the third Promo Concert"),
    BEFORE_PROMO_4("before the fourth Promo Concert"),
    BEFORE_GRAND("before the Grand Concert"),
    UNKNOWN("unknown"),
}

/** Everything the decision engine is allowed to look at. Anything absent stays null. */
data class GrandConcertRunState(
    val balances: PerformanceBalances = PerformanceBalances(),
    val offers: LessonOfferSet = LessonOfferSet(),
    val segment: ConcertSegment = ConcertSegment.UNKNOWN,
    val songsLearned: Int? = null,
    val pattern: LessonPatternState? = null,
    val turnsUntilNextConcert: Int? = null,
    val lessonUnlocked: Boolean? = null,
)

/** What the engine observed, so a report can be audited without re-running it. */
data class GrandConcertEvidence(
    val missing: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

/** The engine's answer. [recommendedSlot] is null whenever nothing can be recommended safely. */
data class GrandConcertDecision(
    val recommendedSlot: Int?,
    val certain: Boolean,
    val reasons: List<String> = emptyList(),
    val constraintsSatisfied: List<String> = emptyList(),
    val constraintsAtRisk: List<String> = emptyList(),
    val evidence: GrandConcertEvidence = GrandConcertEvidence(),
) {
    /** This engine never actuates. A decision is a report, and the campaign may only print it. */
    val actionable: Boolean get() = false
}
