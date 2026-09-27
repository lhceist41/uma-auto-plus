/**
 * Turns the bridge's last-session payload (StartModule.kt `getLastQueueReport`, built by
 * QueueReportText.kt `lastReportPayload`) into what Home's "Last session" card renders.
 *
 * The title, reason and next action are Kotlin's words for the ending, shown verbatim: the
 * notification and this card say the same thing, and the table lives only in Kotlin. This module
 * adds the structured facts the report carries as keys and numbers: the run count, one line per
 * run, recoveries and TP restores. Anything missing or malformed fails closed: a field it cannot
 * read is left out or shown as unknown, never guessed and never echoed raw.
 */

export type LastSessionView = {
    sessionId: string
    dismissed: boolean
    /** The session played a queue or run (not a refused start or a diagnostic). */
    runEnding: boolean
    /** The saved queue record survived this ending. Not a promise that Start resumes it. */
    resumable: boolean
    endedAt: number | null
    totalRuns: number | null
    title: string
    reason: string
    nextAction: string | null
    progress: string | null
    runs: string[]
    recoveries: string | null
    tpRestores: string | null
    caratsUsed: number
}

const GENERIC_TITLE = "Bot stopped"
const GENERIC_REASON = "The bot session ended."

const RUN_ERROR_CODES = new Set(["TASK_RESULT_UNHANDLED_EXCEPTION", "TASK_RESULT_CONNECTION_ERROR", "TASK_RESULT_TIMED_OUT", "TASK_RESULT_QUEUE_NAVIGATION_FAILED"])

const RUN_LABEL_BY_RESULT_CODE: Record<string, string> = {
    TASK_RESULT_COMPLETE: "Completed",
    TASK_RESULT_MANUALLY_STOPPED: "Stopped",
    TASK_RESULT_BREAKPOINT_REACHED: "Paused at a breakpoint",
    TASK_RESULT_SKIPPED_BY_QUEUE: "Skipped",
}

const TP_ITEM_RUNGS = new Set(["Toughness 30", "Star Fruit"])

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}

function count(value: unknown): number {
    return typeof value === "number" && Number.isInteger(value) && value > 0 ? value : 0
}

function plural(n: number, one: string, many: string): string {
    return `${n} ${n === 1 ? one : many}`
}

/** A run's outcome label. A completed career the game ended early (a missed goal) says so. */
function runLabel(resultCode: unknown, outcome: unknown): string {
    if (resultCode === "TASK_RESULT_COMPLETE" && outcome === "FORCE_END") return "Ended early"
    if (typeof resultCode !== "string") return "Outcome unknown"
    if (RUN_ERROR_CODES.has(resultCode)) return "Error"
    return RUN_LABEL_BY_RESULT_CODE[resultCode] ?? "Outcome unknown"
}

function runLines(runs: unknown): string[] {
    if (!Array.isArray(runs)) return []
    return runs.filter(isRecord).flatMap((run) => {
        const n = count(run.run)
        if (n === 0) return []
        const trainee = typeof run.trainee === "string" ? run.trainee.trim() : ""
        const who = trainee.length > 0 && trainee.length <= 40 ? `${trainee}, ` : ""
        return [`Run ${n}: ${who}${runLabel(run.resultCode, run.outcome)}${run.retried === true ? " after a retry" : ""}`]
    })
}

/** "N of M runs done" (finished careers only), then how many runs of this session ended with an error. */
function progressLine(report: Record<string, unknown>): string | null {
    const total = count(report.totalRuns)
    if (report.queueEnabled !== true || total === 0 || typeof report.completedRuns !== "number" || !Number.isInteger(report.completedRuns) || report.completedRuns < 0) return null
    const runs = Array.isArray(report.runs) ? report.runs.filter(isRecord) : []
    const errors = runs.filter((run) => typeof run.resultCode === "string" && RUN_ERROR_CODES.has(run.resultCode)).length
    const note = errors === 0 ? "" : count(report.startFromRun) > 1 ? `; ${errors} since the queue resumed ended with an error` : `; ${errors} ended with an error`
    return `${report.completedRuns} of ${plural(total, "run", "runs")} done${note}`
}

