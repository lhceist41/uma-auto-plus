/**
 * Display + provenance metadata for character presets, derived from preset names so the
 * preset objects in characterPresets.ts stay byte-identical (their `name` is the persistence
 * key in settings, rotation snapshots, and the applied-racing snapshot).
 *
 * Preset naming convention: outfit-specific presets are "Character (Outfit)"; plain names
 * model the character's base card, whose outfit title comes from characterBaseOutfits.
 * All base-outfit names below were read from the cards' gametora pages on 2026-07-06.
 */

/** EN base-card outfit title per character, for presets whose name carries no bracket. */
export const characterBaseOutfits: Record<string, string> = {
    "Agnes Tachyon": "tach-nology",
    "Air Groove": "Empress Road",
    "Biwa Hayahide": "pf. Winning Equation...",
    "Daiwa Scarlet": "Peak Blue",
    "El Condor Pasa": "El☆Número 1",
    "Gold Ship": "Red Strife",
    "Grass Wonder": "Stone-Piercing Blue",
    "Haru Urara": "Bestest Prize ♪",
    "Hishi Amazon": "Azure Amazon",
    "King Halo": "King of Emeralds",
    Matikanefukukitaru: "Rising☆Fortune",
    Matikanetannhauser: "Clippety-Tippety-Clop",
    "Mayano Top Gun": "Scramble☆Zone",
    "Mejiro Palmer": "Line Breakthrough",
    "Mejiro Ryan": "Down the Line",
    "Mihono Bourbon": "MB-19890425",
    "Nice Nature": "Poinsettia Ribbon",
    "Sakura Bakushin O": "Blossom in Learning",
    "Super Creek": "Murmuring Stream",
    "Sweep Tosho": "Platanus Witch",
    "Taiki Shuttle": "Wild Frontier",
    "Tosen Jordan": "Jokester ☆ Vibes",
    Vodka: "Wild Top Gear",
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
    "Mihono Bourbon|URA Finale",
    "Mejiro Palmer|URA Finale",
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
