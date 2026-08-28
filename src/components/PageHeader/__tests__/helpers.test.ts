import fs from "fs"
import path from "path"
import { NESTED_SETTINGS_PAGES, isNestedSettingsPage } from "../helpers"

describe("isNestedSettingsPage", () => {
    it("recognizes every literal nested settings page", () => {
        for (const page of NESTED_SETTINGS_PAGES) {
            expect(isNestedSettingsPage(page)).toBe(true)
        }
    })

    it("recognizes the dynamic SkillPlanSettings family by prefix", () => {
        expect(isNestedSettingsPage("SkillPlanSettingsSkillPointCheck")).toBe(true)
        expect(isNestedSettingsPage("SkillPlanSettingsPreFinals")).toBe(true)
        expect(isNestedSettingsPage("SkillPlanSettingsCareerComplete")).toBe(true)
    })

    it("rejects a top-level Drawer screen", () => {
        expect(isNestedSettingsPage("Home")).toBe(false)
        expect(isNestedSettingsPage("Settings")).toBe(false)
    })
})

describe("NESTED_SETTINGS_PAGES matches the real SettingsStack", () => {
    // Regression guard for the bug this list fixed: a page registered in App.tsx's SettingsStack
    // but missing here makes a search result selected from Home silently no-op instead of
    // navigating anywhere. Scans the actual source rather than trusting a hand-copied list.
    const appSource = fs.readFileSync(path.join(__dirname, "..", "..", "..", "App.tsx"), "utf8")

    // Isolate the SettingsStack function body so an unrelated Stack.Screen elsewhere in App.tsx
    // (there is only one Stack.Navigator, but this keeps the scan scoped to intent) can't leak in.
    const stackMatch = appSource.match(/function SettingsStack\(\)[\s\S]*?\n\}/)
    if (!stackMatch) {
        throw new Error("Could not locate SettingsStack in App.tsx - has it been renamed or restructured?")
    }
    const stackBody = stackMatch[0]

    it("lists every literal Stack.Screen name declared in SettingsStack", () => {
        const missing: string[] = []
        for (const m of stackBody.matchAll(/<Stack\.Screen\s+name="([^"]+)"/g)) {
            const screenName = m[1]
            if (!NESTED_SETTINGS_PAGES.includes(screenName)) {
                missing.push(screenName)
            }
        }
        expect(missing).toEqual([])
    })

    it("does not list a page that SettingsStack no longer declares", () => {
        // The dynamic SkillPlanSettings screens are named via `config.name`, not a literal, and are
        // covered separately by the prefix check - they never appear in NESTED_SETTINGS_PAGES.
        const declared = new Set(Array.from(stackBody.matchAll(/<Stack\.Screen\s+name="([^"]+)"/g)).map((m) => m[1]))
        const stale = NESTED_SETTINGS_PAGES.filter((page) => !declared.has(page))
        expect(stale).toEqual([])
    })
})
