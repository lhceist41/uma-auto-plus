package com.steve1316.uma_android_automation.bot

/**
 * The pure Grand Concert fan-pressure calculation: given committed fan facts, the current turn, and
 * the current fan count, it explains what fan requirement is next and how much room remains, and it
 * formats the [GC_FAN] telemetry line. It reads no pixels, mutates no runtime state, and makes no
 * defer/force decision.
 *
 * Crucially, the production policy inputs it exposes are deliberately held to null (see
 * [reviewGatedPolicyInputs]). The factual snapshot carries an exact deadline, deficit, calendar slack
 * (measured with the correct type-specific window), and a universal-floor race bound, but none is fed
 * to [GrandConcertFanPolicy], so production fan deferral stays fail-closed. The reason the seam stays
 * null is not that the facts are unread: it is that no authoritative guaranteed non-race fan source
 * exists to make a deferral safe AND useful. Single-mode career fans come only from races (the payout
 * curves); Grand Concert concerts award songs, performance points, and master bonuses, not fans (the
 * concert tables carry no fan reward). Per-race payout-curve minima are stronger guaranteed bounds
 * than the global [Snapshot.guaranteedRacesUpperBound] = ceil(deficit / universal-floor), but they do
 * not rescue a defer: even the best guaranteed-minimum race on every available Junior turn totals only
 * about 398 guaranteed fans against a 3000-fan target. A bound strong enough would require a per-race
 * expected-reward model, i.e. race-choice planning, which is out of scope. So the seam stays null until
 * a guaranteed-fan or per-race conservative-reward data foundation exists.
 */
object GrandConcertFanPressure {
    /** Where a requirement's turn sits relative to the current turn (or that there is none). */
    enum class RequirementStatus { NONE, FUTURE, DUE_NOW, OVERDUE, SATISFIED }

    /** Which mechanism, if any, is the earliest safely-interpretable fan requirement. */
    enum class RequirementType { NONE, FAN_GOAL, MANDATORY_GATE, UNKNOWN }

    /** How the runtime trainee name resolved against committed fan facts. */
    enum class MatchStatus { EXACT, NORMALIZED, UNKNOWN_NO_MATCH, UNKNOWN_AMBIGUOUS, NO_FACTS_ASSET }

    /**
     * A read-only snapshot of the fan situation for one turn. Every "exact" figure is present only
     * when it is genuinely exact; ambiguous choice gates expose a min/max range instead of pretending
     * one number is the target.
     */
    data class Snapshot(
        val matchStatus: MatchStatus,
        val canonicalName: String?,
        val reason: String,
        val currentTurn: Int,
        val currentFans: Int,
        val universalFloor: Int,
        // Route fan goal (earliest unmet).
        val goalStatus: RequirementStatus,
        val goalTarget: Int?,
        val goalDeadline: Int?,
        // Mandatory-race entry gate (earliest unmet).
        val gateStatus: RequirementStatus,
        val gateTurn: Int?,
        val gateSharedThreshold: Int?,
        val gateMinThreshold: Int?,
        val gateMaxThreshold: Int?,
        // Effective (earliest, safely-interpretable) requirement.
        val effectiveType: RequirementType,
        val effectiveTarget: Int?,
        val effectiveTurn: Int?,
        val effectiveExact: Boolean,
        val bothSameTurn: Boolean,
        // Derived. [futureRaceOpportunitiesIfTrainNow] and [turnsUntilRequirement] are calendar facts
        // present whenever an effective requirement turn exists; [deficit] and
        // [guaranteedRacesUpperBound] need an exact target.
        val deficit: Int?,
        val turnsUntilRequirement: Int?,
        // Base race-entry-legal future turns available AFTER choosing TRAIN on the current turn and
        // before the effective requirement must already be met. The current turn is excluded (its
        // action becomes training); a fan-goal deadline turn is itself included (an empirical
        // assumption, not yet proven against the game's check timing), but a mandatory-gate turn is
        // excluded because the gated race cannot earn the fans needed to enter itself. Base legality,
        // not guaranteed-free slots: mandatory actions may consume some.
        val futureRaceOpportunitiesIfTrainNow: Int?,
        val guaranteedRacesUpperBound: Int?,
    )

    /**
     * The two policy proof inputs, held separately from the factual snapshot so the gate is a single
     * obvious seam. Both are null today.
     */
    data class ReviewGatedPolicyInputs(val turnsUntilDeadline: Int?, val racesStillNeeded: Int?)

