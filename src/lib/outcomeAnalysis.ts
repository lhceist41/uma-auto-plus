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
    /** 0-based line index within `file` (JSONL only); anchors the positional spark association. */
    lineNumber?: number
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

export interface SparkRowRecord {
    /** Raw OCR text of the spark label. Star count and kind are pixel-classified and authoritative; the name is not. */
    name: string
    /** 0-3, pixel-classified (no OCR). */
    stars: number
    kind: "stat" | "aptitude" | "unique" | "skill"
}

/**
 * One career-end spark set as appended on-device (`type:"sparks"` records). `phase="kept"` is the
 * only authoritative final confirmed set; `original`/`rerolled` are observed-only. `file`+`lineNumber`
 * preserve write order so a set can be positionally associated with the career that produced it
 * (historical records carry no fp/scenario). `fp`/`scenario` are honored if a future writer stamps them.
 */
export interface SparkRecord {
    type: "sparks"
    ts?: number
    trainee?: string
    phase: string
    rows: SparkRowRecord[]
    fp?: string
    scenario?: string
    file?: string
    lineNumber: number
    /** Rows dropped as malformed (unusable kind / non-numeric stars) while parsing this record. */
    droppedRows: number
}

/**
 * One skill-spend session as appended on-device (`type:"skill_spend"` records) — one per invocation of
 * the purchaser, never one per internal scroll pass. `proposed` is what the planner chose; `confirmed`
 * is what the screen evidenced as obtained. Every identity field is optional: the writer omits what it
 * cannot know rather than fabricating it, so readers must tolerate absence.
 */
export interface SkillSpendRecord {
    type: "skill_spend"
    ts?: number
    policy?: string
    outcome: string
    trigger?: string
    plan?: string
    strategy?: string
    trainee?: string
    scenario?: string
    fp?: string
    turn?: number
    spBefore?: number
    spAfter?: number
    unspent?: number
    proposed: { name: string; price: number }[]
    confirmed: string[]
    skipped: { name: string; reason: string }[]
    // Set when the points delta proved the screen's obtained set missed a purchase, so `confirmed` is
    // a floor rather than the full set. Such a session carries no `skipped` rows: which planned skills
    // went unbought is exactly what the gap makes unknowable.
    confirmedIncomplete?: boolean
    // trigger-v2 threshold-policy attribution: the resolved high-water threshold in effect for the
    // career, the tier that produced it ("manual" when no tier governs), and the resolution reason.
    // Absent on trigger-v1 records — readers must never infer values for them.
    threshold?: number
    tier?: string
    reason?: string
    file?: string
    lineNumber: number
}

/** All record kinds parsed from one JSONL corpus, each tagged with file + line number. */
export interface ParsedCorpus {
    outcomes: OutcomeRecord[]
    sparks: SparkRecord[]
    skillSpends: SkillSpendRecord[]
}

const SPARK_KINDS = new Set(["stat", "aptitude", "unique", "skill"])

/** Validates+coerces one spark row; null when the row is malformed (unusable kind or star count). */
function parseSparkRow(raw: unknown): SparkRowRecord | null {
    if (typeof raw !== "object" || raw === null) return null
    const r = raw as Record<string, unknown>
    const kind = typeof r.kind === "string" ? r.kind : ""
    if (!SPARK_KINDS.has(kind)) return null
    const stars = Number(r.stars)
    if (!Number.isFinite(stars)) return null
    return { name: String(r.name ?? "").trim(), stars: Math.max(0, Math.round(stars)), kind: kind as SparkRowRecord["kind"] }
}

/**
 * Parses a JSONL corpus into career outcomes AND `type:"sparks"` records, preserving each record's
 * file and 0-based line number. Malformed lines and malformed spark rows are skipped, never fatal;
 * a single bad row never discards the surrounding set. `parseJsonl` delegates to this.
 */
