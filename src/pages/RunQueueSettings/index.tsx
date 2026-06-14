import { useMemo, useContext, useRef } from "react"
import { View, ScrollView, StyleSheet, TextInput, Text } from "react-native"
import { useTheme } from "../../context/ThemeContext"
import { BotStateContext, defaultSettings } from "../../context/BotStateContext"
import CustomSlider from "../../components/CustomSlider"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomSelect from "../../components/CustomSelect"
import CustomButton from "../../components/CustomButton"
import CustomTitle from "../../components/CustomTitle"
import PageHeader from "../../components/PageHeader"
import WarningContainer from "../../components/WarningContainer"
import { characterPresets } from "../../data/characterPresets"
import { SearchPageProvider } from "../../context/SearchPageContext"
import { usePerformanceLogging } from "../../hooks/usePerformanceLogging"

/**
 * The Run Queue Settings page.
 * Provides controls for queueing multiple consecutive training runs
 * so the bot can run unattended for extended periods.
 */
const RunQueueSettings = () => {
    usePerformanceLogging("RunQueueSettings")
    const { colors, isDark } = useTheme()
    const bsc = useContext(BotStateContext)
    const scrollViewRef = useRef<ScrollView>(null)

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
                rotationCard: {
                    borderWidth: 1,
                    borderColor: isDark ? "#444" : "#ccc",
                    borderRadius: 8,
                    padding: 12,
                    marginTop: 10,
                    backgroundColor: isDark ? "#161616" : "#fafafa",
                },
                rotationHeader: {
                    fontSize: 14,
                    fontWeight: "700",
                    color: colors.foreground,
                    marginBottom: 8,
                },
                inputLabel: {
                    fontSize: 13,
                    fontWeight: "600",
                    color: colors.foreground,
                    marginTop: 10,
                    marginBottom: 4,
                },
                textInput: {
                    borderWidth: 1,
                    borderColor: isDark ? "#444" : "#ccc",
                    borderRadius: 8,
                    padding: 10,
                    fontSize: 14,
                    color: colors.foreground,
                    backgroundColor: isDark ? "#1a1a1a" : "#f9f9f9",
                },
            }),
        [colors, isDark]
    )

    const runQueueSettings = { ...defaultSettings.runQueue, ...bsc.settings.runQueue }

    const updateSetting = (key: keyof typeof bsc.settings.runQueue, value: any) => {
        bsc.setSettings({
            ...bsc.settings,
            runQueue: { ...bsc.settings.runQueue, [key]: value },
        })
    }

    // Preset options for the rotation rows. Each option encodes (name, scenario) so picking one
    // sets both presetKey and scenario for the entry. The "@@" delimiter never occurs in a name.
    const presetOptions = useMemo(() => characterPresets.map((p) => ({ value: `${p.name}@@${p.scenario}`, label: `${p.name} — ${p.scenario}` })).sort((a, b) => a.label.localeCompare(b.label)), [])

    const rotation = runQueueSettings.traineeRotation
    const updateRotationEntry = (index: number, patch: Partial<(typeof rotation)[number]>) => {
        updateSetting(
            "traineeRotation",
            rotation.map((entry, i) => (i === index ? { ...entry, ...patch } : entry))
        )
    }
    const addRotationEntry = () => {
        updateSetting("traineeRotation", [...rotation, { inGameName: "", presetKey: "", scenario: bsc.settings.general.scenario }])
    }
    const removeRotationEntry = (index: number) => {
        updateSetting(
            "traineeRotation",
            rotation.filter((_, i) => i !== index)
        )
    }

    return (
        <View style={styles.root}>
            <PageHeader title="Run Queue Settings" />

            <SearchPageProvider page="RunQueueSettings" scrollViewRef={scrollViewRef}>
                <ScrollView ref={scrollViewRef} nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                    <View className="m-1">
                        <CustomTitle
                            title="Run Queue"
                            description="Queue multiple consecutive runs of the same scenario with the same settings. After each run completes, the bot navigates back to the career start and begins the next run automatically."
                        />

                        <CustomCheckbox
                            searchId="run-queue-enable"
                            checked={runQueueSettings.enableRunQueue}
                            onCheckedChange={(checked) => updateSetting("enableRunQueue", checked)}
                            label="Enable Run Queue"
                            description="When enabled, the bot will automatically start additional runs after the first one completes, using the same scenario and settings."
                            className="mt-4"
                        />

                        {runQueueSettings.enableRunQueue && (
                            <View style={{ marginTop: 8 }}>
                                <CustomSlider
                                    searchId="run-queue-total-runs"
                                    value={runQueueSettings.totalRuns}
                                    placeholder={defaultSettings.runQueue.totalRuns}
                                    onValueChange={(value) => updateSetting("totalRuns", value)}
                                    onSlidingComplete={(value) => updateSetting("totalRuns", value)}
                                    min={2}
                                    max={20}
                                    step={1}
                                    label="Number of Runs"
                                    showValue={true}
                                    showLabels={true}
                                    description="Total number of runs to perform in the queue. Each run uses the same scenario and settings."
                                />

                                <CustomSlider
                                    searchId="run-queue-delay"
                                    value={runQueueSettings.delayBetweenRunsSeconds}
                                    placeholder={defaultSettings.runQueue.delayBetweenRunsSeconds}
                                    onValueChange={(value) => updateSetting("delayBetweenRunsSeconds", value)}
                                    onSlidingComplete={(value) => updateSetting("delayBetweenRunsSeconds", value)}
                                    min={5}
                                    max={120}
                                    step={5}
                                    label="Delay Between Runs"
                                    labelUnit="s"
                                    showValue={true}
                                    showLabels={true}
                                    description="Seconds to wait between runs. This allows the game to settle and gives you a window to intervene if needed."
                                />

                                <CustomSlider
                                    searchId="run-queue-max-runtime"
                                    value={runQueueSettings.maxRuntimePerRunMinutes}
                                    placeholder={defaultSettings.runQueue.maxRuntimePerRunMinutes}
                                    onValueChange={(value) => updateSetting("maxRuntimePerRunMinutes", value)}
                                    onSlidingComplete={(value) => updateSetting("maxRuntimePerRunMinutes", value)}
                                    min={30}
                                    max={360}
                                    step={15}
                                    label="Max Runtime Per Run"
                                    labelUnit="m"
                                    showValue={true}
                                    showLabels={true}
                                    description="Per-run safety timeout in minutes. If a single run takes longer than this it ends with a timeout result. Default 180 (3 hours) is comfortable for any scenario; raise it if you run on a very slow device."
                                />

                                <CustomCheckbox
                                    searchId="run-queue-stop-on-error"
                                    checked={runQueueSettings.stopOnError}
                                    onCheckedChange={(checked) => updateSetting("stopOnError", checked)}
                                    label="Stop Queue on Error"
                                    description="When enabled, the queue will halt if any run ends in an error or timeout. When disabled (recommended), the queue will skip the failed run and continue to the next one."
                                    className="mt-4"
                                />

                                <CustomCheckbox
                                    searchId="run-queue-reuse-setup"
                                    checked={runQueueSettings.reuseLastLaunchSetup}
                                    onCheckedChange={(checked) => updateSetting("reuseLastLaunchSetup", checked)}
                                    label="Reuse Last Launch Setup"
                                    description="Reuse the same trainee, support deck, and scenario setup from the previous run. If the game does not offer a reuse option, the queue will stop cleanly."
                                    className="mt-4"
                                />

                                <CustomCheckbox
                                    searchId="run-queue-auto-fill-supports"
                                    checked={runQueueSettings.autoFillSupports}
                                    onCheckedChange={(checked) => updateSetting("autoFillSupports", checked)}
                                    label="Auto-Fill Support Deck"
                                    description="When enabled, clicks Auto-Fill on the support deck screen to fill empty slots before starting. Only used when the deck has empty slots. Does not modify existing cards in the deck."
                                    className="mt-4"
                                />

                                <CustomCheckbox
                                    searchId="run-queue-tp-restore-items"
                                    checked={runQueueSettings.enableTpRestoreWithItems}
                                    onCheckedChange={(checked) => updateSetting("enableTpRestoreWithItems", checked)}
                                    label="Restore TP with Items"
                                    description="When the game asks to restore TP between queued runs, refill TP to the max with Toughness 30 items and continue. Items only - carats are never spent. With no drinks left, the queue declines and stops cleanly. Capped at 10 restores per session."
                                    className="mt-4"
                                />

                                <CustomCheckbox
                                    searchId="run-queue-event-boost"
                                    checked={runQueueSettings.enableEventBoost}
                                    onCheckedChange={(checked) => updateSetting("enableEventBoost", checked)}
                                    label="Tick Event Boost (TP Usage x2)"
                                    description="On the Start Career screen, tick 'Event Boost (TP Usage x2)' so each career earns double event rewards. The TP cost also doubles - the Restore TP with Items option above covers it. Only worth it while a TP event is running; turn this off once the event ends, or you spend double TP for no extra reward."
                                    className="mt-4"
                                />

                                <View style={styles.rotationCard}>
                                    <CustomCheckbox
                                        searchId="run-queue-trainee-rotation"
                                        checked={runQueueSettings.enableTraineeRotation}
                                        onCheckedChange={(checked) => updateSetting("enableTraineeRotation", checked)}
                                        label="Rotate Trainees"
                                        description="Instead of repeating one trainee, cycle through the list below, switching every N runs. Each trainee plays under HER own preset. The bot picks and verifies the trainee in-game at each switch; if the on-screen name does not match the target it stops rather than run the wrong career."
                                    />

                                    {runQueueSettings.enableTraineeRotation && (
                                        <View style={{ marginTop: 8 }}>
                                            <CustomSlider
                                                searchId="run-queue-switch-every"
                                                value={runQueueSettings.switchEveryNRuns}
                                                placeholder={defaultSettings.runQueue.switchEveryNRuns}
                                                onValueChange={(value) => updateSetting("switchEveryNRuns", value)}
                                                onSlidingComplete={(value) => updateSetting("switchEveryNRuns", value)}
                                                min={1}
                                                max={10}
                                                step={1}
                                                label="Switch Every N Runs"
                                                showValue={true}
                                                showLabels={true}
                                                description="How many consecutive careers each trainee plays before the queue switches to the next. With 3 trainees and a value of 3, a 9-run queue plays 3 careers each."
                                            />

                                            {rotation.map((entry, i) => (
                                                <View key={i} style={styles.rotationCard}>
                                                    <Text style={styles.rotationHeader}>Trainee #{i + 1}</Text>

                                                    <CustomSelect
                                                        placeholder="Pick a preset..."
                                                        label="Preset (character + scenario)"
                                                        options={presetOptions}
                                                        value={entry.presetKey ? `${entry.presetKey}@@${entry.scenario}` : undefined}
                                                        onValueChange={(v) => {
                                                            if (!v) return
                                                            const sep = v.lastIndexOf("@@")
                                                            updateRotationEntry(i, { presetKey: v.slice(0, sep), scenario: v.slice(sep + 2) })
                                                        }}
                                                    />

                                                    <Text style={styles.inputLabel}>In-Game Name</Text>
                                                    <TextInput
                                                        style={styles.textInput}
                                                        value={entry.inGameName}
                                                        onChangeText={(text) => updateRotationEntry(i, { inGameName: text })}
                                                        placeholder="e.g. [Kukulkan Warrior] El Condor Pasa"
                                                        placeholderTextColor={colors.foreground + "55"}
                                                        autoCapitalize="none"
                                                        autoCorrect={false}
                                                    />

                                                    <CustomButton variant="destructive" size="sm" style={{ marginTop: 10 }} onPress={() => removeRotationEntry(i)}>
                                                        {`Remove Trainee #${i + 1}`}
                                                    </CustomButton>
                                                </View>
                                            ))}

                                            <CustomButton variant="outline" style={{ marginTop: 12 }} onPress={addRotationEntry}>
                                                + Add Trainee
                                            </CustomButton>

                                            <WarningContainer style={{ marginTop: 12 }}>
                                                The In-Game Name must match exactly what the Trainee Select preview shows, including the [Outfit] prefix — the bot reads that banner to confirm it
                                                picked the right unit. All trainees must use the same scenario for now. Snapshots are rebuilt from your current settings each time you press Start.
                                            </WarningContainer>
                                        </View>
                                    )}
                                </View>

                                <WarningContainer style={{ marginTop: 16 }}>
                                    The run queue navigates the game menus between runs automatically. If the bot encounters an unexpected screen it cannot handle, the queue will stop and report what
                                    happened. Make sure the game is in a stable state before starting a queued session.
                                </WarningContainer>
                            </View>
                        )}
                    </View>
                </ScrollView>
            </SearchPageProvider>
        </View>
    )
}

export default RunQueueSettings
