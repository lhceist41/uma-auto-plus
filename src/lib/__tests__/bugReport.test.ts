import { defaultSettings, type Settings } from "../../context/BotStateContext"
import {
    BUG_REPORT_BUILD_FAILED,
    BUG_REPORT_SHARE_FAILED,
    SECRET_KEY_PATTERN,
    bugReportContextLines,
    collectSecretValues,
    isBugReportBusy,
    onBugReportBusy,
    runOneBugReport,
    scrubSettingsForReport,
    shareBugReport,
    type BugReportShareDeps,
} from "../bugReport"

// Import the real defaults without loading the provider's native styling runtime.
jest.mock("react-native-css-interop/jsx-runtime", () => jest.requireActual("react/jsx-runtime"))

const TOKEN = "MTIzNDU2Nzg5.planted.token-value"
const USER_ID = "998877665544332211"
const PAIRING = "pair-4821"

const planted = (): Settings => ({
    ...defaultSettings,
    discord: { ...defaultSettings.discord, discordToken: TOKEN, discordUserID: USER_ID },
    debug: { ...defaultSettings.debug, hostInputPairingCode: PAIRING },
})

/** Every key path in the tree, to find secrets wherever they sit. */
const keyPaths = (value: unknown, prefix = ""): string[] => {
    if (typeof value !== "object" || value === null || Array.isArray(value)) return []
    return Object.entries(value).flatMap(([key, inner]) => [`${prefix}${key}`, ...keyPaths(inner, `${prefix}${key}.`)])
}

describe("scrubSettingsForReport", () => {
    it("removes every secret and never lets a planted value through", () => {
        const text = JSON.stringify(scrubSettingsForReport(planted()))
        for (const value of [TOKEN, USER_ID, PAIRING]) expect(text).not.toContain(value)
        const scrubbed = scrubSettingsForReport(planted()) as any
        expect(scrubbed.discord.discordToken).toBeUndefined()
        expect(scrubbed.discord.discordUserID).toBeUndefined()
        expect(scrubbed.debug.hostInputPairingCode).toBeUndefined()
    })

    it("removes a credential that is added to the settings later, by its name", () => {
        const settings = { ...planted(), discord: { ...planted().discord, webhookUrl: "https://example.test/hook-9d8c7b" } } as unknown as Settings
        expect(JSON.stringify(scrubSettingsForReport(settings))).not.toContain("hook-9d8c7b")
    })

    it("removes secrets in nested lists and objects", () => {
        const settings = { ...planted(), misc: { ...planted().misc, extra: [{ apiKey: "deep-key-1234", fine: 1 }] } } as unknown as Settings
        const text = JSON.stringify(scrubSettingsForReport(settings))
        expect(text).not.toContain("deep-key-1234")
        expect(text).toContain('"fine":1')
    })

    it("removes exactly the secret keys the default settings have, and nothing else useful", () => {
        const secretPaths = keyPaths(defaultSettings).filter((path) => SECRET_KEY_PATTERN.test(path.split(".").pop() as string))
        expect(secretPaths.sort()).toEqual(["debug.hostInputPairingCode", "discord.discordToken", "discord.discordUserID"])
    })

    it("keeps the settings that explain a stop and drops the bulky tables", () => {
        const scrubbed = scrubSettingsForReport(planted()) as any
        expect(scrubbed.general.scenario).toBe(defaultSettings.general.scenario)
        expect(scrubbed.runQueue.totalRuns).toBe(defaultSettings.runQueue.totalRuns)
        expect(scrubbed.racing.racingPlanData).toBeUndefined()
        expect(scrubbed.trainingEvent.characterEventData).toBeUndefined()
        expect(scrubbed.trainingEvent.supportEventData).toBeUndefined()
        expect(scrubbed.trainingEvent.scenarioEventData).toBeUndefined()
        expect(scrubbed.misc.formattedSettingsString).toBeUndefined()
    })

    it("does not change the live settings", () => {
        const settings = planted()
        scrubSettingsForReport(settings)
        expect(settings.discord.discordToken).toBe(TOKEN)
    })
})

describe("collectSecretValues", () => {
    it("returns the planted secret values, so the logs can be scrubbed of them too", () => {
        expect(collectSecretValues(planted()).sort()).toEqual([PAIRING, TOKEN, USER_ID].sort())
    })

    it("collects a numeric user id from an imported file for the value scrub", () => {
        const imported = { ...defaultSettings, discord: { ...defaultSettings.discord, discordUserID: 998877665544 } } as unknown as Settings
        expect(collectSecretValues(imported)).toEqual(["998877665544"])
    })

    it("skips empty and very short values that would blank out ordinary log text", () => {
        expect(collectSecretValues(defaultSettings)).toEqual([])
        const short = { ...defaultSettings, discord: { ...defaultSettings.discord, discordToken: "ab" } }
        expect(collectSecretValues(short)).toEqual([])
    })
})

