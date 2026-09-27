/**
 * Setup problems worth knowing about before a normal Start, each of which can quietly end a long
 * unattended run early. Advisory only: Start never waits on these, and the caller always offers
 * "Start anyway".
 *
 * Device facts come from native probes. A probe that fails reports `null`, and an unknown value
 * never produces a warning: the dialog only states what a probe actually read.
 */
import type { Settings } from "../context/BotStateContext"

/** Device facts read at Start. `null` means the probe failed or is unavailable. */
export interface PreflightProbes {
    secureSettingsGranted: boolean | null
    notificationsEnabled: boolean | null
}

/** A `warning` opens the Start dialog; a `tip` only rides along when a warning already did. */
export type PreflightKind = "warning" | "tip"

export interface PreflightItem {
    id: "tp-restore-off" | "launch-setup-reuse-off" | "accessibility-repair-unavailable" | "notifications-off" | "discord-off"
    kind: PreflightKind
    title: string
    text: string
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

    // No screen-timeout check: a running session keeps the screen on (KeepScreenOn.kt), so the
    // timeout cannot end it. The power button still can, which no setting reveals.

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
    const [secureSettingsGranted, notificationsEnabled] = await Promise.all([
        read(module?.hasSecureSettingsGrant?.bind(module), isBoolean),
        read(module?.areNotificationsEnabled?.bind(module), isBoolean),
    ])
    return { secureSettingsGranted, notificationsEnabled }
}
