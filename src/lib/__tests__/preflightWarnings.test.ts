import type { Settings } from "../../context/BotStateContext"
import { collectPreflightWarnings, describeTimeout, readPreflightProbes, shouldShowPreflight, SHORT_SCREEN_TIMEOUT_MS, type PreflightProbes } from "../preflightWarnings"

/**
 * A setup that raises nothing: every probe healthy, Discord on, a single run. Built from just the
 * fields the checks read (the shipped defaults for the rest), because importing BotStateContext
 * pulls React Native into a plain Jest suite.
 */
const quiet = (): Settings =>
    ({
        runQueue: {
            enableRunQueue: false,
            totalRuns: 5,
            reuseLastLaunchSetup: true,
            enableTpRestoreWithItems: false,
            enableEventBoost: false,
            enableSparkReroll: false,
        },
        discord: { enableDiscordNotifications: true },
    }) as unknown as Settings
const healthy: PreflightProbes = {
    secureSettingsGranted: true,
    screenTimeout: { timeoutMs: 60 * 60 * 1000, stayOnWhilePluggedIn: false },
    notificationsEnabled: true,
}
const unknown: PreflightProbes = { secureSettingsGranted: null, screenTimeout: null, notificationsEnabled: null }
const ids = (s: Settings, p: PreflightProbes) => collectPreflightWarnings(s, p).map((w) => w.id)

describe("collectPreflightWarnings", () => {
    it("raises nothing for a healthy single-run setup", () => {
        expect(collectPreflightWarnings(quiet(), healthy)).toEqual([])
    })

    describe("TP restore off in a multi-run queue", () => {
        const queue = (runs: number, restore: boolean) => {
            const s = quiet()
            s.runQueue.enableRunQueue = true
            s.runQueue.totalRuns = runs
            s.runQueue.enableTpRestoreWithItems = restore
            return s
        }

        it("warns when a queue of more than one run has restore off", () => {
            const warning = collectPreflightWarnings(queue(3, false), healthy).find((w) => w.id === "tp-restore-off")
            expect(warning?.kind).toBe("warning")
            expect(warning?.text).toContain("the queue stops before the next career; finished runs are kept")
        })

        it("stays quiet with restore on, a single-run queue, or the queue off", () => {
            expect(ids(queue(3, true), healthy)).not.toContain("tp-restore-off")
            expect(ids(queue(1, false), healthy)).not.toContain("tp-restore-off")
            const off = queue(3, false)
            off.runQueue.enableRunQueue = false
            expect(ids(off, healthy)).not.toContain("tp-restore-off")
        })

        it("names Event Boost and spark reroll costs only when those are on", () => {
            const plain = collectPreflightWarnings(queue(3, false), healthy).find((w) => w.id === "tp-restore-off")!
            expect(plain.text).not.toContain("Event Boost")
            expect(plain.text).not.toContain("spark reroll")
            const both = queue(3, false)
            both.runQueue.enableEventBoost = true
            both.runQueue.enableSparkReroll = true
            const full = collectPreflightWarnings(both, healthy).find((w) => w.id === "tp-restore-off")!
            expect(full.text).toContain("double with Event Boost on")
            expect(full.text).toContain("each spark reroll costs 30 TP")
        })
    })

    describe("Reuse Last Launch Setup off", () => {
        it("warns when the queue is on and reuse is off", () => {
            const s = quiet()
            s.runQueue.enableRunQueue = true
            s.runQueue.reuseLastLaunchSetup = false
            expect(ids(s, healthy)).toContain("launch-setup-reuse-off")
        })

        it("stays quiet with reuse on or the queue off", () => {
            const on = quiet()
            on.runQueue.enableRunQueue = true
            on.runQueue.reuseLastLaunchSetup = true
            expect(ids(on, healthy)).not.toContain("launch-setup-reuse-off")
            const single = quiet()
            single.runQueue.reuseLastLaunchSetup = false
            expect(ids(single, healthy)).not.toContain("launch-setup-reuse-off")
        })
    })

    describe("accessibility self-repair permission", () => {
        it("warns when the grant is missing and points at an existing Troubleshooting section", () => {
            const warning = collectPreflightWarnings(quiet(), { ...healthy, secureSettingsGranted: false }).find((w) => w.id === "accessibility-repair-unavailable")
            expect(warning?.kind).toBe("warning")
            expect(warning?.text).toContain('"The emulator killed the Accessibility service (MuMu)"')
        })

        it("stays quiet when granted", () => {
            expect(ids(quiet(), healthy)).not.toContain("accessibility-repair-unavailable")
        })
    })

    describe("screen timeout", () => {
        const withTimeout = (timeoutMs: number, stayOnWhilePluggedIn = false): PreflightProbes => ({ ...healthy, screenTimeout: { timeoutMs, stayOnWhilePluggedIn } })

        it("warns below 30 minutes and states the real timeout", () => {
            const warning = collectPreflightWarnings(quiet(), withTimeout(2 * 60 * 1000)).find((w) => w.id === "short-screen-timeout")
            expect(warning?.text).toContain("Your screen turns off after 2 minutes.")
        })

        it("stays quiet at 30 minutes or more, or when the device stays on while charging", () => {
            expect(ids(quiet(), withTimeout(SHORT_SCREEN_TIMEOUT_MS))).not.toContain("short-screen-timeout")
            expect(ids(quiet(), withTimeout(2 * 60 * 1000, true))).not.toContain("short-screen-timeout")
        })

        it("treats a non-positive timeout as unknown", () => {
            expect(ids(quiet(), withTimeout(0))).not.toContain("short-screen-timeout")
        })
    })

    describe("notification permission", () => {
        it("warns when notifications are off", () => {
            expect(ids(quiet(), { ...healthy, notificationsEnabled: false })).toContain("notifications-off")
        })

        it("stays quiet when they are on", () => {
            expect(ids(quiet(), healthy)).not.toContain("notifications-off")
        })
    })

    describe("Discord tip", () => {
        it("adds a tip, never a warning, when Discord alerts are off", () => {
            const s = quiet()
            s.discord.enableDiscordNotifications = false
            const tip = collectPreflightWarnings(s, healthy).find((w) => w.id === "discord-off")
            expect(tip?.kind).toBe("tip")
        })

        it("stays quiet when Discord alerts are on", () => {
            expect(ids(quiet(), healthy)).not.toContain("discord-off")
        })
    })

    it("shows nothing for a device check whose probe failed", () => {
        expect(collectPreflightWarnings(quiet(), unknown)).toEqual([])
    })
})

