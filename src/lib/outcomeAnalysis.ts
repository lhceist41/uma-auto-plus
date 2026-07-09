/**
 * Outcome-corpus analysis (Stages 3-4 of the outcome-measurement plan).
 *
 * Pure parsing/aggregation logic for per-career outcome records, consumed by
 * `scripts/analyze-outcomes.mjs` (CLI) and the Jest suite. Two input shapes:
 *
 * - JSONL corpus lines written on-device by `OutcomeCorpus.kt` (one JSON object per line).
 * - Legacy `[CAREER_END]` ledger lines harvested from pulled message logs, so the corpus
 *   that predates the JSONL writer is still analyzable. Ledger lines carry no config
 *   fingerprint; they group under the "legacy" arm.
 *
 * The corpus is NOT one-row-per-started-run: hard terminators (watchdog killProcess,
 * pre-start failures) emit nothing. Read distributions, never absolute run counts.
 */

export interface OutcomeRecord {
    /** Epoch millis when the record was written (JSONL only). */
    ts?: number
    /** App versionName (JSONL only). */
    app?: string
    /** Config-arm fingerprint (JSONL only). */
    fp?: string
    result: string
    outcome: string
    forceEndReason?: string
    stopReason?: string
    trainee: string
    scenario: string
    turn: number
    fans: number
    spd: number
    sta: number
    pwr: number
    grt: number
    wit: number
    skillPts: number
    /** Finale races entered (URA finale only today; undefined on records predating the feature). */
    finaleRaces?: number
    /** Finale races taken at 1st place (the "Congratulations" banner). */
    finaleWins?: number
    /** Career-quality label: WIN / FINALE_LOST / COMPLETED / FORCE_END / INCOMPLETE (undefined on old records). */
    quality?: string
    cfg?: Record<string, string>
    source: "jsonl" | "log"
    file?: string
}

/**
 * Outcome bucket by end turn, refined by the finale win/lose signal where present. The finale block
 * sits at turns 73-75, so >=73 reads as a full arc by turn alone — but a URA career that reached the
 * finale and LOST it (quality=FINALE_LOST) is bucketed "late", not "full", and a confirmed sweep
 * (quality=WIN) as "full". Records without the signal (Unity Cup / Trackblazer / legacy) fall back to
 * the turn bands, where the 60-72 "late" band still hides lost finals and stat walls.
 */
export type OutcomeBucket = "full" | "late" | "mid" | "early" | "incomplete"

export interface ArmSummary {
    trainee: string
    scenario: string
    /** `app@fp` for corpus records, "legacy" for harvested ledger lines. */
    arm: string
    n: number
    buckets: Record<OutcomeBucket, number>
    /** Share of non-incomplete runs that completed the full arc. */
    fullRate: number
    /** Careers that reached an observed finale (URA), and how many were won outright (quality=WIN).
     * 0/0 for scenarios that don't observe finale wins (Unity Cup, Trackblazer, legacy records). */
    finaleReached: number
    finaleWon: number
    fans: { p25: number; p50: number; p75: number }
    medianStats: { spd: number; sta: number; pwr: number; grt: number; wit: number }
    /** End turns of the non-full, non-incomplete runs, ascending — where this arm dies. */
    forceEndTurns: number[]
    /** True when n is too small to read as anything but anecdote. */
    lowN: boolean
}

/**
 * Arms below this many records are flagged lowN. 8 is the floor for reading a direction at
 * all; the plan's honesty note puts conclusions at ~20 runs/arm, so even unflagged arms only
 * catch regressions and big wins, not small deltas.
 */
export const LOW_N_THRESHOLD = 8

const LEDGER_MARKER = "[CAREER_END] "

function toInt(value: string | undefined): number {
    const n = value === undefined ? NaN : parseInt(value, 10)
    return Number.isFinite(n) ? n : 0
}

/**
 * Ledger names come from raw OCR: underscores stand in for spaces, and a capital "El" scans
 * as "EI" often enough that the corpus already contains both spellings of El Condor Pasa.
 * Without the fixup the same trainee splits into two arms.
 */
