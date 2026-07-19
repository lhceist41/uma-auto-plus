package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Lifecycle tests for the career-finalization authorization: who may create it, who may destroy
 * it, and what survives in between.
 *
 * The defect these pin: the gate used to be cleared from the scenario Campaign's initializer as
 * "a new career invalidates the old verdict". The between-run navigator builds a throwaway Game
 * (and therefore a Campaign) during its own startup, so that clear ran a fraction of a second
 * after the finished career armed its verdict and erased it - the navigator then reported that
 * no usable verification existed and refused to press Finish. It failed safe, but an APPROVED
 * verdict would have been erased exactly the same way, so the guard could never authorize a
 * finish for any career.
 *
 * Authorization is therefore created only by [CareerFinalizeGate.beginCareer], which the real
 * career task calls at run start. Constructors must not mutate the gate at all - and because
 * Game and Campaign cannot be instantiated in a JVM unit test, that invariant is enforced here
 * by an automated source guard rather than left to review.
 */
@DisplayName("Career finalization authorization lifecycle")
class CareerFinalizeLifecycleTest {
    private fun verdict(token: String, armedAt: Long = 1_000_000L, approved: Boolean = true) =
        FinalizeVerdict(
            careerToken = token,
            queueRun = 2,
            trainee = "[Murmuring Stream] Super Creek",
            scenario = "Unity Cup",
            objective = "sparks",
            approved = approved,
            verifiedRemainingSp = 5,
            sessionTimestampMs = armedAt - 30_000L,
            reason = "test",
            armedAtMs = armedAt,
        )

    /** Stands in for everything the navigator does while starting up - building a throwaway Game
     * and the scenario Campaign behind it. After the fix that is pure construction, so the model
     * is "nothing happens to the gate", and the source guard below proves the real objects match. */
    private fun simulateNavigatorInitialisation() {
        // deliberately empty: constructors must not touch the gate
    }

    @BeforeEach
    @AfterEach
    fun isolate() {
        CareerFinalizeGate.reset()
    }

    @Nested
    @DisplayName("creation is a career-start event")
    inner class Creation {
        @Test
        fun `a real career start clears the previous verdict and installs a fresh identity`() {
            CareerFinalizeGate.arm(verdict("previous|Unity Cup|run1|old"))
            CareerFinalizeGate.beginCareer(nonce = "new1", queueRun = 2, nowMs = 5_000L)
            assertNull(CareerFinalizeGate.verdict, "the previous career's verdict must not survive a new run")
            assertEquals("new1", CareerFinalizeGate.context?.nonce)
            assertEquals(2, CareerFinalizeGate.context?.queueRun)
            assertEquals(5_000L, CareerFinalizeGate.context?.startedAtMs)
        }

        @Test
        fun `each career start creates a distinct identity, so back-to-back runs never share a token`() {
            CareerFinalizeGate.beginCareer(nonce = "runA", queueRun = 1, nowMs = 1L)
            val first = buildCareerFinalizeToken("Super Creek", "Unity Cup", 1, CareerFinalizeGate.context!!.nonce)
            CareerFinalizeGate.beginCareer(nonce = "runB", queueRun = 2, nowMs = 2L)
            val second = buildCareerFinalizeToken("Super Creek", "Unity Cup", 2, CareerFinalizeGate.context!!.nonce)
            assertFalse(first == second)
        }

        @Test
        fun `a process with no career start has no identity and no authorization`() {
            assertNull(CareerFinalizeGate.context, "process recreation must not inherit a career identity")
            assertNull(CareerFinalizeGate.verdict)
            assertFalse(finalizeVerdictUsable(CareerFinalizeGate.verdict, "any|token|run0|x", 1_000_000L))
        }
    }

    @Nested
    @DisplayName("construction never mutates authorization")
    inner class ConstructorSideEffects {
        @Test
        fun `a FINISH verdict survives navigator initialisation`() {
            CareerFinalizeGate.beginCareer(nonce = "n1", queueRun = null, nowMs = 0L)
            val token = buildCareerFinalizeToken("Super Creek", "Unity Cup", null, "n1")
            CareerFinalizeGate.arm(verdict(token, approved = true))
            simulateNavigatorInitialisation()
            assertNotNull(CareerFinalizeGate.verdict, "the navigator's own startup must not erase the verdict it consumes")
            assertTrue(finalizeVerdictUsable(CareerFinalizeGate.verdict, token, 1_000_050L))
            assertEquals(true, CareerFinalizeGate.verdict?.approved)
        }

        @Test
        fun `a BLOCK verdict survives navigator initialisation and is reported, not lost`() {
            CareerFinalizeGate.beginCareer(nonce = "n2", queueRun = null, nowMs = 0L)
            val token = buildCareerFinalizeToken("Super Creek", "Unity Cup", null, "n2")
            CareerFinalizeGate.arm(verdict(token, approved = false))
            simulateNavigatorInitialisation()
            assertEquals(false, CareerFinalizeGate.verdict?.approved)
            assertTrue(finalizeVerdictUsable(CareerFinalizeGate.verdict, token, 1_000_050L))
        }

        @Test
        fun `neither Game nor Campaign nor the navigator mutates the gate anywhere in their sources`() {
            // Game and Campaign need Android, so the invariant is enforced by reading the source:
            // the gate's mutating calls may appear only at the explicit lifecycle sites.
            val roots = listOf(
                "bot/Game.kt" to setOf<String>(),
                "bot/Campaign.kt" to setOf<String>(),
            )
            for ((relative, allowed) in roots) {
                val text = sourceFile(relative).readText()
                for (call in listOf("CareerFinalizeGate.clear(", "CareerFinalizeGate.beginCareer(", "CareerFinalizeGate.reset(")) {
                    if (call in allowed) continue
                    assertFalse(
                        call in text,
                        "$relative must not call $call - construction-time mutation is what erased the verdict the navigator consumes",
                    )
                }
            }
        }

        @Test
        fun `career-start authorization is created only by the queue run loop`() {
            val begin = "CareerFinalizeGate.beginCareer("
            val callers =
                sourceFiles().filter { it.name != "CareerFinalizeGate.kt" && begin in it.readText() }.map { it.name }.toSet()
            assertEquals(setOf("StartModule.kt"), callers, "only the real career task's run loop may create a career identity")
        }

        @Test
        fun `verdict invalidation happens only at the explicit lifecycle sites`() {
            val clear = "CareerFinalizeGate.clear("
            val callers =
                sourceFiles().filter { it.name != "CareerFinalizeGate.kt" && clear in it.readText() }.map { it.name }.toSet()
            assertEquals(
                setOf("StartModule.kt", "CareerLaunchNavigator.kt"),
                callers,
                "clears belong to the run-result path, the Home return, and the Finish consumption - nowhere else",
            )
        }
    }

