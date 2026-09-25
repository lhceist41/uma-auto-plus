import { spawnSync } from "node:child_process"
import { mkdtempSync, rmdirSync, unlinkSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join, resolve } from "node:path"

export function runParentLabCli(script: "retention" | "affinity", sources: Readonly<Record<string, string>>, args: readonly string[] = []) {
    const directory = mkdtempSync(join(tmpdir(), "parent-lab-cli-"))
    const files: string[] = []
    try {
        const inputs: string[] = []
        for (const [name, content] of Object.entries(sources)) {
            const path = join(directory, `${name}.jsonl`)
            writeFileSync(path, content, "utf8")
            files.push(path)
            inputs.push(`--${name}`, path)
        }
        return spawnSync(process.execPath, [resolve(process.cwd(), `scripts/parent-lab-${script}.mjs`), ...inputs, ...args], {
            encoding: "utf8", cwd: process.cwd(), timeout: 30_000,
        })
    } finally {
        for (const file of files) unlinkSync(file)
        rmdirSync(directory)
    }
}
