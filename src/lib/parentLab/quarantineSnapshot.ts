// ParentLab PL-R3 - advisor snapshot normalization and the semantic evidence digest. Pure, offline,
// deterministic, read-only.
//
// This is the read side of the quarantine stage. It takes the retention documents produced for one
// roster walk (one per target profile) and turns them into one comparable snapshot: a candidate
// reference per identified Veteran, the safety facts behind each PL-R2 verdict, and a digest over
// exactly the evidence that matters to an irreversible transfer.
//
// Two decisions shape the whole file.
//
// First, PL-R2's state label is an input, never a verdict. Every blocker below is re-derived from the
// facts the document carries - protection strings, gate reasons, dominator lists, scarcity coverage -
// rather than read off `state`. A label summarizes rules that ran elsewhere; re-deriving is what
// makes a stale or subtly wrong document visible instead of authoritative.
//
// Second, the digest excludes the roster scan id and every clock. Folding the scan id in would make
// the digest differ on every rescan of an unchanged account, which destroys the only signal it
// exists to carry: same safety evidence, same digest; changed safety evidence, changed digest. The
// scan id travels beside the digest on the snapshot and on every ledger entry, so nothing is lost.

import { contentHash128 } from "./identity.ts"
import { normalizeJoinName } from "./reconcile.ts"
import { TARGET_PROFILE_IDS } from "./retentionTargets.ts"
import { PARENTLAB_RETENTION_SCHEMA, REPLACEABLE_DIFFICULTIES, RETENTION_CONFIDENCE_RANK, type RetentionShadowReport, type VeteranRetentionRecommendation } from "./retentionTypes.ts"
import {
    MIN_SUPPORTED_RETENTION_SCHEMA_VERSION,
    REQUIRED_APPROVAL_CONFIDENCE,
    type AdvisorSnapshot,
    type CandidateRef,
    type CandidateTargetView,
    type QuarantineStatusReason,
    type SnapshotCandidate,
    type SnapshotDefect,
} from "./quarantineTypes.ts"

/** Gate reasons that say the Spark evidence itself is not trustworthy for this Veteran. */
const SPARK_EVIDENCE_GATES = new Set(["INSPIRATION_CAPTURE_MISSING", "INSPIRATION_CAPTURE_INCOMPLETE", "INSPIRATION_FACTORS_UNTRUSTED"])

/** Deterministic sorted unique string list. Used everywhere a set is serialized into a digest. */
function sortedUnique(values: Iterable<string>): readonly string[] {
    return [...new Set(values)].sort()
}

/**
 * The stable key for one Veteran across snapshots.
 *
 * A fingerprint that occurred once IS the key. A fingerprint that occurred more than once gets its
 * occurrence appended, which keeps the two rows apart inside one snapshot but is deliberately NOT
 * treated as a cross-snapshot identity: nothing observable distinguishes two Veterans whose displayed
 * content is identical, so their positions can swap between walks without any evidence of it. Both
 * are blocked by AMBIGUOUS_ROSTER_IDENTITY, and if the duplicate later disappears the survivor keys
 * as a fresh candidate with zero maturity. That is the fail-closed outcome, not a bug.
 */
export function candidateKeyFor(rosterFingerprint: string, occurrenceOrdinal: number | null): string {
    return occurrenceOrdinal === null ? rosterFingerprint : `${rosterFingerprint}#${occurrenceOrdinal}`
}

/** Builds the reference for one recommendation. `occurrenceOrdinal` is null at multiplicity 1. */
export function candidateRefFor(rec: VeteranRetentionRecommendation, occurrenceOrdinal: number | null): CandidateRef {
    return {
        rosterFingerprint: rec.rosterFingerprint as string,
        occurrenceOrdinal,
        character: rec.character,
        outfit: rec.outfit,
        rank: rec.rank,
        stats: { ...rec.stats },
    }
}

/** Whether two references describe the same Veteran, by identity AND by re-identification evidence. */
export function sameCandidate(a: CandidateRef, b: CandidateRef): boolean {
    if (a.rosterFingerprint !== b.rosterFingerprint || a.occurrenceOrdinal !== b.occurrenceOrdinal) return false
    if (a.character !== b.character || a.outfit !== b.outfit || a.rank !== b.rank) return false
    const keys = sortedUnique([...Object.keys(a.stats), ...Object.keys(b.stats)])
    return keys.every((k) => (a.stats[k] ?? null) === (b.stats[k] ?? null))
}

