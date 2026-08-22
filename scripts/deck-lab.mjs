// DeckLab shadow deck advisor - offline, read-only, advisory only.
//
// Answers "for trainee X, scenario Y and target build Z, which of the support cards this account owns
// should run, which borrow is worth taking, and why" from the game's own decoded support-card tables
// plus a maintained owned-card snapshot.
//
// What it does NOT do, by construction: it never touches the device, never opens the game, never
// changes a deck, never borrows anything, never launches a career, and never rewrites its inputs. It
// also never fetches: the community ranking prior, if used at all, is read from a committed snapshot.
//
// Usage:
//   node scripts/deck-lab.mjs --inventory <owned.json> --trainee <name> --scenario <name> [--distance <d>]
//        [--surface turf|dirt] [--style front|pace|late|end] [--stat "Speed,Power"] [--skill 200162,200232]
//        [--label "..."] [--trainee <name> ...]        # repeat to add another target build
//        [--borrow <owned.json> | --borrow-hypothetical] [--no-borrow]
//        [--community-prior <snapshot.json>] [--cards <support_cards.json>]
//        [--pool-limit <n>] [--top-borrow <n>] [--fixture] [--json] [--out <path>] [--summary-out <path>]
//
// Exit codes: 0 report built on a trusted owned inventory | 1 built, but the inventory is not trusted
// for account-wide claims | 2 input or parse failure
//
// Requires node >= 23.6 (native TypeScript type stripping; the logic lives in src/lib/deckLab/ and is
// exercised by the Jest suite).

import { existsSync, readFileSync, writeFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"
import { valueCard, ownedCardInput } from "../src/lib/deckLab/cardValue.ts"
import { buildCommunityPriorIndex, parseCommunityPrior } from "../src/lib/deckLab/communityPrior.ts"
import { hypotheticalBorrowPool, searchDecks } from "../src/lib/deckLab/deckSearch.ts"
import { borrowCandidateCards, parseBorrowPoolSnapshot, resolveBorrowPool } from "../src/lib/deckLab/borrowPool.ts"
import { buildDeckTarget, parseTargetDistance, parseTargetRunningStyle, parseTargetSurface, TARGET_DISTANCES, TARGET_RUNNING_STYLES, TARGET_SURFACES } from "../src/lib/deckLab/deckTarget.ts"
import { assessInventory, buildFixtureInventory, buildOwnedInventory } from "../src/lib/deckLab/inventory.ts"
import { buildDeckLabReport } from "../src/lib/deckLab/report.ts"
import { buildSupportCardIndex, parseSupportCardData } from "../src/lib/deckLab/supportCardData.ts"

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, "..")
const DEFAULT_CARDS = join(REPO, "src", "data", "support_cards.json")

const HELP = `deck-lab - shadow support-deck advisor (read-only, advisory only)

Options:
  --inventory <path>       Owned support-card snapshot. Without it, a labelled fixture inventory is used.
  --complete-account       Assert the snapshot covers the whole account. Required before any
                           "you own nothing better" reading is trustworthy; never inferred.
  --fixture                Ignore --inventory and use the fixture inventory.
  --cards <path>           Support-card catalogue (default src/data/support_cards.json).

  --trainee <name>         Starts a target build for this trainee. Repeatable.
  --scenario <name>        ${"URA Finale | Unity Cup | Grand Concert | Trackblazer, or a scenario id"}
  --distance <d>           ${TARGET_DISTANCES.join(" | ")}
  --surface <s>            ${TARGET_SURFACES.join(" | ")}
  --style <s>              ${TARGET_RUNNING_STYLES.join(" | ")}
  --stat <list>            Comma-separated training stats, highest priority first. Replaces the
                           editorial default the distance implies.
  --skill <list>           Comma-separated skill ids the build wants, matched against hint pools.
  --label <text>           Display label for the current target.

  --borrow <path>          A second owned-format snapshot of cards available to borrow.
  --borrow-pool <path>     A read-only borrow-pool scan of the account's actual friend/guest list.
                           Resolves each observed row against the catalogue and answers "what can you
                           borrow right now". Only trusted, resolved rows are used.
  --borrow-hypothetical    Consider every SSR in the catalogue at MLB. Answers "what would be worth
                           borrowing", not "what can you borrow".
  --no-borrow              Skip borrow analysis entirely.
                           (--borrow, --borrow-pool and --borrow-hypothetical are mutually exclusive.)

  --community-prior <path> A committed community ranking snapshot. Reported beside the decoded values,
                           never mixed into them.

  --pool-limit <n>         Cards in the working pool before enumeration. Higher is slower and finds
                           better decks; the report always states what the search covered.
  --top-borrow <n>         Borrow options listed per target (default 5).

  --json                   Print the report document as JSON instead of the readable report.
  --out <path>             Write the full JSON document here.
  --summary-out <path>     Write a short JSON summary here.
  --domain-out <path>      Write a summary of the support-card catalogue itself here: counts by
                           rarity and role, effect-type coverage, and what is decoded and what is not.
  --help                   This text.
`

