import { DatabaseManager } from "../database"
import type { SettingsDb, SqlRow, SqlValue, TransactionStep } from "../settingsDb"

// settingsDb.ts reaches the Kotlin module through react-native, which this plain-Node Jest config
// does not load. initialize() takes the module's settingsDb, so it is replaced with the fake below;
// every other test injects a fake straight into the manager. babel-jest hoists this above the import.
let mockCurrentDb: SettingsDb | null = null
jest.mock("../settingsDb", () => ({
    get settingsDb() {
        return mockCurrentDb
    },
}))

type Row = Record<string, any>
type Store = Record<string, Row[]>

/**
 * An in-memory stand-in for the native SettingsDatabase module, faithful to what database.ts relies
 * on: `transaction` commits every step's rows together or none of them; `run` autocommits and
 * returns numbers; `exec` refuses a string holding more than one statement (Android runs only the
 * first); every call's arguments must match the statement's placeholders; and `profiles.name` is
 * UNIQUE. One instance per test, shared by every call, like the app's single connection.
 */
function createFakeSettingsDb() {
    let committed: Store = { profiles: [], settings: [], races: [], skills: [] }
    let nextProfileId = 1
    let rowCount = 0
    let failOnRowAt: number | null = null
    let runError: Error | null = null
    let hold: Promise<void> | null = null
    const calls: { method: string; payload: unknown }[] = []

    const clone = (s: Store): Store => Object.fromEntries(Object.entries(s).map(([k, rows]) => [k, rows.map((r) => ({ ...r }))]))

    const singleStatement = (sql: string) => {
        if (sql.trim().replace(/;\s*$/, "").includes(";")) throw new Error(`more than one statement in one string: ${sql}`)
    }

    const checkArgs = (sql: string, args: SqlValue[]) => {
        const placeholders = (sql.match(/\?/g) ?? []).length
        if (placeholders !== args.length) throw new Error(`${args.length} arguments for ${placeholders} placeholders: ${sql}`)
    }

    const table = (sql: string): string => {
        const m = sql.match(/(?:INTO|FROM|UPDATE)\s+(\w+)/i)
        if (!m) throw new Error(`fake: no table in ${sql}`)
        return m[1]
    }

    /** Applies one write to `store`; returns the new row id for a profile insert. */
    const write = (store: Store, sql: string, args: SqlValue[]): number => {
        checkArgs(sql, args)
        const s = sql.replace(/\s+/g, " ").trim()
        const t = table(s)
        if (/^DELETE FROM \w+$/i.test(s)) {
            store[t] = []
            return 0
        }
        if (/^DELETE FROM profiles WHERE id = \?$/i.test(s)) {
            store.profiles = store.profiles.filter((r) => r.id !== args[0])
            return 0
        }
        if (/^DELETE FROM settings WHERE category = \? AND key = \?$/i.test(s)) {
            store.settings = store.settings.filter((r) => !(r.category === args[0] && r.key === args[1]))
            return 0
        }
        if (/^INSERT OR REPLACE INTO settings /i.test(s)) {
            const [category, key, value] = args as string[]
            const existing = store.settings.find((r) => r.category === category && r.key === key)
            if (existing) existing.value = value
            else store.settings.push({ category, key, value })
            return 0
        }
        if (/^INSERT INTO profiles /i.test(s)) {
            const [name, settings] = args as string[]
            if (store.profiles.some((r) => r.name === name)) throw new Error("UNIQUE constraint failed: profiles.name (code 2067 SQLITE_CONSTRAINT_UNIQUE)")
            const id = nextProfileId++
            store.profiles.push({ id, name, settings, created_at: "t", updated_at: "t" })
            return id
        }
        if (/^UPDATE profiles SET name = \?, settings = \?/i.test(s)) {
            const [name, settings, id] = args as [string, string, number]
            if (store.profiles.some((r) => r.name === name && r.id !== id)) throw new Error("UNIQUE constraint failed: profiles.name (code 2067 SQLITE_CONSTRAINT_UNIQUE)")
            const row = store.profiles.find((r) => r.id === id)
            if (row) Object.assign(row, { name, settings })
            return 0
        }
        if (/^UPDATE profiles SET settings = \?/i.test(s)) {
            const [settings, id] = args as [string, number]
            const row = store.profiles.find((r) => r.id === id)
            if (row) row.settings = settings
            return 0
        }
        if (/^INSERT OR REPLACE INTO (races|skills) /i.test(s)) {
            store[t] = store[t].filter((r) => r.key !== args[0])
            store[t].push({ key: args[0], args })
            return 0
        }
        throw new Error(`fake: unsupported write ${s}`)
    }

    const db: SettingsDb = {
        open: async () => {
            calls.push({ method: "open", payload: null })
        },
        exec: async (statements: string[]) => {
            calls.push({ method: "exec", payload: statements })
            statements.forEach(singleStatement)
        },
        run: async (sql: string, params: SqlValue[] = []) => {
            calls.push({ method: "run", payload: { sql, params } })
            singleStatement(sql)
            if (runError) {
                const e = runError
                runError = null
                throw e
            }
            const id = write(committed, sql, params)
            return { changes: 1, lastInsertRowId: id }
        },
        query: async <T = SqlRow>(sql: string, params: SqlValue[] = []): Promise<T[]> => {
            calls.push({ method: "query", payload: { sql, params } })
            singleStatement(sql)
            checkArgs(sql, params)
            const s = sql.replace(/\s+/g, " ").trim()
            if (/^SELECT \* FROM profiles WHERE id = \?$/i.test(s)) return committed.profiles.filter((r) => r.id === params[0]) as T[]
            if (/^SELECT \* FROM settings WHERE category = \? AND key = \?$/i.test(s)) {
                return committed.settings.filter((r) => r.category === params[0] && r.key === params[1]) as T[]
            }
            if (/^PRAGMA table_info/i.test(s)) return [] as T[]
            throw new Error(`fake: unsupported query ${s}`)
        },
        transaction: async (steps: TransactionStep[]) => {
            calls.push({ method: "transaction", payload: steps })
            if (hold) await hold
            const next = clone(committed)
            rowCount = 0
            for (const step of steps) {
                singleStatement(step.sql)
                for (const row of step.rows) {
                    const fail = failOnRowAt !== null && rowCount === failOnRowAt
                    rowCount++
                    if (fail) throw new Error("simulated insert failure")
                    write(next, step.sql, row)
                }
            }
            committed = next
        },
    }

    return {
        db,
        calls,
        getCommitted: (t: string): Row[] => committed[t] ?? [],
        seed: (t: string, rows: Row[]) => {
            committed[t] = rows
            nextProfileId = Math.max(nextProfileId, ...rows.map((r) => (typeof r.id === "number" ? r.id + 1 : 1)))
        },
        /** Make the Nth row (0-indexed, counted across a transaction's steps) throw. */
        setFailOnRowAt: (i: number | null) => {
            failOnRowAt = i
        },
        /** Make the next `run` reject with this error, as the native module would. */
        setRunError: (e: Error) => {
            runError = e
        },
        /** Keep transactions from finishing until the returned release is called. */
        holdTransactions: () => {
            let release: () => void = () => {}
            hold = new Promise<void>((resolve) => (release = resolve))
            return () => {
                hold = null
                release()
            }
        },
        transactions: () => calls.filter((c) => c.method === "transaction").map((c) => c.payload as TransactionStep[]),
    }
}

