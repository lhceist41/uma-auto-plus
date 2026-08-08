// Local Master-Data Compiler v1 - offline CLI.
//
// Compiles the six committed raw `src/data/*.json` files into canonical derived artifacts
// (src/data/compiled/skills.json, races.json, manifest.json). The raw layer stays the single authority
// and is never modified; this is a developer/analytics foundation with no Android/runtime integration.
//
// Deterministic and offline: same raw bytes + same compiler version -> byte-identical artifacts. No
// network access. On any hard validation error nothing is written and the existing compiled set is left
// untouched. Writes are atomic (temp file + rename).
//
// Usage:
//   node scripts/compile-master-data.mjs [--input-dir <dir>] [--output-dir <dir>] [--check] [--help]
//
//   --input-dir <dir>    Directory holding the six raw JSON files (default: <repo>/src/data).
//   --output-dir <dir>   Directory for compiled artifacts (default: <repo>/src/data/compiled).
//   --check              Compile in memory and compare to the committed artifacts; never writes.
//                        Exit 3 when generated output differs (stale).
//
// Exit codes (worst wins): 0 clean | 1 warnings | 2 usage/validation failure | 3 (--check) stale output.
//
// Requires node >= 23.6 (native TypeScript type stripping; the logic lives in src/lib/masterData/).

import { readFileSync, writeFileSync, renameSync, mkdirSync, existsSync } from "node:fs"
import { join, dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"
import { compileMasterData } from "../src/lib/masterData/compiler.ts"
import { EXIT_CLEAN, EXIT_WARNINGS, EXIT_VALIDATION, EXIT_STALE } from "../src/lib/masterData/types.ts"

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..")

// family -> [raw filename, canonical repo-relative path]. The canonical path is what the manifest records,
// independent of --input-dir, so the fingerprint stays deterministic across machines and fixture dirs.
const RAW_FILES = [
    ["skills", "skills.json", "src/data/skills.json"],
    ["races", "races.json", "src/data/races.json"],
    ["characters", "characters.json", "src/data/characters.json"],
    ["supports", "supports.json", "src/data/supports.json"],
    ["scenarios", "scenarios.json", "src/data/scenarios.json"],
    ["objectives", "character_objectives.json", "src/data/character_objectives.json"],
]

const HELP = `compile-master-data - Local Master-Data Compiler v1 (offline, deterministic)

Options:
  --input-dir <dir>    Directory holding the six raw JSON files (default: <repo>/src/data).
  --output-dir <dir>   Directory for compiled artifacts (default: <repo>/src/data/compiled).
  --check              Compile in memory, compare to committed artifacts, write nothing.
  --help               Show this help.

Exit: 0 clean | 1 warnings | 2 usage/validation failure | 3 (--check) stale output.`

function parseArgs(argv) {
    const opts = { inputDir: join(ROOT, "src/data"), outputDir: join(ROOT, "src/data/compiled"), check: false, help: false }
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i]
        const next = () => {
            const v = argv[++i]
            if (v === undefined) throw new Error(`${arg} requires a value`)
            return v
        }
        switch (arg) {
            case "--input-dir":
                opts.inputDir = resolve(next())
                break
            case "--output-dir":
                opts.outputDir = resolve(next())
                break
            case "--check":
                opts.check = true
                break
            case "--help":
            case "-h":
                opts.help = true
                break
            default:
                throw new Error(`unknown argument: ${arg}`)
        }
    }
    return opts
}

function main(argv) {
    let opts
    try {
        opts = parseArgs(argv)
    } catch (e) {
        console.error(`${e.message}\n`)
        console.error(HELP)
        return EXIT_VALIDATION
    }
    if (opts.help) {
        console.log(HELP)
        return EXIT_CLEAN
    }

    // Read the six raw files. A missing/unreadable raw file is a usage error.
    const inputs = []
    for (const [family, filename, canonicalPath] of RAW_FILES) {
        const fsPath = join(opts.inputDir, filename)
        let bytes
        try {
            bytes = readFileSync(fsPath, "utf8")
        } catch (e) {
            console.error(`cannot read raw input ${fsPath}: ${e instanceof Error ? e.message : String(e)}`)
            return EXIT_VALIDATION
        }
        inputs.push({ family, path: canonicalPath, bytes })
    }

    // Read a previous manifest for large-change detection, if one exists.
    let previousManifest = null
    const manifestPath = join(opts.outputDir, "manifest.json")
    if (existsSync(manifestPath)) {
        try {
            previousManifest = JSON.parse(readFileSync(manifestPath, "utf8"))
        } catch {
            previousManifest = null // a corrupt existing manifest simply disables large-change comparison.
        }
    }

    const result = compileMasterData(inputs, { previousManifest })

    // Report diagnostics (never embedded verbatim in the artifact).
    for (const w of result.validation.warnings) console.error(`[warn] ${w.code}: ${w.detail}`)
    for (const info of result.validation.info) console.error(`[info] ${info.code}: ${info.detail}`)
    if (!result.ok) {
        for (const err of result.errors) console.error(`[error] ${err.code}: ${err.detail}`)
        console.error(`compile failed with ${result.errors.length} hard error(s); no artifact written.`)
        return EXIT_VALIDATION
    }

    const artifacts = [
        ["skills.json", result.artifacts.skills],
        ["races.json", result.artifacts.races],
        ["manifest.json", result.artifacts.manifest],
    ]

    if (opts.check) {
        // Compare generated output to the committed artifacts; write nothing.
        let stale = false
        for (const [name, content] of artifacts) {
            const target = join(opts.outputDir, name)
            const current = existsSync(target) ? readFileSync(target, "utf8") : null
            if (current !== content) {
                stale = true
                console.error(`[stale] ${name} would change on recompile`)
            }
        }
        if (stale) return EXIT_STALE
        console.log(`check clean: compiled artifacts are up to date (fingerprint ${result.fingerprint}).`)
        return result.exitCode // 0 or 1 (warnings)
    }

    // Atomic writes: build every file, then rename each into place. A hard error above already returned.
    mkdirSync(opts.outputDir, { recursive: true })
    for (const [name, content] of artifacts) {
        const target = join(opts.outputDir, name)
        const tmp = target + ".tmp"
        writeFileSync(tmp, content, "utf8")
        renameSync(tmp, target)
    }
    console.log(`compiled ${result.stats.skillCompiledCount} skills + ${result.stats.raceCompiledCount} races; fingerprint ${result.fingerprint}.`)
    return result.exitCode
}

process.exitCode = main(process.argv.slice(2))
