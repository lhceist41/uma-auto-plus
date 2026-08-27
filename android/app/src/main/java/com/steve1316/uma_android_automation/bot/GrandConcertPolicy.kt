package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import kotlin.math.roundToInt

/**
 * The Grand Concert decision engine and the manual-handoff boundary.
 *
 * The engine is a REPORTER, not a driver: [GrandConcertPolicy.recommend] returns a decision
 * object whose `actionable` is false by construction, and nothing in this file taps, scrolls, or
 * spends. That is deliberate. The Lesson shop has never been captured on Global, so a purchase
 * policy can be reasoned about and unit-tested long before it may be allowed to touch a career.
 *
 * The hard constraints below are the ones that survive not knowing the shop's exact layout:
 * they are about what the bot must refuse to claim, not about squeezing the optimum.
 */
object GrandConcertPolicy {
    /** Songs that must be learned before a concert for the Hype gauge to reach Great Success.
     * GLOBAL_CONFIRMED: the Global client's own master database states it directly
     * (single_mode_live_live_data.great_success_songs = 3 for all five concerts). The in-client
     * help text describes the gauge without naming a number; JP guides echo the same 3. */
    val GREAT_SUCCESS_SONG_FLOOR = Sourced(3, Provenance.GLOBAL_CONFIRMED)

    /** Song count that unlocks the Senior scenario-link choice event. */
    val LINK_EVENT_SONG_TARGET = Sourced(16, Provenance.COMMUNITY_MODEL)

    /** Community "learned songs" count for the special-song route, used as this engine's reporting
     * target. Deliberately left COMMUNITY_MODEL: the Global master database gates the Grand
     * Concert's special song at a TOTAL setlist size of 20
     * ([GrandConcertScenario.GRAND_CONCERT_SONG_THRESHOLD]), and whether 20 total equals 18 learned
     * plus automatic setlist songs is not yet confirmed on Global. Do not silently raise this to 20:
     * the two numbers may count different things. */
    val SPECIAL_SONG_TARGET = Sourced(18, Provenance.COMMUNITY_MODEL)

    /**
     * Produces a recommendation, or an explicit refusal, for the current shop offer.
     *
     * Refusal is the default: the engine recommends a slot only when the offer set is fully
     * readable, every balance is known, and exactly one card is provably affordable-and-best.
     * Anything less returns a null slot with the missing evidence spelled out, because on this
     * screen a confident wrong answer costs performance points that cannot be refunded.
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

    /** Song-count constraints that are behind schedule, phrased as risks rather than failures
     * because the run may still catch up. */
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

    /**
     * Scores one offered card through the shared strategy scorer, mapping the run state onto the
     * scoring context. [GrandConcertRunState] does not track the per-cycle song count, so the
     * deadline term stays off on this path until the spend loop supplies it.
     */
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
     * Report-only evidence lines for a training-turn choice. This does NOT recommend a facility
     * and, like [recommend], produces nothing actionable - it exists so a supervised run can log
     * what a training turn offers in Grand Concert terms.
     *
     * Two things it must never do, both encoded here rather than left to the caller:
     * - it reads the OBSERVED per-turn performance type from the preview, never the static
     *   facility prior, so a single turn can never teach it a permanent facility-to-type mapping;
     * - it attributes only the previewed stat gains to the training, and reports any post-turn
     *   surplus as a separate intervening-event line rather than folding it into the training's
     *   apparent value.
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
     * Report-only comparison of a live lesson offer against balances and the run's concert
     * progress. Now that real cards, costs, and effects can be read, this ranks the offers - but it
     * still recommends nothing actionable, emits no tap plan, and refuses on any missing evidence.
     * The refusals below are the whole point: an unreadable cost or an ambiguous card identity has
     * to abstain, not guess.
     *
     * Hard constraints encoded here:
     * - an unreadable cost or an unreadable card is non-actionable;
     * - a SCHEDULED card is never treated as learned and never counts toward the concert song
     *   target;
     * - nothing is spent and no tap is emitted.
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

