// Extracts the inheritance and trainee-growth evidence the game's own master.mdb carries into a
// committed, offline data file the STAM-2 Joint Build Budget Planner reads. Read-only against the
// database, deterministic output, no network.
//
// What is being extracted, and why each piece is here:
//
//   succession_factor         every Spark the game ships, as (factor_group_id, rarity) with the
//                             family it belongs to. 1139 rows across seven families.
//   succession_factor_effect  the effect LADDER behind each factor group: for a given accumulated
//                             level, exactly what the Spark gives. This is the table that turns
//                             "a 3-star Stamina Spark" into a number, and it is the reason ParentLab
//                             no longer has to describe inheritance with adjectives.
//   text_data category 147    factor_id -> English Spark name, which is how a decoded ladder joins
//                             onto the canonical factor names the Veteran reader already produces.
//   card_data                 per trainee-outfit growth rates (talent_speed/stamina/pow/guts/wiz).
//                             The percentages the career screen shows, exactly.
//   text_data categories 5, 6 card id -> outfit title, chara id -> character name.
//   card_rarity_data          per trainee-outfit starting stats and aptitude letters at each star
//                             level, plus the stat ceiling every card shares before any scenario
//                             bonus. An inheritance figure means nothing without the base it lands on.
//   single_mode_scenario      the stat cap bonus each scenario adds to that shared ceiling.
//   single_mode_training      the failure rate of each training facility, per level.
//   single_mode_training_effect
//                             the base outcome of one training, per scenario: the primary stat, the
//                             SECONDARY stats it also pays, the energy it costs and the skill points
//                             it hands over. This is the table that decides whether a Power-flex build
//                             is a real option or a story: Power training's own row carries a Stamina
//                             gain, and Grand Concert pays less of it than URA Finale does.
//
// What the ladder means, and the one thing about it that is NOT decoded:
//
//   succession_factor_effect is keyed by (factor_group_id, effect_id). effect_id is a level, and the
//   ladder length differs per family in a way that only makes sense if the level is the lineage's
//   accumulated star count for that factor, clamped to the ladder: blue stat factors run to 10,
//   unique factors to 4, white skill factors to 5, pink aptitude factors to 2, and the race and
//   scenario families to exactly 3, which is also their maximum star count. Four independently
//   shaped families agreeing on that reading is why it is the reading used.
//
//   HOW A LINEAGE'S STARS ADD UP TO THAT LEVEL IS NOT IN THIS DATABASE. Whether a grandparent's star
//   counts for as much as a parent's, and whether the sum is taken before or after clamping, is not
//   stated anywhere in master.mdb. So this generator ships the ladder and refuses to ship an
//   accumulation rule; the planner prices a range across the ladder and names the gap.
//
// Three things are re-proven on every run so a game patch that moves them fails loudly:
//
//   The five blue factor groups are the five training stats, in target_type order 1..5. Proven
//   against the shipped English names rather than assumed from the ids.
//   The ten pink factor groups carry the group id as their own target_type, and their names are the
//   ten aptitude names. Proven the same way.
//   Every ladder is contiguous from effect_id 1, with no gaps, for every (group, target_type) pair.
//   command_id -> training type is derived from the effect rows, never declared, because the mapping
//   is not the obvious one: 102 is Power and 105 is Stamina on the base board, while the camp board
//   runs in a different order again.
//
// One more thing that is NOT decoded and is not shipped as though it were: how a training facility's
// level scales the base gains above. single_mode_training_effect is keyed by command, not by the 1..5
// level the same command carries in single_mode_training, so the scaling is not in this database.
//
// Usage:
//   node scripts/generate-build-budget-data.mjs --db <path-to-master.mdb>
//   node scripts/generate-build-budget-data.mjs --db <path> --check   # exit 1 if committed file is stale
//
// Determinism: ids ascending, fixed key order, two-space JSON, trailing newline, pure-ASCII escaping.
// No timestamps, no absolute paths, no wall-clock.

