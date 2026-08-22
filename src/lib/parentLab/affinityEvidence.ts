// ParentLab PL-R4 - the affinity evidence inventory. Pure, offline, deterministic, read-only.
//
// Before anything is ranked, this file states what is actually known about inheritance affinity in
// this repository and what is not. It is structured data rather than prose because the advisor's own
// output has to carry it: a recommendation that quietly implies a decoded affinity formula would be
// worse than no recommendation at all.
//
// Three statuses, and the boundary between them is the whole content of this module:
//   VERIFIED           - re-derivable right now from a source in hand, and used in the ranking.
//   PARTIALLY_DECODED  - part of the mechanic is re-derivable, the rest is not; the known part may be
//                        used only if it is reported as a component, never as a total.
//   UNKNOWN            - not decoded here. It contributes nothing, and its absence is reported.
//
// Nothing here backfills an unknown mechanic with an assumption. Where a mechanic is unknown the
// reason is stated in terms of what was looked at and what it did not contain.

import type { SuccessionRelationData } from "./affinityData.ts"

export const AFFINITY_DECODE_STATUSES = ["VERIFIED", "PARTIALLY_DECODED", "UNKNOWN"] as const
export type AffinityDecodeStatus = (typeof AFFINITY_DECODE_STATUSES)[number]

/** Confidence in the status claim itself, using the repository's usual four-level vocabulary. */
export const AFFINITY_EVIDENCE_CONFIDENCES = ["high", "moderate", "low", "unknown"] as const
export type AffinityEvidenceConfidence = (typeof AFFINITY_EVIDENCE_CONFIDENCES)[number]

/** One row of the evidence table. */
export interface AffinityMechanicEvidence {
    /** Stable code so a report consumer can key on it rather than on the prose. */
    readonly id: string
    readonly mechanic: string
    readonly source: string
    readonly status: AffinityDecodeStatus
    readonly confidence: AffinityEvidenceConfidence
    /** Whether the advisor's ranking actually consumes this. Unknown mechanics are never consumed. */
    readonly usableInAdvisor: boolean
    /** What is known, and where the boundary of that knowledge is. */
    readonly note: string
}

/**
 * The inventory.
 *
 * The two entries that decide the shape of the whole advisor are PAIRWISE_CHARACTER_RELATION
 * (verified, and the only affinity number the advisor is allowed to state) and
 * LINEAGE_AFFINITY_TOTAL (unknown, which is why there is no single affinity score anywhere in the
 * output).
 */
export const AFFINITY_MECHANIC_EVIDENCE: readonly AffinityMechanicEvidence[] = [
    {
        id: "PAIRWISE_CHARACTER_RELATION",
        mechanic: "Base relation points between two characters",
        source: "master.mdb succession_relation + succession_relation_member, extracted to src/data/succession_relations.json",
        status: "VERIFIED",
        confidence: "high",
        usableInAdvisor: true,
        note: "Two characters that both belong to a relation type share that type's points; the pairwise total is the sum over shared types. Directly re-derivable from the shipped tables for every covered character.",
    },
    {
        id: "RELATION_RANK_BANDS",
        mechanic: "The rank bands the game buckets a relation total into",
        source: "master.mdb succession_relation_rank",
        status: "VERIFIED",
        confidence: "high",
        usableInAdvisor: false,
        note: "Three bands are shipped. They are carried as reference only: they grade a complete in-game total, and no total this repository can compute is comparable to them.",
    },
    {
        id: "LINEAGE_AFFINITY_TOTAL",
        mechanic: "How the game aggregates a lineage into the affinity it displays",
        source: "master.mdb (searched: succession_*, single_mode_*, race_*)",
        status: "UNKNOWN",
        confidence: "unknown",
        usableInAdvisor: false,
        note: "The top rank band starts at 151 and the highest single pair the shipped tables can produce is 43, so the displayed total certainly aggregates more than one pairwise relation. Which relations enter it, and with what weights, is not in the database. No total is computed or reported.",
    },
    {
        id: "RACE_RESULT_AFFINITY_CONTRIBUTION",
        mechanic: "Contribution of shared race results to the affinity total",
        source: "master.mdb (no table links a relation type to a race id) + this repository's own telemetry",
        status: "UNKNOWN",
        confidence: "unknown",
        usableInAdvisor: false,
        note: "Nothing in the database connects the relation tables to race instances, and the per-Veteran race history of an owned Veteran is not captured by the roster or Inspiration reads. Neither side of the question is available.",
    },
    {
        id: "PARENT_PAIR_INTERNAL_RELATION",
        mechanic: "Whether the relation between the two chosen parents enters the total",
        source: "master.mdb succession_relation (computable) + no aggregation rule",
        status: "PARTIALLY_DECODED",
        confidence: "moderate",
        usableInAdvisor: true,
        note: "The number itself is exactly as computable as any other pair. Whether the game counts it is part of the unknown aggregation, so it is reported as its own labelled component and never folded into the parent-to-target figure.",
    },
    {
        id: "GRANDPARENT_IDENTITY",
        mechanic: "Which characters occupy an owned Veteran's two Legacy Origin slots",
        source: "PL-R1c Inspiration capture (veteran_inspiration.jsonl)",
        status: "UNKNOWN",
        confidence: "high",
        usableInAdvisor: false,
        note: "The Legacy Origin panel shows a portrait and a rank medal but no name, and the medal is not classified. The two ancestor blocks are captured as factor sets with no identity, so no grandparent relation can be computed and none is invented.",
    },
    {
        id: "SELF_FACTOR_SET",
        mechanic: "The factors an owned Veteran can pass on",
        source: "PL-R1c Inspiration capture, canonical factor domain",
        status: "VERIFIED",
        confidence: "high",
        usableInAdvisor: true,
        note: "Kind and star count are pixel-classified and the name is snapped onto the canonical domain. Only captures the device proved complete and fully resolved are used.",
    },
    {
        id: "LEGACY_FACTOR_BLOCKS",
        mechanic: "The factors already sitting behind an owned Veteran",
        source: "PL-R1c Inspiration capture, Legacy Origin blocks",
        status: "PARTIALLY_DECODED",
        confidence: "high",
        usableInAdvisor: true,
        note: "The factor content of the two blocks is read on the same terms as the self set. Only the ancestors' identity is missing, so the blocks are reported as legacy support and never merged into the Veteran's own factors.",
    },
    {
        id: "CURRENT_APTITUDE_GRADES",
        mechanic: "An owned Veteran's ten aptitude grades",
        source: "PL-R1b roster scan",
        status: "VERIFIED",
        confidence: "high",
        usableInAdvisor: true,
        note: "Read for every identified roster entry. Used as a coverage gate for a target build, which is a different question from what the Veteran inherits.",
    },
    {
        id: "ACCOUNT_FACTOR_SCARCITY",
        mechanic: "How many owned Veterans can supply a given factor at a given star count",
        source: "PL-R2 factor scarcity index over the trusted captures",
        status: "VERIFIED",
        confidence: "high",
        usableInAdvisor: true,
        note: "An account-wide claim requires a trusted capture for every identified roster entry. Below full coverage the counts describe only the captured subset and are reported as observed rather than account-wide.",
    },
    {
        id: "FACTOR_ID_MAPPING",
        mechanic: "Mapping a captured factor name to the game's own factor_id and effect values",
        source: "master.mdb succession_factor / succession_factor_effect",
        status: "UNKNOWN",
        confidence: "moderate",
        usableInAdvisor: false,
        note: "The tables exist but have not been decoded in this repository. Captured factors are identified by canonical name and star count only, so no effect magnitude is claimed anywhere in the output.",
    },
    {
        id: "INHERITANCE_TRIGGER_RATES",
        mechanic: "How likely a given factor is to actually fire during inheritance",
        source: "none in this repository",
        status: "UNKNOWN",
        confidence: "unknown",
        usableInAdvisor: false,
        note: "No source in hand supports a per-factor trigger probability, so star counts are treated as ordinal value and never converted into an expected stat gain.",
    },
]

