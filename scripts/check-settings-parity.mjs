#!/usr/bin/env node
/**
 * Settings contract parity checker.
 *
 * TypeScript declares the settings contract, Kotlin reads it back out of SQLite by category/key string.
 * That seam fails silently in both directions: a renamed or deleted `Settings` field keeps compiling
 * and simply returns the caller's default forever, and a `Settings` leaf nothing reads any more looks
 * identical to a live one. Neither shows up in tsc, ktlint, or Jest.
 *
 * What is deliberately NOT re-checked here: a `Settings` field required by a typed UI binding is
 * already a compile error, and `defaultSettings: Settings` already forces default completeness.
 *
 * Exit codes:
 *   0  parity holds
 *   1  a real parity violation
 *   2  the checker could not safely understand a source shape (never a silent skip)
 */

import fs from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import ts from "typescript"

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const KOTLIN_ROOT = path.join(REPO_ROOT, "android", "app", "src", "main", "java")
const SETTINGS_SOURCE = path.join(REPO_ROOT, "src", "context", "BotStateContext.tsx")
const TS_CONSUMER_ROOT = path.join(REPO_ROOT, "src")

/** Every `SettingsHelper` read method. An unrecognized method name is a hard stop, not a skip. */
const READ_METHODS = ["getStringSetting", "getBooleanSetting", "getIntSetting", "getStringArraySetting", "getDoubleSetting"]

/** `SettingsHelper` methods confirmed not to read a settings row, so they never need a READ_METHODS entry. */
const NON_READ_METHODS = ["initialize", "isAvailable"]

/**
 * Categories owned entirely by the Kotlin runtime, written straight to the settings table rather than
 * declared in the `Settings` interface. Category-level only, and only where the whole category is
 * demonstrably runtime-owned.
 */
const RUNTIME_CATEGORIES = {
    queueState: "Kotlin-owned queue/rotation runtime state, written by StartModule.setQueueStateValue straight to the settings table. Never a user setting and intentionally absent from the Settings interface.",
}

/** Individual non-Settings rows inside an otherwise user-owned category. Exact paths, never patterns. */
const RUNTIME_SETTING_PATHS = {
    "trainingEvent.characterEventData": "Bundled event data seeded into the settings table by src/hooks/useBootstrap.tsx, not a user setting. Excluded from exports and rotation snapshots.",
    "trainingEvent.supportEventData": "Bundled event data seeded into the settings table by src/hooks/useBootstrap.tsx, not a user setting. Excluded from exports and rotation snapshots.",
    "trainingEvent.scenarioEventData": "Bundled event data seeded into the settings table by src/hooks/useBootstrap.tsx, not a user setting. Excluded from exports and rotation snapshots.",
}

/**
 * `Settings` leaves with no consumer anywhere in this fork. Ballast, not features: every entry has to
 * name why it still exists rather than being deleted. Never a wildcard.
 */
const UNCONSUMED_SETTINGS_ALLOWLIST = {
    "scenarioOverrides.trackblazerPreferredDistances": "Upstream-inherited field with no consumer in this fork; kept as temporary compatibility ballast so upstream diffs stay clean. Tracked for separate cleanup.",
    "scenarioOverrides.trackblazerPreferredSurfaces": "Upstream-inherited field with no consumer in this fork; kept as temporary compatibility ballast so upstream diffs stay clean. Tracked for separate cleanup.",
}

/**
 * Rotation-snapshot category prefixes that are normalized away before parity is checked. A rotation
 * slot stores a queued run's settings under a `rot<N>_` mirror of the live category, over the identical
 * key set, so `rot2_training.moodFloor` is the same contract as `training.moodFloor`. Each entry states
 * the source fact that proves it and is re-verified at scan time, so the normalization cannot outlive
 * the code that justifies it.
 */
const ROTATION_PREFIXES = [
    {
        id: "categoryPrefix-parameter",
        pattern: /^\$\{categoryPrefix\}/,
        reason: "Campaign.buildOutcomeConfigSnapshot reads either the live categories or a rotation slot's stored mirror over the identical key set.",
        proof: { file: "bot/Campaign.kt", contains: 'categoryPrefix: String = ""' },
    },
    {
        id: "rot-index-literal",
        pattern: /^rot\$\{\w+\}_/,
        reason: "`rot${index}_<category>` addresses rotation slot `index`'s stored mirror of the same live category.",
        proof: { file: "bot/Campaign.kt", contains: 'buildOutcomeConfigSnapshot("rot${index}_"' },
    },
]

