// STAM-2 Joint Build Budget Planner - offline, read-only CLI.
//
// Answers one question: for this race, this trainee and this account, which parent pair, support deck
// and borrow best pay for the Stamina the race needs, and what does paying for it cost everywhere
// else. It reads only committed data and account snapshots the other labs already produce, needs no
// device, no emulator and no network, and it changes nothing: no file inside the repository is
// written, no preset or queue is touched, and no bot behaviour depends on any of it.
//
// It is a Shadow tool. It does not select parents, select a deck, borrow a card, emit a Smart Borrow
// intent or start a career.
//
// Usage:
//   node scripts/build-budget.mjs --roster <roster_scan.jsonl> --inspiration <veteran_inspiration.jsonl>
//        --inventory <owned.json> --trainee <name> [--outfit "[Title]"] --scenario <name>
//        --race <name> [--turn <N>] --strategy front|pace|late|end|runaway
//        [--distance sprint|mile|medium|long] [--surface turf|dirt] [--style front|pace|late|end]
//        [--stat "Speed,Stamina"] [--recovery <id,id>] [--debuff-budget 0|1|2] [--margin <fraction>]
//        [--turns <n>] [--top-parents <n>] [--top-decks <n>] [--archetype <name>]
//        [--borrow <owned.json>] [--borrow-pool <snapshot.json>] [--json] [--out <path>]
//
// Race identity, build target and risk policy all mean exactly what they mean in
// scripts/race-survival.mjs and scripts/deck-lab.mjs; this tool joins those two rather than
// redefining either.
//
// Exit: 0 a recommendation was produced | 1 nothing enumerated clears the survival floor | 2 usage or
// load error.
//
// Requires node >= 23.6 (native TypeScript type stripping; the logic lives in src/lib/buildBudget/).

import { existsSync, readFileSync, writeFileSync } from "node:fs"
import { dirname, join, resolve } from "node:path"
import { fileURLToPath } from "node:url"

import { loadRaceCatalog } from "../src/lib/raceLab/catalog.ts"
import { loadMasterDataFromDir } from "../src/lib/masterData/reader.ts"
import { loadRaceSurvivalEvidence } from "../src/lib/raceSurvival/evidence.ts"
import { buildSurvivalConstraint, computeSurvivalEnvelope, createRaceSurvivalInput } from "../src/lib/raceSurvival/envelope.ts"
import { surfaceForTerrain } from "../src/lib/raceSurvival/adapter.ts"
import { RACE_STRATEGIES } from "../src/lib/raceSurvival/types.ts"

import { buildSuccessionRelationIndex, parseSuccessionRelationData } from "../src/lib/parentLab/affinityData.ts"
import { buildInspirationIndex, parseInspirationRecords } from "../src/lib/parentLab/inspiration.ts"
import { buildRosterSnapshots, latestTrustedSnapshot, parseRosterScanRecords } from "../src/lib/parentLab/roster.ts"
import { buildFactorScarcityIndex, buildRetentionEvidence } from "../src/lib/parentLab/retentionEvidence.ts"
import { buildTargetBuild, parseTargetDistance, parseTargetRunningStyle, parseTargetSurface } from "../src/lib/parentLab/targetBuild.ts"
import { affinityMedianOf, enumerateParentPairs, rankParentPairs } from "../src/lib/parentLab/parentPairing.ts"
import { buildParentCandidates } from "../src/lib/parentLab/parentCandidate.ts"

import { buildSupportCardIndex, parseSupportCardData } from "../src/lib/deckLab/supportCardData.ts"
import { buildDeckTarget, parseStatPriority } from "../src/lib/deckLab/deckTarget.ts"
import { buildOwnedInventory } from "../src/lib/deckLab/inventory.ts"
import { searchDecks } from "../src/lib/deckLab/deckSearch.ts"
import { borrowCandidateCards, parseBorrowPoolSnapshot, resolveBorrowPool } from "../src/lib/deckLab/borrowPool.ts"
import { parseBorrowScanJsonl } from "../src/lib/deckLab/borrowScanImport.ts"

