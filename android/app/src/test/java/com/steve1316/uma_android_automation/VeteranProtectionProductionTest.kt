package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.bot.ProtectionPopulation
import com.steve1316.uma_android_automation.bot.ProtectionScanOutcome
import com.steve1316.uma_android_automation.bot.RosterListState
import com.steve1316.uma_android_automation.bot.VeteranProtectionScan
import com.steve1316.uma_android_automation.utils.ApplyButtonState
import com.steve1316.uma_android_automation.utils.FilterBaselineState
import com.steve1316.uma_android_automation.utils.FilterDimensionState
import com.steve1316.uma_android_automation.utils.VeteranFilterAnchor
import com.steve1316.uma_android_automation.utils.VeteranFilterDimension
import com.steve1316.uma_android_automation.utils.VeteranFilterDimension.*
import com.steve1316.uma_android_automation.utils.VeteranFilterDimensionRead
import com.steve1316.uma_android_automation.utils.VeteranFilterFrameRead
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VeteranProtectionProductionTest {
    private fun read(state: FilterDimensionState) = VeteranFilterDimensionRead(state)

    private fun traversal(dimension: VeteranFilterDimension, state: FilterDimensionState): FilterPass<Int> {
        val anchors = listOf(
            listOf(TRACK to 333, DISTANCE to 544, STYLE to 869, ATTRIBUTE_SPARKS to 1194),
            listOf(STYLE to 333, ATTRIBUTE_SPARKS to 658, APTITUDE_SPARKS to 1279),
            listOf(APTITUDE_SPARKS to 333, UNIQUE_SPARKS to 1181),
            listOf(UNIQUE_SPARKS to 333, COMMON_SPARKS to 838, FAVORITES to 1088),
            listOf(COMMON_SPARKS to 350, FAVORITES to 600, MEMO to 1381),
            listOf(COMMON_SPARKS to 385, FAVORITES to 635, MEMO to 1416),
        )
        val completed = listOf(
            listOf(TRACK, DISTANCE, STYLE),
            listOf(STYLE, ATTRIBUTE_SPARKS),
            listOf(APTITUDE_SPARKS),
            listOf(UNIQUE_SPARKS, COMMON_SPARKS),
            listOf(COMMON_SPARKS, FAVORITES),
            listOf(COMMON_SPARKS, FAVORITES, MEMO),
        )
        val frames = anchors.mapIndexed { index, visible ->
            VeteranFilterFrameRead(
                visible.map { (d, y) -> VeteranFilterAnchor(d, y) },
                completed[index].associateWith { d -> read(if (index < 5 && d == dimension) state else FilterDimensionState.NEUTRAL) },
                emptyList(),
            )
        }
        var position = 0
        var topReads = 0
        var captures = 0
        val result = scanProtectionFilterDialog(
            requireDialog = {},
            scrollFilterListToTop = { topReads++ },
            stableFilterFrame = { captures++; Triple(position, position, frames[position]) },
            scrollFilterListDown = { position++ },
        )
        assertEquals(1, topReads)
        assertEquals(6, captures)
        assertEquals(5, position)
        assertTrue(result.errors.isEmpty(), result.errors.toString())
        assertEquals(VeteranFilterDimension.entries.toSet(), result.readings.keys)
        assertEquals(5 to 5, result.bottomFrames)
        return result
    }

    @Test
    fun `scanner return preserves earlier active and unknown evidence across final neutral viewport`() {
        for (dimension in listOf(COMMON_SPARKS, FAVORITES)) {
            for (state in listOf(FilterDimensionState.ACTIVE, FilterDimensionState.UNKNOWN)) {
                val result = traversal(dimension, state)
                assertEquals(state, result.readings.getValue(dimension).state, "$dimension $state must survive")
                assertNotEquals(FilterBaselineState.NEUTRAL_VERIFIED, result.baseline)
            }
        }
    }

    @Test
    fun `complete neutral traversal remains usable`() {
        assertEquals(FilterBaselineState.NEUTRAL_VERIFIED, traversal(COMMON_SPARKS, FilterDimensionState.NEUTRAL).baseline)
    }

    @Test
    fun `favorite and memo reject unrelated active or unknown filters before reading disabled OK`() {
        for (target in listOf(FAVORITES, MEMO)) {
            for (unrelated in listOf(FilterDimensionState.ACTIVE, FilterDimensionState.UNKNOWN)) {
                var applyReads = 0
                val readings = VeteranFilterDimension.entries.associateWith { d ->
                    read(when (d) {
                        target -> FilterDimensionState.ACTIVE
                        DISTANCE -> unrelated
                        else -> FilterDimensionState.NEUTRAL
                    })
                }
                val abort = assertThrows(ProbeAbort::class.java) {
                    protectionTargetApplyState(
                        target.name, readings, target, emptyList(),
                        exactBottomSelection = { true },
                        readApplyStates = { applyReads++; listOf(ApplyButtonState.DISABLED, ApplyButtonState.DISABLED) },
                    )
                }
                assertEquals(ProtectionScanOutcome.PARTITION_SET_FAILED, abort.outcome)
                assertEquals(0, applyReads)
            }
        }
    }

    @Test
    fun `exact target and unrelated neutrality permit a settled apply read`() {
        for (target in listOf(FAVORITES, MEMO)) {
            val readings = VeteranFilterDimension.entries.associateWith { read(if (it == target) FilterDimensionState.ACTIVE else FilterDimensionState.NEUTRAL) }
            assertEquals(
                ApplyButtonState.DISABLED,
                protectionTargetApplyState(
                    target.name, readings, target, emptyList(),
                    exactBottomSelection = { true },
                    readApplyStates = { listOf(ApplyButtonState.DISABLED, ApplyButtonState.DISABLED) },
                ),
            )
            assertThrows(ProbeAbort::class.java) {
                protectionTargetApplyState(target.name, readings, target, emptyList(), { false }, { error("must not read OK") })
            }
        }
    }

    private val before = RosterListState(10, 200, true, "registered", "descending")

    private fun emptyScan() = VeteranProtectionScan(
        schemaVersion = 2, scanId = "proof", startedAt = 1, completedAt = 2,
        registeredUsed = 10, registeredCapacity = 200, filtersOffConfirmed = true,
        favoritePopulation = ProtectionPopulation.EMPTY, favoriteApplyState = ApplyButtonState.DISABLED,
        memoPopulation = ProtectionPopulation.EMPTY, memoApplyState = ApplyButtonState.DISABLED,
        favoriteBaselineVerified = true, memoBaselineVerified = true,
        enumerationPerformed = false, favoritedFingerprints = emptyList(), memoFingerprints = emptyList(),
        restoredFiltersOff = false, outcome = ProtectionScanOutcome.COMPLETE,
        appVersion = "test", screenWidth = 1080, screenHeight = 1920,
    )

    @Test
    fun `failed count capacity filters or sort restoration invalidates both persisted partitions`() {
        val invalid = listOf(
            before.copy(registeredUsed = 9), before.copy(registeredCapacity = 201),
            before.copy(filtersOff = false), before.copy(filtersOff = null),
            before.copy(sortKey = "rating"), before.copy(sortKey = null),
            before.copy(sortDirection = "ascending"), before.copy(sortDirection = null), null,
        )
        for (after in invalid) {
            val restored = protectionRosterRestored(before, after)
            assertFalse(restored, "must reject $after")
            val result = restoreProtectionScan(emptyScan(), restored)
            assertFalse(result.restoredFiltersOff)
            assertEquals(ProtectionScanOutcome.RESTORE_FAILED, result.outcome)
            assertEquals(ProtectionPopulation.UNKNOWN, result.favoritePopulation)
            assertEquals(ProtectionPopulation.UNKNOWN, result.memoPopulation)
            assertFalse(result.favoriteBaselineVerified)
            assertFalse(result.memoBaselineVerified)
        }
    }

    @Test
    fun `unchanged roster restoration preserves verified empty partitions`() {
        val result = restoreProtectionScan(emptyScan(), protectionRosterRestored(before, before.copy()))
        assertTrue(result.restoredFiltersOff)
        assertEquals(ProtectionScanOutcome.COMPLETE, result.outcome)
        assertEquals(ProtectionPopulation.EMPTY, result.favoritePopulation)
        assertEquals(ProtectionPopulation.EMPTY, result.memoPopulation)
        assertTrue(result.favoriteBaselineVerified)
        assertTrue(result.memoBaselineVerified)
    }
}
