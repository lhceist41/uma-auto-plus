// collect-telemetry - durable, read-only host-side archive of the app's JSONL telemetry corpus.
//
// It turns ephemeral device telemetry (decisions.jsonl / career_state.jsonl / careers.jsonl written under
// the app external files dir by OutcomeCorpus.kt) into a durable, hashed, manifested bundle under
// validation/corpus/. Two modes:
//
//   Device pull (read-only adb pull; NEVER root/su/cp/chmod/chown/rm against device telemetry):
//     node scripts/collect-telemetry.mjs --label <label> [--device <serial>] [--adb <adb-path>]
//   Existing-directory archive (no adb needed):
//     node scripts/collect-telemetry.mjs --from-dir <path> --label <label>
//
// It never mutates, normalizes, clears, or renames source/device telemetry. Archived JSONL bytes are
// preserved exactly and hashed with SHA-256. Reset/clear of device telemetry is a SEPARATE manual action,
// only after a verified archive - this collector never does it.
//
// Exit: 0 archived | 2 usage/IO/adb/destination error. Malformed JSONL rows are preserved and counted in
// the manifest, never fatal.
//
// Requires node >= 23.6 (native TypeScript type stripping; the pure logic lives in src/lib/telemetryCorpus/).

import { execFileSync } from "node:child_process"
import { existsSync, statSync, mkdirSync, rmSync, renameSync, readFileSync, writeFileSync, copyFileSync } from "node:fs"
import { join, isAbsolute, resolve, relative } from "node:path"
import {
    TELEMETRY_FILENAMES,
    REQUIRED_FILENAMES,
    remoteTelemetryPath,
    DEVICE_OUTCOMES_DIR,
    buildAdbPullArgs,
    bundleId as makeBundleId,
    sanitizeLabel,
    parseJsonl,
    fileMetadata,
    analyzeCorpus,
    buildManifest,
} from "../src/lib/telemetryCorpus/collect.ts"

const CORPUS_ROOT = "validation/corpus"

const HELP = `collect-telemetry - durable read-only telemetry corpus archive

Modes:
  --label <label>                 Device pull mode (default): read-only adb pull from the app outcomes dir.
  --from-dir <path> --label <l>   Archive already-copied telemetry from a directory (no adb).

Options:
  --device <serial>   adb device serial (adb -s <serial>); omit for single-device adb. Device mode only.
  --adb <path>        adb executable to use (default: adb on PATH). Device mode only.
  --json              Print the manifest JSON to stdout after archiving.
  --help              Show this help.

Archives into ${CORPUS_ROOT}/<UTC-date>-<label>/ with decisions/career_state/careers JSONL + manifest.json.
Required inputs: decisions.jsonl + career_state.jsonl. careers.jsonl is optional.
Exit: 0 archived | 2 usage/IO/adb/destination error.`

