import { useCallback, useEffect, useRef, useState } from "react"
import { logWarningWithTimestamp } from "../lib/logger"
import { decideStarAsk, finishedCareersInReport, type WhatsNewOpenStatus } from "../lib/starAsk"
import { loadStarAsk, recordFinishedCareers, saveStarAskAnswered } from "../lib/uiPrefs"

/**
 * The one-time star ask after a few finished careers. Opens only once the native session state is
 * known, the bot is not active and the What's new dialog was not shown in this app open. Nothing is
 * stored until a button is pressed, so an app closed with the ask open shows it again next open.
 *
 * @param botActive The bot is armed or running.
 * @param sessionKnown The native session state has been read at least once.
 * @param whatsNew The What's new dialog's state in this app open.
 */
export function useStarAsk(botActive: boolean, sessionKnown: boolean, whatsNew: WhatsNewOpenStatus): { open: boolean; answer: () => void; noteReport: (payload: unknown) => void } {
    const [open, setOpen] = useState(false)
    const [counted, setCounted] = useState(0)
    const answered = useRef(false)

    useEffect(() => {
        if (!sessionKnown || open || answered.current) return
        let cancelled = false
        loadStarAsk()
            .then((stored) => {
                if (cancelled || answered.current) return
                if (decideStarAsk({ careers: stored.careers, answered: stored.answered, botActive, whatsNew }) === "show") setOpen(true)
            })
            .catch((e) => {
                logWarningWithTimestamp(`[StarAsk] Could not decide whether to show the ask: ${e}`)
            })
        return () => {
            cancelled = true
        }
    }, [botActive, sessionKnown, whatsNew, counted, open])

    /** Counts the finished careers in the last session's report (once per session) and decides again. */
    const noteReport = useCallback((payload: unknown) => {
        const report = finishedCareersInReport(payload)
        if (!report || report.finished === 0) return
        recordFinishedCareers(report.sessionId, report.finished)
            .then(() => setCounted((n) => n + 1))
            .catch((e) => {
                logWarningWithTimestamp(`[StarAsk] Could not count the finished careers: ${e}`)
            })
    }, [])

    /** Ends the ask for good, whichever button closed it. */
    const answer = useCallback(() => {
        answered.current = true
        setOpen(false)
        saveStarAskAnswered().catch((e) => {
            logWarningWithTimestamp(`[StarAsk] Could not remember the answer: ${e}`)
        })
    }, [])

    return { open, answer, noteReport }
}
