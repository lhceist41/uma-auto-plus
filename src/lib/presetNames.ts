import { characterPresets } from "../data/characterPresets"
import { presetCharacter, presetOutfit } from "../data/presetMeta"

// Preset-name parsing and the player-facing outfit text. Imports no other lib module, so settingsUtils
// and rotationSnapshots can both use it without importing each other.

/**
 * Preset names read "Character" or "Character (Suffix)". Usually the suffix is an OUTFIT
 * ("El Condor Pasa (Kukulkan Warrior)"), which the Trainee Select banner renders as
 * "[Kukulkan Warrior] El Condor Pasa". Build variants like "(Legacy Farm)" are NOT outfits -
 * they are alternate presets of the base character, and no "[Legacy Farm]" banner exists
 * in-game. Deriving one as an OCR target makes the navigator scan the entire roster, find
 * nothing above the match threshold, and stop the queue. Keep this set in sync with any
 * future non-outfit suffix presets.
 */
const NON_OUTFIT_SUFFIXES = new Set(["Legacy Farm"])

/** Splits a preset name into base character name and outfit (absent for plain/variant names). */
export function parsePresetName(presetName: string): { base: string; outfit?: string } {
    const m = presetName.match(/^(.*?)\s*\(([^)]*)\)\s*$/)
    if (!m) return { base: presetName.trim() }
    const suffix = m[2].trim()
    return NON_OUTFIT_SUFFIXES.has(suffix) ? { base: m[1].trim() } : { base: m[1].trim(), outfit: suffix }
}

/**
 * The canonical in-game trainee identity for a preset display name: the preset's explicit
 * `traineeName` when set, otherwise the display name unchanged. Decouples a variant/farming display
 * name (e.g. "Super Creek (Blue Farm)") from the trainee it actually selects ("Super Creek"). General
 * - driven by the preset's own declaration, never by a hard-coded suffix.
 */
export function presetTraineeName(presetName: string): string {
    return characterPresets.find((p) => p.name === presetName)?.traineeName ?? presetName
}

/**
 * The outfit text a player sees for a preset: "[Rouge Caroler]" or "[MB-19890425]" as the game
 * titles the card, "Legacy Farm build" for a build variant, "" when no outfit is known.
 */
export function presetOutfitLabel(presetName: string): string {
    const outfit = presetOutfit(presetName)
    if (!outfit) return ""
    const hasSuffix = /\([^)]*\)\s*$/.test(presetName)
    return hasSuffix && !parsePresetName(presetTraineeName(presetName)).outfit ? `${outfit} build` : `[${outfit}]`
}

/** "[Outfit] · Scenario" under a preset's character name; either part is dropped when unknown. */
export function presetSubtitle(presetName: string, scenario: string): string {
    return [presetOutfitLabel(presetName), scenario].filter(Boolean).join(" · ")
}

/** "Character · [Outfit] · Scenario": a preset named in a list or warning, never by its raw key. */
export function presetLine(presetName: string, scenario: string): string {
    return [presetCharacter(presetName), presetSubtitle(presetName, scenario)].filter(Boolean).join(" · ")
}