import { loadBuildBudgetEvidence } from "../src/lib/buildBudget/evidence.ts"
import { resolveBudgetTrainee, toBudgetParentPair } from "../src/lib/buildBudget/adapter.ts"
import { planJointBuild } from "../src/lib/buildBudget/joint.ts"
import { valueBorrow } from "../src/lib/buildBudget/borrow.ts"
import { formatBorrowBudgetEffect } from "../src/lib/buildBudget/borrow.ts"
import { formatJointBuildRecommendation } from "../src/lib/buildBudget/report.ts"
import { rankBuildAwareBorrows } from "../src/lib/buildBudget/borrowRanking.ts"
import { formatBuildAwareBorrowRanking } from "../src/lib/buildBudget/borrowReport.ts"
import { buildSmartBorrowIntent, serializeSmartBorrowIntent } from "../src/lib/deckLab/smartBorrowIntent.ts"
import { BUILD_ARCHETYPES } from "../src/lib/buildBudget/types.ts"

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..")

/**
 * The Stamina the survival envelope is probed at.
 *
 * The constraint the planner consumes does not depend on it: recovery and debuffs are both fractions
 * of MaxHP, so the required-Stamina solve is a closed form that never reads the probe. It exists only
 * because the envelope reports a margin at some Stamina, and reporting one at an arbitrary round
 * number is clearer than pretending there is no probe at all.
 */
const SURVIVAL_PROBE_STAMINA = 600

const HELP = `build-budget - STAM-2 Joint Build Budget Planner (offline, read-only, Shadow)

Account inputs:
  --roster <path>          ParentLab roster_scan.jsonl (required)
  --inspiration <path>     ParentLab veteran_inspiration.jsonl (required)
  --inventory <path>       DeckLab owned support-card snapshot (required)
  --borrow <path>          A second owned-format snapshot of cards available to borrow
  --borrow-pool <path>     A read-only borrow-pool snapshot from the on-device census

Target:
  --trainee <name>         Trainee character name (required)
  --outfit "[Title]"       Outfit, when the character ships more than one. Growth differs per outfit.
  --scenario <name>        URA Finale | Unity Cup | Grand Concert | Trackblazer
  --distance <d>           sprint | mile | medium | long
  --surface <s>            turf | dirt
  --style <s>              front | pace | late | end
  --stat "Speed,Stamina"   Stat priority for the deck search. Editorial; replaces the distance default.

Race:
  --race <name> [--turn N] Canonical race name from the compiled catalogue (required)
  --strategy <s>           ${RACE_STRATEGIES.join(" | ")} (required)
  --recovery <id,id>       Recovery skill ids the survival minimum may assume
  --debuff-budget 0|1|2    0 = BASE (default), 1 and 2 insure against the worst decoded threat
  --margin <fraction>      Editorial safety margin, fraction of MaxHP (default 0)

Search:
  --turns <n>              Trainings per career. Editorial; defaults to the planner's own constant.
  --top-parents <n>        Parent pairs from ParentLab's frontier to consider (default 5)
  --top-decks <n>          Decks from DeckLab's frontier to consider (default 5)
  --archetype <name>       Restrict to one of ${BUILD_ARCHETYPES.join(", ")}. Repeatable.

Data (all default to the committed assets):
  --cards <path>           src/data/support_cards.json
  --relations <path>       src/data/succession_relations.json
  --budget-data <path>     src/data/build_budget_data.json
  --survival-data <path>   src/data/race_survival_data.json
  --master-data <dir>      src/data/compiled

Build-aware borrow ranking (STAM-2a):
  --build-aware-borrow     Rank the live borrow pool by what it does to the whole build, not just to
                           the deck composite. Needs --borrow-pool or --borrow-scan.
  --emit-intent <path>     Write the recommended borrow as a SmartBorrowIntent JSON. Offline only:
                           nothing in this tool selects a borrow on a device.
  --intent-source <s>      DECKLAB_COMPOSITE or BUILD_AWARE (default: whichever ranking produced the
                           pick, so this only ever forces the provenance label).

Output:
  --json                   Print the recommendation document as JSON
  --out <path>             Write the JSON document here
  --help                   This text
`

