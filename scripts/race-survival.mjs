// STAM-1 Race Survival Shadow Model - offline, read-only CLI.
//
// Answers one question: for this race, this strategy, this Stamina, this recovery package and this
// debuff-risk budget, does the build survive, and what Stamina would. It reads the compiled race
// catalogue and the decoded race-survival evidence, both committed. It needs no device, no emulator
// and no network, and it changes nothing: no file is written, and no bot behaviour depends on it.
//
// Usage:
//   node scripts/race-survival.mjs --race <name> [--turn <N>] --strategy <style> --stamina <N> [options]
//   node scripts/race-survival.mjs --track <name> --distance <m> --surface <turf|dirt> --strategy <style> --stamina <N> [options]
//   node scripts/race-survival.mjs --find-skill <text>
//
// Race identity:
//   --race <name>            canonical race name from the compiled catalogue
//   --turn <N>               disambiguates a name that recurs across turns
//   --track/--distance/--surface   price a course directly, without the catalogue
//
// Build:
//   --strategy front|pace|late|end|runaway
//   --stamina <N>
//   --guts <N>               carried and reported, not priced
//   --target-speed <N>       carried and reported, not priced
//   --recovery <ids>         comma-separated skill ids the build owns
//
// Risk:
//   --debuff-budget 0|1|2|custom   0 = BASE, 1 and 2 insure against the worst decoded threat
//   --ground firm|good|soft|heavy  carried and reported, not priced
//   --rush-risk <text>             carried and reported, not priced
//   --margin <fraction>            editorial safety margin as a fraction of MaxHP (default 0)
//
// Output:
//   --compare-stamina 700,800,900  sweep the same race across several Stamina values
//   --json                         machine-readable output
//   --evidence <path>              race_survival_data.json (default src/data/race_survival_data.json)
//   --master-data <dir>            compiled dir (default src/data/compiled)
//
// Exit: 0 ok | 1 the build does not survive the selected risk policy | 2 usage or load error.
//
// Requires node >= 23.6 (native TypeScript type stripping; logic lives in src/lib/raceSurvival/).

import { dirname, join, resolve } from "node:path"
import { fileURLToPath } from "node:url"
import { loadRaceCatalog } from "../src/lib/raceLab/catalog.ts"
import { loadRaceSurvivalEvidence } from "../src/lib/raceSurvival/evidence.ts"
import { computeSurvivalEnvelope, createRaceSurvivalInput } from "../src/lib/raceSurvival/envelope.ts"
import { computeEffectiveHp, computeMaxHp } from "../src/lib/raceSurvival/mechanics.ts"
import { formatStaminaComparison, formatSurvivalEnvelope } from "../src/lib/raceSurvival/report.ts"
import { RACE_STRATEGIES, RaceSurvivalError } from "../src/lib/raceSurvival/types.ts"

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..")

const HELP = `race-survival - STAM-1 Race Survival Shadow Model (offline, read-only, shadow)

Race identity (pick one):
  --race <name> [--turn <N>]
  --track <name> --distance <metres> --surface turf|dirt

Build:
  --strategy front|pace|late|end|runaway   (required)
  --stamina <N>                            (required)
  --guts <N>                               carried, not priced
  --target-speed <N>                       carried, not priced
  --recovery <id,id,...>                   skill ids the build owns

Risk:
  --debuff-budget 0|1|2                    0 = BASE (default), 1 and 2 insure against the worst decoded threat
  --ground firm|good|soft|heavy            carried, not priced
  --rush-risk <text>                       carried, not priced
  --margin <fraction>                      editorial safety margin, fraction of MaxHP (default 0)

Other:
  --compare-stamina 700,800,900            Stamina sweep
  --find-skill <text>                      list decoded HP skills whose name or text matches
  --json                                   machine-readable output
  --evidence <path>                        default src/data/race_survival_data.json
  --master-data <dir>                      default src/data/compiled
  --help
`

const BUDGET_BY_FLAG = { 0: "BASE", 1: "ONE_STAMINA_DEBUFF", 2: "TWO_STAMINA_DEBUFFS" }

