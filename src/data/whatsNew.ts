/** One release's highlights for the "What's new" dialog: plain one-line sentences, in the order they are shown. */
export interface WhatsNewEntry {
    highlights: string[]
}

export const RELEASE_NOTES_BASE_URL = "https://github.com/lhceist41/uma-auto-plus/releases/tag/"

/**
 * Highlights by app version (the version name, for example "1.6.0"). A version without an entry shows
 * no dialog. To announce a new release, add its entry here.
 */
export const whatsNewEntries: Record<string, WhatsNewEntry> = {
    "1.7.1": {
        highlights: [
            "Start no longer freezes on phones with Android 14 or newer (seen on Android 16).",
            "The bot no longer buys a skill's upgrade while logging it as the negative (×) skill, and never fills rank spending with negative skills.",
            "Android lists the accessibility service as UMA Auto+ now; if you already turned it on, it stays on.",
            "Dialogs, like this one, use the screen width instead of a narrow column.",
        ],
    },
}

/** The release notes page for a version. */
export const releaseNotesUrl = (version: string): string => `${RELEASE_NOTES_BASE_URL}v${version}`
