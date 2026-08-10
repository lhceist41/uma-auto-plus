package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Single-run trainee identity hardening.
 *
 * A queued rotation-off launch (enableRunQueue=true) passed a blank single-run target and had no
 * rotation target either, so the navigator armed no trainee, tapped through Trainee Select onto the
 * game's sticky preselection, and started that career under the applied preset's token (the
 * 2026-08-10 Taiki-under-Symboli mislabel). The pure [SingleRunTraineeGate] branch logic is pinned
 * here; the source guards prove the navigator wires it so no unverified targeted single run can
 * reach a Start-Career-bearing screen.
 */
@DisplayName("Single-run trainee identity gate")
class SingleRunTraineeGateTest {
    @Nested
    @DisplayName("resolveTarget")
    inner class ResolveTarget {
        @Test
        fun `blank target with rotation off derives the applied preset (the queue-launch fix)`() {
            assertEquals(
                "[Emperor's Path] Symboli Rudolf",
                SingleRunTraineeGate.resolveTarget(
                    finalizeToHome = false,
                    passedTarget = "",
                    rotationEnabled = false,
                    appliedPresetTrainee = "[Emperor's Path] Symboli Rudolf",
                ),
            )
        }

        @Test
        fun `rotation on leaves the single-run target blank so rotation keeps ownership`() {
            assertEquals(
                "",
                SingleRunTraineeGate.resolveTarget(
                    finalizeToHome = false,
                    passedTarget = "",
                    rotationEnabled = true,
                    appliedPresetTrainee = "Biwa Hayahide",
                ),
            )
        }

        @Test
        fun `an explicit caller target wins over everything (manual single-run path)`() {
            assertEquals(
                "Mejiro Ryan",
                SingleRunTraineeGate.resolveTarget(
                    finalizeToHome = false,
                    passedTarget = "Mejiro Ryan",
                    rotationEnabled = true,
                    appliedPresetTrainee = "Biwa Hayahide",
                ),
            )
        }

        @Test
        fun `finalize-to-home arms no target (a career is ending, none is launching)`() {
            assertEquals(
                "",
                SingleRunTraineeGate.resolveTarget(
                    finalizeToHome = true,
                    passedTarget = "",
                    rotationEnabled = false,
                    appliedPresetTrainee = "Biwa Hayahide",
                ),
            )
        }

        @Test
        fun `no preset and rotation off leaves it blank (a genuine manual launch is unchanged)`() {
            assertEquals(
                "",
                SingleRunTraineeGate.resolveTarget(
                    finalizeToHome = false,
                    passedTarget = "",
                    rotationEnabled = false,
                    appliedPresetTrainee = "",
                ),
            )
        }
    }

    @Nested
    @DisplayName("resolveExcludes tracks resolveTarget")
    inner class ResolveExcludes {
        @Test
        fun `blank target with rotation off derives the applied preset excludes`() {
            assertEquals(
                "[Kukulkan Warrior] El Condor Pasa",
                SingleRunTraineeGate.resolveExcludes(
                    finalizeToHome = false,
                    passedTarget = "",
                    passedExcludes = "",
                    rotationEnabled = false,
                    appliedPresetExcludes = "[Kukulkan Warrior] El Condor Pasa",
                ),
            )
        }

        @Test
        fun `an explicit caller target uses the caller excludes`() {
            assertEquals(
                "sibling",
                SingleRunTraineeGate.resolveExcludes(
                    finalizeToHome = false,
                    passedTarget = "Mejiro Ryan",
                    passedExcludes = "sibling",
                    rotationEnabled = false,
                    appliedPresetExcludes = "other",
                ),
            )
        }

        @Test
        fun `rotation and finalize leave excludes blank`() {
            assertEquals("", SingleRunTraineeGate.resolveExcludes(false, "", "x", true, "y"))
            assertEquals("", SingleRunTraineeGate.resolveExcludes(true, "", "x", false, "y"))
        }
    }

    @Nested
    @DisplayName("mustFailClosed")
    inner class MustFailClosed {
        @Test
        fun `an armed target that is not verified this attempt must fail closed`() {
            assertTrue(SingleRunTraineeGate.mustFailClosed("Symboli Rudolf", verifiedThisAttempt = false))
        }

        @Test
        fun `an armed target that IS verified this attempt may advance`() {
            assertFalse(SingleRunTraineeGate.mustFailClosed("Symboli Rudolf", verifiedThisAttempt = true))
        }

        @Test
        fun `no armed target never forces a failure (manual launches are unaffected)`() {
            assertFalse(SingleRunTraineeGate.mustFailClosed("", verifiedThisAttempt = false))
            assertFalse(SingleRunTraineeGate.mustFailClosed("", verifiedThisAttempt = true))
        }
    }

