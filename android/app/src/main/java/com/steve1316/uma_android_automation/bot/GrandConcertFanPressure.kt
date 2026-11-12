package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.Aptitude
import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.RaceGrade
import java.util.Locale

/**
 * Pure Grand Concert fan-pressure calculation: explains the next fan requirement, measures the fans the remaining race
 * turns can conservatively earn for every unmet goal and gate, and formats the [GC_FAN] line. Reads no pixels.
 *
 * Concerts do pay fans, but no source gives the amount, so only races count toward a requirement.
 */
object GrandConcertFanPressure {
    /** Every Junior Make Debut program pays fan set 3: 700 fans for the win (master.mdb `single_mode_fan_count`). */
    const val DEBUT_WIN_FANS = 700

    // Share of the face value by finishing place, the same for every fan set (master.mdb `single_mode_fan_count`).
    private const val SECOND_PLACE_SHARE = 0.40
    private const val THIRD_PLACE_SHARE = 0.25
    private const val FIFTH_PLACE_SHARE = 0.10
    private const val SIXTH_PLACE_SHARE = 0.09

    private const val JUNIOR_LAST_TURN = 24

    enum class RequirementStatus { NONE, FUTURE, DUE_NOW, OVERDUE, SATISFIED }

    enum class RequirementType { NONE, FAN_GOAL, MANDATORY_GATE, UNKNOWN }

    enum class MatchStatus { EXACT, NORMALIZED, UNKNOWN_NO_MATCH, UNKNOWN_AMBIGUOUS, NO_FACTS_ASSET }

    /**
     * Read-only fan snapshot for one turn; "exact" figures are present only when exact, ambiguous choice gates expose a
     * min/max range.
     */
    data class Snapshot(
        val matchStatus: MatchStatus,
        val canonicalName: String?,
        val reason: String,
        val currentTurn: Int,
        val currentFans: Int,
        val universalFloor: Int,
        val goalStatus: RequirementStatus,
        val goalTarget: Int?,
        val goalDeadline: Int?,
        val gateStatus: RequirementStatus,
        val gateTurn: Int?,
        val gateSharedThreshold: Int?,
        val gateMinThreshold: Int?,
        val gateMaxThreshold: Int?,
        val effectiveType: RequirementType,
        val effectiveTarget: Int?,
        val effectiveTurn: Int?,
        val effectiveExact: Boolean,
        val bothSameTurn: Boolean,
        // The calendar facts exist whenever an effective requirement turn does; [deficit] and
        // [guaranteedRacesUpperBound] need an exact target.
        val deficit: Int?,
        val turnsUntilRequirement: Int?,
        // Race-entry-legal future turns after choosing TRAIN now and before the requirement must be met. The current
        // turn is excluded; a fan-goal deadline turn is included (empirical assumption, unproven against the game's
        // check timing) but a mandatory-gate turn is not, since the gated race cannot earn its own entry fans.
        val futureRaceOpportunitiesIfTrainNow: Int?,
        // Turns in that window the bot can actually fan-race: not Summer and not the trainee's own mandatory race turns.
        val usableRaceTurnsInWindow: Int?,
        val guaranteedRacesUpperBound: Int?,
    )

    /** The racing requirement is cleared in Summer, so no fan race happens there although the turns are race-entry legal. */
    private val summerTurns: Set<Int> by lazy {
        (GrandConcertRaceCalendar.FIRST_RACEABLE_TURN..GrandConcertRaceCalendar.LAST_CAREER_TURN).filter { GameDate.isSummer(it) }.toSet()
    }

    private fun usableRaceTurns(currentTurn: Int, windowEnd: Int, mandatoryTurns: List<Int>): Int =
        ((currentTurn + 1)..windowEnd).count { GrandConcertRaceCalendar.isRaceableTurn(it) && it !in summerTurns && it !in mandatoryTurns }

    /** One calendar race at a turn, with the worse of the trainee's surface and distance aptitude for it. */
    data class RaceRow(val grade: RaceGrade, val fans: Int, val worstAptitude: Aptitude)

    /** An unmet goal or gate: [target] fans (a choice gate at its highest option) earned by the end of [windowEnd]. */
    data class Requirement(val type: RequirementType, val target: Int, val turn: Int, val windowEnd: Int)

    /** [capacity] is the conservative fans of the window's race turns minus its two best, the margin for a lost turn or a bad race. */
    data class RequirementCapacity(val requirement: Requirement, val deficit: Int, val capacity: Double) {
        val slack: Double get() = capacity - deficit
        val hasSlack: Boolean get() = capacity >= deficit
    }

