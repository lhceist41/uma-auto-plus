package com.steve1316.uma_android_automation.utils

import android.content.Context
import android.util.Log
import com.steve1316.uma_android_automation.MainActivity
import org.json.JSONObject

/**
 * The character and outfit identity domain the Veteran roster reader snaps its noisy header OCR
 * onto, parsed from the generated `veteran_identity.json` asset (see
 * `scripts/generate-veteran-identity-data.mjs`, which derives it from `src/data/characters.json`
 * and `src/data/character_outfits.json`).
 *
 * Outfits are keyed by character on purpose. A flat list has to separate costumes that belong to
 * DIFFERENT trainees - "Down the Line" (Mejiro Ryan) and "Off the Line" (Mejiro Dober) sit at 0.73
 * normalized similarity, close enough that a mangled read of one is a live risk of matching the
 * other. Scored inside one character's own costumes that pair never competes, and the worst
 * same-character pair in the committed domain sits at 0.44, far under the matcher's threshold.
 *
 * There is deliberately no hard-coded outfit fallback. If the asset is missing or malformed the
 * catalog is null, every outfit reads unresolved, and the fingerprint stays blocked - a loud,
 * fail-closed packaging failure rather than a silent quiet regression to base costumes only.
 */
class VeteranIdentityCatalog(
    val schemaVersion: Int,
    val outfitSource: String,
    val outfitsByCharacter: Map<String, List<String>>,
) {
    /** Canonical character names, in the asset's sorted order. */
    val characters: List<String> = outfitsByCharacter.keys.toList()

    /** How many outfit titles the domain carries in total, for the arming/diagnostic log line. */
    val outfitCount: Int = outfitsByCharacter.values.sumOf { it.size }

    /** The outfits belonging to [character], or an empty list for an unknown character. Never the
     * whole domain: a caller that could not resolve the character must not get every costume. */
    fun outfitsFor(character: String): List<String> = outfitsByCharacter[character] ?: emptyList()

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]VeteranIdentityCatalog"

        /** The generated asset the native runtime reads. */
        const val ASSET_NAME: String = "veteran_identity.json"

        /** The only payload shape this reader understands; an unsupported version parses to null. */
        const val SUPPORTED_SCHEMA_VERSION: Int = 1

        /**
         * Parses the runtime asset text. Returns null (never throws) on malformed JSON, a missing
         * field, or an unsupported schema version, so a bad asset degrades to "identity unresolved"
         * rather than crashing a scan.
         */
        fun parse(json: String): VeteranIdentityCatalog? {
            return try {
                val root = JSONObject(json)
                val schema = root.getInt("schemaVersion")
                if (schema != SUPPORTED_SCHEMA_VERSION) {
                    Log.w(TAG, "unsupported veteran_identity schemaVersion $schema (expected $SUPPORTED_SCHEMA_VERSION)")
                    return null
                }
                val charactersObj = root.getJSONObject("characters")
                val byCharacter = linkedMapOf<String, List<String>>()
                for (name in charactersObj.keys()) {
                    val outfitsArray = charactersObj.getJSONObject(name).getJSONArray("outfits")
                    byCharacter[name] = (0 until outfitsArray.length()).map { outfitsArray.getString(it) }
                }
                check(byCharacter.isNotEmpty()) { "the character domain is empty" }
                VeteranIdentityCatalog(schema, root.optString("outfitSource", "unknown"), byCharacter)
            } catch (e: Exception) {
                Log.w(TAG, "failed to parse veteran_identity: ${e.message}")
                null
            }
        }

        /**
         * Loads and parses the asset from the APK's assets. Returns null (never throws) when the
         * asset is missing or unreadable.
         */
        fun loadFromAssets(context: Context, assetName: String = ASSET_NAME): VeteranIdentityCatalog? {
            return try {
                parse(context.assets.open(assetName).bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                Log.w(TAG, "failed to load $assetName from assets: ${e.message}")
                null
            }
        }
    }
}
