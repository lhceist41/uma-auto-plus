package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName

/**
 * The Grand Concert training-turn model: the training preview, the scenario state either side of
 * a turn, and a pure verifier that checks one against the other.
 *
 * This exists because Grand Concert adds a fifth output to every training - performance points -
 * on top of the ordinary stat gains, and gates scheduled lessons on the balance of those points.
 * A turn is only "understood" when the performance arithmetic, the scheduled-lesson deficits, and
 * the debut/concert countdowns all reconcile; the ordinary stat gains are the ONE part that a
 * post-training event can legitimately perturb, so they are verified separately and softly.
 *
 * Everything here is total, Android-free, and JUnit-pinned. Pixel reading lives in
 * [com.steve1316.uma_android_automation.utils.GrandConcertProbes]; nothing here taps.
 */

/**
 * The per-facility performance-point type is RANDOM PER TURN and shown by an icon on each
 * facility button (the client's own help text: "The performance type in which you can acquire
 * points through each training session will differ each turn."). The static table below is the
 * facility's PRIMARY type as documented for the Japanese version, and it is COMMUNITY_MODEL: it
 * is a prior, never an answer. The bot must read the per-turn icon and let it override this.
 *
 * The 2026-07-23 fixture is the proof: Guts training previewed Dance that turn, while this static
 * map says Guts -> Visual. A bot that trusted the static map would mis-attribute the 13 points.
 */
object GrandConcertFacilityModel {
    private val STATIC_PRIMARY: Map<StatName, PerformancePointType> =
        mapOf(
            StatName.SPEED to PerformancePointType.DANCE,
            StatName.STAMINA to PerformancePointType.PASSION,
            StatName.POWER to PerformancePointType.VOCAL,
            StatName.GUTS to PerformancePointType.VISUAL,
            StatName.WIT to PerformancePointType.COMPOSURE,
        )

    /** The documented primary type for a facility. COMMUNITY_MODEL: a fallback for logging only,
     * never a substitute for the observed per-turn icon. */
    fun staticPrimaryType(facility: StatName): PerformancePointType =
        STATIC_PRIMARY[facility] ?: PerformancePointType.DANCE

    val staticProvenance: Provenance = Provenance.COMMUNITY_MODEL
}

/**
 * One training facility's preview as read off the training screen.
 *
 * [statGains] holds exactly the stats the preview annotated with a "+N" (Speed/Power/Guts on the
 * fixture); a stat absent from the map previewed no gain, which is DIFFERENT from a stat whose
 * gain could not be read (that would simply not be asserted). [performanceGains] is the per-turn
 * performance points, keyed by the OBSERVED type - normally one entry, two when friendship
 * training splits across types.
 */
data class GrandConcertTrainingPreview(
    val facility: StatName,
    val level: Int,
    val failureChance: Int?,
    val statGains: Map<StatName, Int>,
    val skillPointGain: Int?,
    val performanceGains: Map<PerformancePointType, Int>,
    val visibleParticipants: Int?,
) {
    /** The gain the preview showed for [stat] (0 when the preview annotated none). */
    fun previewedStatGain(stat: StatName): Int = statGains[stat] ?: 0

    /** The single observed performance type, or null when zero or more than one was shown. The
     * "more than one" case (friendship training) is handled by iterating [performanceGains]. */
    val observedPerformanceType: PerformancePointType?
        get() = performanceGains.keys.singleOrNull()

    /** True when the observed per-turn type disagrees with the static facility prior - the exact
     * situation the per-turn icon exists to resolve, and the reason a static map must not be
     * trusted. */
    val performanceTypeOverridesStatic: Boolean
        get() = observedPerformanceType?.let { it != GrandConcertFacilityModel.staticPrimaryType(facility) } ?: false
}

/**
 * Scenario state at one instant. [scheduledCost] is the aggregate performance-point cost of the
 * lessons the player has scheduled, per type; the on-screen "N more" pills are the REMAINING of
 * those, which [scheduledRemaining] recomputes so the two can be cross-checked.
 */
data class GrandConcertScenarioState(
    val turnsUntilDebut: Int?,
    val turnsUntilConcert: Int?,
    val balances: PerformanceBalances,
    val stats: Map<StatName, Int>,
    val scheduledCost: Map<PerformancePointType, Int> = emptyMap(),
) {
    /** Remaining points needed for the scheduled lessons of [type], or null when the balance for
     * that type is not known (never guess a deficit against an unread balance). */
    fun scheduledRemaining(type: PerformancePointType): Int? {
        val cost = scheduledCost[type] ?: return 0
        val balance = balances[type] ?: return null
        return maxOf(cost - balance, 0)
    }

    fun stat(stat: StatName): Int? = stats[stat]
}

