package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.INSPIRATION_LEFT_CARD_RIGHT_X
import com.steve1316.uma_android_automation.utils.INSPIRATION_RIGHT_CARD_LEFT_X
import com.steve1316.uma_android_automation.utils.INSPIRATION_ROW_PITCH
import com.steve1316.uma_android_automation.utils.INSPIRATION_SWIPE_LOW_Y
import com.steve1316.uma_android_automation.utils.INSPIRATION_SWIPE_X
import com.steve1316.uma_android_automation.utils.INSPIRATION_VIEWPORT_BOTTOM
import com.steve1316.uma_android_automation.utils.INSPIRATION_VIEWPORT_TOP
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Veteran Inspiration capture safety.
 *
 * This walk runs on the same dialog as the roster enumerator, so it shares that screen's hazards -
 * `Transfer` (an irreversible removal of Veterans from the account), Batch Favorite, the favorite
 * marker, share, `Change` (outfit) and the epithet pencil - and adds one of its own: it is the first
 * Veteran-screen reader that SWIPES. A swipe that started on a control, or ran outside the scroll
 * viewport, would be a drag on live account state.
 *
 * The load-bearing invariants cannot be proven by running it, so they are proven the same way the
 * roster enumerator's are: by pinning the coordinates it may use, by requiring every tap to pass the
 * runtime deny check, and by these source guards showing no mutating control is even referenced.
 */
@DisplayName("Veteran Inspiration capture safety")
class VeteranInspirationScannerSafetyTest {
    private val readKey = "debugMode_startVeteranInspirationReadTest"
    private val scanKey = "debugMode_startVeteranInspirationScanTest"

    private val scanner by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/VeteranInspirationScanner.kt") }
    private val reader by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/VeteranInspirationReader.kt") }
    private val campaign by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt") }
    private val debugUi by lazy { source("src/pages/DebugSettings/index.tsx") }
    private val settingsContext by lazy { source("src/context/BotStateContext.tsx") }
    private val searchConfig by lazy { source("src/data/searchConfig.ts") }

