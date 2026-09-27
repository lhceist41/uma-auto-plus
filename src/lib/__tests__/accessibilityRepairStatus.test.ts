import fs from "fs"
import path from "path"
import { accessibilityRepairStatus, secureSettingsGrantCommand } from "../accessibilityRepairStatus"

const PACKAGE = "com.lhceist41.uma_auto_plus"

describe("accessibilityRepairStatus", () => {
    it("granted: says the bot can switch the service back on, with no command and no promise to fix dead taps", () => {
        const status = accessibilityRepairStatus(true, PACKAGE)
        expect(status.state).toBe("granted")
        expect(status.title).toBe("Accessibility self-repair: on")
        expect(status.command).toBeNull()
        expect(status.text).toContain("restart MuMu or the device")
    })

    it("missing: gives the exact grant command for this app's package and what happens without it", () => {
        const status = accessibilityRepairStatus(false, PACKAGE)
        expect(status.state).toBe("missing")
        expect(status.title).toBe("Accessibility self-repair: off")
        expect(status.command).toBe("adb shell pm grant com.lhceist41.uma_auto_plus android.permission.WRITE_SECURE_SETTINGS")
        expect(status.text).toContain("the queue stops")
        expect(status.text).toContain("Run this once")
    })

    it("missing without a readable package name: names the permission instead of inventing a command", () => {
        for (const name of [null, undefined, "", "not a package", "com.example; rm -rf"]) {
            const status = accessibilityRepairStatus(false, name)
            expect(status.state).toBe("missing")
            expect(status.command).toBeNull()
            expect(status.text).toContain("WRITE_SECURE_SETTINGS")
            expect(status.text).not.toContain("Run this once")
        }
    })

    it("probe failed: stays unknown, with no command and no claim either way", () => {
        for (const value of [null, undefined]) {
            const status = accessibilityRepairStatus(value, PACKAGE)
            expect(status.state).toBe("unknown")
            expect(status.title).toBe("Accessibility self-repair: unknown")
            expect(status.command).toBeNull()
            expect(status.text).not.toMatch(/can switch|cannot switch/)
        }
    })

    it("a value from a newer or broken probe fails closed to unknown", () => {
        expect(accessibilityRepairStatus("yes" as unknown as boolean, PACKAGE).state).toBe("unknown")
        expect(accessibilityRepairStatus(1 as unknown as boolean, PACKAGE).state).toBe("unknown")
    })
})

describe("Home wiring", () => {
    const home = fs.readFileSync(path.join(process.cwd(), "src/pages/Home/index.tsx"), "utf8").replace(/\r\n/g, "\n")

    it("reads the grant on mount and on every return to the app", () => {
        const mount = home.indexOf("refreshLastSession()\n        refreshSecureSettingsGrant()\n\n        return () => {")
        expect(mount).toBeGreaterThan(-1)
        expect(home).toContain('if (nextState === "active") {\n                refreshInterruptedQueue()\n                refreshLastSession()\n                refreshSecureSettingsGrant()')
        expect(home).toContain(
            "readPreflightProbes(StartModule)\n            .then((probes) => setSecureSettingsGrant(probes.secureSettingsGranted))\n            .catch(() => setSecureSettingsGrant(null))"
        )
    })

    it("shows the helper's words for this app's package, and nothing before the first read", () => {
        expect(home).toContain("secureSettingsGrant === undefined ? null : accessibilityRepairStatus(secureSettingsGrant, Application.applicationId)")
        expect(home).toContain("{repairStatus.title}")
        expect(home).toContain("{repairStatus.text}")
        expect(home).toContain("{repairStatus.command && (")
    })
})

describe("secureSettingsGrantCommand", () => {
    it("only builds a command for a plain package name", () => {
        expect(secureSettingsGrantCommand("com.lhceist41.uma_auto_plus.debug")).toBe("adb shell pm grant com.lhceist41.uma_auto_plus.debug android.permission.WRITE_SECURE_SETTINGS")
        expect(secureSettingsGrantCommand("single")).toBeNull()
        expect(secureSettingsGrantCommand("com.x y")).toBeNull()
    })
})