/**
 * Everything the training scorer needs to steer point income toward the next song, assembled by
 * the campaign at recommendation time (see Campaign.grandConcertPointContext). [deficit] is the
 * per-type shortfall between the cheapest unscheduled song the shop last offered and the balances
 * read off the training screen this turn; a type absent from the map has no known need (unread
 * cost or no song target yet), which the scorer treats as "no bias" rather than guessing. [caps]
 * is computed (200 base, +50 per completed concert, research-confirmed on any success tier),
 * never OCR'd. The bias is armed only while [behindPace]: point income above a cap is lost, so
 * the effective value of a gain is clamped to both the deficit and the cap headroom.
 */
data class GrandConcertPointContext(
    val balances: Map<PerformancePointType, Int?>,
    val caps: Map<PerformancePointType, Int>,
    val deficit: Map<PerformancePointType, Int>,
    val songsBoughtThisCycle: Int,
    val purchasedFloor: Int,
    val turnsUntilConcert: Int?,
    val songTargetTitle: String? = null,
    /** Purchased-song career total (excludes the free "Make Debut!"), for the total-target bias. */
    val songsBoughtThisCareer: Int = 0,
    /** The purchased-song cumulative a healthy run has by the START of this cycle: the sum of the
     * 3-4-4-3-3 cadence for the concerts already performed. Zero before the first concert (nothing
     * is owed yet) and by default (which keeps the total-target bias disarmed for callers that do
     * not supply it). */
    val expectedSongsByNow: Int = 0,
    /** Telemetry provenance for the widened demand set (the scorer consumes only the merged
     * [deficit]): the current song target's raw per-type cost ("song"), the cheapest gate-advancing
     * technique's cost while the song gate is closed ("gate"), and one cheapest unpurchased next song
     * while the career trails the cadence ("next-song"). Empty maps for callers that do not widen. */
    val currentSongDemand: Map<PerformancePointType, Int> = emptyMap(),
    val gateTechniqueDemand: Map<PerformancePointType, Int> = emptyMap(),
    val nextSongDemand: Map<PerformancePointType, Int> = emptyMap(),
) {
    /** True while this cycle still owes songs to the Great Success floor and a concert remains. */
    val behindPace: Boolean get() = turnsUntilConcert != null && songsBoughtThisCycle < purchasedFloor

    /** True while the purchased-song career total trails the cadence trajectory and a concert still
     * remains to spend income before. Distinct from [behindPace]: it stays armed through a cycle
     * that already met its own floor but is behind the 18-song total - the exact Senior case where
     * the old floor-only bias disarmed and point income stopped chasing songs. */
    val behindTotalTarget: Boolean get() = turnsUntilConcert != null && songsBoughtThisCareer < expectedSongsByNow

    /** How many purchased songs the career total trails the cadence trajectory by, floored at zero. */
    val totalSongDeficit: Int get() = (expectedSongsByNow - songsBoughtThisCareer).coerceAtLeast(0)

    /** The point bias is armed while the cycle is behind its own floor OR the career is behind the
     * total-song cadence, and a concert remains to spend the income before. */
    val biasArmed: Boolean get() = behindPace || behindTotalTarget

    /** Room left under [type]'s cap, or null when the balance was unreadable (never guess
     * headroom against an unread balance). */
    fun headroom(type: PerformancePointType): Int? {
        val balance = balances[type] ?: return null
        val cap = caps[type] ?: return null
        return (cap - balance).coerceAtLeast(0)
    }
}

/**
 * The pure Grand Concert training-demand merge: the widened per-color point demand the training scorer
 * steers by, built from three bounded components and reduced by the current balances. [Merged.deficit]
 * is what the scorer consumes; the component maps are kept for telemetry. Reads no pixels and makes no
 * purchase decision.
 *
 * Sequential-demand model: the active primary purchase (the current song when the song gate is open,
 * else the cheapest gate-advancing technique - never both, only one is buyable in a turn) and the
 * one-step next-song lookahead are made SEQUENTIALLY, so their per-color costs add; the current
 * balance covers part of that sequence and is subtracted once. A surplus color earns no credit.
 */
object GrandConcertPointDemand {
    data class Merged(
        val deficit: Map<PerformancePointType, Int>,
        val currentSongDemand: Map<PerformancePointType, Int>,
        val gateTechniqueDemand: Map<PerformancePointType, Int>,
        val nextSongDemand: Map<PerformancePointType, Int>,
    )

