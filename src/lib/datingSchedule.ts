/**
 * Presets for the support-card dating schedule: which career turns to pin for recreation outings
 * and where the Pure Passion final outing lands. Turn numbers are 1-indexed career turns (1-72).
 *
 * The turn lists follow the community timing for each Group card's outing chain: regular outings
 * spread across Classic/Senior with the final held so Pure Passion's ~3-turn friendship-training
 * buff covers Senior summer camp. Team Sirius does not time Pure Passion (purePassionTurn -1), so
 * its chain simply completes on its pinned turns.
 */
export interface DatingSchedulePreset {
    /** Display label for the preset dropdown. */
    label: string
    /** 1-indexed career turns pinned for regular recreation outings. */
    recreationTurns: number[]
    /** The single turn pinned for the final outing / Pure Passion activation; -1 = untimed. */
    purePassionTurn: number
    /** Total outings in the card's chain. The bot corrects this live from the in-game "X/Y" progress. */
    totalOutings: number
}

/** Sentinel preset key meaning the user hand-edited the turns. */
export const DATING_SCHEDULE_CUSTOM = "custom"

export const DATING_SCHEDULE_PRESETS: Record<string, DatingSchedulePreset> = {
    siriusSenior: {
        label: "Team Sirius",
        recreationTurns: [29, 35, 43, 47, 52, 55, 58],
        purePassionTurn: -1,
        totalOutings: 7,
    },
    throneSenior: {
        label: "Heirs to the Throne - Senior Summer",
        recreationTurns: [35, 43, 52, 58],
        // The chain is 4 regular outings + the Pure Passion final = 5 total. Upstream's TS preset
        // says 4 (its own Kotlin docs and tests say 5); 5 is correct, and the live "X/Y" read
        // overrides this the first time the partner dialog opens anyway.
        purePassionTurn: 60,
        totalOutings: 5,
    },
}