function managerWith(fake: ReturnType<typeof createFakeSettingsDb>): DatabaseManager {
    const dm = new DatabaseManager()
    ;(dm as unknown as { db: SettingsDb }).db = fake.db
    return dm
}

describe("DatabaseManager.initialize", () => {
    it("opens the native connection and sends one statement per exec element, with no journal-mode pragma", async () => {
        const fake = createFakeSettingsDb()
        mockCurrentDb = fake.db
        const dm = new DatabaseManager()
        await dm.initialize()

        expect(fake.calls[0].method).toBe("open")
        expect(dm.isInitialized()).toBe(true)
        const statements = fake.calls.filter((c) => c.method === "exec").flatMap((c) => c.payload as string[])
        expect(statements.length).toBeGreaterThan(5)
        for (const statement of statements) expect(statement.trim().replace(/;\s*$/, "")).not.toContain(";")
        expect(statements.some((s) => /journal_mode/i.test(s))).toBe(false)

        // The skills table is dropped and recreated in one call, so it is never left missing.
        const skillsCall = fake.calls.find((c) => c.method === "exec" && (c.payload as string[]).some((s) => /DROP TABLE IF EXISTS skills/.test(s)))
        expect((skillsCall!.payload as string[]).map((s) => s.trim().split(/\s+/).slice(0, 2).join(" "))).toEqual(["DROP TABLE", "CREATE TABLE"])
        mockCurrentDb = null
    })

    it("stays uninitialized when the native open fails", async () => {
        const fake = createFakeSettingsDb()
        mockCurrentDb = { ...fake.db, open: async () => Promise.reject(new Error("The SettingsDatabase native module is not available")) }
        const dm = new DatabaseManager()
        await expect(dm.initialize()).rejects.toThrow("not available")
        expect(dm.isInitialized()).toBe(false)
        mockCurrentDb = null
    })
})

