// ParentLab PL-R3 transfer quarantine and approval CLI - offline, read-only, advisory only.
//
// Reads the retention documents `parent-lab-retention --out` writes, folds them into a quarantine
// ledger, validates candidate batches, and manages approval manifests. It never transfers, releases,
// favorites, memos or deletes anything, it never touches the device, and it rewrites none of its
// inputs. There is no executor here: an APPROVED manifest is an intent, and nothing in this
// repository can act on one.
//
// Approval is explicit or it does not happen. There is no "approve all", no default candidate set and
// no path that promotes a draft without an `approve <approvalId>` command naming exactly one manifest.
//
// Usage:
//   node scripts/parent-lab-quarantine.mjs report   --snapshot <retention.json> [--snapshot ...]
//                                                   [--manifests <store.jsonl>] [--out <path>]
//                                                   [--ledger-out <path>] [--json]
//   node scripts/parent-lab-quarantine.mjs draft    --snapshot ... --candidate <key> [--candidate ...]
//                                                   --manifests <store.jsonl> [--now <epochMs>]
//   node scripts/parent-lab-quarantine.mjs approve  <approvalId> --snapshot ... --manifests <store.jsonl>
//   node scripts/parent-lab-quarantine.mjs revoke   <approvalId> --manifests <store.jsonl>
//   node scripts/parent-lab-quarantine.mjs validate <approvalId> --snapshot ... --manifests <store.jsonl>
//
// Exit codes: 0 command succeeded | 1 refused on evidence (batch invalid, manifest stale/blocked)
//             2 input/parse failure
//
// Requires node >= 23.6 (native TypeScript type stripping; the logic lives in src/lib/parentLab/ and
// is exercised by the Jest suite).

import { appendFileSync, existsSync, readFileSync, statSync, writeFileSync } from "node:fs"
import { approveManifest, draftApprovalManifest, latestManifests, markStaleIfInvalid, revokeManifest, validateApprovedManifest } from "../src/lib/parentLab/approvalManifest.ts"
import { validateTransferBatch } from "../src/lib/parentLab/quarantineBatch.ts"
import { applyApprovals, buildQuarantineLedger, DEFAULT_QUARANTINE_POLICY } from "../src/lib/parentLab/quarantineLedger.ts"
import { buildAdvisorSnapshot, retentionReportsOf } from "../src/lib/parentLab/quarantineSnapshot.ts"
import { PARENTLAB_QUARANTINE_SCHEMA, PARENTLAB_QUARANTINE_SCHEMA_VERSION, QUARANTINE_STATUSES } from "../src/lib/parentLab/quarantineTypes.ts"
import { RETENTION_STATES } from "../src/lib/parentLab/retentionTypes.ts"

const COMMANDS = ["report", "draft", "approve", "revoke", "validate"]

const HELP = `parent-lab-quarantine - transfer quarantine ledger and manual approval manifests

Commands:
  report                Fold the supplied snapshots into a ledger and print the current-account view.
  draft                 Validate a candidate batch and write a DRAFT manifest. Refused if unsafe.
  approve <approvalId>  Revalidate that exact manifest and, only on a clean pass, mark it APPROVED.
  revoke <approvalId>   Withdraw a manifest. Terminal.
  validate <approvalId> Revalidate a manifest against the latest snapshot. Prints VALID/STALE/BLOCKED.

Options:
  --snapshot <path>     A retention document from parent-lab-retention --out. Repeatable; one per
                        roster walk. Maturity counts distinct roster scans, so replaying one file
                        never advances a candidate.
  --manifests <path>    Append-only approval manifest store (JSONL). Required for every command that
                        reads or writes a manifest.
  --candidate <key>     Candidate key to draft. Repeatable. Required by draft; there is no default set.
  --now <epochMs>       Explicit transition time, so a draft or approval is reproducible. Defaults to
                        the wall clock. Never used in any digest.
  --ledger-out <path>   Write the full ledger JSON.
  --out <path>          Write the current-account report JSON (report command).
  --json                Print JSON instead of the rendered report.
  --help                Show this help.

Nothing is transferred, released, favorited or modified. This tool has no executor.`

