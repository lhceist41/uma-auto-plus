/**
 * Display-side mirror of the Kotlin mood-floor parser (bot/Campaign.kt).
 *
 * Kotlin resolves the acting value on-device with a case-insensitive match:
 * `"normal" -> Mood.NORMAL`, `"great" -> Mood.GREAT`, and anything else falls back to
 * `Mood.GOOD`. Only those three strings are meaningful, so those three are what the settings UI
 * may offer; the `Mood` enum's AWFUL and BAD members are deliberately NOT selectable, because
 * Kotlin would silently resolve them to GOOD and the control would be lying about what it does.
 * Both sides pin the same table in their test suites, so a change to one without the other fails
 * a test rather than silently drifting.
 */

export type MoodFloor = "Normal" | "Good" | "Great"

/** The shipped default. `Good` preserves the historical `mood < Mood.GOOD` recovery behavior. */
export const DEFAULT_MOOD_FLOOR: MoodFloor = "Good"

/** Every value the Kotlin parser resolves to a distinct Mood, in ascending strictness. */
export const MOOD_FLOORS: readonly MoodFloor[] = ["Normal", "Good", "Great"]

/**
 * The mood floor a preset apply must stamp: the preset's own value when it declares a recognized
 * one, else the default.
 *
 * This is preset-owned state, not a global user preference. It exists for trainees whose event
 * chain contains a mood-gated trap (documented in BOT_OVERVIEW.md), so it belongs to the trainee
 * build the same way stat prioritization and preferred distance do. Without an explicit stamp, a
 * preset that never sets the field inherits whatever the PREVIOUS preset left behind through the
 * category spread: applying a trainee with a strict floor and then any other trainee left the
 * strict floor in place indefinitely, with no way to see or change it. That silent carry-over
 * also splits the outcome-corpus config arm, since the field is part of the run fingerprint.
 *
 * Matching mirrors Kotlin: case-insensitive, unrecognized values fall back to the default rather
 * than propagating. Resolution depends only on the preset and this table, never on which trainee
 * ran before, and never on deck contents.
 */
export function presetMoodFloorOf(presetSettings: unknown): MoodFloor {
    const raw = (presetSettings as { training?: { moodFloor?: unknown } } | null | undefined)?.training?.moodFloor
    if (typeof raw !== "string") return DEFAULT_MOOD_FLOOR
    const match = MOOD_FLOORS.find((floor) => floor.toLowerCase() === raw.trim().toLowerCase())
    return match ?? DEFAULT_MOOD_FLOOR
}

/**
 * How the bot behaves at each floor, for the settings control. Phrased in terms of what the bot
 * does, since the floor is the threshold below which it spends a turn recovering mood.
 */
export function moodFloorLabel(floor: MoodFloor): string {
    switch (floor) {
        case "Normal":
            return "Normal (recover only below Normal)"
        case "Great":
            return "Great (always top up to Great)"
        default:
            return "Good (default)"
    }
}