/** The evidence rows the advisor's ranking actually consumes. */
export function usableAffinityMechanics(): readonly AffinityMechanicEvidence[] {
    return AFFINITY_MECHANIC_EVIDENCE.filter((e) => e.usableInAdvisor)
}

/** Rows grouped by status, in the fixed status order, for reporting. */
export function affinityEvidenceByStatus(): Readonly<Record<AffinityDecodeStatus, readonly AffinityMechanicEvidence[]>> {
    const out = {} as Record<AffinityDecodeStatus, AffinityMechanicEvidence[]>
    for (const status of AFFINITY_DECODE_STATUSES) out[status] = []
    for (const row of AFFINITY_MECHANIC_EVIDENCE) out[row.status].push(row)
    return out
}

/**
 * The named components of an affinity figure the advisor reports.
 *
 * `known` is what was computed from verified data. `unknown` names the components that exist in the
 * game and were not computed, so that a reader can never mistake the known part for the whole.
 */
export interface AffinityComponentBreakdown {
    readonly known: readonly AffinityComponent[]
    readonly unknown: readonly string[]
    /** Sum of the known components. Explicitly NOT the game's affinity total. */
    readonly knownPointsTotal: number
    /** Always false in this stage. Present so no consumer has to infer it. */
    readonly isGameAffinityTotal: false
}

export interface AffinityComponent {
    readonly id: string
    readonly label: string
    readonly points: number
    readonly sharedRelationTypes: number
}

/** The components the game has that this repository cannot compute, in a fixed order. */
export const UNKNOWN_AFFINITY_COMPONENTS: readonly string[] = [
    "GRANDPARENT_RELATIONS_ANCESTOR_IDENTITY_UNKNOWN",
    "RACE_RESULT_CONTRIBUTION_NOT_DECODED",
    "AGGREGATION_RULE_NOT_DECODED",
]

/** Assembles a breakdown from the components that resolved. Never invents a missing component. */
export function affinityBreakdown(known: readonly AffinityComponent[]): AffinityComponentBreakdown {
    return {
        known,
        unknown: UNKNOWN_AFFINITY_COMPONENTS,
        knownPointsTotal: known.reduce((sum, c) => sum + c.points, 0),
        isGameAffinityTotal: false,
    }
}

/** A one-line provenance string for a report header, derived from the loaded payload rather than
 * hard-coded, so a regenerated file cannot silently disagree with the text describing it. */
export function affinityDataProvenance(data: SuccessionRelationData): string {
    const d = data.diagnostics
    return `${data.source}; ${d.relationTypesWithMembers} relation types with members, ${d.charactersWithMembership} characters, max pairwise ${d.maxPairwisePoints}, top rank band from ${d.topRankBandMinValue}`
}
