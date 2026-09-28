const mockFiles: Record<string, string> = {}
const PREFS = "file:///docs/uiPrefs.json"

jest.mock("expo-file-system", () => ({
    documentDirectory: "file:///docs/",
    getInfoAsync: jest.fn(async (uri: string) => ({ exists: uri in mockFiles })),
    readAsStringAsync: jest.fn(async (uri: string) => mockFiles[uri]),
    writeAsStringAsync: jest.fn(async (uri: string, contents: string) => {
        mockFiles[uri] = contents
    }),
}))

const freshModule = async () => {
    jest.resetModules()
    return await import("../uiPrefs")
}

const flush = () => new Promise((resolve) => setTimeout(resolve, 0))

beforeEach(() => {
    for (const key of Object.keys(mockFiles)) delete mockFiles[key]
})

describe("the What's new version in the UI preferences file", () => {
    it("is null when nothing is stored, and survives a restart once saved", async () => {
        const first = await freshModule()
        expect(await first.loadWhatsNewSeenVersion()).toBeNull()
        await first.saveWhatsNewSeenVersion("1.6.0")
        expect(JSON.parse(mockFiles[PREFS]).whatsNewSeenVersion).toBe("1.6.0")

        const second = await freshModule()
        expect(await second.loadWhatsNewSeenVersion()).toBe("1.6.0")
    })

    it("keeps the starred presets when the version is saved, and the version when a preset is starred", async () => {
        mockFiles[PREFS] = JSON.stringify({ favoritePresets: ["Special Week"] })
        const prefs = await freshModule()
        await prefs.saveWhatsNewSeenVersion("1.6.0")
        expect(JSON.parse(mockFiles[PREFS])).toEqual({ favoritePresets: ["Special Week"], whatsNewSeenVersion: "1.6.0" })

        const again = await freshModule()
        await again.loadWhatsNewSeenVersion()
        again.toggleFavoritePreset("Tokai Teio")
        await flush()
        expect(JSON.parse(mockFiles[PREFS])).toEqual({ favoritePresets: ["Special Week", "Tokai Teio"], whatsNewSeenVersion: "1.6.0" })
    })

    it("a star toggled before the file is read cannot erase the stored version", async () => {
        mockFiles[PREFS] = JSON.stringify({ favoritePresets: [], whatsNewSeenVersion: "1.6.0" })
        const prefs = await freshModule()
        prefs.toggleFavoritePreset("Special Week")
        await flush()
        await flush()
        expect(JSON.parse(mockFiles[PREFS]).whatsNewSeenVersion).toBe("1.6.0")
    })

    it("falls back to nothing stored when the file is unreadable, and still saves", async () => {
        const warn = jest.spyOn(console, "warn").mockImplementation(() => {})
        mockFiles[PREFS] = "{not json"
        const prefs = await freshModule()
        expect(await prefs.loadWhatsNewSeenVersion()).toBeNull()
        await prefs.saveWhatsNewSeenVersion("1.6.0")
        expect(JSON.parse(mockFiles[PREFS]).whatsNewSeenVersion).toBe("1.6.0")
        warn.mockRestore()
    })

    it("ignores a stored value that is not text", async () => {
        mockFiles[PREFS] = JSON.stringify({ favoritePresets: [], whatsNewSeenVersion: 160 })
        const prefs = await freshModule()
        expect(await prefs.loadWhatsNewSeenVersion()).toBeNull()
    })
})
