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
//   adb -s <device> pull "/storage/emulated/0/Android/data/com.lhceist41.uma_auto_plus/files/outcomes" .\pulled
//   adb -s <device> pull "/storage/emulated/0/Android/data/com.lhceist41.uma_auto_plus/files/logs" .\pulled
//   node scripts/analyze-outcomes.mjs .\pulled
//
// Requires node >= 23.6 (native TypeScript type stripping is default-on there; on 22.6-23.5
// run with --experimental-strip-types).

import { readFileSync, readdirSync, statSync } from "node:fs"
import { basename, extname, join } from "node:path"
import { aggregate, analyzeSkillSpend, analyzeSparkFarm, dedupe, harvestLogText, isBotFault, isFinalizeOnly, parseCorpus, renderMarkdown, renderSkillSpendMarkdown, renderSparkFarmMarkdown } from "../src/lib/outcomeAnalysis.ts"

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
    const sparks = []
    const skillSpends = []
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
            if (ext === ".jsonl") {
                // parseCorpus (not parseJsonl) so the spark records in the same file are not discarded.
                const parsed = parseCorpus(text, basename(file))
                records.push(...parsed.outcomes)
                sparks.push(...parsed.sparks)
                skillSpends.push(...parsed.skillSpends)
            } else {
                records.push(...harvestLogText(text, basename(file)))
            }
            filesRead++
        }
    }

    if (records.length === 0 && sparks.length === 0 && skillSpends.length === 0) {
        console.error(`No outcome, spark or skill-spend records found in ${filesRead} file(s). Pull the device logs/corpus first (see the header of this script).`)
        return 1
    }

    const unique = dedupe(records)
    const dropped = records.length - unique.length
    // Bot faults (UNHANDLED_EXCEPTION crash-stops) are not career outcomes; aggregate() drops them.
    // Surface the count instead of letting them vanish silently. Finalize-only records (a career
    // resumed at its Complete Career screen and finished without ever being played) are dropped for
    // the same reason: the run that played that career already recorded it.
    const botFaults = unique.filter(isBotFault).length
    const finalizeOnly = unique.filter((r) => !isBotFault(r) && isFinalizeOnly(r)).length
    const outcomeCount = unique.length - botFaults - finalizeOnly
    const sparkReport = analyzeSparkFarm(unique, sparks)

    console.log(
        `${outcomeCount} career outcome(s), ${sparks.length} spark record(s), ${skillSpends.length} skill-spend record(s) from ${filesRead} file(s)` +
            `${dropped > 0 ? ` (${dropped} log duplicate(s) of corpus records dropped)` : ""}` +
            `${botFaults > 0 ? `; ${botFaults} bot-fault record(s) (UNHANDLED_EXCEPTION) excluded from outcomes` : ""}` +
            `${finalizeOnly > 0 ? `; ${finalizeOnly} finalize-only record(s) (career finished but never played) excluded from outcomes` : ""}` +
            `${sparkReport.coverage.unjoined > 0 ? `; ${sparkReport.coverage.unjoined} unjoined spark record(s)` : ""}\n`,
    )

    // 1) existing career-outcome report (or a clear note for a spark-only corpus), 2) blank separator,
    // 3) Spark Farm report, 4) caveats.
    if (outcomeCount > 0) {
        console.log(renderMarkdown(aggregate(unique)))
    } else {
        console.log("_No career-outcome records in the input (spark-only corpus); showing the Spark Farm report only._")
    }
    console.log("")
    console.log(renderSparkFarmMarkdown(sparkReport))
    // Skill Spend rides after the farm report and only when the corpus actually carries the records,
    // so an older corpus renders byte-identically to before.
    if (skillSpends.length > 0) {
        console.log("")
        console.log(renderSkillSpendMarkdown(analyzeSkillSpend(skillSpends)))
    }
    console.log(
        "\nCaveats: the corpus is not one-row-per-started-run (hard terminators emit nothing)," +
            "\narms under the low-N threshold are anecdotes - compare distributions, not single runs," +
            "\nand confirmed-kept coverage below 100% means some careers' final sets were never captured.",
    )
    return 0
}

process.exitCode = main(process.argv.slice(2))
