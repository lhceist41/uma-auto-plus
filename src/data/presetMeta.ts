/**
 * Display + provenance metadata for character presets, derived from preset names so the
 * preset objects in characterPresets.ts stay byte-identical (their `name` is the persistence
 * key in settings, rotation snapshots, and the applied-racing snapshot).
 *
 * Preset naming convention: outfit-specific presets are "Character (Outfit)"; plain names
 * model the character's base card, whose outfit title comes from characterBaseOutfits.
 * Base-outfit names come from the cards' gametora pages, except Nakayama Festa, Wonder Acute, Agnes Digital,
 * Inari One, Ines Fujin, Mejiro Bright, Satono Diamond, Aston Machan, Kawakami Princess, Seeking the Pearl,
 * Yamanin Zephyr and Yukino Bijin, which come from the game's master data. The base card is the character's
 * lowest card id in build_budget_data.json.
 */

/** EN base-card outfit title per character, for presets whose name carries no bracket. */
export const characterBaseOutfits: Record<string, string> = {
    "Admire Vega": "Starry Nocturne",
    "Agnes Digital": "Full-Color Fangirling",
    "Agnes Tachyon": "tach-nology",
    "Air Groove": "Empress Road",
    "Air Shakur": "unsigned",
    "Aston Machan": "Flare",
    "Bamboo Memory": "Iron Ambition",
    "Biwa Hayahide": "pf. Winning Equation...",
    "Copano Rickey": "Eightfold☆Fortune",
    "Curren Chan": "Fille Éclair",
    "Daiwa Scarlet": "Peak Blue",
    "Eishin Flash": "Meisterschaft",
    "El Condor Pasa": "El☆Número 1",
    "Fine Motion": "Noble Seamair",
    "Fuji Kiseki": "Shooting Star Revue",
    "Gold Ship": "Red Strife",
    "Grass Wonder": "Stone-Piercing Blue",
    "Haru Urara": "Bestest Prize ♪",
    "Hishi Akebono": "Buono ☆ Alla Moda",
    "Hishi Amazon": "Azure Amazon",
    "Inari One": "Edomurasaki",
    "Ines Fujin": "Always Electrifying",
    "Kawakami Princess": "Princess of Pink",
    "King Halo": "King of Emeralds",
    "Kitasan Black": "Gilded Shrine to Glory",
    "Manhattan Cafe": "Creeping Shadow",
    Matikanefukukitaru: "Rising☆Fortune",
    Matikanetannhauser: "Clippety-Tippety-Clop",
    "Mayano Top Gun": "Scramble☆Zone",
    "Meisho Doto": "Turbulent Blue",
    "Mejiro Ardan": "Crystalline",
    "Mejiro Bright": "Brunissage Line",
    "Mejiro Dober": "Off the Line",
    "Mejiro Palmer": "Line Breakthrough",
    "Mejiro Ryan": "Down the Line",
    "Mihono Bourbon": "MB-19890425",
    "Nakayama Festa": "Desperate Measures",
    "Narita Brian": "Maverick",
    "Narita Taishin": "Nevertheless",
    "Nice Nature": "Poinsettia Ribbon",
    "Nishino Flower": "Layered Petals",
    "Oguri Cap": "Starlight Beat",
    "Rice Shower": "Rosy Dreams",
    "Sakura Bakushin O": "Blossom in Learning",
    "Sakura Chiyono O": "Strength in Full Bloom",
    "Satono Diamond": "Natural Brilliance",
    "Seeking the Pearl": "Rocket☆Star",
    "Seiun Sky": "Reeling in the Big One",
    "Silence Suzuka": "Innocent Silence",
    "Smart Falcon": "LOVE☆4EVER",
    "Special Week": "Special Dreamer",
    "Super Creek": "Murmuring Stream",
    "Sweep Tosho": "Platanus Witch",
    "Taiki Shuttle": "Wild Frontier",
    "Tamamo Cross": "Fast as Lightning",
    "Tokai Teio": "Peak Joy",
    "Tosen Jordan": "Jokester ☆ Vibes",
    Vodka: "Wild Top Gear",
    "Wonder Acute": "Butterfly Sting",
    "Yaeno Muteki": "Blazed Head, Covered Fists",
    "Yamanin Zephyr": "Fluttertail Spirit",
    "Yukino Bijin": "Darl'n Snowflake",
}