import { readFileSync, writeFileSync, renameSync, existsSync } from "node:fs"
import { DatabaseSync } from "node:sqlite"
import { fileURLToPath, pathToFileURL } from "node:url"
import { dirname, join } from "node:path"

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, "..")
const OUT_PATH = join(REPO, "src", "data", "build_budget_data.json")

export const BUILD_BUDGET_DATA_SCHEMA = "build_budget_evidence"
export const BUILD_BUDGET_DATA_SCHEMA_VERSION = 1

const SOURCE = "master.mdb: succession_factor, succession_factor_effect, card_data, card_rarity_data, single_mode_scenario, single_mode_training, single_mode_training_effect, text_data(categories 5, 6, 147)"

/** text_data category holding the English Spark name, keyed by factor_id. */
const TEXT_CATEGORY_FACTOR_NAME = 147
/** text_data category holding the outfit title, keyed by card id. */
const TEXT_CATEGORY_CARD_TITLE = 5
/** text_data category holding the English character name, keyed by chara_id. */
const TEXT_CATEGORY_CHARA_NAME = 6

/**
 * succession_factor.factor_type -> the family the Veteran reader already classifies rows into.
 *
 * The reader pixel-classifies a factor card as STAT, APTITUDE, UNIQUE or WHITE, and everything that
 * is not one of the first three is WHITE. master.mdb splits WHITE further into skill, race and
 * scenario, which is genuine extra information, so both are carried: `family` joins onto the reader,
 * `subfamily` says which kind of white factor it actually is.
 */
const FACTOR_TYPES = {
    1: { family: "stat", subfamily: "stat" },
    2: { family: "aptitude", subfamily: "aptitude" },
    3: { family: "unique", subfamily: "unique" },
    4: { family: "white", subfamily: "skill" },
    5: { family: "white", subfamily: "race" },
    6: { family: "white", subfamily: "scenario" },
    7: { family: "white", subfamily: "bonus" },
}

/**
 * succession_factor_effect.target_type -> what the effect actually does.
 *
 * 1..5 and 61..65 are the two halves of a blue Spark and are deliberately kept apart: one raises the
 * stat the career starts at, the other raises the ceiling that stat can reach. Merging them would
 * make a Spark that lifts a cap look like a Spark that hands over points.
 */
const TARGET_TYPES = {
    1: { kind: "START_STAT", stat: "Speed" },
    2: { kind: "START_STAT", stat: "Stamina" },
    3: { kind: "START_STAT", stat: "Power" },
    4: { kind: "START_STAT", stat: "Guts" },
    5: { kind: "START_STAT", stat: "Wit" },
    6: { kind: "START_STAT", stat: null },
    11: { kind: "APTITUDE", aptitude: "Turf" },
    12: { kind: "APTITUDE", aptitude: "Dirt" },
    21: { kind: "APTITUDE", aptitude: "Front Runner" },
    22: { kind: "APTITUDE", aptitude: "Pace Chaser" },
    23: { kind: "APTITUDE", aptitude: "Late Surger" },
    24: { kind: "APTITUDE", aptitude: "End Closer" },
    31: { kind: "APTITUDE", aptitude: "Sprint" },
    32: { kind: "APTITUDE", aptitude: "Mile" },
    33: { kind: "APTITUDE", aptitude: "Medium" },
    34: { kind: "APTITUDE", aptitude: "Long" },
    41: { kind: "SKILL", stat: null },
    51: { kind: "UNDECODED", stat: null },
    61: { kind: "MAX_STAT", stat: "Speed" },
    62: { kind: "MAX_STAT", stat: "Stamina" },
    63: { kind: "MAX_STAT", stat: "Power" },
    64: { kind: "MAX_STAT", stat: "Guts" },
    65: { kind: "MAX_STAT", stat: "Wit" },
}

/** The five blue factor groups, in the order their target_type asserts. Re-proven, not assumed. */
const EXPECTED_BLUE_GROUPS = [
    { groupId: 1, name: "Speed", targetType: 1 },
    { groupId: 2, name: "Stamina", targetType: 2 },
    { groupId: 3, name: "Power", targetType: 3 },
    { groupId: 4, name: "Guts", targetType: 4 },
    { groupId: 5, name: "Wit", targetType: 5 },
]

