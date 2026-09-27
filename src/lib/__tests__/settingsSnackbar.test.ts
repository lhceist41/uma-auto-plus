import { snackbarBackgroundColor, snackbarMessageFor } from "../settingsSnackbar"

describe("snackbarMessageFor", () => {
    it("returns the exact text for each kind", () => {
        expect(snackbarMessageFor("ready")).toBe("Scenario selected.")
        expect(snackbarMessageFor("not-ready")).toBe("No scenario selected. Choose one on Home.")
        expect(snackbarMessageFor("reset")).toBe("Settings reset to defaults.")
    })
})

describe("snackbarBackgroundColor", () => {
    it("is red only for the not-ready message, so a reset confirmation is never shown in red", () => {
        expect(snackbarBackgroundColor("not-ready")).toBe("red")
        expect(snackbarBackgroundColor("ready")).toBe("green")
        expect(snackbarBackgroundColor("reset")).toBe("green")
    })
})
