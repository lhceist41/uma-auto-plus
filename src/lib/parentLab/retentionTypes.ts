// ParentLab PL-R2 retention advisor - the shared contract. Pure, offline, deterministic, read-only.
//
// This stage answers one question and only advises: if roster capacity becomes tight, which Veterans
// are clearly valuable, which are redundant, which need review, and which might eventually be safe to
// let go. It never transfers, never favorites, never deletes and never touches the device.
//
// The optimization target is deliberately asymmetric. A false KEEP costs roster space; a false
// SAFE_TO_TRANSFER destroys inheritance that cannot be bought back. So every gate here fails closed:
// missing evidence protects, an unprovable scarcity claim is not made, and no weighted score can by
// itself move a Veteran onto the transfer side.
//
// Three separations run through the whole module and are load-bearing:
//   raw evidence vs derived recommendation - the sources are read, never mutated or folded together;
//   merit protection vs evidence gaps      - HARD_PROTECT means "this is precious", UNKNOWN means
//                                            "we cannot tell", and conflating them makes both useless;
//   observed scarcity vs account scarcity  - a factor seen once across 20 captures out of 257 owned
//                                            Veterans is not a factor that exists once on the account.

/** Schema discriminator + version for the retention document. Separate from the roster, inspiration
 * and Veteran schemas: none of those is touched by this stage. */
export const PARENTLAB_RETENTION_SCHEMA = "parent_lab_retention_shadow" as const
/**
 * Version 2 adds the fields a transfer-approval stage needs to identify a Veteran and to recompute
 * account coverage without re-reading the device: `identityMultiplicity`, the five final stats, the
 * effective favorite/protection strings, the full self factor key list, and the protection scan the
 * document was built against. Nothing that existed at version 1 changed meaning, but a version-1
 * document is missing safety-critical identity evidence, so a consumer that needs it must reject
 * rather than infer.
 *
 * Version 3 adds `replacementEvidence`: explicit replacement/rebuildability provenance so a consumer
 * can distinguish "no career corpus was supplied" (null) from "a corpus was supplied but built into an
 * empty or unusable library" (non-null, with `confirmedVeterans` possibly 0). Nothing that existed at
 * version 2 changed meaning.
 */
export const PARENTLAB_RETENTION_SCHEMA_VERSION = 3 as const

/**
 * The recommendation states, in the fixed precedence order the engine resolves them.
 *
 * `SAFE_TO_TRANSFER` is a label, never an action: PL-R2 is shadow-only and nothing downstream of this
 * module can execute a transfer.
 */
export const RETENTION_STATES = [
    /** Evidence says this Veteran must not be considered for transfer at all. */
    "HARD_PROTECT",
    /** Strong positive retention value, short of an absolute protect invariant. */
    "KEEP",
    /** Potentially expendable, but held because Transfer Request value plausibly makes it useful. */
    "TRANSFER_REQUEST_HOLD",
    /** A measured redundancy case: belongs in a future manual transfer-review queue, not in a transfer. */
    "QUARANTINE_TRANSFER",
    /** Every strict dominance, substitutability, coverage and evidence gate passed. */
    "SAFE_TO_TRANSFER",
    /** Potentially redundant or low value, with insufficient evidence to quarantine. */
    "REVIEW",
    /** Required evidence is incomplete, inconsistent or untrusted. Unknown is protected. */
    "UNKNOWN",
] as const
export type RetentionState = (typeof RETENTION_STATES)[number]

/** How much of the evidence a recommendation actually rests on. `SAFE_TO_TRANSFER` requires HIGH. */
export const RETENTION_CONFIDENCES = ["HIGH", "MEDIUM", "LOW", "INSUFFICIENT"] as const
export type RetentionConfidence = (typeof RETENTION_CONFIDENCES)[number]

/** Confidence ordering, high to low, so comparisons never depend on string order. */
export const RETENTION_CONFIDENCE_RANK: Readonly<Record<RetentionConfidence, number>> = {
    HIGH: 3,
    MEDIUM: 2,
    LOW: 1,
    INSUFFICIENT: 0,
}

/**
 * Qualitative replacement bands. Deliberately not a probability: the corpus supports "how rare was
 * this outcome among the careers this bot actually ran", not a reroll likelihood, and claiming the
 * latter from the former would be a fabricated number.
 */