/** The ten pink factor groups. Their group id IS their target_type, which is asserted below. */
const EXPECTED_PINK_GROUPS = [
    { groupId: 11, name: "Turf" },
    { groupId: 12, name: "Dirt" },
    { groupId: 21, name: "Front Runner" },
    { groupId: 22, name: "Pace Chaser" },
    { groupId: 23, name: "Late Surger" },
    { groupId: 24, name: "End Closer" },
    { groupId: 31, name: "Sprint" },
    { groupId: 32, name: "Mile" },
    { groupId: 33, name: "Medium" },
    { groupId: 34, name: "Long" },
]

/** card_data growth columns, in the order the career screen lists them. */
const GROWTH_COLUMNS = [
    ["talent_speed", "speed"],
    ["talent_stamina", "stamina"],
    ["talent_pow", "power"],
    ["talent_guts", "guts"],
    ["talent_wiz", "wit"],
]

/** card_rarity_data stat columns: what a career starts at, and the ceiling before any scenario bonus. */
const BASE_STAT_COLUMNS = [
    ["speed", "max_speed", "Speed"],
    ["stamina", "max_stamina", "Stamina"],
    ["pow", "max_pow", "Power"],
    ["guts", "max_guts", "Guts"],
    ["wiz", "max_wiz", "Wit"],
]

/** card_rarity_data aptitude columns -> the roster aptitude keys the rest of the repository uses. */
const APTITUDE_COLUMNS = [
    ["proper_distance_short", "sprint"],
    ["proper_distance_mile", "mile"],
    ["proper_distance_middle", "medium"],
    ["proper_distance_long", "long"],
    ["proper_ground_turf", "turf"],
    ["proper_ground_dirt", "dirt"],
    ["proper_running_style_nige", "front"],
    ["proper_running_style_senko", "pace"],
    ["proper_running_style_sashi", "late"],
    ["proper_running_style_oikomi", "end"],
]

/**
 * single_mode_training_effect.target_type -> what the effect moves.
 *
 * The stat half (1..5) is the same vocabulary succession_factor_effect uses, which is why the two
 * decodes join without a translation table. The rest is the training economy: energy, mood and the
 * skill points a training hands over alongside the stats.
 */
const TRAINING_TARGETS = {
    1: { kind: "STAT", stat: "Speed" },
    2: { kind: "STAT", stat: "Stamina" },
    3: { kind: "STAT", stat: "Power" },
    4: { kind: "STAT", stat: "Guts" },
    5: { kind: "STAT", stat: "Wit" },
    10: { kind: "ENERGY", stat: null },
    20: { kind: "MOOD", stat: null },
    30: { kind: "SKILL_POINTS", stat: null },
    101: { kind: "UNDECODED", stat: null },
}

/**
 * The two sets of stat training commands.
 *
 * BASE is the ordinary training board. CAMP is the higher-gain, higher-energy variant. They do NOT
 * share an id ordering, which is worth stating because it looks as though they should: the base set
 * runs Speed, Power, Guts, (gap), Stamina, Wit while the camp set runs Speed, Stamina, Power, Guts,
 * Wit. Neither ordering is assumed below; both are derived from the effect rows and then asserted to
 * cover five distinct stats.
 */
const BASE_TRAINING_COMMANDS = [101, 102, 103, 105, 106]
const CAMP_TRAINING_COMMANDS = [601, 602, 603, 604, 605]

/** single_mode_training.command_type 1 is a stat training; everything else is rest, outing or an event. */
const TRAINING_COMMAND_TYPE = 1

/**
 * Aptitude letters in the order the game's own rate tables ascend over ids 1..8.
 *
 * The same eight letters and the same ordering the race-survival extractor asserts against
 * race_proper_*_rate. card_rarity_data stores an aptitude as an index into this list.
 */
const APTITUDE_GRADES = ["G", "F", "E", "D", "C", "B", "A", "S"]

