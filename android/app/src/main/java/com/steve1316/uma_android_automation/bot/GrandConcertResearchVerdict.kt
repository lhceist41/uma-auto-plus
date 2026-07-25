package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * The Grand Concert research verdict, as executable policy rather than prose.
 *
 * Two deep-research documents were produced for this scenario. This module fixes the decision that
 * reconciles them so the eventual per-turn engine does not re-litigate it:
 *
 * - Document 2 ("Brighter Together Our Grand Concert, optimal-run research") is the SPECIFICATION OF
 *   RECORD. It is grounded in the same launch-night fixtures this worktree pins, its scheduling model
 *   matches ScheduledLessonModel exactly, and it marks its uncertain claims honestly.
 * - Document 1 ("Optimal Grand Concert Automation Report") contributes exactly two facts: a
 *   post-concert Skill Point projection (25/song, 5/technique) and the 21-song technique pivot. Both
 *   enter as Provenance.GUIDE, never as confirmed. Everything else Document 1 adds is either already
 *   in Document 2 or explicitly rejected (see REJECTED_CLAIMS).
 *
 * The most consequential reconciliation was the performance-point income formula. Document 1 headlined
 * an exponential support term, Document 2 a linear one. A live Global capture on 2026-07-23 settled it:
 * Power C1=+11, Speed C2=+13, Guts C3=+15, Wit C3=+9 -- the +15 is impossible under the linear floor,
 * so the exponential support term is confirmed and the linear model falsified (CONFIRMED_INCOME_MODEL).
 * Two things stay open: the link term (+2L, never exercised at L>0) and the portrait-to-C counting
 * rule. The visible on-screen preview stays authoritative; the formula is a projection/plausibility
 * check only (resolvePointGain).
 *
 * Everything here is total, Android-free, JUnit-pinned, and report-only. No function accepts a turn
 * or concert index, which is why scenario-link story-date gating is structurally impossible here
 * (STORY_DATE_LINK_GATING).
 */

/** Which research document a fact came from. */
enum class ResearchSource {
    /** Document 1: "Optimal Grand Concert (Grand Live) Automation Report, Global". Corroboration
     * and two imports only. */
    DOCUMENT_1_AUTOMATION_REPORT,

    /** Document 2: "Brighter Together Our Grand Concert, optimal-run research". Specification of
     * record. */
    DOCUMENT_2_OPTIMAL_RUN,
}

/** The only two facts imported from Document 1. */
enum class Document1Import { SKILL_POINT_PROJECTION, TECHNIQUE_PIVOT }

/** Claims that were considered and rejected, kept enumerated so a future engine cannot silently
 * reintroduce one. */
enum class RejectedClaim {
    /** Withholding a scenario-link support's point contribution until a story date. Those dates
     * describe story appearances, not the point multiplier, which is active from unlock. */
    STORY_DATE_LINK_GATING,

    /** Document 1's "confirmed and datamine-validated" label on its income formula -- that datamine
     * basis was never valid grounds. (The exponential form was SUBSEQUENTLY confirmed on 2026-07-23 by
     * our own live capture, not by Document 1's datamine; see [GrandConcertResearchVerdict.INCOME_MODEL_BASIS].) */
    PP_FORMULA_CONFIRMED_LABEL,

    /** An unconditional "schedule the best card immediately" rule. Scheduling is one soft anchor,
     * not a reflex. */
    ALWAYS_SCHEDULE_IMMEDIATELY,

    /** A flat -50 (or any fixed) stat-target offset. Future concert/song gains are reserved
     * dynamically instead ([futureStatReserve]). */
    FLAT_STAT_OFFSET,
}

/** Whether more than one lesson can be scheduled at once. Only single-schedule behaviour has been
 * observed on Global; a second schedule's behaviour is genuinely unknown. */
enum class MultipleScheduleBehavior { SINGLE_TARGET_CONFIRMED, MULTIPLE_TARGET_UNKNOWN }

/**
 * How firmly the current concert's Great Success should be pursued. Gauge-first and feasibility
 * aware: a full gauge is already secured, an easily reachable one is a hard constraint, and one that
 * would cost materially inferior turns or a required objective is softened or abandoned. There is no
 * deck-rating breakpoint; the decision is state-based.
 */
enum class GreatSuccessPosture { SECURED, HARD, SOFT, ABANDONED, UNKNOWN }