function recoveriesLine(recoveries: unknown): string | null {
    if (!isRecord(recoveries)) return null
    const parts: [number, string, string][] = [
        [count(recoveries.accessibilityRebinds) + count(recoveries.accessibilityRewrites), "accessibility repair", "accessibility repairs"],
        [count(recoveries.gameRelaunches), "game restart", "game restarts"],
        [count(recoveries.lobbyReentries), "return to the career from the game's home screen", "returns to the career from the game's home screen"],
        [count(recoveries.connectionHolds), "wait for a lost connection", "waits for a lost connection"],
    ]
    const shown = parts.filter(([n]) => n > 0)
    const total = shown.reduce((sum, [n]) => sum + n, 0)
    if (total === 0) return null
    return `Recovered ${plural(total, "time", "times")}: ${shown.map(([n, one, many]) => plural(n, one, many)).join(", ")}.`
}

function tpRestores(restores: unknown): { line: string | null; carats: number } {
    const entries = Array.isArray(restores) ? restores.filter(isRecord) : []
    if (entries.length === 0) return { line: null, carats: 0 }
    const items = entries.filter((e) => typeof e.rung === "string" && TP_ITEM_RUNGS.has(e.rung)).length
    const carats = entries.filter((e) => e.rung === "Carats").length
    const unrecorded = entries.length - items - carats
    const tail = unrecorded > 0 ? `, ${unrecorded} with an item that was not recorded` : ""
    return { line: `TP restored ${plural(entries.length, "time", "times")}: ${items} with items, ${carats} with Carats${tail}.`, carats }
}

/** The card's view of the bridge payload, or null when there is no report it can attribute to a session. */
export function parseLastSession(payload: unknown): LastSessionView | null {
    let parsed: unknown = payload
    if (typeof payload === "string") {
        try {
            parsed = JSON.parse(payload)
        } catch {
            return null
        }
    }
    if (!isRecord(parsed) || !isRecord(parsed.report)) return null
    const report = parsed.report
    if (typeof report.sessionId !== "string" || report.sessionId.length === 0) return null
    const text = isRecord(parsed.text) ? parsed.text : {}
    const words = typeof text.title === "string" && typeof text.reason === "string"
    const tp = tpRestores(report.tpRestores)
    return {
        sessionId: report.sessionId,
        dismissed: report.dismissed === true,
        runEnding: parsed.runEnding === true,
        resumable: report.resumable === true,
        endedAt: count(report.endedAt) || null,
        totalRuns: report.queueEnabled === true ? count(report.totalRuns) || null : null,
        title: words ? (text.title as string) : GENERIC_TITLE,
        reason: words ? (text.reason as string) : GENERIC_REASON,
        nextAction: words && typeof text.nextAction === "string" ? text.nextAction : null,
        progress: progressLine(report),
        runs: runLines(report.runs),
        recoveries: recoveriesLine(report.recoveries),
        tpRestores: tp.line,
        caratsUsed: tp.carats,
    }
}

/**
 * Whether Home shows the card: an undismissed report while the bot is not running. A queue's own
 * end banner stays on screen for its last few seconds first; the card takes over when it clears.
 */
export function lastSessionCardVisible(view: LastSessionView | null, botRunning: boolean, queueEndBannerShown: boolean): boolean {
    return view !== null && !view.dismissed && !botRunning && !queueEndBannerShown
}

/**
 * What the interrupted-queue banner takes from the last report: its reason, and how long ago the
 * session ended. Only a queue report whose record survived and that matches the saved queue's
 * length qualifies; otherwise the banner keeps its own saved-state age.
 */
export function interruptedBannerReport(view: LastSessionView | null, interrupted: { totalRuns: number } | null, nowMs: number): { reason: string; minutesAgo: number | null } | null {
    if (view === null || interrupted === null || !view.runEnding || !view.resumable || view.totalRuns !== interrupted.totalRuns) return null
    return { reason: view.reason, minutesAgo: view.endedAt === null ? null : Math.max(0, Math.round((nowMs - view.endedAt) / 60000)) }
}
