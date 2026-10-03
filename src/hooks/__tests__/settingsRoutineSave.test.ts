import fs from "fs"
import path from "path"
import { transformSync } from "@babel/core"
import { defaultSettings } from "../../context/BotStateContext"
import { identityFromRows, launchConfigIdentity, verifyLaunchConfigPersisted } from "../../lib/launchConfig"
import { applyMigrations, changedSettingsRows, convertSettingsToBatch, deepMerge, rememberPersistedRows, withBundledData } from "../../lib/settingsUtils"
import { performSettingsImport } from "../../lib/settingsImport"
import { diagnosticRequest, markDiagnosticClearedByImport, requestDiagnostic } from "../../lib/diagnosticLaunch"

// Import the real defaults without loading the provider's native styling runtime.
jest.mock("react-native-css-interop/jsx-runtime", () => jest.requireActual("react/jsx-runtime"))

const MANAGER = path.join(process.cwd(), "src/hooks/useSettingsManager.tsx")

function callback(name: string, bindings: Record<string, unknown>) {
    const source = fs.readFileSync(MANAGER, "utf8")
    const start = source.indexOf(`    const ${name} = `)
    const end = source.indexOf("\n", source.indexOf("\n    }", start) + 1)
    if (start < 0 || end < start) throw new Error(`Missing callback ${name}`)
    const code = transformSync(`${source.slice(start, end)}\nreturn ${name}`, {
        configFile: false,
        babelrc: false,
        parserOpts: { allowReturnOutsideFunction: true },
        presets: ["@babel/preset-typescript"],
        filename: "callback.ts",
    })!.code!
    return new Function(...Object.keys(bindings), code)(...Object.values(bindings))
}

type Row = { category: string; key: string; value: unknown }

/** The settings manager's real callbacks over an in-memory settings table that the bot can also write. */
function app() {
    const rows: Record<string, string> = {}
    const writes: Row[][] = []
    const base = deepMerge(JSON.parse(JSON.stringify(defaultSettings)), { general: { scenario: "URA Finale" }, training: { maximumFailureChance: 20 } } as any)
    for (const row of convertSettingsToBatch(base)) rows[`${row.category}.${row.key}`] = JSON.stringify(row.value)

    const databaseManager = {
        initialize: async () => {},
        loadAllSettings: async () => {
            const out: Record<string, Record<string, unknown>> = {}
            for (const [id, value] of Object.entries(rows)) {
                const [category, key] = id.split(".")
                out[category] = { ...out[category], [key]: JSON.parse(value) }
            }
            return out
        },
        saveSettingsBatch: async (batch: Row[]) => {
            writes.push(batch)
            for (const row of batch) rows[`${row.category}.${row.key}`] = JSON.stringify(row.value)
        },
        loadSettingsRowsSnapshot: async () => Object.fromEntries(Object.entries(rows).map(([id, value]) => [id, JSON.parse(value)])),
        failStalledWriter: jest.fn(),
        getCurrentProfileName: async () => null,
        replaceAllProfiles: jest.fn(),
        setCurrentProfileName: jest.fn(),
    }
    const bsc = {
        settings: JSON.parse(JSON.stringify(defaultSettings)),
        setSettings: (next: any) => {
            bsc.settings = next
        },
        setReadyStatus: jest.fn(),
    }
    let fileText = ""
    const bindings: Record<string, unknown> = {
        bsc,
        settingsRef: {
            get current() {
                return bsc.settings
            },
        },
        hasLoadedRef: { current: false },
        persistedRef: { current: {} },
        autoSaveTimerRef: { current: null },
        databaseManager,
        defaultSettings,
        deepMerge,
        applyMigrations,
        convertSettingsToBatch,
        changedSettingsRows,
        rememberPersistedRows,
        withBundledData,
        launchConfigIdentity,
        verifyLaunchConfigPersisted,
        identityFromRows,
        performSettingsImport,
        diagnosticRequest,
        markDiagnosticClearedByImport,
        requestDiagnostic,
        revokeSupersededDiagnostic: jest.fn(),
        FileSystem: { readAsStringAsync: async () => fileText },
        useCallback: (fn: unknown) => fn,
        startTiming: () => jest.fn(),
        logWithTimestamp: jest.fn(),
        logErrorWithTimestamp: jest.fn(),
        setIsSaving: jest.fn(),
        isSQLiteInitialized: true,
        LAUNCH_FLUSH_TIMEOUT_MS: 1000,
        setTimeout,
        clearTimeout,
    }
    bindings.saveChangedSettings = callback("saveChangedSettings", bindings)
    bindings.fixSettings = callback("fixSettings", bindings)
    bindings.loadFromJSONFile = callback("loadFromJSONFile", bindings)
    return {
        rows,
        writes,
        bsc,
        /** The bot copying a rotation slot into the live rows. */
        botApplies: (values: Record<string, unknown>) => {
            for (const [id, value] of Object.entries(values)) rows[id] = JSON.stringify(value)
        },
        setFile: (text: string) => {
            fileText = text
        },
        load: () => callback("loadSettings", bindings)(true),
        backgroundSave: () => callback("saveSettingsImmediate", bindings)(),
        saveImmediate: (settings: any) => callback("saveSettingsImmediate", bindings)(settings),
        autoSave: () => (bindings.saveChangedSettings as (s: any) => Promise<void>)(bsc.settings),
        start: () => callback("flushAndVerifyLaunchConfig", bindings)(bsc.settings),
        importSettings: () => callback("importSettings", bindings)("file://settings.json"),
        resetSettings: () => callback("resetSettings", bindings)(),
    }
}

