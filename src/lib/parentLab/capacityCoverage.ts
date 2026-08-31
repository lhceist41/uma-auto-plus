// ParentLab Manual Capacity Triage - Slice 2 builder: the Capacity Coverage Exposure Ledger.
// Pure, offline, deterministic, read-only. Structure only - no valuation, ranking, scoring, or advice.
//
// This module builds Slice 1's admission verdicts in-process (buildCapacityTriage) and consumes them
// VERBATIM. It never re-derives ELIGIBLE/EXCLUDED and never reinterprets the strict retention state.
// On top of that pool it measures coverage exposure: for every factor slot (factorKey @ starFloor),
// every character, and the selected target profile, it partitions the observed carriers into the
// eligible review pool (exposed) versus anchored outside it, and classifies the slot.
//
// The conservative direction is fixed. A carrier only anchors a slot when it is genuinely outside the
// review pool AND has trusted factor evidence; a Veteran with missing/untrusted factor evidence anchors
// nothing, so the ledger over-warns rather than certifying a false "this slot is safe". And an untrusted
// roster snapshot yields usable:false with an empty ledger, never a falsely reassuring all-anchored one.

import { buildCapacityTriage } from "./capacityEvidence.ts"
import {
    COVERAGE_STAR_FLOORS,
    PARENTLAB_CAPACITY_COVERAGE_KIND,
    PARENTLAB_CAPACITY_COVERAGE_SCHEMA,
    PARENTLAB_CAPACITY_COVERAGE_SCHEMA_VERSION,
    PERMANENT_COVERAGE_LIMITS,
    WHITE_SUBFAMILIES,
    emptyCoverageExposureCounts,
    factorSlotKey,
    type CapacityCoverageDocument,
    type CharacterCoverageSlot,
    type CoverageClaimStrength,
    type CoverageExposure,
    type CoverageLimit,
    type CoverageLimitCode,
    type CoverageStarFloor,
    type FactorCoverageSlot,
    type LastCopyRisk,
    type TargetCoverageSlot,
    type VeteranCoverageExposure,
    type WhiteFactorDomain,
    type WhiteSubfamily,
    type WhiteSubfamilyCoverage,
} from "./capacityCoverageTypes.ts"
import { PARENTLAB_CAPACITY_SCHEMA_VERSION } from "./capacityTypes.ts"
import { resolveTargetProfile } from "./retentionTargets.ts"
import type { RetentionShadowReport, VeteranRetentionRecommendation } from "./retentionTypes.ts"

/** Deterministic display text for each limit. No clock or environment reads. */
function limitReason(code: CoverageLimitCode, report: RetentionShadowReport): string {
    switch (code) {
        case "COVERAGE_INCOMPLETE":
            return `capture coverage is not account-wide (coverage ${report.scarcity.coverage.toFixed(4)}); factor claims are observed lower bounds, not account-wide uniqueness`
        case "UNRESOLVED_FACTOR_READS":
            return `${report.scarcity.unresolvedFactorReads} self-factor read(s) did not resolve onto the canonical domain and are excluded from carrier evidence`
        case "WHITE_SUBFAMILY_NOT_AVAILABLE":
            return "white factor subfamily is not derived in v1; every white slot reports whiteSubfamily = null"
        case "AFFINITY_NOT_DECODED":
            return "affinity is not decoded in this repository and is not consumed"
        case "REBUILDABILITY_NOT_MEASURED":
            return "Independent Training rebuildability / replacement difficulty is not measured here"
        case "ACTIVE_RACER_VALUE_NOT_MODELLED":
            return "active-racer utility of a Veteran is not modelled"
        case "TARGET_APPLICABILITY_NOT_MODELLED":
            return "cross-target applicability of a factor is not modelled"
        case "SINGLE_TARGET_PROFILE_SCOPE":
            return "coverage is scoped to the single selected target profile; other profiles are not aggregated"
    }
}

/** Assembles the limits array: conditional coverage limits first, then the base limits. When a white
 * factor domain is available, WHITE_SUBFAMILY_NOT_AVAILABLE is dropped since white slots are classified. */
function buildLimits(report: RetentionShadowReport, whiteDomainAvailable: boolean): readonly CoverageLimit[] {
    const codes: CoverageLimitCode[] = []
    if (!report.scarcity.accountWide) codes.push("COVERAGE_INCOMPLETE")
    if (report.scarcity.unresolvedFactorReads > 0) codes.push("UNRESOLVED_FACTOR_READS")
    for (const code of PERMANENT_COVERAGE_LIMITS) {
        if (code === "WHITE_SUBFAMILY_NOT_AVAILABLE" && whiteDomainAvailable) continue
        codes.push(code)
    }
    return codes.map((code) => ({ code, reason: limitReason(code, report) }))
}