function normalizeName(raw: string): string {
    return raw.replace(/_/g, " ").replace(/^EI /, "El ")
}

/**
 * Parses one `[CAREER_END]` ledger line into a record, or null when the line is not a ledger
 * line. Handles both the modern format (with `outcome=`) and the pre-outcome legacy format;
 * quoted values (stopReason, forceEndReason) may contain spaces.
 */
export function parseLedgerLine(line: string, file?: string): OutcomeRecord | null {
    const at = line.indexOf(LEDGER_MARKER)
    if (at < 0) return null
    const body = line.slice(at + LEDGER_MARKER.length)

    const fields: Record<string, string> = {}
    const tokenRe = /(\w+)=("([^"]*)"|\S+)/g
    let m: RegExpExecArray | null
    while ((m = tokenRe.exec(body)) !== null) {
        fields[m[1]] = m[3] !== undefined ? m[3] : m[2]
    }
    // skillPts is the last always-present field: a line cut mid-write (log rotation, crash)
    // would otherwise sneak in with zeroed stats and pollute every distribution.
    if (!fields.result || !fields.trainee || !fields.turn || fields.skillPts === undefined) return null

    const result = fields.result
    return {
        result,
        // Legacy lines predate the outcome field: COMPLETE was the only clean-end code.
        outcome: fields.outcome ?? (result === "COMPLETE" ? "COMPLETED" : "INCOMPLETE"),
        forceEndReason: fields.forceEndReason,
        stopReason: fields.stopReason,
        trainee: normalizeName(fields.trainee),
        scenario: (fields.scenario ?? "unknown").replace(/_/g, " "),
        turn: toInt(fields.turn),
        fans: toInt(fields.fans),
        spd: toInt(fields.spd),
        sta: toInt(fields.sta),
        pwr: toInt(fields.pwr),
        grt: toInt(fields.grt),
        wit: toInt(fields.wit),
        skillPts: toInt(fields.skillPts),
        finaleRaces: fields.finaleRaces !== undefined ? toInt(fields.finaleRaces) : undefined,
        finaleWins: fields.finaleWins !== undefined ? toInt(fields.finaleWins) : undefined,
        quality: fields.quality,
        source: "log",
        file,
    }
}

/** Harvests every ledger line out of a full message-log text. */
export function harvestLogText(text: string, file?: string): OutcomeRecord[] {
    const records: OutcomeRecord[] = []
    for (const line of text.split("\n")) {
        const record = parseLedgerLine(line, file)
        if (record) records.push(record)
    }
    return records
}

/** Parses a JSONL corpus text; malformed lines are skipped, never fatal. */
export function parseJsonl(text: string, file?: string): OutcomeRecord[] {
    const records: OutcomeRecord[] = []
    for (const raw of text.split("\n")) {
        const line = raw.trim()
        if (!line) continue
        try {
            const obj = JSON.parse(line)
            if (typeof obj !== "object" || obj === null || !obj.result || !obj.trainee) continue
            records.push({
                ts: obj.ts,
                app: obj.app,
                fp: obj.fp,
                result: String(obj.result),
                outcome: String(obj.outcome ?? "COMPLETED"),
                forceEndReason: obj.forceEndReason,
                stopReason: obj.stopReason,
                trainee: normalizeName(String(obj.trainee)),
                scenario: String(obj.scenario ?? "unknown").replace(/_/g, " "),
                turn: Number(obj.turn) || 0,
                fans: Number(obj.fans) || 0,
                spd: Number(obj.spd) || 0,
                sta: Number(obj.sta) || 0,
                pwr: Number(obj.pwr) || 0,
                grt: Number(obj.grt) || 0,
                wit: Number(obj.wit) || 0,
                skillPts: Number(obj.skillPts) || 0,
                finaleRaces: obj.finaleRaces !== undefined ? Number(obj.finaleRaces) : undefined,
                finaleWins: obj.finaleWins !== undefined ? Number(obj.finaleWins) : undefined,
                quality: obj.quality !== undefined ? String(obj.quality) : undefined,
                cfg: obj.cfg,
                source: "jsonl",
                file,
            })
        } catch {
            // Malformed line (interrupted write, manual edit): skip, keep reading.
        }
    }
    return records
}

