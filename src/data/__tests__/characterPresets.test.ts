import races from "../races.json"
import objectives from "../character_objectives.json"
import outfitData from "../character_outfits.json"
import skills from "../skills.json"
import scenarios from "../scenarios.json"
import buildBudget from "../build_budget_data.json"
import gcFanRuntime from "../../../android/app/src/main/assets/gc_fan_runtime.json"
import { avoidAdvisoryFor, characterPresets, trainerAdvisories } from "../characterPresets"
import { presetCharacter, presetOutfit, presetValidation } from "../presetMeta"
import { SKILL_SPEND_OBJECTIVES } from "../../lib/adaptiveSkillPolicy"
import { deriveExcludeOutfits, deriveInGameName } from "../../lib/rotationSnapshots"

// Green golds a preset must not plan: each chains from a white in the same card's own kit, so while locked
// the planner counts it as an upgrade of that white row and dead-taps it.
const GREEN_CHAIN_POTENTIAL_GOLDS = [202331, 201561, 202441]

describe("avoidAdvisoryFor", () => {
    it("flags Haru Urara in Trackblazer as an avoid (turf-aptitude mismatch)", () => {
        const avoid = avoidAdvisoryFor("Haru Urara", "Trackblazer")
        expect(avoid).not.toBeNull()
        expect(avoid?.scenario).toBe("Trackblazer")
        expect(avoid?.reason.length).toBeGreaterThan(0)
    })

    it("flags Haru Urara in Unity Cup as an avoid too", () => {
        expect(avoidAdvisoryFor("Haru Urara", "Unity Cup")).not.toBeNull()
    })

    it("does not flag Haru Urara in URA Finale (her recommended scenario)", () => {
        expect(avoidAdvisoryFor("Haru Urara", "URA Finale")).toBeNull()
    })

    it("returns null for a trainee that has no advisory entry", () => {
        expect(avoidAdvisoryFor("Nonexistent Trainee", "Trackblazer")).toBeNull()
    })
})

describe("trainerAdvisories data integrity", () => {
    it("every avoid entry carries a scenario and a non-empty reason", () => {
        for (const advisory of Object.values(trainerAdvisories)) {
            for (const avoid of advisory.avoid ?? []) {
                expect(avoid.scenario.length).toBeGreaterThan(0)
                expect(avoid.reason.trim().length).toBeGreaterThan(0)
            }
        }
    })
})

describe("Super Creek (Blue Farm) preset", () => {
    const find = (name: string, scenario: string) => characterPresets.find((p) => p.name === name && p.scenario === scenario)
    const base = () => find("Super Creek", "Unity Cup")
    const blueFarm = () => find("Super Creek (Blue Farm)", "Unity Cup")

    it("leaves the original Super Creek Unity Cup preset unchanged (Speed+Stamina focus)", () => {
        const b = base()
        expect(b).toBeDefined()
        expect(b!.settings.training?.focusOnSparkStatTarget).toEqual(["Speed", "Stamina"])
    })

    it("exists as a distinct preset under Unity Cup", () => {
        const bf = blueFarm()
        expect(bf).toBeDefined()
        expect(bf!.scenario).toBe("Unity Cup")
        expect(bf).not.toBe(base())
    })

    it("declares its canonical trainee identity as plain 'Super Creek' (display name is a variant label)", () => {
        expect(blueFarm()!.traineeName).toBe("Super Creek")
        expect(base()!.traineeName).toBeUndefined() // the competitive preset selects by its own name
    })

    it("focuses the spark rescue on all five stats, each exactly once", () => {
        const focus = blueFarm()!.settings.training!.focusOnSparkStatTarget!
        expect(focus).toEqual(["Speed", "Stamina", "Power", "Guts", "Wit"])
        expect(new Set(focus).size).toBe(5)
    })

    it("matches the source Creek Unity build in every setting except the spark target and the sparks objective", () => {
        // The two deliberate farming differences: the five-stat spark rescue and, since 2B-1,
        // the planned-only sparks objective. Everything else must stay a faithful clone.
        expect((blueFarm()!.settings.skills as { skillSpendObjective?: string }).skillSpendObjective).toBe("sparks")
        expect((base()!.settings.skills as { skillSpendObjective?: string }).skillSpendObjective).toBeUndefined()
        const strip = (s: unknown) => {
            const clone = JSON.parse(JSON.stringify(s))
            delete clone.training.focusOnSparkStatTarget
            delete clone.skills.skillSpendObjective
            return clone
        }
        expect(strip(blueFarm()!.settings)).toEqual(strip(base()!.settings))
    })

    it("is independent of the source preset — a deep clone (what apply/rotation snapshots do) cannot mutate it", () => {
        const bf = blueFarm()!
        const b = base()!
        expect(bf.settings.training!.focusOnSparkStatTarget).not.toBe(b.settings.training!.focusOnSparkStatTarget)
        const applied = JSON.parse(JSON.stringify(bf))
        applied.settings.training.focusOnSparkStatTarget.push("Speed")
        expect(bf.settings.training!.focusOnSparkStatTarget).toHaveLength(5)
        expect(b.settings.training!.focusOnSparkStatTarget).toEqual(["Speed", "Stamina"])
    })

    it("is not marked recommended (stays unproven until its own careers are analyzed)", () => {
        // The name suffix is the label; deliberately no recommended-badge entry -> no "proven" hint.
        expect(trainerAdvisories["Super Creek (Blue Farm)"]?.recommended ?? []).toEqual([])
    })
})

describe("Copano Rickey presets", () => {
    const trio = characterPresets.filter((p) => p.name === "Copano Rickey")
    const ura = trio.find((p) => p.scenario === "URA Finale")!
    const plannedRaces: { raceName: string; date: string; turnNumber: number }[] = JSON.parse(ura.settings.racing!.racingPlan as string)

    it("ships one preset per scenario, including the derived Grand Concert twin", () => {
        expect(trio).toHaveLength(4)
        expect(trio.map((p) => p.scenario).sort()).toEqual(["Grand Concert", "Trackblazer", "URA Finale", "Unity Cup"])
    })

    it("keeps every preset key unique across the whole roster", () => {
        const keys = characterPresets.map((p) => `${p.name}|${p.scenario}`)
        expect(new Set(keys).size).toBe(keys.length)
    })

    it("selects by plain trainee name (Eightfold☆Fortune is her base card, not a variant label)", () => {
        for (const p of trio) expect(p.traineeName).toBeUndefined()
    })

    it("resolves her base outfit to Eightfold☆Fortune", () => {
        expect(presetOutfit("Copano Rickey")).toBe("Eightfold☆Fortune")
        expect(presetCharacter("Copano Rickey")).toBe("Copano Rickey")
    })

    it("is live-validated in URA, Unity Cup, and Grand Concert - the ledger holds full-arc completions for each", () => {
        // URA (6 completions incl. the Kashiwa Kinen sash win), Unity Cup (2), and Grand
        // Concert (3, two on the 2026-07-26 queue). One Copano preset per scenario, so the
        // trainee-name ledger maps unambiguously.
        for (const scenario of ["URA Finale", "Unity Cup", "Grand Concert"]) {
            expect(presetValidation("Copano Rickey", scenario)).toBe("validated")
        }
        expect(presetValidation("Copano Rickey", "Trackblazer")).toBe("research")
    })

    it("carries the Dirt / Mile / Pace Chaser identity in all three presets", () => {
        for (const p of trio) {
            expect(p.settings.skills!.preferredTrackSurface).toBe("dirt")
            expect(p.settings.skills!.preferredTrackDistance).toBe("mile")
            expect(p.settings.skills!.preferredRunningStyle).toBe("pace_chaser")
            expect(p.settings.racing!.preferredTerrain).toBe("Dirt")
            expect(p.settings.training!.preferredDistanceOverride).toBe("Mile")
            expect(p.settings.general!.enablePopupCheck).toBe(false)
        }
    })

    it("prioritizes Speed then Power, per the Kashiwa checkpoint", () => {
        expect(ura.settings.training!.statPrioritization).toEqual(["Speed", "Power", "Stamina", "Wit", "Guts"])
        expect(ura.settings.training!.focusOnSparkStatTarget).toEqual(["Speed", "Power"])
    })

    it("uses the production high-water threshold and the required plan strategies", () => {
        for (const p of trio) {
            expect(p.settings.skills!.skillPointCheck).toBe(1000)
            expect(p.settings.skills!.plans!.skillPointCheck!.strategy).toBe("optimize_skills")
            expect(p.settings.skills!.plans!.preFinals!.strategy).toBe("optimize_skills")
            expect(p.settings.skills!.plans!.careerComplete!.strategy).toBe("optimize_knapsack")
        }
    })

    it("never buys negative skills on a body built to win Kashiwa", () => {
        for (const p of trio) {
            expect(p.settings.skills!.plans!.skillPointCheck!.enableBuyNegativeSkills).toBe(false)
            expect(p.settings.skills!.plans!.careerComplete!.enableBuyNegativeSkills).toBe(false)
        }
    })

    it("declares the racing-plan trio explicitly in every preset (no plan leaks across trainees)", () => {
        for (const p of trio) {
            expect(p.settings.racing!.enableRacingPlan).toBeDefined()
            expect(p.settings.racing!.enableMandatoryRacingPlan).toBeDefined()
            expect(p.settings.racing!.racingPlan).toBeDefined()
        }
    })

    it("runs a curated mandatory agenda on URA and smart racing elsewhere", () => {
        expect(ura.settings.racing!.enableRacingPlan).toBe(true)
        expect(ura.settings.racing!.enableMandatoryRacingPlan).toBe(true)
        for (const p of trio.filter((x) => x.scenario !== "URA Finale")) {
            expect(p.settings.racing!.enableRacingPlan).toBe(false)
            expect(p.settings.racing!.racingPlan).toBe("")
        }
    })

    it("keeps the URA agenda inside the goal-density rule (fills the empty half, ~4-10 entries)", () => {
        // Her chain is empty before t31 and dense from t47, so the plan covers Junior/Classic only.
        expect(plannedRaces.length).toBeGreaterThanOrEqual(4)
        expect(plannedRaces.length).toBeLessThanOrEqual(10)
        expect(Math.max(...plannedRaces.map((r) => r.turnNumber))).toBeLessThan(47)
    })

    it("never pins Kashiwa itself, and never pins a mandatory objective turn", () => {
        const goalTurns = [31, 47, 52, 57, 60, 67, 69, 72]
        for (const r of plannedRaces) {
            expect(r.raceName).not.toBe("Kashiwa Kinen")
            expect(goalTurns).not.toContain(r.turnNumber)
        }
    })

    it("leaves Kashiwa preparation clear - no pin on t51, and nothing adjacent to a goal turn", () => {
        // The generator offered t51 Kawasaki Kinen and t71 Champions Cup; both abut a goal and t51
        // would race her five turns before the sash race. Dropping them is the point of this preset.
        const goalTurns = [31, 47, 52, 57, 60, 67, 69, 72]
        for (const r of plannedRaces) {
            expect(r.turnNumber).not.toBe(51)
            expect(goalTurns).not.toContain(r.turnNumber - 1)
            expect(goalTurns).not.toContain(r.turnNumber + 1)
        }
    })

    it("is advisory-flagged: URA recommended, Unity Cup and Trackblazer cautioned for Turf F", () => {
        expect(trainerAdvisories["Copano Rickey"]?.recommended).toEqual(["URA Finale"])
        expect(avoidAdvisoryFor("Copano Rickey", "Trackblazer")?.reason).toMatch(/Turf=F/)
        expect(avoidAdvisoryFor("Copano Rickey", "Unity Cup")?.reason).toMatch(/Turf=F/)
        expect(avoidAdvisoryFor("Copano Rickey", "URA Finale")).toBeNull()
    })

    it("does not encode the unproven six-green claim anywhere in her presets", () => {
        // The repo's own data shows her unique gated only on `phase_laterhalf_random==1`.
        const blob = JSON.stringify(trio)
        expect(blob).not.toMatch(/six green/i)
        expect(trainerAdvisories["Copano Rickey"]).toBeDefined()
    })

    it("never plans Strong Steps, her Potential Lv5 gold that chains from a white in her own kit", () => {
        // The manifest files her Lv3-5 tree under `skills_awakening`, which looks star-gated but is not; the planner skips locked skills.
        const potentialGated: Record<number, string> = {
            202331: "Strong Steps (Potential Lv5)",
        }
        for (const p of trio) {
            for (const planKey of ["skillPointCheck", "preFinals", "careerComplete"] as const) {
                const ids = String((p.settings.skills!.plans as any)[planKey].plan)
                    .split(",")
                    .filter(Boolean)
                    .map(Number)
                for (const [id, label] of Object.entries(potentialGated)) {
                    expect(ids).not.toContain(Number(id))
                    if (ids.includes(Number(id))) throw new Error(`${p.scenario}/${planKey} plans ${label}`)
                }
            }
        }
    })

    it("keeps Solid Steps' Potential Lv2 sibling reachable - the plan is trimmed, not emptied", () => {
        // Guards the trim: dropping three ids must not leave a stub plan.
        for (const p of trio) {
            const ids = String((p.settings.skills!.plans as any).careerComplete.plan).split(",").filter(Boolean)
            expect(ids.length).toBeGreaterThanOrEqual(12)
        }
    })

    it("plans only skills that exist in the skill database", () => {
        const known = new Set<number>((Array.isArray(skills) ? skills : Object.values(skills)).map((s: any) => s.id))
        for (const p of trio) {
            for (const planKey of ["skillPointCheck", "preFinals", "careerComplete"] as const) {
                const ids = String((p.settings.skills!.plans as any)[planKey].plan)
                    .split(",")
                    .filter(Boolean)
                    .map(Number)
                expect(ids.length).toBeGreaterThan(0)
                for (const id of ids) expect(known.has(id)).toBe(true)
            }
        }
    })

    it("plans only races that exist in the race database, on the turn the plan claims", () => {
        for (const r of plannedRaces) {
            const entry = (races as Record<string, any>)[`${r.raceName} (${r.date})`]
            expect(entry).toBeDefined()
            expect(entry.turnNumber).toBe(r.turnNumber)
            // Every pinned race must be one she can actually win: dirt only, Turf=F body.
            expect(entry.terrain).toBe("Dirt")
        }
    })
})

