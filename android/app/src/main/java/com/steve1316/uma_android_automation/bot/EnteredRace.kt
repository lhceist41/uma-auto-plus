package com.steve1316.uma_android_automation.bot

/**
 * Post-decision evidence of a race the automation completed on a turn (both `runRaceWithRetries` and
 * `finalizeRaceResults` returned true); never emitted for a considered, planned or aborted race.
 * [name] is set only when a catalog name is resolved. [turnNumber] is the current turn or the explicit planned
 * tuple's, never a bare-name-map `RaceData.turnNumber` (106 same-name collisions in the 402-race catalog bind the wrong year).
 */
data class EnteredRace(
    val turnNumber: Int,
    val resolution: EnteredRaceResolution,
    val path: EnteredRacePath,
    val name: String? = null,
    val matchCount: Int? = null,
)

enum class EnteredRaceResolution(val wire: String) {
    EXACT("exact"),

    AMBIGUOUS_SET("ambiguousSet"),

    /** Carries a name only when the fuzzy match was unique. */
    FUZZY("fuzzy"),

    UNRESOLVED("unresolved"),

    /** E.g. a Unity Cup showdown. */
    NON_CATALOG("nonCatalog"),
}

enum class EnteredRacePath(val wire: String) {
    MANDATORY_GOAL("mandatoryGoal"),

    SCHEDULED("scheduled"),

    PLANNED_MANDATORY("plannedMandatory"),

    SMART("smart"),

    /** The standard/interval/fan-emergency optional-race path, which often does not know the race name. */
    STANDARD("standard"),

    /** A maiden (Make Debut) race, where the runtime generally knows only the turn. */
    MAIDEN("maiden"),

    STANDALONE("standalone"),

    UNITY_CUP_SHOWDOWN("unityCupShowdown"),
}

/** Per-turn holder for the pending [EnteredRace], owned by a single Campaign. Cleared before `decideNextAction` each turn so a fact never leaks across turns; a later [record] in the same turn overwrites (one race completes per turn). */
class PendingEnteredRace {
    private var pending: EnteredRace? = null

    fun clear() {
        pending = null
    }

    fun record(entry: EnteredRace) {
        pending = entry
    }

    fun current(): EnteredRace? = pending
}
