import type { Settings } from "../context/BotStateContext"

/**
 * The Start persistence barrier.
 *
 * Why this exists: the Home preset apply updates React state immediately (the preset row
 * shows the new trainee at once) but the SQLite write is asynchronous, error-swallowing, and
 * shares a single serialized write queue that can stall after a bot-service session. On
 * 2026-07-20 a Super Creek apply's row write did not land for ~2 minutes; Start read the
 * still-stale Mejiro McQueen values, launched her, and the delayed write then flipped the
 * live-read objective to `sparks` mid-career. A resolved `saveSettings` promise proved only
 * that a write was submitted without throwing, never that the intended values reached disk.
 *
 * The barrier closes that gap: it flushes the pending write, reads the persisted rows back in
 * ONE atomic snapshot, and compares a compact identity against what the UI intends. Start
 * launches only on an exact match; any mismatch, stall, or read failure blocks the launch
 * visibly and keeps Start retryable.
 *
 * Everything here is pure and dependency-injected so the whole barrier is unit-testable with
 * fakes and fake timers -- no React, no SQLite, no wall clock.
 */

/**
 * The categories whose rows are launch-critical: everything the bot reads at run time to
 * decide WHO runs and HOW -- trainee identity and scenario (general), the full training
 * configuration, training-event choices, skill spending, racing, and the queue/rotation/
 * spark-reroll/restore/boost switches. Coverage is by CATEGORY, not by a hand-kept key list,
 * so a new field added to any of these categories is covered automatically and can never
 * slip past the barrier unhashed.
 *
 * Deliberately excluded: `debug` and `discord` (device/user-level, never run identity),
 * `misc` (UI bookkeeping), `queueState` and `rot*` (Kotlin-owned live state, never
 * serialized by the RN side -- see convertSettingsToBatch).
 */
export const LAUNCH_CRITICAL_CATEGORIES: string[] = ["general", "training", "trainingEvent", "skills", "racing", "runQueue"]

/**
 * Keys inside launch-critical categories that are still excluded from the identity:
 * - `general.settingsRevision` is the revision itself (carried separately, not hashed);
 * - `racing.racingPlanData` is the picker's large derived dataset, not run configuration
 *   (the actual plan is `racing.racingPlan`, which IS covered).
 */
export const LAUNCH_IDENTITY_EXCLUDED_KEYS: string[] = ["general.settingsRevision", "racing.racingPlanData"]

/** The row that carries the preset-apply revision (see [bumpSettingsRevision]). */
export const SETTINGS_REVISION_FIELD = { category: "general", key: "settingsRevision" }

/** A compact, comparable summary of one launch configuration. */
export interface LaunchConfigIdentity {
    /**
     * Preset-apply revision: a monotonic nonce bumped on every preset apply. It is NOT a
     * global settings version -- manual single-field edits do not advance it; content
     * equality is the hash's job. Its role is staleness ordering between preset applies
     * (the incident's failure mode) and cheap cross-layer correlation with Kotlin.
     */
    revision: number
    /** The launched trainee ("[Outfit] Name"); surfaced for logs and human-readable mismatch. */
    trainee: string
    scenario: string
    objective: string
    mode: string
    tier: string
    /** Stable hash over every launch-critical row, in storage form (see [storageForm]). */
    hash: string
}

/** Deterministic 32-bit FNV-1a over a string, rendered as 8 hex chars. No dependencies. */
export function stableHash(input: string): string {
    let h = 0x811c9dc5
    for (let i = 0; i < input.length; i++) {
        h ^= input.charCodeAt(i)
        // FNV prime 16777619, kept in 32-bit range via Math.imul.
        h = Math.imul(h, 0x01000193)
    }
    // Unsigned, fixed width.
    return (h >>> 0).toString(16).padStart(8, "0")
}

/**
 * Canonicalize a value into exactly the string the settings store persists: strings raw,
 * everything else JSON. This is the load-bearing normalization -- the read-back side hands
 * back the RAW stored strings, while the intended side holds live JS values (which may even
 * be a parsed object where a JSON string was written, e.g. `appliedRacingSnapshot` after an
 * app restart re-loaded it through JSON.parse). Serializing both sides through the same rule
 * makes the comparison round-trip-exact; comparing parsed values would false-mismatch on
 * every string-that-looks-like-JSON and brick Start.
 */
export function storageForm(value: any): string {
    if (value === null || value === undefined) return ""
    return typeof value === "string" ? value : JSON.stringify(value)
}