    /**
     * Every unmet requirement's capacity this turn; [failSafeReason] set means the inputs prove nothing and the bot races.
     * [todayCapacity] counts today's races whatever their entry fans (the race list shows what is enterable).
     */
    data class CapacityCheck(
        val turn: Int,
        val failSafeReason: String?,
        val requirements: List<RequirementCapacity>,
        val todayCapacity: Double,
        val laterTurnHasCapacity: Boolean,
        val fanBonus: Double,
    ) {
        /** The requirement with the least slack decides. */
        val binding: RequirementCapacity? get() = requirements.minByOrNull { it.slack }

        val everyRequirementHasSlack: Boolean get() = requirements.all { it.hasSlack }

        companion object {
            fun failSafe(turn: Int, reason: String, fanBonus: Double) = CapacityCheck(turn, reason, emptyList(), 0.0, false, fanBonus)
        }
    }

    /**
     * Fan Bonus proven by the debut: the debut must be won before any other race opens, so its payout over 700 measures the
     * deck's bonus, not placement. 1.0 when unread (a restart, a Maiden race, no clean read).
     */
    fun fanBonus(debutFans: Int?): Double = if (debutFans == null) 1.0 else maxOf(1.0, debutFans.toDouble() / DEBUT_WIN_FANS)

    /** B or better assumes 3rd place, C 6th, D or worse nothing, each times the Fan Bonus but never above 2nd or 5th place. */
    fun placeShare(worstAptitude: Aptitude, fanBonus: Double): Double =
        when {
            worstAptitude >= Aptitude.B -> minOf(SECOND_PLACE_SHARE, THIRD_PLACE_SHARE * fanBonus)
            worstAptitude == Aptitude.C -> minOf(FIFTH_PLACE_SHARE, SIXTH_PLACE_SHARE * fanBonus)
            else -> 0.0
        }

    /** Highest entry fans per year and grade in master.mdb `single_mode_program.need_fan_count`; the race data has no entry field. */
    fun conservativeEntryFans(turn: Int, grade: RaceGrade): Int {
        val junior = turn <= JUNIOR_LAST_TURN
        return when (grade) {
            RaceGrade.PRE_OP, RaceGrade.OP -> 350
            RaceGrade.G3 -> if (junior) 350 else 1500
            RaceGrade.G2 -> if (junior) 375 else 2000
            RaceGrade.G1 -> if (junior) 1000 else 25000
            else -> Int.MAX_VALUE
        }
    }

    /** The best conservative payout among [rows]; with [projectedFans] a race counts only once its entry fans are reached. */
    fun turnCapacity(turn: Int, rows: List<RaceRow>, projectedFans: Double?, fanBonus: Double): Double {
        var best = 0.0
        for (row in rows) {
            if (projectedFans != null && projectedFans < conservativeEntryFans(turn, row.grade)) continue
            best = maxOf(best, row.fans * placeShare(row.worstAptitude, fanBonus))
        }
        return best
    }

