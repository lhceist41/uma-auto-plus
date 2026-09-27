/**
 * The Settings page's ready/reset snackbar: what it says and what colour it shows, kept as one pure
 * decision so the colour can never drift from the message actually displayed (it used to key off
 * `readyStatus` alone, so a reset confirmation could render red if a reset happened to also flip
 * `readyStatus` from false to true).
 */
export type SnackbarKind = "ready" | "not-ready" | "reset"

/** The message shown for each kind. */
export function snackbarMessageFor(kind: SnackbarKind): string {
    switch (kind) {
        case "ready":
            return "Scenario selected."
        case "not-ready":
            return "No scenario selected. Choose one on Home."
        case "reset":
            return "Settings reset to defaults."
    }
}

/** The background colour for each kind: red only for the not-ready warning. */
export function snackbarBackgroundColor(kind: SnackbarKind): "green" | "red" {
    return kind === "not-ready" ? "red" : "green"
}
