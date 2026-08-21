// Tests for the Veteran identity runtime asset generator: synthetic fixtures for shape/validation,
// and the real committed data for the expanded outfit domain and byte-identity of the checked-in
// asset.
//
// Run: node --test scripts/generate-veteran-identity-data.test.mjs

import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { dirname, join } from "node:path"
import { buildPayload, generate, GenerateError, ASSET_PATH, SCHEMA_VERSION } from "./generate-veteran-identity-data.mjs"

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, "..")
const characters = JSON.parse(readFileSync(join(REPO, "src", "data", "characters.json"), "utf8"))
const outfits = JSON.parse(readFileSync(join(REPO, "src", "data", "character_outfits.json"), "utf8"))

test("buildPayload keys every character and carries its outfits in source order", () => {
    const payload = buildPayload(
        { Beta: {}, Alpha: {} },
        {
            Alpha: { outfits: [{ title: "First" }, { title: "Second" }] },
            Beta: { outfits: [{ title: "Only" }] },
        },
    )
    assert.deepEqual(Object.keys(payload.characters), ["Alpha", "Beta"])
    assert.deepEqual(payload.characters.Alpha.outfits, ["First", "Second"])
    assert.equal(payload.characterCount, 2)
    assert.equal(payload.outfitCount, 3)
    assert.equal(payload.schemaVersion, SCHEMA_VERSION)
})

test("a character with no outfit row still gets an empty list, never a missing key", () => {
    const payload = buildPayload({ Alpha: {}, Ghost: {} }, { Alpha: { outfits: [{ title: "First" }] } })
    assert.deepEqual(payload.characters.Ghost.outfits, [])
})

test("buildPayload rejects an empty title, a duplicate title, and an empty domain", () => {
    assert.throws(() => buildPayload({ A: {} }, { A: { outfits: [{ title: "  " }] } }), GenerateError)
    assert.throws(() => buildPayload({ A: {} }, { A: { outfits: [{ title: "X" }, { title: "X" }] } }), GenerateError)
    assert.throws(() => buildPayload({}, {}), GenerateError)
    assert.throws(() => buildPayload({ A: {} }, {}), GenerateError)
})

test("the committed domain covers every character and is materially bigger than base-card-only", () => {
    const payload = buildPayload(characters, outfits)
    assert.equal(payload.characterCount, Object.keys(characters).length)
    // The old hand-mirrored Kotlin snapshot carried 37 base outfits; anything at or below that means
    // the alternate costumes did not make it in and the roster reader is back to its old blind spot.
    assert.ok(payload.outfitCount > 37, `expected more than 37 outfits, got ${payload.outfitCount}`)
    for (const [name, entry] of Object.entries(payload.characters)) {
        assert.ok(Array.isArray(entry.outfits), `${name} has no outfit list`)
        assert.equal(new Set(entry.outfits).size, entry.outfits.length, `${name} repeats an outfit`)
    }
    const multi = Object.values(payload.characters).filter((c) => c.outfits.length > 1).length
    assert.ok(multi >= 20, `expected many characters to carry alternate costumes, got ${multi}`)
})

test("the live-observed alternate costumes are present with their exact canonical spelling", () => {
    const payload = buildPayload(characters, outfits)
    assert.ok(payload.characters["Symboli Rudolf"].outfits.includes("Emperor's Path"))
    assert.ok(payload.characters["Mihono Bourbon"].outfits.includes("CODE: ICING"))
    assert.ok(payload.characters["Taiki Shuttle"].outfits.includes("Wild Frontier"))
    // The base card sorts first, so plain-named presets still resolve off outfits[0].
    assert.equal(payload.characters["Taiki Shuttle"].outfits[0], "Wild Frontier")
    assert.equal(payload.characters["Mihono Bourbon"].outfits[0], "MB-19890425")
})

test("no two outfits of the SAME character are close enough to be confused", () => {
    // The whole point of conditioning the matcher on the resolved character: within one character
    // the costume titles are far apart, so a confident read cannot land on the wrong costume. This
    // pins that property of the DATA, independent of the Kotlin matcher's thresholds.
    const payload = buildPayload(characters, outfits)
    const norm = (s) =>
        s
            .normalize("NFD")
            .toLowerCase()
            .split("")
            .filter((c) => /[a-z0-9]/.test(c))
            .join("")
    const distance = (a, b) => {
        let prev = Array.from({ length: b.length + 1 }, (_, i) => i)
        for (let i = 1; i <= a.length; i++) {
            const curr = [i]
            for (let j = 1; j <= b.length; j++) curr.push(Math.min(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1)))
            prev = curr
        }
        return prev[b.length]
    }
    let worst = { score: 0, detail: "" }
    for (const [name, entry] of Object.entries(payload.characters)) {
        for (let i = 0; i < entry.outfits.length; i++) {
            for (let j = i + 1; j < entry.outfits.length; j++) {
                const a = norm(entry.outfits[i])
                const b = norm(entry.outfits[j])
                const score = 1 - distance(a, b) / Math.max(a.length, b.length)
                if (score > worst.score) worst = { score, detail: `${name}: "${entry.outfits[i]}" vs "${entry.outfits[j]}"` }
            }
        }
    }
    // The Kotlin matcher accepts at 0.68; staying under 0.60 leaves real headroom.
    assert.ok(worst.score < 0.6, `two costumes of one character are too similar (${worst.score.toFixed(3)}) - ${worst.detail}`)
})

test("the committed asset is byte-identical to a fresh generation", () => {
    assert.equal(readFileSync(ASSET_PATH, "utf8"), generate())
})

test("generation is deterministic across repeat runs", () => {
    assert.equal(generate(), generate())
})