function parseArgs(argv) {
    const opts = {
        roster: null,
        inspiration: null,
        inventory: null,
        borrow: null,
        borrowPool: null,
        borrowScan: null,
        buildAwareBorrow: false,
        emitIntent: null,
        trainee: null,
        outfit: null,
        scenario: null,
        distance: null,
        surface: null,
        style: null,
        stat: null,
        race: null,
        turn: null,
        strategy: null,
        recovery: [],
        debuffBudget: "BASE",
        margin: 0,
        turns: null,
        topParents: 5,
        topDecks: 5,
        archetypes: [],
        cards: join(ROOT, "src", "data", "support_cards.json"),
        relations: join(ROOT, "src", "data", "succession_relations.json"),
        budgetData: join(ROOT, "src", "data", "build_budget_data.json"),
        survivalData: join(ROOT, "src", "data", "race_survival_data.json"),
        masterData: join(ROOT, "src", "data", "compiled"),
        json: false,
        out: null,
        help: false,
    }
    const budgets = { 0: "BASE", 1: "ONE_STAMINA_DEBUFF", 2: "TWO_STAMINA_DEBUFFS" }
    for (let i = 0; i < argv.length; i++) {
        const flag = argv[i]
        const next = () => {
            const value = argv[++i]
            if (value === undefined) throw new Error(`${flag} needs a value`)
            return value
        }
        switch (flag) {
            case "--roster":
                opts.roster = next()
                break
            case "--inspiration":
                opts.inspiration = next()
                break
            case "--inventory":
                opts.inventory = next()
                break
            case "--borrow":
                opts.borrow = next()
                break
            case "--borrow-pool":
                opts.borrowPool = next()
                break
            case "--borrow-scan":
                opts.borrowScan = next()
                break
            case "--build-aware-borrow":
                opts.buildAwareBorrow = true
                break
            case "--emit-intent":
                opts.emitIntent = next()
                break
            case "--trainee":
                opts.trainee = next()
                break
            case "--outfit":
                opts.outfit = next()
                break
            case "--scenario":
                opts.scenario = next()
                break
            case "--distance":
                opts.distance = next()
                break
            case "--surface":
                opts.surface = next()
                break
            case "--style":
                opts.style = next()
                break
            case "--stat":
                opts.stat = next()
                break
            case "--race":
                opts.race = next()
                break
            case "--turn":
                opts.turn = Number(next())
                break
            case "--strategy":
                opts.strategy = next()
                break
            case "--recovery":
                opts.recovery = next()
                    .split(",")
                    .map((v) => Number(v.trim()))
                    .filter((v) => Number.isFinite(v))
                break
            case "--debuff-budget": {
                const value = next()
                const named = budgets[value]
                if (!named) throw new Error(`--debuff-budget ${value} is not 0, 1 or 2`)
                opts.debuffBudget = named
                break
            }
            case "--margin":
                opts.margin = Number(next())
                break
            case "--turns":
                opts.turns = Number(next())
                break
            case "--top-parents":
                opts.topParents = Number(next())
                break
            case "--top-decks":
                opts.topDecks = Number(next())
                break
            case "--archetype": {
                const value = next().trim().toUpperCase()
                if (!BUILD_ARCHETYPES.includes(value)) throw new Error(`--archetype ${value} is not one of ${BUILD_ARCHETYPES.join(", ")}`)
                opts.archetypes.push(value)
                break
            }
            case "--cards":
                opts.cards = next()
                break
            case "--relations":
                opts.relations = next()
                break
            case "--budget-data":
                opts.budgetData = next()
                break
            case "--survival-data":
                opts.survivalData = next()
                break
            case "--master-data":
                opts.masterData = next()
                break
            case "--json":
                opts.json = true
                break
            case "--out":
                opts.out = next()
                break
            case "--help":
            case "-h":
                opts.help = true
                break
            default:
                throw new Error(`unknown option ${flag}`)
        }
    }
    return opts
}

function readJson(path, what) {
    if (!existsSync(path)) throw new Error(`no ${what} at ${path}`)
    return JSON.parse(readFileSync(path, "utf8"))
}

/** Resolves the race exactly as the race-survival CLI does, so both tools price the same course. */
function resolveRaceRecord(catalog, name, turn) {
    if (turn !== null && Number.isFinite(turn)) {
        const race = catalog.raceByKey(name, turn)
        if (!race) throw new Error(`no race named "${name}" at turn ${turn}`)
        return race
    }
    const matches = catalog.racesByName(name)
    if (!matches.length) throw new Error(`no race named "${name}" in the compiled catalogue`)
    const distinct = new Set(matches.map((r) => `${r.raceTrack}|${r.distanceMeters}|${r.terrain}`))
    if (distinct.size > 1) {
        throw new Error(`"${name}" occurs at turns ${matches.map((r) => r.turnNumber).join(", ")} on different courses; pass --turn to pick one`)
    }
    return matches[0]
}

/**
 * Builds both survival constraints the planner may need.
 *
 * The second one matters. A minimum that assumes a gold recovery is not a minimum for a build that
 * cannot reach that recovery, so the no-recovery constraint is always solved alongside it and handed
 * over as the fallback rather than being requested separately by the operator.
 */
