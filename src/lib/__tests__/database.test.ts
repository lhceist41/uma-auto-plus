import { DatabaseManager } from "../database"

// expo-sqlite ships ESM this repo's plain-Node Jest config does not transform, so importing
// database.ts at all (even without opening a real database) requires stubbing the module out first.
// babel-jest hoists this above the import above at compile time, so the textual order here is only
// for eslint's import/first rule; expo-sqlite is still mocked before database.ts ever executes.
jest.mock("expo-sqlite", () => ({}))

type Row = Record<string, any>

/**
 * A minimal in-memory stand-in for the `expo-sqlite` handle, faithful to the behaviors this suite
 * depends on: a nested `BEGIN` throws, as SQLite does not allow starting a transaction while one is
 * already open on the connection; a statement executed outside `BEGIN`/`COMMIT` takes effect
 * immediately (autocommit), one executed between them only lands on `COMMIT` and is discarded by
 * `ROLLBACK`; and `INSERT INTO profiles` enforces the real `UNIQUE(name)` constraint.
 * All four `database.ts` write methods under test share ONE instance, exactly like the app's single
 * shared connection, so a test can prove a failed write leaves the connection usable for the next one.
 */
function createFakeConnection() {
    let committed: Record<string, Row[]> = { profiles: [], settings: [] }
    let pending: Record<string, Row[]> | null = null
    let nextProfileId = 1
    const sentSql: string[] = []
    let insertCount = 0
    let failOnInsertAt: number | null = null

    const activeStore = () => pending ?? committed

    const tableFromSql = (sql: string): string => {
        const m = sql.match(/(?:INTO|FROM)\s+(\w+)/i)
        if (!m) throw new Error(`fake connection: could not find a table name in SQL: ${sql}`)
        return m[1]
    }

    return {
        /** The durably stored rows for `table`, as of the last `COMMIT` (or the initial seed). */
        getCommitted: (table: string): Row[] => committed[table] ?? [],
        isInTransaction: () => pending !== null,
        getSentSql: () => [...sentSql],
        /** Seed a table's committed rows before running the method under test. */
        seed: (table: string, rows: Row[]) => {
            committed[table] = rows
        },
        /** Make the Nth `executeAsync` call (0-indexed, reset at every `BEGIN`) throw instead of writing. */
        setFailOnInsertAt: (i: number | null) => {
            failOnInsertAt = i
        },

        runAsync: async (sql: string) => {
            sentSql.push(sql.trim())
            const s = sql.trim().toUpperCase()
            if (s === "BEGIN TRANSACTION") {
                if (pending !== null) throw new Error("cannot start a transaction within a transaction")
                pending = { profiles: [...committed.profiles], settings: [...committed.settings] }
                insertCount = 0
                return
            }
            if (s === "COMMIT") {
                if (pending === null) throw new Error("cannot commit - no transaction is active")
                committed = pending
                pending = null
                return
            }
            if (s === "ROLLBACK") {
                if (pending === null) throw new Error("cannot rollback - no transaction is active")
                pending = null
                return
            }
            if (s.startsWith("DELETE FROM")) {
                activeStore()[tableFromSql(sql)] = []
                return
            }
            throw new Error(`fake connection: unsupported SQL: ${sql}`)
        },

        prepareAsync: async (sql: string) => {
            const table = tableFromSql(sql)
            return {
                executeAsync: async (args: unknown[]) => {
                    const shouldFail = failOnInsertAt !== null && insertCount === failOnInsertAt
                    insertCount++
                    if (shouldFail) throw new Error("simulated insert failure")

                    const store = activeStore()
                    if (table === "profiles") {
                        const [name, settingsJson] = args as [string, string]
                        if (store.profiles.some((r) => r.name === name)) {
                            throw new Error("UNIQUE constraint failed: profiles.name")
                        }
                        store.profiles.push({ id: nextProfileId++, name, settings: settingsJson, created_at: "t", updated_at: "t" })
                    } else if (table === "settings") {
                        const [category, key, value] = args as [string, string, string]
                        const existing = store.settings.find((r) => r.category === category && r.key === key)
                        if (existing) existing.value = value
                        else store.settings.push({ category, key, value })
                    } else {
                        throw new Error(`fake connection: unsupported table for INSERT: ${table}`)
                    }
                },
                finalizeAsync: async () => {},
            }
        },
    }
}

/** A `DatabaseManager` with a fake connection already injected, bypassing the real `initialize()`/expo-sqlite. */
function managerWithFakeDb(fakeDb: ReturnType<typeof createFakeConnection>): DatabaseManager {
    const dm = new DatabaseManager()
    ;(dm as unknown as { db: unknown }).db = fakeDb
    return dm
}