function parseArgs(argv) {
    const opts = { label: null, device: null, fromDir: null, adb: "adb", json: false, help: false }
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i]
        const next = () => {
            const v = argv[++i]
            if (v === undefined) throw new Error(`${arg} requires a value`)
            return v
        }
        switch (arg) {
            case "--label":
                opts.label = next()
                break
            case "--device":
                opts.device = next()
                break
            case "--from-dir":
                opts.fromDir = next()
                break
            case "--adb":
                opts.adb = next()
                break
            case "--json":
                opts.json = true
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

/** Places each telemetry file into tempDir. Required files must appear; careers.jsonl may be absent. */
function acquireFiles(opts, tempDir) {
    const present = []
    for (const filename of [TELEMETRY_FILENAMES.decisions, TELEMETRY_FILENAMES.careerState, TELEMETRY_FILENAMES.careers]) {
        const dest = join(tempDir, filename)
        const required = REQUIRED_FILENAMES.includes(filename)
        if (opts.fromDir !== null) {
            const src = join(opts.fromDir, filename)
            if (!existsSync(src) || !statSync(src).isFile()) {
                if (required) throw new Error(`required input missing in --from-dir: ${filename}`)
                continue
            }
            copyFileSync(src, dest) // preserves exact bytes; source is only read
            present.push(filename)
        } else {
            // Read-only adb pull. execFile (no shell) with a pull-only argv - never root/su/cp/chmod.
            try {
                execFileSync(opts.adb, buildAdbPullArgs(opts.device, remoteTelemetryPath(filename), dest), { stdio: "pipe" })
            } catch (e) {
                if (required) throw new Error(`adb pull failed for required ${filename}: ${e instanceof Error ? e.message.split("\n")[0] : String(e)}`)
                continue // optional careers.jsonl may legitimately be absent on device
            }
            if (!existsSync(dest)) {
                if (required) throw new Error(`adb pull did not produce ${filename}`)
                continue
            }
            present.push(filename)
        }
    }
    return present
}

function main(argv) {
    let opts
    try {
        opts = parseArgs(argv)
    } catch (e) {
        console.error(`${e.message}\n`)
        console.error(HELP)
        return 2
    }
    if (opts.help) {
        console.log(HELP)
        return 0
    }
    if (!opts.label) {
        console.error("Missing required --label <label>.\n")
        console.error(HELP)
        return 2
    }
    if (opts.fromDir !== null && (opts.device !== null || opts.adb !== "adb")) {
        console.error("--from-dir cannot be combined with device-mode flags (--device/--adb).\n")
        console.error(HELP)
        return 2
    }
    if (opts.fromDir !== null && (!existsSync(opts.fromDir) || !statSync(opts.fromDir).isDirectory())) {
        console.error(`--from-dir is not a readable directory: ${opts.fromDir}`)
        return 2
    }

    let id
    try {
        // UTC date only (deterministic segment); the full timestamp is recorded in the manifest.
        id = makeBundleId(new Date().toISOString(), opts.label)
    } catch (e) {
        console.error(e instanceof Error ? e.message : String(e))
        return 2
    }

    const finalDir = join(CORPUS_ROOT, id)
    const tempDir = `${finalDir}.tmp`
    if (existsSync(finalDir)) {
        console.error(`destination already exists, refusing to overwrite: ${finalDir}`)
        return 2 // never merge, never delete, never pick a random suffix
    }
    // Preservation-first: never auto-delete a pre-existing temp path (it may not be ours). Refuse instead.
    if (existsSync(tempDir)) {
        console.error(`temporary bundle path already exists; inspect/remove it manually before retrying: ${tempDir}`)
        return 2
    }
    // Overlap guard: the source (--from-dir) must not equal or contain, and must not sit inside, the temp or
    // final bundle path. This makes the equal-path attacks (fromDir == tempDir / == finalDir) impossible and
    // guarantees the collector never creates/deletes inside the source.
    if (opts.fromDir !== null) {
        const fromR = resolve(opts.fromDir)
        const tempR = resolve(tempDir)
        const finalR = resolve(finalDir)
        if (pathsOverlap(fromR, tempR) || pathsOverlap(fromR, finalR)) {
            console.error(`--from-dir overlaps the bundle temp/destination path; choose a source outside ${CORPUS_ROOT}: ${opts.fromDir}`)
            return 2
        }
    }

    let tempCreatedByThisRun = false
    try {
        mkdirSync(CORPUS_ROOT, { recursive: true })
        // mkdir (not recursive) throws if it already exists; combined with the refusal above, tempDir is only
        // ever a directory this invocation created, so the failure path may clean it safely.
        mkdirSync(tempDir)
        tempCreatedByThisRun = true

        const present = acquireFiles(opts, tempDir)

        // Build metadata + factual analysis over the exact archived bytes.
        const metas = []
        let decisionRecords = []
        let stateRecords = []
        let careerRecords = null
        let totalByteSize = 0
        for (const filename of present) {
            const bytes = readFileSync(join(tempDir, filename))
            const parsed = parseJsonl(bytes)
            metas.push(fileMetadata(filename, bytes, parsed))
            totalByteSize += bytes.length
            if (filename === TELEMETRY_FILENAMES.decisions) decisionRecords = parsed.records
            else if (filename === TELEMETRY_FILENAMES.careerState) stateRecords = parsed.records
            else if (filename === TELEMETRY_FILENAMES.careers) careerRecords = parsed.records
        }

        const analysis = analyzeCorpus(decisionRecords, stateRecords, careerRecords)
        const manifest = buildManifest({
            label: opts.label,
            sanitizedLabel: sanitizeLabel(opts.label),
            bundleId: id,
            collectedAtUtc: new Date().toISOString(),
            source: {
                mode: opts.fromDir !== null ? "fromDir" : "adbPull",
                deviceSerial: opts.fromDir !== null ? null : opts.device,
                deviceTelemetryPath: opts.fromDir !== null ? null : DEVICE_OUTCOMES_DIR,
                fromDir: opts.fromDir !== null ? (isAbsolute(opts.fromDir) ? "<absolute-path-redacted>" : opts.fromDir) : null,
            },
            files: metas,
            analysis,
            totalByteSize,
        })
        writeFileSync(join(tempDir, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n")

        // Atomic-ish publish: temp -> final only after everything succeeded.
        renameSync(tempDir, finalDir)

        const g = manifest.summary
        console.log(`archived ${present.length} file(s) to ${finalDir}`)
        console.log(`decisions ${g.decisionRecordCount}, state ${g.stateRecordCount}, careers ${g.careerRecordCount ?? "absent"}; paired tokens ${g.pairedCareerTokenCount}, shared seqs ${g.sharedSeqCount}; ${totalByteSize} bytes.`)
        if (opts.json) console.log(JSON.stringify(manifest, null, 2))
        return 0
    } catch (e) {
        // Clean ONLY a temp directory THIS invocation created; never a pre-existing path or any source/device
        // telemetry or an existing final bundle. The ownership flag (not a mere pathname check) enforces this.
        if (tempCreatedByThisRun && existsSync(tempDir)) rmSync(tempDir, { recursive: true, force: true })
        console.error(`collect-telemetry failed: ${e instanceof Error ? e.message : String(e)}`)
        return 2
    }
}

/** True when a and b are the same resolved path, or one contains the other. */
function pathsOverlap(aAbs, bAbs) {
    if (aAbs === bAbs) return true
    const relAB = relative(aAbs, bAbs)
    const relBA = relative(bAbs, aAbs)
    const contains = (rel) => rel.length > 0 && !rel.startsWith("..") && !isAbsolute(rel)
    return contains(relAB) || contains(relBA)
}

process.exitCode = main(process.argv.slice(2))
