import fs from "fs"
import path from "path"
import { transformSync } from "@babel/core"
import { interruptedBannerReport, lastSessionCardVisible, parseLastSession, type LastSessionView } from "../queueReportPresentation"
import { sendStopAfterCareer } from "../stopAfterCareer"

const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, "../__fixtures__/queueReportText.json"), "utf8")) as {
    cases: { name: string; report: Record<string, unknown>; text: { title: string; reason: string; nextAction: string | null } }[]
}

function payload(report: Record<string, unknown> | null, text: unknown = { title: "Queue finished", reason: "All 2 runs are done.", nextAction: null }, runEnding = true): string {
    return JSON.stringify({ report, text, runEnding })
}

function view(report: Record<string, unknown>, text?: unknown, runEnding?: boolean): LastSessionView {
    const v = parseLastSession(payload({ sessionId: "s1", ...report }, text, runEnding))
    if (v === null) throw new Error("expected a view")
    return v
}

const run = (n: number, resultCode: string, extra: Record<string, unknown> = {}) => ({ run: n, resultCode, ...extra })

describe("parseLastSession: the words", () => {
    it.each(fixture.cases.map((c) => [c.name, c] as const))("shows Kotlin's words verbatim: %s", (_name, c) => {
        const v = view(c.report, c.text)
        expect(v.title).toBe(c.text.title)
        expect(v.reason).toBe(c.text.reason)
        expect(v.nextAction).toBe(c.text.nextAction)
    })

    it("falls back to the neutral line when the words are missing or not text", () => {
        for (const text of [null, {}, { title: 1, reason: "x" }, { title: "x" }, "Queue finished"]) {
            const v = view({ kind: "COMPLETED" }, text)
            expect([v.title, v.reason, v.nextAction]).toEqual(["Bot stopped", "The bot session ended.", null])
        }
        const noText = parseLastSession(JSON.stringify({ report: { sessionId: "s1", kind: "COMPLETED" }, runEnding: true }))
        expect([noText?.title, noText?.reason, noText?.nextAction]).toEqual(["Bot stopped", "The bot session ended.", null])
    })

    it("takes the bridge's JSON text as well as an already parsed object", () => {
        const parsed = JSON.parse(payload({ sessionId: "s1", kind: "COMPLETED" }))
        expect(parseLastSession(parsed)?.title).toBe("Queue finished")
        expect(parseLastSession(payload({ sessionId: "s1", kind: "COMPLETED" }))?.title).toBe("Queue finished")
    })

    it("shows nothing when no report can be attributed to a session", () => {
        for (const p of [null, undefined, 3, "", "{broken", "[]", payload(null), JSON.stringify({ text: {} }), payload({ kind: "COMPLETED" }), payload({ sessionId: "" }), payload({ sessionId: 7 })]) {
            expect(parseLastSession(p)).toBeNull()
        }
    })
})

describe("parseLastSession: the report's facts", () => {
    it("reads the flags only when they are exactly true", () => {
        expect(view({ dismissed: true, resumable: true }, undefined, true)).toMatchObject({ dismissed: true, resumable: true, runEnding: true })
        expect(view({ dismissed: "true", resumable: 1 }, undefined, false)).toMatchObject({ dismissed: false, resumable: false, runEnding: false })
        expect(parseLastSession(JSON.stringify({ report: { sessionId: "s1" }, text: {}, runEnding: "true" }))?.runEnding).toBe(false)
    })

    it("keeps the end time only when it is a real time", () => {
        expect(view({ endedAt: 1_700_000_000_000 }).endedAt).toBe(1_700_000_000_000)
        for (const endedAt of [0, -5, "1700000000000", 1.5, null]) expect(view({ endedAt }).endedAt).toBeNull()
    })

    it("has a queue length only for a queue", () => {
        expect(view({ queueEnabled: true, totalRuns: 5 }).totalRuns).toBe(5)
        expect(view({ queueEnabled: false, totalRuns: 1 }).totalRuns).toBeNull()
        expect(view({ queueEnabled: true, totalRuns: 0 }).totalRuns).toBeNull()
    })
})