describe("Copano Rickey game data (NAR dirt patch)", () => {
    const KASHIWA = "Kashiwa Kinen (Senior Class May, First Half)"

    it("has Kashiwa Kinen in the race database", () => {
        expect((races as Record<string, any>)[KASHIWA]).toBeDefined()
    })

    it("describes Kashiwa exactly: t57, G1, Dirt, Mile, 1600m, Funabashi", () => {
        const k = (races as Record<string, any>)[KASHIWA]
        expect(k.turnNumber).toBe(57)
        expect(k.grade).toBe("G1")
        expect(k.terrain).toBe("Dirt")
        expect(k.distanceType).toBe("Mile")
        expect(k.distanceMeters).toBe(1600)
        expect(k.raceTrack).toBe("Funabashi")
        expect(k.date).toBe("Senior Class May, First Half")
    })

    it("carries her whole objective chain, Kashiwa included", () => {
        const chain = (objectives as Record<string, any>)["Copano Rickey"]
        expect(chain).toBeDefined()
        expect(chain.mandatoryRaces.map((m: any) => m.turn)).toEqual([31, 47, 52, 57, 60, 67, 69, 72])
        const kashiwa = chain.mandatoryRaces.find((m: any) => m.turn === 57)
        expect(kashiwa.options[0].raceName).toBe("Kashiwa Kinen")
        expect(kashiwa.options[0].grade).toBe("G1")
        expect(kashiwa.options[0].surface).toBe("Dirt")
    })

    it("resolves every one of her seven late-chain Dirt objectives to a real race entry", () => {
        const chain = (objectives as Record<string, any>)["Copano Rickey"]
        const late = chain.mandatoryRaces.filter((m: any) => m.turn >= 47)
        expect(late).toHaveLength(7)
        for (const m of late) {
            for (const o of m.options) {
                expect(o.surface).toBe("Dirt")
                // The race must exist somewhere on the calendar on that objective's turn.
                const match = Object.values(races as Record<string, any>).some((r) => r.name === o.raceName && r.turnNumber === m.turn)
                expect(match).toBe(true)
            }
        }
    })

    it("keeps her unique in the skill database with no green-count scaling term", () => {
        const all = Array.isArray(skills) ? skills : Object.values(skills)
        const unique = (all as any[]).find((s) => s.id === 100981)
        expect(unique).toBeDefined()
        expect(unique.name_en).toBe("Luck Runs My Way")
        // Guards the advisory's claim: nothing here counts greens.
        expect(unique.condition).toBe("phase_laterhalf_random==1")
    })
})
describe("Grass Wonder (Saintly Jade Cleric) presets", () => {
    const trio = characterPresets.filter((p) => p.name === "Grass Wonder (Saintly Jade Cleric)")
    const sjc = (scenario: string) => trio.find((p) => p.scenario === scenario)!
    const base = (scenario: string) => characterPresets.find((p) => p.name === "Grass Wonder" && p.scenario === scenario)!
    const planIds = (p: (typeof characterPresets)[number], planKey: "skillPointCheck" | "preFinals" | "careerComplete") =>
        String((p.settings.skills!.plans as any)[planKey].plan)
            .split(",")
            .filter(Boolean)
            .map(Number)

    it("ships one preset per scenario - the pipeline trio plus the derived Grand Concert twin", () => {
        expect(trio).toHaveLength(4)
        expect(trio.map((p) => p.scenario).sort()).toEqual(["Grand Concert", "Trackblazer", "URA Finale", "Unity Cup"])
    })

    it("selects by its real-outfit name - no traineeName indirection on either Grass Wonder row", () => {
        for (const p of trio) expect(p.traineeName).toBeUndefined()
        for (const scenario of ["URA Finale", "Unity Cup", "Trackblazer"]) expect(base(scenario).traineeName).toBeUndefined()
    })

    it("renders as a second Grass Wonder picker row with the Saintly Jade Cleric outfit", () => {
        expect(presetCharacter("Grass Wonder (Saintly Jade Cleric)")).toBe("Grass Wonder")
        expect(presetOutfit("Grass Wonder (Saintly Jade Cleric)")).toBe("Saintly Jade Cleric")
        expect(presetCharacter("Grass Wonder")).toBe("Grass Wonder")
        expect(presetOutfit("Grass Wonder")).toBe("Stone-Piercing Blue")
    })

    it("stays research-graded in every scenario until a live career says otherwise", () => {
        for (const p of trio) expect(presetValidation(p.name, p.scenario)).toBe("research")
    })

    it("carries the Turf / Long / Late Surger identity in all three presets", () => {
        for (const p of trio) {
            expect(p.settings.skills!.preferredTrackSurface).toBe("turf")
            expect(p.settings.skills!.preferredTrackDistance).toBe("long")
            expect(p.settings.skills!.preferredRunningStyle).toBe("late_surger")
            expect(p.settings.racing!.preferredTerrain).toBe("Turf")
            expect(p.settings.training!.preferredDistanceOverride).toBe("Long")
            expect(p.settings.general!.enablePopupCheck).toBe(false)
        }
    })

    it("promotes Stamina over Power for the +15% Stamina growth, in priorities and spark focus", () => {
        for (const p of trio) {
            expect(p.settings.training!.statPrioritization).toEqual(["Speed", "Stamina", "Power", "Wit", "Guts"])
            expect(p.settings.training!.focusOnSparkStatTarget).toEqual(["Speed", "Stamina"])
        }
    })

    it("matches its base-scenario preset in everything except the declared kit diffs", () => {
        // Same aptitude grid and same in-game objective chain as the base card (verified
        // 2026-07-17), so everything except the kit-driven diffs must stay a faithful clone:
        // skill plans (her own recovery chain), stat priorities / spark focus (growth), and
        // the Unity Cup safe_completion objective.
        for (const scenario of ["URA Finale", "Unity Cup", "Trackblazer"]) {
            const strip = (s: unknown) => {
                const clone = JSON.parse(JSON.stringify(s))
                delete clone.skills.plans
                delete clone.skills.skillSpendObjective
                delete clone.training.statPrioritization
                delete clone.training.focusOnSparkStatTarget
                return clone
            }
            expect(strip(sjc(scenario).settings)).toEqual(strip(base(scenario).settings))
        }
    })

    it("plans her own Long recovery chain in every scenario (Deep Breaths -> Cooldown)", () => {
        for (const p of trio) {
            for (const planKey of ["skillPointCheck", "preFinals", "careerComplete"] as const) {
                const ids = planIds(p, planKey)
                expect(ids).toContain(200741)
                expect(ids).toContain(200742)
            }
        }
    })

    it("adds her Late recovery A Small Breather to the URA and Unity Cup plans", () => {
        for (const scenario of ["URA Finale", "Unity Cup"]) {
            expect(planIds(sjc(scenario), "careerComplete")).toContain(201422)
        }
    })

    it("never plans the base card's Be Still line (no hint discount on this outfit)", () => {
        for (const p of trio) {
            const ids = planIds(p, "careerComplete")
            expect(ids).not.toContain(201692)
            expect(ids).not.toContain(201691)
        }
    })

    it("plans only skills that exist in the skill database", () => {
        const known = new Set<number>((Array.isArray(skills) ? skills : Object.values(skills)).map((s: any) => s.id))
        for (const p of trio) {
            for (const planKey of ["skillPointCheck", "preFinals", "careerComplete"] as const) {
                const ids = planIds(p, planKey)
                expect(ids.length).toBeGreaterThanOrEqual(12)
                for (const id of ids) expect(known.has(id)).toBe(true)
            }
        }
    })

    it("uses the required plan strategies (greedy at checkpoints, knapsack at career end)", () => {
        for (const p of trio) {
            expect(p.settings.skills!.plans!.skillPointCheck!.strategy).toBe("optimize_skills")
            expect(p.settings.skills!.plans!.preFinals!.strategy).toBe("optimize_skills")
            expect(p.settings.skills!.plans!.careerComplete!.strategy).toBe("optimize_knapsack")
        }
    })

    it("is advisory-covered but carries no recommended badge until her own careers complete", () => {
        expect(trainerAdvisories["Grass Wonder (Saintly Jade Cleric)"]).toBeDefined()
        expect(trainerAdvisories["Grass Wonder (Saintly Jade Cleric)"].recommended).toEqual([])
        expect(trainerAdvisories["Grass Wonder (Saintly Jade Cleric)"].avoid ?? []).toEqual([])
    })

    it("keeps every preset key unique across the whole roster", () => {
        const keys = characterPresets.map((p) => `${p.name}|${p.scenario}`)
        expect(new Set(keys).size).toBe(keys.length)
    })
})