/** Upper-cased canonical-name lookup per white family, matching factorKey()'s uppercase convention. */
function whiteFamilySets(domain: WhiteFactorDomain): Record<WhiteSubfamily, ReadonlySet<string>> {
    return {
        race: new Set(domain.race.map((name) => name.toUpperCase())),
        scenario: new Set(domain.scenario.map((name) => name.toUpperCase())),
        skill: new Set(domain.skill.map((name) => name.toUpperCase())),
    }
}

/** A fully-zeroed per-family exposure histogram, keyed in the fixed WHITE_SUBFAMILIES order. */
function emptyWhiteExposureByFamily(): Record<WhiteSubfamily, Record<CoverageExposure, number>> {
    const out = {} as Record<WhiteSubfamily, Record<CoverageExposure, number>>
    for (const family of WHITE_SUBFAMILIES) out[family] = emptyCoverageExposureCounts()
    return out
}

/**
 * Classifies one coverage slot from its carrier partition.
 *
 * ANCHORED wins first: any anchored carrier means no review outcome can zero the slot. Otherwise, when
 * every observed carrier is accounted for and admitted, the slot is fully exposed (sole vs shared by
 * count). Anything else - carriers we cannot attribute to admitted-or-anchored - is UNMEASURED, the
 * conservative fallback that never reads as "not at risk".
 */
function classifyExposure(observed: number, admitted: number, anchored: number): CoverageExposure {
    if (anchored >= 1) return "ANCHORED"
    if (observed >= 1 && admitted === observed) return observed === 1 ? "FULLY_EXPOSED_SOLE" : "FULLY_EXPOSED"
    return "UNMEASURED"
}

/** Whether a Veteran is an admitted (eligible) carrier, i.e. inside the manual-review pool. */
function isAdmitted(admission: string): boolean {
    return admission === "ELIGIBLE_FOR_MANUAL_REVIEW"
}

/** Per-slot carrier bookkeeping while iterating the roster once. */
interface SlotAccumulator {
    admitted: number
    anchored: number
    /** scanIndex of the single admitted carrier, when there is exactly one and no anchor. */
    admittedScanIndices: number[]
}

/**
 * Builds the Capacity Coverage Exposure Ledger for one target profile's retention report.
 *
 * Slice 1 is built in-process from the same report and validates the retention schema/version, failing
 * closed on an unsupported one. When the roster snapshot is untrusted, the document is usable:false with
 * an empty ledger and poolSize 0 - it must never render as "nothing is at risk".
 */
