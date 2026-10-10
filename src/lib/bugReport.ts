import type { Settings } from "../context/BotStateContext"

/** Settings that identify the player or open access to something. They never go into a bug report. */
const SECRET_PATHS: readonly (readonly [string, string])[] = [
    ["discord", "discordToken"],
    ["discord", "discordUserID"],
    ["debug", "hostInputPairingCode"],
]

/** Catches a credential added to the settings later: any key with one of these words is removed wherever it sits. */
export const SECRET_KEY_PATTERN = /token|secret|password|passcode|webhook|pairing|api[_-]?key|authorization|user[_-]?id/i

/** Large reference tables the report does not need (the same ones Export Settings drops). */
const BULKY_PATHS: readonly (readonly [string, string])[] = [
    ["racing", "racingPlanData"],
    ["trainingEvent", "characterEventData"],
    ["trainingEvent", "supportEventData"],
    ["trainingEvent", "scenarioEventData"],
    ["misc", "formattedSettingsString"],
]

const isSecretKey = (key: string) => SECRET_KEY_PATTERN.test(key)

const isScalar = (value: unknown): value is string | number => typeof value === "string" || typeof value === "number"

const isPlainObject = (value: unknown): value is Record<string, unknown> => typeof value === "object" && value !== null && !Array.isArray(value)

const stripSecrets = (value: unknown): unknown => {
    if (Array.isArray(value)) return value.map(stripSecrets)
    if (!isPlainObject(value)) return value
    const out: Record<string, unknown> = {}
    for (const [key, inner] of Object.entries(value)) {
        if (!isSecretKey(key)) out[key] = stripSecrets(inner)
    }
    return out
}

/** A copy of the settings that is safe to share: secrets and bulky tables removed. */
export const scrubSettingsForReport = (settings: Settings): Record<string, unknown> => {
    const copy = stripSecrets(JSON.parse(JSON.stringify(settings))) as Record<string, Record<string, unknown>>
    for (const [category, key] of [...SECRET_PATHS, ...BULKY_PATHS]) {
        if (isPlainObject(copy[category])) delete copy[category][key]
    }
    return copy
}

const MIN_SECRET_LENGTH = 4

/** The secret values themselves, so the log text can be scrubbed of them too. */
export const collectSecretValues = (settings: Settings): string[] => {
    const found = new Set<string>()
    const addSecret = (value: string) => {
        if (value.length >= MIN_SECRET_LENGTH) found.add(value)
    }
    const visit = (value: unknown) => {
        if (Array.isArray(value)) return value.forEach(visit)
        if (!isPlainObject(value)) return
        for (const [key, inner] of Object.entries(value)) {
            if (isSecretKey(key) && isScalar(inner)) addSecret(String(inner))
            else visit(inner)
        }
    }
    visit(settings)
    for (const [category, key] of SECRET_PATHS) {
        const inner = (settings as unknown as Record<string, Record<string, unknown> | undefined>)[category]?.[key]
        if (isScalar(inner)) addSecret(String(inner))
    }
    return [...found]
}

/** The run context lines for report.txt (the device and app lines are added natively). */
export const bugReportContextLines = (settings: Settings, now: Date): string[] => {
    const lines = [`Time: ${now.toISOString()}`, `Scenario: ${settings.general.scenario}`]
    const rotation = settings.runQueue.traineeRotation ?? []
    if (!settings.runQueue.enableRunQueue) lines.push("Run queue: off")
    else lines.push(`Run queue: on, ${settings.runQueue.totalRuns} runs`)
    rotation.slice(0, 6).forEach((entry, i) => lines.push(`Rotation ${i + 1}: ${entry.inGameName} / ${entry.presetKey} (${entry.scenario})`))
    return lines
}

export type BugReportShareDeps = {
    build: (settingsJson: string, contextLines: string, secrets: string[]) => Promise<string>
    canShare: () => Promise<boolean>
    share: (fileUri: string) => Promise<void>
    alert: (title: string, message: string) => void
    log: (message: string, error: unknown) => void
}

export const BUG_REPORT_BUILD_FAILED = "The bug report could not be built. Try again. If it keeps failing, send the log from a PC as described under Reporting a bug in TROUBLESHOOTING.md."
export const BUG_REPORT_SHARE_FAILED = "The report was built but could not be shared. Try again."

/** Builds the report, then hands it to the share sheet. Says which of the two steps failed. */
export const shareBugReport = async (settings: Settings, now: Date, deps: BugReportShareDeps): Promise<"shared" | "build-failed" | "share-failed"> => {
    let path: string
    try {
        path = await deps.build(JSON.stringify(scrubSettingsForReport(settings), null, 4), bugReportContextLines(settings, now).join("\n"), collectSecretValues(settings))
    } catch (error) {
        deps.log("Error building the bug report:", error)
        deps.alert("Could Not Build Report", BUG_REPORT_BUILD_FAILED)
        return "build-failed"
    }
    try {
        if (!(await deps.canShare())) throw new Error("The share sheet is not available on this device.")
        await deps.share(`file://${path}`)
        return "shared"
    } catch (error) {
        deps.log("Error sharing the bug report:", error)
        deps.alert("Could Not Share Report", BUG_REPORT_SHARE_FAILED)
        return "share-failed"
    }
}
