package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import kotlin.math.roundToInt

/**
 * The Grand Concert decision engine and the manual-handoff boundary. The engine is a reporter, not a driver: decisions are never
 * `actionable` and nothing here taps, scrolls or spends (the Lesson shop has not been captured on Global). Its constraints are
 * about what the bot must refuse to claim, not about squeezing the optimum.
 */
object GrandConcertPolicy {
    /** Songs to learn before a concert for the Hype gauge to reach Great Success. Global master database:
     * single_mode_live_live_data.great_success_songs = 3 for all five concerts. */
    val GREAT_SUCCESS_SONG_FLOOR = Sourced(3, Provenance.GLOBAL_CONFIRMED)

    val LINK_EVENT_SONG_TARGET = Sourced(16, Provenance.COMMUNITY_MODEL)

    /** Community song count for the special-song route. Kept COMMUNITY_MODEL: Global gates the special song at a TOTAL setlist of 20
     * ([GrandConcertScenario.GRAND_CONCERT_SONG_THRESHOLD]) and whether that equals 18 learned plus automatic songs is unconfirmed; do not raise this to 20. */
    val SPECIAL_SONG_TARGET = Sourced(18, Provenance.COMMUNITY_MODEL)

    /**
     * Refusal is the default: a slot is recommended only when the offers are fully readable, every balance is known and exactly one card
     * is provably affordable-and-best; a confident wrong answer costs performance points that cannot be refunded.
     */
    fun recommend(state: GrandConcertRunState): GrandConcertDecision {
        val missing = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val satisfied = mutableListOf<String>()
        val atRisk = mutableListOf<String>()

        if (state.lessonUnlocked == false) {
            return GrandConcertDecision(
                recommendedSlot = null,
                certain = false,
                reasons = listOf("the Lesson system has not unlocked yet on this career"),
                evidence = GrandConcertEvidence(notes = listOf("scenario button reads locked")),
            )
        }

        if (!state.balances.complete) {
            state.balances.unknownTypes.forEach { missing.add("performance balance for ${it.displayName}") }
        }
        if (state.offers.cards.size != 3) {
            missing.add("all three lesson cards (read ${state.offers.cards.size})")
        }
        state.offers.cards.filterNot { it.readable }.forEach { missing.add("readable card in slot ${it.slot}") }

        // Constraint: never claim affordability against an unknown cost or an unknown balance.
        val affordability = state.offers.cards.associate { it.slot to it.cost.affordableWith(state.balances) }
        affordability.filterValues { it == null }.keys.forEach { missing.add("affordability for slot $it") }

        if (missing.isNotEmpty()) {
            return GrandConcertDecision(
                recommendedSlot = null,
                certain = false,
                reasons = listOf("not every input could be read, so no purchase can be recommended safely"),
                constraintsSatisfied = listOf("no card recommended while evidence is incomplete"),
                constraintsAtRisk = songTargetRisks(state),
                evidence = GrandConcertEvidence(missing = missing.distinct(), notes = notes),
            )
        }

        val affordable = state.offers.cards.filter { affordability[it.slot] == true }
        if (affordable.isEmpty()) {
            return GrandConcertDecision(
                recommendedSlot = null,
                certain = true,
                reasons = listOf("no offered lesson is affordable with the current performance points"),
                constraintsSatisfied = listOf("no unaffordable purchase attempted"),
                constraintsAtRisk = songTargetRisks(state),
                evidence = GrandConcertEvidence(notes = notes),
            )
        }

        val scored = affordable.map { it to score(it, state) }.sortedByDescending { it.second }
        val best = scored.first()
        val tied = scored.count { it.second == best.second } > 1

        satisfied.add("every recommended card was fully readable")
        satisfied.add("affordability proven against known balances")
        if (state.songsLearned != null) satisfied.add("song progress known (${state.songsLearned})")

        val reasons = mutableListOf<String>()
        reasons.add(describe(best.first))
        if (tied) reasons.add("another offer scored equally, so this is not a confident pick")

        return GrandConcertDecision(
            recommendedSlot = if (tied) null else best.first.slot,
            certain = !tied,
            reasons = reasons,
            constraintsSatisfied = satisfied,
            constraintsAtRisk = songTargetRisks(state),
            evidence = GrandConcertEvidence(notes = notes),
        )
    }

    private fun songTargetRisks(state: GrandConcertRunState): List<String> {
        val songs = state.songsLearned ?: return listOf("song progress unknown, so concert targets cannot be checked")
        val risks = mutableListOf<String>()
        if (state.segment != ConcertSegment.UNKNOWN && songs < GREAT_SUCCESS_SONG_FLOOR.value) {
            risks.add("fewer than ${GREAT_SUCCESS_SONG_FLOOR.value} songs learned ${state.segment.displayName}")
        }
        if (state.segment == ConcertSegment.BEFORE_GRAND && songs < LINK_EVENT_SONG_TARGET.value) {
            risks.add("below the ${LINK_EVENT_SONG_TARGET.value}-song scenario-link target")
        }
        if (state.segment == ConcertSegment.BEFORE_GRAND && songs < SPECIAL_SONG_TARGET.value) {
            risks.add("below the ${SPECIAL_SONG_TARGET.value}-song special-song target")
        }
        return risks
    }