export class GenerateError extends Error {
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
    for (const table of ["succession_factor", "succession_factor_effect", "card_data", "card_rarity_data", "single_mode_scenario", "single_mode_training", "single_mode_training_effect", "text_data"]) {
        if (!have.has(table)) throw new GenerateError(`master.mdb has no ${table} table; this is not a database this extractor can read`)
    }
}

function textIndex(db, category) {
    const out = new Map()
    for (const row of db.prepare('SELECT "index" AS idx, text FROM text_data WHERE category = ?').all(category)) {
        out.set(row.idx, String(row.text))
    }
    return out
}

/**
 * Groups the raw effect rows into one ladder per (factor_group_id, target_type).
 *
 * Both value columns are kept. For a stat or aptitude effect only value_1 carries anything; for a
 * skill effect (target_type 41) value_1 is the skill id and value_2 is the level it is granted at,
 * and dropping either would make the row unusable.
 */
function buildLadders(db) {
    const rows = db.prepare("SELECT factor_group_id, effect_id, target_type, value_1, value_2 FROM succession_factor_effect ORDER BY factor_group_id, target_type, effect_id").all()
    const byGroup = new Map()
    for (const row of rows) {
        if (!TARGET_TYPES[row.target_type]) {
            throw new GenerateError(`succession_factor_effect carries target_type ${row.target_type}, which this extractor does not know how to describe`)
        }
        let group = byGroup.get(row.factor_group_id)
        if (!group) {
            group = new Map()
            byGroup.set(row.factor_group_id, group)
        }
        let ladder = group.get(row.target_type)
        if (!ladder) {
            ladder = []
            group.set(row.target_type, ladder)
        }
        if (row.effect_id !== ladder.length + 1) {
            throw new GenerateError(`ladder for factor group ${row.factor_group_id} target_type ${row.target_type} jumps to effect_id ${row.effect_id} after ${ladder.length} entries; the levels are not contiguous`)
        }
        ladder.push({ value1: row.value_1, value2: row.value_2 })
    }
    return byGroup
}

/** Re-proves the two family mappings this extractor hard-codes, against the shipped English names. */
export function verifyNamedGroups(groups) {
    for (const expected of EXPECTED_BLUE_GROUPS) {
        const group = groups.find((g) => g.groupId === expected.groupId)
        if (!group) throw new GenerateError(`blue factor group ${expected.groupId} is missing`)
        if (group.canonicalName !== expected.name) {
            throw new GenerateError(`blue factor group ${expected.groupId} is named "${group.canonicalName}", expected "${expected.name}"; the stat order has moved`)
        }
        const start = group.effects.find((e) => e.kind === "START_STAT")
        if (!start || start.targetType !== expected.targetType) {
            throw new GenerateError(`blue factor group ${expected.groupId} (${expected.name}) does not raise target_type ${expected.targetType}; the stat mapping has moved`)
        }
        const cap = group.effects.find((e) => e.kind === "MAX_STAT")
        if (!cap || cap.targetType !== expected.targetType + 60) {
            throw new GenerateError(`blue factor group ${expected.groupId} (${expected.name}) has no max-stat effect on target_type ${expected.targetType + 60}`)
        }
    }
    for (const expected of EXPECTED_PINK_GROUPS) {
        const group = groups.find((g) => g.groupId === expected.groupId)
        if (!group) throw new GenerateError(`pink factor group ${expected.groupId} is missing`)
        if (group.canonicalName !== expected.name) {
            throw new GenerateError(`pink factor group ${expected.groupId} is named "${group.canonicalName}", expected "${expected.name}"`)
        }
        const aptitude = group.effects.find((e) => e.kind === "APTITUDE")
        if (!aptitude || aptitude.targetType !== expected.groupId) {
            throw new GenerateError(`pink factor group ${expected.groupId} (${expected.name}) does not raise aptitude target_type ${expected.groupId}`)
        }
    }
}

