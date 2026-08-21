package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Veteran roster enumerator safety.
 *
 * The walk runs on a screen that also carries `Transfer` - an irreversible removal of Veterans from
 * the account - plus Batch Favorite, the favorite marker, share, Change (outfit) and the epithet
 * pencil. It drives real taps, so the load-bearing invariants cannot be proven by running it: they
 * are proven by pinning the coordinates it may use (`VeteranRosterProbesTest` shows none of them can
 * jitter into a deny zone), by requiring every tap to pass the runtime deny check, and by these
 * source guards showing no mutating control is even referenced from the scanner.
 */
@DisplayName("Veteran roster enumerator safety")
class VeteranRosterScannerSafetyTest {
    private val key = "debugMode_startVeteranRosterScanTest"

    private val scanner by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/VeteranRosterScanner.kt") }
    private val reader by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/VeteranRosterReader.kt") }

    /** Source with comments stripped. The forbidden-name guards below must see what the code CALLS,
     * not what its documentation names: the scanner's own doc comment deliberately lists Transfer and
     * Batch Favorite as the controls it exists to stay away from. */
    private fun codeOnly(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//.*"), "")

    private val campaign by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt") }
    private val debugUi by lazy { source("src/pages/DebugSettings/index.tsx") }
    private val settingsContext by lazy { source("src/context/BotStateContext.tsx") }
    private val searchConfig by lazy { source("src/data/searchConfig.ts") }

    @Nested
    @DisplayName("registry + routing")
    inner class Registry {
        @Test
        fun `the scan key is in the canonical DebugTestGate registry`() {
            assertTrue(DebugTestGate.ALL_KEYS.contains(key))
        }

        @Test
        fun `Campaign startTests routes the key to the scan handler`() {
            assertTrue(campaign.contains("\"$key\" to ::startVeteranRosterScanTest"), "the fnMap routes the scan key")
            val handler = campaign.substring(campaign.indexOf("open fun startVeteranRosterScanTest("))
            assertTrue(handler.contains("VeteranRosterScanner(game).runScan("), "the handler invokes the scanner")
            assertTrue(handler.contains("getIntSetting(\"debug\", \"veteranRosterScanLimit\""), "the handler reads the operator's entry limit")
        }

        @Test
        fun `the entry-limit setting is wired through all five steps`() {
            // Interface, default, UI control, search registration, Kotlin read. Skipping any one of
            // these fails silently, which is exactly why this is pinned rather than eyeballed.
            assertTrue(settingsContext.contains("veteranRosterScanLimit: number"), "declared in the Settings interface")
            assertTrue(settingsContext.contains("veteranRosterScanLimit: 5,"), "has a default")
            assertTrue(debugUi.contains("veteranRosterScanLimit: value"), "has a Debug Settings control")
            assertTrue(searchConfig.contains("\"veteran-roster-scan-limit\""), "registered in the settings search index")
            assertTrue(campaign.contains("\"veteranRosterScanLimit\""), "read on the Kotlin side")
        }

        @Test
        fun `the scan toggle is discoverable in the UI and the search index`() {
            assertTrue(debugUi.contains("\"$key\""), "the key is in the Debug Settings debugTestKeys list")
            assertTrue(debugUi.contains("Start Veteran Roster Scan"), "the toggle has a user-facing label")
            assertTrue(searchConfig.contains("\"debug-veteran-roster-scan-test\""), "the toggle is registered in the settings search index")
        }
    }

    @Nested
    @DisplayName("no mutating control is reachable")
    inner class NoMutation {
        @Test
        fun `the scanner never names a transfer, favorite, memo, outfit, epithet or share control`() {
            val code = codeOnly(scanner)
            for (forbidden in listOf("Transfer", "BatchFavorite", "Batch Favorite", "Memo", "ChangeOutfit", "Epithet", "Share", "EditTeam", "Edit Team")) {
                assertFalse(code.contains(forbidden), "the enumerator must not reference $forbidden")
            }
        }

        @Test
        fun `the scanner never reaches career start, display settings, or the filter controls`() {
            val code = codeOnly(scanner)
            for (forbidden in listOf("ButtonStartCareer", "StartCareer", "DisplaySettings", "Display Settings", "ResetFilters", "Reset Filters", "CareerLaunchNavigator")) {
                assertFalse(code.contains(forbidden), "the enumerator must not reference $forbidden")
            }
        }

        @Test
        fun `the read-only field reader dispatches no gesture at all`() {
            val code = codeOnly(reader)
            for (forbidden in listOf("tapCoordinate(", "gestureUtils", "dispatchGesture", ".swipe(", "CoordinateTap")) {
                assertFalse(code.contains(forbidden), "VeteranRosterReader must stay zero-gesture; found $forbidden")
            }
        }
    }

    @Nested
    @DisplayName("bounded navigation only")
    inner class BoundedNavigation {
        @Test
        fun `every tap goes through the deny-checked helper`() {
            // The one place a gesture leaves this class. If a raw tapCoordinate ever appears outside
            // safeTap, a coordinate could reach the accessibility service without the deny check.
            assertTrue(scanner.contains("private fun safeTap("), "taps are funnelled through safeTap")
            assertTrue(scanner.contains("deniedZoneAt(screen, x, y)"), "safeTap consults the deny list")
            assertTrue(scanner.contains("throw DeniedTapException("), "a denied coordinate aborts instead of tapping")
            assertEquals(1, Regex("game\\.tapCoordinate\\(").findAll(scanner).count(), "exactly one tapCoordinate call, inside safeTap")
        }

        @Test
        fun `the only tapped coordinates are the first card, the next chevron and Close`() {
            val tapped = Regex("safeTap\\([^,]+, ([A-Z_]+), ([A-Z_]+),").findAll(scanner).map { it.groupValues[1] to it.groupValues[2] }.toSet()
            assertEquals(
                setOf(
                    "ROSTER_FIRST_CARD_X" to "ROSTER_FIRST_CARD_Y",
                    "DETAIL_NEXT_CHEVRON_X" to "DETAIL_NEXT_CHEVRON_Y",
                    "DETAIL_CLOSE_X" to "DETAIL_CLOSE_Y",
                ),
                tapped,
            )
        }

        @Test
        fun `the walk is bounded by an entry count and a wall clock`() {
            assertTrue(scanner.contains("HARD_BOUND_SLACK"), "the walk carries a hard entry bound over capacity")
            assertTrue(scanner.contains("WALL_CLOCK_BUDGET_MS"), "the walk carries a wall-clock budget")
            assertTrue(scanner.contains("RosterScanTermination.HARD_BOUND_REACHED"), "both bounds terminate rather than loop")
        }

        @Test
        fun `the walk terminates on more than one condition`() {
            for (reason in listOf("COUNT_REACHED", "CHEVRON_END", "WRAPPED", "STALLED", "ENTRY_LIMIT_REACHED", "HARD_BOUND_REACHED", "UNEXPECTED_SCREEN", "PRECONDITION_FAILED")) {
                assertTrue(scanner.contains("RosterScanTermination.$reason"), "the walk can terminate with $reason")
            }
        }
    }

    @Nested
    @DisplayName("preconditions fail closed before the first gesture")
    inner class Preconditions {
        /** Everything before the first safeTap call: no gesture has been dispatched yet at this point. */
        private fun beforeFirstTap(): String = scanner.substring(0, scanner.indexOf("safeTap(RosterScreenKind.ROSTER_LIST, ROSTER_FIRST_CARD_X"))

        @Test
        fun `the roster list, the Registered count and Filters OFF are all asserted first`() {
            val head = beforeFirstTap()
            assertTrue(head.contains("listScreen.kind != RosterScreenKind.ROSTER_LIST"), "the roster list is required")
            assertTrue(head.contains("list.registeredUsed == null || list.filtersOff != true"), "an unread count or an unconfirmed filter state stops the scan")
            assertEquals(2, Regex("RosterScanTermination\\.PRECONDITION_FAILED").findAll(head).count(), "both precondition failures record the same terminal reason")
        }

        @Test
        fun `the Details title is re-asserted after every chevron tap`() {
            val walk = scanner.substring(scanner.indexOf("private fun walk("))
            assertTrue(walk.contains("screen.kind != RosterScreenKind.UMAMUSUME_DETAILS"), "the dialog is re-asserted after the tap")
            assertTrue(walk.contains("RosterScanTermination.UNEXPECTED_SCREEN"), "a wrong screen stops the walk")
        }

        @Test
        fun `an unexpected screen stops rather than tapping to recover`() {
            val walk = scanner.substring(scanner.indexOf("private fun walk("))
            val afterUnexpected = walk.substring(walk.indexOf("not the Details dialog"))
            val nextTap = afterUnexpected.indexOf("safeTap(")
            val nextReturn = afterUnexpected.indexOf("return RosterScanTermination.UNEXPECTED_SCREEN")
            assertTrue(nextReturn >= 0 && (nextTap < 0 || nextReturn < nextTap), "the wrong-screen branch returns before any further tap")
        }
    }

    @Nested
    @DisplayName("durability")
    inner class Durability {
        @Test
        fun `entries are written before the header so a truncated write leaves no false promise`() {
            val persist = scanner.substring(scanner.indexOf("private fun persist("))
            val entryWrite = persist.indexOf("serializeRosterScanEntry(")
            val headerWrite = persist.indexOf("serializeRosterScanHeader(")
            assertTrue(entryWrite in 0 until headerWrite, "entry rows are appended before the scan header")
        }

        @Test
        fun `the scan stream is its own corpus file`() {
            assertTrue(scanner.contains("OutcomeCorpus.ROSTER_SCAN_PATH"), "the scan writes to its own append-only stream")
            for (other in listOf("CORPUS_PATH", "DECISIONS_PATH", "CAREER_STATE_PATH", "LINEAGE_PATH", "SHADOW_ADVISOR_PATH")) {
                assertFalse(scanner.contains("OutcomeCorpus.$other"), "the scan must not write into $other")
            }
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