describe("parseLastSession: runs done", () => {
    it("counts done runs out of the queue", () => {
        expect(view({ queueEnabled: true, totalRuns: 5, completedRuns: 3 }).progress).toBe("3 of 5 runs done")
        expect(view({ queueEnabled: true, totalRuns: 1, completedRuns: 0 }).progress).toBe("0 of 1 run done")
    })

    it("says how many runs ended with an error, apart from the done count", () => {
        const runs = [run(1, "TASK_RESULT_COMPLETE"), run(2, "TASK_RESULT_TIMED_OUT"), run(3, "TASK_RESULT_CONNECTION_ERROR"), run(4, "TASK_RESULT_MANUALLY_STOPPED")]
        expect(view({ queueEnabled: true, totalRuns: 5, completedRuns: 1, startFromRun: 1, runs }).progress).toBe("1 of 5 runs done; 2 ended with an error")
        expect(view({ queueEnabled: true, totalRuns: 5, completedRuns: 2, startFromRun: 3, runs: [run(3, "TASK_RESULT_UNHANDLED_EXCEPTION")] }).progress).toBe(
            "2 of 5 runs done; 1 since the queue resumed ended with an error"
        )
        expect(view({ queueEnabled: true, totalRuns: 5, completedRuns: 2, runs: [run(1, "TASK_RESULT_QUEUE_NAVIGATION_FAILED")] }).progress).toBe("2 of 5 runs done; 1 ended with an error")
        expect(view({ queueEnabled: true, totalRuns: 5, completedRuns: 3, runs }).progress).not.toContain("(")
    })

    it("has no count for a single run or a count it cannot read", () => {
        expect(view({ queueEnabled: false, totalRuns: 1, completedRuns: 1 }).progress).toBeNull()
        for (const completedRuns of [undefined, -1, 1.5, "3", null]) expect(view({ queueEnabled: true, totalRuns: 5, completedRuns }).progress).toBeNull()
        expect(view({ queueEnabled: true, totalRuns: "5", completedRuns: 3 }).progress).toBeNull()
    })
})

