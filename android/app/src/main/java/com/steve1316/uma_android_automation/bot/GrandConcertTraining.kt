package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName

/**
 * Grand Concert training-turn model: the training preview, the scenario state either side of a turn, and a pure
 * verifier. Ordinary stat gains are the one part a post-training event can legitimately perturb, so they are
 * verified separately and softly.
 */

/**
 * The performance-point type per facility is RANDOM PER TURN and shown by an icon on each facility button, so
 * this static table (the Japanese version's primary types, COMMUNITY_MODEL) is a prior, never an answer: the
 * per-turn icon must override it. A fixture showed Guts previewing Dance where this map says Visual.
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

    /** A fallback for logging only, never a substitute for the observed per-turn icon. */
    fun staticPrimaryType(facility: StatName): PerformancePointType =
        STATIC_PRIMARY[facility] ?: PerformancePointType.DANCE

    val staticProvenance: Provenance = Provenance.COMMUNITY_MODEL
}

/**
 * [statGains] holds only the stats the preview annotated with a "+N"; an absent stat previewed no gain, which
 * differs from an unreadable gain. [performanceGains] is keyed by the OBSERVED type: one entry, two when
 * friendship training splits across types.
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
    fun previewedStatGain(stat: StatName): Int = statGains[stat] ?: 0

    /** Null when zero or more than one type was shown (friendship training); iterate [performanceGains] then. */
    val observedPerformanceType: PerformancePointType?
        get() = performanceGains.keys.singleOrNull()

    val performanceTypeOverridesStatic: Boolean
        get() = observedPerformanceType?.let { it != GrandConcertFacilityModel.staticPrimaryType(facility) } ?: false
}

/**
 * [scheduledCost] is the per-type cost of scheduled lessons; the on-screen "N more" pills are the remainder,
 * recomputed by [scheduledRemaining] to cross-check.
 */
data class GrandConcertScenarioState(
    val turnsUntilDebut: Int?,
    val turnsUntilConcert: Int?,
    val balances: PerformanceBalances,
    val stats: Map<StatName, Int>,
    val scheduledCost: Map<PerformancePointType, Int> = emptyMap(),
) {
    /** Null when the type's balance is unread: never guess a deficit against an unread balance. */
    fun scheduledRemaining(type: PerformancePointType): Int? {
        val cost = scheduledCost[type] ?: return 0
        val balance = balances[type] ?: return null
        return maxOf(cost - balance, 0)
    }

    fun stat(stat: StatName): Int? = stats[stat]
}

/**
 * What the training scorer needs to steer point income toward the next song. A type absent from [deficit] has
 * no known need and gets no bias rather than a guess. [caps] is computed (200 base, +50 per completed concert),
 * never OCR'd. Point income above a cap is lost, so a gain's value is clamped to the deficit and cap headroom.
 */
data class GrandConcertPointContext(
    val balances: Map<PerformancePointType, Int?>,
    val caps: Map<PerformancePointType, Int>,
    val deficit: Map<PerformancePointType, Int>,
    val songsBoughtThisCycle: Int,
    val purchasedFloor: Int,
    val turnsUntilConcert: Int?,
    val songTargetTitle: String? = null,
    /** Purchased-song career total (excludes the free "Make Debut!"). */
    val songsBoughtThisCareer: Int = 0,
    /**
     * Purchased songs a healthy run has by the START of this cycle (3-4-4-3-3 cadence); the default zero keeps the
     * total-target bias disarmed.
     */
    val expectedSongsByNow: Int = 0,
    /**
     * Telemetry only (the scorer consumes the merged [deficit]): raw per-type costs of the current song, the cheapest
     * gate-advancing technique while the song gate is closed, and the next song while trailing the cadence.
     */
    val currentSongDemand: Map<PerformancePointType, Int> = emptyMap(),
    val gateTechniqueDemand: Map<PerformancePointType, Int> = emptyMap(),
    val nextSongDemand: Map<PerformancePointType, Int> = emptyMap(),
) {
    val behindPace: Boolean get() = turnsUntilConcert != null && songsBoughtThisCycle < purchasedFloor

    /**
     * Stays armed through a cycle that met its own floor but trails the 18-song total, where a floor-only bias would
     * stop chasing songs.
     */
    val behindTotalTarget: Boolean get() = turnsUntilConcert != null && songsBoughtThisCareer < expectedSongsByNow

    val totalSongDeficit: Int get() = (expectedSongsByNow - songsBoughtThisCareer).coerceAtLeast(0)

    val biasArmed: Boolean get() = behindPace || behindTotalTarget

    /** Null when the balance is unread: never guess headroom. */
    fun headroom(type: PerformancePointType): Int? {
        val balance = balances[type] ?: return null
        val cap = caps[type] ?: return null
        return (cap - balance).coerceAtLeast(0)
    }
}