    /** Scores one card through the shared strategy scorer; [GrandConcertRunState] has no per-cycle song count, so the deadline term stays off on this path. */
    private fun score(card: LessonCard, state: GrandConcertRunState): Int =
        scoreLesson(
            kind = card.kind,
            title = card.name,
            masteryText = card.songEffect?.masteryText,
            concertText = card.concertBonus?.text,
            effectText = card.techniqueEffect?.text,
            cost = PerformancePointVector(card.cost.amounts),
            context =
                LessonScoreContext(
                    songsLearnedTotal = state.songsLearned,
                    turnsUntilConcert = state.turnsUntilNextConcert,
                    segment = state.segment,
                ),
        )

    private fun describe(card: LessonCard): String {
        val what = card.name ?: "slot ${card.slot}"
        return when (card.kind) {
            LessonCardKind.SONG -> "\"$what\" is a song: it raises the Hype gauge toward Great Success and its Mastery Bonus applies immediately"
            LessonCardKind.TECHNIQUE -> "\"$what\" is a concert technique with an immediate effect"
            LessonCardKind.UNKNOWN -> "slot ${card.slot} could not be classified"
        }
    }

    /**
     * Report-only evidence lines for a training-turn choice; recommends no facility. Reads the observed per-turn performance type from the
     * preview, never the static facility prior (one turn must not teach a permanent facility-to-type mapping), and attributes only the
     * previewed stat gains to the training, reporting any post-turn surplus as a separate event line.
     */
    fun describeTrainingEvidence(preview: GrandConcertTrainingPreview, state: GrandConcertScenarioState): List<String> {
        val lines = mutableListOf<String>()
        preview.failureChance?.let { lines.add("failure risk $it%") }
        preview.visibleParticipants?.let { lines.add("$it visible support participant(s) (portrait count only; no bond or effect inferred)") }
        val gains = preview.statGains.entries.sortedBy { it.key.ordinal }.joinToString(", ") { "${it.key.name} +${it.value}" }
        if (gains.isNotEmpty()) lines.add("previewed stat gains: $gains")
        preview.skillPointGain?.let { lines.add("skill points +$it") }
        val perf = preview.performanceGains.entries.joinToString(", ") { "${it.key.displayName} +${it.value}" }
        if (perf.isNotEmpty()) {
            val observed = preview.observedPerformanceType
            val staticNote =
                if (observed != null && preview.performanceTypeOverridesStatic) {
                    " (observed this turn; the ${preview.facility.name} facility's static prior is ${GrandConcertFacilityModel.staticPrimaryType(preview.facility).displayName}, so the on-screen icon governs)"
                } else {
                    ""
                }
            lines.add("performance points $perf$staticNote")
            preview.performanceGains.forEach { (type, amount) ->
                val remaining = state.scheduledRemaining(type)
                if (remaining != null && remaining > 0) {
                    val after = maxOf(remaining - amount, 0)
                    lines.add("contributes $amount toward the scheduled ${type.displayName} deficit ($remaining -> $after)")
                }
            }
        }
        state.turnsUntilConcert?.let { lines.add("$it turn(s) until the next concert") }
        return lines
    }

    /**
     * Report-only ranking of a live lesson offer: nothing actionable, and an unreadable cost or ambiguous card identity abstains rather than
     * guesses. A SCHEDULED card is never treated as learned and never counts toward the concert song target.
     */
    fun describeLessonOffer(list: LessonList, songsLearned: Int?, hypeTier: HypeTier, turnsUntilConcert: Int?): GrandConcertLessonReport =
        describeLessonOffer(list, hypeTier, LessonScoreContext(songsLearnedTotal = songsLearned, turnsUntilConcert = turnsUntilConcert))

    fun describeLessonOffer(list: LessonList, hypeTier: HypeTier, context: LessonScoreContext): GrandConcertLessonReport {
        val notes = mutableListOf<String>()
        val missing = mutableListOf<String>()

        if (!list.balances.fullyKnown) missing.add("one or more performance balances")
        list.cards.forEachIndexed { i, c ->
            if (!c.readable) missing.add("card ${i + 1} (title/kind/cost)")
        }

        val ranked =
            list.cards.filter { it.readable }.map { card ->
                val affordable = card.cost.affordableWith(list.balances)
                if (affordable == null) missing.add("affordability for \"${card.title}\"")
                LessonOfferLine(
                    slot = card.slot,
                    title = card.title ?: "slot ${card.slot}",
                    kind = card.kind,
                    affordable = affordable,
                    scheduled = card.scheduled == true,
                    score = lessonScore(card, context),
                    weightedCost = weightedCost(card.cost),
                    rawCostTotal = card.cost.total(),
                    rawCost = card.cost,
                )
            }.sortedByDescending { it.score }

        // The live shop always governs; a catalog disagreement is telemetry for improving the strategy weights.
        list.cards.filter { it.readable && it.kind == LessonCardKind.SONG }.forEach { card ->
            val cat = GrandConcertSongCatalog.match(card.title)
            when {
                cat == null -> notes.add("no catalog match for \"${card.title}\"; scoring fell back to the on-card text")
                card.cost.fullyKnown && cat.cost != card.cost -> notes.add("live cost for \"${cat.title}\" differs from the catalog; the live cost governs")
            }
        }

        val effectiveSongs = context.songsLearnedTotal
        if (effectiveSongs != null) {
            notes.add("songs LEARNED so far: $effectiveSongs (scheduled songs are not counted)")
            if (context.turnsUntilConcert != null && effectiveSongs < GREAT_SUCCESS_SONG_FLOOR.value) {
                notes.add("below the ${GREAT_SUCCESS_SONG_FLOOR.value}-song Great Success floor for the next concert")
            }
        } else {
            notes.add("learned-song count unknown, so concert targets cannot be checked")
        }
        notes.add("current hype tier: ${hypeTier.label}")

        return GrandConcertLessonReport(
            ranked = ranked,
            missingEvidence = missing.distinct(),
            notes = notes,
        )
    }

