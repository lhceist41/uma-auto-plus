import { Settings } from "../context/BotStateContext"

/** The true outcome of a settings import: the player must be able to tell these apart (see docs/UI_TRUTHFULNESS.md rule 3). */
export type ImportSettingsOutcome = "success" | "partial" | "failure"

/** A single profile as read from or written to the settings file / database. */
export interface ImportedProfile {
    name: string
    settings: any
}

/** The structured result of an import: a player-safe summary, never raw error text (docs/UI_TRUTHFULNESS.md rule 4). */
export interface ImportSettingsResult {
    outcome: ImportSettingsOutcome
    message: string
    profilesImported: number
}

/**
 * Dependencies `performSettingsImport` needs, injected so the import sequencing and failure handling
 * are testable without a live database, file system, or React context (same shape as
 * `LaunchBarrierDeps` in `lib/launchConfig.ts`).
 */
export interface SettingsImportDeps {
    /** Read and parse the settings file. Throws on a missing, unreadable, or malformed file. */
    readFile: () => Promise<{ settings: Settings; profiles?: ImportedProfile[] }>
    /** Persist the imported `Settings` object durably. Throws on failure; must not have touched profiles yet. */
    saveSettings: (settings: Settings) => Promise<void>
    /** Apply the imported `Settings` object to in-memory app state. Called only after `saveSettings` succeeds. */
    applySettings: (settings: Settings) => void
    /**
     * Whether a profile is currently active, to decide whether to point the active-profile pointer at the
     * import. Read before `saveSettings` runs: the imported file's `misc.currentProfileName` is always the
     * export's stripped-empty default, so calling this after `saveSettings` has already overwritten the
     * real prior value would always read back false.
     */
    hasActiveProfile: () => Promise<boolean>
    /**
     * Atomically replace every stored profile with `profiles`. Must leave the previous profiles exactly as
     * they were on failure (a single transaction, or an equivalent write-then-delete ordering that never
     * leaves zero profiles visible).
     */
    replaceAllProfiles: (profiles: ImportedProfile[]) => Promise<void>
    /** Point the active-profile pointer at `name`. Best-effort: a failure here does not change the outcome. */
    setActiveProfileName: (name: string) => Promise<void>
}

/**
 * Imports a settings file: read it, save the settings, then replace profiles if the file has any.
 * Returns a structured result instead of a bare boolean so the caller can show the player the true
 * outcome (success, partial, or failure) instead of always claiming success.
 *
 * Order matters: settings are only applied to app state after they are durably saved, and profiles are
 * only touched after settings succeed, through `replaceAllProfiles`, which the caller must implement so
 * a failure there leaves the previous profiles intact.
 */
export async function performSettingsImport(deps: SettingsImportDeps): Promise<ImportSettingsResult> {
    let loaded: { settings: Settings; profiles?: ImportedProfile[] }
    try {
        loaded = await deps.readFile()
    } catch {
        return { outcome: "failure", message: "Could not read that file. Make sure it is a UMA Auto+ settings export.", profilesImported: 0 }
    }

    // Captured before saveSettings writes the imported settings, which always carry a stripped-empty
    // currentProfileName: reading this any later would always see "no profile active", regardless of
    // what was actually active before the import.
    let wasProfileActiveBefore = false
    try {
        wasProfileActiveBefore = await deps.hasActiveProfile()
    } catch {
        wasProfileActiveBefore = false
    }

    try {
        await deps.saveSettings(loaded.settings)
    } catch {
        return { outcome: "failure", message: "Could not save the imported settings. Your previous settings were not changed.", profilesImported: 0 }
    }
    deps.applySettings(loaded.settings)

    const profiles = loaded.profiles
    if (!profiles || !Array.isArray(profiles) || profiles.length === 0) {
        return { outcome: "success", message: "Settings imported.", profilesImported: 0 }
    }

    try {
        await deps.replaceAllProfiles(profiles)
    } catch {
        return {
            outcome: "partial",
            message: "Settings imported, but your saved profiles could not be replaced and were left unchanged.",
            profilesImported: 0,
        }
    }

    if (wasProfileActiveBefore) {
        try {
            await deps.setActiveProfileName(profiles[0].name)
        } catch {
            // Best-effort pointer update: the profiles themselves are already safely imported either way.
        }
    }

    return {
        outcome: "success",
        message: `Settings imported with ${profiles.length} profile${profiles.length === 1 ? "" : "s"}.`,
        profilesImported: profiles.length,
    }
}
