import fs from "fs"
import path from "path"
import searchConfig from "../searchConfig"
import { characterPresets } from "../characterPresets"
import { defaultSettings } from "../../context/BotStateContext"
import { skillPlanSettingsPages } from "../../pages/SkillPlanSettings/config"

// Import the real defaults without loading the provider's native styling runtime.
jest.mock("react-native-css-interop/jsx-runtime", () => jest.requireActual("react/jsx-runtime"))

const PAGES_DIR = path.join(__dirname, "..", "..", "pages")

/**
 * Repo-specific registries used to expand a known template-literal searchId's interpolated
 * variable into its full set of concrete runtime values. Keyed by the variable name as written
 * in the template (e.g. `enable-skill-plan-${planKey}` looks up "planKey" here). Add an entry
 * whenever a new dynamic searchId family is introduced with a different backing variable - an
 * unrecognized variable name fails the coverage test loudly instead of being silently skipped.
 */
const TEMPLATE_VARIABLE_DOMAINS: Record<string, string[]> = {
    name: Object.values(skillPlanSettingsPages).map((p) => p.name),
    planKey: Object.values(skillPlanSettingsPages).map((p) => p.planKey),
}

/** Recursively collects every .tsx file under a directory, cross-platform (no shell globbing). */
function collectTsxFiles(dir: string): string[] {
    return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
        const full = path.join(dir, entry.name)
        if (entry.isDirectory()) return collectTsxFiles(full)
        return entry.isFile() && entry.name.endsWith(".tsx") ? [full] : []
    })
}

interface TemplateId {
    /** The static text before the interpolation, e.g. "enable-skill-plan-". */
    prefix: string
    /** The interpolated variable name, e.g. "planKey". */
    variable: string
}

/**
 * Extracts every searchable id a page file declares, covering the three ways a control wires
 * into the search registry:
 *   1. `searchId="literal"` / `searchId='literal'` on CustomCheckbox/CustomSelect/CustomSlider/CustomTitle.
 *   2. `<SearchableItem id="literal" ...>` wrapping custom content directly.
 *   3. A template-literal id (`searchId={\`prefix-${var}\`}` or `id={\`prefix-${var}\`}`), which resolves
 *      to one of several concrete ids at runtime (e.g. one per SkillPlanSettings page). Both the static
 *      prefix and the interpolated variable name are recovered, so the concrete ids can be expanded via
 *      `TEMPLATE_VARIABLE_DOMAINS` and checked exactly instead of by prefix alone.
 *
 * A bare dynamic id (`id={someVariable}`, no literal or template text at all) cannot be resolved by
 * source scanning and is intentionally skipped rather than guessed at.
 */
