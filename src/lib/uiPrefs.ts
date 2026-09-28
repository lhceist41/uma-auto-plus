import { useSyncExternalStore } from "react"
import * as FileSystem from "expo-file-system"

/**
 * App-level UI preferences persisted OUTSIDE the Settings object. Settings serializes to the
 * Kotlin side and feeds the outcome config fingerprint, so pure-UI state like favorites must
 * not live there. Stored as a small JSON file in the app document directory, loaded once and
 * shared through a module-level store so every consumer sees the same state without a provider.
 */
interface UiPrefs {
    /** Presets starred in the preset picker, keyed by PRESET name (one entry per outfit).
     * Favorites used to be keyed by character, which starred every outfit of a trainee at
     * once - wrong for accounts that do not own every outfit variant. */
    favoritePresets: string[]
    /** The app version whose "What's new" dialog was already seen, or that a fresh install started on. */
    whatsNewSeenVersion: string | null
}

const PREFS_FILE = FileSystem.documentDirectory + "uiPrefs.json"

let prefs: UiPrefs = { favoritePresets: [], whatsNewSeenVersion: null }
let loadPromise: Promise<void> | null = null
const listeners = new Set<() => void>()

const emit = () => listeners.forEach((listener) => listener())

/** Loads prefs from disk once; concurrent callers share the same in-flight load. */
const ensureLoaded = (): Promise<void> => {
    if (loadPromise) return loadPromise
    loadPromise = FileSystem.getInfoAsync(PREFS_FILE)
        .then((info) => (info.exists ? FileSystem.readAsStringAsync(PREFS_FILE) : "{}"))
        .then((raw) => {
            const parsed = JSON.parse(raw)
            // Migration: the legacy key stored bare character names, which happen to equal the
            // base outfit's preset name, so carrying them over keeps the base-outfit star lit.
            const stored = Array.isArray(parsed.favoritePresets) ? parsed.favoritePresets : parsed.favoriteCharacters
            prefs = {
                favoritePresets: Array.isArray(stored) ? stored.filter((c: unknown) => typeof c === "string") : prefs.favoritePresets,
                whatsNewSeenVersion: typeof parsed.whatsNewSeenVersion === "string" ? parsed.whatsNewSeenVersion : prefs.whatsNewSeenVersion,
            }
            emit()
        })
        .catch((e) => {
            // A corrupt or unreadable prefs file falls back to defaults; favorites are recoverable state.
            console.warn(`[UiPrefs] Failed to load ${PREFS_FILE}: ${e}`)
        })
    return loadPromise
}

/** Writes after the load, so a save can never replace fields that were not read yet. */
const persist = () => ensureLoaded().then(() => FileSystem.writeAsStringAsync(PREFS_FILE, JSON.stringify(prefs)))

const persistQuietly = () => {
    persist().catch((e) => {
        console.warn(`[UiPrefs] Failed to save ${PREFS_FILE}: ${e}`)
    })
}

const subscribe = (listener: () => void) => {
    listeners.add(listener)
    return () => {
        listeners.delete(listener)
    }
}

const getFavorites = () => prefs.favoritePresets

/**
 * Hook exposing the favorite presets list and a toggle. Favorites are keyed by PRESET name, so
 * starring one outfit never stars a trainee's other outfits (the account may not own them).
 * @returns A tuple of the current favorites and a toggle function.
 */
export function useFavoritePresets(): [string[], (presetName: string) => void] {
    ensureLoaded()
    const favorites = useSyncExternalStore(subscribe, getFavorites)

    return [favorites, toggleFavoritePreset]
}

/** Stars or unstars a preset, keeping every other stored preference. */
export function toggleFavoritePreset(presetName: string) {
    const current = prefs.favoritePresets
    prefs = {
        ...prefs,
        favoritePresets: current.includes(presetName) ? current.filter((c) => c !== presetName) : [...current, presetName],
    }
    emit()
    persistQuietly()
}

/** The app version whose "What's new" dialog was already seen, or null when none is stored. */
export function loadWhatsNewSeenVersion(): Promise<string | null> {
    return ensureLoaded().then(() => prefs.whatsNewSeenVersion)
}

/** Remembers that the player has seen (or a fresh install started on) this version. Rejects when the write fails. */
export function saveWhatsNewSeenVersion(version: string): Promise<void> {
    return ensureLoaded().then(() => {
        prefs = { ...prefs, whatsNewSeenVersion: version }
        return persist()
    })
}
