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
import { Play, Square, AlertCircle, Info, CircleCheck, Repeat, AlertTriangle, ThumbsUp, ChevronRight } from "lucide-react-native"
import type { LucideIcon } from "lucide-react-native"
import { Tooltip, TooltipContent, TooltipTrigger } from "../../components/ui/tooltip"
import PageHeader from "../../components/PageHeader"
import { usePerformanceLogging } from "../../hooks/usePerformanceLogging"
import SelectButton from "../../components/SelectButton"
import PresetPicker from "../../components/PresetPicker"
import { avoidAdvisoryFor, characterPresets, trainerAdvisories } from "../../data/characterPresets"
import { bumpSettingsRevision, createSingleFlight } from "../../lib/launchConfig"
import { presetCharacter, presetOutfit } from "../../data/presetMeta"
import { deriveInGameName, deriveExcludeOutfits } from "../../lib/rotationSnapshots"
import { presetObjectiveOf } from "../../lib/adaptiveSkillPolicy"
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
 * Supported scenarios. First three are Career scenarios (full debut-to-finale run); last two
 * are single-session "misc" modes: Daily Races spends the 3 daily tickets via Multi-Race,
 * Team Trials runs until RP is exhausted. Character presets apply only to career scenarios.
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
    {
        value: "Daily Races",
        label: "Daily Races (misc)",
        disabled: false,
    },
    {
        value: "Team Trials",
        label: "Team Trials (misc)",
        disabled: false,
    },
]

/** Returns true if the given scenario string is a non-career misc task. */
const isMiscScenario = (scenario: string): boolean => scenario === "Daily Races" || scenario === "Team Trials"

/**
 * Scenarios offered in the center-button dropdown. The misc modes are implemented and
 * functional (DailyRaceTask/TeamTrialsTask) but hidden from the picker for now — they are
 * single-session modes that don't fit the career-preset flow the Home screen is built around.
 * Remove the filter to surface them again.
 */