/**
 * Build the identity from a flat row map keyed "category.key". Row values may be raw stored
 * strings (the read-back path) or live JS values (the intended path) -- [storageForm]
 * normalizes both. Rows are filtered to the launch-critical categories, the excluded keys
 * are dropped, and the hash input is sorted so enumeration order can never matter.
 */
export function identityFromRows(rows: Record<string, any>): LaunchConfigIdentity {
    const revisionRaw = rows[`${SETTINGS_REVISION_FIELD.category}.${SETTINGS_REVISION_FIELD.key}`]
    const revisionNum = Number(revisionRaw)
    const revision = Number.isFinite(revisionNum) && revisionNum >= 0 ? Math.floor(revisionNum) : 0

    const hashLines: string[] = []
    for (const rowKey of Object.keys(rows)) {
        const category = rowKey.split(".")[0]
        if (!LAUNCH_CRITICAL_CATEGORIES.includes(category)) continue
        if (LAUNCH_IDENTITY_EXCLUDED_KEYS.includes(rowKey)) continue
        hashLines.push(`${rowKey}=${storageForm(rows[rowKey])}`)
    }
    hashLines.sort()

    const str = (rowKey: string) => storageForm(rows[rowKey])
    return {
        revision,
        trainee: str("general.appliedPresetTrainee"),
        scenario: str("general.scenario"),
        objective: str("skills.skillSpendObjective"),
        mode: str("skills.skillSpendMode"),
        tier: str("skills.accountTier"),
        hash: stableHash(hashLines.join("\n")),
    }
}

/** Flatten the in-memory Settings object into the "category.key" row map [identityFromRows] eats. */
export function rowsFromSettingsObject(settings: Settings): Record<string, any> {
    const rows: Record<string, any> = {}
    for (const category of LAUNCH_CRITICAL_CATEGORIES) {
        const block = (settings as any)?.[category]
        if (!block || typeof block !== "object") continue
        for (const key of Object.keys(block)) {
            rows[`${category}.${key}`] = block[key]
        }
    }
    return rows
}

/** Build the identity from the in-memory Settings object (what the UI currently intends). */
export function launchConfigIdentity(settings: Settings): LaunchConfigIdentity {
    return identityFromRows(rowsFromSettingsObject(settings))
}

/** Immutably increment the preset-apply revision. Called on every preset apply. */
export function bumpSettingsRevision(settings: Settings): Settings {
    const current = Number((settings as any)?.general?.settingsRevision)
    const base = Number.isFinite(current) && current >= 0 ? Math.floor(current) : 0
    return {
        ...settings,
        general: { ...settings.general, settingsRevision: base + 1 },
    }
}

/** Whether two identities are the same launch. Revision first (the staleness signal), then the
 * human-legible fields, then the content hash (catches every field the summary omits). */
export function launchConfigIdentitiesMatch(intended: LaunchConfigIdentity, persisted: LaunchConfigIdentity): { ok: boolean; reason?: string } {
    if (intended.revision !== persisted.revision) {
        return { ok: false, reason: `revision ${persisted.revision} on disk, expected ${intended.revision}` }
    }
    if (intended.trainee !== persisted.trainee) {
        return { ok: false, reason: `trainee "${persisted.trainee}" on disk, expected "${intended.trainee}"` }
    }
    if (intended.scenario !== persisted.scenario) {
        return { ok: false, reason: `scenario "${persisted.scenario}" on disk, expected "${intended.scenario}"` }
    }
    if (intended.objective !== persisted.objective) {
        return { ok: false, reason: `objective "${persisted.objective}" on disk, expected "${intended.objective}"` }
    }
    if (intended.hash !== persisted.hash) {
        return { ok: false, reason: `config hash ${persisted.hash} on disk, expected ${intended.hash}` }
    }
    return { ok: true }
}

/** Where a blocked barrier stopped, for structured diagnostics. */
export type LaunchBarrierStage = "flush" | "readback" | "verify"

export interface LaunchBarrierResult {
    ok: boolean
    stage?: LaunchBarrierStage
    reason?: string
    intended: LaunchConfigIdentity
    persisted: LaunchConfigIdentity | null
}

export interface LaunchBarrierDeps {
    /** What the UI intends to launch. */
    intended: LaunchConfigIdentity
    /** Force the pending settings write to run to completion. May stall or throw. */
    flush: () => Promise<void>
    /**
     * Read the persisted rows back as ONE atomic snapshot (a single SQLite statement) and
     * build their identity. Must not read row-by-row: independent reads could interleave
     * with a landing commit and assemble a mixed identity that matches nothing real.
     */
    readback: () => Promise<LaunchConfigIdentity>
    /** Fail-closed cleanup when the flush stalls (reject queued writes; never force-reset
     * an in-flight transaction). Optional. */
    recover?: () => Promise<void>
    /** Flush stall ceiling in ms. */
    timeoutMs: number
    /** Injected timer scheduler (real setTimeout in production, fake in tests). */
    schedule: (fn: () => void, ms: number) => any
    /** Injected timer canceller. */
    cancel: (timer: any) => void
}