describe("DatabaseManager.replaceAllProfiles", () => {
    it("replaces every profile with the new set in one native transaction: the delete, then every insert", async () => {
        const fake = createFakeSettingsDb()
        fake.seed("profiles", [{ id: 1, name: "Old A", settings: JSON.stringify({ x: 1 }), created_at: "t", updated_at: "t" }])
        const dm = managerWith(fake)

        await dm.replaceAllProfiles([
            { name: "New A", settings: { y: 2 } },
            { name: "New B", settings: { y: 3 } },
        ])

        expect(fake.getCommitted("profiles").map((r) => r.name)).toEqual(["New A", "New B"])
        const [steps] = fake.transactions()
        expect(fake.transactions()).toHaveLength(1)
        expect(steps[0]).toEqual({ sql: "DELETE FROM profiles", rows: [[]] })
        expect(steps[1].rows).toEqual([
            ["New A", JSON.stringify({ y: 2 })],
            ["New B", JSON.stringify({ y: 3 })],
        ])
    })

    it("reuses a name shared with an old profile without a UNIQUE collision (the old row is gone before the new one is inserted, within the same transaction)", async () => {
        const fake = createFakeSettingsDb()
        fake.seed("profiles", [{ id: 1, name: "Default", settings: JSON.stringify({ old: true }), created_at: "t", updated_at: "t" }])
        const dm = managerWith(fake)

        await dm.replaceAllProfiles([{ name: "Default", settings: { old: false } }])

        const committed = fake.getCommitted("profiles")
        expect(committed).toHaveLength(1)
        expect(JSON.parse(committed[0].settings)).toEqual({ old: false })
    })

    it("rejects two profiles in the same import that share a name, and leaves the old profiles untouched", async () => {
        const fake = createFakeSettingsDb()
        fake.seed("profiles", [{ id: 1, name: "Kept", settings: JSON.stringify({}), created_at: "t", updated_at: "t" }])
        const dm = managerWith(fake)

        await expect(
            dm.replaceAllProfiles([
                { name: "Dup", settings: {} },
                { name: "Dup", settings: {} },
            ])
        ).rejects.toThrow(/UNIQUE constraint/)

        expect(fake.getCommitted("profiles").map((r) => r.name)).toEqual(["Kept"])
    })

    it("on a mid-set failure keeps the old profiles, and the next write on the same connection succeeds", async () => {
        const fake = createFakeSettingsDb()
        fake.seed("profiles", [
            { name: "Keep A", settings: JSON.stringify({ a: 1 }), id: 1, created_at: "t", updated_at: "t" },
            { name: "Keep B", settings: JSON.stringify({ b: 2 }), id: 2, created_at: "t", updated_at: "t" },
        ])
        fake.setFailOnRowAt(2) // The delete is row 0; the second insert of the replacement set fails.
        const dm = managerWith(fake)

        await expect(
            dm.replaceAllProfiles([
                { name: "Replacement A", settings: {} },
                { name: "Replacement B", settings: {} },
            ])
        ).rejects.toThrow("simulated insert failure")

        expect(fake.getCommitted("profiles").map((r) => r.name)).toEqual(["Keep A", "Keep B"])

        fake.setFailOnRowAt(null)
        await dm.saveSettingsBatch([{ category: "general", key: "scenario", value: "URA" }])
        expect(fake.getCommitted("settings")).toEqual([{ category: "general", key: "scenario", value: "URA" }])
    })

    it("never leaves zero profiles even mid-replacement: a failure on the first insert rolls the delete back too", async () => {
        const fake = createFakeSettingsDb()
        fake.seed("profiles", [{ id: 1, name: "Solo", settings: JSON.stringify({}), created_at: "t", updated_at: "t" }])
        fake.setFailOnRowAt(1)
        const dm = managerWith(fake)

        await expect(dm.replaceAllProfiles([{ name: "New Solo", settings: {} }])).rejects.toThrow()

        expect(fake.getCommitted("profiles").map((r) => r.name)).toEqual(["Solo"])
    })

    it("an empty import clears the profiles: the delete runs once and the insert zero times", async () => {
        const fake = createFakeSettingsDb()
        fake.seed("profiles", [{ id: 1, name: "Gone", settings: "{}", created_at: "t", updated_at: "t" }])
        const dm = managerWith(fake)

        await dm.replaceAllProfiles([])

        expect(fake.getCommitted("profiles")).toEqual([])
        expect(fake.transactions()[0][1].rows).toEqual([])
    })
})

