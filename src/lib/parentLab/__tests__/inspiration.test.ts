import { fixtureFingerprint } from "./rosterFixtures.ts"
import {
    ancestorStarSignature,
    buildInspirationIndex,
    detectInspirationConflicts,
    joinInspirationToRoster,
    parseInspirationRecords,
} from "../inspiration.ts"
import { buildRosterSnapshots, parseRosterScanRecords } from "../roster.ts"

// The inspiration snapshot is exercised through the same parse path the device writer produces: JSONL
// lines authored exactly as the Kotlin serializer writes them, parsed and joined offline.
//
// The factor contents below are the live 2026-08-21 read of the `[Wild Frontier] Taiki Shuttle`
// Veteran, so the shapes here are the shapes the device actually emits rather than invented ones.

function factor(kind: string, name: string, stars: number, rowIndex: number, column: "left" | "right", o: Record<string, unknown> = {}) {
    return {
        rowIndex,
        column,
        kind,
        displayName: name,
        normalizedName: name.toUpperCase(),
        stars,
        canonicalName: name,
        canonicalPath: "strong",
        factorFingerprint: `${kind}:${name.toUpperCase()}:${stars}`,
        structuralFingerprint: `${kind}:${stars}`,
        ambiguous: false,
        ...o,
    }
}

function factorSetFingerprint(factors: readonly ReturnType<typeof factor>[]): string {
    return factors.map((entry) => entry.factorFingerprint).sort().join("|")
}

function structuralFactorSetFingerprint(factors: readonly ReturnType<typeof factor>[]): string {
    return factors.map((entry) => entry.structuralFingerprint).sort().join("|")
}

const SELF = [
    factor("stat", "Power", 1, 0, "left"),
    factor("aptitude", "Mile", 2, 0, "right"),
    factor("unique", "Shooting for Victory!", 1, 1, "left"),
    factor("white", "Yasuda Kinen", 1, 1, "right"),
]

const ANCESTOR_ZERO = [
    factor("stat", "Power", 1, 0, "left"),
    factor("aptitude", "Pace Chaser", 2, 0, "right"),
    factor("unique", "Dancing in the Leaves", 3, 1, "left"),
    factor("white", "Osaka Hai", 2, 1, "right"),
]

const ANCESTOR_ONE = [
    factor("stat", "Speed", 2, 0, "left"),
    factor("aptitude", "Pace Chaser", 2, 0, "right"),
]

const ROSTER_APTITUDES = { turf: "A", dirt: "B", sprint: "A", mile: "A", medium: "E", long: "G", front: "C", pace: "A", late: "E", end: "G" }
const inspirationFingerprint = (index: number) => fixtureFingerprint({
    character: "Taiki Shuttle", outfit: "Wild Frontier", rank: "A", rating: 10192 + index,
    stats: { spd: 949 + index, sta: 699, pwr: 648, grt: 687, wit: 420 }, aptitudes: ROSTER_APTITUDES,
})