    /**
     * Checks EVERY unmet goal and gate, not only the current one: a met early goal must not leave a larger later gate
     * unprotected. Usable turns skip Summer, the trainee's goal race turns and fan-goal deadline turns. Walking each
     * window in turn order, projected fans grow by the capacities already counted.
     */
    fun capacityCheck(
        facts: GrandConcertFanFacts?,
        rawName: String,
        currentTurn: Int,
        currentFans: Int,
        raceRows: Map<Int, List<RaceRow>>,
        fanBonus: Double,
    ): CapacityCheck {
        fun failSafe(reason: String) = CapacityCheck.failSafe(currentTurn, reason, fanBonus)
        if (facts == null) return failSafe("data-asset-missing-or-malformed")
        val charFacts =
            when (val match = facts.match(rawName)) {
                is GrandConcertFanFacts.Match.Matched -> match.facts
                is GrandConcertFanFacts.Match.UnknownAmbiguous -> return failSafe("trainee-ambiguous")
                is GrandConcertFanFacts.Match.UnknownNoMatch -> return failSafe("trainee-unmatched")
            }
        if (currentFans <= 1) return failSafe("fans-unread")
        val dueOrOverdue =
            charFacts.fanGoals.any { currentFans < it.targetFans && it.deadlineTurn <= currentTurn } ||
                charFacts.mandatoryGates.any { currentFans < it.minFansNeeded && it.turn <= currentTurn }
        if (dueOrOverdue) return failSafe("requirement-due-or-overdue")
        if (raceRows.values.all { it.isEmpty() }) return failSafe("race-data-empty")

        val requirements =
            charFacts.fanGoals.filter { currentFans < it.targetFans }
                .map { Requirement(RequirementType.FAN_GOAL, it.targetFans, it.deadlineTurn, it.deadlineTurn) } +
                charFacts.mandatoryGates.filter { currentFans < it.maxFansNeeded && it.turn > currentTurn }
                    .map { Requirement(RequirementType.MANDATORY_GATE, it.maxFansNeeded, it.turn, it.turn - 1) }
        val blocked = (charFacts.mandatoryGates.map { it.turn } + charFacts.fanGoals.map { it.deadlineTurn }).toSet()
        var laterTurnHasCapacity = false
        val capacities = ArrayList<RequirementCapacity>()
        for (requirement in requirements.sortedBy { it.windowEnd }) {
            val usable =
                ((currentTurn + 1)..requirement.windowEnd).filter {
                    GrandConcertRaceCalendar.isRaceableTurn(it) && it !in summerTurns && it !in blocked
                }
            if (usable.isNotEmpty() && usable.all { raceRows[it].isNullOrEmpty() }) return failSafe("race-data-window-empty")
            var projected = currentFans.toDouble()
            val caps = ArrayList<Double>()
            for (turn in usable) {
                val cap = turnCapacity(turn, raceRows[turn].orEmpty(), projected, fanBonus)
                caps.add(cap)
                projected += cap
            }
            if (caps.any { it > 0.0 }) laterTurnHasCapacity = true
            caps.sortDescending()
            capacities.add(RequirementCapacity(requirement, requirement.target - currentFans, caps.drop(2).sum()))
        }
        val today = turnCapacity(currentTurn, raceRows[currentTurn].orEmpty(), null, fanBonus)
        return CapacityCheck(currentTurn, null, capacities, today, laterTurnHasCapacity, fanBonus)
    }

    /** The last two fan-pressure payouts each fell below the capacity assumed for their turn: the floor is wrong for this trainee. */
    fun payoutsBelowAssumption(payouts: List<Pair<Int, Double>>): Boolean = payouts.size >= 2 && payouts.takeLast(2).all { (paid, assumed) -> paid < assumed }

    /**
     * Never throws: an absent asset, unmatched trainee or ambiguous identity resolves to an UNKNOWN snapshot with a
     * provenance reason.
     */
    fun evaluate(facts: GrandConcertFanFacts?, rawName: String, currentTurn: Int, currentFans: Int): Snapshot {
        if (facts == null) {
            return unknown(MatchStatus.NO_FACTS_ASSET, "data-asset-missing-or-malformed", null, currentTurn, currentFans, 0)
        }
        val floor = facts.universalCompletedRaceFanFloor
        return when (val match = facts.match(rawName)) {
            is GrandConcertFanFacts.Match.UnknownNoMatch -> {
                val reason = if (rawName.isBlank()) "trainee-name-empty" else "trainee-unmatched:'${rawName.trim()}'"
                unknown(MatchStatus.UNKNOWN_NO_MATCH, reason, null, currentTurn, currentFans, floor)
            }
            is GrandConcertFanFacts.Match.UnknownAmbiguous ->
                unknown(MatchStatus.UNKNOWN_AMBIGUOUS, "trainee-ambiguous:'${rawName.trim()}'", null, currentTurn, currentFans, floor)
            is GrandConcertFanFacts.Match.Matched ->
                matched(match, currentTurn, currentFans, floor)
        }
    }

    private fun unknown(status: MatchStatus, reason: String, name: String?, turn: Int, fans: Int, floor: Int): Snapshot =
        Snapshot(
            matchStatus = status,
            canonicalName = name,
            reason = reason,
            currentTurn = turn,
            currentFans = fans,
            universalFloor = floor,
            goalStatus = RequirementStatus.NONE,
            goalTarget = null,
            goalDeadline = null,
            gateStatus = RequirementStatus.NONE,
            gateTurn = null,
            gateSharedThreshold = null,
            gateMinThreshold = null,
            gateMaxThreshold = null,
            effectiveType = RequirementType.UNKNOWN,
            effectiveTarget = null,
            effectiveTurn = null,
            effectiveExact = false,
            bothSameTurn = false,
            deficit = null,
            turnsUntilRequirement = null,
            futureRaceOpportunitiesIfTrainNow = null,
            usableRaceTurnsInWindow = null,
            guaranteedRacesUpperBound = null,
        )