/** Buckets a record by outcome label, the finale win/lose signal, and end turn. */
export function classifyBucket(record: OutcomeRecord): OutcomeBucket {
    if (record.outcome === "INCOMPLETE") return "incomplete"
    // A source-confirmed force-end is a loss even when it happens inside the finale block
    // (a lost mandatory finals race ends at turn 73-75): never count it as a full arc.
    if (record.outcome === "FORCE_END") {
        if (record.turn >= 60) return "late"
        if (record.turn >= 30) return "mid"
        return "early"
    }
    // The finale win/lose signal overrides the turn proxy where it was blind (URA only today): a
    // career that reached the finale but lost a finals race is NOT a full arc even at turn 75, and a
    // confirmed sweep is unambiguously full. Records without the signal fall back to the turn bands.
    if (record.quality === "FINALE_LOST") return "late"
    if (record.quality === "WIN") return "full"
    if (record.turn >= 73) return "full"
    if (record.turn >= 60) return "late"
    if (record.turn >= 30) return "mid"
    return "early"
}

/**
 * A record that reflects a bot fault - an UNHANDLED_EXCEPTION stop on an unrecognized screen -
 * rather than a career outcome. The career usually resumes afterward (the between-run navigator, or
 * the in-place Home-lobby re-entry, picks it back up), so its crash-time snapshot (a mid-career turn,
 * partial stats) is not an end state. Counting these as "incomplete" careers under-reports a
 * trainee's reliability, so aggregate() drops them from arm summaries; the CLI tallies them apart.
 */
export function isBotFault(record: OutcomeRecord): boolean {
    return record.result === "UNHANDLED_EXCEPTION"
}

/** Nearest-rank percentile of an unsorted number list; 0 for an empty list. */
export function percentile(values: number[], p: number): number {
    if (values.length === 0) return 0
    const sorted = [...values].sort((a, b) => a - b)
    const rank = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1))
    return sorted[rank]
}

function armOf(record: OutcomeRecord): string {
    if (record.fp) return `${record.app ?? "?"}@${record.fp}`
    return record.app ?? "legacy"
}

/**
 * Drops log-harvested duplicates of careers that also exist as JSONL records. Every career
 * since the corpus writer shipped emits BOTH a JSONL record and a `[CAREER_END]` line in its
 * message log, so pulling outcomes/ and logs/ together would double-count each run — with the
 * log copy polluting the "legacy" arm. The full field tuple (trainee through skillPts) comes
 * from the same in-memory state on both paths, so an exact match identifies the same career.
 */
export function dedupe(records: OutcomeRecord[]): OutcomeRecord[] {
    const keyOf = (r: OutcomeRecord) => [r.trainee, r.scenario, r.turn, r.fans, r.spd, r.sta, r.pwr, r.grt, r.wit, r.skillPts].join(" ")
    const jsonlKeys = new Set(records.filter((r) => r.source === "jsonl").map(keyOf))
    return records.filter((r) => r.source === "jsonl" || !jsonlKeys.has(keyOf(r)))
}

interface Group {
    trainee: string
    scenario: string
    arm: string
    list: OutcomeRecord[]
}

