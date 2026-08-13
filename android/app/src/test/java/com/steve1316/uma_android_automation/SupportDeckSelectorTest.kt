package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Explicit saved-support-formation selection safety.
 *
 * A mission retry that required the hand-built Deck 5 launched on the game's default Deck 2, because
 * Auto-Fill off leaves whatever formation the career-start screen shows and nothing selected/verified
 * the owned five-card deck (2026-08-13). The pure [SupportDeckSelector] decisions are pinned here, and
 * source guards prove the navigator wires them so Start Career is unreachable unless the active deck is
 * exactly the requested one, before AND after the borrow.
 */
@DisplayName("Support deck selection safety")
class SupportDeckSelectorTest {
    /** Feeds [SupportDeckSelector.run] a scripted sequence of deck reads. */
    private fun scriptedReads(vararg values: Int?): () -> Int? {
        val iterator = values.iterator()
        return { if (iterator.hasNext()) iterator.next() else error("readDeck called more times than scripted") }
    }

    @Nested
    @DisplayName("requestedIndexOrNull (0 = off sentinel)")
    inner class RequestedIndex {
        @Test
        fun `zero means no explicit deck (legacy behavior)`() {
            assertNull(SupportDeckSelector.requestedIndexOrNull(0))
        }

        @Test
        fun `a valid deck is carried through`() {
            assertEquals(5, SupportDeckSelector.requestedIndexOrNull(5))
            assertEquals(1, SupportDeckSelector.requestedIndexOrNull(1))
            assertEquals(10, SupportDeckSelector.requestedIndexOrNull(10))
        }

        @Test
        fun `an out-of-range non-zero value is carried through, not clamped or ignored`() {
            // The caller runs run(), which fails closed on the invalid request. Silent clamp/ignore
            // would launch a wrong deck, which is exactly what this feature prevents.
            assertEquals(11, SupportDeckSelector.requestedIndexOrNull(11))
            assertEquals(-1, SupportDeckSelector.requestedIndexOrNull(-1))
        }
    }

    @Nested
    @DisplayName("parseDeckLabel (exact identity, no fuzzy)")
    inner class ParseDeckLabel {
        @Test
        fun `reads a clean Deck N label`() {
            assertEquals(2, SupportDeckSelector.parseDeckLabel("Deck 2"))
            assertEquals(5, SupportDeckSelector.parseDeckLabel("Deck 5"))
            assertEquals(10, SupportDeckSelector.parseDeckLabel("Deck 10"))
            assertEquals(1, SupportDeckSelector.parseDeckLabel("Deck 1"))
        }

        @Test
        fun `is case-insensitive and tolerates missing space and surrounding noise`() {
            assertEquals(5, SupportDeckSelector.parseDeckLabel("DECK 5"))
            assertEquals(5, SupportDeckSelector.parseDeckLabel("deck5"))
            assertEquals(3, SupportDeckSelector.parseDeckLabel("Deck 3\nStart Career"))
            assertEquals(3, SupportDeckSelector.parseDeckLabel("xDeck 3x"))
        }

        @Test
        fun `an out-of-range number is UNKNOWN, not clamped`() {
            assertNull(SupportDeckSelector.parseDeckLabel("Deck 0"))
            assertNull(SupportDeckSelector.parseDeckLabel("Deck 11"))
            assertNull(SupportDeckSelector.parseDeckLabel("Deck 12"))
        }

        @Test
        fun `a bare number with no Deck token is UNKNOWN (a stray count is not the deck)`() {
            assertNull(SupportDeckSelector.parseDeckLabel("5"))
            assertNull(SupportDeckSelector.parseDeckLabel("x2"))
        }

        @Test
        fun `empty or token-only reads are UNKNOWN`() {
            assertNull(SupportDeckSelector.parseDeckLabel(""))
            assertNull(SupportDeckSelector.parseDeckLabel("Deck"))
            assertNull(SupportDeckSelector.parseDeckLabel("Support Formation"))
        }
    }

