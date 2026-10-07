import { useEffect, useState } from "react"
import { Keyboard, Platform } from "react-native"

// Edge-to-edge disables adjustResize only on API 30+ (setDecorFitsSystemWindows), where React Native also
// reports the keyboard height without the navigation bar inset. Older Android still resizes the window itself.
const WINDOW_DOES_NOT_RESIZE = Platform.OS === "android" && Number(Platform.Version) >= 30

export const useKeyboardHeight = () => {
    const [height, setHeight] = useState(0)

    useEffect(() => {
        if (!WINDOW_DOES_NOT_RESIZE) return
        const show = Keyboard.addListener("keyboardDidShow", (event) => setHeight(event.endCoordinates.height))
        const hide = Keyboard.addListener("keyboardDidHide", () => setHeight(0))
        return () => {
            show.remove()
            hide.remove()
        }
    }, [])

    return height
}
