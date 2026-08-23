import { join } from "node:path"
import process from "node:process"
import { loadRaceCatalog } from "../../raceLab/catalog.ts"
import { DISTANCE_BAND_MAX, classifyActivation, distanceTypeFor, gatesAdmit, loadRaceSurvivalEvidence, opponentDamageFraction, parseRaceGates, resolveCourse, resolveRecoverySkill, resolveWorstThreat, selfRecoveryFraction } from "../evidence.ts"
import { STRATEGY_HP_COEFFICIENT, computeCruiseHp, computeEffectiveHp, computeMaxHp, solveRequiredStamina } from "../mechanics.ts"
import { buildSurvivalConstraint, computeSurvivalEnvelope, createRaceSurvivalInput } from "../envelope.ts"
import { formatSurvivalEnvelope } from "../report.ts"
import { toRaceSurvivalInput } from "../adapter.ts"
import { RACE_STRATEGIES, RaceSurvivalError } from "../types.ts"
import type { RaceStrategy, RaceSurvivalInput } from "../types.ts"
import type { CompiledRace } from "../../masterData/types.ts"

const REPO_ROOT = process.cwd()
const evidence = loadRaceSurvivalEvidence(join(REPO_ROOT, "src/data/race_survival_data.json"))
const catalog = loadRaceCatalog(join(REPO_ROOT, "src/data/compiled"))

/** The anchor race: the current Grand Concert target, and the only dirt Medium in the examples. */
const OI_2000 = { targetRace: "Anchor", raceTrack: "Ooi", distanceMeters: 2000, surface: "dirt" } as const

/** A gold recovery with no race gate at all, so it counts in every fixture below. */
const GOLD_RECOVERY_UNGATED = 200481
/** Its white counterpart. */
const WHITE_RECOVERY_UNGATED = 200482
/** A gold recovery gated to Long races only. */
const GOLD_RECOVERY_LONG_ONLY = 200741

function input(over: Partial<RaceSurvivalInput> = {}): RaceSurvivalInput {
    return createRaceSurvivalInput({ ...OI_2000, strategy: "pace", stamina: 800, ...over })
}

// ---- Evidence integrity (Part 1) ----

describe("decoded evidence", () => {
    it("loads at the supported schema version", () => {
        expect(evidence.schemaVersion).toBe(1)
        expect(evidence.source).toContain("master.mdb")
        expect(evidence.courses.length).toBeGreaterThan(100)
        expect(evidence.hpSkills.length).toBeGreaterThan(100)
    })

    it("carries the white and gold general recovery families at 1.5% and 5.5% of MaxHP", () => {
        const general = evidence.hpSkills.filter((s) => s.id >= 200000 && s.id < 300000)
        const white = general.filter((s) => s.rarity === 1).map((s) => selfRecoveryFraction(s)).filter((f): f is number => f !== null)
        const gold = general.filter((s) => s.rarity === 2).map((s) => selfRecoveryFraction(s)).filter((f): f is number => f !== null)
        expect(white.length).toBeGreaterThan(10)
        expect(gold.length).toBeGreaterThan(10)
        // The families are flat: a general white recovery always pays 1.5%, a gold one 5.5%, and the
        // handful of outliers are the higher tiers. Nothing here is rounded or averaged.
        expect(new Set(white)).toContain(0.015)
        expect(new Set(gold)).toContain(0.055)
    })

    it("decodes Mystifying Murmur as a 3% opponent-targeted HP drain", () => {
        const murmur = evidence.hpSkillById(201161)
        expect(murmur?.name).toBe("Mystifying Murmur")
        expect(opponentDamageFraction(murmur!)).toBeCloseTo(0.03, 10)
        expect(selfRecoveryFraction(murmur!)).toBeNull()
        expect(parseRaceGates(murmur!).distanceTypes).toEqual(["medium"])
    })

    it("never lets an opponent-targeted HP effect be a heal", () => {
        for (const skill of evidence.hpSkills) {
            const damage = opponentDamageFraction(skill)
            if (damage !== null) expect(damage).toBeGreaterThan(0)
        }
    })
})

