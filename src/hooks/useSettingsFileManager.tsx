import { useState, useContext } from "react"
import * as DocumentPicker from "expo-document-picker"
import * as Sharing from "expo-sharing"
import * as FileSystem from "expo-file-system"
import { useNavigation } from "@react-navigation/native"
import { useSettings } from "../context/SettingsContext"
import { BotStateContext, Settings, defaultSettings } from "../context/BotStateContext"
import { logErrorWithTimestamp } from "../lib/logger"

/** Format a value as a human-readable string for the import preview dialog. */
const formatValue = (value: any): string => {
    if (value == null) return "null"
    if (typeof value === "boolean") return value ? "Enabled" : "Disabled"
    if (Array.isArray(value)) return value.length === 0 ? "[]" : value.join(", ")
    if (typeof value === "object") return JSON.stringify(value)
    return String(value)
}

/** Deep equality (recurses objects/arrays) so we detect real value changes, not reference identity. */
const deepEqual = (a: any, b: any): boolean => {
    if (a === b) return true
    if (a == null || b == null || typeof a !== typeof b) return false

    if (Array.isArray(a) && Array.isArray(b)) {
        return a.length === b.length && a.every((item, i) => deepEqual(item, b[i]))
    }

    if (typeof a === "object" && typeof b === "object") {
        // Sort keys so comparison is order-independent.
        const keysA = Object.keys(a).sort()
        const keysB = Object.keys(b).sort()
        return keysA.length === keysB.length && keysA.every((key) => keysB.includes(key) && deepEqual(a[key], b[key]))
    }

    return false
}

/**
 * Diff two `Settings` objects: walk every category/key in `imported`, deep-compare against
 * `current`, and return only the keys that would actually change on import.
 */
const compareSettings = (current: Settings, imported: Settings) => {
    const changes: { category: string; key: string; oldValue: any; newValue: any }[] = []

    for (const category of Object.keys(imported) as (keyof Settings)[]) {
        const currentCategory = current[category]
        const importedCategory = imported[category]

        if (!currentCategory || !importedCategory) continue

        for (const key of Object.keys(importedCategory)) {
            // Skip large blob fields that don't belong in the preview diff.
            if ((category === "racing" && key === "racingPlanData") || (category === "misc" && key === "formattedSettingsString")) {
                continue
            }

            const currentValue = (currentCategory as any)[key]
            const importedValue = (importedCategory as any)[key]

            if (!deepEqual(currentValue, importedValue)) {
                changes.push({ category, key, oldValue: currentValue, newValue: importedValue })
            }
        }
    }

    return changes
}

/**
 * Recursively merge `source` into `target` (nested objects merged, not replaced). Used to
 * overlay imported settings onto defaults so every required field is present.
 */
const deepMerge = <T extends Record<string, any>>(target: T, source: Partial<T>): T => {
    const output = { ...target }
    for (const key in source) {
        if (source[key] && typeof source[key] === "object" && !Array.isArray(source[key]) && source[key] !== null) {
            output[key] = deepMerge((target[key] || {}) as Record<string, any>, source[key] as any) as T[Extract<keyof T, string>]
        } else if (source[key] !== undefined) {
            output[key] = source[key] as T[Extract<keyof T, string>]
        }
    }
    return output
}

/**
 * Read a JSON settings file and merge it onto defaults so all fields are populated, without
 * applying it to app state. Lets the caller preview changes first. Throws on read/parse failure.
 * @param fileUri - URI/path to the JSON settings file.
 */
const loadFromJSONFile = async (fileUri: string): Promise<Settings> => {
    try {
        const data = await FileSystem.readAsStringAsync(fileUri)
        const parsed = JSON.parse(data) as Settings
        return deepMerge(defaultSettings, parsed as Partial<Settings>)
    } catch (error) {
        logErrorWithTimestamp(`Error reading settings from JSON file: ${error}`)
        throw error
    }
}

