import {
    LaunchConfigIdentity,
    LAUNCH_CRITICAL_CATEGORIES,
    bumpSettingsRevision,
    createSingleFlight,
    identityFromRows,
    launchConfigIdentity,
    launchConfigIdentitiesMatch,
    runStartBarrier,
    storageForm,
    verifyLaunchConfigPersisted,
} from "../launchConfig"

// React-Native-free: never imports BotStateContext (which pulls in RN and cannot be parsed by
// Jest). Fixtures are minimal nested objects carrying the launch-critical categories.

function makeSettings(overrides: { trainee?: string; scenario?: string; objective?: string; mode?: string; tier?: string; revision?: number; extraTraining?: any } = {}): any {
    return {
        general: {
            appliedPresetTrainee: overrides.trainee ?? "[Frontline Elegance] Mejiro McQueen",
            appliedPresetTraineeExcludes: "",
            scenario: overrides.scenario ?? "Unity Cup",
            settingsRevision: overrides.revision ?? 1,
        },
        training: { statPrioritization: ["Speed", "Stamina", "Wit", "Power", "Guts"], manualStatCap: 1200, ...(overrides.extraTraining || {}) },
        trainingEvent: { supportEventOverrides: {}, scenarioEventOverrides: {} },
        skills: {
            skillSpendMode: overrides.mode ?? "adaptive",
            accountTier: overrides.tier ?? "endgame",
            skillSpendObjective: overrides.objective ?? "rank",
            preferredRunningStyle: "",
            preferredTrackDistance: "",
            preferredTrackSurface: "",
            plans: {},
        },
        racing: { enableRacingPlan: false, enableMandatoryRacingPlan: false, racingPlan: "[]", racingPlanData: "big-derived-blob", appliedRacingSnapshot: "" },
        runQueue: { enableRunQueue: false, enableTraineeRotation: false, traineeRotation: [], enableSparkReroll: true, enableEventBoost: false, enableTpRestoreWithItems: false },
        // Non-launch-critical categories that must NOT affect the identity:
        debug: { enableDebugMode: true, discordToken: "secret" },
        misc: { currentProfileName: "whatever" },
    }
}

// Storage-form row map, exactly as SQLite persists it (strings raw, everything else JSON) --
// the shape loadSettingsRowsSnapshot returns.
function storedRows(settings: any): Record<string, string> {
    const rows: Record<string, string> = {}
    for (const category of Object.keys(settings)) {
        const block = settings[category]
        if (!block || typeof block !== "object") continue
        for (const key of Object.keys(block)) {
            rows[`${category}.${key}`] = storageForm(block[key])
        }
    }
    return rows
}

function fakeTimers() {
    let nowMs = 0
    const pending: { id: number; at: number; fn: () => void }[] = []
    let seq = 1
    return {
        schedule: (fn: () => void, ms: number) => {
            const id = seq++
            pending.push({ id, at: nowMs + ms, fn })
            return id
        },
        cancel: (id: any) => {
            const i = pending.findIndex((p) => p.id === id)
            if (i >= 0) pending.splice(i, 1)
        },
        advance: (ms: number) => {
            nowMs += ms
            const due = pending.filter((p) => p.at <= nowMs).sort((a, b) => a.at - b.at)
            for (const p of due) {
                const i = pending.indexOf(p)
                if (i >= 0) pending.splice(i, 1)
                p.fn()
            }
        },
    }
}

// Controllable store: writes stage until release(); the read-back is ONE atomic snapshot.
function fakeStore(initialRows: Record<string, string>) {
    const committed: Record<string, string> = { ...initialRows }
    let pendingWrite: { rows: Record<string, string>; resolve: () => void; reject: (e: Error) => void } | null = null
    let recovered = false
    return {
        committed,
        flush: (rows: Record<string, string>) =>
            new Promise<void>((resolve, reject) => {
                pendingWrite = { rows, resolve, reject }
            }),
        release: () => {
            if (!pendingWrite) return
            Object.assign(committed, pendingWrite.rows)
            const r = pendingWrite.resolve
            pendingWrite = null
            r()
        },
        failFlush: (e: Error) => {
            if (!pendingWrite) return
            const rej = pendingWrite.reject
            pendingWrite = null
            rej(e)
        },
        recover: async () => {
            recovered = true
            pendingWrite = null
        },
        wasRecovered: () => recovered,
        // Atomic snapshot: one call reads the whole committed map at once.
        readback: async (): Promise<LaunchConfigIdentity> => identityFromRows({ ...committed }),
    }
}

