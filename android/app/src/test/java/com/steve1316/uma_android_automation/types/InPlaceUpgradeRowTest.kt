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
    private val firmConditionsX = data(200153, "Firm Conditions ×", 10034, 50, upgrade = 200152, downgrade = 200154)
    private val firmConditions = data(200152, "Firm Conditions ○", 10031, 90, upgrade = 200151, downgrade = 200153)
    private val firmConditionsUp = data(200151, "Firm Conditions ◎", 10031, 110, downgrade = 200152)
    private val fukushimaX = data(200103, "Fukushima Racecourse ×", 10024, 40, upgrade = 200102)
    private val fukushima = data(200102, "Fukushima Racecourse ○", 10021, 70, upgrade = 200101, downgrade = 200103)
    private val fukushimaUp = data(200101, "Fukushima Racecourse ◎", 10021, 90, downgrade = 200102)

    private fun resolve(row: SkillData, upgrade: SkillData?, price: Int, owned: Set<String> = emptySet(), rowObtained: Boolean = false): String =
        inPlaceUpgradeRowName(row, upgrade, price, rowObtained, owned)

    /** The resolver's order for a row that fuzzy-matched a × name. */
    private fun resolveRead(read: SkillData, twin: SkillData?, upgrade: SkillData?, price: Int, owned: Set<String> = emptySet(), rowObtained: Boolean = false): String {
        val name: String = negativeTwinRowName(read, twin, price, rowObtained)
        return if (twin != null && name == twin.name) resolve(twin, upgrade, price, owned, rowObtained) else name
    }

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
    fun `a × read charged at the ◎ price with the ○ owned reads as ◎`() {
        // ◎ at the ○'s hint discount: 99 at 10%, 66 at 40%.
        for (price in listOf(99, 66)) {
            assertEquals("Firm Conditions ◎", resolveRead(firmConditionsX, firmConditions, firmConditionsUp, price, owned = setOf("Firm Conditions ○")), "price $price")
        }
    }

    @Test
    fun `a × read above the × cost with nothing owned reads as ○`() {
        assertEquals("Firm Conditions ○", resolveRead(firmConditionsX, firmConditions, firmConditionsUp, 81))
        assertEquals("Firm Conditions ○", resolveRead(firmConditionsX, firmConditions, firmConditionsUp, 54))
    }

    @Test
    fun `a × read at or below the × cost stays ×`() {
        assertEquals("Firm Conditions ×", resolveRead(firmConditionsX, firmConditions, firmConditionsUp, 50, owned = setOf("Firm Conditions ○")))
        assertEquals("Firm Conditions ×", resolveRead(firmConditionsX, firmConditions, firmConditionsUp, 30))
    }

    @Test
    fun `the cheaper × chains use their own × cost`() {
        assertEquals("Fukushima Racecourse ○", resolveRead(fukushimaX, fukushima, fukushimaUp, 63))
        assertEquals("Fukushima Racecourse ○", resolveRead(fukushimaX, fukushima, fukushimaUp, 42))
        assertEquals("Fukushima Racecourse ◎", resolveRead(fukushimaX, fukushima, fukushimaUp, 81, owned = setOf("Fukushima Racecourse ○")))
        assertEquals("Fukushima Racecourse ×", resolveRead(fukushimaX, fukushima, fukushimaUp, 40))
    }

    @Test
    fun `a × read is untouched when obtained, when its ○ is missing, or when the twin is not its upgrade`() {
        assertEquals("Firm Conditions ×", resolveRead(firmConditionsX, firmConditions, firmConditionsUp, 0, rowObtained = true))
        assertEquals("Firm Conditions ×", resolveRead(firmConditionsX, null, null, 99))
        assertEquals("Firm Conditions ×", negativeTwinRowName(firmConditionsX, fukushima, 99, rowObtained = false))
    }

    @Test
    fun `a positive row is never remapped by the × rule`() {
        assertEquals("Firm Conditions ○", negativeTwinRowName(firmConditions, firmConditionsUp, 99, rowObtained = false))
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
        val twin: Int = resolver.indexOf("negativeTwinRowName(read.skillData, read.next?.skillData, price, rowObtained)")
        assertTrue(twin >= 0 && twin < resolver.indexOf("val row: SkillListEntry = entries[name]"), "the × rule runs before the in-place lookup")
    }
}
