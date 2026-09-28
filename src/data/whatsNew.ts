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
            "Watch your queue from your PC: a live dashboard shows the current run, what the bot is doing and each run's result (Debug Settings, Enable Remote Log Viewer; it needs ADB).",
            "A one-click Windows helper, tools/open-dashboard.cmd (see the README), opens the dashboard for MuMu without typing any adb commands.",
            "The notification shows the queue's progress while the bot runs, for example the run number and the game date.",
            "Home has a Last session card, and the notification after a session says how it really ended.",
            "Tap Update in the update dialog to download the new version inside the app, then confirm Android's install prompt.",
            "Overnight queues are sturdier: the screen stays on while the bot runs, an errored run is retried once, and an interrupted queue can be resumed for 24 hours.",
        ],
    },
}

/** The release notes page for a version. */
export const releaseNotesUrl = (version: string): string => `${RELEASE_NOTES_BASE_URL}v${version}`