    private fun matched(match: GrandConcertFanFacts.Match.Matched, currentTurn: Int, currentFans: Int, floor: Int): Snapshot {
        val charFacts = match.facts

        val goal = charFacts.fanGoals.filter { currentFans < it.targetFans }.minWithOrNull(compareBy({ it.deadlineTurn }, { it.targetFans }))
        val goalStatus = statusOf(charFacts.fanGoals.isEmpty(), goal?.deadlineTurn, currentTurn)

        // Unmet gate = cannot yet enter even the cheapest option.
        val gate = charFacts.mandatoryGates.filter { currentFans < it.minFansNeeded }.minWithOrNull(compareBy { it.turn })
        val gateStatus = statusOf(charFacts.mandatoryGates.isEmpty(), gate?.turn, currentTurn)
        val gateExact = gate?.sharedFansNeeded != null

        // Effective requirement: earliest turn; same-turn ties prefer the larger exact target, and the goal over an
        // ambiguous gate.
        val goalTurn = goal?.deadlineTurn
        val gateTurn = gate?.turn
        val bothSameTurn = goalTurn != null && gateTurn != null && goalTurn == gateTurn
        val effectiveIsGoal: Boolean? =
            when {
                goal == null && gate == null -> null
                gate == null -> true
                goal == null -> false
                goalTurn!! < gateTurn!! -> true
                gateTurn < goalTurn -> false
                gateExact && gate.sharedFansNeeded!! > goal.targetFans -> false
                else -> true
            }

        var effectiveType = RequirementType.NONE
        var effectiveTarget: Int? = null
        var effectiveTurn: Int? = null
        var effectiveExact = false
        when (effectiveIsGoal) {
            true -> {
                effectiveType = RequirementType.FAN_GOAL
                effectiveTarget = goal!!.targetFans
                effectiveTurn = goal.deadlineTurn
                effectiveExact = true
            }
            false -> {
                effectiveType = RequirementType.MANDATORY_GATE
                effectiveTurn = gate!!.turn
                effectiveExact = gateExact
                effectiveTarget = gate.sharedFansNeeded // null when ambiguous
            }
            null -> Unit
        }

        // The race-slot window is type-specific: a fan-goal deadline turn counts as usable (unproven), a mandatory-gate
        // window ends at turn - 1. Both exclude the current turn (its action becomes TRAIN); raceableTurnsBetween
        // returns 0 for an inverted window.
        var turnsUntilRequirement: Int? = null
        var futureRaceOpportunitiesIfTrainNow: Int? = null
        var usableRaceTurnsInWindow: Int? = null
        if (effectiveTurn != null) {
            turnsUntilRequirement = effectiveTurn - currentTurn
            val windowEnd = if (effectiveType == RequirementType.MANDATORY_GATE) effectiveTurn - 1 else effectiveTurn
            futureRaceOpportunitiesIfTrainNow = GrandConcertRaceCalendar.raceableTurnsBetween(currentTurn, windowEnd)
            val mandatoryTurns = charFacts.mandatoryGates.map { it.turn }.distinct()
            usableRaceTurnsInWindow = usableRaceTurns(currentTurn, windowEnd, mandatoryTurns)
        }
        var deficit: Int? = null
        var guaranteedRacesUpperBound: Int? = null
        if (effectiveExact && effectiveTarget != null) {
            deficit = (effectiveTarget - currentFans).coerceAtLeast(0)
            guaranteedRacesUpperBound = if (deficit > 0 && floor > 0) ceilDiv(deficit, floor) else 0
        }

        val reason =
            when {
                effectiveType == RequirementType.NONE -> "requirement-satisfied-or-none"
                effectiveType == RequirementType.MANDATORY_GATE && !effectiveExact -> "gate-choice-ambiguous"
                else -> "ok"
            }

        return Snapshot(
            matchStatus = if (match.exact) MatchStatus.EXACT else MatchStatus.NORMALIZED,
            canonicalName = match.canonicalName,
            reason = reason,
            currentTurn = currentTurn,
            currentFans = currentFans,
            universalFloor = floor,
            goalStatus = goalStatus,
            goalTarget = goal?.targetFans,
            goalDeadline = goal?.deadlineTurn,
            gateStatus = gateStatus,
            gateTurn = gate?.turn,
            gateSharedThreshold = gate?.sharedFansNeeded,
            gateMinThreshold = gate?.minFansNeeded,
            gateMaxThreshold = gate?.maxFansNeeded,
            effectiveType = effectiveType,
            effectiveTarget = effectiveTarget,
            effectiveTurn = effectiveTurn,
            effectiveExact = effectiveExact,
            bothSameTurn = bothSameTurn,
            deficit = deficit,
            turnsUntilRequirement = turnsUntilRequirement,
            futureRaceOpportunitiesIfTrainNow = futureRaceOpportunitiesIfTrainNow,
            usableRaceTurnsInWindow = usableRaceTurnsInWindow,
            guaranteedRacesUpperBound = guaranteedRacesUpperBound,
        )
    }

