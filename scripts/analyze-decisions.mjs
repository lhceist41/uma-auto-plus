// DecisionTrace v1 corpus reader (live-validation companion to scripts/analyze-outcomes.mjs).
//
// Reads the on-device per-turn decision corpus (outcomes/decisions.jsonl) written by
// DecisionTrace.kt, checks each record's internal consistency, and - when the careers corpus is
// also supplied - joins each career to its career_finalize row on careerToken. Read-only: the
// input files are never rewritten, normalized or truncated.
//
// Usage:
//   node scripts/analyze-decisions.mjs --decisions <decisions.jsonl> [--careers <careers.jsonl>]
//                                      [--career-token <token>] [--since <epochMs>]
//                                      [--from-line <N>] [--aggregate] [--strict] [--json] [--help]
//
// With --aggregate the reader emits one corpus-level cross-career summary instead of the per-career
// report: what actions and trainings the bot chose across careers, broken down by scenario, plus trace
// coverage, candidate diagnostics, and the joined career_finalize outcomes. It is descriptive only - it
// scores no decision and claims no causality. The same filters (--career-token/--since/--from-line)
// apply before aggregation. Exit-code semantics are unchanged.
//
// Typical pull (MuMu; the device serial and port are dynamic - see /triage):
//   adb -s emulator-5554 pull "/storage/emulated/0/Android/data/com.lhceist41.uma_auto_plus/files/outcomes/decisions.jsonl" .\pulled\
//   adb -s emulator-5554 pull "/storage/emulated/0/Android/data/com.lhceist41.uma_auto_plus/files/outcomes/careers.jsonl"  .\pulled\
//   node scripts/analyze-decisions.mjs --decisions .\pulled\decisions.jsonl --careers .\pulled\careers.jsonl
//
// Corpus-level cross-career summary (descriptive; scores no decision):
//   node scripts/analyze-decisions.mjs --decisions .\pulled\decisions.jsonl --careers .\pulled\careers.jsonl --aggregate
//
// Isolating one new run: capture the decisions line count BEFORE the run, then pass
// --from-line <preRunLineCount+1> (or --since <wall-clock-start-ms>, or --career-token <token>).
//
// Exit codes:
//   0  clean            - no parse errors, no schema failures, no consistency failures, no warnings
//   1  warnings         - analysis succeeded with non-blocking anomalies (duplicate/non-monotonic
//                         turns, empty selections, missing identity, an unclean careers join)
//   2  parse/schema     - a line could not be parsed, or carried the wrong type / an unsupported version
//   3  consistency      - a record contradicts itself (committed selection absent from its candidates,
//                         or more than one selected candidate of a kind)
// When several categories are present the worst (highest) code is returned.
//
// Requires node >= 23.6 (native TypeScript type stripping; the shared analysis logic lives in
// src/lib/decisionAnalysis.ts and is exercised by the Jest suite).

import { createReadStream, existsSync, statSync } from "node:fs"
import { createInterface } from "node:readline"
import { createDecisionAnalyzer, renderReport, renderAggregateReport } from "../src/lib/decisionAnalysis.ts"

const HELP = `analyze-decisions - DecisionTrace v1 corpus reader (read-only)

Options:
  --decisions <path>      Path to decisions.jsonl (required).
  --careers <path>        Path to careers.jsonl, to join each career to its career_finalize row.
  --career-token <token>  Analyze only records whose careerToken equals this value.
  --since <epochMs>       Analyze only records with ts >= this epoch-millis boundary.
  --from-line <N>         Analyze only decisions lines with 1-based number >= N (isolate a new run).
  --aggregate             Emit one corpus-level cross-career summary (descriptive; scores no decision).
                          Filters above apply before aggregation. Joins career_finalize 1:1 on careerToken;
                          outcome stats report their denominator so missing finalize rows stay visible.
  --strict                Abort at the first parse/schema failure instead of continuing past it.
  --json                  Print the raw analysis result as JSON instead of the text report.
                          With --aggregate this prints the structured aggregate object.
  --help                  Show this help.

Exit: 0 clean | 1 warnings | 2 parse/schema failure | 3 consistency failure (worst wins).`

function parseArgs(argv) {
    const opts = { decisions: null, careers: null, careerToken: undefined, since: undefined, fromLine: undefined, aggregate: false, strict: false, json: false, help: false }
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
            case "--careers":
                opts.careers = next()
                break
            case "--career-token":
                opts.careerToken = next()
                break
            case "--since": {
                const v = Number(next())
                if (!Number.isFinite(v)) throw new Error("--since must be a number (epoch millis)")
                opts.since = v
                break
            }
            case "--from-line": {
                const v = Number(next())
                if (!Number.isInteger(v) || v < 1) throw new Error("--from-line must be a positive integer")
                opts.fromLine = v
                break
            }
            case "--aggregate":
                opts.aggregate = true
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
    for (const [label, path] of [["--decisions", opts.decisions], ["--careers", opts.careers]]) {
        if (path && (!existsSync(path) || !statSync(path).isFile())) {
            console.error(`${label} path is not a readable file: ${path}`)
            return 2
        }
    }

    const analyzer = createDecisionAnalyzer({
        careerToken: opts.careerToken,
        since: opts.since,
        fromLine: opts.fromLine,
        strict: opts.strict,
        aggregate: opts.aggregate,
    })

    // Join index first, so every decision record can be joined as it streams in.
    if (opts.careers) {
        await feedFile(opts.careers, (line, n) => analyzer.ingestCareerLine(line, n))
        analyzer.noteCareerFile()
    }

    let stopped = false
    await feedFile(opts.decisions, (line, n) => {
        if (stopped) return
        if (!analyzer.ingestDecisionLine(line, n)) stopped = true
    })
    analyzer.noteDecisionFile()

    const result = analyzer.finish()
    if (opts.aggregate) {
        // Aggregate mode prints the corpus summary; --json emits the structured aggregate object.
        if (opts.json) {
            console.log(JSON.stringify(result.aggregate, null, 2))
        } else {
            console.log(renderAggregateReport(result.aggregate))
        }
    } else if (opts.json) {
        console.log(JSON.stringify(result, null, 2))
    } else {
        console.log(renderReport(result))
    }
    return result.exitCode
}

// process.exitCode (not process.exit) so stdio flushes cleanly - process.exit after console
// writes trips a libuv handle assert on Windows node.
main(process.argv.slice(2)).then(
    (code) => {
        process.exitCode = code
    },
    (e) => {
        console.error(`analyze-decisions failed: ${e instanceof Error ? e.stack ?? e.message : String(e)}`)
        process.exitCode = 2
    },
)