export function buildCapacityCoverage(report: RetentionShadowReport, domain?: WhiteFactorDomain): CapacityCoverageDocument {
    // Slice 1 owns schema/version validation and the admission verdicts. Consume both verbatim.
    const triage = buildCapacityTriage(report)
    const rosterTrusted = triage.evidenceSummary.rosterTrusted
    const claimStrength: CoverageClaimStrength = report.scarcity.accountWide ? "ACCOUNT" : "OBSERVED_LOWER_BOUND"
    const whiteFamilies = domain ? whiteFamilySets(domain) : null
    const whiteDomainAvailable = whiteFamilies !== null
    const limits = buildLimits(report, whiteDomainAvailable)

    const base = {
        schema: PARENTLAB_CAPACITY_COVERAGE_SCHEMA,
        schemaVersion: PARENTLAB_CAPACITY_COVERAGE_SCHEMA_VERSION,
        kind: PARENTLAB_CAPACITY_COVERAGE_KIND,
        targetProfile: report.targetProfile,
        rosterScanId: report.rosterScanId,
        rosterFingerprint: report.rosterFingerprint,
        protectionScanId: report.protectionScanId,
        generatedAt: report.generatedAt,
        capacitySchemaVersion: PARENTLAB_CAPACITY_SCHEMA_VERSION as number,
        coverage: report.scarcity.coverage,
        accountWide: report.scarcity.accountWide,
        unresolvedFactorReads: report.scarcity.unresolvedFactorReads,
        rosterCount: triage.rosterCount,
        excludedSize: triage.excludedCount,
        limits,
    } as const

    // Untrusted roster: fail closed to an empty, non-reassuring ledger. Every Veteran is excluded by
    // construction, so an all-anchored ledger would read as "everything is protected" - the exact
    // dangerous misread. Emit nothing structural instead and let usable:false drive the banner.
    if (!rosterTrusted) {
        return {
            ...base,
            usable: false,
            poolSize: 0,
            recordsWithoutTrustedFactors: 0,
            unkeyedRecords: 0,
            factorSlots: [],
            characterSlots: [],
            targetSlots: [],
            exposures: [],
            factorExposureCounts: emptyCoverageExposureCounts(),
            characterExposureCounts: emptyCoverageExposureCounts(),
            whiteSubfamilyCoverage: {
                available: whiteDomainAvailable,
                exposureByFamily: emptyWhiteExposureByFamily(),
                unresolved: 0,
                ambiguous: 0,
                unresolvedNames: [],
                ambiguousNames: [],
            },
        }
    }

    const admissionByScan = new Map<number, string>()
    for (const record of triage.records) admissionByScan.set(record.scanIndex, record.admission)

    // First pass: accumulate factor-slot carriers from every Veteran's own trusted self factors.
    // A Veteran contributes carriers only when selfFactors !== null; one with null anchors nothing and
    // is counted in recordsWithoutTrustedFactors. A factor at N stars is a carrier for every floor <= N.
    const factorSlotAcc = new Map<string, SlotAccumulator>()
    let recordsWithoutTrustedFactors = 0
    for (const rec of report.recommendations) {
        const selfFactors = rec.factorValueSummary.selfFactors
        if (selfFactors === null) {
            recordsWithoutTrustedFactors++
            continue
        }
        const admitted = isAdmitted(admissionByScan.get(rec.scanIndex) ?? "")
        for (const factor of selfFactors) {
            for (const floor of COVERAGE_STAR_FLOORS) {
                if (factor.stars < floor) continue
                const key = factorSlotKey(factor.factorKey, floor)
                let acc = factorSlotAcc.get(key)
                if (!acc) {
                    acc = { admitted: 0, anchored: 0, admittedScanIndices: [] }
                    factorSlotAcc.set(key, acc)
                }
                if (admitted) {
                    acc.admitted++
                    acc.admittedScanIndices.push(rec.scanIndex)
                } else {
                    // Excluded + trusted self factors carrying the factor at this floor anchors the slot.
                    acc.anchored++
                }
            }
        }
    }

    // Build factor slots from the scarcity index (the canonical factorKey@floor universe), classifying
    // each with the carrier partition above. observedCarriers is the scarcity carrier count at the floor.
    const factorSlots: FactorCoverageSlot[] = []
    const factorExposureCounts = emptyCoverageExposureCounts()
    // White subfamily classification, populated only when a domain is supplied. Exposure is tallied per
    // slot (a factor at N stars occupies a slot at each floor <= N); the name lists dedupe by canonical name.
    const whiteExposureByFamily = emptyWhiteExposureByFamily()
    let whiteUnresolved = 0
    let whiteAmbiguous = 0
    const whiteUnresolvedNames = new Set<string>()
    const whiteAmbiguousNames = new Set<string>()
    // slotKey -> the sole admitted carrier's scanIndex, for the per-Veteran sole-carrier lists.
    const soleFactorCarrierByScan = new Map<number, string[]>()
    // slotKey -> admitted carrier scanIndices, for shared fully-exposed participation and exposureByKind.
    const sharedFactorSlotMembers = new Map<string, { kind: string; scanIndices: number[] }>()

    for (const entry of report.scarcity.entries) {
        for (const floor of COVERAGE_STAR_FLOORS) {
            const observed = entry.carriersByMinStars[String(floor)] ?? 0
            if (observed <= 0) continue
            const key = factorSlotKey(entry.factorKey, floor)
            const acc = factorSlotAcc.get(key) ?? { admitted: 0, anchored: 0, admittedScanIndices: [] }
            const exposure = classifyExposure(observed, acc.admitted, acc.anchored)
            factorExposureCounts[exposure]++
            const characterBound = entry.kind === "unique"
            let whiteSubfamily: WhiteSubfamily | null = null
            if (whiteFamilies && entry.kind === "white") {
                const name = entry.canonicalName.toUpperCase()
                const matches = WHITE_SUBFAMILIES.filter((family) => whiteFamilies[family].has(name))
                if (matches.length === 1) {
                    whiteSubfamily = matches[0]
                    whiteExposureByFamily[matches[0]][exposure]++
                } else if (matches.length === 0) {
                    whiteUnresolved++
                    whiteUnresolvedNames.add(entry.canonicalName)
                } else {
                    whiteAmbiguous++
                    whiteAmbiguousNames.add(entry.canonicalName)
                }
            }
            factorSlots.push({
                factorKey: entry.factorKey,
                kind: entry.kind,
                canonicalName: entry.canonicalName,
                whiteSubfamily,
                characterBound,
                starFloor: floor as CoverageStarFloor,
                observedCarriers: observed,
                admittedCarriers: acc.admitted,
                anchoredCarriers: acc.anchored,
                exposure,
                claimStrength,
                explanation: factorSlotExplanation(entry.factorKey, floor, observed, acc.admitted, acc.anchored, exposure, claimStrength),
            })
            if (exposure === "FULLY_EXPOSED_SOLE" && acc.admittedScanIndices.length === 1) {
                const scan = acc.admittedScanIndices[0]
                const list = soleFactorCarrierByScan.get(scan) ?? []
                list.push(key)
                soleFactorCarrierByScan.set(scan, list)
            } else if (exposure === "FULLY_EXPOSED") {
                sharedFactorSlotMembers.set(key, { kind: entry.kind, scanIndices: acc.admittedScanIndices })
            }
        }
    }

    // Character slots: keyed on normalized character identity. observedCarriers is roster membership,
    // NOT factor coverage. A record with no usable character key contributes to unkeyedRecords.
    interface CharAcc {
        admitted: number
        anchored: number
        total: number
        outfits: Set<string>
        soleAdmittedScan: number | null
    }
    const charAcc = new Map<string, CharAcc>()
    let unkeyedRecords = 0
    for (const rec of report.recommendations) {
        const key = normalizeCharacterKey(rec.character)
        if (key === null) {
            unkeyedRecords++
            continue
        }
        let acc = charAcc.get(key)
        if (!acc) {
            acc = { admitted: 0, anchored: 0, total: 0, outfits: new Set(), soleAdmittedScan: null }
            charAcc.set(key, acc)
        }
        acc.total++
        if (rec.outfit && rec.outfit.trim() !== "") acc.outfits.add(rec.outfit.trim())
        if (isAdmitted(admissionByScan.get(rec.scanIndex) ?? "")) {
            acc.admitted++
            acc.soleAdmittedScan = rec.scanIndex
        } else {
            acc.anchored++
        }
    }

    const characterSlots: CharacterCoverageSlot[] = []
    const characterExposureCounts = emptyCoverageExposureCounts()
    const soleCharacterScan = new Set<number>()
    for (const key of [...charAcc.keys()].sort()) {
        const acc = charAcc.get(key)!
        const exposure = classifyExposure(acc.total, acc.admitted, acc.anchored)
        characterExposureCounts[exposure]++
        if (exposure === "FULLY_EXPOSED_SOLE" && acc.soleAdmittedScan !== null) soleCharacterScan.add(acc.soleAdmittedScan)
        characterSlots.push({
            characterKey: key,
            observedCarriers: acc.total,
            admittedCarriers: acc.admitted,
            anchoredCarriers: acc.anchored,
            exposure,
            outfits: [...acc.outfits].sort(),
            explanation: characterSlotExplanation(key, acc.total, acc.admitted, acc.anchored, exposure),
        })
    }

    // Target slot: one, for the selected profile. clearingCarriers are Veterans clearing this profile's
    // aptitude gate, partitioned by admission. A profile with no gate (e.g. GENERAL_INHERITANCE) is
    // cleared by every roster Veteran, and the retention coverage summary deliberately does not list
    // such a trivially-cleared profile - so a gateless profile counts the whole roster rather than the
    // empty targetsCovered set, which would otherwise read as a false "no Veteran covers this target".
    const selectedProfile = resolveTargetProfile(report.targetProfile)
    const gatelessTarget = selectedProfile !== null && selectedProfile.aptitudeGate === null
    let clearing = 0
    let clearingAdmitted = 0
    let clearingAnchored = 0
    for (const rec of report.recommendations) {
        if (!gatelessTarget && !rec.coverageSummary.targetsCovered.includes(report.targetProfile)) continue
        clearing++
        if (isAdmitted(admissionByScan.get(rec.scanIndex) ?? "")) clearingAdmitted++
        else clearingAnchored++
    }
    const targetExposure = classifyExposure(clearing, clearingAdmitted, clearingAnchored)
    const targetSlots: readonly TargetCoverageSlot[] = [
        {
            targetProfile: report.targetProfile,
            clearingCarriers: clearing,
            admittedCarriers: clearingAdmitted,
            anchoredCarriers: clearingAnchored,
            exposure: targetExposure,
            explanation: targetSlotExplanation(report.targetProfile, clearing, clearingAdmitted, clearingAnchored, targetExposure),
        },
    ]

    // Per-Veteran exposure, for admitted (eligible) Veterans only, in roster scan order.
    const exposures: VeteranCoverageExposure[] = []
    for (const rec of report.recommendations) {
        const admission = admissionByScan.get(rec.scanIndex) ?? ""
        if (!isAdmitted(admission)) continue
        exposures.push(buildVeteranExposure(rec, admission, soleFactorCarrierByScan, sharedFactorSlotMembers, soleCharacterScan))
    }

    const whiteSubfamilyCoverage: WhiteSubfamilyCoverage = {
        available: whiteDomainAvailable,
        exposureByFamily: whiteExposureByFamily,
        unresolved: whiteUnresolved,
        ambiguous: whiteAmbiguous,
        unresolvedNames: [...whiteUnresolvedNames].sort(),
        ambiguousNames: [...whiteAmbiguousNames].sort(),
    }

    return {
        ...base,
        usable: true,
        poolSize: triage.admittedCount,
        recordsWithoutTrustedFactors,
        unkeyedRecords,
        factorSlots,
        characterSlots,
        targetSlots,
        exposures,
        factorExposureCounts,
        characterExposureCounts,
        whiteSubfamilyCoverage,
    }
}

