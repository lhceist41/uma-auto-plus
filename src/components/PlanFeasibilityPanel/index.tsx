import { useMemo } from "react"
import { View, Text, StyleSheet } from "react-native"
import { useTheme } from "../../context/ThemeContext"
import type { PlanFeasibilityPreview, ConsecutiveCheck } from "../../lib/raceLab/planFeasibility"

interface PlanFeasibilityPanelProps {
    /** The result of checking the racing plan the page is currently showing. */
    preview: PlanFeasibilityPreview
}

/** What the consecutive-race part of the check did, in the player's terms. */
function consecutiveNote(check: ConsecutiveCheck): string {
    switch (check.mode) {
        case "trackblazerLimit":
            return `Races in a row are checked against the Trackblazer limit of ${check.limit}.`
        case "gameWarning":
            return "Races in a row are flagged because the bot backs out when the game warns about consecutive races."
        default:
            return "Races in a row are not flagged because your settings tell the bot to race through the game's consecutive-race warning."
    }
}

/**
 * Read-only summary of what the racing-plan checks found. It never edits the plan and never says a plan
 * will work: it reports only what the checks can show before a career starts.
 */
const PlanFeasibilityPanel = ({ preview }: PlanFeasibilityPanelProps) => {
    const { colors } = useTheme()

    const styles = useMemo(
        () =>
            StyleSheet.create({
                container: {
                    backgroundColor: colors.card,
                    borderWidth: 1,
                    borderColor: colors.border,
                    borderRadius: 8,
                    padding: 12,
                    marginBottom: 16,
                },
                title: {
                    fontSize: 16,
                    fontWeight: "600",
                    color: colors.foreground,
                    marginBottom: 6,
                },
                summary: {
                    fontSize: 14,
                    color: colors.foreground,
                    lineHeight: 20,
                },
                note: {
                    fontSize: 13,
                    color: colors.mutedForeground,
                    lineHeight: 18,
                    marginTop: 10,
                },
                finding: {
                    borderLeftWidth: 4,
                    borderRadius: 6,
                    padding: 10,
                    marginTop: 8,
                },
                errorFinding: {
                    backgroundColor: colors.background,
                    borderLeftColor: colors.destructive,
                },
                warningFinding: {
                    backgroundColor: colors.warningBg,
                    borderLeftColor: colors.warningBorder,
                },
                errorText: {
                    fontSize: 14,
                    color: colors.foreground,
                    lineHeight: 20,
                },
                warningText: {
                    fontSize: 14,
                    color: colors.warningText,
                    lineHeight: 20,
                },
            }),
        [colors]
    )

    let summary: string
    if (preview.kind === "empty") summary = "No planned races to check."
    else if (preview.kind === "unavailable") summary = "The bot's race data could not be verified on this device, so the plan was not checked."
    else if (preview.kind === "unreadable") summary = "This plan could not be checked because its saved data is incomplete."
    else if (preview.errorCount === 0 && preview.warningCount === 0) summary = `Checked ${preview.raceCount} planned races. No issues found by the current plan checks.`
    else summary = `Checked ${preview.raceCount} planned races. Found ${preview.errorCount} problem${preview.errorCount === 1 ? "" : "s"} and ${preview.warningCount} warning${preview.warningCount === 1 ? "" : "s"}.`

    const findings = preview.kind === "unreadable" || preview.kind === "checked" ? preview.findings : []
    const hiddenCount = preview.kind === "checked" ? preview.hiddenCount : 0

    return (
        <View style={styles.container}>
            <Text style={styles.title}>Plan check</Text>
            <Text style={styles.summary}>{summary}</Text>

            {findings.map((finding) => (
                <View key={finding.id} style={[styles.finding, finding.severity === "error" ? styles.errorFinding : styles.warningFinding]}>
                    <Text style={finding.severity === "error" ? styles.errorText : styles.warningText}>{finding.message}</Text>
                </View>
            ))}

            {hiddenCount > 0 && <Text style={styles.note}>{`${hiddenCount} more not listed here.`}</Text>}

            {preview.kind === "checked" && (
                <Text style={styles.note}>
                    {`These checks cover race names, turns, and races planned on the same turn. ${consecutiveNote(preview.consecutiveCheck)} They cannot promise a race will be entered: aptitude, fans, and energy are decided during the career.`}
                </Text>
            )}
        </View>
    )
}

export default PlanFeasibilityPanel
