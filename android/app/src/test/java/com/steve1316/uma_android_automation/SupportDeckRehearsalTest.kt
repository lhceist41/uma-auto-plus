package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Support-deck selector REHEARSAL diagnostic safety.
 *
 * The rehearsal exercises the EXACT production saved-deck selector on an already-open Support
 * Formation screen so the arrow movement + exact-target verification can be proven live without a
 * career launch. The load-bearing invariant: no Start Career action is reachable from the rehearsal
 * subtree. These are pure contract checks plus source guards (the rehearsal drives real OCR + real
 * arrow taps, which cannot be unit-tested) pinning that it reuses production logic, requires the
 * right screen + setting, and can never reach a Start Career tap.
 */
@DisplayName("Support deck rehearsal diagnostic")
class SupportDeckRehearsalTest {
    private val key = "debugMode_startSupportDeckRehearsalTest"

    private val nav by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt") }
    private val campaign by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt") }
    private val debugUi by lazy { source("src/pages/DebugSettings/index.tsx") }

    /** The body of rehearseRequiredSupportDeck, up to the next method. */
    private fun rehearsalBody(): String {
        val start = nav.indexOf("fun rehearseRequiredSupportDeck(")
        assertTrue(start >= 0, "rehearseRequiredSupportDeck exists")
        val end = nav.indexOf("private fun readTraineeHeaderText(", start + 1)
        return nav.substring(start, if (end >= 0) end else nav.length)
    }

    @Nested
    @DisplayName("registry + routing")
    inner class Registry {
        @Test
        fun `the rehearsal key is in the canonical DebugTestGate registry`() {
            assertTrue(DebugTestGate.ALL_KEYS.contains(key), "DebugTestGate.ALL_KEYS must include the rehearsal key")
        }

        @Test
        fun `the rehearsal key is in the Debug Settings UI list`() {
            assertTrue(debugUi.contains("\"$key\""), "the rehearsal key must appear in the Debug Settings debugTestKeys list")
            assertTrue(debugUi.contains("Start Support Deck Rehearsal Test"), "the rehearsal has a user-facing Debug Settings entry")
        }

        @Test
        fun `Campaign startTests routes the key to the rehearsal handler`() {
            assertTrue(campaign.contains("\"$key\" to ::startSupportDeckRehearsalTest"), "the fnMap routes the rehearsal key")
            val handler = campaign.substring(campaign.indexOf("open fun startSupportDeckRehearsalTest("))
            assertTrue(handler.contains("rehearseRequiredSupportDeck("), "startSupportDeckRehearsalTest invokes the navigator rehearsal")
        }
    }

    @Nested
    @DisplayName("preconditions (fail closed)")
    inner class Preconditions {
        @Test
        fun `the rehearsal requires a nonzero required deck in range`() {
            val body = rehearsalBody()
            assertTrue(body.contains("SupportDeckSelector.requestedIndexOrNull("), "reads the required deck through the pure gate")
            assertTrue(body.contains("SupportDeckSelector.MIN_DECK..SupportDeckSelector.MAX_DECK"), "range-checks the required deck")
            assertTrue(body.contains("Status.SETTING_OFF"), "an off (0) setting stops")
            assertTrue(body.contains("Status.INVALID_TARGET"), "an out-of-range setting stops")
        }

        @Test
        fun `the rehearsal requires the real Support Formation screen`() {
            val body = rehearsalBody()
            assertTrue(body.contains("LabelSupportFormation.check("), "confirms the Support Formation screen via the production label")
            assertTrue(body.contains("Status.NOT_ON_SUPPORT_FORMATION"), "the wrong screen stops")
        }
    }

