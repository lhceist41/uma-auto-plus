package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Floating button keep-clear check")
class OverlayKeepClearTest {
    private fun hits(
        x: Int,
        y: Int,
        size: Int = 112,
        width: Int = 1080,
        height: Int = 1920,
        inset: Int = 0,
    ) = overlayKeepClearHits(x, y, size, width, height, inset)

    @Test
    @DisplayName("at 1080x1920 the areas are the list as written")
    fun identityAt1920() {
        for (area in OVERLAY_KEEP_CLEAR) {
            assertEquals(listOf(area.name), hits(area.x, area.y, 1), area.name)
            assertEquals(listOf(area.name), hits(area.x + area.w - 1, area.y + area.h - 1, 1), area.name)
            assertEquals(emptyList<String>(), hits(area.x + area.w, area.y, 1), area.name)
            assertEquals(emptyList<String>(), hits(area.x, area.y + area.h, 1), area.name)
            assertEquals(emptyList<String>(), hits(area.x - 1, area.y - 1, 1), area.name)
        }
    }

    @Test
    @DisplayName("the top strip and the screen centre are clear on 1080x1920")
    fun clearPlaces() {
        assertEquals(emptyList<String>(), hits(816, 4))
        assertEquals(emptyList<String>(), hits(484, 904))
    }

    @Test
    @DisplayName("on 1080x2316 the areas follow the bands")
    fun tallPhone() {
        val inset = 94
        assertEquals(emptyList<String>(), hits(816, 98, 112, 1080, 2316, inset))
        assertEquals(listOf("goal banner"), hits(816, 220, 112, 1080, 2316, inset))
        assertEquals(listOf("stats and bottom buttons"), hits(816, 1750, 112, 1080, 2316, inset))
        assertEquals(listOf("stats and bottom buttons"), hits(100, 2100, 112, 1080, 2316, inset))
    }

    @Test
    @DisplayName("a wrong band for an area would be seen")
    fun bandMattersOnTallPhone() {
        val inset = 94
        val unmappedGoalRow = 150
        assertEquals(emptyList<String>(), hits(816, unmappedGoalRow, 20, 1080, 2316, inset))
        val unmappedBottomRow = 1300
        assertEquals(emptyList<String>(), hits(100, unmappedBottomRow, 20, 1080, 2316, inset))
    }

    @Test
    @DisplayName("a button over both areas reports both")
    fun both() {
        assertEquals(listOf("goal banner", "stats and bottom buttons"), hits(400, 150, 1800))
    }
}