function parseArgs(argv) {
    const opts = {
        inventory: null,
        completeAccount: false,
        fixture: false,
        cards: DEFAULT_CARDS,
        targets: [],
        borrow: null,
        borrowPool: null,
        borrowHypothetical: false,
        noBorrow: false,
        prior: null,
        poolLimit: null,
        topBorrow: 5,
        json: false,
        out: null,
        summaryOut: null,
        domainOut: null,
        help: false,
    }
    let current = null
    const requireCurrent = (flag) => {
        if (!current) throw new Error(`${flag} applies to a target build; name one with --trainee first`)
        return current
    }

    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i]
        const next = () => {
            const value = argv[++i]
            if (value === undefined) throw new Error(`${arg} needs a value`)
            return value
        }
        switch (arg) {
            case "--inventory":
                opts.inventory = next()
                break
            case "--complete-account":
                opts.completeAccount = true
                break
            case "--fixture":
                opts.fixture = true
                break
            case "--cards":
                opts.cards = next()
                break
            case "--trainee":
                current = { trainee: next() }
                opts.targets.push(current)
                break
            case "--scenario":
                if (!current) {
                    current = { trainee: null }
                    opts.targets.push(current)
                }
                current.scenario = next()
                break
            case "--distance": {
                const value = next()
                const parsed = parseTargetDistance(value)
                if (!parsed) throw new Error(`--distance ${value} is not one of ${TARGET_DISTANCES.join(", ")}`)
                requireCurrent("--distance").distance = parsed
                break
            }
            case "--surface": {
                const value = next()
                const parsed = parseTargetSurface(value)
                if (!parsed) throw new Error(`--surface ${value} is not one of ${TARGET_SURFACES.join(", ")}`)
                requireCurrent("--surface").surface = parsed
                break
            }
            case "--style": {
                const value = next()
                const parsed = parseTargetRunningStyle(value)
                if (!parsed) throw new Error(`--style ${value} is not one of ${TARGET_RUNNING_STYLES.join(", ")}`)
                requireCurrent("--style").runningStyle = parsed
                break
            }
            case "--stat":
                requireCurrent("--stat").statPriority = next()
                    .split(",")
                    .map((s) => s.trim())
                    .filter(Boolean)
                break
            case "--skill":
                requireCurrent("--skill").prioritySkillIds = next()
                    .split(",")
                    .map((s) => Number(s.trim()))
                    .filter((n) => Number.isFinite(n))
                break
            case "--label":
                requireCurrent("--label").label = next()
                break
            case "--borrow":
                opts.borrow = next()
                break
            case "--borrow-pool":
                opts.borrowPool = next()
                break
            case "--borrow-hypothetical":
                opts.borrowHypothetical = true
                break
            case "--no-borrow":
                opts.noBorrow = true
                break
            case "--community-prior":
                opts.prior = next()
                break
            case "--pool-limit":
                opts.poolLimit = Number(next())
                break
            case "--top-borrow":
                opts.topBorrow = Number(next())
                break
            case "--json":
                opts.json = true
                break
            case "--out":
                opts.out = next()
                break
            case "--summary-out":
                opts.summaryOut = next()
                break
            case "--domain-out":
                opts.domainOut = next()
                break
            case "--help":
            case "-h":
                opts.help = true
                break
            default:
                throw new Error(`unknown option ${arg}`)
        }
    }
    const borrowModes = [opts.borrow && "--borrow", opts.borrowPool && "--borrow-pool", opts.borrowHypothetical && "--borrow-hypothetical"].filter(Boolean)
    if (borrowModes.length > 1) throw new Error(`only one borrow source may be given at a time; saw ${borrowModes.join(" and ")}`)
    if (!opts.targets.length) opts.targets.push({ trainee: null })
    return opts
}

