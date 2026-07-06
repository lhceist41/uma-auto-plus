import React, { useMemo, useState } from "react"
import { Modal, SectionList, TextInput, TouchableOpacity, View } from "react-native"
import { BadgeCheck, FlaskConical, Search, Star, ThumbsUp, TriangleAlert, X } from "lucide-react-native"
import { Text } from "../ui/text"
import { useTheme } from "../../context/ThemeContext"
import { characterPresets, trainerAdvisories } from "../../data/characterPresets"
import { presetCharacter, presetOutfit, presetValidation } from "../../data/presetMeta"
import { useFavoriteCharacters } from "../../lib/uiPrefs"

/** Career scenarios in display order, with the short labels used on the scenario chips. */
const SCENARIOS: { name: string; short: string }[] = [
    { name: "URA Finale", short: "URA" },
    { name: "Unity Cup", short: "UC" },
    { name: "Trackblazer", short: "TB" },
]

const GREEN = "#22c55e"
const AMBER = "#eab308"

/** Deterministic avatar background per character so the list reads consistently across opens. */
const AVATAR_COLORS = ["#7c3aed", "#2563eb", "#0891b2", "#059669", "#ca8a04", "#dc2626", "#db2777", "#9333ea"]

interface PickerRow {
    /** The preset name, the persistence key shared by all of this row's scenario presets. */
    presetName: string
    character: string
    outfit: string
    /** Scenarios a preset exists for, in SCENARIOS order. */
    scenarios: string[]
}

/** Advisory state of one (preset, scenario) pair, driving chip and card colors. */
type AdvisoryState = { kind: "recommend" } | { kind: "avoid"; reason: string } | { kind: "neutral" } | { kind: "missing" }

interface PresetPickerProps {
    /** Whether the picker modal is shown. */
    visible: boolean
    /** Called when the user dismisses the picker without applying. */
    onClose: () => void
    /** Called when the user applies a preset for a scenario. The caller owns the settings mutation. */
    onApply: (presetName: string, scenario: string) => void
}

/** Resolves the advisory state for a (preset, scenario) pair from trainerAdvisories. */
const advisoryFor = (presetName: string, scenario: string, exists: boolean): AdvisoryState => {
    if (!exists) return { kind: "missing" }
    const advisory = trainerAdvisories[presetName]
    const avoid = advisory?.avoid?.find((a) => a.scenario === scenario)
    if (avoid) return { kind: "avoid", reason: avoid.reason }
    if (advisory?.recommended?.includes(scenario)) return { kind: "recommend" }
    return { kind: "neutral" }
}

/** One-line build summary for a preset's scenario entry ("Stamina-first · 8 planned races"). */
const buildSummary = (presetName: string, scenario: string): string => {
    const preset = characterPresets.find((p) => p.name === presetName && p.scenario === scenario)
    if (!preset) return ""
    const parts: string[] = []
    const priority = (preset.settings as any)?.training?.statPrioritization?.[0]
    if (priority) parts.push(`${priority}-first`)
    const rawPlan = (preset.settings as any)?.racing?.racingPlan
    if (rawPlan) {
        try {
            const count = JSON.parse(rawPlan).length
            if (count > 0) parts.push(`${count} planned races`)
        } catch {
            // A malformed plan string only affects the summary line; the apply path re-parses it.
        }
    }
    return parts.join(" · ")
}

/**
 * Full-screen trainee/preset picker: searchable, grouped by character with one row per outfit,
 * scenario advisory chips, validation badges, and a pinned favorites section. Tapping a row
 * expands per-scenario cards; Apply hands (presetName, scenario) back to the caller.
 * @param visible Whether the picker modal is shown.
 * @param onClose Called when the user dismisses the picker.
 * @param onApply Called with the chosen preset name and scenario.
 */