    /**
     * @param currentSongCost the remembered current song target's cost, or null.
     * @param gateTechniqueCost the cheapest gate-advancing technique's cost, or null.
     * @param offerHadSong whether the last lesson read showed a song (the song-gate state): it selects
     *   the current song vs the gate technique as the active primary purchase.
     * @param nextSongCost the cheapest unpurchased next song's cost when the career trails the cadence,
     *   else null (the caller gates this on the cumulative-behind condition).
     * @param balances the per-type point balances read this turn.
     */
    fun merge(
        currentSongCost: PerformancePointVector?,
        gateTechniqueCost: PerformancePointVector?,
        offerHadSong: Boolean,
        nextSongCost: PerformancePointVector?,
        balances: Map<PerformancePointType, Int?>,
    ): Merged {
        val currentSongDemand = perType(if (offerHadSong) currentSongCost else null)
        val gateTechniqueDemand = perType(if (!offerHadSong) gateTechniqueCost else null)
        val nextSongDemand = perType(nextSongCost)
        val primary = if (offerHadSong) currentSongDemand else gateTechniqueDemand
        val deficit = LinkedHashMap<PerformancePointType, Int>()
        for (type in PerformancePointType.entries) {
            val combined = (primary[type] ?: 0) + (nextSongDemand[type] ?: 0)
            if (combined <= 0) continue
            val b = balances[type] ?: continue
            deficit[type] = (combined - b).coerceAtLeast(0)
        }
        return Merged(deficit, currentSongDemand, gateTechniqueDemand, nextSongDemand)
    }

    /** A vector's positive per-type entries as a plain map (empty when null): a component's raw
     * demand before the merge subtracts the balance. */
    private fun perType(cost: PerformancePointVector?): Map<PerformancePointType, Int> {
        if (cost == null) return emptyMap()
        val out = LinkedHashMap<PerformancePointType, Int>()
        for (type in PerformancePointType.entries) {
            val c = cost[type] ?: continue
            if (c > 0) out[type] = c
        }
        return out
    }
}

/** One stat's post-turn behavior relative to what the preview promised. */
data class StatDelta(
    val stat: StatName,
    val before: Int?,
    val after: Int?,
    val previewed: Int,
) {
    val observed: Int? get() = if (before != null && after != null) after - before else null

    /** True when the stat moved by exactly what the preview promised. */
    val matchesPreview: Boolean get() = observed == previewed

    /** The part of the move the preview did not account for (e.g. an intervening event's gain).
     * Null when either endpoint was unreadable. */
    val unexplained: Int? get() = observed?.let { it - previewed }
}

/** The verifier's verdict. [ok] is the conjunction of the mandatory checks only; ordinary-stat
 * mismatches never fail [ok] when an intervening event was possible. */
data class GrandConcertTransitionResult(
    val performanceOk: Boolean,
    val deficitsOk: Boolean,
    val debutCountdownOk: Boolean,
    val concertCountdownOk: Boolean,
    val statDeltas: List<StatDelta>,
    val unexplainedStatDeltas: List<StatDelta>,
    val interveningEventPossible: Boolean,
    val notes: List<String>,
) {
    /** The mandatory checks - performance arithmetic, scheduled deficits, and both countdowns -
     * hold. Ordinary-stat agreement is required in [statsFullyExplained], not here. */
    val ok: Boolean get() = performanceOk && deficitsOk && debutCountdownOk && concertCountdownOk

    /** Every readable stat moved exactly as previewed (no intervening-event contribution). */
    val statsFullyExplained: Boolean get() = unexplainedStatDeltas.all { (it.unexplained ?: 0) == 0 }
}