function readJson(path, what) {
    if (!existsSync(path)) throw new Error(`no such ${what}: ${path}`)
    try {
        return JSON.parse(readFileSync(path, "utf8"))
    } catch (err) {
        throw new Error(`${what} at ${path} is not valid JSON: ${err instanceof Error ? err.message : String(err)}`)
    }
}

const bar = (title) => `\n${title}\n${"=".repeat(title.length)}`

function printCard(card, indent = "  ") {
    const tag = card.borrowed ? " (BORROWED)" : ""
    console.log(`${indent}${card.displayName}${tag}`)
    console.log(`${indent}  ${card.role} ${card.rarity}, ${card.limitBreakImpact}`)
    for (const reason of card.whyIncluded) console.log(`${indent}  - ${reason}`)
    console.log(`${indent}  target fit: ${card.targetFit}`)
    console.log(`${indent}  scenario:   ${card.scenarioFit}`)
    console.log(`${indent}  skills:     ${card.skillContribution}`)
    if (card.whatItReplaces) console.log(`${indent}  replaces:   ${card.whatItReplaces}`)
    for (const unknown of card.unknownMechanics) console.log(`${indent}  UNCOUNTED:  ${unknown}`)
    console.log(`${indent}  confidence: ${card.confidence}`)
}

function printReport(report) {
    console.log(bar("DeckLab shadow deck advisor"))
    console.log(`Catalogue: ${report.catalogueCards} cards from ${report.catalogueSource}`)
    console.log(`Inventory: ${report.inventory.name}${report.inventory.snapshotDate ? ` (${report.inventory.snapshotDate})` : ""}`)
    const completeness = report.inventory.completeness
    console.log(`           ${completeness.resolved} of ${completeness.rowCount} rows resolved, ${completeness.unresolved} unresolved, ${completeness.withWarnings} with warnings`)
    console.log(`           trusted for account-wide claims: ${completeness.trustedForAccountClaims ? "yes" : "NO"}`)
    for (const gap of completeness.gaps) console.log(`           gap: ${gap}`)
    for (const row of report.inventory.unresolved) console.log(`           UNRESOLVED: ${row.rawCharacter} | ${row.rawTitle} | ${row.reason}: ${row.detail}`)
    console.log(`Community prior: ${report.communityPrior.present ? `${report.communityPrior.sourceName} (${report.communityPrior.provenance}, ${report.communityPrior.resolved} cards matched)` : "none supplied"}`)
    const bs = report.borrowSource
    console.log(`Borrow source:  ${bs.mode} - ${bs.description}`)
    if (bs.mode === "REAL") {
        console.log(`           ${bs.distinctCards} distinct cards, ${bs.resolvedRows} rows resolved, ${bs.unresolvedRows} unresolved; complete pool: ${bs.trustedAsCompletePool ? "yes" : "NO"}`)
        const provenance = Object.entries(bs.sourceTypeCounts)
            .sort((a, b) => a[0].localeCompare(b[0]))
            .map(([type, count]) => `${count} ${type}`)
            .join(", ")
        if (provenance) console.log(`           provenance: ${provenance}`)
        for (const note of bs.notes) console.log(`           note: ${note}`)
    }

    for (const target of report.targets) {
        console.log(bar(`Target: ${target.label}`))
        console.log(`Scenario ${target.scenario}; distance ${target.distance ?? "unset"}; surface ${target.surface ?? "unset"}; style ${target.runningStyle ?? "unset"}`)
        console.log(`Stat priority ${target.statPriority.join(" > ") || "none"} (${target.statPriorityOrigin})`)
        if (target.targetGaps.length) console.log(`Target gaps: ${target.targetGaps.join(", ")}`)

        const search = target.searchCompleteness
        console.log(
            `Search: ${search.ownedCards} owned, ${search.legalForScenario} legal here, ${search.afterDominancePrune} undominated, ` +
                `${search.workingPool} in the working pool, ${search.combinationsEvaluated} decks evaluated, ${search.legalDecksFound} legal`,
        )
        console.log(`        exhaustive over the owned pool: ${search.exhaustiveOverOwnedPool ? "yes" : "NO, the result is the best found over the working pool"}`)
        for (const note of search.notes) console.log(`        note: ${note}`)
        for (const line of target.excludedCardSummary) console.log(`        excluded: ${line}`)

        if (!target.recommended) {
            console.log("\nNo legal deck could be built.")
            continue
        }

        console.log(`\nRecommended (${target.recommended.archetype}${target.recommendedIsDominant ? ", dominant on every dimension compared" : ", no deck dominated so this is the balanced pick"}):`)
        const deck = target.recommended.deck
        console.log(`  composite ${deck.composite} (${deck.compositeOrigin})`)
        console.log(`  confidence ${deck.confidence}`)
        for (const strength of deck.strengths) console.log(`  + ${strength}`)
        for (const weakness of deck.weaknesses) console.log(`  - ${weakness}`)
        for (const tradeoff of deck.tradeoffs) console.log(`  ~ ${tradeoff}`)
        for (const unknown of deck.unknownMechanics) console.log(`  UNCOUNTED: ${unknown}`)
        console.log("")
        for (const card of deck.cards) printCard(card)

        if (target.archetypes.length > 1) {
            console.log("\n  Other archetypes:")
            for (const entry of target.archetypes) {
                if (entry.archetype === target.recommended.archetype) continue
                const names = entry.deck.cards.map((c) => c.displayName).join(", ")
                console.log(`    ${entry.archetype}${entry.metric ? ` (max ${entry.metric} = ${entry.deck.dimensions[entry.metric]})` : ""}: ${names}`)
            }
        }

        if (target.borrow.length) {
            console.log("\n  Borrow options, best first:")
            for (const option of target.borrow) {
                console.log(`    ${option.borrowed} replacing ${option.displaced ?? "nothing"}: target stat coverage ${option.improvement >= 0 ? "+" : ""}${option.improvement}`)
                const improved = Object.entries(option.improvedDimensions)
                    .map(([k, v]) => `${k} +${v}`)
                    .join(", ")
                if (improved) console.log(`      improves ${improved}`)
            }
        }
    }

    console.log(bar("Caveats"))
    for (const caveat of report.caveats) console.log(`- ${caveat}`)
}

