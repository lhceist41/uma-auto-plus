package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Pre-loop queue failures must halt through the same post-loop branch an in-loop failure uses.
 *
 * Two failures happen before the run loop body: the first trainee's rotation snapshot missing, and
 * the cold-start career launch failing. Both used to emit queueFailed themselves, set the stop flag
 * and fall through with no halt metadata, so the post-loop block took its success branch: it cleared
 * the persisted queue state and emitted queueComplete(0/N) straight over the failure. On a resumed
 * queue that destroyed the resume record, and the next Start replayed the queue from run 1 (rotation
 * from slot 0).
 *
 * StartModule is a React module that is impractical to unit-test directly, so these are source
 * guards on the wiring that keeps the invariant: the halt metadata is declared above both sites,
 * each site populates it instead of emitting its own terminal event, and the shared halt branch
 * owns the single queueFailed while leaving the persisted state alone.
 */
@DisplayName("StartModule pre-loop queue halt")
class StartModulePreLoopQueueHaltTest {
    private val startModule by lazy {
        repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt")
            .readText().replace("\r\n", "\n")
    }

    /** Everything between the queue bookkeeping locals and the run loop: both pre-loop failure sites live here. */
    private val preLoop by lazy {
        val start = startModule.indexOf("var completedRuns = 0")
        val end = startModule.indexOf("for (i in startFromRun..totalRuns) {")
        assertTrue(start in 0 until end, "the pre-loop region must sit between completedRuns and the run loop")
        startModule.substring(start, end)
    }

    /** The `r == null` arm of the cold-start rotation snapshot lookup. */
    private val rotationSnapshotFailure by lazy {
        val site = preLoop.indexOf("val r = applyRotationForRun(rotation, startFromRun, reuseLastLaunchSetup)")
        val end = preLoop.indexOf("} else {", site)
        assertTrue(site in 0 until end, "the first-trainee rotation snapshot site must exist")
        preLoop.substring(site, end)
    }

    /** The `!navResult.success` arm of the cold-start career launch. */
    private val coldStartFailure by lazy {
        val site = preLoop.indexOf("val navResult = navigateWithDeadline(coldStartReuse, coldStartNavigator)")
        assertTrue(site >= 0, "the cold-start launch site must exist")
        preLoop.substring(site)
    }

    /** The post-loop halt branch, up to the success branch that clears queue state. */
    private val haltBranch by lazy {
        val start = startModule.indexOf("val halt = queueHaltReason")
        val end = startModule.indexOf("// Clear persisted queue state since queue finished normally.", start)
        assertTrue(start in 0 until end, "the post-loop halt branch must precede the success branch")
        startModule.substring(start, end)
    }

    @Nested
    @DisplayName("halt metadata is reachable from both pre-loop sites")
    inner class MetadataScope {
        @Test
        fun `the halt metadata is declared before both pre-loop failure sites`() {
            val reason = startModule.indexOf("var queueHaltReason: String? = null")
            val run = startModule.indexOf("var queueHaltRun = 0")
            val inFlight = startModule.indexOf("var queueHaltCareerInFlight = false")
            val rotationSite = startModule.indexOf("val r = applyRotationForRun(rotation, startFromRun, reuseLastLaunchSetup)")
            val coldStartSite = startModule.indexOf("val navResult = navigateWithDeadline(coldStartReuse, coldStartNavigator)")
            assertTrue(reason >= 0 && run >= 0 && inFlight >= 0, "all three halt fields must exist")
            assertTrue(rotationSite > 0 && coldStartSite > rotationSite, "both pre-loop failure sites must exist, in order")
            assertTrue(reason < rotationSite, "queueHaltReason must be in scope at the rotation snapshot site")
            assertTrue(run < rotationSite, "queueHaltRun must be in scope at the rotation snapshot site")
            assertTrue(inFlight < rotationSite, "queueHaltCareerInFlight must be in scope at the rotation snapshot site")
            assertTrue(reason < coldStartSite && run < coldStartSite, "the halt metadata must be in scope at the cold-start site")
        }

        @Test
        fun `there is exactly one halt mechanism, not a second one for the pre-loop`() {
            assertEquals(1, Regex("var queueHaltReason\\b").findAll(startModule).count(), "one queueHaltReason declaration")
            assertEquals(1, Regex("var queueHaltRun\\b").findAll(startModule).count(), "one queueHaltRun declaration")
            assertEquals(1, startModule.split("val halt = queueHaltReason").size - 1, "one post-loop halt branch")
        }
    }

    @Nested
    @DisplayName("neither pre-loop site emits its own terminal event")
    inner class NoDirectTerminalEvent {
        @Test
        fun `the pre-loop region emits no queueFailed of its own`() {
            assertFalse(
                preLoop.contains("\"queueFailed\""),
                "a pre-loop failure must not emit queueFailed directly; the shared halt branch owns the terminal event",
            )
        }

        @Test
        fun `the pre-loop region emits no queueComplete and clears no queue state`() {
            assertFalse(preLoop.contains("\"queueComplete\""), "no pre-loop queueComplete")
            assertFalse(preLoop.contains("clearQueueState("), "a pre-loop failure must not clear the resume record")
            assertFalse(preLoop.contains("saveQueueState("), "a pre-loop failure must not overwrite the resume record either")
        }

        @Test
        fun `the pre-loop region still reports progress for non-terminal states`() {
            assertTrue(preLoop.contains("\"navigating\""), "the cold-start launch still reports navigating")
        }
    }

