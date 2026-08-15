package com.steve1316.uma_android_automation.bot

import android.content.Context
import android.util.Log
import com.steve1316.uma_android_automation.MainActivity
import org.json.JSONObject

/**
 * One Grand Concert cumulative fan goal: reach [targetFans] fans by career turn [deadlineTurn].
 * These come from the game's master-route fan-count goals and are already filtered to the ones that
 * apply to Grand Concert before they reach the runtime asset.
 */
data class GrandConcertFanGoal(val deadlineTurn: Int, val targetFans: Int)

/** One enterable race at a mandatory-race turn and the [fansNeeded] before it may be entered. */
data class GrandConcertGateOption(val raceName: String, val fansNeeded: Int)

/**
 * A mandatory-race turn's fan entry gate. A choice turn ([isChoice]) carries more than one option,
 * and those options may require different fan counts, so callers must not assume one exact number.
 */
data class GrandConcertMandatoryGate(val turn: Int, val isChoice: Boolean, val options: List<GrandConcertGateOption>) {
    /** The lowest option threshold: the fans that make at least one option enterable. */
    val minFansNeeded: Int get() = options.minOf { it.fansNeeded }

    /** The highest option threshold: the fans that make every option enterable. */
    val maxFansNeeded: Int get() = options.maxOf { it.fansNeeded }

    /** The single shared threshold when every option agrees, or null when a choice turn's options
     * differ (an ambiguous gate a telemetry reader must not collapse to one exact number). */
    val sharedFansNeeded: Int? get() = options.map { it.fansNeeded }.distinct().singleOrNull()
}

/** One character's Grand Concert fan facts: cumulative goals and mandatory-race entry gates. */
data class GrandConcertCharacterFanFacts(
    val fanGoals: List<GrandConcertFanGoal>,
    val mandatoryGates: List<GrandConcertMandatoryGate>,
)

/**
 * The committed Grand Concert fan facts, parsed from the generated `gc_fan_runtime.json` asset (see
 * `scripts/generate-gc-fan-runtime-data.mjs`). This is the read-only, deterministic source of the
 * fan target/deadline, the mandatory-race entry gates, and the universal completed-race payout floor
 * that a fan-pressure calculation needs. It reads no pixels and makes no defer/force decision.
 *
 * Runtime trainee identity is matched conservatively: an exact canonical-name hit first, then a
 * deterministic normalization that only accepts a UNIQUE canonical match, and otherwise UNKNOWN. No
 * fuzzy best-candidate is used for fan safety - an unmatched name yields UNKNOWN and the caller
 * keeps its fail-safe behaviour.
 */