const ROW_COUNT = convertSettingsToBatch(defaultSettings).length
const slot = { "general.scenario": "Grand Concert", "training.maximumFailureChance": 35 }

describe("routine settings saves write only what the player changed", () => {
    it("leaving the app after Start writes no row over the slot the bot applied", async () => {
        const a = app()
        await a.load()
        expect((await a.start()).ok).toBe(true)
        a.writes.length = 0
        a.botApplies(slot)

        await a.backgroundSave()

        expect(a.writes.flat()).toEqual([])
        expect(JSON.parse(a.rows["general.scenario"])).toBe("Grand Concert")
        expect(JSON.parse(a.rows["training.maximumFailureChance"])).toBe(35)
    })

    it("leaving the app right after loading writes no row, even when the database already holds other values", async () => {
        const a = app()
        await a.load()
        a.botApplies(slot)

        await a.backgroundSave()

        expect(a.writes.flat()).toEqual([])
        expect(JSON.parse(a.rows["general.scenario"])).toBe("Grand Concert")
    })

    it("a setting changed during a queue is saved alone, by an explicit save and by the auto-save", async () => {
        const a = app()
        await a.load()
        await a.start()
        a.writes.length = 0
        a.botApplies(slot)

        const edited = { ...a.bsc.settings, debug: { ...a.bsc.settings.debug, enableDebugMode: !a.bsc.settings.debug.enableDebugMode } }
        a.bsc.setSettings(edited)
        await a.saveImmediate(edited)
        expect(a.writes.flat().map((row) => `${row.category}.${row.key}`)).toEqual(["debug.enableDebugMode"])

        a.writes.length = 0
        a.bsc.setSettings({ ...a.bsc.settings, discord: { ...a.bsc.settings.discord, discordToken: "token" } })
        await a.autoSave()
        await a.backgroundSave()
        expect(a.writes.flat().map((row) => `${row.category}.${row.key}`)).toEqual(["discord.discordToken"])
        expect(JSON.parse(a.rows["general.scenario"])).toBe("Grand Concert")
    })

    it("the auto-save timer saves through the changed-rows path", () => {
        const source = fs.readFileSync(MANAGER, "utf8")
        const timer = source.slice(source.indexOf("autoSaveTimerRef.current = setTimeout"), source.indexOf("}, 500)"))
        expect(timer).toContain("await saveChangedSettings(settingsRef.current)")
        expect(timer).not.toContain("saveSettingsBatch")
    })
})

describe("deliberate replacements still write every row", () => {
    it("Start writes every row even when nothing changed", async () => {
        const a = app()
        await a.load()
        a.writes.length = 0
        expect((await a.start()).ok).toBe(true)
        expect(a.writes).toHaveLength(1)
        expect(a.writes[0]).toHaveLength(ROW_COUNT)
    })

    it("Reset Settings writes every row", async () => {
        const a = app()
        await a.load()
        a.writes.length = 0
        expect(await a.resetSettings()).toBe(true)
        expect(a.writes).toHaveLength(1)
        expect(a.writes[0]).toHaveLength(ROW_COUNT)
    })

    it("an import writes every row, then a background save writes none", async () => {
        const a = app()
        await a.load()
        a.setFile(JSON.stringify({ ...JSON.parse(JSON.stringify(a.bsc.settings)), general: { ...a.bsc.settings.general, scenario: "Trackblazer" } }))
        a.writes.length = 0
        expect((await a.importSettings()).outcome).toBe("success")
        expect(a.writes).toHaveLength(1)
        expect(a.writes[0]).toHaveLength(ROW_COUNT)

        a.botApplies(slot)
        a.writes.length = 0
        await a.backgroundSave()
        expect(a.writes.flat()).toEqual([])
    })
})