/** Normalized character key, or null when the record resolves to no usable character identity. */
function normalizeCharacterKey(character: string | null): string | null {
    if (character === null) return null
    const trimmed = character.trim()
    return trimmed === "" ? null : trimmed
}

/** Assembles one eligible Veteran's coverage exposure record. */
function buildVeteranExposure(
    rec: VeteranRetentionRecommendation,
    admission: string,
    soleFactorCarrierByScan: ReadonlyMap<number, string[]>,
    sharedFactorSlotMembers: ReadonlyMap<string, { kind: string; scanIndices: number[] }>,
    soleCharacterScan: ReadonlySet<number>,
): VeteranCoverageExposure {
    const soleCarrierSlots = [...(soleFactorCarrierByScan.get(rec.scanIndex) ?? [])].sort()
    const fullyExposedSharedSlots: string[] = []
    const exposureByKindMap = new Map<string, number>()

    // A Veteran's own trusted self factors decide which shared fully-exposed slots it participates in.
    // exposureByKind counts every exposed factor slot (sole or shared) this Veteran carries, by kind.
    for (const [key, member] of sharedFactorSlotMembers) {
        if (member.scanIndices.includes(rec.scanIndex)) {
            fullyExposedSharedSlots.push(key)
            exposureByKindMap.set(member.kind, (exposureByKindMap.get(member.kind) ?? 0) + 1)
        }
    }
    // Sole factor slots also count toward exposureByKind, keyed by the slot's factor kind (kind:NAME@floor).
    for (const key of soleCarrierSlots) {
        const kind = kindOfSlotKey(key)
        if (kind !== null) exposureByKindMap.set(kind, (exposureByKindMap.get(kind) ?? 0) + 1)
    }
    fullyExposedSharedSlots.sort()

    const soleCharacterSlot = soleCharacterScan.has(rec.scanIndex)
    const exposureByKind: Record<string, number> = {}
    for (const kind of [...exposureByKindMap.keys()].sort()) exposureByKind[kind] = exposureByKindMap.get(kind)!

    const lastCopyRisk = classifyLastCopyRisk(rec, soleCarrierSlots.length, fullyExposedSharedSlots.length, soleCharacterSlot)

    return {
        rosterFingerprint: rec.rosterFingerprint,
        scanIndex: rec.scanIndex,
        character: rec.character,
        outfit: rec.outfit,
        admission: admission as VeteranCoverageExposure["admission"],
        soleCarrierSlots,
        fullyExposedSharedSlots,
        soleCharacterSlot,
        exposureByKind,
        lastCopyRisk,
        explanation: veteranExposureExplanation(soleCarrierSlots.length, fullyExposedSharedSlots.length, soleCharacterSlot, lastCopyRisk),
    }
}

