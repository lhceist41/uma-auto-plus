package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.ApplyButtonState
import com.steve1316.uma_android_automation.utils.FilterDimensionState
import com.steve1316.uma_android_automation.utils.VeteranFilterDimension
import com.steve1316.uma_android_automation.utils.VeteranFilterDimensionRead
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * PL-R2a protection-probe safety.
 *
 * Unlike the roster walk, this probe legitimately taps view controls inside the Display Settings
 * dialog (checkboxes, Reset Filters, OK, Cancel) and swipes to scroll, so the guarantees are
 * different: it must never touch a control that changes account state (Transfer, Batch Favorite, the
 * detail favorite marker, career start); it must read the OK-enabled signal on the common path
 * WITHOUT applying a filter; and it must always leave the roster back on Filters: OFF, recording
 * RESTORE_FAILED when it cannot prove that.
 */
@DisplayName("Veteran protection probe safety")
class VeteranProtectionScannerSafetyTest {
    private val key = "debugMode_startVeteranProtectionScanTest"

    private val scanner by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/VeteranProtectionScanner.kt") }
    private val campaign by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt") }
    private val debugUi by lazy { source("src/pages/DebugSettings/index.tsx") }
    private val settingsContext by lazy { source("src/context/BotStateContext.tsx") }
    private val searchConfig by lazy { source("src/data/searchConfig.ts") }

