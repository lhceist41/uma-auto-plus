package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The between-run wait must never throw its way past the queue's terminal report.
 *
 * The overlay Stop interrupts the bot thread and only then tears the service down. While the queue
 * sat in the delay between runs, that interrupt hit the wait's Thread.sleep and escaped: the run
 * loop, the terminal classifier and every queue-progress event were skipped, EventBus swallowed the
 * exception into a SubscriberExceptionEvent that only logs, and Home stayed on "Waiting..." for a
 * queue that had already ended.
 *
 * StartModule is a React module that is impractical to unit-test directly, so these are source
 * guards on the wiring: the sleep is contained, the abort leaves through the loop's existing break
 * rather than an exception, and the settle gives the stop evidence time to appear so the classifier
 * cannot read the abort as a finished queue.
 */
@DisplayName("StartModule between-run wait")
class StartModuleBetweenRunWaitTest {
    private val startModule by lazy {
        repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt")
            .readText().replace("\r\n", "\n")
    }

    private fun bodyOf(signature: String): String {
        val start = startModule.indexOf(signature)
        assertTrue(start >= 0, "$signature must exist")
        var depth = 0
        var i = startModule.indexOf('{', start)
        assertTrue(i >= 0, "$signature must have a body")
        while (i < startModule.length) {
            when (startModule[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return startModule.substring(start, i + 1)
                }
            }
            i++
        }
        throw IllegalStateException("unbalanced body for $signature")
    }

    /** Strips comment-only lines so a guard reads the code, not the prose explaining it. */
    private fun codeOf(block: String): String = block.lines().filterNot { it.trim().startsWith("//") }.joinToString(" ")

    /** The balanced span starting at the first [open] at or after [from], inclusive of its [close]. */
    private fun spanAt(block: String, from: Int, open: Char, close: Char): String {
        var i = block.indexOf(open, from)
        assertTrue(i >= 0, "expected a $open after index $from")
        val start = i
        var depth = 0
        while (i < block.length) {
            if (block[i] == open) depth++
            if (block[i] == close) {
                depth--
                if (depth == 0) return block.substring(start, i + 1)
            }
            i++
        }
        throw IllegalStateException("unbalanced $open in $block")
    }

    private val wait by lazy { bodyOf("private fun interruptibleWait(seconds: Int): Boolean {") }
    private val settle by lazy { bodyOf("private fun awaitStopEvidence() {") }

    /** Matches the catch header whatever the caught value is named. */
    private val catchHeader = Regex("""catch \((?:_|\w+): InterruptedException\)""")

    /** The interruption catch, up to and including the abort it returns. */
    private val interruptCatch by lazy {
        val start = catchHeader.find(wait)?.range?.first ?: -1
        assertTrue(start >= 0, "the wait must catch InterruptedException")
        val end = wait.indexOf("return false", start)
        assertTrue(end > start, "the catch must abort the wait instead of leaving another way")
        wait.substring(start, end + "return false".length)
    }

    /** The catch with its explanatory comments removed, so brace and paren matching only sees code. */
    private val catchCode by lazy { codeOf(interruptCatch) }

    /** The call site's abort handling, with comments stripped, up to and including its break. */
    private val abortHandling by lazy {
        val code = codeOf(callSite)
        val end = code.indexOf("break")
        assertTrue(end > 0, "the abort must leave the loop through a break")
        code.substring(0, end + "break".length)
    }

    /** The `if` guarding the call site's halt attribution. */
    private val noEvidenceCondition by lazy {
        val at = abortHandling.indexOf("if ", abortHandling.indexOf("interruptibleWait(delayBetweenRuns)"))
        assertTrue(at > 0, "the abort must test for missing stop evidence before it leaves the loop")
        spanAt(abortHandling, at, '(', ')')
    }

    /** The body of that `if`. */
    private val noEvidenceBranch by lazy {
        spanAt(abortHandling, abortHandling.indexOf(noEvidenceCondition) + noEvidenceCondition.length, '{', '}')
    }

