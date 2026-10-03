import fs from "fs"
import path from "path"
import { transformSync } from "@babel/core"
import { acknowledgeDiagnosticRequest, checkDiagnosticLaunch, consumeDiagnosticRequest, diagnosticLaunch, diagnosticRequest, requestDiagnostic, startRefusal } from "../diagnosticLaunch"
import { identityFromRows, launchConfigIdentity, verifyLaunchConfigPersisted } from "../launchConfig"
import { convertSettingsToBatch } from "../settingsUtils"
import { buildRotationSnapshotRows, RotationEntry } from "../rotationSnapshots"
import { characterPresets } from "../../data/characterPresets"

// Execute the actual UI callback bodies with host substitutes for platform effects.
function callback(file: string, name: string, bindings: Record<string, unknown>) {
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

function harness() {
    requestDiagnostic(null)
    const key = "debugMode_startVeteranRosterScanTest"
    const other = "debugMode_startSkillListBuyTest"
    const settings = { general: { scenario: "URA Finale" }, debug: { [key]: false, [other]: false, veteranRosterScanLimit: 5, veteranRosterScanEvidence: true, veteranInspirationScanLimit: 3, veteranInspirationScanStartIndex: 2 } as Record<string, boolean | number>, runQueue: { enableTraineeRotation: false, traineeRotation: [] as RotationEntry[] } }
    const bsc = { settings, setSettings: (next: typeof settings) => { bsc.settings = next } }
    const start = jest.fn()
    const StartModule = { getAccessibilityStatus: async () => ({ enabled: true, active: true }), setVerifiedLaunchIdentity: jest.fn(async () => "token"), start, stop: jest.fn(), revokeDiagnosticLaunch: jest.fn(async (_launchId: string) => true) }
    const bindings = {
        bsc, debugTestKeys: [key, other], saveSettingsImmediate: jest.fn(async (_settings: unknown) => {}),
        requestDiagnostic, diagnosticRequest, diagnosticLaunch, consumeDiagnosticRequest, acknowledgeDiagnosticRequest, checkDiagnosticLaunch, startRefusal,
        StartModule, NativeModules: { StartModule },
        Alert: { alert: jest.fn((_title: string, _message: string, buttons: { onPress: () => void }[]) => buttons[1].onPress()) },
        setAccessibilityRequirement: jest.fn(), setShowAccessibilityDialog: jest.fn(),
        logErrorWithTimestamp: jest.fn(), logWithTimestamp: jest.fn(), setPresetSaveState: jest.fn(), showSnackbar: jest.fn(),
        startGate: { mayLaunch: () => true, begin: () => true, end: () => {} },
        flushAndVerifyLaunchConfig: async () => ({ ok: true, persisted: { revision: 1, hash: "verified" } }),
        prepareTraineeRotation: jest.fn(async () => []),
    }
    const launch = callback("src/pages/Home/index.tsx", "runStartSequence", bindings)
    return { key, other, bsc, start, bindings, launch,
        toggle: callback("src/pages/DebugSettings/index.tsx", "handleDebugTestToggle", bindings),
        press: callback("src/pages/Home/index.tsx", "proceedToStart", { ...bindings, runStartSequence: launch }) }
}

test("positive control: ordinary Start reaches the native handoff", async () => {
    const h = harness()
    await h.launch(true)
    expect(h.start).toHaveBeenCalledTimes(1)
})

test("positive control: selected diagnostic reaches the native handoff", async () => {
    const h = harness()
    h.toggle(h.key, true)
    await h.launch()
    expect(h.start).toHaveBeenCalledTimes(1)
    expect(h.bindings.prepareTraineeRotation).not.toHaveBeenCalled()
})

test("deliberate normal selection persists until cancellation without another confirmation", async () => {
    const h = harness()
    await h.launch(true)
    await h.launch()
    expect(h.start).toHaveBeenCalledTimes(2)
    expect(h.bindings.prepareTraineeRotation).toHaveBeenCalledTimes(2)
    requestDiagnostic(null)
    await expect(h.launch()).rejects.toThrow("explicitly")
})

test("parameters are captured before asynchronous persistence and rotation is untouched", async () => {
    const h = harness()
    h.toggle(h.key, true)
    h.bsc.settings.runQueue.enableTraineeRotation = true
    h.bindings.flushAndVerifyLaunchConfig = async () => {
        h.bsc.settings.debug.veteranRosterScanLimit = 99
        return { ok: true, persisted: { revision: 1, hash: "verified" } }
    }
    await callback("src/pages/Home/index.tsx", "runStartSequence", h.bindings)()
    const json = (h.bindings.StartModule.setVerifiedLaunchIdentity.mock.calls as unknown as unknown[][])[0][2] as string
    expect(JSON.parse(json).veteranRosterScanLimit).toBe(5)
    expect(h.bindings.prepareTraineeRotation).not.toHaveBeenCalled()
})

test("Stop while the barrier is pending prevents native acknowledgment", async () => {
    const h = harness()
    h.toggle(h.key, true)
    h.bindings.startGate.mayLaunch = () => false
    await callback("src/pages/Home/index.tsx", "runStartSequence", h.bindings)()
    expect(h.start).not.toHaveBeenCalled()
    expect(h.bindings.StartModule.setVerifiedLaunchIdentity).not.toHaveBeenCalled()
})

test.each(["none", "write rejection", "read failure", "background writer"])("actual settings barrier reaches Home correctly for %s", async (fault) => {
    const h = harness()
    h.toggle(h.key, true)
    let rows: Record<string, unknown> = {}
    const manager = callback("src/hooks/useSettingsManager.tsx", "flushAndVerifyLaunchConfig", {
        useCallback: (fn: unknown) => fn, startTiming: () => jest.fn(), settingsRef: { current: h.bsc.settings },
        launchConfigIdentity, verifyLaunchConfigPersisted, identityFromRows, convertSettingsToBatch,
        autoSaveTimerRef: { current: null }, LAUNCH_FLUSH_TIMEOUT_MS: 1000, setTimeout, clearTimeout,
        databaseManager: {
            initialize: async () => {},
            saveSettingsBatch: async (batch: { category: string; key: string; value: unknown }[]) => {
                if (fault === "write rejection") throw new Error("write rejected")
                rows = Object.fromEntries(batch.map((row) => [`${row.category}.${row.key}`, row.value]))
            },
            loadSettingsRowsSnapshot: async () => {
                if (fault === "read failure") throw new Error("read failed")
                return fault === "background writer" ? { ...rows, "general.scenario": "Trackblazer" } : rows
            },
            failStalledWriter: jest.fn(),
        },
    })
    h.bindings.flushAndVerifyLaunchConfig = manager
    await callback("src/pages/Home/index.tsx", "runStartSequence", h.bindings)()
    expect(h.start).toHaveBeenCalledTimes(fault === "none" ? 1 : 0)
    expect(h.bindings.prepareTraineeRotation).not.toHaveBeenCalled()
})

test("a requested diagnostic lost to a stale full write cannot start", async () => {
    const h = harness()
    h.toggle(h.key, true)
    h.bsc.settings = { ...h.bsc.settings, debug: { [h.key]: false, [h.other]: false } }
    await expect(h.launch()).rejects.toThrow("no longer match")
    expect(h.start).toHaveBeenCalledTimes(0)
})

test("a competing consequential diagnostic cannot share a ParentLab launch", async () => {
    const h = harness()
    h.toggle(h.key, true)
    h.bsc.settings.debug[h.other] = true
    await expect(h.launch()).rejects.toThrow("no longer match")
    expect(h.start).toHaveBeenCalledTimes(0)
})

test("restart or missing request requires explicit normal confirmation", async () => {
    const h = harness()
    await expect(h.launch()).rejects.toThrow("explicitly")
    expect(h.start).not.toHaveBeenCalled()
})

test("a delayed persistence barrier cannot reach native Start early", async () => {
    const h = harness()
    h.toggle(h.key, true)
    let release!: () => void
    h.bindings.flushAndVerifyLaunchConfig = () => new Promise((resolve) => { release = () => resolve({ ok: true, persisted: { revision: 1, hash: "verified" } }) })
    const launch = callback("src/pages/Home/index.tsx", "runStartSequence", h.bindings)
    const pending = launch()
    await Promise.resolve()
    expect(h.start).not.toHaveBeenCalled()
    release()
    await pending
    expect(h.start).toHaveBeenCalledTimes(1)
})

test("rejected persistence cannot reach native Start", async () => {
    const h = harness()
    h.toggle(h.key, true)
    h.bindings.flushAndVerifyLaunchConfig = async () => { throw new Error("write failed") }
    await expect(callback("src/pages/Home/index.tsx", "runStartSequence", h.bindings)()).rejects.toThrow("write failed")
    expect(h.start).not.toHaveBeenCalled()
})

test("selection changes during acknowledgment cancel the native request", async () => {
    const h = harness()
    h.toggle(h.key, true)
    h.bindings.StartModule.setVerifiedLaunchIdentity = jest.fn(async () => { requestDiagnostic(h.other); return "token" })
    await callback("src/pages/Home/index.tsx", "runStartSequence", h.bindings)()
    expect(h.start).not.toHaveBeenCalled()
    expect(h.bindings.StartModule.stop).toHaveBeenCalledTimes(1)
})

test("consumption rejects repeated launches until explicit reselection", async () => {
    const h = harness()
    h.toggle(h.key, true)
    await h.launch()
    await expect(h.launch()).rejects.toThrow("again")
    expect(h.start).toHaveBeenCalledTimes(1)
    h.toggle(h.key, true)
    await h.launch()
    expect(h.start).toHaveBeenCalledTimes(2)
})

test.each(["cancellation", "reselection"])("%s after native acknowledgment revokes that request before settings persistence", async (action) => {
    const h = harness()
    h.toggle(h.key, true)
    await h.launch()
    h.bindings.saveSettingsImmediate.mockImplementation(() => new Promise<void>(() => {}))
    h.toggle(action === "reselection" ? h.other : h.key, action === "reselection")
    const revoke = h.bindings.StartModule.revokeDiagnosticLaunch
    expect(revoke.mock.calls).toEqual([["token"]])
    expect(revoke.mock.invocationCallOrder[0]).toBeLessThan(h.bindings.saveSettingsImmediate.mock.invocationCallOrder.at(-1)!)
    expect(h.start).toHaveBeenCalledTimes(1)
})

test.each(["cancellation", "reselection"])("a restored matching snapshot cannot reauthorize diagnostic A after %s", async (action) => {
    const h = harness()
    h.toggle(h.key, true)
    const snapshotA = JSON.parse(JSON.stringify(h.bsc.settings))
    await h.launch()
    h.toggle(action === "reselection" ? h.other : h.key, action === "reselection")
    h.bsc.settings = snapshotA
    await expect(h.launch()).rejects.toThrow()
    expect(h.bindings.StartModule.revokeDiagnosticLaunch.mock.calls).toEqual([["token"]])
    expect(h.start).toHaveBeenCalledTimes(1)
})

test("selecting a diagnostic after an acknowledged normal Start revokes the normal request", async () => {
    const h = harness()
    await h.launch(true)
    h.toggle(h.key, true)
    expect(h.bindings.StartModule.revokeDiagnosticLaunch.mock.calls).toEqual([["token"]])
})

test("revocation is sent once per acknowledged request and never before acknowledgment", async () => {
    const h = harness()
    h.toggle(h.key, true)
    h.toggle(h.other, true)
    expect(h.bindings.StartModule.revokeDiagnosticLaunch).not.toHaveBeenCalled()
    await h.launch()
    h.toggle(h.key, true)
    h.toggle(h.key, false)
    expect(h.bindings.StartModule.revokeDiagnosticLaunch.mock.calls).toEqual([["token"]])
})

test("an acknowledgment that arrives after the choice changed is never recorded for revocation", async () => {
    const h = harness()
    h.toggle(h.key, true)
    h.bindings.StartModule.setVerifiedLaunchIdentity = jest.fn(async () => { requestDiagnostic(h.other); return "stale" })
    await callback("src/pages/Home/index.tsx", "runStartSequence", h.bindings)()
    h.toggle(h.key, false)
    expect(h.bindings.StartModule.stop).toHaveBeenCalledTimes(1)
    expect(h.bindings.StartModule.revokeDiagnosticLaunch).not.toHaveBeenCalled()
})

test("after revocation only a fresh explicit launch acknowledges again, and that token is revoked next", async () => {
    const h = harness()
    h.toggle(h.key, true)
    await h.launch()
    h.toggle(h.key, false)
    h.bindings.StartModule.setVerifiedLaunchIdentity.mockImplementation(async () => "fresh")
    h.toggle(h.key, true)
    await h.launch()
    expect(h.start.mock.calls).toEqual([["token"], ["fresh"]])
    h.toggle(h.other, true)
    expect(h.bindings.StartModule.revokeDiagnosticLaunch.mock.calls).toEqual([["token"], ["fresh"]])
})

test.each([
    ["a failed revocation bridge stops projection so the request cannot stay executable", async () => { throw new Error("bridge failed") }, 1],
    ["a request the overlay already consumed keeps its running session", async () => false, 0],
])("%s", async (_name, revoke, stops) => {
    const h = harness()
    h.toggle(h.key, true)
    await h.launch()
    h.bindings.StartModule.revokeDiagnosticLaunch.mockImplementation(revoke)
    h.toggle(h.key, false)
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(h.bindings.StartModule.stop).toHaveBeenCalledTimes(stops)
})

test("overlapping attempts can acknowledge only once", async () => {
    const h = harness()
    h.toggle(h.key, true)
    await Promise.all([h.launch(), h.launch()])
    expect(h.start).toHaveBeenCalledTimes(1)
})

test("reselecting a diagnostic during normal persistence prevents rotation preparation", async () => {
    const h = harness()
    h.bindings.flushAndVerifyLaunchConfig = async () => {
        h.toggle(h.key, true)
        return { ok: true, persisted: { revision: 1, hash: "verified" } }
    }
    await callback("src/pages/Home/index.tsx", "runStartSequence", h.bindings)(true)
    expect(h.start).not.toHaveBeenCalled()
    expect(h.bindings.prepareTraineeRotation).not.toHaveBeenCalled()
})

function rotationManager(h: ReturnType<typeof harness>, afterClear = () => {}, afterWrite = () => {}) {
    const clear = jest.fn(async () => { afterClear() })
    const write = jest.fn(async (_rows: unknown[]) => { afterWrite() })
    const prepare = callback("src/hooks/useSettingsManager.tsx", "prepareTraineeRotation", {
        settingsRef: { get current() { return h.bsc.settings } },
        databaseManager: { clearRotationSnapshots: clear, saveSettingsBatch: write },
        buildRotationSnapshotRows,
        logWithTimestamp: jest.fn(), logErrorWithTimestamp: jest.fn(),
    })
    h.bindings.prepareTraineeRotation = jest.fn(prepare)
    return { clear, write, prepare }
}

function persistenceBarrier(h: ReturnType<typeof harness>, afterRead = () => {}, database: Record<string, unknown> = {}) {
    let rows: Record<string, unknown> = {}
    return callback("src/hooks/useSettingsManager.tsx", "flushAndVerifyLaunchConfig", {
        useCallback: (fn: unknown) => fn, startTiming: () => jest.fn(), settingsRef: { get current() { return h.bsc.settings } },
        launchConfigIdentity, verifyLaunchConfigPersisted, identityFromRows, convertSettingsToBatch,
        autoSaveTimerRef: { current: null }, LAUNCH_FLUSH_TIMEOUT_MS: 1000, setTimeout, clearTimeout,
        databaseManager: {
            initialize: async () => {},
            saveSettingsBatch: async (batch: { category: string; key: string; value: unknown }[]) => {
                rows = Object.fromEntries(batch.map((row) => [`${row.category}.${row.key}`, row.value]))
            },
            loadSettingsRowsSnapshot: async () => { afterRead(); return rows },
            failStalledWriter: jest.fn(),
            ...database,
        },
    })
}

function enableRotation(h: ReturnType<typeof harness>) {
    const preset = characterPresets[0]
    h.bsc.settings.runQueue.enableTraineeRotation = true
    h.bsc.settings.runQueue.traineeRotation = [{ inGameName: preset.name, presetKey: preset.name, scenario: preset.scenario }]
}

test.each([false, true])("unchanged normal intent runs the actual persistence and rotation seams (rotation %s)", async (enabled) => {
    const h = harness()
    if (enabled) enableRotation(h)
    const original = JSON.stringify(h.bsc.settings)
    const rotation = rotationManager(h)
    h.bindings.flushAndVerifyLaunchConfig = persistenceBarrier(h)
    await callback("src/pages/Home/index.tsx", "runStartSequence", h.bindings)(true)
    expect(rotation.clear).toHaveBeenCalledTimes(1)
    expect(rotation.write).toHaveBeenCalledTimes(enabled ? 1 : 0)
    if (enabled) expect(rotation.write.mock.calls[0][0].length).toBeGreaterThan(0)
    expect(h.start).toHaveBeenCalledTimes(1)
    expect(JSON.stringify(h.bsc.settings)).toBe(original)
})

test.each([
    [
        "a failed write",
        {
            saveSettingsBatch: async () => {
                throw new Error("disk I/O")
            },
        },
    ],
    ["a stale read-back", { saveSettingsBatch: async () => {}, loadSettingsRowsSnapshot: async () => ({}) }],
])("the real persistence barrier blocks Home's Start on %s", async (_case, database) => {
    const h = harness()
    const rotation = rotationManager(h)
    h.bindings.flushAndVerifyLaunchConfig = persistenceBarrier(h, () => {}, database)
    await callback("src/pages/Home/index.tsx", "runStartSequence", h.bindings)(true)
    expect(h.start).not.toHaveBeenCalled()
    expect(h.bindings.StartModule.setVerifiedLaunchIdentity).not.toHaveBeenCalled()
    expect(rotation.clear).not.toHaveBeenCalled()
    expect(h.bindings.setPresetSaveState).toHaveBeenLastCalledWith("failed")
    expect(h.bindings.showSnackbar).toHaveBeenCalledWith("Could not confirm your settings were saved, so nothing started. Your preset is kept. Press Start to try again.", "error")
})

test.each(["reselection", "cancellation", "Stop"])("invalidated normal intent has no later effects across awaits: %s", async (action) => {
    for (const stage of ["accessibility", "persistence", "clear", "write", "acknowledgment"]) {
        const h = harness()
        enableRotation(h)
        const invalidate = () => {
            if (action === "reselection") h.toggle(h.key, true)
            else if (action === "cancellation") requestDiagnostic(null)
            else h.bindings.startGate.mayLaunch = () => false
        }
        const at = (point: string) => { if (stage === point) invalidate() }
        const rotation = rotationManager(h, () => at("clear"), () => at("write"))
        h.bindings.StartModule.getAccessibilityStatus = async () => { at("accessibility"); return { enabled: true, active: true } }
        h.bindings.flushAndVerifyLaunchConfig = persistenceBarrier(h, () => at("persistence"))
        h.bindings.StartModule.setVerifiedLaunchIdentity = jest.fn(async () => { at("acknowledgment"); return "token" })
        await callback("src/pages/Home/index.tsx", "runStartSequence", h.bindings)(true)
        expect(rotation.clear).toHaveBeenCalledTimes(["accessibility", "persistence"].includes(stage) ? 0 : 1)
        expect(rotation.write).toHaveBeenCalledTimes(["write", "acknowledgment"].includes(stage) ? 1 : 0)
        expect(h.bindings.StartModule.setVerifiedLaunchIdentity).toHaveBeenCalledTimes(stage === "acknowledgment" ? 1 : 0)
        expect(h.bindings.StartModule.stop).toHaveBeenCalledTimes(stage === "acknowledgment" ? 1 : 0)
        expect(h.start).not.toHaveBeenCalled()
        expect(h.bindings.showSnackbar).not.toHaveBeenCalled()
    }
})

test("the settings manager rejects an already invalid intent before clearing snapshots", async () => {
    const h = harness()
    enableRotation(h)
    const rotation = rotationManager(h)
    expect(await rotation.prepare(() => false)).toBeNull()
    expect(rotation.clear).not.toHaveBeenCalled()
    expect(rotation.write).not.toHaveBeenCalled()
})

describe("a refused Start names its reason", () => {
    test("positive control: a normal Start asks once and reaches the native handoff", async () => {
        const h = harness()
        await h.press()
        expect(h.bindings.Alert.alert).toHaveBeenCalledTimes(1)
        expect(h.start).toHaveBeenCalledTimes(1)
        expect(h.bindings.showSnackbar).not.toHaveBeenCalled()
    })

    test("a used diagnostic selection says it was used", async () => {
        const h = harness()
        h.toggle(h.key, true)
        await h.press()
        await h.press()
        expect(h.start).toHaveBeenCalledTimes(1)
        expect(h.bindings.showSnackbar).toHaveBeenCalledWith("This diagnostic test selection was already used. Select the test again in Debug Settings to run it again, or turn it off there for a normal Start.", "error")
    })

    test("a diagnostic left on from before the app started is refused before the normal Start question", async () => {
        const h = harness()
        h.bsc.settings.debug[h.key] = true
        await h.press()
        expect(h.bindings.Alert.alert).not.toHaveBeenCalled()
        expect(h.start).not.toHaveBeenCalled()
        expect(h.bindings.showSnackbar).toHaveBeenCalledWith("A diagnostic test is turned on but was not selected since the app started. Select it again in Debug Settings to run it, or turn it off there for a normal Start.", "error")
    })

    test("settings that no longer match the selected test say so", async () => {
        const h = harness()
        h.toggle(h.key, true)
        h.bsc.settings.debug[h.other] = true
        await h.press()
        expect(h.start).not.toHaveBeenCalled()
        expect(h.bindings.showSnackbar).toHaveBeenCalledWith("The diagnostic tests turned on in Debug Settings no longer match the one you selected. Select the test again there, or turn it off for a normal Start.", "error")
    })

    test("an unrelated start failure is not reported as a diagnostic problem", async () => {
        const h = harness()
        h.toggle(h.key, true)
        h.bindings.StartModule.setVerifiedLaunchIdentity = jest.fn(async () => { throw new Error("bridge failed") })
        await h.press()
        expect(h.start).not.toHaveBeenCalled()
        expect(h.bindings.showSnackbar.mock.calls).toEqual([["Something went wrong before the bot started, so nothing started. Press Start to try again.", "error"]])
        expect(h.bindings.logErrorWithTimestamp).toHaveBeenCalledWith("[START] Start failed", expect.any(Error))
    })

    test("only the diagnostic check's own refusals carry a reason", () => {
        expect(startRefusal(new Error("Diagnostic settings no longer match the requested test"))).toBeNull()
        expect(startRefusal(Object.assign(new Error("x"), { refusal: "toString" }))).toBeNull()
        expect(startRefusal(null)).toBeNull()
        expect(startRefusal("used")).toBeNull()
    })
})