export const REPLACEMENT_DIFFICULTIES = ["VERY_HARD", "HARD", "MODERATE", "EASY", "UNKNOWN"] as const
export type ReplacementDifficulty = (typeof REPLACEMENT_DIFFICULTIES)[number]

/** Difficulties that leave a Veteran eligible for the transfer side. UNKNOWN is excluded: it protects. */
export const REPLACEABLE_DIFFICULTIES: ReadonlySet<ReplacementDifficulty> = new Set<ReplacementDifficulty>(["MODERATE", "EASY"])

/**
 * Merit-based protection reasons. Each one asserts a positive fact about the Veteran's value, and each
 * is decided by an auditable rule rather than by a threshold on a blended score.
 */
export const HARD_PROTECT_REASONS = [
    /** The operator named this Veteran on the protect list. */
    "MANUAL_PROTECT",
    /** The only Veteran of its character on the roster: the only source of that character's unique factor. */
    "SOLE_CHARACTER_SOURCE",
    /** The only Veteran of its character/outfit pairing, whose potential tree is outfit-specific. */
    "SOLE_CHARACTER_OUTFIT_SOURCE",
    /** The only Veteran clearing a target profile's aptitude gate. */
    "SOLE_TARGET_APTITUDE_COVERAGE",
    /** Carries a high-value factor no other captured Veteran carries at equal or better stars. */
    "OBSERVED_UNIQUE_FACTOR",
    /** Historical evidence says the result is at the top of what this bot has ever produced for the trainee. */
    "IRREPLACEABLE_HISTORICAL_OUTCOME",
    /** The account itself marks this Veteran protected: it is favorited or has a memo, either of which
     * blocks a release in-game. Established by the PL-R2a filter-partition probe, never guessed. */
    "PROTECTED_ON_ACCOUNT",
] as const
export type HardProtectReason = (typeof HARD_PROTECT_REASONS)[number]

/**
 * Evidence gates. A gate is not an opinion about the Veteran: it is a fact about what could not be
 * established, and every one of them blocks the transfer side. They are reported separately from
 * `hardProtectReasons` precisely so "precious" and "unmeasured" never read as the same verdict.
 */
export const RETENTION_GATE_REASONS = [
    /** The roster snapshot behind this record is not trusted-complete. */
    "ROSTER_SNAPSHOT_UNTRUSTED",
    /** The roster entry never resolved to a fingerprint, so nothing can be attached to it. */
    "ROSTER_IDENTITY_UNRESOLVED",
    /** In-game protection (favorite or memo) could not be excluded. Today this is structural: the
     * roster writer emits a constant "unknown" because a memo is not visible on the list screen. */
    "PROTECTION_STATE_UNKNOWN",
    /** The favorite glyph was a saturated icon the device deliberately does not classify. */
    "FAVORITE_STATE_UNKNOWN",
    /** No Inspiration capture exists for this Veteran, so its inheritance value is unmeasured. */
    "INSPIRATION_CAPTURE_MISSING",
    /** A capture exists but the device did not prove it read the whole factor list. */
    "INSPIRATION_CAPTURE_INCOMPLETE",
    /** The capture is complete but at least one factor name did not resolve onto the canonical domain. */
    "INSPIRATION_FACTORS_UNTRUSTED",
    /** Capture coverage is too low for any account-wide scarcity claim about this Veteran's factors. */
    "SCARCITY_COVERAGE_INSUFFICIENT",
    /** No historical evidence supports a replacement-difficulty band. */
    "REPLACEMENT_DIFFICULTY_UNKNOWN",
    /** No peer dominates this Veteran under the active target, so redundancy was never established. */
    "NO_DOMINATOR_FOUND",
] as const
export type RetentionGateReason = (typeof RETENTION_GATE_REASONS)[number]

/** Positive retention arguments. None of these alone can produce a transfer-side state. */
export const RETENTION_KEEP_REASONS = ["HIGH_VALUE_FACTOR_SET", "RARE_FACTOR_CARRIER", "STRONG_TARGET_APTITUDE_FIT", "SCARCE_CHARACTER_SOURCE", "DEEP_LINEAGE_EVIDENCE"] as const
export type RetentionKeepReason = (typeof RETENTION_KEEP_REASONS)[number]

