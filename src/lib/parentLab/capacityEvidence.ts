// ParentLab Manual Capacity Triage - Slice 1 gate and accounting. Pure, offline, deterministic,
// read-only. Given the persisted retention document(s) for one roster walk, this decides which owned
// Veterans are candidates for a future HUMAN capacity review and tallies why the rest are excluded.
//
// Everything here re-derives its verdict from the retention document's per-Veteran EVIDENCE - the
// protection strings, the identity multiplicity, the gate reasons, the self-factor list - rather than
// from the retention state label alone. The strict state is carried verbatim for context; it is an
// input to this stage's exclusions (HARD_PROTECT and UNKNOWN are strict-state exclusions), never a
// verdict this stage may reinterpret. Nothing is ranked, scored or made executor-facing.
//
// One deliberate NON-gate: account-wide capture coverage. Retention/quarantine refuse an
// ACCOUNT_UNIQUE claim (and quarantine refuses approval) until capture coverage is complete. Manual
// Capacity Triage is human-review-only, so it does NOT globally reject the pool when coverage is
// partial. It excludes exactly the Veterans whose OWN factor evidence is missing/untrusted and admits
// the fully-evidenced rest, keeping every entry in the roster denominator.

import {
    CAPACITY_EXCLUSION_REASONS,
    PARENTLAB_CAPACITY_KIND,
    PARENTLAB_CAPACITY_SCHEMA,
    PARENTLAB_CAPACITY_SCHEMA_VERSION,
    emptyExclusionHistogram,
    emptyStrictStateDistribution,
    type CapacityEvidenceSummary,
    type CapacityEvidenceView,
    type CapacityExclusionReason,
    type CapacityTriageDocument,
    type CapacityTriageRecord,
} from "./capacityTypes.ts"
import { MIN_SUPPORTED_RETENTION_SCHEMA_VERSION } from "./quarantineTypes.ts"
import { PARENTLAB_RETENTION_SCHEMA, type RetentionShadowReport, type VeteranRetentionRecommendation } from "./retentionTypes.ts"

/** Retention gate reasons that each say this Veteran's own factor (Inspiration) evidence is untrusted. */
const SELF_FACTOR_EVIDENCE_GATES = new Set(["INSPIRATION_CAPTURE_MISSING", "INSPIRATION_CAPTURE_INCOMPLETE", "INSPIRATION_FACTORS_UNTRUSTED"])

/** Stable enum-order index, so a record's exclusion reasons serialize identically across rebuilds. */
const EXCLUSION_ORDER: ReadonlyMap<CapacityExclusionReason, number> = new Map(CAPACITY_EXCLUSION_REASONS.map((r, i) => [r, i]))

function sortReasons(reasons: Iterable<CapacityExclusionReason>): readonly CapacityExclusionReason[] {
    return [...new Set(reasons)].sort((a, b) => (EXCLUSION_ORDER.get(a) ?? 0) - (EXCLUSION_ORDER.get(b) ?? 0))
}

/**
 * Lifts the evidence facts the gate reads out of one retention recommendation. No score is computed
 * here beyond carrying the retention document's own completeness score for context.
 */
export function normalizeCapacityEvidence(rec: VeteranRetentionRecommendation): CapacityEvidenceView {
    const protectedOnAccount = rec.protectionState === "protected" || rec.favoriteState === "favorite"
    const protectionKnown = rec.protectionState === "not_protected" && rec.favoriteState === "not_set"
    const selfFactorEvidenceTrusted = rec.factorValueSummary.selfFactors !== null && !rec.gateReasons.some((g) => SELF_FACTOR_EVIDENCE_GATES.has(g))
    return {
        identityResolved: rec.rosterFingerprint !== null && rec.dataCompleteness.identityResolved,
        identityMultiplicity: rec.identityMultiplicity,
        rosterTrusted: rec.dataCompleteness.rosterTrusted,
        protectionKnown,
        protectedOnAccount,
        favoriteState: rec.favoriteState,
        protectionState: rec.protectionState,
        selfFactorEvidenceTrusted,
        hardProtected: rec.state === "HARD_PROTECT" || rec.hardProtectReasons.length > 0,
        dataCompletenessScore: rec.dataCompleteness.score,
    }
}

/**
 * The deterministic eligibility gate for one Veteran, as the set of exclusion reasons it triggers.
 * Empty means eligible for manual review. Each reason is grounded in a concrete evidence fact:
 *
 *   STRICT_HARD_PROTECT            hard-protected on its own merits (state or reason).
 *   STRICT_STATE_UNKNOWN           retention could not establish enough to advise at all.
 *   ROSTER_IDENTITY_UNRESOLVED     no fingerprint / identity unresolved.
 *   ROSTER_IDENTITY_AMBIGUOUS      the fingerprint names more than one Veteran.
 *   ROSTER_SNAPSHOT_UNTRUSTED      the snapshot behind the read is not trusted-complete.
 *   PROTECTED_ON_ACCOUNT           favorited/memoed: cannot be released in-game.
 *   PROTECTION_STATE_UNKNOWN       favorite/memo state could not be excluded.
 *   SELF_FACTOR_EVIDENCE_UNTRUSTED this Veteran's own factor capture is missing/incomplete/untrusted.
 *
 * KEEP is intentionally NOT an exclusion: a KEEP Veteran with trusted identity, known-clear protection
 * and trusted self-factor evidence is admitted. Rating/rank never enters. The strict state is used only
 * for the two strict-state exclusions above and is otherwise carried verbatim, never reinterpreted.
 */
