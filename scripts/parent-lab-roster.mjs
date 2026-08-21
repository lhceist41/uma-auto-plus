// ParentLab roster snapshot + reconciliation - offline, read-only.
//
// Reads the device's roster_scan.jsonl, derives the current-roster snapshot, and reconciles it
// against the PL-3 Veteran library rebuilt from careers.jsonl. Input files are never rewritten and
// nothing is persisted unless --out is given.
//
// Two artifacts, deliberately not merged: the snapshot says what the account owns right now, the
// library says what the bot has ever produced. The reconciliation is a third document that annotates
// the two, and it can only be as trustworthy as the scan behind it - an incomplete walk is reported
// as such and its "historical veterans not in the roster" list is explicitly marked unreliable,
// because a partial scan cannot prove an absence.
//
// Usage:
//   node scripts/parent-lab-roster.mjs --roster <roster_scan.jsonl> [--careers <careers.jsonl>]
//                                      [--lineage <lineage.jsonl>] [--scan-id <id>] [--all-scans]
//                                      [--json] [--out <path>] [--help]
//
// Exit codes: 0 trusted-complete snapshot | 1 snapshot present but incomplete | 2 input/parse failure
//
// Requires node >= 23.6 (native TypeScript type stripping; the logic lives in src/lib/parentLab/ and
// is exercised by the Jest suite).

import { existsSync, readFileSync, statSync, writeFileSync } from "node:fs"
import { parseCorpus } from "../src/lib/outcomeAnalysis.ts"
import { buildVeteranLibrary } from "../src/lib/parentLab/buildVeteranLibrary.ts"
import { parseLineageRecords } from "../src/lib/parentLab/lineage.ts"
import { reconcileRoster } from "../src/lib/parentLab/reconcile.ts"
import { buildRosterSnapshots, parseRosterScanRecords } from "../src/lib/parentLab/roster.ts"

const HELP = `parent-lab-roster - current Veteran roster snapshot + historical reconciliation (read-only)

Options:
  --roster <path>     Path to roster_scan.jsonl (required).
  --careers <path>    Path to careers.jsonl, to rebuild the PL-3 Veteran library to reconcile against.
  --lineage <path>    Path to lineage.jsonl, joined into the library by launchTransactionId.
  --scan-id <id>      Reconcile this scan instead of the newest one.
  --all-scans         List every scan found, with its completeness verdict, then reconcile the chosen one.
  --json              Print the raw snapshot + reconciliation document as JSON.
  --out <path>        Write that JSON document to a file.
  --help              Show this help.

Exit: 0 trusted-complete | 1 incomplete snapshot | 2 input/parse failure.`

