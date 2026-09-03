import fs from "fs"
import path from "path"
import searchConfig from "../searchConfig"
import { skillPlanSettingsPages } from "../../pages/SkillPlanSettings/config"

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