export function buildFactorGroups(db) {
    const names = textIndex(db, TEXT_CATEGORY_FACTOR_NAME)
    const ladders = buildLadders(db)
    const factors = db.prepare("SELECT factor_id, factor_group_id, rarity, factor_type, effect_group_id FROM succession_factor ORDER BY factor_id").all()

    const byGroup = new Map()
    for (const factor of factors) {
        const family = FACTOR_TYPES[factor.factor_type]
        if (!family) throw new GenerateError(`succession_factor carries factor_type ${factor.factor_type}, which this extractor does not know`)
        const name = names.get(factor.factor_id)
        if (name === undefined) throw new GenerateError(`factor ${factor.factor_id} has no name in text_data category ${TEXT_CATEGORY_FACTOR_NAME}`)

        let group = byGroup.get(factor.factor_group_id)
        if (!group) {
            group = { groupId: factor.factor_group_id, factorType: factor.factor_type, ...family, canonicalName: name, factorIdsByRarity: {}, effectGroupIds: new Set() }
            byGroup.set(factor.factor_group_id, group)
        }
        if (group.canonicalName !== name) {
            throw new GenerateError(`factor group ${factor.factor_group_id} carries two names, "${group.canonicalName}" and "${name}"`)
        }
        if (group.factorType !== factor.factor_type) {
            throw new GenerateError(`factor group ${factor.factor_group_id} carries two factor types, ${group.factorType} and ${factor.factor_type}`)
        }
        if (group.factorIdsByRarity[factor.rarity] !== undefined) {
            throw new GenerateError(`factor group ${factor.factor_group_id} has two factors at rarity ${factor.rarity}`)
        }
        group.factorIdsByRarity[factor.rarity] = factor.factor_id
        group.effectGroupIds.add(factor.effect_group_id)
    }

    const out = []
    for (const group of [...byGroup.values()].sort((a, b) => a.groupId - b.groupId)) {
        const rarities = Object.keys(group.factorIdsByRarity)
            .map(Number)
            .sort((a, b) => a - b)
        for (let i = 0; i < rarities.length; i++) {
            if (rarities[i] !== i + 1) throw new GenerateError(`factor group ${group.groupId} has rarities ${rarities.join(",")}; they are not contiguous from 1`)
        }
        const ladder = ladders.get(group.groupId)
        const effects = []
        if (ladder) {
            for (const targetType of [...ladder.keys()].sort((a, b) => a - b)) {
                const entries = ladder.get(targetType)
                const meta = TARGET_TYPES[targetType]
                effects.push({
                    targetType,
                    kind: meta.kind,
                    stat: meta.stat ?? null,
                    aptitude: meta.aptitude ?? null,
                    levels: entries.length,
                    value1: entries.map((e) => e.value1),
                    value2: entries.map((e) => e.value2),
                })
            }
        }
        out.push({
            groupId: group.groupId,
            factorType: group.factorType,
            family: group.family,
            subfamily: group.subfamily,
            canonicalName: group.canonicalName,
            maxRarity: rarities.length ? rarities[rarities.length - 1] : 0,
            factorIds: rarities.map((r) => group.factorIdsByRarity[r]),
            effectGroupIds: [...group.effectGroupIds].sort((a, b) => a - b),
            effects,
        })
    }
    verifyNamedGroups(out)
    return out
}

/**
 * Per trainee-outfit growth rates.
 *
 * These are the percentages the career screen shows. The game distributes exactly thirty points of
 * growth across the five stats for every playable trainee outfit, which is the invariant asserted
 * here: a card whose rates sum to anything but 0 (a non-playable entry) or 30 means the columns are
 * no longer what this extractor thinks they are.
 */
const GROWTH_TOTAL = 30

