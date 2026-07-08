import { Settings } from "../context/BotStateContext"
import { characterPresets } from "../data/characterPresets"
import { convertSettingsToBatch } from "./settingsUtils"

/**
 * One trainee in a rotation cycle. Mirrors `Settings["runQueue"]["traineeRotation"]`.
 *
 * - `inGameName`   full "[Outfit] Name" the in-game Trainee Select preview shows. This is what
 *                  the Kotlin navigator OCR-matches against, so it must read exactly as the game
 *                  renders it (outfit prefix included — the same character can own several outfits).
 * - `presetKey`    the `characterPresets` entry name ("Name" or "Name (Outfit)").
 * - `scenario`     which of that trainee's per-scenario presets to apply, and the authoritative
 *                  scenario the career runs under.
 */
export interface RotationEntry {
    inGameName: string
    presetKey: string
    scenario: string
    /** Sibling-outfit names the in-game matcher must skip for a bare base-name target (see BotStateContext). */
    excludeOutfits?: string[]
}

export interface RotationBatchRow {
    category: string
    key: string
    value: unknown
}

export interface BuildRotationResult {
    /** Namespaced rows to persist: `rot{i}_{category}` so the Kotlin queue can copy them verbatim. */
    rows: RotationBatchRow[]
    /** Entries whose (presetKey, scenario) did not resolve to a preset — a config error to surface. */
    missing: { index: number; presetKey: string; scenario: string }[]
}

// Categories that must NEVER be swapped per-trainee: they hold the queue/rotation control state
// itself (and the persisted run cursor). Swapping them mid-queue would clobber the very config
// driving the rotation. Everything else is the trainee's active gameplay config.
// `discord` is global config (one webhook for the user, not per-trainee) and holds the bot token -
// snapshotting it duplicated the secret into a rot{i}_discord row for every trainee. Excluded so the
// live discord rows are left untouched at a switch (same as runQueue/queueState).
// `debug` is device/session-level (Debug Mode, OCR tuning, test flags): stale snapshot rows kept
// reverting the user's Debug Mode toggle at every run start while the UI still showed it enabled.
const SNAPSHOT_DENYLIST = new Set(["runQueue", "queueState", "discord", "debug"])

// `category.key` rows that are static reference data, identical across every trainee: the bundled
// event databases (written at bootstrap by useBootstrap.populateEventData) and the bulk race
// database. Snapshotting them would replicate ~580 KB per rotation entry for no benefit. Safe to
// omit because applyRotationSnapshot upserts (INSERT OR REPLACE) only the snapshot rows, so at a
// switch the live rows for these keys are left in place — and they are the same for every trainee.
// Mirrors the bulk-data fields the export path already strips in useSettingsManager.
// The bulk reference databases (above) plus two transient/UI-state misc keys: currentProfileName is
// the active profile label and formattedSettingsString is a cached human-readable dump - both are
// stale snapshots of the moment the rotation was built and reapplying them per-switch would clobber
// the live profile label / settings echo. Mirrors the fields the export path already strips.
const SNAPSHOT_KEY_DENYLIST = new Set([
    "trainingEvent.characterEventData",
    "trainingEvent.supportEventData",
    "trainingEvent.scenarioEventData",
    "racing.racingPlanData",
    "misc.currentProfileName",
    "misc.formattedSettingsString",
])

/**
 * Builds each rotation trainee's full settings snapshot as namespaced SQLite rows.
 *
 * The merge mirrors Home's `handlePresetChange` exactly — deep-merge the preset onto the current
 * `base`, preserve the user's per-event override maps and skill-spend threshold, force the entry's
 * scenario, and stamp the racing-drift snapshot — so a rotation run behaves identically to the user
 * having hand-applied that preset. Each resulting row is re-keyed `rot{i}_{category}`; at a switch boundary the Kotlin
 * queue copies `rot{i}_*` straight into the live `settings` rows, so no serialization logic is
 * duplicated on the Kotlin side.
 *
 * @param base     the settings to merge each preset onto (the user's live settings at queue start).
 * @param rotation the ordered trainee cycle.
 */
export function buildRotationSnapshotRows(base: Settings, rotation: RotationEntry[]): BuildRotationResult {
    const rows: RotationBatchRow[] = []
    const missing: BuildRotationResult["missing"] = []

    rotation.forEach((entry, i) => {
        const preset = characterPresets.find((p) => p.name === entry.presetKey && p.scenario === entry.scenario)
        if (!preset) {
            missing.push({ index: i, presetKey: entry.presetKey, scenario: entry.scenario })
            return
        }

        // Deep-merge preset onto a clone of the base (same shallow-per-category merge Home uses).
        const merged: any = JSON.parse(JSON.stringify(base))
        for (const [category, values] of Object.entries(preset.settings)) {
            if (typeof values === "object" && values !== null && !Array.isArray(values)) {
                merged[category] = { ...(merged[category] ?? {}), ...values }
            } else {
                merged[category] = values
            }
        }

        // Preserve the user's per-event override maps: presets ship empty `{}` placeholders that a
        // naive spread would clobber. (Identical to handlePresetChange — these maps are deck/scenario
        // specific, not character specific.)
        if (merged.trainingEvent) {
            const presetSupport = (preset.settings as any)?.trainingEvent?.supportEventOverrides || {}
            const presetScenario = (preset.settings as any)?.trainingEvent?.scenarioEventOverrides || {}
            merged.trainingEvent.supportEventOverrides = { ...(base.trainingEvent?.supportEventOverrides || {}), ...presetSupport }
            merged.trainingEvent.scenarioEventOverrides = { ...(base.trainingEvent?.scenarioEventOverrides || {}), ...presetScenario }
        }

        // Preserve the user's skill-spend threshold + on/off switch. Every preset ships a uniform
        // skillPointCheck (350) that the spread above would otherwise apply over whatever the user set
        // in Skill Settings, silently discarding it at every rotation switch. The per-preset skill plan
        // (which skills to buy) still comes from the preset; only the spend timing and enable flag are
        // the user's global choice. (Mirrors handlePresetChange.)
        if (merged.skills && base.skills) {
            merged.skills.skillPointCheck = base.skills.skillPointCheck
            merged.skills.enableSkillPointCheck = base.skills.enableSkillPointCheck
        }

        // The rotation entry's scenario is authoritative for which Campaign subclass runs.
        if (merged.general) merged.general.scenario = entry.scenario

        // Stamp the racing-drift snapshot so Game.warnOnRacingConfigDrift can flag a mismatch at
        // career start, per trainee (a silently-off mandatory flag cost a career).
        if (merged.racing) {
            let plannedRaceCount = 0
            try {
                plannedRaceCount = merged.racing.racingPlan ? JSON.parse(merged.racing.racingPlan).length : 0
            } catch {
                plannedRaceCount = 0
            }
            merged.racing.appliedRacingSnapshot = JSON.stringify({
                presetName: entry.presetKey,
                scenario: entry.scenario,
                enableRacingPlan: merged.racing.enableRacingPlan,
                enableMandatoryRacingPlan: merged.racing.enableMandatoryRacingPlan,
                plannedRaceCount,
            })
        }

        const batch = convertSettingsToBatch(merged).filter((r) => !SNAPSHOT_DENYLIST.has(r.category) && !SNAPSHOT_KEY_DENYLIST.has(`${r.category}.${r.key}`))
        for (const r of batch) {
            rows.push({ category: `rot${i}_${r.category}`, key: r.key, value: r.value })
        }
    })

    return { rows, missing }
}