describe("parseLastSession: one line per run", () => {
    it("says a run was retried only when its record says so", () => {
        const runs = [
            run(1, "TASK_RESULT_COMPLETE", { trainee: "Special Week", retried: true }),
            run(2, "TASK_RESULT_TIMED_OUT", { retried: true }),
            run(3, "TASK_RESULT_COMPLETE", { retried: false }),
            run(4, "TASK_RESULT_COMPLETE", { retried: "true" }),
            run(5, "TASK_RESULT_COMPLETE"),
        ]
        expect(view({ runs }).runs).toEqual(["Run 1: Special Week, Completed after a retry", "Run 2: Error after a retry", "Run 3: Completed", "Run 4: Completed", "Run 5: Completed"])
    })

    it("labels each run by how it ended", () => {
        const runs = [
            run(1, "TASK_RESULT_COMPLETE", { trainee: "Special Week", outcome: "COMPLETED" }),
            run(2, "TASK_RESULT_COMPLETE", { trainee: "Gold Ship", outcome: "FORCE_END" }),
            run(3, "TASK_RESULT_MANUALLY_STOPPED"),
            run(4, "TASK_RESULT_BREAKPOINT_REACHED"),
            run(5, "TASK_RESULT_SKIPPED_BY_QUEUE"),
            run(6, "TASK_RESULT_UNHANDLED_EXCEPTION"),
            run(7, "TASK_RESULT_CONNECTION_ERROR"),
            run(8, "TASK_RESULT_TIMED_OUT"),
            run(9, "TASK_RESULT_QUEUE_NAVIGATION_FAILED"),
            run(10, "A_NEW_RESULT"),
        ]
        expect(view({ runs }).runs).toEqual([
            "Run 1: Special Week, Completed",
            "Run 2: Gold Ship, Ended early",
            "Run 3: Stopped",
            "Run 4: Paused at a breakpoint",
            "Run 5: Skipped",
            "Run 6: Error",
            "Run 7: Error",
            "Run 8: Error",
            "Run 9: Error",
            "Run 10: Outcome unknown",
        ])
    })

    it("leaves out a trainee name it cannot trust and skips entries without a run number", () => {
        const runs = [
            run(1, "TASK_RESULT_COMPLETE", { trainee: "   " }),
            run(2, "TASK_RESULT_COMPLETE", { trainee: "x".repeat(41) }),
            run(3, "TASK_RESULT_COMPLETE", { trainee: 42 }),
            run(0, "TASK_RESULT_COMPLETE"),
            { resultCode: "TASK_RESULT_COMPLETE" },
            "Run 9",
            null,
            { run: 4 },
        ]
        expect(view({ runs }).runs).toEqual(["Run 1: Completed", "Run 2: Completed", "Run 3: Completed", "Run 4: Outcome unknown"])
        expect(view({ runs: "none" }).runs).toEqual([])
    })

    it("shows the trainee as the game names her, never the stored identifier", () => {
        const runs = [
            run(1, "TASK_RESULT_MANUALLY_STOPPED", { trainee: "El_Condor_Pasa", traineeName: "El Condor Pasa" }),
            // An older report has only the identifier: it is matched back to the character data.
            run(2, "TASK_RESULT_COMPLETE", { trainee: "El_Condor_Pasa" }),
            // A name the character data does not know is shown as stored, never rewritten.
            run(3, "TASK_RESULT_COMPLETE", { trainee: "Somebody_New" }),
            run(4, "TASK_RESULT_COMPLETE", { trainee: "El_Condor_Pasa", traineeName: "   " }),
            // The carried name wins even where the character data has no entry.
            run(5, "TASK_RESULT_COMPLETE", { trainee: "Somebody_New", traineeName: "Somebody New" }),
        ]
        expect(view({ runs }).runs).toEqual([
            "Run 1: El Condor Pasa, Stopped",
            "Run 2: El Condor Pasa, Completed",
            "Run 3: Somebody_New, Completed",
            "Run 4: El Condor Pasa, Completed",
            "Run 5: Somebody New, Completed",
        ])
    })
})

describe("parseLastSession: recoveries", () => {
    it("adds up the recoveries and names each kind", () => {
        const recoveries = { accessibilityRebinds: 1, accessibilityRewrites: 1, gameRelaunches: 1, lobbyReentries: 2, connectionHolds: 1 }
        expect(view({ recoveries }).recoveries).toBe("Recovered 6 times: 2 accessibility repairs, 1 game restart, 2 returns to the career from the game's home screen, 1 wait for a lost connection.")
        expect(view({ recoveries: { gameRelaunches: 1 } }).recoveries).toBe("Recovered 1 time: 1 game restart.")
    })

    it("says nothing without a recovery, and ignores values it cannot read", () => {
        expect(view({ recoveries: { accessibilityRebinds: 0, gameRelaunches: 0 } }).recoveries).toBeNull()
        expect(view({ recoveries: { gameRelaunches: "3", connectionHolds: -2, lobbyReentries: 1.5 } }).recoveries).toBeNull()
        expect(view({ recoveries: [1, 2] }).recoveries).toBeNull()
        expect(view({}).recoveries).toBeNull()
    })
})

describe("parseLastSession: TP restores", () => {
    it("splits items from Carats and always shows the Carats count", () => {
        const tp = (rung: unknown) => ({ rung, context: "launch" })
        expect(view({ tpRestores: [tp("Toughness 30"), tp("Star Fruit")] })).toMatchObject({ tpRestores: "TP restored 2 times: 2 with items, 0 with Carats.", caratsUsed: 0 })
        expect(view({ tpRestores: [tp("Toughness 30"), tp("Carats"), tp("Carats")] })).toMatchObject({ tpRestores: "TP restored 3 times: 1 with items, 2 with Carats.", caratsUsed: 2 })
        expect(view({ tpRestores: [tp("Carats")] })).toMatchObject({ tpRestores: "TP restored 1 time: 0 with items, 1 with Carats.", caratsUsed: 1 })
    })

    it("counts a restore whose item was not recorded without naming it", () => {
        const v = view({ tpRestores: [{ rung: "unknown" }, { rung: "Mystery Box" }, { rung: 3 }, { rung: "Star Fruit" }] })
        expect(v.tpRestores).toBe("TP restored 4 times: 1 with items, 0 with Carats, 3 with an item that was not recorded.")
        expect(v.tpRestores).not.toContain("Mystery")
    })

    it("says nothing without a restore", () => {
        for (const tpRestores of [[], undefined, "Carats", [null, "Carats"]]) expect(view({ tpRestores })).toMatchObject({ tpRestores: null, caratsUsed: 0 })
    })
})

