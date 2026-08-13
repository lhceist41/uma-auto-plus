package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * StartModule outer-orchestration gate for debug diagnostics.
 *
 * The Game.kt fail-closed gate (which stops the bot inside
 * Game.start() when a diagnostic is armed but ran nothing) can be bypassed by the run queue:
 * StartModule presses Start Career in two places OUTSIDE Game.start() -- the cold-start launch
 * before run 1, and the between-run LAUNCH_NEXT -- so an armed diagnostic under a queue could still
 * start a real career (and spend TP) before or between diagnostic reads.
 *
 * The global invariant: when ANY debug diagnostic is armed, StartModule must never execute a
 * career-launching navigation; it runs exactly one diagnostic, then terminates. These are source
 * guards (StartModule is a React module that is impractical to unit-test directly) pinning the
 * wiring topology that enforces it: one canonical registry read, cold-start suppression, and a
 * single-shot break placed so both career-launching navigations become unreachable.
 */
@DisplayName("StartModule debug-diagnostic launch gate")
class StartModuleDebugGateTest {
    private val startModule by lazy {
        repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt")
            .readText().replace("\r\n", "\n")
    }
    private val game by lazy {
        repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Game.kt")
            .readText().replace("\r\n", "\n")
    }

    @Nested
    @DisplayName("one canonical registry (no second key list)")
    inner class CanonicalRegistry {
        @Test
        fun `StartModule resolves diagnostic intent through DebugTestGate with the debug category`() {
            assertTrue(
                startModule.contains("DebugTestGate.requested { key -> SettingsHelper.getBooleanSetting(\"debug\", key) }"),
                "StartModule must resolve armed diagnostics via the canonical DebugTestGate registry, same as Game.kt",
            )
        }

        @Test
        fun `StartModule hardcodes no debug-test key strings`() {
            val hardcoded = Regex("\"debugMode_\\w+\"").findAll(startModule).map { it.value }.toList()
            assertTrue(hardcoded.isEmpty(), "StartModule must not duplicate debug-test keys; found $hardcoded")
        }
    }

    @Nested
    @DisplayName("cold-start launch suppression (cold-start bypass)")
    inner class ColdStartSuppression {
        @Test
        fun `the armed set is resolved before the cold-start career launch`() {
            val resolve = startModule.indexOf("val debugDiagnosticArmed")
            val coldStartLaunch = startModule.indexOf("navigateWithDeadline(coldStartReuse")
            assertTrue(resolve >= 0, "debugDiagnosticArmed must be resolved")
            assertTrue(coldStartLaunch >= 0, "the cold-start launch site must exist")
            assertTrue(resolve < coldStartLaunch, "diagnostic intent must be resolved before the cold-start launch")
        }

        @Test
        fun `an armed diagnostic suppresses the cold-start launch and the legacy launch is the else branch`() {
            val suppression = startModule.indexOf("[DEBUG-TEST] StartModule launch suppressed")
            val coldStartLaunch = startModule.indexOf("navigateWithDeadline(coldStartReuse")
            assertTrue(
                suppression in 0 until coldStartLaunch,
                "the launch-suppressed branch must precede the cold-start launch",
            )
            // The original cold-start condition must now be reached only when NOT armed.
            assertTrue(
                startModule.contains("} else if (enableRunQueue && startFromRun <= totalRuns && BotService.isRunning && !queueStopRequested) {"),
                "the legacy cold-start launch must be gated as the else branch of the armed check",
            )
        }
    }

    @Nested
    @DisplayName("single-shot: no LAUNCH_NEXT, no career-end nav (LAUNCH_NEXT bypass)")
    inner class SingleShot {
        private val runGame = startModule.indexOf("val result = runSingleGame()")
        private val singleShot = startModule.indexOf("[DEBUG-TEST] Diagnostic run complete")
        private val decide = startModule.indexOf("decidePostCareerAction(", runGame)
        private val launchNext = startModule.indexOf("navigateWithDeadline(nextReuse", decide)

        @Test
        fun `the diagnostic single-shot break runs after the game and before the post-career decision`() {
            assertTrue(runGame >= 0 && singleShot >= 0 && decide >= 0, "the run, single-shot log, and decision must all exist")
            assertTrue(runGame < singleShot, "the single-shot break must be evaluated after the diagnostic run returns")
            assertTrue(singleShot < decide, "the single-shot break must run before decidePostCareerAction")
        }

        @Test
        fun `the single-shot break is gated on debugDiagnosticArmed and breaks the loop`() {
            val guard = startModule.lastIndexOf("if (debugDiagnosticArmed)", singleShot)
            assertTrue(guard in runGame until singleShot, "the single-shot break must be gated on debugDiagnosticArmed after the run")
            val region = startModule.substring(guard, decide)
            assertTrue(region.contains("break"), "an armed diagnostic must break the run loop (single-shot)")
        }

        @Test
        fun `LAUNCH_NEXT navigation sits after the decision, unreachable once the diagnostic has broken out`() {
            assertTrue(launchNext > decide, "LAUNCH_NEXT navigation must sit after the post-career decision")
            assertTrue(decide > singleShot, "the diagnostic break precedes the decision, so LAUNCH_NEXT is unreachable when armed")
        }

        @Test
        fun `the single-shot override does not mutate totalRuns`() {
            val region = startModule.substring(singleShot, decide)
            assertFalse(region.contains("totalRuns"), "the diagnostic single-shot path must not touch the user's totalRuns")
            assertTrue(startModule.contains("val totalRuns ="), "totalRuns is an immutable binding read from settings")
        }
    }

