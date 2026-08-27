import fs from "fs"
import path from "path"
import searchConfig from "../searchConfig"

const PAGES_DIR = path.join(__dirname, "..", "..", "pages")

/** Recursively collects every .tsx file under a directory, cross-platform (no shell globbing). */
function collectTsxFiles(dir: string): string[] {
    return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
        const full = path.join(dir, entry.name)
        if (entry.isDirectory()) return collectTsxFiles(full)
        return entry.isFile() && entry.name.endsWith(".tsx") ? [full] : []
    })
}

/**
 * Extracts every searchable id a page file declares, covering the three ways a control wires
 * into the search registry:
 *   1. `searchId="literal"` / `searchId='literal'` on CustomCheckbox/CustomSelect/CustomSlider/CustomTitle.
 *   2. `<SearchableItem id="literal" ...>` wrapping custom content directly.
 *   3. A template-literal id (`searchId={\`prefix-${var}\`}` or `id={\`prefix-${var}\`}`), which resolves
 *      to one of several concrete ids at runtime (e.g. one per SkillPlanSettings page). Only the static
 *      prefix can be recovered without evaluating the source, so these are checked as a prefix match
 *      instead of an exact id.
 *
 * A bare dynamic id (`id={someVariable}`, no literal or template text at all) cannot be resolved by
 * source scanning and is intentionally skipped rather than guessed at.
 */
function extractSearchableIds(source: string): { literal: string[]; prefixes: string[] } {
    const literal: string[] = []
    const prefixes: string[] = []

    for (const m of source.matchAll(/searchId=(["'])([^"']+)\1/g)) literal.push(m[2])
    for (const m of source.matchAll(/searchId=\{`([^$`]*)\$\{/g)) prefixes.push(m[1])

    // `(?<!=)>` stops at the tag's real closing bracket rather than an `=>` inside an attribute
    // value (e.g. `onCheckedChange={(checked) => ...}`), so multi-line attribute lists are safe.
    for (const tagMatch of source.matchAll(/<SearchableItem\b([\s\S]*?)(?<!=)>/g)) {
        const attrs = tagMatch[1]
        const idLiteral = attrs.match(/\bid=(["'])([^"']+)\1/)
        if (idLiteral) {
            literal.push(idLiteral[2])
            continue
        }
        const idTemplate = attrs.match(/\bid=\{`([^$`]*)\$\{/)
        if (idTemplate) prefixes.push(idTemplate[1])
        // else: bare dynamic id, e.g. `id={id}` - not statically resolvable, skipped.
    }

    return { literal, prefixes }
}

describe("searchId registration coverage (RH2)", () => {
    const pageFiles = collectTsxFiles(PAGES_DIR)
    const configIds = new Set(searchConfig.map((item) => item.id))

    /**
     * Ids the scanner resolves as a template-literal prefix (e.g. `enable-skill-plan-`) are checked
     * against every id in searchConfig, not just page-local ones, since the prefix alone doesn't say
     * which page registered the concrete variant.
     */
    const allConfigIds = Array.from(configIds)

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

    it("every template-literal searchId prefix in a page has at least one matching searchConfig entry", () => {
        const missing: string[] = []
        for (const file of pageFiles) {
            const source = fs.readFileSync(file, "utf8")
            const { prefixes } = extractSearchableIds(source)
            for (const prefix of prefixes) {
                if (!allConfigIds.some((id) => id.startsWith(prefix))) {
                    missing.push(`${prefix}* (${path.relative(PAGES_DIR, file)})`)
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
        for (const id of ["training-blacklist", "training-prioritization", "focus-on-sparks"]) {
            expect(configIds.has(id)).toBe(true)
        }
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
