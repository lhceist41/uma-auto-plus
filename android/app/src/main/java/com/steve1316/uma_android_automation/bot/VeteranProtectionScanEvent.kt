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
 * selection is empty" behaviour. Only two positively empty partitions can yield COMPLETE. A
 * nonempty partition remains unknown until a separate filtered-list census is proven.
 */
const val VETERAN_PROTECTION_SCHEMA_VERSION: Int = 2

/** The account-wide size class of a favorite/memo partition, from the OK-enabled probe. */
enum class ProtectionPopulation { EMPTY, NONEMPTY, UNKNOWN }

/** How the probe ended. Only COMPLETE is trustworthy; every other value means the derived protection
 * for this snapshot must stay UNKNOWN rather than being read as a positive result. */
enum class ProtectionScanOutcome {
    /** Both exact partitions positively empty, with filters confirmed restored OFF. */
    COMPLETE,

    /** A nonempty partition has no independent filtered-list census. */
    NONEMPTY_PARTITION_CENSUS_UNAVAILABLE,

    /** The roster list, its Registered count, or Filters: OFF could not be confirmed before any tap. */
    PRECONDITION_FAILED,

    /** A frame that should have been the Display Settings dialog was not. The probe stops where it is. */
    UI_UNEXPECTED,

    /** A partition could not be set to the intended checkbox state after retries. Read nothing from it. */
    PARTITION_SET_FAILED,

    /** The probe finished reading but could not confirm the roster returned to Filters: OFF. */
    RESTORE_FAILED,
}

/** Called only after the exact target and unrelated filters were positively reread. An empty target
 * also needs a fresh neutral baseline and an enabled, exact complementary probe. */
fun populationFromProvenFilters(state: ApplyButtonState, complementary: ApplyButtonState? = null): ProtectionPopulation =
    when (state) {
        ApplyButtonState.ENABLED -> ProtectionPopulation.NONEMPTY
        ApplyButtonState.DISABLED -> if (complementary == ApplyButtonState.ENABLED) ProtectionPopulation.EMPTY else ProtectionPopulation.UNKNOWN
        ApplyButtonState.UNKNOWN -> ProtectionPopulation.UNKNOWN
    }

/**
 * One protection probe's durable record.
 *
 * [favoritedFingerprints] and [memoFingerprints] stay empty for current probes. Only a complete
 * empty-partition record lets the offline reader derive the whole-roster complement. The restored
 * filter state is checked after every probe.
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
    val favoriteBaselineVerified: Boolean = false,
    val memoBaselineVerified: Boolean = false,
    val rosterBindingVersion: Int? = null,
    val rosterScanId: String? = null,
    val rosterDigest: String? = null,
    val filterBaselineEvidenceVersion: Int? = null,
    val favoriteBaselineReadings: Map<String, String> = emptyMap(),
    val memoBaselineReadings: Map<String, String> = emptyMap(),
    val probeDiagnostics: List<String> = emptyList(),
)

/** Serializes the protection scan to its durable `type:"veteran_protection"` record. Every value the
 * reader must not confuse for a positive result (an UNKNOWN population, a non-COMPLETE outcome) is
 * written verbatim rather than defaulted. */
fun serializeVeteranProtectionScan(s: VeteranProtectionScan): JSONObject =
    JSONObject().apply {
        put("type", "veteran_protection")
        put("schemaVersion", s.schemaVersion)
        s.rosterBindingVersion?.let { put("rosterBindingVersion", it) }
        s.rosterScanId?.let { put("rosterScanId", it) }
        s.rosterDigest?.let { put("rosterDigest", it) }
        put("scanId", s.scanId)
        put("startedAt", s.startedAt)
        put("completedAt", s.completedAt)
        s.registeredUsed?.let { put("registeredUsed", it) }
        s.registeredCapacity?.let { put("registeredCapacity", it) }
        s.filtersOffConfirmed?.let { put("filtersOffConfirmed", it) }
        put("favoritePopulation", s.favoritePopulation.name.lowercase())
        put("favoriteApplyState", s.favoriteApplyState.name.lowercase())
        put("favoriteBaselineVerified", s.favoriteBaselineVerified)
        s.filterBaselineEvidenceVersion?.let { put("filterBaselineEvidenceVersion", it) }
        put("favoriteBaselineReadings", JSONObject(s.favoriteBaselineReadings))
        put("memoPopulation", s.memoPopulation.name.lowercase())
        put("memoApplyState", s.memoApplyState.name.lowercase())
        put("memoBaselineVerified", s.memoBaselineVerified)
        put("memoBaselineReadings", JSONObject(s.memoBaselineReadings))
        put("probeDiagnostics", JSONArray().apply { s.probeDiagnostics.forEach { put(it) } })
        put("enumerationPerformed", s.enumerationPerformed)
        put("favoritedFingerprints", JSONArray().apply { s.favoritedFingerprints.forEach { put(it) } })
        put("memoFingerprints", JSONArray().apply { s.memoFingerprints.forEach { put(it) } })
        put("restoredFiltersOff", s.restoredFiltersOff)
        put("outcome", s.outcome.name.lowercase())
        put("app", s.appVersion)
        put("screenWidth", s.screenWidth)
        put("screenHeight", s.screenHeight)
    }
