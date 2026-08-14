package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Smart Borrow REHEARSAL diagnostic safety + extraction parity.
 *
 * The production Smart Borrow sub-flow is extracted into one shared boundary ([runBorrowStep] plus
 * the [fillEmptyFriendSlot] / [performBorrowReplacement] step methods) that BOTH the normal launch
 * (handleSupportDeckScreen) and the new Smart Borrow rehearsal diagnostic call, so the borrow the
 * diagnostic rehearses is exactly the borrow production runs. The load-bearing invariant: no Start
 * Career action is reachable from the rehearsal subtree, and the borrow logic is not duplicated. These
 * are source guards (the borrow drives real OCR + real taps, which cannot be unit-tested) plus a pure
 * result-contract check.
 */
@DisplayName("Smart Borrow rehearsal diagnostic")
class SmartBorrowRehearsalTest {
    private val key = "debugMode_startSmartBorrowRehearsalTest"

    private val nav by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt") }
    private val campaign by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt") }
    private val debugUi by lazy { source("src/pages/DebugSettings/index.tsx") }
    private val botState by lazy { source("src/context/BotStateContext.tsx") }

    /** Body of a method, from its signature up to the start of [next]. */
    private fun slice(signature: String, next: String): String {
        val start = nav.indexOf(signature)
        assertTrue(start >= 0, "$signature exists")
        val end = nav.indexOf(next, start + signature.length)
        assertTrue(end > start, "$next follows $signature")
        return nav.substring(start, end)
    }

    private fun handlerBody() = slice("private fun handleSupportDeckScreen(", "private fun runBorrowStep(")

    private fun runBorrowStepBody() = slice("private fun runBorrowStep(", "private fun fillEmptyFriendSlot(")

    private fun fillBody() = slice("private fun fillEmptyFriendSlot(", "private fun performBorrowReplacement(")

    private fun replaceBody() = slice("private fun performBorrowReplacement(", "\n    /**")

    private fun diagnosticBody() = slice("internal fun rehearseSmartBorrowForRequiredDeck(", "private fun readTraineeHeaderText(")

    @Nested
    @DisplayName("extraction parity (one shared borrow boundary)")
    inner class ExtractionParity {
        @Test
        fun `production handler drives the shared runBorrowStep boundary`() {
            assertTrue(handlerBody().contains("runBorrowStep(bitmap)"), "handleSupportDeckScreen must call the shared boundary")
        }

        @Test
        fun `the rehearsal diagnostic drives the same shared runBorrowStep boundary`() {
            assertTrue(diagnosticBody().contains("runBorrowStep(bitmap)"), "the diagnostic must call the same shared boundary")
        }

        @Test
        fun `the old borrow branches are no longer inline in the deck handler (no duplication)`() {
            val handler = handlerBody()
            assertFalse(handler.contains("IconFriendSlotEmpty.click"), "the friend-slot fill moved out of the deck handler")
            assertFalse(handler.contains("trySmartBorrowPick"), "the Smart Borrow pick moved out of the deck handler")
            assertFalse(handler.contains("LabelDuplicateSupportDeck.check"), "the duplicate-pill detection moved into runBorrowStep")
        }

        @Test
        fun `candidate selection and conflict handling live inside the shared step methods`() {
            assertTrue(fillBody().contains("trySmartBorrowPick()"), "the empty-slot fill runs Smart Borrow inside fillEmptyFriendSlot")
            assertTrue(fillBody().contains("selectFromBorrowList("), "the validated default pick lives inside fillEmptyFriendSlot")
            assertTrue(replaceBody().contains("trySmartBorrowPick(replaceMode = true)"), "conflict replacement runs Smart Borrow inside performBorrowReplacement")
            assertTrue(
                runBorrowStepBody().contains("fillEmptyFriendSlot(bitmap)") && runBorrowStepBody().contains("performBorrowReplacement("),
                "runBorrowStep dispatches to both shared step methods",
            )
        }

        @Test
        fun `runBorrowStep returns null when no borrow action is needed (Start Career left to the caller)`() {
            assertTrue(runBorrowStepBody().contains("return null"), "runBorrowStep yields null so the caller owns the next step, not the borrow")
        }
    }

