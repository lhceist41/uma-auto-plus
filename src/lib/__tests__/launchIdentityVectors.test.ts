import { readFileSync } from "fs"
import { join } from "path"
import { LAUNCH_CRITICAL_CATEGORIES, LAUNCH_IDENTITY_EXCLUDED_KEYS, identityFromRows, stableHash } from "../launchConfig"

// Shared with LaunchIdentityHashTest.kt: the bot recomputes this hash from its own read of the
// settings and refuses a launch whose hash differs from the one this side verified, so both
// implementations must agree on every vector.

interface Vectors {
    categories: string[]
    excludedKeys: string[]
    stableHash: { name: string; input: string; hash: string }[]
    identity: { name: string; rows: Record<string, string>; hash: string; sameHashAs?: string }[]
}

const vectors: Vectors = JSON.parse(readFileSync(join(__dirname, "..", "__fixtures__", "launchIdentity.json"), "utf8"))

describe("launch identity golden vectors", () => {
    it("pin the hashed categories and excluded keys", () => {
        expect(LAUNCH_CRITICAL_CATEGORIES).toEqual(vectors.categories)
        expect(LAUNCH_IDENTITY_EXCLUDED_KEYS).toEqual(vectors.excludedKeys)
    })

    it("match the published FNV-1a values for plain ASCII", () => {
        const byInput = Object.fromEntries(vectors.stableHash.map((v) => [v.input, v.hash]))
        expect(byInput[""]).toBe("811c9dc5")
        expect(byInput["a"]).toBe("e40c292c")
        expect(byInput["foobar"]).toBe("bf9cf968")
    })

    it.each(vectors.stableHash.map((v) => [v.name, v] as const))("stableHash: %s", (_name, v) => {
        expect(stableHash(v.input)).toBe(v.hash)
    })

    it.each(vectors.identity.map((v) => [v.name, v] as const))("identity hash: %s", (_name, v) => {
        expect(identityFromRows(v.rows).hash).toBe(v.hash)
    })

    it("cover what the bot's check depends on", () => {
        const byName = Object.fromEntries(vectors.identity.map((v) => [v.name, v]))
        for (const v of vectors.identity) {
            if (v.sameHashAs) expect(v.hash).toBe(byName[v.sameHashAs].hash)
        }
        expect(byName["the same rows with reuse flipped to true"].hash).not.toBe(byName["launch-critical rows in shuffled order, with a JSON-looking value"].hash)
        const surrogate = vectors.identity.find((v) => Object.values(v.rows).some((value) => /[\ud800-\udbff]/.test(value)) && Object.keys(v.rows).some((key) => /[\ud800-\udbff]/.test(key)))
        expect(surrogate).toBeDefined()
    })
})

describe("the bot hashes the rows this side reads back", () => {
    const repo = join(__dirname, "..", "..", "..")
    const read = (relative: string) => readFileSync(join(repo, relative), "utf8").replace(/\r\n/g, "\n")

    // Both sides must hash the same rows, or a real split would hide behind a filter difference and an
    // honest launch would be refused. Read each side's query from its own source.
    function whereOf(body: string, pattern: RegExp, side: string): string {
        const match = body.match(pattern)
        if (!match) throw new Error(`the ${side} settings query was not found`)
        return match[1].replace(/\s+/g, " ").trim()
    }

    it("uses the same WHERE clause on both sides", () => {
        const database = read("src/lib/database.ts")
        const snapshot = database.slice(database.indexOf("async loadSettingsRowsSnapshot("), database.indexOf("\n    }\n", database.indexOf("async loadSettingsRowsSnapshot(")))
        expect(database).toContain('private TABLE_SETTINGS = "settings"')
        const appWhere = whereOf(snapshot, /SELECT category, key, value FROM \$\{this\.TABLE_SETTINGS\} (WHERE [^`]*)`/, "app")

        const startModule = read("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt")
        const launchRead = startModule.slice(startModule.indexOf("private fun readLaunchSnapshot("), startModule.indexOf("\n    }\n", startModule.indexOf("private fun readLaunchSnapshot(")))
        const botWhere = whereOf(launchRead, /"SELECT category, key, value FROM settings (WHERE [^"]*)"/, "bot")

        expect(botWhere).toBe(appWhere)
    })
})
