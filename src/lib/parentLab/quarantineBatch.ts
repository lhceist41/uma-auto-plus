// ParentLab PL-R3 - the transfer batch validator. Pure, offline, deterministic, read-only.
//
// A set of individually safe removals is not a safe removal. Every proof PL-R2 makes about one
// Veteran is a statement about the account AS IT STANDS: "a peer dominates it", "another Veteran
// carries this character", "this factor has other carriers". Take several Veterans out at once and
// each of those proofs can be quietly invalidated by a different member of the same batch.
//
// So the batch is re-checked as a batch: the account is recomputed as it WOULD BE after the whole
// removal, and every claim that made a member eligible has to survive that recomputation. Nothing
// here trusts that concatenating safe things yields a safe thing.
//
// Two failure modes are called out by name because they are the ones that look fine in a list view:
//
//   mutual dominance - A is redundant because B exists, B is redundant because A exists, and the
//     batch contains both. Each row reads "dominated by a peer" and the account loses the pair.
//   last carrier by aggregation - no single member is the last carrier of anything, but between them
//     the batch holds every copy of a factor. Only a post-batch recompute over the full carrier
//     lists can see it, which is why the retention document now carries every self factor.

import { contentHash128 } from "./identity.ts"
import { ledgerEntry } from "./quarantineLedger.ts"
import type { AdvisorSnapshot, BatchRejection, BatchRejectionReason, BatchValidation, QuarantineLedger, RetainedCoverage, SnapshotCandidate } from "./quarantineTypes.ts"
import { TARGET_PROFILE_IDS, TARGET_PROFILES } from "./retentionTargets.ts"

/** Star floors a factor's carrier count is checked at. Matches the scarcity index's own floors. */
const STAR_FLOORS = [1, 2, 3] as const

function reject(reason: BatchRejectionReason, candidateKey: string | null, detail: readonly string[], explanation: string): BatchRejection {
    return { reason, candidateKey, detail: [...detail].sort(), explanation }
}

/** Carrier counts by `factorKey@floor` over a candidate set. Only trusted factor sets contribute. */
function factorCarriers(candidates: readonly SnapshotCandidate[]): Map<string, number> {
    const counts = new Map<string, number>()
    for (const c of candidates) {
        if (!c.selfFactors) continue
        const seen = new Set<string>()
        for (const f of c.selfFactors) {
            for (const floor of STAR_FLOORS) {
                if (f.stars < floor) continue
                const key = `${f.factorKey}@${floor}`
                // One Veteran carrying the same factor twice is still one Veteran that can pass it on.
                if (seen.has(key)) continue
                seen.add(key)
                counts.set(key, (counts.get(key) ?? 0) + 1)
            }
        }
    }
    return counts
}

function characterCarriers(candidates: readonly SnapshotCandidate[]): Map<string, number> {
    const counts = new Map<string, number>()
    for (const c of candidates) {
        if (!c.characterKey) continue
        counts.set(c.characterKey, (counts.get(c.characterKey) ?? 0) + 1)
    }
    return counts
}

function targetCarriers(candidates: readonly SnapshotCandidate[]): Map<string, number> {
    const counts = new Map<string, number>()
    for (const id of TARGET_PROFILE_IDS) if (TARGET_PROFILES[id].aptitudeGate !== null) counts.set(id, 0)
    for (const c of candidates) for (const id of c.targetsCovered) if (counts.has(id)) counts.set(id, (counts.get(id) as number) + 1)
    return counts
}

/**
 * Cycles in the "is only kept safe by" graph, restricted to the batch.
 *
 * The edge is candidate -> dominator, drawn only when the dominator is itself in the batch. A cycle
 * means no removal order exists in which every member still has its proof, so the set is refused
 * even when each member also has a dominator outside the batch: a circular transfer set is exactly
 * the shape where "each of these is redundant" is true one at a time and false together.
 */
function circularMembers(batch: readonly SnapshotCandidate[], byFingerprint: ReadonlyMap<string, readonly string[]>): readonly string[] {
    const inBatch = new Set(batch.map((c) => c.candidateKey))
    const edges = new Map<string, string[]>()
    for (const c of batch) {
        const targets = new Set<string>()
        for (const t of c.perTarget) for (const fp of t.dominatorFingerprints) for (const key of byFingerprint.get(fp) ?? []) if (inBatch.has(key)) targets.add(key)
        edges.set(c.candidateKey, [...targets].sort())
    }
    const found = new Set<string>()
    const state = new Map<string, 0 | 1 | 2>()
    const stack: string[] = []
    const visit = (node: string): void => {
        state.set(node, 1)
        stack.push(node)
        for (const next of edges.get(node) ?? []) {
            const s = state.get(next) ?? 0
            if (s === 1) for (const member of stack.slice(stack.indexOf(next))) found.add(member)
            else if (s === 0) visit(next)
        }
        stack.pop()
        state.set(node, 2)
    }
    for (const key of [...inBatch].sort()) if ((state.get(key) ?? 0) === 0) visit(key)
    return [...found].sort()
}

