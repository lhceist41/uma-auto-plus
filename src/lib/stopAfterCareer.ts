import type { AlertButton, AlertOptions } from "react-native"

/** The native call behind "Stop after this career": Kotlin answers with the request as it now stands. */
export interface StopAfterCareerNative {
    setStopAfterCareer(requested: boolean): Promise<unknown>
}

/** React Native's `Alert.alert` signature, injected so the confirm can be tested without a device. */
export type ShowAlert = (title: string, message?: string, buttons?: AlertButton[], options?: AlertOptions) => void

export const STOP_AFTER_CAREER_FAILED = "Could not reach the bot to change Stop after this career. Nothing changed; try again."

/**
 * Asks before pausing the queue. Uses the system alert: the in-app dialog's confirm button closed the
 * dialog without sending the request while a run was on screen (live 2026-09-28, 2 of 2 taps).
 */
export function confirmStopAfterCareer(showAlert: ShowAlert, onConfirm: () => void): void {
    showAlert(
        "Stop after this career?",
        "The bot finishes the career it is on, including its end steps, then pauses the queue. Pressing Start later continues with the next run.",
        [
            { text: "Keep going", style: "cancel" },
            { text: "Stop after this career", onPress: onConfirm },
        ],
        { cancelable: true }
    )
}

/**
 * Sends the request and resolves with the request as Kotlin now holds it, so Home shows what the bot
 * will do rather than what was asked. Rejects with a player message when the bot does not confirm it.
 */
export async function sendStopAfterCareer(native: StopAfterCareerNative, requested: boolean): Promise<boolean> {
    let now: unknown
    try {
        now = await native.setStopAfterCareer(requested)
    } catch {
        throw new Error(STOP_AFTER_CAREER_FAILED)
    }
    if (typeof now !== "boolean") throw new Error(STOP_AFTER_CAREER_FAILED)
    return now
}