describe("DatabaseManager.saveSettingsBatch", () => {
    it("saves every setting in one native transaction, values serialized", async () => {
        const fake = createFakeSettingsDb()
        const dm = managerWith(fake)

        await dm.saveSettingsBatch([
            { category: "general", key: "scenario", value: "URA" },
            { category: "training", key: "maximumFailureChance", value: 30 },
        ])

        expect(fake.getCommitted("settings")).toEqual([
            { category: "general", key: "scenario", value: "URA" },
            { category: "training", key: "maximumFailureChance", value: "30" },
        ])
        expect(fake.transactions()).toHaveLength(1)
    })

    it("on a mid-batch failure keeps the old settings, and the next batch on the same connection succeeds", async () => {
        const fake = createFakeSettingsDb()
        fake.seed("settings", [{ category: "general", key: "scenario", value: "URA" }])
        fake.setFailOnRowAt(1)
        const dm = managerWith(fake)

        await expect(
            dm.saveSettingsBatch([
                { category: "training", key: "a", value: 1 },
                { category: "training", key: "b", value: 2 },
            ])
        ).rejects.toThrow("simulated insert failure")

        expect(fake.getCommitted("settings")).toEqual([{ category: "general", key: "scenario", value: "URA" }])

        fake.setFailOnRowAt(null)
        await dm.saveSettingsBatch([{ category: "general", key: "scenario", value: "Unity Cup" }])
        expect(fake.getCommitted("settings")).toEqual([{ category: "general", key: "scenario", value: "Unity Cup" }])
    })

    it("sends nothing for an empty batch", async () => {
        const fake = createFakeSettingsDb()
        await managerWith(fake).saveSettingsBatch([])
        expect(fake.calls).toEqual([])
    })
})