/** Arguments toward expendability. Every one is an input to review, never a decision by itself. */
export const RETENTION_RISK_REASONS = ["DOMINATED_BY_PEER", "NO_UNIQUE_OBSERVED_FACTOR", "WEAK_TARGET_APTITUDE_FIT", "GENERIC_FACTOR_SET", "LOW_FACTOR_STAR_TOTAL"] as const
export type RetentionRiskReason = (typeof RETENTION_RISK_REASONS)[number]

/**
 * How strong a scarcity statement the current capture coverage can support.
 *
 * The distinction is mandatory, not decorative. `OBSERVED_UNIQUE` says "one carrier among the Veterans
 * actually captured"; `ACCOUNT_UNIQUE` says "one carrier on the account", and only complete trusted
 * coverage of every identified roster entry can say that.
 */
export const SCARCITY_CLAIMS = ["ACCOUNT_UNIQUE", "OBSERVED_UNIQUE", "OBSERVED_SCARCE", "OBSERVED_COMMON", "UNMEASURED"] as const
export type ScarcityClaim = (typeof SCARCITY_CLAIMS)[number]

/** One canonical factor's inventory across the captured, trusted part of the roster. */
export interface FactorScarcityEntry {
    /** `kind:CANONICAL_NAME`, the semantic key. Star count is a property of a carrier, not of the key. */
    readonly factorKey: string
    readonly kind: string
    readonly canonicalName: string
    /** Distinct captured Veterans carrying this factor at any star count. */
    readonly observedCarriers: number
    /** Carriers at >= 1, >= 2 and >= 3 stars, keyed by the star floor as a string. */
    readonly carriersByMinStars: Readonly<Record<string, number>>
    readonly maxObservedStars: number
}

/**
 * The account's observed factor inventory plus the coverage that bounds every claim made from it.
 *
 * `coverage` is the fraction of identified roster entries that have a complete AND trusted capture -
 * an incomplete or unresolved capture contributes no evidence of absence, so it is excluded from the
 * numerator rather than counted as a Veteran that lacks the factor.
 */
export interface FactorScarcityIndex {
    readonly schema: typeof PARENTLAB_RETENTION_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_RETENTION_SCHEMA_VERSION
    /** Roster entries carrying a fingerprint, the only ones a capture can attach to. */
    readonly identifiedRosterEntries: number
    /** Captures that are complete and whose self factor set fully resolved. */
    readonly capturedTrusted: number
    /** Captures present but not usable as inventory evidence (incomplete or unresolved). */
    readonly capturedUntrusted: number
    /** capturedTrusted / identifiedRosterEntries, rounded to four decimals. 0 when nothing identified. */
    readonly coverage: number
    /** True only at complete coverage. The single flag that licenses an ACCOUNT_UNIQUE claim. */
    readonly accountWide: boolean
    /** Sorted by factorKey, so the index serializes identically across rebuilds. */
    readonly entries: readonly FactorScarcityEntry[]
    /** Self-factor reads inside trusted captures that carried no canonical name. Surfaced, not dropped. */
    readonly unresolvedFactorReads: number
}

/** Why a peer that wins on every target dimension still does not dominate. */
export const DOMINANCE_BLOCKERS = [
    /** The candidate does not carry every factor of the subject at equal or better stars. */
    "FACTOR_SET_NOT_COVERED",
    /** The subject is the only observed carrier of some factor, so removing it would cost coverage. */
    "SUBJECT_HAS_UNIQUE_COVERAGE",
    /** The subject is hard-protected on its own merits. */
    "SUBJECT_HARD_PROTECTED",
    /** One of the two sides has no trusted factor evidence, so no comparison is admissible. */
    "EVIDENCE_NOT_TRUSTED",
] as const
export type DominanceBlocker = (typeof DOMINANCE_BLOCKERS)[number]

/**
 * A dominance or substitution finding, always with the evidence that produced it.
 *
 * A finding with an empty `blockedBy` is a true Pareto dominator. A finding with blockers is a
 * SUBSTITUTE: it won the dimension comparison but failed a strict gate, and it is reported because
 * "who nearly replaces this Veteran, and what stopped it" is the question a human review queue
 * actually needs answered.
 */