describe("parseLastSession: nothing raw reaches the card", () => {
    it("never echoes an unknown code, key or rung", () => {
        const v = view(
            {
                kind: "NEW_KIND",
                reasonKey: "NEW_KEY",
                queueEnabled: true,
                totalRuns: 2,
                completedRuns: 1,
                runs: [run(1, "NEW_CODE", { outcome: "NEW_OUTCOME" })],
                tpRestores: [{ rung: "NEW_RUNG" }],
                recoveries: { newCounter: 4 },
            },
            {}
        )
        const shown = [v.title, v.reason, v.nextAction, v.progress, ...v.runs, v.recoveries, v.tpRestores].join(" ")
        for (const raw of ["NEW_KIND", "NEW_KEY", "NEW_CODE", "NEW_OUTCOME", "NEW_RUNG", "newCounter", "TASK_RESULT"]) expect(shown).not.toContain(raw)
    })
})

describe("lastSessionCardVisible", () => {
    const shown = view({})

    it("shows an undismissed report while the bot is not running", () => {
        expect(lastSessionCardVisible(shown, false, false)).toBe(true)
    })

    it("hides with no report, a dismissed report, or a running bot", () => {
        expect(lastSessionCardVisible(null, false, false)).toBe(false)
        expect(lastSessionCardVisible({ ...shown, dismissed: true }, false, false)).toBe(false)
        expect(lastSessionCardVisible(shown, true, false)).toBe(false)
    })

    it("takes over from the queue's end banner once it clears", () => {
        expect(lastSessionCardVisible(shown, false, true)).toBe(false)
        expect(lastSessionCardVisible(shown, false, false)).toBe(true)
    })
})

describe("interruptedBannerReport", () => {
    const now = 1_700_000_000_000
    const halted = view({ resumable: true, queueEnabled: true, totalRuns: 5, endedAt: now - 42 * 60_000 }, { title: "Queue paused", reason: "Run 2 stopped at a breakpoint.", nextAction: null }, true)

    it("gives the report's reason and the minutes since the session ended", () => {
        expect(interruptedBannerReport(halted, { totalRuns: 5 }, now)).toEqual({ reason: "Run 2 stopped at a breakpoint.", minutesAgo: 42, paused: false })
        expect(interruptedBannerReport({ ...halted, endedAt: null }, { totalRuns: 5 }, now)).toEqual({ reason: "Run 2 stopped at a breakpoint.", minutesAgo: null, paused: false })
        expect(interruptedBannerReport({ ...halted, endedAt: now + 60_000 }, { totalRuns: 5 }, now)?.minutesAgo).toBe(0)
    })

    it("says when the player paused the queue after a career, so the banner never reads as a crash", () => {
        const paused = view(
            { kind: "STOPPED_AFTER_CAREER", resumable: true, queueEnabled: true, totalRuns: 4, endedAt: now - 5 * 60_000 },
            { title: "Queue paused", reason: "You paused the queue after run 2 of 4. Start continues with run 3.", nextAction: null },
            true
        )
        expect(paused.paused).toBe(true)
        expect(interruptedBannerReport(paused, { totalRuns: 4 }, now)).toEqual({ reason: "You paused the queue after run 2 of 4. Start continues with run 3.", minutesAgo: 5, paused: true })
        expect(halted.paused).toBe(false)
    })

    it("uses only a queue report whose record survived and matches the saved queue", () => {
        expect(interruptedBannerReport(null, { totalRuns: 5 }, now)).toBeNull()
        expect(interruptedBannerReport(halted, null, now)).toBeNull()
        expect(interruptedBannerReport({ ...halted, resumable: false }, { totalRuns: 5 }, now)).toBeNull()
        expect(interruptedBannerReport({ ...halted, runEnding: false }, { totalRuns: 5 }, now)).toBeNull()
        expect(interruptedBannerReport(halted, { totalRuns: 4 }, now)).toBeNull()
        expect(interruptedBannerReport({ ...halted, totalRuns: null }, { totalRuns: 5 }, now)).toBeNull()
    })
})

