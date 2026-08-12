package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The cross-layer launch-identity gate: the bot session must re-verify the React-verified
 * settings revision against what is actually on disk at load time, and abort before any game
 * interaction on a mismatch (a write landed in the time-of-check to time-of-use window).
 */
@DisplayName("Launch identity gate")
class LaunchIdentityGateTest {
    @BeforeEach
    @AfterEach
    fun reset() = LaunchIdentityGate.clear()

    @Test
    fun `no expectation yields NOT_SET (non-UI entry proceeds)`() {
        assertEquals(LaunchIdentityGate.Verdict.NOT_SET, LaunchIdentityGate.verdict(5))
        assertNull(LaunchIdentityGate.current)
    }

    @Test
    fun `matching revision passes`() {
        LaunchIdentityGate.setExpected(7, "abcd1234")
        assertEquals("abcd1234", LaunchIdentityGate.current?.hash)
        assertEquals(LaunchIdentityGate.Verdict.PASS, LaunchIdentityGate.verdict(7))
    }

    @Test
    fun `a revision that moved after verification is a MISMATCH (the TOCTOU window)`() {
        LaunchIdentityGate.setExpected(7, "abcd1234")
        assertEquals(LaunchIdentityGate.Verdict.MISMATCH, LaunchIdentityGate.verdict(8))
    }

    @Test
    fun `the expectation is single-use -- a stale identity cannot validate a later session`() {
        LaunchIdentityGate.setExpected(7, "h")
        assertEquals(LaunchIdentityGate.Verdict.PASS, LaunchIdentityGate.verdict(7))
        // No new setExpected: the next session must not inherit the consumed one.
        assertEquals(LaunchIdentityGate.Verdict.NOT_SET, LaunchIdentityGate.verdict(7))
    }

    @Test
    fun `a MISMATCH also consumes the expectation (no retry validates against the old one)`() {
        LaunchIdentityGate.setExpected(7, "h")
        assertEquals(LaunchIdentityGate.Verdict.MISMATCH, LaunchIdentityGate.verdict(9))
        assertEquals(LaunchIdentityGate.Verdict.NOT_SET, LaunchIdentityGate.verdict(7))
    }

    @Test
    fun `describe renders the expectation`() {
        LaunchIdentityGate.setExpected(3, "deadbeef")
        assertEquals("revision=3 hash=deadbeef", LaunchIdentityGate.describe(LaunchIdentityGate.current!!))
    }

    @Nested
    @DisplayName("source guard")
    inner class SourceGuard {
        @Test
        fun `the session entry verifies the identity BEFORE reading run settings or touching the game`() {
            val start = sourceFile("StartModule.kt").readText()
            val onStart = start.indexOf("fun onStartEvent(")
            assertTrue(onStart >= 0)
            val verdictAt = start.indexOf("LaunchIdentityGate.verdict(", onStart)
            // The launch-critical queue settings read is where run consumption begins.
            val queueRead = start.indexOf("SettingsHelper.getBooleanSetting(\"runQueue\", \"enableRunQueue\"", onStart)
            val projection = start.indexOf("startProjection(", onStart)
            assertTrue(verdictAt in (onStart + 1) until queueRead, "the identity verdict runs before the run settings are read")
            // A MISMATCH must return (abort) -- there is a `return` between the verdict and any projection.
            val mismatch = start.indexOf("Verdict.MISMATCH", verdictAt)
            val returnAfter = start.indexOf("return", mismatch)
            assertTrue(mismatch in verdictAt until (if (projection > 0) projection else start.length), "the mismatch branch is handled in onStartEvent")
            assertTrue(returnAfter > mismatch, "a mismatch aborts the session")
        }

        @Test
        fun `the React bridge method that hands over the verified identity exists`() {
            val start = sourceFile("StartModule.kt").readText()
            assertTrue("fun setVerifiedLaunchIdentity(" in start)
            assertTrue("LaunchIdentityGate.setExpected(" in start)
        }

        @Test
        fun `a NOT_SET start blocked after a mismatch fails closed before any run-settings navigation`() {
            val start = sourceFile("StartModule.kt").readText().replace("\r\n", "\n")
            val onStart = start.indexOf("fun onStartEvent(")
            assertTrue(onStart >= 0)
            val notSet = start.indexOf("Verdict.NOT_SET ->", onStart)
            assertTrue(notSet > 0, "the NOT_SET branch exists")
            val blockedCheck = start.indexOf("isBlockedAfterMismatch()", notSet)
            assertTrue(blockedCheck in notSet until (notSet + 1200), "the NOT_SET branch checks the sticky latch first")
            val blockedReturn = start.indexOf("return", blockedCheck)
            assertTrue(blockedReturn > blockedCheck, "a blocked NOT_SET returns (fails closed)")
            // The blocked return must precede the run-queue settings read that begins run consumption/navigation.
            val queueRead = start.indexOf("SettingsHelper.getBooleanSetting(\"runQueue\", \"enableRunQueue\"", onStart)
            assertTrue(queueRead > blockedReturn, "the blocked return runs before the run-queue settings are read")
        }

        @Test
        fun `the blocked NOT_SET path emits a high-signal error and keeps the legacy proceed for the unblocked case`() {
            val start = sourceFile("StartModule.kt").readText().replace("\r\n", "\n")
            val notSet = start.indexOf("Verdict.NOT_SET ->")
            val end = start.indexOf("nonUiEntry = true", notSet)
            assertTrue(notSet in 0 until end)
            val branch = start.substring(notSet, end + "nonUiEntry = true".length)
            assertTrue(branch.contains("MessageLog.e("), "the blocked case logs at error level")
            assertTrue(branch.contains("blocked after a prior launch-identity mismatch"), "the log names the mismatch cause")
            assertTrue(branch.contains("verified Start Queue"), "the log directs the operator back through the UI")
            // The latch check gates the fail-closed return; the legacy warn/proceed still exists for the unblocked NOT_SET.
            assertTrue(branch.indexOf("isBlockedAfterMismatch()") < branch.indexOf("MessageLog.w("), "the latch check precedes the legacy warn")
            assertTrue(branch.contains("nonUiEntry = true"), "the unblocked NOT_SET still proceeds (legacy trust-disk)")
        }

        @Test
        fun `the MISMATCH branch still aborts and the PASS branch still just verifies`() {
            val start = sourceFile("StartModule.kt").readText().replace("\r\n", "\n")
            val mismatch = start.indexOf("Verdict.MISMATCH ->")
            val pass = start.indexOf("Verdict.PASS ->")
            assertTrue(mismatch > 0 && pass > mismatch)
            // MISMATCH aborts (a return before the PASS branch begins).
            assertTrue(start.indexOf("return", mismatch) in mismatch until pass, "MISMATCH still returns immediately")
            // PASS is unchanged: it logs the verified line and neither returns nor touches the latch.
            val passBranch = start.substring(pass, start.indexOf("Verdict.NOT_SET ->", pass))
            assertTrue(passBranch.contains("launch identity verified"), "PASS still logs the verified line")
            assertFalse(passBranch.contains("return"), "PASS does not abort")
            assertFalse(passBranch.contains("isBlockedAfterMismatch"), "PASS does not consult the latch")
        }
    }