    @Nested
    @DisplayName("diagnostic registry + routing")
    inner class Registry {
        @Test
        fun `the key is in the canonical DebugTestGate registry`() {
            assertTrue(DebugTestGate.ALL_KEYS.contains(key), "DebugTestGate.ALL_KEYS must include the rehearsal key")
        }

        @Test
        fun `the key is in the Debug Settings UI list with a user-facing entry`() {
            assertTrue(debugUi.contains("\"$key\""), "the key must appear in the Debug Settings debugTestKeys list")
            assertTrue(debugUi.contains("Start Smart Borrow Rehearsal Test"), "the rehearsal has a user-facing Debug Settings entry")
        }

        @Test
        fun `an existing settings db without the key defaults to false`() {
            assertTrue(botState.contains("$key: false,"), "the default in BotStateContext must be false")
        }

        @Test
        fun `Campaign startTests routes the key to the rehearsal handler`() {
            assertTrue(campaign.contains("\"$key\" to ::startSmartBorrowRehearsalTest"), "the fnMap routes the rehearsal key")
            val handler = campaign.substring(campaign.indexOf("open fun startSmartBorrowRehearsalTest("))
            assertTrue(handler.contains("rehearseSmartBorrowForRequiredDeck("), "startSmartBorrowRehearsalTest invokes the navigator rehearsal")
        }
    }

    @Nested
    @DisplayName("pre-borrow gates (fail closed before any borrow)")
    inner class PreBorrowGates {
        @Test
        fun `an off or out-of-range required deck stops before any borrow`() {
            val body = diagnosticBody()
            assertTrue(body.contains("SupportDeckSelector.requestedIndexOrNull("), "reads the required deck through the pure gate")
            assertTrue(body.contains("SupportDeckSelector.MIN_DECK..SupportDeckSelector.MAX_DECK"), "range-checks the required deck")
            assertTrue(body.contains("Status.SETTING_OFF") && body.contains("Status.INVALID_TARGET"), "off/out-of-range stop")
        }

        @Test
        fun `the real Support Formation screen is required`() {
            val body = diagnosticBody()
            assertTrue(body.contains("LabelSupportFormation.check("), "confirms Support Formation via the production label")
            assertTrue(body.contains("Status.NOT_ON_SUPPORT_FORMATION"), "the wrong screen stops")
        }

        @Test
        fun `a fresh pre-borrow deck read must equal the target, else fail closed`() {
            val body = diagnosticBody()
            assertTrue(body.contains("readDeckNumberWithRaw("), "reads the deck via the production OCR reader")
            assertTrue(body.contains("preParsed != requested"), "requires an exact pre-borrow match")
            assertTrue(body.contains("Status.PRE_BORROW_DECK_MISMATCH"), "a pre-borrow mismatch fails closed")
        }

        @Test
        fun `the saved-deck selector is not driven by this diagnostic`() {
            assertFalse(diagnosticBody().contains("SupportDeckSelector.run("), "the borrow rehearsal must not move the deck arrows (the selector has its own rehearsal)")
        }

        @Test
        fun `a pre-borrow mismatch takes zero friend-slot action`() {
            val body = diagnosticBody()
            val mismatch = body.indexOf("Status.PRE_BORROW_DECK_MISMATCH")
            val firstFriendSlot = body.indexOf("IconFriendSlotEmpty")
            val firstStep = body.indexOf("runBorrowStep(")
            assertTrue(mismatch in 0 until firstFriendSlot, "the pre-borrow mismatch return must precede any friend-slot check")
            assertTrue(mismatch in 0 until firstStep, "the pre-borrow mismatch return must precede any borrow step")
        }

        @Test
        fun `an empty friend-slot precondition is enforced before the borrow`() {
            val body = diagnosticBody()
            assertTrue(body.contains("Status.FRIEND_SLOT_NOT_AVAILABLE"), "a populated friend slot fails closed")
            val friendSlotGate = body.indexOf("Status.FRIEND_SLOT_NOT_AVAILABLE")
            val firstStep = body.indexOf("runBorrowStep(")
            assertTrue(friendSlotGate in 0 until firstStep, "the empty-slot precondition must precede the borrow loop")
        }

        @Test
        fun `the shared borrow helper is invoked only after every gate`() {
            val body = diagnosticBody()
            val firstStep = body.indexOf("runBorrowStep(")
            assertTrue(firstStep > 0, "the diagnostic drives the shared boundary")
            for (gate in listOf("Status.SETTING_OFF", "Status.INVALID_TARGET", "Status.NOT_ON_SUPPORT_FORMATION", "Status.PRE_BORROW_DECK_MISMATCH", "Status.FRIEND_SLOT_NOT_AVAILABLE")) {
                assertTrue(body.indexOf(gate) in 0 until firstStep, "$gate must be checked before the first borrow step")
            }
        }
    }

