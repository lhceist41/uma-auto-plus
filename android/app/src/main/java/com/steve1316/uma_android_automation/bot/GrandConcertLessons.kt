package com.steve1316.uma_android_automation.bot

/**
 * Pure models for the Grand Concert Lesson and Concert-Info screens: the lesson list, the two
 * confirmation dialogs (learn and schedule), the scheduling-complete dialog, the scheduled-state
 * lifecycle, the Concert Info screen, and the Hype model.
 *
 * Every type here is total, Android-free, and JUnit-pinned against the 2026-07-23 launch-night
 * captures (fixtures/grandconcert/PROVENANCE.md). Nothing here taps. The screen-family guard at
 * the bottom is the safety spine: it exists so a generic Confirm/Close/Next/OK handler can never
 * act on a lesson or concert screen, all of which route to the manual handoff instead.
 *
 * The distinction the whole file protects is scheduling vs learning. Scheduling a card is free and
 * inert - it queues nothing, spends nothing, and changes no counter - while learning is the only
 * transition that applies effects. Conflating the two would let the bot believe it had banked a
 * song's stat gain, concert bonus, and hype when it had merely reserved it.
 */

/** Which Grand Concert screen family is on-screen, for the generic-handler guard. Detection reads
 * the header text live; the pixel probes corroborate. */
enum class LessonScreen {
    /** Career main screen (the Lessons button lives here). Not a lesson screen itself. */
    CAREER,

    /** The three-card lesson list ("Select a technique or song to learn."). */
    LESSON_LIST,

    /** "Confirmation": the learn dialog (affordable), with Cancel / Learn. */
    LEARN_CONFIRMATION,

    /** "Schedule": the schedule dialog (unaffordable), with Cancel / Schedule. */
    SCHEDULE_CONFIRMATION,

    /** "Scheduling Complete": the post-schedule acknowledgement, Close only. */
    SCHEDULING_COMPLETE,

    /** "Concert Info": the hype / bonus / set-list summary, Close only. */
    CONCERT_INFO,

    /** Could not be identified. */
    UNKNOWN,
}

/** A five-type performance-point vector (costs, balances, or "points left over" which can go
 * negative). A null component means "not readable". */
data class PerformancePointVector(val values: Map<PerformancePointType, Int?> = emptyMap()) {
    operator fun get(type: PerformancePointType): Int? = values[type]

    val fullyKnown: Boolean get() = PerformancePointType.entries.all { values[it] != null }

    /** True when any readable component is negative (the shortfall the Schedule dialog shows). */
    val hasNegative: Boolean get() = values.values.any { it != null && it < 0 }

    /**
     * Affordability of this vector-as-a-cost against [balances], as a three-valued answer: true,
     * false, or null when any relevant component is unread. Never collapses unknown to false.
     */
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

/**
 * One card as read off the lesson list. Every field is nullable-tolerant because every field can
 * fail to read; [readable] gates whether the card may be reasoned about at all.
 */
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

    /** A technique's concert field reads "None"; a song carries a real concert bonus. Used only as
     * corroboration of [kind], never as its sole source. */
    val hasConcertBonus: Boolean get() = !concertText.isNullOrBlank() && !concertText.equals("None", ignoreCase = true)
}

/** The whole lesson list: the five current balances, the three offered cards, and whether the two
 * navigation buttons are present. */
data class LessonList(
    val balances: PerformancePointVector,
    val cards: List<LessonListCard>,
    val hasFullStats: Boolean,
    val hasConcertInfo: Boolean,
) {
    val complete: Boolean get() = cards.size == 3 && cards.all { it.readable } && balances.fullyKnown
}

/** The verdict of matching a confirmation dialog against the card the bot intended to act on. */
enum class LearnVerdict {
    /** The dialog names exactly the intended card (title, kind, effects agree). */
    EXACT_MATCH,

    /** Something could not be read well enough to be sure. Never act. */
    AMBIGUOUS,

    /** The dialog names a different card than intended. Never act. */
    CONTRADICTION,
}

/** A learn/schedule confirmation dialog as read. [isSchedule] is true for the unaffordable
 * "Schedule" dialog (the red "Not enough performance points" shortfall), false for "Confirmation".
 * [pointsLeftOver] is the post-action balance, which is negative on a schedule dialog. */
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
    /** A learn dialog must leave every balance non-negative; a schedule dialog is defined by a
     * negative balance. This is a self-consistency check, not a decision. */
    val affordabilityConsistent: Boolean
        get() = if (isSchedule) pointsLeftOver.hasNegative else !pointsLeftOver.hasNegative

    /**
     * Matches this dialog against the card the bot meant to confirm. Title mismatch is a
     * contradiction; an unreadable field is ambiguous; only a full agreement is an exact match.
     * This is the gate that keeps the bot from ever confirming a card it did not choose.
     */
    fun verifyAgainst(intended: LessonListCard): LearnVerdict {
        if (title.isNullOrBlank() || intended.title.isNullOrBlank()) return LearnVerdict.AMBIGUOUS
        if (!lessonTitlesCompatible(title, intended.title)) return LearnVerdict.CONTRADICTION
        if (kind == LessonCardKind.UNKNOWN || intended.kind == LessonCardKind.UNKNOWN) return LearnVerdict.AMBIGUOUS
        if (kind != intended.kind) return LearnVerdict.CONTRADICTION
        return LearnVerdict.EXACT_MATCH
    }
}