const dropdownScenarios = scenarios.filter((s) => !isMiscScenario(s.value))

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
    // Preset persistence state for the Home row + Start gate: a preset shows as launch-ready only
    // once its write is confirmed on disk, so Start can never launch a not-yet-saved selection.
    const [presetSaveState, setPresetSaveState] = useState<"idle" | "saving" | "saved" | "failed">("idle")
    const [deviceMetrics, setDeviceMetrics] = useState<{ width: number; height: number; dpi: number } | null>(null)
    const [unsupportedReason, setUnsupportedReason] = useState<string | null>(null)
    const [showAccessibilityDialog, setShowAccessibilityDialog] = useState<boolean>(false)
    const [accessibilityRequirement, setAccessibilityRequirement] = useState<"enable" | "restart" | null>(null)
    const [queueProgress, setQueueProgress] = useState<{ currentRun: number; totalRuns: number; status: string; message?: string } | null>(null)
    const [selectedPreset, setSelectedPreset] = useState<string | undefined>(undefined)
    const [pickerOpen, setPickerOpen] = useState<boolean>(false)
    // Pre-start mismatch gate: the avoid pairings found in the pending launch, and whether the
    // confirm dialog is shown. Empty list → start proceeds without a prompt.
    const [showAvoidDialog, setShowAvoidDialog] = useState<boolean>(false)
    const [avoidWarnings, setAvoidWarnings] = useState<{ label: string; reason: string }[]>([])
    const [interruptedQueue, setInterruptedQueue] = useState<{ currentRun: number; totalRuns: number; ageMinutes: number } | null>(null)

    const navigation = useNavigation()

    const bsc = useContext(BotStateContext)
    const mlc = useContext(MessageLogContext)
    const { flushAndVerifyLaunchConfig, prepareTraineeRotation } = useSettings()

    // Single-flight gate for Start: at most one barrier+launch sequence in flight; re-entrant
    // presses are ignored, and a cancel (Stop, preset change, unmount) refuses a launch even if
    // the in-flight barrier later verifies. Held in a ref so it survives re-renders.
    const startGate = useRef(createSingleFlight()).current

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

    // Refuse an in-flight Start launch if this screen unmounts mid-barrier, so a verified launch
    // cannot fire after the component is gone.
    useEffect(() => {
        return () => {
            startGate.cancel()
        }
    }, [startGate])

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
     * The preset currently applied to the settings, surviving app restarts: the session's
     * picker choice when there is one, otherwise the last applied preset recorded in the
     * racing snapshot (written on every apply).
     */
    const appliedPreset: { name: string; scenario: string } | null = useMemo(() => {
        if (selectedPreset) return { name: selectedPreset, scenario: bsc.settings.general.scenario }
        try {
            const snapshot = bsc.settings.racing.appliedRacingSnapshot ? JSON.parse(bsc.settings.racing.appliedRacingSnapshot) : null
            if (snapshot?.presetName) return { name: snapshot.presetName, scenario: snapshot.scenario ?? "" }
        } catch {
            // A malformed snapshot only loses the restored card label; applying a preset rewrites it.
        }
        return null
    }, [selectedPreset, bsc.settings.racing.appliedRacingSnapshot, bsc.settings.general.scenario])

    /**
     * Computes whether the current (preset, scenario) combination is a known mismatch
     * (avoid) or an above-average fit (recommended). Result drives the colored banner
     * shown directly below the preset selector. `null` means no advisory exists for
     * this trainee/scenario pair (most common case → no banner rendered).
     */
    const presetAdvisory: { kind: "avoid"; reason: string } | { kind: "recommend" } | null = useMemo(() => {
        if (!appliedPreset) return null
        const scenario = bsc.settings.general.scenario
        if (!scenario) return null
        const advisories = trainerAdvisories[appliedPreset.name]
        if (!advisories) return null
        const avoidEntry = advisories.avoid?.find((a) => a.scenario === scenario)
        if (avoidEntry) return { kind: "avoid", reason: avoidEntry.reason }
        if (advisories.recommended?.includes(scenario)) return { kind: "recommend" }
        return null
    }, [appliedPreset, bsc.settings.general.scenario])

    /**
     * Action label for the center button: what pressing it will do, not which scenario is
     * selected (the preset card below already shows that). Undefined falls back to the
     * "Select a Scenario" placeholder.
     */
    const startButtonLabel: string | undefined = useMemo(() => {
        if (isRunning) return "Stop"
        // While the selected preset is being persisted, the launch is gated (handleButtonPress
        // ignores the press) and the label says so, so a user cannot launch a not-yet-saved preset.
        if (presetSaveState === "saving") return "Saving preset..."
        const scenario = bsc.settings.general.scenario
        if (!scenario) return undefined
        if (bsc.settings.runQueue.enableRunQueue) return `Start Queue (${bsc.settings.runQueue.totalRuns} runs)`
        return `Start · ${scenario}`
    }, [isRunning, presetSaveState, bsc.settings.general.scenario, bsc.settings.runQueue.enableRunQueue, bsc.settings.runQueue.totalRuns])

    /**
     * Applies a character preset's settings to the current configuration.
     * @param presetName The preset to apply.
     * @param scenarioOverride Optional scenario chosen in the picker; sets general.scenario as
     * part of the same apply so a trainee-first pick lands atomically. Defaults to the current scenario.
     */
    const handlePresetChange = async (presetName: string | undefined, scenarioOverride?: string) => {
        setSelectedPreset(presetName)
        if (!presetName) return

        const scenario = scenarioOverride ?? bsc.settings.general.scenario
        const preset = characterPresets.find((p) => p.name === presetName && p.scenario === scenario)
        if (!preset) return

        // Capture the user's per-event override maps before the merge. Presets ship empty
        // `supportEventOverrides`/`scenarioEventOverrides` placeholders, so a shallow merge would wipe
        // the user's picks. These maps are deck- and scenario-specific, not character-specific, so they
        // must survive a preset switch.
        const preservedSupportOverrides = bsc.settings.trainingEvent?.supportEventOverrides || {}
        const preservedScenarioOverrides = bsc.settings.trainingEvent?.scenarioEventOverrides || {}

        // Capture the user's skill-spend threshold + on/off switch before the merge. Every preset ships
        // a uniform skillPointCheck (350); the shallow merge below would otherwise apply it over whatever
        // the user set in Skill Settings. The per-preset skill plan still applies — only the spend timing
        // is the user's global choice, so it must survive a preset switch (same rationale as the maps).
        const preservedSkillPointCheck = bsc.settings.skills.skillPointCheck
        const preservedEnableSkillPointCheck = bsc.settings.skills.enableSkillPointCheck

        // Deep merge preset settings with current settings
        let merged = { ...bsc.settings }
        for (const [category, values] of Object.entries(preset.settings)) {
            // Device/user-level categories are never per-trainee tuning; skip them so a preset
            // can't clobber the user's Debug Mode or Discord webhook (mirrors the rotation
            // snapshot denylist). Presets no longer ship these, but guard against regressions.
            if (category === "debug" || category === "discord") continue
            if (typeof values === "object" && values !== null && !Array.isArray(values)) {
                ;(merged as any)[category] = { ...(merged as any)[category], ...values }
            } else {
                ;(merged as any)[category] = values
            }
        }

        // Restore the preserved override maps. If a preset ever ships non-empty values for these keys
        // (currently none do), those preset values still take precedence via the spread.
        if (merged.trainingEvent) {
            const presetSupport = (preset.settings as any)?.trainingEvent?.supportEventOverrides || {}
            const presetScenario = (preset.settings as any)?.trainingEvent?.scenarioEventOverrides || {}
            merged.trainingEvent.supportEventOverrides = { ...preservedSupportOverrides, ...presetSupport }
            merged.trainingEvent.scenarioEventOverrides = { ...preservedScenarioOverrides, ...presetScenario }
        }

        // Restore the user's skill-spend threshold + on/off switch (captured above) so the preset's
        // uniform 350 never overrides the user's Skill Settings choice.
        if (merged.skills) {
            merged.skills.skillPointCheck = preservedSkillPointCheck
            merged.skills.enableSkillPointCheck = preservedEnableSkillPointCheck
            // Stamp the preset's objective on EVERY apply (absent -> "rank"): the spread above
            // merges over current settings, so without the stamp an objective-less preset would
            // silently inherit the previous preset's objective. Mode/tier stay user-global and
            // are never stamped - presets must not set them.
            merged.skills.skillSpendObjective = presetObjectiveOf(preset.settings)
        }

        // A trainee-first pick from the picker carries its scenario; land it in the same apply so
        // the preset and its scenario can never persist out of sync.
        if (scenario !== merged.general.scenario) {
            merged.general = { ...merged.general, scenario }
        }

        // Record what this preset intended for the racing plan so the bot can warn at career start
        // when the live settings have drifted (a silently-off mandatory flag once dropped a career's
        // planned races unnoticed).
        let plannedRaceCount = 0
        try {
            plannedRaceCount = merged.racing.racingPlan ? JSON.parse(merged.racing.racingPlan).length : 0
        } catch {
            plannedRaceCount = 0
        }
        merged.racing.appliedRacingSnapshot = JSON.stringify({
            presetName,
            scenario,
            enableRacingPlan: merged.racing.enableRacingPlan,
            enableMandatoryRacingPlan: merged.racing.enableMandatoryRacingPlan,
            plannedRaceCount,
        })

        // Record who this preset is for, in the roster-preview form, so a single (non-queue)
        // run can verify the Trainee Select screen instead of accepting the game's sticky
        // preselection (an interrupted queue once left El Condor preselected and a Rudolf
        // single run would have launched her under Rudolf's settings).
        merged.general.appliedPresetTrainee = deriveInGameName(presetName)
        merged.general.appliedPresetTraineeExcludes = deriveExcludeOutfits(presetName).join("\n")

        // A preset change invalidates any Start launch already in flight: it was verifying a
        // different configuration. Refuse that launch so it cannot start the just-replaced preset.
        startGate.cancel()

        // Bump the launch-config revision so the Start barrier can prove THIS apply reached disk
        // (a prior apply's stale revision would then fail the read-back and block launch).
        merged = bumpSettingsRevision(merged)

        bsc.setSettings(merged)
        // Persist and CONFIRM before calling the preset launch-ready: the row shows "Saving..."
        // until the write is verified on disk, so Start (gated on this) can never launch a
        // not-yet-saved selection. Pass merged explicitly -- settingsRef only syncs after the
        // state update re-renders, so a no-arg save here read the STALE pre-preset settings.
        setPresetSaveState("saving")
        logWithTimestamp(`[SETTINGS] preset_apply_requested preset="${presetName}" scenario="${scenario}" revision=${merged.general.settingsRevision}`)
        const result = await flushAndVerifyLaunchConfig(merged)
        if (result.ok) {
            setPresetSaveState("saved")
            logWithTimestamp(`[SETTINGS] readback_verified preset="${presetName}" trainee="${result.persisted?.trainee}" revision=${result.persisted?.revision} hash=${result.persisted?.hash}`)
            setSnackbarMessage(`Preset "${presetName}" applied`)
        } else {
            setPresetSaveState("failed")
            logErrorWithTimestamp(`[SETTINGS] persistence failed at ${result.stage}: ${result.reason}`)
            setSnackbarMessage(`Could not save preset "${presetName}": ${result.reason}. Tap the preset again to retry.`)
        }
        setSnackbarOpen(true)
    }

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
        let version = Application.nativeApplicationVersion || "0.0.0"
        version += " (" + (Application.nativeBuildVersion || "0") + ")"
        logWithTimestamp(`Android app ${appName} version is ${version}`)
        bsc.setAppName(appName)
        bsc.setAppVersion(version)
    }

    /**
     * Handles the button press for starting or stopping the bot.
     */
    /**
     * Every avoid pairing in the launch about to start: each rotation slot under its own scenario
     * when rotation is on, otherwise the single applied preset. Drives the pre-start confirm so a
     * known mismatch (e.g. a Turf=G trainee dropped into a turf scenario) can't launch silently.
     */
    const collectAvoidWarnings = (): { label: string; reason: string }[] => {
        if (bsc.settings.runQueue.enableTraineeRotation) {
            const out: { label: string; reason: string }[] = []
            bsc.settings.runQueue.traineeRotation.forEach((entry, i) => {
                if (!entry.presetKey) return
                const avoid = avoidAdvisoryFor(entry.presetKey, entry.scenario)
                if (avoid) out.push({ label: `#${i + 1} ${entry.presetKey} — ${entry.scenario}`, reason: avoid.reason })
            })
            return out
        }
        if (presetAdvisory?.kind === "avoid") {
            return [{ label: `${appliedPreset?.name ?? "This trainee"} — ${bsc.settings.general.scenario}`, reason: presetAdvisory.reason }]
        }
        return []
    }

    /** Runs the actual start sequence: accessibility gate → save settings → rotation snapshots → start.
     * Single-flight: every caller (button press and the avoid-dialog "Start anyway") enters the gate
     * here, so a re-entrant call while one is in flight is ignored and a cancel refuses the launch. */
    const proceedToStart = async () => {
        if (!startGate.begin()) {
            return
        }
        try {
            await runStartSequence()
        } finally {
            startGate.end()
        }
    }

    const runStartSequence = async () => {
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

        // Start persistence barrier: flush the pending settings write, read the launch-critical
        // rows back OUT of SQLite, and verify they match what the UI intends. Launch only on an
        // exact match. A plain awaited save is not enough -- the save path swallows errors and a
        // resolved promise never proved the intended values reached disk, so a stalled write once
        // let Start read stale rows and launch the wrong trainee. On any block, do NOT start:
        // surface a retryable error and leave the game untouched.
        logWithTimestamp("[START] launch_barrier_waiting")
        setPresetSaveState("saving")
        const barrier = await flushAndVerifyLaunchConfig()
        if (!barrier.ok) {
            setPresetSaveState("failed")
            logErrorWithTimestamp(`[START] launch_barrier_blocked stage=${barrier.stage} reason=${barrier.reason}`)
            setSnackbarMessage(`Could not start: ${barrier.reason}. Your preset is kept -- press Start to try again.`)
            setSnackbarOpen(true)
            return
        }
        // A cancel (Stop / preset change / unmount) during the barrier await refuses the launch,
        // even though verification passed -- the user is no longer asking for this run.
        if (!startGate.mayLaunch()) {
            setPresetSaveState("idle")
            logWithTimestamp("[START] launch cancelled before service start (Stop, preset change, or navigation).")
            return
        }
        setPresetSaveState("saved")
        logWithTimestamp(
            `[START] launch_barrier_passed trainee="${barrier.persisted?.trainee}" scenario="${barrier.persisted?.scenario}" objective="${barrier.persisted?.objective}" revision=${barrier.persisted?.revision} hash=${barrier.persisted?.hash}`
        )

        // Precompute the per-trainee rotation snapshots from the just-saved settings. Block start
        // on an unresolved preset or a persistence failure rather than let the queue hit a switch
        // boundary it can't satisfy — match-or-stop applies at config time, not just in-game.
        // These write only Kotlin-owned rot* rows, which are excluded from the verified identity,
        // so they never invalidate the revision the Kotlin gate re-checks.
        if (bsc.settings.runQueue.enableTraineeRotation) {
            // Mixed scenarios are supported: each snapshot carries its entry's scenario and the
            // navigator pages the Scenario Select carousel to it before confirming the launch.
            const missing = await prepareTraineeRotation()
            if (missing === null) {
                setSnackbarMessage("Failed to prepare trainee rotation snapshots. Not starting.")
                setSnackbarOpen(true)
                return
            }
            if (missing.length > 0) {
                const detail = missing.map((m) => `#${m.index + 1} ${m.presetKey} (${m.scenario})`).join(", ")
                setSnackbarMessage(`Rotation has unresolved presets: ${detail}. Fix the rotation list before starting.`)
                setSnackbarOpen(true)
                return
            }
        } else {
            // Rotation off: still clear any stale snapshots so a later enable starts clean.
            await prepareTraineeRotation()
        }

        // Re-check cancellation after the rotation writes (another await point).
        if (!startGate.mayLaunch()) {
            setPresetSaveState("idle")
            logWithTimestamp("[START] launch cancelled after rotation prep.")
            return
        }

        // Hand the verified identity (the revision + content hash React just confirmed on disk)
        // to Kotlin. The bot session re-reads the revision and aborts before any game interaction
        // if a write landed in the meantime -- closing the time-of-check to time-of-use window.
        StartModule.setVerifiedLaunchIdentity(barrier.persisted!.revision, barrier.persisted!.hash)
        StartModule.start()
    }

    const handleButtonPress = async () => {
        if (isRunning) {
            // Stopping cancels any in-flight Start barrier so a race between Stop and a
            // just-verifying launch cannot start the bot after the user asked it to stop.
            startGate.cancel()
            StartModule.stop()
            return
        }
        // Gate: while a preset apply is still persisting, do not launch -- the barrier would read
        // an incomplete config. Stopping (above) is always allowed. The label reads "Saving preset...".
        if (presetSaveState === "saving" || startGate.busy) {
            return
        }
        if (!bsc.readyStatus) {
            setShowNotReadyDialog(true)
            return
        }
        // Gate a known-mismatch launch behind an explicit confirm so it never starts silently.
        const avoids = collectAvoidWarnings()
        if (avoids.length > 0) {
            setAvoidWarnings(avoids)
            setShowAvoidDialog(true)
            return
        }
        await proceedToStart()
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
            // Red = "press to stop", not an actual error. Checked first so a running bot is always red
            // regardless of the other conditions.
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
                        <Text>Pick a trainee preset below, or a scenario from the center button dropdown, to get ready.</Text>
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
                        options={dropdownScenarios}
                        placeholder={deviceMetrics ? "Select a Scenario" : "Not Ready"}
                        value={bsc.settings.general.scenario}
                        displayLabel={startButtonLabel}
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

            {!isRunning && (
                <View style={{ width: "100%", paddingHorizontal: 4, marginBottom: 6 }}>
                    <TouchableOpacity
                        onPress={() => setPickerOpen(true)}
                        style={{
                            flexDirection: "row",
                            alignItems: "center",
                            paddingHorizontal: 12,
                            paddingVertical: 10,
                            borderRadius: 8,
                            borderWidth: 1,
                            borderColor: colors.border,
                            backgroundColor: colors.background,
                        }}
                    >
                        <View style={{ flex: 1 }}>
                            {appliedPreset ? (
                                <>
                                    <Text style={{ fontSize: 11, color: colors.foreground, opacity: 0.55 }}>Trainee Preset</Text>
                                    <Text style={{ fontSize: 15, fontWeight: "600", color: colors.foreground }}>{presetCharacter(appliedPreset.name)}</Text>
                                    <Text style={{ fontSize: 12, color: colors.foreground, opacity: 0.6 }}>
                                        {presetOutfit(appliedPreset.name)}
                                        {appliedPreset.scenario ? ` — ${appliedPreset.scenario}` : ""}
                                    </Text>
                                </>
                            ) : (
                                <Text style={{ fontSize: 14, color: colors.foreground, opacity: 0.7 }}>Select Trainee Preset...</Text>
                            )}
                        </View>
                        <ChevronRight size={18} color={colors.foreground} opacity={0.5} />
                    </TouchableOpacity>
                    {presetAdvisory?.kind === "avoid" && (
                        <View
                            style={{
                                flexDirection: "row",
                                alignItems: "flex-start",
                                marginTop: 6,
                                paddingHorizontal: 10,
                                paddingVertical: 8,
                                backgroundColor: "rgba(234, 179, 8, 0.15)",
                                borderLeftWidth: 3,
                                borderLeftColor: "#eab308",
                                borderRadius: 6,
                            }}
                        >
                            <AlertTriangle size={16} color="#eab308" style={{ marginRight: 6, marginTop: 2 }} />
                            <Text style={{ flex: 1, fontSize: 12, color: colors.foreground, lineHeight: 16 }}>
                                <Text style={{ fontWeight: "700", color: "#eab308" }}>Mismatch warning: </Text>
                                {presetAdvisory.reason}
                            </Text>
                        </View>
                    )}
                    {presetAdvisory?.kind === "recommend" && (
                        <View
                            style={{
                                flexDirection: "row",
                                alignItems: "center",
                                marginTop: 6,
                                paddingHorizontal: 10,
                                paddingVertical: 6,
                                backgroundColor: "rgba(34, 197, 94, 0.15)",
                                borderLeftWidth: 3,
                                borderLeftColor: "#22c55e",
                                borderRadius: 6,
                            }}
                        >
                            <ThumbsUp size={14} color="#22c55e" style={{ marginRight: 6 }} />
                            <Text style={{ fontSize: 12, color: colors.foreground }}>
                                <Text style={{ fontWeight: "700", color: "#22c55e" }}>Good pick </Text>— this trainee is a recommended fit for {bsc.settings.general.scenario}.
                            </Text>
                        </View>
                    )}
                </View>
            )}

            <PresetPicker
                visible={pickerOpen}
                onClose={() => setPickerOpen(false)}
                onApply={async (presetName, scenario) => {
                    setPickerOpen(false)
                    await handlePresetChange(presetName, scenario)
                }}
            />

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

            <AlertDialog open={showAvoidDialog} onOpenChange={setShowAvoidDialog}>
                <AlertDialogContent onDismiss={() => setShowAvoidDialog(false)}>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Poor trainee/scenario fit</AlertDialogTitle>
                        <AlertDialogDescription>
                            {avoidWarnings.length === 1
                                ? "This pairing is a known mismatch and will likely force-end the career early:"
                                : "These pairings are known mismatches and will likely force-end their careers early:"}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <View style={{ gap: 8 }}>
                        {avoidWarnings.map((w, i) => (
                            <View
                                key={i}
                                style={{
                                    paddingHorizontal: 10,
                                    paddingVertical: 8,
                                    backgroundColor: "rgba(234, 179, 8, 0.15)",
                                    borderLeftWidth: 3,
                                    borderLeftColor: "#eab308",
                                    borderRadius: 6,
                                }}
                            >
                                <Text style={{ fontSize: 13, fontWeight: "700", color: "#eab308", marginBottom: 2 }}>{w.label}</Text>
                                <Text style={{ fontSize: 12, color: colors.foreground, lineHeight: 16 }}>{w.reason}</Text>
                            </View>
                        ))}
                    </View>
                    <AlertDialogFooter>
                        <AlertDialogCancel onPress={() => setShowAvoidDialog(false)}>
                            <Text>Cancel</Text>
                        </AlertDialogCancel>
                        <AlertDialogAction
                            onPress={() => {
                                setShowAvoidDialog(false)
                                proceedToStart()
                            }}
                        >
                            <Text>Start anyway</Text>
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