export function parseCorpus(text: string, file?: string): ParsedCorpus {
    const outcomes: OutcomeRecord[] = []
    const sparks: SparkRecord[] = []
    const skillSpends: SkillSpendRecord[] = []
    const rawLines = text.split("\n")
    for (let i = 0; i < rawLines.length; i++) {
        const line = rawLines[i].trim()
        if (!line) continue
        let obj: any
        try {
            obj = JSON.parse(line)
        } catch {
            continue // malformed line (interrupted write, manual edit): skip, keep reading.
        }
        if (typeof obj !== "object" || obj === null) continue

        if (obj.type === "sparks") {
            // A malformed typed record (rows not an array) is skipped like any malformed line.
            if (!Array.isArray(obj.rows)) continue
            const rows: SparkRowRecord[] = []
            let dropped = 0
            for (const rawRow of obj.rows) {
                const row = parseSparkRow(rawRow)
                if (row) rows.push(row)
                else dropped++
            }
            sparks.push({
                type: "sparks",
                ts: obj.ts !== undefined ? Number(obj.ts) : undefined,
                trainee: obj.trainee !== undefined ? normalizeName(String(obj.trainee)) : undefined,
                phase: typeof obj.phase === "string" ? obj.phase : "unknown",
                rows,
                fp: obj.fp !== undefined ? String(obj.fp) : undefined,
                scenario: obj.scenario !== undefined ? String(obj.scenario).replace(/_/g, " ") : undefined,
                file,
                lineNumber: i,
                droppedRows: dropped,
            })
            continue
        }

        if (obj.type === "skill_spend") {
            // A record with no usable outcome is malformed: skip it like any malformed line rather than
            // invent an outcome bucket for it.
            if (typeof obj.outcome !== "string" || !obj.outcome) continue
            skillSpends.push({
                type: "skill_spend",
                ts: obj.ts !== undefined ? Number(obj.ts) : undefined,
                policy: obj.policy !== undefined ? String(obj.policy) : undefined,
                outcome: String(obj.outcome),
                trigger: obj.trigger !== undefined ? String(obj.trigger) : undefined,
                plan: obj.plan !== undefined ? String(obj.plan) : undefined,
                strategy: obj.strategy !== undefined ? String(obj.strategy) : undefined,
                trainee: obj.trainee !== undefined ? normalizeName(String(obj.trainee)) : undefined,
                scenario: obj.scenario !== undefined ? String(obj.scenario).replace(/_/g, " ") : undefined,
                fp: obj.fp !== undefined ? String(obj.fp) : undefined,
                turn: Number.isFinite(Number(obj.turn)) ? Number(obj.turn) : undefined,
                spBefore: Number.isFinite(Number(obj.spBefore)) ? Number(obj.spBefore) : undefined,
                spAfter: Number.isFinite(Number(obj.spAfter)) ? Number(obj.spAfter) : undefined,
                unspent: Number.isFinite(Number(obj.unspent)) ? Number(obj.unspent) : undefined,
                proposed: Array.isArray(obj.proposed)
                    ? obj.proposed
                          .filter((p: any) => p && typeof p.name === "string" && Number.isFinite(Number(p.price)))
                          .map((p: any) => ({ name: String(p.name), price: Number(p.price) }))
                    : [],
                confirmed: Array.isArray(obj.confirmed) ? obj.confirmed.filter((n: any) => typeof n === "string").map((n: any) => String(n)) : [],
                skipped: Array.isArray(obj.skipped)
                    ? obj.skipped.filter((s: any) => s && typeof s.name === "string" && typeof s.reason === "string").map((s: any) => ({ name: String(s.name), reason: String(s.reason) }))
                    : [],
                confirmedIncomplete: obj.confirmedIncomplete === true ? true : undefined,
                threshold: Number.isFinite(Number(obj.threshold)) && obj.threshold !== undefined ? Number(obj.threshold) : undefined,
                tier: obj.tier !== undefined ? String(obj.tier) : undefined,
                reason: obj.reason !== undefined ? String(obj.reason) : undefined,
                file,
                lineNumber: i,
            })
            continue
        }

        // Any other typed record is not a career outcome (mirrors the pre-spark parseJsonl skip).
        if (obj.type !== undefined) continue
        if (!obj.result || !obj.trainee) continue
        outcomes.push({
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
            lineNumber: i,
        })
    }
    return { outcomes, sparks, skillSpends }
}

/** Parses a JSONL corpus text into career outcomes only; malformed lines are skipped, never fatal.
 * Auxiliary typed records (e.g. sparks) are excluded for backward compatibility — use `parseCorpus`
 * to also get spark records. */
