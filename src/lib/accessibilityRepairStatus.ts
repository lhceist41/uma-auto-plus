/**
 * The Home status of accessibility self-repair: whether UMA Auto+ holds WRITE_SECURE_SETTINGS, which
 * lets it turn its own accessibility service back on when an emulator switches it off. The grant
 * probe reports `null` when it failed, and that stays "unknown": nothing is claimed that the probe
 * did not read.
 */

export type AccessibilityRepairState = "granted" | "missing" | "unknown"

export interface AccessibilityRepairStatus {
    state: AccessibilityRepairState
    title: string
    text: string
    /** The exact command that grants the permission, only when it is missing and the package name is known. */
    command: string | null
}

/** The grant command for [packageName], or null when the name is not a plain Android package name. */
export function secureSettingsGrantCommand(packageName: string | null | undefined): string | null {
    if (typeof packageName !== "string" || !/^[A-Za-z][\w]*(\.[A-Za-z][\w]*)+$/.test(packageName)) return null
    return `adb shell pm grant ${packageName} android.permission.WRITE_SECURE_SETTINGS`
}

export function accessibilityRepairStatus(granted: boolean | null | undefined, packageName: string | null | undefined): AccessibilityRepairStatus {
    if (granted === true) {
        return {
            state: "granted",
            title: "Accessibility self-repair: on",
            text: "If the emulator or Android switches off the bot's accessibility service during a run, the bot can switch it back on. If its taps stop landing while the service stays on, restart MuMu or the device.",
            command: null,
        }
    }
    if (granted === false) {
        const command = secureSettingsGrantCommand(packageName)
        return {
            state: "missing",
            title: "Accessibility self-repair: off",
            text: command
                ? "If the emulator or Android switches off the bot's accessibility service during a run, the bot cannot switch it back on, and the queue stops. Run this once from a computer with adb (or on the device with aShell You and Shizuku):"
                : "If the emulator or Android switches off the bot's accessibility service during a run, the bot cannot switch it back on, and the queue stops. Grant UMA Auto+ the WRITE_SECURE_SETTINGS permission once with adb (see Troubleshooting).",
            command,
        }
    }
    return {
        state: "unknown",
        title: "Accessibility self-repair: unknown",
        text: "UMA Auto+ could not check whether it may switch its accessibility service back on.",
        command: null,
    }
}