export interface DominanceFinding {
    /** The candidate Veteran's roster fingerprint. */
    readonly rosterFingerprint: string
    readonly character: string | null
    readonly outfit: string | null
    /** Dimensions on which the candidate was strictly better. Never empty for a real finding. */
    readonly strictlyBetterOn: readonly string[]
    /** Empty for a dominator; the failed strict gates for a substitute. */
    readonly blockedBy: readonly DominanceBlocker[]
    /** One line of human-readable evidence, assembled from the facts above. */
    readonly explanation: string
}

/** What the evidence could not say about one Veteran, as machine-readable field names. */
export interface RetentionDataCompleteness {
    readonly rosterTrusted: boolean
    readonly identityResolved: boolean
    readonly inspirationCaptured: boolean
    readonly inspirationComplete: boolean
    readonly inspirationTrusted: boolean
    readonly historicalMatched: boolean
    readonly protectionKnown: boolean
    readonly scarcityAccountWide: boolean
    /** Fraction (0..1) of the eight flags above that are true, rounded to four decimals. */
    readonly score: number
}

/** One resolved self factor, reduced to the two things a coverage recompute needs. */
export interface SelfFactorRef {
    /** `kind:CANONICAL_NAME`, the same semantic key the scarcity index is built on. */
    readonly factorKey: string
    readonly stars: number
}

/** The retention value dimensions, kept separate rather than collapsed into one number. */
export interface RetentionValueSummary {
    /** Max stars over the Veteran's own stat (blue) factors, or null when unmeasured. */
    readonly statFactorStars: number | null
    /** Max stars over its aptitude (red) factors, or null when unmeasured. */
    readonly aptitudeFactorStars: number | null
    /** Max stars over its unique (green) factors, or null when unmeasured. */
    readonly uniqueFactorStars: number | null
    /** Count of its skill/race (white) factors, or null when unmeasured. */
    readonly whiteFactorCount: number | null
    /** Sum of stars over every self factor, or null when unmeasured. */
    readonly totalFactorStars: number | null
    /** The scarcest claim any of its self factors supports. */
    readonly scarcestClaim: ScarcityClaim
    /** Factor keys where this Veteran is the only observed carrier at its own star count. */
    readonly observedUniqueFactorKeys: readonly string[]
    /**
     * Every resolved self factor, sorted by key then stars. Null when no trusted capture backs it.
     *
     * Present so a consumer can recompute what the account would still carry after a hypothetical
     * removal. `observedUniqueFactorKeys` answers that only for THIS Veteran in isolation; a set of
     * individually non-unique carriers can still hold the last copy of a factor between them, and
     * that is only visible with the full carrier lists in hand.
     */
    readonly selfFactors: readonly SelfFactorRef[] | null
    /** Legacy Origin ancestors observed on its Inspiration panel (0..2), or null when uncaptured. */
    readonly lineageAncestorsObserved: number | null
    /** In-game rating. A WEAK dimension: reported and used for ordering only, never a gate and never
     * a dominance dimension. A rare-factor Veteran must be able to outrank a higher-rated generic one. */
    readonly rating: number | null
}

/** What disappears from the account if this Veteran goes. */
export interface RetentionCoverageSummary {
    readonly character: string | null
    /** Roster Veterans sharing this character, including this one. */
    readonly characterCarriers: number
    /** Roster Veterans sharing this character AND outfit, including this one. */
    readonly characterOutfitCarriers: number
    /** Target profile ids whose aptitude gate this Veteran clears. */
    readonly targetsCovered: readonly string[]
    /** Target profile ids this Veteran is the ONLY roster Veteran to cover. */
    readonly soleTargetCoverage: readonly string[]
}

/** Why this Veteran is or is not replaceable, and how far the evidence actually reaches. */
export interface ReplacementSummary {
    readonly difficulty: ReplacementDifficulty
    /** Historical careers of the same trainee found in the PL-3 library. */
    readonly historicalSamples: number
    /** How many of those reached a stat total at least as high as this Veteran's. Null when unmeasured. */
    readonly historicalAtOrAbove: number | null
    /** The Veteran's own stat total from the roster read. Null when unread. */
    readonly statTotal: number | null
    /** Reconciliation status against history (EXACT / PROBABLE / ROSTER_ONLY / AMBIGUOUS / UNRESOLVED). */
    readonly historicalMatchStatus: string
    /** One line naming the method and its limit, so the band is never read as a probability. */
    readonly basis: string
}

