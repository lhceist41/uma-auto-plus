import { readFileSync, readdirSync, statSync } from "fs"
import { join, relative } from "path"

// settings.db has one owner, the native SettingsDatabase module. A second SQLite copy in the app
// process cannot see the owner's locks and can split the settings into two views, so no JS SQLite
// library may come back.

const repo = join(__dirname, "..", "..", "..")

function sourceFiles(dir: string): string[] {
    return readdirSync(dir).flatMap((name) => {
        const path = join(dir, name)
        if (statSync(path).isDirectory()) return sourceFiles(path)
        return /\.(ts|tsx|js|jsx)$/.test(name) ? [path] : []
    })
}

describe("settings.db has one owner", () => {
    it("no source file imports expo-sqlite", () => {
        const importers = sourceFiles(join(repo, "src"))
            .filter((file) => !file.endsWith("settingsDbOwnership.test.ts"))
            .filter((file) => /(?:from|import)\s+["']expo-sqlite["']|require\(\s*["']expo-sqlite["']\s*\)|jest\.mock\(\s*["']expo-sqlite["']/.test(readFileSync(file, "utf8")))
            .map((file) => relative(repo, file))
        expect(importers).toEqual([])
    })

    it("package.json depends on no JS SQLite library, and the lockfile resolves none", () => {
        const pkg = JSON.parse(readFileSync(join(repo, "package.json"), "utf8"))
        const deps = { ...pkg.dependencies, ...pkg.devDependencies, ...pkg.peerDependencies, ...pkg.optionalDependencies }
        expect(Object.keys(deps).filter((name) => /sqlite/i.test(name))).toEqual([])
        expect(readFileSync(join(repo, "yarn.lock"), "utf8")).not.toMatch(/^"?expo-sqlite@/m)
    })

    it("database.ts reaches the file only through the native module wrapper", () => {
        const database = readFileSync(join(repo, "src/lib/database.ts"), "utf8")
        expect(database).toContain('import { settingsDb, SettingsDb } from "./settingsDb"')
        expect(readFileSync(join(repo, "src/lib/settingsDb.ts"), "utf8")).toContain("NativeModules.SettingsDatabase")
    })
})
