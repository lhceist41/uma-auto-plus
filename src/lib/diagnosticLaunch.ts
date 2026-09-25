import type { Settings } from "../context/BotStateContext"

// This request survives settings replacement, but never authorizes a launch after a JS restart.
let requestedKey: string | null = null
let revision = 0
let consumed = false
let normalConfirmed = false
let acknowledgedLaunchId: string | null = null

/** Returns the natively acknowledged launch the caller must revoke, because it no longer matches the choice. */
export function requestDiagnostic(key: string | null) {
    const superseded = acknowledgedLaunchId
    acknowledgedLaunchId = null
    requestedKey = key
    consumed = false
    normalConfirmed = false
    revision++
    return superseded
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

export function diagnosticLaunch(settings: Settings, confirmNormal: boolean) {
    const request = diagnosticRequest()
    if (request.consumed) throw new Error("Select the diagnostic again before another launch")
    const armed = Object.entries(settings.debug).filter(([key, value]) => key.startsWith("debugMode_start") && value === true).map(([key]) => key)
    if (request.key === null) {
        if ((!confirmNormal && !normalConfirmed) || armed.length !== 0) throw new Error("Choose normal Start explicitly or select a diagnostic again")
        normalConfirmed = true
    } else if (armed.length !== 1 || armed[0] !== request.key) {
        throw new Error("Diagnostic settings no longer match the requested test")
    }
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
