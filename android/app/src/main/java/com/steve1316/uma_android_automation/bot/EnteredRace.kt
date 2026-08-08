package com.steve1316.uma_android_automation.bot

/**
 * Factual identity of a race the automation actually COMPLETED on a turn (Phase 1 entered-race
 * telemetry). It answers one question truthfully: "the automation finished this race-entry execution
 * path, and this is the strongest race-identity fact the runtime honestly had for it."
 *
 * It is post-decision execution evidence, deliberately separate from [CareerState] (which is the
 * immutable pre-decision world state). It is produced only at a proven completion point - after both
 * `runRaceWithRetries` and `finalizeRaceResults` returned true for the same entry - and is never
 * emitted for a considered, planned, intended, or aborted race. A RACE decision with no [EnteredRace]
 * is valid and simply means a race action was chosen but no completed-race identity fact was produced.
 *
 * Honesty rules, matching [DecisionTrace]:
 * - [name] is present ONLY when the runtime resolved a catalog name it can stand behind. It is absent
 *   for [EnteredRaceResolution.UNRESOLVED], [EnteredRaceResolution.NON_CATALOG], and for an
 *   ambiguous match set (multiple turn-scoped candidates) that the runtime did not disambiguate.
 * - [turnNumber] is the factual current turn (`campaign.date.day`) or an explicit planned tuple's
 *   turn. It is NEVER sourced from a bare-name-map-derived `RaceData.turnNumber`, whose same-name
 *   collisions (106 across the 402-race catalog) would silently bind the wrong year.
 * - [matchCount] is present only where multiple turn-scoped candidates matched, so a consumer can
 *   see the ambiguity instead of a flattened first pick.
 */
data class EnteredRace(
    /** The turn the race was entered on. Current-turn or explicit-planned-tuple authority only. */
    val turnNumber: Int,
    /** How certain the runtime's identity is for this completed entry. */
    val resolution: EnteredRaceResolution,
    /** Which execution path entered the race. Factual provenance, not policy quality. */
    val path: EnteredRacePath,
    /** Canonical race name, or null when identity is unresolved, non-catalog, or ambiguous. */
    val name: String? = null,
    /** Turn-scoped candidate count when more than one race matched, or null. */
    val matchCount: Int? = null,
)

/**
 * Certainty of a completed entry's race identity. The [wire] token is the stable serialized value; the
 * enum name is internal only.
 */
enum class EnteredRaceResolution(val wire: String) {
    /** Exactly one turn-scoped catalog race resolved and was entered. Carries a [EnteredRace.name]. */
    EXACT("exact"),

    /** Multiple turn-scoped candidates matched and the runtime did not disambiguate. No name; carries matchCount. */
    AMBIGUOUS_SET("ambiguousSet"),

    /** Identity came from the fuzzy (Jaro-Winkler) lookup tier. Carries a name only when the fuzzy match was unique. */
    FUZZY("fuzzy"),

    /** A race completed but the runtime resolved no catalog identity for it. No name. */
    UNRESOLVED("unresolved"),

    /** A completed entry with no ordinary canonical race identity (e.g. a Unity Cup showdown). No name. */
    NON_CATALOG("nonCatalog"),
}

/**
 * Execution-path provenance of a completed race entry. The [wire] token is the stable serialized
 * value. This records HOW the race was entered, not whether the choice was good.
 */
enum class EnteredRacePath(val wire: String) {
    /** A mandatory goal/objective race on the home-screen ribbon. */
    MANDATORY_GOAL("mandatoryGoal"),

    /** A scenario-scheduled/agenda race. */
    SCHEDULED("scheduled"),

    /** A user racing-plan race entered by its explicit planned tuple in mandatory-plan mode. */
    PLANNED_MANDATORY("plannedMandatory"),

    /** An optional race chosen by the bot's scored smart-racing selection. */
    SMART("smart"),

    /** The standard/interval/fan-emergency optional-race path, which often does not know the race name. */
    STANDARD("standard"),

    /** A maiden (Make Debut) race, where the runtime generally knows only the turn. */
    MAIDEN("maiden"),

    /** A race entered via the already-selected/standalone tail with no proven selected identity. */
    STANDALONE("standalone"),

    /** A Unity Cup showdown, which is not an ordinary catalog race. */
    UNITY_CUP_SHOWDOWN("unityCupShowdown"),
}

/**
 * Per-turn holder for the pending [EnteredRace] fact, mirroring [CareerStateDecisionSequence]'s
 * Campaign-held pattern. Owned by a single Campaign (one career).
 *
 * Lifecycle, tied to the main-screen decision cycle:
 * - [clear] once at the start of each decision turn, before `decideNextAction`, so a completed-race
 *   fact can never leak from a prior turn into a turn that did not complete a race.
 * - [record] only from a proven completion tail (both run + finalize succeeded). A later completion
 *   in the same turn (a scenario override re-recording a stronger identity) overwrites the earlier
 *   one; only one race completes per turn, so this is last-write-wins on the same entry, not a merge.
 * - [current] read once at trace-emit time, after the action executed.
 *
 * A turn that completes no race leaves [current] null, and the trace omits the field entirely.
 */
class PendingEnteredRace {
    private var pending: EnteredRace? = null

    /** Drop any held fact at the start of a new decision turn. */
    fun clear() {
        pending = null
    }

    /** Store the identity of a race that just completed this turn. */
    fun record(entry: EnteredRace) {
        pending = entry
    }

    /** The completed-race fact for the current turn, or null when no race completed. */
    fun current(): EnteredRace? = pending
}