function entryLine(scanId: string, scanIndex: number, o: Record<string, unknown> = {}): string {
    const selfFactors = Array.isArray(o.selfFactors) ? o.selfFactors as ReturnType<typeof factor>[] : SELF
    const blocks = [selfFactors, ANCESTOR_ZERO, ANCESTOR_ONE]
    return JSON.stringify({
        type: "veteran_inspiration",
        schemaVersion: 2,
        scanId,
        scanIndex,
        observedAt: 1_700_000_000_000 + scanIndex,
        rosterFingerprint: inspirationFingerprint(scanIndex),
        character: "Taiki Shuttle",
        outfit: "Wild Frontier",
        rank: "A",
        selfPortraitObserved: true,
        selfFactorCount: selfFactors.length,
        selfFactorFingerprint: factorSetFingerprint(selfFactors),
        selfStructuralFingerprint: structuralFactorSetFingerprint(selfFactors),
        selfFactorSetTrusted: true,
        selfFactors,
        legacyAncestors: [
            { ancestorIndex: 0, portraitObserved: true, factorCount: ANCESTOR_ZERO.length, ancestorFactorFingerprint: factorSetFingerprint(ANCESTOR_ZERO), ancestorStructuralFingerprint: structuralFactorSetFingerprint(ANCESTOR_ZERO), factorSetTrusted: true, factors: ANCESTOR_ZERO },
            { ancestorIndex: 1, portraitObserved: true, factorCount: ANCESTOR_ONE.length, ancestorFactorFingerprint: factorSetFingerprint(ANCESTOR_ONE), ancestorStructuralFingerprint: structuralFactorSetFingerprint(ANCESTOR_ONE), factorSetTrusted: true, factors: ANCESTOR_ONE },
        ],
        termination: "reached_bottom",
        sparkCaptureComplete: true,
        screenReadCompleteness: 1,
        unresolvedFields: [],
        diagnostics: {
            frames: 3,
            swipes: 2,
            startedAtTop: true,
            reachedBottom: true,
            factorListEndObserved: true,
            gapFrames: 0,
            spacingBreaks: 0,
            alignmentFailures: 0,
            unsettledFrames: 0,
            deadReckonedFrames: 0,
            scrollbarContentHeight: 1807,
            observedContentHeight: 1795,
            rowsAccepted: blocks.reduce((count, block) => count + new Set(block.map((entry) => entry.rowIndex)).size, 0),
            clippedRowsRejected: 1,
            leadingPartialBlockRows: 0,
            blocksObserved: blocks.length,
        },
        ...o,
    })
}

function headerLine(scanId: string, o: Record<string, unknown> = {}): string {
    return JSON.stringify({
        type: "veteran_inspiration_scan",
        schemaVersion: 2,
        scanId,
        startedAt: 1_700_000_000_000,
        completedAt: 1_700_000_300_000,
        registeredUsedAtStart: 257,
        registeredUsedAtEnd: 257,
        registeredCapacity: 260,
        filtersOff: true,
        sortKey: "Rating",
        sortDirection: "Desc",
        snapshotCompatibility: true,
        entryLimit: 20,
        startIndex: 0,
        entriesCaptured: 2,
        entriesComplete: 2,
        terminationReason: "entry_limit_reached",
        app: "1.3.8",
        ...o,
    })
}

function fullHeader(scanId: string, o: Record<string, unknown> = {}): string {
    return headerLine(scanId, {
        registeredUsedAtStart: 1,
        registeredUsedAtEnd: 1,
        entryLimit: 0,
        entriesCaptured: 1,
        entriesComplete: 1,
        terminationReason: "cycle_closed",
        pagerCycleClosed: true,
        ...o,
    })
}

function rosterCorpus(count: number): string {
    const lines: string[] = []
    for (let i = 0; i < count; i++) {
        lines.push(
            JSON.stringify({
                type: "roster_entry",
                schemaVersion: 1,
                scanId: "rs-1",
                scanIndex: i,
                observedAt: 1_700_000_000_000 + i,
                character: "Taiki Shuttle",
                outfit: "Wild Frontier",
                rank: "A",
                rating: 10192 + i,
                stats: { spd: 949 + i, sta: 699, pwr: 648, grt: 687, wit: 420 },
                statGrades: {},
                aptitudes: ROSTER_APTITUDES,
                favoriteState: "not_set",
                protectionState: "unknown",
                rosterFingerprint: inspirationFingerprint(i),
                readCompleteness: 1,
                identityMultiplicity: 1,
                unresolvedFields: [],
            }),
        )
    }
    lines.push(
        JSON.stringify({
            type: "roster_scan",
            schemaVersion: 1,
            scanId: "rs-1",
            startedAt: 1_700_000_000_000,
            completedAt: 1_700_000_100_000,
            displayedRegisteredUsed: count,
            displayedRegisteredCapacity: 260,
            filtersOff: true,
            sortKey: "Rating",
            sortDirection: "Desc",
            entryLimit: 0,
            entriesEnumerated: count,
            uniqueFingerprints: count,
            unidentifiedCount: 0,
            duplicateFingerprintCount: 0,
            countDiscrepancy: 0,
            terminationReason: count === 0 ? "empty_list" : "cycle_closed",
            enumerationComplete: true,
            identityComplete: true,
            completeness: "trusted_complete",
            evidenceCropCount: 0,
            app: "1.3.8",
            screenWidth: 1080,
            screenHeight: 1920,
        }),
    )
    return lines.join("\n")
}