export function parseJsonl(text: string, file?: string): OutcomeRecord[] {
    return parseCorpus(text, file).outcomes
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

// ===========================================================================
// Spark Farm Analyzer — read-side over the existing `type:"sparks"` corpus records.
//
// Confirmed-kept (phase="kept") is the ONLY authoritative final set. original/rerolled are
// observed-only and are never reported as retained. Nothing here claims a spark or veteran is
// "currently owned": the corpus cannot know whether a produced veteran was later transferred or
// released. "Missing" always means "missing from the confirmed-kept corpus records", not the account.
// ===========================================================================

export const BLUE_STATS = ["Speed", "Stamina", "Power", "Guts", "Wit"] as const
export type BlueStat = (typeof BLUE_STATS)[number]

/** Outcome-record field holding each blue stat's final value. */
const STAT_FIELD: Record<BlueStat, "spd" | "sta" | "pwr" | "grt" | "wit"> = {
    Speed: "spd",
    Stamina: "sta",
    Power: "pwr",
    Guts: "grt",
    Wit: "wit",
}

/** The 3-star blue-spark stat floor (a blue spark below this final value cannot roll 3 stars). */
export const BLUE_3STAR_FLOOR = 600
/** The reroll gate's high band; a final stat here has the best 3-star blue odds. */
export const HIGH_STAT_BAND = 1100

function levenshtein(a: string, b: string): number {
    const m = a.length
    const n = b.length
    if (m === 0) return n
    if (n === 0) return m
    let prev = Array.from({ length: n + 1 }, (_, j) => j)
    let curr = new Array<number>(n + 1).fill(0)
    for (let i = 1; i <= m; i++) {
        curr[0] = i
        for (let j = 1; j <= n; j++) {
            const cost = a[i - 1] === b[j - 1] ? 0 : 1
            curr[j] = Math.min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
        }
        const swap = prev
        prev = curr
        curr = swap
    }
    return prev[n]
}

/**
 * Conservatively canonicalizes an OCR'd stat-spark label to one of the five blue stats, or null when
 * the read is too distant to trust. Exact normalized match wins; otherwise a UNIQUE canonical within
 * edit distance 1 is accepted, and only for tokens >=4 chars (a 1-char slip on a short token like
 * "Wit" is too ambiguous). Unknowns are never forced into a stat name.
 */
export function canonicalizeStatName(raw: string): BlueStat | null {
    const norm = raw.toUpperCase().replace(/[^A-Z]/g, "")
    if (!norm) return null
    for (const s of BLUE_STATS) if (norm === s.toUpperCase()) return s
    if (norm.length >= 4) {
        let match: BlueStat | null = null
        let hits = 0
        for (const s of BLUE_STATS) {
            if (levenshtein(norm, s.toUpperCase()) <= 1) {
                match = s
                hits++
            }
        }
        if (hits === 1) return match
    }
    return null
}

/** Conservatively recognizes Long / Dirt aptitude labels (exact normalized match only); null otherwise. */
export function recognizeAptitude(raw: string): "Long" | "Dirt" | null {
    const norm = raw.toUpperCase().replace(/[^A-Z]/g, "")
    if (norm === "LONG") return "Long"
    if (norm === "DIRT") return "Dirt"
    return null
}

/** A row whose name OCR came back empty or the literal "unreadable" sentinel. */
function isUnreadableName(name: string): boolean {
    const norm = name.toUpperCase().replace(/[^A-Z]/g, "")
    return norm === "" || norm === "UNREADABLE"
}

/** A career outcome with the spark records positionally associated to it, split by phase. */
export interface CareerSparks {
    outcome: OutcomeRecord
    original: SparkRecord[]
    rerolled: SparkRecord[]
    kept: SparkRecord[]
    all: SparkRecord[]
}

export interface SparkJoin {
    /** Career outcomes that received >=1 spark record. */
    careers: CareerSparks[]
    /** Spark records attached to some career. */
    joinedCount: number
    /** Spark records with no preceding career in their file, or a hard trainee/fp mismatch. */
    unjoined: SparkRecord[]
    byOutcome: Map<OutcomeRecord, CareerSparks>
}

/**
 * Conservatively associates spark records with the career that produced them, using write order
 * WITHIN each file only:
 *   - a career outcome opens a group; subsequent sparks belong to it until the next career in that file;
 *   - when both records name a trainee, they must match (normalized), else the spark stays unjoined;
 *   - a direct fp on a spark VALIDATES the positional match: a mismatch leaves the spark unjoined,
 *     never re-routed to another career (scenario rides along for reporting; it is not a join key);
 *   - never joins across files; a spark with no preceding career is left unjoined and counted.
 * Only JSONL outcomes (those carrying a lineNumber) can anchor sparks.
 */
export function joinSparks(outcomes: OutcomeRecord[], sparks: SparkRecord[]): SparkJoin {
    const byOutcome = new Map<OutcomeRecord, CareerSparks>()
    const unjoined: SparkRecord[] = []
    let joinedCount = 0

    const files = new Set<string>()
    for (const o of outcomes) if (o.lineNumber !== undefined) files.add(o.file ?? "")
    for (const s of sparks) files.add(s.file ?? "")

    for (const file of files) {
        const timeline: { line: number; outcome?: OutcomeRecord; spark?: SparkRecord }[] = []
        for (const o of outcomes) if (o.lineNumber !== undefined && (o.file ?? "") === file) timeline.push({ line: o.lineNumber, outcome: o })
        for (const s of sparks) if ((s.file ?? "") === file) timeline.push({ line: s.lineNumber, spark: s })
        // Stable order: line number, then careers before sparks written on the same line (defensive).
        timeline.sort((a, b) => a.line - b.line || (a.outcome ? 0 : 1) - (b.outcome ? 0 : 1))

        let current: OutcomeRecord | null = null
        for (const ev of timeline) {
            if (ev.outcome) {
                current = ev.outcome
                if (!byOutcome.has(current)) byOutcome.set(current, { outcome: current, original: [], rerolled: [], kept: [], all: [] })
                continue
            }
            const s = ev.spark as SparkRecord
            const traineeMismatch = s.trainee !== undefined && current !== null && s.trainee !== current.trainee
            const fpMismatch = s.fp !== undefined && current !== null && current.fp !== undefined && s.fp !== current.fp
            if (current === null || traineeMismatch || fpMismatch) {
                unjoined.push(s)
                continue
            }
            const bucket = byOutcome.get(current) as CareerSparks
            bucket.all.push(s)
            if (s.phase === "original") bucket.original.push(s)
            else if (s.phase === "rerolled") bucket.rerolled.push(s)
            else if (s.phase === "kept") bucket.kept.push(s)
            joinedCount++
        }
    }

    const careers = [...byOutcome.values()].filter((c) => c.all.length > 0)
    return { careers, joinedCount, unjoined, byOutcome }
}

// ---- Report shapes -------------------------------------------------------

export interface SparkCoverage {
    careerOutcomes: number
    sparkRecords: number
    original: number
    rerolled: number
    kept: number
    joined: number
    unjoined: number
    malformedRows: number
    careersWithKept: number
    keptCoveragePct: number
}

export interface StatConfirmed {
    best: number
    ones: number
    twos: number
    threes: number
    bestSource: string | null
    hasThree: boolean
}

export interface AptitudeConfirmed {
    best: number
    byStar: { 1: number; 2: number; 3: number }
    total: number
    bestSource: string | null
}

export interface StatBands {
    n: number
    below600: number
    mid: number
    atLeast1100: number
}

export interface ArmEligibility {
    trainee: string
    scenario: string
    arm: string
    n: number
    perStat: Record<BlueStat, StatBands>
    allFive600: number
    avgBelow600: number
}

export interface ArmYield {
    trainee: string
    scenario: string
    arm: string
    careerCount: number
    keptCount: number
    keptCoveragePct: number
    blue2Count: number
    blue2Rate: number
    blue3Count: number
    blue3Rate: number
    long2Count: number
    dirt2Count: number
    allFive600Rate: number
    lowN: boolean
}

export interface SparkFarmReport {
    coverage: SparkCoverage
    confirmedBlue: Record<BlueStat, StatConfirmed>
    missingThreeBlue: BlueStat[]
    unknownStatReads: number
    long: AptitudeConfirmed
    dirt: AptitudeConfirmed
    unreadableAptitudeRows: number
    eligibility: { perArm: ArmEligibility[]; overall: ArmEligibility }
    yield: ArmYield[]
    guidance: string[]
}

function armKeyOf(r: OutcomeRecord): { trainee: string; scenario: string; arm: string; key: string } {
    const arm = armOf(r)
    return { trainee: r.trainee, scenario: r.scenario, arm, key: [r.trainee, r.scenario, arm].join("  ") }
}

function emptyBands(): Record<BlueStat, StatBands> {
    return {
        Speed: { n: 0, below600: 0, mid: 0, atLeast1100: 0 },
        Stamina: { n: 0, below600: 0, mid: 0, atLeast1100: 0 },
        Power: { n: 0, below600: 0, mid: 0, atLeast1100: 0 },
        Guts: { n: 0, below600: 0, mid: 0, atLeast1100: 0 },
        Wit: { n: 0, below600: 0, mid: 0, atLeast1100: 0 },
    }
}

/** The final confirmed set for a career: the LAST phase="kept" record, or null if none was observed. */
function finalKept(c: CareerSparks): SparkRecord | null {
    return c.kept.length > 0 ? c.kept[c.kept.length - 1] : null
}

/**
 * Builds the Spark Farm report from career outcomes + spark records. Pure. Bot-fault outcomes are
 * excluded (they crash before the sparks screen); incomplete careers are excluded from stat bands,
 * matching the career report.
 */
export function analyzeSparkFarm(outcomes: OutcomeRecord[], sparks: SparkRecord[]): SparkFarmReport {
    const realOutcomes = outcomes.filter((o) => !isBotFault(o))
    const join = joinSparks(outcomes, sparks)

    // ---- §1 Coverage ----
    const original = sparks.filter((s) => s.phase === "original").length
    const rerolled = sparks.filter((s) => s.phase === "rerolled").length
    const kept = sparks.filter((s) => s.phase === "kept").length
    const malformedRows = sparks.reduce((sum, s) => sum + s.droppedRows, 0)
    const careersWithKept = join.careers.filter((c) => finalKept(c) !== null).length
    const coverage: SparkCoverage = {
        careerOutcomes: realOutcomes.length,
        sparkRecords: sparks.length,
        original,
        rerolled,
        kept,
        joined: join.joinedCount,
        unjoined: join.unjoined.length,
        malformedRows,
        careersWithKept,
        keptCoveragePct: realOutcomes.length === 0 ? 0 : (100 * careersWithKept) / realOutcomes.length,
    }

    // ---- §2 Confirmed kept blue sparks ----
    const confirmedBlue: Record<BlueStat, StatConfirmed> = {
        Speed: { best: 0, ones: 0, twos: 0, threes: 0, bestSource: null, hasThree: false },
        Stamina: { best: 0, ones: 0, twos: 0, threes: 0, bestSource: null, hasThree: false },
        Power: { best: 0, ones: 0, twos: 0, threes: 0, bestSource: null, hasThree: false },
        Guts: { best: 0, ones: 0, twos: 0, threes: 0, bestSource: null, hasThree: false },
        Wit: { best: 0, ones: 0, twos: 0, threes: 0, bestSource: null, hasThree: false },
    }
    let unknownStatReads = 0
    const long: AptitudeConfirmed = { best: 0, byStar: { 1: 0, 2: 0, 3: 0 }, total: 0, bestSource: null }
    const dirt: AptitudeConfirmed = { best: 0, byStar: { 1: 0, 2: 0, 3: 0 }, total: 0, bestSource: null }
    let unreadableAptitudeRows = 0

    for (const c of join.careers) {
        const set = finalKept(c)
        if (!set) continue
        const src = `${c.outcome.trainee} / ${c.outcome.scenario}`
        for (const row of set.rows) {
            if (row.kind === "stat") {
                const stat = canonicalizeStatName(row.name)
                if (!stat) {
                    unknownStatReads++
                    continue
                }
                const cb = confirmedBlue[stat]
                if (row.stars === 1) cb.ones++
                else if (row.stars === 2) cb.twos++
                else if (row.stars >= 3) {
                    cb.threes++
                    cb.hasThree = true
                }
                if (row.stars > cb.best) {
                    cb.best = row.stars
                    cb.bestSource = src
                }
            } else if (row.kind === "aptitude") {
                if (isUnreadableName(row.name)) {
                    unreadableAptitudeRows++
                    continue
                }
                const apt = recognizeAptitude(row.name)
                if (apt === "Long" || apt === "Dirt") {
                    const target = apt === "Long" ? long : dirt
                    const star = row.stars >= 3 ? 3 : row.stars === 2 ? 2 : row.stars === 1 ? 1 : 0
                    if (star >= 1) {
                        target.byStar[star as 1 | 2 | 3]++
                        target.total++
                    }
                    if (row.stars > target.best) {
                        target.best = row.stars
                        target.bestSource = src
                    }
                }
            }
        }
    }
    const missingThreeBlue = BLUE_STATS.filter((s) => !confirmedBlue[s].hasThree)

    // ---- §4 Final-stat eligibility (finished career outcomes only) ----
    const finished = realOutcomes.filter((o) => classifyBucket(o) !== "incomplete")
    const armMap = new Map<string, ArmEligibility>()
    const overall: ArmEligibility = { trainee: "*", scenario: "*", arm: "ALL", n: 0, perStat: emptyBands(), allFive600: 0, avgBelow600: 0 }
    let overallBelowSum = 0
    for (const o of finished) {
        const { trainee, scenario, arm, key } = armKeyOf(o)
        let e = armMap.get(key)
        if (!e) {
            e = { trainee, scenario, arm, n: 0, perStat: emptyBands(), allFive600: 0, avgBelow600: 0 }
            armMap.set(key, e)
        }
        e.n++
        overall.n++
        let belowThisCareer = 0
        let allAtLeast = true
        for (const stat of BLUE_STATS) {
            const v = o[STAT_FIELD[stat]]
            for (const bands of [e.perStat[stat], overall.perStat[stat]]) {
                bands.n++
                if (v < BLUE_3STAR_FLOOR) bands.below600++
                else if (v >= HIGH_STAT_BAND) bands.atLeast1100++
                else bands.mid++
            }
            if (v < BLUE_3STAR_FLOOR) {
                belowThisCareer++
                allAtLeast = false
            }
        }
        e.avgBelow600 += belowThisCareer
        overallBelowSum += belowThisCareer
        if (allAtLeast) {
            e.allFive600++
            overall.allFive600++
        }
    }
    for (const e of armMap.values()) e.avgBelow600 = e.n === 0 ? 0 : e.avgBelow600 / e.n
    overall.avgBelow600 = overall.n === 0 ? 0 : overallBelowSum / overall.n
    const perArmEligibility = [...armMap.values()].sort((a, b) => a.trainee.localeCompare(b.trainee) || a.scenario.localeCompare(b.scenario) || a.arm.localeCompare(b.arm))

    // ---- §5 Farming yield (arms with >=1 confirmed-kept set) ----
    const yieldMap = new Map<string, ArmYield>()
    for (const c of join.careers) {
        const set = finalKept(c)
        if (!set) continue
        const { trainee, scenario, arm, key } = armKeyOf(c.outcome)
        let y = yieldMap.get(key)
        if (!y) {
            y = { trainee, scenario, arm, careerCount: 0, keptCount: 0, keptCoveragePct: 0, blue2Count: 0, blue2Rate: 0, blue3Count: 0, blue3Rate: 0, long2Count: 0, dirt2Count: 0, allFive600Rate: 0, lowN: true }
            yieldMap.set(key, y)
        }
        y.keptCount++
        const blueStars = Math.max(0, ...set.rows.filter((r) => r.kind === "stat").map((r) => r.stars), 0)
        if (blueStars >= 2) y.blue2Count++
        if (blueStars >= 3) y.blue3Count++
        if (set.rows.some((r) => r.kind === "aptitude" && recognizeAptitude(r.name) === "Long" && r.stars >= 2)) y.long2Count++
        if (set.rows.some((r) => r.kind === "aptitude" && recognizeAptitude(r.name) === "Dirt" && r.stars >= 2)) y.dirt2Count++
    }
    const yieldRows: ArmYield[] = []
    for (const y of yieldMap.values()) {
        const e = armMap.get([y.trainee, y.scenario, y.arm].join("  "))
        y.careerCount = e ? e.n : y.keptCount
        y.keptCoveragePct = y.careerCount === 0 ? 0 : (100 * y.keptCount) / y.careerCount
        y.blue2Rate = y.keptCount === 0 ? 0 : y.blue2Count / y.keptCount
        y.blue3Rate = y.keptCount === 0 ? 0 : y.blue3Count / y.keptCount
        y.allFive600Rate = e && e.n > 0 ? e.allFive600 / e.n : 0
        y.lowN = y.keptCount < LOW_N_THRESHOLD
        yieldRows.push(y)
    }
    yieldRows.sort(
        (a, b) =>
            Number(a.lowN) - Number(b.lowN) ||
            b.blue3Rate - a.blue3Rate ||
            b.blue2Rate - a.blue2Rate ||
            b.allFive600Rate - a.allFive600Rate ||
            a.trainee.localeCompare(b.trainee) ||
            a.scenario.localeCompare(b.scenario) ||
            a.arm.localeCompare(b.arm),
    )

    // ---- §6 Next-farm guidance (deterministic, conservative) ----
    const guidance: string[] = []
    if (coverage.kept === 0) {
        guidance.push("Insufficient data: no confirmed-kept (phase=\"kept\") spark records in the corpus yet — run more careers to build farm signal.")
    } else {
        if (missingThreeBlue.length > 0) guidance.push(`No confirmed 3-star blue spark in the kept corpus for: ${missingThreeBlue.join(", ")}. These stat categories are unproven, not necessarily missing from the account.`)
        else guidance.push("Every blue stat has at least one confirmed 3-star in the kept corpus.")

        const sufficient = yieldRows.filter((y) => !y.lowN)
        if (sufficient.length > 0) {
            const byAllFive = [...sufficient].sort((a, b) => b.allFive600Rate - a.allFive600Rate || a.trainee.localeCompare(b.trainee))
            const top = byAllFive[0]
            guidance.push(`Most reliable all-five-≥600 arm with a sufficient sample: ${top.trainee} / ${top.scenario} (${(100 * top.allFive600Rate).toFixed(0)}% of ${top.careerCount} finished careers).`)
        } else {
            guidance.push(`No arm has a sufficient confirmed-kept sample (>=${LOW_N_THRESHOLD}) yet — treat all yield rows as anecdote.`)
        }

        // Arms that repeatedly leave a specific stat under 600 (sufficient finished sample only).
        for (const e of perArmEligibility) {
            if (e.n < LOW_N_THRESHOLD) continue
            for (const stat of BLUE_STATS) {
                const pct = e.perStat[stat].n === 0 ? 0 : (100 * e.perStat[stat].below600) / e.perStat[stat].n
                if (pct >= 50) guidance.push(`${e.trainee} / ${e.scenario} leaves ${stat} under 600 in ${pct.toFixed(0)}% of finished careers — that stat cannot 3-star-blue there.`)
            }
        }

        if (long.total === 0) guidance.push("No confirmed Long aptitude spark in the kept corpus.")
        if (dirt.total === 0) guidance.push("No confirmed Dirt aptitude spark in the kept corpus.")
    }

    return {
        coverage,
        confirmedBlue,
        missingThreeBlue,
        unknownStatReads,
        long,
        dirt,
        unreadableAptitudeRows,
        eligibility: { perArm: perArmEligibility, overall },
        yield: yieldRows,
        guidance,
    }
}

function pct(part: number, whole: number): string {
    if (whole === 0) return "-"
    return `${((100 * part) / whole).toFixed(0)}%`
}

/** Renders the Spark Farm report as GitHub-flavored markdown (sections 1-6 plus caveats). */
export function renderSparkFarmMarkdown(r: SparkFarmReport): string {
    const L: string[] = []
    L.push("# Spark Farm Analyzer")
    L.push("")
    L.push("_Confirmed-kept = the final `phase=\"kept\"` set (authoritative). Original/rerolled are observed-only. \"Missing\" means missing from the confirmed-kept corpus records, not from the live account._")

    // §1
    L.push("")
    L.push("## 1. Corpus Coverage")
    const c = r.coverage
    L.push(`- Career outcomes: **${c.careerOutcomes}**`)
    L.push(`- Spark records: **${c.sparkRecords}** (original ${c.original}, rerolled ${c.rerolled}, confirmed-kept ${c.kept})`)
    L.push(`- Joined spark records: **${c.joined}** · Unjoined: **${c.unjoined}** · Malformed rows dropped: ${c.malformedRows}`)
    L.push(`- Careers with a confirmed-kept set: **${c.careersWithKept}** of ${c.careerOutcomes} (${c.keptCoveragePct.toFixed(0)}% confirmed-kept coverage)`)

    // §2
    L.push("")
    L.push("## 2. Confirmed Kept Blue Sparks")
    L.push("| Stat | Best | 1★ | 2★ | 3★ | Confirmed 3★? | Best source |")
    L.push("|---|---:|---:|---:|---:|:--:|---|")
    for (const stat of BLUE_STATS) {
        const b = r.confirmedBlue[stat]
        L.push(`| ${stat} | ${b.best || "-"} | ${b.ones} | ${b.twos} | ${b.threes} | ${b.hasThree ? "yes" : "**no**"} | ${b.bestSource ?? "-"} |`)
    }
    if (r.unknownStatReads > 0) L.push(`\nUnresolved stat-name reads (kept, not counted above): ${r.unknownStatReads}.`)
    L.push("")
    L.push(`**Missing from confirmed-kept corpus records (no confirmed 3★ blue):** ${r.missingThreeBlue.length ? r.missingThreeBlue.join(", ") : "none"}`)

    // §3
    L.push("")
    L.push("## 3. Long and Dirt Aptitude Sparks (confirmed-kept)")
    L.push("| Aptitude | Best | 1★ | 2★ | 3★ | Best source |")
    L.push("|---|---:|---:|---:|---:|---|")
    for (const [label, a] of [["Long", r.long] as const, ["Dirt", r.dirt] as const]) {
        L.push(`| ${label} | ${a.best || "-"} | ${a.byStar[1]} | ${a.byStar[2]} | ${a.byStar[3]} | ${a.bestSource ?? "-"} |`)
    }
    L.push(`\nUnreadable/unrecognized aptitude rows in kept sets: ${r.unreadableAptitudeRows}.`)

    // §4
    L.push("")
    L.push("## 4. Final-Stat Eligibility (finished careers)")
    L.push("Overall final-stat bands (share of finished careers):")
    L.push("| Stat | <600 | 600-1099 | 1100+ |")
    L.push("|---|---:|---:|---:|")
    const ov = r.eligibility.overall
    for (const stat of BLUE_STATS) {
        const s = ov.perStat[stat]
        L.push(`| ${stat} | ${pct(s.below600, s.n)} | ${pct(s.mid, s.n)} | ${pct(s.atLeast1100, s.n)} |`)
    }
    L.push("")
    L.push(`All five stats ≥600: **${pct(ov.allFive600, ov.n)}** of ${ov.n} finished careers · avg stats under 600 per finished career: **${ov.avgBelow600.toFixed(2)}**`)
    L.push("")
    L.push("Per arm — share of finished careers leaving each stat under 600 (the 3★-blue floor):")
    L.push("| Trainee | Scenario | Arm | N | Spd<600 | Sta<600 | Pwr<600 | Guts<600 | Wit<600 | All5≥600 |")
    L.push("|---|---|---|---:|---:|---:|---:|---:|---:|---:|")
    for (const e of r.eligibility.perArm) {
        const cell = (stat: BlueStat) => pct(e.perStat[stat].below600, e.perStat[stat].n)
        const nCell = e.n < LOW_N_THRESHOLD ? `${e.n}*` : `${e.n}`
        L.push(`| ${e.trainee} | ${e.scenario} | ${e.arm} | ${nCell} | ${cell("Speed")} | ${cell("Stamina")} | ${cell("Power")} | ${cell("Guts")} | ${cell("Wit")} | ${pct(e.allFive600, e.n)} |`)
    }

    // §5
    L.push("")
    L.push("## 5. Farming Yield by Trainee / Scenario / Arm")
    L.push("| Trainee | Scenario | Arm | Careers | Kept | Cov | 2★+ blue | 3★ blue | Long 2★+ | Dirt 2★+ | All5≥600 |")
    L.push("|---|---|---|---:|---:|---:|---|---|---:|---:|---:|")
    for (const y of r.yield) {
        const keptCell = y.lowN ? `${y.keptCount}*` : `${y.keptCount}`
        const blue2 = `${y.blue2Count} (${(100 * y.blue2Rate).toFixed(0)}%)`
        const blue3 = `${y.blue3Count} (${(100 * y.blue3Rate).toFixed(0)}%)`
        L.push(`| ${y.trainee} | ${y.scenario} | ${y.arm} | ${y.careerCount} | ${keptCell} | ${y.keptCoveragePct.toFixed(0)}% | ${blue2} | ${blue3} | ${y.long2Count} | ${y.dirt2Count} | ${(100 * y.allFive600Rate).toFixed(0)}% |`)
    }
    if (r.yield.length === 0) L.push("_No arm has a confirmed-kept set yet._")
    L.push("")
    L.push(`\\* fewer than ${LOW_N_THRESHOLD} confirmed-kept sets: anecdote, not signal. Yield rows sort sufficient-sample first, then 3★ blue rate.`)

    // §6
    L.push("")
    L.push("## 6. Next-Farm Guidance")
    for (const g of r.guidance) L.push(`- ${g}`)

    return L.join("\n")
}

// ===========================================================================
// Skill Spend — read-side over the `type:"skill_spend"` corpus records.
//
// Descriptive only: counts and point summaries of what the purchaser actually did. It deliberately
// draws no conclusion about whether a policy is good — that needs far more sessions than the first
// corpora will hold, and the records exist to make that judgement possible later, not now.
// ===========================================================================

export interface SkillSpendSummary {
    sessions: number
    byTrigger: Record<string, number>
    byPlan: Record<string, number>
    byOutcome: Record<string, number>
    /** Per resolved threshold-policy tier ("manual", "developing", ...). trigger-v1 records land
     * under "pre-v2" — their governing policy is unknown, never inferred. */
    byTier: Record<string, number>
    /** Sessions whose commit was verified on screen. */
    committed: number
    /** Skills evidenced as bought across all sessions. */
    confirmedSkills: number
    /** Skills the planner proposed across all sessions. */
    proposedSkills: number
    /** Sessions whose points delta proved the on-screen confirmation missed at least one purchase. */
    confirmedIncomplete: number
    spBefore: { n: number; p50: number; min: number; max: number }
    spAfter: { n: number; p50: number; min: number; max: number }
    unspent: { n: number; p50: number; min: number; max: number }
    /** Per (trainee, scenario, arm) counts — only for records carrying that identity. */
    byArm: { trainee: string; scenario: string; arm: string; sessions: number; committed: number; unspentP50: number }[]
    /** Records lacking the identity needed to place them on an arm. */
    unidentified: number
}

function tally(values: number[]): { n: number; p50: number; min: number; max: number } {
    if (values.length === 0) return { n: 0, p50: 0, min: 0, max: 0 }
    return { n: values.length, p50: percentile(values, 50), min: Math.min(...values), max: Math.max(...values) }
}

function bump(map: Record<string, number>, key: string): void {
    map[key] = (map[key] ?? 0) + 1
}

/**
 * Aggregates skill-spend sessions. Pure. An arm needs app+fp to exist; records written before a
 * fingerprint was available (or by a future writer that omits it) are counted as unidentified rather
 * than lumped onto a wrong arm.
 */
export function analyzeSkillSpend(records: SkillSpendRecord[]): SkillSpendSummary {
    const byTrigger: Record<string, number> = {}
    const byPlan: Record<string, number> = {}
    const byOutcome: Record<string, number> = {}
    const byTier: Record<string, number> = {}
    const armMap = new Map<string, { trainee: string; scenario: string; arm: string; sessions: number; committed: number; unspents: number[] }>()
    let unidentified = 0
    let committed = 0
    let confirmedSkills = 0
    let proposedSkills = 0
    let confirmedIncomplete = 0

    for (const r of records) {
        bump(byTrigger, r.trigger ?? "unknown")
        bump(byPlan, r.plan ?? "unknown")
        bump(byOutcome, r.outcome)
        bump(byTier, r.tier ?? "pre-v2")
        if (r.outcome === "committed") committed++
        if (r.confirmedIncomplete) confirmedIncomplete++
        confirmedSkills += r.confirmed.length
        proposedSkills += r.proposed.length

        if (r.trainee && r.scenario && r.fp) {
            const key = [r.trainee, r.scenario, r.fp].join("  ")
            let e = armMap.get(key)
            if (!e) {
                e = { trainee: r.trainee, scenario: r.scenario, arm: r.fp, sessions: 0, committed: 0, unspents: [] }
                armMap.set(key, e)
            }
            e.sessions++
            if (r.outcome === "committed") e.committed++
            if (r.unspent !== undefined) e.unspents.push(r.unspent)
        } else {
            unidentified++
        }
    }

    const byArm = [...armMap.values()]
        .map((e) => ({ trainee: e.trainee, scenario: e.scenario, arm: e.arm, sessions: e.sessions, committed: e.committed, unspentP50: percentile(e.unspents, 50) }))
        .sort((a, b) => a.trainee.localeCompare(b.trainee) || a.scenario.localeCompare(b.scenario) || a.arm.localeCompare(b.arm))

    return {
        sessions: records.length,
        byTrigger,
        byPlan,
        byOutcome,
        byTier,
        committed,
        confirmedSkills,
        proposedSkills,
        confirmedIncomplete,
        spBefore: tally(records.map((r) => r.spBefore).filter((v): v is number => v !== undefined)),
        spAfter: tally(records.map((r) => r.spAfter).filter((v): v is number => v !== undefined)),
        unspent: tally(records.map((r) => r.unspent).filter((v): v is number => v !== undefined)),
        byArm,
        unidentified,
    }
}

/** Renders the Skill Spend summary as GitHub-flavored markdown. */
export function renderSkillSpendMarkdown(s: SkillSpendSummary): string {
    const L: string[] = []
    L.push("# Skill Spend")
    L.push("")
    if (s.sessions === 0) {
        L.push("_No `skill_spend` records in the corpus yet._")
        return L.join("\n")
    }
    L.push(`- Sessions: **${s.sessions}** (one per purchaser invocation, not per scroll pass) · committed: **${s.committed}**`)
    L.push(`- Skills proposed: ${s.proposedSkills} · evidenced as bought: ${s.confirmedSkills}`)
    if (s.confirmedIncomplete > 0) {
        L.push(
            `- Sessions whose points delta proves the on-screen confirmation missed a purchase: **${s.confirmedIncomplete}** ` +
                `(their \`confirmed\` is a floor, so the bought count above is understated)`,
        )
    }
    if (s.unidentified > 0) L.push(`- Records without trainee/scenario/fp identity: ${s.unidentified} (excluded from the arm table)`)
    const row = (label: string, t: { n: number; p50: number; min: number; max: number }) => `| ${label} | ${t.n} | ${t.p50} | ${t.min} | ${t.max} |`
    L.push("")
    L.push("| Points | N | p50 | min | max |")
    L.push("|---|---:|---:|---:|---:|")
    L.push(row("SP before", s.spBefore))
    L.push(row("SP after", s.spAfter))
    L.push(row("Unspent", s.unspent))
    const counts = (label: string, m: Record<string, number>) => {
        const entries = Object.entries(m).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
        L.push("")
        L.push(`${label}: ${entries.map(([k, v]) => `${k}=${v}`).join(" · ")}`)
    }
    counts("By trigger", s.byTrigger)
    counts("By plan", s.byPlan)
    counts("By outcome", s.byOutcome)
    counts("By threshold-policy tier", s.byTier)
    if (s.byArm.length > 0) {
        L.push("")
        L.push("| Trainee | Scenario | Arm | Sessions | Committed | Unspent p50 |")
        L.push("|---|---|---|---:|---:|---:|")
        for (const a of s.byArm) L.push(`| ${a.trainee} | ${a.scenario} | ${a.arm} | ${a.sessions} | ${a.committed} | ${a.unspentP50} |`)
    }
    L.push("")
    L.push("\\* Descriptive only. These counts say what the purchaser did, not whether the policy is good — that needs many more sessions than a first corpus holds.")
    return L.join("\n")
}
