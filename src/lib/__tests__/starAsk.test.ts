import { decideStarAsk, finishedCareersInReport, STAR_ASK_AFTER_CAREERS, type StarAskInput } from "../starAsk"

const base: StarAskInput = { careers: 3, answered: false, botActive: false, whatsNew: "clear" }

describe("decideStarAsk", () => {
    it("stays quiet with fewer than three finished careers", () => {
        expect(decideStarAsk({ ...base, careers: 0 })).toBe("none")
        expect(decideStarAsk({ ...base, careers: STAR_ASK_AFTER_CAREERS - 1 })).toBe("none")
    })

    it("shows at exactly three finished careers, and after more", () => {
        expect(decideStarAsk({ ...base, careers: 3 })).toBe("show")
        expect(decideStarAsk({ ...base, careers: 12 })).toBe("show")
    })

    it("waits while the bot is armed or running", () => {
        expect(decideStarAsk({ ...base, botActive: true })).toBe("wait")
    })

    it("does not count a bot that is active below the threshold as something to wait for", () => {
        expect(decideStarAsk({ ...base, careers: 2, botActive: true })).toBe("none")
    })

    it("waits until What's new is decided, and skips this app open when What's new was shown", () => {
        expect(decideStarAsk({ ...base, whatsNew: "pending" })).toBe("wait")
        expect(decideStarAsk({ ...base, whatsNew: "shown" })).toBe("none")
        expect(decideStarAsk({ ...base, whatsNew: "shown", botActive: true })).toBe("none")
    })

    it("shows again on a later open when What's new was shown only in an earlier one", () => {
        expect(decideStarAsk({ ...base, whatsNew: "clear" })).toBe("show")
    })

    it("never shows once answered", () => {
        expect(decideStarAsk({ ...base, answered: true })).toBe("none")
        expect(decideStarAsk({ ...base, answered: true, careers: 50 })).toBe("none")
    })

    it("shows again after an app closed with the ask open, because nothing was stored for it", () => {
        const stored = { careers: 3, answered: false }
        expect(decideStarAsk({ ...base, ...stored })).toBe("show")
        expect(decideStarAsk({ ...base, ...stored })).toBe("show")
    })
})

const run = (extra: Record<string, unknown> = {}) => ({ run: 1, resultCode: "TASK_RESULT_COMPLETE", scenario: "URA Finale", ...extra })
const report = (runs: unknown[], extra: Record<string, unknown> = {}) => ({ runEnding: true, report: { sessionId: "s1", runs, ...extra } })

describe("finishedCareersInReport", () => {
    it("counts completed careers that produced a career-end record", () => {
        expect(finishedCareersInReport(report([run({ run: 1 }), run({ run: 2, scenario: "Unity Cup" })]))).toEqual({ sessionId: "s1", finished: 2 })
    })

    it("reads the payload as JSON text too", () => {
        expect(finishedCareersInReport(JSON.stringify(report([run()])))).toEqual({ sessionId: "s1", finished: 1 })
    })

    it("does not count skipped, stopped, errored, breakpointed, lost-finish or non-career runs", () => {
        const runs = [
            run({ run: 1, resultCode: "TASK_RESULT_SKIPPED_BY_QUEUE" }),
            run({ run: 2, resultCode: "TASK_RESULT_MANUALLY_STOPPED" }),
            run({ run: 3, resultCode: "TASK_RESULT_UNHANDLED_EXCEPTION" }),
            run({ run: 4, resultCode: "TASK_RESULT_BREAKPOINT_REACHED" }),
            run({ run: 5, finishLost: true }),
            run({ run: 6, scenario: null }),
            run({ run: 7, scenario: "" }),
        ]
        expect(finishedCareersInReport(report(runs))).toEqual({ sessionId: "s1", finished: 0 })
    })

    it("does not count the last run when the career-end flow stopped before the game confirmed the Finish", () => {
        const payload = report([run({ run: 1 }), run({ run: 2 })], { kind: "SINGLE_RUN_ENDED", finalizeStopKey: "CAREER_NOT_FINISHED" })
        expect(finishedCareersInReport(payload)?.finished).toBe(1)
        expect(finishedCareersInReport(report([run({ run: 1 })], { finalizeStopKey: "CAREER_NOT_FINISHED" }))?.finished).toBe(0)
    })

    it("does not count the last run of a session whose process ended", () => {
        const payload = report([run({ run: 1 }), run({ run: 2 })], { kind: "PROCESS_ENDED" })
        expect(finishedCareersInReport(payload)?.finished).toBe(1)
        expect(finishedCareersInReport(report([run({ run: 1 })], { kind: "PROCESS_ENDED" }))?.finished).toBe(0)
    })

    it("counts a career the game ended early", () => {
        expect(finishedCareersInReport(report([run({ outcome: "FORCE_END" })]))?.finished).toBe(1)
    })

    it("counts one career per run number, and ignores a malformed run", () => {
        expect(finishedCareersInReport(report([run({ run: 1 }), run({ run: 1 }), run({ run: 0 }), run({ run: "2" }), null, "x"]))?.finished).toBe(1)
    })

    it("counts only this session's runs, not a resumed queue's earlier ones", () => {
        const payload = report([run({ run: 4 })], { earlier: { runs: [run({ run: 1 }), run({ run: 2 }), run({ run: 3 })] } })
        expect(finishedCareersInReport(payload)?.finished).toBe(1)
    })

    it("is null for anything that is not a run report", () => {
        expect(finishedCareersInReport(null)).toBeNull()
        expect(finishedCareersInReport("{not json")).toBeNull()
        expect(finishedCareersInReport({ runEnding: false, report: { sessionId: "s1", runs: [run()] } })).toBeNull()
        expect(finishedCareersInReport({ runEnding: true, report: { runs: [run()] } })).toBeNull()
        expect(finishedCareersInReport({ runEnding: true, report: { sessionId: "s1" } })).toBeNull()
    })
})
