package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Grand Concert research verdict as executable policy: total, Android-free and report-only. No function accepts a turn
 * or concert index, so scenario-link story-date gating is structurally impossible here (STORY_DATE_LINK_GATING).
 */

enum class ResearchSource {
    DOCUMENT_1_AUTOMATION_REPORT,

    DOCUMENT_2_OPTIMAL_RUN,
}

enum class Document1Import { SKILL_POINT_PROJECTION, TECHNIQUE_PIVOT }

/** Rejected claims, enumerated so a future engine cannot silently reintroduce one. */
enum class RejectedClaim {
    /** Scenario-link point contribution is active from unlock; story dates describe appearances, not the multiplier. */
    STORY_DATE_LINK_GATING,

    /**
     * Document 1's "datamine-validated" income label was never valid grounds (the exponential form was later confirmed
     * by our own live capture).
     */
    PP_FORMULA_CONFIRMED_LABEL,

    /** Scheduling is one soft anchor, not an unconditional "schedule the best card immediately" rule. */
    ALWAYS_SCHEDULE_IMMEDIATELY,

    /** Future concert/song gains are reserved dynamically, not as a flat stat-target offset. */
    FLAT_STAT_OFFSET,
}

/** Only single-schedule behaviour has been observed on Global. */
enum class MultipleScheduleBehavior { SINGLE_TARGET_CONFIRMED, MULTIPLE_TARGET_UNKNOWN }

/** How firmly the current concert's Great Success is pursued; state-based, with no deck-rating breakpoint. */
enum class GreatSuccessPosture { SECURED, HARD, SOFT, ABANDONED, UNKNOWN }

/**
 * A performance-point income model, a pure function of the four readable inputs. Still open: the link term (+2L, never
 * exercised at L>0) and the rule turning on-screen support portraits into [c].
 *
 * @param s training-class base (9 for Speed/Stamina/Power/Guts, 5 for Wit)
 * @param c counting support cards on the facility
 */
sealed interface PointIncomeModel {
    val label: String
    val provenance: Provenance
    val source: ResearchSource

    fun estimate(s: Int, f: Int, c: Int, l: Int): Int
}

/**
 * Exponential support term (live Global capture: Power C1=+11, Speed C2=+13, Guts C3=+15, Wit C3=+9); the +2L link term
 * is only confirmed for L=0.
 */
object ExponentialSupportModel : PointIncomeModel {
    override val label = "floor((S+F) * 1.15^C + 2L)"
    override val provenance = Provenance.GLOBAL_CONFIRMED
    override val source = ResearchSource.DOCUMENT_1_AUTOMATION_REPORT

    override fun estimate(s: Int, f: Int, c: Int, l: Int): Int =
        floor((s + f) * 1.15.pow(c.coerceAtLeast(0)) + 2.0 * l.coerceAtLeast(0)).toInt()
}

/**
 * Linear form, falsified by the live capture (it cannot produce +15 at three supports); kept for the plausibility band
 * only.
 */
object LinearSupportModel : PointIncomeModel {
    override val label = "floor((S+F) * (1 + 0.15C + 0.20L))"
    override val provenance = Provenance.INFERRED
    override val source = ResearchSource.DOCUMENT_2_OPTIMAL_RUN

    override fun estimate(s: Int, f: Int, c: Int, l: Int): Int =
        floor((s + f) * (1.0 + 0.15 * c.coerceAtLeast(0) + 0.20 * l.coerceAtLeast(0))).toInt()
}