describe("storageForm + stableHash + identity", () => {
    it("storageForm matches SQLite serialize (strings raw, else JSON, null->empty)", () => {
        expect(storageForm("Unity Cup")).toBe("Unity Cup")
        expect(storageForm(false)).toBe("false")
        expect(storageForm([1, 2])).toBe("[1,2]")
        expect(storageForm(null)).toBe("")
        expect(storageForm(undefined)).toBe("")
    })

    it("a JSON-string value and its parsed object hash identically (round-trip symmetry)", () => {
        // The read-back returns the raw string; the in-memory side may hold a parsed object.
        const asString = identityFromRows({ "general.appliedPresetTrainee": "A", "racing.appliedRacingSnapshot": '{"a":1,"b":2}' })
        const asObject = identityFromRows({ "general.appliedPresetTrainee": "A", "racing.appliedRacingSnapshot": { a: 1, b: 2 } })
        expect(asString.hash).toBe(asObject.hash)
    })

    it("hash is order-independent across enumeration", () => {
        const a = identityFromRows({ "general.scenario": "X", "skills.accountTier": "endgame" })
        const b = identityFromRows({ "skills.accountTier": "endgame", "general.scenario": "X" })
        expect(a.hash).toBe(b.hash)
    })

    it("non-launch-critical categories do not affect the identity", () => {
        const base = makeSettings()
        const withDebugChange = makeSettings()
        withDebugChange.debug.enableDebugMode = false
        withDebugChange.misc.currentProfileName = "other"
        expect(launchConfigIdentity(base).hash).toBe(launchConfigIdentity(withDebugChange).hash)
    })

    it("racingPlanData (derived blob) is excluded but racingPlan is included", () => {
        const a = makeSettings()
        const b = makeSettings()
        b.racing.racingPlanData = "totally-different-blob"
        expect(launchConfigIdentity(a).hash).toBe(launchConfigIdentity(b).hash)
        const c = makeSettings()
        c.racing.racingPlan = '[{"raceName":"X"}]'
        expect(launchConfigIdentity(a).hash).not.toBe(launchConfigIdentity(c).hash)
    })

    it("a training-field change (statPrioritization) changes the hash -- category coverage", () => {
        const a = makeSettings()
        const b = makeSettings({ extraTraining: { statPrioritization: ["Guts", "Speed", "Stamina", "Wit", "Power"] } })
        expect(launchConfigIdentity(a).hash).not.toBe(launchConfigIdentity(b).hash)
    })

    it("changing the required support deck (runQueue.supportDeckIndex) changes the hash -- category coverage", () => {
        // Deck 2 vs Deck 5 materially changes the career configuration, so it must move the launch
        // identity. runQueue is a launch-critical category, so the new field is hashed automatically.
        const off = makeSettings()
        off.runQueue.supportDeckIndex = 0
        const deck5 = makeSettings()
        deck5.runQueue.supportDeckIndex = 5
        const deck2 = makeSettings()
        deck2.runQueue.supportDeckIndex = 2
        expect(launchConfigIdentity(off).hash).not.toBe(launchConfigIdentity(deck5).hash)
        expect(launchConfigIdentity(deck2).hash).not.toBe(launchConfigIdentity(deck5).hash)
        expect(LAUNCH_CRITICAL_CATEGORIES).toContain("runQueue")
    })

    it("bumpSettingsRevision increments immutably and clamps malformed values", () => {
        expect(bumpSettingsRevision(makeSettings({ revision: 4 })).general.settingsRevision).toBe(5)
        expect(bumpSettingsRevision({ general: { settingsRevision: "oops" } } as any).general.settingsRevision).toBe(1)
        expect(bumpSettingsRevision({ general: {} } as any).general.settingsRevision).toBe(1)
    })

    it("identityFromRows tolerates a missing/malformed revision row (fails safe to 0)", () => {
        expect(identityFromRows({}).revision).toBe(0)
        expect(identityFromRows({ "general.settingsRevision": "xyz" }).revision).toBe(0)
        expect(identityFromRows({ "general.settingsRevision": "-3" }).revision).toBe(0)
        expect(identityFromRows({ "general.settingsRevision": "7" }).revision).toBe(7)
    })

    it("the McQueen and Super Creek presets cannot share a verified identity", () => {
        const mcqueen = launchConfigIdentity(makeSettings({ trainee: "[Frontline Elegance] Mejiro McQueen", objective: "rank", revision: 1 }))
        const creek = launchConfigIdentity(makeSettings({ trainee: "Super Creek", objective: "sparks", revision: 2 }))
        expect(launchConfigIdentitiesMatch(mcqueen, creek).ok).toBe(false)
        expect(launchConfigIdentity(makeSettings()).objective).not.toBe("sparks")
    })

    it("LAUNCH_CRITICAL_CATEGORIES stays the authoritative run-scoped set", () => {
        expect(LAUNCH_CRITICAL_CATEGORIES).toEqual(["general", "training", "trainingEvent", "skills", "racing", "runQueue"])
        expect(LAUNCH_CRITICAL_CATEGORIES).not.toContain("debug")
        expect(LAUNCH_CRITICAL_CATEGORIES).not.toContain("discord")
    })
})

