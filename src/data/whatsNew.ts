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
    "1.7.5": {
        highlights: [
            "On long runs on MuMu the bot now frees its unused memory itself, because Android 12 stopped doing it. Memory left after each cleanup stayed level over 7 careers in one run in our tests.",
            'Team Trials taps past the "NEW HIGH SCORE!" banner and closes the "Story Unlocked" dialog instead of stopping, and passes the weekly class result screen. Daily Races waits for its results and reads an empty ticket counter correctly.',
            "Grand Concert skill points are saved for an offered gold skill, which is bought as soon as it is affordable, and spent before the Finals instead of staying unspent.",
            "New, off by default: Rewards and Club in Settings. After Daily Races or Team Trials the bot can collect Missions and Presents and make a Club item request (shoes, a Winner's Sash, or the sash you hold fewest of). It never donates.",
            "On 1080-wide phones that draw the game's text smaller, labels are now read at slightly smaller sizes (not tested live). The log warns at Start when the screen capture is smaller than the screen, and Home warns when a 1080x1920 screen is not at 240 DPI.",
            "The story event's Event Home popup and the first-time New Scenario popup are closed, and the Trackblazer shop is waited for (not seen live yet).",
        ],
    },
    "1.7.4": {
        highlights: [
            "Daily Races spends all your tickets in one multi-race, and Team Trials plays matches until your Race Points run out or your Max Matches Per Session is reached. Opponent To Pick has a Random choice.",
            "A frozen game is restarted in careers, Team Trials and Daily Races. On Android 14 and newer the bot can only bring the game back to the front.",
            "Grand Concert uses the Infirmary now, and when the concerts are behind schedule it trains instead of racing while enough race turns are left to earn the fans for the next goal.",
            "A Recreation date the bot cannot start is closed and it rests instead, so low energy no longer repeats the same turn.",
            "Careers log one plain line per race saying which race and why.",
            "Fewer false stops on healthy rests and outings, one tap per training tab, and better gain, stat and score reads.",
            "A stopped run queue resumes on the right slot and run, and Start works from Legacy Select or Support Formation. Share bug report on Home and Save to Phone in Settings make bug reports easy.",
            "The trainee scan reads the last roster row on tall phones. Zenno Rob Roy's URA Finale preset is validated, and several event and preset fixes are in.",
        ],
    },
    "1.7.3": {
        highlights: [
            "Zenno Rob Roy has presets for URA Finale, Unity Cup, Trackblazer and Grand Concert. They are research-based: no full career with her has been played yet.",
            "More training events are recognized: those of Daiwa Scarlet's and Vodka's newest outfits, of the [Are You Merry?] Air Groove and [Glad Tidings] Narita Brian support cards, and of Sakura Laurel's support cards. Before, the bot took the first option for them.",
            "The bot no longer takes a trainee's other outfit when the game's name banner is read with a small slip. If the only outfit you own is another one, the run still stops before Start Career and tells you which preset to pick.",
        ],
    },
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
