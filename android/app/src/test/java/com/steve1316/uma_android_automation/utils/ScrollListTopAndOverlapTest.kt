package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.types.BoundingBox
import com.steve1316.uma_android_automation.types.TrackblazerShopList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The scroll-to-top skip and the frame-overlap proof behind [ScrollList.process].
 *
 * Scrollbar geometry is the Trackblazer shop's as read on a 1080x1920 device: a 690 px track and
 * a 168 px thumb, 1 px below the track top when the shop has just opened.
 */
@DisplayName("ScrollList top proof and frame overlap")
class ScrollListTopAndOverlapTest {
    private val track = BoundingBox(x = 8, y = 8, w = 10, h = 690)

    @Nested
    @DisplayName("listProvenAtTop")
    inner class ListProvenAtTop {
        @Test
        fun `a thumb resting at the track top proves the list is at its top`() {
            assertTrue(listProvenAtTop(track, BoundingBox(x = 8, y = 9, w = 10, h = 168)))
        }

        @Test
        fun `a thumb further down the track does not`() {
            assertFalse(listProvenAtTop(track, BoundingBox(x = 8, y = 131, w = 10, h = 168)))
        }

        @Test
        fun `a track collapsed to the thumb's height proves nothing`() {
            val thumb = BoundingBox(x = 8, y = 300, w = 10, h = 168)
            assertFalse(listProvenAtTop(BoundingBox(x = 8, y = 300, w = 10, h = 170), thumb))
        }

        @Test
        fun `a missing scrollbar or thumb proves nothing`() {
            assertFalse(listProvenAtTop(null, BoundingBox(x = 8, y = 9, w = 10, h = 168)))
            assertFalse(listProvenAtTop(track, null))
        }
    }

    @Nested
    @DisplayName("frameOverlapCount")
    inner class FrameOverlapCount {
        @Test
        fun `a scrolled frame skips only the rows it shares with the previous one`() {
            assertEquals(1, frameOverlapCount(listOf("Reset Whistle", "Grilled Carrots"), listOf("Grilled Carrots", "Stamina Manual")))
            assertEquals(0, frameOverlapCount(listOf("Reset Whistle"), listOf("Stamina Manual")))
        }

        @Test
        fun `an unmoved frame of unreadable shop rows proves the end of the list`() {
            val ys = listOf(38, 243, 448, 653)
            val keys = ys.map { TrackblazerShopList.shopRowKey(null, it) }
            val overlap = frameOverlapCount(keys, ys.map { TrackblazerShopList.shopRowKey(null, it) })
            assertEquals(keys.size, overlap)
            assertTrue(endOfListProven(atTrackBottom = true, foundNewEntries = overlap < keys.size, entriesDetected = true))
        }

        @Test
        fun `index-based fallback keys for the same rows never overlap, so the proof never came`() {
            val lastFrame = (0..3).map { "UNKNOWN_$it" }
            val sameRowsNextFrame = (4..7).map { "UNKNOWN_$it" }
            val overlap = frameOverlapCount(lastFrame, sameRowsNextFrame)
            assertEquals(0, overlap)
            assertFalse(endOfListProven(atTrackBottom = true, foundNewEntries = overlap < sameRowsNextFrame.size, entriesDetected = true))
        }
    }
}