        // Catalog cross-checks: the live shop always governs, but a disagreement (or a title the
        // catalog cannot place) is exactly the telemetry the strategy weights need to improve.
        list.cards.filter { it.readable && it.kind == LessonCardKind.SONG }.forEach { card ->
            val cat = GrandConcertSongCatalog.match(card.title)
            when {
                cat == null -> notes.add("no catalog match for \"${card.title}\"; scoring fell back to the on-card text")
                card.cost.fullyKnown && cat.cost != card.cost -> notes.add("live cost for \"${cat.title}\" differs from the catalog; the live cost governs")
            }
        }

        // Scheduled songs are explicitly not counted toward the concert target.
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
    // Starting weights from the consolidated strategy research (grand-concert-research/
    // STRATEGY_DECIDER_SPEC.md section 1). Every number in this block is a tunable HYPOTHESIS
    // for run telemetry to correct; the game facts (which song carries which bonus, what things
    // cost) live in GrandConcertSongCatalog and the live shop.

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
     * Point scarcity on the standard Speed/Wit deck: Speed pays Dance/Visual and Wit pays
     * Composure/Passion, so Vocal is the bottleneck and Dance/Composure overflow.
     *
     * Vocal is scarce on SUPPLY, not demand. The client's own song table demands the LEAST Vocal of
     * any token (Da 252 / Pa 201 / Vo 150 / Vi 275 / Co 196 across all 21 songs), but Vocal is only
     * ever Power's primary token or Stamina's secondary, and a Speed/Wit deck trains neither: its
     * first-token share stays at 3.33% whatever the Speed-to-Wit split, against a demand share near
     * 14%. Both research passes confirmed Vocal as the genuine bottleneck and both flagged that the
     * game actively repairs it, since a friendship training's second token and Light Hello's event
     * both top up whichever type is currently lowest. Lowered 1.50 -> 1.30 on that basis: the deck
     * now runs Light Hello, whose top-up disproportionately lands on Vocal. Raise it back toward
     * 1.5 for a deck without her, and treat these as a prior to be replaced by measured shadow
     * prices once enough careers have logged which token actually blocked a purchase.
     */
    private val SCARCITY: Map<PerformancePointType, Double> =
        mapOf(
            PerformancePointType.DANCE to 0.85,
            PerformancePointType.PASSION to 1.10,
            PerformancePointType.VOCAL to 1.30,
            PerformancePointType.VISUAL to 1.00,
            PerformancePointType.COMPOSURE to 0.85,
        )

    /** A concert bonus queued right before the Grand activates after the final concert and covers
     * only the epilogue, so it is worth a fifth of face value there. */
    private const val PRE_GRAND_QUEUED_CONCERT_MULTIPLIER = 0.20

    /** Residual weight of a per-training mastery bonus once the career is complete. Not zero: the
     * maintainer confirmed songs still apply on the Complete Career screen, and a nonzero residual
     * keeps a free or trivially cheap training song above doing nothing at all. */
    private const val CAREER_COMPLETE_COMPOUNDING_MULTIPLIER = 0.1

    /** The spend loop's default stop line: below this a purchase is judged worse than carrying the
     * points (junk techniques, stranded-cost songs). The end-of-career drain lowers it to 1,
     * because expiring points have zero opportunity cost. */
    const val SPEND_MIN_SCORE = 25
    const val SPEND_MIN_SCORE_CAREER_COMPLETE = 1

    /** How much a technique's cost counts once the career is over and the points expire. Small on
     * purpose: enough to prefer the cheaper of two equal cards, since cheap buys more re-rolls, but
     * never enough to price a positive-value card out of a budget that is about to be destroyed. */
    private const val CAREER_END_COST_WEIGHT = 0.02

