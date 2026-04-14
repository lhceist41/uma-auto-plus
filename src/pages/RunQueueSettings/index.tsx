import { useMemo, useContext, useRef } from "react"
import { View, ScrollView, StyleSheet } from "react-native"
import { useTheme } from "../../context/ThemeContext"
import { BotStateContext, defaultSettings } from "../../context/BotStateContext"
import CustomSlider from "../../components/CustomSlider"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomTitle from "../../components/CustomTitle"
import PageHeader from "../../components/PageHeader"
import WarningContainer from "../../components/WarningContainer"
import { SearchPageProvider } from "../../context/SearchPageContext"
import { usePerformanceLogging } from "../../hooks/usePerformanceLogging"

/**
 * The Run Queue Settings page.
 * Provides controls for queueing multiple consecutive training runs
 * so the bot can run unattended for extended periods.
 */
const RunQueueSettings = () => {
    usePerformanceLogging("RunQueueSettings")
    const { colors } = useTheme()
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
            }),
        [colors]
    )

    const runQueueSettings = { ...defaultSettings.runQueue, ...bsc.settings.runQueue }

    const updateSetting = (key: keyof typeof bsc.settings.runQueue, value: any) => {
        bsc.setSettings({
            ...bsc.settings,
            runQueue: { ...bsc.settings.runQueue, [key]: value },
        })
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
                                <CustomCheckbox
                                    searchId="run-queue-stop-on-error"
                                    checked={runQueueSettings.stopOnError}
                                    onCheckedChange={(checked) => updateSetting("stopOnError", checked)}
                                    label="Stop Queue on Error"
                                    description="When enabled, the queue will halt if any run ends in an error or timeout. When disabled, the queue will skip the failed run and continue to the next one."
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

                                <WarningContainer style={{ marginTop: 16 }}>
                                    The run queue navigates the game menus between runs automatically. If the bot encounters an unexpected screen it cannot handle, the queue will stop and report what happened. Make sure the game is in a stable state before starting a queued session.
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