/**
 * Deterministic last-copy risk precedence:
 *   UNMEASURED             the Veteran lacks the trusted factor evidence to classify factor exposure;
 *   SOLE_OBSERVED_CARRIER  it is the sole observed carrier of a factor slot or of its own character;
 *   SHARED_FULLY_EXPOSED   it participates in a shared fully-exposed factor slot;
 *   NO_EXPOSED_SLOT_OBSERVED  none of the above.
 */
function classifyLastCopyRisk(rec: VeteranRetentionRecommendation, soleFactorSlots: number, sharedSlots: number, soleCharacter: boolean): LastCopyRisk {
    if (rec.factorValueSummary.selfFactors === null) return "UNMEASURED"
    if (soleFactorSlots > 0 || soleCharacter) return "SOLE_OBSERVED_CARRIER"
    if (sharedSlots > 0) return "SHARED_FULLY_EXPOSED"
    return "NO_EXPOSED_SLOT_OBSERVED"
}

/** Extracts the factor kind from a slot key `kind:CANONICAL_NAME@floor`. Null if it has no `kind:` prefix. */
function kindOfSlotKey(slotKey: string): string | null {
    const at = slotKey.lastIndexOf("@")
    const factorKey = at >= 0 ? slotKey.slice(0, at) : slotKey
    const colon = factorKey.indexOf(":")
    return colon > 0 ? factorKey.slice(0, colon) : null
}

