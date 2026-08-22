// Extracts the game's own support-card tables from an installed master.mdb into a committed, offline
// data file the DeckLab shadow deck advisor reads. Read-only against the database, deterministic
// output, no network.
//
// What is being extracted:
//   support_card_data            id -> chara, rarity, support type, effect table, unique effect
//   support_card_effect_table    the per-effect value curve, keyed by level threshold
//   support_card_unique_effect   the unique perk and the level it unlocks at
//   support_card_limit           rarity -> the level cap at each limit-break step
//   support_card_group           the member characters of a Group card
//   single_mode_hint_gain        the skills a card can hint
//   single_mode_restrict_support cards a scenario forbids
//   single_mode_special_chara    the characters a scenario treats as its own
//   single_mode_scenario         the stat cap bonus each scenario grants over the shared base
//   text_data category 6         chara_id -> English character name
//   text_data category 150       support_card_id -> English card title
//   text_data category 151       effect type -> English effect name
//   text_data category 155       support_card_id -> the English unique-effect line, condition included
//   text_data category 119       scenario_id -> the scenario title
//
// Two enum mappings in here were derived rather than assumed, because the obvious guess is wrong:
//
//   command_id -> training type. The natural reading (101 Speed, 102 Stamina, 103 Power, 105 Guts)
//   does not hold. single_mode_training_effect states the stats each command actually raises, and the
//   profiles are unambiguous: 102 raises Power and Stamina (Power training), 103 raises Guts, Speed
//   and Power (Guts training), 105 raises Stamina and Guts (Stamina training). The mapping below is
//   the one the training-effect table proves, and the generator re-proves it on every run.
//
//   support_card_effect_table thresholds. A row carries an init value and one column per five levels,
//   with -1 meaning "no change here". The value at a level is the newest threshold at or below it,
//   falling back to init. Nothing scales linearly, so nothing here interpolates.
//
// Usage:
//   node scripts/generate-support-card-data.mjs --db <path-to-master.mdb>
//   node scripts/generate-support-card-data.mjs --db <path> --check   # exit 1 if the committed file is stale
//
// Determinism: ids ascending, effects by type ascending, skills ascending, fixed key order, two-space
// JSON, trailing newline. No timestamps, no absolute paths.

import { readFileSync, writeFileSync, renameSync, existsSync } from "node:fs"
import { DatabaseSync } from "node:sqlite"
import { fileURLToPath, pathToFileURL } from "node:url"
import { dirname, join } from "node:path"

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, "..")
const OUT_PATH = join(REPO, "src", "data", "support_cards.json")

export const SUPPORT_CARD_SCHEMA = "deck_lab_support_cards"
export const SUPPORT_CARD_SCHEMA_VERSION = 2

const TEXT_CATEGORY_CHARA_NAME = 6
const TEXT_CATEGORY_CARD_TITLE = 150
const TEXT_CATEGORY_EFFECT_NAME = 151
const TEXT_CATEGORY_UNIQUE_EFFECT = 155
const TEXT_CATEGORY_SCENARIO_NAME = 119

const SOURCE =
    "master.mdb: support_card_data, support_card_effect_table, support_card_unique_effect, support_card_limit, " +
    "support_card_group, single_mode_hint_gain, single_mode_restrict_support, single_mode_special_chara, " +
    "single_mode_training_effect, single_mode_scenario, text_data(categories 6, 119, 150, 151, 155)"

/** Rarity code -> the name the game prints on the card. */
export const RARITY_NAMES = { 1: "R", 2: "SR", 3: "SSR" }

/**
 * command_id -> training type, proven from single_mode_training_effect rather than assumed.
 * verifyCommandMapping() below re-derives it from the database on every run and refuses to write a
 * file if the shipped tables ever stop agreeing.
 */
export const COMMAND_TRAINING_TYPE = { 101: "Speed", 102: "Power", 103: "Guts", 105: "Stamina", 106: "Wit" }

