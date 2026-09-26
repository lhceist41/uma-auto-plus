/**
 * Setup problems worth knowing about before a normal Start, each of which can quietly end a long
 * unattended run early. Advisory only: Start never waits on these, and the caller always offers
 * "Start anyway".
 *
 * Device facts come from native probes. A probe that fails reports `null`, and an unknown value
 * never produces a warning: the dialog only states what a probe actually read.
 */
import type { Settings } from "../context/BotStateContext"

export interface ScreenTimeoutProbe {
    /** Screen-off timeout in milliseconds, as Android reports it. */
    timeoutMs: number
    /** True when the device is set to stay awake while charging. */
    stayOnWhilePluggedIn: boolean
}

/** Device facts read at Start. `null` means the probe failed or is unavailable. */
export interface PreflightProbes {
    secureSettingsGranted: boolean | null
    screenTimeout: ScreenTimeoutProbe | null
    notificationsEnabled: boolean | null
}

/** A `warning` opens the Start dialog; a `tip` only rides along when a warning already did. */
export type PreflightKind = "warning" | "tip"

export interface PreflightItem {
    id: "tp-restore-off" | "launch-setup-reuse-off" | "accessibility-repair-unavailable" | "short-screen-timeout" | "notifications-off" | "discord-off"
    kind: PreflightKind
    title: string
    text: string
}

/** The screen turning off stops a running bot, so anything shorter than this is flagged. */
export const SHORT_SCREEN_TIMEOUT_MS = 30 * 60 * 1000

/** "12 minutes", "1 minute", or seconds when under a minute. */
export function describeTimeout(ms: number): string {
    if (ms < 60_000) {
        const seconds = Math.max(1, Math.round(ms / 1000))
        return `${seconds} second${seconds === 1 ? "" : "s"}`
    }
    const minutes = Math.round(ms / 60_000)
    return `${minutes} minute${minutes === 1 ? "" : "s"}`
}

/**
 * Everything worth flagging for this Start, warnings first. Settings-only checks always run;
 * device checks run only when their probe returned a value.
 */
export function collectPreflightWarnings(settings: Settings, probes: PreflightProbes): PreflightItem[] {
    const items: PreflightItem[] = []
    const queue = settings.runQueue

    if (queue.enableRunQueue && queue.totalRuns > 1 && !queue.enableTpRestoreWithItems) {
        const careerCost = queue.enableEventBoost ? "Each career costs TP, double with Event Boost on" : "Each career costs TP"
        const rerollCost = queue.enableSparkReroll ? ", and each spark reroll costs 30 TP" : ""
        items.push({
            id: "tp-restore-off",
            kind: "warning",
            title: "Restore TP with Items is off",
            text: `${careerCost}${rerollCost}. If TP runs out, the queue stops before the next career; finished runs are kept.`,
        })
    }

    if (queue.enableRunQueue && !queue.reuseLastLaunchSetup) {
        items.push({
            id: "launch-setup-reuse-off",
            kind: "warning",
            title: "Reuse Last Launch Setup is off",
            text: "The bot will stop at the support deck screen instead of starting the next career.",
        })
    }

    if (probes.secureSettingsGranted === false) {
        items.push({
            id: "accessibility-repair-unavailable",
            kind: "warning",
            title: "Accessibility self-repair is not available",
            text: 'The bot cannot turn its accessibility service back on by itself on this device. If the emulator turns it off during a run, the bot stops. See Troubleshooting: "The emulator killed the Accessibility service (MuMu)".',
        })
    }

    const screen = probes.screenTimeout
    if (screen !== null && !screen.stayOnWhilePluggedIn && screen.timeoutMs > 0 && screen.timeoutMs < SHORT_SCREEN_TIMEOUT_MS) {
        items.push({
            id: "short-screen-timeout",
            kind: "warning",
            title: "Screen timeout is short",
            text: `Android is set to turn the screen off after ${describeTimeout(screen.timeoutMs)}. If the screen turns off, the bot stops. Some emulators, including MuMu, ignore this setting; on a phone or tablet, set a longer timeout (or keep the screen on while charging) for long runs.`,
        })
    }

    if (probes.notificationsEnabled === false) {
        items.push({
            id: "notifications-off",
            kind: "warning",
            title: "Notifications are off",
            text: "Notifications are off for UMA Auto+, so you will not see how a run ended until you open the app.",
        })
    }

    if (!settings.discord.enableDiscordNotifications) {
        items.push({
            id: "discord-off",
            kind: "tip",
            title: "Tip",
            text: "Turn on Discord alerts to hear about a stopped queue right away.",
        })
    }

    return items
}

/** True when the Start dialog should open: at least one warning, never a tip alone. */
export function shouldShowPreflight(items: PreflightItem[]): boolean {
    return items.some((item) => item.kind === "warning")
}

/** The native probe surface, narrowed so tests can stub it. */
export interface PreflightProbeModule {
    hasSecureSettingsGrant(): Promise<boolean>
    getScreenTimeout(): Promise<ScreenTimeoutProbe>
    areNotificationsEnabled(): Promise<boolean>
}

/** Runs every probe; any rejection, missing method, or malformed value becomes `null`. */
export async function readPreflightProbes(module: Partial<PreflightProbeModule> | null | undefined): Promise<PreflightProbes> {
    const read = async <T>(probe: (() => Promise<T>) | undefined, valid: (value: unknown) => value is T): Promise<T | null> => {
        if (typeof probe !== "function") return null
        try {
            const value = await probe()
            return valid(value) ? value : null
        } catch {
            return null
        }
    }
    const isBoolean = (value: unknown): value is boolean => typeof value === "boolean"
    const isScreenTimeout = (value: unknown): value is ScreenTimeoutProbe =>
        typeof value === "object" &&
        value !== null &&
        typeof (value as ScreenTimeoutProbe).timeoutMs === "number" &&
        Number.isFinite((value as ScreenTimeoutProbe).timeoutMs) &&
        typeof (value as ScreenTimeoutProbe).stayOnWhilePluggedIn === "boolean"

    const [secureSettingsGranted, screenTimeout, notificationsEnabled] = await Promise.all([
        read(module?.hasSecureSettingsGrant?.bind(module), isBoolean),
        read(module?.getScreenTimeout?.bind(module), isScreenTimeout),
        read(module?.areNotificationsEnabled?.bind(module), isBoolean),
    ])
    return { secureSettingsGranted, screenTimeout, notificationsEnabled }
}
