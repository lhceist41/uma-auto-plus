package com.steve1316.uma_android_automation.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.opencv.core.Point
import java.io.File

@DisplayName("Skill buy row guards")
class SkillBuyRowGuardTest {
    private val lowerVersions: Map<String, String> =
        mapOf(
            "Clairvoyance" to "Hawkeye",
            "Corner Recovery ◎" to "Corner Recovery ○",
            "Corner Recovery ○" to "Corner Recovery ×",
            "Long Straightaways ◎" to "Long Straightaways ○",
            "Long Straightaways ○" to "Blast Forward",
        )
    private val golds: Set<String> = setOf("Clairvoyance", "Blast Forward")

    private fun covered(name: String): Set<String> = namesCoveredByVerifiedBuy(name, { it in golds }) { lowerVersions[it] }

    @Nested
    @DisplayName("ownedAfterScan() and namesCoveredByVerifiedBuy()")
    inner class StaleFrameTests {
        @Test
        fun `a white read as not obtained from a frame captured before its gold's verified buy stays obtained`() {
            val owned = covered("Clairvoyance")
            assertTrue(ownedAfterScan(scanObtained = false, name = "Hawkeye", ownedThisSession = owned))
        }

        @Test
        fun `an unrelated skill keeps its scan value`() {
            val owned = covered("Clairvoyance")
            assertFalse(ownedAfterScan(scanObtained = false, name = "Swinging Maestro", ownedThisSession = owned))
            assertTrue(ownedAfterScan(scanObtained = true, name = "Swinging Maestro", ownedThisSession = owned))
        }

        @Test
        fun `a fresh obtained read stays obtained`() {
            assertTrue(ownedAfterScan(scanObtained = true, name = "Hawkeye", ownedThisSession = emptySet()))
        }

        @Test
        fun `a verified buy covers itself and every lower version, never a higher one`() {
            assertEquals(setOf("Corner Recovery ○", "Corner Recovery ×"), covered("Corner Recovery ○"))
            assertEquals(setOf("Corner Recovery ◎", "Corner Recovery ○", "Corner Recovery ×"), covered("Corner Recovery ◎"))
        }

        @Test
        fun `a gold listed below its whites is never covered by a white's buy`() {
            assertEquals(setOf("Long Straightaways ○"), covered("Long Straightaways ○"))
            assertEquals(setOf("Long Straightaways ◎", "Long Straightaways ○"), covered("Long Straightaways ◎"))
            assertEquals(setOf("Blast Forward"), covered("Blast Forward"))
        }

        @Test
        fun `a looping chain still ends`() {
            assertEquals(setOf("A", "B"), namesCoveredByVerifiedBuy("A", { false }) { if (it == "A") "B" else "A" })
        }
    }

    @Nested
    @DisplayName("skillUpTapTargetOrSkip()")
    inner class NoPlusTests {
        @Test
        fun `no (+) in the band means no tap, one log line and a dead-tap record`() {
            val dead: MutableSet<String> = mutableSetOf()
            val logs: MutableList<String> = mutableListOf()
            assertNull(skillUpTapTargetOrSkip("Hawkeye", emptyList(), Point(976.0, 1193.0), dead) { logs.add(it) })
            assertEquals(setOf("Hawkeye"), dead)
            assertEquals(1, logs.size)
            assertTrue(logs.single().contains("no (+) on the row"))
        }

        @Test
        fun `a (+) in the band is tapped at the match nearest the computed point`() {
            val dead: MutableSet<String> = mutableSetOf()
            val logs: MutableList<String> = mutableListOf()
            val target = skillUpTapTargetOrSkip("Hawkeye", listOf(Point(976.0, 1240.0), Point(976.0, 1210.0)), Point(976.0, 1193.0), dead) { logs.add(it) }
            assertEquals(Point(976.0, 1210.0), target)
            assertTrue(dead.isEmpty())
            assertTrue(logs.isEmpty())
        }
    }

    @Nested
    @DisplayName("SkillList wiring")
    inner class WiringTests {
        // SkillList needs a live Game, so its call sites are pinned by source.
        private val src: String by lazy {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            val rel = "android/app/src/main/java/com/steve1316/uma_android_automation/types/SkillList.kt"
            while (dir != null && !File(dir, rel).isFile) dir = dir.parentFile
            File(dir ?: throw AssertionError("$rel not found"), rel).readText()
        }

        private fun body(signature: String): String {
            val start = src.indexOf(signature)
            assertTrue(start >= 0, signature)
            return src.substring(start, src.indexOf("\n    }", start))
        }

        @Test
        fun `both scan paths apply the owned-this-session set`() {
            for (fn in listOf("fun analyzeSkillListEntry(", "fun analyzeSkillListEntryThreadSafe(")) {
                assertTrue(body(fn).contains("entry.bIsObtained = ownedAfterScan(bIsObtained, entry.name, ownedThisSession)"), fn)
            }
        }

        @Test
        fun `only a verified buy feeds the set, and the plan reset keeps it`() {
            val buy = body("fun buySkill(")
            val verified = buy.indexOf("entry.markObtained()")
            assertTrue(verified >= 0 && buy.indexOf("ownedThisSession += namesCoveredByVerifiedBuy(name, { entries[it]?.skillData?.bIsGold == true })") > verified)
            assertEquals(1, Regex("ownedThisSession \\+=").findAll(src).count())
            assertTrue(body("fun sellAllSkills(").contains("name !in ownedThisSession"))
        }

        @Test
        fun `a row without a (+) returns before the tap and the zero-landing read`() {
            val buy = body("fun buySkill(")
            val skip = buy.indexOf("relocateSkillUpButton(name, skillUpButtonLocation) ?: return null")
            assertTrue(skip >= 0 && skip < buy.indexOf("val zeroLandingCheckable") && skip < buy.indexOf("entry.buy(tapTarget)"))
            assertTrue(body("private fun relocateSkillUpButton(").contains("?: return null"))
        }
    }
}
