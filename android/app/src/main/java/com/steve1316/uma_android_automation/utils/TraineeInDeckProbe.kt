package com.steve1316.uma_android_automation.utils

/**
 * OCR-free probe for the red "! Trainee" tag the game puts on a Support Formation card of the trainee's
 * own character, while it keeps Start Career disabled ("Includes a character identical to the
 * Trainee." only appears after a tap). The tag is a red pill with white lettering across the top edge
 * of the card, at the same place on each of the six slots (three columns, two rows). Both red edges
 * and the lettering are required: the card art and the orange Power icon have red patches, and the
 * Borrow Card picker's red "Duplicate Support" tags sit elsewhere. Measured on adb screencaps (true
 * RGB) under src/test/resources/fixtures/traineeindeck.
 */
object TraineeInDeckProbe {
    /** Slots 1 to 5 hold the player's own cards; this one holds the borrowed Friends card. */
    const val FRIEND_SLOT = 6

    // The tag's top-left corner on slot 1, and the slot pitch, on the 1080x1920 surface.
    private const val FIRST_TAG_LEFT = 127
    private const val FIRST_TAG_TOP = 408
    private const val COLUMN_PITCH = 318
    private const val ROW_PITCH = 410
    private const val MIN_EDGE_HITS = 27
    private const val MIN_TEXT_WHITE = 4
    private const val MIN_TEXT_RED = 8

    /** The 1-based slots whose card carries the tag, in slot order. Any other surface size reads none. */
    fun taggedSlots(sampler: SparkPixelSampler, width: Int, height: Int): List<Int> {
        if (width != 1080 || height != 1920) return emptyList()
        return (0 until 6)
            .filter { hasTag(sampler, FIRST_TAG_LEFT + COLUMN_PITCH * (it % 3), FIRST_TAG_TOP + ROW_PITCH * (it / 3)) }
            .map { it + 1 }
    }

    private fun hasTag(sampler: SparkPixelSampler, left: Int, top: Int): Boolean {
        val xs = left + 30..left + 170 step 10
        fun edgeHits(vararg ys: Int) = ys.sumOf { y -> xs.count { x -> isTagRed(sampler.argb(x, y)) } }
        if (edgeHits(top + 3, top + 5) < MIN_EDGE_HITS || edgeHits(top + 32, top + 33) < MIN_EDGE_HITS) return false
        val lettering = (left + 60..left + 150 step 3).map { sampler.argb(it, top + 22) }
        return lettering.count(::isWhite) >= MIN_TEXT_WHITE && lettering.count(::isTagRed) >= MIN_TEXT_RED
    }

    // The tag is (255, 79, 49).
    private fun isTagRed(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return r >= 215 && g in 45..120 && b in 15..95
    }

    private fun isWhite(argb: Int): Boolean = ((argb shr 16) and 0xFF) >= 225 && ((argb shr 8) and 0xFF) >= 225 && (argb and 0xFF) >= 225
}