const violations = []
const blockers = []

const addViolation = (rule, subject, where, reason, hint) => violations.push({ rule, subject, where, reason, hint })
const addBlocker = (where, reason) => blockers.push({ where, reason })

/** Recursively collects files under `dir` whose name ends with `ext`. */
function collectFiles(dir, ext) {
    return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
        const full = path.join(dir, entry.name)
        if (entry.isDirectory()) return collectFiles(full, ext)
        return entry.isFile() && entry.name.endsWith(ext) ? [full] : []
    })
}

const relative = (file) => path.relative(REPO_ROOT, file).replace(/\\/g, "/")

/**
 * Splits a Kotlin argument list starting just past its opening paren, respecting nested parens,
 * braces, brackets, string literals, `${...}` template holes, and escapes. Returns the raw argument
 * texts, or null if the list never closes.
 */
function splitArguments(source, openIndex) {
    const args = []
    let depth = 1
    let current = ""
    let inString = false
    let inChar = false

    for (let i = openIndex + 1; i < source.length; i++) {
        const c = source[i]

        if (inString) {
            current += c
            if (c === "\\") {
                current += source[++i] ?? ""
            } else if (c === '"') {
                inString = false
            } else if (c === "$" && source[i + 1] === "{") {
                // A template hole can contain arbitrary code, including quotes and commas.
                let holeDepth = 0
                do {
                    i++
                    current += source[i]
                    if (source[i] === "{") holeDepth++
                    else if (source[i] === "}") holeDepth--
                } while (i < source.length && holeDepth > 0)
            }
            continue
        }
        if (inChar) {
            current += c
            if (c === "\\") current += source[++i] ?? ""
            else if (c === "'") inChar = false
            continue
        }

        if (c === '"') {
            inString = true
            current += c
            continue
        }
        if (c === "'") {
            inChar = true
            current += c
            continue
        }
        if (c === "(" || c === "{" || c === "[") depth++
        else if (c === ")" || c === "}" || c === "]") {
            depth--
            if (depth === 0) {
                args.push(current)
                return args
            }
        }

        if (c === "," && depth === 1) {
            args.push(current)
            current = ""
            continue
        }
        current += c
    }
    return null
}

const kotlinFiles = collectFiles(KOTLIN_ROOT, ".kt")
const kotlinSources = new Map(kotlinFiles.map((file) => [file, fs.readFileSync(file, "utf8")]))

/** Guards against a new `SettingsHelper.<method>` shape being scanned by a checker that predates it. */
for (const [file, source] of kotlinSources) {
    for (const m of source.matchAll(/SettingsHelper\s*\.\s*(\w+)\s*\(/g)) {
        if (!READ_METHODS.includes(m[1]) && !NON_READ_METHODS.includes(m[1])) {
            const line = source.slice(0, m.index).split("\n").length
            addBlocker(`${relative(file)}:${line}`, `unrecognized SettingsHelper method "${m[1]}" - add it to READ_METHODS after confirming its argument order, or to NON_READ_METHODS if it does not read a setting`)
        }
    }
}

/** Every production settings-read call site, with its raw category and key expressions. */
const sites = []
for (const [file, source] of kotlinSources) {
    const callPattern = new RegExp(`SettingsHelper\\s*\\.\\s*(${READ_METHODS.join("|")})\\s*\\(`, "g")
    for (const m of source.matchAll(callPattern)) {
        const args = splitArguments(source, m.index + m[0].length - 1)
        const line = source.slice(0, m.index).split("\n").length
        if (!args || args.length < 2) {
            addBlocker(`${relative(file)}:${line}`, `could not parse the argument list of SettingsHelper.${m[1]}`)
            continue
        }
        sites.push({ categoryExpr: args[0].trim(), keyExpr: args[1].trim(), file, line, source })
    }
}

/** Parsed once: the canonical debug-test key registry the `DebugTestGate` lambdas iterate. */
const debugTestKeys = (() => {
    const gate = [...kotlinSources].find(([file]) => file.endsWith("DebugTestGate.kt"))
    if (!gate) return null
    const block = gate[1].match(/val ALL_KEYS[\s\S]*?listOf\(([\s\S]*?)\n\s*\)/)
    if (!block) return null
    return [...block[1].matchAll(/"([^"]+)"/g)].map((m) => m[1])
})()

/** Kotlin enum entry names, used to expand generated key templates. */
function enumEntries(name) {
    for (const source of kotlinSources.values()) {
        const block = source.match(new RegExp(`enum class ${name}\\s*\\{([\\s\\S]*?)\\n\\s*;`))
        if (block) return block[1].split(",").map((e) => e.trim()).filter(Boolean)
    }
    return null
}

const ENUM_VALUE_DOMAINS = {
    // Trainee.setStatTargetsByDistances builds `training<Distance>StatTarget_<stat>StatTarget` from the
    // full cross product of these two enums, so the generated keys are exactly enumerable.
    trackDistanceString: () => enumEntries("TrackDistance")?.map((e) => e[0] + e.slice(1).toLowerCase()),
    statNameString: () => enumEntries("StatName")?.map((e) => e.toLowerCase()),
}

/** A quoted Kotlin string with no `$` interpolation, i.e. one whose value is knowable from the token alone. */
const isStringLiteral = (expr) => /^"(?:[^"\\$]|\\.)*"$/.test(expr)