function survivalConstraints(evidence, race, opts) {
    const shared = {
        targetRace: race.name,
        raceTrack: race.raceTrack,
        distanceMeters: race.distanceMeters,
        surface: surfaceForTerrain(race.terrain),
        strategy: opts.strategy,
        stamina: SURVIVAL_PROBE_STAMINA,
        debuffBudget: opts.debuffBudget,
        marginFraction: opts.margin,
    }
    const withRecovery = buildSurvivalConstraint(computeSurvivalEnvelope(evidence, createRaceSurvivalInput({ ...shared, recoverySkillIds: opts.recovery })))
    const withoutRecovery = buildSurvivalConstraint(computeSurvivalEnvelope(evidence, createRaceSurvivalInput({ ...shared, recoverySkillIds: [] })))
    return { withRecovery, withoutRecovery }
}

/** Skill id -> skill point cost, from the compiled catalogue. Used to price a recovery buy. */
function skillPointCosts(masterDataDir) {
    const reader = loadMasterDataFromDir(masterDataDir)
    const costs = new Map()
    for (const skill of reader.skills) {
        if (typeof skill.cost === "number") costs.set(skill.id, skill.cost)
    }
    return costs
}

function loadParentPairs(opts, targetBuild, relations, topCount) {
    const rosterRecords = parseRosterScanRecords(readFileSync(opts.roster, "utf8"))
    const snapshots = buildRosterSnapshots(rosterRecords)
    const snapshot = latestTrustedSnapshot(snapshots)
    if (!snapshot) throw new Error(`no trusted roster snapshot in ${opts.roster}`)
    const inspiration = buildInspirationIndex(parseInspirationRecords(readFileSync(opts.inspiration, "utf8")))
    const evidenceSet = buildRetentionEvidence(snapshot, inspiration)
    const scarcity = buildFactorScarcityIndex(evidenceSet)

    const candidates = buildParentCandidates(evidenceSet.veterans, targetBuild, scarcity, relations)
    const median = affinityMedianOf(candidates)
    const pairs = enumerateParentPairs(candidates, targetBuild, relations, scarcity, median)
    const ranking = rankParentPairs(pairs, topCount)

    // The frontier first, then the balanced top list, deduplicated. Both are ParentLab's own answer to
    // "which pairs are worth looking at"; this tool does not re-rank them, it budgets them.
    const keys = []
    for (const entry of ranking.frontier) if (!keys.includes(entry.pairKey)) keys.push(entry.pairKey)
    for (const entry of ranking.topPairs) if (!keys.includes(entry.pairKey)) keys.push(entry.pairKey)

    const byFingerprint = new Map()
    for (const veteran of evidenceSet.veterans) {
        byFingerprint.set(veteran.rosterFingerprint ?? `scanIndex:${veteran.entry.scanIndex}`, veteran)
    }

    const out = []
    for (const key of keys.slice(0, topCount)) {
        const pair = ranking.pairsByKey.get(key)
        if (!pair) continue
        const a = byFingerprint.get(pair.parentA.rosterFingerprint ?? `scanIndex:${pair.parentA.scanIndex}`)
        const b = byFingerprint.get(pair.parentB.rosterFingerprint ?? `scanIndex:${pair.parentB.scanIndex}`)
        if (!a || !b) continue
        out.push(toBudgetParentPair(pair, a, b))
    }
    if (!out.length) throw new Error("ParentLab produced no rankable pair for this target")
    return { pairs: out, candidateCount: candidates.length, pairsEvaluated: ranking.pairsEvaluated }
}

/**
 * Builds the deck shortlist, in two passes.
 *
 * DeckLab ranks decks by how good they are, which is the right question and the wrong shortlist for
 * this planner: its top decks all carry a Stamina card, so the two no-Stamina archetypes would have
 * no deck to be evaluated on and the comparison Part 7 asks for would silently not happen. So the
 * search runs a second time over the same owned inventory with the Stamina-typed cards removed, and
 * both sets go into the pool. Nothing is fabricated: the second pass is the same search over a
 * subset of the same account.
 */
/**
 * Resolves the live borrow pool once, so the deck search and the build-aware ranking read the same
 * object. Two resolutions of the same scan would be two chances to disagree about which cards exist.
 */
function resolveLivePool(opts, cardIndex) {
    if (opts.borrowScan) return resolveBorrowPool(parseBorrowScanJsonl(readFileSync(opts.borrowScan, "utf8")), cardIndex)
    if (opts.borrowPool) return resolveBorrowPool(parseBorrowPoolSnapshot(readJson(opts.borrowPool, "borrow pool snapshot")), cardIndex)
    return null
}