describe("bugReportContextLines", () => {
    it("names the time, scenario, queue and rotation", () => {
        const settings: Settings = {
            ...defaultSettings,
            general: { ...defaultSettings.general, scenario: "Unity Cup" },
            runQueue: {
                ...defaultSettings.runQueue,
                enableRunQueue: true,
                totalRuns: 4,
                traineeRotation: [{ inGameName: "[Outfit] Special Week", presetKey: "Special Week", scenario: "Unity Cup" }],
            },
        }
        expect(bugReportContextLines(settings, new Date("2026-10-10T01:02:03Z"))).toEqual([
            "Time: 2026-10-10T01:02:03.000Z",
            "Scenario: Unity Cup",
            "Run queue: on, 4 runs",
            "Rotation 1: [Outfit] Special Week / Special Week (Unity Cup)",
        ])
    })

    it("says so when the run queue is off", () => {
        const lines = bugReportContextLines({ ...defaultSettings, runQueue: { ...defaultSettings.runQueue, enableRunQueue: false } }, new Date(0))
        expect(lines).toContain("Run queue: off")
    })
})

describe("shareBugReport", () => {
    const fakes = (over: Partial<BugReportShareDeps> = {}) => {
        const calls: string[] = []
        const deps: BugReportShareDeps = {
            build: jest.fn(async () => "/data/user/0/app/files/bug-report/report.zip"),
            canShare: jest.fn(async () => true),
            share: jest.fn(async (uri: string) => void calls.push(uri)),
            alert: jest.fn(),
            log: jest.fn(),
            ...over,
        }
        return { deps, calls }
    }

    it("builds from the scrubbed settings and hands the zip to the share sheet", async () => {
        const { deps, calls } = fakes()
        expect(await shareBugReport(planted(), new Date(0), deps)).toBe("shared")
        const [settingsJson, contextLines, secrets] = (deps.build as jest.Mock).mock.calls[0]
        for (const value of [TOKEN, USER_ID, PAIRING]) expect(settingsJson).not.toContain(value)
        expect(contextLines).toContain("Scenario:")
        expect([...secrets].sort()).toEqual([PAIRING, TOKEN, USER_ID].sort())
        expect(calls).toEqual(["file:///data/user/0/app/files/bug-report/report.zip"])
        expect(deps.alert).not.toHaveBeenCalled()
    })

    it("says the report could not be built, and shares nothing, when building fails", async () => {
        const { deps } = fakes({ build: jest.fn(async () => Promise.reject(new Error("no space"))) })
        expect(await shareBugReport(planted(), new Date(0), deps)).toBe("build-failed")
        expect(deps.alert).toHaveBeenCalledWith("Could Not Build Report", BUG_REPORT_BUILD_FAILED)
        expect(deps.share).not.toHaveBeenCalled()
        expect(BUG_REPORT_BUILD_FAILED).not.toMatch(/logs folder/i)
    })

    it("says the report was built but not shared when the share sheet fails or is missing", async () => {
        const failing = fakes({ share: jest.fn(async () => Promise.reject(new Error("denied"))) })
        expect(await shareBugReport(planted(), new Date(0), failing.deps)).toBe("share-failed")
        expect(failing.deps.alert).toHaveBeenCalledWith("Could Not Share Report", BUG_REPORT_SHARE_FAILED)
        const missing = fakes({ canShare: jest.fn(async () => false) })
        expect(await shareBugReport(planted(), new Date(0), missing.deps)).toBe("share-failed")
        expect(missing.deps.share).not.toHaveBeenCalled()
    })
})

describe("runOneBugReport", () => {
    it("lets only one report run at a time across screens, and tells every screen", async () => {
        const seen: boolean[] = []
        const stop = onBugReportBusy((busy) => seen.push(busy))
        let release: () => void = () => {}
        const first = runOneBugReport(() => new Promise<void>((resolve) => (release = resolve)))
        expect(isBugReportBusy()).toBe(true)
        const second = jest.fn(async () => {})
        await runOneBugReport(second)
        expect(second).not.toHaveBeenCalled()
        release()
        await first
        expect(isBugReportBusy()).toBe(false)
        expect(seen).toEqual([true, false])
        stop()
    })

    it("frees the slot when the action fails", async () => {
        await expect(runOneBugReport(async () => Promise.reject(new Error("boom")))).rejects.toThrow("boom")
        expect(isBugReportBusy()).toBe(false)
        const again = jest.fn(async () => {})
        await runOneBugReport(again)
        expect(again).toHaveBeenCalled()
    })
})
