// ParentLab Manual Capacity Triage - coverage ledger builder: the Capacity Coverage Exposure Ledger.
// Pure, offline, deterministic, read-only. Structure only - no valuation, ranking, scoring, or advice.
//
// This module builds capacity triage's admission verdicts in-process (buildCapacityTriage) and consumes them
// VERBATIM. It never re-derives ELIGIBLE/EXCLUDED and never reinterprets the strict retention state.
// On top of that pool it measures coverage exposure: for every factor slot (factorKey @ starFloor),
// every character, and every known target profile, it partitions the observed carriers into the
// eligible review pool (exposed) versus anchored outside it, and classifies the slot. The pool itself
// stays scoped to the selected profile; the target slots are read-only views over that one pool.
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
    type WhiteFactorDomainProvenance,
    type WhiteSubfamily,
    type WhiteSubfamilyCoverage,
} from "./capacityCoverageTypes.ts"
import { PARENTLAB_CAPACITY_SCHEMA_VERSION } from "./capacityTypes.ts"
import { contentHash128 } from "./identity.ts"
import { TARGET_PROFILE_IDS, TARGET_PROFILES } from "./retentionTargets.ts"
import type { ReplacementEvidenceProvenance, RetentionShadowReport, VeteranRetentionRecommendation } from "./retentionTypes.ts"

/** Deterministic display text for each limit. No clock or environment reads. */
function limitReason(code: CoverageLimitCode, report: RetentionShadowReport): string {
    switch (code) {
        case "COVERAGE_INCOMPLETE":
            return `capture coverage is not account-wide (coverage ${report.scarcity.coverage.toFixed(4)}); factor claims are observed lower bounds, not account-wide uniqueness`
        case "UNRESOLVED_FACTOR_READS":
            return `${report.scarcity.unresolvedFactorReads} self-factor read(s) did not resolve onto the canonical domain and are excluded from carrier evidence`
        case "WHITE_SUBFAMILY_NOT_AVAILABLE":
            return "white factor subfamily is not derived in v1; every white slot reports whiteSubfamily = null"
        case "CAPTURE_FACTOR_DOMAIN_NOT_RECORDED":
            return "the white factor domain used to classify these slots is pinned by schemaVersion, source and content hash; the domain identity used on device when these factor names were captured is not recorded anywhere in this pipeline, so classification-side and capture-side domains are not shown to agree"
        case "AFFINITY_NOT_DECODED":
            return "affinity is not decoded in this repository and is not consumed"
        case "REBUILDABILITY_NOT_MEASURED":
            return "Independent Training rebuildability / replacement difficulty is not measured here"
        case "REPLACEMENT_EVIDENCE_CORPUS_DEPENDENT":
            return "the retention states behind this document consumed replacement-difficulty evidence derived from the recorded career corpus; a different corpus can move Veterans between the eligible pool and the excluded set in either direction"
        case "ACTIVE_RACER_VALUE_NOT_MODELLED":
            return "active-racer utility of a Veteran is not modelled"
        case "TARGET_APPLICABILITY_NOT_MODELLED":
            return "cross-target applicability of a factor is not modelled"
        case "SINGLE_TARGET_PROFILE_SCOPE":
            return "the eligible review pool is scoped to the selected target profile; target coverage slots report every known profile against that same pool, and no other profile's pool is recalculated"
    }
}

/** Assembles the limits array: conditional coverage limits first, then the base limits. A supplied white
 * factor domain classifies the white slots, so WHITE_SUBFAMILY_NOT_AVAILABLE gives up its slot to
 * CAPTURE_FACTOR_DOMAIN_NOT_RECORDED; exactly one of the two is always present.
 * REPLACEMENT_EVIDENCE_CORPUS_DEPENDENT is seated with the rebuildability limit it qualifies. */
