import { execFileSync } from "node:child_process"
import { readFileSync } from "node:fs"
import { join } from "node:path"
import process from "node:process"

// Regression guard for the CI portability defect where the Jest job ran on Node 20.
//
// The scripts/*.mjs CLIs (generate-racing-plan, compile-master-data, shadow-advisor, collect-telemetry)
// import TypeScript .ts source directly. Node executes those imports only from v22.18 / v23.6 onward,
// where TypeScript type-stripping is on by default; on v20 they throw ERR_UNKNOWN_FILE_EXTENSION. The
// generatorAdoption / compiler / shadowAdvisorEvaluation / telemetryCorpus suites spawn those CLIs, so
// they pass on a newer local Node but failed on the Node-20 CI pin. This file makes that contract
// explicit: it proves the running Node can strip .ts, and asserts the CI Jest job stays above the floor.

const REPO_ROOT = process.cwd()

// 22.18.0 and 23.6.0 are the first releases that strip TypeScript types without a flag. Node 20 cannot
// import a .ts file at all. Anything the CI Jest job pins must clear this major.
const MIN_NODE_MAJOR = 22

function majorOf(spec: string): number {
    const m = spec.match(/(\d+)/)
    return m ? Number(m[1]) : NaN
}

describe("script toolchain node contract", () => {
    it("the running Node can import .ts source from a spawned .mjs CLI", () => {
        // No args makes the generator exit non-zero on its own usage check, but only AFTER it has
        // imported its .ts modules. An incapable Node instead throws ERR_UNKNOWN_FILE_EXTENSION during
        // that import, before any usage check runs. Assert on stderr, not the exit code.
        const generator = join(REPO_ROOT, "scripts/generate-racing-plan.mjs")
        let stderr = ""
        try {
            execFileSync(process.execPath, [generator], { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] })
        } catch (err) {
            const e = err as { stderr?: string }
            stderr = e.stderr ?? ""
        }
        expect(stderr).not.toContain("ERR_UNKNOWN_FILE_EXTENSION")
        expect(stderr).not.toContain('Unknown file extension ".ts"')
    })

    it("the CI Jest job pins a Node that can strip TypeScript types", () => {
        const ci = readFileSync(join(REPO_ROOT, ".github/workflows/ci.yml"), "utf8")
        // Locate the job block that runs `yarn jest` and read the Node version pinned above it. That is
        // the job whose spawned CLIs import .ts, so it is the one that must stay on a capable Node.
        const jestIdx = ci.indexOf("yarn jest")
        expect(jestIdx).toBeGreaterThan(-1)
        const pins = [...ci.slice(0, jestIdx).matchAll(/node-version:\s*'([^']+)'/g)]
        expect(pins.length).toBeGreaterThan(0)
        const jestJobNode = pins[pins.length - 1][1]
        expect(majorOf(jestJobNode)).toBeGreaterThanOrEqual(MIN_NODE_MAJOR)
    })
})