/**
 * A performance-point income model. The support term was resolved by a live Global capture on
 * 2026-07-23: the exponential model is confirmed and the linear model falsified (see
 * [GrandConcertResearchVerdict.CONFIRMED_INCOME_MODEL]). Two things stay open and must not read as
 * settled: the link term (+2L) was never exercised (L=0 in every captured frame), and the rule that
 * turns on-screen support portraits into the count [c] is unresolved. [estimate] is a pure function of
 * the four readable inputs and, deliberately, of nothing else.
 *
 * @param s training-class base (9 for Speed/Stamina/Power/Guts, 5 for Wit -- confirmed live)
 * @param f facility level (1..5)
 * @param c number of counting support cards on the facility (the portrait->C rule is still open)
 * @param l number of those supports that are scenario-link cards
 */
sealed interface PointIncomeModel {
    val label: String
    val provenance: Provenance
    val source: ResearchSource

    fun estimate(s: Int, f: Int, c: Int, l: Int): Int
}

/** An exponential support term with a flat additive link term (form from Document 1). The support
 * term is GLOBAL_CONFIRMED by a live capture (Power C1=+11, Speed C2=+13, Guts C3=+15, Wit C3=+9);
 * +15 is impossible under the linear form, exact here. The link term (+2L) is still inferred -- L=0
 * in every captured frame -- so it is confirmed only for L=0. */
object ExponentialSupportModel : PointIncomeModel {
    override val label = "floor((S+F) * 1.15^C + 2L)"
    override val provenance = Provenance.GLOBAL_CONFIRMED
    override val source = ResearchSource.DOCUMENT_1_AUTOMATION_REPORT

    override fun estimate(s: Int, f: Int, c: Int, l: Int): Int =
        floor((s + f) * 1.15.pow(c.coerceAtLeast(0)) + 2.0 * l.coerceAtLeast(0)).toInt()
}

/** A linear support-and-link term (form from Document 2). FALSIFIED by the 2026-07-23 live capture:
 * its floors at base-9 Lvl-1 run 10, 11, 13, 14, 16 -- it can never produce the observed +15 at three
 * supports. Retained for the record and the plausibility band only; provenance stays INFERRED (it was
 * never confirmed), and it must not be read as a live alternative to the exponential model. */
object LinearSupportModel : PointIncomeModel {
    override val label = "floor((S+F) * (1 + 0.15C + 0.20L))"
    override val provenance = Provenance.INFERRED
    override val source = ResearchSource.DOCUMENT_2_OPTIMAL_RUN

    override fun estimate(s: Int, f: Int, c: Int, l: Int): Int =
        floor((s + f) * (1.0 + 0.15 * c.coerceAtLeast(0) + 0.20 * l.coerceAtLeast(0))).toInt()
}

/**
 * The resolved per-turn point gain for one training type. When a preview was read it is
 * authoritative and [value] is exactly it; the model band is carried only for plausibility. When no
 * preview was read, [value] is null and [uncertain] is true: a formula-only number never becomes an
 * actionable gain.
 */
data class ResolvedPointGain(
    val value: Int?,
    val authoritative: Boolean,
    val source: String,
    val uncertain: Boolean,
    val modelLow: Int,
    val modelHigh: Int,
    val modelsAgree: Boolean,
)

/** How feasible the 18-song route still is, with the projection that produced the verdict. */
data class SongRouteFeasibility(
    val feasible: Boolean,
    val maxFutureSongs: Int,
    val target: Int,
    val reason: String?,
)

object GrandConcertResearchVerdict {
    /** This whole module reports; it never actuates. */
    val actionable: Boolean get() = false

    // --- Source hierarchy ---

    val SPECIFICATION_OF_RECORD = ResearchSource.DOCUMENT_2_OPTIMAL_RUN

    /** Exactly the two facts taken from Document 1. */
    val DOCUMENT_1_IMPORTS: Set<Document1Import> =
        setOf(Document1Import.SKILL_POINT_PROJECTION, Document1Import.TECHNIQUE_PIVOT)

    /** Every claim considered and rejected. */
    val REJECTED_CLAIMS: Set<RejectedClaim> =
        setOf(
            RejectedClaim.STORY_DATE_LINK_GATING,
            RejectedClaim.PP_FORMULA_CONFIRMED_LABEL,
            RejectedClaim.ALWAYS_SCHEDULE_IMMEDIATELY,
            RejectedClaim.FLAT_STAT_OFFSET,
        )

    /** Both income models, retained for the record. As of the 2026-07-23 live capture they no longer
     * "compete": [CONFIRMED_INCOME_MODEL] is confirmed and [FALSIFIED_INCOME_MODEL] falsified. */
    val POINT_INCOME_MODELS: List<PointIncomeModel> = listOf(ExponentialSupportModel, LinearSupportModel)

