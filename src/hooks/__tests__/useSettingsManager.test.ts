import * as fs from "fs"
import * as path from "path"
import { deepMerge, convertSettingsToBatch, applyMigrations, formatValue, formatChange } from "../../lib/settingsUtils"

// ===========================================================================
// deepMerge
// ===========================================================================

describe("deepMerge", () => {
    it("shallow merge: source overrides target", () => {
        const target = { a: 1, b: 2 }
        const source = { b: 3 }
        expect(deepMerge(target, source)).toEqual({ a: 1, b: 3 })
    })

    it("nested merge: preserves nested target keys not in source", () => {
        const target = { nested: { a: 1, b: 2 } }
        const source = { nested: { a: 10 } }
        expect(deepMerge(target, source as any)).toEqual({ nested: { a: 10, b: 2 } })
    })

    it("arrays are replaced entirely, not merged", () => {
        const target = { arr: [1, 2, 3] }
        const source = { arr: [4, 5] }
        expect(deepMerge(target, source)).toEqual({ arr: [4, 5] })
    })

    it("null in source overrides target", () => {
        const target = { a: { b: 1 } }
        const source = { a: null }
        // null is not an object, so it should override
        expect(deepMerge(target, source as any)).toEqual({ a: null })
    })

    it("undefined in source is skipped", () => {
        const target = { a: 1, b: 2 }
        const source = { a: undefined, b: 3 }
        expect(deepMerge(target, source)).toEqual({ a: 1, b: 3 })
    })

    it("empty source returns copy of target", () => {
        const target = { a: 1, b: { c: 2 } }
        const result = deepMerge(target, {})
        expect(result).toEqual({ a: 1, b: { c: 2 } })
        // Should be a new object (not same reference)
        expect(result).not.toBe(target)
    })

    it("merges 3+ levels deep", () => {
        const target = { l1: { l2: { l3: { a: 1, b: 2 } } } }
        const source = { l1: { l2: { l3: { a: 10 } } } }
        expect(deepMerge(target, source as any)).toEqual({ l1: { l2: { l3: { a: 10, b: 2 } } } })
    })

    it("adds new keys from source", () => {
        const target = { a: 1 }
        const source = { b: 2 }
        expect(deepMerge(target, source as any)).toEqual({ a: 1, b: 2 })
    })

    it("creates nested structure when target lacks the key", () => {
        const target = {} as any
        const source = { nested: { a: 1, b: 2 } }
        expect(deepMerge(target, source)).toEqual({ nested: { a: 1, b: 2 } })
    })
})

// ===========================================================================
// convertSettingsToBatch
// ===========================================================================

describe("convertSettingsToBatch", () => {
    it("converts single category with two keys to batch entries", () => {
        const settings = { general: { scenario: "URA", enablePopupCheck: true } } as any
        const batch = convertSettingsToBatch(settings)
        expect(batch).toHaveLength(2)
        expect(batch).toContainEqual({ category: "general", key: "scenario", value: "URA" })
        expect(batch).toContainEqual({ category: "general", key: "enablePopupCheck", value: true })
    })

    it("converts multiple categories", () => {
        const settings = {
            general: { scenario: "URA" },
            training: { maximumFailureChance: 30 },
        } as any
        const batch = convertSettingsToBatch(settings)
        expect(batch).toHaveLength(2)
        expect(batch).toContainEqual({ category: "general", key: "scenario", value: "URA" })
        expect(batch).toContainEqual({ category: "training", key: "maximumFailureChance", value: 30 })
    })

    it("handles values of different types", () => {
        const settings = {
            test: {
                str: "hello",
                num: 42,
                bool: false,
                arr: [1, 2],
                obj: { nested: true },
            },
        } as any
        const batch = convertSettingsToBatch(settings)
        expect(batch).toHaveLength(5)
    })

    it("skips rotation-snapshot categories (rot*) so they can never be re-serialized and compound", () => {
        const settings = {
            general: { scenario: "URA" },
            rot0_general: { scenario: "URA" },
            rot0_rot0_trainingEvent: { characterEventData: "junk" },
        } as any
        const batch = convertSettingsToBatch(settings)
        expect(batch).toEqual([{ category: "general", key: "scenario", value: "URA" }])
    })
})

// ===========================================================================
// applyMigrations
// ===========================================================================

