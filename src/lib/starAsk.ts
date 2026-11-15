/** The repository the star ask opens. */
export const STAR_REPO_URL = "https://github.com/lhceist41/uma-auto-plus"

/** The ask appears once this many careers have finished. */
export const STAR_ASK_AFTER_CAREERS = 3

export const STAR_ASK_TITLE = "Enjoying UMA Auto+?"
export const STAR_ASK_BODY = "If UMA Auto+ saves you time, a star on GitHub helps other players find it. The app asks this once."

/** Whether the What's new dialog has been decided in this app open: still pending, shown, or not shown. */
export type WhatsNewOpenStatus = "pending" | "shown" | "clear"

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}

/** Finished careers in a run report; the last run is skipped when its Finish may not have gone through. */
export function finishedCareersInReport(payload: unknown): { sessionId: string; finished: number } | null {
    let parsed: unknown = payload
    if (typeof payload === "string") {
        try {
            parsed = JSON.parse(payload)
        } catch {
            return null
        }
    }
    if (!isRecord(parsed) || parsed.runEnding !== true || !isRecord(parsed.report)) return null
    const report = parsed.report
    if (typeof report.sessionId !== "string" || report.sessionId.length === 0 || !Array.isArray(report.runs)) return null
    const runNumber = (run: unknown): number => (isRecord(run) && typeof run.run === "number" && Number.isInteger(run.run) && run.run > 0 ? run.run : 0)
    // Recorded as completed before its career-end flow; a stop there leaves the Finish unconfirmed.
    const unconfirmedRun = report.kind === "PROCESS_ENDED" || (typeof report.finalizeStopKey === "string" && report.finalizeStopKey.length > 0) ? Math.max(0, ...report.runs.map(runNumber)) : 0
    const finishedRuns = new Set<number>()
    for (const run of report.runs) {
        if (!isRecord(run) || run.resultCode !== "TASK_RESULT_COMPLETE" || run.finishLost === true) continue
        if (typeof run.scenario !== "string" || run.scenario.length === 0) continue
        const n = runNumber(run)
        if (n > 0 && n !== unconfirmedRun) finishedRuns.add(n)
    }
    return { sessionId: report.sessionId, finished: finishedRuns.size }
}

export interface StarAskInput {
    /** Finished careers counted so far. */
    careers: number
    /** The player already pressed a button. */
    answered: boolean
    /** The bot is armed or running. */
    botActive: boolean
    /** The What's new dialog's state in this app open. */
    whatsNew: WhatsNewOpenStatus
}

/**
 * - `show`: open the ask.
 * - `wait`: not yet (the bot is active or What's new is undecided); decide again later.
 * - `none`: not in this app open.
 *
 * Nothing is stored for `show`, so an app closed with the ask open shows it again next open.
 */
export type StarAskDecision = "show" | "wait" | "none"

export function decideStarAsk(input: StarAskInput): StarAskDecision {
    if (input.answered || input.careers < STAR_ASK_AFTER_CAREERS) return "none"
    if (input.whatsNew === "shown") return "none"
    if (input.botActive || input.whatsNew === "pending") return "wait"
    return "show"
}
