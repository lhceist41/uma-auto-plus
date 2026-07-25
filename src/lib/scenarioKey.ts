/**
 * Scenario key normalization.
 *
 * The Grand Concert scenario ("Brighter Together Our Grand Concert", community name "Grand
 * Live") reaches us under several spellings: the community name, the punctuated title variants
 * pre-launch research predicted, the unpunctuated form the Global client actually renders, and
 * the client's own internal name ("Our Grand Concert", which is also the name of its
 * inheritance spark). All of them must resolve to one key before the string is persisted,
 * hashed into the launch identity, matched against presets, or compared on the Kotlin side --
 * otherwise a career could be persisted under one spelling and launched under another, which is
 * precisely the class of drift the Start persistence barrier exists to prevent.
 *
 * The Kotlin mirror of this lives in `bot/GrandConcertScenario.kt`; the two alias lists and the
 * fold are kept identical and are pinned by tests on both sides.
 */

/** The canonical Grand Concert key: short and human-readable, like the existing scenario keys. */
export const GRAND_CONCERT_KEY = "Grand Concert"

/** The title as the Global client renders it (two lines, no colon, no exclamation mark).
 * Display only -- never a persistence key. */
export const GRAND_CONCERT_DISPLAY_TITLE = "Brighter Together Our Grand Concert"

/** Every spelling that must resolve to {@link GRAND_CONCERT_KEY}. */
export const GRAND_CONCERT_ALIASES = [
    "Grand Concert",
    "Grand Live",
    "Our Grand Concert",
    "Brighter Together Our Grand Concert",
    "Brighter Together! Our Grand Concert",
    "Brighter Together: Our Grand Concert",
]

/** Casing, punctuation, and whitespace are all OCR- and localisation-fragile, so the fold keeps
 * only letters and digits. */
const fold = (text: string): string => text.toLowerCase().replace(/[^a-z0-9]/g, "")

const FOLDED_ALIASES = new Set(GRAND_CONCERT_ALIASES.map(fold))

/**
 * Resolves any accepted Grand Concert spelling to the canonical key, and leaves every other
 * scenario string untouched (including the empty string, so "unset" stays distinguishable).
 */
export const normalizeScenarioKey = (raw: string | null | undefined): string => {
    const trimmed = (raw ?? "").trim()
    if (trimmed === "") return trimmed
    return FOLDED_ALIASES.has(fold(trimmed)) ? GRAND_CONCERT_KEY : trimmed
}

/** True when the string names the Grand Concert scenario under any accepted spelling. */
export const isGrandConcert = (raw: string | null | undefined): boolean => normalizeScenarioKey(raw) === GRAND_CONCERT_KEY

/**
 * Scenario-level capability gate.
 *
 * Grand Concert support is experimental and supervised: the shared career loop drives it, but
 * the Lesson shop and the concerts stop for manual input. Features that assume a career can run
 * start-to-finish unattended are therefore unavailable for it -- not because they would crash,
 * but because they would silently produce a queue of careers that each stall waiting for a
 * player who has gone to bed.
 */
export interface ScenarioCapabilities {
    /** Multi-run queues require unattended completion. */
    runQueue: boolean
    /** Trainee rotation is a queue feature and inherits the same requirement. */
    rotation: boolean
    /** Automatic TP restore exists to keep a queue fed; pointless without a queue. */
    tpRestore: boolean
    /** Single supervised runs are allowed. */
    singleRun: boolean
}

export const scenarioCapabilities = (raw: string | null | undefined): ScenarioCapabilities =>
    isGrandConcert(raw)
        ? { runQueue: false, rotation: false, tpRestore: false, singleRun: true }
        : { runQueue: true, rotation: true, tpRestore: true, singleRun: true }

/** Player-facing explanation for why the queue features are unavailable. Shown on Home. */
export const GRAND_CONCERT_WARNING =
    "Grand Concert support is experimental and needs supervision. Training, races, events, and skills are automated, " +
    "but the Lesson screen and the concerts are not: the bot stops safely and asks you to handle those in game, then " +
    "you press Start to resume the same career. Run queues, trainee rotation, and automatic TP restore are unavailable " +
    "for this scenario."