// ---- Distance bands, derived not assumed (Part 1) ----

describe("distance bands", () => {
    it("agrees with every race in the compiled catalogue", () => {
        const mismatches = catalog
            .allRaces()
            .filter((race) => distanceTypeFor(race.distanceMeters) !== race.distanceType.toLowerCase())
            .map((race) => `${race.name} ${race.distanceMeters}m is ${race.distanceType}, model says ${distanceTypeFor(race.distanceMeters)}`)
        expect(mismatches).toEqual([])
    })

    it("puts the cut points inside the gaps the catalogue leaves", () => {
        for (const [band, max] of DISTANCE_BAND_MAX) {
            const inBand = catalog.allRaces().filter((r) => r.distanceType.toLowerCase() === band)
            expect(Math.max(...inBand.map((r) => r.distanceMeters))).toBeLessThanOrEqual(max)
        }
    })
})

// ---- Course resolution ----

describe("course resolution", () => {
    it("resolves the Ooi spelling the race catalogue uses onto the game's Oi track", () => {
        const course = resolveCourse(evidence, { raceTrack: "Ooi", distanceMeters: 2000, surface: "dirt" })
        expect(course.resolution).toBe("exact")
        expect(course.track).toBe("Oi")
        expect(course.courseSetIds).toEqual([11103])
        expect(course.finishTimeSecondsLow).toBeCloseTo(121.9, 10)
        expect(course.finishTimeSecondsHigh).toBeCloseTo(129.0, 10)
    })

    it("resolves every race in the compiled catalogue onto at least one course set", () => {
        const unresolved = catalog
            .allRaces()
            .map((race) => ({ race, course: resolveCourse(evidence, { raceTrack: race.raceTrack, distanceMeters: race.distanceMeters, surface: race.terrain.toLowerCase() === "dirt" ? "dirt" : "turf" }) }))
            .filter((r) => r.course.resolution === "unresolved")
            .map((r) => `${r.race.name} at ${r.race.raceTrack} ${r.race.distanceMeters}m ${r.race.terrain}`)
        expect(unresolved).toEqual([])
    })

    it("reports an inner/outer variant pair as ambiguous and widens the band across it", () => {
        const course = resolveCourse(evidence, { raceTrack: "Niigata", distanceMeters: 2000, surface: "turf" })
        expect(course.resolution).toBe("ambiguous")
        expect(course.courseSetIds.length).toBeGreaterThan(1)
        expect(course.finishTimeSecondsHigh).toBeGreaterThan(course.finishTimeSecondsLow)
    })

    it("refuses to price a race it cannot resolve rather than inventing a duration", () => {
        expect(() => computeSurvivalEnvelope(evidence, input({ raceTrack: "Nowhere" }))).toThrow(RaceSurvivalError)
    })
})

// ---- MaxHP (Part 2) ----

describe("MaxHP", () => {
    it("is the exact external expression", () => {
        expect(computeMaxHp({ stamina: 800, distanceMeters: 2000, strategy: "pace" })).toBeCloseTo(0.8 * 0.89 * 800 + 2000, 10)
        expect(computeMaxHp({ stamina: 0, distanceMeters: 2400, strategy: "late" })).toBe(2400)
    })

    it("orders the strategies by their coefficients on the same race and Stamina", () => {
        const at = (strategy: RaceStrategy) => computeMaxHp({ stamina: 900, distanceMeters: 2000, strategy })
        expect(at("late")).toBeGreaterThan(at("end"))
        expect(at("end")).toBeGreaterThan(at("front"))
        expect(at("front")).toBeGreaterThan(at("pace"))
        expect(at("pace")).toBeGreaterThan(at("runaway"))
    })

    it("rejects a negative Stamina and a non-positive distance", () => {
        expect(() => computeMaxHp({ stamina: -1, distanceMeters: 2000, strategy: "pace" })).toThrow(RaceSurvivalError)
        expect(() => computeMaxHp({ stamina: 800, distanceMeters: 0, strategy: "pace" })).toThrow(RaceSurvivalError)
    })
})

