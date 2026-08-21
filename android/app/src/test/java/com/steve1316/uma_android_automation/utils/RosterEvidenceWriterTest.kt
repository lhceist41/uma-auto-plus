package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The field-to-crop mapping behind the roster failure evidence.
 *
 * The crop write itself needs a real Bitmap and a Context, so it is proven live rather than here;
 * what is pinned here is the part that can silently rot: that every immutable field name the corpus
 * can emit maps to the exact region the reader scored, and that a field with no meaningful crop says
 * so instead of returning a wrong box.
 */
@DisplayName("Roster failure-evidence crops")
class RosterEvidenceWriterTest {
    @Test
    fun `every immutable identity field the corpus can emit maps to a crop`() {
        // These names come straight from the scan record's unresolvedFields, so a rename on either
        // side breaks this rather than silently producing no evidence for that field.
        assertNotNull(RosterEvidenceWriter.boxForField("character"))
        assertNotNull(RosterEvidenceWriter.boxForField("outfit"))
        assertNotNull(RosterEvidenceWriter.boxForField("rank"))
        assertNotNull(RosterEvidenceWriter.boxForField("rating"))
        for (key in STAT_KEYS) assertNotNull(RosterEvidenceWriter.boxForField("stat_$key"), "stat_$key has no crop region")
    }

    @Test
    fun `each stat maps to the same box its digit OCR was read from`() {
        // A crop from a different box would show pixels the reader never scored, which is worse than
        // no crop: it would send the next fix at the wrong region.
        STAT_KEYS.forEachIndexed { i, key ->
            assertEquals(STAT_VALUE_BOXES[i], RosterEvidenceWriter.boxForField("stat_$key"))
        }
    }

    @Test
    fun `the name and outfit share the one region they are both read from`() {
        // Both are resolved out of a single OCR of the two-line header, so one crop explains either.
        assertEquals(RosterEvidenceWriter.boxForField("character"), RosterEvidenceWriter.boxForField("outfit"))
        val box = RosterEvidenceWriter.boxForField("outfit")!!
        assertEquals(DETAIL_NAME_OUTFIT_X, box.x0)
        assertEquals(DETAIL_NAME_OUTFIT_Y, box.y0)
        assertEquals(DETAIL_NAME_OUTFIT_X + DETAIL_NAME_OUTFIT_W, box.x1)
        assertEquals(DETAIL_NAME_OUTFIT_Y + DETAIL_NAME_OUTFIT_H, box.y1)
    }

    @Test
    fun `the rank crop is the medal region the classifier correlates`() {
        assertEquals(RANK_MEDAL_BOX, RosterEvidenceWriter.boxForField("rank"))
    }

    @Test
    fun `a field with no single meaningful crop returns null rather than a wrong box`() {
        // Aptitude grades are ten tiny letter boxes and have never been a failure class; auxiliary
        // fields do not block identity at all.
        assertNull(RosterEvidenceWriter.boxForField("aptitude_turf"))
        assertNull(RosterEvidenceWriter.boxForField("statGrade_spd"))
        assertNull(RosterEvidenceWriter.boxForField("careerFans"))
        assertNull(RosterEvidenceWriter.boxForField("stat_nonsense"))
        assertNull(RosterEvidenceWriter.boxForField(""))
    }

    @Test
    fun `the per-scan cap is bounded well above a full roster walk`() {
        // One bad field on every entry of a 260-Veteran roster must still be captured in full; a walk
        // where everything fails must not fill the device.
        assertTrue(RosterEvidenceWriter.MAX_CROPS_PER_SCAN >= 300)
        assertTrue(RosterEvidenceWriter.MAX_CROPS_PER_SCAN <= 2000)
    }
}
