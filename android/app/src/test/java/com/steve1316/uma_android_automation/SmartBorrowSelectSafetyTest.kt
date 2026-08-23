package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * DeckLab Smart Borrow 2.0 select-verify-rollback safety + wiring (source guards).
 *
 * The select cycle drives real OCR and real taps, so its behaviour cannot be unit-tested directly.
 * These guards pin the load-bearing invariants against the source: the cycle taps a borrow row and the
 * Remove control but NEVER reaches Start Career, it verifies the committed slot via the reopened
 * picker's "Selected" marker, it confirms the slot returns to empty, and the diagnostic is registered
 * and routed so the arming fail-closed covers it.
 */
@DisplayName("Smart Borrow select-verify-rollback safety")
class SmartBorrowSelectSafetyTest {
    private val key = "debugMode_startSmartBorrowSelectRollbackTest"

    private val nav by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt") }
    private val campaign by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt") }
    private val label by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/components/Label.kt") }
    private val debugUi by lazy { source("src/pages/DebugSettings/index.tsx") }
    private val botState by lazy { source("src/context/BotStateContext.tsx") }

    private fun slice(signature: String, next: String): String {
        val start = nav.indexOf(signature)
        assertTrue(start >= 0, "$signature exists")
        val end = nav.indexOf(next, start + signature.length)
        assertTrue(end > start, "$next follows $signature")
        return nav.substring(start, end)
    }

    // The cycle body is everything from the cycle entry point up to its persistence helper.
    private fun cycleBody() = slice("private fun runSmartBorrowSelectCycle(", "private fun persistSmartBorrowSelect(")

    private fun orchestratorBody() = slice("internal fun rehearseSmartBorrowSelectAndRollback(", "private fun runSmartBorrowSelectCycle(")

    @Nested
    @DisplayName("the cycle taps a row and removes it but cannot reach Start Career")
    inner class Safety {
        @Test
        fun `neither the cycle nor the orchestrator presses Start Career`() {
            assertFalse(cycleBody().contains("ButtonStartCareer"), "the select cycle must never reference the Start Career button")
            assertFalse(orchestratorBody().contains("ButtonStartCareer"), "the orchestrator must never reference the Start Career button")
        }

        @Test
        fun `the cycle taps exactly the located row via the proven selection primitive`() {
            val body = cycleBody()
            assertTrue(body.contains("selectFromBorrowList("), "the cycle reuses the production selection walk")
            assertTrue(body.contains("CoordinateTap.tap(gestureUtils, 540.0, row.first, \"borrow_select_row\")"), "the cycle taps the row's live center, not a stale coordinate")
        }

        @Test
        fun `the cycle only taps a row after a single-identity locate`() {
            val body = cycleBody()
            assertTrue(body.contains("locateSmartBorrowIntentReadOnly("), "the cycle establishes the located card read-only first")
            assertTrue(body.contains("match.identityCandidates.size != 1"), "the cycle fails closed on more than one distinct identity candidate before tapping")
            assertTrue(body.contains("SELECT_AMBIGUOUS_IDENTITY"), "the ambiguous-identity fail-closed status is used")
        }

        @Test
        fun `the cycle verifies the committed slot via the Selected marker`() {
            val body = cycleBody()
            assertTrue(body.contains("readSelectedSlotVerification("), "the cycle verifies the committed slot's identity")
            assertTrue(nav.contains("LabelBorrowSelected.findAll("), "the verification reads the reopened picker's Selected marker")
            assertTrue(label.contains("object LabelBorrowSelected"), "the Selected marker template component exists")
        }

        @Test
        fun `the cycle rolls back with Remove and confirms an empty slot`() {
            val body = cycleBody()
            assertTrue(body.contains("ButtonBorrowCardRemove.click(iu)"), "the cycle taps Remove to roll back")
            assertTrue(body.contains("IconFriendSlotEmpty.check("), "the cycle confirms the friend slot returned to empty")
            assertTrue(body.contains("ROLLBACK_FAILED"), "a slot still filled after Remove fails closed")
        }
    }

    @Nested
    @DisplayName("registry + routing (arming fail-closed covers it)")
    inner class Registry {
        @Test
        fun `the key is in the canonical DebugTestGate registry`() {
            assertTrue(DebugTestGate.ALL_KEYS.contains(key), "ALL_KEYS must include the select-rollback key")
        }

        @Test
        fun `the key has a user-facing Debug Settings entry`() {
            assertTrue(debugUi.contains("\"$key\""), "the key is in the debugTestKeys list")
            assertTrue(debugUi.contains("Start Smart Borrow Select + Rollback Rehearsal"), "the diagnostic has a user-facing entry")
        }

        @Test
        fun `an existing settings db without the key defaults it to false`() {
            assertTrue(botState.contains("$key: false,"), "the default in BotStateContext must be false")
        }

        @Test
        fun `Campaign routes the key to a handler that invokes the navigator rehearsal`() {
            assertTrue(campaign.contains("\"$key\" to ::startSmartBorrowSelectRollbackTest"), "the fnMap routes the key")
            val handler = campaign.substring(campaign.indexOf("open fun startSmartBorrowSelectRollbackTest("))
            assertTrue(handler.contains("rehearseSmartBorrowSelectAndRollback("), "the handler invokes the navigator rehearsal")
        }
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val f = File(dir, relative)
            if (f.isFile) return f.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${System.getProperty("user.dir")}")
    }
}
