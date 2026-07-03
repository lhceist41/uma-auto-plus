package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.StartModule.Companion.RotationConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for the rotation-cursor resync math on [RotationConfig].
 *
 * The scenario under test: an externally interrupted queue (game update, kill without resumable
 * state) is restarted, the cursor resets to entry 0, but the game resumes the old in-flight
 * career. The mismatch guard resyncs by computing an offset ([RotationConfig.resyncOffsetFor])
 * that makes the in-flight run map onto the on-screen trainee's slot, and every later slot lookup
 * folds that offset in ([RotationConfig.indexForRun] with offset) so the cycle continues from her
 * entry instead of stopping the whole queue.
 */
@DisplayName("Rotation cursor resync math")
class RotationResyncTest {
    private fun rotation(count: Int, switchEvery: Int = 1) =
        RotationConfig(
            enabled = true,
            switchEvery = switchEvery,
            inGameNames = (1..count).map { "Trainee $it" },
        )

    @Nested
    @DisplayName("resyncOffsetFor()")
    inner class ResyncOffsetTests {
        @Test
        fun `maps the interrupted run onto the on-screen entry`() {
            // Cursor restarted at run 1 (slot 0) but the career on screen is entry 1.
            val r = rotation(count = 3)
            val offset = r.resyncOffsetFor(1, 1)
            assertEquals(1, offset)
            assertEquals(1, r.indexForRun(1, offset))
        }

        @Test
        fun `zero when the cursor already points at the entry`() {
            val r = rotation(count = 3, switchEvery = 2)
            for (run in 1..8) {
                assertEquals(0, r.resyncOffsetFor(run, r.indexForRun(run)))
            }
        }

        @Test
        fun `wraps positive when the on-screen entry is behind the cursor`() {
            // Run 2 (slot 1) resynced onto entry 0: offset must wrap forward, never go negative.
            val r = rotation(count = 3)
            val offset = r.resyncOffsetFor(2, 0)
            assertEquals(2, offset)
            assertEquals(0, r.indexForRun(2, offset))
        }

        @Test
        fun `single-entry rotation is always offset zero`() {
            val r = rotation(count = 1)
            assertEquals(0, r.resyncOffsetFor(5, 0))
            assertEquals(0, r.indexForRun(5, 0))
        }

        @Test
        fun `empty rotation returns slot zero and offset zero`() {
            // count=0 is the disabled-config shape (RotationConfig(false, 1, emptyList())); the
            // guards must keep the math at 0 instead of dividing/mod-ing by zero.
            val r = RotationConfig(enabled = false, switchEvery = 1, inGameNames = emptyList())
            assertEquals(0, r.resyncOffsetFor(5, 2))
            assertEquals(0, r.indexForRun(5, 3))
        }

        @Test
        fun `out-of-range target index wraps by modulo`() {
            // The caller bounds-checks before calling; the pure function itself wraps, so an
            // out-of-range target behaves as target mod count rather than corrupting the offset.
            val r = rotation(count = 3)
            val offset = r.resyncOffsetFor(2, 5)
            assertEquals(r.resyncOffsetFor(2, 5.mod(3)), offset)
            assertEquals(5.mod(3), r.indexForRun(2, offset))
        }

        @Test
        fun `mid-block resync with switchEvery greater than 1`() {
            // A crash rarely lands on a block boundary: switchEvery=3, 2 trainees, interrupted
            // career detected on run 2 (mid-block, slot 0) belonging to entry 1. The resynced
            // run and the rest of its block map to entry 1, then blocks alternate as usual.
            val r = rotation(count = 2, switchEvery = 3)
            val offset = r.resyncOffsetFor(2, 1)
            assertEquals(1, offset)
            assertEquals(
                listOf(1, 1, 1, 0, 0, 0, 1, 1, 1),
                (1..9).map { r.indexForRun(it, offset) },
            )
        }
    }

    @Nested
    @DisplayName("indexForRun() with offset")
    inner class OffsetIndexTests {
        @Test
        fun `subsequent runs continue the cycle from the resynced entry`() {
            // switchEvery=1, 3 trainees, resynced run 1 onto entry 1: 2, 0, 1 must follow.
            val r = rotation(count = 3)
            val offset = r.resyncOffsetFor(1, 1)
            assertEquals(listOf(1, 2, 0, 1), (1..4).map { r.indexForRun(it, offset) })
        }

        @Test
        fun `block boundaries shift with the offset under switchEvery greater than 1`() {
            // Blocks of 3: runs 1-3 were entry 0's block; after resyncing run 1 onto entry 1 the
            // blocks become 1,1,1 / 2,2,2 / 0,0,0.
            val r = rotation(count = 3, switchEvery = 3)
            val offset = r.resyncOffsetFor(1, 1)
            assertEquals(
                listOf(1, 1, 1, 2, 2, 2, 0, 0, 0),
                (1..9).map { r.indexForRun(it, offset) },
            )
        }

        @Test
        fun `two-trainee rotation returns to the pre-resync entry on the next run`() {
            // The regression case behind the prev-index override: after resyncing run 1 onto
            // entry 1, run 2 maps back to entry 0 — the same slot the cursor had loaded before
            // the resync, so a stale switch-boundary comparison would skip the snapshot swap.
            val r = rotation(count = 2)
            val offset = r.resyncOffsetFor(1, 1)
            assertEquals(1, r.indexForRun(1, offset))
            assertEquals(0, r.indexForRun(2, offset))
        }

        @Test
        fun `zero offset preserves the original mapping`() {
            val r = rotation(count = 3, switchEvery = 2)
            for (run in 1..12) {
                assertEquals(r.indexForRun(run), r.indexForRun(run, 0))
            }
        }

        @Test
        fun `offset arithmetic stays consistent at large run numbers`() {
            val r = rotation(count = 3, switchEvery = 2)
            for (run in listOf(999, 1000, 100_000)) {
                assertEquals((r.indexForRun(run) + 2).mod(3), r.indexForRun(run, 2))
            }
        }
    }
}