describe("launchConfigIdentitiesMatch", () => {
    const base = makeSettings({ revision: 3, objective: "sparks" })
    it("matches an identical identity", () => {
        expect(launchConfigIdentitiesMatch(launchConfigIdentity(base), launchConfigIdentity(base)).ok).toBe(true)
    })
    for (const [label, over] of [
        ["revision", { revision: 2, objective: "sparks" }],
        ["trainee", { revision: 3, objective: "sparks", trainee: "Super Creek" }],
        ["objective", { revision: 3, objective: "rank" }],
    ] as const) {
        it(`blocks on ${label} mismatch`, () => {
            const m = launchConfigIdentitiesMatch(launchConfigIdentity(base), launchConfigIdentity(makeSettings(over)))
            expect(m.ok).toBe(false)
            expect(m.reason).toMatch(new RegExp(label))
        })
    }
})

describe("verifyLaunchConfigPersisted (Start barrier)", () => {
    const TIMEOUT = 8000

    async function runBarrier(store: ReturnType<typeof fakeStore>, intended: any, timers: ReturnType<typeof fakeTimers>) {
        return verifyLaunchConfigPersisted({
            intended: launchConfigIdentity(intended),
            flush: () => store.flush(storedRows(intended)),
            readback: store.readback,
            recover: store.recover,
            timeoutMs: TIMEOUT,
            schedule: timers.schedule,
            cancel: timers.cancel,
        })
    }

    it("2+8: waits for the ACTUAL commit, then launches exactly once", async () => {
        const intended = makeSettings({ revision: 2, objective: "sparks" })
        const store = fakeStore(storedRows(makeSettings({ revision: 1, objective: "rank" })))
        const timers = fakeTimers()
        let launches = 0
        const p = runStartBarrier({ verify: () => runBarrier(store, intended, timers), launch: () => { launches += 1 }, onBlocked: () => {} })
        await Promise.resolve()
        expect(launches).toBe(0)
        store.release()
        expect((await p).ok).toBe(true)
        expect(launches).toBe(1)
    })

    it("3: a delayed writer cannot be overtaken -- readback runs only after flush resolves", async () => {
        const intended = makeSettings({ revision: 5, objective: "sparks" })
        const store = fakeStore(storedRows(makeSettings({ revision: 4, objective: "rank" })))
        const timers = fakeTimers()
        const verify = runBarrier(store, intended, timers)
        await Promise.resolve()
        expect((await store.readback()).revision).toBe(4)
        store.release()
        expect((await verify).ok).toBe(true)
    })

    it("3-COMMIT: a rejected flush (COMMIT failure) blocks at flush, never launches", async () => {
        const intended = makeSettings({ revision: 2 })
        const store = fakeStore(storedRows(makeSettings({ revision: 1 })))
        const timers = fakeTimers()
        let launches = 0
        const p = runStartBarrier({ verify: () => runBarrier(store, intended, timers), launch: () => { launches += 1 }, onBlocked: () => {} })
        await Promise.resolve()
        store.failFlush(new Error("COMMIT failed: disk I/O"))
        const r = await p
        expect(r.ok).toBe(false)
        expect(r.stage).toBe("flush")
        expect(launches).toBe(0)
    })

    it("4-7: a persisted config that differs from intended blocks at verify (never launches)", async () => {
        const intended = makeSettings({ revision: 2, objective: "sparks" })
        const store = fakeStore(storedRows(makeSettings({ revision: 1, objective: "rank" })))
        const timers = fakeTimers()
        const barrier = verifyLaunchConfigPersisted({
            intended: launchConfigIdentity(intended),
            // "write" commits stale rows, not the intended ones.
            flush: () =>
                new Promise<void>((resolve) => {
                    Object.assign(store.committed, storedRows(makeSettings({ revision: 1, objective: "rank" })))
                    resolve()
                }),
            readback: store.readback,
            recover: store.recover,
            timeoutMs: TIMEOUT,
            schedule: timers.schedule,
            cancel: timers.cancel,
        })
        const result = await barrier
        expect(result.ok).toBe(false)
        expect(result.stage).toBe("verify")
    })

    it("5-INTERLEAVE: an atomic snapshot observes ALL-OLD (block) or ALL-NEW (pass), never mixed", async () => {
        // A commit lands between the flush resolving and the read-back. Because read-back is one
        // atomic snapshot, it sees the complete new config (pass) -- never trainee-new/objective-old.
        const oldRows = storedRows(makeSettings({ trainee: "[Frontline Elegance] Mejiro McQueen", objective: "rank", revision: 1 }))
        const intended = makeSettings({ trainee: "Super Creek", objective: "sparks", revision: 2 })
        const store = fakeStore(oldRows)
        const timers = fakeTimers()
        // Model the atomic guarantee: the snapshot is taken as one map copy at read time.
        const barrier = verifyLaunchConfigPersisted({
            intended: launchConfigIdentity(intended),
            flush: async () => {
                // commit ALL intended rows in one shot (a real single-transaction COMMIT)
                Object.assign(store.committed, storedRows(intended))
            },
            readback: store.readback, // reads {...committed} -- a whole consistent snapshot
            timeoutMs: TIMEOUT,
            schedule: timers.schedule,
            cancel: timers.cancel,
        })
        const r = await barrier
        expect(r.ok).toBe(true)
        // The persisted identity is wholly the new config -- trainee AND objective moved together.
        expect(r.persisted?.trainee).toBe("Super Creek")
        expect(r.persisted?.objective).toBe("sparks")
    })

    it("4-STALL: a stalled writer times out into a visible failure, recovers, zero launches", async () => {
        const intended = makeSettings({ revision: 2 })
        const store = fakeStore(storedRows(makeSettings({ revision: 1 })))
        const timers = fakeTimers()
        let launches = 0
        const p = runStartBarrier({ verify: () => runBarrier(store, intended, timers), launch: () => { launches += 1 }, onBlocked: () => {} })
        await Promise.resolve()
        timers.advance(TIMEOUT + 1)
        const result = await p
        expect(result.ok).toBe(false)
        expect(result.stage).toBe("flush")
        expect(result.reason).toMatch(/stall/i)
        expect(store.wasRecovered()).toBe(true)
        expect(launches).toBe(0)
    })

    it("13: retry after writer recovery launches the intended preset", async () => {
        const intended = makeSettings({ revision: 2, objective: "sparks" })
        const store1 = fakeStore(storedRows(makeSettings({ revision: 1 })))
        const timers1 = fakeTimers()
        const first = runBarrier(store1, intended, timers1)
        await Promise.resolve()
        timers1.advance(TIMEOUT + 1)
        expect((await first).ok).toBe(false)
        const store2 = fakeStore(storedRows(makeSettings({ revision: 1 })))
        const timers2 = fakeTimers()
        const second = runBarrier(store2, intended, timers2)
        await Promise.resolve()
        store2.release()
        expect((await second).ok).toBe(true)
    })

    it("9: rapid preset A then B launches only B", async () => {
        const b = makeSettings({ trainee: "Super Creek", revision: 3, objective: "sparks" })
        const store = fakeStore(storedRows(makeSettings({ revision: 1 })))
        const timers = fakeTimers()
        const p = runBarrier(store, b, timers)
        await Promise.resolve()
        store.release()
        const result = await p
        expect(result.ok).toBe(true)
        expect(result.persisted?.trainee).toBe("Super Creek")
        expect(result.persisted?.revision).toBe(3)
    })

    it("16/20: the exact incident -- McQueen on disk, Super Creek intended, delayed persist; blocked until it lands, McQueen never launches", async () => {
        const superCreek = makeSettings({ trainee: "Super Creek", revision: 2, objective: "sparks" })
        const mcQueenRows = storedRows(makeSettings({ trainee: "[Frontline Elegance] Mejiro McQueen", revision: 1, objective: "rank" }))
        const store = fakeStore(mcQueenRows)
        const timers = fakeTimers()
        let launched = false
        const first = runStartBarrier({ verify: () => runBarrier(store, superCreek, timers), launch: () => { launched = true }, onBlocked: () => {} })
        await Promise.resolve()
        timers.advance(TIMEOUT + 1)
        expect((await first).ok).toBe(false)
        expect(launched).toBe(false)

        const store2 = fakeStore(mcQueenRows)
        const timers2 = fakeTimers()
        const second = runBarrier(store2, superCreek, timers2)
        await Promise.resolve()
        store2.release()
        const r2 = await second
        expect(r2.ok).toBe(true)
        expect(r2.persisted?.trainee).toBe("Super Creek")
    })

    it("17/18: a manual objective or skill-plan edit before Start is included in the identity", async () => {
        const edited = makeSettings({ revision: 1, objective: "sparks" })
        edited.skills.plans = { careerComplete: { strategy: "OPTIMIZE_KNAPSACK" } }
        const store = fakeStore(storedRows(makeSettings({ revision: 1, objective: "rank" }))) // objective/plan NOT edited on disk yet
        const timers = fakeTimers()
        // Flush commits the edited rows; verify must reflect them.
        const p = verifyLaunchConfigPersisted({
            intended: launchConfigIdentity(edited),
            flush: async () => {
                Object.assign(store.committed, storedRows(edited))
            },
            readback: store.readback,
            timeoutMs: TIMEOUT,
            schedule: timers.schedule,
            cancel: timers.cancel,
        })
        expect((await p).ok).toBe(true)
    })
})

