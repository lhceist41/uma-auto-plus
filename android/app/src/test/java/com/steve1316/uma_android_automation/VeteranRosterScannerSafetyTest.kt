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
    private val launchGate by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/DebugTestGate.kt") }
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
            assertTrue(handler.contains("val limit = selected.rosterLimit"), "the handler uses the verified immutable entry limit")
        }

        @Test
        fun `the entry-limit setting is wired through all five steps`() {
            // Interface, default, UI control, search registration, Kotlin read. Skipping any one of
            // these fails silently, which is exactly why this is pinned rather than eyeballed.
            assertTrue(settingsContext.contains("veteranRosterScanLimit: number"), "declared in the Settings interface")
            assertTrue(settingsContext.contains("veteranRosterScanLimit: 5,"), "has a default")
            assertTrue(debugUi.contains("veteranRosterScanLimit: value"), "has a Debug Settings control")
            assertTrue(searchConfig.contains("\"veteran-roster-scan-limit\""), "registered in the settings search index")
            assertTrue(launchGate.contains("integer(\"veteranRosterScanLimit\", selection.rosterLimit)"), "validated against the native snapshot")
            assertTrue(campaign.contains("val limit = selected.rosterLimit"), "the validated limit reaches the scanner")
        }

        @Test
        fun `the failure-evidence setting is wired through all five steps and defaults off`() {
            assertTrue(settingsContext.contains("veteranRosterScanEvidence: boolean"), "declared in the Settings interface")
            assertTrue(settingsContext.contains("veteranRosterScanEvidence: false,"), "defaults OFF - it writes PNGs to the device")
            assertTrue(debugUi.contains("veteranRosterScanEvidence: checked"), "has a Debug Settings control")
            assertTrue(searchConfig.contains("\"veteran-roster-scan-evidence\""), "registered in the settings search index")
            assertTrue(launchGate.contains("rows[\"debug/veteranRosterScanEvidence\"] == selection.rosterEvidence.toString()"), "validated against the native snapshot")
            assertTrue(campaign.contains("val evidence = selected.rosterEvidence"), "the validated evidence setting reaches the scanner")
        }

        @Test
        fun `the evidence crop writer only ever runs on unresolved identity fields`() {
            // The crops are driven off identityUnresolved, the same list the fingerprint gate uses, so
            // the pixels kept on disk and the corpus's own unresolvedFields can never name different
            // fields - and a clean walk writes nothing at all.
            val code = codeOnly(scanner)
            assertTrue(code.contains("for (field in identityUnresolved(observation))"), "crops are driven off the identity-unresolved list")
            assertTrue(code.contains("if (evidence == null) return"), "the whole path is a no-op when the diagnostic is off")
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
        private val pager = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/VeteranRosterScanEvent.kt").readText()
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
        fun `only the first list card, next chevron and Close are tapped`() {
            val tapped = Regex("safeTap\\([^,]+, ([A-Z_]+), ([A-Z_]+),").findAll(scanner).map { it.groupValues[1] to it.groupValues[2] }.toSet()
            assertEquals(
                setOf(
                    "ROSTER_FIRST_CARD_X" to "ROSTER_FIRST_CARD_Y",
                    "DETAIL_NEXT_CHEVRON_X" to "DETAIL_NEXT_CHEVRON_Y",
                    "DETAIL_CLOSE_X" to "DETAIL_CLOSE_Y",
                ),
                tapped,
            )
            assertEquals(1, Regex("safeTap\\(RosterScreenKind\\.ROSTER_LIST,").findAll(scanner).count(), "the first card is opened once")
            assertTrue(scanner.contains("minOf(used, ROSTER_VISIBLE_CARD_COUNT)"), "optional evidence stops at the measured 25-card boundary")
        }

        @Test
        fun `optional card OCR cannot abort the ordinary chevron walk`() {
            val read = scanner.indexOf("reader.readListCardRating(listBitmap, index)")
            val tap = scanner.indexOf("safeTap(RosterScreenKind.ROSTER_LIST, ROSTER_FIRST_CARD_X")
            assertTrue(read in 0 until tap, "the one-frame list pre-pass precedes the first tap")
            val prepass = scanner.substring(scanner.indexOf("val listRatings ="), scanner.indexOf("var lastBitmap ="))
            assertTrue(prepass.contains("catch (e: Exception)"), "a single card OCR failure is local")
            assertFalse(prepass.contains("return finish("), "optional evidence cannot end the scan")
            assertTrue(scanner.contains("rosterListBindingStable(list, afterList)"), "the final list state is checked before persistence")
        }

        @Test
        fun `a chevron is tapped once then recaptured until a new Details member is proven`() {
            val walk = scanner.substring(scanner.indexOf("private fun walk("), scanner.indexOf("private fun appendEntry("))
            assertTrue(walk.contains("for (attempt in 1..TRANSITION_CAPTURE_ATTEMPTS)"))
            assertTrue(walk.contains("settledRosterPagerRead(previous, candidate, read, used)"))
            assertTrue(pager.contains("advance(current, seen.size) ?: return RosterScanTermination.STALLED"))
            assertEquals(1, Regex("safeTap\\(RosterScreenKind\\.UMAMUSUME_DETAILS, DETAIL_NEXT_CHEVRON_X").findAll(walk).count())
            assertTrue(pager.contains("rankFreeCycleStep(seen, identity, used)"), "the extra transition decides cycle closure")
            assertTrue(pager.indexOf("RankFreeCycleStep.CLOSED -> return RosterScanTermination.CYCLE_CLOSED") < pager.indexOf("visit(seen.lastIndex, current)"), "the closure read is not appended")
            assertTrue(scanner.contains("rosterListBindingStable(list, afterList)"), "the final list state is checked before persistence")
        }

        @Test
        fun `an unreadable pre-tap chevron stops before advancing`() {
            val walk = scanner.substring(scanner.indexOf("private fun walk("), scanner.indexOf("private fun appendEntry("))
            val unknown = walk.indexOf("if (chevron != ChevronState.ENABLED) return@advance null")
            val tap = walk.indexOf("safeTap(RosterScreenKind.UMAMUSUME_DETAILS, DETAIL_NEXT_CHEVRON_X")
            assertTrue(unknown in 0 until tap, "unknown state is rejected before the tap")
        }

        @Test
        fun `the walk is bounded by an entry count and a wall clock`() {
            assertTrue(scanner.contains("HARD_BOUND_SLACK"), "the walk carries a hard entry bound over capacity")
            assertTrue(scanner.contains("WALL_CLOCK_BUDGET_MS"), "the walk carries a wall-clock budget")
            assertTrue(pager.contains("RosterScanTermination.HARD_BOUND_REACHED"), "both bounds terminate rather than loop")
        }

        @Test
        fun `the walk terminates on more than one condition`() {
            for (reason in listOf("CYCLE_CLOSED", "EMPTY_LIST", "WRAPPED", "STALLED", "ENTRY_LIMIT_REACHED", "HARD_BOUND_REACHED", "UNEXPECTED_SCREEN", "PRECONDITION_FAILED")) {
                assertTrue((scanner + pager).contains("RosterScanTermination.$reason"), "the walk can terminate with $reason")
            }
            assertFalse(scanner.contains("RosterScanTermination.CHEVRON_END"))
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
            assertTrue(head.contains("!rosterScanPrerequisitesMet(list)"), "an unread count or an unconfirmed filter state stops the scan")
            assertTrue(head.contains("hybridListEvidenceEligible(list, viewportSupported)"), "sort gates only optional list evidence")
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
            assertTrue(walk.contains("if (screen.kind != RosterScreenKind.UMAMUSUME_DETAILS) {"))
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
