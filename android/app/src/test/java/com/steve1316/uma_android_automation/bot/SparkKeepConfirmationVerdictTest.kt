package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The keep-confirmation evidence fusion, pinned against the 2026-07-21 live false block: the
 * SPARKS screen read Medium 3* correctly, the keep dialog's rescan undercounted the same row
 * to 2* on one frame, and the old positional star check hard-blocked a finished no-spend
 * career. On this dialog (and only this dialog) names, kinds, order, and count are the
 * primary confirmation evidence; star counts corroborate, mismatches are retried on fresh
 * frames, and only a mismatch that reproduces with unambiguous slot evidence blocks.
 */
@DisplayName("Keep-confirmation evidence fusion")
class SparkKeepConfirmationVerdictTest {
    private fun fact(name: String, stars: Int, kind: SparkRowKind) =
        SparkRowFact(name, stars, kind, if (kind == SparkRowKind.WHITE) SparkWhiteClass.SKILL else null)

    /** The exact live set as the SPARKS screen read it (2026-07-21 21:12), including the
     * "Unity CupP" leading-glyph OCR fuzz the live log shows. */
    private val original =
        listOf(
            fact("Speed", 2, SparkRowKind.STAT),
            fact("Medium", 3, SparkRowKind.APTITUDE),
            fact("Behold Thine Emperor's Divine Might", 2, SparkRowKind.UNIQUE),
            fact("Arima Kinen", 1, SparkRowKind.WHITE),
            fact("Ramp Up", 1, SparkRowKind.WHITE),
            fact("Unity CupP", 1, SparkRowKind.WHITE),
        )

    /** The same set as a clean keep-dialog read delivers it. */
    private val dialogExact =
        listOf(
            fact("Speed", 2, SparkRowKind.STAT),
            fact("Medium", 3, SparkRowKind.APTITUDE),
            fact("Behold Thine Emperor's Divine Might", 2, SparkRowKind.UNIQUE),
            fact("Arima Kinen", 1, SparkRowKind.WHITE),
            fact("Ramp Up", 1, SparkRowKind.WHITE),
            fact("Unity Cup", 1, SparkRowKind.WHITE),
        )

    private val cleanEvidence = dialogExact.map { SparkStarEvidence(it.stars, 0) }

    private fun withStars(rows: List<SparkRowFact>, index: Int, stars: Int) = rows.mapIndexed { i, r -> if (i == index) r.copy(stars = stars) else r }

    private fun withName(rows: List<SparkRowFact>, index: Int, name: String) = rows.mapIndexed { i, r -> if (i == index) r.copy(name = name) else r }

