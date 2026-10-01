package com.steve1316.uma_android_automation.bot

import android.content.Context
import android.util.Log
import com.steve1316.uma_android_automation.MainActivity
import org.json.JSONObject

/**
 * One cumulative fan goal: [targetFans] by career turn [deadlineTurn], already filtered to Grand Concert in the runtime
 * asset.
 */
data class GrandConcertFanGoal(val deadlineTurn: Int, val targetFans: Int)

/** One enterable race at a mandatory-race turn and the [fansNeeded] before it may be entered. */
data class GrandConcertGateOption(val raceName: String, val fansNeeded: Int)

/**
 * A mandatory-race turn's fan entry gate. A choice turn ([isChoice]) can carry options with different fan requirements,
 * so callers must not assume one number.
 */
data class GrandConcertMandatoryGate(val turn: Int, val isChoice: Boolean, val options: List<GrandConcertGateOption>) {
    /** The lowest option threshold: the fans that make at least one option enterable. */
    val minFansNeeded: Int get() = options.minOf { it.fansNeeded }

    /** The highest option threshold: the fans that make every option enterable. */
    val maxFansNeeded: Int get() = options.maxOf { it.fansNeeded }

    /**
     * The shared threshold when every option agrees, or null when a choice turn's options differ (telemetry must not
     * collapse an ambiguous gate to one number).
     */
    val sharedFansNeeded: Int? get() = options.map { it.fansNeeded }.distinct().singleOrNull()
}

/** One character's Grand Concert fan facts: cumulative goals and mandatory-race entry gates. */
data class GrandConcertCharacterFanFacts(
    val fanGoals: List<GrandConcertFanGoal>,
    val mandatoryGates: List<GrandConcertMandatoryGate>,
)

/**
 * Read-only, deterministic parse of the generated `gc_fan_runtime.json` asset (see
 * `scripts/generate-gc-fan-runtime-data.mjs`): fan targets and deadlines, mandatory-race entry gates, and the
 * completed-race payout floor. Makes no defer/force decision.
 * Trainee identity is matched conservatively: an exact canonical-name hit, then a normalization that accepts only a
 * UNIQUE canonical match, otherwise UNKNOWN. No fuzzy best guess is used for fan safety, so the caller keeps its
 * fail-safe behaviour.
 */
class GrandConcertFanFacts private constructor(
    val schemaVersion: Int,
    val universalCompletedRaceFanFloor: Int,
    private val byCanonicalName: Map<String, GrandConcertCharacterFanFacts>,
) {
    /** Canonical names grouped by their normalized form, for the unique-normalized-match rule. */
    private val byNormalizedName: Map<String, List<String>> =
        byCanonicalName.keys.groupBy { normalize(it) }

    sealed class Match {
        /** A unique canonical match. [exact] is true for a verbatim hit, false for a normalized one. */
        data class Matched(val canonicalName: String, val facts: GrandConcertCharacterFanFacts, val exact: Boolean) : Match()

        /** No canonical or normalized match exists for the name. */
        object UnknownNoMatch : Match()

        /** The normalized name maps to more than one canonical character; refuse to guess. */
        object UnknownAmbiguous : Match()
    }

    /**
     * Exact-then-unique-normalized match of a runtime [rawName] (as OCR'd from the Details dialog); never a fuzzy
     * guess.
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

        /**
         * Lowercases and drops every non-alphanumeric character so "T.M. Opera O" and "TM Opera O" collapse to one key.
         * Only used to find a UNIQUE canonical match; it never scores candidates.
         */
        fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

        /**
         * Returns null (never throws) on malformed JSON, a missing field, or an unsupported [schemaVersion], so a bad
         * asset degrades to UNKNOWN instead of crashing a career.
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
                            // A gate with no options is malformed (minFansNeeded/maxFansNeeded would throw): fail the
                            // whole parse closed to null. The generator never emits one.
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
         * Returns null (never throws) when the asset is missing or unreadable, so a packaging or I/O failure degrades
         * to UNKNOWN.
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