    /**
     * The technique reserve, in raw points summed across all five types. While a future concert
     * remains, a technique purchase may not drop the total balance below this floor: the
     * 2026-07-26 A+ career entered its Grand finale with ZERO new songs because the 48-61 cycle
     * bought four techniques after its own songs and started the last cycle broke. Sized from
     * master.mdb's own lesson-cost table (single_mode_live_square, square_type 4 = songs:
     * median 44, p75 63, max 68 total) as one expensive song plus margin; per-turn point income
     * covers the rest of a cycle's three songs. Songs are never reserve-blocked, gate advances
     * are exempt (they exist to roll the offer toward songs and are capped cheap), and the
     * career-end drain never activates the reserve because expiring points have nothing to be
     * reserved FOR.
     */
    const val TECH_RESERVE_TOTAL = 70

    /** Purchased-song targets per concert cycle (index = concerts already performed). The floor
     * of [GREAT_SUCCESS_SONG_FLOOR] secures every concert's gauge; the raised mid-cycle targets
     * follow the community 3-4-4-3-3 cadence whose sum (17 purchased, 18 total with the free
     * "Make Debut!") unlocks the 16-song lyric event and the 18-song special finale. The extras
     * above the floor are conditional in [chooseSongFirst]: they must provably leave the
     * technique reserve intact, so the milestone chase can never starve the NEXT cycle's floor,
     * the exact failure both strategy reports warn pure greed produces. */
    val PURCHASED_SONG_TARGETS = listOf(3, 4, 4, 3, 3)

    /** The purchased-song target for the cycle entered after [concertsPassed] concerts. */
    fun songTargetForCycle(concertsPassed: Int): Int = PURCHASED_SONG_TARGETS.getOrElse(concertsPassed) { GREAT_SUCCESS_SONG_FLOOR.value }

    /**
     * Picks the purchase the spend loop should make from a ranked offer report, or null to stop
     * buying. Pure and deliberately strict: only a provably affordable, unscheduled card at or
     * above [minScore] qualifies, and the ranking order already encodes the strategy weights.
     *
     * When [reserveActive] is true, TECHNIQUES are reserve-checked; songs never are. With the
     * next song's cost vector known ([songTargetCost] plus [balances]), the check is TYPE-AWARE:
     * a technique may not spend a type below what that song still needs from it, because the
     * total-only rule provably fails - a live cycle held 101 total points (above the 70 floor)
     * with 69 of them Vocal, and both the next song and every gate technique were unaffordable
     * in the types that actually mattered (2026-07-27, two straight two-song cycles). Types the
     * song does not cost stay freely spendable. Without the vectors, the total rule is the
     * fallback: total balance minus the technique's cost must stay at or above
     * [TECH_RESERVE_TOTAL]; unreadable costs fail toward the reserve, never through it.
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

    /** The reserve check for one technique line; see [chooseSpend] for the rule. */
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
     * The song-first pick: while the cycle is below the [GREAT_SUCCESS_SONG_FLOOR], an offered,
     * provably affordable, unscheduled song is ALWAYS worth buying, score notwithstanding. The
     * measured alternative was worse on every 2026-07-26 career: roughly two of five concerts
     * missed the three-song Great Success condition while affordable songs sat below the score
     * floor or behind higher-ranked techniques. Ranking order still decides among multiple songs.
     *
     * Between the floor and [cycleTarget] (the 3-4-4-3-3 milestone cadence), the extra song is
     * conditional: it is bought only when the balance and the song's raw cost are both readable
     * and the purchase provably leaves [TECH_RESERVE_TOTAL] in the pool, so chasing the 17-song
     * total can never starve the next cycle's three-song floor. At or above the target, null:
     * the normal ranking takes over.
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

    /** A gate-advance purchase may cost at most this much weighted-cost: cheap tier-I techniques
     * qualify, expensive ones do not. Under concert-deadline pressure the cap doubles, because a
     * stalled gate near the concert is worse than an overpriced technique: cycle 4 of the first
     * validation career ended at two songs when every gate technique on offer cost more than the
     * calm cap. */
    const val GATE_ADVANCE_MAX_WEIGHTED_COST = 20.0
    const val GATE_ADVANCE_MAX_WEIGHTED_COST_URGENT = 40.0

