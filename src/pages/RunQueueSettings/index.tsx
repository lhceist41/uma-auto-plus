import { useMemo, useContext, useRef, useState } from "react"
import { View, ScrollView, StyleSheet, TextInput, Text, TouchableOpacity } from "react-native"
import { ChevronRight, TriangleAlert } from "lucide-react-native"
import { useTheme } from "../../context/ThemeContext"
import { BotStateContext, defaultSettings } from "../../context/BotStateContext"
import CustomSlider from "../../components/CustomSlider"
import CustomCheckbox from "../../components/CustomCheckbox"
import PresetPicker from "../../components/PresetPicker"
import CustomButton from "../../components/CustomButton"
import CustomTitle from "../../components/CustomTitle"
import PageHeader from "../../components/PageHeader"
import WarningContainer from "../../components/WarningContainer"
import { avoidAdvisoryFor } from "../../data/characterPresets"
import { baseCharacter, deriveInGameName, deriveExcludeOutfits } from "../../lib/rotationSnapshots"
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
                inputDescription: {
                    fontSize: 12,
                    color: colors.foreground + "99",
                    marginBottom: 6,
                    lineHeight: 17,
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

    // Which rotation row the preset picker is currently open for, or null when closed.
    const [pickerForRow, setPickerForRow] = useState<number | null>(null)

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
                                    description="When enabled, clicks the game's own Auto-Fill button on the support deck screen before each career starts. The game then rebuilds the deck with its own logic, which may replace cards you placed yourself as well as fill empty slots. Disable this to keep your hand-built deck exactly as you left it."
                                    className="mt-4"
                                />

                                <CustomSlider
                                    searchId="run-queue-support-deck-index"
                                    value={runQueueSettings.supportDeckIndex}
                                    placeholder={defaultSettings.runQueue.supportDeckIndex}
                                    onValueChange={(value) => updateSetting("supportDeckIndex", value)}
                                    onSlidingComplete={(value) => updateSetting("supportDeckIndex", value)}
                                    min={0}
                                    max={10}
                                    step={1}
                                    label="Required Support Deck"
                                    showValue={true}
                                    showLabels={true}
                                    description="Require a specific saved support formation (Deck 1-10) at career start. The bot selects that deck on the Support Formation screen, verifies it before and after the friend borrow, and refuses to start the career (no TP spent) if it cannot -- so it can never launch on the game's default deck by mistake. Setting this also suppresses Auto-Fill. 0 = off: use whatever deck the game shows."
                                    className="mt-4"
                                />

                                <CustomCheckbox
                                    searchId="run-queue-tp-restore-items"
                                    checked={runQueueSettings.enableTpRestoreWithItems}
                                    onCheckedChange={(checked) => updateSetting("enableTpRestoreWithItems", checked)}
                                    label="Restore TP with Items"
                                    description="When the game asks to restore TP between queued runs, refill TP to the max and continue. Priority: Toughness 30, then Star Fruit, then Carats as the last resort. Capped at 10 restores per session."
                                    className="mt-4"
                                />

                                <CustomCheckbox
                                    searchId="run-queue-spark-reroll"
                                    checked={runQueueSettings.enableSparkReroll}
                                    onCheckedChange={(checked) => updateSetting("enableSparkReroll", checked)}
                                    label="Auto-Reroll Sparks (30 TP)"
                                    description="On the career-end Sparks screen, spend 30 TP to redraw the spark set once when it prices below a fresh roll: a 2 or 3-star stat spark is always kept, and a 1-star stat spark is redrawn unless every stat finished under 600 (a redraw cannot roll a 3-star there) or the set holds a 3-star aptitude or skill spark worth protecting. After the spend, the bot reads both sets on the game's selection screen and keeps the better one, verifying which set is named on the final confirmation before committing; if it cannot verify the selection it stops safely and you finish the choice by hand (keeping the original is always available there). Experimental: supervise the first spends. This spends TP."
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

                                <CustomCheckbox
                                    searchId="run-queue-legacy-include-guests"
                                    checked={runQueueSettings.enableLegacyIncludeGuests}
                                    onCheckedChange={(checked) => updateSetting("enableLegacyIncludeGuests", checked)}
                                    label="Include Guests in Legacy Auto-Select"
                                    description="On the Confirm Auto-Select legacy screen, tick 'Include Guests' so Auto-Select may borrow a guest (rental) parent. Borrowing a guest costs monies. OFF by default - Auto-Select then uses only your owned umas (free), which suits farming your own spark parents; turn it on if your owned parents are weak and you would rather inherit stronger borrowed 3-star parents. Note: during the rare Racing Carnival event this also leaves the free Carnival spark-bonus tick off, so enable it then if you want that bonus."
                                    className="mt-4"
                                />

                                <CustomCheckbox
                                    searchId="run-queue-lineage-capture"
                                    checked={runQueueSettings.enableLineageCapture}
                                    onCheckedChange={(checked) => updateSetting("enableLineageCapture", checked)}
                                    label="Capture Lineage Data"
                                    description="After Auto-Select fills the legacy slots, briefly open the Legacy Select 'Sparks' view and record the six ancestors (both parents and all four grandparents) with their inherited factors, saved as data for later analysis. OFF by default. Purely observational: it never changes the selected parents and never blocks a career - if the read fails for any reason the launch continues normally. Adds a few seconds and some extra taps to each launch while it reads the list."
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

                                                    <Text style={styles.inputLabel}>Preset (character + scenario)</Text>
                                                    <TouchableOpacity
                                                        onPress={() => setPickerForRow(i)}
                                                        style={{
                                                            flexDirection: "row",
                                                            alignItems: "center",
                                                            paddingHorizontal: 10,
                                                            paddingVertical: 9,
                                                            borderRadius: 6,
                                                            borderWidth: 1,
                                                            borderColor: colors.border,
                                                            backgroundColor: colors.background,
                                                        }}
                                                    >
                                                        <Text style={{ flex: 1, fontSize: 13, color: colors.foreground, opacity: entry.presetKey ? 1 : 0.6 }}>
                                                            {entry.presetKey ? `${entry.presetKey} — ${entry.scenario}` : "Pick a preset..."}
                                                        </Text>
                                                        <ChevronRight size={16} color={colors.foreground} opacity={0.5} />
                                                    </TouchableOpacity>

                                                    {entry.presetKey
                                                        ? (() => {
                                                              const avoid = avoidAdvisoryFor(entry.presetKey, entry.scenario)
                                                              return avoid ? (
                                                                  <View
                                                                      style={{
                                                                          flexDirection: "row",
                                                                          alignItems: "flex-start",
                                                                          marginTop: 10,
                                                                          paddingHorizontal: 10,
                                                                          paddingVertical: 8,
                                                                          backgroundColor: "rgba(234, 179, 8, 0.15)",
                                                                          borderLeftWidth: 3,
                                                                          borderLeftColor: "#eab308",
                                                                          borderRadius: 6,
                                                                      }}
                                                                  >
                                                                      <TriangleAlert size={16} color="#eab308" style={{ marginRight: 6, marginTop: 2 }} />
                                                                      <Text style={{ flex: 1, fontSize: 12, color: colors.foreground, lineHeight: 16 }}>
                                                                          <Text style={{ fontWeight: "700", color: "#eab308" }}>Mismatch: </Text>
                                                                          {avoid.reason}
                                                                      </Text>
                                                                  </View>
                                                              ) : null
                                                          })()
                                                        : null}

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

                                            <PresetPicker
                                                visible={pickerForRow !== null}
                                                onClose={() => setPickerForRow(null)}
                                                onApply={(presetName, scenario) => {
                                                    const row = pickerForRow
                                                    setPickerForRow(null)
                                                    if (row === null || row >= rotation.length) return
                                                    const entry = rotation[row]
                                                    const patch: Partial<(typeof rotation)[number]> = {
                                                        presetKey: presetName,
                                                        scenario,
                                                        excludeOutfits: deriveExcludeOutfits(presetName),
                                                    }
                                                    // Auto-fill the In-Game Name to the selected character+outfit. Overwrite when the
                                                    // character changes (kills the stale-name drift) or the field is empty; keep a
                                                    // user-customized name when only the scenario changes for the same character.
                                                    if (!entry.inGameName.trim() || baseCharacter(entry.presetKey) !== baseCharacter(presetName)) {
                                                        patch.inGameName = deriveInGameName(presetName)
                                                    }
                                                    updateRotationEntry(row, patch)
                                                }}
                                            />

                                            <WarningContainer style={{ marginTop: 12 }}>
                                                The In-Game Name must match exactly what the Trainee Select preview shows, including the [Outfit] prefix — the bot reads that banner to confirm it
                                                picked the right unit. Mixed scenarios are supported: the bot pages the Scenario Select carousel to each trainee&apos;s scenario between runs. Snapshots
                                                are rebuilt from your current settings each time you press Start.
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

                        <CustomCheckbox
                            searchId="run-queue-smart-borrow"
                            checked={runQueueSettings.enableSmartBorrow}
                            onCheckedChange={(checked) => updateSetting("enableSmartBorrow", checked)}
                            label="Smart Borrow"
                            description="Whenever the bot fills the empty friend slot before starting a career - queued runs and single-run launches alike - it scrolls down through the Borrow Card list and borrows the best card it finds from a curated list of great picks (Kitasan Black first). Cards marked '! Duplicate Support' are skipped, and if a duplicate lands in the slot anyway it is swapped for the next-best pick instead of blocking the career start. Follow trainers with strong cards to give it good options. Off = the default pick: your strong friend card when spotted, otherwise the top row."
                            className="mt-4"
                        />

                        <CustomCheckbox
                            searchId="run-queue-build-aware-launch"
                            checked={runQueueSettings.enableBuildAwareLaunch}
                            onCheckedChange={(checked) => updateSetting("enableBuildAwareLaunch", checked)}
                            label="Build-Aware Launch (advanced)"
                            description="Advanced. When on, a career launch borrows and starts through the build-aware launch transaction: it re-scans the live borrow pool for freshness, selects and verifies the exact card named by a pushed BUILD_AWARE intent (outcomes/smart_borrow_intent.json), checks the owned deck is unchanged, and presses Start Career only when everything verifies. If no fresh valid build-aware intent is available, the launch is blocked and no career starts - there is no fallback to the normal borrow. Leave OFF for the normal hands-off launch; turn on only when you are pushing build-aware intents per launch."
                            className="mt-4"
                        />
                    </View>
                </ScrollView>
            </SearchPageProvider>
        </View>
    )
}

export default RunQueueSettings
