// ParentLab Manual Capacity Triage (Slice 2) - the Capacity Coverage Exposure Ledger.
// Offline, read-only, structure-only, advisory only.
//
// Reads a persisted retention document (what `parent-lab-retention --out` writes), builds Slice 1's
// admission verdicts in-process from the SAME selected report, and reports coverage exposure on top of
// that pool: for every factor slot (factorKey @ starFloor), every character, and the selected target
// profile, which carriers are inside the eligible manual-review pool (exposed) versus anchored outside
// it. It never ranks, scores, transfers, favorites, memos or deletes anything, never touches the
// device, and rewrites none of its inputs.
//
// "Exposed" is a coverage-structure fact, NOT a transfer-safety claim: it says every observed carrier
// of a slot is inside the review pool, so a human should see the exposure before acting on Slice 1.
//
// Usage:
//   node scripts/parent-lab-capacity-coverage.mjs --retention <retention_document.json>
//        [--target <PROFILE>] [--expect-roster-scan <id>] [--json]
//        [--out <path>] [--exposed-out <path>] [--help]
//
// Exit codes: 0 usable (trusted roster) | 1 unusable (untrusted roster) | 2 input/parse/freshness failure
//
// Requires node >= 23.6 (native TypeScript type stripping; the logic lives in src/lib/parentLab/ and
// is exercised by the Jest suite).

