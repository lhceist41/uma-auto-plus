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
    "1.6.0": {
        highlights: [
            "Watch your queue from your PC on a live dashboard (Debug Settings, Enable Remote Log Viewer; on MuMu for Windows tools/open-dashboard.cmd opens it).",
            "Stop after this career: the bot finishes the career it is playing, then pauses the queue.",
            "The notification shows live progress and how a session really ended, and Home has a Last session card.",
            "Tap Update in the update dialog to install a new version inside the app.",
            "Sturdier overnight queues: the screen stays on, errors are retried, and the notification's Stop button really stops the bot.",
            "Applying a preset no longer resets your timing, OCR, display or stop settings.",
        ],
    },
}

/** The release notes page for a version. */
export const releaseNotesUrl = (version: string): string => `${RELEASE_NOTES_BASE_URL}v${version}`