    private fun lessonScore(card: LessonListCard, context: LessonScoreContext): Int =
        scoreLesson(
            kind = card.kind,
            title = card.title,
            masteryText = card.masteryText,
            concertText = card.concertText,
            // On the list card the same line carries a song's mastery text or a technique's effect.
            effectText = card.masteryText,
            cost = card.cost,
            context = context,
        )

    // ---- Strategy scoring -----------------------------------------------------------------
    // Every number in this block is a tunable hypothesis for run telemetry to correct; game facts live in GrandConcertSongCatalog and the live shop.

    private val MASTERY_WEIGHTS: Map<MasteryBonusType, Double> =
        mapOf(
            MasteryBonusType.FREE_ALL to 1000.0,
            MasteryBonusType.SKILL_POINT_TRAINING_3 to 190.0,
            MasteryBonusType.SKILL_POINT_TRAINING_2 to 160.0,
            MasteryBonusType.SPEED_TRAINING_2 to 150.0,
            MasteryBonusType.WIT_TRAINING_2 to 145.0,
            MasteryBonusType.SPEED_TRAINING_1 to 115.0,
            MasteryBonusType.WIT_TRAINING_1 to 110.0,
            MasteryBonusType.POWER_TRAINING_2 to 105.0,
            MasteryBonusType.POWER_TRAINING_1 to 80.0,
            MasteryBonusType.IMMEDIATE_SKILL_POINTS to 90.0,
            MasteryBonusType.IMMEDIATE_SPEED_26 to 85.0,
            MasteryBonusType.IMMEDIATE_SPEED_22 to 75.0,
            MasteryBonusType.IMMEDIATE_WIT_22 to 72.0,
            MasteryBonusType.IMMEDIATE_POWER_22 to 68.0,
            MasteryBonusType.STAMINA_TRAINING_2 to 45.0,
            MasteryBonusType.IMMEDIATE_STAMINA_22 to 40.0,
            MasteryBonusType.IMMEDIATE_GUTS_26 to 38.0,
            MasteryBonusType.GUTS_TRAINING_2 to 35.0,
            MasteryBonusType.IMMEDIATE_GUTS_22 to 32.0,
            MasteryBonusType.STAMINA_TRAINING_1 to 30.0,
            MasteryBonusType.GUTS_TRAINING_1 to 25.0,
        )

    /**
     * Point scarcity on the standard Speed/Wit deck. Vocal is scarce on supply: it is only Power's primary or Stamina's secondary token, which
     * that deck does not train (first-token share stays 3.33% against ~14% demand). The game repairs it (a friendship training's second token and
     * Light Hello top up the lowest type), so Vocal was lowered 1.50 -> 1.30 for a deck running Light Hello; raise it back toward 1.5 without her.
     * A prior to be replaced by measured shadow prices.
     */
    private val SCARCITY: Map<PerformancePointType, Double> =
        mapOf(
            PerformancePointType.DANCE to 0.85,
            PerformancePointType.PASSION to 1.10,
            PerformancePointType.VOCAL to 1.30,
            PerformancePointType.VISUAL to 1.00,
            PerformancePointType.COMPOSURE to 0.85,
        )

    /** A concert bonus queued right before the Grand covers only the epilogue, so it is worth a fifth of face value. */
    private const val PRE_GRAND_QUEUED_CONCERT_MULTIPLIER = 0.20

    /** Residual weight of a per-training mastery bonus once the career is complete; nonzero because songs still apply on the Complete Career screen. */
    private const val CAREER_COMPLETE_COMPOUNDING_MULTIPLIER = 0.1

    /** The spend loop's default stop line; the end-of-career drain lowers it to 1 because expiring points have no opportunity cost. */
    const val SPEND_MIN_SCORE = 25
    const val SPEND_MIN_SCORE_CAREER_COMPLETE = 1

    /** Small on purpose: prefers the cheaper of two equal cards (cheap buys more re-rolls) without pricing a positive-value card out of a budget about to expire. */
    private const val CAREER_END_COST_WEIGHT = 0.02

    /**
     * The technique reserve, in raw points across all five types: while a future concert remains, a technique may not drop the total balance below
     * it (a career once entered its Grand finale with zero new songs after buying techniques and starting the last cycle broke). Sized from master.mdb's
     * lesson-cost table (single_mode_live_square, square_type 4 = songs: median 44, p75 63, max 68). Songs are never reserve-blocked, gate advances are
     * exempt, and the career-end drain never activates it.
     */
    const val TECH_RESERVE_TOTAL = 70

    /** Purchased-song targets per concert cycle (index = concerts already performed): [GREAT_SUCCESS_SONG_FLOOR] secures each gauge; the community
     * 3-4-4-3-3 cadence (17 purchased, 18 with the free "Make Debut!") unlocks the 16-song lyric event and the 18-song special finale. Extras above
     * the floor are conditional in [chooseSongFirst] so they never starve the next cycle's floor. */
    val PURCHASED_SONG_TARGETS = listOf(3, 4, 4, 3, 3)

    fun songTargetForCycle(concertsPassed: Int): Int = PURCHASED_SONG_TARGETS.getOrElse(concertsPassed) { GREAT_SUCCESS_SONG_FLOOR.value }