/** The stat each command's own training raises the most, used only to re-prove COMMAND_TRAINING_TYPE. */
const TRAINING_EFFECT_TARGET_STAT = { 1: "Speed", 2: "Stamina", 3: "Power", 4: "Guts", 5: "Wit" }

/** support_card_type -> the deck role the card fills. 1 is a stat trainer, refined by command_id. */
const SUPPORT_CARD_TYPE_ROLE = { 1: null, 2: "Friend", 3: "Group" }

/** The effect-table columns, in level order. Index 0 is the level-1 value. */
export const EFFECT_LEVEL_THRESHOLDS = [1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50]
const EFFECT_COLUMNS = ["init", "limit_lv5", "limit_lv10", "limit_lv15", "limit_lv20", "limit_lv25", "limit_lv30", "limit_lv35", "limit_lv40", "limit_lv45", "limit_lv50"]

/**
 * Unique-effect type codes at or above this are a separate, conditional encoding that this repository
 * has not decoded. They are carried through as ids so a card is never silently treated as having no
 * unique perk, and they never become a number.
 */
export const UNDECODED_UNIQUE_EFFECT_TYPE_FLOOR = 100

class GenerateError extends Error {
    constructor(message) {
        super(message)
        this.name = "GenerateError"
    }
}

function requireTables(db) {
    const have = new Set(
        db
            .prepare("SELECT name FROM sqlite_master WHERE type='table'")
            .all()
            .map((r) => r.name),
    )
    const needed = [
        "support_card_data",
        "support_card_effect_table",
        "support_card_unique_effect",
        "support_card_limit",
        "support_card_group",
        "single_mode_hint_gain",
        "single_mode_restrict_support",
        "single_mode_special_chara",
        "single_mode_scenario",
        "single_mode_training_effect",
        "text_data",
    ]
    for (const table of needed) {
        if (!have.has(table)) throw new GenerateError(`master.mdb has no ${table} table; this is not a database this extractor can read`)
    }
}

/**
 * Re-derives command_id -> training type from single_mode_training_effect and checks it against the
 * constant. Each training raises its own stat by more than any other stat it touches, so the argmax
 * over the base URA profile names the training. If a future master.mdb renumbers the commands this
 * throws instead of writing a file that silently mislabels every card's type.
 */
export function verifyCommandMapping(db) {
    const rows = db.prepare("SELECT command_id, target_type, effect_value FROM single_mode_training_effect WHERE sub_id=1 AND result_state=2 AND scenario_id=1").all()
    const best = new Map()
    for (const row of rows) {
        const stat = TRAINING_EFFECT_TARGET_STAT[row.target_type]
        if (!stat) continue
        const current = best.get(row.command_id)
        if (!current || row.effect_value > current.value) best.set(row.command_id, { stat, value: row.effect_value })
    }
    for (const [commandId, expected] of Object.entries(COMMAND_TRAINING_TYPE)) {
        const derived = best.get(Number(commandId))
        if (!derived) throw new GenerateError(`single_mode_training_effect has no rows for command ${commandId}; the command mapping can no longer be proven`)
        if (derived.stat !== expected) {
            throw new GenerateError(`command ${commandId} raises ${derived.stat} the most but is mapped to ${expected}; the shipped tables disagree with this extractor`)
        }
    }
    return { ...COMMAND_TRAINING_TYPE }
}

function readText(db, category) {
    const out = new Map()
    for (const row of db.prepare('SELECT "index" AS idx, text FROM text_data WHERE category=?').all(category)) {
        out.set(row.idx, row.text)
    }
    return out
}

/** Card titles carry inline rich-text markup the game strips before display. */
function cleanTitle(raw) {
    return String(raw)
        .replace(/<[^>]*>/g, "")
        .replace(/\s+/g, " ")
        .trim()
}

/** The value curve for one effect: index 0 is level 1, then one entry per five levels, -1 = unchanged. */
function effectCurve(row) {
    return EFFECT_COLUMNS.map((column) => row[column])
}

