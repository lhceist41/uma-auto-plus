import fs from "fs"
import path from "path"
import { transformSync } from "@babel/core"
import { defaultSettings } from "../../context/BotStateContext"
import { identityFromRows, launchConfigIdentity, verifyLaunchConfigPersisted } from "../launchConfig"
import { applyMigrations, convertSettingsToBatch, deepMerge, withBundledData } from "../settingsUtils"
import { performSettingsImport } from "../settingsImport"

// Import the real defaults without loading the provider's native styling runtime.
jest.mock("react-native-css-interop/jsx-runtime", () => jest.requireActual("react/jsx-runtime"))

// Runs the settings manager's real callbacks against an in-memory settings table, so an import or a
// reset followed by the real Start check behaves as on a device.
function callback(name: string, bindings: Record<string, unknown>) {
    const source = fs.readFileSync(path.join(process.cwd(), "src/hooks/useSettingsManager.tsx"), "utf8")
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

/** The app writes these reference datasets into the settings table on every start. */
const BUNDLED = {
    characterEventData: { "Daiwa Scarlet": { events: 12 } },
    supportEventData: { "Kitasan Black": { events: 7 } },
    scenarioEventData: { Trackblazer: { events: 30 } },
    racingPlanData: JSON.stringify({ races: 201 }),
}

function device() {
    const rows: Record<string, string> = {}
    const write = (batch: { category: string; key: string; value: unknown }[]) => {
        for (const row of batch) rows[`${row.category}.${row.key}`] = typeof row.value === "string" ? row.value : JSON.stringify(row.value)
    }
    // Bootstrap writes the datasets, then the loaded settings (defaults plus every stored row) are saved.
    const loaded = deepMerge(JSON.parse(JSON.stringify(defaultSettings)), {
        trainingEvent: { characterEventData: BUNDLED.characterEventData, supportEventData: BUNDLED.supportEventData, scenarioEventData: BUNDLED.scenarioEventData },
        racing: { racingPlanData: BUNDLED.racingPlanData },
    } as any)
    write(convertSettingsToBatch(loaded))

    const bsc = {
        settings: loaded,
        setSettings: (next: any) => {
            bsc.settings = next
        },
        setReadyStatus: jest.fn(),
    }
    const settingsRef = {
        get current() {
            return bsc.settings
        },
    }
    const databaseManager = {
        initialize: async () => {},
        saveSettingsBatch: async (batch: { category: string; key: string; value: unknown }[]) => write(batch),
        loadSettingsRowsSnapshot: async () => ({ ...rows }),
        failStalledWriter: jest.fn(),
        getCurrentProfileName: async () => null,
        replaceAllProfiles: jest.fn(),
        setCurrentProfileName: jest.fn(),
    }
    let fileText = ""
    const bindings: Record<string, unknown> = {
        bsc,
        settingsRef,
        databaseManager,
        defaultSettings,
        deepMerge,
        applyMigrations,
        convertSettingsToBatch,
        withBundledData,
        performSettingsImport,
        launchConfigIdentity,
        verifyLaunchConfigPersisted,
        identityFromRows,
        FileSystem: { readAsStringAsync: async () => fileText },
        useCallback: (fn: unknown) => fn,
        startTiming: () => jest.fn(),
        logWithTimestamp: jest.fn(),
        logErrorWithTimestamp: jest.fn(),
        setIsSaving: jest.fn(),
        isSQLiteInitialized: true,
        autoSaveTimerRef: { current: null },
        LAUNCH_FLUSH_TIMEOUT_MS: 1000,
        setTimeout,
        clearTimeout,
    }
    bindings.fixSettings = callback("fixSettings", bindings)
    bindings.loadFromJSONFile = callback("loadFromJSONFile", bindings)
    return {
        rows,
        bsc,
        databaseManager,
        setFile: (text: string) => {
            fileText = text
        },
        importSettings: callback("importSettings", bindings),
        resetSettings: callback("resetSettings", bindings),
        start: () => callback("flushAndVerifyLaunchConfig", bindings)(JSON.parse(JSON.stringify(bsc.settings))),
    }
}

/** What Export Settings writes: the live settings without the bulk datasets and misc bookkeeping. */
function exported(settings: any, change: (s: any) => void) {
    const file = JSON.parse(JSON.stringify(settings))
    delete file.racing.racingPlanData
    delete file.trainingEvent.characterEventData
    delete file.trainingEvent.supportEventData
    delete file.misc.formattedSettingsString
    delete file.misc.currentProfileName
    change(file)
    return JSON.stringify(file)
}

describe("Start after importing or resetting settings", () => {
    it("positive control: Start passes on freshly loaded settings", async () => {
        expect((await device().start()).ok).toBe(true)
    })

    it("passes the Start check right after an import, without restarting the app", async () => {
        const d = device()
        d.setFile(
            exported(d.bsc.settings, (s) => {
                s.general.scenario = "URA Finale"
                s.runQueue.totalRuns = 3
            })
        )
        expect((await d.importSettings("file")).outcome).toBe("success")
        const result = await d.start()
        expect(result.reason).toBeUndefined()
        expect(result.ok).toBe(true)
        expect(d.bsc.settings.general.scenario).toBe("URA Finale")
    })

    it("keeps the app's own event and race data when the file carries an older copy", async () => {
        const d = device()
        d.setFile(
            exported(d.bsc.settings, (s) => {
                s.trainingEvent.scenarioEventData = { Trackblazer: { events: 1 } }
            })
        )
        await d.importSettings("file")
        expect((await d.start()).ok).toBe(true)
        expect(JSON.parse(d.rows["trainingEvent.scenarioEventData"])).toEqual(BUNDLED.scenarioEventData)
        expect(d.bsc.settings.racing.racingPlanData).toBe(BUNDLED.racingPlanData)
    })

    it("passes the Start check right after Reset Settings", async () => {
        const d = device()
        expect(await d.resetSettings()).toBe(true)
        expect((await d.start()).ok).toBe(true)
    })

    it("still blocks Start when a launch setting on disk differs after an import", async () => {
        const d = device()
        d.setFile(
            exported(d.bsc.settings, (s) => {
                s.runQueue.totalRuns = 3
            })
        )
        await d.importSettings("file")
        d.databaseManager.loadSettingsRowsSnapshot = async () => ({ ...d.rows, "runQueue.totalRuns": "9" })
        const result = await d.start()
        expect(result.ok).toBe(false)
        expect(result.stage).toBe("verify")
        expect(result.reason).toMatch(/config hash/)
    })
})