function parseArgs(argv) {
    const opts = { command: null, approvalId: null, snapshots: [], manifests: null, candidates: [], now: null, ledgerOut: null, out: null, json: false, help: false }
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i]
        const next = () => {
            const v = argv[++i]
            if (v === undefined) throw new Error(`${arg} requires a value`)
            return v
        }
        switch (arg) {
            case "--snapshot":
                opts.snapshots.push(next())
                break
            case "--manifests":
                opts.manifests = next()
                break
            case "--candidate":
                opts.candidates.push(next())
                break
            case "--now": {
                const n = Number(next())
                if (!Number.isFinite(n)) throw new Error("--now requires an epoch millisecond value")
                opts.now = n
                break
            }
            case "--ledger-out":
                opts.ledgerOut = next()
                break
            case "--out":
                opts.out = next()
                break
            case "--json":
                opts.json = true
                break
            case "--help":
            case "-h":
                opts.help = true
                break
            default:
                if (arg.startsWith("--")) throw new Error(`unknown argument: ${arg}`)
                else if (opts.command === null) opts.command = arg
                else if (opts.approvalId === null) opts.approvalId = arg
                else throw new Error(`unexpected positional argument: ${arg}`)
        }
    }
    return opts
}

function readable(label, path) {
    if (!existsSync(path) || !statSync(path).isFile()) throw new Error(`${label} path is not a readable file: ${path}`)
    return readFileSync(path, "utf8")
}

/** One snapshot per supplied retention document. A parse failure is fatal: a document that cannot be
 * read must never become an absent snapshot, because absence reads as "the account had nothing". */
function loadSnapshots(paths) {
    return paths.map((path) => {
        let parsed
        try {
            parsed = JSON.parse(readable("--snapshot", path))
        } catch (e) {
            throw new Error(`--snapshot ${path} is not valid JSON: ${e instanceof Error ? e.message : String(e)}`)
        }
        return buildAdvisorSnapshot(retentionReportsOf(parsed))
    })
}

/** The append-only manifest store, collapsed to the current state of each approval id. */
function loadManifests(path) {
    if (!path || !existsSync(path)) return []
    const records = []
    const lines = readFileSync(path, "utf8").split("\n")
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim()
        if (!line) continue
        try {
            const obj = JSON.parse(line)
            if (obj && obj.schema === PARENTLAB_QUARANTINE_SCHEMA && typeof obj.approvalId === "string") records.push(obj)
        } catch {
            throw new Error(`${path}:${i + 1} is not valid JSON. The manifest store is append-only and must not be hand-edited.`)
        }
    }
    return latestManifests(records)
}

/** Appends a manifest state. The store is never rewritten, so every transition stays auditable. */
function appendManifest(path, manifest) {
    if (!path) throw new Error("--manifests <path> is required to record an approval transition")
    appendFileSync(path, `${JSON.stringify(manifest)}\n`, "utf8")
}

function pad(value, width) {
    return String(value).padStart(width)
}

/**
 * The current-account report.
 *
 * Deterministic: every field is derived from the snapshots and the manifest store, and no wall clock
 * is read, so rebuilding it from the same inputs produces a byte-identical document.
 */