describe("Wonder Acute and Nakayama Festa presets", () => {
    const planKeys = ["skillPointCheck", "preFinals", "careerComplete"] as const
    const planIds = (p: (typeof characterPresets)[number], planKey: (typeof planKeys)[number]) =>
        String((p.settings.skills!.plans as any)[planKey].plan)
            .split(",")
            .filter(Boolean)
            .map(Number)
    const knownSkills = new Set<number>((Array.isArray(skills) ? skills : Object.values(skills)).map((s: any) => s.id))
    const goalTurns = (name: string): number[] => (objectives as Record<string, any>)[name].mandatoryRaces.map((m: any) => m.turn)

    // Identity and kit read from the game's master data (card_rarity_data, available_skill_set); `gated` is the Potential Lv2-5 tree.
    const trainees = [
        {
            name: "Wonder Acute",
            outfit: "Butterfly Sting",
            surface: "dirt",
            distance: "mile",
            style: "pace_chaser",
            terrain: "Dirt",
            override: "Mile",
            goals: [37, 42, 48, 51, 57, 60, 69, 71, 72],
            ownSkills: [202302, 201532, 201072],
            gated: [201032, 201071, 202332, 202301],
            gcSpeed: 1600,
        },
        {
            name: "Nakayama Festa",
            outfit: "Desperate Measures",
            surface: "turf",
            distance: "medium",
            style: "late_surger",
            terrain: "Turf",
            override: "Medium",
            goals: [25, 31, 34, 44, 47, 60, 70],
            ownSkills: [202442, 201702],
            gated: [201382, 202031, 201102, 202441],
            gcSpeed: 1400,
        },
    ]

    describe.each(trainees)("$name", (t) => {
        const all = characterPresets.filter((p) => p.name === t.name)
        const pipeline = all.filter((p) => p.scenario !== "Grand Concert")

        it("ships one preset per scenario, including the derived Grand Concert twin", () => {
            expect(all.map((p) => p.scenario).sort()).toEqual(["Grand Concert", "Trackblazer", "URA Finale", "Unity Cup"])
        })

        it("selects by the bare trainee name: a single owned outfit needs no exclusions", () => {
            for (const p of all) expect(p.traineeName).toBeUndefined()
            expect(deriveInGameName(t.name)).toBe(t.name)
            expect(deriveExcludeOutfits(t.name)).toEqual([])
        })

        it("renders as one picker row with her base outfit, research-graded everywhere", () => {
            expect(presetCharacter(t.name)).toBe(t.name)
            expect(presetOutfit(t.name)).toBe(t.outfit)
            for (const p of all) expect(presetValidation(p.name, p.scenario)).toBe("research")
        })

        it("still has the goal chain the presets were built against", () => {
            expect(goalTurns(t.name)).toEqual(t.goals)
        })

        it("carries one surface / distance / style identity in every scenario", () => {
            for (const p of all) {
                expect(p.settings.skills!.preferredTrackSurface).toBe(t.surface)
                expect(p.settings.skills!.preferredTrackDistance).toBe(t.distance)
                expect(p.settings.skills!.preferredRunningStyle).toBe(t.style)
                expect(p.settings.racing!.preferredTerrain).toBe(t.terrain)
                expect(p.settings.training!.preferredDistanceOverride).toBe(t.override)
                expect(p.settings.general!.enablePopupCheck).toBe(false)
                expect(p.settings.general!.scenario).toBe(p.scenario)
            }
        })

        it("declares the racing-plan trio explicitly in every preset", () => {
            for (const p of all) {
                expect(p.settings.racing!.enableRacingPlan).toBeDefined()
                expect(p.settings.racing!.enableMandatoryRacingPlan).toBeDefined()
                expect(p.settings.racing!.racingPlan).toBeDefined()
            }
        })

        it("plans only known skills with the required strategies, and never a green-chain hold from its Potential tree", () => {
            for (const p of pipeline) {
                expect(p.settings.skills!.plans!.skillPointCheck!.strategy).toBe("optimize_skills")
                expect(p.settings.skills!.plans!.preFinals!.strategy).toBe("optimize_skills")
                expect(p.settings.skills!.plans!.careerComplete!.strategy).toBe("optimize_knapsack")
                for (const planKey of planKeys) {
                    const ids = planIds(p, planKey)
                    expect(ids.length).toBeGreaterThanOrEqual(12)
                    for (const id of ids) expect(knownSkills.has(id)).toBe(true)
                    for (const id of t.gated.filter((g) => GREEN_CHAIN_POTENTIAL_GOLDS.includes(g))) expect(ids).not.toContain(id)
                    for (const id of t.ownSkills) expect(ids).toContain(id)
                }
            }
        })

        it("keeps the whole default excluded-item list when Trackblazer adds to it (arrays replace)", () => {
            for (const p of pipeline.filter((p) => p.scenario === "Trackblazer")) {
                const items = p.settings.scenarioOverrides!.trackblazerExcludedItems as string[]
                for (const item of ["Energy Drink MAX", "Energy Drink MAX EX", "Yummy Cat Food", "Coaching Megaphone"]) expect(items).toContain(item)
            }
        })

        it("raises the Grand Concert Speed target to the policy value for her distance", () => {
            const gc = all.find((p) => p.scenario === "Grand Concert")!
            const key = t.override === "Mile" ? "trainingMileStatTarget_speedStatTarget" : "trainingMediumStatTarget_speedStatTarget"
            expect((gc.settings.trainingStatTarget as any)[key]).toBe(t.gcSpeed)
        })

        it("is advisory-covered with no recommended badge until her own careers complete", () => {
            expect(trainerAdvisories[t.name]).toBeDefined()
            expect(trainerAdvisories[t.name].recommended).toEqual([])
            expect(avoidAdvisoryFor(t.name, "URA Finale")).toBeNull()
        })
    })

    describe("Wonder Acute URA agenda", () => {
        const ura = characterPresets.find((p) => p.name === "Wonder Acute" && p.scenario === "URA Finale")!
        const planned: { raceName: string; date: string; turnNumber: number }[] = JSON.parse(ura.settings.racing!.racingPlan as string)
        const entry = (r: { raceName: string; date: string }) => (races as Record<string, any>)[`${r.raceName} (${r.date})`]

        it("runs a curated mandatory agenda on URA and smart racing elsewhere", () => {
            expect(ura.settings.racing!.enableRacingPlan).toBe(true)
            expect(ura.settings.racing!.enableMandatoryRacingPlan).toBe(true)
            for (const p of characterPresets.filter((x) => x.name === "Wonder Acute" && x.scenario !== "URA Finale")) {
                expect(p.settings.racing!.enableRacingPlan).toBe(false)
                expect(p.settings.racing!.racingPlan).toBe("")
            }
        })

        it("plans only real dirt races, on the turn each entry claims", () => {
            for (const r of planned) {
                expect(entry(r)).toBeDefined()
                expect(entry(r).turnNumber).toBe(r.turnNumber)
                expect(entry(r).terrain).toBe("Dirt")
            }
        })

        const fanGoals: { turn: number; targetFans: number }[] = (objectives as Record<string, any>)["Wonder Acute"].fanGoals ?? []

        it("carries her route fan goal (5000 fans by t30) in the objectives data and the GC runtime asset", () => {
            // master.mdb single_mode_route_race 829. Grand Concert reads it from the runtime asset;
            // without it her first GC fan requirement would silently fall to the t37 entry gate.
            expect(fanGoals).toEqual([{ turn: 30, targetFans: 5000, scenarioGroupId: 100, appliesToScenarioIds: [1, 2, 3, 4] }])
            expect((gcFanRuntime as any).characters["Wonder Acute"].fanGoals).toEqual([{ turn: 30, targetFans: 5000 }])
        })

        it("never plans on or next to a goal turn, the fan deadline included", () => {
            const blocked = [12, ...fanGoals.map((g) => g.turn), ...goalTurns("Wonder Acute")]
            for (const r of planned) {
                expect(blocked).not.toContain(r.turnNumber)
                expect(blocked).not.toContain(r.turnNumber - 1)
                expect(blocked).not.toContain(r.turnNumber + 1)
            }
        })

        it("can clear each fan goal from its own pre-deadline wins", () => {
            // Mandatory mode races planned turns only, and the fan emergency cannot add a race here:
            // its B+ aptitude filter leaves no dirt race before t30 beyond the ones already planned.
            expect(fanGoals.length).toBeGreaterThan(0)
            for (const goal of fanGoals) {
                const winnerFans = planned.filter((r) => r.turnNumber < goal.turn).reduce((sum, r) => sum + entry(r).fans, 0)
                expect(winnerFans).toBeGreaterThanOrEqual(goal.targetFans)
            }
        })

        it("stays inside the goal-density rule and leaves Senior to the goal chain", () => {
            expect(planned.length).toBeGreaterThanOrEqual(4)
            expect(planned.length).toBeLessThanOrEqual(10)
            expect(Math.max(...planned.map((r) => r.turnNumber))).toBeLessThan(49)
        })

        it("cautions Unity Cup and Trackblazer for Turf=G", () => {
            expect(avoidAdvisoryFor("Wonder Acute", "Unity Cup")?.reason).toMatch(/Turf=G/)
            expect(avoidAdvisoryFor("Wonder Acute", "Trackblazer")?.reason).toMatch(/Turf=G/)
        })
    })

    describe("Nakayama Festa racing and recovery", () => {
        const festa = characterPresets.filter((p) => p.name === "Nakayama Festa" && p.scenario !== "Grand Concert")

        it("uses smart racing everywhere, with fan farming on URA only", () => {
            for (const p of festa) {
                expect(p.settings.racing!.enableRacingPlan).toBe(false)
                expect(p.settings.racing!.enableMandatoryRacingPlan).toBe(false)
                expect(p.settings.racing!.racingPlan).toBe("")
                expect(p.settings.racing!.enableFarmingFans).toBe(p.scenario === "URA Finale")
            }
        })

        it("plans recovery her kit lacks (Be Still, A Small Breather) and skips Risky Business", () => {
            for (const p of festa) {
                for (const planKey of planKeys) {
                    const ids = planIds(p, planKey)
                    expect(ids).toContain(201692)
                    expect(ids).toContain(201422)
                    expect(ids).not.toContain(202032)
                }
            }
        })

        it("carries no avoid advisory in any scenario", () => {
            expect(trainerAdvisories["Nakayama Festa"].avoid ?? []).toEqual([])
        })
    })
})