function extractSearchableIds(source: string): { literal: string[]; templates: TemplateId[] } {
    const literal: string[] = []
    const templates: TemplateId[] = []

    for (const m of source.matchAll(/searchId=(["'])([^"']+)\1/g)) literal.push(m[2])
    for (const m of source.matchAll(/searchId=\{`([^$`]*)\$\{(\w+)\}`\}/g)) templates.push({ prefix: m[1], variable: m[2] })

    // `(?<!=)>` stops at the tag's real closing bracket rather than an `=>` inside an attribute
    // value (e.g. `onCheckedChange={(checked) => ...}`), so multi-line attribute lists are safe.
    for (const tagMatch of source.matchAll(/<SearchableItem\b([\s\S]*?)(?<!=)>/g)) {
        const attrs = tagMatch[1]
        const idLiteral = attrs.match(/\bid=(["'])([^"']+)\1/)
        if (idLiteral) {
            literal.push(idLiteral[2])
            continue
        }
        const idTemplate = attrs.match(/\bid=\{`([^$`]*)\$\{(\w+)\}`\}/)
        if (idTemplate) templates.push({ prefix: idTemplate[1], variable: idTemplate[2] })
        // else: bare dynamic id, e.g. `id={id}` - not statically resolvable, skipped.
    }

    return { literal, templates }
}

/**
 * Searchable ids that are neither a literal nor a template literal in source: TrainingSettings renders
 * three stat selectors through a shared helper that forwards a plain `id` variable into
 * `<SearchableItem id={id}>`. Pinned here because source scanning cannot resolve a bare dynamic id.
 */
const BARE_DYNAMIC_IDS = ["training-blacklist", "training-prioritization", "focus-on-sparks"]

describe("searchId registration coverage", () => {
    const pageFiles = collectTsxFiles(PAGES_DIR)
    const configIds = new Set(searchConfig.map((item) => item.id))

    it("every literal searchId in a page has a matching searchConfig entry", () => {
        const missing: string[] = []
        for (const file of pageFiles) {
            const source = fs.readFileSync(file, "utf8")
            const { literal } = extractSearchableIds(source)
            for (const id of literal) {
                if (!configIds.has(id)) {
                    missing.push(`${id} (${path.relative(PAGES_DIR, file)})`)
                }
            }
        }
        expect(missing).toEqual([])
    })

    it("every template-literal searchId resolves to a registered entry for every concrete variant", () => {
        // Expands each template to its full set of concrete ids via TEMPLATE_VARIABLE_DOMAINS and checks
        // each one exactly, so deleting a single concrete registration (e.g. removing one SkillPlanSettings
        // page from searchConfig while it's still rendered) fails here by name, not just a prefix match.
        const missing: string[] = []
        for (const file of pageFiles) {
            const source = fs.readFileSync(file, "utf8")
            const { templates } = extractSearchableIds(source)
            for (const { prefix, variable } of templates) {
                const domain = TEMPLATE_VARIABLE_DOMAINS[variable]
                if (!domain) {
                    missing.push(`${prefix}\${${variable}} (${path.relative(PAGES_DIR, file)}): unrecognized template variable - add it to TEMPLATE_VARIABLE_DOMAINS`)
                    continue
                }
                for (const value of domain) {
                    const concreteId = `${prefix}${value}`
                    if (!configIds.has(concreteId)) {
                        missing.push(`${concreteId} (${path.relative(PAGES_DIR, file)})`)
                    }
                }
            }
        }
        expect(missing).toEqual([])
    })

    it("training's dynamic stat-selector ids resolve to registered entries", () => {
        // TrainingSettings renders three stat selectors through a shared helper that forwards a
        // plain `id` variable into `<SearchableItem id={id}>` - a bare dynamic reference the
        // generic scanner above cannot resolve. Pinned here instead, since it is the one place in
        // the app where a searchable id is neither a literal nor a template literal.
        for (const id of BARE_DYNAMIC_IDS) {
            expect(configIds.has(id)).toBe(true)
        }
    })

    it("every searchConfig entry is declared by a rendered control", () => {
        // The reverse direction of the checks above. Without it a control can be deleted or renamed
        // while its searchConfig entry survives, leaving in-app search offering a result that
        // navigates to a control that no longer exists.
        const renderedIds = new Set(BARE_DYNAMIC_IDS)
        for (const file of pageFiles) {
            const { literal, templates } = extractSearchableIds(fs.readFileSync(file, "utf8"))
            for (const id of literal) renderedIds.add(id)
            for (const { prefix, variable } of templates) {
                for (const value of TEMPLATE_VARIABLE_DOMAINS[variable] ?? []) renderedIds.add(`${prefix}${value}`)
            }
        }

        const stale = searchConfig.filter((item) => !renderedIds.has(item.id)).map((item) => `${item.id} (page: ${item.page})`)
        expect(stale).toEqual([])
    })
})

describe("searchConfig validation", () => {
    it("has no duplicate IDs", () => {
        const ids = searchConfig.map((item) => item.id)
        const uniqueIds = new Set(ids)
        const duplicates = ids.filter((id, index) => ids.indexOf(id) !== index)
        expect(duplicates).toEqual([])
        expect(uniqueIds.size).toBe(ids.length)
    })

    it("all parentId references point to an existing id", () => {
        const allIds = new Set(searchConfig.map((item) => item.id))
        const orphanedParents: string[] = []

        for (const item of searchConfig) {
            if (item.parentId && !allIds.has(item.parentId)) {
                orphanedParents.push(`${item.id} references parentId="${item.parentId}" which does not exist`)
            }
        }

        expect(orphanedParents).toEqual([])
    })

    it("every entry has a non-empty id", () => {
        for (const item of searchConfig) {
            expect(item.id).toBeTruthy()
        }
    })

    it("every entry has a non-empty title", () => {
        for (const item of searchConfig) {
            expect(item.title).toBeTruthy()
        }
    })

    it("every entry has a non-empty page", () => {
        for (const item of searchConfig) {
            expect(item.page).toBeTruthy()
        }
    })

    it("config contains at least 50 entries", () => {
        // Sanity check: the config is substantial
        expect(searchConfig.length).toBeGreaterThanOrEqual(50)
    })
})

describe("Remote Log Viewer copy", () => {
    const page = fs.readFileSync(path.join(PAGES_DIR, "DebugSettings", "index.tsx"), "utf8")
    const searchText = searchConfig.find((item) => item.id === "settings-enable-remote-log-viewer")?.description ?? ""

    it("says the viewer is reached on the device or over ADB, never over Wi-Fi", () => {
        // The server binds to loopback only, so a same-network browser cannot reach it.
        for (const text of [searchText, page]) {
            expect(text).not.toMatch(/wi-?fi|local network/i)
        }
        expect(searchText).toMatch(/adb forward/)
        expect(searchText).toMatch(/localhost/)
    })

    it("shows the access code from the running viewer and keeps it out of settings and storage", () => {
        expect(page).toContain("NativeModules.StartModule.getRemoteLogViewerAccessCode()")
        expect(page).not.toMatch(/served with no authentication/)
        const writes = page.split("\n").filter((line) => /setSettings|saveSettings|Storage|writeAsString/.test(line))
        expect(writes.filter((line) => /AccessCode/.test(line))).toEqual([])
    })
})

describe("settings slider ranges", () => {
    /** Every slider whose placeholder is a `defaultSettings.<category>.<key>` value and whose min and max are literals. */
    const sliders = collectTsxFiles(PAGES_DIR).flatMap((file) =>
        [...fs.readFileSync(file, "utf8").matchAll(/<CustomSlider\b([\s\S]*?)\/>/g)].flatMap((m) => {
            const key = m[1].match(/placeholder=\{(?:bsc\.)?defaultSettings\.(\w+)\.(\w+)\}/)
            const min = m[1].match(/\bmin=\{(-?[\d.]+)\}/)
            const max = m[1].match(/\bmax=\{(-?[\d.]+)\}/)
            return key && min && max ? [{ file: path.basename(path.dirname(file)), category: key[1], key: key[2], min: Number(min[1]), max: Number(max[1]) }] : []
        })
    )

    it("finds the sliders it checks", () => {
        expect(sliders.length).toBeGreaterThanOrEqual(40)
        expect(sliders.some((s) => s.key === "trackblazerConsecutiveRacesLimit")).toBe(true)
    })

    it("holds every default, so touching a slider never moves a setting off its default by itself", () => {
        const outside = sliders.filter((s) => {
            const value = (defaultSettings as any)[s.category]?.[s.key]
            return typeof value === "number" && (value < s.min || value > s.max)
        })
        expect(outside.map((s) => `${s.file} ${s.category}.${s.key}`)).toEqual([])
    })

    it("holds every value a preset ships", () => {
        const outside = sliders.flatMap((s) =>
            characterPresets
                .filter((p) => {
                    const value = (p.settings as any)[s.category]?.[s.key]
                    return typeof value === "number" && (value < s.min || value > s.max)
                })
                .map((p) => `${p.name}|${p.scenario} ${s.category}.${s.key}`)
        )
        expect(outside).toEqual([])
    })
})

describe("Complete Career on Failure", () => {
    const page = fs.readFileSync(path.join(PAGES_DIR, "RacingSettings", "index.tsx"), "utf8")
    const checkbox = page.match(/<CustomCheckbox\s+searchId="enable-complete-career-on-failure"[\s\S]*?\/>/)![0]
    const entry = searchConfig.find((e) => e.id === "enable-complete-career-on-failure")!

    it("sits under Disable Race Retries, the only case where the bot reads it", () => {
        expect(checkbox).toMatch(/searchCondition=\{disableRaceRetries\}/)
        expect(checkbox).toMatch(/parentId="disable-race-retries"/)
        expect(entry.parentId).toBe("disable-race-retries")
    })

    it("says it only applies with race retries disabled, on the page and in search", () => {
        for (const text of [checkbox, entry.description]) {
            expect(text).toMatch(/Disable Race Retries is on/)
            expect(text).not.toMatch(/run out of retries/)
        }
    })
})