/**
 * The widened per-color point demand the training scorer steers by ([Merged.deficit]; the component maps are
 * telemetry only). The active primary purchase (the current song when the song gate is open, else the cheapest
 * gate-advancing technique; only one is buyable per turn) and the next-song lookahead happen sequentially, so
 * their per-color costs add and the balance is subtracted once. A surplus color earns no credit.
 */
object GrandConcertPointDemand {
    data class Merged(
        val deficit: Map<PerformancePointType, Int>,
        val currentSongDemand: Map<PerformancePointType, Int>,
        val gateTechniqueDemand: Map<PerformancePointType, Int>,
        val nextSongDemand: Map<PerformancePointType, Int>,
    )

    /**
     * [offerHadSong] (the last lesson read showed a song) selects the current song vs the gate technique as the
     * primary purchase.
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

data class StatDelta(
    val stat: StatName,
    val before: Int?,
    val after: Int?,
    val previewed: Int,
) {
    val observed: Int? get() = if (before != null && after != null) after - before else null

    val matchesPreview: Boolean get() = observed == previewed

    /** E.g. an intervening event's gain; null when either endpoint was unreadable. */
    val unexplained: Int? get() = observed?.let { it - previewed }
}

/**
 * [ok] covers the mandatory checks only; ordinary-stat mismatches never fail it when an intervening event was
 * possible.
 */
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
    val ok: Boolean get() = performanceOk && deficitsOk && debutCountdownOk && concertCountdownOk

    val statsFullyExplained: Boolean get() = unexplainedStatDeltas.all { (it.unexplained ?: 0) == 0 }
}

object GrandConcertTransition {
    /**
     * Mandatory: each previewed type's balance rose by exactly its previewed amount, the recomputed scheduled
     * deficits match, and both countdowns dropped by one. Ordinary stats are soft: with [interveningEventPossible]
     * a mismatch is recorded as unexplained instead of failing, since an event can add stats between the preview
     * and the next screen. Deltas are always returned.
     */
    fun verify(
        before: GrandConcertScenarioState,
        preview: GrandConcertTrainingPreview,
        after: GrandConcertScenarioState,
        interveningEventPossible: Boolean,
    ): GrandConcertTransitionResult {
        val notes = mutableListOf<String>()

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
 * The per-turn performance-point income record: the trained facility and the per-color points its own on-screen
 * "+N" preview promised. Training-attributable by construction (never a differenced balance or the static
 * prior), so no concert bonus, event reward or lesson spend can leak in. No clean post-training balance read
 * exists (only the pre-training panel balance, [Training.gcTurnBalances]), so it never invents a ppAfter or ppDelta.
 */
object GrandConcertPointIncome {
    enum class Attribution {
        TRAINING,

        /** Glyph seen but number unread: the color is attributed, the magnitude is not. */
        AMBIGUOUS,

        /** Empty preview: unknown, never a fabricated zero, since a facility always awards some point. */
        UNKNOWN,
    }

    fun classify(gains: Map<PerformancePointType, Int?>): Attribution =
        when {
            gains.isEmpty() -> Attribution.UNKNOWN
            gains.values.all { it != null } -> Attribution.TRAINING
            else -> Attribution.AMBIGUOUS
        }

    private fun code(type: PerformancePointType): String = type.displayName.take(2)

    /** Ordered by the enum so every record's colors line up for parsing. */
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
     * [turn] is the canonical game-calendar turn and is NOT unique per record: every Pre-Debut training resolves to
     * the Debut race turn (the UI has no per-turn date). [seq] is the per-career committed-action sequence shared
     * with the `decision_trace`/`career_state` streams; it disambiguates those records and is OMITTED, never
     * placeholder-filled, when null (release builds).
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
        val seqField = seq?.let { "seq=$it " } ?: ""
        return "[TRAINING] [GC_PP_INCOME] turn=$turn ${seqField}selected=${selected.name} " +
            "income=[${incomeString(gains)}] observed=${observedString(gains)} attribution=$attribution " +
            "ppBefore=[${balanceString(ppBefore)}] concertIn=${concertIn ?: "?"} " +
            "cycleSongs=$songsBoughtThisCycle/$purchasedFloor careerSongs=$songsBoughtThisCareer/$expectedSongsByNow " +
            "demand=[song:${demandString(currentSongDemand)} next:${demandString(nextSongDemand)}] " +
            "rainbows=$numRainbow hints=$numSkillHints"
    }
}
