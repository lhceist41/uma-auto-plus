package com.steve1316.uma_android_automation.bot

/**
 * Pure models for the Grand Concert Lesson and Concert-Info screens: the lesson list, the two
 * confirmation dialogs (learn and schedule), the scheduling-complete dialog, the scheduled-state
 * lifecycle, the Concert Info screen, and the Hype model.
 *
 * Android-free and pinned by fixtures (fixtures/grandconcert/PROVENANCE.md); nothing here taps.
 *
 * Scheduling a card is free and inert, while learning is the only transition that applies effects.
 * Conflating them would bank a song's stat gain, concert bonus, and hype that was merely reserved.
 */

/** A five-type performance-point vector; a null component means "not readable". */
data class PerformancePointVector(val values: Map<PerformancePointType, Int?> = emptyMap()) {
    operator fun get(type: PerformancePointType): Int? = values[type]

    val fullyKnown: Boolean get() = PerformancePointType.entries.all { values[it] != null }

    val hasNegative: Boolean get() = values.values.any { it != null && it < 0 }

    /** Raw sum across all five types, or null when any component is unread; a partial sum would understate and consumers must fail open. */
    fun total(): Int? {
        var sum = 0
        for (type in PerformancePointType.entries) {
            sum += values[type] ?: return null
        }
        return sum
    }

    /** Three-valued affordability against [balances]; null when any relevant component is unread, never collapsed to false. */
    fun affordableWith(balances: PerformancePointVector): Boolean? {
        for (type in PerformancePointType.entries) {
            val need = values[type] ?: return null
            if (need <= 0) continue
            val have = balances[type] ?: return null
            if (have < need) return false
        }
        return true
    }

    companion object {
        fun of(da: Int?, pa: Int?, vo: Int?, vi: Int?, co: Int?) =
            PerformancePointVector(
                mapOf(
                    PerformancePointType.DANCE to da,
                    PerformancePointType.PASSION to pa,
                    PerformancePointType.VOCAL to vo,
                    PerformancePointType.VISUAL to vi,
                    PerformancePointType.COMPOSURE to co,
                ),
            )
    }
}

/** One card as read off the lesson list; every field can fail to read, and [readable] gates whether it may be reasoned about. */
data class LessonListCard(
    val slot: Int,
    val title: String?,
    val kind: LessonCardKind,
    val masteryText: String?,
    val concertText: String?,
    val cost: PerformancePointVector,
    val learnable: Boolean?,
    val scheduled: Boolean?,
) {
    val readable: Boolean get() = kind != LessonCardKind.UNKNOWN && !title.isNullOrBlank() && cost.fullyKnown

    /** A technique's concert field reads "None"; used only to corroborate [kind]. */
    val hasConcertBonus: Boolean get() = !concertText.isNullOrBlank() && !concertText.equals("None", ignoreCase = true)
}

/** The whole lesson list: five balances, three offered cards, and whether the two navigation buttons are present. */
data class LessonList(
    val balances: PerformancePointVector,
    val cards: List<LessonListCard>,
    val hasFullStats: Boolean,
    val hasConcertInfo: Boolean,
) {
    val complete: Boolean get() = cards.size == 3 && cards.all { it.readable } && balances.fullyKnown
}

enum class LearnVerdict {
    EXACT_MATCH,

    /** Something could not be read well enough to be sure. Never act. */
    AMBIGUOUS,

    /** The dialog names a different card. Never act. */
    CONTRADICTION,
}

/** A learn/schedule confirmation dialog as read; [isSchedule] is the unaffordable "Schedule" dialog, whose [pointsLeftOver] is negative. */
data class LessonConfirmation(
    val isSchedule: Boolean,
    val title: String?,
    val kind: LessonCardKind,
    val masteryText: String?,
    val concertText: String?,
    val pointsLeftOver: PerformancePointVector,
    val hasCancel: Boolean,
    val hasAffirmative: Boolean,
) {
    /** A learn dialog must leave every balance non-negative; a schedule dialog has a negative balance. */
    val affordabilityConsistent: Boolean
        get() = if (isSchedule) pointsLeftOver.hasNegative else !pointsLeftOver.hasNegative

    /** Matches this dialog against the intended card; the gate that keeps the bot from confirming a card it did not choose. */
    fun verifyAgainst(intended: LessonListCard): LearnVerdict {
        if (title.isNullOrBlank() || intended.title.isNullOrBlank()) return LearnVerdict.AMBIGUOUS
        if (!lessonTitlesCompatible(title, intended.title)) return LearnVerdict.CONTRADICTION
        if (kind == LessonCardKind.UNKNOWN || intended.kind == LessonCardKind.UNKNOWN) return LearnVerdict.AMBIGUOUS
        if (kind != intended.kind) return LearnVerdict.CONTRADICTION
        return LearnVerdict.EXACT_MATCH
    }
}

/**
 * Tolerant equality for two OCR reads of the same lesson title: folded to letters and digits, then equal,
 * distinctive containment, or one edit on a long-enough fold. The edit covers punctuation OCRing into a letter
 * ("Getaway! Fallin' Love" read as "Getawayl ..."), which folding cannot cancel.
 */