function targetViewFor(rec: VeteranRetentionRecommendation, targetProfile: string): CandidateTargetView {
    return {
        targetProfile,
        state: rec.state,
        confidence: rec.confidence,
        hardProtectReasons: sortedUnique(rec.hardProtectReasons),
        gateReasons: sortedUnique(rec.gateReasons),
        riskReasons: sortedUnique(rec.riskReasons),
        dominatorFingerprints: sortedUnique(rec.dominators.map((d) => d.rosterFingerprint)),
        substituteFingerprints: sortedUnique(rec.substitutes.map((d) => d.rosterFingerprint)),
        replacementDifficulty: rec.replacement.difficulty,
        observedUniqueFactorKeys: sortedUnique(rec.factorValueSummary.observedUniqueFactorKeys),
        scarcestClaim: rec.factorValueSummary.scarcestClaim,
        targetsCovered: sortedUnique(rec.coverageSummary.targetsCovered),
        soleTargetCoverage: sortedUnique(rec.coverageSummary.soleTargetCoverage),
    }
}

/**
 * The semantic evidence digest for one candidate in one snapshot.
 *
 * Fixed key order, sorted collections, no clock, no scan id, no rendered explanation text. Two
 * snapshots of an account whose transfer-relevant evidence did not move produce the same digest;
 * anything that could change whether the transfer is safe changes it.
 */
export function candidateEvidenceDigest(candidate: Omit<SnapshotCandidate, "blockers" | "eligible" | "evidenceDigest">, accountWide: boolean, rosterTrusted: boolean): string {
    return contentHash128(
        JSON.stringify({
            v: 1,
            fingerprint: candidate.ref.rosterFingerprint,
            occurrence: candidate.ref.occurrenceOrdinal,
            multiplicity: candidate.identityMultiplicity,
            character: candidate.ref.character,
            outfit: candidate.ref.outfit,
            rank: candidate.ref.rank,
            stats: candidate.ref.stats,
            favoriteState: candidate.favoriteState,
            protectionState: candidate.protectionState,
            selfFactors: candidate.selfFactors,
            targetsCovered: candidate.targetsCovered,
            accountWide,
            rosterTrusted,
            targets: candidate.perTarget.map((t) => [
                t.targetProfile,
                t.state,
                t.confidence,
                t.hardProtectReasons,
                t.gateReasons,
                t.riskReasons,
                t.dominatorFingerprints,
                t.substituteFingerprints,
                t.replacementDifficulty,
                t.observedUniqueFactorKeys,
                t.scarcestClaim,
                t.targetsCovered,
                t.soleTargetCoverage,
            ]),
        }),
    )
}

/**
 * Re-derives every safety fact that disqualifies a candidate, independently of PL-R2's state label.
 *
 * The overlap with PL-R2's own gates is deliberate. If a future advisor change ever let a Veteran
 * reach SAFE_TO_TRANSFER while, say, its protection state was unknown, this pass would still refuse
 * it, and the disagreement would be visible in the ledger rather than silently trusted.
 */
