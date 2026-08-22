// ParentLab shadow affinity / lineage advisor - offline, read-only, advisory only.
//
// Answers "for trainee X and target build Y, which owned Veterans are the strongest parent pair, and
// why" from the artifacts PL-R1 already produces: the current roster scan, the Inspiration captures,
// and the protection probe. It adds one new source, the game's own succession-relation tables,
// extracted offline by scripts/generate-succession-relation-data.mjs.
//
// What it does NOT do, by construction: it never selects parents in game, never launches a career,
// never transfers or releases anything, never touches the device, and never rewrites its inputs. It
// also never prints an affinity total: the base relation between the target and each parent is a
// decoded number, the lineage aggregation is not, and the two are kept apart everywhere.
//
// Usage:
//   node scripts/parent-lab-affinity.mjs --roster <roster_scan.jsonl> --inspiration <veteran_inspiration.jsonl>
//        [--protection <veteran_protection.jsonl>] [--relations <succession_relations.json>]
//        --trainee <name> [--distance sprint|mile|medium|long] [--surface turf|dirt]
//        [--style front|pace|late|end] [--stat "Speed,Power"] [--aptitude "..."] [--unique "..."]
//        [--white "..."] [--label "..."]
//        [--trainee <name> ...]            # repeat to add another target build
//        [--top <n>] [--scan-id <id>] [--json] [--out <path>] [--summary-out <path>] [--help]
//
// Exit codes: 0 report built on a trusted snapshot | 1 snapshot not trusted | 2 input/parse failure
//
// Requires node >= 23.6 (native TypeScript type stripping; the logic lives in src/lib/parentLab/ and
// is exercised by the Jest suite).

import { existsSync, readFileSync, statSync, writeFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"
import { buildAffinityAdvisorReport, DEFAULT_TOP_PAIRS } from "../src/lib/parentLab/affinityAdvisor.ts"
import { buildSuccessionRelationIndex, parseSuccessionRelationData } from "../src/lib/parentLab/affinityData.ts"
import { buildInspirationIndex, parseInspirationRecords } from "../src/lib/parentLab/inspiration.ts"
import { buildProtectionInventory, latestTrustedProtectionRecord, parseProtectionRecords } from "../src/lib/parentLab/protection.ts"
import { buildRosterSnapshots, latestTrustedSnapshot, parseRosterScanRecords } from "../src/lib/parentLab/roster.ts"
import { buildRetentionEvidence, buildFactorScarcityIndex } from "../src/lib/parentLab/retentionEvidence.ts"
import { buildTargetBuild, parseTargetDistance, parseTargetRunningStyle, parseTargetSurface, TARGET_DISTANCES, TARGET_RUNNING_STYLES, TARGET_SURFACES } from "../src/lib/parentLab/targetBuild.ts"

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, "..")
const DEFAULT_RELATIONS = join(REPO, "src", "data", "succession_relations.json")

const HELP = `parent-lab-affinity - shadow parent / lineage advisor (read-only, advisory only)

Options:
  --roster <path>       Path to roster_scan.jsonl (required).
  --inspiration <path>  Path to veteran_inspiration.jsonl (required for factor evidence).
  --protection <path>   Path to veteran_protection.jsonl (PL-R2a). Optional; only affects reported gaps.
  --relations <path>    Path to succession_relations.json (default src/data/succession_relations.json).
  --trainee <name>      Starts a target build for this trainee. Repeatable.
  --distance <d>        ${TARGET_DISTANCES.join(" | ")} (applies to the current target).
  --surface <s>         ${TARGET_SURFACES.join(" | ")}.
  --style <s>           ${TARGET_RUNNING_STYLES.join(" | ")}.
  --stat <list>         Comma-separated canonical blue factor names; replaces the distance default.
  --aptitude <list>     Comma-separated canonical pink factor names, added to the implied ones.
  --unique <list>       Comma-separated canonical green factor names.
  --white <list>        Comma-separated canonical white factor names.
  --label <text>        Display label for the current target.
  --top <n>             Pairs listed per target (default ${DEFAULT_TOP_PAIRS}).
  --scan-id <id>        Use this roster scan instead of the newest one.
  --json                Print the raw document as JSON instead of the report.
  --out <path>          Write the full JSON document to a file.
  --summary-out <path>  Write only the per-target summary to a file.
  --help                Show this help.

Nothing is selected, launched, transferred or modified. Exit: 0 trusted snapshot | 1 untrusted | 2 input failure.`