describe("DatabaseManager races and skills batches", () => {
    const race = {
        key: "arima",
        name: "Arima Kinen",
        date: "Senior Year Late Dec",
        raceTrack: "Nakayama",
        course: null,
        direction: "Right",
        grade: "G1",
        terrain: "Turf",
        distanceType: "Long",
        distanceMeters: 2500,
        fans: 30000,
        turnNumber: 72,
        nameFormatted: "arima_kinen",
    }
    const skill = {
        key: "s1",
        skill_id: 100011,
        name_en: "Right-Handed",
        desc_en: "",
        icon_id: 1,
        cost: 90,
        eval_pt: 100,
        condition: "",
        precondition: "",
        inherited: true,
        community_tier: null,
        upgrade: null,
        downgrade: null,
    }

    it("saves races in one native transaction, keeping a null course null", async () => {
        const fake = createFakeSettingsDb()
        await managerWith(fake).saveRacesBatch([race])
        const [steps] = fake.transactions()
        expect(steps).toHaveLength(1)
        expect(steps[0].rows[0]).toHaveLength(13)
        expect(steps[0].rows[0][4]).toBeNull()
    })

    it("saves skills in one native transaction, passing inherited through as a boolean", async () => {
        const fake = createFakeSettingsDb()
        await managerWith(fake).saveSkillsBatch([skill, { ...skill, key: "s2", inherited: false }])
        const [steps] = fake.transactions()
        expect(steps[0].rows.map((row) => row[9])).toEqual([true, false])
        expect(fake.getCommitted("skills")).toHaveLength(2)
    })

    it("rolls a failing skills batch back whole", async () => {
        const fake = createFakeSettingsDb()
        fake.setFailOnRowAt(1)
        await expect(managerWith(fake).saveSkillsBatch([skill, { ...skill, key: "s2" }])).rejects.toThrow("simulated insert failure")
        expect(fake.getCommitted("skills")).toEqual([])
    })
})

describe("DatabaseManager single statements", () => {
    it("returns the native lastInsertRowId as the new profile's id", async () => {
        const fake = createFakeSettingsDb()
        fake.seed("profiles", [{ id: 4, name: "A", settings: "{}", created_at: "t", updated_at: "t" }])
        const id = await managerWith(fake).saveProfile({ name: "B", settings: { x: 1 } })
        expect(id).toBe(5)
        expect(typeof id).toBe("number")
    })

    it("keeps a profile's name when the native UNIQUE error comes from its own unchanged name", async () => {
        const fake = createFakeSettingsDb()
        fake.seed("profiles", [{ id: 1, name: "Main", settings: "{}", created_at: "t", updated_at: "t" }])
        fake.setRunError(new Error("UNIQUE constraint failed: profiles.name (code 2067 SQLITE_CONSTRAINT_UNIQUE)"))
        const id = await managerWith(fake).saveProfile({ id: 1, name: "Main", settings: { changed: true } })
        expect(id).toBe(1)
        expect(JSON.parse(fake.getCommitted("profiles")[0].settings)).toEqual({ changed: true })
    })

    it("passes a native error message to the caller unchanged", async () => {
        const fake = createFakeSettingsDb()
        fake.setRunError(new Error("disk I/O error (code 10 SQLITE_IOERR)"))
        await expect(managerWith(fake).saveSetting("general", "scenario", "URA", true)).rejects.toThrow("disk I/O error (code 10 SQLITE_IOERR)")
    })
})

describe("DatabaseManager stall handling", () => {
    it("with a write stuck in flight, queued writes are rejected and new ones fail fast until it settles", async () => {
        const fake = createFakeSettingsDb()
        const dm = managerWith(fake)
        const release = fake.holdTransactions()

        const stuck = dm.saveSettingsBatch([{ category: "general", key: "a", value: "1" }])
        const queued = dm.saveSettingsBatch([{ category: "general", key: "b", value: "2" }])
        await dm.failStalledWriter("flush timed out")

        await expect(queued).rejects.toThrow("flush timed out")
        await expect(dm.saveSettingsBatch([{ category: "general", key: "c", value: "3" }])).rejects.toThrow(/stalled/)

        release()
        await stuck
        await dm.saveSettingsBatch([{ category: "general", key: "d", value: "4" }])
        expect(fake.getCommitted("settings").map((r) => r.key)).toEqual(["a", "d"])
    })
})
