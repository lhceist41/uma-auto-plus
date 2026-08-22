// ParentLab PL-R4 - the succession-relation data the affinity advisor reads. Pure, offline,
// deterministic, read-only.
//
// The payload is produced by `scripts/generate-succession-relation-data.mjs` from an installed
// master.mdb and committed as `src/data/succession_relations.json`. This module only parses it and
// answers one question: how many relation points do two characters share.
//
// The scope of that answer is the whole point of this file. Two characters that both belong to a
// relation type share that type's points, and summing every shared type gives the pairwise base
// relation between them. That number is a fact from the game's own tables. It is NOT the affinity
// total the game displays over a lineage: the shipped rank bands run to 151+, and the highest pair
// the tables can produce is 43, so the displayed total demonstrably aggregates several relations.
// Which relations, with what weights, and what the race-result contribution is are not in the
// database, so this module deliberately offers no function that turns a partial sum into a rank.
//
// No RaceLab/ReplayLab/Android/runtime imports: leaf analysis module.

/** Schema discriminator + version for the succession-relation payload. Separate from every other
 * ParentLab schema: none of them is touched by this stage. */
export const PARENTLAB_SUCCESSION_RELATION_SCHEMA = "parent_lab_succession_relations" as const
export const PARENTLAB_SUCCESSION_RELATION_SCHEMA_VERSION = 1 as const

/** One of the game's own relation-total rank bands. Carried as reference data only: nothing in this
 * repository can compute a total that is comparable to it. */
export interface SuccessionRelationRankBand {
    readonly rank: number
    readonly minValue: number
    readonly maxValue: number
}

/** One relation type and the characters that belong to it. A type with fewer than two members can
 * never be shared, and so contributes to no pair. */
export interface SuccessionRelationType {
    readonly relationType: number
    readonly relationPoint: number
    readonly members: readonly number[]
}

export interface SuccessionRelationDiagnostics {
    readonly relationTypes: number
    readonly relationTypesWithMembers: number
    readonly relationTypesWithoutMembers: number
    readonly pairwiseCapableTypes: number
    readonly memberRows: number
    readonly charactersWithMembership: number
    /** The highest pairwise total the shipped tables can produce, across every character pair. */
    readonly maxPairwisePoints: number
    /** Lowest value of the top rank band. Compare against `maxPairwisePoints` to see for yourself
     * that one pair cannot reach it. */
    readonly topRankBandMinValue: number
}

export interface SuccessionRelationData {
    readonly schema: typeof PARENTLAB_SUCCESSION_RELATION_SCHEMA
    readonly schemaVersion: number
    readonly source: string
    readonly rankBands: readonly SuccessionRelationRankBand[]
    /** English character name -> chara_id, for every character with at least one relation membership. */
    readonly characters: Readonly<Record<string, number>>
    readonly relations: readonly SuccessionRelationType[]
    readonly diagnostics: SuccessionRelationDiagnostics
}

/** A parse failure. The payload is generated data: a malformed one is a bug, not a degraded input. */
export class SuccessionRelationDataError extends Error {
    constructor(message: string) {
        super(message)
        this.name = "SuccessionRelationDataError"
    }
}

/**
 * Identity key for a character name.
 *
 * The roster reader's canonical names and the game database's own names differ in punctuation for at
 * least one character ("TM Opera O" against "T.M. Opera O"), so the key drops everything that is not
 * a letter or a digit and upper-cases the rest. Nothing else is normalized: two genuinely different
 * names must not collide, and the index build rejects it if they do.
 */
export function normalizeCharacterName(name: string): string {
    return name.replace(/[^A-Za-z0-9]/g, "").toUpperCase()
}

/** The pairwise relation between two characters, with the shared types that produced it. */
export interface PairwiseRelation {
    readonly charaIdA: number
    readonly charaIdB: number
    readonly points: number
    /** The relation types both characters belong to, ascending. */
    readonly sharedRelationTypes: readonly number[]
}

export interface SuccessionRelationIndex {
    readonly data: SuccessionRelationData
    /** Normalized name -> chara_id. */
    readonly charaIdByName: ReadonlyMap<string, number>
    /** chara_id -> English name as the database spells it. */
    readonly nameByCharaId: ReadonlyMap<number, string>
    /** chara_id -> (relation type -> point). */
    readonly membershipByCharaId: ReadonlyMap<number, ReadonlyMap<number, number>>
}

function asRecord(value: unknown, what: string): Record<string, unknown> {
    if (typeof value !== "object" || value === null || Array.isArray(value)) throw new SuccessionRelationDataError(`${what} is not an object`)
    return value as Record<string, unknown>
}

function asInt(value: unknown, what: string): number {
    if (typeof value !== "number" || !Number.isFinite(value) || !Number.isInteger(value)) throw new SuccessionRelationDataError(`${what} is not an integer`)
    return value
}

/**
 * Parses and validates the committed payload. Strict on purpose: a field that is missing or the wrong
 * type means the file and this reader have drifted, and continuing on a partial parse would silently
 * lower every pair's relation points rather than fail.
 */