// ---- Recovery (Part 4, Part 12) ----

describe("recovery", () => {
    const base = computeSurvivalEnvelope(evidence, input())
    const white = computeSurvivalEnvelope(evidence, input({ recoverySkillIds: [WHITE_RECOVERY_UNGATED] }))
    const gold = computeSurvivalEnvelope(evidence, input({ recoverySkillIds: [GOLD_RECOVERY_UNGATED] }))

    it("raises the HP margin, white less than gold", () => {
        expect(white.baselineMarginHp).toBeGreaterThan(base.baselineMarginHp)
        expect(gold.baselineMarginHp).toBeGreaterThan(white.baselineMarginHp)
    })

    it("lowers the Stamina requirement, gold more than white", () => {
        expect(white.requiredStaminaTarget!).toBeLessThan(base.requiredStaminaTarget!)
        expect(gold.requiredStaminaTarget!).toBeLessThan(white.requiredStaminaTarget!)
    })

    it("keeps raw HP and never produces an expected value", () => {
        expect(gold.recoveryContribution.totalPotentialHp).toBeCloseTo(0.055 * gold.maxHp, 10)
        expect(gold.recoveryContribution.expectedHp).toBeNull()
        expect(gold.recoveryContribution.effectiveStaminaEquivalent).toBeGreaterThan(0)
    })

    it("gives a recovery whose target condition excludes this race exactly zero HP, not its nominal effect", () => {
        const envelope = computeSurvivalEnvelope(evidence, input({ recoverySkillIds: [GOLD_RECOVERY_LONG_ONLY] }))
        const rejected = envelope.recoveryContribution.unsupported.find((r) => r.skillId === GOLD_RECOVERY_LONG_ONLY)
        expect(rejected?.supportStatus).toBe("INELIGIBLE_FOR_TARGET")
        // The nominal effect is still reported, so a reader can see what was thrown away and why.
        expect(rejected?.hpFraction).toBeCloseTo(0.055, 10)
        expect(envelope.recoveryContribution.totalPotentialHp).toBe(0)
        expect(envelope.requiredStaminaTarget).toBe(base.requiredStaminaTarget)
    })

    it("rejects a skill whose HP effect is not aimed at its own runner", () => {
        const resolved = resolveRecoverySkill(evidence, 201161, { distanceType: "medium", surface: "dirt", strategy: "pace" })
        expect(resolved.supportStatus).toBe("NOT_SELF_TARGETED")
        expect(resolved.targetConditionValid).toBe(false)
    })

    it("marks an unknown skill id unresolved and drops confidence to low", () => {
        const envelope = computeSurvivalEnvelope(evidence, input({ recoverySkillIds: [999999999] }))
        expect(envelope.recoveryContribution.unsupported[0].supportStatus).toBe("NOT_IN_EVIDENCE")
        expect(envelope.confidence).toBe("low")
    })
})

// ---- Activation classes and gates ----

describe("activation classes", () => {
    it("classifies a phase-gated recovery as PHASE_ONLY and a slope-gated one as GEOMETRY_CONDITIONAL", () => {
        expect(classifyActivation(evidence.hpSkillById(200742)!)).toBe("PHASE_ONLY")
        expect(classifyActivation(evidence.hpSkillById(201482)!)).toBe("GEOMETRY_CONDITIONAL")
    })

    it("never returns a probability for any skill in the decoded set", () => {
        for (const skill of evidence.hpSkills) {
            const resolved = resolveRecoverySkill(evidence, skill.id, { distanceType: "medium", surface: "turf", strategy: "pace" })
            expect(typeof resolved.activationClass).toBe("string")
            expect(resolved).not.toHaveProperty("activationProbability")
        }
    })

    it("admits an ungated skill in every race and a Long-gated one only in a Long race", () => {
        const ungated = parseRaceGates(evidence.hpSkillById(GOLD_RECOVERY_UNGATED)!)
        const longOnly = parseRaceGates(evidence.hpSkillById(GOLD_RECOVERY_LONG_ONLY)!)
        expect(longOnly.distanceTypes).toEqual(["long"])
        for (const strategy of RACE_STRATEGIES) {
            expect(gatesAdmit(ungated, { distanceType: "sprint", surface: "turf", strategy })).toBe(true)
        }
        expect(gatesAdmit(longOnly, { distanceType: "medium", surface: "turf", strategy: "pace" })).toBe(false)
        expect(gatesAdmit(longOnly, { distanceType: "long", surface: "turf", strategy: "pace" })).toBe(true)
    })
})

