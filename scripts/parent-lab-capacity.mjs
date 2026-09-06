// ParentLab Manual Capacity Triage - offline, read-only, counts-only, advisory only.
//
// Reads a persisted retention document (what `parent-lab-retention --out` writes) and reports how
// large the eligible HUMAN capacity-review pool is, how many Veterans are excluded, and why. It never
// ranks, never scores, never transfers, favorites, memos or deletes anything, it never touches the
// device, and it rewrites none of its inputs.
//
// ELIGIBLE_FOR_MANUAL_REVIEW is not "safe to release": it means a human may consider this Veteran when
// roster capacity is tight. The strict retention state is carried verbatim for context, never
// reinterpreted. Account-wide capture coverage is NOT a gate here: partial coverage excludes only the
// specific Veterans whose own factor evidence is missing/untrusted, not the whole pool.
//
// Usage:
//   node scripts/parent-lab-capacity.mjs --retention <retention_document.json>
//                                        [--target <PROFILE>] [--json] [--out <path>]
//                                        [--summary-out <path>] [--help]
//
// Exit codes: 0 built on a trusted roster snapshot | 1 roster snapshot untrusted | 2 input/parse failure
//
// Requires node >= 23.6 (native TypeScript type stripping; the logic lives in src/lib/parentLab/ and
// is exercised by the Jest suite).

import { existsSync, readFileSync, statSync, writeFileSync } from "node:fs"
import { buildCapacityTriage } from "../src/lib/parentLab/capacityEvidence.ts"
import { retentionReportsOf } from "../src/lib/parentLab/quarantineSnapshot.ts"
import { resolveTargetProfile, TARGET_PROFILE_IDS, TARGET_PROFILES } from "../src/lib/parentLab/retentionTargets.ts"

const DEFAULT_TARGET = "GENERAL_INHERITANCE"

const HELP = `parent-lab-capacity - Manual Capacity Triage (read-only, counts-only, advisory only)

Options:
  --retention <path>    Path to a persisted retention document from parent-lab-retention --out (required).
  --target <PROFILE>    Which target report to triage: ${TARGET_PROFILE_IDS.join(" | ")}.
                        Defaults to the only report if the document has one, else ${DEFAULT_TARGET}.
  --json                Print the raw capacity document as JSON instead of the summary.
  --out <path>          Write the full JSON capacity document to a file.
  --summary-out <path>  Write only the counts summary to a file.
  --help                Show this help.

Admission is human-review-only. Nothing is ranked, scored, transferred, released, favorited or modified.
Exit: 0 trusted roster | 1 untrusted roster | 2 input failure.`

