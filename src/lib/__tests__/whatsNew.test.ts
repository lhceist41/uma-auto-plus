import fs from "fs"
import path from "path"
import { RELEASE_NOTES_BASE_URL, releaseNotesUrl, whatsNewEntries } from "../../data/whatsNew"
import { decideWhatsNew, isFreshInstall, whatsNewContent, type WhatsNewInput } from "../whatsNew"

const source = (relative: string) => fs.readFileSync(path.join(__dirname, "..", "..", "..", relative), "utf8").replace(/\r\n/g, "\n")

const EM_DASH = String.fromCharCode(0x2014)

const base: WhatsNewInput = { currentVersion: "1.7.1", lastSeenVersion: null, freshInstall: false, botActive: false, hasEntry: true }

describe("decideWhatsNew: the show rule", () => {
    const cases: [string, Partial<WhatsNewInput>, string][] = [
        ["an update from a version that never stored one (1.7.0 to 1.7.1)", {}, "show"],
        ["an update from a stored earlier version", { lastSeenVersion: "1.7.0" }, "show"],
        ["the version already seen", { lastSeenVersion: "1.7.1" }, "none"],
        ["a fresh install records the version and shows nothing", { freshInstall: true }, "record"],
        ["a fresh install of a version without highlights also records", { freshInstall: true, hasEntry: false }, "record"],
        ["an unknown install kind with nothing stored shows nothing and changes nothing", { freshInstall: null }, "none"],
        ["an unknown install kind does not matter once a version is stored", { freshInstall: null, lastSeenVersion: "1.5.0" }, "show"],
        ["a version without highlights records itself after an update", { hasEntry: false }, "record"],
        ["the bot is active: wait", { botActive: true }, "wait"],
        ["the bot is active on a fresh install: still wait, record nothing yet", { botActive: true, freshInstall: true }, "wait"],
        ["the version could not be read", { currentVersion: null }, "none"],
    ]
    it.each(cases)("%s", (_name, override, expected) => {
        expect(decideWhatsNew({ ...base, ...override })).toBe(expected)
    })
})

describe("isFreshInstall", () => {
    const at = (ms: number) => new Date(1_700_000_000_000 + ms)
    it("is true when the last update is the install", () => {
        expect(isFreshInstall(at(0), at(0))).toBe(true)
        expect(isFreshInstall(at(0), at(30_000))).toBe(true)
    })
    it("is false once the app was updated", () => {
        expect(isFreshInstall(at(0), at(3_600_000))).toBe(false)
    })
    it("is unknown when either time is missing or unreadable", () => {
        expect(isFreshInstall(null, at(0))).toBeNull()
        expect(isFreshInstall(at(0), undefined)).toBeNull()
        expect(isFreshInstall(new Date("nope"), at(0))).toBeNull()
    })
})

describe("whatsNewContent and the highlights data", () => {
    it("a version with no entry shows nothing", () => {
        expect(whatsNewContent("0.0.1")).toBeNull()
        expect(whatsNewContent("constructor")).toBeNull()
        expect(whatsNewContent(null)).toBeNull()
        expect(whatsNewContent("")).toBeNull()
    })

    it("builds the title, the lines and the release notes link for a version", () => {
        const content = whatsNewContent("1.7.1")!
        expect(content.title).toBe("What's new in 1.7.1")
        expect(content.highlights).toEqual(whatsNewEntries["1.7.1"].highlights)
        expect(content.releaseNotesUrl).toBe("https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.7.1")
        expect(releaseNotesUrl("1.7.1")).toBe(`${RELEASE_NOTES_BASE_URL}v1.7.1`)
    })

    it("every entry has 3 to 8 plain one-line highlights, with no em dash or internal label", () => {
        // Task and review labels (a letter and digits, or a name and digits) and the names of the tools behind them.
        const toolNames = [
            ["Main ", "Brain"],
            ["task ", "file"],
            ["road", "map"],
            ["Op", "us"],
            ["Son", "net"],
            ["Fa", "ble"],
            ["Cla", "ude"],
        ].map((parts) => parts.join(""))
        const internalLabel = new RegExp(`\\b(?:[A-Z]\\d{1,2}[a-z]?|[A-Z]{3,}\\d+|Stage \\d|${toolNames.join("|")})\\b`)
        expect(Object.keys(whatsNewEntries).length).toBeGreaterThan(0)
        for (const [version, entry] of Object.entries(whatsNewEntries)) {
            expect(version).toMatch(/^\d+\.\d+\.\d+$/)
            expect(entry.highlights.length).toBeGreaterThanOrEqual(3)
            expect(entry.highlights.length).toBeLessThanOrEqual(8)
            for (const line of entry.highlights) {
                expect(line.trim()).not.toBe("")
                expect(line).not.toContain("\n")
                expect(line).not.toContain(EM_DASH)
                expect(line).not.toMatch(internalLabel)
            }
        }
    })
})

describe("the dialog and its wiring", () => {
    const dialog = source("src/components/WhatsNewDialog/index.tsx")
    const home = source("src/pages/Home/index.tsx")

    it("renders the title and every highlight, with a Release notes button and OK", () => {
        expect(dialog).toContain("content?.title")
        expect(dialog).toContain("(content?.highlights ?? []).map((line)")
        expect(dialog).toContain("Linking.openURL(content.releaseNotesUrl)")
        expect(dialog).toContain("<Text>Release notes</Text>")
        expect(dialog).toContain("<Text>OK</Text>")
        expect(dialog).toContain("open={content !== null}")
    })

    it("labels its controls for screen readers", () => {
        expect(dialog).toContain('accessibilityRole="list"')
        expect(dialog).toContain("accessibilityLabel={line}")
        expect(dialog).toContain('accessibilityLabel="Open the full release notes in your browser"')
        expect(dialog).toContain('accessibilityLabel="Close the what\'s new dialog"')
    })

    it("Home mounts it once, closed while the bot is armed or running, and only decides after the native state is read", () => {
        expect(home.match(/<WhatsNewDialog /g)?.length).toBe(1)
        expect(home).toContain("content={armed || botRunning ? null : whatsNew.content}")
        expect(home).toContain("useWhatsNew(armed || botRunning, sessionKnown)")
        const refresh = home.slice(home.indexOf("const refreshSessionState = useCallback"), home.indexOf("const refreshQueueProgress = useCallback"))
        const dispatch = refresh.indexOf('dispatchSession({ type: "NATIVE_STATE"')
        expect(dispatch).toBeGreaterThan(0)
        expect(refresh.indexOf("setSessionKnown(true)")).toBeGreaterThan(dispatch)
    })

    it("the hook stores the version only from the dismiss and from the fresh-install record", () => {
        const hook = source("src/hooks/useWhatsNew.ts")
        expect(hook.match(/saveWhatsNewSeenVersion\(/g)?.length).toBe(2)
        expect(hook).toContain("if (!sessionKnown || botActive || decided.current) return")
        expect(hook).toContain('decision === "record" && currentVersion')
        expect(hook).toContain("const version = content?.version")
    })
})
