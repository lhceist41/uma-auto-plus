// ParentLab PL-R2 shadow Veteran retention advisor - offline, read-only, advisory only.
//
// Reads the three artifacts PL-R1 already produces (the current roster scan, the career corpus behind
// the PL-3 Veteran library, and the PL-R1c Inspiration captures) and prints what the evidence
// supports about keeping or eventually releasing each owned Veteran. It never transfers, favorites,
// memos or deletes anything, it never touches the device, and it rewrites none of its inputs.
//
// A recommendation of SAFE_TO_TRANSFER is a label on a report, not an instruction and not an action.
// Expect very few of them, and none at all while capture coverage is partial: the engine refuses to
// make an account-wide scarcity claim it cannot support, and refuses to call anything safe while
// in-game protection (favorite or memo) cannot be excluded from the roster read.
//
// Usage:
//   node scripts/parent-lab-retention.mjs --roster <roster_scan.jsonl> [--careers <careers.jsonl>]
//                                         [--lineage <lineage.jsonl>] [--inspiration <veteran_inspiration.jsonl>]
//                                         [--target <PROFILE>] [--all-targets] [--protect <fingerprint>]
//                                         [--scan-id <id>] [--examples <n>] [--json] [--out <path>]
//                                         [--summary-out <path>] [--help]
//
// Exit codes: 0 report built on a trusted snapshot | 1 snapshot not trusted | 2 input/parse failure
//
// Requires node >= 23.6 (native TypeScript type stripping; the logic lives in src/lib/parentLab/ and
// is exercised by the Jest suite).

import { existsSync, readFileSync, statSync, writeFileSync } from "node:fs"
import { parseCorpus } from "../src/lib/outcomeAnalysis.ts"
import { buildVeteranLibrary } from "../src/lib/parentLab/buildVeteranLibrary.ts"
import { buildInspirationIndex, parseInspirationRecords } from "../src/lib/parentLab/inspiration.ts"
import { parseLineageRecords } from "../src/lib/parentLab/lineage.ts"
import { buildProtectionInventory, latestTrustedProtectionRecord, parseProtectionRecords } from "../src/lib/parentLab/protection.ts"
import { reconcileRoster } from "../src/lib/parentLab/reconcile.ts"
import { buildRosterSnapshots, parseRosterScanRecords } from "../src/lib/parentLab/roster.ts"
import { buildRetentionShadowReport } from "../src/lib/parentLab/retentionAdvisor.ts"
import { buildRetentionEvidence } from "../src/lib/parentLab/retentionEvidence.ts"
import { resolveTargetProfile, TARGET_PROFILE_IDS, TARGET_PROFILES } from "../src/lib/parentLab/retentionTargets.ts"

const HELP = `parent-lab-retention - shadow Veteran retention advisor (read-only, advisory only)

Options:
  --roster <path>       Path to roster_scan.jsonl (required).
  --careers <path>      Path to careers.jsonl, for the historical library and replacement difficulty.
  --lineage <path>      Path to lineage.jsonl, joined into the library by launchTransactionId.
  --inspiration <path>  Path to veteran_inspiration.jsonl, the Spark/factor evidence.
  --protection <path>   Path to veteran_protection.jsonl (PL-R2a). Clears the protection/favorite gates
                        for Veterans the probe proves unprotected, and hard-protects favorited/memoed ones.
  --target <PROFILE>    Target profile: ${TARGET_PROFILE_IDS.join(" | ")}. Default GENERAL_INHERITANCE.
  --all-targets         Build one document per target profile instead of just one.
  --protect <id>        Roster fingerprint to hard-protect by operator instruction. Repeatable.
  --scan-id <id>        Use this roster scan instead of the newest one.
  --examples <n>        How many worked examples to print per section (default 5).
  --json                Print the raw document(s) as JSON instead of the report.
  --out <path>          Write the full JSON document to a file.
  --summary-out <path>  Write only the counts/coverage summary to a file.
  --help                Show this help.

Nothing is transferred, released, favorited or modified. Exit: 0 trusted snapshot | 1 untrusted | 2 input failure.`

