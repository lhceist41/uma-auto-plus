// Extracts the race-survival evidence the game's own master.mdb carries into a committed, offline data
// file the STAM-1 Race Survival Shadow Model reads. Read-only against the database, deterministic
// output, no network.
//
// What is being extracted, and why each piece is here:
//
//   race_course_set          one row per (track, distance, ground, inout) course the game ships, with
//                            the game's own finish-time band for that course. That band is the only
//                            course-specific race-duration evidence this repository has; without it a
//                            survival model has to guess how long a race lasts.
//   race_track               the 15 track ids, named through text_data category 35.
//   race_proper_*_rate       the aptitude-letter multiplier tables (distance, ground, running style).
//                            Their values are decoded exactly; where the race engine applies them is
//                            NOT decoded here, so they ship as reference data, not as model inputs.
//   skill_data               every skill carrying an HP effect (ability_type 9), positive (recovery)
//                            or negative (drain/debuff), with its activation conditions.
//   text_data category 47    skill id -> English skill name.
//   text_data category 48    skill id -> English skill description (used to re-prove the enums).
//
// Three things in here are derived rather than assumed, and the generator re-proves each on every run
// so a game patch that moves them fails loudly instead of silently:
//
//   The fixed-point scale. skill_data ability values and race_course_set finish times are integers
//   scaled by 10000. verifyFixedPointScale asserts every shipped finish time lands in a plausible
//   seconds band once divided, which a scale change would break.
//
//   ability_type 9 = HP. Proven by the shipped English descriptions: skills whose text says they
//   recover endurance carry a positive value, skills whose text says they increase fatigue carry a
//   negative one. verifyHpAbilityType asserts that pairing across the whole set.
//
//   The condition enums (distance_type, running_style, ground_type). Proven by witness skills whose
//   English description names the same thing their condition string encodes: "Deep Breaths ... (Long)"
//   has distance_type==4, "Moxie ... (Front Runner)" has running_style==1, "Master of the Sands ...
//   (Dirt)" has ground_type==2. verifyConditionEnums asserts every witness.
//
// The aptitude-grade ordering (row id 1..8 = G..S) is asserted monotonic rather than assumed: the
// tables are ascending in that order and would stop being so if the ids were remapped.
//
// Usage:
//   node scripts/generate-race-survival-data.mjs --db <path-to-master.mdb>
//   node scripts/generate-race-survival-data.mjs --db <path> --check   # exit 1 if committed file is stale
//
// Determinism: ids ascending, fixed key order, two-space JSON, trailing newline, pure-ASCII escaping.
// No timestamps, no absolute paths, no wall-clock.

import { readFileSync, writeFileSync, renameSync, existsSync } from "node:fs"
import { DatabaseSync } from "node:sqlite"
import { fileURLToPath, pathToFileURL } from "node:url"
import { dirname, join } from "node:path"

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, "..")
const OUT_PATH = join(REPO, "src", "data", "race_survival_data.json")

export const RACE_SURVIVAL_DATA_SCHEMA = "race_survival_evidence"
export const RACE_SURVIVAL_DATA_SCHEMA_VERSION = 1

const TEXT_CATEGORY_TRACK_SHORT_NAME = 35
const TEXT_CATEGORY_SKILL_NAME = 47
const TEXT_CATEGORY_SKILL_DESC = 48

/** master.mdb stores ability values, ability durations and finish times as integers scaled by this. */
export const FIXED_POINT_DIVISOR = 10000

/** The ability_type that reads and writes race HP ("endurance" in the English text). */
export const HP_ABILITY_TYPE = 9

/** The six effect slots a skill row can carry, in the order the columns appear. */
const EFFECT_SLOTS = ["1_1", "1_2", "1_3", "2_1", "2_2", "2_3"]

/** Aptitude letters in row-id order. Row id 0 is the "no aptitude" sentinel and is not exported. */
const APTITUDE_GRADES = ["G", "F", "E", "D", "C", "B", "A", "S"]

/** Tracks the game ships as dirt-only. Used to prove race_course_set.ground 2 = dirt. */
const DIRT_ONLY_TRACK_IDS = [10101, 10103, 10104, 10105]