import { existsSync, readFileSync, statSync, writeFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"
import { buildCapacityCoverage } from "../src/lib/parentLab/capacityCoverage.ts"
import { retentionReportsOf } from "../src/lib/parentLab/quarantineSnapshot.ts"
import { resolveTargetProfile, TARGET_PROFILE_IDS, TARGET_PROFILES } from "../src/lib/parentLab/retentionTargets.ts"

const DEFAULT_TARGET = "GENERAL_INHERITANCE"

// The canonical white factor domain ships as an app asset. Resolving it relative to this script keeps the
// lookup independent of the caller's working directory; PARENT_LAB_WHITE_FACTOR_DOMAIN overrides it for tests.
const DOMAIN_ASSET = process.env.PARENT_LAB_WHITE_FACTOR_DOMAIN ?? join(dirname(fileURLToPath(import.meta.url)), "..", "android", "app", "src", "main", "assets", "veteran_factor_domain.json")

/** Parses the white factor families from the committed domain asset. This asset is required CLI evidence,
 * so a missing, syntax-invalid, or structurally invalid asset fails closed instead of degrading. */
function loadWhiteFactorDomain(path) {
    if (!existsSync(path) || !statSync(path).isFile()) throw new Error(`white factor domain asset is missing: ${path}`)
    let families
    try {
        families = JSON.parse(readFileSync(path, "utf8")).families
    } catch (e) {
        throw new Error(`white factor domain asset is not valid JSON (${e instanceof Error ? e.message : String(e)}): ${path}`)
    }
    if (!families || !Array.isArray(families.skill) || !Array.isArray(families.race) || !Array.isArray(families.scenario)) {
        throw new Error(`white factor domain asset has an invalid structure (expected families.skill/race/scenario arrays): ${path}`)
    }
    return { skill: families.skill, race: families.race, scenario: families.scenario }
}

const HELP = `parent-lab-capacity-coverage - Capacity Coverage Exposure Ledger, Slice 2 (read-only, structure only)

Options:
  --retention <path>        Path to a persisted retention document from parent-lab-retention --out (required).
  --target <PROFILE>        Which target report to measure: ${TARGET_PROFILE_IDS.join(" | ")}.
                            Defaults to the only report if the document has one, else ${DEFAULT_TARGET}.
  --expect-roster-scan <id> Fail closed unless the selected report's rosterScanId matches exactly.
  --json                    Print the raw coverage document as JSON instead of the summary.
  --out <path>              Write the full JSON coverage document to a file.
  --exposed-out <path>      Write an exposed-slot-focused JSON view to a file.
  --help                    Show this help.

Coverage exposure is structure only. Nothing is ranked, scored, valued, transferred, released, favorited or modified.
Exit: 0 usable (trusted roster) | 1 unusable (untrusted roster) | 2 input/freshness failure.`

function parseArgs(argv) {
    const opts = { retention: null, target: null, expectRosterScan: null, json: false, out: null, exposedOut: null, help: false }
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
            case "--expect-roster-scan":
                opts.expectRosterScan = next()
                break
            case "--json":
                opts.json = true
                break
            case "--out":
                opts.out = next()
                break
            case "--exposed-out":
                opts.exposedOut = next()
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

/** Chooses which target report to measure, exactly as Slice 1 does. */
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

/** The exposed-slot-focused view for --exposed-out: only slots inside the review pool, plus the risky Veterans. */
function exposedViewOf(doc) {
    const exposedFactorSlots = doc.factorSlots.filter((s) => s.exposure === "FULLY_EXPOSED" || s.exposure === "FULLY_EXPOSED_SOLE")
    const exposedCharacterSlots = doc.characterSlots.filter((s) => s.exposure === "FULLY_EXPOSED" || s.exposure === "FULLY_EXPOSED_SOLE")
    const exposedTargetSlots = doc.targetSlots.filter((s) => s.exposure === "FULLY_EXPOSED" || s.exposure === "FULLY_EXPOSED_SOLE")
    const riskyVeterans = doc.exposures.filter((v) => v.lastCopyRisk === "SOLE_OBSERVED_CARRIER" || v.lastCopyRisk === "SHARED_FULLY_EXPOSED")
    return {
        schema: doc.schema,
        schemaVersion: doc.schemaVersion,
        kind: doc.kind,
        targetProfile: doc.targetProfile,
        rosterScanId: doc.rosterScanId,
        rosterFingerprint: doc.rosterFingerprint,
        generatedAt: doc.generatedAt,
        usable: doc.usable,
        poolSize: doc.poolSize,
        factorExposureCounts: doc.factorExposureCounts,
        characterExposureCounts: doc.characterExposureCounts,
        whiteSubfamilyCoverage: doc.whiteSubfamilyCoverage,
        exposedFactorSlots,
        exposedCharacterSlots,
        exposedTargetSlots,
        riskyVeterans,
        limits: doc.limits,
    }
}

function renderSummary(doc) {
    const lines = []
    const label = TARGET_PROFILES[doc.targetProfile] ? TARGET_PROFILES[doc.targetProfile].label : doc.targetProfile
    lines.push(`=== Capacity Coverage Exposure Ledger (Slice 2): ${doc.targetProfile} (${label}) ===`)
    lines.push(`  roster scan           ${doc.rosterScanId}`)
    lines.push(`  roster fingerprint    ${doc.rosterFingerprint}`)
    lines.push(`  protection scan       ${doc.protectionScanId ?? "none (protection gates stayed closed)"}`)
    lines.push(`  evidence observed at  ${doc.generatedAt ? new Date(doc.generatedAt).toISOString() : "unknown"}  (newest input observation, not a clock read)`)
    lines.push(`  usable                ${doc.usable ? "YES" : "NO"}`)
    lines.push("")
    if (!doc.usable) {
        lines.push("  The roster snapshot behind this document is NOT trusted-complete, so no coverage exposure")
        lines.push("  could be measured. This is NOT a finding that nothing is at risk: the ledger is empty because")
        lines.push("  the input could not be trusted, not because the account has no exposed coverage.")
        lines.push("")
    }
    lines.push("  Pool")
    lines.push(`    roster count                    ${pad(doc.rosterCount, 5)}`)
    lines.push(`    eligible review pool            ${pad(doc.poolSize, 5)}`)
    lines.push(`    excluded from review            ${pad(doc.excludedSize, 5)}`)
    lines.push(`    records without trusted factors ${pad(doc.recordsWithoutTrustedFactors, 5)}  (anchor nothing)`)
    lines.push(`    unkeyed records                 ${pad(doc.unkeyedRecords, 5)}`)
    lines.push("")
    lines.push("  Capture coverage")
    lines.push(`    coverage                        ${(doc.coverage * 100).toFixed(1)}%`)
    lines.push(`    account-wide                    ${doc.accountWide ? "YES (factor claims ACCOUNT)" : "NO (factor claims OBSERVED_LOWER_BOUND)"}`)
    lines.push(`    unresolved factor reads         ${pad(doc.unresolvedFactorReads, 5)}`)
    lines.push("")
    lines.push("  Factor slot exposure (factorKey @ starFloor)")
    lines.push(`    total slots                     ${pad(doc.factorSlots.length, 5)}`)
    for (const [exposure, count] of Object.entries(doc.factorExposureCounts)) lines.push(`    ${exposure.padEnd(24)} ${pad(count, 5)}`)
    lines.push("")
    lines.push("  Character slot exposure (roster membership)")
    lines.push(`    total slots                     ${pad(doc.characterSlots.length, 5)}`)
    for (const [exposure, count] of Object.entries(doc.characterExposureCounts)) lines.push(`    ${exposure.padEnd(24)} ${pad(count, 5)}`)
    lines.push("")
    lines.push("  White factor subfamily exposure")
    const w = doc.whiteSubfamilyCoverage
    if (!w.available) {
        lines.push("    (no white factor domain supplied; every white slot's subfamily is null)")
    } else {
        for (const [family, counts] of Object.entries(w.exposureByFamily)) {
            const total = counts.ANCHORED + counts.FULLY_EXPOSED + counts.FULLY_EXPOSED_SOLE + counts.UNMEASURED
            lines.push(`    ${family.padEnd(10)} total ${pad(total, 4)}  ANCHORED ${pad(counts.ANCHORED, 4)}  FULLY_EXPOSED ${pad(counts.FULLY_EXPOSED, 4)}  SOLE ${pad(counts.FULLY_EXPOSED_SOLE, 3)}  UNMEASURED ${pad(counts.UNMEASURED, 3)}`)
        }
        lines.push(`    unresolved ${pad(w.unresolved, 4)}  ambiguous ${pad(w.ambiguous, 4)}`)
        if (w.unresolvedNames.length > 0) lines.push(`    unresolved names: ${w.unresolvedNames.join(", ")}`)
        if (w.ambiguousNames.length > 0) lines.push(`    ambiguous names: ${w.ambiguousNames.join(", ")}`)
    }
    lines.push("")
    const sole = doc.factorSlots.filter((s) => s.exposure === "FULLY_EXPOSED_SOLE")
    if (sole.length > 0) {
        lines.push(`  Fully-exposed SOLE factor slots (${sole.length}) - one observed carrier, inside the review pool`)
        for (const s of sole) lines.push(`    ${s.factorKey} @${s.starFloor}  (${s.claimStrength})`)
        lines.push("")
    }
    lines.push("  Target coverage")
    for (const s of doc.targetSlots) lines.push(`    ${s.targetProfile.padEnd(24)} ${s.exposure}  (clearing ${s.clearingCarriers}, admitted ${s.admittedCarriers}, anchored ${s.anchoredCarriers})`)
    lines.push("")
    lines.push("  Limits / degradations")
    for (const l of doc.limits) lines.push(`    ${l.code}: ${l.reason}`)
    lines.push("")
    lines.push("  Coverage exposure is STRUCTURE only: an anchored slot cannot be zeroed by any review outcome,")
    lines.push("  an exposed slot is one whose every observed carrier is inside the review pool. Nothing here says a")
    lines.push("  Veteran is valuable, redundant, or safe to transfer. This document is advisory and human-review-only.")
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
        // Freshness binding: refuse to act on a report whose roster scan is not the one expected. This
        // closes the Slice 1 carry-forward at the first consumer where acting on stale evidence bites.
        if (opts.expectRosterScan !== null && report.rosterScanId !== opts.expectRosterScan) {
            throw new Error(`expected roster scan ${opts.expectRosterScan} but the selected ${report.targetProfile} report is ${report.rosterScanId}; refusing to run on unexpected evidence`)
        }
        const domain = loadWhiteFactorDomain(DOMAIN_ASSET)
        doc = buildCapacityCoverage(report, domain)
    } catch (e) {
        console.error(e instanceof Error ? e.message : String(e))
        return 2
    }

    if (opts.json) console.log(JSON.stringify(doc, null, 2))
    else console.log(renderSummary(doc))

    if (!doc.usable) {
        console.error("")
        console.error("The roster snapshot behind this document is NOT trusted-complete: usable=false, pool 0. The empty ledger does NOT mean nothing is at risk.")
    }

    if (opts.out) {
        writeFileSync(opts.out, `${JSON.stringify(doc, null, 2)}\n`, "utf8")
        if (!opts.json) console.log(`\nWrote ${opts.out}`)
    }
    if (opts.exposedOut) {
        writeFileSync(opts.exposedOut, `${JSON.stringify(exposedViewOf(doc), null, 2)}\n`, "utf8")
        if (!opts.json) console.log(`Wrote ${opts.exposedOut}`)
    }

    return doc.usable ? 0 : 1
}

// process.exitCode (not process.exit) so stdio flushes cleanly - process.exit after console writes trips
// a libuv handle assert on Windows node.
try {
    process.exitCode = main(process.argv.slice(2))
} catch (e) {
    console.error(`parent-lab-capacity-coverage failed: ${e instanceof Error ? (e.stack ?? e.message) : String(e)}`)
    process.exitCode = 2
}