internal fun lessonTitlesCompatible(a: String?, b: String?): Boolean {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return true
    fun fold(s: String) = s.lowercase().replace('0', 'o').replace('1', 'i').replace('l', 'i').filter { it.isLetterOrDigit() }
    val fa = fold(a)
    val fb = fold(b)
    if (fa.isEmpty() || fb.isEmpty()) return true
    if (fa == fb) return true
    val shorter = if (fa.length <= fb.length) fa else fb
    val longer = if (fa.length <= fb.length) fb else fa
    if (shorter.length >= 6 && longer.contains(shorter)) return true
    return shorter.length >= 8 && editDistanceAtMost(fa, fb, 1)
}

private fun editDistanceAtMost(a: String, b: String, cap: Int): Boolean {
    if (kotlin.math.abs(a.length - b.length) > cap) return false
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
        if (rowMin > cap) return false
        prev = cur
    }
    return prev[b.length] <= cap
}

data class SchedulingComplete(val title: String?, val kind: LessonCardKind) {
    val readable: Boolean get() = !title.isNullOrBlank()
}

/** Lifecycle of one lesson card: only [learn] applies effects; [schedule] is inert. */
enum class ScheduledLessonState {
    OFFERED,

    LEARNABLE,

    /** Reserved for later; costs and grants nothing yet. */
    SCHEDULED,

    /** Learned: the only state that has applied the card's effects. */
    LEARNED,
}

/** The effects a lesson transition applies; scheduling yields the all-zero instance so the two are never conflated. */
data class LessonEffects(
    val pointsSpent: PerformancePointVector,
    val masteryApplied: Boolean,
    val concertBonusQueued: Boolean,
    val hypeAdded: Int,
    val learnedSongDelta: Int,
) {
    companion object {
        val INERT =
            LessonEffects(
                pointsSpent = PerformancePointVector.of(0, 0, 0, 0, 0),
                masteryApplied = false,
                concertBonusQueued = false,
                hypeAdded = 0,
                learnedSongDelta = 0,
            )
    }
}

object ScheduledLessonModel {
    fun scheduleEffects(): LessonEffects = LessonEffects.INERT

    /** Learning spends the cost and applies the card's effects. [hypeAdded] is a presence flag (1 for songs, 0 for techniques): the gauge is not numerically modeled on Global. */
    fun learnEffects(card: LessonListCard): LessonEffects {
        val isSong = card.kind == LessonCardKind.SONG
        return LessonEffects(
            pointsSpent = card.cost,
            masteryApplied = true,
            concertBonusQueued = isSong && card.hasConcertBonus,
            hypeAdded = if (isSong) 1 else 0,
            learnedSongDelta = if (isSong) 1 else 0,
        )
    }

    /** Deficit remaining for a scheduled card's [type], or null when the balance is unreadable. Never negative. */
    fun scheduledRemaining(cost: PerformancePointVector, balances: PerformancePointVector, type: PerformancePointType): Int? {
        val c = cost[type] ?: return null
        val b = balances[type] ?: return null
        return maxOf(c - b, 0)
    }
}

/** A concert-bonus panel with before/after values kept as read text plus optional parsed ints. */
data class ConcertBonusPanel(val name: String, val beforeText: String?, val afterText: String?)

/** The Hype tier as labeled on screen; UNKNOWN keeps an unread gauge from being treated as a known tier. */
enum class HypeTier(val label: String) {
    NONE("No Hype"),
    MILD("Mild Hype"),
    GREAT("Great Hype"),
    UNKNOWN("Unknown"),
    ;

    companion object {
        fun fromText(text: String?): HypeTier {
            val t = text?.trim()?.lowercase() ?: return UNKNOWN
            return when {
                t.contains("great") -> GREAT
                t.contains("mild") -> MILD
                t.contains("no hype") -> NONE
                else -> UNKNOWN
            }
        }
    }
}

data class ConcertInfo(
    val concertIndex: Int?,
    val hypeTier: HypeTier,
    val songsLearned: Int?,
    val bonuses: List<ConcertBonusPanel>,
    val setList: List<String>,
)

/** Hype state keeping preview and applied separate: the Schedule dialog's "HYPE Lv UP!" gauge is a preview, only a learned song moves [appliedIncreases]/[learnedSongs]. */
data class HypeState(
    val currentTier: HypeTier,
    val gaugeConfidence: Boolean,
    val previewedIncrease: Boolean,
    val appliedIncrease: Boolean,
    val learnedSongs: Int,
    val scheduledSongs: Int,
) {
    /** Songs that have contributed to hype; scheduled songs are excluded. */
    val songsTowardConcert: Int get() = learnedSongs

    fun afterScheduling(): HypeState = copy(previewedIncrease = true, scheduledSongs = scheduledSongs + 1)

    fun afterLearningSong(): HypeState = copy(appliedIncrease = true, learnedSongs = learnedSongs + 1, previewedIncrease = false)
}