/** Groups records into (trainee, scenario, arm) summaries, sorted by trainee then scenario. */
export function aggregate(records: OutcomeRecord[]): ArmSummary[] {
    // Bot faults (UNHANDLED_EXCEPTION crash-stops) are not career outcomes - the career usually
    // resumes afterward via the between-run navigator or the in-place Home-lobby re-entry - so they
    // must not land in any arm's buckets or record count. The CLI tallies them separately.
    const outcomeRecords = records.filter((r) => !isBotFault(r))
    // Group identity is held on the group object, never re-split from the key: an OCR'd pipe
    // in a trainee name must not shift fields.
    const groups = new Map<string, Group>()
    for (const record of outcomeRecords) {
        const arm = armOf(record)
        const key = [record.trainee, record.scenario, arm].join(" ")
        const group = groups.get(key)
        if (group) group.list.push(record)
        else groups.set(key, { trainee: record.trainee, scenario: record.scenario, arm, list: [record] })
    }

    const summaries: ArmSummary[] = []
    for (const { trainee, scenario, arm, list } of groups.values()) {
        const buckets: Record<OutcomeBucket, number> = { full: 0, late: 0, mid: 0, early: 0, incomplete: 0 }
        for (const record of list) buckets[classifyBucket(record)]++

        // Distribution fields exclude incomplete runs: a user stop mid-career carries stats
        // of an unfinished trainee and would drag every percentile down.
        const finished = list.filter((r) => classifyBucket(r) !== "incomplete")
        const fans = finished.map((r) => r.fans)
        const median = (pick: (r: OutcomeRecord) => number) => percentile(finished.map(pick), 50)
        const nonIncomplete = list.length - buckets.incomplete
        const finaleReached = list.filter((r) => r.quality === "WIN" || r.quality === "FINALE_LOST").length
        const finaleWon = list.filter((r) => r.quality === "WIN").length

        summaries.push({
            trainee,
            scenario,
            arm,
            n: list.length,
            buckets,
            fullRate: nonIncomplete === 0 ? 0 : buckets.full / nonIncomplete,
            finaleReached,
            finaleWon,
            fans: { p25: percentile(fans, 25), p50: percentile(fans, 50), p75: percentile(fans, 75) },
            medianStats: {
                spd: median((r) => r.spd),
                sta: median((r) => r.sta),
                pwr: median((r) => r.pwr),
                grt: median((r) => r.grt),
                wit: median((r) => r.wit),
            },
            forceEndTurns: finished
                .filter((r) => classifyBucket(r) !== "full")
                .map((r) => r.turn)
                .sort((a, b) => a - b),
            lowN: list.length < LOW_N_THRESHOLD,
        })
    }

    summaries.sort((a, b) => a.trainee.localeCompare(b.trainee) || a.scenario.localeCompare(b.scenario) || a.arm.localeCompare(b.arm))
    return summaries
}

/** Renders arm summaries as a GitHub-flavored markdown table. */
export function renderMarkdown(summaries: ArmSummary[]): string {
    const lines: string[] = []
    lines.push("| Trainee | Scenario | Arm | N | Full | Late | Mid | Early | Inc | Finale W/R | Fans p50 (p25-p75) | Sta p50 | Spd p50 | Died at turns |")
    lines.push("|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|---|")
    for (const s of summaries) {
        const fans = `${s.fans.p50.toLocaleString()} (${s.fans.p25.toLocaleString()}-${s.fans.p75.toLocaleString()})`
        const died = s.forceEndTurns.length > 0 ? s.forceEndTurns.join(", ") : "-"
        const nCell = s.lowN ? `${s.n}*` : `${s.n}`
        const finale = s.finaleReached > 0 ? `${s.finaleWon}/${s.finaleReached}` : "-"
        lines.push(
            `| ${s.trainee} | ${s.scenario} | ${s.arm} | ${nCell} | ${s.buckets.full} | ${s.buckets.late} | ${s.buckets.mid} | ${s.buckets.early} | ${s.buckets.incomplete} | ${finale} | ${fans} | ${s.medianStats.sta} | ${s.medianStats.spd} | ${died} |`
        )
    }
    lines.push("")
    lines.push(
        `\\* fewer than ${LOW_N_THRESHOLD} records: anecdote, not signal. Full = confirmed finale WIN or turn >=73 with no loss signal; a URA finale loss now falls to Late. Finale W/R = careers won / reached the finale (URA only).`
    )
    return lines.join("\n")
}
