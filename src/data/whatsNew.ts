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
    "1.7.0": {
        highlights: [
            "Grand Concert is fully supported: no experimental label, and it runs unattended with queues, trainee rotation and TP restore.",
            "Every released Global trainee outfit has presets for all four scenarios, shown the way the game titles them.",
            "Stop keeps your queue, and the bot stops a career it can tell is of another trainee or scenario instead of playing it with the wrong settings.",
            "A frozen game is restarted for you where Android allows it.",
            "Better careers: lost goal races are retried, hinted trainings no longer beat much better ones, and the last skill points buy the best skills.",
            "On MuMu for Windows, the dashboard helper from the release page opens the dashboard in one double-click.",
        ],
    },
}

/** The release notes page for a version. */
export const releaseNotesUrl = (version: string): string => `${RELEASE_NOTES_BASE_URL}v${version}`
