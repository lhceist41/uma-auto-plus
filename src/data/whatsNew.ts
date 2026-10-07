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
    "1.7.2": {
        highlights: [
            "Grand Concert plays on phones: on screens 1080 pixels wide and taller than 1920 the bot plays Grand Concert careers, finale included, and can reroll sparks at the end. Tested on one 1080x2316 phone; other 1080-wide phones use the same rule but are not tested.",
            'The Grand Concert tutorial\'s "Are you sure?" question is answered, so the bot no longer reopens its menu over and over.',
            "The app's screens fit phones: pages, the menu and the Home log no longer hide behind the navigation bar, the last preset trainee and the Grand Concert Quick Mode option can be tapped, and the keyboard no longer covers the log search.",
            "The Alarm Clock option names are readable in dark mode, page titles and Settings buttons are no longer cut off, and the reset dialog in Settings shows its full text.",
            "A goal that needs a G1 race, or a race of G3 or Pre-OP grade or higher, now enters only races that count for it, and close to its deadline it also enters one marked with a single star instead of ending the career. Not seen in a live career yet.",
            "Restore TP with Items, Allow Carats for TP Restore and Auto-Reroll Sparks show in Run Queue Settings without a run queue.",
        ],
    },
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