function fail(message) {
    console.error(message)
    return 2
}

function parseArgs(argv) {
    const opts = {
        race: null,
        turn: null,
        track: null,
        distance: null,
        surface: null,
        strategy: null,
        stamina: null,
        guts: null,
        targetSpeed: null,
        recovery: [],
        debuffBudget: "BASE",
        ground: null,
        rushRisk: null,
        margin: 0,
        compareStamina: null,
        findSkill: null,
        json: false,
        evidence: join(ROOT, "src", "data", "race_survival_data.json"),
        masterData: join(ROOT, "src", "data", "compiled"),
        help: false,
    }
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i]
        const next = () => argv[++i]
        switch (arg) {
            case "--race":
                opts.race = next()
                break
            case "--turn":
                opts.turn = Number(next())
                break
            case "--track":
                opts.track = next()
                break
            case "--distance":
                opts.distance = Number(next())
                break
            case "--surface":
                opts.surface = next()
                break
            case "--strategy":
                opts.strategy = next()
                break
            case "--stamina":
                opts.stamina = Number(next())
                break
            case "--guts":
                opts.guts = Number(next())
                break
            case "--target-speed":
                opts.targetSpeed = Number(next())
                break
            case "--recovery":
                opts.recovery = (next() ?? "")
                    .split(",")
                    .map((s) => Number(s.trim()))
                    .filter((n) => Number.isFinite(n))
                break
            case "--debuff-budget": {
                const raw = (next() ?? "").trim()
                const mapped = BUDGET_BY_FLAG[raw]
                if (!mapped) throw new Error(`--debuff-budget must be 0, 1 or 2, got ${raw}`)
                opts.debuffBudget = mapped
                break
            }
            case "--ground":
                opts.ground = next()
                break
            case "--rush-risk":
                opts.rushRisk = next()
                break
            case "--margin":
                opts.margin = Number(next())
                break
            case "--compare-stamina":
                opts.compareStamina = (next() ?? "")
                    .split(",")
                    .map((s) => Number(s.trim()))
                    .filter((n) => Number.isFinite(n))
                break
            case "--find-skill":
                opts.findSkill = next()
                break
            case "--json":
                opts.json = true
                break
            case "--evidence":
                opts.evidence = resolve(next())
                break
            case "--master-data":
                opts.masterData = resolve(next())
                break
            case "--help":
            case "-h":
                opts.help = true
                break
            default:
                throw new Error(`unknown option ${arg}`)
        }
    }
    return opts
}

/** Resolves the race the caller named onto the compiled catalogue, or fails with what it found. */
function resolveRaceRecord(opts) {
    const catalog = loadRaceCatalog(opts.masterData)
    if (opts.turn !== null) {
        const race = catalog.raceByKey(opts.race, opts.turn)
        if (!race) throw new Error(`no race named "${opts.race}" at turn ${opts.turn}`)
        return race
    }
    const matches = catalog.racesByName(opts.race)
    if (!matches.length) throw new Error(`no race named "${opts.race}" in the compiled catalogue`)
    // Same-name races recur across turns with identical course facts; only survival-relevant fields
    // matter here, so a unanimous set is safe to collapse and a divided one has to be disambiguated.
    const distinct = new Set(matches.map((r) => `${r.raceTrack}|${r.distanceMeters}|${r.terrain}`))
    if (distinct.size > 1) {
        throw new Error(`"${opts.race}" occurs at turns ${matches.map((r) => r.turnNumber).join(", ")} on different courses; pass --turn to pick one`)
    }
    return matches[0]
}

function findSkills(evidence, text) {
    const needle = text.toLowerCase()
    const lines = []
    for (const skill of evidence.hpSkills) {
        const haystack = `${skill.name ?? ""} ${skill.desc ?? ""}`.toLowerCase()
        if (!haystack.includes(needle)) continue
        const effects = skill.effects.map((e) => `${(e.hpValueRaw / 10000) * 100}% -> target_type ${e.targetType}`).join("; ")
        lines.push(`${String(skill.id).padEnd(8)} ${(skill.name ?? "(unnamed)").padEnd(34)} ${effects}\n         ${skill.desc ?? ""}`)
    }
    return lines
}