describe("parseInspirationRecords", () => {
    it("checks canonical marker multiplicity independently of factor and declaration order", () => {
        const record = JSON.parse(entryLine("s1", 0))
        for (const ancestor of record.legacyAncestors) {
            for (const factor of ancestor.factors.slice(0, 2)) {
                delete factor.canonicalName
                factor.canonicalPath = "reject"
                delete factor.factorFingerprint
                record.unresolvedFields.push(`factorCanonical@${factor.kind}:${factor.rowIndex}:${factor.column}`)
            }
            ancestor.factorSetTrusted = false
            delete ancestor.ancestorFactorFingerprint
        }
        const parsed = parseInspirationRecords(JSON.stringify(record)).entries[0]
        expect(parsed.canonicalDeclarationsConsistent).toBe(true)
        expect(parsed.sparkCaptureConsistent).toBe(true)
        expect(parsed.selfFactorContentConsistent).toBe(true)
        expect(parsed.unresolvedFields).toHaveLength(4)
        record.selfFactors.reverse()
        record.legacyAncestors.reverse().forEach((ancestor: Record<string, any>) => ancestor.factors.reverse())
        record.unresolvedFields.reverse()
        const reordered = parseInspirationRecords(JSON.stringify(record)).entries[0]
        expect(reordered.canonicalDeclarationsConsistent).toBe(true)
        expect(reordered.sparkCaptureConsistent).toBe(true)
        expect(reordered.selfFactorContentConsistent).toBe(true)
    })

    it("preserves complete unresolved self reads while requiring their declaration", () => {
        const record = JSON.parse(entryLine("s1", 0))
        const factor = record.selfFactors[0]
        delete factor.canonicalName
        factor.canonicalPath = "reject"
        delete factor.factorFingerprint
        record.selfFactorSetTrusted = false
        delete record.selfFactorFingerprint
        record.unresolvedFields = [`factorCanonical@${factor.kind}:${factor.rowIndex}:${factor.column}`]
        const coherent = parseInspirationRecords(JSON.stringify(record)).entries[0]
        expect(coherent.canonicalDeclarationsConsistent).toBe(true)
        expect(coherent.sparkCaptureConsistent).toBe(true)
        expect(coherent.selfFactorContentConsistent).toBe(true)
        expect(buildInspirationIndex({ scans: [], entries: [coherent], malformedRecords: 0 }).get(inspirationFingerprint(0))?.selfFactorSetTrusted).toBe(false)
        record.unresolvedFields = []
        const missing = parseInspirationRecords(JSON.stringify(record), "missing-marker.jsonl").entries[0]
        expect(missing.canonicalDeclarationsConsistent).toBe(false)
        expect(missing.sparkCaptureConsistent).toBe(false)
        expect(missing.file).toBe("missing-marker.jsonl")
        expect(missing.selfFactors).toHaveLength(4)
        factor.displayName = ""
        record.unresolvedFields = [`factorName@${factor.kind}:${factor.rowIndex}:${factor.column}`]
        const unread = parseInspirationRecords(JSON.stringify(record)).entries[0]
        expect(unread.canonicalDeclarationsConsistent).toBe(true)
        expect(unread.sparkCaptureConsistent).toBe(false)
    })

    it("trusts a display_alias resolution the Kotlin producer emits, but not an unknown path", () => {
        const record = JSON.parse(entryLine("s1", 0))
        record.selfFactors[3].canonicalPath = "display_alias"
        record.legacyAncestors[0].factors[3].canonicalPath = "display_alias"
        const alias = parseInspirationRecords(JSON.stringify(record)).entries[0]
        expect(alias.selfFactorContentConsistent).toBe(true)
        expect(alias.sparkCaptureConsistent).toBe(true)
        expect(buildInspirationIndex({ scans: [], entries: [alias], malformedRecords: 0 }).get(inspirationFingerprint(0))?.selfFactorSetTrusted).toBe(true)
        record.selfFactors[3].canonicalPath = "phonetic"
        const unknown = parseInspirationRecords(JSON.stringify(record)).entries[0]
        expect(unknown.selfFactorContentConsistent).toBe(false)
        expect(unknown.sparkCaptureConsistent).toBe(false)
        expect(buildInspirationIndex({ scans: [], entries: [unknown], malformedRecords: 0 }).get(inspirationFingerprint(0))?.selfFactorSetTrusted).not.toBe(true)
    })

    it("splits batch headers from per-Veteran entries", () => {
        const parsed = parseInspirationRecords([entryLine("s1", 0), entryLine("s1", 1), headerLine("s1")].join("\n"), "f.jsonl")
        expect(parsed.entries).toHaveLength(2)
        expect(parsed.scans).toHaveLength(1)
        expect(parsed.malformedRecords).toBe(0)
        expect(parsed.entries[0].file).toBe("f.jsonl")
    })

    it("keeps the self factors and the two ancestor blocks apart", () => {
        const entry = parseInspirationRecords(entryLine("s1", 0)).entries[0]
        expect(entry.selfFactors.map((f) => f.displayName)).toEqual(["Power", "Mile", "Shooting for Victory!", "Yasuda Kinen"])
        expect(entry.legacyAncestors).toHaveLength(2)
        expect(entry.legacyAncestors[0].factors[2].displayName).toBe("Dancing in the Leaves")
        expect(entry.legacyAncestors[1].factors).toHaveLength(2)
    })

    it("leaves ancestor rank unresolved rather than defaulting it", () => {
        const entry = parseInspirationRecords(entryLine("s1", 0)).entries[0]
        expect(entry.legacyAncestors.every((a) => a.rank === null)).toBe(true)
    })

    it("counts malformed and unknown lines instead of failing", () => {
        const text = [entryLine("s1", 0), "{ not json", JSON.stringify({ type: "something_else" }), ""].join("\n")
        const parsed = parseInspirationRecords(text)
        expect(parsed.entries).toHaveLength(1)
        expect(parsed.malformedRecords).toBe(2)
    })

    it("drops a record with no scanId rather than inventing one", () => {
        const parsed = parseInspirationRecords(entryLine("s1", 0, { scanId: undefined }))
        expect(parsed.entries).toHaveLength(0)
        expect(parsed.malformedRecords).toBe(1)
    })

    it("drops a factor row with no kind or fingerprint rather than inventing one", () => {
        const parsed = parseInspirationRecords(entryLine("s1", 0, { selfFactors: [...SELF, { rowIndex: 9, stars: 2 }] }))
        expect(parsed.entries[0].selfFactors).toHaveLength(SELF.length)
    })

    it("reads the batch's roster binding, including an incompatible one", () => {
        const scan = parseInspirationRecords(headerLine("s1", { registeredUsedAtEnd: 258, snapshotCompatibility: false })).scans[0]
        expect(scan.snapshotCompatibility).toBe(false)
        expect(scan.registeredUsedAtStart).toBe(257)
        expect(scan.registeredUsedAtEnd).toBe(258)
    })
})