/** Strips a proven rotation-snapshot prefix from a category expression's literal body. */
function normalizeRotationPrefix(body, site) {
    for (const prefix of ROTATION_PREFIXES) {
        if (!prefix.pattern.test(body)) continue
        const proofFile = [...kotlinSources].find(([file]) => relative(file).endsWith(prefix.proof.file))
        if (!proofFile || !proofFile[1].includes(prefix.proof.contains)) {
            addBlocker(`${relative(site.file)}:${site.line}`, `rotation-prefix normalization "${prefix.id}" no longer matches source (expected ${prefix.proof.file} to contain \`${prefix.proof.contains}\`)`)
            return null
        }
        return body.replace(prefix.pattern, "")
    }
    return body
}

/**
 * Resolves a Kotlin string expression to the concrete set of values it can take, or null when the
 * shape is not one the checker provably understands. `null` is always escalated to exit 2 by the
 * caller: an unrecognized access shape must never be treated as probably fine.
 */
function resolveExpr(expr, site, seen = new Set()) {
    if (isStringLiteral(expr)) return [expr.slice(1, -1)]

    // Template literal: expand every `${var}` / `$var` hole against its enumerable domain.
    if (/^"/.test(expr) && expr.endsWith('"')) {
        let body = expr.slice(1, -1)
        if (site.isCategory) {
            body = normalizeRotationPrefix(body, site)
            if (body === null) return null
            if (!body.includes("$")) return [body]
        }
        let results = [""]
        let rest = body
        while (rest.length > 0) {
            const hole = rest.match(/\$\{(\w+)\}|\$(\w+)/)
            if (!hole) {
                results = results.map((r) => r + rest)
                break
            }
            const literalHead = rest.slice(0, hole.index)
            const variable = hole[1] ?? hole[2]
            const domain = ENUM_VALUE_DOMAINS[variable]?.() ?? resolveIdentifier(variable, site, seen)
            if (!domain) return null
            results = results.flatMap((r) => domain.map((v) => r + literalHead + v))
            rest = rest.slice(hole.index + hole[0].length)
        }
        return results
    }

    if (/^\w+$/.test(expr)) return resolveIdentifier(expr, site, seen)
    return null
}

