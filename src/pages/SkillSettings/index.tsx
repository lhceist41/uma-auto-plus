import { useMemo, useContext, useRef } from "react"
import { View, Text, ScrollView, StyleSheet, TouchableOpacity } from "react-native"
import { useNavigation } from "@react-navigation/native"
import { Divider } from "react-native-paper"
import { useTheme } from "../../context/ThemeContext"
import NavigationLink from "../../components/NavigationLink"
import CustomSelect from "../../components/CustomSelect"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomSlider from "../../components/CustomSlider"
import CustomTitle from "../../components/CustomTitle"
import PageHeader from "../../components/PageHeader"
import { BotStateContext, defaultSettings } from "../../context/BotStateContext"
import { SearchPageProvider } from "../../context/SearchPageContext"
import { skillPlanSettingsPages } from "../SkillPlanSettings/config"
import InfoContainer from "../../components/InfoContainer"
import { usePerformanceLogging } from "../../hooks/usePerformanceLogging"
import { adaptiveThresholdLabel, isPlannedOnlyObjective, objectiveLabel, type AccountTier } from "../../lib/adaptiveSkillPolicy"

/**
 * The Skill Settings page.
 * Provides configuration for automated skill purchasing including skill point
 * thresholds, running style / track distance / track surface filters, and
 * navigation links to individual Skill Plan Settings sub-pages.
 */
