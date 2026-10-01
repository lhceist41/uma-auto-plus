package com.steve1316.uma_android_automation.bot

/**
 * Pure Grand Concert fan-pressure calculation: explains the next fan requirement and remaining room, and formats the
 * [GC_FAN] line. Reads no pixels and makes no defer/force decision.
 *
 * The policy inputs stay null (see [reviewGatedPolicyInputs]) so production fan deferral stays fail-closed: career fans
 * come only from races and concerts award none, and even the best guaranteed-minimum race on every Junior turn totals
 * about 398 fans against a 3000-fan target. A safe defer would need a per-race conservative-reward model (race-choice
 * planning), which is out of scope.
 */
object GrandConcertFanPressure {
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
        val guaranteedRacesUpperBound: Int?,
    )

    data class ReviewGatedPolicyInputs(val turnsUntilDeadline: Int?, val racesStillNeeded: Int?)

    /**
     * Deliberately null however complete [snapshot] is: no guaranteed non-race fan source exists and the race bounds
     * are far too weak to permit a defer (see file header).
     */
    @Suppress("UNUSED_PARAMETER")
    fun reviewGatedPolicyInputs(snapshot: Snapshot): ReviewGatedPolicyInputs = ReviewGatedPolicyInputs(null, null)

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
        if (effectiveTurn != null) {
            turnsUntilRequirement = effectiveTurn - currentTurn
            val windowEnd = if (effectiveType == RequirementType.MANDATORY_GATE) effectiveTurn - 1 else effectiveTurn
            futureRaceOpportunitiesIfTrainNow = GrandConcertRaceCalendar.raceableTurnsBetween(currentTurn, windowEnd)
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

    /**
     * Formats the diagnostic [GC_FAN] line; policy inputs render as review-gated to show the facts are not wired into
     * the decision.
     */
    fun telemetryLine(
        snapshot: Snapshot,
        concertBehindPace: Boolean,
        policyInputs: ReviewGatedPolicyInputs,
        decision: GrandConcertFanPolicy.FanRaceDecision,
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
            "futureRaceSlotsIfTrainNow=${snapshot.futureRaceOpportunitiesIfTrainNow ?: "unknown"} floor=${snapshot.universalFloor} " +
            "guaranteedRacesUB=${snapshot.guaranteedRacesUpperBound ?: "unknown"} " +
            "concertBehindPace=$concertBehindPace " +
            "policyDeadline=${policyInputs.turnsUntilDeadline ?: "gated"} " +
            "policyRacesNeeded=${policyInputs.racesStillNeeded ?: "gated"} decision=$decision"
    }
}