export function buildPayload(db) {
    requireTables(db)
    verifyCommandMapping(db)

    const charaNames = readText(db, TEXT_CATEGORY_CHARA_NAME)
    const cardTitles = readText(db, TEXT_CATEGORY_CARD_TITLE)
    const effectNames = readText(db, TEXT_CATEGORY_EFFECT_NAME)
    const uniqueDescriptions = readText(db, TEXT_CATEGORY_UNIQUE_EFFECT)
    const scenarioNames = readText(db, TEXT_CATEGORY_SCENARIO_NAME)

    const effectTypes = {}
    for (const [index, text] of [...effectNames.entries()].sort((a, b) => a[0] - b[0])) effectTypes[String(index)] = text

    const levelCaps = {}
    for (const row of db.prepare("SELECT * FROM support_card_limit ORDER BY rarity").all()) {
        const name = RARITY_NAMES[row.rarity]
        if (!name) throw new GenerateError(`support_card_limit has rarity ${row.rarity}, which this extractor cannot name`)
        levelCaps[name] = [row.limit_0, row.limit_1, row.limit_2, row.limit_3, row.limit_4]
    }
    for (const name of Object.values(RARITY_NAMES)) {
        if (!levelCaps[name]) throw new GenerateError(`support_card_limit has no row for rarity ${name}`)
    }

    const effectsByTable = new Map()
    for (const row of db.prepare("SELECT * FROM support_card_effect_table ORDER BY id, type").all()) {
        if (!effectsByTable.has(row.id)) effectsByTable.set(row.id, [])
        effectsByTable.get(row.id).push({ type: row.type, curve: effectCurve(row) })
    }

    const uniqueById = new Map()
    for (const row of db.prepare("SELECT * FROM support_card_unique_effect ORDER BY id").all()) uniqueById.set(row.id, row)

    const hintsByCard = new Map()
    for (const row of db.prepare("SELECT support_card_id, hint_value_1 FROM single_mode_hint_gain ORDER BY support_card_id, hint_value_1").all()) {
        if (!hintsByCard.has(row.support_card_id)) hintsByCard.set(row.support_card_id, new Set())
        hintsByCard.get(row.support_card_id).add(row.hint_value_1)
    }

    const groupMembers = new Map()
    for (const row of db.prepare("SELECT support_card_id, chara_id FROM support_card_group ORDER BY support_card_id, chara_id").all()) {
        if (!groupMembers.has(row.support_card_id)) groupMembers.set(row.support_card_id, new Set())
        groupMembers.get(row.support_card_id).add(row.chara_id)
    }

    const restrictedByCard = new Map()
    for (const row of db.prepare("SELECT scenario_id, support_card_id FROM single_mode_restrict_support ORDER BY support_card_id, scenario_id").all()) {
        if (!restrictedByCard.has(row.support_card_id)) restrictedByCard.set(row.support_card_id, new Set())
        restrictedByCard.get(row.support_card_id).add(row.scenario_id)
    }

    const specialByScenario = new Map()
    for (const row of db.prepare("SELECT scenario_id, chara_id FROM single_mode_special_chara ORDER BY scenario_id, chara_id").all()) {
        if (!specialByScenario.has(row.scenario_id)) specialByScenario.set(row.scenario_id, new Set())
        specialByScenario.get(row.scenario_id).add(row.chara_id)
    }

    const cards = []
    const usedCharaIds = new Set()
    for (const row of db.prepare("SELECT * FROM support_card_data ORDER BY id").all()) {
        const rarity = RARITY_NAMES[row.rarity]
        if (!rarity) throw new GenerateError(`support card ${row.id} has rarity ${row.rarity}, which this extractor cannot name`)

        // R cards carry no epithet: text_data category 150 starts at the SR block, so an R card has no
        // title by design rather than by omission. They stay in the catalogue with a null title so the
        // file is the whole shipped card list and an inventory entry can never fail to have a home.
        const title = cardTitles.has(row.id) ? cleanTitle(cardTitles.get(row.id)) : null

        if (!(row.support_card_type in SUPPORT_CARD_TYPE_ROLE)) throw new GenerateError(`support card ${row.id} has unmapped support_card_type ${row.support_card_type}`)
        let supportType = SUPPORT_CARD_TYPE_ROLE[row.support_card_type]
        if (supportType === null) {
            supportType = COMMAND_TRAINING_TYPE[row.command_id] ?? null
            if (!supportType) throw new GenerateError(`support card ${row.id} is a training card with unmapped command ${row.command_id}`)
        }

        const effects = effectsByTable.get(row.effect_table_id) ?? []
        const unique = row.unique_effect_id ? uniqueById.get(row.unique_effect_id) : undefined
        if (row.unique_effect_id && !unique) throw new GenerateError(`support card ${row.id} names unique effect ${row.unique_effect_id}, which support_card_unique_effect does not have`)

        let uniqueEffect = null
        if (unique) {
            const decoded = []
            const undecodedTypes = []
            for (const [typeKey, valueKey] of [
                ["type_0", "value_0"],
                ["type_1", "value_1"],
            ]) {
                const type = unique[typeKey]
                if (!type) continue
                if (type >= UNDECODED_UNIQUE_EFFECT_TYPE_FLOOR) undecodedTypes.push(type)
                else decoded.push({ type, value: unique[valueKey] })
            }
            // The description is the game's own wording and names the condition an undecoded type
            // encodes, so an unresolved unique perk can still be explained without inventing a number.
            const description = uniqueDescriptions.has(row.id) ? cleanTitle(uniqueDescriptions.get(row.id)) : null
            uniqueEffect = { unlockLevel: unique.lv, description, effects: decoded, undecodedTypes: undecodedTypes.sort((a, b) => a - b) }
        }

        usedCharaIds.add(row.chara_id)

        cards.push({
            id: row.id,
            charaId: row.chara_id,
            title,
            rarity,
            supportType,
            effects,
            uniqueEffect,
            hintSkillIds: [...(hintsByCard.get(row.id) ?? [])].sort((a, b) => a - b),
            groupMemberCharaIds: groupMembers.has(row.id) ? [...groupMembers.get(row.id)].sort((a, b) => a - b) : null,
            restrictedScenarioIds: restrictedByCard.has(row.id) ? [...restrictedByCard.get(row.id)].sort((a, b) => a - b) : [],
        })
    }

    if (!cards.length) throw new GenerateError("no support cards were found; refusing to write an empty file")
    if (!cards.some((c) => c.title)) throw new GenerateError("no support card had an English title; text_data category 150 is not the title category in this database")

    // Scenario-special characters are carried for every scenario the table names, plus the characters
    // used by cards, so a consumer can resolve a name without reopening the database.
    for (const set of specialByScenario.values()) for (const id of set) usedCharaIds.add(id)
    const characters = {}
    for (const id of [...usedCharaIds].sort((a, b) => a - b)) characters[String(id)] = charaNames.get(id) ?? null

    // Scenarios come from single_mode_scenario, which is the list of scenarios the client can actually
    // play. text_data 119 holds titles for scenarios beyond that list; carrying only the playable ones
    // keeps a name that has no scenario behind it from looking like a target DeckLab can advise on.
    const restrictedByScenario = new Map()
    for (const [cardId, scenarioIds] of restrictedByCard) {
        for (const scenarioId of scenarioIds) {
            if (!restrictedByScenario.has(scenarioId)) restrictedByScenario.set(scenarioId, new Set())
            restrictedByScenario.get(scenarioId).add(cardId)
        }
    }
    const scenarios = db
        .prepare("SELECT id, max_speed, max_stamina, max_pow, max_guts, max_wiz FROM single_mode_scenario ORDER BY id")
        .all()
        .map((row) => ({
            id: row.id,
            // The title is the first line: two of the four are shipped as a two-line display string.
            // Two of the four titles are shipped as a two-line display string, and the break is stored
            // as the two characters backslash-n rather than as a real newline. The break becomes a
            // space rather than a cut: for Grand Concert the scenario name is on the second line, so
            // keeping only the first would throw away the half a reader recognises.
            name: cleanTitle(String(scenarioNames.get(row.id) ?? "").replace(/\\n|[\r\n]/g, " ")) || null,
            statCapBonus: { Speed: row.max_speed, Stamina: row.max_stamina, Power: row.max_pow, Guts: row.max_guts, Wit: row.max_wiz },
            specialCharaIds: [...(specialByScenario.get(row.id) ?? [])].sort((a, b) => a - b),
            restrictedCardIds: [...(restrictedByScenario.get(row.id) ?? [])].sort((a, b) => a - b),
        }))
    if (!scenarios.length) throw new GenerateError("single_mode_scenario is empty; refusing to write a file with no scenarios")

    return {
        schema: SUPPORT_CARD_SCHEMA,
        schemaVersion: SUPPORT_CARD_SCHEMA_VERSION,
        source: SOURCE,
        effectTypes,
        undecodedUniqueEffectTypeFloor: UNDECODED_UNIQUE_EFFECT_TYPE_FLOOR,
        effectLevelThresholds: EFFECT_LEVEL_THRESHOLDS,
        levelCapsByRarity: levelCaps,
        scenarios,
        characters,
        cards,
    }
}

