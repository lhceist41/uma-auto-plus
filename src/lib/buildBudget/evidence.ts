// STAM-2 Joint Build Budget Planner - the decoded evidence layer. Pure, offline, deterministic.
//
// Parses and indexes src/data/build_budget_data.json, the asset
// scripts/generate-build-budget-data.mjs extracts from the installed master.mdb. Everything in here
// is DECODED_GAME_DATA: nothing is estimated, weighted or combined at this layer, because the moment
// a decoded number is mixed with an editorial one a reader can no longer tell them apart.
//
// The one join that matters is factor-name to factor-group. The Veteran reader produces a canonical
// factor name for what it read off a Spark card, and the game's own Spark names come out of the same
// text table those canonical names were generated from, so the two match on a normalized comparison.
// Where they do not, the factor is reported unresolved rather than guessed at: an unpriced Spark is a
// visible gap, a mis-priced one is a wrong recommendation.

import { readFileSync } from "node:fs"
import { BUDGET_STATS, BuildBudgetError, type BudgetStat } from "./types.ts"

export const BUILD_BUDGET_EVIDENCE_SCHEMA = "build_budget_evidence"
export const BUILD_BUDGET_EVIDENCE_SCHEMA_VERSION = 1

/** One rung of a factor group's effect ladder, as the game ships it. */
export interface FactorEffect {
    readonly targetType: number
    readonly kind: string
    readonly stat: BudgetStat | null
    readonly aptitude: string | null
    readonly levels: number
    readonly value1: readonly number[]
    readonly value2: readonly number[]
}

export interface FactorGroup {
    readonly groupId: number
    readonly factorType: number
    readonly family: string
    readonly subfamily: string
    readonly canonicalName: string
    readonly maxRarity: number
    readonly factorIds: readonly number[]
    readonly effects: readonly FactorEffect[]
}

export interface TraineeGrowthRecord {
    readonly cardId: number
    readonly charaId: number
    readonly character: string | null
    readonly outfit: string | null
    readonly growth: Readonly<Record<string, number>>
    readonly runningStyle: number
}

export interface TraineeBaseRecord {
    readonly cardId: number
    readonly starLevel: number
    readonly startStats: Readonly<Record<BudgetStat, number>>
    readonly aptitudes: Readonly<Record<string, string>>
}

export interface TrainingEffectRecord {
    readonly scenarioId: number
    readonly commandId: number
    readonly subId: number
    readonly resultState: number
    readonly trainingType: BudgetStat | null
    readonly isCamp: boolean
    readonly stats: Readonly<Record<BudgetStat, number>>
    readonly energy: number
    readonly mood: number
    readonly skillPoints: number
    readonly failureRateByLevel: readonly number[]
}

export interface StatCapEvidence {
    readonly baseline: number
    readonly scenarioBonus: readonly { readonly scenarioId: number; readonly bonus: Readonly<Record<BudgetStat, number>> }[]
}

export interface CareerTurnRecord {
    readonly scenarioId: number
    readonly turnSetId: number
    readonly totalTurns: number
}

export interface BuildBudgetEvidenceDocument {
    readonly schema: string
    readonly schemaVersion: number
    readonly source: string
    readonly statCaps: StatCapEvidence
    readonly factorGroups: readonly FactorGroup[]
    readonly traineeGrowth: readonly TraineeGrowthRecord[]
    readonly traineeBase: readonly TraineeBaseRecord[]
    readonly trainingEffects: readonly TrainingEffectRecord[]
    readonly careerTurns: readonly CareerTurnRecord[]
}

/** The parsed asset plus the lookups the planner actually reads it through. */
export interface BuildBudgetEvidence {
    readonly document: BuildBudgetEvidenceDocument
    readonly schemaVersion: number
    /** Factor group by normalized canonical name within a family. Null when the name does not resolve. */
    factorGroup(family: string, canonicalName: string): FactorGroup | null
    /** Growth and identity for a trainee outfit, by card id. */
    growthFor(cardId: number): TraineeGrowthRecord | null
    /** Every outfit of a character, by normalized character name, in card id order. */
    outfitsFor(character: string): readonly TraineeGrowthRecord[]
    baseFor(cardId: number, starLevel: number): TraineeBaseRecord | null
    /** Every star level shipped for an outfit, ascending. */
    starLevelsFor(cardId: number): readonly number[]
    /** The base outcome of one stat training in one scenario, on the ordinary board. */
    baseTraining(scenarioId: number, stat: BudgetStat): TrainingEffectRecord | null
    scenarioCapBonus(scenarioId: number): Readonly<Record<BudgetStat, number>> | null
    careerTurns(scenarioId: number): number | null
}

/**
 * Normalizes a name for the factor-name join.
 *
 * The Veteran reader's canonical names come from the same shipped strings as the game's own Spark
 * names, but a factor card can show a skill with its grade glyph while the canonical domain strips
 * it, and case and spacing differ. Folding all three is enough for the two to meet; nothing looser is
 * used, because a fuzzy match here would silently price the wrong Spark.
 */