describe("buildInspirationIndex", () => {
    it("keys one view per Veteran by its roster fingerprint", () => {
        const index = buildInspirationIndex(parseInspirationRecords([entryLine("s1", 0), entryLine("s1", 1)].join("\n")))
        expect([...index.keys()].sort()).toEqual([inspirationFingerprint(0), inspirationFingerprint(1)].sort())
        expect(index.get(inspirationFingerprint(0))!.legacyAncestorFactorCounts).toEqual([4, 2])
    })

    it("never lets a newer partial read displace an older complete one", () => {
        // The factor set is immutable, so a partial re-read is not fresher information about the same
        // Veteran - it is less information about it.
        const complete = entryLine("s1", 0, { observedAt: 1000 })
        const partial = entryLine("s2", 0, { observedAt: 9999, sparkCaptureComplete: false, unresolvedFields: ["contentGap"] })
        const index = buildInspirationIndex(parseInspirationRecords([complete, partial].join("\n")))
        expect(index.get(inspirationFingerprint(0))!.sparkCaptureComplete).toBe(true)
        expect(index.get(inspirationFingerprint(0))!.observedAt).toBe(1000)
    })

    it("prefers the newest read among equally complete ones", () => {
        const older = entryLine("s1", 0, { observedAt: 1000 })
        const newer = entryLine("s2", 0, { observedAt: 2000 })
        const index = buildInspirationIndex(parseInspirationRecords([older, newer].join("\n")))
        expect(index.get(inspirationFingerprint(0))!.observedAt).toBe(2000)
    })

    it("ignores a capture whose own identity did not resolve", () => {
        const index = buildInspirationIndex(parseInspirationRecords(entryLine("s1", 0, { rosterFingerprint: undefined })))
        expect(index.size).toBe(0)
    })

    it("never lets a newer complete-but-untrusted read displace an older complete trusted one", () => {
        // Trust ranks above recency: a self set that canonicalized is an identity, a newer one that did
        // not is a noisier read of the same immutable factors, so it must not win on the clock alone.
        const trusted = entryLine("s1", 0, { observedAt: 1000 })
        const untrusted = entryLine("s2", 0, { observedAt: 9999, selfFactorSetTrusted: false, selfFactorFingerprint: null })
        const index = buildInspirationIndex(parseInspirationRecords([trusted, untrusted].join("\n")))
        expect(index.get(inspirationFingerprint(0))!.selfFactorSetTrusted).toBe(true)
        expect(index.get(inspirationFingerprint(0))!.selfFactorFingerprint).toBe(factorSetFingerprint(SELF))
    })

    it("prefers a snapshot-compatible capture over an incompatible newer one", () => {
        // An incompatible batch may have read an identity that a mid-batch registration had shifted, so
        // a clean batch's older read is the more trustworthy attribution.
        const good = [fullHeader("ok"), entryLine("ok", 0, { observedAt: 1000 })]
        const bad = [headerLine("shifted", { snapshotCompatibility: false }), entryLine("shifted", 0, { observedAt: 9999 })]
        const index = buildInspirationIndex(parseInspirationRecords([...good, ...bad].join("\n")))
        expect(index.get(inspirationFingerprint(0))!.observedAt).toBe(1000)
    })

    it("matching counts without cycle proof leave a partial capture individually visible but incompatible", () => {
        const partial = headerLine("partial", { registeredUsedAtStart: 1, registeredUsedAtEnd: 1, entriesCaptured: 1, entryLimit: 1, snapshotCompatibility: true })
        const parsed = parseInspirationRecords([partial, entryLine("partial", 0)].join("\n"))
        expect(parsed.scans[0].pagerCycleClosed).toBe(false)
        const view = buildInspirationIndex(parsed).get(inspirationFingerprint(0))
        expect(view).toBeDefined()
        expect(view?.snapshotCompatible).toBe(false)
        const full = buildInspirationIndex(parseInspirationRecords([fullHeader("full"), entryLine("full", 0)].join("\n"))).get(inspirationFingerprint(0))
        expect(full?.snapshotCompatible).toBe(true)
    })

    it("still uses an incompatible capture when it is the only evidence for a Veteran", () => {
        const only = [headerLine("shifted", { snapshotCompatibility: false }), entryLine("shifted", 0)]
        const index = buildInspirationIndex(parseInspirationRecords(only.join("\n")))
        expect(index.get(inspirationFingerprint(0))).toBeDefined()
    })
})