    /** The run loop's call site, up to the terminal classifier that follows the loop. */
    private val callSite by lazy {
        val start = startModule.indexOf("if (!interruptibleWait(delayBetweenRuns)) {")
        assertTrue(start >= 0, "the between-run call site must exist")
        val end = startModule.indexOf("if (enableRunQueue) {", start)
        assertTrue(end > start, "the terminal classifier must follow the run loop")
        startModule.substring(start, end)
    }

    @Nested
    @DisplayName("the wait contains its own interruption")
    inner class Containment {
        @Test
        fun `the sleep is wrapped in a catch for InterruptedException`() {
            val sleep = wait.indexOf("Thread.sleep(100)")
            val tryBlock = wait.lastIndexOf("try {", sleep)
            val catchBlock = catchHeader.find(wait, sleep)?.range?.first ?: -1
            assertTrue(tryBlock in 0 until sleep, "the between-run sleep must sit inside a try")
            assertTrue(catchBlock > sleep, "the between-run sleep must be followed by an InterruptedException catch")
        }

        @Test
        fun `the catch aborts the wait instead of rethrowing`() {
            assertTrue(interruptCatch.contains("return false"), "an interrupted wait must report the abort through its return value")
            assertFalse(codeOf(interruptCatch).contains("throw"), "an escaping exception skips the queue's terminal report entirely")
        }

        @Test
        fun `the catch does not restore the interrupt flag`() {
            // Thread.sleep already cleared it, and the terminal report that follows logs and saves
            // the session log; a live interrupt flag would break that teardown the same way it
            // broke this wait.
            assertFalse(wait.contains("Thread.currentThread().interrupt()"), "the wait must not re-arm the interrupt flag")
            assertFalse(settle.contains("Thread.currentThread().interrupt()"), "the settle must not re-arm the interrupt flag")
        }

        @Test
        fun `an uninterrupted wait still counts down and completes`() {
            val catchEnd = wait.indexOf("}", wait.lastIndexOf("return false"))
            val advance = wait.indexOf("elapsed += 100")
            assertTrue(advance > catchEnd, "the elapsed counter must advance outside the catch, so a normal tick still progresses")
            assertTrue(wait.trimEnd().endsWith("return true\n    }"), "a wait that runs its full budget must still return true")
        }
    }

    @Nested
    @DisplayName("the abort leaves through the loop, not through an exception")
    inner class AbortReachesClassifier {
        @Test
        fun `a false result breaks the run loop`() {
            assertTrue(callSite.contains("break"), "the abort must leave the loop through its existing break")
            assertFalse(callSite.contains("return"), "returning from the session body would skip the terminal classifier")
            assertFalse(callSite.contains("throw"), "throwing would skip the terminal classifier")
        }

        @Test
        fun `the terminal classifier is downstream of the loop`() {
            val loop = startModule.indexOf("for (i in startFromRun..totalRuns) {")
            val call = startModule.indexOf("if (!interruptibleWait(delayBetweenRuns)) {")
            val classifier = startModule.indexOf("val halt = queueHaltReason")
            assertTrue(loop in 0 until call, "the wait belongs to the run loop")
            assertTrue(call < classifier, "the classifier must run after the loop the abort breaks out of")
        }
    }

    @Nested
    @DisplayName("the settle lets the stop evidence catch up")
    inner class StopEvidence {
        @Test
        fun `the interrupted wait settles before returning`() {
            val settleCall = interruptCatch.indexOf("awaitStopEvidence()")
            val abort = interruptCatch.indexOf("return false")
            assertTrue(settleCall in 0 until abort, "the wait must settle before it reports the abort")
        }

        @Test
        fun `the settle watches both stop signals`() {
            assertTrue(settle.contains("queueStopRequested"), "the settle must watch the app Stop flag")
            assertTrue(settle.contains("BotService.isRunning"), "the settle must watch the service teardown the overlay Stop causes")
        }

        @Test
        fun `the settle is bounded`() {
            assertTrue(settle.contains("STOP_EVIDENCE_SETTLE_MS"), "the settle must run against a declared budget")
            assertTrue(
                Regex("private const val STOP_EVIDENCE_SETTLE_MS: Long = \\d+L").containsMatchIn(startModule),
                "the settle budget must be a declared constant",
            )
            assertTrue(settle.contains("System.currentTimeMillis() < deadline"), "the settle must stop at its deadline")
        }

        @Test
        fun `the settle helper only observes`() {
            assertFalse(settle.contains("queueStopRequested ="), "the settle observes the stop, it does not invent one")
            assertFalse(settle.contains("queueStopReason ="), "the settle must not attribute the stop")
            assertFalse(settle.contains("queueHaltReason ="), "the settle must not turn an overlay Stop into a halt")
            assertFalse(settle.contains("sendQueueProgressEvent"), "the settle must not report anything; the classifier owns the terminal event")
        }
    }