const SOURCE =
    "master.mdb: race_course_set, race_track, race_proper_distance_rate, race_proper_ground_rate, " +
    "race_proper_runningstyle_rate, skill_data, text_data(categories 35, 47, 48)"

export class GenerateError extends Error {
    constructor(message) {
        super(message)
        this.name = "GenerateError"
    }
}

function readTextCategory(db, category) {
    const rows = db.prepare('SELECT "index" AS idx, text FROM text_data WHERE category = ?').all(category)
    const map = new Map()
    for (const row of rows) map.set(row.idx, row.text)
    return map
}

/**
 * Asserts the 10000 fixed-point scale still holds for the finish-time columns.
 *
 * Every course the game ships is between 1000m and 3600m, so a real finish time has to land somewhere
 * between roughly 50 and 250 seconds. A scale change, or a column rename that silently returns zeroes,
 * pushes the whole set outside that band, which is what this catches.
 */
export function verifyFixedPointScale(courseRows) {
    for (const row of courseRows) {
        const min = row.finish_time_min / FIXED_POINT_DIVISOR
        const max = row.finish_time_max / FIXED_POINT_DIVISOR
        if (!(min >= 50 && min <= 250 && max >= 50 && max <= 250)) {
            throw new GenerateError(`course ${row.id}: finish-time band ${min}..${max}s is outside the plausible band; the ${FIXED_POINT_DIVISOR} fixed-point scale no longer holds`)
        }
        if (max < min) throw new GenerateError(`course ${row.id}: finish_time_max ${max} is below finish_time_min ${min}`)
    }
    return true
}

/** target_type 1 is the skill's own runner. */
export const TARGET_TYPE_SELF = 1

/**
 * The target types that name the opposing field, proven by the shipped descriptions on every skill
 * that uses one: "runners ahead", "other runners", "front runners", "rushed runners behind". Every HP
 * effect aimed at one of these is negative, which is what fixes the sign convention.
 */
export const OPPONENT_TARGET_TYPES = [4, 9, 18, 19, 20]

/**
 * The target types that name a specific friendly runner rather than the field. Only the team-scenario
 * cheer skills use one ("Recover Silence Suzuka's endurance ..."), and their effect is positive, so
 * they are excluded from the opponent rule instead of breaking it.
 */
export const ALLY_TARGET_TYPES = [22]

/**
 * Asserts ability_type 9 is the HP effect, using the shipped English descriptions as the witness.
 *
 * Two independent invariants have to hold at once, and between them they pin the sign convention:
 *
 *   An effect aimed at the opposing field is always negative. The game has no skill that heals an
 *   opponent, so a positive value on an opponent target would mean ability_type 9 is not HP at all.
 *   A target type that is neither self, opponent nor a named ally is a hard failure rather than a
 *   silent pass: an undecoded target is exactly the case where a wrong reading would go unnoticed.
 *
 *   A self-aimed effect agrees with the skill's own English text: a skill that says it recovers
 *   endurance carries a positive value, one that says it expends or loses endurance carries a
 *   negative one. Skills whose text says both are the trade-off skills and prove nothing either way.
 *
 * "Steal endurance from runners ahead" is deliberately not read as a self-drain: those skills carry
 * two effects, a negative one aimed at the field and a positive one aimed at the runner, and the
 * target rule already covers the negative half.
 */