/**
 * A summary of the catalogue itself, independent of any account or target.
 *
 * Reports what the shipped data covers and, just as importantly, what it does not: how many cards
 * carry a unique perk whose type code is undecoded, how many have no hint pool, and which effect
 * types no shipped card actually uses.
 */
function domainSummaryOf(index) {
    const data = index.data
    const byRarity = {}
    const byRole = {}
    const effectUse = {}
    let withUnique = 0
    let withUndecodedUnique = 0
    let withoutHints = 0
    let untitled = 0
    let restricted = 0

    for (const card of data.cards) {
        byRarity[card.rarity] = (byRarity[card.rarity] ?? 0) + 1
        byRole[card.supportType] = (byRole[card.supportType] ?? 0) + 1
        for (const effect of card.effects) effectUse[String(effect.type)] = (effectUse[String(effect.type)] ?? 0) + 1
        if (card.uniqueEffect) withUnique += 1
        if (card.uniqueEffect?.undecodedTypes.length) withUndecodedUnique += 1
        if (!card.hintSkillIds.length) withoutHints += 1
        if (!card.title) untitled += 1
        if (card.restrictedScenarioIds.length) restricted += 1
    }

    const unusedEffectTypes = Object.keys(data.effectTypes)
        .filter((type) => !effectUse[type])
        .map((type) => ({ type: Number(type), name: data.effectTypes[type] }))
        .sort((a, b) => a.type - b.type)

    return {
        source: data.source,
        schemaVersion: data.schemaVersion,
        cards: data.cards.length,
        byRarity,
        byRole,
        untitledCards: untitled,
        levelCapsByRarity: data.levelCapsByRarity,
        effectTypes: data.effectTypes,
        effectTypeUsage: Object.fromEntries(
            Object.entries(effectUse)
                .map(([type, count]) => [type, count])
                .sort((a, b) => Number(a[0]) - Number(b[0])),
        ),
        unusedEffectTypes,
        uniqueEffects: { withUnique, withUndecodedUnique },
        cardsWithoutHintPool: withoutHints,
        scenarioRestrictedCards: restricted,
        scenarios: data.scenarios.map((scenario) => ({
            id: scenario.id,
            name: scenario.name,
            statCapBonus: scenario.statCapBonus,
            specialCharacters: scenario.specialCharaIds.map((id) => index.characterName(id)),
            restrictedCards: scenario.restrictedCardIds.map((id) => index.byId.get(id)?.title ?? String(id)),
        })),
        notDecoded: [
            "unique effect type codes at or above the undecoded floor: a conditional encoding this repository has not read, carried as ids plus the game's own description",
            "the magnitude of a scenario naming a character as its own: the membership is decoded, the bonus is not",
            "how the game weighs any of these effects against each other: nothing in the shipped data states it, so DeckLab's weighting is editorial",
        ],
    }
}

