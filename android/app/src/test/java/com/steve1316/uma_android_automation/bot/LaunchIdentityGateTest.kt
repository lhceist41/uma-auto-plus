package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