/**
 * Validates one candidate set against a ledger and the snapshot the ledger's latest entry came from.
 *
 * Pure: it reads, it decides, it writes nothing and it touches no device. `ok` is true only when the
 * rejection list is empty, and the retained-coverage block is reported either way so a refusal can
 * be read without re-running anything.
 */
export function validateTransferBatch(ledger: QuarantineLedger, snapshot: AdvisorSnapshot, candidateKeys: readonly string[]): BatchValidation {
    const rejections: BatchRejection[] = []
    const all = [...snapshot.candidates.values()]

    const seen = new Set<string>()
    const duplicates = new Set<string>()
    for (const key of candidateKeys) {
        if (seen.has(key)) duplicates.add(key)
        seen.add(key)
    }
    const keys = [...seen].sort()
    if (keys.length === 0) rejections.push(reject("EMPTY_BATCH", null, [], "no candidates were named, so there is nothing to validate"))
    for (const key of [...duplicates].sort()) rejections.push(reject("DUPLICATE_CANDIDATE_IN_BATCH", key, [key], "the same candidate was named more than once"))

    if (!snapshot.rosterTrusted || !snapshot.accountWide || snapshot.defects.length > 0) {
        rejections.push(
            reject(
                "SNAPSHOT_NOT_TRUSTED",
                null,
                snapshot.defects,
                `the latest snapshot cannot support a removal decision: rosterTrusted=${snapshot.rosterTrusted}, accountWide=${snapshot.accountWide}, defects=${snapshot.defects.length}`,
            ),
        )
    }

    const batch: SnapshotCandidate[] = []
    for (const key of keys) {
        const entry = ledgerEntry(ledger, key)
        const candidate = snapshot.candidates.get(key) ?? null
        if (!entry) rejections.push(reject("CANDIDATE_NOT_IN_LEDGER", key, [key], "no quarantine history exists for this candidate"))
        if (!candidate) {
            rejections.push(reject("CANDIDATE_NOT_IN_LATEST_SNAPSHOT", key, [key], "the candidate is not in the latest roster snapshot"))
            continue
        }
        batch.push(candidate)
        if (entry && entry.status !== "MATURE" && entry.status !== "APPROVED") {
            rejections.push(reject("CANDIDATE_NOT_MATURE", key, [entry.status, ...entry.statusReasons], `quarantine status is ${entry.status}, and only a MATURE candidate may be drafted`))
        }
        if (candidate.identityMultiplicity > 1) {
            rejections.push(reject("AMBIGUOUS_ROSTER_IDENTITY", key, [candidate.ref.rosterFingerprint], `${candidate.identityMultiplicity} roster entries share this fingerprint, so it names no single Veteran`))
        }
        const hard = [...new Set(candidate.perTarget.flatMap((t) => t.hardProtectReasons))].sort()
        if (hard.length > 0) rejections.push(reject("HARD_PROTECT_IN_BATCH", key, hard, "the candidate carries a merit protection rule and must not be in a transfer batch"))
    }

    const batchKeys = new Set(batch.map((c) => c.candidateKey))
    const batchFingerprints = new Set(batch.map((c) => c.ref.rosterFingerprint))
    const retained = all.filter((c) => !batchKeys.has(c.candidateKey))

    // Each member's own redundancy proof has to survive the removal of every other member. The two
    // ways it can fail read very differently to a human, so they are explained separately: a proof
    // eaten by the batch is a batch composition problem, while a proof that never existed means the
    // candidate should not have been drafted at all.
    for (const c of batch) {
        const never: string[] = []
        const eaten: string[] = []
        for (const t of c.perTarget) {
            if (t.dominatorFingerprints.length === 0) never.push(t.targetProfile)
            else if (t.dominatorFingerprints.every((fp) => batchFingerprints.has(fp))) eaten.push(t.targetProfile)
        }
        if (eaten.length > 0) {
            rejections.push(reject("DOMINATOR_REMOVED_BY_BATCH", c.candidateKey, eaten, `every peer that made this candidate redundant under ${eaten.join(", ")} is itself in the batch`))
        }
        if (never.length > 0) {
            rejections.push(reject("DOMINATOR_REMOVED_BY_BATCH", c.candidateKey, never, `no peer dominates this candidate under ${never.join(", ")}, so its redundancy was never established`))
        }
    }

    const byFingerprint = new Map<string, string[]>()
    for (const c of all) byFingerprint.set(c.ref.rosterFingerprint, [...(byFingerprint.get(c.ref.rosterFingerprint) ?? []), c.candidateKey])
    const circular = circularMembers(batch, byFingerprint)
    if (circular.length > 0) rejections.push(reject("CIRCULAR_SUBSTITUTE_DEPENDENCE", null, circular, "candidates in this batch dominate each other in a cycle, so no removal order leaves every proof standing"))

    const factorsBefore = factorCarriers(all)
    const factorsAfter = factorCarriers(retained)
    const lostFactors = [...factorsBefore.keys()].filter((k) => (factorsBefore.get(k) ?? 0) > 0 && (factorsAfter.get(k) ?? 0) === 0).sort()
    if (lostFactors.length > 0) {
        rejections.push(reject("BATCH_REMOVES_LAST_FACTOR_CARRIER", null, lostFactors, `the batch holds every remaining carrier of ${lostFactors.length} factor/star combination(s)`))
    }

    const charactersBefore = characterCarriers(all)
    const charactersAfter = characterCarriers(retained)
    const lostCharacters = [...charactersBefore.keys()].filter((k) => (charactersAfter.get(k) ?? 0) === 0).sort()
    if (lostCharacters.length > 0) {
        rejections.push(reject("BATCH_REMOVES_LAST_CHARACTER_SOURCE", null, lostCharacters, `the batch holds every remaining Veteran of ${lostCharacters.length} character(s)`))
    }

    const targetsBefore = targetCarriers(all)
    const targetsAfter = targetCarriers(retained)
    const lostTargets = [...targetsBefore.keys()].filter((k) => (targetsBefore.get(k) ?? 0) > 0 && (targetsAfter.get(k) ?? 0) === 0).sort()
    if (lostTargets.length > 0) {
        rejections.push(reject("BATCH_REMOVES_LAST_TARGET_COVERAGE", null, lostTargets, `no Veteran would still clear the aptitude gate for ${lostTargets.join(", ")}`))
    }

    const retainedCoverage: RetainedCoverage = {
        rosterBefore: all.length,
        rosterAfter: retained.length,
        distinctFactorsBefore: new Set([...factorsBefore.keys()].map((k) => k.split("@")[0])).size,
        distinctFactorsAfter: new Set([...factorsAfter.keys()].filter((k) => (factorsAfter.get(k) ?? 0) > 0).map((k) => k.split("@")[0])).size,
        charactersBefore: charactersBefore.size,
        charactersAfter: [...charactersAfter.values()].filter((v) => v > 0).length,
        targetsCoveredAfter: [...targetsAfter.entries()]
            .filter(([, v]) => v > 0)
            .map(([k]) => k)
            .sort(),
    }

    return {
        ok: rejections.length === 0,
        candidateKeys: keys,
        rejections: rejections.sort((a, b) => (a.reason < b.reason ? -1 : a.reason > b.reason ? 1 : ((a.candidateKey ?? "") < (b.candidateKey ?? "") ? -1 : 1))),
        retainedCoverage,
        batchEvidenceDigest: keys.length === 0 ? "" : batchEvidenceDigestOf(snapshot, keys),
    }
}

/**
 * The batch's semantic digest: the members' own evidence digests plus the account facts the batch
 * decision rests on. Deterministic, and deliberately free of clocks, scan ids and approval ids, so a
 * manifest can be compared against a later snapshot by meaning rather than by provenance.
 */
export function batchEvidenceDigestOf(snapshot: AdvisorSnapshot, candidateKeys: readonly string[]): string {
    const keys = [...new Set(candidateKeys)].sort()
    return contentHash128(
        JSON.stringify({
            v: 1,
            targetProfiles: snapshot.targetProfiles,
            accountWide: snapshot.accountWide,
            rosterTrusted: snapshot.rosterTrusted,
            rosterSize: snapshot.candidates.size,
            members: keys.map((k) => [k, snapshot.candidates.get(k)?.evidenceDigest ?? null]),
        }),
    )
}