function parseArgs(argv) {
    const opts = {
        roster: null,
        careers: null,
        lineage: null,
        inspiration: null,
        protection: null,
        target: "GENERAL_INHERITANCE",
        allTargets: false,
        protect: [],
        scanId: null,
        examples: 5,
        json: false,
        out: null,
        summaryOut: null,
        help: false,
    }
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
            case "--inspiration":
                opts.inspiration = next()
                break
            case "--protection":
                opts.protection = next()
                break
            case "--target":
                opts.target = next()
                break
            case "--all-targets":
                opts.allTargets = true
                break
            case "--protect":
                opts.protect.push(next())
                break
            case "--scan-id":
                opts.scanId = next()
                break
            case "--examples": {
                const n = Number(next())
                if (!Number.isInteger(n) || n < 0) throw new Error("--examples requires a non-negative integer")
                opts.examples = n
                break
            }
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

/** One Veteran's identity line for the worked examples. */
function who(r) {
    return `[${pad(r.scanIndex, 3)}] ${(r.character ?? "?").padEnd(20)} ${(r.outfit ?? "?").padEnd(22)} ${(r.rank ?? "?").padEnd(3)} rating=${pad(r.factorValueSummary.rating ?? "?", 5)}`
}

/**
 * Renders one target document.
 *
 * @param {any} report The retention document.
 * @param {number} examples How many worked examples per section.
 * @returns {string} The report text.
 */
function renderReport(report, examples) {
    const lines = []
    const s = report.scarcity
    lines.push(`=== Shadow retention: ${report.targetProfile} (${TARGET_PROFILES[report.targetProfile].label}) ===`)
    lines.push(`  roster scan           ${report.rosterScanId}`)
    lines.push(`  roster fingerprint    ${report.rosterFingerprint}`)
    lines.push(`  evidence observed at  ${report.generatedAt ? new Date(report.generatedAt).toISOString() : "unknown"}  (newest input observation, not a clock read)`)
    lines.push("")
    lines.push("  Recommendation counts")
    for (const [state, count] of Object.entries(report.counts)) lines.push(`    ${state.padEnd(22)} ${pad(count, 4)}`)
    lines.push("")
    lines.push("  Spark evidence coverage")
    lines.push(`    identified entries    ${s.identifiedRosterEntries}`)
    lines.push(`    trusted captures      ${s.capturedTrusted}  (complete AND every factor name resolved)`)
    lines.push(`    untrusted captures    ${s.capturedUntrusted}`)
    lines.push(`    coverage              ${(s.coverage * 100).toFixed(1)}%`)
    lines.push(`    account-wide claims   ${s.accountWide ? "SUPPORTED" : "NOT SUPPORTED at this coverage"}`)
    lines.push(`    distinct factors seen ${s.entries.length}  unresolved factor reads ${s.unresolvedFactorReads}`)
    if (!s.accountWide) {
        lines.push(`    A factor seen on one of ${s.capturedTrusted} captured Veterans is NOT a factor that exists once`)
        lines.push(`    among ${s.identifiedRosterEntries} owned Veterans. No ACCOUNT_UNIQUE claim is made below.`)
    }
    lines.push("")
    lines.push("  Replacement evidence")
    const re = report.replacementEvidence
    if (!re) {
        lines.push("    NOT SUPPLIED  (no career corpus supplied)")
    } else {
        lines.push(`    BOUND  confirmedVeterans=${re.confirmedVeterans}  traineeCount=${re.traineeCount}  identityCollisions=${re.identityCollisions}`)
        lines.push(`    appVersions           ${re.appVersions.length ? re.appVersions.join(", ") : "(none declared)"}`)
        lines.push(`    newestObservationTs   ${re.newestObservationTs !== null ? new Date(re.newestObservationTs).toISOString() : "none"}`)
    }

    const section = (title, rows, describe) => {
        if (rows.length === 0) return
        lines.push("")
        lines.push(`  ${title} (${rows.length} total, showing ${Math.min(examples, rows.length)})`)
        for (const r of rows.slice(0, examples)) {
            lines.push(`    ${who(r)}`)
            for (const line of describe(r)) lines.push(`        ${line}`)
        }
    }

    const byState = (state) => report.recommendations.filter((r) => r.state === state)
    section("HARD_PROTECT", byState("HARD_PROTECT"), (r) => [
        `reasons: ${r.hardProtectReasons.join(", ")}`,
        `replacement: ${r.replacement.difficulty} (${r.replacement.basis})`,
        `coverage: character carriers ${r.coverageSummary.characterCarriers}, outfit carriers ${r.coverageSummary.characterOutfitCarriers}`,
    ])
    section("KEEP", byState("KEEP"), (r) => [
        `reasons: ${r.keepReasons.join(", ")}`,
        `factors: blue=${r.factorValueSummary.statFactorStars ?? "?"} red=${r.factorValueSummary.aptitudeFactorStars ?? "?"} green=${r.factorValueSummary.uniqueFactorStars ?? "?"} white=${r.factorValueSummary.whiteFactorCount ?? "?"} scarcest=${r.factorValueSummary.scarcestClaim}`,
    ])
    section("QUARANTINE_TRANSFER", byState("QUARANTINE_TRANSFER"), (r) => [`dominated by: ${r.dominators.map((d) => d.explanation).join(" | ")}`, `replacement: ${r.replacement.difficulty}`])
    section("SAFE_TO_TRANSFER", byState("SAFE_TO_TRANSFER"), (r) => [`dominated by: ${r.dominators.map((d) => d.explanation).join(" | ")}`, `every strict gate passed at ${r.confidence} confidence`])
    section("REVIEW", byState("REVIEW"), (r) => [`risk: ${r.riskReasons.join(", ") || "none recorded"}`, `blocked from transfer by: ${r.gateReasons.join(", ")}`])

    const nearMiss = report.recommendations.filter((r) => r.substitutes.length > 0)
    section("Nearest substitutes (no dominance established)", nearMiss, (r) => r.substitutes.slice(0, 2).map((d) => d.explanation))

    const unknown = byState("UNKNOWN")
    section("UNKNOWN", unknown, (r) => [`missing: ${r.unknownEvidence.join(", ")}`, `gates: ${r.gateReasons.join(", ")}`])

    lines.push("")
    lines.push("  Rules deliberately not applied")
    for (const rule of report.inactiveRules) lines.push(`    ${rule.rule}: ${rule.reason}`)
    lines.push("")
    lines.push("  This document is advisory. Nothing was transferred, released, favorited or modified.")
    return lines.join("\n")
}

/** The PL-R2a protection inventory block. Rendered once, not per target. */
function renderProtection(inventory) {
    const lines = []
    const c = inventory.counts
    lines.push("=== Protection inventory (PL-R2a) ===")
    if (!inventory.protectionScanId) {
        lines.push("  no protection probe supplied (--protection). Every Veteran's protection stays UNKNOWN,")
        lines.push("  which blocks the transfer side exactly as before.")
        return lines.join("\n")
    }
    lines.push(`  protection scan       ${inventory.protectionScanId}`)
    lines.push(`  bound to roster scan  ${inventory.rosterScanId}`)
    lines.push(`  compatible            ${inventory.compatible ? "YES" : `NO (${inventory.defects.join(", ")})`}`)
    lines.push(`  favorite    favorite=${c.favorite}  not_favorite=${c.notFavorite}  unknown=${c.favoriteUnknown}`)
    lines.push(`  memo        has_memo=${c.hasMemo}  no_memo=${c.noMemo}  unknown=${c.memoUnknown}`)
    lines.push(`  protection  protected=${c.protected}  not_protected=${c.notProtected}  unknown=${c.protectionUnknown}`)
    if (!inventory.compatible) {
        lines.push("  Incompatible or untrusted, so the probe was NOT applied and every state above is UNKNOWN.")
    }
    return lines.join("\n")
}

/** The counts/coverage-only view, for --summary-out. */
function summaryOf(reports, inventory) {
    return {
        schema: reports[0].schema,
        schemaVersion: reports[0].schemaVersion,
        rosterScanId: reports[0].rosterScanId,
        rosterFingerprint: reports[0].rosterFingerprint,
        generatedAt: reports[0].generatedAt,
        coverage: {
            identifiedRosterEntries: reports[0].scarcity.identifiedRosterEntries,
            capturedTrusted: reports[0].scarcity.capturedTrusted,
            capturedUntrusted: reports[0].scarcity.capturedUntrusted,
            coverage: reports[0].scarcity.coverage,
            accountWide: reports[0].scarcity.accountWide,
            distinctFactors: reports[0].scarcity.entries.length,
        },
        targets: reports.map((r) => ({ targetProfile: r.targetProfile, counts: r.counts })),
        replacementEvidence: reports[0].replacementEvidence,
        protection: inventory
            ? { protectionScanId: inventory.protectionScanId, compatible: inventory.compatible, defects: inventory.defects, counts: inventory.counts }
            : null,
        inactiveRules: reports[0].inactiveRules,
    }
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
    const profiles = opts.allTargets ? TARGET_PROFILE_IDS.map((id) => TARGET_PROFILES[id]) : [resolveTargetProfile(opts.target)]
    if (profiles.some((p) => !p)) {
        console.error(`Unknown --target ${opts.target}. Known profiles: ${TARGET_PROFILE_IDS.join(", ")}`)
        return 2
    }

    let parsed
    // library stays null when no career corpus was supplied, so replacementEvidence can distinguish
    // "no corpus" from "corpus supplied but empty". A supplied --careers always yields a non-null
    // library, even one that admits zero Veterans.
    let library = null
    let inspirationIndex = new Map()
    let protectionRecord = null
    try {
        parsed = parseRosterScanRecords(readable("--roster", opts.roster), opts.roster)
        if (opts.inspiration) inspirationIndex = buildInspirationIndex(parseInspirationRecords(readable("--inspiration", opts.inspiration), opts.inspiration))
        if (opts.protection) protectionRecord = latestTrustedProtectionRecord(parseProtectionRecords(readable("--protection", opts.protection), opts.protection))
        const lineageEvents = opts.lineage ? parseLineageRecords(readable("--lineage", opts.lineage), opts.lineage) : []
        if (opts.careers) {
            const corpus = parseCorpus(readable("--careers", opts.careers), opts.careers)
            library = buildVeteranLibrary({ outcomes: corpus.outcomes, sparks: corpus.sparks, lineageEvents })
        }
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

    // reconcileRoster needs a library object; with no career corpus an empty one resolves every entry
    // to ROSTER_ONLY, which is exactly what "no careers" means. The advisor still receives library=null.
    const reconciliation = reconcileRoster(library ?? buildVeteranLibrary({ outcomes: [], sparks: [] }), snapshot)
    const inventory = buildProtectionInventory(protectionRecord, snapshot)
    const evidence = buildRetentionEvidence(snapshot, inspirationIndex, reconciliation, inventory.byFingerprint)
    const manualProtect = new Set(opts.protect)
    const reports = profiles.map((profile) => buildRetentionShadowReport({ evidence, library, reconciliation, profile, manualProtect, protectionScanId: inventory.protectionScanId }))
    const document = reports.length === 1 ? reports[0] : { schema: reports[0].schema, schemaVersion: reports[0].schemaVersion, reports }

    if (opts.json) console.log(JSON.stringify(document, null, 2))
    else console.log(`${renderProtection(inventory)}\n\n${reports.map((r) => renderReport(r, opts.examples)).join("\n\n")}`)

    if (!snapshot.trustedComplete) {
        console.error("")
        console.error("The roster snapshot is NOT trusted-complete, so every recommendation above is UNKNOWN by construction.")
        console.error(`Defects: ${snapshot.defects.join(", ")}`)
    }

    if (opts.out) {
        writeFileSync(opts.out, `${JSON.stringify(document, null, 2)}\n`, "utf8")
        if (!opts.json) console.log(`\nWrote ${opts.out}`)
    }
    if (opts.summaryOut) {
        writeFileSync(opts.summaryOut, `${JSON.stringify(summaryOf(reports, inventory), null, 2)}\n`, "utf8")
        if (!opts.json) console.log(`Wrote ${opts.summaryOut}`)
    }

    return snapshot.trustedComplete ? 0 : 1
}

// process.exitCode (not process.exit) so stdio flushes cleanly - process.exit after console writes trips
// a libuv handle assert on Windows node.
try {
    process.exitCode = main(process.argv.slice(2))
} catch (e) {
    console.error(`parent-lab-retention failed: ${e instanceof Error ? (e.stack ?? e.message) : String(e)}`)
    process.exitCode = 2
}