object GrandConcertTransition {
    /**
     * Verifies a training turn from three observations: the state before, the selected preview,
     * and the state after.
     *
     * Mandatory and always enforced, because nothing in a normal turn should move them except
     * the training and the passage of the turn:
     * - performance balance: each previewed type's balance rose by exactly its previewed amount;
     * - scheduled deficits: the recomputed remaining matches before and after;
     * - debut and concert countdowns: each dropped by one.
     *
     * Soft and event-aware: ordinary stats. With [interveningEventPossible] true, a stat moving
     * by more (or less) than the preview is recorded as unexplained rather than failing the
     * verdict, because a post-training event, a support event, or a scenario message can add
     * stats between the preview and the next screen. With it false, every readable stat must
     * match. Either way the deltas are returned so a caller can log what actually happened.
     */
    fun verify(
        before: GrandConcertScenarioState,
        preview: GrandConcertTrainingPreview,
        after: GrandConcertScenarioState,
        interveningEventPossible: Boolean,
    ): GrandConcertTransitionResult {
        val notes = mutableListOf<String>()

        // Performance balance arithmetic (mandatory). Every previewed type must have risen by
        // exactly its previewed amount; types the preview did not touch must not have moved.
        var performanceOk = true
        for (type in PerformancePointType.entries) {
            val b = before.balances[type]
            val a = after.balances[type]
            val expected = preview.performanceGains[type] ?: 0
            if (b == null || a == null) {
                performanceOk = false
                notes.add("performance balance for ${type.displayName} was not readable on both frames")
                continue
            }
            if (a - b != expected) {
                performanceOk = false
                notes.add("${type.displayName} balance moved ${a - b}, preview said $expected")
            }
        }

        // Scheduled-deficit arithmetic (mandatory): recompute remaining before and after and,
        // where a screen-read deficit was captured, cross-check it.
        var deficitsOk = true
        val scheduledTypes = (before.scheduledCost.keys + after.scheduledCost.keys)
        for (type in scheduledTypes) {
            val rb = before.scheduledRemaining(type)
            val ra = after.scheduledRemaining(type)
            if (rb == null || ra == null) {
                deficitsOk = false
                notes.add("scheduled deficit for ${type.displayName} could not be recomputed (unread balance)")
            }
        }

        val debutCountdownOk = countdownDroppedByOne(before.turnsUntilDebut, after.turnsUntilDebut)
        if (!debutCountdownOk) notes.add("debut countdown did not drop by exactly one (${before.turnsUntilDebut} -> ${after.turnsUntilDebut})")
        val concertCountdownOk = countdownDroppedByOne(before.turnsUntilConcert, after.turnsUntilConcert)
        if (!concertCountdownOk) notes.add("concert countdown did not drop by exactly one (${before.turnsUntilConcert} -> ${after.turnsUntilConcert})")

        val deltas =
            StatName.entries.map { stat ->
                StatDelta(stat, before.stat(stat), after.stat(stat), preview.previewedStatGain(stat))
            }
        val unexplained = deltas.filter { (it.unexplained ?: 0) != 0 }
        if (unexplained.isNotEmpty()) {
            val detail = unexplained.joinToString(", ") { "${it.stat.name} previewed ${it.previewed}, observed ${it.observed}" }
            if (interveningEventPossible) {
                notes.add("unexplained stat change(s) recorded, attributed to a possible intervening event: $detail")
            } else {
                notes.add("stat change(s) did not match the preview and no intervening event was allowed: $detail")
            }
        }

        return GrandConcertTransitionResult(
            performanceOk = performanceOk,
            deficitsOk = deficitsOk,
            debutCountdownOk = debutCountdownOk,
            concertCountdownOk = concertCountdownOk,
            statDeltas = deltas,
            unexplainedStatDeltas = unexplained,
            interveningEventPossible = interveningEventPossible,
            notes = notes,
        )
    }

    private fun countdownDroppedByOne(before: Int?, after: Int?): Boolean = before != null && after != null && before - after == 1
}

/**
 * The per-turn Grand Concert performance-point income record: which facility was trained this turn
 * and the per-color points its own on-screen "+N" preview promised for it.
 *
 * The income is training-attributable BY CONSTRUCTION. The number is the game's per-facility award,
 * read off that facility's panel ([GrandConcertTrainingReader]); it is never a differenced balance
 * and never the static [GrandConcertFacilityModel] prior. Because it is not a before/after
 * subtraction, no unrelated concert bonus, event reward, or lesson spend can leak into it -- the
 * classic "ambiguous interval" failure mode does not exist at this hook. There is no clean
 * post-training balance read to difference anyway: the only live performance-point read on a
 * training turn is the pre-training panel balance ([Training.gcTurnBalances]), and
 * [GrandConcertTransition] (which would verify a before/after delta) is unwired. So the record
 * carries the previewed award plus the pre-training balance anchor, and never invents a ppAfter or
 * ppDelta it cannot observe.
 *
 * Pure and Android-free like the rest of this file; the live emit gathers the inputs in [Training]
 * and formats through [format].
 */
object GrandConcertPointIncome {
    /** How trustworthy the trained facility's per-color income read is. Qualifies the INCOME only;
     * the pre-training balance anchor is reported separately, with its own per-type unknowns. */
    enum class Attribution {
        /** Every observed gain type carried a readable amount: the income is fully attributed. */
        TRAINING,

