import fs from "fs"
import path from "path"
import { EXPORT_FAILED_MESSAGE, SAVED_MESSAGE, SAVE_FAILED_MESSAGE, saveToPhone, type SaveToPhoneDeps } from "../saveToPhone"
import { saveBugReport, shareBugReport } from "../bugReport"
import { defaultSettings } from "../../context/BotStateContext"

// Import the real defaults without loading the provider's native styling runtime.
jest.mock("react-native-css-interop/jsx-runtime", () => jest.requireActual("react/jsx-runtime"))

const fakes = (save: SaveToPhoneDeps["save"]) => ({ save: jest.fn(save), alert: jest.fn(), log: jest.fn() })

describe("saveToPhone", () => {
    it("copies the file the app wrote under its own name and tells the player it is saved", async () => {
        const deps = fakes(async () => "saved")
        expect(await saveToPhone("file:///data/user/0/app/files/UAA-settings-1.json", "application/json", deps)).toBe("saved")
        expect(deps.save).toHaveBeenCalledWith("/data/user/0/app/files/UAA-settings-1.json", "UAA-settings-1.json", "application/json")
        expect(deps.alert).toHaveBeenCalledWith("Saved", SAVED_MESSAGE)
    })

    it("does nothing visible when the player closes the dialog", async () => {
        const deps = fakes(async () => "cancelled")
        expect(await saveToPhone("file:///files/a.zip", "application/zip", deps)).toBe("cancelled")
        expect(deps.alert).not.toHaveBeenCalled()
        expect(deps.log).not.toHaveBeenCalled()
    })

    it("shows a real alert when the write fails", async () => {
        const deps = fakes(async () => Promise.reject(new Error("disk full")))
        expect(await saveToPhone("file:///files/a.zip", "application/zip", deps)).toBe("failed")
        expect(deps.alert).toHaveBeenCalledWith("Could Not Save File", SAVE_FAILED_MESSAGE)
        expect(deps.log).toHaveBeenCalled()
    })
})

describe("saveBugReport", () => {
    const build = async () => "/data/user/0/app/files/bug-report/uma-auto-plus-bug-report-1.zip"

    it("saves the same zip that Share would send", async () => {
        const shared: string[] = []
        const saved: string[] = []
        await shareBugReport(defaultSettings, new Date(0), {
            build,
            canShare: async () => true,
            share: async (uri) => void shared.push(uri),
            alert: jest.fn(),
            log: jest.fn(),
        })
        const deps = { build, ...fakes(async (filePath) => (saved.push(filePath), "saved" as const)) }
        expect(await saveBugReport(defaultSettings, new Date(0), deps)).toBe("saved")
        expect(`file://${saved[0]}`).toBe(shared[0])
        expect(deps.save).toHaveBeenCalledWith(saved[0], "uma-auto-plus-bug-report-1.zip", "application/zip")
    })

    it("does not open the save dialog when the report cannot be built", async () => {
        const deps = { build: async () => Promise.reject(new Error("no space")), ...fakes(async () => "saved") }
        expect(await saveBugReport(defaultSettings, new Date(0), deps)).toBe("build-failed")
        expect(deps.save).not.toHaveBeenCalled()
        expect(deps.alert).toHaveBeenCalledWith("Could Not Build Report", expect.any(String))
    })

    it("stays quiet when the dialog is cancelled", async () => {
        const deps = { build, ...fakes(async () => "cancelled") }
        expect(await saveBugReport(defaultSettings, new Date(0), deps)).toBe("cancelled")
        expect(deps.alert).not.toHaveBeenCalled()
    })
})

describe("settings save wiring", () => {
    const source = fs.readFileSync(path.join(__dirname, "..", "..", "hooks", "useSettingsFileManager.tsx"), "utf8")

    it("saves the file exportSettings wrote, the same one Share sends", () => {
        const share = source.slice(source.indexOf("const handleExportSettings"), source.indexOf("const handleSaveSettingsToPhone"))
        const save = source.slice(source.indexOf("const handleSaveSettingsToPhone"), source.indexOf("return {", source.indexOf("const handleSaveSettingsToPhone")))
        expect(share).toContain("await exportSettings()")
        expect(save).toContain("await exportSettings()")
        expect(save).toContain('saveToPhone(fileUri, "application/json"')
    })

    it("ignores a second tap while the first save is running, and says an export failure is an export failure", () => {
        const save = source.slice(source.indexOf("const handleSaveSettingsToPhone"), source.indexOf("return {", source.indexOf("const handleSaveSettingsToPhone")))
        expect(save.indexOf("if (savingSettings.current) return")).toBeGreaterThan(-1)
        expect(save.indexOf("savingSettings.current = true")).toBeLessThan(save.indexOf("await exportSettings()"))
        expect(save).toContain("savingSettings.current = false")
        expect(save).toContain("EXPORT_FAILED_MESSAGE")
        expect(EXPORT_FAILED_MESSAGE).not.toBe(SAVE_FAILED_MESSAGE)
        expect(EXPORT_FAILED_MESSAGE).not.toMatch(/folder/i)
    })
})

describe("settings page placement", () => {
    const page = fs.readFileSync(path.join(__dirname, "..", "..", "pages", "Settings", "index.tsx"), "utf8")
    const pageSection = page.slice(page.indexOf("<PageHeader"))

    it("shows the file and bug report buttons first, before every settings link", () => {
        expect(pageSection.indexOf("{renderSettingsManagement()}")).toBeGreaterThan(-1)
        expect(pageSection.indexOf("{renderSettingsManagement()}")).toBeLessThan(pageSection.indexOf("{renderTrainingLink()}"))
    })

    it("keeps one copy of each button, and the search entry still points at the block", () => {
        for (const handler of ["handleImportSettings", "handleExportSettings", "handleSaveSettingsToPhone", "shareBugReport", "saveBugReportToPhone"]) {
            expect(page.split(`onPress={${handler}}`).length - 1).toBe(1)
        }
        expect(page).toContain('searchId="settings-management-title"')
        expect(page.split('searchId="settings-management-title"').length - 1).toBe(1)
    })
})