function parseArgs(argv) {
    const opts = { roster: null, careers: null, lineage: null, scanId: null, allScans: false, json: false, out: null, help: false }
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i]
        const next = () => {
            const v = argv[++i]
            if (v === undefined) throw new Error(`${arg} requires a value`)
            return v
        }
        switch (arg) {
            case "--roster":
                opts.roster = next()
                break
            case "--careers":
                opts.careers = next()
                break
            case "--lineage":
                opts.lineage = next()
                break
            case "--scan-id":
                opts.scanId = next()
                break
            case "--all-scans":
                opts.allScans = true
                break
            case "--json":
                opts.json = true
                break
            case "--out":
                opts.out = next()
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

function readable(label, path) {
    if (!existsSync(path) || !statSync(path).isFile()) throw new Error(`${label} path is not a readable file: ${path}`)
    return readFileSync(path, "utf8")
}

function pad(value, width) {
    return String(value).padStart(width)
}

function renderReport(snapshot, reconciliation, library, parsed, allScans) {
    const lines = []
    lines.push("=== Veteran roster scans ===")
    if (allScans) {
        for (const s of allScans) {
            const when = s.observedAt ? new Date(s.observedAt).toISOString().replace("T", " ").slice(0, 19) : "unknown time"
            lines.push(`  ${s.scanId}  ${when}  ${pad(s.scanCount, 4)} entries  ${s.trustedComplete ? "TRUSTED" : `INCOMPLETE (${s.defects.join(", ")})`}`)
        }
        lines.push("")
    }

    lines.push("=== Roster scan ===")
    lines.push(`  scanId                ${snapshot.scanId}`)
    lines.push(`  observedAt            ${snapshot.observedAt ? new Date(snapshot.observedAt).toISOString() : "unknown"}`)
    lines.push(`  Registered            ${snapshot.registeredUsed ?? "?"}/${snapshot.registeredCapacity ?? "?"}${snapshot.percentFull !== null ? ` (${snapshot.percentFull}% full)` : ""}`)
    lines.push(`  view state            filtersOff=${snapshot.filtersOff ?? "unread"} sort=${snapshot.sortKey ?? "unread"}/${snapshot.sortDirection ?? "unread"}`)
    lines.push(`  entries enumerated    ${snapshot.scanCount}${snapshot.entryLimit > 0 ? ` (operator limit ${snapshot.entryLimit})` : ""}`)
    lines.push(`  unique fingerprints   ${snapshot.uniqueFingerprints}  duplicates=${snapshot.duplicateFingerprints}  unidentified=${snapshot.unidentified}`)
    lines.push(`  count discrepancy     ${snapshot.countDiscrepancy ?? "n/a"}`)
    lines.push(`  termination           ${snapshot.terminationReason ?? "no header record"}`)
    lines.push(`  enumeration complete  ${snapshot.enumerationComplete}  (did the walk cover the whole roster?)`)
    lines.push(`  identity complete     ${snapshot.identityComplete}  (did every entry resolve to a distinct identity?)`)
    lines.push(`  trusted for retention ${snapshot.trustedComplete ? "TRUSTED_COMPLETE" : "INCOMPLETE"}`)
    if (snapshot.defects.length > 0) lines.push(`  defects               ${snapshot.defects.join(", ")}`)
    if (parsed.malformedRecords > 0) lines.push(`  malformed lines       ${parsed.malformedRecords}`)

    lines.push("")
    lines.push("=== Reconciliation against ParentLab history ===")
    const c = reconciliation.counts
    lines.push(`  historical veterans   ${reconciliation.diagnostics.historicalVeterans}`)
    lines.push(`  exact                 ${c.exact}`)
    lines.push(`  probable              ${c.probable}`)
    lines.push(`  roster only           ${c.rosterOnly}`)
    lines.push(`  ambiguous             ${c.ambiguous}`)
    lines.push(`  unresolved            ${c.unresolved}`)
    lines.push(`  historical not in roster ${c.historicalNotInRoster}${reconciliation.historicalNotInRosterReliable ? "" : "  (UNRELIABLE: the scan is incomplete, so this is 'not seen', not 'not owned')"}`)
    lines.push(`  joinable entries      ${reconciliation.diagnostics.joinableEntries}/${snapshot.scanCount}`)
    lines.push(`  entries w/ career info ${reconciliation.diagnostics.entriesWithCareerInfo}`)
    lines.push(`  history key collisions ${reconciliation.diagnostics.historicalKeyCollisions}`)
    lines.push(`  contested matches     ${reconciliation.diagnostics.contestedMatches}`)

    if (library.veterans.length === 0) {
        lines.push("")
        lines.push("  No career corpus was supplied (--careers), so every roster entry is ROSTER_ONLY by construction.")
    }

    const notable = reconciliation.entries.filter((e) => e.status === "AMBIGUOUS" || e.status === "UNRESOLVED")
    if (notable.length > 0) {
        lines.push("")
        lines.push("=== Entries needing attention ===")
        for (const e of notable.slice(0, 40)) {
            lines.push(`  [${pad(e.scanIndex, 3)}] ${e.status.padEnd(10)} ${e.character ?? "?"} [${e.outfit ?? "?"}] rating=${e.rating ?? "?"} - ${e.reason}`)
        }
        if (notable.length > 40) lines.push(`  ... and ${notable.length - 40} more (use --json for the full list)`)
    }

    return lines.join("\n")
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
    if (!opts.roster) {
        console.error("Missing required --roster <path>.\n")
        console.error(HELP)
        return 2
    }

    let parsed
    let library
    try {
        parsed = parseRosterScanRecords(readable("--roster", opts.roster), opts.roster)
        const corpus = opts.careers ? parseCorpus(readable("--careers", opts.careers), opts.careers) : { outcomes: [], sparks: [] }
        const lineageEvents = opts.lineage ? parseLineageRecords(readable("--lineage", opts.lineage), opts.lineage) : []
        library = buildVeteranLibrary({ outcomes: corpus.outcomes, sparks: corpus.sparks, lineageEvents })
    } catch (e) {
        console.error(e instanceof Error ? e.message : String(e))
        return 2
    }

    const snapshots = buildRosterSnapshots(parsed)
    if (snapshots.length === 0) {
        console.error(`No roster scan records found in ${opts.roster}. Run the Veteran Roster Scan diagnostic first.`)
        return 2
    }
    const snapshot = opts.scanId ? snapshots.find((s) => s.scanId === opts.scanId) : snapshots[0]
    if (!snapshot) {
        console.error(`No scan with id ${opts.scanId}. Known ids: ${snapshots.map((s) => s.scanId).join(", ")}`)
        return 2
    }

    const reconciliation = reconcileRoster(library, snapshot)
    const document = { snapshot, reconciliation }

    if (opts.json) console.log(JSON.stringify(document, null, 2))
    else console.log(renderReport(snapshot, reconciliation, library, parsed, opts.allScans ? snapshots : null))

    if (opts.out) {
        writeFileSync(opts.out, `${JSON.stringify(document, null, 2)}\n`, "utf8")
        if (!opts.json) console.log(`\nWrote ${opts.out}`)
    }

    return snapshot.trustedComplete ? 0 : 1
}

// process.exitCode (not process.exit) so stdio flushes cleanly - process.exit after console writes trips
// a libuv handle assert on Windows node.
try {
    process.exitCode = main(process.argv.slice(2))
} catch (e) {
    console.error(`parent-lab-roster failed: ${e instanceof Error ? (e.stack ?? e.message) : String(e)}`)
    process.exitCode = 2
}