describe("detectInspirationConflicts", () => {
    it("flags a Veteran with two contradicting trusted captures", () => {
        const weaker = SELF.slice(0, -1)
        const a = [fullHeader("s1"), entryLine("s1", 0)]
        const b = [fullHeader("s2"), entryLine("s2", 0, { selfFactors: weaker })]
        const conflicts = detectInspirationConflicts(parseInspirationRecords([...a, ...b].join("\n")))
        expect(conflicts.get(inspirationFingerprint(0))).toEqual([factorSetFingerprint(weaker), factorSetFingerprint(SELF)].sort())
    })

    it("does not flag agreeing trusted captures", () => {
        const a = [fullHeader("s1"), entryLine("s1", 0)]
        const b = [fullHeader("s2"), entryLine("s2", 0)]
        const conflicts = detectInspirationConflicts(parseInspirationRecords([...a, ...b].join("\n")))
        expect(conflicts.size).toBe(0)
    })

    it("ignores an untrusted or incompatible capture when judging conflict", () => {
        // Only compatible, complete, self-trusted reads carry an identity strong enough to contradict
        // another - a noisy or incompatible read is not evidence of a real disagreement.
        const trusted = [fullHeader("s1"), entryLine("s1", 0)]
        const untrusted = [fullHeader("s2"), entryLine("s2", 0, { selfFactors: SELF.slice(0, -1), selfFactorSetTrusted: false, selfFactorFingerprint: null })]
        const incompatible = [headerLine("s3", { snapshotCompatibility: false }), entryLine("s3", 0, { selfFactors: SELF.slice(1) })]
        const conflicts = detectInspirationConflicts(parseInspirationRecords([...trusted, ...untrusted, ...incompatible].join("\n")))
        expect(conflicts.size).toBe(0)
    })
})

