import { logWithTimestamp } from "./logger"

/**
 * Deep merges two objects, preserving nested structure.
 * @param target - The target object to merge into.
 * @param source - The source object to merge from.
 * @returns A new object with merged values from both target and source.
 */
export const deepMerge = <T extends Record<string, any>>(target: T, source: Partial<T>): T => {
    const output = { ...target }
    for (const key in source) {
        if (source[key] && typeof source[key] === "object" && !Array.isArray(source[key]) && source[key] !== null) {
            output[key] = deepMerge((target[key] || {}) as Record<string, any>, source[key] as any) as T[Extract<keyof T, string>]
        } else if (source[key] !== undefined) {
            output[key] = source[key] as T[Extract<keyof T, string>]
        }
    }
    return output
}

/**
 * Reference datasets the app writes into the settings table on every start (useBootstrap), keyed
 * by category. They are not the player's settings: Export leaves most of them out, and a
 * settings object applied without them no longer matches the rows on disk, which the Start check
 * refuses until the app is restarted and reloads every row.
 */
const BUNDLED_DATA_KEYS: Record<string, string[]> = {
    trainingEvent: ["characterEventData", "supportEventData", "scenarioEventData"],
    racing: ["racingPlanData"],
}

/**
 * Copies the bundled datasets from the settings the app is running with into settings about to
 * replace them (an import or a reset), so a file can neither drop them nor bring an older copy.
 * @param next - The settings about to be applied.
 * @param current - The settings the app is running with.
 * @returns `next` with the current bundled datasets.
 */
export const withBundledData = <T extends Record<string, any>>(next: T, current: Record<string, any>): T => {
    const output: Record<string, any> = { ...next }
    for (const [category, keys] of Object.entries(BUNDLED_DATA_KEYS)) {
        for (const key of keys) {
            if (current?.[category]?.[key] === undefined) continue
            output[category] = { ...output[category], [key]: current[category][key] }
        }
    }
    return output as T
}

/**
 * Converts `Settings` object to database batch format.
 * @param settings - The `Settings` object to convert.
 * @returns An array of objects in the format `{ category: string; key: string; value: any }`.
 */
export const convertSettingsToBatch = (settings: Record<string, any>) => {
    const batch: { category: string; key: string; value: any }[] = []

    Object.entries(settings).forEach(([category, categorySettings]) => {
        // Never serialize trainee-rotation snapshot categories (`rot{i}_*`). They are produced only by
        // buildRotationSnapshotRows (which re-prefixes this function's output) and consumed only by the
        // Kotlin queue. If a polluted in-memory Settings object carried them in, re-emitting them here
        // would let buildRotationSnapshotRows compound the prefix (`rot0_` -> `rot0_rot0_` ...) every
        // run — the runaway that ballooned settings.db to 319 MB. Skip them at the serialization gate.
        // `queueState` is likewise Kotlin-owned live state (active/currentRun/phase/...) written raw by
        // StartModule mid-run; re-emitting a bootstrap-time snapshot of it here clobbered rotation
        // switches and resurrected dead queues whenever a background save fired during a run.
        if (/^rot[0-9]/.test(category) || category === "queueState") return
        Object.entries(categorySettings).forEach(([key, value]) => {
            batch.push({ category, key, value })
        })
    })

    return batch
}

/**
 * Applies all registered migrations to the Settings object.
 * @param settings - The Settings object to apply migrations to.
 * @returns An object containing the migrated Settings object and a boolean indicating whether any migrations were applied.
 */
export const applyMigrations = (settings: any): { settings: any; anyMigrated: boolean } => {
    let anyMigrated = false
    let migratedSettings = settings

    // Migration: Move Training Event specific OCR settings to trainingEvent category.
    const ocr = (migratedSettings as any).ocr
    const debug = (migratedSettings as any).debug

    // Ensure the migration DESTINATION categories exist before assigning into them. A legacy or
    // partial imported settings file can carry an `ocr` (or `debug`) block without the newer
    // `trainingEvent` category, in which case the assignments below threw "Cannot set property of
    // undefined" - an uncaught crash on the bootstrap settings-load path. Only created when there is
    // something to migrate, so a clean settings object is untouched.
    if (ocr || debug) {
        ;(migratedSettings as any).trainingEvent ??= {}
        ;(migratedSettings as any).debug ??= {}
    }

    if (ocr?.ocrConfidence !== undefined) {
        migratedSettings.trainingEvent.ocrConfidence = ocr.ocrConfidence
        delete ocr.ocrConfidence
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Migrated ocrConfidence to trainingEvent category.")
    }

    if (ocr?.enableAutomaticOCRRetry !== undefined) {
        migratedSettings.trainingEvent.enableAutomaticOCRRetry = ocr.enableAutomaticOCRRetry
        delete ocr.enableAutomaticOCRRetry
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Migrated enableAutomaticOCRRetry to trainingEvent category.")
    }

    if (debug?.enableHideOCRComparisonResults !== undefined) {
        migratedSettings.trainingEvent.enableHideOCRComparisonResults = debug.enableHideOCRComparisonResults
        delete debug.enableHideOCRComparisonResults
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Migrated enableHideOCRComparisonResults to trainingEvent category.")
    }

    if (ocr?.ocrThreshold !== undefined) {
        migratedSettings.debug.ocrThreshold = ocr.ocrThreshold
        delete ocr.ocrThreshold
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Migrated ocrThreshold to debug category.")
    }

    // After moving all OCR settings, delete the empty ocr object.
    if (migratedSettings && (migratedSettings as any).ocr && Object.keys((migratedSettings as any).ocr).length === 0) {
        delete (migratedSettings as any).ocr
    }

    // Migration: Convert single stopAtDate string to stopAtDates array.
    const general = migratedSettings.general as any
    if (general?.stopAtDate !== undefined && typeof general.stopAtDate === "string") {
        migratedSettings.general.stopAtDates = [general.stopAtDate]
        delete general.stopAtDate
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Migrated stopAtDate to stopAtDates array.")
    }

    // The removed Trackblazer race-retry settings leave stored rows nothing reads; drop them on load and Export without flagging a save.
    const overrides = (migratedSettings as any).scenarioOverrides
    if (overrides) {
        delete overrides.trackblazerMaxRetriesPerRace
        delete overrides.trackblazerRetryRacesBeforeFinalGrades
    }

    return { settings: migratedSettings, anyMigrated }
}
