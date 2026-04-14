import * as Application from "expo-application"
import MessageLog from "../../components/MessageLog"
import { useContext, useEffect, useRef, useState, useMemo } from "react"
import { BotStateContext } from "../../context/BotStateContext"
import { useSettings } from "../../context/SettingsContext"
import { logWithTimestamp, logErrorWithTimestamp } from "../../lib/logger"
import { Animated, DeviceEventEmitter, StyleSheet, TouchableOpacity, View, NativeModules } from "react-native"
import { Snackbar } from "react-native-paper"
import { MessageLogContext } from "../../context/MessageLogContext"
import { useTheme } from "../../context/ThemeContext"
import { Text } from "../../components/ui/text"
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "../../components/ui/alert-dialog"
import { Play, Square, AlertCircle, Info, CircleCheck, Repeat } from "lucide-react-native"
import type { LucideIcon } from "lucide-react-native"
import { Tooltip, TooltipContent, TooltipTrigger } from "../../components/ui/tooltip"
import PageHeader from "../../components/PageHeader"
import { usePerformanceLogging } from "../../hooks/usePerformanceLogging"
import SelectButton from "../../components/SelectButton"
import CustomSelect from "../../components/CustomSelect"
import { useNavigation } from "@react-navigation/native"

const styles = StyleSheet.create({
    root: {
        flex: 1,
        flexDirection: "column",
        alignItems: "center",
        paddingHorizontal: 10,
        paddingVertical: 10,
    },
    contentContainer: {
        flex: 1,
        width: "100%",
        flexDirection: "column",
    },
    button: {
        width: 100,
    },
})

/**
 * List of scenarios that are supported by the app.
 */
const scenarios = [
    {
        value: "URA Finale",
        label: "URA Finale",
        disabled: false,
    },
    {
        value: "Unity Cup",
        label: "Unity Cup",
        disabled: false,
    },
    {
        value: "Trackblazer",
        label: "Trackblazer",
        disabled: false,
    },
]

/**
 * The main Home page of the application.
 * Displays the Start/Stop button for the bot, a message log, and handles bot lifecycle events including settings persistence and readiness checks.
 */
