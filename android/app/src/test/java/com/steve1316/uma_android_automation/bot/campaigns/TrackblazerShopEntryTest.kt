package com.steve1316.uma_android_automation.bot.campaigns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Shop entry checks")
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

    @Test
    fun `Main on screen while the shop slides in keeps waiting until the shop shows`() {
        val frames = ArrayDeque(listOf(false, false, true))
        var pauses = 0
        assertEquals(true, awaitShopOpen(5, { frames.removeFirst() }, { pauses++ }))
        assertEquals(2, pauses)
        assertEquals(0, frames.size)
    }

    @Test
    fun `a shop on the first frame opens without pausing`() {
        var pauses = 0
        assertEquals(true, awaitShopOpen(5, { true }, { pauses++ }))
        assertEquals(0, pauses)
    }

    @Test
    fun `no shop after every try is a failed entry and pauses only between tries`() {
        var checks = 0
        var pauses = 0
        val noShop = {
            checks++
            false
        }
        assertEquals(false, awaitShopOpen(5, noShop, { pauses++ }))
        assertEquals(5, checks)
        assertEquals(4, pauses)
    }

    @Test
    fun `a shop on the last try still counts`() {
        val frames = ArrayDeque(listOf(false, false, false, false, true))
        assertEquals(true, awaitShopOpen(5, { frames.removeFirst() }, {}))
    }
}
