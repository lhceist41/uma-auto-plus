package com.steve1316.uma_android_automation.bot

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Invariant tests over the shipped skill data (`src/data/skills.json`, the file the app seeds
 * its skill table from) pinning the price-floor facts the finalization guard's design rests
 * on. The guard performs NO scan-free price-floor acceptance, because the data cannot support
 * one: purchasable negative skills cost 40 before any discount (below the mid-career 42
 * early-exit heuristic), and hint discounts are observed off the screen, never bounded by
 * repository data. These tests fail loudly when future data moves either floor, forcing that
 * design decision to be revisited instead of silently rotting.
 */
@DisplayName("Shipped skill-data price floors")
class SkillDataFloorTest {
    private fun loadSkills(): List<JSONObject> {
        // Gradle runs JVM tests with the module directory as CWD; walk up until the repo's
        // data file is found so the test also works from the repo root.
        var dir: File? = File(System.getProperty("user.dir"))
        var file: File? = null
        repeat(5) {
            val candidate = File(dir, "src/data/skills.json")
            if (candidate.isFile) {
                file = candidate
                return@repeat
            }
            dir = dir?.parentFile
        }
        val data = requireNotNull(file) { "src/data/skills.json not found from ${System.getProperty("user.dir")}" }
        val root = JSONObject(data.readText())
        return root.keys().asSequence().map { root.getJSONObject(it) }.toList()
    }

    @Test
    fun `the data loads and covers the full roster of skills`() {
        val skills = loadSkills()
        assertTrue(skills.size > 600, "expected the full skill table, got ${skills.size}")
    }

    @Test
    fun `the cheapest purchasable skill is a 40-point negative - below the 42 heuristic, so no floor acceptance is sound`() {
        val priced = loadSkills().filter { it.optInt("cost", 0) > 0 }
        val minCost = priced.minOf { it.getInt("cost") }
        assertEquals(40, minCost, "the purchasable price floor moved - revisit the finalization guard's no-floor design note")
        assertTrue(
            minCost < SkillPlan.SKILL_POINTS_EARLY_EXIT_FLOOR,
            "a legal purchase below the mid-career early-exit heuristic exists, which is exactly why the guard never uses that heuristic for acceptance",
        )
    }

    @Test
    fun `every skill priced below 70 is negative-family - the cheapest normal skill costs 70`() {
        val priced = loadSkills().filter { it.optInt("cost", 0) > 0 }
        val below70 = priced.filter { it.getInt("cost") < 70 }
        assertTrue(below70.isNotEmpty())
        for (skill in below70) {
            assertEquals(
                4,
                skill.getInt("icon_id") % 10,
                "\"${skill.optString("name_en")}\" costs ${skill.getInt("cost")} but is not negative-family - the non-negative floor moved below 70",
            )
        }
        val minNonNegative = priced.filter { it.getInt("icon_id") % 10 != 4 }.minOf { it.getInt("cost") }
        assertEquals(70, minNonNegative, "the non-negative price floor moved - the 42 mid-career heuristic (70 at the deepest observed 40% discount) no longer derives")
    }
}