function buildLimits(report: RetentionShadowReport, whiteDomainAvailable: boolean, hasReplacementEvidence: boolean): readonly CoverageLimit[] {
    const codes: CoverageLimitCode[] = []
    if (!report.scarcity.accountWide) codes.push("COVERAGE_INCOMPLETE")
    if (report.scarcity.unresolvedFactorReads > 0) codes.push("UNRESOLVED_FACTOR_READS")
    for (const code of PERMANENT_COVERAGE_LIMITS) {
        codes.push(code === "WHITE_SUBFAMILY_NOT_AVAILABLE" && whiteDomainAvailable ? "CAPTURE_FACTOR_DOMAIN_NOT_RECORDED" : code)
        if (code === "REBUILDABILITY_NOT_MEASURED" && hasReplacementEvidence) codes.push("REPLACEMENT_EVIDENCE_CORPUS_DEPENDENT")
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

/**
 * Pins which white factor domain classified this document's white slots. Null only when no domain was
 * supplied.
 *
 * Counts and hash are derived from the classifier's own canonical family sets rather than accepted from
 * the caller, so they cannot disagree with how the slots were actually classified. A hand-edited asset can
 * carry any JSON under schemaVersion/source, so a malformed identity throws instead of emitting a document
 * whose provenance cannot be trusted.
 */
function whiteFactorDomainProvenanceOf(domain: WhiteFactorDomain | undefined, families: Record<WhiteSubfamily, ReadonlySet<string>> | null): WhiteFactorDomainProvenance | null {
    if (domain === undefined || families === null) return null
    const schemaVersion = domain.schemaVersion
    if (typeof schemaVersion !== "number" || !Number.isInteger(schemaVersion) || schemaVersion < 0) throw new Error(`white factor domain schemaVersion is not a non-negative integer: ${String(schemaVersion)}`)
    const source = domain.source
    if (typeof source !== "string") throw new Error(`white factor domain source is not a string: ${String(source)}`)
    const canonical = (family: WhiteSubfamily) => [...families[family]].sort()
    return {
        schemaVersion,
        source,
        counts: { race: families.race.size, scenario: families.scenario.size, skill: families.skill.size },
        contentHash128: contentHash128(JSON.stringify({ v: 1, race: canonical("race"), scenario: canonical("scenario"), skill: canonical("skill") })),
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
 * Reads the five approved replacement-evidence provenance fields off a parsed retention document.
 *
 * The input may be hand-edited JSON, so approved keys carrying unapproved values are rejected rather
 * than coerced or nulled: null means no career corpus was supplied, and mapping corrupt provenance onto
 * it would make a false absence claim. Any malformed field rejects the whole block, so no coverage
 * document is emitted. Extra top-level keys are ignored - the projection never carries them through.
 * `newestObservationTs` is finite-or-null, NOT an integer: the producer emits whatever observation time
 * the corpus carried.
 */
function readReplacementEvidence(value: unknown): ReplacementEvidenceProvenance | null {
    if (value === null || value === undefined) return null
    if (typeof value !== "object" || Array.isArray(value)) throw new Error(`replacement evidence is not an object: ${String(value)}`)
    const block = value as Record<string, unknown>
    const count = (field: string): number => {
        const raw = block[field]
        if (typeof raw !== "number" || !Number.isInteger(raw) || raw < 0) throw new Error(`replacement evidence ${field} is not a non-negative integer: ${String(raw)}`)
        return raw
    }
    const confirmedVeterans = count("confirmedVeterans")
    const traineeCount = count("traineeCount")
    const identityCollisions = count("identityCollisions")
    const versions = block.appVersions
    if (!Array.isArray(versions) || versions.some((entry) => typeof entry !== "string")) throw new Error("replacement evidence appVersions is not an array of strings")
    const newestObservationTs = block.newestObservationTs
    if (newestObservationTs !== null && (typeof newestObservationTs !== "number" || !Number.isFinite(newestObservationTs))) {
        throw new Error(`replacement evidence newestObservationTs is not a finite number or null: ${String(newestObservationTs)}`)
    }
    return {
        confirmedVeterans,
        traineeCount,
        identityCollisions,
        appVersions: [...(versions as readonly string[])],
        newestObservationTs,
    }
}

/**
 * Builds the Capacity Coverage Exposure Ledger for one target profile's retention report.
 *
 * Capacity triage is built in-process from the same report and validates the retention schema/version, failing
 * closed on an unsupported one. When the roster snapshot is untrusted, the document is usable:false with
 * an empty ledger and poolSize 0 - it must never render as "nothing is at risk".
 */
export function buildCapacityCoverage(report: RetentionShadowReport, domain?: WhiteFactorDomain): CapacityCoverageDocument {
    // Capacity triage owns schema/version validation and the admission verdicts. Consume both verbatim.
    const triage = buildCapacityTriage(report)
    const rosterTrusted = triage.evidenceSummary.rosterTrusted
    const claimStrength: CoverageClaimStrength = report.scarcity.accountWide ? "ACCOUNT" : "OBSERVED_LOWER_BOUND"
    const whiteFamilies = domain ? whiteFamilySets(domain) : null
    const whiteDomainAvailable = whiteFamilies !== null
    // Persisted retention v2 inputs carry no replacementEvidence key, and JSON.stringify drops an
    // undefined value, so normalize to null to keep the coverage key always present.
    const replacementEvidence = readReplacementEvidence(report.replacementEvidence)
    const whiteFactorDomainProvenance = whiteFactorDomainProvenanceOf(domain, whiteFamilies)
    const limits = buildLimits(report, whiteDomainAvailable, replacementEvidence !== null)

    const base = {
        schema: PARENTLAB_CAPACITY_COVERAGE_SCHEMA,
        schemaVersion: PARENTLAB_CAPACITY_COVERAGE_SCHEMA_VERSION,
        kind: PARENTLAB_CAPACITY_COVERAGE_KIND,
        targetProfile: report.targetProfile,
        rosterScanId: report.rosterScanId,
        rosterFingerprint: report.rosterFingerprint,
        protectionScanId: report.protectionScanId,
        replacementEvidence,
        whiteFactorDomainProvenance,
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

    // Target slots: one per known profile, every one measured against this same selected-lens pool.
    // clearingCarriers are Veterans clearing that profile's aptitude gate, partitioned by admission. A
    // profile with no gate (e.g. GENERAL_INHERITANCE) is cleared by every roster Veteran, and the retention
    // coverage summary deliberately does not list such a trivially-cleared profile - so a gateless profile
    // counts the whole roster rather than the empty targetsCovered set, which would otherwise read as a
    // false "no Veteran covers this target". Gatelessness follows the profile being measured, never the
    // selected one, or a gated lens would bypass the gate for every profile.
    //
    // A gated non-selected slot with exactly one admitted clearer also names that Veteran on its own row.
    // Derived from this pass rather than from the retention coverage summary's soleTargetCoverage, which is
    // roster-relative and hand-editable, so the row and the slot cannot contradict each other.
    const soleTargetSlotsByScan = new Map<number, string[]>()
    const targetSlots: readonly TargetCoverageSlot[] = TARGET_PROFILE_IDS.map((targetProfile) => {
        const gateless = TARGET_PROFILES[targetProfile].aptitudeGate === null
        let clearing = 0
        let clearingAdmitted = 0
        let clearingAnchored = 0
        let soleAdmittedScan: number | null = null
        for (const rec of report.recommendations) {
            if (!gateless && !rec.coverageSummary.targetsCovered.includes(targetProfile)) continue
            clearing++
            if (isAdmitted(admissionByScan.get(rec.scanIndex) ?? "")) {
                clearingAdmitted++
                soleAdmittedScan = clearingAdmitted === 1 ? rec.scanIndex : null
            } else clearingAnchored++
        }
        const exposure = classifyExposure(clearing, clearingAdmitted, clearingAnchored)
        if (exposure === "FULLY_EXPOSED_SOLE" && !gateless && targetProfile !== report.targetProfile && soleAdmittedScan !== null) {
            const list = soleTargetSlotsByScan.get(soleAdmittedScan) ?? []
            list.push(targetProfile)
            soleTargetSlotsByScan.set(soleAdmittedScan, list)
        }
        return {
            targetProfile,
            clearingCarriers: clearing,
            admittedCarriers: clearingAdmitted,
            anchoredCarriers: clearingAnchored,
            exposure,
            explanation: targetSlotExplanation(targetProfile, clearing, clearingAdmitted, clearingAnchored, exposure),
        }
    })

    // Per-Veteran exposure, for admitted (eligible) Veterans only, in roster scan order.
    const exposures: VeteranCoverageExposure[] = []
    for (const rec of report.recommendations) {
        const admission = admissionByScan.get(rec.scanIndex) ?? ""
        if (!isAdmitted(admission)) continue
        exposures.push(buildVeteranExposure(rec, admission, soleFactorCarrierByScan, sharedFactorSlotMembers, soleCharacterScan, soleTargetSlotsByScan))
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
    soleTargetSlotsByScan: ReadonlyMap<number, string[]>,
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
    // Already in TARGET_PROFILE_IDS order: the target-slot pass appends in that order.
    const soleTargetSlots = [...(soleTargetSlotsByScan.get(rec.scanIndex) ?? [])]

    return {
        rosterFingerprint: rec.rosterFingerprint,
        scanIndex: rec.scanIndex,
        character: rec.character,
        outfit: rec.outfit,
        admission: admission as VeteranCoverageExposure["admission"],
        soleCarrierSlots,
        fullyExposedSharedSlots,
        soleCharacterSlot,
        soleTargetSlots,
        exposureByKind,
        lastCopyRisk,
        explanation: veteranExposureExplanation(soleCarrierSlots.length, fullyExposedSharedSlots.length, soleCharacterSlot, lastCopyRisk, soleTargetSlots),
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

function veteranExposureExplanation(soleFactorSlots: number, sharedSlots: number, soleCharacter: boolean, risk: LastCopyRisk, soleTargetSlots: readonly string[]): string {
    const parts: string[] = []
    if (soleFactorSlots > 0) parts.push(`${soleFactorSlots} sole factor slot(s)`)
    if (soleCharacter) parts.push("sole character")
    if (sharedSlots > 0) parts.push(`${sharedSlots} shared fully-exposed slot(s)`)
    const detail = parts.length > 0 ? parts.join(", ") : "no exposed slot observed"
    const base = `eligible for manual review; ${risk} (${detail})`
    return soleTargetSlots.length > 0 ? `${base}; sole admitted clearer of ${soleTargetSlots.join(", ")}` : base
}
