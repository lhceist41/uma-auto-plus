package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.ApplyButtonState
import com.steve1316.uma_android_automation.utils.VeteranFilterDimension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Veteran protection scan record")
class VeteranProtectionScanEventTest {
    @Test
    fun `only disabled target with enabled complementary probe proves empty`() {
        assertEquals(ProtectionPopulation.NONEMPTY, populationFromProvenFilters(ApplyButtonState.ENABLED))
        assertEquals(ProtectionPopulation.UNKNOWN, populationFromProvenFilters(ApplyButtonState.DISABLED))
        assertEquals(ProtectionPopulation.UNKNOWN, populationFromProvenFilters(ApplyButtonState.DISABLED, ApplyButtonState.DISABLED))
        assertEquals(ProtectionPopulation.UNKNOWN, populationFromProvenFilters(ApplyButtonState.DISABLED, ApplyButtonState.UNKNOWN))
        assertEquals(ProtectionPopulation.EMPTY, populationFromProvenFilters(ApplyButtonState.DISABLED, ApplyButtonState.ENABLED))
        assertEquals(ProtectionPopulation.UNKNOWN, populationFromProvenFilters(ApplyButtonState.UNKNOWN, ApplyButtonState.ENABLED))
    }

    @Test
    fun `a zero-favorite zero-memo probe serializes both populations as empty`() {
        val neutral = VeteranFilterDimension.entries.associate { it.name.lowercase() to "neutral" }
        val record =
            VeteranProtectionScan(
                schemaVersion = VETERAN_PROTECTION_SCHEMA_VERSION,
                scanId = "vp-1-abc",
                startedAt = 1000L,
                completedAt = 2000L,
                registeredUsed = 257,
                registeredCapacity = 260,
                filtersOffConfirmed = true,
                favoritePopulation = ProtectionPopulation.EMPTY,
                favoriteApplyState = ApplyButtonState.DISABLED,
                memoPopulation = ProtectionPopulation.EMPTY,
                memoApplyState = ApplyButtonState.DISABLED,
                enumerationPerformed = false,
                favoritedFingerprints = emptyList(),
                memoFingerprints = emptyList(),
                restoredFiltersOff = true,
                outcome = ProtectionScanOutcome.COMPLETE,
                appVersion = "1.3.8",
                screenWidth = 1080,
                screenHeight = 1920,
                favoriteBaselineVerified = true,
                memoBaselineVerified = true,
                filterBaselineEvidenceVersion = 1,
                favoriteBaselineReadings = neutral,
                memoBaselineReadings = neutral,
                probeDiagnostics = listOf("roster filters, count and sort restored"),
                rosterBindingVersion = 1,
                rosterScanId = "rs-1",
                rosterDigest = "a".repeat(32),
            )
        val json = serializeVeteranProtectionScan(record)
        assertEquals("veteran_protection", json.getString("type"))
        assertEquals(257, json.getInt("registeredUsed"))
        assertEquals(2, json.getInt("schemaVersion"))
        assertEquals(1, json.getInt("rosterBindingVersion"))
        assertEquals("rs-1", json.getString("rosterScanId"))
        assertEquals("a".repeat(32), json.getString("rosterDigest"))
        assertEquals("empty", json.getString("favoritePopulation"))
        assertEquals("empty", json.getString("memoPopulation"))
        assertEquals("disabled", json.getString("favoriteApplyState"))
        assertTrue(json.getBoolean("favoriteBaselineVerified"))
        assertTrue(json.getBoolean("memoBaselineVerified"))
        assertEquals(1, json.getInt("filterBaselineEvidenceVersion"))
        assertEquals(9, json.getJSONObject("favoriteBaselineReadings").length())
        assertEquals("neutral", json.getJSONObject("memoBaselineReadings").getString("common_sparks"))
        assertEquals(1, json.getJSONArray("probeDiagnostics").length())
        assertEquals("complete", json.getString("outcome"))
        assertTrue(json.getBoolean("restoredFiltersOff"))
        assertFalse(json.getBoolean("enumerationPerformed"))
        assertEquals(0, json.getJSONArray("favoritedFingerprints").length())
        assertEquals(0, json.getJSONArray("memoFingerprints").length())
    }