/** Resolves a bare Kotlin identifier used as a category or key to its concrete value domain. */
function resolveIdentifier(name, site, seen) {
    if (seen.has(name)) return null
    seen.add(name)
    const { source, file } = site
    const lines = source.split("\n")
    const before = lines.slice(0, site.line).join("\n")

    // 1. The `DebugTestGate` registry lambdas: the key is the lambda's own parameter, and its domain is
    //    exactly ALL_KEYS. Matched on the call's own line, so the binding is unambiguous.
    const callLine = lines[site.line - 1] ?? ""
    const insideGateLambda = /DebugTestGate\.(requested|anyRequested)\s*\{/.test(callLine)
    if (insideGateLambda && (name === "it" || new RegExp(`\\{\\s*${name}\\s*->`).test(callLine))) {
        if (!debugTestKeys?.length) {
            addBlocker(`${relative(file)}:${site.line}`, "could not parse DebugTestGate.ALL_KEYS, which this dynamic key shape depends on")
            return null
        }
        return debugTestKeys
    }

    // 2. A `for ((name, _) in map)` loop over a local `mapOf("key" to handler, ...)` registry.
    const loop = [...before.matchAll(new RegExp(`for\\s*\\(\\s*\\(\\s*${name}\\s*,[^)]*\\)\\s+in\\s+(\\w+)\\s*\\)`, "g"))].pop()
    if (loop) {
        const mapDecl = [...before.matchAll(new RegExp(`val ${loop[1]}[^=]*=\\s*\\n?\\s*mapOf\\(([\\s\\S]*?)\\n\\s*\\)`, "g"))].pop()
        if (!mapDecl) return null
        return [...mapDecl[1].matchAll(/"([^"]+)"\s+to\s/g)].map((m) => m[1])
    }

    // 3. A local `val name = <string expr>` in the same file, or a named `const val NAME = "..."`
    //    anywhere in production Kotlin (the injected-fault and gate debug keys are declared that way).
    const local = [...before.matchAll(new RegExp(`\\b(?:private\\s+)?(?:const\\s+)?val ${name}(?::\\s*\\w+)?\\s*=\\s*("(?:[^"\\\\]|\\\\.|\\$\\{[^}]*\\})*")`, "g"))].pop()
    if (local) return resolveExpr(local[1], site, seen)

    for (const [, otherSource] of kotlinSources) {
        const constDecl = otherSource.match(new RegExp(`\\b(?:internal\\s+|private\\s+)?const val ${name}\\s*=\\s*("[^"]*")`))
        if (constDecl) return resolveExpr(constDecl[1], site, seen)
    }

    // 4. A function parameter with a string-literal default, the shape rotation snapshots use.
    const param = source.match(new RegExp(`\\b${name}:\\s*String\\s*=\\s*("[^"]*")`))
    if (param) return resolveExpr(param[1], site, seen)

    return null
}

/** Every concrete `category.key` pair Kotlin can read, plus the site it came from. */
const resolvedReads = []
let dynamicSiteCount = 0
const literalPairs = new Set()

for (const site of sites) {
    const literalCategory = isStringLiteral(site.categoryExpr)
    const literalKey = isStringLiteral(site.keyExpr)
    if (!literalCategory || !literalKey) dynamicSiteCount++

    const categories = resolveExpr(site.categoryExpr, { ...site, isCategory: true })
    if (!categories) {
        addBlocker(`${relative(site.file)}:${site.line}`, `unrecognized settings category expression \`${site.categoryExpr}\` - teach resolveExpr this shape or give it a reasoned exception`)
        continue
    }
    const keys = resolveExpr(site.keyExpr, { ...site, isCategory: false })
    if (!keys) {
        addBlocker(`${relative(site.file)}:${site.line}`, `unrecognized settings key expression \`${site.keyExpr}\` - teach resolveExpr this shape or give it a reasoned exception`)
        continue
    }

    for (const category of categories) {
        for (const key of keys) {
            resolvedReads.push({ category, key, site })
            if (literalCategory && literalKey) literalPairs.add(`${category}.${key}`)
        }
    }
}

/**
 * Enumerates the `Settings` interface's leaf paths from the real AST. A nested object literal type is
 * descended into; everything else (`Record<...>`, arrays, unions, primitives) is a terminal leaf,
 * because its members are runtime data rather than declared contract keys.
 */
function collectSettingsLeaves() {
    const source = ts.createSourceFile(SETTINGS_SOURCE, fs.readFileSync(SETTINGS_SOURCE, "utf8"), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
    const declaration = source.statements.find((s) => ts.isInterfaceDeclaration(s) && s.name.text === "Settings")
    if (!declaration) return null

    const leaves = []
    const walk = (members, prefix) => {
        for (const member of members) {
            if (!ts.isPropertySignature(member) || !member.name || !member.type) continue
            const name = ts.isIdentifier(member.name) || ts.isStringLiteral(member.name) ? member.name.text : null
            if (!name) continue
            const pathName = prefix ? `${prefix}.${name}` : name
            if (ts.isTypeLiteralNode(member.type)) walk(member.type.members, pathName)
            else leaves.push(pathName)
        }
    }
    walk(declaration.members, "")
    return leaves
}

const settingsLeaves = collectSettingsLeaves()
if (!settingsLeaves) {
    addBlocker(relative(SETTINGS_SOURCE), "could not locate the exported `Settings` interface declaration")
}
const leafSet = new Set(settingsLeaves ?? [])

for (const { category, key, site } of resolvedReads) {
    const settingPath = `${category}.${key}`
    if (leafSet.has(settingPath)) continue
    if (RUNTIME_CATEGORIES[category]) continue
    if (RUNTIME_SETTING_PATHS[settingPath]) continue
    addViolation(
        "kotlin-read-without-setting",
        settingPath,
        `${relative(site.file)}:${site.line}`,
        "Kotlin reads this category/key but no matching Settings leaf exists, so the read silently returns its default forever.",
        `Add \`${settingPath}\` to the Settings interface and defaultSettings, or record it as runtime-owned with a reason.`
    )
}

const kotlinConsumed = new Set(resolvedReads.map(({ category, key }) => `${category}.${key}`))

/**
 * Conservative lexical consumer scan over the TypeScript side. Limits, accepted deliberately: it
 * matches a leaf's own name as a whole word, so a field consumed only through a computed key would be
 * missed, and a same-named field on an unrelated object counts as a consumer. It is a liveness signal
 * for newly orphaned settings, not a whole-program reachability analysis - hence the false-negative
 * bias. The defining interface, the defaults literal, and the preset overrides are excluded, since all
 * three only restate a value rather than acting on it.
 */
function collectTsConsumedNames() {
    const excluded = new Set([SETTINGS_SOURCE, path.join(REPO_ROOT, "src", "data", "characterPresets.ts")])
    const names = new Set()
    for (const file of [...collectFiles(TS_CONSUMER_ROOT, ".ts"), ...collectFiles(TS_CONSUMER_ROOT, ".tsx")]) {
        if (excluded.has(file) || file.includes("__tests__")) continue
        for (const m of fs.readFileSync(file, "utf8").matchAll(/\b[A-Za-z_]\w*\b/g)) names.add(m[0])
    }
    return names
}

const tsConsumedNames = collectTsConsumedNames()

for (const leaf of settingsLeaves ?? []) {
    if (kotlinConsumed.has(leaf)) continue
    if (tsConsumedNames.has(leaf.split(".").pop())) continue
    if (UNCONSUMED_SETTINGS_ALLOWLIST[leaf]) continue
    addViolation(
        "setting-without-consumer",
        leaf,
        relative(SETTINGS_SOURCE),
        "No Kotlin read and no TypeScript reference outside the interface, the defaults, and the presets.",
        `Wire \`${leaf}\` to a consumer, delete it, or record it in UNCONSUMED_SETTINGS_ALLOWLIST with a reason.`
    )
}

for (const [name, table] of [
    ["RUNTIME_CATEGORIES", RUNTIME_CATEGORIES],
    ["RUNTIME_SETTING_PATHS", RUNTIME_SETTING_PATHS],
    ["UNCONSUMED_SETTINGS_ALLOWLIST", UNCONSUMED_SETTINGS_ALLOWLIST],
]) {
    for (const [entry, reason] of Object.entries(table)) {
        if (typeof reason !== "string" || reason.trim().length === 0) {
            addBlocker(`${name}.${entry}`, "allowlist entry has no reason")
        }
    }
}
for (const prefix of ROTATION_PREFIXES) {
    if (!prefix.reason?.trim()) addBlocker(`ROTATION_PREFIXES.${prefix.id}`, "rotation-prefix entry has no reason")
}

if (blockers.length > 0) {
    console.error("settings-parity: cannot safely verify current source.\n")
    for (const { where, reason } of blockers) console.error(`  [blocked] ${where}\n            ${reason}`)
    console.error("\nAn unrecognized settings-access shape is never assumed safe. Teach the checker the shape, then re-run.")
    process.exit(2)
}

if (violations.length > 0) {
    console.error("settings-parity: contract violations.\n")
    for (const { rule, subject, where, reason, hint } of violations) {
        console.error(`  [${rule}] ${subject}`)
        console.error(`      at   ${where}`)
        console.error(`      why  ${reason}`)
        console.error(`      fix  ${hint}`)
    }
    process.exit(1)
}

const allowlisted = Object.keys(RUNTIME_SETTING_PATHS).length + Object.keys(UNCONSUMED_SETTINGS_ALLOWLIST).length
console.log(
    `settings-parity: ${sites.length} reads, ${literalPairs.size} literal pairs, ${dynamicSiteCount} dynamic sites, ` +
        `${settingsLeaves.length} leaves, ${allowlisted} allowlisted. OK`
)