class InputError extends Error {}

function splitList(value) {
    return value
        .split(",")
        .map((s) => s.trim())
        .filter((s) => s.length > 0)
}

function parseArgs(argv) {
    const opts = {
        roster: null,
        inspiration: null,
        protection: null,
        relations: DEFAULT_RELATIONS,
        targets: [],
        top: DEFAULT_TOP_PAIRS,
        scanId: null,
        json: false,
        out: null,
        summaryOut: null,
        help: false,
    }
    const current = () => {
        if (opts.targets.length === 0) throw new InputError("a target option was given before any --trainee")
        return opts.targets[opts.targets.length - 1]
    }
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i]
        const next = () => {
            const v = argv[++i]
            if (v === undefined) throw new InputError(`${arg} requires a value`)
            return v
        }
        switch (arg) {
            case "--roster":
                opts.roster = next()
                break
            case "--inspiration":
                opts.inspiration = next()
                break
            case "--protection":
                opts.protection = next()
                break
            case "--relations":
                opts.relations = next()
                break
            case "--trainee":
                opts.targets.push({ targetTrainee: next(), distance: null, surface: null, runningStyle: null, statFactors: null, aptitudeFactors: null, uniqueFactors: null, whiteFactors: null, label: null })
                break
            case "--distance": {
                const v = next()
                const parsed = parseTargetDistance(v)
                if (!parsed) throw new InputError(`unknown distance "${v}" (expected ${TARGET_DISTANCES.join(" | ")})`)
                current().distance = parsed
                break
            }
            case "--surface": {
                const v = next()
                const parsed = parseTargetSurface(v)
                if (!parsed) throw new InputError(`unknown surface "${v}" (expected ${TARGET_SURFACES.join(" | ")})`)
                current().surface = parsed
                break
            }
            case "--style": {
                const v = next()
                const parsed = parseTargetRunningStyle(v)
                if (!parsed) throw new InputError(`unknown running style "${v}" (expected ${TARGET_RUNNING_STYLES.join(" | ")})`)
                current().runningStyle = parsed
                break
            }
            case "--stat":
                current().statFactors = splitList(next())
                break
            case "--aptitude":
                current().aptitudeFactors = splitList(next())
                break
            case "--unique":
                current().uniqueFactors = splitList(next())
                break
            case "--white":
                current().whiteFactors = splitList(next())
                break
            case "--label":
                current().label = next()
                break
            case "--top": {
                const n = Number(next())
                if (!Number.isInteger(n) || n < 0) throw new InputError("--top requires a non-negative integer")
                opts.top = n
                break
            }
            case "--scan-id":
                opts.scanId = next()
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
                throw new InputError(`unknown argument: ${arg}`)
        }
    }
    return opts
}

function readable(label, path) {
    if (!existsSync(path) || !statSync(path).isFile()) throw new InputError(`${label} path is not a readable file: ${path}`)
    return readFileSync(path, "utf8")
}

function loadRelations(path) {
    if (!existsSync(path)) throw new InputError(`succession relation data not found: ${path} (run scripts/generate-succession-relation-data.mjs)`)
    let raw
    try {
        raw = JSON.parse(readFileSync(path, "utf8"))
    } catch (err) {
        throw new InputError(`succession relation data is not valid JSON: ${err.message}`)
    }
    return buildSuccessionRelationIndex(parseSuccessionRelationData(raw))
}

function pickSnapshot(snapshots, scanId) {
    if (snapshots.length === 0) throw new InputError("no roster scan found in the roster file; run the Veteran Roster Scan diagnostic first")
    if (scanId) {
        const found = snapshots.find((s) => s.scanId === scanId)
        if (!found) throw new InputError(`no roster scan with id ${scanId}. Known ids: ${snapshots.map((s) => s.scanId).join(", ")}`)
        return found
    }
    // The newest TRUSTED scan, not simply the newest: a later incomplete walk must never displace an
    // earlier complete one, because the incomplete one silently shrinks the candidate roster.
    return latestTrustedSnapshot(snapshots) ?? snapshots[0]
}

function pct(n) {
    return `${Math.round(n * 1000) / 10}%`
}