    @Nested
    @DisplayName("an abort no stop claims halts without losing the resume record")
    inner class NoEvidenceHalt {
        @Test
        fun `the wait itself attributes nothing`() {
            // The wait only answers "completed" or "aborted". Attributing from inside it once sent
            // the abort down the stop path, whose branch clears the saved queue state.
            listOf("queueStopReason =", "queueStopRequested = true", "queueHaltReason =", "queueHaltResultCode =", "queueHaltRun =")
                .forEach { assertFalse(catchCode.contains(it), "the catch must not classify the abort: $it") }
        }

        @Test
        fun `the call site tests for missing stop evidence`() {
            assertTrue(noEvidenceCondition.contains("!queueStopRequested"), "the halt must require the app Stop flag to be absent")
            assertTrue(noEvidenceCondition.contains("BotService.isRunning"), "the halt must require the service to still be running")
            assertTrue(noEvidenceCondition.contains("&&"), "both signals must be absent before the halt fires")
        }

        @Test
        fun `the halt branch fills in the shared halt metadata`() {
            assertTrue(noEvidenceBranch.contains("queueHaltReason ="), "the halt reason selects the state-preserving branch")
            assertTrue(
                noEvidenceBranch.contains("queueHaltResultCode = TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION.name"),
                "an unclaimed interrupt is an unexpected ending, the same code Task.interruptResult reports",
            )
            assertTrue(noEvidenceBranch.contains("queueHaltRun = i"), "the last finished run is the loop's own index")
        }

        @Test
        fun `the halt reason is developer prose about the run, not internal state`() {
            val reason = noEvidenceBranch.substringAfter("queueHaltReason =").substringAfter("\"").substringBefore("\"")
            assertTrue(reason.isNotBlank(), "the halt reason must be a literal sentence")
            assertTrue(reason.contains("run \$i"), "the reason must name the run the queue reached")
            assertFalse(reason.contains("by the user"), "an unclaimed abort must not be blamed on the user")
            listOf("InterruptedException", "Thread", "java.lang").forEach {
                assertFalse(reason.contains(it), "the halt reason must not name internals: $it")
            }
        }

        @Test
        fun `the halt is recorded before the loop is left`() {
            val branch = abortHandling.indexOf(noEvidenceBranch)
            val leave = abortHandling.indexOf("break")
            assertTrue(branch in 0 until leave, "the halt metadata must be set before the break that ends the loop")
        }

        @Test
        fun `the abort path claims no stop of its own`() {
            // queueStopRequested lands in the classifier's non-halt branch, which clears the saved
            // launch record before reporting; the halt branch keeps it for the next Start.
            assertFalse(abortHandling.contains("queueStopRequested ="), "the abort must not fake a stop request")
            assertFalse(abortHandling.contains("queueStopReason ="), "the abort must not route through the stop-reason arm")
        }
    }

    @Nested
    @DisplayName("the halt keeps the saved queue state")
    inner class ResumeRecord {
        @Test
        fun `the launch record is saved before the wait can be interrupted`() {
            val save = startModule.indexOf("saveQueueState(context, active = true, currentRun = i, totalRuns = totalRuns, phase = PHASE_LAUNCHING)")
            val waitCall = startModule.indexOf("if (!interruptibleWait(delayBetweenRuns)) {")
            assertTrue(save in 0 until waitCall, "run i's launch record must already be on disk when the wait starts")
        }

        @Test
        fun `the halt branch does not clear that record`() {
            val haltBranch = startModule.substring(
                startModule.indexOf("val halt = queueHaltReason"),
                startModule.indexOf("// Clear persisted queue state since queue finished normally."),
            )
            assertFalse(haltBranch.contains("clearQueueState("), "a halt owes the remaining runs; the resume record must survive")
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