    /**
     * Returns the policy inputs the production deferral path may use. Deliberately null regardless of
     * how complete [snapshot] is. The reader itself is reviewed and its calendar windows are corrected,
     * but the seam stays null because no authoritative data proves a deferral that is both safe and
     * useful: there is no guaranteed non-race fan source (concerts award no fans), and the conservative
     * race bounds available (the universal floor, and the stronger per-race curve minima) are still far
     * too weak to ever permit a defer -- the best guaranteed-minimum race every Junior turn totals only
     * about 398 fans against a 3000-fan target. Wiring a non-null value here needs a guaranteed-fan or
     * per-race conservative-reward data foundation that does not yet exist. Until then a real fan
     * requirement resolves exactly as it did before.
     */
    @Suppress("UNUSED_PARAMETER")
    fun reviewGatedPolicyInputs(snapshot: Snapshot): ReviewGatedPolicyInputs = ReviewGatedPolicyInputs(null, null)

    /**
     * Builds the fan-pressure snapshot for one turn. Never throws: an absent asset, an unmatched
     * trainee, or an ambiguous identity all resolve to an UNKNOWN snapshot with a provenance reason.
     *
     * @param facts the committed fan facts, or null when the asset is missing/malformed.
     * @param rawName the runtime trainee name as read from the Details dialog.
     * @param currentTurn the current career turn.
     * @param currentFans the trainee's current fan count.
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

        // Earliest unmet route fan goal.
        val goal = charFacts.fanGoals.filter { currentFans < it.targetFans }.minWithOrNull(compareBy({ it.deadlineTurn }, { it.targetFans }))
        val goalStatus = statusOf(charFacts.fanGoals.isEmpty(), goal?.deadlineTurn, currentTurn)

        // Earliest unmet mandatory gate (unmet = cannot yet enter even the cheapest option).
        val gate = charFacts.mandatoryGates.filter { currentFans < it.minFansNeeded }.minWithOrNull(compareBy { it.turn })
        val gateStatus = statusOf(charFacts.mandatoryGates.isEmpty(), gate?.turn, currentTurn)
        val gateExact = gate?.sharedFansNeeded != null

        // Effective requirement: the earliest turn; a same-turn tie prefers the larger exact target,
        // and prefers the goal (always exact) when the gate is an ambiguous choice.
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
                // Same turn: prefer the larger exact target, but never treat an ambiguous gate as exact.
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

        // Calendar-fact derivations: present whenever an effective requirement turn exists (both an
        // exact goal/gate and an ambiguous choice gate carry a turn). The race-slot window is
        // type-specific: a fan goal's deadline turn is counted as usable (an empirical assumption, not
        // yet proven), but a mandatory-gate turn is the gated race itself and cannot earn its own entry
        // fans, so its window ends at turn - 1. Both windows
        // exclude the current turn (its action becomes TRAIN), so the count is exactly the opportunities
        // remaining after choosing to train now; raceableTurnsBetween returns 0 for an inverted window.
        var turnsUntilRequirement: Int? = null
        var futureRaceOpportunitiesIfTrainNow: Int? = null
        if (effectiveTurn != null) {
            turnsUntilRequirement = effectiveTurn - currentTurn
            val windowEnd = if (effectiveType == RequirementType.MANDATORY_GATE) effectiveTurn - 1 else effectiveTurn
            futureRaceOpportunitiesIfTrainNow = GrandConcertRaceCalendar.raceableTurnsBetween(currentTurn, windowEnd)
        }
        // Deficit and the universal-floor race bound need an exact target.
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

    /** FUTURE/DUE_NOW/OVERDUE for a requirement turn, SATISFIED when none is unmet, NONE when the
     * character has no requirement of this kind at all. */
    private fun statusOf(noneAtAll: Boolean, unmetTurn: Int?, currentTurn: Int): RequirementStatus =
        when {
            noneAtAll -> RequirementStatus.NONE
            unmetTurn == null -> RequirementStatus.SATISFIED
            unmetTurn > currentTurn -> RequirementStatus.FUTURE
            unmetTurn == currentTurn -> RequirementStatus.DUE_NOW
            else -> RequirementStatus.OVERDUE
        }

    /** Integer ceiling of [a] / [b] for positive [b]. */
    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

    /**
     * Formats the compact, diagnostic [GC_FAN] line. The policy inputs render as review-gated so it
     * is obvious the factual figures above are not wired into the decision.
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