describe("Home wiring", () => {
    const homeFile = "src/pages/Home/index.tsx"
    const home = fs.readFileSync(path.join(process.cwd(), homeFile), "utf8").replace(/\r\n/g, "\n")

    // Runs the actual Home callback body with host substitutes for React and the bridge.
    function callback(name: string, bindings: Record<string, unknown>) {
        const start = home.indexOf(`    const ${name} = `)
        const end = home.indexOf("\n", home.indexOf("\n    }", start) + 1)
        if (start < 0 || end < start) throw new Error(`Missing callback ${name}`)
        const code = transformSync(`${home.slice(start, end)}\nreturn ${name}`, {
            configFile: false,
            babelrc: false,
            parserOpts: { allowReturnOutsideFunction: true },
            presets: ["@babel/preset-typescript"],
            filename: "callback.ts",
        })!.code!
        return new Function(...Object.keys(bindings), code)(...Object.values(bindings))
    }

    const flush = () => new Promise((resolve) => setImmediate(resolve))
    const stored = payload({ sessionId: "s1", kind: "COMPLETED", queueEnabled: true, totalRuns: 2, completedRuns: 2 })

    it("re-reads the report from the bridge every time it refreshes, as after a remount", async () => {
        const StartModule = { getLastQueueReport: jest.fn(async () => stored) }
        const setLastSession = jest.fn()
        const refresh = callback("refreshLastSession", { StartModule, setLastSession, parseLastSession, useCallback: (f: unknown) => f })
        refresh()
        await flush()
        refresh()
        await flush()
        expect(StartModule.getLastQueueReport).toHaveBeenCalledTimes(2)
        expect(setLastSession).toHaveBeenLastCalledWith(parseLastSession(stored))
    })

    it("keeps what it shows when the bridge fails", async () => {
        const StartModule = { getLastQueueReport: jest.fn(async () => Promise.reject(new Error("bridge"))) }
        const setLastSession = jest.fn()
        callback("refreshLastSession", { StartModule, setLastSession, parseLastSession, useCallback: (f: unknown) => f })()
        await flush()
        expect(setLastSession).not.toHaveBeenCalled()
    })

    it("dismisses the shown report in Kotlin and then re-reads", async () => {
        for (const outcome of ["resolve", "reject"] as const) {
            const StartModule = { dismissLastQueueReport: jest.fn(async (_id: string) => (outcome === "resolve" ? true : Promise.reject(new Error("bridge")))) }
            const setLastSession = jest.fn()
            const refreshLastSession = jest.fn()
            callback("dismissLastSession", { StartModule, setLastSession, refreshLastSession })("s1")
            expect(setLastSession).toHaveBeenCalledWith(null)
            await flush()
            expect(StartModule.dismissLastQueueReport).toHaveBeenCalledWith("s1")
            expect(refreshLastSession).toHaveBeenCalledTimes(1)
        }
    })

    it("refreshes on mount, on every bot end and when the app returns to the foreground", () => {
        const mount = home.slice(home.indexOf("        getVersion()\n"), home.indexOf("        return () => {\n            mediaProjectionSubscription.remove()"))
        expect(mount).toContain("refreshLastSession()")
        const botEnd = home.slice(home.indexOf('dispatchSession({ type: "BOT_NOT_RUNNING" })'), home.indexOf("const queueProgressSubscription"))
        expect(botEnd).toContain("refreshInterruptedQueue()\n                refreshLastSession()")
        const foreground = home.slice(home.indexOf('if (nextState === "active") {'), home.indexOf("return () => subscription.remove()"))
        expect(foreground).toContain("refreshLastSession()")
        expect(home).toContain("}, [refreshInterruptedQueue, refreshLastSession, refreshSecureSettingsGrant])")
    })

    it("shows the card by the pure rule, hands over from the end banner, and states the dismiss consequence at the control", () => {
        expect(home).toContain("{lastSession && lastSessionCardVisible(lastSession, botRunning, queueProgressView?.isTerminal === true) && (")
        expect(home).toContain("setTimeout(() => setQueueProgress((shown) => (shown === event ? null : shown)), 10000)")
        const control = home.slice(home.indexOf("onPress={() => dismissLastSession(lastSession.sessionId)}"), home.indexOf("Hides this summary."))
        expect(control).toContain(">Dismiss</Text>")
        expect(control.length).toBeLessThan(600)
    })

    it("predicts the resume the way Kotlin does: a career in flight is re-entered, rotation or not", () => {
        const memo = home.slice(home.indexOf("const noAutoResumeReason"), home.indexOf("\n    }, [", home.indexOf("const noAutoResumeReason")))
        expect(memo).toContain('const nextRun = interruptedQueue.phase === "career" ? interruptedQueue.currentRun : interruptedQueue.currentRun + 1')
        expect(memo).not.toContain("enableTraineeRotation")
    })

    it("gives the interrupted banner the report's reason and its end time", () => {
        expect(home).toContain("interruptedBannerReport(lastSession, interruptedQueue, Date.now())")
        expect(home).toContain("({interruptedReport?.minutesAgo ?? Math.round(interruptedQueue.ageMinutes)} min ago)")
        expect(home).toContain("{interruptedReport && <Text")
        expect(home).toContain("{interruptedReport.reason}</Text>}")
    })

    it("offers Stop after this career by the pure rule, asks first, withdraws at once, and reads the request back", async () => {
        expect(home).toContain("const stopAfterCareerAvailable = offersStopAfterCareer(botRunning, queueProgress)")
        const indent = "\n                            "
        expect(home).toContain(`accessibilityRole="button"${indent}accessibilityLabel={stopAfterCareer ? "Cancel the stop after this career" : "Stop the queue after this career"}`)
        expect(home).toContain(`onPress={() => StartModule.skipQueueRun()}${indent}accessibilityRole="button"${indent}accessibilityLabel="Skip this run"`)
        expect(home).toContain("onPress={() => (stopAfterCareer ? requestStopAfterCareer(false) : confirmStopAfterCareer(Alert.alert, () => requestStopAfterCareer(true)))}")
        const asked: boolean[] = []
        const shown: boolean[] = []
        const request = callback("requestStopAfterCareer", {
            useCallback: (f: unknown) => f,
            sendStopAfterCareer,
            showSnackbar: () => {},
            StartModule: { setStopAfterCareer: (r: boolean) => (asked.push(r), Promise.resolve(r)) },
            setStopAfterCareer: (v: boolean) => shown.push(v),
        })
        request(true)
        request(false)
        await flush()
        expect(asked).toEqual([true, false])
        expect(shown).toEqual([true, false])
        const read: boolean[] = []
        const refresh = callback("refreshSessionState", {
            useCallback: (f: unknown) => f,
            liveSessionEvents: { current: 0 },
            StartModule: { getSessionState: () => Promise.resolve({ armed: true, botRunning: true, stopAfterCareer: true }) },
            dispatchSession: () => {},
            setSessionKnown: () => {},
            setStopAfterCareer: (v: boolean) => read.push(v),
        })
        refresh()
        await flush()
        expect(read).toEqual([true])
    })

    it("words a saved pause as a pause, and says which run Start continues with", () => {
        expect(home).toContain("? `Queue paused after run ${interruptedQueue.currentRun} of ${interruptedQueue.totalRuns}`")
        expect(home).toContain("? `Pressing Start continues this queue with run ${interruptedQueue.currentRun + 1}.`")
    })
})