export function verifyHpAbilityType(effectRows, descriptions) {
    let recoveries = 0
    let drains = 0
    let opponentEffects = 0
    for (const effect of effectRows) {
        if (OPPONENT_TARGET_TYPES.includes(effect.targetType)) {
            if (effect.hpValueRaw >= 0) {
                throw new GenerateError(`skill ${effect.skillId} carries a non-negative ability_type ${HP_ABILITY_TYPE} value ${effect.hpValueRaw} aimed at opponent target_type ${effect.targetType}; no skill heals an opponent, so this is no longer an HP effect`)
            }
            opponentEffects++
            continue
        }
        if (effect.targetType !== TARGET_TYPE_SELF) {
            if (!ALLY_TARGET_TYPES.includes(effect.targetType)) {
                throw new GenerateError(`skill ${effect.skillId} carries an ability_type ${HP_ABILITY_TYPE} effect on undecoded target_type ${effect.targetType}; classify it as self, opponent or ally before trusting this data`)
            }
            continue
        }
        const desc = descriptions.get(effect.skillId)
        if (!desc) continue
        const saysRecover = /recover endurance|decrease fatigue|reduce fatigue|regain the energy|gain energy/i.test(desc)
        const saysDrain = /increase fatigue|lose endurance|expend endurance|consume endurance/i.test(desc)
        if (saysRecover && !saysDrain) {
            if (effect.hpValueRaw <= 0) throw new GenerateError(`skill ${effect.skillId} describes recovery but its ability_type ${HP_ABILITY_TYPE} value is ${effect.hpValueRaw}`)
            recoveries++
        } else if (saysDrain && !saysRecover) {
            if (effect.hpValueRaw >= 0) throw new GenerateError(`skill ${effect.skillId} describes fatigue but its ability_type ${HP_ABILITY_TYPE} value is ${effect.hpValueRaw}`)
            drains++
        }
    }
    if (recoveries < 20 || drains < 5 || opponentEffects < 5) {
        throw new GenerateError(`ability_type ${HP_ABILITY_TYPE} witness set is too small (${recoveries} recoveries, ${drains} self-drains, ${opponentEffects} opponent effects); the HP mapping is no longer provable from the shipped data`)
    }
    return { recoveries, drains, opponentEffects }
}

/**
 * The condition-string enums, each proven by a witness skill whose English description names the same
 * thing its condition encodes. Each entry maps the numeric code to our own token and names the witness
 * skill id plus the phrase that has to appear in that skill's description.
 */
export const CONDITION_ENUM_WITNESSES = {
    distanceType: [
        { code: 3, token: "medium", witnessSkillId: 201142, phrase: "(Medium)" },
        { code: 4, token: "long", witnessSkillId: 200742, phrase: "(Long)" },
    ],
    runningStyle: [
        { code: 1, token: "front", witnessSkillId: 201282, phrase: "(Front Runner)" },
        { code: 2, token: "pace", witnessSkillId: 200572, phrase: "(Pace Chaser)" },
        { code: 3, token: "late", witnessSkillId: 201422, phrase: "(Late Surger)" },
        { code: 4, token: "end", witnessSkillId: 200622, phrase: "(End Closer)" },
    ],
    groundType: [{ code: 2, token: "dirt", witnessSkillId: 202002, phrase: "(Dirt)" }],
}

/**
 * The full enum domains. Only the codes listed in CONDITION_ENUM_WITNESSES are proven by a witness
 * skill; the remaining codes are the unambiguous completion of an ordered domain (sprint and mile are
 * the two shorter distance types below medium, turf is the surface that is not dirt) and are marked
 * as such here so a reader can see exactly which entries rest on a proof and which do not.
 */
export const CONDITION_ENUM_DOMAINS = {
    distanceType: { 1: "sprint", 2: "mile", 3: "medium", 4: "long" },
    runningStyle: { 1: "front", 2: "pace", 3: "late", 4: "end" },
    groundType: { 1: "turf", 2: "dirt" },
}

/** Enum name -> the condition-string variable that carries it. */
const CONDITION_VARIABLE = { distanceType: "distance_type", runningStyle: "running_style", groundType: "ground_type" }

/**
 * Re-proves every witnessed condition enum against the shipped descriptions. A witness passes when the
 * skill's description carries the phrase and its condition string carries `<variable>==<code>`.
 */
export function verifyConditionEnums(conditionsBySkillId, descriptions) {
    for (const [enumName, witnesses] of Object.entries(CONDITION_ENUM_WITNESSES)) {
        const variable = CONDITION_VARIABLE[enumName]
        for (const witness of witnesses) {
            const desc = descriptions.get(witness.witnessSkillId)
            if (!desc) throw new GenerateError(`${enumName} witness skill ${witness.witnessSkillId} has no description in text_data category ${TEXT_CATEGORY_SKILL_DESC}`)
            if (!desc.includes(witness.phrase)) {
                throw new GenerateError(`${enumName} witness skill ${witness.witnessSkillId} description does not carry ${witness.phrase}: ${desc}`)
            }
            const condition = conditionsBySkillId.get(witness.witnessSkillId) ?? ""
            if (!condition.includes(`${variable}==${witness.code}`)) {
                throw new GenerateError(`${enumName} witness skill ${witness.witnessSkillId} condition does not carry ${variable}==${witness.code}: ${condition}`)
            }
        }
    }
    return true
}