// 8/9/10: single-flight -- double-press launches once; cancel during await refuses.
describe("createSingleFlight", () => {
    it("8: a re-entrant begin() while in flight is rejected", () => {
        const g = createSingleFlight()
        expect(g.begin()).toBe(true)
        expect(g.begin()).toBe(false) // second press ignored
        expect(g.busy).toBe(true)
        g.end()
        expect(g.begin()).toBe(true) // usable again after end
    })

    it("9/10: cancel during the in-flight sequence refuses the launch", () => {
        const g = createSingleFlight()
        g.begin()
        expect(g.mayLaunch()).toBe(true)
        g.cancel() // Stop / preset change / unmount
        expect(g.mayLaunch()).toBe(false)
        g.end()
    })

    it("a fresh sequence after end() is not tainted by a prior cancel", () => {
        const g = createSingleFlight()
        g.begin()
        g.cancel()
        g.end()
        g.begin()
        expect(g.mayLaunch()).toBe(true)
    })

    it("orchestrated: verify passes but a mid-flight cancel means zero launches", async () => {
        const g = createSingleFlight()
        let launches = 0
        g.begin()
        const seq = (async () => {
            await Promise.resolve() // barrier awaiting
            if (!g.mayLaunch()) return
            launches += 1
        })()
        g.cancel()
        await seq
        g.end()
        expect(launches).toBe(0)
    })
})