/** The full, explainable recommendation for one Veteran under one target profile. */
export interface VeteranRetentionRecommendation {
    readonly rosterFingerprint: string | null
    readonly scanIndex: number
    readonly character: string | null
    readonly outfit: string | null
    readonly rank: string | null
    /** How many entries in the same snapshot share this fingerprint. > 1 means the fingerprint does
     * not name one Veteran, which no downstream stage may paper over. */
    readonly identityMultiplicity: number
    /** The five final stats as read, in ROSTER_STAT_KEYS order. An unread stat stays null. */
    readonly stats: Readonly<Record<string, number | null>>
    /** The effective favorite state after any protection probe was applied. */
    readonly favoriteState: string
    /** The effective protection state after any protection probe was applied. */
    readonly protectionState: string
    readonly state: RetentionState
    readonly confidence: RetentionConfidence
    readonly hardProtectReasons: readonly HardProtectReason[]
    readonly gateReasons: readonly RetentionGateReason[]
    readonly keepReasons: readonly RetentionKeepReason[]
    readonly riskReasons: readonly RetentionRiskReason[]
    readonly factorValueSummary: RetentionValueSummary
    readonly coverageSummary: RetentionCoverageSummary
    readonly replacement: ReplacementSummary
    /** Peers that dominate this Veteran under the active target. Empty when none does. */
    readonly dominators: readonly DominanceFinding[]
    /** Peers that win every target dimension but fail a strict gate, with the gate that stopped them. */
    readonly substitutes: readonly DominanceFinding[]
    readonly dataCompleteness: RetentionDataCompleteness
    /** Field names whose evidence is missing, as machine-readable tokens. */
    readonly unknownEvidence: readonly string[]
    /** Human-readable justification, assembled deterministically from the reason codes above. */
    readonly explanation: string
}

/** A rule the engine deliberately does not apply, and why. Reported, never silently absent. */
export interface InactiveRuleNote {
    readonly rule: string
    readonly reason: string
}

/** Counts by state, for the summary document. */
export type RetentionStateCounts = Readonly<Record<RetentionState, number>>

/**
 * Replacement/rebuildability provenance for the whole document: what career corpus stood behind the
 * replacement-difficulty bands. On `RetentionShadowReport` this is null when no corpus was supplied and
 * non-null when one was, even if that corpus built into an empty library. The three counts are the
 * library's own diagnostics; `appVersions` and `newestObservationTs` describe the SUPPLIED parsed
 * corpus, not just the admitted Veterans, so a corpus that yields zero Veterans still reports the
 * versions and timestamps it actually carried.
 */
export interface ReplacementEvidenceProvenance {
    readonly confirmedVeterans: number
    readonly traineeCount: number
    readonly identityCollisions: number
    readonly appVersions: readonly string[]
    readonly newestObservationTs: number | null
}

/** The shadow document: one target profile's recommendations over one roster snapshot. */
export interface RetentionShadowReport {
    readonly schema: typeof PARENTLAB_RETENTION_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_RETENTION_SCHEMA_VERSION
    /** Roster scan this document describes. */
    readonly rosterScanId: string
    /** The PL-R2a protection probe whose derivation was applied, or null when none was supplied.
     * Null means every protection gate stayed closed, which is a different fact from a probe that
     * ran and found nothing. */
    readonly protectionScanId: string | null
    /** Fingerprint of the roster state: `<scanId>:<identified>/<entries>`. Stable per snapshot. */
    readonly rosterFingerprint: string
    /**
     * Newest observation time across the inputs, NOT a wall clock. The document must rebuild
     * byte-identically from the same inputs, so nothing here may read the current time.
     */
    readonly generatedAt: number | null
    readonly targetProfile: string
    readonly counts: RetentionStateCounts
    readonly scarcity: FactorScarcityIndex
    readonly recommendations: readonly VeteranRetentionRecommendation[]
    readonly inactiveRules: readonly InactiveRuleNote[]
    /**
     * Replacement-evidence provenance, or null when no career corpus was supplied. Non-null means a
     * corpus was supplied and built into the library, even one that yielded zero confirmed Veterans, so
     * a consumer can tell "no corpus" from "corpus supplied but empty/unusable".
     */
    readonly replacementEvidence: ReplacementEvidenceProvenance | null
}