describe("Aston Machan, Kawakami Princess, Seeking the Pearl, T.M. Opera O (O Sole Suo!), Yamanin Zephyr and Yukino Bijin presets", () => {
    const planKeys = ["skillPointCheck", "preFinals", "careerComplete"] as const
    const planIds = (p: (typeof characterPresets)[number], planKey: (typeof planKeys)[number]) =>
        String((p.settings.skills!.plans as any)[planKey].plan)
            .split(",")
            .filter(Boolean)
            .map(Number)
    const skillList = (Array.isArray(skills) ? skills : Object.values(skills)) as { id: number; condition?: string }[]
    const skillById = new Map(skillList.map((s) => [s.id, s]))
    const STYLE_CODE: Record<string, string> = { front_runner: "1", pace_chaser: "2", late_surger: "3", end_closer: "4" }
    const objectivesFor = (key: string) => (objectives as Record<string, any>)[key]

    // Identity and kit read from master data (card_data, available_skill_set). `ownSkills` is the Potential Lv1 kit;
    // `gated` is the Lv2-5 tree plus the upgrades above a gated skill.
    const trainees = [
        {
            name: "Aston Machan",
            character: "Aston Machan",
            objectivesKey: "Aston Machan",
            outfit: "Flare",
            inGameName: "Aston Machan",
            distance: "sprint",
            style: "front_runner",
            override: "Sprint",
            goals: [21, 23, 29, 31, 42, 54, 66],
            ownSkills: [200162, 200532, 202042],
            gated: [200972, 200531, 201601, 202041, 200971],
            gcSpeed: 1600,
            trackblazerAvoid: /Medium and Long/,
        },
        {
            name: "Kawakami Princess",
            character: "Kawakami Princess",
            objectivesKey: "Kawakami Princess",
            outfit: "Princess of Pink",
            inGameName: "Kawakami Princess",
            distance: "medium",
            style: "late_surger",
            override: "Medium",
            goals: [34, 44, 45, 53, 57, 60, 69],
            ownSkills: [200492, 200612, 201072],
            gated: [201382, 200491, 200132, 200611, 201381, 200131],
            gcSpeed: 1400,
            trackblazerAvoid: null,
        },
        {
            name: "Seeking the Pearl",
            character: "Seeking the Pearl",
            objectivesKey: "Seeking the Pearl",
            outfit: "Rocket☆Star",
            inGameName: "Seeking the Pearl",
            distance: "mile",
            style: "pace_chaser",
            override: "Mile",
            goals: [21, 23, 31, 33, 41, 46, 54, 59, 66, 70],
            ownSkills: [200152, 200962, 201072],
            gated: [202042, 200963, 201902, 201071, 202041, 201901],
            gcSpeed: 1600,
            trackblazerAvoid: /Medium=E and Long=G/,
        },
        {
            name: "T.M. Opera O (O Sole Suo!)",
            character: "T.M. Opera O",
            objectivesKey: "TM Opera O",
            outfit: "O Sole Suo!",
            inGameName: "[O Sole Suo!] T.M. Opera O",
            distance: "medium",
            style: "pace_chaser",
            override: "Medium",
            goals: [31, 34, 48, 56, 60, 70, 72],
            ownSkills: [200142, 200582, 200722],
            gated: [200362, 200581, 200562, 200721, 200361, 200561],
            gcSpeed: 1400,
            trackblazerAvoid: null,
        },
        {
            name: "Yamanin Zephyr",
            character: "Yamanin Zephyr",
            objectivesKey: "Yamanin Zephyr",
            outfit: "Fluttertail Spirit",
            inGameName: "Yamanin Zephyr",
            distance: "mile",
            style: "pace_chaser",
            override: "Mile",
            goals: [31, 34, 42, 46, 59, 66, 68],
            ownSkills: [200302, 201052, 202412],
            gated: [201042, 201051, 200342, 202411, 201041, 200341],
            gcSpeed: 1600,
            trackblazerAvoid: null,
        },
        {
            name: "Yukino Bijin",
            character: "Yukino Bijin",
            objectivesKey: "Yukino Bijin",
            outfit: "Darl'n Snowflake",
            inGameName: "Yukino Bijin",
            distance: "medium",
            style: "pace_chaser",
            override: "Medium",
            goals: [23, 31, 34, 38, 44, 54, 57, 69, 70],
            ownSkills: [200492, 201102, 201342],
            gated: [200242, 200491, 201322, 201341, 200241, 201321],
            gcSpeed: 1400,
            trackblazerAvoid: null,
        },
    ]

    describe.each(trainees)("$name", (t) => {
        const all = characterPresets.filter((p) => p.name === t.name)
        const pipeline = all.filter((p) => p.scenario !== "Grand Concert")

        it("ships one preset per scenario, including the derived Grand Concert twin", () => {
            expect(all.map((p) => p.scenario).sort()).toEqual(["Grand Concert", "Trackblazer", "URA Finale", "Unity Cup"])
        })

        it("selects the right card in Trainee Select", () => {
            for (const p of all) expect(p.traineeName).toBeUndefined()
            expect(deriveInGameName(t.name)).toBe(t.inGameName)
            expect(deriveExcludeOutfits(t.name)).toEqual([])
        })

        it("renders with its exact outfit title, research-graded everywhere", () => {
            expect(presetCharacter(t.name)).toBe(t.character)
            expect(presetOutfit(t.name)).toBe(t.outfit)
            const outfits = (outfitData as Record<string, any>)[t.objectivesKey].outfits.map((o: any) => o.title)
            expect(outfits).toContain(t.outfit)
            for (const p of all) expect(presetValidation(p.name, p.scenario)).toBe("research")
        })

        it("still has the goal chain the presets were built against", () => {
            expect(objectivesFor(t.objectivesKey).mandatoryRaces.map((m: any) => m.turn)).toEqual(t.goals)
        })

        it("carries one surface / distance / style identity and the racing-plan trio in every scenario", () => {
            for (const p of all) {
                expect(p.settings.skills!.preferredTrackSurface).toBe("turf")
                expect(p.settings.skills!.preferredTrackDistance).toBe(t.distance)
                expect(p.settings.skills!.preferredRunningStyle).toBe(t.style)
                expect(p.settings.racing!.preferredTerrain).toBe("Turf")
                expect(p.settings.training!.preferredDistanceOverride).toBe(t.override)
                expect(p.settings.general!.enablePopupCheck).toBe(false)
                expect(p.settings.general!.scenario).toBe(p.scenario)
                expect(p.settings.racing!.enableRacingPlan).toBeDefined()
                expect(p.settings.racing!.enableMandatoryRacingPlan).toBeDefined()
                expect(p.settings.racing!.racingPlan).toBeDefined()
            }
        })

        it("plans her own kit and known skills, never a green-chain hold, with the required strategies", () => {
            for (const p of pipeline) {
                expect(p.settings.skills!.plans!.skillPointCheck!.strategy).toBe("optimize_skills")
                expect(p.settings.skills!.plans!.preFinals!.strategy).toBe("optimize_skills")
                expect(p.settings.skills!.plans!.careerComplete!.strategy).toBe("optimize_knapsack")
                for (const planKey of planKeys) {
                    const ids = planIds(p, planKey)
                    expect(ids.length).toBeGreaterThanOrEqual(12)
                    expect(new Set(ids).size).toBe(ids.length)
                    for (const id of ids) expect(skillById.has(id)).toBe(true)
                    for (const id of t.gated.filter((g) => GREEN_CHAIN_POTENTIAL_GOLDS.includes(g))) expect(ids).not.toContain(id)
                    for (const id of t.ownSkills) expect(ids).toContain(id)
                }
            }
        })

        it("plans no skill that only works for another running style", () => {
            for (const p of pipeline) {
                const dead = planIds(p, "careerComplete").filter((id) => {
                    const code = /running_style==(\d)/.exec(skillById.get(id)?.condition ?? "")
                    return code !== null && code[1] !== STYLE_CODE[t.style]
                })
                expect(dead).toEqual([])
            }
        })

        it("keeps Trackblazer settings in the Trackblazer preset only, with the whole default excluded-item list", () => {
            for (const p of pipeline) {
                if (p.scenario !== "Trackblazer") {
                    expect(p.settings.scenarioOverrides).toBeUndefined()
                    continue
                }
                const items = p.settings.scenarioOverrides!.trackblazerExcludedItems as string[]
                for (const item of ["Energy Drink MAX", "Energy Drink MAX EX", "Yummy Cat Food", "Coaching Megaphone"]) expect(items).toContain(item)
            }
        })

        it("raises the Grand Concert Speed target to the policy value for her distance", () => {
            const gc = all.find((p) => p.scenario === "Grand Concert")!
            expect((gc.settings.trainingStatTarget as any)[`training${t.override}StatTarget_speedStatTarget`]).toBe(t.gcSpeed)
        })

        it("carries her advisory with no recommended badge until her own careers complete", () => {
            expect(trainerAdvisories[t.name].recommended).toEqual([])
            for (const scenario of ["URA Finale", "Unity Cup", "Grand Concert"]) expect(avoidAdvisoryFor(t.name, scenario)).toBeNull()
            if (t.trackblazerAvoid) expect(avoidAdvisoryFor(t.name, "Trackblazer")?.reason).toMatch(t.trackblazerAvoid)
            else expect(avoidAdvisoryFor(t.name, "Trackblazer")).toBeNull()
        })
    })

    describe.each([
        { name: "Kawakami Princess", objectivesKey: "Kawakami Princess", fanGoals: [{ turn: 31, targetFans: 7000 }] },
        { name: "T.M. Opera O (O Sole Suo!)", objectivesKey: "TM Opera O", fanGoals: [{ turn: 28, targetFans: 5000 }] },
    ])("$name URA agenda", ({ name, objectivesKey, fanGoals }) => {
        const ura = characterPresets.find((p) => p.name === name && p.scenario === "URA Finale")!
        const planned: { raceName: string; date: string; turnNumber: number }[] = JSON.parse(ura.settings.racing!.racingPlan as string)
        const entry = (r: { raceName: string; date: string }) => (races as Record<string, any>)[`${r.raceName} (${r.date})`]

        it("runs a curated mandatory agenda on URA and smart racing elsewhere", () => {
            expect(ura.settings.racing!.enableRacingPlan).toBe(true)
            expect(ura.settings.racing!.enableMandatoryRacingPlan).toBe(true)
            for (const p of characterPresets.filter((x) => x.name === name && x.scenario !== "URA Finale")) {
                expect(p.settings.racing!.enableRacingPlan).toBe(false)
                expect(p.settings.racing!.racingPlan).toBe("")
            }
        })

        it("carries the fan goal the agenda was built for, in the objectives data and the Grand Concert runtime asset", () => {
            expect(objectivesFor(objectivesKey).fanGoals.map((g: any) => ({ turn: g.turn, targetFans: g.targetFans }))).toEqual(fanGoals)
            expect((gcFanRuntime as any).characters[objectivesKey].fanGoals).toEqual(fanGoals)
        })

        it("plans only real turf races on the turn each entry claims, never on or next to a goal, debut or fan turn", () => {
            const blocked = [12, ...fanGoals.map((g) => g.turn), ...objectivesFor(objectivesKey).mandatoryRaces.map((m: any) => m.turn)]
            for (const r of planned) {
                expect(entry(r)).toBeDefined()
                expect(entry(r).turnNumber).toBe(r.turnNumber)
                expect(entry(r).terrain).toBe("Turf")
                for (const turn of [r.turnNumber - 1, r.turnNumber, r.turnNumber + 1]) expect(blocked).not.toContain(turn)
            }
        })

        it("can clear each fan goal from its own pre-deadline wins, with no back-to-back pair", () => {
            for (const goal of fanGoals) {
                const winnerFans = planned.filter((r) => r.turnNumber < goal.turn).reduce((sum, r) => sum + entry(r).fans, 0)
                expect(winnerFans).toBeGreaterThanOrEqual(goal.targetFans)
            }
            const turns = planned.map((r) => r.turnNumber)
            for (let i = 1; i < turns.length; i++) expect(turns[i] - turns[i - 1]).toBeGreaterThan(1)
        })

        it("stays light on a dense goal chain and leaves Senior to the goals", () => {
            expect(planned.length).toBeGreaterThanOrEqual(3)
            expect(planned.length).toBeLessThanOrEqual(10)
            expect(Math.max(...planned.map((r) => r.turnNumber))).toBeLessThan(49)
        })
    })

    it("uses smart racing everywhere for the four trainees without a fan goal", () => {
        for (const name of ["Aston Machan", "Seeking the Pearl", "Yamanin Zephyr", "Yukino Bijin"]) {
            expect(objectivesFor(name).fanGoals ?? []).toEqual([])
            for (const p of characterPresets.filter((x) => x.name === name)) {
                expect(p.settings.racing!.enableRacingPlan).toBe(false)
                expect(p.settings.racing!.enableMandatoryRacingPlan).toBe(false)
                expect(p.settings.racing!.racingPlan).toBe("")
            }
        }
    })
})

describe("Zenno Rob Roy presets", () => {
    const name = "Zenno Rob Roy"
    const planKeys = ["skillPointCheck", "preFinals", "careerComplete"] as const
    const planIds = (p: (typeof characterPresets)[number], planKey: (typeof planKeys)[number]) =>
        String((p.settings.skills!.plans as any)[planKey].plan)
            .split(",")
            .filter(Boolean)
            .map(Number)
    const skillList = (Array.isArray(skills) ? skills : Object.values(skills)) as { id: number; condition?: string }[]
    const skillById = new Map(skillList.map((s) => [s.id, s]))
    const objective = (objectives as Record<string, any>)[name]
    const all = characterPresets.filter((p) => p.name === name)
    const pipeline = all.filter((p) => p.scenario !== "Grand Concert")
    const ura = all.find((p) => p.scenario === "URA Finale")!
    // Card 104701 from GameTora's card data: Potential Lv1 kit, then the Lv2-5 tree.
    const ownSkills = [200192, 200572, 201112]
    const gated = [201322, 200571, 201172, 201113]
    const fanGoal = { turn: 27, targetFans: 5000 }

    it("ships one preset per scenario, including the derived Grand Concert twin", () => {
        expect(all.map((p) => p.scenario).sort()).toEqual(["Grand Concert", "Trackblazer", "URA Finale", "Unity Cup"])
    })

    it("selects the right card and shows her outfit title, validated only for URA Finale", () => {
        for (const p of all) expect(p.traineeName).toBeUndefined()
        expect(deriveInGameName(name)).toBe(name)
        expect(deriveExcludeOutfits(name)).toEqual([])
        expect(presetCharacter(name)).toBe(name)
        expect(presetOutfit(name)).toBe("Heroic Author")
        expect((outfitData as Record<string, any>)[name].outfits.map((o: any) => o.title)).toContain("Heroic Author")
        for (const p of all) expect(presetValidation(p.name, p.scenario)).toBe(p.scenario === "URA Finale" ? "validated" : "research")
    })

    it("still has the goal chain the presets were built against", () => {
        expect(objective.mandatoryRaces.map((m: any) => m.turn)).toEqual([32, 34, 44, 48, 56, 60, 68, 70, 72])
        expect(objective.fanGoals.map((g: any) => ({ turn: g.turn, targetFans: g.targetFans }))).toEqual([fanGoal])
        expect((gcFanRuntime as any).characters[name].fanGoals).toEqual([fanGoal])
    })

    it("carries one turf / Medium / Pace Chaser identity and the racing-plan trio in every scenario", () => {
        for (const p of all) {
            expect(p.settings.skills!.preferredTrackSurface).toBe("turf")
            expect(p.settings.skills!.preferredTrackDistance).toBe("medium")
            expect(p.settings.skills!.preferredRunningStyle).toBe("pace_chaser")
            expect(p.settings.racing!.preferredTerrain).toBe("Turf")
            expect(p.settings.training!.preferredDistanceOverride).toBe("Medium")
            expect(p.settings.general!.enablePopupCheck).toBe(false)
            expect(p.settings.general!.scenario).toBe(p.scenario)
            expect(p.settings.racing!.enableRacingPlan).toBeDefined()
            expect(p.settings.racing!.enableMandatoryRacingPlan).toBeDefined()
            expect(p.settings.racing!.racingPlan).toBeDefined()
        }
    })

    it("plans her own kit and Pace Chaser skills only, never a green-chain hold, with the required strategies", () => {
        for (const p of pipeline) {
            expect(p.settings.skills!.plans!.skillPointCheck!.strategy).toBe("optimize_skills")
            expect(p.settings.skills!.plans!.preFinals!.strategy).toBe("optimize_skills")
            expect(p.settings.skills!.plans!.careerComplete!.strategy).toBe("optimize_knapsack")
            for (const planKey of planKeys) {
                const ids = planIds(p, planKey)
                expect(new Set(ids).size).toBe(ids.length)
                for (const id of ids) expect(skillById.has(id)).toBe(true)
                for (const id of GREEN_CHAIN_POTENTIAL_GOLDS) expect(ids).not.toContain(id)
                for (const id of [...ownSkills, ...gated]) expect(ids).toContain(id)
                const offStyle = ids.filter((id) => (/running_style==(\d)/.exec(skillById.get(id)?.condition ?? "")?.[1] ?? "2") !== "2")
                expect(offStyle).toEqual([])
            }
        }
    })

    it("runs a curated Junior agenda on URA that clears the fan goal, and smart racing elsewhere", () => {
        const planned: { raceName: string; date: string; turnNumber: number }[] = JSON.parse(ura.settings.racing!.racingPlan as string)
        const entry = (r: { raceName: string; date: string }) => (races as Record<string, any>)[`${r.raceName} (${r.date})`]
        expect(ura.settings.racing!.enableMandatoryRacingPlan).toBe(true)
        const blocked = [12, fanGoal.turn, ...objective.mandatoryRaces.map((m: any) => m.turn)]
        for (const r of planned) {
            expect(entry(r).turnNumber).toBe(r.turnNumber)
            expect(entry(r).terrain).toBe("Turf")
            for (const turn of [r.turnNumber - 1, r.turnNumber, r.turnNumber + 1]) expect(blocked).not.toContain(turn)
        }
        const preDeadline = planned.filter((r) => r.turnNumber < fanGoal.turn)
        expect(preDeadline.reduce((sum, r) => sum + entry(r).fans, 0)).toBeGreaterThanOrEqual(fanGoal.targetFans)
        expect(Math.max(...planned.map((r) => r.turnNumber))).toBeLessThan(fanGoal.turn + 5)
        for (const p of all.filter((x) => x.scenario !== "URA Finale")) {
            expect(p.settings.racing!.enableRacingPlan).toBe(false)
            expect(p.settings.racing!.racingPlan).toBe("")
        }
    })

    it("keeps Trackblazer settings in the Trackblazer preset only and raises the Grand Concert Medium Speed target", () => {
        for (const p of pipeline) {
            if (p.scenario !== "Trackblazer") expect(p.settings.scenarioOverrides).toBeUndefined()
            else for (const item of ["Energy Drink MAX", "Energy Drink MAX EX", "Yummy Cat Food", "Coaching Megaphone"]) expect(p.settings.scenarioOverrides!.trackblazerExcludedItems).toContain(item)
        }
        expect((all.find((p) => p.scenario === "Grand Concert")!.settings.trainingStatTarget as any).trainingMediumStatTarget_speedStatTarget).toBe(1400)
    })

    it("carries her advisory with no recommended badge and no avoid", () => {
        expect(trainerAdvisories[name].recommended).toEqual([])
        for (const scenario of ["URA Finale", "Unity Cup", "Trackblazer", "Grand Concert"]) expect(avoidAdvisoryFor(name, scenario)).toBeNull()
    })
})

