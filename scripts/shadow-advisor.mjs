// shadow-advisor - Shadow Advisor S2 offline corpus evaluation (read-only companion to replay-lab.mjs).
//
// Evaluates the landed default S1 policy (`raw-gain-ranker-v1`) against a decision + career_state corpus and
// reports factual coverage / agreement / disagreement metrics. It never re-runs bot policy, never tunes
// weights, never uses a career outcome, and never claims accuracy or that the advisor "would have" done
// better. RACE-committed turns are non-comparable by design.
//
// Usage:
//   node scripts/shadow-advisor.mjs --trace <decisions.jsonl> --state <career_state.jsonl>
//                                   [--career-token <token>] [--json] [--details] [--help]
//
// Exit codes: 0 clean | 1 non-fatal corpus issues (duplicate/missing rows, schema failures) | 2 usage/IO/JSON-syntax error.
// A JOINED-only evaluation with insufficient/notApplicable rows is a normal exit 0; disagreement is never an error.
//
// Requires node >= 23.6 (native TypeScript type stripping; logic lives in src/lib/shadowAdvisor/).

import { createReadStream, existsSync, statSync } from "node:fs"
import { createInterface } from "node:readline"
import { evaluateCorpus, stableStringify } from "../src/lib/shadowAdvisor/evaluate.ts"
import { renderEvaluationReport } from "../src/lib/shadowAdvisor/report.ts"

const HELP = `shadow-advisor - Shadow Advisor S2 corpus evaluation (read-only)

Options:
  --trace <path>          Path to decisions.jsonl (required).
  --state <path>          Path to career_state.jsonl (required; S1 evaluation is JOINED-only).
  --career-token <token>  Evaluate only the career with this exact careerToken (no substring match).
  --json                  Emit the complete deterministic evaluation as JSON instead of the text report.
  --details               Add one deterministic per-turn row per evaluated context to the text report.
  --help                  Show this help.

Exit: 0 clean | 1 non-fatal corpus issues | 2 usage/IO/JSON-syntax error.`

function parseArgs(argv) {
    const opts = { trace: null, state: null, careerToken: undefined, json: false, details: false, help: false }
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i]
        const next = () => {
            const v = argv[++i]
            if (v === undefined) throw new Error(`${arg} requires a value`)
            return v
        }
        switch (arg) {
            case "--trace":
                opts.trace = next()
                break
            case "--state":
                opts.state = next()
                break
            case "--career-token":
                opts.careerToken = next()
                break
            case "--json":
                opts.json = true
                break
            case "--details":
                opts.details = true
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

/** Reads a JSONL file into parsed records. Throws on the first non-blank line that is not valid JSON. */
async function readJsonl(path, label) {
    const records = []
    const rl = createInterface({ input: createReadStream(path, { encoding: "utf8" }), crlfDelay: Infinity })
    let lineNumber = 0
    for await (const line of rl) {
        lineNumber++
        if (line.trim().length === 0) continue
        try {
            records.push(JSON.parse(line))
        } catch (e) {
            throw new Error(`${label} line ${lineNumber}: invalid JSON (${e instanceof Error ? e.message : String(e)})`)
        }
    }
    return records
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
    if (!opts.trace || !opts.state) {
        console.error("Both --trace <path> and --state <path> are required.\n")
        console.error(HELP)
        return 2
    }
    for (const [label, path] of [["--trace", opts.trace], ["--state", opts.state]]) {
        if (!existsSync(path) || !statSync(path).isFile()) {
            console.error(`${label} path is not a readable file: ${path}`)
            return 2
        }
    }

    let decisionRecords
    let stateRecords
    try {
        decisionRecords = await readJsonl(opts.trace, "--trace")
        stateRecords = await readJsonl(opts.state, "--state")
    } catch (e) {
        console.error(e instanceof Error ? e.message : String(e))
        return 2
    }

    const result = evaluateCorpus(decisionRecords, stateRecords, opts.careerToken !== undefined ? { careerToken: opts.careerToken } : {})

    if (opts.careerToken !== undefined && result.source.contextsBuilt === 0 && result.source.joinedCareerCount === 0) {
        console.error(`No JOINED career matched --career-token ${opts.careerToken}.`)
        // Fall through to still print the (empty) report so the caller sees the source metadata.
    }

    if (opts.json) {
        console.log(stableStringify(result))
    } else {
        console.log(renderEvaluationReport(result, opts.details))
    }
    return result.exitCode
}

// process.exitCode (not process.exit) so stdio flushes cleanly before the process ends.
main(process.argv.slice(2)).then(
    (code) => {
        process.exitCode = code
    },
    (e) => {
        console.error(`shadow-advisor failed: ${e instanceof Error ? (e.stack ?? e.message) : String(e)}`)
        process.exitCode = 2
    },
)
