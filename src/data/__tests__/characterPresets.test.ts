import races from "../races.json"
import objectives from "../character_objectives.json"
import skills from "../skills.json"
import gcFanRuntime from "../../../android/app/src/main/assets/gc_fan_runtime.json"
import { avoidAdvisoryFor, characterPresets, trainerAdvisories } from "../characterPresets"
import { presetCharacter, presetOutfit, presetValidation } from "../presetMeta"
import { SKILL_SPEND_OBJECTIVES } from "../../lib/adaptiveSkillPolicy"
import { deriveExcludeOutfits, deriveInGameName } from "../../lib/rotationSnapshots"

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

    it("never plans a skill locked behind a Potential level the account has not reached", () => {
        // Read off her live Potential screen 2026-07-17: the account is on Potential Lv2, and her tree
        // gates Chance of Victory (Lv3), Collaborative Graded Races o (Lv4) and Strong Steps (Lv5).
        // The card manifest files these under `skills_awakening`, which is easy to misread as
        // star-gated - they are not. A locked skill is a planner no-op, so this never crashed; it just
        // silently wasted plan slots. Raise her Potential -> delete the id from this list and re-add it.
        const potentialGated: Record<number, string> = {
            202261: "Chance of Victory (Potential Lv3)",
            202252: "Collaborative Graded Races o (Potential Lv4)",
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
        // Design decision for recovery protection: this profile PLANS its recovery (her own
        // hint-discounted kit) rather than relying on automatic injection. 200741 Cooldown is
        // awakening Lv3 and the account's verified Potential is Lv3, so the gold is obtainable.
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

    it("never plans a skill locked behind the account's Potential Lv3", () => {
        // Read off her live Potential screen 2026-07-17: Lv2 Trick (Front) and Lv3 Cooldown are
        // unlocked; Lv4 Late Surger Savvy ○ (201542) and Lv5 Relax (201421) are not. The base
        // card's Trackblazer plan carries 201542, so a blind clone would have planned a locked
        // skill - the Copano lesson. Raise her Potential -> revisit this pin.
        for (const p of trio) {
            for (const planKey of ["skillPointCheck", "preFinals", "careerComplete"] as const) {
                const ids = planIds(p, planKey)
                expect(ids).not.toContain(201542)
                expect(ids).not.toContain(201421)
            }
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

    // Identity and kit read from the game's master data (card_rarity_data, available_skill_set).
    // `gated` is each card's Potential Lv2-5 tree: the account's Potential on these new cards is
    // unverified, so none of them may be planned until it is read in-game.
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

        it("plans only known skills with the required strategies, and never a Potential-gated one", () => {
            for (const p of pipeline) {
                expect(p.settings.skills!.plans!.skillPointCheck!.strategy).toBe("optimize_skills")
                expect(p.settings.skills!.plans!.preFinals!.strategy).toBe("optimize_skills")
                expect(p.settings.skills!.plans!.careerComplete!.strategy).toBe("optimize_knapsack")
                for (const planKey of planKeys) {
                    const ids = planIds(p, planKey)
                    expect(ids.length).toBeGreaterThanOrEqual(12)
                    for (const id of ids) expect(knownSkills.has(id)).toBe(true)
                    for (const id of t.gated) expect(ids).not.toContain(id)
                    for (const id of t.ownSkills) expect(ids).toContain(id)
                }
            }
        })

        it("keeps the whole default excluded-item list when Trackblazer adds to it (arrays replace)", () => {
            for (const p of pipeline) {
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
        Vodka: 1400,
        "Sakura Bakushin O": 1600,
        "King Halo": 1600,
        "Maruzensky (Formula R)": 1600,
        "Daiwa Scarlet": 1600,
        "Copano Rickey": 1600,
        "Super Creek": undefined,
        "Gold Ship": undefined,
        // Full-roster port: Sprint.
        "Curren Chan": 1600,
        "King Halo (Cheerleader in Noble White)": 1600,
        "Nishino Flower": 1600,
        "Haru Urara": 1400,
        "Hishi Akebono": 1400,
        // Mile.
        "Bamboo Memory": 1600,
        "El Condor Pasa": 1600,
        "Maruzensky (Hot☆Summer Night)": 1600,
        "Oguri Cap": 1600,
        "Oguri Cap (Ashen Miracle)": 1600,
        "Taiki Shuttle (Bubblegum☆Memories)": 1600,
        "Wonder Acute": 1600,
        "Agnes Digital": 1400,
        "Fuji Kiseki": 1400,
        "Gold City (Autumn Cosmos)": 1400,
        "Smart Falcon": 1400,
        // Medium.
        "Admire Vega": 1400,
        "Air Groove": 1400,
        "Air Shakur": 1400,
        "Eishin Flash": 1400,
        "El Condor Pasa (Kukulkan Warrior)": 1400,
        "Fine Motion": 1400,
        "Hishi Amazon": 1400,
        "Inari One": 1400,
        "Ines Fujin": 1400,
        "Kitasan Black": 1400,
        "Meisho Doto": 1400,
        "Mejiro Ardan": 1400,
        "Mejiro Dober": 1400,
        "Mejiro Ryan": 1400,
        "Nakayama Festa": 1400,
        "Narita Taishin": 1400,
        "Nice Nature": 1400,
        "Sakura Chiyono O": 1400,
        "Seiun Sky": 1400,
        "Seiun Sky (Soirée des Chatons)": 1400,
        "Silence Suzuka": 1400,
        "Special Week": 1400,
        "Special Week (Hopp'n♪Happy Heart)": 1400,
        "Sweep Tosho": 1400,
        "Symboli Rudolf (Emperor's Path)": 1400,
        "T.M. Opera O (New Year, Same Radiance!)": 1400,
        "Tokai Teio": 1400,
        "Tokai Teio (Beyond the Horizon)": 1400,
        "Tosen Jordan": 1400,
        "Winning Ticket (Get to Winning!)": 1400,
        "Yaeno Muteki": 1400,
        // Long stayers.
        "Biwa Hayahide": undefined,
        "Gold Ship (RUN! RUIN! LAUNCHER!)": undefined,
        "Grass Wonder": undefined,
        "Grass Wonder (Saintly Jade Cleric)": undefined,
        "Manhattan Cafe": undefined,
        Matikanefukukitaru: undefined,
        Matikanetannhauser: undefined,
        "Mayano Top Gun": undefined,
        "Mayano Top Gun (Sunlight Bouquet)": undefined,
        "Mejiro Bright": undefined,
        "Mejiro McQueen (Frontline Elegance)": undefined,
        "Mejiro Palmer": undefined,
        "Narita Brian": undefined,
        "Rice Shower": undefined,
        "Satono Diamond": undefined,
        "Tamamo Cross": undefined,
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
        expect(characterPresets.length).toBe(300)
        expect(characterPresets.filter((p) => p.scenario === "Grand Concert")).toHaveLength(74)
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
            const distance = ura(name).settings.training!.preferredDistanceOverride as string
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

    it("differs from its URA source ONLY in the scenario, racing and Speed-target fields", () => {
        const allowed = new Set(["general.scenario", "racing.enableRacingPlan", "racing.enableMandatoryRacingPlan", "racing.racingPlan", "skills.skillSpendObjective"])
        for (const name of derived) {
            const a = ura(name).settings as any
            const b = gc(name).settings as any
            const speedKey = SPEED_KEY[a.training.preferredDistanceOverride as string]
            for (const category of new Set([...Object.keys(a), ...Object.keys(b)])) {
                for (const key of new Set([...Object.keys(a[category] ?? {}), ...Object.keys(b[category] ?? {})])) {
                    const path = `${category}.${key}`
                    if (allowed.has(path) || (category === "trainingStatTarget" && key === speedKey)) continue
                    expect({ path, value: JSON.stringify(b[category]?.[key]) }).toEqual({ path, value: JSON.stringify(a[category]?.[key]) })
                }
            }
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