export interface SettingsChange {
    /** The category of the setting that changed. */
    category: string
    /** The key of the setting that changed. */
    key: string
    /** The old value of the setting that changed. */
    oldValue: any
    /** The new value of the setting that changed. */
    newValue: any
    /** The formatted old value of the setting that changed. */
    formattedOldValue: string
    /** The formatted new value of the setting that changed. */
    formattedNewValue: string
}

/**
 * Hook for managing settings file operations (import/export) with file picker and restart prompts.
 * @returns An object containing the state and functions for managing settings file operations.
 */
export const useSettingsFileManager = () => {
    const [showImportDialog, setShowImportDialog] = useState(false)
    const [showResetDialog, setShowResetDialog] = useState(false)
    const [importPreviewChanges, setImportPreviewChanges] = useState<SettingsChange[]>([])
    const [pendingImportUri, setPendingImportUri] = useState<string | null>(null)

    const { importSettings, exportSettings } = useSettings()
    const bsc = useContext(BotStateContext)
    const navigation = useNavigation()

    /** Clear the import-preview state (pending URI and pending changes). */
    const clearPreviewState = () => {
        setPendingImportUri(null)
        setImportPreviewChanges([])
    }

    /**
     * Perform the actual import from `fileUri`, show the success dialog on success, and clear preview state.
     */
    const confirmImportSettings = async (fileUri: string) => {
        if (!fileUri) return

        try {
            const success = await importSettings(fileUri)
            if (success) {
                setShowImportDialog(true)
            }
            clearPreviewState()
        } catch (error) {
            logErrorWithTimestamp("Error importing settings:", error)
        }
    }

    /**
     * Load the file, diff it against current settings, format the changes, and navigate to the
     * preview screen. Runs when the user picks a file, before anything is applied.
     */
    const compareAndPreviewSettings = async (fileUri: string) => {
        try {
            const importedSettings = await loadFromJSONFile(fileUri)
            const changes = compareSettings(bsc.settings, importedSettings)

            const formattedChanges = changes.map((change) => ({
                ...change,
                formattedOldValue: formatValue(change.oldValue),
                formattedNewValue: formatValue(change.newValue),
            }))

            setPendingImportUri(fileUri)
            setImportPreviewChanges(formattedChanges)
            ;(navigation as any).navigate("ImportSettingsPreview", {
                changes: formattedChanges,
                fileUri: fileUri,
            })
        } catch (error) {
            logErrorWithTimestamp("Error comparing settings:", error)
        }
    }

    /** Cancel the import preview. */
    const cancelImportPreview = clearPreviewState

    /**
     * Open the system document picker for a JSON settings file, then preview the diff instead of
     * importing immediately.
     */
    const handleImportSettings = async () => {
        try {
            const result = await DocumentPicker.getDocumentAsync({
                type: "application/json",
                copyToCacheDirectory: true,
            })

            if (result.canceled || !result.assets?.[0]) return

            await compareAndPreviewSettings(result.assets[0].uri)
        } catch (error) {
            logErrorWithTimestamp("Error importing settings:", error)
        }
    }

    /** Export current settings to a JSON file and hand it to the system share dialog. */
    const handleExportSettings = async () => {
        try {
            const fileUri = await exportSettings()
            if (fileUri && (await Sharing.isAvailableAsync())) {
                await Sharing.shareAsync(fileUri, {
                    mimeType: "application/json",
                    dialogTitle: "Export Settings",
                })
            }
        } catch (error) {
            logErrorWithTimestamp("Error exporting settings:", error)
        }
    }

    /** Confirm the import using the pending URI held in state. */
    const confirmPendingImport = async () => {
        if (pendingImportUri) {
            await confirmImportSettings(pendingImportUri)
        }
    }

    return {
        handleImportSettings,
        handleExportSettings,
        showImportDialog,
        setShowImportDialog,
        showResetDialog,
        setShowResetDialog,
        confirmImportSettings,
        confirmPendingImport,
        cancelImportPreview,
        importPreviewChanges,
        pendingImportUri,
        clearPreviewState,
    }
}
