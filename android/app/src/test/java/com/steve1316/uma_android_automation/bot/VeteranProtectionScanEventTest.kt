package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.ApplyButtonState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Veteran protection scan record")
class VeteranProtectionScanEventTest {
    @Test
    fun `population maps from the OK-button reading`() {
        assertEquals(ProtectionPopulation.NONEMPTY, populationFromApply(ApplyButtonState.ENABLED))
        assertEquals(ProtectionPopulation.EMPTY, populationFromApply(ApplyButtonState.DISABLED))
        assertEquals(ProtectionPopulation.UNKNOWN, populationFromApply(ApplyButtonState.UNKNOWN))
    }

    @Test
    fun `a zero-favorite zero-memo probe serializes both populations as empty`() {
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
            )
        val json = serializeVeteranProtectionScan(record)
        assertEquals("veteran_protection", json.getString("type"))
        assertEquals(257, json.getInt("registeredUsed"))
        assertEquals("empty", json.getString("favoritePopulation"))
        assertEquals("empty", json.getString("memoPopulation"))
        assertEquals("disabled", json.getString("favoriteApplyState"))
        assertEquals("complete", json.getString("outcome"))
        assertTrue(json.getBoolean("restoredFiltersOff"))
        assertFalse(json.getBoolean("enumerationPerformed"))
        assertEquals(0, json.getJSONArray("favoritedFingerprints").length())
        assertEquals(0, json.getJSONArray("memoFingerprints").length())
    }

    @Test
    fun `an enumerated non-empty favorite partition serializes its fingerprints`() {
        val record =
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
                enumerationPerformed = true,
                favoritedFingerprints = listOf("fp-a", "fp-b"),
                memoFingerprints = emptyList(),
                restoredFiltersOff = true,
                outcome = ProtectionScanOutcome.COMPLETE,
                appVersion = "1.3.8",
                screenWidth = 1080,
                screenHeight = 1920,
            )
        val json = serializeVeteranProtectionScan(record)
        assertEquals("nonempty", json.getString("favoritePopulation"))
        assertTrue(json.getBoolean("enumerationPerformed"))
        assertEquals(2, json.getJSONArray("favoritedFingerprints").length())
        assertEquals("fp-a", json.getJSONArray("favoritedFingerprints").getString(0))
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
        assertEquals("precondition_failed", json.getString("outcome"))
        assertFalse(json.has("registeredUsed"), "an unread count is omitted, not written as 0")
    }
}
