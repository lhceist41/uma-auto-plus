import fs from "fs"
import path from "path"
import { transformSync } from "@babel/core"
import { defaultSettings } from "../../context/BotStateContext"
import { identityFromRows, launchConfigIdentity, verifyLaunchConfigPersisted } from "../launchConfig"
import { applyMigrations, convertSettingsToBatch, deepMerge, rememberPersistedRows, withBundledData } from "../settingsUtils"
import { performSettingsImport } from "../settingsImport"
import { acknowledgeDiagnosticRequest, checkDiagnosticLaunch, diagnosticLaunch, diagnosticRequest, markDiagnosticClearedByImport, requestDiagnostic, startRefusal } from "../diagnosticLaunch"

// Import the real defaults without loading the provider's native styling runtime.
jest.mock("react-native-css-interop/jsx-runtime", () => jest.requireActual("react/jsx-runtime"))

// Runs the settings manager's real callbacks against an in-memory settings table, so an import or a
// reset followed by the real Start check behaves as on a device.
function callback(name: string, bindings: Record<string, unknown>, file = "src/hooks/useSettingsManager.tsx") {
    const source = fs.readFileSync(path.join(process.cwd(), file), "utf8")
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
    const revokeSupersededDiagnostic = jest.fn()
    const bindings: Record<string, unknown> = {
        bsc,
        settingsRef,
        databaseManager,
        defaultSettings,
        deepMerge,
        applyMigrations,
        convertSettingsToBatch,
        withBundledData,
        rememberPersistedRows,
        persistedRef: { current: {} },
        performSettingsImport,
        diagnosticRequest,
        markDiagnosticClearedByImport,
        requestDiagnostic,
        revokeSupersededDiagnostic,
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
        revokeSupersededDiagnostic,
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

const ROSTER_TEST = "debugMode_startVeteranRosterScanTest"

/** Presses Home's Start against the device's live settings; the start sequence itself is a stub. */
async function pressStart(d: ReturnType<typeof device>) {
    const ui = { runStartSequence: jest.fn(async () => {}), alert: jest.fn((_title: string, _message: string, buttons: { onPress: () => void }[]) => buttons[1].onPress()), showSnackbar: jest.fn() }
    await callback(
        "proceedToStart",
        {
            bsc: d.bsc, startGate: { begin: () => true, end: () => {} }, Alert: { alert: ui.alert }, runStartSequence: ui.runStartSequence, showSnackbar: ui.showSnackbar,
            checkDiagnosticLaunch, diagnosticRequest, requestDiagnostic, startRefusal, logWithTimestamp: jest.fn(), logErrorWithTimestamp: jest.fn(),
        },
        "src/pages/Home/index.tsx"
    )()
    return ui
}

const IMPORTED = "The imported settings turn on a diagnostic test. Select it again in Debug Settings to run it, or turn it off there for a normal Start."
const NOT_SELECTED = "A diagnostic test is turned on but was not selected since the app started. Select it again in Debug Settings to run it, or turn it off there for a normal Start."

describe("Diagnostic tests after importing or resetting settings", () => {
    beforeEach(() => {
        requestDiagnostic(null)
    })

    it("positive control: a plain import then Start asks for normal Start and starts", async () => {
        const d = device()
        d.setFile(exported(d.bsc.settings, () => {}))
        await d.importSettings("file")
        const ui = await pressStart(d)
        expect(ui.alert).toHaveBeenCalledTimes(1)
        expect(ui.runStartSequence).toHaveBeenCalledWith(true)
        expect(ui.showSnackbar).not.toHaveBeenCalled()
    })

    it("an import that turns on a diagnostic refuses Start with the import reason, before the normal Start question", async () => {
        const d = device()
        d.setFile(exported(d.bsc.settings, (s) => (s.debug[ROSTER_TEST] = true)))
        expect((await d.importSettings("file")).outcome).toBe("success")
        const ui = await pressStart(d)
        expect(ui.showSnackbar).toHaveBeenCalledWith(IMPORTED, "error")
        expect(ui.alert).not.toHaveBeenCalled()
        expect(ui.runStartSequence).not.toHaveBeenCalled()
    })

    it("an import never counts as a selection, even of the diagnostic selected before it", async () => {
        const d = device()
        requestDiagnostic(ROSTER_TEST)
        d.bsc.settings.debug[ROSTER_TEST] = true
        d.setFile(exported(d.bsc.settings, () => {}))
        await d.importSettings("file")
        expect(diagnosticRequest().key).toBeNull()
        expect(() => diagnosticLaunch(d.bsc.settings, true)).toThrow()
        const ui = await pressStart(d)
        expect(ui.showSnackbar).toHaveBeenCalledWith(IMPORTED, "error")
        expect(ui.runStartSequence).not.toHaveBeenCalled()
    })

    it("an import revokes an acknowledged request before it saves, and asks for normal Start again", async () => {
        const d = device()
        diagnosticLaunch(d.bsc.settings, true)
        acknowledgeDiagnosticRequest(diagnosticRequest().revision, "token")
        const save = jest.spyOn(d.databaseManager, "saveSettingsBatch")
        d.setFile(exported(d.bsc.settings, () => {}))
        await d.importSettings("file")
        expect(d.revokeSupersededDiagnostic.mock.calls).toEqual([["token"]])
        expect(d.revokeSupersededDiagnostic.mock.invocationCallOrder[0]).toBeLessThan(save.mock.invocationCallOrder[0])
        expect(diagnosticRequest()).toMatchObject({ key: null, consumed: false, normalConfirmed: false })
        expect((await pressStart(d)).alert).toHaveBeenCalledTimes(1)
    })

    it("an import whose save fails still clears and revokes the selection first, but Start does not blame an import", async () => {
        const d = device()
        requestDiagnostic(ROSTER_TEST)
        acknowledgeDiagnosticRequest(diagnosticRequest().revision, "token")
        d.bsc.settings.debug[ROSTER_TEST] = true
        d.setFile(exported(d.bsc.settings, () => {}))
        const save = jest.spyOn(d.databaseManager, "saveSettingsBatch").mockRejectedValue(new Error("disk full"))
        expect((await d.importSettings("file")).outcome).not.toBe("success")
        expect(d.revokeSupersededDiagnostic.mock.calls).toEqual([["token"]])
        expect(d.revokeSupersededDiagnostic.mock.invocationCallOrder[0]).toBeLessThan(save.mock.invocationCallOrder[0])
        expect(diagnosticRequest().key).toBeNull()
        const ui = await pressStart(d)
        expect(ui.showSnackbar).toHaveBeenCalledWith(NOT_SELECTED, "error")
        expect(ui.showSnackbar).not.toHaveBeenCalledWith(IMPORTED, "error")
        expect(ui.runStartSequence).not.toHaveBeenCalled()
    })

    it("an unreadable file leaves the current selection alone", async () => {
        const d = device()
        requestDiagnostic(ROSTER_TEST)
        d.setFile("not json")
        expect((await d.importSettings("file")).outcome).toBe("failure")
        expect(diagnosticRequest().key).toBe(ROSTER_TEST)
        expect(d.revokeSupersededDiagnostic).not.toHaveBeenCalled()
    })

    it("Reset Settings clears and revokes a selected diagnostic", async () => {
        const d = device()
        requestDiagnostic(ROSTER_TEST)
        acknowledgeDiagnosticRequest(diagnosticRequest().revision, "token")
        expect(await d.resetSettings()).toBe(true)
        expect(d.revokeSupersededDiagnostic.mock.calls).toEqual([["token"]])
        expect(diagnosticRequest().key).toBeNull()
    })
})
