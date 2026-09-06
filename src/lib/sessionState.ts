/**
 * Truthful Home session state: distinguishes "projection armed" (capture permission granted,
 * overlay not yet pressed) from "bot actually running" from "bot ended but projection is still
 * armed". Home used to drive everything off a single isRunning boolean sourced from
 * MediaProjectionService, which looks running before the bot starts and stays looking running
 * after it naturally ends. Pure, no React/React Native imports so it can be unit tested and
 * reasoned about independent of the event-subscription boundary.
 */

export interface SessionState {
    /** MediaProjectionService is up: capture permission granted, Stop is meaningful. */
    armed: boolean
    /** BotService is actually executing (from cold start through the final run of a queue). */
    botRunning: boolean
    /** The bot ended naturally while projection stayed armed, and no new run has started since. */
    endedSinceArm: boolean
}

export const initialSessionState: SessionState = {
    armed: false,
    botRunning: false,
    endedSinceArm: false,
}

export type SessionEvent = { type: "PROJECTION_RUNNING" } | { type: "PROJECTION_NOT_RUNNING" } | { type: "BOT_RUNNING" } | { type: "BOT_NOT_RUNNING" }

/**
 * Advances [state] by one semantic event. Deterministic and idempotent: duplicate or
 * out-of-order events (a late BOT_NOT_RUNNING after projection already dropped, a repeated
 * BOT_RUNNING) are harmless no-ops rather than corrupting the state.
 */
export function sessionStateReducer(state: SessionState, event: SessionEvent): SessionState {
    switch (event.type) {
        case "PROJECTION_RUNNING":
            return { ...state, armed: true }

        case "PROJECTION_NOT_RUNNING":
            // Projection loss ends the session outright, even mid-run: nothing downstream can
            // still be truthfully "running" once the capture pipeline is gone.
            return initialSessionState

        case "BOT_RUNNING":
            // Covers a duplicate Running while already running, and a fresh overlay press after
            // a natural end (no new projection prompt in between) -- either way the bot is
            // running now, and any stale "ended" flag from before no longer applies.
            return { ...state, botRunning: true, endedSinceArm: false }

        case "BOT_NOT_RUNNING":
            // Only a real transition (was running) marks the session ended. A stray
            // Not-Running while already idle, or arriving after projection already reset the
            // whole session, changes nothing.
            if (!state.botRunning) return state
            return { ...state, botRunning: false, endedSinceArm: true }
    }
}

export type SessionPhase = "idle" | "armed" | "running" | "ended"

/** The user-visible phase [state] represents: idle, armed/waiting, running, or ended-but-armed. */
export function sessionPhase(state: SessionState): SessionPhase {
    if (state.botRunning) return "running"
    if (state.endedSinceArm) return "ended"
    if (state.armed) return "armed"
    return "idle"
}