// ---- Debuff budget (Part 5, Part 12) ----

describe("debuff budget", () => {
    it("resolves the worst threat from decoded data instead of naming one in code", () => {
        const threat = resolveWorstThreat(evidence, { distanceType: "medium", surface: "dirt", strategy: "pace" })
        expect(threat?.hpDamageFraction).toBeCloseTo(0.03, 10)
        expect(threat?.confidence).toBe("decoded")
        expect(threat?.skillId).not.toBeNull()
    })

    it("raises the Stamina requirement monotonically across 0, 1 and 2 threats", () => {
        const envelope = computeSurvivalEnvelope(evidence, input({ debuffBudget: "TWO_STAMINA_DEBUFFS" }))
        const required = envelope.debuffScenarios.map((s) => s.requiredStaminaTarget!)
        expect(required).toHaveLength(3)
        expect(required[1]).toBeGreaterThan(required[0])
        expect(required[2]).toBeGreaterThan(required[1])
    })

    it("reports all three budgets whichever one was selected", () => {
        const labels = computeSurvivalEnvelope(evidence, input()).debuffScenarios.map((s) => s.label)
        expect(labels).toEqual(["BASE", "ONE_STAMINA_DEBUFF", "TWO_STAMINA_DEBUFFS"])
    })

    it("returns no Stamina at all when a custom budget removes more HP than the build has", () => {
        const envelope = computeSurvivalEnvelope(
            evidence,
            input({
                debuffBudget: "CUSTOM",
                customThreats: [{ skillId: null, canonicalName: "hypothetical", hpDamageFraction: 1.2, flatHpDamage: null, maxOccurrences: 1, channel: "EDITORIAL_RISK_POLICY", confidence: "assumed" }],
            }),
        )
        expect(envelope.requiredStaminaTarget).toBeNull()
        expect(envelope.survivesSelectedRisk).toBe(false)
    })
})

// ---- Distance (Part 12) ----

describe("distance", () => {
    it("gives a longer race a larger MaxHP but a larger requirement still", () => {
        const short = computeSurvivalEnvelope(evidence, input({ raceTrack: "Tokyo", distanceMeters: 1600, surface: "turf" }))
        const long = computeSurvivalEnvelope(evidence, input({ raceTrack: "Tokyo", distanceMeters: 2400, surface: "turf" }))
        expect(long.maxHp).toBeGreaterThan(short.maxHp)
        expect(long.maxHp - short.maxHp).toBeCloseTo(800, 10)
        expect(long.baselineRequiredHpTarget).toBeGreaterThan(short.baselineRequiredHpTarget)
        expect(long.requiredStaminaTarget!).toBeGreaterThan(short.requiredStaminaTarget!)
    })
})

// ---- Inverse solve (Part 9) ----