    /**
     * Picks the purchase the spend loop should make from a ranked offer report, or null to stop. Only a provably affordable, unscheduled card at or
     * above [minScore] qualifies. With [reserveActive], techniques are reserve-checked and songs never. When the next song's cost vector is known the
     * check is type-aware: a technique may not spend a type below what that song still needs, since the total-only rule fails (a cycle held 101 total
     * points with 69 of them Vocal and could afford neither the song nor any gate technique). Without vectors, total balance minus the technique's cost
     * must stay at or above [TECH_RESERVE_TOTAL]; unreadable costs fail toward the reserve.
     */
    fun chooseSpend(
        report: GrandConcertLessonReport,
        minScore: Int = SPEND_MIN_SCORE,
        totalBalance: Int? = null,
        reserveActive: Boolean = false,
        songTargetCost: PerformancePointVector? = null,
        balances: PerformancePointVector? = null,
    ): LessonOfferLine? =
        report.ranked.firstOrNull { line ->
            line.affordable == true && !line.scheduled && line.score >= minScore &&
                !(reserveActive && line.kind == LessonCardKind.TECHNIQUE && reserveBlocksTechnique(line, totalBalance, songTargetCost, balances))
        }

    private fun reserveBlocksTechnique(
        line: LessonOfferLine,
        totalBalance: Int?,
        songTargetCost: PerformancePointVector?,
        balances: PerformancePointVector?,
    ): Boolean {
        if (songTargetCost != null && balances != null) {
            val cost = line.rawCost ?: return true // unreadable cost fails toward the reserve.
            for (type in PerformancePointType.entries) {
                val needed = songTargetCost[type] ?: continue
                if (needed <= 0) continue
                val balance = balances[type] ?: return true // unreadable balance: do not risk it.
                val spend = cost[type] ?: return true
                if (spend > 0 && balance - spend < needed) return true
            }
            return false
        }
        if (totalBalance == null) return false
        return line.rawCostTotal == null || totalBalance - line.rawCostTotal < TECH_RESERVE_TOTAL
    }

    /**
     * While the cycle is below [GREAT_SUCCESS_SONG_FLOOR], an offered, provably affordable, unscheduled song is always worth buying, score
     * notwithstanding (about two of five concerts missed the three-song condition while affordable songs sat below the score floor). Between the
     * floor and [cycleTarget] the extra song needs a readable balance and cost and must provably leave [TECH_RESERVE_TOTAL] in the pool. At or above
     * the target, null.
     */
    fun chooseSongFirst(
        report: GrandConcertLessonReport,
        songsLearnedThisCycle: Int?,
        cycleTarget: Int = GREAT_SUCCESS_SONG_FLOOR.value,
        totalBalance: Int? = null,
    ): LessonOfferLine? {
        if (songsLearnedThisCycle == null) return null
        val song = report.ranked.firstOrNull { it.kind == LessonCardKind.SONG && it.affordable == true && !it.scheduled } ?: return null
        if (songsLearnedThisCycle < GREAT_SUCCESS_SONG_FLOOR.value) return song
        if (songsLearnedThisCycle >= cycleTarget) return null
        if (totalBalance == null || song.rawCostTotal == null) return null
        return if (totalBalance - song.rawCostTotal >= TECH_RESERVE_TOTAL) song else null
    }

    /** Cap on a gate-advance purchase's weighted cost; it doubles under concert-deadline pressure because a stalled gate near the concert is worse
     * than an overpriced technique (a cycle once ended at two songs because every gate technique cost more than the calm cap). */
    const val GATE_ADVANCE_MAX_WEIGHTED_COST = 20.0
    const val GATE_ADVANCE_MAX_WEIGHTED_COST_URGENT = 40.0

    /**
     * Technique-gate fallback: when the trio offers no song and nothing clears [minScore], the cheapest sane technique beats stalling, because the
     * deterministic technique-then-song gate cannot advance without purchases. Techniques only, provably affordable, positive score, weighted cost
     * within the cap for the current urgency.
     *
     * The defer guard must judge lines as [chooseSpend] does: a line the type-aware reserve blocks was not going to be bought, or the reserve and this
     * guard deadlock (the reserve refuses, this defers to it, nothing is bought, the cycle ends songless). The pick itself stays reserve-exempt:
     * minimum-cost gate movement is what un-stalls songs.
     */
    fun chooseGateAdvance(
        report: GrandConcertLessonReport,
        minScore: Int = SPEND_MIN_SCORE,
        urgent: Boolean = false,
        totalBalance: Int? = null,
        reserveActive: Boolean = false,
        songTargetCost: PerformancePointVector? = null,
        balances: PerformancePointVector? = null,
    ): LessonOfferLine? {
        if (report.ranked.any { it.kind == LessonCardKind.SONG }) return null
        val clearsUnblocked =
            report.ranked.any { line ->
                line.affordable == true && !line.scheduled && line.score >= minScore &&
                    !(reserveActive && line.kind == LessonCardKind.TECHNIQUE && reserveBlocksTechnique(line, totalBalance, songTargetCost, balances))
            }
        if (clearsUnblocked) return null
        val cap = if (urgent) GATE_ADVANCE_MAX_WEIGHTED_COST_URGENT else GATE_ADVANCE_MAX_WEIGHTED_COST
        return report.ranked
            .filter {
                it.kind == LessonCardKind.TECHNIQUE && it.affordable == true && !it.scheduled &&
                    it.score >= 1 && it.weightedCost <= cap
            }
            .minByOrNull { it.weightedCost }
    }