/**
 * Serializes to pure ASCII, escaping every non-ASCII character as a \uXXXX sequence.
 *
 * Card titles are shipped game strings and carry characters this repository does not write by hand:
 * an em dash in the title "5:00 a.m.\u2014Right on Schedule", and the star, heart and chevron marks several
 * titles end with. Escaping them keeps the file byte-identical on every platform and keeps those
 * characters out of the repository, while JSON.parse still returns the exact shipped string. The
 * alternative, rewriting a title, would silently corrupt the key the owned-inventory match runs on.
 */
export function serializePayload(payload) {
    // Only code units above ASCII: JSON.stringify already escapes control characters inside strings,
    // and the newlines it emits between keys are structure, not content.
    const json = JSON.stringify(payload, null, 2).replace(/[\u007f-\uffff]/g, (ch) => "\\u" + ch.charCodeAt(0).toString(16).padStart(4, "0"))
    return `${json}\n`
}

function parseArgs(argv) {
    const opts = { db: null, check: false, out: OUT_PATH, help: false }
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i]
        if (arg === "--db") opts.db = argv[++i] ?? null
        else if (arg === "--out") opts.out = argv[++i] ?? OUT_PATH
        else if (arg === "--check") opts.check = true
        else if (arg === "--help" || arg === "-h") opts.help = true
        else throw new GenerateError(`unknown option ${arg}`)
    }
    return opts
}