const Home = () => {
    usePerformanceLogging("Home")
    const { StartModule } = NativeModules

    const { colors } = useTheme()
    const [isRunning, setIsRunning] = useState<boolean>(false)
    const [showNotReadyDialog, setShowNotReadyDialog] = useState<boolean>(false)
    const [snackbarOpen, setSnackbarOpen] = useState<boolean>(false)
    const [snackbarMessage, setSnackbarMessage] = useState<string>("")
    const [deviceMetrics, setDeviceMetrics] = useState<{ width: number; height: number; dpi: number } | null>(null)
    const [unsupportedReason, setUnsupportedReason] = useState<string | null>(null)
    const [showAccessibilityDialog, setShowAccessibilityDialog] = useState<boolean>(false)
    const [accessibilityRequirement, setAccessibilityRequirement] = useState<"enable" | "restart" | null>(null)
    const [queueProgress, setQueueProgress] = useState<{ currentRun: number; totalRuns: number; status: string; message?: string } | null>(null)
    const [interruptedQueue, setInterruptedQueue] = useState<{ currentRun: number; totalRuns: number; ageMinutes: number } | null>(null)

    const navigation = useNavigation()

    const bsc = useContext(BotStateContext)
    const mlc = useContext(MessageLogContext)
    const { saveSettings } = useSettings()

    const pulseAnim = useRef(new Animated.Value(1)).current

    useEffect(() => {
        let animation: Animated.CompositeAnimation | null = null

        if (unsupportedReason) {
            // Pulsate the icon to grab attention when there's an unsupported device.
            animation = Animated.loop(
                Animated.sequence([
                    Animated.timing(pulseAnim, {
                        toValue: 1.25,
                        duration: 700,
                        useNativeDriver: true,
                    }),
                    Animated.timing(pulseAnim, {
                        toValue: 1,
                        duration: 700,
                        useNativeDriver: true,
                    }),
                ])
            )
            animation.start()
        } else {
            pulseAnim.setValue(1)
        }

        return () => {
            animation?.stop()
        }
    }, [unsupportedReason])

    useEffect(() => {
        const mediaProjectionSubscription = DeviceEventEmitter.addListener("MediaProjectionService", (data) => {
            setIsRunning(data["message"] === "Running")
        })

        const botServiceSubscription = DeviceEventEmitter.addListener("BotService", (data) => {
            if (data["message"] === "Running") {
                mlc.setMessageLog([])
            }
        })

        const queueProgressSubscription = DeviceEventEmitter.addListener("RunQueueProgress", (data) => {
            try {
                const payload = JSON.parse(data["message"])
                setQueueProgress({
                    currentRun: payload.currentRun,
                    totalRuns: payload.totalRuns,
                    status: payload.status,
                    message: payload.message,
                })
                // Clear queue progress when queue is complete or failed.
                if (payload.status === "queueComplete" || payload.status === "queueFailed") {
                    setTimeout(() => setQueueProgress(null), 10000)
                }
            } catch (e) {
                // Ignore parse errors.
            }
        })

        getVersion()
        fetchDeviceMetrics()

        // Check for interrupted queue state from a previous crash.
        StartModule.getInterruptedQueueState()
            .then((state: any) => {
                if (state) {
                    setInterruptedQueue({
                        currentRun: state.currentRun,
                        totalRuns: state.totalRuns,
                        ageMinutes: state.ageMinutes,
                    })
                }
            })
            .catch(() => {})

        return () => {
            mediaProjectionSubscription.remove()
            botServiceSubscription.remove()
            queueProgressSubscription.remove()
        }
    }, [])

    /**
     * Checks if the currently selected scenario exists in the available scenarios data.
     */
    const isScenarioValid: boolean = useMemo(() => {
        return scenarios.some((it) => it.value === bsc.settings.general.scenario)
    }, [bsc.settings.general.scenario])

    /**
     * Fetch device metrics from NativeModule.
     */
    const fetchDeviceMetrics = async () => {
        try {
            const metrics = await StartModule.getDeviceDimensions()
            setDeviceMetrics(metrics)

            const { width, height, dpi } = metrics
            const isConfig1 = width === 1080 && height === 1920 && dpi === 240
            const isConfig2 = width === 1080 && height === 2340 && dpi === 450

            if (isConfig1 || isConfig2) {
                setUnsupportedReason(null)
            } else {
                setUnsupportedReason(`unsupported configuration: ${width}x${height} @ ${dpi} DPI`)
            }
        } catch (error) {
            logErrorWithTimestamp("[Home] Failed to fetch device dimensions:", error)
        }
    }

    /**
     * Grab the program name and version.
     */
    const getVersion = () => {
        const appName = Application.applicationName || "App"
        var version = Application.nativeApplicationVersion || "0.0.0"
        version += " (" + (Application.nativeBuildVersion || "0") + ")"
        logWithTimestamp(`Android app ${appName} version is ${version}`)
        bsc.setAppName(appName)
        bsc.setAppVersion(version)
    }

    /**
     * Handles the button press for starting or stopping the bot.
     */
    const handleButtonPress = async () => {
        if (isRunning) {
            StartModule.stop()
        } else if (bsc.readyStatus) {
            // Check accessibility status first.
            try {
                const status = await StartModule.getAccessibilityStatus()
                if (!status.enabled) {
                    setAccessibilityRequirement("enable")
                    setShowAccessibilityDialog(true)
                    return
                } else if (!status.active) {
                    setAccessibilityRequirement("restart")
                    setShowAccessibilityDialog(true)
                    return
                }
            } catch (error) {
                logErrorWithTimestamp("[Home] Failed to check accessibility status:", error)
            }

            // Save settings before starting the bot.
            // Also has the added benefit of only writing to the SQLite database when the bot is started instead of every time the settings are changed.
            logWithTimestamp("[Home] Saving settings before starting bot...")
            try {
                await saveSettings()
                logWithTimestamp("[Home] Settings saved successfully, starting bot...")
            } catch (error) {
                logErrorWithTimestamp("[Home] Failed to save settings:", error)
                setSnackbarMessage(`Failed to save settings before starting: ${error}`)
                setSnackbarOpen(true)
            }
            StartModule.start()
        } else {
            setShowNotReadyDialog(true)
        }
    }

    /** Gets the appropriate icon component for the SelectButton based on device state. */
    const getSelectButtonIconName = (): LucideIcon | undefined => {
        if (!isScenarioValid) {
            return undefined
        } else if (isRunning) {
            return Square
        } else {
            return Play
        }
    }

    /** Gets the SelectButton variant based on device state. */
    const getSelectButtonVariant = (): any => {
        if (isRunning) {
            // Not an error, but we want the button to be red to indicate that
            // pressing it will stop the service.
            // Must come first because we always want the button to be red
            // if the bot is running, regardless of the other conditions.
            return "error"
        } else if (unsupportedReason !== null) {
            return "warning"
        } else if (deviceMetrics === null) {
            return "warning"
        } else if (isScenarioValid) {
            return "success"
        } else {
            return "primary"
        }
    }

    /** Returns a status indicator based on the device state. */
    const renderStatus = (): React.ReactElement | null => {
        const warningText = `Current Display: ${deviceMetrics?.width}x${deviceMetrics?.height} (${deviceMetrics?.dpi} DPI).

Warning: Performance may be degraded due to ${unsupportedReason}.

Supported Configurations:
• 1080x1920 @ 240 DPI
• 1080x2340 @ 450 DPI

Note: Height is not as important to meet as the width. In addition, DPI is tied to the width and height together. How to calculate your specific DPI:

DPI = sqrt(width^2 + height^2) / diagonal

where width and height of the screen is in pixels, and diagonal is the diagonal size of the physical screen in inches.`

        if (unsupportedReason) {
            return (
                <Tooltip delayDuration={150}>
                    <TooltipTrigger>
                        <Animated.View style={{ transform: [{ scale: pulseAnim }] }}>
                            <AlertCircle size={24} color={colors.warning} />
                        </Animated.View>
                    </TooltipTrigger>
                    <TooltipContent sideOffset={12} side="bottom" style={{ maxWidth: 350, backgroundColor: colors.warningBg, borderColor: colors.warningBorder, borderWidth: 1 }}>
                        <Text style={{ color: colors.warningText }}>{warningText}</Text>
                    </TooltipContent>
                </Tooltip>
            )
        }

        if (!bsc.readyStatus && !isRunning) {
            return (
                <Tooltip delayDuration={150}>
                    <TooltipTrigger>
                        <Info size={24} color={colors.info} />
                    </TooltipTrigger>
                    <TooltipContent sideOffset={12} side="bottom" style={{ width: 200 }}>
                        <Text>Select a Scenario to start from the center button dropdown.</Text>
                    </TooltipContent>
                </Tooltip>
            )
        }

        if (deviceMetrics) {
            return (
                <Tooltip delayDuration={150}>
                    <TooltipTrigger>
                        <CircleCheck size={24} color={colors.success} />
                    </TooltipTrigger>
                    <TooltipContent sideOffset={12} side="bottom">
                        <Text>Everything looks good and ready to go!</Text>
                    </TooltipContent>
                </Tooltip>
            )
        }

        return null
    }

    return (
        <View style={styles.root}>
            <PageHeader
                title=""
                showHomeButton={false}
                style={{ width: "100%" }}
                leftComponent={
                    <SelectButton
                        variant={getSelectButtonVariant()}
                        iconName={getSelectButtonIconName()}
                        options={scenarios}
                        placeholder={deviceMetrics ? "Select a Scenario" : "Not Ready"}
                        value={bsc.settings.general.scenario}
                        onValueChange={(value) => {
                            const newScenario = value || ""
                            bsc.setSettings({ ...bsc.settings, general: { ...bsc.settings.general, scenario: newScenario } })
                            bsc.setReadyStatus(newScenario !== "")
                            setSelectedPreset(undefined)
                        }}
                        onPress={handleButtonPress}
                    />
                }
                rightComponent={renderStatus()}
            />

            {interruptedQueue && !isRunning && (
                <View
                    style={{
                        width: "100%",
                        paddingHorizontal: 12,
                        paddingVertical: 10,
                        marginBottom: 6,
                        backgroundColor: colors.warningBg || "#3d2e00",
                        borderRadius: 8,
                        borderWidth: 1,
                        borderColor: colors.warningBorder || "#665200",
                    }}
                >
                    <Text style={{ fontSize: 13, color: colors.warningText || "#ffd000", fontWeight: "600", marginBottom: 6 }}>
                        Queue interrupted at run {interruptedQueue.currentRun} of {interruptedQueue.totalRuns} ({Math.round(interruptedQueue.ageMinutes)} min ago)
                    </Text>
                    <Text style={{ fontSize: 12, color: colors.warningText || "#ffd000", marginBottom: 8 }}>
                        The app crashed during a queued session. Navigate to the training menu in-game and tap Start to resume.
                    </Text>
                    <View style={{ flexDirection: "row", gap: 8 }}>
                        <TouchableOpacity
                            onPress={() => {
                                // Update queue settings to resume from the interrupted run.
                                const remainingRuns = interruptedQueue.totalRuns - interruptedQueue.currentRun + 1
                                bsc.setSettings({
                                    ...bsc.settings,
                                    runQueue: {
                                        ...bsc.settings.runQueue,
                                        enableRunQueue: true,
                                        totalRuns: remainingRuns,
                                    },
                                })
                                StartModule.clearInterruptedQueueState()
                                setInterruptedQueue(null)
                                setSnackbarMessage(`Queue will resume: ${remainingRuns} runs remaining`)
                                setSnackbarOpen(true)
                            }}
                            style={{ paddingHorizontal: 14, paddingVertical: 6, backgroundColor: colors.primary, borderRadius: 6 }}
                        >
                            <Text style={{ fontSize: 12, color: colors.primaryForeground, fontWeight: "600" }}>Resume ({interruptedQueue.totalRuns - interruptedQueue.currentRun + 1} runs left)</Text>
                        </TouchableOpacity>
                        <TouchableOpacity
                            onPress={() => {
                                StartModule.clearInterruptedQueueState()
                                setInterruptedQueue(null)
                            }}
                            style={{ paddingHorizontal: 14, paddingVertical: 6, backgroundColor: colors.muted, borderRadius: 6 }}
                        >
                            <Text style={{ fontSize: 12, color: colors.foreground }}>Dismiss</Text>
                        </TouchableOpacity>
                    </View>
                </View>
            )}

            {queueProgress && (
                <View
                    style={{
                        flexDirection: "row",
                        alignItems: "center",
                        justifyContent: "space-between",
                        width: "100%",
                        paddingHorizontal: 8,
                        paddingVertical: 6,
                        marginBottom: 4,
                        backgroundColor: colors.muted,
                        borderRadius: 8,
                    }}
                >
                    <View style={{ flexDirection: "row", alignItems: "center", flex: 1 }}>
                        <Repeat size={16} color={colors.primary} style={{ marginRight: 6 }} />
                        <Text style={{ fontSize: 13, color: colors.foreground }}>
                            {queueProgress.status === "queueComplete"
                                ? `Queue complete: ${queueProgress.currentRun}/${queueProgress.totalRuns} runs`
                                : queueProgress.status === "queueFailed"
                                  ? `Queue failed at run ${queueProgress.currentRun}/${queueProgress.totalRuns}`
                                  : queueProgress.status === "waiting"
                                    ? `Run ${queueProgress.currentRun}/${queueProgress.totalRuns} - Waiting...`
                                    : queueProgress.status === "navigating"
                                      ? `Run ${queueProgress.currentRun}/${queueProgress.totalRuns} - Navigating...`
                                      : `Run ${queueProgress.currentRun}/${queueProgress.totalRuns} - ${queueProgress.status}`}
                        </Text>
                    </View>
                    {isRunning && queueProgress.status !== "queueComplete" && queueProgress.status !== "queueFailed" && (
                        <TouchableOpacity
                            onPress={() => StartModule.skipQueueRun()}
                            style={{
                                paddingHorizontal: 10,
                                paddingVertical: 4,
                                backgroundColor: colors.primary,
                                borderRadius: 6,
                                marginLeft: 8,
                            }}
                        >
                            <Text style={{ fontSize: 12, color: colors.primaryForeground, fontWeight: "600" }}>Skip Run</Text>
                        </TouchableOpacity>
                    )}
                </View>
            )}

            <View style={styles.contentContainer}>
                <MessageLog />
            </View>

            <AlertDialog open={showNotReadyDialog} onOpenChange={setShowNotReadyDialog}>
                <AlertDialogContent onDismiss={() => setShowNotReadyDialog(false)}>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Not Ready</AlertDialogTitle>
                        <AlertDialogDescription>A scenario must be selected before starting the bot. Please go to Settings to select a scenario.</AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogAction onPress={() => setShowNotReadyDialog(false)}>
                            <Text>OK</Text>
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            <AlertDialog open={showAccessibilityDialog} onOpenChange={setShowAccessibilityDialog}>
                <AlertDialogContent onDismiss={() => setShowAccessibilityDialog(false)}>
                    <AlertDialogHeader>
                        <AlertDialogTitle>{accessibilityRequirement === "enable" ? "Accessibility Service Disabled" : "Accessibility Service Error"}</AlertDialogTitle>
                        <AlertDialogDescription>
                            {accessibilityRequirement === "enable"
                                ? "The Accessibility Service must be enabled in system settings for the bot to perform clicks and gestures."
                                : "The Accessibility Service is enabled but seems to have been killed by Android in the background. It needs to be toggled off and back on to restart."}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel onPress={() => setShowAccessibilityDialog(false)}>
                            <Text>Cancel</Text>
                        </AlertDialogCancel>
                        <AlertDialogAction
                            onPress={() => {
                                setShowAccessibilityDialog(false)
                                ;(navigation.navigate as any)("Settings", {
                                    screen: "DebugSettings",
                                    params: { targetId: "debug-accessibility-service-check" },
                                })
                            }}
                        >
                            <Text>Go to Settings</Text>
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            <Snackbar
                visible={snackbarOpen}
                onDismiss={() => setSnackbarOpen(false)}
                action={{
                    label: "Close",
                    onPress: () => {
                        setSnackbarOpen(false)
                    },
                }}
                style={{ backgroundColor: snackbarMessage.startsWith("Preset") ? "green" : "red", borderRadius: 10 }}
            >
                {snackbarMessage}
            </Snackbar>
        </View>
    )
}

export default Home