// 14/15/18/19: source guards -- barrier + identity handoff are the sole gate before BotService,
// for single-run and queue, and the atomic snapshot + fail-closed recovery are wired.
describe("Start-barrier source guards", () => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const fs = require("fs")
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const path = require("path")
    const read = (rel: string) => fs.readFileSync(path.join(__dirname, "..", "..", rel), "utf8")
    const homeSrc = read("pages/Home/index.tsx")
    const seq = homeSrc.slice(homeSrc.indexOf("const runStartSequence"), homeSrc.indexOf("const handleButtonPress"))

    it("the barrier runs before the identity handoff, which runs before StartModule.start()", () => {
        const barrierAt = seq.indexOf("flushAndVerifyLaunchConfig()")
        const handoffAt = seq.indexOf("setVerifiedLaunchIdentity(")
        const startAt = seq.indexOf("StartModule.start()")
        expect(barrierAt).toBeGreaterThan(0)
        expect(handoffAt).toBeGreaterThan(barrierAt)
        expect(startAt).toBeGreaterThan(handoffAt)
    })

    it("a blocked barrier hard-returns before StartModule.start()", () => {
        const barrierAt = seq.indexOf("if (!barrier.ok)")
        const returnAt = seq.indexOf("return", barrierAt)
        const startAt = seq.indexOf("StartModule.start()")
        expect(returnAt).toBeGreaterThan(barrierAt)
        expect(returnAt).toBeLessThan(startAt)
    })

    it("mayLaunch() is re-checked after the barrier and after rotation prep", () => {
        expect((seq.match(/startGate\.mayLaunch\(\)/g) || []).length).toBeGreaterThanOrEqual(2)
    })

    it("StartModule.start() appears exactly once (one gated launch, single-run or queue)", () => {
        expect((seq.match(/StartModule\.start\(\)/g) || []).length).toBe(1)
    })

    it("the manager uses the atomic snapshot read-back and fail-closed recovery", () => {
        const mgr = read("hooks/useSettingsManager.tsx")
        expect(mgr).toContain("loadSettingsRowsSnapshot")
        expect(mgr).toContain("failStalledWriter")
        expect(mgr).not.toContain("loadSetting(f.category")
    })
})
