import { releaseNotesUrl, whatsNewEntries } from "../data/whatsNew"

/** What the "What's new" dialog shows for one version. */
export interface WhatsNewContent {
    version: string
    title: string
    highlights: string[]
    releaseNotesUrl: string
}

/** The dialog's content for a version, or null when that version has no highlights. */
export function whatsNewContent(version: string | null | undefined): WhatsNewContent | null {
    if (!version || !Object.prototype.hasOwnProperty.call(whatsNewEntries, version)) return null
    const highlights = whatsNewEntries[version].highlights.filter((line) => line.trim().length > 0)
    if (highlights.length === 0) return null
    return { version, title: `What's new in ${version}`, highlights, releaseNotesUrl: releaseNotesUrl(version) }
}

/** An app that was updated in the same minute it was installed is treated as a fresh install. */
const FRESH_INSTALL_WINDOW_MS = 60_000

/**
 * Whether this is a fresh install (never updated) from Android's first-install and last-update
 * times, or null when either is missing or unreadable, which means the answer is unknown.
 */
export function isFreshInstall(firstInstall: Date | null | undefined, lastUpdate: Date | null | undefined): boolean | null {
    if (!(firstInstall instanceof Date) || !(lastUpdate instanceof Date)) return null
    const first = firstInstall.getTime()
    const last = lastUpdate.getTime()
    if (!Number.isFinite(first) || !Number.isFinite(last)) return null
    return last - first <= FRESH_INSTALL_WINDOW_MS
}

export interface WhatsNewInput {
    /** The running app's version name, or null when it could not be read. */
    currentVersion: string | null
    /** The version whose dialog was already seen (or that a fresh install started on), or null when none is stored. */
    lastSeenVersion: string | null
    /** From [isFreshInstall]: null when unknown. */
    freshInstall: boolean | null
    /** The bot is armed or running. */
    botActive: boolean
    /** The current version has highlights. */
    hasEntry: boolean
}

/**
 * - `show`: open the dialog.
 * - `record`: show nothing, but remember the current version (a fresh install, or a version without highlights).
 * - `wait`: the bot is active; decide again later and change nothing.
 * - `none`: show nothing and change nothing.
 */
export type WhatsNewDecision = "show" | "record" | "wait" | "none"

/**
 * Shows the dialog once per version after an update: never for the version already seen, never on a
 * fresh install (that only records the version), never while the bot is active, and never for a
 * version without highlights or when it cannot tell whether this is an update.
 */
export function decideWhatsNew(input: WhatsNewInput): WhatsNewDecision {
    if (!input.currentVersion) return "none"
    if (input.botActive) return "wait"
    if (input.lastSeenVersion === input.currentVersion) return "none"
    if (input.lastSeenVersion === null) {
        if (input.freshInstall === null) return "none"
        if (input.freshInstall) return "record"
    }
    return input.hasEntry ? "show" : "record"
}