function blockersFor(candidate: Omit<SnapshotCandidate, "blockers" | "eligible" | "evidenceDigest">, snapshot: { accountWide: boolean; rosterTrusted: boolean; targetProfiles: readonly string[]; defects: readonly SnapshotDefect[] }): readonly QuarantineStatusReason[] {
    const blockers = new Set<QuarantineStatusReason>()

    if (candidate.identityMultiplicity > 1) blockers.add("AMBIGUOUS_ROSTER_IDENTITY")
    if (candidate.protectionState !== "not_protected" || candidate.favoriteState !== "not_set") blockers.add("PROTECTED_ON_ACCOUNT")
    if (!snapshot.rosterTrusted) blockers.add("ROSTER_SNAPSHOT_UNTRUSTED")
    if (!snapshot.accountWide) blockers.add("ACCOUNT_COVERAGE_NOT_ACCOUNT_WIDE")
    if (candidate.selfFactors === null) blockers.add("SPARK_EVIDENCE_UNTRUSTED")
    // A snapshot that does not cover every required profile cannot prove a Veteran is redundant under
    // the profile it is missing, and a Veteran redundant for Mile may be the account's only Long.
    for (const id of TARGET_PROFILE_IDS) if (!snapshot.targetProfiles.includes(id)) blockers.add("TARGET_PROFILE_COVERAGE_INCOMPLETE")
    if (snapshot.defects.length > 0) blockers.add("ROSTER_SNAPSHOT_UNTRUSTED")

    for (const t of candidate.perTarget) {
        if (t.hardProtectReasons.length > 0) blockers.add("HARD_PROTECT_PRESENT")
        if (t.observedUniqueFactorKeys.length > 0) blockers.add("UNIQUE_FACTOR_COVERAGE_PRESENT")
        if (t.gateReasons.some((g) => SPARK_EVIDENCE_GATES.has(g))) blockers.add("SPARK_EVIDENCE_UNTRUSTED")
        if (RETENTION_CONFIDENCE_RANK[t.confidence] < RETENTION_CONFIDENCE_RANK[REQUIRED_APPROVAL_CONFIDENCE]) blockers.add("CONFIDENCE_BELOW_REQUIRED")
        if (t.dominatorFingerprints.length === 0) blockers.add("DOMINATOR_SET_EMPTY")
        if (!REPLACEABLE_DIFFICULTIES.has(t.replacementDifficulty)) blockers.add("REPLACEMENT_NOT_REPLACEABLE")
        if (t.state === "QUARANTINE_TRANSFER") blockers.add("ADVISOR_STATE_NOT_APPROVAL_ELIGIBLE")
        else if (t.state !== "SAFE_TO_TRANSFER") blockers.add("ADVISOR_STATE_NOT_ELIGIBLE")
    }

    return [...blockers].sort()
}

/** The retention documents for one roster walk, validated. Nothing is inferred for a missing field. */
function snapshotDefects(reports: readonly RetentionShadowReport[]): readonly SnapshotDefect[] {
    const defects = new Set<SnapshotDefect>()
    if (reports.length === 0) return ["EMPTY_SNAPSHOT"]
    if (reports.some((r) => r.schema !== PARENTLAB_RETENTION_SCHEMA || (r.schemaVersion as number) < MIN_SUPPORTED_RETENTION_SCHEMA_VERSION)) defects.add("RETENTION_SCHEMA_UNSUPPORTED")
    if (new Set(reports.map((r) => r.rosterScanId)).size > 1 || new Set(reports.map((r) => r.rosterFingerprint)).size > 1) defects.add("ROSTER_SNAPSHOT_MISMATCH")

    const profiles = reports.map((r) => r.targetProfile)
    if (new Set(profiles).size !== profiles.length) defects.add("DUPLICATE_TARGET_PROFILE")
    for (const id of TARGET_PROFILE_IDS) if (!profiles.includes(id)) defects.add("TARGET_PROFILE_COVERAGE_INCOMPLETE")

    const signature = (r: RetentionShadowReport) => r.recommendations.map((x) => x.scanIndex).join(",")
    if (new Set(reports.map(signature)).size > 1) defects.add("RECOMMENDATION_SET_MISMATCH")

    if (reports.some((r) => r.recommendations.length === 0)) defects.add("EMPTY_SNAPSHOT")
    if (reports.some((r) => r.recommendations.some((x) => !x.dataCompleteness.rosterTrusted))) defects.add("ROSTER_SNAPSHOT_UNTRUSTED")
    if (reports.some((r) => r.recommendations.some((x) => x.rosterFingerprint === null))) defects.add("UNRESOLVED_ROSTER_IDENTITY_PRESENT")

    return [...defects].sort()
}

/**
 * Normalizes one roster walk's retention documents into a comparable snapshot.
 *
 * A defective snapshot is still built and still recorded: a gap in the evidence history has to be
 * visible, and every candidate in it carries the defect as a blocker, so nothing in it can accrue
 * maturity. Silently dropping it would make the history read as if the walk never happened.
 */