    /** The income model confirmed by the live Global capture (2026-07-23). Use this for any projection. */
    val CONFIRMED_INCOME_MODEL: PointIncomeModel = ExponentialSupportModel

    /** The income model the same capture falsified. Kept only for the plausibility band and the record. */
    val FALSIFIED_INCOME_MODEL: PointIncomeModel = LinearSupportModel

    /** What the confirmation rests on, so a future reader can audit it. */
    const val INCOME_MODEL_BASIS =
        "live Global 2026-07-23: Power C1=+11, Speed C2=+13, Guts C3=+15, Wit(base5) C3=+9; " +
            "+15 is impossible under the linear floor at base-9 Lvl-1"

    /** The link term (+2L) was never exercised (L=0 in every captured frame): still inferred. */
    const val INCOME_LINK_TERM_CONFIRMED = false

    /** The rule mapping on-screen support portraits to the count C is unresolved (Stamina anomaly). */
    const val INCOME_COUNTING_RULE_RESOLVED = false

    // --- Document 1 imports (GUIDE provenance, projection only) ---

    /** Post-concert Skill Points per learned song. Document 1 / GameTora. A projection, not verified
     * balance arithmetic: it must not drive an irreversible choice until a live Global concert result
     * proves it. */
    val SKILL_POINTS_PER_LEARNED_SONG = Sourced(25, Provenance.GUIDE)

    /** Post-concert Skill Points per learned technique. Same provenance and caveat as
     * [SKILL_POINTS_PER_LEARNED_SONG]. */
    val SKILL_POINTS_PER_LEARNED_TECHNIQUE = Sourced(5, Provenance.GUIDE)

    /** The 25/5 figures are a guide projection, never treated as confirmed balance arithmetic. */
    const val SKILL_POINT_PROJECTION_VERIFIED = false

    /** Song count at which weak remaining song offers may justify pivoting to technique lessons for
     * Skill Points. Document 1. Never overrides a higher constraint (see [techniquePivotAllowed]). */
    val TECHNIQUE_PIVOT_SONG_THRESHOLD = Sourced(21, Provenance.GUIDE)

    /** A pure projection of end-of-run Skill Points from lessons. Uses the GUIDE 25/5 figures and is
     * for reporting/planning only. */
    fun projectedSkillPointsFromLessons(songsLearned: Int, techniquesLearned: Int): Int =
        songsLearned.coerceAtLeast(0) * SKILL_POINTS_PER_LEARNED_SONG.value +
            techniquesLearned.coerceAtLeast(0) * SKILL_POINTS_PER_LEARNED_TECHNIQUE.value

    // --- Performance-point income: models are inferred, preview is authoritative ---

    /** The guide-published class base for the income models: 5 for Wit, 9 for every other facility. */
    fun baseYield(facility: StatName): Int = if (facility == StatName.WIT) 5 else 9

    /** True where the two inferred models produce different integers, so a projection there is a band
     * rather than a number. They agree at 0..2 supports and first diverge at 3. */
    fun modelsDiverge(s: Int, f: Int, c: Int, l: Int): Boolean =
        ExponentialSupportModel.estimate(s, f, c, l) != LinearSupportModel.estimate(s, f, c, l)

    /**
     * Resolves the point gain for one training type. A non-null [preview] is authoritative and wins
     * over both formulas, even when it contradicts them; a null preview yields no actionable value,
     * only the model band and an uncertainty flag.
     */
    fun resolvePointGain(preview: Int?, s: Int, f: Int, c: Int, l: Int): ResolvedPointGain {
        val exp = ExponentialSupportModel.estimate(s, f, c, l)
        val lin = LinearSupportModel.estimate(s, f, c, l)
        val low = minOf(exp, lin)
        val high = maxOf(exp, lin)
        return if (preview != null) {
            ResolvedPointGain(preview, true, "preview", false, low, high, exp == lin)
        } else {
            ResolvedPointGain(null, false, "formula-projection-only", true, low, high, exp == lin)
        }
    }

    // --- Scenario-link and Light Hello: no story-date gate ---

    /** Scenario-link support-card point contribution is NOT gated behind any story date; it counts
     * from performance-point unlock onward. Encoded structurally: no function here takes a turn or
     * concert index, so a date gate cannot be expressed. */
    const val STORY_DATE_LINK_GATING = false

    val LINK_CONTRIBUTION_ACTIVE_FROM = "performance-point unlock (not story-date gated)"

    /** Light Hello's Training Together grants +20 of the currently lowest point type, but only on a
     * detected trigger with a post-turn balance readback. Counted as zero until then. */
    const val LIGHT_HELLO_LOWEST_TYPE_BONUS = 20