    @Nested
    @DisplayName("rotation snapshot failure populates the halt path")
    inner class RotationSnapshotSite {
        @Test
        fun `it sets a halt reason naming the missing snapshot`() {
            assertTrue(rotationSnapshotFailure.contains("queueHaltReason ="), "the site must set a halt reason")
            assertTrue(
                rotationSnapshotFailure.contains("rotation snapshot"),
                "the halt reason must keep the real failure, not generic halt text",
            )
        }

        @Test
        fun `it derives the halt run from startFromRun instead of hard-coding zero`() {
            assertTrue(
                rotationSnapshotFailure.contains("queueHaltRun = startFromRun - 1"),
                "runs before startFromRun finished in an earlier session and are still done",
            )
            assertFalse(rotationSnapshotFailure.contains("queueHaltRun = 0"), "the halt run must not be hard-coded to 0")
        }

        @Test
        fun `it still stops the queue so no run starts on the wrong trainee`() {
            assertTrue(rotationSnapshotFailure.contains("queueStopRequested = true"), "the queue must stop at the site")
        }
    }

    @Nested
    @DisplayName("cold-start launch failure populates the halt path")
    inner class ColdStartSite {
        /** The non-user-Stop arm: a real navigation failure. */
        private val realFailure by lazy {
            val start = coldStartFailure.indexOf("if (navResult.lastDetectedState != \"STOPPED\") {")
            // Ends at the stop flag that follows the guard; a brace search would stop inside the
            // halt reason's own "${navResult.failureReason}" interpolation.
            val end = coldStartFailure.indexOf("queueStopRequested = true", start)
            assertTrue(start in 0 until end, "the STOPPED discrimination must exist")
            coldStartFailure.substring(start, end)
        }

        @Test
        fun `a real navigation failure sets the halt reason and run`() {
            assertTrue(realFailure.contains("queueHaltReason ="), "a navigation failure must set a halt reason")
            assertTrue(
                realFailure.contains("navResult.failureReason"),
                "the halt reason must carry the navigator's own failure reason, not generic halt text",
            )
            assertTrue(
                realFailure.contains("queueHaltRun = startFromRun - 1"),
                "the halt run must be derived from startFromRun, not hard-coded",
            )
        }

        @Test
        fun `a user Stop mid-navigation sets no halt reason`() {
            val outsideStoppedGuard = coldStartFailure.substringBefore("if (navResult.lastDetectedState != \"STOPPED\") {")
            assertFalse(
                outsideStoppedGuard.contains("queueHaltReason ="),
                "a user Stop is a clean cancellation; only the non-STOPPED arm may halt",
            )
        }

        @Test
        fun `it still stops the queue on any launch failure`() {
            assertTrue(coldStartFailure.contains("queueStopRequested = true"), "the queue must stop after a failed cold-start launch")
        }
    }

    @Nested
    @DisplayName("the shared halt branch is terminal and non-destructive")
    inner class SharedHaltBranch {
        @Test
        fun `the halt branch is selected by the halt reason alone`() {
            assertTrue(startModule.contains("if (halt != null) {"), "the halt branch must be selected by a non-null halt reason")
        }

        @Test
        fun `it emits exactly one queueFailed and no queueComplete`() {
            assertEquals(1, haltBranch.split("\"queueFailed\"").size - 1, "the halt branch emits exactly one queueFailed")
            assertFalse(haltBranch.contains("\"queueComplete\""), "the halt branch must never report completion")
        }

        @Test
        fun `it leaves the persisted queue state alone`() {
            assertFalse(haltBranch.contains("clearQueueState("), "a halt must keep the resume record; the remaining runs are still owed")
        }

        @Test
        fun `it reports the halted run from queueHaltRun`() {
            assertTrue(
                haltBranch.contains("val doneRuns = if (queueHaltRun > 0) queueHaltRun else completedRuns"),
                "the halt branch must report the run count the failing site recorded",
            )
        }

        @Test
        fun `the clear-and-complete path is the else of the halt branch`() {
            assertTrue(
                Regex("notifyQueueHalted\\([^\\n]*\\)\\s*\\} else \\{\\s*//[^\\n]*\\n\\s*clearQueueState\\(context\\)")
                    .containsMatchIn(startModule),
                "clearQueueState must be the else arm of the halt branch, unreachable once a halt reason is set",
            )
            val haltStart = startModule.indexOf("val halt = queueHaltReason")
            val clear = startModule.indexOf("clearQueueState(context)", haltStart)
            val complete = startModule.indexOf("\"queueComplete\"", haltStart)
            assertTrue(clear in 0 until complete, "the else arm clears state and then reports completion")
        }

        @Test
        fun `the halt copy does not claim a career completed`() {
            assertFalse(
                haltBranch.contains("The career itself completed"),
                "a pre-loop halt reaches the same log without any career having completed",
            )
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