    /** Pre-Grand, immediate mastery bonuses gain relative value because compounding runway is gone. */
    private const val PRE_GRAND_IMMEDIATE_MASTERY_MULTIPLIER = 1.25

    /** Energy at or below this percent means a Rest is plausibly next, which an energy technique prevents; above [MODERATE_ENERGY_PERCENT] recovery is mostly overheal. */
    private const val LOW_ENERGY_PERCENT = 45
    private const val MODERATE_ENERGY_PERCENT = 70

    /** Career turns left after the NEXT concert, per segment: concerts land every 12 turns from turn 24 and the career runs to roughly turn 78. */
    private fun postActivationTurns(segment: ConcertSegment): Int =
        when (segment) {
            ConcertSegment.BEFORE_PROMO_1 -> 54
            ConcertSegment.BEFORE_PROMO_2 -> 42
            ConcertSegment.BEFORE_PROMO_3 -> 30
            ConcertSegment.BEFORE_PROMO_4 -> 18
            ConcertSegment.BEFORE_GRAND -> 6
            ConcertSegment.UNKNOWN -> 30
        }

    /** Bond proxy for Friendship concert bonuses: rainbow trainings barely exist before bonds form. */
    private fun rainbowRate(segment: ConcertSegment): Double =
        when (segment) {
            ConcertSegment.BEFORE_PROMO_1 -> 0.25
            ConcertSegment.BEFORE_PROMO_2 -> 0.5
            ConcertSegment.BEFORE_PROMO_3 -> 0.8
            ConcertSegment.BEFORE_PROMO_4 -> 1.0
            ConcertSegment.BEFORE_GRAND -> 1.0
            ConcertSegment.UNKNOWN -> 0.6
        }

    /**
     * Per-post-activation-turn value of each concert-bonus family, ranked Friendship > Specialty > Support Chain. Friendship is the only queued bonus
     * that scales every rainbow's full stat and Skill Point output, so it is the yardstick at face percentage. Specialty Priority +5 is an additive
     * placement weight (four such songs move a card's chance on its specialty facility by about 1-3 points). Support Chain: a JP data-miner measured
     * the baseline trigger rate at 32.43% +/- 1.87% over 2400 turns and Level 3 at 31.50% +/- 4.45%, under +1 point per level, so it is token-nonzero
     * while chains can complete and zero once they cannot.
     */
    private fun concertPerTurn(type: ConcertBonusType?, segment: ConcertSegment): Double =
        when (type) {
            ConcertBonusType.FRIENDSHIP_10 -> 10.0
            ConcertBonusType.FRIENDSHIP_5 -> 5.0
            ConcertBonusType.SPECIALTY_PRIORITY_5 -> 1.5
            ConcertBonusType.SUPPORT_CHAIN_1 ->
                when (segment) {
                    ConcertSegment.BEFORE_PROMO_1 -> 0.5
                    ConcertSegment.BEFORE_PROMO_2 -> 0.3
                    else -> 0.0
                }
            ConcertBonusType.NONE, null -> 0.0
        }

    private fun compoundingMultiplier(segment: ConcertSegment): Double =
        when (segment) {
            ConcertSegment.BEFORE_PROMO_4 -> 0.9
            ConcertSegment.BEFORE_GRAND -> 0.6
            else -> 1.0
        }

    /** Scarcity-weighted total cost; unreadable components are skipped (readability is gated upstream). With [flat] the scarcity multipliers are
     * dropped: on a completed career the points expire, and Vocal at 1.5x once made the drain refuse an affordable Power technique while 227 Dance rotted. */
    private fun weightedCost(cost: PerformancePointVector, flat: Boolean = false): Double =
        PerformancePointType.entries.sumOf { type ->
            val amount = cost[type] ?: 0
            if (amount > 0) amount * (if (flat) 1.0 else SCARCITY[type] ?: 1.0) else 0.0
        }

    /**
     * The strategy scorer shared by both report paths. Songs are valued by bonus type, resolved catalog-first (titles read reliably, the small bonus
     * text often does not) with the on-card text as fallback. A negative song score is meaningful: buying nothing is better, which the spend loop's
     * stop rule consumes.
     */
    internal fun scoreLesson(
        kind: LessonCardKind,
        title: String?,
        masteryText: String?,
        concertText: String?,
        effectText: String?,
        cost: PerformancePointVector,
        context: LessonScoreContext,
    ): Int =
        when (kind) {
            LessonCardKind.SONG -> scoreSong(title, masteryText, concertText, cost, context)
            LessonCardKind.TECHNIQUE -> scoreTechnique(title, effectText, cost, context)
            LessonCardKind.UNKNOWN -> 0
        }