function summaryOf(report) {
    return {
        schema: report.schema,
        schemaVersion: report.schemaVersion,
        catalogueCards: report.catalogueCards,
        inventory: {
            name: report.inventory.name,
            isFixture: report.inventory.isFixture,
            resolved: report.inventory.completeness.resolved,
            unresolved: report.inventory.completeness.unresolved,
            trustedForAccountClaims: report.inventory.completeness.trustedForAccountClaims,
        },
        communityPriorPresent: report.communityPrior.present,
        borrowSource: {
            mode: report.borrowSource.mode,
            distinctCards: report.borrowSource.distinctCards,
            trustedAsCompletePool: report.borrowSource.trustedAsCompletePool,
        },
        targets: report.targets.map((target) => ({
            label: target.label,
            scenario: target.scenario,
            distance: target.distance,
            statPriority: target.statPriority,
            statPriorityOrigin: target.statPriorityOrigin,
            exhaustiveOverOwnedPool: target.searchCompleteness.exhaustiveOverOwnedPool,
            legalDecksFound: target.searchCompleteness.legalDecksFound,
            recommendedArchetype: target.recommended?.archetype ?? null,
            recommendedIsDominant: target.recommendedIsDominant,
            recommendedDeck: target.recommended?.deck.cards.map((c) => ({ supportCardId: c.supportCardId, displayName: c.displayName, role: c.role, limitBreak: c.limitBreak, level: c.level })) ?? [],
            recommendedComposite: target.recommended?.deck.composite ?? null,
            confidence: target.recommended?.deck.confidence ?? null,
            topBorrow: target.borrow[0] ? { borrowed: target.borrow[0].borrowed, displaced: target.borrow[0].displaced, improvement: target.borrow[0].improvement } : null,
        })),
    }
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

    let report
    let completeness
    let domainSummary
    try {
        const index = buildSupportCardIndex(parseSupportCardData(readJson(opts.cards, "support card catalogue")))
        domainSummary = domainSummaryOf(index)

        const useFixture = opts.fixture || !opts.inventory
        const inventory = useFixture
            ? buildFixtureInventory(index)
            : buildOwnedInventory(readJson(opts.inventory, "owned inventory"), index, { evidenceSource: opts.inventory, claimsCompleteAccount: opts.completeAccount })
        completeness = assessInventory(inventory, index)

        let borrowCandidates = []
        let borrowSource
        if (!opts.noBorrow) {
            if (opts.borrowPool) {
                const resolution = resolveBorrowPool(parseBorrowPoolSnapshot(readJson(opts.borrowPool, "borrow pool snapshot")), index)
                borrowCandidates = borrowCandidateCards(resolution)
                const sourceTypeCounts = {}
                for (const candidate of resolution.candidates) for (const source of candidate.sources) sourceTypeCounts[source.sourceType] = (sourceTypeCounts[source.sourceType] ?? 0) + 1
                borrowSource = {
                    mode: "REAL",
                    description: `read-only borrow scan ${resolution.snapshot.scanId} of ${resolution.snapshot.sourceScreen}`,
                    scanId: resolution.snapshot.scanId,
                    observedAt: resolution.snapshot.observedAt,
                    refreshGeneration: resolution.snapshot.refreshGeneration,
                    termination: resolution.snapshot.termination,
                    distinctCards: resolution.distinctCards,
                    resolvedRows: resolution.resolvedRows,
                    unresolvedRows: resolution.unresolved.length,
                    trustedAsCompletePool: resolution.trustedAsCompletePool,
                    sourceTypeCounts,
                    notes: resolution.notes,
                }
            } else if (opts.borrow) {
                const snapshot = buildOwnedInventory(readJson(opts.borrow, "borrow inventory"), index, { evidenceSource: opts.borrow, claimsCompleteAccount: false })
                borrowCandidates = snapshot.cards
                borrowSource = {
                    mode: "REAL",
                    description: `owned-format borrow snapshot from ${opts.borrow}`,
                    scanId: null,
                    observedAt: snapshot.snapshotDate,
                    refreshGeneration: null,
                    termination: null,
                    distinctCards: new Set(snapshot.cards.map((c) => c.card.supportCardId)).size,
                    resolvedRows: snapshot.cards.length,
                    unresolvedRows: snapshot.unresolved.length,
                    trustedAsCompletePool: false,
                    sourceTypeCounts: {},
                    notes: snapshot.unresolved.length ? [`${snapshot.unresolved.length} borrow rows did not resolve onto a catalogue card`] : [],
                }
            } else if (opts.borrowHypothetical) {
                borrowCandidates = hypotheticalBorrowPool(index)
                borrowSource = {
                    mode: "HYPOTHETICAL",
                    description: "every SSR in the catalogue at full limit break",
                    scanId: null,
                    observedAt: null,
                    refreshGeneration: null,
                    termination: null,
                    distinctCards: borrowCandidates.length,
                    resolvedRows: borrowCandidates.length,
                    unresolvedRows: 0,
                    trustedAsCompletePool: false,
                    sourceTypeCounts: {},
                    notes: [],
                }
            }
        }

        const prior = opts.prior ? buildCommunityPriorIndex(parseCommunityPrior(readJson(opts.prior, "community prior"), index)) : null

        const results = opts.targets.map((request) => {
            const build = buildDeckTarget(request, index)
            return searchDecks(index, inventory, build, {
                poolLimit: opts.poolLimit ?? undefined,
                borrowCandidates,
                noBorrow: opts.noBorrow,
            })
        })

        report = buildDeckLabReport({ index, inventory, completeness, isFixture: useFixture, results, prior, topBorrow: opts.topBorrow, borrowSource })
    } catch (err) {
        console.error(err instanceof Error ? err.message : String(err))
        return 2
    }

    if (opts.json) console.log(JSON.stringify(report, null, 2))
    else printReport(report)

    if (opts.out) {
        writeFileSync(opts.out, `${JSON.stringify(report, null, 2)}\n`, "utf8")
        if (!opts.json) console.log(`\nWrote ${opts.out}`)
    }
    if (opts.summaryOut) {
        writeFileSync(opts.summaryOut, `${JSON.stringify(summaryOf(report), null, 2)}\n`, "utf8")
        if (!opts.json) console.log(`Wrote ${opts.summaryOut}`)
    }
    if (opts.domainOut) {
        writeFileSync(opts.domainOut, `${JSON.stringify(domainSummary, null, 2)}\n`, "utf8")
        if (!opts.json) console.log(`Wrote ${opts.domainOut}`)
    }

    if (!completeness.trustedForAccountClaims) {
        console.error("")
        console.error("The owned inventory is NOT trusted for account-wide claims, so nothing above should be read as 'you own nothing better'.")
        for (const gap of completeness.gaps) console.error(`  ${gap}`)
    }
    return completeness.trustedForAccountClaims ? 0 : 1
}

// process.exitCode (not process.exit) so stdio flushes cleanly - process.exit after console writes trips
// a libuv handle assert on Windows node.
try {
    process.exitCode = main(process.argv.slice(2))
} catch (e) {
    console.error(`deck-lab failed: ${e instanceof Error ? (e.stack ?? e.message) : String(e)}`)
    process.exitCode = 2
}

export { parseArgs, summaryOf, domainSummaryOf }