describe("joinInspirationToRoster", () => {
    const snapshot = buildRosterSnapshots(parseRosterScanRecords(rosterCorpus(3)))[0]

    it("reports coverage of the roster and the factors behind it", () => {
        const index = buildInspirationIndex(parseInspirationRecords([entryLine("s1", 0), entryLine("s1", 1)].join("\n")))
        const coverage = joinInspirationToRoster(snapshot, index)
        expect(coverage.identifiedRosterEntries).toBe(3)
        expect(coverage.captured).toBe(2)
        expect(coverage.capturedComplete).toBe(2)
        expect(coverage.missing).toBe(1)
        expect(coverage.totalSelfFactors).toBe(8)
        expect(coverage.totalAncestorFactors).toBe(12)
    })

    it("counts a capture from another roster state as an orphan instead of adding it", () => {
        // The roster snapshot is the authority on what the account owns; a capture is only ever
        // evidence about one of its members.
        const index = buildInspirationIndex(parseInspirationRecords([entryLine("s1", 0), entryLine("s1", 7)].join("\n")))
        const coverage = joinInspirationToRoster(snapshot, index)
        expect(coverage.captured).toBe(1)
        expect(coverage.orphanCaptures).toBe(1)
    })

    it("counts an incomplete capture separately and excludes it from the factor totals", () => {
        const index = buildInspirationIndex(
            parseInspirationRecords([entryLine("s1", 0), entryLine("s1", 1, { sparkCaptureComplete: false, unresolvedFields: ["contentGap"] })].join("\n")),
        )
        const coverage = joinInspirationToRoster(snapshot, index)
        expect(coverage.capturedComplete).toBe(1)
        expect(coverage.capturedIncomplete).toBe(1)
        expect(coverage.totalSelfFactors).toBe(4)
    })

    it("is deterministic regardless of the order the records were read in", () => {
        const forward = parseInspirationRecords([entryLine("s1", 0), entryLine("s1", 1)].join("\n"))
        const reversed = parseInspirationRecords([entryLine("s1", 1), entryLine("s1", 0)].join("\n"))
        expect(joinInspirationToRoster(snapshot, buildInspirationIndex(forward)).views.map((v) => v.rosterFingerprint)).toEqual(
            joinInspirationToRoster(snapshot, buildInspirationIndex(reversed)).views.map((v) => v.rosterFingerprint),
        )
    })
})

describe("ancestorStarSignature", () => {
    it("describes an ancestor without using any OCR text", () => {
        // The Legacy Select reader (PL-4) returns names like "OPower" and bare "O", and a factor name
        // from either source is only about 97% stable across re-reads. Kinds and star counts are
        // pixel-classified and exact, so a cross-source match has to be built on those.
        const parsed = parseInspirationRecords(entryLine("s1", 0)).entries[0]
        expect(ancestorStarSignature(parsed.legacyAncestors[0].factors)).toBe("stat:1|aptitude:2|unique:3|white:2")
    })

    it("ignores the order the white factors were read in", () => {
        const a = [factor("stat", "Power", 1, 0, "left"), factor("white", "A", 2, 1, "left"), factor("white", "B", 1, 1, "right")]
        const b = [factor("white", "B", 1, 0, "left"), factor("stat", "Power", 1, 1, "left"), factor("white", "A", 2, 1, "right")]
        expect(ancestorStarSignature(a)).toBe(ancestorStarSignature(b))
    })

    it("marks a missing lead factor rather than shifting the others up", () => {
        expect(ancestorStarSignature([factor("stat", "Power", 1, 0, "left")])).toBe("stat:1|aptitude:-|unique:-|white:")
    })
})