export function capacityExclusionsFor(rec: VeteranRetentionRecommendation): readonly CapacityExclusionReason[] {
    const e = normalizeCapacityEvidence(rec)
    const reasons: CapacityExclusionReason[] = []
    if (e.hardProtected) reasons.push("STRICT_HARD_PROTECT")
    if (rec.state === "UNKNOWN") reasons.push("STRICT_STATE_UNKNOWN")
    if (!e.identityResolved) reasons.push("ROSTER_IDENTITY_UNRESOLVED")
    if (e.identityMultiplicity > 1) reasons.push("ROSTER_IDENTITY_AMBIGUOUS")
    if (!e.rosterTrusted) reasons.push("ROSTER_SNAPSHOT_UNTRUSTED")
    // Known-protected and protection-unknown are mutually exclusive facts: a proven-protected Veteran
    // is a hard capacity exclusion, an unproven one is a soft evidence gap, and reporting both would
    // double-count the same protection field into two buckets.
    if (e.protectedOnAccount) reasons.push("PROTECTED_ON_ACCOUNT")
    else if (!e.protectionKnown) reasons.push("PROTECTION_STATE_UNKNOWN")
    if (!e.selfFactorEvidenceTrusted) reasons.push("SELF_FACTOR_EVIDENCE_UNTRUSTED")
    return sortReasons(reasons)
}

function explain(admitted: boolean, strictState: string, reasons: readonly CapacityExclusionReason[]): string {
    if (admitted) return `eligible for manual capacity review; strict retention state ${strictState} carried for context`
    return `excluded from manual capacity review: ${reasons.join(", ")}`
}

/** Builds one Veteran's admission record from its retention recommendation. */
export function buildCapacityTriageRecord(rec: VeteranRetentionRecommendation): CapacityTriageRecord {
    const evidence = normalizeCapacityEvidence(rec)
    const exclusionReasons = capacityExclusionsFor(rec)
    const admitted = exclusionReasons.length === 0
    return {
        rosterFingerprint: rec.rosterFingerprint,
        scanIndex: rec.scanIndex,
        character: rec.character,
        outfit: rec.outfit,
        rank: rec.rank,
        identityMultiplicity: rec.identityMultiplicity,
        admission: admitted ? "ELIGIBLE_FOR_MANUAL_REVIEW" : "EXCLUDED_FROM_MANUAL_REVIEW",
        exclusionReasons,
        strictState: rec.state,
        strictConfidence: rec.confidence,
        evidence,
        explanation: explain(admitted, rec.state, exclusionReasons),
    }
}

/**
 * Builds the Manual Capacity Triage document from one target profile's retention document.
 *
 * The report is validated against the retention schema first and fails closed on an incompatible or
 * unsupported version - a malformed document must never silently produce an empty pool that reads as
 * "the account had nothing to review". Record order is preserved from the retention document (roster
 * scan order), so the output is deterministic for identical input.
 */
export function buildCapacityTriage(report: RetentionShadowReport): CapacityTriageDocument {
    if (report.schema !== PARENTLAB_RETENTION_SCHEMA) throw new Error(`capacity triage requires a ${PARENTLAB_RETENTION_SCHEMA} report, got schema ${String(report.schema)}`)
    if ((report.schemaVersion as number) < MIN_SUPPORTED_RETENTION_SCHEMA_VERSION) {
        throw new Error(`capacity triage requires retention schema version >= ${MIN_SUPPORTED_RETENTION_SCHEMA_VERSION}, got ${String(report.schemaVersion)}`)
    }

    const records = report.recommendations.map(buildCapacityTriageRecord)
    const exclusionHistogram = emptyExclusionHistogram()
    const admittedStrictStateDistribution = emptyStrictStateDistribution()
    const excludedStrictStateDistribution = emptyStrictStateDistribution()
    let admittedCount = 0
    let recordsWithTrustedSelfFactors = 0
    let recordsProtectionKnown = 0

    for (const record of records) {
        if (record.evidence.selfFactorEvidenceTrusted) recordsWithTrustedSelfFactors++
        if (record.evidence.protectionKnown) recordsProtectionKnown++
        if (record.admission === "ELIGIBLE_FOR_MANUAL_REVIEW") {
            admittedCount++
            admittedStrictStateDistribution[record.strictState]++
        } else {
            excludedStrictStateDistribution[record.strictState]++
            for (const reason of record.exclusionReasons) exclusionHistogram[reason]++
        }
    }

    const excludedCount = records.length - admittedCount
    const evidenceSummary: CapacityEvidenceSummary = {
        identifiedRosterEntries: report.scarcity.identifiedRosterEntries,
        capturedTrusted: report.scarcity.capturedTrusted,
        capturedUntrusted: report.scarcity.capturedUntrusted,
        coverage: report.scarcity.coverage,
        accountWide: report.scarcity.accountWide,
        rosterTrusted: records.length > 0 && records.every((r) => r.evidence.rosterTrusted),
        recordsWithTrustedSelfFactors,
        recordsProtectionKnown,
    }

    return {
        schema: PARENTLAB_CAPACITY_SCHEMA,
        schemaVersion: PARENTLAB_CAPACITY_SCHEMA_VERSION,
        kind: PARENTLAB_CAPACITY_KIND,
        targetProfile: report.targetProfile,
        rosterScanId: report.rosterScanId,
        rosterFingerprint: report.rosterFingerprint,
        protectionScanId: report.protectionScanId,
        generatedAt: report.generatedAt,
        rosterCount: records.length,
        admittedCount,
        excludedCount,
        exclusionHistogram,
        admittedStrictStateDistribution,
        excludedStrictStateDistribution,
        evidenceSummary,
        records,
    }
}