    @Nested
    @DisplayName("sticky mismatch guard")
    inner class StickyMismatchGuard {
        @Test
        fun `a fresh gate is not blocked`() {
            assertFalse(LaunchIdentityGate.isBlockedAfterMismatch())
        }

        @Test
        fun `a MISMATCH latches the blocked state`() {
            LaunchIdentityGate.setExpected(7, "h")
            assertEquals(LaunchIdentityGate.Verdict.MISMATCH, LaunchIdentityGate.verdict(9))
            assertTrue(LaunchIdentityGate.isBlockedAfterMismatch())
        }

        @Test
        fun `a PASS never latches the blocked state`() {
            LaunchIdentityGate.setExpected(7, "h")
            assertEquals(LaunchIdentityGate.Verdict.PASS, LaunchIdentityGate.verdict(7))
            assertFalse(LaunchIdentityGate.isBlockedAfterMismatch())
        }

        @Test
        fun `an ordinary NOT_SET before any mismatch stays unblocked (Scenario D fresh-process legacy entry)`() {
            assertEquals(LaunchIdentityGate.Verdict.NOT_SET, LaunchIdentityGate.verdict(5))
            assertFalse(LaunchIdentityGate.isBlockedAfterMismatch())
        }

        @Test
        fun `Scenario A plus B -- the second start after a mismatch is a still-blocked NOT_SET`() {
            // UI expected N+1, disk loaded N -> MISMATCH -> blocked; a second PLAY with no new setExpected
            // reaches NOT_SET but the latch is still set, so the caller must refuse (the live incident).
            LaunchIdentityGate.setExpected(8, "h")
            assertEquals(LaunchIdentityGate.Verdict.MISMATCH, LaunchIdentityGate.verdict(7))
            assertTrue(LaunchIdentityGate.isBlockedAfterMismatch())
            assertEquals(LaunchIdentityGate.Verdict.NOT_SET, LaunchIdentityGate.verdict(7))
            assertTrue(LaunchIdentityGate.isBlockedAfterMismatch())
        }

        @Test
        fun `repeated unverified reads stay blocked until a fresh setExpected`() {
            LaunchIdentityGate.setExpected(8, "h")
            LaunchIdentityGate.verdict(7)
            repeat(3) {
                assertEquals(LaunchIdentityGate.Verdict.NOT_SET, LaunchIdentityGate.verdict(7))
                assertTrue(LaunchIdentityGate.isBlockedAfterMismatch())
            }
        }

        @Test
        fun `Scenario C -- a fresh UI setExpected clears the block and re-arms verification`() {
            LaunchIdentityGate.setExpected(8, "h")
            LaunchIdentityGate.verdict(7)
            assertTrue(LaunchIdentityGate.isBlockedAfterMismatch())
            LaunchIdentityGate.setExpected(9, "h2")
            assertFalse(LaunchIdentityGate.isBlockedAfterMismatch())
            assertEquals(LaunchIdentityGate.Verdict.PASS, LaunchIdentityGate.verdict(9))
        }

        @Test
        fun `Scenario E -- a mismatch after a re-arm latches again`() {
            LaunchIdentityGate.setExpected(8, "h")
            LaunchIdentityGate.verdict(7)
            LaunchIdentityGate.setExpected(9, "h2")
            assertFalse(LaunchIdentityGate.isBlockedAfterMismatch())
            assertEquals(LaunchIdentityGate.Verdict.MISMATCH, LaunchIdentityGate.verdict(8))
            assertTrue(LaunchIdentityGate.isBlockedAfterMismatch())
        }

        @Test
        fun `clear resets the block (process-local reset, standing in for a process restart)`() {
            LaunchIdentityGate.setExpected(8, "h")
            LaunchIdentityGate.verdict(7)
            assertTrue(LaunchIdentityGate.isBlockedAfterMismatch())
            LaunchIdentityGate.clear()
            assertFalse(LaunchIdentityGate.isBlockedAfterMismatch())
            assertEquals(LaunchIdentityGate.Verdict.NOT_SET, LaunchIdentityGate.verdict(7))
            assertFalse(LaunchIdentityGate.isBlockedAfterMismatch())
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