const PresetPicker: React.FC<PresetPickerProps> = ({ visible, onClose, onApply }) => {
    const { colors } = useTheme()
    const [query, setQuery] = useState("")
    const [expandedPreset, setExpandedPreset] = useState<string | null>(null)
    const [favorites, toggleFavorite] = useFavoriteCharacters()

    // One row per preset name (= per outfit), with the scenarios that name has presets for.
    const allRows = useMemo<PickerRow[]>(() => {
        const byName = new Map<string, Set<string>>()
        for (const preset of characterPresets) {
            if (!byName.has(preset.name)) byName.set(preset.name, new Set())
            byName.get(preset.name)!.add(preset.scenario)
        }
        return Array.from(byName.entries())
            .map(([presetName, scenarios]) => ({
                presetName,
                character: presetCharacter(presetName),
                outfit: presetOutfit(presetName),
                scenarios: SCENARIOS.filter((s) => scenarios.has(s.name)).map((s) => s.name),
            }))
            .sort((a, b) => a.character.localeCompare(b.character) || a.outfit.localeCompare(b.outfit))
    }, [])

    const sections = useMemo(() => {
        const q = query.trim().toLowerCase()
        const matches = q.length === 0 ? allRows : allRows.filter((r) => r.character.toLowerCase().includes(q) || r.outfit.toLowerCase().includes(q))
        const favoriteRows = matches.filter((r) => favorites.includes(r.character))
        const result: { title: string; data: PickerRow[] }[] = []
        if (favoriteRows.length > 0) result.push({ title: "Favorites", data: favoriteRows })
        result.push({ title: "All Trainees", data: matches })
        return result
    }, [allRows, favorites, query])

    const avatarColor = (character: string) => {
        let sum = 0
        for (let i = 0; i < character.length; i++) sum += character.charCodeAt(i)
        return AVATAR_COLORS[sum % AVATAR_COLORS.length]
    }

    const monogram = (character: string) =>
        character
            .split(/\s+/)
            .slice(0, 2)
            .map((word) => word[0])
            .join("")
            .toUpperCase()

    const chipColors = (state: AdvisoryState): { bg: string; fg: string } => {
        switch (state.kind) {
            case "recommend":
                return { bg: "rgba(34, 197, 94, 0.18)", fg: GREEN }
            case "avoid":
                return { bg: "rgba(234, 179, 8, 0.18)", fg: AMBER }
            case "neutral":
                return { bg: colors.muted, fg: colors.foreground }
            case "missing":
                return { bg: "transparent", fg: colors.border }
        }
    }

    const renderScenarioCard = (row: PickerRow, scenario: { name: string; short: string }) => {
        const exists = row.scenarios.includes(scenario.name)
        const advisory = advisoryFor(row.presetName, scenario.name, exists)
        const validation = presetValidation(row.presetName, scenario.name)
        const summary = exists ? buildSummary(row.presetName, scenario.name) : ""

        return (
            <View
                key={scenario.name}
                style={{
                    marginTop: 6,
                    padding: 10,
                    borderRadius: 8,
                    backgroundColor: colors.muted,
                    borderLeftWidth: 3,
                    borderLeftColor: advisory.kind === "recommend" ? GREEN : advisory.kind === "avoid" ? AMBER : colors.border,
                    opacity: exists ? 1 : 0.5,
                }}
            >
                <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
                    <View style={{ flexDirection: "row", alignItems: "center", flex: 1 }}>
                        <Text style={{ fontSize: 14, fontWeight: "700", color: colors.foreground }}>{scenario.name}</Text>
                        {exists && (
                            <View style={{ flexDirection: "row", alignItems: "center", marginLeft: 8 }}>
                                {validation === "validated" ? <BadgeCheck size={13} color={GREEN} /> : <FlaskConical size={13} color={colors.foreground} opacity={0.6} />}
                                <Text style={{ fontSize: 11, marginLeft: 3, color: validation === "validated" ? GREEN : colors.foreground, opacity: validation === "validated" ? 1 : 0.6 }}>
                                    {validation === "validated" ? "Validated" : "Research"}
                                </Text>
                            </View>
                        )}
                    </View>
                    {exists ? (
                        <TouchableOpacity
                            onPress={() => onApply(row.presetName, scenario.name)}
                            style={{ paddingHorizontal: 14, paddingVertical: 6, backgroundColor: colors.primary, borderRadius: 6 }}
                        >
                            <Text style={{ fontSize: 12, fontWeight: "700", color: colors.background }}>Apply</Text>
                        </TouchableOpacity>
                    ) : (
                        <Text style={{ fontSize: 11, color: colors.foreground, opacity: 0.5 }}>No preset</Text>
                    )}
                </View>
                {summary.length > 0 && <Text style={{ fontSize: 12, color: colors.foreground, opacity: 0.75, marginTop: 4 }}>{summary}</Text>}
                {advisory.kind === "recommend" && (
                    <View style={{ flexDirection: "row", alignItems: "center", marginTop: 4 }}>
                        <ThumbsUp size={12} color={GREEN} style={{ marginRight: 4 }} />
                        <Text style={{ fontSize: 12, color: GREEN }}>Recommended fit</Text>
                    </View>
                )}
                {advisory.kind === "avoid" && (
                    <View style={{ flexDirection: "row", alignItems: "flex-start", marginTop: 4 }}>
                        <TriangleAlert size={12} color={AMBER} style={{ marginRight: 4, marginTop: 2 }} />
                        <Text style={{ flex: 1, fontSize: 12, color: colors.foreground, opacity: 0.85, lineHeight: 16 }}>{advisory.reason}</Text>
                    </View>
                )}
            </View>
        )
    }

    const renderRow = ({ item }: { item: PickerRow }) => {
        const expanded = expandedPreset === item.presetName
        const isFavorite = favorites.includes(item.character)

        return (
            <View style={{ paddingHorizontal: 12, paddingVertical: 2 }}>
                <TouchableOpacity
                    onPress={() => setExpandedPreset(expanded ? null : item.presetName)}
                    style={{
                        flexDirection: "row",
                        alignItems: "center",
                        paddingVertical: 10,
                        paddingHorizontal: 10,
                        borderRadius: 10,
                        backgroundColor: expanded ? colors.muted : "transparent",
                    }}
                >
                    <View style={{ width: 38, height: 38, borderRadius: 19, backgroundColor: avatarColor(item.character), alignItems: "center", justifyContent: "center", marginRight: 10 }}>
                        <Text style={{ fontSize: 14, fontWeight: "700", color: "#ffffff" }}>{monogram(item.character)}</Text>
                    </View>
                    <View style={{ flex: 1 }}>
                        <Text style={{ fontSize: 15, fontWeight: "600", color: colors.foreground }}>{item.character}</Text>
                        {item.outfit.length > 0 && <Text style={{ fontSize: 12, color: colors.foreground, opacity: 0.6 }}>{item.outfit}</Text>}
                    </View>
                    <View style={{ flexDirection: "row", alignItems: "center" }}>
                        {SCENARIOS.map((scenario) => {
                            const state = advisoryFor(item.presetName, scenario.name, item.scenarios.includes(scenario.name))
                            const { bg, fg } = chipColors(state)
                            return (
                                <View
                                    key={scenario.short}
                                    style={{
                                        paddingHorizontal: 6,
                                        paddingVertical: 2,
                                        borderRadius: 4,
                                        backgroundColor: bg,
                                        marginLeft: 4,
                                        borderWidth: state.kind === "missing" ? 1 : 0,
                                        borderColor: colors.border,
                                    }}
                                >
                                    <Text style={{ fontSize: 10, fontWeight: "700", color: fg }}>{scenario.short}</Text>
                                </View>
                            )
                        })}
                        <TouchableOpacity onPress={() => toggleFavorite(item.character)} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }} style={{ marginLeft: 8 }}>
                            <Star size={18} color={isFavorite ? AMBER : colors.border} fill={isFavorite ? AMBER : "transparent"} />
                        </TouchableOpacity>
                    </View>
                </TouchableOpacity>
                {expanded && <View style={{ paddingLeft: 12, paddingBottom: 8 }}>{SCENARIOS.map((scenario) => renderScenarioCard(item, scenario))}</View>}
            </View>
        )
    }

    return (
        <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
            <View style={{ flex: 1, backgroundColor: colors.background, paddingTop: 40 }}>
                <View style={{ flexDirection: "row", alignItems: "center", paddingHorizontal: 16, marginBottom: 10 }}>
                    <Text style={{ flex: 1, fontSize: 20, fontWeight: "700", color: colors.foreground }}>Select Trainee</Text>
                    <TouchableOpacity onPress={onClose} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}>
                        <X size={22} color={colors.foreground} />
                    </TouchableOpacity>
                </View>
                <View
                    style={{
                        flexDirection: "row",
                        alignItems: "center",
                        marginHorizontal: 16,
                        marginBottom: 8,
                        paddingHorizontal: 10,
                        borderRadius: 8,
                        borderWidth: 1,
                        borderColor: colors.border,
                        backgroundColor: colors.background,
                    }}
                >
                    <Search size={16} color={colors.foreground} opacity={0.5} />
                    <TextInput
                        value={query}
                        onChangeText={setQuery}
                        placeholder="Search trainee or outfit..."
                        placeholderTextColor={colors.border}
                        style={{ flex: 1, paddingVertical: 8, paddingHorizontal: 8, fontSize: 14, color: colors.foreground }}
                    />
                    {query.length > 0 && (
                        <TouchableOpacity onPress={() => setQuery("")} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
                            <X size={16} color={colors.foreground} opacity={0.5} />
                        </TouchableOpacity>
                    )}
                </View>
                <SectionList
                    sections={sections}
                    keyExtractor={(item, index) => `${item.presetName}-${index}`}
                    renderItem={renderRow}
                    renderSectionHeader={({ section }) => (
                        <View style={{ flexDirection: "row", alignItems: "center", paddingHorizontal: 22, paddingTop: 10, paddingBottom: 4, backgroundColor: colors.background }}>
                            {section.title === "Favorites" && <Star size={13} color={AMBER} fill={AMBER} style={{ marginRight: 5 }} />}
                            <Text style={{ fontSize: 13, fontWeight: "700", color: colors.foreground, opacity: 0.7 }}>{section.title}</Text>
                        </View>
                    )}
                    ListEmptyComponent={
                        <View style={{ alignItems: "center", paddingTop: 40 }}>
                            <Text style={{ fontSize: 14, color: colors.foreground, opacity: 0.6 }}>No trainee matches your search.</Text>
                        </View>
                    }
                    stickySectionHeadersEnabled={false}
                    keyboardShouldPersistTaps="handled"
                />
            </View>
        </Modal>
    )
}

export default React.memo(PresetPicker)