export function buildAdvisorSnapshot(reports: readonly RetentionShadowReport[]): AdvisorSnapshot {
    const defects = snapshotDefects(reports)
    const first = reports[0] ?? null
    const targetProfiles = sortedUnique(reports.map((r) => r.targetProfile))
    const accountWide = reports.length > 0 && reports.every((r) => r.scarcity.accountWide)
    const rosterTrusted = reports.length > 0 && reports.every((r) => r.recommendations.length > 0 && r.recommendations.every((x) => x.dataCompleteness.rosterTrusted))
    const observedAt = reports.reduce<number | null>((best, r) => (r.generatedAt === null ? best : best === null || r.generatedAt > best ? r.generatedAt : best), null)

    // The occurrence ordinal is assigned in scan order within the snapshot, so it is stable for a
    // given document set. It is recorded, not trusted: see `candidateKeyFor`.
    const ordinals = new Map<string, number>()
    const candidates = new Map<string, SnapshotCandidate>()
    let unidentifiedEntries = 0

    const base = reports.find((r) => r.targetProfile === TARGET_PROFILE_IDS[0]) ?? first
    for (const rec of [...(base?.recommendations ?? [])].sort((a, b) => a.scanIndex - b.scanIndex)) {
        if (rec.rosterFingerprint === null) {
            unidentifiedEntries++
            continue
        }
        const seen = ordinals.get(rec.rosterFingerprint) ?? 0
        ordinals.set(rec.rosterFingerprint, seen + 1)
        const ordinal = rec.identityMultiplicity > 1 ? seen : null
        const key = candidateKeyFor(rec.rosterFingerprint, ordinal)

        const perTarget: CandidateTargetView[] = []
        for (const report of [...reports].sort((a, b) => (a.targetProfile < b.targetProfile ? -1 : 1))) {
            const match = report.recommendations.find((x) => x.scanIndex === rec.scanIndex)
            if (match) perTarget.push(targetViewFor(match, report.targetProfile))
        }

        const partial = {
            candidateKey: key,
            ref: candidateRefFor(rec, ordinal),
            scanIndex: rec.scanIndex,
            identityMultiplicity: rec.identityMultiplicity,
            favoriteState: rec.favoriteState,
            protectionState: rec.protectionState,
            characterKey: rec.character ? normalizeJoinName(rec.character) : null,
            selfFactors: rec.factorValueSummary.selfFactors ?? null,
            targetsCovered: sortedUnique(perTarget.flatMap((t) => t.targetsCovered)),
            perTarget,
            tracked: perTarget.some((t) => t.state === "SAFE_TO_TRANSFER" || t.state === "QUARANTINE_TRANSFER"),
        }
        const blockers = blockersFor(partial, { accountWide, rosterTrusted, targetProfiles, defects })
        const evidenceDigest = candidateEvidenceDigest(partial, accountWide, rosterTrusted)
        candidates.set(key, { ...partial, blockers, eligible: partial.tracked && blockers.length === 0, evidenceDigest })
    }

    const digest = contentHash128(
        JSON.stringify({
            v: 1,
            targetProfiles,
            accountWide,
            rosterTrusted,
            defects,
            candidates: [...candidates.keys()].sort().map((k) => [k, candidates.get(k)?.evidenceDigest]),
        }),
    )

    return {
        snapshotId: first?.rosterScanId ?? "",
        rosterScanId: first?.rosterScanId ?? "",
        rosterFingerprint: first?.rosterFingerprint ?? "",
        protectionScanId: first?.protectionScanId ?? null,
        observedAt,
        targetProfiles,
        accountWide,
        rosterTrusted,
        defects,
        candidates,
        unidentifiedEntries,
        digest,
    }
}

/**
 * Normalizes a persisted retention document into its report list.
 *
 * `parent-lab-retention --out` writes either one report or `{schema, schemaVersion, reports: [...]}`,
 * so both shapes are accepted. Anything else throws rather than being coerced: a malformed document
 * must not become an empty snapshot that reads as "the account had nothing eligible".
 */
export function retentionReportsOf(document: unknown): readonly RetentionShadowReport[] {
    if (typeof document !== "object" || document === null) throw new Error("retention document is not an object")
    const doc = document as { schema?: unknown; reports?: unknown; recommendations?: unknown }
    if (doc.schema !== PARENTLAB_RETENTION_SCHEMA) throw new Error(`retention document has schema ${String(doc.schema)}, expected ${PARENTLAB_RETENTION_SCHEMA}`)
    if (Array.isArray(doc.reports)) return doc.reports as readonly RetentionShadowReport[]
    if (Array.isArray(doc.recommendations)) return [document as RetentionShadowReport]
    throw new Error("retention document carries neither a reports array nor a recommendations array")
}