    @Nested
    @DisplayName("post-borrow verification (fresh + exact)")
    inner class PostBorrow {
        @Test
        fun `a successful borrow must return to Support Formation`() {
            val body = diagnosticBody()
            assertTrue(body.contains("Status.RETURN_TO_SUPPORT_FORMATION_FAILED"), "failing to return to Support Formation fails closed")
            val loopEnd = body.indexOf("if (!borrowComplete)")
            assertTrue(loopEnd > 0, "the diagnostic tracks borrow completion")
            assertTrue(body.indexOf("LabelSupportFormation.check", loopEnd) > loopEnd, "it re-confirms Support Formation after the borrow loop")
        }

        @Test
        fun `the post-borrow deck read is a fresh capture through the production reader`() {
            val body = diagnosticBody()
            assertTrue(body.contains("val doneBitmap = iu.getSourceBitmap()"), "the post-borrow read uses a fresh capture")
            assertTrue(body.contains("readDeckNumberWithRaw(doneBitmap)"), "the post-borrow read uses the production OCR reader on that capture")
        }

        @Test
        fun `an unreadable or mismatched post-borrow deck fails closed`() {
            val body = diagnosticBody()
            assertTrue(body.contains("postParsed == null") && body.contains("Status.POST_BORROW_DECK_UNREADABLE"), "unreadable fails closed")
            assertTrue(body.contains("postParsed != requested") && body.contains("Status.POST_BORROW_DECK_MISMATCH"), "a mismatch fails closed")
        }

        @Test
        fun `only an exact post-borrow match latches verification`() {
            val body = diagnosticBody()
            assertTrue(body.contains("Status.POST_BORROW_VERIFIED"), "an exact match is the success status")
            assertEquals(1, Regex("postBorrowVerified = true").findAll(body).count(), "the verification latch is set in exactly one place (the exact-match branch)")
            assertEquals(1, Regex("supportDeckPostBorrowVerified = true").findAll(body).count(), "the production post-borrow latch is set only on the exact match")
        }
    }

    @Nested
    @DisplayName("Start Career is unreachable from the rehearsal subtree")
    inner class StartCareerBarrier {
        @Test
        fun `the diagnostic body never references Start Career or launch continuation`() {
            val body = diagnosticBody()
            assertFalse(body.contains("ButtonStartCareer"), "the diagnostic must never tap Start Career")
            assertFalse(body.contains("handleSupportDeckScreen("), "the diagnostic must not re-enter the Start-Career-owning deck handler")
            assertFalse(body.contains("handlePreRunConfirmation("), "the diagnostic must not call the pre-run confirmation gate")
            assertFalse(body.contains(".navigate("), "the diagnostic must not enter normal launch navigation")
        }

        @Test
        fun `the shared borrow boundary and its step methods never reference Start Career`() {
            for (body in listOf(runBorrowStepBody(), fillBody(), replaceBody())) {
                assertFalse(body.contains("ButtonStartCareer"), "the shared borrow boundary must never tap Start Career")
                assertFalse(body.contains("handlePreRunConfirmation("), "the shared borrow boundary must not call pre-run confirmation")
                assertFalse(body.contains(".navigate("), "the shared borrow boundary must not enter launch navigation")
            }
        }

        @Test
        fun `runBorrowStep's null signal is consumed inside the diagnostic, not returned to the deck handler`() {
            val body = diagnosticBody()
            assertTrue(body.contains("if (step == null)") && body.contains("borrowComplete = true"), "the diagnostic consumes the borrow-done signal itself")
        }
    }