const SkillSettings = () => {
    usePerformanceLogging("SkillSettings")
    const { colors } = useTheme()
    const navigation = useNavigation()
    const bsc = useContext(BotStateContext)
    const scrollViewRef = useRef<ScrollView>(null)

    const { settings, setSettings } = bsc

    // Merge current skills settings with defaults to handle missing properties.
    const skillSettings = { ...defaultSettings.skills, ...settings.skills }
    const { preferredRunningStyle, preferredTrackDistance, preferredTrackSurface } = skillSettings

    /**
     * Update a skill setting.
     * @param key The key of the setting to update.
     * @param value The value to set the setting to.
     */
    const updateSkillsSetting = (key: string, value: any) => {
        setSettings({
            ...bsc.settings,
            skills: {
                ...bsc.settings.skills,
                [key]: value,
            },
        })
    }

    // Skill-point spending threshold. A preset chip or a typed number sets the mid-career threshold
    // and turns the check on; the plan is always forced to SPEND (never the legacy "stop the bot at
    // the threshold" behavior). "Career end" turns the mid-career check off entirely, leaving only
    // the day-72 Pre-Finals and end-of-career buys - the way the upstream project handles skills.
    const SPEND_PRESETS = [0, 350, 700, 1200]
    const setSpendThreshold = (value: number) => {
        setSettings({
            ...bsc.settings,
            skills: {
                ...bsc.settings.skills,
                enableSkillPointCheck: true,
                skillPointCheck: value,
                plans: {
                    ...bsc.settings.skills.plans,
                    skillPointCheck: { ...bsc.settings.skills.plans.skillPointCheck, enabled: true },
                },
            },
        })
    }
    const setSpendAtCareerEnd = () => {
        setSettings({
            ...bsc.settings,
            skills: { ...bsc.settings.skills, enableSkillPointCheck: false },
        })
    }

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
                description: {
                    fontSize: 14,
                    color: colors.foreground,
                    opacity: 0.7,
                    marginBottom: 16,
                    lineHeight: 20,
                },
                section: {
                    marginBottom: 16,
                },
                inputContainer: {
                    marginBottom: 16,
                },
                inputLabel: {
                    fontSize: 16,
                    color: colors.foreground,
                    marginBottom: 8,
                },
                infoBlock: {
                    marginTop: 12,
                },
                infoLabel: {
                    fontWeight: "bold",
                    color: colors.foreground,
                    fontSize: 14,
                    lineHeight: 22,
                    includeFontPadding: false,
                },
                infoDescription: {
                    fontSize: 14,
                    color: colors.foreground,
                    opacity: 0.7,
                    lineHeight: 22,
                    includeFontPadding: false,
                    marginTop: 2,
                },
                chipRow: {
                    flexDirection: "row",
                    flexWrap: "wrap",
                    gap: 8,
                    marginBottom: 4,
                },
                chip: {
                    paddingVertical: 8,
                    paddingHorizontal: 16,
                    borderRadius: 20,
                    borderWidth: 1,
                    borderColor: colors.border,
                    backgroundColor: colors.card,
                },
                chipActive: {
                    backgroundColor: colors.primary,
                    borderColor: colors.primary,
                },
                chipText: {
                    fontSize: 14,
                    color: colors.foreground,
                },
                chipTextActive: {
                    color: colors.primaryForeground,
                    fontWeight: "600",
                },
            }),
        [colors]
    )

    return (
        <View style={styles.root}>
            <PageHeader title="Skill Settings" />
            <SearchPageProvider page="SkillSettings" scrollViewRef={scrollViewRef}>
                <ScrollView ref={scrollViewRef} nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                    <View style={styles.inputContainer}>
                        <Text style={styles.description}>Allows configuration of automated skill point spending.</Text>
                        <Text style={styles.description}>
                            This feature is not made of magic. If you wish to train an uma up for TT or CM, then you should buy your skills manually. The main purpose of this feature is to make the
                            process of farming rank in events less of a hassle.
                        </Text>
                        <Divider style={{ marginBottom: 16 }} />
                        <CustomSelect
                            searchId="skill-spend-mode"
                            options={[
                                { value: "manual", label: "Manual" },
                                { value: "adaptive", label: "Adaptive" },
                            ]}
                            value={skillSettings.skillSpendMode}
                            defaultValue={defaultSettings.skills.skillSpendMode}
                            onValueChange={(value) => updateSkillsSetting("skillSpendMode", value)}
                            label="Skill Spend Mode"
                            description={
                                skillSettings.skillSpendMode === "adaptive"
                                    ? "Chooses a high-water threshold from the account-strength tier below. Finals and career-end spending still occur normally."
                                    : "Uses the Skill Point threshold configured below. This preserves the current behavior."
                            }
                            placeholder="Select Mode"
                        />
                        {skillSettings.skillSpendMode === "adaptive" && (
                            <View style={{ marginTop: 8 }}>
                                <CustomSelect
                                    searchId="account-strength"
                                    options={[
                                        { value: "auto", label: "Auto" },
                                        { value: "new", label: "New" },
                                        { value: "developing", label: "Developing" },
                                        { value: "established", label: "Established" },
                                        { value: "endgame", label: "Endgame" },
                                    ]}
                                    value={skillSettings.accountTier}
                                    defaultValue={defaultSettings.skills.accountTier}
                                    onValueChange={(value) => updateSkillsSetting("accountTier", value)}
                                    label="Account Strength"
                                    description="How developed this account's supports and roster are. Support quality and progression matter far more than the Team Rank letter - the ranks below are only a loose guide."
                                    placeholder="Select Account Strength"
                                />
                                <InfoContainer>
                                    <View>
                                        <Text style={styles.infoLabel}>{adaptiveThresholdLabel(skillSettings.accountTier as AccountTier)}</Text>
                                        <View style={styles.infoBlock}>
                                            <Text style={styles.infoDescription}>Objective: {objectiveLabel(skillSettings.skillSpendObjective)} (from preset)</Text>
                                        </View>
                                        {isPlannedOnlyObjective(skillSettings.skillSpendObjective) && (
                                            <View style={styles.infoBlock}>
                                                <Text style={styles.infoDescription}>Planned-only spending: On</Text>
                                                <Text style={styles.infoDescription}>Only planned skills and the existing inherited/negative options are considered. Leftover SP is accepted.</Text>
                                            </View>
                                        )}
                                        <View style={styles.infoBlock}>
                                            <Text style={styles.infoDescription}>Auto — uses Developing for now. New — early account, thin supports (roughly F–E). Developing — growing roster (roughly D–B). Established — reliable roster (roughly A–S). Endgame — strong roster (roughly SS and up).</Text>
                                        </View>
                                        {!skillSettings.enableSkillPointCheck && (
                                            <View style={styles.infoBlock}>
                                                <Text style={styles.infoDescription}>
                                                    Mid-career spending is currently set to “Career end”, so no high-water threshold applies. Switch to Manual and pick a spend chip to re-enable it;
                                                    the tier threshold then takes effect when you return to Adaptive.
                                                </Text>
                                            </View>
                                        )}
                                    </View>
                                </InfoContainer>
                            </View>
                        )}
                        {skillSettings.skillSpendMode === "manual" && (
                            <>
                                <Text style={styles.inputLabel}>Spend skill points when SP reaches</Text>
                                <Text style={styles.description}>
                                    The bot buys skills mid-career once your skill points reach this amount. Tap a preset or set an exact number below. Career end (the last chip) holds everything for
                                    the Pre-Finals and end-of-career buys, matching the upstream project. Your choice here sticks across trainee preset and rotation switches.
                                </Text>
                                <View style={styles.chipRow}>
                                    {SPEND_PRESETS.map((preset) => {
                                        const active = bsc.settings.skills.enableSkillPointCheck && bsc.settings.skills.skillPointCheck === preset
                                        return (
                                            <TouchableOpacity key={preset} style={[styles.chip, active && styles.chipActive]} onPress={() => setSpendThreshold(preset)}>
                                                <Text style={[styles.chipText, active && styles.chipTextActive]}>{preset}</Text>
                                            </TouchableOpacity>
                                        )
                                    })}
                                    <TouchableOpacity style={[styles.chip, !bsc.settings.skills.enableSkillPointCheck && styles.chipActive]} onPress={setSpendAtCareerEnd}>
                                        <Text style={[styles.chipText, !bsc.settings.skills.enableSkillPointCheck && styles.chipTextActive]}>Career end</Text>
                                    </TouchableOpacity>
                                </View>

                                <View style={bsc.settings.skills.enableSkillPointCheck ? { marginTop: 8 } : { display: "none" }}>
                                    <CustomSlider
                                        searchId="skill-point-check"
                                        searchCondition={bsc.settings.skills.enableSkillPointCheck}
                                        value={bsc.settings.skills.skillPointCheck}
                                        placeholder={bsc.defaultSettings.skills.skillPointCheck}
                                        onValueChange={(value) => setSpendThreshold(value)}
                                        onSlidingComplete={(value) => setSpendThreshold(value)}
                                        min={0}
                                        max={2000}
                                        step={10}
                                        label="Custom Threshold"
                                        description="Spend when skill points reach this exact amount. Type a number or drag."
                                        labelUnit=""
                                        showValue={true}
                                        showLabels={true}
                                    />
                                </View>
                            </>
                        )}
                    </View>
                    <CustomTitle title="Skill Style Overrides" description="Override which types of skills the bot can purchase." />
                    <Text style={styles.description}>
                        Any skills whose activation condition does not match the selected override will be filtered out of the list of available skills that the bot can consider for purchasing. Skills
                        that have no activation conditions will still be available.
                    </Text>
                    <View>
                        <View style={styles.inputContainer}>
                            <CustomSelect
                                searchId="skill-plan-running-style"
                                options={[
                                    { value: "inherit", label: "Use [Racing Settings] -> [Original Race Strategy]" },
                                    { value: "no_preference", label: "Any" },
                                    { value: "front_runner", label: "Front Runner" },
                                    { value: "pace_chaser", label: "Pace Chaser" },
                                    { value: "late_surger", label: "Late Surger" },
                                    { value: "end_closer", label: "End Closer" },
                                ]}
                                value={preferredRunningStyle}
                                defaultValue={defaultSettings.skills.preferredRunningStyle}
                                onValueChange={(value) => updateSkillsSetting("preferredRunningStyle", value)}
                                label="Running Style for Skills"
                                description="Dictates which skills are considered for purchase based on the preferred running style."
                                placeholder="Select Running Style"
                            />
                            <InfoContainer>
                                <View>
                                    <Text style={styles.infoLabel}>There are two different groups of Running Style skills.</Text>
                                    <View style={styles.infoBlock}>
                                        <Text style={styles.infoDescription}>
                                            The first are skills that specifically say in their description that they are for a specific running style. These cannot be activated unless the trainee is
                                            using that running style.
                                        </Text>
                                    </View>
                                    <View style={styles.infoBlock}>
                                        <Text style={styles.infoDescription}>
                                            The second are skills that do not say they are for a running style, but have activation conditions which limit which styles would actually be able to
                                            activate them (ignoring rare cases).
                                        </Text>
                                    </View>
                                    <View style={styles.infoBlock}>
                                        <Text style={styles.infoDescription}>
                                            This setting will filter skills based on both of these conditions. This helps us avoid having situations like an End Closer purchasing a skill like
                                            &quot;Keeping the Lead&quot;. This skill doesn&apos;t require using the Front Runner style to activate, but it does require the runner to be in the lead
                                            mid-race which is very unlikely for an End Closer.
                                        </Text>
                                    </View>
                                    <Text style={[styles.infoLabel, { marginTop: 12 }]}>Detailed breakdown of examples:</Text>

                                    <View style={styles.infoBlock}>
                                        <Text style={styles.infoLabel}>Use [Racing Settings] {"->"} [Original Race Strategy]</Text>
                                        <Text style={styles.infoDescription}>
                                            • Inherits the running style from your Racing Settings. For example, if you set the Strategy to &quot;Late Surger&quot; in Racing Settings, only Late Surger
                                            skills will be considered.
                                        </Text>
                                    </View>

                                    <View style={styles.infoBlock}>
                                        <Text style={styles.infoLabel}>Any</Text>
                                        <Text style={styles.infoDescription}>
                                            • Does not filter any skills based on running style. For example, even if your trainee is an &quot;End Closer&quot;, the bot may still purchase &quot;Pace
                                            Chaser Corners ○&quot; (a Pace Chaser skill) if it&apos;s available.
                                        </Text>
                                    </View>

                                    <View style={styles.infoBlock}>
                                        <Text style={styles.infoLabel}>Front Runner</Text>
                                        <Text style={styles.infoDescription}>
                                            • Only considers skills that are compatible with the Front Runner style. For example, skills like &quot;Escape Artist&quot; will be included, while
                                            &quot;Outer Swell&quot; (Late Surger) will be ignored.
                                        </Text>
                                    </View>
                                </View>
                            </InfoContainer>
                        </View>
                        <View style={styles.inputContainer}>
                            <CustomSelect
                                searchId="preferred-distance-override"
                                options={[
                                    { value: "inherit", label: "Use [Training Settings] -> [Preferred Distance Override]" },
                                    { value: "no_preference", label: "Any" },
                                    { value: "sprint", label: "Sprint" },
                                    { value: "mile", label: "Mile" },
                                    { value: "medium", label: "Medium" },
                                    { value: "long", label: "Long" },
                                ]}
                                value={preferredTrackDistance}
                                defaultValue={defaultSettings.skills.preferredTrackDistance}
                                onValueChange={(value) => updateSkillsSetting("preferredTrackDistance", value)}
                                label="Track Distance for Skills"
                                description="Dictates which skills are considered for purchase based on the track distance."
                                placeholder="Select Track Distance"
                            />
                        </View>
                        <View style={styles.inputContainer}>
                            <CustomSelect
                                searchId="preferred-track-surface"
                                options={[
                                    { value: "no_preference", label: "Any" },
                                    { value: "turf", label: "Turf" },
                                    { value: "dirt", label: "Dirt" },
                                ]}
                                value={preferredTrackSurface}
                                defaultValue={defaultSettings.skills.preferredTrackSurface}
                                onValueChange={(value) => updateSkillsSetting("preferredTrackSurface", value)}
                                label="Track Surface for Skills"
                                description="Dictates which skills are considered for purchase based on the terrain."
                                placeholder="Select Track Surface"
                            />
                            <InfoContainer>
                                <Text style={styles.infoDescription}>
                                    As of 2026-02-19, there are no skills that only apply to the Turf surface type. The only track surface specific skills are ones for Dirt. So if you choose Dirt, all
                                    skills will still be available for purchase. However if you choose Turf, then all the Dirt skills will be ignored.
                                </Text>
                            </InfoContainer>
                        </View>
                        <View style={styles.inputContainer}>
                            <CustomCheckbox
                                searchId="skip-double-circle-upgrades"
                                checked={skillSettings.skipDoubleCircleUpgrades}
                                onCheckedChange={(checked) => updateSkillsSetting("skipDoubleCircleUpgrades", checked)}
                                label="Skip ◎ Skill Upgrades"
                                description="Buy only the ○ version of a skill and skip its ◎ upgrade. The ◎ costs far more for a small extra gain, so skipping it spreads the same skill points across more distinct skills."
                            />
                        </View>
                    </View>
                    <Divider style={{ marginBottom: 24 }} />
                    <View style={styles.section}>
                        <View className="m-1">
                            {Object.values(skillPlanSettingsPages).map((value) => (
                                <NavigationLink
                                    key={value.name}
                                    title={`Go to ${value.title} Skill Plan Settings`}
                                    description={value.description.split("\n")[0]}
                                    onPress={() => navigation.navigate(value.name as never)}
                                    style={{ ...styles.section, marginTop: 0 }}
                                />
                            ))}
                        </View>
                    </View>
                </ScrollView>
            </SearchPageProvider>
        </View>
    )
}

export default SkillSettings