function parseArgs(argv) {
    const opts = { retention: null, target: null, json: false, out: null, summaryOut: null, help: false }
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i]
        const next = () => {
            const v = argv[++i]
            if (v === undefined) throw new Error(`${arg} requires a value`)
            return v
        }
        switch (arg) {
            case "--retention":
                opts.retention = next()
                break
            case "--target":
                opts.target = next()
                break
            case "--json":
                opts.json = true
                break
            case "--out":
                opts.out = next()
                break
            case "--summary-out":
                opts.summaryOut = next()
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

/** Chooses which target report to triage from the retention document's report list. */
function selectReport(reports, requestedTarget) {
    if (requestedTarget) {
        const profile = resolveTargetProfile(requestedTarget)
        if (!profile) throw new Error(`unknown --target ${requestedTarget}. Known profiles: ${TARGET_PROFILE_IDS.join(", ")}`)
        const match = reports.find((r) => r.targetProfile === profile.id)
        if (!match) throw new Error(`retention document has no report for target ${profile.id}. Present: ${reports.map((r) => r.targetProfile).join(", ")}`)
        return match
    }
    if (reports.length === 1) return reports[0]
    const fallback = reports.find((r) => r.targetProfile === DEFAULT_TARGET)
    if (!fallback) throw new Error(`retention document has ${reports.length} reports and no ${DEFAULT_TARGET}; pass --target. Present: ${reports.map((r) => r.targetProfile).join(", ")}`)
    return fallback
}

/** The counts-only view, for --summary-out and the default human render. */
function summaryOf(doc) {
    return {
        schema: doc.schema,
        schemaVersion: doc.schemaVersion,
        kind: doc.kind,
        targetProfile: doc.targetProfile,
        rosterScanId: doc.rosterScanId,
        rosterFingerprint: doc.rosterFingerprint,
        protectionScanId: doc.protectionScanId,
        generatedAt: doc.generatedAt,
        rosterCount: doc.rosterCount,
        admittedCount: doc.admittedCount,
        excludedCount: doc.excludedCount,
        exclusionHistogram: doc.exclusionHistogram,
        admittedStrictStateDistribution: doc.admittedStrictStateDistribution,
        excludedStrictStateDistribution: doc.excludedStrictStateDistribution,
        evidenceSummary: doc.evidenceSummary,
    }
}

function renderSummary(doc) {
    const lines = []
    const label = TARGET_PROFILES[doc.targetProfile] ? TARGET_PROFILES[doc.targetProfile].label : doc.targetProfile
    lines.push(`=== Manual Capacity Triage: ${doc.targetProfile} (${label}) ===`)
    lines.push(`  roster scan           ${doc.rosterScanId}`)
    lines.push(`  roster fingerprint    ${doc.rosterFingerprint}`)
    lines.push(`  protection scan       ${doc.protectionScanId ?? "none (protection gates stayed closed)"}`)
    lines.push(`  evidence observed at  ${doc.generatedAt ? new Date(doc.generatedAt).toISOString() : "unknown"}  (newest input observation, not a clock read)`)
    lines.push("")
    lines.push("  Pool")
    lines.push(`    roster count                    ${pad(doc.rosterCount, 5)}`)
    lines.push(`    eligible for manual review      ${pad(doc.admittedCount, 5)}`)
    lines.push(`    excluded from manual review     ${pad(doc.excludedCount, 5)}`)
    lines.push(`    reconciles                      ${doc.admittedCount + doc.excludedCount === doc.rosterCount ? "YES" : "NO"}  (admitted + excluded == roster count)`)
    lines.push("")
    lines.push("  Exclusion histogram by reason (a Veteran may carry more than one reason)")
    for (const [reason, count] of Object.entries(doc.exclusionHistogram)) lines.push(`    ${reason.padEnd(32)} ${pad(count, 5)}`)
    lines.push("")
    lines.push("  Strict retention state distribution inside the ADMITTED pool")
    for (const [state, count] of Object.entries(doc.admittedStrictStateDistribution)) if (count > 0) lines.push(`    ${state.padEnd(24)} ${pad(count, 5)}`)
    lines.push("")
    lines.push("  Strict retention state distribution inside the EXCLUDED pool")
    for (const [state, count] of Object.entries(doc.excludedStrictStateDistribution)) if (count > 0) lines.push(`    ${state.padEnd(24)} ${pad(count, 5)}`)
    lines.push("")
    const e = doc.evidenceSummary
    lines.push("  Evidence completeness / trust")
    lines.push(`    identified roster entries       ${pad(e.identifiedRosterEntries, 5)}`)
    lines.push(`    trusted captures                ${pad(e.capturedTrusted, 5)}`)
    lines.push(`    untrusted captures              ${pad(e.capturedUntrusted, 5)}`)
    lines.push(`    capture coverage                ${(e.coverage * 100).toFixed(1)}%`)
    lines.push(`    account-wide coverage           ${e.accountWide ? "SUPPORTED" : "NOT SUPPORTED (context only; NOT a triage gate)"}`)
    lines.push(`    roster snapshot trusted         ${e.rosterTrusted ? "YES" : "NO"}`)
    lines.push(`    records w/ trusted self-factors ${pad(e.recordsWithTrustedSelfFactors, 5)}`)
    lines.push(`    records w/ known protection     ${pad(e.recordsProtectionKnown, 5)}`)
    lines.push("")
    lines.push("  Capacity triage is counts-only: it does NOT say which admitted Veteran is better or worse to release.")
    lines.push("  This document is advisory and human-review-only. Nothing was ranked, transferred or modified.")
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
    if (!opts.retention) {
        console.error("Missing required --retention <path>.\n")
        console.error(HELP)
        return 2
    }

    let doc
    try {
        const parsed = JSON.parse(readable("--retention", opts.retention))
        const reports = retentionReportsOf(parsed)
        if (reports.length === 0) throw new Error("retention document carries no reports")
        const report = selectReport(reports, opts.target)
        doc = buildCapacityTriage(report)
    } catch (e) {
        console.error(e instanceof Error ? e.message : String(e))
        return 2
    }

    if (opts.json) console.log(JSON.stringify(doc, null, 2))
    else console.log(renderSummary(doc))

    if (!doc.evidenceSummary.rosterTrusted) {
        console.error("")
        console.error("The roster snapshot behind this document is NOT trusted-complete, so every Veteran is excluded by construction.")
    }

    if (opts.out) {
        writeFileSync(opts.out, `${JSON.stringify(doc, null, 2)}\n`, "utf8")
        if (!opts.json) console.log(`\nWrote ${opts.out}`)
    }
    if (opts.summaryOut) {
        writeFileSync(opts.summaryOut, `${JSON.stringify(summaryOf(doc), null, 2)}\n`, "utf8")
        if (!opts.json) console.log(`Wrote ${opts.summaryOut}`)
    }

    return doc.evidenceSummary.rosterTrusted ? 0 : 1
}

// process.exitCode (not process.exit) so stdio flushes cleanly - process.exit after console writes trips
// a libuv handle assert on Windows node.
try {
    process.exitCode = main(process.argv.slice(2))
} catch (e) {
    console.error(`parent-lab-capacity failed: ${e instanceof Error ? (e.stack ?? e.message) : String(e)}`)
    process.exitCode = 2
}