    private fun scoreSong(title: String?, masteryText: String?, concertText: String?, cost: PerformancePointVector, ctx: LessonScoreContext): Int {
        val catalog = GrandConcertSongCatalog.match(title)
        val preGrand = ctx.segment == ConcertSegment.BEFORE_GRAND

        val masteryType = catalog?.mastery ?: fallbackMasteryType(masteryText)
        var mastery = masteryType?.let { MASTERY_WEIGHTS[it] } ?: 0.0
        if (masteryType?.compounding == true) {
            // Career complete: only the residual on-learn value remains.
            mastery *= if (ctx.careerComplete) CAREER_COMPLETE_COMPOUNDING_MULTIPLIER else compoundingMultiplier(ctx.segment)
        } else if (preGrand && !ctx.careerComplete) {
            mastery *= PRE_GRAND_IMMEDIATE_MASTERY_MULTIPLIER
        }

        val concertType = catalog?.concert ?: fallbackConcertType(concertText)
        var concert = concertPerTurn(concertType, ctx.segment) * (ctx.turnsAfterNextConcert ?: postActivationTurns(ctx.segment))
        if (concertType == ConcertBonusType.FRIENDSHIP_10 || concertType == ConcertBonusType.FRIENDSHIP_5) {
            concert *= rainbowRate(ctx.segment)
        }
        if (preGrand) concert *= PRE_GRAND_QUEUED_CONCERT_MULTIPLIER
        // A queued bonus can never activate once the final concert is over.
        if (ctx.careerComplete) concert = 0.0

        // Deadline pressure: the per-cycle Great Success floor first, then the 18-song special route once the Grand window is the only one left;
        // moot on a completed career.
        var deadline = 0.0
        if (!ctx.careerComplete) {
            val cycleSongs = ctx.songsLearnedThisCycle
            if (cycleSongs != null && cycleSongs < GREAT_SUCCESS_SONG_FLOOR.value) {
                deadline += 60.0
                val t = ctx.turnsUntilConcert
                if (t != null && t <= 4) deadline += 80.0
                if (t != null && t <= 2) deadline += 80.0
            }
            if (preGrand && (ctx.songsLearnedTotal ?: Int.MAX_VALUE) < SPECIAL_SONG_TARGET.value) {
                deadline += 120.0
            }
        }

        return (mastery + concert + deadline - weightedCost(cost, flat = ctx.careerComplete)).roundToInt()
    }

    private fun scoreTechnique(title: String?, effectText: String?, cost: PerformancePointVector, ctx: LessonScoreContext): Int {
        val read = GrandConcertSongCatalog.parseTechnique(title, effectText, cost)
        val energy = ctx.energyPercent
        val base =
            when (read.kind) {
                GrandConcertSongCatalog.TechniqueEffectKind.ENERGY ->
                    when {
                        ctx.careerComplete -> 2.0
                        energy == null -> 45.0
                        energy <= LOW_ENERGY_PERCENT -> 250.0
                        energy <= MODERATE_ENERGY_PERCENT -> 45.0
                        else -> 8.0
                    }
                GrandConcertSongCatalog.TechniqueEffectKind.SKILL_POINTS -> 60.0
                GrandConcertSongCatalog.TechniqueEffectKind.STAT_PLUS_SKILL_POINTS -> 50.0
                GrandConcertSongCatalog.TechniqueEffectKind.SINGLE_STAT ->
                    singleStatBase(read.stat, read.coreStat, ctx.statPriority) + singleStatMagnitudeAdjustment(read.magnitude)
                GrandConcertSongCatalog.TechniqueEffectKind.TWO_STATS -> 30.0
                // Default off per the research; whitelist support is a later, separate decision.
                GrandConcertSongCatalog.TechniqueEffectKind.SKILL_HINT -> 5.0
                GrandConcertSongCatalog.TechniqueEffectKind.UNKNOWN -> 15.0
            }
        // Technique costs weigh half: they are the cheap gate currency, not the song budget.
        if (!ctx.careerComplete) return (base - weightedCost(cost) * 0.5).roundToInt()

        // At career end the points are destroyed at Finish, so charging for them is backwards: half-cost pricing refused two affordable cards (Energy +20
        // scored 2.0 - 12.5 = -10) and 213 points expired, and declining one card freezes the shop's deterministic re-roll gate, forfeiting every stat
        // technique behind it. A small cost weight remains (a cheap card leaves budget for more re-rolls) and the floor keeps any positive-value card
        // purchasable; a 40-base stat technique still outranks a 2-base energy one.
        return (base - weightedCost(cost, flat = true) * CAREER_END_COST_WEIGHT)
            .roundToInt()
            .coerceAtLeast(SPEND_MIN_SCORE_CAREER_COMPLETE)
    }

    /** Score for the run's highest-priority stat; the lowest scores [SINGLE_STAT_LOW_PRIORITY_SCORE]. The endpoints are the scenario's old fixed 40 (Speed/Wit/Power) and 22 (Stamina/Guts). */
    private const val SINGLE_STAT_HIGH_PRIORITY_SCORE = 40.0
    private const val SINGLE_STAT_LOW_PRIORITY_SCORE = 22.0

    /**
     * Values a single-stat technique by where its stat sits in the run's priority order, spaced evenly between the old high and low scores, so a
     * Stamina- or Guts-focused build is not structurally demoted and the default order reproduces the old core-beats-secondary result. A stat missing
     * from a partial list is lowest priority. Falls back to the coarse core/secondary read when the exact stat could not be identified.
     */
    private fun singleStatBase(stat: StatName?, coreStat: Boolean?, priority: List<StatName>): Double {
        if (stat == null) return if (coreStat == true) SINGLE_STAT_HIGH_PRIORITY_SCORE else SINGLE_STAT_LOW_PRIORITY_SCORE
        val order = priority.ifEmpty { StatName.entries }
        val rank = order.indexOf(stat).let { if (it >= 0) it else order.lastIndex }
        val span = (order.size - 1).coerceAtLeast(1)
        val t = rank.toDouble() / span
        return SINGLE_STAT_HIGH_PRIORITY_SCORE + t * (SINGLE_STAT_LOW_PRIORITY_SCORE - SINGLE_STAT_HIGH_PRIORITY_SCORE)
    }