class GrandConcertFanFacts private constructor(
    val schemaVersion: Int,
    val universalCompletedRaceFanFloor: Int,
    private val byCanonicalName: Map<String, GrandConcertCharacterFanFacts>,
) {
    /** Canonical names grouped by their normalized form, for the unique-normalized-match rule. */
    private val byNormalizedName: Map<String, List<String>> =
        byCanonicalName.keys.groupBy { normalize(it) }

    /** The outcome of resolving a runtime trainee name to committed fan facts. */
    sealed class Match {
        /** A unique canonical match. [exact] is true for a verbatim hit, false for a normalized one. */
        data class Matched(val canonicalName: String, val facts: GrandConcertCharacterFanFacts, val exact: Boolean) : Match()

        /** No canonical or normalized match exists for the name. */
        object UnknownNoMatch : Match()

        /** The normalized name maps to more than one canonical character; refuse to guess. */
        object UnknownAmbiguous : Match()
    }

    /**
     * Resolves a runtime [rawName] (as OCR'd from the Details dialog) to committed fan facts using
     * exact-then-unique-normalized matching. Never returns a fuzzy best guess.
     */
    fun match(rawName: String): Match {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return Match.UnknownNoMatch
        byCanonicalName[trimmed]?.let { return Match.Matched(trimmed, it, exact = true) }
        val normalized = normalize(trimmed)
        if (normalized.isEmpty()) return Match.UnknownNoMatch
        val candidates = byNormalizedName[normalized] ?: return Match.UnknownNoMatch
        return if (candidates.size == 1) {
            Match.Matched(candidates[0], byCanonicalName.getValue(candidates[0]), exact = false)
        } else {
            Match.UnknownAmbiguous
        }
    }

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]GrandConcertFanFacts"

        /** The generated asset the native runtime reads. */
        const val ASSET_NAME: String = "gc_fan_runtime.json"

        /** The only payload shape this reader understands; an unsupported version parses to null. */
        const val SUPPORTED_SCHEMA_VERSION: Int = 1

        /** Deterministic identity normalization: lowercase and drop every non-alphanumeric character,
         * so "T.M. Opera O" and "TM Opera O" collapse to one key. Only ever used to find a UNIQUE
         * canonical match; it never scores or ranks candidates. */
        fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

        /**
         * Parses the runtime asset text. Returns null (never throws) on malformed JSON, a missing
         * field, or an unsupported [schemaVersion], so a bad asset degrades to UNKNOWN rather than
         * crashing a career.
         */
        fun parse(json: String): GrandConcertFanFacts? {
            return try {
                val root = JSONObject(json)
                val schema = root.getInt("schemaVersion")
                if (schema != SUPPORTED_SCHEMA_VERSION) {
                    Log.w(TAG, "unsupported gc_fan_runtime schemaVersion $schema (expected $SUPPORTED_SCHEMA_VERSION)")
                    return null
                }
                val floor = root.getInt("universalCompletedRaceFanFloor")
                if (floor <= 0) {
                    Log.w(TAG, "gc_fan_runtime universal floor $floor is not positive")
                    return null
                }
                val charactersObj = root.getJSONObject("characters")
                val byName = mutableMapOf<String, GrandConcertCharacterFanFacts>()
                for (name in charactersObj.keys()) {
                    val charObj = charactersObj.getJSONObject(name)
                    val goalsArray = charObj.getJSONArray("fanGoals")
                    val goals =
                        (0 until goalsArray.length()).map { i ->
                            val goal = goalsArray.getJSONObject(i)
                            GrandConcertFanGoal(goal.getInt("turn"), goal.getInt("targetFans"))
                        }
                    val gatesArray = charObj.getJSONArray("mandatoryRaces")
                    val gates =
                        (0 until gatesArray.length()).map { i ->
                            val gate = gatesArray.getJSONObject(i)
                            val optionsArray = gate.getJSONArray("options")
                            val options =
                                (0 until optionsArray.length()).map { j ->
                                    val option = optionsArray.getJSONObject(j)
                                    GrandConcertGateOption(option.getString("raceName"), option.getInt("fansNeeded"))
                                }
                            // A gate with no options is malformed: minFansNeeded/maxFansNeeded would throw
                            // in the pressure calculation. Fail the whole parse closed to null (UNKNOWN)
                            // rather than let an empty list reach minOf/maxOf. The generator never emits one.
                            check(options.isNotEmpty()) { "mandatory gate at turn ${gate.getInt("turn")} has no options" }
                            GrandConcertMandatoryGate(gate.getInt("turn"), gate.getBoolean("isChoice"), options)
                        }
                    byName[name] = GrandConcertCharacterFanFacts(goals, gates)
                }
                GrandConcertFanFacts(schema, floor, byName)
            } catch (e: Exception) {
                Log.w(TAG, "failed to parse gc_fan_runtime: ${e.message}")
                null
            }
        }

        /**
         * Loads and parses the asset from the APK's assets. Returns null (never throws) when the
         * asset is missing or unreadable, so a packaging or I/O failure degrades to UNKNOWN rather
         * than crashing a career.
         */
        fun loadFromAssets(context: Context, assetName: String = ASSET_NAME): GrandConcertFanFacts? {
            return try {
                val text = context.assets.open(assetName).bufferedReader().use { it.readText() }
                parse(text)
            } catch (e: Exception) {
                Log.w(TAG, "failed to load $assetName from assets: ${e.message}")
                null
            }
        }
    }
}
