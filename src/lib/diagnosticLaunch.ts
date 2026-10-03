import type { Settings } from "../context/BotStateContext"

// This request survives settings replacement, but never authorizes a launch after a JS restart.
let requestedKey: string | null = null
let revision = 0
let consumed = false
let normalConfirmed = false
let acknowledgedLaunchId: string | null = null
let clearedByImport = false

export type StartRefusal = "used" | "imported" | "notSelected" | "changed" | "normalNotConfirmed"

const REFUSAL_TEXT: Record<StartRefusal, string> = {
    used: "This diagnostic test selection was already used. Select the test again in Debug Settings to run it again, or turn it off there for a normal Start.",
    imported: "The imported settings turn on a diagnostic test. Select it again in Debug Settings to run it, or turn it off there for a normal Start.",
    notSelected: "A diagnostic test is turned on but was not selected since the app started. Select it again in Debug Settings to run it, or turn it off there for a normal Start.",
    changed: "The diagnostic tests turned on in Debug Settings no longer match the one you selected. Select the test again there, or turn it off for a normal Start.",
    normalNotConfirmed: "Normal Start was not confirmed. Press Start again and choose Start normal automation.",
}

function refuse(refusal: StartRefusal, message: string): never {
    throw Object.assign(new Error(message), { refusal })
}

/** The reason and player text for a refusal thrown by the diagnostic check, or null for any other error. */
export function startRefusal(error: unknown): { reason: StartRefusal; text: string } | null {
    const reason = (error as { refusal?: unknown } | null)?.refusal
    if (typeof reason !== "string" || !Object.prototype.hasOwnProperty.call(REFUSAL_TEXT, reason)) return null
    return { reason: reason as StartRefusal, text: REFUSAL_TEXT[reason as StartRefusal] }
}

/** Returns the natively acknowledged launch the caller must revoke, because it no longer matches the choice. */
export function requestDiagnostic(key: string | null) {
    const superseded = acknowledgedLaunchId
    acknowledgedLaunchId = null
    requestedKey = key
    consumed = false
    normalConfirmed = false
    clearedByImport = false
    revision++
    return superseded
}

/** After an import's save: a diagnostic it turned on is refused as imported, unless the request changed since the import cleared it. */
export function markDiagnosticClearedByImport(clearedRevision: number) {
    if (revision === clearedRevision) clearedByImport = true
}

export function acknowledgeDiagnosticRequest(expectedRevision: number, launchId: string) {
    if (revision === expectedRevision) acknowledgedLaunchId = launchId
}

export function diagnosticRequest() {
    return { key: requestedKey, revision, consumed, normalConfirmed }
}

export function consumeDiagnosticRequest(expectedRevision: number) {
    if (revision !== expectedRevision || consumed) throw new Error("Diagnostic selection changed or was already used")
    if (requestedKey !== null) consumed = true
}

/** Throws a refusal when these settings may not start now; changes no state. */
export function checkDiagnosticLaunch(settings: Settings, confirmNormal: boolean) {
    const request = diagnosticRequest()
    if (request.consumed) refuse("used", "Select the diagnostic again before another launch")
    const armed = Object.entries(settings.debug).filter(([key, value]) => key.startsWith("debugMode_start") && value === true).map(([key]) => key)
    if (request.key === null) {
        if (armed.length !== 0) refuse(clearedByImport ? "imported" : "notSelected", "A diagnostic is turned on but was not selected again")
        if (!confirmNormal && !normalConfirmed) refuse("normalNotConfirmed", "Choose normal Start explicitly or select a diagnostic again")
    } else if (armed.length !== 1 || armed[0] !== request.key) {
        refuse("changed", "Diagnostic settings no longer match the requested test")
    }
}

export function diagnosticLaunch(settings: Settings, confirmNormal: boolean) {
    checkDiagnosticLaunch(settings, confirmNormal)
    const request = diagnosticRequest()
    if (request.key === null) normalConfirmed = true
    return {
        revision: request.revision,
        key: request.key,
        json: JSON.stringify({
            key: request.key,
            scenario: settings.general.scenario,
            veteranRosterScanLimit: settings.debug.veteranRosterScanLimit,
            veteranRosterScanEvidence: settings.debug.veteranRosterScanEvidence,
            veteranInspirationScanLimit: settings.debug.veteranInspirationScanLimit,
            veteranInspirationScanStartIndex: settings.debug.veteranInspirationScanStartIndex,
        }),
    }
}