    @Nested
    @DisplayName("reuses production logic (no duplication)")
    inner class ReusesProduction {
        @Test
        fun `the rehearsal drives the production SupportDeckSelector`() {
            assertTrue(rehearsalBody().contains("SupportDeckSelector.run("), "the rehearsal calls the exact production selector loop")
        }

        @Test
        fun `the rehearsal uses the production deck-number OCR reader`() {
            assertTrue(rehearsalBody().contains("readDeckNumberWithRaw("), "the rehearsal reads via the production OCR reader (readDeckNumber delegates to it)")
            assertTrue(nav.contains("private fun readDeckNumber(bitmap: Bitmap): Int? = readDeckNumberWithRaw(bitmap).second"), "the production gate and the rehearsal share one OCR reader")
        }

        @Test
        fun `the rehearsal taps the production deck arrows at the shared coordinate`() {
            val body = rehearsalBody()
            assertTrue(body.contains("tapDeckArrow("), "the rehearsal taps via the production arrow tapper")
            assertTrue(body.contains("deckArrowPoint("), "the rehearsal logs the exact coordinate the production tapper uses")
            assertTrue(nav.contains("val (x, y) = deckArrowPoint(direction, bitmap.width, bitmap.height)"), "the production tapper uses the shared coordinate helper")
        }

        @Test
        fun `a verified selector outcome is the pre-borrow success`() {
            val body = rehearsalBody()
            assertTrue(body.contains("Outcome.Verified"), "the rehearsal handles the Verified outcome")
            assertTrue(body.contains("Status.PRE_BORROW_VERIFIED"), "a verified deck is the pre-borrow success status")
            assertTrue(body.contains("Outcome.Blocked") && body.contains("Status.SELECTOR_BLOCKED"), "a blocked selector fails closed")
        }
    }

    @Nested
    @DisplayName("Start Career is unreachable from the rehearsal subtree")
    inner class StartCareerBarrier {
        @Test
        fun `the rehearsal body never references any Start Career or launch-continuation path`() {
            val body = rehearsalBody()
            assertFalse(body.contains("ButtonStartCareer"), "the rehearsal must never tap Start Career")
            assertFalse(body.contains("handleSupportDeckScreen("), "the rehearsal must not call the Start-Career-owning deck handler")
            assertFalse(body.contains("handlePreRunConfirmation("), "the rehearsal must not call the pre-run confirmation gate")
            assertFalse(body.contains(".navigate("), "the rehearsal must not enter normal launch navigation")
            assertFalse(body.contains("trySmartBorrowPick("), "Smart Borrow is not exercised by the rehearsal")
        }

        @Test
        fun `the rehearsal's tap and read helpers are themselves Start-Career-free`() {
            // The only device helpers the rehearsal subtree calls; if these are clean, the whole
            // subtree is (SupportDeckSelector.run is pure and drives only these injected lambdas).
            for (helper in listOf("private fun tapDeckArrow(", "private fun readDeckNumberWithRaw(", "private fun deckArrowPoint(")) {
                val s = nav.indexOf(helper)
                assertTrue(s >= 0, "$helper exists")
                val body = nav.substring(s, nav.indexOf("\n    private fun ", s + helper.length).let { if (it >= 0) it else nav.length })
                assertFalse(body.contains("ButtonStartCareer"), "$helper must not tap Start Career")
                assertFalse(body.contains(".navigate("), "$helper must not enter launch navigation")
            }
        }

        @Test
        fun `Smart Borrow is explicitly documented as not included`() {
            assertTrue(rehearsalBody().contains("SMART_BORROW_REHEARSAL_NOT_INCLUDED"), "the rehearsal logs that Smart Borrow is not rehearsed")
        }
    }

    @Nested
    @DisplayName("result contract")
    inner class ResultContract {
        @Test
        fun `borrow fields default to not-attempted`() {
            val r = SupportDeckRehearsalResult(SupportDeckRehearsalResult.Status.PRE_BORROW_VERIFIED)
            assertFalse(r.smartBorrowAttempted, "Smart Borrow is not attempted by this diagnostic")
            assertFalse(r.postBorrowVerified, "post-borrow verification is out of scope")
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
