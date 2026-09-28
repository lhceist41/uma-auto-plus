import * as Application from "expo-application"
import { useCallback, useEffect, useRef, useState } from "react"
import { logWarningWithTimestamp } from "../lib/logger"
import { loadWhatsNewSeenVersion, saveWhatsNewSeenVersion } from "../lib/uiPrefs"
import { decideWhatsNew, isFreshInstall, whatsNewContent, type WhatsNewContent } from "../lib/whatsNew"

/**
 * The one-time "What's new" dialog after an update. Decides once the native session state is known
 * and the bot is not active; a fresh install only records its version. The version is stored when the
 * dialog is dismissed, so a dialog closed by killing the app shows again on the next open.
 *
 * @param botActive The bot is armed or running.
 * @param sessionKnown The native session state has been read at least once.
 */
export function useWhatsNew(botActive: boolean, sessionKnown: boolean): { content: WhatsNewContent | null; dismiss: () => void } {
    const [content, setContent] = useState<WhatsNewContent | null>(null)
    const decided = useRef(false)
    const botActiveNow = useRef(botActive)
    botActiveNow.current = botActive

    useEffect(() => {
        if (!sessionKnown || botActive || decided.current) return
        decided.current = true
        const currentVersion = Application.nativeApplicationVersion
        Promise.all([loadWhatsNewSeenVersion(), Application.getInstallationTimeAsync().catch(() => null), Application.getLastUpdateTimeAsync().catch(() => null)])
            .then(([lastSeenVersion, firstInstall, lastUpdate]) => {
                const shown = whatsNewContent(currentVersion)
                const decision = decideWhatsNew({
                    currentVersion,
                    lastSeenVersion,
                    freshInstall: isFreshInstall(firstInstall, lastUpdate),
                    botActive: botActiveNow.current,
                    hasEntry: shown !== null,
                })
                if (decision === "wait") {
                    decided.current = false
                } else if (decision === "record" && currentVersion) {
                    return saveWhatsNewSeenVersion(currentVersion)
                } else if (decision === "show") {
                    setContent(shown)
                }
            })
            .catch((e) => {
                logWarningWithTimestamp(`[WhatsNew] Could not decide whether to show the dialog: ${e}`)
            })
    }, [botActive, sessionKnown])

    const dismiss = useCallback(() => {
        const version = content?.version
        setContent(null)
        if (!version) return
        saveWhatsNewSeenVersion(version).catch((e) => {
            logWarningWithTimestamp(`[WhatsNew] Could not remember that ${version} was seen: ${e}`)
        })
    }, [content])

    return { content, dismiss }
}