    /** Source with comments stripped. The forbidden-name guards must see what the code CALLS, not
     * what its documentation names: both files' doc comments deliberately list Transfer and the
     * epithet pencil as the controls they exist to stay away from. */
    private fun codeOnly(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//.*"), "")

    @Nested
    @DisplayName("registry + routing")
    inner class Registry {
        @Test
        fun `both keys are in the canonical DebugTestGate registry`() {
            assertTrue(DebugTestGate.ALL_KEYS.contains(readKey))
            assertTrue(DebugTestGate.ALL_KEYS.contains(scanKey))
        }

        @Test
        fun `Campaign startTests routes each key to its handler`() {
            assertTrue(campaign.contains("\"$readKey\" to ::startVeteranInspirationReadTest"))
            assertTrue(campaign.contains("\"$scanKey\" to ::startVeteranInspirationScanTest"))
            val readHandler = campaign.substring(campaign.indexOf("open fun startVeteranInspirationReadTest("))
            assertTrue(readHandler.contains("VeteranInspirationReader(game"))
            assertTrue(readHandler.contains(".debugRead()"))
            val scanHandler = campaign.substring(campaign.indexOf("open fun startVeteranInspirationScanTest("))
            assertTrue(scanHandler.contains("VeteranInspirationScanner(game).runScan("))
            assertTrue(scanHandler.contains("getIntSetting(\"debug\", \"veteranInspirationScanLimit\""))
        }

        @Test
        fun `the capture limit is wired through all five steps`() {
            // Interface, default, UI control, search registration, Kotlin read. Skipping any one of
            // these fails silently, which is exactly why this is pinned rather than eyeballed.
            assertTrue(settingsContext.contains("veteranInspirationScanLimit: number"), "declared in the Settings interface")
            assertTrue(settingsContext.contains("veteranInspirationScanLimit: 1,"), "defaults to a single Veteran, not the whole roster")
            assertTrue(debugUi.contains("veteranInspirationScanLimit: value"), "has a Debug Settings control")
            assertTrue(searchConfig.contains("\"veteran-inspiration-scan-limit\""), "registered in the settings search index")
            assertTrue(campaign.contains("\"veteranInspirationScanLimit\""), "read on the Kotlin side")
        }

        @Test
        fun `both toggles are discoverable in the UI and the search index`() {
            assertTrue(debugUi.contains("\"$readKey\""), "the read key is in the debugTestKeys list")
            assertTrue(debugUi.contains("\"$scanKey\""), "the scan key is in the debugTestKeys list")
            assertTrue(searchConfig.contains("\"debug-veteran-inspiration-read-test\""))
            assertTrue(searchConfig.contains("\"debug-veteran-inspiration-scan-test\""))
        }
    }

    @Nested
    @DisplayName("no mutating control is reachable")
    inner class NoMutation {
        @Test
        fun `neither the reader nor the scanner names a transfer, favorite, memo, outfit, epithet or share control`() {
            for ((name, code) in listOf("scanner" to codeOnly(scanner), "reader" to codeOnly(reader))) {
                for (forbidden in listOf("Transfer", "BatchFavorite", "Batch Favorite", "Memo", "ChangeOutfit", "Epithet", "Share", "EditTeam", "Edit Team")) {
                    assertFalse(code.contains(forbidden), "the $name must not reference $forbidden")
                }
            }
        }

        @Test
        fun `neither reaches career start, display settings or the filter controls`() {
            for ((name, code) in listOf("scanner" to codeOnly(scanner), "reader" to codeOnly(reader))) {
                for (forbidden in listOf("ButtonStartCareer", "StartCareer", "DisplaySettings", "Display Settings", "ResetFilters", "Reset Filters", "CareerLaunchNavigator")) {
                    assertFalse(code.contains(forbidden), "the $name must not reference $forbidden")
                }
            }
        }
    }

    @Nested
    @DisplayName("bounded, deny-checked navigation only")
    inner class BoundedNavigation {
        @Test
        fun `every tap in either class goes through a deny-checked helper`() {
            for ((name, code) in listOf("scanner" to scanner, "reader" to reader)) {
                assertTrue(code.contains("private fun safeTap("), "$name funnels taps through safeTap")
                assertEquals(1, Regex("game\\.tapCoordinate\\(").findAll(code).count(), "$name has exactly one tapCoordinate call, inside safeTap")
                assertTrue(code.contains("deniedZoneAt("), "$name's safeTap consults the deny list")
            }
        }

        @Test
        fun `the scanner taps only the first card, the next chevron and Close`() {
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
        fun `the reader taps only the Inspiration tab`() {
            val tapped = Regex("safeTap\\(([A-Z_]+), ([A-Z_]+),").findAll(reader).map { it.groupValues[1] to it.groupValues[2] }.toSet()
            assertEquals(setOf("DETAIL_TAB_INSPIRATION_CX" to "DETAIL_TAB_CY"), tapped)
        }

        @Test
        fun `both swipe endpoints stay inside the scroll viewport`() {
            // A drag that ran past the viewport would land on the dialog chrome above the panel or on
            // Close below it. Computed from the same constants the reader uses, not eyeballed.
            val distance = 4 * INSPIRATION_ROW_PITCH
            val high = INSPIRATION_SWIPE_LOW_Y + distance
            assertTrue(INSPIRATION_SWIPE_LOW_Y > INSPIRATION_VIEWPORT_TOP, "the low endpoint is below the viewport top")
            assertTrue(high < INSPIRATION_VIEWPORT_BOTTOM, "the high endpoint is above the viewport bottom, at ${high.toInt()}")
        }

        @Test
        fun `the swipe column is the empty gutter between the two card columns`() {
            assertTrue(INSPIRATION_SWIPE_X > INSPIRATION_LEFT_CARD_RIGHT_X, "right of the left column's cards")
            assertTrue(INSPIRATION_SWIPE_X < INSPIRATION_RIGHT_CARD_LEFT_X, "left of the right column's cards")
        }

        @Test
        fun `the reader's swipe distance is expressed in card pitches, not a magic number`() {
            assertTrue(reader.contains("rows * INSPIRATION_ROW_PITCH"), "the swipe advances a whole number of rows")
            assertTrue(reader.contains("private const val SWIPE_ROWS = 4"), "four of the ~8.7 visible rows, so consecutive frames always overlap")
            assertTrue(reader.contains("private const val SWIPE_ROWS_COARSE = 3"), "and fewer still when the merge must align on pixels alone")
        }

        @Test
        fun `every scroll loop is bounded and terminates rather than spinning`() {
            assertTrue(reader.contains("private const val MAX_SWIPES"), "the traversal carries a swipe budget")
            assertTrue(reader.contains("private const val MAX_RESET_SWIPES"), "the scroll-to-top reset carries its own budget")
            assertTrue(reader.contains("private const val STALL_LIMIT"), "a scrollbar that stops moving terminates the traversal")
            for (reason in listOf("REACHED_BOTTOM", "REACHED_FACTOR_LIST_END", "NO_SCROLL_NEEDED", "SCROLL_BUDGET_EXHAUSTED", "STALLED", "PANEL_NOT_READY", "NOT_AT_TOP")) {
                assertTrue(reader.contains("InspirationReadTermination.$reason"), "the traversal can terminate with $reason")
            }
        }

        @Test
        fun `the scanner terminates on more than one condition`() {
            for (reason in listOf("COUNT_REACHED", "ENTRY_LIMIT_REACHED", "CHEVRON_END", "UNEXPECTED_SCREEN", "HARD_BOUND_REACHED")) {
                assertTrue(scanner.contains("InspirationScanTermination.$reason"), "the walk can terminate with $reason")
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
            assertTrue(head.contains("list.registeredUsed == null || list.filtersOff != true"), "an unread count or an unconfirmed filter state stops the capture")
        }

        @Test
        fun `the Details title is re-asserted on every entry`() {
            val walk = scanner.substring(scanner.indexOf("private fun walk("))
            assertTrue(walk.contains("screen.kind != RosterScreenKind.UMAMUSUME_DETAILS"))
            assertTrue(walk.contains("InspirationScanTermination.UNEXPECTED_SCREEN"))
        }

        @Test
        fun `an unexpected screen stops rather than tapping to recover`() {
            val walk = scanner.substring(scanner.indexOf("private fun walk("))
            val afterUnexpected = walk.substring(walk.indexOf("not the Details dialog"))
            val nextTap = afterUnexpected.indexOf("safeTap(")
            val nextReturn = afterUnexpected.indexOf("return InspirationScanTermination.UNEXPECTED_SCREEN")
            assertTrue(nextReturn >= 0 && (nextTap < 0 || nextReturn < nextTap), "the wrong-screen branch returns before any further tap")
        }
    }

    @Nested
    @DisplayName("the batch is bound to one roster state")
    inner class SnapshotBinding {
        @Test
        fun `the Registered count is read before and after the walk`() {
            assertTrue(scanner.contains("registeredUsedAtStart = used"))
            assertTrue(scanner.contains("registeredUsedAtEnd = registeredUsedAtEnd"))
            assertTrue(scanner.contains("val registeredUsedAtEnd = closeDialogAndReadRoster()"))
        }

        @Test
        fun `an unread or changed post-walk count marks the batch incompatible`() {
            // A Veteran registered or released mid-capture shifts every later chevron position, which
            // would attach one Veteran's factors to another Veteran's identity - undetectable later.
            assertTrue(scanner.contains("snapshotCompatibility = registeredUsedAtEnd != null && registeredUsedAtEnd == used"))
        }
    }

    @Nested
    @DisplayName("durability")
    inner class Durability {
        @Test
        fun `the capture writes to its own corpus stream and touches no other`() {
            assertTrue(scanner.contains("OutcomeCorpus.VETERAN_INSPIRATION_PATH"))
            for (other in listOf("CORPUS_PATH", "DECISIONS_PATH", "CAREER_STATE_PATH", "LINEAGE_PATH", "SHADOW_ADVISOR_PATH", "ROSTER_SCAN_PATH")) {
                assertFalse(scanner.contains("OutcomeCorpus.$other"), "the capture must not write into $other")
            }
        }

        @Test
        fun `each Veteran's record is appended as it is read, before the batch header`() {
            // The walk checkpoints per entry, so an interrupted batch leaves headerless records that
            // the offline reader treats as partial, rather than nothing at all.
            val walk = scanner.substring(scanner.indexOf("private fun walk("))
            assertTrue(walk.contains("serializeVeteranInspiration(observation)"), "the entry record is written inside the walk")
            val runScan = scanner.substring(scanner.indexOf("fun runScan("), scanner.indexOf("private fun walk("))
            assertTrue(runScan.contains("serializeVeteranInspirationScan(header)"), "the header is written after the walk returns")
        }

        @Test
        fun `PL-R1b's enumerator is left untouched by this stage`() {
            // PL-R1c must not change the proven roster walk. It reuses VeteranRosterReader for the
            // identity header and drives its own traversal; it never calls into VeteranRosterScanner.
            val code = codeOnly(scanner)
            assertFalse(code.contains("VeteranRosterScanner"), "the Inspiration capture is a separate walk")
            assertTrue(code.contains("VeteranRosterReader("), "identity is read by the same proven reader")
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