    @Nested
    @DisplayName("result contract (pure)")
    inner class ResultContract {
        @Test
        fun `borrow evidence defaults to not-attempted`() {
            val r = SmartBorrowRehearsalResult(SmartBorrowRehearsalResult.Status.SETTING_OFF)
            assertFalse(r.smartBorrowAttempted, "no borrow attempted by default")
            assertFalse(r.smartBorrowSelected, "no borrow selected by default")
            assertFalse(r.postBorrowVerified, "not verified by default")
            assertFalse(r.friendSlotOpened, "friend slot not opened by default")
            assertEquals(0, r.replacementPasses, "no replacement passes by default")
        }
    }

    /**
     * Adversarial matrix A-J from the task, mapped to the source guard that enforces each. The borrow
     * itself runs real OCR + taps, so these pin the fail-closed topology rather than execute the flow.
     */
    @Nested
    @DisplayName("adversarial matrix A-J")
    inner class Adversarial {
        @Test
        fun `A wrong screen - no friend-slot click, stop`() {
            assertTrue(diagnosticBody().contains("Status.NOT_ON_SUPPORT_FORMATION"))
        }

        @Test
        fun `B supportDeckIndex 0 - no friend-slot click, stop`() {
            assertTrue(diagnosticBody().contains("Status.SETTING_OFF"))
        }

        @Test
        fun `C visible deck != required - pre-borrow mismatch, no friend-slot click, stop`() {
            val body = diagnosticBody()
            assertTrue(body.contains("Status.PRE_BORROW_DECK_MISMATCH"))
            assertTrue(body.indexOf("Status.PRE_BORROW_DECK_MISMATCH") < body.indexOf("runBorrowStep("))
        }

        @Test
        fun `D friend slot populated - fail closed, no destructive replacement`() {
            assertTrue(diagnosticBody().contains("Status.FRIEND_SLOT_NOT_AVAILABLE"))
        }

        @Test
        fun `E Smart Borrow finds no candidate - fail closed, no Start Career`() {
            val body = diagnosticBody()
            assertTrue(body.contains("Status.BORROW_PICK_FAILED"))
            assertTrue(body.contains("is TransitionResult.Failed"), "a failed borrow step fails the diagnostic closed")
        }

        @Test
        fun `F and G duplicate or trainee conflict - production replacement, bounded`() {
            // The diagnostic reuses production's bounded replacement (MAX_BORROW_DUPLICATE_REPLACEMENTS)
            // via runBorrowStep -> performBorrowReplacement, and records what happened.
            val body = diagnosticBody()
            assertTrue(body.contains("duplicateConflictObserved") && body.contains("traineeConflictObserved"))
            assertTrue(replaceBody().contains("MAX_BORROW_DUPLICATE_REPLACEMENTS"), "replacement stays bounded by the production budget")
        }

        @Test
        fun `H post-borrow OCR null - fail closed, no Start Career`() {
            assertTrue(diagnosticBody().contains("Status.POST_BORROW_DECK_UNREADABLE"))
        }

        @Test
        fun `I post-borrow deck != required - mismatch, fail closed, no Start Career`() {
            assertTrue(diagnosticBody().contains("Status.POST_BORROW_DECK_MISMATCH"))
        }

        @Test
        fun `J post-borrow deck == required - POST_BORROW_VERIFIED, return, no Start Career`() {
            val body = diagnosticBody()
            assertTrue(body.contains("Status.POST_BORROW_VERIFIED"))
            assertFalse(body.contains("ButtonStartCareer"))
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
