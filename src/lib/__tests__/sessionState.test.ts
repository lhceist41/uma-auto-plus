import { initialSessionState, sessionPhase, sessionStateReducer, SessionEvent, SessionState } from "../sessionState"

/** Applies a sequence of events in order, starting from idle, returning the final state. */
const run = (events: SessionEvent[]): SessionState => events.reduce(sessionStateReducer, initialSessionState)

const PROJECTION_RUNNING: SessionEvent = { type: "PROJECTION_RUNNING" }
const PROJECTION_NOT_RUNNING: SessionEvent = { type: "PROJECTION_NOT_RUNNING" }
const BOT_RUNNING: SessionEvent = { type: "BOT_RUNNING" }
const BOT_NOT_RUNNING: SessionEvent = { type: "BOT_NOT_RUNNING" }

describe("sessionStateReducer event ordering", () => {
    it("1. starts idle", () => {
        expect(sessionPhase(initialSessionState)).toBe("idle")
    })

    it("2. projection Running moves to armed/waiting", () => {
        const state = run([PROJECTION_RUNNING])
        expect(sessionPhase(state)).toBe("armed")
    })

    it("3. Bot Running moves to running", () => {
        const state = run([PROJECTION_RUNNING, BOT_RUNNING])
        expect(sessionPhase(state)).toBe("running")
    })

    it("4. Bot Not Running moves to ended/still armed", () => {
        const state = run([PROJECTION_RUNNING, BOT_RUNNING, BOT_NOT_RUNNING])
        expect(sessionPhase(state)).toBe("ended")
        expect(state.armed).toBe(true)
    })

    it("5. projection Not Running resets to idle", () => {
        const state = run([PROJECTION_RUNNING, BOT_RUNNING, BOT_NOT_RUNNING, PROJECTION_NOT_RUNNING])
        expect(state).toEqual(initialSessionState)
        expect(sessionPhase(state)).toBe("idle")
    })

    it("6. duplicate Bot Running is harmless", () => {
        const once = run([PROJECTION_RUNNING, BOT_RUNNING])
        const twice = run([PROJECTION_RUNNING, BOT_RUNNING, BOT_RUNNING])
        expect(twice).toEqual(once)
        expect(sessionPhase(twice)).toBe("running")
    })

    it("7. Bot Not Running while idle changes nothing", () => {
        const state = run([BOT_NOT_RUNNING])
        expect(state).toEqual(initialSessionState)
    })

    it("8. natural end then overlay re-press without a new projection prompt resumes running", () => {
        const state = run([PROJECTION_RUNNING, BOT_RUNNING, BOT_NOT_RUNNING, BOT_RUNNING])
        expect(sessionPhase(state)).toBe("running")
        expect(state.endedSinceArm).toBe(false)
    })

    it("9. projection loss mid-run resets to idle, not stuck running", () => {
        const state = run([PROJECTION_RUNNING, BOT_RUNNING, PROJECTION_NOT_RUNNING])
        expect(sessionPhase(state)).toBe("idle")
        expect(state.botRunning).toBe(false)
    })

    it("10. Stop/projection-off followed by a late bot-end event stays idle", () => {
        const state = run([PROJECTION_RUNNING, BOT_RUNNING, PROJECTION_NOT_RUNNING, BOT_NOT_RUNNING])
        expect(state).toEqual(initialSessionState)
        expect(sessionPhase(state)).toBe("idle")
    })

    it("11. fast abort ordering (Running, Bot Running, Bot Not Running) ends non-running", () => {
        const state = run([PROJECTION_RUNNING, BOT_RUNNING, BOT_NOT_RUNNING])
        expect(state.botRunning).toBe(false)
        expect(sessionPhase(state)).not.toBe("running")
    })
})

describe("sessionStateReducer additional invariants", () => {
    it("Bot Running before projection Running still marks the bot running (armed stays false)", () => {
        // Out-of-order arrival should not throw or silently drop the event.
        const state = sessionStateReducer(initialSessionState, BOT_RUNNING)
        expect(state.botRunning).toBe(true)
        expect(state.armed).toBe(false)
    })

    it("duplicate projection Running while already armed is a no-op on the other fields", () => {
        const running = run([PROJECTION_RUNNING, BOT_RUNNING])
        const stillRunning = sessionStateReducer(running, PROJECTION_RUNNING)
        expect(stillRunning).toEqual(running)
    })

    it("duplicate Not Running while already idle is a no-op", () => {
        const state = sessionStateReducer(initialSessionState, PROJECTION_NOT_RUNNING)
        expect(state).toEqual(initialSessionState)
    })
})