/** Tolerant equality for two reads of the SAME lesson title across OCR fuzz: fold to letters and
 * digits, then accept equality, distinctive containment (the shorter form long enough not to
 * alias), or a single edit on a long-enough fold. The edit tolerance exists because punctuation
 * can OCR into a LETTER ("Getaway! Fallin' Love" read as "Getawayl Fallin' Love" on the live
 * confirmation dialog), which folding cannot cancel: the true "!" folds away while the misread
 * "l" survives as "i", leaving a one-character insertion between two reads of the same card.
 * Kept local so the Grand Concert code does not depend on the spark chooser's evolving text
 * helpers. */
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

/** True when the Levenshtein distance between [a] and [b] is at most [cap]; bails early. */
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

/** The "Scheduling Complete" acknowledgement. */
data class SchedulingComplete(val title: String?, val kind: LessonCardKind) {
    val readable: Boolean get() = !title.isNullOrBlank()
}

/**
 * The lifecycle of one lesson card. The transitions carry the effect invariants that matter: only
 * [learn] applies effects; [schedule] is inert.
 */
enum class ScheduledLessonState {
    /** On offer but not affordable and not scheduled. */
    OFFERED,

    /** On offer and affordable now. */
    LEARNABLE,

    /** Reserved for later. Costs nothing and grants nothing yet. */
    SCHEDULED,

    /** Actually learned. The only state that has applied the card's effects. */
    LEARNED,
}

/** The concrete effects a lesson transition applies. Scheduling produces the all-zero instance; a
 * learn produces the card's real effects. Modeled so the two can never be silently conflated. */
data class LessonEffects(
    val pointsSpent: PerformancePointVector,
    val masteryApplied: Boolean,
    val concertBonusQueued: Boolean,
    val hypeAdded: Int,
    val learnedSongDelta: Int,
) {
    companion object {
        /** Scheduling is inert by definition: nothing spent, nothing applied, nothing queued. */
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
    /** Scheduling always yields the inert effects, regardless of the card. */
    fun scheduleEffects(): LessonEffects = LessonEffects.INERT

    /**
     * Learning applies the card's effects: it spends the cost, applies the Mastery bonus, queues a
     * Concert bonus for a song, adds hype for a song, and increments the learned-song count for a
     * song. Techniques add no hype and no learned song. The hype amount itself is UNKNOWN on Global
     * (the gauge is not yet numerically modeled), so [hypeAdded] is 1 as a presence flag for songs
     * and 0 for techniques - callers treat it as "hype moved", never as an exact gauge value.
     */
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

    /** Deficit remaining for a scheduled card's [type], from cost minus current balance, or null
     * when the balance is unreadable. Never negative. */
    fun scheduledRemaining(cost: PerformancePointVector, balances: PerformancePointVector, type: PerformancePointType): Int? {
        val c = cost[type] ?: return null
        val b = balances[type] ?: return null
        return maxOf(c - b, 0)
    }
}

/** A single concert-bonus panel on the Concert Info screen, with its before/after values as
 * rendered ("+0% -> +5%", "Lvl 0", etc.). Values are kept as read text plus optional parsed ints
 * so an unparsed one stays honest. */
data class ConcertBonusPanel(val name: String, val beforeText: String?, val afterText: String?)

/** The Hype tier as labeled on screen. UNKNOWN keeps an unread gauge from being treated as a
 * known tier. */
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

/** The Concert Info screen as read. */
data class ConcertInfo(
    val concertIndex: Int?,
    val hypeTier: HypeTier,
    val songsLearned: Int?,
    val bonuses: List<ConcertBonusPanel>,
    val setList: List<String>,
)

/**
 * The Hype state, keeping preview and applied strictly separate. The Schedule dialog shows a
 * "HYPE Lv UP!" preview gauge; that is what WOULD happen on learning, not what has happened. Only
 * a learned song moves [appliedIncreases]/[learnedSongs].
 */
data class HypeState(
    val currentTier: HypeTier,
    val gaugeConfidence: Boolean,
    val previewedIncrease: Boolean,
    val appliedIncrease: Boolean,
    val learnedSongs: Int,
    val scheduledSongs: Int,
) {
    /** Songs that have actually contributed to hype - scheduled songs are excluded by design. */
    val songsTowardConcert: Int get() = learnedSongs

    /** A scheduled song is a preview, never applied state. */
    fun afterScheduling(): HypeState = copy(previewedIncrease = true, scheduledSongs = scheduledSongs + 1)

    /** A learned song is the only thing that moves applied hype and the learned count. */
    fun afterLearningSong(): HypeState = copy(appliedIncrease = true, learnedSongs = learnedSongs + 1, previewedIncrease = false)
}

/**
 * The safety guard: which screens a generic Confirm/Close/Next/OK handler must NOT act on, and
 * which handoff reason they route to. Every lesson and concert screen is guarded, because a stray
 * generic tap on any of them can spend performance points, dismiss a choice, or skip a concert.
 */
object LessonScreenGuard {
    /** True when the generic post-run navigation handlers must not touch this screen. */
    fun requiresHandoff(screen: LessonScreen): Boolean =
        when (screen) {
            LessonScreen.LESSON_LIST,
            LessonScreen.LEARN_CONFIRMATION,
            LessonScreen.SCHEDULE_CONFIRMATION,
            LessonScreen.SCHEDULING_COMPLETE,
            LessonScreen.CONCERT_INFO,
            -> true
            LessonScreen.CAREER, LessonScreen.UNKNOWN -> false
        }

    /** The handoff reason for a guarded screen. */
    fun handoffReason(screen: LessonScreen): GrandConcertHandoffReason =
        when (screen) {
            LessonScreen.CONCERT_INFO -> GrandConcertHandoffReason.CONCERT_NOT_AUTOMATED
            else -> GrandConcertHandoffReason.LESSON_SHOP_NOT_AUTOMATED
        }
}
