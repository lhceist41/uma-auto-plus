package com.steve1316.uma_android_automation.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The "Exchange Complete" result after a Pro Shop purchase with the game's auto-use option on. Each case
 * is a real purchase from a Trackblazer career on MuMu: what was bought, and which rows the game tagged "Used".
 */
@DisplayName("Trackblazer auto-used purchase split")
class TrackblazerAutoUsedPurchaseTest {
    private fun split(bought: List<String>, tags: List<String>): Pair<List<String>, List<String>> {
        val quickUse = bought.filter { TrackblazerShopList.shopItems[it]?.isQuickUsage == true }
        return TrackblazerShopList.splitAutoUsedPurchase(bought, quickUse, tags)
    }

    @Test
    fun `a mixed purchase consumes only the tagged rows and leaves the rest to the normal queue`() {
        // Stamina Scroll and Power Scroll tagged Used; Miracle Cure left with its (greyed) use buttons.
        val (used, remaining) = split(listOf("Stamina Scroll", "Power Scroll", "Miracle Cure"), listOf("Stamina Scroll", "Power Scroll"))
        assertEquals(listOf("Stamina Scroll", "Power Scroll"), used)
        assertEquals(listOf("Miracle Cure"), remaining)
    }

    @Test
    fun `an untagged quick-use item is still queued the normal way`() {
        // Speed Scroll tagged Used; Grilled Carrots still held 1 -> 1 with its plus button.
        val (used, remaining) = split(listOf("Speed Scroll", "Grilled Carrots", "Master Cleat Hammer"), listOf("Speed Scroll"))
        assertEquals(listOf("Speed Scroll"), used)
        assertEquals(listOf("Grilled Carrots"), remaining)
    }

    @Test
    fun `a pure auto-use purchase is consumed as before`() {
        val (used, remaining) = split(listOf("Stamina Scroll", "Good-Luck Charm"), listOf("Stamina Scroll"))
        assertEquals(listOf("Stamina Scroll"), used)
        assertEquals(emptyList<String>(), remaining)
    }

    @Test
    fun `an unreadable result claims nothing`() {
        val (used, remaining) = split(listOf("Power Manual", "Master Cleat Hammer"), emptyList())
        assertEquals(emptyList<String>(), used)
        assertEquals(listOf("Power Manual"), remaining)
    }

    @Test
    fun `a misread or extra tag never consumes an item that was not bought`() {
        val (used, remaining) = split(listOf("Speed Scroll"), listOf("Speed Manual", "Speed Scroll", "Speed Scroll"))
        assertEquals(listOf("Speed Scroll"), used)
        assertEquals(emptyList<String>(), remaining)
    }

    @Test
    fun `the Exchange Complete handler counts only tagged rows and queues the rest`() {
        val trackblazer = File(kotlinRoot(), "bot/campaigns/Trackblazer.kt").readText().replace("\r\n", "\n")
        assertTrue(trackblazer.contains("shopList.readUsedTagNames()"))
        assertTrue(trackblazer.contains("TrackblazerShopList.splitAutoUsedPurchase(boughtItems, quickUseItemsOnly, usedTagNames)"))
        assertTrue(trackblazer.contains("shopList.useSpecificItems(remainingQuickUse, bUseAll = true"))
        // The old inference that every quick-use item was used whenever Confirm Use was not seen is gone.
        assertTrue(!trackblazer.contains("quickUseItemsOnly.forEach { useInventoryItem(it) }"))
    }

    @Test
    fun `tagged rows are readable and an unreadable row cannot stall the dialog scan`() {
        val shopList = File(kotlinRoot(), "types/TrackblazerShopList.kt").readText().replace("\r\n", "\n")
        // The Used tag sits on the plus button's line, 83 px to its left at 1080 wide (measured on the frames).
        assertTrue(shopList.contains("Point(usedTag.x + game.imageUtils.relWidth(83), usedTag.y + game.imageUtils.relHeight(6))"))
        assertTrue(shopList.contains("name ?: \"unread@\${entry.bbox.y}\""))
    }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/com/steve1316/uma_android_automation")
            if (candidate.isDirectory) return candidate
            val nested = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (nested.isDirectory) return nested
            dir = dir.parentFile
        }
        error("Kotlin source root not found")
    }
}
