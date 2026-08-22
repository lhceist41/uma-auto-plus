package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.ApplyButtonState
import org.json.JSONArray
import org.json.JSONObject

/**
 * The read-only Veteran protection record (`type:"veteran_protection"`): one row per filter-partition
 * probe of the roster. Pure model and serializer - [com.steve1316.uma_android_automation.VeteranProtectionScanner]
 * drives the Display Settings dialog and reads the pixels, this turns the reads into the durable
 * record, and `src/lib/parentLab/protection.ts` reads it back and binds it to a roster snapshot.
 *
 * Protection in this game is DERIVED, never read as its own field: there is no lock concept, only two
 * user-mutable markers that block a release - a favorite icon and a memo. The probe establishes the
 * account-wide POPULATION of each (empty / non-empty) from the game's own "OK disabled when the
 * selection is empty" behaviour, and - only when a partition is non-empty - enumerates which Veterans
 * are in it. When a partition is empty, every Veteran in the trusted roster snapshot is outside it,
 * and that complement is derived offline against the snapshot rather than by re-walking the roster.
 */
const val VETERAN_PROTECTION_SCHEMA_VERSION: Int = 1

/** The account-wide size class of a favorite/memo partition, from the OK-enabled probe. */
enum class ProtectionPopulation { EMPTY, NONEMPTY, UNKNOWN }

/** How the probe ended. Only COMPLETE is trustworthy; every other value means the derived protection
 * for this snapshot must stay UNKNOWN rather than being read as a positive result. */
enum class ProtectionScanOutcome {
    /** Both partitions probed, and any non-empty one enumerated, with filters confirmed restored OFF. */
    COMPLETE,

    /** The roster list, its Registered count, or Filters: OFF could not be confirmed before any tap. */
    PRECONDITION_FAILED,

    /** A frame that should have been the Display Settings dialog was not. The probe stops where it is. */
    UI_UNEXPECTED,

    /** A partition could not be set to the intended checkbox state after retries. Read nothing from it. */
    PARTITION_SET_FAILED,

    /** The probe finished reading but could not confirm the roster returned to Filters: OFF. */
    RESTORE_FAILED,
}

/** Maps a probe's OK-button reading to the partition's population. ENABLED means the partition would
 * return rows (non-empty); DISABLED means zero rows (empty); UNKNOWN never resolves a population. */
fun populationFromApply(state: ApplyButtonState): ProtectionPopulation =
    when (state) {
        ApplyButtonState.ENABLED -> ProtectionPopulation.NONEMPTY
        ApplyButtonState.DISABLED -> ProtectionPopulation.EMPTY
        ApplyButtonState.UNKNOWN -> ProtectionPopulation.UNKNOWN
    }

/**
 * One protection probe's durable record.
 *
 * [favoritedFingerprints] and [memoFingerprints] carry per-Veteran identity ONLY for a non-empty
 * partition that was enumerated; an empty partition leaves them empty and the offline reader derives
 * the whole-roster complement from the snapshot. [restoredFiltersOff] is the safety proof that the
 * probe left the roster exactly as it found it.
 */
data class VeteranProtectionScan(
    val schemaVersion: Int,
    val scanId: String,
    val startedAt: Long,
    val completedAt: Long,
    val registeredUsed: Int?,
    val registeredCapacity: Int?,
    val filtersOffConfirmed: Boolean?,
    val favoritePopulation: ProtectionPopulation,
    val favoriteApplyState: ApplyButtonState,
    val memoPopulation: ProtectionPopulation,
    val memoApplyState: ApplyButtonState,
    val enumerationPerformed: Boolean,
    val favoritedFingerprints: List<String>,
    val memoFingerprints: List<String>,
    val restoredFiltersOff: Boolean,
    val outcome: ProtectionScanOutcome,
    val appVersion: String,
    val screenWidth: Int,
    val screenHeight: Int,
)

/** Serializes the protection scan to its durable `type:"veteran_protection"` record. Every value the
 * reader must not confuse for a positive result (an UNKNOWN population, a non-COMPLETE outcome) is
 * written verbatim rather than defaulted. */
fun serializeVeteranProtectionScan(s: VeteranProtectionScan): JSONObject =
    JSONObject().apply {
        put("type", "veteran_protection")
        put("schemaVersion", s.schemaVersion)
        put("scanId", s.scanId)
        put("startedAt", s.startedAt)
        put("completedAt", s.completedAt)
        s.registeredUsed?.let { put("registeredUsed", it) }
        s.registeredCapacity?.let { put("registeredCapacity", it) }
        s.filtersOffConfirmed?.let { put("filtersOffConfirmed", it) }
        put("favoritePopulation", s.favoritePopulation.name.lowercase())
        put("favoriteApplyState", s.favoriteApplyState.name.lowercase())
        put("memoPopulation", s.memoPopulation.name.lowercase())
        put("memoApplyState", s.memoApplyState.name.lowercase())
        put("enumerationPerformed", s.enumerationPerformed)
        put("favoritedFingerprints", JSONArray().apply { s.favoritedFingerprints.forEach { put(it) } })
        put("memoFingerprints", JSONArray().apply { s.memoFingerprints.forEach { put(it) } })
        put("restoredFiltersOff", s.restoredFiltersOff)
        put("outcome", s.outcome.name.lowercase())
        put("app", s.appVersion)
        put("screenWidth", s.screenWidth)
        put("screenHeight", s.screenHeight)
    }