    @Nested
    @DisplayName("navigator wiring (source guard)")
    inner class NavigatorWiring {
        private val nav by lazy { sourceFile("CareerLaunchNavigator.kt").readText().replace("\r\n", "\n") }

        @Test
        fun `navigate resolves the single-run target through the gate, not a raw assignment`() {
            assertTrue(
                nav.contains("singleRunTraineeTarget =\n") && nav.contains("SingleRunTraineeGate.resolveTarget("),
                "navigate() must derive the target via SingleRunTraineeGate.resolveTarget",
            )
            assertTrue(nav.contains("SingleRunTraineeGate.resolveExcludes("), "excludes are resolved through the gate too")
        }

        @Test
        fun `verification is reset per navigate attempt`() {
            assertTrue(nav.contains("singleRunTraineeSelectHandled = false"), "each navigate() clears the verified latch")
        }

        @Test
        fun `the verified latch is set only from markSingleRunTraineeVerified, never on handler entry`() {
            // Exactly one place assigns the latch true, and it is the guarded helper.
            val trueAssignments = Regex("singleRunTraineeSelectHandled = true").findAll(nav).count()
            assertEquals(1, trueAssignments, "the only 'verified = true' assignment is inside markSingleRunTraineeVerified")
            val helper = nav.indexOf("private fun markSingleRunTraineeVerified()")
            assertTrue(helper >= 0, "the verified-marker helper exists")
            val assignAt = nav.indexOf("singleRunTraineeSelectHandled = true")
            val helperEnd = nav.indexOf("\n    }\n", helper)
            assertTrue(assignAt in helper until helperEnd, "the true assignment lives inside the helper body")
        }

        @Test
        fun `each of the three verified-advance points marks verification`() {
            assertEquals(
                3,
                Regex("markSingleRunTraineeVerified\\(\\)").findAll(nav).count() - 1, // minus the definition
                "the fast path, remembered-position path, and full-scan match each mark verification",
            )
        }

        @Test
        fun `all three Start-Career-bearing screens fail closed through the gate`() {
            assertEquals(
                3,
                Regex("SingleRunTraineeGate\\.mustFailClosed\\(").findAll(nav).count(),
                "Legacy Select, Support Deck, and Pre-Run Confirmation each carry the identity backstop",
            )
        }

        @Test
        fun `the support-deck backstop runs before the reuse gate so reuse cannot bypass it`() {
            val deck = nav.indexOf("private fun handleSupportDeckScreen(")
            assertTrue(deck >= 0)
            val gate = nav.indexOf("SingleRunTraineeGate.mustFailClosed(", deck)
            val reuse = nav.indexOf("if (!reuseLastLaunchSetup) {", deck)
            assertTrue(gate in deck until reuse, "the identity check precedes the reuseLastLaunchSetup check in the deck handler")
        }

        @Test
        fun `the pre-run confirmation backstop runs before the Start Career click`() {
            val conf = nav.indexOf("private fun handlePreRunConfirmation(")
            assertTrue(conf >= 0)
            val gate = nav.indexOf("SingleRunTraineeGate.mustFailClosed(", conf)
            val click = nav.indexOf("Clicking 'Start Career!'", conf)
            assertTrue(gate in conf until click, "the identity check precedes the Start Career tap")
        }
    }

    @Nested
    @DisplayName("rotation non-regression (source guard)")
    inner class RotationNonRegression {
        private val nav by lazy { sourceFile("CareerLaunchNavigator.kt").readText().replace("\r\n", "\n") }

        @Test
        fun `rotation still targets queueState currentTrainee, not the single-run target`() {
            assertTrue(
                nav.contains("else SettingsHelper.getStringSetting(\"queueState\", \"currentTrainee\")"),
                "the non-single-run branch reads the rotation-managed currentTrainee",
            )
        }

        @Test
        fun `rotation still disarms its switch-pending flag on handler entry`() {
            assertTrue(
                nav.contains("if (!singleRunMode) {") && nav.contains("StartModule.setRotationSwitchPending(context, false)"),
                "rotation's entry disarm is preserved and gated to the non-single-run branch",
            )
        }

        @Test
        fun `a no-match roster scan still stops rather than launch the wrong trainee`() {
            assertTrue(nav.contains("Stopping to avoid running the wrong trainee."), "the scan-out failure is unchanged")
        }
    }

    @Nested
    @DisplayName("Trainee.readName retry port (source guard)")
    inner class ReadNameRetry {
        private val trainee by lazy { sourceFile("types/Trainee.kt").readText().replace("\r\n", "\n") }

        @Test
        fun `a missing anchor no longer caches the string null`() {
            assertFalse(trainee.contains("name = \"null\""), "the null placeholder that poisoned retries is gone")
        }

        @Test
        fun `a missing anchor warns and returns so a later read can retry`() {
            val readName = trainee.indexOf("fun readName(")
            assertTrue(readName >= 0)
            val nullCheck = trainee.indexOf("if (refPoint == null) {", readName)
            assertTrue(nullCheck >= 0)
            val block = trainee.substring(nullCheck, trainee.indexOf("}", nullCheck) + 1)
            assertTrue(block.contains("MessageLog.w"), "the missing-anchor branch logs a warning")
            assertTrue(block.contains("return"), "the missing-anchor branch returns without setting a name")
            assertFalse(block.contains("name ="), "the missing-anchor branch does not overwrite the name")
        }

        @Test
        fun `a successful read still sets the name and the log-file prefix`() {
            assertTrue(trainee.contains("name = detectedName"), "successful reads are unchanged")
            assertTrue(trainee.contains("MessageLog.logFileNamePrefix = name.replace(\" \", \"_\")"), "log prefix behavior is unchanged")
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