describe("shouldShowPreflight", () => {
    it("opens the dialog for a warning but never for a tip alone", () => {
        const s = quiet()
        s.discord.enableDiscordNotifications = false
        expect(shouldShowPreflight(collectPreflightWarnings(s, healthy))).toBe(false)
        expect(shouldShowPreflight(collectPreflightWarnings(s, { ...healthy, notificationsEnabled: false }))).toBe(true)
    })
})

describe("describeTimeout", () => {
    it("uses minutes, singular where it should, and seconds under a minute", () => {
        expect(describeTimeout(60_000)).toBe("1 minute")
        expect(describeTimeout(10 * 60_000)).toBe("10 minutes")
        expect(describeTimeout(15_000)).toBe("15 seconds")
    })
})

describe("readPreflightProbes", () => {
    it("passes healthy probe values through", async () => {
        const probes = await readPreflightProbes({
            hasSecureSettingsGrant: async () => true,
            getScreenTimeout: async () => ({ timeoutMs: 120_000, stayOnWhilePluggedIn: false }),
            areNotificationsEnabled: async () => false,
        })
        expect(probes).toEqual({ secureSettingsGranted: true, screenTimeout: { timeoutMs: 120_000, stayOnWhilePluggedIn: false }, notificationsEnabled: false })
    })

    it("turns a rejected, missing, or malformed probe into unknown", async () => {
        const probes = await readPreflightProbes({
            hasSecureSettingsGrant: async () => {
                throw new Error("probe failed")
            },
            getScreenTimeout: async () => ({ timeoutMs: "soon" }) as never,
        })
        expect(probes).toEqual(unknown)
        expect(await readPreflightProbes(undefined)).toEqual(unknown)
    })
})