describe("inverse solve", () => {
    const grid = { requiredHp: 2500, distanceMeters: 2000, strategy: "pace" as RaceStrategy, recoveryFraction: 0, debuffFraction: 0, flatDebuffHp: 0, marginFraction: 0 }

    it("returns a Stamina that actually survives, and one point less that does not", () => {
        const required = solveRequiredStamina(grid)!
        const at = (stamina: number) => computeEffectiveHp({ stamina, distanceMeters: grid.distanceMeters, strategy: grid.strategy, recoveryFraction: 0, debuffFraction: 0, flatDebuffHp: 0 })
        expect(at(required)).toBeGreaterThanOrEqual(grid.requiredHp)
        expect(at(required - 1)).toBeLessThan(grid.requiredHp)
    })

    it("never increases the requirement when recovery increases", () => {
        let previous = Infinity
        for (const recoveryFraction of [0, 0.015, 0.03, 0.055, 0.11]) {
            const required = solveRequiredStamina({ ...grid, recoveryFraction })!
            expect(required).toBeLessThanOrEqual(previous)
            previous = required
        }
    })

    it("never decreases the requirement when debuff pressure increases", () => {
        let previous = -Infinity
        for (const debuffFraction of [0, 0.03, 0.06, 0.12]) {
            const required = solveRequiredStamina({ ...grid, debuffFraction })!
            expect(required).toBeGreaterThanOrEqual(previous)
            previous = required
        }
    })

    it("never asks a more HP-efficient strategy for more Stamina", () => {
        const ordered = [...RACE_STRATEGIES].sort((a, b) => STRATEGY_HP_COEFFICIENT[b] - STRATEGY_HP_COEFFICIENT[a])
        let previous = -Infinity
        for (const strategy of ordered) {
            const required = solveRequiredStamina({ ...grid, strategy })!
            expect(required).toBeGreaterThanOrEqual(previous)
            previous = required
        }
    })

    it("is deterministic and integral", () => {
        for (let i = 0; i < 50; i++) {
            const required = solveRequiredStamina({ ...grid, requiredHp: 2000 + i * 13.7 })
            expect(Number.isInteger(required)).toBe(true)
            expect(solveRequiredStamina({ ...grid, requiredHp: 2000 + i * 13.7 })).toBe(required)
        }
    })

    it("returns zero when the course distance alone already covers the cost", () => {
        expect(solveRequiredStamina({ ...grid, requiredHp: 1500 })).toBe(0)
    })
})

// ---- Cruise cost ----

describe("cruise cost", () => {
    it("scales linearly with the decoded race duration", () => {
        expect(computeCruiseHp(100)).toBe(2000)
        expect(computeCruiseHp(120)).toBe(2400)
        expect(() => computeCruiseHp(0)).toThrow(RaceSurvivalError)
    })
})

// ---- Unknown mechanics stay explicit (Part 12) ----

describe("unknown mechanics", () => {
    const envelope = computeSurvivalEnvelope(evidence, input({ guts: 600, groundCondition: "good", rushRiskPolicy: "avoid" }))

    it("names them rather than pricing them, and never claims high confidence", () => {
        const names = envelope.unknownMechanics.map((m) => m.mechanic)
        expect(names).toContain("SKILL_ACTIVATION_PROBABILITY")
        expect(names).toContain("GUTS_LATE_RACE_MITIGATION")
        expect(names).toContain("COURSE_SLOPE_GEOMETRY")
        expect(["low", "moderate"]).toContain(envelope.confidence)
    })

    it("carries guts, ground condition and rush policy without letting them move a number", () => {
        const plain = computeSurvivalEnvelope(evidence, input())
        expect(envelope.requiredStaminaTarget).toBe(plain.requiredStaminaTarget)
        expect(envelope.assumptions.join(" ")).toContain("Guts 600 is carried but not priced")
        expect(envelope.assumptions.join(" ")).toContain("Rush risk policy")
    })

    it("labels every external constant as external", () => {
        for (const constant of envelope.constants) {
            expect(constant.channel).toBe("EXTERNAL_MECHANICS_REFERENCE")
            expect(constant.provenance).toContain("master.mdb")
        }
    })
})

// ---- The anchor fixture (Part 7) ----

describe("Oi 2000m dirt anchor", () => {
    // Exact figures, so a change to any coefficient or to the decoded course band breaks this test
    // instead of quietly moving every recommendation the model produces.
    it("prices the raw-Stamina arm of the external guidance", () => {
        const twoDebuffs = computeSurvivalEnvelope(evidence, input({ debuffBudget: "TWO_STAMINA_DEBUFFS" }))
        expect(twoDebuffs.race.courseSetIds).toEqual([11103])
        expect(twoDebuffs.baselineRequiredHpTarget).toBeCloseTo(2509, 10)
        expect(twoDebuffs.requiredStaminaTarget).toBe(940)
        expect(twoDebuffs.requiredStaminaHigh).toBe(1046)
    })

    it("prices the gold-recovery arm of the external guidance", () => {
        const withGold = computeSurvivalEnvelope(evidence, input({ debuffBudget: "TWO_STAMINA_DEBUFFS", recoverySkillIds: [GOLD_RECOVERY_UNGATED] }))
        expect(withGold.requiredStaminaTarget).toBe(733)
        expect(withGold.requiredStaminaHigh).toBe(833)
        expect(withGold.survivesSelectedRisk).toBe(true)
    })
})

