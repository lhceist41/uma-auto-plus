import fs from "fs"
import path from "path"
import type { AlertButton } from "react-native"
import { confirmStopAfterCareer, sendStopAfterCareer, STOP_AFTER_CAREER_FAILED, type ShowAlert } from "../stopAfterCareer"

/** A fake Kotlin side holding the flag, like `StartModule.stopAfterCareerRequested`. */
function fakeNative(fail = false) {
    const calls: boolean[] = []
    let flag = false
    return {
        calls,
        flag: () => flag,
        setStopAfterCareer: async (requested: boolean) => {
            calls.push(requested)
            if (fail) throw new Error("bridge down")
            flag = requested
            return flag
        },
    }
}

/** Records the alert and lets a test press one of its buttons by label. */
function fakeAlert() {
    const shown: { title: string; buttons: AlertButton[] }[] = []
    const showAlert: ShowAlert = (title, _message, buttons) => {
        shown.push({ title, buttons: buttons ?? [] })
    }
    const press = (label: string) => shown[shown.length - 1].buttons.find((b) => b.text === label)?.onPress?.()
    return { shown, showAlert, press }
}

describe("Stop after this career confirm", () => {
    it("confirming sends the request and the state reads it back as requested", async () => {
        const native = fakeNative()
        const alert = fakeAlert()
        let shownState: boolean | null = null
        confirmStopAfterCareer(alert.showAlert, () => {
            void sendStopAfterCareer(native, true).then((now) => (shownState = now))
        })
        expect(alert.shown[0].title).toBe("Stop after this career?")
        expect(native.calls).toEqual([])
        alert.press("Stop after this career")
        await new Promise((r) => setImmediate(r))
        expect(native.calls).toEqual([true])
        expect(native.flag()).toBe(true)
        expect(shownState).toBe(true)
    })

    it("keep going sends nothing", () => {
        const native = fakeNative()
        const alert = fakeAlert()
        confirmStopAfterCareer(alert.showAlert, () => void sendStopAfterCareer(native, true))
        alert.press("Keep going")
        expect(native.calls).toEqual([])
        expect(alert.shown[0].buttons.map((b) => b.text)).toEqual(["Keep going", "Stop after this career"])
    })

    it("shows the state Kotlin answers, not the one asked for", async () => {
        const native = { setStopAfterCareer: async () => false }
        await expect(sendStopAfterCareer(native, true)).resolves.toBe(false)
    })

    it("cancelling the request resolves false", async () => {
        const native = fakeNative()
        await sendStopAfterCareer(native, true)
        await expect(sendStopAfterCareer(native, false)).resolves.toBe(false)
        expect(native.calls).toEqual([true, false])
    })

    it("a failed call rejects with the player message", async () => {
        await expect(sendStopAfterCareer(fakeNative(true), true)).rejects.toThrow(STOP_AFTER_CAREER_FAILED)
    })

    it("an answer that is not a yes or no is a failure, never assumed", async () => {
        await expect(sendStopAfterCareer({ setStopAfterCareer: async () => undefined }, true)).rejects.toThrow(STOP_AFTER_CAREER_FAILED)
    })
})

describe("Home wiring", () => {
    const home = fs.readFileSync(path.join(process.cwd(), "src/pages/Home/index.tsx"), "utf8").replace(/\r\n/g, "\n")

    it("the button confirms through the system alert and requests on confirm", () => {
        expect(home).toContain("confirmStopAfterCareer(Alert.alert, () => requestStopAfterCareer(true))")
    })

    it("the request goes through sendStopAfterCareer and a failure is shown to the player", () => {
        const start = home.indexOf("const requestStopAfterCareer = (requested: boolean) => {")
        expect(start).toBeGreaterThanOrEqual(0)
        const body = home.slice(start, start + 300)
        expect(body).toContain("sendStopAfterCareer(StartModule, requested)")
        expect(body).toContain(".then(setStopAfterCareer)")
        expect(body).toContain('.catch((error: Error) => showSnackbar(error.message, "error"))')
    })

    it("the in-app dialog whose confirm did not arrive is gone", () => {
        expect(home).not.toContain("showStopAfterCareerDialog")
        expect(home).not.toContain("<AlertDialogTitle>Stop after this career?</AlertDialogTitle>")
    })
})
