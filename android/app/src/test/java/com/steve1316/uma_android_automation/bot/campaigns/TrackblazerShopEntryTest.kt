package com.steve1316.uma_android_automation.bot.campaigns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Shop check state after tapping the Shop dialog button")
class TrackblazerShopEntryTest {
    @Test
    fun `a tap that did not open the shop keeps the check pending and the initial check untouched`() {
        assertEquals(Triple(true, 3, false), shopFlagsAfterDialogTap(enteredShop = false, shopCheckCounter = 3, initialShopCheckPerformed = false))
        assertEquals(Triple(true, 0, true), shopFlagsAfterDialogTap(enteredShop = false, shopCheckCounter = 0, initialShopCheckPerformed = true))
    }

    @Test
    fun `a tap that opened the shop clears the check and marks the initial check done`() {
        assertEquals(Triple(false, 0, true), shopFlagsAfterDialogTap(enteredShop = true, shopCheckCounter = 4, initialShopCheckPerformed = false))
    }

    @Test
    fun `a failed entry on the unlock dialog leaves the initial check pending`() {
        val (shouldCheck, _, initialDone) = shopFlagsAfterDialogTap(enteredShop = false, shopCheckCounter = 0, initialShopCheckPerformed = false)
        assertEquals(true, shouldCheck)
        assertEquals(false, initialDone)
    }
}