export function parseSuccessionRelationData(raw: unknown): SuccessionRelationData {
    const root = asRecord(raw, "succession relation payload")
    if (root.schema !== PARENTLAB_SUCCESSION_RELATION_SCHEMA) {
        throw new SuccessionRelationDataError(`unexpected schema ${String(root.schema)}, wanted ${PARENTLAB_SUCCESSION_RELATION_SCHEMA}`)
    }
    const schemaVersion = asInt(root.schemaVersion, "schemaVersion")
    if (schemaVersion !== PARENTLAB_SUCCESSION_RELATION_SCHEMA_VERSION) {
        throw new SuccessionRelationDataError(`unsupported schemaVersion ${schemaVersion}, wanted ${PARENTLAB_SUCCESSION_RELATION_SCHEMA_VERSION}`)
    }
    if (typeof root.source !== "string") throw new SuccessionRelationDataError("source is not a string")

    if (!Array.isArray(root.rankBands)) throw new SuccessionRelationDataError("rankBands is not an array")
    const rankBands: SuccessionRelationRankBand[] = root.rankBands.map((b, i) => {
        const band = asRecord(b, `rankBands[${i}]`)
        return { rank: asInt(band.rank, `rankBands[${i}].rank`), minValue: asInt(band.minValue, `rankBands[${i}].minValue`), maxValue: asInt(band.maxValue, `rankBands[${i}].maxValue`) }
    })

    const charactersRaw = asRecord(root.characters, "characters")
    const characters: Record<string, number> = {}
    for (const [name, id] of Object.entries(charactersRaw)) characters[name] = asInt(id, `characters["${name}"]`)

    if (!Array.isArray(root.relations)) throw new SuccessionRelationDataError("relations is not an array")
    const relations: SuccessionRelationType[] = root.relations.map((r, i) => {
        const rel = asRecord(r, `relations[${i}]`)
        if (!Array.isArray(rel.members)) throw new SuccessionRelationDataError(`relations[${i}].members is not an array`)
        return {
            relationType: asInt(rel.relationType, `relations[${i}].relationType`),
            relationPoint: asInt(rel.relationPoint, `relations[${i}].relationPoint`),
            members: rel.members.map((m, j) => asInt(m, `relations[${i}].members[${j}]`)),
        }
    })

    const diag = asRecord(root.diagnostics, "diagnostics")
    const diagnostics: SuccessionRelationDiagnostics = {
        relationTypes: asInt(diag.relationTypes, "diagnostics.relationTypes"),
        relationTypesWithMembers: asInt(diag.relationTypesWithMembers, "diagnostics.relationTypesWithMembers"),
        relationTypesWithoutMembers: asInt(diag.relationTypesWithoutMembers, "diagnostics.relationTypesWithoutMembers"),
        pairwiseCapableTypes: asInt(diag.pairwiseCapableTypes, "diagnostics.pairwiseCapableTypes"),
        memberRows: asInt(diag.memberRows, "diagnostics.memberRows"),
        charactersWithMembership: asInt(diag.charactersWithMembership, "diagnostics.charactersWithMembership"),
        maxPairwisePoints: asInt(diag.maxPairwisePoints, "diagnostics.maxPairwisePoints"),
        topRankBandMinValue: asInt(diag.topRankBandMinValue, "diagnostics.topRankBandMinValue"),
    }

    return { schema: PARENTLAB_SUCCESSION_RELATION_SCHEMA, schemaVersion, source: root.source, rankBands, characters, relations, diagnostics }
}

/** Builds the lookup index. Rejects a name collision rather than letting one character's relations be
 * attributed to another. */
export function buildSuccessionRelationIndex(data: SuccessionRelationData): SuccessionRelationIndex {
    const charaIdByName = new Map<string, number>()
    const nameByCharaId = new Map<number, string>()
    for (const [name, id] of Object.entries(data.characters)) {
        const key = normalizeCharacterName(name)
        const existing = charaIdByName.get(key)
        if (existing !== undefined && existing !== id) {
            throw new SuccessionRelationDataError(`character key "${key}" maps to both chara_id ${existing} and ${id}`)
        }
        charaIdByName.set(key, id)
        if (!nameByCharaId.has(id)) nameByCharaId.set(id, name)
    }

    const membership = new Map<number, Map<number, number>>()
    for (const relation of data.relations) {
        for (const id of relation.members) {
            let map = membership.get(id)
            if (!map) {
                map = new Map()
                membership.set(id, map)
            }
            map.set(relation.relationType, relation.relationPoint)
        }
    }

    return { data, charaIdByName, nameByCharaId, membershipByCharaId: membership }
}

/** Resolves a character name to its chara_id, or null when the name is not in the relation domain.
 * Null is a real answer: a character the shipped tables do not cover has no computable relation. */
export function resolveCharaId(index: SuccessionRelationIndex, name: string | null | undefined): number | null {
    if (!name) return null
    return index.charaIdByName.get(normalizeCharacterName(name)) ?? null
}

/**
 * The pairwise base relation between two characters.
 *
 * Returns null when either side is not in the relation domain, which is different from a relation of
 * zero: an unresolved character is unmeasured, and the advisor must not read it as "not related".
 * A character compared with itself also returns null - inheritance never pairs a character with
 * itself, and summing its own memberships would produce a large meaningless number.
 */
export function pairwiseRelation(index: SuccessionRelationIndex, charaIdA: number | null, charaIdB: number | null): PairwiseRelation | null {
    if (charaIdA === null || charaIdB === null || charaIdA === charaIdB) return null
    const a = index.membershipByCharaId.get(charaIdA)
    const b = index.membershipByCharaId.get(charaIdB)
    if (!a || !b) return null
    let points = 0
    const shared: number[] = []
    for (const [type, point] of a) {
        if (!b.has(type)) continue
        points += point
        shared.push(type)
    }
    shared.sort((x, y) => x - y)
    return { charaIdA, charaIdB, points, sharedRelationTypes: shared }
}