    @Nested
    @DisplayName("verdicts")
    inner class Verdicts {
        @Test
        fun `an exact match confirms, across the live OCR name fuzz`() {
            assertEquals(SparkKeepVerdict.Confirm, keepDialogVerdict(original, dialogExact, cleanEvidence, 0, 2))
        }

        @Test
        fun `the live Medium undercount retries instead of blocking`() {
            // The exact 2026-07-21 frame: dialog row 2 read 2* against the original's 3*, the
            // third slot ambiguous. The old check blocked here; a fresh frame must be tried.
            val dialog = withStars(dialogExact, 1, 2)
            val evidence = cleanEvidence.mapIndexed { i, e -> if (i == 1) SparkStarEvidence(2, 1) else e }
            assertEquals(SparkKeepVerdict.Retry, keepDialogVerdict(original, dialog, evidence, 0, 2))
            assertEquals(SparkKeepVerdict.Retry, keepDialogVerdict(original, dialog, evidence, 1, 2))
        }

        @Test
        fun `a second frame that resolves the star confirms`() {
            // Frame 1 mismatched and retried; frame 2 reads the true count.
            assertEquals(SparkKeepVerdict.Confirm, keepDialogVerdict(original, dialogExact, cleanEvidence, 1, 2))
        }

        @Test
        fun `a mismatch still ambiguous after every retry confirms corroboratively`() {
            val dialog = withStars(dialogExact, 1, 2)
            val evidence = cleanEvidence.mapIndexed { i, e -> if (i == 1) SparkStarEvidence(2, 1) else e }
            val verdict = keepDialogVerdict(original, dialog, evidence, 2, 2)
            assertEquals(SparkKeepVerdict.ConfirmCorroborative(listOf(2)), verdict)
        }

        @Test
        fun `a mismatch that reproduces with unambiguous evidence blocks`() {
            val dialog = withStars(dialogExact, 1, 2)
            val verdict = keepDialogVerdict(original, dialog, cleanEvidence.mapIndexed { i, e -> if (i == 1) SparkStarEvidence(2, 0) else e }, 2, 2)
            assertTrue(verdict is SparkKeepVerdict.Block, "got $verdict")
            assertTrue((verdict as SparkKeepVerdict.Block).reason.contains("row 2"))
            assertTrue(verdict.reason.contains("not confirming"))
        }

        @Test
        fun `a high-confidence mismatch still gets its retries before the block`() {
            // A single frame can misread with confident-looking evidence too (a glint whitens
            // the slot toward a confident EMPTY). The block requires reproduction.
            val dialog = withStars(dialogExact, 1, 2)
            val evidence = cleanEvidence.mapIndexed { i, e -> if (i == 1) SparkStarEvidence(2, 0) else e }
            assertEquals(SparkKeepVerdict.Retry, keepDialogVerdict(original, dialog, evidence, 0, 2))
        }

        @Test
        fun `the scrolling-reader fallback carries no evidence and stays strict`() {
            val dialog = withStars(dialogExact, 1, 2)
            assertEquals(SparkKeepVerdict.Retry, keepDialogVerdict(original, dialog, null, 0, 2))
            assertTrue(keepDialogVerdict(original, dialog, null, 2, 2) is SparkKeepVerdict.Block)
        }

        @Test
        fun `a different readable name blocks immediately`() {
            val dialog = withName(dialogExact, 4, "Pace Chaser Savvy")
            val verdict = keepDialogVerdict(original, dialog, cleanEvidence, 0, 2)
            assertTrue(verdict is SparkKeepVerdict.Block, "got $verdict")
            assertTrue((verdict as SparkKeepVerdict.Block).reason.contains("row 5"))
        }

        @Test
        fun `a different row order blocks`() {
            val swapped = withName(withName(dialogExact, 3, "Ramp Up"), 4, "Arima Kinen")
            assertTrue(keepDialogVerdict(original, swapped, cleanEvidence, 0, 2) is SparkKeepVerdict.Block)
        }

        @Test
        fun `a missing row blocks`() {
            assertTrue(keepDialogVerdict(original, dialogExact.take(5), cleanEvidence.take(5), 0, 2) is SparkKeepVerdict.Block)
        }

        @Test
        fun `an extra row blocks`() {
            val extra = dialogExact + fact("Corner Recovery", 1, SparkRowKind.WHITE)
            assertTrue(keepDialogVerdict(original, extra, cleanEvidence + SparkStarEvidence(1, 0), 0, 2) is SparkKeepVerdict.Block)
        }

        @Test
        fun `a kind mismatch blocks even where names cannot arbitrate`() {
            val dialog = dialogExact.mapIndexed { i, r -> if (i == 1) r.copy(kind = SparkRowKind.WHITE) else r }
            val verdict = keepDialogVerdict(original, dialog, cleanEvidence, 0, 2)
            assertTrue(verdict is SparkKeepVerdict.Block, "got $verdict")
            assertTrue((verdict as SparkKeepVerdict.Block).reason.contains("row 2"))
        }

        @Test
        fun `unreadable names on one side never block on names alone`() {
            val dialog = withName(dialogExact, 3, SPARK_UNREADABLE_NAME)
            assertEquals(SparkKeepVerdict.Confirm, keepDialogVerdict(original, dialog, cleanEvidence, 0, 2))
        }
    }

