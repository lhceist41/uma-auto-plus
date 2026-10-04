package com.steve1316.uma_android_automation.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

@DisplayName("In-place ◎ upgrade rows read as ○")
class InPlaceUpgradeRowTest {
    private fun data(id: Int, name: String, iconId: Int, cost: Int, upgrade: Int? = null, downgrade: Int? = null): SkillData =
        SkillData(
            id = id,
            name = name,
            description = "",
            iconId = iconId,
            cost = cost,
            evalPt = 100,
            condition = "",
            precondition = "",
            bIsInheritedUnique = false,
            communityTier = null,
            upgrade = upgrade,
            downgrade = downgrade,
        )

    // Ids, icon ids and costs from src/data/skills.json.
    private val leftHanded = data(200022, "Left-Handed ○", 10011, 90, upgrade = 200021, downgrade = 200023)
    private val leftHandedUp = data(200021, "Left-Handed ◎", 10011, 110, downgrade = 200022)
    private val kyoto = data(200062, "Kyoto Racecourse ○", 10021, 90, upgrade = 200061, downgrade = 200063)
    private val kyotoUp = data(200061, "Kyoto Racecourse ◎", 10021, 110, downgrade = 200062)
    private val summerRunner = data(200182, "Summer Runner ○", 10011, 90, upgrade = 200181, downgrade = 200183)
    private val summerRunnerUp = data(200181, "Summer Runner ◎", 10011, 110, downgrade = 200182)
    private val frontRunnerSavvy = data(201522, "Front Runner Savvy ○", 10051, 110, upgrade = 201521)
    private val frontRunnerSavvyUp = data(201521, "Front Runner Savvy ◎", 10051, 130, downgrade = 201522)
    private val mediumStraightaways = data(201102, "Medium Straightaways ○", 20011, 100, upgrade = 201101, downgrade = 201103)
    private val mediumStraightawaysUp = data(201101, "Medium Straightaways ◎", 20011, 110, downgrade = 201102)
    private val cornerRecovery = data(200352, "Corner Recovery ○", 20021, 170, upgrade = 200351, downgrade = 200353)
    private val swingingMaestro = data(200351, "Swinging Maestro", 20022, 170, downgrade = 200352)

    private fun resolve(row: SkillData, upgrade: SkillData?, price: Int, owned: Set<String> = emptySet(), rowObtained: Boolean = false): String =
        inPlaceUpgradeRowName(row, upgrade, price, rowObtained, owned)

    @Test
    fun `an owned ○ whose row price is above the ○ cost reads as ◎`() {
        assertEquals("Left-Handed ◎", resolve(leftHanded, leftHandedUp, 99, owned = setOf("Left-Handed ○")))
    }

    @Test
    fun `an owned ○ reads as ◎ even when the ◎ price also fits the ○ range`() {
        assertEquals("Kyoto Racecourse ◎", resolve(kyoto, kyotoUp, 77, owned = setOf("Kyoto Racecourse ○")))
    }

    @Test
    fun `an unowned ○ at a ○ price stays ○`() {
        assertEquals("Summer Runner ○", resolve(summerRunner, summerRunnerUp, 81))
    }

    @Test
    fun `an unowned ○ priced above its cost but within the ◎ cost reads as ◎`() {
        assertEquals("Front Runner Savvy ◎", resolve(frontRunnerSavvy, frontRunnerSavvyUp, 130))
    }

    @Test
    fun `a price above the ◎ cost is a misread, not proof of the ◎`() {
        assertEquals("Front Runner Savvy ○", resolve(frontRunnerSavvy, frontRunnerSavvyUp, 199))
    }

    @Test
    fun `a straightaway chain is in-place too`() {
        assertEquals("Medium Straightaways ◎", resolve(mediumStraightaways, mediumStraightawaysUp, 71, owned = setOf("Medium Straightaways ○")))
    }

    @Test
    fun `a non-in-place white and gold pair is untouched`() {
        assertEquals("Corner Recovery ○", resolve(cornerRecovery, swingingMaestro, 300, owned = setOf("Corner Recovery ○")))
    }

    @Test
    fun `a ◎ missing from the skill data leaves the row unchanged`() {
        assertEquals("Left-Handed ○", resolve(leftHanded, null, 99, owned = setOf("Left-Handed ○")))
    }

    @Test
    fun `an obtained row is untouched`() {
        assertEquals("Left-Handed ○", resolve(leftHanded, leftHandedUp, 0, owned = setOf("Left-Handed ○"), rowObtained = true))
    }

    @Test
    fun `both scan paths look the row up by its resolved name`() {
        // SkillList needs a live Game, so its call sites are pinned by source.
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val rel = "android/app/src/main/java/com/steve1316/uma_android_automation/types/SkillList.kt"
        while (dir != null && !File(dir, rel).isFile) dir = dir.parentFile
        val src: String = File(dir ?: throw AssertionError("$rel not found"), rel).readText()
        for (fn in listOf("fun analyzeSkillListEntry(", "fun analyzeSkillListEntryThreadSafe(")) {
            val start: Int = src.indexOf(fn)
            assertTrue(start >= 0, fn)
            val body: String = src.substring(start, src.indexOf("\n    }", start))
            assertTrue(body.contains("resolveInPlaceUpgradeRow(") && body.contains("entries[rowName]"), fn)
        }
        val resolver: String = src.substring(src.indexOf("private fun resolveInPlaceUpgradeRow("))
        assertTrue(resolver.contains("val owned: Set<String> = campaign.trainee.ownedSkillNames + ownedThisSession"))
        assertTrue(resolver.contains("inPlaceUpgradeRowName(row.skillData, upgrade.skillData, price, rowObtained, owned)"))
    }
}
