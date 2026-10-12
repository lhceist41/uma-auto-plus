package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.GameDate

/**
 * Pure Grand Concert fan-pressure calculation: explains the next fan requirement and remaining room, derives the
 * [GrandConcertFanPolicy] inputs, and formats the [GC_FAN] line. Reads no pixels.
 *
 * Concerts do pay fans, but too late before some deadlines to rely on, so only races count toward a requirement.
 */
object GrandConcertFanPressure {
    /** 3rd place (25%) of the cheapest OP win (1600 base, `races.json`) with no Fan Bonus; measured wins pay about 2300+. */
    const val CONSERVATIVE_FANS_PER_RACE = 400

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
        // The unmet requirement after the effective one with the least slack (a choice gate at its highest threshold):
        // the same races serve it, so deferring for the nearest must not starve a larger later one. Null when none.
        val tightestLaterUsableTurns: Int?,
        val tightestLaterDeficit: Int?,
        val guaranteedRacesUpperBound: Int?,
    )

    data class PolicyInputs(val turnsUntilDeadline: Int?, val racesStillNeeded: Int?)

    /**
     * Race for the requirement only once the free race turns left before it no longer cover a conservative race count with
     * room to spare: defer while usable >= ceil(1.5 * ceil(deficit / [CONSERVATIVE_FANS_PER_RACE])) + 2. The 1.5 avoids
     * back-to-back race warnings, the +2 is margin. The tightest of the effective and later requirements decides. Null
     * inputs (a fail-safe race) for an unknown or ambiguous requirement, a restart's fan read of 0 or 1, a requirement
     * due now or overdue, or when [recentFanRacesPaidLow].
     */
    fun policyInputs(snapshot: Snapshot, recentFanRacesPaidLow: Boolean): PolicyInputs {
        val failClosed = PolicyInputs(null, null)
        if (recentFanRacesPaidLow) return failClosed
        if (snapshot.matchStatus != MatchStatus.EXACT && snapshot.matchStatus != MatchStatus.NORMALIZED) return failClosed
        if (!snapshot.effectiveExact || snapshot.currentFans <= 1) return failClosed
        val turn = snapshot.effectiveTurn ?: return failClosed
        if (turn <= snapshot.currentTurn) return failClosed
        var deficit = snapshot.deficit ?: return failClosed
        var usable = snapshot.usableRaceTurnsInWindow ?: return failClosed
        val laterUsable = snapshot.tightestLaterUsableTurns
        val laterDeficit = snapshot.tightestLaterDeficit
        if (laterUsable != null && laterDeficit != null && laterUsable - racesWithMargin(laterDeficit) < usable - racesWithMargin(deficit)) {
            usable = laterUsable
            deficit = laterDeficit
        }
        // decide() defers iff turnsUntilDeadline - 1 >= racesStillNeeded.
        return PolicyInputs(turnsUntilDeadline = usable - 1, racesStillNeeded = racesWithMargin(deficit))
    }

    /**
     * The later requirement's (fans still needed, free race turns left) when [policyInputs] weighs it above the effective one, so the
     * race line can say why the bot raced; null when the effective requirement binds or the inputs are unknown. Read-only.
     */
    fun bindingLaterRequirement(snapshot: Snapshot, recentFanRacesPaidLow: Boolean = false): Pair<Int, Int>? {
        if (recentFanRacesPaidLow) return null
        if (snapshot.matchStatus != MatchStatus.EXACT && snapshot.matchStatus != MatchStatus.NORMALIZED) return null
        if (!snapshot.effectiveExact || snapshot.currentFans <= 1) return null
        val turn = snapshot.effectiveTurn ?: return null
        if (turn <= snapshot.currentTurn) return null
        val deficit = snapshot.deficit ?: return null
        val usable = snapshot.usableRaceTurnsInWindow ?: return null
        val laterUsable = snapshot.tightestLaterUsableTurns ?: return null
        val laterDeficit = snapshot.tightestLaterDeficit ?: return null
        return if (laterUsable - racesWithMargin(laterDeficit) < usable - racesWithMargin(deficit)) laterDeficit to laterUsable else null
    }

    private fun racesWithMargin(deficit: Int): Int = ceilDiv(ceilDiv(deficit, CONSERVATIVE_FANS_PER_RACE) * 3, 2)

    /** The racing requirement is cleared in Summer, so no fan race happens there although the turns are race-entry legal. */
    private val summerTurns: Set<Int> by lazy {
        (GrandConcertRaceCalendar.FIRST_RACEABLE_TURN..GrandConcertRaceCalendar.LAST_CAREER_TURN).filter { GameDate.isSummer(it) }.toSet()
    }

    private fun usableRaceTurns(currentTurn: Int, windowEnd: Int, mandatoryTurns: List<Int>): Int =
        ((currentTurn + 1)..windowEnd).count { GrandConcertRaceCalendar.isRaceableTurn(it) && it !in summerTurns && it !in mandatoryTurns }

    /** The last two fan-pressure races each paid under [CONSERVATIVE_FANS_PER_RACE], so the rule's reward assumption failed for this trainee. */
    fun recentFanRacesPaidLow(payouts: List<Int>): Boolean = payouts.size >= 2 && payouts.takeLast(2).all { it < CONSERVATIVE_FANS_PER_RACE }

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
            tightestLaterUsableTurns = null,
            tightestLaterDeficit = null,
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
        var tightestLaterUsableTurns: Int? = null
        var tightestLaterDeficit: Int? = null
        if (effectiveTurn != null) {
            turnsUntilRequirement = effectiveTurn - currentTurn
            val windowEnd = if (effectiveType == RequirementType.MANDATORY_GATE) effectiveTurn - 1 else effectiveTurn
            futureRaceOpportunitiesIfTrainNow = GrandConcertRaceCalendar.raceableTurnsBetween(currentTurn, windowEnd)
            val mandatoryTurns = charFacts.mandatoryGates.map { it.turn }.distinct()
            usableRaceTurnsInWindow = usableRaceTurns(currentTurn, windowEnd, mandatoryTurns)
            // (deadline, target, window end) of each unmet requirement.
            val later =
                charFacts.fanGoals.filter { currentFans < it.targetFans }.map { Triple(it.deadlineTurn, it.targetFans, it.deadlineTurn) } +
                    charFacts.mandatoryGates.filter { currentFans < it.minFansNeeded }.map { Triple(it.turn, it.maxFansNeeded, it.turn - 1) }
            later.filter { it.first > effectiveTurn }
                .map { (_, target, end) -> usableRaceTurns(currentTurn, end, mandatoryTurns) to target - currentFans }
                .minByOrNull { (usable, deficit) -> usable - racesWithMargin(deficit) }
                ?.let { (usable, deficit) ->
                    tightestLaterUsableTurns = usable
                    tightestLaterDeficit = deficit
                }
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
            tightestLaterUsableTurns = tightestLaterUsableTurns,
            tightestLaterDeficit = tightestLaterDeficit,
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

    /** Formats the diagnostic [GC_FAN] line; null policy inputs render as failClosed. */
    fun telemetryLine(
        snapshot: Snapshot,
        concertBehindPace: Boolean,
        policyInputs: PolicyInputs,
        decision: GrandConcertFanPolicy.FanRaceDecision,
        recentFanRacesPaidLow: Boolean = false,
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
        val nameTag = snapshot.canonicalName?.let { "($it)" } ?: ""
        return "[GRAND_CONCERT] [GC_FAN] fanReq=true turn=${snapshot.currentTurn} fans=${snapshot.currentFans} " +
            "match=${snapshot.matchStatus}$nameTag reason=${snapshot.reason} " +
            "goal=$goal gate=$gate bothSameTurn=${snapshot.bothSameTurn} " +
            "eff=$effective deficit=${snapshot.deficit ?: "unknown"} " +
            "futureRaceSlotsIfTrainNow=${snapshot.futureRaceOpportunitiesIfTrainNow ?: "unknown"} " +
            "usableRaceTurns=${snapshot.usableRaceTurnsInWindow ?: "unknown"} " +
            "later=${snapshot.tightestLaterDeficit?.let { "$it/${snapshot.tightestLaterUsableTurns}turns" } ?: "none"} " +
            "floor=${snapshot.universalFloor} " +
            "guaranteedRacesUB=${snapshot.guaranteedRacesUpperBound ?: "unknown"} " +
            "concertBehindPace=$concertBehindPace lowPayouts=$recentFanRacesPaidLow " +
            "policyDeadline=${policyInputs.turnsUntilDeadline ?: "failClosed"} " +
            "policyRacesNeeded=${policyInputs.racesStillNeeded ?: "failClosed"} decision=$decision"
    }
}