export function buildTraineeGrowth(db) {
    const titles = textIndex(db, TEXT_CATEGORY_CARD_TITLE)
    const charaNames = textIndex(db, TEXT_CATEGORY_CHARA_NAME)
    const rows = db.prepare("SELECT id, chara_id, default_rarity, talent_speed, talent_stamina, talent_pow, talent_guts, talent_wiz, running_style FROM card_data ORDER BY id").all()
    const out = []
    for (const row of rows) {
        const growth = {}
        let total = 0
        for (const [column, key] of GROWTH_COLUMNS) {
            const value = row[column]
            if (!Number.isInteger(value) || value < 0 || value > GROWTH_TOTAL) {
                throw new GenerateError(`card ${row.id} has ${column} = ${value}, which is not a growth percentage in 0..${GROWTH_TOTAL}`)
            }
            growth[key] = value
            total += value
        }
        if (total !== 0 && total !== GROWTH_TOTAL) {
            throw new GenerateError(`card ${row.id} distributes ${total} growth points, expected 0 or ${GROWTH_TOTAL}`)
        }
        out.push({
            cardId: row.id,
            charaId: row.chara_id,
            character: charaNames.get(row.chara_id) ?? null,
            outfit: titles.get(row.id) ?? null,
            defaultRarity: row.default_rarity,
            growth,
            runningStyle: row.running_style,
        })
    }
    if (!out.length) throw new GenerateError("card_data is empty; no trainee growth rates could be read")
    return out
}

/**
 * The stat ceiling, split into the part every career shares and the part a scenario adds.
 *
 * card_rarity_data ships a per-card ceiling and every single row of it is 1200, which is asserted
 * rather than assumed: a card that ever shipped a different one would mean the baseline is per-card
 * and the split below is wrong. single_mode_scenario then carries the bonus each scenario adds on
 * top, which is the number that makes Trackblazer a Stamina scenario and Grand Concert a Speed one.
 */
export function buildStatCaps(db) {
    const rows = db.prepare("SELECT DISTINCT max_speed, max_stamina, max_pow, max_guts, max_wiz FROM card_rarity_data").all()
    if (rows.length !== 1) throw new GenerateError(`card_rarity_data ships ${rows.length} distinct stat ceilings; the baseline is no longer shared across cards`)
    const distinct = new Set(BASE_STAT_COLUMNS.map(([, capColumn]) => rows[0][capColumn]))
    if (distinct.size !== 1) throw new GenerateError(`the shared stat ceiling differs per stat (${[...distinct].join(", ")}); this extractor assumes one baseline`)
    const baseline = [...distinct][0]
    if (!Number.isInteger(baseline) || baseline <= 0) throw new GenerateError(`the shared stat ceiling is ${baseline}, which is not a usable cap`)

    const scenarioBonus = []
    for (const row of db.prepare("SELECT id, max_speed, max_stamina, max_pow, max_guts, max_wiz FROM single_mode_scenario ORDER BY id").all()) {
        const bonus = {}
        for (const [statColumn, capColumn, stat] of BASE_STAT_COLUMNS) {
            void statColumn
            const value = row[capColumn]
            if (!Number.isInteger(value) || value < 0) throw new GenerateError(`scenario ${row.id} has a ${stat} cap bonus of ${value}`)
            bonus[stat] = value
        }
        scenarioBonus.push({ scenarioId: row.id, bonus })
    }
    if (!scenarioBonus.length) throw new GenerateError("single_mode_scenario is empty; no scenario cap bonus could be read")
    return { baseline, scenarioBonus }
}

/**
 * Per trainee-outfit starting stats and aptitude grades, at each star level the card ships.
 *
 * These are the numbers a career actually begins at before a single Spark is applied, which is what
 * makes an inheritance figure mean anything: +28 Stamina onto a base of 88 is a different build from
 * +28 onto a base of 108. The star level matters and is kept, because unlocking stars moves them.
 */
