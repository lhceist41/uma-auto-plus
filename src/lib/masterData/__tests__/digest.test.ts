import { createHash } from "node:crypto"
import { readFileSync } from "node:fs"
import { join } from "node:path"
import process from "node:process"
import { canonicalJson, sha256Hex } from "../digest.ts"

const COMPILED_DIR = join(process.cwd(), "src/data/compiled")
const ARTIFACTS = ["manifest.json", "skills.json", "races.json"]

const nodeSha = (text: string): string => createHash("sha256").update(text, "utf8").digest("hex")

describe("sha256Hex matches node crypto", () => {
    it("agrees on empty, block-boundary, and multi-byte inputs", () => {
        const cases = ["", "a", "abc", "x".repeat(55), "x".repeat(56), "x".repeat(63), "x".repeat(64), "x".repeat(65), "x".repeat(119), "x".repeat(120), "x".repeat(1000)]
        // U+2014, a 2-byte, a 3-byte, an astral pair, and an unpaired surrogate (encoders emit U+FFFD).
        cases.push(String.fromCharCode(0x2014), "é", "中", "😀", "a\ud800b")
        for (const c of cases) expect(sha256Hex(c)).toBe(nodeSha(c))
    })

    it("agrees on every tracked compiled artifact", () => {
        for (const name of ARTIFACTS) {
            const text = readFileSync(join(COMPILED_DIR, name), "utf8")
            expect(sha256Hex(text)).toBe(nodeSha(text))
        }
    })
})

describe("canonicalJson reproduces the tracked artifact bytes", () => {
    // The app verifies hashes by re-serializing parsed JSON, so parse-then-serialize must be byte-exact.
    // If the compiler ever emits something that does not round-trip, this fails before the app does.
    it.each(ARTIFACTS)("%s round-trips through parse and serialize", (name) => {
        const text = readFileSync(join(COMPILED_DIR, name), "utf8")
        expect(canonicalJson(JSON.parse(text))).toBe(text)
    })

    it("re-escapes an em dash rather than emitting the literal code point", () => {
        const emDash = String.fromCharCode(0x2014)
        const text = canonicalJson({ name: `a${emDash}b` })
        expect(text).toContain("\\u2014")
        expect(text).not.toContain(emDash)
        expect(JSON.parse(text).name).toBe(`a${emDash}b`)
    })
})