function currentAccountReport(ledger, snapshot, manifests) {
    const candidates = snapshot ? [...snapshot.candidates.values()] : []
    const counts = Object.fromEntries(QUARANTINE_STATUSES.map((s) => [s, ledger.counts[s]]))

    const stateDistribution = (snapshot?.targetProfiles ?? []).map((targetProfile) => ({
        targetProfile,
        counts: Object.fromEntries(RETENTION_STATES.map((state) => [state, candidates.filter((c) => c.perTarget.some((t) => t.targetProfile === targetProfile && t.state === state)).length])),
    }))

    const blockerHistogram = {}
    for (const c of candidates) for (const b of c.blockers) blockerHistogram[b] = (blockerHistogram[b] ?? 0) + 1

    const eligibleNow = candidates.filter((c) => c.eligible).length
    const why = []
    if (snapshot === null) {
        why.push("no snapshot was supplied, so nothing could be evaluated")
    } else if (eligibleNow === 0) {
        for (const d of stateDistribution) {
            const safe = d.counts.SAFE_TO_TRANSFER
            const nonZero = Object.entries(d.counts)
                .filter(([, n]) => n > 0)
                .map(([state, n]) => `${state}=${n}`)
                .join(", ")
            why.push(`${safe} of ${candidates.length} Veterans reach SAFE_TO_TRANSFER under ${d.targetProfile}; the PL-R2 state distribution there is ${nonZero}`)
        }
        for (const reason of Object.keys(blockerHistogram).sort()) {
            why.push(`${blockerHistogram[reason]} of ${candidates.length} Veterans carry the blocker ${reason}`)
        }
        if (ledger.snapshots.length < ledger.policy.requiredConsecutiveEligibleSnapshots) {
            why.push(`only ${ledger.snapshots.length} distinct roster snapshot(s) exist, below the ${ledger.policy.requiredConsecutiveEligibleSnapshots} a candidate would need to mature`)
        }
    }

    return {
        schema: PARENTLAB_QUARANTINE_SCHEMA,
        schemaVersion: PARENTLAB_QUARANTINE_SCHEMA_VERSION,
        policy: ledger.policy,
        latestSnapshot: snapshot
            ? {
                  snapshotId: snapshot.snapshotId,
                  rosterScanId: snapshot.rosterScanId,
                  rosterFingerprint: snapshot.rosterFingerprint,
                  protectionScanId: snapshot.protectionScanId,
                  observedAt: snapshot.observedAt,
                  targetProfiles: snapshot.targetProfiles,
                  defects: snapshot.defects,
              }
            : null,
        currentRoster: candidates.length,
        unidentifiedEntries: snapshot?.unidentifiedEntries ?? 0,
        advisorEvidenceComplete: snapshot !== null && snapshot.rosterTrusted && snapshot.accountWide && snapshot.defects.length === 0,
        accountWide: snapshot?.accountWide ?? false,
        snapshotsFolded: ledger.snapshots.length,
        duplicateSnapshotsIgnored: ledger.duplicateSnapshotsIgnored,
        eligibleNow,
        trackedNow: candidates.filter((c) => c.tracked).length,
        ledgerCounts: counts,
        ledgerEntries: ledger.entries.length,
        stateDistribution,
        blockerHistogram: Object.fromEntries(Object.keys(blockerHistogram).sort().map((k) => [k, blockerHistogram[k]])),
        whyZeroCandidates: why,
        manifests: manifests.map((m) => ({ approvalId: m.approvalId, humanApprovalStatus: m.humanApprovalStatus, candidateCount: m.candidateKeys.length, batchEvidenceDigest: m.batchEvidenceDigest })),
    }
}

