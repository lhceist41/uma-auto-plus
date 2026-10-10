export type SaveToPhoneDeps = {
    /** Opens Android's "Save to" dialog and copies the file; "cancelled" when the player backs out. */
    save: (path: string, fileName: string, mimeType: string) => Promise<"saved" | "cancelled">
    alert: (title: string, message: string) => void
    log: (message: string, error: unknown) => void
}

export const SAVE_FAILED_MESSAGE = "The file could not be saved. Try another folder, or use Share instead."
export const EXPORT_FAILED_MESSAGE = "The settings file could not be created. Try again."
export const SAVED_MESSAGE = "The file was saved to the place you picked."

/** Saves a file the app wrote (a file:// URI) through the system "Save to" dialog. A cancelled dialog is silent. */
export const saveToPhone = async (fileUri: string, mimeType: string, deps: SaveToPhoneDeps): Promise<"saved" | "cancelled" | "failed"> => {
    const path = fileUri.replace(/^file:\/\//, "")
    const fileName = path.split("/").pop() ?? "file"
    try {
        const result = await deps.save(path, fileName, mimeType)
        if (result === "saved") deps.alert("Saved", SAVED_MESSAGE)
        return result
    } catch (error) {
        deps.log("Error saving the file to the phone:", error)
        deps.alert("Could Not Save File", SAVE_FAILED_MESSAGE)
        return "failed"
    }
}
