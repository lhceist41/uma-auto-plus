import { Linking } from "react-native"
import { AlertDialog, AlertDialogAction, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "../ui/alert-dialog"
import { Button } from "../ui/button"
import { Text } from "../ui/text"
import { logWarningWithTimestamp } from "../../lib/logger"
import { STAR_ASK_BODY, STAR_ASK_TITLE, STAR_REPO_URL } from "../../lib/starAsk"

interface StarAskDialogProps {
    open: boolean
    /** Called when either button, or the dialog's own dismissal, ends the ask. */
    onAnswer: () => void
}

/** The one-time, dismissible ask to star the project on GitHub. */
const StarAskDialog = ({ open, onAnswer }: StarAskDialogProps) => (
    <AlertDialog open={open} onOpenChange={(next) => !next && onAnswer()}>
        <AlertDialogContent onDismiss={onAnswer}>
            <AlertDialogHeader>
                <AlertDialogTitle>{STAR_ASK_TITLE}</AlertDialogTitle>
                <AlertDialogDescription>{STAR_ASK_BODY}</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
                <Button variant="outline" accessibilityLabel="Close without starring" onPress={onAnswer}>
                    <Text>Not now</Text>
                </Button>
                <AlertDialogAction
                    accessibilityLabel="Open UMA Auto+ on GitHub in your browser"
                    onPress={() => {
                        Linking.openURL(STAR_REPO_URL).catch((e) => logWarningWithTimestamp(`[StarAsk] Could not open the GitHub page: ${e}`))
                        onAnswer()
                    }}
                >
                    <Text>Star on GitHub</Text>
                </AlertDialogAction>
            </AlertDialogFooter>
        </AlertDialogContent>
    </AlertDialog>
)

export default StarAskDialog