    /** Source with comments stripped, so the forbidden-name guards see what the code CALLS, not what
     * the doc comment names (it deliberately names Transfer/Batch Favorite as controls it avoids). */
    private fun codeOnly(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//.*"), "")

    @Nested
    @DisplayName("registry + routing")
    inner class Registry {
        @Test
        fun `the probe key is in the canonical DebugTestGate registry`() {
            assertTrue(DebugTestGate.ALL_KEYS.contains(key))
        }

        @Test
        fun `Campaign startTests routes the key to the probe handler`() {
            assertTrue(campaign.contains("\"$key\" to ::startVeteranProtectionScanTest"), "the fnMap routes the probe key")
            val handler = campaign.substring(campaign.indexOf("open fun startVeteranProtectionScanTest("))
            assertTrue(handler.contains("VeteranProtectionScanner(game).runScan()"), "the handler invokes the scanner")
        }

        @Test
        fun `the toggle is wired through interface, default, UI, and search`() {
            assertTrue(settingsContext.contains("debugMode_startVeteranProtectionScanTest: boolean"), "declared in the Settings interface")
            assertTrue(settingsContext.contains("debugMode_startVeteranProtectionScanTest: false,"), "defaults OFF")
            assertTrue(debugUi.contains("\"$key\""), "the key is in the Debug Settings debugTestKeys list")
            assertTrue(debugUi.contains("Start Veteran Protection Probe"), "the toggle has a user-facing label")
            assertTrue(searchConfig.contains("\"debug-veteran-protection-scan-test\""), "registered in the settings search index")
        }
    }

    @Nested
    @DisplayName("no account-state control is reachable")
    inner class NoMutation {
        @Test
        fun `the probe never names a transfer, batch-favorite, career-start, or detail-favorite control`() {
            val rosterScanner = source("android/app/src/main/java/com/steve1316/uma_android_automation/VeteranRosterScanner.kt")
            for (code in listOf(codeOnly(scanner), codeOnly(rosterScanner))) {
                for (forbidden in listOf("Transfer", "BatchFavorite", "Batch Favorite", "StartCareer", "ButtonStartCareer", "CareerLaunchNavigator", "DETAIL_FAVORITE")) {
                    assertFalse(code.contains(forbidden), "the probe and roster walk must not reference $forbidden")
                }
            }
        }

        @Test
        fun `the delegated roster walk and the protection probe write only their evidence streams`() {
            assertTrue(scanner.contains("OutcomeCorpus.VETERAN_PROTECTION_PATH"), "the probe writes to its own append-only stream")
            assertTrue(scanner.contains("VeteranRosterScanner(game).runScan(0)"), "the probe delegates a full roster walk")
            assertTrue(source("android/app/src/main/java/com/steve1316/uma_android_automation/VeteranRosterScanner.kt").contains("OutcomeCorpus.ROSTER_SCAN_PATH"), "the delegated walk persists roster evidence")
            for (other in listOf("CORPUS_PATH", "DECISIONS_PATH", "CAREER_STATE_PATH", "LINEAGE_PATH", "SHADOW_ADVISOR_PATH", "VETERAN_INSPIRATION_PATH")) {
                assertFalse(scanner.contains("OutcomeCorpus.$other"), "the probe must not write into $other")
            }
        }
    }

    @Nested
    @DisplayName("the probe reads OK-enabled without applying a filter")
    inner class ProbeDoesNotApply {
        @Test
        fun `probePartition never taps OK`() {
            // Applying a filter (tapping OK) belongs only to the enumeration and restore paths. The
            // common probe path sets a partition, reads the greyed-out OK, and leaves it un-applied.
            val body = scanner.substring(scanner.indexOf("private fun probePartition("), scanner.indexOf("private fun setPartition("))
            assertFalse(body.contains("DIALOG_OK_X"), "probePartition must not tap OK")
            assertTrue(body.contains("classifyApplyButton("), "probePartition reads the OK-enabled state")
        }

        @Test
        fun `the clean exit is Cancel, not OK`() {
            assertTrue(scanner.contains("cancelDialog()"), "the probe leaves through Cancel on the normal path")
            val cancel = scanner.substring(scanner.indexOf("private fun cancelDialog("), scanner.indexOf("private fun cancelDialog(") + 400)
            assertTrue(cancel.contains("DIALOG_CANCEL_X"), "cancelDialog taps Cancel")
        }
    }

    @Nested
    @DisplayName("preconditions fail closed before protection filter gestures")
    inner class Preconditions {
        private fun beforeFirstGesture(): String = scanner.substring(0, scanner.indexOf("openDialogToFilter()"))

        @Test
        fun `the roster list, the Registered count and Filters OFF are asserted before protection filter taps`() {
            val head = beforeFirstGesture()
            assertTrue(head.contains("listScreen.kind != RosterScreenKind.ROSTER_LIST"), "the roster list is required")
            assertTrue(head.contains("rosterListBindingStable(roster.header.list, list)"), "the post-walk list and sort identity must still agree")
            assertTrue(head.contains("rosterBindingDigest(roster)"), "the protection probe computes its binding from the completed roster walk")
            assertEquals(3, Regex("ProtectionScanOutcome\\.PRECONDITION_FAILED").findAll(head).count(), "binding and both screen preconditions record failure")
        }

        @Test
        fun `the Display Settings dialog is asserted before every mutation phase`() {
            assertTrue(scanner.contains("private fun requireDialog("), "there is a dialog assertion")
            assertTrue(scanner.contains("throw ProbeAbort(ProtectionScanOutcome.UI_UNEXPECTED"), "a wrong frame aborts")
            // Every phase that taps checkboxes/Reset re-asserts the dialog first.
            assertTrue(scanner.contains("requireDialog(\"before setting the \$label partition\")"), "probing asserts the dialog")
            assertTrue(scanner.contains("requireDialog(\"before Reset Filters\")"), "Reset asserts the dialog")
            assertTrue(scanner.contains("requireDialog(\"before Cancel\")"), "Cancel asserts the dialog")
        }
    }

    @Nested
    @DisplayName("exact empty proof and nonempty fail-closed outcome")
    inner class PartitionVerify {
        @Test
        fun `a bounded overlapping traversal reads every section before any empty claim`() {
            assertTrue(scanner.contains("repeat(8) { step ->"))
            assertTrue(scanner.contains("if (a != b) throw ProbeAbort"), "moving or inconsistent captures abort")
            assertTrue(scanner.contains("previous.intersect(visible).isEmpty()"), "adjacent scroll positions overlap")
            val traversal = scanner.substring(scanner.indexOf("internal fun <T> scanProtectionFilterDialog("), scanner.indexOf("internal class ProbeAbort"))
            assertTrue(traversal.contains("val aggregation = VeteranFilterViewportAggregation()"), "the traversal owns one production viewport aggregation seam")
            assertTrue(traversal.contains("aggregation.add(frame)"), "every stable viewport enters the production aggregation seam")
            assertFalse(traversal.contains("mergeVeteranFilterDimensionReads"), "the traversal must not keep a second manual merge")
            assertTrue(scanner.contains("section transition \${pair.first} to \${pair.second} unseen"), "missing sections abort")
            assertTrue(scanner.contains("commonRows.any { it > 1 }"), "extra Common rows cannot be neutral")
            assertTrue(scanner.contains("it.dimension == VeteranFilterDimension.TRACK && it.y in 325..342"), "absolute top is anchored")
        }

        @Test
        fun `partition verification still requires exact selection and a positive control`() {
            assertTrue(scanner.contains("requireNeutralFilterBaseline()"))
            assertTrue(scanner.contains("baseline != FilterBaselineState.NEUTRAL_VERIFIED"))
            assertTrue(scanner.contains("setPartition(desiredSelected)"))
            assertTrue(scanner.contains("classifyFilterCheckbox("))
            assertTrue(scanner.contains("FAVORITE_NOT_SET_CHECKBOX"))
            assertTrue(scanner.contains("MEMO_NO_CHECKBOX"))
            assertTrue(scanner.contains("val pass = scanFilterDialog()"))
            assertTrue(scanner.contains("exactFilterTargetState(readings.mapValues { it.value.state }, target, errors.isNotEmpty())"))
            assertTrue(scanner.contains("exactBottomSelection(it, desiredSelected)"))
        }

        @Test
        fun `favorite target and positive control acquire independent fresh reset proofs`() {
            assertTrue(scanner.contains("resetAndRead = ::resetFilters"), "the device adapter always resets and re-reads")
            val events = runFreshnessSequence()
            assertEquals(
                listOf(
                    "reset:FAVORITE_TARGET:1",
                    "probe:FAVORITE_TARGET:1",
                    "reset:FAVORITE_POSITIVE_CONTROL:2",
                    "probe:FAVORITE_POSITIVE_CONTROL:2",
                ),
                events.take(4),
            )
        }

        @Test
        fun `memo target and positive control acquire proofs independent of favorite work`() {
            val events = runFreshnessSequence()
            assertEquals(
                listOf(
                    "reset:MEMO_TARGET:3",
                    "probe:MEMO_TARGET:3",
                    "reset:MEMO_POSITIVE_CONTROL:4",
                    "probe:MEMO_POSITIVE_CONTROL:4",
                ),
                events.drop(4),
            )
        }

        @Test
        fun `baseline proof is rejected after mutation and for a different probe stage`() {
            val readings = proofReadings(1)
            val favorite = ProtectionFilterBaselineProof.acquire(ProtectionFilterProbeStage.FAVORITE_TARGET) { readings }
            assertEquals(readings, favorite.beforeMutation(ProtectionFilterProbeStage.FAVORITE_TARGET) { it })
            assertThrows(IllegalStateException::class.java) {
                favorite.beforeMutation(ProtectionFilterProbeStage.FAVORITE_TARGET) { it }
            }

            val wrongStage = ProtectionFilterBaselineProof.acquire(ProtectionFilterProbeStage.FAVORITE_TARGET) { proofReadings(2) }
            assertThrows(IllegalStateException::class.java) {
                wrongStage.beforeMutation(ProtectionFilterProbeStage.MEMO_TARGET) { it }
            }
        }

        @Test
        fun `either nonempty partition prevents complete evidence and member arrays stay empty`() {
            assertTrue(scanner.contains("favoritePop == ProtectionPopulation.NONEMPTY || memoPop == ProtectionPopulation.NONEMPTY"))
            assertTrue(scanner.contains("ProtectionScanOutcome.NONEMPTY_PARTITION_CENSUS_UNAVAILABLE"))
            assertTrue(scanner.contains("favoritedFingerprints = emptyList()"))
            assertTrue(scanner.contains("memoFingerprints = emptyList()"))
            assertTrue(scanner.contains("enumerationPerformed = false"))
            assertFalse(scanner.contains("walkFilteredFingerprints("))
            assertFalse(scanner.contains("ChevronState.DISABLED"))
        }
    }

    @Nested
    @DisplayName("the roster is always restored to Filters OFF")
    inner class Restore {
        @Test
        fun `the probe dismisses the dialog and checks the roster in finally`() {
            val body = scanner.substring(scanner.indexOf("var dialogMayBeOpen"), scanner.indexOf("val record ="))
            assertTrue(body.contains("} finally {"))
            assertTrue(body.contains("if (dialogMayBeOpen) bestEffortDismissDialog()"))
            assertTrue(body.contains("verifyRosterFiltersOff(list)"))
        }

        @Test
        fun `the run verifies Filters OFF afterwards and downgrades to RESTORE_FAILED when it cannot`() {
            assertTrue(scanner.contains("verifyRosterFiltersOff("), "the run re-reads the roster to prove restoration")
            assertTrue(scanner.contains("rosterListBindingStable(before, after)"), "restoration is proven by re-reading Filters OFF and count")
            assertTrue(scanner.contains("ProtectionScanOutcome.RESTORE_FAILED"), "an unproven restore is recorded, never hidden")
            assertTrue(scanner.contains("val record = restoreProtectionScan("), "the persisted record uses the restoration decision")
        }
    }

    private fun runFreshnessSequence(): List<String> {
        val events = mutableListOf<String>()
        var proofId = 0
        runProtectionFilterProbeSequence(
            resetAndRead = {
                val stage = ProtectionFilterProbeStage.entries[proofId]
                val id = ++proofId
                events += "reset:$stage:$id"
                proofReadings(id)
            },
            probe = { stage, readings ->
                val id = readings.getValue(VeteranFilterDimension.TRACK).reasons.single().removePrefix("proof-")
                events += "probe:$stage:$id"
                when (stage) {
                    ProtectionFilterProbeStage.FAVORITE_TARGET,
                    ProtectionFilterProbeStage.MEMO_TARGET,
                    -> ApplyButtonState.DISABLED
                    ProtectionFilterProbeStage.FAVORITE_POSITIVE_CONTROL,
                    ProtectionFilterProbeStage.MEMO_POSITIVE_CONTROL,
                    -> ApplyButtonState.ENABLED
                }
            },
        )
        return events
    }

    private fun proofReadings(id: Int) =
        mapOf(
            VeteranFilterDimension.TRACK to
                VeteranFilterDimensionRead(
                    FilterDimensionState.NEUTRAL,
                    listOf("proof-$id"),
                ),
        )

    private fun source(relative: String): String = repoFile(relative).readText().replace("\r\n", "\n")

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val f = File(dir, relative)
            if (f.isFile) return f
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${System.getProperty("user.dir")}")
    }
}
