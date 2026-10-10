import { useContext, useEffect, useState } from "react"
import { Alert, NativeModules } from "react-native"
import * as Sharing from "expo-sharing"
import { BotStateContext } from "../context/BotStateContext"
import { logErrorWithTimestamp } from "../lib/logger"
import { isBugReportBusy, onBugReportBusy, runOneBugReport, saveBugReport as runBugReportSave, shareBugReport as runBugReportShare } from "../lib/bugReport"

/** Builds the bug report zip on the phone and opens the system share sheet. The app sends nothing itself. */
export const useBugReport = () => {
    const { StartModule } = NativeModules
    const bsc = useContext(BotStateContext)
    const [isPreparing, setIsPreparing] = useState(isBugReportBusy())
    useEffect(() => {
        setIsPreparing(isBugReportBusy())
        return onBugReportBusy(setIsPreparing)
    }, [])

    const run = runOneBugReport

    const shareBugReport = () =>
        run(() =>
            runBugReportShare(bsc.settings, new Date(), {
                build: (settingsJson, contextLines, secrets) => StartModule.createBugReport(settingsJson, contextLines, secrets),
                canShare: () => Sharing.isAvailableAsync(),
                share: (fileUri) => Sharing.shareAsync(fileUri, { mimeType: "application/zip", dialogTitle: "Share bug report" }),
                alert: (title, message) => Alert.alert(title, message),
                log: (message, error) => logErrorWithTimestamp(message, error),
            })
        )

    const saveBugReportToPhone = () =>
        run(() =>
            runBugReportSave(bsc.settings, new Date(), {
                build: (settingsJson, contextLines, secrets) => StartModule.createBugReport(settingsJson, contextLines, secrets),
                save: (path, fileName, mimeType) => StartModule.saveFileToPhone(path, fileName, mimeType),
                alert: (title, message) => Alert.alert(title, message),
                log: (message, error) => logErrorWithTimestamp(message, error),
            })
        )

    return { shareBugReport, saveBugReportToPhone, isPreparing }
}