function main(argv) {
    let opts
    try {
        opts = parseArgs(argv)
    } catch (err) {
        return fail(err.message)
    }
    if (opts.help || argv.length === 0) {
        console.log(HELP)
        return 0
    }

    let evidence
    try {
        evidence = loadRaceSurvivalEvidence(opts.evidence)
    } catch (err) {
        return fail(err instanceof Error ? err.message : String(err))
    }

    if (opts.findSkill !== null) {
        const lines = findSkills(evidence, opts.findSkill)
        if (!lines.length) {
            console.log(`no decoded HP skill matches "${opts.findSkill}"`)
            return 0
        }
        console.log(lines.join("\n"))
        return 0
    }

    if (!opts.strategy || !RACE_STRATEGIES.includes(opts.strategy)) {
        return fail(`--strategy must be one of ${RACE_STRATEGIES.join(", ")}`)
    }
    if (opts.stamina === null || !Number.isFinite(opts.stamina)) {
        return fail("--stamina <N> is required")
    }

    let raceFacts
    try {
        if (opts.race) {
            const record = resolveRaceRecord(opts)
            raceFacts = {
                targetRace: record.name,
                raceTrack: record.raceTrack,
                distanceMeters: record.distanceMeters,
                surface: record.terrain.toLowerCase() === "dirt" ? "dirt" : "turf",
            }
        } else {
            if (!opts.track || !Number.isFinite(opts.distance) || !opts.surface) {
                return fail("name a race with --race, or a course with --track, --distance and --surface")
            }
            raceFacts = { targetRace: null, raceTrack: opts.track, distanceMeters: opts.distance, surface: opts.surface }
        }
    } catch (err) {
        return fail(err instanceof Error ? err.message : String(err))
    }

    const input = createRaceSurvivalInput({
        ...raceFacts,
        evidenceVersion: evidence.schemaVersion,
        groundCondition: opts.ground,
        strategy: opts.strategy,
        stamina: opts.stamina,
        guts: opts.guts,
        targetSpeed: opts.targetSpeed,
        recoverySkillIds: opts.recovery,
        debuffBudget: opts.debuffBudget,
        rushRiskPolicy: opts.rushRisk,
        marginFraction: opts.margin,
    })

    let envelope
    try {
        envelope = computeSurvivalEnvelope(evidence, input)
    } catch (err) {
        if (err instanceof RaceSurvivalError) return fail(`${err.code}: ${err.message}`)
        return fail(err instanceof Error ? err.message : String(err))
    }

    const comparison = (opts.compareStamina ?? []).map((value) => {
        const maxHp = computeMaxHp({ stamina: value, distanceMeters: input.distanceMeters, strategy: input.strategy })
        const recoveryFraction = envelope.recoveryContribution.supported.reduce((sum, r) => sum + (r.hpFraction ?? 0), 0)
        const selected = envelope.debuffScenarios.find((s) => s.label === envelope.selectedRiskPolicy)
        const debuffFraction = selected ? selected.hpLossFraction : 0
        const effectiveHp = computeEffectiveHp({ stamina: value, distanceMeters: input.distanceMeters, strategy: input.strategy, recoveryFraction, debuffFraction, flatDebuffHp: 0 })
        return {
            stamina: value,
            maxHp,
            effectiveHp,
            marginHp: effectiveHp - envelope.baselineRequiredHpTarget,
            survivesSelectedRisk: effectiveHp >= envelope.baselineRequiredHpTarget + input.marginFraction * maxHp,
        }
    })

    if (opts.json) {
        console.log(JSON.stringify({ envelope, comparison }, null, 2))
    } else {
        console.log(formatSurvivalEnvelope(envelope))
        if (comparison.length) {
            console.log("")
            console.log(formatStaminaComparison(comparison))
        }
    }

    return envelope.survivesSelectedRisk ? 0 : 1
}

process.exitCode = main(process.argv.slice(2))
