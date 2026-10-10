import { useContext, useRef, useState } from "react"
import { Alert, NativeModules } from "react-native"
import * as Sharing from "expo-sharing"
import { BotStateContext } from "../context/BotStateContext"
import { logErrorWithTimestamp } from "../lib/logger"
import { shareBugReport as runBugReportShare } from "../lib/bugReport"

/** Builds the bug report zip on the phone and opens the system share sheet. The app sends nothing itself. */
export const useBugReport = () => {
    const { StartModule } = NativeModules
    const bsc = useContext(BotStateContext)
    const [isPreparing, setIsPreparing] = useState(false)
    // State updates after the tap's render; the ref closes a second tap in the same frame.
    const busy = useRef(false)

    const shareBugReport = async () => {
        if (busy.current) return
        busy.current = true
        setIsPreparing(true)
        try {
            await runBugReportShare(bsc.settings, new Date(), {
                build: (settingsJson, contextLines, secrets) => StartModule.createBugReport(settingsJson, contextLines, secrets),
                canShare: () => Sharing.isAvailableAsync(),
                share: (fileUri) => Sharing.shareAsync(fileUri, { mimeType: "application/zip", dialogTitle: "Share bug report" }),
                alert: (title, message) => Alert.alert(title, message),
                log: (message, error) => logErrorWithTimestamp(message, error),
            })
        } finally {
            busy.current = false
            setIsPreparing(false)
        }
    }

    return { shareBugReport, isPreparing }
}