describe("Alternate-outfit presets built from their base outfit's preset", () => {
    const planKeys = ["skillPointCheck", "preFinals", "careerComplete"] as const
    const planIds = (p: (typeof characterPresets)[number], planKey: (typeof planKeys)[number]) =>
        String((p.settings.skills!.plans as any)[planKey].plan)
            .split(",")
            .filter(Boolean)
            .map(Number)
    const skillList = (Array.isArray(skills) ? skills : Object.values(skills)) as { id: number; condition?: string }[]
    const skillById = new Map(skillList.map((s) => [s.id, s]))
    const STYLE_CODE: Record<string, number> = { front_runner: 1, pace_chaser: 2, late_surger: 3, end_closer: 4 }
    const cards = (buildBudget as any).traineeGrowth as { cardId: number; character: string; outfit: string; runningStyle: number }[]
    const find = (name: string, scenario: string) => characterPresets.find((p) => p.name === name && p.scenario === scenario)!

    // Kit read from master data (available_skill_set): `own` is the kit the plan counts on (Lv1, or up to Potential Lv3
    // for Rouge Caroler and CODE: ICING), `gated` the rest of the tree. Grid and goal route are the base outfit's.
    const outfits = [
        { name: "Biwa Hayahide (Rouge Caroler)", base: "Biwa Hayahide", cardId: 102302, own: [200512, 200572, 201202, 201532, 201201], gated: [201312, 200511, 201311] },
        { name: "Mihono Bourbon (CODE: ICING)", base: "Mihono Bourbon", cardId: 102602, own: [200432, 200542, 200762, 201522, 200541], gated: [201601, 200431] },
        { name: "Tamamo Cross (Raging Thunder)", base: "Tamamo Cross", cardId: 102102, own: [200462, 200722, 201902], gated: [200162, 200721, 201611, 200461, 200161] },
        { name: "Inari One (Golden Dream)", base: "Inari One", cardId: 103402, own: [200952, 200752, 200642], gated: [201472, 200751, 202322, 200641, 201471, 202321] },
        { name: "Smart Falcon (Twilight Triumph)", base: "Smart Falcon", cardId: 104602, own: [201672, 201252, 202132], gated: [202312, 201671, 202352, 202311, 202351] },
        { name: "Special Week (Ruler of Japan)", base: "Special Week", cardId: 100103, own: [200332, 200612], gated: [201172, 201211, 201182, 202061, 201171, 201181] },
        { name: "Curren Chan (Ma Chérie of the New Moon)", base: "Curren Chan", cardId: 103802, own: [200851, 201322, 201012], gated: [200652, 201011, 201532, 200651, 201531] },
        { name: "Meisho Doto (Dot-o'-Lantern)", base: "Meisho Doto", cardId: 105802, own: [200352, 201902, 201102], gated: [202372, 201901, 200012, 200351, 202371, 200011] },
        { name: "Agnes Digital (Fanatic♡Jiangshi)", base: "Agnes Digital", cardId: 101902, own: [202272, 200462, 200702], gated: [201591, 200461, 201682, 202271, 201681] },
        { name: "Narita Taishin (Difference Engineer)", base: "Narita Taishin", cardId: 105002, own: [200492, 202382, 202082], gated: [201552, 202381, 201452, 202081, 201551, 201451] },
        { name: "Winning Ticket (Dream Deliverer)", base: "Winning Ticket (Get to Winning!)", cardId: 103502, own: [202172, 201412, 201702], gated: [200592, 201411, 202152, 201701, 200591, 202151] },
        { name: "Mejiro McQueen (Fair Lady of the Waves)", base: "Mejiro McQueen (Frontline Elegance)", cardId: 101303, own: [200432, 201532, 202012], gated: [202192, 202011, 200562, 202191, 200561] },
        {
            name: "Air Groove (Quercus Civilis)",
            base: "Air Groove",
            cardId: 101802,
            own: [200342, 201072, 201322],
            gated: [201532, 201071, 200462, 200341, 201531, 200461],
        },
        {
            name: "Eishin Flash (Precise Chocolatier)",
            base: "Eishin Flash",
            cardId: 103702,
            own: [200302, 200592, 201152],
            gated: [201102, 201151, 201392, 201103, 201101, 201391],
        },
        {
            name: "Fine Motion (Titania)",
            base: "Fine Motion",
            cardId: 102202,
            own: [200152, 201902, 201052],
            gated: [201042, 201051, 200192, 201901, 201041, 200191],
        },
        {
            name: "Fuji Kiseki (Succès Étoilé)",
            base: "Fuji Kiseki",
            cardId: 100502,
            own: [200771, 201042, 200582],
            gated: [201332, 200772, 201902, 200581, 201331, 201901],
        },
        {
            name: "Gold City (Authentic / 1928)",
            base: "Gold City (Autumn Cosmos)",
            cardId: 104001,
            own: [200052, 200692, 200602],
            gated: [201651, 200691, 201062, 200601, 201061],
        },
        {
            name: "Haru Urara (New Year ♪ New Urara!)",
            base: "Haru Urara",
            cardId: 105202,
            own: [200452, 201072, 201402],
            gated: [201621, 200451, 200242, 201401, 200241],
        },
        {
            name: "Matikanefukukitaru (Lucky Tidings)",
            base: "Matikanefukukitaru",
            cardId: 105602,
            own: [200012, 200602],
            gated: [200752, 201211, 200062, 200751, 200061],
        },
        {
            name: "Mejiro Dober (Sapphire Sojourn)",
            base: "Mejiro Dober",
            cardId: 105902,
            own: [200062, 202122, 202082],
            gated: [201102, 202121, 202152, 202081, 201101, 202151],
        },
        {
            name: "Mejiro McQueen (End of the Skies)",
            base: "Mejiro McQueen (Frontline Elegance)",
            cardId: 101302,
            own: [200362, 200562, 200742],
            gated: [200192, 200741, 200462, 200361, 200191, 200461],
        },
        {
            name: "Nice Nature (Run & Win)",
            base: "Nice Nature",
            cardId: 106002,
            own: [200492, 201152, 201542],
            gated: [202082, 201151, 200302, 200491, 202081, 200301],
        },
        {
            name: "Rice Shower (Vampire Makeover!)",
            base: "Rice Shower",
            cardId: 103002,
            own: [200771, 201172, 200562],
            gated: [200352, 200561, 200851, 200772, 200351],
        },
        {
            name: "Super Creek (Chiffon-Wrapped Mummy)",
            base: "Super Creek",
            cardId: 104502,
            own: [200332, 201162, 201352],
            gated: [201322, 200331, 201102, 201161, 201321, 201101],
        },
        {
            name: "Symboli Rudolf (Archer by Moonlight)",
            base: "Symboli Rudolf (Emperor's Path)",
            cardId: 101702,
            own: [200192, 200752, 200562],
            gated: [201342, 200561, 201312, 200194, 201341, 201311],
        },
        {
            name: "Daiwa Scarlet (Nuit Étoilée de Scarlet)",
            base: "Daiwa Scarlet",
            cardId: 100902,
            own: [202462, 201282, 202012],
            gated: [201172, 201281, 201182, 202461, 201171, 201181],
        },
        {
            name: "Vodka (Fiery Aqua Vitae)",
            base: "Vodka",
            cardId: 100802,
            own: [202452, 200492, 201112],
            gated: [202152, 200491, 201382, 202451, 202151, 201381],
        },
    ]

    const withoutPlans = (settings: any) => ({ ...settings, skills: { ...settings.skills, plans: undefined } })
    // Outfits whose card style (and Game8 build) differs from the base preset's skill style race and buy as their own style.
    const NEVER_PLANNED = [202331, 201561, 202441]
    const REAIMED = new Set(["Daiwa Scarlet (Nuit Étoilée de Scarlet)"])
    const RESTYLED = new Set(["Special Week (Ruler of Japan)", "Air Groove (Quercus Civilis)", "Symboli Rudolf (Archer by Moonlight)"])

    describe.each(outfits)("$name", (t) => {
        const all = characterPresets.filter((p) => p.name === t.name)
        const pipeline = all.filter((p) => p.scenario !== "Grand Concert")
        const card = cards.find((c) => c.cardId === t.cardId)!

        it("ships one preset per scenario, including the derived Grand Concert twin", () => {
            expect(all.map((p) => p.scenario).sort()).toEqual(["Grand Concert", "Trackblazer", "URA Finale", "Unity Cup"])
        })

        it("names a real released outfit of her character exactly, and selects that outfit in Trainee Select", () => {
            expect(`[${presetOutfit(t.name)}]`).toBe(card.outfit)
            expect(presetCharacter(t.name)).toBe(presetCharacter(t.base))
            expect(deriveInGameName(t.name)).toBe(`[${presetOutfit(t.name)}] ${presetCharacter(t.name)}`)
            expect(deriveExcludeOutfits(t.name)).toEqual([])
            for (const p of all) expect(p.traineeName).toBeUndefined()
        })

        it("stays research-graded with no recommended badge, and carries the base outfit's avoid advisories", () => {
            for (const p of all) expect(presetValidation(p.name, p.scenario)).toBe("research")
            expect(trainerAdvisories[t.name].recommended).toEqual([])
            for (const scenario of ["URA Finale", "Unity Cup", "Trackblazer", "Grand Concert"]) {
                expect(avoidAdvisoryFor(t.name, scenario)?.scenario).toBe(avoidAdvisoryFor(t.base, scenario)?.scenario)
            }
        })

        it("keeps the base build in every scenario apart from the skill plans", () => {
            if (RESTYLED.has(t.name)) return
            // Nuit Étoilée de Scarlet aims at Medium where the base card aims at Mile.
            const aim = (s: any) =>
                REAIMED.has(t.name)
                    ? {
                          ...s,
                          racing: { ...s.racing, preferredDistances: undefined },
                          skills: { ...s.skills, preferredTrackDistance: undefined },
                          training: { ...s.training, preferredDistanceOverride: undefined },
                      }
                    : s
            for (const p of pipeline) expect(aim(withoutPlans(p.settings))).toEqual(aim(withoutPlans(find(t.base, p.scenario).settings)))
        })

        it("races the style it buys skills for", () => {
            for (const p of pipeline) {
                const strategy = p.settings.racing!.originalRaceStrategy
                if (strategy === "Default") expect(card.runningStyle).toBe(STYLE_CODE[p.settings.skills!.preferredRunningStyle as string])
            }
        })

        it("plans her own learnable kit and known skills, never a green-chain hold or one for another style", () => {
            for (const p of pipeline) {
                const style = STYLE_CODE[p.settings.skills!.preferredRunningStyle as string]
                for (const planKey of planKeys) {
                    const ids = planIds(p, planKey)
                    expect(ids.length).toBeGreaterThanOrEqual(12)
                    expect(new Set(ids).size).toBe(ids.length)
                    for (const id of ids) expect(skillById.has(id)).toBe(true)
                    for (const id of [...t.gated.filter((g) => GREEN_CHAIN_POTENTIAL_GOLDS.includes(g)), ...NEVER_PLANNED]) expect(ids).not.toContain(id)
                    for (const id of t.own) expect(ids).toContain(id)
                    const dead = ids.filter((id) => {
                        const code = /running_style==(\d)/.exec(skillById.get(id)?.condition ?? "")
                        return code !== null && Number(code[1]) !== style
                    })
                    expect(dead).toEqual([])
                }
            }
        })

        it("takes the base outfit's Grand Concert Speed target, or the Medium policy value when re-aimed at Medium", () => {
            const key = (p: (typeof characterPresets)[number]) => `training${p.settings.training!.preferredDistanceOverride}StatTarget_speedStatTarget`
            const twin = find(t.name, "Grand Concert")
            const baseTwin = find(t.base, "Grand Concert")
            const expected = REAIMED.has(t.name) ? 1400 : (baseTwin.settings.trainingStatTarget as any)[key(baseTwin)]
            expect((twin.settings.trainingStatTarget as any)[key(twin)]).toBe(expected)
        })
    })

    it("builds Special Week (Ruler of Japan) as a Late Surger, the card's own style, and otherwise keeps the base build", () => {
        for (const scenario of ["URA Finale", "Unity Cup", "Trackblazer"]) {
            const p = find("Special Week (Ruler of Japan)", scenario)
            const base = find("Special Week", scenario)
            expect(p.settings.skills!.preferredRunningStyle).toBe("late_surger")
            // The base races explicit Pace; this outfit keeps Default, which races its Late card.
            expect(p.settings.racing!.originalRaceStrategy).toBe("Default")
            const strip = (s: any) => ({
                ...withoutPlans(s),
                racing: { ...s.racing, juniorYearRaceStrategy: undefined, originalRaceStrategy: undefined },
                skills: { ...s.skills, plans: undefined, preferredRunningStyle: undefined },
                trainingEvent: { ...s.trainingEvent, scenarioEventOverrides: undefined },
            })
            expect(strip(p.settings)).toEqual(strip(base.settings))
        }
        expect(find("Special Week (Ruler of Japan)", "Trackblazer").settings.trainingEvent!.scenarioEventOverrides).toEqual({ "Trackblazer|A Grandkid Get-Together": 0 })
    })

    it.each([
        ["Air Groove (Quercus Civilis)", "Air Groove"],
        ["Symboli Rudolf (Archer by Moonlight)", "Symboli Rudolf (Emperor's Path)"],
    ])("builds %s as a Pace Chaser, the card's own style, and otherwise keeps the base build", (name, base) => {
        for (const scenario of ["URA Finale", "Unity Cup", "Trackblazer"]) {
            const p = find(name, scenario)
            expect(p.settings.skills!.preferredRunningStyle).toBe("pace_chaser")
            const strip = (s: any) => ({
                ...withoutPlans(s),
                skills: { ...s.skills, plans: undefined, preferredRunningStyle: undefined },
                trainingEvent: { ...s.trainingEvent, scenarioEventOverrides: undefined },
            })
            expect(strip(p.settings)).toEqual(strip(find(base, scenario).settings))
        }
        // The Grandkid hint (Prepared to Pass) is a Pace Chaser pick; Trackblazer's other picks are the base's.
        const picks = (n: string) => find(n, "Trackblazer").settings.trainingEvent!.scenarioEventOverrides
        expect(picks(name)).toEqual({ ...picks(base), "Trackblazer|A Grandkid Get-Together": 1 })
    })

    it("keeps Pressure (201212) in Mejiro Bright's plans now that skills.json resolves it", () => {
        expect(skillById.has(201212)).toBe(true)
        const bright = characterPresets.filter((p) => p.name === "Mejiro Bright" && p.scenario !== "Grand Concert")
        expect(bright).toHaveLength(3)
        for (const p of bright) for (const planKey of planKeys) expect(planIds(p, planKey)).toContain(201212)
    })

    it("makes the base outfit's preset skip every alternate outfit that now has its own preset", () => {
        expect(deriveExcludeOutfits("Biwa Hayahide")).toEqual(["Rouge Caroler"])
        expect(deriveExcludeOutfits("Mihono Bourbon")).toEqual(["CODE: ICING"])
    })
})

