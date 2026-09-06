import { presentQueueProgress } from "../queueProgressPresentation"

describe("presentQueueProgress", () => {
    it("presents a fresh queue starting", () => {
        const p = presentQueueProgress({ currentRun: 1, totalRuns: 4, status: "starting" })
        expect(p).toEqual({ kind: "running", title: "Run 1/4 - Starting...", isTerminal: false })
    })

    it("presents a resumed queue starting with the auto-resume reason", () => {
        const p = presentQueueProgress({
            currentRun: 4,
            totalRuns: 6,
            status: "resuming",
            message: "Auto-resuming: starting at run 4 of 6 (previous run was interrupted)",
        })
        expect(p.kind).toBe("running")
        expect(p.isTerminal).toBe(false)
        expect(p.detail).toBe("Auto-resuming: starting at run 4 of 6 (previous run was interrupted)")
    })

    it("presents an intermediate run starting", () => {
        const p = presentQueueProgress({ currentRun: 3, totalRuns: 6, status: "navigating" })
        expect(p.title).toBe("Run 3/6 - Navigating...")
        expect(p.isTerminal).toBe(false)
    })

    it("presents an intermediate run completing with a safe resultCode label, not the raw message", () => {
        const p = presentQueueProgress({
            currentRun: 3,
            totalRuns: 6,
            status: "completed",
            resultCode: "TASK_RESULT_COMPLETE",
            message: "Task completed successfully.",
        })
        expect(p.title).toBe("Run 3/6 - Completed")
        expect(p.isTerminal).toBe(false)
    })

    it("presents a fresh queue completing", () => {
        const p = presentQueueProgress({ currentRun: 4, totalRuns: 4, status: "queueComplete", message: "Completed 4 of 4 runs." })
        expect(p).toEqual({ kind: "completed", title: "Queue complete: 4/4 runs", detail: "Completed 4 of 4 runs.", isTerminal: true })
    })

    it("presents a resumed queue completing with the truthful total, not the this-session count", () => {
        // Resumed at run 4 of 6, finished runs 4-6: the producer now reports completedRuns=6,
        // not 3 (see StartModule.kt's priorCompletedRuns) -- this pins the Home side of that fix.
        const p = presentQueueProgress({ currentRun: 6, totalRuns: 6, status: "queueComplete", message: "Completed 6 of 6 runs." })
        expect(p.title).toBe("Queue complete: 6/6 runs")
        expect(p.detail).toBe("Completed 6 of 6 runs.")
    })

    it("presents a breakpoint as a controlled halt with its own reason, not a failure", () => {
        const p = presentQueueProgress({
            currentRun: 3,
            totalRuns: 6,
            status: "queueFailed",
            resultCode: "TASK_RESULT_BREAKPOINT_REACHED",
            message: "Mandatory race detected. Stopping bot...",
        })
        expect(p.kind).toBe("halted")
        // The producer's numerator here is the run the queue reached (queueHaltRun), and run 3 did
        // not finish -- "3/6 runs" would read as three completed careers.
        expect(p.title).toBe("Queue paused at run 3/6")
        expect(p.detail).toBe("Mandatory race detected. Stopping bot...")
    })

    it("presents a controlled internal stop (trainee mismatch) as halted, not stopped or failed", () => {
        const p = presentQueueProgress({
            currentRun: 2,
            totalRuns: 6,
            status: "queueHalted",
            message: "Stopped on trainee mismatch - career was 'Foo' but the queue loaded the preset for 'Bar'.",
        })
        expect(p.kind).toBe("halted")
        expect(p.detail).toBe("Stopped on trainee mismatch - career was 'Foo' but the queue loaded the preset for 'Bar'.")
    })

    it("presents a user Stop as stopped, not failed or completed", () => {
        const p = presentQueueProgress({ currentRun: 2, totalRuns: 6, status: "queueStopped", message: "Stopped by the user after 2 of 6 runs." })
        expect(p.kind).toBe("stopped")
        expect(p.title).toBe("Queue stopped: 2/6 runs")
    })

    it("presents an unexpected failure with sanitized generic copy derived from resultCode", () => {
        const p = presentQueueProgress({ currentRun: 2, totalRuns: 6, status: "queueFailed", resultCode: "TASK_RESULT_UNHANDLED_EXCEPTION" })
        expect(p.kind).toBe("failed")
        expect(p.title).toBe("Queue failed at run 2/6")
        expect(p.detail).toBe("Queue stopped after an unexpected error.")
    })

    it("says nothing was reached when a queue fails before its first run", () => {
        // A cold-start launch failure at run 1 reports queueHaltRun = startFromRun - 1 = 0.
        const p = presentQueueProgress({ currentRun: 0, totalRuns: 4, status: "queueFailed", resultCode: "TASK_RESULT_QUEUE_NAVIGATION_FAILED" })
        expect(p.title).toBe("Queue failed before any run started")
    })

    it("falls back to a generic failure reason for an unrecognized resultCode", () => {
        const p = presentQueueProgress({ currentRun: 2, totalRuns: 6, status: "queueFailed", resultCode: "TASK_RESULT_SOMETHING_NEW" })
        expect(p.detail).toBe("Queue stopped after an unexpected failure.")
    })

    it("never leaks an internal exception-shaped message, even if one reaches the payload", () => {
        // Non-breakpoint queueFailed detail is always resultCode-keyed, on purpose: a navigation
        // failure's raw reason can contain caught-exception text (see CareerLaunchNavigator's
        // "threw <Exception>: ..." failureReason strings), so this branch must not trust
        // `message` even if a malformed or future payload sets one.
        const p = presentQueueProgress({
            currentRun: 2,
            totalRuns: 6,
            status: "queueFailed",
            resultCode: "TASK_RESULT_QUEUE_NAVIGATION_FAILED",
            message: "java.lang.IllegalStateException: capture pipeline died at NativeCaptureThread.run",
        })
        expect(p.detail).toBe("Queue stopped because the next run could not be prepared.")
    })

    it("falls back to a generic failure reason when neither message nor resultCode is present", () => {
        const p = presentQueueProgress({ currentRun: 1, totalRuns: 6, status: "queueFailed" })
        expect(p.detail).toBe("Queue stopped after an unexpected failure.")
    })

    it("marks every terminal status terminal and every per-run status not", () => {
        const terminal = ["queueComplete", "queueStopped", "queueHalted", "queueFailed"]
        terminal.forEach((status) => {
            expect(presentQueueProgress({ currentRun: 2, totalRuns: 6, status }).isTerminal).toBe(true)
        })
        const running = ["starting", "resuming", "navigating", "waiting", "completed"]
        running.forEach((status) => {
            expect(presentQueueProgress({ currentRun: 2, totalRuns: 6, status }).isTerminal).toBe(false)
        })
    })

    it("keeps a per-run completed event non-terminal even when the run failed", () => {
        // The banner must not auto-clear mid-queue just because one run ended badly.
        const p = presentQueueProgress({
            currentRun: 2,
            totalRuns: 6,
            status: "completed",
            resultCode: "TASK_RESULT_UNHANDLED_EXCEPTION",
            message: "Unhandled exception: java.lang.IllegalStateException",
        })
        expect(p.isTerminal).toBe(false)
        expect(p.title).toBe("Run 2/6 - Ended with an error")
        expect(p.detail).toBeUndefined()
    })

    it("drops an exception-shaped message on a queueFailed with no resultCode at all", () => {
        const p = presentQueueProgress({
            currentRun: 2,
            totalRuns: 6,
            status: "queueFailed",
            message: "java.lang.NullPointerException at Campaign.start(Campaign.kt:2041)",
        })
        expect(p.detail).toBe("Queue stopped after an unexpected failure.")
        expect(p.title).toBe("Queue failed at run 2/6")
    })

    it("fails closed on an unknown status instead of rendering it raw", () => {
        const p = presentQueueProgress({ currentRun: 2, totalRuns: 6, status: "some_future_status" })
        expect(p).toEqual({ kind: "running", title: "Queue status unavailable", isTerminal: false })
    })

    it("does not interpolate an unknown status or resultCode into the banner", () => {
        const p = presentQueueProgress({ currentRun: 2, totalRuns: 6, status: "queueMeltdown", resultCode: "TASK_RESULT_MELTDOWN" })
        expect(p.title).not.toContain("queueMeltdown")
        expect(p.title).not.toContain("TASK_RESULT_MELTDOWN")
        expect(p.detail).toBeUndefined()
    })

    it("clamps a currentRun that overshoots totalRuns", () => {
        const p = presentQueueProgress({ currentRun: 99, totalRuns: 6, status: "queueComplete" })
        expect(p.title).toBe("Queue complete: 6/6 runs")
    })

    it("clamps a negative or non-finite count to zero", () => {
        const p = presentQueueProgress({ currentRun: -3, totalRuns: NaN, status: "starting" })
        expect(p.title).toBe("Run 0/0 - Starting...")
    })
})