// ---- Report and constraint ----

describe("report", () => {
    it("renders byte-identically twice and decomposes the answer", () => {
        const envelope = computeSurvivalEnvelope(evidence, input({ recoverySkillIds: [GOLD_RECOVERY_UNGATED, GOLD_RECOVERY_LONG_ONLY], debuffBudget: "ONE_STAMINA_DEBUFF" }))
        const first = formatSurvivalEnvelope(envelope)
        expect(formatSurvivalEnvelope(envelope)).toBe(first)
        for (const section of ["RACE", "CRUISE COST", "RECOVERY", "DEBUFF BUDGET", "REQUIRED STAMINA", "EVIDENCE", "ASSUMPTIONS", "NOT PRICED", "CONFIDENCE"]) {
            expect(first).toContain(section)
        }
        expect(first).toContain("INELIGIBLE_FOR_TARGET")
    })
})

describe("STAM-2 constraint", () => {
    it("carries the minimum, the preferred range and the recovery the minimum assumes", () => {
        const envelope = computeSurvivalEnvelope(evidence, input({ recoverySkillIds: [GOLD_RECOVERY_UNGATED], debuffBudget: "ONE_STAMINA_DEBUFF" }))
        const constraint = buildSurvivalConstraint(envelope)
        expect(constraint.schemaVersion).toBe(1)
        expect(constraint.minimumStamina).toBe(envelope.requiredStaminaLow)
        expect(constraint.preferredStaminaRange).toEqual([envelope.requiredStaminaTarget, envelope.requiredStaminaHigh])
        expect(constraint.recoveryRequirements).toEqual([GOLD_RECOVERY_UNGATED])
        expect(constraint.debuffRiskPolicy).toBe("ONE_STAMINA_DEBUFF")
        expect(constraint.unknownMechanics).toContain("SKILL_ACTIVATION_PROBABILITY")
    })
})

// ---- Target-profile adapter (Part 10) ----

describe("target-profile adapter", () => {
    const race = catalog.racesByName("Tokyo Daishoten")[0] ?? catalog.allRaces().find((r) => r.raceTrack === "Ooi" && r.distanceMeters === 2000)!

    it("reuses the shared build vocabulary and flags a mismatch instead of swallowing it", () => {
        const matched = toRaceSurvivalInput({ distance: "medium", surface: "dirt", runningStyle: "pace" }, race as CompiledRace, { stamina: 800 })
        expect(matched.notes).toEqual([])
        expect(matched.input.strategy).toBe("pace")
        expect(matched.input.surface).toBe("dirt")

        const mismatched = toRaceSurvivalInput({ distance: "long", surface: "turf", runningStyle: "late" }, race as CompiledRace, { stamina: 800 })
        expect(mismatched.notes).toContain("TARGET_DISTANCE_DIFFERS_FROM_RACE")
        expect(mismatched.notes).toContain("TARGET_SURFACE_DIFFERS_FROM_RACE")
    })

    it("refuses to guess a running style", () => {
        expect(() => toRaceSurvivalInput({ distance: "medium" }, race as CompiledRace, { stamina: 800 })).toThrow(RaceSurvivalError)
    })

    it("lets a caller override the profile's style and says it did", () => {
        const overridden = toRaceSurvivalInput({ runningStyle: "pace" }, race as CompiledRace, { stamina: 800, strategy: "late" })
        expect(overridden.input.strategy).toBe("late")
        expect(overridden.notes).toContain("RUNNING_STYLE_FROM_CALLER")
    })
})
