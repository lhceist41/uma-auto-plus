export {}

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

beforeEach(() => {
    for (const key of Object.keys(mockFiles)) delete mockFiles[key]
})

describe("the star ask state in the UI preferences file", () => {
    it("starts with nothing counted and writes nothing by itself", async () => {
        const prefs = await freshModule()
        expect(await prefs.loadStarAsk()).toEqual({ careers: 0, countedSessionId: null, answered: false })
        expect(PREFS in mockFiles).toBe(false)
    })

    it("adds a session's finished careers once and survives a restart", async () => {
        const first = await freshModule()
        await first.recordFinishedCareers("s1", 2)
        await first.recordFinishedCareers("s1", 2)
        expect((await first.loadStarAsk()).careers).toBe(2)
        await first.recordFinishedCareers("s2", 1)

        const second = await freshModule()
        expect(await second.loadStarAsk()).toEqual({ careers: 3, countedSessionId: "s2", answered: false })
    })

    it("does not write for a session that finished nothing", async () => {
        const prefs = await freshModule()
        await prefs.recordFinishedCareers("s1", 0)
        expect(PREFS in mockFiles).toBe(false)
    })

    it("remembers the answer for good and keeps the count", async () => {
        const first = await freshModule()
        await first.recordFinishedCareers("s1", 3)
        await first.saveStarAskAnswered()

        const second = await freshModule()
        expect(await second.loadStarAsk()).toEqual({ careers: 3, countedSessionId: "s1", answered: true })
    })

    it("keeps the starred presets and the What's new version, and leaves the file as before until the ask is used", async () => {
        mockFiles[PREFS] = JSON.stringify({ favoritePresets: ["Special Week"], whatsNewSeenVersion: "1.6.0" })
        const prefs = await freshModule()
        await prefs.loadStarAsk()
        await prefs.saveWhatsNewSeenVersion("1.6.0")
        expect(JSON.parse(mockFiles[PREFS])).toEqual({ favoritePresets: ["Special Week"], whatsNewSeenVersion: "1.6.0" })

        await prefs.recordFinishedCareers("s1", 1)
        expect(JSON.parse(mockFiles[PREFS])).toEqual({
            favoritePresets: ["Special Week"],
            whatsNewSeenVersion: "1.6.0",
            starAsk: { careers: 1, countedSessionId: "s1", answered: false },
        })
    })

    it("falls back to nothing counted for a stored value of the wrong shape", async () => {
        mockFiles[PREFS] = JSON.stringify({ favoritePresets: [], starAsk: { careers: "3", countedSessionId: 7, answered: "yes" } })
        const prefs = await freshModule()
        expect(await prefs.loadStarAsk()).toEqual({ careers: 0, countedSessionId: null, answered: false })
    })
})