    /** The smallest single-stat technique tier (+5, e.g. Dance Step Basics) and the zero point for [singleStatMagnitudeAdjustment]: at or below it, or
     * with an unreadable magnitude, the score is unchanged, so an OCR-uncertain read is treated as baseline rather than penalized. */
    private const val SINGLE_STAT_MAGNITUDE_BASELINE = 5

    /** The largest single-stat technique tier (Advanced Class, +12). OCR reads an unbounded "+N", so a garbled read (a stray digit turning +5 into +52)
     * is clamped to a magnitude the game actually offers. */
    private const val SINGLE_STAT_MAGNITUDE_CEILING = 12

    /**
     * Per point of magnitude above [SINGLE_STAT_MAGNITUDE_BASELINE], on top of [singleStatBase]. Set just above the highest [SCARCITY] weight (Vocal, 1.30):
     * tier costs scale with magnitude (10/16/24 points for +5/+8/+12), so the half-weighted cost term already charges magnitude * scarcity per point, and
     * a larger weight guarantees two techniques of the same stat rank by magnitude (Speed +5/cost 10 once outscored Speed +12/cost 24, 36 vs 30).
     * Small against the priority spread (18): a preferred stat's smallest tier still outranks an unwanted stat's largest, and an outsized cost still
     * loses to a cheaper technique, so the scarcity and reserve rules stay authoritative.
     */
    private const val SINGLE_STAT_MAGNITUDE_WEIGHT = 1.5

    /** Zero at or below the baseline or when the magnitude is unreadable, reproducing the pre-magnitude score; clamped to [SINGLE_STAT_MAGNITUDE_CEILING]
     * so an off-ladder OCR read cannot outscore the largest real tier. */
    private fun singleStatMagnitudeAdjustment(magnitude: Int?): Double =
        ((magnitude ?: SINGLE_STAT_MAGNITUDE_BASELINE).coerceAtMost(SINGLE_STAT_MAGNITUDE_CEILING) - SINGLE_STAT_MAGNITUDE_BASELINE)
            .coerceAtLeast(0)
            .toDouble() * SINGLE_STAT_MAGNITUDE_WEIGHT

