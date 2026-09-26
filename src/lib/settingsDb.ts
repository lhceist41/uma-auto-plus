import { NativeModules } from "react-native"

/**
 * The app's only way to `settings.db`: the Kotlin `SettingsDatabase` module, which owns the one
 * SQLite connection in the process. A second SQLite copy (as expo-sqlite was) cannot see the other
 * copy's locks, so the two can split the file into two views of the settings.
 *
 * Android binds a transaction to one thread, so a multi-statement write is one [SettingsDb.transaction]
 * call that the native side commits or rolls back as a whole; [SettingsDb.exec] takes one statement
 * per element because Android silently drops everything after a string's first statement.
 */
export type SqlValue = string | number | boolean | null

export type SqlRow = Record<string, string | number | null>

/** One statement, run once per row of arguments. `rows: []` runs it zero times; `[[]]` once without arguments. */
export interface TransactionStep {
    sql: string
    rows: SqlValue[][]
}

export interface SettingsDb {
    /** Resolves once the shared connection is open. */
    open(): Promise<void>
    /** Runs each statement (one per element) in one transaction. */
    exec(statements: string[]): Promise<void>
    run(sql: string, params?: SqlValue[]): Promise<{ changes: number; lastInsertRowId: number }>
    query<T = SqlRow>(sql: string, params?: SqlValue[]): Promise<T[]>
    /** All or nothing: every step's rows commit together, or none do. */
    transaction(steps: TransactionStep[]): Promise<void>
}

function nativeModule() {
    const module = NativeModules.SettingsDatabase
    if (!module) throw new Error("The SettingsDatabase native module is not available")
    return module
}

// The bridge turns an undefined array element into null; do it here so what is bound is explicit.
const bound = (values: (SqlValue | undefined)[]): SqlValue[] => values.map((v) => (v === undefined ? null : v))

export const settingsDb: SettingsDb = {
    open: async () => {
        await nativeModule().open()
    },
    exec: async (statements) => {
        await nativeModule().exec(statements)
    },
    run: (sql, params = []) => nativeModule().run(sql, bound(params)),
    query: (sql, params = []) => nativeModule().query(sql, bound(params)),
    transaction: async (steps) => {
        await nativeModule().transaction(steps.map((step) => ({ sql: step.sql, rows: step.rows.map(bound) })))
    },
}
