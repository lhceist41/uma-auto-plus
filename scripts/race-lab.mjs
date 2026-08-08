// RaceLab v1 - offline, read-only race-intelligence CLI.
//
// Consumes the landed canonical compiled race layer (via the hash-verifying master-data reader) plus
// character_objectives.json to do catalog lookup, objective timelines, aptitude fit, schedule pressure,
// and racing-plan validation. It is strictly factual: it never chooses races for the bot, never predicts
// outcomes/win probability, never simulates, and never invents race identity/fans/goal state. The live
// runtime policy remains the authority. Read-only: no input file is modified; output goes to stdout/stderr.
//
// Usage:
//   node scripts/race-lab.mjs <mode> [options]
//
// Modes (pick one; default = catalog-stats):
//   --catalog-stats                     race/key/collision counts
//   --race-name <name> [--turn <N>]     canonical (name,turn) lookup, or all same-name races
//   --character <name>                  objective timeline for a trainee (URA-scoped)
//   --validate --character <name> --plan <path-or-json>   validate a racing plan
//   --pressure --character <name> [--plan <path-or-json>] schedule-pressure summary
//
// Options:
//   --master-data <dir>   compiled dir (default: <repo>/src/data/compiled)
//   --objectives <path>   character_objectives.json (default: <repo>/src/data/character_objectives.json)
//   --consecutive-limit <N>  flag streaks reaching N (scenario-specific, e.g. Trackblazer default 2)
//   --json                machine-readable output
//   --help
//
// Exit: 0 ok | 1 validation errors present | 2 usage/load error.
//
// Requires node >= 23.6 (native TypeScript type stripping; logic lives in src/lib/raceLab/).

import { existsSync, statSync } from "node:fs"
import { join, dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"
import { loadRaceCatalog } from "../src/lib/raceLab/catalog.ts"
import { buildObjectiveTimeline, buildAllObjectiveTimelines, loadRawObjectives, RaceLabError } from "../src/lib/raceLab/objectives.ts"
import { loadPlan, validatePlan } from "../src/lib/raceLab/planValidator.ts"
import { buildSchedule, analyzePressure } from "../src/lib/raceLab/pressure.ts"

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..")

const HELP = `race-lab - RaceLab v1 offline race intelligence (read-only, factual)

Modes (default: --catalog-stats):
  --catalog-stats
  --race-name <name> [--turn <N>]
  --character <name>
  --validate --character <name> --plan <path-or-json>
  --pressure --character <name> [--plan <path-or-json>]

Options:
  --master-data <dir>       compiled dir (default: <repo>/src/data/compiled)
  --objectives <path>       character_objectives.json (default: <repo>/src/data/character_objectives.json)
  --consecutive-limit <N>   flag streaks reaching N (scenario-specific; Trackblazer default 2)
  --json                    machine-readable output
  --help

Exit: 0 ok | 1 validation errors | 2 usage/load error.`

function parseArgs(argv) {
    const opts = { masterData: join(ROOT, "src/data/compiled"), objectives: join(ROOT, "src/data/character_objectives.json"), raceName: undefined, turn: undefined, character: undefined, plan: undefined, consecutiveLimit: undefined, mode: undefined, json: false, help: false }
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i]
        const next = () => {
            const v = argv[++i]
            if (v === undefined) throw new Error(`${arg} requires a value`)
            return v
        }
        switch (arg) {
            case "--master-data": opts.masterData = resolve(next()); break
            case "--objectives": opts.objectives = resolve(next()); break
            case "--race-name": opts.raceName = next(); opts.mode = "race"; break
            case "--turn": opts.turn = Number(next()); break
            case "--character": opts.character = next(); break
            case "--plan": opts.plan = next(); break
            case "--consecutive-limit": opts.consecutiveLimit = Number(next()); break
            case "--catalog-stats": opts.mode = "catalog"; break
            case "--validate": opts.mode = "validate"; break
            case "--pressure": opts.mode = "pressure"; break
            case "--json": opts.json = true; break
            case "--help": case "-h": opts.help = true; break
            default: throw new Error(`unknown argument: ${arg}`)
        }
    }
    if (!opts.mode) opts.mode = opts.character ? "objective" : "catalog"
    return opts
}

function emit(opts, obj, text) {
    if (opts.json) console.log(JSON.stringify(obj, null, 2))
    else console.log(text)
}