/** Race a promise against an injected timer; rejects with a stall marker on timeout. */
function withTimeout<T>(work: Promise<T>, timeoutMs: number, schedule: (fn: () => void, ms: number) => any, cancel: (t: any) => void): Promise<T> {
    return new Promise<T>((resolve, reject) => {
        let settled = false
        const timer = schedule(() => {
            if (settled) return
            settled = true
            reject(new Error("__launch_flush_timeout__"))
        }, timeoutMs)
        work.then(
            (v) => {
                if (settled) return
                settled = true
                cancel(timer)
                resolve(v)
            },
            (e) => {
                if (settled) return
                settled = true
                cancel(timer)
                reject(e)
            }
        )
    })
}

/**
 * The Start barrier. Flush -> atomic read-back -> verify identity. Returns ok only when the
 * intended configuration is provably on disk. A stall or throw during flush triggers the
 * fail-closed cleanup and blocks; a read-back that does not match the intended identity
 * blocks. The caller launches BotService only on `ok === true`.
 */
export async function verifyLaunchConfigPersisted(deps: LaunchBarrierDeps): Promise<LaunchBarrierResult> {
    const { intended } = deps

    // 1. Flush the pending write, bounded by the stall ceiling.
    try {
        await withTimeout(deps.flush(), deps.timeoutMs, deps.schedule, deps.cancel)
    } catch (error) {
        const stalled = error instanceof Error && error.message === "__launch_flush_timeout__"
        if (deps.recover) {
            try {
                await deps.recover()
            } catch {
                // Cleanup is best-effort; the block below stands regardless.
            }
        }
        return {
            ok: false,
            stage: "flush",
            reason: stalled ? "saving the preset did not finish in time (the settings writer stalled)" : `saving the preset failed: ${error instanceof Error ? error.message : String(error)}`,
            intended,
            persisted: null,
        }
    }

    // 2. Read the persisted rows back as one atomic snapshot.
    let persisted: LaunchConfigIdentity
    try {
        persisted = await deps.readback()
    } catch (error) {
        return {
            ok: false,
            stage: "readback",
            reason: `could not read the saved preset back: ${error instanceof Error ? error.message : String(error)}`,
            intended,
            persisted: null,
        }
    }

    // 3. Verify the persisted identity matches what the UI intends.
    const match = launchConfigIdentitiesMatch(intended, persisted)
    if (!match.ok) {
        return { ok: false, stage: "verify", reason: match.reason, intended, persisted }
    }
    return { ok: true, intended, persisted }
}

/**
 * Orchestrate a gated Start: run the barrier, launch only on success, report a block otherwise.
 * Kept separate from React so "launches exactly once, only when verified" is directly testable.
 */
export async function runStartBarrier(opts: {
    verify: () => Promise<LaunchBarrierResult>
    launch: () => void | Promise<void>
    onBlocked: (result: LaunchBarrierResult) => void
}): Promise<LaunchBarrierResult> {
    const result = await opts.verify()
    if (result.ok) {
        await opts.launch()
    } else {
        opts.onBlocked(result)
    }
    return result
}

/**
 * A single-flight gate for the Start button: at most one barrier+launch sequence in flight,
 * re-entrant presses are rejected until it settles, and a cancelled gate (Stop pressed,
 * screen unmounted, preset changed) refuses the launch even if the in-flight barrier later
 * verifies. Pure so double-press, cancel-during-await, and unmount are all unit-testable.
 */
export function createSingleFlight() {
    let inFlight = false
    let cancelled = false
    return {
        /** Try to enter the gate. False = something is already in flight; do nothing. */
        begin(): boolean {
            if (inFlight) return false
            inFlight = true
            cancelled = false
            return true
        },
        /** Abandon the in-flight sequence: a later mayLaunch() refuses. */
        cancel() {
            cancelled = true
        },
        /** Whether the sequence that called begin() is still allowed to launch. */
        mayLaunch(): boolean {
            return inFlight && !cancelled
        },
        /** Leave the gate (always call, success or failure). */
        end() {
            inFlight = false
            cancelled = false
        },
        get busy(): boolean {
            return inFlight
        },
    }
}