describe("skill spend objective (Phase 2A)", () => {
    it("exactly the farming set, the Copano sash profile, and the SJC safety profile declare objectives", () => {
        // The four farming profiles run sparks (planned-only spending under Adaptive); Copano
        // URA keeps race_reward; Saintly Jade Cleric Unity Cup is the first safe_completion
        // profile (arms recovery protection). Everything else stays implicitly "rank". A new
        // entry here must be a deliberate decision.
        const declared = characterPresets
            .filter((p) => (p.settings as any)?.skills?.skillSpendObjective !== undefined)
            .map((p) => `${p.name} / ${p.scenario} -> ${(p.settings as any).skills.skillSpendObjective}`)
            .sort()
        expect(declared).toEqual([
            "Air Groove (Legacy Farm) / URA Finale -> sparks",
            "Copano Rickey / URA Finale -> race_reward",
            "Daiwa Scarlet (Legacy Farm) / URA Finale -> sparks",
            "El Condor Pasa (Legacy Farm) / URA Finale -> sparks",
            "Grass Wonder (Saintly Jade Cleric) / Unity Cup -> safe_completion",
            "Super Creek (Blue Farm) / Unity Cup -> sparks",
        ])
    })

    it("the competitive clones next to the farming profiles stay undeclared", () => {
        // The farming presets are clones of these; a stray objective here would flip a
        // competitive profile into planned-only mode.
        const undeclared = (name: string, scenario: string) => {
            const p = characterPresets.find((x) => x.name === name && x.scenario === scenario)
            expect(p).toBeDefined()
            expect((p!.settings as any)?.skills?.skillSpendObjective).toBeUndefined()
        }
        undeclared("Super Creek", "Unity Cup")
        undeclared("Daiwa Scarlet", "URA Finale")
        undeclared("El Condor Pasa", "URA Finale")
        undeclared("Air Groove", "URA Finale")
        undeclared("Copano Rickey", "Unity Cup")
        undeclared("Copano Rickey", "Trackblazer")
        // The safety profile is Unity Cup only: her other scenarios and every base Grass Wonder
        // preset stay on the implicit rank default.
        undeclared("Grass Wonder (Saintly Jade Cleric)", "URA Finale")
        undeclared("Grass Wonder (Saintly Jade Cleric)", "Trackblazer")
        undeclared("Grass Wonder", "Unity Cup")
        undeclared("Grass Wonder", "URA Finale")
        undeclared("Grass Wonder", "Trackblazer")
    })

    it("never declares an objective outside the known enum", () => {
        for (const p of characterPresets) {
            const objective = (p.settings as any)?.skills?.skillSpendObjective
            if (objective !== undefined) {
                expect(SKILL_SPEND_OBJECTIVES).toContain(objective)
            }
        }
    })

    it("no preset ever sets the user-global mode or tier", () => {
        for (const p of characterPresets) {
            expect((p.settings as any)?.skills?.skillSpendMode).toBeUndefined()
            expect((p.settings as any)?.skills?.accountTier).toBeUndefined()
        }
    })
})

describe("Grand Concert derived presets", () => {
    // Intended Speed target per twin. Sprint/Mile Speed-primary builds take the scenario's full
    // 1600 cap, Sprint/Mile builds whose URA Speed target sits below the 1200 norm are tempered
    // to 1400, Medium takes 1400, and Long stayers take no raise at all: a stat target is a
    // WEIGHT, not a ceiling, so lifting Speed on a stayer starves the Stamina its 3000m+ goals
    // need. The three Legacy Farm arms are deliberately absent (URA-only farm specializations).
    const EXPECTED_SPEED: Record<string, number | undefined> = {
        "Agnes Tachyon": 1400,
        "Mihono Bourbon": 1400,
        "Mihono Bourbon (CODE: ICING)": 1400,
        Vodka: 1400,
        "Vodka (Fiery Aqua Vitae)": 1400,
        "Sakura Bakushin O": 1600,
        "King Halo": 1600,
        "Maruzensky (Formula R)": 1600,
        "Daiwa Scarlet": 1600,
        "Daiwa Scarlet (Nuit Étoilée de Scarlet)": 1400,
        "Copano Rickey": 1600,
        "Super Creek": undefined,
        "Super Creek (Chiffon-Wrapped Mummy)": undefined,
        "Gold Ship": undefined,
        // Full-roster port: Sprint.
        "Aston Machan": 1600,
        "Curren Chan": 1600,
        "Curren Chan (Ma Chérie of the New Moon)": 1600,
        "King Halo (Cheerleader in Noble White)": 1600,
        "Nishino Flower": 1600,
        "Haru Urara": 1400,
        "Haru Urara (New Year ♪ New Urara!)": 1400,
        "Hishi Akebono": 1400,
        // Mile.
        "Bamboo Memory": 1600,
        "El Condor Pasa": 1600,
        "Maruzensky (Hot☆Summer Night)": 1600,
        "Oguri Cap": 1600,
        "Oguri Cap (Ashen Miracle)": 1600,
        "Taiki Shuttle (Bubblegum☆Memories)": 1600,
        "Wonder Acute": 1600,
        "Seeking the Pearl": 1600,
        "Yamanin Zephyr": 1600,
        "Agnes Digital": 1400,
        "Agnes Digital (Fanatic♡Jiangshi)": 1400,
        "Fuji Kiseki": 1400,
        "Fuji Kiseki (Succès Étoilé)": 1400,
        "Gold City (Autumn Cosmos)": 1400,
        "Gold City (Authentic / 1928)": 1400,
        "Smart Falcon": 1400,
        "Smart Falcon (Twilight Triumph)": 1400,
        // Medium.
        "Admire Vega": 1400,
        "Air Groove": 1400,
        "Air Groove (Quercus Civilis)": 1400,
        "Air Shakur": 1400,
        "Eishin Flash": 1400,
        "Eishin Flash (Precise Chocolatier)": 1400,
        "El Condor Pasa (Kukulkan Warrior)": 1400,
        "Fine Motion": 1400,
        "Fine Motion (Titania)": 1400,
        "Hishi Amazon": 1400,
        "Inari One": 1400,
        "Inari One (Golden Dream)": 1400,
        "Ines Fujin": 1400,
        "Kawakami Princess": 1400,
        "Kitasan Black": 1400,
        "Meisho Doto": 1400,
        "Meisho Doto (Dot-o'-Lantern)": 1400,
        "Mejiro Ardan": 1400,
        "Mejiro Dober": 1400,
        "Mejiro Dober (Sapphire Sojourn)": 1400,
        "Mejiro Ryan": 1400,
        "Nakayama Festa": 1400,
        "Narita Taishin": 1400,
        "Narita Taishin (Difference Engineer)": 1400,
        "Nice Nature": 1400,
        "Nice Nature (Run & Win)": 1400,
        "Sakura Chiyono O": 1400,
        "Seiun Sky": 1400,
        "Seiun Sky (Soirée des Chatons)": 1400,
        "Silence Suzuka": 1400,
        "Special Week": 1400,
        "Special Week (Ruler of Japan)": 1400,
        "Special Week (Hopp'n♪Happy Heart)": 1400,
        "Sweep Tosho": 1400,
        "Symboli Rudolf (Emperor's Path)": 1400,
        "Symboli Rudolf (Archer by Moonlight)": 1400,
        "T.M. Opera O (New Year, Same Radiance!)": 1400,
        "T.M. Opera O (O Sole Suo!)": 1400,
        "Tokai Teio": 1400,
        "Tokai Teio (Beyond the Horizon)": 1400,
        "Tosen Jordan": 1400,
        "Winning Ticket (Get to Winning!)": 1400,
        "Winning Ticket (Dream Deliverer)": 1400,
        "Yaeno Muteki": 1400,
        "Yukino Bijin": 1400,
        "Zenno Rob Roy": 1400,
        // Long stayers.
        "Biwa Hayahide": undefined,
        "Biwa Hayahide (Rouge Caroler)": undefined,
        "Gold Ship (RUN! RUIN! LAUNCHER!)": undefined,
        "Grass Wonder": undefined,
        "Grass Wonder (Saintly Jade Cleric)": undefined,
        "Manhattan Cafe": undefined,
        Matikanefukukitaru: undefined,
        "Matikanefukukitaru (Lucky Tidings)": undefined,
        Matikanetannhauser: undefined,
        "Mayano Top Gun": undefined,
        "Mayano Top Gun (Sunlight Bouquet)": undefined,
        "Mejiro Bright": undefined,
        "Mejiro McQueen (Fair Lady of the Waves)": undefined,
        "Mejiro McQueen (Frontline Elegance)": undefined,
        "Mejiro McQueen (End of the Skies)": undefined,
        "Mejiro Palmer": undefined,
        "Narita Brian": undefined,
        "Rice Shower": undefined,
        "Rice Shower (Vampire Makeover!)": undefined,
        "Satono Diamond": undefined,
        "Tamamo Cross": undefined,
        "Tamamo Cross (Raging Thunder)": undefined,
    }
    const SPEED_KEY: Record<string, string> = {
        Sprint: "trainingSprintStatTarget_speedStatTarget",
        Mile: "trainingMileStatTarget_speedStatTarget",
        Medium: "trainingMediumStatTarget_speedStatTarget",
        Long: "trainingLongStatTarget_speedStatTarget",
    }
    const gc = (name: string) => characterPresets.find((p) => p.name === name && p.scenario === "Grand Concert")!
    const ura = (name: string) => characterPresets.find((p) => p.name === name && p.scenario === "URA Finale")!
    const derived = Object.keys(EXPECTED_SPEED)

    it("ships a Grand Concert twin for every trainee in the batch, plus the hand-written Taiki build", () => {
        for (const name of derived) expect(gc(name)).toBeDefined()
        const all = characterPresets.filter((p) => p.scenario === "Grand Concert").map((p) => p.name)
        expect(all.sort()).toEqual([...derived, "Taiki Shuttle"].sort())
    })

    it("locks the roster totals the docs quote", () => {
        // The docs used to be checked with `grep -c '^        scenario: "'`, which no longer works:
        // derived twins are not literals, and grandConcertFrom's own return adds a matching line.
        // This assertion is the authoritative count now. Update the docs whenever it changes.
        expect(characterPresets.length).toBe(436)
        expect(characterPresets.filter((p) => p.scenario === "Grand Concert")).toHaveLength(108)
        expect(new Set(characterPresets.map((p) => `${p.name}|${p.scenario}`)).size).toBe(characterPresets.length)
    })

    it("carries the scenario in both places so applying it switches the scenario", () => {
        for (const name of derived) {
            expect(gc(name).scenario).toBe("Grand Concert")
            expect(gc(name).settings.general!.scenario).toBe("Grand Concert")
        }
    })

    it("uses smart racing, never a URA curated agenda", () => {
        for (const name of derived) {
            expect(gc(name).settings.racing!.enableRacingPlan).toBe(false)
            expect(gc(name).settings.racing!.enableMandatoryRacingPlan).toBe(false)
            expect(gc(name).settings.racing!.racingPlan).toBe("")
        }
    })

    it("drops the URA-specific skill-spend objective", () => {
        for (const name of derived) expect((gc(name).settings as any).skills?.skillSpendObjective).toBeUndefined()
    })

    it("raises Speed exactly as intended and leaves stayers alone", () => {
        for (const name of derived) {
            const distance = gc(name).settings.training!.preferredDistanceOverride as string
            const key = SPEED_KEY[distance]
            const got = (gc(name).settings.trainingStatTarget as any)[key]
            const uraValue = (ura(name).settings.trainingStatTarget as any)[key]
            expect(got).toBe(EXPECTED_SPEED[name] ?? uraValue)
        }
    })

    it("never sets a target above a Grand Concert stat cap", () => {
        const caps: Record<string, number> = { speed: 1600, guts: 1500, stamina: 1300, power: 1300, wit: 1300 }
        for (const name of derived) {
            for (const [k, v] of Object.entries(gc(name).settings.trainingStatTarget ?? {})) {
                const stat = k.match(/StatTarget_(\w+?)StatTarget$/)?.[1]?.toLowerCase()
                if (stat !== undefined && caps[stat] !== undefined) expect(v as number).toBeLessThanOrEqual(caps[stat])
            }
        }
    })

    const SCENARIO_FIELDS = ["general.scenario", "racing.enableRacingPlan", "racing.enableMandatoryRacingPlan", "racing.racingPlan", "skills.skillSpendObjective"]
    const PINNED_MILE_TWINS = ["Gold City (Autumn Cosmos)", "Gold City (Authentic / 1928)"]
    const expectOnlyDiffers = (name: string, allowed: Set<string>, speedKey: string) => {
        const a = ura(name).settings as any
        const b = gc(name).settings as any
        for (const category of new Set([...Object.keys(a), ...Object.keys(b)])) {
            for (const key of new Set([...Object.keys(a[category] ?? {}), ...Object.keys(b[category] ?? {})])) {
                const path = `${category}.${key}`
                if (allowed.has(path) || (category === "trainingStatTarget" && key === speedKey)) continue
                expect({ path, value: JSON.stringify(b[category]?.[key]) }).toEqual({ path, value: JSON.stringify(a[category]?.[key]) })
            }
        }
    }

    it("differs from its URA source ONLY in the scenario, racing and Speed-target fields", () => {
        for (const name of derived.filter((n) => !PINNED_MILE_TWINS.includes(n))) {
            expectOnlyDiffers(name, new Set(SCENARIO_FIELDS), SPEED_KEY[ura(name).settings.training!.preferredDistanceOverride as string])
        }
    })

    it("keeps Gold City's twins on the Mile build while her URA builds train Long", () => {
        for (const name of PINNED_MILE_TWINS) {
            expect(ura(name).settings.training!.preferredDistanceOverride).toBe("Long")
            expect(gc(name).settings.training!.preferredDistanceOverride).toBe("Mile")
            const targets = gc(name).settings.trainingStatTarget!
            expect(targets.trainingMileStatTarget_speedStatTarget).toBe(1400)
            expect(targets.trainingLongStatTarget_speedStatTarget).toBe(1000)
            expect(targets.trainingLongStatTarget_staminaStatTarget).toBe(600)
            expectOnlyDiffers(name, new Set([...SCENARIO_FIELDS, "training.preferredDistanceOverride", "trainingStatTarget.trainingLongStatTarget_staminaStatTarget"]), SPEED_KEY.Mile)
        }
    })

    it("is a deep copy - mutating a twin cannot reach back into its URA source", () => {
        // Guards the JSON round-trip in grandConcertFrom. A spread would leave the two sharing
        // nested category objects, so a future edit to one would silently corrupt the other.
        const twin = gc("Super Creek")
        const source = ura("Super Creek")
        expect(twin.settings.training).not.toBe(source.settings.training)
        expect(twin.settings.trainingStatTarget).not.toBe(source.settings.trainingStatTarget)
        expect(twin.settings.skills).not.toBe(source.settings.skills)
        const before = source.settings.training!.preferredDistanceOverride
        ;(twin.settings.training as any).preferredDistanceOverride = "MUTATED"
        expect(source.settings.training!.preferredDistanceOverride).toBe(before)
        ;(twin.settings.training as any).preferredDistanceOverride = before
    })
})