export function buildTraineeBase(db) {
    const rows = db
        .prepare(
            "SELECT card_id, rarity, speed, stamina, pow, guts, wiz, proper_distance_short, proper_distance_mile, proper_distance_middle, proper_distance_long, " +
                "proper_ground_turf, proper_ground_dirt, proper_running_style_nige, proper_running_style_senko, proper_running_style_sashi, proper_running_style_oikomi " +
                "FROM card_rarity_data ORDER BY card_id, rarity",
        )
        .all()
    const out = []
    for (const row of rows) {
        const startStats = {}
        for (const [statColumn, , stat] of BASE_STAT_COLUMNS) {
            const value = row[statColumn]
            if (!Number.isInteger(value) || value < 0) throw new GenerateError(`card ${row.card_id} rarity ${row.rarity} has a ${stat} start of ${value}`)
            startStats[stat] = value
        }
        const aptitudes = {}
        for (const [column, key] of APTITUDE_COLUMNS) {
            const raw = row[column]
            const grade = APTITUDE_GRADES[raw - 1]
            if (grade === undefined) throw new GenerateError(`card ${row.card_id} rarity ${row.rarity} has aptitude ${column} = ${raw}, outside the decoded 1..${APTITUDE_GRADES.length} grade range`)
            aptitudes[key] = grade
        }
        out.push({ cardId: row.card_id, starLevel: row.rarity, startStats, aptitudes })
    }
    if (!out.length) throw new GenerateError("card_rarity_data is empty; no trainee base stats could be read")
    return out
}

/**
 * Re-derives command_id -> training type from the effect table, exactly as the support-card extractor
 * does, and asserts the five stat trainings are all present and all distinct.
 *
 * Deriving it rather than declaring it is the point: the mapping is not the obvious one (102 is Power,
 * not Stamina), and a hard-coded copy in a second file is how two assets quietly disagree.
 */
export function deriveCommandTrainingTypes(db) {
    const rows = db.prepare("SELECT command_id, target_type, effect_value FROM single_mode_training_effect WHERE sub_id = 1 AND result_state = 2 AND scenario_id = 1").all()
    const best = new Map()
    for (const row of rows) {
        const target = TRAINING_TARGETS[row.target_type]
        if (!target) throw new GenerateError(`single_mode_training_effect carries target_type ${row.target_type}, which this extractor does not know how to describe`)
        if (target.kind !== "STAT" || row.effect_value <= 0) continue
        const held = best.get(row.command_id)
        if (!held || row.effect_value > held.value) best.set(row.command_id, { stat: target.stat, value: row.effect_value })
    }
    const types = {}
    for (const [commandId, top] of best) types[commandId] = top.stat
    for (const [label, commands] of [
        ["base", BASE_TRAINING_COMMANDS],
        ["camp", CAMP_TRAINING_COMMANDS],
    ]) {
        for (const commandId of commands) {
            if (!types[commandId]) throw new GenerateError(`${label} training command ${commandId} raises no stat in scenario 1; the five stat trainings are not all present`)
        }
        const stats = commands.map((id) => types[id])
        if (new Set(stats).size !== stats.length) throw new GenerateError(`the five ${label} trainings do not raise five distinct stats: ${stats.join(", ")}`)
    }
    return types
}

/**
 * The base outcome of one training, per scenario, straight out of the game's own table.
 *
 * This is the piece that makes a Power-flex build arguable instead of folklore: Power training's own
 * row carries a Stamina gain, and the size of it differs by scenario. Nothing here is scaled,
 * multiplied or averaged; a support deck's contribution is a separate layer and stays separate.
 *
 * What is NOT in this table, and is therefore not shipped as though it were: the facility level. The
 * table is keyed by command, not by the 1..5 level the same command has in single_mode_training, so
 * how a level-5 facility scales these numbers is not decoded here.
 */