function renderReport(report, ledger) {
    const lines = []
    lines.push("=== ParentLab PL-R3: transfer quarantine ===")
    if (!report.latestSnapshot) {
        lines.push("  no snapshots supplied. Pass --snapshot <retention.json> at least once.")
        return lines.join("\n")
    }
    const s = report.latestSnapshot
    lines.push(`  latest roster scan      ${s.rosterScanId}`)
    lines.push(`  roster fingerprint      ${s.rosterFingerprint}`)
    lines.push(`  protection scan         ${s.protectionScanId ?? "none supplied"}`)
    lines.push(`  evidence observed at    ${s.observedAt ? new Date(s.observedAt).toISOString() : "unknown"}  (newest input observation, not a clock read)`)
    lines.push(`  target profiles         ${s.targetProfiles.join(", ")}`)
    lines.push(`  snapshot defects        ${s.defects.length === 0 ? "none" : s.defects.join(", ")}`)
    lines.push("")
    lines.push(`  current roster          ${report.currentRoster}`)
    lines.push(`  advisor evidence        ${report.advisorEvidenceComplete ? "COMPLETE" : "INCOMPLETE"}`)
    lines.push(`  account-wide scarcity   ${report.accountWide ? "SUPPORTED" : "NOT SUPPORTED"}`)
    lines.push(`  snapshots folded        ${report.snapshotsFolded}${report.duplicateSnapshotsIgnored.length ? `  (${report.duplicateSnapshotsIgnored.length} repeated scan id(s) ignored for maturity)` : ""}`)
    lines.push(`  maturity policy         ${report.policy.requiredConsecutiveEligibleSnapshots} consecutive eligible snapshots at ${report.policy.requiredConfidence} confidence`)
    lines.push("")
    lines.push("  Quarantine ledger")
    lines.push(`    eligible now          ${pad(report.eligibleNow, 4)}`)
    lines.push(`    tracked now           ${pad(report.trackedNow, 4)}`)
    for (const status of QUARANTINE_STATUSES) lines.push(`    ${status.padEnd(21)} ${pad(report.ledgerCounts[status], 4)}`)
    lines.push("")
    lines.push("  PL-R2 state distribution in the latest snapshot")
    for (const d of report.stateDistribution) {
        const nonZero = Object.entries(d.counts)
            .filter(([, n]) => n > 0)
            .map(([state, n]) => `${state}=${n}`)
            .join("  ")
        lines.push(`    ${d.targetProfile.padEnd(21)} ${nonZero}`)
    }
    if (Object.keys(report.blockerHistogram).length > 0) {
        lines.push("")
        lines.push("  Approval blockers across the roster")
        for (const [reason, n] of Object.entries(report.blockerHistogram)) lines.push(`    ${reason.padEnd(38)} ${pad(n, 4)}`)
    }
    if (report.whyZeroCandidates.length > 0) {
        lines.push("")
        lines.push("  Why there are no approval candidates")
        for (const line of report.whyZeroCandidates) lines.push(`    ${line}`)
    }
    const interesting = ledger.entries.filter((e) => e.status !== "EXPIRED").slice(0, 20)
    if (interesting.length > 0) {
        lines.push("")
        lines.push(`  Ledger entries (${ledger.entries.length} total, showing ${interesting.length})`)
        for (const e of interesting) {
            lines.push(`    ${e.status.padEnd(11)} ${e.candidateKey}  ${e.candidateRef.character ?? "?"} (${e.candidateRef.outfit ?? "?"})`)
            lines.push(`        consecutive=${e.consecutiveEligibleSnapshots}  reasons: ${e.statusReasons.join(", ")}`)
        }
    }
    if (report.manifests.length > 0) {
        lines.push("")
        lines.push("  Approval manifests")
        for (const m of report.manifests) lines.push(`    ${m.humanApprovalStatus.padEnd(9)} ${m.approvalId}  ${m.candidateCount} candidate(s)`)
    }
    lines.push("")
    lines.push("  Advisory only. Nothing was transferred, released, favorited or modified, and this tool")
    lines.push("  has no executor: an APPROVED manifest records an intent and acts on nothing.")
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
    if (opts.help || opts.command === null) {
        console.log(HELP)
        return opts.command === null && !opts.help ? 2 : 0
    }
    if (!COMMANDS.includes(opts.command)) {
        console.error(`Unknown command ${opts.command}. Known commands: ${COMMANDS.join(", ")}\n`)
        console.error(HELP)
        return 2
    }

    let snapshots
    let stored
    try {
        snapshots = loadSnapshots(opts.snapshots)
        stored = loadManifests(opts.manifests)
    } catch (e) {
        console.error(e instanceof Error ? e.message : String(e))
        return 2
    }

    const ledger = applyApprovals(buildQuarantineLedger(snapshots, DEFAULT_QUARANTINE_POLICY), stored)
    const latest = snapshots.length > 0 ? (snapshots.find((s) => s.snapshotId === ledger.latestSnapshotId) ?? null) : null
    const now = opts.now ?? Date.now()

    if (opts.ledgerOut) writeFileSync(opts.ledgerOut, `${JSON.stringify({ ...ledger, snapshots: ledger.snapshots }, null, 2)}\n`, "utf8")

    if (opts.command === "report") {
        const report = currentAccountReport(ledger, latest, stored)
        if (opts.json) console.log(JSON.stringify(report, null, 2))
        else console.log(renderReport(report, ledger))
        if (opts.out) {
            writeFileSync(opts.out, `${JSON.stringify(report, null, 2)}\n`, "utf8")
            if (!opts.json) console.log(`\nWrote ${opts.out}`)
        }
        return 0
    }

    if (opts.command === "draft") {
        if (latest === null) {
            console.error("draft requires at least one --snapshot.")
            return 2
        }
        if (opts.candidates.length === 0) {
            console.error("draft requires at least one --candidate <key>. There is no default candidate set and no approve-all.")
            return 2
        }
        const result = draftApprovalManifest({ ledger, snapshot: latest, candidateKeys: opts.candidates, createdAt: now })
        if (!result.manifest) {
            console.error("Batch refused. No draft was written.")
            for (const r of result.validation.rejections) console.error(`  ${r.reason}${r.candidateKey ? ` [${r.candidateKey}]` : ""}: ${r.explanation}`)
            if (opts.json) console.log(JSON.stringify(result.validation, null, 2))
            return 1
        }
        appendManifest(opts.manifests, result.manifest)
        if (opts.json) console.log(JSON.stringify(result.manifest, null, 2))
        else {
            console.log(`Drafted ${result.manifest.approvalId} over ${result.manifest.candidateKeys.length} candidate(s).`)
            console.log("A DRAFT authorizes nothing. Approve it explicitly:")
            console.log(`  node scripts/parent-lab-quarantine.mjs approve ${result.manifest.approvalId} --snapshot <...> --manifests ${opts.manifests}`)
        }
        return 0
    }

    if (opts.approvalId === null) {
        console.error(`${opts.command} requires an <approvalId>. Approval is always explicit about which manifest it names.`)
        return 2
    }
    const manifest = stored.find((m) => m.approvalId === opts.approvalId) ?? null
    if (!manifest) {
        console.error(`No manifest ${opts.approvalId} in ${opts.manifests ?? "(no store supplied)"}.`)
        return 2
    }

    if (opts.command === "revoke") {
        const revoked = revokeManifest(manifest, now)
        appendManifest(opts.manifests, revoked)
        console.log(`Revoked ${revoked.approvalId}. It now binds nothing.`)
        return 0
    }

    if (latest === null) {
        console.error(`${opts.command} requires at least one --snapshot: a manifest is only ever judged against current evidence.`)
        return 2
    }

    if (opts.command === "validate") {
        const validation = validateApprovedManifest({ manifest, ledger, snapshot: latest })
        if (opts.json) console.log(JSON.stringify(validation, null, 2))
        else {
            console.log(`${validation.verdict}  ${manifest.approvalId} (${manifest.humanApprovalStatus}) against ${latest.rosterScanId}`)
            for (const reason of validation.reasons) console.log(`  ${reason}: ${(validation.detail[reason] ?? []).join(", ")}`)
        }
        // An APPROVED manifest that no longer validates is written back as STALE, so the store cannot
        // keep presenting a live approval over evidence that has moved.
        const staled = markStaleIfInvalid({ manifest, ledger, snapshot: latest }, now)
        if (staled.manifest.humanApprovalStatus !== manifest.humanApprovalStatus) {
            appendManifest(opts.manifests, staled.manifest)
            console.log(`Manifest ${manifest.approvalId} moved APPROVED -> STALE.`)
        }
        return validation.verdict === "VALID" ? 0 : 1
    }

    // approve
    const transition = approveManifest({ manifest, ledger, snapshot: latest, at: now })
    if (transition.manifest.humanApprovalStatus !== manifest.humanApprovalStatus) appendManifest(opts.manifests, transition.manifest)
    if (opts.json) console.log(JSON.stringify(transition, null, 2))
    else {
        console.log(`${transition.manifest.approvalId} is now ${transition.manifest.humanApprovalStatus} (revalidation ${transition.validation.verdict}).`)
        for (const reason of transition.validation.reasons) console.log(`  ${reason}: ${(transition.validation.detail[reason] ?? []).join(", ")}`)
        if (transition.manifest.humanApprovalStatus === "APPROVED") {
            console.log("This records an approval intent only. Nothing in this repository can act on it, and it must be")
            console.log("revalidated against a fresh snapshot before any future executor could.")
        }
    }
    return transition.manifest.humanApprovalStatus === "APPROVED" ? 0 : 1
}

// process.exitCode (not process.exit) so stdio flushes cleanly - process.exit after console writes trips
// a libuv handle assert on Windows node.
try {
    process.exitCode = main(process.argv.slice(2))
} catch (e) {
    console.error(`parent-lab-quarantine failed: ${e instanceof Error ? (e.stack ?? e.message) : String(e)}`)
    process.exitCode = 2
}