const HELP = `generate-support-card-data - extract the support-card tables from an installed master.mdb

Options:
  --db <path>   Path to master.mdb (required).
  --out <path>  Output file (default src/data/support_cards.json).
  --check       Do not write; exit 1 if the committed file differs from what would be written.
  --help        This text.
`

function main(argv) {
    let opts
    try {
        opts = parseArgs(argv)
    } catch (err) {
        console.error(err.message)
        return 2
    }
    if (opts.help) {
        console.log(HELP)
        return 0
    }
    if (!opts.db) {
        console.error("--db <path-to-master.mdb> is required")
        return 2
    }
    if (!existsSync(opts.db)) {
        console.error(`no such file: ${opts.db}`)
        return 2
    }

    let text
    try {
        const db = new DatabaseSync(opts.db, { readOnly: true })
        try {
            text = serializePayload(buildPayload(db))
        } finally {
            db.close()
        }
    } catch (err) {
        console.error(err instanceof Error ? err.message : String(err))
        return 2
    }

    if (opts.check) {
        const current = existsSync(opts.out) ? readFileSync(opts.out, "utf8") : null
        if (current === text) {
            console.log(`${opts.out} is current`)
            return 0
        }
        console.error(`${opts.out} is stale; re-run without --check`)
        return 1
    }

    const tmp = `${opts.out}.tmp`
    writeFileSync(tmp, text, "utf8")
    renameSync(tmp, opts.out)
    console.log(`Wrote ${opts.out}`)
    return 0
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    process.exitCode = main(process.argv.slice(2))
}
