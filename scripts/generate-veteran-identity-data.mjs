// Generates the compact Veteran identity runtime asset the native roster reader snaps its noisy
// `Umamusume Details` header OCR onto. It is a deterministic function of the committed authoritative
// data (src/data/characters.json + src/data/character_outfits.json), NOT of any live source, so the
// root files stay the single authority and this asset is a strip-down of what native runtime needs:
// the playable character names, and each character's own outfit titles.
//
// Why per-character rather than one flat outfit list: the matcher scores a read against the resolved
// character's costumes only. Measured on the committed domain, the worst similarity between two
// costumes of the SAME character is 0.44, while two costumes of DIFFERENT characters reach 0.73
// ("Down the Line" vs "Off the Line"). A flat list has to separate the 0.73 pair; a conditioned one
// never sees it.
//
// Usage:
//   node scripts/generate-veteran-identity-data.mjs            # write the asset
//   node scripts/generate-veteran-identity-data.mjs --check    # verify the committed asset is current (exit 1 if stale)
//
// Determinism: sorted character keys, outfits in source order (ascending card id, base card first),
// fixed top-level key order, two-space JSON, trailing newline. No timestamps, no absolute paths,
// no network.

import { readFileSync, writeFileSync, renameSync } from "node:fs"
import { fileURLToPath, pathToFileURL } from "node:url"
import { dirname, join } from "node:path"

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, "..")
const CHARACTERS_PATH = join(REPO, "src", "data", "characters.json")
const OUTFITS_PATH = join(REPO, "src", "data", "character_outfits.json")
const ASSET_PATH = join(REPO, "android", "app", "src", "main", "assets", "veteran_identity.json")

/** Bumped only when the native reader's expected shape changes. */
const SCHEMA_VERSION = 1

/** Where the outfit titles come from, carried into the asset so a pulled scan's evidence names its
 * own identity domain instead of an anonymous list. */
const OUTFIT_SOURCE = "gametora character-cards title_en_gl"

/** A hard-validation failure in the input data. */
class GenerateError extends Error {
    constructor(message) {
        super(message)
        this.name = "GenerateError"
    }
}

/**
 * Builds the asset payload from the two committed data files.
 *
 * @param {Record<string, unknown>} characters The parsed characters.json object (keys are the names).
 * @param {Record<string, {name?: string, outfits?: {title?: string}[]}>} outfits The parsed character_outfits.json object.
 * @returns {object} The payload, ready to serialize.
 */
export function buildPayload(characters, outfits) {
    const names = Object.keys(characters).sort()
    if (names.length === 0) throw new GenerateError("characters.json has no characters")

    const out = {}
    let outfitCount = 0
    for (const name of names) {
        const record = outfits[name]
        // A character with no outfit row is a data gap, not a runtime error: it still belongs in the
        // character domain (so the name resolves) and simply has no costume to match against, which
        // leaves its outfit unresolved fail-closed. Emitting an empty list says that explicitly.
        const titles = []
        for (const outfit of record?.outfits ?? []) {
            const title = typeof outfit?.title === "string" ? outfit.title.trim() : ""
            if (!title) throw new GenerateError(`character "${name}" has an outfit with no title`)
            if (titles.includes(title)) throw new GenerateError(`character "${name}" lists the outfit "${title}" twice`)
            titles.push(title)
        }
        outfitCount += titles.length
        out[name] = { outfits: titles }
    }
    if (outfitCount === 0) throw new GenerateError("no outfit titles at all; character_outfits.json is empty or unreadable")

    return { schemaVersion: SCHEMA_VERSION, outfitSource: OUTFIT_SOURCE, characterCount: names.length, outfitCount, characters: out }
}

/** Reads the committed inputs and returns the asset text, trailing newline included. */
export function generate() {
    const characters = JSON.parse(readFileSync(CHARACTERS_PATH, "utf8"))
    const outfits = JSON.parse(readFileSync(OUTFITS_PATH, "utf8"))
    return `${JSON.stringify(buildPayload(characters, outfits), null, 2)}\n`
}

function main(argv) {
    const check = argv.includes("--check")
    const text = generate()
    if (check) {
        let current = null
        try {
            current = readFileSync(ASSET_PATH, "utf8")
        } catch {
            console.error(`veteran_identity.json missing at ${ASSET_PATH}; run without --check to generate it`)
            return 1
        }
        if (current !== text) {
            console.error("veteran_identity.json is stale; re-run scripts/generate-veteran-identity-data.mjs")
            return 1
        }
        console.log("veteran_identity.json is up to date")
        return 0
    }
    // Atomic write: write a sibling temp then rename over the target.
    const tmp = `${ASSET_PATH}.tmp`
    writeFileSync(tmp, text, "utf8")
    renameSync(tmp, ASSET_PATH)
    const payload = JSON.parse(text)
    console.log(`wrote ${ASSET_PATH}: ${payload.characterCount} characters, ${payload.outfitCount} outfits`)
    return 0
}

// Only run as a CLI, not when imported by the test.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    try {
        process.exit(main(process.argv.slice(2)))
    } catch (e) {
        console.error(e instanceof GenerateError ? `generate error: ${e.message}` : e)
        process.exit(2)
    }
}

export { GenerateError, ASSET_PATH, SCHEMA_VERSION, OUTFIT_SOURCE }