    @Nested
    @DisplayName("stepToward (shortest linear path)")
    inner class StepToward {
        @Test
        fun `moves right toward a higher deck and left toward a lower one`() {
            assertEquals(SupportDeckSelector.Direction.RIGHT, SupportDeckSelector.stepToward(2, 5))
            assertEquals(SupportDeckSelector.Direction.LEFT, SupportDeckSelector.stepToward(5, 2))
        }

        @Test
        fun `is null when already on the requested deck`() {
            assertNull(SupportDeckSelector.stepToward(5, 5))
        }
    }

    @Nested
    @DisplayName("run (bounded read -> navigate -> verify, fail closed)")
    inner class Run {
        @Test
        fun `already on the requested deck verifies with no navigation`() {
            val taps = mutableListOf<SupportDeckSelector.Direction>()
            val outcome = SupportDeckSelector.run(5, scriptedReads(5), { taps.add(it) })
            assertTrue(outcome is SupportDeckSelector.Outcome.Verified)
            assertTrue(taps.isEmpty())
        }

        @Test
        fun `navigates up from the default deck to the requested one`() {
            val taps = mutableListOf<SupportDeckSelector.Direction>()
            val outcome = SupportDeckSelector.run(5, scriptedReads(2, 3, 4, 5), { taps.add(it) })
            assertTrue(outcome is SupportDeckSelector.Outcome.Verified)
            assertEquals(listOf(SupportDeckSelector.Direction.RIGHT, SupportDeckSelector.Direction.RIGHT, SupportDeckSelector.Direction.RIGHT), taps)
        }

        @Test
        fun `navigates down in the opposite direction`() {
            val taps = mutableListOf<SupportDeckSelector.Direction>()
            val outcome = SupportDeckSelector.run(2, scriptedReads(5, 4, 3, 2), { taps.add(it) })
            assertTrue(outcome is SupportDeckSelector.Outcome.Verified)
            assertEquals(listOf(SupportDeckSelector.Direction.LEFT, SupportDeckSelector.Direction.LEFT, SupportDeckSelector.Direction.LEFT), taps)
        }

        @Test
        fun `an unreadable initial deck blocks with no navigation`() {
            val taps = mutableListOf<SupportDeckSelector.Direction>()
            val outcome = SupportDeckSelector.run(5, scriptedReads(null), { taps.add(it) })
            assertTrue(outcome is SupportDeckSelector.Outcome.Blocked)
            assertTrue(taps.isEmpty())
        }

        @Test
        fun `an unreadable deck after a tap blocks`() {
            val outcome = SupportDeckSelector.run(5, scriptedReads(2, 3, null), {})
            assertTrue(outcome is SupportDeckSelector.Outcome.Blocked)
        }

        @Test
        fun `a stalled arrow (number does not change) blocks`() {
            val outcome = SupportDeckSelector.run(5, scriptedReads(2, 2), {})
            assertTrue(outcome is SupportDeckSelector.Outcome.Blocked)
            assertTrue((outcome as SupportDeckSelector.Outcome.Blocked).reason.contains("stalled"))
        }

        @Test
        fun `an out-of-range request blocks immediately (config fail closed)`() {
            val zeroTaps = mutableListOf<SupportDeckSelector.Direction>()
            assertTrue(SupportDeckSelector.run(0, scriptedReads(2), { zeroTaps.add(it) }) is SupportDeckSelector.Outcome.Blocked)
            assertTrue(zeroTaps.isEmpty())
            assertTrue(SupportDeckSelector.run(11, scriptedReads(2), {}) is SupportDeckSelector.Outcome.Blocked)
            assertTrue(SupportDeckSelector.run(-1, scriptedReads(2), {}) is SupportDeckSelector.Outcome.Blocked)
        }

        @Test
        fun `exceeding the step bound blocks rather than looping`() {
            val outcome = SupportDeckSelector.run(5, scriptedReads(2, 3, 4), { }, maxSteps = 2)
            assertTrue(outcome is SupportDeckSelector.Outcome.Blocked)
        }
    }

