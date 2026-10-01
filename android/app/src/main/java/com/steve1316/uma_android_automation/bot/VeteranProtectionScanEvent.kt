package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.ApplyButtonState
import org.json.JSONArray
import org.json.JSONObject

/**
 * The read-only Veteran protection record (`type:"veteran_protection"`): one row per filter-partition probe.
 * Protection is derived, never read: only a favorite icon or a memo blocks a release, and the probe infers each
 * marker's account-wide population from the game's "OK disabled when the selection is empty" behaviour. Only
 * two positively empty partitions can yield COMPLETE.
 */
const val VETERAN_PROTECTION_SCHEMA_VERSION: Int = 2

enum class ProtectionPopulation { EMPTY, NONEMPTY, UNKNOWN }

/** Only COMPLETE is trustworthy; every other value keeps the derived protection UNKNOWN. */
enum class ProtectionScanOutcome {
    COMPLETE,

    NONEMPTY_PARTITION_CENSUS_UNAVAILABLE,

    PRECONDITION_FAILED,

    /** The probe stops where it is. */
    UI_UNEXPECTED,

    /** Read nothing from the partition. */
    PARTITION_SET_FAILED,

    RESTORE_FAILED,
}

/** Called only after the target and unrelated filters were positively reread; an empty target also needs a fresh
 * baseline and an enabled complementary probe. */
fun populationFromProvenFilters(state: ApplyButtonState, complementary: ApplyButtonState? = null): ProtectionPopulation =
    when (state) {
        ApplyButtonState.ENABLED -> ProtectionPopulation.NONEMPTY
        ApplyButtonState.DISABLED -> if (complementary == ApplyButtonState.ENABLED) ProtectionPopulation.EMPTY else ProtectionPopulation.UNKNOWN
        ApplyButtonState.UNKNOWN -> ProtectionPopulation.UNKNOWN
    }

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

/** UNKNOWN populations and non-COMPLETE outcomes are written verbatim, never defaulted. */
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