/** Asserts an aptitude rate table is ascending over row ids 1..8, which fixes the G..S ordering. */
export function verifyAptitudeOrdering(tableName, rows, valueColumns) {
    const byId = new Map(rows.map((r) => [r.id, r]))
    for (const column of valueColumns) {
        let previous = -Infinity
        for (let id = 1; id <= APTITUDE_GRADES.length; id++) {
            const row = byId.get(id)
            if (!row) throw new GenerateError(`${tableName} is missing row id ${id}; the aptitude-grade mapping cannot be proven`)
            if (!(row[column] >= previous)) {
                throw new GenerateError(`${tableName}.${column} is not ascending at id ${id} (${row[column]} below ${previous}); row id 1..8 no longer means G..S`)
            }
            previous = row[column]
        }
    }
    return true
}

/** Asserts race_course_set.ground uses 1 = turf, 2 = dirt, using the game's dirt-only tracks as witness. */
export function verifyGroundEnum(courseRows) {
    for (const value of new Set(courseRows.map((r) => r.ground))) {
        if (value !== 1 && value !== 2) throw new GenerateError(`race_course_set.ground carries unexpected value ${value}; only 1 (turf) and 2 (dirt) are decoded`)
    }
    for (const trackId of DIRT_ONLY_TRACK_IDS) {
        const rows = courseRows.filter((r) => r.race_track_id === trackId)
        if (!rows.length) throw new GenerateError(`no course set for dirt-only track ${trackId}; the ground enum witness is gone`)
        if (rows.some((r) => r.ground !== 2)) throw new GenerateError(`dirt-only track ${trackId} carries a non-2 ground value; race_course_set.ground 2 = dirt no longer holds`)
    }
    return true
}

export function buildCourses(db) {
    const rows = db.prepare("SELECT * FROM race_course_set").all()
    verifyFixedPointScale(rows)
    verifyGroundEnum(rows)
    const trackNames = readTextCategory(db, TEXT_CATEGORY_TRACK_SHORT_NAME)
    return rows
        .slice()
        .sort((a, b) => a.id - b.id)
        .map((row) => ({
            id: row.id,
            trackId: row.race_track_id,
            track: trackNames.get(row.race_track_id) ?? null,
            distanceMeters: row.distance,
            ground: row.ground === 1 ? "turf" : "dirt",
            inout: row.inout,
            turn: row.turn,
            finishTimeMinRaw: row.finish_time_min,
            finishTimeMaxRaw: row.finish_time_max,
        }))
}

export function buildAptitudeRates(db) {
    const distance = db.prepare("SELECT * FROM race_proper_distance_rate").all()
    const ground = db.prepare("SELECT * FROM race_proper_ground_rate").all()
    const style = db.prepare("SELECT * FROM race_proper_runningstyle_rate").all()
    verifyAptitudeOrdering("race_proper_distance_rate", distance, ["proper_rate_speed", "proper_rate_power"])
    verifyAptitudeOrdering("race_proper_ground_rate", ground, ["proper_rate"])
    verifyAptitudeOrdering("race_proper_runningstyle_rate", style, ["proper_rate"])
    const byId = (rows) => new Map(rows.map((r) => [r.id, r]))
    const d = byId(distance)
    const g = byId(ground)
    const s = byId(style)
    return {
        distance: APTITUDE_GRADES.map((grade, i) => ({ grade, speedRateRaw: d.get(i + 1).proper_rate_speed, powerRateRaw: d.get(i + 1).proper_rate_power })),
        ground: APTITUDE_GRADES.map((grade, i) => ({ grade, rateRaw: g.get(i + 1).proper_rate })),
        runningStyle: APTITUDE_GRADES.map((grade, i) => ({ grade, rateRaw: s.get(i + 1).proper_rate })),
    }
}

