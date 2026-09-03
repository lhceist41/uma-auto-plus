import { useMemo, useContext, useEffect, useState, useRef, useCallback } from "react"
import { SearchPageProvider } from "../../context/SearchPageContext"
import { BotStateContext } from "../../context/BotStateContext"
import { ScrollView, StyleSheet, Text, View } from "react-native"
import { Snackbar } from "react-native-paper"
import { useNavigation } from "@react-navigation/native"
import ThemeToggle from "../../components/ThemeToggle"
import { useTheme } from "../../context/ThemeContext"
import CustomSelect from "../../components/CustomSelect"
import NavigationLink from "../../components/NavigationLink"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomSlider from "../../components/CustomSlider"
import CustomTitle from "../../components/CustomTitle"
import CustomButton from "../../components/CustomButton"
import PageHeader from "../../components/PageHeader"
import { Separator } from "../../components/ui/separator"
import WarningContainer from "../../components/WarningContainer"
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "../../components/ui/alert-dialog"
import SearchableItem from "../../components/SearchableItem"
import { useSettings } from "../../context/SettingsContext"
import { useSettingsFileManager } from "../../hooks/useSettingsFileManager"
import { usePerformanceLogging } from "../../hooks/usePerformanceLogging"
import { DATING_SCHEDULE_PRESETS } from "../../lib/datingSchedule"

// The Daily Races and Team Trials tasks match on the persisted strings themselves, so these
// tables hold the exact values DailyRaceTask/TeamTrialsTask accept. Selecting through them keeps
// the controls from ever writing a value the Kotlin side would fall back on.
const DAILY_RACE_TARGETS = ["Moonlight Sho", "Jupiter Cup"] as const

const DAILY_RACE_DIFFICULTIES = [
    { value: "VERY_HARD", label: "Very Hard" },
    { value: "HARD", label: "Hard" },
    { value: "NORMAL", label: "Normal" },
    { value: "EASY", label: "Easy" },
] as const

const TEAM_TRIALS_OPPONENT_PICKS = [
    { value: "TOP", label: "Top" },
    { value: "MIDDLE", label: "Middle" },
    { value: "BOTTOM", label: "Bottom" },
] as const

/**
 * The main Settings page of the application.
 * Provides scenario selection, navigation links to sub-settings pages,
 * misc bot configuration options, and settings management (import/export/reset).
 */