    @Nested
    @DisplayName("same-list name compatibility")
    inner class NamesCompatible {
        @Test
        fun `the live Unity CupP fuzz is compatible with Unity Cup`() {
            assertTrue(SparkTextNorm.namesCompatible("Unity CupP", "Unity Cup"))
        }

        @Test
        fun `single-glyph OCR damage folds away`() {
            assertTrue(SparkTextNorm.namesCompatible("Arima Kinen", "Arima K1nen"))
            assertTrue(SparkTextNorm.namesCompatible("Behold Thine Emperor's Divine Might", "Behold Thine Emperors Divine Might"))
        }

        @Test
        fun `an unreadable side matches anything`() {
            assertTrue(SparkTextNorm.namesCompatible(SPARK_UNREADABLE_NAME, "Ramp Up"))
            assertTrue(SparkTextNorm.namesCompatible("", "Ramp Up"))
        }

        @Test
        fun `genuinely different names are incompatible`() {
            assertFalse(SparkTextNorm.namesCompatible("Ramp Up", "Unity Cup"))
            assertFalse(SparkTextNorm.namesCompatible("Arima Kinen", "Ramp Up"))
        }

        @Test
        fun `containment needs a distinctive length so short fragments cannot alias`() {
            assertFalse(SparkTextNorm.namesCompatible("Up", "Ramp Up"))
            assertFalse(SparkTextNorm.namesCompatible("Wit", "Wits"))
        }
    }

    @Nested
    @DisplayName("handler source guards")
    inner class SourceGuard {
        private fun keepHandlerBody(): String {
            val nav = sourceFile("CareerLaunchNavigator.kt").readText()
            val start = nav.indexOf("private fun handleSparksKeepConfirmation")
            val end = nav.indexOf("private fun clickSparkConfirmationCancel")
            require(start in 0 until end) { "keep handler not found where expected" }
            return nav.substring(start, end)
        }

        @Test
        fun `the fusion verdict is used by the keep handler and nowhere else`() {
            val nav = sourceFile("CareerLaunchNavigator.kt").readText()
            assertEquals(1, Regex("keepDialogVerdict\\(").findAll(nav).count(), "the selected-side confirmation must keep its strict check")
            assertTrue(keepHandlerBody().contains("keepDialogVerdict("))
        }

        @Test
        fun `the retry loop checks the soft-stop flags before every capture`() {
            val body = keepHandlerBody()
            val stop = body.indexOf("!BotService.isRunning || StartModule.queueStopRequested")
            val capture = body.indexOf("parseSparkRowCellsWithEvidence")
            assertTrue(stop in 0 until capture, "the soft-stop check must precede the evidence capture in the retry loop")
        }

        @Test
        fun `retries are bounded`() {
            assertTrue(keepHandlerBody().contains("maxStarRetries = 2"))
        }

        @Test
        fun `exactly one Confirm click exists on the success path`() {
            assertEquals(1, Regex("ButtonConfirm\\.click").findAll(keepHandlerBody()).count())
        }

        @Test
        fun `every block returns before the Confirm click`() {
            val body = keepHandlerBody()
            for (match in Regex("sparkSelectionBlocked\\(").findAll(body)) {
                val prefix = body.substring((match.range.first - 40).coerceAtLeast(0), match.range.first)
                assertTrue(prefix.contains("return"), "a blocked verdict must return, never fall through to the click: ...$prefix")
            }
            val lastBlock = body.lastIndexOf("sparkSelectionBlocked(")
            assertTrue(body.indexOf("ButtonConfirm.click") > lastBlock, "the click must come after every possible block")
        }

        @Test
        fun `the keep handler spends nothing and creates no transaction`() {
            val body = keepHandlerBody()
            assertFalse(body.contains("confirmSpend"), "the keep path must never confirm a spend")
            assertTrue(body.contains("declineSpend"), "keeping the rolled set closes the transaction as a declined spend")
            val gateMembers = Regex("SparkRerollGate\\.(\\w+)").findAll(body).map { it.groupValues[1] }.toSet()
            assertEquals(setOf("transaction"), gateMembers, "the handler may only read the live transaction, never create one")
        }

        @Test
        fun `a rerolled pill on the plain dialog still blocks before any list work`() {
            val body = keepHandlerBody()
            val pillBlock = body.indexOf("SparkConfirmationPill.REROLLED")
            val listRead = body.indexOf("parseSparkRowCellsWithEvidence")
            assertTrue(pillBlock in 0 until listRead, "the contradictory-pill block must precede the list verification")
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
        error("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