export function buildHpSkillEffects(db) {
    const rows = db.prepare("SELECT * FROM skill_data").all()
    const names = readTextCategory(db, TEXT_CATEGORY_SKILL_NAME)
    const descriptions = readTextCategory(db, TEXT_CATEGORY_SKILL_DESC)

    const conditionsBySkillId = new Map()
    for (const row of rows) {
        conditionsBySkillId.set(row.id, [row.condition_1, row.condition_2].filter(Boolean).join("@"))
    }
    verifyConditionEnums(conditionsBySkillId, descriptions)

    const flatEffects = []
    const skills = []
    for (const row of rows.slice().sort((a, b) => a.id - b.id)) {
        const effects = []
        for (const slot of EFFECT_SLOTS) {
            if (row[`ability_type_${slot}`] !== HP_ABILITY_TYPE) continue
            const effect = {
                slot,
                hpValueRaw: row[`float_ability_value_${slot}`],
                targetType: row[`target_type_${slot}`],
                targetValue: row[`target_value_${slot}`],
            }
            effects.push(effect)
            flatEffects.push({ skillId: row.id, ...effect })
        }
        if (!effects.length) continue
        skills.push({
            id: row.id,
            name: names.get(row.id) ?? null,
            desc: descriptions.get(row.id) ?? null,
            rarity: row.rarity,
            // Both condition blocks, verbatim. `@` separates alternative clauses inside one block; the
            // two blocks are the skill's two independent activation branches.
            condition1: row.condition_1,
            precondition1: row.precondition_1,
            condition2: row.condition_2,
            precondition2: row.precondition_2,
            // Cooldown 0 means the branch fires at most once in a race. A non-zero cooldown is the
            // only evidence here that a skill can repeat, so it travels with the skill.
            cooldown1Raw: row.float_cooldown_time_1,
            cooldown2Raw: row.float_cooldown_time_2,
            effects,
        })
    }
    const witness = verifyHpAbilityType(flatEffects, descriptions)
    return { skills, witness }
}

export function buildPayload(db) {
    const courses = buildCourses(db)
    const aptitudeRates = buildAptitudeRates(db)
    const { skills, witness } = buildHpSkillEffects(db)
    const trackNames = readTextCategory(db, TEXT_CATEGORY_TRACK_SHORT_NAME)
    const tracks = {}
    for (const id of [...trackNames.keys()].sort((a, b) => a - b)) tracks[String(id)] = trackNames.get(id)

    return {
        schema: RACE_SURVIVAL_DATA_SCHEMA,
        schemaVersion: RACE_SURVIVAL_DATA_SCHEMA_VERSION,
        source: SOURCE,
        fixedPointDivisor: FIXED_POINT_DIVISOR,
        hpAbilityType: HP_ABILITY_TYPE,
        // Every enum below is re-proven on each run against the shipped English text; see the verify*
        // functions above for the witness each one rests on. `witnessedCodes` names the subset that
        // carries a proof, so a reader never has to guess which entries were completed by reasoning.
        enums: CONDITION_ENUM_DOMAINS,
        groundEnum: { 1: "turf", 2: "dirt" },
        aptitudeGradeEnum: Object.fromEntries(APTITUDE_GRADES.map((g, i) => [i + 1, g])),
        proof: {
            witnessedCodes: Object.fromEntries(Object.entries(CONDITION_ENUM_WITNESSES).map(([name, list]) => [name, list.map((w) => w.code)])),
            hpRecoveryWitnessCount: witness.recoveries,
            hpSelfDrainWitnessCount: witness.drains,
            hpOpponentEffectWitnessCount: witness.opponentEffects,
        },
        tracks,
        courses,
        aptitudeRates,
        hpSkillEffects: skills,
    }
}

/**
 * Serializes to pure ASCII, escaping every non-ASCII character as a \uXXXX sequence.
 *
 * Skill names are shipped game strings and carry characters this repository does not write by hand:
 * the circle and cross grade marks, a music note, a star, an em dash and accented letters. Escaping
 * them keeps the file byte-identical on every platform and keeps those characters out of the tree,
 * while JSON.parse still returns the exact shipped string.
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

const HELP = `generate-race-survival-data - extract the race-survival evidence from an installed master.mdb

Options:
  --db <path>   Path to master.mdb (required).
  --out <path>  Output file (default src/data/race_survival_data.json).
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