    /** SATISFIED when none is unmet, NONE when the character has no requirement of this kind. */
    private fun statusOf(noneAtAll: Boolean, unmetTurn: Int?, currentTurn: Int): RequirementStatus =
        when {
            noneAtAll -> RequirementStatus.NONE
            unmetTurn == null -> RequirementStatus.SATISFIED
            unmetTurn > currentTurn -> RequirementStatus.FUTURE
            unmetTurn == currentTurn -> RequirementStatus.DUE_NOW
            else -> RequirementStatus.OVERDUE
        }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

    /** Formats the diagnostic [GC_FAN] line: the snapshot, then the requirement with the least slack and the decision. */
    fun telemetryLine(
        snapshot: Snapshot,
        check: CapacityCheck,
        concertBehindPace: Boolean,
        decision: GrandConcertFanPolicy.FanRaceDecision,
        payoutsLow: Boolean,
        rearmed: Boolean,
    ): String {
        val goal =
            when (snapshot.goalStatus) {
                RequirementStatus.NONE -> "none"
                RequirementStatus.SATISFIED -> "satisfied"
                else -> "${snapshot.goalTarget}@${snapshot.goalDeadline}(${snapshot.goalStatus})"
            }
        val gate =
            when (snapshot.gateStatus) {
                RequirementStatus.NONE -> "none"
                RequirementStatus.SATISFIED -> "satisfied"
                else -> {
                    val threshold =
                        if (snapshot.gateSharedThreshold != null) {
                            "${snapshot.gateSharedThreshold}"
                        } else {
                            "min${snapshot.gateMinThreshold}/max${snapshot.gateMaxThreshold}(ambiguous)"
                        }
                    "$threshold@${snapshot.gateTurn}(${snapshot.gateStatus})"
                }
            }
        val effective =
            when (snapshot.effectiveType) {
                RequirementType.NONE, RequirementType.UNKNOWN -> "${snapshot.effectiveType}"
                else -> {
                    val target = if (snapshot.effectiveExact) "${snapshot.effectiveTarget}" else "inexact"
                    "${snapshot.effectiveType}(target=$target turn=${snapshot.effectiveTurn})"
                }
            }
        val binding =
            check.binding?.let {
                "${it.requirement.type}(target=${it.requirement.target} turn=${it.requirement.turn}) " +
                    "capacity=${it.capacity.toInt()} slack=${it.slack.toInt()}"
            } ?: "none"
        val nameTag = snapshot.canonicalName?.let { "($it)" } ?: ""
        return "[GRAND_CONCERT] [GC_FAN] fanReq=true turn=${snapshot.currentTurn} fans=${snapshot.currentFans} " +
            "match=${snapshot.matchStatus}$nameTag reason=${snapshot.reason} " +
            "goal=$goal gate=$gate bothSameTurn=${snapshot.bothSameTurn} " +
            "eff=$effective deficit=${snapshot.deficit ?: "unknown"} " +
            "futureRaceSlotsIfTrainNow=${snapshot.futureRaceOpportunitiesIfTrainNow ?: "unknown"} " +
            "usableRaceTurns=${snapshot.usableRaceTurnsInWindow ?: "unknown"} " +
            "floor=${snapshot.universalFloor} " +
            "guaranteedRacesUB=${snapshot.guaranteedRacesUpperBound ?: "unknown"} " +
            "concertBehindPace=$concertBehindPace lowPayouts=$payoutsLow " +
            "binding=$binding " +
            "today=${check.todayCapacity.toInt()} k=${String.format(Locale.US, "%.2f", check.fanBonus)} rearmed=$rearmed " +
            "failSafe=${check.failSafeReason ?: "none"} decision=$decision"
    }
}
