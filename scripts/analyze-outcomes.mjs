// Career-outcome distribution report (Stage 4 of the outcome-measurement plan).
//
// Usage:
//   node scripts/analyze-outcomes.mjs <file-or-dir> [more paths...]
//
// Accepts the on-device JSONL corpus (outcomes/careers.jsonl), pulled message logs (*.txt,
// harvested for [CAREER_END] lines), or directories containing either. Careers recorded in
// BOTH sources (every career since the corpus writer shipped also logs a ledger line) are
// deduplicated, keeping the fingerprinted JSONL copy. Typical pull:
//
//   adb -s emulator-5554 pull "/storage/emulated/0/Android/data/com.lhceist41.uma_auto_plus/files/outcomes" .\pulled
//   adb -s emulator-5554 pull "/storage/emulated/0/Android/data/com.lhceist41.uma_auto_plus/files/logs" .\pulled
//   node scripts/analyze-outcomes.mjs .\pulled
//
// Requires node >= 23.6 (native TypeScript type stripping is default-on there; on 22.6-23.5
// run with --experimental-strip-types).

import { readFileSync, readdirSync, statSync } from "node:fs"
import { basename, extname, join } from "node:path"
import { aggregate, dedupe, harvestLogText, isBotFault, parseJsonl, renderMarkdown } from "../src/lib/outcomeAnalysis.ts"

function collectFiles(path, depth = 0) {
    const stat = statSync(path)
    if (stat.isFile()) return [path]
    if (!stat.isDirectory() || depth > 2) return []
    return readdirSync(path).flatMap((entry) => collectFiles(join(path, entry), depth + 1))
}

// process.exitCode (not process.exit) so stdio flushes cleanly - process.exit after console
// writes trips a libuv handle assert on Windows node.
function main(args) {
    if (args.length === 0) {
        console.error("Usage: node scripts/analyze-outcomes.mjs <file-or-dir> [more paths...]")
        return 1
    }

    const records = []
    let filesRead = 0
    for (const arg of args) {
        let files
        try {
            files = collectFiles(arg)
        } catch (e) {
            console.error(`Cannot read "${arg}": ${e.message}`)
            return 1
        }
        for (const file of files) {
            const ext = extname(file).toLowerCase()
            if (ext !== ".jsonl" && ext !== ".txt") continue
            const text = readFileSync(file, "utf8")
            const parsed = ext === ".jsonl" ? parseJsonl(text, basename(file)) : harvestLogText(text, basename(file))
            records.push(...parsed)
            filesRead++
        }
    }

    if (records.length === 0) {
        console.error(`No outcome records found in ${filesRead} file(s). Pull the device logs/corpus first (see the header of this script).`)
        return 1
    }

    const unique = dedupe(records)
    const dropped = records.length - unique.length
    // Bot faults (UNHANDLED_EXCEPTION crash-stops) are not career outcomes; aggregate() drops them.
    // Surface the count instead of letting them vanish silently.
    const botFaults = unique.filter(isBotFault).length
    const outcomeCount = unique.length - botFaults
    console.log(
        `${outcomeCount} career outcome(s) from ${filesRead} file(s)` +
            `${dropped > 0 ? ` (${dropped} log duplicate(s) of corpus records dropped)` : ""}` +
            `${botFaults > 0 ? `; ${botFaults} bot-fault record(s) (UNHANDLED_EXCEPTION) excluded from outcomes` : ""}\n`,
    )
    console.log(renderMarkdown(aggregate(unique)))
    console.log(
        "\nCaveats: the corpus is not one-row-per-started-run (hard terminators emit nothing)," +
            "\nand arms under the low-N threshold are anecdotes - compare distributions, not single runs.",
    )
    return 0
}

process.exitCode = main(process.argv.slice(2))