    /**
     * The technique-gate fallback: when the trio offers NO song and nothing buyable clears
     * [minScore], buying the cheapest sane technique still beats stalling, because the
     * deterministic technique-then-song gate cannot advance without purchases and a stalled gate
     * means no songs before the concert (the first live run proved this failure mode). Bounded
     * hard: techniques only, provably affordable, positive score, weighted cost at most the cap
     * for the current urgency, and only when the normal pick declined.
     *
     * The defer guard must judge lines the way [chooseSpend] does: a line the type-aware reserve
     * blocks was NOT going to be bought, so it cannot count as "something still clears the bar".
     * Without that, the reserve and this guard deadlock each other - the reserve refuses the
     * technique, this guard defers to it, nothing is ever bought, and the cycle ends songless
     * (live 2026-07-27: a whole cycle of "s=36 a=true" stop rules and zero purchases). The pick
     * itself stays reserve-EXEMPT by design: minimum-cost gate movement is what un-stalls the
     * pattern the reserve is protecting songs for.
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

    /** Energy at or below this percent means a Rest is plausibly next, which an energy technique
     * prevents at a fraction of a turn's value. Above [MODERATE_ENERGY_PERCENT] recovery is
     * mostly overheal. */
    private const val LOW_ENERGY_PERCENT = 45
    private const val MODERATE_ENERGY_PERCENT = 70

    /** Career turns remaining after the NEXT concert, per segment: concerts land every 12 turns
     * from turn 24 and the career runs to roughly turn 78. Used when the caller cannot supply the
     * exact runway. */
    private fun postActivationTurns(segment: ConcertSegment): Int =
        when (segment) {
            ConcertSegment.BEFORE_PROMO_1 -> 54
            ConcertSegment.BEFORE_PROMO_2 -> 42
            ConcertSegment.BEFORE_PROMO_3 -> 30
            ConcertSegment.BEFORE_PROMO_4 -> 18
            ConcertSegment.BEFORE_GRAND -> 6
            ConcertSegment.UNKNOWN -> 30
        }

    /** Bond proxy for Friendship concert bonuses: rainbow trainings barely exist before bonds
     * form, so a Friendship bonus bought in Junior year multiplies almost nothing at first. */
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
     * Per-post-activation-turn value of each concert-bonus family.
     *
     * Recalibrated 2026-07-25 against two independent research passes, which agreed on the ranking
     * Friendship > Specialty > Support Chain but put the gaps far wider than these weights did:
     *
     * - **Friendship** is the only queued bonus that scales the full stat and Skill Point output of
     *   every rainbow, so it stays the yardstick at its face percentage.
     * - **Specialty Priority +5** was 3.5, i.e. 70% of a Friendship +5%. It is not remotely that
     *   strong: +5 is an additive placement WEIGHT, and four such songs move a card's chance of
     *   appearing on its specialty facility by roughly 1 to 3 percentage points in total. It buys a
     *   slightly better chance of seeing a rainbow, not a bigger rainbow.
     * - **Support Chain** was 4.0 in the first cycle, nearly matching Friendship. A JP data-miner
     *   measured the baseline support-chain trigger rate at 32.43% +/- 1.87% over 2400 turns and
     *   found Level 3 statistically indistinguishable from it at 31.50% +/- 4.45%, concluding under
     *   +1 percentage point per level. Both reports independently rate it last and confirm that the
     *   first validated career finishing at Level 0 was correct, not a miss. It is now a token
     *   nonzero while chains can still complete, and zero once they cannot.
     *
     * Songs are still scored as a whole bundle, so this only prices the queued tail: the songs that
     * carry Support Chain remain top-tier when their mastery effect is (Grow Up and Shine! and Zero
     * Is Where the Center Stands! are bought for the compounding gain, never for the chain).
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

