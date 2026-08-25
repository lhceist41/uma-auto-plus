package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BorrowPostSelectionValidationGateTest {
    private val navigator by lazy {
        repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt")
            .readText()
            .replace("\r\n", "\n")
    }

    @Test
    fun `1 missing setting keeps the seam inert`() {
        val gate = BorrowPostSelectionValidationGate()
        gate.recordTap(tapped())

        assertNull(gate.evaluate(true, true, verified(), true))
        assertFalse(gate.armed)
        assertTrue(repoFile("src/context/BotStateContext.tsx").readText().contains("debugMode_stopAfterBorrowSelectionVerified: false"))
        assertTrue(navigator.contains("getBooleanSetting(\"debug\", BORROW_POST_SELECTION_VALIDATION_SETTING, false)"))
    }

    @Test
    fun `2 explicit off preserves the normal path without verification`() {
        val gate = BorrowPostSelectionValidationGate(armed = false)
        gate.recordTap(tapped())

        assertFalse(gate.hasTappedRow)
        assertNull(gate.evaluate(true, true, verified(), true))
    }

    @Test
    fun `3 armed gate does not stop before a real tap`() {
        val gate = BorrowPostSelectionValidationGate(armed = true)

        assertFalse(gate.hasTappedRow)
        assertEquals(
            BorrowPostSelectionStatus.SELECTION_VERIFICATION_FAILED,
            gate.evaluate(true, true, verified(), true)?.status,
        )
    }

    @Test
    fun `4 pre-tap suppression never arms the post-selection gate`() {
        var taps = 0
        val preTap = BorrowPreTapValidationGate(armed = true).attempt(accepted()) { taps++ }
        val postTap = BorrowPostSelectionValidationGate(armed = true)
        postTap.recordTap(preTap)

        assertTrue(preTap.suppressed)
        assertEquals(0, taps)
        assertFalse(postTap.hasTappedRow)
    }

    @Test
    fun `5 verified committed selection suppresses Start Career truthfully`() {
        val gate = armedAfterTap(tpRaw = "27/30")
        val result = gate.evaluate(true, true, verified(), true, "27/30")!!

        assertEquals(BorrowPostSelectionStatus.SELECTED_VERIFIED_START_SUPPRESSED, result.status)
        assertTrue(result.suppressed)
        assertEquals("[Fire at My Heels] Kitasan Black", result.acceptedRow?.identity)
        assertTrue(result.slotCommitted)
        assertTrue(result.freshVerifier)
        assertEquals(SelectedSlotVerdict.VERIFIED, result.verification?.verdict)
        assertFalse(result.startCareerTapped)
        assertEquals("27/30", result.tpRawAtStart)
        assertEquals("27/30", result.tpRawAtEnd)
    }

    @Test
    fun `6 uncommitted slot fails closed after the real tap`() {
        val result = armedAfterTap().evaluate(false, false, null, true)!!

        assertEquals(BorrowPostSelectionStatus.SELECTION_VERIFICATION_FAILED, result.status)
        assertFalse(result.slotCommitted)
        assertFalse(result.startCareerTapped)
    }

    @Test
    fun `7 picker reopen failure fails closed`() {
        val result = armedAfterTap().evaluate(true, false, null, true)!!

        assertEquals(BorrowPostSelectionStatus.SELECTION_VERIFICATION_FAILED, result.status)
        assertTrue(result.reason.contains("fresh verifier"))
    }

    @Test
    fun `8 unreadable selected marker fails closed`() {
        val verification = AcceptedBorrowSelectionVerifier.verify(accepted(), emptyList())
        val result = armedAfterTap().evaluate(true, true, verification, true)!!

        assertEquals(SelectedSlotVerdict.NO_SELECTION, verification.verdict)
        assertEquals(BorrowPostSelectionStatus.SELECTION_VERIFICATION_FAILED, result.status)
    }

    @Test
    fun `9 multiple selected markers fail closed`() {
        val verification = AcceptedBorrowSelectionVerifier.verify(accepted(), listOf(selected(), selected(owner = "other")))
        val result = armedAfterTap().evaluate(true, true, verification, true)!!

        assertEquals(SelectedSlotVerdict.MULTIPLE_SELECTION, verification.verdict)
        assertEquals(BorrowPostSelectionStatus.SELECTION_VERIFICATION_FAILED, result.status)
    }

    @Test
    fun `10 selected character mismatch fails closed`() {
        val verification = AcceptedBorrowSelectionVerifier.verify(accepted(), listOf(selected(character = "Tokai Teio")))

        assertEquals(SelectedSlotVerdict.IDENTITY_MISMATCH, verification.verdict)
        assertFalse(armedAfterTap().evaluate(true, true, verification, true)!!.suppressed)
    }

    @Test
    fun `11 selected title mismatch fails closed`() {
        val verification = AcceptedBorrowSelectionVerifier.verify(accepted(), listOf(selected(outfit = "Another Outfit")))

        assertEquals(SelectedSlotVerdict.IDENTITY_MISMATCH, verification.verdict)
    }

    @Test
    fun `12 selected limit break mismatch fails closed`() {
        val verification = AcceptedBorrowSelectionVerifier.verify(accepted(), listOf(selected(limitBreak = 3)))

        assertEquals(SelectedSlotVerdict.IDENTITY_MISMATCH, verification.verdict)
    }

    @Test
    fun `13 unreadable accepted identity cannot verify a different card`() {
        val unreadable = AcceptedBorrowRow(700.0, "preferred template row")
        val verification = AcceptedBorrowSelectionVerifier.verify(unreadable, listOf(selected()))

        assertEquals(SelectedSlotVerdict.IDENTITY_MISMATCH, verification.verdict)
        assertTrue(verification.reason.contains("unreadable"))
    }

    @Test
    fun `14 equivalent owner verifies the same committed card`() {
        val verification = AcceptedBorrowSelectionVerifier.verify(accepted(owner = "friend-a"), listOf(selected(owner = "friend-b")))

        assertEquals(SelectedSlotVerdict.VERIFIED, verification.verdict)
        assertEquals("friend-b", verification.selectedRow?.ownerAlias)
    }

    @Test
    fun `15 unknown limit break remains equivalent under existing verifier policy`() {
        val expected = accepted(limitBreak = null)
        val verification = AcceptedBorrowSelectionVerifier.verify(expected, listOf(selected(limitBreak = 4)))

        assertEquals(SelectedSlotVerdict.VERIFIED, verification.verdict)
    }

    @Test
    fun `15b unreadable accepted title fails closed`() {
        val expected = accepted().copy(observedIdentity = selected(outfit = null))
        val verification = AcceptedBorrowSelectionVerifier.verify(expected, listOf(selected()))

        assertEquals(SelectedSlotVerdict.IDENTITY_MISMATCH, verification.verdict)
        assertTrue(verification.reason.contains("unreadable"))
    }

    @Test
    fun `16 failed return to Support Formation blocks the terminal success`() {
        val result = armedAfterTap().evaluate(true, true, verified(), false)!!

        assertEquals(BorrowPostSelectionStatus.SELECTION_VERIFICATION_FAILED, result.status)
        assertTrue(result.reason.contains("Support Formation"))
    }

    @Test
    fun `16b readable TP mismatch fails closed`() {
        val result = armedAfterTap(tpRaw = "27/30").evaluate(true, true, verified(), true, "26/30")!!

        assertEquals(BorrowPostSelectionStatus.SELECTION_VERIFICATION_FAILED, result.status)
        assertEquals(false, result.tpUnchanged)
        assertTrue(result.reason.contains("TP changed"))
    }

    @Test
    fun `17 terminal result prevents a fallback tap from replacing evidence`() {
        val gate = armedAfterTap()
        val first = gate.evaluate(true, true, verified(), true)!!
        gate.recordTap(tapped(accepted(identity = "fallback", centerY = 999.0)))
        val second = gate.evaluate(false, false, null, false)!!

        assertSame(first, second)
        assertEquals(700.0, second.acceptedRow?.centerY)
        assertEquals("[Fire at My Heels] Kitasan Black", second.acceptedRow?.identity)
    }

    @Test
    fun `18 host MOVED can feed fresh accepted evidence into the post-selection verifier`() {
        var hostCalls = 0
        var hostMoved = false
        val selection =
            selectFromBorrowList(
                BorrowListWalker(
                    maxPageGestures = 1,
                    maxSwallowedRetries = 0,
                    readScreen = {
                        if (hostMoved) BorrowScan(listOf(999.0 to "[Fire at My Heels] Kitasan Black")) else BorrowScan(listOf(111.0 to "stale"))
                    },
                    advancePage = {},
                    recoverService = { false },
                    recoverHost = {
                        hostCalls++
                        hostMoved = true
                        HostScrollRecoveryReport(
                            scope = HostInputScope.BORROW_LIST_SCROLL,
                            execution = InputExecutionResult(InputExecutionStatus.EXECUTED, foreground = true, detailCode = "MOVED"),
                            movement = SwipeMovement.MOVED,
                            detailCode = "MOVED",
                            swipeAttempts = 1,
                            stopped = false,
                        )
                    },
                ),
            ) { text -> hostMoved && text.contains("Kitasan Black") }
        val freshAccepted = accepted(centerY = selection.row!!.first)
        val gate = BorrowPostSelectionValidationGate(armed = true)
        gate.recordTap(tapped(freshAccepted))
        val result = gate.evaluate(true, true, AcceptedBorrowSelectionVerifier.verify(freshAccepted, listOf(selected())), true)!!

        assertEquals(1, hostCalls)
        assertEquals(999.0, result.acceptedRow?.centerY)
        assertEquals(BorrowPostSelectionStatus.SELECTED_VERIFIED_START_SUPPRESSED, result.status)
    }

    @Test
    fun `19 failed host movement cannot fabricate post-selection evidence`() {
        val gate = BorrowPostSelectionValidationGate(armed = true)
        gate.recordTap(BorrowTapResult(BorrowTapStatus.WAITING_FOR_ACCEPTED_ROW))

        assertFalse(gate.hasTappedRow)
    }

    @Test
    fun `20 setting saves immediately and stays outside debug rehearsal keys`() {
        val ui = repoFile("src/pages/DebugSettings/index.tsx").readText()
        val search = repoFile("src/data/searchConfig.ts").readText()

        assertTrue(ui.contains("updateBorrowPostSelectionValidationSetting"))
        assertTrue(ui.contains("debugMode_stopAfterBorrowSelectionVerified: checked"))
        assertTrue(ui.contains("saveSettingsImmediate(nextSettings)"))
        assertTrue(search.contains("debug-stop-after-borrow-selection-verified"))
        assertFalse(DebugTestGate.ALL_KEYS.contains(BORROW_POST_SELECTION_VALIDATION_SETTING))
    }

    @Test
    fun `21 empty fill conflict replacement and reselection all return through the post gate`() {
        val runBorrow = section("private fun runBorrowStep", "/** The single production boundary")
        val smart = section("private fun trySmartBorrowPick", "/** Outcome of one reopen-and-select pass")
        val reselection = section("private fun reopenAndSelect", "/** The bounded list walker")

        assertTrue(runBorrow.contains("finishBorrowPostSelectionValidation(fillEmptyFriendSlot(bitmap))"))
        assertTrue(runBorrow.contains("finishBorrowPostSelectionValidation(performBorrowReplacement"))
        assertTrue(smart.contains("tapAcceptedBorrowRow"))
        assertTrue(reselection.contains("tapAcceptedBorrowRow"))
    }

    @Test
    fun `22 accepted identity and real tap share one production boundary`() {
        val tapBoundary = section("private fun tapAcceptedBorrowRow", "private fun finishBorrowPostSelectionValidation")

        assertTrue(tapBoundary.contains("readBorrowPoolRichRows"))
        assertTrue(tapBoundary.contains("AcceptedBorrowRow(centerY, identity, observedIdentity)"))
        assertTrue(tapBoundary.contains("borrowPreTapValidationGate.attempt"))
        assertTrue(tapBoundary.contains("borrowPostSelectionValidationGate.recordTap"))
        assertTrue(tapBoundary.indexOf("borrowPreTapValidationGate.attempt") < tapBoundary.indexOf("recordTap"))
    }

    @Test
    fun `23 default path verifies fresh selected rows before its first Start Career tap`() {
        val support = section("private fun handleSupportDeckScreen", "/**\n     * A3 build-aware production launch")
        val post = section("private fun finishBorrowPostSelectionValidation", "private fun borrowPostSelectionValidationStopped")

        assertTrue(support.indexOf("runBorrowStep(bitmap)") < support.indexOf("ButtonStartCareer.click"))
        assertTrue(post.contains("readSelectedSlotRows()"))
        assertTrue(post.contains("AcceptedBorrowSelectionVerifier.verify"))
        assertFalse(post.contains("ButtonStartCareer"))
        assertFalse(post.contains("rollbackCommittedBorrow"))
    }

    @Test
    fun `24 build-aware READY path stops before its Start Career gate`() {
        val buildAware = section("private fun handleBuildAwareLaunch", "/**\n     * One pass of the Smart Borrow sub-flow")
        val postEvaluate = buildAware.indexOf("borrowPostSelectionValidationGate")
        val structuralGate = buildAware.indexOf("val gateState")
        val startTap = buildAware.indexOf("ButtonStartCareer.click")

        assertTrue(postEvaluate > buildAware.indexOf("supportDeckPostBorrowVerified = true"))
        assertTrue(postEvaluate < structuralGate)
        assertTrue(structuralGate < startTap)
        assertTrue(buildAware.contains("readyLaunch?.verification"))
        assertFalse(buildAware.substring(postEvaluate).contains("rollbackCommittedBorrow()"))
    }

    @Test
    fun `25 final confirmation refuses a previously stopped validation launch`() {
        val confirmation = section("private fun handlePreRunConfirmation", "private fun tickEventBoostIfOff")

        assertTrue(confirmation.indexOf("lastBorrowPostSelectionValidationResult") < confirmation.indexOf("ButtonStartCareer.click"))
    }

    @Test
    fun `26 seam-off source avoids unconditional rich identity and selected-marker reads`() {
        val tapBoundary = section("private fun tapAcceptedBorrowRow", "private fun finishBorrowPostSelectionValidation")
        val post = section("private fun finishBorrowPostSelectionValidation", "private fun borrowPostSelectionValidationStopped")

        assertTrue(tapBoundary.indexOf("if (productionPostSelectionValidationActive)") < tapBoundary.indexOf("readBorrowPoolRichRows"))
        assertTrue(post.contains("if (!productionPostSelectionValidationActive"))
    }

    @Test
    fun `27 accessibility fault injection and production host recovery remain separate`() {
        val walker = section("private fun borrowWalker", "private fun recoverBorrowScrollWithHost")

        assertTrue(navigator.contains("borrowAccessibilityScrollFaultInjector.dispatch"))
        assertTrue(walker.contains("recoverBorrowScrollWithHost"))
        assertFalse(walker.contains(BORROW_POST_SELECTION_VALIDATION_SETTING))
    }

    @Test
    fun `28 pre-tap and post-selection settings remain independently default off`() {
        val settings = repoFile("src/context/BotStateContext.tsx").readText()

        assertTrue(settings.contains("debugMode_stopBeforeBorrowTap: false"))
        assertTrue(settings.contains("debugMode_stopAfterBorrowSelectionVerified: false"))
        assertFalse(DebugTestGate.ALL_KEYS.contains(BORROW_PRETAP_VALIDATION_SETTING))
        assertFalse(DebugTestGate.ALL_KEYS.contains(BORROW_POST_SELECTION_VALIDATION_SETTING))
    }

    private fun armedAfterTap(tpRaw: String? = null): BorrowPostSelectionValidationGate =
        BorrowPostSelectionValidationGate(armed = true).also { it.recordTap(tapped(), tpRaw) }

    private fun tapped(row: AcceptedBorrowRow = accepted()): BorrowTapResult = BorrowTapResult(BorrowTapStatus.TAPPED, row)

    private fun verified(row: AcceptedBorrowRow = accepted()): SelectedSlotVerification =
        AcceptedBorrowSelectionVerifier.verify(row, listOf(selected()))

    private fun accepted(
        identity: String = "[Fire at My Heels] Kitasan Black",
        centerY: Double = 700.0,
        owner: String? = "friend-a",
        limitBreak: Int? = 4,
    ): AcceptedBorrowRow =
        AcceptedBorrowRow(
            centerY = centerY,
            identity = identity,
            observedIdentity = selected(owner = owner, limitBreak = limitBreak),
        )

    private fun selected(
        character: String? = "Kitasan Black",
        outfit: String? = "Fire at My Heels",
        limitBreak: Int? = 4,
        owner: String? = "friend-a",
    ): LocatableBorrowRow =
        LocatableBorrowRow(
            pageIndex = 0,
            character = character,
            outfit = outfit,
            limitBreakIndex = limitBreak,
            level = 50,
            ownerAlias = owner,
            blocked = false,
            confidence = "high",
        )

    private fun section(start: String, end: String): String {
        val startAt = navigator.indexOf(start)
        val endAt = navigator.indexOf(end, startAt + start.length)
        check(startAt >= 0 && endAt > startAt) { "could not isolate navigator section $start -> $end" }
        return navigator.substring(startAt, endAt)
    }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(8) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${System.getProperty("user.dir")}")
    }
}