describe("applyMigrations", () => {
    it("migrates ocrConfidence from ocr to trainingEvent", () => {
        const settings = {
            ocr: { ocrConfidence: 85 },
            trainingEvent: { ocrConfidence: 90 },
        } as any

        const { settings: migrated, anyMigrated } = applyMigrations(settings)
        expect(anyMigrated).toBe(true)
        expect(migrated.trainingEvent.ocrConfidence).toBe(85)
        expect((migrated as any).ocr?.ocrConfidence).toBeUndefined()
    })

    it("migrates enableAutomaticOCRRetry from ocr to trainingEvent", () => {
        const settings = {
            ocr: { enableAutomaticOCRRetry: false },
            trainingEvent: { enableAutomaticOCRRetry: true },
        } as any

        const { settings: migrated } = applyMigrations(settings)
        expect(migrated.trainingEvent.enableAutomaticOCRRetry).toBe(false)
    })

    it("migrates enableHideOCRComparisonResults from debug to trainingEvent", () => {
        const settings = {
            debug: { enableHideOCRComparisonResults: false },
            trainingEvent: { enableHideOCRComparisonResults: true },
        } as any

        const { settings: migrated } = applyMigrations(settings)
        expect(migrated.trainingEvent.enableHideOCRComparisonResults).toBe(false)
    })

    it("migrates ocrThreshold from ocr to debug", () => {
        const settings = {
            ocr: { ocrThreshold: 0.8 },
            debug: { ocrThreshold: 0.7 },
        } as any

        const { settings: migrated } = applyMigrations(settings)
        expect(migrated.debug.ocrThreshold).toBe(0.8)
    })

    it("deletes empty ocr object after all fields migrated", () => {
        const settings = {
            ocr: { ocrConfidence: 85 },
            trainingEvent: { ocrConfidence: 90 },
            debug: {},
        } as any

        const { settings: migrated } = applyMigrations(settings)
        expect((migrated as any).ocr).toBeUndefined()
    })

    it("migrates stopAtDate string to stopAtDates array", () => {
        const settings = {
            general: { stopAtDate: "Senior January Early", stopAtDates: [] },
        } as any

        const { settings: migrated, anyMigrated } = applyMigrations(settings)
        expect(anyMigrated).toBe(true)
        expect(migrated.general.stopAtDates).toEqual(["Senior January Early"])
        expect((migrated.general as any).stopAtDate).toBeUndefined()
    })

    it("returns anyMigrated=false when no migration needed", () => {
        const settings = {
            general: { stopAtDates: ["Senior January Early"] },
            trainingEvent: { ocrConfidence: 90 },
            debug: { ocrThreshold: 0.7 },
        } as any

        const { anyMigrated } = applyMigrations(settings)
        expect(anyMigrated).toBe(false)
    })

    it("is idempotent: running twice produces same result", () => {
        const settings = {
            ocr: { ocrConfidence: 85 },
            trainingEvent: { ocrConfidence: 90 },
            debug: {},
            general: { stopAtDate: "Senior January Early", stopAtDates: [] },
        } as any

        const { settings: first } = applyMigrations(settings)
        const { settings: second, anyMigrated } = applyMigrations(first)
        expect(anyMigrated).toBe(false)
        expect(second).toEqual(first)
    })

    it("drops the removed Trackblazer race-retry settings without asking for a save", () => {
        const settings = {
            general: { stopAtDates: [] },
            scenarioOverrides: { trackblazerMaxRetriesPerRace: 1, trackblazerRetryRacesBeforeFinalGrades: ["G1"], trackblazerEnergyThreshold: 40 },
        } as any

        const { settings: migrated, anyMigrated } = applyMigrations(settings)
        expect(migrated.scenarioOverrides).toEqual({ trackblazerEnergyThreshold: 40 })
        expect(anyMigrated).toBe(false)
    })
})

// ===========================================================================
// formatValue / formatChange (import and profile preview text)
// ===========================================================================

