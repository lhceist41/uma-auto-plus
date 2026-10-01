package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName

/**
 * Pure, Android-free model for the Grand Concert scenario ("Brighter Together Our Grand Concert", community
 * name "Grand Live"). Provenance is tracked per fact because values mix Global master-database strings,
 * mechanics published for the Japanese version, and community models.
 */

enum class Provenance {
    GLOBAL_CONFIRMED,

    JP_CONFIRMED,

    /** A published strategy guide's synthesis (uma.guide, GameTora); may not survive a live Global capture. */
    GUIDE,

    COMMUNITY_MODEL,

    INFERRED,

    /** Must never be treated as a number. */
    UNKNOWN,
}

data class Sourced<T>(val value: T, val provenance: Provenance)

object GrandConcertScenario {
    /**
     * Short key in the style of the existing keys; persisted in settings, hashed into the launch identity, matched by
     * presets and logged.
     */
    const val KEY = "Grand Concert"

    /**
     * The Global client's title on Scenario Select (two lines, no colon or exclamation mark). Display only, never a
     * persistence key.
     */
    const val DISPLAY_TITLE = "Brighter Together Our Grand Concert"

    /**
     * Every spelling that must resolve to [KEY], including the client's internal name ("Our Grand Concert", also its
     * spark name).
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
     * Base stat caps read off the Trainee Select and career screens; blue sparks raise them per career, so these are
     * a floor.
     */
    fun baseStatCap(statName: StatName): Int =
        when (statName) {
            StatName.SPEED -> 1600
            StatName.GUTS -> 1500
            StatName.STAMINA, StatName.POWER, StatName.WIT -> 1300
        }

    /**
     * Fixed concert turns (four Promo Concerts, then the Grand Concert), from single_mode_live_live_data.turn in the
     * Global master database.
     */
    val CONCERT_TURNS = Sourced(listOf(24, 36, 48, 60, 72), Provenance.GLOBAL_CONFIRMED)

    /**
     * Total setlist size gating the Grand Concert's special song (grand_song_threshold; Promo Concerts carry 0).
     * Whether it equals the community "18 learned songs" plus automatic songs is unconfirmed; see [GrandConcertPolicy.SPECIAL_SONG_TARGET].
     */
    val GRAND_CONCERT_SONG_THRESHOLD = Sourced(20, Provenance.GLOBAL_CONFIRMED)

    const val SPARK_NAME = "Our Grand Concert"

    /** The badge is Global-observed; the roster is JP-confirmed plus Global guide agreement. */
    val SCENARIO_LINK_TRAINEES =
        Sourced(
            listOf("Light Hello", "Agnes Tachyon", "Silence Suzuka", "Mihono Bourbon", "Smart Falcon"),
            Provenance.JP_CONFIRMED,
        )

    /**
     * Applied before dispatch, persistence, queue/rotation validation and logs so a career is never persisted under
     * one spelling and dispatched under another.
     */
    fun normalizeScenarioKey(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return trimmed
        val folded = fold(trimmed)
        return if (ALIASES.any { fold(it) == folded }) KEY else trimmed
    }

    fun matches(raw: String?): Boolean = normalizeScenarioKey(raw) == KEY

    /** Casing, punctuation and whitespace are OCR- and localisation-fragile, so only letters and digits are kept. */
    private fun fold(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }
}

/** Global calls the fifth type "Composure"; the community "Mental" is accepted as an alias but never emitted. */
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

/** A null entry means "not readable", which is different from zero and must stay different through the policy. */
data class PerformanceBalances(val values: Map<PerformancePointType, Int?> = emptyMap()) {
    operator fun get(type: PerformancePointType): Int? = values[type]

    val complete: Boolean get() = PerformancePointType.entries.all { values[it] != null }

    val unknownTypes: List<PerformancePointType> get() = PerformancePointType.entries.filter { values[it] == null }
}

/**
 * Seeded from the facility-to-type mapping, a COMMUNITY_MODEL: Global does not publish it and a guide says there is
 * no fixed correspondence.
 */
data class PerformanceRewardPreview(
    val primary: PerformancePointType?,
    val secondary: PerformancePointType?,
    val provenance: Provenance,
)

data class LessonCost(val amounts: Map<PerformancePointType, Int?> = emptyMap()) {
    val fullyKnown: Boolean get() = amounts.values.none { it == null }

    /** Three-valued: yes, no, or unknown (null); never collapse unknown. */
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

data class TechniqueEffect(val text: String?, val provenance: Provenance = Provenance.UNKNOWN)

data class SongEffect(val masteryText: String?, val provenance: Provenance = Provenance.UNKNOWN)

/** Activates after the next concert; same types stack additively. */
data class ConcertBonus(val text: String?, val provenance: Provenance = Provenance.UNKNOWN)

/** Every field can fail to read, and an unreadable card must never be recommended. */
data class LessonCard(
    val slot: Int,
    val kind: LessonCardKind = LessonCardKind.UNKNOWN,
    val name: String? = null,
    val cost: LessonCost = LessonCost(),
    val techniqueEffect: TechniqueEffect? = null,
    val songEffect: SongEffect? = null,
    val concertBonus: ConcertBonus? = null,
    /** True when the shop offers to schedule this card for later instead of learning it now. */
    val scheduleOnly: Boolean? = null,
) {
    val readable: Boolean get() = kind != LessonCardKind.UNKNOWN && !name.isNullOrBlank() && cost.fullyKnown
}

data class LessonOfferSet(val cards: List<LessonCard> = emptyList()) {
    val complete: Boolean get() = cards.size == 3 && cards.all { it.readable }
}

/** JP_CONFIRMED counts; the pattern is not published on Global. */
data class LessonPatternState(
    val techniquesSinceLastSong: Int?,
    val techniquesNeededForNextSong: Sourced<Int?>,
)

enum class ConcertSegment(val displayName: String) {
    BEFORE_PROMO_1("before the first Promo Concert"),
    BEFORE_PROMO_2("before the second Promo Concert"),
    BEFORE_PROMO_3("before the third Promo Concert"),
    BEFORE_PROMO_4("before the fourth Promo Concert"),
    BEFORE_GRAND("before the Grand Concert"),
    UNKNOWN("unknown"),
}

data class GrandConcertRunState(
    val balances: PerformanceBalances = PerformanceBalances(),
    val offers: LessonOfferSet = LessonOfferSet(),
    val segment: ConcertSegment = ConcertSegment.UNKNOWN,
    val songsLearned: Int? = null,
    val pattern: LessonPatternState? = null,
    val turnsUntilNextConcert: Int? = null,
    val lessonUnlocked: Boolean? = null,
)

data class GrandConcertEvidence(
    val missing: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

/** [recommendedSlot] is null whenever nothing can be recommended safely. */
data class GrandConcertDecision(
    val recommendedSlot: Int?,
    val certain: Boolean,
    val reasons: List<String> = emptyList(),
    val constraintsSatisfied: List<String> = emptyList(),
    val constraintsAtRisk: List<String> = emptyList(),
    val evidence: GrandConcertEvidence = GrandConcertEvidence(),
) {
    /** Never actuates: the campaign may only print a decision. */
    val actionable: Boolean get() = false
}
