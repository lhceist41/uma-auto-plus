#!/usr/bin/env node
// Enables mid-run skill buying on all 51 character presets with a conservative
// threshold (1200 SP) that only fires late in Senior year — matching the
// community-recommended "Senior April" buy window.
//
// For each preset:
//   skills.enableSkillPointCheck:         false → true
//   skills.skillPointCheck:               600   → 1200
//   skills.plans.skillPointCheck:
//     enabled:                            false → true
//     strategy:                           "default" → "optimize_skills"
//     enableBuyInheritedUniqueSkills:     false → true
//     plan:                               "" → <same IDs as preFinals.plan>
//
// Rationale:
//   - 1200 is high enough that it won't fire in Junior / early-Classic (when
//     hints haven't accumulated and early purchases overpay).
//   - Reuses the per-character priority list we set up for preFinals, so the
//     mid-run buy follows the same meta-aligned priority order.
//   - If a character accumulates less than 1200 SP during the career (low-Wit
//     builds), mid-run simply never fires and current behavior is preserved.
//   - preFinals + careerComplete stay enabled as safety nets.

const fs = require("fs")
const path = require("path")

const PRESET_FILE = path.resolve(__dirname, "..", "src/data/characterPresets.ts")
const THRESHOLD = 1200

function main() {
    console.log("Reading preset file...")
    const raw = fs.readFileSync(PRESET_FILE, "utf8")
    const declIdx = raw.indexOf("export const characterPresets")
    const eqIdx = raw.indexOf("=", declIdx)
    const arrayStart = raw.indexOf("[", eqIdx)
    const arrayEnd = raw.lastIndexOf("]")
    const header = raw.slice(0, arrayStart)
    const footer = raw.slice(arrayEnd + 1)
    const presets = JSON.parse(raw.slice(arrayStart, arrayEnd + 1))
    console.log(`  Loaded ${presets.length} preset entries\n`)

    let updated = 0
    let planCopied = 0
    for (const entry of presets) {
        const s = entry.settings?.skills
        if (!s) continue

        s.enableSkillPointCheck = true
        s.skillPointCheck = THRESHOLD

        if (!s.plans) continue
        const spc = s.plans.skillPointCheck
        if (!spc) continue

        spc.enabled = true
        spc.strategy = "optimize_skills"
        spc.enableBuyInheritedUniqueSkills = true

        // Reuse the per-character priority list we already populated on preFinals
        // so the mid-run buy prioritizes the same community-meta skills.
        const preFinalsPlan = s.plans.preFinals?.plan
        if (typeof preFinalsPlan === "string" && preFinalsPlan.length > 0) {
            spc.plan = preFinalsPlan
            planCopied++
        }

        updated++
    }
    console.log(`  Updated mid-run buying on ${updated} preset entries`)
    console.log(`  Copied preFinals priority plan to skillPointCheck on ${planCopied} entries\n`)

    console.log("Writing preset file...")
    const serialized = JSON.stringify(presets, null, 4)
    fs.writeFileSync(PRESET_FILE, header + serialized + footer, "utf8")
    console.log(`  Wrote ${PRESET_FILE}`)
}

main()