describe("import preview text", () => {
    const entry = (inGameName: string, scenario = "Trackblazer", extra: Record<string, unknown> = {}) => ({ inGameName, presetKey: "k", scenario, ...extra })

    it("shows a trainee rotation as names with scenario, never [object Object]", () => {
        const text = formatValue([entry("[Wedding] Mayano Top Gun"), entry("Bourbon", "URA Finale")])
        expect(text).toBe("[Wedding] Mayano Top Gun (Trackblazer), Bourbon (URA Finale)")
        expect(text).not.toContain("[object Object]")
    })

    it("does not shorten long lists", () => {
        const text = formatValue(Array.from({ length: 10 }, (_, i) => entry(`T${i}`)))
        expect(text).toBe(Array.from({ length: 10 }, (_, i) => `T${i} (Trackblazer)`).join(", "))
    })

    it("a 7-item list changed only in the 7th shows different old and new text", () => {
        const { oldText, newText } = formatChange([29, 35, 43, 47, 52, 55, 58], [29, 35, 43, 47, 52, 55, 60])
        expect(oldText).toBe("29, 35, 43, 47, 52, 55, 58")
        expect(newText).toBe("29, 35, 43, 47, 52, 55, 60")
    })

    it("a 10-item list changed only in the 8th shows different text", () => {
        const base = Array.from({ length: 10 }, (_, i) => `item${i}`)
        const changed = base.map((v, i) => (i === 7 ? "other" : v))
        const { oldText, newText } = formatChange(base, changed)
        expect(oldText).not.toBe(newText)
    })

    it("a rotation entry changed only in scenario shows different text", () => {
        const { oldText, newText } = formatChange([entry("[Wedding] Mayano Top Gun", "URA Finale")], [entry("[Wedding] Mayano Top Gun", "Trackblazer")])
        expect(oldText).toBe("[Wedding] Mayano Top Gun (URA Finale)")
        expect(newText).toBe("[Wedding] Mayano Top Gun (Trackblazer)")
    })

    it("a rotation entry changed only in preset shows different text naming the preset", () => {
        const { oldText, newText } = formatChange([entry("Bourbon", "Trackblazer", { presetKey: "preset_a" })], [entry("Bourbon", "Trackblazer", { presetKey: "preset_b" })])
        expect(oldText).toBe("Bourbon (Trackblazer, preset preset_a)")
        expect(newText).toBe("Bourbon (Trackblazer, preset preset_b)")
    })

    it("names a real preset by character and outfit, not by its raw key", () => {
        const { newText } = formatChange([entry("Biwa", "Trackblazer", { presetKey: "Biwa Hayahide" })], [entry("Biwa", "Trackblazer", { presetKey: "Biwa Hayahide (Rouge Caroler)" })])
        expect(newText).toBe("Biwa (Trackblazer, preset Biwa Hayahide · [Rouge Caroler])")
    })

    it("a rotation entry changed only in excluded outfits shows different text", () => {
        const { oldText, newText } = formatChange([entry("Bourbon", "Trackblazer", { excludeOutfits: [] })], [entry("Bourbon", "Trackblazer", { excludeOutfits: ["Summer"] })])
        expect(oldText).not.toBe(newText)
        expect(newText).toContain("excluding Summer")
    })

    it("keeps the short form when it already differs", () => {
        const { oldText, newText } = formatChange([entry("A")], [entry("B")])
        expect(oldText).toBe("A (Trackblazer)")
        expect(newText).toBe("B (Trackblazer)")
    })

    it("an entry with an empty name shows neutral text, not field names", () => {
        const text = formatValue([
            { inGameName: "", presetKey: "", scenario: "URA Finale" },
            { inGameName: "", presetKey: "", scenario: "" },
        ])
        expect(text).toBe("(empty URA Finale entry), (empty entry)")
        expect(text).not.toContain("inGameName")
    })

    it("null and undefined list entries render empty, as the plain join did", () => {
        expect(formatValue([1, null, undefined, 2])).toBe("1, , , 2")
    })

    it("falls back to JSON for list objects without a name, and for changes text cannot tell apart", () => {
        expect(formatValue([{ a: 1 }])).toBe('{"a":1}')
        const { oldText, newText } = formatChange([1], ["1"])
        expect(oldText).not.toBe(newText)
    })

    it("renders plain values as before", () => {
        expect(formatValue(null)).toBe("null")
        expect(formatValue(true)).toBe("Enabled")
        expect(formatValue(false)).toBe("Disabled")
        expect(formatValue(10)).toBe("10")
        expect(formatValue("fast")).toBe("fast")
        expect(formatValue([])).toBe("[]")
        expect(formatValue(["a", "b"])).toBe("a, b")
        expect(formatValue({ x: 1 })).toBe('{"x":1}')
    })

    it("the profile comparison uses the shared formatter, so a rotation never shows [object Object]", () => {
        const source = fs.readFileSync(path.join(__dirname, "../../components/ProfileComparison/index.tsx"), "utf8")
        expect(source).toContain('import { formatChange } from "../../lib/settingsUtils"')
        expect(source).toContain("formatChange(current, profile)")
        expect(source).not.toMatch(/const formatValue|\.join\(/)
        const { oldText, newText } = formatChange([entry("A")], [entry("A"), entry("B")])
        expect(`${oldText}|${newText}`).not.toContain("[object Object]")
        expect(oldText).not.toBe(newText)
    })
})
