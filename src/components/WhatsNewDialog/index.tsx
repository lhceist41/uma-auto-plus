import { Linking, View } from "react-native"
import { AlertDialog, AlertDialogAction, AlertDialogContent, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "../ui/alert-dialog"
import { Button } from "../ui/button"
import { Text } from "../ui/text"
import { useTheme } from "../../context/ThemeContext"
import type { WhatsNewContent } from "../../lib/whatsNew"

interface WhatsNewDialogProps {
    /** What to show; null keeps the dialog closed. */
    content: WhatsNewContent | null
    /** Called when the player presses OK or dismisses the dialog. */
    onClose: () => void
}

/** The one-time dialog listing a release's highlights, with a link to its full release notes. */
const WhatsNewDialog = ({ content, onClose }: WhatsNewDialogProps) => {
    const { colors } = useTheme()

    return (
        <AlertDialog open={content !== null} onOpenChange={(open) => !open && onClose()}>
            <AlertDialogContent onDismiss={onClose}>
                <AlertDialogHeader>
                    <AlertDialogTitle>{content?.title ?? ""}</AlertDialogTitle>
                </AlertDialogHeader>
                <View accessibilityRole="list" style={{ gap: 8 }}>
                    {(content?.highlights ?? []).map((line) => (
                        <View key={line} accessible accessibilityLabel={line} style={{ flexDirection: "row", gap: 8 }}>
                            <Text importantForAccessibility="no" style={{ fontSize: 13, color: colors.foreground }}>
                                {"•"}
                            </Text>
                            <Text style={{ flex: 1, fontSize: 13, lineHeight: 18, color: colors.foreground }}>{line}</Text>
                        </View>
                    ))}
                </View>
                <AlertDialogFooter>
                    <Button variant="outline" accessibilityLabel="Open the full release notes in your browser" onPress={() => content && Linking.openURL(content.releaseNotesUrl).catch(() => {})}>
                        <Text>Release notes</Text>
                    </Button>
                    <AlertDialogAction accessibilityLabel="Close the what's new dialog" onPress={onClose}>
                        <Text>OK</Text>
                    </AlertDialogAction>
                </AlertDialogFooter>
            </AlertDialogContent>
        </AlertDialog>
    )
}

export default WhatsNewDialog
