import { execFileSync } from "node:child_process"
import { mkdtempSync, writeFileSync, readFileSync, rmSync, existsSync, mkdirSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import process from "node:process"
import { createHash } from "node:crypto"
import {
    sanitizeLabel,
    bundleId,
    buildAdbPullArgs,
    remoteTelemetryPath,
    DEVICE_OUTCOMES_DIR,
    parseJsonl,
    fileMetadata,
    analyzeCorpus,
    buildManifest,
    MANIFEST_VERSION,
    TELEMETRY_FILENAMES,
} from "../collect.ts"

// ---- builders ----

function dec(o: { token?: string; seq?: number; scenario?: string; trainee?: string }): Record<string, unknown> {
    const r: Record<string, unknown> = { type: "decision_trace", v: 1, ts: 1, careerToken: o.token ?? "T|A", scenario: o.scenario ?? "Trackblazer", trainee: o.trainee ?? "Taiki" }
    if (o.seq !== undefined) r.seq = o.seq
    return r
}
function st(o: { token?: string; seq?: number; scenarioType?: string; trainee?: string }): Record<string, unknown> {
    const r: Record<string, unknown> = { type: "career_state", v: 1, ts: 1, identity: { careerToken: o.token ?? "T|A", trainee: o.trainee ?? "Taiki" }, scenario: { type: o.scenarioType ?? "trackblazer" } }
    if (o.seq !== undefined) r.seq = o.seq
    return r
}
const toJsonl = (recs: unknown[]): string => recs.map((r) => JSON.stringify(r)).join("\n") + "\n"

// ---- label sanitizing + bundle naming ----

describe("label + bundle naming", () => {
    it("22. sanitizes a safe label to a path segment", () => {
        expect(sanitizeLabel("Taiki TB run1")).toBe("Taiki-TB-run1")
        expect(sanitizeLabel("URA_special.v2")).toBe("URA_special.v2")
    })
    it("22b. rejects mid-token traversal and empty labels; strips shell metacharacters to a safe slug", () => {
        expect(() => sanitizeLabel("a/../b")).toThrow() // ".." survives in the middle -> rejected
        expect(() => sanitizeLabel("   ")).toThrow()
        expect(() => sanitizeLabel("...")).toThrow()
        // Shell metacharacters are stripped, never executed; the result is always a safe path segment.
        const slug = sanitizeLabel("$(rm -rf)")
        expect(/^[A-Za-z0-9._-]+$/.test(slug)).toBe(true)
        expect(slug).not.toContain("..")
    })
    it("deterministic UTC bundle id (date only, no absolute path)", () => {
        expect(bundleId("2026-08-10T12:34:56.789Z", "Taiki TB")).toBe("2026-08-10-Taiki-TB")
        expect(() => bundleId("not-a-date", "x")).toThrow()
    })
})

// ---- adb command construction (pull-only, no root) ----

describe("adb command construction", () => {
    it("26. --device adds -s <serial>", () => {
        expect(buildAdbPullArgs("emulator-5554", remoteTelemetryPath("decisions.jsonl"), "/tmp/d.jsonl")).toEqual([
            "-s",
            "emulator-5554",
            "pull",
            `${DEVICE_OUTCOMES_DIR}/decisions.jsonl`,
            "/tmp/d.jsonl",
        ])
    })
    it("27. no device serial uses plain adb pull", () => {
        expect(buildAdbPullArgs(null, remoteTelemetryPath("career_state.jsonl"), "/tmp/s.jsonl")).toEqual(["pull", `${DEVICE_OUTCOMES_DIR}/career_state.jsonl`, "/tmp/s.jsonl"])
    })
    it("28/29. the command is always pull-only and never contains root/su/chmod/chown/rm/shell", () => {
        for (const args of [buildAdbPullArgs("dev", "/r", "/l"), buildAdbPullArgs(null, "/r", "/l")]) {
            expect(args).toContain("pull")
            for (const forbidden of ["su", "root", "chmod", "chown", "rm", "shell", "cp", "run-as"]) expect(args).not.toContain(forbidden)
        }
    })
})

// ---- parse + hashing ----

describe("parse + hashing", () => {
    it("2/3. byte size + exact-byte SHA-256", () => {
        const bytes = Buffer.from(toJsonl([dec({ seq: 1 })]), "utf8")
        const parsed = parseJsonl(bytes)
        const meta = fileMetadata("decisions.jsonl", bytes, parsed)
        expect(meta.byteSize).toBe(bytes.length)
        expect(meta.sha256).toBe(createHash("sha256").update(bytes).digest("hex"))
    })
    it("4/15. record + deterministic malformed-line counts (never silently dropped)", () => {
        const bytes = Buffer.from(`${JSON.stringify(dec({ seq: 1 }))}\n{bad json\n\n${JSON.stringify(dec({ seq: 2 }))}\n`, "utf8")
        const parsed = parseJsonl(bytes)
        expect(parsed.recordCount).toBe(2)
        expect(parsed.malformedLineCount).toBe(1)
        expect(parsed.malformedLineNumbers).toEqual([2])
    })
})

// ---- corpus analysis ----

describe("corpus analysis (careerToken + seq only)", () => {
    it("5/6/7. per-token decision/state counts + shared/decision-only/state-only seqs", () => {
        const decs = [dec({ token: "T|A", seq: 1 }), dec({ token: "T|A", seq: 2 }), dec({ token: "T|A", seq: 3 })]
        const sts = [st({ token: "T|A", seq: 2 }), st({ token: "T|A", seq: 3 }), st({ token: "T|A", seq: 4 })]
        const a = analyzeCorpus(decs, sts, null)
        const t = a.tokens.find((x) => x.careerToken === "T|A")!
        expect(t.decisionRecordCount).toBe(3)
        expect(t.stateRecordCount).toBe(3)
        expect(t.sharedSeqCount).toBe(2) // seqs 2,3
        expect(t.decisionOnlySeqCount).toBe(1) // seq 1
        expect(t.stateOnlySeqCount).toBe(1) // seq 4
    })

    it("8. seq-less rows counted, never joined", () => {
        const a = analyzeCorpus([dec({ seq: 1 }), dec({})], [st({ seq: 1 }), st({})], null)
        expect(a.global.seqLessDecisionCount).toBe(1)
        expect(a.global.seqLessStateCount).toBe(1)
    })

    it("10/11. same seq in two careers stays isolated (no cross-career, no turn join)", () => {
        const a = analyzeCorpus([dec({ token: "T|A", seq: 5 }), dec({ token: "T|B", seq: 5 })], [st({ token: "T|A", seq: 5 })], null)
        const A = a.tokens.find((x) => x.careerToken === "T|A")!
        const B = a.tokens.find((x) => x.careerToken === "T|B")!
        expect(A.sharedSeqCount).toBe(1) // T|A seq5 shared with its own state
        expect(B.sharedSeqCount).toBe(0) // T|B seq5 has no state, never borrows T|A's
        expect(a.global.pairedCareerTokenCount).toBe(1) // only T|A is in both streams
    })

    it("12/13/14. duplicate decision/state seqs counted; shared not double-counted", () => {
        const a = analyzeCorpus([dec({ seq: 1 }), dec({ seq: 1 }), dec({ seq: 2 })], [st({ seq: 1 }), st({ seq: 1 })], null)
        const t = a.tokens[0]
        expect(t.duplicateDecisionSeqCount).toBe(1) // seq 1 twice
        expect(t.duplicateStateSeqCount).toBe(1)
        expect(t.sharedSeqCount).toBe(1) // distinct seq 1 shared once despite duplicates
    })

    it("9/25. deterministic token/seq/scenario/trainee sorting + order-independent output", () => {
        const decs = [dec({ token: "T|B", seq: 2, scenario: "Unity Cup", trainee: "Z" }), dec({ token: "T|A", seq: 1, scenario: "Trackblazer", trainee: "A" })]
        const sts = [st({ token: "T|A", seq: 1, scenarioType: "trackblazer", trainee: "A" })]
        const a = analyzeCorpus(decs, sts, null)
        expect(a.tokens.map((t) => t.careerToken)).toEqual(["T|A", "T|B"]) // lexical
        expect(a.tokens[0].scenarios).toEqual(["Trackblazer", "trackblazer"].sort())
        // Shuffled input yields identical JSON.
        expect(JSON.stringify(analyzeCorpus([decs[1], decs[0]], sts, null))).toBe(JSON.stringify(a))
    })
})

// ---- manifest ----

describe("manifest", () => {
    it("1/19. manifest version + file presence (careers optional)", () => {
        const decBytes = Buffer.from(toJsonl([dec({ seq: 1 })]), "utf8")
        const stBytes = Buffer.from(toJsonl([st({ seq: 1 })]), "utf8")
        const files = [fileMetadata("decisions.jsonl", decBytes, parseJsonl(decBytes)), fileMetadata("career_state.jsonl", stBytes, parseJsonl(stBytes))]
        const analysis = analyzeCorpus([dec({ seq: 1 })], [st({ seq: 1 })], null)
        const m = buildManifest({
            label: "run1",
            sanitizedLabel: "run1",
            bundleId: "2026-08-10-run1",
            collectedAtUtc: "2026-08-10T00:00:00.000Z",
            source: { mode: "fromDir", deviceSerial: null, deviceTelemetryPath: null, fromDir: "pulled" },
            files,
            analysis,
            totalByteSize: decBytes.length + stBytes.length,
        })
        expect(m.manifestVersion).toBe(MANIFEST_VERSION)
        expect(m.filePresence).toEqual({ "decisions.jsonl": true, "career_state.jsonl": true, "careers.jsonl": false, "shadow_advisor.jsonl": false })
        expect(m.summary.totalByteSize).toBe(decBytes.length + stBytes.length)
        expect(m.collectionMode).toBe("fromDir")
    })

    it("2/19. an optional shadow_advisor.jsonl is hashed, counted, and marked present when supplied", () => {
        const decBytes = Buffer.from(toJsonl([dec({ seq: 1 })]), "utf8")
        const stBytes = Buffer.from(toJsonl([st({ seq: 1 })]), "utf8")
        // One valid shadow row and one malformed line, to prove hashing + malformed counting cover the stream.
        const shadowBytes = Buffer.from(`${JSON.stringify({ type: "shadow_advisor", v: 1, seq: 1, careerToken: "T", status: "notApplicable", reasons: [], limitations: [] })}\n{ not json\n`, "utf8")
        const shadowMeta = fileMetadata("shadow_advisor.jsonl", shadowBytes, parseJsonl(shadowBytes))
        expect(shadowMeta.recordCount).toBe(1)
        expect(shadowMeta.malformedLineCount).toBe(1)
        expect(shadowMeta.sha256).toMatch(/^[0-9a-f]{64}$/)

        const files = [fileMetadata("decisions.jsonl", decBytes, parseJsonl(decBytes)), fileMetadata("career_state.jsonl", stBytes, parseJsonl(stBytes)), shadowMeta]
        const m = buildManifest({
            label: "run1",
            sanitizedLabel: "run1",
            bundleId: "2026-08-10-run1",
            collectedAtUtc: "2026-08-10T00:00:00.000Z",
            source: { mode: "fromDir", deviceSerial: null, deviceTelemetryPath: null, fromDir: "pulled" },
            files,
            analysis: analyzeCorpus([dec({ seq: 1 })], [st({ seq: 1 })], null),
            totalByteSize: decBytes.length + stBytes.length + shadowBytes.length,
        })
        expect(m.filePresence).toEqual({ "decisions.jsonl": true, "career_state.jsonl": true, "careers.jsonl": false, "shadow_advisor.jsonl": true })
        // The manifest carries the shadow file's hash + malformed count; the join analysis ignores it (it is not a
        // decision/state/careers record), and an archive without it stays valid (the case above).
        expect(m.files.find((f) => f.filename === "shadow_advisor.jsonl")).toEqual(shadowMeta)
    })
})

// ---- CLI (--from-dir + failure modes) ----

describe("CLI", () => {
    const SCRIPT = join(process.cwd(), "scripts/collect-telemetry.mjs")
    let root: string
    let src: string

    beforeEach(() => {
        root = mkdtempSync(join(tmpdir(), "collect-"))
        src = join(root, "pulled")
        mkdirSync(src)
        writeFileSync(join(src, "decisions.jsonl"), toJsonl([dec({ token: "T|A", seq: 1 }), dec({ token: "T|A", seq: 2 })]))
        writeFileSync(join(src, "career_state.jsonl"), toJsonl([st({ token: "T|A", seq: 1 }), st({ token: "T|A", seq: 2 })]))
    })
    afterEach(() => rmSync(root, { recursive: true, force: true }))

    function run(args: string[]): { code: number; stdout: string; stderr: string } {
        try {
            const stdout = execFileSync(process.execPath, [SCRIPT, ...args], { cwd: root, encoding: "utf8" })
            return { code: 0, stdout, stderr: "" }
        } catch (e) {
            const err = e as { status?: number; stdout?: string; stderr?: string }
            return { code: err.status ?? 1, stdout: err.stdout ?? "", stderr: err.stderr ?? "" }
        }
    }

    it("16/20/24. --from-dir copies exact bytes, leaves the source unchanged", () => {
        const before = readFileSync(join(src, "decisions.jsonl"))
        const { code } = run(["--from-dir", src, "--label", "run1"])
        expect(code).toBe(0)
        const bundle = join(root, "validation/corpus")
        const bundleDir = join(bundle, require("node:fs").readdirSync(bundle)[0])
        const archived = readFileSync(join(bundleDir, "decisions.jsonl"))
        expect(archived.equals(before)).toBe(true) // exact bytes
        expect(readFileSync(join(src, "decisions.jsonl")).equals(before)).toBe(true) // source untouched
        const manifest = JSON.parse(readFileSync(join(bundleDir, "manifest.json"), "utf8"))
        expect(manifest.files.find((f: { filename: string }) => f.filename === "decisions.jsonl").sha256).toBe(createHash("sha256").update(before).digest("hex"))
    })

    it("17/18. missing required decisions or state fails with no bundle", () => {
        rmSync(join(src, "decisions.jsonl"))
        const { code } = run(["--from-dir", src, "--label", "run1"])
        expect(code).toBe(2)
        expect(existsSync(join(root, "validation/corpus"))).toBe(true) // root may exist
        // no completed bundle dir
        const inner = existsSync(join(root, "validation/corpus")) ? require("node:fs").readdirSync(join(root, "validation/corpus")).filter((d: string) => !d.endsWith(".tmp")) : []
        expect(inner).toEqual([])
    })

    it("21. an existing destination is never overwritten", () => {
        expect(run(["--from-dir", src, "--label", "run1"]).code).toBe(0)
        const second = run(["--from-dir", src, "--label", "run1"]) // same UTC date + label -> same bundle id
        expect(second.code).toBe(2)
        expect(second.stderr).toContain("refusing to overwrite")
    })

    it("23. a collector-created temp is cleaned after a mid-run failure (ownership flag still allows it)", () => {
        rmSync(join(src, "career_state.jsonl")) // required missing -> acquireFiles throws after temp is created
        run(["--from-dir", src, "--label", "run1"])
        const corpus = join(root, "validation/corpus")
        const leftovers = existsSync(corpus) ? require("node:fs").readdirSync(corpus) : []
        expect(leftovers.filter((d: string) => d.endsWith(".tmp"))).toEqual([]) // its own temp was cleaned
    })

    it("F1-ATK2. a pre-existing temp path is refused, never deleted", () => {
        const date = new Date().toISOString().slice(0, 10)
        const tempDir = join(root, "validation/corpus", `${date}-run1.tmp`)
        mkdirSync(tempDir, { recursive: true })
        const marker = join(tempDir, "marker.txt")
        writeFileSync(marker, "keepme")
        const { code, stderr } = run(["--from-dir", src, "--label", "run1"])
        expect(code).toBe(2)
        expect(stderr).toContain("already exists")
        expect(readFileSync(marker, "utf8")).toBe("keepme") // foreign temp NOT deleted
        expect(existsSync(join(root, "validation/corpus", `${date}-run1`))).toBe(false) // no final bundle
        expect(existsSync(join(src, "decisions.jsonl"))).toBe(true) // source unchanged
    })

    it("F1-ATK1. --from-dir equal to the temp path is refused and the source is untouched", () => {
        const date = new Date().toISOString().slice(0, 10)
        const tempDir = join(root, "validation/corpus", `${date}-run1.tmp`)
        mkdirSync(tempDir, { recursive: true })
        writeFileSync(join(tempDir, "decisions.jsonl"), toJsonl([dec({ seq: 1 })]))
        writeFileSync(join(tempDir, "career_state.jsonl"), toJsonl([st({ seq: 1 })]))
        writeFileSync(join(tempDir, "marker.txt"), "keepme")
        const before = readFileSync(join(tempDir, "decisions.jsonl"))
        const { code } = run(["--from-dir", tempDir, "--label", "run1"])
        expect(code).toBe(2)
        expect(readFileSync(join(tempDir, "decisions.jsonl")).equals(before)).toBe(true) // source bytes unchanged
        expect(readFileSync(join(tempDir, "marker.txt"), "utf8")).toBe("keepme")
        expect(existsSync(join(root, "validation/corpus", `${date}-run1`))).toBe(false) // no final bundle
    })

    it("F1. --from-dir containing the bundle destination is refused (containment guard)", () => {
        const corpusRoot = join(root, "validation/corpus")
        mkdirSync(corpusRoot, { recursive: true })
        writeFileSync(join(corpusRoot, "decisions.jsonl"), toJsonl([dec({ seq: 1 })]))
        writeFileSync(join(corpusRoot, "career_state.jsonl"), toJsonl([st({ seq: 1 })]))
        const before = readFileSync(join(corpusRoot, "decisions.jsonl"))
        expect(run(["--from-dir", corpusRoot, "--label", "run1"]).code).toBe(2)
        expect(readFileSync(join(corpusRoot, "decisions.jsonl")).equals(before)).toBe(true)
    })

    it("30/32. --from-dir cannot combine with --device", () => {
        expect(run(["--from-dir", src, "--label", "run1", "--device", "emulator-5554"]).code).toBe(2)
    })

    it("31. missing label exits nonzero", () => {
        expect(run(["--from-dir", src]).code).toBe(2)
    })

    it("22c. a mid-token path-traversal label is rejected before any archiving", () => {
        expect(run(["--from-dir", src, "--label", "a/../b"]).code).toBe(2)
    })

    it("33. adb failure (unreachable adb binary) gives a clear nonzero and no bundle", () => {
        const { code } = run(["--label", "run1", "--adb", join(root, "no-such-adb")])
        expect(code).toBe(2)
        const corpus = join(root, "validation/corpus")
        const done = existsSync(corpus) ? require("node:fs").readdirSync(corpus).filter((d: string) => !d.endsWith(".tmp")) : []
        expect(done).toEqual([])
    })

    it("28b. the only device command the CLI can execute is a pull-only adb argv", () => {
        // The single execFileSync device call uses buildAdbPullArgs (proven pull-only above); the CLI has no
        // other adb/shell invocation. Assert there is exactly one execFileSync and it targets buildAdbPullArgs.
        const cli = readFileSync(SCRIPT, "utf8")
        expect((cli.match(/execFileSync\(/g) ?? []).length).toBe(1)
        expect(cli).toContain("buildAdbPullArgs(opts.device")
        expect(cli).not.toContain("adb shell")
    })
})