    /** Guide-reported trigger rate (~45%); the JP datamine measured lower. COMMUNITY_MODEL, and never
     * used to pre-credit points. */
    val LIGHT_HELLO_TRIGGER_RATE = Sourced(0.45, Provenance.COMMUNITY_MODEL)

    /** The Light Hello bonus, credited only after a confirmed trigger. */
    fun lightHelloLowestTypeBonus(triggerDetected: Boolean): Int =
        if (triggerDetected) LIGHT_HELLO_LOWEST_TYPE_BONUS else 0

    // --- Great Success: gauge-first, feasibility-aware ---

    /** Normal Success stat gain, per stat. */
    const val NORMAL_SUCCESS_GAIN = 3

    /** Great Success stat gain, per stat. */
    const val GREAT_SUCCESS_GAIN = 10

    /** Marginal value of a Great over a normal Success, per stat (+7). */
    const val GREAT_SUCCESS_MARGINAL_GAIN = GREAT_SUCCESS_GAIN - NORMAL_SUCCESS_GAIN

    /** GIRLS' LEGEND U Mastery Bonus: +10 to all stats when granted. */
    const val GIRLS_LEGEND_U_MASTERY_GAIN = 10

    /** Stat value at and above which training/concert gains are halved. */
    const val SOFT_CAP_THRESHOLD = 1200

    /** Song increments needed to fill the Hype gauge for a Great Success. Gauge-first: this is the
     * fallback count when the gauge itself cannot be read. Mirrors
     * [GrandConcertPolicy.GREAT_SUCCESS_SONG_FLOOR], now GLOBAL_CONFIRMED from the Global client's
     * master database (single_mode_live_live_data.great_success_songs = 3 for all five concerts). */
    val HYPE_GAUGE_SONG_INCREMENTS = Sourced(3, Provenance.GLOBAL_CONFIRMED)

    /** Song increments still needed for a full gauge, gauge-first: from the observed gauge when it is
     * readable, never inferred from total career song count. Null when the gauge could not be read. */
    fun neededSongIncrements(observedHypeIncrements: Int?): Int? =
        observedHypeIncrements?.let { maxOf(HYPE_GAUGE_SONG_INCREMENTS.value - it, 0) }

    /**
     * The Great Success posture for the current concert.
     *
     * @param observedHypeIncrements gauge increments read this segment, or null if unread
     * @param reachableSafely whether the remaining deficit can be closed by one acceptable training
     *   or an already-affordable purchase; null when it cannot be determined
     * @param wouldForceInferiorTurns whether closing the gap needs materially inferior turns
     * @param threatensRequiredObjective whether closing the gap threatens a required race/objective
     */
    fun greatSuccessPosture(
        observedHypeIncrements: Int?,
        reachableSafely: Boolean?,
        wouldForceInferiorTurns: Boolean,
        threatensRequiredObjective: Boolean,
    ): GreatSuccessPosture {
        val needed = neededSongIncrements(observedHypeIncrements)
        if (needed != null && needed <= 0) return GreatSuccessPosture.SECURED
        if (reachableSafely == null) return GreatSuccessPosture.UNKNOWN
        if (threatensRequiredObjective) return GreatSuccessPosture.ABANDONED
        if (!reachableSafely) return GreatSuccessPosture.SOFT
        if (wouldForceInferiorTurns) return GreatSuccessPosture.SOFT
        return GreatSuccessPosture.HARD
    }

    // --- Soft-cap transform and dynamic stat reserve ---

    private fun capFor(stat: StatName, caps: Map<StatName, Int>): Int =
        caps[stat] ?: GrandConcertScenario.baseStatCap(stat)

    /**
     * The effective stat increase from [rawGain] raw points starting at [current], applying the 1200
     * soft cap (points at or above 1200 count half) and the hard [cap] (points at or above it count
     * zero). This is the same transform the scoring should use to value concert and song gains.
     */
    fun transformedGain(current: Int, rawGain: Int, cap: Int): Int {
        if (rawGain <= 0) return 0
        var effective = 0.0
        var pos = current
        var added = 0
        while (added < rawGain && pos < cap) {
            effective += if (pos >= SOFT_CAP_THRESHOLD) 0.5 else 1.0
            pos += 1
            added += 1
        }
        return floor(effective).toInt()
    }

    /** The transformed value of a Great over a normal Success this concert: +7 to all five stats,
     * each passed through [transformedGain] at its current value and cap. This is the number a
     * per-turn engine compares against a training's opportunity loss. */
    fun marginalGreatSuccessValue(currentStats: Map<StatName, Int>, caps: Map<StatName, Int> = emptyMap()): Int =
        StatName.entries.sumOf { transformedGain(currentStats[it] ?: 0, GREAT_SUCCESS_MARGINAL_GAIN, capFor(it, caps)) }