export function buildTrainingEffects(db) {
    const commandTypes = deriveCommandTrainingTypes(db)
    const failureRates = new Map()
    for (const row of db.prepare("SELECT command_id, command_level, command_type, failure_rate FROM single_mode_training ORDER BY command_id, command_level").all()) {
        if (row.command_type !== TRAINING_COMMAND_TYPE) continue
        const held = failureRates.get(row.command_id) ?? []
        held[row.command_level - 1] = row.failure_rate
        failureRates.set(row.command_id, held)
    }

    const grouped = new Map()
    for (const row of db.prepare("SELECT scenario_id, command_id, sub_id, result_state, target_type, effect_value FROM single_mode_training_effect ORDER BY scenario_id, command_id, sub_id, result_state, target_type").all()) {
        const target = TRAINING_TARGETS[row.target_type]
        if (!target) throw new GenerateError(`single_mode_training_effect carries target_type ${row.target_type}, which this extractor does not know how to describe`)
        const key = `${row.scenario_id}|${row.command_id}|${row.sub_id}|${row.result_state}`
        let entry = grouped.get(key)
        if (!entry) {
            entry = {
                scenarioId: row.scenario_id,
                commandId: row.command_id,
                subId: row.sub_id,
                resultState: row.result_state,
                trainingType: commandTypes[row.command_id] ?? null,
                isCamp: CAMP_TRAINING_COMMANDS.includes(row.command_id),
                stats: {},
                energy: 0,
                mood: 0,
                skillPoints: 0,
                undecodedTargets: [],
            }
            grouped.set(key, entry)
        }
        if (target.kind === "STAT") entry.stats[target.stat] = (entry.stats[target.stat] ?? 0) + row.effect_value
        else if (target.kind === "ENERGY") entry.energy += row.effect_value
        else if (target.kind === "MOOD") entry.mood += row.effect_value
        else if (target.kind === "SKILL_POINTS") entry.skillPoints += row.effect_value
        else entry.undecodedTargets.push(row.target_type)
    }

    const out = [...grouped.values()].map((entry) => ({
        ...entry,
        stats: Object.fromEntries(BASE_STAT_COLUMNS.map(([, , stat]) => [stat, entry.stats[stat] ?? 0])),
        undecodedTargets: [...new Set(entry.undecodedTargets)].sort((a, b) => a - b),
        failureRateByLevel: failureRates.get(entry.commandId) ?? [],
    }))
    if (!out.length) throw new GenerateError("single_mode_training_effect is empty; no training outcome could be read")
    return out
}

export function buildPayload(db) {
    requireTables(db)
    const factorGroups = buildFactorGroups(db)
    const traineeGrowth = buildTraineeGrowth(db)
    const traineeBase = buildTraineeBase(db)
    const statCaps = buildStatCaps(db)
    const trainingEffects = buildTrainingEffects(db)
    return {
        schema: BUILD_BUDGET_DATA_SCHEMA,
        schemaVersion: BUILD_BUDGET_DATA_SCHEMA_VERSION,
        source: SOURCE,
        targetTypes: Object.fromEntries(
            Object.entries(TARGET_TYPES)
                .sort((a, b) => Number(a[0]) - Number(b[0]))
                .map(([id, meta]) => [id, { kind: meta.kind, stat: meta.stat ?? null, aptitude: meta.aptitude ?? null }]),
        ),
        trainingTargets: Object.fromEntries(
            Object.entries(TRAINING_TARGETS)
                .sort((a, b) => Number(a[0]) - Number(b[0]))
                .map(([id, meta]) => [id, { kind: meta.kind, stat: meta.stat ?? null }]),
        ),
        aptitudeGrades: APTITUDE_GRADES,
        counts: {
            factorGroups: factorGroups.length,
            factorsByFamily: factorGroups.reduce((acc, g) => {
                acc[g.subfamily] = (acc[g.subfamily] ?? 0) + 1
                return acc
            }, {}),
            traineeCards: traineeGrowth.length,
            traineeBaseRows: traineeBase.length,
            trainingEffectRows: trainingEffects.length,
        },
        statCaps,
        factorGroups,
        traineeGrowth,
        traineeBase,
        trainingEffects,
    }
}

/**
 * Serializes to pure ASCII, escaping every non-ASCII character as a \uXXXX sequence.
 *
 * Spark and outfit names are shipped game strings and carry characters this repository does not write
 * by hand: grade marks, a music note, a star, accented letters. Escaping them keeps the file
 * byte-identical on every platform while JSON.parse still returns the exact shipped string.
 */
export function serializePayload(payload) {
    const json = JSON.stringify(payload, null, 2).replace(new RegExp("[\\u007f-\\uffff]", "g"), (ch) => "\\u" + ch.charCodeAt(0).toString(16).padStart(4, "0"))
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

const HELP = `generate-build-budget-data - extract inheritance and trainee-growth evidence from an installed master.mdb

Options:
  --db <path>   Path to master.mdb (required).
  --out <path>  Output file (default src/data/build_budget_data.json).
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
