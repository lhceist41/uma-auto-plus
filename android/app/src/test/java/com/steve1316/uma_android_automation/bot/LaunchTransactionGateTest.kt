package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Correlation lifecycle for the launch-transaction id: the handoff that lets a lineage read taken
 * on the Legacy Select screen (during launch navigation, before the new career attaches) be joined
 * to the career it belongs to - and, critically, NOT to the previous career.
 *
 * The hazard these pin is the between-run off-by-one: [CareerFinalizeGate.context] and
 * [SparkRerollGate.transaction] both still describe the PREVIOUS career while the next launch is
 * being navigated, so a lineage read must never borrow either one. The lineage capture reads
 * [LaunchTransactionGate.pending] (minted for THIS launch), and the new career adopts it as
 * [LaunchTransactionGate.active] only when it actually attaches.
 */
@DisplayName("Launch-transaction correlation lifecycle")
class LaunchTransactionGateTest {
    @BeforeEach
    @AfterEach
    fun isolate() {
        LaunchTransactionGate.reset()
        CareerFinalizeGate.reset()
    }

    private fun deterministicProcess(nonce: String = "proc") = LaunchTransactionGate.initProcess(nonce)

    @Nested
    @DisplayName("minting and adoption")
    inner class MintAdopt {
        @Test
        fun `a cold launch mints a pending id that the attaching career adopts as active`() {
            deterministicProcess()
            assertNull(LaunchTransactionGate.pending)
            assertNull(LaunchTransactionGate.active)

            val pending = LaunchTransactionGate.beginLaunch(nowMs = 1_000L)
            assertNotNull(pending.id)
            assertEquals(pending, LaunchTransactionGate.pending, "the lineage capture reads exactly this pending id")
            assertNull(LaunchTransactionGate.active, "no career has attached yet")

            val adopted = LaunchTransactionGate.adopt(nowMs = 2_000L)
            assertEquals(pending.id, adopted.id, "the career adopts the id the launch minted")
            assertEquals(adopted, LaunchTransactionGate.active)
            assertNull(LaunchTransactionGate.pending, "adoption clears pending so no later career can re-adopt it")
        }

        @Test
        fun `adoption without a pending id mints a fresh active id - a resumed career or a mid-career restart`() {
            deterministicProcess()
            val adopted = LaunchTransactionGate.adopt(nowMs = 5_000L)
            assertNotNull(adopted.id)
            assertEquals(adopted, LaunchTransactionGate.active)
            assertNull(LaunchTransactionGate.pending)
        }

        @Test
        fun `a next launch adopts its own pending id, never the previous career's`() {
            deterministicProcess()
            // Career 1.
            LaunchTransactionGate.beginLaunch(nowMs = 1L)
            val firstActive = LaunchTransactionGate.adopt(nowMs = 2L)
            // Career 2 (between-run launch).
            val secondPending = LaunchTransactionGate.beginLaunch(nowMs = 3L)
            assertNotEquals(firstActive.id, secondPending.id, "the between-run launch must not reuse the previous run's id")
            // While career 2 is being navigated, active still holds career 1's id until attach.
            assertEquals(firstActive.id, LaunchTransactionGate.active?.id)
            val secondActive = LaunchTransactionGate.adopt(nowMs = 4L)
            assertEquals(secondPending.id, secondActive.id)
            assertNotEquals(firstActive.id, secondActive.id)
        }
    }