export function normalizeFactorName(value: string): string {
    return value
        .normalize("NFKC")
        .replace(/[■-◿☀-➿〇○◎×]/g, "")
        .replace(/[^0-9a-zA-Z]+/g, "")
        .toUpperCase()
}

function requireArray(raw: Record<string, unknown>, key: string): unknown[] {
    const value = raw[key]
    if (!Array.isArray(value)) throw new BuildBudgetError("malformedEvidence", `build budget evidence has no ${key} array`)
    return value
}

function statRecord(raw: unknown, context: string): Record<BudgetStat, number> {
    if (!raw || typeof raw !== "object") throw new BuildBudgetError("malformedEvidence", `${context} is not a stat record`)
    const source = raw as Record<string, unknown>
    const out = {} as Record<BudgetStat, number>
    for (const stat of BUDGET_STATS) {
        const value = source[stat]
        if (typeof value !== "number" || !Number.isFinite(value)) throw new BuildBudgetError("malformedEvidence", `${context} has no numeric ${stat}`)
        out[stat] = value
    }
    return out
}

/** Parses the asset text. Throws rather than defaulting: a half-read evidence file is not evidence. */
export function createBuildBudgetEvidence(text: string): BuildBudgetEvidence {
    let parsed: unknown
    try {
        parsed = JSON.parse(text)
    } catch (e) {
        throw new BuildBudgetError("malformedEvidence", `build budget evidence is not valid JSON: ${e instanceof Error ? e.message : String(e)}`)
    }
    if (!parsed || typeof parsed !== "object") throw new BuildBudgetError("malformedEvidence", "build budget evidence is not an object")
    const raw = parsed as Record<string, unknown>
    if (raw.schema !== BUILD_BUDGET_EVIDENCE_SCHEMA) {
        throw new BuildBudgetError("wrongSchema", `expected schema ${BUILD_BUDGET_EVIDENCE_SCHEMA}, got ${String(raw.schema)}`)
    }
    if (raw.schemaVersion !== BUILD_BUDGET_EVIDENCE_SCHEMA_VERSION) {
        throw new BuildBudgetError("wrongSchemaVersion", `expected schema version ${BUILD_BUDGET_EVIDENCE_SCHEMA_VERSION}, got ${String(raw.schemaVersion)}`)
    }

    const capsRaw = raw.statCaps as Record<string, unknown> | undefined
    if (!capsRaw || typeof capsRaw.baseline !== "number") throw new BuildBudgetError("malformedEvidence", "build budget evidence has no statCaps.baseline")
    const statCaps: StatCapEvidence = {
        baseline: capsRaw.baseline,
        scenarioBonus: (Array.isArray(capsRaw.scenarioBonus) ? capsRaw.scenarioBonus : []).map((entry) => {
            const row = entry as Record<string, unknown>
            return { scenarioId: Number(row.scenarioId), bonus: statRecord(row.bonus, `scenario ${String(row.scenarioId)} cap bonus`) }
        }),
    }

    const factorGroups = requireArray(raw, "factorGroups").map((entry) => {
        const row = entry as Record<string, unknown>
        return {
            groupId: Number(row.groupId),
            factorType: Number(row.factorType),
            family: String(row.family),
            subfamily: String(row.subfamily),
            canonicalName: String(row.canonicalName),
            maxRarity: Number(row.maxRarity),
            factorIds: (row.factorIds as number[]) ?? [],
            effects: ((row.effects as Record<string, unknown>[]) ?? []).map((effect) => ({
                targetType: Number(effect.targetType),
                kind: String(effect.kind),
                stat: (effect.stat as BudgetStat | null) ?? null,
                aptitude: (effect.aptitude as string | null) ?? null,
                levels: Number(effect.levels),
                value1: (effect.value1 as number[]) ?? [],
                value2: (effect.value2 as number[]) ?? [],
            })),
        } satisfies FactorGroup
    })

    const traineeGrowth = requireArray(raw, "traineeGrowth").map((entry) => {
        const row = entry as Record<string, unknown>
        return {
            cardId: Number(row.cardId),
            charaId: Number(row.charaId),
            character: (row.character as string | null) ?? null,
            outfit: (row.outfit as string | null) ?? null,
            growth: (row.growth as Record<string, number>) ?? {},
            runningStyle: Number(row.runningStyle),
        } satisfies TraineeGrowthRecord
    })

    const traineeBase = requireArray(raw, "traineeBase").map((entry) => {
        const row = entry as Record<string, unknown>
        return {
            cardId: Number(row.cardId),
            starLevel: Number(row.starLevel),
            startStats: statRecord(row.startStats, `card ${String(row.cardId)} start stats`),
            aptitudes: (row.aptitudes as Record<string, string>) ?? {},
        } satisfies TraineeBaseRecord
    })

    const trainingEffects = requireArray(raw, "trainingEffects").map((entry) => {
        const row = entry as Record<string, unknown>
        return {
            scenarioId: Number(row.scenarioId),
            commandId: Number(row.commandId),
            subId: Number(row.subId),
            resultState: Number(row.resultState),
            trainingType: (row.trainingType as BudgetStat | null) ?? null,
            isCamp: Boolean(row.isCamp),
            stats: statRecord(row.stats, `training ${String(row.commandId)} in scenario ${String(row.scenarioId)}`),
            energy: Number(row.energy),
            mood: Number(row.mood),
            skillPoints: Number(row.skillPoints),
            failureRateByLevel: (row.failureRateByLevel as number[]) ?? [],
        } satisfies TrainingEffectRecord
    })

    const careerTurns = requireArray(raw, "careerTurns").map((entry) => {
        const row = entry as Record<string, unknown>
        return { scenarioId: Number(row.scenarioId), turnSetId: Number(row.turnSetId), totalTurns: Number(row.totalTurns) } satisfies CareerTurnRecord
    })

    const document: BuildBudgetEvidenceDocument = {
        schema: BUILD_BUDGET_EVIDENCE_SCHEMA,
        schemaVersion: BUILD_BUDGET_EVIDENCE_SCHEMA_VERSION,
        source: String(raw.source ?? ""),
        statCaps,
        factorGroups,
        traineeGrowth,
        traineeBase,
        trainingEffects,
        careerTurns,
    }

    const byFamilyName = new Map<string, FactorGroup>()
    for (const group of factorGroups) {
        // A name can legitimately appear in two families (a scenario Spark and a race Spark can share
        // wording), so the key carries the family. Within one family the first shipped group wins and
        // a later duplicate is left unresolvable rather than silently overwriting.
        const key = `${group.family}|${normalizeFactorName(group.canonicalName)}`
        if (!byFamilyName.has(key)) byFamilyName.set(key, group)
    }

    const growthByCard = new Map(traineeGrowth.map((row) => [row.cardId, row]))
    const outfitsByCharacter = new Map<string, TraineeGrowthRecord[]>()
    for (const row of traineeGrowth) {
        if (!row.character) continue
        const key = normalizeFactorName(row.character)
        const held = outfitsByCharacter.get(key) ?? []
        held.push(row)
        outfitsByCharacter.set(key, held)
    }
    for (const list of outfitsByCharacter.values()) list.sort((a, b) => a.cardId - b.cardId)

    const baseByCardStar = new Map(traineeBase.map((row) => [`${row.cardId}|${row.starLevel}`, row]))
    const starsByCard = new Map<number, number[]>()
    for (const row of traineeBase) {
        const held = starsByCard.get(row.cardId) ?? []
        held.push(row.starLevel)
        starsByCard.set(row.cardId, held)
    }
    for (const list of starsByCard.values()) list.sort((a, b) => a - b)

    // The ordinary board only: subId 1, a success, not a camp training. Camp rows stay in the document
    // for a reader, but a projection that quietly used camp gains would overstate every build.
    const baseTrainingByKey = new Map<string, TrainingEffectRecord>()
    for (const row of trainingEffects) {
        if (row.subId !== 1 || row.resultState !== 2 || row.isCamp || !row.trainingType) continue
        baseTrainingByKey.set(`${row.scenarioId}|${row.trainingType}`, row)
    }

    const capsByScenario = new Map(statCaps.scenarioBonus.map((row) => [row.scenarioId, row.bonus]))
    const turnsByScenario = new Map(careerTurns.map((row) => [row.scenarioId, row.totalTurns]))

    return {
        document,
        schemaVersion: document.schemaVersion,
        factorGroup: (family, canonicalName) => byFamilyName.get(`${family}|${normalizeFactorName(canonicalName)}`) ?? null,
        growthFor: (cardId) => growthByCard.get(cardId) ?? null,
        outfitsFor: (character) => outfitsByCharacter.get(normalizeFactorName(character)) ?? [],
        baseFor: (cardId, starLevel) => baseByCardStar.get(`${cardId}|${starLevel}`) ?? null,
        starLevelsFor: (cardId) => starsByCard.get(cardId) ?? [],
        baseTraining: (scenarioId, stat) => baseTrainingByKey.get(`${scenarioId}|${stat}`) ?? null,
        scenarioCapBonus: (scenarioId) => capsByScenario.get(scenarioId) ?? null,
        careerTurns: (scenarioId) => turnsByScenario.get(scenarioId) ?? null,
    }
}

export function loadBuildBudgetEvidence(path: string): BuildBudgetEvidence {
    let text: string
    try {
        text = readFileSync(path, "utf8")
    } catch (e) {
        throw new BuildBudgetError("missingEvidence", `cannot read ${path}: ${e instanceof Error ? e.message : String(e)}`)
    }
    return createBuildBudgetEvidence(text)
}