    /** Training-gain bonuses lose value as the training turns they multiply run out. */
    private fun compoundingMultiplier(segment: ConcertSegment): Double =
        when (segment) {
            ConcertSegment.BEFORE_PROMO_4 -> 0.9
            ConcertSegment.BEFORE_GRAND -> 0.6
            else -> 1.0
        }

    /** Scarcity-weighted total cost. Unreadable components are skipped here because readability
     * is gated upstream; this function stays total so ranking never throws. With [flat] the
     * scarcity multipliers are dropped: on a completed career the points expire, so no type is
     * scarcer than another, and pricing Vocal at 1.5x there made the drain refuse an affordable
     * Power technique while 227 Dance rotted (observed on the first validation career). */
    private fun weightedCost(cost: PerformancePointVector, flat: Boolean = false): Double =
        PerformancePointType.entries.sumOf { type ->
            val amount = cost[type] ?: 0
            if (amount > 0) amount * (if (flat) 1.0 else SCARCITY[type] ?: 1.0) else 0.0
        }

    /**
     * The strategy scorer shared by both report paths. Songs are valued by bonus TYPE, resolved
     * catalog-first (titles read reliably; the small bonus text often does not) with the on-card
     * text as fallback; a queued concert bonus by its post-activation runway with the bond proxy
     * discounting Friendship early; costs by the scarcity weights; and the per-cycle song floor
     * as an additive deadline term. A negative song score is meaningful: it says buying nothing
     * is better, which is what the spend loop's stop rule will consume.
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
            // With the career complete there are no training turns left for a per-training bonus to
            // multiply; only its residual on-learn value remains.
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

        // Deadline pressure: the per-cycle Great Success floor first, then the 18-song special
        // route once the Grand window is the only one left. Under pressure even a low-tier song
        // outranks its own weaknesses, which is the research's song-count tie-break. All moot on a
        // completed career.
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
                        // No training turns remain, so recovered energy has nothing to prevent.
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

        // At career end the points are DESTROYED at Finish, so charging for them is backwards.
        // Half-cost pricing here refused two affordable cards on a live career (Energy +20 scored
        // 2.0 - 12.5 = -10, a Skill Hint +3 scored 5.0 - 15.0 = -10) and 213 points expired. Worse
        // than the cards themselves: the shop re-rolls after every purchase along a deterministic
        // gate, so declining one card freezes the sequence and forfeits every stat technique behind
        // it. A stat technique is worth 30-40 base and feeds rank directly, so stopping early is the
        // expensive move, not buying badly.
        //
        // Cost still carries a small weight rather than none, because a cheap card leaves budget for
        // more re-rolls, and the floor keeps any positive-value card at or above the stop line so it
        // stays purchasable. Ranking is unchanged where it matters: a 40-base stat technique still
        // outranks a 2-base energy one, so the loop buys the best card first and only reaches the
        // dregs when nothing better is affordable.
        return (base - weightedCost(cost, flat = true) * CAREER_END_COST_WEIGHT)
            .roundToInt()
            .coerceAtLeast(SPEND_MIN_SCORE_CAREER_COMPLETE)
    }

    /** The single-stat base score for the run's highest-priority stat; the lowest-priority stat
     * scores [SINGLE_STAT_LOW_PRIORITY_SCORE]. These are the same two endpoints the scenario used
     * before this method existed, when every single-stat technique was scored 40 for Speed/Wit/
     * Power and 22 for Stamina/Guts regardless of what the run was actually built for. */
    private const val SINGLE_STAT_HIGH_PRIORITY_SCORE = 40.0
    private const val SINGLE_STAT_LOW_PRIORITY_SCORE = 22.0