    @Nested
    @DisplayName("retry and staleness safety")
    inner class Safety {
        @Test
        fun `a capture retried within one launch pass keeps a single pending id`() {
            deterministicProcess()
            val minted = LaunchTransactionGate.beginLaunch(nowMs = 1L)
            // The lineage capture reads pending; a transient read failure that re-reads it must see
            // the same id (reading never mints).
            val firstRead = LaunchTransactionGate.pending
            val secondRead = LaunchTransactionGate.pending
            assertEquals(minted.id, firstRead?.id)
            assertEquals(minted.id, secondRead?.id)
        }

        @Test
        fun `an un-adopted pending from an abandoned launch is discarded by the next launch`() {
            deterministicProcess()
            val abandoned = LaunchTransactionGate.beginLaunch(nowMs = 1L)
            // The launch never attached a career (aborted navigation). The next launch mints anew.
            val fresh = LaunchTransactionGate.beginLaunch(nowMs = 2L)
            assertNotEquals(abandoned.id, fresh.id)
            assertEquals(fresh.id, LaunchTransactionGate.pending?.id)
            // The abandoned id can never become active.
            val adopted = LaunchTransactionGate.adopt(nowMs = 3L)
            assertEquals(fresh.id, adopted.id)
        }

        @Test
        fun `a stale finalize context cannot contaminate the lineage id`() {
            deterministicProcess()
            // The previous career's finalize identity is still installed while the next launch is
            // navigated - exactly the state during between-run lineage capture.
            CareerFinalizeGate.beginCareer(nonce = "prevCareerNonce", queueRun = 1, nowMs = 0L)
            val pending = LaunchTransactionGate.beginLaunch(nowMs = 1L)
            // The lineage id is independent of the finalize context; it never derives from it.
            assertFalse(pending.id.contains("prevCareerNonce"))
            assertNotNull(CareerFinalizeGate.context, "the finalize context legitimately still holds the previous career")
            assertEquals("prevCareerNonce", CareerFinalizeGate.context?.nonce)
        }

        @Test
        fun `every launch in a process gets a distinct id`() {
            deterministicProcess()
            val ids = mutableSetOf<String>()
            repeat(50) {
                ids.add(LaunchTransactionGate.beginLaunch(nowMs = it.toLong()).id)
                LaunchTransactionGate.adopt(nowMs = it.toLong())
            }
            assertEquals(50, ids.size, "no two launches in one process may share an id")
        }

        @Test
        fun `initProcess is idempotent so ids cannot be renumbered mid-process`() {
            LaunchTransactionGate.initProcess("first")
            LaunchTransactionGate.initProcess("second")
            val id = LaunchTransactionGate.beginLaunch(nowMs = 1L).id
            assertTrue(id.startsWith("first-"), "the first process nonce wins; a later call is a no-op")
        }

        @Test
        fun `invalidate drops the active id`() {
            deterministicProcess()
            LaunchTransactionGate.beginLaunch(nowMs = 1L)
            LaunchTransactionGate.adopt(nowMs = 2L)
            assertNotNull(LaunchTransactionGate.active)
            LaunchTransactionGate.invalidate()
            assertNull(LaunchTransactionGate.active)
        }
    }

    @Nested
    @DisplayName("only explicit lifecycle sites mutate the gate")
    inner class SourceGuards {
        @Test
        fun `pending ids are minted only by the launch navigator`() {
            val callers = callersOf("LaunchTransactionGate.beginLaunch(")
            assertEquals(setOf("CareerLaunchNavigator.kt"), callers, "beginLaunch belongs to the navigate() launch pass and nowhere else")
        }

        @Test
        fun `adoption happens only at the Game career-attachment boundary`() {
            val callers = callersOf("LaunchTransactionGate.adopt(")
            assertEquals(setOf("Game.kt"), callers, "adopt belongs to the career-attachment boundary in Game.start and nowhere else")
        }

        @Test
        fun `the scenario Campaign never touches the gate - constructors must stay side-effect free`() {
            val text = sourceFile("bot/Campaign.kt").readText()
            for (call in listOf("LaunchTransactionGate.beginLaunch(", "LaunchTransactionGate.adopt(", "LaunchTransactionGate.reset(")) {
                assertFalse(call in text, "Campaign.kt must not call $call - a constructor-time mutation is the defect this design avoids")
            }
        }
    }

    private fun callersOf(call: String): Set<String> =
        sourceFiles().filter { it.name != "LaunchTransactionGate.kt" && call in it.readText() }.map { it.name }.toSet()

    private fun sourceFile(relative: String): File = File(sourceRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

    private fun sourceFiles(): List<File> = sourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun sourceRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(5) {
            val candidate = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (candidate.isDirectory) return candidate
            val fromRepoRoot = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (fromRepoRoot.isDirectory) return fromRepoRoot
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