    /** Handles both observed formats ("Training Wit Gain +1", "Skill Pts +22", "Speed +22"). Never returns FREE_ALL: those songs are identified by
     * title, and a garbled line must not claim their weight. */
    internal fun fallbackMasteryType(masteryText: String?): MasteryBonusType? {
        val t = masteryText?.lowercase()?.trim().orEmpty()
        if (t.isEmpty()) return null
        val magnitude = Regex("""\+\s*(\d+)""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        fun hasWord(w: String) = Regex("""\b$w\b""").containsMatchIn(t)
        val skillPoints = t.contains("skill pt") || t.contains("skill point")
        val training = t.contains("training") || t.contains("gain")
        return when {
            training && skillPoints -> if (magnitude == 3) MasteryBonusType.SKILL_POINT_TRAINING_3 else MasteryBonusType.SKILL_POINT_TRAINING_2
            training && hasWord("speed") -> if (magnitude != null && magnitude >= 2) MasteryBonusType.SPEED_TRAINING_2 else MasteryBonusType.SPEED_TRAINING_1
            training && hasWord("wit") -> if (magnitude != null && magnitude >= 2) MasteryBonusType.WIT_TRAINING_2 else MasteryBonusType.WIT_TRAINING_1
            training && hasWord("power") -> if (magnitude != null && magnitude >= 2) MasteryBonusType.POWER_TRAINING_2 else MasteryBonusType.POWER_TRAINING_1
            training && hasWord("stamina") -> if (magnitude != null && magnitude >= 2) MasteryBonusType.STAMINA_TRAINING_2 else MasteryBonusType.STAMINA_TRAINING_1
            training && hasWord("guts") -> if (magnitude != null && magnitude >= 2) MasteryBonusType.GUTS_TRAINING_2 else MasteryBonusType.GUTS_TRAINING_1
            skillPoints -> MasteryBonusType.IMMEDIATE_SKILL_POINTS
            hasWord("speed") -> if (magnitude != null && magnitude >= 26) MasteryBonusType.IMMEDIATE_SPEED_26 else MasteryBonusType.IMMEDIATE_SPEED_22
            hasWord("wit") -> MasteryBonusType.IMMEDIATE_WIT_22
            hasWord("power") -> MasteryBonusType.IMMEDIATE_POWER_22
            hasWord("stamina") -> MasteryBonusType.IMMEDIATE_STAMINA_22
            hasWord("guts") -> if (magnitude != null && magnitude >= 26) MasteryBonusType.IMMEDIATE_GUTS_26 else MasteryBonusType.IMMEDIATE_GUTS_22
            else -> null
        }
    }

    internal fun fallbackConcertType(concertText: String?): ConcertBonusType? {
        val t = concertText?.lowercase()?.trim().orEmpty()
        return when {
            t.isEmpty() -> null
            t.contains("none") -> ConcertBonusType.NONE
            t.contains("friend") -> if (t.contains("10")) ConcertBonusType.FRIENDSHIP_10 else ConcertBonusType.FRIENDSHIP_5
            t.contains("special") -> ConcertBonusType.SPECIALTY_PRIORITY_5
            t.contains("chain") || t.contains("frequen") -> ConcertBonusType.SUPPORT_CHAIN_1
            else -> null
        }
    }
}

/**
 * Scoring context for a lesson offer; an unknown input degrades its term to a conservative default instead of blocking ranking.
 * [songsLearnedThisCycle] counts new songs since the last concert (the Great Success floor is per-cycle); [songsLearnedTotal] includes Make Debut!.
 * [turnsAfterNextConcert] is the exact post-activation runway when known, else the segment estimate is used.
 */
data class LessonScoreContext(
    val songsLearnedTotal: Int? = null,
    val songsLearnedThisCycle: Int? = null,
    /** The cycle's purchased-song target from [GrandConcertPolicy.songTargetForCycle], or null when the turn context is unknown. */
    val cycleSongTarget: Int? = null,
    val turnsUntilConcert: Int? = null,
    val turnsAfterNextConcert: Int? = null,
    val segment: ConcertSegment = ConcertSegment.UNKNOWN,
    val energyPercent: Int? = null,
    /** True on the Complete Career screen's final drain: no training turns or concerts remain, so bonuses are residual. */
    val careerComplete: Boolean = false,
    /** The run's own stat priority ([Training.statPrioritization]); defaults to declaration order (Speed, Stamina, Power, Guts, Wit), but callers should pass the run's resolved list. */
    val statPriority: List<StatName> = StatName.entries,
)

/** One ranked offer line; [affordable] is null when undetermined. [weightedCost] is carried so gate-advance can prefer the cheapest option
 * without re-deriving costs. */
data class LessonOfferLine(
    val slot: Int,
    val title: String,
    val kind: LessonCardKind,
    val affordable: Boolean?,
    val scheduled: Boolean,
    val score: Int,
    val weightedCost: Double = 0.0,
    /** Raw per-type cost sum, unweighted, for the technique reserve; null when any cost cell was unreadable. */
    val rawCostTotal: Int? = null,
    /** The full per-type cost vector for the type-aware reserve: a wallet can satisfy the total reserve while broke in exactly the types the next song needs. */
    val rawCost: PerformancePointVector? = null,
)

data class GrandConcertLessonReport(
    val ranked: List<LessonOfferLine>,
    val missingEvidence: List<String>,
    val notes: List<String>,
) {
    val actionable: Boolean get() = false

    val fullyReadable: Boolean get() = missingEvidence.isEmpty() && ranked.all { it.affordable != null }
}

/**
 * Why the bot stopped on a Grand Concert screen it cannot drive. A generic Confirm on an unknown scenario screen can spend points, skip a concert
 * or dismiss a choice, and the unknown-screen ladder's last resort is a game relaunch, which is wrong for a live screen the bot has not learned yet.
 */
enum class GrandConcertHandoffReason(val playerText: String) {
    UNRECOGNIZED_SCENARIO_SCREEN("Grand Concert needs manual input on this screen."),
    CONCERT_NOT_AUTOMATED("Concert screens are not automated yet."),
    QUICK_MODE_UNCONFIGURED("Quick Mode has not been configured in UMA Auto+."),
    QUICK_MODE_UNREADABLE("The Quick Mode dialog could not be read reliably."),
}

/** A typed stop that preserves the career. [gameIsAlive] is true by definition, so recovery must never restart the game; keeps the queue's failure accounting honest. */
data class GrandConcertHandoff(
    val reason: GrandConcertHandoffReason,
    val screenNote: String? = null,
    val evidenceScreenshot: String? = null,
) {
    val gameIsAlive: Boolean get() = true

    /** A handoff must never trigger [Game.restartGame]. */
    val permitsGameRelaunch: Boolean get() = false

    /** A handoff must not tap anything on its way out. */
    val permitsGenericClick: Boolean get() = false

    val preservesCareer: Boolean get() = true

    /** Message shown to the player; the second sentence tells them the run is recoverable and how. */
    fun playerMessage(): String =
        "${reason.playerText} The career is preserved. Handle it in-game, return to the Career screen, " +
            "then press Start to resume." + (screenNote?.let { " ($it)" } ?: "")
}

/**
 * There is deliberately no default choice: the options change how much of the game the player sees, a preference the bot cannot invent, and
 * picking silently is an irreversible per-career decision. Unconfigured, the bot hands off before the career starts, which costs no TP.
 */
sealed class QuickModeAction {
    data class Select(val rowIndex: Int) : QuickModeAction()

    object ConfirmOnly : QuickModeAction()

    data class HandOff(val handoff: GrandConcertHandoff) : QuickModeAction()
}

object QuickModePlanner {
    fun plan(configuredWire: String?, selectedIndex: Int?): QuickModeAction {
        val configured =
            com.steve1316.uma_android_automation.utils.QuickModeOption.fromWire(configuredWire)
                ?: return QuickModeAction.HandOff(
                    GrandConcertHandoff(
                        GrandConcertHandoffReason.QUICK_MODE_UNCONFIGURED,
                        "choose a Quick Mode option in UMA Auto+ before starting a Grand Concert career",
                    ),
                )
        if (selectedIndex == null) {
            return QuickModeAction.HandOff(
                GrandConcertHandoff(
                    GrandConcertHandoffReason.QUICK_MODE_UNREADABLE,
                    "the dialog did not read as exactly one selected option",
                ),
            )
        }
        return if (selectedIndex == configured.rowIndex) QuickModeAction.ConfirmOnly else QuickModeAction.Select(configured.rowIndex)
    }
}
