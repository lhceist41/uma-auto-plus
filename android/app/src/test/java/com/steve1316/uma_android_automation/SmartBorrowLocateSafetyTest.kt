package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * DeckLab Smart Borrow 2.0 diagnostics safety + wiring (source guards).
 *
 * The locate rehearsal and the Remove probe drive real OCR and real taps, so their behaviour cannot be
 * unit-tested directly. These guards pin the load-bearing invariants against the source: the locate
 * stage taps NO card and never reaches Start Career, the Remove probe never reaches Start Career, and
 * both diagnostics are registered and routed so the arming fail-closed covers them.
 */
@DisplayName("Smart Borrow locate + remove-probe safety")
class SmartBorrowLocateSafetyTest {
    private val locateKey = "debugMode_startSmartBorrowLocateTest"
    private val probeKey = "debugMode_startBorrowRemoveProbeTest"

    private val nav by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt") }
    private val campaign by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt") }
    private val debugUi by lazy { source("src/pages/DebugSettings/index.tsx") }
    private val botState by lazy { source("src/context/BotStateContext.tsx") }

    private fun slice(signature: String, next: String): String {
        val start = nav.indexOf(signature)
        assertTrue(start >= 0, "$signature exists")
        val end = nav.indexOf(next, start + signature.length)
        assertTrue(end > start, "$next follows $signature")
        return nav.substring(start, end)
    }

    private fun locateBody() = slice("internal fun locateSmartBorrowIntentReadOnly(", "private fun persistSmartBorrowLocate(")

    private fun probeBody() = slice("internal fun probeBorrowRemoveBehavior(", "private fun persistBorrowRemoveProbe(")

    @Nested
    @DisplayName("locate stage taps no card and cannot reach Start Career")
    inner class LocateSafety {
        @Test
        fun `the locate never presses Start Career`() {
            val body = locateBody()
            assertFalse(body.contains("ButtonStartCareer"), "the locate must never reference the Start Career button")
        }

        @Test
        fun `the locate never taps a borrow row`() {
            val body = locateBody()
            assertFalse(body.contains("trySmartBorrowPick"), "the locate must not run the production pick (which taps a row)")
            assertFalse(body.contains("CoordinateTap.tap(gestureUtils, 540.0"), "the locate must not tap a borrow row at the row-center coordinate")
        }

        @Test
        fun `the locate reads the pool and closes the picker without selecting`() {
            val body = locateBody()
            assertTrue(body.contains("readBorrowPoolRowsRich("), "the locate uses the read-only census reader")
            assertTrue(body.contains("SmartBorrowLocator.locate("), "the locate resolves the intent to a row purely")
            assertTrue(body.contains("ButtonClose.click(iu)"), "the locate closes the picker it opened")
            assertTrue(body.contains("Stage A locate-only"), "the locate states it dispatches no tap")
        }
    }

    @Nested
    @DisplayName("remove probe stays within its bounds")
    inner class ProbeSafety {
        @Test
        fun `the probe never presses Start Career`() {
            assertFalse(probeBody().contains("ButtonStartCareer"), "the Remove probe must never reference the Start Career button")
        }

        @Test
        fun `the probe fails closed on an already-empty slot`() {
            assertTrue(probeBody().contains("FRIEND_SLOT_ALREADY_EMPTY"), "the probe requires a card to remove")
        }

        @Test
        fun `the probe taps only the Remove control`() {
            assertTrue(probeBody().contains("ButtonBorrowCardRemove.click(iu)"), "the probe taps the Remove control")
        }
    }

    @Nested
    @DisplayName("registry + routing (arming fail-closed covers both)")
    inner class Registry {
        @Test
        fun `both keys are in the canonical DebugTestGate registry`() {
            assertTrue(DebugTestGate.ALL_KEYS.contains(locateKey), "ALL_KEYS must include the locate key")
            assertTrue(DebugTestGate.ALL_KEYS.contains(probeKey), "ALL_KEYS must include the probe key")
        }

        @Test
        fun `both keys have user-facing Debug Settings entries`() {
            assertTrue(debugUi.contains("\"$locateKey\""), "the locate key is in the debugTestKeys list")
            assertTrue(debugUi.contains("Start Smart Borrow Locate Rehearsal"), "the locate has a user-facing entry")
            assertTrue(debugUi.contains("\"$probeKey\""), "the probe key is in the debugTestKeys list")
            assertTrue(debugUi.contains("Start Borrow Remove Probe"), "the probe has a user-facing entry")
        }

        @Test
        fun `an existing settings db without the keys defaults both to false`() {
            assertTrue(botState.contains("$locateKey: false,"), "the locate default in BotStateContext must be false")
            assertTrue(botState.contains("$probeKey: false,"), "the probe default in BotStateContext must be false")
        }

        @Test
        fun `Campaign routes both keys to handlers that invoke the navigator diagnostics`() {
            assertTrue(campaign.contains("\"$locateKey\" to ::startSmartBorrowLocateTest"), "the fnMap routes the locate key")
            assertTrue(campaign.contains("\"$probeKey\" to ::startBorrowRemoveProbeTest"), "the fnMap routes the probe key")
            val locateHandler = campaign.substring(campaign.indexOf("open fun startSmartBorrowLocateTest("))
            assertTrue(locateHandler.contains("locateSmartBorrowIntentReadOnly("), "the locate handler invokes the navigator locate")
            val probeHandler = campaign.substring(campaign.indexOf("open fun startBorrowRemoveProbeTest("))
            assertTrue(probeHandler.contains("probeBorrowRemoveBehavior("), "the probe handler invokes the navigator probe")
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
