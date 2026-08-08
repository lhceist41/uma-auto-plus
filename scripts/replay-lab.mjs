// ReplayLab v1 - offline, read-only factual replay of the decision corpus (companion to
// scripts/analyze-decisions.mjs).
//
// Reconstructs what the bot recorded from the three durable streams and joins them by careerToken + seq:
//   - decision_trace v1  (outcomes/decisions.jsonl)     - required
//   - career_state  v1   (outcomes/career_state.jsonl)  - optional; enables the JOINED capability
//   - career_finalize    (outcomes/careers.jsonl)        - optional; joins a structural outcome by token
//
// It is strictly factual: it replays recorded candidates, selections, race-eligibility, scenario
// evidence, and observed state changes between sequenced decisions. It does NOT re-run policy, does NOT
// rescore, and does NOT model counterfactuals. A state delta is a betweenDecisionObservedTransition,
// never an action effect; the training score margin is recordedScoreGap, never regret. Read-only: input
// files are never rewritten, and nothing is persisted.
//
// Usage:
//   node scripts/replay-lab.mjs --decisions <decisions.jsonl> [--career-state <career_state.jsonl>]
//                               [--careers <careers.jsonl>] [--career-token <token>] [--scenario <name>]
//                               [--fp <fingerprint>] [--action <ACTION>] [--strict] [--json] [--help]
//
// Exit codes (worst wins, matching analyze-decisions):
//   0 clean | 1 warnings (coverage/anomaly facts) | 2 parse/schema failure | 3 consistency failure
// A TRACE_ONLY career and a missing state/finalize are valid results, not errors.
//
// Requires node >= 23.6 (native TypeScript type stripping; the logic lives in src/lib/replayLab.ts and
// is exercised by the Jest suite).

import { createReadStream, existsSync, statSync } from "node:fs"
import { createInterface } from "node:readline"
import { createReplayLab, renderReplayReport } from "../src/lib/replayLab.ts"

const HELP = `replay-lab - ReplayLab v1 factual replay (read-only)

Options:
  --decisions <path>      Path to decisions.jsonl (required).
  --career-state <path>   Path to career_state.jsonl. Enables the JOINED capability and transitions.
  --careers <path>        Path to careers.jsonl, to join a structural career_finalize outcome by token.
  --career-token <token>  Replay only the career with this careerToken.
  --scenario <name>       Replay only careers whose scenario equals this value.
  --fp <fingerprint>      Replay only careers whose config fingerprint (fp) equals this value.
  --action <ACTION>       Replay only careers that committed this action at least once (career-level).
  --strict                Stop at the first decisions parse/schema failure instead of continuing.
  --json                  Print the raw ReplayLab result as JSON instead of the text report.
  --help                  Show this help.

Exit: 0 clean | 1 warnings | 2 parse/schema failure | 3 consistency failure (worst wins).`

function parseArgs(argv) {
    const opts = { decisions: null, careerState: null, careers: null, careerToken: undefined, scenario: undefined, fp: undefined, action: undefined, strict: false, json: false, help: false }
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i]
        const next = () => {
            const v = argv[++i]
            if (v === undefined) throw new Error(`${arg} requires a value`)
            return v
        }
        switch (arg) {
            case "--decisions":
                opts.decisions = next()
                break
            case "--career-state":
                opts.careerState = next()
                break
            case "--careers":
                opts.careers = next()
                break
            case "--career-token":
                opts.careerToken = next()
                break
            case "--scenario":
                opts.scenario = next()
                break
            case "--fp":
                opts.fp = next()
                break
            case "--action":
                opts.action = next()
                break
            case "--strict":
                opts.strict = true
                break
            case "--json":
                opts.json = true
                break
            case "--help":
            case "-h":
                opts.help = true
                break
            default:
                throw new Error(`unknown argument: ${arg}`)
        }
    }
    return opts
}

async function feedFile(path, onLine) {
    const stream = createReadStream(path, { encoding: "utf8" })
    // crlfDelay collapses CRLF so Windows-pulled files do not leave a stray \r on every line.
    const rl = createInterface({ input: stream, crlfDelay: Infinity })
    let lineNumber = 0
    for await (const line of rl) {
        onLine(line, ++lineNumber)
    }
    return lineNumber
}

async function main(argv) {
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
    if (!opts.decisions) {
        console.error("Missing required --decisions <path>.\n")
        console.error(HELP)
        return 2
    }
    for (const [label, path] of [["--decisions", opts.decisions], ["--career-state", opts.careerState], ["--careers", opts.careers]]) {
        if (path && (!existsSync(path) || !statSync(path).isFile())) {
            console.error(`${label} path is not a readable file: ${path}`)
            return 2
        }
    }

    const lab = createReplayLab({ careerToken: opts.careerToken, scenario: opts.scenario, fp: opts.fp, action: opts.action, strict: opts.strict })

    // Index the optional streams first, so every decision can be joined as it streams in.
    if (opts.careerState) {
        await feedFile(opts.careerState, (line, n) => lab.ingestCareerStateLine(line, n))
        lab.noteCareerStateFile()
    }
    if (opts.careers) {
        await feedFile(opts.careers, (line, n) => lab.ingestFinalizeLine(line, n))
    }

    let stopped = false
    await feedFile(opts.decisions, (line, n) => {
        if (stopped) return
        if (!lab.ingestDecisionLine(line, n)) stopped = true
    })

    const result = lab.finish()
    if (opts.json) {
        console.log(JSON.stringify(result, null, 2))
    } else {
        console.log(renderReplayReport(result))
    }
    return result.exitCode
}

// process.exitCode (not process.exit) so stdio flushes cleanly - process.exit after console writes trips
// a libuv handle assert on Windows node.
main(process.argv.slice(2)).then(
    (code) => {
        process.exitCode = code
    },
    (e) => {
        console.error(`replay-lab failed: ${e instanceof Error ? e.stack ?? e.message : String(e)}`)
        process.exitCode = 2
    },
)