    @Test
    fun `either nonempty partition serializes a non-complete record with no member evidence`() {
        val base =
            VeteranProtectionScan(
                schemaVersion = VETERAN_PROTECTION_SCHEMA_VERSION,
                scanId = "vp-2-def",
                startedAt = 1000L,
                completedAt = 2000L,
                registeredUsed = 100,
                registeredCapacity = 260,
                filtersOffConfirmed = true,
                favoritePopulation = ProtectionPopulation.NONEMPTY,
                favoriteApplyState = ApplyButtonState.ENABLED,
                memoPopulation = ProtectionPopulation.EMPTY,
                memoApplyState = ApplyButtonState.DISABLED,
                enumerationPerformed = false,
                favoritedFingerprints = emptyList(),
                memoFingerprints = emptyList(),
                restoredFiltersOff = true,
                outcome = ProtectionScanOutcome.NONEMPTY_PARTITION_CENSUS_UNAVAILABLE,
                appVersion = "1.3.8",
                screenWidth = 1080,
                screenHeight = 1920,
            )
        for ((favorite, memo) in listOf(
            ProtectionPopulation.NONEMPTY to ProtectionPopulation.EMPTY,
            ProtectionPopulation.EMPTY to ProtectionPopulation.NONEMPTY,
            ProtectionPopulation.NONEMPTY to ProtectionPopulation.NONEMPTY,
        )) {
            val record = base.copy(
                favoritePopulation = favorite,
                favoriteApplyState = if (favorite == ProtectionPopulation.NONEMPTY) ApplyButtonState.ENABLED else ApplyButtonState.DISABLED,
                memoPopulation = memo,
                memoApplyState = if (memo == ProtectionPopulation.NONEMPTY) ApplyButtonState.ENABLED else ApplyButtonState.DISABLED,
            )
            val json = serializeVeteranProtectionScan(record)
            assertEquals("nonempty_partition_census_unavailable", json.getString("outcome"))
            assertFalse(json.getBoolean("enumerationPerformed"))
            assertEquals(0, json.getJSONArray("favoritedFingerprints").length())
            assertEquals(0, json.getJSONArray("memoFingerprints").length())
        }
    }

    @Test
    fun `a precondition failure omits nothing that would read as a positive result`() {
        val record =
            VeteranProtectionScan(
                schemaVersion = VETERAN_PROTECTION_SCHEMA_VERSION,
                scanId = "vp-3-ghi",
                startedAt = 1000L,
                completedAt = 1500L,
                registeredUsed = null,
                registeredCapacity = null,
                filtersOffConfirmed = null,
                favoritePopulation = ProtectionPopulation.UNKNOWN,
                favoriteApplyState = ApplyButtonState.UNKNOWN,
                memoPopulation = ProtectionPopulation.UNKNOWN,
                memoApplyState = ApplyButtonState.UNKNOWN,
                enumerationPerformed = false,
                favoritedFingerprints = emptyList(),
                memoFingerprints = emptyList(),
                restoredFiltersOff = true,
                outcome = ProtectionScanOutcome.PRECONDITION_FAILED,
                appVersion = "1.3.8",
                screenWidth = 1080,
                screenHeight = 1920,
            )
        val json = serializeVeteranProtectionScan(record)
        assertEquals("unknown", json.getString("favoritePopulation"))
        assertFalse(json.getBoolean("favoriteBaselineVerified"))
        assertFalse(json.getBoolean("memoBaselineVerified"))
        assertEquals("precondition_failed", json.getString("outcome"))
        assertFalse(json.has("registeredUsed"), "an unread count is omitted, not written as 0")
        assertFalse(json.has("rosterDigest"), "a failed scan has no usable binding")
        assertFalse(json.has("filterBaselineEvidenceVersion"), "a pre-tap failure has no baseline proof")
    }
}