    /**
     * Values a single-stat technique by where its stat sits in the run's own priority order
     * instead of the scenario's old fixed Speed/Wit/Power-over-Stamina/Guts split, so a Stamina-
     * or Guts-focused build is no longer structurally demoted. The five ranks are spaced evenly
     * between the old high and low scores, so reversing two stats in the priority list reverses
     * which one this scores higher, and the untouched default order (Speed/Wit/Power ahead of
     * Stamina/Guts) reproduces the old core-beats-secondary result.
     *
     * A stat missing from a partially-specified priority list is treated as lowest priority: with
     * no stat-target ratio to fall back on (unlike normal Training scoring), a stat the player
     * removed from the list is the closest available signal to "do not care about this."
     *
     * Falls back to the coarse core/secondary read when the technique's exact stat could not be
     * identified (title unmatched, effect text unreadable, cost signature ambiguous) - the same
     * fallback the scenario used everywhere before this change.
     */
    private fun singleStatBase(stat: StatName?, coreStat: Boolean?, priority: List<StatName>): Double {
        if (stat == null) return if (coreStat == true) SINGLE_STAT_HIGH_PRIORITY_SCORE else SINGLE_STAT_LOW_PRIORITY_SCORE
        val order = priority.ifEmpty { StatName.entries }
        val rank = order.indexOf(stat).let { if (it >= 0) it else order.lastIndex }
        val span = (order.size - 1).coerceAtLeast(1)
        val t = rank.toDouble() / span
        return SINGLE_STAT_HIGH_PRIORITY_SCORE + t * (SINGLE_STAT_LOW_PRIORITY_SCORE - SINGLE_STAT_HIGH_PRIORITY_SCORE)
    }

    /** The smallest known single-stat technique tier (Dance Step Basics and its siblings all grant
     * +5) and the zero point for [singleStatMagnitudeAdjustment]. A technique at or below this
     * tier, or one whose magnitude could not be read at all, keeps its exact pre-magnitude score;
     * only a confirmed Intermediate or Advanced card picks up extra value. Zeroing at the smallest
     * tier rather than at zero magnitude means the huge majority of existing scoring - every
     * technique already offered and scored at its most common tier - is untouched by this change,
     * and an OCR-uncertain magnitude is treated as the baseline instead of penalized. */
    private const val SINGLE_STAT_MAGNITUDE_BASELINE = 5

    /** The largest known single-stat technique tier (Advanced Class, +12). OCR reads an unbounded
     * "+N" from free text, so a garbled read (e.g. a stray digit turning +5 into +52) could
     * otherwise inject a magnitude the game never actually offers. Clamping the magnitude itself,
     * not just the adjustment, keeps a bogus reading from scoring any higher than the largest real
     * tier while leaving every genuine +5/+8/+12 read untouched. */
    private const val SINGLE_STAT_MAGNITUDE_CEILING = 12

    /** Per point of a single-stat technique's magnitude above [SINGLE_STAT_MAGNITUDE_BASELINE], on
     * top of [singleStatBase]. Set just above the highest [SCARCITY] weight (Vocal, 1.30): every
     * stat-technique tier doubles its cost with its magnitude (10/16/24 points for +5/+8/+12), so
     * the half-weighted cost term already charges `magnitude * scarcity` for one more point of
     * stat. A weight above the highest scarcity guarantees that charge can never fully offset the
     * credit, so two techniques of the SAME stat always rank by magnitude no matter which token
     * either one costs, including Power's own Vocal token, the scarcest one there is. This is the
     * exact defect an upstream audit caught: Speed +5/cost 10 outscored Speed +12/cost 24 (36 vs
     * 30) because the flat, magnitude-blind base let a proportional cost win by default.
     *
     * Deliberately small relative to the run's own priority spread (18, the gap between
     * [SINGLE_STAT_HIGH_PRIORITY_SCORE] and [SINGLE_STAT_LOW_PRIORITY_SCORE]): the full swing from
     * the baseline to the largest known tier is worth barely more than half that gap, so a
     * preferred stat's smallest tier still outranks an unwanted stat's largest one. It is also
     * easily cleared by an outsized cost: a technique whose price eats deep into a scarce color
     * still loses to a cheaper, smaller technique of the same stat, so the scarcity and reserve
     * rules stay authoritative over raw magnitude. */
    private const val SINGLE_STAT_MAGNITUDE_WEIGHT = 1.5