const Settings = () => {
    usePerformanceLogging("Settings")
    const [snackbarOpen, setSnackbarOpen] = useState<boolean>(false)
    const scrollViewRef = useRef<ScrollView>(null)

    const bsc = useContext(BotStateContext)
    const { colors } = useTheme()
    const navigation = useNavigation()

    const { openDataDirectory, resetSettings } = useSettings()
    const { handleImportSettings, handleExportSettings, showImportDialog, setShowImportDialog, showResetDialog, setShowResetDialog } = useSettingsFileManager()

    const styles = useMemo(
        () =>
            StyleSheet.create({
                root: {
                    flex: 1,
                    flexDirection: "column",
                    justifyContent: "center",
                    margin: 10,
                    backgroundColor: colors.background,
                },
            }),
        [colors]
    )

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////
    // Callbacks

    useEffect(() => {
        // Manually set this flag to false as the snackbar autohiding does not set this to false automatically.
        setSnackbarOpen(true)
        setTimeout(() => setSnackbarOpen(false), 2500)
    }, [bsc.readyStatus])

    /**
     * Reset the settings to their default values.
     */
    const handleResetSettings = async () => {
        const success = await resetSettings()
        if (success) {
            setSnackbarOpen(true)
            setTimeout(() => setSnackbarOpen(false), 2500)
        }
    }

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////
    // Rendering

    const datingPresetOptions = Object.entries(DATING_SCHEDULE_PRESETS).map(([key, preset]) => ({ label: preset.label, value: key }))

    // Applying a preset writes the whole schedule (turns, Pure Passion timing, chain length) in one
    // update so the Kotlin side never sees a half-applied mix of two presets.
    const handleDatingPresetChange = (presetKey: string) => {
        const preset = DATING_SCHEDULE_PRESETS[presetKey]
        if (!preset) return
        bsc.setSettings({
            ...bsc.settings,
            general: {
                ...bsc.settings.general,
                datingSchedulePreset: presetKey,
                recreationTurns: [...preset.recreationTurns],
                purePassionTurn: preset.purePassionTurn,
                recreationTotalOutings: preset.totalOutings,
            },
        })
    }

    const years = [
        { label: "Junior", value: "Junior" },
        { label: "Classic", value: "Classic" },
        { label: "Senior", value: "Senior" },
    ]

    const months = [
        { label: "January", value: "January" },
        { label: "February", value: "February" },
        { label: "March", value: "March" },
        { label: "April", value: "April" },
        { label: "May", value: "May" },
        { label: "June", value: "June" },
        { label: "July", value: "July" },
        { label: "August", value: "August" },
        { label: "September", value: "September" },
        { label: "October", value: "October" },
        { label: "November", value: "November" },
        { label: "December", value: "December" },
    ]

    const phases = [
        { label: "Early", value: "Early" },
        { label: "Late", value: "Late" },
    ]

    const handleStopAtDateChange = useCallback(
        (index: number, part: "year" | "month" | "phase", value: string) => {
            const dates = [...bsc.settings.general.stopAtDates]
            const currentParts = dates[index].split(" ")
            let newYear = currentParts[0] || "Senior"
            let newMonth = currentParts[1] || "January"
            let newPhase = currentParts[2] || "Early"

            if (part === "year") newYear = value
            if (part === "month") newMonth = value
            if (part === "phase") newPhase = value

            dates[index] = `${newYear} ${newMonth} ${newPhase}`
            bsc.setSettings({
                ...bsc.settings,
                general: { ...bsc.settings.general, stopAtDates: dates },
            })
        },
        [bsc]
    )

    const handleAddStopAtDate = useCallback(() => {
        bsc.setSettings({
            ...bsc.settings,
            general: { ...bsc.settings.general, stopAtDates: [...bsc.settings.general.stopAtDates, "Senior January Early"] },
        })
    }, [bsc])

    const handleRemoveStopAtDate = useCallback(
        (index: number) => {
            const dates = bsc.settings.general.stopAtDates.filter((_, i) => i !== index)
            bsc.setSettings({
                ...bsc.settings,
                general: { ...bsc.settings.general, stopAtDates: dates.length > 0 ? dates : ["Senior January Early"] },
            })
        },
        [bsc]
    )

    const renderTrainingLink = () => {
        return (
            <NavigationLink
                title="Go to Training Settings"
                description="Configure which stats to train, set priorities, and customize training behavior."
                onPress={() => navigation.navigate("TrainingSettings" as never)}
            />
        )
    }

    const renderTrainingEventLink = () => {
        return (
            <NavigationLink
                title="Go to Training Event Settings"
                description="Configure training event preferences and event selection behavior."
                onPress={() => navigation.navigate("TrainingEventSettings" as never)}
            />
        )
    }

    const renderRacingLink = () => {
        return (
            <NavigationLink
                title="Go to Racing Settings"
                description="Configure racing behavior, retry settings, mandatory race handling, and more."
                onPress={() => navigation.navigate("RacingSettings" as never)}
            />
        )
    }

    const renderSkillsLink = () => {
        return <NavigationLink title="Go to Skills Settings" description="Configure skill purchasing behavior." onPress={() => navigation.navigate("SkillSettings" as never)} />
    }

    const renderEventLogVisualizerLink = () => {
        return (
            <NavigationLink
                title="Go to Event Log Visualizer (Beta)"
                description="Import logs and view a day-by-day timeline of actions."
                onPress={() => navigation.navigate("EventLogVisualizer" as never)}
            />
        )
    }

    const renderScenarioOverridesLink = () => {
        return (
            <NavigationLink
                title="Go to Scenario Overrides Settings"
                description="Configure behavior overrides specific to each scenario."
                onPress={() => navigation.navigate("ScenarioOverridesSettings" as never)}
            />
        )
    }

    const renderRunQueueLink = () => {
        return (
            <NavigationLink
                title="Go to Run Queue Settings"
                description="Queue multiple consecutive runs of the same scenario to let the bot run unattended."
                onPress={() => navigation.navigate("RunQueueSettings" as never)}
            />
        )
    }

    const renderDebugLink = () => {
        return (
            <NavigationLink
                title="Go to Debug Settings"
                description="Configure debug mode, template matching settings, and diagnostic tests for bot troubleshooting."
                onPress={() => navigation.navigate("DebugSettings" as never)}
            />
        )
    }

    const renderDiscordLink = () => {
        return (
            <NavigationLink
                title="Go to Discord Settings"
                description="Configure Discord bot notifications to receive DM updates when the bot stops."
                onPress={() => navigation.navigate("DiscordSettings" as never)}
            />
        )
    }

    const renderMiscTaskSettings = () => {
        return (
            <View style={{ marginTop: 16 }}>
                <Separator style={{ marginVertical: 16 }} />

                <CustomTitle title="Daily Races" description="Options for the Daily Races mode, which spends the day's race tickets in one multi-race sequence." />

                <CustomCheckbox
                    searchId="settings-daily-race-multi-race"
                    checked={bsc.settings.miscDailyRace.ensureMultiRaceOn}
                    onCheckedChange={(checked) => {
                        bsc.setSettings({
                            ...bsc.settings,
                            miscDailyRace: { ...bsc.settings.miscDailyRace, ensureMultiRaceOn: checked },
                        })
                    }}
                    label="Ensure Multi-Race Is On"
                    description="Keeps multi-race enabled when running Daily Races, so every remaining ticket runs in one go. Turn this off to run the races one at a time."
                    className="mt-4"
                />

                <CustomSelect
                    searchId="settings-daily-race-target-race"
                    value={bsc.settings.miscDailyRace.targetRace}
                    options={DAILY_RACE_TARGETS.map((race) => ({ label: race, value: race }))}
                    onValueChange={(value) => {
                        const race = DAILY_RACE_TARGETS.find((option) => option === value)
                        if (race) {
                            bsc.setSettings({ ...bsc.settings, miscDailyRace: { ...bsc.settings.miscDailyRace, targetRace: race } })
                        }
                    }}
                    placeholder="Select race"
                    label="Target Race"
                    description="Which daily race to enter. If the chosen race is not in the current rotation, the bot stops cleanly instead of picking the other one."
                    style={{ marginTop: 16 }}
                />

                <CustomSelect
                    searchId="settings-daily-race-difficulty"
                    value={bsc.settings.miscDailyRace.targetDifficulty}
                    options={DAILY_RACE_DIFFICULTIES.map((tier) => ({ label: tier.label, value: tier.value }))}
                    onValueChange={(value) => {
                        const tier = DAILY_RACE_DIFFICULTIES.find((option) => option.value === value)
                        if (tier) {
                            bsc.setSettings({ ...bsc.settings, miscDailyRace: { ...bsc.settings.miscDailyRace, targetDifficulty: tier.value } })
                        }
                    }}
                    placeholder="Select difficulty"
                    label="Target Difficulty"
                    description="Which difficulty tier to race. Very Hard pays the most and costs the same ticket."
                    style={{ marginTop: 16 }}
                />

                <Separator style={{ marginVertical: 16 }} />

                <CustomTitle title="Team Trials" description="Options for the Team Trials mode, which runs matches until Race Points run out." />

                <CustomSelect
                    searchId="settings-team-trials-opponent-pick"
                    value={bsc.settings.miscTeamTrials.opponentPick}
                    options={TEAM_TRIALS_OPPONENT_PICKS.map((pick) => ({ label: pick.label, value: pick.value }))}
                    onValueChange={(value) => {
                        const pick = TEAM_TRIALS_OPPONENT_PICKS.find((option) => option.value === value)
                        if (pick) {
                            bsc.setSettings({ ...bsc.settings, miscTeamTrials: { ...bsc.settings.miscTeamTrials, opponentPick: pick.value } })
                        }
                    }}
                    placeholder="Select opponent"
                    label="Opponent To Pick"
                    description="Which of the three listed opponents to fight. They are listed strongest first, so Bottom is the safest and Top pays the most points."
                    style={{ marginTop: 16 }}
                />

                <CustomSlider
                    searchId="settings-team-trials-max-matches"
                    value={bsc.settings.miscTeamTrials.maxMatchesPerSession}
                    placeholder={bsc.defaultSettings.miscTeamTrials.maxMatchesPerSession}
                    onValueChange={(value) => {
                        bsc.setSettings({ ...bsc.settings, miscTeamTrials: { ...bsc.settings.miscTeamTrials, maxMatchesPerSession: value } })
                    }}
                    onSlidingComplete={(value) => {
                        bsc.setSettings({ ...bsc.settings, miscTeamTrials: { ...bsc.settings.miscTeamTrials, maxMatchesPerSession: value } })
                    }}
                    min={1}
                    max={5}
                    step={1}
                    label="Max Matches Per Session"
                    showValue={true}
                    showLabels={true}
                    description="Stops after this many matches even if Race Points remain. A full Race Point pool is 5 matches, so 5 lets the run finish on the game's own limit."
                />
            </View>
        )
    }

    const renderMiscSettings = () => {
        return (
            <View style={{ marginTop: 16 }}>
                <Separator style={{ marginVertical: 16 }} />

                <CustomTitle title="Misc Settings" description="General settings for the bot that don't fit into the other categories." />

                <CustomCheckbox
                    searchId="settings-popup-check"
                    checked={bsc.settings.general.enablePopupCheck}
                    onCheckedChange={(checked) => {
                        bsc.setSettings({
                            ...bsc.settings,
                            general: { ...bsc.settings.general, enablePopupCheck: checked },
                        })
                    }}
                    label="Stop on Unexpected Popups"
                    description="Stops the bot when an unexpected popup with a Cancel button is detected (e.g. lack of fans or trophies). You will need to dismiss the popup and restart the bot manually."
                    className="mt-4"
                />

                <CustomCheckbox
                    searchId="settings-stop-before-finals"
                    checked={bsc.settings.general.enableStopBeforeFinals}
                    onCheckedChange={(checked) => {
                        bsc.setSettings({
                            ...bsc.settings,
                            general: { ...bsc.settings.general, enableStopBeforeFinals: checked },
                        })
                    }}
                    label="Stop before Finals"
                    description="Stops the bot on turn 72 so you can purchase skills before the final races."
                    className="mt-4"
                />

                <CustomCheckbox
                    searchId="settings-stop-at-date"
                    checked={bsc.settings.general.enableStopAtDate}
                    onCheckedChange={(checked) => {
                        bsc.setSettings({
                            ...bsc.settings,
                            general: { ...bsc.settings.general, enableStopAtDate: checked },
                        })
                    }}
                    label="Stop at Date"
                    description="Stops the bot on one or more specified dates. The bot will stop at the earliest matching date it reaches."
                    className="mt-4"
                />

                {bsc.settings.general.enableStopAtDate && (
                    <SearchableItem id="settings-stop-at-date" title="Target Dates" description="Stops the bot on the specified dates." style={{ marginLeft: 16, marginTop: 8 }}>
                        {bsc.settings.general.stopAtDates.map((dateStr, index) => {
                            const parts = dateStr.split(" ")
                            return (
                                <View key={index} style={{ marginBottom: index < bsc.settings.general.stopAtDates.length - 1 ? 12 : 0 }}>
                                    <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 4 }}>
                                        <Text style={{ fontSize: 14, fontWeight: "600", color: colors.foreground }}>Date {index + 1}</Text>
                                        {bsc.settings.general.stopAtDates.length > 1 && (
                                            <CustomButton onPress={() => handleRemoveStopAtDate(index)} variant="destructive" size="sm" fontSize={12}>
                                                Remove
                                            </CustomButton>
                                        )}
                                    </View>
                                    <View style={{ flexDirection: "row", gap: 8, justifyContent: "space-between" }}>
                                        <View style={{ flex: 1 }}>
                                            <CustomSelect
                                                placeholder="Year"
                                                width="100%"
                                                options={years}
                                                value={parts[0]}
                                                onValueChange={(value) => handleStopAtDateChange(index, "year", value || "Senior")}
                                            />
                                        </View>
                                        <View style={{ flex: 1 }}>
                                            <CustomSelect
                                                placeholder="Month"
                                                width="100%"
                                                options={months}
                                                value={parts[1]}
                                                onValueChange={(value) => handleStopAtDateChange(index, "month", value || "January")}
                                            />
                                        </View>
                                        <View style={{ flex: 1 }}>
                                            <CustomSelect
                                                placeholder="Phase"
                                                width="100%"
                                                options={phases}
                                                value={parts[2]}
                                                onValueChange={(value) => handleStopAtDateChange(index, "phase", value || "Early")}
                                            />
                                        </View>
                                    </View>
                                </View>
                            )
                        })}
                        <CustomButton onPress={handleAddStopAtDate} variant="default" fontSize={14} style={{ marginTop: 12, alignSelf: "flex-start" }}>
                            + Add Date
                        </CustomButton>
                    </SearchableItem>
                )}

                <CustomCheckbox
                    searchId="settings-crane-game-attempt"
                    checked={bsc.settings.general.enableCraneGameAttempt}
                    onCheckedChange={(checked) => {
                        bsc.setSettings({
                            ...bsc.settings,
                            general: { ...bsc.settings.general, enableCraneGameAttempt: checked },
                        })
                    }}
                    label="Enable Crane Game Attempt"
                    description="When enabled, the bot will attempt to complete the crane game. By default, the bot will stop when it is detected."
                    className="mt-4"
                />

                <CustomCheckbox
                    searchId="settings-dating-schedule"
                    checked={bsc.settings.general.enableDatingSchedule}
                    onCheckedChange={(checked) => {
                        bsc.setSettings({
                            ...bsc.settings,
                            general: { ...bsc.settings.general, enableDatingSchedule: checked },
                        })
                    }}
                    label="Enable Support Card Dating Schedule"
                    description="Performs a recreation outing on the selected preset's pinned career turns to advance a Group support card's outing chain, holding the final outing for the Pure Passion turn where the preset times it. Mandatory races always outrank a pinned outing; scheduled agenda races are overridden."
                    className="mt-4"
                />

                {bsc.settings.general.enableDatingSchedule && (
                    <SearchableItem
                        id="settings-dating-schedule-preset"
                        title="Dating Schedule Preset"
                        description="Pinned recreation turns for the equipped Group support card."
                        style={{ marginLeft: 16, marginTop: 8 }}
                    >
                        <CustomSelect
                            placeholder="Preset"
                            width="100%"
                            options={datingPresetOptions}
                            value={bsc.settings.general.datingSchedulePreset}
                            onValueChange={(value) => handleDatingPresetChange(value || "siriusSenior")}
                        />
                        <Text style={{ fontSize: 12, color: colors.foreground, opacity: 0.7, marginTop: 8 }}>
                            {`Pinned turns: ${bsc.settings.general.recreationTurns.join(", ")}${
                                bsc.settings.general.purePassionTurn > 0 ? ` - Pure Passion final on turn ${bsc.settings.general.purePassionTurn}` : " - Pure Passion untimed"
                            }`}
                        </Text>
                        <CustomCheckbox
                            searchId="settings-dating-catch-up"
                            checked={bsc.settings.general.enableRecreationCatchUp}
                            onCheckedChange={(checked) => {
                                bsc.setSettings({
                                    ...bsc.settings,
                                    general: { ...bsc.settings.general, enableRecreationCatchUp: checked },
                                })
                            }}
                            label="Catch Up Missed Outings"
                            description="If a pinned turn gets pre-empted (e.g. by a race), makes up the outing on the next available turn."
                            className="mt-4"
                        />
                    </SearchableItem>
                )}

                <CustomCheckbox
                    searchId="settings-enable-settings-display"
                    checked={bsc.settings.misc.enableSettingsDisplay}
                    onCheckedChange={(checked) => {
                        bsc.setSettings({
                            ...bsc.settings,
                            misc: { ...bsc.settings.misc, enableSettingsDisplay: checked },
                        })
                    }}
                    label="Enable Settings Display in Message Log"
                    description="Shows current bot configuration settings at the top of the message log."
                    className="mt-4"
                />

                <CustomCheckbox
                    searchId="settings-enable-message-id-display"
                    checked={bsc.settings.misc.enableMessageIdDisplay}
                    onCheckedChange={(checked) => {
                        bsc.setSettings({
                            ...bsc.settings,
                            misc: { ...bsc.settings.misc, enableMessageIdDisplay: checked },
                        })
                    }}
                    label="Enable Message ID Display"
                    description="Shows message IDs in the message log to help with debugging."
                    className="mt-4"
                />

                <CustomCheckbox
                    searchId="settings-record-decision-data"
                    checked={bsc.settings.misc.recordDecisionData}
                    onCheckedChange={(checked) => {
                        bsc.setSettings({
                            ...bsc.settings,
                            misc: { ...bsc.settings.misc, recordDecisionData: checked },
                        })
                    }}
                    label="Record Decision Data"
                    description="Records lightweight decision data each turn, stored only on this device, so a career can be reviewed and improved later. Nothing is uploaded. Leave this on unless you want to minimize storage use."
                    className="mt-4"
                />

                <CustomSlider
                    searchId="settings-wait-delay"
                    value={bsc.settings.general.waitDelay}
                    placeholder={bsc.defaultSettings.general.waitDelay}
                    onValueChange={(value) => {
                        bsc.setSettings({ ...bsc.settings, general: { ...bsc.settings.general, waitDelay: value } })
                    }}
                    onSlidingComplete={(value) => {
                        bsc.setSettings({ ...bsc.settings, general: { ...bsc.settings.general, waitDelay: value } })
                    }}
                    min={0.0}
                    max={1.0}
                    step={0.1}
                    label="Wait Delay"
                    labelUnit="s"
                    showValue={true}
                    showLabels={true}
                    description="Sets the delay between actions and imaging operations. Lowering this will make the bot run much faster at the risk of the bot losing track of its location after loading/connecting screens."
                />

                <CustomSlider
                    searchId="settings-dialog-wait-delay"
                    value={bsc.settings.general.dialogWaitDelay}
                    placeholder={bsc.defaultSettings.general.dialogWaitDelay}
                    onValueChange={(value) => {
                        bsc.setSettings({ ...bsc.settings, general: { ...bsc.settings.general, dialogWaitDelay: value } })
                    }}
                    onSlidingComplete={(value) => {
                        bsc.setSettings({ ...bsc.settings, general: { ...bsc.settings.general, dialogWaitDelay: value } })
                    }}
                    min={0.0}
                    max={1.0}
                    step={0.1}
                    label="Dialog Wait Delay"
                    labelUnit="s"
                    showValue={true}
                    showLabels={true}
                    description="Sets the delay between clicking a button that opens dialog and actually handling the dialog. Lowering this will make the bot run faster at an increased risk of the bot incorrectly handling dialogs that pop up."
                />

                <CustomSlider
                    searchId="settings-overlay-button-size"
                    value={bsc.settings.misc.overlayButtonSizeDP}
                    placeholder={bsc.defaultSettings.misc.overlayButtonSizeDP}
                    onValueChange={(value) => {
                        bsc.setSettings({ ...bsc.settings, misc: { ...bsc.settings.misc, overlayButtonSizeDP: value } })
                    }}
                    onSlidingComplete={(value) => {
                        bsc.setSettings({ ...bsc.settings, misc: { ...bsc.settings.misc, overlayButtonSizeDP: value } })
                    }}
                    min={30}
                    max={60}
                    step={5}
                    label="Overlay Button Size"
                    labelUnit=" dp"
                    showValue={true}
                    showLabels={true}
                    description="Sets the size of the floating overlay button in density-independent pixels (dp). Higher values make the button easier to tap."
                />

                <Separator style={{ marginVertical: 16 }} />

                <CustomTitle searchId="settings-management-title" title="Settings Management" description="Import and export settings from JSON file or access the app's data directory." />

                <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
                    <CustomButton onPress={handleImportSettings} variant="default" style={{ width: 150 }}>
                        📥 Import Settings
                    </CustomButton>

                    <CustomButton onPress={handleExportSettings} variant="default" style={{ width: 150 }}>
                        📤 Export Settings
                    </CustomButton>
                </View>

                <View style={{ flexDirection: "row", marginTop: 16, justifyContent: "space-between" }}>
                    <CustomButton onPress={openDataDirectory} variant="default" style={{ width: 150 }} fontSize={12}>
                        📁 Open Data Directory
                    </CustomButton>

                    <CustomButton onPress={() => setShowResetDialog(true)} variant="destructive" style={{ width: 150 }}>
                        🔄 Reset Settings
                    </CustomButton>
                </View>

                <WarningContainer style={{ marginBottom: 12 }}>
                    <View style={{ flexDirection: "row", flexWrap: "wrap" }}>
                        <Text style={{ fontWeight: "bold", color: colors.warningText }}>⚠️ File Explorer Note:</Text>
                        <Text style={{ fontSize: 14, color: colors.warningText, lineHeight: 20 }}>
                            To manually access files, you need a file explorer app that can access the /Android/data folder (like CX File Explorer). Standard file managers will not work.
                        </Text>
                    </View>
                </WarningContainer>
            </View>
        )
    }

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////

    return (
        <View style={styles.root}>
            <PageHeader title="Settings" rightComponent={<ThemeToggle />} />

            <SearchPageProvider page="SettingsMain" scrollViewRef={scrollViewRef}>
                <ScrollView ref={scrollViewRef} nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                    <View className="m-1">
                        {renderTrainingLink()}
                        {renderTrainingEventLink()}
                        {renderRacingLink()}
                        {renderSkillsLink()}
                        {renderEventLogVisualizerLink()}
                        {renderDiscordLink()}
                        {renderRunQueueLink()}
                        {renderScenarioOverridesLink()}
                        {renderDebugLink()}
                        {renderMiscTaskSettings()}
                        {renderMiscSettings()}
                    </View>
                </ScrollView>
            </SearchPageProvider>

            <Snackbar
                visible={snackbarOpen}
                onDismiss={() => setSnackbarOpen(false)}
                action={{
                    label: "Close",
                    onPress: () => {
                        setSnackbarOpen(false)
                    },
                }}
                style={{ backgroundColor: bsc.readyStatus ? "green" : "red", borderRadius: 10 }}
            >
                {bsc.readyStatus ? "Bot is ready!" : "Bot is not ready!"}
            </Snackbar>

            {/* Restart Dialog */}
            <AlertDialog open={showImportDialog} onOpenChange={setShowImportDialog}>
                <AlertDialogContent style={{ backgroundColor: "black" }}>
                    <AlertDialogHeader>
                        <AlertDialogTitle>
                            <Text style={{ color: "white" }}>Settings Imported</Text>
                        </AlertDialogTitle>
                        <AlertDialogDescription>
                            <Text style={{ color: "white" }}>Settings have been imported successfully.</Text>
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogAction style={{ backgroundColor: "white" }}>
                            <Text style={{ color: "black" }}>OK</Text>
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            {/* Reset Settings Dialog */}
            <AlertDialog open={showResetDialog} onOpenChange={setShowResetDialog}>
                <AlertDialogContent style={{ backgroundColor: "black" }}>
                    <AlertDialogHeader>
                        <AlertDialogTitle>
                            <Text style={{ color: "white" }}>Reset Settings to Default</Text>
                        </AlertDialogTitle>
                        <AlertDialogDescription style={{ height: 50 }}>
                            <Text style={{ color: "white" }}>
                                Are you sure you want to reset all settings to their default values? This action cannot be undone and will overwrite your current configuration.
                            </Text>
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel onPress={() => setShowResetDialog(false)} style={{ backgroundColor: "black" }}>
                            <Text style={{ color: "white" }}>Cancel</Text>
                        </AlertDialogCancel>
                        <AlertDialogAction onPress={handleResetSettings} style={{ backgroundColor: "white" }}>
                            <Text style={{ color: "black" }}>Reset</Text>
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </View>
    )
}

export default Settings
