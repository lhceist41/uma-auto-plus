import races from "../races.json"
import objectives from "../character_objectives.json"
import skills from "../skills.json"
import { avoidAdvisoryFor, characterPresets, trainerAdvisories } from "../characterPresets"
import { presetCharacter, presetOutfit, presetValidation } from "../presetMeta"
import { SKILL_SPEND_OBJECTIVES } from "../../lib/adaptiveSkillPolicy"

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

    it("matches the source Creek Unity build in every setting except the spark target", () => {
        const strip = (s: unknown) => {
            const clone = JSON.parse(JSON.stringify(s))
            delete clone.training.focusOnSparkStatTarget
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

    it("ships exactly the pipeline trio, one preset per scenario", () => {
        expect(trio).toHaveLength(3)
        expect(trio.map((p) => p.scenario).sort()).toEqual(["Trackblazer", "URA Finale", "Unity Cup"])
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

    it("stays research-graded in every scenario until a live career says otherwise", () => {
        for (const p of trio) expect(presetValidation(p.name, p.scenario)).toBe("research")
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
describe("skill spend objective (Phase 2A)", () => {
    it("only the Copano Rickey URA preset declares an objective in 2A", () => {
        // 2A migrates exactly one preset - the sash profile, whose whole point is a must-win
        // race. Everything else stays implicitly "rank" (the V1-identical behavior) until the
        // Phase 2B migration set is validated. A new entry here must be a deliberate decision.
        const declared = characterPresets.filter((p) => (p.settings as any)?.skills?.skillSpendObjective !== undefined)
        expect(declared.map((p) => `${p.name} / ${p.scenario}`)).toEqual(["Copano Rickey / URA Finale"])
        expect((declared[0].settings as any).skills.skillSpendObjective).toBe("race_reward")
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