describe("Trackblazer scenario-event picks never take a hint for a style the preset does not race", () => {
    // Codes of skills.json `running_style==N` and of build_budget_data's card `runningStyle`
    // (both master.mdb), as SkillDatabase.kt and raceSurvival/evidence.ts read them.
    const STYLE_BY_CODE: Record<number, string> = { 1: "front_runner", 2: "pace_chaser", 3: "late_surger", 4: "end_closer" }
    const STYLE_BY_STRATEGY: Record<string, string> = { Front: "front_runner", Pace: "pace_chaser", Late: "late_surger", End: "end_closer" }
    const cards = (buildBudget as any).traineeGrowth as { cardId: number; character: string; outfit: string; runningStyle: number }[]
    const trackblazer = (scenarios as any).Trackblazer as Record<string, string[]>

    /** The preset's card; a plain name whose base outfit presetMeta does not list is that character's base card, the lowest card id. */
    function presetCard(presetName: string) {
        const outfit = presetOutfit(presetName)
        const own = cards.filter((c) => c.character === presetCharacter(presetName)).sort((a, b) => a.cardId - b.cardId)
        return outfit ? own.find((c) => c.outfit === (outfit.startsWith("[") ? outfit : `[${outfit}]`)) : own[0]
    }

    /**
     * The style a preset races: its explicit race strategy. With "Default" the bot keeps the game's
     * preselected strategy, the card's own style; when that card is not in the game data, the style
     * the preset buys skills for is the only evidence.
     */
    function racedStyle(preset: (typeof characterPresets)[number]): string {
        const strategy = (preset.settings as any).racing?.originalRaceStrategy
        if (strategy && strategy !== "Default") return STYLE_BY_STRATEGY[strategy]
        const card = presetCard(preset.name)
        return card ? STYLE_BY_CODE[card.runningStyle] : (preset.settings as any).skills?.preferredRunningStyle
    }

    /** Every pick whose option is a hint for a skill that only works in another style. */
    function deadHintPicks(): string[] {
        const dead: string[] = []
        for (const preset of characterPresets.filter((p) => p.scenario === "Trackblazer")) {
            const picks = ((preset.settings as any).trainingEvent?.scenarioEventOverrides ?? {}) as Record<string, number>
            for (const [key, option] of Object.entries(picks)) {
                const text = trackblazer[key.split("|")[1]]?.[option] ?? ""
                const hint = /^(.*) hint \+\d+$/m.exec(text)
                const skill = hint ? (skills as any)[hint[1]] : undefined
                const code = skill ? /running_style==(\d)/.exec(skill.condition ?? "") : null
                if (code && STYLE_BY_CODE[Number(code[1])] !== racedStyle(preset)) dead.push(`${preset.name}|${key}`)
            }
        }
        return dead.sort()
    }

    // Left as shipped on purpose: the card's style and the preset's skill style disagree, so the pick
    // is a preset decision (its race strategy or its skill style), not this data rule.
    const STYLE_CONFLICTS: Record<string, string> = {
        "Mayano Top Gun|Trackblazer|A Grandkid Get-Together": "Default races the card's Front style; the preset buys Pace skills",
    }

    // Validated presets keep their recorded race behaviour (card default style) until a live A/B settles them:
    // Mayano Top Gun, Symboli Rudolf (Emperor's Path), Daiwa Scarlet's Default URA and Unity Cup, Sakura Bakushin O's URA.
    const RACE_STYLE_HOLDS = new Set([
        "Mayano Top Gun|URA Finale",
        "Mayano Top Gun|Unity Cup",
        "Mayano Top Gun|Trackblazer",
        "Mayano Top Gun|Grand Concert",
        "Symboli Rudolf (Emperor's Path)|URA Finale",
        "Symboli Rudolf (Emperor's Path)|Unity Cup",
        "Symboli Rudolf (Emperor's Path)|Trackblazer",
        "Symboli Rudolf (Emperor's Path)|Grand Concert",
        "Daiwa Scarlet|URA Finale",
        "Daiwa Scarlet|Unity Cup",
        "Daiwa Scarlet|Grand Concert",
        "Sakura Bakushin O|URA Finale",
        "Sakura Bakushin O|Grand Concert",
    ])
    const STYLE_CODE_OF: Record<string, string> = { front_runner: "1", pace_chaser: "2", late_surger: "3", end_closer: "4" }
    const skillIndex = new Map((Object.values(skills as any) as { id: number; condition?: string }[]).map((s) => [s.id, s]))

    it("races the style every preset buys skills for, apart from the validated holds", () => {
        const mismatched = characterPresets
            .filter((p) => (p.settings as any).skills?.preferredRunningStyle in STYLE_CODE_OF)
            .filter((p) => racedStyle(p) !== (p.settings as any).skills.preferredRunningStyle)
            .map((p) => `${p.name}|${p.scenario}`)
            .sort()
        expect(mismatched).toEqual([...RACE_STYLE_HOLDS].sort())
    })

    it("plans no skill that only works for another running style", () => {
        const offStyle: string[] = []
        for (const preset of characterPresets) {
            const style = (preset.settings as any).skills?.preferredRunningStyle
            if (!(style in STYLE_CODE_OF)) continue
            for (const [planKey, plan] of Object.entries(((preset.settings as any).skills?.plans ?? {}) as Record<string, { plan?: string }>)) {
                for (const id of String(plan.plan ?? "")
                    .split(",")
                    .filter(Boolean)
                    .map(Number)) {
                    const codes = [...String(skillIndex.get(id)?.condition ?? "").matchAll(/running_style==(\d)/g)].map((m) => m[1])
                    if (codes.length > 0 && !codes.includes(STYLE_CODE_OF[style])) offStyle.push(`${preset.name}|${preset.scenario} ${planKey} ${id}`)
                }
            }
        }
        // Tosen Jordan's URA preset and its Grand Concert twin are validated: Slick Surge stays until a live A/B.
        const held = ["Tosen Jordan|URA Finale", "Tosen Jordan|Grand Concert"].flatMap((k) => ["skillPointCheck", "preFinals", "careerComplete"].map((pk) => `${k} ${pk} 200602`))
        expect(offStyle.sort()).toEqual(held.sort())
    })

    it("takes the stat option wherever the hint is dead, apart from the listed style conflicts", () => {
        expect(deadHintPicks()).toEqual(Object.keys(STYLE_CONFLICTS).sort())
    })

    it("reads the hints and styles it judges from the game data", () => {
        expect(trackblazer["A Grandkid Get-Together"]).toEqual(["Stamina +6\nWit +6", "Prepared to Pass hint +1"])
        expect((skills as any)["Prepared to Pass"].condition).toContain("running_style==2")
        expect((skills as any)["Fast-Paced"].condition).toContain("running_style==1")
        expect((skills as any)["Front Runner Straightaways ○"].condition).toContain("running_style==1")
        const nishino = characterPresets.find((p) => p.name === "Nishino Flower" && p.scenario === "Trackblazer")!
        expect(racedStyle(nishino)).toBe("pace_chaser")
        const seiun = characterPresets.find((p) => p.name === "Seiun Sky" && p.scenario === "Trackblazer")!
        expect(racedStyle(seiun)).toBe("front_runner")
        expect(presetCard("Agnes Digital")?.outfit).toBe("[Full-Color Fangirling]")
        expect(presetCard("Ines Fujin")?.runningStyle).toBe(1)
    })
})

describe("presets for other scenarios leave the Trackblazer settings alone", () => {
    it("ships no Trackblazer override outside a Trackblazer preset", () => {
        // Home's scenario dropdown switches the scenario without re-applying a preset, so any
        // Trackblazer value a URA, Unity Cup or Grand Concert preset shipped would run in Trackblazer.
        const shipped = characterPresets
            .filter((p) => p.scenario !== "Trackblazer")
            .flatMap((p) =>
                Object.keys(p.settings.scenarioOverrides ?? {})
                    .filter((key) => key.startsWith("trackblazer"))
                    .map((key) => `${p.name}|${p.scenario} ${key}`)
            )
        expect(shipped).toEqual([])
    })
})

describe("skill plans resolve against the skill database", () => {
    const skillList = (Array.isArray(skills) ? skills : Object.values(skills)) as { id: number; name_en: string }[]
    const knownIds = new Set<number>(skillList.map((s) => s.id))

    it("resolves every skill id planned by any preset", () => {
        // The bot turns plan ids into names through this database and silently drops an id it cannot find.
        const unresolved: string[] = []
        for (const p of characterPresets) {
            const plans = (p.settings.skills?.plans ?? {}) as Record<string, { plan?: string }>
            for (const [planKey, plan] of Object.entries(plans)) {
                for (const id of String(plan.plan ?? "")
                    .split(",")
                    .map((x) => x.trim())
                    .filter(Boolean)
                    .map(Number)) {
                    if (!knownIds.has(id)) unresolved.push(`${p.name}|${p.scenario} ${planKey} ${id}`)
                }
            }
        }
        expect(unresolved).toEqual([])
    })

    it("keeps the Global Pressure (201212), not a same-named skill Global does not have", () => {
        const pressure = skillList.filter((s) => s.name_en === "Pressure")
        expect(pressure.map((s) => s.id)).toEqual([201212])
        expect(knownIds.has(202542)).toBe(false)
    })
})