    @Nested
    @DisplayName("navigator wiring (source guard)")
    inner class NavigatorWiring {
        private val nav by lazy { sourceFile("CareerLaunchNavigator.kt").readText().replace("\r\n", "\n") }

        private fun deckHandler(): String {
            val start = nav.indexOf("private fun handleSupportDeckScreen(")
            assertTrue(start >= 0, "handleSupportDeckScreen exists")
            val end = nav.indexOf("\n    private fun ", start + 1)
            return nav.substring(start, if (end >= 0) end else nav.length)
        }

        @Test
        fun `navigate resolves the required deck through the selector, reset per attempt`() {
            assertTrue(nav.contains("SupportDeckSelector.requestedIndexOrNull("), "the required deck is derived through the pure gate")
            assertTrue(nav.contains("supportDeckPreBorrowVerified = false"), "pre-borrow verification is reset per navigate()")
            assertTrue(nav.contains("supportDeckPostBorrowVerified = false"), "post-borrow verification is reset per navigate()")
        }

        @Test
        fun `the deck-selection gate runs in the support deck handler via the pure selector`() {
            assertTrue(deckHandler().contains("SupportDeckSelector.run("), "the handler drives the bounded selector")
        }

        @Test
        fun `the deck gate precedes Auto-Fill so a required deck is never auto-filled over`() {
            val handler = deckHandler()
            val gate = handler.indexOf("SupportDeckSelector.run(")
            val autoFill = handler.indexOf("ButtonAutoFill")
            assertTrue(gate in 0 until autoFill, "the explicit-deck selection precedes the Auto-Fill click path")
        }

        @Test
        fun `Auto-Fill is suppressed when an explicit deck is required`() {
            val handler = deckHandler()
            // The Auto-Fill click is guarded so it cannot fire while a required deck owns the slots.
            assertTrue(handler.contains("requiredDeck == null") || handler.contains("requestedSupportDeckIndex == null"), "the Auto-Fill path is gated on no explicit deck being required")
            assertTrue(handler.contains("[SUPPORT_DECK] Auto-Fill suppressed"), "the suppression is logged, never silent")
        }

        @Test
        fun `the handler re-verifies the deck after the borrow before Start Career`() {
            val handler = deckHandler()
            val postRead = handler.indexOf("supportDeckPostBorrowVerified")
            assertTrue(postRead >= 0 && handler.contains("readDeckNumber("), "a post-borrow deck read gates the Start Career click")
        }

        @Test
        fun `both Start-Career gates fail closed without full verification`() {
            // Support deck handler: the click is gated on both verifications.
            assertTrue(deckHandler().contains("supportDeckPreBorrowVerified") && deckHandler().contains("supportDeckPostBorrowVerified"))
            // Pre-Run Confirmation: a defense-in-depth gate before the final Start Career tap.
            val conf = nav.indexOf("private fun handlePreRunConfirmation(")
            assertTrue(conf >= 0)
            val gate = nav.indexOf("supportDeckPostBorrowVerified", conf)
            val click = nav.indexOf("Clicking 'Start Career!'", conf)
            assertTrue(gate in conf until click, "the explicit-deck check precedes the final Start Career tap")
        }

        @Test
        fun `the deck gate does not depend on reuseLastLaunchSetup to run`() {
            // The gate keys on the required deck, not the reuse flag, so reuse cannot bypass it.
            val handler = deckHandler()
            val gate = handler.indexOf("SupportDeckSelector.run(")
            assertTrue(gate >= 0)
            val guard = handler.lastIndexOf("if (", gate)
            val guardLine = handler.substring(guard, gate)
            assertFalse(guardLine.contains("reuseLastLaunchSetup"), "the selector's guard is the required deck, not the reuse flag")
        }
    }

    private fun sourceFile(relative: String): File = File(kotlinRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
