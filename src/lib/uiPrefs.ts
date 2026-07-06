import { useCallback, useSyncExternalStore } from "react"
import * as FileSystem from "expo-file-system"

/**
 * App-level UI preferences persisted OUTSIDE the Settings object. Settings serializes to the
 * Kotlin side and feeds the outcome config fingerprint, so pure-UI state like favorites must
 * not live there. Stored as a small JSON file in the app document directory, loaded once and
 * shared through a module-level store so every consumer sees the same state without a provider.
 */
interface UiPrefs {
    /** Characters starred in the preset picker. Character names, not preset names. */
    favoriteCharacters: string[]
}

const PREFS_FILE = FileSystem.documentDirectory + "uiPrefs.json"

let prefs: UiPrefs = { favoriteCharacters: [] }
let loadStarted = false
const listeners = new Set<() => void>()

const emit = () => listeners.forEach((listener) => listener())

/** Loads prefs from disk once; concurrent callers share the same in-flight load. */
const ensureLoaded = () => {
    if (loadStarted) return
    loadStarted = true
    FileSystem.getInfoAsync(PREFS_FILE)
        .then((info) => (info.exists ? FileSystem.readAsStringAsync(PREFS_FILE) : "{}"))
        .then((raw) => {
            const parsed = JSON.parse(raw)
            if (Array.isArray(parsed.favoriteCharacters)) {
                prefs = { favoriteCharacters: parsed.favoriteCharacters.filter((c: unknown) => typeof c === "string") }
                emit()
            }
        })
        .catch((e) => {
            // A corrupt or unreadable prefs file falls back to defaults; favorites are recoverable state.
            console.warn(`[UiPrefs] Failed to load ${PREFS_FILE}: ${e}`)
        })
}

const persist = () => {
    FileSystem.writeAsStringAsync(PREFS_FILE, JSON.stringify(prefs)).catch((e) => {
        console.warn(`[UiPrefs] Failed to save ${PREFS_FILE}: ${e}`)
    })
}

const subscribe = (listener: () => void) => {
    listeners.add(listener)
    return () => {
        listeners.delete(listener)
    }
}

const getFavorites = () => prefs.favoriteCharacters

/**
 * Hook exposing the favorite characters list and a toggle. Favorites are keyed by CHARACTER
 * name (one star covers all of a character's outfits and scenarios).
 * @returns A tuple of the current favorites and a toggle function.
 */
export function useFavoriteCharacters(): [string[], (character: string) => void] {
    ensureLoaded()
    const favorites = useSyncExternalStore(subscribe, getFavorites)

    const toggleFavorite = useCallback((character: string) => {
        const current = prefs.favoriteCharacters
        prefs = {
            favoriteCharacters: current.includes(character) ? current.filter((c) => c !== character) : [...current, character],
        }
        emit()
        persist()
    }, [])

    return [favorites, toggleFavorite]
}