describe("DatabaseManager.replaceAllProfiles", () => {
    it("replaces every profile with the new set in one commit", async () => {
        const fakeDb = createFakeConnection()
        fakeDb.seed("profiles", [{ id: 1, name: "Old A", settings: JSON.stringify({ x: 1 }), created_at: "t", updated_at: "t" }])
        const dm = managerWithFakeDb(fakeDb)

        await dm.replaceAllProfiles([
            { name: "New A", settings: { y: 2 } },
            { name: "New B", settings: { y: 3 } },
        ])

        expect(fakeDb.getCommitted("profiles").map((r) => r.name)).toEqual(["New A", "New B"])
        expect(fakeDb.isInTransaction()).toBe(false)
    })

    it("reuses a name shared with an old profile without a UNIQUE collision (the old row is gone before the new one is inserted, within the same transaction)", async () => {
        const fakeDb = createFakeConnection()
        fakeDb.seed("profiles", [{ id: 1, name: "Default", settings: JSON.stringify({ old: true }), created_at: "t", updated_at: "t" }])
        const dm = managerWithFakeDb(fakeDb)

        await dm.replaceAllProfiles([{ name: "Default", settings: { old: false } }])

        const committed = fakeDb.getCommitted("profiles")
        expect(committed).toHaveLength(1)
        expect(JSON.parse(committed[0].settings)).toEqual({ old: false })
    })

    it("rejects two profiles in the same import that share a name, and leaves the old profiles untouched", async () => {
        const fakeDb = createFakeConnection()
        fakeDb.seed("profiles", [{ id: 1, name: "Kept", settings: JSON.stringify({}), created_at: "t", updated_at: "t" }])
        const dm = managerWithFakeDb(fakeDb)

        await expect(
            dm.replaceAllProfiles([
                { name: "Dup", settings: {} },
                { name: "Dup", settings: {} },
            ])
        ).rejects.toThrow(/UNIQUE constraint/)

        expect(fakeDb.getCommitted("profiles").map((r) => r.name)).toEqual(["Kept"])
        expect(fakeDb.isInTransaction()).toBe(false)
    })

    it(
        "on a mid-set failure: ROLLBACK is sent, no transaction is left open, the old rows are visible on the same " +
            "connection, and a following saveSettingsBatch succeeds on that same connection " +
            "(rollback runs inside the queued operation, not in the caller's catch, so it executes while the " +
            "operation still owns the connection instead of after the shared transaction-active flag has " +
            "already been cleared)",
        async () => {
            const original = [
                { name: "Keep A", settings: JSON.stringify({ a: 1 }), id: 1, created_at: "t", updated_at: "t" },
                { name: "Keep B", settings: JSON.stringify({ b: 2 }), id: 2, created_at: "t", updated_at: "t" },
            ]
            const fakeDb = createFakeConnection()
            fakeDb.seed("profiles", original)
            fakeDb.setFailOnInsertAt(1) // Second insert of the replacement set fails.
            const dm = managerWithFakeDb(fakeDb)

            await expect(
                dm.replaceAllProfiles([
                    { name: "Replacement A", settings: {} },
                    { name: "Replacement B", settings: {} },
                ])
            ).rejects.toThrow("simulated insert failure")

            expect(fakeDb.getSentSql().filter((s) => s.toUpperCase() === "ROLLBACK")).toHaveLength(1)
            expect(fakeDb.isInTransaction()).toBe(false)
            expect(fakeDb.getCommitted("profiles").map((r) => r.name)).toEqual(["Keep A", "Keep B"])

            // The connection is not left mid-transaction: a completely unrelated batch write on it succeeds.
            await dm.saveSettingsBatch([{ category: "general", key: "scenario", value: "URA" }])
            expect(fakeDb.getCommitted("settings")).toEqual([{ category: "general", key: "scenario", value: "URA" }])
        }
    )

    it("never leaves zero profiles even mid-replacement: the delete and every insert share one transaction", async () => {
        // Fail on the very first insert, right after the delete: the highest-risk window for a
        // reader to ever observe zero profiles if the delete were not rolled back with it.
        const fakeDb = createFakeConnection()
        fakeDb.seed("profiles", [{ id: 1, name: "Solo", settings: JSON.stringify({}), created_at: "t", updated_at: "t" }])
        fakeDb.setFailOnInsertAt(0)
        const dm = managerWithFakeDb(fakeDb)

        await expect(dm.replaceAllProfiles([{ name: "New Solo", settings: {} }])).rejects.toThrow()

        expect(fakeDb.getCommitted("profiles").map((r) => r.name)).toEqual(["Solo"])
        expect(fakeDb.isInTransaction()).toBe(false)
    })
})

describe("DatabaseManager.saveSettingsBatch", () => {
    it("saves every setting in one commit", async () => {
        const fakeDb = createFakeConnection()
        const dm = managerWithFakeDb(fakeDb)

        await dm.saveSettingsBatch([
            { category: "general", key: "scenario", value: "URA" },
            { category: "training", key: "maximumFailureChance", value: 30 },
        ])

        // Values pass through DatabaseManager.serializeValue before reaching the connection, so a
        // number arrives here already stringified.
        expect(fakeDb.getCommitted("settings")).toEqual([
            { category: "general", key: "scenario", value: "URA" },
            { category: "training", key: "maximumFailureChance", value: "30" },
        ])
        expect(fakeDb.isInTransaction()).toBe(false)
    })

    it(
        "on a mid-batch failure: ROLLBACK is sent, no transaction is left open, the old settings are visible on the " +
            "same connection, and a following saveSettingsBatch succeeds on that same connection " +
            "(rollback runs inside the queued operation, not in the caller's catch, the same pattern proven above " +
            "for replaceAllProfiles)",
        async () => {
            const fakeDb = createFakeConnection()
            fakeDb.seed("settings", [{ category: "general", key: "scenario", value: "URA" }])
            fakeDb.setFailOnInsertAt(1)
            const dm = managerWithFakeDb(fakeDb)

            await expect(
                dm.saveSettingsBatch([
                    { category: "training", key: "a", value: 1 },
                    { category: "training", key: "b", value: 2 },
                ])
            ).rejects.toThrow("simulated insert failure")

            expect(fakeDb.getSentSql().filter((s) => s.toUpperCase() === "ROLLBACK")).toHaveLength(1)
            expect(fakeDb.isInTransaction()).toBe(false)
            expect(fakeDb.getCommitted("settings")).toEqual([{ category: "general", key: "scenario", value: "URA" }])

            await dm.saveSettingsBatch([{ category: "general", key: "scenario", value: "Unity Cup" }])
            expect(fakeDb.getCommitted("settings")).toEqual([{ category: "general", key: "scenario", value: "Unity Cup" }])
        }
    )
})