    /** Zero at or below the baseline tier, and zero when the magnitude could not be read at all -
     * both cases reproduce the pre-magnitude score exactly. [coerceAtLeast] guards against a future
     * tier below the current known floor ever being penalized rather than simply left at baseline.
     * [coerceAtMost] against [SINGLE_STAT_MAGNITUDE_CEILING] stops an off-ladder OCR read from
     * scoring above the largest real tier. */
    private fun singleStatMagnitudeAdjustment(magnitude: Int?): Double =
        ((magnitude ?: SINGLE_STAT_MAGNITUDE_BASELINE).coerceAtMost(SINGLE_STAT_MAGNITUDE_CEILING) - SINGLE_STAT_MAGNITUDE_BASELINE)
            .coerceAtLeast(0)
            .toDouble() * SINGLE_STAT_MAGNITUDE_WEIGHT

    /** Maps a readable mastery line to its bonus type. Handles both observed formats
     * ("Training Wit Gain +1", "Skill Pts +22", "Speed +22"). Never returns FREE_ALL: the free
     * songs are identified by title, and a garbled line must not be able to claim their weight. */
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

    /** Maps a readable concert-bonus line to its family. */
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
 * Scoring context for a lesson offer. Everything is optional; an unknown input degrades its term
 * to a conservative default instead of blocking the ranking. [songsLearnedThisCycle] counts NEW
 * songs since the last concert (the Great Success floor is per-cycle); [songsLearnedTotal] is the
 * career total including Make Debut!. [turnsAfterNextConcert] is the exact post-activation runway
 * for a queued concert bonus when the caller knows it; otherwise the segment estimate is used.
 */
data class LessonScoreContext(
    val songsLearnedTotal: Int? = null,
    val songsLearnedThisCycle: Int? = null,
    /** The cycle's purchased-song target from [GrandConcertPolicy.songTargetForCycle] (3-4-4-3-3
     * cadence), or null when the turn context is unknown. */
    val cycleSongTarget: Int? = null,
    val turnsUntilConcert: Int? = null,
    val turnsAfterNextConcert: Int? = null,
    val segment: ConcertSegment = ConcertSegment.UNKNOWN,
    val energyPercent: Int? = null,
    /** True on the Complete Career screen's final drain: no training turns or concerts remain, so
     * compounding and queued bonuses are residual and every deadline has already resolved. */
    val careerComplete: Boolean = false,
    /** The run's own training stat priority ([Training.statPrioritization]), so a single-stat
     * technique is valued against the stats the player actually chose to build rather than a
     * scenario-fixed preference. Defaults to declaration order (Speed, Stamina, Power, Guts, Wit),
     * the same empty-list fallback [Training] itself falls back to; callers should pass the run's
     * resolved list rather than leaving this default. */
    val statPriority: List<StatName> = StatName.entries,
)

/** One ranked offer line. [affordable] is null when it could not be determined. [weightedCost]
 * is the scarcity-weighted price the scorer already charged, carried so the spend loop's
 * gate-advance rule can prefer the cheapest option without re-deriving costs. */
data class LessonOfferLine(
    val slot: Int,
    val title: String,
    val kind: LessonCardKind,
    val affordable: Boolean?,
    val scheduled: Boolean,
    val score: Int,
    val weightedCost: Double = 0.0,
    /** Raw sum of the card's per-type point costs, unweighted. The technique reserve compares
     * this against the live balance total; null when any cost cell was unreadable. */
    val rawCostTotal: Int? = null,
    /** The card's full per-type cost vector, for the type-aware reserve: a wallet can satisfy
     * the total reserve while being broke in exactly the types the next song needs (measured
     * live 2026-07-27: 101 points held, 69 of them Vocal, song and gate both starved). */
    val rawCost: PerformancePointVector? = null,
)

/** The report-only result of comparing a lesson offer. Never actionable. */
data class GrandConcertLessonReport(
    val ranked: List<LessonOfferLine>,
    val missingEvidence: List<String>,
    val notes: List<String>,
) {
    /** This layer never actuates. */
    val actionable: Boolean get() = false

    /** True when nothing is missing and every ranked line has a known affordability. */
    val fullyReadable: Boolean get() = missingEvidence.isEmpty() && ranked.all { it.affordable != null }
}

/**
 * Why the bot stopped on a Grand Concert screen it cannot drive.
 *
 * The handoff exists because the alternative is worse in every direction: a generic Confirm on
 * an unknown scenario screen can spend performance points, skip a concert, or dismiss a choice
 * the player wanted; and the unknown-screen ladder's last resort is a game relaunch, which is
 * the correct response to a dead game and exactly the wrong response to a live screen the bot
 * simply has not learned yet.
 */
enum class GrandConcertHandoffReason(val playerText: String) {
    UNRECOGNIZED_SCENARIO_SCREEN("Grand Concert needs manual input on this screen."),
    LESSON_SHOP_NOT_AUTOMATED("The Lesson shop is not automated yet."),
    CONCERT_NOT_AUTOMATED("Concert screens are not automated yet."),
    QUICK_MODE_UNCONFIGURED("Quick Mode has not been configured in UMA Auto+."),
    QUICK_MODE_UNREADABLE("The Quick Mode dialog could not be read reliably."),
    // CAREER_COMPLETE_NOT_AUTOMATED was removed 2026-07-26: the Complete Career screen is owned
    // by the shared career-end path (GrandConcert.checkEndScreen recognises it by template or
    // pixel probe), so no handoff for it can fire anymore.
}

/**
 * A typed stop that preserves the career. Distinguishing this from an error is what keeps the
 * queue's failure accounting honest and what stops the relaunch ladder from firing at a live
 * game: [gameIsAlive] is true here by definition, so recovery must never restart the game.
 */
data class GrandConcertHandoff(
    val reason: GrandConcertHandoffReason,
    val screenNote: String? = null,
    val evidenceScreenshot: String? = null,
) {
    /** The game is up and the screen is real; only the bot lacks a handler. */
    val gameIsAlive: Boolean get() = true

    /** A handoff must never trigger [Game.restartGame]. */
    val permitsGameRelaunch: Boolean get() = false

    /** A handoff must not tap anything on its way out. */
    val permitsGenericClick: Boolean get() = false

    /** The career is left exactly where it is, so Start can reattach to it. */
    val preservesCareer: Boolean get() = true

    /** Message shown to the player. The second sentence is the part that matters: it tells them
     * the run is recoverable and exactly how to recover it. */
    fun playerMessage(): String =
        "${reason.playerText} The career is preserved. Handle it in-game, return to the Career screen, " +
            "then press Start to resume." + (screenNote?.let { " ($it)" } ?: "")
}

/**
 * Decides what to do with the Quick Mode Settings dialog.
 *
 * There is deliberately NO default choice. The four options change how much of the game the
 * player actually sees, which is a preference the bot has no standing to invent - and picking
 * one silently would also be an irreversible per-career decision. With nothing configured the
 * bot hands off before the career starts, which costs nothing because no TP has been spent at
 * that point.
 */
sealed class QuickModeAction {
    /** Tap row [rowIndex], verify the radio moved, then Confirm exactly once. */
    data class Select(val rowIndex: Int) : QuickModeAction()

    /** The configured option is already selected: Confirm exactly once, tap nothing else. */
    object ConfirmOnly : QuickModeAction()

    /** Stop safely and tell the player what to configure. */
    data class HandOff(val handoff: GrandConcertHandoff) : QuickModeAction()
}

object QuickModePlanner {
    /**
     * @param configuredWire the option the player chose in UMA Auto+, or null/blank when unset.
     * @param selectedIndex which row the dialog currently shows as selected, or null when the
     *   dialog could not be read as exactly one selection.
     */
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
