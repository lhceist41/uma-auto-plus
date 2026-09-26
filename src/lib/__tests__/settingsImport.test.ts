import { performSettingsImport, SettingsImportDeps, ImportedProfile } from "../settingsImport"
import { Settings } from "../../context/BotStateContext"

const SETTINGS = { general: { scenario: "URA Finale" } } as unknown as Settings

const DEFAULT_IMPLS = {
    readFile: async () => ({ settings: SETTINGS }) as { settings: Settings; profiles?: ImportedProfile[] },
    saveSettings: async () => {},
    applySettings: () => {},
    hasActiveProfile: async () => true,
    replaceAllProfiles: async () => {},
    setActiveProfileName: async () => {},
}

/**
 * A deps object where every call records that it happened (even when overridden), and every step
 * defaults to succeeding. Recording wraps the override itself, so a test can both customize behavior
 * (e.g. throw) and still see the call show up in `calls` up to the point of the throw.
 */
function makeDeps(overrides: Partial<typeof DEFAULT_IMPLS> = {}): SettingsImportDeps & { calls: string[] } {
    const calls: string[] = []
    const impls = { ...DEFAULT_IMPLS, ...overrides }

    const record =
        <A extends unknown[], R>(name: string, fn: (...args: A) => R) =>
        (...args: A): R => {
            calls.push(name)
            return fn(...args)
        }

    return {
        calls,
        readFile: record("readFile", impls.readFile),
        saveSettings: record("saveSettings", impls.saveSettings),
        applySettings: record("applySettings", impls.applySettings),
        hasActiveProfile: record("hasActiveProfile", impls.hasActiveProfile),
        replaceAllProfiles: record("replaceAllProfiles", impls.replaceAllProfiles),
        setActiveProfileName: record("setActiveProfileName", impls.setActiveProfileName),
    }
}

describe("performSettingsImport", () => {
    it("succeeds with no profiles in the file: settings are saved and applied, profiles are never touched", async () => {
        const deps = makeDeps()

        const result = await performSettingsImport(deps)

        expect(result).toEqual({ outcome: "success", message: "Settings imported.", profilesImported: 0 })
        expect(deps.calls).toEqual(["readFile", "hasActiveProfile", "saveSettings", "applySettings"])
    })

    it("succeeds with profiles: the active pointer is read before saveSettings runs, then settings are saved, then profiles are replaced, then the pointer is restored (this is the guard: read it after saveSettings instead, and it always sees no active profile, since the imported settings always carry a stripped-empty currentProfileName)", async () => {
        const profiles: ImportedProfile[] = [
            { name: "Career A", settings: {} },
            { name: "Career B", settings: {} },
        ]
        const deps = makeDeps({
            readFile: async () => ({ settings: SETTINGS, profiles }),
        })

        const result = await performSettingsImport(deps)

        expect(result).toEqual({ outcome: "success", message: "Settings imported with 2 profiles.", profilesImported: 2 })
        expect(deps.calls).toEqual(["readFile", "hasActiveProfile", "saveSettings", "applySettings", "replaceAllProfiles", "setActiveProfileName"])
    })

    it("does not point the active-profile pointer at the import when no profile was previously active", async () => {
        const deps = makeDeps({
            readFile: async () => ({ settings: SETTINGS, profiles: [{ name: "Career A", settings: {} }] }),
            hasActiveProfile: async () => false,
        })

        const result = await performSettingsImport(deps)

        expect(result.outcome).toBe("success")
        expect(deps.calls).not.toContain("setActiveProfileName")
    })

    it("an unreadable file returns failure and never touches settings or profiles (this is the guard: remove the try/catch around readFile and this fails)", async () => {
        const deps = makeDeps({
            readFile: async () => {
                throw new Error("ENOENT: no such file")
            },
        })

        const result = await performSettingsImport(deps)

        expect(result.outcome).toBe("failure")
        expect(result.message).not.toMatch(/ENOENT/)
        expect(deps.calls).toEqual(["readFile"])
    })

    it("a failed settings save returns failure without applying the settings or touching profiles", async () => {
        const deps = makeDeps({
            readFile: async () => ({ settings: SETTINGS, profiles: [{ name: "Career A", settings: {} }] }),
            saveSettings: async () => {
                throw new Error("disk full")
            },
        })

        const result = await performSettingsImport(deps)

        expect(result.outcome).toBe("failure")
        expect(result.message).not.toMatch(/disk full/)
        expect(deps.calls).toEqual(["readFile", "hasActiveProfile", "saveSettings"])
        expect(deps.calls).not.toContain("applySettings")
    })

    it(
        "a profile-write failure reports partial, and never calls any deps step that could delete the old profiles " +
            "outside of replaceAllProfiles's own atomic contract (this is the guard: performSettingsImport has no " +
            "separate delete step to call, so old profiles can only be lost if replaceAllProfiles itself is unsafe)",
        async () => {
            const deps = makeDeps({
                readFile: async () => ({ settings: SETTINGS, profiles: [{ name: "Career A", settings: {} }] }),
                replaceAllProfiles: async () => {
                    throw new Error("UNIQUE constraint failed")
                },
            })

            const result = await performSettingsImport(deps)

            expect(result).toEqual({
                outcome: "partial",
                message: "Settings imported, but your saved profiles could not be replaced and were left unchanged.",
                profilesImported: 0,
            })
            expect(result.message).not.toMatch(/UNIQUE constraint/)
            // Settings were still saved and applied; only the profile replacement failed.
            expect(deps.calls).toEqual(["readFile", "hasActiveProfile", "saveSettings", "applySettings", "replaceAllProfiles"])
            // In particular, the active-profile pointer is never touched after a failed replace.
            expect(deps.calls).not.toContain("setActiveProfileName")
        }
    )

    it(
        "restores the active-profile pointer even though saveSettings would make hasActiveProfile read back false " +
            "afterward (the real bug: an exported file's misc.currentProfileName is always stripped to \"\", so " +
            "reading the pointer after saveSettings runs always sees no active profile, whatever was active before " +
            "the import). This is the guard: read hasActiveProfile after saveSettings instead of before, and this fails",
        async () => {
            // Models the real database: a profile is active until saveSettings persists the imported
            // settings, at which point the stored currentProfileName becomes the export's stripped "".
            let settingsSaved = false
            const deps = makeDeps({
                readFile: async () => ({ settings: SETTINGS, profiles: [{ name: "Career A", settings: {} }] }),
                saveSettings: async () => {
                    settingsSaved = true
                },
                hasActiveProfile: async () => !settingsSaved,
            })

            const result = await performSettingsImport(deps)

            expect(result.outcome).toBe("success")
            expect(deps.calls).toContain("setActiveProfileName")
        }
    )

    it("a failure setting the active-profile pointer does not turn a successful import into a failure", async () => {
        const deps = makeDeps({
            readFile: async () => ({ settings: SETTINGS, profiles: [{ name: "Career A", settings: {} }] }),
            setActiveProfileName: async () => {
                throw new Error("write conflict")
            },
        })

        const result = await performSettingsImport(deps)

        expect(result.outcome).toBe("success")
        expect(result.profilesImported).toBe(1)
    })
})