function loadDecks(opts, cardIndex, deckTarget, topCount, livePool) {
    const inventory = buildOwnedInventory(readJson(opts.inventory, "owned inventory"), cardIndex, { evidenceSource: opts.inventory, claimsCompleteAccount: false })

    let borrowCandidates = []
    if (livePool) {
        borrowCandidates = borrowCandidateCards(livePool)
    } else if (opts.borrow) {
        borrowCandidates = buildOwnedInventory(readJson(opts.borrow, "borrow inventory"), cardIndex, { evidenceSource: opts.borrow, claimsCompleteAccount: false }).cards
    }

    const decks = []
    const seen = new Set()
    const push = (label, score) => {
        if (!score) return
        const key = score.cards
            .map((c) => `${c.card.supportCardId}${c.borrowed ? "b" : ""}`)
            .sort()
            .join(",")
        if (seen.has(key)) return
        seen.add(key)
        decks.push({ label, score })
    }

    const harvest = (search, prefix, limit) => {
        const before = decks.length
        push(`${prefix}best no-borrow`, search.bestNoBorrow)
        for (const entry of search.archetypes) push(`${prefix}${entry.archetype}`, entry.deck)
        for (const entry of search.frontier) push(`${prefix}frontier ${entry.cards.map((c) => c.card.supportType).join("/")}`, entry)
        for (const option of search.borrowOptions) push(`${prefix}borrow ${option.borrowed.card.displayName}`, option.deck)
        while (decks.length > before + limit) decks.pop()
    }

    const search = searchDecks(cardIndex, inventory, deckTarget, { borrowCandidates })
    harvest(search, "", topCount)

    const withoutStamina = { ...inventory, cards: inventory.cards.filter((c) => c.card.supportType !== "Stamina") }
    if (withoutStamina.cards.length >= 6 && withoutStamina.cards.length < inventory.cards.length) {
        const flexSearch = searchDecks(cardIndex, withoutStamina, deckTarget, { borrowCandidates: borrowCandidates.filter((c) => c.card.supportType !== "Stamina") })
        harvest(flexSearch, "no-stamina ", topCount)
    }

    if (!decks.length) throw new Error("DeckLab produced no legal deck for this target")
    return { decks, inventory, search }
}