describe("Potential skills in preset plans", () => {
    const planStrings = (p: (typeof characterPresets)[number]) =>
        Object.entries(((p.settings as any).skills?.plans ?? {}) as Record<string, { plan?: string }>).map(
            ([key, plan]) => [key, String(plan.plan ?? "").split(",").filter(Boolean).map(Number)] as const,
        )

    it("never plans a green-chain Potential gold in any preset", () => {
        const planned: string[] = []
        for (const p of characterPresets) {
            for (const [key, ids] of planStrings(p)) {
                for (const id of GREEN_CHAIN_POTENTIAL_GOLDS) if (ids.includes(id)) planned.push(`${p.name}|${p.scenario} ${key} ${id}`)
            }
        }
        expect(planned).toEqual([])
    })

    // Plan order is buying priority, so a Potential addition must never outrank a gold the preset already planned.
    const POTENTIAL_ADDITIONS: Record<string, number[]> = {
        "Agnes Digital (Fanatic♡Jiangshi)|Trackblazer": [201591],
        "Agnes Digital (Fanatic♡Jiangshi)|URA Finale": [201591],
        "Agnes Digital (Fanatic♡Jiangshi)|Unity Cup": [201591],
        "Agnes Tachyon|URA Finale": [200571],
        "Agnes Tachyon|Unity Cup": [200571],
        "Aston Machan|Trackblazer": [200531, 202041, 200972],
        "Aston Machan|URA Finale": [200531, 202041, 200972],
        "Aston Machan|Unity Cup": [200531, 202041, 200972],
        "Biwa Hayahide (Rouge Caroler)|Trackblazer": [200511, 201312],
        "Biwa Hayahide (Rouge Caroler)|URA Finale": [200511, 201312],
        "Biwa Hayahide (Rouge Caroler)|Unity Cup": [200511, 201312],
        "Biwa Hayahide|Trackblazer": [200741],
        "Biwa Hayahide|URA Finale": [200741],
        "Biwa Hayahide|Unity Cup": [200741],
        "Copano Rickey|Trackblazer": [202261],
        "Copano Rickey|URA Finale": [202261],
        "Copano Rickey|Unity Cup": [202261],
        "Curren Chan (Ma Chérie of the New Moon)|Trackblazer": [200651],
        "Curren Chan (Ma Chérie of the New Moon)|URA Finale": [200651],
        "Curren Chan (Ma Chérie of the New Moon)|Unity Cup": [200651],
        "Eishin Flash (Precise Chocolatier)|Trackblazer": [201103, 201102, 201392],
        "Eishin Flash (Precise Chocolatier)|URA Finale": [201103, 201102, 201392],
        "Eishin Flash (Precise Chocolatier)|Unity Cup": [201103, 201102, 201392],
        "Fine Motion (Titania)|Trackblazer": [201901],
        "Fine Motion (Titania)|URA Finale": [201901],
        "Fine Motion (Titania)|Unity Cup": [201901],
        "Fine Motion|Trackblazer": [200581],
        "Fine Motion|URA Finale": [200581],
        "Fine Motion|Unity Cup": [200581],
        "Fuji Kiseki (Succès Étoilé)|Trackblazer": [200581],
        "Fuji Kiseki (Succès Étoilé)|URA Finale": [200581],
        "Fuji Kiseki (Succès Étoilé)|Unity Cup": [200581],
        "Fuji Kiseki|Trackblazer": [200571],
        "Fuji Kiseki|URA Finale": [200571],
        "Fuji Kiseki|Unity Cup": [200571],
        "Gold City (Authentic / 1928)|Trackblazer": [200691],
        "Gold City (Authentic / 1928)|URA Finale": [200691],
        "Gold City (Authentic / 1928)|Unity Cup": [200691],
        "Gold City (Autumn Cosmos)|Trackblazer": [201392],
        "Gold City (Autumn Cosmos)|URA Finale": [201392],
        "Gold City (Autumn Cosmos)|Unity Cup": [201392],
        "Gold Ship|Trackblazer": [201481],
        "Gold Ship|URA Finale": [201481],
        "Gold Ship|Unity Cup": [201481],
        "Inari One (Golden Dream)|Trackblazer": [200641],
        "Inari One (Golden Dream)|URA Finale": [200641],
        "Inari One (Golden Dream)|Unity Cup": [200641],
        "Kawakami Princess|Trackblazer": [200491, 201382],
        "Kawakami Princess|URA Finale": [200491, 201382],
        "Kawakami Princess|Unity Cup": [200491, 201382],
        "King Halo (Cheerleader in Noble White)|Trackblazer": [201382],
        "King Halo (Cheerleader in Noble White)|URA Finale": [201382],
        "King Halo (Cheerleader in Noble White)|Unity Cup": [201382],
        "Kitasan Black|Trackblazer": [201272],
        "Kitasan Black|URA Finale": [201272],
        "Kitasan Black|Unity Cup": [201272],
        "Maruzensky (Hot☆Summer Night)|Trackblazer": [201281],
        "Mayano Top Gun|Trackblazer": [200381],
        "Mayano Top Gun|URA Finale": [200381],
        "Mayano Top Gun|Unity Cup": [200381],
        "Meisho Doto (Dot-o'-Lantern)|Trackblazer": [201901, 200351],
        "Meisho Doto (Dot-o'-Lantern)|URA Finale": [201901, 200351],
        "Meisho Doto (Dot-o'-Lantern)|Unity Cup": [201901, 200351],
        "Mejiro Bright|Trackblazer": [202071],
        "Mejiro Bright|URA Finale": [202071],
        "Mejiro Bright|Unity Cup": [202071],
        "Mejiro Dober (Sapphire Sojourn)|Trackblazer": [202121, 202081, 201102],
        "Mejiro Dober (Sapphire Sojourn)|URA Finale": [202121, 202081, 201102],
        "Mejiro Dober (Sapphire Sojourn)|Unity Cup": [202121, 202081, 201102],
        "Mejiro Dober|Trackblazer": [201382],
        "Mejiro Dober|URA Finale": [201382],
        "Mejiro Dober|Unity Cup": [201382],
        "Mejiro McQueen (End of the Skies)|Trackblazer": [200741],
        "Mejiro McQueen (End of the Skies)|URA Finale": [200741],
        "Mejiro McQueen (End of the Skies)|Unity Cup": [200741],
        "Mejiro Palmer|Trackblazer": [200532],
        "Mejiro Palmer|URA Finale": [200532],
        "Mejiro Palmer|Unity Cup": [200532],
        "Mihono Bourbon (CODE: ICING)|Trackblazer": [200431],
        "Mihono Bourbon (CODE: ICING)|URA Finale": [200431],
        "Mihono Bourbon (CODE: ICING)|Unity Cup": [200431],
        "Nakayama Festa|Trackblazer": [201382, 201102],
        "Nakayama Festa|URA Finale": [201382, 201102],
        "Nakayama Festa|Unity Cup": [201382, 201102],
        "Narita Taishin (Difference Engineer)|Trackblazer": [202081, 201452],
        "Narita Taishin (Difference Engineer)|URA Finale": [202081, 201452],
        "Narita Taishin (Difference Engineer)|Unity Cup": [202081, 201452],
        "Narita Taishin|Trackblazer": [200621],
        "Narita Taishin|URA Finale": [200621],
        "Narita Taishin|Unity Cup": [200621],
        "Nice Nature (Run & Win)|Trackblazer": [200491],
        "Nice Nature (Run & Win)|URA Finale": [200491],
        "Nice Nature (Run & Win)|Unity Cup": [200491],
        "Nice Nature|Trackblazer": [201441],
        "Nice Nature|URA Finale": [201441],
        "Nice Nature|Unity Cup": [201441],
        "Nishino Flower|Trackblazer": [200361],
        "Nishino Flower|URA Finale": [200361],
        "Nishino Flower|Unity Cup": [200361],
        "Rice Shower (Vampire Makeover!)|Trackblazer": [200561, 200352],
        "Rice Shower (Vampire Makeover!)|URA Finale": [200561, 200352],
        "Rice Shower (Vampire Makeover!)|Unity Cup": [200561, 200352],
        "Seeking the Pearl|Trackblazer": [201071],
        "Seeking the Pearl|URA Finale": [201071],
        "Seeking the Pearl|Unity Cup": [201071],
        "Seiun Sky (Soirée des Chatons)|Trackblazer": [200541, 201271, 201272, 200532],
        "Seiun Sky (Soirée des Chatons)|URA Finale": [200541, 201271, 201272, 200532],
        "Seiun Sky (Soirée des Chatons)|Unity Cup": [200541, 201271, 201272, 200532],
        "Smart Falcon (Twilight Triumph)|Trackblazer": [202311],
        "Smart Falcon (Twilight Triumph)|URA Finale": [202311],
        "Smart Falcon (Twilight Triumph)|Unity Cup": [202311],
        "Special Week|Trackblazer": [201351],
        "Special Week|URA Finale": [201351, 200511],
        "Special Week|Unity Cup": [201351, 200511],
        "Super Creek (Chiffon-Wrapped Mummy)|Trackblazer": [200331, 201322],
        "Super Creek (Chiffon-Wrapped Mummy)|URA Finale": [200331, 201322],
        "Super Creek (Chiffon-Wrapped Mummy)|Unity Cup": [200331, 201322],
        "Symboli Rudolf (Archer by Moonlight)|Trackblazer": [201312],
        "Symboli Rudolf (Archer by Moonlight)|URA Finale": [201312],
        "Symboli Rudolf (Archer by Moonlight)|Unity Cup": [201312],
        "Taiki Shuttle (Bubblegum☆Memories)|Trackblazer": [201901, 201042],
        "Taiki Shuttle (Bubblegum☆Memories)|URA Finale": [201901, 201042, 201322],
        "Taiki Shuttle (Bubblegum☆Memories)|Unity Cup": [201901, 201042, 201322],
        "Tamamo Cross (Raging Thunder)|Trackblazer": [200461, 201611],
        "Tamamo Cross (Raging Thunder)|URA Finale": [200461, 201611],
        "Tamamo Cross (Raging Thunder)|Unity Cup": [200461, 201611],
        "Tamamo Cross|Trackblazer": [201612],
        "Tamamo Cross|URA Finale": [201612],
        "Tamamo Cross|Unity Cup": [201612],
        "Tokai Teio (Beyond the Horizon)|Trackblazer": [200571, 201141],
        "Tokai Teio (Beyond the Horizon)|URA Finale": [200571, 201141],
        "Tokai Teio (Beyond the Horizon)|Unity Cup": [200571, 201141],
        "Tosen Jordan|Trackblazer": [200571],
        "Tosen Jordan|URA Finale": [200571],
        "Tosen Jordan|Unity Cup": [200571],
        "Vodka|Trackblazer": [200381],
        "Vodka|URA Finale": [200381],
        "Vodka|Unity Cup": [200381],
        "Winning Ticket (Dream Deliverer)|Trackblazer": [201701],
        "Winning Ticket (Dream Deliverer)|URA Finale": [201701],
        "Winning Ticket (Dream Deliverer)|Unity Cup": [201701],
        "Wonder Acute|Trackblazer": [201071, 202301, 201032],
        "Wonder Acute|URA Finale": [201071, 202301, 201032],
        "Wonder Acute|Unity Cup": [201071, 202301, 201032],
        "Yamanin Zephyr|Trackblazer": [201051, 202411, 201042],
        "Yamanin Zephyr|URA Finale": [201051, 202411, 201042],
        "Yamanin Zephyr|Unity Cup": [201051, 202411, 201042],
        "Yukino Bijin|Trackblazer": [200491, 201322],
        "Yukino Bijin|URA Finale": [200491, 201322],
        "Yukino Bijin|Unity Cup": [200491, 201322],
        "Zenno Rob Roy|Trackblazer": [200571, 201113, 201322, 201172],
        "Zenno Rob Roy|URA Finale": [200571, 201113, 201322, 201172],
        "Zenno Rob Roy|Unity Cup": [200571, 201113, 201322, 201172],
    }
    // Gold or higher: a gold icon (ending in 2) or an inherited unique.
    const skillById = new Map((Object.values(skills as any) as { id: number; icon_id: number; inherited?: boolean }[]).map((s) => [s.id, s]))
    const isGold = (id: number) => {
        const s = skillById.get(id)
        return s !== undefined && (s.icon_id % 10 === 2 || s.inherited === true)
    }

    it("places added Potential skills after every gold the preset already planned", () => {
        const misplaced: string[] = []
        for (const p of characterPresets) {
            const added = POTENTIAL_ADDITIONS[`${p.name}|${p.scenario === "Grand Concert" ? "URA Finale" : p.scenario}`]
            if (added === undefined) continue
            for (const [key, ids] of planStrings(p)) {
                const firstAdded = ids.findIndex((id) => added.includes(id))
                if (firstAdded === -1) continue
                const laterGold = ids.slice(firstAdded).filter((id) => !added.includes(id) && isGold(id))
                if (laterGold.length > 0) misplaced.push(`${p.name}|${p.scenario} ${key} gold after the block: ${laterGold.join(",")}`)
            }
        }
        expect(misplaced).toEqual([])
    })
})
