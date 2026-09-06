/**
 * Turns a raw `RunQueueProgress` event (StartModule.kt's `sendQueueProgressEvent`) into what
 * Home actually renders.
 *
 * The producer emits a small set of status strings (`starting`, `resuming`, `navigating`,
 * `waiting`, the per-run `completed`, and the terminal `queueComplete` / `queueStopped` /
 * `queueHalted` / `queueFailed`) plus an optional `resultCode` (a `TaskResultCode` name) and an
 * optional `message`. Whether `message` is safe to show depends on the status: the terminal
 * statuses carry a dev-authored sentence or a breakpoint's own description, while the per-run
 * `completed` carries the task's own message, which can be raw exception text. So each branch
 * below decides for itself whether to surface `message`, and none of them invents one when
 * Kotlin sent none or echoes a status or resultCode string it does not recognize.
 */

export type QueueProgressEvent = {
    currentRun: number
    totalRuns: number
    status: string
    resultCode?: string
    message?: string
}

export type QueueProgressPresentation = {
    kind: "running" | "completed" | "halted" | "stopped" | "failed"
    title: string
    detail?: string
    isTerminal: boolean
}

/** Fallback reason for a `queueFailed` event with no breakpoint message, keyed by resultCode. */
const FAILURE_REASON_BY_RESULT_CODE: Record<string, string> = {
    TASK_RESULT_QUEUE_NAVIGATION_FAILED: "Queue stopped because the next run could not be prepared.",
    TASK_RESULT_UNHANDLED_EXCEPTION: "Queue stopped after an unexpected error.",
    TASK_RESULT_CONNECTION_ERROR: "Queue stopped after a connection error.",
    TASK_RESULT_TIMED_OUT: "Queue stopped after a run timed out.",
}
const DEFAULT_FAILURE_REASON = "Queue stopped after an unexpected failure."

/** Label for a non-terminal per-run `completed` event, keyed by resultCode. */
const PER_RUN_LABEL_BY_RESULT_CODE: Record<string, string> = {
    TASK_RESULT_COMPLETE: "Completed",
    TASK_RESULT_SKIPPED_BY_QUEUE: "Skipped",
    TASK_RESULT_MANUALLY_STOPPED: "Stopped",
    TASK_RESULT_BREAKPOINT_REACHED: "Hit a breakpoint",
}
const DEFAULT_PER_RUN_LABEL = "Ended with an error"

/** Clamps a run count from the event payload to a sane, non-negative integer. */
function safeCount(value: number): number {
    return Number.isFinite(value) && value > 0 ? Math.floor(value) : 0
}

export function presentQueueProgress(event: QueueProgressEvent): QueueProgressPresentation {
    const totalRuns = safeCount(event.totalRuns)
    const currentRun = Math.min(safeCount(event.currentRun), totalRuns > 0 ? totalRuns : safeCount(event.currentRun))
    const runOf = `${currentRun}/${totalRuns} runs`
    // queueFailed's count is the run the queue reached, not a completed-run tally: a breakpoint
    // at run 3 of 6 leaves run 3 unfinished. Say "at run 3/6" so it cannot be read as three done.
    const atRun = currentRun > 0 ? `at run ${currentRun}/${totalRuns}` : "before any run started"

    switch (event.status) {
        case "starting":
            return { kind: "running", title: `Run ${currentRun}/${totalRuns} - Starting...`, isTerminal: false }
        case "resuming":
            return { kind: "running", title: `Run ${currentRun}/${totalRuns} - Resuming...`, detail: event.message, isTerminal: false }
        case "navigating":
            return { kind: "running", title: `Run ${currentRun}/${totalRuns} - Navigating...`, isTerminal: false }
        case "waiting":
            return { kind: "running", title: `Run ${currentRun}/${totalRuns} - Waiting...`, isTerminal: false }
        case "completed": {
            const label = (event.resultCode && PER_RUN_LABEL_BY_RESULT_CODE[event.resultCode]) || DEFAULT_PER_RUN_LABEL
            return { kind: "running", title: `Run ${currentRun}/${totalRuns} - ${label}`, isTerminal: false }
        }
        case "queueComplete":
            return { kind: "completed", title: `Queue complete: ${runOf}`, detail: event.message, isTerminal: true }
        case "queueStopped":
            return { kind: "stopped", title: `Queue stopped: ${runOf}`, detail: event.message ?? "Stopped by the user.", isTerminal: true }
        case "queueHalted":
            return { kind: "halted", title: `Queue paused: ${runOf}`, detail: event.message ?? "Queue paused.", isTerminal: true }
        case "queueFailed": {
            if (event.resultCode === "TASK_RESULT_BREAKPOINT_REACHED") {
                // Breakpoints are the one queueFailed cause with a trustworthy free-text reason:
                // it comes from CampaignBreakpointException.message, a deliberate description of
                // why the bot paused, never a caught generic exception.
                return { kind: "halted", title: `Queue paused ${atRun}`, detail: event.message ?? "Queue paused at a breakpoint.", isTerminal: true }
            }
            // Every other failure cause is deliberately resultCode-only: `message` here can
            // carry a navigation layer's raw exception text (see CareerLaunchNavigator's caught
            // "threw <Exception>: ..." reasons), so it is never trusted for this branch even if
            // a caller passes one.
            const detail = (event.resultCode && FAILURE_REASON_BY_RESULT_CODE[event.resultCode]) ?? DEFAULT_FAILURE_REASON
            return { kind: "failed", title: `Queue failed ${atRun}`, detail, isTerminal: true }
        }
        default:
            // An unrecognized status must never render as raw text -- fail closed instead of
            // guessing at a shape the producer hasn't documented.
            return { kind: "running", title: "Queue status unavailable", isTerminal: false }
    }
}