    @Nested
    @DisplayName("queue-state neutrality (armed diagnostic must not touch resume state)")
    inner class QueueStateNeutrality {
        private val resolve = startModule.indexOf("val debugDiagnosticArmed")
        private val diagRun = startModule.indexOf("val diagnosticResult = runSingleGame()")
        private val loopRun = startModule.indexOf("val result = runSingleGame()")
        private val rotationParse = startModule.indexOf("val rotation = loadRotationConfig()", diagRun)
        private val firstLoadQueueState = startModule.indexOf("loadQueueState(context)")
        private val phaseCareerWrite = startModule.indexOf("phase = PHASE_CAREER")

        @Test
        fun `the diagnostic runs exactly one runSingleGame, distinct from and before the queue loop`() {
            assertTrue(resolve in 0 until diagRun, "the diagnostic runSingleGame must follow intent resolution")
            assertTrue(diagRun in 0 until loopRun, "the diagnostic runSingleGame is a distinct early call, before the queue loop's run")
        }

        @Test
        fun `the diagnostic branch returns before the rotation parse and all queue bookkeeping`() {
            val branchReturn = startModule.indexOf("return", diagRun)
            assertTrue(rotationParse > diagRun, "the rotation parse and queue lifecycle must sit after the diagnostic branch")
            assertTrue(branchReturn in diagRun until rotationParse, "the diagnostic branch must return before any queue bookkeeping")
        }

        @Test
        fun `the diagnostic branch reads no saved queue state and writes or clears none`() {
            assertTrue(diagRun in 0 until firstLoadQueueState, "the diagnostic must execute before the resume-state read (loadQueueState)")
            val branchReturn = startModule.indexOf("return", diagRun)
            val exec = startModule.substring(diagRun, branchReturn)
            assertFalse(exec.contains("saveQueueState("), "no queue save in the diagnostic branch")
            assertFalse(exec.contains("clearQueueState("), "no queue clear in the diagnostic branch")
            assertFalse(exec.contains("decidePostCareerAction("), "no post-career decision in the diagnostic branch")
            assertFalse(exec.contains("CareerFinalizeGate"), "no career-finalization bookkeeping in the diagnostic branch")
            assertFalse(exec.contains("applyRotationForRun"), "no run-specific rotation snapshot in the diagnostic branch")
        }

        @Test
        fun `no PHASE_CAREER record is written before the diagnostic executes (process-kill safety)`() {
            assertTrue(phaseCareerWrite > diagRun, "the diagnostic must run before any PHASE_CAREER write, so a mid-diagnostic kill cannot fake a career-in-flight")
        }

        @Test
        fun `the normal queue path still reads, saves, clears, and decides downstream`() {
            assertTrue(firstLoadQueueState > diagRun, "normal resume still reads saved queue state")
            assertTrue(startModule.indexOf("saveQueueState(context, active = true", diagRun) > diagRun, "normal runs still persist queue state")
            assertTrue(startModule.indexOf("clearQueueState(context)", diagRun) > diagRun, "normal completion still clears queue state")
            assertTrue(startModule.indexOf("decidePostCareerAction(", diagRun) > diagRun, "normal post-career routing still runs")
        }
    }

    @Nested
    @DisplayName("inner Game.kt gate preserved (defense-in-depth)")
    inner class InnerGate {
        @Test
        fun `Game start still fails closed when a diagnostic is armed but ran nothing`() {
            val startTests = game.indexOf("task.startTests()")
            val failClosed = game.indexOf("armedDebugTests.isNotEmpty()", startTests)
            val navigation = game.indexOf("warnOnRacingConfigDrift()", startTests)
            assertTrue(startTests >= 0 && navigation > startTests, "the startTests gate and normal navigation must exist")
            assertTrue(failClosed in startTests until navigation, "the inner fail-closed gate must run after startTests and before normal navigation")
        }
    }

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
