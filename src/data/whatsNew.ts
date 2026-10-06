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
            "Careers run on phones now: on screens 1080 pixels wide and taller than 1920 the bot places its taps and reads correctly (tested on one 1080x2316 phone). Grand Concert needs 1080x1920 for now and stops before Start Career without spending TP on other sizes.",
            "A run on a phone that stops because Android stopped delivering the bot's taps now tells you to turn UMA Auto+ off and on again in Settings > Accessibility.",
            "The opening \"Would you like to skip this scene?\" question on a fresh game install is answered, and a cinematic that ends mid-tap no longer stops the run.",
            "A \"+4\" training gain is no longer read as \"+41\".",
            "The bot no longer buys a skill's upgrade while logging it as the negative (×) skill, and never fills rank spending with negative skills.",
            "Phone setup is easier: Android lists the accessibility service as UMA Auto+ (if you turned it on, it stays on), and the Home page and log say which screens work and warn when the floating button may cover what the bot reads.",
            "Dialogs, like this one, use the screen width instead of a narrow column.",
        ],
    },
}

/** The release notes page for a version. */
export const releaseNotesUrl = (version: string): string => `${RELEASE_NOTES_BASE_URL}v${version}`