function main(argv) {
    let opts
    try {
        opts = parseArgs(argv)
    } catch (err) {
        console.error(err.message)
        return 2
    }
    if (opts.help) {
        console.log(HELP)
        return 0
    }
    for (const [flag, value] of [
        ["--roster", opts.roster],
        ["--inspiration", opts.inspiration],
        ["--inventory", opts.inventory],
        ["--trainee", opts.trainee],
        ["--race", opts.race],
        ["--strategy", opts.strategy],
    ]) {
        if (!value) {
            console.error(`${flag} is required`)
            return 2
        }
    }
    if (!RACE_STRATEGIES.includes(opts.strategy)) {
        console.error(`--strategy ${opts.strategy} is not one of ${RACE_STRATEGIES.join(", ")}`)
        return 2
    }

    let result
    let borrowEffects = []
    let borrowRanking = null
    let intent = null
    let livePool = null
    let deckSearch = null
    let budgetInput = null
    try {
        const budgetEvidence = loadBuildBudgetEvidence(opts.budgetData)
        const survivalEvidence = loadRaceSurvivalEvidence(opts.survivalData)
        const catalog = loadRaceCatalog(opts.masterData)
        const race = resolveRaceRecord(catalog, opts.race, opts.turn)
        const constraints = survivalConstraints(survivalEvidence, race, opts)

        const cardIndex = buildSupportCardIndex(parseSupportCardData(readJson(opts.cards, "support card catalogue")))
        const relations = buildSuccessionRelationIndex(parseSuccessionRelationData(readJson(opts.relations, "succession relation data")))

        const distance = parseTargetDistance(opts.distance)
        const surface = parseTargetSurface(opts.surface)
        const style = parseTargetRunningStyle(opts.style)

        const parentBuild = buildTargetBuild({ targetTrainee: opts.trainee, distance, surface, runningStyle: style, scenario: opts.scenario }, relations)
        const deckTarget = buildDeckTarget(
            { trainee: opts.trainee, scenario: opts.scenario, distance, surface, runningStyle: style, statPriority: parseStatPriority(opts.stat ? opts.stat.split(",") : null) },
            cardIndex,
        )
        if (!deckTarget.scenario) throw new Error(`--scenario ${String(opts.scenario)} did not resolve to a scenario in the support-card catalogue`)

        const trainee = resolveBudgetTrainee(budgetEvidence, { traineeName: opts.trainee, outfit: opts.outfit })
        const { pairs } = loadParentPairs(opts, parentBuild, relations, opts.topParents)
        livePool = resolveLivePool(opts, cardIndex)
        const loaded = loadDecks(opts, cardIndex, deckTarget, opts.topDecks, livePool)
        const decks = loaded.decks
        deckSearch = loaded.search

        budgetInput = {
            evidenceVersion: budgetEvidence.schemaVersion,
            targetLabel: `${opts.trainee} / ${deckTarget.scenarioName} / ${race.name}`,
            scenarioId: deckTarget.scenario.id,
            survivalConstraint: constraints.withRecovery,
            fallbackConstraintWithoutRecovery: constraints.withoutRecovery,
            trainee,
            parentPairs: pairs,
            decks,
            trainingTurns: opts.turns ?? undefined,
            skillPointCosts: skillPointCosts(opts.masterData),
            archetypes: opts.archetypes.length ? opts.archetypes : undefined,
        }
        result = planJointBuild(budgetEvidence, cardIndex, budgetInput)

        if (opts.buildAwareBorrow) {
            if (!livePool) throw new Error("--build-aware-borrow needs a live pool: pass --borrow-scan or --borrow-pool")
            // The baseline must borrow nothing, or the comparison would measure one borrow against
            // another. The preference order below is fixed so the same inputs always pick the same
            // baseline: the recommendation if there is one, then the frontier, then the marginal tier,
            // then the per-archetype bests.
            const baseline = [result.recommended, ...result.frontier, ...result.marginal, ...result.byArchetype].filter((c) => c && !c.deck.score.borrowedCard)[0]
            if (!baseline) throw new Error("no no-borrow build was produced, so a borrow has nothing to be measured against")
            borrowRanking = rankBuildAwareBorrows(budgetEvidence, cardIndex, {
                budgetInput,
                baseline,
                resolution: livePool,
                deckTarget,
                deckLabBorrowOptions: deckSearch?.borrowOptions ?? [],
            })
            if (opts.emitIntent) {
                if (!borrowRanking.recommended) throw new Error("no build-aware borrow was recommended, so no intent can be emitted")
                intent = buildSmartBorrowIntent(livePool, borrowRanking.recommended.supportCardId, deckTarget.label, "BUILD_AWARE")
                writeFileSync(opts.emitIntent, serializeSmartBorrowIntent(intent), "utf8")
            }
        }

        // A borrow is valued against the same pair and archetype without it, which is the only
        // comparison that attributes the difference to the borrow rather than to the pair.
        const borrowed = [...result.frontier, ...result.byArchetype].filter((c) => c.deck.score.borrowedCard)
        for (const candidate of borrowed) {
            const baseline = [...result.frontier, ...result.byArchetype].find(
                (c) => c !== candidate && !c.deck.score.borrowedCard && c.archetype === candidate.archetype && c.parentPair.label === candidate.parentPair.label,
            )
            if (!baseline) continue
            const effect = valueBorrow(candidate, baseline)
            if (effect) borrowEffects.push(effect)
        }
    } catch (err) {
        console.error(err instanceof Error ? err.message : String(err))
        return 2
    }

    const document = { recommendation: result, borrowEffects, borrowRanking, intent }
    if (opts.out) writeFileSync(opts.out, `${JSON.stringify(document, null, 2)}\n`, "utf8")
    if (opts.json) {
        console.log(JSON.stringify(document, null, 2))
    } else if (opts.buildAwareBorrow) {
        // The build-aware run is its own report. Printing the whole joint-build report in front of it
        // would bury the comparison the run exists to produce.
        console.log(formatBuildAwareBorrowRanking(borrowRanking, intent))
    } else {
        console.log(formatJointBuildRecommendation(result))
        if (borrowEffects.length) {
            console.log("")
            console.log("BORROW CONSTRAINT RELIEF (advisory only; nothing here selects a borrow)")
            for (const effect of borrowEffects) console.log(formatBorrowBudgetEffect(effect))
        }
    }

    if (opts.buildAwareBorrow) return borrowRanking && borrowRanking.recommended ? 0 : 1
    return result.recommended ? 0 : 1
}

process.exitCode = main(process.argv.slice(2))