function main(argv) {
    let opts
    try {
        opts = parseArgs(argv)
    } catch (e) {
        console.error(`${e.message}\n`)
        console.error(HELP)
        return 2
    }
    if (opts.help) {
        console.log(HELP)
        return 0
    }
    for (const [label, p] of [["--master-data", opts.masterData], ["--objectives", opts.objectives]]) {
        if (label === "--master-data" ? !existsSync(p) : opts.mode !== "catalog" && opts.mode !== "race" && (!existsSync(p) || !statSync(p).isFile())) {
            console.error(`${label} path not found: ${p}`)
            return 2
        }
    }

    let catalog
    try {
        catalog = loadRaceCatalog(opts.masterData)
    } catch (e) {
        console.error(`failed to load compiled master data: ${e instanceof Error ? e.message : String(e)}`)
        return 2
    }

    try {
        if (opts.mode === "catalog") {
            const s = catalog.catalogStats()
            emit(opts, { mode: "catalogStats", fingerprint: catalog.fingerprint(), ...s }, `races ${s.raceCount} | unique keys ${s.uniqueKeyCount} | distinct names ${s.distinctBareNameCount} | bare-name collisions ${s.bareNameCollisionCount} | fingerprint ${catalog.fingerprint()}`)
            return 0
        }
        if (opts.mode === "race") {
            if (opts.turn !== undefined) {
                const race = catalog.raceByKey(opts.raceName, opts.turn)
                emit(opts, { mode: "race", race: race ?? null }, race ? `${race.name} @${race.turnNumber} | ${race.grade} ${race.raceTrack} ${race.terrain} ${race.distanceType} ${race.distanceMeters}m | fans ${race.fans}` : `no race "${opts.raceName}" on turn ${opts.turn}`)
                return race ? 0 : 1
            }
            const all = catalog.racesByName(opts.raceName)
            emit(opts, { mode: "racesByName", races: all }, all.length ? all.map((r) => `${r.name} @${r.turnNumber} (${r.grade} ${r.terrain} ${r.distanceType})`).join("\n") : `no race named "${opts.raceName}"`)
            return all.length ? 0 : 1
        }
        if (opts.mode === "objective") {
            const timeline = buildObjectiveTimeline(opts.character, loadRawObjectives(opts.objectives), catalog)
            emit(opts, { mode: "objectiveTimeline", ...timeline }, renderTimeline(timeline))
            return 0
        }
        if (opts.mode === "validate" || opts.mode === "pressure") {
            if (!opts.character) {
                console.error("--character is required for this mode")
                return 2
            }
            const timeline = buildObjectiveTimeline(opts.character, loadRawObjectives(opts.objectives), catalog)
            const parsed = opts.plan !== undefined ? loadPlan(opts.plan) : { plan: [], issues: [] }
            if (opts.mode === "pressure") {
                const report = analyzePressure(buildSchedule(timeline.requirements, parsed.plan), { consecutiveLimit: opts.consecutiveLimit })
                emit(opts, { mode: "pressure", character: opts.character, ...report }, renderPressure(report))
                return 0
            }
            const report = validatePlan(parsed.plan, catalog, timeline, { consecutiveLimit: opts.consecutiveLimit, parseIssues: parsed.issues })
            emit(opts, { mode: "planValidation", character: opts.character, ...report }, renderValidation(report))
            return report.ok ? 0 : 1
        }
        console.error(`unknown mode: ${opts.mode}`)
        return 2
    } catch (e) {
        if (e instanceof RaceLabError) {
            console.error(`RaceLab error [${e.code}]: ${e.message}`)
            return 2
        }
        throw e
    }
}

function renderTimeline(t) {
    const lines = [`objective timeline for ${t.character} [scenario ${t.scenario}] - ${t.requirements.length} objective turn(s)`]
    for (const r of t.requirements) {
        lines.push(`  turn ${r.turn}${r.isChoice ? " (choice)" : ""}: ${r.options.map((o) => `${o.raceName} [${o.canonicalRace.grade} ${o.canonicalRace.terrain} ${o.canonicalRace.distanceType}]`).join(r.isChoice ? " / " : "")}`)
    }
    return lines.join("\n")
}

function renderPressure(p) {
    const lines = [`schedule pressure: ${p.entries.length} scheduled race turn(s), ${p.streaks.length} streak(s), ${p.sameTurn.length} same-turn stack(s), ${p.gaps.length} gap(s)`]
    for (const s of p.streaks) lines.push(`  streak turns ${s.startTurn}..${s.endTurn} (${s.length}) source=${s.source}${s.reachesConsecutiveLimit === true ? " [reaches limit]" : ""}`)
    for (const s of p.sameTurn) lines.push(`  same-turn turn ${s.turn}: ${s.raceCount} races`)
    return lines.join("\n")
}

function renderValidation(r) {
    const errors = r.issues.filter((i) => i.severity === "error").length
    const lines = [`plan validation: ${r.plan.length} entr(y/ies), ${errors} error(s), ok=${r.ok}`]
    for (const i of r.issues) lines.push(`  [${i.severity}] ${i.code}${i.turn !== null ? ` (turn ${i.turn})` : ""}: ${i.detail}`)
    return lines.join("\n")
}

// process.exitCode (not process.exit) so stdio flushes cleanly on Windows node.
try {
    process.exitCode = main(process.argv.slice(2))
} catch (e) {
    console.error(`race-lab failed: ${e instanceof Error ? (e.stack ?? e.message) : String(e)}`)
    process.exitCode = 2
}