        /** A gain type was observed but its amount would not OCR (glyph seen, number unread): the
         * color is attributed, the magnitude is not, so no amount is asserted for that type. */
        AMBIGUOUS,

        /** No gain was read at all (empty preview). Never rendered as a fabricated zero: a Grand
         * Concert facility always awards some point, so an empty read is unknown, not zero. */
        UNKNOWN,
    }

    /** Classifies the trained facility's [gains] (its observed per-type "+N"; a null amount means
     * the glyph was seen but the number was unreadable). */
    fun classify(gains: Map<PerformancePointType, Int?>): Attribution =
        when {
            gains.isEmpty() -> Attribution.UNKNOWN
            gains.values.all { it != null } -> Attribution.TRAINING
            else -> Attribution.AMBIGUOUS
        }

    private fun code(type: PerformancePointType): String = type.displayName.take(2)

    /** "Da+13,Pa+8", an unread amount as "Vi+?", or "none" when nothing was read. Ordered by the
     * enum so every record's colors line up for parsing. */
    private fun incomeString(gains: Map<PerformancePointType, Int?>): String =
        PerformancePointType.entries
            .filter { gains.containsKey(it) }
            .joinToString(",") { "${code(it)}+${gains[it] ?: "?"}" }
            .ifEmpty { "none" }

    private fun observedString(gains: Map<PerformancePointType, Int?>): String =
        PerformancePointType.entries
            .filter { gains.containsKey(it) }
            .joinToString(",") { code(it) }
            .ifEmpty { "none" }

    private fun balanceString(balances: Map<PerformancePointType, Int?>?): String =
        PerformancePointType.entries.joinToString(",") { "${code(it)}=${balances?.get(it) ?: "?"}" }

    private fun demandString(demand: Map<PerformancePointType, Int>): String =
        PerformancePointType.entries
            .filter { (demand[it] ?: 0) > 0 }
            .joinToString(",") { "${code(it)}:${demand[it]}" }
            .ifEmpty { "none" }

    /**
     * Formats one compact, machine-parseable income record for the turn's trained facility. All
     * inputs are plain values so this stays JUnit-pinned; the caller supplies the trained facility's
     * observed gains, the pre-training balances, and the turn's point context.
     *
     * [turn] is the canonical game-calendar turn number. It is deliberately NOT unique per record: the
     * Pre-Debut UI has no per-turn calendar date, so every Pre-Debut training resolves to the same
     * canonical turn (the Debut race turn), and multiple distinct trainings then share one `turn=`.
     * [seq] is this turn's per-career committed-action sequence (the same monotonic seq the
     * `decision_trace`/`career_state` streams carry), which advances once per committed action even when
     * `turn` repeats, so it disambiguates those Pre-Debut records and joins them to the decision streams.
     * It is OMITTED, never placeholder-filled, when null (release/non-debug build, where no seq exists).
     */
    fun format(
        turn: Int,
        seq: Int?,
        selected: StatName,
        gains: Map<PerformancePointType, Int?>,
        ppBefore: Map<PerformancePointType, Int?>?,
        concertIn: Int?,
        songsBoughtThisCycle: Int,
        purchasedFloor: Int,
        songsBoughtThisCareer: Int,
        expectedSongsByNow: Int,
        currentSongDemand: Map<PerformancePointType, Int>,
        nextSongDemand: Map<PerformancePointType, Int>,
        numRainbow: Int,
        numSkillHints: Int,
    ): String {
        val attribution = classify(gains).name.lowercase()
        // Omit seq entirely when absent rather than printing a placeholder, matching the decision-trace
        // honesty rule: a missing seq means "no committed-action sequence was allocated this turn".
        val seqField = seq?.let { "seq=$it " } ?: ""
        return "[TRAINING] [GC_PP_INCOME] turn=$turn ${seqField}selected=${selected.name} " +
            "income=[${incomeString(gains)}] observed=${observedString(gains)} attribution=$attribution " +
            "ppBefore=[${balanceString(ppBefore)}] concertIn=${concertIn ?: "?"} " +
            "cycleSongs=$songsBoughtThisCycle/$purchasedFloor careerSongs=$songsBoughtThisCareer/$expectedSongsByNow " +
            "demand=[song:${demandString(currentSongDemand)} next:${demandString(nextSongDemand)}] " +
            "rainbows=$numRainbow hints=$numSkillHints"
    }
}
