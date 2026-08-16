package com.steve1316.uma_android_automation.bot

/**
 * Pure, current-scope Grand Concert fan-requirement derivation over the committed route facts. It
 * answers one question per turn: does the requirement belonging to the CURRENT requirement period
 * (the earliest goal-or-gate turn at or after now, met or not) remain unmet at the current fan
 * count. It deliberately does NOT look past a satisfied current scope to a later unmet requirement
 * (that lookahead is the Junior over-racing trap), and it never consults the fan-emergency deadline
 * OCR. It reads no pixels, mutates nothing, and makes no defer/force decision: the caller applies
 * the result to `hasFanRequirement`.
 *
 * This is intentionally NOT [GrandConcertFanPressure.evaluate], which selects the earliest UNMET
 * requirement and so skips a met period boundary. Activation needs the earliest requirement by turn,
 * INCLUDING met ones, to define the current period: once the current goal is met the alarm must shut
 * off even though a much larger requirement waits months later.
 */
object GrandConcertFanRequirement {
    /** Which committed requirement class the active current-scope requirement came from. */
    enum class Type { FAN_GOAL, MANDATORY_GATE }

    /** How the runtime trainee name resolved to committed facts, for provenance. */
    enum class MatchKind { EXACT, NORMALIZED }

    /**
     * Reason a non-Grand-Concert scenario returns from the Campaign hook: it has no route-facts
     * signal at all, so the caller keeps its legacy/template requirement value and logs nothing.
     * Distinct from a Grand Concert UNKNOWN (unmatched/ambiguous/missing asset), which is worth
     * logging because facts were expected.
     */
    const val REASON_NO_SCENARIO_FACTS = "no-scenario-facts"

    sealed class Result {
        /**
         * The current-scope requirement is unmet: force racing. [targetFans] is the binding
         * threshold (the larger of the two when a same-turn goal and gate are both unmet),
         * [requirementTurn] its turn, and [deficit] = target - currentFans (never negative).
         * [goalUnmet]/[gateUnmet] retain the truthful same-turn semantics even though a single
         * descriptor is returned.
         */
        data class Active(
            val type: Type,
            val targetFans: Int,
            val requirementTurn: Int,
            val deficit: Int,
            val match: MatchKind,
            val goalUnmet: Boolean,
            val gateUnmet: Boolean,
        ) : Result()

        /** Facts are authoritative and the current-scope requirement is satisfied (or none remains). */
        data class Inactive(val reason: String, val match: MatchKind) : Result()

        /**
         * Facts cannot authoritatively answer (missing asset, blank/unmatched/ambiguous identity, or
         * a non-facts scenario): the caller preserves its legacy behavior, never forcing from a guess.
         */
        data class Unknown(val reason: String) : Result()
    }

    /**
     * Resolves the current-scope requirement for a runtime trainee against committed facts. Mirrors
     * [GrandConcertFanPressure.evaluate]'s signature. Returns [Result.Unknown] for a null asset, a
     * blank name, or an unmatched/ambiguous identity so the caller keeps its fail-safe behavior.
     *
     * @param facts the committed fan facts, or null when the asset is missing/malformed.
     * @param rawName the runtime trainee name as read from the Details dialog.
     * @param currentTurn the current career turn.
     * @param currentFans the trainee's current fan count.
     */
    fun evaluate(facts: GrandConcertFanFacts?, rawName: String, currentTurn: Int, currentFans: Int): Result {
        if (facts == null) return Result.Unknown("facts-unavailable")
        if (rawName.isBlank()) return Result.Unknown("trainee-name-empty")
        return when (val match = facts.match(rawName)) {
            is GrandConcertFanFacts.Match.UnknownNoMatch -> Result.Unknown("trainee-unmatched")
            is GrandConcertFanFacts.Match.UnknownAmbiguous -> Result.Unknown("trainee-ambiguous")
            is GrandConcertFanFacts.Match.Matched ->
                currentScope(match.facts, currentTurn, currentFans, if (match.exact) MatchKind.EXACT else MatchKind.NORMALIZED)
        }
    }

    private fun currentScope(cf: GrandConcertCharacterFanFacts, currentTurn: Int, currentFans: Int, match: MatchKind): Result {
        // Step 1-2: the current requirement period is the earliest goal-or-gate turn at or after now,
        // INCLUDING already-met requirements (a met requirement still defines the period boundary and
        // must not be filtered out before the current scope is chosen).
        val requirementTurns =
            (cf.fanGoals.map { it.deadlineTurn } + cf.mandatoryGates.map { it.turn }).filter { it >= currentTurn }
        val scopeTurn = requirementTurns.minOrNull() ?: return Result.Inactive("no-remaining-requirement", match)

        // Step 3: is the current-scope requirement unmet at the current fan count? A mandatory gate is
        // unmet only below its cheapest option (minFansNeeded): between the min and max of an ambiguous
        // choice gate the minimum requirement is already satisfiable, so we never force on the
        // unprovable higher threshold.
        val unmetGoal =
            cf.fanGoals.filter { it.deadlineTurn == scopeTurn && currentFans < it.targetFans }.maxByOrNull { it.targetFans }
        val unmetGate =
            cf.mandatoryGates.filter { it.turn == scopeTurn && currentFans < it.minFansNeeded }.maxByOrNull { it.minFansNeeded }
        val goalUnmet = unmetGoal != null
        val gateUnmet = unmetGate != null

        // Step 4: a fully-met current scope stands down. No lookahead to a later unmet requirement --
        // this is the protection against premature over-racing before the current goal even matters.
        if (!goalUnmet && !gateUnmet) return Result.Inactive("current-scope-met", match)

        // Step 3 (same turn): a goal and a gate can both be unmet on the earliest turn. Force to the
        // binding (larger) threshold while keeping the truthful either-unmet booleans; a tie prefers
        // the goal type deterministically. Reaching the larger threshold satisfies both.
        val goalThreshold = unmetGoal?.targetFans
        val gateThreshold = unmetGate?.minFansNeeded
        val (type, target) =
            if (goalThreshold != null && (gateThreshold == null || goalThreshold >= gateThreshold)) {
                Type.FAN_GOAL to goalThreshold
            } else {
                Type.MANDATORY_GATE to gateThreshold!!
            }
        return Result.Active(type, target, scopeTurn, (target - currentFans).coerceAtLeast(0), match, goalUnmet, gateUnmet)
    }

    /**
     * The authoritative per-turn `hasFanRequirement` value: committed facts own it when they are
     * authoritative (an [Result.Active] forces true, an [Result.Inactive] forces false), while an
     * [Result.Unknown] preserves the caller's legacy/template value. Centralizing the
     * facts-vs-legacy precedence here keeps `hasFanRequirement` written from one place in the
     * requirement-refresh path.
     */
    fun resolveHasFanRequirement(legacyValue: Boolean, result: Result): Boolean =
        when (result) {
            is Result.Active -> true
            is Result.Inactive -> false
            is Result.Unknown -> legacyValue
        }
}
