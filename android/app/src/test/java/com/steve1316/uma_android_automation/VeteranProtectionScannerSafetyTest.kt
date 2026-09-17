package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        private fun beforeFirstGesture(): String = scanner.substring(0, scanner.indexOf("openDialogToFilterBottom()"))

        @Test
        fun `the roster list, the Registered count and Filters OFF are asserted before protection filter taps`() {
            val head = beforeFirstGesture()
            assertTrue(head.contains("listScreen.kind != RosterScreenKind.ROSTER_LIST"), "the roster list is required")
            assertTrue(head.contains("list.registeredUsed != roster.header.list.registeredUsed || list.filtersOff != true"), "the post-walk count and filter state must still agree")
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
    @DisplayName("the partition is verified, not assumed")
    inner class PartitionVerify {
        @Test
        fun `nonempty enumeration requires bound unique identities and disabled chevron termination`() {
            val walk = scanner.substring(scanner.indexOf("private fun walkFilteredFingerprints("), scanner.indexOf("private fun restoreFiltersOffFromRoster("))
            assertTrue(walk.contains("?: throw ProbeAbort"), "an unread row aborts")
            assertTrue(walk.contains("fp !in boundFingerprints || fp in fingerprints"), "foreign and repeated identities abort")
            assertTrue(walk.contains("chevron == ChevronState.DISABLED) break"), "only a disabled chevron proves the end")
            assertTrue(walk.contains("chevron != ChevronState.ENABLED"), "an unread chevron aborts")
            assertTrue(walk.contains("fingerprints.size >= boundFingerprints.size"), "the bound roster limits traversal")
            assertFalse(walk.contains("fingerprints.distinct()"), "duplicates cannot be silently dropped")
        }

        @Test
        fun `setPartition re-checks the checkbox states and retries`() {
            assertTrue(scanner.contains("PARTITION_SET_ROUNDS"), "the setter retries over several rounds")
            assertTrue(scanner.contains("classifyFilterCheckbox("), "it verifies each checkbox state")
            assertTrue(scanner.contains("ProtectionScanOutcome.PARTITION_SET_FAILED"), "an unconvergeable partition aborts")
        }

        @Test
        fun `a fresh dialog must show every checkbox unselected before the probe trusts its geometry`() {
            assertTrue(scanner.contains("private fun requireBaselineUnselected("), "there is a baseline sanity check")
            assertTrue(scanner.contains("requireBaselineUnselected()"), "the baseline is asserted after opening the dialog")
        }
    }

    @Nested
    @DisplayName("the roster is always restored to Filters OFF")
    inner class Restore {
        @Test
        fun `enumeration restores the filter in a finally`() {
            val body = scanner.substring(scanner.indexOf("private fun enumeratePartition("), scanner.indexOf("private fun walkFilteredFingerprints("))
            assertTrue(body.contains("} finally {"), "the applied filter is cleared in a finally")
            assertTrue(body.contains("restoreFiltersOffFromRoster()"), "the finally reopens the dialog and clears the filter")
        }

        @Test
        fun `the run verifies Filters OFF afterwards and downgrades to RESTORE_FAILED when it cannot`() {
            assertTrue(scanner.contains("verifyRosterFiltersOff("), "the run re-reads the roster to prove restoration")
            assertTrue(scanner.contains("after.filtersOff == true"), "restoration is proven by re-reading Filters OFF")
            assertTrue(scanner.contains("ProtectionScanOutcome.RESTORE_FAILED"), "an unproven restore is recorded, never hidden")
        }
    }

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