function describeCandidate(c) {
    return `${c.character ?? "unknown"}${c.outfit ? ` [${c.outfit}]` : ""}`
}

function printReport(report) {
    const lines = []
    lines.push("ParentLab shadow affinity / lineage advisor")
    lines.push(`roster scan ${report.rosterScanId}  trusted_complete=${report.rosterTrustedComplete}`)
    lines.push(`capture coverage ${report.coverage.capturedTrusted}/${report.coverage.identifiedRosterEntries} (${pct(report.coverage.coverage)})  account_wide=${report.coverage.accountWide}  distinct factors ${report.coverage.distinctFactors}`)
    lines.push(`relation data: ${report.relationDataProvenance}`)
    lines.push("")
    lines.push("Affinity mechanics")
    for (const row of report.affinityEvidence) {
        lines.push(`  ${row.status.padEnd(18)} ${row.usableInAdvisor ? "used" : "not used"}  ${row.mechanic}`)
    }
    lines.push("")

    for (const target of report.targets) {
        const b = target.build
        lines.push(`=== ${b.label} (${b.id})`)
        lines.push(`  target chara_id ${b.targetCharaId ?? "unresolved"}   priority blue=[${b.priorityStatFactors.join(", ")}] (${b.statPriorityOrigin})  pink=[${b.priorityAptitudeFactors.join(", ")}]  green=[${b.priorityUniqueFactors.join(", ")}]  white=[${b.priorityWhiteFactors.join(", ")}]`)
        lines.push(`  candidates ${target.candidates.total}: identified ${target.candidates.identified}, factors trusted ${target.candidates.selfFactorsTrusted}, relation resolved ${target.candidates.affinityResolved}, same character as target ${target.candidates.sameCharacterAsTarget}, carrying a priority factor ${target.candidates.carryingAnyPriorityFactor}`)
        lines.push(`  median relation points to one parent: ${target.affinityMedianPerParent ?? "n/a"}   pairs evaluated ${target.pairsEvaluated}`)
        lines.push(`  confidence: ${Object.entries(target.confidenceCounts).map(([k, v]) => `${k} ${v}`).join("  ")}`)
        lines.push("")
        lines.push("  Pareto frontier")
        const byKey = new Map(target.pairs.map((p) => [p.pairKey, p]))
        for (const entry of target.frontier) {
            const pair = byKey.get(entry.pairKey)
            const who = pair ? `${describeCandidate(pair.parentA)} + ${describeCandidate(pair.parentB)}` : entry.pairKey
            lines.push(`    ${entry.category.padEnd(24)} ${String(entry.value).padStart(9)}  ties ${String(entry.tiedPairs).padStart(6)}  dominated=${entry.dominatedByOtherPair}  ${who}`)
        }
        for (const entry of target.inactiveFrontierCategories) {
            lines.push(`    ${entry.category.padEnd(24)} ${String(entry.value).padStart(9)}  no winner: ${entry.reason.toLowerCase()}`)
        }
        lines.push("")
        lines.push(`  Top ${target.topPairKeys.length} pairs by balance index (presentation order, not a verdict)`)
        for (const key of target.topPairKeys) {
            const pair = byKey.get(key)
            if (!pair) continue
            const d = pair.dimensions
            lines.push(`    ${describeCandidate(pair.parentA)} + ${describeCandidate(pair.parentB)}`)
            lines.push(
                `      balance ${pair.balanceIndex.toFixed(4)}  confidence ${pair.confidence}  known relation ${pair.knownAffinity.resolved ? pair.knownAffinity.knownPointsTotal : "unresolved"}  parent-to-parent ${pair.knownAffinity.parentToParentRelationPoints ?? "n/a"}`,
            )
            lines.push(`      blue ${d.statCoverageStars}/${d.statStackedStars} stacked  pink ${d.aptitudeCoverageStars}  green ${d.uniqueCoverageStars}  white ${d.whiteCoverageStars}  scarcity ${d.scarcityValue}  legacy ${d.legacySupportStars}  covered ${d.distinctPriorityCoverage} (shared ${pair.sharedPriorityFactors})`)
            lines.push(`      ${pair.reasonCodes.join(", ")}`)
            lines.push(`      ${pair.explanation}`)
        }
        lines.push("")
        lines.push(`  Recommendation: ${target.recommendation.kind}${target.recommendation.pairKey ? ` -> ${target.recommendation.pairKey}` : ""}`)
        lines.push(`    ${target.recommendation.note}`)
        lines.push(`  Missing evidence: ${target.missingEvidence.join(", ")}`)
        lines.push("")
    }
    console.log(lines.join("\n"))
}

