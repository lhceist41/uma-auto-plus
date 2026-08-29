import fs from "fs"
import path from "path"

/**
 * The Record Decision Data setting decouples the factual per-turn decision corpus (`decision_trace`
 * + `career_state`) from Debug Mode: it defaults on and records the lightweight machine-readable
 * corpus during normal play without the heavy debug diagnostics.
 *
 * React-Native-free, following the sibling lib suites: no BotStateContext import (it pulls in RN,
 * which Jest cannot parse here), so the five-step settings wiring is asserted against source text.
 * The generic UI/searchConfig coverage lives in searchConfig.test.ts; this pins the pieces specific
 * to this setting - the default value, the exact binding, and the Kotlin read path.
 */

const srcFile = (...parts: string[]) => fs.readFileSync(path.join(__dirname, "..", "..", ...parts), "utf8")
const repoFile = (...parts: string[]) => fs.readFileSync(path.join(__dirname, "..", "..", "..", ...parts), "utf8")

const CATEGORY = "misc"
const KEY = "recordDecisionData"
const SEARCH_ID = "settings-record-decision-data"

describe("Record Decision Data setting", () => {
    const context = srcFile("context", "BotStateContext.tsx")

    it("declares the field on the misc Settings interface", () => {
        // The interface block ends at the first closing brace after `misc: {`.
        const miscInterface = context.slice(context.indexOf("misc: {"))
        expect(miscInterface.slice(0, miscInterface.indexOf("}"))).toContain(`${KEY}: boolean`)
    })

    it("defaults to true", () => {
        // The defaultSettings.misc literal is the second `misc: {` block (the first is the interface).
        const firstMisc = context.indexOf("misc: {")
        const defaultMisc = context.indexOf("misc: {", firstMisc + 1)
        expect(defaultMisc).toBeGreaterThan(firstMisc)
        const block = context.slice(defaultMisc, context.indexOf("}", defaultMisc))
        expect(block).toContain(`${KEY}: true`)
    })

    it("renders a bound control on the main Settings page", () => {
        const ui = srcFile("pages", "Settings", "index.tsx")
        expect(ui).toContain(`searchId="${SEARCH_ID}"`)
        expect(ui).toContain(`checked={bsc.settings.${CATEGORY}.${KEY}}`)
        expect(ui).toContain(`${CATEGORY}: { ...bsc.settings.${CATEGORY}, ${KEY}: checked }`)
    })

    it("is registered in settings search under the main Settings page", () => {
        const config = srcFile("data", "searchConfig.ts")
        const entry = config.slice(config.indexOf(`id: "${SEARCH_ID}"`))
        expect(entry).toContain(`id: "${SEARCH_ID}"`)
        expect(entry.slice(0, 400)).toContain('page: "SettingsMain"')
    })

    it("help text stays honest: local only, no upload implied", () => {
        const ui = srcFile("pages", "Settings", "index.tsx")
        const config = srcFile("data", "searchConfig.ts")
        for (const source of [ui, config]) {
            const idx = source.indexOf(SEARCH_ID)
            const nearby = source.slice(idx, idx + 700).toLowerCase()
            expect(nearby).toContain("device")
            expect(nearby).toContain("uploaded")
        }
    })

    it("is read on the Kotlin side with the same category, key, and default", () => {
        const campaign = repoFile("android", "app", "src", "main", "java", "com", "steve1316", "uma_android_automation", "bot", "Campaign.kt")
        expect(campaign).toContain(`SettingsHelper.getBooleanSetting("${CATEGORY}", "${KEY}", true)`)
    })
})