    @Nested
    @DisplayName("invalidating lifecycle events")
    inner class Invalidation {
        @Test
        fun `manual stop, abort, error, breakpoint, and skipped run all clear`() {
            for (code in listOf(
                TaskResultCode.TASK_RESULT_MANUALLY_STOPPED,
                TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION,
                TaskResultCode.TASK_RESULT_BREAKPOINT_REACHED,
                TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE,
            )) {
                assertTrue(shouldClearVerdictForRunResult(code), code.name)
            }
        }

        @Test
        fun `only a completed career keeps its verdict for the finalize navigation`() {
            assertFalse(shouldClearVerdictForRunResult(TaskResultCode.TASK_RESULT_COMPLETE))
            for (code in TaskResultCode.entries.filter { it != TaskResultCode.TASK_RESULT_COMPLETE }) {
                assertTrue(shouldClearVerdictForRunResult(code), code.name)
            }
        }

        @Test
        fun `clearing drops the verdict but keeps the career identity`() {
            CareerFinalizeGate.beginCareer(nonce = "n3", queueRun = 1, nowMs = 0L)
            CareerFinalizeGate.arm(verdict("t"))
            CareerFinalizeGate.clear()
            assertNull(CareerFinalizeGate.verdict, "Home return and Finish consumption both drop the verdict")
            assertEquals("n3", CareerFinalizeGate.context?.nonce, "the career identity is only replaced by the next real career start")
        }

        @Test
        fun `a cleared verdict cannot authorize anything afterwards`() {
            CareerFinalizeGate.beginCareer(nonce = "n4", queueRun = 1, nowMs = 0L)
            val token = buildCareerFinalizeToken("Super Creek", "Unity Cup", 1, "n4")
            CareerFinalizeGate.arm(verdict(token))
            CareerFinalizeGate.clear()
            assertFalse(finalizeVerdictUsable(CareerFinalizeGate.verdict, token, 1_000_050L))
        }
    }

    @Nested
    @DisplayName("exact-career matching")
    inner class ExactCareer {
        @Test
        fun `a verdict from another career, run, trainee, scenario, or outfit never authorizes this one`() {
            val mine = buildCareerFinalizeToken("[Murmuring Stream] Super Creek", "Unity Cup", 2, "nonceA")
            val others = listOf(
                buildCareerFinalizeToken("[Murmuring Stream] Super Creek", "Unity Cup", 3, "nonceA"),
                buildCareerFinalizeToken("[Murmuring Stream] Super Creek", "Unity Cup", 2, "nonceB"),
                buildCareerFinalizeToken("[Blue Farm] Super Creek", "Unity Cup", 2, "nonceA"),
                buildCareerFinalizeToken("Super Creek", "Unity Cup", 2, "nonceA"),
                buildCareerFinalizeToken("[Murmuring Stream] Super Creek", "URA Finale", 2, "nonceA"),
            )
            for (other in others) {
                assertFalse(finalizeVerdictUsable(verdict(other), mine, 1_000_050L), "$other must not authorize $mine")
            }
            assertTrue(finalizeVerdictUsable(verdict(mine), mine, 1_000_050L))
        }

        @Test
        fun `an expired verdict is unusable even with the right token`() {
            val token = buildCareerFinalizeToken("Super Creek", "Unity Cup", 1, "n")
            val old = verdict(token, armedAt = 1_000_000L)
            assertTrue(finalizeVerdictUsable(old, token, 1_000_000L + FINALIZE_VERDICT_MAX_AGE_MS))
            assertFalse(finalizeVerdictUsable(old, token, 1_000_001L + FINALIZE_VERDICT_MAX_AGE_MS))
        }

        @Test
        fun `a verdict armed while the navigator was starting up is still matched by its token`() {
            // The navigator captures the token when navigation begins; helper objects created
            // during its startup neither change nor invalidate that token.
            CareerFinalizeGate.beginCareer(nonce = "n5", queueRun = null, nowMs = 0L)
            val token = buildCareerFinalizeToken("Super Creek", "Unity Cup", null, "n5")
            CareerFinalizeGate.arm(verdict(token))
            val captured = CareerFinalizeGate.verdict?.careerToken
            simulateNavigatorInitialisation()
            assertEquals(token, captured)
            assertTrue(finalizeVerdictUsable(CareerFinalizeGate.verdict, captured, 1_000_050L))
        }
    }

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