    /**
     * Expected raw stat gain still to come from concerts and the special song, before soft-capping.
     * Per remaining concert this is 3 + 7 x P(Great Success); the GIRLS' LEGEND U Mastery adds 10
     * when it is still expected. [pGreatSuccess] is clamped to [0,1] and should be 1 only when the
     * gauge is already full or the plan is effectively guaranteed.
     */
    fun expectedFutureRawGain(remainingConcerts: Int, pGreatSuccess: Double, girlsLegendUExpected: Boolean): Double {
        val perConcert = NORMAL_SUCCESS_GAIN + GREAT_SUCCESS_MARGINAL_GAIN * pGreatSuccess.coerceIn(0.0, 1.0)
        val concerts = remainingConcerts.coerceAtLeast(0) * perConcert
        val glu = if (girlsLegendUExpected) GIRLS_LEGEND_U_MASTERY_GAIN else 0
        return concerts + glu
    }

    /**
     * The dynamic amount to subtract from a stat's final training target, because future concerts and
     * the special song will add it later. Replaces any flat offset: it depends on the stat's current
     * value, its cap, how many concerts remain, the Great-Success probability, and whether GIRLS'
     * LEGEND U is still expected, and it is soft-capped so a reserve near 1200 is not over-counted.
     */
    fun futureStatReserve(
        current: Int,
        cap: Int,
        remainingConcerts: Int,
        pGreatSuccess: Double,
        girlsLegendUExpected: Boolean,
    ): Int {
        val raw = expectedFutureRawGain(remainingConcerts, pGreatSuccess, girlsLegendUExpected).roundToInt()
        return transformedGain(current, raw, cap)
    }

    // --- Song-route feasibility ---

    /** Song target for the special-song route ([GrandConcertPolicy.SPECIAL_SONG_TARGET]). */
    val SPECIAL_SONG_TARGET = GrandConcertPolicy.SPECIAL_SONG_TARGET

    /**
     * Whether the 18-song route is still reachable, from an explicit projection of songs from current
     * balances, from safe projected training income, and from automatic future songs. The route is a
     * soft target that becomes a hard constraint only while this returns feasible, and it can become
     * infeasible mid-run.
     */
    fun eighteenSongFeasibility(
        currentSongs: Int,
        songsFromCurrentBalances: Int,
        songsFromProjectedSafeTraining: Int,
        automaticFutureSongs: Int,
        target: Int = SPECIAL_SONG_TARGET.value,
    ): SongRouteFeasibility {
        val max =
            currentSongs.coerceAtLeast(0) +
                songsFromCurrentBalances.coerceAtLeast(0) +
                songsFromProjectedSafeTraining.coerceAtLeast(0) +
                automaticFutureSongs.coerceAtLeast(0)
        val feasible = max >= target
        return SongRouteFeasibility(
            feasible = feasible,
            maxFutureSongs = max,
            target = target,
            reason = if (feasible) null else "projected max $max songs is below the $target-song target",
        )
    }

    // --- 21-song technique pivot, subordinate to every higher constraint ---

    /** Second-schedule behaviour is unknown; the model must not assume a queue or a replacement. */
    val SECOND_SCHEDULE_BEHAVIOR = Sourced(MultipleScheduleBehavior.MULTIPLE_TARGET_UNKNOWN, Provenance.UNKNOWN)

    /**
     * Whether pivoting remaining lessons to techniques for Skill Points is allowed. It is permitted
     * ONLY at or above the 21-song threshold AND with every higher constraint satisfied: the Hype
     * floor already secured, the 18-song route not being sacrificed, no mandatory objective pending,
     * the turn safe, and only when the remaining song offers are actually weak. Any single failure
     * returns false, which is what keeps the pivot from ever overriding a Great Success, the song
     * route, an objective, or safety.
     */
    fun techniquePivotAllowed(
        songsLearned: Int?,
        hypeFloorSecured: Boolean,
        eighteenRouteSecuredOrDone: Boolean,
        mandatoryObjectivePending: Boolean,
        turnIsSafe: Boolean,
        remainingSongOffersWeak: Boolean,
    ): Boolean {
        val songs = songsLearned ?: return false
        if (songs < TECHNIQUE_PIVOT_SONG_THRESHOLD.value) return false
        if (!hypeFloorSecured) return false
        if (!eighteenRouteSecuredOrDone) return false
        if (mandatoryObjectivePending) return false
        if (!turnIsSafe) return false
        return remainingSongOffersWeak
    }
}
