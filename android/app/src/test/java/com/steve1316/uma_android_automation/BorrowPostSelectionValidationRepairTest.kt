package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BorrowPostSelectionValidationRepairTest {
    private val navigator by lazy {
        repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt")
            .readText()
            .replace("\r\n", "\n")
    }

    @Test
    fun `1 armed default path blocks an already-filled slot without a current tap`() {
        val gate = BorrowPostSelectionValidationGate(armed = true)
        var startCareerCount = 0
        val result = gate.failClosedWithoutCurrentTap(slotCommitted = true, returnedToSupportFormation = true)
        if (result == null) startCareerCount++

        assertEquals(BorrowPostSelectionStatus.SELECTION_VERIFICATION_FAILED, result?.status)
        assertEquals(0, startCareerCount)
        assertTrue(result?.slotCommitted == true)
        assertFalse(result?.freshVerifier == true)
        assertFalse(result?.startCareerTapped == true)
        assertTrue(result?.reason?.contains("no real Borrow tap occurred in this navigation") == true)
    }

    @Test
    fun `2 disabled gate preserves ordinary start behavior for an already-filled slot`() {
        val gate = BorrowPostSelectionValidationGate(armed = false)
        var startCareerCount = 0
        val result = gate.failClosedWithoutCurrentTap(slotCommitted = true, returnedToSupportFormation = true)
        if (result == null) startCareerCount++

        assertNull(result)
        assertEquals(1, startCareerCount)
    }

    @Test
    fun `3 a second fresh armed navigation cannot start from the first run committed slot`() {
        val firstNavigation = gateAfterTap(tpRaw = "27/30")
        val firstResult = firstNavigation.evaluate(true, true, verified(), true, "27/30")!!
        val secondNavigation = BorrowPostSelectionValidationGate(armed = true)
        var secondStartCareerCount = 0
        val secondResult = secondNavigation.failClosedWithoutCurrentTap(true, true)
        if (secondResult == null) secondStartCareerCount++

        assertEquals(BorrowPostSelectionStatus.SELECTED_VERIFIED_START_SUPPRESSED, firstResult.status)
        assertTrue(firstResult.slotCommitted)
        assertEquals(BorrowPostSelectionStatus.SELECTION_VERIFICATION_FAILED, secondResult?.status)
        assertEquals(0, secondStartCareerCount)
    }

    @Test
    fun `4 default no-tap failure returns before Start Career and final confirmation`() {
        val support = section("private fun handleSupportDeckScreen", "/**\n     * The production build-aware launch")
        val noTapGate = support.indexOf("failClosedWithoutCurrentTap")
        val startCareer = support.indexOf("ButtonStartCareer.click")

        assertTrue(noTapGate > support.indexOf("runBorrowStep(bitmap)"))
        assertTrue(noTapGate < startCareer)
        assertTrue(support.substring(noTapGate, startCareer).contains("return borrowPostSelectionValidationStopped(result)"))
        assertFalse(support.contains("handlePreRunConfirmation"))
    }

    @Test
    fun `5 equal readable TP evidence is unchanged`() {
        val result = gateAfterTap("27/30").evaluate(true, true, verified(), true, "27/30")!!

        assertEquals(true, result.tpUnchanged)
        assertEquals("unchanged", result.tpEvidenceStatus)
        assertTrue(result.suppressed)
    }

    @Test
    fun `6 different readable TP evidence fails with a truthful change reason`() {
        val result = gateAfterTap("27/30").evaluate(true, true, verified(), true, "26/30")!!

        assertEquals(false, result.tpUnchanged)
        assertEquals("changed", result.tpEvidenceStatus)
        assertEquals(BorrowPostSelectionStatus.SELECTION_VERIFICATION_FAILED, result.status)
        assertTrue(result.reason.contains("TP changed"))
    }

    @Test
    fun `7 blank TP before the tap is unknown`() {
        val result = gateAfterTap("   ").evaluate(true, true, verified(), true, "27/30")!!

        assertNull(result.tpUnchanged)
        assertNull(result.tpRawAtStart)
        assertEquals("unknown", result.tpEvidenceStatus)
        assertFalse(result.reason.contains("TP changed"))
    }

    @Test
    fun `8 blank TP after the tap is unknown`() {
        val result = gateAfterTap("27/30").evaluate(true, true, verified(), true, "")!!

        assertNull(result.tpUnchanged)
        assertNull(result.tpRawAtEnd)
        assertEquals("unknown", result.tpEvidenceStatus)
        assertFalse(result.reason.contains("TP changed"))
    }

    @Test
    fun `9 two blank TP samples remain unknown`() {
        val result = gateAfterTap("").evaluate(true, true, verified(), true, "\t")!!

        assertNull(result.tpUnchanged)
        assertNull(result.tpRawAtStart)
        assertNull(result.tpRawAtEnd)
        assertEquals("unknown", result.tpEvidenceStatus)
    }

    @Test
    fun `10 rehearsal authority cannot activate the production gate`() {
        assertFalse(BorrowPostSelectionAuthority.REHEARSAL.allowsPostSelectionValidation(armed = true))
        assertTrue(BorrowPostSelectionAuthority.PRODUCTION_LAUNCH.allowsPostSelectionValidation(armed = true))

        val rehearsal = section("internal fun rehearseSmartBorrowForRequiredDeck", "private fun readTraineeHeaderText")
        assertTrue(rehearsal.indexOf("BorrowPostSelectionAuthority.REHEARSAL") < rehearsal.indexOf("runBorrowStep(bitmap)"))
    }

    @Test
    fun `11 select-verify-rollback keeps its cleanup after rehearsal isolation`() {
        val entry = section("internal fun rehearseSmartBorrowSelectAndRollback", "/**\n     * One Smart Borrow select-verify-rollback cycle")
        val cycle = section("private fun runSmartBorrowSelectCycle", "private fun persistSmartBorrowSelect")

        assertTrue(entry.indexOf("BorrowPostSelectionAuthority.REHEARSAL") < entry.indexOf("runSmartBorrowSelectCycle(1)"))
        assertTrue(cycle.indexOf("readSelectedSlotVerification") < cycle.indexOf("ButtonBorrowCardRemove.click"))
        assertTrue(cycle.indexOf("ButtonBorrowCardRemove.click") < cycle.indexOf("slotEmpty"))
    }

    @Test
    fun `12 production navigation explicitly restores production authority`() {
        val navigate = section("fun navigate(", "private fun detectScreenState")

        assertTrue(navigate.contains("borrowPostSelectionAuthority = BorrowPostSelectionAuthority.PRODUCTION_LAUNCH"))
        assertTrue(BorrowPostSelectionAuthority.PRODUCTION_LAUNCH.allowsPostSelectionValidation(armed = true))
        assertFalse(BorrowPostSelectionAuthority.PRODUCTION_LAUNCH.allowsPostSelectionValidation(armed = false))
    }

    @Test
    fun `13 build-aware branch remains ahead of the default no-tap stop`() {
        val support = section("private fun handleSupportDeckScreen", "/**\n     * The production build-aware launch")
        val buildAware = section("private fun handleBuildAwareLaunch", "/**\n     * One pass of the Smart Borrow sub-flow")

        assertTrue(support.indexOf("return handleBuildAwareLaunch(requiredDeck)") < support.indexOf("failClosedWithoutCurrentTap"))
        assertTrue(buildAware.contains("readyLaunch?.verification"))
        assertTrue(buildAware.indexOf("borrowPostSelectionValidationGate") < buildAware.indexOf("ButtonStartCareer.click"))
    }

    @Test
    fun `14 pre-tap suppression remains distinct from post-selection evidence`() {
        var tapCount = 0
        val accepted = accepted()
        val preTapResult = BorrowPreTapValidationGate(armed = true).attempt(accepted) { tapCount++ }
        val postSelection = BorrowPostSelectionValidationGate(armed = true)
        postSelection.recordTap(preTapResult)

        assertEquals(BorrowTapStatus.LOCATED_VALIDATED_TAP_SUPPRESSED, preTapResult.status)
        assertEquals(0, tapCount)
        assertFalse(postSelection.hasTappedRow)
    }

    @Test
    fun `15 accessibility fault and host recovery remain outside the repair boundary`() {
        val walker = section("private fun borrowWalker", "private fun recoverBorrowScrollWithHost")
        val host = section("private fun recoverBorrowScrollWithHost", "private fun borrowLogText")

        assertTrue(navigator.contains("borrowAccessibilityScrollFaultInjector.dispatch"))
        assertTrue(walker.contains("recoverBorrowScrollWithHost"))
        assertFalse(walker.contains("failClosedWithoutCurrentTap"))
        assertFalse(host.contains("BorrowPostSelectionAuthority"))
    }

    @Test
    fun `16 successful default validation still requires a real tap and fresh verifier`() {
        val withoutTap = BorrowPostSelectionValidationGate(armed = true).failClosedWithoutCurrentTap(true, true)!!
        val withTap = gateAfterTap().evaluate(true, true, verified(), true)!!
        val staleVerifier = gateAfterTap().evaluate(true, false, verified(), true)!!

        assertEquals(BorrowPostSelectionStatus.SELECTION_VERIFICATION_FAILED, withoutTap.status)
        assertEquals(BorrowPostSelectionStatus.SELECTED_VERIFIED_START_SUPPRESSED, withTap.status)
        assertEquals(BorrowPostSelectionStatus.SELECTION_VERIFICATION_FAILED, staleVerifier.status)
    }

    private fun gateAfterTap(tpRaw: String? = null): BorrowPostSelectionValidationGate =
        BorrowPostSelectionValidationGate(armed = true).also {
            it.recordTap(BorrowTapResult(BorrowTapStatus.TAPPED, accepted()), tpRaw)
        }

    private fun verified(): SelectedSlotVerification =
        AcceptedBorrowSelectionVerifier.verify(accepted(), listOf(selected()))

    private fun accepted(): AcceptedBorrowRow =
        AcceptedBorrowRow(
            centerY = 700.0,
            identity = "[Fire at My Heels] Kitasan Black",
            observedIdentity = selected(),
        )

    private fun selected(): LocatableBorrowRow =
        LocatableBorrowRow(
            pageIndex = 0,
            character = "Kitasan Black",
            outfit = "Fire at My Heels",
            limitBreakIndex = 4,
            level = 50,
            ownerAlias = "friend-a",
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