function summaryOf(report) {
    return {
        schema: report.schema,
        schemaVersion: report.schemaVersion,
        generatedAt: report.generatedAt,
        rosterScanId: report.rosterScanId,
        coverage: report.coverage,
        targets: report.targets.map((t) => ({
            buildId: t.build.id,
            label: t.build.label,
            targetCharaId: t.build.targetCharaId,
            candidates: t.candidates,
            affinityMedianPerParent: t.affinityMedianPerParent,
            pairsEvaluated: t.pairsEvaluated,
            confidenceCounts: t.confidenceCounts,
            recommendation: t.recommendation.kind,
            frontier: t.frontier.map((f) => ({ category: f.category, value: f.value, tiedPairs: f.tiedPairs, dominatedByOtherPair: f.dominatedByOtherPair, pairKey: f.pairKey })),
            inactiveFrontierCategories: t.inactiveFrontierCategories,
            missingEvidence: t.missingEvidence,
        })),
    }
}

function main(argv) {
    let opts
    try {
        opts = parseArgs(argv)
    } catch (err) {
        console.error(`${err.message}\n`)
        console.error(HELP)
        return 2
    }
    if (opts.help || argv.length === 0) {
        console.log(HELP)
        return 0
    }

    let report
    let snapshot
    try {
        if (!opts.roster) throw new InputError("Missing required --roster <path>.")
        if (!opts.inspiration) throw new InputError("Missing required --inspiration <path>.")
        if (opts.targets.length === 0) throw new InputError("At least one --trainee target is required.")

        const relations = loadRelations(opts.relations)
        const snapshots = buildRosterSnapshots(parseRosterScanRecords(readable("--roster", opts.roster), opts.roster))
        snapshot = pickSnapshot(snapshots, opts.scanId)

        const inspirationIndex = buildInspirationIndex(parseInspirationRecords(readable("--inspiration", opts.inspiration), opts.inspiration))
        const protectionRecord = opts.protection ? latestTrustedProtectionRecord(parseProtectionRecords(readable("--protection", opts.protection), opts.protection)) : null
        const inventory = buildProtectionInventory(protectionRecord, snapshot)

        // Reconciliation against the historical library is deliberately not read here: this stage ranks
        // what the account owns now, and a career's history changes none of a Veteran's factors.
        const evidence = buildRetentionEvidence(snapshot, inspirationIndex, null, inventory.byFingerprint)
        const scarcity = buildFactorScarcityIndex(evidence)
        const builds = opts.targets.map((t) => buildTargetBuild(t, relations))

        report = buildAffinityAdvisorReport({ evidence, scarcity, relations, builds, topCount: opts.top })
    } catch (err) {
        console.error(err instanceof Error ? err.message : String(err))
        return 2
    }

    if (opts.json) console.log(JSON.stringify(report, null, 2))
    else printReport(report)

    if (opts.out) {
        writeFileSync(opts.out, `${JSON.stringify(report, null, 2)}\n`, "utf8")
        if (!opts.json) console.log(`Wrote ${opts.out}`)
    }
    if (opts.summaryOut) {
        writeFileSync(opts.summaryOut, `${JSON.stringify(summaryOf(report), null, 2)}\n`, "utf8")
        if (!opts.json) console.log(`Wrote ${opts.summaryOut}`)
    }

    if (!snapshot.trustedComplete) {
        console.error("")
        console.error("The roster snapshot is NOT trusted-complete, so every account-wide claim above is limited to what was actually enumerated.")
        console.error(`Defects: ${snapshot.defects.join(", ")}`)
    }
    return snapshot.trustedComplete ? 0 : 1
}

// process.exitCode (not process.exit) so stdio flushes cleanly - process.exit after console writes trips
// a libuv handle assert on Windows node.
try {
    process.exitCode = main(process.argv.slice(2))
} catch (e) {
    console.error(`parent-lab-affinity failed: ${e instanceof Error ? (e.stack ?? e.message) : String(e)}`)
    process.exitCode = 2
}