function factorSlotExplanation(factorKey: string, floor: number, observed: number, admitted: number, anchored: number, exposure: CoverageExposure, claimStrength: CoverageClaimStrength): string {
    const scope = claimStrength === "ACCOUNT" ? "account-wide" : "observed (lower bound)"
    switch (exposure) {
        case "ANCHORED":
            return `${factorKey} at >=${floor} stars: ${observed} ${scope} carrier(s); ${anchored} outside the review pool anchor this slot, so no review outcome can drop it to zero`
        case "FULLY_EXPOSED_SOLE":
            return `${factorKey} at >=${floor} stars: ${scope} sole carrier, inside the review pool; possible observed last-copy risk if released`
        case "FULLY_EXPOSED":
            return `${factorKey} at >=${floor} stars: ${observed} ${scope} carriers, all inside the review pool`
        case "UNMEASURED":
            return `${factorKey} at >=${floor} stars: trusted carrier evidence insufficient to classify (observed ${observed}, admitted ${admitted}, anchored ${anchored})`
    }
}

function characterSlotExplanation(characterKey: string, total: number, admitted: number, anchored: number, exposure: CoverageExposure): string {
    switch (exposure) {
        case "ANCHORED":
            return `${characterKey}: ${total} roster Veteran(s); ${anchored} outside the review pool anchor this character`
        case "FULLY_EXPOSED_SOLE":
            return `${characterKey}: sole roster Veteran, inside the review pool`
        case "FULLY_EXPOSED":
            return `${characterKey}: ${total} roster Veterans, all inside the review pool`
        case "UNMEASURED":
            return `${characterKey}: coverage could not be classified (roster ${total}, admitted ${admitted}, anchored ${anchored})`
    }
}

function targetSlotExplanation(targetProfile: string, clearing: number, admitted: number, anchored: number, exposure: CoverageExposure): string {
    switch (exposure) {
        case "ANCHORED":
            return `${targetProfile}: ${clearing} Veteran(s) clear the gate; ${anchored} outside the review pool anchor this coverage`
        case "FULLY_EXPOSED_SOLE":
            return `${targetProfile}: sole Veteran clears the gate, inside the review pool`
        case "FULLY_EXPOSED":
            return `${targetProfile}: ${clearing} Veterans clear the gate, all inside the review pool`
        case "UNMEASURED":
            return `${targetProfile}: no Veteran was observed clearing the gate (clearing ${clearing}, admitted ${admitted}, anchored ${anchored})`
    }
}

function veteranExposureExplanation(soleFactorSlots: number, sharedSlots: number, soleCharacter: boolean, risk: LastCopyRisk): string {
    const parts: string[] = []
    if (soleFactorSlots > 0) parts.push(`${soleFactorSlots} sole factor slot(s)`)
    if (soleCharacter) parts.push("sole character")
    if (sharedSlots > 0) parts.push(`${sharedSlots} shared fully-exposed slot(s)`)
    const detail = parts.length > 0 ? parts.join(", ") : "no exposed slot observed"
    return `eligible for manual review; ${risk} (${detail})`
}