/**
 * Extracts the character from a preset name by stripping a trailing "(Outfit)" segment.
 * "Maruzensky (Formula R)" -> "Maruzensky"; "Gold Ship" -> "Gold Ship".
 * @param presetName The preset name as stored in characterPresets.ts.
 * @returns The character name.
 */
export function presetCharacter(presetName: string): string {
    return presetName.replace(/\s*\([^)]*\)\s*$/, "").trim()
}

/**
 * Resolves the outfit title for a preset: the bracketed segment when the name carries one,
 * otherwise the character's base-card outfit. Unknown characters return "".
 * @param presetName The preset name as stored in characterPresets.ts.
 * @returns The EN outfit title, or "" when unknown.
 */
export function presetOutfit(presetName: string): string {
    const m = presetName.match(/\(([^)]*)\)\s*$/)
    if (m) return m[1].trim()
    return characterBaseOutfits[presetName.trim()] ?? ""
}

/**
 * Presets with at least one full-arc career completion recorded in the outcome ledger on the
 * maintainer's account, keyed "presetName|scenario". Everything not listed shows as
 * research-graded in the picker. Refresh this list from the outcomes corpus
 * (scripts/analyze-outcomes.mjs) when new completions land.
 */
export const validatedPresets: ReadonlySet<string> = new Set([
    "El Condor Pasa|Trackblazer",
    "Mejiro McQueen (Frontline Elegance)|URA Finale",
    "Mayano Top Gun|URA Finale",
    "Tosen Jordan|URA Finale",
    "Symboli Rudolf (Emperor's Path)|URA Finale",
    "Mejiro Ryan|URA Finale",
    "Mejiro Palmer|URA Finale",
    // A+ 12,611 on 2026-07-24: the Grand Concert scenario's first fully automated completion.
    "Taiki Shuttle|Grand Concert",
    // Three full-arc completions by 2026-07-26, two of them on the same day's queue (A+ 13,352
    // and an A+ turn-75 with a Power 3-star spark) with the whole career-end pipeline unattended.
    "Copano Rickey|Grand Concert",
    // Six URA full-arc completions in the ledger, including the Kashiwa Kinen win the sash
    // profile was built for, plus two Unity Cup completions. Single preset per scenario, so the
    // trainee-name ledger maps unambiguously.
    "Copano Rickey|URA Finale",
    "Copano Rickey|Unity Cup",
    // Completed 2026-07-26 after the mid-career daily-reset interruption was fixed; the career
    // resumed from turn 49 and finished its full arc.
    "Daiwa Scarlet|Grand Concert",
    // Unity Cup and Trackblazer completions map unambiguously to the base presets: the Legacy
    // Farm arm is URA-only, so it cannot be the source of either. Her URA ledger mixes the base
    // build with Legacy Farm careers and stays unpromoted until the corpus refresh resolves it
    // by config fingerprint.
    "Daiwa Scarlet|Unity Cup",
    "Daiwa Scarlet|Trackblazer",
    // The 2026-07-27 four-trainee rotation queue: every career completed its full 75-turn arc at
    // A+ with the whole pipeline unattended (Bakushin's run additionally proved the mid-career
    // resume path: interrupted at turn 59, resumed, completed at est. 14,212, the scenario's
    // best score on record).
    "Sakura Bakushin O|Grand Concert",
    "Super Creek|Grand Concert",
    "Agnes Tachyon|Grand Concert",
    // Biwa's validated run was on her Rouge Caroler outfit, so it does not promote the plain Biwa Hayahide preset,
    // which models her base outfit.
    "Meisho Doto|Grand Concert",
    "Nice Nature|Grand Concert",
    "Tosen Jordan|Grand Concert",
])

/**
 * Validation tier for a (preset, scenario) pair.
 * @param presetName The preset name.
 * @param scenario The career scenario.
 * @returns "validated" when a full career completion is on record, else "research".
 */
export function presetValidation(presetName: string, scenario: string): "validated" | "research" {
    return validatedPresets.has(`${presetName}|${scenario}`) ? "validated" : "research"
}