/**
 * A read preview is authoritative; without one [value] is null and [uncertain] true, so a formula-only number never
 * becomes an actionable gain.
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

data class SongRouteFeasibility(
    val feasible: Boolean,
    val maxFutureSongs: Int,
    val target: Int,
    val reason: String?,
)

object GrandConcertResearchVerdict {
    val actionable: Boolean get() = false

    // --- Source hierarchy ---

    val SPECIFICATION_OF_RECORD = ResearchSource.DOCUMENT_2_OPTIMAL_RUN

    val DOCUMENT_1_IMPORTS: Set<Document1Import> =
        setOf(Document1Import.SKILL_POINT_PROJECTION, Document1Import.TECHNIQUE_PIVOT)

    val REJECTED_CLAIMS: Set<RejectedClaim> =
        setOf(
            RejectedClaim.STORY_DATE_LINK_GATING,
            RejectedClaim.PP_FORMULA_CONFIRMED_LABEL,
            RejectedClaim.ALWAYS_SCHEDULE_IMMEDIATELY,
            RejectedClaim.FLAT_STAT_OFFSET,
        )

    val POINT_INCOME_MODELS: List<PointIncomeModel> = listOf(ExponentialSupportModel, LinearSupportModel)

    val CONFIRMED_INCOME_MODEL: PointIncomeModel = ExponentialSupportModel

    val FALSIFIED_INCOME_MODEL: PointIncomeModel = LinearSupportModel

    const val INCOME_MODEL_BASIS =
        "live Global 2026-07-23: Power C1=+11, Speed C2=+13, Guts C3=+15, Wit(base5) C3=+9; " +
            "+15 is impossible under the linear floor at base-9 Lvl-1"

    /** The link term (+2L) was never exercised (L=0 in every captured frame): still inferred. */
    const val INCOME_LINK_TERM_CONFIRMED = false

    /** The rule mapping on-screen support portraits to the count C is unresolved (Stamina anomaly). */
    const val INCOME_COUNTING_RULE_RESOLVED = false

    // --- Document 1 imports (GUIDE provenance, projection only) ---

    /**
     * Document 1 / GameTora projection, not verified balance arithmetic; must not drive an irreversible choice until a
     * live Global concert result proves it.
     */
    val SKILL_POINTS_PER_LEARNED_SONG = Sourced(25, Provenance.GUIDE)

    val SKILL_POINTS_PER_LEARNED_TECHNIQUE = Sourced(5, Provenance.GUIDE)

    const val SKILL_POINT_PROJECTION_VERIFIED = false

    /**
     * Song count at which weak remaining offers may justify pivoting to technique lessons; never overrides a higher
     * constraint.
     */
    val TECHNIQUE_PIVOT_SONG_THRESHOLD = Sourced(21, Provenance.GUIDE)

    fun projectedSkillPointsFromLessons(songsLearned: Int, techniquesLearned: Int): Int =
        songsLearned.coerceAtLeast(0) * SKILL_POINTS_PER_LEARNED_SONG.value +
            techniquesLearned.coerceAtLeast(0) * SKILL_POINTS_PER_LEARNED_TECHNIQUE.value

    // --- Performance-point income: models are inferred, preview is authoritative ---

    fun baseYield(facility: StatName): Int = if (facility == StatName.WIT) 5 else 9

    /** True where the two models give different integers (they agree at 0..2 supports and first diverge at 3). */
    fun modelsDiverge(s: Int, f: Int, c: Int, l: Int): Boolean =
        ExponentialSupportModel.estimate(s, f, c, l) != LinearSupportModel.estimate(s, f, c, l)

    /**
     * A non-null [preview] is authoritative even when it contradicts the formulas; a null preview yields only the model
     * band and an uncertainty flag.
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

    /**
     * Scenario-link point contribution counts from unlock; no function here takes a turn or concert index, so a date
     * gate cannot be expressed.
     */
    const val STORY_DATE_LINK_GATING = false

    val LINK_CONTRIBUTION_ACTIVE_FROM = "performance-point unlock (not story-date gated)"

    /**
     * Light Hello's Training Together grants +20 of the lowest point type only on a detected trigger with a post-turn
     * balance readback; counted as zero until then.
     */
    const val LIGHT_HELLO_LOWEST_TYPE_BONUS = 20

    /** Guide-reported trigger rate; the JP datamine measured lower. Never used to pre-credit points. */
    val LIGHT_HELLO_TRIGGER_RATE = Sourced(0.45, Provenance.COMMUNITY_MODEL)

    fun lightHelloLowestTypeBonus(triggerDetected: Boolean): Int =
        if (triggerDetected) LIGHT_HELLO_LOWEST_TYPE_BONUS else 0

    // --- Great Success: gauge-first, feasibility-aware ---

    const val NORMAL_SUCCESS_GAIN = 3

    const val GREAT_SUCCESS_GAIN = 10

    const val GREAT_SUCCESS_MARGINAL_GAIN = GREAT_SUCCESS_GAIN - NORMAL_SUCCESS_GAIN

    /** GIRLS' LEGEND U Mastery Bonus: +10 to all stats when granted. */
    const val GIRLS_LEGEND_U_MASTERY_GAIN = 10

    /** Stat value at and above which training/concert gains are halved. */
    const val SOFT_CAP_THRESHOLD = 1200

    /**
     * Fallback song increments for a full Hype gauge when the gauge cannot be read; GLOBAL_CONFIRMED from the Global
     * master database (great_success_songs = 3 for all five concerts).
     */
    val HYPE_GAUGE_SONG_INCREMENTS = Sourced(3, Provenance.GLOBAL_CONFIRMED)

    /**
     * Gauge-first: from the observed gauge when readable, never inferred from total career song count; null when
     * unread.
     */
    fun neededSongIncrements(observedHypeIncrements: Int?): Int? =
        observedHypeIncrements?.let { maxOf(HYPE_GAUGE_SONG_INCREMENTS.value - it, 0) }

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
     * Effective stat increase with the 1200 soft cap (points at or above it count half) and the hard [cap] (count
     * zero).
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

    /** Value of a Great over a normal Success this concert: +7 to all five stats, each through [transformedGain]. */
    fun marginalGreatSuccessValue(currentStats: Map<StatName, Int>, caps: Map<StatName, Int> = emptyMap()): Int =
        StatName.entries.sumOf { transformedGain(currentStats[it] ?: 0, GREAT_SUCCESS_MARGINAL_GAIN, capFor(it, caps)) }

    /**
     * Expected raw stat gain still to come: per remaining concert 3 + 7 x P(Great Success), plus 10 while GIRLS' LEGEND
     * U Mastery is expected. [pGreatSuccess] is clamped to [0,1].
     */
    fun expectedFutureRawGain(remainingConcerts: Int, pGreatSuccess: Double, girlsLegendUExpected: Boolean): Double {
        val perConcert = NORMAL_SUCCESS_GAIN + GREAT_SUCCESS_MARGINAL_GAIN * pGreatSuccess.coerceIn(0.0, 1.0)
        val concerts = remainingConcerts.coerceAtLeast(0) * perConcert
        val glu = if (girlsLegendUExpected) GIRLS_LEGEND_U_MASTERY_GAIN else 0
        return concerts + glu
    }

    /**
     * Amount to subtract from a stat's final training target because future concerts and the special song add it later;
     * replaces any flat offset and is soft-capped near 1200.
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

    val SPECIAL_SONG_TARGET = GrandConcertPolicy.SPECIAL_SONG_TARGET

    /**
     * Whether the 18-song route is still reachable; a soft target that becomes a hard constraint only while feasible,
     * and it can turn infeasible mid-run.
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
     * Pivoting to techniques for Skill Points is allowed only at or above the 21-song threshold with every higher
     * constraint satisfied (Hype floor, 18-song route, no mandatory objective, safe turn, weak offers).
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
