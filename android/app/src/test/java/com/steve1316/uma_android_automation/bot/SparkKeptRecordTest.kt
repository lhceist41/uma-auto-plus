package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The kept record shows the names the chooser scored, and the reason text never prints the not-a-target marker. */
class SparkKeptRecordTest {
    private val tx = "abc12345"

    private fun stat(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.STAT)

    private fun apt(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.APTITUDE)

    private fun unique(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.UNIQUE)

    private fun white(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.WHITE, SparkWhiteClass.SKILL)

    private fun complete(vararg rows: SparkRowFact) = SparkSetReading(rows.toList(), SparkScanTermination.COMPLETE_END_MARKER)

    private fun authority(earlier: SparkSetReading?, pager: SparkSetReading) =
        SparkReadReconcile.reconcile(SparkSetSide.REROLLED, tx, earlier?.let { SparkSideCapture(it, SparkSetSide.REROLLED, tx) }, pager, null)

    @Test
    fun `a repaired winner side is kept under the repaired names with the pager's count, order, kinds and stars`() {
        val earlier = complete(stat("Speed", 2), apt("Long", 2), unique("Super-Duper Climax", 1), white("Corner Adept O", 1))
        val pager = complete(stat("peed", 2), apt("-ong", 2), unique("Super-Duper Climax", 1), white("Corner Adept O", 1))
        val result = authority(earlier, pager)
        assertEquals(SparkReadAuthority.PAGER_WITH_STRUCTURAL_NAME_REPAIR, result.authority)

        val kept = SparkReadReconcile.keptRows(pager.rows, result)

        assertEquals(listOf("Speed", "Long", "Super-Duper Climax", "Corner Adept O"), kept.map { it.name })
        assertEquals(pager.rows.map { it.kind }, kept.map { it.kind })
        assertEquals(pager.rows.map { it.stars }, kept.map { it.stars })
    }

    @Test
    fun `a white name that lost its first glyph is kept under the earlier name`() {
        val earlier = complete(stat("Speed", 2), white("Corner Adept O", 1))
        val pager = complete(stat("peed", 2), white("orner Adept O", 1))

        val kept = SparkReadReconcile.keptRows(pager.rows, authority(earlier, pager))

        assertEquals(listOf("Speed", "Corner Adept O"), kept.map { it.name })
    }

    @Test
    fun `a side the chooser did not repair is kept as the pager read it`() {
        val clean = complete(stat("Speed", 2), apt("Long", 2))
        val unchanged = authority(clean, clean)
        assertEquals(SparkReadAuthority.PAGER_UNCHANGED, unchanged.authority)
        assertSame(clean.rows, SparkReadReconcile.keptRows(clean.rows, unchanged))

        val damaged = complete(stat("peed", 2), apt("Long", 2))
        val pagerOnly = authority(null, damaged)
        assertEquals(SparkReadAuthority.PAGER_ONLY, pagerOnly.authority)
        assertEquals(listOf("peed", "Long"), SparkReadReconcile.keptRows(damaged.rows, pagerOnly).map { it.name })

        assertEquals(listOf("peed", "Long"), SparkReadReconcile.keptRows(damaged.rows, null).map { it.name })
    }

    @Test
    fun `an authority scored on a different pager read is not applied`() {
        val earlier = complete(stat("Speed", 2), apt("Long", 2))
        val scored = complete(stat("peed", 2), apt("Long", 2))
        val repaired = authority(earlier, scored)
        val rescanned = listOf(stat("peed", 2), apt("Long", 2), white("Sympathy", 1))

        assertEquals(rescanned, SparkReadReconcile.keptRows(rescanned, repaired))
    }

    @Test
    fun `the confirmation handler records the kept set through the winner's recorded authority`() {
        val navigator = sourceFile("CareerLaunchNavigator.kt").readText()
        val handler = navigator.indexOf("private fun handleSparkSelectionConfirmation(")
        val end = navigator.indexOf("recordSparkRows(", handler)
        assertTrue(handler >= 0 && end > handler)
        val body = navigator.substring(handler, end)
        assertTrue("SparkReadReconcile.keptRows(expectedRows, transaction.recordedReadAuthorities[winner])" in body)
    }

    @Test
    fun `the blue reason text says no target instead of the ordering marker`() {
        val profile =
            SparkChooserProfile(
                traineeIdentity = "[Jokester Vibes] Tosen Jordan",
                objective = "sparks",
                blueTargetsOrdered = listOf("Speed"),
                preferredDistance = "Medium",
                preferredStyle = "Pace Chaser",
                preferredSurface = "Turf",
                plannedSkillNames = emptyList(),
            )
        val original = complete(stat("Guts", 2), apt("Turf", 1), unique("u", 1))
        val rerolled = complete(stat("Wit", 1), apt("Turf", 1), unique("u", 1))

        val choice = SparkKeepPolicy.choose(original, rerolled, profile)

        assertEquals("blue", choice.decidedBy)
        assertEquals(SparkSetSide.ORIGINAL, choice.side)
        assertTrue("blue 0/no target/2 vs 0/no target/1" in choice.reason, choice.reason)
        assertFalse(Int.MIN_VALUE.toString() in choice.reason, choice.reason)
    }

    private fun sourceFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(5) {
            for (root in listOf("src/main/java/com/steve1316/uma_android_automation", "android/app/src/main/java/com/steve1316/uma_android_automation")) {
                val candidate = File(dir, "$root/$relative")
                if (candidate.isFile) return candidate
            }
            dir = dir?.parentFile
        }
        throw IllegalStateException("missing $relative from ${System.getProperty("user.dir")}")
    }
}
