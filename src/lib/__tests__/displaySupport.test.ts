import { unsupportedDisplayReason } from "../displaySupport"

describe("unsupportedDisplayReason", () => {
    it("accepts the documented emulator setup, 1080x1920 at 240 DPI", () => {
        expect(unsupportedDisplayReason(1080, 1920, 240)).toBeNull()
    })

    it("flags 1080x1920 at any other density", () => {
        for (const dpi of [480, 320, 160, 450]) {
            expect(unsupportedDisplayReason(1080, 1920, dpi)).toBe("an unsupported display density")
        }
    })

    it("accepts a taller 1080-wide phone at its own density", () => {
        expect(unsupportedDisplayReason(1080, 2316, 450)).toBeNull()
        expect(unsupportedDisplayReason(1080, 2340, 450)).toBeNull()
    })

    it("flags a screen that is not 1080 wide or is shorter than 1920 as a size problem", () => {
        expect(unsupportedDisplayReason(1440, 3120, 560)).toBe("an unsupported screen size")
        expect(unsupportedDisplayReason(720, 1280, 240)).toBe("an unsupported screen size")
        expect(unsupportedDisplayReason(1080, 1800, 240)).toBe("an unsupported screen size")
    })
})
